package com.soylemis.obdgaraj.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class TripCsvTest {
    @Test
    fun rendersSemicolonSeparatedPivot() {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse("2026-11-02 10:00:00").time
        val trip = TripEntity(id = 7, vehicleId = 1, startedAt = ts, vin = "VF7X", protocol = "CAN_11_500")
        val samples = listOf(
            SampleEntity(tripId = 7, pid = 0x0C, value = 1726.0, ts = ts),
            SampleEntity(tripId = 7, pid = 0x0D, value = 50.0, ts = ts),
            SampleEntity(tripId = 7, pid = 0x0C, value = 2100.0, ts = ts + 1000),
        )
        val csv = TripCsv.render(trip, samples, pidOf = { if (it == 0x0C) "RPM" else "Hız [km/h]" })
        val lines = csv.lines()
        assertEquals("# Araç seferi #7 · VIN VF7X · CAN_11_500", lines[0])
        assertEquals("Zaman;RPM;Hız [km/h]", lines[1])
        assertEquals("2026-11-02 10:00:00;1726.0;50.0", lines[2])
        assertEquals("2026-11-02 10:00:01;2100.0;", lines[3])
    }
}
