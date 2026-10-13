package com.universal.carplay.profiles

/**
 * Hər bir avtomobil markası üçün ekran və sistem konfiqurasiyası
 */
sealed class VehicleProfile(
    val id: String,
    val name: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val targetFps: Int,
    val targetDpi: Int,
    val supportsRotatingScreen: Boolean = false,
    val supportsHudNavigation: Boolean = false
) {
    /**
     * Standart Universal Rejim (Bütün Android monitorlar üçün)
     */
    object Universal : VehicleProfile(
        id = "universal",
        name = "Universal Android",
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
        defaultWidth = 2880,
        defaultHeight = 1620,
        targetFps = 60,
        targetDpi = 320
    )

    companion object {
        val ALL = listOf(Universal, BYD, Changan, LiAuto)

        fun fromId(id: String): VehicleProfile {
            return ALL.find { it.id == id } ?: Universal
        }
    }
}
