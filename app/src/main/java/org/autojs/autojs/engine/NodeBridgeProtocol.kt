package org.autojs.autojs.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.autojs.autojs6.BuildConfig
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
            val id = value.optNonBlankString("id")
                ?: throw IllegalArgumentException("Node bridge request id must be non-empty.")
            val module = value.optNonBlankString("module")
                ?: throw IllegalArgumentException("Node bridge request module must be non-empty.")
            val method = value.optNonBlankString("method")
                ?: throw IllegalArgumentException("Node bridge request method must be non-empty.")
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
                JSONObject(json).optNonBlankString(name)
            }.getOrNull()
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
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("name", name)
        put("message", message)
        put("code", code)
        module?.let { put("module", it) }
        method?.let { put("method", it) }
        stack?.let { put("stack", it) }
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

        fun network(message: String, code: String, module: String? = null, method: String? = null): NodeBridgeError {
            return NodeBridgeError(
                message = message,
                code = code,
                module = module,
                method = method,
            )
        }
    }
}

private fun JSONObject.optNonBlankString(name: String): String? {
    return optString(name).takeIf { it.isNotBlank() }
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
    const val CLIPBOARD = "clipboard"
    const val DEVICE = "device"
    const val SHELL = "shell"
    const val FETCH = "fetch"
    const val WEBSOCKET = "websocket"
    const val WORK_MANAGER = "work_manager"

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
    val clipboardMethods = setOf("getText", "setText", "hasText")
    val deviceMethods = setOf("isScreenOn", "wakeUp", "vibrate")
    val shellMethods = setOf("exec")
    val fetchMethods = setOf("request")
    val websocketMethods = setOf("connect", "send", "close", "drainEvents")
    val workManagerMethods = setOf("scheduleOnce", "schedulePeriodic", "cancel", "list")

    val supportedMethodsByModule: Map<String, Set<String>> = mapOf(
        TOAST to toastMethods,
        APP to appMethods,
        ENGINES to enginesMethods,
        DIALOGS to dialogsMethods,
        ACCESSIBILITY to accessibilityMethods,
        MEDIA_PROJECTION to mediaProjectionMethods,
        IMAGE to imageMethods,
        IMAGES to imageMethods,
        CLIPBOARD to clipboardMethods,
        DEVICE to deviceMethods,
        SHELL to shellMethods,
        FETCH to fetchMethods,
        WEBSOCKET to websocketMethods,
        WORK_MANAGER to workManagerMethods,
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
    private val workManagerTasks = ConcurrentHashMap<String, JSONObject>()

    override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        when {
            request.module == NodeBridgeModules.TOAST && request.method in NodeBridgeModules.toastMethods -> dispatchToast(request, callback)
            request.module == NodeBridgeModules.APP && request.method in NodeBridgeModules.appMethods -> dispatchApp(request, callback)
            request.module == NodeBridgeModules.ENGINES && request.method in NodeBridgeModules.enginesMethods -> dispatchEngines(request, callback)
            request.module == NodeBridgeModules.DIALOGS && request.method in NodeBridgeModules.dialogsMethods -> dispatchDialogs(request, callback)
            request.module == NodeBridgeModules.ACCESSIBILITY && request.method in NodeBridgeModules.accessibilityMethods -> dispatchAccessibility(request, callback)
            request.module == NodeBridgeModules.MEDIA_PROJECTION && request.method in NodeBridgeModules.mediaProjectionMethods -> dispatchMediaProjection(request, callback)
            request.module in NodeBridgeModules.imageModules && request.method in NodeBridgeModules.imageMethods -> dispatchImage(request, callback)
            request.module == NodeBridgeModules.CLIPBOARD && request.method in NodeBridgeModules.clipboardMethods -> dispatchClipboard(request, callback)
            request.module == NodeBridgeModules.DEVICE && request.method in NodeBridgeModules.deviceMethods -> dispatchDevice(request, callback)
            request.module == NodeBridgeModules.SHELL && request.method in NodeBridgeModules.shellMethods -> dispatchShell(request, callback)
            request.module == NodeBridgeModules.FETCH && request.method in NodeBridgeModules.fetchMethods -> dispatchFetch(request, callback)
            request.module == NodeBridgeModules.WEBSOCKET && request.method in NodeBridgeModules.websocketMethods -> dispatchWebSocket(request, callback)
            request.module == NodeBridgeModules.WORK_MANAGER && request.method in NodeBridgeModules.workManagerMethods -> dispatchWorkManager(request, callback)
            else -> callback(unavailable(request))
        }
    }

    override fun nativeDiagnostics(): Map<String, String> = linkedMapOf<String, String>().apply {
        putAll(networkDiagnostics.nativeValues())
        putAll(websocketDiagnostics.nativeValues())
        put("embedded_script.work_manager_registered_task_count", workManagerTasks.size.toString())
    }

    override fun destroy(reason: NodeBridgeError) {
        networkDiagnostics.recordServiceDestroyCancellation(networkCalls.size)
        networkCalls.values.forEach { call ->
            runCatching { call.cancel() }
        }
        networkCalls.clear()
        websocketDiagnostics.recordServiceDestroyClose(websocketSessions.size)
        websocketSessions.values.forEach { session ->
            runCatching { session.close(1001, "AutoJs6 service destroyed") }
        }
        websocketSessions.clear()
        workManagerTasks.clear()
    }

    private fun unavailable(request: NodeBridgeRequest): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.bridgeUnavailable(request.module, request.method),
        )
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
        val response = when (request.method) {
            "isEnabled", "ensureEnabled" -> NodeBridgeResponse.success(request.id, "false")
            else -> NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.capabilityProviderMissing(
                    module = request.module,
                    method = request.method,
                    requirement = "an enabled accessibility capability provider",
                ),
            )
        }
        callback(response)
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
        thread(name = "AutoJs6NodeShellExec-${request.id}", isDaemon = true) {
            val response = runCatching {
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
            callback(response)
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
        return NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(id))
    }

    private fun cancelWorkManagerTask(request: NodeBridgeRequest): NodeBridgeResponse {
        val id = request.firstStringArg().trim()
        if (id.isBlank()) {
            return workManagerPolicyFailure(request, "work_manager.cancel requires a non-empty task id.")
        }
        workManagerTasks.remove(id)
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
        if (!networkDiagnostics.tryStartRequest()) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.network(
                        message = "AutoJs6 controlled fetch exceeded ${ControlledFetchPolicy.MAX_CONCURRENT_REQUESTS} active requests.",
                        code = NodeRuntimeErrorCodes.NETWORK_POLICY_DENIED,
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
        fun complete(response: NodeBridgeResponse) {
            networkCalls.remove(request.id)
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
        if (!websocketDiagnostics.tryStartConnection()) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.network(
                        message = "AutoJs6 controlled WebSocket exceeded ${ControlledWebSocketPolicy.MAX_CONNECTIONS} active connections.",
                        code = NodeRuntimeErrorCodes.NETWORK_POLICY_DENIED,
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

        val timeoutRunnable = Runnable {
            if (responded.compareAndSet(false, true)) {
                websocketDiagnostics.recordTimeout()
                websocketSessions.remove(sessionId)
                websocketDiagnostics.finishConnection()
                session.close(1001, "AutoJs6 WebSocket connect timed out")
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
                websocketDiagnostics.finishConnection()
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
                websocketDiagnostics.finishConnection()
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
        websocketDiagnostics.finishConnection()
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
        val stdout = StreamCapture(process.inputStream)
        val stderr = StreamCapture(process.errorStream)
        val stdoutThread = thread(name = "AutoJs6NodeShellStdout", isDaemon = true) { stdout.read() }
        val stderrThread = thread(name = "AutoJs6NodeShellStderr", isDaemon = true) { stderr.read() }
        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        val code = if (finished) {
            process.exitValue()
        } else {
            process.destroyForcibly()
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

private object ControlledFetchPolicy {
    const val MAX_CONCURRENT_REQUESTS = 4
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val HARD_TIMEOUT_MS = 120_000L
    const val DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L
    const val HARD_MAX_RESPONSE_BYTES = 16_777_216L
    const val DEFAULT_MAX_REDIRECTS = 5
    const val HARD_MAX_REDIRECTS = 10
}

private class ControlledFetchDiagnostics {
    private val activeRequestCount = AtomicInteger(0)
    private val maxActiveRequestCount = AtomicInteger(0)
    private val timeoutCount = AtomicInteger(0)
    private val rejectedByPolicyCount = AtomicInteger(0)
    private val abortedCount = AtomicInteger(0)
    private val completedCount = AtomicInteger(0)
    private val serviceDestroyCancellationCount = AtomicInteger(0)

    fun tryStartRequest(): Boolean {
        while (true) {
            val current = activeRequestCount.get()
            if (current >= ControlledFetchPolicy.MAX_CONCURRENT_REQUESTS) {
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

    fun tryStartConnection(): Boolean {
        while (true) {
            val current = activeConnectionCount.get()
            if (current >= ControlledWebSocketPolicy.MAX_CONNECTIONS) {
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
