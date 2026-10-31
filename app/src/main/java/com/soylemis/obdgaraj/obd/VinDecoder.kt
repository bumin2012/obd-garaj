package com.soylemis.obdgaraj.obd

object VinDecoder {
    /** VIN alfabesi (I, O, Q yok); bu sayede sayaç, dolgu ve tekrar eden 49 02 baytları elenir. */
    private val vinChars = Regex("[A-HJ-NPR-Z0-9]")

    /** [payload] Mode 09 PID 02 cevabının "49 02" sonrasıdır. */
    fun decode(payload: ByteArray): String? {
        val chars = payload.map { (it.toInt() and 0xFF).toChar() }.filter { vinChars.matches(it.toString()) }
        return if (chars.size < 17) null else chars.takeLast(17).joinToString("")
    }
}
