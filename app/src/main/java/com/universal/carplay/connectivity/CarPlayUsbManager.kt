package com.universal.carplay.connectivity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.universal.carplay.engine.AudioEngine
import com.universal.carplay.engine.VideoDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tam Həcmli Carlinkit / AutoKit & Apple MFi USB Protokol Sürücüsü (Driver)
 * 
 * Avtomobilin USB portuna qoşulan Carlinkit (0x1314) və ya Apple (0x05AC)
 * adapterləri ilə 0x55aa55aa protokolu üzərindən əlaqə qurur,
 * H.264 video və PCM audio axınını birbaşa dekoderlərə ötürür.
 */
class CarPlayUsbManager(
    private val context: Context,
    private val getVideoDecoder: () -> VideoDecoder?,
    private val getAudioEngine: () -> AudioEngine?,
    private val onAppleDeviceConnected: (UsbDevice) -> Unit,
    private val onDeviceDisconnected: () -> Unit,
    private val onStatusUpdate: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "CarPlay_USB"
        private const val ACTION_USB_PERMISSION = "com.universal.carplay.USB_PERMISSION"

        // Rəsmi Carlinkit & Apple Vendor / Product ID-ləri
        private const val APPLE_VENDOR_ID = 0x05AC
        private const val CARLINKIT_VENDOR_ID = 0x1314

        // Protokol Sabitləri
        private const val MAGIC_NUMBER = 0x55aa55aa.toInt()

        // Mesaj Növləri (MessageType)
        const val MSG_OPEN = 0x01
        const val MSG_PLUGGED = 0x02
        const val MSG_PHASE = 0x03
        const val MSG_UNPLUGGED = 0x04
        const val MSG_TOUCH = 0x05
        const val MSG_VIDEO = 0x06
        const val MSG_AUDIO = 0x07
        const val MSG_COMMAND = 0x08
        const val MSG_HEARTBEAT = 0xaa
    }

    private val usbManager = try {
        context.getSystemService(Context.USB_SERVICE) as? UsbManager
    } catch (_: Throwable) { null }

    private var isRegistered = false
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    private var currentDevice: UsbDevice? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isDongleOpen = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return

            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                        }
                    } catch (_: Throwable) { null }
                    device?.let { handleDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.i(TAG, "USB Cihaz ayrıldı.")
                    closeConnection()
                    onDeviceDisconnected()
                    onStatusUpdate?.invoke("USB: Cihaz ayrıldı. Gözlənilir...")
                }
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                        }
                    } catch (_: Throwable) { null }
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (permissionGranted && device != null) {
                        Log.i(TAG, "USB İcazəsi verildi: ${device.deviceName}")
                        startDongleCommunication(device)
                    } else {
                        onStatusUpdate?.invoke("USB: İcazə verilmədi.")
                    }
                }
            }
        }
    }

    fun start() {
        if (isRegistered || usbManager == null) return

        try {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            isRegistered = true
            Log.i(TAG, "USB Meneceri aktivləşdirildi")

            checkConnectedDevices()
        } catch (e: Throwable) {
            Log.e(TAG, "USB menecer başlarkən xəta: ${e.message}")
        }
    }

    private fun checkConnectedDevices() {
        try {
            val deviceList = usbManager?.deviceList ?: return
            for (device in deviceList.values) {
                if (isCarPlayDevice(device)) {
                    handleDeviceAttached(device)
                    break
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Cihazlar yoxlanarkən xəta: ${e.message}")
        }
    }

    private fun handleDeviceAttached(device: UsbDevice) {
        try {
            if (isCarPlayDevice(device)) {
                currentDevice = device
                val vidHex = Integer.toHexString(device.vendorId)
                val pidHex = Integer.toHexString(device.productId)
                Log.i(TAG, "CarPlay / Carlinkit USB Cihazı qoşuldu! VID=0x$vidHex, PID=0x$pidHex")
                onStatusUpdate?.invoke("USB: Carlinkit Tapıldı (VID:0x$vidHex) — Aktivləşdirilir…")

                if (usbManager?.hasPermission(device) == true) {
                    startDongleCommunication(device)
                } else {
                    requestUsbPermission(device)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "handleDeviceAttached xətası: ${e.message}")
        }
    }

    private fun isCarPlayDevice(device: UsbDevice): Boolean {
        val vid = device.vendorId
        val pid = device.productId
        val name = (device.productName ?: "") + " " + (device.manufacturerName ?: "")
        return vid == CARLINKIT_VENDOR_ID ||
               vid == APPLE_VENDOR_ID ||
               vid == 0x1520 || vid == 0x1521 ||
               pid == 0x1520 || pid == 0x1521 || pid == 0x1522 || pid == 0x1523 ||
               name.contains("Carlinkit", ignoreCase = true) ||
               name.contains("AutoKit", ignoreCase = true) ||
               name.contains("CarPlay", ignoreCase = true)
    }

    private fun requestUsbPermission(device: UsbDevice) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags
            )
            usbManager?.requestPermission(device, permissionIntent)
            Log.i(TAG, "USB icazə sorğusu göndərildi: ${device.deviceName}")
            onStatusUpdate?.invoke("USB: Avtomobil ekranında 'İcazə Ver' təsdiqləyin")
        } catch (e: Throwable) {
            Log.e(TAG, "USB icazəsi istənilərkən xəta: ${e.message}")
        }
    }

    /**
     * Carlinkit Dongle ilə daxili rabitəni və H.264 axınını başladır
     */
    private fun startDongleCommunication(device: UsbDevice) {
        try {
            closeConnection()

            val connection = usbManager?.openDevice(device) ?: run {
                Log.e(TAG, "USB cihaz açıla bilmədi (openDevice null)")
                onStatusUpdate?.invoke("USB: Adapter açıla bilmədi")
                return
            }

            // Uyğun Bulk interfeysini tapırıq
            var matchedInterface: UsbInterface? = null
            var matchedIn: UsbEndpoint? = null
            var matchedOut: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                var epIn: UsbEndpoint? = null
                var epOut: UsbEndpoint? = null

                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
                        else if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
                    }
                }

                if (epIn != null && epOut != null) {
                    matchedInterface = iface
                    matchedIn = epIn
                    matchedOut = epOut
                    break
                }
            }

            if (matchedInterface == null || matchedIn == null || matchedOut == null) {
                Log.e(TAG, "Bulk endpoint-lər tapılmadı!")
                onStatusUpdate?.invoke("USB: Bulk portlar tapılmadı")
                connection.close()
                return
            }

            connection.claimInterface(matchedInterface, true)
            usbConnection = connection
            usbInterface = matchedInterface
            inEndpoint = matchedIn
            outEndpoint = matchedOut
            isDongleOpen = true

            Log.i(TAG, "Carlinkit USB bağlantısı quruldu! Interfeys #${matchedInterface.id}")
            onStatusUpdate?.invoke("USB: Carlinkit Aktivdir. Açılış əmri göndərilir…")

            // 1. Açılış (Open) paketini göndəririk (1920x1080, 30fps, H.264, CarPlay)
            sendOpenPacket(1920, 1080, 30)

            // 2. Video Fokus (500) və Audio Rejimi (23) göndəririk
            sendCommand(500) // requestVideoFocus
            sendCommand(23)  // audioTransferOff (dongle streams audio over USB)

            // 3. Ürək döyüntüsü (Heartbeat) dövrünü başladırıq (hər 1 saniyədən bir)
            startHeartbeat()

            // 4. Qırılmaz və sinxronlaşan stream oxuma dövrünü başladırıq
            startPacketReader()

            onAppleDeviceConnected(device)
        } catch (e: Throwable) {
            Log.e(TAG, "startDongleCommunication xətası: ${e.message}", e)
            onStatusUpdate?.invoke("USB Xətası: ${e.message}")
        }
    }

    private fun sendOpenPacket(width: Int, height: Int, fps: Int) {
        val payload = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(width)
            putInt(height)
            putInt(fps)
            putInt(5) // format: 5 (H.264)
            putInt(49152) // packetMax
            putInt(2) // iBoxVersion
            putInt(2) // phoneWorkMode: 2 (CarPlay)
        }.array()

        sendPacket(MSG_OPEN, payload)
        Log.i(TAG, "Carlinkit Open paketi göndərildi (${width}x${height} @ ${fps}fps)")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isDongleOpen) {
                delay(1000)
                sendPacket(MSG_HEARTBEAT, ByteArray(0))
            }
        }
    }

    private fun startPacketReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val rawBuffer = ByteArray(32 * 1024)
            val streamAccumulator = ByteArrayOutputStream()

            while (isActive && isDongleOpen && usbConnection != null && inEndpoint != null) {
                try {
                    val read = usbConnection!!.bulkTransfer(inEndpoint, rawBuffer, rawBuffer.size, 1000)
                    if (read > 0) {
                        synchronized(streamAccumulator) {
                            streamAccumulator.write(rawBuffer, 0, read)
                        }
                        parseAccumulatedPackets(streamAccumulator)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "USB oxuma xətası: ${e.message}")
                }
            }
        }
    }

    private fun parseAccumulatedPackets(accumulator: ByteArrayOutputStream) {
        synchronized(accumulator) {
            var data = accumulator.toByteArray()

            while (data.size >= 16) {
                // 0x55aa55aa Magic nömrəsini axtarırıq (Little Endian)
                var syncIndex = -1
                for (i in 0..data.size - 4) {
                    val b0 = data[i].toInt() and 0xFF
                    val b1 = data[i + 1].toInt() and 0xFF
                    val b2 = data[i + 2].toInt() and 0xFF
                    val b3 = data[i + 3].toInt() and 0xFF
                    val intVal = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    if (intVal == MAGIC_NUMBER) {
                        syncIndex = i
                        break
                    }
                }

                if (syncIndex == -1) {
                    // Paket başlığı tapılmadı, son 3 baytı saxlayıb qalanını atırıq
                    accumulator.reset()
                    if (data.size >= 3) {
                        accumulator.write(data, data.size - 3, 3)
                    }
                    return
                }

                if (syncIndex > 0) {
                    // Magic-dən əvvəlki natəmiz baytları kənarlaşdırırıq
                    val trimmed = ByteArray(data.size - syncIndex)
                    System.arraycopy(data, syncIndex, trimmed, 0, trimmed.size)
                    data = trimmed
                }

                if (data.size < 16) {
                    accumulator.reset()
                    accumulator.write(data)
                    return
                }

                val bb = ByteBuffer.wrap(data, 0, 16).order(ByteOrder.LITTLE_ENDIAN)
                val magic = bb.int
                val length = bb.int
                val type = bb.int
                val typeCheck = bb.int

                val expectedCheck = (type xor -1) and 0xffffffff.toInt()
                if (typeCheck != expectedCheck || length < 0 || length > 4_000_000) {
                    // Yanlış magic uyğunluğu: 1 bayt irəliləyib təkrar axtarırıq
                    val trimmed = ByteArray(data.size - 1)
                    System.arraycopy(data, 1, trimmed, 0, trimmed.size)
                    data = trimmed
                    continue
                }

                val totalPacketSize = 16 + length
                if (data.size < totalPacketSize) {
                    // Paketin yükü hələ tam gəlməyib, növbəti USB oxunuşunu gözləyirik
                    accumulator.reset()
                    accumulator.write(data)
                    return
                }

                // Bütöv paket hazırdır!
                val payload = ByteArray(length)
                if (length > 0) {
                    System.arraycopy(data, 16, payload, 0, length)
                }

                val remainingSize = data.size - totalPacketSize
                val remaining = ByteArray(remainingSize)
                if (remainingSize > 0) {
                    System.arraycopy(data, totalPacketSize, remaining, 0, remainingSize)
                }
                data = remaining

                accumulator.reset()
                accumulator.write(data)

                handleIncomingPacket(type, payload)
            }

            accumulator.reset()
            if (data.isNotEmpty()) {
                accumulator.write(data)
            }
        }
    }

    private fun handleIncomingPacket(type: Int, payload: ByteArray) {
        when (type) {
            MSG_PLUGGED -> {
                Log.i(TAG, "Carlinkit: iPhone qoşuldu!")
                currentDevice?.let { onAppleDeviceConnected(it) }
                onStatusUpdate?.invoke("USB: iPhone qoşuldu! Görüntü aktivdir.")

                // iPhone qoşulan kimi dərhal Video Kadr (SPS/PPS I-frame) tələb edirik
                sendCommand(12)  // frame request
                sendCommand(500) // requestVideoFocus
            }
            MSG_UNPLUGGED -> {
                Log.i(TAG, "Carlinkit: iPhone ayrıldı.")
                onDeviceDisconnected()
                onStatusUpdate?.invoke("USB: iPhone ayrıldı. Kabeli yenidən taxın.")
            }
            MSG_VIDEO -> {
                // Video başlığı 20 baytdır: [width:4][height:4][flags:4][length:4][unknown:4] + [H.264 NAL]
                if (payload.size > 20) {
                    val nalOffset = 20
                    val nalLength = payload.size - nalOffset
                    getVideoDecoder()?.feedFrame(payload, nalOffset, nalLength, System.nanoTime() / 1000)
                }
            }
            MSG_AUDIO -> {
                // Audio başlığı 12 baytdır: [decodeType:4][volume:4][audioType:4] + [PCM data]
                if (payload.size > 12) {
                    val pcmOffset = 12
                    val pcmLength = payload.size - pcmOffset
                    getAudioEngine()?.writeMusic(payload, pcmOffset, pcmLength)
                }
            }
            else -> {
                // Digər idarəetmə mesajları
            }
        }
    }

    /**
     * Ekrana toxunmanı Carlinkit adapterinə göndərir
     */
    fun sendTouch(action: Int, x: Float, y: Float) {
        if (!isDongleOpen || usbConnection == null || outEndpoint == null) return

        val clampedX = (x * 10000).toInt().coerceIn(0, 10000)
        val clampedY = (y * 10000).toInt().coerceIn(0, 10000)

        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(action) // 14: Down, 15: Move, 16: Up
            putInt(clampedX)
            putInt(clampedY)
            putInt(0) // flags
        }.array()

        sendPacket(MSG_TOUCH, payload)
    }

    /**
     * Sükan və ya multimedia düyməsini adapterə ötürür
     */
    fun sendCommand(commandId: Int) {
        if (!isDongleOpen || usbConnection == null || outEndpoint == null) return

        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(commandId)
        }.array()

        sendPacket(MSG_COMMAND, payload)
    }

    private fun sendPacket(type: Int, payload: ByteArray) {
        val conn = usbConnection ?: return
        val ep = outEndpoint ?: return

        try {
            val length = payload.size
            val typeCheck = (type xor -1) and 0xffffffff.toInt()

            val packet = ByteBuffer.allocate(16 + length).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(MAGIC_NUMBER)
                putInt(length)
                putInt(type)
                putInt(typeCheck)
                put(payload)
            }.array()

            conn.bulkTransfer(ep, packet, packet.size, 1000)
        } catch (e: Throwable) {
            Log.e(TAG, "sendPacket xətası: ${e.message}")
        }
    }

    private fun closeConnection() {
        isDongleOpen = false
        readerJob?.cancel()
        heartbeatJob?.cancel()

        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Throwable) {}

        usbConnection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
    }

    fun stop() {
        closeConnection()
        if (!isRegistered) return
        try {
            context.unregisterReceiver(usbReceiver)
            isRegistered = false
            Log.i(TAG, "USB Meneceri dayandırıldı")
        } catch (e: Throwable) {
            Log.e(TAG, "USB menecer dayandırılarkən xəta: ${e.message}")
        }
    }
}
