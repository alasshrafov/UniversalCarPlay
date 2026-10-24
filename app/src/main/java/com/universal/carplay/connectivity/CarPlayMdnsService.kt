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
 *
 * 100% Sinxronizasiyalı və Crash-Proof tərtibat: Təkrar qeydiyyatlar və NsdManager çöküşlərinin qarşısı tam alınır.
 */
class CarPlayMdnsService(private val context: Context) {

    private val TAG = "CarPlay_mDNS"
    private val nsdManager = try {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    } catch (_: Throwable) {
        null
    }

    private val lock = Any()
    private var carplayListener: NsdManager.RegistrationListener? = null
    private var airplayListener: NsdManager.RegistrationListener? = null

    /**
     * Həm CarPlay, həm də AirPlay Screen Mirroring mDNS xidmətlərini təhlükəsiz elan edir
     */
    fun publishService(serviceName: String = "CarTwin-CarPlay", port: Int = 7000) {
        synchronized(lock) {
            if (nsdManager == null) {
                Log.w(TAG, "NsdManager dəstəklənmir.")
                return
            }

            // Əvvəlki qeydiyyatları tam və təhlükəsiz şəkildə dayandırırıq
            unpublishServiceLocked()

            // 1. _carplay._tcp yayımı
            publishCarPlayLocked(serviceName, port)

            // 2. _airplay._tcp yayımı
            publishAirPlayLocked(serviceName, port)
        }
    }

    private fun publishCarPlayLocked(serviceName: String, port: Int) {
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

            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.i(TAG, "mDNS CarPlay servisi uğurla qeydiyyatdan keçdi: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS CarPlay qeydiyyat xətası: kod=$errorCode")
                    synchronized(lock) {
                        if (carplayListener === this) carplayListener = null
                    }
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    Log.i(TAG, "mDNS CarPlay dayandırıldı")
                    synchronized(lock) {
                        if (carplayListener === this) carplayListener = null
                    }
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS CarPlay dayandırılarkən xəta: kod=$errorCode")
                    synchronized(lock) {
                        if (carplayListener === this) carplayListener = null
                    }
                }
            }

            carplayListener = listener
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Throwable) {
            Log.e(TAG, "CarPlay mDNS xətası: ${e.message}")
            carplayListener = null
        }
    }

    private fun publishAirPlayLocked(serviceName: String, port: Int) {
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

            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.i(TAG, "mDNS AirPlay servisi qeydiyyatdan keçdi: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS AirPlay qeydiyyat xətası: kod=$errorCode")
                    synchronized(lock) {
                        if (airplayListener === this) airplayListener = null
                    }
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    Log.i(TAG, "mDNS AirPlay dayandırıldı")
                    synchronized(lock) {
                        if (airplayListener === this) airplayListener = null
                    }
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS AirPlay dayandırılarkən xəta: kod=$errorCode")
                    synchronized(lock) {
                        if (airplayListener === this) airplayListener = null
                    }
                }
            }

            airplayListener = listener
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Throwable) {
            Log.e(TAG, "AirPlay mDNS xətası: ${e.message}")
            airplayListener = null
        }
    }

    /**
     * Hər iki mDNS servisini təhlükəsiz dayandırır
     */
    fun unpublishService() {
        synchronized(lock) {
            unpublishServiceLocked()
        }
    }

    private fun unpublishServiceLocked() {
        if (nsdManager == null) return

        carplayListener?.let { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Throwable) {
                Log.w(TAG, "CarPlay unregister xətası: ${e.message}")
            } finally {
                carplayListener = null
            }
        }

        airplayListener?.let { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Throwable) {
                Log.w(TAG, "AirPlay unregister xətası: ${e.message}")
            } finally {
                airplayListener = null
            }
        }
    }
}
