package com.universal.carplay.car

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Avtomobil Kondisioner və İqlim Sistemlərini (HVAC / CAN-Bus) İdarə Edən Menecer
 * 
 * Dəstəklənən Sistemlər:
 * 1. BYD DiLink AC CAN-Bus API
 * 2. Changan / Deepal Avtomobil İqlim Xidməti
 * 3. Android Automotive OS (AAOS) CarHvacManager
 * 4. Universal Çin Avtomobil Ekranları üçün Broadcast Şəbəkəsi
 */
class VehicleHvacManager(
    private val context: Context,
    private val onClimateUpdated: (ClimateState) -> Unit
) {
    companion object {
        private const val TAG = "CarTwin_HVAC"
    }

    data class ClimateState(
        var targetTemp: Float = 22.0f,
        var isAcOn: Boolean = true,
        var isAuto: Boolean = true,
        var fanSpeed: Int = 3, // 1 - 7
        var statusMessage: String = "Normal"
    )

    private val currentState = ClimateState()

    fun getClimateState(): ClimateState = currentState

    /**
     * Səsli əmrlə və ya ekrandan temperaturu təyin edir
     */
    fun setTemperature(tempCelsius: Float) {
        val clamped = tempCelsius.coerceIn(16.0f, 32.0f)
        currentState.targetTemp = clamped
        currentState.isAcOn = true
        currentState.statusMessage = "${clamped.toInt()}°C Təyin Edildi"

        Log.i(TAG, "Kondisioner temperaturu dəyişdirildi: ${clamped}°C")

        // 1. Android Automotive & CAN-Bus inteqrasiya siqnalları göndəririk
        broadcastHvacChange("TEMP", clamped)

        // 2. UI-ı xəbərdar edirik
        onClimateUpdated(currentState)
    }

    /**
     * Kondisioneri yandırır və ya söndürür
     */
    fun setAcPower(enabled: Boolean) {
        currentState.isAcOn = enabled
        currentState.statusMessage = if (enabled) "Kondisioner Açıldı" else "Kondisioner Söndürüldü"

        broadcastHvacChange("POWER", if (enabled) 1.0f else 0.0f)
        onClimateUpdated(currentState)
    }

    /**
     * Fan sürətini dəyişir (1-7)
     */
    fun setFanSpeed(speed: Int) {
        currentState.fanSpeed = speed.coerceIn(1, 7)
        currentState.statusMessage = "Fan Sürəti: $speed"

        broadcastHvacChange("FAN", speed.toFloat())
        onClimateUpdated(currentState)
    }

    private fun broadcastHvacChange(property: String, value: Float) {
        // BYD, Changan və Universal avtomobil modulları üçün sistem intentləri
        val intentList = listOf(
            Intent("com.byd.intent.action.AC_COMMAND").apply {
                putExtra("command", property)
                putExtra("value", value)
            },
            Intent("com.changan.car.hvac.SET").apply {
                putExtra("param", property)
                putExtra("val", value)
            },
            Intent("android.car.hardware.hvac.SET_PROPERTY").apply {
                putExtra("property", property)
                putExtra("value", value)
            }
        )

        for (intent in intentList) {
            try {
                context.sendBroadcast(intent)
            } catch (ignored: Exception) {}
        }
    }
}
