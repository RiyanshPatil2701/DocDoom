package com.example.docdoom.presentation.tools

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.docdoom.data.pdf.PdfMergeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val MergeEmerald = Color(0xFF16856B)
private val MergeMint = Color(0xFFE8F5F0)
private val MergeBackground = Color(0xFFF8FAF9)
private val MergeText = Color(0xFF202A27)
private val MergeMuted = Color(0xFF687570)
private val MergeRed = Color(0xFFC62828)

private data class MergePdfFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long
)

@Composable
fun MergePdfScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedPdfs = remember { mutableStateListOf<MergePdfFile>() }

    var statusMessage by remember { mutableStateOf("") }
    var isMerging by remember { mutableStateOf(false) }
    var showLoading by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("Preparing PDFs…") }
    var completedFiles by remember { mutableStateOf(0) }
    var totalFiles by remember { mutableStateOf(0) }
    var generatedPdf by remember { mutableStateOf<File?>(null) }
    var pdfName by remember { mutableStateOf(defaultMergedPdfName()) }
    var isSaving by remember { mutableStateOf(false) }
    var saveSource by remember { mutableStateOf<File?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var savedName by remember { mutableStateOf<String?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (selectedPdfs.any { it.uri == uri }) {
                statusMessage = "That PDF is already in the list."
            } else {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some document providers do not offer persistable permissions.
                }
                val details = queryMergePdfDetails(context, uri)
                selectedPdfs.add(MergePdfFile(uri, details.first, details.second))
                statusMessage = ""
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        val source = saveSource
        if (destinationUri != null && source != null) {
            scope.launch {
                isSaving = true
                statusMessage = ""
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                            source.inputStream().buffered().use { input -> input.copyTo(output) }
                        } ?: throw IOException("Android couldn't open the selected save location.")
                    }
                    val displayName = queryMergeDisplayName(context, destinationUri)
                        ?: sanitizeMergedPdfName(pdfName)
                    val size = queryMergeFileSize(context, destinationUri) ?: source.length()
                    savedUri = destinationUri
                    savedName = displayName
                    onSaved(destinationUri, displayName, size)
                    statusMessage = "PDF saved successfully."
                } catch (error: Exception) {
                    statusMessage = "Couldn't save PDF: ${error.localizedMessage ?: "Please try again."}"
                } finally {
                    isSaving = false
                    saveSource = null
                }
            }
        } else {
            saveSource = null
            if (destinationUri == null) statusMessage = "Saving cancelled."
        }
    }

    val pdf = generatedPdf
    if (pdf != null) {
        MergePdfPreviewScreen(
            pdfFile = pdf,
            pdfName = pdfName,
            onPdfNameChange = { pdfName = it },
            isSaving = isSaving,
            statusMessage = statusMessage,
            savedName = savedName,
            onBack = {
                if (!isSaving) {
                    generatedPdf = null
                    savedUri = null
                    savedName = null
                    statusMessage = ""
                }
            },
            onSave = {
                saveSource = pdf
                saveLauncher.launch(sanitizeMergedPdfName(pdfName))
            },
            onShare = {
                savedUri?.let { shareMergedPdf(context, it) }
            }
        )
        return
    }

    if (isMerging && showLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MergeBackground)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MergeEmerald)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Merging your PDFs…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MergeText
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = progressMessage,
                    color = MergeMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (totalFiles > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "$completedFiles of $totalFiles files prepared",
                        color = MergeEmerald,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val moved = selectedPdfs.removeAt(from.index)
        selectedPdfs.add(to.index, moved)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MergeBackground)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !isMerging) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Merge PDF",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MergeText
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Combine multiple PDF files into one document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MergeMuted
        )
        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MergeMint),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MergeType,
                    contentDescription = null,
                    tint = MergeEmerald,
                    modifier = Modifier.size(36.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your PDFs, in your order", fontWeight = FontWeight.SemiBold, color = MergeText)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Files are merged in the order shown. Drag to rearrange them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MergeMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Selected PDFs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MergeText)
                Text(
                    "${selectedPdfs.size} ${if (selectedPdfs.size == 1) "file" else "files"} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MergeMuted
                )
            }
            OutlinedButton(
                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                enabled = !isMerging,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (selectedPdfs.isEmpty()) "Add PDF" else "Add another")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (selectedPdfs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MergeEmerald, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No PDFs selected yet", fontWeight = FontWeight.SemiBold, color = MergeText)
                    Spacer(Modifier.height(5.dp))
                    Text("Add your first PDF to get started.", color = MergeMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(selectedPdfs, key = { _, file -> file.uri.toString() }) { index, file ->
                    ReorderableItem(state = reorderableState, key = file.uri.toString()) { isDragging ->
                        Card(
                            modifier = Modifier.fillMaxWidth().longPressDraggableHandle(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDragging) MergeMint else Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", color = MergeEmerald, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
                                Icon(Icons.Outlined.Description, contentDescription = "PDF file", tint = MergeEmerald, modifier = Modifier.size(28.dp))
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(file.displayName, color = MergeText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatMergeFileSize(file.sizeBytes), color = MergeMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { selectedPdfs.remove(file) }, enabled = !isMerging) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove ${file.displayName}", tint = MergeRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(statusMessage, color = if (statusMessage.startsWith("Couldn't")) MergeRed else MergeMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!isMerging && selectedPdfs.size >= 2) {
                    val uriSnapshot = selectedPdfs.map { it.uri }
                    scope.launch {
                        isMerging = true
                        showLoading = false
                        progressMessage = "Preparing PDFs…"
                        completedFiles = 0
                        totalFiles = uriSnapshot.size
                        statusMessage = ""

                        val loadingDelay = launch {
                            delay(250)
                            if (isMerging) showLoading = true
                        }

                        try {
                            val output = File(
                                context.cacheDir,
                                "merged_pdfs/merged_${UUID.randomUUID()}.pdf"
                            )
                            val result = PdfMergeEngine.merge(
                                context = context,
                                sourceUris = uriSnapshot,
                                outputFile = output,
                                onProgress = { done, total, message ->
                                    scope.launch(Dispatchers.Main) {
                                        completedFiles = done
                                        totalFiles = total
                                        progressMessage = message
                                    }
                                }
                            )
                            loadingDelay.cancel()
                            generatedPdf = result
                            pdfName = defaultMergedPdfName()
                            savedUri = null
                            savedName = null
                        } catch (error: Exception) {
                            loadingDelay.cancel()
                            statusMessage = "Couldn't merge PDFs: ${error.localizedMessage ?: "Please try again."}"
                        } finally {
                            isMerging = false
                            showLoading = false
                        }
                    }
                }
            },
            enabled = selectedPdfs.size >= 2 && !isMerging,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isMerging) {
                CircularProgressIndicator(modifier = Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Merging PDFs…")
            } else {
                Icon(Icons.Outlined.MergeType, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Merge ${selectedPdfs.size} PDFs")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MergePdfPreviewScreen(
    pdfFile: File,
    pdfName: String,
    onPdfNameChange: (String) -> Unit,
    isSaving: Boolean,
    statusMessage: String,
    savedName: String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val pageCount = remember(pdfFile) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { descriptor -> PdfRenderer(descriptor).use { renderer -> renderer.pageCount } }
        } catch (_: Exception) { 0 }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MergeBackground).padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !isSaving) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to selected PDFs")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("PDF Preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MergeText)
                Text("$pageCount ${if (pageCount == 1) "page" else "pages"}", style = MaterialTheme.typography.bodySmall, color = MergeMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (pageCount > 0) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(pageCount) { pageIndex ->
                    MergePdfPagePreview(pdfFile = pdfFile, pageIndex = pageIndex)
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("The PDF preview couldn't be opened. Please try merging again.", color = MergeMuted)
            }
        }
        Text("PDF file name", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MergeText)
        OutlinedTextField(
            value = pdfName,
            onValueChange = onPdfNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving,
            placeholder = { Text("Enter a name for your merged PDF") },
            shape = RoundedCornerShape(12.dp)
        )
        Text("The .pdf extension will be added automatically.", color = MergeMuted, style = MaterialTheme.typography.bodySmall)
        if (savedName != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MergeMint)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MergeEmerald)
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PDF saved", fontWeight = FontWeight.SemiBold, color = MergeText)
                        Text(savedName, color = MergeMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(statusMessage, color = if (statusMessage.startsWith("Couldn't")) MergeRed else MergeEmerald, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSave, enabled = pageCount > 0 && !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Saving PDF…")
            } else {
                Text(if (savedName == null) "Save merged PDF" else "Save to another location")
            }
        }
        if (savedName != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShare, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Share PDF")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MergePdfPagePreview(pdfFile: File, pageIndex: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pdfFile.absolutePath, key2 = pageIndex) {
        value = withContext(Dispatchers.IO) {
            try {
                val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            val width = 1000
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rendered ->
                                page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("Page ${pageIndex + 1}", color = MergeMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
            if (bitmap != null) {
                Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Merged PDF page ${pageIndex + 1}", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.FillWidth)
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(MergeMint), contentAlignment = Alignment.Center) {
                    Text("Couldn't render this page", color = MergeMuted)
                }
            }
        }
    }
}

private fun queryMergePdfDetails(context: Context, uri: Uri): Pair<String, Long> {
    var name = "PDF document"
    var size = 0L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    return name to size
}

private fun queryMergeDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else null
    }
} catch (_: Exception) { null }

private fun queryMergeFileSize(context: Context, uri: Uri): Long? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        } else null
    }
} catch (_: Exception) { null }

private fun formatMergeFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Size unavailable"
    return if (bytes >= 1024L * 1024L) String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
    else String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
}

private fun defaultMergedPdfName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    return "Merged_Document_$timestamp"
}

private fun sanitizeMergedPdfName(input: String): String {
    val cleaned = input.trim().removeSuffix(".pdf")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
        .trim('.', ' ')
    return "${cleaned.ifBlank { defaultMergedPdfName() }}.pdf"
}

private fun shareMergedPdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "DocDoom merged PDF", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share merged PDF"))
    } catch (_: Exception) {
        // Keep the preview visible if no share target is available.
    }
}
