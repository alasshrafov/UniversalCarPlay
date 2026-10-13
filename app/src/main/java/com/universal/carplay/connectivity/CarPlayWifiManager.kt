package com.universal.carplay.connectivity

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Avtomobilin daxili Wi-Fi və Hotspot vəziyyətini idarə edən köməkçi sinif
 */
class CarPlayWifiManager(private val context: Context) {

    private val TAG = "CarPlay_WiFi"
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    /**
     * Cari aktiv yerli IP ünvanını qaytarır
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IP ünvanı tapılarkən xəta: ${e.message}")
        }
        return null
    }

    /**
     * Wi-Fi-ın aktiv olub-olmadığını yoxlayır
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager?.isWifiEnabled ?: false
    }
}
