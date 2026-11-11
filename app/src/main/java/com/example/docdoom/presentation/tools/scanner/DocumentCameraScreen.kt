package com.example.docdoom.presentation.tools.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
import java.io.FileInputStream
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

private data class ScanPage(val uri: Uri, val sourceFile: File? = null)

@Composable
fun DocumentCameraScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val screenScope = rememberCoroutineScope()
    val executor = remember { Executors.newSingleThreadExecutor() }
    val pages = remember { mutableStateListOf<ScanPage>() }

    var cameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var creatingPdf by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingPdf by remember { mutableStateOf<File?>(null) }
    var pdfName by remember { mutableStateOf("Scanned_Document.pdf") }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var importingImages by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraPermission = it
        permissionAsked = true
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            importingImages = true
            errorMessage = null
            screenScope.launch {
                try {
                    val copied = withContext(Dispatchers.IO) {
                        val directory = File(context.cacheDir, "docdoom_scans").apply { mkdirs() }
                        val result = mutableListOf<ScanPage>()
                        uris.forEachIndexed { index, sourceUri ->
                            try {
                                val extension = (queryDisplayName(context, sourceUri)
                                    ?.substringAfterLast('.', "jpg")
                                    ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "jpg")
                                val target = File(directory, "gallery_${System.currentTimeMillis()}_${index}.$extension")
                                val input = context.contentResolver.openInputStream(sourceUri)
                                    ?: throw IOException("Android couldn't open the selected photo.")
                                input.use { source -> FileOutputStream(target).use { destination -> source.copyTo(destination) } }
                                if (target.length() <= 0L) throw IOException("The selected photo is empty.")
                                result += ScanPage(Uri.fromFile(target), target)
                            } catch (e: Exception) {
                                result.forEach { it.sourceFile?.delete() }
                                throw IOException("Couldn't import ${queryDisplayName(context, sourceUri) ?: "a selected photo"}: ${e.localizedMessage ?: "image access failed"}", e)
                            }
                        }
                        result
                    }
                    pages.addAll(copied)
                    showReview = true
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Couldn't import the selected images."
                } finally {
                    importingImages = false
                }
            }
        }
    }
    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
        val source = pendingPdf
        if (destination != null && source != null) {
            creatingPdf = true
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            source.inputStream().use { it.copyTo(output) }
                        } ?: throw IOException("Android couldn't open the selected save location.")
                    }
                    val displayName = queryDisplayName(context, destination) ?: pdfName
                    val size = querySize(context, destination) ?: source.length()
                    savedUri = destination
                    onSaved(destination, displayName, size)
                    errorMessage = "PDF saved successfully."
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Couldn't save the PDF."
                } finally {
                    creatingPdf = false
                    source.delete()
                    pendingPdf = null
                }
            }
        } else {
            pendingPdf?.delete()
            pendingPdf = null
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission && !permissionAsked) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    fun capturePage() {
        val capture = imageCapture ?: return
        if (capturing) return
        capturing = true
        errorMessage = null
        val directory = File(context.cacheDir, "docdoom_scans").apply { mkdirs() }
        val output = File(directory, "scan_${System.currentTimeMillis()}.jpg")
        val options = OutputFileOptions.Builder(output).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                pages.add(ScanPage(Uri.fromFile(output), output))
                capturing = false
                showReview = true
            }
            override fun onError(exception: ImageCaptureException) {
                output.delete()
                capturing = false
                errorMessage = exception.localizedMessage ?: "Couldn't capture this page."
            }
        })
    }

    fun makePdf() {
        if (pages.isEmpty() || creatingPdf) return
        creatingPdf = true
        errorMessage = null
        val orderedUris = pages.map { it.uri }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val output = withContext(Dispatchers.IO) { createScanPdf(context, orderedUris) }
                pendingPdf = output
                creatingPdf = false
                savePdfLauncher.launch("Scanned_Document.pdf")
            } catch (e: Exception) {
                creatingPdf = false
                errorMessage = e.localizedMessage ?: "Couldn't create the PDF."
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7F6))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (showReview) showReview = false else onBack()
            }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(if (showReview) "Review pages" else "Scan to PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (showReview) "${pages.size} ${if (pages.size == 1) "page" else "pages"} selected" else "Capture documents or import photos", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (!showReview) {
                IconButton(onClick = {
                    flashOn = !flashOn
                    camera?.cameraControl?.enableTorch(flashOn)
                }) { Icon(if (flashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, contentDescription = "Flash") }
            }
        }

        if (!showReview) {
            if (cameraPermission) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PreviewView(viewContext).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                                providerFuture.addListener({
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                        .setTargetRotation(display.rotation)
                                        .build()
                                    try {
                                        provider.unbindAll()
                                        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                        imageCapture = capture
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage ?: "Couldn't start the camera."
                                    }
                                }, ContextCompat.getMainExecutor(viewContext))
                            }
                        }
                    )
                    if (capturing) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission is needed to scan documents.")
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant permission") }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF17211D)).padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    galleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import photos", tint = Color.White, modifier = Modifier.size(28.dp)) }
                Surface(Modifier.size(76.dp), shape = CircleShape, color = Color.White) {
                    IconButton(enabled = cameraPermission && !capturing && imageCapture != null, onClick = { capturePage() }) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = "Capture page", tint = Color(0xFF176B50), modifier = Modifier.size(34.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${pages.size} pages", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { if (pages.isNotEmpty()) showReview = true }, enabled = pages.isNotEmpty()) {
                        Text("Review", color = Color.White)
                    }
                }
            }
        } else {
            if (pages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No pages yet. Capture or import photos.") }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(pages, key = { index, page -> "${page.uri}-$index" }) { index, page ->
                        ScanPageRow(
                            index = index,
                            uri = page.uri,
                            onMoveUp = { if (index > 0) { val item = pages.removeAt(index); pages.add(index - 1, item) } },
                            onMoveDown = { if (index < pages.lastIndex) { val item = pages.removeAt(index); pages.add(index + 1, item) } },
                            onDelete = { pages.removeAt(index); page.sourceFile?.delete() },
                            onPreview = { previewUri = page.uri },
                            onRetake = {
                                pages.removeAt(index)
                                page.sourceFile?.delete()
                                showReview = false
                            }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { galleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(6.dp)); Text("Add pages") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showReview = false }
                ) { Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.size(6.dp)); Text("Camera") }
            }
            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(52.dp),
                enabled = pages.isNotEmpty() && !creatingPdf && !importingImages,
                onClick = { makePdf() }
            ) {
                if (creatingPdf) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.PictureAsPdf, null)
                Spacer(Modifier.size(8.dp))
                Text(if (creatingPdf) "Creating PDF…" else "Create PDF")
            }
            Spacer(Modifier.height(12.dp))
        }

        errorMessage?.let {
            Text(it, color = if (it.startsWith("PDF saved")) Color(0xFF176B50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        }
        savedUri?.let { uri ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newUri(context.contentResolver, "Scanned PDF", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share scanned PDF"))
                }) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.size(6.dp)); Text("Share PDF") }
                TextButton(onClick = onBack) { Text("Done") }
            }
        }
    }

    previewUri?.let { uri ->
        FullscreenScanPreview(uri = uri, onDismiss = { previewUri = null })
    }
}

