package com.soylemis.obdgaraj.ui.dtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DtcClearFlowTest {
    @Test
    fun confirmWithoutRequestReturnsNull() {
        val flow = DtcClearFlow()
        assertNull(flow.confirm())
        assertEquals(ClearStep.IDLE, flow.step.value)
    }

    @Test
    fun requestThenConfirmReturnsToken() {
        val flow = DtcClearFlow()
        flow.request()
        assertEquals(ClearStep.WARNING_SHOWN, flow.step.value)
        assertNotNull(flow.confirm())
        assertEquals(ClearStep.CONFIRMED, flow.step.value)
        assertNull("Onay bir kez kullanılır", flow.confirm())
    }

    @Test
    fun cancelResets() {
        val flow = DtcClearFlow()
        flow.request()
        flow.cancel()
        assertEquals(ClearStep.IDLE, flow.step.value)
        assertNull(flow.confirm())
    }
}
