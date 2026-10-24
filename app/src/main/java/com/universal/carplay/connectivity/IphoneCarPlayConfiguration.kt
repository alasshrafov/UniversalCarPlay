package com.universal.carplay.connectivity

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.util.Log

/**
 * Apple iPhone üçün CarPlay / NCM / USBMUX konfiqurasiya təyini və aktivləşdirilməsi
 */
object IphoneCarPlayConfiguration {
    private const val TAG = "CarPlay_IphoneConfig"

    private const val USBMUX_CLASS = 0xff
    private const val USBMUX_SUBCLASS = 0xfe
    private const val USBMUX_PROTOCOL = 0x02
    private const val APPLE_ETHERNET_CLASS = 0xff
    private const val APPLE_ETHERNET_SUBCLASS = 0xfd
    private const val APPLE_ETHERNET_PROTOCOL = 0x01
    private const val NCM_CONTROL_CLASS = 0x02
    private const val NCM_CONTROL_SUBCLASS = 0x0d

    fun find(device: UsbDevice): UsbConfiguration? {
        val configurations = (0 until device.configurationCount).map { device.getConfiguration(it) }
        val chosen = configurations.firstOrNull { usbMuxInterface(it) != null && hasCdcNcm(it) && hasAppleEthernet(it) }
            ?: configurations.firstOrNull { usbMuxInterface(it) != null && hasCdcNcm(it) }
            ?: configurations.firstOrNull { hasCdcNcm(it) }
            ?: configurations.firstOrNull { it.interfaceCount > 1 }
            ?: configurations.firstOrNull()

        Log.i(TAG, "CarPlay USB konfiqurasiyası seçildi: id=${chosen?.id}, mövcud=${configurations.map { it.id }}")
        return chosen
    }

    private fun usbMuxInterface(configuration: UsbConfiguration): UsbInterface? =
        (0 until configuration.interfaceCount).map { configuration.getInterface(it) }.firstOrNull {
            it.interfaceClass == USBMUX_CLASS &&
                    it.interfaceSubclass == USBMUX_SUBCLASS &&
                    it.interfaceProtocol == USBMUX_PROTOCOL
        }

    private fun hasCdcNcm(configuration: UsbConfiguration): Boolean =
        (0 until configuration.interfaceCount).map { configuration.getInterface(it) }.any {
            it.interfaceClass == NCM_CONTROL_CLASS && it.interfaceSubclass == NCM_CONTROL_SUBCLASS
        }

    private fun hasAppleEthernet(configuration: UsbConfiguration): Boolean =
        (0 until configuration.interfaceCount).map { configuration.getInterface(it) }.any {
            it.interfaceClass == APPLE_ETHERNET_CLASS &&
                    it.interfaceSubclass == APPLE_ETHERNET_SUBCLASS &&
                    it.interfaceProtocol == APPLE_ETHERNET_PROTOCOL
        }
}