@Composable
private fun FullscreenScanPreview(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try { decodeBitmap(context, uri, 2600) } catch (_: Exception) { null }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101412)) {
            Box(Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap!!.asImageBitmap(),
                        contentDescription = "Full page preview",
                        modifier = Modifier.fillMaxSize().padding(top = 54.dp, bottom = 20.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Couldn't load this image", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Close preview", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ScanPageRow(
    index: Int,
    uri: Uri,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
    onRetake: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                decodeBitmap(context, uri, 420)
            } catch (_: Exception) { null }
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 76.dp, height = 96.dp).background(Color(0xFFF0F3F1), RoundedCornerShape(8.dp)).clickable(onClick = onPreview), contentAlignment = Alignment.Center) {
                if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize().padding(2.dp))
                else Text("${index + 1}")
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Page ${index + 1}", fontWeight = FontWeight.SemiBold)
                Text("Tap thumbnail to view full image", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                    IconButton(onClick = onMoveDown) { Icon(Icons.Outlined.ArrowDownward, "Move down") }
                    IconButton(onClick = onRetake) { Icon(Icons.Outlined.Refresh, "Retake") }
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                }
            }
        }
    }
}

private fun createScanPdf(context: Context, uris: List<Uri>): File {
    if (uris.isEmpty()) throw IOException("Add at least one page.")
    val document = android.graphics.pdf.PdfDocument()
    val output = File(context.cacheDir, "docdoom_scan_${UUID.randomUUID()}.pdf")
    try {
        uris.forEachIndexed { index, uri ->
            val bitmap = decodeBitmap(context, uri)
            try {
                val maxEdge = 842f
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val pageWidth = if (ratio >= 1f) maxEdge else maxEdge * ratio
                val pageHeight = if (ratio >= 1f) maxEdge / ratio else maxEdge
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth.toInt().coerceAtLeast(1), pageHeight.toInt().coerceAtLeast(1), index + 1).create()
                val page = document.startPage(pageInfo)
                try {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageWidth, pageHeight), paint)
                } finally { document.finishPage(page) }
            } finally { bitmap.recycle() }
        }
        FileOutputStream(output).use { stream -> document.writeTo(stream); stream.fd.sync() }
        if (!output.exists() || output.length() == 0L) throw IOException("The PDF file is empty.")
        return output
    } catch (e: Exception) {
        output.delete()
        throw e
    } finally { document.close() }
}

private fun openImageStream(context: Context, uri: Uri): java.io.InputStream? {
    return when (uri.scheme?.lowercase()) {
        "file" -> {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) null else FileInputStream(file)
        }
        "content" -> context.contentResolver.openInputStream(uri)
        else -> context.contentResolver.openInputStream(uri)
    }
}

private fun decodeBitmap(context: Context, uri: Uri, maxEdge: Int = 2200): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStreamOpened = openImageStream(context, uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
        true
    } ?: false
    if (!boundsStreamOpened || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IOException("Can't read image (${uri.scheme ?: "unknown"}): ${uri.lastPathSegment ?: uri}")
    }
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxEdge) sample *= 2
    val bitmap = openImageStream(context, uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }
    return bitmap ?: throw IOException("Image format isn't supported or image is damaged: ${uri.lastPathSegment ?: uri}")
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null
    }
} catch (_: Exception) { null }

private fun querySize(context: Context, uri: Uri): Long? = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
    }
} catch (_: Exception) { null }
