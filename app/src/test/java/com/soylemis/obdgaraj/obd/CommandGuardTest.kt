package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertThrows
import org.junit.Test

class CommandGuardTest {
    @Test
    fun allowsAtAndReadServices() {
        listOf("ATZ", "atsp0", "ATMA", "0100", "010C", "0200", "03", "07", "0A", "0902", "01 0c")
            .forEach { CommandGuard.check(it) }
    }

    @Test
    fun rejectsClearWithoutConfirmation() {
        assertThrows(ForbiddenCommandException::class.java) { CommandGuard.check("04") }
    }

    @Test
    fun allowsClearWithConfirmation() {
        CommandGuard.check("04", ClearConfirmation.forTest())
    }

    @Test
    fun rejectsWriteServices() {
        listOf("2E F190 00", "3101FF00", "1003", "14FFFFFF", "").forEach { cmd ->
            assertThrows(ForbiddenCommandException::class.java) { CommandGuard.check(cmd) }
        }
    }
}
