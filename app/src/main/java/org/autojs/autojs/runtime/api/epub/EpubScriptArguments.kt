package org.autojs.autojs.runtime.api.epub

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.epub.EpubErrorMapper
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import kotlin.math.floor

/**
 * Argument normalization of the `epub` script API (plugin roadmap P6.1, appendix A.2 and A.3):
 * the script values arrive as Gson trees (the Rhino layer serializes each argument like
 * `JSON.stringify`, so functions and `undefined` are already gone) and leave as the request
 * shapes of the host client. Unknown option keys are refused by name before anything reaches the
 * plugin; every refusal is an `INVALID_ARGUMENT`. Nothing here touches Rhino or Android, so the
 * JVM tests cover it.
 * zh-CN: `epub` 脚本 API 的参数规范化 (插件路线图 P6.1, 附录 A.2 与 A.3): 脚本值以 Gson 树到达 (Rhino 层像 `JSON.stringify` 一样序列化每个参数,
 * 函数与 `undefined` 已消失), 离开时是宿主客户端的请求形态. 未知选项键在到达插件前按名拒绝; 每个拒绝都是 `INVALID_ARGUMENT`. 这里不触碰 Rhino
 * 与 Android, 因此由 JVM 测试覆盖.
 */
internal object EpubScriptArguments {

    /** `epub.open(path | { path, displayName? })`. zh-CN: `epub.open(path | { path, displayName? })`. */
    class Open(val path: String, val displayName: String?)

    /**
     * A text request: one reading-order resource by [href] or [index], or the whole book when
     * both are null; [maxChars] caps the characters collected over all chunks.
     * zh-CN: 文本请求: 按 [href] 或 [index] 取一个阅读顺序资源, 二者皆空时为全书; [maxChars] 限制所有分块合计的字符数.
     */
    class Text(val href: String?, val index: Int?, val offset: Int, val maxChars: Int, val format: String?) {
        val wholeBook: Boolean
            get() = href == null && index == null
    }

    class Search(val query: String, val offset: Int, val limit: Int)

    /** Options of the file exports (`cover(outputPath)`, `resource(href, outputPath)`). zh-CN: 文件导出 (`cover(outputPath)`, `resource(href, outputPath)`) 的选项. */
    class Export(val overwrite: Boolean)

    const val PATH = "path"
    const val DISPLAY_NAME = "displayName"
    const val HREF = "href"
    const val OFFSET = "offset"
    const val MAX_CHARS = "maxChars"
    const val FORMAT = "format"
    const val LIMIT = "limit"
    const val OVERWRITE = "overwrite"

    /** Default cap of `text()` / `textAll()` (appendix A.2: 4 MiB). zh-CN: `text()` / `textAll()` 的默认上限 (附录 A.2: 4 MiB). */
    const val DEFAULT_MAX_CHARS = 4 * 1024 * 1024

    val FORMATS: Set<String> = setOf(EpubContract.FORMAT_TEXT, EpubContract.FORMAT_MARKDOWN)

    private val OPEN_KEYS = setOf(PATH, DISPLAY_NAME)
    private val TEXT_KEYS = setOf(OFFSET, MAX_CHARS, FORMAT)
    private val SEARCH_KEYS = setOf(OFFSET, LIMIT)
    private val EXPORT_KEYS = setOf(OVERWRITE)

