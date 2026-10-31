package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportedPidsTest {
    @Test
    fun classicExample() {
        assertEquals(
            setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20),
            decodeSupportBitmap(0x00, hex("BE3FA813")),
        )
    }

    @Test
    fun offsetsByBase() {
        assertEquals(setOf(0x21), decodeSupportBitmap(0x20, hex("80000000")))
    }
}
