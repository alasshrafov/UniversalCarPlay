package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log

/**
 * Avtomobilin brendini (BYD, Changan, Geely, Chery, Li Auto, Zeekr, Haval vT s.)
 * 100% avtomatik tTyin edTn AAll Detektor
 */
object VehicleDetector {
    private const val TAG = "CarTwin_Vehicle"
    private const val PREFS = "cartwin_vehicle"
    private const val KEY_BRAND = "selected_brand"

    fun getActiveBrand(context: Context): VehicleBrand {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BRAND, null)
        if (saved != null) {
            val brand = VehicleBrand.fromId(saved)
            Log.i(TAG, "Yadda saxlanm avtomobil profili aktivlTdirildi: ${brand.brandName}")
            return brand
        }

        val detected = autoDetect(context)
        Log.i(TAG, "Avtomatik tTyin edilTn avtomobil brendi: ${detected.brandName}")
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

        Log.i(TAG, "Aparat parametrlTri tThlil edilir: $full")

        // 1. BYD Analizi
        if (full.contains("byd") || full.contains("dilink") || checkPackage(context, "com.byd.carsettings") || checkPackage(context, "com.byd.service")) {
            return VehicleBrand.BYD
        }

        // 2. Changan / Deepal / Avatr Analizi
        if (full.contains("changan") || full.contains("deepal") || full.contains("avatr") || full.contains("incall") || checkPackage(context, "com.changan.incall")) {
            return VehicleBrand.CHANGAN
        }

        // 3. Geely / Lynk & Co Analizi
        if (full.contains("geely") || full.contains("ecarx") || full.contains("flyme") || checkPackage(context, "com.geely.inputservice")) {
            return VehicleBrand.GEELY
        }

        // 4. Chery / Exeed / Omoda / Jaecoo Analizi
        if (full.contains("chery") || full.contains("exeed") || full.contains("omoda") || full.contains("jaecoo") || checkPackage(context, "com.chery.automap")) {
            return VehicleBrand.CHERY
        }

        // 5. Li Auto (Li Xiang) Analizi
        if (full.contains("liauto") || full.contains("chj") || full.contains("ideal") || checkPackage(context, "com.liauto.car")) {
            return VehicleBrand.LI_AUTO
        }

        // 6. Zeekr Analizi
        if (full.contains("zeekr") || checkPackage(context, "com.zeekrlife.car")) {
            return VehicleBrand.ZEEKR
        }

        // 7. Haval / Great Wall / Tank / Wey Analizi
        if (full.contains("haval") || full.contains("gwm") || full.contains("greatwall") || full.contains("tank") || full.contains("wey") || checkPackage(context, "com.greatwall.hvac")) {
            return VehicleBrand.HAVAL
        }

        // 8. Ekran nisbTti analizi (Aspect Ratio & Resolution)
        try {
            val metrics: DisplayMetrics = context.resources.displayMetrics
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val ratio = if (h > 0) w.toFloat() / h.toFloat() else 1.77f

            // 21:9 ultra-geni ekran (Changan Deepal)
            if (ratio > 2.05f) {
                return VehicleBrand.CHANGAN
            }

            // 3K 2880x1620 yAksTk dTqiqlikli ekran (Li Auto)
            if (w >= 2560 || h >= 1440) {
                return VehicleBrand.LI_AUTO
            }
        } catch (_: Throwable) {}

        return VehicleBrand.UNIVERSAL
    }

    private fun checkPackage(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
