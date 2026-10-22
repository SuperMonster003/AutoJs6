package org.autojs.autojs.runtime.api.modules.device

import org.autojs.autojs.core.plugin.nativealign.NativePageSize
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.augment.device.Device
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `device` module on the new script module layer (roadmap P3.4): the module object's prototype is the runtime's
 * `Device` (`ScriptRuntime.device`: the build properties, brightness, volume, battery, wake locks, ...), the functions and
 * getters of the current module come in its order, `pageSize` read-only as before.
 *
 * `vibrate` keeps the documented overloads (milliseconds, a timing array, Morse code text, each with an optional second
 * argument) and runs on the current module's implementation; this layer checks the argument types first: other
 * combinations and timing elements without a number are TypeErrors (D10; the current module threw
 * IllegalArgumentException). `getIpAddress(useIPv4)` and `setPointerLocation(enabled)` take booleans (T4; the current
 * module took the truthiness).
 *
 * zh-CN: 新脚本模块层上的 device 模块 (P3.4): 模块对象的原型是运行时的 Device (ScriptRuntime.device: 构建属性, 亮度, 音量, 电池, 唤醒锁等), 函数与 getter
 * 的顺序同现有模块, pageSize 与以前一样只读. vibrate 保留文档所述的重载 (毫秒数, 时长数组, 摩斯电码文本, 各可带第二个参数), 由现有模块的实现执行; 本层先检查
 * 参数类型: 其他组合以及不是数值的时长元素为 TypeError (D10; 现有模块抛 IllegalArgumentException). getIpAddress(useIPv4) 与 setPointerLocation(enabled)
 * 接受布尔值 (T4; 现有模块取真值).
 */
@ScriptModule(name = "device")
class DeviceModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.device

    @ScriptFunction
    fun summary(): String = Device.summary(runtime, emptyArray())

    @ScriptFunction
    fun digest(): String = Device.digest(runtime, emptyArray())

    /** `vibrate(millis | timings | morseCode)` or `vibrate(off, millis)`, `vibrate(timingsWithoutOff, off)`, `vibrate(morseCode, delay)`. */
    @ScriptFunction
    fun vibrate(pattern: Any?, second: Any? = ABSENT) {
        val first = RhinoUtils.unwrap(pattern)
        if (second === ABSENT) {
            if (first !is Number && first !is String && first !is NativeArray) throw GlobalValues.typeError(VIBRATE, "pattern", "number | number[] | string", pattern)
            timings(first)
            Device.vibrateRhinoWithRuntime(runtime, first)
            return
        }
        val other = RhinoUtils.unwrap(second)
        when {
            first is String -> Unit
            first is Number || first is NativeArray -> if (other !is Number) throw GlobalValues.typeError(VIBRATE, "off / millis", "number", second)
            else -> throw GlobalValues.typeError(VIBRATE, "pattern", "number | number[] | string", pattern)
        }
        timings(first)
        Device.vibrateRhinoWithRuntime(runtime, first, other)
    }

    @ScriptFunction
    fun isScreenOff(): Boolean = !runtime.device.isScreenOn

    @ScriptFunction
    fun isScreenPortrait(): Boolean = runtime.device.isScreenPortrait

    @ScriptFunction
    fun isScreenLandscape(): Boolean = runtime.device.isScreenLandscape

    @ScriptFunction
    fun getIpAddress(useIPv4: Boolean? = null): String = if (useIPv4 == null) NetworkUtils.getIpAddress() else NetworkUtils.getIpAddress(useIPv4)

    @ScriptFunction
    fun getIpv6Address(): String = NetworkUtils.getIpv6Address()

    @ScriptFunction
    fun getGatewayAddress(): String = NetworkUtils.getGatewayAddress()

    @ScriptFunction
    fun isActiveNetworkMetered(): Boolean = NetworkUtils.isActiveNetworkMetered()

    @ScriptFunction
    fun isConnectedOrConnecting(): Boolean = NetworkUtils.isConnectedOrConnecting()

    @ScriptFunction
    fun isWifiAvailable(): Boolean = NetworkUtils.isWifiAvailable()

    @ScriptFunction
    fun getSharedDeviceId(): String? = Device.getSharedDeviceId(runtime, emptyArray())

    @ScriptFunction
    fun setPointerLocation(enabled: Boolean): Boolean = Device.setPointerLocation(runtime, arrayOf(enabled))

    @ScriptFunction
    fun setPointerLocationEnabled(): Boolean = setPointerLocation(true)

    @ScriptFunction
    fun setPointerLocationDisabled(): Boolean = setPointerLocation(false)

    @ScriptFunction
    fun isPointerLocationEnabled(): Boolean = Device.isPointerLocationEnabled(runtime, emptyArray())

    @ScriptFunction
    fun isPointerLocationDisabled(): Boolean = Device.isPointerLocationDisabled(runtime, emptyArray())

    @ScriptFunction
    fun togglePointerLocation(): Boolean = setPointerLocation(!isPointerLocationEnabled())

    @ScriptGetter
    val width: Int
        get() = ScreenMetrics.deviceScreenWidth

    @ScriptGetter
    val height: Int
        get() = ScreenMetrics.deviceScreenHeight

    @ScriptGetter
    val rotation: Int
        get() = runtime.device.rotation

    @ScriptGetter
    val orientation: Int
        get() = runtime.device.orientation

    @ScriptGetter
    val density: Int
        get() = ScreenMetrics.deviceScreenDensity

    @ScriptGetter(attributes = ScriptAttributes.READONLY or ScriptAttributes.PERMANENT)
    val pageSize: Long
        get() = NativePageSize.current()

    /** Checks the elements of a timing array: each must have a number (JS ToNumber), as the current module requires. */
    private fun timings(pattern: Any?) {
        if (pattern !is NativeArray) return
        pattern.forEachIndexed { i, e ->
            if (RhinoScriptRuntime.toNumber(e).isNaN()) throw GlobalValues.typeError(VIBRATE, "timings[$i]", "number", e)
        }
    }

    private companion object {
        const val VIBRATE = "device.vibrate"
        val ABSENT = Any()
    }

}
