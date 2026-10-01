package com.focustube.app.domain

/** Converts YouTube's ISO 8601 durations ("PT1H2M3S") to seconds. */
object Durations {

    private val ISO_8601 = Regex("""^P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)(?:\.\d+)?S)?)?$""")

    /** Returns null when [iso] isn't a duration we understand. */
    fun parseSeconds(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso == "P" || iso.endsWith("T")) return null
        val match = ISO_8601.matchEntire(iso.trim()) ?: return null
        val (weeks, days, hours, minutes, seconds) = match.destructured
        return weeks.toLongOrZero() * 7 * 86_400 +
            days.toLongOrZero() * 86_400 +
            hours.toLongOrZero() * 3_600 +
            minutes.toLongOrZero() * 60 +
            seconds.toLongOrZero()
    }

    /** 75 -> "1:15", 3_725 -> "1:02:05". */
    fun format(totalSeconds: Long): String {
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun String.toLongOrZero(): Long = toLongOrNull() ?: 0L
}
