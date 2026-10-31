package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.FakeTransport
import com.soylemis.obdgaraj.transport.scriptResponder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

val C4_SCRIPT: (String) -> String? = scriptResponder(File("src/main/assets/demo/c4_can.txt").readText())

class ObdSessionTest {
    private suspend fun session(responder: (String) -> String?): Pair<ObdSession, FakeTransport> {
        val t = FakeTransport(responder)
        t.open()
        return ObdSession(ElmConnection(t, ObdLog())) to t
    }

    @Test
    fun initSequence() = runTest {
        val (s, t) = session(C4_SCRIPT)
        val init = s.initialize()
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0", "ATAT1", "0100", "ATDPN"), t.written)
        assertEquals(ObdProtocol.CAN_11_500, init.protocol)
        assertEquals(listOf("7E8"), init.ecus)
        assertEquals("ELM327 v1.5", init.elmVersion)
        assertEquals(emptyList<String>(), init.skippedCommands)
    }

    @Test
    fun initSkipsUnsupportedAtCommand() = runTest {
        val (s, _) = session { if (it == "ATAT1") "?" else C4_SCRIPT(it) }
        assertEquals(listOf("ATAT1"), s.initialize().skippedCommands)
    }

    @Test
    fun initFailsOnUnableToConnect() = runTest {
        val (s, _) = session { if (it == "0100") "SEARCHING...|UNABLE TO CONNECT" else C4_SCRIPT(it) }
        val e = runCatching { s.initialize() }.exceptionOrNull()
        assertTrue("NoVehicleResponseException bekleniyordu: $e", e is NoVehicleResponseException)
    }

    @Test
    fun discoveryChainsBitmaps() = runTest {
        val (s, t) = session(C4_SCRIPT)
        s.initialize()
        val pids = s.discoverSupportedPids().getValue("7E8")
        assertTrue(t.written.containsAll(listOf("0120", "0140")))
        assertTrue("0160 sorulmamalı", "0160" !in t.written)
        assertTrue(pids.containsAll(listOf(0x0C, 0x21, 0x42, 0x46)))
    }

    @Test
    fun discoveryKeepsPerEcuSets() = runTest {
        val (s, _) = session {
            when (it) {
                "0100" -> "7E8064100BE3FA813|7E906410098180001"
                "0120" -> "7E806412080000000"
                else -> C4_SCRIPT(it)
            }
        }
        s.initialize()
        val map = s.discoverSupportedPids()
        assertEquals(setOf("7E8", "7E9"), map.keys)
        assertTrue(0x03 in map.getValue("7E8"))
        assertTrue(0x03 !in map.getValue("7E9"))
        assertTrue(0x20 in map.getValue("7E9"))
    }

    @Test
    fun readsPidValue() = runTest {
        val (s, _) = session(C4_SCRIPT)
        s.initialize()
        val r = s.readPid(0x0C).single()
        assertEquals("7E8", r.ecu)
        assertEquals("1726 rpm", r.text)
    }

    @Test
    fun readsStoredDtcs() = runTest {
        val (s, _) = session(C4_SCRIPT)
        s.initialize()
        assertEquals(listOf(Dtc("P0133", "7E8", DtcKind.STORED)), s.readDtcs(DtcKind.STORED))
        assertEquals(emptyList<Dtc>(), s.readDtcs(DtcKind.PENDING))
    }

    @Test
    fun readsDtcsOnKwpMultiLine() = runTest {
        val (s, _) = session {
            when (it) {
                "0100" -> "83F110 4100BE3FA813 B9"
                "ATDPN" -> "A5"
                "03" -> "87F110430133 0300 0000 AA|87F110430420 0000 0000 BB"
                else -> C4_SCRIPT(it)
            }
        }
        s.initialize()
        assertEquals(listOf("P0133", "P0300", "P0420"), s.readDtcs(DtcKind.STORED).map { it.code })
    }

    @Test
    fun readsVin() = runTest {
        val (s, _) = session(C4_SCRIPT)
        s.initialize()
        val info = s.readVehicleInfo()
        assertEquals("WF0AAAAAAAAAAAAAA", info.vin)
        assertEquals("ECM-EngineCtrl", info.ecuNames["7E8"])
        assertEquals(listOf("CALID1234567890A"), info.calibrationIds["7E8"])
    }

    @Test
    fun readsMonitorStatus() = runTest {
        val (s, _) = session(C4_SCRIPT)
        s.initialize()
        val m = s.readMonitorStatus().getValue("7E8")
        assertEquals(false, m.milOn)
        assertTrue(m.monitors.first { it.nameTr == "Tekleme" }.supported)
    }

    @Test
    fun readsFreezeFrame() = runTest {
        val (s, _) = session(C4_SCRIPT)
        s.initialize()
        val ff = s.readFreezeFrame(setOf(0x05, 0x0C))
        assertEquals("P0133", ff.first { it.pid == 0x02 }.text)
        assertEquals("83 °C", ff.first { it.pid == 0x05 }.text)
        assertEquals("1726 rpm", ff.first { it.pid == 0x0C }.text)
    }

    @Test
    fun clearSendsMode04() = runTest {
        val (s, t) = session(C4_SCRIPT)
        s.initialize()
        assertTrue(s.clearDtcs(ClearConfirmation.forTest()))
        assertEquals("04", t.written.last())
    }
}
