
package com.example.docdoom.data.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

object PdfMergeEngine {

    suspend fun merge(
        context: Context,
        sourceUris: List<Uri>,
        outputFile: File,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {

        require(sourceUris.size >= 2) {
            "Select at least two PDF files to merge."
        }

        val appContext = context.applicationContext

        PDFBoxResourceLoader.init(appContext)

        val workingDirectory = File(
            appContext.cacheDir,
            "pdf_merge_work"
        )

        if (workingDirectory.exists()) {
            workingDirectory.deleteRecursively()
        }

        if (!workingDirectory.mkdirs()) {
            throw IOException("Couldn't prepare temporary storage.")
        }

        try {
            val preparedFiles = mutableListOf<File>()

            sourceUris.forEachIndexed { index, uri ->

                coroutineContext.ensureActive()

                onProgress(
                    index,
                    sourceUris.size,
                    "Preparing PDF ${index + 1} of ${sourceUris.size}..."
                )

                val inputFile = File(
                    workingDirectory,
                    "source_${index + 1}.pdf"
                )

                val inputStream =
                    appContext.contentResolver.openInputStream(uri)
                        ?: throw IOException(
                            "Couldn't open PDF ${index + 1}."
                        )

                inputStream.use { input ->
                    inputFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }

                if (inputFile.length() == 0L) {
                    throw IOException(
                        "PDF ${index + 1} is empty."
                    )
                }

                preparedFiles.add(inputFile)

                onProgress(
                    index + 1,
                    sourceUris.size,
                    "Prepared ${index + 1} of ${sourceUris.size} PDFs"
                )
            }

            coroutineContext.ensureActive()

            onProgress(
                sourceUris.size,
                sourceUris.size,
                "Combining PDF pages..."
            )

            outputFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Couldn't create output folder.")
                }
            }

            if (outputFile.exists() && !outputFile.delete()) {
                throw IOException("Couldn't prepare the output file.")
            }

            val merger = PDFMergerUtility()

            preparedFiles.forEach { file ->
                merger.addSource(file)
            }

            merger.destinationFileName = outputFile.absolutePath

            merger.mergeDocuments(
                MemoryUsageSetting.setupTempFileOnly()
            )

            coroutineContext.ensureActive()

            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IOException("The merged PDF couldn't be created.")
            }

            onProgress(
                sourceUris.size,
                sourceUris.size,
                "Merge complete"
            )

            outputFile

        } catch (error: Exception) {
            outputFile.delete()
            throw error

        } finally {
            workingDirectory.deleteRecursively()
        }
    }
}
