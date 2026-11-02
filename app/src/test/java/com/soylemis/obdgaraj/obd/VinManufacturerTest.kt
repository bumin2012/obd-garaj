package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinManufacturerTest {
    @Test
    fun psaWmis() {
        assertEquals(Manufacturer.PSA, VinDecoder.manufacturer("VF7RUXT0N8XXXXXXX"))
        assertEquals(Manufacturer.PSA, VinDecoder.manufacturer("VF3P2HYNX5XXXXXXX"))
    }

    @Test
    fun renaultNotSwallowedByPsa() {
        assertEquals(Manufacturer.RENAULT, VinDecoder.manufacturer("VF1J0700000000000"))
        assertEquals(Manufacturer.RENAULT, VinDecoder.manufacturer("VF2X9500000000000"))
        assertEquals(Manufacturer.RENAULT, VinDecoder.manufacturer("VS1A0000000000000"))
    }

    @Test
    fun yamahaMotorcycle() {
        assertEquals(Manufacturer.YAMAHA, VinDecoder.manufacturer("LYMXG03MXR0000000"))
        assertEquals(Manufacturer.YAMAHA, VinDecoder.manufacturer("JY2SE23E00A000000"))
        assertTrue(VinDecoder.isMotorcycle(Manufacturer.YAMAHA))
    }

    @Test
    fun toyotaTurkeyAndUnknown() {
        assertEquals(Manufacturer.TOYOTA, VinDecoder.manufacturer("MR0XXXXXXXXXXXXXX"))
        assertEquals(Manufacturer.UNKNOWN, VinDecoder.manufacturer(null))
        assertEquals(Manufacturer.UNKNOWN, VinDecoder.manufacturer("ZZZ123"))
    }
}

class CommandGuardExtendedTest {
    @Test
    fun allowsMode22Read() {
        CommandGuard.check("22010C") // okuma istemi — engellenmemeli
        CommandGuard.check("22F40C")
    }

    @Test
    fun rejectsShort22AndOtherUds() {
        kotlin.runCatching { CommandGuard.check("22") }.onSuccess { throw AssertionError("22 tek başına geçmemeli") }
        kotlin.runCatching { CommandGuard.check("2EF190") }.onSuccess { throw AssertionError("0x2E yazma servisi geçmemeli") }
        kotlin.runCatching { CommandGuard.check("2E00") }.onSuccess { throw AssertionError("0x2E geçmemeli") }
    }
}
