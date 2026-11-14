package com.example.docdoom.presentation
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.docdoom.data.database.DocDoomDatabase
import com.example.docdoom.data.database.DocumentEntity
import com.example.docdoom.data.database.DocumentRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docdoom.presentation.tools.CompressImageScreen
import com.example.docdoom.presentation.tools.CompressPdfScreen
import com.example.docdoom.presentation.tools.CompressDocumentScreen
import com.example.docdoom.presentation.tools.CompressFileScreen
import com.example.docdoom.presentation.tools.ImageToPdfScreen
import com.example.docdoom.presentation.tools.MergePdfScreen
import com.example.docdoom.presentation.tools.SplitPdfScreen
import com.example.docdoom.presentation.tools.SignPdfScreen
import com.example.docdoom.presentation.tools.WatermarkPdfScreen
import com.example.docdoom.presentation.tools.DocToPdfScreen
import com.example.docdoom.presentation.tools.scanner.DocumentCameraScreen
import com.example.docdoom.presentation.tools.PdfViewerScreen
import com.example.docdoom.ui.components.ToolCard
import com.example.docdoom.ui.components.StorageOverviewCard
import com.example.docdoom.ui.theme.DocDoomCanvas
import com.example.docdoom.ui.theme.DocDoomDark
import com.example.docdoom.ui.theme.DocDoomEmerald
import com.example.docdoom.ui.theme.DocDoomMint
import com.example.docdoom.ui.theme.DocDoomMuted
import com.example.docdoom.ui.theme.DocDoomWhite

private data class Tool(
    val title: String,
    val description: String,
    val icon: ImageVector
)

private enum class Destination(
    val label: String,
    val icon: ImageVector
) {
    Home("Home", Icons.Outlined.Home),
    Files("Files", Icons.Outlined.Folder),
    Tools("Tools", Icons.Outlined.Tune),
    Settings("Settings", Icons.Outlined.Settings)
}

private val availableTools = listOf(
    Tool(
        "Images to PDF",
        "Turn photos into a PDF",
        Icons.Outlined.Collections
    ),
    Tool(
        "Compress",
        "Reduce image, PDF and file sizes",
        Icons.Outlined.ArrowDownward
    ),
    Tool(
        "Merge PDF",
        "Combine documents",
        Icons.Outlined.MergeType
    ),
    Tool(
        "Split PDF",
        "Extract pages or split every page",
        Icons.Outlined.ContentCut
    ),
    Tool(
        "Sign PDF",
        "Add your handwritten signature to a PDF",
        Icons.Outlined.Edit
    ),
    Tool(
        "Watermark PDF",
        "Mark your documents",
        Icons.Outlined.WaterDrop
    ),
    Tool(
        "Doc to PDF",
        "Convert Word documents to PDF",
        Icons.Outlined.Description
    ),
    Tool(
        "Scan to PDF",
        "Scan paper documents with your camera",
        Icons.Outlined.Collections
    )
)

