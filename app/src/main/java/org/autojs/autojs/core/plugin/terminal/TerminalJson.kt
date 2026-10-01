package org.autojs.autojs.core.plugin.terminal

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import java.nio.charset.StandardCharsets

/**
 * Host-side codec of the JSON documents carried by the terminal contract (plugin roadmap P1.2,
 * appendix B.2; protocol document `docs/dev/terminal-plugin-protocol-v1.md` lands in P1.4). Pure
 * Kotlin on top of Gson, so the JVM tests cover it without Android. Documents coming from the
 * plugin are bounded by `TerminalContract.MAX_JSON_BYTES` and malformed ones surface as
 * `INTERNAL` errors instead of parser exceptions.
 */
object TerminalJson {

    /** One session as `listSessions` / `openSession` / `onSessionsChanged` describe it. */
    data class Session(
        val id: String,
        val cwd: String,
        val title: String,
        val createdAt: Long,
        val alive: Boolean,
        val exitCode: Int?,
        val pid: Int,
        val state: String,
        val json: String,
    ) {
        val isPending: Boolean get() = state == TerminalContract.STATE_PENDING
        val isExited: Boolean get() = TerminalContract.isTerminalState(state)
    }

    /** The `openSession` request; [validate] enforces the contract ceilings before encoding. */
    data class OpenRequest(
        val cwd: String? = null,
        val command: String? = null,
        val env: Map<String, String> = emptyMap(),
        val title: String? = null,
        val keepOpen: Boolean = true,
    ) {
        fun validate(): String? {
            if (cwd != null && (cwd.isBlank() || utf8Size(cwd) > TerminalContract.MAX_PATH_BYTES)) return "cwd is blank or longer than ${TerminalContract.MAX_PATH_BYTES} bytes"
            if (command != null && utf8Size(command) > TerminalContract.MAX_COMMAND_BYTES) return "command is longer than ${TerminalContract.MAX_COMMAND_BYTES} bytes"
            if (env.size > TerminalContract.MAX_ENV_ENTRIES) return "more than ${TerminalContract.MAX_ENV_ENTRIES} env entries"
            env.entries.firstOrNull { (key, value) -> key.isBlank() || '=' in key || utf8Size(key) + 1 + utf8Size(value) > TerminalContract.MAX_ENV_ENTRY_BYTES }
                ?.let { (key, _) -> return "env entry ${key.ifBlank { "<blank>" }} is invalid or longer than ${TerminalContract.MAX_ENV_ENTRY_BYTES} bytes" }
            if (title != null && title.isBlank()) return "title is blank"
            return null
        }
    }

    /** The Node.js part of the environment document. */
    data class NodeInfo(
        val available: Boolean,
        val version: String?,
        val packageName: String?,
        val pluginVersion: String?,
        val reason: String?,
    )

    /** The `getEnvironment` document. */
    data class Environment(
        val home: String?,
        val prefix: String?,
        val shell: String?,
        val node: NodeInfo,
        val npmRegistry: String?,
        val storageAccess: String?,
        val pluginVersion: String?,
        val contractVersion: Int,
        val json: String,
    )

