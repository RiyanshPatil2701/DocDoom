package com.example.docdoom

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.docdoom.presentation.DocDoomApp
import com.example.docdoom.ui.theme.DocDoomTheme

class MainActivity : AppCompatActivity() {

    private var incomingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        incomingPdfUri = extractPdfUri(intent)

        setContent {
            DocDoomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DocDoomApp(
                        incomingPdfUri = incomingPdfUri,
                        onIncomingPdfHandled = {
                            incomingPdfUri = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPdfUri = extractPdfUri(intent)
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent == null) return null

        val candidate: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data

            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.uri
            }

            else -> null
        }

        if (candidate == null) return null

        val mimeType = intent.type.orEmpty()
        val looksLikePdf =
            mimeType.equals("application/pdf", ignoreCase = true) ||
                    candidate.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)

        return candidate.takeIf { looksLikePdf }
    }
}
