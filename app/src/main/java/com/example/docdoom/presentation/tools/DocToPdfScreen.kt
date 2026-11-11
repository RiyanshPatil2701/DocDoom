package com.example.docdoom.presentation.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun DocToPdfScreen(
    onBack: () -> Unit,
    onSaved: (Uri, String, Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceName by remember { mutableStateOf("") }
    var convertedFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pageCount by remember { mutableStateOf(0) }
    var renderedPages by remember { mutableStateOf(0) }
    var saveFile by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = ""
                renderedPages = 0
                pageCount = 0
                convertedFile?.delete()
                convertedFile = null
                var sourceCopy: File? = null
                try {
                    val name = queryDisplayName(context, uri)
                    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (extension != "docx") {
                        throw IOException("For now, DocDoom supports .docx files only. Please choose a DOCX document.")
                    }

                    sourceCopy = withContext(Dispatchers.IO) {
                        val folder = File(context.filesDir, "docx-export").apply { mkdirs() }
                        val target = File(folder, "source_${UUID.randomUUID()}.docx")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().buffered().use { output -> input.copyTo(output) }
                        } ?: throw IOException("Couldn't read the selected DOCX document.")
                        target
                    }

                    val result = renderDocxToPdf(context, sourceCopy) { done, total ->
                        renderedPages = done
                        pageCount = total
                    }
                    convertedFile = result.file
                    pageCount = result.pages
                    sourceName = name
                    status = "Offline conversion complete. Please compare page breaks, fonts, tables and images with the original before relying on the PDF."
                } catch (error: Exception) {
                    status = error.localizedMessage ?: "Couldn't convert this DOCX document."
                } finally {
                    sourceCopy?.delete()
                    busy = false
                }
            }
        }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val ready = saveFile
        if (destination != null && ready != null) {
            scope.launch {
                saving = true
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            ready.inputStream().buffered().use { input -> input.copyTo(output) }
                        } ?: throw IOException("Couldn't create the PDF at the selected location.")
                    }
                    val displayName = "${sourceName.substringBeforeLast('.', sourceName)}.pdf"
                    onSaved(destination, displayName, ready.length())
                    status = "PDF saved and added to DocDoom."
                } catch (error: Exception) {
                    status = error.localizedMessage ?: "Couldn't save the PDF."
                } finally {
                    saving = false
                    saveFile = null
                }
            }
        } else {
            saveFile = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, enabled = !busy && !saving) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = null
                )
                Text(" Back")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "DOCX TO PDF",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            "Word to PDF",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.09f)
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.padding(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    "Upload a DOCX file",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Choose a Word document to convert it into a PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )

                Button(
                    onClick = {
                        picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    },
                    enabled = !busy && !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Outlined.Description,
                        contentDescription = null
                    )
                    Text("  Choose DOCX file")
                }
            }
        }

        if (busy || saving) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator()
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (saving) "Saving PDF" else "Converting document",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            when {
                                saving -> "Please wait…"
                                pageCount > 0 -> "Rendering page ${renderedPages.coerceAtMost(pageCount)} of $pageCount"
                                else -> "Preparing your document…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (sourceName.isNotBlank() && convertedFile != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "  PDF ready",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        sourceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$pageCount page(s) • ${(convertedFile!!.length() / 1024).coerceAtLeast(1)} KB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = {
                            saveFile = convertedFile
                            saver.launch("${sourceName.substringBeforeLast('.', sourceName)}.pdf")
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val shareUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    convertedFile!!
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share PDF"))
                            } catch (error: Exception) {
                                status = error.localizedMessage ?: "Couldn't share the PDF."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Share,
                            contentDescription = null
                        )
                        Text("  Share PDF")
                    }
                }
            }
        }

        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.startsWith("Couldn't") || status.startsWith("Please") || status.startsWith("For now")) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class RenderedPdf(val file: File, val pages: Int)
private data class RenderedPageImage(val file: File, val widthPt: Int, val heightPt: Int)

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = ""
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index).orEmpty()
        }
    }
    return name.ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "Word document.docx" }
}

