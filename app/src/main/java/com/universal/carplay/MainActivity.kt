package com.universal.carplay

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.universal.carplay.protocol.MockCarPlayClient
import com.universal.carplay.ui.CarPlaySurfaceView
import com.universal.carplay.voice.UniversalVoiceAssistant
import java.net.URLEncoder

class MainActivity : AppCompatActivity(), CarPlaySurfaceView.SurfaceListener {

    private lateinit var surfaceView: CarPlaySurfaceView
    private lateinit var statusOverlay: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvCurrentProfile: TextView
    private lateinit var ivTopBarLogo: ImageView
    private lateinit var btnSettings: Button
    private lateinit var btnReplayIntro: Button
    private lateinit var btnConnectMock: Button

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
    private lateinit var voiceAssistant: UniversalVoiceAssistant
    private lateinit var vehicleHvacManager: VehicleHvacManager
    private lateinit var usbManager: CarPlayUsbManager
    private val cryptoHandshake = CarPlayCryptoHandshake()

    private var currentProfile: VehicleProfile = VehicleProfile.Universal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Avtomobil üçün ekranın sönməməsini təmin edirik
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContentView(R.layout.activity_main)

        initViews()
        initBrowser()
        initVehicleDetection()
        initCarIntegrations()
        initSessionCoordinator()
        initUsbManager()
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

        btnSettings.setOnClickListener {
            showProfileSelectionDialog()
        }

