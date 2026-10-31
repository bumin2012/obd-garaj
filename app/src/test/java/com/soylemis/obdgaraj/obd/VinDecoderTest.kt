package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VinDecoderTest {
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test
    fun canPayload() {
        assertEquals("WF0AAAAAAAAAAAAAA", VinDecoder.decode(hex("01") + ascii("WF0AAAAAAAAAAAAAA")))
    }

    @Test
    fun legacyLinesWithPadding() {
        // 49 02 01 00 00 00 'W' / 49 02 02 'F0AA' / ... (ilk 49 02 çıkarılmış)
        val payload = hex("01000000") + ascii("W") +
            hex("490202") + ascii("F0AA") + hex("490203") + ascii("AAAA") +
            hex("490204") + ascii("AAAA") + hex("490205") + ascii("AAAA")
        assertEquals("WF0AAAAAAAAAAAAAA", VinDecoder.decode(payload))
    }

    @Test
    fun tooShortIsNull() {
        assertNull(VinDecoder.decode(hex("01") + ascii("WF0AAAAA")))
    }
}
