package com.example.docdoom.presentation.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.content.Intent
import androidx.core.content.FileProvider
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.example.docdoom.data.pdf.PdfCompressionEngine
import com.example.docdoom.data.pdf.PdfCompressionLevel
import com.example.docdoom.data.pdf.PdfCompressionResult
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.UUID

private val PdfCompressGreen = Color(0xFF16856B)
private val PdfCompressMuted = Color(0xFF687570)

@Composable
fun CompressPdfScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var sourceBytes by remember { mutableLongStateOf(0L) }
    var level by remember { mutableStateOf(PdfCompressionLevel.MEDIUM) }
    var result by remember { mutableStateOf<PdfCompressionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var saveSource by remember { mutableStateOf<File?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(result?.outputFile?.absolutePath) {
        previewBitmap?.recycle()
        previewBitmap = null
        val file = result?.outputFile ?: return@LaunchedEffect
        previewBitmap = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (renderer.pageCount == 0) return@use null
                        renderer.openPage(0).use { page ->
                            val scale = (1200f / page.width).coerceAtMost(2f)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewBitmap?.recycle() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            sourceUri = uri
            val details = pdfDetails(context, uri)
            sourceName = details.first
            sourceBytes = details.second
            result?.outputFile?.delete()
            result = null
            status = ""
        }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = saveSource
        if (uri != null && file != null) {
            scope.launch {
                try {
                    withContextCompatIo {
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            file.inputStream().use { it.copyTo(output) }
                        } ?: error("Couldn't create the destination PDF.")
                    }
                    val providerName = withContext(Dispatchers.IO) {
                        pdfDetails(context, uri).first
                    }
                    val name = providerName
                        .takeIf { it.isNotBlank() && it != "document.pdf" }
                        ?: defaultCompressedName(sourceName.ifBlank { "Document.pdf" })
                    onSaved(uri, name, file.length())
                    status = "PDF saved and added to Recent Files."
                } catch (e: Exception) {
                    status = "Save failed: ${e.localizedMessage ?: "Please try again."}"
                }
            }
        }
    }

    fun shareCompressedPdf(file: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share compressed PDF"))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Compress PDF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF123B32)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Color(0xFF8BE0C2))
                Text("Reduce PDF file size", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Optimize the PDF locally on your device. Text and page layout are not rasterized.", color = Color(0xFFD0E6DF))
            }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (sourceUri == null) "Choose PDF" else "Choose another PDF")
        }
        if (sourceUri != null) {
            Text(sourceName, fontWeight = FontWeight.SemiBold)
            Text("Original size: ${formatPdfBytes(sourceBytes)}", color = PdfCompressMuted)
            Text("Compression level", fontWeight = FontWeight.Bold)
            listOf(
                PdfCompressionLevel.LOW to ("Low" to "Keeps more image detail. Larger output, better for zooming and printing."),
                PdfCompressionLevel.MEDIUM to ("Recommended" to "Balanced image resizing and JPEG compression."),
                PdfCompressionLevel.HIGH to ("High" to "Strong image reduction. Best for smaller files; fine details may soften.")
            ).forEach { (value, labels) ->
                Card(
                    onClick = { level = value; result?.outputFile?.delete(); result = null },
                    colors = CardDefaults.cardColors(
                        containerColor = if (level == value) Color(0xFF174B3F) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (level == value) androidx.compose.foundation.BorderStroke(1.dp, PdfCompressGreen) else null
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = level == value, onClick = { level = value; result?.outputFile?.delete(); result = null })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(labels.first, fontWeight = FontWeight.SemiBold,
                                color = if (level == value) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(labels.second,
                                color = if (level == value) Color(0xFFD0E6DF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val uri = sourceUri ?: return@Button
                    busy = true; status = ""; result?.outputFile?.delete(); result = null
                    scope.launch {
                        try {
                            val output = File(context.cacheDir, "docdoom_compressed_${UUID.randomUUID()}.pdf")
                            result = PdfCompressionEngine.compress(context, uri, level, output) { progress = it }
                            status = "Compression finished."
                        } catch (e: Exception) {
                            status = "Couldn't compress PDF: ${e.localizedMessage ?: "Please try another PDF."}"
                        } finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Optimizing…" else "Compress PDF") }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(progress, color = PdfCompressMuted)
        }
        result?.let { r ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF123B32))
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PDF compression complete", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Original: ${formatPdfBytes(r.originalBytes)}", color = Color(0xFFD0E6DF))
                    Text("Output: ${formatPdfBytes(r.outputBytes)}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (r.outputBytes < r.originalBytes) {
                        Text("${r.reductionPercent}% smaller · ${formatPdfBytes(r.savedBytes)} saved",
                            color = Color(0xFF8BE0C2), fontWeight = FontWeight.Bold)
                    } else if (r.outputBytes == r.originalBytes) {
                        Text("No size reduction. The PDF is already optimized.", color = Color(0xFFFFD58A))
                    } else {
                        Text("${formatPdfBytes(r.outputBytes - r.originalBytes)} larger (${percentLarger(r.originalBytes, r.outputBytes)}%).",
                            color = Color(0xFFFFA6A6), fontWeight = FontWeight.Bold)
                    }
                    Text("${r.pageCount} pages · Original remains untouched", color = Color(0xFFD0E6DF),
                        style = MaterialTheme.typography.bodySmall)
                    Text("Preview · Page 1", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "First page preview of compressed PDF",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(140.dp)
                                .background(Color(0xFF24564A), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Preparing PDF preview…", color = Color.White)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { saveSource = r.outputFile; saver.launch(defaultCompressedName(sourceName)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8BE0C2), contentColor = Color(0xFF10372E))
                        ) { Text("Save", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { shareCompressedPdf(r.outputFile) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Share", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = PdfCompressMuted)
    }
}

private fun pdfDetails(context: Context, uri: Uri): Pair<String, Long> {
    var name = "document.pdf"
    var size = 0L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst()) {
            name = it.getString(0) ?: name
            if (!it.isNull(1)) size = it.getLong(1)
        }
    }
    return name to size
}
private fun defaultCompressedName(name: String): String = name.substringBeforeLast('.', name) + "_compressed.pdf"
private fun formatPdfBytes(bytes: Long): String = DecimalFormat("#,##0.00").format(bytes / (1024.0 * 1024.0)) + " MB"
private fun percentLarger(original: Long, output: Long): Int = if (original <= 0) 0 else (((output - original) * 100.0) / original).toInt()

private suspend fun withContextCompatIo(block: suspend () -> Unit) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
