package com.universal.carplay.connectivity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.universal.carplay.R
import com.universal.carplay.engine.AudioEngine
import com.universal.carplay.engine.VideoDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CarTwin 2.0 Tam Həcmli Universal USB Meneceri
 * 
 * 1. Android Telefonlar: AOA 2.0 (Android Open Accessory) protokolu ilə birbaşa Type-C kabel ilə qoşulma.
 * 2. Apple iPhone: Birbaşa Apple MFi Vendor Switch (0x52) və CDC-NCM şəbəkəsi.
 * 3. Carlinkit / AutoKit: Fiziki CPC200 adapterlər üçün 0x55aa55aa binar protokolu.
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
        private const val TAG = "CarTwin_USB"
        private const val ACTION_USB_PERMISSION = "com.universal.carplay.USB_PERMISSION"

        // Rəsmi Vendor ID-ləri
        private const val APPLE_VENDOR_ID = 0x05AC
        private const val CARLINKIT_VENDOR_ID = 0x1314
        private const val GOOGLE_AOA_VENDOR_ID = 0x18D1
        private const val SAMSUNG_VENDOR_ID = 0x04E8
        private const val XIAOMI_VENDOR_ID = 0x2717
        private const val HUAWEI_VENDOR_ID = 0x12D1
        private const val HONOR_VENDOR_ID = 0x33BB
        private const val HONOR_TERMINAL_VENDOR_ID = 0x329B
        private const val OPPO_VENDOR_ID = 0x22D9
        private const val VIVO_VENDOR_ID = 0x2CA3
        private const val ONEPLUS_VENDOR_ID = 0x2A70

        // Carlinkit Protokol Sabitləri
        private const val MAGIC_NUMBER = 0x55aa55aa.toInt()

        // Mesaj Növləri (Dongle üçün)
        const val MSG_OPEN = 0x01
        const val MSG_PLUGGED = 0x02
        const val MSG_PHASE = 0x03
        const val MSG_UNPLUGGED = 0x04
        const val MSG_TOUCH = 0x05
        const val MSG_VIDEO = 0x06
        const val MSG_AUDIO = 0x07
        const val MSG_COMMAND = 0x08
        const val MSG_HEARTBEAT = 0xaa

        // Telefon Platformaları
        const val PHONE_TYPE_UNKNOWN = 0
        const val PHONE_TYPE_CARPLAY = 1
        const val PHONE_TYPE_ANDROID_AUTO = 2
        const val PHONE_TYPE_HICAR = 3
        const val PHONE_TYPE_CARLIFE = 4
    }

    var currentPhoneType: Int = PHONE_TYPE_UNKNOWN
        private set

    var currentPhoneModel: String = "Naməlum Cihaz"
        private set

    private var previousIdentifiedBrand: String = ""
    private var previousIdentifiedModel: String = ""

    var preferredPhoneMode: Int = 3 // 3: Universal, 2: Apple CarPlay, 1: Android Auto

    var onPlatformDetected: ((platformName: String, icon: String) -> Unit)? = null
    var onPhoneModelIdentified: ((modelName: String, brand: String, isAndroid: Boolean) -> Unit)? = null

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

    @Volatile
    private var isAoaMode = false

    @Volatile
    private var isAoaSwitching = false
    private var aoaSwitchTimestamp = 0L

    @Volatile
    private var isAppleSwitching = false
    private var appleSwitchTimestamp = 0L

    private var currentAccessory: UsbAccessory? = null
    private var accessoryParcelFileDescriptor: ParcelFileDescriptor? = null
    private var accessoryInputStream: FileInputStream? = null
    private var accessoryOutputStream: FileOutputStream? = null

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
                    val detachedDevice: UsbDevice? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                        }
                    } catch (_: Throwable) { null }

                    if (detachedDevice != null && currentDevice != null && detachedDevice.deviceId != currentDevice?.deviceId) {
                        Log.d(TAG, "Başqa bir USB cihaz ayrıldı (${detachedDevice.deviceName}), əsas bağlantıya toxunulmur.")
                        return
                    }

                    val isRecentlySwitched = (isAoaSwitching && (System.currentTimeMillis() - aoaSwitchTimestamp < 6000L)) ||
                                             (isAppleSwitching && (System.currentTimeMillis() - appleSwitchTimestamp < 6000L))
                    if (isRecentlySwitched) {
                        val msg = if (isAppleSwitching) "🍏 Apple CarPlay: Re-enumeration gözlənilir…" else "🤖 Android Auto: Re-enumeration gözlənilir…"
                        Log.i(TAG, "Re-enumeration zamanı cihaz ayrılması gözləniləndir. UI 'Cihaz ayrıldı' göstərmir.")
                        onStatusUpdate?.invoke(msg)
                        return
                    }
                    Log.i(TAG, "USB Cihaz ayrıldı.")
                    isAoaSwitching = false
                    isAppleSwitching = false
                    closeConnection()
                    onDeviceDisconnected()
                    onStatusUpdate?.invoke("USB: Cihaz ayrıldı. Apple və ya Android telefonunuzu qoşun.")
                }
                "android.hardware.usb.action.USB_ACCESSORY_ATTACHED" -> {
                    val accessory: UsbAccessory? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY) as? UsbAccessory
                        }
                    } catch (_: Throwable) { null }
                    accessory?.let { handleAccessoryAttached(it) }
                }
                "android.hardware.usb.action.USB_ACCESSORY_DETACHED" -> {
                    Log.i(TAG, "USB Aksesuar ayrıldı.")
                    isAoaSwitching = false
                    closeConnection()
                    onDeviceDisconnected()
                    onStatusUpdate?.invoke("USB: Cihaz ayrıldı. Apple və ya Android telefonunuzu qoşun.")
                }
                ACTION_USB_PERMISSION -> {
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device: UsbDevice? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                        }
                    } catch (_: Throwable) { null } ?: currentDevice

                    val accessory: UsbAccessory? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY) as? UsbAccessory
                        }
                    } catch (_: Throwable) { null } ?: currentAccessory

                    if (permissionGranted) {
                        if (accessory != null) {
                            Log.i(TAG, "UsbAccessory İcazəsi verildi: ${accessory.description}")
                            startAccessoryCommunication(accessory)
                        } else if (device != null) {
                            Log.i(TAG, "USB İcazəsi verildi: ${device.deviceName}")
                            routeDeviceConnection(device)
                        }
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
                addAction("android.hardware.usb.action.USB_ACCESSORY_ATTACHED")
                addAction("android.hardware.usb.action.USB_ACCESSORY_DETACHED")
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
            // 1. Mövcud UsbAccessory rejimində olan cihazları yoxlayırıq
            val accessoryList = usbManager?.accessoryList
            if (!accessoryList.isNullOrEmpty()) {
                val acc = accessoryList[0]
                Log.i(TAG, "Mövcud UsbAccessory aşkarlandı: ${acc.description}")
                handleAccessoryAttached(acc)
                return
            }

            // 2. UsbDevice siyahısını yoxlayırıq
            val deviceList = usbManager?.deviceList ?: return

            // Prioritet 1: Artıq Google AOA rejimində olan cihazlar
            for (device in deviceList.values) {
                if (isAndroidAoaAccessory(device)) {
                    handleDeviceAttached(device)
                    return
                }
            }

            // Prioritet 2: Carlinkit dongle və ya digər dəstəklənən cihazlar
            for (device in deviceList.values) {
                if (isSupportedDevice(device)) {
                    if (isDongleOpen && currentDevice?.deviceId == device.deviceId) {
                        continue
                    }
                    handleDeviceAttached(device)
                    break
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Cihazlar yoxlanarkən xəta: ${e.message}")
        }
    }

    fun handleDeviceAttached(device: UsbDevice) {
        try {
            if (!isSupportedDevice(device)) {
                Log.d(TAG, "Dəstəklənməyən USB cihaz: VID=0x${Integer.toHexString(device.vendorId)}, PID=0x${Integer.toHexString(device.productId)}")
                return
            }

            if (isDongleOpen && currentDevice?.deviceId == device.deviceId) {
                Log.d(TAG, "Cihaz artıq açıqdır və işləyir: ${device.deviceName}")
                return
            }
            currentDevice = device
            val (brand, model) = detectPhoneModel(device)
            currentPhoneModel = model
            val vidHex = Integer.toHexString(device.vendorId)
            val pidHex = Integer.toHexString(device.productId)
            Log.i(TAG, "USB Cihaz Aşkarlandı: $model (VID=0x$vidHex, PID=0x$pidHex)")

            when {
                // 1. Android Auto AOA Aksesuar Rejimi artıq aktivdir
                isAndroidAoaAccessory(device) -> {
                    Log.i(TAG, "Google AOA Aksesuar rejimi aktivdir: $model")
                    if (usbManager?.hasPermission(device) == true) {
                        handleAndroidAutoAccessory(device)
                    } else {
                        requestUsbPermission(device)
                    }
                }

                // 2. Android Telefon birbaşa kabel ilə qoşulub (Samsung, Xiaomi, Pixel və s.)
                isAndroidPhone(device) -> {
                    Log.i(TAG, "Android telefon taxıldı: $model. AOA 2.0 başlatmağa hazırlaşır...")
                    onStatusUpdate?.invoke("🤖 $model Aşkarlandı! Android Auto rejiminə keçirilir…")
                    if (usbManager?.hasPermission(device) == true) {
                        initiateAndroidAutoAoa(device)
                    } else {
                        requestUsbPermission(device)
                    }
                }

                // 3. Apple iOS Cihazı taxılıb
                isAppleDevice(device) -> {
                    Log.i(TAG, "Apple iOS cihazı taxıldı: $model")
                    onStatusUpdate?.invoke("🍏 $model Aşkarlandı! Apple CarPlay aktivləşdirilir…")
                    if (usbManager?.hasPermission(device) == true) {
                        handleAppleDeviceConnected(device)
                    } else {
                        requestUsbPermission(device)
                    }
                }

                // 4. Carlinkit CPC200 Aparat Adapteri
                isCarlinKitDongle(device) -> {
                    Log.i(TAG, "Carlinkit / AutoKit Dongle taxıldı")
                    onStatusUpdate?.invoke("⚡ Carlinkit Adapteri (VID:0x$vidHex) — Aktivləşdirilir…")
                    if (usbManager?.hasPermission(device) == true) {
                        startDongleCommunication(device)
                    } else {
                        requestUsbPermission(device)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "handleDeviceAttached xətası: ${e.message}")
        }
    }

    private fun routeDeviceConnection(device: UsbDevice) {
        when {
            isAndroidAoaAccessory(device) -> handleAndroidAutoAccessory(device)
            isAndroidPhone(device) -> initiateAndroidAutoAoa(device)
            isAppleDevice(device) -> handleAppleDeviceConnected(device)
            isCarlinKitDongle(device) -> startDongleCommunication(device)
            else -> Log.w(TAG, "Naməlum icazəli cihaz: ${device.deviceName}")
        }
    }

    /**
     * Android telefonunu Android Open Accessory 2.0 rejiminə keçirir
     */
    private fun initiateAndroidAutoAoa(device: UsbDevice) {
        if (isAoaSwitching && System.currentTimeMillis() - aoaSwitchTimestamp < 4000L) {
            Log.d(TAG, "AOA keçidi artıq icra olunur, təkrar çağırış ləğv edilir.")
            return
        }
        isAoaSwitching = true
        aoaSwitchTimestamp = System.currentTimeMillis()

        scope.launch {
            try {
                val connection = usbManager?.openDevice(device) ?: run {
                    Log.e(TAG, "Android cihazı açıla bilmədi (openDevice null)")
                    requestUsbPermission(device)
                    isAoaSwitching = false
                    return@launch
                }

                // 1. AOA Protokol Versiyasını Sorğu Edirik (Request 51)
                val protocolBuffer = ByteArray(2)
                val transferred = connection.controlTransfer(
                    UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR, // 0xC0
                    51, // ACCESSORY_GET_PROTOCOL
                    0,
                    0,
                    protocolBuffer,
                    protocolBuffer.size,
                    2000
                )

                if (transferred < 2) {
                    Log.w(TAG, "AOA Protokol sorğusuna cavab alınmadı (transferred=$transferred)")
                    connection.close()
                    isAoaSwitching = false
                    return@launch
                }

                val protocolVersion = (protocolBuffer[1].toInt() shl 8) or (protocolBuffer[0].toInt() and 0xFF)
                Log.i(TAG, "Android AOA Dəstəklənir! Protokol versiyası: $protocolVersion")

                if (protocolVersion < 1) {
                    Log.w(TAG, "Cihaz AOA protokolunu dəstəkləmir")
                    connection.close()
                    isAoaSwitching = false
                    return@launch
                }

                // 2. Rəsmi Google Android Auto İdentifikasiya Məlumatlarını Göndəririk (Request 52)
                sendAoaString(connection, 0, "Android")
                sendAoaString(connection, 1, "Android Auto")
                sendAoaString(connection, 2, "Android Auto")
                sendAoaString(connection, 3, "2.0.1")
                sendAoaString(connection, 4, "https://developer.android.com/auto/index.html")
                sendAoaString(connection, 5, "HU-AAAAAA001")

                Log.i(TAG, "Rəsmi Android Auto AOA parametrləri göndərildi. ACCESSORY_START (Request 53) icra olunur...")

                // 3. Aksesuar Rejimini Başladırıq (Request 53)
                connection.controlTransfer(
                    UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR, // 0x40
                    53, // ACCESSORY_START
                    0,
                    0,
                    byteArrayOf(),
                    0,
                    2000
                )

                connection.close()
                Log.i(TAG, "AOA Başladıldı! Telefon USB-ni yenidən başladır (Re-enumeration gözlənilir)...")
                onStatusUpdate?.invoke("🤖 Android Auto: Re-enumeration gözlənilir…")

                // Telefon USB interfeysini söndürüb AOA kimi açanda 0.5 - 2 saniyə çəkə bilər
                delay(800)
                checkConnectedDevices()
                delay(1200)
                if (!isDongleOpen && isAoaSwitching) {
                    checkConnectedDevices()
                }
                delay(1500)
                if (!isDongleOpen && isAoaSwitching) {
                    checkConnectedDevices()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "AOA Başladılarkən xəta: ${e.message}", e)
                isAoaSwitching = false
            }
        }
    }

    private fun sendAoaString(connection: UsbDeviceConnection, index: Int, string: String): Boolean {
        val bytes = (string + "\u0000").toByteArray(Charsets.UTF_8)
        val transferred = connection.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR, // 0x40
            52, // ACCESSORY_SEND_STRING
            0,
            index,
            bytes,
            bytes.size,
            2000
        )
        return transferred >= 0
    }

    /**
     * AOA Aksesuar Rejiminə keçmiş Android telefonu ilə rabitə qurur
     */
    private fun handleAndroidAutoAccessory(device: UsbDevice) {
        Log.i(TAG, "🎉 Android Auto Aksesuar Rejimi Təsdiqləndi! (VID=0x18D1, PID=0x${Integer.toHexString(device.productId)})")
        isAoaSwitching = false
        closeConnection()
        currentPhoneType = PHONE_TYPE_ANDROID_AUTO
        isAoaMode = true

        try {
            val connection = usbManager?.openDevice(device) ?: run {
                requestUsbPermission(device)
                return
            }

            var matchedIface: UsbInterface? = null
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
                    matchedIface = iface
                    matchedIn = epIn
                    matchedOut = epOut
                    break
                }
            }

            if (matchedIface != null && matchedIn != null && matchedOut != null) {
                connection.claimInterface(matchedIface, true)
                usbConnection = connection
                usbInterface = matchedIface
                inEndpoint = matchedIn
                outEndpoint = matchedOut
                isDongleOpen = true

                Log.i(TAG, "Android Auto Bulk kanalı aktivdir! Daxili oxucu başladılır...")
                startAndroidAutoReader()
                sendAapVersionRequest()
            }

            val (brand, model) = detectPhoneModel(device)
            currentPhoneModel = model
            onAppleDeviceConnected(device)
            onPlatformDetected?.invoke("Android Auto", "🤖")
            onPhoneModelIdentified?.invoke(model, brand, true)
            onStatusUpdate?.invoke("🤖 Android Auto Qoşuldu ($model)! Aura AI Aktivdir.")
        } catch (e: Throwable) {
            Log.e(TAG, "handleAndroidAutoAccessory xətası: ${e.message}")
        }
    }

    fun handleAccessoryAttached(accessory: UsbAccessory) {
        Log.i(TAG, "Google AOA UsbAccessory taxıldı: ${accessory.description} (${accessory.manufacturer} - ${accessory.model})")
        isAoaSwitching = false
        currentAccessory = accessory
        currentPhoneType = PHONE_TYPE_ANDROID_AUTO
        isAoaMode = true

        val modelName = if (previousIdentifiedModel.isNotBlank()) previousIdentifiedModel else "Android (AOA)"
        val brandName = if (previousIdentifiedBrand.isNotBlank()) previousIdentifiedBrand else "Android"
        currentPhoneModel = modelName

        if (usbManager?.hasPermission(accessory) == true) {
            startAccessoryCommunication(accessory)
        } else {
            requestAccessoryPermission(accessory)
        }
    }

    private fun requestAccessoryPermission(accessory: UsbAccessory) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags
            )
            usbManager?.requestPermission(accessory, permissionIntent)
            Log.i(TAG, "UsbAccessory icazə sorğusu göndərildi: ${accessory.description}")
            onStatusUpdate?.invoke("USB: 'İcazə Ver' təsdiqləyin")
        } catch (e: Throwable) {
            Log.e(TAG, "UsbAccessory icazəsi istənilərkən xəta: ${e.message}")
        }
    }

    private fun startAccessoryCommunication(accessory: UsbAccessory) {
        try {
            closeConnection()
            val pfd = usbManager?.openAccessory(accessory)
            if (pfd == null) {
                Log.e(TAG, "openAccessory null qaytardı!")
                requestAccessoryPermission(accessory)
                return
            }

            currentAccessory = accessory
            currentPhoneType = PHONE_TYPE_ANDROID_AUTO
            isAoaMode = true
            accessoryParcelFileDescriptor = pfd
            val fd = pfd.fileDescriptor
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            accessoryInputStream = input
            accessoryOutputStream = output
            isDongleOpen = true

            val modelName = if (previousIdentifiedModel.isNotBlank()) previousIdentifiedModel else "Android Auto"
            val brandName = if (previousIdentifiedBrand.isNotBlank()) previousIdentifiedBrand else "Android"

            currentDevice?.let { onAppleDeviceConnected(it) }
            onPlatformDetected?.invoke("Android Auto", "🤖")
            onPhoneModelIdentified?.invoke(modelName, brandName, true)
            onStatusUpdate?.invoke("🤖 Android Auto Qoşuldu ($modelName)! Aura AI Aktivdir.")

            startAccessoryReader(input)
        } catch (e: Throwable) {
            Log.e(TAG, "startAccessoryCommunication xətası: ${e.message}", e)
        }
    }

    private fun startAccessoryReader(input: FileInputStream) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(16 * 1024)
            while (isActive && isDongleOpen && accessoryInputStream != null) {
                try {
                    val read = input.read(buffer)
                    if (read > 0) {
                        val channel = buffer[0].toInt() and 0xFF
                        if (read > 4) {
                            val payload = ByteArray(read - 4)
                            System.arraycopy(buffer, 4, payload, 0, payload.size)
                            if (channel == 3) {
                                getVideoDecoder()?.feedFrame(payload, 0, payload.size, System.nanoTime() / 1000)
                            } else if (channel == 1 || channel == 2) {
                                getAudioEngine()?.writeMusic(payload, 0, payload.size)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    if (isActive && isDongleOpen) {
                        Log.w(TAG, "Accessory oxuma xətası: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    private fun startAndroidAutoReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val buffer = ByteArray(16 * 1024)
            while (isActive && isDongleOpen && usbConnection != null && inEndpoint != null) {
                try {
                    val read = usbConnection!!.bulkTransfer(inEndpoint, buffer, buffer.size, 1000)
                    if (read > 0) {
                        val channel = buffer[0].toInt() and 0xFF
                        if (read > 4) {
                            val payload = ByteArray(read - 4)
                            System.arraycopy(buffer, 4, payload, 0, payload.size)
                            if (channel == 3) {
                                getVideoDecoder()?.feedFrame(payload, 0, payload.size, System.nanoTime() / 1000)
                            } else if (channel == 1 || channel == 2) {
                                getAudioEngine()?.writeMusic(payload, 0, payload.size)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Android Auto USB oxuma xətası: ${e.message}")
                }
            }
        }
    }

    /**
     * Android Auto Protocol (AAP) ilkin salamlaşmasını (VersionRequest) göndərir
     */
    private fun sendAapVersionRequest() {
        scope.launch {
            try {
                // AAP Frame: Channel 0, Single Frame (0x03), Length 6, Message 0x0001 (VersionRequest), Major 1, Minor 6, Status 0
                val aapVersionReq = byteArrayOf(
                    0x00,               // Channel 0 (Control)
                    0x03,               // Flags: 0x01 | 0x02 = 0x03 (First + Last frame)
                    0x00, 0x06,         // Length: 6 bytes payload
                    0x00, 0x01,         // Message ID: 1 (VersionRequest)
                    0x00, 0x01,         // Major: 1
                    0x00, 0x06          // Minor: 6
                )
                val conn = usbConnection
                val ep = outEndpoint
                if (conn != null && ep != null) {
                    val transferred = conn.bulkTransfer(ep, aapVersionReq, aapVersionReq.size, 1000)
                    Log.i(TAG, "AAP VersionRequest telefonuna göndərildi: $transferred bayt")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "sendAapVersionRequest xətası: ${e.message}")
            }
        }
    }

    /**
     * Apple iPhone ilə birbaşa kabel bağlantısını başladır
     */
    private fun handleAppleDeviceConnected(device: UsbDevice) {
        currentDevice = device
        val (brand, model) = detectPhoneModel(device)
        currentPhoneModel = model
        currentPhoneType = PHONE_TYPE_CARPLAY
        isAppleSwitching = false
        Log.i(TAG, "🍏 Apple iPhone aşkarlandı: $model (VID=0x05AC)")

        // iPhone-da CarPlay USB konfiqurasiyasını aktivləşdiririk (SET_CONFIGURATION)
        try {
            val connection = usbManager?.openDevice(device)
            if (connection != null) {
                usbConnection = connection
                val targetConfig = IphoneCarPlayConfiguration.find(device)
                if (targetConfig != null) {
                    val ok = connection.setConfiguration(targetConfig)
                    Log.i(TAG, "iPhone CarPlay konfiqurasiyası aktivləşdirildi (config ${targetConfig.id}): $ok")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "iPhone setConfiguration xətası: ${e.message}")
        }

        onAppleDeviceConnected(device)
        onPlatformDetected?.invoke("Apple CarPlay", "🍏")
        onPhoneModelIdentified?.invoke(model, brand, false)
        val guideMsg = try {
            context.getString(R.string.status_apple_wireless_guide)
        } catch (_: Throwable) {
            "🍏 $model Qoşuldu! Apple CarPlay aktivləşdirildi."
        }
        onStatusUpdate?.invoke(guideMsg)
    }

    /**
     * Carlinkit Dongle ilə daxili rabitəni və H.264 axınını başladır (Yalnız Fiziki Adapter üçün)
     */
    private fun startDongleCommunication(device: UsbDevice) {
        try {
            closeConnection()

            val connection = usbManager?.openDevice(device) ?: run {
                Log.e(TAG, "USB cihaz açıla bilmədi (openDevice null)")
                onStatusUpdate?.invoke("USB: Adapter açıla bilmədi")
                return
            }

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
            currentDevice = device
            usbConnection = connection
            usbInterface = matchedInterface
            inEndpoint = matchedIn
            outEndpoint = matchedOut
            isDongleOpen = true

            Log.i(TAG, "Carlinkit USB bağlantısı quruldu! Interfeys #${matchedInterface.id}")
            onStatusUpdate?.invoke("USB: Carlinkit Aktivdir. Açılış əmri göndərilir…")

            // 1. Açılış (Open) paketini göndəririk
            sendOpenPacket(1920, 1080, 30, preferredPhoneMode)

            // 2. Video Fokus (500) və Audio Rejimi (23) göndəririk
            sendCommand(500)
            sendCommand(23)

            // 3. Ürək döyüntüsü (Heartbeat) dövrünü başladırıq
            startHeartbeat()

            // 4. Stream oxuma dövrünü başladırıq
            startPacketReader()

            onAppleDeviceConnected(device)
        } catch (e: Throwable) {
            Log.e(TAG, "startDongleCommunication xətası: ${e.message}", e)
            onStatusUpdate?.invoke("USB Xətası: ${e.message}")
        }
    }

    private fun sendOpenPacket(width: Int, height: Int, fps: Int, phoneMode: Int = 3) {
        val payload = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(width)
            putInt(height)
            putInt(fps)
            putInt(5)
            putInt(49152)
            putInt(2)
            putInt(phoneMode)
        }.array()

        sendPacket(MSG_OPEN, payload)
        val modeStr = when (phoneMode) {
            1 -> "🤖 Yalnız Android Auto"
            2 -> "🍏 Yalnız Apple CarPlay"
            else -> "⚡ Universal (Apple CarPlay & Android Auto)"
        }
        Log.i(TAG, "CarTwin 2.0 Open paketi göndərildi: $modeStr (${width}x${height} @ ${fps}fps)")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isDongleOpen && !isAoaMode) {
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
                    accumulator.reset()
                    if (data.size >= 3) {
                        accumulator.write(data, data.size - 3, 3)
                    }
                    return
                }

                if (syncIndex > 0) {
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
                    val trimmed = ByteArray(data.size - 1)
                    System.arraycopy(data, 1, trimmed, 0, trimmed.size)
                    data = trimmed
                    continue
                }

                val totalPacketSize = 16 + length
                if (data.size < totalPacketSize) {
                    accumulator.reset()
                    accumulator.write(data)
                    return
                }

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
                val phoneType = if (payload.size >= 4) {
                    ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                } else PHONE_TYPE_UNKNOWN
                currentPhoneType = phoneType

                val (platformName, icon) = when (phoneType) {
                    PHONE_TYPE_ANDROID_AUTO -> "Android Auto" to "🤖"
                    PHONE_TYPE_CARPLAY -> "Apple CarPlay" to "🍏"
                    PHONE_TYPE_HICAR -> "Huawei HiCar" to "📱"
                    PHONE_TYPE_CARLIFE -> "Baidu CarLife" to "🚗"
                    else -> "Universal Cihaz" to "⚡"
                }

                val isAndroid = phoneType == PHONE_TYPE_ANDROID_AUTO
                val modelName = if (isAndroid) "Android Auto" else "Apple iPhone"
                currentPhoneModel = modelName

                Log.i(TAG, "CarTwin 2.0: $icon $platformName qoşuldu! (Type=$phoneType)")
                currentDevice?.let { onAppleDeviceConnected(it) }
                onPlatformDetected?.invoke(platformName, icon)
                onPhoneModelIdentified?.invoke(modelName, if (isAndroid) "Android" else "Apple", isAndroid)
                onStatusUpdate?.invoke("$icon $platformName qoşuldu! CarPlay aktivdir.")

                sendCommand(12)
                sendCommand(500)
            }
            MSG_UNPLUGGED -> {
                Log.i(TAG, "CarTwin 2.0: Cihaz ayrıldı.")
                currentPhoneType = PHONE_TYPE_UNKNOWN
                onDeviceDisconnected()
                onStatusUpdate?.invoke("Cihaz ayrıldı. Apple və ya Android telefonunuzu qoşun.")
            }
            MSG_VIDEO -> {
                if (payload.size > 20) {
                    val nalOffset = 20
                    val nalLength = payload.size - nalOffset
                    getVideoDecoder()?.feedFrame(payload, nalOffset, nalLength, System.nanoTime() / 1000)
                }
            }
            MSG_AUDIO -> {
                if (payload.size > 12) {
                    val pcmOffset = 12
                    val pcmLength = payload.size - pcmOffset
                    getAudioEngine()?.writeMusic(payload, pcmOffset, pcmLength)
                }
            }
        }
    }

    fun isSupportedDevice(device: UsbDevice): Boolean {
        return isCarlinKitDongle(device) ||
               isAndroidAoaAccessory(device) ||
               isAndroidPhone(device) ||
               isAppleDevice(device)
    }

    fun isCarlinKitDongle(device: UsbDevice): Boolean {
        val vid = device.vendorId
        val pid = device.productId
        val name = ((device.productName ?: "") + " " + (device.manufacturerName ?: "")).lowercase()
        return vid == CARLINKIT_VENDOR_ID ||
               vid == 0x1520 || vid == 0x1521 ||
               pid == 0x1520 || pid == 0x1521 || pid == 0x1522 || pid == 0x1523 ||
               name.contains("carlinkit") ||
               name.contains("autokit")
    }

    fun isAndroidAoaAccessory(device: UsbDevice): Boolean {
        return device.vendorId == GOOGLE_AOA_VENDOR_ID &&
               (device.productId in 0x2D00..0x2D05)
    }

    fun isAndroidPhone(device: UsbDevice): Boolean {
        if (isCarlinKitDongle(device) || isAppleDevice(device) || isAndroidAoaAccessory(device)) return false

        val vid = device.vendorId
        if (vid == HONOR_VENDOR_ID || vid == HONOR_TERMINAL_VENDOR_ID || vid == HUAWEI_VENDOR_ID ||
            vid == SAMSUNG_VENDOR_ID || vid == XIAOMI_VENDOR_ID || vid == OPPO_VENDOR_ID ||
            vid == VIVO_VENDOR_ID || vid == ONEPLUS_VENDOR_ID || vid == GOOGLE_AOA_VENDOR_ID) {
            return true
        }

        // Mass Storage (USB fleşkartlar) və HID (siçan/klaviatura) istisna edilir
        if (device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE || device.deviceClass == UsbConstants.USB_CLASS_HID) {
            return false
        }
        var hasMassStorage = false
        var hasHid = false
        for (i in 0 until device.interfaceCount) {
            val cls = device.getInterface(i).interfaceClass
            if (cls == UsbConstants.USB_CLASS_MASS_STORAGE) hasMassStorage = true
            if (cls == UsbConstants.USB_CLASS_HID) hasHid = true
        }
        if (hasMassStorage && device.interfaceCount <= 1) return false
        if (hasHid && device.interfaceCount <= 1) return false

        return true
    }

    fun isAppleDevice(device: UsbDevice): Boolean {
        return device.vendorId == APPLE_VENDOR_ID
    }

    fun detectPhoneModel(device: UsbDevice): Pair<String, String> {
        if (isAndroidAoaAccessory(device) && previousIdentifiedModel.isNotBlank()) {
            return previousIdentifiedBrand to previousIdentifiedModel
        }

        val vid = device.vendorId
        val prodName = device.productName ?: ""
        val mfgName = device.manufacturerName ?: ""

        val brand = when {
            vid == HONOR_VENDOR_ID || vid == HONOR_TERMINAL_VENDOR_ID || mfgName.contains("Honor", true) || prodName.contains("Honor", true) -> "Honor"
            vid == SAMSUNG_VENDOR_ID || mfgName.contains("Samsung", true) -> "Samsung"
            vid == XIAOMI_VENDOR_ID || mfgName.contains("Xiaomi", true) -> "Xiaomi"
            vid == HUAWEI_VENDOR_ID || mfgName.contains("Huawei", true) -> "Huawei"
            vid == GOOGLE_AOA_VENDOR_ID || mfgName.contains("Google", true) -> "Google Pixel"
            vid == APPLE_VENDOR_ID || mfgName.contains("Apple", true) -> "Apple iPhone"
            vid == OPPO_VENDOR_ID || mfgName.contains("Oppo", true) -> "OPPO"
            vid == ONEPLUS_VENDOR_ID || mfgName.contains("OnePlus", true) -> "OnePlus"
            vid == VIVO_VENDOR_ID || mfgName.contains("Vivo", true) -> "Vivo"
            vid == 0x22B8 || mfgName.contains("Motorola", true) -> "Motorola"
            vid == 0x0FCE || mfgName.contains("Sony", true) -> "Sony"
            vid == 0x1004 || mfgName.contains("LG", true) -> "LG"
            vid == 0x2C03 || mfgName.contains("Realme", true) -> "Realme"
            vid == 0x2C64 || mfgName.contains("Infinix", true) || mfgName.contains("Tecno", true) -> "Tecno / Infinix"
            else -> mfgName.ifBlank { "Android Cihazı" }
        }

        val model = if (prodName.isNotBlank() && !prodName.equals(brand, ignoreCase = true)) {
            "$brand $prodName"
        } else {
            brand
        }

        if (brand != "Android Cihazı") {
            previousIdentifiedBrand = brand
            previousIdentifiedModel = model
        }

        return brand to model
    }

    private fun requestUsbPermission(device: UsbDevice) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
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

    fun sendTouch(action: Int, x: Float, y: Float) {
        if (!isDongleOpen || usbConnection == null || outEndpoint == null) return

        val clampedX = (x * 10000).toInt().coerceIn(0, 10000)
        val clampedY = (y * 10000).toInt().coerceIn(0, 10000)

        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(action)
            putInt(clampedX)
            putInt(clampedY)
            putInt(0)
        }.array()

        if (!isAoaMode) {
            sendPacket(MSG_TOUCH, payload)
        }
    }

    fun sendCommand(commandId: Int) {
        if (!isDongleOpen || usbConnection == null || outEndpoint == null) return

        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(commandId)
        }.array()

        if (!isAoaMode) {
            sendPacket(MSG_COMMAND, payload)
        }
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
        isAoaMode = false
        isAppleSwitching = false
        readerJob?.cancel()
        heartbeatJob?.cancel()

        try {
            accessoryInputStream?.close()
        } catch (_: Throwable) {}
        try {
            accessoryOutputStream?.close()
        } catch (_: Throwable) {}
        try {
            accessoryParcelFileDescriptor?.close()
        } catch (_: Throwable) {}
        accessoryInputStream = null
        accessoryOutputStream = null
        accessoryParcelFileDescriptor = null
        currentAccessory = null

        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Throwable) {}

        usbConnection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
    }

    fun isAppleDeviceConnected(): Boolean {
        return currentDevice?.let { isAppleDevice(it) } ?: false
    }

    fun hasConnectedDevice(): Boolean {
        if (currentDevice != null || currentAccessory != null || isDongleOpen || isAoaMode) return true
        val deviceList = usbManager?.deviceList
        if (!deviceList.isNullOrEmpty()) {
            for (d in deviceList.values) {
                if (isSupportedDevice(d)) {
                    currentDevice = d
                    val (_, model) = detectPhoneModel(d)
                    currentPhoneModel = model
                    return true
                }
            }
        }
        val accList = usbManager?.accessoryList
        return !accList.isNullOrEmpty()
    }

    fun refreshAndConnect(): Boolean {
        checkConnectedDevices()
        return hasConnectedDevice()
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
