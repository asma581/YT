package com.focustube.app.ui.components

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "3:45 PM" or "15:45", following the phone's locale. */
fun formatClockTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(instant.atZone(zone))

fun formatMinuteOfDay(minuteOfDay: Int): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))

/** "1:04:09" or "12:30" left until [end]. */
fun formatRemaining(now: Instant, end: Instant): String {
    val seconds = Duration.between(now, end).seconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
