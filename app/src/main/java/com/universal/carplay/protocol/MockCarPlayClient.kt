package com.universal.carplay.protocol

import android.util.Log
import com.universal.carplay.engine.AudioEngine
import com.universal.carplay.engine.VideoDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Kompüterdəki Virtual iPhone (Mock Server) ilə əlaqə quran və video/audio qəbul edən müştəri
 */
class MockCarPlayClient(
    private val host: String = "10.0.2.2", // Emulyator üçün kompüterin IP-si (və ya yerli Wi-Fi IP)
    private val port: Int = 9998,
    private val videoDecoder: VideoDecoder?,
    private val audioEngine: AudioEngine?
) {
    private val TAG = "MockCarPlayClient"

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    private var workerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    fun connect() {
        workerJob?.cancel()
        workerJob = scope.launch {
            try {
                Log.i(TAG, "Virtual iPhone-a qoşulur: $host:$port")
                socket = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), 4000)
                }
                outputStream = DataOutputStream(socket!!.getOutputStream())
                inputStream = DataInputStream(socket!!.getInputStream())

                onConnectionStateChanged?.invoke(true)
                Log.i(TAG, "Virtual iPhone ilə əlaqə quruldu!")

                // Şəbəkədən gələn paketləri davamlı oxuyuruq
                while (isActive && socket != null && !socket!!.isClosed) {
                    val length = inputStream!!.readInt()
                    if (length <= 0) continue

                    val type = inputStream!!.readByte().toInt()
                    val payload = ByteArray(length - 1)
                    inputStream!!.readFully(payload)

                    when (type) {
                        0x01 -> {
                            // Video H.264 NAL kadrı
                            videoDecoder?.feedFrame(payload, 0, payload.size, System.nanoTime() / 1000)
                        }
                        0x02 -> {
                            // Musiqi Audio (48kHz)
                            audioEngine?.writeMusic(payload, 0, payload.size)
                        }
                        0x03 -> {
                            // Danışıq / Siri Audio (16kHz)
                            audioEngine?.writeSpeech(payload, 0, payload.size)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Şəbəkə xətası: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }

    /**
     * Ekrana toxunma hadisəsini Virtual iPhone-a göndərir
     */
    fun sendTouchEvent(touchJson: String) {
        scope.launch {
            try {
                val bytes = touchJson.toByteArray(Charsets.UTF_8)
                outputStream?.let {
                    it.writeInt(bytes.size + 1)
                    it.writeByte(0x10) // Type 0x10: Touch Event
                    it.write(bytes)
                    it.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Toxunma göndərilərkən xəta: ${e.message}")
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
            socket = null
        } catch (ignored: Exception) {}
        onConnectionStateChanged?.invoke(false)
        Log.i(TAG, "Virtual iPhone ilə əlaqə kəsildi")
    }
}