    /** `content://`, `file://`, `http://` and friends: the script side has no authorization model for them (appendix A.1). zh-CN: `content://`, `file://`, `http://` 等: 脚本侧没有针对它们的授权模型 (附录 A.1). */
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]+:")

    fun open(value: JsonElement?): Open {
        if (value == null || value.isJsonNull) throw invalid("epub.open expects a file path or an options object with 'path'")
        if (value.isJsonPrimitive) return Open(path(value, "path"), null)
        val options = obj(value, "options")
        options.rejectUnknown("options", OPEN_KEYS)
        return Open(path(options.get(PATH), "options.path"), options.stringOrNull("options", DISPLAY_NAME)?.takeIf { it.isNotBlank() })
    }

    /** A script path: a non-blank string that is not a URI; the caller applies the working-directory rules. zh-CN: 脚本路径: 非空白且不是 URI 的字符串; 工作目录规则由调用方应用. */
    fun path(value: JsonElement?, label: String): String {
        val text = string(value, label)
        if (text.isBlank()) throw invalid("'$label' must not be blank")
        if (URI_SCHEME.containsMatchIn(text)) {
            throw invalid("'$label' must be a file path (relative to the script directory or absolute); URIs such as content:// are not accepted, got $text")
        }
        return text
    }

    /**
     * `text(target?, options?)`: [target] is an href string, a reading-order index, an object
     * carrying `href` (a table-of-contents entry, a reading-order link or a locator) or nothing
     * for the whole book; [options] are `offset` (single resource only), `maxChars` and `format`.
     * zh-CN: `text(target?, options?)`: [target] 为 href 字符串, 阅读顺序索引, 带 `href` 的对象 (目录项, 阅读顺序链接或 locator) 或省略 (全书);
     * [options] 为 `offset` (仅单个资源), `maxChars` 与 `format`.
     */
    fun text(target: JsonElement?, options: JsonElement?): Text {
        var href: String? = null
        var index: Int? = null
        when {
            target == null || target.isJsonNull -> Unit
            target.isJsonPrimitive && target.asJsonPrimitive.isString -> href = href(target, "target")
            target.isJsonPrimitive && target.asJsonPrimitive.isNumber -> {
                index = int(target, "target")
                if (index < 0) throw invalid("'target' index must be a non-negative integer, got $index")
            }
            target.isJsonObject -> href = href(target.asJsonObject.get(HREF), "target.href")
            else -> throw invalid("'target' must be an href string, a reading-order index or an object with 'href', got ${brief(target)}")
        }
        val given = optionalObject(options, "options")
        given?.rejectUnknown("options", TEXT_KEYS) { key -> if (key == LIMIT) " (use 'options.maxChars')" else "" }
        val offset = given?.intOrNull("options", OFFSET) ?: 0
        if (offset < 0) throw invalid("'options.offset' must be a non-negative integer, got $offset")
        if (offset > 0 && href == null && index == null) throw invalid("'options.offset' applies to a single resource target, not to the whole book")
        val maxChars = given?.intOrNull("options", MAX_CHARS) ?: DEFAULT_MAX_CHARS
        if (maxChars < 1) throw invalid("'options.maxChars' must be a positive integer, got $maxChars")
        val format = given?.stringOrNull("options", FORMAT)
        if (format != null && format !in FORMATS) throw invalid("'options.format' must be one of ${FORMATS.joinToString()}, got $format")
        return Text(href, index, offset, maxChars, format)
    }

    /** `textAll(options?)`: the whole book with `maxChars` and `format`. zh-CN: `textAll(options?)`: 带 `maxChars` 与 `format` 的全书. */
    fun textAll(options: JsonElement?): Text = text(null, options)

    /** A publication href: a non-blank string within the contract's length ceiling. zh-CN: 出版物 href: 非空白且在契约长度上限内的字符串. */
    fun href(value: JsonElement?, label: String): String {
        val text = string(value, label)
        if (text.isBlank()) throw invalid("'$label' must not be blank")
        if (text.length > EpubContract.MAX_HREF_LENGTH) throw invalid("'$label' exceeds ${EpubContract.MAX_HREF_LENGTH} characters")
        return text
    }

    /**
     * `search(query, options?)`: the query is trimmed with inner whitespace collapsed (as the
     * plugin does) and must stay within the contract's length range; `offset` and `limit` page
     * within the ceiling of `MAX_SEARCH_RESULTS` hits, `limit` defaulting to the contract's
     * `DEFAULT_SEARCH_LIMIT` (plugin roadmap P6 deviation from appendix A.2's 100).
     * zh-CN: `search(query, options?)`: 查询去首尾空白并折叠内部空白 (与插件相同), 须在契约长度范围内; `offset` 与 `limit` 在 `MAX_SEARCH_RESULTS`
     * 命中上限内分页, `limit` 默认为契约的 `DEFAULT_SEARCH_LIMIT` (插件路线图 P6 相对附录 A.2 的 100 的偏差).
     */
    fun search(query: JsonElement?, options: JsonElement?): Search {
        val text = string(query, "query").trim().replace(WHITESPACE, " ")
        if (text.length < EpubContract.MIN_QUERY_LENGTH) throw invalid("'query' must not be blank")
        if (text.length > EpubContract.MAX_QUERY_LENGTH) throw invalid("'query' exceeds ${EpubContract.MAX_QUERY_LENGTH} characters")
        val given = optionalObject(options, "options")
        given?.rejectUnknown("options", SEARCH_KEYS)
        val offset = given?.intOrNull("options", OFFSET) ?: 0
        if (offset < 0) throw invalid("'options.offset' must be a non-negative integer, got $offset")
        val limit = given?.intOrNull("options", LIMIT) ?: 0
        if (limit < 0 || limit > EpubContract.MAX_SEARCH_RESULTS) throw invalid("'options.limit' must be between 1 and ${EpubContract.MAX_SEARCH_RESULTS}, got $limit")
        if (given?.has(LIMIT) == true && limit == 0) throw invalid("'options.limit' must be between 1 and ${EpubContract.MAX_SEARCH_RESULTS}, got 0")
        val effective = if (limit == 0) EpubContract.DEFAULT_SEARCH_LIMIT else limit
        if (offset + effective > EpubContract.MAX_SEARCH_RESULTS) {
            throw invalid("'options.offset' + 'options.limit' must not exceed ${EpubContract.MAX_SEARCH_RESULTS} (a search yields at most that many hits), got ${offset + effective}")
        }
        return Search(text, offset, limit)
    }

    fun export(options: JsonElement?): Export {
        val given = optionalObject(options, "options")
        given?.rejectUnknown("options", EXPORT_KEYS)
        return Export(given?.booleanOrNull("options", OVERWRITE) ?: false)
    }

    /** An `INVALID_ARGUMENT` failure as the script sees it. zh-CN: 脚本眼中的 `INVALID_ARGUMENT` 失败. */
    fun invalid(message: String): EpubPluginException =
        EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.INVALID_ARGUMENT, message))

    // ------------------------------------------------------------------ helpers

    private val WHITESPACE = Regex("\\s+")

    private fun string(value: JsonElement?, label: String): String {
        if (value == null || value.isJsonNull) throw invalid("'$label' is required")
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw invalid("'$label' must be a string, got ${brief(value)}")
        return value.asString
    }

    private fun int(value: JsonElement, label: String): Int {
        val number = value.asJsonPrimitive.takeIf { it.isNumber }?.asDouble ?: throw invalid("'$label' must be an integer, got ${brief(value)}")
        if (number.isNaN() || number.isInfinite() || number != floor(number) || number < Int.MIN_VALUE || number > Int.MAX_VALUE) {
            throw invalid("'$label' must be an integer, got ${value.asJsonPrimitive.asString}")
        }
        return number.toInt()
    }

    private fun obj(value: JsonElement, label: String): JsonObject {
        if (!value.isJsonObject) throw invalid("'$label' must be an object, got ${brief(value)}")
        return value.asJsonObject
    }

    private fun optionalObject(value: JsonElement?, label: String): JsonObject? {
        if (value == null || value.isJsonNull) return null
        return obj(value, label)
    }

    private fun JsonObject.rejectUnknown(label: String, known: Set<String>, hint: (String) -> String = { "" }) {
        keySet().firstOrNull { it !in known }?.let { key -> throw invalid("unknown option '$label.$key'${hint(key)}") }
    }

    private fun JsonObject.stringOrNull(label: String, key: String): String? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw invalid("'$label.$key' must be a string, got ${brief(value)}")
        return value.asString
    }

    private fun JsonObject.intOrNull(label: String, key: String): Int? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive) throw invalid("'$label.$key' must be an integer, got ${brief(value)}")
        return int(value, "$label.$key")
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
