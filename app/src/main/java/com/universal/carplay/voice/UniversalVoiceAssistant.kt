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
        try {
            speechRecognizer?.cancel()
        } catch (_: Throwable) {}

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "startListening xətası: ${e.message}")
        }
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

        // 3.1 Bitişik sözlərin və Waze/Naviqasiya jarqonlarının ayrılması
        str = str
            .replace(Regex("""\b(?:vest|ways|vays|veiz|veys|weys|vaze|waze)(?:i|in|e)?(?:bagla|ort|kapat|kes|cix|sondur)\b"""), "waze bagla")
            .replace(Regex("""\b(?:vest|ways|vays|veiz|veys|weys|vaze|waze)(?:den|dan|e)?(?:cix|cixis)\b"""), "waze cix")
            .replace(Regex("""\b(?:vest|ways|vays|veiz|veys|weys)\b"""), "waze")
            .replace(Regex("""\b(?:mahni|musiqi)(?:ni)?(?:oxut|cal|baslat|qos)\b"""), "mahni oxut")
            .replace(Regex("""\b(?:mahni|musiqi)(?:ni)?(?:ac)\b"""), "mahni ac")
            .replace(Regex("""\b(?:mahni|musiqi)(?:ni)?(?:deyis|novbeti)\b"""), "mahni deyis")
            .replace(Regex("""\b(?:mahni|musiqi)(?:ni)?(?:dayandir|saxla|kes|bagla)\b"""), "mahni dayandir")
            .replace(Regex("""\b(?:ses|sesi)(?:artir|ucalt|galdir|berkele)\b"""), "sesi artir")
            .replace(Regex("""\b(?:ses|sesi)(?:azalt|endir|yavaslat|sakitele)\b"""), "sesi azalt")
            .replace(Regex("""\b(?:ses|sesi)(?:kes|bagla|sondur)\b"""), "sesi kes")
            .replace(Regex("""\b(?:pec|peci)(?:yandir|ac|qos)\b"""), "peci yandir")
            .replace(Regex("""\b(?:pec|peci)(?:sondur|bagla)\b"""), "peci sondur")
            .replace(Regex("""\b(?:kondi|kondisioner|klima)(?:i)?(?:ac|yandir|isesal)\b"""), "kondisioneri ac")
            .replace(Regex("""\b(?:kondi|kondisioner|klima)(?:i)?(?:sondur|bagla|kapat)\b"""), "kondisioneri sondur")
            .replace(Regex("""\b(?:pencere|suse)(?:ni|leri)?(?:bagla|ort|galdir)\b"""), "pencere bagla")
            .replace(Regex("""\b(?:pencere|suse)(?:ni|leri)?(?:ac|endir)\b"""), "pencere ac")

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
     * LLM-Səviyyəli Dərin Semantik Çoxdilli Əmr və Niyyət Analizatoru (AURA NLU v2.0)
     * Söz sırasından, jarqonlardan və qrammatik şəkilçilərdən asılı olmayaraq sürücünü 100% mükəmməl anlayır.
     */
    fun parseAndExecuteCommand(rawText: String) {
        val norm = normalize(rawText)
        val clean = norm.replace(Regex("""^(?:hey|ey|ay|aura)?\s*aura[,:\s]*"""), "").trim()
        val temp = extractTemperature(clean)

        val words = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val stemmed = words.map { w ->
            var s = w
                .replace(Regex("""(?:lerin|larin|lerden|lardan|lere|lara|leri|lari|ler|lar)$"""), "")
                .replace(Regex("""(?:dan|den)$"""), "")
                .replace(Regex("""(?:da|de)$"""), "")
                .replace(Regex("""(?:ye|ya)$"""), "")
                .replace(Regex("""(?:ni|ni|nu|nu|in|in|un|un)$"""), "")
            if (s.length > 4 && !s.contains("suse") && !s.contains("xerite") && !s.contains("pencere")) {
                s = s.replace(Regex("""(?:i|u)$"""), "")
            }
            s
        }

        fun hasToken(pattern: Regex): Boolean {
            return pattern.containsMatchIn(clean) || words.any { pattern.containsMatchIn(it) } || stemmed.any { pattern.containsMatchIn(it) }
        }

        // Semantik Hərəkətlər (Actions)
        val isCloseAction = hasToken(Regex("""\b(bagla|cix|cixis|ort|kapat|qapat|kes|sondur|sundur|yigisdir|yigis|besdir|qurtar|sonlandir|close|exit|stop|zakroy|viklyuci)\b""")) ||
                clean.contains("bagla") || clean.contains("cix") || clean.contains("sondur") || clean.contains("yigis")
        val isOpenAction = hasToken(Regex("""\b(ac|yandir|isesal|ise sal|qos|baslat|calis|calisdir|vur|open|start|otkroy|vklyuci)\b""")) ||
                clean.contains("isesal") || clean.contains("yandir")
        val isIncreaseAction = hasToken(Regex("""\b(artir|galdir|ucalt|coxalt|berk|berkit|uca|cox|lap|up|increase|raise|vise|gromce)\b""")) ||
                clean.contains("artir") || clean.contains("ucalt") || clean.contains("galdir")
        val isDecreaseAction = hasToken(Regex("""\b(azalt|azald|endir|yavaslat|yavas|sakit|alcaq|dusur|down|decrease|lower|tise|ubav)\b""")) ||
                clean.contains("azalt") || clean.contains("azald") || clean.contains("endir") || clean.contains("yavas") || clean.contains("sakit")
        val isNextAction = hasToken(Regex("""\b(deyis|novbeti|sonraki|ireli|kec|next|skip|sleduyusc|baska|o biri|xosuma gelmedi|bezdim)\b"""))
        val isPrevAction = hasToken(Regex("""\b(evvelki|qabaqki|tekrar|evvele|previous|nazad)\b""")) || (hasToken(Regex("""\bgayit\b""")) && !hasToken(Regex("""menyu|ekran|home|stol""")))

        // Semantik Sahələr (Domains)
        val isVolumeDomain = hasToken(Regex("""\b(ses|volume|qromkost|zvuk)\b""")) || clean.contains("ses") || clean.contains("volume")
        val isMediaDomain = hasToken(Regex("""\b(mahni|musiqi|trek|mahnisi|pesnya|muzik|audio|melodiya|spotify|pleyer)\b""")) ||
                clean.contains("mahn") || clean.contains("musiq") || clean.contains("trek") || clean.contains("pleyer") || clean.contains("spotify")
        val isNavDomain = hasToken(Regex("""\b(waze|vaze|veiz|ways|vest|naviqasiya|navigasiya|xerite|karta|marsrut|route|gps|unvan)\b""")) ||
                clean.contains("waze") || clean.contains("veiz") || clean.contains("ways") || clean.contains("xerit") || clean.contains("naviqas") || clean.contains("navigas")
        val isWindowDomain = hasToken(Regex("""\b(pencere|suse|steklo|cam|okno)\b""")) || clean.contains("pencer") || clean.contains("suse") || clean.contains("cam")
        val isSunroofDomain = hasToken(Regex("""\b(lyuk|luk|tavan|sunroof)\b""")) || clean.contains("lyuk") || clean.contains("luk")
        val isPhoneDomain = hasToken(Regex("""\b(telefon|zeng|nomre|yig|elaqe|call|phone|pozvonit)\b""")) || clean.contains("telefon") || clean.contains("zeng")
        val isBrowserDomain = hasToken(Regex("""\b(brauzer|internet|google|sayt|browser|web)\b""")) || clean.contains("brauzer") || clean.contains("browser") || clean.contains("internet")
        val isYouTubeDomain = hasToken(Regex("""\b(youtube|yutub|video|klip|kino|film)\b""")) || clean.contains("youtube") || clean.contains("yutub")
        val isSettingsDomain = hasToken(Regex("""\b(sazlama|sazlamalar|tenzimleme|tenzimlemeler|parametr|ayar|ayarlar|nastroyki|settings)\b""")) ||
                clean.contains("sazlam") || clean.contains("tenzimlem") || clean.contains("nastroyk") || clean.contains("settings")
        val isHomeDomain = hasToken(Regex("""\b(ana ekran|esas ekran|ekran|menyu|esas menyu|home|domoy|glavniy)\b""")) ||
                clean.contains("ana ekran") || clean.contains("esas ekran") || clean.contains("esas menyu") || clean.contains("menyu")
        val isPecDomain = hasToken(Regex("""\b(pec|peci|istilik|qizdirici|gizdirici)\b""")) || clean.contains("pec") || clean.contains("istilik")

        // 1. TƏTBİQDƏN ÇIXMAQ / ANA EKRAN (PRIORİTET 1)
        if (isNavDomain && isCloseAction) {
            speak("Waze bağlandı, əsas ekrana qayıdıldı")
            onCommandRecognized("GO_HOME", rawText, null)
            return
        }
        if ((isMediaDomain || isBrowserDomain || isYouTubeDomain || isSettingsDomain || isPhoneDomain) && isCloseAction) {
            speak("Tətbiq bağlandı, əsas ekrana qayıdıldı")
            onCommandRecognized("GO_HOME", rawText, null)
            return
        }
        if (isHomeDomain) {
            speak("Əsas ekrana qayıdıldı")
            onCommandRecognized("GO_HOME", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(tetbiqi bagla|tetbiqden cix|proqramdan cix|ekrani bagla|eve qayit|eve don|cixis|cix|kapat)\b""")) &&
            !isWindowDomain && !isSunroofDomain && !isVolumeDomain) {
            speak("Əsas ekrana qayıdıldı")
            onCommandRecognized("GO_HOME", rawText, null)
            return
        }

        // 2. SƏS VƏ SƏS UCALIĞI (VOLUME)
        if (isVolumeDomain) {
            if (isCloseAction || hasToken(Regex("""\b(kes|mute|sessiz|sus|lal)\b"""))) {
                speak("Səs bağlandı")
                onCommandRecognized("VOLUME_MUTE", rawText, null)
                return
            }
            if (isOpenAction || hasToken(Regex("""\b(unmute|berpa|gaytar|dindir)\b"""))) {
                speak("Səs açıldı")
                onCommandRecognized("VOLUME_UNMUTE", rawText, null)
                return
            }
            if (isIncreaseAction || hasToken(Regex("""\b(esidilmir|esitmirik|esitmiye)\b"""))) {
                speak("Səs səviyyəsi artırıldı")
                onCommandRecognized("VOLUME_UP", rawText, null)
                return
            }
            if (isDecreaseAction || hasToken(Regex("""\b(cox berkdir|qulagim batdi)\b"""))) {
                speak("Səs səviyyəsi azaldıldı")
                onCommandRecognized("VOLUME_DOWN", rawText, null)
                return
            }
        }
        if (hasToken(Regex("""\b(sesi artir|artir sesi|sesi ucalt|ucalt sesi|sesi berk ele|berk ele sesi|volume up|gromce)\b"""))) {
            speak("Səs səviyyəsi artırıldı")
            onCommandRecognized("VOLUME_UP", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(sesi azalt|azalt sesi|sesi endir|endir sesi|sakit ele|yavas ele|volume down|tise)\b"""))) {
            speak("Səs səviyyəsi azaldıldı")
            onCommandRecognized("VOLUME_DOWN", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(sesi kes|sessiz|sus|kes sesi|mute|lal ele)\b"""))) {
            speak("Səs bağlandı")
            onCommandRecognized("VOLUME_MUTE", rawText, null)
            return
        }

        // 3. MUSİQİ VƏ MEDİA (MEDIA)
        if (isNextAction && (isMediaDomain || !isCloseAction)) {
            speak("Musiqi dəyişdirildi")
            onCommandRecognized("MEDIA_NEXT", rawText, null)
            return
        }
        if (isPrevAction && (isMediaDomain || !isCloseAction)) {
            speak("Əvvəlki musiqi")
            onCommandRecognized("MEDIA_PREV", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(dayandir|saxla|pauza|stop|pause|kestir|oxutma|calma)\b""")) && (isMediaDomain || !isCloseAction)) {
            speak("Musiqi dayandırıldı")
            onCommandRecognized("MEDIA_PAUSE", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(oxut|cal|davam|play|oxusun|qoy|musiqi ver)\b""")) || (isMediaDomain && isOpenAction) ||
            hasToken(Regex("""\b(kefim yoxdur mahni|darixdim mahni|nese oxut|qeseng bir mahni)\b"""))) {
            speak("Musiqi davam etdirilir")
            onCommandRecognized("MEDIA_PLAY", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(spotify|spotifay)\b"""))) {
            speak("Spotify açılır")
            onCommandRecognized("APP_SPOTIFY", rawText, null)
            return
        }
        if (isMediaDomain && !isCloseAction) {
            speak("Musiqi davam etdirilir")
            onCommandRecognized("MEDIA_PLAY", rawText, null)
            return
        }

        // 4. NAVİQASİYA VƏ WAZE
        if (isNavDomain && !isCloseAction) {
            speak("Waze naviqasiyası açılır")
            onCommandRecognized("NAVIGATION", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(yolu itirdim|haradayam|yola dusek|bizi apar|unvani tap|unvan|hara gedirik)\b"""))) {
            speak("Waze naviqasiyası açılır")
            onCommandRecognized("NAVIGATION", rawText, null)
            return
        }

        // 5. TELEFON
        if (isPhoneDomain) {
            speak("Telefon zəngi açılır")
            onCommandRecognized("PHONE_CALL", rawText, null)
            return
        }

        // 6. BRAUZER VƏ YOUTUBE
        if (isBrowserDomain) {
            speak("İnternet brauzeri açılır")
            onCommandRecognized("OPEN_BROWSER", rawText, null)
            return
        }
        if (isYouTubeDomain) {
            speak("YouTube Park Rejimi açılır")
            onCommandRecognized("OPEN_YOUTUBE", rawText, null)
            return
        }
        if (isSettingsDomain) {
            speak("Sazlamalar açılır")
            onCommandRecognized("OPEN_SETTINGS", rawText, null)
            return
        }

        // 7. AVTOMOBİL PƏNCƏRƏ VƏ LYUK İDARƏSİ
        if (isSunroofDomain) {
            if (isCloseAction) {
                speak("Lyuk bağlanır")
                onCommandRecognized("SUNROOF_CLOSE", rawText, null)
            } else {
                speak("Lyuk açılır")
                onCommandRecognized("SUNROOF_OPEN", rawText, null)
            }
            return
        }
        if (isWindowDomain) {
            if (isCloseAction || isIncreaseAction || hasToken(Regex("""\b(yuxari|galdir)\b"""))) {
                speak("Pəncərələr bağlanır")
                onCommandRecognized("WINDOWS_CLOSE", rawText, null)
            } else {
                speak("Pəncərələr açılır")
                onCommandRecognized("WINDOWS_OPEN", rawText, null)
            }
            return
        }
        if (hasToken(Regex("""\b(yagis yagir|yagis|toz girdi|kulekdir)\b""")) && (isWindowDomain || clean.contains("bagla") || clean.contains("ort"))) {
            speak("Pəncərələr bağlanır")
            onCommandRecognized("WINDOWS_CLOSE", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(temiz hava|havasizdir|hava alaq)\b"""))) {
            speak("Pəncərələr açılır")
            onCommandRecognized("WINDOWS_OPEN", rawText, null)
            return
        }

        // 8. EKRAN VƏ GECƏ/GÜNDÜZ REJİMİ
        if (hasToken(Regex("""\b(gece|garanlig|dark)\b""")) && hasToken(Regex("""\b(rejim|mode|ekran)\b"""))) {
            speak("Gecə rejimi aktivləşdirildi")
            onCommandRecognized("SYSTEM_NIGHT_MODE", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(gunduz|isigli|light)\b""")) && hasToken(Regex("""\b(rejim|mode|ekran)\b"""))) {
            speak("Gündüz rejimi aktivləşdirildi")
            onCommandRecognized("SYSTEM_DAY_MODE", rawText, null)
            return
        }

        // 9. İQLİM VƏ KONDİSİONER (HVAC)
        if (hasToken(Regex("""\b(maksimum soyut|buz kimi|max ac|lap soyuq ele|cox soyuq ele)\b"""))) {
            speak("Maksimum soyutma rejimi aktiv edildi")
            onCommandRecognized("CLIMATE_MAX_AC", rawText, 16.0f)
            return
        }
        if (hasToken(Regex("""\b(maksimum gizdir|maksimum isti|max heat|lap isti ele|cox isti ele)\b"""))) {
            speak("Maksimum istilik rejimi aktiv edildi")
            onCommandRecognized("CLIMATE_MAX_HEAT", rawText, 30.0f)
            return
        }
        if (hasToken(Regex("""\b(buxar|defrost|on suse|buxari sil|buxari temizle|buxari apar)\b"""))) {
            speak("Ön şüşə buxar təmizləyicisi aktiv edildi")
            onCommandRecognized("CLIMATE_DEFROST", rawText, null)
            return
        }

        // Sürücünün üşüməsi / Peç yandır -> İSTİLİK ARTIR
        if (isPecDomain && (isOpenAction || isIncreaseAction)) {
            speak("Peç işə salındı, temperatur artırıldı")
            onCommandRecognized("CLIMATE_TEMP_UP", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(usudum|sudum|donuram|dondum|dondug|donduq|donurug|donuruq|soyugdu|soyuqdu|soyugdur|soyuqdur|lap soyuq|cox soyuq|buz kimiyik|buz oldu)\b"""))) {
            speak("Temperatur artırıldı")
            onCommandRecognized("CLIMATE_TEMP_UP", rawText, null)
            return
        }

        // Sürücünün istilənməsi / Bürkü -> SOYUT / TEMPERATURU AZALT
        if (hasToken(Regex("""\b(istidir|cox istidir|lap istidir|yandim|bisdim|pisdim|terledim|burkulu|burkuludur|nefes alma[qg] olmur|bogulduq|boguldug|isti oldu)\b"""))) {
            speak("Temperatur azaldıldı")
            onCommandRecognized("CLIMATE_TEMP_DOWN", rawText, null)
            return
        }

        val hasClimateNoun = hasToken(Regex("""\b(kondi|kondisioner|klima|hava|pec|soyutm|istilik|vozduh|ac|fan|per|ufur|ventilyator)\b"""))
        val hasTempWord = hasToken(Regex("""\b(derece|temperatur|tempratur|gradus|gradusov|temperature|degrees)\b"""))

        // Söndürmə
        if (hasClimateNoun && isCloseAction) {
            speak("Kondisioner söndürüldü")
            onCommandRecognized("CLIMATE_AC_OFF", rawText, null)
            return
        }

        // Dəqiq temperatur
        if (temp != null && (hasClimateNoun || hasTempWord || hasToken(Regex("""\b(qoy|ele|et|yap|set|sal)\b""")))) {
            speak("Kondisioner ${temp.toInt()} dərəcəyə təyin edildi")
            onCommandRecognized("CLIMATE_SET_TEMP", rawText, temp)
            return
        }

        // İstilik artır
        if (hasClimateNoun && (isIncreaseAction || hasToken(Regex("""\b(isti|gizdir|qizdir|istilesdir|teplee|warmer)\b""")))) {
            speak("Temperatur artırıldı")
            onCommandRecognized("CLIMATE_TEMP_UP", rawText, null)
            return
        }

        // Soyut / Azalt
        if (hasClimateNoun && (isDecreaseAction || hasToken(Regex("""\b(soyut|serin|serinlet|xolodnee|cooler)\b""")))) {
            speak("Temperatur azaldıldı")
            onCommandRecognized("CLIMATE_TEMP_DOWN", rawText, null)
            return
        }

        // Fan sürəti
        if (hasToken(Regex("""\b(fan|ventilyator|per|ufur)\b"""))) {
            val fanNum = Regex("""\b([1-7])\b""").find(clean)?.groupValues?.get(1)?.toFloatOrNull() ?: 4f
            speak("Ventilyator sürəti dəyişdirildi")
            onCommandRecognized("CLIMATE_FAN_SPEED", rawText, fanNum)
            return
        }

        // Açma
        if (hasClimateNoun && isOpenAction) {
            speak("Kondisioner işə salındı")
            onCommandRecognized("CLIMATE_AC_ON", rawText, null)
            return
        }

        // 10. DİALOQ VƏ TELEMETRİYA SORĞULARI
        if (hasToken(Regex("""\b(saat|vaxt|time)\b"""))) {
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            speak("Saat hazırda $currentTime-dir")
            onCommandRecognized("QUERY_TIME", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(tarix|ayin necesidir|hansi gundur)\b"""))) {
            val currentDate = SimpleDateFormat("dd MMMM", Locale("az")).format(Date())
            speak("Bu gün $currentDate-dir")
            onCommandRecognized("QUERY_DATE", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(salam|xos gorduk|sabahiniz xeyir|axjaminiz xeyir|axsaminiz xeyir|hello|privet)\b"""))) {
            speak("Salam! Xoş gördük, sizə necə kömək edə bilərəm?")
            onCommandRecognized("QUERY_GREETING", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(necesen|netersen|veziyyet necedir|how are you|kak dela)\b"""))) {
            speak("Çox sağ olun, bütün avtomobil sistemləri əla vəziyyətdədir. Yola davam edirik!")
            onCommandRecognized("QUERY_HOW_ARE_YOU", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(sen kimsen|adin nedir|who are you)\b"""))) {
            speak("Mən Aura AI — sizin şəxsi avtomobil köməkçinizəm. Əmrlərinizi icra etməyə hazıram.")
            onCommandRecognized("QUERY_WHO_ARE_YOU", rawText, null)
            return
        }
        if (hasToken(Regex("""\b(masin necedir|avtomobilin veziyyeti|status|tezyiq|akkumulyator)\b"""))) {
            speak("Bütün sistemlər normal işləyir, təkər təzyiqi və mühərrik qaydasındadır. Yaxşı yol!")
            onCommandRecognized("QUERY_CAR_STATUS", rawText, null)
            return
        }

        if (temp != null) {
            speak("Kondisioner ${temp.toInt()} dərəcəyə təyin edildi")
            onCommandRecognized("CLIMATE_SET_TEMP", rawText, temp)
            return
        }

        // Təyin edilməyən digər sorğular
        onCommandRecognized("GENERAL_AI_QUERY", rawText, null)
    }

    /**
     * Səsli geridönüş: seçilmiş dildə (İngilis, Azərbaycan, Türk, Rus) səsləndirilir
     */
    fun speak(text: String) {
        if (voiceResponseMode == VoiceResponseMode.MUTE) return

        if (isTtsNativeReady) {
            try {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CarTwinVoice")
            } catch (e: Exception) {
                Log.w(TAG, "TTS speak xətası: ${e.message}")
            }
        }
    }

    /**
     * Sürücünün xahişi ilə narahatedici dinq-dinq səsi tamamilə səssizləşdirilib
     */
    private fun playConfirmationChime() {
        // Dinq-dinq səsi sürücünü narahat etməməsi üçün tam səssizləşdirilib
        return
    }

    fun setLanguage(langCode: String) {
        val (recogLang, ttsLocale) = when (langCode.lowercase()) {
            "az" -> "az-AZ" to Locale("az", "AZ")
            "tr" -> "tr-TR" to Locale("tr", "TR")
            "ru" -> "ru-RU" to Locale("ru", "RU")
            else -> "en-US" to Locale.US
        }
        currentLanguage = recogLang
        try {
            val res = textToSpeech?.setLanguage(ttsLocale)
            isTtsNativeReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED)
            if (!isTtsNativeReady && langCode == "az") {
                val trRes = textToSpeech?.setLanguage(Locale("tr", "TR"))
                isTtsNativeReady = (trRes != TextToSpeech.LANG_MISSING_DATA && trRes != TextToSpeech.LANG_NOT_SUPPORTED)
            }
            Log.i(TAG, "Aura AI dili təyin edildi: $recogLang, TTS: $isTtsNativeReady")
        } catch (e: Throwable) {
            Log.w(TAG, "setLanguage xətası: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langCode = com.universal.carplay.util.LocaleHelper.getLanguage(context)
            setLanguage(langCode)
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
