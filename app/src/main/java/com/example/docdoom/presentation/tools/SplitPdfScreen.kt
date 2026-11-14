package com.example.docdoom.presentation.tools

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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

private val SplitGreen = Color(0xFF16856B)
private val SplitPale = Color(0xFFE8F5F0)
private val SplitInk = Color(0xFF202A27)
private val SplitMuted = Color(0xFF687570)

private data class SplitPdfSource(
    val uri: Uri,
    val file: File,
    val name: String,
    val pageCount: Int
)

@Composable
fun SplitPdfScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedPages = remember { mutableStateListOf<Int>() }

    var source by remember { mutableStateOf<SplitPdfSource?>(null) }
    var pageRange by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingOutput by remember { mutableStateOf<File?>(null) }
    var pendingName by remember { mutableStateOf("Extracted_Pages.pdf") }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "Loading PDF…"
                try {
                    val loaded = withContext(Dispatchers.IO) { importPdfForSplit(context, uri) }
                    source?.file?.delete()
                    source = loaded
                    selectedPages.clear()
                    selectedPages.addAll(0 until loaded.pageCount)
                    pageRange = if (loaded.pageCount == 1) "1" else "1-${loaded.pageCount}"
                    status = "${loaded.pageCount} pages loaded"
                } catch (e: Exception) {
                    status = e.localizedMessage ?: "Couldn't open this PDF."
                } finally {
                    busy = false
                }
            }
        }
    }

    val saveExtractLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val output = pendingOutput
        if (destination != null && output != null) {
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                            output.inputStream().use { input -> input.copyTo(out) }
                        } ?: throw IOException("Couldn't create the destination PDF.")
                    }
                    val name = querySplitDisplayName(context, destination) ?: pendingName
                    onSaved(destination, name, output.length())
                    status = "PDF saved successfully"
                } catch (e: Exception) {
                    status = e.localizedMessage ?: "Couldn't save the extracted PDF."
                } finally {
                    output.delete()
                    pendingOutput = null
                    busy = false
                }
            }
        } else {
            output?.delete()
            pendingOutput = null
            busy = false
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { folderUri ->
        val current = source
        if (folderUri != null && current != null) {
            scope.launch {
                busy = true
                status = "Splitting pages…"
                var savedCount = 0
                try {
                    val outputs = withContext(Dispatchers.IO) {
                        splitEveryPageToFiles(context, current.file)
                    }
                    val folder = DocumentFile.fromTreeUri(context, folderUri)
                        ?: throw IOException("Couldn't access the selected folder.")
                    outputs.forEachIndexed { index, output ->
                        val outputName = "Page_%03d.pdf".format(Locale.US, index + 1)
                        val destination = folder.createFile("application/pdf", outputName)
                            ?: throw IOException("Couldn't create $outputName in that folder.")
                        context.contentResolver.openOutputStream(destination.uri, "w")?.use { out ->
                            output.inputStream().use { input -> input.copyTo(out) }
                        } ?: throw IOException("Couldn't write $outputName.")
                        onSaved(destination.uri, outputName, output.length())
                        savedCount++
                        output.delete()
                    }
                    status = "Done! $savedCount separate PDFs saved to the selected folder."
                } catch (e: Exception) {
                    status = if (savedCount > 0) {
                        "$savedCount PDFs saved. ${e.localizedMessage ?: "The remaining pages couldn't be saved."}"
                    } else {
                        e.localizedMessage ?: "Couldn't split this PDF."
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF7F9F8))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Split PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SplitInk)
                Text("Extract pages or save each page separately", style = MaterialTheme.typography.bodySmall, color = SplitMuted)
            }
            Icon(Icons.Outlined.ContentCut, contentDescription = null, tint = SplitGreen, modifier = Modifier.size(26.dp))
        }

        if (source == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = SplitGreen, modifier = Modifier.size(58.dp))
                    Text("Choose a PDF to get started", fontWeight = FontWeight.SemiBold)
                    Text("Select pages visually or enter a page range.", color = SplitMuted)
                    Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Outlined.FolderOpen, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Select PDF")
                    }
                }
            }
        } else {
            val current = source!!
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(current.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${current.pageCount} pages", color = SplitMuted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) { Text("Change PDF") }
            }

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select pages", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedPages.size} of ${current.pageCount} selected", color = SplitMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            selectedPages.clear()
                            selectedPages.addAll(0 until current.pageCount)
                            pageRange = if (current.pageCount == 1) "1" else "1-${current.pageCount}"
                        }) { Text("Select all") }
                        TextButton(onClick = { selectedPages.clear(); pageRange = "" }) { Text("Clear") }
                    }
                    OutlinedTextField(
                        value = pageRange,
                        onValueChange = { pageRange = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Page range") },
                        placeholder = { Text("Example: 1-3, 5, 8-10") },
                        singleLine = true
                    )
                    Text("Tap Apply to select the pages in your range.", color = SplitMuted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = {
                            try {
                                val parsed = parseSplitPageRange(pageRange, current.pageCount)
                                selectedPages.clear()
                                selectedPages.addAll(parsed)
                                status = "${parsed.size} pages selected"
                            } catch (e: Exception) {
                                status = e.localizedMessage ?: "Invalid page range."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Apply page range") }
                }
            }

            Text("Tap page thumbnails to select or deselect", color = SplitMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items((0 until current.pageCount).toList(), key = { it }) { pageIndex ->
                    SplitPageThumbnail(
                        file = current.file,
                        pageIndex = pageIndex,
                        selected = selectedPages.contains(pageIndex),
                        onClick = {
                            if (selectedPages.contains(pageIndex)) selectedPages.remove(pageIndex)
                            else selectedPages.add(pageIndex)
                            selectedPages.sort()
                            pageRange = selectedPagesToRange(selectedPages.toList())
                        }
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (selectedPages.isEmpty()) {
                            status = "Select at least one page."
                        } else {
                            scope.launch {
                                busy = true
                                status = "Preparing extracted PDF…"
                                try {
                                    val output = withContext(Dispatchers.IO) {
                                        extractSplitPages(context, current.file, selectedPages.toList())
                                    }
                                    pendingOutput = output
                                    pendingName = "Extracted_Pages.pdf"
                                    saveExtractLauncher.launch(pendingName)
                                } catch (e: Exception) {
                                    status = e.localizedMessage ?: "Couldn't extract the selected pages."
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy && selectedPages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.PictureAsPdf, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Extract selected pages")
                }
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Outlined.ContentCut, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Split every page into separate PDFs")
                }
            }
        }

        if (status.isNotBlank()) {
            Text(
                status,
                color = when {
                    status.startsWith("Couldn't") || status.startsWith("Invalid") || status.startsWith("Enter") || status.startsWith("Page ") -> MaterialTheme.colorScheme.error
                    status.startsWith("Done") || status.startsWith("PDF saved") || status.endsWith("loaded") -> SplitGreen
                    else -> SplitMuted
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SplitPageThumbnail(
    file: File,
    pageIndex: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = file.absolutePath, key2 = pageIndex) {
        value = withContext(Dispatchers.IO) {
            try {
                renderSplitThumbnail(file, pageIndex)
            } catch (_: Exception) {
                null
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) SplitPale else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) SplitGreen else Color(0xFFE3E9E6))
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(142.dp).background(Color(0xFFF1F4F2), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(bitmap!!.asImageBitmap(), contentDescription = "Page ${pageIndex + 1}", modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                } else {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                if (selected) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = SplitGreen, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
                }
            }
            Text("Page ${pageIndex + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 7.dp, bottom = 2.dp))
            Text(if (selected) "Selected" else "Tap to select", color = SplitMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private suspend fun importPdfForSplit(context: Context, uri: Uri): SplitPdfSource {
    val directory = File(context.cacheDir, "docdoom_split").apply { mkdirs() }
    val file = File(directory, "source_${UUID.randomUUID()}.pdf")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    } ?: throw IOException("Android couldn't open the selected PDF.")
    if (file.length() < 5L) {
        file.delete()
        throw IOException("The selected file is empty or isn't a valid PDF.")
    }
    val pageCount = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
    }
    if (pageCount <= 0) {
        file.delete()
        throw IOException("This PDF doesn't contain any pages.")
    }
    val name = querySplitDisplayName(context, uri) ?: "Selected_Document.pdf"
    return SplitPdfSource(uri, file, name, pageCount)
}

private fun renderSplitThumbnail(file: File, pageIndex: Int): Bitmap {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) throw IOException("Page no longer exists.")
            renderer.openPage(pageIndex).use { page ->
                val width = 420
                val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private fun extractSplitPages(context: Context, sourceFile: File, pageIndexes: List<Int>): File {
    if (pageIndexes.isEmpty()) throw IOException("Select at least one page.")
    PDFBoxResourceLoader.init(context.applicationContext)
    val output = PDDocument()
    try {
        val target = File(sourceFile.parentFile, "extract_${UUID.randomUUID()}.pdf")
        PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { source ->
            pageIndexes.sorted().forEach { index ->
                if (index !in 0 until source.numberOfPages) {
                    throw IOException("Invalid page selection.")
                }
                output.importPage(source.getPage(index))
            }

            // Imported pages can still reference streams owned by the source document.
            // Save the output BEFORE closing the source PDDocument.
            output.save(target)
        }
        return target
    } catch (e: Exception) {
        throw IOException(e.localizedMessage ?: "PDF extraction failed.", e)
    } finally {
        output.close()
    }
}

private fun splitEveryPageToFiles(context: Context, sourceFile: File): List<File> {
    PDFBoxResourceLoader.init(context.applicationContext)
    val outputs = mutableListOf<File>()
    PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { source ->
        for (index in 0 until source.numberOfPages) {
            val output = PDDocument()
            try {
                output.importPage(source.getPage(index))
                val target = File(sourceFile.parentFile, "page_${index + 1}_${UUID.randomUUID()}.pdf")
                output.save(target)
                outputs += target
            } finally {
                output.close()
            }
        }
    }
    return outputs
}

private fun parseSplitPageRange(value: String, pageCount: Int): List<Int> {
    if (value.isBlank()) throw IllegalArgumentException("Enter a page range, such as 1-3, 5.")
    val pages = sortedSetOf<Int>()
    value.split(",").forEach { token ->
        val part = token.trim()
        if (part.isEmpty()) throw IllegalArgumentException("Check the page range format.")
        val range = part.split("-").map { it.trim() }
        if (range.size == 1) {
            val page = range[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid page number: $part")
            if (page !in 1..pageCount) throw IllegalArgumentException("Page $page is outside 1-$pageCount.")
            pages += page - 1
        } else if (range.size == 2) {
            val first = range[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid range: $part")
            val last = range[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid range: $part")
            if (first !in 1..pageCount || last !in 1..pageCount || first > last) {
                throw IllegalArgumentException("Invalid range: $part. Use pages 1-$pageCount.")
            }
            (first..last).forEach { pages += it - 1 }
        } else {
            throw IllegalArgumentException("Invalid range: $part")
        }
    }
    return pages.toList()
}

private fun selectedPagesToRange(indexes: List<Int>): String {
    if (indexes.isEmpty()) return ""
    val sorted = indexes.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var previous = start
    for (index in sorted.drop(1)) {
        if (index == previous + 1) {
            previous = index
        } else {
            parts += if (start == previous) "${start + 1}" else "${start + 1}-${previous + 1}"
            start = index
            previous = index
        }
    }
    parts += if (start == previous) "${start + 1}" else "${start + 1}-${previous + 1}"
    return parts.joinToString(", ")
}

private fun querySplitDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
    }
} catch (_: Exception) {
    null
}