@Composable
fun DocDoomApp(
    incomingPdfUri: Uri? = null,
    onIncomingPdfHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documentRepository = remember(context) {
        DocumentRepository(
            DocDoomDatabase.getDatabase(context).documentDao()
        )
    }

    var destination by remember {
        mutableStateOf(Destination.Home)
    }

    var selectedTool by remember {
        mutableStateOf<Tool?>(null)
    }

    var pdfToView by remember {
        mutableStateOf<Pair<Uri, String>?>(null)
    }
    var isPreparingPdf by remember { mutableStateOf(false) }
    var pdfOpenError by remember { mutableStateOf<String?>(null) }
    var pendingLegacyDocument by remember {
        mutableStateOf<DocumentEntity?>(null)
    }
    var selectedLegacyPdfUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val legacyPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri ->
        if (selectedUri != null) {
            selectedLegacyPdfUri = selectedUri
        } else {
            pendingLegacyDocument = null
        }
    }

    // Open the original URI directly. Never create another permanent copy just to view a PDF.
    val openPdfUri: (Uri, String, (() -> Unit)?, ((Uri) -> Unit)?) -> Unit =
        { sourceUri, displayName, onAccessDenied, _ ->
            isPreparingPdf = true
            pdfOpenError = null

            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(sourceUri)
                            ?: throw FileNotFoundException("Android couldn't access this PDF.")
                        input.use { stream ->
                            val header = ByteArray(5)
                            var bytesRead = 0
                            while (bytesRead < header.size) {
                                val count = stream.read(header, bytesRead, header.size - bytesRead)
                                if (count <= 0) break
                                bytesRead += count
                            }
                            if (bytesRead != 5 ||
                                !header.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
                            ) {
                                throw IllegalArgumentException("This file doesn't have a valid PDF header.")
                            }
                        }
                    }
                    pdfToView = sourceUri to displayName
                } catch (error: SecurityException) {
                    if (onAccessDenied != null) {
                        onAccessDenied()
                    } else {
                        pdfOpenError = "DocDoom couldn't access this PDF. Please select it again."
                    }
                } catch (error: FileNotFoundException) {
                    if (onAccessDenied != null) {
                        onAccessDenied()
                    } else {
                        pdfOpenError = "This PDF is no longer accessible. Please select it again."
                    }
                } catch (error: Exception) {
                    pdfOpenError = error.localizedMessage ?: "Couldn't open this PDF."
                } finally {
                    isPreparingPdf = false
                }
            }
        }

    val openDocument: (DocumentEntity) -> Unit = { document ->
        scope.launch {
            runCatching { documentRepository.markAsRecent(document.id) }
        }

        val documentUri = Uri.parse(document.uri)
        val isPdf = document.mimeType.equals("application/pdf", ignoreCase = true) ||
                document.displayName.endsWith(".pdf", ignoreCase = true)

        if (isPdf) {
            openPdfUri(
                documentUri,
                document.displayName,
                {
                    pendingLegacyDocument = document
                    legacyPdfPicker.launch(arrayOf("application/pdf"))
                },
                null
            )
        } else {
            try {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(documentUri, document.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(openIntent)
            } catch (_: ActivityNotFoundException) {
                pdfOpenError = "No compatible app is installed to open this document."
            } catch (error: Exception) {
                pdfOpenError = error.localizedMessage ?: "Couldn't open this document."
            }
        }
    }

    LaunchedEffect(incomingPdfUri) {
        val sharedUri = incomingPdfUri
        if (sharedUri != null) {
            val displayName = withContext(Dispatchers.IO) {
                var name: String? = null
                try {
                    context.contentResolver.query(
                        sharedUri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) name = cursor.getString(nameIndex)
                        }
                    }
                } catch (_: Exception) {
                    // Fall back to the URI path below.
                }
                name ?: sharedUri.lastPathSegment ?: "Shared document.pdf"
            }
            openPdfUri(sharedUri, displayName, null, null)
            onIncomingPdfHandled()
        }
    }

    suspend fun recordSavedDocument(
        uri: Uri,
        displayName: String,
        mimeType: String,
        sizeBytes: Long
    ) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // CreateDocument providers may not offer persistable grants; keep the returned URI.
        }

        documentRepository.insertDocument(
            DocumentEntity(
                displayName = displayName,
                uri = uri.toString(),
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                createdAt = System.currentTimeMillis(),
                isFavorite = false
            )
        )
    }

    LaunchedEffect(selectedLegacyPdfUri) {
        val selectedUri = selectedLegacyPdfUri ?: return@LaunchedEffect
        val document = pendingLegacyDocument ?: return@LaunchedEffect

        try {
            isPreparingPdf = true
            pdfOpenError = null

            val isValidPdf = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(selectedUri)
                    ?: throw FileNotFoundException("Couldn't read the selected file.")

                input.use { stream ->
                    val header = ByteArray(5)
                    var bytesRead = 0

                    while (bytesRead < header.size) {
                        val count = stream.read(header, bytesRead, header.size - bytesRead)
                        if (count <= 0) break
                        bytesRead += count
                    }

                    bytesRead == 5 && header.contentEquals(
                        "%PDF-".toByteArray(Charsets.US_ASCII)
                    )
                }
            }

            if (!isValidPdf) {
                throw IllegalArgumentException("Please select a valid PDF file.")
            }

            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some document providers grant access only for the current session.
            }

            documentRepository.updateDocument(
                document.copy(uri = selectedUri.toString())
            )

            pendingLegacyDocument = null
            selectedLegacyPdfUri = null

            openPdfUri(selectedUri, document.displayName, null, null)

        } catch (error: Exception) {
            pdfOpenError = error.localizedMessage
                ?: "Couldn't recover this PDF. Please try again."
            selectedLegacyPdfUri = null

        } finally {
            isPreparingPdf = false
        }
    }

    Scaffold(
        containerColor = DocDoomCanvas,
        bottomBar = {
            if (selectedTool == null && pdfToView == null) {
                NavigationBar(
                    containerColor = DocDoomWhite
                ) {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = {
                                destination = item
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(item.label)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            if (pdfToView != null) {
                val currentPdf = pdfToView!!
                PdfViewerScreen(
                    uri = currentPdf.first,
                    documentName = currentPdf.second,
                    onBack = {
                        pdfToView = null
                    }
                )
            } else {
                selectedTool?.let { tool ->

                    when (tool.title) {
                        "Images to PDF" -> ImageToPdfScreen(
                            onBack = {
                                selectedTool = null
                            }
                        )
                        "Merge PDF" -> MergePdfScreen(
                            onBack = {
                                selectedTool = null
                            },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/pdf", sizeBytes)
                                }
                            }
                        )
                        "Split PDF" -> SplitPdfScreen(
                            onBack = { selectedTool = null },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/pdf", sizeBytes)
                                }
                            }
                        )
                        "Sign PDF" -> SignPdfScreen(
                            onBack = { selectedTool = null },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/pdf", sizeBytes)
                                }
                            }
                        )
                        "Compress" -> CompressScreen(
                            onBack = {
                                selectedTool = null
                            },
                            onCategoryClick = { category ->
                                val categoryIcon = when (category) {
                                    "Compress Image" -> Icons.Outlined.Collections
                                    "Compress PDF" -> Icons.Outlined.PictureAsPdf
                                    "Compress Document" -> Icons.Outlined.Description
                                    else -> Icons.Outlined.Folder
                                }
                                selectedTool = Tool(
                                    title = category,
                                    description = "Compression tool",
                                    icon = categoryIcon
                                )
                            }
                        )
                        "Compress PDF" -> CompressPdfScreen(
                            onBack = {
                                selectedTool = availableTools.first { it.title == "Compress" }
                            },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/pdf", sizeBytes)
                                }
                            }
                        )
                        "Compress Document" -> CompressDocumentScreen(
                            onBack = {
                                selectedTool = availableTools.first { it.title == "Compress" }
                            },
                            onSaved = { uri, displayName, sizeBytes ->
                                val documentMimeType = when {
                                    displayName.endsWith(".docx", ignoreCase = true) ->
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    displayName.endsWith(".xlsx", ignoreCase = true) ->
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    displayName.endsWith(".pptx", ignoreCase = true) ->
                                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                    else -> "application/octet-stream"
                                }
                                scope.launch {
                                    recordSavedDocument(uri, displayName, documentMimeType, sizeBytes)
                                }
                            }
                        )
                        "Compress Image" -> CompressImageScreen(
                            onBack = {
                                selectedTool = availableTools.first { it.title == "Compress" }
                            },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "image/webp", sizeBytes)
                                }
                            }
                        )
                        "Compress File" -> CompressFileScreen(
                            onBack = {
                                selectedTool = availableTools.first { it.title == "Compress" }
                            },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/zip", sizeBytes)
                                }
                            }
                        )
                        "Doc to PDF" -> DocToPdfScreen(
                            onBack = { selectedTool = null },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/pdf", sizeBytes)
                                }
                            }
                        )
                        "Scan to PDF" -> DocumentCameraScreen(
                            onBack = { selectedTool = null },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/pdf", sizeBytes)
                                }
                            }
                        )
                        "Watermark PDF" -> WatermarkPdfScreen(
                            onBack = { selectedTool = null },
                            onSaved = { uri, displayName, sizeBytes ->
                                scope.launch {
                                    recordSavedDocument(uri, displayName, "application/pdf", sizeBytes)
                                }
                            }
                        )
                        else -> ToolDetailScreen(
                            tool = tool,
                            onBack = {
                                selectedTool = null
                            }
                        )
                    }

                } ?: run {

                    when (destination) {

                        Destination.Home -> HomeScreen(
                            repository = documentRepository,
                            onCreatePdf = {
                                selectedTool = availableTools.first()
                            },
                            onMergePdf = {
                                selectedTool = availableTools.first { it.title == "Merge PDF" }
                            },
                            onCompress = {
                                selectedTool = availableTools.first { it.title == "Compress" }
                            },
                            onOpenFiles = {
                                destination = Destination.Files
                            },
                            onOpenTools = {
                                destination = Destination.Tools
                            },
                            onOpenDocument = openDocument
                        )

                        Destination.Files -> FilesScreen(
                            repository = documentRepository,
                            onOpenDocument = openDocument
                        )

                        Destination.Tools -> ToolsScreen(
                            onToolClick = {
                                selectedTool = it
                            }
                        )

                        Destination.Settings -> SimpleScreen(
                            title = "Settings",
                            description = "App preferences and storage options will be added in a later milestone.",
                            icon = Icons.Outlined.Settings
                        )
                    }
                }
            }

            if (isPreparingPdf) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(12.dp))
                        Text("Preparing PDF…")
                    }
                }
            }

            if (!pdfOpenError.isNullOrBlank()) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = pdfOpenError.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    repository: DocumentRepository,
    onCreatePdf: () -> Unit,
    onMergePdf: () -> Unit,
    onCompress: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit
) {
    val recentDocuments by repository.recentDocuments.collectAsState(initial = emptyList())
    val favoriteDocuments by repository.favoriteDocuments.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 28.dp)
    ) {

        // Brand header

        Text(
            text = "DocDoom",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DocDoomEmerald
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Your documents. Under control.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomMuted
        )

        Spacer(Modifier.height(22.dp))

        StorageOverviewCard()

        Spacer(Modifier.height(28.dp))

        // Quick actions

        Text(
            text = "Quick actions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            QuickActionItem(
                title = "Images to PDF",
                icon = Icons.Outlined.Collections,
                modifier = Modifier.weight(1f),
                onClick = onCreatePdf
            )

            QuickActionItem(
                title = "Merge PDF",
                icon = Icons.Outlined.MergeType,
                modifier = Modifier.weight(1f),
                onClick = onMergePdf
            )

            QuickActionItem(
                title = "Compress",
                icon = Icons.Outlined.ArrowDownward,
                modifier = Modifier.weight(1f),
                onClick = onCompress
            )

            QuickActionItem(
                title = "All tools",
                icon = Icons.Outlined.Tune,
                modifier = Modifier.weight(1f),
                onClick = onOpenTools
            )
        }

        Spacer(Modifier.height(32.dp))

        // Recent documents

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent documents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DocDoomDark
            )

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = onOpenFiles,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = "See all",
                    color = DocDoomEmerald
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (recentDocuments.isEmpty()) {
            Text(
                text = "No documents yet",
                style = MaterialTheme.typography.bodyMedium,
                color = DocDoomMuted,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            recentDocuments.take(3).forEach { document ->
                HomeDocumentRow(
                    document = document,
                    repository = repository,
                    onMessage = { },
                    onOpen = { onOpenDocument(document) }
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // Favorites
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(12.dp))

        if (favoriteDocuments.isEmpty()) {
            Text(
                text = "Your starred documents will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = DocDoomMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            favoriteDocuments.take(3).forEach { document ->
                HomeDocumentRow(
                    document = document,
                    repository = repository,
                    onMessage = { },
                    onOpen = { onOpenDocument(document) }
                )
            }
        }
    }
}

@Composable
private fun HomeDocumentRow(
    document: DocumentEntity,
    repository: DocumentRepository,
    onMessage: (String) -> Unit,
    onOpen: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DocDoomWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (document.mimeType.startsWith("image/")) {
                    Icons.Outlined.Collections
                } else {
                    Icons.Outlined.PictureAsPdf
                },
                contentDescription = if (document.mimeType.startsWith("image/")) "Image document" else "PDF document",
                tint = DocDoomEmerald,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DocDoomDark,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatDocumentDetails(document),
                    style = MaterialTheme.typography.bodySmall,
                    color = DocDoomMuted
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        try {
                            repository.updateDocument(
                                document.copy(isFavorite = !document.isFavorite)
                            )
                            onMessage("")
                        } catch (error: Exception) {
                            onMessage(error.localizedMessage ?: "Couldn't update favorite.")
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (document.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (document.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = DocDoomEmerald
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = DocDoomEmerald,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = DocDoomDark,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            minLines = 2
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilesScreen(
    repository: DocumentRepository,
    onOpenDocument: (DocumentEntity) -> Unit
) {
    val allDocuments by repository.allDocuments.collectAsState(initial = emptyList())
    val favoriteDocuments by repository.favoriteDocuments.collectAsState(initial = emptyList())
    val recentDocuments by repository.recentDocuments.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf("All") }
    var message by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedDocumentIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }

    val visibleDocuments = when (selectedFilter) {
        "Recent" -> recentDocuments
        "Favorites" -> favoriteDocuments
        else -> allDocuments
    }
    val selectedDocuments = visibleDocuments.filter { it.id in selectedDocumentIds }
    val allVisibleSelected = visibleDocuments.isNotEmpty() &&
            visibleDocuments.all { it.id in selectedDocumentIds }

    fun toggleDocumentSelection(document: DocumentEntity) {
        selectedDocumentIds = if (document.id in selectedDocumentIds) {
            selectedDocumentIds - document.id
        } else {
            selectedDocumentIds + document.id
        }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedDocumentIds = emptySet()
    }

    BackHandler(enabled = selectionMode) {
        exitSelectionMode()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exitSelectionMode() }) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Exit selection mode"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${selectedDocumentIds.size} selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DocDoomDark
                    )
                    Text(
                        text = "Tap files to select or unselect",
                        style = MaterialTheme.typography.bodySmall,
                        color = DocDoomMuted
                    )
                }

                TextButton(
                    onClick = {
                        selectedDocumentIds = if (allVisibleSelected) {
                            emptySet()
                        } else {
                            visibleDocuments.map { it.id }.toSet()
                        }
                    },
                    enabled = visibleDocuments.isNotEmpty()
                ) {
                    Text(if (allVisibleSelected) "Deselect all" else "Select all")
                }
            }
        } else {
            Text(
                text = "Your files",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = DocDoomDark
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Your saved documents, all in one place.",
                style = MaterialTheme.typography.bodyMedium,
                color = DocDoomMuted
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Recent", "Favorites").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = {
                        selectedFilter = filter
                        // Selection is scoped to the current filter. Switching tabs
                        // exits selection mode so actions cannot target hidden items.
                        exitSelectionMode()
                    },
                    label = { Text(filter) },
                    enabled = !selectionMode
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (visibleDocuments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = DocDoomEmerald,
                        modifier = Modifier.size(46.dp)
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = when (selectedFilter) {
                            "Recent" -> "No recent documents"
                            "Favorites" -> "No favorite documents yet"
                            else -> "Your library is empty"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DocDoomDark
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Create a PDF with Images to PDF and save it to see it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DocDoomMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = visibleDocuments,
                    key = { it.id }
                ) { document ->
                    val isSelected = document.id in selectedDocumentIds

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        toggleDocumentSelection(document)
                                    } else {
                                        onOpenDocument(document)
                                        message = ""
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedDocumentIds = setOf(document.id)
                                    } else {
                                        toggleDocumentSelection(document)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                DocDoomWhite
                            }
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isSelected) 2.dp else 1.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 10.dp,
                                    top = 10.dp,
                                    bottom = 10.dp,
                                    end = 6.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        toggleDocumentSelection(document)
                                    }
                                )
                            } else {
                                Icon(
                                    imageVector = if (document.mimeType.startsWith("image/")) {
                                        Icons.Outlined.Collections
                                    } else {
                                        Icons.Outlined.PictureAsPdf
                                    },
                                    contentDescription = if (
                                        document.mimeType.startsWith("image/")
                                    ) {
                                        "Image document"
                                    } else {
                                        "PDF document"
                                    },
                                    tint = DocDoomEmerald,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(34.dp)
                                )
                            }

                            Spacer(Modifier.size(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = document.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DocDoomDark,
                                    maxLines = 1
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = formatDocumentDetails(document),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DocDoomMuted
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            repository.updateDocument(
                                                document.copy(
                                                    isFavorite = !document.isFavorite
                                                )
                                            )
                                            message = ""
                                        } catch (error: Exception) {
                                            message = "Couldn't update favorite: ${
                                                error.localizedMessage ?: "Please try again."
                                            }"
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (document.isFavorite) {
                                        Icons.Outlined.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                    contentDescription = if (document.isFavorite) {
                                        "Remove from favorites"
                                    } else {
                                        "Add to favorites"
                                    },
                                    tint = DocDoomEmerald
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (selectedDocuments.isNotEmpty()) {
                                shareDocuments(context, selectedDocuments)
                            } else {
                                message = "Select at least one file to share."
                            }
                        },
                        enabled = selectedDocuments.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Share")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { showRemoveConfirmation = true },
                        enabled = selectedDocuments.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Remove")
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            Text(
                text = message,
                color = DocDoomMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = {
                Text("Remove ${selectedDocuments.size} files from DocDoom?")
            },
            text = {
                Text(
                    "These entries will be removed from All, Recent and Favorites. " +
                            "The original files on your phone will not be deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmation = false
                        val documentsToRemove = selectedDocuments.toList()
                        scope.launch {
                            try {
                                documentsToRemove.forEach { document ->
                                    repository.deleteDocument(document)
                                }
                                message = "${documentsToRemove.size} files removed from DocDoom. Original files are safe."
                                exitSelectionMode()
                            } catch (error: Exception) {
                                message = "Couldn't remove all selected files: ${
                                    error.localizedMessage ?: "Please try again."
                                }"
                            }
                        }
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveConfirmation = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


private fun shareDocument(context: android.content.Context, document: DocumentEntity) {
    shareDocuments(context, listOf(document))
}

private fun shareDocuments(
    context: android.content.Context,
    documents: List<DocumentEntity>
) {
    if (documents.isEmpty()) return

    val uris = documents.map { Uri.parse(it.uri) }

    val commonMimeType = documents
        .map { it.mimeType.ifBlank { "*/*" } }
        .distinct()
        .singleOrNull() ?: "*/*"

    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = commonMimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMimeType
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(uris)
            )
        }
    }.apply {
        clipData = ClipData.newUri(
            context.contentResolver,
            documents.first().displayName,
            uris.first()
        ).also { clip ->
            uris.drop(1).forEach { uri ->
                clip.addItem(ClipData.Item(uri))
            }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                if (documents.size == 1) "Share document" else "Share ${documents.size} files"
            )
        )
    } catch (_: ActivityNotFoundException) {
        // Keep all library records unchanged if no share target is available.
    } catch (_: Exception) {
        // Do not modify source files or library records when sharing fails.
    }
}

