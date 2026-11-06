
#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <algorithm>
#include <limits>
#include <vector>
#include <setjmp.h>

extern "C" {
#include "jpeglib.h"
#include "png.h"
#include "zlib.h"
}

// =====================================================
// Shared helpers
// =====================================================

static bool getPaths(
        JNIEnv* env,
        jstring inputPath,
        jstring outputPath,
        std::string& input,
        std::string& output
) {
    if (!inputPath || !outputPath) return false;

    const char* in = env->GetStringUTFChars(inputPath, nullptr);
    if (!in) return false;

    input.assign(in);
    env->ReleaseStringUTFChars(inputPath, in);

    const char* out = env->GetStringUTFChars(outputPath, nullptr);
    if (!out) return false;

    output.assign(out);
    env->ReleaseStringUTFChars(outputPath, out);

    return !input.empty() && !output.empty() && input != output;
}

static void removeIncompleteOutput(const std::string& path) {
    if (!path.empty()) remove(path.c_str());
}

// =====================================================
// JPEG error handling
// =====================================================

struct DocDoomJpegError {
    jpeg_error_mgr manager;
    jmp_buf jumpBuffer;
};

static void docDoomJpegErrorExit(j_common_ptr info) {
    auto* error =
            reinterpret_cast<DocDoomJpegError*>(info->err);
    longjmp(error->jumpBuffer, 1);
}

