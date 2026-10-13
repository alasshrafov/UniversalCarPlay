package com.universal.carplay

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
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

class MainActivity : AppCompatActivity(), CarPlaySurfaceView.SurfaceListener {

    private lateinit var surfaceView: CarPlaySurfaceView
    private lateinit var statusOverlay: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvCurrentProfile: TextView
    private lateinit var btnSettings: Button
    private lateinit var btnConnectMock: Button

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
        btnSettings = findViewById(R.id.btnSettings)
        btnConnectMock = findViewById(R.id.btnConnectMock)

        surfaceView.setSurfaceListener(this)

        btnSettings.setOnClickListener {
            showProfileSelectionDialog()
        }

        btnConnectMock.setOnClickListener {
            startMockConnection()
        }
    }

    /**
     * Avtomobil modelini 100% avtomatik təyin edir (BYD, Changan, Li Auto və ya Universal)
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

        // 4. Bütün Dilləri Dəstəkləyən Universal AI Səs Köməkçisi
        voiceAssistant = UniversalVoiceAssistant(this) { commandType, originalText ->
            runOnUiThread {
                Toast.makeText(this, "AI Əmr: $commandType (\"$originalText\")", Toast.LENGTH_SHORT).show()
            }
        }
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
        sessionCoordinator?.startDiscovery(carName = "CarPlay-${currentProfile.name}")
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

    private fun setupProfile(profile: VehicleProfile) {
        currentProfile = profile
        tvCurrentProfile.text = "Profil: ${profile.name} (${profile.defaultWidth}x${profile.defaultHeight})"

        touchManager = TouchManager(
            screenWidth = profile.defaultWidth,
            screenHeight = profile.defaultHeight,
            targetWidth = profile.defaultWidth,
            targetHeight = profile.defaultHeight
        )
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
        videoDecoder?.stop()
        audioEngine?.stop()
        mockClient?.disconnect()
        sessionCoordinator?.stopDiscovery()
        voiceAssistant.destroy()
        usbManager.stop()
    }
}
