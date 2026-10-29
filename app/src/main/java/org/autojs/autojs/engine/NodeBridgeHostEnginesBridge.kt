package org.autojs.autojs.engine

import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ScriptExecution
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-process semantics of the Node Bridge `engines` module (MCP roadmap P1.3): `list()` and
 * `stop(id)` see the executions tracked by the host's `ScriptEngineService`, and `stopAll` with
 * `{scope: "host"}` stops all of them. The existing plugin-process views (`all`, `stopAll` without
 * a scope) are left untouched by the caller.
 *
 * zh-CN: Node Bridge `engines` 模块的宿主进程语义 (MCP 路线图 P1.3): `list()` 与 `stop(id)` 作用于宿主 `ScriptEngineService` 跟踪的执行,
 * 带 `{scope: "host"}` 的 `stopAll` 停止全部执行. 既有的插件进程视角 (`all`, 无 scope 的 `stopAll`) 由调用方原样保留.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal class NodeBridgeHostEnginesBridge(
    private val serviceProvider: () -> ScriptEngineService = { AutoJs.instance.scriptEngineService },
) {

    fun list(request: NodeBridgeRequest): NodeBridgeResponse = guarded(request) {
        val now = System.currentTimeMillis()
        val executions = snapshot().sortedBy { it.id }
        val array = JSONArray()
        executions.forEach { array.put(executionJson(it, now)) }
        JSONObject()
            .put("schema", SCHEMA_LIST)
            .put("scope", SCOPE_HOST)
            .put("count", array.length())
            .put("executions", array)
    }

    fun stop(request: NodeBridgeRequest): NodeBridgeResponse = guarded(request) {
        val args = request.args()
        val id = (args.opt(0) as? Number)?.toInt()
            ?: throw IllegalArgumentException("engines.stop requires a numeric execution id")
        val execution = serviceProvider().getScriptExecution(id)
            ?: throw IllegalArgumentException("no running execution with id $id")
        val engine = execution.engine
        val stopped = if (engine != null && !engine.isDestroyed) {
            engine.forceStop()
            true
        } else {
            false
        }
        JSONObject()
            .put("schema", SCHEMA_STOP)
            .put("scope", SCOPE_HOST)
            .put("id", id)
            .put("sourceName", execution.source.name)
            .put("state", stateOf(execution))
            .put("stopped", stopped)
    }

    /**
     * Handles `stopAll` only when `{scope: "host"}` is passed; null keeps the caller's legacy
     * plugin-process answer. Other scope values are rejected.
     * zh-CN: 仅在传入 `{scope: "host"}` 时处理 `stopAll`; 返回 null 表示由调用方保留既有的插件进程答复. 其他 scope 值被拒绝.
     */
    fun stopAll(request: NodeBridgeRequest): NodeBridgeResponse? {
        val options = request.args().optJSONObject(0) ?: return null
        val scope = options.optString("scope", "")
        return when (scope) {
            "", SCOPE_PLUGIN -> null
            SCOPE_HOST -> guarded(request) {
                val stopped = serviceProvider().stopAll()
                JSONObject()
                    .put("schema", SCHEMA_STOP_ALL)
                    .put("scope", SCOPE_HOST)
                    .put("stopped", stopped)
            }
            else -> NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest(
                    "engines.stopAll scope must be \"$SCOPE_HOST\" or \"$SCOPE_PLUGIN\", got \"$scope\"",
                    request.module,
                    request.method,
                ),
            )
        }
    }

    /** Rhino's engines.stopAllAndToast (api-coverage P3.1b): every host script stopped, with the host's toast. */
    fun stopAllAndToast(request: NodeBridgeRequest): NodeBridgeResponse = guarded(request) {
        JSONObject()
            .put("schema", SCHEMA_STOP_ALL)
            .put("scope", SCOPE_HOST)
            .put("stopped", serviceProvider().stopAllAndToast())
    }

    private fun snapshot(): List<ScriptExecution> {
        val service = serviceProvider()
        repeat(3) {
            runCatching { return ArrayList(service.scriptExecutions) }
        }
        return ArrayList(service.scriptExecutions)
    }

    private fun executionJson(execution: ScriptExecution, now: Long): JSONObject {
        val source = execution.source
        val startedAt = runCatching { execution.engine?.startTime }.getOrNull() ?: 0L
        return JSONObject()
            .put("id", execution.id)
            .put("engineName", source.engineName.orEmpty())
            .put("sourceName", source.name)
            .put("sourcePath", source.elegantPath)
            .put("workingDirectory", execution.config.workingDirectory)
            .put("state", stateOf(execution))
            .put("startedAt", startedAt)
            .put("uptimeMs", if (startedAt > 0L) now - startedAt else JSONObject.NULL)
    }

    private fun stateOf(execution: ScriptExecution): String {
        val engine = execution.engine ?: return STATE_STARTING
        return if (engine.isDestroyed) STATE_FINISHED else STATE_RUNNING
    }

    private fun guarded(request: NodeBridgeRequest, block: () -> JSONObject): NodeBridgeResponse {
        return try {
            NodeBridgeResponse.success(request.id, block().toString())
        } catch (error: IllegalArgumentException) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest("engines.${request.method}: ${error.message}", request.module, request.method),
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
        }
    }

    private fun NodeBridgeRequest.args(): JSONArray = runCatching { JSONArray(argsJson) }.getOrDefault(JSONArray())

    companion object {
        const val SCHEMA_LIST = "autojs6-bridge-engines-list-v1"
        const val SCHEMA_STOP = "autojs6-bridge-engines-stop-v1"
        const val SCHEMA_STOP_ALL = "autojs6-bridge-engines-stop-all-v1"
        const val SCOPE_HOST = "host"
        const val SCOPE_PLUGIN = "plugin"
        const val STATE_STARTING = "starting"
        const val STATE_RUNNING = "running"
        const val STATE_FINISHED = "finished"
    }
}
