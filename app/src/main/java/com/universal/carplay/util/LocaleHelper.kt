package com.universal.carplay.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    const val PREF_LANGUAGE = "app_language"
    const val LANG_EN = "en"
    const val LANG_AZ = "az"
    const val LANG_TR = "tr"
    const val LANG_RU = "ru"

    val SUPPORTED_LANGUAGES = listOf(LANG_EN, LANG_AZ, LANG_TR, LANG_RU)

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("cartwin_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, LANG_EN) ?: LANG_EN
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        return updateResources(context, language)
    }

    private fun persist(context: Context, language: String) {
        val prefs = context.getSharedPreferences("cartwin_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, language).apply()
    }

    fun getLocale(language: String): Locale {
        return when (language) {
            LANG_AZ -> Locale("az")
            LANG_TR -> Locale("tr")
            LANG_RU -> Locale("ru")
            else -> Locale("en")
        }
    }

    fun getLanguageDisplayName(langCode: String): String {
        return when (langCode) {
            LANG_AZ -> "🇦🇿 Azərbaycan dili"
            LANG_TR -> "🇹🇷 Türkçe"
            LANG_RU -> "🇷🇺 Русский"
            else -> "🇬🇧 English (Default)"
        }
    }

    fun getLanguageShortBadge(langCode: String): String {
        return when (langCode) {
            LANG_AZ -> "🌐 AZ"
            LANG_TR -> "🌐 TR"
            LANG_RU -> "🌐 RU"
            else -> "🌐 EN"
        }
    }

    fun updateResources(context: Context, language: String): Context {
        val locale = getLocale(language)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            return context
        }
    }
}
