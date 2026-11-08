package com.example.docdoom.data.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class DocumentCompressionLevel(val jpegQuality: Int) {
    RECOMMENDED(82),
    HIGH(68)
}

enum class OfficeDocumentType(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val requiredPart: String
) {
    WORD(
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "Word document",
        "word/document.xml"
    ),
    EXCEL(
        "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Excel workbook",
        "xl/workbook.xml"
    ),
    POWERPOINT(
        "pptx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "PowerPoint presentation",
        "ppt/presentation.xml"
    )
}

data class DocumentCompressionResult(
    val outputFile: File,
    val type: OfficeDocumentType,
    val originalBytes: Long,
    val outputBytes: Long,
    val imagesOptimized: Int
) {
    val isSmaller: Boolean get() = outputBytes < originalBytes
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)
}

/**
 * Local Office Open XML compressor.
 *
 * DOCX, XLSX and PPTX are ZIP packages. This engine rebuilds the package using
 * maximum DEFLATE compression and recompresses supported embedded JPEG images.
 * PNG images are losslessly re-encoded and retained only when the result is
 * smaller. Unsupported media and all XML/package parts are copied unchanged.
 */
object OfficeDocumentCompressionEngine {

    @Throws(IOException::class)
    fun detectType(file: File): OfficeDocumentType {
        if (!file.isFile || file.length() < 4L) {
            throw IOException("The selected file is empty or unavailable.")
        }

        val signature = ByteArray(4)
        FileInputStream(file).use { input ->
            if (input.read(signature) != 4 ||
                signature[0] != 0x50.toByte() ||
                signature[1] != 0x4B.toByte()
            ) {
                throw IOException("This is not a valid Word, Excel or PowerPoint document.")
            }
        }

        try {
            ZipFile(file).use { zip ->
                if (zip.getEntry("[Content_Types].xml") == null ||
                    zip.getEntry("_rels/.rels") == null
                ) {
                    throw IOException("This ZIP file is not a supported Office document.")
                }

                return OfficeDocumentType.entries.firstOrNull { type ->
                    zip.getEntry(type.requiredPart) != null
                } ?: throw IOException(
                    "Unsupported document. Choose a DOCX, XLSX or PPTX file."
                )
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("The selected Office document is damaged or unsupported.", error)
        }
    }

    @Throws(IOException::class)
    fun compress(
        inputFile: File,
        outputFile: File,
        level: DocumentCompressionLevel
    ): DocumentCompressionResult {
        val type = detectType(inputFile)
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val workDir = File(
            outputFile.parentFile ?: outputFile.absoluteFile.parentFile,
            "office_work_${System.nanoTime()}"
        )
        if (!workDir.mkdirs()) {
            throw IOException("Couldn't prepare temporary storage for compression.")
        }

        var optimizedCount = 0
        try {
            ZipInputStream(FileInputStream(inputFile).buffered()).use { zipInput ->
                ZipOutputStream(FileOutputStream(outputFile).buffered()).use { zipOutput ->
                    zipOutput.setLevel(9)
                    var entry = zipInput.nextEntry

                    while (entry != null) {
                        val newEntry = ZipEntry(entry.name).apply {
                            if (entry.time >= 0L) time = entry.time
                            comment = entry.comment
                        }

                        zipOutput.putNextEntry(newEntry)

                        if (!entry.isDirectory) {
                            val tempEntry = File(workDir, "entry_${System.nanoTime()}.bin")
                            try {
                                FileOutputStream(tempEntry).buffered().use { temp ->
                                    zipInput.copyTo(temp)
                                }

                                val isOfficeMedia = entry.name.contains("/media/") ||
                                    entry.name.startsWith("word/media/") ||
                                    entry.name.startsWith("xl/media/") ||
                                    entry.name.startsWith("ppt/media/")

                                val optimized = if (isOfficeMedia) {
                                    optimizeMedia(tempEntry, entry.name, level)
                                } else {
                                    null
                                }

                                val contentFile = optimized ?: tempEntry
                                contentFile.inputStream().buffered().use { it.copyTo(zipOutput) }
                                if (optimized != null) optimizedCount++
                            } finally {
                                tempEntry.delete()
                            }
                        }

                        zipOutput.closeEntry()
                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }
            }

            // Validate the rebuilt package before exposing it to the UI.
            val outputType = detectType(outputFile)
            if (outputType != type) {
                throw IOException("The rebuilt document failed validation.")
            }

            // Never replace a document with a larger result.
            if (outputFile.length() >= inputFile.length()) {
                outputFile.delete()
                inputFile.inputStream().buffered().use { input ->
                    FileOutputStream(outputFile).buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                optimizedCount = 0
            }

            return DocumentCompressionResult(
                outputFile = outputFile,
                type = type,
                originalBytes = inputFile.length(),
                outputBytes = outputFile.length(),
                imagesOptimized = optimizedCount
            )
        } catch (error: Exception) {
            outputFile.delete()
            if (error is IOException) throw error
            throw IOException(
                error.localizedMessage ?: "Document compression failed.",
                error
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Returns a replacement only when it is smaller than the original.
     * JPEG quality changes only pixel encoding; dimensions are never reduced.
     */
    private fun optimizeMedia(
        source: File,
        entryName: String,
        level: DocumentCompressionLevel
    ): File? {
        val extension = entryName.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("jpg", "jpeg", "png")) return null
        if (source.length() < MIN_MEDIA_BYTES) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        // Avoid risking an out-of-memory crash on unusually large embedded images.
        if (pixels > MAX_PIXELS) return null

        val bitmap = try {
            BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null

        val candidate = File(source.parentFile, "optimized_${System.nanoTime()}.$extension")
        try {
            val format = if (extension == "png") Bitmap.CompressFormat.PNG
            else Bitmap.CompressFormat.JPEG

            val written = FileOutputStream(candidate).buffered().use { output ->
                bitmap.compress(
                    format,
                    if (format == Bitmap.CompressFormat.JPEG) level.jpegQuality else 100,
                    output
                )
            }

            return if (written && candidate.length() in 1 until source.length()) {
                candidate
            } else {
                candidate.delete()
                null
            }
        } catch (_: Exception) {
            candidate.delete()
            return null
        } finally {
            bitmap.recycle()
        }
    }

    private const val MIN_MEDIA_BYTES = 24L * 1024L
    private const val MAX_PIXELS = 40_000_000L
}
