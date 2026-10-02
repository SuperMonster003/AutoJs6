package org.autojs.autojs.runtime.api.terminal

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.core.plugin.terminal.TerminalErrorMapper
import org.autojs.autojs.core.plugin.terminal.TerminalJson
import org.autojs.autojs.core.plugin.terminal.TerminalPluginException
import org.autojs.autojs.core.plugin.terminal.TerminalPluginState
import org.autojs.plugin.terminal.api.TerminalContract
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import kotlin.math.floor

/**
 * Argument normalization of the `terminal` script API (plugin roadmap P4.1, appendix A.1 and A.2):
 * the script values arrive as Gson trees (the Rhino layer serializes each argument like
 * `JSON.stringify`, so functions and `undefined` are already gone) and leave as the open request
 * of the host client. Paths follow the working-directory rule of `files.path` ([resolvePath]) and
 * `content://` or any other URI is refused; option keys are refused by name when unknown; the
 * environment may not override `HOME`, `PREFIX` or `PATH`; every refusal is an `INVALID_ARGUMENT`.
 * Nothing here touches Rhino or Android, so the JVM tests cover it.
 * zh-CN: `terminal` 脚本 API 的参数规范化 (插件路线图 P4.1, 附录 A.1 与 A.2): 脚本值以 Gson 树到达 (Rhino 层像 `JSON.stringify` 一样序列化每个参数,
 * 函数与 `undefined` 已消失), 离开时是宿主客户端的打开请求. 路径遵循 `files.path` 的工作目录规则 ([resolvePath]), 拒绝 `content://` 等任何 URI;
 * 未知选项键按名拒绝; 环境不可覆盖 `HOME` / `PREFIX` / `PATH`; 每个拒绝都是 `INVALID_ARGUMENT`. 这里不触碰 Rhino 与 Android, 因此由 JVM 测试覆盖.
 */
internal object TerminalScriptArguments {

    /** `terminal.open(dir | options)` (appendix A.2). zh-CN: `terminal.open(dir | options)` (附录 A.2). */
    class Open(val cwd: String?, val env: Map<String, String>, val show: Boolean, val title: String?)

    /** `terminal.exec(command, options)` and `terminal.npm.run(script, dir, options)`. zh-CN: `terminal.exec(command, options)` 与 `terminal.npm.run(script, dir, options)`. */
    class Exec(val command: String, val open: Open, val keepOpen: Boolean, val wait: Boolean, val timeoutMillis: Long, val stripAnsi: Boolean)

    const val CWD = "cwd"
    const val ENV = "env"
    const val SHOW = "show"
    const val TITLE = "title"
    const val KEEP_OPEN = "keepOpen"
    const val WAIT = "wait"
    const val TIMEOUT = "timeout"
    const val STRIP_ANSI = "stripAnsi"

    /** Default of `exec({ wait: true })` (appendix A.2: 10 minutes); `0` means no limit. zh-CN: `exec({ wait: true })` 的默认超时 (附录 A.2: 10 分钟); `0` 表示不限. */
    const val DEFAULT_WAIT_TIMEOUT_MILLIS = 600_000L

    /** Variables the plugin owns; a script may not override them (appendix A.2). zh-CN: 插件拥有的变量; 脚本不可覆盖 (附录 A.2). */
    val PROTECTED_ENV_KEYS: Set<String> = setOf("HOME", "PREFIX", "PATH")

    private val OPEN_KEYS = setOf(CWD, ENV, SHOW, TITLE)
    private val EXEC_KEYS = OPEN_KEYS + setOf(KEEP_OPEN, WAIT, TIMEOUT, STRIP_ANSI)
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    /** npm script names the plugin's `sh` receives unquoted: no whitespace, quotes or shell operators. zh-CN: 插件的 `sh` 不加引号接收的 npm 脚本名: 不含空白, 引号或 shell 运算符. */
    private val NPM_SCRIPT_NAME = Regex("^[A-Za-z0-9_.:@/+-]+$")

    private val DEFAULT_OPEN = Open(cwd = null, env = emptyMap(), show = true, title = null)

    private const val NUL = '\u0000'
    private const val MAX_SESSION_ID_LENGTH = 256

    // ------------------------------------------------------------------ first tier

    /** `terminal.open()`, `terminal.open(dir)` or `terminal.open(options)`. zh-CN: `terminal.open()`, `terminal.open(dir)` 或 `terminal.open(options)`. */
    fun open(value: JsonElement?): Open {
        if (value == null || value.isJsonNull) return DEFAULT_OPEN
        if (value.isJsonPrimitive) return Open(path(value, "dir"), emptyMap(), show = true, title = null)
        return openOptions(obj(value, "options"), "options", OPEN_KEYS)
    }