// =====================================================
// JPEG compressor
// Existing native JPEG functionality retained
// =====================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_docdoom_NativeImageCompressor_compressJpeg(
        JNIEnv* env,
        jobject,
        jstring inputPath,
        jstring outputPath,
        jint quality
) {
    std::string input;
    std::string output;

    if (!getPaths(env, inputPath, outputPath, input, output)) {
        return JNI_FALSE;
    }

    FILE* inputFile = fopen(input.c_str(), "rb");
    if (!inputFile) return JNI_FALSE;

    FILE* outputFile = nullptr;

    jpeg_decompress_struct decoder{};
    jpeg_compress_struct encoder{};

    DocDoomJpegError decoderError{};
    DocDoomJpegError encoderError{};

    volatile bool decoderCreated = false;
    volatile bool encoderCreated = false;

    decoder.err = jpeg_std_error(&decoderError.manager);
    decoderError.manager.error_exit = docDoomJpegErrorExit;

    if (setjmp(decoderError.jumpBuffer)) {
        if (encoderCreated) jpeg_destroy_compress(&encoder);
        if (decoderCreated) jpeg_destroy_decompress(&decoder);
        if (outputFile) fclose(outputFile);
        fclose(inputFile);
        removeIncompleteOutput(output);
        return JNI_FALSE;
    }

    jpeg_create_decompress(&decoder);
    decoderCreated = true;

    jpeg_stdio_src(&decoder, inputFile);

    if (jpeg_read_header(&decoder, TRUE) != JPEG_HEADER_OK) {
        jpeg_destroy_decompress(&decoder);
        fclose(inputFile);
        return JNI_FALSE;
    }

    decoder.out_color_space = JCS_RGB;

    if (!jpeg_start_decompress(&decoder) ||
        decoder.output_components != 3 ||
        decoder.output_width == 0 ||
        decoder.output_height == 0) {
        jpeg_destroy_decompress(&decoder);
        fclose(inputFile);
        return JNI_FALSE;
    }

    encoder.err = jpeg_std_error(&encoderError.manager);
    encoderError.manager.error_exit = docDoomJpegErrorExit;

    if (setjmp(encoderError.jumpBuffer)) {
        if (encoderCreated) jpeg_destroy_compress(&encoder);
        if (decoderCreated) jpeg_destroy_decompress(&decoder);
        if (outputFile) fclose(outputFile);
        fclose(inputFile);
        removeIncompleteOutput(output);
        return JNI_FALSE;
    }

    outputFile = fopen(output.c_str(), "wb");

    if (!outputFile) {
        jpeg_destroy_decompress(&decoder);
        fclose(inputFile);
        return JNI_FALSE;
    }

    jpeg_create_compress(&encoder);
    encoderCreated = true;

    jpeg_stdio_dest(&encoder, outputFile);

    encoder.image_width = decoder.output_width;
    encoder.image_height = decoder.output_height;
    encoder.input_components = 3;
    encoder.in_color_space = JCS_RGB;

    jpeg_set_defaults(&encoder);
    jpeg_set_quality(
            &encoder,
            std::clamp(static_cast<int>(quality), 1, 100),
            TRUE
    );

    jpeg_start_compress(&encoder, TRUE);

    const size_t rowSize =
            static_cast<size_t>(decoder.output_width) * 3;

    JSAMPARRAY row = (*decoder.mem->alloc_sarray)(
            reinterpret_cast<j_common_ptr>(&decoder),
            JPOOL_IMAGE,
            static_cast<JDIMENSION>(rowSize),
            1
    );

    bool success = true;

    while (decoder.output_scanline < decoder.output_height) {
        if (jpeg_read_scanlines(&decoder, row, 1) != 1 ||
            jpeg_write_scanlines(&encoder, row, 1) != 1) {
            success = false;
            break;
        }
    }

    if (success &&
        decoder.output_scanline == decoder.output_height) {
        jpeg_finish_decompress(&decoder);
        jpeg_finish_compress(&encoder);
    } else {
        success = false;
    }

    jpeg_destroy_compress(&encoder);
    jpeg_destroy_decompress(&decoder);

    fclose(outputFile);
    fclose(inputFile);

    if (!success) {
        removeIncompleteOutput(output);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

// =====================================================
// Fast lossy PNG compressor
// =====================================================

struct DocDoomPngContext {
    jmp_buf jumpBuffer;
    png_structp reader = nullptr;
    png_structp writer = nullptr;
    png_infop readInfo = nullptr;
    png_infop writeInfo = nullptr;
    FILE* inputFile = nullptr;
    FILE* outputFile = nullptr;
    png_bytep* rows = nullptr;
    unsigned char* pixels = nullptr;
    png_uint_32 height = 0;
};

static void docDoomPngErrorExit(png_structp png, png_const_charp) {
    auto* c = static_cast<DocDoomPngContext*>(png_get_error_ptr(png));
    if (c) longjmp(c->jumpBuffer, 1);
}

static void docDoomPngWarning(png_structp, png_const_charp) {}

static void docDoomCleanupPng(DocDoomPngContext* c) {
    if (!c) return;
    if (c->reader) png_destroy_read_struct(&c->reader, c->readInfo ? &c->readInfo : nullptr, nullptr);
    if (c->writer) png_destroy_write_struct(&c->writer, c->writeInfo ? &c->writeInfo : nullptr);
    if (c->inputFile) { fclose(c->inputFile); c->inputFile = nullptr; }
    if (c->outputFile) { fclose(c->outputFile); c->outputFile = nullptr; }
    free(c->rows);
    free(c->pixels);
    c->rows = nullptr;
    c->pixels = nullptr;
}

static bool docDoomCopyFile(const std::string& input, const std::string& output) {
    FILE* in = fopen(input.c_str(), "rb");
    if (!in) return false;
    FILE* out = fopen(output.c_str(), "wb");
    if (!out) { fclose(in); return false; }
    unsigned char buffer[64 * 1024];
    bool ok = true;
    while (true) {
        size_t n = fread(buffer, 1, sizeof(buffer), in);
        if (n && fwrite(buffer, 1, n, out) != n) { ok = false; break; }
        if (n < sizeof(buffer)) { if (ferror(in)) ok = false; break; }
    }
    if (fflush(out) != 0) ok = false;
    if (fclose(out) != 0) ok = false;
    fclose(in);
    if (!ok) remove(output.c_str());
    return ok;
}

static long long docDoomFileSize(const std::string& path) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return -1;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return -1; }
    long long size = static_cast<long long>(ftell(f));
    fclose(f);
    return size;
}

