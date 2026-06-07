package dev.algol.saudenabr.sensor

import kotlinx.coroutines.*
import kotlin.random.Random

class FakeSensor(private val driverId: String = "001") {

    private var job: Job? = null

    /**
     * Inicia a geração de dados.
     * @param scope O escopo da corotina (geralmente vindo do Service)
     * @param onData O callback que recebe o dado)
     */
    fun startGenerating(scope: CoroutineScope, onData: (String) -> Unit) {
        // Evita iniciar múltiplos loops se já estiver rodando
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                val unixTs = System.currentTimeMillis() / 1000

                // Geração de números aleatórios simplificada em Kotlin
                val bpm = Random.nextInt(60, 101)   // 60 a 100
                val vfc = Random.nextInt(20, 101)   // 20 a 100
                val spo2 = Random.nextInt(95, 101)  // 95 a 100

                // Template de String (muito mais limpo que String.format)
                val dataLine = "$unixTs,$driverId,$bpm,$vfc,$spo2"

                onData(dataLine) // invoca o callback passando o dado gerado

                delay(1000) // Espera 1 segundo sem travar a thread
            }
        }
    }

    fun stopGenerating() {
        job?.cancel()
    }
}