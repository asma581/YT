package com.focustube.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focustube.app.data.SettingsRepository
import com.focustube.app.data.youtube.VideoRepository
import com.focustube.app.domain.ContentFilter
import com.focustube.app.domain.FilterDecision
import com.focustube.app.domain.TrustedChannel
import com.focustube.app.domain.VideoInfo
import com.focustube.app.ui.Routes
import com.focustube.app.ui.components.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val message: String) : PlayerUiState
    data class Ready(
        val video: VideoInfo,
        val decision: FilterDecision,
        val channelTrusted: Boolean,
        val locked: Boolean,
    ) : PlayerUiState
}

class PlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Navigation puts the "{videoId}" from the route into the SavedStateHandle for us. */
    private val videoId: String = checkNotNull(savedStateHandle[Routes.VIDEO_ID_ARG])

    private sealed interface Load {
        data object InProgress : Load
        data class Failed(val message: String) : Load
        data class Done(val video: VideoInfo?) : Load
    }

    private val load = MutableStateFlow<Load>(Load.InProgress)

    /**
     * The filter runs again whenever settings change, so trusting a channel
     * immediately unblocks its video.
     */
    val uiState: StateFlow<PlayerUiState> = combine(load, settingsRepository.settings) { load, settings ->
        when (load) {
            Load.InProgress -> PlayerUiState.Loading
            is Load.Failed -> PlayerUiState.Error(load.message)
            is Load.Done -> {
                val video = load.video ?: return@combine PlayerUiState.Error("This video isn't available.")
                PlayerUiState.Ready(
                    video = video,
                    decision = ContentFilter(settings.filterRules()).evaluate(video),
                    channelTrusted = settings.trustedChannels.any { it.id == video.channelId },
                    locked = settings.isLocked(),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState.Loading)

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch {
            load.value = Load.InProgress
            load.value = try {
                Load.Done(videoRepository.video(videoId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(VideoRepository.describe(e))
            }
        }
    }

    fun setChannelTrusted(video: VideoInfo, trusted: Boolean) {
        if (video.channelId.isEmpty()) return
        viewModelScope.launch {
            if (trusted) {
                settingsRepository.trustChannel(TrustedChannel(video.channelId, video.channelTitle))
            } else {
                settingsRepository.untrustChannel(video.channelId)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PlayerViewModel(createSavedStateHandle(), appContainer.videoRepository, appContainer.settingsRepository)
            }
        }
    }
}
