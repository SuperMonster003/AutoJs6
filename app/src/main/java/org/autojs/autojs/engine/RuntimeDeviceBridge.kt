package org.autojs.autojs.engine

import android.content.Context
import androidx.core.net.toUri
import org.autojs.autojs.app.tool.PointerLocationTool
import org.autojs.autojs.runtime.api.Device
import org.autojs.autojs.util.DeviceUtils
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.RomUtils
import org.json.JSONArray

/**
 * Host side of Rhino's `device` members that the JS runtimes reach through the `device` module (api-coverage P3.1c): the
 * screen, power source, network and pointer location queries, the phone state permission, `roms.isBackgroundStartGranted`
 * and the hardware identifiers, answered by the same host calls as Rhino (one [Device] per execution, as each Rhino runtime
 * has one). The Node bridge runs these calls on its system threads, since pointer location goes through a Root or Shizuku
 * shell and the shared device id through a content provider. Capabilities: [capabilities] (Q146 (a): `device`, the
 * identifiers also `device.identifiers`, the pointer location switches also `device.power`).
 *
 * zh-CN: JS 运行时经 device 模块访问的 Rhino device 成员的宿主侧 (api-coverage P3.1c): 屏幕, 电源, 网络与指针位置查询, 电话状态权限,
 * roms.isBackgroundStartGranted 与硬件标识符, 宿主调用与 Rhino 相同 (每次执行一个 [Device], 与每个 Rhino 运行时一个相同). Node 桥在系统线程上执行这些调用,
 * 因为指针位置经 Root 或 Shizuku shell, 共享设备 id 经内容提供者. 所需能力见 [capabilities] (Q146 (a): device, 标识符另需 device.identifiers,
 * 指针位置开关另需 device.power).
 */
internal class RuntimeDeviceBridge(private val context: Context) {

    private val device by lazy { Device(context) }

    fun invoke(method: String, args: JSONArray): Any? = when (method) {
        "summary" -> DeviceUtils.getDeviceSummary(context)
        "getOrientation" -> device.orientation
        "getRotation" -> device.rotation
        "isScreenOff" -> !device.isScreenOn
        "isScreenPortrait" -> device.isScreenPortrait
        "isScreenLandscape" -> device.isScreenLandscape
        "isPowerSourceAC" -> device.isPowerSourceAC
        "isPowerSourceUSB" -> device.isPowerSourceUSB
        "isPowerSourceWireless" -> device.isPowerSourceWireless
        "isPowerSourceDock" -> device.isPowerSourceDock
        "getIpAddress" -> if (args.isNull(0)) NetworkUtils.getIpAddress() else NetworkUtils.getIpAddress(args.getBoolean(0))
        "getIpv6Address" -> NetworkUtils.getIpv6Address()
        "getGatewayAddress" -> NetworkUtils.getGatewayAddress()
        "isActiveNetworkMetered" -> NetworkUtils.isActiveNetworkMetered()
        "isConnectedOrConnecting" -> NetworkUtils.isConnectedOrConnecting()
        "isWifiAvailable" -> NetworkUtils.isWifiAvailable()
        "isPointerLocationEnabled" -> PointerLocationTool.isPointerLocationEnabled(context)
        "isPointerLocationDisabled" -> PointerLocationTool.isPointerLocationDisabled(context)
        "setPointerLocation" -> setPointerLocation(args.getBoolean(0))
        "togglePointerLocation" -> setPointerLocation(!PointerLocationTool.isPointerLocationEnabled(context))
        "hasReadPhoneStatePermission" -> device.hasReadPhoneStatePermission()
        "ensureReadPhoneStatePermission" -> device.ensureReadPhoneStatePermission()
        "isBackgroundStartGranted" -> RomUtils.isBackgroundStartGranted(context)
        "getIMEI" -> device.getIMEI()
        "getSerial" -> device.getSerial()
        "getAndroidId" -> device.getAndroidId(!args.isNull(0) && args.getBoolean(0))
        "getMacAddress" -> device.getMacAddress()
        "getSharedDeviceId" -> sharedDeviceId()
        else -> throw IllegalArgumentException("Unknown device method: $method")
    }

    /** As Rhino's device.setPointerLocation: nothing to do when the state is already the aim, else toggled by Root or Shizuku. */
    private fun setPointerLocation(enabled: Boolean) =
        PointerLocationTool.checkPointerLocationState(context, enabled) || PointerLocationTool.togglePointerLocation(context)

    /** The query of Rhino's device.getSharedDeviceId (modules/device/Device.kt). */
    private fun sharedDeviceId(): String? =
        context.contentResolver.query("content://org.autojs.autojs6.deviceid.provider/v1/device_id".toUri(), arrayOf("device_id"), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    companion object {

        private val IDENTIFIERS = setOf("getIMEI", "getSerial", "getAndroidId", "getMacAddress", "getSharedDeviceId")

        private val POWER = setOf("setPointerLocation", "togglePointerLocation")

        val methods: Set<String> = setOf(
            "summary", "getOrientation", "getRotation", "isScreenOff", "isScreenPortrait", "isScreenLandscape",
            "isPowerSourceAC", "isPowerSourceUSB", "isPowerSourceWireless", "isPowerSourceDock",
            "getIpAddress", "getIpv6Address", "getGatewayAddress", "isActiveNetworkMetered", "isConnectedOrConnecting", "isWifiAvailable",
            "isPointerLocationEnabled", "isPointerLocationDisabled", "hasReadPhoneStatePermission", "ensureReadPhoneStatePermission",
            "isBackgroundStartGranted",
        ) + POWER + IDENTIFIERS

        /** Q146 (a): `device`, plus `device.identifiers` for the hardware identifiers and `device.power` for the pointer location switches. */
        fun capabilities(method: String): List<String> = when (method) {
            in IDENTIFIERS -> listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_IDENTIFIERS)
            in POWER -> listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER)
            else -> listOf(NodeBridgePermissionManifest.DEVICE)
        }
    }
}
