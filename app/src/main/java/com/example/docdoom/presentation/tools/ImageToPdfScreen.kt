package com.example.docdoom.presentation.tools
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Emerald = Color(0xFF16856B)
private val SoftMint = Color(0xFFE8F5F0)
private val DarkText = Color(0xFF202A27)
private val MutedText = Color(0xFF687570)
private val RemoveBackground = Color(0xFFFCE8E8)
private val RemoveRed = Color(0xFFC62828)
private val ScreenBackground = Color(0xFFF8FAF9)

/**
 * Image-to-PDF flow:
 * photo selection -> page arrangement -> PDF generation -> PDF preview -> save.
 */
@Composable
fun ImageToPdfScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedImages = remember { mutableStateListOf<Uri>() }

    var isGenerating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var generatedPdf by remember { mutableStateOf<File?>(null) }
    var saveSource by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var savedDestinationUri by remember { mutableStateOf<Uri?>(null) }
    var savedDisplayName by remember { mutableStateOf<String?>(null) }
    var pdfName by remember { mutableStateOf(defaultPdfName()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            if (uri !in selectedImages) selectedImages.add(uri)
        }
        statusMessage = ""
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
                            source.inputStream().use { input -> input.copyTo(output) }
                        } ?: throw IOException("Android couldn't open the selected save location.")
                    }
                    savedDestinationUri = destinationUri
                    savedDisplayName = queryDisplayName(context, destinationUri)
                    statusMessage = "PDF saved successfully in the folder you selected."
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
        PdfPreviewScreen(
            pdfFile = pdf,
            isSaving = isSaving,
            statusMessage = statusMessage,
            savedDisplayName = savedDisplayName,
            isAlreadySaved = savedDestinationUri != null,
            pdfName = pdfName,
            onPdfNameChange = { pdfName = it },
            onSave = {
                saveSource = pdf
                saveLauncher.launch(sanitizePdfFileName(pdfName))
            },
            onShare = {
                val uriToShare = savedDestinationUri
                if (uriToShare != null) {
                    sharePdf(context, uriToShare)
                }
            },
            onBack = {
                // Return to the arranged photo list only when the user explicitly chooses Back.
                generatedPdf = null
                savedDestinationUri = null
                savedDisplayName = null
                statusMessage = ""
            }
        )
        return
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val movedImage = selectedImages.removeAt(from.index)
        selectedImages.add(to.index, movedImage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !isGenerating) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Images to PDF",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Choose photos, arrange your pages, and create a PDF.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )
        if (selectedImages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tip: Long-press any page to rearrange it.",
                style = MaterialTheme.typography.labelMedium,
                color = Emerald
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected pages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                Text(
                    text = "${selectedImages.size} pages",
                    color = Emerald,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }

        if (selectedImages.isEmpty()) {
            // Compact empty state: keeps the screen balanced without a giant blank panel.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SoftMint),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Turn photos into PDF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "Combine your images into one document. Choose your photos and arrange them in your preferred order.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "HOW IT WORKS",
                        color = Emerald,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    EmptyStateStep(number = "1", text = "Select photos from your gallery")
                    Spacer(Modifier.height(9.dp))
                    EmptyStateStep(number = "2", text = "Arrange and manage your pages")
                    Spacer(Modifier.height(9.dp))
                    EmptyStateStep(number = "3", text = "Create and save your PDF")
                }
            }
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = selectedImages,
                    key = { _, uri -> uri.toString() }
                ) { index, uri ->
                    ReorderableItem(
                        state = reorderableState,
                        key = uri.toString()
                    ) { isDragging ->
                        val itemScope = this
                        val cardElevation by animateDpAsState(
                            targetValue = if (isDragging) 16.dp else 1.dp,
                            label = "pageCardElevation"
                        )
                        val cardScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.035f else 1f,
                            label = "pageCardScale"
                        )
                        val cardColor by animateColorAsState(
                            targetValue = if (isDragging) SoftMint else Color.White,
                            label = "pageCardColor"
                        )

                        Card(
                            modifier = with(itemScope) {
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                    }
                                    .longPressDraggableHandle()
                            },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                            colors = CardDefaults.cardColors(containerColor = cardColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PhotoThumbnail(
                                    uri = uri,
                                    modifier = Modifier.size(76.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = "Page ${index + 1}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Selected image",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedText
                                    )
                                }
                                IconButton(
                                    onClick = { selectedImages.remove(uri) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(RemoveBackground)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Remove page",
                                        tint = RemoveRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (selectedImages.isNotEmpty()) {
            Button(
                onClick = {
                    if (!isGenerating) {
                        val imagesSnapshot = selectedImages.toList()
                        scope.launch {
                            isGenerating = true
                            statusMessage = "Preparing images and creating PDF..."
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    createAndValidatePdf(context, imagesSnapshot)
                                }
                                generatedPdf = result
                                pdfName = defaultPdfName()
                                savedDestinationUri = null
                                savedDisplayName = null
                                statusMessage = ""
                            } catch (error: Exception) {
                                statusMessage =
                                    "Couldn't create PDF: ${error.localizedMessage ?: "Please try again."}"
                            } finally {
                                isGenerating = false
                            }
                        }
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("Creating PDF...")
                } else {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Create PDF")
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (selectedImages.isEmpty()) "Select Photos" else "Add More Photos")
        }

        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusMessage,
                color = if (statusMessage.startsWith("Couldn't")) RemoveRed else Emerald,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EmptyStateStep(
    number: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Emerald,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            color = DarkText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PhotoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = uri
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return@withContext null
                }
                options.inSampleSize = calculateSampleSize(
                    options.outWidth,
                    options.outHeight,
                    300
                )
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Selected image preview",
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SoftMint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "Image preview unavailable",
                tint = Emerald
            )
        }
    }
}

