package com.universal.carplay.car

import android.view.KeyEvent
import org.json.JSONObject

/**
 * Avtomobilin sükanındakı fiziki düymələri (Steering Wheel Controls) idarə edən menecer
 * 
 * Android monitorlarının böyük əksəriyyəti (BYD, Changan, Li Auto və aftermarket monitorlar)
 * sükandakı düymələri standart Android KeyEvent kodları kimi ötürür.
 */
class SteeringWheelManager(
    private val onControlAction: (commandJson: String) -> Unit
) {

    /**
     * Klaviş basılmasını analiz edir və CarPlay idarəetmə əmrinə çevirir
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val command = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> "NEXT_TRACK"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "PREV_TRACK"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "TOGGLE_PLAY"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "PLAY"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "PAUSE"
            KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH -> "TRIGGER_SIRI"
            KeyEvent.KEYCODE_CALL -> "ANSWER_CALL"
            KeyEvent.KEYCODE_ENDCALL -> "HANGUP_CALL"
            KeyEvent.KEYCODE_VOLUME_UP -> "VOLUME_UP"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "VOLUME_DOWN"
            KeyEvent.KEYCODE_VOLUME_MUTE -> "MUTE"
            else -> return false // Bizim maraqlandığımız düymə deyil
        }

        val json = JSONObject().apply {
            put("type", "STEERING_WHEEL")
            put("command", command)
            put("keyCode", keyCode)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        onControlAction(json)
        return true
    }
}
