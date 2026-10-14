package com.universal.carplay.voice

import android.content.Context
import android.content.Intent
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
 * Sürücünün ana dilində dediyi əmrləri anlayır və CAN-Bus/Avtomobil iqlim sisteminə çevirir:
 * Məsələn:
 * - "Kondisioneri 20 dərəcəyə qoy" -> 20°C təyin edilir
 * - "Kondisioneri söndür" / "Kondisioneri aç"
 * - "Havanı soyut" / "Temperaturu artır"
 * - "Musiqini dəyiş", "Brauzeri aç"
 */
class UniversalVoiceAssistant(
    private val context: Context,
    private val onCommandRecognized: (commandType: String, originalText: String, param: Float?) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CarTwin_VoiceAI"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

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
     * Azərbaycan dilində deyilən sözlərdən temperatur rəqəmini çıxarır
     */
    private fun extractTemperature(text: String): Float? {
        val lower = text.lowercase(Locale.getDefault())

        // 1. Rəqəmlə deyilənlər (məs: 20, 21.5, 22)
        val digitRegex = Regex("""\b(1[6-9]|2[0-9]|3[0-2])(?:[.,]([0-9]))?\b""")
        val match = digitRegex.find(lower)
        if (match != null) {
            val whole = match.groupValues[1].toFloatOrNull() ?: 20f
            val decimal = match.groupValues.getOrNull(2)?.toFloatOrNull() ?: 0f
            return whole + (decimal / 10f)
        }

        // 2. Sözlə deyilən Azərbaycan rəqəmləri
        val numberWords = mapOf(
            "on alti" to 16f, "on altı" to 16f,
            "on yeddi" to 17f,
            "on sekkiz" to 18f, "on səkkiz" to 18f,
            "on doqquz" to 19f,
            "iyirmi" to 20f,
            "iyirmi bir" to 21f,
            "iyirmi iki" to 22f,
            "iyirmi uc" to 23f, "iyirmi üç" to 23f,
            "iyirmi dord" to 24f, "iyirmi dörd" to 24f,
            "iyirmi bes" to 25f, "iyirmi beş" to 25f,
            "iyirmi alti" to 26f, "iyirmi altı" to 26f,
            "iyirmi yeddi" to 27f,
            "iyirmi sekkiz" to 28f, "iyirmi səkkiz" to 28f,
            "iyirmi doqquz" to 29f,
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
     * Sürücünün ana dilində dediyi cümləni təhlil edir və avtomobil əmrinə çevirir
     */
    private fun parseAndExecuteCommand(text: String) {
        val lower = text.lowercase(Locale.getDefault())

        val isClimateContext = lower.contains("kondisioner") ||
                lower.contains("klima") ||
                lower.contains("dərəcə") ||
                lower.contains("derece") ||
                lower.contains("hava") ||
                lower.contains("isti") ||
                lower.contains("soyut") ||
                lower.contains("temperatur")

        if (isClimateContext) {
            val extractedTemp = extractTemperature(text)

            when {
                lower.contains("söndür") || lower.contains("sondur") || lower.contains("bağla") || lower.contains("bagla") -> {
                    speak("Kondisioner söndürüldü")
                    onCommandRecognized("CLIMATE_AC_OFF", text, null)
                }
                extractedTemp != null -> {
                    speak("Kondisioner ${extractedTemp.toInt()} dərəcəyə təyin edildi")
                    onCommandRecognized("CLIMATE_SET_TEMP", text, extractedTemp)
                }
                lower.contains("artır") || lower.contains("artir") || lower.contains("isti") -> {
                    speak("Temperatur artırıldı")
                    onCommandRecognized("CLIMATE_TEMP_UP", text, null)
                }
                lower.contains("azalt") || lower.contains("soyut") -> {
                    speak("Temperatur azaldıldı")
                    onCommandRecognized("CLIMATE_TEMP_DOWN", text, null)
                }
                lower.contains("aç") || lower.contains("ac") || lower.contains("yandır") || lower.contains("yandir") -> {
                    speak("Kondisioner işə salındı")
                    onCommandRecognized("CLIMATE_AC_ON", text, null)
                }
                else -> {
                    speak("Kondisioner tənzimləndi")
                    onCommandRecognized("CLIMATE_SET_TEMP", text, 22.0f)
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
            lower.contains("mahnı") || lower.contains("musiqi") || lower.contains("oxut") || lower.contains("spotify") || lower.contains("dəyiş") -> {
                speak("Musiqi dəyişdirilir")
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
                speak("Sizi eşitdim: $text")
                onCommandRecognized("GENERAL_AI_QUERY", text, null)
            }
        }
    }

    fun speak(text: String) {
        try {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CarTwinVoice")
        } catch (e: Exception) {
            Log.w(TAG, "TTS xətası: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("az", "AZ"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.setLanguage(Locale("tr", "TR"))
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
