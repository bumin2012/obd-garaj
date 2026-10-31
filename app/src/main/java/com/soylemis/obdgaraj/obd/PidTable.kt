package com.soylemis.obdgaraj.obd

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class PidDef(
    val pid: Int,
    val nameTr: String,
    val unit: String,
    val byteCount: Int,
    val decode: (IntArray) -> Double,
)

/** SAE J1979 Mode 01 PID tanımları. */
object PidTable {
    private fun ab(d: IntArray) = d[0] * 256.0 + d[1]
    private fun pct(d: IntArray) = d[0] * 100.0 / 255
    private fun temp(d: IntArray) = d[0] - 40.0
    private fun trim(d: IntArray) = d[0] / 1.28 - 100

    val defs: Map<Int, PidDef> = listOf(
        PidDef(0x04, "Hesaplanan motor yükü", "%", 1, ::pct),
        PidDef(0x05, "Soğutma suyu sıcaklığı", "°C", 1, ::temp),
        PidDef(0x06, "Kısa dönem yakıt düzeltmesi (Bank 1)", "%", 1, ::trim),
        PidDef(0x07, "Uzun dönem yakıt düzeltmesi (Bank 1)", "%", 1, ::trim),
        PidDef(0x08, "Kısa dönem yakıt düzeltmesi (Bank 2)", "%", 1, ::trim),
        PidDef(0x09, "Uzun dönem yakıt düzeltmesi (Bank 2)", "%", 1, ::trim),
        PidDef(0x0A, "Yakıt basıncı", "kPa", 1) { it[0] * 3.0 },
        PidDef(0x0B, "Emme manifoldu basıncı", "kPa", 1) { it[0].toDouble() },
        PidDef(0x0C, "Motor devri", "rpm", 2) { ab(it) / 4 },
        PidDef(0x0D, "Araç hızı", "km/h", 1) { it[0].toDouble() },
        PidDef(0x0E, "Ateşleme avansı", "°", 1) { it[0] / 2.0 - 64 },
        PidDef(0x0F, "Emme havası sıcaklığı", "°C", 1, ::temp),
        PidDef(0x10, "Hava akış (MAF)", "g/s", 2) { ab(it) / 100 },
        PidDef(0x11, "Gaz kelebeği konumu", "%", 1, ::pct),
        PidDef(0x1F, "Motor çalışma süresi", "s", 2, ::ab),
        PidDef(0x21, "Arıza lambası yanarken gidilen yol", "km", 2, ::ab),
        PidDef(0x22, "Yakıt rampası basıncı (manifolda göre)", "kPa", 2) { ab(it) * 0.079 },
        PidDef(0x23, "Yakıt rampası basıncı", "kPa", 2) { ab(it) * 10 },
        PidDef(0x2C, "Komut edilen EGR", "%", 1, ::pct),
        PidDef(0x2F, "Yakıt seviyesi", "%", 1, ::pct),
        PidDef(0x31, "Kodlar silindikten sonra gidilen yol", "km", 2, ::ab),
        PidDef(0x33, "Barometrik basınç", "kPa", 1) { it[0].toDouble() },
        PidDef(0x3C, "Katalizör sıcaklığı (B1S1)", "°C", 2) { ab(it) / 10 - 40 },
        PidDef(0x42, "ECU besleme voltajı", "V", 2) { ab(it) / 1000 },
        PidDef(0x43, "Mutlak yük", "%", 2) { ab(it) * 100 / 255 },
        PidDef(0x44, "Komut edilen lambda", "λ", 2) { ab(it) * 2 / 65536 },
        PidDef(0x45, "Bağıl gaz kelebeği konumu", "%", 1, ::pct),
        PidDef(0x46, "Ortam sıcaklığı", "°C", 1, ::temp),
        PidDef(0x47, "Mutlak gaz kelebeği konumu B", "%", 1, ::pct),
        PidDef(0x49, "Gaz pedalı konumu D", "%", 1, ::pct),
        PidDef(0x4C, "Komut edilen gaz kelebeği", "%", 1, ::pct),
        PidDef(0x4D, "Arıza lambası yanarken geçen süre", "dk", 2, ::ab),
        PidDef(0x4E, "Kodlar silindikten sonra geçen süre", "dk", 2, ::ab),
        PidDef(0x51, "Yakıt tipi", "", 1) { it[0].toDouble() },
        PidDef(0x5A, "Bağıl gaz pedalı konumu", "%", 1, ::pct),
        PidDef(0x5C, "Motor yağı sıcaklığı", "°C", 1, ::temp),
        PidDef(0x5E, "Yakıt tüketimi", "L/h", 2) { ab(it) / 20 },
    ).associateBy { it.pid }

    fun name(pid: Int): String = defs[pid]?.nameTr ?: "PID %02X".format(pid)

    fun format(pid: Int, data: IntArray): String {
        val def = defs[pid]
        if (def == null || data.size < def.byteCount) return "Ham: " + data.joinToString(" ") { "%02X".format(it) }
        val text = NUMBER.get()!!.format(def.decode(data))
        return if (def.unit.isEmpty()) text else "$text ${def.unit}"
    }

    private val NUMBER = ThreadLocal.withInitial {
        DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("tr")))
    }
}
