package com.universal.carplay.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Avtomobilin vəziyyətini (Arxa kamera, Parktronik, Audio Fokus) izləyən və idarə edən sistem
 */
class VehicleStateManager(
    private val context: Context,
    private val onAudioDuckingChanged: (shouldDuck: Boolean) -> Unit,
    private val onReverseCameraStateChanged: (isInReverse: Boolean) -> Unit
) {
    private val TAG = "VehicleStateManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null

    // Çin və universal Android monitorlarında arxa kameraya keçid xəbərdarlıqları
    private val reverseGearReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Sistem xəbərdarlığı alındı: $action")

            if (action.contains("REVERSE", ignoreCase = true) || action.contains("CAMERA", ignoreCase = true)) {
                val isReverse = intent.getBooleanExtra("state", false)
                onReverseCameraStateChanged(isReverse)
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Parktronik səsi və ya naviqasiya bildirişi zamanı səsi azaldırıq
                Log.i(TAG, "Audio Fokus: Səs azaldılır (Ducking aktivdir)")
                onAudioDuckingChanged(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Səsi əvvəlki səviyyəsinə bərpa edirik
                Log.i(TAG, "Audio Fokus: Tam səs bərpa olundu")
                onAudioDuckingChanged(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Müvəqqəti səs dayandırılır (məsələn, zəng gələndə), lakin CarPlay sessiyası qorunur
                Log.i(TAG, "Audio Fokus: Müvəqqəti pauza")
                onAudioDuckingChanged(true)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i(TAG, "Audio Fokus tam itirildi")
            }
        }
    }

    fun register() {
        // Audio Fokus tələbi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        // Kamera və arxa ötürücü Intent-ləri qeydiyyatdan keçiririk
        val filter = IntentFilter().apply {
            addAction("android.intent.action.CAMERA_BUTTON")
            addAction("com.android.internal.action.REVERSE_GEAR")
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                reverseGearReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (ignored: Exception) {}
    }

    fun unregister() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            context.unregisterReceiver(reverseGearReceiver)
        } catch (ignored: Exception) {}
    }
}
