package dev.algol.saudenabr.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HealthData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDataDao(): HealthDataDao
}