package com.example.docdoom.presentation.tools

import android.net.Uri
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun PdfViewerScreen(
    uri: Uri,
    documentName: String,
    onBack: () -> Unit
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? FragmentActivity
    val documentReady = remember(uri) { mutableStateOf(false) }
    val activeFragment = remember(uri) { mutableStateOf<DocDoomPdfViewerFragment?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                Text(
                    text = documentName,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    enabled = documentReady.value,
                    onClick = {
                        val fragment = activeFragment.value
                        if (fragment != null && fragment.isAdded) {
                            fragment.isTextSearchActive = !fragment.isTextSearchActive
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Find in PDF",
                        tint = if (documentReady.value) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }

        if (activity == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("PDF viewer could not connect to the Android screen.")
            }
        } else {
            key(uri) {
                val fragmentTag = remember(uri) {
                    "DocDoomPdfViewer_${uri.hashCode()}"
                }
                val containerId = remember(uri) { View.generateViewId() }
                val containerState = remember(uri) {
                    mutableStateOf<FragmentContainerView?>(null)
                }
                val fragmentManager = activity.supportFragmentManager

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        FragmentContainerView(context).apply {
                            id = containerId
                            containerState.value = this
                        }
                    },
                    update = { view ->
                        if (containerState.value !== view) {
                            containerState.value = view
                        }
                    }
                )

                val container = containerState.value

                LaunchedEffect(container, uri, fragmentTag) {
                    val host = container ?: return@LaunchedEffect
                    host.awaitAttachedToWindow()

                    if (activity.isFinishing || activity.isDestroyed) {
                        return@LaunchedEffect
                    }
                    if (fragmentManager.isStateSaved) {
                        return@LaunchedEffect
                    }

                    var fragment =
                        fragmentManager.findFragmentByTag(fragmentTag) as? DocDoomPdfViewerFragment

                    if (fragment == null) {
                        fragment = DocDoomPdfViewerFragment()
                        fragmentManager.beginTransaction()
                            .replace(host.id, fragment, fragmentTag)
                            .commitNow()
                    }

                    activeFragment.value = fragment

                    if (fragment.isAdded) {
                        fragment.documentUri = uri
                        // The native fragment owns document loading and search UI.
                        // Enable the search action once the URI has been handed to it.
                        documentReady.value = true
                    }
                }

                DisposableEffect(fragmentManager, fragmentTag) {
                    onDispose {
                        activeFragment.value = null
                        documentReady.value = false
                        val existing = fragmentManager.findFragmentByTag(fragmentTag)
                        if (existing != null && !fragmentManager.isStateSaved) {
                            fragmentManager.beginTransaction()
                                .remove(existing)
                                .commitAllowingStateLoss()
                        }
                    }
                }
            }
        }
    }
}

private suspend fun FragmentContainerView.awaitAttachedToWindow() {
    if (isAttachedToWindow) return

    suspendCancellableCoroutine<Unit> { continuation ->
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        }

        addOnAttachStateChangeListener(listener)
        if (isAttachedToWindow) {
            removeOnAttachStateChangeListener(listener)
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }

        continuation.invokeOnCancellation {
            removeOnAttachStateChangeListener(listener)
        }
    }
}


/**
 * Top-level, public, no-argument Fragment subclass.
 * Android can recreate this class after process/activity restoration.
 * The annotation toolbox is hidden only after the PDF document has loaded,
 * when the fragment's internal views are ready.
 */
class DocDoomPdfViewerFragment : PdfViewerFragment() {
    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        isToolboxVisible = false
    }

    // The base implementation may show the annotation toolbox again when
    // immersive mode changes. Keep it hidden for this read-only viewer.
    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        isToolboxVisible = false
    }
}
