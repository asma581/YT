package com.focustube.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focustube.app.domain.DailySchedule
import com.focustube.app.domain.FilterRules
import com.focustube.app.domain.FocusRules
import com.focustube.app.domain.FocusStatus
import com.focustube.app.domain.TrustedChannel
import com.focustube.app.domain.YouTubeCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Everything the user has configured, as one immutable snapshot. */
data class FocusSettings(
    val sessionEndsAtMillis: Long? = null,
    val strictMode: Boolean = false,
    val schedule: DailySchedule = DailySchedule(),
    val allowedCategoryIds: Set<String> = YouTubeCategories.DEFAULT_ALLOWED,
    val blockedKeywords: Set<String> = FilterRules.DEFAULT_BLOCKED_KEYWORDS,
    val trustedChannels: List<TrustedChannel> = emptyList(),
    val blockShorts: Boolean = true,
    val apiKeyOverride: String = "",
) {
    fun filterRules() = FilterRules(
        allowedCategoryIds = allowedCategoryIds,
        blockedKeywords = blockedKeywords,
        trustedChannelIds = trustedChannels.mapTo(mutableSetOf()) { it.id },
        blockShorts = blockShorts,
    )

    fun focusStatus(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): FocusStatus =
        FocusRules.status(now, zone, sessionEndsAtMillis?.let(Instant::ofEpochMilli), schedule)

    fun isLocked(now: Instant = Instant.now()): Boolean = FocusRules.isLocked(strictMode, focusStatus(now))
}

val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_settings")

