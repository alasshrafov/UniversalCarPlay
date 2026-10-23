package com.universal.carplay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.universal.carplay.car.DayNightManager
import com.universal.carplay.car.SteeringWheelManager
import com.universal.carplay.car.VehicleDetector
import com.universal.carplay.car.VehicleHvacManager
import com.universal.carplay.car.VehicleStateManager
import com.universal.carplay.connectivity.CarPlaySessionCoordinator
import com.universal.carplay.connectivity.CarPlayUsbManager
import com.universal.carplay.engine.AudioEngine
import com.universal.carplay.engine.TouchManager
import com.universal.carplay.engine.VideoDecoder
import com.universal.carplay.profiles.VehicleProfile
import com.universal.carplay.protocol.CarPlayCryptoHandshake
import android.content.Context
import com.universal.carplay.protocol.MockCarPlayClient
import com.universal.carplay.ui.CarPlaySurfaceView
import com.universal.carplay.util.LocaleHelper
import com.universal.carplay.voice.UniversalVoiceAssistant
import java.net.URLEncoder

class MainActivity : AppCompatActivity(), CarPlaySurfaceView.SurfaceListener {

    companion object {
        private const val TAG = "CarTwin_MainActivity"
        private const val PERMISSIONS_REQUEST_CODE = 1001
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    private lateinit var surfaceView: CarPlaySurfaceView
    private lateinit var statusOverlay: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvCurrentProfile: TextView
    private lateinit var ivTopBarLogo: ImageView
    private lateinit var btnSettings: Button
    private lateinit var btnReplayIntro: Button
    private lateinit var btnConnectMock: Button
    private lateinit var btnRefreshConnection: Button
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnHelpGuide: Button
    private lateinit var btnHelpGuideTop: Button
    private lateinit var btnLanguage: Button
    private lateinit var cardCarPlayInfo: LinearLayout
    private lateinit var cardAndroidAutoInfo: LinearLayout
    private lateinit var otaUpdateManager: com.universal.carplay.update.OtaUpdateManager

    // Lüks Reklam Açılış Şousu Overlay Elementləri
    private lateinit var commercialSplashOverlay: FrameLayout
    private lateinit var commercialCenterContainer: LinearLayout
    private lateinit var ivCommercialLogo: ImageView
    private lateinit var tvCommercialTitle: TextView
    private lateinit var tvCommercialTagline: TextView
    private lateinit var tvCommercialStatus: TextView
    private val introHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    // İnternet Brauzeri Elementləri
    private lateinit var browserOverlay: LinearLayout
    private lateinit var webViewBrowser: WebView
    private lateinit var etBrowserUrl: EditText
    private lateinit var btnBrowserBack: Button
    private lateinit var btnBrowserForward: Button
    private lateinit var btnBrowserRefresh: Button
    private lateinit var btnBrowserGo: Button
    private lateinit var btnBrowserClose: Button
    private lateinit var btnOpenBrowser: Button
    private lateinit var btnQuickGoogle: Button
    private lateinit var btnQuickYouTube: Button
    private lateinit var btnQuickWiki: Button
    private lateinit var browserProgressBar: ProgressBar

    // Nüvə Mühərrikləri
    private var videoDecoder: VideoDecoder? = null
    private var audioEngine: AudioEngine? = null
    private var touchManager: TouchManager? = null
    private var mockClient: MockCarPlayClient? = null
    private var sessionCoordinator: CarPlaySessionCoordinator? = null

    // Avtomobil İnteqrasiyaları
    private lateinit var vehicleDetector: VehicleDetector
    private lateinit var vehicleStateManager: VehicleStateManager
    private lateinit var steeringWheelManager: SteeringWheelManager
    private lateinit var dayNightManager: DayNightManager
    private var voiceAssistant: UniversalVoiceAssistant? = null
    private lateinit var vehicleHvacManager: VehicleHvacManager
    private lateinit var usbManager: CarPlayUsbManager
    private val cryptoHandshake = CarPlayCryptoHandshake()

    private var currentProfile: VehicleProfile = VehicleProfile.Universal

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Qlobal Crash Müdafiəsi (Çökmə və Təkrar Çökmə Dövrəsinin Qarşısını Alır)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Fatal Uncaught Exception in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                // Təhlükəsiz sıfırlama
                getSharedPreferences("carplay_vehicle_prefs", MODE_PRIVATE).edit().clear().apply()
            } catch (_: Throwable) {}
        }

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        super.onCreate(savedInstanceState)

        try {
            // Avtomobil üçün ekranın sönməməsini təmin edirik
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemUI()

            setContentView(R.layout.activity_main)

            initViews()
            initBrowser()
            requestRequiredPermissions()
            initVehicleDetection()
            initCarIntegrations()
            initSessionCoordinator()
            initUsbManager()
            handleUsbIntent(intent)
            initOtaUpdates()
        } catch (e: Throwable) {
            Log.e(TAG, "onCreate zamanı kritik xəta: ${e.message}", e)
        }
    }

    private fun initOtaUpdates() {
        try {
            val currentVer = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "2.0.2"
            } catch (_: Throwable) { "2.0.2" }

            otaUpdateManager = com.universal.carplay.update.OtaUpdateManager(
                context = this,
                currentVersionName = currentVer
            ) { hasUpdate, newVersion, _ ->
                if (hasUpdate) {
                    btnCheckUpdate.text = "🚀 v$newVersion Yenilə!"
                    btnCheckUpdate.setTextColor(Color.parseColor("#30D158"))
                }
            }

            // Avtomobil işə düşəndən 3 saniyə sonra Wi-Fi/LTE qoşulan kimi avtomatik arxa planda yoxlayır
            Handler(Looper.getMainLooper()).postDelayed({
                otaUpdateManager.checkForUpdates(silent = true)
            }, 3000)
        } catch (e: Throwable) {
            Log.e(TAG, "OTA yeniləmə xətası: ${e.message}")
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }

    private fun initViews() {
        surfaceView = findViewById(R.id.surfaceView)
        statusOverlay = findViewById(R.id.statusOverlay)
        tvStatus = findViewById(R.id.tvStatus)
        tvDetail = findViewById(R.id.tvDetail)
        tvCurrentProfile = findViewById(R.id.tvCurrentProfile)
        ivTopBarLogo = findViewById(R.id.ivTopBarLogo)
        btnSettings = findViewById(R.id.btnSettings)
        btnReplayIntro = findViewById(R.id.btnReplayIntro)
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
        btnConnectMock = findViewById(R.id.btnConnectMock)
        btnRefreshConnection = findViewById(R.id.btnRefreshConnection)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        btnCheckUpdate.setOnClickListener {
            otaUpdateManager.checkForUpdates(silent = false)
        }

        // Reklam Şousu Görünüşləri
        commercialSplashOverlay = findViewById(R.id.commercialSplashOverlay)
        commercialCenterContainer = findViewById(R.id.commercialCenterContainer)
        ivCommercialLogo = findViewById(R.id.ivCommercialLogo)
        tvCommercialTitle = findViewById(R.id.tvCommercialTitle)
        tvCommercialTagline = findViewById(R.id.tvCommercialTagline)
        tvCommercialStatus = findViewById(R.id.tvCommercialStatus)

        // Brauzer Elementləri
        browserOverlay = findViewById(R.id.browserOverlay)
        webViewBrowser = findViewById(R.id.webViewBrowser)
        etBrowserUrl = findViewById(R.id.etBrowserUrl)
        btnBrowserBack = findViewById(R.id.btnBrowserBack)
        btnBrowserForward = findViewById(R.id.btnBrowserForward)
        btnBrowserRefresh = findViewById(R.id.btnBrowserRefresh)
        btnBrowserGo = findViewById(R.id.btnBrowserGo)
        btnBrowserClose = findViewById(R.id.btnBrowserClose)
        btnQuickGoogle = findViewById(R.id.btnQuickGoogle)
        btnQuickYouTube = findViewById(R.id.btnQuickYouTube)
        btnQuickWiki = findViewById(R.id.btnQuickWiki)
        browserProgressBar = findViewById(R.id.browserProgressBar)

        commercialSplashOverlay.setOnClickListener {
            dismissCommercialIntro()
        }

        btnReplayIntro.setOnClickListener {
            playCommercialIntro(currentProfile)
        }

        surfaceView.setSurfaceListener(this)

        ivTopBarLogo.setOnClickListener {
            voiceAssistant?.startListening()
            Toast.makeText(this@MainActivity, getString(R.string.aura_listening), Toast.LENGTH_SHORT).show()
        }

        btnSettings.setOnClickListener {
            showProfileSelectionDialog()
        }

        btnConnectMock.setOnClickListener {
            startMockConnection()
        }

        btnRefreshConnection.setOnClickListener {
            restartAllCarPlayServices()
        }

        btnHelpGuide = findViewById(R.id.btnHelpGuide)
        btnHelpGuideTop = findViewById(R.id.btnHelpGuideTop)
        btnLanguage = findViewById(R.id.btnLanguage)
        cardCarPlayInfo = findViewById(R.id.cardCarPlayInfo)
        cardAndroidAutoInfo = findViewById(R.id.cardAndroidAutoInfo)

        val currentLang = LocaleHelper.getLanguage(this)
        btnLanguage.text = LocaleHelper.getLanguageShortBadge(currentLang)
        btnLanguage.setOnClickListener { showLanguageSelectionDialog() }

        btnHelpGuide.setOnClickListener { showConnectionGuideDialog() }
        btnHelpGuideTop.setOnClickListener { showConnectionGuideDialog() }
        cardCarPlayInfo.setOnClickListener {
            if (::usbManager.isInitialized && (usbManager.currentPhoneType == CarPlayUsbManager.PHONE_TYPE_CARPLAY || usbManager.isAppleDeviceConnected())) {
                statusOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        statusOverlay.visibility = View.GONE
                        statusOverlay.alpha = 1f
                    }
                    .start()
            } else {
                showConnectionGuideDialog()
            }
        }
        cardAndroidAutoInfo.setOnClickListener {
            if (::usbManager.isInitialized && usbManager.currentPhoneType == CarPlayUsbManager.PHONE_TYPE_ANDROID_AUTO) {
                statusOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        statusOverlay.visibility = View.GONE
                        statusOverlay.alpha = 1f
                    }
                    .start()
            } else {
                showConnectionGuideDialog()
            }
        }
    }

    private fun restartAllCarPlayServices() {
        Toast.makeText(this, getString(R.string.services_restarting), Toast.LENGTH_SHORT).show()
        sessionCoordinator?.stopDiscovery()
        usbManager.stop()

        Handler(Looper.getMainLooper()).postDelayed({
            usbManager.start()
            initSessionCoordinator()
            sessionCoordinator?.startDiscovery(carName = "CarTwin")
            Toast.makeText(this, getString(R.string.services_ready), Toast.LENGTH_SHORT).show()
        }, 500)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initBrowser() {
        try {
            webViewBrowser.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
            }

            webViewBrowser.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let { etBrowserUrl.setText(it) }
                    browserProgressBar.visibility = View.GONE
                }
            }

            webViewBrowser.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        browserProgressBar.visibility = View.VISIBLE
                        browserProgressBar.progress = newProgress
                    } else {
                        browserProgressBar.visibility = View.GONE
                    }
                }
            }

            btnBrowserBack.setOnClickListener {
                if (webViewBrowser.canGoBack()) webViewBrowser.goBack()
            }

            btnBrowserForward.setOnClickListener {
                if (webViewBrowser.canGoForward()) webViewBrowser.goForward()
            }

            btnBrowserRefresh.setOnClickListener {
                webViewBrowser.reload()
            }

            btnBrowserClose.setOnClickListener {
                closeBrowser()
            }

            btnBrowserGo.setOnClickListener {
                loadEnteredUrl()
            }

            etBrowserUrl.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    loadEnteredUrl()
                    true
                } else {
                    false
                }
            }

            btnQuickGoogle.setOnClickListener { openBrowser("https://www.google.com") }
            btnQuickYouTube.setOnClickListener { openBrowser("https://m.youtube.com") }
            btnQuickWiki.setOnClickListener { openBrowser("https://az.wikipedia.org") }
            btnOpenBrowser.setOnClickListener { openBrowser("https://www.google.com") }
        } catch (e: Throwable) {
            Log.e(TAG, "Brauzer başladılarkən xəta: ${e.message}")
        }
    }

    fun openBrowser(url: String = "https://www.google.com") {
        browserOverlay.visibility = View.VISIBLE
        var target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://www.google.com/search?q=" + URLEncoder.encode(target, "UTF-8")
        }
        etBrowserUrl.setText(target)
        webViewBrowser.loadUrl(target)
    }

    private fun closeBrowser() {
        browserOverlay.visibility = View.GONE
    }

    private fun loadEnteredUrl() {
        val input = etBrowserUrl.text.toString().trim()
        if (input.isNotEmpty()) {
            openBrowser(input)
        }
    }

    private fun initVehicleDetection() {
        try {
            vehicleDetector = VehicleDetector(this)
            val detectedProfile = vehicleDetector.detectProfile()
            setupProfile(detectedProfile)
        } catch (e: Throwable) {
            Log.e(TAG, "VehicleDetector xətası: ${e.message}")
            setupProfile(VehicleProfile.Universal)
        }
    }

    private fun initCarIntegrations() {
        try {
            // 1. Sükan Düymələri
            steeringWheelManager = SteeringWheelManager { commandJson ->
                if (commandJson.contains("TRIGGER_SIRI")) {
                    runOnUiThread {
                        voiceAssistant?.startListening()
                        val prompt = if (::usbManager.isInitialized && usbManager.currentPhoneType == CarPlayUsbManager.PHONE_TYPE_ANDROID_AUTO) {
                            "🎙️ Aura AI Dinləyir (Android Səs Köməkçisi)..."
                        } else {
                            "🎙️ Aura AI Dinləyir..."
                        }
                        Toast.makeText(this@MainActivity, prompt, Toast.LENGTH_SHORT).show()
                    }
                }
                sessionCoordinator?.sendTouchEvent(commandJson)
                mockClient?.sendTouchEvent(commandJson)

                if (::usbManager.isInitialized) {
                    if (commandJson.contains("MEDIA_NEXT")) usbManager.sendCommand(204)
                    if (commandJson.contains("MEDIA_PREV")) usbManager.sendCommand(205)
                    if (commandJson.contains("MEDIA_PLAY") || commandJson.contains("MEDIA_PAUSE")) usbManager.sendCommand(203)
                    if (commandJson.contains("TRIGGER_SIRI")) usbManager.sendCommand(5)
                }
            }

            // 2. Audio Fokus və Arxa Kamera Meneceri
            vehicleStateManager = VehicleStateManager(
                context = this,
                onAudioDuckingChanged = { shouldDuck ->
                    audioEngine?.setVolumeDucking(shouldDuck)
                },
                onReverseCameraStateChanged = { inReverse ->
                    audioEngine?.setVolumeDucking(inReverse)
                }
            )

            // 3. Gecə/Gündüz Rejimi
            dayNightManager = DayNightManager(this)

            // 4. Avtomobil İqlim və Kondisioner Meneceri
            vehicleHvacManager = VehicleHvacManager(this) { state ->
                runOnUiThread {
                    showClimateHud(state)
                }
            }

            // 5. Azərbaycan Dilli Aura AI Səs Köməkçisi
            voiceAssistant = UniversalVoiceAssistant(this) { commandType, originalText, param ->
                runOnUiThread {
                    when (commandType) {
                        "CLIMATE_SET_TEMP" -> {
                            val target = param ?: 22.0f
                            vehicleHvacManager.setTemperature(target)
                        }
                        "CLIMATE_AC_OFF" -> vehicleHvacManager.setAcPower(false)
                        "CLIMATE_AC_ON" -> vehicleHvacManager.setAcPower(true)
                        "CLIMATE_MAX_AC" -> {
                            vehicleHvacManager.setTemperature(16.0f)
                            vehicleHvacManager.setFanSpeed(7)
                            vehicleHvacManager.setAcPower(true)
                        }
                        "CLIMATE_MAX_HEAT" -> {
                            vehicleHvacManager.setTemperature(30.0f)
                            vehicleHvacManager.setFanSpeed(7)
                            vehicleHvacManager.setAcPower(true)
                        }
                        "CLIMATE_DEFROST" -> {
                            vehicleHvacManager.setAcPower(true)
                            vehicleHvacManager.setFanSpeed(7)
                            Toast.makeText(this, getString(R.string.toast_defrost_active), Toast.LENGTH_SHORT).show()
                        }
                        "CLIMATE_FAN_SPEED" -> {
                            val speed = param?.toInt() ?: 4
                            vehicleHvacManager.setFanSpeed(speed)
                        }
                        "CLIMATE_TEMP_UP" -> {
                            val current = vehicleHvacManager.getClimateState().targetTemp
                            vehicleHvacManager.setTemperature(current + 1.0f)
                        }
                        "CLIMATE_TEMP_DOWN" -> {
                            val current = vehicleHvacManager.getClimateState().targetTemp
                            vehicleHvacManager.setTemperature(current - 1.0f)
                        }
                        "VOLUME_UP" -> {
                            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        }
                        "VOLUME_DOWN" -> {
                            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        }
                        "VOLUME_MUTE" -> {
                            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                            } else {
                                @Suppress("DEPRECATION")
                                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
                            }
                        }
                        "VOLUME_UNMUTE" -> {
                            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                            } else {
                                @Suppress("DEPRECATION")
                                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false)
                            }
                        }
                        "MEDIA_NEXT" -> {
                            sessionCoordinator?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_NEXT","keyCode":87}""")
                            mockClient?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_NEXT","keyCode":87}""")
                            Toast.makeText(this, getString(R.string.toast_next_track), Toast.LENGTH_SHORT).show()
                        }
                        "MEDIA_PREV" -> {
                            sessionCoordinator?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_PREV","keyCode":88}""")
                            mockClient?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_PREV","keyCode":88}""")
                            Toast.makeText(this, getString(R.string.toast_prev_track), Toast.LENGTH_SHORT).show()
                        }
                        "MEDIA_PLAY" -> {
                            sessionCoordinator?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_PLAY","keyCode":126}""")
                            mockClient?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_PLAY","keyCode":126}""")
                            Toast.makeText(this, getString(R.string.toast_music_play), Toast.LENGTH_SHORT).show()
                        }
                        "MEDIA_PAUSE" -> {
                            sessionCoordinator?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_PAUSE","keyCode":127}""")
                            mockClient?.sendTouchEvent("""{"type":"STEERING_WHEEL","command":"MEDIA_PAUSE","keyCode":127}""")
                            Toast.makeText(this, getString(R.string.toast_music_pause), Toast.LENGTH_SHORT).show()
                        }
                        "APP_SPOTIFY" -> {
                            sessionCoordinator?.sendTouchEvent("""{"type":"APP_LAUNCH","app":"Spotify"}""")
                            Toast.makeText(this, getString(R.string.toast_open_spotify), Toast.LENGTH_SHORT).show()
                        }
                        "NAVIGATION", "APP_WAZE" -> {
                            sessionCoordinator?.sendTouchEvent("""{"type":"APP_LAUNCH","app":"Waze"}""")
                            Toast.makeText(this, getString(R.string.toast_open_waze), Toast.LENGTH_SHORT).show()
                        }
                        "PHONE_CALL" -> {
                            sessionCoordinator?.sendTouchEvent("""{"type":"APP_LAUNCH","app":"Phone"}""")
                            Toast.makeText(this, getString(R.string.toast_open_phone), Toast.LENGTH_SHORT).show()
                        }
                        "OPEN_BROWSER" -> openBrowser("https://www.google.com")
                        "OPEN_YOUTUBE" -> openBrowser("https://m.youtube.com")
                        "OPEN_SETTINGS" -> showProfileSelectionDialog()
                        "GO_HOME" -> {
                            if (browserOverlay.visibility == View.VISIBLE) {
                                closeBrowser()
                            }
                            Toast.makeText(this, getString(R.string.toast_home), Toast.LENGTH_SHORT).show()
                        }
                        "SYSTEM_NIGHT_MODE" -> {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                            Toast.makeText(this, getString(R.string.toast_night_mode), Toast.LENGTH_SHORT).show()
                        }
                        "SYSTEM_DAY_MODE" -> {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                            Toast.makeText(this, getString(R.string.toast_day_mode), Toast.LENGTH_SHORT).show()
                        }
                        "SUNROOF_OPEN" -> Toast.makeText(this, getString(R.string.toast_sunroof_open), Toast.LENGTH_SHORT).show()
                        "SUNROOF_CLOSE" -> Toast.makeText(this, getString(R.string.toast_sunroof_close), Toast.LENGTH_SHORT).show()
                        "WINDOWS_OPEN" -> Toast.makeText(this, getString(R.string.toast_windows_open), Toast.LENGTH_SHORT).show()
                        "WINDOWS_CLOSE" -> Toast.makeText(this, getString(R.string.toast_windows_close), Toast.LENGTH_SHORT).show()
                        "QUERY_TIME", "QUERY_DATE", "QUERY_GREETING", "QUERY_HOW_ARE_YOU", "QUERY_WHO_ARE_YOU", "QUERY_CAR_STATUS" -> {
                            // Səsli cavab artıq UniversalVoiceAssistant tərəfindən deyildi
                            Toast.makeText(this, "🔮 Aura: $originalText", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(this, "Aura: $originalText", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Avtomobil inteqrasiyaları başladılarkən xəta: ${e.message}")
        }
    }

    private fun showClimateHud(state: VehicleHvacManager.ClimateState) {
        val acStatus = if (state.isAcOn) getString(R.string.hvac_ac_on) else getString(R.string.hvac_ac_off)
        val banner = getString(R.string.hvac_banner, state.targetTemp.toInt(), acStatus, state.fanSpeed)
        Toast.makeText(this, banner, Toast.LENGTH_SHORT).show()
        tvDetail.text = banner
    }

    private fun initSessionCoordinator() {
        try {
            sessionCoordinator = CarPlaySessionCoordinator(
                context = this,
                videoDecoder = videoDecoder,
                audioEngine = audioEngine
            ) { state, message ->
                runOnUiThread {
                    tvDetail.text = message
                    when (state) {
                        CarPlaySessionCoordinator.SessionState.CONNECTED -> {
                            statusOverlay.visibility = View.GONE
                        }
                        CarPlaySessionCoordinator.SessionState.WAITING_FOR_DEVICE -> {
                            statusOverlay.visibility = View.VISIBLE
                            tvStatus.text = getString(R.string.status_universal_connection)
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "SessionCoordinator xətası: ${e.message}")
        }
    }

    private fun initUsbManager() {
        try {
            val savedMode = getSharedPreferences("cartwin_prefs", MODE_PRIVATE).getInt("preferred_phone_mode", 3)
            usbManager = CarPlayUsbManager(
                context = this,
                getVideoDecoder = { videoDecoder },
                getAudioEngine = { audioEngine },
                onAppleDeviceConnected = { device ->
                    runOnUiThread {
                        val devName = device.productName ?: device.deviceName ?: "CarTwin"
                        tvStatus.text = "🟢 $devName"
                        tvDetail.text = getString(R.string.status_preparing_stream)
                        cryptoHandshake.startHandshake()
                    }
                },
                onDeviceDisconnected = {
                    runOnUiThread {
                        statusOverlay.animate().cancel()
                        statusOverlay.alpha = 1f
                        statusOverlay.visibility = View.VISIBLE
                        tvStatus.text = getString(R.string.status_universal_connection)
                        tvDetail.text = getString(R.string.status_waiting_device)
                        cryptoHandshake.reset()
                    }
                },
                onStatusUpdate = { statusMsg ->
                    runOnUiThread {
                        tvDetail.text = statusMsg
                    }
                }
            ).apply {
                preferredPhoneMode = savedMode
                onPlatformDetected = { platformName, icon ->
                    runOnUiThread {
                        tvStatus.text = "$icon $platformName"
                        Toast.makeText(this@MainActivity, getString(R.string.toast_device_connected, icon, platformName), Toast.LENGTH_SHORT).show()
                    }
                }
                onPhoneModelIdentified = { modelName, brand, isAndroid ->
                    runOnUiThread {
                        tvStatus.text = if (isAndroid) "🤖 $modelName (Android Auto)" else "🍏 $modelName (Apple CarPlay)"
                        tvDetail.text = if (isAndroid) {
                            "🤖 $modelName • Android Auto & Aura AI"
                        } else {
                            "🍏 $modelName • Apple CarPlay"
                        }
                        Toast.makeText(this@MainActivity, getString(R.string.toast_device_adapted, modelName), Toast.LENGTH_SHORT).show()

                        // Telefon qoşulan kimi pərdəni dərhal açırıq (qosan kimi acilsin)
                        statusOverlay.animate()
                            .alpha(0f)
                            .setDuration(350)
                            .withEndAction {
                                statusOverlay.visibility = View.GONE
                                statusOverlay.alpha = 1f
                            }
                            .start()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "UsbManager xətası: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        intent ?: return
        if (!::usbManager.isInitialized) return
        val action = intent.action ?: return
        Log.i(TAG, "MainActivity USB Intent daxil oldu: $action")
        when (action) {
            "android.hardware.usb.action.USB_ACCESSORY_ATTACHED" -> {
                val accessory: UsbAccessory? = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY) as? UsbAccessory
                    }
                } catch (_: Throwable) { null }
                accessory?.let { usbManager.handleAccessoryAttached(it) }
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device: UsbDevice? = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                    }
                } catch (_: Throwable) { null }
                device?.let { usbManager.handleDeviceAttached(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            vehicleStateManager.register()
            usbManager.start()
            sessionCoordinator?.startDiscovery(carName = "CarTwin")
        } catch (e: Throwable) {
            Log.e(TAG, "onResume xətası: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            vehicleStateManager.unregister()
            usbManager.stop()
            sessionCoordinator?.stopDiscovery()
        } catch (e: Throwable) {
            Log.e(TAG, "onPause xətası: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && ::steeringWheelManager.isInitialized && steeringWheelManager.handleKeyEvent(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (browserOverlay.visibility == View.VISIBLE) {
            if (webViewBrowser.canGoBack()) {
                webViewBrowser.goBack()
            } else {
                closeBrowser()
            }
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun setupProfile(profile: VehicleProfile) {
        currentProfile = profile
        tvCurrentProfile.text = getString(R.string.profile_label, profile.name)

        try {
            ivTopBarLogo.setImageResource(profile.logoResId)
        } catch (_: Throwable) {
            ivTopBarLogo.setImageResource(R.drawable.cartwin_logo)
        }

        touchManager = TouchManager(
            screenWidth = profile.defaultWidth,
            screenHeight = profile.defaultHeight,
            targetWidth = profile.defaultWidth,
            targetHeight = profile.defaultHeight
        )

        playCommercialIntro(profile)
    }

    private fun playCommercialIntro(profile: VehicleProfile) {
        try {
            try {
                ivCommercialLogo.setImageResource(profile.logoResId)
            } catch (_: Throwable) {
                ivCommercialLogo.setImageResource(R.drawable.cartwin_logo)
            }

            tvCommercialTitle.text = profile.commercialTitle
            tvCommercialTagline.text = profile.commercialTagline
            try {
                tvCommercialTagline.setTextColor(Color.parseColor(profile.accentColorHex))
            } catch (_: Exception) {}

            commercialSplashOverlay.visibility = View.VISIBLE
            commercialSplashOverlay.alpha = 1f

            commercialCenterContainer.scaleX = 0.80f
            commercialCenterContainer.scaleY = 0.80f
            commercialCenterContainer.alpha = 0f

            commercialCenterContainer.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(700)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    commercialCenterContainer.animate()
                        .scaleX(1.04f)
                        .scaleY(1.04f)
                        .setDuration(600)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()

            dismissRunnable?.let { introHandler.removeCallbacks(it) }
            dismissRunnable = Runnable {
                dismissCommercialIntro()
            }
            introHandler.postDelayed(dismissRunnable!!, 2200)
        } catch (e: Throwable) {
            Log.e(TAG, "playCommercialIntro xətası: ${e.message}")
            dismissCommercialIntro()
        }
    }

    private fun dismissCommercialIntro() {
        dismissRunnable?.let { introHandler.removeCallbacks(it) }
        commercialSplashOverlay.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                commercialSplashOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun showProfileSelectionDialog() {
        val options = VehicleProfile.ALL.map { it.name }.toMutableList().apply {
            add(getString(R.string.settings_mode_universal))
            add(getString(R.string.settings_mode_carplay))
            add(getString(R.string.settings_mode_android))
            add(getString(R.string.settings_wireless_adapter))
            add(getString(R.string.settings_language))
            add(getString(R.string.settings_connection_guide))
            add(getString(R.string.settings_check_ota))
            add(getString(R.string.settings_reset))
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_title))
            .setItems(options) { _, which ->
                val vehicleCount = VehicleProfile.ALL.size
                when {
                    which < vehicleCount -> {
                        val selected = VehicleProfile.ALL[which]
                        vehicleDetector.saveProfile(selected)
                        setupProfile(selected)
                        restartAllCarPlayServices()
                        Toast.makeText(this, getString(R.string.toast_profile_saved, selected.name), Toast.LENGTH_SHORT).show()
                    }
                    which == vehicleCount -> {
                        // Universal mode (CarPlay & Android Auto)
                        getSharedPreferences("cartwin_prefs", MODE_PRIVATE).edit().putInt("preferred_phone_mode", 3).apply()
                        if (::usbManager.isInitialized) usbManager.preferredPhoneMode = 3
                        restartAllCarPlayServices()
                        Toast.makeText(this, getString(R.string.toast_universal_mode), Toast.LENGTH_SHORT).show()
                    }
                    which == vehicleCount + 1 -> {
                        // CarPlay only
                        getSharedPreferences("cartwin_prefs", MODE_PRIVATE).edit().putInt("preferred_phone_mode", 2).apply()
                        if (::usbManager.isInitialized) usbManager.preferredPhoneMode = 2
                        restartAllCarPlayServices()
                        Toast.makeText(this, getString(R.string.toast_carplay_mode), Toast.LENGTH_SHORT).show()
                    }
                    which == vehicleCount + 2 -> {
                        // Android Auto only
                        getSharedPreferences("cartwin_prefs", MODE_PRIVATE).edit().putInt("preferred_phone_mode", 1).apply()
                        if (::usbManager.isInitialized) usbManager.preferredPhoneMode = 1
                        restartAllCarPlayServices()
                        Toast.makeText(this, getString(R.string.toast_android_auto_mode), Toast.LENGTH_SHORT).show()
                    }
                    which == vehicleCount + 3 -> {
                        openBrowser("http://192.168.50.2")
                        Toast.makeText(this, "📶 192.168.50.2", Toast.LENGTH_LONG).show()
                    }
                    which == vehicleCount + 4 -> {
                        showLanguageSelectionDialog()
                    }
                    which == vehicleCount + 5 -> {
                        showConnectionGuideDialog()
                    }
                    which == vehicleCount + 6 -> {
                        otaUpdateManager.checkForUpdates(silent = false)
                    }
                    else -> {
                        vehicleDetector.resetSavedProfile()
                        Toast.makeText(this, getString(R.string.settings_reset_done), Toast.LENGTH_SHORT).show()
                        initVehicleDetection()
                        restartAllCarPlayServices()
                    }
                }
            }
            .setNegativeButton(getString(R.string.dialog_close), null)
            .show()
    }

    private fun showLanguageSelectionDialog() {
        val languages = listOf(
            LocaleHelper.LANG_EN to "🇬🇧 English (Default)",
            LocaleHelper.LANG_AZ to "🇦🇿 Azərbaycan dili",
            LocaleHelper.LANG_TR to "🇹🇷 Türkçe",
            LocaleHelper.LANG_RU to "🇷🇺 Русский"
        )
        val currentLang = LocaleHelper.getLanguage(this)
        val currentIndex = languages.indexOfFirst { it.first == currentLang }.let { if (it >= 0) it else 0 }
        val options = languages.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_language_title))
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selectedLang = languages[which].first
                if (selectedLang != currentLang) {
                    LocaleHelper.setLocale(this, selectedLang)
                    voiceAssistant?.setLanguage(selectedLang)
                    dialog.dismiss()
                    Toast.makeText(this, getString(R.string.toast_language_changed, languages[which].second), Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.dialog_close), null)
            .show()
    }

    private fun showConnectionGuideDialog() {
        try {
            val guideView = layoutInflater.inflate(R.layout.dialog_connection_guide, null)
            AlertDialog.Builder(this)
                .setView(guideView)
                .setPositiveButton(getString(R.string.dialog_close), null)
                .show()
        } catch (e: Throwable) {
            Log.e(TAG, "Qoşulma bələdçisi açılarkən xəta: ${e.message}")
        }
    }


    override fun onSurfaceCreated(holder: SurfaceHolder) {
        try {
            videoDecoder = VideoDecoder(holder.surface).apply {
                onFirstFrameRendered = {
                    runOnUiThread {
                        statusOverlay.animate()
                            .alpha(0f)
                            .setDuration(350)
                            .withEndAction {
                                statusOverlay.visibility = View.GONE
                                statusOverlay.alpha = 1f
                            }
                            .start()
                    }
                }
                start(currentProfile.defaultWidth, currentProfile.defaultHeight)
            }
            audioEngine = AudioEngine(this).apply {
                start()
            }
            initSessionCoordinator()
            sessionCoordinator?.startDiscovery(carName = "CarTwin")
        } catch (e: Throwable) {
            Log.e(TAG, "onSurfaceCreated xətası: ${e.message}")
        }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, width: Int, height: Int) {
        touchManager?.updateDimensions(width, height, currentProfile.defaultWidth, currentProfile.defaultHeight)
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
        videoDecoder?.stop()
        audioEngine?.stop()
        mockClient?.disconnect()
    }

    override fun onTouchEventCaptured(event: MotionEvent) {
        val touchData = touchManager?.processTouchEvent(event)
        if (touchData != null) {
            sessionCoordinator?.sendTouchEvent(touchData)
            mockClient?.sendTouchEvent(touchData)
        }

        if (::usbManager.isInitialized && surfaceView.width > 0 && surfaceView.height > 0) {
            val action = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> 14
                MotionEvent.ACTION_MOVE -> 15
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 16
                else -> null
            }
            if (action != null) {
                val normX = (event.x / surfaceView.width).coerceIn(0f, 1f)
                val normY = (event.y / surfaceView.height).coerceIn(0f, 1f)
                usbManager.sendTouch(action, normX, normY)
            }
        }
    }

    private fun startMockConnection() {
        val prefs = getSharedPreferences("cartwin_prefs", MODE_PRIVATE)
        val lastIp = prefs.getString("last_mock_ip", "10.10.20.215") ?: "10.10.20.215"

        val input = EditText(this).apply {
            setText(lastIp)
            hint = "Kompüterin Wi-Fi IP ünvanı (məs: 10.10.20.215)"
            setSingleLine(true)
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("🧪 PC Simulyatoruna Qoşul")
            .setMessage("Kompüterinizdə server aktivdirsə (Port: 9998), kompüterin yerli Wi-Fi IP ünvanını daxil edin:")
            .setView(input)
            .setPositiveButton("Qoşul") { _, _ ->
                val targetHost = input.text.toString().trim()
                if (targetHost.isNotEmpty()) {
                    prefs.edit().putString("last_mock_ip", targetHost).apply()
                    connectToMockServer(targetHost, 9998)
                }
            }
            .setNegativeButton("Ləğv et", null)
            .show()
    }

    private fun connectToMockServer(targetHost: String, targetPort: Int) {
        tvStatus.text = "Simulyatora qoşulur: $targetHost:$targetPort…"
        try {
            mockClient?.disconnect()
            mockClient = MockCarPlayClient(
                host = targetHost,
                port = targetPort,
                videoDecoder = videoDecoder,
                audioEngine = audioEngine
            ).apply {
                onConnectionStateChanged = { connected ->
                    runOnUiThread {
                        if (connected) {
                            statusOverlay.visibility = View.GONE
                            Toast.makeText(this@MainActivity, getString(R.string.toast_simulator_connected), Toast.LENGTH_SHORT).show()
                        } else {
                            statusOverlay.visibility = View.VISIBLE
                            tvStatus.text = getString(R.string.status_universal_connection)
                        }
                    }
                }
                connect()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Mock bağlantı xətası: ${e.message}")
            Toast.makeText(this, getString(R.string.toast_simulator_not_found, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissRunnable?.let { introHandler.removeCallbacks(it) }
        try { videoDecoder?.stop() } catch (_: Throwable) {}
        try { audioEngine?.stop() } catch (_: Throwable) {}
        try { mockClient?.disconnect() } catch (_: Throwable) {}
        try { sessionCoordinator?.stopDiscovery() } catch (_: Throwable) {}
        try { voiceAssistant?.destroy() } catch (_: Throwable) {}
        try { usbManager.stop() } catch (_: Throwable) {}
        try { webViewBrowser.destroy() } catch (_: Throwable) {}
    }
}
