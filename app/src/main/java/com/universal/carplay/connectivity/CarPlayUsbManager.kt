package com.universal.carplay.connectivity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Simli USB Apple CarPlay Əlaqə Meneceri
 * 
 * iPhone kabel ilə avtomobilin USB portuna taxılan kimi Apple Vendor ID-sini (0x05AC) tanıyır
 * və simli CarPlay sessiyasını başladır.
 */
class CarPlayUsbManager(
    private val context: Context,
    private val onAppleDeviceConnected: (UsbDevice) -> Unit,
    private val onDeviceDisconnected: () -> Unit
) {
    private val TAG = "CarPlay_USB"
    private val ACTION_USB_PERMISSION = "com.universal.carplay.USB_PERMISSION"
    private val APPLE_VENDOR_ID = 0x05AC // Apple Inc. rəsmi Vendor ID-si (1452)

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var isRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return

            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let { handleDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.i(TAG, "USB Cihaz ayrıldı.")
                    onDeviceDisconnected()
                }
                ACTION_USB_PERMISSION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
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

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }

        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true
        Log.i(TAG, "USB Meneceri aktivləşdirildi")

        // Hazırda qoşulu olan USB cihazları yoxlayırıq
        checkConnectedDevices()
    }

    private fun checkConnectedDevices() {
        val deviceList = usbManager?.deviceList ?: return
        for (device in deviceList.values) {
            if (isAppleDevice(device)) {
                handleDeviceAttached(device)
                break
            }
        }
    }

    private fun handleDeviceAttached(device: UsbDevice) {
        if (isAppleDevice(device)) {
            Log.i(TAG, "Apple iPhone USB ilə qoşuldu! VendorID=0x${Integer.toHexString(device.vendorId)}, ProductID=0x${Integer.toHexString(device.productId)}")

            if (usbManager?.hasPermission(device) == true) {
                onAppleDeviceConnected(device)
            } else {
                requestUsbPermission(device)
            }
        } else {
            Log.d(TAG, "Digər USB cihaz taxıldı: VendorID=0x${Integer.toHexString(device.vendorId)}")
        }
    }

    private fun isAppleDevice(device: UsbDevice): Boolean {
        return device.vendorId == APPLE_VENDOR_ID
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager?.requestPermission(device, permissionIntent)
    }

    fun stop() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(usbReceiver)
            isRegistered = false
        } catch (ignored: Exception) {}
    }
}
