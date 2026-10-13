package com.universal.carplay.engine

import android.view.MotionEvent
import org.json.JSONObject

/**
 * Android toxunma jestlərini (Touch events) CarPlay HID formatına və ya şəbəkə paketlərinə çevirir
 */
class TouchManager(
    private var screenWidth: Int,
    private var screenHeight: Int,
    private var targetWidth: Int,
    private var targetHeight: Int
) {

    fun updateDimensions(screenWidth: Int, screenHeight: Int, targetWidth: Int, targetHeight: Int) {
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        this.targetWidth = targetWidth
        this.targetHeight = targetHeight
    }

    /**
     * MotionEvent hadisəsini hədəf koordinatlara uyğunlaşdırır və JSON / bayt paketinə çevirir
     */
    fun processTouchEvent(event: MotionEvent): String? {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> return null
        }

        // Koordinatların hədəf ekrana miqyaslanması
        val normX = (event.x / screenWidth.toFloat()).coerceIn(0f, 1f)
        val normY = (event.y / screenHeight.toFloat()).coerceIn(0f, 1f)

        val targetX = (normX * targetWidth).toInt()
        val targetY = (normY * targetHeight).toInt()

        return JSONObject().apply {
            put("type", "TOUCH")
            put("action", action)
            put("x", targetX)
            put("y", targetY)
            put("normX", normX)
            put("normY", normY)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }
}
