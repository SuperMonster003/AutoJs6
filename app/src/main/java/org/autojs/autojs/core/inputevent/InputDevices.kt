package org.autojs.autojs.core.inputevent

import android.util.Log
import android.view.InputDevice
import android.view.InputDevice.SOURCE_TOUCHPAD
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.util.RootUtils

/**
 * Created by Stardust on Aug 1, 2017.
 */
object InputDevices {
    private const val LOG_TAG = "InputDevices"

    @JvmStatic
    val touchDeviceName: String?
        get() {
            val device = touchDevice
            return device?.name
        }

    @JvmStatic
    val touchDevice: InputDevice?
        get() {
            for (id in InputDevice.getDeviceIds()) {
                val device = InputDevice.getDevice(id) ?: continue
                Log.d(LOG_TAG, "device: $device")
                if (device.supportsSource(SOURCE_TOUCHSCREEN) || device.supportsSource(SOURCE_TOUCHPAD)) {
                    return device
                }
            }
            return null
        }

    @JvmStatic
    val touchDeviceId: Int
        get() {
            val deviceName = touchDeviceName ?: return -1
            val cmd = "adb shell cat /proc/bus/input/devices"
            try {
                val result = run {
                    if (RootUtils.isRootAvailable()) {
                        val result = ProcessShell.execCommand(cmd, true)
                        if (result.code == 0) return@run result.result
                    }
                    if (WrappedShizuku.isOperational()) {
                        val result = WrappedShizuku.execCommand(cmd)
                        if (result.code == 0) return@run result.result
                    }
                    return -1
                }
                val lines = result.lines()

                var currentDeviceName: String? = null

                lines.forEach { line ->
                    if (line.startsWith("N: Name=")) {
                        currentDeviceName = line.substringAfter("N: Name=\"").substringBefore("\"")
                    }
                    if (currentDeviceName == deviceName && line.contains("Handlers=")) {
                        val id = line.substringAfter("event").substringBefore(" ")
                        return id.toIntOrNull() ?: -1
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return -1
        }


    private fun supportSource(device: InputDevice, source: Int) = (device.sources and source) == source

}
