package com.universal.carplay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.universal.carplay.MainActivity

/**
 * Avtomobil İşə Düşəndə (İqnişn / Xodlanma) CarTwin-i Avtomatik Başladan Qəbuledici
 * 
 * Dəstəklənən Oyanış Siqnalları:
 * 1. android.intent.action.BOOT_COMPLETED (Soyuq sistem açılışı)
 * 2. android.intent.action.QUICKBOOT_POWERON (Snapdragon / MTK avtomobil ekranlarının tez oyanması)
 * 3. com.htc.intent.action.QUICKBOOT_POWERON (BYD DiLink, Changan və Geely ekranlarının yuxudan oyanması)
 * 4. android.intent.action.ACTION_POWER_CONNECTED (Avtomobil generatoru işə düşdükdə enerji verilişi)
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CarTwin_BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Avtomobil oyanış siqnalı qəbul edildi: $action")

        val validBootActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT",
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (action in validBootActions) {
            Log.i(TAG, "Avtomobil işə düşdü. CarTwin avtomatik olaraq ön plana gətirilir...")

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("EXTRA_AUTO_BOOT_LAUNCH", true)
                putExtra("EXTRA_TRIGGER_ACTION", action)
            }

            try {
                context.startActivity(launchIntent)
                Log.i(TAG, "MainActivity uğurla başladıldı.")
            } catch (e: Exception) {
                Log.e(TAG, "CarTwin avtomatik işə düşərkən xəta: ${e.message}", e)
            }
        }
    }
}
