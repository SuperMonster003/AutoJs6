package org.autojs.autojs.core.inputevent

import android.util.Log
import android.view.InputDevice
import android.view.InputDevice.SOURCE_TOUCHPAD
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import androidx.annotation.VisibleForTesting
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

    /**
     * The N of the /dev/input/eventN node of the touch screen, or -1 when it cannot be resolved.
     * zh-CN: 触摸屏节点 /dev/input/eventN 的 N, 无法解析时为 -1.
     */
    @JvmStatic
    val touchDeviceId: Int
        get() {
            val deviceName = touchDeviceName ?: return -1
            // Without root, the check may take hundreds of milliseconds (it starts a shell), so it runs once per lookup.
            // zh-CN: 没有 root 时该检查可能耗时数百毫秒 (会启动 shell), 因此每次解析只检查一次.
            val rootAvailable = RootUtils.isRootAvailable()
            return resolveTouchDeviceId(deviceName) { execPrivileged(it, rootAvailable) }
        }

    /**
     * Looks [deviceName] up in /proc/bus/input/devices, then in the output of `getevent -pl`, whose nodes the shell user
     * can read when /proc/bus/input/devices is denied to it (docs/shizuku-oom/ROADMAP.md D17).
     * zh-CN: 先在 /proc/bus/input/devices 中查找 [deviceName], 再查找 `getevent -pl` 的输出; shell 用户读不到
     * /proc/bus/input/devices 时仍可读取后者 (docs/shizuku-oom/ROADMAP.md D17).
     */
    @VisibleForTesting
    internal fun resolveTouchDeviceId(deviceName: String, exec: (String) -> String?): Int {
        exec(CMD_PROC_INPUT_DEVICES)?.let { parseProcInputDevices(it, deviceName) }?.let { return it }
        exec(CMD_GETEVENT_DEVICES)?.let { parseGeteventDevices(it, deviceName) }?.let { return it }
        return -1
    }

    // The standard output of [cmd] run as root or by Shizuku, or null when neither prints any. The exit code is not checked:
    // a result turns code 0 into 1 when anything goes to the standard error, as `getevent -p` does for /dev/input/mice
    // while still listing every other node.
    // zh-CN: 以 root 或经 Shizuku 运行 [cmd] 的标准输出; 两者都没有输出时为 null. 不检查退出码: 标准错误有内容时结果会把
    // 退出码 0 改为 1, 而 `getevent -p` 会为 /dev/input/mice 输出错误, 同时仍列出其他全部节点.
    private fun execPrivileged(cmd: String, rootAvailable: Boolean): String? {
        try {
            if (rootAvailable) {
                val result = ProcessShell.execCommand(cmd, true)
                if (!result.result.isNullOrBlank()) return result.result
            }
            if (WrappedShizuku.isRunning()) {
                runCatching {
                    val result = WrappedShizuku.execCommand(cmd)
                    if (!result.result.isNullOrBlank()) return result.result
                }.onFailure { Log.w(LOG_TAG, "$cmd: ${it.message}") }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    @VisibleForTesting
    internal fun parseProcInputDevices(text: String, deviceName: String): Int? {
        var currentDeviceName: String? = null
        text.lines().forEach { line ->
            if (line.startsWith("N: Name=")) {
                currentDeviceName = line.substringAfter("N: Name=\"").substringBefore("\"")
            }
            if (currentDeviceName == deviceName && line.contains("Handlers=")) {
                return Regex("\\bevent(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
            }
        }
        return null
    }

    @VisibleForTesting
    internal fun parseGeteventDevices(text: String, deviceName: String): Int? {
        var currentNode: Int? = null
        text.lines().forEach { line ->
            Regex("^add device \\d+: /dev/input/event(\\d+)").find(line)?.let {
                currentNode = it.groupValues[1].toIntOrNull()
                return@forEach
            }
            val name = Regex("^\\s+name:\\s+\"(.*)\"\\s*$").find(line)?.groupValues?.get(1) ?: return@forEach
            if (name == deviceName) currentNode?.let { return it }
        }
        return null
    }

    private const val CMD_PROC_INPUT_DEVICES = "adb shell cat /proc/bus/input/devices"
    private const val CMD_GETEVENT_DEVICES = "adb shell getevent -pl"


    private fun supportSource(device: InputDevice, source: Int) = (device.sources and source) == source

}
