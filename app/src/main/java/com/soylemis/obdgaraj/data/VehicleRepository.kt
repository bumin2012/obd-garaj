package com.soylemis.obdgaraj.data

import com.soylemis.obdgaraj.obd.Dtc
import com.soylemis.obdgaraj.obd.ObdProtocol
import kotlinx.coroutines.flow.Flow

class VehicleRepository(
    private val dao: VehicleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<VehicleEntity>> = dao.observeAll()

    fun observeDtcs(vehicleId: Long): Flow<List<DtcHistoryEntity>> = dao.observeDtcs(vehicleId)

    suspend fun get(id: Long): VehicleEntity? = dao.get(id)

    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        dao.insertAll(
            listOf(
                VehicleEntity(name = "Citroën C4 (2022)", kind = VehicleKind.CAR),
                VehicleEntity(name = "Yamaha MT-125 (2025)", kind = VehicleKind.MOTORCYCLE),
                VehicleEntity(name = "Peugeot XP400 (2024)", kind = VehicleKind.MOTORCYCLE),
            ),
        )
    }

    suspend fun matchByVin(vin: String): VehicleEntity? = dao.findByVin(vin)

    suspend fun saveConnection(vehicleId: Long, vin: String?, protocol: ObdProtocol, supported: Map<String, Set<Int>>) {
        val v = dao.get(vehicleId) ?: return
        dao.update(
            v.copy(
                vin = vin ?: v.vin,
                protocol = protocol.name,
                supportedPids = encodeSupported(supported),
                lastConnectedAt = clock(),
            ),
        )
    }

    suspend fun recordDtcs(vehicleId: Long, dtcs: List<Dtc>) {
        val now = clock()
        dao.insertDtcs(dtcs.map { DtcHistoryEntity(vehicleId = vehicleId, code = it.code, ecu = it.ecu, kind = it.kind.name, seenAt = now) })
        dao.get(vehicleId)?.let { dao.update(it.copy(lastDtcCount = dtcs.size)) }
    }

    companion object {
        fun encodeSupported(map: Map<String, Set<Int>>): String =
            map.entries.sortedBy { it.key }.joinToString(";") { (ecu, pids) ->
                ecu + ":" + pids.sorted().joinToString(",") { "%02X".format(it) }
            }

        fun decodeSupported(text: String): Map<String, Set<Int>> =
            text.split(';').filter { ':' in it }.associate { part ->
                val (ecu, pids) = part.split(':', limit = 2)
                ecu to pids.split(',').mapNotNull { it.toIntOrNull(16) }.toSet()
            }
    }
}
