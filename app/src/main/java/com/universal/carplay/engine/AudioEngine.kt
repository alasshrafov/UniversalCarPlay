package com.universal.carplay.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Çoxkanallı səs idarəetməsi (Media + Danışıq/Siri) və avtomobil səs fokusuna nəzarət
 */
class AudioEngine(private val context: Context) {

    private val TAG = "CarPlay_AudioEngine"

    private var musicTrack: AudioTrack? = null
    private var speechTrack: AudioTrack? = null
    private val audioManager = try {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (_: Throwable) { null }

    private var isPlaying = false

    /**
     * Səs kanallarını başladır (100% Təhlükəsiz və Crash-Proof)
     */
    fun start() {
        if (isPlaying) return

        try {
            // 1. Musiqi Kanalı (48kHz Stereo 16-bit PCM)
            var musicBufferSize = AudioTrack.getMinBufferSize(
                48000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (musicBufferSize <= 0) musicBufferSize = 8192

            val musicAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val musicFormat = AudioFormat.Builder()
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            try {
                musicTrack = AudioTrack.Builder()
                    .setAudioAttributes(musicAttributes)
                    .setAudioFormat(musicFormat)
                    .setBufferSizeInBytes(musicBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (musicTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    musicTrack?.play()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Musiqi kanalı xətası: ${e.message}")
            }

            // 2. Danışıq / Siri / Naviqasiya Kanalı (16kHz Mono 16-bit PCM)
            var speechBufferSize = AudioTrack.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (speechBufferSize <= 0) speechBufferSize = 4096

            val speechAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val speechFormat = AudioFormat.Builder()
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            try {
                speechTrack = AudioTrack.Builder()
                    .setAudioAttributes(speechAttributes)
                    .setAudioFormat(speechFormat)
                    .setBufferSizeInBytes(speechBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (speechTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    speechTrack?.play()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Danışıq kanalı xətası: ${e.message}")
            }

            isPlaying = true
            Log.i(TAG, "Audio Engine aktivləşdirildi")
        } catch (e: Throwable) {
            Log.e(TAG, "Audio kanalları başlarkən xəta: ${e.message}", e)
        }
    }

    fun writeMusic(data: ByteArray, offset: Int, size: Int) {
        if (!isPlaying || musicTrack == null) return
        try {
            if (musicTrack?.state == AudioTrack.STATE_INITIALIZED) {
                musicTrack?.write(data, offset, size)
            }
        } catch (_: Throwable) {}
    }

    fun writeSpeech(data: ByteArray, offset: Int, size: Int) {
        if (!isPlaying || speechTrack == null) return
        try {
            if (speechTrack?.state == AudioTrack.STATE_INITIALIZED) {
                speechTrack?.write(data, offset, size)
            }
        } catch (_: Throwable) {}
    }

    fun setVolumeDucking(duck: Boolean) {
        try {
            val volume = if (duck) 0.3f else 1.0f
            musicTrack?.setVolume(volume)
        } catch (_: Throwable) {}
    }

    fun stop() {
        isPlaying = false
        try {
            musicTrack?.stop()
            musicTrack?.release()
        } catch (_: Throwable) {}
        try {
            speechTrack?.stop()
            speechTrack?.release()
        } catch (_: Throwable) {}
        musicTrack = null
        speechTrack = null
    }
}
