package com.example.docdoom.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.cos.COSName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.IdentityHashMap
import kotlin.coroutines.coroutineContext

enum class PdfCompressionLevel { LOW, MEDIUM, HIGH }

data class PdfCompressionResult(
    val outputFile: File,
    val originalBytes: Long,
    val outputBytes: Long,
    val pageCount: Int,
    val imagesOptimized: Int
) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)
    val reductionPercent: Int
        get() = if (originalBytes <= 0L || outputBytes >= originalBytes) 0
        else (((originalBytes - outputBytes) * 100.0) / originalBytes).toInt()
}

object PdfCompressionEngine {
    private data class Settings(val jpegQuality: Float, val maxLongEdge: Int, val minImageBytes: Long)

    private fun settings(level: PdfCompressionLevel): Settings = when (level) {
        // Resolution targets are based on the user's iLovePDF benchmark:
        // Low keeps detail, Recommended targets ~1250px, High targets ~600px.
        PdfCompressionLevel.LOW -> Settings(0.82f, 1800, 12L * 1024L)
        PdfCompressionLevel.MEDIUM -> Settings(0.72f, 1250, 8L * 1024L)
        PdfCompressionLevel.HIGH -> Settings(0.78f, 848, 4L * 1024L)
    }

    suspend fun compress(
        context: Context,
        sourceUri: Uri,
        level: PdfCompressionLevel,
        outputFile: File,
        onProgress: (String) -> Unit = {}
    ): PdfCompressionResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        PDFBoxResourceLoader.init(app)
        val workDir = File(app.cacheDir, "pdf_compress_${System.nanoTime()}")
        if (!workDir.mkdirs()) throw IOException("Couldn't prepare PDF workspace.")
        val input = File(workDir, "source.pdf")
        val stagedOutput = File(workDir, "optimized_with_orphans.pdf")
        val cleanOutput = File(workDir, "optimized_clean.pdf")
        try {
            onProgress("Reading PDF…")
            app.contentResolver.openInputStream(sourceUri)?.use { source ->
                input.outputStream().buffered().use { target -> source.copyTo(target) }
            } ?: throw IOException("Couldn't open the selected PDF.")
            val originalBytes = input.length()
            if (originalBytes == 0L) throw IOException("The selected PDF is empty.")
            coroutineContext.ensureActive()

            onProgress("Scanning embedded images…")
            val document = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly())
            var optimizedCount = 0
            val seen = IdentityHashMap<Any, Boolean>()
            val config = settings(level)
            try {
                for (pageIndex in 0 until document.numberOfPages) {
                    coroutineContext.ensureActive()
                    onProgress("Optimizing page ${pageIndex + 1} of ${document.numberOfPages}…")
                    val page = document.getPage(pageIndex)
                    optimizedCount += optimizeResources(
                        document, page.resources, config, seen
                    )
                }
                onProgress("Writing optimized pages…")
                document.save(stagedOutput)
            } finally {
                document.close()
            }

            if (!stagedOutput.exists() || stagedOutput.length() == 0L) {
                throw IOException("PDF compression did not produce an output file.")
            }

            // Rebuild into a brand-new document. PDFBox's save() can retain
            // detached COS streams from the source document. Appending pages
            // into a fresh document clones only objects reachable from pages,
            // dropping stale image streams left behind after XObject replacement.
            onProgress("Rebuilding PDF and removing unused data…")
            val optimizedDocument = PDDocument.load(
                stagedOutput,
                MemoryUsageSetting.setupTempFileOnly()
            )
            val cleanDocument = PDDocument()
            try {
                PDFMergerUtility().appendDocument(cleanDocument, optimizedDocument)
                cleanDocument.documentInformation = optimizedDocument.documentInformation
                cleanDocument.documentCatalog.language = optimizedDocument.documentCatalog.language
                cleanDocument.save(cleanOutput)
            } finally {
                cleanDocument.close()
                optimizedDocument.close()
            }

            if (!cleanOutput.exists() || cleanOutput.length() == 0L) {
                throw IOException("Couldn't rebuild the optimized PDF cleanly.")
            }

            outputFile.parentFile?.let {
                if (!it.exists() && !it.mkdirs()) throw IOException("Couldn't create output folder.")
            }
            if (outputFile.exists()) outputFile.delete()

            // Never return a larger file as a successful compression result.
            if (cleanOutput.length() < originalBytes) {
                cleanOutput.copyTo(outputFile, overwrite = true)
            } else {
                input.copyTo(outputFile, overwrite = true)
            }

            coroutineContext.ensureActive()
            PdfCompressionResult(
                outputFile = outputFile,
                originalBytes = originalBytes,
                outputBytes = outputFile.length(),
                pageCount = documentPageCount(input),
                imagesOptimized = optimizedCount
            )
        } catch (e: Exception) {
            outputFile.delete()
            throw e
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun documentPageCount(file: File): Int {
        val doc = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        return try { doc.numberOfPages } finally { doc.close() }
    }

    private fun optimizeResources(
        document: PDDocument,
        resources: PDResources?,
        config: Settings,
        seen: IdentityHashMap<Any, Boolean>
    ): Int {
        if (resources == null) return 0
        var changed = 0
        val names = resources.xObjectNames.toList()
        for (name in names) {
            val xObject: PDXObject = try { resources.getXObject(name) } catch (_: Exception) { continue }
            when (xObject) {
                is PDImageXObject -> {
                    val cosIdentity = xObject.cosObject
                    if (seen.put(cosIdentity, true) != null) continue
                    val replacement = optimizeImage(document, xObject, config) ?: continue
                    resources.put(name, replacement)
                    changed++
                }
                is PDFormXObject -> {
                    val formResources = xObject.resources
                    if (formResources != null) changed += optimizeResources(document, formResources, config, seen)
                }
            }
        }
        return changed
    }

    private fun optimizeImage(
        document: PDDocument,
        source: PDImageXObject,
        config: Settings
    ): PDImageXObject? {
        return try {
            val streamLength = source.cosObject.length
            if (streamLength in 1 until config.minImageBytes) return null
            if (source.isStencil) return null

            val bitmap = source.image ?: return null
            try {
                val width = bitmap.width
                val height = bitmap.height
                if (width < 160 || height < 160) return null

                val longEdge = maxOf(width, height)
                val scaled = if (longEdge > config.maxLongEdge) {
                    val ratio = config.maxLongEdge.toFloat() / longEdge.toFloat()
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (width * ratio).toInt().coerceAtLeast(1),
                        (height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else bitmap

                try {
                    val encoded = JPEGFactory.createFromImage(document, scaled, config.jpegQuality)
                    val encodedLength = encoded.cosObject.length
                    if (encodedLength > 0 && encodedLength < streamLength) encoded else null
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
