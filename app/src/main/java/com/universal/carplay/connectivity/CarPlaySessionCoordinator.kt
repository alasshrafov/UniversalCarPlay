package com.universal.carplay.connectivity

import android.content.Context
import android.util.Log

/**
 * Bütün bağlantı proseslərini (Bluetooth, Wi-Fi, mDNS və Video axını) birləşdirən Əsas Sessiya Koordinatoru
 */
class CarPlaySessionCoordinator(
    private val context: Context,
    private val onStateChanged: (SessionState, String) -> Unit
) {
    private val TAG = "CarPlay_Coordinator"

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

    var currentState: SessionState = SessionState.IDLE
        private set

    /**
     * Bütün simsiz qəbuledici servisləri işə salır
     */
    fun startDiscovery(carName: String = "Universal CarPlay") {
        updateState(SessionState.INITIALIZING, "Şəbəkə və Bluetooth xidmətləri başladılır…")

        // 1. IP Ünvanını yoxlayırıq
        val ipAddress = wifiManager.getLocalIpAddress()
        Log.i(TAG, "Cari IP: $ipAddress")

        // 2. Bluetooth elanını işə salırıq
        bluetoothAdvertiser.startAdvertising(carName)

        // 3. Apple Bonjour (mDNS) servisini yayımlayırıq
        mdnsService.publishService(serviceName = carName, port = 7000)

        updateState(
            SessionState.WAITING_FOR_DEVICE,
            "iPhone gözlənilir (Bluetooth: $carName, IP: ${ipAddress ?: "Wi-Fi yoxdur"})"
        )
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
        bluetoothAdvertiser.stopAdvertising()
        mdnsService.unpublishService()
        updateState(SessionState.IDLE, "Xidmətlər dayandırıldı")
    }

    private fun updateState(state: SessionState, message: String) {
        currentState = state
        onStateChanged(state, message)
    }
}
