package com.soylemis.obdgaraj.ui.console

import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.obd.C4_SCRIPT
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.obd.ConnectionManager
import com.soylemis.obdgaraj.transport.FakeTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun forbiddenCommandIsLoggedNotSent() = runTest(dispatcher) {
        val log = ObdLog()
        val t = FakeTransport(C4_SCRIPT)
        val m = ConnectionManager(log, backgroundScope)
        m.connect(t)
        runCurrent()
        assertTrue(m.state.value is ConnState.Connected)

        val vm = ConsoleViewModel(m, log)
        vm.send("2E 01 01")
        runCurrent()
        assertTrue("2E0101" !in t.written && "2E 01 01" !in t.written)
        assertTrue(log.entries.value.last().direction == LogDirection.ERROR)

        vm.send("010C")
        runCurrent()
        assertEquals("010C", t.written.last())
    }
}
