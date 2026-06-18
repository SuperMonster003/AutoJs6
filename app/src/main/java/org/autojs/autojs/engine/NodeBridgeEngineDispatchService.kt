package org.autojs.autojs.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class NodeBridgeEngineDispatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val requestJson = intent?.getStringExtra(EXTRA_REQUEST_JSON).orEmpty()
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
        val config = buildExecutionConfig(request, options)
            ?: return policyFailure(request, "engines.execScript requires scoped cwd, positive timeoutMs, and allowed spawn depth.")
        val source = StringScriptSource(name, script).apply { prefix = "\$engine/" }
        if (source.engineName == JavaScriptSource.ENGINE_NODEJS) {
            return policyFailure(request, "engines.execScript cannot start nested embedded Node scripts yet.")
        }
        val execution = runCatching {
            AutoJs.instance.scriptEngineService.execute(source, config)
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
        return NodeBridgeResponse.success(request.id, execution.toBridgeJson(config.spawnDepth()))
    }

    private fun executeScriptFile(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = request.argsArray()
        val path = args.optString(0).takeIf { it.isNotBlank() && !it.contains('\u0000') }
            ?: return policyFailure(request, "engines.execScriptFile requires a non-empty scoped path without NUL bytes.")
        val options = args.optJSONObject(1) ?: JSONObject()
        val config = buildExecutionConfig(request, options)
            ?: return policyFailure(request, "engines.execScriptFile requires scoped cwd, positive timeoutMs, and allowed spawn depth.")
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
            return policyFailure(request, "engines.execScriptFile cannot start nested embedded Node scripts yet.")
        }
        val executionConfig = launch.createExecutionConfig(
            base = config,
            overrideWorkingDirectory = true,
        )
        val execution = runCatching {
            AutoJs.instance.scriptEngineService.execute(launch.source, executionConfig)
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
        return NodeBridgeResponse.success(request.id, execution.toBridgeJson(executionConfig.spawnDepth()))
    }

    private fun buildExecutionConfig(request: NodeBridgeRequest, options: JSONObject): ExecutionConfig? {
        if (!options.has("timeoutMs") || options.isNull("timeoutMs")) {
            return null
        }
        val timeoutMs = options.optLong("timeoutMs", 0L)
        if (timeoutMs <= 0L || timeoutMs > MAX_EXEC_START_TIMEOUT_MS) {
            return null
        }
        val parentSpawnDepth = options.optInt(INTERNAL_PARENT_SPAWN_DEPTH, 0)
            .coerceAtLeast(0)
        if (parentSpawnDepth >= NodeBridgeEngineInfo.MAX_SPAWN_DEPTH) {
            return null
        }
        val baseCwd = options.optString(INTERNAL_BASE_CWD).takeIf { it.isNotBlank() }
            ?: return null
        val cwd = resolveScopedCwd(baseCwd, options.optString("cwd", "."))
            ?: return null
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

    private fun ScriptExecution.toBridgeJson(spawnDepth: Int): String {
        return JSONObject().apply {
            put("id", id)
            put("engineName", source.engineName.orEmpty())
            put("sourceName", source.name)
            put("workingDirectory", config.workingDirectory)
            put("spawnDepth", spawnDepth)
        }.toString()
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

    companion object {
        const val INTERNAL_BASE_CWD = "__autojs6BaseCwd"
        const val INTERNAL_PARENT_SPAWN_DEPTH = "__autojs6ParentSpawnDepth"

        private const val ACTION_DISPATCH = "org.autojs.autojs.engine.action.DISPATCH_NODE_BRIDGE_ENGINES"
        private const val EXTRA_REQUEST_JSON = "request_json"
        private const val EXTRA_RESPONSE_JSON = "response_json"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val RESULT_RESPONSE = 1
        private const val MAX_EXEC_START_TIMEOUT_MS = 60_000L
        private const val MAX_LOOP_TIMES = 1_000

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
