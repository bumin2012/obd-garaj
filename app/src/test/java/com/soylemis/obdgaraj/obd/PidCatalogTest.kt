package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PidCatalogTest {
    @Test
    fun parsesAndDecodesFormula() {
        val defs = PidCatalog.parse("""[{"pid":"0x5E","name":"Tüketim","unit":"L/h","bytes":2,"formula":"(A*256+B)/20"}]""")
        assertEquals(1, defs.size)
        assertEquals("Tüketim", defs[0].nameTr)
        assertEquals(123.45, defs[0].decode(intArrayOf(0x09, 0xA5)), 0.01)
    }

    @Test
    fun infersByteCountFromFormula() {
        val defs = PidCatalog.parse("""[{"pid":"0C","name":"RPM","formula":"A*256+B"}]""")
        assertEquals(2, defs[0].byteCount)
        assertEquals(6904, defs[0].decode(intArrayOf(0x1A, 0xF8)).toInt()) // 0x1AF8 = 6904 (ham A*256+B)
    }

    @Test
    fun extraOverridesBase() {
        val merged = PidCatalog.merge(PidTable.baseDefs, """[{"pid":"0x0D","name":"Hız (klon düzeltme)","unit":"km/h","formula":"A"}]""")
        assertEquals("Hız (klon düzeltme)", merged.getValue(0x0D).nameTr)
        assertEquals(37, merged.size) // 37 taban; 0x0D üzerine yazıldı, yeni anahtar yok
    }

    @Test
    fun rejectsInjectionInFormula() {
        // Formül aritmetik dışı hiçbir şeye izin vermez.
        val bad = """[{"pid":"0D","name":"x","formula":"Runtime.exec('calc')"}]"""
        assertFalse(PidTable.installTest(bad))
    }

    @Test
    fun installKeepsBaseOnBadJson() {
        assertFalse(PidTable.install("""{bozuk"""))
        assertEquals(PidTable.baseDefs, PidTable.defs)
        assertTrue(PidTable.install(null))
    }

    private fun PidTable.installTest(s: String) = runCatching { PidCatalog.parse(s) }.isSuccess
}
