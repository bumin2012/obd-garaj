package com.soylemis.obdgaraj.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sefer örneklerini Torque uyumlu CSV'ye çevirir (ObdGraphs/Torque eklenti kalıbı). */
object TripCsv {
    private val TS = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /**
     * Başlık: `Zaman` + pid başına bir sütun (isim/birim [nameOf] ile gelir, yoksa "PID 0C").
     * Ayraç noktalı virgül — Türkçe Excel ondalık virgül kullandığı için CSV ayıracı ';' olmalı.
     */
    fun render(
        trip: TripEntity,
        samples: List<SampleEntity>,
        pidOf: (Int) -> String,
        format: (Int, Double) -> String = { _, v -> v.toString() },
    ): String = buildString {
        append("# Araç seferi #").append(trip.id)
        trip.vin?.let { append(" · VIN ").append(it) }
        trip.protocol?.let { append(" · ").append(it) }
        append('\n')
        val pids = samples.map { it.pid }.distinct().sorted()
        append("Zaman").append(';').append(pids.joinToString(";") { pidOf(it) }).append('\n')
        // ts → (pid → değer); aynı ts'de birden çok PID gelen satırları birleştir.
        val byTs = samples.groupBy { it.ts }
        for (ts in byTs.keys.sorted()) {
            append(TS.format(Date(ts)))
            val row = byTs.getValue(ts).associate { it.pid to it.value }
            for (pid in pids) {
                append(';')
                row[pid]?.let { append(format(pid, it)) }
            }
            append('\n')
        }
    }
}
