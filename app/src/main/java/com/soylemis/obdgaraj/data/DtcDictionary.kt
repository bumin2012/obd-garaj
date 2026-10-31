package com.soylemis.obdgaraj.data

import android.content.Context
import org.json.JSONObject

sealed interface DtcDescription {
    data class Known(val text: String) : DtcDescription
    data object ManufacturerSpecific : DtcDescription
    data object Unknown : DtcDescription
}

/** Genel (SAE J2012) arıza kodu açıklamaları. Kaynak: NOTICE.md. */
class DtcDictionary(json: String) {
    private val map: Map<String, String> = JSONObject(json).let { o ->
        o.keys().asSequence().associateWith { o.getString(it) }
    }

    fun describe(code: String): DtcDescription {
        val c = code.uppercase()
        map[c]?.let { return DtcDescription.Known(it) }
        return if (isManufacturerSpecific(c)) DtcDescription.ManufacturerSpecific else DtcDescription.Unknown
    }

    companion object {
        fun fromAssets(context: Context): DtcDictionary =
            DtcDictionary(context.assets.open("dtc_generic.json").bufferedReader().use { it.readText() })

        /** P1xxx, P3000–P33FF ile B/C/U 1xxx–2xxx üreticiye ayrılmıştır. */
        fun isManufacturerSpecific(code: String): Boolean {
            if (code.length != 5) return false
            val d1 = code[1]
            return when (code[0]) {
                'P' -> d1 == '1' || (d1 == '3' && code.substring(1).toIntOrNull(16)?.let { it < 0x3400 } == true)
                'B', 'C', 'U' -> d1 == '1' || d1 == '2'
                else -> false
            }
        }
    }
}
