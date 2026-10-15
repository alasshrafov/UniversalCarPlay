package com.universal.carplay.connectivity

import android.content.Context
import android.util.Log
import com.universal.carplay.engine.AudioEngine
import com.universal.carplay.engine.VideoDecoder

/**
 * Bütün bağlantı proseslərini (Bluetooth, Wi-Fi, mDNS, Server və Video axını) birləşdirən Əsas Sessiya Koordinatoru
 */
class CarPlaySessionCoordinator(
    private val context: Context,
    private val videoDecoder: VideoDecoder?,
    private val audioEngine: AudioEngine?,
    private val onStateChanged: (SessionState, String) -> Unit
) {
    companion object {
        private const val TAG = "CarPlay_Coordinator"
    }

    enum class SessionState {
        IDLE,
        INITIALIZING,
        WAITING_FOR_DEVICE,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    private val bluetoothAdvertiser = CarPlayBluetoothAdvertiser(context)
    private val mdnsService = CarPlayMdnsService(context)
    private val wifiManager = CarPlayWifiManager(context)

    private var carPlayServer: CarPlayServer? = null

    var currentState: SessionState = SessionState.IDLE
        private set

    /**
     * Bütün simsiz qəbuledici servisləri və port 7000 dinləyici serverini işə salır
     */
    fun startDiscovery(carName: String = "Universal CarPlay") {
        updateState(SessionState.INITIALIZING, "Şəbəkə və Bluetooth xidmətləri başladılır…")

        // 1. IP Ünvanını yoxlayırıq
        val ipAddress = try {
            wifiManager.getLocalIpAddress()
        } catch (_: Throwable) { null }
        Log.i(TAG, "Cari IP: $ipAddress")

        // 2. CarPlay TCP Serverini (Port 7000) başladırıq
        try {
            carPlayServer?.stop()
            carPlayServer = CarPlayServer(
                port = 7000,
                videoDecoder = videoDecoder,
                audioEngine = audioEngine,
                onClientConnected = { clientIp ->
                    onDeviceConnected(clientIp)
                },
                onClientDisconnected = {
                    updateState(SessionState.WAITING_FOR_DEVICE, "iPhone ayrıldı. Yeni qoşulma gözlənilir…")
                }
            ).apply { start() }
        } catch (e: Throwable) {
            Log.e(TAG, "Server başlarkən xəta: ${e.message}")
        }

        // 3. Bluetooth BLE elanını işə salırıq (Crash-proof)
        try {
            bluetoothAdvertiser.startAdvertising(carName)
        } catch (e: Throwable) {
            Log.e(TAG, "Bluetooth elanı xətası: ${e.message}")
        }

        // 4. Apple Bonjour (mDNS) servisini yayımlayırıq (Port 7000)
        try {
            mdnsService.publishService(serviceName = carName, port = 7000)
        } catch (e: Throwable) {
            Log.e(TAG, "mDNS servisi xətası: ${e.message}")
        }

        val ipDisplay = if (!ipAddress.isNullOrBlank()) ipAddress else "Wi-Fi Hotspot gözlənilir"
        updateState(
            SessionState.WAITING_FOR_DEVICE,
            "iPhone Gözlənilir (Bluetooth: $carName • IP: $ipDisplay • Port: 7000)"
        )
    }

    fun sendTouchEvent(touchJson: String) {
        carPlayServer?.sendTouchEvent(touchJson)
    }

    /**
     * Qoşulma uğurlu olduqda çağırılır
     */
    fun onDeviceConnected(deviceName: String) {
        updateState(SessionState.CONNECTED, "Qoşuldu: $deviceName")
    }

    /**
     * Sessiyanı dayandırır
     */
    fun stopDiscovery() {
        try {
            bluetoothAdvertiser.stopAdvertising()
        } catch (_: Throwable) {}
        try {
            mdnsService.unpublishService()
        } catch (_: Throwable) {}
        try {
            carPlayServer?.stop()
            carPlayServer = null
        } catch (_: Throwable) {}
        updateState(SessionState.IDLE, "Xidmətlər dayandırıldı")
    }

    private fun updateState(state: SessionState, message: String) {
        currentState = state
        onStateChanged(state, message)
    }
}
