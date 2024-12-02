package org.autojs.autojs.runtime.api.augment.device

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.util.MorseCode
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.DeviceUtils
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
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
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "width" to Supplier { ScreenMetrics.deviceScreenWidth },
        "height" to Supplier { ScreenMetrics.deviceScreenHeight },
        "rotation" to Supplier { scriptRuntime.device.rotation },
        "orientation" to Supplier { scriptRuntime.device.orientation },
        "density" to Supplier { ScreenMetrics.deviceScreenDensity },
    )

    companion object : FlexibleArray() {

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

        private fun toVibrateTimingElement(it: Any?): Long {
            return Context.toNumber(it).takeUnless { it.isNaN() }?.toLong()
                ?: throw WrappedIllegalArgumentException("Argument $it cannot be converted as a timing element for device.${::vibrate.name}")
        }

    }

}