/**
 * Reads and writes [FocusSettings] with Jetpack DataStore.
 *
 * Strict mode is enforced *here*, not only in the UI: while settings are locked, any
 * change that would weaken the rules is ignored and the function returns false.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<FocusSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toFocusSettings() }

    // --- Changes that make the rules stricter: always allowed. ---

    /** Starts a session, or extends the current one if it would end sooner. */
    suspend fun startSession(durationMillis: Long) {
        dataStore.edit { prefs ->
            val newEnd = System.currentTimeMillis() + durationMillis
            val currentEnd = prefs[Keys.SESSION_ENDS_AT] ?: 0L
            prefs[Keys.SESSION_ENDS_AT] = maxOf(newEnd, currentEnd)
        }
    }

    suspend fun enableStrictMode() {
        dataStore.edit { it[Keys.STRICT_MODE] = true }
    }

    suspend fun addBlockedKeyword(keyword: String) {
        val clean = keyword.trim().lowercase()
        if (clean.isEmpty()) return
        dataStore.edit { prefs -> prefs[Keys.BLOCKED_KEYWORDS] = prefs.toFocusSettings().blockedKeywords + clean }
    }

    // --- Changes that could weaken the rules: refused while locked. ---

    suspend fun endSession(): Boolean = editUnlessLocked { it.remove(Keys.SESSION_ENDS_AT) }

    suspend fun disableStrictMode(): Boolean = editUnlessLocked { it[Keys.STRICT_MODE] = false }

    suspend fun setSchedule(schedule: DailySchedule): Boolean = editUnlessLocked { prefs ->
        prefs[Keys.SCHEDULE_ENABLED] = schedule.enabled
        prefs[Keys.SCHEDULE_DAYS] = schedule.days.mapTo(mutableSetOf()) { it.name }
        prefs[Keys.SCHEDULE_START] = schedule.startMinute
        prefs[Keys.SCHEDULE_END] = schedule.endMinute
    }

    suspend fun setCategoryAllowed(categoryId: String, allowed: Boolean): Boolean = editUnlessLocked { prefs ->
        val current = prefs.toFocusSettings().allowedCategoryIds
        prefs[Keys.ALLOWED_CATEGORIES] = if (allowed) current + categoryId else current - categoryId
    }

    suspend fun removeBlockedKeyword(keyword: String): Boolean = editUnlessLocked { prefs ->
        prefs[Keys.BLOCKED_KEYWORDS] = prefs.toFocusSettings().blockedKeywords - keyword
    }

    suspend fun setBlockShorts(block: Boolean): Boolean = editUnlessLocked { it[Keys.BLOCK_SHORTS] = block }

    suspend fun trustChannel(channel: TrustedChannel): Boolean = editUnlessLocked { prefs ->
        val others = prefs.toFocusSettings().trustedChannels.filterNot { it.id == channel.id }
        prefs[Keys.TRUSTED_CHANNELS] = (others + channel).mapTo(mutableSetOf()) { it.encode() }
    }

    suspend fun untrustChannel(channelId: String): Boolean = editUnlessLocked { prefs ->
        val remaining = prefs.toFocusSettings().trustedChannels.filterNot { it.id == channelId }
        prefs[Keys.TRUSTED_CHANNELS] = remaining.mapTo(mutableSetOf()) { it.encode() }
    }

    suspend fun setApiKey(key: String): Boolean = editUnlessLocked { it[Keys.API_KEY] = key.trim() }

    /** Runs [change] only if strict mode isn't currently locking the settings. */
    private suspend fun editUnlessLocked(change: (MutablePreferences) -> Unit): Boolean {
        var applied = false
        dataStore.edit { prefs ->
            if (!prefs.toFocusSettings().isLocked()) {
                change(prefs)
                applied = true
            }
        }
        return applied
    }

    private object Keys {
        val SESSION_ENDS_AT = longPreferencesKey("session_ends_at")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCHEDULE_DAYS = stringSetPreferencesKey("schedule_days")
        val SCHEDULE_START = intPreferencesKey("schedule_start_minute")
        val SCHEDULE_END = intPreferencesKey("schedule_end_minute")
        val ALLOWED_CATEGORIES = stringSetPreferencesKey("allowed_categories")
        val BLOCKED_KEYWORDS = stringSetPreferencesKey("blocked_keywords")
        val TRUSTED_CHANNELS = stringSetPreferencesKey("trusted_channels")
        val BLOCK_SHORTS = booleanPreferencesKey("block_shorts")
        val API_KEY = stringPreferencesKey("api_key")
    }

    private fun Preferences.toFocusSettings(): FocusSettings {
        val defaults = FocusSettings()
        val defaultSchedule = defaults.schedule
        return FocusSettings(
            sessionEndsAtMillis = this[Keys.SESSION_ENDS_AT],
            strictMode = this[Keys.STRICT_MODE] ?: defaults.strictMode,
            schedule = DailySchedule(
                enabled = this[Keys.SCHEDULE_ENABLED] ?: defaultSchedule.enabled,
                days = this[Keys.SCHEDULE_DAYS]
                    ?.mapNotNullTo(mutableSetOf()) { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                    ?: defaultSchedule.days,
                startMinute = this[Keys.SCHEDULE_START] ?: defaultSchedule.startMinute,
                endMinute = this[Keys.SCHEDULE_END] ?: defaultSchedule.endMinute,
            ),
            // A missing key means "never changed", so we use the defaults. An empty set is a real choice.
            allowedCategoryIds = this[Keys.ALLOWED_CATEGORIES] ?: defaults.allowedCategoryIds,
            blockedKeywords = this[Keys.BLOCKED_KEYWORDS] ?: defaults.blockedKeywords,
            trustedChannels = this[Keys.TRUSTED_CHANNELS].orEmpty()
                .mapNotNull { decodeTrustedChannel(it) }
                .sortedBy { it.title.lowercase() },
            blockShorts = this[Keys.BLOCK_SHORTS] ?: defaults.blockShorts,
            apiKeyOverride = this[Keys.API_KEY] ?: defaults.apiKeyOverride,
        )
    }

    // Channel IDs never contain a tab, so "id<TAB>title" is a safe way to store both.
    private fun TrustedChannel.encode() = "$id\t$title"

    private fun decodeTrustedChannel(stored: String): TrustedChannel? {
        val parts = stored.split('\t', limit = 2)
        if (parts[0].isBlank()) return null
        return TrustedChannel(id = parts[0], title = parts.getOrElse(1) { parts[0] })
    }
}