/**
 * Copies each picked URI to app-private cache before decoding it.
 * This avoids relying on a provider stream being reopenable.
 */
private fun createAndValidatePdf(
    context: Context,
    imageUris: List<Uri>
): File {
    require(imageUris.isNotEmpty()) { "Select at least one photo first." }

    val pdfFile = File(context.cacheDir, "docdoom_${UUID.randomUUID()}.pdf")
    val temporaryImages = mutableListOf<File>()
    val document = PdfDocument()

    try {
        imageUris.forEachIndexed { index, uri ->
            val localImage = File(context.cacheDir, "docdoom_image_${UUID.randomUUID()}.tmp")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Android couldn't open selected photo ${index + 1}.")
                input.use { source ->
                    FileOutputStream(localImage).use { destination ->
                        source.copyTo(destination)
                    }
                }
                if (!localImage.exists() || localImage.length() == 0L) {
                    throw IOException("Selected photo ${index + 1} is empty or unreadable.")
                }
                temporaryImages.add(localImage)

                val bitmap = decodeImageFile(localImage)
                try {
                    drawBitmapPage(document, bitmap, index + 1)
                } finally {
                    bitmap.recycle()
                }
            } catch (error: Exception) {
                throw IOException(
                    "Photo ${index + 1} could not be processed: ${error.localizedMessage ?: "Unknown image error."}",
                    error
                )
            }
        }

        FileOutputStream(pdfFile).use { output ->
            document.writeTo(output)
            output.fd.sync()
        }
    } catch (error: Exception) {
        pdfFile.delete()
        throw error
    } finally {
        document.close()
        temporaryImages.forEach { it.delete() }
    }

    validatePdf(pdfFile, imageUris.size)
    return pdfFile
}

private fun decodeImageFile(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IOException("The selected file isn't a supported image.")
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 2200)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    return BitmapFactory.decodeFile(file.absolutePath, options)
        ?: throw IOException("Android couldn't decode the selected image.")
}

