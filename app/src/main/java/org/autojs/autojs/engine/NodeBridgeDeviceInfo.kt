package org.autojs.autojs.engine

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

/**
 * `device.info()` for the Node Bridge (MCP roadmap P1.3): build fields, screen size / density /
 * orientation / rotation, battery, host version and process, accessibility service state and
 * the screen state. No hardware identifiers (IMEI, serial, Android ID) are included.
 *
 * zh-CN: Node Bridge 的 `device.info()` (MCP 路线图 P1.3): 构建字段, 屏幕尺寸 / 密度 / 方向 / 旋转, 电量, 宿主版本与进程, 无障碍服务状态与屏幕状态.
 * 不包含硬件标识 (IMEI, 序列号, Android ID).
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal object NodeBridgeDeviceInfo {

    const val SCHEMA = "autojs6-bridge-device-info-v1"

    fun json(context: Context): JSONObject {
        val appContext = context.applicationContext
        return JSONObject()
            .put("schema", SCHEMA)
            .put("build", buildJson())
            .put("screen", section { screenJson(appContext) })
            .put("battery", section { batteryJson(appContext) })
            .put("memory", section { memoryJson(appContext) })
            .put("host", hostJson(appContext))
            .put("accessibility", section { accessibilityJson() })
            .put("screenOn", isScreenOn(appContext))
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timeZone", TimeZone.getDefault().id)
            .put("uptimeMs", SystemClock.elapsedRealtime())
            .put("now", System.currentTimeMillis())
    }

    private fun buildJson(): JSONObject = JSONObject()
        .put("brand", Build.BRAND)
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("product", Build.PRODUCT)
        .put("device", Build.DEVICE)
        .put("board", Build.BOARD)
        .put("hardware", Build.HARDWARE)
        .put("bootloader", Build.BOOTLOADER)
        .put("fingerprint", Build.FINGERPRINT)
        .put("sdkInt", Build.VERSION.SDK_INT)
        .put("release", Build.VERSION.RELEASE)
        .put("codename", Build.VERSION.CODENAME)
        .put("incremental", Build.VERSION.INCREMENTAL)
        .put("securityPatch", Build.VERSION.SECURITY_PATCH ?: JSONObject.NULL)
        .put("buildId", Build.ID)
        .put("display", Build.DISPLAY)
        .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))

    private fun screenJson(context: Context): JSONObject {
        val rotation = ScreenMetrics.rotation
        return JSONObject()
            .put("width", ScreenMetrics.deviceScreenWidth)
            .put("height", ScreenMetrics.deviceScreenHeight)
            .put("densityDpi", ScreenMetrics.deviceScreenDensity)
            .put("density", context.resources.displayMetrics.density.toDouble())
            .put("brightness", Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1))
            .put("brightnessMode", Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))
            .put("canWriteSettings", Settings.System.canWrite(context))
            .put("orientation", if (ScreenMetrics.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
            .put("rotation", rotation)
            .put("rotationDegrees", rotation * 90)
    }

    private fun batteryJson(context: Context): JSONObject {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return JSONObject().put("available", false)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not-charging"
            else -> "unknown"
        }
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            0 -> "none"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "unknown"
        }
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return JSONObject()
            .put("available", true)
            .put("percent", percent)
            .put("isCharging", status == "charging" || status == "full")
            .put("status", status)
            .put("plugged", plugged)
            .put("temperatureCelsius", if (temperature == Int.MIN_VALUE) JSONObject.NULL else temperature / 10.0)
    }

    private fun memoryJson(context: Context): JSONObject {
        val manager = checkNotNull(context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return JSONObject().put("totalMem", memory.totalMem).put("availMem", memory.availMem)
    }

    private fun hostJson(context: Context): JSONObject = JSONObject()
        .put("packageName", context.packageName)
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("processName", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else JSONObject.NULL)
        .put("pid", Process.myPid())

    private fun accessibilityJson(): JSONObject {
        val running = AutoJsAccessibilityService.instance != null
        return JSONObject()
            .put("serviceRunning", running)
            .put("operational", running && AutoJsAccessibilityService.registry.isOperational)
    }

    private fun isScreenOn(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isInteractive
    }

    private fun section(block: () -> JSONObject): JSONObject {
        return runCatching(block).getOrElse { error ->
            JSONObject().put("error", error.message ?: error.javaClass.name)
        }
    }
}
