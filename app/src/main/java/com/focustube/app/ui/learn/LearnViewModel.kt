package com.focustube.app.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focustube.app.data.SettingsRepository
import com.focustube.app.data.youtube.VideoRepository
import com.focustube.app.domain.ContentFilter
import com.focustube.app.domain.VideoInfo
import com.focustube.app.ui.components.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnUiState(
    val query: String = "",
    /** The query the current results belong to (the text field may have changed since). */
    val searchedQuery: String? = null,
    val videos: List<VideoInfo> = emptyList(),
    /** How many results the filter removed - shown so the user knows filtering happened. */
    val hiddenCount: Int = 0,
    val nextPageToken: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class LearnViewModel(
    private val videoRepository: VideoRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search(query: String = _uiState.value.query) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        _uiState.update { it.copy(query = clean) }
        load(clean, pageToken = null)
    }

    fun loadMore() {
        val state = _uiState.value
        val query = state.searchedQuery ?: return
        val token = state.nextPageToken ?: return
        if (state.isLoading) return
        load(query, token)
    }

    private fun load(query: String, pageToken: String?) {
        val isFirstPage = pageToken == null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = videoRepository.search(query, pageToken)
                val filter = ContentFilter(settingsRepository.settings.first().filterRules())
                val (allowed, hidden) = page.videos.partition(filter::isAllowed)
                _uiState.update { state ->
                    val previous = if (isFirstPage) emptyList() else state.videos
                    state.copy(
                        searchedQuery = query,
                        videos = (previous + allowed).distinctBy { it.id },
                        hiddenCount = (if (isFirstPage) 0 else state.hiddenCount) + hidden.size,
                        nextPageToken = page.nextPageToken,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = VideoRepository.describe(e)) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LearnViewModel(appContainer.videoRepository, appContainer.settingsRepository) }
        }
    }
}
