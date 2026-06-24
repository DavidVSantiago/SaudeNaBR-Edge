package dev.algol.saudenabr.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import dev.algol.saudenabr.database.AppDatabase
import dev.algol.saudenabr.database.HealthData
import dev.algol.saudenabr.database.HealthDataDao
import dev.algol.saudenabr.sensor.FakeSensor
import dev.algol.saudenabr.websocket.Resultados
import dev.algol.saudenabr.websocket.Sender
import kotlinx.coroutines.*

class AppService : Service() {

    private val TAG = "SaudeNaBR_Service"
    private val CHANNEL_ID = "AppServiceChannel"

    // Escopo que gerencia as corotinas do serviço
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var healthDataDao: HealthDataDao

    // para simular vários dispositivos geradores de dados
    private val fakeSensor_list = mutableListOf<FakeSensor>()
    private val sender_list = mutableListOf<Sender>()
    private val numSimulatedDevices = 150

    // ********************************************************************************************
    // Ciclo de vida do Service
    // ********************************************************************************************

    override fun onCreate() {
        super.onCreate()
        // Inicializa o Banco
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "saude_db").build()
        healthDataDao = db.healthDataDao()
        // Inicializa a lista de sensores e senders
        for(i in 1..numSimulatedDevices){
            val deviceId = String.format("%03d",i) // "001", "002", "003", ...
            fakeSensor_list.add(FakeSensor(deviceId)) // adiciona um novo sensor à lista
            
            val sender = Sender()
            sender.connect()
            sender_list.add(sender)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, createNotification())

        // 1. Inicia o Produtor (Sensor -> Banco)
        startProductionData();

        // 2. Inicia o Consumidor (Banco -> VPS)
        startConsumptionData();

        return START_STICKY
    }

    fun startProductionData(){
        fakeSensor_list.forEach { fakeSensor ->
            fakeSensor.startGenerating(serviceScope) { data ->
                // Callback do sensor: Salva no banco de forma assíncrona
                serviceScope.launch {
                    val record = parseToEntity(data)
                    val id = healthDataDao.insert(record)
                    Log.i(TAG, "💾 [${record.idMotorista}] Registro salvo no Banco! ID: $id")
                }
            }
        }
    }

    fun startConsumptionData(){

        /* percorre a lista de senders */
        for (i in 0 until numSimulatedDevices) {
            val deviceId = String.format("%03d", i + 1)
            val sender = sender_list[i]

            serviceScope.launch {
                while (isActive) { // Loop infinito enquanto o serviço estiver vivo
                    // Busca o registro mais antigo no banco para este motorista
                    val data = healthDataDao.getNextByMotorista(deviceId)

                    if (data == null) { // Se não há registro de telemetria no banco...
                        // espera um pouco antes de verificar novamente
                        delay(2000)
                        continue
                    }

                    val payload = data.toText() // converte o dado para formato de texto
                    Log.d(TAG, "📤 [$deviceId] Tentando enviar: $payload")

                    val resultado = sender.publishAndWait(payload)

                    when (resultado){
                        // se obteve um ACK do servidor,
                        Resultados.SUCCESS ->{
                            Log.i(TAG, "✅ [$deviceId] Registro enviado e removido do banco! ID: ${data.id}")
                            healthDataDao.delete(data) // remove do banco
                        }
                        // falhou por um NACK de pacote defeituoso
                        Resultados.MALFORMED->{
                            Log.e(TAG, "🛑 [$deviceId] VPS rejeitou (0,M). Descartando Pílula Envenenada: $payload")
                            healthDataDao.delete(data) // remove do banco
                        }
                        // falhou por rede ou dado duplicado
                        Resultados.FAILED_RETRY->{
                            Log.w(TAG, "⏳ [$deviceId] Falha ao enviar ou Timeout. Tentando novamente em 5s...")
                            delay(5000)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fakeSensor_list.forEach { it.stopGenerating() } // interrompe todas as execuções
        sender_list.forEach { it.disconnect() } // desconecta todos os senders
        serviceScope.cancel() // Cancela todas as tarefas pendentes
        Log.d(TAG, "Serviço encerrado.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ********************************************************************************************
    // Métodos auxiliares
    // ********************************************************************************************

    private fun parseToEntity(data: String): HealthData {
        val p = data.split(",")
        return HealthData(
            timestamp = p[0].toLong(),
            idMotorista = p[1],
            bpm = p[2].toInt(),
            vfc = p[3].toInt(),
            spo2 = p[4].toInt()
        )
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SaudeNaBR")
            .setContentText("Monitorando saúde em tempo real...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Sensor Service", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}