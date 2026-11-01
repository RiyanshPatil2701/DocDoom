
package com.example.docdoom.presentation

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.History
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
import com.example.docdoom.ui.theme.DocDoomCanvas
import com.example.docdoom.ui.theme.DocDoomDark
import com.example.docdoom.ui.theme.DocDoomEmerald
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
    Tools("Tools", Icons.Outlined.Tune),
    History("History", Icons.Outlined.History),
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
                        onToolClick = {
                            selectedTool = it
                        },
                        onSeeAll = {
                            destination = Destination.Tools
                        }
                    )

                    Destination.Tools -> ToolsScreen(
                        onToolClick = {
                            selectedTool = it
                        }
                    )

                    Destination.History -> SimpleScreen(
                        title = "Your history",
                        description = "Completed files will appear here once file processing and local history are implemented.",
                        icon = Icons.Outlined.History
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
    onToolClick: (Tool) -> Unit,
    onSeeAll: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {

        Spacer(Modifier.height(24.dp))

        Text(
            text = "DocDoom",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = DocDoomEmerald
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Your documents. Under control.",
            style = MaterialTheme.typography.bodyLarge,
            color = DocDoomMuted
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Make documents simpler.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Useful tools for your everyday files.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomMuted
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "Quick tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = DocDoomDark
            )

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = onSeeAll
            ) {
                Text("See all")
            }
        }

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
            text = "This tool is not enabled yet. The next milestone will add real file selection and processing.",
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