    /** A session id argument: a non-blank string. zh-CN: 会话 id 参数: 非空白字符串. */
    fun sessionId(value: JsonElement?, label: String = "id"): String {
        val text = string(value, label)
        if (text.isBlank()) throw invalid("'$label' must not be blank")
        if (text.length > MAX_SESSION_ID_LENGTH) throw invalid("'$label' is not a session id")
        return text
    }

    /** Like [sessionId], or null when the script passed nothing. zh-CN: 同 [sessionId], 脚本未传时为 null. */
    fun optionalSessionId(value: JsonElement?, label: String = "id"): String? =
        if (value == null || value.isJsonNull) null else sessionId(value, label)

    // ------------------------------------------------------------------ second tier

    /** `terminal.exec(command, options?)`. zh-CN: `terminal.exec(command, options?)`. */
    fun exec(command: JsonElement?, options: JsonElement?): Exec = exec(commandText(command, "command"), optionalObject(options, "options"), "options")

    /** `terminal.npm.run(script, dir?, options?)`: `npm run <script>` inside [dir] (or `options.cwd`). zh-CN: `terminal.npm.run(script, dir?, options?)`: 在 [dir] (或 `options.cwd`) 里运行 `npm run <script>`. */
    fun npmRun(script: JsonElement?, dir: JsonElement?, options: JsonElement?): Exec {
        val name = string(script, "script")
        if (name.isBlank()) throw invalid("'script' must not be blank")
        if (!NPM_SCRIPT_NAME.matches(name)) throw invalid("'script' must be an npm script name without spaces, quotes or shell operators, got \"${name.take(40)}\"")
        val directory = if (dir == null || dir.isJsonNull) null else path(dir, "dir")
        val parsed = exec("npm run $name", optionalObject(options, "options"), "options")
        if (directory == null) return parsed
        if (parsed.open.cwd != null && parsed.open.cwd != directory) throw invalid("'dir' and 'options.cwd' disagree; pass one of them")
        return Exec(parsed.command, Open(directory, parsed.open.env, parsed.open.show, parsed.open.title), parsed.keepOpen, parsed.wait, parsed.timeoutMillis, parsed.stripAnsi)
    }

