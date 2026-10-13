package com.universal.carplay.profiles

import com.universal.carplay.R

/**
 * Hər bir avtomobil markası üçün ekran və sistem konfiqurasiyası
 */
sealed class VehicleProfile(
    val id: String,
    val name: String,
    val commercialTitle: String,
    val commercialTagline: String,
    val logoResId: Int,
    val accentColorHex: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val targetFps: Int,
    val targetDpi: Int,
    val supportsRotatingScreen: Boolean = false,
    val supportsHudNavigation: Boolean = false
) {
    /**
     * Standart Universal Rejim (CarTwin Premium Connect - Bütün Android monitorlar üçün)
     */
    object Universal : VehicleProfile(
        id = "universal",
        name = "CarTwin Universal",
        commercialTitle = "CARTWIN",
        commercialTagline = "PREMIUM CONNECT • WIRELESS CARPLAY",
        logoResId = R.drawable.cartwin_logo,
        accentColorHex = "#E5B869",
        defaultWidth = 1920,
        defaultHeight = 1080,
        targetFps = 60,
        targetDpi = 160
    )

    /**
     * BYD DiLink Profili (15.6" fırlanan ekran və HUD inteqrasiyası)
     */
    object BYD : VehicleProfile(
        id = "byd",
        name = "BYD DiLink (Fırlanan Ekran)",
        commercialTitle = "BUILD YOUR DREAMS",
        commercialTagline = "BYD DILINK 5.0 • ULTRA SMART COCKPIT",
        logoResId = R.drawable.ic_logo_byd,
        accentColorHex = "#00F0FF",
        defaultWidth = 1920,
        defaultHeight = 1080,
        targetFps = 60,
        targetDpi = 240,
        supportsRotatingScreen = true,
        supportsHudNavigation = true
    )

    /**
     * Changan / Deepal / Avatr Profili (Geniş sunflower ekran)
     */
    object Changan : VehicleProfile(
        id = "changan",
        name = "Changan / Deepal",
        commercialTitle = "CHANGAN AUTOMOBILE",
        commercialTagline = "DEEPAL INTELLIGENCE • SUNFLOWER SCREEN",
        logoResId = R.drawable.ic_logo_changan,
        accentColorHex = "#2F80ED",
        defaultWidth = 1920,
        defaultHeight = 720,
        targetFps = 60,
        targetDpi = 180
    )

    /**
     * Li Auto (Li Xiang L7/L8/L9) Profili (15.7" 3K yüksək dəqiqlikli ekran)
     */
    object LiAuto : VehicleProfile(
        id = "liauto",
        name = "Li Auto (3K Ekran)",
        commercialTitle = "LEADING IDEAL",
        commercialTagline = "SMART LUXURY FLAGSHIP • 3K SCREEN CARPLAY",
        logoResId = R.drawable.ic_logo_liauto,
        accentColorHex = "#FFD60A",
        defaultWidth = 2880,
        defaultHeight = 1620,
        targetFps = 60,
        targetDpi = 320
    )

    /**
     * Zeekr / Geely Profili (Snapdragon 8295 çipi və 60 FPS axıcılıq)
     */
    object Zeekr : VehicleProfile(
        id = "zeekr",
        name = "Zeekr OS (001 / 007)",
        commercialTitle = "ZEEKR PERFORMANCE",
        commercialTagline = "HIGH-PERFORMANCE ARCHITECTURE • 60 FPS CARPLAY",
        logoResId = R.drawable.ic_logo_zeekr,
        accentColorHex = "#30D158",
        defaultWidth = 1920,
        defaultHeight = 1080,
        targetFps = 60,
        targetDpi = 220
    )

    companion object {
        val ALL = listOf(Universal, BYD, Changan, LiAuto, Zeekr)

        fun fromId(id: String): VehicleProfile {
            return ALL.find { it.id == id } ?: Universal
        }
    }
}
