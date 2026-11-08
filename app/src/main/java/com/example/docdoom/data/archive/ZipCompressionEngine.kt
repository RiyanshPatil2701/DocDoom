package com.example.docdoom.data.archive
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext

enum class ArchiveCompressionLevel {
    FAST,
    NORMAL,
    MAXIMUM
}

data class ZipCompressionResult(
    val outputFile: File,
    val originalBytes: Long,
    val compressedBytes: Long,
    val fileCount: Int,
    val folderCount: Int,
    val passwordProtected: Boolean
) {
    val savedBytes: Long
        get() = (originalBytes - compressedBytes).coerceAtLeast(0L)

    val reductionPercent: Int
        get() = if (originalBytes <= 0L || compressedBytes >= originalBytes) {
            0
        } else {
            ((savedBytes * 100.0) / originalBytes).toInt()
        }
}

object ZipCompressionEngine {

    private data class CopySummary(
        val bytes: Long,
        val files: Int,
        val folders: Int
    )

    suspend fun createZip(
        context: Context,
        fileUris: List<Uri>,
        folderTreeUris: List<Uri>,
        outputFile: File,
        level: ArchiveCompressionLevel,
        password: String? = null,
        onProgress: (String) -> Unit = {}
    ): ZipCompressionResult = withContext(Dispatchers.IO) {

        require(fileUris.isNotEmpty() || folderTreeUris.isNotEmpty()) {
            "Please select at least one file or folder."
        }

        require(password.isNullOrBlank() || password.length >= 4) {
            "Password must contain at least 4 characters."
        }

        val appContext = context.applicationContext
        val workDirectory = File(
            appContext.cacheDir,
            "docdoom_zip_${System.nanoTime()}"
        )

        val stagingDirectory = File(workDirectory, "archive_contents")

        if (!stagingDirectory.mkdirs()) {
            throw IOException("Couldn't prepare the compression workspace.")
        }

        val encryptionPassword = password
            ?.takeIf { it.isNotBlank() }
            ?.toCharArray()

        try {
            var totalBytes = 0L
            var totalFiles = 0
            var totalFolders = 0

            onProgress("Preparing selected files and folders…")

            // Copy individual selected files into the staging directory.
            for (uri in fileUris) {
                coroutineContext.ensureActive()

                val source = DocumentFile.fromSingleUri(appContext, uri)
                    ?: throw IOException("Couldn't access a selected file.")

                if (!source.exists() || !source.isFile) {
                    throw IOException("A selected item is not a valid file.")
                }

                val fileName = safeName(source.name ?: "document")
                val destination = uniqueDestination(stagingDirectory, fileName)

                onProgress("Preparing $fileName…")

                val copiedBytes = copyDocumentFile(
                    appContext,
                    source,
                    destination
                )

                totalBytes += copiedBytes
                totalFiles++
            }

            // Copy selected folders recursively.
            for (treeUri in folderTreeUris) {
                coroutineContext.ensureActive()

                val sourceFolder = DocumentFile.fromTreeUri(
                    appContext,
                    treeUri
                ) ?: throw IOException("Couldn't access a selected folder.")

                if (!sourceFolder.exists() || !sourceFolder.isDirectory) {
                    throw IOException("A selected folder is unavailable.")
                }

                val folderName = safeName(sourceFolder.name ?: "Folder")
                val destinationFolder = uniqueDestination(
                    stagingDirectory,
                    folderName
                )

                if (!destinationFolder.mkdirs()) {
                    throw IOException("Couldn't prepare folder: $folderName")
                }

                onProgress("Preparing folder $folderName…")

                val summary = copyDirectory(
                    appContext,
                    sourceFolder,
                    destinationFolder,
                    onProgress
                )

                totalBytes += summary.bytes
                totalFiles += summary.files
                totalFolders += summary.folders + 1
            }

            coroutineContext.ensureActive()

            if (totalFiles == 0) {
                throw IOException("No files were found in the selected items.")
            }

            outputFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Couldn't prepare the output location.")
                }
            }

