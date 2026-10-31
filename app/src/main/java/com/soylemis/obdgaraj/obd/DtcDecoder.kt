package com.soylemis.obdgaraj.obd

enum class DtcKind { STORED, PENDING, PERMANENT }

data class Dtc(val code: String, val ecu: String, val kind: DtcKind)

object DtcDecoder {
    private val letters = charArrayOf('P', 'C', 'B', 'U')

    /** [payload] servis baytından (43/47/4A) sonrasıdır; CAN'de ilk bayt kod sayısıdır. */
    fun decode(payload: ByteArray, isCan: Boolean): List<String> {
        val body = if (isCan && payload.isNotEmpty()) payload.copyOfRange(1, payload.size) else payload
        return (0 until body.size / 2).mapNotNull { i ->
            val a = body[i * 2].toInt() and 0xFF
            val b = body[i * 2 + 1].toInt() and 0xFF
            if (a == 0 && b == 0) null
            else "${letters[a shr 6]}${(a shr 4) and 0x3}${"%X".format(a and 0xF)}${"%02X".format(b)}"
        }
    }
}
