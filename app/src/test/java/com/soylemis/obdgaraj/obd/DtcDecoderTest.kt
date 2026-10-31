package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class DtcDecoderTest {
    @Test
    fun canHasCountByte() {
        assertEquals(listOf("P0133", "U0100"), DtcDecoder.decode(hex("020133C100"), isCan = true))
    }

    @Test
    fun legacySkipsPadding() {
        assertEquals(listOf("P0133"), DtcDecoder.decode(hex("013300000000"), isCan = false))
    }

    @Test
    fun decodesAllLetters() {
        assertEquals(listOf("C0300", "B0123", "U1234"), DtcDecoder.decode(hex("4300812 3D234".replace(" ", "")), isCan = false))
    }
}
