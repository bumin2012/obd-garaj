package com.soylemis.obdgaraj.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class VehicleKind { CAR, MOTORCYCLE }

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: VehicleKind,
    val vin: String? = null,
    val protocol: String? = null,
    /** "7E8:01,05,0C;7E9:..." biçiminde, bkz. [VehicleRepository.encodeSupported]. */
    val supportedPids: String = "",
    val lastConnectedAt: Long? = null,
    val lastDtcCount: Int? = null,
)

@Entity(tableName = "dtc_history", indices = [Index("vehicleId")])
data class DtcHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val code: String,
    val ecu: String,
    val kind: String,
    val seenAt: Long,
)
