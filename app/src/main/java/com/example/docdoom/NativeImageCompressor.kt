package com.example.docdoom

object NativeImageCompressor {
    init {
        System.loadLibrary("docdoom_native")
    }

    external fun compressJpeg(
        inputPath: String,
        outputPath: String,
        quality: Int
    ): Boolean

    // Quality is 20..100. PNG uses a fast direct RGB precision reduction pass.
    external fun compressPng(
        inputPath: String,
        outputPath: String,
        quality: Int
    ): Boolean
}
