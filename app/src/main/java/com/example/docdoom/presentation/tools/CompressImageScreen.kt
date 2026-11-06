package com.example.docdoom.presentation.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docdoom.NativeImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val CompressEmerald = Color(0xFF16856B)
private val CompressDark = Color(0xFF202A27)
private val CompressMuted = Color(0xFF687570)
private val CompressSurface = Color(0xFFFFFFFF)

private const val PREVIEW_MAX_DIMENSION = 1000

private data class ImageInfo(
    val width: Int,
    val height: Int
) {
    val estimatedArgbBytes: Long
        get() = width.toLong() * height.toLong() * 4L
}

private data class CompressionResult(
    val file: File,
    val preview: Bitmap,
    val width: Int,
    val height: Int,
    val isSmaller: Boolean
)

@Composable
fun CompressImageScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var originalPreview by remember { mutableStateOf<Bitmap?>(null) }
    var imageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var sourceIsJpeg by remember { mutableStateOf(false) }
    var sourceIsPng by remember { mutableStateOf(false) }

    var originalName by remember { mutableStateOf("image") }
    var originalSize by remember { mutableStateOf(0L) }

    var outputFormat by remember { mutableStateOf("JPG") }
    var quality by remember { mutableStateOf(60f) }

    var resultFile by remember { mutableStateOf<File?>(null) }
    var resultPreview by remember { mutableStateOf<Bitmap?>(null) }
    var resultSize by remember { mutableStateOf(0L) }
    var resultWidth by remember { mutableStateOf(0) }
    var resultHeight by remember { mutableStateOf(0) }
    var resultIsSmaller by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isCompressing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                message = ""
                saved = false
                savedUri = null

                resultFile?.delete()
                resultFile = null
                resultPreview?.recycle()
                resultPreview = null

                originalPreview?.recycle()
                originalPreview = null

                sourceFile?.delete()
                sourceFile = null
                imageInfo = null
                sourceIsJpeg = false
                sourceIsPng = false

                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }

                    val copied = withContext(Dispatchers.IO) {
                        copySelectedImageToCache(context, uri)
                    }

                    val info = withContext(Dispatchers.IO) {
                        inspectImage(copied)
                    }

                    val isJpeg = withContext(Dispatchers.IO) {
                        isJpegFile(copied)
                    }

                    val isPng = withContext(Dispatchers.IO) {
                        isPngFile(copied)
                    }
                    val isWebp = withContext(Dispatchers.IO) {
                        isWebpFile(copied)
                    }

                    val detectedFormat = when {
                        isJpeg -> "JPG"
                        isPng -> "PNG"
                        isWebp -> "WebP"
                        else -> "JPG"
                    }

                    val thumb = withContext(Dispatchers.IO) {
                        decodeSampledImage(copied, PREVIEW_MAX_DIMENSION)
                    }

                    sourceFile = copied
                    imageInfo = info
                    sourceIsJpeg = isJpeg
                    sourceIsPng = isPng
                    outputFormat = detectedFormat
                    originalPreview = thumb

                    originalName = queryImageName(context, uri)
                        ?.substringBeforeLast('.', "image")
                        ?.ifBlank { "image" } ?: "image"

                    originalSize = copied.length()

                    message = "Detected $detectedFormat. Original format selected automatically."
                } catch (error: Exception) {
                    message = error.localizedMessage
                        ?: "Couldn't open this image. Try another image."
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { destinationUri ->
        val readyFile = resultFile

        if (destinationUri != null && readyFile != null) {
            scope.launch {
                isSaving = true
                message = ""

                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver
                            .openOutputStream(destinationUri, "w")
                            ?.use { output ->
                                readyFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            ?: throw IOException(
                                "Couldn't open the selected save location."
                            )
                    }

                    val displayName = queryImageName(context, destinationUri)
                        ?: "${originalName}_compressed.${outputExtension(outputFormat)}"

                    val size = withContext(Dispatchers.IO) {
                        queryImageSize(context, destinationUri)
                            .takeIf { it > 0L }
                            ?: readyFile.length()
                    }

                    onSaved(destinationUri, displayName, size)

                    savedUri = destinationUri
                    saved = true

                    message = if (resultIsSmaller) {
                        "Compressed image saved successfully."
                    } else {
                        "Converted image saved successfully."
                    }
                } catch (error: Exception) {
                    message = error.localizedMessage ?: "Couldn't save the image."
                } finally {
                    isSaving = false
                }
            }
        }
    }

    val info = imageInfo

    val useNativeJpeg = outputFormat == "JPG" && sourceIsJpeg
    val useNativePng = outputFormat == "PNG" && sourceIsPng

    val canStartCompression =
        sourceFile != null &&
                info != null &&
                !isCompressing &&
                !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = onBack,
            enabled = !isCompressing && !isSaving
        ) {
            Icon(
                Icons.Outlined.ArrowBack,
                contentDescription = null,
                tint = CompressEmerald
            )
            Text("  Back", color = CompressEmerald)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Compress Image",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = CompressDark
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Compress images while keeping your original file safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = CompressMuted
        )

        Spacer(Modifier.height(18.dp))

        OutlinedButton(
            onClick = {
                imagePicker.launch(arrayOf("image/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            enabled = !isLoading && !isCompressing && !isSaving
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Text(
                if (sourceFile == null) "Choose an image"
                else "Choose another image"
            )
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CompressEmerald
                )
                Text(
                    "  Inspecting image and creating thumbnail…",
                    color = CompressMuted
                )
            }
        }

        if (sourceFile != null && info != null && originalPreview != null) {
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImagePreviewCard(
                    "Original",
                    originalPreview,
                    "${formatImageSize(originalSize)} · ${info.width} × ${info.height}",
                    Modifier.weight(1f)
                )

                ImagePreviewCard(
                    "Compressed result",
                    resultPreview,
                    if (resultSize > 0L) {
                        "${formatImageSize(resultSize)} · $resultWidth × $resultHeight"
                    } else {
                        "Not compressed yet"
                    },
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Output format",
                fontWeight = FontWeight.SemiBold,
                color = CompressDark
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull("JPG", if (sourceIsPng) "PNG" else null, "WebP").forEach { format ->
                    FilterChip(
                        selected = outputFormat == format,
                        onClick = {
                            outputFormat = format

                            resultFile?.delete()
                            resultFile = null

                            resultPreview?.recycle()
                            resultPreview = null

                            resultSize = 0L
                            saved = false
                            savedUri = null
                                    },
                        enabled = !isCompressing,
                        label = { Text(format) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Quality: ${quality.roundToInt()}%",
                fontWeight = FontWeight.SemiBold,
                color = CompressDark
            )

            Slider(
                value = quality,
                onValueChange = { quality = it },
                valueRange = 20f..100f,
                steps = 7,
                enabled = !isCompressing
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Smaller file", color = CompressMuted, fontSize = 12.sp)
                Text("Better quality", color = CompressMuted, fontSize = 12.sp)
            }

            if (outputFormat == "PNG") {
                Text(
                    "PNG uses fast colour-precision reduction. Transparency and dimensions are preserved.",
                    color = CompressMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!sourceIsPng) {
                Text(
                    "PNG output is unavailable for this image to prevent photo conversions that can greatly increase file size. Use WebP for smaller photo files.",
                    color = CompressMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (useNativeJpeg || useNativePng) {
                Spacer(Modifier.height(8.dp))

                Text(
                    if (useNativeJpeg) {
                        "Native JPEG engine · Original image dimensions preserved"
                    } else {
                        "Native PNG engine · Fast colour reduction · Original dimensions preserved"
                    },
                    color = CompressEmerald,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    val source = sourceFile ?: return@Button
                    val sourceInfo = imageInfo ?: return@Button

                    scope.launch {
                        isCompressing = true
                        message = ""
                        saved = false
                        savedUri = null

                        resultFile?.delete()
                        resultFile = null

                        resultPreview?.recycle()
                        resultPreview = null

                        resultSize = 0L

                        try {
                            val output = withContext(Dispatchers.IO) {
                                when {
                                    useNativeJpeg -> compressNativeJpeg(
                                        source = source,
                                        originalSize = originalSize,
                                        quality = quality.roundToInt()
                                    )

                                    useNativePng -> compressNativePng(
                                        source = source,
                                        originalSize = originalSize,
                                        quality = quality.roundToInt()
                                    )

                                    else -> compressBitmapImage(
                                        source = source,
                                        originalSize = originalSize,
                                        info = sourceInfo,
                                        format = outputFormat,
                                        quality = quality.roundToInt(),
                                        maxDimension = null
                                    )
                                }
                            }

                            resultFile = output.file
                            resultPreview = output.preview
                            resultSize = output.file.length()
                            resultWidth = output.width
                            resultHeight = output.height
                            resultIsSmaller = output.isSmaller

                            message = if (output.isSmaller) {
                                "Compression complete. The result is smaller than the original."
                            } else {
                                "This output is not smaller than the original. You can keep the original or choose to save this format conversion."
                            }
                        } catch (error: OutOfMemoryError) {
                            message = "This image is too large for the available memory. Try the reduced-resolution option or a smaller image."
                        } catch (error: Exception) {
                            message = error.localizedMessage
                                ?: "Couldn't compress this image."
                        } finally {
                            isCompressing = false
                        }
                    }
                },
                enabled = canStartCompression,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CompressEmerald
                )
            ) {
                if (isCompressing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text("  Compressing…")
                } else {
                    Text(
                        if (resultFile == null) "Compress image"
                        else "Compress again"
                    )
                }
            }

            if (resultSize > 0L && !isCompressing) {
                Spacer(Modifier.height(12.dp))

                val difference = originalSize - resultSize

                when {
                    difference > 0L -> Text(
                        "${String.format(Locale.getDefault(), "%.1f", difference.toDouble() / originalSize * 100.0)}% smaller · ${formatImageSize(difference)} saved",
                        color = CompressEmerald,
                        fontWeight = FontWeight.SemiBold
                    )

                    difference < 0L -> {
                        Text(
                            "Output is ${formatImageSize(-difference)} larger than the original.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )

                    }

                    else -> Text(
                        "No size reduction. The original is already as small as this result.",
                        color = CompressMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        saveLauncher.launch(
                            "${originalName}_compressed.${outputExtension(outputFormat)}"
                        )
                    },
                    enabled = !isSaving && resultFile != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CompressEmerald
                    )
                ) {
                    Text(
                        if (isSaving) "Saving…" else "Save compressed image"
                    )
                }

                if (saved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = CompressEmerald
                        )
                        Text("  Saved successfully", color = CompressEmerald)
                    }

                    OutlinedButton(
                        onClick = {
                            savedUri?.let { uri ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = outputMimeType(outputFormat)
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share image")
                                )
                            }
                        },
                        enabled = savedUri != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Text("  Share image")
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = CompressMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ImagePreviewCard(
    title: String,
    bitmap: Bitmap?,
    detail: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CompressSurface
        )
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CompressDark,
                maxLines = 1
            )

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Color(0xFFF0F3F1),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        "Waiting",
                        color = CompressMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = CompressMuted
            )
        }
    }
}

private fun copySelectedImageToCache(
    context: Context,
    uri: Uri
): File {
    val output = File(
        context.cacheDir,
        "docdoom_source_${System.currentTimeMillis()}"
    )

    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Android couldn't open this image.")

        input.use { source ->
            FileOutputStream(output).use { destination ->
                source.copyTo(destination)
            }
        }

        if (output.length() <= 0L) {
            throw IOException("The selected image is empty or unavailable.")
        }

        return output
    } catch (error: Exception) {
        output.delete()
        throw IOException(
            "Couldn't read the selected image. Please try selecting it again.",
            error
        )
    }
}

private fun isJpegFile(file: File): Boolean {
    file.inputStream().use { input ->
        val first = input.read()
        val second = input.read()
        return first == 0xFF && second == 0xD8
    }
}

private fun isPngFile(file: File): Boolean {
    val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )
    file.inputStream().use { input ->
        val actual = ByteArray(signature.size)
        var offset = 0
        while (offset < actual.size) {
            val count = input.read(actual, offset, actual.size - offset)
            if (count < 0) return false
            offset += count
        }
        return actual.contentEquals(signature)
    }
}