private fun formatDocumentDetails(document: DocumentEntity): String {
    val date = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault()
    ).format(Date(document.createdAt))

    val size = when {
        document.sizeBytes < 1024L -> "${document.sizeBytes} B"
        document.sizeBytes < 1024L * 1024L ->
            String.format(Locale.getDefault(), "%.1f KB", document.sizeBytes / 1024.0)
        else ->
            String.format(
                Locale.getDefault(),
                "%.1f MB",
                document.sizeBytes / (1024.0 * 1024.0)
            )
    }

    return "$date · $size"
}

@Composable
private fun ToolsScreen(
    onToolClick: (Tool) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {

        Spacer(Modifier.height(24.dp))

        Text(
            text = "All tools",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Choose what you want to do.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomMuted
        )

        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(
                availableTools,
                key = { it.title }
            ) { tool ->
                ToolCard(
                    title = tool.title,
                    description = tool.description,
                    icon = tool.icon,
                    onClick = {
                        onToolClick(tool)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolDetailScreen(
    tool: Tool,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        TextButton(
            onClick = onBack
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = null
            )
            Text("  Back")
        }

        Spacer(Modifier.height(28.dp))

        Icon(
            imageVector = tool.icon,
            contentDescription = null,
            tint = DocDoomEmerald,
            modifier = Modifier.size(52.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = tool.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = tool.description,
            style = MaterialTheme.typography.bodyLarge,
            color = DocDoomMuted
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "This tool is not enabled yet. Real file selection and processing will be added in a future milestone.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomDark
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = DocDoomEmerald
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Back to tools")
        }
    }
}

@Composable
private fun SimpleScreen(
    title: String,
    description: String,
    icon: ImageVector
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DocDoomEmerald,
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomMuted
        )
    }
}
