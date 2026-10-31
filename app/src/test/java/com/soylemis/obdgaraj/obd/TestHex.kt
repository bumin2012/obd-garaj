package com.soylemis.obdgaraj.obd

fun hex(s: String): ByteArray =
    s.filterNot { it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
