
package com.example.docdoom.presentation.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.min

private enum class WatermarkPosition(val label: String) {
    CENTER("Centre"),
    TOP("Top"),
    BOTTOM("Bottom"),
    TOP_LEFT("Top left"),
    TOP_RIGHT("Top right"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right")
}

private enum class WatermarkLayer(val label: String) {
    ABOVE("Above PDF content"),
    BEHIND("Behind PDF content")
}

private enum class WatermarkType {
    TEXT,
    IMAGE
}

private data class WatermarkSettings(
    val position: WatermarkPosition,
    val opacity: Float,
    val size: Float,
    val rotation: Float,
    val layer: WatermarkLayer,
    val textColor: Int
)

@Composable
fun WatermarkPdfScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }

    var watermarkType by remember { mutableStateOf(WatermarkType.TEXT) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var position by remember { mutableStateOf(WatermarkPosition.CENTER) }
    var layer by remember { mutableStateOf(WatermarkLayer.ABOVE) }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var size by remember { mutableFloatStateOf(0.35f) }
    var rotation by remember { mutableFloatStateOf(-30f) }
    var textColor by remember { mutableStateOf(android.graphics.Color.GRAY) }
    var pageRange by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var menuPosition by remember { mutableStateOf(false) }
    var menuLayer by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            sourceUri = uri
            sourceName = getDisplayName(context, uri)
            outputFile?.delete()
            outputFile = null
            previewBitmap?.recycle()
            previewBitmap = null
            status = "PDF selected."
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }

                if (decoded != null) {
                    imageBitmap?.recycle()
                    imageBitmap = decoded
                    watermarkType = WatermarkType.IMAGE
                    status = "Watermark image selected."
                } else {
                    status = "Couldn't read that image. Please choose a PNG or JPG."
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        val file = outputFile

        if (destinationUri != null && file != null && file.exists()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(
                            destinationUri,
                            "w"
                        )?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        } ?: error("Could not open the destination file.")
                    }

                    val savedName = getDisplayName(context, destinationUri)
                        .ifBlank { defaultWatermarkName(sourceName) }

                    onSaved(destinationUri, savedName, file.length())
                    status = "Watermarked PDF saved and added to DocDoom."
                } catch (error: Exception) {
                    status = "Save failed: ${error.localizedMessage ?: "Please try again."}"
                }
            }
        }
    }

    LaunchedEffect(outputFile?.absolutePath) {
        previewBitmap?.recycle()
        previewBitmap = null

        val file = outputFile ?: return@LaunchedEffect

        previewBitmap = withContext(Dispatchers.IO) {
            renderFirstPage(file)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewBitmap?.recycle()
            imageBitmap?.recycle()
            outputFile?.delete()
        }
    }

    fun createWatermark() {
        val input = sourceUri ?: return

        if (watermarkType == WatermarkType.TEXT && watermarkText.isBlank()) {
            status = "Please enter watermark text."
            return
        }

        if (watermarkType == WatermarkType.IMAGE && imageBitmap == null) {
            status = "Please choose a watermark image first."
            return
        }

        busy = true
        status = "Applying watermark..."
        outputFile?.delete()
        outputFile = null

        val settings = WatermarkSettings(
            position = position,
            opacity = opacity,
            size = size,
            rotation = rotation,
            layer = layer,
            textColor = textColor
        )

        val text = watermarkText
        val imageCopy = imageBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        val type = watermarkType
        val range = pageRange

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val destination = File(
                        context.cacheDir,
                        "docdoom_watermark_${UUID.randomUUID()}.pdf"
                    )

                    applyWatermark(
                        context = context,
                        inputUri = input,
                        outputFile = destination,
                        type = type,
                        text = text,
                        image = imageCopy,
                        settings = settings,
                        pageRange = range
                    )

                    destination
                }

                outputFile = result
                status = "Watermark applied. Preview is ready."
            } catch (error: Exception) {
                status = "Couldn't apply watermark: ${
                    error.localizedMessage ?: "Please try another PDF."
                }"
            } finally {
                imageCopy?.recycle()
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Watermark PDF",
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
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFF8BE0C2)
                )
                Text(
                    "Add a watermark to your PDF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Your original PDF stays untouched. Processing happens on this device.",
                    color = Color(0xFFD0E6DF)
                )
            }
        }

        OutlinedButton(
            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sourceUri == null) "Choose PDF" else "Choose another PDF")
        }

        if (sourceUri != null) {
            Text(
                sourceName,
                fontWeight = FontWeight.SemiBold
            )

            Text("Watermark type", fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { watermarkType = WatermarkType.TEXT },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (watermarkType == WatermarkType.TEXT) "✓ Text" else "Text")
                }
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(arrayOf("image/png", "image/jpeg"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (imageBitmap != null) "✓ Image" else "Choose image")
                }
            }

            if (watermarkType == WatermarkType.TEXT) {
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Text colour", fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        android.graphics.Color.GRAY,
                        android.graphics.Color.RED,
                        android.graphics.Color.BLUE,
                        android.graphics.Color.BLACK,
                        android.graphics.Color.GREEN
                    ).forEach { colour ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Color(colour),
                                    RoundedCornerShape(50)
                                )
                                .clickable { textColor = colour }
                        )
                    }
                }
            } else {
                Text(
                    if (imageBitmap != null) "Watermark image ready."
                    else "Choose a PNG or JPG logo/image.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Position", fontWeight = FontWeight.Bold)

            Box {
                OutlinedButton(
                    onClick = { menuPosition = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(position.label)
                }

                DropdownMenu(
                    expanded = menuPosition,
                    onDismissRequest = { menuPosition = false }
                ) {
                    WatermarkPosition.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                position = item
                                menuPosition = false
                            }
                        )
                    }
                }
            }

            Text("Opacity: ${(opacity * 100).toInt()}%")
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = 0.05f..1f
            )

            Text("Size: ${(size * 100).toInt()}%")
            Slider(
                value = size,
                onValueChange = { size = it },
                valueRange = 0.10f..0.65f
            )

            Text("Rotation: ${rotation.toInt()}°")
            Slider(
                value = rotation,
                onValueChange = { rotation = it },
                valueRange = -90f..90f
            )

            Text("Watermark layer", fontWeight = FontWeight.Bold)

            Box {
                OutlinedButton(
                    onClick = { menuLayer = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(layer.label)
                }

                DropdownMenu(
                    expanded = menuLayer,
                    onDismissRequest = { menuLayer = false }
                ) {
                    WatermarkLayer.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                layer = item
                                menuLayer = false
                            }
                        )
                    }
                }
            }

            Text("Pages (optional)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = pageRange,
                onValueChange = { pageRange = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Example: 1-5 or 2,4,6-8") },
                supportingText = {
                    Text("Leave empty to apply to all pages.")
                },
                singleLine = true
            )

            Button(
                onClick = { createWatermark() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Applying watermark..." else "Apply watermark")
            }
        }

        if (busy) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        outputFile?.let { file ->
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Preview · First page",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Watermarked PDF preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        )
                    } else {
                        Text(
                            "Preparing preview...",
                            color = Color.White
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                saveLauncher.launch(defaultWatermarkName(sourceName))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save PDF")
                        }

                        OutlinedButton(
                            onClick = {
                                shareWatermarkedPdf(context, file)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Share")
                        }
                    }
                }
            }
        }

        if (status.isNotBlank()) {
            Text(
                status,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

private suspend fun applyWatermark(
    context: Context,
    inputUri: Uri,
    outputFile: File,
    type: WatermarkType,
    text: String,
    image: Bitmap?,
    settings: WatermarkSettings,
    pageRange: String
) {
    PDFBoxResourceLoader.init(context)

    val input = context.contentResolver.openInputStream(inputUri)
        ?: error("Could not open the selected PDF.")

    input.use { stream ->
        PDDocument.load(stream).use { document ->

            require(document.numberOfPages > 0) {
                "This PDF has no pages."
            }

            val selectedPages = parsePageRange(
                pageRange,
                document.numberOfPages
            )

            val watermarkBitmap = when (type) {
                WatermarkType.TEXT -> createTextBitmap(
                    text,
                    settings.textColor
                )
                WatermarkType.IMAGE -> image?.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                ) ?: error("Watermark image is missing.")
            }

            try {
                val pdfImage: PDImageXObject =
                    LosslessFactory.createFromImage(
                        document,
                        watermarkBitmap
                    )

                selectedPages.forEach { pageIndex ->
                    val page = document.getPage(pageIndex)

                    val crop = page.cropBox
                    val pageWidth = crop.width
                    val pageHeight = crop.height

                    val targetWidth = pageWidth * settings.size
                    val aspect = pdfImage.height.toFloat() /
                            pdfImage.width.toFloat()
                    val targetHeight = targetWidth * aspect

                    val x = when (settings.position) {
                        WatermarkPosition.TOP_LEFT,
                        WatermarkPosition.BOTTOM_LEFT -> crop.lowerLeftX + pageWidth * 0.06f

                        WatermarkPosition.TOP_RIGHT,
                        WatermarkPosition.BOTTOM_RIGHT -> crop.lowerLeftX +
                                pageWidth * 0.94f - targetWidth

                        else -> crop.lowerLeftX +
                                (pageWidth - targetWidth) / 2f
                    }

                    val y = when (settings.position) {
                        WatermarkPosition.TOP,
                        WatermarkPosition.TOP_LEFT,
                        WatermarkPosition.TOP_RIGHT -> crop.lowerLeftY +
                                pageHeight * 0.82f - targetHeight / 2f

                        WatermarkPosition.BOTTOM,
                        WatermarkPosition.BOTTOM_LEFT,
                        WatermarkPosition.BOTTOM_RIGHT -> crop.lowerLeftY +
                                pageHeight * 0.12f - targetHeight / 2f

                        WatermarkPosition.CENTER -> crop.lowerLeftY +
                                (pageHeight - targetHeight) / 2f
                    }

                    val appendMode =
                        if (settings.layer == WatermarkLayer.ABOVE) {
                            PDPageContentStream.AppendMode.APPEND
                        } else {
                            PDPageContentStream.AppendMode.PREPEND
                        }

                    PDPageContentStream(
                        document,
                        page,
                        appendMode,
                        true,
                        settings.layer == WatermarkLayer.ABOVE
                    ).use { content ->

                        val graphicsState = PDExtendedGraphicsState()
                        graphicsState.nonStrokingAlphaConstant =
                            settings.opacity

                        content.saveGraphicsState()
                        content.setGraphicsStateParameters(graphicsState)

                        val centreX = x + targetWidth / 2f
                        val centreY = y + targetHeight / 2f
                        val radians = Math.toRadians(
                            settings.rotation.toDouble()
                        )

                        content.transform(
                            Matrix.getRotateInstance(
                                radians,
                                centreX,
                                centreY
                            )
                        )

                        content.drawImage(
                            pdfImage,
                            x - centreX,
                            y - centreY,
                            targetWidth,
                            targetHeight
                        )

                        content.restoreGraphicsState()
                    }
                }
            } finally {
                watermarkBitmap.recycle()
            }

            outputFile.outputStream().use { output ->
                document.save(output)
            }
        }
    }
}

private fun createTextBitmap(
    text: String,
    colour: Int
): Bitmap {
    val bitmap = Bitmap.createBitmap(
        1800,
        500,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        textSize = 150f
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
        textAlign = Paint.Align.CENTER
    }

    val safeText = text.take(80)
    val bounds = Rect()
    paint.getTextBounds(safeText, 0, safeText.length, bounds)

    if (bounds.width() > 1650) {
        paint.textSize *= 1650f / bounds.width()
    }

    val baseline = 250f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(safeText, 900f, baseline, paint)

    return bitmap
}

private fun parsePageRange(
    input: String,
    pageCount: Int
): List<Int> {
    if (input.isBlank()) {
        return (0 until pageCount).toList()
    }

    val pages = linkedSetOf<Int>()

    input.split(",").forEach { part ->
        val item = part.trim()
        require(item.isNotEmpty()) {
            "Invalid page range."
        }

        if (item.contains("-")) {
            val pieces = item.split("-")
            require(pieces.size == 2) {
                "Invalid page range: $item"
            }

            val start = pieces[0].trim().toIntOrNull()
                ?: error("Invalid page number: $item")
            val end = pieces[1].trim().toIntOrNull()
                ?: error("Invalid page number: $item")

            require(start >= 1 && end >= start && end <= pageCount) {
                "Page range must be between 1 and $pageCount."
            }

            for (page in start..end) {
                pages.add(page - 1)
            }
        } else {
            val page = item.toIntOrNull()
                ?: error("Invalid page number: $item")

            require(page in 1..pageCount) {
                "Page number must be between 1 and $pageCount."
            }

            pages.add(page - 1)
        }
    }

    require(pages.isNotEmpty()) {
        "Please select at least one page."
    }

    return pages.toList()
}

private fun renderFirstPage(file: File): Bitmap? {
    return try {
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    null
                } else {
                    renderer.openPage(0).use { page ->
                        val scale = min(
                            1200f / page.width,
                            2f
                        )

                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )

                        bitmap.eraseColor(android.graphics.Color.WHITE)

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        bitmap
                    }
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun getDisplayName(
    context: Context,
    uri: Uri
): String {
    var name = ""

    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(0).orEmpty()
        }
    }

    return name.ifBlank { "document.pdf" }
}

private fun defaultWatermarkName(sourceName: String): String {
    val base = sourceName.substringBeforeLast(
        ".",
        sourceName
    )
    return "${base}_watermarked.pdf"
}

private fun shareWatermarkedPdf(
    context: Context,
    file: File
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(
            intent,
            "Share watermarked PDF"
        )
    )
}
