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
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
    )

    fun supports(module: String, method: String): Boolean {
        return supportedMethodsByModule[module]?.contains(method) == true
    }
}

interface NodeBridgeCapabilityProvider {
    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit)

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
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val networkCalls = ConcurrentHashMap<String, Call>()

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
            else -> callback(unavailable(request))
        }
    }

    override fun destroy(reason: NodeBridgeError) {
        networkCalls.values.forEach { call ->
            runCatching { call.cancel() }
        }
        networkCalls.clear()
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
            callback(NodeBridgeResponse.failure(request.id, fetchRequest.error ?: NodeBridgeError.processDead()))
            return
        }

        val controlled = fetchRequest.value
        val okHttpRequest = controlled.toOkHttpRequest()
        val client = networkClient.newBuilder()
            .callTimeout(controlled.timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val call = client.newCall(okHttpRequest)
        networkCalls[request.id] = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                networkCalls.remove(request.id)
                callback(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.network(
                            message = if (call.isCanceled()) {
                                "AutoJs6 controlled fetch was cancelled."
                            } else {
                                "AutoJs6 controlled fetch timed out or failed: ${e.message ?: e.javaClass.name}"
                            },
                            code = if (call.isCanceled()) NodeRuntimeErrorCodes.NETWORK_ABORTED else NodeRuntimeErrorCodes.NETWORK_TIMEOUT,
                            module = request.module,
                            method = request.method,
                        ),
                    ),
                )
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                networkCalls.remove(request.id)
                response.use { okResponse ->
                    val result = runCatching {
                        controlled.toBridgeResponse(okResponse)
                    }.getOrElse { error ->
                        val code = (error as? ControlledFetchException)?.code ?: NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD
                        callback(
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
                    callback(NodeBridgeResponse.success(request.id, result.toString()))
                }
            }
        })
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

private data class ControlledFetchRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timeoutMs: Long,
    val maxResponseBytes: Long,
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
        const val DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L
        const val HARD_MAX_RESPONSE_BYTES = 16_777_216L

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
                    timeoutMs = parsePositiveLong(payload.opt("timeoutMs"), request.timeoutMs).coerceIn(1L, 120_000L),
                    maxResponseBytes = parsePositiveLong(payload.opt("maxResponseBytes"), DEFAULT_MAX_RESPONSE_BYTES)
                        .coerceIn(0L, HARD_MAX_RESPONSE_BYTES),
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
