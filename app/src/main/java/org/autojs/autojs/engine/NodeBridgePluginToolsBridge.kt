package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.ServiceBindingLease
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.tool.PluginToolCatalogParser
import org.autojs.autojs.core.plugin.tool.PluginToolDefinition
import org.autojs.autojs.core.plugin.tool.PluginToolInput
import org.autojs.autojs.core.plugin.tool.PluginToolInvocationPlan
import org.autojs.autojs.core.plugin.tool.PluginToolInvocationPlanner
import org.autojs.autojs.core.plugin.tool.PluginToolPlanResult
import org.autojs.autojs.core.plugin.tool.PluginToolProvider
import org.autojs.autojs.core.plugin.tool.PluginToolProviderHost
import org.autojs.autojs.core.plugin.tool.PluginToolProviderStatus
import org.autojs.autojs.core.plugin.tool.PluginToolRegistry
import org.autojs.autojs.core.plugin.tool.PluginToolSchema
import org.autojs.plugin.tool.api.IPluginToolCallback
import org.autojs.plugin.tool.api.IPluginToolProvider
import org.autojs.plugin.tool.api.PluginToolAcceptKeys
import org.autojs.plugin.tool.api.PluginToolCancelKeys
import org.autojs.plugin.tool.api.PluginToolContract
import org.autojs.plugin.tool.api.PluginToolErrorCodes
import org.autojs.plugin.tool.api.PluginToolErrorKeys
import org.autojs.plugin.tool.api.PluginToolInputJson
import org.autojs.plugin.tool.api.PluginToolInvokeKeys
import org.autojs.plugin.tool.api.PluginToolLimits
import org.autojs.plugin.tool.api.PluginToolOutputJson
import org.autojs.plugin.tool.api.PluginToolResultKeys
import org.autojs.plugin.tool.api.PluginToolValues
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where image / file arguments are read and binary outputs are written; failures throw [SecurityException] with the
 * reason the caller should see. Bridge sessions use their path policy ([of]); the script API resolves like `files.path`.
 * zh-CN: 图片 / 文件参数的读取位置与二进制输出的写入位置; 失败时抛出带原因的 [SecurityException].
 */
internal interface PluginToolPaths {

    fun read(path: String, permissions: Collection<String>, label: String): File

    fun write(path: String, label: String): File

    companion object {

        fun of(policy: NodeBridgeModelPathPolicy): PluginToolPaths = object : PluginToolPaths {
            override fun read(path: String, permissions: Collection<String>, label: String) = policy.resolveRead(path, permissions, label).file
            override fun write(path: String, label: String) = policy.resolveWrite(path, label).file
        }
    }
}

/**
 * The `plugin_tools` bridge module (MCP Server roadmap P12 D29 / D32 / D34, docs/dev/plugin-tool-protocol-v1.md):
 * `list` returns the tools of the available providers, `invoke` runs one call and `cancel` stops it. The host checks
 * the arguments against the tool's schema, resolves image arguments (`"screen"` is captured like
 * `accessibility.captureScreenImage`) and file arguments within the session's path policy into descriptors, writes the
 * binary outputs atomically to the working-directory paths the caller names, enforces the time limit and tells the
 * provider when a call is cancelled or timed out. Tools of USER_GRANTED providers are listed and run only for requests
 * that declare `plugin_tools.user_granted`; the screen needs `screen_capture`, paths need `files`, outputs need
 * `files.write`.
 *
 * zh-CN: `plugin_tools` 桥接模块: `list` 返回可用提供者的工具, `invoke` 执行一次调用, `cancel` 停止调用. 宿主按工具 schema 校验参数,
 * 把图片参数 (`"screen"` 按截屏处理) 与文件参数在会话路径策略内解析为描述符, 把二进制输出原子写入调用方指定的工作目录路径, 执行时限,
 * 并在取消或超时时通知提供者. USER_GRANTED 提供者的工具只对声明了 `plugin_tools.user_granted` 的请求列出与执行.
 */