    /** `session.write(text | bytes)`: UTF-8 text or an array of byte values, at most `MAX_INPUT_BYTES`. zh-CN: `session.write(text | bytes)`: UTF-8 文本或字节值数组, 至多 `MAX_INPUT_BYTES`. */
    fun input(value: JsonElement?): ByteArray {
        if (value == null || value.isJsonNull) throw invalid("'data' is required: a string or an array of byte values")
        val bytes = when {
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.toByteArray(Charsets.UTF_8)
            value.isJsonArray -> ByteArray(value.asJsonArray.size()) { index ->
                val element = value.asJsonArray[index]
                val number = if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) element.asDouble else Double.NaN
                if (number.isNaN() || number != floor(number) || number < 0 || number > 255) throw invalid("'data[$index]' must be a byte value 0..255, got ${brief(element)}")
                number.toInt().toByte()
            }
            else -> throw invalid("'data' must be a string or an array of byte values, got ${brief(value)}")
        }
        if (bytes.isEmpty()) throw invalid("'data' must not be empty")
        if (bytes.size > TerminalContract.MAX_INPUT_BYTES) throw invalid("'data' is longer than ${TerminalContract.MAX_INPUT_BYTES} bytes")
        return bytes
    }

    // ------------------------------------------------------------------ paths and requests

    /** A script path: a non-blank string that is not a URI; [resolvePath] applies the working-directory rule. zh-CN: 脚本路径: 非空白且不是 URI 的字符串; [resolvePath] 应用工作目录规则. */
    fun path(value: JsonElement?, label: String): String {
        val text = string(value, label)
        if (text.isBlank()) throw invalid("'$label' must not be blank")
        if (NUL in text) throw invalid("'$label' must not contain NUL characters")
        if (URI_SCHEME.containsMatchIn(text)) {
            throw invalid("'$label' must be a directory path (relative to the script directory or absolute); URIs such as content:// are not accepted, got $text")
        }
        return text
    }

    /**
     * The rule of `files.path`: an absolute path passes through, a relative one is resolved
     * against the script's working directory [cwd] segment by segment (`.` is skipped, `..` goes
     * up, a trailing `/` is kept). A relative path without a working directory is refused.
     * zh-CN: `files.path` 的规则: 绝对路径原样通过, 相对路径逐段相对脚本工作目录 [cwd] 解析 (`.` 跳过, `..` 上行, 保留结尾 `/`). 没有工作目录时拒绝相对路径.
     */
    fun resolvePath(path: String, cwd: String?, label: String = CWD): String {
        if (path.startsWith("/")) return path
        if (cwd.isNullOrBlank()) throw invalid("'$label' is relative but the script has no working directory; pass an absolute path, got $path")
        var current = cwd.trimEnd('/').ifEmpty { "/" }
        path.split('/').forEach { segment ->
            when {
                segment == ".." -> {
                    if (current == "/") throw invalid("'$label' leaves the root directory, got $path")
                    current = current.substringBeforeLast('/', "").ifEmpty { "/" }
                }
                segment != "." && segment.isNotBlank() -> current = if (current == "/") "/$segment" else "$current/$segment"
            }
        }
        return if (path.endsWith("/") && current != "/") "$current/" else current
    }

    /**
     * The validated open request for [open] in a script whose working directory is [scriptCwd]:
     * `cwd` defaults to that directory (the plugin's home when a script has none), [command] and
     * [keepOpen] come from `exec`.
     * zh-CN: 工作目录为 [scriptCwd] 的脚本里 [open] 的已校验打开请求: `cwd` 默认该目录 (脚本没有时为插件主目录), [command] 与 [keepOpen] 来自 `exec`.
     */
    fun request(open: Open, scriptCwd: String?, command: String? = null, keepOpen: Boolean = true): TerminalJson.OpenRequest {
        val cwd = when {
            open.cwd != null -> resolvePath(open.cwd, scriptCwd)
            !scriptCwd.isNullOrBlank() -> scriptCwd
            else -> null
        }
        val request = TerminalJson.OpenRequest(cwd = cwd, command = command, env = open.env, title = open.title, keepOpen = keepOpen)
        request.validate()?.let { throw invalid(it) }
        return request
    }

    /** The five-state name of `terminal.state()` (appendix A.1). zh-CN: `terminal.state()` 的五态名称 (附录 A.1). */
    fun stateName(state: TerminalPluginState): String = state.name.lowercase()

    // ------------------------------------------------------------------ third tier (P4.2)

    /** `waitFor(pattern, timeout?)`: milliseconds, `0` for no limit, default [DEFAULT_WAIT_TIMEOUT_MILLIS]. zh-CN: `waitFor(pattern, timeout?)`: 毫秒, `0` 表示不限, 默认 [DEFAULT_WAIT_TIMEOUT_MILLIS]. */
    fun waitTimeout(value: JsonElement?, label: String = TIMEOUT): Long =
        if (value == null || value.isJsonNull) DEFAULT_WAIT_TIMEOUT_MILLIS else millis(value, label)

    /** `transcript(maxBytes?)`: `1..MAX_TRANSCRIPT_BYTES`, default the maximum. zh-CN: `transcript(maxBytes?)`: `1..MAX_TRANSCRIPT_BYTES`, 默认取最大值. */
    fun transcriptMaxBytes(value: JsonElement?, label: String = "maxBytes"): Int {
        if (value == null || value.isJsonNull) return TerminalContract.MAX_TRANSCRIPT_BYTES
        val number = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            ?: throw invalid("'$label' must be a number of bytes, got ${brief(value)}")
        if (number.isNaN() || number != floor(number) || number < 1 || number > TerminalContract.MAX_TRANSCRIPT_BYTES) {
            throw invalid("'$label' must be an integer within 1..${TerminalContract.MAX_TRANSCRIPT_BYTES}, got ${value.asJsonPrimitive.asString}")
        }
        return number.toInt()
    }

    /**
     * The line test of `waitFor`: [regexLiteral] is a JavaScript RegExp as `RegExp.toString()`
     * renders it (`/.../flags`, compiled like the selector API does), otherwise [text] is a
     * non-empty substring.
     * zh-CN: `waitFor` 的行判定: [regexLiteral] 是 `RegExp.toString()` 形态的 JavaScript 正则 (`/.../flags`, 像选择器 API 一样编译), 否则 [text] 为非空子串.
     */
    fun lineMatcher(regexLiteral: String?, text: String?, label: String = "pattern"): (String) -> Boolean {
        if (regexLiteral != null) {
            val regex = try {
                JsRegexUtils.compileJsOrPlainRegex(regexLiteral)
            } catch (e: RuntimeException) {
                throw invalid("'$label' is not a valid regular expression: ${e.message.orEmpty()}")
            }
            return { line -> regex.containsMatchIn(line) }
        }
        if (text == null) throw invalid("'$label' must be a string or a RegExp")
        if (text.isEmpty()) throw invalid("'$label' must not be empty")
        return { line -> line.contains(text) }
    }

    fun invalid(message: String): TerminalPluginException =
        TerminalPluginException(TerminalErrorMapper.normalize(TerminalErrorCodes.INVALID_ARGUMENT, message))

    // ------------------------------------------------------------------ internals

    private fun exec(command: String, options: JsonObject?, label: String): Exec {
        val open = if (options == null) DEFAULT_OPEN else openOptions(options, label, EXEC_KEYS)
        val keepOpen = options?.booleanOrNull(label, KEEP_OPEN) ?: true
        val wait = options?.booleanOrNull(label, WAIT) ?: false
        val timeout = options?.get(TIMEOUT)?.takeUnless { it.isJsonNull }?.let { millis(it, "$label.$TIMEOUT") } ?: DEFAULT_WAIT_TIMEOUT_MILLIS
        val stripAnsi = options?.booleanOrNull(label, STRIP_ANSI) ?: true
        return Exec(command, open, keepOpen, wait, timeout, stripAnsi)
    }

    private fun commandText(value: JsonElement?, label: String): String {
        val text = string(value, label)
        if (text.isBlank()) throw invalid("'$label' must not be blank")
        if (NUL in text) throw invalid("'$label' must not contain NUL characters")
        if (TerminalJson.utf8Size(text) > TerminalContract.MAX_COMMAND_BYTES) throw invalid("'$label' is longer than ${TerminalContract.MAX_COMMAND_BYTES} bytes")
        return text
    }

    private fun openOptions(options: JsonObject, label: String, known: Set<String>): Open {
        options.rejectUnknown(label, known)
        val cwd = options.get(CWD)?.takeUnless { it.isJsonNull }?.let { path(it, "$label.$CWD") }
        val env = options.get(ENV)?.takeUnless { it.isJsonNull }?.let { env(it, "$label.$ENV") } ?: emptyMap()
        val show = options.booleanOrNull(label, SHOW) ?: true
        val title = options.stringOrNull(label, TITLE)?.also { if (it.isBlank()) throw invalid("'$label.$TITLE' must not be blank") }
        return Open(cwd, env, show, title)
    }

    private fun env(value: JsonElement, label: String): Map<String, String> {
        val entries = obj(value, label)
        if (entries.size() > TerminalContract.MAX_ENV_ENTRIES) throw invalid("'$label' has more than ${TerminalContract.MAX_ENV_ENTRIES} entries")
        return entries.entrySet().associate { (key, element) ->
            if (key.isBlank() || '=' in key || NUL in key) throw invalid("'$label' has an invalid variable name \"${key.take(40)}\"")
            if (key in PROTECTED_ENV_KEYS) throw invalid("'$label.$key' cannot be overridden; the plugin owns ${PROTECTED_ENV_KEYS.joinToString(", ")}")
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) throw invalid("'$label.$key' must be a string, got ${brief(element)}")
            val text = element.asString
            if (NUL in text) throw invalid("'$label.$key' must not contain NUL characters")
            if (TerminalJson.utf8Size(key) + 1 + TerminalJson.utf8Size(text) > TerminalContract.MAX_ENV_ENTRY_BYTES) {
                throw invalid("'$label.$key' is longer than ${TerminalContract.MAX_ENV_ENTRY_BYTES} bytes")
            }
            key to text
        }
    }

    private fun millis(value: JsonElement, label: String): Long {
        val number = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: throw invalid("'$label' must be a non-negative number of milliseconds, got ${brief(value)}")
        if (number.isNaN() || number.isInfinite() || number < 0 || number != floor(number) || number > Long.MAX_VALUE.toDouble()) {
            throw invalid("'$label' must be a non-negative integer number of milliseconds (0 for no limit), got ${value.asJsonPrimitive.asString}")
        }
        return number.toLong()
    }

    private fun string(value: JsonElement?, label: String): String {
        if (value == null || value.isJsonNull) throw invalid("'$label' is required")
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw invalid("'$label' must be a string, got ${brief(value)}")
        return value.asString
    }

    private fun obj(value: JsonElement, label: String): JsonObject {
        if (!value.isJsonObject) throw invalid("'$label' must be an object, got ${brief(value)}")
        return value.asJsonObject
    }

    private fun optionalObject(value: JsonElement?, label: String): JsonObject? {
        if (value == null || value.isJsonNull) return null
        return obj(value, label)
    }

    private fun JsonObject.rejectUnknown(label: String, known: Set<String>) {
        keySet().firstOrNull { it !in known }?.let { key -> throw invalid("unknown option '$label.$key'; known options are ${known.sorted().joinToString(", ")}") }
    }

    private fun JsonObject.stringOrNull(label: String, key: String): String? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw invalid("'$label.$key' must be a string, got ${brief(value)}")
        return value.asString
    }

    private fun JsonObject.booleanOrNull(label: String, key: String): Boolean? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) throw invalid("'$label.$key' must be a boolean, got ${brief(value)}")
        return value.asBoolean
    }

    private fun brief(value: JsonElement): String = when {
        value.isJsonNull -> "null"
        value.isJsonArray -> "an array"
        value.isJsonObject -> "an object"
        value.asJsonPrimitive.isString -> "\"${value.asString.take(40)}\""
        else -> value.asJsonPrimitive.asString
    }
}
