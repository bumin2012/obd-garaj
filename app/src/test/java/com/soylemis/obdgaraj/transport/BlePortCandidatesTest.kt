package com.soylemis.obdgaraj.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlePortCandidatesTest {
    private fun c(id: Int, write: Boolean = false, notify: Boolean = false) = CharProps(uuid16(id), write, notify)

    @Test
    fun picksKnownPorts() {
        assertEquals(
            BlePort(uuid16(0xFFF0), uuid16(0xFFF2), uuid16(0xFFF1)),
            BlePortCandidates.pick(mapOf(uuid16(0xFFF0) to listOf(c(0xFFF1, notify = true), c(0xFFF2, write = true)))),
        )
        assertEquals(
            BlePort(uuid16(0xFFE0), uuid16(0xFFE1), uuid16(0xFFE1)),
            BlePortCandidates.pick(mapOf(uuid16(0xFFE0) to listOf(c(0xFFE1, write = true, notify = true)))),
        )
        assertEquals(
            BlePort(uuid16(0x18F0), uuid16(0x2AF1), uuid16(0x2AF0)),
            BlePortCandidates.pick(mapOf(uuid16(0x18F0) to listOf(c(0x2AF0, notify = true), c(0x2AF1, write = true)))),
        )
    }

    @Test
    fun fallsBackToServiceWithWriteAndNotify() {
        val services = mapOf(
            uuid16(0x1800) to listOf(c(0x2A00)),
            uuid16(0xABCD) to listOf(c(0x1111, notify = true), c(0x2222, write = true)),
        )
        assertEquals(BlePort(uuid16(0xABCD), uuid16(0x2222), uuid16(0x1111)), BlePortCandidates.pick(services))
    }

    @Test
    fun nullWhenNothingFits() {
        assertNull(BlePortCandidates.pick(mapOf(uuid16(0x1800) to listOf(c(0x2A00)))))
    }
}
