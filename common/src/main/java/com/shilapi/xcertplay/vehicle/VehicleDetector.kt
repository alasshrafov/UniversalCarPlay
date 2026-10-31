package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log

/**
 * BAtAn Cin avtomobillTrini (BYD, Changan, Geely, Zeekr, Chery, Jetour, Jaecoo,
 * Haval, Tank, Li Auto, NIO, XPeng, GAC, Voyah, Hongqi, BAIC, MG, Xiaomi, Leapmotor)
 * 100% dTxTliqlT tTyin edTn Universal Detektor
 */
object VehicleDetector {
    private const val TAG = "CarTwin_Detector"
    private const val PREFS = "cartwin_vehicle_config"
    private const val KEY_BRAND = "detected_brand_id"

    fun getActiveBrand(context: Context): VehicleBrand {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BRAND, null)
        if (saved != null) {
            val brand = VehicleBrand.fromId(saved)
            Log.i(TAG, "Yadda saxlanm avtomobil brendi yAklTndi: ${brand.brandName}")
            return brand
        }

        val detected = autoDetect(context)
        Log.i(TAG, "Avtomatik tTyin edilTn Cin avtomobili: ${detected.brandName} (${detected.groupName})")
        saveBrand(context, detected)
        return detected
    }

    fun saveBrand(context: Context, brand: VehicleBrand) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BRAND, brand.id)
            .apply()
    }

    private fun autoDetect(context: Context): VehicleBrand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val full = "$manufacturer $brand $model $fingerprint $hardware"

        Log.i(TAG, "Avtomobil aparat parametrlTri: $full")

        // 1. BYD Qrupu (BYD, Denza, Yangwang, Fangchengbao)
        if (full.contains("byd") || full.contains("dilink") || full.contains("denza") || full.contains("yangwang") ||
            checkAnyPackage(context, "com.byd.carsettings", "com.byd.service", "com.byd.amapservice", "com.denza.car")) {
            return VehicleBrand.BYD
        }

        // 2. Changan Qrupu (Changan, Deepal, Avatr, Qiyuan)
        if (full.contains("changan") || full.contains("deepal") || full.contains("avatr") || full.contains("incall") || full.contains("shenlan") ||
            checkAnyPackage(context, "com.changan.incall", "com.changan.carsettings", "com.deepal.car", "com.avatr.car")) {
            return VehicleBrand.CHANGAN
        }

        // 3. Zeekr (001, 007, 009, X)
        if (full.contains("zeekr") || checkAnyPackage(context, "com.zeekrlife.car", "com.zeekr.carsettings")) {
            return VehicleBrand.ZEEKR
        }

        // 4. Geely Qrupu (Geely, Galaxy, Lynk & Co, Geometry)
        if (full.contains("geely") || full.contains("ecarx") || full.contains("flyme") || full.contains("lynk") ||
            checkAnyPackage(context, "com.geely.inputservice", "com.geely.ecarx", "com.ecarx.carsettings", "com.lynkco.car")) {
            return VehicleBrand.GEELY
        }

        // 5. Chery Qrupu (Chery, Jetour, Jaecoo, Omoda, Exeed, iCAR)
        if (full.contains("chery") || full.contains("exeed") || full.contains("omoda") || full.contains("jaecoo") || full.contains("jetour") || full.contains("icar") ||
            checkAnyPackage(context, "com.chery.automap", "com.chery.carsettings", "com.jetour.car", "com.exeed.service")) {
            return VehicleBrand.CHERY
        }

        // 6. Great Wall Motor (Haval, Tank, Wey, Poer, Ora)
        if (full.contains("haval") || full.contains("gwm") || full.contains("greatwall") || full.contains("tank") || full.contains("wey") || full.contains("poer") ||
            checkAnyPackage(context, "com.greatwall.hvac", "com.gwm.carsettings", "com.tank.service", "com.haval.car")) {
            return VehicleBrand.HAVAL
        }

        // 7. Li Auto (L6, L7, L8, L9, Mega)
        if (full.contains("liauto") || full.contains("chj") || full.contains("ideal") ||
            checkAnyPackage(context, "com.liauto.car", "com.chj.carsettings")) {
            return VehicleBrand.LI_AUTO
        }

        // 8. Xiaomi Auto (SU7, SU7 Max / Ultra)
        if (full.contains("xiaomi") || full.contains("su7") || full.contains("hyperos") ||
            checkAnyPackage(context, "com.xiaomi.car", "com.miui.car", "com.xiaomi.ev")) {
            return VehicleBrand.XIAOMI_AUTO
        }

        // 9. NIO & Onvo
        if (full.contains("nio") || full.contains("onvo") || full.contains("banyan") ||
            checkAnyPackage(context, "com.nio.car", "com.nio.banyan")) {
            return VehicleBrand.NIO
        }

        // 10. XPeng (G6, G9, P7, X9)
        if (full.contains("xpeng") || full.contains("xiaopeng") || full.contains("xmart") ||
            checkAnyPackage(context, "com.xiaopeng.car", "com.xiaopeng.xmart")) {
            return VehicleBrand.XPENG
        }

        // 11. GAC Group (Trumpchi, AION, Hyper)
        if (full.contains("gac") || full.contains("trumpchi") || full.contains("aion") || full.contains("adigo") ||
            checkAnyPackage(context, "com.gac.car", "com.aion.carsettings", "com.gac.adigo")) {
            return VehicleBrand.GAC
        }

        // 12. Voyah & Dongfeng (Forthing, M-Hero)
        if (full.contains("voyah") || full.contains("dongfeng") || full.contains("forthing") || full.contains("mengshi") ||
            checkAnyPackage(context, "com.voyah.car", "com.dongfeng.car", "com.forthing.car")) {
            return VehicleBrand.VOYAH
        }

        // 13. Hongqi (FAW)
        if (full.contains("hongqi") || full.contains("faw") ||
            checkAnyPackage(context, "com.faw.hongqi", "com.hongqi.carsettings")) {
            return VehicleBrand.HONGQI
        }

        // 14. BAIC (BJ40, BJ60, Arcfox)
        if (full.contains("baic") || full.contains("beijing") || full.contains("arcfox") ||
            checkAnyPackage(context, "com.baic.car", "com.arcfox.carsettings")) {
            return VehicleBrand.BAIC
        }

        // 15. SAIC MG & Roewe (Maxus, IM Motors)
        if (full.contains("saic") || full.contains("mg") || full.contains("roewe") || full.contains("maxus") || full.contains("immotors") ||
            checkAnyPackage(context, "com.saic.car", "com.mg.carsettings", "com.roewe.car")) {
            return VehicleBrand.MG_SAIC
        }

        // 16. Leapmotor (C10, C11, C16)
        if (full.contains("leapmotor") ||
            checkAnyPackage(context, "com.leapmotor.car", "com.leapmotor.carsettings")) {
            return VehicleBrand.LEAPMOTOR
        }

        // 17. Ekran NisbTti vT Rezolyusiya ilT DTqiq TTyin
        try {
            val metrics: DisplayMetrics = context.resources.displayMetrics
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val ratio = if (h > 0) w.toFloat() / h.toFloat() else 1.77f

            // 21:9 Ultra-Geniq Sunflower Ekran (Changan Deepal)
            if (ratio > 2.05f) {
                return VehicleBrand.CHANGAN
            }

            // 3K Flagship Ekran (Li Auto)
            if (w >= 2560 || h >= 1440) {
                return VehicleBrand.LI_AUTO
            }
        } catch (_: Throwable) {}

        return VehicleBrand.UNIVERSAL
    }

    private fun checkAnyPackage(context: Context, vararg packages: String): Boolean {
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (_: Throwable) {}
        }
        return false
    }
}
