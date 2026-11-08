package com.example.docdoom.presentation.tools
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.docdoom.data.archive.ArchiveCompressionLevel
import com.example.docdoom.data.archive.ZipCompressionEngine
import com.example.docdoom.data.archive.ZipCompressionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.util.UUID

private val ArchiveGreen = Color(0xFF16856B)
private val ArchiveMuted = Color(0xFF687570)

@Composable
fun CompressFileScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedFolders by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var level by remember { mutableStateOf(ArchiveCompressionLevel.NORMAL) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ZipCompressionResult?>(null) }
    var saveSource by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            selectedFiles = (selectedFiles + uris).distinct()
            result?.outputFile?.delete()
            result = null
            status = ""
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            selectedFolders = (selectedFolders + uri).distinct()
            result?.outputFile?.delete()
            result = null
            status = ""
        }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val file = saveSource

        if (uri != null && file != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        } ?: error("Couldn't create the ZIP destination.")
                    }

                    val providerName = queryArchiveDisplayName(context, uri)
                    val name = providerName
                        ?.takeIf { it.isNotBlank() }
                        ?: "DocDoom_Archive.zip"

                    onSaved(uri, name, file.length())
                    status = "ZIP saved and added to Recent Files."
                } catch (e: Exception) {
                    status = "Save failed: ${e.localizedMessage ?: "Please try again."}"
                }
            }
        }
    }

    fun shareZip(file: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "Share ZIP archive")
        )
    }

    val canCreate = selectedFiles.isNotEmpty() || selectedFolders.isNotEmpty()
    val passwordValid = !usePassword ||
            (password.length >= 4 && password == confirmPassword)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Compress File",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF123B32)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.FolderZip,
                    contentDescription = null,
                    tint = Color(0xFF8BE0C2)
                )
                Text(
                    "Create a ZIP archive",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Combine multiple files and folders into one ZIP. " +
                            "Your original files will remain untouched.",
                    color = Color(0xFFD0E6DF)
                )
            }
        }

        Text("1. Select content", fontWeight = FontWeight.Bold)

        OutlinedButton(
            onClick = {
                filePicker.launch(arrayOf("*/*"))
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose Files")
        }

        OutlinedButton(
            onClick = {
                folderPicker.launch(null)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose Folder")
        }

        if (selectedFiles.isNotEmpty() || selectedFolders.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${selectedFiles.size} file(s) · ${selectedFolders.size} folder(s) selected",
                        fontWeight = FontWeight.SemiBold
                    )

                    selectedFiles.take(5).forEach {
                        Text(
                            "• ${it.lastPathSegment?.substringAfterLast('/') ?: "Selected file"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    selectedFolders.take(5).forEach {
                        Text(
                            "• Folder: ${it.lastPathSegment?.substringAfterLast('/') ?: "Selected folder"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (selectedFiles.size + selectedFolders.size > 10) {
                        Text(
                            "Showing first 5 files and first 5 folders.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TextButton(
                        onClick = {
                            selectedFiles = emptyList()
                            selectedFolders = emptyList()
                            result?.outputFile?.delete()
                            result = null
                            status = ""
                        },
                        enabled = !busy
                    ) {
                        Text("Clear selection")
                    }
                }
            }
        }

        HorizontalDivider()

        Text("2. Compression level", fontWeight = FontWeight.Bold)

        listOf(
            ArchiveCompressionLevel.FAST to
                    ("Fast" to "Faster ZIP creation."),
            ArchiveCompressionLevel.NORMAL to
                    ("Normal · Recommended" to "Balanced speed and compression."),
            ArchiveCompressionLevel.MAXIMUM to
                    ("Maximum" to "Slower processing; attempts stronger compression.")
        ).forEach { (value, labels) ->
            Card(
                onClick = {
                    level = value
                    result?.outputFile?.delete()
                    result = null
                },
                enabled = !busy,
                colors = CardDefaults.cardColors(
                    containerColor = if (level == value) {
                        Color(0xFFE0F2EB)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                border = if (level == value) {
                    androidx.compose.foundation.BorderStroke(1.dp, ArchiveGreen)
                } else {
                    null
                }
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = level == value,
                        onClick = {
                            level = value
                            result?.outputFile?.delete()
                            result = null
                        },
                        enabled = !busy
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(labels.first, fontWeight = FontWeight.SemiBold)
                        Text(
                            labels.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = ArchiveMuted
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Text("3. Password protection", fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = usePassword,
                onCheckedChange = {
                    usePassword = it
                    password = ""
                    confirmPassword = ""
                    result?.outputFile?.delete()
                    result = null
                },
                enabled = !busy
            )
            Text("Protect ZIP with AES-256 password")
        }

        if (usePassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (minimum 4 characters)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )

            if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Text(
                    "Passwords do not match.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Button(
            onClick = {
                if (!canCreate || !passwordValid) return@Button

                busy = true
                status = ""
                progress = "Starting…"
                result?.outputFile?.delete()
                result = null

                scope.launch {
                    try {
                        val output = File(
                            context.cacheDir,
                            "docdoom_archive_${UUID.randomUUID()}.zip"
                        )

                        result = ZipCompressionEngine.createZip(
                            context = context,
                            fileUris = selectedFiles,
                            folderTreeUris = selectedFolders,
                            outputFile = output,
                            level = level,
                            password = if (usePassword) password else null,
                            onProgress = { progress = it }
                        )

                        status = "ZIP creation finished."
                    } catch (e: Exception) {
                        status = "Couldn't create ZIP: ${
                            e.localizedMessage ?: "Please try again."
                        }"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = canCreate && passwordValid && !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = ArchiveGreen
            )
        ) {
            Text(if (busy) "Creating ZIP…" else "Create ZIP")
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(progress, color = ArchiveMuted)
        }

        result?.let { archive ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF123B32)
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "ZIP archive created",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Original size: ${formatArchiveBytes(archive.originalBytes)}",
                        color = Color(0xFFD0E6DF)
                    )
                    Text(
                        "ZIP size: ${formatArchiveBytes(archive.compressedBytes)}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )

                    when {
                        archive.compressedBytes < archive.originalBytes -> {
                            Text(
                                "${archive.reductionPercent}% smaller · " +
                                        "${formatArchiveBytes(archive.savedBytes)} saved",
                                color = Color(0xFF8BE0C2),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        archive.compressedBytes == archive.originalBytes -> {
                            Text(
                                "No size reduction.",
                                color = Color(0xFFFFD58A)
                            )
                        }
                        else -> {
                            Text(
                                "${formatArchiveBytes(archive.compressedBytes - archive.originalBytes)} larger. " +
                                        "These files may already be compressed.",
                                color = Color(0xFFFFA6A6)
                            )
                        }
                    }

                    Text(
                        "${archive.fileCount} files · ${archive.folderCount} folders",
                        color = Color(0xFFD0E6DF)
                    )

                    Text(
                        if (archive.passwordProtected) {
                            "AES-256 password protection enabled."
                        } else {
                            "No password protection."
                        },
                        color = Color(0xFFD0E6DF),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                saveSource = archive.outputFile
                                saver.launch("DocDoom_Archive.zip")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8BE0C2),
                                contentColor = Color(0xFF10372E)
                            )
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            onClick = { shareZip(archive.outputFile) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text("Share")
                        }
                    }
                }
            }
        }

        if (status.isNotBlank()) {
            Text(status, color = ArchiveMuted)
        }

        Text(
            "Note: ZIP compression may not reduce already-compressed files such as JPG, MP4 or existing ZIP archives.",
            style = MaterialTheme.typography.bodySmall,
            color = ArchiveMuted
        )
    }
}

private fun queryArchiveDisplayName(
    context: android.content.Context,
    uri: android.net.Uri
): String? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatArchiveBytes(bytes: Long): String {
    return if (bytes < 1024L * 1024L) {
        DecimalFormat("#,##0.0").format(bytes / 1024.0) + " KB"
    } else {
        DecimalFormat("#,##0.00").format(
            bytes / (1024.0 * 1024.0)
        ) + " MB"
    }
}