private fun drawBitmapPage(
    document: PdfDocument,
    bitmap: Bitmap,
    pageNumber: Int
) {
    // Match each PDF page to the source image's aspect ratio.
    // The longest page edge is 842 points; the shorter edge is calculated
    // proportionally. This avoids cropping, stretching, and artificial margins.
    val longestPageEdge = 842
    val imageWidth = bitmap.width.toFloat()
    val imageHeight = bitmap.height.toFloat()
    val aspectRatio = imageWidth / imageHeight

    val pageWidth: Int
    val pageHeight: Int

    if (aspectRatio >= 1f) {
        pageWidth = longestPageEdge
        pageHeight = (longestPageEdge / aspectRatio).toInt().coerceAtLeast(1)
    } else {
        pageHeight = longestPageEdge
        pageWidth = (longestPageEdge * aspectRatio).toInt().coerceAtLeast(1)
    }

    val pageInfo = PdfDocument.PageInfo.Builder(
        pageWidth,
        pageHeight,
        pageNumber
    ).create()

    val page = document.startPage(pageInfo)
    try {
        val canvas: Canvas = page.canvas
        val destination = RectF(
            0f,
            0f,
            pageWidth.toFloat(),
            pageHeight.toFloat()
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Draw the complete source bitmap into the matching page ratio.
        // Because both rectangles have the same aspect ratio, no image content is cropped.
        canvas.drawBitmap(bitmap, null, destination, paint)
    } finally {
        document.finishPage(page)
    }
}

private fun validatePdf(
    file: File,
    expectedPageCount: Int
) {
    if (!file.exists() || file.length() <= 0L) {
        throw IOException("The generated PDF file is empty.")
    }

    val descriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_ONLY
    )
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount != expectedPageCount) {
                throw IOException(
                    "PDF validation failed: expected $expectedPageCount pages, found ${renderer.pageCount}."
                )
            }
            if (renderer.pageCount > 0) {
                renderer.openPage(0).use { firstPage ->
                    val testBitmap = Bitmap.createBitmap(
                        64,
                        64,
                        Bitmap.Config.ARGB_8888
                    )
                    try {
                        firstPage.render(
                            testBitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                    } finally {
                        testBitmap.recycle()
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPreviewScreen(
    pdfFile: File,
    isSaving: Boolean,
    statusMessage: String,
    savedDisplayName: String?,
    isAlreadySaved: Boolean,
    pdfName: String,
    onPdfNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    val pageCount = remember(pdfFile) {
        try {
            val descriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (_: Exception) {
            0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !isSaving) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to pages")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PDF Preview",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Text(
                    text = "$pageCount ${if (pageCount == 1) "page" else "pages"} · Original image sizes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (pageCount == 0) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("The PDF preview couldn't be opened. Please go back and try again.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pageCount) { pageIndex ->
                    PdfPagePreview(
                        pdfFile = pdfFile,
                        pageIndex = pageIndex
                    )
                }
            }
        }

        Text(
            text = "PDF file name",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = DarkText
        )
        OutlinedTextField(
            value = pdfName,
            onValueChange = onPdfNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSaving,
            placeholder = { Text("Enter a name for your PDF") },
            shape = RoundedCornerShape(12.dp)
        )
        Text(
            text = "The .pdf extension will be added automatically.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        if (isAlreadySaved) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SoftMint)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        tint = Emerald,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PDF saved",
                            color = DarkText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = savedDisplayName ?: "Saved in the folder you selected",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusMessage,
                color = if (statusMessage.startsWith("Couldn't")) RemoveRed else Emerald,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onSave,
            enabled = pageCount > 0 && !isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.size(8.dp))
                Text("Saving PDF...")
            } else {
                Text(if (isAlreadySaved) "Save PDF to Another Location" else "Save PDF")
            }
        }

        if (isAlreadySaved) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onShare,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Share PDF")
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PdfPagePreview(
    pdfFile: File,
    pageIndex: Int
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = pdfFile.absolutePath,
        key2 = pageIndex
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                val descriptor = ParcelFileDescriptor.open(
                    pdfFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            val targetWidth = 1000
                            val targetHeight = (
                                    targetWidth.toFloat() *
                                            page.height.toFloat() /
                                            page.width.toFloat()
                                    ).toInt().coerceAtLeast(1)

                            val rendered = Bitmap.createBitmap(
                                targetWidth,
                                targetHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            page.render(
                                rendered,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            rendered
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Page ${pageIndex + 1}",
                color = MutedText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1} preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(SoftMint),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Couldn't render this page", color = MutedText)
                }
            }
        }
    }
}

/** Shares the saved document URI through Android's system share sheet. */
private fun sharePdf(
    context: Context,
    pdfUri: Uri
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        clipData = ClipData.newUri(
            context.contentResolver,
            "DocDoom PDF",
            pdfUri
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(sendIntent, "Share PDF with")
    try {
        context.startActivity(chooser)
    } catch (_: Exception) {
        // The caller remains on the preview screen if no compatible share target exists.
    }
}

private fun queryDisplayName(
    context: Context,
    uri: Uri
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

private fun defaultPdfName(): String {
    val timestamp = SimpleDateFormat(
        "yyyyMMdd_HHmm",
        Locale.getDefault()
    ).format(Date())
    return "Document_$timestamp"
}

private fun sanitizePdfFileName(input: String): String {
    val cleaned = input
        .trim()
        .removeSuffix(".pdf")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
        .trim('.', ' ')

    val safeName = cleaned.ifBlank { defaultPdfName() }
    return "$safeName.pdf"
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int
): Int {
    var sampleSize = 1
    while (
        width / sampleSize > maxDimension ||
        height / sampleSize > maxDimension
    ) {
        sampleSize *= 2
    }
    return sampleSize
}