package com.universal.carplay.engine

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Android MediaCodec üzərində qurulmuş aşağı gecikməli (low-latency) H.264 video dekoderi
 */
class VideoDecoder(private val surface: Surface) {

    private val TAG = "CarPlay_VideoDecoder"
    private var codec: MediaCodec? = null
    private var isRunning = false

    var currentWidth = 1920
        private set
    var currentHeight = 1080
        private set

    /**
     * Dekoderi müəyyən təsvir ölçüsü ilə işə salır
     */
    fun start(width: Int, height: Int, mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC) {
        if (isRunning) stop()

        try {
            currentWidth = width
            currentHeight = height

            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                // CarPlay üçün aşağı gecikmə (Low Latency) rejimi aktivləşdirilir
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                setInteger(MediaFormat.KEY_PRIORITY, 0) // Ən yüksək prioritet
            }

            codec = MediaCodec.createDecoderByType(mimeType).apply {
                configure(format, surface, null, 0)
                start()
            }

            isRunning = true
            Log.i(TAG, "Video dekoder işə düşdü: ${width}x${height}, format=$mimeType")
        } catch (e: Exception) {
            Log.e(TAG, "Dekoder başlarkən xəta: ${e.message}", e)
        }
    }

    /**
     * iPhone və ya Mock Serverdən gələn H.264 NAL kadrını birbaşa dekoderə ötürür
     */
    fun feedFrame(data: ByteArray, offset: Int, length: Int, presentationTimeUs: Long) {
        if (!isRunning || codec == null) return

        try {
            val inputIndex = codec!!.dequeueInputBuffer(10_000L) // 10ms timeout
            if (inputIndex >= 0) {
                val inputBuffer: ByteBuffer? = codec!!.getInputBuffer(inputIndex)
                inputBuffer?.let {
                    it.clear()
                    it.put(data, offset, length)
                    codec!!.queueInputBuffer(
                        inputIndex,
                        0,
                        length,
                        presentationTimeUs,
                        0
                    )
                }
            }

            // Dekod olunmuş kadrı dərhal ekrana (Surface) çıxarırıq
            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec!!.dequeueOutputBuffer(bufferInfo, 0L)
            while (outputIndex >= 0) {
                // render = true verildikdə kadr birbaşa SurfaceView-də göstərilir
                codec!!.releaseOutputBuffer(outputIndex, true)
                outputIndex = codec!!.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kadr emal edilərkən xəta: ${e.message}")
        }
    }

    /**
     * Dekoderi dayandırır və resursları azad edir
     */
    fun stop() {
        isRunning = false
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Dekoder dayanarkən xəta: ${e.message}")
        } finally {
            codec = null
        }
        Log.i(TAG, "Video dekoder dayandırıldı")
    }
}
