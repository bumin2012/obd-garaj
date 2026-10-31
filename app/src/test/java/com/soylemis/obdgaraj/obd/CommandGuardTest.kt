package com.soylemis.obdgaraj.obd

import org.junit.Assert.assertThrows
import org.junit.Test

class CommandGuardTest {
    @Test
    fun allowsAtAndReadServices() {
        listOf(
            "ATZ", "atsp0", "ATSP6", "ATE0", "ATH1", "ATDPN", "ATRV", "ATI", "AT@1", "ATST64", "ATAT1",
            "0100", "010C", "0104", "0200", "03", "07", "0A", "0902", "01 0c", "01 0C 0D",
        ).forEach { CommandGuard.check(it) }
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

    /** Çerçevelemeyi, adreslemeyi ya da adaptör belleğini değiştiren AT komutları izin listesini aşmaya yarar. */
    @Test
    fun rejectsFramingAndAddressingAtCommands() {
        listOf("ATCAF0", "ATSH7E0", "AT SH 7E0", "ATMA", "ATCRA7E8", "ATFCSH7E0", "ATPP0CSV00", "ATBRD23", "ATD", "ATWS", "ATMR10", "ATCEA")
            .forEach { cmd -> assertThrows(cmd, ForbiddenCommandException::class.java) { CommandGuard.check(cmd) } }
    }

    @Test
    fun rejectsControlCharactersAndJunk() {
        listOf("01\r04", "ATZ\r04", "01\n04", "03\t", "01;04", "010G")
            .forEach { cmd -> assertThrows(cmd, ForbiddenCommandException::class.java) { CommandGuard.check(cmd) } }
    }
}
