package org.autojs.autojs.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ResultReceiver
import android.os.SystemClock
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class NodeBridgeEngineDispatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_REGISTERED) {
            registeredExecutions[intent.getStringExtra(EXTRA_REGISTERED_TOKEN).orEmpty()]?.cancel()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val requestJson = intent?.getStringExtra(EXTRA_REQUEST_JSON).orEmpty()
        if (intent?.action == ACTION_REGISTERED || intent?.action == ACTION_DYNAMIC) {
            startRegistered(intent, receiver, requestJson, startId)
            return START_NOT_STICKY
        }
        thread(name = "AutoJs6NodeBridgeEngineDispatch-$startId", isDaemon = true) {
            val response = runCatching {
                handleRequest(NodeBridgeRequest.fromJson(requestJson))
            }.getOrElse { error ->
                NodeBridgeResponse.failure(
                    NodeBridgeRequest.recoverId(requestJson).orEmpty(),
                    NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = NodeBridgeRequest.recoverModule(requestJson),
                        method = NodeBridgeRequest.recoverMethod(requestJson),
                    ),
                )
            }
            receiver?.send(RESULT_RESPONSE, response.toBundle())
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startRegistered(intent: Intent, receiver: ResultReceiver?, requestJson: String, startId: Int) {
        val token = intent.getStringExtra(EXTRA_REGISTERED_TOKEN).orEmpty()
        val request = runCatching { NodeBridgeRequest.fromJson(requestJson) }.getOrNull()
        val execution = runCatching {
            require(token.isNotBlank())
            require(request != null && ((intent.action == ACTION_REGISTERED && request.module == "agent" && request.method == "execRegistered") ||
                (intent.action == ACTION_DYNAMIC && request.module == "engines" && request.method == "execScript")))
            val payload = JSONObject(intent.getStringExtra(EXTRA_REGISTERED_JSON).orEmpty())
            val cancellation = requireNotNull(intent.getBundleExtra(EXTRA_REGISTERED_CONTROL)?.getBinder("cancel"))
            if (intent.action == ACTION_DYNAMIC) AgentOwnedScriptExecution.dynamic(
                name = payload.getString("name"), source = payload.getString("source"),
                workingDirectory = payload.getString("workingDirectory"), runId = payload.getString("runId"),
                presetName = payload.optString("presetName").takeIf { it.isNotEmpty() },
                timeoutMs = minOf(payload.getLong("timeoutMs"), request.timeoutMs),
                captureConsole = payload.getBoolean("captureConsole"),
                cancellationRequested = { DispatchCancellation.read(cancellation) },
            ) else AgentOwnedScriptExecution.registered(
                launchPath = payload.getString("launchPath"), workingDirectory = payload.getString("workingDirectory"),
                expectedScriptPath = payload.getString("expectedScriptPath"),
                parametersJson = payload.getString("parametersJson"), runId = payload.getString("runId"),
                presetName = payload.optString("presetName").takeIf { it.isNotEmpty() },
                timeoutMs = minOf(payload.getLong("timeoutMs"), request.timeoutMs),
                captureConsole = payload.getBoolean("captureConsole"),
                cancellationRequested = { DispatchCancellation.read(cancellation) },
            )
        }.getOrNull()
        if (request == null || execution == null) {
            receiver?.send(RESULT_RESPONSE, NodeBridgeResponse.failure(NodeBridgeRequest.recoverId(requestJson).orEmpty(),
                NodeBridgeError.invalidRequest("Invalid Agent script dispatch", request?.module, request?.method)).toBundle())
            stopSelf(startId)
            return
        }
        if (registeredExecutions.size >= MAX_REGISTERED_DISPATCHES || registeredExecutions.putIfAbsent(token, execution) != null) {
            receiver?.send(RESULT_RESPONSE, NodeBridgeResponse.failure(request.id,
                NodeBridgeError.resourceLimit("Agent script execution queue is full", request.module, request.method)).toBundle())
            stopSelf(startId)
            return
        }
        try {
            registeredExecutor.execute {
                try {
                    val response = runCatching { NodeBridgeResponse.success(request.id, execution.execute().toString()) }
                        .getOrElse { NodeBridgeResponse.failure(request.id,
                            NodeBridgeError.providerFailed("Agent script dispatch failed", request.module, request.method)) }
                    receiver?.send(RESULT_RESPONSE, response.toBundle())
                } finally {
                    registeredExecutions.remove(token, execution)
                    stopSelf(startId)
                }
            }
        } catch (_: RejectedExecutionException) {
            registeredExecutions.remove(token, execution)
            execution.cancel()
            receiver?.send(RESULT_RESPONSE, NodeBridgeResponse.failure(request.id,
                NodeBridgeError.resourceLimit("Agent script execution queue is full", request.module, request.method)).toBundle())
            stopSelf(startId)
        }
    }

    private fun handleRequest(request: NodeBridgeRequest): NodeBridgeResponse {
        if (request.module != NodeBridgeModules.ENGINES) {
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.bridgeUnavailable(request.module, request.method),
            )
        }
        return when (request.method) {
            "execScript" -> executeScript(request)
            "execScriptFile" -> executeScriptFile(request)
            else -> NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.bridgeUnavailable(request.module, request.method),
            )
        }
    }

    private fun executeScript(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val name = args.optString(0).takeIf { it.isNotBlank() && !it.contains('\u0000') }
            ?: return policyFailure(request, "engines.execScript requires a non-empty script name without NUL bytes.")
        val script = args.optString(1)
        val options = args.optJSONObject(2) ?: JSONObject()
        val config = try {
            buildExecutionConfig(request, options)
        } catch (denied: ExecutionDenied) {
            return NodeBridgeResponse.failure(request.id, denied.error)
        }
        val source = StringScriptSource(name, script).apply { prefix = "\$engine/" }
        if (source.engineName == JavaScriptSource.ENGINE_NODEJS) {
            return policyFailure(request, "engines.execScript cannot start nested Node.js plugin executions yet.")
        }
        return startExecution(request, options, source, config)
    }

    private fun executeScriptFile(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val path = args.optString(0).takeIf { it.isNotBlank() && !it.contains('\u0000') }
            ?: return policyFailure(request, "engines.execScriptFile requires a non-empty scoped path without NUL bytes.")
        val options = args.optJSONObject(1) ?: JSONObject()
        val config = try {
            buildExecutionConfig(request, options)
        } catch (denied: ExecutionDenied) {
            return NodeBridgeResponse.failure(request.id, denied.error)
        }
        val scriptFile = resolveScopedFile(config.workingDirectory, path)
            ?.takeIf { it.isFile }
            ?: return policyFailure(request, "engines.execScriptFile path must stay inside the scoped working directory.")
        val launch = runCatching {
            ScriptLaunchSourceFactory.createPathLaunch(
                path = scriptFile.absolutePath,
                workingDirectory = config.workingDirectory,
                defaultWorkingDirectory = config.workingDirectory,
            )
        }.getOrElse { error ->
            return NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.permissionDenied(
                    message = error.message ?: error.javaClass.name,
                    module = request.module,
                    method = request.method,
                ),
            )
        }
        if (launch.source.engineName == JavaScriptSource.ENGINE_NODEJS) {
            return policyFailure(request, "engines.execScriptFile cannot start nested Node.js plugin executions yet.")
        }
        val executionConfig = launch.createExecutionConfig(
            base = config,
            overrideWorkingDirectory = true,
        )
        return startExecution(request, options, launch.source, executionConfig)
    }

    /**
     * Starts [source] and, when `waitMs` or `captureConsole` is set (MCP roadmap P1.3), waits up
     * to `waitMs` for it to finish and reports `finished` / `outcome` / `error`; `captureConsole`
     * adds the shared console's id window for the run (`captureMode` is `global-window` because
     * every runtime prints into the one GlobalConsole). `waitMs` defaults to `timeoutMs` when
     * `captureConsole` is set and to 0 otherwise.
     * zh-CN: 启动 [source]; 设置了 `waitMs` 或 `captureConsole` 时 (MCP 路线图 P1.3) 最多等待 `waitMs` 让脚本结束并报告 `finished` / `outcome` /
     * `error`; `captureConsole` 附带本次运行在共享控制台上的 id 窗口 (`captureMode` 为 `global-window`, 因为所有运行时都打印到同一个
     * GlobalConsole). `waitMs` 在设置 `captureConsole` 时默认为 `timeoutMs`, 否则为 0.
     */
    private fun startExecution(request: NodeBridgeRequest, options: JSONObject, source: ScriptSource, config: ExecutionConfig): NodeBridgeResponse {
        val wait = resolveWaitOptions(options)
            ?: return policyFailure(request, "engines.${request.method} waitMs must be an integer between 0 and $MAX_WAIT_MS and captureConsole a boolean.")
        val service = AutoJs.instance.scriptEngineService
        val console = AutoJs.instance.globalConsole
        val watch = CompletionWatch()
        service.registerGlobalScriptExecutionListener(watch)
        try {
            val consoleSinceId = console.latestLogId
            val execution = runCatching {
                service.execute(source, config)
            }.getOrElse { error ->
                return NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val startedAt = SystemClock.elapsedRealtime()
            val outcome = if (wait.waitMs > 0L) watch.await(execution.id, wait.waitMs) else watch.peek(execution.id)
            val json = execution.toBridgeJsonObject(config.spawnDepth())
            if (wait.waitMs > 0L || wait.captureConsole) {
                json.put("waitMs", wait.waitMs)
                json.put("waitedMs", SystemClock.elapsedRealtime() - startedAt)
                json.put("finished", outcome != null)
                json.put(
                    "outcome",
                    when {
                        outcome == null -> OUTCOME_RUNNING
                        outcome.error != null -> OUTCOME_EXCEPTION
                        else -> OUTCOME_SUCCESS
                    },
                )
                outcome?.error?.let { json.put("error", it.message ?: it.javaClass.name) }
            }
            if (wait.captureConsole) {
                json.put("console", NodeBridgeConsoleBridge.captureJson(console, sinceId = consoleSinceId, untilId = console.latestLogId))
            }
            return NodeBridgeResponse.success(request.id, json.toString())
        } finally {
            service.unregisterGlobalScriptExecutionListener(watch)
        }
    }

    private class WaitOptions(val captureConsole: Boolean, val waitMs: Long)

    private fun resolveWaitOptions(options: JSONObject): WaitOptions? {
        val rawCaptureConsole: Any? = options.opt("captureConsole")
        val captureConsole = when (rawCaptureConsole) {
            null, JSONObject.NULL -> false
            is Boolean -> rawCaptureConsole
            else -> return null
        }
        val rawWaitMs: Any? = options.opt("waitMs")
        val waitMs = when (rawWaitMs) {
            null, JSONObject.NULL -> if (captureConsole) options.optLong("timeoutMs", 0L).coerceIn(0L, MAX_WAIT_MS) else 0L
            is Number -> rawWaitMs.toLong().takeIf { it in 0L..MAX_WAIT_MS } ?: return null
            else -> return null
        }
        return WaitOptions(captureConsole, waitMs)
    }

    /**
     * Records completions of every execution while registered as a global listener, so a
     * script that finishes before `execute` returns is not missed; the host's own global
     * listener runs first, so its console lines are inside the captured window.
     * zh-CN: 作为全局监听器期间记录所有执行的完成, 使 `execute` 返回前就结束的脚本不被漏掉; 宿主自身的全局监听器先运行, 其控制台输出因此落在归集窗口内.
     */
    private class CompletionWatch : ScriptExecutionListener {

        class Outcome(val error: Throwable?)

        private val lock = ReentrantLock()
        private val changed = lock.newCondition()
        private val outcomes = HashMap<Int, Outcome>()

        override fun onStart(execution: ScriptExecution) = Unit

        override fun onSuccess(execution: ScriptExecution, result: Any?) = record(execution.id, Outcome(null))

        override fun onException(execution: ScriptExecution, e: Throwable) = record(execution.id, Outcome(e))

        private fun record(id: Int, outcome: Outcome) {
            lock.lock()
            try {
                outcomes[id] = outcome
                changed.signalAll()
            } finally {
                lock.unlock()
            }
        }

        fun peek(id: Int): Outcome? {
            lock.lock()
            try {
                return outcomes[id]
            } finally {
                lock.unlock()
            }
        }

        fun await(id: Int, timeoutMs: Long): Outcome? {
            var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            lock.lock()
            try {
                while (true) {
                    outcomes[id]?.let { return it }
                    if (remainingNanos <= 0L) return null
                    remainingNanos = changed.awaitNanos(remainingNanos)
                }
            } finally {
                lock.unlock()
            }
        }
    }

    /** A start request the policy refuses, with the one reason that applies. */
    private class ExecutionDenied(val error: NodeBridgeError) : RuntimeException(error.message)

    /**
     * The execution config of a start request, or [ExecutionDenied] naming the one rule it breaks: a
     * missing or out-of-range `timeoutMs`, the spawn depth, an unusable working directory (its own code,
     * see NodeBridgeWorkingDirectory), or a `cwd` outside it.
     * zh-CN: 启动请求的执行配置, 或抛出 [ExecutionDenied] 并指明违反的那一条规则: `timeoutMs` 缺失或越界, 派生深度, 工作目录不可用
     * (使用专用错误码, 见 NodeBridgeWorkingDirectory), 或 `cwd` 超出工作目录.
     */
    private fun buildExecutionConfig(request: NodeBridgeRequest, options: JSONObject): ExecutionConfig {
        val label = "${request.module}.${request.method}"
        fun deny(message: String): Nothing = throw ExecutionDenied(NodeBridgeError.permissionDenied("$label $message", request.module, request.method))
        if (!options.has("timeoutMs") || options.isNull("timeoutMs")) {
            deny("requires options.timeoutMs between 1 and $MAX_EXEC_START_TIMEOUT_MS ms.")
        }
        val timeoutMs = options.optLong("timeoutMs", 0L)
        if (timeoutMs <= 0L || timeoutMs > MAX_EXEC_START_TIMEOUT_MS) {
            deny("options.timeoutMs must be between 1 and $MAX_EXEC_START_TIMEOUT_MS ms.")
        }
        val parentSpawnDepth = options.optInt(INTERNAL_PARENT_SPAWN_DEPTH, 0)
            .coerceAtLeast(0)
        if (parentSpawnDepth >= NodeBridgeEngineInfo.MAX_SPAWN_DEPTH) {
            deny("cannot start a script from a script more than ${NodeBridgeEngineInfo.MAX_SPAWN_DEPTH} level(s) deep.")
        }
        val baseCwd = options.optString(INTERNAL_BASE_CWD)
        val base = baseCwd.takeIf { it.isNotBlank() && !it.contains('\u0000') }
            ?.let { runCatching { File(it).canonicalFile }.getOrNull() }
            ?.takeIf { it.isDirectory }
            ?: throw ExecutionDenied(NodeBridgeWorkingDirectory.error(baseCwd, request.module, request.method)
                ?: NodeBridgeError.permissionDenied("$label requires the scoped AutoJs6 working directory.", request.module, request.method))
        val cwd = resolveScopedCwd(base.path, options.optString("cwd", "."))
            ?: deny("options.cwd must name an existing directory inside the working directory ${base.path} (a relative path without .. segments).")
        return ExecutionConfig(
            workingDirectory = cwd.absolutePath,
            delay = options.optLong("delay", 0L).coerceAtLeast(0L),
            interval = options.optLong("interval", 0L).coerceAtLeast(0L),
            loopTimes = options.optInt("loopTimes", 1).coerceIn(1, MAX_LOOP_TIMES),
        ).apply {
            val spawnDepth = (parentSpawnDepth + 1).coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH)
            setArgument(NodeBridgeEngineInfo.SPAWN_DEPTH_ARGUMENT, spawnDepth)
            optArguments(options)?.let { argumentObject ->
                for (key in argumentObject.keys()) {
                    setArgument(key, argumentObject.get(key))
                }
            }
        }
    }

    private fun optArguments(options: JSONObject): JSONObject? {
        if (!options.has("arguments") || options.isNull("arguments")) {
            return null
        }
        val arguments = options.optJSONObject("arguments") ?: return null
        val sanitized = JSONObject()
        for (key in arguments.keys()) {
            if (key.isBlank() || key.contains('\u0000')) {
                return null
            }
            when (val value = arguments.get(key)) {
                is String -> {
                    if (value.contains('\u0000')) return null
                    sanitized.put(key, value)
                }
                is Number, is Boolean -> sanitized.put(key, value)
                JSONObject.NULL -> sanitized.put(key, JSONObject.NULL)
                else -> return null
            }
        }
        return sanitized
    }

    private fun resolveScopedCwd(baseCwd: String, cwd: String): File? {
        if (baseCwd.contains('\u0000') || cwd.contains('\u0000')) {
            return null
        }
        val base = runCatching { File(baseCwd).canonicalFile }.getOrNull()
            ?.takeIf { it.isDirectory }
            ?: return null
        val cwdText = cwd.ifBlank { "." }
        if (File(cwdText).isAbsolute || cwdText.hasParentSegment()) {
            return null
        }
        return runCatching { File(base, cwdText).canonicalFile }.getOrNull()
            ?.takeIf { it.isDirectory && it.isInsideOrSame(base) }
    }

    private fun resolveScopedFile(cwd: String, path: String): File? {
        if (cwd.contains('\u0000') || path.contains('\u0000')) {
            return null
        }
        val base = runCatching { File(cwd).canonicalFile }.getOrNull()
            ?.takeIf { it.isDirectory }
            ?: return null
        if (File(path).isAbsolute || path.hasParentSegment()) {
            return null
        }
        return runCatching { File(base, path).canonicalFile }.getOrNull()
            ?.takeIf { it.isInsideOrSame(base) }
    }

    private fun policyFailure(request: NodeBridgeRequest, message: String): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.permissionDenied(
                message = message,
                module = request.module,
                method = request.method,
            ),
        )
    }

    private fun NodeBridgeRequest.argsArray(): JSONArray {
        return runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())
    }

    private fun ScriptExecution.toBridgeJsonObject(spawnDepth: Int): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("engineName", source.engineName.orEmpty())
            put("sourceName", source.name)
            put("workingDirectory", config.workingDirectory)
            put("spawnDepth", spawnDepth)
        }
    }

    private fun ExecutionConfig.spawnDepth(): Int {
        return (getArgument(NodeBridgeEngineInfo.SPAWN_DEPTH_ARGUMENT) as? Number)
            ?.toInt()
            ?.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH)
            ?: 0
    }

    private fun String.hasParentSegment(): Boolean {
        return split('/', '\\').any { it == ".." }
    }

    private fun File.isInsideOrSame(root: File): Boolean {
        val rootPath = root.path
        val path = path
        return path == rootPath || path.startsWith(rootPath + File.separator)
    }

    private fun NodeBridgeResponse.toBundle(): Bundle {
        return Bundle().apply {
            putString(EXTRA_RESPONSE_JSON, toJson())
        }
    }

    /** A one-bit sender-owned token makes cancellation observable before service dispatch starts. */
    private class DispatchCancellation : Binder() {
        val cancelled = AtomicBoolean()
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            reply?.writeNoException()
            reply?.writeInt(if (cancelled.get()) 1 else 0)
            return true
        }

        companion object {
            private const val DESCRIPTOR = "org.autojs.autojs.engine.RegisteredScriptCancellation"
            fun read(binder: IBinder): Boolean {
                if (binder is DispatchCancellation) return binder.cancelled.get()
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) true else {
                        reply.readException()
                        reply.readInt() != 0
                    }
                } catch (_: Exception) { true } finally { data.recycle(); reply.recycle() }
            }
        }
    }

    companion object {
        const val INTERNAL_BASE_CWD = "__autojs6BaseCwd"
        const val INTERNAL_PARENT_SPAWN_DEPTH = "__autojs6ParentSpawnDepth"

        private const val ACTION_DISPATCH = "org.autojs.autojs.engine.action.DISPATCH_NODE_BRIDGE_ENGINES"
        private const val ACTION_DYNAMIC = "org.autojs.autojs.engine.action.DISPATCH_DYNAMIC_AGENT_SCRIPT"
        private const val ACTION_REGISTERED = "org.autojs.autojs.engine.action.DISPATCH_REGISTERED_AGENT_SCRIPT"
        private const val ACTION_CANCEL_REGISTERED = "org.autojs.autojs.engine.action.CANCEL_REGISTERED_AGENT_SCRIPT"
        private const val EXTRA_REGISTERED_TOKEN = "registered_token"
        private const val EXTRA_REGISTERED_JSON = "registered_json"
        private const val EXTRA_REGISTERED_CONTROL = "registered_control"
        private const val EXTRA_REQUEST_JSON = "request_json"
        private const val EXTRA_RESPONSE_JSON = "response_json"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val RESULT_RESPONSE = 1
        private const val MAX_EXEC_START_TIMEOUT_MS = 60_000L
        private const val MAX_LOOP_TIMES = 1_000
        private const val MAX_WAIT_MS = 300_000L
        private const val OUTCOME_RUNNING = "running"
        private const val OUTCOME_SUCCESS = "success"
        private const val OUTCOME_EXCEPTION = "exception"
        private const val MAX_REGISTERED_DISPATCHES = 8
        private val registeredExecutions = ConcurrentHashMap<String, AgentOwnedScriptExecution>()
        private val registeredExecutor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAX_REGISTERED_DISPATCHES),
            { Thread(it, "AutoJs6AgentRegisteredScript").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())

        /**
         * Dispatches a path and scalar parameters already authorized by the host's Agent catalog.
         * Closing the returned handle cancels only this invocation, including the pre-start race.
         * Ordinary engines.execScript/File waiting semantics are unchanged.
         */
        fun dispatchRegistered(
            context: Context,
            request: NodeBridgeRequest,
            launchPath: String,
            workingDirectory: String,
            parametersJson: String,
            agentRunId: String,
            presetName: String?,
            timeoutMs: Long,
            captureConsole: Boolean,
            expectedScriptPath: String = launchPath,
            callback: (NodeBridgeResponse) -> Unit,
        ): Closeable {
            val payload = JSONObject().put("launchPath", launchPath).put("workingDirectory", workingDirectory)
                .put("expectedScriptPath", expectedScriptPath)
                .put("parametersJson", parametersJson).put("runId", agentRunId).put("presetName", presetName.orEmpty())
                .put("timeoutMs", timeoutMs).put("captureConsole", captureConsole)
            return dispatchOwned(context, request, ACTION_REGISTERED, payload, callback)
        }

        /** Same ownership protocol as registered scripts, using bounded in-memory Rhino source. */
        fun dispatchDynamic(
            context: Context, request: NodeBridgeRequest, name: String, source: String,
            workingDirectory: String, agentRunId: String, presetName: String?, timeoutMs: Long,
            captureConsole: Boolean, callback: (NodeBridgeResponse) -> Unit,
        ): Closeable {
            val payload = JSONObject().put("name", name).put("source", source).put("workingDirectory", workingDirectory)
                .put("runId", agentRunId).put("presetName", presetName.orEmpty())
                .put("timeoutMs", timeoutMs).put("captureConsole", captureConsole)
            return dispatchOwned(context, request, ACTION_DYNAMIC, payload, callback)
        }

        private fun dispatchOwned(
            context: Context, request: NodeBridgeRequest, dispatchAction: String, payload: JSONObject,
            callback: (NodeBridgeResponse) -> Unit,
        ): Closeable {
            val appContext = context.applicationContext
            val token = UUID.randomUUID().toString()
            val terminal = AtomicBoolean()
            val cancelled = AtomicBoolean()
            val cancellationSignal = DispatchCancellation()
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (!terminal.compareAndSet(false, true)) return
                    val response = if (resultCode == RESULT_RESPONSE) {
                        runCatching { NodeBridgeResponse.fromJson(resultData?.getString(EXTRA_RESPONSE_JSON).orEmpty()) }
                            .getOrElse { request.processDead("Agent script dispatch returned a malformed response") }
                    } else request.processDead("Agent script dispatch returned an unexpected result")
                    callback(response)
                }
            }
            val intent = Intent(appContext, NodeBridgeEngineDispatchService::class.java).apply {
                action = dispatchAction
                putExtra(EXTRA_REQUEST_JSON, request.toJson())
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
                putExtra(EXTRA_REGISTERED_TOKEN, token)
                putExtra(EXTRA_REGISTERED_JSON, payload.toString())
                putExtra(EXTRA_REGISTERED_CONTROL, Bundle().apply { putBinder("cancel", cancellationSignal) })
            }
            val started = runCatching { appContext.startService(intent) != null }.getOrDefault(false)
            if (!started && terminal.compareAndSet(false, true)) {
                callback(request.processDead("Failed to start Agent script dispatch service"))
            }
            return Closeable {
                if (!terminal.get() && cancelled.compareAndSet(false, true)) {
                    cancellationSignal.cancelled.set(true)
                    val local = registeredExecutions[token]
                    if (local != null) local.cancel() else {
                        runCatching { appContext.startService(Intent(appContext, NodeBridgeEngineDispatchService::class.java).apply {
                            action = ACTION_CANCEL_REGISTERED
                            putExtra(EXTRA_REGISTERED_TOKEN, token)
                        }) }
                    }
                }
            }
        }

        fun dispatch(context: Context, request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
            val appContext = context.applicationContext
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_RESPONSE) {
                        callback(request.processDead("Main-process engines bridge returned an unexpected result."))
                        return
                    }
                    val responseJson = resultData?.getString(EXTRA_RESPONSE_JSON).orEmpty()
                    callback(
                        runCatching { NodeBridgeResponse.fromJson(responseJson) }
                            .getOrElse {
                                request.processDead("Main-process engines bridge returned a malformed response.")
                            },
                    )
                }
            }
            val intent = Intent(appContext, NodeBridgeEngineDispatchService::class.java).apply {
                action = ACTION_DISPATCH
                putExtra(EXTRA_REQUEST_JSON, request.toJson())
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
            }
            val component = runCatching { appContext.startService(intent) }.getOrElse { error ->
                callback(request.processDead(error.message ?: error.javaClass.name))
                return
            }
            if (component == null) {
                callback(request.processDead("Failed to start main-process engines bridge service."))
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