    fun encodeOpenRequest(request: OpenRequest): String {
        request.validate()?.let { throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, it)) }
        val root = JsonObject()
        request.cwd?.let { root.addProperty(TerminalContract.FIELD_CWD, it) }
        request.command?.let { root.addProperty(TerminalContract.FIELD_COMMAND, it) }
        if (request.env.isNotEmpty()) {
            root.add(TerminalContract.FIELD_ENV, JsonObject().apply { request.env.forEach { (key, value) -> addProperty(key, value) } })
        }
        request.title?.let { root.addProperty(TerminalContract.FIELD_TITLE, it) }
        if (!request.keepOpen) root.addProperty(TerminalContract.FIELD_KEEP_OPEN, false)
        val json = root.toString()
        if (utf8Size(json) > TerminalContract.MAX_JSON_BYTES) {
            throw TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, "request document is longer than ${TerminalContract.MAX_JSON_BYTES} bytes"))
        }
        return json
    }

    fun encodeSubscribeOptions(fromStart: Boolean): String = JsonObject().apply {
        if (fromStart) addProperty(TerminalContract.FIELD_FROM_START, true)
    }.toString()

    /** The error document a Bundle or String answer carries, or null for `null`, blank or code-less text. */
    fun decodeError(json: String?): TerminalError? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val code = root.string(TerminalContract.FIELD_ERROR_CODE) ?: return null
        return TerminalErrorMapper.fromPlugin(code, root.string(TerminalContract.FIELD_ERROR_MESSAGE), root.string(TerminalContract.FIELD_ERROR_SESSION_ID))
    }

    /** A session document, or the error document the plugin answered instead (thrown as [TerminalPluginException]). */
    fun decodeSession(json: String?): Session {
        val root = parseObject(json, "session")
        decodeError(json)?.let { throw TerminalPluginException(it) }
        return session(root, json!!)
    }

    /** The array document of `listSessions` / `onSessionsChanged`; an error document is thrown. */
    fun decodeSessions(json: String?): List<Session> {
        val element = parse(json, "session list")
        if (element.isJsonObject) {
            decodeError(json)?.let { throw TerminalPluginException(it) }
            throw malformed("session list is not an array")
        }
        if (!element.isJsonArray) throw malformed("session list is not an array")
        return element.asJsonArray.map { item ->
            if (!item.isJsonObject) throw malformed("session list item is not an object")
            session(item.asJsonObject, item.toString())
        }
    }

    fun decodeEnvironment(json: String?): Environment {
        val root = parseObject(json, "environment")
        decodeError(json)?.let { throw TerminalPluginException(it) }
        val node = root.getAsJsonObjectOrNull(TerminalContract.FIELD_NODE)
        val contractVersion = root.int(TerminalContract.FIELD_CONTRACT_VERSION) ?: throw malformed("environment has no contractVersion")
        return Environment(
            home = root.string(TerminalContract.FIELD_HOME),
            prefix = root.string(TerminalContract.FIELD_PREFIX),
            shell = root.string(TerminalContract.FIELD_SHELL),
            node = NodeInfo(
                available = node?.boolean(TerminalContract.FIELD_NODE_AVAILABLE) ?: false,
                version = node?.string(TerminalContract.FIELD_NODE_VERSION),
                packageName = node?.string(TerminalContract.FIELD_NODE_PACKAGE_NAME),
                pluginVersion = node?.string(TerminalContract.FIELD_NODE_PLUGIN_VERSION),
                reason = node?.string(TerminalContract.FIELD_NODE_REASON),
            ),
            npmRegistry = root.string(TerminalContract.FIELD_NPM_REGISTRY),
            storageAccess = root.string(TerminalContract.FIELD_STORAGE_ACCESS),
            pluginVersion = root.string(TerminalContract.FIELD_PLUGIN_VERSION),
            contractVersion = contractVersion,
            json = json!!,
        )
    }

    /** Encodes sessions the way the plugin does; the host uses it for its own tests and the script layer. */
    fun encodeSessions(sessions: List<Session>): String = JsonArray().apply {
        sessions.forEach { add(JsonParser.parseString(it.json)) }
    }.toString()

    fun utf8Size(text: String): Int = text.toByteArray(StandardCharsets.UTF_8).size

    private fun session(root: JsonObject, json: String): Session {
        val state = root.string(TerminalContract.FIELD_STATE) ?: throw malformed("session has no state")
        if (!TerminalContract.isState(state)) throw malformed("unknown session state $state")
        return Session(
            id = root.string(TerminalContract.FIELD_ID)?.takeIf { it.isNotBlank() } ?: throw malformed("session has no id"),
            cwd = root.string(TerminalContract.FIELD_CWD).orEmpty(),
            title = root.string(TerminalContract.FIELD_TITLE).orEmpty(),
            createdAt = root.long(TerminalContract.FIELD_CREATED_AT) ?: 0L,
            alive = root.boolean(TerminalContract.FIELD_ALIVE) ?: (state != TerminalContract.STATE_EXITED),
            exitCode = root.int(TerminalContract.FIELD_EXIT_CODE),
            pid = root.int(TerminalContract.FIELD_PID) ?: -1,
            state = state,
            json = json,
        )
    }

    private fun parse(json: String?, what: String): JsonElement {
        if (json.isNullOrBlank()) throw malformed("$what document is empty")
        if (utf8Size(json) > TerminalContract.MAX_JSON_BYTES) throw malformed("$what document is longer than ${TerminalContract.MAX_JSON_BYTES} bytes")
        return runCatching { JsonParser.parseString(json) }.getOrElse { throw malformed("$what document is not JSON: ${it.message}") }
    }

    private fun parseObject(json: String?, what: String): JsonObject {
        val element = parse(json, what)
        if (!element.isJsonObject) throw malformed("$what document is not an object")
        return element.asJsonObject
    }

    private fun malformed(detail: String) = TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INTERNAL, "malformed plugin answer: $detail"))

    private fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.boolean(name: String): Boolean? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

    private fun JsonObject.int(name: String): Int? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    private fun JsonObject.long(name: String): Long? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
}
