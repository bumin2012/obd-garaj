package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.FakeTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmConnectionTest {
    private val log = ObdLog(clock = { 0L })

    @Test
    fun sendsCommandWithCrAndReturnsUntilPrompt() = runTest {
        val t = FakeTransport({ if (it == "ATI") "ELM327 v1.5" else null })
        val conn = ElmConnection(t, log).also { t.open() }
        assertEquals("ELM327 v1.5", conn.execute("ATI"))
        assertEquals(listOf("ATI"), t.written)
        val entries = log.entries.value.map { it.direction to it.text }
        assertTrue(entries.contains(LogDirection.TX to "ATI"))
        assertTrue(entries.contains(LogDirection.RX to "ELM327 v1.5"))
    }

    @Test
    fun serializesConcurrentCommands() = runTest {
        val t = FakeTransport({ "R-$it" }, delayMs = 50)
        val conn = ElmConnection(t, log).also { t.open() }
        val a = async { conn.execute("0100") }
        val b = async { conn.execute("010C") }
        assertEquals("R-0100", a.await())
        assertEquals("R-010C", b.await())
    }

    @Test
    fun timesOutWithoutPrompt() = runTest {
        val t = FakeTransport({ null })
        val conn = ElmConnection(t, log).also { t.open() }
        val e = runCatching { conn.execute("0100", timeoutMs = 1000) }.exceptionOrNull()
        assertTrue("ElmTimeoutException bekleniyordu: $e", e is ElmTimeoutException)
        assertTrue(log.entries.value.any { it.direction == LogDirection.ERROR })
    }

    @Test
    fun rejectsForbiddenBeforeWriting() = runTest {
        val t = FakeTransport({ "7F2E11" })
        val conn = ElmConnection(t, log).also { t.open() }
        val e = runCatching { conn.execute("2E0101") }.exceptionOrNull()
        assertTrue(e is ForbiddenCommandException)
        assertEquals(emptyList<String>(), t.written)
    }

    @Test
    fun handlesPromptSplitAcrossChunks() = runTest {
        val t = FakeTransport({ "41 0C 1A F8" }, chunkSize = 3)
        val conn = ElmConnection(t, log).also { t.open() }
        assertEquals("41 0C 1A F8", conn.execute("010C"))
    }
}
