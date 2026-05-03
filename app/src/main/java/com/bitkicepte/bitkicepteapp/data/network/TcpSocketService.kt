package com.bitkicepte.bitkicepteapp.data.network

import com.bitkicepte.bitkicepteapp.domain.model.ActuatorState
import com.bitkicepte.bitkicepteapp.domain.model.SensorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TcpSocketService {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    sealed class ConnectResult {
        object Success : ConnectResult()
        data class Failure(val message: String) : ConnectResult()
    }

    suspend fun connect(host: String, port: Int): ConnectResult = withContext(Dispatchers.IO) {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5_000)
            s.soTimeout = 10_000
            socket = s
            writer = PrintWriter(s.getOutputStream(), true)
            ConnectResult.Success
        } catch (e: Exception) {
            ConnectResult.Failure(e.message ?: "Bağlantı hatası")
        }
    }

    fun disconnect() {
        runCatching { writer?.close(); socket?.close() }
        socket = null; writer = null
    }

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    /** ESP32'den sürekli JSON satırı okur → SensorData akışı */
    fun dataStream(): Flow<SensorData> = flow {
        val reader = BufferedReader(InputStreamReader(socket?.getInputStream() ?: return@flow))
        while (isConnected) {
            try {
                val line = reader.readLine() ?: break
                Esp32Packet.parse(line)?.let { emit(it) }
            } catch (_: SocketTimeoutException) {
                // normal, devam
            } catch (_: Exception) {
                break
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendCommand(state: ActuatorState) = withContext(Dispatchers.IO) {
        writer?.println(Esp32Packet.buildCommand(state))
    }
}
