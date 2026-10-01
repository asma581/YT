package com.focustube.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.focustube.app.domain.YouTubeLinks
import com.focustube.app.ui.FocusTubeAppUi
import com.focustube.app.ui.theme.FocusTubeTheme

/**
 * The app's single Activity. All screens are Compose destinations inside it.
 *
 * It also receives YouTube links: either "Share -> FocusTube" from another app, or
 * tapping a youtube.com link and choosing FocusTube. The video then opens in our
 * player, which only plays it if it passes the educational filter.
 */
class MainActivity : ComponentActivity() {

    /** A video ID from an incoming link, waiting for the UI to open it. */
    private val pendingVideoId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FocusTubeTheme {
                FocusTubeAppUi(
                    pendingVideoId = pendingVideoId.value,
                    onPendingVideoHandled = { pendingVideoId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        YouTubeLinks.extractVideoId(text)?.let { pendingVideoId.value = it }
    }
}
