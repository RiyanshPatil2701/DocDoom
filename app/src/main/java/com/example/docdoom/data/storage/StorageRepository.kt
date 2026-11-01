package com.example.docdoom.data.storage
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore

data class StorageSnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val photosBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val documentsBytes: Long,
    val othersBytes: Long
)

object StorageRepository {

    fun readStorage(context: Context): StorageSnapshot {

        // Actual device storage information
        val storagePath = Environment
            .getExternalStorageDirectory()
            .absolutePath

        val stat = StatFs(storagePath)

        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

        // Media category sizes
        val resolver = context.contentResolver

        val photosBytes = getMediaSize(
            resolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )

        val videosBytes = getMediaSize(
            resolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        val audioBytes = getMediaSize(
            resolver,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )

        // Documents and other indexed files
        val (documentsBytes, othersBytes) = getOtherFileSizes(resolver)

        return StorageSnapshot(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            availableBytes = availableBytes,
            photosBytes = photosBytes,
            videosBytes = videosBytes,
            audioBytes = audioBytes,
            documentsBytes = documentsBytes,
            othersBytes = othersBytes
        )
    }

    private fun getMediaSize(
        resolver: ContentResolver,
        collection: android.net.Uri
    ): Long {

        var total = 0L

        val projection = arrayOf(
            MediaStore.MediaColumns.SIZE
        )

        try {
            resolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.SIZE} > 0",
                null,
                null
            )?.use { cursor ->

                while (cursor.moveToNext()) {
                    total += cursor.getLong(0)
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted.
            // Return the accessible total rather than crashing.
        }

        return total
    }

    private fun getOtherFileSizes(
        resolver: ContentResolver
    ): Pair<Long, Long> {

        var documents = 0L
        var others = 0L

        val collection = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
        )

        try {
            resolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.SIZE} > 0",
                null,
                null
            )?.use { cursor ->

                val sizeIndex = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.SIZE
                )

                val mimeIndex = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.MIME_TYPE
                )

                while (cursor.moveToNext()) {

                    val size = cursor.getLong(sizeIndex)
                    val mimeType = cursor.getString(mimeIndex)
                        ?.lowercase()
                        ?: ""

                    // Media categories are counted separately.
                    if (
                        mimeType.startsWith("image/") ||
                        mimeType.startsWith("video/") ||
                        mimeType.startsWith("audio/")
                    ) {
                        continue
                    }

                    if (
                        mimeType.startsWith("application/") ||
                        mimeType.startsWith("text/")
                    ) {
                        documents += size
                    } else {
                        others += size
                    }
                }
            }
        } catch (e: SecurityException) {
            // Android may restrict access to these files.
        }

        return Pair(documents, others)
    }
}
