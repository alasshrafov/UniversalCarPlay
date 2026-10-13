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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var isPlaying = false

    /**
     * Səs kanallarını başladır
     */
    fun start() {
        if (isPlaying) return

        try {
            // 1. Musiqi Kanalı (48kHz Stereo 16-bit PCM)
            val musicBufferSize = AudioTrack.getMinBufferSize(
                48000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val musicAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val musicFormat = AudioFormat.Builder()
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            musicTrack = AudioTrack.Builder()
                .setAudioAttributes(musicAttributes)
                .setAudioFormat(musicFormat)
                .setBufferSizeInBytes(musicBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // 2. Danışıq / Siri / Naviqasiya Kanalı (16kHz Mono 16-bit PCM)
            val speechBufferSize = AudioTrack.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val speechAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val speechFormat = AudioFormat.Builder()
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            speechTrack = AudioTrack.Builder()
                .setAudioAttributes(speechAttributes)
                .setAudioFormat(speechFormat)
                .setBufferSizeInBytes(speechBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            musicTrack?.play()
            speechTrack?.play()
            isPlaying = true

            Log.i(TAG, "Audio Engine aktivləşdirildi (Musiqi + Danışıq kanalları)")
        } catch (e: Exception) {
            Log.e(TAG, "Audio kanalları başlarkən xəta: ${e.message}", e)
        }
    }

    /**
     * Musiqi paketlərini oxudur
     */
    fun writeMusic(data: ByteArray, offset: Int, size: Int) {
        if (!isPlaying || musicTrack == null) return
        musicTrack?.write(data, offset, size)
    }

    /**
     * Siri, Zəng və ya Naviqasiya səsini oxudur
     */
    fun writeSpeech(data: ByteArray, offset: Int, size: Int) {
        if (!isPlaying || speechTrack == null) return
        speechTrack?.write(data, offset, size)
    }

    /**
     * Parktronik və ya arxa kamera qoşulduqda səsin bir qədər azaldılması (Ducking)
     */
    fun setVolumeDucking(duck: Boolean) {
        val volume = if (duck) 0.3f else 1.0f
        musicTrack?.setVolume(volume)
    }

    /**
     * Səs mühərrikini dayandırır
     */
    fun stop() {
        isPlaying = false
        try {
            musicTrack?.stop()
            musicTrack?.release()
            speechTrack?.stop()
            speechTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Audio dayandırılarkən xəta: ${e.message}")
        } finally {
            musicTrack = null
            speechTrack = null
        }
    }
}
