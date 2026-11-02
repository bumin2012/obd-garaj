package com.soylemis.obdgaraj.obd

import java.io.IOException

class NoVehicleResponseException(reason: String) : IOException(reason)

data class InitResult(
    val elmVersion: String,
    val protocol: ObdProtocol,
    val ecus: List<String>,
    val skippedCommands: List<String>,
)

data class VehicleInfo(
    val vin: String?,
    val ecuNames: Map<String, String>,
    val calibrationIds: Map<String, List<String>>,
)

class PidReading(val ecu: String, val pid: Int, val raw: IntArray, val text: String)

/** Canlı veride "araç bu PID'i göndermiyor" ile "veri yolu anlık hata verdi" ayrımı için. */
sealed interface PidResult {
    data class Values(val readings: List<PidReading>) : PidResult
    data object NoData : PidResult
    data class Failed(val error: ElmError) : PidResult
}

/** Başlatılmış bir ELM327 üzerinden standart OBD2 servislerini okur. */
class ObdSession(private val conn: ElmConnection) {
    var protocol: ObdProtocol = ObdProtocol.AUTO
        private set
    private var firstBitmaps: Map<String, ByteArray> = emptyMap()

    suspend fun initialize(): InitResult {
        val atz = conn.execute("ATZ", timeoutMs = 5000)
        val elmVersion = atz.split('\r').lastOrNull { it.isNotBlank() }?.trim().orEmpty()
        val skipped = mutableListOf<String>()
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH1", "ATSP0", "ATAT1")) {
            if (conn.execute(cmd).trim() == "?") skipped += cmd
        }
        // ATSP0 otomatik araması K-line (ISO 9141/KWP 5 baud) protokollerinde uzun sürebilir.
        val first = conn.execute("0100", timeoutMs = 25_000)
        protocol = ObdProtocol.fromDpn(conn.execute("ATDPN")) ?: ObdProtocol.AUTO
        val parsed = ResponseParser.parse(first, protocol)
        if (parsed !is ElmResponse.Data) throw NoVehicleResponseException(describe(parsed))
        firstBitmaps = parsed.byEcu
        return InitResult(elmVersion, protocol, parsed.byEcu.keys.toList(), skipped)
    }

    suspend fun discoverSupportedPids(): Map<String, Set<Int>> {
        val result = firstBitmaps.mapValues { (_, d) -> bitmapOf(0x00, d) }.toMutableMap()
        var base = 0x20
        while (base <= 0xE0 && result.values.any { base in it }) {
            query("01%02X".format(base))?.forEach { (ecu, d) ->
                if (d.size >= 6 && d[1].toInt() and 0xFF == base) {
                    result[ecu] = result.getOrDefault(ecu, emptySet()) + bitmapOf(base, d)
                }
            }
            base += 0x20
        }
        return result
    }

    suspend fun readPid(pid: Int): List<PidReading> =
        (readPidResult(pid) as? PidResult.Values)?.readings.orEmpty()

    suspend fun readPidResult(pid: Int): PidResult =
        when (val r = ResponseParser.parse(conn.execute("01%02X".format(pid)), protocol)) {
            ElmResponse.NoData -> PidResult.NoData
            is ElmResponse.Error -> PidResult.Failed(r.kind)
            is ElmResponse.Data -> {
                val readings = r.byEcu.mapNotNull { (ecu, d) ->
                    if (d.size < 2 || d[1].toInt() and 0xFF != pid) null else reading(ecu, pid, d.drop(2))
                }
                // 7F 01 12 gibi olumsuz cevaplar da "desteklenmiyor" sayılır.
                if (readings.isEmpty()) PidResult.NoData else PidResult.Values(readings)
            }
        }

    suspend fun readMonitorStatus(): Map<String, MonitorStatus> =
        query("0101").orEmpty().mapNotNull { (ecu, d) ->
            if (d.size < 6) null
            else ecu to decodeMonitorStatus(d.u(2), d.u(3), d.u(4), d.u(5))
        }.toMap()

    suspend fun readDtcs(kind: DtcKind): List<Dtc> {
        val (cmd, service) = when (kind) {
            DtcKind.STORED -> "03" to 0x43
            DtcKind.PENDING -> "07" to 0x47
            DtcKind.PERMANENT -> "0A" to 0x4A
        }
        return query(cmd).orEmpty().flatMap { (ecu, d) ->
            val payload = if (protocol.isCan) d.drop(1) else stripLegacyRepeats(d, service)
            DtcDecoder.decode(payload.toByteArray(), protocol.isCan).map { Dtc(it, ecu, kind) }
        }
    }

    suspend fun readFreezeFrame(pids: Set<Int>): List<PidReading> {
        val out = mutableListOf<PidReading>()
        query("0202")?.forEach { (ecu, d) ->
            if (d.size >= 5) {
                val code = DtcDecoder.decode(byteArrayOf(d[3], d[4]), isCan = false).firstOrNull() ?: "Yok"
                out += PidReading(ecu, 0x02, intArrayOf(d.u(3), d.u(4)), code)
            }
        }
        for (pid in pids.filter { it % 0x20 != 0 && it != 0x01 && it != 0x02 }.sorted()) {
            query("02%02X00".format(pid))?.forEach { (ecu, d) ->
                if (d.size > 3 && d.u(1) == pid) out += reading(ecu, pid, d.drop(3))
            }
        }
        return out
    }

    /**
     * Mode 22 genişletilmiş okuma. Yalnız [ExtendedPids] beyaz listesindeki PID'ler
     * ve yalnız CAN protokolleri için; diğer her durumda boş liste döner (sessiz hayır).
     */
    suspend fun readExtendedPids(m: Manufacturer): List<PidReading> {
        if (!protocol.isCan) return emptyList()
        val out = mutableListOf<PidReading>()
        for (x in ExtendedPids.forManufacturer(m)) {
            val cmd = "22%04X".format(x.pid)
            // Araç/klon adaptör Mode 22'yi hiç desteklemeyebilir: tek PID'in hatası
            // bağlantıyı düşürmez, o PID atlanır.
            val parsed = runCatching { ResponseParser.parse(conn.execute(cmd, timeoutMs = 2000), protocol) }
                .getOrNull()
            if (parsed !is ElmResponse.Data) continue
            for ((ecu, d) in parsed.byEcu) {
                val payload = d.drop(3) // 6A + 2 bayt PID yankısı
                if (payload.size < x.byteCount) continue
                val raw = payload.take(x.byteCount).map { it.toInt() and 0xFF }.toIntArray()
                val text = with(java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols(java.util.Locale.forLanguageTag("tr")))) {
                    format(x.decode(raw)).let { if (x.unit.isEmpty()) it else "$it ${x.unit}" }
                }
                out += PidReading(ecu, x.pid, raw, text)
            }
        }
        return out
    }

    suspend fun readVehicleInfo(): VehicleInfo {
        val vin = query("0902")?.values?.firstNotNullOfOrNull { VinDecoder.decode(it.drop(2).toByteArray()) }
        val names = query("090A").orEmpty().mapNotNull { (ecu, d) ->
            ascii(d.drop(3)).takeIf { it.isNotEmpty() }?.let { ecu to it }
        }.toMap()
        val cals = query("0904").orEmpty().mapNotNull { (ecu, d) ->
            val ids = d.drop(3).chunked(16).map { ascii(it) }.filter { it.isNotEmpty() }
            if (ids.isEmpty()) null else ecu to ids
        }.toMap()
        return VehicleInfo(vin, names, cals)
    }

    suspend fun clearDtcs(confirmation: ClearConfirmation): Boolean {
        val r = ResponseParser.parse(conn.execute("04", timeoutMs = 5000, confirmation = confirmation), protocol)
        return r is ElmResponse.Data && r.byEcu.values.any { it.isNotEmpty() && it.u(0) == 0x44 }
    }

    /** Veri yoksa ya da hata varsa null; zaman aşımı yukarı fırlatılır. */
    private suspend fun query(cmd: String): Map<String, ByteArray>? =
        (ResponseParser.parse(conn.execute(cmd), protocol) as? ElmResponse.Data)?.byEcu

    private fun reading(ecu: String, pid: Int, data: List<Byte>): PidReading {
        val raw = data.map { it.toInt() and 0xFF }.toIntArray()
        return PidReading(ecu, pid, raw, PidTable.format(pid, raw))
    }

    private fun bitmapOf(base: Int, d: ByteArray) =
        if (d.size >= 6) decodeSupportBitmap(base, d.copyOfRange(2, 6)) else emptySet()

    /** CAN dışı protokollerde her satır servis baytıyla başlar (43 + 6 bayt); tekrarları ayıklar. */
    private fun stripLegacyRepeats(d: ByteArray, service: Int): List<Byte> {
        val chunks = d.toList().chunked(7)
        return if (chunks.all { it.size == 7 && it[0].toInt() and 0xFF == service }) chunks.flatMap { it.drop(1) }
        else d.drop(1)
    }

    private fun ascii(bytes: List<Byte>) =
        bytes.filter { it in 0x20..0x7E }.map { it.toInt().toChar() }.joinToString("").trim()

    private fun describe(r: ElmResponse) = when (r) {
        is ElmResponse.Error -> when (r.kind) {
            ElmError.UNABLE_TO_CONNECT -> "Araç cevap vermiyor (kontak açık mı?)"
            ElmError.BUS_INIT -> "Araç veri yolu başlatılamadı"
            else -> "Adaptör hatası: ${r.raw.trim()}"
        }
        ElmResponse.NoData -> "Araç cevap vermiyor (kontak açık mı?)"
        is ElmResponse.Data -> ""
    }

    private fun ByteArray.u(i: Int) = this[i].toInt() and 0xFF
}
