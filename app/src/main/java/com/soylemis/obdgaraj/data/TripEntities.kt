package com.soylemis.obdgaraj.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Bir bağlantı oturumu (kontağı aç-kapa arası). Canlı veri kayıtları buna bağlanır. */
@Entity(tableName = "trips", indices = [Index("vehicleId")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val protocol: String? = null,
    val vin: String? = null,
    /** Kayıt sırasında izlenen PID'ler ("%02X" virgül listesi). */
    val pids: String = "",
)

/** Canlı veriden örneklenmiş tek ölçüm. (tripId, pid, ts) üzerinden indeksli. */
@Entity(tableName = "samples", indices = [Index("tripId", "pid")])
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val pid: Int,
    val value: Double,
    val ts: Long,
)