private suspend fun renderDocxToPdf(
    context: Context,
    sourceFile: File,
    onProgress: (completed: Int, total: Int) -> Unit
): RenderedPdf {
    val workDirectory = File(context.cacheDir, "docx-export/${UUID.randomUUID()}").apply { mkdirs() }
    val pageDirectory = File(workDirectory, "pages").apply { mkdirs() }
    val pageResult = CompletableDeferred<List<RenderedPageImage>>()
    val mainHandler = Handler(Looper.getMainLooper())
    var webView: WebView? = null
    var bridge: DocxExportBridge? = null

    try {
        withContext(Dispatchers.Main.immediate) {
            val loader = WebViewAssetLoader.Builder()
                // Android's MimeTypeMap does not consistently recognise .mjs/.wasm.
                // Serve the bundled renderer with the MIME types required by ES modules and WASM.
                .addPathHandler("/assets/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? {
                        val stream = try {
                            context.assets.open(path)
                        } catch (_: Exception) {
                            return null
                        }
                        val mimeType = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                            "html" -> "text/html"
                            "mjs", "js" -> "text/javascript"
                            "css" -> "text/css"
                            "json" -> "application/json"
                            "wasm" -> "application/wasm"
                            "svg" -> "image/svg+xml"
                            else -> "application/octet-stream"
                        }
                        val encoding = if (mimeType == "application/wasm" || mimeType == "application/octet-stream") null else "UTF-8"
                        return WebResourceResponse(mimeType, encoding, stream)
                    }
                })
                .addPathHandler("/documents/", WebViewAssetLoader.InternalStoragePathHandler(context, sourceFile.parentFile!!))
                .build()

            val view = WebView(context)
            webView = view
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = false
            view.settings.allowFileAccess = false
            view.settings.allowContentAccess = false
            view.settings.blockNetworkLoads = true

            // Forward JavaScript console messages from docx-export.html to Logcat.
            view.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    android.util.Log.d(
                        "DocDoomDOCX",
                        "${message.message()} | line ${message.lineNumber()} | ${message.sourceId()}"
                    )
                    return true
                }
            }

            view.webViewClient = object : WebViewClient() {
                private val pageStarted = AtomicBoolean(false)

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                    loader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String?) {
                    android.util.Log.d("DocDoomDOCX", "WebView page finished: $url")
                    if (!pageStarted.compareAndSet(false, true)) return
                    val documentUrl = "https://appassets.androidplatform.net/documents/${sourceFile.name}"
                    android.util.Log.d("DocDoomDOCX", "Calling startDocxExport for $documentUrl")
                    view.evaluateJavascript("window.startDocxExport(${JSONObject.quote(documentUrl)})") { value ->
                        android.util.Log.d("DocDoomDOCX", "startDocxExport JS evaluation result: $value")
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    android.util.Log.e(
                        "DocDoomDOCX",
                        "WebView resource error: ${error.description}; url=${request.url}"
                    )
                    if (request.isForMainFrame) {
                        pageResult.completeExceptionally(IOException("DOCX renderer page failed to load: ${error.description}"))
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    android.util.Log.e("DocDoomDOCX", "WebView render process gone; didCrash=${detail.didCrash()}")
                    pageResult.completeExceptionally(IOException("Android WebView renderer stopped unexpectedly. Please try a smaller DOCX file."))
                    return true
                }
            }

            val exportBridge = DocxExportBridge(
                webView = view,
                pageDirectory = pageDirectory,
                result = pageResult,
                onProgress = onProgress,
                mainHandler = mainHandler
            )
            bridge = exportBridge
            view.addJavascriptInterface(exportBridge, "AndroidBridge")
            view.loadUrl("https://appassets.androidplatform.net/assets/docx-export.html")
        }

        val pageImages = withTimeout(5 * 60 * 1000L) { pageResult.await() }
        val pdfFile = withContext(Dispatchers.IO) {
            createPdfFromRenderedPages(pageImages, File(workDirectory, "converted.pdf"))
        }
        return RenderedPdf(pdfFile, pageImages.size)
    } finally {
        withContext(Dispatchers.Main.immediate) {
            try {
                webView?.removeJavascriptInterface("AndroidBridge")
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
                webView?.destroy()
            } catch (_: Exception) {
            }
        }
        bridge?.close()
        // Keep only the finished PDF; temporary page JPEGs and the copied source are disposable.
        pageDirectory.deleteRecursively()
    }
}

