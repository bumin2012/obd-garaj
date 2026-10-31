package com.soylemis.obdgaraj.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY id")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(list: List<VehicleEntity>)

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun get(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE vin = :vin LIMIT 1")
    suspend fun findByVin(vin: String): VehicleEntity?

    @Update
    suspend fun update(v: VehicleEntity)

    @Insert
    suspend fun insertDtcs(list: List<DtcHistoryEntity>)

    @Query("SELECT * FROM dtc_history WHERE vehicleId = :vehicleId ORDER BY seenAt DESC")
    fun observeDtcs(vehicleId: Long): Flow<List<DtcHistoryEntity>>
}
