package com.soylemis.obdgaraj.obd

/** ELM327 protokol numaraları (ATDPN / ATSPx). */
enum class ObdProtocol(val code: Char, val isCan: Boolean, val headerHexLength: Int) {
    AUTO('0', false, 6),
    J1850_PWM('1', false, 6),
    J1850_VPW('2', false, 6),
    ISO_9141('3', false, 6),
    KWP_5BAUD('4', false, 6),
    KWP_FAST('5', false, 6),
    CAN_11_500('6', true, 3),
    CAN_29_500('7', true, 8),
    CAN_11_250('8', true, 3),
    CAN_29_250('9', true, 8),
    J1939('A', true, 8),
    USER1('B', true, 3),
    USER2('C', true, 3);

    companion object {
        /** "A6" (otomatik seçilmiş 6) ya da "6" biçimindeki ATDPN cevabını çözer. */
        fun fromDpn(raw: String): ObdProtocol? {
            val s = raw.trim().uppercase()
            val code = when {
                s.length == 2 && s[0] == 'A' -> s[1]
                s.length == 1 -> s[0]
                else -> return null
            }
            return entries.firstOrNull { it.code == code }
        }
    }
}
