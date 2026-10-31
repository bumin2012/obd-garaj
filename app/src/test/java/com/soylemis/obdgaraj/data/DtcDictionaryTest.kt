package com.soylemis.obdgaraj.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DtcDictionaryTest {
    private val fixture = DtcDictionary("""{"P0133":"O2 sensor slow response"}""")

    @Test
    fun knownGeneric() {
        assertEquals(DtcDescription.Known("O2 sensor slow response"), fixture.describe("P0133"))
    }

    @Test
    fun manufacturerRanges() {
        listOf("P1234", "P3000", "B1001", "C2100", "U1234").forEach {
            assertEquals(it, DtcDescription.ManufacturerSpecific, fixture.describe(it))
        }
        assertEquals(DtcDescription.Unknown, fixture.describe("P3400"))
        assertEquals(DtcDescription.Unknown, fixture.describe("P0999"))
    }

    @Test
    fun bundledFileCoversCommonCodes() {
        val dict = DtcDictionary(File("src/main/assets/dtc_generic.json").readText())
        listOf("P0300", "P0420", "P0171", "U0100").forEach {
            assertTrue(it, dict.describe(it) is DtcDescription.Known)
        }
    }
}