private fun isWebpFile(file: File): Boolean {
    file.inputStream().use { input ->
        val header = ByteArray(12)
        var offset = 0
        while (offset < header.size) {
            val count = input.read(header, offset, header.size - offset)
            if (count < 0) return false
            offset += count
        }
        return String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
    }
}

private fun inspectImage(file: File): ImageInfo {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    BitmapFactory.decodeFile(file.absolutePath, options)

    if (options.outWidth <= 0 || options.outHeight <= 0) {
        throw IOException("This image format isn't supported on this device.")
    }

    return ImageInfo(options.outWidth, options.outHeight)
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int?
): Int {
    if (maxDimension == null) return 1

    var sample = 1

    while (max(width / sample, height / sample) > maxDimension) {
        sample *= 2
    }

    return sample
}

private fun decodeSampledImage(
    file: File,
    maxDimension: Int
): Bitmap {
    val bounds = inspectImage(file)
    val sample = calculateSampleSize(
        bounds.width,
        bounds.height,
        maxDimension
    )

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    return BitmapFactory.decodeFile(file.absolutePath, options)
        ?: throw IOException("Couldn't create an image preview.")
}


private fun compressNativeJpeg(
    source: File,
    originalSize: Long,
    quality: Int
): CompressionResult {
    val outputFile = File.createTempFile(
        "docdoom_native_",
        ".jpg",
        source.parentFile
    )

    try {
        val success = NativeImageCompressor.compressJpeg(
            source.absolutePath,
            outputFile.absolutePath,
            quality.coerceIn(1, 100)
        )

        if (!success || !outputFile.exists() || outputFile.length() <= 0L) {
            throw IOException("The native JPEG compressor couldn't process this image.")
        }

        // Preserve useful EXIF information and remove GPS metadata.
        com.example.docdoom.data.image.JpegMetadataHelper
            .preserveSafeMetadata(source, outputFile)

        val outputInfo = inspectImage(outputFile)

        val preview = decodeSampledImage(
            outputFile,
            PREVIEW_MAX_DIMENSION
        )

        return CompressionResult(
            file = outputFile,
            preview = preview,
            width = outputInfo.width,
            height = outputInfo.height,
            isSmaller = outputFile.length() < originalSize
        )
    } catch (error: Exception) {
        outputFile.delete()
        throw error
    }
}