// Reduce RGB precision in-place. Alpha is deliberately untouched so that
// transparent pixels and anti-aliased edges retain their transparency.
// This is a single linear pass: O(number of pixels), with no palette search,
// histogram, sorting, or dithering.
static void docDoomQuantizePixels(unsigned char* pixels, size_t count, int quality, int channels) {
    const int q = std::clamp(quality, 20, 100);
    // 20 -> 3 bits/channel; 100 -> 8 bits/channel.
    const int bits = 3 + ((q - 20) * 5 + 40) / 80;
    const int shift = 8 - std::clamp(bits, 3, 8);
    if (shift == 0) return;
    const unsigned char mask = static_cast<unsigned char>(0xFFu << shift);
    const int halfStep = 1 << (shift - 1);

    for (size_t i = 0; i < count; ++i) {
        unsigned char* p = pixels + i * channels;
        if (channels == 4 && p[3] == 0) {
            p[0] = p[1] = p[2] = 0;
            continue;
        }
        // Rounded quantization avoids a systematic darkening bias.
        p[0] = static_cast<unsigned char>(std::min(255, (p[0] + halfStep) & mask));
        p[1] = static_cast<unsigned char>(std::min(255, (p[1] + halfStep) & mask));
        p[2] = static_cast<unsigned char>(std::min(255, (p[2] + halfStep) & mask));
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_docdoom_NativeImageCompressor_compressPng(
        JNIEnv* env, jobject, jstring inputPath, jstring outputPath, jint quality) {
    std::string input, output;
    if (!getPaths(env, inputPath, outputPath, input, output)) return JNI_FALSE;

    const std::string temp = output + ".docdoom_fast_tmp";
    remove(temp.c_str());

    auto* c = static_cast<DocDoomPngContext*>(calloc(1, sizeof(DocDoomPngContext)));
    if (!c) return JNI_FALSE;

    c->inputFile = fopen(input.c_str(), "rb");
    if (!c->inputFile) { free(c); return JNI_FALSE; }

    if (setjmp(c->jumpBuffer)) {
        docDoomCleanupPng(c);
        remove(temp.c_str());
        free(c);
        return JNI_FALSE;
    }

    c->reader = png_create_read_struct(PNG_LIBPNG_VER_STRING, c, docDoomPngErrorExit, docDoomPngWarning);
    if (!c->reader) { docDoomCleanupPng(c); free(c); return JNI_FALSE; }
    c->readInfo = png_create_info_struct(c->reader);
    if (!c->readInfo) png_error(c->reader, "Could not create PNG input info");

    png_init_io(c->reader, c->inputFile);
    png_read_info(c->reader, c->readInfo);

    const png_uint_32 width = png_get_image_width(c->reader, c->readInfo);
    c->height = png_get_image_height(c->reader, c->readInfo);
    const int colorType = png_get_color_type(c->reader, c->readInfo);
    const int bitDepth = png_get_bit_depth(c->reader, c->readInfo);
    if (!width || !c->height || width > PNG_UINT_31_MAX || c->height > PNG_UINT_31_MAX)
        png_error(c->reader, "Invalid PNG dimensions");

    // Normalize supported PNG input to 8-bit RGB or RGBA. Keep opaque images
    // as RGB instead of needlessly expanding every pixel to RGBA.
    if (bitDepth == 16) png_set_strip_16(c->reader);
    if (colorType == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(c->reader);
    if (colorType == PNG_COLOR_TYPE_GRAY && bitDepth < 8)
        png_set_expand_gray_1_2_4_to_8(c->reader);
    if (png_get_valid(c->reader, c->readInfo, PNG_INFO_tRNS))
        png_set_tRNS_to_alpha(c->reader);
    if (colorType == PNG_COLOR_TYPE_GRAY || colorType == PNG_COLOR_TYPE_GRAY_ALPHA)
        png_set_gray_to_rgb(c->reader);
    const bool hasAlpha = (colorType & PNG_COLOR_MASK_ALPHA) ||
                          png_get_valid(c->reader, c->readInfo, PNG_INFO_tRNS);
    if (!hasAlpha) {
        // RGB stays 3 bytes per pixel; this is materially smaller for photos.
    }

    png_set_interlace_handling(c->reader);
    png_read_update_info(c->reader, c->readInfo);

    const size_t pixelCount = static_cast<size_t>(width) * static_cast<size_t>(c->height);
    const int channels = hasAlpha ? 4 : 3;
    if (pixelCount > (256ULL * 1024ULL * 1024ULL) / channels)
        png_error(c->reader, "PNG exceeds decoded memory limit");
    const png_size_t rowBytes = png_get_rowbytes(c->reader, c->readInfo);
    if (rowBytes != static_cast<png_size_t>(width) * channels)
        png_error(c->reader, "Unexpected decoded PNG layout");

    c->pixels = static_cast<unsigned char*>(malloc(pixelCount * channels));
    c->rows = static_cast<png_bytep*>(malloc(sizeof(png_bytep) * c->height));
    if (!c->pixels || !c->rows) png_error(c->reader, "PNG memory allocation failed");
    for (png_uint_32 y = 0; y < c->height; ++y)
        c->rows[y] = c->pixels + static_cast<size_t>(y) * width * channels;

    png_read_image(c->reader, c->rows);
    png_read_end(c->reader, nullptr);

    // Preserve sRGB intent when present; strip potentially sensitive EXIF/GPS.
    int srgbIntent = PNG_sRGB_INTENT_PERCEPTUAL;
    const bool hasSrgb = png_get_sRGB(c->reader, c->readInfo, &srgbIntent) != 0;

    png_destroy_read_struct(&c->reader, &c->readInfo, nullptr);
    fclose(c->inputFile);
    c->inputFile = nullptr;

    docDoomQuantizePixels(c->pixels, pixelCount, static_cast<int>(quality), channels);

    c->outputFile = fopen(temp.c_str(), "wb");
    if (!c->outputFile) {
        docDoomCleanupPng(c); free(c); return JNI_FALSE;
    }
    c->writer = png_create_write_struct(PNG_LIBPNG_VER_STRING, c, docDoomPngErrorExit, docDoomPngWarning);
    if (!c->writer) png_error(nullptr, "Could not create PNG writer");
    c->writeInfo = png_create_info_struct(c->writer);
    if (!c->writeInfo) png_error(c->writer, "Could not create PNG output info");

    if (setjmp(c->jumpBuffer)) {
        docDoomCleanupPng(c);
        remove(temp.c_str());
        free(c);
        return JNI_FALSE;
    }

    png_init_io(c->writer, c->outputFile);
    // Balanced fast settings. Adaptive PNG filters and level 3 generally
    // compress much better than forcing SUB + level 1, without a slow max pass.
    png_set_compression_level(c->writer, 3);
    png_set_compression_strategy(c->writer, Z_DEFAULT_STRATEGY);
    png_set_compression_mem_level(c->writer, 8);
    png_set_filter(c->writer, PNG_FILTER_TYPE_BASE,
                   PNG_FILTER_NONE | PNG_FILTER_SUB | PNG_FILTER_UP |
                   PNG_FILTER_AVG | PNG_FILTER_PAETH);

    png_set_IHDR(c->writer, c->writeInfo, width, c->height, 8,
                 hasAlpha ? PNG_COLOR_TYPE_RGBA : PNG_COLOR_TYPE_RGB,
                 PNG_INTERLACE_NONE,
                 PNG_COMPRESSION_TYPE_BASE, PNG_FILTER_TYPE_BASE);
    if (hasSrgb) png_set_sRGB(c->writer, c->writeInfo, srgbIntent);
    png_write_info(c->writer, c->writeInfo);
    png_write_image(c->writer, c->rows);
    png_write_end(c->writer, c->writeInfo);

    docDoomCleanupPng(c);
    free(c);

    const long long originalSize = docDoomFileSize(input);
    const long long compressedSize = docDoomFileSize(temp);
    if (originalSize <= 0 || compressedSize <= 0) {
        remove(temp.c_str());
        return JNI_FALSE;
    }

    if (compressedSize < originalSize) {
        remove(output.c_str());
        if (rename(temp.c_str(), output.c_str()) == 0) return JNI_TRUE;
        remove(temp.c_str());
        remove(output.c_str());
        return JNI_FALSE;
    }

    remove(temp.c_str());
    return docDoomCopyFile(input, output) ? JNI_TRUE : JNI_FALSE;
}
