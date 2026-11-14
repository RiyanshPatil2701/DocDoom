package com.example.docdoom.presentation.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

private enum class SignPageMode(val label: String) {
    CURRENT("Selected page"), ALL("All pages"), CUSTOM("Custom pages")
}

@Composable
fun SignPdfScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var pageCount by remember { mutableStateOf(0) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signatureSource by remember { mutableStateOf("Draw your signature below") }
    var pageNumber by remember { mutableStateOf("1") }
    var customPages by remember { mutableStateOf("1, 3, 5-8") }
    var pageMode by remember { mutableStateOf(SignPageMode.ALL) }
    var xPosition by remember { mutableFloatStateOf(0.62f) }
    var yPosition by remember { mutableFloatStateOf(0.12f) }
    var signatureWidth by remember { mutableFloatStateOf(0.28f) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var outputFile by remember { mutableStateOf<File?>(null) }

    // Store strokes in normalized 0..1 coordinates so the saved signature is crisp
    // and independent of the phone's display density or signature-pad size.
    val strokes = remember { mutableStateListOf<List<Offset>>() }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) { copyUriToCache(context, uri, "source") }
                    val count = withContext(Dispatchers.IO) { countPdfPages(imported) }
                    sourceUri = uri
                    sourceName = queryDisplayName(context, uri) ?: "Document.pdf"
                    pageCount = count
                    pageNumber = "1"
                    outputFile?.delete()
                    outputFile = null
                    status = "PDF selected · $count pages"
                    imported.delete()
                } catch (e: Exception) {
                    status = "Couldn't open PDF: ${e.localizedMessage ?: "Please choose another file."}"
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                    if (bitmap != null) {
                        signatureBitmap?.recycle()
                        signatureBitmap = bitmap
                        strokes.clear()
                        signatureSource = "Signature image imported"
                        status = "Signature image ready."
                    } else status = "Couldn't read that image. Choose a PNG or JPG."
                } catch (e: Exception) {
                    status = e.localizedMessage ?: "Couldn't import the signature image."
                }
            }
        }
    }

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val generated = outputFile
        if (destination != null && generated != null && generated.exists()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                            generated.inputStream().use { input -> input.copyTo(out) }
                        } ?: throw IOException("Couldn't open the destination file.")
                    }
                    val savedName = queryDisplayName(context, destination)
                        ?: "Signed_${sourceName.substringBeforeLast('.')}.pdf"
                    onSaved(destination, savedName, generated.length())
                    status = "Signed PDF saved and added to DocDoom."
                } catch (e: Exception) {
                    status = "Save failed: ${e.localizedMessage ?: "Please try again."}"
                } finally {
                    generated.delete()
                    outputFile = null
                    busy = false
                }
            }
        } else {
            generated?.delete()
            outputFile = null
            busy = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            signatureBitmap?.recycle()
            outputFile?.delete()
        }
    }

    fun makeSignatureBitmap(): Bitmap? {
        signatureBitmap?.let { return it }
        if (strokes.isEmpty()) return null
        val bitmapWidth = 1600
        val bitmapHeight = 520
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 11f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { points ->
            if (points.isEmpty()) return@forEach
            val path = AndroidPath()
            val first = points.first()
            path.moveTo(first.x * bitmapWidth, first.y * bitmapHeight)
            if (points.size == 1) {
                canvas.drawCircle(first.x * bitmapWidth, first.y * bitmapHeight, 5.5f, paint)
            } else if (points.size == 2) {
                path.lineTo(points[1].x * bitmapWidth, points[1].y * bitmapHeight)
                canvas.drawPath(path, paint)
            } else {
                for (index in 1 until points.lastIndex) {
                    val current = points[index]
                    val next = points[index + 1]
                    val midX = (current.x + next.x) * 0.5f * bitmapWidth
                    val midY = (current.y + next.y) * 0.5f * bitmapHeight
                    path.quadTo(current.x * bitmapWidth, current.y * bitmapHeight, midX, midY)
                }
                val last = points.last()
                path.lineTo(last.x * bitmapWidth, last.y * bitmapHeight)
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }

    fun createSignedPdf() {
        val uri = sourceUri
        if (uri == null) {
            status = "Select a PDF first."
            return
        }
        val pageIndexes = try {
            when (pageMode) {
                SignPageMode.ALL -> (0 until pageCount).toList()
                SignPageMode.CURRENT -> {
                    val page = pageNumber.toIntOrNull()
                        ?: throw IllegalArgumentException("Enter a valid page number.")
                    if (page !in 1..pageCount) throw IllegalArgumentException("Page must be between 1 and $pageCount.")
                    listOf(page - 1)
                }
                SignPageMode.CUSTOM -> parseSignPageRange(customPages, pageCount)
            }
        } catch (e: Exception) {
            status = e.localizedMessage ?: "Check the page selection."
            return
        }
        val sign = makeSignatureBitmap()
        if (sign == null) {
            status = "Please hand-draw or import your signature first."
            return
        }
        val isImportedBitmap = signatureBitmap != null
        busy = true
        status = "Applying signature to ${pageIndexes.size} page(s)…"
        scope.launch {
            try {
                val generated = withContext(Dispatchers.IO) {
                    val source = copyUriToCache(context, uri, "source_process")
                    try {
                        createSignedPdfFile(context, source, sign, pageIndexes, xPosition, yPosition, signatureWidth)
                    } finally {
                        source.delete()
                    }
                }
                outputFile?.delete()
                outputFile = generated
                status = "Signature applied to ${pageIndexes.size} page(s). Choose where to save."
                savePicker.launch("Signed_${sourceName.substringBeforeLast('.')}.pdf")
            } catch (e: Exception) {
                status = "Signing failed: ${e.localizedMessage ?: "Please try again."}"
                busy = false
            } finally {
                if (!isImportedBitmap && !sign.isRecycled) sign.recycle()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Sign PDF", style = MaterialTheme.typography.titleLarge)
                Text("Create a natural handwritten signature and apply it to PDF pages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Choose PDF", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (sourceUri == null) "Select PDF" else "Change PDF")
                }
                if (sourceUri != null) {
                    Text(sourceName, style = MaterialTheme.typography.bodyMedium)
                    Text("$pageCount pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Hand-draw your signature", style = MaterialTheme.typography.titleMedium)
                Text("Use your finger or stylus. Smooth rounded strokes are kept on a transparent background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(168.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    signatureBitmap?.recycle()
                                    signatureBitmap = null
                                    val normalized = Offset(
                                        (start.x / size.width).coerceIn(0f, 1f),
                                        (start.y / size.height).coerceIn(0f, 1f)
                                    )
                                    strokes.add(listOf(normalized))
                                    signatureSource = "Handwritten signature"
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (strokes.isNotEmpty()) {
                                        val normalized = Offset(
                                            (change.position.x / size.width).coerceIn(0f, 1f),
                                            (change.position.y / size.height).coerceIn(0f, 1f)
                                        )
                                        val last = strokes.last()
                                        strokes[strokes.lastIndex] = last + normalized
                                    }
                                }
                            )
                        }
                    ) {
                        // Imported image is previewed without distorting its aspect ratio.
                        signatureBitmap?.let { bitmap ->
                            val image = bitmap.asImageBitmap()
                            val scale = minOf(size.width / image.width, size.height / image.height)
                            val w = (image.width * scale).toInt().coerceAtLeast(1)
                            val h = (image.height * scale).toInt().coerceAtLeast(1)
                            drawImage(image, dstSize = IntSize(w, h), dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()))
                        }
                        strokes.forEach { points ->
                            if (points.isEmpty()) return@forEach
                            val path = Path().apply {
                                moveTo(points.first().x * size.width, points.first().y * size.height)
                                if (points.size == 1) lineTo(points.first().x * size.width + 0.1f, points.first().y * size.height + 0.1f)
                                else if (points.size == 2) lineTo(points[1].x * size.width, points[1].y * size.height)
                                else for (index in 1 until points.lastIndex) {
                                    val current = points[index]
                                    val next = points[index + 1]
                                    quadraticTo(
                                        current.x * size.width, current.y * size.height,
                                        (current.x + next.x) * 0.5f * size.width,
                                        (current.y + next.y) * 0.5f * size.height
                                    )
                                }
                                if (points.size > 2) lineTo(points.last().x * size.width, points.last().y * size.height)
                            }
                            drawPath(
                                path, Color(0xFF101820),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.4.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                            )
                        }
                    }
                }
                Text(signatureSource, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        strokes.clear()
                        signatureBitmap?.recycle()
                        signatureBitmap = null
                        signatureSource = "Draw your signature below"
                        status = "Signature pad cleared."
                    }, modifier = Modifier.weight(1f), enabled = !busy) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Clear")
                    }
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/*")) }, modifier = Modifier.weight(1f), enabled = !busy) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Import image")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Where should the signature go?", style = MaterialTheme.typography.titleMedium)
                SignPageMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pageMode == mode, onClick = { pageMode = mode }, enabled = pageCount > 0 && !busy)
                        Text(mode.label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                when (pageMode) {
                    SignPageMode.CURRENT -> OutlinedTextField(
                        value = pageNumber,
                        onValueChange = { pageNumber = it.filter(Char::isDigit).take(6) },
                        label = { Text("Page number (1–$pageCount)") },
                        enabled = pageCount > 0 && !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SignPageMode.CUSTOM -> OutlinedTextField(
                        value = customPages,
                        onValueChange = { customPages = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '-' || ch.isWhitespace() }.take(120) },
                        label = { Text("Page numbers / ranges") },
                        placeholder = { Text("Example: 1, 3, 5-8") },
                        enabled = pageCount > 0 && !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SignPageMode.ALL -> Text("The same signature will be placed on all $pageCount pages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Horizontal position · ${(xPosition * 100).toInt()}% across available page area")
                Slider(value = xPosition, onValueChange = { xPosition = it }, valueRange = 0f..1f, enabled = pageCount > 0 && !busy)
                Text("Vertical position · ${(yPosition * 100).toInt()}% from top")
                Slider(value = yPosition, onValueChange = { yPosition = it }, valueRange = 0f..1f, enabled = pageCount > 0 && !busy)
                Text("Signature width · ${(signatureWidth * 100).toInt()}% of page width")
                Slider(value = signatureWidth, onValueChange = { signatureWidth = it }, valueRange = 0.10f..0.65f, enabled = pageCount > 0 && !busy)
                Text("The same size and placement are used on every selected page. Review the saved PDF before sharing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(onClick = { createSignedPdf() }, enabled = !busy && sourceUri != null && pageCount > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Brush, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (busy) "Applying signature…" else "Apply signature & save PDF")
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun parseSignPageRange(value: String, pageCount: Int): List<Int> {
    if (value.isBlank()) throw IllegalArgumentException("Enter page numbers, for example 1, 3, 5-8.")
    val pages = sortedSetOf<Int>()
    value.split(',').forEach { token ->
        val part = token.trim()
        if (part.isEmpty()) throw IllegalArgumentException("Check the page range format.")
        val bounds = part.split('-').map { it.trim() }
        if (bounds.size == 1) {
            val page = bounds[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid page: $part")
            if (page !in 1..pageCount) throw IllegalArgumentException("Page $page is outside 1-$pageCount.")
            pages += page - 1
        } else if (bounds.size == 2) {
            val first = bounds[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid range: $part")
            val last = bounds[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid range: $part")
            if (first !in 1..pageCount || last !in 1..pageCount || first > last) throw IllegalArgumentException("Invalid range: $part. Use pages 1-$pageCount.")
            (first..last).forEach { pages += it - 1 }
        } else throw IllegalArgumentException("Invalid range: $part")
    }
    return pages.toList()
}

private fun copyUriToCache(context: Context, uri: Uri, prefix: String): File {
    val folder = File(context.cacheDir, "docdoom_sign").apply { mkdirs() }
    val file = File(folder, "${prefix}_${UUID.randomUUID()}.pdf")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    } ?: throw IOException("Android couldn't read the selected PDF.")
    if (file.length() < 5L) {
        file.delete()
        throw IOException("The selected PDF is empty.")
    }
    return file
}

private fun countPdfPages(file: File): Int {
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    pfd.use { descriptor -> PdfRenderer(descriptor).use { renderer -> return renderer.pageCount } }
}

private fun createSignedPdfFile(
    context: Context,
    sourceFile: File,
    signature: Bitmap,
    pageIndexes: List<Int>,
    xFraction: Float,
    yFraction: Float,
    widthFraction: Float
): File {
    PDFBoxResourceLoader.init(context.applicationContext)
    val output = File(sourceFile.parentFile, "signed_${UUID.randomUUID()}.pdf")
    try {
        PDDocument.load(sourceFile).use { document ->
            if (pageIndexes.isEmpty()) throw IOException("No pages were selected.")
            if (pageIndexes.any { it !in 0 until document.numberOfPages }) throw IOException("A selected page doesn't exist.")
            val image = LosslessFactory.createFromImage(document, signature)
            pageIndexes.distinct().sorted().forEach { pageIndex ->
                val page = document.getPage(pageIndex)
                val box = page.mediaBox
                val pageWidth = box.width
                val pageHeight = box.height
                val drawWidth = (pageWidth * widthFraction).coerceAtMost(pageWidth)
                val ratio = signature.height.toFloat() / signature.width.toFloat()
                val drawHeight = (drawWidth * ratio).coerceAtMost(pageHeight)
                val x = (pageWidth - drawWidth).coerceAtLeast(0f) * xFraction.coerceIn(0f, 1f)
                val top = (pageHeight - drawHeight).coerceAtLeast(0f) * yFraction.coerceIn(0f, 1f)
                val y = (pageHeight - top - drawHeight).coerceAtLeast(0f)
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.drawImage(image, box.lowerLeftX + x, box.lowerLeftY + y, drawWidth, drawHeight)
                }
            }
            document.save(output)
        }
        return output
    } catch (e: Exception) {
        output.delete()
        throw IOException(e.localizedMessage ?: "Couldn't apply the signature to the PDF.", e)
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
    }
} catch (_: Exception) {
    null
}
