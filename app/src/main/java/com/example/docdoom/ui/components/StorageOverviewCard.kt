package com.example.docdoom.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.docdoom.data.storage.StoragePermissionHelper
import com.example.docdoom.data.storage.StorageRepository
import com.example.docdoom.data.storage.StorageSnapshot
import com.example.docdoom.data.preferences.StoragePreferences
import com.example.docdoom.ui.theme.DocDoomDark
import com.example.docdoom.ui.theme.DocDoomEmerald
import com.example.docdoom.ui.theme.DocDoomMuted
import com.example.docdoom.ui.theme.DocDoomWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageOverviewCard(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var snapshot by remember {
        mutableStateOf<StorageSnapshot?>(null)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var loadError by remember {
        mutableStateOf(false)
    }

    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    // Read the saved preference; the card starts expanded on first launch.
    val isExpanded by StoragePreferences
        .storageCardExpanded(context)
        .collectAsState(initial = true)

    val preferenceScope = rememberCoroutineScope()

    fun toggleExpanded() {
        preferenceScope.launch {
            StoragePreferences.setStorageCardExpanded(
                context = context,
                expanded = !isExpanded
            )
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            refreshKey++
        }

    LaunchedEffect(refreshKey) {
        isLoading = true
        loadError = false

        try {
            snapshot = withContext(Dispatchers.IO) {
                StorageRepository.readStorage(context)
            }
        } catch (e: Exception) {
            loadError = true
        }

        isLoading = false
    }

    val hasImageAccess =
        StoragePermissionHelper.hasImageAccess(context)

    val hasVideoAccess =
        StoragePermissionHelper.hasVideoAccess(context)

    val hasAudioAccess =
        StoragePermissionHelper.hasAudioAccess(context)

    val hasAllMediaAccess =
        hasImageAccess && hasVideoAccess && hasAudioAccess

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = DocDoomWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Tap the title area or chevron to expand/collapse details.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            toggleExpanded()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = DocDoomEmerald,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.size(8.dp))

                    Text(
                        text = "Phone storage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DocDoomDark
                    )

                    Spacer(Modifier.size(4.dp))

                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = if (isExpanded) {
                            "Collapse storage details"
                        } else {
                            "Expand storage details"
                        },
                        tint = DocDoomMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                TextButton(
                    onClick = { refreshKey++ },
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh storage",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when {
                isLoading -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = DocDoomEmerald
                    )

                    Text(
                        text = "Reading storage...",
                        style = MaterialTheme.typography.bodySmall,
                        color = DocDoomMuted
                    )
                }

                loadError || snapshot == null -> {
                    Text(
                        text = "Storage information couldn't be loaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DocDoomMuted
                    )

                    TextButton(
                        onClick = { refreshKey++ }
                    ) {
                        Text("Try again")
                    }
                }

                else -> {
                    val data = snapshot!!

                    val usedPercentage =
                        if (data.totalBytes > 0L) {
                            (
                                    data.usedBytes.toFloat() /
                                            data.totalBytes.toFloat()
                                    ).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                    // These key figures remain visible when details are collapsed.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Used",
                                style = MaterialTheme.typography.bodySmall,
                                color = DocDoomMuted
                            )

                            Text(
                                text = formatBytes(data.usedBytes),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = DocDoomDark
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Available",
                                style = MaterialTheme.typography.bodySmall,
                                color = DocDoomMuted
                            )

                            Text(
                                text = formatBytes(data.availableBytes),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = DocDoomEmerald
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = usedPercentage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp),
                        color = DocDoomEmerald,
                        trackColor = Color(0xFFE8F0EC)
                    )

                    Text(
                        text = "${formatBytes(data.totalBytes)} total",
                        style = MaterialTheme.typography.labelMedium,
                        color = DocDoomMuted
                    )

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Storage breakdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = DocDoomDark
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StorageCategoryCell(
                                    title = "Photos",
                                    size = data.photosBytes,
                                    icon = Icons.Outlined.Image,
                                    isAccessible = hasImageAccess,
                                    modifier = Modifier.weight(1f)
                                )

                                StorageCategoryCell(
                                    title = "Videos",
                                    size = data.videosBytes,
                                    icon = Icons.Outlined.Movie,
                                    isAccessible = hasVideoAccess,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StorageCategoryCell(
                                    title = "Audio",
                                    size = data.audioBytes,
                                    icon = Icons.Outlined.MusicNote,
                                    isAccessible = hasAudioAccess,
                                    modifier = Modifier.weight(1f)
                                )

                                StorageCategoryCell(
                                    title = "Documents",
                                    size = data.documentsBytes,
                                    icon = Icons.Outlined.Description,
                                    isAccessible = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StorageCategoryCell(
                                    title = "Others",
                                    size = data.othersBytes,
                                    icon = Icons.Outlined.Folder,
                                    isAccessible = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(Modifier.weight(1f))
                            }

                            if (!hasAllMediaAccess) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Media access is incomplete. Allow access to measure all available photos, videos and audio.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DocDoomMuted
                                    )

                                    TextButton(
                                        onClick = {
                                            permissionLauncher.launch(
                                                StoragePermissionHelper.requiredPermissions()
                                            )
                                        }
                                    ) {
                                        Text(
                                            text = "Allow media access",
                                            color = DocDoomEmerald
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Category sizes include files accessible to DocDoom, not all system or app data.",
                                style = MaterialTheme.typography.labelSmall,
                                color = DocDoomMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCategoryCell(
    title: String,
    size: Long,
    icon: ImageVector,
    isAccessible: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DocDoomEmerald,
            modifier = Modifier.size(19.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = DocDoomMuted
            )

            Text(
                text = if (isAccessible) {
                    formatBytes(size)
                } else {
                    "Access needed"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = DocDoomDark
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "—"

    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val tb = gb * 1024.0

    return when {
        bytes >= tb ->
            String.format("%.2f TB", bytes / tb)

        bytes >= gb ->
            String.format("%.2f GB", bytes / gb)

        bytes >= mb ->
            String.format("%.1f MB", bytes / mb)

        bytes >= kb ->
            String.format("%.1f KB", bytes / kb)

        else -> "$bytes B"
    }
}
