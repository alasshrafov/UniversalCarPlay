package com.universal.carplay.car

import android.content.Context
import android.content.res.Configuration

/**
 * Gecə və Gündüz rejiminin avtomatik təyini (Dark / Light Mode)
 */
class DayNightManager(private val context: Context) {

    enum class AppearanceMode {
        LIGHT,
        DARK
    }

    /**
     * Cari sistem rejiminə uyğun görünüş rejimini qaytarır
     */
    fun getCurrentAppearance(): AppearanceMode {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return when (currentNightMode) {
            Configuration.UI_MODE_NIGHT_YES -> AppearanceMode.DARK
            Configuration.UI_MODE_NIGHT_NO -> AppearanceMode.LIGHT
            else -> AppearanceMode.DARK // Avtomobillərdə adətən tünd rejim üstünlük təşkil edir
        }
    }
}
