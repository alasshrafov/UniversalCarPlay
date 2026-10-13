package com.universal.carplay

import android.os.Bundle
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
import com.universal.carplay.engine.AudioEngine
import com.universal.carplay.engine.TouchManager
import com.universal.carplay.engine.VideoDecoder
import com.universal.carplay.profiles.VehicleProfile
import com.universal.carplay.protocol.MockCarPlayClient
import com.universal.carplay.ui.CarPlaySurfaceView

class MainActivity : AppCompatActivity(), CarPlaySurfaceView.SurfaceListener {

    private lateinit var surfaceView: CarPlaySurfaceView
    private lateinit var statusOverlay: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvCurrentProfile: TextView
    private lateinit var btnSettings: Button
    private lateinit var btnConnectMock: Button

    private var videoDecoder: VideoDecoder? = null
    private var audioEngine: AudioEngine? = null
    private var touchManager: TouchManager? = null
    private var mockClient: MockCarPlayClient? = null

    private var currentProfile: VehicleProfile = VehicleProfile.Universal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Avtomobil üçün ekranın sönməməsini təmin edirik
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContentView(R.layout.activity_main)

        initViews()
        setupProfile(VehicleProfile.Universal)
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
                setupProfile(selected)
                Toast.makeText(this, "${selected.name} aktivləşdirildi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Bağla", null)
            .show()
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
        // Video və səs mühərriklərini başladırıq
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
            host = "10.0.2.2", // Emulyator üçün kompüter ünvanı
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

    /**
     * Bütün Android naviqasiya və status zolaqlarını gizlədir (Tam ekran / Immersive mode)
     */
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
    }
}
