package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorStatusTest {
    @Test
    fun milAndCount() {
        val s = decodeMonitorStatus(0x83, 0x00, 0x00, 0x00)
        assertTrue(s.milOn)
        assertEquals(3, s.dtcCount)
    }

    @Test
    fun continuousMonitorsSupportedAndComplete() {
        val s = decodeMonitorStatus(0x00, 0x07, 0x00, 0x00)
        val byName = s.monitors.associateBy { it.nameTr }
        listOf("Tekleme", "Yakıt sistemi", "Bileşenler").forEach {
            val m = byName.getValue(it)
            assertTrue(it, m.supported && m.complete)
        }
    }

    @Test
    fun incompleteBitMeansNotComplete() {
        val s = decodeMonitorStatus(0x00, 0x11, 0x00, 0x00)
        val m = s.monitors.first { it.nameTr == "Tekleme" }
        assertTrue(m.supported && !m.complete)
    }
}
