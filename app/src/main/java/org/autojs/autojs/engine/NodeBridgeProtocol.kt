package org.autojs.autojs.engine

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
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import okio.ByteString.Companion.decodeBase64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import kotlin.concurrent.thread

data class NodeBridgeRequest(
    val id: String,
    val module: String,
    val method: String,
    val argsJson: String,
    val timeoutMs: Long,
    val permissions: List<String> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("module", module)
        put("method", method)
        put("args", NodeBridgeJson.parseJsonValue(argsJson.ifBlank { "[]" }))
        put("timeoutMs", timeoutMs)
        put("permissions", JSONArray(permissions))
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
            val timeoutValue = value.opt("timeoutMs")
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
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("ok", ok)
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

data class NodeBridgeError(
    val name: String = "Error",
    val message: String,
    val code: String,
    val module: String? = null,
    val method: String? = null,
    val stack: String? = null,
    val missingCapabilities: List<String> = emptyList(),
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("name", name)
        put("message", message)
        put("code", code)
        module?.let { put("module", it) }
        method?.let { put("method", it) }
        stack?.let { put("stack", it) }
        if (missingCapabilities.isNotEmpty()) {
            put("missingCapabilities", JSONArray(missingCapabilities))
        }
    }

    companion object {
        fun fromJsonObject(value: JSONObject): NodeBridgeError {
            return NodeBridgeError(
                name = value.optString("name", "Error"),
                message = value.optString("message"),
                code = value.optString("code", NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD),
                module = value.optString("module").takeIf { it.isNotBlank() },
                method = value.optString("method").takeIf { it.isNotBlank() },
                stack = value.optString("stack").takeIf { it.isNotBlank() },
                missingCapabilities = value.optionalStringArray("missingCapabilities"),
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

        fun permissionDenied(message: String, module: String? = null, method: String? = null): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED,
                module = module,
                method = method,
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
            )
        }

        fun bridgeUnavailable(module: String, method: String): NodeBridgeError {
            return permissionDenied(
                message = "AutoJs6 bridge module or method is not available: $module.$method",
                module = module,
                method = method,
            )
        }

        fun capabilityProviderMissing(module: String, method: String, requirement: String): NodeBridgeError {
            return permissionDenied(
                message = "AutoJs6 $module bridge requires $requirement.",
                module = module,
                method = method,
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
            )
        }

        fun network(message: String, code: String, module: String? = null, method: String? = null): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = code,
                module = module,
                method = method,
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
    val backend: String = "embedded",
    val sourceName: String = "",
    val cwd: String = "",
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

    const val TOAST = "toast"
    const val APP = "app"
    const val ENGINES = "engines"
    const val DIALOGS = "dialogs"
    const val ACCESSIBILITY = "accessibility"
    const val MEDIA_PROJECTION = "media_projection"
    const val IMAGE = "image"
    const val IMAGES = "images"
    const val OCR = "ocr"
    const val STORAGE = "storage"
    const val STORAGES = "storages"
    const val NOTIFICATIONS = "notifications"
    const val CLIPBOARD = "clipboard"
    const val DEVICE = "device"
    const val SHELL = "shell"
    const val FETCH = "fetch"
    const val WEBSOCKET = "websocket"
    const val WORK_MANAGER = "work_manager"
    const val JAVA = "java"

    val toastMethods = setOf("showToast", "toast")
    val appMethods = setOf("launchPackage", "launchApp", "openAppSetting", "startActivity")
    val enginesMethods = setOf("myEngine", "all", "stopAll", "stopSelf", "execScript", "execScriptFile")
    val dialogsMethods = setOf("alert", "confirm", "input", "select")
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
    )
    val mediaProjectionMethods = setOf("requestScreenCapture", "nextImage", "stop")
    val imageModules = setOf(IMAGE, IMAGES)
    val imageMethods = setOf("captureScreen", "readImage", "saveImage", "clip", "resize", "findImage", "recycle")
    val ocrMethods = setOf("recognize", "recognizeText", "detectTextBounds")
    val storageModules = setOf(STORAGE, STORAGES)
    val storageMethods = setOf("get", "put", "remove", "clear", "keys", "contains")
    val notificationsMethods = setOf("notify", "cancel", "cancelAll")
    val clipboardMethods = setOf("getText", "setText", "hasText")
    val deviceMethods = setOf("isScreenOn", "wakeUp", "vibrate")
    val shellMethods = setOf("exec")
    val fetchMethods = setOf("request")
    val websocketMethods = setOf("connect", "send", "close", "drainEvents")
    val workManagerMethods = setOf("scheduleOnce", "schedulePeriodic", "cancel", "list")
    val javaMethods = setOf("type", "callStatic", "new")

    val supportedMethodsByModule: Map<String, Set<String>> = mapOf(
        TOAST to toastMethods,
        APP to appMethods,
        ENGINES to enginesMethods,
        DIALOGS to dialogsMethods,
        ACCESSIBILITY to accessibilityMethods,
        MEDIA_PROJECTION to mediaProjectionMethods,
        IMAGE to imageMethods,
        IMAGES to imageMethods,
        OCR to ocrMethods,
        STORAGE to storageMethods,
        STORAGES to storageMethods,
        NOTIFICATIONS to notificationsMethods,
        CLIPBOARD to clipboardMethods,
        DEVICE to deviceMethods,
        SHELL to shellMethods,
        FETCH to fetchMethods,
        WEBSOCKET to websocketMethods,
        WORK_MANAGER to workManagerMethods,
        JAVA to javaMethods,
    )

    fun supports(module: String, method: String): Boolean {
        return supportedMethodsByModule[module]?.contains(method) == true
    }
}

interface NodeBridgeCapabilityProvider {
    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit)

    fun nativeDiagnostics(): Map<String, String> = emptyMap()

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
}

