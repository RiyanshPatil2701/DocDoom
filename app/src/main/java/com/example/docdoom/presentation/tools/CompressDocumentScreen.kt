package com.example.docdoom.presentation.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.docdoom.data.document.DocumentCompressionLevel
import com.example.docdoom.data.document.DocumentCompressionResult
import com.example.docdoom.data.document.OfficeDocumentCompressionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

private val OfficeGreen = Color(0xFF16856B)
private val OfficeDark = Color(0xFF202A27)
private val OfficeMuted = Color(0xFF687570)
private val OfficeMint = Color(0xFFE8F5F0)

@Composable
fun CompressDocumentScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceFile by remember { mutableStateOf<File?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var sourceBytes by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf(DocumentCompressionLevel.RECOMMENDED) }
    var result by remember { mutableStateOf<DocumentCompressionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var saveFile by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = ""
                result?.outputFile?.delete()
                result = null
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                        // Temporary access is sufficient because the file is copied immediately.
                    }

                    val details = queryOfficeDetails(context, uri)
                    val copied = withContext(Dispatchers.IO) {
                        File(context.cacheDir, "office_source_${System.nanoTime()}.bin").also { target ->
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                target.outputStream().buffered().use { output -> input.copyTo(output) }
                            } ?: throw IOException("Couldn't read the selected document.")
                        }
                    }
                    val detected = withContext(Dispatchers.IO) {
                        OfficeDocumentCompressionEngine.detectType(copied)
                    }
                    sourceFile?.delete()
                    sourceFile = copied
                    sourceUri = uri
                    sourceName = details.first.ifBlank { "document.${detected.extension}" }
                    sourceBytes = copied.length()
                    status = "Detected ${detected.displayName} (.${detected.extension})."
                } catch (error: Exception) {
                    status = error.localizedMessage ?: "Couldn't open this Office document."
                    sourceFile?.delete()
                    sourceFile = null
                    sourceUri = null
                } finally {
                    busy = false
                }
            }
        }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { destination ->
        val readyFile = saveFile
        if (destination != null && readyFile != null) {
            scope.launch {
                saving = true
                status = ""
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            readyFile.inputStream().buffered().use { input -> input.copyTo(output) }
                        } ?: throw IOException("Couldn't create the destination document.")
                    }
                    val displayName = queryOfficeDetails(context, destination).first
                        .ifBlank { compressedName(sourceName, result?.type?.extension ?: "docx") }
                    val size = queryOfficeDetails(context, destination).second
                        .takeIf { it > 0L } ?: readyFile.length()
                    onSaved(destination, displayName, size)
                    status = "Compressed document saved and added to Recent Files."
                } catch (error: Exception) {
                    status = "Couldn't save document: ${error.localizedMessage ?: "Please try again."}"
                } finally {
                    saving = false
                    saveFile = null
                }
            }
        } else {
            saveFile = null
            if (destination == null) status = "Saving cancelled."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack, enabled = !busy && !saving) {
                androidx.compose.material3.Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Compress Document",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OfficeDark
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF123B32)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = Color(0xFF8BE0C2)
                )
                Text("One tool for Word, Excel and PowerPoint", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Choose a document. DocDoom detects its format and optimizes it locally. Your original stays untouched.",
                    color = Color(0xFFD0E6DF),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && !saving
        ) {
            Text(if (sourceFile == null) "Choose document" else "Choose another document")
        }

        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = OfficeGreen)
                Text("  Checking document…", color = OfficeMuted)
            }
        }

        if (sourceFile != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = OfficeMint),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(sourceName, color = OfficeDark, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Original size: ${formatOfficeBytes(sourceBytes)}", color = OfficeMuted)
                    Text("Format detected automatically", color = OfficeGreen, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Compression level", fontWeight = FontWeight.Bold, color = OfficeDark)
            listOf(
                DocumentCompressionLevel.RECOMMENDED to ("Recommended" to "Balanced image optimization; keeps document dimensions and layout."),
                DocumentCompressionLevel.HIGH to ("High" to "Stronger JPEG image compression for smaller files.")
            ).forEach { (value, labels) ->
                Card(
                    onClick = {
                        level = value
                        result?.outputFile?.delete()
                        result = null
                        status = ""
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (level == value) Color(0xFFE3F3ED) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (level == value) androidx.compose.foundation.BorderStroke(1.dp, OfficeGreen) else null,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = level == value, onClick = {
                            level = value
                            result?.outputFile?.delete()
                            result = null
                            status = ""
                        })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(labels.first, fontWeight = FontWeight.SemiBold, color = OfficeDark)
                            Text(labels.second, color = OfficeMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val input = sourceFile ?: return@Button
                    busy = true
                    status = "Optimizing document package…"
                    result?.outputFile?.delete()
                    result = null
                    scope.launch {
                        try {
                            val compressed = withContext(Dispatchers.IO) {
                                val output = File(
                                    context.cacheDir,
                                    "docdoom_office_${System.nanoTime()}.zip"
                                )
                                OfficeDocumentCompressionEngine.compress(input, output, level)
                            }
                            result = compressed
                            status = if (compressed.isSmaller) {
                                "Compression analysis complete."
                            } else {
                                "This document is already optimized."
                            }
                        } catch (error: Exception) {
                            status = error.localizedMessage ?: "Document compression failed."
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = OfficeGreen)
            ) {
                if (busy) CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                ) else Text("Compress document")
            }
        }

        result?.let { compressed ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF123B32)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF8BE0C2)
                        )
                        Text("  Compression result", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("Original: ${formatOfficeBytes(compressed.originalBytes)}", color = Color(0xFFD0E6DF))
                    Text("Result: ${formatOfficeBytes(compressed.outputBytes)}", color = Color.White, fontWeight = FontWeight.Bold)
                    if (compressed.isSmaller) {
                        val percent = (compressed.savedBytes * 100.0 / compressed.originalBytes).toInt()
                        Text("$percent% smaller · ${formatOfficeBytes(compressed.savedBytes)} saved", color = Color(0xFF8BE0C2))
                    } else {
                        Text(
                            "This document was already optimized. No smaller output was found, so the original content was preserved.",
                            color = Color(0xFFD0E6DF)
                        )
                    }
                    Text(
                        "Detected: ${compressed.type.displayName}",
                        color = Color(0xFFD0E6DF),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Embedded images optimized: ${compressed.imagesOptimized}",
                        color = Color(0xFFD0E6DF),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            saveFile = compressed.outputFile
                            saver.launch(compressedName(sourceName, compressed.type.extension))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8BE0C2),
                            contentColor = Color(0xFF10372E)
                        )
                    ) {
                        if (saving) CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            color = Color(0xFF10372E),
                            strokeWidth = 2.dp
                        ) else Text("Save document")
                    }
                }
            }
        }

        if (status.isNotBlank()) {
            Text(status, color = OfficeMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun queryOfficeDetails(context: Context, uri: Uri): Pair<String, Long> {
    var name = ""
    var size = 0L
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(0).orEmpty()
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    return name to size
}

private fun compressedName(original: String, extension: String): String {
    val base = original.substringBeforeLast('.', original).ifBlank { "document" }
    return "${base}_compressed.$extension"
}

private fun formatOfficeBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
}
