package com.universal.carplay.car

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import com.universal.carplay.profiles.VehicleProfile

/**
 * Avtomobilin brendini, modelini və ekran xüsusiyyətlərini 100% avtomatik təyin edən Ağıllı Detektor
 */
class VehicleDetector(private val context: Context) {

    private val TAG = "VehicleDetector"
    private val PREFS_NAME = "carplay_vehicle_prefs"
    private val KEY_SAVED_PROFILE = "saved_profile_id"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Avtomobil profilini təyin edir (Əvvəlcə yaddaşdan, yoxdursa avtomatik aparat analizindən)
     */
    fun detectProfile(): VehicleProfile {
        // 1. Əgər istifadəçi əvvəllər əl ilə seçim edibsə, onu qaytarırıq
        val savedId = prefs.getString(KEY_SAVED_PROFILE, null)
        if (savedId != null) {
            val savedProfile = VehicleProfile.fromId(savedId)
            Log.i(TAG, "Yadda saxlanılmış profil aktivləşdirildi: ${savedProfile.name}")
            return savedProfile
        }

        // 2. Avtomatik Aparat və Sistem Analizi
        val detected = autoDetectHardware()
        Log.i(TAG, "Aparat analizi nəticəsində avtomatik təyin olundu: ${detected.name}")

        // Yadda saxlayırıq ki, növbəti dəfə təkrar axtarışa vaxt getməsin
        saveProfile(detected)
        return detected
    }

    /**
     * Android sistem məlumatlarına (Manufacturer, Model, Brand) və ekrana baxaraq avtomobili tanıyır
     */
    private fun autoDetectHardware(): VehicleProfile {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()

        val fullInfo = "$manufacturer $brand $model $device"
        Log.d(TAG, "Cihaz sistem məlumatları: $fullInfo")

        // A. BYD Analizi
        if (fullInfo.contains("byd") || fullInfo.contains("dilink")) {
            return VehicleProfile.BYD
        }

        // B. Changan / Deepal / Avatr Analizi
        if (fullInfo.contains("changan") || fullInfo.contains("deepal") || fullInfo.contains("avatr") || fullInfo.contains("shenlan")) {
            return VehicleProfile.Changan
        }

        // C. Li Auto (Li Xiang) Analizi
        if (fullInfo.contains("liauto") || fullInfo.contains("chj") || fullInfo.contains("ideal")) {
            return VehicleProfile.LiAuto
        }

        // D. Zeekr / Geely Analizi
        if (fullInfo.contains("zeekr") || fullInfo.contains("geely") || fullInfo.contains("ecarx")) {
            return VehicleProfile.Zeekr
        }

        // D. Ekran Ölçüsü və Nisbəti ilə Təyin Etmə
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1.77f

        Log.d(TAG, "Ekran: ${width}x${height}, Nisbət: $aspectRatio")

        // Əgər ultra-geniş 21:9 ekran nisbətidirsə -> Changan / Deepal profili
        if (aspectRatio > 2.1f) {
            return VehicleProfile.Changan
        }

        // Əgər 3K və ya 2560+ piksel yüksək dəqiqlikli ekrandırsa -> Li Auto profili
        if (width >= 2560 || height >= 1440) {
            return VehicleProfile.LiAuto
        }

        // Digər bütün hallar üçün -> Standart Universal Rejim
        return VehicleProfile.Universal
    }

    /**
     * Seçilmiş profili yaddaşa yazır
     */
    fun saveProfile(profile: VehicleProfile) {
        prefs.edit().putString(KEY_SAVED_PROFILE, profile.id).apply()
        Log.i(TAG, "Profil yaddaşa yazıldı: ${profile.name} (${profile.id})")
    }

    /**
     * Yaddaşı sıfırlayır (yenidən avtomatik təyin üçün)
     */
    fun resetSavedProfile() {
        prefs.edit().remove(KEY_SAVED_PROFILE).apply()
    }
}
