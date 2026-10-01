package com.focustube.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focustube.app.data.FocusSettings
import com.focustube.app.data.SettingsRepository
import com.focustube.app.domain.FocusStatus
import com.focustube.app.ui.components.appContainer
import com.focustube.app.ui.components.clockTicks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class FocusUiState(
    val now: Instant,
    val settings: FocusSettings,
    val status: FocusStatus,
    val locked: Boolean,
)

class FocusViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** Settings + the ticking clock = everything the Focus screen shows. */
    val uiState: StateFlow<FocusUiState?> =
        combine(settingsRepository.settings, clockTicks()) { settings, now ->
            val status = settings.focusStatus(now)
            FocusUiState(now, settings, status, locked = settings.strictMode && status.isActive)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun startSession(minutes: Int) {
        viewModelScope.launch { settingsRepository.startSession(minutes * 60_000L) }
    }

    fun endSession() {
        viewModelScope.launch { settingsRepository.endSession() }
    }

    fun setStrictMode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) settingsRepository.enableStrictMode() else settingsRepository.disableStrictMode()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { FocusViewModel(appContainer.settingsRepository) }
        }
    }
}
