package com.universal.carplay.connectivity

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/**
 * Apple Bonjour (mDNS) servisi
 * 
 * Yerli Wi-Fi şəbəkəsində "_carplay._tcp" və "_airplay._tcp" xidmətlərini yayımlayır ki,
 * iPhone eyni Wi-Fi-a qoşulan kimi avtomobilin CarPlay xidmətini avtomatik tapsın.
 */
class CarPlayMdnsService(private val context: Context) {

    private val TAG = "CarPlay_mDNS"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isPublished = false

    /**
     * mDNS xidmətini qeydiyyatdan keçirir və elan edir
     */
    fun publishService(serviceName: String = "Universal-CarPlay", port: Int = 7000) {
        if (nsdManager == null) {
            Log.e(TAG, "NsdManager dəstəklənmir.")
            return
        }

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = "_carplay._tcp"
            this.port = port

            // Apple CarPlay spesifikasiyasına uyğun TXT atributları
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("model", "CarPlay1,1")
                setAttribute("srcvers", "220.68")
                setAttribute("features", "0x1e") // Ekran, səs, toxunma imkanları
                setAttribute("flags", "0x4")
                setAttribute("vv", "2")
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                isPublished = true
                Log.i(TAG, "mDNS CarPlay servisi uğurla qeydiyyatdan keçdi: ${NsdServiceInfo.serviceName} (Port: $port)")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isPublished = false
                Log.e(TAG, "mDNS qeydiyyat xətası: kod=$errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                isPublished = false
                Log.i(TAG, "mDNS servisi dayandırıldı.")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "mDNS dayandırılarkən xəta: kod=$errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "mDNS servisi başlarkən xəta: ${e.message}", e)
        }
    }

    /**
     * mDNS servisini dayandırır
     */
    fun unpublishService() {
        if (!isPublished || registrationListener == null || nsdManager == null) return
        try {
            nsdManager.unregisterService(registrationListener)
            isPublished = false
        } catch (e: Exception) {
            Log.e(TAG, "mDNS ləğv edilərkən xəta: ${e.message}")
        }
    }
}
