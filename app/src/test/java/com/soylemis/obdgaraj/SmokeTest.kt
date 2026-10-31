package com.soylemis.obdgaraj

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun appIdIsCorrect() {
        assertEquals("com.soylemis.obdgaraj", BuildConfig.APPLICATION_ID)
    }
}
