package com.soylemis.obdgaraj.obd

/** Mode 01 PID 00/20/40… cevabındaki 4 baytlık bit maskesini PID kümesine çevirir. */
fun decodeSupportBitmap(basePid: Int, data: ByteArray): Set<Int> {
    val out = mutableSetOf<Int>()
    for (i in 0 until minOf(4, data.size)) {
        val b = data[i].toInt() and 0xFF
        for (bit in 0 until 8) {
            if (b and (0x80 shr bit) != 0) out += basePid + i * 8 + bit + 1
        }
    }
    return out
}
