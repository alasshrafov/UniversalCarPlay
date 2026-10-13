package com.universal.carplay.connectivity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * Apple CarPlay üçün Bluetooth Low Energy (BLE) elanı (Advertising)
 * 
 * iPhone yaxınlaşdıqda bu BLE yayımını tutur və avtomobili CarPlay qəbuledicisi kimi tanıyır.
 */
@android.annotation.SuppressLint("MissingPermission")
class CarPlayBluetoothAdvertiser(private val context: Context) {

    private val TAG = "CarPlay_BLE"

    // Apple CarPlay rəsmi xidmət UUID-si
    private val CARPLAY_SERVICE_UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null

    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.i(TAG, "CarPlay Bluetooth elanı uğurla başladı! iPhone axtarışı aktivdir.")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.e(TAG, "CarPlay Bluetooth elanı uğursuz oldu: Xəta kodu=$errorCode")
        }
    }

    /**
     * Bluetooth yayımını başladır
     */
    fun startAdvertising(carName: String = "Universal CarPlay") {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth söndürülüb və ya dəstəklənmir.")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "Cihaz BLE Advertising dəstəkləmir.")
            return
        }

        // BLE Yayım Tənzimləmələri (Aşağı gecikmə və yüksək güc)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0) // Limitsiz
            .build()

        // Yayım paketinə CarPlay xidmət məlumatlarını əlavə edirik
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(CARPLAY_SERVICE_UUID))
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth icazəsi çatışmır: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth xətası: ${e.message}")
        }
    }

    /**
     * Yayımı dayandırır
     */
    fun stopAdvertising() {
        if (!isAdvertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.i(TAG, "Bluetooth elanı dayandırıldı")
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth dayandırılarkən xəta: ${e.message}")
        }
    }
}
