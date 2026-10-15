package com.universal.carplay.connectivity

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/**
 * Apple Bonjour (mDNS) servisi
 * 
 * Yerli Wi-Fi şəbəkəsində həm "_carplay._tcp", həm də "_airplay._tcp" xidmətlərini yayımlayır ki,
 * iPhone eyni Wi-Fi Hotspot-a qoşulan kimi:
 * 1. Apple CarPlay qəbuledicisi kimi avtomatik tapsın.
 * 2. iPhone İdarəetmə Mərkəzi (Control Center) -> "Ekran Yansıtma" (Screen Mirroring) bölməsində CarTwin dərhal görünsün!
 */
class CarPlayMdnsService(private val context: Context) {

    private val TAG = "CarPlay_mDNS"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var carplayListener: NsdManager.RegistrationListener? = null
    private var airplayListener: NsdManager.RegistrationListener? = null
    private var isCarplayPublished = false
    private var isAirplayPublished = false

    /**
     * Həm CarPlay, həm də AirPlay Screen Mirroring mDNS xidmətlərini elan edir
     */
    fun publishService(serviceName: String = "CarTwin-CarPlay", port: Int = 7000) {
        if (nsdManager == null) {
            Log.e(TAG, "NsdManager dəstəklənmir.")
            return
        }

        // 1. _carplay._tcp yayımı
        publishCarPlay(serviceName, port)

        // 2. _airplay._tcp yayımı (iPhone Control Center Screen Mirroring üçün)
        publishAirPlay(serviceName, port)
    }

    private fun publishCarPlay(serviceName: String, port: Int) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = serviceName
                this.serviceType = "_carplay._tcp"
                this.port = port

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAttribute("model", "CarPlay1,1")
                    setAttribute("srcvers", "220.68")
                    setAttribute("features", "0x1e")
                    setAttribute("flags", "0x4")
                    setAttribute("vv", "2")
                }
            }

            carplayListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    isCarplayPublished = true
                    Log.i(TAG, "mDNS CarPlay servisi uğurla qeydiyyatdan keçdi: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    isCarplayPublished = false
                    Log.w(TAG, "mDNS CarPlay qeydiyyat xətası: kod=$errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    isCarplayPublished = false
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS CarPlay dayandırılarkən xəta: kod=$errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, carplayListener)
        } catch (e: Throwable) {
            Log.e(TAG, "CarPlay mDNS xətası: ${e.message}")
        }
    }

    private fun publishAirPlay(serviceName: String, port: Int) {
        try {
            val cleanName = serviceName.replace("CarPlay", "Display").trim()
            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = cleanName
                this.serviceType = "_airplay._tcp"
                this.port = port

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAttribute("model", "AppleTV3,2")
                    setAttribute("srcvers", "220.68")
                    setAttribute("features", "0x5A7FFFF7,0x1E")
                    setAttribute("flags", "0x4")
                    setAttribute("deviceid", "00:11:22:33:44:55")
                }
            }

            airplayListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    isAirplayPublished = true
                    Log.i(TAG, "mDNS AirPlay (Screen Mirroring) servisi qeydiyyatdan keçdi: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    isAirplayPublished = false
                    Log.w(TAG, "mDNS AirPlay qeydiyyat xətası: kod=$errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    isAirplayPublished = false
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS AirPlay dayandırılarkən xəta: kod=$errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, airplayListener)
        } catch (e: Throwable) {
            Log.e(TAG, "AirPlay mDNS xətası: ${e.message}")
        }
    }

    /**
     * Hər iki mDNS servisini dayandırır
     */
    fun unpublishService() {
        if (nsdManager == null) return

        if (isCarplayPublished && carplayListener != null) {
            try {
                nsdManager.unregisterService(carplayListener)
                isCarplayPublished = false
            } catch (_: Throwable) {}
        }

        if (isAirplayPublished && airplayListener != null) {
            try {
                nsdManager.unregisterService(airplayListener)
                isAirplayPublished = false
            } catch (_: Throwable) {}
        }
    }
}