private fun compressNativePng(
    source: File,
    originalSize: Long,
    quality: Int
): CompressionResult {
    val outputFile = File.createTempFile(
        "docdoom_native_",
        ".png",
        source.parentFile
    )

    try {
        val success = NativeImageCompressor.compressPng(
            source.absolutePath,
            outputFile.absolutePath,
            quality.coerceIn(20, 100)
        )

        if (!success || !outputFile.exists() || outputFile.length() <= 0L) {
            throw IOException("The native PNG compressor couldn't process this image.")
        }

        val outputInfo = inspectImage(outputFile)
        val preview = decodeSampledImage(outputFile, PREVIEW_MAX_DIMENSION)

        return CompressionResult(
            file = outputFile,
            preview = preview,
            width = outputInfo.width,
            height = outputInfo.height,
            isSmaller = outputFile.length() < originalSize
        )
    } catch (error: Exception) {
        outputFile.delete()
        throw error
    }
}


private fun compressBitmapImage(
    source: File,
    originalSize: Long,
    info: ImageInfo,
    format: String,
    quality: Int,
    maxDimension: Int?
): CompressionResult {
    val sample = calculateSampleSize(
        info.width,
        info.height,
        maxDimension
    )

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val bitmap = BitmapFactory.decodeFile(
        source.absolutePath,
        options
    ) ?: throw IOException("Android couldn't decode this image.")

    var outputFile: File? = null

    try {
        val createdFile = File.createTempFile(
            "docdoom_result_",
            ".${outputExtension(format)}",
            source.parentFile
        )

        outputFile = createdFile

        val compressFormat = when (format) {
            "JPG" -> Bitmap.CompressFormat.JPEG
            "PNG" -> Bitmap.CompressFormat.PNG
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }

        FileOutputStream(createdFile).use { stream ->
            val encoderQuality = if (format == "PNG") {
                100
            } else {
                quality.coerceIn(20, 100)
            }

            if (!bitmap.compress(compressFormat, encoderQuality, stream)) {
                throw IOException("The image encoder couldn't create the output file.")
            }
        }

        val preview = decodeSampledImage(
            createdFile,
            PREVIEW_MAX_DIMENSION
        )

        return CompressionResult(
            file = createdFile,
            preview = preview,
            width = bitmap.width,
            height = bitmap.height,
            isSmaller = createdFile.length() < originalSize
        )
    } catch (error: Exception) {
        outputFile?.delete()
        throw error
    } finally {
        bitmap.recycle()
    }
}

private fun queryImageName(
    context: Context,
    uri: Uri
): String? {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }
}

private fun queryImageSize(
    context: Context,
    uri: Uri
): Long {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0) cursor.getLong(index) else 0L
        } else {
            0L
        }
    } ?: 0L
}

private fun outputExtension(format: String): String = when (format) {
    "JPG" -> "jpg"
    "PNG" -> "png"
    else -> "webp"
}

private fun outputMimeType(format: String): String = when (format) {
    "JPG" -> "image/jpeg"
    "PNG" -> "image/png"
    else -> "image/webp"
}

private fun formatImageSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"

    bytes < 1024L * 1024L ->
        String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)

    else ->
        String.format(
            Locale.getDefault(),
            "%.2f MB",
            bytes / (1024.0 * 1024.0)
        )
}
