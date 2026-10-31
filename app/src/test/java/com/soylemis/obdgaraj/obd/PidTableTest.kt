package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PidTableTest {
    private fun v(pid: Int, vararg b: Int) = PidTable.defs.getValue(pid).decode(b)

    @Test
    fun decodesKnownPids() {
        assertEquals(1726.0, v(0x0C, 0x1A, 0xF8), 0.001)
        assertEquals(83.0, v(0x05, 0x7B), 0.001)
        assertEquals(50.0, v(0x0D, 0x32), 0.001)
        assertEquals(100.0, v(0x11, 0xFF), 0.001)
        assertEquals(14.19, v(0x42, 0x37, 0x6E), 0.001)
        assertEquals(5.0, v(0x10, 0x01, 0xF4), 0.001)
        assertEquals(0.0, v(0x06, 0x80), 0.001)
        assertEquals(60.0, v(0x5C, 0x64), 0.001)
    }

    @Test
    fun unknownPidShowsRaw() {
        assertEquals("Ham: 1A F8", PidTable.format(0x99, intArrayOf(0x1A, 0xF8)))
    }

    @Test
    fun formatsWithUnit() {
        assertEquals("1726 rpm", PidTable.format(0x0C, intArrayOf(0x1A, 0xF8)))
        assertEquals("14,19 V", PidTable.format(0x42, intArrayOf(0x37, 0x6E)))
    }

    @Test
    fun coversRequiredPids() {
        val required = listOf(
            0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x1F, 0x21, 0x22, 0x23,
            0x2C, 0x2F, 0x31, 0x33, 0x3C, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x49, 0x4C, 0x4D, 0x4E, 0x51, 0x5A, 0x5C, 0x5E,
        )
        val missing = required.filterNot { it in PidTable.defs }
        assertTrue("Eksik PID'ler: $missing", missing.isEmpty())
    }
}
