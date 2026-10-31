package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.FakeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.soylemis.obdgaraj.transport.ObdTransport
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
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
    fun openTimeoutFailsAndClosesTransport() = runTest {
        val t = ScriptedOpenTransport { withTimeout(10) { delay(1000) } }
        val m = ConnectionManager(ObdLog(), backgroundScope)
        m.connect(t)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertTrue("Failed bekleniyordu: ${m.state.value}", m.state.value is ConnState.Failed)
        assertTrue(t.closed)
    }

    @Test
    fun staleConnectDoesNotOverrideNewConnection() = runTest {
        // İptal edilemeyen (Bluetooth soketi gibi) ve sonunda başarısız olan bir bağlantı denemesi.
        val slow = ScriptedOpenTransport { withContext(NonCancellable) { delay(5000) }; throw IOException("timeout") }
        val m = ConnectionManager(ObdLog(), backgroundScope)
        m.connect(slow)
        runCurrent()
        m.connect(FakeTransport(C4_SCRIPT))
        runCurrent()
        assertTrue(m.state.value is ConnState.Connected)
        advanceTimeBy(6000)
        runCurrent()
        assertTrue("Yeni bağlantı korunmalı: ${m.state.value}", m.state.value is ConnState.Connected)
        assertTrue(slow.closed)
    }

    @Test
    fun lateSuccessOfCancelledConnectClosesTransport() = runTest {
        val slow = ScriptedOpenTransport { withContext(NonCancellable) { delay(5000) } }
        val m = ConnectionManager(ObdLog(), backgroundScope)
        m.connect(slow)
        runCurrent()
        m.disconnect()
        advanceTimeBy(6000)
        runCurrent()
        assertEquals(ConnState.Idle, m.state.value)
        assertTrue(slow.closed)
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

private class ScriptedOpenTransport(private val onOpen: suspend () -> Unit) : ObdTransport {
    override val label = "test"
    override val incoming: Flow<ByteArray> = MutableSharedFlow()
    var closed = false
    override suspend fun open() = onOpen()
    override suspend fun write(bytes: ByteArray) = Unit
    override suspend fun close() {
        closed = true
    }
}

private fun CoroutineScope.launchCollect(m: ConnectionManager, into: MutableList<ConnState>) {
    launch(Dispatchers.Unconfined) { m.state.collect { into += it } }
}
