package org.autojs.autojs.runtime.api.augment.device

import androidx.core.net.toUri
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.app.tool.PointerLocationTool
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.util.MorseCode
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.DeviceUtils
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import java.util.function.Supplier
import org.autojs.autojs.runtime.api.Device as ApiDevice

@Suppress("unused", "UNUSED_PARAMETER")
class Device(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::summary.name,
        ::digest.name,
        ::vibrate.name,
        ::isScreenOff.name,
        ::isScreenPortrait.name,
        ::isScreenLandscape.name,
        ::getIpAddress.name,
        ::getIpv6Address.name,
        ::getGatewayAddress.name,
        ::isActiveNetworkMetered.name,
        ::isConnectedOrConnecting.name,
        ::isWifiAvailable.name,
        ::getSharedDeviceId.name,

        ::setPointerLocation.name,
        ::setPointerLocationEnabled.name,
        ::setPointerLocationDisabled.name,
        ::isPointerLocationEnabled.name,
        ::isPointerLocationDisabled.name,
        ::togglePointerLocation.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "width" to Supplier { ScreenMetrics.deviceScreenWidth },
        "height" to Supplier { ScreenMetrics.deviceScreenHeight },
        "rotation" to Supplier { scriptRuntime.device.rotation },
        "orientation" to Supplier { scriptRuntime.device.orientation },
        "density" to Supplier { ScreenMetrics.deviceScreenDensity },
    )

    companion object : ArgumentGuards() {

        const val KEY_POINTER_LOCATION = "pointer_location"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun summary(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            DeviceUtils.getDeviceSummary(globalContext)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun digest(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            fun suffix(body: String, reject: String?) = if (body == reject) "" else " ($body)"

            val brandSuffix = suffix(ApiDevice.manufacturer, ApiDevice.brand)
            val deviceSuffix = suffix(ApiDevice.model, ApiDevice.device)
            val releaseSuffix = suffix("${ApiDevice.sdkInt}", null)

            listOf(
                "${ApiDevice.brand}$brandSuffix",
                "${ApiDevice.device}$deviceSuffix",
                "${ApiDevice.release}$releaseSuffix",
            ).joinToString(" / ")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun vibrate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (arg0, arg1) = argList
            when (argList.size) {
                2 -> vibrateRhinoWithRuntime(scriptRuntime, arg0, arg1)
                1 -> vibrateRhinoWithRuntime(scriptRuntime, arg0)
                else -> throw ShouldNeverHappenException()
            }
            UNDEFINED
        }

        /**
         * Parameters <br>
         * - o <1> millis: Long <br>
         * - o <2> timings: LongArray <br>
         * - o <3> morseCode: String <br>
         */
        @JvmStatic
        @RhinoFunctionBody
        fun vibrateRhinoWithRuntime(scriptRuntime: ScriptRuntime, o: Any?): Unit = when (o) {
            is Number -> scriptRuntime.device.vibrate(o.toLong())
            is String -> MorseCode.vibrateRhino(o)
            is NativeArray -> scriptRuntime.device.vibrate(o.map { toVibrateTimingElement(it) }.toLongArray())
            else -> throw WrappedIllegalArgumentException("Invalid argument o ($o) for device.${::vibrate.name}")
        }

        /**
         * Parameters <br>
         * - o <1> off: Long <br>
         * - p <1> millis: Long <br>
         * - o <2> timingsWithoutOff: LongArray <br>
         * - p <2> off: Long <br>
         */
        @JvmStatic
        @RhinoFunctionBody
        fun vibrateRhinoWithRuntime(scriptRuntime: ScriptRuntime, o: Any?, p: Any?) {
            when {
                o is String -> {
                    MorseCode.vibrateRhino(o, p)
                }
                o is Number && p is Number -> {
                    scriptRuntime.device.vibrate(/* off = */ o.toLong(), /* millis = */ p.toLong())
                }
                o is NativeArray && p is Number -> {
                    val listOff = listOf(p.toLong())
                    val listOthers = o.map { toVibrateTimingElement(it) }
                    val timings = (listOff + listOthers).toLongArray()
                    scriptRuntime.device.vibrate(timings)
                }
                else -> throw WrappedIllegalArgumentException("Invalid argument ${o.jsBrief()} and ${p.jsBrief()} for device.${::vibrate.name}")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isScreenOff(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.device.isScreenOn.not()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isScreenPortrait(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.device.isScreenPortrait
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isScreenLandscape(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.device.isScreenLandscape
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getIpAddress(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) {
            when (it.size) {
                0 -> getIpAddressRhino()
                1 -> getIpAddressRhino(it[0])
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun getIpAddressRhino(): String = NetworkUtils.getIpAddress()

        @JvmStatic
        @RhinoFunctionBody
        fun getIpAddressRhino(useIPv4: Any?): String = when {
            useIPv4.isJsNullish() -> NetworkUtils.getIpAddress()
            else -> NetworkUtils.getIpAddress(Context.toBoolean(useIPv4))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getIpv6Address(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            NetworkUtils.getIpv6Address()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getGatewayAddress(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            NetworkUtils.getGatewayAddress()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isActiveNetworkMetered(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            NetworkUtils.isActiveNetworkMetered()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isConnectedOrConnecting(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            NetworkUtils.isConnectedOrConnecting()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isWifiAvailable(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            NetworkUtils.isWifiAvailable()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getSharedDeviceId(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String? = ensureArgumentsIsEmpty(args) {
            // val b = globalContext.contentResolver.call("content://org.autojs.autojs6.deviceid.provider".toUri(), "getDeviceId", null, null)
            // b?.getString("device_id")
            val uri = "content://org.autojs.autojs6.deviceid.provider/v1/device_id".toUri()
            globalContext.contentResolver.query(uri, arrayOf("device_id"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }

        private fun toVibrateTimingElement(it: Any?): Long {
            return Context.toNumber(it).takeUnless { it.isNaN() }?.toLong()
                ?: throw WrappedIllegalArgumentException("Argument $it cannot be converted as a timing element for device.${::vibrate.name}")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setPointerLocation(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            val enabled = coerceBoolean(it, false)

            // @Hint by SuperMonster003 on Jan 10, 2025.
            //  ! Starting from Android API 23 (6.0) [M], in the android.provider.Settings.System source code,
            //  ! `PRIVATE_SETTINGS.add(POINTER_LOCATION);` means that `POINTER_LOCATION` is not settable,
            //  ! otherwise it will trigger an exception:
            //  ! "java.lang.IllegalArgumentException: You cannot change private secure settings.".
            //  ! zh-CN:
            //  ! Android API 23 (6.0) [M] 起, android.provider.Settings.System 源码中,
            //  ! `PRIVATE_SETTINGS.add(POINTER_LOCATION);` 意味着 `POINTER_LOCATION` 是不可设置的,
            //  ! 否则会触发异常: "java.lang.IllegalArgumentException: You cannot change private secure settings.".
            //  !
            //  # if (SettingsCompat.canWriteSettings(globalContext)) {
            //  #     try {
            //  #         if (Settings.System.putInt(globalContext.contentResolver, KEY_POINTER_LOCATION, if (enabled) PointerLocation.ENABLED.value else PointerLocation.DISABLED.value)) {
            //  #             return@ensureArgumentsOnlyOne true
            //  #         }
            //  #     } catch (e: Exception) {
            //  #         if (e.message == null || !e.message!!.lowercase().contains("private secure settings")) {
            //  #             e.printStackTrace()
            //  #         }
            //  #     }
            //  # }

            PointerLocationTool.checkPointerLocationState(globalContext, enabled) || PointerLocationTool.togglePointerLocation(globalContext)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setPointerLocationEnabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            setPointerLocation(scriptRuntime, arrayOf(true))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setPointerLocationDisabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            setPointerLocation(scriptRuntime, arrayOf(false))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isPointerLocationEnabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            PointerLocationTool.isPointerLocationEnabled(globalContext)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isPointerLocationDisabled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            PointerLocationTool.isPointerLocationDisabled(globalContext)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun togglePointerLocation(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            setPointerLocation(scriptRuntime, arrayOf(!isPointerLocationEnabled(scriptRuntime, args)))
        }

    }

}