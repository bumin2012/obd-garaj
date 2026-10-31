package com.soylemis.obdgaraj.transport

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GattStepTest {
    @Test
    fun mismatchedCallbackIsIgnored() = runTest {
        val steps = GattSteps()
        val pending = async { steps.run(GattOp.DISCOVER, timeoutMs = 1000) { true } }
        runCurrent()
        assertFalse("MTU geri çağrısı keşfi bitirmemeli", steps.complete(GattOp.MTU, 0))
        assertTrue(steps.complete(GattOp.DISCOVER, 0))
        assertEquals(0, pending.await())
    }

    @Test
    fun timeoutIsIOException() = runTest {
        val steps = GattSteps()
        val e = runCatching { steps.run(GattOp.DESCRIPTOR, timeoutMs = 1000) { true } }.exceptionOrNull()
        assertTrue("IOException bekleniyordu: $e", e is java.io.IOException)
    }
}