        btnConnectMock.setOnClickListener {
            startMockConnection()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initBrowser() {
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

    /**
     * Avtomobil modelini 100% avtomatik təyin edir (BYD, Changan, Li Auto, Zeekr və ya Universal)
     */
    private fun initVehicleDetection() {
        vehicleDetector = VehicleDetector(this)
        val detectedProfile = vehicleDetector.detectProfile()
        setupProfile(detectedProfile)
    }

    private fun initCarIntegrations() {
        // 1. Sükan Düymələri
        steeringWheelManager = SteeringWheelManager { commandJson ->
            mockClient?.sendTouchEvent(commandJson)
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

        // 4. Avtomobil İqlim və Kondisioner Meneceri (CAN-Bus)
        vehicleHvacManager = VehicleHvacManager(this) { state ->
            runOnUiThread {
                showClimateHud(state)
            }
        }

        // 5. Bütün Dilləri (Azərbaycan) Dəstəkləyən Universal AI Səs Köməkçisi
        voiceAssistant = UniversalVoiceAssistant(this) { commandType, originalText, param ->
            runOnUiThread {
                when (commandType) {
                    "CLIMATE_SET_TEMP" -> {
                        val target = param ?: 22.0f
                        vehicleHvacManager.setTemperature(target)
                    }
                    "CLIMATE_AC_OFF" -> vehicleHvacManager.setAcPower(false)
                    "CLIMATE_AC_ON" -> vehicleHvacManager.setAcPower(true)
                    "CLIMATE_TEMP_UP" -> {
                        val current = vehicleHvacManager.getClimateState().targetTemp
                        vehicleHvacManager.setTemperature(current + 1.0f)
                    }
                    "CLIMATE_TEMP_DOWN" -> {
                        val current = vehicleHvacManager.getClimateState().targetTemp
                        vehicleHvacManager.setTemperature(current - 1.0f)
                    }
                    "OPEN_BROWSER" -> openBrowser("https://www.google.com")
                    "OPEN_YOUTUBE" -> openBrowser("https://m.youtube.com")
                    else -> Toast.makeText(this, "AI Əmr: $commandType (\"$originalText\")", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showClimateHud(state: VehicleHvacManager.ClimateState) {
        val banner = "❄️ İqlim: ${state.targetTemp.toInt()}°C • ${if (state.isAcOn) "AC AÇIQ" else "AC BAĞLI"} • Fan: ${state.fanSpeed}/7"
        Toast.makeText(this, banner, Toast.LENGTH_SHORT).show()
        tvDetail.text = banner
    }

    private fun initSessionCoordinator() {
        sessionCoordinator = CarPlaySessionCoordinator(this) { state, message ->
            runOnUiThread {
                tvDetail.text = message
                when (state) {
                    CarPlaySessionCoordinator.SessionState.CONNECTED -> {
                        statusOverlay.visibility = View.GONE
                    }
                    CarPlaySessionCoordinator.SessionState.WAITING_FOR_DEVICE -> {
                        statusOverlay.visibility = View.VISIBLE
                        tvStatus.text = "iPhone Axtarılır…"
                    }
                    else -> {}
                }
            }
        }
    }

    private fun initUsbManager() {
        usbManager = CarPlayUsbManager(
            context = this,
            onAppleDeviceConnected = { device ->
                runOnUiThread {
                    Toast.makeText(this, "Simli Apple CarPlay Qoşuldu: ${device.deviceName}", Toast.LENGTH_LONG).show()
                    statusOverlay.visibility = View.GONE
                    cryptoHandshake.startHandshake()
                }
            },
            onDeviceDisconnected = {
                runOnUiThread {
                    statusOverlay.visibility = View.VISIBLE
                    tvStatus.text = getString(R.string.waiting_for_connection)
                    cryptoHandshake.reset()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        vehicleStateManager.register()
        usbManager.start()
        sessionCoordinator?.startDiscovery(carName = "CarTwin-${currentProfile.name}")
    }

    override fun onPause() {
        super.onPause()
        vehicleStateManager.unregister()
        usbManager.stop()
        sessionCoordinator?.stopDiscovery()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && steeringWheelManager.handleKeyEvent(keyCode, event)) {
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
        tvCurrentProfile.text = "CarTwin • ${profile.name}"
        ivTopBarLogo.setImageResource(profile.logoResId)

        touchManager = TouchManager(
            screenWidth = profile.defaultWidth,
            screenHeight = profile.defaultHeight,
            targetWidth = profile.defaultWidth,
            targetHeight = profile.defaultHeight
        )

        playCommercialIntro(profile)
    }

    /**
     * Reklam Çarxlarındakı kimi Zərif və Dinamik Avtomobil Açılış Şousu
     */
    private fun playCommercialIntro(profile: VehicleProfile) {
        ivCommercialLogo.setImageResource(profile.logoResId)
        tvCommercialTitle.text = profile.commercialTitle
        tvCommercialTagline.text = profile.commercialTagline
        try {
            tvCommercialTagline.setTextColor(Color.parseColor(profile.accentColorHex))
        } catch (_: Exception) {}

        commercialSplashOverlay.visibility = View.VISIBLE
        commercialSplashOverlay.alpha = 1f

        // Animasiya: Miqyas və Şəffaflıq artımı
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
                // Metalik nəfəs alma effekti
                commercialCenterContainer.animate()
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(600)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()

        // 2.2 saniyə sonra CarPlay ekranına axıcı keçid
        dismissRunnable?.let { introHandler.removeCallbacks(it) }
        dismissRunnable = Runnable {
            dismissCommercialIntro()
        }
        introHandler.postDelayed(dismissRunnable!!, 2200)
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
        val profileNames = VehicleProfile.ALL.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Avtomobil Profilini Seçin")
            .setItems(profileNames) { _, which ->
                val selected = VehicleProfile.ALL[which]
                vehicleDetector.saveProfile(selected)
                setupProfile(selected)
                Toast.makeText(this, "${selected.name} aktivləşdirildi və yadda saxlanıldı", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Bağla", null)
            .show()
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
        videoDecoder = VideoDecoder(holder.surface).apply {
            start(currentProfile.defaultWidth, currentProfile.defaultHeight)
        }
        audioEngine = AudioEngine(this).apply {
            start()
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
            mockClient?.sendTouchEvent(touchData)
        }
    }

    private fun startMockConnection() {
        tvStatus.text = "Qoşulur…"
        mockClient = MockCarPlayClient(
            host = "10.0.2.2",
            port = 9999,
            videoDecoder = videoDecoder,
            audioEngine = audioEngine
        ).apply {
            onConnectionStateChanged = { connected ->
                runOnUiThread {
                    if (connected) {
                        statusOverlay.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "CarPlay Canlı Yayım Başladı!", Toast.LENGTH_SHORT).show()
                    } else {
                        statusOverlay.visibility = View.VISIBLE
                        tvStatus.text = getString(R.string.waiting_for_connection)
                    }
                }
            }
            connect()
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
        videoDecoder?.stop()
        audioEngine?.stop()
        mockClient?.disconnect()
        sessionCoordinator?.stopDiscovery()
        voiceAssistant.destroy()
        usbManager.stop()
        webViewBrowser.destroy()
    }
}
