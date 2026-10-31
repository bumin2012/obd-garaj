package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.FakeTransport
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePollerTest {
    private suspend fun TestScope.connected(responder: (String) -> String?): Pair<ConnectionManager, FakeTransport> {
        val t = FakeTransport(responder)
        val m = ConnectionManager(ObdLog(), backgroundScope)
        m.connect(t)
        runCurrent()
        assertTrue(m.state.value is ConnState.Connected)
        return m to t
    }

    @Test
    fun pollerCyclesSelectedPids() = runTest {
        val (m, t) = connected(C4_SCRIPT)
        val p = m.newPoller()
        p.setPids(setOf(0x0C, 0x0D))
        val before = t.written.size
        p.start()
        advanceTimeBy(350)
        p.stop()
        val polled = t.written.drop(before)
        assertTrue("En az iki tur bekleniyordu: $polled", polled.count { it == "010C" } >= 2)
        assertTrue("100 ms bekleme olmalı: $polled", polled.count { it == "010C" } <= 4)
        assertEquals("1726 rpm", p.readings.value.getValue(0x0C).text)
        assertEquals("50 km/h", p.readings.value.getValue(0x0D).text)
    }

    @Test
    fun pollerDropsPidAfterNoData() = runTest {
        val (m, t) = connected { if (it == "010D") "NO DATA" else C4_SCRIPT(it) }
        val p = m.newPoller()
        p.setPids(setOf(0x0C, 0x0D))
        val before = t.written.size
        p.start()
        advanceTimeBy(1000)
        p.stop()
        assertEquals(setOf(0x0D), p.dropped.value)
        assertEquals(2, t.written.drop(before).count { it == "010D" })
    }

    @Test
    fun pollerKeepsPidOnTransientBusErrors() = runTest {
        val (m, t) = connected { if (it == "010D") "BUS BUSY" else C4_SCRIPT(it) }
        val p = m.newPoller()
        p.setPids(setOf(0x0D))
        val before = t.written.size
        p.start()
        advanceTimeBy(1000)
        p.stop()
        assertEquals(emptySet<Int>(), p.dropped.value)
        assertTrue(t.written.drop(before).count { it == "010D" } > 2)
    }

    @Test
    fun pollerSurvivesGarbledResponse() = runTest {
        val (m, _) = connected { if (it == "010C") "7E810" else C4_SCRIPT(it) }
        val p = m.newPoller()
        p.setPids(setOf(0x0C, 0x0D))
        p.start()
        advanceTimeBy(500)
        assertTrue(p.isRunning)
        assertEquals("50 km/h", p.readings.value.getValue(0x0D).text)
        p.stop()
    }

    @Test
    fun pollerStopsOnTimeout() = runTest {
        var dead = false
        val (m, _) = connected { if (dead) null else C4_SCRIPT(it) }
        val p = m.newPoller()
        p.setPids(setOf(0x0C))
        dead = true
        p.start()
        advanceTimeBy(10_000)
        assertEquals(ConnState.Lost, m.state.value)
        assertTrue(!p.isRunning)
    }
}