            if (outputFile.exists() && !outputFile.delete()) {
                throw IOException("Couldn't replace the temporary output file.")
            }

            onProgress("Creating ZIP archive…")

            val parameters = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE

                compressionLevel = when (level) {
                    ArchiveCompressionLevel.FAST ->
                        CompressionLevel.FASTEST

                    ArchiveCompressionLevel.NORMAL ->
                        CompressionLevel.NORMAL

                    ArchiveCompressionLevel.MAXIMUM ->
                        CompressionLevel.MAXIMUM
                }

                if (encryptionPassword != null) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }

            val zipFile = if (encryptionPassword != null) {
                ZipFile(outputFile, encryptionPassword)
            } else {
                ZipFile(outputFile)
            }

            // Add the contents of the staging directory without
            // introducing an extra temporary root folder in the ZIP.
            parameters.isIncludeRootFolder = false

            zipFile.addFolder(stagingDirectory, parameters)

            coroutineContext.ensureActive()

            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IOException("ZIP creation failed.")
            }

            onProgress("ZIP archive created successfully.")

            ZipCompressionResult(
                outputFile = outputFile,
                originalBytes = totalBytes,
                compressedBytes = outputFile.length(),
                fileCount = totalFiles,
                folderCount = totalFolders,
                passwordProtected = encryptionPassword != null
            )

        } catch (exception: Exception) {
            outputFile.delete()
            throw exception
        } finally {
            encryptionPassword?.fill('\u0000')
            workDirectory.deleteRecursively()
        }
    }

    private suspend fun copyDirectory(
        context: Context,
        sourceDirectory: DocumentFile,
        destinationDirectory: File,
        onProgress: (String) -> Unit
    ): CopySummary {
        var bytes = 0L
        var files = 0
        var folders = 0

        for (child in sourceDirectory.listFiles()) {
            coroutineContext.ensureActive()

            val childName = safeName(child.name ?: "item")
            val destination = uniqueDestination(
                destinationDirectory,
                childName
            )

            if (child.isDirectory) {
                if (!destination.mkdirs()) {
                    throw IOException("Couldn't create folder: $childName")
                }

                folders++

                val nested = copyDirectory(
                    context,
                    child,
                    destination,
                    onProgress
                )

                bytes += nested.bytes
                files += nested.files
                folders += nested.folders

            } else if (child.isFile) {
                onProgress("Copying $childName…")

                bytes += copyDocumentFile(
                    context,
                    child,
                    destination
                )

                files++
            }
        }

        return CopySummary(
            bytes = bytes,
            files = files,
            folders = folders
        )
    }

    private suspend fun copyDocumentFile(
        context: Context,
        source: DocumentFile,
        destination: File
    ): Long {
        coroutineContext.ensureActive()

        val input = context.contentResolver.openInputStream(source.uri)
            ?: throw IOException("Couldn't read ${source.name ?: "a selected file"}.")

        var copiedBytes = 0L

        input.use { sourceStream ->
            destination.outputStream().buffered().use { targetStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (true) {
                    coroutineContext.ensureActive()

                    val count = sourceStream.read(buffer)
                    if (count < 0) break

                    targetStream.write(buffer, 0, count)
                    copiedBytes += count
                }
            }
        }

        if (copiedBytes == 0L && source.length() > 0L) {
            throw IOException("Couldn't completely copy ${source.name ?: "a file"}.")
        }

        return copiedBytes
    }

    private fun uniqueDestination(
        parent: File,
        originalName: String
    ): File {
        val first = File(parent, originalName)
        if (!first.exists()) return first

        val extension = originalName.substringAfterLast(
            '.',
            ""
        ).takeIf { it.isNotEmpty() }

        val baseName = if (extension == null) {
            originalName
        } else {
            originalName.removeSuffix(".$extension")
        }

        var index = 2

        while (true) {
            val candidateName = if (extension == null) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }

            val candidate = File(parent, candidateName)

            if (!candidate.exists()) return candidate

            index++
        }
    }

    private fun safeName(name: String): String {
        val cleaned = name
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .trim('.')

        return cleaned.ifBlank { "unnamed" }
    }
}