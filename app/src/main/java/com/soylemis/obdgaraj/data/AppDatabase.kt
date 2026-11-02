package com.soylemis.obdgaraj.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VehicleEntity::class, DtcHistoryEntity::class, TripEntity::class, SampleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun tripDao(): TripDao

    companion object {
        fun create(context: Context): AppDatabase =
            // v1→v2 yalnız yeni tablo ekler (trips, samples); mevcut sürüm beta olduğundan
            // yıkıcı geçiş kabul — garaj ve arıza geçmişi yeniden tohumlanır.
            Room.databaseBuilder(context, AppDatabase::class.java, "obd-garaj.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
