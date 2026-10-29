package org.autojs.autojs.engine

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.widget.Toast
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.autojs.plugin.bun.runtime.api.IBunHostCapabilityBroker
import org.autojs.plugin.bun.runtime.api.IBunHostCapabilityCallback
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Host side of the Bun capability bridge (plugin ROADMAP M7, dynamic calls): one Binder object per execution,
 * pinned to the plugin UID and the execution ID and handed over in the `runScript` request together with the
 * granted capability IDs. Every accepted call is answered exactly once through the oneway callback; calls from
 * another UID, for another execution or after [close] are ignored rather than failed. Capability groups are
 * explicitly granted and dispatched here; this is not a generic route into the Node host API registry.
 * Payloads are JSON strings on both sides, so the plugin never interprets them.
 *
 * The plugin process holds the exported Binder ([binder]) until it collects its proxy, so the exported side lets go
 * of the broker in [close] (docs/memory-phase2/ROADMAP.md C37).
 * zh-CN: 插件进程在回收代理之前一直持有导出的 Binder ([binder]), 因此导出端在 [close] 时放开 broker (docs/memory-phase2/ROADMAP.md C37).
 */
internal class BunHostCapabilityBroker(
    context: Context,
    private val expectedCallerUid: Int,
    private val executionId: String,
    capabilities: List<String>,
    consoleProvider: () -> ConsoleImpl,
    private val workingDirectory: String? = null,
    private val agentExecutionState: org.autojs.autojs.execution.AgentScriptExecutionState? = null,
    // The spawn depth of this execution (engines.execScript* of api-coverage P4.1b): a script started by a script starts no other.
    private val spawnDepth: Int = 0,
    // The script's name as the plugin reports it (engines.myEngine of api-coverage P4.1b).
    private val sourceName: String = "",
) {
    private val exported = ExportedBroker(this)
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val calls = AtomicInteger(0)
    private val toasts = AtomicInteger(0)
    private val callExecutor = BunHostCallExecutor()
    private val coordinates = RuntimeCoordinateBridge()
    private val console = RuntimeConsoleBridge(consoleProvider, workingDirectory)
    private val pinyin by lazy { AndroidNodePinyinBackend(appContext) }
    private val pinyin4j by lazy { AndroidNodePinyin4jBackend(appContext) }
    private val opencc by lazy { AndroidNodeOpenccBackend(appContext) }
    private val imageScopeLock = Any()
    private var imageProvider: AndroidNodeBridgeCapabilityProvider? = null
    private val automation = BunAutomationBridge(appContext, capabilities, ::imagesProvider)
    private val files = BunFilesBridge(java.io.File(workingDirectory?.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath),
        assets = { appContext.assets.open(it) }, simplify = { org.autojs.autojs.pio.PFiles.getElegantPath(it) })
    private val captureLock = Any()
    private data class CaptureAttempt(val id: String, val result: CompletableFuture<NodeBridgeResponse>)
    private var captureAttempt: CaptureAttempt? = null
    private var captureId: String? = null

    /** Granted capability IDs in contract order; a broker is only attached when this is non-empty. */
    val capabilities: List<String> = capabilities.toList()

    fun attachTo(request: Bundle) {
        request.putInt(BunRuntimeContract.KEY_HOST_CAPABILITY_BRIDGE_VERSION, BunRuntimeContract.HOST_CAPABILITY_BRIDGE_VERSION)
        request.putBinder(BunRuntimeContract.KEY_HOST_CAPABILITY_BROKER, exported)
        request.putStringArrayList(BunRuntimeContract.KEY_HOST_CAPABILITIES, ArrayList(capabilities))
    }

    /** After this no call is answered; the plugin fails its pending calls itself when the run ends. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        exported.clear()
        callExecutor.close()
        automation.close()
        synchronized(captureLock) { captureAttempt = null; captureId = null }
        val provider = synchronized(imageScopeLock) { imageProvider.also { imageProvider = null } }
        provider?.destroy(NodeBridgeError.processDead("Bun execution finished"))
    }

    /** The exported Binder handed to the plugin. */
    val binder: IBinder
        get() = exported

    fun invoke(request: Bundle?, callback: IBunHostCapabilityCallback?) {
        if (closed.get() || Binder.getCallingUid() != expectedCallerUid) return
        if (request == null || callback == null) return
        if (request.getInt(BunRuntimeContract.KEY_HOST_CAPABILITY_BRIDGE_VERSION, -1) != BunRuntimeContract.HOST_CAPABILITY_BRIDGE_VERSION) return
        if (request.getString(BunRuntimeContract.KEY_EXECUTION_ID) != executionId) return
        val callId = request.getString(BunRuntimeContract.KEY_HOST_CALL_ID)
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_CALL_ID_CHARS }
            ?: return
        val capability = request.getString(BunRuntimeContract.KEY_HOST_CAPABILITY).orEmpty()
        val json = request.getString(BunRuntimeContract.KEY_HOST_CALL_REQUEST_JSON).orEmpty()
        // Oneway transactions on this Binder object are serialized. Never wait for UI events here.
        if (!callExecutor.submit { execute(callId, capability, json, callback) }) {
            respond(callId, Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_TOO_MANY_REQUESTS, "Host call capacity reached"), callback)
        }
    }

    private fun execute(callId: String, capability: String, json: String, callback: IBunHostCapabilityCallback) {
        if (closed.get()) return
        // Flow, speech, EPUB and mail stream without a finite script-call budget.
        val isStreaming = capability in setOf(BunHostCapabilityGrants.AGENT_CONTROL, BunHostCapabilityGrants.AI_CONTROL, BunHostCapabilityGrants.FLOW_CONTROL, BunHostCapabilityGrants.SPEECH_CONTROL, BunHostCapabilityGrants.MAIL_CONTROL, BunHostCapabilityGrants.EPUB_CONTROL)
        val outcome = if (!isStreaming && calls.incrementAndGet() > BunRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION) {
            val identity=Binder.clearCallingIdentity()
            try { synchronized(imageScopeLock) { imageProvider }?.let { it.closeShellProcesses(); it.closeEventResources(); it.closeDeviceDialogResources(); it.closeAndroidObjects(); it.closeJavaObjects() } } finally { Binder.restoreCallingIdentity(identity) }
            Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_TOO_MANY_REQUESTS, "Call limit per execution reached")
        } else {
            try {
                val identity = Binder.clearCallingIdentity()
                try {
                    dispatch(capability, json)
                } finally {
                    Binder.restoreCallingIdentity(identity)
                }
            } catch (error: IllegalArgumentException) {
                Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, error.message ?: "Invalid capability arguments")
            } catch (error: SecurityException) {
                Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED, error.message ?: "Android permission is not granted")
            } catch (error: Throwable) {
                Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INTERNAL, "Host capability failed")
            }
        }
        respond(callId, outcome, callback)
    }

    private fun respond(callId: String, outcome: Outcome, callback: IBunHostCapabilityCallback) {
        if (closed.get()) return
        val result = Bundle().apply {
            putString(BunRuntimeContract.KEY_EXECUTION_ID, executionId)
            putString(BunRuntimeContract.KEY_HOST_CALL_ID, callId)
            when (outcome) {
                is Outcome.Success -> {
                    putBoolean(BunRuntimeContract.KEY_SUCCEEDED, true)
                    putString(BunRuntimeContract.KEY_HOST_CALL_RESULT_JSON, outcome.json)
                }
                is Outcome.Failure -> {
                    putBoolean(BunRuntimeContract.KEY_SUCCEEDED, false)
                    putString(BunRuntimeContract.KEY_ERROR_CODE, outcome.code)
                    putString(BunRuntimeContract.KEY_ERROR_MESSAGE, outcome.message)
                }
            }
        }
        try {
            callback.onResult(result)
        } catch (error: RemoteException) {
            // The plugin process is gone; the run is over anyway.
        }
    }

    private fun dispatch(capability: String, requestJson: String): Outcome {
        if (capability !in capabilities) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED, "Capability is not granted for this execution")
        }
        if (requestJson.toByteArray(Charsets.UTF_8).size > BunRuntimeContract.MAX_HOST_CALL_REQUEST_BYTES) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_PAYLOAD_TOO_LARGE, "Request exceeds ${BunRuntimeContract.MAX_HOST_CALL_REQUEST_BYTES} bytes")
        }
        val request = try {
            JSONObject(requestJson.ifBlank { "{}" })
        } catch (error: JSONException) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "Request is not a JSON object")
        }
        val outcome = when (capability) {
            BunHostCapabilityGrants.APP_CONTROL, BunHostCapabilityGrants.ACCESSIBILITY_CONTROL,
            BunHostCapabilityGrants.CLIPBOARD_CONTROL, BunHostCapabilityGrants.STORAGE_CONTROL,
            BunHostCapabilityGrants.DATABASE_CONTROL, BunHostCapabilityGrants.SENSORS_CONTROL, BunHostCapabilityGrants.SHELL_CONTROL, BunHostCapabilityGrants.EVENTS_OBSERVE, BunHostCapabilityGrants.NOTIFICATIONS_POST, BunHostCapabilityGrants.DEVICE_CONTROL, BunHostCapabilityGrants.DEVICE_AUDIO, BunHostCapabilityGrants.DIALOGS_CONTROL, BunHostCapabilityGrants.ANDROID_OBJECTS, BunHostCapabilityGrants.TASKS_CONTROL, BunHostCapabilityGrants.JAVA_INTEROP, BunHostCapabilityGrants.TTS_CONTROL, BunHostCapabilityGrants.MEDIAINFO_CONTROL, BunHostCapabilityGrants.AGENT_CONTROL, BunHostCapabilityGrants.AI_CONTROL, BunHostCapabilityGrants.FLOW_CONTROL, BunHostCapabilityGrants.SPEECH_CONTROL, BunHostCapabilityGrants.MAIL_CONTROL, BunHostCapabilityGrants.EPUB_CONTROL,
            BunHostCapabilityGrants.AUTOJS_CONTROL, BunHostCapabilityGrants.SETTINGS_CONTROL, BunHostCapabilityGrants.SYSPROPS_CONTROL, BunHostCapabilityGrants.MEMORY_CONTROL,
            BunHostCapabilityGrants.INSTALLER_CONTROL, BunHostCapabilityGrants.ENGINES_CONTROL, BunHostCapabilityGrants.PLUGIN_TOOLS_CONTROL -> bridgeResponse(automation.invoke(capability, request))
            BunHostCapabilityGrants.FILES_CONTROL -> {
                try {
                    val (method, args) = parseMethodCall(request)
                    val value = files.invoke(method, List(args.length()) { args.opt(it).takeUnless { value -> value === JSONObject.NULL } })
                    Outcome.Success(JSONObject().put("value", JSONObject.wrap(value)).toString())
                }
                catch (error: BunFilePayloadException) { Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_PAYLOAD_TOO_LARGE, error.message!!) }
                catch (error: java.io.IOException) { Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INTERNAL, error.message ?: "File operation failed") }
            }
            BunHostCapabilityGrants.UI_LAYOUT, BunHostCapabilityGrants.UI_COMPOSE -> {
                val (method, args) = parseMethodCall(request)
                val compose = capability == BunHostCapabilityGrants.UI_COMPOSE
                require(method in if (compose) RuntimeComposeBridge.methods else NodeBridgeModules.uiMethods) { "Unsupported UI operation" }
                awaitImageCall(if (compose) NodeBridgeModules.COMPOSE else NodeBridgeModules.UI, method, args)
            }
            BunRuntimeContract.HOST_CAPABILITY_DEVICE_INFO -> deviceInfo(request)
            BunRuntimeContract.HOST_CAPABILITY_UI_TOAST -> toast(request)
            BunHostCapabilityGrants.TEXT_PINYIN -> textCall(request, NodeBridgeModules.PINYIN, PINYIN_METHODS) {
                NodePinyinBridge.dispatch(it, pinyin)
            }
            BunHostCapabilityGrants.TEXT_PINYIN4J -> textCall(request, NodeBridgeModules.PINYIN4J, PINYIN4J_METHODS) {
                NodePinyin4jBridge.dispatch(it, pinyin4j)
            }
            BunHostCapabilityGrants.TEXT_OPENCC -> textCall(request, NodeBridgeModules.OPENCC, NodeOpenccBridge.METHODS) {
                NodeOpenccBridge.dispatch(it, opencc)
            }
            BunHostCapabilityGrants.IMAGE_PROCESS -> imageCall(request, IMAGE_PROCESS_METHODS)
            BunHostCapabilityGrants.IMAGE_FILES -> imageCall(request, IMAGE_FILE_METHODS)
            BunHostCapabilityGrants.SCREEN_CAPTURE -> screenCaptureCall(request)
            BunHostCapabilityGrants.SCREEN_RECORD -> screenRecordingCall(request)
            BunHostCapabilityGrants.SCREEN_METRICS -> methodCall(request, coordinates::invoke)
            BunHostCapabilityGrants.CONSOLE_CONTROL -> methodCall(request, console::invoke)
            else -> Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_UNKNOWN_CAPABILITY, "Capability is not implemented by this host")
        }
        if (outcome is Outcome.Success && outcome.json.toByteArray(Charsets.UTF_8).size > BunRuntimeContract.MAX_HOST_CALL_RESULT_BYTES) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_PAYLOAD_TOO_LARGE, "Result exceeds ${BunRuntimeContract.MAX_HOST_CALL_RESULT_BYTES} bytes")
        }
        return outcome
    }

    private fun parseMethodCall(request: JSONObject): Pair<String, JSONArray> {
        require(request.keys().asSequence().all { it == "method" || it == "args" }) {
            "Capability call expects {method, args?}"
        }
        val method = request.opt("method") as? String ?: throw IllegalArgumentException("method must be a string")
        val args = if (!request.has("args")) JSONArray() else request.optJSONArray("args")
            ?: throw IllegalArgumentException("args must be an array")
        return method to args
    }

    private fun methodCall(request: JSONObject, invoke: (String, JSONArray) -> Any?): Outcome {
        val (method, args) = parseMethodCall(request)
        val result = invoke(method, args)
        return Outcome.Success(JSONObject().put("value", result ?: JSONObject.NULL).toString())
    }

    private fun textCall(
        request: JSONObject,
        module: String,
        methods: Set<String>,
        dispatch: (NodeBridgeRequest) -> NodeBridgeResponse,
    ): Outcome {
        val (method, args) = parseMethodCall(request)
        require(method in methods) { "Unsupported $module method: $method" }
        val response = dispatch(NodeBridgeRequest(executionId, module, method, args.toString(), BunRuntimeContract.HOST_CALL_TIMEOUT_MILLIS.toLong()))
        return bridgeResponse(response)
    }

    private fun bridgeResponse(response: NodeBridgeResponse): Outcome {
        if (response.ok) {
            // The Bun wire contract requires an outer object, including for strings, matrices and null.
            return Outcome.Success("{\"value\":${response.resultJson ?: "null"}}")
        }
        val error = response.error
        val code = when (error?.category) {
            NodeBridgeErrorCategory.INVALID_REQUEST -> BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST
            NodeBridgeErrorCategory.PERMISSION_DENIED, NodeBridgeErrorCategory.CAPABILITY_DENIED -> BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED
            NodeBridgeErrorCategory.TIMEOUT -> BunRuntimeContract.HOST_CALL_ERROR_TIMEOUT
            NodeBridgeErrorCategory.UNAVAILABLE, NodeBridgeErrorCategory.PROCESS_DEAD -> BunRuntimeContract.HOST_CALL_ERROR_HOST_UNAVAILABLE
            NodeBridgeErrorCategory.RESOURCE_LIMIT -> BunRuntimeContract.HOST_CALL_ERROR_QUOTA_EXCEEDED
            NodeBridgeErrorCategory.RATE_LIMITED -> BunRuntimeContract.HOST_CALL_ERROR_TOO_MANY_REQUESTS
            else -> BunRuntimeContract.HOST_CALL_ERROR_INTERNAL
        }
        return Outcome.Failure(code, error?.message ?: "Host capability failed")
    }

    private fun imagesProvider(): AndroidNodeBridgeCapabilityProvider = synchronized(imageScopeLock) {
            check(!closed.get()) { "Bun execution has finished" }
            imageProvider ?: AndroidNodeBridgeCapabilityProvider(
                appContext,
                engineInfo = NodeBridgeEngineInfo.fromContext(appContext, id = executionId, sourceName = sourceName, cwd = workingDirectory.orEmpty(), spawnDepth = spawnDepth)
                    .copy(engineName = "bun", backend = "plugin"),
                javaInteropEnabled = BunHostCapabilityGrants.JAVA_INTEROP in capabilities,
                agentExecutionState = agentExecutionState,
                permissionManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(buildList {
                    add(NodeBridgePermissionManifest.IMAGE)
                    addAll(BunAutomationBridge.nodeCapabilities(capabilities))
                    if (BunHostCapabilityGrants.UI_LAYOUT in capabilities || BunHostCapabilityGrants.UI_COMPOSE in capabilities) add(NodeBridgePermissionManifest.UI)
                    if (BunHostCapabilityGrants.IMAGE_FILES in capabilities) add(NodeBridgePermissionManifest.NETWORK)
                    if (BunHostCapabilityGrants.SCREEN_CAPTURE in capabilities) add(NodeBridgePermissionManifest.SCREEN_CAPTURE)
                }),
            ).also { imageProvider = it }
    }

    /** Reuses Node's codec, serial image lane, execution-owned handles and resource cleanup. */
    private fun imageCall(request: JSONObject, methods: Set<String>): Outcome {
        val (method, args) = parseMethodCall(request)
        require(method in methods) { "Image method is not part of this capability: $method" }
        if (method == "quantizeToFile" && BunHostCapabilityGrants.IMAGE_PROCESS !in capabilities)
            throw SecurityException("PNG quantization requires the image.process grant.")
        if (method == "matchFeatures" && args.optJSONObject(2)?.has("drawMatches") == true &&
            !args.getJSONObject(2).isNull("drawMatches") && BunHostCapabilityGrants.IMAGE_FILES !in capabilities)
            throw SecurityException("Drawing feature matches requires the image.files grant.")
        return awaitImageCall(NodeBridgeModules.IMAGES, method, args)
    }

    private fun awaitImageCall(module: String, method: String, args: JSONArray): Outcome {
        val result = CompletableFuture<NodeBridgeResponse>()
        imagesProvider().dispatch(NodeBridgeRequest(
            id = "$executionId-image-${UUID.randomUUID()}", module = module, method = method,
            argsJson = args.toString(), timeoutMs = IMAGE_CALL_TIMEOUT_MILLIS,
            permissions = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method),
        )) { response -> result.complete(response) }
        return try {
            bridgeResponse(result.get(IMAGE_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        } catch (_: TimeoutException) {
            Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_TIMEOUT, "Image operation did not finish within $IMAGE_CALL_TIMEOUT_MILLIS ms")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_HOST_UNAVAILABLE, "Image execution is finishing")
        }
    }

    /** Consent can outlive one 10 s bridge call. Polls wait at most 1 s, leaving stop/exit bounded. */
    private fun screenCaptureCall(request: JSONObject): Outcome {
        val (method, args) = parseMethodCall(request)
        require(method in SCREEN_CAPTURE_METHODS) { "Unsupported screen capture method: $method" }
        return when (method) {
            "requestScreenCapture" -> {
                require(args.length() <= 1 && (args.length() == 0 || args.opt(0) is JSONObject)) { "Capture options must be an object" }
                val options = args.optJSONObject(0) ?: JSONObject()
                val duration = if (options.has("timeoutMs")) (options.opt("timeoutMs") as? Number)?.toDouble() else 120_000.0
                require(duration != null && duration.isFinite() && duration % 1 == 0.0 && duration in 1.0..120_000.0) { "Consent timeoutMs must be an integer in 1..120000" }
                val attempt = synchronized(captureLock) {
                    require(captureAttempt?.result?.isDone != false) { "Screen capture consent is already pending" }
                    CaptureAttempt(UUID.randomUUID().toString(), CompletableFuture()).also { captureAttempt = it }
                }
                val provider = imagesProvider()
                provider.dispatch(NodeBridgeRequest(
                    id = "$executionId-consent-${attempt.id}", module = NodeBridgeModules.MEDIA_PROJECTION,
                    method = "requestScreenCapture", argsJson = JSONArray().put(options).toString(),
                    timeoutMs = duration.toLong(), permissions = listOf(NodeBridgePermissionManifest.SCREEN_CAPTURE),
                )) { response ->
                    val id = if (response.ok) JSONObject(response.resultJson!!).getString("id") else null
                    val accepted = synchronized(captureLock) {
                        if (!closed.get() && captureAttempt === attempt) {
                            if (id != null) captureId = id
                            attempt.result.complete(response)
                            true
                        } else { attempt.result.complete(response); false }
                    }
                    if (!accepted && id != null) provider.dispatch(NodeBridgeRequest(
                        id = "$executionId-late-consent", module = NodeBridgeModules.MEDIA_PROJECTION, method = "stop",
                        argsJson = JSONArray().put(id).toString(), timeoutMs = IMAGE_CALL_TIMEOUT_MILLIS,
                        permissions = listOf(NodeBridgePermissionManifest.SCREEN_CAPTURE),
                    )) { }
                }
                value(JSONObject().put("requestId", attempt.id))
            }
            "getRequestResult" -> {
                require(args.length() == 1 && args.opt(0) is String) { "Consent request id is required" }
                val attempt = synchronized(captureLock) { captureAttempt?.takeIf { it.id == args.getString(0) } }
                    ?: return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "Consent request has ended or was replaced")
                try {
                    val response = attempt.result.get(1, TimeUnit.SECONDS)
                    if (response.ok) value(JSONObject().put("state", "granted")) else bridgeResponse(response)
                } catch (_: TimeoutException) { value(JSONObject().put("state", "pending")) }
            }
            "getScreenCaptureOptions" -> { require(args.length() == 0); awaitImageCall(NodeBridgeModules.IMAGES, method, args) }
            "captureScreen" -> {
                require(args.length() == 0) { "captureScreen takes no arguments" }
                val id = synchronized(captureLock) { captureId }
                    ?: return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_NOT_GRANTED, "Request Android screen capture consent first")
                awaitImageCall(NodeBridgeModules.MEDIA_PROJECTION, "nextImage", JSONArray().put(id))
            }
            else -> {
                require(args.length() == 0) { "stopScreenCapture takes no arguments" }
                val hadSession = synchronized(captureLock) {
                    val existed = captureAttempt != null || captureId != null
                    captureAttempt = null; captureId = null
                    existed
                }
                val stopped = awaitImageCall(NodeBridgeModules.MEDIA_PROJECTION, "stop", JSONArray())
                if (stopped is Outcome.Failure) stopped else value(hadSession)
            }
        }
    }

    private fun value(value: Any) = Outcome.Success(JSONObject().put("value", value).toString())

    private fun screenRecordingCall(request: JSONObject): Outcome {
        if (BunHostCapabilityGrants.SCREEN_CAPTURE !in capabilities) throw SecurityException("Screen recording also requires the screen.capture grant.")
        val (method, args) = parseMethodCall(request)
        require(method in SCREEN_RECORD_METHODS) { "Unsupported screen recording method: $method" }
        if (method == "start") {
            val audio = NodeScreenRecording.audioSource(args.optJSONObject(1) ?: JSONObject())
            if ((audio == "microphone" || audio == "mixed") && BunHostCapabilityGrants.SCREEN_RECORD_MICROPHONE !in capabilities)
                throw SecurityException("Screen microphone recording requires the screen.record.microphone grant.")
            if ((audio == "playback" || audio == "mixed") && BunHostCapabilityGrants.SCREEN_RECORD_PLAYBACK !in capabilities)
                throw SecurityException("Playback audio recording requires the screen.record.playback grant.")
        }
        val result = CompletableFuture<NodeBridgeResponse>()
        imagesProvider().dispatchScreenRecording(NodeBridgeRequest(
            id = "$executionId-recording-${UUID.randomUUID()}", module = NodeBridgeModules.MEDIA_PROJECTION,
            method = method, argsJson = args.toString(), timeoutMs = IMAGE_CALL_TIMEOUT_MILLIS,
        )) { result.complete(it) }
        return try { bridgeResponse(result.get(IMAGE_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) }
        catch (_: TimeoutException) { Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_TIMEOUT, "Screen recording operation timed out") }
    }

    /** `{}` in, the `android.os.Build` facts out; never a serial, IMEI, Android ID, account or network identifier. */
    private fun deviceInfo(request: JSONObject): Outcome {
        if (request.length() != 0) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "device.info takes no fields")
        }
        val json = JSONObject()
            .put("brand", Build.BRAND.orEmpty())
            .put("manufacturer", Build.MANUFACTURER.orEmpty())
            .put("model", Build.MODEL.orEmpty())
            .put("device", Build.DEVICE.orEmpty())
            .put("product", Build.PRODUCT.orEmpty())
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE.orEmpty())
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.orEmpty().toList()))
        return Outcome.Success(json.toString())
    }

    /** `{"text": string, "duration"?: "short" | "long"}`; text is trimmed, 1..1024 UTF-8 bytes, newlines only. */
    private fun toast(request: JSONObject): Outcome {
        val keys = request.keys().asSequence().toSet()
        if (TOAST_TEXT_KEY !in keys || !TOAST_KEYS.containsAll(keys)) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast expects {\"text\", \"duration\"?}")
        }
        val text = (request.opt(TOAST_TEXT_KEY) as? String)?.trim()
            ?: return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast text must be a string")
        if (text.toByteArray(Charsets.UTF_8).size !in 1..MAX_TOAST_TEXT_BYTES) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast text must be 1..$MAX_TOAST_TEXT_BYTES UTF-8 bytes")
        }
        if (text.any { it != '\n' && it.isISOControl() }) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast text contains control characters")
        }
        val durationValue: Any? = request.opt(TOAST_DURATION_KEY)
        val duration = when (durationValue) {
            null, TOAST_DURATION_SHORT -> Toast.LENGTH_SHORT
            TOAST_DURATION_LONG -> Toast.LENGTH_LONG
            else -> return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_INVALID_REQUEST, "ui.toast duration must be \"short\" or \"long\"")
        }
        if (toasts.incrementAndGet() > MAX_TOAST_CALLS_PER_EXECUTION) {
            return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_QUOTA_EXCEEDED, "ui.toast allows $MAX_TOAST_CALLS_PER_EXECUTION calls per run")
        }
        // The Runnable keeps what it shows, not the broker (C13).
        // zh-CN: Runnable 只保留要显示的内容, 不保留 broker (C13).
        val closed = closed
        val appContext = appContext
        val posted = mainHandler.post {
            if (!closed.get()) Toast.makeText(appContext, text, duration).show()
        }
        if (!posted) return Outcome.Failure(BunRuntimeContract.HOST_CALL_ERROR_HOST_UNAVAILABLE, "Host UI lane is unavailable")
        return Outcome.Success("{\"shown\":true}")
    }

    /**
     * The exported side of a broker: forwards to it until [clear], then ignores every call, as a closed broker does.
     * zh-CN: broker 的导出端: [clear] 之前转发给 broker, 之后与已关闭的 broker 一样忽略所有调用.
     */
    private class ExportedBroker(broker: BunHostCapabilityBroker) : IBunHostCapabilityBroker.Stub() {

        private val target = AtomicReference<BunHostCapabilityBroker?>(broker)

        fun clear() {
            target.set(null)
        }

        override fun invoke(request: Bundle?, callback: IBunHostCapabilityCallback?) {
            target.get()?.invoke(request, callback)
        }
    }

    private sealed interface Outcome {
        class Success(val json: String) : Outcome
        class Failure(val code: String, val message: String) : Outcome
    }

    private companion object {
        const val MAX_CALL_ID_CHARS = 256
        val PINYIN_METHODS = setOf("convert", "simple", "compare", "compact", "fromCodePoint", "fromPhrase")
        val PINYIN4J_METHODS = setOf("of", "as", "readings")
        const val IMAGE_CALL_TIMEOUT_MILLIS = 9_000L
        val IMAGE_PROCESS_METHODS = setOf(
            "fromBase64", "toBase64", "pixel", "readPixels", "copy", "clip", "resize", "scale", "rotate", "flip",
            "grayscale", "threshold", "blur", "gaussianBlur", "medianBlur", "invert",
            "findImage", "matchTemplate", "findColor", "findMultiColors", "detectsColor", "findAllPointsForColor", "recycle",
            "initOpenCvIfNeeded", "isGrayscale", "inRange", "interval", "adaptiveThreshold", "bilateralFilter", "cvtColor", "findCircles", "concat", "detectMultiColors", "findPointsByColors", "countPointsByColor", "getMeanColor", "matToImage", "detectAndComputeFeatures", "matchFeatures", "quantize", "compress", "compressToBytes", "downsample", "psnr", "ssim", "mssim", "hist", "mse", "ncc", "isEqual", "getSimilarity",
        )
        val IMAGE_FILE_METHODS = setOf("readImage", "saveImage", "imread", "load", "quantizeToFile", "recycle")
        val SCREEN_CAPTURE_METHODS = setOf("requestScreenCapture", "getRequestResult", "captureScreen", "stopScreenCapture", "getScreenCaptureOptions")
        val SCREEN_RECORD_METHODS = setOf("start", "stop", "cancel", "getStatus")
        const val TOAST_TEXT_KEY = "text"
        const val TOAST_DURATION_KEY = "duration"
        const val TOAST_DURATION_SHORT = "short"
        const val TOAST_DURATION_LONG = "long"
        val TOAST_KEYS = setOf(TOAST_TEXT_KEY, TOAST_DURATION_KEY)
        const val MAX_TOAST_TEXT_BYTES = 1024
        const val MAX_TOAST_CALLS_PER_EXECUTION = 4
    }
}
