package org.autojs.autojs.engine

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.terminal.TerminalError
import org.autojs.autojs.core.plugin.terminal.TerminalErrorMapper
import org.autojs.autojs.core.plugin.terminal.TerminalJson
import org.autojs.autojs.runtime.api.terminal.TerminalScriptArguments
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The `terminal` module of the JS runtimes' bridge (api-coverage P3.3, Q167 (a)): the methods of Rhino's `terminal` object
 * and of its `TerminalSession` over the same [TerminalService] (the 3-Shell Terminal plugin), with the argument shapes of
 * [TerminalScriptArguments]. Sessions travel as JSON documents (`id`, `cwd`, `title`, `createdAt`, `pid`, `state`,
 * `alive`, `exitCode`); the runtime keeps the session object locally and calls the session methods here by id. Every call
 * runs on an IO coroutine and answers once; a failure carries the Rhino `TerminalError` code (`PLUGIN_UNAVAILABLE`,
 * `INVALID_ARGUMENT`, ...) as `error.code`, `TerminalError` as `error.name` and the plugin center's hint in the message.
 * [close] runs at the end of the execution: it cancels the pending calls and the callback lease but closes no session,
 * as for Rhino scripts. The capability is `terminal`, never implied by `shell` or `shell.root`.
 * zh-CN: JS 运行时桥接的 `terminal` 模块 (api-coverage P3.3, Q167 (a)): 经同一个 [TerminalService] (3-Shell Terminal 插件) 提供 Rhino `terminal`
 * 对象及其 `TerminalSession` 的方法, 参数形态同 [TerminalScriptArguments]. 会话以 JSON 文档传递 (`id`, `cwd`, `title`, `createdAt`, `pid`,
 * `state`, `alive`, `exitCode`); 运行时在本地保留会话对象, 按 id 调用这里的会话方法. 每次调用在 IO 协程上运行且只应答一次; 失败时 `error.code` 为
 * Rhino `TerminalError` 的代码 (`PLUGIN_UNAVAILABLE`, `INVALID_ARGUMENT` 等), `error.name` 为 `TerminalError`, 消息带插件中心提示. [close] 在执行
 * 结束时运行: 取消挂起的调用与回调租约, 但与 Rhino 脚本一样不关闭任何会话. 能力为 `terminal`, 不被 `shell` 或 `shell.root` 隐含.
 */
internal class RuntimeTerminalBridge(context: Context, cwd: String?) : Closeable {