private class DocxExportBridge(
    private val webView: WebView,
    private val pageDirectory: File,
    private val result: CompletableDeferred<List<RenderedPageImage>>,
    private val onProgress: (Int, Int) -> Unit,
    private val mainHandler: Handler
) {
    private val lock = Any()
    private val pages = sortedMapOf<Int, RenderedPageImage>()
    @Volatile private var expectedPages = 0
    @Volatile private var closed = false

    @JavascriptInterface
    fun onReady(totalPages: Int) {
        android.util.Log.d("DocDoomDOCX", "AndroidBridge.onReady: totalPages=$totalPages")
        if (closed) return
        if (totalPages <= 0 || totalPages > 500) {
            result.completeExceptionally(IOException("This DOCX has an unsupported page count ($totalPages). Maximum supported in this free converter is 500 pages."))
            return
        }
        expectedPages = totalPages
        mainHandler.post {
            onProgress(0, totalPages)
            android.util.Log.d("DocDoomDOCX", "Requesting first page from JavaScript")
            webView.evaluateJavascript("window.renderNextDocxPage()") { value ->
                android.util.Log.d("DocDoomDOCX", "renderNextDocxPage JS evaluation result: $value")
            }
        }
    }

    @JavascriptInterface
    fun onPageRendered(index: Int, widthPt: Int, heightPt: Int, base64Jpeg: String) {
        android.util.Log.d(
            "DocDoomDOCX",
            "AndroidBridge.onPageRendered: index=$index, size=${widthPt}x${heightPt}pt, base64Chars=${base64Jpeg.length}"
        )
        if (closed || result.isCompleted) return
        try {
            if (index !in 0 until expectedPages) throw IOException("Renderer returned an invalid page index.")
            if (widthPt <= 0 || heightPt <= 0) throw IOException("Renderer returned invalid page dimensions.")
            val bytes = Base64.decode(base64Jpeg, Base64.DEFAULT)
            if (bytes.isEmpty()) throw IOException("Renderer returned an empty page image.")
            val file = File(pageDirectory, "page_${index.toString().padStart(4, '0')}.jpg")
            file.outputStream().buffered().use { it.write(bytes) }
            android.util.Log.d("DocDoomDOCX", "Saved rendered page image: ${file.name}, ${bytes.size} bytes")
            val orderedPages: List<RenderedPageImage>
            val allPagesReceived: Boolean

            synchronized(lock) {
                pages[index] = RenderedPageImage(file, widthPt, heightPt)
                orderedPages = pages.values.toList()
                allPagesReceived =
                    pages.size == expectedPages &&
                            pages.keys.toList() == (0 until expectedPages).toList()
            }

            mainHandler.post {
                onProgress(index + 1, expectedPages)

                if (allPagesReceived) {
                    // The final JPEG is already safely stored. Complete the Kotlin
                    // deferred directly instead of depending on a second JavaScript
                    // round-trip just to receive onComplete().
                    android.util.Log.d(
                        "DocDoomDOCX",
                        "All $expectedPages page image(s) received; completing export directly."
                    )
                    mainHandler.postDelayed({
                        if (!closed && !result.isCompleted) {
                            result.complete(orderedPages)
                        }
                    }, 50L)
                } else {
                    // Allow the JavaScript callback to return and clear its in-flight
                    // flag before requesting the next page.
                    mainHandler.postDelayed({
                        if (!closed && !result.isCompleted) {
                            android.util.Log.d(
                                "DocDoomDOCX",
                                "Requesting next page from JavaScript; received ${orderedPages.size}/$expectedPages"
                            )
                            webView.evaluateJavascript("window.renderNextDocxPage()") { value ->
                                android.util.Log.d(
                                    "DocDoomDOCX",
                                    "next renderNextDocxPage JS result: $value"
                                )
                            }
                        }
                    }, 50L)
                }
            }
        } catch (error: Exception) {
            result.completeExceptionally(error)
        }
    }

    @JavascriptInterface
    fun onComplete(totalPages: Int) {
        android.util.Log.d("DocDoomDOCX", "AndroidBridge.onComplete: totalPages=$totalPages")
        if (closed || result.isCompleted) return
        val ordered = synchronized(lock) { pages.values.toList() }
        if (totalPages != expectedPages || ordered.size != expectedPages) {
            result.completeExceptionally(IOException("DOCX export ended early: expected $expectedPages pages, received ${ordered.size}."))
        } else {
            result.complete(ordered)
        }
    }

    @JavascriptInterface
    fun onError(message: String) {
        android.util.Log.e("DocDoomDOCX", "AndroidBridge.onError: $message")
        if (!closed) result.completeExceptionally(IOException(message.ifBlank { "The DOCX renderer could not process this document." }))
    }

    fun close() {
        closed = true
    }
}

private fun createPdfFromRenderedPages(pages: List<RenderedPageImage>, outputFile: File): File {
    if (pages.isEmpty()) throw IOException("The DOCX renderer did not return any pages.")
    val document = PdfDocument()
    try {
        pages.forEachIndexed { index, pageImage ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(pageImage.file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Rendered page ${index + 1} is not a valid image.")
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, 1800)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(pageImage.file.absolutePath, options)
                ?: throw IOException("Couldn't decode rendered page ${index + 1}.")

            try {
                val pageWidth = pageImage.widthPt.coerceIn(1, 14400)
                val pageHeight = pageImage.heightPt.coerceIn(1, 14400)
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(pageInfo)
                try {
                    val destination = RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        destination,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                } finally {
                    document.finishPage(page)
                }
            } finally {
                bitmap.recycle()
            }
        }

        outputFile.outputStream().buffered().use { output ->
            document.writeTo(output)
            output.flush()
        }
    } catch (error: Exception) {
        outputFile.delete()
        throw error
    } finally {
        document.close()
    }

    validateGeneratedPdf(outputFile, pages.size)
    return outputFile
}

private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    while (width / (sample * 2) > maxDimension || height / (sample * 2) > maxDimension) {
        sample *= 2
    }
    return sample
}

private fun validateGeneratedPdf(file: File, expectedPages: Int) {
    if (!file.exists() || file.length() < 500L) throw IOException("Generated PDF is empty.")
    val descriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use { pfd ->
        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount != expectedPages) {
                throw IOException("PDF validation failed: expected $expectedPages pages, found ${renderer.pageCount}.")
            }
            renderer.openPage(0).use { page ->
                val testBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                try {
                    page.render(testBitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    testBitmap.recycle()
                }
            }
        }
    }
}