internal class NodeBridgePluginToolsBridge(
    context: Context,
    private val paths: PluginToolPaths,
    private val consumer: String,
    private val captureScreen: (Long) -> Bitmap,
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = ConcurrentHashMap<String, Operation>()
    private val destroyed = AtomicBoolean(false)

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (destroyed.get()) return callback(failure(request, NodeBridgeError.processDead("AutoJs6 plugin_tools bridge is closed.")))
        when (request.method) {
            "list" -> scope.launch { callback(list(request)) }
            "invoke" -> invoke(request, callback)
            "cancel" -> callback(cancel(request))
            else -> callback(failure(request, NodeBridgeError.bridgeUnavailable(request.module, request.method)))
        }
    }

    /** Stops every running call (the provider is told with reason `host`). */
    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        operations.values.toList().forEach { operation ->
            operation.finish(
                failure(operation.request, NodeBridgeError.processDead("AutoJs6 plugin_tools session closed while ${operation.toolName} was running.")),
                cancelReason = PluginToolValues.CANCEL_REASON_HOST,
            )
        }
        scope.cancel()
    }

    private suspend fun list(request: NodeBridgeRequest): NodeBridgeResponse = runCatching {
        PluginToolRegistry.refresh(appContext)
        val userGranted = declares(request, NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED)
        val providers = PluginToolRegistry.providers
        val tools = JSONArray()
        providers.filter { it.status == PluginToolProviderStatus.AVAILABLE && admits(it, userGranted) }.forEach { provider ->
            provider.catalog?.tools?.forEach { tools.put(toolJson(provider, it)) }
        }
        val providerArray = JSONArray()
        providers.forEach { provider ->
            providerArray.put(JSONObject().apply {
                put("packageName", provider.packageName)
                put("versionCode", provider.versionCode)
                put("trust", provider.trust.name.lowercase())
                put("status", provider.status.name.lowercase())
                provider.catalog?.let { catalog ->
                    put("shortName", catalog.shortName)
                    catalog.sharedFamily?.let { put("sharedFamily", it) }
                    put("tools", catalog.tools.size)
                    put("diagnostics", JSONArray(catalog.diagnostics))
                }
                provider.detail?.let { put("detail", it) }
                if (provider.status == PluginToolProviderStatus.AVAILABLE && !admits(provider, userGranted)) put("toolsHidden", true)
            })
        }
        success(request, JSONObject().put("schema", LIST_SCHEMA).put("tools", tools).put("providers", providerArray))
    }.getOrElse { error ->
        failure(request, NodeBridgeError.providerFailed("plugin_tools.list failed: ${error.message}", request.module, request.method, code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED))
    }

    private fun invoke(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val args = runCatching { JsonParser.parseString(request.argsJson.ifBlank { "[]" }).asJsonArray }.getOrElse { JsonArray() }
        val name = args.stringAt(0) ?: return callback(invalid(request, "plugin_tools.invoke requires the tool name as the first argument"))
        val options = args.objectAt(2) ?: JsonObject()
        val invocationId = options.stringOf("invocationId") ?: UUID.randomUUID().toString()
        if (!INVOCATION_ID.matches(invocationId)) return callback(invalid(request, "invocationId must have 1 to 128 letters, digits, '.', '_', ':' or '-'"))
        val overwrite = (options.get("overwrite") as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean ?: false
        val requestedTimeout = (options.get("timeoutMs") as? JsonPrimitive)?.takeIf { it.isNumber }?.asLong
        val ceiling = request.timeoutMs - DEADLINE_MARGIN_MS
        val operation = Operation(request, callback, invocationId, name)
        if (operations.putIfAbsent(invocationId, operation) != null) return callback(invalid(request, "invocationId $invocationId is already running"))
        scope.launch {
            runCatching { start(operation, args.getOrNull(1), options.get("outputs") as? JsonObject, overwrite, requestedTimeout, ceiling) }.onFailure { error ->
                operation.finish(failure(request, startFailure(request, name, error)))
            }
        }
    }

    private suspend fun start(operation: Operation, arguments: JsonElement?, outputs: JsonObject?, overwrite: Boolean, requestedTimeout: Long?, ceiling: Long) {
        val request = operation.request
        PluginToolRegistry.refresh(appContext)
        val (provider, tool) = PluginToolRegistry.tools.firstOrNull { (_, tool) -> tool.fullName == operation.toolName }
            ?: throw RequestProblem(NodeBridgeError.invalidRequest("no plugin tool named ${operation.toolName}; call plugin_tools.list", request.module, request.method))
        if (!admits(provider, declares(request, NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED))) {
            throw RequestProblem(NodeBridgeError.permissionDenied(
                "${tool.fullName} comes from the USER_GRANTED plugin ${provider.packageName}; the request must declare ${NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED}",
                request.module, request.method,
            ))
        }
        val plan = when (val result = PluginToolInvocationPlanner.plan(tool, arguments, outputs, requestedTimeout, ceiling)) {
            is PluginToolPlanResult.Invalid -> throw RequestProblem(NodeBridgeError.invalidRequest("plugin_tools.invoke: ${result.message}", request.module, request.method))
            is PluginToolPlanResult.Ready -> result.plan
        }
        if (plan.inputs.any { it.isScreen }) requireToken(request, NodeBridgePermissionManifest.SCREEN_CAPTURE, "capture the screen for ${tool.fullName}")
        if (plan.inputs.any { !it.isScreen }) requireToken(request, NodeBridgePermissionManifest.FILES, "read files for ${tool.fullName}")
        if (plan.outputs.isNotEmpty()) requireToken(request, NodeBridgePermissionManifest.FILES_WRITE, "write the outputs of ${tool.fullName}")

        val targets = plan.outputs.mapValues { (name, path) ->
            val file = resolve { paths.write(path, "plugin_tools.invoke output $name") }
            if (tool.outputs.firstOrNull { it.name == name }?.isDirectory == true) {
                // A directory output may land in an existing directory (MCP roadmap D56); its files are checked one by one.
                if (file.exists() && !file.isDirectory) throw RequestProblem(NodeBridgeError.invalidRequest("output $name points at a file: $path", request.module, request.method))
                return@mapValues OutputTarget(path, file, PluginToolOutputTree(file, overwrite, ".${file.name}.${UUID.randomUUID()}.partial"))
            }
            if (file.isDirectory) throw RequestProblem(NodeBridgeError.invalidRequest("output $name points at a directory: $path", request.module, request.method))
            if (file.exists() && !overwrite) throw RequestProblem(NodeBridgeError.invalidRequest("output $name already exists: $path (pass overwrite: true to replace it)", request.module, request.method))
            OutputTarget(path, file)
        }
        operation.prepare(tool, provider, plan, targets, overwrite)
        mainHandler.postDelayed(operation.timeout, plan.timeoutMs)

        val resolved = resolveInputs(request, plan)
        try {
            val bundle = Bundle().apply {
                putInt(PluginToolInvokeKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
                putString(PluginToolInvokeKeys.INVOCATION_ID, operation.invocationId)
                putString(PluginToolInvokeKeys.TOOL, tool.name)
                putString(PluginToolInvokeKeys.ARGUMENTS_JSON, plan.arguments.toString())
                putString(PluginToolInvokeKeys.INPUTS_JSON, JSONArray().apply { resolved.forEach { put(it.meta) } }.toString())
                putString(PluginToolInvokeKeys.OUTPUTS_JSON, JSONArray(plan.outputs.keys.toList()).toString())
                putLong(PluginToolInvokeKeys.TIMEOUT_MS, plan.timeoutMs)
                putString(PluginToolInvokeKeys.CONSUMER, consumer)
                val trees = targets.filterValues { it.tree != null }
                if (trees.isNotEmpty()) {
                    putBundle(PluginToolInvokeKeys.OUTPUT_TREES, Bundle().apply { trees.forEach { (name, target) -> putBinder(name, target.tree!!.asBinder()) } })
                }
            }
            if (operation.isFinished) return
            val target = provider.target ?: throw IOException("the provider ${provider.packageName} is not bound")
            val started = PluginToolProviderHost.invoke(appContext, target, bundle, resolved.map { it.descriptor }.toTypedArray(), operation.callback())
            operation.attach(started.provider, started.lease)
            if (!started.answer.getBoolean(PluginToolAcceptKeys.ACCEPTED, false)) {
                val code = started.answer.getString(PluginToolAcceptKeys.ERROR_CODE) ?: PluginToolErrorCodes.FAILED
                operation.finish(failure(request, pluginError(request, tool.fullName, code, started.answer.getString(PluginToolAcceptKeys.ERROR_MESSAGE) ?: "refused")))
            }
        } finally {
            // The provider received duplicates; the host closes its own copies.
            resolved.forEach { runCatching { it.descriptor.close() } }
        }
    }

    private fun resolveInputs(request: NodeBridgeRequest, plan: PluginToolInvocationPlan): List<ResolvedInput> {
        val resolved = mutableListOf<ResolvedInput>()
        try {
            for (input in plan.inputs) {
                when {
                    input.isScreen -> resolved += screenInput(input, plan.timeoutMs)
                    input.type == PluginToolValues.SPECIAL_TYPE_DIRECTORY -> directoryInputs(request, input, resolved)
                    else -> resolved += fileInput(request, input)
                }
                if (resolved.size > PluginToolLimits.MAX_INPUT_DESCRIPTORS) {
                    throw RequestProblem(NodeBridgeError.resourceLimit("the inputs need more than ${PluginToolLimits.MAX_INPUT_DESCRIPTORS} descriptors", request.module, request.method))
                }
            }
        } catch (error: Throwable) {
            resolved.forEach { runCatching { it.descriptor.close() } }
            throw error
        }
        return resolved
    }

    private fun screenInput(input: PluginToolInput, timeoutMs: Long): ResolvedInput {
        val bitmap = runCatching { captureScreen(timeoutMs) }.getOrElse { error ->
            throw RequestProblem(when (error) {
                is SecurityException -> NodeBridgeError.permissionDenied(error.message ?: "screen capture was refused", NodeBridgeModules.PLUGIN_TOOLS, "invoke")
                else -> NodeBridgeError.providerFailed("could not capture the screen: ${error.message}", NodeBridgeModules.PLUGIN_TOOLS, "invoke", code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED)
            })
        }
        val directory = File(appContext.cacheDir, INPUT_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" } }
            val meta = JSONObject()
                .put(PluginToolInputJson.NAME, input.parameter)
                .put(PluginToolInputJson.SOURCE, PluginToolValues.INPUT_SOURCE_SCREEN)
                .put(PluginToolInputJson.MIME_TYPE, "image/png")
                .put(PluginToolInputJson.BYTE_COUNT, file.length())
                .put(PluginToolInputJson.WIDTH, bitmap.width)
                .put(PluginToolInputJson.HEIGHT, bitmap.height)
            return ResolvedInput(meta, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        } finally {
            bitmap.recycle()
            // The open descriptor keeps the data; the name goes away at once.
            file.delete()
        }
    }

    private fun fileInput(request: NodeBridgeRequest, input: PluginToolInput): ResolvedInput {
        val file = resolve { paths.read(input.value, request.permissions, "plugin_tools.invoke ${input.parameter}") }
        if (!file.isFile) throw RequestProblem(NodeBridgeError.invalidRequest("${input.parameter} is not a readable file: ${input.value}", request.module, request.method))
        // Files are handed over without a copy; images are decoded by the provider and keep the descriptor ceiling (MCP roadmap D49).
        val ceiling = if (input.type == PluginToolValues.SPECIAL_TYPE_IMAGE) PluginToolLimits.MAX_DESCRIPTOR_BYTES.toLong() else PluginToolLimits.MAX_FILE_INPUT_BYTES
        if (file.length() > ceiling) {
            throw RequestProblem(NodeBridgeError.resourceLimit("${input.parameter} has ${file.length()} bytes, more than $ceiling", request.module, request.method))
        }
        val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        val meta = JSONObject()
            .put(PluginToolInputJson.NAME, input.parameter)
            .put(PluginToolInputJson.SOURCE, PluginToolValues.INPUT_SOURCE_FILE)
            .put(PluginToolInputJson.MIME_TYPE, mimeType)
            .put(PluginToolInputJson.DISPLAY_NAME, file.name)
            .put(PluginToolInputJson.BYTE_COUNT, file.length())
        if (input.type == PluginToolValues.SPECIAL_TYPE_IMAGE) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) meta.put(PluginToolInputJson.WIDTH, bounds.outWidth).put(PluginToolInputJson.HEIGHT, bounds.outHeight)
        }
        return ResolvedInput(meta, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    }

    /**
     * The files of a directory parameter (MCP roadmap D49): the names the tool asks for, each of which must exist, or
     * every regular file directly inside; each becomes one read-only descriptor, named by DISPLAY_NAME.
     */
    private fun directoryInputs(request: NodeBridgeRequest, input: PluginToolInput, into: MutableList<ResolvedInput>) {
        val directory = resolve { paths.read(input.value, request.permissions, "plugin_tools.invoke ${input.parameter}") }
        if (!directory.isDirectory) throw RequestProblem(NodeBridgeError.invalidRequest("${input.parameter} is not a readable directory: ${input.value}", request.module, request.method))
        val files = if (input.files.isNotEmpty()) {
            input.files.map { name ->
                File(directory, name).also { file ->
                    if (!file.isFile) throw RequestProblem(NodeBridgeError.invalidRequest("${input.parameter}: ${input.value} has no file named $name", request.module, request.method))
                }
            }
        } else {
            val all = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
            if (all.size > PluginToolLimits.MAX_DIRECTORY_FILES) {
                throw RequestProblem(NodeBridgeError.resourceLimit("${input.parameter}: ${input.value} has more than ${PluginToolLimits.MAX_DIRECTORY_FILES} files", request.module, request.method))
            }
            all
        }
        for (file in files) {
            if (file.length() > PluginToolLimits.MAX_FILE_INPUT_BYTES) {
                throw RequestProblem(NodeBridgeError.resourceLimit("${input.parameter}: ${file.name} has ${file.length()} bytes, more than ${PluginToolLimits.MAX_FILE_INPUT_BYTES}", request.module, request.method))
            }
            val meta = JSONObject()
                .put(PluginToolInputJson.NAME, input.parameter)
                .put(PluginToolInputJson.SOURCE, PluginToolValues.INPUT_SOURCE_DIRECTORY)
                .put(PluginToolInputJson.MIME_TYPE, URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream")
                .put(PluginToolInputJson.DISPLAY_NAME, file.name)
                .put(PluginToolInputJson.BYTE_COUNT, file.length())
            into += ResolvedInput(meta, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }
    }

    private fun cancel(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = runCatching { JsonParser.parseString(request.argsJson.ifBlank { "[]" }).asJsonArray }.getOrElse { JsonArray() }
        val invocationId = args.stringAt(0) ?: args.objectAt(0)?.stringOf("invocationId")
            ?: return invalid(request, "plugin_tools.cancel requires an invocationId")
        val operation = operations[invocationId]
        val cancelled = operation != null && operation.finish(
            failure(operation.request, NodeBridgeError.providerFailed(
                "plugin_tools.invoke: ${operation.toolName} was cancelled by the caller", operation.request.module, operation.request.method, code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
            )),
            cancelReason = PluginToolValues.CANCEL_REASON_CALLER,
        )
        return success(request, JSONObject().put("schema", CANCEL_SCHEMA).put("invocationId", invocationId).put("cancelled", cancelled))
    }

    /** Where one requested output goes; a directory output also has the [tree] the plugin writes through. */
    private class OutputTarget(val path: String, val file: File, val tree: PluginToolOutputTree? = null)

    private class ResolvedInput(val meta: JSONObject, val descriptor: ParcelFileDescriptor)

    /** A request error that answers the caller as is. */
    private class RequestProblem(val error: NodeBridgeError) : RuntimeException(error.message)

    /** One running call; it answers its caller exactly once. */
    private inner class Operation(
        val request: NodeBridgeRequest,
        private val reply: (NodeBridgeResponse) -> Unit,
        val invocationId: String,
        val toolName: String,
    ) {
        private val finished = AtomicBoolean(false)
        private val startedAt = SystemClock.elapsedRealtime()
        @Volatile private var provider: IPluginToolProvider? = null
        @Volatile private var lease: ServiceBindingLease? = null
        @Volatile private var deathRecipient: IBinder.DeathRecipient? = null
        @Volatile private var tool: PluginToolDefinition? = null
        @Volatile private var owner: PluginToolProvider? = null
        @Volatile private var targets: Map<String, OutputTarget> = emptyMap()
        @Volatile private var overwrite = false
        @Volatile private var timeoutMs = 0L
        /** The cancel reason of a call that ended before the provider was bound; [attach] sends it once it has the proxy. */
        @Volatile private var pendingCancel: String? = null

        val isFinished: Boolean get() = finished.get()

        val timeout = Runnable {
            finish(failure(request, NodeBridgeError(
                message = "plugin_tools.invoke: $toolName did not answer within $timeoutMs ms",
                code = NodeRuntimeErrorCodes.BRIDGE_TIMEOUT,
                module = request.module,
                method = request.method,
            )), cancelReason = PluginToolValues.CANCEL_REASON_TIMEOUT)
        }

        fun prepare(tool: PluginToolDefinition, owner: PluginToolProvider, plan: PluginToolInvocationPlan, targets: Map<String, OutputTarget>, overwrite: Boolean) {
            this.tool = tool
            this.owner = owner
            this.targets = targets
            this.overwrite = overwrite
            this.timeoutMs = plan.timeoutMs
        }

        fun attach(provider: IPluginToolProvider, lease: ServiceBindingLease) {
            this.provider = provider
            this.lease = lease
            val recipient = IBinder.DeathRecipient {
                finish(failure(request, NodeBridgeError.providerFailed(
                    "plugin_tools.invoke: the provider of $toolName died during the call", request.module, request.method, code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                )))
            }
            if (runCatching { provider.asBinder().linkToDeath(recipient, 0) }.isFailure) recipient.binderDied() else deathRecipient = recipient
            // A call that ended while the provider was being bound tells the provider and releases the binding right away.
            if (isFinished) {
                pendingCancel?.let(::sendCancel)
                release()
            }
        }

        private fun sendCancel(reason: String) {
            // Recorded first, so that an [attach] racing with this call either sees the reason or leaves the provider to us.
            pendingCancel = reason
            val target = provider ?: return
            runCatching { target.cancel(Bundle().apply { putString(PluginToolCancelKeys.INVOCATION_ID, invocationId); putString(PluginToolCancelKeys.REASON, reason) }) }
        }

        /**
         * Answers the caller once. A [cancelReason] (timeout, caller or host) also tells the provider, but only after this
         * answer has won: a provider that replies CANCELLED at once must not turn a timeout into its own error.
         */
        fun finish(response: NodeBridgeResponse, cancelReason: String? = null): Boolean {
            if (!finished.compareAndSet(false, true)) return false
            operations.remove(invocationId, this)
            mainHandler.removeCallbacks(timeout)
            // A successful result committed its directory outputs already; any other end drops what the plugin wrote.
            targets.values.forEach { it.tree?.discard() }
            cancelReason?.let(::sendCancel)
            release()
            reply(response)
            return true
        }

        private fun release() {
            deathRecipient?.let { recipient -> runCatching { provider?.asBinder()?.unlinkToDeath(recipient, 0) } }
            deathRecipient = null
            runCatching { lease?.close() }
        }

        fun callback(): IPluginToolCallback = object : IPluginToolCallback.Stub() {
            override fun onProgress(update: Bundle?) = Unit

            override fun onResult(result: Bundle?, outputs: Array<out ParcelFileDescriptor>?) {
                val descriptors = outputs?.toList().orEmpty()
                if (isFinished) return descriptors.forEach { runCatching { it.close() } }
                scope.launch { complete(result ?: Bundle(), descriptors) }
            }

            override fun onError(error: Bundle?) {
                val code = error?.getString(PluginToolErrorKeys.CODE) ?: PluginToolErrorCodes.FAILED
                val message = error?.getString(PluginToolErrorKeys.MESSAGE)?.take(PluginToolLimits.MAX_ERROR_MESSAGE_CHARS) ?: "no message"
                finish(failure(request, pluginError(request, toolName, code, message)))
            }
        }

        private fun complete(result: Bundle, descriptors: List<ParcelFileDescriptor>) {
            try {
                if (isFinished) return
                val resultText = result.getString(PluginToolResultKeys.RESULT_JSON)
                val text = result.getString(PluginToolResultKeys.TEXT)
                val size = (resultText?.toByteArray(Charsets.UTF_8)?.size ?: 0) + (text?.toByteArray(Charsets.UTF_8)?.size ?: 0)
                if (size > PluginToolLimits.MAX_RESULT_BYTES) {
                    finish(failure(request, NodeBridgeError.resourceLimit("plugin_tools.invoke: $toolName returned $size bytes, more than ${PluginToolLimits.MAX_RESULT_BYTES}", request.module, request.method)))
                    return
                }
                val parsed = resultText?.let { json -> runCatching { JsonParser.parseString(json) }.getOrElse { badResult("its result is not JSON"); return } }
                tool?.outputSchema?.let { schema ->
                    val problems = PluginToolSchema.validate(schema, parsed ?: JsonObject())
                    if (problems.isNotEmpty()) return badResult("its result does not match its output schema: ${problems.take(3).joinToString("; ")}")
                }
                val written = JSONArray()
                val names = runCatching { JSONArray(result.getString(PluginToolResultKeys.OUTPUTS_JSON) ?: "[]") }.getOrElse { JSONArray() }
                for (index in descriptors.indices) {
                    val name = names.optJSONObject(index)?.optString(PluginToolOutputJson.NAME).orEmpty()
                    val target = targets[name]?.takeIf { it.tree == null } ?: continue
                    val bytes = runCatching { writeOutput(descriptors[index], target.file) }.getOrElse { error ->
                        return badResult("output $name could not be written: ${error.message}")
                    }
                    written.put(JSONObject().put("name", name).put("path", target.path).put("bytes", bytes)
                        .put("mimeType", names.optJSONObject(index)?.optString(PluginToolOutputJson.MIME_TYPE).orEmpty()))
                }
                for ((name, target) in targets) {
                    val tree = target.tree ?: continue
                    val committed = runCatching { tree.commit() }.getOrElse { error -> return badResult("output $name could not be committed: ${error.message}") }
                    written.put(JSONObject().put("name", name).put("kind", PluginToolValues.OUTPUT_KIND_DIRECTORY).put("path", target.path)
                        .put("mimeType", PluginToolCatalogParser.DIRECTORY_MIME_TYPE).put("files", committed.files).put("bytes", committed.bytes).apply {
                            if (committed.conflicts.isNotEmpty()) {
                                put("conflicts", JSONArray(committed.conflicts.take(MAX_REPORTED_CONFLICTS))).put("conflictCount", committed.conflicts.size)
                            }
                        })
                }
                val missing = targets.keys.filter { name -> (0 until written.length()).none { written.getJSONObject(it).getString("name") == name } }
                val json = JSONObject()
                    .put("schema", INVOKE_SCHEMA)
                    .put("tool", toolName)
                    .put("provider", owner?.packageName)
                    .put("invocationId", invocationId)
                    .put("result", parsed?.let { JSONTokener(it.toString()).nextValue() } ?: JSONObject.NULL)
                    .put("outputs", written)
                    .put("durationMs", SystemClock.elapsedRealtime() - startedAt)
                text?.let { json.put("text", it) }
                if (missing.isNotEmpty()) json.put("missingOutputs", JSONArray(missing))
                finish(success(request, json))
            } finally {
                descriptors.forEach { runCatching { it.close() } }
            }
        }

        private fun badResult(reason: String) {
            finish(failure(request, NodeBridgeError.providerFailed("plugin_tools.invoke: $toolName answered, but $reason", request.module, request.method, code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED)))
        }

        /** Copies at most the descriptor ceiling into a sibling temp file and renames it over [target] (atomic on one file system). */
        private fun writeOutput(descriptor: ParcelFileDescriptor, target: File): Long {
            val directory = target.parentFile ?: throw IOException("no parent directory")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create ${directory.name}")
            val temp = File(directory, ".${target.name}.${UUID.randomUUID()}.part")
            try {
                var total = 0L
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > PluginToolLimits.MAX_DESCRIPTOR_BYTES) throw IOException("more than ${PluginToolLimits.MAX_DESCRIPTOR_BYTES} bytes")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (target.exists() && !overwrite) throw IOException("${target.name} appeared while the call ran")
                if (!temp.renameTo(target)) throw IOException("cannot move the output into place")
                return total
            } finally {
                temp.delete()
            }
        }
    }

    private fun admits(provider: PluginToolProvider, userGranted: Boolean): Boolean =
        provider.trust != PluginAuthorizedState.USER_GRANTED || userGranted

    private fun declares(request: NodeBridgeRequest, token: String): Boolean =
        request.permissions.any { NodeBridgePermissionManifest.normalizeCapability(it) == token }

    private fun requireToken(request: NodeBridgeRequest, token: String, purpose: String) {
        if (!declares(request, token)) throw RequestProblem(NodeBridgeError.permissionDenied("the request must declare $token to $purpose", request.module, request.method))
    }

    /** Path policy failures (SecurityException) become permission errors. */
    private fun <T> resolve(block: () -> T): T = runCatching(block).getOrElse { error ->
        throw RequestProblem(NodeBridgeError.permissionDenied(error.message ?: "the path is not allowed", NodeBridgeModules.PLUGIN_TOOLS, "invoke"))
    }

    private fun startFailure(request: NodeBridgeRequest, name: String, error: Throwable): NodeBridgeError = when (error) {
        is RequestProblem -> error.error
        else -> NodeBridgeError.providerFailed("plugin_tools.invoke: $name could not start: ${error.message}", request.module, request.method, code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED)
    }

    private fun pluginError(request: NodeBridgeRequest, toolName: String, code: String, message: String): NodeBridgeError {
        val text = "plugin_tools.invoke: $toolName failed with $code: $message"
        return when (PluginToolInvocationPlanner.bridgeCategoryOf(code)) {
            NodeBridgeErrorCategory.INVALID_REQUEST -> NodeBridgeError.invalidRequest(text, request.module, request.method)
            NodeBridgeErrorCategory.RATE_LIMITED -> NodeBridgeError.rateLimited(text, request.module, request.method)
            NodeBridgeErrorCategory.RESOURCE_LIMIT -> NodeBridgeError.resourceLimit(text, request.module, request.method)
            NodeBridgeErrorCategory.TIMEOUT -> NodeBridgeError(message = text, code = NodeRuntimeErrorCodes.BRIDGE_TIMEOUT, module = request.module, method = request.method)
            NodeBridgeErrorCategory.UNAVAILABLE -> NodeBridgeError.permissionDenied(text, request.module, request.method, category = NodeBridgeErrorCategory.UNAVAILABLE)
            else -> NodeBridgeError.providerFailed(text, request.module, request.method, code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED)
        }
    }

    private fun toolJson(provider: PluginToolProvider, tool: PluginToolDefinition): JSONObject = JSONObject().apply {
        put("name", tool.fullName)
        put("tool", tool.name)
        put("shortName", provider.catalog?.shortName)
        put("provider", provider.packageName)
        put("trust", provider.trust.name.lowercase())
        tool.title?.let { put("title", it) }
        put("description", tool.description)
        put("inputSchema", JSONObject(tool.inputSchema.toString()))
        tool.outputSchema?.let { put("outputSchema", JSONObject(it.toString())) }
        put("risk", tool.risk)
        put("timeoutMs", tool.timeoutMs)
        put("maxConcurrency", tool.maxConcurrency)
        put("outputs", JSONArray().apply {
            tool.outputs.forEach { output ->
                put(JSONObject().put("name", output.name).put("kind", output.kind).put("mimeType", output.mimeType).apply { output.description?.let { put("description", it) } })
            }
        })
        put("imageParameters", JSONArray(tool.specialParameters.filterValues { it == PluginToolValues.SPECIAL_TYPE_IMAGE }.keys.toList()))
        put("fileParameters", JSONArray(tool.specialParameters.filterValues { it == PluginToolValues.SPECIAL_TYPE_FILE }.keys.toList()))
        put("directoryParameters", JSONArray(tool.specialParameters.filterValues { it == PluginToolValues.SPECIAL_TYPE_DIRECTORY }.keys.toList()))
    }

    private fun success(request: NodeBridgeRequest, json: JSONObject) = NodeBridgeResponse.success(request.id, json.toString())

    private fun failure(request: NodeBridgeRequest, error: NodeBridgeError) = NodeBridgeResponse.failure(request.id, error)

    private fun invalid(request: NodeBridgeRequest, message: String) = failure(request, NodeBridgeError.invalidRequest(message, request.module, request.method))

    private fun JsonArray.getOrNull(index: Int): JsonElement? = if (index < size()) get(index) else null

    private fun JsonArray.stringAt(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.takeIf { it.isString }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonArray.objectAt(index: Int): JsonObject? = getOrNull(index) as? JsonObject

    private fun JsonObject.stringOf(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString?.takeIf { it.isNotBlank() }

    companion object {
        const val LIST_SCHEMA = "autojs6-bridge-plugin-tools-list-v1"
        const val INVOKE_SCHEMA = "autojs6-bridge-plugin-tools-invoke-v1"
        const val CANCEL_SCHEMA = "autojs6-bridge-plugin-tools-cancel-v1"

        /** The bridge answers this much before the broker's own deadline, so a timed-out call still tells the provider. */
        const val DEADLINE_MARGIN_MS = 750L

        private const val INPUT_DIRECTORY = "plugin-tool-inputs"

        /** Conflicts a directory output reports by path; `conflictCount` has the total. */
        private const val MAX_REPORTED_CONFLICTS = 20
        private val INVOCATION_ID = Regex("^[A-Za-z0-9._:-]{1,128}$")

        fun consumerOf(engineName: String): String = when (engineName) {
            "mcp" -> PluginToolValues.CONSUMER_MCP
            "three-stove-agent" -> PluginToolValues.CONSUMER_AGENT
            else -> PluginToolValues.CONSUMER_SCRIPT
        }
    }
}
