package org.autojs.autojs.engine

import org.autojs.autojs.core.http.LocalNetworkDiagnostics

import android.Manifest
import android.accessibilityservice.AccessibilityService as AndroidAccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import android.database.sqlite.SQLiteProgram
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.automator.GlobalActionAutomator
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.bridge.BridgeNode
import org.autojs.autojs.core.automator.bridge.BridgeNodeActions
import org.autojs.autojs.core.automator.bridge.BridgeSelector
import org.autojs.autojs.core.automator.search.DFS
import org.autojs.autojs.core.plugin.mcp.McpHostCapabilityBroker
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optString
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.inrt.InrtToastVisibilityGuard
import org.autojs.autojs.runtime.api.ScriptToast
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult as PluginOcrResult
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.autojs.plugin.mediainfo.api.MediainfoSnapshotSchemas
import org.autojs.plugin.mediainfo.api.MediainfoQueryOptions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import okio.BufferedSink
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.plugin.mlkit.barcode.api.BarcodeFormats
import org.autojs.plugin.mlkit.barcode.api.BarcodeOptions
import org.autojs.plugin.mlkit.barcode.api.BarcodeResult
import org.autojs.plugin.mlkit.barcode.api.BarcodeResultExtraKeys
import org.autojs.plugin.mlkit.barcode.api.BarcodeValueTypes
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import kotlin.concurrent.thread

/**
 * A fetch request body or WebSocket message the plugin handed over as a read-only file
 * descriptor instead of inline base64 JSON (M20.2 batch 14), so Binder carries only the
 * small descriptor JSON whatever the payload size. The host owns [descriptor]; the broker
 * closes it once the call that consumed it has answered.
 */
class NodeBridgeRequestUpload(
    val descriptor: ParcelFileDescriptor,
    val byteCount: Long,
) : java.io.Closeable {

    /** Opens an independent stream over the same file, positioned at its start. */
    fun openStream(): java.io.FileInputStream {
        val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor.dup())
        stream.channel.position(0L)
        return stream
    }

    /** Reads the whole upload; callers apply their own size policy before calling. */
    fun readBytes(): ByteArray {
        if (byteCount < 0L || byteCount > Int.MAX_VALUE.toLong()) {
            throw IOException("AutoJs6 bridge upload byte count $byteCount is not readable.")
        }
        openStream().use { input ->
            val bytes = ByteArray(byteCount.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val read = input.read(bytes, offset, bytes.size - offset)
                if (read < 0) {
                    throw IOException("AutoJs6 bridge upload ended after $offset of $byteCount bytes.")
                }
                offset += read
            }
            return bytes
        }
    }

    override fun close() {
        runCatching { descriptor.close() }
    }
}

data class NodeBridgeRequest(
    val id: String,
    val module: String,
    val method: String,
    val argsJson: String,
    val timeoutMs: Long,
    val permissions: List<String> = emptyList(),
    val events: Boolean = false,
    val binary: Boolean = false,
    /** Request body or message handed over as a descriptor (M20.2 batch 14); owned by the host. */
    val upload: NodeBridgeRequestUpload? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("module", module)
        put("method", method)
        put("args", NodeBridgeJson.parseJsonValue(argsJson.ifBlank { "[]" }))
        put("timeoutMs", timeoutMs)
        put("permissions", JSONArray(permissions))
        if (events) put("events", true)
        if (binary) put("binary", true)
        upload?.let { put("upload", JSONObject().put("byteCount", it.byteCount)) }
    }.toString()

    companion object {
        fun fromJson(json: String): NodeBridgeRequest {
            val value = JSONObject(json)
            val id = value.requiredRequestString("id")
            val module = value.requiredRequestString("module")
            val method = value.requiredRequestString("method")
            return NodeBridgeRequest(
                id = id,
                module = module,
                method = method,
                argsJson = parseArgs(value).toString(),
                timeoutMs = parseTimeoutMs(value),
                permissions = parsePermissions(value),
                events = value.optBoolean("events", false),
                binary = value.optBoolean("binary", false),
            )
        }

        fun fromJsonArray(json: String): List<NodeBridgeRequest> {
            val value = runCatching {
                JSONTokener(json.trim().ifEmpty { "[]" }).nextValue()
            }.getOrNull() as? JSONArray ?: return emptyList()
            return (0 until value.length()).mapNotNull { index ->
                value.optJSONObject(index)?.let { requestJson ->
                    runCatching { fromJson(requestJson.toString()) }.getOrNull()
                }
            }
        }

        fun recoverId(json: String): String? = recoverString(json, "id")

        fun recoverModule(json: String): String? = recoverString(json, "module")

        fun recoverMethod(json: String): String? = recoverString(json, "method")

        private fun recoverString(json: String, name: String): String? {
            return runCatching {
                JSONObject(json).optionalRequestString(name)
            }.getOrNull()
        }

        private fun JSONObject.requiredRequestString(name: String): String {
            return optionalRequestString(name)
                ?: throw IllegalArgumentException("Node bridge request $name must be a non-empty string.")
        }

        private fun JSONObject.optionalRequestString(name: String): String? {
            val value = opt(name)
            return (value as? String)?.takeIf { it.isNotBlank() }
        }

        private fun parseArgs(value: JSONObject): JSONArray {
            if (!value.has("args") || value.isNull("args")) {
                return JSONArray()
            }
            return value.opt("args") as? JSONArray
                ?: throw IllegalArgumentException("Node bridge request args must be a JSON array.")
        }

        private fun parsePermissions(value: JSONObject): List<String> {
            if (!value.has("permissions") || value.isNull("permissions")) {
                return emptyList()
            }
            val permissions = value.opt("permissions") as? JSONArray
                ?: throw IllegalArgumentException("Node bridge request permissions must be a JSON array.")
            return (0 until permissions.length()).map { index ->
                val permission = permissions.opt(index)
                if (permission !is String || permission.isBlank()) {
                    throw IllegalArgumentException("Node bridge request permissions must contain non-empty strings.")
                }
                permission
            }
        }

        private fun parseTimeoutMs(value: JSONObject): Long {
            if (!value.has("timeoutMs") || value.isNull("timeoutMs")) {
                return DEFAULT_TIMEOUT_MS
            }
            val timeoutValue: Any? = value.opt("timeoutMs")
            val timeoutMs = when (timeoutValue) {
                is Int -> timeoutValue.toLong()
                is Long -> timeoutValue
                is Number -> {
                    val doubleValue = timeoutValue.toDouble()
                    if (!doubleValue.isFinite()) {
                        null
                    } else {
                        doubleValue.toLong()
                    }
                }
                else -> null
            } ?: throw IllegalArgumentException("Node bridge request timeoutMs must be a positive number.")
            if (timeoutMs <= 0L) {
                throw IllegalArgumentException("Node bridge request timeoutMs must be a positive number.")
            }
            return timeoutMs.coerceAtMost(MAX_TIMEOUT_MS)
        }
    }
}

data class NodeBridgeResponse(
    val id: String,
    val ok: Boolean,
    val resultJson: String? = null,
    val error: NodeBridgeError? = null,
    val event: Boolean = false,
    val subscriptionId: String? = null,
    val binary: android.os.ParcelFileDescriptor? = null,
    val binaryByteCount: Long = 0,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("ok", ok)
        if (event) {
            put("event", true)
            put("subscriptionId", subscriptionId)
        }
        if (ok) {
            put("result", NodeBridgeJson.parseJsonValue(resultJson ?: "null"))
        } else {
            put("error", error?.toJsonObject() ?: NodeBridgeError.processDead().toJsonObject())
        }
    }.toString()

    companion object {
        fun success(id: String, resultJson: String? = null): NodeBridgeResponse {
            return NodeBridgeResponse(id = id, ok = true, resultJson = resultJson)
        }

        fun failure(id: String, error: NodeBridgeError): NodeBridgeResponse {
            return NodeBridgeResponse(id = id, ok = false, error = error)
        }

        fun fromJson(json: String): NodeBridgeResponse {
            val value = JSONObject(json)
            val ok = value.optBoolean("ok", false)
            return if (ok) {
                NodeBridgeResponse(
                    id = value.getString("id"),
                    ok = true,
                    resultJson = NodeBridgeJson.valueToJson(value.opt("result")),
                    event = value.optBoolean("event", false),
                    subscriptionId = value.optString("subscriptionId").takeIf { it.isNotBlank() },
                )
            } else {
                NodeBridgeResponse(
                    id = value.getString("id"),
                    ok = false,
                    error = value.optJSONObject("error")?.let(NodeBridgeError::fromJsonObject)
                        ?: NodeBridgeError.processDead(),
                )
            }
        }
    }
}

object NodeBridgeErrorCategory {
    const val UNAVAILABLE = "unavailable"
    const val PERMISSION_DENIED = "permission-denied"
    const val CAPABILITY_DENIED = "capability-denied"
    const val PROVIDER_FAILED = "provider-failed"
    const val INVALID_REQUEST = "invalid-request"
    const val RESOURCE_LIMIT = "resource-limit"
    const val RATE_LIMITED = "rate-limited"
    const val TIMEOUT = "timeout"
    const val PROCESS_DEAD = "process-dead"
    const val RUNTIME_ERROR = "runtime-error"

    fun fromCode(code: String): String = when (code) {
        NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED -> CAPABILITY_DENIED
        NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED,
        NodeRuntimeErrorCodes.NETWORK_PERMISSION_DENIED,
        NodeRuntimeErrorCodes.NETWORK_POLICY_DENIED -> PERMISSION_DENIED
        NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST,
        NodeRuntimeErrorCodes.NETWORK_INVALID_URL -> INVALID_REQUEST
        NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT,
        NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE -> RESOURCE_LIMIT
        NodeRuntimeErrorCodes.BRIDGE_RATE_LIMITED -> RATE_LIMITED
        NodeRuntimeErrorCodes.BRIDGE_TIMEOUT,
        NodeRuntimeErrorCodes.NETWORK_TIMEOUT -> TIMEOUT
        NodeRuntimeErrorCodes.NETWORK_DISABLED -> UNAVAILABLE
        NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED -> PROVIDER_FAILED
        NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD -> PROCESS_DEAD
        else -> RUNTIME_ERROR
    }
}

data class NodeBridgeError(
    val name: String = "Error",
    val message: String,
    val code: String,
    val module: String? = null,
    val method: String? = null,
    val stack: String? = null,
    val missingCapabilities: List<String> = emptyList(),
    val category: String = NodeBridgeErrorCategory.fromCode(code),
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("name", name)
        put("message", message)
        put("code", code)
        put("category", category)
        module?.let { put("module", it) }
        method?.let { put("method", it) }
        stack?.let { put("stack", it) }
        if (missingCapabilities.isNotEmpty()) {
            put("missingCapabilities", JSONArray(missingCapabilities))
        }
    }

    companion object {
        fun fromJsonObject(value: JSONObject): NodeBridgeError {
            val code = value.optString("code", NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD)
            return NodeBridgeError(
                name = value.optString("name", "Error"),
                message = value.optString("message"),
                code = code,
                module = value.optString("module").takeIf { it.isNotBlank() },
                method = value.optString("method").takeIf { it.isNotBlank() },
                stack = value.optString("stack").takeIf { it.isNotBlank() },
                missingCapabilities = value.optionalStringArray("missingCapabilities"),
                category = value.optNonBlankString("category") ?: NodeBridgeErrorCategory.fromCode(code),
            )
        }

        fun timeout(module: String? = null, method: String? = null): NodeBridgeError {
            return NodeBridgeError(
                message = "AutoJs6 bridge call timed out.",
                code = NodeRuntimeErrorCodes.BRIDGE_TIMEOUT,
                module = module,
                method = method,
            )
        }

        fun processDead(message: String = "AutoJs6 bridge process is not available."): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD,
            )
        }

        fun invalidRequest(message: String = "AutoJs6 bridge request is malformed.", module: String? = null, method: String? = null): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST,
                module = module,
                method = method,
            )
        }

        fun permissionDenied(
            message: String,
            module: String? = null,
            method: String? = null,
            category: String = NodeBridgeErrorCategory.PERMISSION_DENIED,
        ): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED,
                module = module,
                method = method,
                category = category,
            )
        }

        fun rateLimited(message: String, module: String? = null, method: String? = null): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = NodeRuntimeErrorCodes.BRIDGE_RATE_LIMITED,
                module = module,
                method = method,
            )
        }

        fun resourceLimit(message: String, module: String? = null, method: String? = null): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT,
                module = module,
                method = method,
                category = NodeBridgeErrorCategory.RESOURCE_LIMIT,
            )
        }

        fun providerFailed(
            message: String,
            module: String? = null,
            method: String? = null,
            stack: String? = null,
            code: String = NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD,
        ): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = code,
                module = module,
                method = method,
                stack = stack,
                category = NodeBridgeErrorCategory.PROVIDER_FAILED,
            )
        }

        fun bridgeUnavailable(module: String, method: String): NodeBridgeError {
            return permissionDenied(
                message = "AutoJs6 bridge module or method is not available: $module.$method",
                module = module,
                method = method,
                category = NodeBridgeErrorCategory.UNAVAILABLE,
            )
        }

        fun capabilityProviderMissing(module: String, method: String, requirement: String): NodeBridgeError {
            return permissionDenied(
                message = "AutoJs6 $module bridge requires $requirement.",
                module = module,
                method = method,
                category = NodeBridgeErrorCategory.UNAVAILABLE,
            )
        }

        fun capabilityNotDeclared(module: String, method: String, missingCapabilities: List<String>): NodeBridgeError {
            val missing = missingCapabilities.filter { it.isNotBlank() }.distinct()
            return NodeBridgeError(
                message = "AutoJs6 bridge capability is not declared for $module.$method: ${missing.joinToString(", ")}",
                code = NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED,
                module = module,
                method = method,
                missingCapabilities = missing,
                category = NodeBridgeErrorCategory.CAPABILITY_DENIED,
            )
        }

        fun network(
            message: String,
            code: String,
            module: String? = null,
            method: String? = null,
            category: String = NodeBridgeErrorCategory.fromCode(code),
        ): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = code,
                module = module,
                method = method,
                category = category,
            )
        }

        fun javaInterop(
            message: String,
            code: String,
            module: String? = null,
            method: String? = null,
            stack: String? = null,
        ): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = code,
                module = module,
                method = method,
                stack = stack,
            )
        }
    }
}

private fun JSONObject.optNonBlankString(name: String): String? {
    return optString(name).takeIf { it.isNotBlank() }
}

private fun JSONObject.optionalStringArray(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        (array.opt(index) as? String)?.takeIf { it.isNotBlank() }
    }
}

data class NodeBridgeEngineInfo(
    val id: String,
    val engineName: String = "nodejs",
    // Historical wire token consumed by the plugin's work_manager shim. It identifies the
    // Node execution family and does not imply that this host packages or loads libnode.
    val backend: String = "embedded",
    val sourceName: String = "",
    val cwd: String = "",
    val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
    val launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    val spawnDepth: Int = 0,
    val processName: String = "",
    val pid: Int = Process.myPid(),
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("engineName", engineName)
        put("backend", backend)
        put("sourceName", sourceName)
        put("cwd", cwd)
        put("executionMode", executionMode)
        put("launchSurface", launchSurface)
        put("spawnDepth", spawnDepth)
        put("processName", processName)
        put("pid", pid)
        put("packageName", packageName)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("startedAt", startedAt)
    }

    fun toJson(): String = toJsonObject().toString()

    companion object {
        fun fromContext(
            context: Context,
            id: String = "",
            sourceName: String = "",
            cwd: String = "",
            executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
            launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
            spawnDepth: Int = 0,
            processName: String = "",
            pid: Int = Process.myPid(),
            startedAt: Long = System.currentTimeMillis(),
        ): NodeBridgeEngineInfo {
            val appContext = context.applicationContext
            val packageName = appContext.packageName
            val packageInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    appContext.packageManager.getPackageInfo(packageName, 0)
                }
            }.getOrNull()
            val versionCode = packageInfo?.let { info ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            } ?: 0L
            return NodeBridgeEngineInfo(
                id = id,
                sourceName = sourceName,
                cwd = cwd,
                executionMode = executionMode,
                launchSurface = launchSurface,
                spawnDepth = spawnDepth.coerceIn(0, MAX_SPAWN_DEPTH),
                processName = processName,
                pid = pid,
                packageName = packageName,
                versionName = packageInfo?.versionName.orEmpty(),
                versionCode = versionCode,
                startedAt = startedAt,
            )
        }

        const val SPAWN_DEPTH_ARGUMENT = "autojs6.node.spawnDepth"
        const val MAX_SPAWN_DEPTH = 1
    }
}

object NodeBridgeModules {

    const val TOAST = NodeBridgePermissionManifest.TOAST
    const val APP = NodeBridgePermissionManifest.APP
    const val ENGINES = NodeBridgePermissionManifest.ENGINES
    const val AGENT = NodeBridgePermissionManifest.AGENT
    const val DIALOGS = NodeBridgePermissionManifest.DIALOGS
    const val ACCESSIBILITY = NodeBridgePermissionManifest.ACCESSIBILITY
    const val SCREEN_RECORDER = "screen_recorder"
    const val MEDIA_PROJECTION = "media_projection"
    const val IMAGE = NodeBridgePermissionManifest.IMAGE
    const val IMAGES = "images"
    const val OCR = NodeBridgePermissionManifest.OCR
    const val BARCODE = NodeBridgePermissionManifest.BARCODE
    const val MEDIA = NodeBridgePermissionManifest.MEDIA
    const val MEDIAINFO = "mediainfo"
    const val MEDIA_STORE = "media_store"
    const val RECORDER = "recorder"
    const val PINYIN = NodeBridgePermissionManifest.PINYIN
    const val OPENCC = NodeBridgePermissionManifest.OPENCC
    const val PINYIN4J = NodeBridgePermissionManifest.PINYIN4J
    const val STORAGE = NodeBridgePermissionManifest.STORAGE
    const val STORAGES = "storages"
    const val DATABASE = NodeBridgePermissionManifest.DATABASE
    const val SQLITE = "sqlite"
    const val NOTIFICATIONS = NodeBridgePermissionManifest.NOTIFICATIONS
    const val SENSORS = NodeBridgePermissionManifest.SENSORS
    const val COMPOSE = "compose"
    const val UI = NodeBridgePermissionManifest.UI
    const val UI_OVERLAY = NodeBridgePermissionManifest.UI_OVERLAY
    const val CLIPBOARD = NodeBridgePermissionManifest.CLIPBOARD
    const val DEVICE = NodeBridgePermissionManifest.DEVICE
    const val SCREEN_METRICS = "screen_metrics"
    const val SHELL = NodeBridgePermissionManifest.SHELL
    const val FETCH = "fetch"
    const val WEBSOCKET = "websocket"
    const val ANDROID_OBJECTS = "android"
    const val TASKS = "tasks"
    const val WORK_MANAGER = NodeBridgePermissionManifest.WORK_MANAGER
    const val PACKAGE_MANAGER = NodeBridgePermissionManifest.PACKAGE_MANAGER
    const val MAIL = "mail"
    const val FLOW = "flow"
    const val AI = "ai"
    const val AI_AGENT = "aiAgent"
    const val EPUB = "epub"
    const val SPEECH = "speech"
    const val TTS = "tts"
    const val JAVA = "java"
    const val RHINO = NodeBridgePermissionManifest.RHINO
    const val INPUT_OBSERVER = NodeBridgePermissionManifest.INPUT_OBSERVER
    const val FILES = NodeBridgePermissionManifest.FILES
    const val CONSOLE = NodeBridgePermissionManifest.CONSOLE
    const val PLUGIN_TOOLS = NodeBridgePermissionManifest.PLUGIN_TOOLS
    const val EVENTS = NodeBridgePermissionManifest.EVENTS
    const val KEYS = NodeBridgePermissionManifest.KEYS

    val toastMethods = setOf("showToast", "toast")
    val pluginToolsMethods = setOf("list", "invoke", "cancel")
    val appMethods = setOf(
        "launchPackage",
        "launchApp",
        "openAppSetting",
        "startActivity",
        "getPackageName",
        "getAppName",
        "isInstalled",
        "viewFile",
        "editFile",
        "currentWindow",
        "listSamples",
        "readSample",
        "listDocs",
        "readDoc",
        "pluginCapabilities",
        "startService",
        "sendBroadcast",
        "getInstalledApps",
        "getPackageInfo",
    )
    val enginesMethods = setOf("myEngine", "all", "stopAll", "stopSelf", "execScript", "execScriptFile", "list", "stop")
    val agentMethods = setOf("listScripts", "readManifest", "execRegistered")
    val dialogsMethods = setOf("alert", "confirm", "input", "select", "rawInput", "singleChoice", "multiChoice", "progressShow", "progressUpdate", "progressDismiss", "cancelRequest", "closeScope")
    val keysMethods = setOf("notifications", "quickSettings", "lockScreen", "takeScreenshot", "splitScreen", "powerDialog")
    val accessibilityMethods = setOf(
        "isEnabled",
        "ensureEnabled",
        "click",
        "back",
        "home",
        "recentApps",
        "findByText",
        "clickText",
        "findOne",
        "findAll",
        "flowProbe",
        "flowForeground",
        "longClick",
        "setText",
        "scrollForward",
        "scrollBackward",
        "swipe",
        "gesture",
        "dump",
        "explain",
        "screenshot",
        "readScreenText",
        "inspectNode",
        "captureScreenImage",
    )
    val screenRecorderMethods = setOf("start", "stop", "cancel", "getStatus")
    val mediaProjectionMethods = setOf("requestScreenCapture", "nextImage", "stop")
    val imageModules = setOf(IMAGE, IMAGES)
    val imageMethods = setOf(
        "captureScreen",
        "toBytes",
        "readImage",
        "saveImage",
        "clip",
        "resize",
        "grayscale",
        "threshold",
        "findImage",
        "matchTemplate",
        "findColor",
        "findMultiColors",
        "recycle",
        "fromBase64", "fromBytes", "toBase64", "pixel", "readPixels", "copy", "rotate", "scale", "flip",
        "blur", "gaussianBlur", "medianBlur", "invert", "detectsColor", "findAllPointsForColor",
        "initOpenCvIfNeeded", "isGrayscale", "inRange", "interval", "adaptiveThreshold", "bilateralFilter", "cvtColor", "findCircles", "concat", "detectMultiColors", "findPointsByColors", "countPointsByColor", "getMeanColor", "imread", "matToImage", "detectAndComputeFeatures", "matchFeatures", "quantize", "quantizeToFile", "compress", "compressToBytes", "downsample", "psnr", "ssim", "mssim", "hist", "mse", "ncc", "isEqual", "getSimilarity", "load", "getScreenCaptureOptions",
    )
    val ocrMethods = setOf("recognize", "recognizeText", "detectTextBounds")
    val barcodeMethods = setOf("detect", "detectAll", "recognizeText", "recognizeTexts")
    val mediaAudioStreamMethods = setOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo", "setAudioStreamVolume")
    val mediaPlaybackMethods = setOf("play", "pause", "resume", "stop", "seekTo", "getPlaybackStatus")
    val mediaMethods = mediaAudioStreamMethods + mediaPlaybackMethods
    val mediaStoreMethods = setOf("capabilities", "query", "get", "insert", "update", "delete", "scanFile", "exportFile")
    val mediainfoMethods = setOf("read", "get", "countGet", "capabilities")
    val recorderMethods = setOf("getStatus", "start", "stop")
    val pinyinMethods = setOf("convert", "simple", "compare", "compact", "fromCodePoint", "fromPhrase")
    val pinyin4jMethods = setOf("of", "as", "readings")
    val openccMethods = NodeOpenccBridge.METHODS
    val storageModules = setOf(STORAGE, STORAGES)
    val storageMethods = setOf("get", "put", "remove", "clear", "keys", "contains")
    val databaseModules = setOf(DATABASE, SQLITE)
    val databaseMethods = setOf("open", "exec", "run", "get", "all", "transaction", "close")
    val notificationsMethods = setOf("notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings")
    val sensorsMethods = setOf("getAvailableSensors", "once", "subscribe", "unsubscribe", "drainEvents")
    val uiMethods = setOf("showLayout", "update", "batchUpdate", "close", "drainEvents") + RuntimeUiInterop.methods
    val uiOverlayMethods = setOf("show", "update", "drainEvents", "close", "closeAll", "hasPermission", "openPermissionSettings")
    val clipboardMethods = setOf("getText", "setText", "hasText")
    val deviceMethods = setOf("isScreenOn", "wakeUp", "vibrate", "isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings", "info",
        "setBrightness", "setBrightnessMode", "keepScreenOn", "cancelKeepingAwake", "closeScope")
    val shellMethods = setOf("exec", "spawn", "open", "drain", "write", "closeInput", "kill", "close", "getAccess", "requestShizukuPermission")
    val fetchMethods = setOf("request")
    val websocketMethods = setOf("connect", "send", "close", "drainEvents")
    val workManagerMethods = setOf("scheduleOnce", "schedulePeriodic", "cancel", "list")
    val packageManagerMethods = setOf(
        "list",
        "listApps",
        "verify",
        "prune",
        "planInstall",
        "planUpdate",
        "planRemove",
        "install",
        "update",
        "remove",
    )
    val javaMethods = ControlledJavaInteropPolicy.methods
    val rhinoMethods = setOf("run")
    val inputObserverMethods = setOf("observeKeys", "drainEvents", "close", "getAvailableSources")
    val filesMethods = setOf("list", "stat", "read", "write", "mkdir", "rename", "delete")
    val consoleMethods = setOf("tail") + RuntimeConsoleBridge.METHODS
    val screenMetricsMethods = RuntimeCoordinateBridge.METHODS
    val eventsMethods = setOf("observeNotification", "observeToast", "observeKey", "observeBroadcasts", "drainEvents", "close")

    val supportedMethodsByModule: Map<String, Set<String>> = mapOf(
        TOAST to toastMethods,
        APP to appMethods,
        ENGINES to enginesMethods,
        AGENT to agentMethods,
        DIALOGS to dialogsMethods,
        ACCESSIBILITY to accessibilityMethods,
        SCREEN_RECORDER to screenRecorderMethods,
        MEDIA_PROJECTION to mediaProjectionMethods,
        IMAGE to imageMethods,
        IMAGES to imageMethods,
        OCR to ocrMethods,
        BARCODE to barcodeMethods,
        MEDIA to mediaMethods,
        MEDIAINFO to mediainfoMethods,
        MEDIA_STORE to mediaStoreMethods,
        RECORDER to recorderMethods,
        PINYIN to pinyinMethods,
        OPENCC to openccMethods,
        PINYIN4J to pinyin4jMethods,
        STORAGE to storageMethods,
        STORAGES to storageMethods,
        DATABASE to databaseMethods,
        SQLITE to databaseMethods,
        NOTIFICATIONS to notificationsMethods,
        SENSORS to sensorsMethods,
        UI to uiMethods,
        COMPOSE to RuntimeComposeBridge.methods,
        UI_OVERLAY to uiOverlayMethods,
        CLIPBOARD to clipboardMethods,
        DEVICE to deviceMethods,
        SCREEN_METRICS to screenMetricsMethods,
        EVENTS to eventsMethods,
        KEYS to keysMethods,
        SHELL to shellMethods,
        FETCH to fetchMethods,
        WEBSOCKET to websocketMethods,
        ANDROID_OBJECTS to RuntimeAndroidObjects.methods,
        TASKS to RuntimeTasksBridge.methods,
        WORK_MANAGER to workManagerMethods,
        PACKAGE_MANAGER to packageManagerMethods,
        MAIL to RuntimeMailBridge.methods,
        FLOW to RuntimeFlowBridge.methods,
        AI to RuntimeAiBridge.methods,
        AI_AGENT to RuntimeAgentBridge.methods,
        EPUB to RuntimeEpubBridge.methods,
        SPEECH to RuntimeSpeechBridge.methods,
        TTS to RuntimeTtsBridge.methods,
        JAVA to javaMethods,
        RHINO to rhinoMethods,
        INPUT_OBSERVER to inputObserverMethods,
        FILES to filesMethods,
        CONSOLE to consoleMethods,
        PLUGIN_TOOLS to pluginToolsMethods,
    )

    fun supports(module: String, method: String): Boolean {
        return supportedMethodsByModule[module]?.contains(method) == true
    }
}

interface NodeBridgeCapabilityProvider {
    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit)

    fun nativeDiagnostics(): Map<String, String> = emptyMap()

    fun healthCheck(): List<NodeBridgeProviderHealth> = NodeBridgeProviderRegistry.unavailableHealth()

    fun destroy(reason: NodeBridgeError = NodeBridgeError.processDead()) = Unit
}

class FakeNodeBridgeCapabilityProvider(
    private val handler: (NodeBridgeRequest) -> NodeBridgeResponse = { request ->
        NodeBridgeResponse.success(request.id, request.argsJson)
    },
) : NodeBridgeCapabilityProvider {

    val requests: MutableList<NodeBridgeRequest> = mutableListOf()

    override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        requests += request
        callback(handler(request))
    }

    override fun healthCheck(): List<NodeBridgeProviderHealth> = NodeBridgeProviderRegistry.fakeHealth()
}

class AndroidNodeBridgeCapabilityProvider(
    context: Context,
    private val engineInfo: NodeBridgeEngineInfo = NodeBridgeEngineInfo.fromContext(context),
    private val javaInteropEnabled: Boolean = true,
    private val bridgeLimits: NodeBridgeLimits.Policy = NodeBridgeLimits.defaults,
    private val pinyinBackend: NodePinyinBackend = AndroidNodePinyinBackend(context),
    private val pinyin4jBackend: NodePinyin4jBackend = AndroidNodePinyin4jBackend(context),
    private val openccBackend: NodeOpenccBackend = AndroidNodeOpenccBackend(context),
    private val permissionManifest: NodeBridgePermissionManifest.Manifest? = null,
    private val agentScriptRoots: List<File> = emptyList(),
    private val executionConsole: org.autojs.autojs.core.console.ConsoleImpl? = null,
    private val agentExecutionState: org.autojs.autojs.execution.AgentScriptExecutionState? = null,
    resources: RuntimeResourceScope? = null,
) : NodeBridgeCapabilityProvider {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkClient = OkHttpClient.Builder()
        .addInterceptor(LocalNetworkDiagnostics { appContext })
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val networkCalls = ConcurrentHashMap<String, Call>()
    private val networkDiagnostics = ControlledFetchDiagnostics()
    private val websocketSessions = ConcurrentHashMap<String, ControlledWebSocketSession>()
    private val websocketDiagnostics = ControlledWebSocketDiagnostics()
    private val imageHandles = ConcurrentHashMap<String, ControlledImageHandle>()
    /**
     * A plugin session that serves a model (MCP Server, 3-Stove Agent). Node.js plugin scripts also run
     * with backend "plugin" but are user code and keep the script-facing behaviour.
     */
    private val modelFacingPluginSession: Boolean =
        engineInfo.backend == "plugin" && engineInfo.engineName in MODEL_FACING_ENGINES
    /** Path policy of model-facing sessions (MCP roadmap P11 D26); null means script behaviour. */
    @Suppress("DEPRECATION")
    private val modelPathPolicy: NodeBridgeModelPathPolicy? = if (!modelFacingPluginSession) null else NodeBridgeModelPathPolicy(
        workingDirectory = File(engineInfo.cwd.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath),
        sharedStorageRoot = android.os.Environment.getExternalStorageDirectory(),
        hasStorageAccess = { org.autojs.autojs.permission.AllFilesAccessPermission(appContext).has() },
    )
    private val imageLock = Any()
    private val imageDestroyed = AtomicBoolean(false)
    private val imageExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AutoJs6NodeImage").apply { isDaemon = true }
    }
    private val screenCapture = NodeScreenCaptureSession(appContext, ::onScreenCaptureStopped)
    private val agentHelpersLock = Any()
    private var agentHelpersClosed = false
    private val screenText = lazy { NodeBridgeScreenText(appContext, { screenCapture.capture() }) }
    private val agentBridge = lazy { NodeBridgeAgentBridge(appContext, engineInfo, agentScriptRoots) }
    private val mailScopeLock = Any()
    private val flowScopeLock = Any()
    private var flowScopeClosed = false
    private val flowBridge = lazy { RuntimeFlowBridge(appContext) }
    internal fun closeFlowClient(id: String) { synchronized(flowScopeLock) { if (flowBridge.isInitialized()) flowBridge.value.closeClient(id) } }
    private var mailScopeClosed = false
    private val mailBridge = lazy { RuntimeMailBridge(appContext, engineInfo.cwd) }
    internal fun closeMailClient(id: String) { if (mailBridge.isInitialized()) mailBridge.value.closeClient(id) }
    private val aiAgentScopeLock = Any()
    private var aiAgentScopeClosed = false
    private val aiAgentBridge = lazy { RuntimeAgentBridge(appContext, engineInfo.cwd, executionState = { agentExecutionState }) }
    internal fun closeAiAgentClient(id: String) { if (aiAgentBridge.isInitialized()) aiAgentBridge.value.closeClient(id) }
    private val aiScopeLock = Any()
    private var aiScopeClosed = false
    private val aiBridge = lazy { RuntimeAiBridge(appContext) }
    internal fun closeAiClient(id: String) { if (aiBridge.isInitialized()) aiBridge.value.closeClient(id) }
    private val epubScopeLock = Any()
    private var epubScopeClosed = false
    private val epubBridge = lazy { RuntimeEpubBridge(appContext, engineInfo.cwd) }
    internal fun closeEpubClient(id: String) { if (epubBridge.isInitialized()) epubBridge.value.closeClient(id) }
    private val speechScopeLock = Any()
    private var speechScopeClosed = false
    private val speechBridge = lazy { RuntimeSpeechBridge(appContext, engineInfo.cwd) }
    internal fun closeSpeechClient(id: String) { if (speechBridge.isInitialized()) speechBridge.value.closeClient(id) }
    private val ttsScopeLock = Any()
    private var ttsScopeClosed = false
    private val ttsBridge = lazy { RuntimeTtsBridge(appContext, engineInfo.cwd) }
    internal fun closeTtsClient(id: String) { if (ttsBridge.isInitialized()) ttsBridge.value.closeSession(id) }
    private val deviceControls = NodeDeviceControls(appContext)
    private val eventObservers = NodeEventObservers(appContext)
    private val dialogs = NodeDialogs(appContext)
    private val androidObjects = RuntimeAndroidObjects(appContext,engineInfo.cwd)
    private val calendarTasks = RuntimeTasksBridge(appContext,engineInfo.cwd)
    internal fun closeAndroidObjects() = androidObjects.close()
    private val audioRecorder = lazy { NodeAudioRecorder(appContext, ::resolveAndroidFile) }
    private val mediaPlayback = lazy { NodeMediaPlayback(appContext, ::resolveAndroidFile) }
    private val mediaStore = lazy { NodeMediaStore(appContext, ::resolveAndroidFile) }
    private val imageOperations = NodeImageOperations(appContext)
    private val advancedImages = lazy { NodeAdvancedImages(appContext, resourceRegistry, ::checkImageRoom) }
    private val ocrOperations = ConcurrentHashMap<String, ControlledOcrOperation>()
    private val ocrDiagnostics = ControlledOcrDiagnostics()
    private val barcodeOperations = ConcurrentHashMap<String, ControlledBarcodeOperation>()
    private val barcodeDiagnostics = ControlledBarcodeDiagnostics()
    private val bridgePermissionManifest by lazy {
        permissionManifest ?: NodeBridgePermissionManifest.fromWorkingDirectory(engineInfo.cwd)
    }
    private val databaseHandles = ConcurrentHashMap<String, ControlledDatabaseHandle>()
    private val databaseHandleCounter = AtomicInteger(1)
    private val databaseDestroyed = AtomicBoolean(false)
    @Volatile private var databaseThread: Thread? = null
    private val databaseExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoJs6NodeDatabase").apply { isDaemon = true; databaseThread = this }
    }
    private val workManagerTasks = ConcurrentHashMap<String, Map<String, Any?>>()
    private val packageInstallManager by lazy {
        NodeAndroidPackageInstallManager(File(appContext.filesDir, "nodejs/package-manager-store"))
    }
    private val javaHandles = ConcurrentHashMap<String, Any>()
    private val javaLock = Any()
    private val javaSessionDelegate: Lazy<RuntimeJavaSession> = lazy { RuntimeJavaSession(appContext,engineInfo.cwd,javaInteropHandles) { resourceRegistry.close(it,"java.callback.argument") } }
    private val javaSession: RuntimeJavaSession by javaSessionDelegate
    @Volatile private var javaClosed = false
    internal fun closeJavaObjects() = synchronized(javaLock) {
        javaClosed = true
        if(javaSessionDelegate.isInitialized())javaSession.close()
        javaHandles.keys.toList().forEach { resourceRegistry.close(it, "java.execution.finished") }
        javaHandles.clear()
    }
    internal fun releaseJavaResult(json: String?) {
        fun release(value: Any?) {
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) release(value.opt(i))
                is JSONObject -> if (value.has("__autojs6JavaHandle")) resourceRegistry.close(value.getString("__autojs6JavaHandle"), "java.abandoned")
            }
        }
        runCatching { release(org.json.JSONTokener(json ?: "null").nextValue()) }
    }
    private val notificationHandles = ConcurrentHashMap<Pair<String, Int>, NodeBridgeResourceHandle>()
    private val notificationTag = "autojs6-runtime-${UUID.randomUUID()}"
    private val notificationsClosed = AtomicBoolean(false)
    internal fun closeDeviceDialogResources() { deviceControls.close(); dialogs.closeAll() }
    internal fun cancelDialogRequest(requestId: String) { dialogs.cancelRequest(requestId) }
    internal fun closeEventResources() {
        eventObservers.closeAll()
        synchronized(notificationHandles) {
            notificationsClosed.set(true)
            notificationHandles.values.toList().forEach { it.close("execution-ended") }
        }
    }
    private val notificationIdCounter = AtomicInteger(1_000_000)
    private val sensorLock = Any()
    private var sensorsDestroyed = false
    private val sensorSubscriptions = ConcurrentHashMap<String, ControlledSensorSubscription>()
    private val uiInterop = lazy { RuntimeUiInterop(appContext) { value ->
        synchronized(imageLock) { requireNotNull(imageArgument(JSONArray().put(value), 0).bitmap.copy(Bitmap.Config.ARGB_8888, false)) }
    } }
    private val composeBridge = lazy { RuntimeComposeBridge(appContext, uiInterop.value) { value ->
        synchronized(imageLock) { requireNotNull(imageArgument(JSONArray().put(value), 0).bitmap.copy(Bitmap.Config.ARGB_8888, false)) }
    } }
    private val uiSessionIds = ConcurrentHashMap.newKeySet<String>()
    private val uiDispatchReceivers = ConcurrentHashMap.newKeySet<AutoCloseable>()
    private val overlaySessionIds = ConcurrentHashMap.newKeySet<String>()
    private val overlaySessions = ConcurrentHashMap<String, ControlledOverlaySession>()
    private val overlayPermissionSettingsLaunches = AtomicInteger(0)
    private val inputObserverSubscriptions = ConcurrentHashMap<String, ControlledInputObserverSubscription>()
    private val sensorManager by lazy {
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val resourceRegistry = NodeBridgeResourceRegistry(
        engineInfo.id,
        tracker = resources?.let { scope -> { resource, name -> scope.track(resource, name) } },
    )
    private val shellScopeLock=Any()
    private var shellScopeClosed=false
    private val shellBridge=lazy { RuntimeShellBridge(engineInfo.cwd.ifBlank { appContext.filesDir.path },resourceRegistry,
        {tryAcquireShellCommand().also { if(!it)bridgeResourceLimitRejections.incrementAndGet() }},{activeShellCommands.updateAndGet { (it-1).coerceAtLeast(0) }},{bridgePermissionManifest}) }
    internal fun cancelShellRequest(id:String){if(shellBridge.isInitialized())shellBridge.value.cancelRequest(id)}
    internal fun closeShellProcesses(){synchronized(shellScopeLock){shellScopeClosed=true;if(shellBridge.isInitialized())shellBridge.value.close()}}

    private val activeShellCommands = AtomicInteger(0)
    private val maxActiveShellCommands = AtomicInteger(0)
    private val bridgeResourceLimitRejections = AtomicInteger(0)
    private val bridgeRateLimitedRejections = AtomicInteger(0)
    private val accessibilityRateLimiter = NodeBridgeRateLimiter()
    private val accessibilityDiagnostics by lazy { NodeBridgeAccessibilityDiagnostics(appContext, engineInfo.cwd) }
    private val filesBridge by lazy {
        // Model-facing sessions root `files` at the session's working directory (the host working directory in production).
        val policy = modelPathPolicy
        if (policy == null) NodeBridgeFilesBridge(appContext)
        else NodeBridgeFilesBridge(appContext, rootProvider = { policy.workingRoot }, modelPathPolicy = policy)
    }
    private val consoleBridge by lazy {
        NodeBridgeConsoleBridge(controls = if (modelFacingPluginSession) null else RuntimeConsoleBridge(
            consoleProvider = { executionConsole ?: org.autojs.autojs.AutoJs.instance.globalConsole },
            workingDirectory = engineInfo.cwd,
        ))
    }
    private val coordinateBridge = RuntimeCoordinateBridge()
    private val hostEnginesBridge by lazy { NodeBridgeHostEnginesBridge() }
    private val samplesBridge by lazy { NodeBridgeSamplesBridge(appContext) }
    // Self-described plugin tools (MCP roadmap P12 D29); the screen is captured like accessibility.captureScreenImage.
    private val pluginToolsBridge = lazy {
        // Script sessions get the same scope without shared storage: inside the working directory only.
        val policy = modelPathPolicy ?: NodeBridgeModelPathPolicy(File(engineInfo.cwd.ifBlank { appContext.filesDir.absolutePath }), null) { false }
        NodeBridgePluginToolsBridge(
            context = appContext,
            paths = PluginToolPaths.of(policy),
            consumer = NodeBridgePluginToolsBridge.consumerOf(engineInfo.engineName),
            captureScreen = { timeoutMs -> NodeBridgeScreenCapture.capture(timeoutMs) { screenCapture.capture() }.bitmap },
        )
    }
    private val capabilityBrokerAudit = NodeCapabilityBrokerAuditLog(engineInfo.id)

    /** Host-only configuration; bridge requests cannot widen their own registered-script roots. */
    internal fun prepareAgentScriptRoots(roots: List<File>): () -> Unit {
        val bridge = synchronized(agentHelpersLock) {
            check(!agentHelpersClosed) { "Agent provider scope is closed." }
            agentBridge.value
        }
        val prepared = bridge.prepareRoots(roots)
        return {
            synchronized(agentHelpersLock) {
                check(!agentHelpersClosed) { "Agent provider scope is closed." }
                prepared()
            }
        }
    }

    /** Lazy creation and teardown share one lock; callbacks and worker shutdown run outside it. */
    private fun closeAgentHelpers() {
        val helpers = synchronized(agentHelpersLock) {
            if (agentHelpersClosed) return
            agentHelpersClosed = true
            Pair(if (screenText.isInitialized()) screenText.value else null,
                if (agentBridge.isInitialized()) agentBridge.value else null)
        }
        runCatching { helpers.first?.close() }
        runCatching { helpers.second?.close() }
    }

    private fun dispatchAgent(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val bridge = synchronized(agentHelpersLock) { if (agentHelpersClosed) null else agentBridge.value }
        if (bridge == null) callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("Agent provider scope is closed.")))
        else bridge.dispatch(request, callback)
    }

    private fun dispatchScreenText(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val bridge = synchronized(agentHelpersLock) { if (agentHelpersClosed) null else screenText.value }
        if (bridge == null) callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("Agent provider scope is closed.")))
        else bridge.dispatch(request, callback)
    }

    override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val brokerAttempt = capabilityBrokerAudit.begin(request)
        val auditedCallback: (NodeBridgeResponse) -> Unit = { response ->
            if (!response.event) capabilityBrokerAudit.complete(brokerAttempt, response)
            callback(response)
        }
        brokerAttempt.preflightError?.let { error ->
            auditedCallback(NodeBridgeResponse.failure(request.id, error))
            return
        }
        val auditFailure = capabilityAuditFailure(request)
        if (auditFailure != null) {
            auditedCallback(NodeBridgeResponse.failure(request.id, auditFailure))
            return
        }
        // Agent observations and actions must not treat the lock screen as the target app.
        // These identities come from the host-owned broker, never from request parameters.
        val agentScreenCall = engineInfo.backend == "plugin" && engineInfo.engineName == ThreeStoveAgentIds.ENGINE && when (request.module) {
            NodeBridgeModules.ACCESSIBILITY -> request.method !in setOf("isEnabled", "ensureEnabled")
            NodeBridgeModules.APP -> request.method in setOf("launchApp", "launchPackage")
            NodeBridgeModules.KEYS -> request.method in setOf("notifications", "quickSettings")
            else -> false
        }
        if (agentScreenCall && (!appContext.getSystemService(android.os.PowerManager::class.java).isInteractive ||
                appContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)) {
            auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.permissionDenied(
                "SCREEN_LOCKED: Unlock the device before continuing screen operations.", request.module, request.method,
                category = NodeBridgeErrorCategory.CAPABILITY_DENIED)))
            return
        }
        when {
            request.module == NodeBridgeModules.TOAST && request.method in NodeBridgeModules.toastMethods -> dispatchToast(request, auditedCallback)
            request.module == NodeBridgeModules.APP && request.method in NodeBridgeModules.appMethods -> dispatchApp(request, auditedCallback)
            request.module == NodeBridgeModules.ENGINES && request.method in NodeBridgeModules.enginesMethods -> dispatchEngines(request, auditedCallback)
            request.module in setOf(NodeBridgeModules.ANDROID_OBJECTS,NodeBridgeModules.TASKS) -> dispatchOnMain(request,auditedCallback) {
                try {
                    val result=if(request.module==NodeBridgeModules.ANDROID_OBJECTS)androidObjects.invoke(request.method,request.argsArray())else calendarTasks.invoke(request.method,request.argsArray())
                    NodeBridgeResponse.success(request.id,NodeBridgeJson.valueToJson(result))
                } catch(error:Exception) { NodeBridgeResponse.failure(request.id,when(error){
                    is SecurityException -> NodeBridgeError.permissionDenied(error.message?:"Android permission denied.",request.module,request.method)
                    is IllegalArgumentException, is org.json.JSONException -> NodeBridgeError.invalidRequest(error.message?:"Invalid Android or task argument.",request.module,request.method)
                    else -> NodeBridgeError.providerFailed(error.message?:"Android operation failed.",request.module,request.method)
                }) }
            }
            request.module == NodeBridgeModules.DIALOGS && request.method in NodeBridgeModules.dialogsMethods -> dispatchDialogs(request, auditedCallback)
            request.module == NodeBridgeModules.ACCESSIBILITY && request.method in NodeBridgeModules.accessibilityMethods -> dispatchAccessibility(request, auditedCallback)
            request.module == NodeBridgeModules.SCREEN_RECORDER && request.method in NodeBridgeModules.screenRecorderMethods -> dispatchScreenRecording(request, auditedCallback)
            request.module == NodeBridgeModules.MEDIA_PROJECTION && request.method in NodeBridgeModules.mediaProjectionMethods -> dispatchMediaProjection(request, auditedCallback)
            request.module in NodeBridgeModules.imageModules && request.method in NodeBridgeModules.imageMethods -> dispatchImage(request, auditedCallback)
            request.module == NodeBridgeModules.OCR && request.method in NodeBridgeModules.ocrMethods -> dispatchOcr(request, auditedCallback)
            request.module == NodeBridgeModules.BARCODE && request.method in NodeBridgeModules.barcodeMethods -> dispatchBarcode(request, auditedCallback)
            request.module == NodeBridgeModules.MEDIA && request.method in NodeBridgeModules.mediaPlaybackMethods -> dispatchMediaPlayback(request, auditedCallback)
            request.module == NodeBridgeModules.MEDIA && request.method in NodeBridgeModules.mediaAudioStreamMethods -> dispatchMedia(request, auditedCallback)
            request.module == NodeBridgeModules.MEDIA_STORE && request.method in NodeBridgeModules.mediaStoreMethods -> dispatchMediaStore(request, auditedCallback)
            request.module == NodeBridgeModules.MEDIAINFO && request.method in NodeBridgeModules.mediainfoMethods -> dispatchMediainfo(request, auditedCallback)
            request.module == NodeBridgeModules.RECORDER && request.method in NodeBridgeModules.recorderMethods -> dispatchRecorder(request, auditedCallback)
            request.module == NodeBridgeModules.PINYIN && request.method in NodeBridgeModules.pinyinMethods -> auditedCallback(NodePinyinBridge.dispatch(request, pinyinBackend))
            request.module == NodeBridgeModules.PINYIN4J && request.method in NodeBridgeModules.pinyin4jMethods -> auditedCallback(NodePinyin4jBridge.dispatch(request, pinyin4jBackend))
            request.module == NodeBridgeModules.OPENCC && request.method in NodeBridgeModules.openccMethods -> auditedCallback(NodeOpenccBridge.dispatch(request, openccBackend))
            request.module in NodeBridgeModules.storageModules && request.method in NodeBridgeModules.storageMethods -> dispatchStorage(request, auditedCallback)
            request.module in NodeBridgeModules.databaseModules && request.method in NodeBridgeModules.databaseMethods -> dispatchDatabase(request, auditedCallback)
            request.module == NodeBridgeModules.NOTIFICATIONS && request.method in NodeBridgeModules.notificationsMethods -> dispatchNotifications(request, auditedCallback)
            request.module == NodeBridgeModules.SENSORS && request.method in NodeBridgeModules.sensorsMethods -> dispatchSensors(request, auditedCallback)
            request.module == NodeBridgeModules.COMPOSE -> composeBridge.value.dispatch(request, auditedCallback)
            request.module == NodeBridgeModules.UI && request.method in NodeBridgeModules.uiMethods -> dispatchUi(request, auditedCallback)
            request.module == NodeBridgeModules.UI_OVERLAY && request.method in NodeBridgeModules.uiOverlayMethods -> dispatchUiOverlay(request, auditedCallback)
            request.module == NodeBridgeModules.CLIPBOARD && request.method in NodeBridgeModules.clipboardMethods -> dispatchClipboard(request, auditedCallback)
            request.module == NodeBridgeModules.DEVICE && request.method in NodeBridgeModules.deviceMethods -> dispatchDevice(request, auditedCallback)
            request.module == NodeBridgeModules.SCREEN_METRICS && request.method in NodeBridgeModules.screenMetricsMethods -> dispatchOnMain(request, auditedCallback) {
                try {
                    NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(coordinateBridge.invoke(request.method, request.argsArray())))
                } catch (error: IllegalArgumentException) {
                    NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest(error.message ?: "Invalid coordinate arguments", request.module, request.method))
                }
            }
            request.module == NodeBridgeModules.EVENTS && request.method in NodeBridgeModules.eventsMethods -> dispatchEvents(request, auditedCallback)
            request.module == NodeBridgeModules.KEYS && request.method in NodeBridgeModules.keysMethods -> dispatchKeys(request, auditedCallback)
            request.module == NodeBridgeModules.SHELL && request.method in NodeBridgeModules.shellMethods -> dispatchShell(request, auditedCallback)
            request.module == NodeBridgeModules.FETCH && request.method in NodeBridgeModules.fetchMethods -> dispatchFetch(request, auditedCallback)
            request.module == NodeBridgeModules.WEBSOCKET && request.method in NodeBridgeModules.websocketMethods -> dispatchWebSocket(request, auditedCallback)
            request.module == NodeBridgeModules.WORK_MANAGER && request.method in NodeBridgeModules.workManagerMethods -> dispatchWorkManager(request, auditedCallback)
            request.module == NodeBridgeModules.PACKAGE_MANAGER && request.method in NodeBridgeModules.packageManagerMethods -> dispatchPackageManager(request, auditedCallback)
            request.module == NodeLifecycleCheckpointStore.BRIDGE_MODULE_NAME -> dispatchLifecycle(request, auditedCallback)
            request.module == NodeBridgeModules.MAIL && request.method in RuntimeMailBridge.methods -> {
                val bridge = synchronized(mailScopeLock) { if (mailScopeClosed) null else mailBridge.value }
                if (bridge == null) auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("MAIL execution has ended")))
                else bridge.dispatch(request, auditedCallback)
            }
            request.module == NodeBridgeModules.FLOW && request.method in RuntimeFlowBridge.methods -> {
                val bridge = synchronized(flowScopeLock) { if (flowScopeClosed) null else flowBridge.value }
                if (bridge == null) auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("Flow execution has ended")))
                else bridge.dispatch(request, auditedCallback)
            }
            request.module == NodeBridgeModules.AI_AGENT && request.method in RuntimeAgentBridge.methods -> {
                val bridge = synchronized(aiAgentScopeLock) { if (aiAgentScopeClosed) null else aiAgentBridge.value }
                if (bridge == null) auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("Agent execution has ended")))
                else bridge.dispatch(request, auditedCallback)
            }
            request.module == NodeBridgeModules.AI && request.method in RuntimeAiBridge.methods -> {
                val bridge = synchronized(aiScopeLock) { if (aiScopeClosed) null else aiBridge.value }
                if (bridge == null) auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("AI execution has ended")))
                else bridge.dispatch(request, auditedCallback)
            }
            request.module == NodeBridgeModules.EPUB && request.method in RuntimeEpubBridge.methods -> {
                val bridge = synchronized(epubScopeLock) { if (epubScopeClosed) null else epubBridge.value }
                if (bridge == null) auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("EPUB execution has ended")))
                else bridge.dispatch(request, auditedCallback)
            }
            request.module == NodeBridgeModules.SPEECH && request.method in RuntimeSpeechBridge.methods -> {
                val bridge = synchronized(speechScopeLock) { if (speechScopeClosed) null else speechBridge.value }
                if (bridge == null) auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("Speech execution has ended")))
                else bridge.dispatch(request, auditedCallback)
            }
            request.module == NodeBridgeModules.TTS && request.method in RuntimeTtsBridge.methods -> {
                val bridge = synchronized(ttsScopeLock) { if (ttsScopeClosed) null else ttsBridge.value }
                if (bridge == null) auditedCallback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("TTS execution has ended")))
                else bridge.dispatch(request, auditedCallback)
            }
            request.module == NodeBridgeModules.JAVA && request.method in NodeBridgeModules.javaMethods -> dispatchJava(request, auditedCallback)
            request.module == NodeBridgeModules.RHINO && request.method in NodeBridgeModules.rhinoMethods -> dispatchRhino(request, auditedCallback)
            request.module == NodeBridgeModules.INPUT_OBSERVER && request.method in NodeBridgeModules.inputObserverMethods -> dispatchInputObserver(request, auditedCallback)
            request.module == NodeBridgeModules.FILES && request.method in NodeBridgeModules.filesMethods -> auditedCallback(filesBridge.dispatch(request))
            request.module == NodeBridgeModules.AGENT && request.method in NodeBridgeModules.agentMethods -> dispatchAgent(request, auditedCallback)
            request.module == NodeBridgeModules.CONSOLE && request.method in NodeBridgeModules.consoleMethods -> auditedCallback(consoleBridge.dispatch(request))
            request.module == NodeBridgeModules.PLUGIN_TOOLS && request.method in NodeBridgeModules.pluginToolsMethods -> pluginToolsBridge.value.dispatch(request, auditedCallback)
            else -> auditedCallback(unavailable(request))
        }
    }

    override fun nativeDiagnostics(): Map<String, String> = linkedMapOf<String, String>().apply {
        putAll(networkDiagnostics.nativeValues())
        putAll(websocketDiagnostics.nativeValues())
        putAll(ocrDiagnostics.nativeValues())
        putAll(barcodeDiagnostics.nativeValues())
        put("embedded_script.image_active_handle_count", imageHandleCount().toString())
        put("embedded_script.ocr_active_operation_count", ocrOperations.size.toString())
        put("embedded_script.barcode_active_operation_count", barcodeOperations.size.toString())
        put("embedded_script.database_active_handle_count", databaseHandles.size.toString())
        put("embedded_script.work_manager_registered_task_count", workManagerTasks.size.toString())
        put("embedded_script.overlay_active_session_count", overlaySessionIds.size.toString())
        put("embedded_script.overlay_permission_granted", overlayPermissionGranted().toString())
        put("embedded_script.overlay_permission_settings_launch_count", overlayPermissionSettingsLaunches.get().toString())
        put("embedded_script.overlay_live_provider_state", "android_window_manager")
        put("embedded_script.overlay_pending_event_count", overlaySessions.values.sumOf { it.pendingEventCount() }.toString())
        put("embedded_script.overlay_dropped_event_count", overlaySessions.values.sumOf { it.droppedEventCount() }.toString())
        put("embedded_script.input_observer_active_subscription_count", inputObserverSubscriptions.size.toString())
        put("embedded_script.input_observer_pending_event_count", inputObserverSubscriptions.values.sumOf { it.pendingEventCount() }.toString())
        put("embedded_script.input_observer_dropped_event_count", inputObserverSubscriptions.values.sumOf { it.droppedEventCount() }.toString())
        put("embedded_script.input_observer_live_listener_count", inputObserverSubscriptions.values.sumOf { it.liveListenerCount() }.toString())
        putAll(NodeLifecycleCheckpointStore.nativeDiagnostics(appContext, engineInfo))
        put("embedded_script.java_interop_handle_count", javaHandles.size.toString())
        if(javaSessionDelegate.isInitialized())putAll(javaSession.diagnostics())
        put("embedded_script.package_manager_record_count", runCatching { packageInstallManager.list().size }.getOrDefault(0).toString())
        putAll(packageInstallManager.nativeValues())
        putAll(capabilityBrokerAudit.nativeDiagnostics())
        putAll(resourceRegistry.nativeDiagnostics())
        put("embedded_script.android.active_object_count",androidObjects.activeCount().toString())
        put("embedded_script.device.active_wake_lock_count", deviceControls.activeCount().toString())
        put("embedded_script.events.active_handle_count", eventObservers.activeCount().toString())
        put("embedded_script.shell.active_handle_count",if(shellBridge.isInitialized())shellBridge.value.activeCount.toString()else "0")
        putAll(bridgeLimits.nativeDiagnostics())
        put("embedded_script.bridge_resource_limit_rejection_count", bridgeResourceLimitRejections.get().toString())
        put("embedded_script.bridge_rate_limited_count", bridgeRateLimitedRejections.get().toString())
        put("embedded_script.shell_active_command_count", activeShellCommands.get().toString())
        put("embedded_script.shell_max_active_command_count", maxActiveShellCommands.get().toString())
        put("embedded_script.bridge_provider_health_json", NodeBridgeProviderRegistry.toJsonArray(healthCheck()).toString())
    }

    override fun healthCheck(): List<NodeBridgeProviderHealth> {
        return NodeBridgeProviderRegistry.androidHealth(
            appContext,
            activeResourceCounts = mapOf(
                "fetch" to networkCalls.size,
                "websocket" to websocketSessions.size,
                "ocr" to ocrOperations.size,
                "barcode" to barcodeOperations.size,
                "image" to imageHandleCount(),
                "database" to databaseHandles.size,
                "notifications" to notificationHandles.size,
                "events" to eventObservers.activeCount(),
                "device" to deviceControls.activeCount(),
                "sensors" to sensorSubscriptions.size,
                "ui" to uiSessionIds.size + (if (composeBridge.isInitialized()) composeBridge.value.activeCount else 0) +
                    (if (uiInterop.isInitialized()) uiInterop.value.activeCount else 0),
                NodeBridgeModules.UI_OVERLAY to overlaySessionIds.size,
                "input_observer" to inputObserverSubscriptions.size,
                "work_manager" to workManagerTasks.size,
                "package_manager" to runCatching { packageInstallManager.list().size }.getOrDefault(0),
                "shell" to activeShellCommands.get(),
            ),
        )
    }

    override fun destroy(reason: NodeBridgeError) {
        closeShellProcesses()
        closeEventResources()
        closeAgentHelpers()
        synchronized(mailScopeLock) { mailScopeClosed = true; if (mailBridge.isInitialized()) mailBridge.value.close() }
        synchronized(flowScopeLock) { flowScopeClosed = true; if (flowBridge.isInitialized()) flowBridge.value.close() }
        synchronized(aiScopeLock) { aiScopeClosed = true; if (aiBridge.isInitialized()) aiBridge.value.close() }
        synchronized(aiAgentScopeLock) { aiAgentScopeClosed = true; if (aiAgentBridge.isInitialized()) aiAgentBridge.value.close() }
        synchronized(epubScopeLock) { epubScopeClosed = true; if (epubBridge.isInitialized()) epubBridge.value.close() }
        synchronized(speechScopeLock) { speechScopeClosed = true; if (speechBridge.isInitialized()) speechBridge.value.close() }
        synchronized(ttsScopeLock) { ttsScopeClosed = true; if (ttsBridge.isInitialized()) ttsBridge.value.close() }
        if (composeBridge.isInitialized()) composeBridge.value.close()
        if (uiInterop.isInitialized()) uiInterop.value.close()
        // UI dispatch receivers stay JNI global roots until system_server collects their proxies; drop their callbacks (C33).
        // zh-CN: UI 分发接收器在 system_server 回收代理之前一直是 JNI 全局根, 在此丢弃它们持有的回调 (C33).
        // A receiver also removes itself on the main thread, so walk the concurrent set instead of copying it (C35).
        // zh-CN: 接收器也会在主线程上移除自己, 因此直接遍历该并发集合而不是复制它 (C35).
        uiDispatchReceivers.forEach { it.close() }
        if (pluginToolsBridge.isInitialized()) pluginToolsBridge.value.destroy()
        accessibilityDiagnostics.clearNodeRefs()
        imageDestroyed.set(true)
        if (advancedImages.isInitialized()) advancedImages.value.cancelPending()
        imageExecutor.shutdownNow()
        screenCapture.close()
        deviceControls.close()
        eventObservers.closeAll()
        dialogs.closeAll()
        androidObjects.close()
        if (audioRecorder.isInitialized()) audioRecorder.value.close()
        if (mediaPlayback.isInitialized()) mediaPlayback.value.close()
        if (mediaStore.isInitialized()) mediaStore.value.close()
        val destroyReason = reason.code.ifBlank { reason.message }
        networkDiagnostics.recordServiceDestroyCancellation(networkCalls.size)
        websocketDiagnostics.recordServiceDestroyClose(websocketSessions.size)
        ocrDiagnostics.recordServiceDestroyCancellation(ocrOperations.size)
        barcodeDiagnostics.recordServiceDestroyCancellation(barcodeOperations.size)
        // The timeout runnables capture this provider and the callback; up to timeoutMs on the main queue otherwise (C5).
        // zh-CN: 超时 Runnable 捕获本 provider 与回调, 不移除的话会在主线程队列里留到 timeoutMs (C5).
        ocrOperations.values.forEach { operation ->
            operation.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            operation.cancel("destroy:$destroyReason")
        }
        ocrOperations.clear()
        barcodeOperations.values.forEach { operation ->
            operation.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            operation.cancel("destroy:$destroyReason")
        }
        barcodeOperations.clear()
        databaseDestroyed.set(true)
        // Copied through toArray(), which tolerates sessions removing themselves meanwhile (C35).
        // zh-CN: 经 toArray() 复制, 可容忍会话在此期间移除自己 (C35).
        val closingWebSockets = ArrayList(websocketSessions.values)
        synchronized(sensorLock) {
            sensorsDestroyed = true
            synchronized(imageLock) { resourceRegistry.closeAll(destroyReason) }
        }
        // SQLite sessions and manual transactions belong to the executor thread. Drain their
        // close/rollback jobs before stopping it; interrupting and closing from another thread leaks locks.
        databaseExecutor.shutdown()
        // The registry only enqueued the 1001 close frames; OkHttp writes them from its task runner.
        // Cancelling the dispatcher (which runs the WebSocket reader) and evicting the pool right away
        // would drop the socket first, so peers would see an abrupt disconnect instead of the close.
        val closeDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WEBSOCKET_DESTROY_CLOSE_WAIT_MS)
        closingWebSockets.forEach { session ->
            session.awaitClosed(TimeUnit.NANOSECONDS.toMillis(closeDeadlineNanos - System.nanoTime()).coerceAtLeast(0L))
        }
        networkCalls.values.forEach { call -> runCatching { call.cancel() } }
        networkClient.dispatcher.cancelAll()
        networkClient.dispatcher.executorService.shutdownNow()
        networkClient.connectionPool.evictAll()
        runCatching { networkClient.cache?.close() }
        runCatching { databaseExecutor.awaitTermination(250L, TimeUnit.MILLISECONDS) }
        networkCalls.clear()
        websocketSessions.clear()
        imageHandles.clear()
        databaseHandles.clear()
        workManagerTasks.clear()
        closeJavaObjects()
        notificationHandles.clear()
        sensorSubscriptions.clear()
        uiSessionIds.clear()
        closeOverlaySessions("destroy:$destroyReason")
        overlaySessionIds.clear()
        inputObserverSubscriptions.clear()
    }

    private fun unavailable(request: NodeBridgeRequest): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.bridgeUnavailable(request.module, request.method),
        )
    }

    private fun capabilityAuditFailure(request: NodeBridgeRequest): NodeBridgeError? {
        val requiredCapabilities = NodeBridgePermissionManifest.requiredCapabilitiesFor(request)
        if (requiredCapabilities.isEmpty()) {
            return null
        }
        val missingRequestCapabilities = NodeBridgePermissionManifest.missingCapabilities(
            declaredCapabilities = request.permissions,
            requiredCapabilities = requiredCapabilities,
        )
        if (missingRequestCapabilities.isNotEmpty()) {
            return NodeBridgeError.capabilityNotDeclared(
                request.module,
                request.method,
                missingRequestCapabilities,
            )
        }
        val missingManifestCapabilities = NodeBridgePermissionManifest.missingCapabilities(
            declaredCapabilities = bridgePermissionManifest.permissions,
            requiredCapabilities = requiredCapabilities,
        )
        if (bridgePermissionManifest.enforced && missingManifestCapabilities.isNotEmpty()) {
            return NodeBridgeError.capabilityNotDeclared(
                request.module,
                request.method,
                missingManifestCapabilities,
            )
        }
        return null
    }

    private fun dispatchEngines(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (request.method == "stop" && runCatching { JSONArray(request.argsJson).opt(0) is JSONObject }.getOrDefault(false)) {
            dispatchAgent(request, callback)
            return
        }
        if (request.method == "execScript" && runCatching {
                JSONArray(request.argsJson).optJSONObject(2)?.has("agentInvocationId") == true
            }.getOrDefault(false)) {
            dispatchAgent(request, callback)
            return
        }
        if (request.method == "execScript" || request.method == "execScriptFile") {
            NodeBridgeEngineDispatchService.dispatch(
                appContext,
                request.withEngineExecutionContext(),
                callback,
            )
            return
        }
        val response = when (request.method) {
            "myEngine" -> NodeBridgeResponse.success(request.id, engineInfo.toJson())
            "all" -> NodeBridgeResponse.success(
                request.id,
                JSONArray().put(engineInfo.toJsonObject()).toString(),
            )
            // MCP roadmap P1.3: host-process semantics only with {scope: "host"}; the plugin view stays.
            "stopAll" -> hostEnginesBridge.stopAll(request) ?: NodeBridgeResponse.success(request.id, "0")
            "list" -> hostEnginesBridge.list(request)
            "stop" -> hostEnginesBridge.stop(request)
            "stopSelf" -> NodeBridgeResponse.success(request.id, "true")
            else -> NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.permissionDenied(
                    message = "AutoJs6 engines bridge method is not available: ${request.method}",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        callback(response)
    }

    private fun NodeBridgeRequest.withEngineExecutionContext(): NodeBridgeRequest {
        val args = runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())
        val optionsIndex = when (method) {
            "execScript" -> 2
            "execScriptFile" -> 1
            else -> return this
        }
        val options = args.optJSONObject(optionsIndex) ?: JSONObject()
        options.put(NodeBridgeEngineDispatchService.INTERNAL_BASE_CWD, engineInfo.cwd)
        options.put(NodeBridgeEngineDispatchService.INTERNAL_PARENT_SPAWN_DEPTH, engineInfo.spawnDepth)
        args.put(optionsIndex, options)
        return copy(argsJson = args.toString())
    }

    private fun dispatchDialogs(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dialogs.dispatch(request, callback)
    }

    private fun dispatchUi(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args=JSONArray(request.argsJson)
        if(request.method in RuntimeUiInterop.methods || request.method == "showLayout" && RuntimeUiInterop.isLayout(args.opt(0)) ||
            uiInterop.isInitialized() && uiInterop.value.ownsWindow(args.optString(0))) {
            uiInterop.value.dispatch(request,callback);return
        }
        when (request.method) {
            "showLayout" -> dispatchUiShowLayout(request, callback)
            "close" -> dispatchOwnedUiRequest(request, releaseOnClose = true, callback = callback)
            "update", "batchUpdate", "drainEvents" -> dispatchOwnedUiRequest(request, releaseOnClose = false, callback = callback)
            else -> callback(unavailable(request))
        }
    }

    private fun dispatchUiOverlay(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        when (request.method) {
            "show" -> dispatchOverlayShow(request, callback)
            "update" -> dispatchOwnedOverlayRequest(request, callback) { session, args ->
                val patch = args.optJSONObject(1)
                    ?: return@dispatchOwnedOverlayRequest NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.invalidRequest(
                            message = "ui.overlay.update requires a JSON patch object.",
                            module = request.module,
                            method = request.method,
                        ),
                    )
                try {
                    session.update(patch)
                    NodeBridgeResponse.success(request.id, session.snapshot().toString())
                } catch (error: IllegalArgumentException) {
                    NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest(error.message ?: "Invalid overlay update.", request.module, request.method))
                }
            }
            "drainEvents" -> dispatchOwnedOverlayRequest(request, callback) { session, args ->
                val options = args.optJSONObject(1) ?: JSONObject()
                val maxEvents = options.optInt("maxEvents", 16).coerceIn(1, 64)
                NodeBridgeResponse.success(request.id, session.drainEvents(maxEvents).toString())
            }
            "close" -> dispatchOwnedOverlayRequest(request, callback) { session, args ->
                val reason = args.optJSONObject(1)?.optString("reason").orEmpty().ifBlank { "close" }
                val closed = closeOverlaySession(session.id, reason, releaseResource = true)
                NodeBridgeResponse.success(request.id, closed.toString())
            }
            "closeAll" -> mainHandler.post {
                // P13 historical verifier token: overlayCloseAllSummary("p12_14_live_gate_no_active_provider")
                callback(NodeBridgeResponse.success(request.id, overlayCloseAllSummary("close_all").toString()))
            }
            "hasPermission" -> callback(NodeBridgeResponse.success(request.id, overlayPermissionSnapshot().toString()))
            "openPermissionSettings" -> callback(dispatchOverlayOpenPermissionSettings(request))
            else -> callback(unavailable(request))
        }
    }

    private fun dispatchOverlayShow(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (!overlayPermissionGranted()) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.permissionDenied(
                        message = "AutoJs6 ui.overlay.show requires android.permission.SYSTEM_ALERT_WINDOW to be granted by the user.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        if (overlaySessionIds.size >= MAX_ACTIVE_OVERLAY_SESSIONS) {
            bridgeResourceLimitRejections.incrementAndGet()
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.resourceLimit(
                        message = "AutoJs6 ui.overlay exceeded $MAX_ACTIVE_OVERLAY_SESSIONS active overlay sessions.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        val args = request.args()
        val descriptor = args.optJSONObject(0)
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "ui.overlay.show requires a descriptor object.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        ControlledOverlaySession.validateDescriptor(descriptor)?.let { message ->
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = message,
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        mainHandler.post {
            val id = "overlay-${UUID.randomUUID()}"
            val eventStream = NodeBridgeEventStream.forRequest(request, id, callback)
            val session = ControlledOverlaySession(appContext, id, descriptor, eventStream)
            val response = runCatching {
                session.show()
                val resource = resourceRegistry.register(
                    type = NodeBridgeResourceType.OVERLAY_SESSION,
                    id = id,
                    metadata = mapOf(
                        "module" to request.module,
                        "disclosureTitle" to session.disclosureTitle(),
                    ),
                    resource = session,
                ) { reason ->
                    mainHandler.post {
                        closeOverlaySession(id, reason, releaseResource = false)
                    }
                }
                session.attachResource(resource)
                overlaySessions[id] = session
                overlaySessionIds += id
                NodeBridgeResponse.success(request.id, session.snapshot().toString())
            }.getOrElse { error ->
                session.close("show_failed")
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = request.module,
                        method = request.method,
                        code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                    ),
                )
            }
            if (response.ok && eventStream != null) eventStream.acknowledge(JSONObject(response.resultJson.orEmpty()))
            else callback(response)
        }
    }

    private fun dispatchOwnedOverlayRequest(
        request: NodeBridgeRequest,
        callback: (NodeBridgeResponse) -> Unit,
        handler: (ControlledOverlaySession, JSONArray) -> NodeBridgeResponse,
    ) {
        val args = request.args()
        val sessionId = overlaySessionId(args)
        if (sessionId.isBlank() || sessionId !in overlaySessionIds) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "ui.overlay.${request.method} requires an active overlay handle owned by this execution.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        mainHandler.post {
            val session = overlaySessions[sessionId]
            val response = if (session == null || session.isClosed()) {
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "ui.overlay.${request.method} requires an active overlay handle owned by this execution.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            } else {
                runCatching { handler(session, args) }.getOrElse { error ->
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.providerFailed(
                            message = error.message ?: error.javaClass.name,
                            module = request.module,
                            method = request.method,
                            code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                        ),
                    )
                }
            }
            callback(response)
        }
    }

    private fun dispatchOverlayOpenPermissionSettings(request: NodeBridgeRequest): NodeBridgeResponse {
        val options = request.args().optJSONObject(0) ?: JSONObject()
        val dryRun = options.optBoolean("dryRun", false)
        val intent = overlayPermissionSettingsIntent()
        val canOpenSettings = overlayPermissionSettingsIntentAvailable(intent)
        var opened = false
        var reason = if (canOpenSettings) "settings-intent-ready" else "settings-intent-unavailable"
        if (canOpenSettings && !dryRun) {
            runCatching {
                appContext.startActivity(intent)
                overlayPermissionSettingsLaunches.incrementAndGet()
                opened = true
                reason = "settings-intent-opened"
            }.onFailure { error ->
                reason = "settings-intent-failed:${error.javaClass.simpleName}"
            }
        } else if (dryRun) {
            reason = "settings-intent-dry-run"
        }
        return NodeBridgeResponse.success(
            request.id,
            JSONObject()
                .put("opened", opened)
                .put("canOpenSettings", canOpenSettings)
                .put("dryRun", dryRun)
                .put("reason", reason)
                .put("providerState", "android_window_manager")
                .toString(),
        )
    }

    private fun overlayPermissionSnapshot(): JSONObject {
        val granted = overlayPermissionGranted()
        return JSONObject()
            .put("granted", granted)
            .put("canOpenSettings", overlayPermissionSettingsIntentAvailable())
            .put("reason", if (granted) "permission-granted" else "permission-required")
            .put("providerState", "android_window_manager")
            .put("packagedManifestPermission", "android.permission.SYSTEM_ALERT_WINDOW")
            .put("foregroundDisclosure", "declared_overlay_disclosure")
            .put("activeOverlayCount", overlaySessionIds.size)
    }

    private fun overlayCloseAllSummary(reason: String): JSONObject {
        val closedCount = closeOverlaySessions(reason)
        return JSONObject()
            .put("closedCount", closedCount)
            .put("remainingCount", overlaySessionIds.size)
            .put("reason", reason)
            .put("providerState", "android_window_manager")
            .put("rawWindowLeaked", false)
    }

    private fun overlaySessionId(args: JSONArray): String {
        return when (val raw = args.opt(0)) {
            is String -> raw
            is JSONObject -> raw.optString("id")
            else -> ""
        }.trim()
    }

    private fun closeOverlaySessions(reason: String): Int {
        // Walk the map itself: an overlay closing on the main thread removes its id meanwhile, and toList() of a map that
        // held one entry then throws NoSuchElementException, which stopped destroy() before the execution was released (C35).
        // zh-CN: 直接遍历该表: 在主线程上关闭的浮窗会同时移除自己的 id, 此时对原有一项的表调用 toList() 会抛出
        // zh-CN: NoSuchElementException, destroy() 因而在本次执行释放之前中止 (C35).
        var closedCount = 0
        overlaySessions.keys.forEach { id ->
            if (closeOverlaySession(id, reason, releaseResource = true)) {
                closedCount += 1
            }
        }
        return closedCount
    }

    private fun closeOverlaySession(id: String, reason: String, releaseResource: Boolean): Boolean {
        val session = overlaySessions.remove(id) ?: return false
        overlaySessionIds.remove(id)
        val closed = session.close(reason)
        if (releaseResource) {
            session.releaseResource(reason)
        }
        return closed
    }

    @Suppress("unused")
    private fun overlayLiveProviderMissing(request: NodeBridgeRequest): NodeBridgeError {
        return NodeBridgeError.capabilityProviderMissing(
            module = request.module,
            method = request.method,
            requirement = "a promoted overlay capability provider; P12-14 exposes only permission/settings/cleanup live-gate evidence and keeps live overlay creation fail-closed",
        )
    }

    private fun overlayPermissionGranted(): Boolean {
        return runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)
    }

    private fun overlayPermissionSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun overlayPermissionSettingsIntentAvailable(intent: Intent = overlayPermissionSettingsIntent()): Boolean {
        return appContext.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    }

    private fun intentAvailable(intent: Intent): Boolean {
        return appContext.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    }

    private fun dispatchUiShowLayout(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (uiSessionIds.size >= MAX_ACTIVE_UI_SESSIONS) {
            callback(NodeBridgeResponse.failure(request.id, uiSessionLimitError(request)))
            return
        }
        NodeBridgeUiDispatchService.dispatch(appContext, request, uiDispatchReceivers) { response ->
            if (response.event) {
                callback(response)
                if (JSONObject(response.resultJson.orEmpty()).optString("type") == "close") {
                    response.subscriptionId?.let { releaseOwnedUiSession(it, "ui.terminal_close") }
                }
                return@dispatch
            }
            if (!response.ok) {
                callback(response)
                return@dispatch
            }
            val sessionId = runCatching {
                JSONObject(response.resultJson.orEmpty()).optString("id")
            }.getOrDefault("")
            if (sessionId.isBlank()) {
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.providerFailed(
                            message = "AutoJs6 UI provider returned an invalid UI handle.",
                            module = request.module,
                            method = request.method,
                            code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                        ),
                    ),
                )
                return@dispatch
            }
            if (uiSessionIds.size >= MAX_ACTIVE_UI_SESSIONS) {
                NodeBridgeUiDispatchService.dispatch(appContext, uiCloseRequest(sessionId, "ui_session_limit")) {}
                callback(NodeBridgeResponse.failure(request.id, uiSessionLimitError(request)))
                return@dispatch
            }
            val handle = runCatching {
                resourceRegistry.register(
                    type = NodeBridgeResourceType.UI_SESSION,
                    id = sessionId,
                    metadata = mapOf("module" to request.module),
                ) { reason ->
                    uiSessionIds.remove(sessionId)
                    // The store lives in this process. Going through startService could fail or run after destroy and
                    // leave the session in the static store (C7).
                    // zh-CN: 会话存储就在本进程. 经 startService 关闭可能失败或晚于 destroy 执行, 会把会话留在静态存储里 (C7).
                    NodeBridgeUiSessionStore.remove(sessionId)?.close(reason)
                }
            }.getOrElse { error ->
                NodeBridgeUiDispatchService.dispatch(appContext, uiCloseRequest(sessionId, "resource_register_failed")) {}
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.providerFailed(
                            message = error.message ?: error.javaClass.name,
                            module = request.module,
                            method = request.method,
                            code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                        ),
                    ),
                )
                return@dispatch
            }
            uiSessionIds += handle.id
            callback(response)
        }
    }

    private fun dispatchOwnedUiRequest(
        request: NodeBridgeRequest,
        releaseOnClose: Boolean,
        callback: (NodeBridgeResponse) -> Unit,
    ) {
        val sessionId = uiSessionId(request.argsJson)
        if (sessionId.isBlank() || sessionId !in uiSessionIds) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 ui.${request.method} requires an active UI handle owned by this execution.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        NodeBridgeUiDispatchService.dispatch(appContext, request, uiDispatchReceivers) { response ->
            val releaseReason = when {
                releaseOnClose -> "ui.close"
                request.method == "drainEvents" && response.hasTerminalUiCloseEvent(sessionId) -> "ui.terminal_close"
                else -> null
            }
            if (releaseReason != null) {
                releaseOwnedUiSession(sessionId, releaseReason)
            }
            callback(response)
        }
    }

    private fun releaseOwnedUiSession(sessionId: String, reason: String) {
        uiSessionIds.remove(sessionId)
        resourceRegistry.release(sessionId, reason)
    }

    private fun NodeBridgeResponse.hasTerminalUiCloseEvent(sessionId: String): Boolean {
        if (!ok) {
            return false
        }
        val events = runCatching { JSONArray(resultJson.orEmpty()) }.getOrNull() ?: return false
        return (0 until events.length()).any { index ->
            val event = events.optJSONObject(index) ?: return@any false
            event.optString("type") == "close" &&
                event.optString("handleId") == sessionId &&
                event.optJSONObject("snapshot")?.optBoolean("closed") == true
        }
    }

    private fun uiSessionId(argsJson: String): String {
        val args = runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())
        return when (val raw = args.opt(0)) {
            is String -> raw
            is JSONObject -> raw.optString("id", raw.optString("handleId", raw.optString("__autojs6UiHandle")))
            else -> ""
        }.trim()
    }

    private fun uiCloseRequest(sessionId: String, reason: String): NodeBridgeRequest {
        return NodeBridgeRequest(
            id = "ui-close-${UUID.randomUUID()}",
            module = NodeBridgeModules.UI,
            method = "close",
            argsJson = JSONArray()
                .put(sessionId)
                .put(JSONObject().put("reason", reason))
                .toString(),
            timeoutMs = 5_000L,
            permissions = listOf(NodeBridgePermissionManifest.UI),
        )
    }

    private fun uiSessionLimitError(request: NodeBridgeRequest): NodeBridgeError {
        return NodeBridgeError.resourceLimit(
            message = "AutoJs6 UI bridge reached the active UI session limit ($MAX_ACTIVE_UI_SESSIONS).",
            module = request.module,
            method = request.method,
        )
    }

    private fun dispatchAccessibility(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (!accessibilityRateLimiter.tryAcquire(bridgeLimits.accessibilityQueriesPerSecond)) {
            bridgeRateLimitedRejections.incrementAndGet()
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.rateLimited(
                        message = "AutoJs6 accessibility bridge exceeded ${bridgeLimits.accessibilityQueriesPerSecond} requests per second.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        if (request.method == "screenshot") {
            dispatchAccessibilityScreenshot(request, callback)
            return
        }
        if (request.method == "captureScreenImage") {
            dispatchCaptureScreenImage(request, callback)
            return
        }
        if (request.method == "readScreenText") {
            dispatchScreenText(request, callback)
            return
        }
        if (request.method == "ensureEnabled") {
            dispatchAccessibilityEnsureEnabled(request, callback)
            return
        }
        val response = when (request.method) {
            "isEnabled" -> NodeBridgeResponse.success(request.id, accessibilityBridgeOperational().toString())
            else -> dispatchAccessibilityOperation(request)
        }
        callback(response)
    }

    private fun dispatchAccessibilityScreenshot(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val service = AutoJsAccessibilityService.instance
        if (service == null || !AutoJsAccessibilityService.registry.isOperational) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.capabilityProviderMissing(
                        module = request.module,
                        method = request.method,
                        requirement = "an enabled accessibility capability provider in the current process",
                    ),
                ),
            )
            return
        }
        accessibilityDiagnostics.screenshot(request, service, callback)
    }

    /**
     * One screen capture registered as a provider-owned image handle (MCP roadmap P11 D23): the
     * accessibility screenshot when it can serve, otherwise the session's MediaProjection capture.
     * Barcode and image-matching consumers get a handle plus `captureSource`, never pixels.
     */
    private fun dispatchCaptureScreenImage(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val timeoutMs = request.timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)
        imageOperation(request, callback) {
            if (imageHandleCount() >= bridgeLimits.maxImageHandles) {
                // Refuse before taking a screenshot nobody could keep; registerImage rechecks under the same lock.
                bridgeResourceLimitRejections.incrementAndGet()
                throw ImageHandleLimitException("Image bridge exceeded ${bridgeLimits.maxImageHandles} active handles.")
            }
            val captured = NodeBridgeScreenCapture.capture(timeoutMs) { screenCapture.capture() }
            registerImage(captured.bitmap, "screen-${captured.source}").toJson()
                .put("captureSource", captured.source)
                .toString()
        }
    }

    private fun accessibilityBridgeOperational(): Boolean {
        return AutoJsAccessibilityService.registry.isOperational
    }

    /**
     * `ensureEnabled` lets the controller enable the service through every available strategy and wait for
     * the operational state, which can take longer than the bridge deadline the plugin enforces
     * ([NodeBridgeRequest.timeoutMs]); a service that is enabled but cannot bind would then surface as
     * ERR_AUTOJS6_BRIDGE_TIMEOUT instead of `false`. The reply is therefore sent before that deadline with the
     * state reached so far, and the controller keeps working in the background.
     * zh-CN: `ensureEnabled` 会让控制器逐个策略启用服务并等待可用状态, 耗时可能超过插件执行的桥超时
     * ([NodeBridgeRequest.timeoutMs]); 已启用但无法绑定的服务就会表现为 ERR_AUTOJS6_BRIDGE_TIMEOUT 而不是 `false`.
     * 因此在该期限之前按当时的状态作答, 控制器继续在后台工作.
     */
    private fun dispatchAccessibilityEnsureEnabled(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val waitMillis = (request.timeoutMs - ENSURE_ENABLED_REPLY_MARGIN_MS).coerceIn(0L, ENSURE_ENABLED_MAX_WAIT_MS)
        val replied = AtomicBoolean(false)
        fun reply(satisfied: Boolean) {
            if (replied.compareAndSet(false, true)) {
                callback(NodeBridgeResponse.success(request.id, satisfied.toString()))
            }
        }
        val deadline = Runnable { reply(accessibilityBridgeOperational()) }
        mainHandler.postDelayed(deadline, waitMillis)
        org.autojs.autojs.core.accessibility.AccessibilityTool(appContext).ensureServiceAsync(
            level = org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State.OPERATIONAL,
            timeoutMillis = waitMillis,
            launchSettingsIfNeeded = false,
        ) { result ->
            mainHandler.removeCallbacks(deadline)
            reply(result.isSatisfied)
        }
    }

    private fun dispatchAccessibilityOperation(request: NodeBridgeRequest): NodeBridgeResponse {
        val service = AutoJsAccessibilityService.instance
        if (service == null || !AutoJsAccessibilityService.registry.isOperational) {
            if (request.method == "flowProbe" || request.method == "flowForeground") {
                return NodeBridgeResponse.success(request.id, JSONObject().put("available", false).toString())
            }
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.capabilityProviderMissing(
                    module = request.module,
                    method = request.method,
                    requirement = "an enabled accessibility capability provider in the current process",
                ),
            )
        }
        accessibilityDiagnostics.nodeRefAction(request)?.let { return it }
        return when (request.method) {
            "back" -> NodeBridgeResponse.success(
                request.id,
                service.performGlobalAction(AndroidAccessibilityService.GLOBAL_ACTION_BACK).toString(),
            )
            "home" -> NodeBridgeResponse.success(
                request.id,
                service.performGlobalAction(AndroidAccessibilityService.GLOBAL_ACTION_HOME).toString(),
            )
            "recentApps" -> NodeBridgeResponse.success(
                request.id,
                service.performGlobalAction(AndroidAccessibilityService.GLOBAL_ACTION_RECENTS).toString(),
            )
            "findByText" -> dispatchAccessibilityFindByText(request)
            "clickText" -> dispatchAccessibilityClickText(request)
            "flowProbe" -> {
                val args = request.argsArray()
                require(args.length() in 1..2) { "flowProbe requires a selector and optional scope" }
                if (args.length() == 2) {
                    val response = JSONObject().put("available", true)
                    try {
                        RuntimeFlowNodes({}, {
                            WindowRootProvider.default.activeRoot(A11yWindowRootPolicy())?.let { UiObject.createRoot(it) }
                        }).use { arena ->
                            response.put("value", RuntimeFlowPick.json(RuntimeFlowPick(arena).probe(args.getJSONObject(0), args.getJSONObject(1))))
                        }
                    } catch (error: org.autojs.autojs.core.automator.flow.FlowError) {
                        response.put("error", RuntimeFlowJson.error(error))
                    }
                    return NodeBridgeResponse.success(request.id, response.toString())
                }
                val filter = RuntimeFlowQuery.filter(args.getJSONObject(0))
                val json = withBridgeRoot { root ->
                    DFS.search(root, filter, limit = 1, lifecycle = NodeLifecycle.default).firstOrNull()
                        ?.useBridgeNode(root) { if (it.refresh() && filter.filter(it)) RuntimeFlowQuery.snapshot(it).toString() else null }
                }
                NodeBridgeResponse.success(request.id, "{\"available\":true,\"value\":${json ?: "null"}}")
            }
            "flowForeground" -> {
                require(request.argsArray().length() == 0) { "flowForeground takes no arguments" }
                val info = RuntimeFlowForeground.read()
                NodeBridgeResponse.success(request.id, JSONObject().put("available", true).put("value",
                    JSONObject().put("packageName", info.packageName).put("activityName", info.activityName)).toString())
            }
            "findOne" -> dispatchAccessibilityFindOne(request)
            "findAll" -> dispatchAccessibilityFindAll(request)
            "click" -> dispatchAccessibilityClick(request)
            "longClick" -> dispatchAccessibilitySelectorAction(request, BridgeNodeActions.ACTION_LONG_CLICK)
            "setText" -> dispatchAccessibilitySetText(request)
            "scrollForward" -> dispatchAccessibilitySelectorAction(request, BridgeNodeActions.ACTION_SCROLL_FORWARD)
            "scrollBackward" -> dispatchAccessibilitySelectorAction(request, BridgeNodeActions.ACTION_SCROLL_BACKWARD)
            "swipe" -> dispatchAccessibilitySwipe(request, service)
            "gesture" -> dispatchAccessibilityGesture(request, service)
            "dump" -> accessibilityDiagnostics.dump(request)
            "inspectNode" -> accessibilityDiagnostics.inspectNode(request)
            "explain" -> accessibilityDiagnostics.explain(request)
            else -> unavailable(request)
        }
    }

    /**
     * M3.2: coordinate swipe through AccessibilityService.dispatchGesture.
     * args: [x1, y1, x2, y2, durationMs?]. Returns "true" once the system
     * reports the gesture completed, "false" when it was cancelled.
     * Identical end points are dispatched as a press of durationMs (the
     * automator's single-point stroke); the MCP plugin (roadmap P3.2) sends
     * its coordinate taps and long presses this way.
     */
    private fun dispatchAccessibilitySwipe(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
    ): NodeBridgeResponse {
        val args = request.args()
        val x1 = args.optInt(0, -1)
        val y1 = args.optInt(1, -1)
        val x2 = args.optInt(2, -1)
        val y2 = args.optInt(3, -1)
        val durationMs = args.optLong(4, 300L)
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0 || durationMs <= 0L) {
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest(
                    message = "accessibility.swipe requires non-negative x1, y1, x2, y2 and a positive durationMs.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        val automator = accessibilityGestureAutomator(service)
        val duration = durationMs.coerceAtMost(MAX_ACCESSIBILITY_GESTURE_DURATION_MS)
        val completed = if (x1 == x2 && y1 == y2) automator.press(x1, y1, duration.toInt()) else automator.swipe(x1, y1, x2, y2, duration)
        return NodeBridgeResponse.success(request.id, completed.toString())
    }

    /**
     * M3.2: free-path gesture. args: [durationMs, [[x, y], [x, y], ...]] —
     * the same shape AutoJs6's Rhino gesture() takes after its duration
     * argument. At least two points are required.
     */
    private fun dispatchAccessibilityGesture(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
    ): NodeBridgeResponse {
        val args = request.args()
        val durationMs = args.optLong(0, -1L)
        val pointsJson = args.optJSONArray(1)
        val points = ArrayList<IntArray>()
        if (pointsJson != null) {
            for (index in 0 until pointsJson.length()) {
                val point = pointsJson.optJSONArray(index) ?: continue
                val x = point.optInt(0, -1)
                val y = point.optInt(1, -1)
                if (x < 0 || y < 0) {
                    points.clear()
                    break
                }
                points.add(intArrayOf(x, y))
            }
        }
        if (durationMs <= 0L || points.size < 2) {
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest(
                    message = "accessibility.gesture requires a positive durationMs and at least two [x, y] points with non-negative coordinates.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        val completed = accessibilityGestureAutomator(service)
            .gesture(0L, durationMs.coerceAtMost(MAX_ACCESSIBILITY_GESTURE_DURATION_MS), *points.toTypedArray())
        return NodeBridgeResponse.success(request.id, completed.toString())
    }

    private fun accessibilityGestureAutomator(
        service: AutoJsAccessibilityService,
    ): GlobalActionAutomator {
        // The main-looper handler, as PythonHostCapabilityBroker does: the dispatch
        // thread (never the main thread) blocks until the system reports the
        // gesture result on the main Looper.
        return GlobalActionAutomator(appContext, mainHandler) { service }
    }

    // Roadmap P5.4: the selector descriptor, the node descriptor and the node actions of the frozen
    // accessibility protocol are the Android-free core/automator/bridge classes over UiObject; the
    // search is the runtime's DFS (pre-order, the order of the former recursion), the root comes
    // from the window root provider (the active window root, then the service's cached fast root,
    // as before) and the nodes are released through the node lifecycle once encoded or acted on.
    // Roots are never released (the runtime's rule: the fast root is the service's cache).
    // zh-CN: Roadmap P5.4: 冻结的 accessibility 协议的选择器描述符, 节点描述符与节点动作是 UiObject 之上的 Android-free
    // core/automator/bridge 类; 搜索用运行时的 DFS (先序, 即原先递归的顺序), 根节点来自窗口根节点 provider (活动窗口根节点, 取不到时
    // 为服务缓存的 fast 根节点, 与原先相同), 节点在编码或执行动作后经节点生命周期释放. 根节点从不释放 (运行时的规则: fast 根节点是
    // 服务的缓存).
    private val bridgeNodeActions = BridgeNodeActions(NodeLifecycle.default)

    private fun dispatchAccessibilityFindByText(request: NodeBridgeRequest): NodeBridgeResponse {
        val text = request.args().optString(0)
        val json = withBridgeRoot { root ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { UiObject(it.unwrap()) }?.useBridgeNode(root) { node ->
                node.toBridgeJson().toString()
            }
        }
        return NodeBridgeResponse.success(request.id, json ?: "null")
    }

    private fun dispatchAccessibilityClickText(request: NodeBridgeRequest): NodeBridgeResponse {
        val text = request.args().optString(0)
        val clicked = withBridgeRoot { root ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { UiObject(it.unwrap()) }?.useBridgeNode(root) { node ->
                bridgeNodeActions.click(node)
            }
        }
        return NodeBridgeResponse.success(request.id, clicked.toString())
    }

    private fun dispatchAccessibilityFindOne(request: NodeBridgeRequest): NodeBridgeResponse {
        val selector = request.args().optJSONObject(0) ?: return NodeBridgeResponse.success(request.id, "null")
        val json = withBridgeRoot { root ->
            findBridgeNode(root, selector)?.useBridgeNode(root) { node -> node.toBridgeJson().toString() }
        }
        return NodeBridgeResponse.success(request.id, json ?: "null")
    }

    private fun dispatchAccessibilityFindAll(request: NodeBridgeRequest): NodeBridgeResponse {
        val selector = request.args().optJSONObject(0) ?: JSONObject()
        val nodes = JSONArray()
        withBridgeRoot { root ->
            DFS.search(root, selector.toBridgeSelector().toFilter(), lifecycle = NodeLifecycle.default).forEach { node ->
                node.useBridgeNode(root) { nodes.put(it.toBridgeJson()) }
            }
        }
        return NodeBridgeResponse.success(request.id, nodes.toString())
    }

    private fun dispatchAccessibilityClick(request: NodeBridgeRequest): NodeBridgeResponse {
        val selector = request.args().optJSONObject(0) ?: return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = "AutoJs6 accessibility bridge supports real-device click only through selector descriptors.",
                module = request.module,
                method = request.method,
            ),
        )
        val clicked = withBridgeRoot { root ->
            findBridgeNode(root, selector)?.useBridgeNode(root) { node -> bridgeNodeActions.click(node) }
        }
        return NodeBridgeResponse.success(request.id, clicked.toString())
    }

    private fun dispatchAccessibilitySelectorAction(request: NodeBridgeRequest, action: Int): NodeBridgeResponse {
        val selector = request.args().optJSONObject(0) ?: return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = "AutoJs6 accessibility bridge supports ${request.method} only through selector descriptors.",
                module = request.module,
                method = request.method,
            ),
        )
        val performed = withBridgeRoot { root ->
            findBridgeNode(root, selector)?.useBridgeNode(root) { node -> bridgeNodeActions.perform(node, action) }
        }
        return NodeBridgeResponse.success(request.id, performed.toString())
    }

    private fun dispatchAccessibilitySetText(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.args()
        val selector = args.optJSONObject(0) ?: return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = "AutoJs6 accessibility bridge supports setText only through selector descriptors.",
                module = request.module,
                method = request.method,
            ),
        )
        val text = args.optString(1)
        val set = withBridgeRoot { root ->
            findBridgeNode(root, selector)?.useBridgeNode(root) { node -> bridgeNodeActions.setText(node, text) }
        }
        return NodeBridgeResponse.success(request.id, set.toString())
    }

    private fun NodeBridgeRequest.args(): JSONArray {
        return runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())
    }

    /**
     * [block] over the root of the active window (the cached fast root when the system provides
     * none), `null` without one. zh-CN: 以活动窗口的根节点 (系统不提供时为缓存的 fast 根节点) 运行 [block], 没有根节点时为 `null`.
     */
    private fun <T> withBridgeRoot(block: (UiObject) -> T?): T? {
        val provider = WindowRootProvider.default
        val info = provider.activeRoot(BRIDGE_ROOT_NORMAL) ?: provider.activeRoot(BRIDGE_ROOT_FAST) ?: return null
        return block(UiObject.createRoot(info))
    }

    private fun findBridgeNode(root: UiObject, selector: JSONObject): UiObject? {
        return DFS.search(root, selector.toBridgeSelector().toFilter(), limit = 1, lifecycle = NodeLifecycle.default).firstOrNull()
    }

    // The node is released after [block] unless it is the root itself.
    // zh-CN: [block] 之后释放节点, 除非它就是根节点.
    private inline fun <T> UiObject.useBridgeNode(root: UiObject, block: (UiObject) -> T): T {
        try {
            return block(this)
        } finally {
            if (this !== root) NodeLifecycle.default.release(this)
        }
    }

    private fun JSONObject.toBridgeSelector(): BridgeSelector = NodeBridgeAccessibilityCodec.selectorOf(this)

    private fun UiObject.toBridgeJson(): JSONObject = NodeBridgeAccessibilityCodec.nodeJson(BridgeNode.of(this))

    private fun dispatchMediaProjection(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = request.argsArray()
        when (request.method) {
            "requestScreenCapture" -> screenCapture.request(args.optJSONObject(0) ?: JSONObject(), request.timeoutMs) { result ->
                result.fold(
                    onSuccess = { info ->
                        if (imageDestroyed.get()) {
                            callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead()))
                        } else {
                            val id = info.getString("id")
                            // One active capturer per execution. Repeated requests reuse its handle.
                            if (screenCaptureResourceId != id) {
                                screenCaptureResourceId = id
                                resourceRegistry.register(NodeBridgeResourceType.SCREEN_CAPTURER, id) {
                                    screenCapture.stop(id)
                                }
                            }
                            callback(NodeBridgeResponse.success(request.id, info.toString()))
                        }
                    },
                    onFailure = { callback(imageFailure(request, it)) },
                )
            }
            "nextImage" -> imageOperation(request, callback) {
                val bitmap = screenCapture.capture(args.optString(0).takeIf(String::isNotBlank))
                registerImage(bitmap, "screen").toJson().toString()
            }
            "stop" -> imageOperation(request, callback) {
                val id = args.optString(0).takeIf(String::isNotBlank)
                if (id == null) {
                    // Also cancel an outstanding consent dialog, before a capturer id exists.
                    screenCapture.stop()
                    screenCaptureResourceId?.let { resourceRegistry.close(it, "media_projection.stop") }
                    screenCaptureResourceId = null
                } else {
                    resourceRegistry.close(id, "media_projection.stop")
                    if (screenCaptureResourceId == id) screenCaptureResourceId = null
                }
                "null"
            }
        }
    }

    @Volatile private var screenCaptureResourceId: String? = null

    /** Node and Bun share the execution capture session and serialized image lane. */
    internal fun dispatchScreenRecording(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) = imageOperation(request, callback) {
        val args = request.argsArray()
        when (request.method) {
            "start" -> {
                require(args.length() in 1..2 && args.opt(0) is String && (args.length() < 2 || args.opt(1) is JSONObject)) { "Recording start expects a path and optional options object." }
                val file = resolveWritableFile(request, args.getString(0)) ?: throw IllegalArgumentException("Invalid recording output path.")
                screenCapture.startRecording(file, args.optJSONObject(1) ?: JSONObject()).toString()
            }
            "stop", "cancel" -> {
                require(args.length() <= 1 && (args.length() == 0 || args.opt(0) is JSONObject)) { "Recording control expects optional options." }
                val options = args.optJSONObject(0)
                val expected = if (options?.has("id") == true) options.opt("id").also {
                    require(it is String && it.isNotBlank()) { "Recording id must be a non-empty string." }
                } as String else null
                if (request.method == "stop") screenCapture.stopRecording(expected)?.toString() ?: "null"
                else screenCapture.cancelRecording(expected).toString()
            }
            "getStatus" -> { require(args.length() == 0); screenCapture.recordingStatus().toString() }
            else -> throw IllegalArgumentException("Unknown screen recording operation.")
        }
    }

    private fun onScreenCaptureStopped(id: String) {
        resourceRegistry.release(id, "media_projection.stopped")
        if (screenCaptureResourceId == id) screenCaptureResourceId = null
    }

    private fun dispatchImage(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (request.method == "captureScreen" && request.argsArray().length() > 0) {
            accessibilityDiagnostics.captureScreen(request, { screenCapture.capture() }, callback)
            return
        }
        imageOperation(request, callback) {
            when (request.method) {
                "initOpenCvIfNeeded", "isGrayscale", "inRange", "interval", "adaptiveThreshold", "bilateralFilter", "cvtColor", "findCircles", "concat", "detectMultiColors", "findPointsByColors", "countPointsByColor", "getMeanColor", "imread", "matToImage", "detectAndComputeFeatures", "matchFeatures", "quantize", "quantizeToFile", "compress", "compressToBytes", "downsample", "psnr", "ssim", "mssim", "hist", "mse", "ncc", "isEqual", "getSimilarity", "load", "getScreenCaptureOptions" -> dispatchAdvancedImage(request, callback)
                "captureScreen" -> registerImage(screenCapture.capture(), "screen").toJson().toString()
                "saveImage" -> saveImage(request)
                "fromBase64", "fromBytes" -> {
                    val args = request.argsArray()
                    val bitmap = if (request.method == "fromBytes") {
                        val options = args.optJSONObject(0) ?: throw IllegalArgumentException("fromBytes requires an upload descriptor.")
                        val upload = requireNotNull(request.upload) { "fromBytes requires descriptor upload support." }
                        require(options.optString("bodyTransport") == "pfd" && options.optLong("bodyBytes", -1) == upload.byteCount && upload.byteCount > 0) { "Image upload descriptor does not match the received bytes." }
                        upload.openStream().use { BitmapFactory.decodeStream(it) }
                    } else {
                        val text = args.opt(0) as? String ?: throw IllegalArgumentException("fromBase64 requires a base64 string.")
                        val encoded = if (text.startsWith("data:", ignoreCase = true)) {
                            val comma = text.indexOf(',')
                            require(comma > 0 && text.substring(0, comma).endsWith(";base64", ignoreCase = true)) { "Image data URL must use base64 encoding." }
                            text.substring(comma + 1)
                        } else text
                        val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    requireNotNull(bitmap) { "Encoded data could not be decoded as an image." }
                    registerImage(bitmap, request.method).toJson().toString()
                }
                "toBase64" -> {
                    val args = request.argsArray()
                    val options = args.optJSONObject(1) ?: JSONObject()
                    val format = options.optString("format", "png").lowercase(Locale.ROOT)
                    require(format in setOf("png", "jpg", "jpeg", "webp")) { "Base64 image format must be png, jpg, jpeg or webp." }
                    val encoded = NodeImageByteTransport.encode(appContext, imageArgument(args, 0).bitmap, format, options.optInt("quality", 100))
                    val bytes = ParcelFileDescriptor.AutoCloseInputStream(encoded.descriptor).use { it.readBytes() }
                    NodeBridgeJson.valueToJson(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                }
                "toBytes" -> {
                    require(request.binary) { "Image bytes require a client with PFD transport support." }
                    val args = request.argsArray()
                    val options = args.optJSONObject(1) ?: JSONObject()
                    val format = options.optString("format", "png").lowercase(Locale.ROOT)
                    val bitmap = imageArgument(args, 0).bitmap
                    val region = imageOperations.region(bitmap, options)
                    val bytes = NodeImageByteTransport.encode(appContext, bitmap, format, options.optInt("quality", 100),
                        android.graphics.Rect(region.x, region.y, region.x + region.width, region.y + region.height))
                    callback(NodeBridgeResponse(request.id, true,
                        JSONObject().put("byteCount", bytes.size).put("format", format).toString(),
                        binary = bytes.descriptor, binaryByteCount = bytes.size))
                    null
                }
                "clip", "resize", "grayscale", "threshold", "copy", "rotate", "scale", "flip", "blur", "gaussianBlur", "medianBlur", "invert" -> {
                    val args = request.argsArray()
                    val source = imageArgument(args, 0)
                    val bitmap = imageOperations.transform(request.method, source.bitmap, args.optJSONObject(1) ?: JSONObject())
                    val retained = source.nativeMat?.let { advancedImages.value.matFromBitmap(bitmap,
                        if (request.method in setOf("grayscale", "threshold")) 1 else it.channels()) }
                    registerImage(bitmap, request.method, retained).toJson().toString()
                }
                "findImage", "matchTemplate" -> {
                    val args = request.argsArray()
                    imageOperations.match(imageArgument(args, 0).bitmap, imageArgument(args, 1).bitmap,
                        args.optJSONObject(2) ?: JSONObject(), request.method == "matchTemplate")
                }
                "findColor", "findMultiColors" -> {
                    val args = request.argsArray()
                    val multiple = request.method == "findMultiColors"
                    imageOperations.findColors(imageArgument(args, 0).bitmap, args.get(1),
                        if (multiple) args.getJSONArray(2) else JSONArray(), args.optJSONObject(if (multiple) 3 else 2) ?: JSONObject())
                }
                "pixel" -> {
                    val args = request.argsArray()
                    imageOperations.pixel(imageArgument(args, 0).bitmap, imageOperations.integer(args.opt(1), "x"), imageOperations.integer(args.opt(2), "y")).toString()
                }
                "readPixels" -> {
                    val args = request.argsArray()
                    imageOperations.readPixels(imageArgument(args, 0).bitmap, args.optJSONObject(1) ?: JSONObject()).toString()
                }
                "detectsColor" -> {
                    val args = request.argsArray()
                    imageOperations.detectsColor(imageArgument(args, 0).bitmap, args.get(1), imageOperations.integer(args.opt(2), "x"), imageOperations.integer(args.opt(3), "y"), args.optJSONObject(4) ?: JSONObject()).toString()
                }
                "findAllPointsForColor" -> {
                    val args = request.argsArray()
                    imageOperations.findAllPointsForColor(imageArgument(args, 0).bitmap, args.get(1), args.optJSONObject(2) ?: JSONObject())
                }
                "readImage" -> {
                    dispatchImageRead(request, callback)
                    null
                }
                "recycle" -> {
                    dispatchImageRecycle(request, callback)
                    null
                }
                else -> {
                    callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.capabilityProviderMissing(
                        request.module, request.method, "an image processing provider for ${request.method}",
                    )))
                    null
                }
            }
        }
    }

    private fun dispatchAdvancedImage(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit): String? {
        val args=request.argsArray();val method=request.method;val advanced=advancedImages.value
        fun options(index:Int)=args.optJSONObject(index)?:JSONObject()
        fun path(index:Int):String {
            val value=args.opt(index)
            require(value is String && value.isNotEmpty() && '\u0000' !in value){"Image path or URL must be a non-empty string without NUL."}
            return value
        }
        fun publish(value:NodeAdvancedImages.NativeImage)=registerImage(value.bitmap,method,value.mat).toJson().toString()
        fun bytes(value:ByteArray,metadata:JSONObject=JSONObject()):String? {
            if(request.binary){
                val data=NodeImageByteTransport.bytes(appContext,value)
                callback(NodeBridgeResponse(request.id,true,metadata.put("byteCount",value.size).toString(),binary=data.descriptor,binaryByteCount=data.size))
                return null
            }
            val base64=android.util.Base64.encodeToString(value,android.util.Base64.NO_WRAP)
            return if(method=="quantize")metadata.put("base64",base64).toString() else NodeBridgeJson.valueToJson(base64)
        }
        fun quantizationMetadata(result:org.autojs.autojs.runtime.api.PngQuantBridge.Result)=JSONObject().put("size",result.size)
            .put("quality",result.quality).put("quantizationError",result.quantizationError).put("peakWorkingMemoryBytes",result.peakWorkingMemoryBytes)
        when(method){
            "initOpenCvIfNeeded"->{require(args.length()==0);advanced.init();return "null"}
            "getScreenCaptureOptions"->{require(args.length()==0);return screenCapture.captureOptions()?.toString()?:"null"}
            "imread"->{val file=resolveReadableFile(request,path(0))?:throw IllegalArgumentException("Invalid image path.");return advanced.imread(file.path).toString()}
            "matToImage"->return publish(advanced.matToImage(args.getJSONObject(0)))
            "load"->{
                val url=java.net.URL(path(0))
                if(url.protocol=="file"){
                    val file=resolveReadableFile(request,java.io.File(url.toURI()).path)?:throw IllegalArgumentException("Invalid image path.")
                    return BitmapFactory.decodeFile(file.path)?.let{registerImage(it,method).toJson().toString()}?:"null"
                }
                require(url.protocol in setOf("http","https")){"Image URL must use http, https or file."}
                val connection=url.openConnection().apply{connectTimeout=5000;readTimeout=5000;doInput=true}
                try{return connection.getInputStream().use{BitmapFactory.decodeStream(it)}?.let{registerImage(it,method).toJson().toString()}?:"null"}
                finally{(connection as? java.net.HttpURLConnection)?.disconnect()}
            }
            "matchFeatures"->{
                val opt=options(2);val temporary=mutableListOf<NodeAdvancedImages.Features>()
                fun descriptor(index:Int):NodeAdvancedImages.Features {
                    advanced.feature(args.opt(index))?.let{return it}
                    val image=imageArgument(args,index)
                    val detection=JSONObject().apply{for(key in listOf("method","scale","grayscale","maxFeatures"))if(opt.has(key))put(key,opt.get(key))}
                    return advanced.detect(image.bitmap,image.nativeMat,detection).also{temporary.add(it)}
                }
                try{
                    val draw=opt.opt("drawMatches")?.takeUnless{it==JSONObject.NULL}?.let {
                        require(it is String){"drawMatches must be an output path."}
                        resolveWritableFile(request,it)?:throw IllegalArgumentException("Invalid match drawing path.")
                    }
                    return advanced.match(descriptor(0),descriptor(1),opt,draw?.let{file->{bitmap:Bitmap->
                        file.parentFile?.mkdirs();file.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,100,it)){"Match drawing encoding failed."}}
                    }})?.toString()?:"null"
                }finally{temporary.forEach{it.descriptor.release()}}
            }
        }
        val input=imageArgument(args,0);val bitmap=input.bitmap;val opt=options(1)
        return when(method){
            "isGrayscale"->advanced.isGrayscale(bitmap).toString()
            "inRange","interval","adaptiveThreshold","bilateralFilter","cvtColor"->publish(advanced.filter(method,bitmap,input.nativeMat,opt))
            "findCircles"->advanced.circles(bitmap,input.nativeMat,opt).toString()
            "findPointsByColors"->advanced.findPoints(bitmap,args.get(1),args.getJSONArray(2),options(3)).toString()
            "countPointsByColor"->advanced.count(bitmap,args.get(1),options(2)).toString()
            "getMeanColor"->advanced.mean(bitmap,opt).toString()
            "detectMultiColors"->advanced.detectMultiple(bitmap,args.get(1),args.getJSONArray(2),imageOperations.integer(args.opt(3),"x"),imageOperations.integer(args.opt(4),"y"),options(5)).toString()
            "detectAndComputeFeatures"->advanced.registerFeatures(advanced.detect(bitmap,input.nativeMat,opt)).toString()
            "psnr","ssim","mssim","hist","mse","ncc","isEqual","getSimilarity"->{
                val other=imageArgument(args,1);NodeBridgeJson.valueToJson(advanced.similarity(method,bitmap,input.nativeMat,other.bitmap,other.nativeMat,options(2)))
            }
            "concat"->{
                val second=imageArgument(args,1).bitmap
                val direction=options(2).optString("direction","right").lowercase()
                require(direction in setOf("left","right","top","bottom","start","end")){"Invalid image concatenation direction."}
                val horizontal=direction in setOf("left","right","start","end")
                val first=if(direction in setOf("left","top","start"))second else bitmap
                val last=if(first===bitmap)second else bitmap
                val width=if(horizontal)Math.addExact(first.width,last.width)else maxOf(first.width,last.width)
                val height=if(horizontal)maxOf(first.height,last.height)else Math.addExact(first.height,last.height)
                val output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(output);val paint=android.graphics.Paint()
                if(horizontal){canvas.drawBitmap(first,0f,(height-first.height)/2f,paint);canvas.drawBitmap(last,first.width.toFloat(),(height-last.height)/2f,paint)}
                else{canvas.drawBitmap(first,(width-first.width)/2f,0f,paint);canvas.drawBitmap(last,(width-last.width)/2f,first.height.toFloat(),paint)}
                registerImage(output,method).toJson().toString()
            }
            "quantize","quantizeToFile"->{
                if(method=="quantizeToFile"){
                    val file=resolveWritableFile(request,path(1))?:throw IllegalArgumentException("Invalid quantization output path.")
                    val quant=advanced.quantizeToFile(bitmap,file,options(2))
                    JSONObject().put("path",file.path).put("size",quant.size).put("quality",quant.quality)
                        .put("quantizationError",quant.quantizationError).put("peakWorkingMemoryBytes",quant.peakWorkingMemoryBytes).toString()
                }else {val quant=advanced.quantize(bitmap,opt);bytes(quant.bytes,quantizationMetadata(quant))}
            }
            "compress","compressToBytes"->{
                val format=opt.optString("format","png").lowercase();val quality=imageOperations.integer(opt.opt("quality")?:60,"quality")
                val quantOptions=opt.optJSONObject("quantization")
                if(quantOptions!=null)require(format=="png"){"Named quantization options require PNG format."}
                val encoded=if(format=="png"&&(quality!=100||quantOptions!=null))advanced.quantize(bitmap,quantOptions?:JSONObject().put("quality",quality)).bytes else advanced.encode(bitmap,format,quality)
                if(method=="compressToBytes")bytes(encoded)else registerImage(requireNotNull(BitmapFactory.decodeByteArray(encoded,0,encoded.size)){"Compressed image could not be decoded."},method).toJson().toString()
            }
            "downsample"->{
                val width=imageOperations.integer(opt.opt("width"),"width");val height=imageOperations.integer(opt.opt("height"),"height")
                require(width>0&&height>0){"Downsample dimensions must be positive."}
                val scaled=org.autojs.autojs.util.BitmapUtils.downsample(bitmap,width,height,opt.optBoolean("withAlpha",true))
                val owned=if(scaled===bitmap)checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888,false))else scaled
                registerImage(owned,method).toJson().toString()
            }
            else->throw IllegalArgumentException("Unknown advanced image method: $method")
        }
    }

    private fun imageOperation(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit, operation: () -> String?) {
        try {
            imageExecutor.execute {
                val response = runCatching {
                    synchronized(imageLock) {
                        if (imageDestroyed.get()) throw java.util.concurrent.CancellationException("Image provider is closed.")
                        operation()?.let { NodeBridgeResponse.success(request.id, it) }
                    }
                }.getOrElse { imageFailure(request, it) }
                response?.let(callback)
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead()))
        }
    }

    private fun imageFailure(request: NodeBridgeRequest, error: Throwable): NodeBridgeResponse {
        val message = error.message ?: "Image operation failed."
        val failure = when (error) {
            is SecurityException -> NodeBridgeError.permissionDenied(message, request.module, request.method)
            is IllegalArgumentException, is org.json.JSONException -> NodeBridgeError.invalidRequest(message, request.module, request.method)
            is java.util.concurrent.TimeoutException -> NodeBridgeError.timeout(request.module, request.method)
            is java.util.concurrent.CancellationException, is InterruptedException -> NodeBridgeError.processDead(message)
            is ImageHandleLimitException -> NodeBridgeError.resourceLimit(message, request.module, request.method)
            else -> NodeBridgeError.providerFailed(message, request.module, request.method)
        }
        return NodeBridgeResponse.failure(request.id, failure)
    }

    private class ImageHandleLimitException(message: String) : RuntimeException(message)

    private fun imageHandleCount() = imageHandles.size + (if (advancedImages.isInitialized()) advancedImages.value.handleCount else 0)

    private fun checkImageRoom() {
        if (imageHandleCount() >= bridgeLimits.maxImageHandles) {
            bridgeResourceLimitRejections.incrementAndGet()
            throw ImageHandleLimitException("Image bridge exceeded ${bridgeLimits.maxImageHandles} active image/Mat/feature handles.")
        }
    }

    private fun registerImage(bitmap: Bitmap, source: String, nativeMat: org.opencv.core.Mat? = null): ControlledImageHandle {
        try { checkImageRoom() } catch (error: Throwable) { bitmap.recycle(); nativeMat?.release(); throw error }
        val id = "image-${UUID.randomUUID()}"
        val resource = resourceRegistry.register(NodeBridgeResourceType.IMAGE_HANDLE, id, mapOf(
            "source" to source, "width" to bitmap.width.toString(), "height" to bitmap.height.toString(),
        ), resource = bitmap) { if (!bitmap.isRecycled) bitmap.recycle(); nativeMat?.release() }
        return ControlledImageHandle(id, bitmap, resource, nativeMat).also { imageHandles[id] = it }
    }

    private fun imageArgument(args: JSONArray, index: Int): ControlledImageHandle {
        val value = args.optJSONObject(index)
        val id = value?.optNonBlankString("__autojs6ImageHandle") ?: value?.optNonBlankString("id")
            ?: throw IllegalArgumentException("Image argument $index requires an image handle.")
        return imageHandles[id]?.takeUnless { it.bitmap.isRecycled }
            ?: throw IllegalArgumentException("Image argument $index must be an active image handle from this execution.")
    }

    private fun saveImage(request: NodeBridgeRequest): String {
        val args = request.argsArray()
        val image = imageArgument(args, 0)
        val file = resolveWritableFile(request, args.optString(1))
            ?: throw SecurityException("Image output path must be a non-empty file path without NUL bytes.")
        val options = args.optJSONObject(2) ?: JSONObject()
        // Preserve legacy WEBP behavior, including lossless output at quality 100 on API 29+.
        // zh-CN: 保留旧有 WEBP 行为, 包括 API 29+ 在 quality 为 100 时输出无损格式.
        @Suppress("DEPRECATION")
        val format = when (options.optString("format", "png").lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> Bitmap.CompressFormat.WEBP
            else -> throw IllegalArgumentException("Image format must be png, jpg, jpeg or webp.")
        }
        val quality = options.optInt("quality", 100)
        require(quality in 0..100) { "Image quality must be in 0..100." }
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { stream ->
            check(image.bitmap.compress(format, quality, stream)) { "Image encoding failed." }
        }
        return "true"
    }

    private fun dispatchOcr(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = request.argsArray()
        val imageArg = args.optJSONObject(0)
        val options = args.optJSONObject(1) ?: JSONObject()
        val imageId = imageArg?.optNonBlankString("__autojs6ImageHandle")
            ?: imageArg?.optNonBlankString("id")
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 OCR requires an existing provider-owned image handle.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        val handle = imageHandles[imageId]
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 OCR image handle is not active or has been recycled: $imageId",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        val ocrInput = runCatching { handle.ocrBitmap(options) }.getOrElse { error ->
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = error.message ?: "AutoJs6 OCR region is invalid.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        val operationResource = resourceRegistry.register(
            type = NodeBridgeResourceType.OCR_OPERATION,
            id = "ocr-${request.id}",
            metadata = mapOf(
                "method" to request.method,
                "image" to imageId,
            ),
        ) {
            ocrInput.recycleIfNeeded()
        }
        val operation = ControlledOcrOperation(resource = operationResource)
        ocrOperations[request.id] = operation
        ocrDiagnostics.recordDispatch()
        val timeoutMs = request.timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)
        val timeoutRunnable = Runnable {
            if (operation.finish()) {
                ocrOperations.remove(request.id)
                operation.resource.close("timeout:$timeoutMs")
                ocrDiagnostics.recordFailure()
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.timeout(request.module, request.method),
                    ),
                )
            }
        }
        operation.timeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)

        thread(name = "AutoJs6NodeOcr-${request.id}", isDaemon = true) {
            val response = runCatching {
                val ocrOptions = buildOcrPluginOptions(options)
                val results = runBlocking {
                    // Without an explicit engine, pick the first enabled plugin in ML Kit / Paddle / Rapid
                    // order, the same automatic selection accessibility.readScreenText uses.
                    val target = selectRecognitionProvider { OcrPluginHost.selectOrThrow(
                        context = appContext,
                        engineId = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE_ID),
                        engine = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.ENGINE),
                        variant = ocrOptions.extras.getNonBlankString(PaddleOcrOptionExtraKeys.VARIANT),
                        moduleLabel = OCR_MODULE_LABEL,
                        automatic = true,
                    ) }
                    OcrPluginHost.detect(appContext, target, ocrInput.bitmap, ocrOptions, callTimeoutMs = timeoutMs)
                }
                NodeBridgeResponse.success(request.id, results.toOcrResultsJson(ocrInput.offsetX, ocrInput.offsetY))
            }.getOrElse { error ->
                ocrDiagnostics.recordFailure()
                NodeBridgeResponse.failure(
                    request.id,
                    if (error is RecognitionProviderUnavailable) recognitionUnavailable(request, error) else NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = request.module,
                        method = request.method,
                        stack = error.stackTraceToString(),
                        code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                    ),
                )
            }

            if (operation.finish()) {
                mainHandler.removeCallbacks(timeoutRunnable)
                ocrOperations.remove(request.id)
                operation.resource.close(if (response.ok) "success" else "failed")
                callback(response)
            }
        }
    }

    private fun buildOcrPluginOptions(options: JSONObject): OcrOptions {
        val extras = Bundle()
        options.optNonBlankString(PaddleOcrOptionExtraKeys.ENGINE_ID)?.let {
            extras.putString(PaddleOcrOptionExtraKeys.ENGINE_ID, it)
        }
        options.optNonBlankString(PaddleOcrOptionExtraKeys.ENGINE)?.let {
            extras.putString(PaddleOcrOptionExtraKeys.ENGINE, it)
        }
        options.optNonBlankString(PaddleOcrOptionExtraKeys.VARIANT)?.let {
            extras.putString(PaddleOcrOptionExtraKeys.VARIANT, it)
        }
        val profile = options.optNonBlankString(PaddleOcrOptionExtraKeys.PROFILE)
            ?: options.optNonBlankString("lang")
            ?: options.optNonBlankString("model")
        profile?.let {
            extras.putString(PaddleOcrOptionExtraKeys.PROFILE, it)
        }
        if ((options.optBoolean("useRaw", true) || options.optBoolean("raw", false)) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        ) {
            extras.putBoolean(PaddleOcrOptionExtraKeys.RAW_IMAGE, true)
        }
        options.optInt("imageQuality", -1).takeIf { it > 0 }?.let {
            extras.putInt(PaddleOcrOptionExtraKeys.IMAGE_QUALITY, it)
        }
        options.optNonBlankString("imageFormat")?.let {
            extras.putString(PaddleOcrOptionExtraKeys.IMAGE_FORMAT, it)
        }
        return OcrOptions().apply {
            this.extras = extras.takeIf { !it.isEmpty } ?: Bundle()
        }
    }

    private class RecognitionProviderUnavailable(cause: IllegalStateException) : RuntimeException(cause.message, cause)

    private inline fun <T> selectRecognitionProvider(select: () -> T): T = try {
        select()
    } catch (error: IllegalStateException) {
        throw RecognitionProviderUnavailable(error)
    }

    private fun recognitionUnavailable(request: NodeBridgeRequest, error: RecognitionProviderUnavailable) =
        NodeBridgeError.capabilityProviderMissing(request.module, request.method, "an enabled compatible recognition plugin")
            .copy(message = error.message ?: "No compatible recognition plugin is available.")

    private fun Bundle.getNonBlankString(key: String): String? {
        return getString(key)?.takeIf { it.isNotBlank() }
    }

    private fun dispatchBarcode(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = request.argsArray()
        val imageArg = args.optJSONObject(0)
        val options = args.optJSONObject(1) ?: JSONObject()
        val imageId = imageArg?.optNonBlankString("__autojs6ImageHandle")
            ?: imageArg?.optNonBlankString("id")
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 barcode requires an existing provider-owned image handle.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        val handle = imageHandles[imageId]
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 barcode image handle is not active or has been recycled: $imageId",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        val barcodeOptions = runCatching { ControlledBarcodeOptions.fromOptions(options) }.getOrElse { error ->
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = error.message ?: "AutoJs6 barcode options are invalid.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        val barcodeInput = runCatching { handle.barcodeBitmap(options) }.getOrElse { error ->
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = error.message ?: "AutoJs6 barcode region is invalid.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        val operationResource = resourceRegistry.register(
            type = NodeBridgeResourceType.BARCODE_OPERATION,
            id = "barcode-${request.id}",
            metadata = mapOf(
                "method" to request.method,
                "image" to imageId,
            ),
        ) {
            barcodeInput.recycleIfNeeded()
            barcodeOperations[request.id]?.job?.cancel()
        }
        val operation = ControlledBarcodeOperation(resource = operationResource)
        barcodeOperations[request.id] = operation
        barcodeDiagnostics.recordDispatch()
        val timeoutMs = request.timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)
        val timeoutRunnable = Runnable {
            if (operation.finish()) {
                barcodeOperations.remove(request.id)
                operation.job?.cancel()
                operation.resource.close("timeout:$timeoutMs")
                barcodeDiagnostics.recordFailure()
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.timeout(request.module, request.method),
                    ),
                )
            }
        }
        operation.timeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)

        operation.job = CoroutineScope(Dispatchers.IO).launch {
            val response = runCatching {
                val target = selectRecognitionProvider { BarcodePluginHost.selectOrThrow(
                    context = appContext,
                    engine = ENGINE_MLKIT_BARCODE,
                ) }
                BarcodePluginHost.detect(
                    context = appContext,
                    target = target,
                    bitmap = barcodeInput.bitmap,
                    options = barcodeOptions.toPluginOptions(),
                    callTimeoutMs = timeoutMs,
                ).toBarcodeResponseJson(
                    method = request.method,
                    maxResults = barcodeOptions.maxResults,
                    offsetX = barcodeInput.offsetX,
                    offsetY = barcodeInput.offsetY,
                )
            }.fold(
                onSuccess = { resultJson ->
                    NodeBridgeResponse.success(request.id, resultJson)
                },
                onFailure = { error ->
                    NodeBridgeResponse.failure(
                        request.id,
                        if (error is RecognitionProviderUnavailable) recognitionUnavailable(request, error) else NodeBridgeError.providerFailed(
                            message = error.message ?: error.javaClass.name,
                            module = request.module,
                            method = request.method,
                            stack = error.stackTraceToString(),
                            code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                        ),
                    )
                },
            )
            mainHandler.post {
                if (operation.finish()) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    barcodeOperations.remove(request.id)
                    operation.resource.close(if (response.ok) "success" else "failed")
                    if (!response.ok) {
                        barcodeDiagnostics.recordFailure()
                    }
                    callback(response)
                }
            }
        }
    }

    private fun dispatchMedia(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            runCatching {
                val args = request.argsArray()
                val stream = audioStreamFromArg(args.opt(0))
                when (request.method) {
                    "getAudioStreamVolume" -> NodeBridgeResponse.success(
                        request.id,
                        NodeBridgeJson.valueToJson(audioManager().getStreamVolume(stream.type)),
                    )
                    "getAudioStreamMaxVolume" -> NodeBridgeResponse.success(
                        request.id,
                        NodeBridgeJson.valueToJson(audioManager().getStreamMaxVolume(stream.type)),
                    )
                    "getAudioStreamInfo" -> NodeBridgeResponse.success(
                        request.id,
                        audioStreamInfo(stream).toString(),
                    )
                    "setAudioStreamVolume" -> {
                        val value = args.opt(1)
                        val manager = audioManager()
                        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.getStreamMinVolume(stream.type) else 0
                        val max = manager.getStreamMaxVolume(stream.type)
                        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble() && value.toInt() in min..max) {
                            "media.setAudioStreamVolume requires an integer in $min..$max for ${stream.name}."
                        }
                        manager.setStreamVolume(stream.type, value.toInt(), 0)
                        NodeBridgeResponse.success(request.id, "null")
                    }
                    else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
                }
            }.getOrElse { error ->
                NodeBridgeResponse.failure(
                    request.id,
                    if (error is SecurityException) NodeBridgeError.permissionDenied(
                        error.message ?: "Android denied the audio change.", request.module, request.method,
                    ) else NodeBridgeError.invalidRequest(
                        error.message ?: "AutoJs6 media request failed.", request.module, request.method,
                    ),
                )
            }
        }
    }

    private fun dispatchMediainfo(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        runCatching {
            if (request.method == "capabilities") {
                val capabilities = runBlocking { MediainfoPluginHost.capabilities(appContext) }
                val result = JSONObject()
                    .put("schema", MEDIAINFO_CAPABILITIES_SCHEMA)
                    .put("snapshotSchemas", JSONArray(capabilities.snapshotSchemas))
                    .put("defaultSnapshotSchema", capabilities.defaultSnapshotSchema)
                    .put("streamCount", capabilities.streamCount)
                    .put("streamNumber", true)
                    .put("infoKinds", JSONArray(capabilities.infoKinds))
                capabilities.engineVersion?.let { result.put("engineVersion", it) }
                return callback(NodeBridgeResponse.success(request.id, result.toString()))
            }
            val args = request.argsArray()
            val rawPath = args.optString(0, "")
            val file = resolveReadableFile(request, rawPath)
                ?: return callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.permissionDenied(
                            message = "AutoJs6 mediainfo path must be a non-empty file path without NUL bytes.",
                            module = request.module,
                            method = request.method,
                        ),
                    ),
                )
            if (!file.isFile) {
                return callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.invalidRequest(
                            message = "AutoJs6 mediainfo target does not exist: $rawPath",
                            module = request.module,
                            method = request.method,
                        ),
                    ),
                )
            }
            when (request.method) {
                "read" -> {
                    val options = args.optJSONObject(1) ?: JSONObject()
                    callback(NodeBridgeResponse.success(request.id, mediainfoSnapshot(rawPath, file, options).toString()))
                }
                "get" -> {
                    val streamKind = mediaInfoStreamKind(args.optString(1, "general"))
                    val parameter = args.optString(2, "")
                    val rawOptions: Any? = args.opt(3).takeUnless { it == JSONObject.NULL }
                    val options = when (rawOptions) {
                        null -> null
                        is JSONObject -> rawOptions
                        else -> throw IllegalArgumentException("MediaInfo query options must be an object")
                    }
                    val query = MediainfoQueryOptions.parse(
                        options?.opt("streamNumber").takeUnless { it == JSONObject.NULL },
                        options?.opt("infoKind").takeUnless { it == JSONObject.NULL },
                    )
                    val value = runBlocking { MediainfoPluginHost.get(appContext, file, streamKind, query.streamNumber, parameter, query.infoKind) }
                    callback(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(value)))
                }
                "countGet" -> {
                    val streamKind = mediaInfoStreamKind(args.optString(1, "general"))
                    val value = runBlocking { MediainfoPluginHost.countGet(appContext, file, streamKind) }
                    callback(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(value)))
                }
                else -> callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method)))
            }
        }.getOrElse { error ->
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    if (error is SecurityException) {
                        NodeBridgeError.permissionDenied(error.message ?: "AutoJs6 mediainfo path is not allowed.", request.module, request.method)
                    } else if (error is IllegalStateException && MediainfoPluginHost.queryServiceCount(appContext) == 0) {
                        NodeBridgeError.capabilityProviderMissing(request.module, request.method, "an installed MediaInfo plugin")
                            .copy(message = error.message ?: "Install the MediaInfo plugin to inspect audio files.")
                    } else NodeBridgeError.invalidRequest(
                        message = error.message ?: "AutoJs6 mediainfo request failed.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        }
    }

    private fun dispatchRecorder(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        audioRecorder.value.dispatch(request.method, request.argsArray().optJSONObject(0) ?: JSONObject()) { result ->
            result.fold(
                onSuccess = { callback(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(it))) },
                onFailure = { error -> callback(NodeBridgeResponse.failure(request.id, when (error) {
                    is SecurityException -> NodeBridgeError.permissionDenied(error.message ?: "Microphone permission is required.", request.module, request.method)
                    is IllegalArgumentException -> NodeBridgeError.invalidRequest(error.message ?: "Invalid audio recording request.", request.module, request.method)
                    else -> NodeBridgeError.providerFailed(error.message ?: "Android could not record audio.", request.module, request.method)
                })) },
            )
        }
    }

    private fun dispatchMediaPlayback(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        mediaPlayback.value.dispatch(request.method, request.argsArray()) { result ->
            result.fold(
                onSuccess = { callback(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(it))) },
                onFailure = { error -> callback(NodeBridgeResponse.failure(request.id, mediaBridgeError(error, request, "Android could not control media playback."))) },
            )
        }
    }

    private fun dispatchMediaStore(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        mediaStore.value.dispatch(request.method, request.argsArray()) { result ->
            result.fold(
                onSuccess = { callback(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(it))) },
                onFailure = { error -> callback(NodeBridgeResponse.failure(request.id, mediaBridgeError(error, request, "Android MediaStore request failed."))) },
            )
        }
    }

    private fun mediaBridgeError(error: Throwable, request: NodeBridgeRequest, fallback: String): NodeBridgeError = when (error) {
        is SecurityException -> NodeBridgeError.permissionDenied(error.message ?: "Android denied the media request.", request.module, request.method)
        is IllegalArgumentException, is java.io.FileNotFoundException -> NodeBridgeError.invalidRequest(error.message ?: fallback, request.module, request.method)
        else -> NodeBridgeError.providerFailed(error.message ?: fallback, request.module, request.method)
    }

    private fun audioManager(): AudioManager {
        return appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: throw IllegalStateException("Android AudioManager is unavailable.")
    }

    private fun audioStreamFromArg(raw: Any?): ControlledAudioStream {
        val text = when (raw) {
            null, JSONObject.NULL -> "music"
            is JSONObject -> raw.optString("stream", raw.optString("type", "music"))
            else -> raw.toString()
        }.trim().lowercase(Locale.US).replace('-', '_')
        return when (text) {
            "", "music", "media", "stream_music" -> ControlledAudioStream("music", AudioManager.STREAM_MUSIC)
            "notification", "notifications", "stream_notification" -> ControlledAudioStream("notification", AudioManager.STREAM_NOTIFICATION)
            "alarm", "stream_alarm" -> ControlledAudioStream("alarm", AudioManager.STREAM_ALARM)
            "ring", "ringtone", "stream_ring" -> ControlledAudioStream("ring", AudioManager.STREAM_RING)
            "system", "stream_system" -> ControlledAudioStream("system", AudioManager.STREAM_SYSTEM)
            "voice_call", "call", "stream_voice_call" -> ControlledAudioStream("voice_call", AudioManager.STREAM_VOICE_CALL)
            "dtmf", "stream_dtmf" -> ControlledAudioStream("dtmf", AudioManager.STREAM_DTMF)
            else -> throw IllegalArgumentException("Unsupported audio stream: $text")
        }
    }

    private fun audioStreamInfo(stream: ControlledAudioStream): JSONObject {
        val manager = audioManager()
        return JSONObject()
            .put("schema", "autojs6-node-media-audio-stream-v1")
            .put("stream", stream.name)
            .put("streamType", stream.type)
            .put("volume", manager.getStreamVolume(stream.type))
            .put("maxVolume", manager.getStreamMaxVolume(stream.type))
            .put("minVolume", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.getStreamMinVolume(stream.type) else 0)
            .put("volumeFixed", manager.isVolumeFixed)
    }

    private fun mediainfoSnapshot(requestedPath: String, file: File, options: JSONObject): JSONObject {
        val includeInform = options.optBoolean("includeInform", true)
        val includeSections = options.optBoolean("includeSections", true)
        val rawSchema: Any? = options.opt("schema")
        val requestedSchema = when (rawSchema) {
            null, JSONObject.NULL -> null
            is String -> rawSchema
            else -> throw IllegalArgumentException("MediaInfo snapshot schema must be a string")
        }
        val pluginSchema = requestedSchema?.takeUnless { it.isBlank() }
        if (pluginSchema != null && pluginSchema !in MediainfoSnapshotSchemas.VALUES) {
            throw IllegalArgumentException("Unsupported MediaInfo snapshot schema: $pluginSchema")
        }
        val rawSnapshot = runBlocking {
            MediainfoPluginHost.snapshot(
                context = appContext,
                file = file,
                includeInform = includeInform,
                includeSections = includeSections,
                schema = pluginSchema,
            )
        }
        val snapshot = JSONObject(rawSnapshot)
        if (pluginSchema != null) {
            require(snapshot.optString("schema") == pluginSchema) {
                "MediaInfo plugin returned an unexpected snapshot schema"
            }
            return snapshot
        }
        require(snapshot.optString("schema") == MediainfoSnapshotSchemas.V1) {
            "MediaInfo plugin returned an unexpected default snapshot schema"
        }
        return snapshot
            .put("schema", MEDIAINFO_NODE_SNAPSHOT_SCHEMA_V1)
            .put("path", requestedPath)
    }

    private fun mediaInfoStreamKind(raw: String): String {
        val normalized = raw.trim().uppercase(Locale.US)
        return MEDIAINFO_STREAM_KINDS.firstOrNull { it == normalized }
            ?: throw IllegalArgumentException("Unsupported mediainfo stream kind: $raw")
    }

    private val MEDIAINFO_STREAM_KINDS = setOf("GENERAL", "VIDEO", "AUDIO", "TEXT", "OTHER", "IMAGE", "MENU", "MAX")
    private val MEDIAINFO_NODE_SNAPSHOT_SCHEMA_V1 = "autojs6-node-mediainfo-snapshot-v1"
    private val MEDIAINFO_CAPABILITIES_SCHEMA = "autojs6-node-mediainfo-capabilities-v1"

    private fun dispatchImageRead(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = request.argsArray()
        val path = args.optString(0, "")
        val file = try {
            resolveReadableFile(request, path)
        } catch (error: SecurityException) {
            return callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.permissionDenied(error.message ?: "Path is not allowed.", request.module, request.method)))
        }
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.permissionDenied(
                        message = "AutoJs6 image.readImage path must be a non-empty file path without NUL bytes.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        if (!file.isFile) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 image.readImage target does not exist: $path",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        if (imageHandleCount() >= bridgeLimits.maxImageHandles) {
            bridgeResourceLimitRejections.incrementAndGet()
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.resourceLimit(
                        message = "AutoJs6 image bridge exceeded ${bridgeLimits.maxImageHandles} active image handles.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        val decoded = decodeImageFile(file)
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 image.readImage could not decode image: $path",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        val handle = registerImage(decoded.bitmap, file.name)
        val result = handle.toJson()
        if (modelFacingPluginSession) {
            // Tools map recognition bounds back to the file's own pixels with `scale` (MCP roadmap P11 D45).
            result.put("sourceWidth", decoded.sourceWidth)
                .put("sourceHeight", decoded.sourceHeight)
                .put("scale", decoded.bitmap.width.toDouble() / decoded.sourceWidth)
        }
        callback(NodeBridgeResponse.success(request.id, result.toString()))
    }

    private class DecodedImage(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)

    /**
     * Decodes a file for `image.readImage`. Model-facing plugin sessions decode within
     * [NodeImageDownsample.MAX_EDGE_PX] (MCP roadmap P11 D45): a gallery photo at full size costs tens of
     * megabytes per handle and OCR, barcodes or template matching do not need it. Scripts keep full size.
     */
    private fun decodeImageFile(file: File): DecodedImage? {
        if (!modelFacingPluginSession) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            return DecodedImage(bitmap, bitmap.width, bitmap.height)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = NodeImageDownsample.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return DecodedImage(bitmap, bounds.outWidth, bounds.outHeight)
    }

    private fun dispatchImageRecycle(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = request.argsArray()
        val image = args.optJSONObject(0)
        val id = image?.optNonBlankString("__autojs6ImageHandle")
            ?: image?.optNonBlankString("id")
            ?: return callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 image.recycle requires an image handle.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
        if (advancedImages.isInitialized() && advancedImages.value.recycle(id)) { callback(NodeBridgeResponse.success(request.id, "true")); return }
        val handle = imageHandles.remove(id)
        if (handle != null) {
            handle.resource.close("recycle")
        }
        callback(NodeBridgeResponse.success(request.id, "true"))
    }

    /**
     * Read targets by path: scripts resolve freely through [resolveAndroidFile]; model-facing plugin sessions
     * go through [modelPathPolicy] (MCP roadmap P11 D26), which throws [SecurityException] with the reason.
     */
    private fun resolveReadableFile(request: NodeBridgeRequest, rawPath: String): File? {
        val policy = modelPathPolicy ?: return resolveAndroidFile(rawPath)
        if (rawPath.isEmpty() || rawPath.indexOf('\u0000') >= 0) return null
        return policy.resolveRead(rawPath, request.permissions, "${request.module}.${request.method}").file
    }

    /** Write targets by path; model-facing plugin sessions may only write inside the working directory. */
    private fun resolveWritableFile(request: NodeBridgeRequest, rawPath: String): File? {
        val policy = modelPathPolicy ?: return resolveAndroidFile(rawPath)
        if (rawPath.isEmpty() || rawPath.indexOf('\u0000') >= 0) return null
        return policy.resolveWrite(rawPath, "${request.module}.${request.method}").file
    }

    private fun resolveAndroidFile(rawPath: String): File? {
        if (rawPath.isEmpty() || rawPath.indexOf('\u0000') >= 0) {
            return null
        }
        val file = File(rawPath)
        val base = File(engineInfo.cwd.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath)
        // Preserve path components and symlinks for Android's file APIs to resolve.
        return if (file.isAbsolute) file else File(base, rawPath)
    }

    private fun resolveScopedProjectFile(rawPath: String): File? {
        if (rawPath.isBlank() || rawPath.indexOf('\u0000') >= 0) {
            return null
        }
        val input = rawPath.trim().replace('\\', '/')
        if (input.startsWith("/") || input.contains(':')) {
            return null
        }
        val segments = input.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty() || segments.any { it == ".." }) {
            return null
        }
        val base = File(engineInfo.cwd.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath).canonicalFile
        val candidate = File(base, segments.joinToString(File.separator)).canonicalFile
        val basePath = base.path.trimEnd(File.separatorChar)
        val candidatePath = candidate.path
        return candidate.takeIf { candidatePath == basePath || candidatePath.startsWith(basePath + File.separator) }
    }

    private fun dispatchStorage(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val response = runCatching {
            val args = JSONArray(request.argsJson.ifBlank { "[]" })
            val storeName = storageStringArg(request, args, 0, "storage name", NodeStorageBridgePolicy.MAX_STORE_NAME_LENGTH)
            val prefs = appContext.getSharedPreferences(storagePreferencesName(storeName), Context.MODE_PRIVATE)
            when (request.method) {
                "get" -> {
                    val key = storageStringArg(request, args, 1, "key", NodeStorageBridgePolicy.MAX_KEY_LENGTH)
                    val stored = prefs.getString(key, null)
                    val result = JSONObject().put("found", stored != null)
                    if (stored != null) {
                        result.put("value", NodeBridgeJson.parseJsonValue(stored))
                    }
                    NodeBridgeResponse.success(request.id, result.toString())
                }
                "put" -> {
                    val key = storageStringArg(request, args, 1, "key", NodeStorageBridgePolicy.MAX_KEY_LENGTH)
                    if (args.length() < 3) {
                        throw StorageBridgeException(
                            NodeBridgeError.invalidRequest(
                                message = "AutoJs6 storage.put requires a JSON value.",
                                module = request.module,
                                method = request.method,
                            ),
                        )
                    }
                    val valueJson = NodeBridgeJson.valueToJson(args.opt(2))
                    if (valueJson.toByteArray(Charsets.UTF_8).size > NodeStorageBridgePolicy.MAX_VALUE_BYTES) {
                        throw StorageBridgeException(
                            NodeBridgeError.resourceLimit(
                                message = "AutoJs6 storage value exceeded ${NodeStorageBridgePolicy.MAX_VALUE_BYTES} bytes.",
                                module = request.module,
                                method = request.method,
                            ),
                        )
                    }
                    val projectedBytes = storageProjectedBytes(prefs, key, valueJson)
                    if (projectedBytes > NodeStorageBridgePolicy.MAX_STORE_BYTES) {
                        throw StorageBridgeException(
                            NodeBridgeError.resourceLimit(
                                message = "AutoJs6 storage store exceeded ${NodeStorageBridgePolicy.MAX_STORE_BYTES} bytes.",
                                module = request.module,
                                method = request.method,
                            ),
                        )
                    }
                    if (!prefs.edit().putString(key, valueJson).commit()) {
                        throw StorageBridgeException(
                            NodeBridgeError.permissionDenied(
                                message = "AutoJs6 storage write failed.",
                                module = request.module,
                                method = request.method,
                            ),
                        )
                    }
                    NodeBridgeResponse.success(request.id, "null")
                }
                "remove" -> {
                    val key = storageStringArg(request, args, 1, "key", NodeStorageBridgePolicy.MAX_KEY_LENGTH)
                    prefs.edit().remove(key).commit()
                    NodeBridgeResponse.success(request.id, "null")
                }
                "clear" -> {
                    prefs.edit().clear().commit()
                    NodeBridgeResponse.success(request.id, "null")
                }
                "keys" -> {
                    val keys = prefs.all.keys.sorted()
                    NodeBridgeResponse.success(request.id, JSONArray(keys).toString())
                }
                "contains" -> {
                    val key = storageStringArg(request, args, 1, "key", NodeStorageBridgePolicy.MAX_KEY_LENGTH)
                    NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(prefs.contains(key)))
                }
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }.getOrElse { error ->
            val bridgeError = (error as? StorageBridgeException)?.bridgeError
                ?: NodeBridgeError.invalidRequest(
                    message = error.message ?: "AutoJs6 storage request failed.",
                    module = request.module,
                    method = request.method,
                )
            NodeBridgeResponse.failure(request.id, bridgeError)
        }
        callback(response)
    }

    private fun storageStringArg(
        request: NodeBridgeRequest,
        args: JSONArray,
        index: Int,
        label: String,
        maxLength: Int,
    ): String {
        val value = args.opt(index)
        if (value !is String || value.isBlank() || value.length > maxLength || '\u0000' in value) {
            throw StorageBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 storage $label must be a non-empty string up to $maxLength characters.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        return value.trim()
    }

    private fun storageProjectedBytes(prefs: android.content.SharedPreferences, key: String, valueJson: String): Int {
        val existingBytes = prefs.all.entries.sumOf { (entryKey, entryValue) ->
            if (entryKey == key) {
                0
            } else {
                entryKey.toByteArray(Charsets.UTF_8).size + entryValue.toString().toByteArray(Charsets.UTF_8).size
            }
        }
        return existingBytes + key.toByteArray(Charsets.UTF_8).size + valueJson.toByteArray(Charsets.UTF_8).size
    }

    private fun storagePreferencesName(storeName: String): String {
        val packageName = engineInfo.packageName.ifBlank { appContext.packageName }
        return NodeStorageBridgePolicy.preferencesName(packageName, engineInfo.cwd, storeName)
    }

    private fun dispatchDatabase(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (databaseDestroyed.get()) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.processDead("AutoJs6 database bridge provider is destroyed."),
                ),
            )
            return
        }
        try {
            databaseExecutor.execute {
                val response = runCatching {
                    if (databaseDestroyed.get()) {
                        throw DatabaseBridgeException(
                            NodeBridgeError.processDead("AutoJs6 database bridge provider is destroyed."),
                        )
                    }
                    val args = JSONArray(request.argsJson.ifBlank { "[]" })
                    when (request.method) {
                        "open" -> databaseOpen(request, args)
                        "exec" -> databaseExec(request, args)
                        "run" -> databaseRun(request, args)
                        "get" -> databaseGet(request, args)
                        "all" -> databaseAll(request, args)
                        "transaction" -> databaseTransaction(request, args)
                        "close" -> databaseClose(request, args)
                        else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
                    }
                }.getOrElse { error ->
                    NodeBridgeResponse.failure(request.id, databaseError(request, error))
                }
                callback(response)
            }
        } catch (error: RejectedExecutionException) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.processDead("AutoJs6 database bridge executor is not available."),
                ),
            )
        }
    }

    private fun databaseOpen(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        if (databaseHandles.size >= NodeDatabaseBridgePolicy.MAX_OPEN_HANDLES) {
            bridgeResourceLimitRejections.incrementAndGet()
            throw DatabaseBridgeException(
                NodeBridgeError.resourceLimit(
                    message = "AutoJs6 database bridge reached the open handle limit.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        val name = NodeDatabaseBridgePolicy.openName(request, args.opt(0))
        val databaseFile = NodeDatabaseBridgePolicy.databaseFile(appContext, engineInfo, name)
        val parent = databaseFile.parentFile
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw DatabaseBridgeException(
                NodeBridgeError.permissionDenied(
                    message = "AutoJs6 database bridge could not create the scoped database directory.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        database.setForeignKeyConstraintsEnabled(true)
        if (databaseDestroyed.get()) {
            runCatching { database.close() }
            throw DatabaseBridgeException(
                NodeBridgeError.processDead("AutoJs6 database bridge provider is destroyed."),
            )
        }
        val handleId = "database-${databaseHandleCounter.getAndIncrement()}-${UUID.randomUUID()}"
        val resource = resourceRegistry.register(
            type = NodeBridgeResourceType.DATABASE_HANDLE,
            id = handleId,
            metadata = mapOf(
                "name" to name,
                "path" to databaseFile.absolutePath,
            ),
        ) {
            databaseHandles.remove(handleId)
            val release = Runnable {
                runCatching { while (database.isOpen && database.inTransaction()) database.endTransaction() }
                runCatching { database.close() }
            }
            if (Thread.currentThread() === databaseThread) release.run()
            else try { databaseExecutor.execute(release) } catch (_: RejectedExecutionException) { release.run() }
        }
        databaseHandles[handleId] = ControlledDatabaseHandle(
            id = handleId,
            name = name,
            file = databaseFile,
            database = database,
            resource = resource,
        )
        if (databaseDestroyed.get()) {
            databaseHandles.remove(handleId)
            resource.close("database.open.after_destroy")
            throw DatabaseBridgeException(NodeBridgeError.processDead("AutoJs6 database bridge provider is destroyed."))
        }
        val result = JSONObject()
            .put("id", handleId)
            .put("name", name)
        return NodeBridgeResponse.success(request.id, result.toString())
    }

    private fun databaseExec(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val handle = databaseHandle(request, args)
        val sql = NodeDatabaseBridgePolicy.sql(request, args.optString(1, ""))
        val params = NodeDatabaseBridgePolicy.params(request, args.opt(2))
        handle.exec(request, sql, params)
        return NodeBridgeResponse.success(request.id, "null")
    }

    private fun databaseRun(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val handle = databaseHandle(request, args)
        val sql = NodeDatabaseBridgePolicy.sql(request, args.optString(1, ""))
        val params = NodeDatabaseBridgePolicy.params(request, args.opt(2))
        return NodeBridgeResponse.success(request.id, handle.run(request, sql, params).toString())
    }

    private fun databaseGet(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val handle = databaseHandle(request, args)
        val sql = NodeDatabaseBridgePolicy.sql(request, args.optString(1, ""))
        val params = NodeDatabaseBridgePolicy.params(request, args.opt(2))
        val row = handle.query(request, sql, params)
        return NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(row))
    }

    private fun databaseAll(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val handle = databaseHandle(request, args)
        val sql = NodeDatabaseBridgePolicy.sql(request, args.optString(1, ""))
        val params = NodeDatabaseBridgePolicy.params(request, args.opt(2))
        return NodeBridgeResponse.success(request.id, handle.queryAll(request, sql, params).toString())
    }

    private fun databaseTransaction(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val handle = databaseHandle(request, args)
        val operations = args.optJSONArray(1)
            ?: throw DatabaseBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 database.transaction requires an array of operations.",
                    module = request.module,
                    method = request.method,
                ),
            )
        if (operations.length() > NodeDatabaseBridgePolicy.MAX_TRANSACTION_OPERATIONS) {
            throw DatabaseBridgeException(
                NodeBridgeError.resourceLimit(
                    message = "AutoJs6 database.transaction exceeded ${NodeDatabaseBridgePolicy.MAX_TRANSACTION_OPERATIONS} operations.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        val results = JSONArray()
        handle.database.beginTransaction()
        try {
            for (index in 0 until operations.length()) {
                val operation = operations.optJSONObject(index)
                    ?: throw DatabaseBridgeException(
                        NodeBridgeError.invalidRequest(
                            message = "AutoJs6 database.transaction operation must be an object.",
                            module = request.module,
                            method = request.method,
                        ),
                    )
                val type = operation.optString("type", operation.optString("method", "")).trim().lowercase(Locale.US)
                val sql = NodeDatabaseBridgePolicy.sql(request, operation.optString("sql", ""))
                val params = NodeDatabaseBridgePolicy.params(request, operation.opt("params"))
                val result = when (type) {
                    "exec" -> {
                        handle.exec(request, sql, params)
                        JSONObject.NULL
                    }
                    "run" -> handle.run(request, sql, params)
                    "get" -> handle.query(request, sql, params) ?: JSONObject.NULL
                    "all" -> handle.queryAll(request, sql, params)
                    else -> throw DatabaseBridgeException(
                        NodeBridgeError.invalidRequest(
                            message = "AutoJs6 database.transaction supports only exec, run, get, and all operations.",
                            module = request.module,
                            method = request.method,
                        ),
                    )
                }
                results.put(result)
            }
            handle.database.setTransactionSuccessful()
        } finally {
            handle.database.endTransaction()
        }
        return NodeBridgeResponse.success(request.id, results.toString())
    }

    private fun databaseClose(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val handleId = NodeDatabaseBridgePolicy.handleId(request, args.opt(0))
        val closed = databaseHandles.containsKey(handleId) && resourceRegistry.close(handleId, "database.close")
        return NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(closed))
    }

    private fun databaseHandle(request: NodeBridgeRequest, args: JSONArray): ControlledDatabaseHandle {
        val handleId = NodeDatabaseBridgePolicy.handleId(request, args.opt(0))
        return databaseHandles[handleId]
            ?: throw DatabaseBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 database handle is not open.",
                    module = request.module,
                    method = request.method,
                ),
            )
    }

    private fun databaseError(request: NodeBridgeRequest, error: Throwable): NodeBridgeError {
        return when (error) {
            is DatabaseBridgeException -> error.bridgeError
            is SQLiteException -> NodeBridgeError.invalidRequest(
                message = "AutoJs6 database request failed: ${error.message ?: error.javaClass.name}",
                module = request.module,
                method = request.method,
            )
            else -> NodeBridgeError.invalidRequest(
                message = error.message ?: "AutoJs6 database request failed.",
                module = request.module,
                method = request.method,
            )
        }
    }

    private fun dispatchNotifications(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            synchronized(notificationHandles) { runCatching {
                check(!notificationsClosed.get()) { "The notification execution has ended." }
                val args = JSONArray(request.argsJson.ifBlank { "[]" })
                val rawScope = args.opt(if (request.method == "cancelAll") 0 else 1)
                require(rawScope == null || rawScope === JSONObject.NULL || rawScope is String && rawScope.length <= 80) { "Invalid notification scope." }
                val scope = rawScope as? String ?: ""
                val tag = "$notificationTag:$scope"
                when (request.method) {
                    "notify" -> {
                        val payload = args.optJSONObject(0)
                            ?: throw NotificationBridgeException(
                                NodeBridgeError.invalidRequest(
                                    message = "notifications.notify requires an options object.",
                                    module = request.module,
                                    method = request.method,
                                ),
                            )
                        val descriptor = ControlledNotificationDescriptor.fromJson(
                            request = request,
                            payload = payload,
                            fallbackId = notificationIdCounter.getAndIncrement(),
                            defaultChannelName = appContext.getString(R.string.app_name),
                        )
                        val key = scope to descriptor.id
                        ensureNotificationPostingAllowed(request)
                        if (!notificationHandles.containsKey(key) &&
                            notificationHandles.size >= ControlledNotificationPolicy.MAX_ACTIVE_NOTIFICATIONS
                        ) {
                            bridgeResourceLimitRejections.incrementAndGet()
                            throw NotificationBridgeException(
                                NodeBridgeError.resourceLimit(
                                    message = "AutoJs6 notifications bridge reached the active notification limit.",
                                    module = request.module,
                                    method = request.method,
                                ),
                            )
                        }
                        ensureNotificationChannel(descriptor)
                        try {
                            NotificationManagerCompat.from(appContext).notify(tag, descriptor.id, descriptor.toNotification(appContext))
                        } catch (error: SecurityException) {
                            // Preserve the bridge's permission-denied response if permission is revoked after the check.
                            throw NotificationBridgeException(
                                NodeBridgeError.permissionDenied(
                                    message = "AutoJs6 notifications bridge could not post notification: ${error.message ?: error.javaClass.name}",
                                    module = request.module,
                                    method = request.method,
                                ),
                            )
                        }
                        notificationHandles.remove(key)?.release("notification.update")
                        val handle = resourceRegistry.register(
                            type = NodeBridgeResourceType.NOTIFICATION,
                            id = "notification-$scope-${descriptor.id}",
                            metadata = mapOf(
                                "channel" to descriptor.channel,
                                "ongoing" to descriptor.ongoing.toString(),
                            ),
                        ) {
                            notificationHandles.remove(key)
                            NotificationManagerCompat.from(appContext).cancel(tag, descriptor.id)
                        }
                        notificationHandles[key] = handle
                        NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(descriptor.id))
                    }
                    "cancel" -> {
                        val id = ControlledNotificationDescriptor.parseNotificationIdArg(request, args, 0)
                        notificationHandles.remove(scope to id)?.close("notification.cancel")
                        NodeBridgeResponse.success(request.id, "null")
                    }
                    "cancelAll" -> {
                        notificationHandles.filterKeys { it.first == scope }.values.toList().forEach { handle ->
                            handle.close("notification.cancelAll")
                        }
                        NodeBridgeResponse.success(request.id, "null")
                    }
                    "getPermissionStatus" -> {
                        NodeBridgeResponse.success(request.id, notificationPermissionStatus().toString())
                    }
                    "openSettings" -> {
                        val options = args.optJSONObject(0) ?: JSONObject()
                        NodeBridgeResponse.success(
                            request.id,
                            openNotificationSettings(dryRun = options.optBoolean("dryRun", false)).toString(),
                        )
                    }
                    else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
                }
            }.getOrElse { error ->
                val bridgeError = when (error) {
                    is NotificationBridgeException -> error.bridgeError
                    is SecurityException -> NodeBridgeError.permissionDenied(
                        message = "AutoJs6 notifications bridge could not post notification: ${error.message ?: error.javaClass.name}",
                        module = request.module,
                        method = request.method,
                    )
                    else -> NodeBridgeError.invalidRequest(
                        message = error.message ?: "AutoJs6 notifications request failed.",
                        module = request.module,
                        method = request.method,
                    )
                }
                NodeBridgeResponse.failure(request.id, bridgeError)
            } }
        }
    }

    private fun ensureNotificationPostingAllowed(request: NodeBridgeRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            throw NotificationBridgeException(
                NodeBridgeError.permissionDenied(
                    message = "AutoJs6 notifications bridge requires android.permission.POST_NOTIFICATIONS.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            throw NotificationBridgeException(
                NodeBridgeError.permissionDenied(
                    message = "AutoJs6 notifications are disabled for this app.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
    }

    private fun ensureNotificationChannel(descriptor: ControlledNotificationDescriptor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(descriptor.channel) != null) {
            return
        }
        val channel = NotificationChannel(
            descriptor.channel,
            descriptor.channelName,
            ControlledNotificationPolicy.importanceForPriority(descriptor.priority),
        ).apply {
            description = "AutoJs6 Node notifications"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationPermissionStatus(): JSONObject {
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val runtimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val runtimePermissionGranted = !runtimePermissionRequired ||
                appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val intent = notificationSettingsIntent()
        return JSONObject()
            .put("schema", "autojs6-node-notification-permission-status-v1")
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("notificationsEnabled", notificationsEnabled)
            .put("postNotificationsPermissionRequired", runtimePermissionRequired)
            .put("postNotificationsPermissionGranted", runtimePermissionGranted)
            .put("canOpenSettings", intentAvailable(intent))
    }

    private fun openNotificationSettings(dryRun: Boolean): JSONObject {
        val intent = notificationSettingsIntent()
        val canOpenSettings = intentAvailable(intent)
        var opened = false
        var reason = if (canOpenSettings) "settings-intent-ready" else "settings-intent-unavailable"
        if (canOpenSettings && dryRun) {
            reason = "settings-intent-dry-run"
        } else if (canOpenSettings) {
            runCatching {
                appContext.startActivity(intent)
                opened = true
                reason = "settings-intent-opened"
            }.getOrElse { error ->
                reason = "settings-intent-failed:${error.javaClass.simpleName}"
            }
        }
        return notificationPermissionStatus()
            .put("opened", opened)
            .put("dryRun", dryRun)
            .put("action", intent.action.orEmpty())
            .put("reason", reason)
    }

    private fun notificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${appContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun dispatchSensors(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        when (request.method) {
            "getAvailableSensors" -> dispatchSensorsAvailable(request, callback)
            "once" -> dispatchSensorOnce(request, callback)
            "subscribe" -> dispatchSensorSubscribe(request, callback)
            "unsubscribe" -> dispatchSensorUnsubscribe(request, callback)
            "drainEvents" -> dispatchSensorDrainEvents(request, callback)
            else -> callback(unavailable(request))
        }
    }

    private fun dispatchSensorsAvailable(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val manager = sensorManager
        if (manager == null) {
            callback(NodeBridgeResponse.success(request.id, JSONArray().toString()))
            return
        }
        val sensors = JSONArray()
        ControlledSensorPolicy.allowedSensorTypes.forEach { (wireName, sensorType) ->
            manager.getDefaultSensor(sensorType)?.let { sensor ->
                sensors.put(ControlledSensorPolicy.sensorInfoJson(wireName, sensor))
            }
        }
        callback(NodeBridgeResponse.success(request.id, sensors.toString()))
    }

    private fun dispatchSensorOnce(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val descriptor = ControlledSensorRequestDescriptor.fromRequest(request)
        if (descriptor.error != null || descriptor.value == null) {
            callback(NodeBridgeResponse.failure(request.id, descriptor.error ?: NodeBridgeError.processDead()))
            return
        }
        mainHandler.post { synchronized(sensorLock) {
            if (sensorsDestroyed) {
                callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("Sensors provider is destroyed.")))
                return@post
            }
            val manager = sensorManager
            val sensor = manager?.getDefaultSensor(descriptor.value.sensorType)
            if (manager == null || sensor == null) {
                callback(NodeBridgeResponse.failure(request.id, sensorUnavailableError(request, descriptor.value.type)))
                return@post
            }
            val listenerRef = arrayOfNulls<SensorEventListener>(1)
            val resourceId = "sensor-once-${UUID.randomUUID()}"
            val responded = AtomicBoolean(false)
            val timeoutRunnable = Runnable {
                if (responded.compareAndSet(false, true)) {
                    resourceRegistry.close(resourceId, "sensor.once.timeout")
                    callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.timeout(request.module, request.method)))
                }
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (responded.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        resourceRegistry.close(resourceId, "sensor.once.event")
                        callback(
                            NodeBridgeResponse.success(
                                request.id,
                                ControlledSensorPolicy.sensorEventJson(descriptor.value.type, event).toString(),
                            ),
                        )
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            listenerRef[0] = listener
            resourceRegistry.register(
                type = NodeBridgeResourceType.SENSOR_SUBSCRIPTION,
                id = resourceId,
                metadata = mapOf("type" to descriptor.value.type, "mode" to "once"),
            ) {
                listenerRef[0]?.let { manager.unregisterListener(it) }
                mainHandler.removeCallbacks(timeoutRunnable)
            }
            if (!manager.registerListener(listener, sensor, descriptor.value.samplingPeriodUs)) {
                resourceRegistry.close(resourceId, "sensor.once.register_failed")
                callback(NodeBridgeResponse.failure(request.id, sensorUnavailableError(request, descriptor.value.type)))
                return@post
            }
            mainHandler.postDelayed(timeoutRunnable, descriptor.value.timeoutMs)
        } }
    }

    private fun dispatchSensorSubscribe(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val descriptor = ControlledSensorRequestDescriptor.fromRequest(request)
        if (descriptor.error != null || descriptor.value == null) {
            callback(NodeBridgeResponse.failure(request.id, descriptor.error ?: NodeBridgeError.processDead()))
            return
        }
        mainHandler.post { synchronized(sensorLock) {
            if (sensorsDestroyed) {
                callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead("Sensors provider is destroyed.")))
                return@post
            }
            val manager = sensorManager
            val sensor = manager?.getDefaultSensor(descriptor.value.sensorType)
            if (manager == null || sensor == null) {
                callback(NodeBridgeResponse.failure(request.id, sensorUnavailableError(request, descriptor.value.type)))
                return@post
            }
            if (sensorSubscriptions.size >= ControlledSensorPolicy.MAX_SUBSCRIPTIONS) {
                bridgeResourceLimitRejections.incrementAndGet()
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.resourceLimit(
                            message = "AutoJs6 sensors bridge reached the active subscription limit.",
                            module = request.module,
                            method = request.method,
                        ),
                    ),
                )
                return@post
            }
            val subscriptionId = "sensor-${UUID.randomUUID()}"
            val eventStream = NodeBridgeEventStream.forRequest(request, subscriptionId, callback)
            val subscription = ControlledSensorSubscription(
                id = subscriptionId,
                type = descriptor.value.type,
                maxQueueSize = descriptor.value.maxQueueSize,
                eventStream = eventStream,
            )
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    subscription.queueEvent(ControlledSensorPolicy.sensorEventJson(descriptor.value.type, event))
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    subscription.queueAccuracy(accuracy)
                }
            }
            subscription.listener = listener
            sensorSubscriptions[subscriptionId] = subscription
            resourceRegistry.register(
                type = NodeBridgeResourceType.SENSOR_SUBSCRIPTION,
                id = subscriptionId,
                metadata = mapOf(
                    "type" to descriptor.value.type,
                    "samplingIntervalMs" to descriptor.value.samplingIntervalMs.toString(),
                ),
            ) {
                sensorSubscriptions.remove(subscriptionId)
                subscription.listener?.let { manager.unregisterListener(it) }
                subscription.queueClose()
            }
            if (!manager.registerListener(listener, sensor, descriptor.value.samplingPeriodUs)) {
                resourceRegistry.close(subscriptionId, "sensor.subscribe.register_failed")
                callback(NodeBridgeResponse.failure(request.id, sensorUnavailableError(request, descriptor.value.type)))
                return@post
            }
            val result = subscription.toJson(descriptor.value)
            eventStream?.acknowledge(result) ?: callback(NodeBridgeResponse.success(request.id, result.toString()))
        } }
    }

    private fun dispatchSensorUnsubscribe(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val payload = request.firstObjectArg()
        val subscriptionId = payload?.optString("id").orEmpty()
        if (subscriptionId.isBlank()) {
            callback(NodeBridgeResponse.failure(request.id, sensorInvalidRequest(request, "sensors.unsubscribe requires a subscription id.")))
            return
        }
        synchronized(sensorLock) {
            if (sensorSubscriptions.containsKey(subscriptionId)) resourceRegistry.close(subscriptionId, "sensor.unsubscribe")
        }
        callback(NodeBridgeResponse.success(request.id, "null"))
    }

    private fun dispatchSensorDrainEvents(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val payload = request.firstObjectArg()
        val subscriptionId = payload?.optString("id").orEmpty()
        val subscription = sensorSubscriptions[subscriptionId]
        if (payload == null || subscription == null) {
            callback(NodeBridgeResponse.success(request.id, JSONArray().toString()))
            return
        }
        callback(NodeBridgeResponse.success(request.id, subscription.drainEvents().toString()))
    }

    private fun sensorUnavailableError(request: NodeBridgeRequest, type: String): NodeBridgeError {
        return NodeBridgeError.permissionDenied(
            message = "AutoJs6 sensors bridge cannot access sensor type: $type.",
            module = request.module,
            method = request.method,
        )
    }

    private fun sensorInvalidRequest(request: NodeBridgeRequest, message: String): NodeBridgeError {
        return NodeBridgeError.invalidRequest(
            message = message,
            module = request.module,
            method = request.method,
        )
    }

    private fun dispatchInputObserver(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        when (request.method) {
            "getAvailableSources" -> dispatchInputObserverAvailableSources(request, callback)
            "observeKeys" -> dispatchInputObserverObserveKeys(request, callback)
            "drainEvents" -> dispatchInputObserverDrainEvents(request, callback)
            "close" -> dispatchInputObserverClose(request, callback)
            else -> callback(unavailable(request))
        }
    }

    private fun dispatchInputObserverAvailableSources(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val livePolicyError = inputObserverLivePolicyError(request)
        val accessibilityAvailable = livePolicyError == null && accessibilityBridgeOperational()
        callback(
            NodeBridgeResponse.success(
                request.id,
                JSONArray()
                    .put(ControlledInputObserverPolicy.sourceJson("fake", available = true, live = false))
                    .put(
                        ControlledInputObserverPolicy.sourceJson(
                            "accessibility",
                            available = accessibilityAvailable,
                            live = true,
                            reason = when {
                                accessibilityAvailable -> ""
                                livePolicyError != null -> livePolicyError.message
                                else -> "accessibility service is not operational"
                            },
                        ),
                    )
                    .toString(),
            ),
        )
    }

    private fun dispatchInputObserverObserveKeys(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val parsed = ControlledInputObserverDescriptor.fromRequest(request)
        if (parsed.error != null || parsed.value == null) {
            callback(NodeBridgeResponse.failure(request.id, parsed.error ?: NodeBridgeError.processDead()))
            return
        }
        val descriptor = parsed.value
        if (descriptor.source !in ControlledInputObserverPolicy.allowedSources) {
            callback(NodeBridgeResponse.failure(request.id, inputObserverInvalidSourceError(request, descriptor.source)))
            return
        }
        if (inputObserverSubscriptions.size >= ControlledInputObserverPolicy.MAX_ACTIVE_SUBSCRIPTIONS) {
            bridgeResourceLimitRejections.incrementAndGet()
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.resourceLimit(
                        message = "AutoJs6 input_observer bridge reached the active subscription limit.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        if (descriptor.source == "accessibility") {
            val livePolicyError = inputObserverLivePolicyError(request)
            if (livePolicyError != null) {
                callback(NodeBridgeResponse.failure(request.id, livePolicyError))
                return
            }
        }
        val subscriptionId = "input-observer-${UUID.randomUUID()}"
        val eventStream = NodeBridgeEventStream.forRequest(request, subscriptionId, callback)
        val subscription = ControlledInputObserverSubscription(
            id = subscriptionId,
            source = descriptor.source,
            keys = descriptor.keys,
            actions = descriptor.actions,
            maxQueueSize = descriptor.maxQueueSize,
            eventStream = eventStream,
        )
        inputObserverSubscriptions[subscriptionId] = subscription
        resourceRegistry.register(
            type = NodeBridgeResourceType.INPUT_OBSERVER_SUBSCRIPTION,
            id = subscriptionId,
            metadata = mapOf(
                "source" to descriptor.source,
                "keys" to descriptor.keys.joinToString(","),
                "actions" to descriptor.actions.joinToString(","),
            ),
        ) {
            inputObserverSubscriptions.remove(subscriptionId)?.close()
        }
        if (descriptor.source == "fake") {
            descriptor.keys.forEach { key ->
                descriptor.actions.forEach { action ->
                    subscription.queueEvent(
                        key = key,
                        keyCode = ControlledInputObserverPolicy.keyCodeFor(key),
                        action = action,
                    )
                }
            }
        } else if (!attachAccessibilityInputObserver(subscriptionId, subscription)) {
            resourceRegistry.close(subscriptionId, "input_observer.accessibility_unavailable")
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.capabilityProviderMissing(
                        module = request.module,
                        method = request.method,
                        requirement = "an operational AutoJs6 accessibility service with key observation enabled",
                    ),
                ),
            )
            return
        }
        val result = subscription.toJson()
        eventStream?.acknowledge(result) ?: callback(NodeBridgeResponse.success(request.id, result.toString()))
    }

    private fun attachAccessibilityInputObserver(
        subscriptionId: String,
        subscription: ControlledInputObserverSubscription,
    ): Boolean = runCatching {
        val detach = NodeEventObservers.observeKeys(
            { keyCode, event -> subscription.queueKeyEvent(keyCode, event) },
            { resourceRegistry.close(subscriptionId, "input_observer.accessibility_service_destroy") },
        )
        subscription.addCloseAction { detach(); subscription.markLiveListenerDetached() }
        subscription.markLiveListenerAttached()
        true
    }.getOrDefault(false)

    private fun inputObserverInvalidSourceError(request: NodeBridgeRequest, source: String): NodeBridgeError {
        return NodeBridgeError.invalidRequest(
            message = "input_observer.${request.method} source is not supported: $source.",
            module = request.module,
            method = request.method,
        )
    }

    private fun inputObserverLivePolicyError(request: NodeBridgeRequest): NodeBridgeError? =
        if (accessibilityBridgeOperational()) null else NodeBridgeError.permissionDenied(
            "Enable AutoJs6 accessibility to observe hardware keys.", request.module, request.method,
        )

    private fun dispatchInputObserverDrainEvents(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = request.argsArray()
        val subscriptionId = args.optString(0, "")
        val maxEvents = (args.optJSONObject(1)?.optInt("maxEvents", ControlledInputObserverPolicy.DEFAULT_DRAIN_BATCH_SIZE)
            ?: ControlledInputObserverPolicy.DEFAULT_DRAIN_BATCH_SIZE)
            .coerceIn(1, ControlledInputObserverPolicy.MAX_DRAIN_BATCH_SIZE)
        val subscription = inputObserverSubscriptions[subscriptionId]
        callback(NodeBridgeResponse.success(request.id, (subscription?.drainEvents(maxEvents) ?: JSONArray()).toString()))
    }

    private fun dispatchInputObserverClose(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val subscriptionId = request.argsArray().optString(0, "")
        val closed = subscriptionId.isNotBlank() && resourceRegistry.close(subscriptionId, "input_observer.close")
        callback(NodeBridgeResponse.success(request.id, closed.toString()))
    }

    private fun dispatchClipboard(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@dispatchOnMain NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.capabilityProviderMissing(
                        module = request.module,
                        method = request.method,
                        requirement = "an Android ClipboardManager",
                    ),
                )
            when (request.method) {
                "getText" -> {
                    val text = runCatching { readClipboardText(clipboard) }.getOrElse { error ->
                        return@dispatchOnMain clipboardAccessFailure(request, error)
                    }
                    NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(text))
                }
                "setText" -> {
                    val text = request.firstStringArg()
                    runCatching {
                        clipboard.setPrimaryClip(ClipData.newPlainText("AutoJs6 Node", text))
                    }.getOrElse { error ->
                        return@dispatchOnMain clipboardAccessFailure(request, error)
                    }
                    NodeBridgeResponse.success(request.id, "null")
                }
                "hasText" -> {
                    val hasText = runCatching { readClipboardText(clipboard).isNotEmpty() }.getOrElse { error ->
                        return@dispatchOnMain clipboardAccessFailure(request, error)
                    }
                    NodeBridgeResponse.success(request.id, hasText.toString())
                }
                else -> unavailable(request)
            }
        }
    }

    private fun dispatchEvents(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val deadline = android.os.SystemClock.elapsedRealtime() + request.timeoutMs.coerceIn(1L, 4000L)
        var rebindRequested = false
        lateinit var deliver: Runnable
        deliver = Runnable {
            if (request.method == "observeNotification" && !eventObservers.isDestroyed() &&
                org.autojs.autojs.core.notification.NotificationListenerService.isNotificationListenerEnabled(appContext) &&
                org.autojs.autojs.core.notification.NotificationListenerService.instance?.connected != true) {
                if (!rebindRequested) {
                    rebindRequested = true
                    org.autojs.autojs.core.notification.NotificationListenerService.requestRebindIfPossible(appContext)
                }
                if (android.os.SystemClock.elapsedRealtime() < deadline) {
                    mainHandler.postDelayed(deliver, 50L)
                    return@Runnable
                }
            }
            runCatching {
                when (request.method) {
                    "close" -> callback(NodeBridgeResponse.success(request.id, eventObservers.close(request.argsArray().optString(0), "closed").toString()))
                    "drainEvents" -> callback(NodeBridgeResponse.success(request.id, eventObservers.drain(request.argsArray().optString(0)).toString()))
                    else -> {
                        val kind = when (request.method) {
                            "observeNotification" -> "notification"
                            "observeToast" -> "toast"
                            "observeKey" -> "key"
                            else -> "broadcast"
                        }
                        val stream = NodeBridgeEventStream.forRequest(request, "events-${UUID.randomUUID()}", callback)
                        val session = eventObservers.observe(kind, stream)
                        val result = session.toJson()
                        stream?.acknowledge(result) ?: callback(NodeBridgeResponse.success(request.id, result.toString()))
                    }
                }
            }.onFailure { error ->
                callback(NodeBridgeResponse.failure(request.id, when (error) {
                    is SecurityException -> NodeBridgeError.permissionDenied(error.message ?: "Event observation permission denied.", request.module, request.method)
                    is IllegalArgumentException -> NodeBridgeError.invalidRequest(error.message ?: "Invalid event subscription.", request.module, request.method)
                    else -> NodeBridgeError.capabilityProviderMissing(request.module, request.method, error.message ?: "a connected Android event source")
                }))
            }
        }
        mainHandler.post(deliver)
    }

    private fun dispatchDevice(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            when (request.method) {
                "setBrightness", "setBrightnessMode", "keepScreenOn", "cancelKeepingAwake", "closeScope" -> runCatching {
                    val value = request.argsArray().opt(0)
                    if (request.method !in setOf("cancelKeepingAwake", "closeScope")) require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble()) {
                        "device.${request.method} requires an integer."
                    }
                    when (request.method) {
                        "setBrightness" -> { require((value as Number).toLong() in 0..255); deviceControls.setBrightness(value.toInt()) }
                        "setBrightnessMode" -> { require((value as Number).toLong() in 0..1); deviceControls.setBrightnessMode(value.toInt()) }
                        "keepScreenOn" -> deviceControls.keepScreenOn((value as Number).toLong(), request.argsArray().optString(1))
                        "closeScope" -> deviceControls.closeScope(request.argsArray().optString(0))
                        else -> deviceControls.cancelKeepingAwake(request.argsArray().optString(0))
                    }
                    NodeBridgeResponse.success(request.id, "null")
                }.getOrElse { error ->
                    NodeBridgeResponse.failure(request.id, when (error) {
                        is IllegalArgumentException -> NodeBridgeError.invalidRequest(error.message ?: "Invalid device argument.", request.module, request.method)
                        is SecurityException -> NodeBridgeError.permissionDenied(error.message ?: "Device permission denied.", request.module, request.method)
                        else -> NodeBridgeError.providerFailed(error.message ?: error.javaClass.name, request.module, request.method)
                    })
                }
                "info" -> runCatching {
                    NodeBridgeResponse.success(request.id, NodeBridgeDeviceInfo.json(appContext).toString())
                }.getOrElse { error ->
                    deviceAccessFailure(request, error)
                }
                "isScreenOn" -> runCatching {
                    NodeBridgeResponse.success(request.id, isScreenOn().toString())
                }.getOrElse { error ->
                    deviceAccessFailure(request, error)
                }
                "wakeUp" -> runCatching {
                    wakeUpDevice()
                    NodeBridgeResponse.success(request.id, "null")
                }.getOrElse { error ->
                    deviceAccessFailure(request, error)
                }
                "vibrate" -> runCatching {
                    vibrateDevice(request.firstLongArg())
                    NodeBridgeResponse.success(request.id, "null")
                }.getOrElse { error ->
                    deviceAccessFailure(request, error)
                }
                "isIgnoringBatteryOptimizations" -> runCatching {
                    NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(isIgnoringBatteryOptimizations()))
                }.getOrElse { error ->
                    deviceAccessFailure(request, error)
                }
                "openBatteryOptimizationSettings" -> runCatching {
                    val args = request.argsArray()
                    val options = args.optJSONObject(0) ?: JSONObject()
                    NodeBridgeResponse.success(
                        request.id,
                        openBatteryOptimizationSettings(dryRun = options.optBoolean("dryRun", false)).toString(),
                    )
                }.getOrElse { error ->
                    deviceAccessFailure(request, error)
                }
                else -> unavailable(request)
            }
        }
    }

    private fun dispatchShell(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val bridge=synchronized(shellScopeLock){
            if(shellScopeClosed){callback(NodeBridgeResponse.failure(request.id,NodeBridgeError.processDead("Shell execution is closed")));return}
            shellBridge.value
        }
        bridge.dispatch(request,callback)
    }

    private fun tryAcquireShellCommand(): Boolean {
        while (true) {
            val current = activeShellCommands.get()
            if (current >= bridgeLimits.maxShellCommands) {
                return false
            }
            if (activeShellCommands.compareAndSet(current, current + 1)) {
                recordMaxActiveShellCommands(current + 1)
                return true
            }
        }
    }

    private fun recordMaxActiveShellCommands(count: Int) {
        while (true) {
            val current = maxActiveShellCommands.get()
            if (count <= current) {
                return
            }
            if (maxActiveShellCommands.compareAndSet(current, count)) {
                return
            }
        }
    }

    private fun dispatchWorkManager(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val response = when (request.method) {
            "scheduleOnce" -> scheduleWorkManagerTask(request, ControlledWorkTaskKind.ONCE)
            "schedulePeriodic" -> scheduleWorkManagerTask(request, ControlledWorkTaskKind.PERIODIC)
            "cancel" -> cancelWorkManagerTask(request)
            "list" -> listWorkManagerTasks(request)
            else -> unavailable(request)
        }
        callback(response)
    }

    private fun scheduleWorkManagerTask(request: NodeBridgeRequest, kind: ControlledWorkTaskKind): NodeBridgeResponse {
        val payload = request.firstObjectArg()
            ?: return workManagerPolicyFailure(request, "work_manager.${request.method} requires a JSON task descriptor.")
        val ownerKey = NodeWorkManagerBridgeScheduler.ownerKey(engineInfo)
        val activeTasks = NodeWorkManagerBridgeScheduler.list(appContext, ownerKey)
        if (activeTasks.size >= ControlledWorkManagerPolicy.MAX_TASKS_PER_PROVIDER) {
            return workManagerPolicyFailure(
                request,
                "AutoJs6 work_manager exceeded ${ControlledWorkManagerPolicy.MAX_TASKS_PER_PROVIDER} registered tasks for this provider.",
            )
        }
        val parsed = ControlledWorkTaskDescriptor.fromJson(request, kind, payload, appContext, engineInfo)
        if (parsed.error != null || parsed.value == null) {
            return NodeBridgeResponse.failure(request.id, parsed.error ?: NodeBridgeError.processDead())
        }
        val id = "node-work-${UUID.randomUUID()}"
        val scheduled = NodeWorkManagerBridgeScheduler.schedule(appContext, parsed.value.toJson(id, engineInfo))
        if (scheduled.errorMessage != null || scheduled.task == null) {
            return workManagerPolicyFailure(request, scheduled.errorMessage ?: "work_manager provider failed to schedule task.")
        }
        val task = scheduled.task
        workManagerTasks[id] = task
        resourceRegistry.register(
            type = NodeBridgeResourceType.WORK_MANAGER_TASK,
            id = id,
            metadata = mapOf(
                "kind" to kind.name.lowercase(),
                "scheduler" to task.optString("scheduler", "metadata"),
                "backend" to task.optString("backend", "embedded"),
                "entryKind" to task.optString("entryKind", "scriptPath"),
            ),
        ) {
            workManagerTasks.remove(id)
        }
        return NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(id))
    }

    private fun cancelWorkManagerTask(request: NodeBridgeRequest): NodeBridgeResponse {
        val id = request.firstStringArg().trim()
        if (id.isBlank()) {
            return workManagerPolicyFailure(request, "work_manager.cancel requires a non-empty task id.")
        }
        val cancelled = NodeWorkManagerBridgeScheduler.cancel(
            appContext,
            id,
            ownerKey = NodeWorkManagerBridgeScheduler.ownerKey(engineInfo),
        )
        if (cancelled.errorMessage != null) {
            return workManagerPolicyFailure(request, cancelled.errorMessage)
        }
        if (!resourceRegistry.close(id, "work_manager.cancel")) {
            workManagerTasks.remove(id)
        }
        return NodeBridgeResponse.success(request.id, "null")
    }

    private fun listWorkManagerTasks(request: NodeBridgeRequest): NodeBridgeResponse {
        return NodeBridgeResponse.success(
            request.id,
            SettingsJson.stringify(
                NodeWorkManagerBridgeScheduler.list(
                    appContext,
                    ownerKey = NodeWorkManagerBridgeScheduler.ownerKey(engineInfo),
                ),
            ),
        )
    }

    private fun workManagerPolicyFailure(request: NodeBridgeRequest, message: String): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = message,
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun dispatchPackageManager(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val response = runCatching {
            when (request.method) {
                "listApps" -> NodeBridgeResponse.success(request.id, NodeBridgeDeviceActions.listApps(appContext, request.firstObjectArg()).toString())
                "list" -> NodeBridgeResponse.success(
                    request.id,
                    NodeAndroidPackageInstallManager.recordsToJson(packageInstallManager.list()).toString(),
                )
                "verify" -> {
                    val payload = request.firstObjectArg()
                        ?: return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.verify requires a JSON request with name and version.",
                        )
                    val name = payload.optString("name").trim()
                    val version = payload.optString("version").trim()
                    if (name.isBlank() || version.isBlank()) {
                        return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.verify requires non-empty name and version.",
                        )
                    }
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.verify(name, version).toJson().toString(),
                    )
                }
                "prune" -> {
                    val payload = request.firstObjectArg()
                    val maxTransactionAgeMs = packageManagerMaxTransactionAgeMs(payload)
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.prune(maxTransactionAgeMs).toJson().toString(),
                    )
                }
                "planInstall" -> {
                    val payload = request.firstObjectArg()
                        ?: return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.planInstall requires a JSON request with name, version, and sourceDir.",
                        )
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.planInstall(packageManagerPackageRequest(payload, "planInstall")).toJson().toString(),
                    )
                }
                "planUpdate" -> {
                    val payload = request.firstObjectArg()
                        ?: return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.planUpdate requires a JSON request with name, version, and sourceDir.",
                        )
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.planUpdate(packageManagerPackageRequest(payload, "planUpdate")).toJson().toString(),
                    )
                }
                "planRemove" -> {
                    val payload = request.firstObjectArg()
                        ?: return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.planRemove requires a JSON request with name and version.",
                        )
                    val nameVersion = packageManagerNameVersion(payload, "planRemove")
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.planRemove(nameVersion.first, nameVersion.second).toJson().toString(),
                    )
                }
                "install" -> {
                    val payload = request.firstObjectArg()
                        ?: return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.install requires a JSON request with name, version, sourceDir, expectedIntegrity, and allowMutation.",
                        )
                    packageManagerRequireMutationConfirmation(payload, "install")
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.install(
                            packageManagerPackageRequest(payload, "install", requireExpectedIntegrity = true),
                        ).toJson().toString(),
                    )
                }
                "update" -> {
                    val payload = request.firstObjectArg()
                        ?: return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.update requires a JSON request with name, version, sourceDir, expectedIntegrity, and allowMutation.",
                        )
                    packageManagerRequireMutationConfirmation(payload, "update")
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.update(
                            packageManagerPackageRequest(payload, "update", requireExpectedIntegrity = true),
                        ).toJson().toString(),
                    )
                }
                "remove" -> {
                    val payload = request.firstObjectArg()
                        ?: return@runCatching packageManagerInvalidRequest(
                            request,
                            "package_manager.remove requires a JSON request with name, version, expectedIntegrity, and allowMutation.",
                        )
                    packageManagerRequireMutationConfirmation(payload, "remove")
                    val nameVersion = packageManagerNameVersion(payload, "remove")
                    val expectedIntegrity = packageManagerExpectedIntegrity(payload, "remove", requireExpectedIntegrity = true)
                    NodeBridgeResponse.success(
                        request.id,
                        packageInstallManager.remove(
                            nameVersion.first,
                            nameVersion.second,
                            expectedIntegrity = expectedIntegrity,
                        ).toJson().toString(),
                    )
                }
                else -> unavailable(request)
            }
        }.getOrElse { error ->
            NodeBridgeResponse.failure(request.id, packageManagerError(request, error))
        }
        callback(response)
    }

    private fun packageManagerPackageRequest(
        payload: JSONObject,
        methodName: String,
        requireExpectedIntegrity: Boolean = false,
    ): NodeAndroidPackageInstallManager.PackageRequest {
        val (name, version) = packageManagerNameVersion(payload, methodName)
        val sourceDirText = payload.optString("sourceDir").trim()
        if (sourceDirText.isBlank()) {
            throw IllegalArgumentException("package_manager.$methodName requires a non-empty relative sourceDir.")
        }
        val sourceDir = resolveScopedProjectFile(sourceDirText)
            ?: throw IllegalArgumentException("package_manager.$methodName sourceDir must be a relative path inside the current workingDirectory.")
        val expectedIntegrity = packageManagerExpectedIntegrity(payload, methodName, requireExpectedIntegrity)
        val kind = payload.optString("kind", NodeAndroidPackageInstallManager.KIND_DEPENDENCY)
            .trim()
            .takeIf { it.isNotBlank() }
            ?: NodeAndroidPackageInstallManager.KIND_DEPENDENCY
        return NodeAndroidPackageInstallManager.PackageRequest(
            name = name,
            version = version,
            sourceDir = sourceDir,
            expectedIntegrity = expectedIntegrity,
            kind = kind,
        )
    }

    private fun packageManagerRequireMutationConfirmation(payload: JSONObject, methodName: String) {
        if (payload.opt("allowMutation") != true) {
            throw NodeAndroidPackageInstallManager.PackageInstallException(
                NodeAndroidPackageInstallManager.ERROR_MUTATION_NOT_CONFIRMED,
                "package_manager.$methodName requires allowMutation: true.",
            )
        }
    }

    private fun packageManagerExpectedIntegrity(
        payload: JSONObject,
        methodName: String,
        requireExpectedIntegrity: Boolean,
    ): String? {
        val expectedIntegrity = payload.optString("expectedIntegrity").trim().takeIf { it.isNotBlank() }
        if (requireExpectedIntegrity && expectedIntegrity == null) {
            throw IllegalArgumentException("package_manager.$methodName requires expectedIntegrity from a dry-run plan.")
        }
        return expectedIntegrity
    }

    private fun packageManagerNameVersion(payload: JSONObject, methodName: String): Pair<String, String> {
        val name = payload.optString("name").trim()
        val version = payload.optString("version").trim()
        if (name.isBlank() || version.isBlank()) {
            throw IllegalArgumentException("package_manager.$methodName requires non-empty name and version.")
        }
        return name to version
    }

    private fun packageManagerMaxTransactionAgeMs(payload: JSONObject?): Long {
        if (payload == null || !payload.has("maxTransactionAgeMs") || payload.isNull("maxTransactionAgeMs")) {
            return 0L
        }
        val value: Any? = payload.opt("maxTransactionAgeMs")
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }?.coerceAtLeast(0L) ?: throw IllegalArgumentException("package_manager.prune maxTransactionAgeMs must be a finite non-negative integer.")
    }

    private fun packageManagerInvalidRequest(request: NodeBridgeRequest, message: String): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.invalidRequest(
                message = message,
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun packageManagerError(request: NodeBridgeRequest, error: Throwable): NodeBridgeError {
        return when (error) {
            is NodeAndroidPackageInstallManager.PackageInstallException -> NodeBridgeError.providerFailed(
                message = error.message,
                module = request.module,
                method = request.method,
                code = error.code,
            )
            is IllegalArgumentException -> NodeBridgeError.invalidRequest(
                message = error.message ?: "package_manager request is malformed.",
                module = request.module,
                method = request.method,
            )
            else -> NodeBridgeError.providerFailed(
                message = error.message ?: "Android package manager operation failed.",
                module = request.module,
                method = request.method,
            )
        }
    }

    private fun dispatchLifecycle(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val response = runCatching {
            when (request.method) {
                NodeLifecycleCheckpointStore.METHOD_CHECKPOINT -> {
                    val payload = request.firstObjectArg()
                        ?: throw NodeLifecycleCheckpointException(
                            NodeBridgeError.invalidRequest(
                                message = "AutoJs6 lifecycle.checkpoint requires a JSON descriptor.",
                                module = request.module,
                                method = request.method,
                            ),
                        )
                    val checkpoint = if (payload.has("checkpoint")) payload.opt("checkpoint") else JSONObject.NULL
                    val record = NodeLifecycleCheckpointStore.writeCheckpoint(
                        context = appContext,
                        engineInfo = engineInfo,
                        reason = payload.optString("reason", "manual"),
                        checkpoint = checkpoint,
                    )
                    NodeBridgeResponse.success(request.id, SettingsJson.stringify(record))
                }
                NodeLifecycleCheckpointStore.METHOD_READ_CHECKPOINT -> {
                    val record = NodeLifecycleCheckpointStore.readCheckpoint(appContext, engineInfo)
                    NodeBridgeResponse.success(request.id, record?.let { SettingsJson.stringify(it) } ?: "null")
                }
                NodeLifecycleCheckpointStore.METHOD_CLEAR_CHECKPOINT -> {
                    val result = NodeLifecycleCheckpointStore.clearCheckpoint(appContext, engineInfo)
                    NodeBridgeResponse.success(request.id, SettingsJson.stringify(result))
                }
                else -> unavailable(request)
            }
        }.getOrElse { error ->
            val bridgeError = (error as? NodeLifecycleCheckpointException)?.bridgeError
                ?: NodeBridgeError.invalidRequest(
                    message = error.message ?: "AutoJs6 lifecycle request failed.",
                    module = request.module,
                    method = request.method,
                )
            NodeBridgeResponse.failure(request.id, bridgeError)
        }
        callback(response)
    }

    private fun dispatchJava(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val action = {
            if (!javaInteropEnabled) NodeBridgeResponse.failure(request.id, NodeBridgeError.javaInterop(
                message = "Java interop is disabled for this execution.", code = NodeRuntimeErrorCodes.JAVA_INTEROP_DISABLED,
                module = request.module, method = request.method))
            else if (javaClosed) javaPolicyFailure(request, "Java execution has finished")
            else {
                val payload = request.firstObjectArg() ?: JSONObject()
                if (request.method in setOf("release", "dispose")) releaseJavaObject(request)
                else javaResult(request, if(request.method in RuntimeJavaSession.sessionMethods)javaSession.invoke(request.method,payload)
                    else ControlledJavaInteropPolicy.execute(request.method, payload, javaInteropHandles, appContext))
            }
        }
        val payload=request.firstObjectArg()
        // Return from oneway Binder dispatch immediately; callback polling uses the same Binder.
        javaSession.submit(request,callback) {
            if(payload?.optString("thread")=="main" && Looper.myLooper()!=Looper.getMainLooper()) {
                val result=java.util.concurrent.CompletableFuture<NodeBridgeResponse>()
                mainHandler.post { result.complete(runCatching { javaSession.nativeCall(action) }.getOrElse { NodeBridgeResponse.failure(request.id,NodeBridgeError.javaInterop(it.toString(),NodeRuntimeErrorCodes.JAVA_CALL_FAILED,request.module,request.method)) }) }
                result.get(request.timeoutMs.coerceAtLeast(1),TimeUnit.MILLISECONDS)
            } else action()
        }
    }

    private fun dispatchRhino(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        NodeBridgeRhinoExecutionService.dispatch(appContext, request, callback)
    }

    // Public members are resolved in the host process; object references belong to this execution.
    private val javaInteropHandles: ControlledJavaInteropPolicy.Handles = object : ControlledJavaInteropPolicy.Handles {
        override fun resolve(handleId: String): Any? = javaHandles[handleId]
        override fun loadClass(name:String,loader:String?):Class<*> = javaSession.loadClass(name,loader)
        override fun constructorPrefix(type:Class<*>):Array<Any?> = javaSession.constructorPrefix(type)
        override fun describeClass(type:Class<*>):JSONObject = javaSession.describeClass(type)

        override fun register(value: Any, spec: JavaInteropClassSpec): JSONObject = synchronized(javaLock) {
            check(!javaClosed) { "Java execution has finished" }
            val handleId = "java-${UUID.randomUUID()}"
            javaHandles[handleId] = value
            resourceRegistry.register(
                type = NodeBridgeResourceType.JAVA_OBJECT,
                id = handleId,
                metadata = mapOf("className" to spec.className),
            ) {
                javaHandles.remove(handleId)
                javaSession.onRelease(handleId)
            }
            ControlledJavaInteropPolicy.handleJson(handleId, value, spec)
        }
    }

    private fun releaseJavaObject(request: NodeBridgeRequest): NodeBridgeResponse {
        val payload = request.firstObjectArg()
            ?: return javaPolicyFailure(request, "java.release requires an object handle descriptor.")
        val handleId = javaHandleId(payload)
            ?: return javaPolicyFailure(request, "java.release requires a non-empty Java handle.")
        val closed = synchronized(javaLock) { javaHandles.containsKey(handleId) && resourceRegistry.close(handleId, "java.${request.method}") }
        return NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(closed))
    }

    private fun javaHandleTarget(payload: JSONObject): Any? {
        val handleId = javaHandleId(payload) ?: return null
        return javaHandles[handleId]
    }

    private fun javaHandleId(payload: JSONObject): String? {
        return payload.optNonBlankString("handle")
            ?: payload.optNonBlankString("__autojs6JavaHandle")
            ?: payload.optJSONObject("target")?.optNonBlankString("__autojs6JavaHandle")
            ?: payload.optJSONObject("target")?.optNonBlankString("handle")
    }

    private fun javaResult(request: NodeBridgeRequest, result: ControlledJavaInteropPolicy.Result): NodeBridgeResponse {
        result.error?.let { return NodeBridgeResponse.failure(request.id, it.toBridgeError(request)) }
        return NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(result.value))
    }

    private fun javaPolicyFailure(request: NodeBridgeRequest, message: String): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.javaInterop(
                message = message,
                code = NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun dispatchFetch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val payload = request.firstObjectArg()
        if (payload == null) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "fetch.request requires a JSON request descriptor.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        val fetchRequest = ControlledFetchRequest.fromJson(request, payload)
        if (fetchRequest.error != null || fetchRequest.value == null) {
            networkDiagnostics.recordPolicyRejection()
            callback(NodeBridgeResponse.failure(request.id, fetchRequest.error ?: NodeBridgeError.processDead()))
            return
        }
        if (!networkDiagnostics.tryStartRequest(bridgeLimits.maxNetworkRequests)) {
            bridgeResourceLimitRejections.incrementAndGet()
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.network(
                        message = "AutoJs6 controlled fetch exceeded ${bridgeLimits.maxNetworkRequests} active requests.",
                        code = NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT,
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }

        val controlled = fetchRequest.value
        val okHttpRequest = controlled.toOkHttpRequest()
        val client = networkClient.newBuilder()
            .callTimeout(controlled.timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val call = client.newCall(okHttpRequest)
        networkCalls[request.id] = call
        val networkResource = resourceRegistry.register(
            type = NodeBridgeResourceType.NETWORK_CALL,
            id = "network-${request.id}",
            metadata = mapOf(
                "method" to controlled.method,
                "url" to controlled.url,
            ),
        ) {
            networkCalls.remove(request.id)
            call.cancel()
            networkDiagnostics.finishRequest()
        }
        fun complete(response: NodeBridgeResponse) {
            networkCalls.remove(request.id)
            networkResource.release("fetch.complete")
            networkDiagnostics.finishRequest()
            callback(response)
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val cancelled = call.isCanceled() && !LocalNetworkDiagnostics.isCallTimeout(e)
                val failure = if (cancelled) e else LocalNetworkDiagnostics.explain(appContext, call.request().url.host, e)
                val code = if (cancelled) NodeRuntimeErrorCodes.NETWORK_ABORTED else NodeRuntimeErrorCodes.NETWORK_TIMEOUT
                if (code == NodeRuntimeErrorCodes.NETWORK_ABORTED) {
                    networkDiagnostics.recordAbort()
                } else {
                    networkDiagnostics.recordTimeout()
                }
                complete(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.network(
                            message = if (cancelled) {
                                "AutoJs6 controlled fetch was cancelled."
                            } else {
                                "AutoJs6 controlled fetch timed out or failed: ${failure.message ?: failure.javaClass.name}"
                            },
                            code = code,
                            module = request.module,
                            method = request.method,
                        ),
                    ),
                )
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use { okResponse ->
                    val result = runCatching {
                        controlled.toBridgeResponse(
                            okResponse,
                            binaryDeliveryDir = if (request.binary) appContext.cacheDir else null,
                        )
                    }.getOrElse { error ->
                        val code = (error as? ControlledFetchException)?.code ?: NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD
                        if (code == NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE || code == NodeRuntimeErrorCodes.NETWORK_POLICY_DENIED) {
                            networkDiagnostics.recordPolicyRejection()
                        }
                        complete(
                            NodeBridgeResponse.failure(
                                request.id,
                                NodeBridgeError.network(
                                    message = error.message ?: error.javaClass.name,
                                    code = code,
                                    module = request.module,
                                    method = request.method,
                                ),
                            ),
                        )
                        return
                    }
                    networkDiagnostics.recordCompleted()
                    complete(
                        NodeBridgeResponse(
                            id = request.id,
                            ok = true,
                            resultJson = result.json.toString(),
                            binary = result.binary,
                            binaryByteCount = result.binaryByteCount,
                        ),
                    )
                }
            }
        })
    }

    private fun dispatchWebSocket(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        when (request.method) {
            "connect" -> dispatchWebSocketConnect(request, callback)
            "send" -> dispatchWebSocketSend(request, callback)
            "close" -> dispatchWebSocketClose(request, callback)
            "drainEvents" -> dispatchWebSocketDrainEvents(request, callback)
            else -> callback(unavailable(request))
        }
    }

    private fun dispatchWebSocketConnect(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val payload = request.firstObjectArg()
        if (payload == null) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.invalidRequest(
                        message = "websocket.connect requires a JSON request descriptor.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        val connectRequest = ControlledWebSocketConnectRequest.fromJson(request, payload)
        if (connectRequest.error != null || connectRequest.value == null) {
            websocketDiagnostics.recordPolicyRejection()
            callback(NodeBridgeResponse.failure(request.id, connectRequest.error ?: NodeBridgeError.processDead()))
            return
        }
        if (!websocketDiagnostics.tryStartConnection(bridgeLimits.maxWebSocketConnections)) {
            bridgeResourceLimitRejections.incrementAndGet()
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.network(
                        message = "AutoJs6 controlled WebSocket exceeded ${bridgeLimits.maxWebSocketConnections} active connections.",
                        code = NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT,
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }

        val controlled = connectRequest.value
        val sessionId = "websocket-${UUID.randomUUID()}"
        val eventStream = NodeBridgeEventStream.forRequest(request, sessionId, callback)
        val responded = AtomicBoolean(false)
        val session = ControlledWebSocketSession(
            id = sessionId,
            url = controlled.url,
            maxMessageBytes = controlled.maxMessageBytes,
            maxQueueSize = controlled.maxQueueSize,
            eventStream = eventStream,
        )
        websocketSessions[sessionId] = session
        val websocketResource = resourceRegistry.register(
            type = NodeBridgeResourceType.WEBSOCKET_CONNECTION,
            id = sessionId,
            metadata = mapOf(
                "url" to controlled.url,
                "maxMessageBytes" to controlled.maxMessageBytes.toString(),
            ),
        ) { closeReason ->
            websocketSessions.remove(sessionId)
            session.close(1001, closeReason.take(123))
            websocketDiagnostics.finishConnection()
        }

        val timeoutRunnable = Runnable {
            if (responded.compareAndSet(false, true)) {
                websocketDiagnostics.recordTimeout()
                websocketResource.close("AutoJs6 WebSocket connect timed out")
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.network(
                            message = "AutoJs6 controlled WebSocket connect timed out.",
                            code = NodeRuntimeErrorCodes.NETWORK_TIMEOUT,
                            module = request.module,
                            method = request.method,
                        ),
                    ),
                )
            }
        }
        mainHandler.postDelayed(timeoutRunnable, controlled.timeoutMs)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                session.attach(webSocket)
                session.queueOpen()
                if (responded.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    websocketDiagnostics.recordCompletedConnect()
                    eventStream?.acknowledge(session.toJson()) ?: callback(NodeBridgeResponse.success(request.id, session.toJson().toString()))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.toByteArray(Charsets.UTF_8).size > session.maxMessageBytes) {
                    websocketDiagnostics.recordPolicyRejection()
                    session.queueError(NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE, "AutoJs6 controlled WebSocket message exceeded ${session.maxMessageBytes} bytes.")
                    webSocket.close(1009, "message too large")
                    return
                }
                session.queueMessageText(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                if (bytes.size > session.maxMessageBytes) {
                    websocketDiagnostics.recordPolicyRejection()
                    session.queueError(NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE, "AutoJs6 controlled WebSocket message exceeded ${session.maxMessageBytes} bytes.")
                    webSocket.close(1009, "message too large")
                    return
                }
                session.queueMessageBinary(bytes.base64())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                session.markClosed()
                mainHandler.removeCallbacks(timeoutRunnable)
                if (websocketResource.release("websocket.failure")) {
                    websocketDiagnostics.finishConnection()
                }
                session.queueError(NodeRuntimeErrorCodes.NETWORK_TIMEOUT, t.message ?: t.javaClass.name)
                session.queueClose(1006, t.message ?: t.javaClass.name)
                session.markTerminal()
                if (eventStream != null) websocketSessions.remove(sessionId)
                if (responded.compareAndSet(false, true)) {
                    websocketSessions.remove(sessionId)
                    websocketDiagnostics.recordTimeout()
                    callback(
                        NodeBridgeResponse.failure(
                            request.id,
                            NodeBridgeError.network(
                                message = "AutoJs6 controlled WebSocket failed: ${t.message ?: t.javaClass.name}",
                                code = NodeRuntimeErrorCodes.NETWORK_TIMEOUT,
                                module = request.module,
                                method = request.method,
                            ),
                        ),
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                session.markClosed()
                session.queueClose(code, reason)
                session.markTerminal()
                if (eventStream != null) websocketSessions.remove(sessionId)
                if (websocketResource.release("websocket.closed")) {
                    websocketDiagnostics.finishConnection()
                }
            }
        }
        val webSocket = networkClient.newBuilder()
            .callTimeout(controlled.timeoutMs, TimeUnit.MILLISECONDS)
            .build()
            .newWebSocket(controlled.toOkHttpRequest(), listener)
        session.attach(webSocket)
    }

    private fun dispatchWebSocketSend(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val payload = request.firstObjectArg()
        val sessionId = payload?.optString("id").orEmpty()
        val session = websocketSessions[sessionId]
        if (payload == null || session == null) {
            callback(websocketMissingSessionResponse(request))
            return
        }
        val message = ControlledWebSocketSendMessage.fromJson(request, payload, session.maxMessageBytes)
        if (message.error != null || message.value == null) {
            websocketDiagnostics.recordPolicyRejection()
            callback(NodeBridgeResponse.failure(request.id, message.error ?: NodeBridgeError.processDead()))
            return
        }
        val sent = message.value.send(session)
        callback(
            if (sent) {
                NodeBridgeResponse.success(request.id, "true")
            } else {
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.network(
                        message = "AutoJs6 controlled WebSocket send failed.",
                        code = NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD,
                        module = request.module,
                        method = request.method,
                    ),
                )
            },
        )
    }

    private fun dispatchWebSocketClose(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val payload = request.firstObjectArg()
        val sessionId = payload?.optString("id").orEmpty()
        val session = websocketSessions.remove(sessionId)
        if (payload == null || session == null) {
            callback(websocketMissingSessionResponse(request))
            return
        }
        val code = payload.optInt("code", 1000).coerceIn(1000, 4999)
        val reason = payload.optString("reason", "")
        session.close(code, reason)
        if (resourceRegistry.release(sessionId, "websocket.close")) {
            websocketDiagnostics.finishConnection()
        }
        callback(NodeBridgeResponse.success(request.id, "null"))
    }

    private fun dispatchWebSocketDrainEvents(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val payload = request.firstObjectArg()
        val sessionId = payload?.optString("id").orEmpty()
        val session = websocketSessions[sessionId]
        if (payload == null || session == null) {
            callback(NodeBridgeResponse.success(request.id, JSONArray().toString()))
            return
        }
        val events = session.drainEvents()
        if (session.removeAfterDrain()) {
            websocketSessions.remove(sessionId, session)
        }
        callback(NodeBridgeResponse.success(request.id, events.toString()))
    }

    private fun websocketMissingSessionResponse(request: NodeBridgeRequest): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.network(
                message = "AutoJs6 controlled WebSocket session is not available.",
                code = NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD,
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun dispatchKeys(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            val service = AutoJsAccessibilityService.instance
            if (service == null || !AutoJsAccessibilityService.registry.isOperational) {
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.capabilityProviderMissing(
                        module = request.module,
                        method = request.method,
                        requirement = "an enabled accessibility capability provider in the current process",
                    ),
                )
            } else {
                val action = when (request.method) {
                    "notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                    "quickSettings" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                    "lockScreen" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                    "takeScreenshot" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                    "splitScreen" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
                    else -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
                }
                NodeBridgeResponse.success(request.id, service.performGlobalAction(action).toString())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun dispatchAppExtension(request: NodeBridgeRequest): NodeBridgeResponse = try {
        val args = request.argsArray()
        val value: Any? = when (request.method) {
            "getInstalledApps" -> {
                val includeSystem = args.optJSONObject(0)?.optBoolean("includeSystem", true) ?: true
                JSONArray(appContext.packageManager.getInstalledPackages(0)
                    .filter { includeSystem || (it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                    .map(::appPackageInfoJson))
            }
            "getPackageInfo" -> {
                val packageName = args.getString(0)
                require(packageName.isNotBlank()) { "A package name is required." }
                try { appPackageInfoJson(appContext.packageManager.getPackageInfo(packageName, 0)) }
                catch (_: PackageManager.NameNotFoundException) { null }
            }
            else -> {
                val intent = createIntent(args.getJSONObject(0), forActivity = false)
                    ?: throw IllegalArgumentException("A valid JSON intent descriptor is required.")
                if (request.method == "startService") appContext.startService(intent)?.flattenToString()
                else { appContext.sendBroadcast(intent); null }
            }
        }
        NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(value))
    } catch (error: Exception) {
        NodeBridgeResponse.failure(request.id, when (error) {
            is SecurityException -> NodeBridgeError.permissionDenied(error.message ?: "Android denied this app operation.", request.module, request.method)
            is IllegalArgumentException, is org.json.JSONException -> NodeBridgeError.invalidRequest(error.message ?: "Invalid app request.", request.module, request.method)
            else -> NodeBridgeError.providerFailed(error.message ?: "Android could not perform this app operation.", request.module, request.method)
        })
    }

    @Suppress("DEPRECATION")
    private fun appPackageInfoJson(info: android.content.pm.PackageInfo): JSONObject {
        val application = info.applicationInfo
        return JSONObject().put("packageName", info.packageName)
            .put("appName", application?.let { appContext.packageManager.getApplicationLabel(it).toString() }.orEmpty())
            .put("versionName", info.versionName ?: "")
            .put("versionCode", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
            .put("system", (application?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)
            .put("enabled", application?.enabled ?: false)
            .put("firstInstallTime", info.firstInstallTime).put("lastUpdateTime", info.lastUpdateTime)
    }

    private fun dispatchApp(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (request.method in NodeBridgeSamplesBridge.METHODS) {
            // Sample and offline documentation asset reads stay off the main thread (MCP roadmap P1.3 / P3.5, D20).
            callback(samplesBridge.dispatch(request))
            return
        }
        if (request.method == "pluginCapabilities") {
            // Package manager and plugin store reads for every family; keep them off the main thread (MCP roadmap P11 D27).
            thread(name = "AutoJs6NodePluginCapabilities-${request.id}", isDaemon = true) {
                callback(
                    runCatching { NodeBridgeResponse.success(request.id, NodeBridgePluginFamilyStatus.report(appContext).toString()) }
                        .getOrElse { error ->
                            NodeBridgeResponse.failure(request.id, NodeBridgeError.providerFailed(error.message ?: error.javaClass.name, request.module, request.method))
                        },
                )
            }
            return
        }
        dispatchOnMain(request, callback) {
            when (request.method) {
                "startService", "sendBroadcast", "getInstalledApps", "getPackageInfo" -> dispatchAppExtension(request)
                "launchPackage" -> NodeBridgeResponse.success(
                    request.id,
                    launchPackage(request.firstStringArg()).toString(),
                )
                "launchApp" -> NodeBridgeResponse.success(
                    request.id,
                    launchAppByLabel(request.firstStringArg()).toString(),
                )
                "getPackageName" -> NodeBridgeResponse.success(
                    request.id,
                    NodeBridgeJson.valueToJson(packageNameForLabel(request.firstStringArg())),
                )
                "getAppName" -> NodeBridgeResponse.success(
                    request.id,
                    NodeBridgeJson.valueToJson(appLabelForPackage(request.firstStringArg())),
                )
                "isInstalled" -> NodeBridgeResponse.success(
                    request.id,
                    isPackageInstalled(request.firstStringArg()).toString(),
                )
                "viewFile" -> dispatchScopedAppFileAction(request, "viewFile") { file ->
                    IntentUtils.viewFile(
                        context = appContext,
                        path = file.absolutePath,
                        fileProviderAuthority = appFileProviderAuthority(),
                    )
                }
                "editFile" -> dispatchScopedAppFileAction(request, "editFile") { file ->
                    val args = request.argsArray()
                    if (args.length() > 1) {
                        NodeBridgeFileScope.normalizeRelative(request.firstStringArg())
                        val (line, column) = NodeBridgeDeviceActions.editorPosition(args.getJSONObject(1))
                        org.autojs.autojs.ui.edit.EditActivity.editFileAt(appContext, file.name, file.absolutePath, line - 1, column - 1, true)
                    } else IntentUtils.editFile(
                        context = appContext,
                        path = file.absolutePath,
                        fileProviderAuthority = appFileProviderAuthority(),
                    )
                }
                "currentWindow" -> accessibilityDiagnostics.currentWindow(request)
                "openAppSetting" -> {
                    openAppSetting(request.firstStringArg().ifBlank { appContext.packageName })
                    NodeBridgeResponse.success(request.id, "null")
                }
                "startActivity" -> {
                    val options = request.firstObjectArg()
                    if (options == null) {
                        NodeBridgeResponse.failure(
                            request.id,
                            NodeBridgeError.permissionDenied(
                                message = "app.startActivity requires a JSON object intent descriptor.",
                                module = request.module,
                                method = request.method,
                            ),
                        )
                    } else {
                        val intent = createIntent(options)
                        if (intent == null) {
                            NodeBridgeResponse.failure(
                                request.id,
                                NodeBridgeError.permissionDenied(
                                    message = "Invalid app.startActivity intent descriptor.",
                                    module = request.module,
                                    method = request.method,
                                ),
                            )
                        } else {
                            appContext.startActivity(intent)
                            NodeBridgeResponse.success(request.id, "null")
                        }
                    }
                }
                else -> NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.permissionDenied(
                        message = "AutoJs6 app bridge method is not available: ${request.method}",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
        }
    }

    private fun dispatchToast(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = runCatching { JSONArray(request.argsJson) }.getOrDefault(JSONArray())
        val message = args.optString(0, "")
        val options = args.optJSONObject(1)
        val isLong = options?.optString("duration").equals("long", ignoreCase = true)
        val shouldLog = options?.optBoolean("log", false) ?: false
        dispatchOnMain(request, callback) {
            if (shouldLog) {
                // Plugin requests may contain observations or model output. A caller's log flag
                // cannot opt those bodies into host logs. Direct scripts keep explicit logging.
                val detail = if (engineInfo.backend == "plugin") {
                    "messageBytes=${message.toByteArray(Charsets.UTF_8).size}"
                } else {
                    "message=$message"
                }
                Log.i(TAG, "toast.showToast $detail duration=${if (isLong) "long" else "short"}")
            }
            InrtToastVisibilityGuard.holdForToast(isLong)
            ScriptToast.enqueueToast(appContext, "nodejs@${engineInfo.id}", message, isLong)
            NodeBridgeResponse.success(request.id, "null")
        }
    }

    private fun dispatchOnMain(
        request: NodeBridgeRequest,
        callback: (NodeBridgeResponse) -> Unit,
        action: () -> NodeBridgeResponse,
    ) {
        val runnable = Runnable {
            runCatching {
                callback(action())
            }.onFailure { error ->
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.providerFailed(
                            message = error.message ?: error.javaClass.name,
                            module = request.module,
                            method = request.method,
                            stack = error.stackTraceToString(),
                        ),
                    ),
                )
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    private fun readClipboardText(clipboard: ClipboardManager): String {
        val clip = clipboard.primaryClip ?: return ""
        if (clip.itemCount <= 0) {
            return ""
        }
        return clip.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty()
    }

    private fun clipboardAccessFailure(request: NodeBridgeRequest, error: Throwable): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = "AutoJs6 clipboard bridge could not access clipboard: ${error.message ?: error.javaClass.name}",
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun deviceAccessFailure(request: NodeBridgeRequest, error: Throwable): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = "AutoJs6 device bridge could not access device capability: ${error.message ?: error.javaClass.name}",
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun isScreenOn(): Boolean {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isInteractive
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    private fun openBatteryOptimizationSettings(dryRun: Boolean): JSONObject {
        val intent = batteryOptimizationSettingsIntent()
        val canOpenSettings = intentAvailable(intent)
        var opened = false
        var reason = if (canOpenSettings) "settings-intent-ready" else "settings-intent-unavailable"
        if (canOpenSettings && dryRun) {
            reason = "settings-intent-dry-run"
        } else if (canOpenSettings) {
            runCatching {
                appContext.startActivity(intent)
                opened = true
                reason = "settings-intent-opened"
            }.getOrElse { error ->
                reason = "settings-intent-failed:${error.javaClass.simpleName}"
            }
        }
        return JSONObject()
            .put("schema", "autojs6-node-battery-optimization-settings-v1")
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("ignoringBatteryOptimizations", isIgnoringBatteryOptimizations())
            .put("canOpenSettings", canOpenSettings)
            .put("opened", opened)
            .put("dryRun", dryRun)
            .put("action", intent.action.orEmpty())
            .put("reason", reason)
    }

    private fun batteryOptimizationSettingsIntent(): Intent {
        return Intent(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            } else {
                Settings.ACTION_SETTINGS
            },
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Suppress("DEPRECATION")
    private fun wakeUpDevice() {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return
        if (powerManager.isInteractive) {
            return
        }
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "$TAG:DeviceWakeUp",
        )
        wakeLock.acquire(1000L)
    }

    @Suppress("DEPRECATION")
    private fun vibrateDevice(durationMs: Long) {
        if (durationMs <= 0L) {
            return
        }
        val duration = durationMs.coerceAtMost(10_000L)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(duration)
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return true
    }

    @Suppress("DEPRECATION")
    private fun launchAppByLabel(label: String): Boolean {
        if (label.isBlank()) {
            return false
        }
        val packageManager = appContext.packageManager
        val applications = runCatching {
            packageManager.getInstalledApplications(0)
        }.getOrDefault(emptyList())
        val matched = applications.firstOrNull { applicationInfo ->
            runCatching {
                packageManager.getApplicationLabel(applicationInfo).toString() == label
            }.getOrDefault(false)
        } ?: return false
        return launchPackage(matched.packageName)
    }

    @Suppress("DEPRECATION")
    private fun packageNameForLabel(label: String): String? {
        if (label.isBlank()) {
            return null
        }
        val packageManager = appContext.packageManager
        val applications = runCatching {
            packageManager.getInstalledApplications(0)
        }.getOrDefault(emptyList())
        return applications.firstOrNull { applicationInfo ->
            runCatching {
                packageManager.getApplicationLabel(applicationInfo).toString() == label
            }.getOrDefault(false)
        }?.packageName
    }

    @Suppress("DEPRECATION")
    private fun appLabelForPackage(packageName: String): String? {
        if (packageName.isBlank()) {
            return null
        }
        val packageManager = appContext.packageManager
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        return runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun dispatchScopedAppFileAction(
        request: NodeBridgeRequest,
        methodName: String,
        launcher: (File) -> Boolean,
    ): NodeBridgeResponse {
        val rawPath = request.firstStringArg()
        val file = resolveScopedAppFile(rawPath)
            ?: return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.permissionDenied(
                    message = "app.$methodName path must stay inside the scoped working directory.",
                    module = request.module,
                    method = request.method,
                ),
            )
        if (!file.isFile) {
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest(
                    message = "app.$methodName target does not exist or is not a file: $rawPath",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        return NodeBridgeResponse.success(request.id, launcher(file).toString())
    }

    private fun resolveScopedAppFile(rawPath: String): File? {
        if (rawPath.isBlank() || rawPath.indexOf('\u0000') >= 0) {
            return null
        }
        val base = File(engineInfo.cwd.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath).canonicalFile
        val trimmed = rawPath.trim()
        val candidate = runCatching {
            val input = File(trimmed)
            if (input.isAbsolute) {
                input.canonicalFile
            } else {
                val normalized = trimmed.replace('\\', '/')
                val segments = normalized.split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) {
                    return null
                }
                File(base, segments.joinToString(File.separator)).canonicalFile
            }
        }.getOrNull() ?: return null
        val basePath = base.path.trimEnd(File.separatorChar)
        val candidatePath = candidate.path
        return candidate.takeIf { candidatePath == basePath || candidatePath.startsWith(basePath + File.separator) }
    }

    private fun appFileProviderAuthority(): String =
        if (BuildConfig.isInrt) {
            "${appContext.packageName}.fileprovider"
        } else {
            AppFileProvider.AUTHORITY
        }

    private fun openAppSetting(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    private fun createIntent(options: JSONObject, forActivity: Boolean = true): Intent? {
        val action = options.optNonBlankString("action")
        val data = options.optNonBlankString("data")
        val type = options.optNonBlankString("type")
        val packageName = options.optNonBlankString("packageName") ?: options.optNonBlankString("package")
        val className = options.optNonBlankString("className")
        val component = options.optNonBlankString("component")
        val hasTarget = action != null || data != null || type != null || packageName != null || className != null || component != null
        if (!hasTarget) {
            return null
        }
        val categories = parseIntentCategories(options) ?: return null
        val flags = parseIntentFlags(options) ?: return null
        val extras = options.optJSONObject("extras")
        if (options.has("extras") && !options.isNull("extras") && extras == null) {
            return null
        }
        return Intent().apply {
            action?.let { setAction(it) }
            when {
                data != null && type != null -> setDataAndType(Uri.parse(data), type)
                data != null -> setData(Uri.parse(data))
                type != null -> setType(type)
            }
            packageName?.let { setPackage(it) }
            if (packageName != null && className != null) {
                setClassName(packageName, className)
            }
            component?.let { setComponent(parseComponentName(it) ?: return null) }
            categories.forEach(::addCategory)
            addFlags(flags)
            if (forActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            extras?.let { putJsonIntentExtras(this, it) }
        }
    }

    private fun parseIntentCategories(options: JSONObject): List<String>? {
        val values = mutableListOf<String>()
        fun append(value: Any?): Boolean {
            return when (value) {
                null, JSONObject.NULL -> true
                is String -> {
                    value.takeIf { it.isNotBlank() }?.let(values::add)
                    true
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        if (!append(value.opt(index))) {
                            return false
                        }
                    }
                    true
                }
                else -> false
            }
        }
        if (options.has("category") && !append(options.opt("category"))) {
            return null
        }
        if (options.has("categories") && !append(options.opt("categories"))) {
            return null
        }
        return values.distinct()
    }

    private fun parseIntentFlags(options: JSONObject): Int? {
        if (!options.has("flags") || options.isNull("flags")) {
            return 0
        }
        fun flagOf(value: Any?): Int? {
            return when (value) {
                is Int -> value
                is Long -> value.toInt()
                is Number -> value.toInt()
                else -> null
            }
        }
        val value: Any? = options.opt("flags")
        return when (value) {
            is JSONArray -> {
                var flags = 0
                for (index in 0 until value.length()) {
                    val flag = flagOf(value.opt(index)) ?: return null
                    flags = flags or flag
                }
                flags
            }
            else -> flagOf(value)
        }
    }

    private fun parseComponentName(component: String): android.content.ComponentName? {
        val separator = component.indexOf('/')
        if (separator <= 0 || separator >= component.lastIndex) {
            return null
        }
        val packageName = component.substring(0, separator)
        val className = component.substring(separator + 1).let { name ->
            if (name.startsWith(".")) packageName + name else name
        }
        return android.content.ComponentName(packageName, className)
    }

    private fun putJsonIntentExtras(intent: Intent, extras: JSONObject) {
        val keys = extras.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value: Any? = extras.opt(key)
            when (value) {
                is Boolean -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is Double -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
                is JSONArray -> putJsonIntentExtraArray(intent, key, value)
                JSONObject.NULL, null -> Unit
                else -> intent.putExtra(key, value.toString())
            }
        }
    }

    private fun putJsonIntentExtraArray(intent: Intent, key: String, values: JSONArray) {
        if (values.length() == 0) {
            intent.putExtra(key, emptyArray<String>())
            return
        }
        when (values.opt(0)) {
            is Boolean -> intent.putExtra(key, BooleanArray(values.length()) { index -> values.optBoolean(index) })
            is Int -> intent.putExtra(key, IntArray(values.length()) { index -> values.optInt(index) })
            is Long -> intent.putExtra(key, LongArray(values.length()) { index -> values.optLong(index) })
            is Number -> intent.putExtra(key, DoubleArray(values.length()) { index -> values.optDouble(index) })
            is String -> intent.putExtra(key, Array(values.length()) { index -> values.optString(index) })
            else -> intent.putExtra(key, values.toString())
        }
    }

    private fun NodeBridgeRequest.argsArray(): JSONArray {
        return runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())
    }

    private fun NodeBridgeRequest.firstStringArg(): String {
        return argsArray().optString(0, "")
    }

    private fun NodeBridgeRequest.firstObjectArg(): JSONObject? {
        return argsArray().optJSONObject(0)
    }

    private fun NodeBridgeRequest.firstLongArg(): Long {
        val value = argsArray().opt(0)
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private companion object {
        const val TAG = "AndroidNodeBridge"
        const val SHELL_STREAM_JOIN_MS = 500L
        const val MAX_ACTIVE_UI_SESSIONS = 4

        /**
         * How long before the plugin's bridge deadline `accessibility.ensureEnabled` answers, and the longest it
         * waits for the service at all (the controller's own strategies run within this budget).
         * zh-CN: `accessibility.ensureEnabled` 在插件桥超时前多久作答, 以及最多等待服务多久 (控制器的各策略都在此预算内).
         */
        const val ENSURE_ENABLED_REPLY_MARGIN_MS = 500L
        const val ENSURE_ENABLED_MAX_WAIT_MS = 10_000L
        const val MAX_ACTIVE_OVERLAY_SESSIONS = 4
        const val OCR_MODULE_LABEL = "OCR"
        val MODEL_FACING_ENGINES: Set<String> = setOf(McpHostCapabilityBroker.ENGINE_NAME, ThreeStoveAgentIds.ENGINE)
        // Gestures block a broker dispatch thread until completion; cap the
        // requested duration so one call cannot pin it for minutes.
        const val MAX_ACCESSIBILITY_GESTURE_DURATION_MS = 10_000L

        /** The active window root of the normal mode (`rootInActiveWindow`). zh-CN: normal 模式的活动窗口根节点 (`rootInActiveWindow`). */
        private val BRIDGE_ROOT_NORMAL = A11yWindowRootPolicy()

        /** The service's cached fast root, the fallback of the frozen protocol. zh-CN: 服务缓存的 fast 根节点, 冻结协议的兜底. */
        private val BRIDGE_ROOT_FAST = A11yWindowRootPolicy(mode = WindowRootPolicy.Mode.FAST)
    }
}

private class ControlledOverlaySession(
    private val context: Context,
    val id: String,
    descriptor: JSONObject,
    private val eventStream: NodeBridgeEventStream? = null,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val events = ConcurrentLinkedQueue<JSONObject>()
    private val eventCount = AtomicInteger(0)
    private val droppedCount = AtomicInteger(0)
    private val createdAt = System.currentTimeMillis()
    private var model = JSONObject(descriptor.toString())
    private var disclosure = model.getJSONObject("disclosure")
    private var windowOptions = model.optJSONObject("window") ?: JSONObject()

    @Volatile
    private var rootView: View? = null

    @Volatile
    private var layoutParams: WindowManager.LayoutParams? = null

    @Volatile
    private var resource: NodeBridgeResourceHandle? = null

    private var dragDownX = 0f
    private var dragDownY = 0f
    private var dragOriginX = 0
    private var dragOriginY = 0
    private var dragging = false
    private val dragSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val dragListener = View.OnTouchListener { view, event ->
        val params = layoutParams
        if (!windowOptions.optBoolean("draggable", false) || params == null) false
        else when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                dragDownX = event.rawX; dragDownY = event.rawY
                dragOriginX = params.x; dragOriginY = params.y
                dragging = false; view.isPressed = true
                true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragDownX; val dy = event.rawY - dragDownY
                if (kotlin.math.abs(dx) > dragSlop || kotlin.math.abs(dy) > dragSlop) dragging = true
                if (dragging) {
                    view.isPressed = false
                    params.x = dragOriginX + dx.toInt(); params.y = dragOriginY + dy.toInt()
                    rootView?.let { windowManager.updateViewLayout(it, params) }
                }
                true
            }
            android.view.MotionEvent.ACTION_UP -> {
                view.isPressed = false
                if (dragging) enqueueEvent(JSONObject().put("type", "move").put("state", JSONObject().put("x", params.x).put("y", params.y)))
                else view.performClick()
                dragging = false
                true
            }
            android.view.MotionEvent.ACTION_CANCEL -> { view.isPressed = false; dragging = false; true }
            else -> false
        }
    }

    fun show() {
        check(rootView == null) { "Overlay session already shown." }
        val content = model.getJSONObject("content")
        val view = android.widget.FrameLayout(context).apply {
            setBackgroundColor(Color.argb(245, 255, 255, 255))
            val padding = dp(8)
            setPadding(padding, padding, padding, padding)
            alpha = windowOptions.optDouble("alpha", 1.0).toFloat().coerceIn(0.2f, 1f)
            setOnTouchListener(dragListener)
            addView(createContentView(content))
        }
        val params = createLayoutParams(windowOptions)
        windowManager.addView(view, params)
        rootView = view
        layoutParams = params
        enqueueEvent(JSONObject().put("type", "shown"))
    }

    fun attachResource(value: NodeBridgeResourceHandle) {
        resource = value
    }

    fun releaseResource(reason: String) {
        resource?.release(reason)
        resource = null
    }

    fun disclosureTitle(): String = disclosure.optString("title")

    fun isClosed(): Boolean = closed.get()

    fun pendingEventCount(): Int = eventCount.get()

    fun droppedEventCount(): Int = droppedCount.get()

    fun update(patch: JSONObject) {
        check(!closed.get()) { "The overlay is closed." }
        val next = JSONObject(model.toString())
        val window = patch.optJSONObject("window")
        if (window != null) {
            val merged = JSONObject(windowOptions.toString())
            window.keys().forEach { merged.put(it, window.get(it)) }
            next.put("window", merged)
        }
        patch.optJSONObject("disclosure")?.let { next.put("disclosure", it) }
        val targetId = patch.optString("id").takeIf { it.isNotBlank() }
        val contentChanged = patch.has("content") || patch.keys().asSequence().any { it !in listOf("id", "window", "disclosure") }
        if (patch.has("content")) next.put("content", patch.getJSONObject("content"))
        else if (contentChanged) {
            val content = next.getJSONObject("content")
            val target = if (targetId == null) content else findContent(content, targetId)
            require(target != null) { "No overlay component has id $targetId." }
            patch.keys().forEach { key -> if (key !in listOf("id", "window", "disclosure")) target.put(key, patch.get(key)) }
        }
        validateDescriptor(next)?.let { throw IllegalArgumentException(it) }
        // Construct the replacement before altering the displayed tree, so invalid properties leave it intact.
        val contentView = if (contentChanged) createContentView(next.getJSONObject("content")) else null
        model = next
        disclosure = model.getJSONObject("disclosure")
        windowOptions = model.optJSONObject("window") ?: windowOptions
        val params = layoutParams
        if (params != null && window != null) {
            applyWindowPatch(params, window)
            params.flags = overlayWindowFlags(windowOptions)
            rootView?.alpha = windowOptions.optDouble("alpha", 1.0).toFloat()
            rootView?.let { windowManager.updateViewLayout(it, params) }
        }
        if (contentView != null) (rootView as? android.widget.FrameLayout)?.apply { removeAllViews(); addView(contentView) }
        enqueueEvent(JSONObject().put("type", "update").put("targetId", targetId ?: ""))
    }

    private fun findContent(node: JSONObject, id: String): JSONObject? {
        if (node.optString("id") == id) return node
        val children = node.optJSONArray("children") ?: return null
        for (index in 0 until children.length()) findContent(children.getJSONObject(index), id)?.let { return it }
        return null
    }

    fun drainEvents(maxEvents: Int): JSONArray {
        val output = JSONArray()
        repeat(maxEvents.coerceAtLeast(0)) {
            val event = events.poll() ?: return@repeat
            eventCount.decrementAndGet()
            output.put(event)
        }
        return output
    }

    fun close(reason: String): Boolean {
        if (!closed.compareAndSet(false, true)) {
            return false
        }
        enqueueEvent(JSONObject().put("type", "close").put("reason", reason))
        runOnMain {
            rootView?.let { view ->
                runCatching { windowManager.removeViewImmediate(view) }
            }
            rootView = null
            layoutParams = null
        }
        return true
    }

    fun snapshot(): JSONObject {
        val params = layoutParams
        val bounds = JSONObject()
            .put("x", params?.x ?: 0)
            .put("y", params?.y ?: 0)
            .put("width", publicDimension(params?.width ?: WindowManager.LayoutParams.WRAP_CONTENT))
            .put("height", publicDimension(params?.height ?: WindowManager.LayoutParams.WRAP_CONTENT))
        return JSONObject()
            .put("id", id)
            .put("type", "overlay")
            .put("closed", closed.get())
            .put("visible", rootView != null && !closed.get())
            .put("createdAt", createdAt)
            .put("bounds", bounds)
            .put("window", JSONObject().put("touchable", windowOptions.optBoolean("touchable", true))
                .put("focusable", windowOptions.optBoolean("focusable", false)).put("draggable", windowOptions.optBoolean("draggable", false))
                .put("alpha", rootView?.alpha ?: 1f))
            .put("disclosure", JSONObject().put("title", disclosure.optString("title")))
            .put("droppedCount", droppedCount.get())
    }

    private fun createContentView(descriptor: JSONObject): View {
        return when (normalizeType(descriptor.optString("type"))) {
            TYPE_COLUMN -> createLinearLayout(descriptor, LinearLayout.VERTICAL)
            TYPE_ROW -> createLinearLayout(descriptor, LinearLayout.HORIZONTAL)
            TYPE_TEXT -> createText(descriptor)
            TYPE_BUTTON -> createButton(descriptor)
            else -> throw IllegalArgumentException("Unsupported ui.overlay component type: ${descriptor.optString("type")}")
        }.also { view ->
            descriptor.optString("id").takeIf { it.isNotBlank() }?.let { id -> view.tag = id }
            view.layoutParams = ViewGroup.LayoutParams(dimension(descriptor.opt("width"), ViewGroup.LayoutParams.WRAP_CONTENT),
                dimension(descriptor.opt("height"), ViewGroup.LayoutParams.WRAP_CONTENT))
            applyViewPatch(view, descriptor)
            view.setOnTouchListener(dragListener)
        }
    }

    private fun createLinearLayout(descriptor: JSONObject, orientation: Int): LinearLayout {
        val layout = LinearLayout(context).apply {
            this.orientation = orientation
            gravity = Gravity.START
        }
        val children = descriptor.optJSONArray("children") ?: JSONArray()
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            layout.addView(createContentView(child))
        }
        return layout
    }

    private fun createText(descriptor: JSONObject): TextView {
        return TextView(context).apply {
            text = descriptor.optString("text")
            textSize = descriptor.optDouble("textSize", 16.0).toFloat().coerceIn(8f, 48f)
            setTextColor(Color.rgb(32, 32, 32))
        }
    }

    private fun createButton(descriptor: JSONObject): Button {
        val targetId = descriptor.optString("id")
        return Button(context).apply {
            text = descriptor.optString("text", targetId.ifBlank { "Button" })
            isAllCaps = false
            setOnClickListener {
                enqueueEvent(JSONObject().put("type", "click").put("targetId", targetId))
            }
        }
    }

    private fun applyViewPatch(target: View?, patch: JSONObject) {
        if (target == null) {
            return
        }
        if (target is TextView && patch.has("text")) {
            target.text = patch.optString("text")
        }
        if (target is TextView && patch.has("textSize")) target.textSize = patch.optDouble("textSize", 16.0).toFloat().coerceIn(8f, 48f)
        if (target is TextView && patch.has("textColor")) target.setTextColor(overlayColor(patch.get("textColor")))
        if (patch.has("backgroundColor")) target.setBackgroundColor(overlayColor(patch.get("backgroundColor")))
        if (patch.has("padding")) dp(patch.optInt("padding").coerceAtLeast(0)).let { target.setPadding(it, it, it, it) }
        if (patch.has("enabled")) {
            target.isEnabled = patch.optBoolean("enabled", true)
        }
        if (patch.has("visible")) {
            target.visibility = if (patch.optBoolean("visible", true)) View.VISIBLE else View.GONE
        }
    }

    private fun overlayColor(value: Any): Int = if (value is Number) value.toInt() else Color.parseColor(value.toString())

    private fun createLayoutParams(window: JSONObject): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dimension(window.opt("width"), WindowManager.LayoutParams.WRAP_CONTENT),
            dimension(window.opt("height"), WindowManager.LayoutParams.WRAP_CONTENT),
            overlayWindowType(),
            overlayWindowFlags(window),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(window.optInt("x", 24))
            y = dp(window.optInt("y", 96))
            packageName = context.packageName
        }
    }

    private fun applyWindowPatch(params: WindowManager.LayoutParams, window: JSONObject) {
        if (window.has("width")) {
            params.width = dimension(window.opt("width"), params.width)
        }
        if (window.has("height")) {
            params.height = dimension(window.opt("height"), params.height)
        }
        if (window.has("x")) {
            params.x = dp(window.optInt("x", params.x))
        }
        if (window.has("y")) {
            params.y = dp(window.optInt("y", params.y))
        }
    }

    private fun overlayWindowFlags(window: JSONObject): Int {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!window.optBoolean("focusable", false)) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (!window.optBoolean("touchable", true)) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dimension(raw: Any?, fallback: Int): Int {
        return when (raw) {
            "match_parent" -> WindowManager.LayoutParams.MATCH_PARENT
            "wrap_content" -> WindowManager.LayoutParams.WRAP_CONTENT
            null, JSONObject.NULL -> fallback
            is Int -> dp(raw.coerceIn(1, 4096))
            is Long -> dp(raw.coerceIn(1L, 4096L).toInt())
            is Number -> dp(raw.toInt().coerceIn(1, 4096))
            is String -> raw.toIntOrNull()?.let { dp(it.coerceIn(1, 4096)) } ?: fallback
            else -> fallback
        }
    }

    private fun publicDimension(value: Int): Int {
        return when (value) {
            WindowManager.LayoutParams.MATCH_PARENT -> -1
            WindowManager.LayoutParams.WRAP_CONTENT -> -2
            else -> value
        }
    }

    private fun enqueueEvent(rawEvent: JSONObject) {
        val event = JSONObject(rawEvent.toString())
            .put("overlayId", id)
            .put("eventTime", System.currentTimeMillis())
        events.offer(event)
        if (eventCount.incrementAndGet() > MAX_EVENT_QUEUE_SIZE) {
            events.poll()
            eventCount.decrementAndGet()
            droppedCount.incrementAndGet()
        }
        eventStream?.publish(event)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    companion object {
        private const val TYPE_COLUMN = "Column"
        private const val TYPE_ROW = "Row"
        private const val TYPE_TEXT = "Text"
        private const val TYPE_BUTTON = "Button"
        private const val TYPE_WEB_VIEW = "WebView"
        private const val MAX_EVENT_QUEUE_SIZE = 128

        private val supportedTypes = setOf(
            TYPE_COLUMN,
            TYPE_ROW,
            TYPE_TEXT,
            TYPE_BUTTON,
            "Vertical",
            "Horizontal",
            "column",
            "row",
            "text",
            "button",
        )

        fun validateDescriptor(descriptor: JSONObject): String? {
            val disclosure = descriptor.optJSONObject("disclosure")
                ?: return "ui.overlay.show requires disclosure title and text."
            if (disclosure.optString("title").isBlank() || disclosure.optString("text").isBlank()) {
                return "ui.overlay.show requires non-empty disclosure title and text."
            }
            val window = descriptor.optJSONObject("window") ?: JSONObject()
            if (window.optBoolean("visible", true) == false) {
                return "ui.overlay.show does not allow hidden overlays."
            }
            if (window.has("alpha") && window.optDouble("alpha", 1.0).let { !it.isFinite() || it !in 0.2..1.0 }) {
                return "ui.overlay alpha must be between 0.2 and 1.0."
            }
            if (window.has("flags") || window.has("type") || window.has("layoutParams")) {
                return "ui.overlay.show rejects raw WindowManager.LayoutParams, flags, and platform window types."
            }
            val content = descriptor.optJSONObject("content")
                ?: return "ui.overlay.show requires a JSON content descriptor."
            return validateContent(content)
        }

        private fun validateContent(descriptor: JSONObject): String? {
            val type = descriptor.optString("type")
            val normalizedType = normalizeType(type)
            if (normalizedType == TYPE_WEB_VIEW) {
                return "ui.overlay.show component type WebView requires a separate security gate."
            }
            if (type.isBlank() || type !in supportedTypes && normalizedType !in supportedTypes) {
                return "ui.overlay.show uses an unsupported component type: $type."
            }
            val children = descriptor.optJSONArray("children")
            if (children != null) {
                for (index in 0 until children.length()) {
                    val child = children.optJSONObject(index)
                        ?: return "ui.overlay.show children must be JSON object components."
                    validateContent(child)?.let { return it }
                }
            }
            return null
        }

        private fun normalizeType(rawType: String): String = when (rawType) {
            "Vertical", "vertical", "LinearLayout", "linearLayout", "Column", "column" -> TYPE_COLUMN
            "Horizontal", "horizontal", "Row", "row" -> TYPE_ROW
            "Text", "text" -> TYPE_TEXT
            "Button", "button" -> TYPE_BUTTON
            "WebView", "webView", "webview" -> TYPE_WEB_VIEW
            else -> rawType
        }
    }
}

private data class ShellExecResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
    val truncated: Boolean = false,
    val timedOut: Boolean = false,
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("code", code)
            put("stdout", stdout)
            put("stderr", stderr)
            put("truncated", truncated)
            put("timedOut", timedOut)
            put("outputBytes", stdout.toByteArray(Charsets.UTF_8).size + stderr.toByteArray(Charsets.UTF_8).size)
        }.toString()
    }
}

private data class ControlledAudioStream(
    val name: String,
    val type: Int,
)

enum class ControlledWorkTaskKind(val wireName: String) {
    ONCE("once"),
    PERIODIC("periodic"),
}

object ControlledWorkManagerPolicy {
    const val MAX_TASKS_PER_PROVIDER = 64
    const val MAX_DELAY_MS = 2_592_000_000L
    const val MIN_PERIODIC_INTERVAL_MS = 900_000L
    const val MAX_PERIODIC_INTERVAL_MS = 2_592_000_000L
    const val DEFAULT_TASK_TIMEOUT_MS = 30_000L
    const val MAX_TASK_TIMEOUT_MS = 600_000L
}

private data class ControlledWorkTaskDescriptor(
    val kind: ControlledWorkTaskKind,
    val entryPath: String,
    val entryKind: String,
    val backend: String,
    val packaged: Boolean,
    val delayMs: Long,
    val intervalMs: Long?,
    val timeoutMs: Long,
    val constraints: JSONObject,
) {
    fun toJson(id: String, engineInfo: NodeBridgeEngineInfo): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", kind.wireName)
            put("status", "registered")
            put("scheduler", NodeWorkManagerBridgeScheduler.SCHEDULER_NAME)
            put("backend", backend)
            put("entryKind", entryKind)
            if (entryKind == "projectEntry") {
                put("entry", entryPath)
            } else {
                put("scriptPath", entryPath)
            }
            put("packaged", packaged)
            put("delayMs", delayMs)
            intervalMs?.let { put("intervalMs", it) }
            put("timeoutMs", timeoutMs)
            put("constraints", JSONObject(constraints.toString()))
            put("engineId", engineInfo.id)
            put("sourceName", engineInfo.sourceName)
            put("workingDirectory", engineInfo.cwd)
            put("packageName", engineInfo.packageName)
            put("versionName", engineInfo.versionName)
            put("versionCode", engineInfo.versionCode)
            put("createdAtEpochMs", System.currentTimeMillis())
        }
    }

    companion object {
        fun fromJson(
            request: NodeBridgeRequest,
            kind: ControlledWorkTaskKind,
            payload: JSONObject,
            appContext: Context,
            engineInfo: NodeBridgeEngineInfo,
        ): ControlledWorkTaskParseResult {
            // `embedded` is the v1 work_manager wire token implemented by the runtime plugin.
            // Keep accepting it until that contract is versioned; the host itself has no
            // embedded Node runtime.
            val backend = payload.optString("backend", "embedded").ifBlank { "embedded" }
            if (!backend.equals("embedded", ignoreCase = true)) {
                return ControlledWorkTaskParseResult(
                    error = workTaskError(request, "work_manager supports only its legacy backend=\"embedded\" plugin wire token."),
                )
            }

            val hasScriptPath = payload.has("scriptPath") && !payload.isNull("scriptPath")
            val hasEntry = payload.has("entry") && !payload.isNull("entry")
            val rawScriptPath = when {
                hasScriptPath -> payload.optString("scriptPath", "")
                hasEntry -> payload.optString("entry", "")
                else -> ""
            }
            val entryKind = when {
                hasScriptPath -> "scriptPath"
                hasEntry -> "projectEntry"
                else -> ""
            }
            val entryPath = normalizeRelativeScriptPath(rawScriptPath)
                ?: return ControlledWorkTaskParseResult(
                    error = workTaskError(request, "work_manager task path must be a non-empty relative path inside the scoped project."),
                )
            val packaged = payload.optBoolean("packaged", false)
            if (resolveScopedScriptPath(appContext, engineInfo, entryPath, packaged) == null) {
                return ControlledWorkTaskParseResult(
                    error = workTaskError(request, "work_manager task path must resolve inside the scoped working directory."),
                )
            }

            val delayMs = parseDuration(
                payload = payload,
                name = "delayMs",
                fallback = 0L,
                min = 0L,
                max = ControlledWorkManagerPolicy.MAX_DELAY_MS,
            ) ?: return ControlledWorkTaskParseResult(
                error = workTaskError(request, "work_manager delayMs must be between 0 and ${ControlledWorkManagerPolicy.MAX_DELAY_MS}."),
            )
            val intervalMs = when (kind) {
                ControlledWorkTaskKind.ONCE -> null
                ControlledWorkTaskKind.PERIODIC -> {
                    if (!payload.has("intervalMs") || payload.isNull("intervalMs")) {
                        return ControlledWorkTaskParseResult(
                            error = workTaskError(request, "work_manager.schedulePeriodic requires intervalMs."),
                        )
                    }
                    parseDuration(
                        payload = payload,
                        name = "intervalMs",
                        fallback = ControlledWorkManagerPolicy.MIN_PERIODIC_INTERVAL_MS,
                        min = ControlledWorkManagerPolicy.MIN_PERIODIC_INTERVAL_MS,
                        max = ControlledWorkManagerPolicy.MAX_PERIODIC_INTERVAL_MS,
                    ) ?: return ControlledWorkTaskParseResult(
                        error = workTaskError(
                            request,
                            "work_manager intervalMs must be between ${ControlledWorkManagerPolicy.MIN_PERIODIC_INTERVAL_MS} and ${ControlledWorkManagerPolicy.MAX_PERIODIC_INTERVAL_MS}.",
                        ),
                    )
                }
            }
            val constraints = parseConstraints(payload)
                ?: return ControlledWorkTaskParseResult(
                    error = workTaskError(request, "work_manager constraints may contain only boolean charging, network, and idle fields."),
                )
            val timeoutFieldName = if (payload.has("taskTimeoutMs") && !payload.isNull("taskTimeoutMs")) {
                "taskTimeoutMs"
            } else {
                "timeoutMs"
            }
            val timeoutMs = parseDuration(
                payload = payload,
                name = timeoutFieldName,
                fallback = ControlledWorkManagerPolicy.DEFAULT_TASK_TIMEOUT_MS,
                min = 1L,
                max = ControlledWorkManagerPolicy.MAX_TASK_TIMEOUT_MS,
            ) ?: return ControlledWorkTaskParseResult(
                error = workTaskError(
                    request,
                    "work_manager $timeoutFieldName must be between 1 and ${ControlledWorkManagerPolicy.MAX_TASK_TIMEOUT_MS}.",
                ),
            )

            return ControlledWorkTaskParseResult(
                value = ControlledWorkTaskDescriptor(
                    kind = kind,
                    entryPath = entryPath,
                    entryKind = entryKind,
                    backend = "embedded",
                    packaged = packaged,
                    delayMs = delayMs,
                    intervalMs = intervalMs,
                    timeoutMs = timeoutMs,
                    constraints = constraints,
                ),
            )
        }

        private fun normalizeRelativeScriptPath(value: String): String? {
            val text = value.trim()
            if (text.isBlank() || text.indexOf('\u0000') >= 0) {
                return null
            }
            if (File(text).isAbsolute || text.startsWith("/") || WINDOWS_ABSOLUTE_PATH.matches(text)) {
                return null
            }
            val segments = text.replace('\\', '/')
                .split('/')
                .filter { it.isNotBlank() && it != "." }
            if (segments.isEmpty() || segments.any { it == ".." }) {
                return null
            }
            return segments.joinToString("/")
        }

        private fun resolveScopedScriptPath(
            appContext: Context,
            engineInfo: NodeBridgeEngineInfo,
            scriptPath: String,
            packaged: Boolean,
        ): File? {
            val base = runCatching {
                File(engineInfo.cwd.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath).canonicalFile
            }.getOrNull() ?: return null
            val candidate = runCatching { File(base, scriptPath).canonicalFile }.getOrNull() ?: return null
            val basePath = base.path.trimEnd(File.separatorChar)
            val candidatePath = candidate.path
            if (candidatePath != basePath && !candidatePath.startsWith(basePath + File.separator)) {
                return null
            }
            if (!packaged && !candidate.isFile) {
                return null
            }
            return candidate
        }

        private fun parseDuration(payload: JSONObject, name: String, fallback: Long, min: Long, max: Long): Long? {
            if (!payload.has(name) || payload.isNull(name)) {
                return fallback
            }
            val value: Any? = payload.opt(name)
            val parsed = when (value) {
                is Int -> value.toLong()
                is Long -> value
                is Number -> {
                    val doubleValue = value.toDouble()
                    if (!doubleValue.isFinite()) null else doubleValue.toLong()
                }
                is String -> value.toLongOrNull()
                else -> null
            } ?: return null
            return parsed.takeIf { it in min..max }
        }

        private fun parseConstraints(payload: JSONObject): JSONObject? {
            if (!payload.has("constraints") || payload.isNull("constraints")) {
                return defaultConstraints()
            }
            val source = payload.optJSONObject("constraints") ?: return null
            val allowed = setOf("charging", "network", "idle")
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in allowed || source.opt(key) !is Boolean) {
                    return null
                }
            }
            return JSONObject().apply {
                put("charging", source.optBoolean("charging", false))
                put("network", source.optBoolean("network", false))
                put("idle", source.optBoolean("idle", false))
            }
        }

        private fun defaultConstraints(): JSONObject {
            return JSONObject()
                .put("charging", false)
                .put("network", false)
                .put("idle", false)
        }

        private fun workTaskError(request: NodeBridgeRequest, message: String): NodeBridgeError {
            return NodeBridgeError.permissionDenied(
                message = message,
                module = request.module,
                method = request.method,
            )
        }

        private val WINDOWS_ABSOLUTE_PATH = Regex("""^[A-Za-z]:[\\/].*""")
    }
}

private data class ControlledWorkTaskParseResult(
    val value: ControlledWorkTaskDescriptor? = null,
    val error: NodeBridgeError? = null,
)

private object ControlledFetchPolicy {
    const val MAX_CONCURRENT_REQUESTS = 16
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val HARD_TIMEOUT_MS = 120_000L
    const val DEFAULT_MAX_RESPONSE_BYTES = 33_554_432L
    const val HARD_MAX_RESPONSE_BYTES = 67_108_864L
    const val HARD_MAX_REQUEST_BYTES = 67_108_864L
    const val DEFAULT_MAX_REDIRECTS = 5
    const val HARD_MAX_REDIRECTS = 10
}

private class NodeBridgeRateLimiter(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var windowStartedAt = 0L
    private var countInWindow = 0

    @Synchronized
    fun tryAcquire(maxPerSecond: Int): Boolean {
        if (maxPerSecond <= 0) {
            return false
        }
        val now = clock()
        if (windowStartedAt == 0L || now - windowStartedAt >= 1000L) {
            windowStartedAt = now
            countInWindow = 0
        }
        if (countInWindow >= maxPerSecond) {
            return false
        }
        countInWindow += 1
        return true
    }
}

private class ControlledFetchDiagnostics {
    private val activeRequestCount = AtomicInteger(0)
    private val maxActiveRequestCount = AtomicInteger(0)
    private val timeoutCount = AtomicInteger(0)
    private val rejectedByPolicyCount = AtomicInteger(0)
    private val abortedCount = AtomicInteger(0)
    private val completedCount = AtomicInteger(0)
    private val serviceDestroyCancellationCount = AtomicInteger(0)

    fun tryStartRequest(maxConcurrentRequests: Int): Boolean {
        while (true) {
            val current = activeRequestCount.get()
            if (current >= maxConcurrentRequests) {
                recordPolicyRejection()
                return false
            }
            if (activeRequestCount.compareAndSet(current, current + 1)) {
                recordMaxActive(current + 1)
                return true
            }
        }
    }

    fun finishRequest() {
        while (true) {
            val current = activeRequestCount.get()
            if (current <= 0) {
                return
            }
            if (activeRequestCount.compareAndSet(current, current - 1)) {
                return
            }
        }
    }

    fun recordCompleted() {
        completedCount.incrementAndGet()
    }

    fun recordTimeout() {
        timeoutCount.incrementAndGet()
    }

    fun recordAbort() {
        abortedCount.incrementAndGet()
    }

    fun recordPolicyRejection() {
        rejectedByPolicyCount.incrementAndGet()
    }

    fun recordServiceDestroyCancellation(activeCalls: Int) {
        if (activeCalls > 0) {
            serviceDestroyCancellationCount.addAndGet(activeCalls)
            abortedCount.addAndGet(activeCalls)
        }
        activeRequestCount.set(0)
    }

    fun nativeValues(): Map<String, String> = linkedMapOf(
        "embedded_script.fetch_active_request_count" to activeRequestCount.get().toString(),
        "embedded_script.fetch_max_active_request_count" to maxActiveRequestCount.get().toString(),
        "embedded_script.fetch_timeout_count" to timeoutCount.get().toString(),
        "embedded_script.fetch_rejected_by_policy_count" to rejectedByPolicyCount.get().toString(),
        "embedded_script.fetch_aborted_count" to abortedCount.get().toString(),
        "embedded_script.fetch_completed_count" to completedCount.get().toString(),
        "embedded_script.fetch_service_destroy_cancellation_count" to serviceDestroyCancellationCount.get().toString(),
    )

    private fun recordMaxActive(count: Int) {
        while (true) {
            val current = maxActiveRequestCount.get()
            if (count <= current) {
                return
            }
            if (maxActiveRequestCount.compareAndSet(current, count)) {
                return
            }
        }
    }
}

/** Controlled fetch reply: JSON plus an optional body descriptor (`bodyTransport: "pfd"`). */
private class ControlledFetchBridgeResult(
    val json: JSONObject,
    val binary: android.os.ParcelFileDescriptor?,
    val binaryByteCount: Long,
)

/** Streams a plugin-supplied descriptor as the OkHttp request body (M20.2 batch 14). */
private class DescriptorRequestBody(
    private val upload: NodeBridgeRequestUpload,
    private val contentType: okhttp3.MediaType?,
) : okhttp3.RequestBody() {
    override fun contentType(): okhttp3.MediaType? = contentType

    override fun contentLength(): Long = upload.byteCount

    override fun writeTo(sink: BufferedSink) {
        // Each attempt reopens the file from its start, so OkHttp may safely retry the call.
        upload.openStream().use { input ->
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = upload.byteCount
            while (remaining > 0L) {
                val read = input.read(chunk, 0, minOf(remaining, chunk.size.toLong()).toInt())
                if (read < 0) {
                    throw IOException(
                        "AutoJs6 controlled fetch request body ended after ${upload.byteCount - remaining} of ${upload.byteCount} bytes.",
                    )
                }
                sink.write(chunk, 0, read)
                remaining -= read
            }
        }
    }
}

private data class ControlledFetchRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeoutMs: Long,
    val maxResponseBytes: Long,
    val maxRedirects: Int,
    /** Descriptor-delivered body (`bodyTransport: "pfd"`); streamed instead of [body]. */
    val upload: NodeBridgeRequestUpload? = null,
) {
    fun toOkHttpRequest(): okhttp3.Request {
        val builder = okhttp3.Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank()) {
                builder.addHeader(name, value)
            }
        }
        val requestBody = upload?.let { DescriptorRequestBody(it, contentType()) } ?: body?.toRequestBody(contentType())
        builder.method(method, requestBody)
        return builder.build()
    }

    /** Inline reply: the body travels as base64 inside the response JSON (plugins that do not ask for `binary`). */
    fun toBridgeResponse(response: okhttp3.Response): JSONObject = toBridgeResponse(response, null).json

    /**
     * With [binaryDeliveryDir] (the plugin sent `binary: true`) the body is written to a
     * private cache file and handed over as a read-only descriptor together with
     * `"bodyTransport": "pfd"`, so the reply JSON stays far below the Binder transaction
     * size whatever the body size. Empty bodies stay inline and carry no descriptor.
     */
    fun toBridgeResponse(response: okhttp3.Response, binaryDeliveryDir: File?): ControlledFetchBridgeResult {
        val headersJson = JSONArray()
        response.headers.forEach { header ->
            headersJson.put(JSONArray().put(header.first).put(header.second))
        }
        val result = JSONObject().apply {
            put("url", response.request.url.toString())
            put("status", response.code)
            put("statusText", response.message)
            put("headers", headersJson)
            put("redirected", response.isRedirect)
            put("maxRedirects", maxRedirects)
        }
        val stream = response.body?.byteStream()
        if (binaryDeliveryDir == null) {
            val bytes = stream?.use { input -> readBoundedBytes(input, maxResponseBytes) } ?: ByteArray(0)
            result.put("bodyBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            result.put("bodyBytes", bytes.size)
            return ControlledFetchBridgeResult(result, null, 0L)
        }
        val file = File.createTempFile("node-fetch-body-", ".bin", binaryDeliveryDir)
        try {
            val byteCount = stream?.use { input ->
                FileOutputStream(file).use { output -> copyBounded(input, output, maxResponseBytes) }
            } ?: 0L
            result.put("bodyBytes", byteCount)
            if (byteCount <= 0L) {
                result.put("bodyBase64", "")
                return ControlledFetchBridgeResult(result, null, 0L)
            }
            result.put("bodyTransport", "pfd")
            return ControlledFetchBridgeResult(
                result,
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY),
                byteCount,
            )
        } finally {
            // The open descriptor keeps the unlinked file readable until the plugin has mapped it.
            file.delete()
        }
    }

    private fun copyBounded(input: InputStream, output: FileOutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                return total
            }
            if (total + read > maxBytes) {
                throw ControlledFetchException(
                    NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE,
                    "AutoJs6 controlled fetch response exceeded ${maxBytes} bytes.",
                )
            }
            output.write(buffer, 0, read)
            total += read
        }
    }

    private fun contentType() = headers
        .firstOrNull { it.first.equals("content-type", ignoreCase = true) }
        ?.second
        ?.toMediaTypeOrNull()

    private fun readBoundedBytes(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                return output.toByteArray()
            }
            if (output.size().toLong() + read > maxBytes) {
                throw ControlledFetchException(
                    NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE,
                    "AutoJs6 controlled fetch response exceeded ${maxBytes} bytes.",
                )
            }
            output.write(buffer, 0, read)
        }
    }

    companion object {
        fun fromJson(request: NodeBridgeRequest, payload: JSONObject): ControlledFetchParseResult {
            val url = payload.optString("url", "").trim()
            val protocol = runCatching { URL(url).protocol.lowercase() }.getOrNull()
            if (url.isBlank() || protocol !in setOf("http", "https")) {
                return ControlledFetchParseResult(
                    error = NodeBridgeError.network(
                        message = "fetch.request only accepts http and https URLs.",
                        code = NodeRuntimeErrorCodes.NETWORK_INVALID_URL,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val method = payload.optString("method", "GET").uppercase()
            if (method !in setOf("GET", "POST")) {
                return ControlledFetchParseResult(
                    error = NodeBridgeError.network(
                        message = "fetch.request currently supports only GET and POST.",
                        code = NodeRuntimeErrorCodes.NETWORK_PERMISSION_DENIED,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val descriptorBody = payload.optString("bodyTransport", "") == "pfd"
            val upload = request.upload.takeIf { descriptorBody }
            if (descriptorBody) {
                if (upload == null) {
                    return ControlledFetchParseResult(
                        error = NodeBridgeError.invalidRequest(
                            message = "fetch.request declared a descriptor body but no descriptor was attached.",
                            module = request.module,
                            method = request.method,
                        ),
                    )
                }
                val declaredBytes = parsePositiveLong(payload.opt("bodyBytes"), -1L)
                if (declaredBytes != upload.byteCount) {
                    return ControlledFetchParseResult(
                        error = NodeBridgeError.invalidRequest(
                            message = "fetch.request bodyBytes ($declaredBytes) does not match the attached descriptor (${upload.byteCount} bytes).",
                            module = request.module,
                            method = request.method,
                        ),
                    )
                }
                if (upload.byteCount > ControlledFetchPolicy.HARD_MAX_REQUEST_BYTES) {
                    return ControlledFetchParseResult(
                        error = NodeBridgeError.network(
                            message = "fetch.request body exceeded ${ControlledFetchPolicy.HARD_MAX_REQUEST_BYTES} bytes.",
                            code = NodeRuntimeErrorCodes.NETWORK_POLICY_DENIED,
                            module = request.module,
                            method = request.method,
                        ),
                    )
                }
            }
            val body = if (upload == null) parseBody(payload) else null
            if (method == "GET" && (upload != null || (body != null && body.isNotEmpty()))) {
                return ControlledFetchParseResult(
                    error = NodeBridgeError.network(
                        message = "fetch.request GET must not include a body.",
                        code = NodeRuntimeErrorCodes.NETWORK_PERMISSION_DENIED,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            return ControlledFetchParseResult(
                value = ControlledFetchRequest(
                    url = url,
                    method = method,
                    headers = parseHeaders(payload.opt("headers")),
                    body = if (method == "POST" && upload == null) body ?: ByteArray(0) else null,
                    timeoutMs = parsePositiveLong(payload.opt("timeoutMs"), request.timeoutMs)
                        .coerceIn(1L, ControlledFetchPolicy.HARD_TIMEOUT_MS),
                    maxResponseBytes = parsePositiveLong(payload.opt("maxResponseBytes"), ControlledFetchPolicy.DEFAULT_MAX_RESPONSE_BYTES)
                        .coerceIn(0L, ControlledFetchPolicy.HARD_MAX_RESPONSE_BYTES),
                    maxRedirects = parsePositiveInt(payload.opt("maxRedirects"), ControlledFetchPolicy.DEFAULT_MAX_REDIRECTS)
                        .coerceIn(0, ControlledFetchPolicy.HARD_MAX_REDIRECTS),
                    upload = if (method == "POST") upload else null,
                ),
            )
        }

        private fun parseHeaders(value: Any?): List<Pair<String, String>> {
            return when (value) {
                is JSONArray -> (0 until value.length()).mapNotNull { index ->
                    val pair = value.optJSONArray(index) ?: return@mapNotNull null
                    val name = pair.optString(0, "").trim()
                    val headerValue = pair.optString(1, "")
                    if (name.isBlank()) null else name to headerValue
                }
                is JSONObject -> value.keys().asSequence().mapNotNull { name ->
                    val headerValue = value.optString(name, "")
                    if (name.isBlank()) null else name to headerValue
                }.toList()
                else -> emptyList()
            }
        }

        private fun parseBody(payload: JSONObject): ByteArray? {
            if (payload.has("bodyBase64") && !payload.isNull("bodyBase64")) {
                return Base64.decode(payload.optString("bodyBase64"), Base64.DEFAULT)
            }
            if (payload.has("bodyText") && !payload.isNull("bodyText")) {
                return payload.optString("bodyText").toByteArray(Charsets.UTF_8)
            }
            return null
        }

        private fun parsePositiveLong(value: Any?, fallback: Long): Long {
            val parsed = when (value) {
                is Int -> value.toLong()
                is Long -> value
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            return parsed?.takeIf { it >= 0L } ?: fallback
        }

        private fun parsePositiveInt(value: Any?, fallback: Int): Int {
            val parsed = when (value) {
                is Int -> value
                is Long -> value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                is Number -> {
                    val doubleValue = value.toDouble()
                    if (!doubleValue.isFinite()) {
                        null
                    } else {
                        doubleValue.toInt()
                    }
                }
                is String -> value.toIntOrNull()
                else -> null
            }
            return parsed?.takeIf { it >= 0 } ?: fallback
        }
    }
}

private data class ControlledFetchParseResult(
    val value: ControlledFetchRequest? = null,
    val error: NodeBridgeError? = null,
)

private object NodeStorageBridgePolicy {
    const val MAX_STORE_NAME_LENGTH = 128
    const val MAX_KEY_LENGTH = 256
    const val MAX_VALUE_BYTES = 64 * 1024
    const val MAX_STORE_BYTES = 256 * 1024
    private const val PREFS_PREFIX = "autojs.node.storage"

    fun preferencesName(packageName: String, cwd: String, storeName: String): String {
        val packagePart = sanitize(packageName.ifBlank { "unknown" })
        val cwdPart = sha256(cwd.ifBlank { "default" }).take(16)
        val storePart = sanitize(storeName).take(64).ifBlank { "store" }
        return "$PREFS_PREFIX.$packagePart.$cwdPart.$storePart"
    }

    private fun sanitize(value: String): String {
        return value.map { char ->
            when {
                char.isLetterOrDigit() -> char.lowercaseChar()
                char == '.' || char == '_' || char == '-' -> char
                else -> '_'
            }
        }.joinToString("")
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> String.format(Locale.US, "%02x", byte.toInt() and 0xff) }
    }
}

private class StorageBridgeException(val bridgeError: NodeBridgeError) : RuntimeException(bridgeError.message)

private data class ControlledDatabaseHandle(
    val id: String,
    val name: String,
    val file: File,
    val database: SQLiteDatabase,
    val resource: NodeBridgeResourceHandle,
) {
    fun exec(request: NodeBridgeRequest, sql: String, params: List<Any?>) {
        ensureOpen(request)
        val statement = database.compileStatement(sql)
        try {
            NodeDatabaseBridgePolicy.bind(statement, params)
            statement.execute()
        } finally {
            statement.close()
        }
    }

    fun run(request: NodeBridgeRequest, sql: String, params: List<Any?>): JSONObject {
        ensureOpen(request)
        val command = NodeDatabaseBridgePolicy.command(sql)
        val statement = database.compileStatement(sql)
        try {
            NodeDatabaseBridgePolicy.bind(statement, params)
            return when (command) {
                "INSERT", "REPLACE" -> {
                    // Keep the statement and changes() on the same connection, including with WAL.
                    val ownTransaction = !database.inTransaction()
                    if (ownTransaction) database.beginTransaction()
                    var failed = false
                    try {
                        val insertId = statement.executeInsert()
                        val changed = DatabaseUtils.longForQuery(database, "SELECT changes()", null)
                        val result = JSONObject().put("rowsAffected", changed)
                            .put("insertId", if (changed > 0 && insertId >= 0L) insertId else JSONObject.NULL)
                        if (ownTransaction) database.setTransactionSuccessful()
                        result
                    } catch (error: Throwable) {
                        failed = true
                        throw error
                    } finally {
                        if (ownTransaction) {
                            if (failed) {
                                // This transaction only pins the connection. Preserve SQLite's
                                // autocommit conflict semantics: OR FAIL keeps earlier rows, while
                                // OR ROLLBACK may already have ended the native transaction.
                                runCatching { database.setTransactionSuccessful() }
                                runCatching { database.endTransaction() }
                            } else database.endTransaction()
                        }
                    }
                }
                "UPDATE", "DELETE" -> JSONObject()
                    .put("rowsAffected", statement.executeUpdateDelete())
                    .put("insertId", JSONObject.NULL)
                else -> {
                    statement.execute()
                    JSONObject()
                        .put("rowsAffected", JSONObject.NULL)
                        .put("insertId", JSONObject.NULL)
                }
            }
        } finally {
            statement.close()
        }
    }

    fun query(
        request: NodeBridgeRequest,
        sql: String,
        params: List<Any?>,
    ): JSONObject? {
        ensureOpen(request)
        val cursor = queryCursor(sql, params)
        try {
            if (!cursor.moveToFirst()) {
                return null
            }
            val row = NodeDatabaseBridgePolicy.cursorRow(cursor)
            val bytes = row.toString().toByteArray(Charsets.UTF_8).size
            if (bytes > NodeDatabaseBridgePolicy.MAX_RESULT_BYTES) {
                throw DatabaseBridgeException(
                    NodeBridgeError.resourceLimit(
                        message = "AutoJs6 database result exceeded ${NodeDatabaseBridgePolicy.MAX_RESULT_BYTES} bytes.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            return row
        } finally {
            cursor.close()
        }
    }

    fun queryAll(request: NodeBridgeRequest, sql: String, params: List<Any?>): JSONArray {
        val rows = queryRows(request, sql, params, maxRows = NodeDatabaseBridgePolicy.MAX_RESULT_ROWS)
        return JSONArray().apply { rows.forEach(::put) }
    }

    private fun queryRows(
        request: NodeBridgeRequest,
        sql: String,
        params: List<Any?>,
        maxRows: Int,
    ): List<JSONObject> {
        ensureOpen(request)
        val rows = mutableListOf<JSONObject>()
        val cursor = queryCursor(sql, params)
        try {
            var projectedBytes = 2
            while (cursor.moveToNext()) {
                if (rows.size >= maxRows) {
                    throw DatabaseBridgeException(
                        NodeBridgeError.resourceLimit(
                            message = "AutoJs6 database result exceeded $maxRows rows.",
                            module = request.module,
                            method = request.method,
                        ),
                    )
                }
                val row = NodeDatabaseBridgePolicy.cursorRow(cursor)
                projectedBytes += row.toString().toByteArray(Charsets.UTF_8).size + 1
                if (projectedBytes > NodeDatabaseBridgePolicy.MAX_RESULT_BYTES) {
                    throw DatabaseBridgeException(
                        NodeBridgeError.resourceLimit(
                            message = "AutoJs6 database result exceeded ${NodeDatabaseBridgePolicy.MAX_RESULT_BYTES} bytes.",
                            module = request.module,
                            method = request.method,
                        ),
                    )
                }
                rows += row
            }
        } finally {
            cursor.close()
        }
        return rows
    }

    private fun queryCursor(sql: String, params: List<Any?>): Cursor = database.rawQueryWithFactory(
        { _, driver, editTable, query ->
            NodeDatabaseBridgePolicy.bind(query, params)
            SQLiteCursor(driver, editTable, query)
        }, sql, emptyArray(), "",
    )

    private fun ensureOpen(request: NodeBridgeRequest) {
        if (!database.isOpen) {
            throw DatabaseBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 database handle is closed.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
    }

}

private object NodeDatabaseBridgePolicy {
    const val MAX_OPEN_HANDLES = 4
    const val MAX_NAME_LENGTH = 64
    const val MAX_SQL_BYTES = 16 * 1024
    const val MAX_PARAMS = 128
    const val MAX_TRANSACTION_OPERATIONS = 64
    const val MAX_RESULT_ROWS = 200
    const val MAX_RESULT_BYTES = 256 * 1024

    private const val DATABASE_DIR = "node-databases"
    private val namePattern = Regex("^[A-Za-z0-9_.-]+$")
    private val forbiddenSqlPattern = Regex("(?i)(^|[^A-Za-z0-9_])(load_extension|attach|detach)($|[^A-Za-z0-9_])")

    fun openName(request: NodeBridgeRequest, value: Any?): String {
        val rawName = when (value) {
            is String -> value
            is JSONObject -> value.optString("name", value.optString("path", ""))
            else -> ""
        }.trim()
        if (rawName.isBlank() || rawName.length > MAX_NAME_LENGTH || '\u0000' in rawName) {
            throw DatabaseBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 database.open requires a non-empty database name up to $MAX_NAME_LENGTH characters.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        val normalized = rawName.replace('\\', '/')
        if (
            normalized.startsWith("/") ||
            normalized.contains("/") ||
            normalized.contains(":") ||
            normalized == "." ||
            normalized == ".." ||
            normalized.contains("..") ||
            !namePattern.matches(normalized)
        ) {
            throw DatabaseBridgeException(
                NodeBridgeError.permissionDenied(
                    message = "AutoJs6 database.open only accepts scoped database names, not paths.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        return normalized
    }

    fun databaseFile(context: Context, engineInfo: NodeBridgeEngineInfo, name: String): File {
        val packagePart = sanitize(engineInfo.packageName.ifBlank { context.packageName }).ifBlank { "unknown" }
        val cwdPart = sha256(engineInfo.cwd.ifBlank { "default" }).take(16)
        val base = File(context.filesDir, "$DATABASE_DIR/$packagePart/$cwdPart").canonicalFile
        val fileName = sanitize(name).ifBlank { "database" }.let { sanitized ->
            if (sanitized.endsWith(".db", ignoreCase = true)) sanitized else "$sanitized.db"
        }
        val candidate = File(base, fileName).canonicalFile
        val basePath = base.path.trimEnd(File.separatorChar)
        val candidatePath = candidate.path
        if (candidatePath != basePath && !candidatePath.startsWith(basePath + File.separator)) {
            throw DatabaseBridgeException(
                NodeBridgeError.permissionDenied(
                    message = "AutoJs6 database path escaped the scoped database directory.",
                    module = NodeBridgeModules.DATABASE,
                    method = "open",
                ),
            )
        }
        return candidate
    }

    fun handleId(request: NodeBridgeRequest, value: Any?): String {
        val text = (value as? String).orEmpty().trim()
        if (text.isBlank() || '\u0000' in text || text.length > 128) {
            throw DatabaseBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 database handle id is invalid.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        return text
    }

    fun sql(request: NodeBridgeRequest, value: String): String {
        val text = value.trim()
        val bytes = text.toByteArray(Charsets.UTF_8).size
        if (text.isBlank() || '\u0000' in text || bytes > MAX_SQL_BYTES) {
            throw DatabaseBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 database SQL must be non-empty and at most $MAX_SQL_BYTES bytes.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        if (forbiddenSqlPattern.containsMatchIn(text)) {
            throw DatabaseBridgeException(
                NodeBridgeError.permissionDenied(
                    message = "AutoJs6 database SQL denied by policy.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        return text
    }

    fun params(request: NodeBridgeRequest, value: Any?): List<Any?> {
        if (value == null || value == JSONObject.NULL) {
            return emptyList()
        }
        val array = value as? JSONArray
            ?: throw DatabaseBridgeException(
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 database SQL parameters must be an array.",
                    module = request.module,
                    method = request.method,
                ),
            )
        if (array.length() > MAX_PARAMS) {
            throw DatabaseBridgeException(
                NodeBridgeError.resourceLimit(
                    message = "AutoJs6 database SQL parameters exceeded $MAX_PARAMS entries.",
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        return (0 until array.length()).map { index ->
            when (val item = array.opt(index)) {
                null, JSONObject.NULL -> null
                is String -> item
                is Boolean -> item
                is Int -> item.toLong()
                is Long -> item
                is Number -> {
                    val number = item.toDouble()
                    if (!number.isFinite()) {
                        throw DatabaseBridgeException(
                            NodeBridgeError.invalidRequest(
                                message = "AutoJs6 database SQL parameter must be finite.",
                                module = request.module,
                                method = request.method,
                            ),
                        )
                    }
                    number
                }
                else -> throw DatabaseBridgeException(
                    NodeBridgeError.invalidRequest(
                        message = "AutoJs6 database SQL parameters must be JSON primitive values.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
        }
    }

    fun bind(statement: SQLiteProgram, params: List<Any?>) {
        params.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.bindNull(position)
                is Boolean -> statement.bindLong(position, if (value) 1L else 0L)
                is Long -> statement.bindLong(position, value)
                is Int -> statement.bindLong(position, value.toLong())
                is Number -> statement.bindDouble(position, value.toDouble())
                else -> statement.bindString(position, value.toString())
            }
        }
    }

    fun cursorRow(cursor: Cursor): JSONObject {
        val row = JSONObject()
        for (index in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(index)
            val value: Any = when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                Cursor.FIELD_TYPE_BLOB -> JSONObject()
                    .put("base64", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
                else -> cursor.getString(index) ?: JSONObject.NULL
            }
            row.put(name, value)
        }
        return row
    }

    fun command(sql: String): String {
        var text = sql.trimStart()
        while (true) {
            text = when {
                text.startsWith("--") -> text.substringAfter('\n', "").trimStart()
                text.startsWith("/*") && text.indexOf("*/", 2) >= 0 -> text.substring(text.indexOf("*/", 2) + 2).trimStart()
                else -> break
            }
        }
        return text
            .takeWhile { !it.isWhitespace() && it != '(' }
            .uppercase(Locale.US)
    }

    private fun sanitize(value: String): String {
        return value.map { char ->
            when {
                char.isLetterOrDigit() -> char.lowercaseChar()
                char == '.' || char == '_' || char == '-' -> char
                else -> '_'
            }
        }.joinToString("")
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> String.format(Locale.US, "%02x", byte.toInt() and 0xff) }
    }
}

private class DatabaseBridgeException(val bridgeError: NodeBridgeError) : RuntimeException(bridgeError.message)

private data class ControlledImageHandle(
    val id: String,
    val bitmap: Bitmap,
    val resource: NodeBridgeResourceHandle,
    val nativeMat: org.opencv.core.Mat? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("__autojs6ImageHandle", id)
        .put("channels", nativeMat?.channels() ?: 4)
        .put("width", bitmap.width)
        .put("height", bitmap.height)

    fun ocrBitmap(options: JSONObject): ControlledOcrInput = processingBitmap(options, "OCR")

    fun barcodeBitmap(options: JSONObject): ControlledOcrInput = processingBitmap(options, "barcode")

    private fun processingBitmap(options: JSONObject, apiName: String): ControlledOcrInput {
        check(!bitmap.isRecycled) { "AutoJs6 image handle has been recycled: $id" }
        val region = ControlledOcrRegion.fromOptions(options, apiName)
            ?: return ControlledOcrInput(bitmap = bitmap)
        require(region.x >= 0 && region.y >= 0 && region.width > 0 && region.height > 0) {
            "AutoJs6 $apiName region must use non-negative x/y and positive width/height."
        }
        require(region.x + region.width <= bitmap.width && region.y + region.height <= bitmap.height) {
            "AutoJs6 $apiName region must stay inside the image bounds."
        }
        return ControlledOcrInput(
            bitmap = Bitmap.createBitmap(bitmap, region.x, region.y, region.width, region.height),
            offsetX = region.x,
            offsetY = region.y,
            recycleAfterUse = true,
        )
    }
}

private data class ControlledOcrInput(
    val bitmap: Bitmap,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val recycleAfterUse: Boolean = false,
) {
    fun recycleIfNeeded() {
        if (recycleAfterUse && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

private data class ControlledOcrRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    companion object {
        fun fromOptions(options: JSONObject, apiName: String = "OCR"): ControlledOcrRegion? {
            if (!options.has("region") || options.isNull("region")) {
                return null
            }
            val value: Any? = options.opt("region")
            return when (value) {
                is JSONObject -> ControlledOcrRegion(
                    x = finiteInt(value.opt("x"), "region.x", apiName),
                    y = finiteInt(value.opt("y"), "region.y", apiName),
                    width = finiteInt(value.opt("width"), "region.width", apiName),
                    height = finiteInt(value.opt("height"), "region.height", apiName),
                )
                is JSONArray -> {
                    require(value.length() >= 4) { "AutoJs6 $apiName region array must contain x, y, width, and height." }
                    ControlledOcrRegion(
                        x = finiteInt(value.opt(0), "region[0]", apiName),
                        y = finiteInt(value.opt(1), "region[1]", apiName),
                        width = finiteInt(value.opt(2), "region[2]", apiName),
                        height = finiteInt(value.opt(3), "region[3]", apiName),
                    )
                }
                else -> throw IllegalArgumentException("AutoJs6 $apiName region must be an object or array.")
            }
        }

        private fun finiteInt(value: Any?, label: String, apiName: String): Int {
            val number = when (value) {
                is Int -> value.toDouble()
                is Long -> value.toDouble()
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            require(number != null && number.isFinite()) { "AutoJs6 $apiName $label must be a finite number." }
            return number.toInt()
        }
    }
}

private class ControlledOcrOperation(
    val resource: NodeBridgeResourceHandle,
) {
    private val completed = AtomicBoolean(false)

    @Volatile
    var timeoutRunnable: Runnable? = null

    fun finish(): Boolean = completed.compareAndSet(false, true)

    fun cancel(reason: String) {
        if (finish()) {
            resource.close(reason)
        }
    }
}

private class ControlledOcrDiagnostics {
    private val dispatchCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val serviceDestroyCancellationCount = AtomicInteger(0)

    fun recordDispatch() {
        dispatchCount.incrementAndGet()
    }

    fun recordFailure() {
        failedCount.incrementAndGet()
    }

    fun recordServiceDestroyCancellation(count: Int) {
        if (count > 0) {
            serviceDestroyCancellationCount.addAndGet(count)
        }
    }

    fun nativeValues(): Map<String, String> = linkedMapOf(
        "embedded_script.ocr_live_dispatch_count" to dispatchCount.get().toString(),
        "embedded_script.ocr_live_dispatch_failed_count" to failedCount.get().toString(),
        "embedded_script.ocr_service_destroy_cancel_count" to serviceDestroyCancellationCount.get().toString(),
    )
}

private fun List<PluginOcrResult>.toOcrResultsJson(offsetX: Int, offsetY: Int): String {
    val results = JSONArray()
    forEach { result ->
        val bounds = result.bounds
        val item = JSONObject()
            .put("text", result.text.orEmpty())
            .put("confidence", result.confidence.takeIf { it.isFinite() }?.toDouble() ?: JSONObject.NULL)
        if (bounds != null) {
            item.put(
                "bounds",
                JSONObject()
                    .put("left", bounds.left + offsetX)
                    .put("top", bounds.top + offsetY)
                    .put("right", bounds.right + offsetX)
                    .put("bottom", bounds.bottom + offsetY),
            )
        }
        results.put(item)
    }
    return results.toString()
}

private data class ControlledBarcodeOptions(
    val formats: IntArray,
    val maxResults: Int,
    val enableAllPotentialBarcodes: Boolean,
) {
    fun toPluginOptions(): BarcodeOptions {
        return BarcodeOptions().apply {
            formats = this@ControlledBarcodeOptions.formats
            maxResults = this@ControlledBarcodeOptions.maxResults
            enableAllPotentialBarcodes = this@ControlledBarcodeOptions.enableAllPotentialBarcodes
        }
    }

    companion object {
        private const val MAX_RESULTS = 128
        private const val MAX_FORMATS = 16

        fun fromOptions(options: JSONObject): ControlledBarcodeOptions {
            val qrOnly = options.optBoolean("qrOnly", false)
            val parsedFormats = parseFormats(options)
            if (qrOnly && parsedFormats.any { it != BarcodeFormats.FORMAT_QR_CODE }) {
                throw IllegalArgumentException("AutoJs6 barcode qrOnly mode accepts only QR_CODE format.")
            }
            val effectiveFormats = if (qrOnly) {
                intArrayOf(BarcodeFormats.FORMAT_QR_CODE)
            } else {
                parsedFormats
            }
            return ControlledBarcodeOptions(
                formats = effectiveFormats,
                maxResults = parseMaxResults(options),
                enableAllPotentialBarcodes = options.optBoolean("enableAllPotentialBarcodes", false),
            )
        }

        private fun parseFormats(options: JSONObject): IntArray {
            val rawFormats = when {
                options.has("formats") && !options.isNull("formats") -> options.opt("formats")
                options.has("format") && !options.isNull("format") -> options.opt("format")
                else -> return intArrayOf()
            }
            val values = when (rawFormats) {
                is JSONArray -> (0 until rawFormats.length()).map { index -> rawFormats.opt(index) }
                else -> listOf(rawFormats)
            }
            require(values.size <= MAX_FORMATS) { "AutoJs6 barcode formats must contain at most $MAX_FORMATS entries." }
            return values
                .map(::parseFormat)
                .distinct()
                .toIntArray()
        }

        private fun parseFormat(value: Any?): Int {
            val format = when (value) {
                is Int -> value
                is Long -> value.toInt()
                is Number -> {
                    val number = value.toDouble()
                    require(number.isFinite()) { "AutoJs6 barcode format number must be finite." }
                    number.toInt()
                }
                is String -> {
                    val name = normalizeFormatName(value)
                    BARCODE_FORMATS_BY_NAME[name]
                        ?: throw IllegalArgumentException("AutoJs6 barcode format is not supported: $value")
                }
                else -> throw IllegalArgumentException("AutoJs6 barcode format must be a number or string.")
            }
            require(format == BarcodeFormats.FORMAT_ALL_FORMATS || BARCODE_FORMAT_NAMES_BY_VALUE.containsKey(format)) {
                "AutoJs6 barcode format is not supported: $value"
            }
            return format
        }

        private fun parseMaxResults(options: JSONObject): Int {
            if (!options.has("maxResults") || options.isNull("maxResults")) {
                return MAX_RESULTS
            }
            val value: Any? = options.opt("maxResults")
            val number = when (value) {
                is Int -> value.toDouble()
                is Long -> value.toDouble()
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            require(number != null && number.isFinite()) { "AutoJs6 barcode maxResults must be a finite number." }
            val maxResults = number.toInt()
            require(maxResults in 1..MAX_RESULTS) { "AutoJs6 barcode maxResults must be between 1 and $MAX_RESULTS." }
            return maxResults
        }

        private fun normalizeFormatName(value: String): String {
            val normalized = value
                .trim()
                .uppercase(Locale.US)
                .replace(Regex("[^A-Z0-9]+"), "_")
                .trim('_')
            return when {
                normalized == "PDF417" -> "PDF_417"
                normalized == "QRCODE" -> "QR_CODE"
                normalized.startsWith("FORMAT_") -> normalizeFormatName(normalized.removePrefix("FORMAT_"))
                else -> normalized
            }
        }
    }
}

private class ControlledBarcodeOperation(
    val resource: NodeBridgeResourceHandle,
) {
    private val completed = AtomicBoolean(false)

    @Volatile
    var timeoutRunnable: Runnable? = null

    @Volatile
    var job: Job? = null

    fun finish(): Boolean = completed.compareAndSet(false, true)

    fun cancel(reason: String) {
        if (finish()) {
            job?.cancel()
            resource.close(reason)
        }
    }
}

private class ControlledBarcodeDiagnostics {
    private val dispatchCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val serviceDestroyCancellationCount = AtomicInteger(0)

    fun recordDispatch() {
        dispatchCount.incrementAndGet()
    }

    fun recordFailure() {
        failedCount.incrementAndGet()
    }

    fun recordServiceDestroyCancellation(count: Int) {
        if (count > 0) {
            serviceDestroyCancellationCount.addAndGet(count)
        }
    }

    fun nativeValues(): Map<String, String> = linkedMapOf(
        "embedded_script.barcode_live_dispatch_count" to dispatchCount.get().toString(),
        "embedded_script.barcode_live_dispatch_failed_count" to failedCount.get().toString(),
        "embedded_script.barcode_service_destroy_cancel_count" to serviceDestroyCancellationCount.get().toString(),
    )
}

private fun List<BarcodeResult>.toBarcodeResponseJson(method: String, maxResults: Int, offsetX: Int, offsetY: Int): String {
    val snapshots = take(maxResults).map { barcode -> barcode.toBarcodeSnapshotJson(offsetX, offsetY) }
    return when (method) {
        "detect" -> snapshots.firstOrNull()?.toString() ?: "null"
        "detectAll" -> JSONArray().apply { snapshots.forEach(::put) }.toString()
        "recognizeText" -> barcodeJsonStringOrNull(snapshots.firstOrNull()?.optStringOrNull("rawValue"))
        "recognizeTexts" -> JSONArray().apply {
            snapshots.mapNotNull { snapshot -> snapshot.optStringOrNull("rawValue") }.forEach(::put)
        }.toString()
        else -> "null"
    }
}

private fun BarcodeResult.toBarcodeSnapshotJson(offsetX: Int, offsetY: Int): JSONObject {
    val snapshot = JSONObject()
        .put("schema", BARCODE_SNAPSHOT_SCHEMA)
        .put("rawValue", barcodeJsonNullableString(rawValue))
        .put("displayValue", barcodeJsonNullableString(displayValue))
        .put("format", format)
        .put("formatName", BARCODE_FORMAT_NAMES_BY_VALUE[format] ?: "ALL")
        .put("valueType", valueType)
        .put("valueTypeName", BARCODE_VALUE_TYPE_NAMES_BY_VALUE[valueType] ?: "UNKNOWN")
    boundingBox?.let { bounds ->
        snapshot.put(
            "boundingBox",
            JSONObject()
                .put("left", bounds.left + offsetX)
                .put("top", bounds.top + offsetY)
                .put("right", bounds.right + offsetX)
                .put("bottom", bounds.bottom + offsetY)
                .put("width", bounds.width())
                .put("height", bounds.height()),
        )
    }
    cornerPoints?.takeIf { it.isNotEmpty() }?.let { points ->
        snapshot.put(
            "cornerPoints",
            JSONArray().apply {
                points.toList().chunked(2).take(8).forEach { point ->
                    if (point.size == 2) {
                        put(
                            JSONObject()
                                .put("x", point[0] + offsetX)
                                .put("y", point[1] + offsetY),
                        )
                    }
                }
            },
        )
    }
    extras?.getString(BarcodeResultExtraKeys.STRUCTURED_JSON)
        ?.takeIf { it.isNotBlank() }
        ?.let { structuredJson ->
            runCatching { JSONObject(structuredJson) }.getOrNull()
        }
        ?.let { structured -> snapshot.put("structured", structured) }
    return snapshot
}

private fun JSONObject.optStringOrNull(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}

private fun barcodeJsonNullableString(value: String?): Any {
    if (value == null) {
        return JSONObject.NULL
    }
    require(value.toByteArray(Charsets.UTF_8).size <= BARCODE_MAX_RAW_VALUE_BYTES) {
        "AutoJs6 barcode raw/display value exceeded $BARCODE_MAX_RAW_VALUE_BYTES bytes."
    }
    return value
}

private fun barcodeJsonStringOrNull(value: String?): String = value?.let { JSONObject.quote(it) } ?: "null"

private const val BARCODE_SNAPSHOT_SCHEMA = "autojs6-node-barcode-snapshot-v1"
private const val BARCODE_MAX_RAW_VALUE_BYTES = 8192
private const val ENGINE_MLKIT_BARCODE = "mlkit-barcode"

private val BARCODE_FORMATS_BY_NAME: Map<String, Int> = BarcodeFormats.BY_NAME
private val BARCODE_FORMAT_NAMES_BY_VALUE: Map<Int, String> = BarcodeFormats.NAME_BY_VALUE
private val BARCODE_VALUE_TYPE_NAMES_BY_VALUE: Map<Int, String> = BarcodeValueTypes.NAME_BY_VALUE

private data class ControlledNotificationDescriptor(
    val id: Int,
    val title: String,
    val text: String,
    val channel: String,
    val channelName: String,
    val ongoing: Boolean,
    val priority: Int,
) {
    fun toNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setLocalOnly(true)
            .build()
    }

    companion object {
        fun fromJson(
            request: NodeBridgeRequest,
            payload: JSONObject,
            fallbackId: Int,
            defaultChannelName: String,
        ): ControlledNotificationDescriptor {
            val title = optionalString(request, payload, "title", ControlledNotificationPolicy.MAX_TITLE_LENGTH)
                ?: "AutoJs6 Node"
            val text = optionalString(request, payload, "text", ControlledNotificationPolicy.MAX_TEXT_LENGTH).orEmpty()
            if (title.isBlank() && text.isBlank()) {
                throw NotificationBridgeException(
                    NodeBridgeError.invalidRequest(
                        message = "notifications.notify requires title or text.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val id = parseOptionalNotificationId(request, payload.opt("id")) ?: fallbackId
            val channel = optionalString(request, payload, "channel", ControlledNotificationPolicy.MAX_CHANNEL_LENGTH)
                ?.takeIf { it.isNotBlank() }
                ?: ControlledNotificationPolicy.DEFAULT_CHANNEL_ID
            val channelName = optionalString(request, payload, "channelName", ControlledNotificationPolicy.MAX_TITLE_LENGTH)
                ?.takeIf { it.isNotBlank() }
                ?: defaultChannelName
            val priority = parsePriority(request, payload.opt("priority"))
            return ControlledNotificationDescriptor(
                id = id,
                title = title,
                text = text,
                channel = channel,
                channelName = channelName,
                ongoing = payload.optBoolean("ongoing", false),
                priority = priority,
            )
        }

        fun parseNotificationIdArg(request: NodeBridgeRequest, args: JSONArray, index: Int): Int {
            return parseOptionalNotificationId(request, args.opt(index))
                ?: throw NotificationBridgeException(
                    NodeBridgeError.invalidRequest(
                        message = "notifications.cancel requires a positive integer id.",
                        module = request.module,
                        method = request.method,
                    ),
                )
        }

        private fun optionalString(
            request: NodeBridgeRequest,
            payload: JSONObject,
            name: String,
            maxLength: Int,
        ): String? {
            if (!payload.has(name) || payload.isNull(name)) {
                return null
            }
            val value = payload.opt(name)
            if (value !is String || value.length > maxLength || '\u0000' in value) {
                throw NotificationBridgeException(
                    NodeBridgeError.invalidRequest(
                        message = "notifications.notify option $name must be a string up to $maxLength characters.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            return value.trim()
        }

        private fun parseOptionalNotificationId(request: NodeBridgeRequest, value: Any?): Int? {
            if (value == null || value == JSONObject.NULL) {
                return null
            }
            val id = when (value) {
                is Int -> value
                is Long -> value.takeIf { it <= Int.MAX_VALUE }?.toInt()
                is Number -> value.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 && it <= Int.MAX_VALUE }?.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
            if (id == null || id <= 0) {
                throw NotificationBridgeException(
                    NodeBridgeError.invalidRequest(
                        message = "notifications notify/cancel id must be a positive integer.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            return id
        }

        private fun parsePriority(request: NodeBridgeRequest, value: Any?): Int {
            if (value == null || value == JSONObject.NULL) {
                return NotificationCompat.PRIORITY_DEFAULT
            }
            val priority = when (value) {
                is Number -> value.toInt()
                is String -> when (value.trim().lowercase(Locale.US)) {
                    "min" -> NotificationCompat.PRIORITY_MIN
                    "low" -> NotificationCompat.PRIORITY_LOW
                    "default" -> NotificationCompat.PRIORITY_DEFAULT
                    "high" -> NotificationCompat.PRIORITY_HIGH
                    "max" -> NotificationCompat.PRIORITY_MAX
                    else -> null
                }
                else -> null
            }
            if (priority == null || priority !in NotificationCompat.PRIORITY_MIN..NotificationCompat.PRIORITY_MAX) {
                throw NotificationBridgeException(
                    NodeBridgeError.invalidRequest(
                        message = "notifications.notify priority must be min, low, default, high, max, or a supported NotificationCompat priority.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            return priority
        }
    }
}

private object ControlledNotificationPolicy {
    const val DEFAULT_CHANNEL_ID = "autojs6-node"
    const val MAX_ACTIVE_NOTIFICATIONS = 32
    const val MAX_TITLE_LENGTH = 120
    const val MAX_TEXT_LENGTH = 1024
    const val MAX_CHANNEL_LENGTH = 80

    fun importanceForPriority(priority: Int): Int {
        return when (priority) {
            NotificationCompat.PRIORITY_MIN -> NotificationManager.IMPORTANCE_MIN
            NotificationCompat.PRIORITY_LOW -> NotificationManager.IMPORTANCE_LOW
            NotificationCompat.PRIORITY_HIGH,
            NotificationCompat.PRIORITY_MAX -> NotificationManager.IMPORTANCE_HIGH
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
    }
}

private class NotificationBridgeException(val bridgeError: NodeBridgeError) : RuntimeException(bridgeError.message)

private data class ControlledInputObserverRequest(
    val source: String,
    val keys: List<String>,
    val actions: List<String>,
    val maxQueueSize: Int,
)

private data class ControlledInputObserverDescriptor(
    val value: ControlledInputObserverRequest? = null,
    val error: NodeBridgeError? = null,
) {
    companion object {
        fun fromRequest(request: NodeBridgeRequest): ControlledInputObserverDescriptor {
            val args = runCatching { JSONArray(request.argsJson.ifBlank { "[]" }) }.getOrDefault(JSONArray())
            val payload = args.optJSONObject(0)
                ?: return ControlledInputObserverDescriptor(
                    error = NodeBridgeError.invalidRequest(
                        message = "input_observer.${request.method} requires a descriptor object.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            if (payload.optString("type", "key") != "key") {
                return ControlledInputObserverDescriptor(
                    error = NodeBridgeError.invalidRequest(
                        message = "input_observer.${request.method} supports only type=key.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            if (payload.optBoolean("intercept", false)) {
                return ControlledInputObserverDescriptor(
                    error = NodeBridgeError.permissionDenied(
                        message = "input_observer key interception requires the separate input_observer.intercept provider gate.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val keys = payload.optJSONArray("keys")?.toStringList()
                .orEmpty()
                .map { ControlledInputObserverPolicy.normalizeKeyName(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            if (keys.isEmpty()) {
                return ControlledInputObserverDescriptor(
                    error = NodeBridgeError.invalidRequest(
                        message = "input_observer.${request.method} requires at least one key name.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val actions = payload.optJSONArray("actions")?.toStringList()
                .orEmpty()
                .map { it.trim().lowercase(Locale.US) }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("down", "up") }
                .distinct()
            if (actions.any { it !in ControlledInputObserverPolicy.allowedActions }) {
                return ControlledInputObserverDescriptor(
                    error = NodeBridgeError.invalidRequest(
                        message = "input_observer.${request.method} actions may contain only down and up.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val options = args.optJSONObject(1) ?: JSONObject()
            val maxQueueSize = parseInt(
                options.opt("maxQueueSize"),
                fallback = ControlledInputObserverPolicy.DEFAULT_QUEUE_SIZE,
            ).coerceIn(1, ControlledInputObserverPolicy.MAX_QUEUE_SIZE)
            return ControlledInputObserverDescriptor(
                value = ControlledInputObserverRequest(
                    source = payload.optString("source", "fake").trim().lowercase(Locale.US).ifBlank { "fake" },
                    keys = keys,
                    actions = actions,
                    maxQueueSize = maxQueueSize,
                ),
            )
        }

        private fun JSONArray.toStringList(): List<String> {
            return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
        }

        private fun parseInt(value: Any?, fallback: Int): Int {
            return when (value) {
                is Int -> value
                is Long -> value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                is Number -> value.toDouble().takeIf { it.isFinite() }?.toInt()
                is String -> value.toIntOrNull()
                else -> null
            } ?: fallback
        }
    }
}

private object ControlledInputObserverPolicy {
    const val MAX_ACTIVE_SUBSCRIPTIONS = 8
    const val DEFAULT_QUEUE_SIZE = 64
    const val MAX_QUEUE_SIZE = 256
    const val DEFAULT_DRAIN_BATCH_SIZE = 32
    const val MAX_DRAIN_BATCH_SIZE = 128

    val allowedActions: Set<String> = setOf("down", "up")
    val allowedSources: Set<String> = setOf("fake", "accessibility")

    fun sourceJson(id: String, available: Boolean, live: Boolean, reason: String = ""): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", "key")
            .put("available", available)
            .put("live", live)
            .apply {
                if (reason.isNotBlank()) {
                    put("reason", reason)
                }
            }
    }

    fun normalizeKeyName(key: String): String {
        val raw = key
            .trim()
            .lowercase(Locale.US)
            .removePrefix("keycode_")
            .replace('-', '_')
            .replace(' ', '_')
        return when (raw) {
            "volumeup" -> "volume_up"
            "volumedown" -> "volume_down"
            else -> raw
        }
    }

    fun keyNameForKeyCode(keyCode: Int): String {
        return normalizeKeyName(KeyEvent.keyCodeToString(keyCode))
    }

    fun actionNameFor(action: Int): String {
        return when (action) {
            KeyEvent.ACTION_DOWN -> "down"
            KeyEvent.ACTION_UP -> "up"
            else -> "unknown"
        }
    }

    fun keyCodeFor(key: String): Int {
        return when (normalizeKeyName(key)) {
            "home" -> 3
            "back" -> 4
            "menu" -> 82
            "volume_up" -> 24
            "volume_down" -> 25
            else -> 0
        }
    }
}

private class ControlledInputObserverSubscription(
    val id: String,
    private val source: String,
    private val keys: List<String>,
    private val actions: List<String>,
    private val maxQueueSize: Int,
    private val eventStream: NodeBridgeEventStream? = null,
) {
    private val events = ConcurrentLinkedQueue<JSONObject>()
    private val closeActions = ConcurrentLinkedQueue<() -> Unit>()
    private val droppedCount = AtomicInteger(0)
    private val liveListenerAttached = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val createdAt = System.currentTimeMillis()

    fun queueEvent(key: String, keyCode: Int, action: String) {
        if (closed.get()) {
            return
        }
        queue(
            JSONObject()
                .put("type", "key")
                .put("source", source)
                .put("key", key)
                .put("keyCode", keyCode)
                .put("action", action)
                .put("eventTime", System.currentTimeMillis())
                .put("repeatCount", 0),
        )
    }

    fun queueKeyEvent(keyCode: Int, event: KeyEvent) {
        val keyName = ControlledInputObserverPolicy.keyNameForKeyCode(keyCode)
        val actionName = ControlledInputObserverPolicy.actionNameFor(event.action)
        if (keyName !in keys || actionName !in actions) {
            return
        }
        queue(
            JSONObject()
                .put("type", "key")
                .put("source", source)
                .put("key", keyName)
                .put("keyCode", keyCode)
                .put("action", actionName)
                .put("eventTime", event.eventTime)
                .put("repeatCount", event.repeatCount),
        )
    }

    fun drainEvents(maxEvents: Int): JSONArray {
        val result = JSONArray()
        repeat(maxEvents.coerceAtLeast(0)) {
            val event = events.poll() ?: return@repeat
            result.put(event)
        }
        return result
    }

    fun addCloseAction(action: () -> Unit) {
        closeActions.add(action)
    }

    fun markLiveListenerAttached() {
        liveListenerAttached.set(true)
    }

    fun markLiveListenerDetached() {
        liveListenerAttached.set(false)
    }

    fun pendingEventCount(): Int = events.size

    fun droppedEventCount(): Int = droppedCount.get()

    fun liveListenerCount(): Int = if (!closed.get() && liveListenerAttached.get()) 1 else 0

    fun close() {
        if (closed.getAndSet(true)) {
            return
        }
        eventStream?.publish(JSONObject().put("type", "close"))
        events.clear()
        while (true) {
            val action = closeActions.poll() ?: return
            runCatching { action() }
        }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", "key")
            .put("source", source)
            .put("keys", JSONArray(keys))
            .put("actions", JSONArray(actions))
            .put("ready", true)
            .put("closed", closed.get())
            .put("createdAt", createdAt)
            .put("pendingEventCount", pendingEventCount())
            .put("droppedCount", droppedCount.get())
            .put("liveListenerCount", liveListenerCount())
    }

    private fun queue(event: JSONObject) {
        while (events.size >= maxQueueSize) {
            if (events.poll() == null) {
                break
            }
            droppedCount.incrementAndGet()
        }
        events.add(event)
        eventStream?.publish(event)
    }
}

private data class ControlledSensorRequest(
    val type: String,
    val sensorType: Int,
    val samplingIntervalMs: Long,
    val samplingPeriodUs: Int,
    val timeoutMs: Long,
    val maxQueueSize: Int,
)

private data class ControlledSensorRequestDescriptor(
    val value: ControlledSensorRequest? = null,
    val error: NodeBridgeError? = null,
) {
    companion object {
        fun fromRequest(request: NodeBridgeRequest): ControlledSensorRequestDescriptor {
            val args = runCatching { JSONArray(request.argsJson.ifBlank { "[]" }) }.getOrDefault(JSONArray())
            val payload = args.optJSONObject(0)
                ?: return ControlledSensorRequestDescriptor(
                    error = NodeBridgeError.invalidRequest(
                        message = "sensors.${request.method} requires a descriptor object.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            val type = payload.optString("type", "").trim().lowercase(Locale.US)
            val sensorType = ControlledSensorPolicy.allowedSensorTypes[type]
                ?: return ControlledSensorRequestDescriptor(
                    error = NodeBridgeError.invalidRequest(
                        message = "sensors.${request.method} supports only accelerometer, gyroscope, and light.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            val samplingIntervalMs = parseLong(
                payload.opt("samplingIntervalMs"),
                fallback = ControlledSensorPolicy.DEFAULT_SAMPLING_INTERVAL_MS,
            ).coerceIn(ControlledSensorPolicy.MIN_SAMPLING_INTERVAL_MS, ControlledSensorPolicy.MAX_SAMPLING_INTERVAL_MS)
            val timeoutMs = parseLong(
                payload.opt("timeoutMs"),
                fallback = ControlledSensorPolicy.DEFAULT_TIMEOUT_MS,
            ).coerceIn(ControlledSensorPolicy.MIN_TIMEOUT_MS, ControlledSensorPolicy.MAX_TIMEOUT_MS)
            val maxQueueSize = parseInt(
                payload.opt("maxQueueSize"),
                fallback = ControlledSensorPolicy.DEFAULT_MAX_QUEUE_SIZE,
            ).coerceIn(1, ControlledSensorPolicy.HARD_MAX_QUEUE_SIZE)
            return ControlledSensorRequestDescriptor(
                value = ControlledSensorRequest(
                    type = type,
                    sensorType = sensorType,
                    samplingIntervalMs = samplingIntervalMs,
                    samplingPeriodUs = (samplingIntervalMs * 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    timeoutMs = timeoutMs,
                    maxQueueSize = maxQueueSize,
                ),
            )
        }

        private fun parseLong(value: Any?, fallback: Long): Long {
            return when (value) {
                is Int -> value.toLong()
                is Long -> value
                is Number -> value.toDouble().takeIf { it.isFinite() }?.toLong()
                is String -> value.toLongOrNull()
                else -> null
            } ?: fallback
        }

        private fun parseInt(value: Any?, fallback: Int): Int {
            return parseLong(value, fallback.toLong()).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }
    }
}

private object ControlledSensorPolicy {
    const val MAX_SUBSCRIPTIONS = 4
    const val MIN_SAMPLING_INTERVAL_MS = 20L
    const val DEFAULT_SAMPLING_INTERVAL_MS = 250L
    const val MAX_SAMPLING_INTERVAL_MS = 60_000L
    const val MIN_TIMEOUT_MS = 100L
    const val DEFAULT_TIMEOUT_MS = 5_000L
    const val MAX_TIMEOUT_MS = 60_000L
    const val DEFAULT_MAX_QUEUE_SIZE = 16
    const val HARD_MAX_QUEUE_SIZE = 64

    val allowedSensorTypes: Map<String, Int> = linkedMapOf(
        "accelerometer" to Sensor.TYPE_ACCELEROMETER,
        "gyroscope" to Sensor.TYPE_GYROSCOPE,
        "light" to Sensor.TYPE_LIGHT,
    )

    fun sensorInfoJson(type: String, sensor: Sensor): JSONObject {
        return JSONObject()
            .put("type", type)
            .put("name", sensor.name.orEmpty())
            .put("vendor", sensor.vendor.orEmpty())
            .put("version", sensor.version)
            .put("resolution", sensor.resolution.toDouble())
            .put("power", sensor.power.toDouble())
            .put("minDelayUs", sensor.minDelay)
            .put("maxDelayUs", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) sensor.maxDelay else 0)
    }

    fun sensorEventJson(type: String, event: SensorEvent): JSONObject {
        return JSONObject()
            .put("type", type)
            .put("timestamp", event.timestamp)
            .put("accuracy", event.accuracy)
            .put("values", JSONArray().also { values ->
                event.values.forEach { values.put(it.toDouble()) }
            })
    }
}

private class ControlledSensorSubscription(
    val id: String,
    val type: String,
    private val maxQueueSize: Int,
    private val eventStream: NodeBridgeEventStream? = null,
) {
    private val events = ConcurrentLinkedQueue<JSONObject>()
    @Volatile
    var listener: SensorEventListener? = null

    fun queueEvent(event: JSONObject) {
        queue(JSONObject().put("type", "event").put("event", event))
    }

    fun queueAccuracy(accuracy: Int) {
        queue(JSONObject().put("type", "accuracy").put("accuracy", accuracy))
    }

    fun queueClose() {
        queue(JSONObject().put("type", "close"))
    }

    fun drainEvents(): JSONArray {
        val result = JSONArray()
        while (true) {
            val event = events.poll() ?: break
            result.put(event)
        }
        return result
    }

    fun toJson(request: ControlledSensorRequest): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", type)
            .put("samplingIntervalMs", request.samplingIntervalMs)
            .put("maxQueueSize", maxQueueSize)
    }

    private fun queue(event: JSONObject) {
        while (events.size >= maxQueueSize) {
            events.poll() ?: break
        }
        events.add(event)
        eventStream?.publish(event)
    }
}

private class ControlledFetchException(
    val code: String,
    message: String,
) : IOException(message)

private object ControlledWebSocketPolicy {
    const val MAX_CONNECTIONS = 2
    const val DEFAULT_TIMEOUT_MS = 10_000L
    const val HARD_TIMEOUT_MS = 60_000L
    const val DEFAULT_MAX_MESSAGE_BYTES = 65_536
    const val HARD_MAX_MESSAGE_BYTES = 1_048_576
    const val DEFAULT_MAX_QUEUE_SIZE = 32
    const val HARD_MAX_QUEUE_SIZE = 128
}

/** Upper bound destroy() spends waiting for the 1001 close frames of live WebSockets to be written. */
private const val WEBSOCKET_DESTROY_CLOSE_WAIT_MS = 500L

private class ControlledWebSocketDiagnostics {
    private val activeConnectionCount = AtomicInteger(0)
    private val maxActiveConnectionCount = AtomicInteger(0)
    private val timeoutCount = AtomicInteger(0)
    private val rejectedByPolicyCount = AtomicInteger(0)
    private val serviceDestroyCloseCount = AtomicInteger(0)
    private val completedConnectCount = AtomicInteger(0)

    fun tryStartConnection(maxConnections: Int): Boolean {
        while (true) {
            val current = activeConnectionCount.get()
            if (current >= maxConnections) {
                recordPolicyRejection()
                return false
            }
            if (activeConnectionCount.compareAndSet(current, current + 1)) {
                recordMaxActive(current + 1)
                return true
            }
        }
    }

    fun finishConnection() {
        while (true) {
            val current = activeConnectionCount.get()
            if (current <= 0) {
                return
            }
            if (activeConnectionCount.compareAndSet(current, current - 1)) {
                return
            }
        }
    }

    fun recordCompletedConnect() {
        completedConnectCount.incrementAndGet()
    }

    fun recordTimeout() {
        timeoutCount.incrementAndGet()
    }

    fun recordPolicyRejection() {
        rejectedByPolicyCount.incrementAndGet()
    }

    fun recordServiceDestroyClose(activeSessions: Int) {
        if (activeSessions > 0) {
            serviceDestroyCloseCount.addAndGet(activeSessions)
        }
        activeConnectionCount.set(0)
    }

    fun nativeValues(): Map<String, String> = linkedMapOf(
        "embedded_script.websocket_active_connection_count" to activeConnectionCount.get().toString(),
        "embedded_script.websocket_max_active_connection_count" to maxActiveConnectionCount.get().toString(),
        "embedded_script.websocket_timeout_count" to timeoutCount.get().toString(),
        "embedded_script.websocket_rejected_by_policy_count" to rejectedByPolicyCount.get().toString(),
        "embedded_script.websocket_service_destroy_close_count" to serviceDestroyCloseCount.get().toString(),
        "embedded_script.websocket_completed_connect_count" to completedConnectCount.get().toString(),
    )

    private fun recordMaxActive(count: Int) {
        while (true) {
            val current = maxActiveConnectionCount.get()
            if (count <= current) {
                return
            }
            if (maxActiveConnectionCount.compareAndSet(current, count)) {
                return
            }
        }
    }
}

private data class ControlledWebSocketConnectRequest(
    val url: String,
    val headers: List<Pair<String, String>>,
    val protocols: List<String>,
    val timeoutMs: Long,
    val maxMessageBytes: Int,
    val maxQueueSize: Int,
) {
    fun toOkHttpRequest(): okhttp3.Request {
        val builder = okhttp3.Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank()) {
                builder.addHeader(name, value)
            }
        }
        if (protocols.isNotEmpty()) {
            builder.header("Sec-WebSocket-Protocol", protocols.joinToString(", "))
        }
        return builder.build()
    }

    companion object {
        fun fromJson(request: NodeBridgeRequest, payload: JSONObject): ControlledWebSocketConnectParseResult {
            val url = payload.optString("url", "").trim()
            val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
            if (url.isBlank() || scheme !in setOf("ws", "wss")) {
                return ControlledWebSocketConnectParseResult(
                    error = NodeBridgeError.network(
                        message = "websocket.connect only accepts ws and wss URLs.",
                        code = NodeRuntimeErrorCodes.NETWORK_INVALID_URL,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            return ControlledWebSocketConnectParseResult(
                value = ControlledWebSocketConnectRequest(
                    url = url,
                    headers = parseHeaders(payload.opt("headers")),
                    protocols = parseProtocols(payload.opt("protocols")),
                    timeoutMs = parsePositiveLong(payload.opt("timeoutMs"), ControlledWebSocketPolicy.DEFAULT_TIMEOUT_MS)
                        .coerceIn(1L, ControlledWebSocketPolicy.HARD_TIMEOUT_MS),
                    maxMessageBytes = parsePositiveInt(payload.opt("maxMessageBytes"), ControlledWebSocketPolicy.DEFAULT_MAX_MESSAGE_BYTES)
                        .coerceIn(0, ControlledWebSocketPolicy.HARD_MAX_MESSAGE_BYTES),
                    maxQueueSize = parsePositiveInt(payload.opt("maxQueueSize"), ControlledWebSocketPolicy.DEFAULT_MAX_QUEUE_SIZE)
                        .coerceIn(1, ControlledWebSocketPolicy.HARD_MAX_QUEUE_SIZE),
                ),
            )
        }
    }
}

private data class ControlledWebSocketConnectParseResult(
    val value: ControlledWebSocketConnectRequest? = null,
    val error: NodeBridgeError? = null,
)

private class ControlledWebSocketSession(
    val id: String,
    val url: String,
    val maxMessageBytes: Int,
    val maxQueueSize: Int,
    private val eventStream: NodeBridgeEventStream? = null,
) {
    private val events = ConcurrentLinkedQueue<JSONObject>()
    private val closed = CountDownLatch(1)
    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var terminal = false

    fun attach(value: WebSocket) {
        webSocket = value
    }

    fun queueOpen() {
        queueEvent(JSONObject().put("type", "open"))
    }

    fun queueMessageText(text: String) {
        queueEvent(
            JSONObject()
                .put("type", "message")
                .put("message", JSONObject().put("type", "text").put("text", text)),
        )
    }

    fun queueMessageBinary(dataBase64: String) {
        queueEvent(
            JSONObject()
                .put("type", "message")
                .put("message", JSONObject().put("type", "binary").put("dataBase64", dataBase64)),
        )
    }

    fun queueClose(code: Int, reason: String) {
        queueEvent(JSONObject().put("type", "close").put("code", code).put("reason", reason))
    }

    fun queueError(code: String, message: String) {
        queueEvent(
            JSONObject()
                .put("type", "error")
                .put("error", JSONObject().put("code", code).put("message", message)),
        )
    }

    fun markTerminal() {
        terminal = true
    }

    fun drainEvents(): JSONArray {
        val result = JSONArray()
        while (true) {
            val event = events.poll() ?: break
            result.put(event)
        }
        return result
    }

    fun removeAfterDrain(): Boolean = terminal && events.isEmpty()

    fun sendText(text: String): Boolean = webSocket?.send(text) == true

    fun sendBinary(base64: String): Boolean {
        val bytes = base64.decodeBase64() ?: return false
        return webSocket?.send(bytes) == true
    }

    fun sendBinary(bytes: ByteArray): Boolean = webSocket?.send(bytes.toByteString()) == true

    fun close(code: Int, reason: String) {
        webSocket?.close(code, reason)
    }

    /** OkHttp reported the socket closed or failed; destroy() waits for this before tearing the client down. */
    fun markClosed() {
        closed.countDown()
    }

    fun awaitClosed(timeoutMs: Long): Boolean = closed.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("url", url)
        .put("readyState", "open")
        .put("maxMessageBytes", maxMessageBytes)
        .put("maxQueueSize", maxQueueSize)

    private fun queueEvent(event: JSONObject) {
        while (events.size >= maxQueueSize) {
            events.poll() ?: break
        }
        events.add(event)
        eventStream?.publish(event)
    }
}

private data class ControlledWebSocketSendMessage(
    val type: String,
    val text: String?,
    val dataBase64: String?,
    /** Descriptor-delivered payload (`messageTransport: "pfd"`, M20.2 batch 14), already read. */
    val bytes: ByteArray? = null,
) {
    fun send(session: ControlledWebSocketSession): Boolean {
        return when {
            bytes != null && type == "binary" -> session.sendBinary(bytes)
            bytes != null -> session.sendText(String(bytes, Charsets.UTF_8))
            type == "binary" -> session.sendBinary(dataBase64.orEmpty())
            else -> session.sendText(text.orEmpty())
        }
    }

    companion object {
        fun fromJson(request: NodeBridgeRequest, payload: JSONObject, maxMessageBytes: Int): ControlledWebSocketSendParseResult {
            if (payload.optString("messageTransport", "") == "pfd") {
                return fromUpload(request, payload, maxMessageBytes)
            }
            val text = payload.optString("text").takeIf { payload.has("text") && !payload.isNull("text") }
            val dataBase64 = payload.optString("dataBase64").takeIf { payload.has("dataBase64") && !payload.isNull("dataBase64") }
            val size = when {
                dataBase64 != null -> (dataBase64.decodeBase64()?.size ?: Int.MAX_VALUE)
                text != null -> text.toByteArray(Charsets.UTF_8).size
                else -> 0
            }
            if (size > maxMessageBytes) {
                return ControlledWebSocketSendParseResult(
                    error = NodeBridgeError.network(
                        message = "websocket.send message exceeded ${maxMessageBytes} bytes.",
                        code = NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            return ControlledWebSocketSendParseResult(
                value = ControlledWebSocketSendMessage(
                    type = if (dataBase64 != null) "binary" else "text",
                    text = text,
                    dataBase64 = dataBase64,
                ),
            )
        }

        /** The message bytes arrived as a descriptor; the session policy still bounds them. */
        private fun fromUpload(request: NodeBridgeRequest, payload: JSONObject, maxMessageBytes: Int): ControlledWebSocketSendParseResult {
            val upload = request.upload
                ?: return ControlledWebSocketSendParseResult(
                    error = NodeBridgeError.invalidRequest(
                        message = "websocket.send declared a descriptor message but no descriptor was attached.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            val declaredBytes = payload.optLong("messageBytes", -1L)
            if (declaredBytes != upload.byteCount) {
                return ControlledWebSocketSendParseResult(
                    error = NodeBridgeError.invalidRequest(
                        message = "websocket.send messageBytes ($declaredBytes) does not match the attached descriptor (${upload.byteCount} bytes).",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            if (upload.byteCount > maxMessageBytes) {
                return ControlledWebSocketSendParseResult(
                    error = NodeBridgeError.network(
                        message = "websocket.send message exceeded ${maxMessageBytes} bytes.",
                        code = NodeRuntimeErrorCodes.NETWORK_RESPONSE_TOO_LARGE,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val bytes = try {
                upload.readBytes()
            } catch (error: IOException) {
                return ControlledWebSocketSendParseResult(
                    error = NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = request.module,
                        method = request.method,
                        code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                    ),
                )
            }
            return ControlledWebSocketSendParseResult(
                value = ControlledWebSocketSendMessage(
                    type = if (payload.optString("messageKind", "binary") == "text") "text" else "binary",
                    text = null,
                    dataBase64 = null,
                    bytes = bytes,
                ),
            )
        }
    }
}

private data class ControlledWebSocketSendParseResult(
    val value: ControlledWebSocketSendMessage? = null,
    val error: NodeBridgeError? = null,
)

private fun parseHeaders(value: Any?): List<Pair<String, String>> {
    return when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { index ->
            val pair = value.optJSONArray(index) ?: return@mapNotNull null
            val name = pair.optString(0, "").trim()
            val headerValue = pair.optString(1, "")
            if (name.isBlank()) null else name to headerValue
        }
        is JSONObject -> value.keys().asSequence().mapNotNull { name ->
            val headerValue = value.optString(name, "")
            if (name.isBlank()) null else name to headerValue
        }.toList()
        else -> emptyList()
    }
}

private fun parseProtocols(value: Any?): List<String> {
    return when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { index ->
            value.optString(index).trim().takeIf { it.isNotBlank() }
        }
        is String -> value.split(",").mapNotNull { it.trim().takeIf(String::isNotBlank) }
        else -> emptyList()
    }
}

private fun parsePositiveLong(value: Any?, fallback: Long): Long {
    val parsed = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    return parsed?.takeIf { it >= 0L } ?: fallback
}

private fun parsePositiveInt(value: Any?, fallback: Int): Int {
    val parsed = when (value) {
        is Int -> value
        is Long -> value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        is Number -> {
            val doubleValue = value.toDouble()
            if (!doubleValue.isFinite()) {
                null
            } else {
                doubleValue.toInt()
            }
        }
        is String -> value.toIntOrNull()
        else -> null
    }
    return parsed?.takeIf { it >= 0 } ?: fallback
}

private const val SHELL_OUTPUT_LIMIT_BYTES = 1024 * 1024

private class StreamCapture(
    private val inputStream: InputStream,
    private val maximum: Int? = null,
) {
    private val output = ByteArrayOutputStream()
    @Volatile
    var truncated = false
        private set
    @Volatile
    private var failure: Throwable? = null

    fun read() {
        runCatching {
            inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    val remaining = (maximum ?: SHELL_OUTPUT_LIMIT_BYTES) - output.size()
                    if (remaining > 0) {
                        output.write(buffer, 0, read.coerceAtMost(remaining))
                    }
                    if (read > remaining) {
                        truncated = true
                    }
                }
            }
        }.onFailure { error ->
            failure = error
        }
    }

    fun text(): String {
        if (maximum != null) return synchronized(output) { NodeBridgeShellOutput.text(output.toByteArray(), maximum) }
        val base = output.toByteArray().toString(Charsets.UTF_8)
        val suffix = when {
            truncated -> "\n[AutoJs6 shell output truncated]\n"
            failure != null && base.isBlank() -> failure?.message ?: failure?.javaClass?.name.orEmpty()
            else -> ""
        }
        return base + suffix
    }
}

internal object NodeBridgeJson {

    fun parseJsonValue(json: String): Any {
        val trimmed = json.trim().ifEmpty { "null" }
        return JSONTokener(trimmed).nextValue() ?: JSONObject.NULL
    }

    fun valueToJson(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            else -> JSONArray().put(value).toString().let { wrapped ->
                wrapped.substring(1, wrapped.length - 1)
            }
        }
    }
}

private const val DEFAULT_TIMEOUT_MS = 30_000L
private const val MAX_TIMEOUT_MS = 2_147_483_647L
