package com.soylemis.obdgaraj.log

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdLogTest {
    @Test
    fun exportFormat() {
        val log = ObdLog(clock = { 0L })
        log.record(LogDirection.TX, "0100")
        assertEquals("1970-01-01T00:00:00.000Z TX 0100", log.exportText().trim())
    }

    @Test
    fun dropsOldestOverCapacity() {
        val log = ObdLog(clock = { 0L }, capacity = 3)
        listOf("a", "b", "c", "d").forEach { log.record(LogDirection.INFO, it) }
        assertEquals(listOf("b", "c", "d"), log.entries.value.map { it.text })
    }
}
