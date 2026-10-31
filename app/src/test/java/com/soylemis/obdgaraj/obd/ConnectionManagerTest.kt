package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.FakeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionManagerTest {
    @Test
    fun connectRunsInitDiscoveryAndVin() = runTest {
        val log = ObdLog()
        val m = ConnectionManager(log, backgroundScope)
        val seen = mutableListOf<ConnState>()
        backgroundScope.launchCollect(m, seen)
        m.connect(FakeTransport(C4_SCRIPT))
        runCurrent()
        val state = m.state.value
        assertTrue("Connected bekleniyordu: $state", state is ConnState.Connected)
        state as ConnState.Connected
        assertEquals("WF0AAAAAAAAAAAAAA", state.vin)
        assertTrue(0x42 in state.supported.getValue("7E8"))
        assertTrue(seen.any { it is ConnState.Connecting })
    }

    @Test
    fun connectFailsGracefully() = runTest {
        val log = ObdLog()
        val m = ConnectionManager(log, backgroundScope)
        m.connect(FakeTransport({ if (it == "0100") "UNABLE TO CONNECT" else C4_SCRIPT(it) }))
        runCurrent()
        val state = m.state.value
        assertTrue("Failed bekleniyordu: $state", state is ConnState.Failed)
        assertTrue((state as ConnState.Failed).reason.startsWith("Araç cevap vermiyor"))
        assertTrue(log.entries.value.any { it.direction == LogDirection.ERROR })
    }

    @Test
    fun disconnectReturnsToIdle() = runTest {
        val m = ConnectionManager(ObdLog(), backgroundScope)
        m.connect(FakeTransport(C4_SCRIPT))
        runCurrent()
        m.disconnect()
        runCurrent()
        assertEquals(ConnState.Idle, m.state.value)
    }
}

private fun CoroutineScope.launchCollect(m: ConnectionManager, into: MutableList<ConnState>) {
    launch(Dispatchers.Unconfined) { m.state.collect { into += it } }
}
