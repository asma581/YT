package com.focustube.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustube.app.R
import com.focustube.app.domain.FilterDecision
import com.focustube.app.domain.VideoInfo
import com.focustube.app.ui.components.LockedBanner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        // The outer Scaffold already handles the status bar and the bottom navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val current = state) {
                PlayerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is PlayerUiState.Error -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(current.message, textAlign = TextAlign.Center)
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                is PlayerUiState.Ready -> ReadyContent(
                    state = current,
                    onTrustChange = { trusted -> viewModel.setChannelTrusted(current.video, trusted) },
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(state: PlayerUiState.Ready, onTrustChange: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        when (val decision = state.decision) {
            FilterDecision.Allowed -> EmbeddedPlayer(videoId = state.video.id, modifier = Modifier.fillMaxWidth())
            is FilterDecision.Blocked -> BlockedCard(state, decision.reason, onTrustChange)
        }
        VideoDetails(state, onTrustChange)
    }
}

@Composable
private fun BlockedCard(state: PlayerUiState.Ready, reason: String, onTrust: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.player_blocked_title), style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodyMedium)
            val canTrust = !state.channelTrusted && state.video.channelId.isNotEmpty()
            if (canTrust && !state.locked) {
                Text(stringResource(R.string.player_trust_explainer), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onTrust(true) }) {
                    Text(stringResource(R.string.player_trust_button, state.video.channelTitle))
                }
            }
        }
    }
    if (state.locked) {
        LockedBanner(
            stringResource(R.string.strict_locked_player),
            Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun VideoDetails(state: PlayerUiState.Ready, onTrustChange: (Boolean) -> Unit) {
    val video: VideoInfo = state.video
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Text(video.title, style = MaterialTheme.typography.titleLarge)
        Text(
            video.channelTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.channelTrusted) {
            TextButton(onClick = { onTrustChange(false) }, enabled = !state.locked) {
                Text(stringResource(R.string.player_untrust_button))
            }
        }
        // Don't show the description of blocked videos - it's often full of tempting links.
        if (state.decision == FilterDecision.Allowed && video.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                video.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
            }
        }
    }
}

/**
 * Wraps the YouTubePlayerView (a classic Android View) so it can live in Compose.
 * It plays through YouTube's official IFrame player, as YouTube's terms require.
 */
@Composable
private fun EmbeddedPlayer(videoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val playerView = remember(videoId) {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            val options = IFramePlayerOptions.Builder(context)
                .controls(1)
                .rel(0)          // At the end, suggest only videos from the same channel.
                .ivLoadPolicy(3) // Hide video annotations.
                .fullscreen(0)
                .build()
            initialize(
                object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                },
                options,
            )
        }
    }

    // Pause when the app goes to the background; free the WebView when we leave the screen.
    DisposableEffect(lifecycle, playerView) {
        lifecycle.addObserver(playerView)
        onDispose {
            lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(factory = { playerView }, modifier = modifier)
}
