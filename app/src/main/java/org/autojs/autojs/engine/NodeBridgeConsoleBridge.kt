package org.autojs.autojs.engine

import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleLogFormat
import org.json.JSONArray
import org.json.JSONObject

/**
 * The JSON layer of the Node Bridge `console` module (MCP roadmap P1.3) over the shared global
 * console: `tail(options?)` with `{lines <= 500, sinceId, level, maxBytes <= 256 KiB}` returns the
 * newest matching entries in id order plus the ids a caller needs to continue. Every script
 * runtime prints into the one `GlobalConsole`, so a tail is process-wide; per-execution capture
 * is an id window over the same console (see [captureJson]).
 *
 * zh-CN: Node Bridge `console` 模块 (MCP 路线图 P1.3) 的 JSON 层, 基于共享的全局控制台: `tail(options?)` 接受 `{lines <= 500, sinceId, level,
 * maxBytes <= 256 KiB}`, 按 id 顺序返回最新的匹配条目及续读所需的 id. 所有脚本运行时都打印到同一个 `GlobalConsole`, 因此尾部是进程级的;
 * 按执行归集即同一控制台上的 id 窗口 (见 [captureJson]).
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal class NodeBridgeConsoleBridge(
    private val consoleProvider: () -> ConsoleImpl = { AutoJs.instance.globalConsole },
    private val controls: RuntimeConsoleBridge? = null,
) {

    fun dispatch(request: NodeBridgeRequest): NodeBridgeResponse {
        return try {
            when (request.method) {
                "tail" -> tail(request)
                in RuntimeConsoleBridge.METHODS -> {
                    val control = checkNotNull(controls) { "Console controls require a script execution" }
                    control.invoke(request.method, JSONArray(request.argsJson))
                    NodeBridgeResponse.success(request.id, "null")
                }
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        } catch (error: SecurityException) {
            NodeBridgeResponse.failure(request.id, NodeBridgeError.permissionDenied(error.message ?: "Console permission denied", request.module, request.method))
        } catch (error: IllegalArgumentException) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest("console.${request.method}: ${error.message}", request.module, request.method),
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

    private fun tail(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = runCatching { JSONArray(request.argsJson) }.getOrDefault(JSONArray())
        val options = optionsArg(args, 0, TAIL_KEYS)
        val lines = intOf(options, "lines", NodeBridgeConsoleTail.DEFAULT_LINES)
        require(lines in 1..NodeBridgeConsoleTail.MAX_LINES) { "lines must be between 1 and ${NodeBridgeConsoleTail.MAX_LINES}, got $lines" }
        val sinceId = intOf(options, "sinceId", NodeBridgeConsoleTail.NO_ID)
        require(sinceId >= NodeBridgeConsoleTail.NO_ID) { "sinceId must be -1 or a log entry id, got $sinceId" }
        val maxBytes = intOf(options, "maxBytes", NodeBridgeConsoleTail.MAX_TEXT_BYTES)
        require(maxBytes in 1..NodeBridgeConsoleTail.MAX_TEXT_BYTES) { "maxBytes must be between 1 and ${NodeBridgeConsoleTail.MAX_TEXT_BYTES}, got $maxBytes" }
        val minLevel = if (options.has("level") && !options.isNull("level")) {
            NodeBridgeConsoleTail.parseLevel(options.opt("level"))
                ?: throw IllegalArgumentException("level must be verbose, debug, info, warn, error, assert or a priority 2..7")
        } else {
            NodeBridgeConsoleTail.LEVEL_VERBOSE
        }
        val console = consoleProvider()
        val snapshot = console.snapshotLogEntries()
        val latestId = console.latestLogId
        val selection = NodeBridgeConsoleTail.select(
            entries = snapshot.map { it.toTailEntry() },
            lines = lines,
            sinceId = sinceId,
            minLevel = minLevel,
            maxBytes = maxBytes,
        )
        val json = JSONObject()
            .put("schema", SCHEMA_TAIL)
            .put("lines", lines)
            .put("sinceId", sinceId)
            .put("minLevel", minLevel)
            .put("minLevelName", NodeBridgeConsoleTail.levelName(minLevel))
            .put("count", selection.entries.size)
            .put("firstId", selection.firstId)
            .put("lastId", selection.lastId)
            .put("nextSinceId", if (selection.entries.isEmpty()) maxOf(sinceId, latestId) else selection.lastId)
            .put("latestId", latestId)
            .put("total", snapshot.size)
            .put("capacity", ConsoleImpl.MAX_LOG_ENTRIES)
            .put("bytes", selection.bytes)
            .put("truncated", selection.truncated)
            .put("evicted", selection.evicted)
            .put("entries", entriesJson(selection.entries))
        return NodeBridgeResponse.success(request.id, json.toString())
    }

    private fun optionsArg(args: JSONArray, index: Int, keys: Set<String>): JSONObject {
        val value = args.opt(index)
        if (value == null || value == JSONObject.NULL) return JSONObject()
        val options = value as? JSONObject ?: throw IllegalArgumentException("options must be an object")
        val iterator = options.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            require(key in keys) { "unknown option \"$key\"; expected one of ${keys.joinToString(", ")}" }
        }
        return options
    }

    private fun intOf(options: JSONObject, key: String, default: Int): Int {
        if (!options.has(key) || options.isNull(key)) return default
        return (options.opt(key) as? Number)?.toInt() ?: throw IllegalArgumentException("$key must be a number")
    }

    companion object {

        const val SCHEMA_TAIL = "autojs6-bridge-console-tail-v1"
        const val CAPTURE_MODE_GLOBAL_WINDOW = "global-window"

        private val TAIL_KEYS = setOf("lines", "sinceId", "level", "maxBytes")

        /**
         * The console output that appeared while an execution ran, as an id window
         * `sinceId < id <= untilId` over the shared console (newest 500 lines / 256 KiB).
         * zh-CN: 某次执行期间出现的控制台输出, 即共享控制台上 `sinceId < id <= untilId` 的 id 窗口 (最新 500 行 / 256 KiB).
         */
        fun captureJson(console: ConsoleImpl, sinceId: Int, untilId: Int): JSONObject {
            val snapshot = console.snapshotLogEntries()
            val selection = NodeBridgeConsoleTail.select(
                entries = snapshot.map { it.toTailEntry() },
                lines = NodeBridgeConsoleTail.MAX_LINES,
                sinceId = sinceId,
                untilId = untilId,
            )
            return JSONObject()
                .put("captureMode", CAPTURE_MODE_GLOBAL_WINDOW)
                .put("sinceId", sinceId)
                .put("untilId", untilId)
                .put("count", selection.entries.size)
                .put("bytes", selection.bytes)
                .put("truncated", selection.truncated)
                .put("evicted", selection.evicted)
                .put("entries", entriesJson(selection.entries))
        }

        /**
         * `text` keeps the `HH:mm:ss.SSS/D: ` prefix the global console has always exposed on the
         * wire; the raw timestamp travels separately as `time`.
         * zh-CN: `text` 保留全局控制台一直对外暴露的 `HH:mm:ss.SSS/D: ` 前缀; 原始时间戳单独以 `time` 传递.
         */
        private fun ConsoleImpl.LogEntry.toTailEntry(): NodeBridgeConsoleTail.Entry {
            return NodeBridgeConsoleTail.Entry(id, level, ConsoleLogFormat.formatLine(timestamp, level, content), timestamp)
        }

        private fun entriesJson(entries: List<NodeBridgeConsoleTail.Entry>): JSONArray {
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("level", entry.level)
                        .put("levelName", NodeBridgeConsoleTail.levelName(entry.level))
                        .put("time", entry.timestamp)
                        .put("text", entry.text),
                )
            }
            return array
        }
    }
}
