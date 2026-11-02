package com.soylemis.obdgaraj.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/** Sefer kaydı: bağlantı oturumu boyunca canlı veri örneklerini saklar (ObdGraphs kalıbı). */
class TripRepository(private val dao: TripDao, private val clock: () -> Long = System::currentTimeMillis) {

    fun observeTrips(vehicleId: Long): Flow<List<TripEntity>> = dao.observeTrips(vehicleId)

    suspend fun startTrip(vehicleId: Long, protocol: String?, vin: String?, pids: Set<Int>): Long =
        dao.insertTrip(
            TripEntity(
                vehicleId = vehicleId,
                startedAt = clock(),
                protocol = protocol,
                vin = vin,
                pids = pids.sorted().joinToString(",") { "%02X".format(it) },
            ),
        )

    suspend fun appendSamples(tripId: Long, samples: List<Pair<Int, Double>>) {
        if (samples.isEmpty()) return
        val now = clock()
        dao.insertSamples(samples.map { (pid, v) -> SampleEntity(tripId = tripId, pid = pid, value = v, ts = now) })
    }

    suspend fun endTrip(tripId: Long) {
        dao.getTrip(tripId)?.let { if (it.endedAt == null) dao.updateTrip(it.copy(endedAt = clock())) }
    }

    /** Son (en yeni) sefer; hiç yoksa null. */
    suspend fun lastTrip(vehicleId: Long): TripEntity? = dao.observeTrips(vehicleId).firstOrNull()?.firstOrNull()

    suspend fun samplesOf(tripId: Long): List<SampleEntity> = dao.samplesOf(tripId)

    suspend fun closeOpenTrips() {
        dao.openTrip()?.let { dao.updateTrip(it.copy(endedAt = clock())) }
    }
}
