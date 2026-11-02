package com.soylemis.obdgaraj.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Insert
    suspend fun insertSamples(samples: List<SampleEntity>)

    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId ORDER BY startedAt DESC LIMIT 30")
    fun observeTrips(vehicleId: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    @Query("SELECT * FROM samples WHERE tripId = :tripId ORDER BY ts")
    suspend fun samplesOf(tripId: Long): List<SampleEntity>

    @Query("SELECT * FROM samples WHERE tripId = :tripId AND pid = :pid ORDER BY ts")
    suspend fun samplesOf(tripId: Long, pid: Int): List<SampleEntity>

    /** Yarım kalmış (endedAt null) seferleri bağlantı kopunca kapatmak için. */
    @Query("SELECT * FROM trips WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openTrip(): TripEntity?
}
