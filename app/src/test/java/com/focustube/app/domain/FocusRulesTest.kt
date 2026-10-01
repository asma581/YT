package com.focustube.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class FocusRulesTest {

    private val zone = ZoneId.of("UTC")

    // 2026-10-05 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)
    private fun instant(day: Int, hour: Int, minute: Int = 0) = at(day, hour, minute).atZone(zone).toInstant()

    private val workday = DailySchedule(enabled = true, startMinute = 9 * 60, endMinute = 17 * 60)
    private val night = DailySchedule(
        enabled = true,
        days = setOf(DayOfWeek.MONDAY),
        startMinute = 22 * 60,
        endMinute = 2 * 60,
    )

    @Test
    fun `daytime schedule is active only inside the window on chosen days`() {
        assertFalse(workday.isActive(at(5, 8, 59)))
        assertTrue(workday.isActive(at(5, 9, 0)))
        assertTrue(workday.isActive(at(5, 16, 59)))
        assertFalse(workday.isActive(at(5, 17, 0)))
        assertFalse(workday.isActive(at(4, 12))) // Sunday
    }

    @Test
    fun `disabled or empty schedules are never active`() {
        assertFalse(workday.copy(enabled = false).isActive(at(5, 12)))
        assertFalse(workday.copy(days = emptySet()).isActive(at(5, 12)))
        assertFalse(workday.copy(endMinute = 9 * 60).isActive(at(5, 9)))
    }

    @Test
    fun `overnight schedule belongs to the day it starts on`() {
        assertTrue(night.isActive(at(5, 23)))      // Monday night
        assertTrue(night.isActive(at(6, 1, 59)))   // early Tuesday, still Monday's window
        assertFalse(night.isActive(at(6, 2)))
        assertFalse(night.isActive(at(5, 1)))      // early Monday belongs to Sunday's window
        assertEquals(at(6, 2), night.currentWindowEnd(at(5, 23)))
        assertEquals(at(6, 2), night.currentWindowEnd(at(6, 1)))
    }

    @Test
    fun `a running session activates focus until it ends`() {
        val status = FocusRules.status(instant(5, 7), zone, sessionEndsAt = instant(5, 8), schedule = DailySchedule())
        assertTrue(status.isActive)
        assertEquals(instant(5, 8), status.endsAt)
        assertEquals(setOf(FocusSource.SESSION), status.sources)
    }

    @Test
    fun `an expired session does nothing`() {
        val status = FocusRules.status(instant(5, 9), zone, sessionEndsAt = instant(5, 8), schedule = DailySchedule())
        assertFalse(status.isActive)
        assertNull(status.endsAt)
    }

    @Test
    fun `when session and schedule overlap the later end wins`() {
        val status = FocusRules.status(instant(5, 16), zone, sessionEndsAt = instant(5, 18), schedule = workday)
        assertEquals(instant(5, 18), status.endsAt)
        assertEquals(setOf(FocusSource.SESSION, FocusSource.SCHEDULE), status.sources)

        val shorter = FocusRules.status(instant(5, 10), zone, sessionEndsAt = instant(5, 11), schedule = workday)
        assertEquals(instant(5, 17), shorter.endsAt)
    }

    @Test
    fun `strict mode locks settings only while focus is active`() {
        val active = FocusRules.status(instant(5, 10), zone, null, workday)
        val inactive = FocusRules.status(instant(5, 20), zone, null, workday)
        assertTrue(FocusRules.isLocked(strictMode = true, status = active))
        assertFalse(FocusRules.isLocked(strictMode = true, status = inactive))
        assertFalse(FocusRules.isLocked(strictMode = false, status = active))
    }
}
