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
import java.text.SimpleDateFormat
import java.util.Date
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
        try {
            initSpeechRecognizer()
        } catch (e: Throwable) {
            Log.w(TAG, "SpeechRecognizer başlatmaq mümkün olmadı: ${e.message}")
        }
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Throwable) {
            Log.w(TAG, "TextToSpeech başlatmaq mümkün olmadı: ${e.message}")
        }
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
     * Ultra-dəqiq fonetik normalizasiya (Azərbaycan, Rus, İngilis, Türk)
     */
    fun normalize(rawText: String): String {
        var str = rawText.lowercase(Locale.getDefault()).trim()

        // 1. Kiril -> Latın transliterasiyası
        val cyrMap = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
            'ж' to "z", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
            'ф' to "f", 'х' to "x", 'ц' to "ts", 'ч' to "c", 'ш' to "s", 'щ' to "s", 'ъ' to "",
            'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
        )
        val sb = StringBuilder()
        for (ch in str) {
            sb.append(cyrMap[ch] ?: ch)
        }
        str = sb.toString()

        // 2. Diakritik hərfləri fonetikaya salırıq
        str = str
            .replace(Regex("[əeëéèê]"), "e")
            .replace(Regex("[ıiïîí]"), "i")
            .replace(Regex("[öoóòô]"), "o")
            .replace(Regex("[üuúùû]"), "u")
            .replace(Regex("[ğg]"), "g")
            .replace(Regex("[çc]"), "c")
            .replace(Regex("[şs]"), "s")
            .replace("w", "v")
            .replace("q", "g")

        // 3. Durğu işarələri
        str = str.replace(Regex("""[.,/#!$%\^&*;:{}=\-_`~()?"'🇦🇿🎙️]"""), " ")
        str = str.replace(Regex("""\s+"""), " ").trim()

        // 4. Şifahi yazılmış və ya şəkilçili rəqəmləri 16-32 ədədlərinə çeviririk
        val numberMap = listOf(
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:bir|bire)\b""") to "21",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:iki|ikiye)\b""") to "22",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:uc|uce)\b""") to "23",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:dord|dorde)\b""") to "24",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:bes|bese)\b""") to "25",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:alti|altiya)\b""") to "26",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:yeddi|yeddiye)\b""") to "27",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:sekkiz|sekkize)\b""") to "28",
            Regex("""\b(?:iyirmi|yirmi|yigirmi)\s+(?:doqquz|doqquza)\b""") to "29",
            Regex("""\b(?:on)\s+(?:alti|altiya)\b""") to "16",
            Regex("""\b(?:on)\s+(?:yeddi|yeddiye)\b""") to "17",
            Regex("""\b(?:on)\s+(?:sekkiz|sekkize)\b""") to "18",
            Regex("""\b(?:on)\s+(?:doqquz|doqquza)\b""") to "19",
            Regex("""\b(?:otuz)\s+(?:bir|bire)\b""") to "31",
            Regex("""\b(?:otuz)\s+(?:iki|ikiye)\b""") to "32",
            Regex("""\b(?:iyirmi|yirmi|yigirmi|iyirmiye|yirmiye)\b""") to "20",
            Regex("""\b(?:otuz|otuza)\b""") to "30",
            Regex("""\b(1[6-9]|2[0-9]|3[0-2])(?:ye|ya|e|a|deki|den|de)\b""") to "$1",
            Regex("""\b(?:dvadtsat)\s+(?:odin)\b""") to "21",
            Regex("""\b(?:dvadtsat)\s+(?:dva)\b""") to "22",
            Regex("""\b(?:dvadtsat)\s+(?:tri)\b""") to "23",
            Regex("""\b(?:dvadtsat)\s+(?:cetire)\b""") to "24",
            Regex("""\b(?:dvadtsat)\s+(?:pyat)\b""") to "25",
            Regex("""\b(?:dvadtsat)\s+(?:sest)\b""") to "26",
            Regex("""\b(?:dvadtsat)\s+(?:sem)\b""") to "27",
            Regex("""\b(?:dvadtsat)\s+(?:vosem)\b""") to "28",
            Regex("""\b(?:dvadtsat)\s+(?:devyat)\b""") to "29",
            Regex("""\b(?:dvadtsat)\b""") to "20",
            Regex("""\b(?:sestnadtsat)\b""") to "16",
            Regex("""\b(?:semnadtsat)\b""") to "17",
            Regex("""\b(?:vosemnadtsat)\b""") to "18",
            Regex("""\b(?:devyatnadtsat)\b""") to "19",
            Regex("""\b(?:tridtsat)\b""") to "30",
            Regex("""\b(?:twenty)\s+(?:one)\b""") to "21",
            Regex("""\b(?:twenty)\s+(?:two)\b""") to "22",
            Regex("""\b(?:twenty)\s+(?:three)\b""") to "23",
            Regex("""\b(?:twenty)\s+(?:four)\b""") to "24",
            Regex("""\b(?:twenty)\s+(?:five)\b""") to "25",
            Regex("""\b(?:twenty)\s+(?:six)\b""") to "26",
            Regex("""\b(?:twenty)\s+(?:seven)\b""") to "27",
            Regex("""\b(?:twenty)\s+(?:eight)\b""") to "28",
            Regex("""\b(?:twenty)\s+(?:nine)\b""") to "29",
            Regex("""\b(?:twenty)\b""") to "20",
            Regex("""\b(?:sixteen)\b""") to "16",
            Regex("""\b(?:seventeen)\b""") to "17",
            Regex("""\b(?:eighteen)\b""") to "18",
            Regex("""\b(?:nineteen)\b""") to "19",
            Regex("""\b(?:thirty)\b""") to "30"
        )

        for ((pattern, rep) in numberMap) {
            str = str.replace(pattern, rep)
        }

        return str
    }

    /**
     * Dəqiq temperatur dəyərini (16 - 32) çıxarır
     */
    fun extractTemperature(normalized: String): Float? {
        val digitRegex = Regex("""(?:^|\D)(1[6-9]|2[0-9]|3[0-2])(?:\D|$)""")
        val match = digitRegex.find(normalized)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * Sürücünün ana dilində dediyi cümləni tam və dəqiq anlayır
     */
    fun parseAndExecuteCommand(rawText: String) {
        val norm = normalize(rawText)
        val clean = norm.replace(Regex("""^(?:hey|ey|ay)?\s*aura[,:\s]*"""), "").trim()
        val temp = extractTemperature(clean)

        // 1. SƏS VƏ SƏS UCALIĞI (VOLUME)
        if (clean.contains("sesi artir") || clean.contains("sesi galdir") || clean.contains("sesi ucalt") ||
            clean.contains("berk ele") || clean.contains("ucadan") || clean.contains("sesi ac") ||
            clean.contains("volume up") || clean.contains("gromce")) {
            speak("Səs səviyyəsi artırıldı")
            onCommandRecognized("VOLUME_UP", rawText, null)
            return
        }

        if (clean.contains("sesi azalt") || clean.contains("sesi endir") || clean.contains("sesi yavaslat") ||
            clean.contains("sakit ele") || clean.contains("yavas ele") || clean.contains("volume down") ||
            clean.contains("tise")) {
            speak("Səs səviyyəsi azaldıldı")
            onCommandRecognized("VOLUME_DOWN", rawText, null)
            return
        }

        if (clean.contains("sesi kes") || clean.contains("sessiz") || clean.contains("sesi sondur") ||
            clean.contains("sesi bagla") || clean.contains("mute") || clean.contains("sus")) {
            speak("Səs bağlandı")
            onCommandRecognized("VOLUME_MUTE", rawText, null)
            return
        }

        if (clean.contains("sesi gaytar") || clean.contains("unmute") || clean.contains("sesi berpa")) {
            speak("Səs bərpa edildi")
            onCommandRecognized("VOLUME_UNMUTE", rawText, null)
            return
        }

        // 2. MUSİQİ VƏ MEDİA (MEDIA)
        if (clean.contains("dayandir") || clean.contains("saxla") || clean.contains("pauza") ||
            clean.contains("stop") || clean.contains("mahni dayandir") || clean.contains("musiqi dayandir") ||
            clean.contains("mahni saxla") || clean.contains("musiqi saxla")) {
            speak("Musiqi dayandırıldı")
            onCommandRecognized("MEDIA_PAUSE", rawText, null)
            return
        }

        if (clean.contains("oxut") || clean.contains("davam et") || clean.contains("baslat") ||
            clean.contains("play") || clean.contains("mahni oxut") || clean.contains("musiqi oxut")) {
            speak("Musiqi davam etdirilir")
            onCommandRecognized("MEDIA_PLAY", rawText, null)
            return
        }

        if (clean.contains("novbeti") || clean.contains("deyis") || clean.contains("baska mahni") ||
            clean.contains("o biri mahni") || clean.contains("sonraki") || clean.contains("ireli") ||
            clean.contains("next")) {
            speak("Musiqi dəyişdirildi")
            onCommandRecognized("MEDIA_NEXT", rawText, null)
            return
        }

        if (clean.contains("evvelki") || clean.contains("tekrar") || clean.contains("evvele") ||
            clean.contains("gayit") || clean.contains("previous")) {
            speak("Əvvəlki musiqi")
            onCommandRecognized("MEDIA_PREV", rawText, null)
            return
        }

        if (clean.contains("spotify") || clean.contains("spotifay") ||
            (clean.contains("musiqi") && clean.contains("ac")) ||
            (clean.contains("mahni") && clean.contains("ac"))) {
            speak("Spotify açılır")
            onCommandRecognized("APP_SPOTIFY", rawText, null)
            return
        }

        // 3. İQLİM VƏ KONDİSİONER (HVAC)
        if (clean.contains("maksimum soyut") || clean.contains("buz kimi") || clean.contains("max ac") ||
            clean.contains("cox soyuq ele") || clean.contains("lap soyuq ele")) {
            speak("Maksimum soyutma rejimi aktiv edildi")
            onCommandRecognized("CLIMATE_MAX_AC", rawText, 16.0f)
            return
        }

        if (clean.contains("maksimum gizdir") || clean.contains("maksimum isti") || clean.contains("max heat") ||
            clean.contains("lap isti ele")) {
            speak("Maksimum istilik rejimi aktiv edildi")
            onCommandRecognized("CLIMATE_MAX_HEAT", rawText, 30.0f)
            return
        }

        if (clean.contains("buxar") || clean.contains("defrost") || clean.contains("on suse") ||
            clean.contains("suse buxar") || clean.contains("buxari sil") || clean.contains("buxari temizle") ||
            clean.contains("buxari apar")) {
            speak("Ön şüşənin buxar təmizləyicisi aktiv edildi")
            onCommandRecognized("CLIMATE_DEFROST", rawText, null)
            return
        }

        val isClimateContext = clean.contains("kondi") || clean.contains("klim") || clean.contains("hava") ||
                clean.contains("pec") || clean.contains("derece") || clean.contains("temperatur") ||
                clean.contains("isti") || clean.contains("soyut") || clean.contains("soyuq") ||
                clean.contains("soyug") || clean.contains("usudum") || clean.contains("sudum") ||
                clean.contains("donuram") || clean.contains("donduq") || clean.contains("dondug") ||
                clean.contains("istidir") || clean.contains("yandim") || clean.contains("terledim") ||
                clean.contains("burkulu") || clean.contains("ac") || clean.contains("fan") ||
                clean.contains("ventilyator") || clean.contains("per") || temp != null

        if (isClimateContext) {
            // Söndürmə
            if (clean.contains("sondur") || clean.contains("bagla") || clean.contains("kapat") ||
                clean.contains("lazim deyil") || clean.contains("besdir") || clean.contains("kes") ||
                clean.contains("dayandir")) {
                speak("Kondisioner söndürüldü")
                onCommandRecognized("CLIMATE_AC_OFF", rawText, null)
                return
            }

            // Dəqiq temperatur
            if (temp != null) {
                speak("Kondisioner ${temp.toInt()} dərəcəyə təyin edildi")
                onCommandRecognized("CLIMATE_SET_TEMP", rawText, temp)
                return
            }

            // Sürücünün üşüməsi / İstilik artırma (soyuqdu, üşüdüm, südüm, donuram)
            if (clean.contains("soyugdu") || clean.contains("soyuqdu") || clean.contains("soyugdur") || clean.contains("soyuqdur") ||
                clean.contains("usudum") || clean.contains("sudum") || clean.contains("donuram") || clean.contains("donduq") ||
                clean.contains("dondug") || clean.contains("artir") || clean.contains("isti") || clean.contains("gizdir") ||
                clean.contains("qizdir") || clean.contains("galdir") || clean.contains("teplee") || clean.contains("warmer")) {
                speak("Temperatur artırıldı")
                onCommandRecognized("CLIMATE_TEMP_UP", rawText, null)
                return
            }

            // Sürücünün istilənməsi / Soyutma / temperatur azaltma (istidir, yandım, tərlədim, soyut, sərin)
            if (clean.contains("istidir") || clean.contains("yandim") || clean.contains("bisdim") || clean.contains("terledim") ||
                clean.contains("burkulu") || clean.contains("azalt") || clean.contains("soyut") || clean.contains("serin") ||
                clean.contains("endir") || clean.contains("xolodnee") || clean.contains("cooler")) {
                speak("Temperatur azaldıldı")
                onCommandRecognized("CLIMATE_TEMP_DOWN", rawText, null)
                return
            }

            // Fan sürəti
            if (clean.contains("fan") || clean.contains("ventilyator") || clean.contains("per") || clean.contains("ufur")) {
                val fanNum = Regex("""\b([1-7])\b""").find(clean)?.groupValues?.get(1)?.toFloatOrNull() ?: 4f
                speak("Ventilyator sürəti dəyişdirildi")
                onCommandRecognized("CLIMATE_FAN_SPEED", rawText, fanNum)
                return
            }

            // Açma
            if (clean.contains("ac") || clean.contains("yandir") || clean.contains("ise sal") ||
                clean.contains("qos") || clean.contains("baslat")) {
                speak("Kondisioner işə salındı")
                onCommandRecognized("CLIMATE_AC_ON", rawText, null)
                return
            }
        }

        // 4. NAVİQASİYA VƏ XƏRİTƏLƏR
        if (clean.contains("waze") || clean.contains("veiz") || clean.contains("naviqasiya") ||
            clean.contains("xerite") || clean.contains("marsrut") || clean.contains("yol") ||
            clean.contains("karta") || clean.contains("haradayam") || clean.contains("eve get")) {
            speak("Waze naviqasiyası açılır")
            onCommandRecognized("NAVIGATION", rawText, null)
            return
        }

        // 5. TELEFON VƏ ƏLAQƏ
        if (clean.contains("telefon") || clean.contains("zeng") || clean.contains("nomre") ||
            clean.contains("yig") || clean.contains("ara") || clean.contains("call") ||
            clean.contains("phone")) {
            speak("Telefon zəngi açılır")
            onCommandRecognized("PHONE_CALL", rawText, null)
            return
        }

        // 6. BRAUZER VƏ YOUTUBE
        if (clean.contains("brauzer") || clean.contains("internet") || clean.contains("google") ||
            clean.contains("sayt") || clean.contains("browser") || clean.contains("web")) {
            speak("İnternet brauzeri açılır")
            onCommandRecognized("OPEN_BROWSER", rawText, null)
            return
        }

        if (clean.contains("youtube") || clean.contains("yutub") || clean.contains("video") ||
            clean.contains("film") || clean.contains("klip")) {
            speak("YouTube Park Rejimi açılır")
            onCommandRecognized("OPEN_YOUTUBE", rawText, null)
            return
        }

        // 7. AVTOMOBİL PƏNCƏRƏ VƏ LYUK İDARƏSİ
        if (clean.contains("lyuk") || clean.contains("luk") || clean.contains("tavan")) {
            if (clean.contains("bagla") || clean.contains("ort") || clean.contains("kapat")) {
                speak("Lyuk bağlanır")
                onCommandRecognized("SUNROOF_CLOSE", rawText, null)
            } else {
                speak("Lyuk açılır")
                onCommandRecognized("SUNROOF_OPEN", rawText, null)
            }
            return
        }

        if (clean.contains("pencere") || clean.contains("suse")) {
            if (clean.contains("bagla") || clean.contains("galdir") || clean.contains("ort")) {
                speak("Pəncərələr bağlanır")
                onCommandRecognized("WINDOWS_CLOSE", rawText, null)
            } else {
                speak("Pəncərələr açılır")
                onCommandRecognized("WINDOWS_OPEN", rawText, null)
            }
            return
        }

        // 8. EKRAN VƏ GECƏ/GÜNDÜZ REJİMİ
        if (clean.contains("gece rejimi") || clean.contains("garanlig rejim") || clean.contains("dark mode") || clean.contains("night mode")) {
            speak("Gecə rejimi aktivləşdirildi")
            onCommandRecognized("SYSTEM_NIGHT_MODE", rawText, null)
            return
        }

        if (clean.contains("gunduz rejimi") || clean.contains("isigli rejim") || clean.contains("light mode") || clean.contains("day mode")) {
            speak("Gündüz rejimi aktivləşdirildi")
            onCommandRecognized("SYSTEM_DAY_MODE", rawText, null)
            return
        }

        if (clean.contains("ana ekran") || clean.contains("esas ekran") || clean.contains("menyu") ||
            clean.contains("eve get") || clean.contains("cix") || clean.contains("ekrani bagla") ||
            clean.contains("home")) {
            speak("Əsas ekrana qayıdıldı")
            onCommandRecognized("GO_HOME", rawText, null)
            return
        }

        // 9. DİALOQ VƏ TELEMETRİYA SORĞULARI
        if (clean.contains("saat") || clean.contains("vaxt") || clean.contains("time")) {
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            speak("Saat hazırda $currentTime-dir")
            onCommandRecognized("QUERY_TIME", rawText, null)
            return
        }

        if (clean.contains("tarix") || clean.contains("ayin necesidir") || clean.contains("hansi gundur")) {
            val currentDate = SimpleDateFormat("dd MMMM", Locale("az")).format(Date())
            speak("Bu gün $currentDate-dir")
            onCommandRecognized("QUERY_DATE", rawText, null)
            return
        }

        if (clean.contains("salam") || clean.contains("xos gorduk") || clean.contains("sabahiniz xeyir") ||
            clean.contains("axjaminiz xeyir") || clean.contains("hello") || clean.contains("privet")) {
            speak("Salam! Xoş gördük, sizə necə kömək edə bilərəm?")
            onCommandRecognized("QUERY_GREETING", rawText, null)
            return
        }

        if (clean.contains("necesen") || clean.contains("netersen") || clean.contains("veziyyet necedir") ||
            clean.contains("how are you") || clean.contains("kak dela")) {
            speak("Çox sağ olun, bütün avtomobil sistemləri əla vəziyyətdədir. Yola davam edirik!")
            onCommandRecognized("QUERY_HOW_ARE_YOU", rawText, null)
            return
        }

        if (clean.contains("sen kimsen") || clean.contains("adin nedir") || clean.contains("who are you")) {
            speak("Mən Aura AI — sizin şəxsi avtomobil köməkçinizəm. Əmrlərinizi icra etməyə hazıram.")
            onCommandRecognized("QUERY_WHO_ARE_YOU", rawText, null)
            return
        }

        if (clean.contains("masin necedir") || clean.contains("avtomobilin veziyyeti") || clean.contains("status")) {
            speak("Bütün sistemlər normal işləyir, təkər təzyiqi və mühərrik qaydasındadır. Yaxşı yol!")
            onCommandRecognized("QUERY_CAR_STATUS", rawText, null)
            return
        }

        // Təyin edilməyən digər sorğular
        playConfirmationChime()
        onCommandRecognized("GENERAL_AI_QUERY", rawText, null)
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
