package dev.algol.saudenabr.websocket

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import java.util.concurrent.TimeUnit

// tipos de retorno da VPS
enum class Resultados {
    SUCCESS,       // VPS respondeu ACK (1,timestamp)
    MALFORMED,     // VPS respondeu NACK por payload inválido (0,M)
    FAILED_RETRY   // Timeout, queda de rede ou erro interno (0,timestamp)
}

class Sender : WebSocketListener() {
    private companion object {
        const val TAG = "SaudeNaBR_Sender"
        //const val WS_URL = "wss://saudenabr.algol.dev/savetelemetry"
        const val WS_URL = "ws://localhost:3003/loadtelemetry"
        const val API_KEY = "#htxrlLaWaU3F8aNnjviFhreqyWzI1YowyZ8bFoCBNjhp8umKToLxTF4kau0tnp@"
    }

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null

    @Volatile
    var isConnected = false
        private set

    private var ackDeferred: CompletableDeferred<Resultados>? = null

    // ********************************************************************************************
    // Métodos
    // ********************************************************************************************

    /** Inicia a conexão com a VPS */
    fun connect() {
        if (client == null) {
            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
        iniciarWebSocket()
    }

    private fun iniciarWebSocket() {
        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", API_KEY)
            .build()

        webSocket = client?.newWebSocket(request, this)
    }

    /** Encerra a conexão e libera recursos */
    fun disconnect() {
        webSocket?.close(1000, "Serviço destruído")
        client?.dispatcher?.executorService?.shutdown()
        client = null
        isConnected = false
        Log.d(TAG, "Recursos do Sender liberados.")
    }

    /** Envia dados e aguarda o ACK (confirmação) da VPS */
    suspend fun publishAndWait(payload: String): Resultados {
        // Se não houver conexão, falhou mas deve tentar reenviar depois
        if (!isConnected || webSocket == null) return Resultados.FAILED_RETRY

        val deferred = CompletableDeferred<Resultados>()
        ackDeferred = deferred

        val sent = webSocket?.send(payload) ?: false
        if (!sent) return Resultados.FAILED_RETRY

        return try {
            // Aguarda o ACK por até 5 segundos
            withTimeoutOrNull(5000) {
                deferred.await()
            } ?: Resultados.FAILED_RETRY
        } catch (e: Exception) {
            Resultados.FAILED_RETRY
        } finally {
            ackDeferred = null
        }
    }

    // ********************************************************************************************
    // Ciclo de vida do WebSocket (okhttp3)
    // ********************************************************************************************


    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "✅ Conectado com sucesso à VPS!")
        isConnected = true
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val partes = text.trim().split(",")
        if (partes.size<2) return

        val status = partes[0] // '0' ou '1'
        val info = partes[1]   // timestamp ou 'M'

        val resultado = when{
            status == "1" -> Resultados.SUCCESS
            status == "0" && info == "M" -> Resultados.MALFORMED
            else -> Resultados.FAILED_RETRY
        }
        // Completa a espera do publishAndWait
        ackDeferred?.complete(resultado)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isConnected = false
        Log.e(TAG, "❌ Falha na conexão WebSocket: ${t.message}")

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            delay(5000)
            if (!isConnected) {
                Log.d(TAG, "Tentando reconectar à VPS...")
                iniciarWebSocket()
            }
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        isConnected = false
        Log.d(TAG, "🔴 Conexão Fechada.")
    }
}