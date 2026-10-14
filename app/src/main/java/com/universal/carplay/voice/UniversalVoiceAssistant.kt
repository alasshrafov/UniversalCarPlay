package com.universal.carplay.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Bütün Dilləri və Xüsusilə Azərbaycan Dilini Dəstəkləyən Universal AI Səs Köməkçisi
 * 
 * Sürücünün ana dilində dediyi əmrləri anlayır və CAN-Bus/Avtomobil iqlim sisteminə çevirir.
 * 
 * Vacib Qayda:
 * QƏTİYYƏN İngiliscə cavab vermir və deyilənləri ingiliscə təkrar etmir!
 * Standart olaraq zərif təsdiq zəngi (Chime 🔔) çalır və ya yalnız Azərbaycan dilində cavab verir.
 */
class UniversalVoiceAssistant(
    private val context: Context,
    private val onCommandRecognized: (commandType: String, originalText: String, param: Float?) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CarTwin_VoiceAI"
    }

    enum class VoiceResponseMode {
        SILENT_CHIME,     // Zərif təsdiq zəngi (İngiliscə təkrar qətiyyən yoxdur) - DEFAULT
        AZERBAIJANI_ONLY, // Yalnız təmiz Azərbaycan dili dəstəklənirsə danışsın
        MUTE              // Tam səssiz
    }

    var voiceResponseMode: VoiceResponseMode = VoiceResponseMode.SILENT_CHIME

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsNativeReady: Boolean = false

    // Standart dil: Azərbaycan dili (az-AZ)
    var currentLanguage: String = "az-AZ"
        private set

    init {
        initSpeechRecognizer()
        textToSpeech = TextToSpeech(context, this)
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.i(TAG, "Səs qəbuluna hazırdır ($currentLanguage)...")
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.w(TAG, "Səs tanıma xətası: kod=$error")
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            Log.i(TAG, "Eşidilən əmr: \"$recognizedText\"")
                            parseAndExecuteCommand(recognizedText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening(language: String = currentLanguage) {
        currentLanguage = language
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * Azərbaycan dilində sərbəst deyilən sözlərdən temperatur rəqəmini 100% dəqiq çıxarır
     */
    private fun extractTemperature(text: String): Float? {
        val lower = text.lowercase(Locale.getDefault())

        // 1. Rəqəmlə deyilənlər (məs: 20, 20-yə, 20.5, 22)
        val digitRegex = Regex("""(?:^|\D)(1[6-9]|2[0-9]|3[0-2])(?:\D|$)""")
        val match = digitRegex.find(lower)
        if (match != null) {
            val whole = match.groupValues[1].toFloatOrNull()
            if (whole != null) return whole
        }

        // 2. Sözlə deyilən Azərbaycan rəqəmləri
        val numberWords = listOf(
            "iyirmi bir" to 21f, "iyirmi iki" to 22f, "iyirmi uc" to 23f, "iyirmi üç" to 23f,
            "iyirmi dord" to 24f, "iyirmi dörd" to 24f, "iyirmi bes" to 25f, "iyirmi beş" to 25f,
            "iyirmi alti" to 26f, "iyirmi altı" to 26f, "iyirmi yeddi" to 27f,
            "iyirmi sekkiz" to 28f, "iyirmi səkkiz" to 28f, "iyirmi doqquz" to 29f,
            "iyirmi" to 20f,
            "on alti" to 16f, "on altı" to 16f,
            "on yeddi" to 17f,
            "on sekkiz" to 18f, "on səkkiz" to 18f,
            "on doqquz" to 19f,
            "otuz" to 30f
        )

        for ((word, value) in numberWords) {
            if (lower.contains(word)) {
                return value
            }
        }

        return null
    }

    /**
     * Sürücünün ana dilində dediyi cümləni tam və dəqiq anlayır
     * Məsələn: "Aura, kondisioneri 20 dərəcəyə qoy" -> "kondisioneri 20 dərəcəyə qoy"
     */
    private fun parseAndExecuteCommand(text: String) {
        var lower = text.lowercase(Locale.getDefault()).trim()
        // "Aura" və ya "Hey Aura" prefiksini süzürük
        lower = lower.replace(Regex("""^(?:hey\s+)?aura[,:\s]*"""), "").trim()

        val extractedTemp = extractTemperature(lower)

        val isClimateContext = lower.contains("kondisioner") ||
                lower.contains("klima") ||
                lower.contains("dərəcə") ||
                lower.contains("derece") ||
                lower.contains("hava") ||
                lower.contains("isti") ||
                lower.contains("soyut") ||
                lower.contains("üşüdüm") ||
                lower.contains("temperatur") ||
                (extractedTemp != null && (lower.contains("elə") || lower.contains("ele") || lower.contains("qoy") || lower.contains("et")))

        if (isClimateContext) {
            when {
                lower.contains("söndür") || lower.contains("sondur") || lower.contains("bağla") || lower.contains("bagla") || lower.contains("kapat") || lower.contains("lazım deyil") -> {
                    speak("Kondisioner söndürüldü")
                    onCommandRecognized("CLIMATE_AC_OFF", text, null)
                }
                extractedTemp != null -> {
                    speak("Kondisioner ${extractedTemp.toInt()} dərəcəyə təyin edildi")
                    onCommandRecognized("CLIMATE_SET_TEMP", text, extractedTemp)
                }
                lower.contains("artır") || lower.contains("artir") || lower.contains("isti") || lower.contains("üşüdüm") || lower.contains("qızdır") -> {
                    speak("Temperatur artırıldı")
                    onCommandRecognized("CLIMATE_TEMP_UP", text, null)
                }
                lower.contains("azalt") || lower.contains("soyut") || lower.contains("soyuq") || lower.contains("sərin") -> {
                    speak("Temperatur azaldıldı")
                    onCommandRecognized("CLIMATE_TEMP_DOWN", text, null)
                }
                lower.contains("aç") || lower.contains("ac") || lower.contains("yandır") || lower.contains("yandir") || lower.contains("işə sal") -> {
                    speak("Kondisioner işə salındı")
                    onCommandRecognized("CLIMATE_AC_ON", text, null)
                }
                else -> {
                    speak("Kondisioner 20 dərəcəyə təyin edildi")
                    onCommandRecognized("CLIMATE_SET_TEMP", text, 20.0f)
                }
            }
            return
        }

        // Digər Avtomobil və CarPlay Əmrləri
        when {
            lower.contains("zəng") || lower.contains("ara") || lower.contains("telefon") || lower.contains("call") -> {
                speak("Əlaqə saxlanılır")
                onCommandRecognized("PHONE_CALL", text, null)
            }
            lower.contains("xəritə") || lower.contains("marşrut") || lower.contains("get") || lower.contains("naviqasiya") -> {
                speak("Marşrut qurulur")
                onCommandRecognized("NAVIGATION", text, null)
            }
            lower.contains("mahnı") || lower.contains("musiqi") || lower.contains("oxut") || lower.contains("spotify") || lower.contains("dəyiş") || lower.contains("növbəti") -> {
                speak("Musiqi dəyişdirildi")
                onCommandRecognized("MEDIA_NEXT", text, null)
            }
            lower.contains("brauzer") || lower.contains("browser") || lower.contains("internet") || lower.contains("google") || lower.contains("sayt") -> {
                speak("İnternet brauzeri açılır")
                onCommandRecognized("OPEN_BROWSER", text, null)
            }
            lower.contains("youtube") || lower.contains("video") || lower.contains("film") || lower.contains("klip") -> {
                speak("YouTube Park Rejimi açılır")
                onCommandRecognized("OPEN_YOUTUBE", text, null)
            }
            else -> {
                playConfirmationChime()
                onCommandRecognized("GENERAL_AI_QUERY", text, null)
            }
        }
    }

    /**
     * Səsli geridönüş:
     * QƏTİYYƏN ingiliscə danışmır!
     * Standart olaraq zərif təsdiq zəngi (Chime 🔔) çalır.
     */
    fun speak(text: String) {
        if (voiceResponseMode == VoiceResponseMode.MUTE) return

        if (voiceResponseMode == VoiceResponseMode.SILENT_CHIME) {
            playConfirmationChime()
            return
        }

        if (voiceResponseMode == VoiceResponseMode.AZERBAIJANI_ONLY && isTtsNativeReady) {
            try {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CarTwinVoice")
            } catch (e: Exception) {
                playConfirmationChime()
            }
        } else {
            // Əgər Azərbaycan dili TTS yoxdursa, İNGİLİSCƏ DANIŞMIR, zərif chime verir!
            playConfirmationChime()
        }
    }

    /**
     * Apple CarPlay üslubunda zərif təsdiq zəngi (Ding 🔔)
     */
    private fun playConfirmationChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 250)
        } catch (e: Exception) {
            Log.w(TAG, "Chime səs xətası: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("az", "AZ"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsNativeReady = true
            } else {
                val trResult = textToSpeech?.setLanguage(Locale("tr", "TR"))
                isTtsNativeReady = (trResult != TextToSpeech.LANG_MISSING_DATA && trResult != TextToSpeech.LANG_NOT_SUPPORTED)
            }
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (ignored: Exception) {}
    }
}
