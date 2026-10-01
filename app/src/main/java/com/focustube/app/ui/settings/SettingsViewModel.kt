package com.focustube.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focustube.app.BuildConfig
import com.focustube.app.data.FocusSettings
import com.focustube.app.data.SettingsRepository
import com.focustube.app.domain.DailySchedule
import com.focustube.app.ui.components.appContainer
import com.focustube.app.ui.components.clockTicks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

data class SettingsUiState(
    val settings: FocusSettings,
    val locked: Boolean,
    val hasBuiltInApiKey: Boolean,
)

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState?> =
        combine(repository.settings, clockTicks(periodMillis = 5_000)) { settings, now ->
            SettingsUiState(settings, settings.isLocked(now), BuildConfig.YOUTUBE_API_KEY.isNotBlank())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    private val schedule: DailySchedule? get() = uiState.value?.settings?.schedule

    fun setScheduleEnabled(enabled: Boolean) = updateSchedule { it.copy(enabled = enabled) }

    fun toggleDay(day: DayOfWeek) = updateSchedule { s ->
        s.copy(days = if (day in s.days) s.days - day else s.days + day)
    }

    fun setStart(minuteOfDay: Int) = updateSchedule { it.copy(startMinute = minuteOfDay) }

    fun setEnd(minuteOfDay: Int) = updateSchedule { it.copy(endMinute = minuteOfDay) }

    private fun updateSchedule(change: (DailySchedule) -> DailySchedule) {
        val current = schedule ?: return
        viewModelScope.launch { repository.setSchedule(change(current)) }
    }

    fun setCategoryAllowed(id: String, allowed: Boolean) {
        viewModelScope.launch { repository.setCategoryAllowed(id, allowed) }
    }

    fun setBlockShorts(block: Boolean) {
        viewModelScope.launch { repository.setBlockShorts(block) }
    }

    fun addKeyword(keyword: String) {
        viewModelScope.launch { repository.addBlockedKeyword(keyword) }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch { repository.removeBlockedKeyword(keyword) }
    }

    fun untrustChannel(id: String) {
        viewModelScope.launch { repository.untrustChannel(id) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { repository.setApiKey(key) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(appContainer.settingsRepository) }
        }
    }
}
