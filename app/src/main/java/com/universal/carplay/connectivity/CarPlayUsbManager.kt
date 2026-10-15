package com.universal.carplay.connectivity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Simli USB Apple CarPlay Əlaqə Meneceri
 * 
 * iPhone kabel ilə avtomobilin USB portuna taxılan kimi Apple Vendor ID-sini (0x05AC) 
 * və ya Carlinkit/AutoKit adapterini (0x1314) tanıyır və simli CarPlay sessiyasını başladır.
 */
class CarPlayUsbManager(
    private val context: Context,
    private val onAppleDeviceConnected: (UsbDevice) -> Unit,
    private val onDeviceDisconnected: () -> Unit
) {
    companion object {
        private const val TAG = "CarPlay_USB"
        private const val ACTION_USB_PERMISSION = "com.universal.carplay.USB_PERMISSION"
        private const val APPLE_VENDOR_ID = 0x05AC // Apple Inc. rəsmi Vendor ID-si (1452)
        private const val CARLINKIT_VENDOR_ID = 0x1314 // Carlinkit / AutoKit Dongle ID
    }

    private val usbManager = try {
        context.getSystemService(Context.USB_SERVICE) as? UsbManager
    } catch (_: Throwable) { null }

    private var isRegistered = false

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
                    onDeviceDisconnected()
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
                        onAppleDeviceConnected(device)
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

            // Sistem USB broadcastləri üçün RECEIVER_EXPORTED istifadə edirik
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            isRegistered = true
            Log.i(TAG, "USB Meneceri aktivləşdirildi")

            // Hazırda qoşulu olan USB cihazları yoxlayırıq
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
                Log.i(TAG, "CarPlay USB Cihazı qoşuldu! VendorID=0x${Integer.toHexString(device.vendorId)}, ProductID=0x${Integer.toHexString(device.productId)}")

                if (usbManager?.hasPermission(device) == true) {
                    onAppleDeviceConnected(device)
                } else {
                    requestUsbPermission(device)
                }
            } else {
                Log.d(TAG, "Digər USB cihaz taxıldı: VendorID=0x${Integer.toHexString(device.vendorId)}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "handleDeviceAttached xətası: ${e.message}")
        }
    }

    private fun isCarPlayDevice(device: UsbDevice): Boolean {
        return device.vendorId == APPLE_VENDOR_ID || device.vendorId == CARLINKIT_VENDOR_ID
    }

    private fun requestUsbPermission(device: UsbDevice) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            usbManager?.requestPermission(device, permissionIntent)
            Log.i(TAG, "USB icazə sorğusu göndərildi: ${device.deviceName}")
        } catch (e: Throwable) {
            Log.e(TAG, "USB icazəsi istənilərkən xəta: ${e.message}")
        }
    }

    fun stop() {
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
