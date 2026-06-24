package dev.algol.saudenabr.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HealthDataDao {

    @Insert
    suspend fun insert(data: HealthData): Long

    // Retornamos um HealthData opcional (?), pois o banco pode estar vazio
    @Query("SELECT * FROM health_data ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNext(): HealthData?

    @Query("SELECT * FROM health_data WHERE idMotorista = :idMotorista ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextByMotorista(idMotorista: String): HealthData?

    @Delete
    suspend fun delete(data: HealthData)
}