class AndroidNodeBridgeCapabilityProvider(
    context: Context,
    private val engineInfo: NodeBridgeEngineInfo = NodeBridgeEngineInfo.fromContext(context),
    private val javaInteropExperimentalEnabled: Boolean = BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
    private val bridgeLimits: NodeBridgeLimits.Policy = NodeBridgeLimits.defaults,
) : NodeBridgeCapabilityProvider {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val networkCalls = ConcurrentHashMap<String, Call>()
    private val networkDiagnostics = ControlledFetchDiagnostics()
    private val websocketSessions = ConcurrentHashMap<String, ControlledWebSocketSession>()
    private val websocketDiagnostics = ControlledWebSocketDiagnostics()
    private val bridgePermissionManifest by lazy {
        NodeBridgePermissionManifest.fromWorkingDirectory(engineInfo.cwd)
    }
    private val workManagerTasks = ConcurrentHashMap<String, JSONObject>()
    private val javaHandles = ConcurrentHashMap<String, Any>()
    private val notificationHandles = ConcurrentHashMap<Int, NodeBridgeResourceHandle>()
    private val notificationIdCounter = AtomicInteger(1_000_000)
    private val resourceRegistry = NodeBridgeResourceRegistry(engineInfo.id)
    private val activeShellCommands = AtomicInteger(0)
    private val maxActiveShellCommands = AtomicInteger(0)
    private val bridgeResourceLimitRejections = AtomicInteger(0)
    private val bridgeRateLimitedRejections = AtomicInteger(0)
    private val accessibilityRateLimiter = NodeBridgeRateLimiter()

    override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val auditFailure = capabilityAuditFailure(request)
        if (auditFailure != null) {
            callback(NodeBridgeResponse.failure(request.id, auditFailure))
            return
        }
        when {
            request.module == NodeBridgeModules.TOAST && request.method in NodeBridgeModules.toastMethods -> dispatchToast(request, callback)
            request.module == NodeBridgeModules.APP && request.method in NodeBridgeModules.appMethods -> dispatchApp(request, callback)
            request.module == NodeBridgeModules.ENGINES && request.method in NodeBridgeModules.enginesMethods -> dispatchEngines(request, callback)
            request.module == NodeBridgeModules.DIALOGS && request.method in NodeBridgeModules.dialogsMethods -> dispatchDialogs(request, callback)
            request.module == NodeBridgeModules.ACCESSIBILITY && request.method in NodeBridgeModules.accessibilityMethods -> dispatchAccessibility(request, callback)
            request.module == NodeBridgeModules.MEDIA_PROJECTION && request.method in NodeBridgeModules.mediaProjectionMethods -> dispatchMediaProjection(request, callback)
            request.module in NodeBridgeModules.imageModules && request.method in NodeBridgeModules.imageMethods -> dispatchImage(request, callback)
            request.module == NodeBridgeModules.OCR && request.method in NodeBridgeModules.ocrMethods -> dispatchOcr(request, callback)
            request.module in NodeBridgeModules.storageModules && request.method in NodeBridgeModules.storageMethods -> dispatchStorage(request, callback)
            request.module == NodeBridgeModules.NOTIFICATIONS && request.method in NodeBridgeModules.notificationsMethods -> dispatchNotifications(request, callback)
            request.module == NodeBridgeModules.CLIPBOARD && request.method in NodeBridgeModules.clipboardMethods -> dispatchClipboard(request, callback)
            request.module == NodeBridgeModules.DEVICE && request.method in NodeBridgeModules.deviceMethods -> dispatchDevice(request, callback)
            request.module == NodeBridgeModules.SHELL && request.method in NodeBridgeModules.shellMethods -> dispatchShell(request, callback)
            request.module == NodeBridgeModules.FETCH && request.method in NodeBridgeModules.fetchMethods -> dispatchFetch(request, callback)
            request.module == NodeBridgeModules.WEBSOCKET && request.method in NodeBridgeModules.websocketMethods -> dispatchWebSocket(request, callback)
            request.module == NodeBridgeModules.WORK_MANAGER && request.method in NodeBridgeModules.workManagerMethods -> dispatchWorkManager(request, callback)
            request.module == NodeBridgeModules.JAVA && request.method in NodeBridgeModules.javaMethods -> dispatchJava(request, callback)
            else -> callback(unavailable(request))
        }
    }

    override fun nativeDiagnostics(): Map<String, String> = linkedMapOf<String, String>().apply {
        putAll(networkDiagnostics.nativeValues())
        putAll(websocketDiagnostics.nativeValues())
        put("embedded_script.work_manager_registered_task_count", workManagerTasks.size.toString())
        put("embedded_script.java_interop_handle_count", javaHandles.size.toString())
        putAll(resourceRegistry.nativeDiagnostics())
        putAll(bridgeLimits.nativeDiagnostics())
        put("embedded_script.bridge_resource_limit_rejection_count", bridgeResourceLimitRejections.get().toString())
        put("embedded_script.bridge_rate_limited_count", bridgeRateLimitedRejections.get().toString())
        put("embedded_script.shell_active_command_count", activeShellCommands.get().toString())
        put("embedded_script.shell_max_active_command_count", maxActiveShellCommands.get().toString())
    }

    override fun destroy(reason: NodeBridgeError) {
        val destroyReason = reason.code.ifBlank { reason.message }
        networkDiagnostics.recordServiceDestroyCancellation(networkCalls.size)
        websocketDiagnostics.recordServiceDestroyClose(websocketSessions.size)
        resourceRegistry.closeAll(destroyReason)
        networkCalls.values.forEach { call -> runCatching { call.cancel() } }
        networkCalls.clear()
        websocketSessions.clear()
        workManagerTasks.clear()
        javaHandles.clear()
        notificationHandles.clear()
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
            "stopAll" -> NodeBridgeResponse.success(request.id, "0")
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
        callback(
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.capabilityProviderMissing(
                    module = request.module,
                    method = request.method,
                    requirement = "a UI capability provider",
                ),
            ),
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
        val response = when (request.method) {
            "isEnabled", "ensureEnabled" -> NodeBridgeResponse.success(request.id, accessibilityBridgeOperational().toString())
            else -> dispatchAccessibilityOperation(request)
        }
        callback(response)
    }

    private fun accessibilityBridgeOperational(): Boolean {
        return AutoJsAccessibilityService.hasInstance() &&
                AutoJsAccessibilityService.hasOperationalState &&
                AutoJsAccessibilityService.instance != null
    }

    private fun dispatchAccessibilityOperation(request: NodeBridgeRequest): NodeBridgeResponse {
        val service = AutoJsAccessibilityService.instance
        if (service == null || !AutoJsAccessibilityService.hasOperationalState) {
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.capabilityProviderMissing(
                    module = request.module,
                    method = request.method,
                    requirement = "an enabled accessibility capability provider in the current process",
                ),
            )
        }
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
            "findByText" -> dispatchAccessibilityFindByText(request, service)
            "clickText" -> dispatchAccessibilityClickText(request, service)
            "findOne" -> dispatchAccessibilityFindOne(request, service)
            "findAll" -> dispatchAccessibilityFindAll(request, service)
            "click" -> dispatchAccessibilityClick(request, service)
            else -> unavailable(request)
        }
    }

    private fun dispatchAccessibilityFindByText(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
    ): NodeBridgeResponse {
        val text = request.args().optString(0)
        val node = service.accessibilityRoot()?.findAccessibilityNodeInfosByText(text).orEmpty().firstOrNull()
        return NodeBridgeResponse.success(request.id, node?.toAccessibilityJson()?.toString() ?: "null")
    }

    private fun dispatchAccessibilityClickText(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
    ): NodeBridgeResponse {
        val text = request.args().optString(0)
        val node = service.accessibilityRoot()?.findAccessibilityNodeInfosByText(text).orEmpty().firstOrNull()
        return NodeBridgeResponse.success(request.id, node?.clickAccessibilityNode().toString())
    }

    private fun dispatchAccessibilityFindOne(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
    ): NodeBridgeResponse {
        val selector = request.args().optJSONObject(0) ?: return NodeBridgeResponse.success(request.id, "null")
        val node = service.findAccessibilityNodes(selector).firstOrNull()
        return NodeBridgeResponse.success(request.id, node?.toAccessibilityJson()?.toString() ?: "null")
    }

    private fun dispatchAccessibilityFindAll(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
    ): NodeBridgeResponse {
        val selector = request.args().optJSONObject(0) ?: JSONObject()
        val nodes = JSONArray()
        service.findAccessibilityNodes(selector).forEach { node ->
            nodes.put(node.toAccessibilityJson())
        }
        return NodeBridgeResponse.success(request.id, nodes.toString())
    }

    private fun dispatchAccessibilityClick(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
    ): NodeBridgeResponse {
        val args = request.args()
        val selector = args.optJSONObject(0) ?: return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = "AutoJs6 accessibility bridge supports real-device click only through selector descriptors.",
                module = request.module,
                method = request.method,
            ),
        )
        val node = service.findAccessibilityNodes(selector).firstOrNull()
        return NodeBridgeResponse.success(request.id, node?.clickAccessibilityNode().toString())
    }

    private fun NodeBridgeRequest.args(): JSONArray {
        return runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())
    }

    private fun AutoJsAccessibilityService.accessibilityRoot(): AccessibilityNodeInfo? {
        return runCatching { rootInActiveWindow ?: fastRootInActiveWindow }.getOrNull()
    }

    private fun AutoJsAccessibilityService.findAccessibilityNodes(selector: JSONObject): List<AccessibilityNodeInfo> {
        val root = accessibilityRoot() ?: return emptyList()
        val text = selector.optString("text").takeIf { it.isNotBlank() }
        if (text != null) {
            return root.findAccessibilityNodeInfosByText(text).orEmpty()
        }
        val textContains = selector.optString("textContains").takeIf { it.isNotBlank() }
        if (textContains != null) {
            return root.findAccessibilityNodeInfosByText(textContains).orEmpty()
        }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectAccessibilityNodes(root, selector, nodes)
        return nodes
    }

    private fun collectAccessibilityNodes(
        node: AccessibilityNodeInfo?,
        selector: JSONObject,
        output: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node == null) {
            return
        }
        if (node.matchesAccessibilitySelector(selector)) {
            output += node
        }
        repeat(node.childCount) { index ->
            collectAccessibilityNodes(runCatching { node.getChild(index) }.getOrNull(), selector, output)
        }
    }

    private fun AccessibilityNodeInfo.matchesAccessibilitySelector(selector: JSONObject): Boolean {
        val desc = selector.optString("desc").takeIf { it.isNotBlank() }
        if (desc != null && contentDescription?.toString() != desc) {
            return false
        }
        val descContains = selector.optString("descContains").takeIf { it.isNotBlank() }
        if (descContains != null && contentDescription?.toString()?.contains(descContains) != true) {
            return false
        }
        val id = selector.optString("id").takeIf { it.isNotBlank() }
        if (id != null && viewIdResourceName != id) {
            return false
        }
        val className = selector.optString("className").takeIf { it.isNotBlank() }
        if (className != null && this.className?.toString() != className) {
            return false
        }
        return desc != null || descContains != null || id != null || className != null
    }

    private fun AccessibilityNodeInfo.toAccessibilityJson(): JSONObject {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return JSONObject()
            .put("text", text?.toString().orEmpty())
            .put("desc", contentDescription?.toString().orEmpty())
            .put("id", viewIdResourceName.orEmpty())
            .put("className", className?.toString().orEmpty())
            .put(
                "bounds",
                JSONObject()
                    .put("left", bounds.left)
                    .put("top", bounds.top)
                    .put("right", bounds.right)
                    .put("bottom", bounds.bottom),
            )
            .put("clickable", isClickable)
            .put("enabled", isEnabled)
    }

    private fun AccessibilityNodeInfo.clickAccessibilityNode(): Boolean {
        var current: AccessibilityNodeInfo? = this
        while (current != null) {
            if (current.isEnabled && current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = runCatching { current.parent }.getOrNull()
        }
        return runCatching { performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
    }

    private fun dispatchMediaProjection(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        callback(
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.capabilityProviderMissing(
                    module = request.module,
                    method = request.method,
                    requirement = "a MediaProjection capability provider",
                ),
            ),
        )
    }

    private fun dispatchImage(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        callback(
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.capabilityProviderMissing(
                    module = request.module,
                    method = request.method,
                    requirement = "an image capability provider",
                ),
            ),
        )
    }

    private fun dispatchOcr(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        callback(
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.capabilityProviderMissing(
                    module = request.module,
                    method = request.method,
                    requirement = "an OCR capability provider",
                ),
            ),
        )
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

    private fun storagePreferencesName(storeName: String): String {
        val packageName = engineInfo.packageName.ifBlank { appContext.packageName }
        return NodeStorageBridgePolicy.preferencesName(packageName, engineInfo.cwd, storeName)
    }

    private fun dispatchNotifications(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            runCatching {
                val args = JSONArray(request.argsJson.ifBlank { "[]" })
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
                        ensureNotificationPostingAllowed(request)
                        if (!notificationHandles.containsKey(descriptor.id) &&
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
                        NotificationManagerCompat.from(appContext).notify(descriptor.id, descriptor.toNotification(appContext))
                        notificationHandles.remove(descriptor.id)?.release("notification.update")
                        val handle = resourceRegistry.register(
                            type = NodeBridgeResourceType.NOTIFICATION,
                            id = "notification-${descriptor.id}",
                            metadata = mapOf(
                                "channel" to descriptor.channel,
                                "ongoing" to descriptor.ongoing.toString(),
                            ),
                        ) {
                            notificationHandles.remove(descriptor.id)
                            NotificationManagerCompat.from(appContext).cancel(descriptor.id)
                        }
                        notificationHandles[descriptor.id] = handle
                        NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(descriptor.id))
                    }
                    "cancel" -> {
                        val id = ControlledNotificationDescriptor.parseNotificationIdArg(request, args, 0)
                        notificationHandles.remove(id)?.close("notification.cancel")
                        NodeBridgeResponse.success(request.id, "null")
                    }
                    "cancelAll" -> {
                        notificationHandles.values.toList().forEach { handle ->
                            handle.close("notification.cancelAll")
                        }
                        NodeBridgeResponse.success(request.id, "null")
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
            }
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

    private fun dispatchDevice(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            when (request.method) {
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
                else -> unavailable(request)
            }
        }
    }

    private fun dispatchShell(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (!tryAcquireShellCommand()) {
            bridgeResourceLimitRejections.incrementAndGet()
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.resourceLimit(
                        message = "AutoJs6 shell bridge exceeded ${bridgeLimits.maxShellCommands} active commands.",
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
        thread(name = "AutoJs6NodeShellExec-${request.id}", isDaemon = true) {
            val response = try {
                runCatching {
                    when (request.method) {
                        "exec" -> executeShell(request)
                        else -> unavailable(request)
                    }
                }.getOrElse { error ->
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError(
                            message = error.message ?: error.javaClass.name,
                            code = NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD,
                            module = request.module,
                            method = request.method,
                            stack = error.stackTraceToString(),
                        ),
                    )
                }
            } finally {
                activeShellCommands.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            }
            callback(response)
        }
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
        if (workManagerTasks.size >= ControlledWorkManagerPolicy.MAX_TASKS_PER_PROVIDER) {
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
        val task = parsed.value.toJson(id, engineInfo)
        workManagerTasks[id] = task
        resourceRegistry.register(
            type = NodeBridgeResourceType.WORK_MANAGER_TASK,
            id = id,
            metadata = mapOf(
                "kind" to kind.name.lowercase(),
                "scheduler" to task.optString("scheduler", "metadata"),
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
        if (!resourceRegistry.close(id, "work_manager.cancel")) {
            workManagerTasks.remove(id)
        }
        return NodeBridgeResponse.success(request.id, "null")
    }

    private fun listWorkManagerTasks(request: NodeBridgeRequest): NodeBridgeResponse {
        val tasks = workManagerTasks.values
            .map { value -> JSONObject(value.toString()) }
            .sortedBy { value -> value.optLong("createdAtEpochMs", 0L) }
        val result = JSONArray()
        tasks.forEach { task -> result.put(task) }
        return NodeBridgeResponse.success(request.id, result.toString())
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

    private fun dispatchJava(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val response = runCatching {
            if (!javaInteropExperimentalEnabled) {
                return@runCatching NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.javaInterop(
                        message = "AutoJs6 Java interop is disabled; enable autojs.nodejs.javaInterop.experimental for an allowlist-only experiment.",
                        code = NodeRuntimeErrorCodes.JAVA_INTEROP_DISABLED,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            when (request.method) {
                "type" -> describeJavaType(request)
                "callStatic" -> callJavaStatic(request)
                "new" -> newJavaObject(request)
                else -> unavailable(request)
            }
        }.getOrElse { error ->
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.javaInterop(
                    message = error.message ?: error.javaClass.name,
                    code = NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                    module = request.module,
                    method = request.method,
                    stack = error.stackTraceToString(),
                ),
            )
        }
        callback(response)
    }

    private fun describeJavaType(request: NodeBridgeRequest): NodeBridgeResponse {
        val payload = request.firstObjectArg()
            ?: return javaPolicyFailure(request, "java.type requires a class descriptor.")
        val className = payload.optNonBlankString("className")
            ?: return javaPolicyFailure(request, "java.type requires a non-empty className.")
        ControlledJavaInteropPolicy.validateClass(className)?.let { return NodeBridgeResponse.failure(request.id, it.toBridgeError(request)) }
        return NodeBridgeResponse.success(
            request.id,
            ControlledJavaInteropPolicy.describeClass(className).toString(),
        )
    }

    private fun callJavaStatic(request: NodeBridgeRequest): NodeBridgeResponse {
        val payload = request.firstObjectArg()
            ?: return javaPolicyFailure(request, "java.callStatic requires a call descriptor.")
        val className = payload.optNonBlankString("className")
            ?: return javaPolicyFailure(request, "java.callStatic requires a non-empty className.")
        val method = payload.optNonBlankString("method")
            ?: return javaPolicyFailure(request, "java.callStatic requires a non-empty method.")
        ControlledJavaInteropPolicy.validateClass(className)?.let { return NodeBridgeResponse.failure(request.id, it.toBridgeError(request)) }
        val args = payload.optJSONArray("args") ?: JSONArray()
        val result = when (className) {
            "java.lang.Math" -> ControlledJavaInteropPolicy.callMath(method, args)
            "java.util.UUID" -> ControlledJavaInteropPolicy.callUuid(method, args)
            else -> ControlledJavaInteropPolicy.Result.denied(
                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                "AutoJs6 Java interop does not allow static calls on $className.",
            )
        }
        if (result.error != null) {
            return NodeBridgeResponse.failure(request.id, result.error.toBridgeError(request))
        }
        return NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(result.value))
    }

    private fun newJavaObject(request: NodeBridgeRequest): NodeBridgeResponse {
        val payload = request.firstObjectArg()
            ?: return javaPolicyFailure(request, "java.new requires a constructor descriptor.")
        val className = payload.optNonBlankString("className")
            ?: return javaPolicyFailure(request, "java.new requires a non-empty className.")
        ControlledJavaInteropPolicy.validateClass(className)?.let { return NodeBridgeResponse.failure(request.id, it.toBridgeError(request)) }
        val args = payload.optJSONArray("args") ?: JSONArray()
        val result = when (className) {
            "android.graphics.Rect" -> ControlledJavaInteropPolicy.newRect(args)
            else -> ControlledJavaInteropPolicy.Result.denied(
                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                "AutoJs6 Java interop does not allow constructing $className.",
            )
        }
        if (result.error != null) {
            return NodeBridgeResponse.failure(request.id, result.error.toBridgeError(request))
        }
        val rect = result.value as? Rect
            ?: return javaPolicyFailure(request, "AutoJs6 Java interop constructor returned an unsupported value.")
        val handleId = "java-${UUID.randomUUID()}"
        javaHandles[handleId] = rect
        resourceRegistry.register(
            type = NodeBridgeResourceType.JAVA_OBJECT,
            id = handleId,
            metadata = mapOf("className" to className),
        ) {
            javaHandles.remove(handleId)
        }
        return NodeBridgeResponse.success(request.id, javaHandleJson(handleId, className, rect).toString())
    }

    private fun javaHandleJson(handleId: String, className: String, rect: Rect): JSONObject {
        return JSONObject().apply {
            put("__autojs6JavaHandle", handleId)
            put("className", className)
            put("value", JSONObject().apply {
                put("left", rect.left)
                put("top", rect.top)
                put("right", rect.right)
                put("bottom", rect.bottom)
            })
        }
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
        if (!BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.network(
                        message = "AutoJs6 controlled fetch is disabled; enable autojs.nodejs.network.experimental in an experimental build.",
                        code = NodeRuntimeErrorCodes.NETWORK_DISABLED,
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
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
                val code = if (call.isCanceled()) NodeRuntimeErrorCodes.NETWORK_ABORTED else NodeRuntimeErrorCodes.NETWORK_TIMEOUT
                if (code == NodeRuntimeErrorCodes.NETWORK_ABORTED) {
                    networkDiagnostics.recordAbort()
                } else {
                    networkDiagnostics.recordTimeout()
                }
                complete(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.network(
                            message = if (call.isCanceled()) {
                                "AutoJs6 controlled fetch was cancelled."
                            } else {
                                "AutoJs6 controlled fetch timed out or failed: ${e.message ?: e.javaClass.name}"
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
                        controlled.toBridgeResponse(okResponse)
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
                    complete(NodeBridgeResponse.success(request.id, result.toString()))
                }
            }
        })
    }

    private fun dispatchWebSocket(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (!BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.network(
                        message = "AutoJs6 controlled WebSocket is disabled; enable autojs.nodejs.network.experimental in an experimental build.",
                        code = NodeRuntimeErrorCodes.NETWORK_DISABLED,
                        module = request.module,
                        method = request.method,
                    ),
                ),
            )
            return
        }
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
        val responded = AtomicBoolean(false)
        val session = ControlledWebSocketSession(
            id = sessionId,
            url = controlled.url,
            maxMessageBytes = controlled.maxMessageBytes,
            maxQueueSize = controlled.maxQueueSize,
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
                    callback(NodeBridgeResponse.success(request.id, session.toJson().toString()))
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
                mainHandler.removeCallbacks(timeoutRunnable)
                websocketSessions.remove(sessionId)
                if (websocketResource.release("websocket.failure")) {
                    websocketDiagnostics.finishConnection()
                }
                session.queueError(NodeRuntimeErrorCodes.NETWORK_TIMEOUT, t.message ?: t.javaClass.name)
                if (responded.compareAndSet(false, true)) {
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
                session.queueClose(code, reason)
                websocketSessions.remove(sessionId)
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
        callback(NodeBridgeResponse.success(request.id, session.drainEvents().toString()))
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

    private fun dispatchApp(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        dispatchOnMain(request, callback) {
            when (request.method) {
                "launchPackage" -> NodeBridgeResponse.success(
                    request.id,
                    launchPackage(request.firstStringArg()).toString(),
                )
                "launchApp" -> NodeBridgeResponse.success(
                    request.id,
                    launchAppByLabel(request.firstStringArg()).toString(),
                )
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
                Log.i(TAG, "toast.showToast message=$message duration=${if (isLong) "long" else "short"}")
            }
            Toast.makeText(appContext, message, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
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
                        NodeBridgeError(
                            message = error.message ?: error.javaClass.name,
                            code = NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD,
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

    private fun executeShell(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val command = args.optString(0, "")
        if (command.isBlank() || command.indexOf('\u0000') >= 0) {
            return shellPolicyFailure(request, "shell.exec requires a non-empty command without NUL bytes.")
        }
        val options = args.optJSONObject(1) ?: JSONObject()
        val root = options.optBoolean("root", false)
        val timeoutMs = parseShellTimeoutMs(options.opt("timeoutMs"))
        val cwd = resolveShellCwd(options.optString("cwd", ""))
            ?: return shellPolicyFailure(request, "shell.exec cwd must stay inside the scoped working directory.")
        val env = parseShellEnv(options.optJSONObject("env"))
            ?: return shellPolicyFailure(request, "shell.exec env must be a JSON object with string keys and string values.")

        val result = runControlledShellCommand(
            requestId = request.id,
            command = command,
            root = root,
            cwd = cwd,
            env = env,
            timeoutMs = timeoutMs,
        )
        return NodeBridgeResponse.success(request.id, result.toJson())
    }

    private fun parseShellTimeoutMs(value: Any?): Long {
        val parsed = when (value) {
            null, JSONObject.NULL -> 30_000L
            is Int -> value.toLong()
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 30_000L
            else -> 30_000L
        }
        return parsed.coerceIn(1L, MAX_TIMEOUT_MS)
    }

    private fun parseShellEnv(envJson: JSONObject?): Map<String, String>? {
        if (envJson == null) {
            return emptyMap()
        }
        val env = linkedMapOf<String, String>()
        val keys = envJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = envJson.opt(key)
            if (key.isBlank() || key.indexOf('\u0000') >= 0 || value !is String || value.indexOf('\u0000') >= 0) {
                return null
            }
            env[key] = value
        }
        return env
    }

    private fun resolveShellCwd(rawCwd: String): File? {
        if (rawCwd.indexOf('\u0000') >= 0) {
            return null
        }
        val base = File(engineInfo.cwd.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath)
            .canonicalFile
        val input = rawCwd.trim()
        if (input.isBlank()) {
            return base
        }
        val candidateInput = File(input)
        if (candidateInput.isAbsolute) {
            return null
        }
        val segments = input.replace('\\', '/').split('/').filter { it.isNotBlank() }
        if (segments.any { it == ".." }) {
            return null
        }
        val candidate = File(base, input).canonicalFile
        val basePath = base.path.trimEnd(File.separatorChar)
        val candidatePath = candidate.path
        if (candidatePath != basePath && !candidatePath.startsWith(basePath + File.separator)) {
            return null
        }
        return candidate.takeIf { it.isDirectory }
    }

    private fun runControlledShellCommand(
        requestId: String,
        command: String,
        root: Boolean,
        cwd: File,
        env: Map<String, String>,
        timeoutMs: Long,
    ): ShellExecResult {
        val commandLine = if (root) {
            listOf("su", "-c", command)
        } else {
            listOf("sh", "-c", command)
        }
        val process = runCatching {
            ProcessBuilder(commandLine)
                .directory(cwd)
                .apply { environment().putAll(env) }
                .start()
        }.getOrElse { error ->
            return ShellExecResult(
                code = 1,
                stdout = "",
                stderr = error.message ?: error.javaClass.name,
            )
        }
        val resource = resourceRegistry.register(
            type = NodeBridgeResourceType.SHELL_EXECUTION,
            id = "shell-$requestId",
            metadata = mapOf(
                "cwd" to cwd.absolutePath,
                "root" to root.toString(),
            ),
        ) {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
        val stdout = StreamCapture(process.inputStream)
        val stderr = StreamCapture(process.errorStream)
        val stdoutThread = thread(name = "AutoJs6NodeShellStdout", isDaemon = true) { stdout.read() }
        val stderrThread = thread(name = "AutoJs6NodeShellStderr", isDaemon = true) { stderr.read() }
        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        val code = if (finished) {
            process.exitValue().also { exitCode ->
                resource.release("exit:$exitCode")
            }
        } else {
            resource.close("timeout:$timeoutMs")
            124
        }
        stdoutThread.join(SHELL_STREAM_JOIN_MS)
        stderrThread.join(SHELL_STREAM_JOIN_MS)
        val timeoutMessage = if (finished) {
            ""
        } else {
            "AutoJs6 shell command timed out after ${timeoutMs}ms."
        }
        return ShellExecResult(
            code = code,
            stdout = stdout.text(),
            stderr = listOf(stderr.text(), timeoutMessage)
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n"),
        )
    }

    private fun shellPolicyFailure(request: NodeBridgeRequest, message: String): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = message,
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

    private fun openAppSetting(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    private fun createIntent(options: JSONObject): Intent? {
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
            options.optJSONArray("categories")?.let { categories ->
                for (index in 0 until categories.length()) {
                    categories.optString(index).takeIf { it.isNotBlank() }?.let(::addCategory)
                }
            }
            if (options.has("flags")) {
                addFlags(options.optInt("flags", 0))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            options.optJSONObject("extras")?.let { extras ->
                putJsonIntentExtras(this, extras)
            }
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
            when (val value = extras.opt(key)) {
                is Boolean -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is Double -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
                JSONObject.NULL, null -> Unit
                else -> intent.putExtra(key, value.toString())
            }
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
    }
}

private data class ShellExecResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("code", code)
            put("stdout", stdout)
            put("stderr", stderr)
        }.toString()
    }
}

private enum class ControlledWorkTaskKind(val wireName: String) {
    ONCE("once"),
    PERIODIC("periodic"),
}

private object ControlledWorkManagerPolicy {
    const val MAX_TASKS_PER_PROVIDER = 64
    const val MAX_DELAY_MS = 2_592_000_000L
    const val MIN_PERIODIC_INTERVAL_MS = 900_000L
    const val MAX_PERIODIC_INTERVAL_MS = 2_592_000_000L
}

private data class ControlledWorkTaskDescriptor(
    val kind: ControlledWorkTaskKind,
    val scriptPath: String,
    val entryKind: String,
    val backend: String,
    val packaged: Boolean,
    val delayMs: Long,
    val intervalMs: Long?,
    val constraints: JSONObject,
) {
    fun toJson(id: String, engineInfo: NodeBridgeEngineInfo): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", kind.wireName)
            put("status", "registered")
            put("scheduler", "metadata")
            put("backend", backend)
            put("scriptPath", scriptPath)
            put("entryKind", entryKind)
            put("packaged", packaged)
            put("delayMs", delayMs)
            intervalMs?.let { put("intervalMs", it) }
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
            val backend = payload.optString("backend", "embedded").ifBlank { "embedded" }
            if (!backend.equals("embedded", ignoreCase = true)) {
                return ControlledWorkTaskParseResult(
                    error = workTaskError(request, "work_manager supports only the embedded Node backend."),
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
            val scriptPath = normalizeRelativeScriptPath(rawScriptPath)
                ?: return ControlledWorkTaskParseResult(
                    error = workTaskError(request, "work_manager task path must be a non-empty relative path inside the scoped project."),
                )
            val packaged = payload.optBoolean("packaged", false)
            if (resolveScopedScriptPath(appContext, engineInfo, scriptPath, packaged) == null) {
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

            return ControlledWorkTaskParseResult(
                value = ControlledWorkTaskDescriptor(
                    kind = kind,
                    scriptPath = scriptPath,
                    entryKind = entryKind,
                    backend = "embedded",
                    packaged = packaged,
                    delayMs = delayMs,
                    intervalMs = intervalMs,
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
            val value = payload.opt(name)
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

private object ControlledJavaInteropPolicy {

    private val allowedClasses = setOf(
        "java.lang.Math",
        "java.util.UUID",
        "android.graphics.Rect",
    )

    private val deniedExactClasses = setOf(
        "java.lang.Class",
        "java.lang.ClassLoader",
        "java.lang.Runtime",
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.System",
        "dalvik.system.DexClassLoader",
        "dalvik.system.PathClassLoader",
        "dalvik.system.InMemoryDexClassLoader",
    )

    private val deniedPrefixes = listOf(
        "java.io.",
        "java.nio.file.",
        "java.net.",
        "android.content.",
        "android.app.",
        "android.view.",
        "android.accessibilityservice.",
        "android.media.projection.",
    )

    private val reflectionPrefixes = listOf(
        "java.lang.reflect.",
        "kotlin.reflect.",
        "java.lang.invoke.",
    )

    fun validateClass(className: String): ErrorDescriptor? {
        val normalized = className.trim()
        if (normalized.isBlank()) {
            return ErrorDescriptor(
                code = NodeRuntimeErrorCodes.JAVA_CLASS_DENIED,
                message = "AutoJs6 Java interop requires a non-empty className.",
            )
        }
        if (normalized in reflectionPrefixes || reflectionPrefixes.any { normalized.startsWith(it) }) {
            return ErrorDescriptor(
                code = NodeRuntimeErrorCodes.JAVA_REFLECTION_DENIED,
                message = "AutoJs6 Java interop denies reflection class $normalized.",
            )
        }
        if (normalized in deniedExactClasses || deniedPrefixes.any { normalized.startsWith(it) }) {
            return ErrorDescriptor(
                code = NodeRuntimeErrorCodes.JAVA_CLASS_DENIED,
                message = "AutoJs6 Java interop denies class $normalized.",
            )
        }
        if (normalized !in allowedClasses) {
            return ErrorDescriptor(
                code = NodeRuntimeErrorCodes.JAVA_CLASS_DENIED,
                message = "AutoJs6 Java interop allowlist does not include $normalized.",
            )
        }
        return null
    }

    fun describeClass(className: String): JSONObject {
        return JSONObject().apply {
            put("className", className)
            put("experimental", true)
            put("constructable", className == "android.graphics.Rect")
            put(
                "staticMethods",
                JSONArray(
                    when (className) {
                        "java.lang.Math" -> listOf("abs", "ceil", "floor", "max", "min", "pow", "round", "sqrt")
                        "java.util.UUID" -> listOf("fromString", "randomUUID")
                        else -> emptyList()
                    },
                ),
            )
        }
    }

    fun callMath(method: String, args: JSONArray): Result {
        return when (method) {
            "max" -> binaryNumber(args, method) { a, b -> kotlin.math.max(a, b) }
            "min" -> binaryNumber(args, method) { a, b -> kotlin.math.min(a, b) }
            "pow" -> binaryNumber(args, method) { a, b -> java.lang.Math.pow(a, b) }
            "abs" -> unaryNumber(args, method) { value -> kotlin.math.abs(value) }
            "ceil" -> unaryNumber(args, method) { value -> kotlin.math.ceil(value) }
            "floor" -> unaryNumber(args, method) { value -> kotlin.math.floor(value) }
            "round" -> unaryNumber(args, method) { value -> kotlin.math.round(value) }
            "sqrt" -> unaryNumber(args, method) { value -> kotlin.math.sqrt(value) }
            else -> Result.denied(
                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                "AutoJs6 Java interop does not allow java.lang.Math.$method.",
            )
        }
    }

    fun callUuid(method: String, args: JSONArray): Result {
        return when (method) {
            "randomUUID" -> {
                if (args.length() != 0) {
                    Result.denied(
                        NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                        "java.util.UUID.randomUUID requires no arguments.",
                    )
                } else {
                    Result.value(UUID.randomUUID().toString())
                }
            }
            "fromString" -> {
                if (args.length() != 1) {
                    Result.denied(
                        NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                        "java.util.UUID.fromString requires one string argument.",
                    )
                } else {
                    runCatching {
                        UUID.fromString(args.optString(0)).toString()
                    }.fold(
                        onSuccess = Result::value,
                        onFailure = {
                            Result.denied(
                                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                                "java.util.UUID.fromString rejected the supplied value.",
                            )
                        },
                    )
                }
            }
            else -> Result.denied(
                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                "AutoJs6 Java interop does not allow java.util.UUID.$method.",
            )
        }
    }

    fun newRect(args: JSONArray): Result {
        if (args.length() != 4) {
            return Result.denied(
                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                "android.graphics.Rect requires four integer arguments.",
            )
        }
        val left = args.optIntOrNull(0) ?: return invalidRectArg()
        val top = args.optIntOrNull(1) ?: return invalidRectArg()
        val right = args.optIntOrNull(2) ?: return invalidRectArg()
        val bottom = args.optIntOrNull(3) ?: return invalidRectArg()
        return Result.value(Rect(left, top, right, bottom))
    }

    private fun invalidRectArg(): Result {
        return Result.denied(
            NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
            "android.graphics.Rect constructor arguments must be finite integers.",
        )
    }

    private fun unaryNumber(args: JSONArray, method: String, operation: (Double) -> Double): Result {
        if (args.length() != 1) {
            return Result.denied(
                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                "java.lang.Math.$method requires one numeric argument.",
            )
        }
        val value = args.optDoubleOrNull(0) ?: return Result.denied(
            NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
            "java.lang.Math.$method argument must be a finite number.",
        )
        return Result.value(operation(value))
    }

    private fun binaryNumber(args: JSONArray, method: String, operation: (Double, Double) -> Double): Result {
        if (args.length() != 2) {
            return Result.denied(
                NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
                "java.lang.Math.$method requires two numeric arguments.",
            )
        }
        val first = args.optDoubleOrNull(0) ?: return Result.denied(
            NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
            "java.lang.Math.$method arguments must be finite numbers.",
        )
        val second = args.optDoubleOrNull(1) ?: return Result.denied(
            NodeRuntimeErrorCodes.JAVA_METHOD_DENIED,
            "java.lang.Math.$method arguments must be finite numbers.",
        )
        return Result.value(operation(first, second))
    }

    data class ErrorDescriptor(
        val code: String,
        val message: String,
    ) {
        fun toBridgeError(request: NodeBridgeRequest): NodeBridgeError {
            return NodeBridgeError.javaInterop(
                message = message,
                code = code,
                module = request.module,
                method = request.method,
            )
        }
    }

    data class Result(
        val value: Any? = null,
        val error: ErrorDescriptor? = null,
    ) {
        companion object {
            fun value(value: Any?): Result = Result(value = value)

            fun denied(code: String, message: String): Result = Result(
                error = ErrorDescriptor(code = code, message = message),
            )
        }
    }
}

private fun JSONArray.optDoubleOrNull(index: Int): Double? {
    val value = opt(index)
    val number = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    } ?: return null
    return number.takeIf { it.isFinite() }
}

private fun JSONArray.optIntOrNull(index: Int): Int? {
    val value = optDoubleOrNull(index) ?: return null
    if (value % 1.0 != 0.0 || value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
        return null
    }
    return value.toInt()
}

private object ControlledFetchPolicy {
    const val MAX_CONCURRENT_REQUESTS = 4
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val HARD_TIMEOUT_MS = 120_000L
    const val DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L
    const val HARD_MAX_RESPONSE_BYTES = 16_777_216L
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

private data class ControlledFetchRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeoutMs: Long,
    val maxResponseBytes: Long,
    val maxRedirects: Int,
) {
    fun toOkHttpRequest(): okhttp3.Request {
        val builder = okhttp3.Request.Builder().url(url)
        headers.forEach { (name, value) ->
            if (name.isNotBlank()) {
                builder.addHeader(name, value)
            }
        }
        val requestBody = body?.toRequestBody(contentType())
        builder.method(method, requestBody)
        return builder.build()
    }

    fun toBridgeResponse(response: okhttp3.Response): JSONObject {
        val bytes = response.body?.byteStream()?.use { input ->
            readBoundedBytes(input, maxResponseBytes)
        } ?: ByteArray(0)
        val headersJson = JSONArray()
        response.headers.forEach { header ->
            headersJson.put(JSONArray().put(header.first).put(header.second))
        }
        return JSONObject().apply {
            put("url", response.request.url.toString())
            put("status", response.code)
            put("statusText", response.message)
            put("headers", headersJson)
            put("bodyBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("bodyBytes", bytes.size)
            put("redirected", response.isRedirect)
            put("maxRedirects", maxRedirects)
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
            val body = parseBody(payload)
            if (method == "GET" && body != null && body.isNotEmpty()) {
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
                    body = if (method == "POST") body ?: ByteArray(0) else null,
                    timeoutMs = parsePositiveLong(payload.opt("timeoutMs"), request.timeoutMs)
                        .coerceIn(1L, ControlledFetchPolicy.HARD_TIMEOUT_MS),
                    maxResponseBytes = parsePositiveLong(payload.opt("maxResponseBytes"), ControlledFetchPolicy.DEFAULT_MAX_RESPONSE_BYTES)
                        .coerceIn(0L, ControlledFetchPolicy.HARD_MAX_RESPONSE_BYTES),
                    maxRedirects = parsePositiveInt(payload.opt("maxRedirects"), ControlledFetchPolicy.DEFAULT_MAX_REDIRECTS)
                        .coerceIn(0, ControlledFetchPolicy.HARD_MAX_REDIRECTS),
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
) {
    private val events = ConcurrentLinkedQueue<JSONObject>()
    @Volatile
    private var webSocket: WebSocket? = null

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

    fun drainEvents(): JSONArray {
        val result = JSONArray()
        while (true) {
            val event = events.poll() ?: break
            result.put(event)
        }
        return result
    }

    fun sendText(text: String): Boolean = webSocket?.send(text) == true

    fun sendBinary(base64: String): Boolean {
        val bytes = base64.decodeBase64() ?: return false
        return webSocket?.send(bytes) == true
    }

    fun close(code: Int, reason: String) {
        webSocket?.close(code, reason)
    }

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
    }
}

private data class ControlledWebSocketSendMessage(
    val type: String,
    val text: String?,
    val dataBase64: String?,
) {
    fun send(session: ControlledWebSocketSession): Boolean {
        return when (type) {
            "binary" -> session.sendBinary(dataBase64.orEmpty())
            else -> session.sendText(text.orEmpty())
        }
    }

    companion object {
        fun fromJson(request: NodeBridgeRequest, payload: JSONObject, maxMessageBytes: Int): ControlledWebSocketSendParseResult {
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
) {
    private val output = ByteArrayOutputStream()
    @Volatile
    private var truncated = false
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
                    val remaining = SHELL_OUTPUT_LIMIT_BYTES - output.size()
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
        val base = output.toByteArray().toString(Charsets.UTF_8)
        val suffix = when {
            truncated -> "\n[AutoJs6 shell output truncated]\n"
            failure != null && base.isBlank() -> failure?.message ?: failure?.javaClass?.name.orEmpty()
            else -> ""
        }
        return base + suffix
    }
}

private object NodeBridgeJson {

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
