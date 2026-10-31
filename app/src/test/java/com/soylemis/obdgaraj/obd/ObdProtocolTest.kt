package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdProtocolTest {
    @Test
    fun fromDpn() {
        assertEquals(ObdProtocol.CAN_11_500, ObdProtocol.fromDpn("A6"))
        assertEquals(ObdProtocol.CAN_11_500, ObdProtocol.fromDpn("6"))
        assertEquals(ObdProtocol.KWP_FAST, ObdProtocol.fromDpn("A5"))
        assertNull(ObdProtocol.fromDpn("X"))
    }
}