    private val service = TerminalService(context) { cwd?.takeIf { it.isNotBlank() } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (closed.get()) {
            callback(NodeBridgeResponse.failure(request.id, failure(request.method, TerminalErrorMapper.normalize(TerminalErrorCodes.SESSION_CLOSED, "the execution has ended"))))
            return
        }
        val answered = AtomicBoolean(false)
        fun answer(response: NodeBridgeResponse) {
            if (answered.compareAndSet(false, true)) callback(response)
        }
        val job = scope.launch {
            answer(try {
                NodeBridgeResponse.success(request.id, invoke(request.method, JSONArray(request.argsJson)))
            } catch (error: Throwable) {
                NodeBridgeResponse.failure(request.id, failure(request.method, terminalError(error)))
            })
        }
        // A call cancelled by close before it ran still answers once.
        // zh-CN: 运行前即被 close 取消的调用仍然应答一次.
        job.invokeOnCompletion { cause ->
            if (cause != null) answer(NodeBridgeResponse.failure(request.id, failure(request.method, terminalError(cause))))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        service.close()
        scope.cancel()
    }

    private suspend fun invoke(method: String, args: JSONArray): String {
        checkArity(method, args)
        return when (method) {
            "state" -> JSONObject.quote(service.state())
            "isAvailable" -> service.isAvailable().toString()
            "open" -> {
                val open = TerminalScriptArguments.open(element(args, 0))
                val request = TerminalScriptArguments.request(open, service.cwd())
                sessionJson(service.open(request, open.show)).toString()
            }
            "sessions" -> JSONArray().apply { service.sessions().forEach { put(sessionJson(it)) } }.toString()
            "session" -> service.session(TerminalScriptArguments.sessionId(element(args, 0)))?.let { sessionJson(it).toString() } ?: "null"
            "close" -> service.close(TerminalScriptArguments.sessionId(element(args, 0))).toString()
            "closeAll" -> service.closeAll().toString()
            "show" -> {
                service.show(TerminalScriptArguments.optionalSessionId(element(args, 0)))
                "null"
            }
            "exec" -> run(TerminalScriptArguments.exec(element(args, 0), element(args, 1)))
            "npmRun" -> run(TerminalScriptArguments.npmRun(element(args, 0), element(args, 1), element(args, 2)))
            "env" -> environmentJson(service.environment()).toString()
            "write" -> {
                service.write(TerminalScriptArguments.sessionId(element(args, 0)), TerminalScriptArguments.input(element(args, 1)))
                "null"
            }
            "waitFor" -> {
                val id = TerminalScriptArguments.sessionId(element(args, 0))
                val (regex, text) = pattern(args.opt(1))
                val matcher = TerminalScriptArguments.lineMatcher(regex, text)
                val timeout = TerminalScriptArguments.waitTimeout(element(args, 2))
                JSONObject.quote(service.waitForLine(id, stripAnsi(args.opt(3)), matcher, timeout))
            }
            "transcript" -> {
                val id = TerminalScriptArguments.sessionId(element(args, 0))
                JSONObject.quote(service.transcript(id, TerminalScriptArguments.transcriptMaxBytes(element(args, 1))).text)
            }
            "isAlive" -> {
                val id = TerminalScriptArguments.sessionId(element(args, 0))
                (service.exitCodeOf(id) == null && service.session(id)?.alive == true).toString()
            }
            else -> throw TerminalScriptArguments.invalid("unknown terminal method '$method'")
        }
    }

    /** `exec` / `npmRun`: registers the exit callback, opens with the command, then waits for the exit when `wait` (as Rhino). zh-CN: `exec` / `npmRun`: 登记退出回调, 带命令打开, `wait` 时再等待退出 (同 Rhino). */
    private suspend fun run(exec: TerminalScriptArguments.Exec): String {
        val request = TerminalScriptArguments.request(exec.open, service.cwd(), exec.command, exec.keepOpen)
        service.ensureCallback()
        var session = service.open(request, show = exec.open.show)
        if (exec.wait) session = service.awaitExit(session, exec.timeoutMillis)
        return sessionJson(session).toString()
    }

    private fun sessionJson(session: TerminalJson.Session): JSONObject = sessionJson(session, service.exitCodeOf(session.id))

    companion object {

        /**
         * Methods of the bridge module with their argument counts (api-coverage P3.3). `waitFor [id, pattern, timeoutMs?,
         * stripAnsi?]` takes the session's `stripAnsi` (Rhino keeps it on the session object; default true).
         * zh-CN: 桥接模块的方法及其参数个数 (api-coverage P3.3). `waitFor [id, pattern, timeoutMs?, stripAnsi?]` 接收会话的 `stripAnsi`
         * (Rhino 把它保存在会话对象上; 默认 true).
         */
        val ARITY: Map<String, IntRange> = linkedMapOf(
            "state" to 0..0, "isAvailable" to 0..0, "open" to 0..1, "sessions" to 0..0, "session" to 1..1, "close" to 1..1,
            "closeAll" to 0..0, "show" to 0..1, "exec" to 1..2, "env" to 0..0, "npmRun" to 1..3, "write" to 2..2,
            "waitFor" to 2..4, "transcript" to 1..2, "isAlive" to 1..1,
        )

        val METHODS: Set<String> = ARITY.keys

        fun checkArity(method: String, args: JSONArray) {
            val range = ARITY[method] ?: throw TerminalScriptArguments.invalid("unknown terminal method '$method'")
            if (args.length() !in range) throw TerminalScriptArguments.invalid("terminal.$method expects ${range.first}..${range.last} arguments, got ${args.length()}")
        }

        /** Argument [index] as the Gson tree [TerminalScriptArguments] reads; a missing argument is null. zh-CN: 第 [index] 个参数, 转为 [TerminalScriptArguments] 读取的 Gson 树; 缺失的参数为 null. */
        fun element(args: JSONArray, index: Int): JsonElement? =
            if (index >= args.length()) null else JsonParser.parseString(NodeBridgeJson.valueToJson(args.opt(index)))

        /**
         * The `waitFor` pattern: a string is a substring test, `{regex, flags?}` (the `source` and `flags` of a RegExp)
         * becomes the `/source/flags` literal Rhino passes for a RegExp.
         * zh-CN: `waitFor` 的模式: 字符串为子串判定, `{regex, flags?}` (RegExp 的 `source` 与 `flags`) 转为 Rhino 对 RegExp 传入的
         * `/source/flags` 字面量.
         */
        fun pattern(value: Any?): Pair<String?, String?> = when (value) {
            is String -> null to value
            is JSONObject -> {
                val source = value.opt("regex") as? String ?: throw TerminalScriptArguments.invalid("'pattern.regex' must be a string")
                val flags = value.opt("flags").let { if (it == null || it == JSONObject.NULL) "" else it as? String ?: throw TerminalScriptArguments.invalid("'pattern.flags' must be a string") }
                "/$source/$flags" to null
            }
            else -> throw TerminalScriptArguments.invalid("'pattern' must be a string or a RegExp")
        }

        fun stripAnsi(value: Any?): Boolean = when (value) {
            null, JSONObject.NULL -> true
            is Boolean -> value
            else -> throw TerminalScriptArguments.invalid("'stripAnsi' must be a boolean")
        }

        /**
         * The session document: an exit code reported through the callback wins over the listed one and marks the session
         * exited, as Rhino's `TerminalSession.state` / `exitCode` do.
         * zh-CN: 会话文档: 经回调报告的退出码优先于列表中的退出码, 并把会话标记为已退出, 同 Rhino 的 `TerminalSession.state` / `exitCode`.
         */
        fun sessionJson(session: TerminalJson.Session, reportedExitCode: Int?): JSONObject {
            val exitCode = reportedExitCode ?: session.exitCode
            return JSONObject()
                .put("id", session.id)
                .put("cwd", session.cwd)
                .put("title", session.title)
                .put("createdAt", session.createdAt)
                .put("pid", session.pid)
                .put("state", if (reportedExitCode != null) TerminalContract.STATE_EXITED else session.state)
                .put("alive", session.alive && reportedExitCode == null)
                .put("exitCode", exitCode ?: JSONObject.NULL)
        }

        /** The `TerminalEnvironmentInfo` shape of Rhino's `terminal.env()`. zh-CN: Rhino `terminal.env()` 的 `TerminalEnvironmentInfo` 形态. */
        fun environmentJson(environment: TerminalJson.Environment): JSONObject = JSONObject()
            .put("home", environment.home ?: JSONObject.NULL)
            .put("prefix", environment.prefix ?: JSONObject.NULL)
            .put("shell", environment.shell ?: JSONObject.NULL)
            .put("node", JSONObject()
                .put("available", environment.node.available)
                .put("version", environment.node.version ?: JSONObject.NULL)
                .put("packageName", environment.node.packageName ?: JSONObject.NULL)
                .put("pluginVersion", environment.node.pluginVersion ?: JSONObject.NULL)
                .put("reason", environment.node.reason ?: JSONObject.NULL))
            .put("npmRegistry", environment.npmRegistry ?: JSONObject.NULL)
            .put("storageAccess", environment.storageAccess ?: JSONObject.NULL)
            .put("pluginVersion", environment.pluginVersion ?: JSONObject.NULL)
            .put("contractVersion", environment.contractVersion)

        /** Any failure as the terminal error a Rhino script would catch. zh-CN: 任何失败转为 Rhino 脚本会捕获到的终端错误. */
        fun terminalError(error: Throwable): TerminalError = when (error) {
            is IllegalArgumentException, is JSONException -> TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, error.message ?: "invalid argument")
            else -> TerminalErrorMapper.fromThrowable(error, TerminalErrorMapper.Phase.CALL)
        }

        /**
         * The bridge error of [error]: `code` is the terminal code, `name` is `TerminalError`; the category follows the code
         * (plugin missing -> unavailable, bad argument -> invalid-request, ...).
         * zh-CN: [error] 对应的桥接错误: `code` 为终端代码, `name` 为 `TerminalError`; 类别随代码而定 (插件缺失 -> unavailable, 参数错误 ->
         * invalid-request 等).
         */
        fun failure(method: String, error: TerminalError): NodeBridgeError = NodeBridgeError(
            name = "TerminalError",
            message = error.message,
            code = error.code,
            module = NodeBridgePermissionManifest.TERMINAL,
            method = method,
            category = when (error.code) {
                TerminalErrorCodes.PLUGIN_UNAVAILABLE, TerminalErrorCodes.PLUGIN_INCOMPATIBLE, TerminalErrorCodes.NODE_UNAVAILABLE -> NodeBridgeErrorCategory.UNAVAILABLE
                TerminalErrorCodes.INVALID_ARGUMENT -> NodeBridgeErrorCategory.INVALID_REQUEST
                TerminalErrorCodes.TIMEOUT -> NodeBridgeErrorCategory.TIMEOUT
                TerminalErrorCodes.SESSION_LIMIT, TerminalErrorCodes.SUBSCRIPTION_LIMIT -> NodeBridgeErrorCategory.RESOURCE_LIMIT
                TerminalErrorCodes.STORAGE_PERMISSION_REQUIRED -> NodeBridgeErrorCategory.PERMISSION_DENIED
                else -> NodeBridgeErrorCategory.PROVIDER_FAILED
            },
        )
    }
}
