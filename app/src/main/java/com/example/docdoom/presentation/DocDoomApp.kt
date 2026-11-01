package com.example.docdoom.presentation
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
import com.example.docdoom.presentation.tools.ImageToPdfScreen
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
        "Compress PDF",
        "Reduce document size",
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

                if (tool.title == "Images to PDF") {
                    ImageToPdfScreen(
                        onBack = {
                            selectedTool = null
                        }
                    )
                } else {
                    ToolDetailScreen(
                        tool = tool,
                        onBack = {
                            selectedTool = null
                        }
                    )
                }

            } ?: run {

                when (destination) {

                    Destination.Home -> HomeScreen(
                        onCreatePdf = {
                            selectedTool = availableTools.first()
                        },
                        onOpenFiles = {
                            destination = Destination.Files
                        },
                        onOpenTools = {
                            destination = Destination.Tools
                        }
                    )

                    Destination.Files -> FilesScreen()

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
    onCreatePdf: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTools: () -> Unit
) {

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
                onClick = onOpenTools
            )

            QuickActionItem(
                title = "Compress",
                icon = Icons.Outlined.ArrowDownward,
                modifier = Modifier.weight(1f),
                onClick = onOpenTools
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = DocDoomEmerald,
                    modifier = Modifier.size(27.dp)
                )
            }

            Spacer(Modifier.size(12.dp))

            Column {
                Text(
                    text = "No documents yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DocDoomDark
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = "Your created documents will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DocDoomMuted
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

        Text(
            text = "Your starred documents will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomMuted,
            modifier = Modifier.padding(vertical = 8.dp)
        )
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
private fun FilesScreen() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your files",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Your document workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomMuted
        )

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = DocDoomEmerald,
                    modifier = Modifier.size(46.dp)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Your library is coming soon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DocDoomDark
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Created and imported documents will be organized here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DocDoomMuted
                )
            }
        }
    }
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

