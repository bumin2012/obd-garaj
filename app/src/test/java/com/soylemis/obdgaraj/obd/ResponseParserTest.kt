package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {
    private fun data(raw: String, protocol: ObdProtocol): Map<String, String> {
        val r = ResponseParser.parse(raw, protocol)
        assertTrue("Data bekleniyordu: $r", r is ElmResponse.Data)
        return (r as ElmResponse.Data).byEcu.mapValues { it.value.toHex() }
    }

    @Test
    fun parsesCanSingleFrame() {
        assertEquals(
            mapOf("7E8" to "41 00 BE 3F A8 13"),
            data("7E8064100BE3FA813\r", ObdProtocol.CAN_11_500),
        )
    }

    @Test
    fun keepsEachEcuSeparately() {
        val d = data("7E8064100BE3FA813\r7E906410098180001\r", ObdProtocol.CAN_11_500)
        assertEquals(2, d.size)
        assertEquals("41 00 BE 3F A8 13", d["7E8"])
        assertEquals("41 00 98 18 00 01", d["7E9"])
    }

    @Test
    fun assemblesIsoTpMultiFrame() {
        val raw = "7E81014490201574630\r7E82141414141414141\r7E82241414141414141\r"
        val expected = hex("490201") + "WF0AAAAAAAAAAAAAA".toByteArray(Charsets.US_ASCII)
        assertEquals(mapOf("7E8" to expected.toHex()), data(raw, ObdProtocol.CAN_11_500))
    }

    @Test
    fun parsesKwpHeadersAndDropsChecksum() {
        assertEquals(mapOf("10" to "41 0C 1A F8"), data("83F110410C1AF8B1\r", ObdProtocol.KWP_FAST))
    }

    @Test
    fun ignoresSearchingAndBusInitLines() {
        assertEquals(mapOf("7E8" to "41 00 FF"), data("SEARCHING...\r7E8034100FF00\r", ObdProtocol.CAN_11_500))
        assertEquals(mapOf("10" to "41 0D 32"), data("BUS INIT: ...OK\r83F110410D3285\r", ObdProtocol.KWP_FAST))
    }

    @Test
    fun toleratesSpacesFromClonesThatIgnoreAts0() {
        assertEquals(
            mapOf("7E8" to "41 0C 1A F8"),
            data("7E8 04 41 0C 1A F8 00 00 00\r", ObdProtocol.CAN_11_500),
        )
    }

    @Test
    fun truncatedFramesAreErrorsNotCrashes() {
        val p = ObdProtocol.CAN_11_500
        assertTrue(ResponseParser.parse("7E810\r", p) is ElmResponse.Error)
        assertTrue(ResponseParser.parse("7E8\r", p) is ElmResponse.Error)
        assertTrue(ResponseParser.parse("83F1\r", ObdProtocol.KWP_FAST) is ElmResponse.Error)
    }

    @Test
    fun mapsNoDataAndErrors() {
        val p = ObdProtocol.CAN_11_500
        assertEquals(ElmResponse.NoData, ResponseParser.parse("NO DATA\r", p))
        fun err(raw: String) = (ResponseParser.parse(raw, p) as ElmResponse.Error).kind
        assertEquals(ElmError.UNABLE_TO_CONNECT, err("UNABLE TO CONNECT"))
        assertEquals(ElmError.UNKNOWN_COMMAND, err("?"))
        assertEquals(ElmError.BUS_INIT, err("BUS INIT: ...ERROR"))
        assertEquals(ElmError.CAN_ERROR, err("CAN ERROR"))
        assertEquals(ElmError.STOPPED, err("STOPPED"))
        assertEquals(ElmError.BUFFER_FULL, err("BUFFER FULL"))
        assertEquals(ElmError.BUS_ERROR, err("BUS ERROR"))
    }
}
