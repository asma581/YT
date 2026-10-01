package com.focustube.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationsTest {

    @Test
    fun `parses youtube durations`() {
        assertEquals(3L, Durations.parseSeconds("PT3S"))
        assertEquals(15 * 60L + 33, Durations.parseSeconds("PT15M33S"))
        assertEquals(3_600L + 2 * 60 + 5, Durations.parseSeconds("PT1H2M5S"))
        assertEquals(86_400L + 3_600, Durations.parseSeconds("P1DT1H"))
        assertEquals(0L, Durations.parseSeconds("P0D")) // live streams
    }

    @Test
    fun `rejects things that are not durations`() {
        assertNull(Durations.parseSeconds(null))
        assertNull(Durations.parseSeconds(""))
        assertNull(Durations.parseSeconds("P"))
        assertNull(Durations.parseSeconds("PT"))
        assertNull(Durations.parseSeconds("15:33"))
    }

    @Test
    fun `formats durations like youtube does`() {
        assertEquals("0:09", Durations.format(9))
        assertEquals("1:15", Durations.format(75))
        assertEquals("1:02:05", Durations.format(3_725))
    }
}
