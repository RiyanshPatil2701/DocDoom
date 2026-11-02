package com.example.docdoom.presentation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.docdoom.ui.theme.DocDoomDark
import com.example.docdoom.ui.theme.DocDoomEmerald
import com.example.docdoom.ui.theme.DocDoomMuted
import com.example.docdoom.ui.theme.DocDoomWhite

private data class CompressionCategory(
    val title: String,
    val description: String,
    val icon: ImageVector
)

private val compressionCategories = listOf(
    CompressionCategory(
        title = "Compress Image",
        description = "Reduce photo and image file sizes",
        icon = Icons.Outlined.Collections
    ),
    CompressionCategory(
        title = "Compress PDF",
        description = "Make PDF documents smaller",
        icon = Icons.Outlined.PictureAsPdf
    ),
    CompressionCategory(
        title = "Compress Document",
        description = "Optimise Word, Excel and PowerPoint files",
        icon = Icons.Outlined.Description
    ),
    CompressionCategory(
        title = "Compress File",
        description = "Create smaller ZIP archives",
        icon = Icons.Outlined.FolderZip
    )
)

@Composable
fun CompressScreen(
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        TextButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = null,
                tint = DocDoomEmerald
            )
            Text("  Back", color = DocDoomEmerald)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Compress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DocDoomDark
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Choose the type of file you want to make smaller.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocDoomMuted
        )

        Spacer(Modifier.height(22.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(compressionCategories, key = { it.title }) { category ->
                Card(
                    onClick = { onCategoryClick(category.title) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DocDoomWhite
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.height(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = DocDoomEmerald,
                                modifier = Modifier.height(30.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = DocDoomDark,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = category.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = DocDoomMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
