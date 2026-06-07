package dev.algol.saudenabr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_data")
data class HealthData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,            // Valor padrão 0 para o Room gerar o ID automaticamente
    val timestamp: Long,
    val idMotorista: String,
    val bpm: Int,
    val vfc: Int,
    val spo2: Int,
) {
    /** Método utilitário para converter o objeto de volta para texto
     * Útil na hora de enviar para a VPS */
    fun toText(): String {
        return "$timestamp,$idMotorista,$bpm,$vfc,$spo2"
    }
}