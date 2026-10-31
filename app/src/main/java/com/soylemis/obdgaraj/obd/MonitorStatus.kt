package com.soylemis.obdgaraj.obd

data class Monitor(val nameTr: String, val supported: Boolean, val complete: Boolean)

data class MonitorStatus(val milOn: Boolean, val dtcCount: Int, val monitors: List<Monitor>)

private val continuous = listOf("Tekleme", "Yakıt sistemi", "Bileşenler")
private val spark = listOf("Katalizör", "Isıtmalı katalizör", "Buharlaşma sistemi", "İkincil hava", "Klima gazı", "Oksijen sensörü", "Oksijen sensörü ısıtıcısı", "EGR sistemi")
private val compression = listOf("NMHC katalizörü", "NOx/SCR", null, "Turbo basıncı", null, "Egzoz gazı sensörü", "Partikül filtresi", "EGR/VVT sistemi")

/** Mode 01 PID 01 baytları (A, B, C, D). Tamamlanma bitlerinde 0 = tamamlandı. */
fun decodeMonitorStatus(a: Int, b: Int, c: Int, d: Int): MonitorStatus {
    val monitors = mutableListOf<Monitor>()
    continuous.forEachIndexed { i, name ->
        monitors += Monitor(name, b and (1 shl i) != 0, b and (1 shl (i + 4)) == 0)
    }
    val names = if (b and 0x08 != 0) compression else spark
    names.forEachIndexed { i, name ->
        if (name != null) monitors += Monitor(name, c and (1 shl i) != 0, d and (1 shl i) == 0)
    }
    return MonitorStatus(milOn = a and 0x80 != 0, dtcCount = a and 0x7F, monitors = monitors)
}
