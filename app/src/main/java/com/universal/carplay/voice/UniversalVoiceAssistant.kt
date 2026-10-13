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
 * Bütün Dilləri (Azərbaycan, Türk, Rus, İngilis) Dəstəkləyən Universal AI Səs Köməkçisi
 * 
 * Sürücü sükan düyməsinə basdıqda və ya səsli əmr verdikdə ana dilində sərbəst danışır:
 * Məsələn: "Kondisioneri 21 dərəcəyə qoy", "Əliyə zəng et", "Heydər Əliyev mərkəzinə marşrut qur".
 */
class UniversalVoiceAssistant(
    private val context: Context,
    private val onCommandRecognized: (commandType: String, originalText: String) -> Unit
) : TextToSpeech.OnInitListener {

    private val TAG = "UniversalVoiceAI"

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

    /**
     * Səs dinləməsini başladır
     */
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

    /**
     * Səs dinləməsini dayandırır
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * Sürücünün ana dilində dediyi cümləni təhlil edir və avtomobil əmrinə çevirir
     */
    private fun parseAndExecuteCommand(text: String) {
        val lower = text.lowercase(Locale.getDefault())

        val commandType = when {
            lower.contains("kondisioner") || lower.contains("dərəcə") || lower.contains("klima") || lower.contains("hava") -> {
                speak("Kondisioner tənzimləndi")
                "CLIMATE_CONTROL"
            }
            lower.contains("zəng") || lower.contains("ara") || lower.contains("telefon") || lower.contains("call") -> {
                speak("Əlaqə saxlanılır")
                "PHONE_CALL"
            }
            lower.contains("xəritə") || lower.contains("marşrut") || lower.contains("get") || lower.contains("naviqasiya") -> {
                speak("Marşrut qurulur")
                "NAVIGATION"
            }
            lower.contains("mahnı") || lower.contains("musiqi") || lower.contains("oxut") || lower.contains("spotify") -> {
                speak("Musiqi başladılır")
                "MEDIA_PLAY"
            }
            lower.contains("brauzer") || lower.contains("browser") || lower.contains("internet") || lower.contains("google") || lower.contains("sayt") || lower.contains("veb") -> {
                speak("İnternet brauzeri açılır")
                "OPEN_BROWSER"
            }
            lower.contains("youtube") || lower.contains("video") || lower.contains("film") || lower.contains("klip") -> {
                speak("YouTube Park Rejimi açılır")
                "OPEN_YOUTUBE"
            }
            else -> {
                "GENERAL_AI_QUERY"
            }
        }

        onCommandRecognized(commandType, text)
    }

    /**
     * Avtomobilin sürücüyə səslə cavab verməsi (Text-To-Speech)
     */
    fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CarPlayVoice")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale("az", "AZ")
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
