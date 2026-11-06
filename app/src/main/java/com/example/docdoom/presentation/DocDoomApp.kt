package com.example.docdoom.presentation
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Settings
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
import com.example.docdoom.presentation.tools.ImageToPdfScreen
import com.example.docdoom.presentation.tools.MergePdfScreen
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
        "Watermark PDF",
        "Mark your documents",
        Icons.Outlined.WaterDrop
    )
)

@Composable
fun DocDoomApp() {
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

    Scaffold(
        containerColor = DocDoomCanvas,
        bottomBar = {
            if (selectedTool == null) {
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
                                documentRepository.insertDocument(
                                    DocumentEntity(
                                        displayName = displayName,
                                        uri = uri.toString(),
                                        mimeType = "application/pdf",
                                        sizeBytes = sizeBytes,
                                        createdAt = System.currentTimeMillis(),
                                        isFavorite = false
                                    )
                                )
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
                                documentRepository.insertDocument(
                                    DocumentEntity(
                                        displayName = displayName,
                                        uri = uri.toString(),
                                        mimeType = "application/pdf",
                                        sizeBytes = sizeBytes,
                                        createdAt = System.currentTimeMillis(),
                                        isFavorite = false
                                    )
                                )
                            }
                        }
                    )
                    "Compress Image" -> CompressImageScreen(
                        onBack = {
                            selectedTool = availableTools.first { it.title == "Compress" }
                        },
                        onSaved = { uri, displayName, sizeBytes ->
                            scope.launch {
                                documentRepository.insertDocument(
                                    DocumentEntity(
                                        displayName = displayName,
                                        uri = uri.toString(),
                                        mimeType = "image/webp",
                                        sizeBytes = sizeBytes,
                                        createdAt = System.currentTimeMillis(),
                                        isFavorite = false
                                    )
                                )
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
                        }
                    )

                    Destination.Files -> FilesScreen(repository = documentRepository)

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
    }
}

@Composable
private fun HomeScreen(
    repository: DocumentRepository,
    onCreatePdf: () -> Unit,
    onMergePdf: () -> Unit,
    onCompress: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTools: () -> Unit
) {
    val recentDocuments by repository.recentDocuments.collectAsState(initial = emptyList())
    val favoriteDocuments by repository.favoriteDocuments.collectAsState(initial = emptyList())
    val context = LocalContext.current

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
                    onOpen = {
                        try {
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(document.uri), document.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(openIntent)
                        } catch (_: Exception) {
                            // Keep Home usable if no compatible PDF viewer is installed.
                        }
                    }
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
                    onOpen = {
                        try {
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(document.uri), document.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(openIntent)
                        } catch (_: Exception) {
                            // Keep Home usable if no compatible PDF viewer is installed.
                        }
                    }
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

@Composable
private fun FilesScreen(
    repository: DocumentRepository
) {
    val allDocuments by repository.allDocuments.collectAsState(initial = emptyList())
    val favoriteDocuments by repository.favoriteDocuments.collectAsState(initial = emptyList())
    val recentDocuments by repository.recentDocuments.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf("All") }
    var message by remember { mutableStateOf("") }

    val visibleDocuments = when (selectedFilter) {
        "Recent" -> recentDocuments
        "Favorites" -> favoriteDocuments
        else -> allDocuments
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

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

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Recent", "Favorites").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) }
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
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = visibleDocuments,
                    key = { it.id }
                ) { document ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = DocDoomWhite
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(
                                                Uri.parse(document.uri),
                                                document.mimeType
                                            )
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(openIntent)
                                        message = ""
                                    } catch (_: ActivityNotFoundException) {
                                        message = "No PDF viewer is available on this device."
                                    } catch (error: Exception) {
                                        message = "Couldn't open this document: ${
                                            error.localizedMessage ?: "Please try again."
                                        }"
                                    }
                                }
                                .padding(start = 14.dp, top = 13.dp, bottom = 13.dp, end = 6.dp),
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
                                modifier = Modifier.size(34.dp)
                            )

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
                                                document.copy(isFavorite = !document.isFavorite)
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

        if (message.isNotBlank()) {
            Text(
                text = message,
                color = DocDoomMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
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