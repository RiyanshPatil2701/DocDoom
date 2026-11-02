package com.example.docdoom.presentation.tools

import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException

/**
 * Reads image header information without allocating a full-resolution Bitmap.
 * Use this before asking AdaptiveImageResourcePlanner to choose a processing path.
 */
object ImageMetadataInspector {

    data class ImageMetadata(
        val width: Int,
        val height: Int,
        val mimeType: String?,
        val fileSizeBytes: Long,
        val pixelCount: Long,
        val estimatedRgbaBytes: Long
    )

    /**
     * Inspects a local image file using BitmapFactory's bounds-only mode.
     * The method does not decode pixel data into a Bitmap.
     */
    @Throws(IOException::class)
    fun inspect(file: File): ImageMetadata {
        if (!file.exists() || !file.isFile) {
            throw IOException("The selected image is no longer available.")
        }

        if (file.length() <= 0L) {
            throw IOException("The selected image is empty.")
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        // In bounds-only mode this reads image dimensions and type without
        // creating the full pixel bitmap.
        BitmapFactory.decodeFile(file.absolutePath, options)

        val width = options.outWidth
        val height = options.outHeight

        if (width <= 0 || height <= 0) {
            throw IOException("This image format could not be inspected.")
        }

        val pixels = safeMultiply(width.toLong(), height.toLong())
        val rgbaBytes = safeMultiply(pixels, BYTES_PER_RGBA_PIXEL)

        return ImageMetadata(
            width = width,
            height = height,
            mimeType = options.outMimeType,
            fileSizeBytes = file.length(),
            pixelCount = pixels,
            estimatedRgbaBytes = rgbaBytes
        )
    }

    private fun safeMultiply(first: Long, second: Long): Long {
        if (first == 0L || second == 0L) return 0L
        if (first > Long.MAX_VALUE / second) return Long.MAX_VALUE
        return first * second
    }

    private const val BYTES_PER_RGBA_PIXEL = 4L
}
