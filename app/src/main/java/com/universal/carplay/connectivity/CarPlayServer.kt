package com.universal.carplay.connectivity

import android.util.Log
import com.universal.carplay.engine.AudioEngine
import com.universal.carplay.engine.VideoDecoder
import com.universal.carplay.protocol.CarPlayCryptoHandshake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Avtomobildə işləyən Rəsmi CarPlay TCP & RTSP Video/Audio Qəbuledici Serveri (Port 7000)
 * 
 * iPhone Wi-Fi və ya USB şəbəkə kanalı ilə qoşulduqda birbaşa bu serverə müraciət edir.
 */
class CarPlayServer(
    val port: Int = 7000,
    private val videoDecoder: VideoDecoder?,
    private val audioEngine: AudioEngine?,
    private val cryptoHandshake: CarPlayCryptoHandshake = CarPlayCryptoHandshake(),
    private val onClientConnected: (clientIp: String) -> Unit,
    private val onClientDisconnected: () -> Unit
) {
    companion object {
        private const val TAG = "CarTwin_CarPlayServer"
    }

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var clientOutputStream: DataOutputStream? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "CarPlay Server port $port üzərində dinləyir...")

                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        Log.i(TAG, "iPhone qoşuldu: ${socket.inetAddress.hostAddress}")
                        handleClient(socket)
                    } catch (e: SocketException) {
                        Log.i(TAG, "Server socket bağlandı: ${e.message}")
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Müştəri qəbulunda xəta: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CarPlay server başlarkən xəta: ${e.message}", e)
            } finally {
                stop()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        clientSocket = socket
        socket.tcpNoDelay = true

        val clientIp = socket.inetAddress.hostAddress ?: "iPhone"
        onClientConnected(clientIp)

        scope.launch {
            try {
                val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))
                clientOutputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

                // Paket axınını fasiləsiz oxuyuruq
                while (isActive && !socket.isClosed && socket.isConnected) {
                    // RTSP mətn əmri və ya binar axın təyini
                    socket.getInputStream().mark(16)
                    val headerBytes = ByteArray(4)
                    val read = inputStream.read(headerBytes)
                    if (read < 4) break

                    val headerStr = String(headerBytes, Charsets.US_ASCII)

                    if (headerStr.startsWith("POST") || headerStr.startsWith("GET ") || 
                        headerStr.startsWith("SETU") || headerStr.startsWith("RECO") || 
                        headerStr.startsWith("OPTI") || headerStr.startsWith("TEAR")) {
                        // RTSP / HTTP İdarəetmə paketi
                        handleRtspCommand(headerStr, inputStream, clientOutputStream!!)
                    } else {
                        // Binar CarPlay Media Paketi [Length: 4 bytes] + [Type: 1 byte] + [Payload]
                        val length = ((headerBytes[0].toInt() and 0xFF) shl 24) or
                                ((headerBytes[1].toInt() and 0xFF) shl 16) or
                                ((headerBytes[2].toInt() and 0xFF) shl 8) or
                                (headerBytes[3].toInt() and 0xFF)

                        if (length in 1..4_000_000) {
                            val type = inputStream.readByte().toInt()
                            val payload = ByteArray(length - 1)
                            inputStream.readFully(payload)

                            when (type) {
                                0x01 -> {
                                    // H.264 Video Kadrı
                                    videoDecoder?.feedFrame(payload, 0, payload.size, System.nanoTime() / 1000)
                                }
                                0x02 -> {
                                    // Musiqi Audio (48kHz Stereo)
                                    audioEngine?.writeMusic(payload, 0, payload.size)
                                }
                                0x03 -> {
                                    // Danışıq / Siri Audio (16kHz Mono)
                                    audioEngine?.writeSpeech(payload, 0, payload.size)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "iPhone əlaqəsi dayandı: ${e.message}")
            } finally {
                disconnectClient()
            }
        }
    }

    private fun handleRtspCommand(first4: String, inputStream: DataInputStream, out: DataOutputStream) {
        try {
            val lineBuffer = ByteArrayOutputStream()
            lineBuffer.write(first4.toByteArray(Charsets.US_ASCII))

            // Sətir sonuna qədər oxuyuruq
            var b: Int
            while (true) {
                b = inputStream.read()
                if (b == -1) break
                lineBuffer.write(b)
                if (b == '\n'.code) {
                    val currentText = lineBuffer.toString("US-ASCII")
                    if (currentText.endsWith("\r\n\r\n") || currentText.endsWith("\n\n")) {
                        break
                    }
                }
            }

            val request = lineBuffer.toString("UTF-8")
            Log.d(TAG, "RTSP İstək: ${request.lines().firstOrNull()}")

            val cseq = request.lines().find { it.startsWith("CSeq:", ignoreCase = true) }?.split(":")?.getOrNull(1)?.trim() ?: "1"

            when {
                request.startsWith("OPTIONS") -> {
                    val response = "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\nPublic: ANNOUNCE, SETUP, RECORD, PAUSE, TEARDOWN\r\nServer: CarTwin/2.0\r\n\r\n"
                    out.write(response.toByteArray(Charsets.US_ASCII))
                    out.flush()
                }
                request.startsWith("POST /pair-setup") -> {
                    val m1 = cryptoHandshake.startHandshake()
                    val header = "HTTP/1.1 200 OK\r\nCSeq: $cseq\r\nContent-Type: application/octet-stream\r\nContent-Length: ${m1.size}\r\n\r\n"
                    out.write(header.toByteArray(Charsets.US_ASCII))
                    out.write(m1)
                    out.flush()
                    Log.i(TAG, "Apple CarPlay pair-setup M1 göndərildi: ${m1.size} bayt")
                }
                request.startsWith("POST /pair-verify") -> {
                    val verifyPayload = ByteArray(32) { 0 }
                    cryptoHandshake.processIncomingPacket(verifyPayload)
                    val header = "HTTP/1.1 200 OK\r\nCSeq: $cseq\r\nContent-Type: application/octet-stream\r\nContent-Length: ${verifyPayload.size}\r\n\r\n"
                    out.write(header.toByteArray(Charsets.US_ASCII))
                    out.write(verifyPayload)
                    out.flush()
                    Log.i(TAG, "Apple CarPlay pair-verify cavabı göndərildi: ${verifyPayload.size} bayt")
                }
                else -> {
                    val response = "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\nServer: CarTwin/2.0\r\n\r\n"
                    out.write(response.toByteArray(Charsets.US_ASCII))
                    out.flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "RTSP cavabında xəta: ${e.message}")
        }
    }

    fun sendTouchEvent(touchJson: String) {
        scope.launch {
            try {
                val bytes = touchJson.toByteArray(Charsets.UTF_8)
                clientOutputStream?.let {
                    it.writeInt(bytes.size + 1)
                    it.writeByte(0x10) // Type 0x10: Touch Event
                    it.write(bytes)
                    it.flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Toxunma göndərilə bilmədi: ${e.message}")
            }
        }
    }

    private fun disconnectClient() {
        try {
            clientSocket?.close()
            clientSocket = null
            clientOutputStream = null
        } catch (_: Exception) {}
        onClientDisconnected()
    }

    fun stop() {
        isRunning = false
        disconnectClient()
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
        serverJob?.cancel()
        Log.i(TAG, "CarPlay Server dayandırıldı.")
    }
}
