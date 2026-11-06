
package com.example.docdoom.data.image

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

object JpegMetadataHelper {

    private val preservedTags = listOf(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION
    )

    /**
     * Copies selected useful EXIF information from the original JPEG
     * to the compressed JPEG.
     *
     * GPS metadata is deliberately not copied.
     * The original file is never modified.
     */
    @Throws(IOException::class)
    fun preserveSafeMetadata(
        originalFile: File,
        compressedFile: File
    ) {
        if (!originalFile.exists() || !compressedFile.exists()) {
            throw IOException("Original or compressed image is missing.")
        }

        val originalExif = ExifInterface(originalFile.absolutePath)
        val outputExif = ExifInterface(compressedFile.absolutePath)

        preservedTags.forEach { tag ->
            val value = originalExif.getAttribute(tag)

            if (value != null) {
                outputExif.setAttribute(tag, value)
            }
        }

        // Explicitly remove GPS information from the compressed copy.
        outputExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
        outputExif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)

        outputExif.saveAttributes()
    }
}
