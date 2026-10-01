package com.focustube.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A weekly repeating focus window, e.g. Monday-Friday 09:00-17:00.
 *
 * Times are minutes since midnight. If [endMinute] is earlier than [startMinute] the
 * window runs past midnight (22:00-02:00), and [days] are the days the window *starts* on.
 */
data class DailySchedule(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of range: $endMinute" }
    }

    private val crossesMidnight get() = endMinute < startMinute

    /** A window that starts and ends at the same minute is treated as empty. */
    private val isUsable get() = enabled && days.isNotEmpty() && startMinute != endMinute

    fun isActive(at: LocalDateTime): Boolean = currentWindowEnd(at) != null

    /** When the window we're currently inside ends, or null if we're not inside one. */
    fun currentWindowEnd(at: LocalDateTime): LocalDateTime? {
        if (!isUsable) return null
        val minute = at.hour * 60 + at.minute
        val today = at.dayOfWeek
        val date = at.toLocalDate()
        val end = LocalTime.of(endMinute / 60, endMinute % 60)
        return if (!crossesMidnight) {
            if (today in days && minute >= startMinute && minute < endMinute) date.atTime(end) else null
        } else when {
            today in days && minute >= startMinute -> date.plusDays(1).atTime(end)
            today.minus(1) in days && minute < endMinute -> date.atTime(end)
            else -> null
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.filter { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY }.toSet()
    }
}

enum class FocusSource { SESSION, SCHEDULE }

data class FocusStatus(
    val isActive: Boolean,
    /** When blocking stops, if it's active. */
    val endsAt: Instant? = null,
    val sources: Set<FocusSource> = emptySet(),
) {
    companion object {
        val INACTIVE = FocusStatus(isActive = false)
    }
}

object FocusRules {

    /**
     * Focus is active if a manual session is running OR we're inside the schedule.
     * If both are active, blocking lasts until the later of the two ends.
     */
    fun status(
        now: Instant,
        zone: ZoneId,
        sessionEndsAt: Instant?,
        schedule: DailySchedule,
    ): FocusStatus {
        val sources = mutableSetOf<FocusSource>()
        var endsAt: Instant? = null

        if (sessionEndsAt != null && sessionEndsAt.isAfter(now)) {
            sources += FocusSource.SESSION
            endsAt = sessionEndsAt
        }
        schedule.currentWindowEnd(LocalDateTime.ofInstant(now, zone))?.let { windowEnd ->
            sources += FocusSource.SCHEDULE
            val windowEndInstant = windowEnd.atZone(zone).toInstant()
            endsAt = if (endsAt == null || windowEndInstant.isAfter(endsAt)) windowEndInstant else endsAt
        }
        return if (sources.isEmpty()) FocusStatus.INACTIVE else FocusStatus(true, endsAt, sources)
    }

    /**
     * Strict mode "locks" the settings while focus is active, so you can't weaken the
     * rules (end the session, allow more categories, trust a channel...) in a weak moment.
     */
    fun isLocked(strictMode: Boolean, status: FocusStatus): Boolean = strictMode && status.isActive
}
