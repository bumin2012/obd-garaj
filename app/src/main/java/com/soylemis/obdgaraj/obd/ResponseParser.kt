package com.soylemis.obdgaraj.obd

sealed interface ElmResponse {
    /** ECU adresi → servis cevabı (ör. 41 0C 1A F8); PCI/checksum temizlenmiş. */
    data class Data(val byEcu: Map<String, ByteArray>) : ElmResponse
    data object NoData : ElmResponse
    data class Error(val kind: ElmError, val raw: String) : ElmResponse
}

enum class ElmError { UNABLE_TO_CONNECT, BUS_INIT, CAN_ERROR, BUS_ERROR, STOPPED, UNKNOWN_COMMAND, BUFFER_FULL, OTHER }

/** ATH1 ATS0 ATE0 ATL0 modunda ELM327 çıktısını ECU bazında bayt dizilerine çevirir. */
object ResponseParser {
    private val hexLine = Regex("^[0-9A-F]+$")

    private val errors = listOf(
        "UNABLE TO CONNECT" to ElmError.UNABLE_TO_CONNECT,
        "BUS INIT" to ElmError.BUS_INIT,
        "CAN ERROR" to ElmError.CAN_ERROR,
        "BUS ERROR" to ElmError.BUS_ERROR,
        "BUS BUSY" to ElmError.BUS_ERROR,
        "STOPPED" to ElmError.STOPPED,
        "BUFFER FULL" to ElmError.BUFFER_FULL,
        "DATA ERROR" to ElmError.OTHER,
        "FB ERROR" to ElmError.OTHER,
        "LV RESET" to ElmError.OTHER,
        "ACT ALERT" to ElmError.OTHER,
        "ERR" to ElmError.OTHER,
    )

    fun parse(raw: String, protocol: ObdProtocol): ElmResponse {
        val lines = raw.split('\r', '\n').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val dataLines = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("SEARCHING") || (line.startsWith("BUS INIT") && line.endsWith("OK"))) continue
            if (line == "?") return ElmResponse.Error(ElmError.UNKNOWN_COMMAND, raw)
            if (line == "NO DATA") return ElmResponse.NoData
            errors.firstOrNull { line.startsWith(it.first) }?.let { return ElmResponse.Error(it.second, raw) }
            val compact = line.replace(" ", "")
            if (hexLine.matches(compact)) dataLines += compact
        }
        if (dataLines.isEmpty()) return ElmResponse.Error(ElmError.OTHER, raw)
        val byEcu = if (protocol.isCan) parseCan(dataLines, protocol.headerHexLength) else parseLegacy(dataLines)
        return if (byEcu.isEmpty()) ElmResponse.Error(ElmError.OTHER, raw) else ElmResponse.Data(byEcu)
    }

    private fun parseCan(lines: List<String>, headerLen: Int): Map<String, ByteArray> {
        val frames = LinkedHashMap<String, MutableList<ByteArray>>()
        for (line in lines) {
            if (line.length <= headerLen || (line.length - headerLen) % 2 != 0) continue
            val header = line.substring(0, headerLen)
            frames.getOrPut(header) { mutableListOf() } += bytes(line.substring(headerLen))
        }
        return frames.mapNotNull { (ecu, list) -> assembleIsoTp(list)?.let { ecu to it } }.toMap()
    }

    /** ISO 15765-2: tek çerçeve (0N), ilk çerçeve (1N NN), ardışık çerçeve (2N). */
    private fun assembleIsoTp(frames: List<ByteArray>): ByteArray? {
        val first = frames.firstOrNull() ?: return null
        val pci = first[0].toInt() and 0xFF
        return when (pci shr 4) {
            0x0 -> {
                val len = pci and 0x0F
                if (len == 0 || first.size < 1 + len) null else first.copyOfRange(1, 1 + len)
            }
            0x1 -> {
                val len = ((pci and 0x0F) shl 8) or (first[1].toInt() and 0xFF)
                val out = ArrayList<Byte>(len)
                first.drop(2).forEach { out += it }
                frames.drop(1).filter { (it[0].toInt() and 0xF0) == 0x20 }.forEach { cf -> cf.drop(1).forEach { out += it } }
                if (out.size < len) null else out.take(len).toByteArray()
            }
            else -> null
        }
    }

    /** J1850/ISO 9141/KWP: 3 bayt başlık (kaynak adres 3. bayt) + veri + 1 bayt checksum. */
    private fun parseLegacy(lines: List<String>): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for (line in lines) {
            if (line.length % 2 != 0) continue
            val b = bytes(line)
            if (b.size < 5) continue
            val ecu = "%02X".format(b[2])
            out[ecu] = (out[ecu] ?: ByteArray(0)) + b.copyOfRange(3, b.size - 1)
        }
        return out
    }

    private fun bytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
