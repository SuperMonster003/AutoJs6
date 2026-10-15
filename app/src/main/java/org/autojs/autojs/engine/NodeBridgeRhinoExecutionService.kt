package org.autojs.autojs.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.Log
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Shell
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NodeBridgeRhinoExecutionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val requestJson = intent?.getStringExtra(EXTRA_REQUEST_JSON).orEmpty()
        Thread({
            val response = runCatching {
                handleRequest(NodeBridgeRequest.fromJson(requestJson))
            }.getOrElse { error ->
                NodeBridgeResponse.failure(
                    NodeBridgeRequest.recoverId(requestJson).orEmpty(),
                    NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = NodeBridgeRequest.recoverModule(requestJson),
                        method = NodeBridgeRequest.recoverMethod(requestJson),
                        stack = error.stackTraceToString(),
                    ),
                )
            }
            receiver?.send(RESULT_RESPONSE, response.toBundle())
            stopSelf(startId)
        }, "AutoJs6NodeBridgeRhinoDispatch-$startId").apply {
            isDaemon = true
            start()
        }
        return START_NOT_STICKY
    }

    private fun handleRequest(request: NodeBridgeRequest): NodeBridgeResponse {
        if (request.module != NodeBridgeModules.RHINO || request.method != "run") {
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.bridgeUnavailable(request.module, request.method),
            )
        }
        workingDirectoryError(request)?.let { return NodeBridgeResponse.failure(request.id, it) }
        val descriptor = RhinoRunDescriptor.fromRequest(request)
            ?: return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest(
                    message = "rhino.run requires explicit:true, exactly one of source/path, timeoutMs, scoped cwd, and JSON args.",
                    module = request.module,
                    method = request.method,
                ),
            )
        return executeRhino(request, descriptor)
    }

    /** The reason the session's working directory cannot host the run (NodeBridgeWorkingDirectory), checked before the generic request rules. */
    private fun workingDirectoryError(request: NodeBridgeRequest): NodeBridgeError? {
        val options = runCatching { JSONArray(request.argsJson.ifBlank { "[]" }) }.getOrNull()?.optJSONObject(0) ?: return null
        val baseCwd = options.optString(INTERNAL_BASE_CWD).takeIf { it.isNotBlank() } ?: return null
        return NodeBridgeWorkingDirectory.error(baseCwd, request.module, request.method)
    }

    private fun executeRhino(request: NodeBridgeRequest, descriptor: RhinoRunDescriptor): NodeBridgeResponse {
        val startedAt = SystemClock.elapsedRealtime()
        val console = RhinoConsoleCapture()
        val timedOut = AtomicBoolean(false)
        val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AutoJs6RhinoJsonOnlyPoc").apply { isDaemon = true }
        }
        val future = executor.submit(Callable {
            runRhinoOnExecutionThread(descriptor, console, engineRef, timedOut, startedAt)
        })
        return try {
            NodeBridgeResponse.success(request.id, future.get(descriptor.timeoutMs, TimeUnit.MILLISECONDS).toString())
        } catch (_: TimeoutException) {
            timedOut.set(true)
            val engine = engineRef.get()
            runCatching { engine?.forceStop() }
            val destroyed = awaitTimedOutEngineDestroy(future)
            NodeBridgeResponse.success(
                request.id,
                timeoutResultJson(
                    descriptor = descriptor,
                    console = console,
                    durationMs = elapsedSince(startedAt),
                    destroyed = destroyed || engine?.isDestroyed == true,
                    forceStopRequested = true,
                ).toString(),
            )
        } catch (error: Throwable) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.providerFailed(
                    message = error.message ?: error.javaClass.name,
                    module = request.module,
                    method = request.method,
                    stack = error.stackTraceToString(),
                ),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun runRhinoOnExecutionThread(
        descriptor: RhinoRunDescriptor,
        console: RhinoConsoleCapture,
        engineRef: AtomicReference<RhinoJavaScriptEngine?>,
        timedOut: AtomicBoolean,
        startedAt: Long,
    ): JSONObject {
        val engine = RhinoJavaScriptEngine(applicationContext)
        engineRef.set(engine)
        var destroyed = false
        var valueJson: String? = null
        var errorJson: JSONObject? = null
        var resultTimedOut = false
        try {
            val runtime = createRhinoRuntime(applicationContext)
            engine.runtime = runtime
            val config = ExecutionConfig(workingDirectory = descriptor.workingDirectory.absolutePath)
            val source = StringScriptSource(descriptor.sourceName, descriptor.source).apply {
                prefix = "\$rhino/"
            }
            if ((source.executionMode and JavaScriptSource.EXECUTION_MODE_NODEJS) != 0) {
                errorJson = stableErrorJson(
                        name = "RhinoInvalidRequestError",
                        message = "rhino.run cannot execute a source marked with the nodejs execution mode.",
                        code = NodeRuntimeErrorCodes.RHINO_INVALID_REQUEST,
                        category = "invalid-request",
                )
            } else {
                engine.setTag(ExecutionConfig.tag, config)
                engine.setTag(ScriptEngine.TAG_WORKING_DIRECTORY, config.workingDirectory)
                engine.setTag(ScriptEngine.TAG_ENV_PATH, config.envPath)
                engine.setTag(ScriptEngine.TAG_SOURCE, source)
                engine.init()
                console.install(engine.context, engine.scriptable)
                installArgs(engine, descriptor.argsJson)
                val result = engine.execute(source)
                valueJson = jsonSafeResult(engine, result)
            }
        } catch (error: RhinoUnsafeResultException) {
            errorJson = stableErrorJson(
                    name = "RhinoJsonUnsafeResultError",
                    message = error.message ?: "rhino.run result is not JSON-safe.",
                    code = NodeRuntimeErrorCodes.RHINO_JSON_UNSAFE_RESULT,
                    category = "json-unsafe-result",
            )
        } catch (error: Throwable) {
            val code = if (timedOut.get()) {
                NodeRuntimeErrorCodes.RHINO_EXECUTION_TIMEOUT
            } else {
                NodeRuntimeErrorCodes.RHINO_EXECUTION_ERROR
            }
            val category = if (timedOut.get()) "timeout" else "rhino-exception"
            errorJson = error.toStableErrorJson(code, category)
            resultTimedOut = timedOut.get()
        } finally {
            destroyed = runCatching {
                engine.destroy()
                true
            }.getOrElse { error ->
                Log.w(TAG, "Failed to destroy Rhino JSON-only POC engine.", error)
                engine.isDestroyed
            }
            engineRef.compareAndSet(engine, null)
            console.markDestroyed(destroyed)
        }
        val durationMs = elapsedSince(startedAt)
        return if (valueJson != null && errorJson == null) {
            rhinoSuccessResult(
                descriptor = descriptor,
                console = console,
                valueJson = valueJson,
                durationMs = durationMs,
                destroyed = destroyed,
            )
        } else {
            rhinoFailureResult(
                descriptor = descriptor,
                console = console,
                error = errorJson ?: stableErrorJson(
                    name = "RhinoExecutionError",
                    message = "rhino.run failed without a stable error.",
                    code = NodeRuntimeErrorCodes.RHINO_EXECUTION_ERROR,
                    category = "rhino-exception",
                ),
                durationMs = durationMs,
                destroyed = destroyed,
                timedOut = resultTimedOut,
            )
        }
    }

    private fun awaitTimedOutEngineDestroy(future: Future<JSONObject>): Boolean {
        return runCatching {
            future.get(TIMEOUT_DESTROY_GRACE_MS, TimeUnit.MILLISECONDS)
                .optJSONObject("lifecycle")
                ?.optBoolean("destroyed", false) == true
        }.getOrElse {
            future.cancel(true)
            false
        }
    }

    private fun createRhinoRuntime(context: Context): ScriptRuntime {
        val autoJs = AutoJs.instance
        return ScriptRuntime.Builder()
            .setConsole(autoJs.globalConsole)
            .setUiHandler(autoJs.uiHandler)
            .setAccessibilityBridge(autoJs.createAccessibilityBridge())
            .setAppUtils(autoJs.appUtils)
            .setShellSupplier { Shell(context.applicationContext, true) }
            .build()
    }

    private fun installArgs(engine: RhinoJavaScriptEngine, argsJson: String) {
        val args = engine.context.evaluateString(
            engine.scriptable,
            "JSON.parse(${JSONObject.quote(argsJson)})",
            "<autojs6-rhino-json-only-args>",
            1,
            null,
        )
        ScriptableObject.putProperty(engine.scriptable, "args", args)
    }

    private fun jsonSafeResult(engine: RhinoJavaScriptEngine, result: Any?): String {
        if (result == null || result == Undefined.instance) {
            return "null"
        }
        if (result is Function || result is Wrapper) {
            throw RhinoUnsafeResultException("rhino.run result cannot be a function, Java object, Android object, or Rhino wrapper.")
        }
        val scope = engine.scriptable
        ScriptableObject.putProperty(scope, RESULT_PROPERTY, result)
        return try {
            val rawJson = engine.context.evaluateString(
                scope,
                "JSON.stringify($RESULT_PROPERTY)",
                "<autojs6-rhino-json-only-result>",
                1,
                null,
            )
            if (rawJson == null || rawJson == Undefined.instance) {
                throw RhinoUnsafeResultException("rhino.run result cannot be represented as JSON.")
            }
            val json = RhinoContext.toString(rawJson)
            if (json == "undefined") {
                throw RhinoUnsafeResultException("rhino.run result cannot be represented as JSON.")
            }
            if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_RESULT_BYTES) {
                throw RhinoUnsafeResultException("rhino.run JSON result exceeds $MAX_RESULT_BYTES bytes.")
            }
            parseJsonValue(json)
            json
        } finally {
            scope.delete(RESULT_PROPERTY)
        }
    }

    private fun rhinoSuccessResult(
        descriptor: RhinoRunDescriptor,
        console: RhinoConsoleCapture,
        valueJson: String,
        durationMs: Long,
        destroyed: Boolean,
    ): JSONObject {
        return baseResultJson(descriptor, console, durationMs, destroyed).apply {
            put("ok", true)
            put("timedOut", false)
            put("value", parseJsonValue(valueJson))
        }
    }

    private fun rhinoFailureResult(
        descriptor: RhinoRunDescriptor,
        console: RhinoConsoleCapture,
        error: JSONObject,
        durationMs: Long,
        destroyed: Boolean,
        timedOut: Boolean = false,
    ): JSONObject {
        return baseResultJson(descriptor, console, durationMs, destroyed).apply {
            put("ok", false)
            put("timedOut", timedOut)
            put("error", error)
        }
    }

    private fun timeoutResultJson(
        descriptor: RhinoRunDescriptor,
        console: RhinoConsoleCapture,
        durationMs: Long,
        destroyed: Boolean,
        forceStopRequested: Boolean,
    ): JSONObject {
        return baseResultJson(descriptor, console, durationMs, destroyed).apply {
            put("ok", false)
            put("timedOut", true)
            optJSONObject("lifecycle")?.put("forceStopRequested", forceStopRequested)
            put(
                "error",
                stableErrorJson(
                    name = "RhinoTimeoutError",
                    message = "rhino.run timed out after ${descriptor.timeoutMs} ms.",
                    code = NodeRuntimeErrorCodes.RHINO_EXECUTION_TIMEOUT,
                    category = "timeout",
                ),
            )
        }
    }

    private fun baseResultJson(
        descriptor: RhinoRunDescriptor,
        console: RhinoConsoleCapture,
        durationMs: Long,
        destroyed: Boolean,
    ): JSONObject {
        return JSONObject().apply {
            put("schema", RESULT_SCHEMA)
            put("mode", "json-only-poc")
            put("provider", "android-rhino-json-only-poc")
            put("sourceName", descriptor.sourceName)
            put("durationMs", durationMs.coerceAtLeast(0L))
            put("console", console.toJsonObject())
            put(
                "lifecycle",
                JSONObject().apply {
                    put("engineCreated", true)
                    put("forceStopRequested", false)
                    put("destroyed", destroyed || console.destroyed)
                },
            )
        }
    }

    private fun Throwable.toStableErrorJson(code: String, category: String): JSONObject {
        val base = stableErrorJson(
            name = javaClass.simpleName.ifBlank { "RhinoExecutionError" },
            message = message ?: javaClass.name,
            code = code,
            category = category,
            stack = ScriptRuntime.getStackTrace(this, true)?.take(MAX_STACK_CHARS),
        )
        if (this is RhinoException) {
            sourceName()?.takeIf { it.isNotBlank() }?.let { base.put("sourceName", it) }
            val line = lineNumber()
            if (line > 0) base.put("lineNumber", line)
            val column = columnNumber()
            if (column > 0) base.put("columnNumber", column)
        }
        return base
    }

    private fun stableErrorJson(
        name: String,
        message: String,
        code: String,
        category: String,
        stack: String? = null,
    ): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("message", message)
            put("code", code)
            put("category", category)
            stack?.takeIf { it.isNotBlank() }?.let { put("stack", it) }
        }
    }

    private fun elapsedSince(startedAt: Long): Long = SystemClock.elapsedRealtime() - startedAt

    private fun parseJsonValue(json: String): Any {
        return JSONTokener(json.trim().ifEmpty { "null" }).nextValue() ?: JSONObject.NULL
    }

    private fun NodeBridgeResponse.toBundle(): Bundle {
        return Bundle().apply {
            putString(EXTRA_RESPONSE_JSON, toJson())
        }
    }

    private data class RhinoRunDescriptor(
        val sourceName: String,
        val source: String,
        val workingDirectory: File,
        val timeoutMs: Long,
        val argsJson: String,
    ) {
        companion object {
            fun fromRequest(request: NodeBridgeRequest): RhinoRunDescriptor? {
                val args = runCatching { JSONArray(request.argsJson.ifBlank { "[]" }) }.getOrNull() ?: return null
                val options = args.optJSONObject(0) ?: return null
                if (options.optBoolean("explicit", false) != true) {
                    return null
                }
                val timeoutMs = options.optLong("timeoutMs", 0L)
                    .takeIf { it in 1L..MAX_TIMEOUT_MS }
                    ?: return null
                val baseCwd = options.optString(INTERNAL_BASE_CWD)
                    .takeIf { it.isNotBlank() && !it.contains('\u0000') }
                    ?: return null
                val cwd = resolveScopedCwd(baseCwd, options.optString("cwd", "."))
                    ?: return null
                val hasSource = options.has("source") && !options.isNull("source")
                val hasPath = options.has("path") && !options.isNull("path")
                if (hasSource == hasPath) {
                    return null
                }
                val argsJson = (options.optJSONArray("args") ?: JSONArray()).toString()
                    .takeIf { it.toByteArray(StandardCharsets.UTF_8).size <= MAX_ARGS_BYTES }
                    ?: return null
                return if (hasSource) {
                    val source = options.optString("source")
                        .takeIf { it.isNotEmpty() && !it.contains('\u0000') && it.utf8Size() <= MAX_SOURCE_BYTES }
                        ?: return null
                    RhinoRunDescriptor(
                        sourceName = options.optString("name").takeIf { it.isNotBlank() && !it.contains('\u0000') }
                            ?: "<node-rhino-json-only-source>",
                        source = source,
                        workingDirectory = cwd,
                        timeoutMs = timeoutMs,
                        argsJson = argsJson,
                    )
                } else {
                    val path = options.optString("path")
                        .takeIf { it.isNotBlank() && !it.contains('\u0000') }
                        ?: return null
                    val file = resolveScopedFile(cwd, path)?.takeIf { it.isFile } ?: return null
                    val bytes = file.readBytes().takeIf { it.size <= MAX_SOURCE_BYTES } ?: return null
                    RhinoRunDescriptor(
                        sourceName = file.absolutePath,
                        source = String(bytes, StandardCharsets.UTF_8),
                        workingDirectory = cwd,
                        timeoutMs = timeoutMs,
                        argsJson = argsJson,
                    )
                }
            }

            private fun resolveScopedCwd(baseCwd: String, cwd: String): File? {
                val base = runCatching { File(baseCwd).canonicalFile }.getOrNull()
                    ?.takeIf { it.isDirectory }
                    ?: return null
                val text = cwd.ifBlank { "." }
                if (File(text).isAbsolute || text.hasParentSegment() || text.contains('\u0000')) {
                    return null
                }
                return runCatching { File(base, text).canonicalFile }.getOrNull()
                    ?.takeIf { it.isDirectory && it.isInsideOrSame(base) }
            }

            private fun resolveScopedFile(cwd: File, path: String): File? {
                if (File(path).isAbsolute || path.hasParentSegment()) {
                    return null
                }
                return runCatching { File(cwd, path).canonicalFile }.getOrNull()
                    ?.takeIf { it.isInsideOrSame(cwd) }
            }

            private fun String.hasParentSegment(): Boolean {
                return split('/', '\\').any { it == ".." }
            }

            private fun File.isInsideOrSame(root: File): Boolean {
                val rootPath = root.path
                val path = path
                return path == rootPath || path.startsWith(rootPath + File.separator)
            }

            private fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size
        }
    }

    private class RhinoConsoleCapture {
        private val lines = ArrayList<JSONObject>()
        private var bytes = 0
        private var truncated = false

        @Volatile
        var destroyed: Boolean = false
            private set

        fun install(cx: RhinoContext, scope: VarScope) {
            val console = NativeObject().apply {
                parentScope = scope
                prototype = ScriptableObject.getObjectPrototype(scope)
            }
            listOf("log", "info", "warn", "error").forEach { level ->
                ScriptableObject.putProperty(console, level, function(cx, scope, level))
            }
            ScriptableObject.putProperty(scope, "console", console)
        }

        fun markDestroyed(value: Boolean) {
            destroyed = value
        }

        fun toJsonObject(): JSONObject {
            return synchronized(this) {
                JSONObject().apply {
                    put("schema", "autojs6-rhino-json-only-console-v1")
                    put("lines", JSONArray(lines))
                    put("truncated", truncated)
                    put("maxLines", MAX_CONSOLE_LINES)
                    put("maxBytes", MAX_CONSOLE_BYTES)
                }
            }
        }

        private fun function(cx: RhinoContext, scope: VarScope, level: String): BaseFunction {
            return object : BaseFunction() {
                init {
                    parentScope = scope
                    prototype = ScriptableObject.getFunctionPrototype(scope)
                }

                override fun call(cx: RhinoContext?, nt: Any?, scope: VarScope?, thisObj: Any?, args: Array<out Any?>?): Any {
                    record(level, args.orEmpty())
                    return Undefined.instance
                }
            }
        }

        private fun record(level: String, args: Array<out Any?>) {
            val message = args.joinToString(" ") { value ->
                runCatching { RhinoContext.toString(value) }.getOrDefault(java.lang.String.valueOf(value))
            }.take(MAX_CONSOLE_LINE_CHARS)
            val messageBytes = message.toByteArray(StandardCharsets.UTF_8).size
            synchronized(this) {
                if (truncated || lines.size >= MAX_CONSOLE_LINES || bytes + messageBytes > MAX_CONSOLE_BYTES) {
                    truncated = true
                    return
                }
                bytes += messageBytes
                lines += JSONObject().apply {
                    put("level", level)
                    put("message", message)
                }
            }
        }
    }

    private class RhinoUnsafeResultException(message: String) : RuntimeException(message)

    companion object {
        private const val TAG = "NodeBridgeRhino"
        private const val ACTION_DISPATCH = "org.autojs.autojs.engine.action.DISPATCH_NODE_BRIDGE_RHINO"
        private const val EXTRA_REQUEST_JSON = "request_json"
        private const val EXTRA_RESPONSE_JSON = "response_json"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val RESULT_RESPONSE = 1
        private const val RESULT_SCHEMA = "autojs6-rhino-json-only-run-result-v1"
        private const val INTERNAL_BASE_CWD = "__autojs6BaseCwd"
        private const val RESULT_PROPERTY = "__autojs6RhinoJsonOnlyResult"
        private const val MAX_TIMEOUT_MS = 30_000L
        private const val MAX_SOURCE_BYTES = 64 * 1024
        private const val MAX_ARGS_BYTES = 16 * 1024
        private const val MAX_RESULT_BYTES = 64 * 1024
        private const val MAX_STACK_CHARS = 4096
        private const val MAX_CONSOLE_LINES = 32
        private const val MAX_CONSOLE_BYTES = 8 * 1024
        private const val MAX_CONSOLE_LINE_CHARS = 1024
        private const val TIMEOUT_DESTROY_GRACE_MS = 1_000L

        fun dispatch(context: Context, request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
            val appContext = context.applicationContext
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_RESPONSE) {
                        callback(request.processDead("Main-process rhino bridge returned an unexpected result."))
                        return
                    }
                    val responseJson = resultData?.getString(EXTRA_RESPONSE_JSON).orEmpty()
                    callback(
                        runCatching { NodeBridgeResponse.fromJson(responseJson) }
                            .getOrElse {
                                request.processDead("Main-process rhino bridge returned a malformed response.")
                            },
                    )
                }
            }
            val intent = Intent(appContext, NodeBridgeRhinoExecutionService::class.java).apply {
                action = ACTION_DISPATCH
                putExtra(EXTRA_REQUEST_JSON, request.toJson())
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
            }
            val component = runCatching { appContext.startService(intent) }.getOrElse { error ->
                callback(request.processDead(error.message ?: error.javaClass.name))
                return
            }
            if (component == null) {
                callback(request.processDead("Failed to start main-process rhino bridge service."))
            }
        }

        @Suppress("DEPRECATION")
        private fun Intent?.resultReceiverExtra(name: String): ResultReceiver? {
            return this?.getParcelableExtra(name)
        }

        private fun NodeBridgeRequest.processDead(message: String): NodeBridgeResponse {
            return NodeBridgeResponse.failure(
                id,
                NodeBridgeError.providerFailed(
                    message = message,
                    module = module,
                    method = method,
                ),
            )
        }
    }
}
