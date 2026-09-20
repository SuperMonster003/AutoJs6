package org.autojs.autojs.runtime.api.epub

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import org.autojs.plugin.epub.api.EpubContract

/**
 * Conversions between the JSON documents of the EPUB contract and the plain JVM values the
 * script layer hands to Rhino (plugin roadmap P6.1). A Gson tree becomes `LinkedHashMap` /
 * `List` / `String` / `Boolean` / `Int` / `Double` / `null`, which `RhinoUtils.toJsValue` turns
 * into script objects on the script thread; the flattened table of contents of the contract
 * (`depth` per entry) is rebuilt into the `{ title, href, children }` tree of appendix A.2.
 * Nothing here touches Rhino, so the JVM tests cover it.
 * zh-CN: EPUB 契约 JSON 文档与脚本层交给 Rhino 的普通 JVM 值之间的转换 (插件路线图 P6.1). Gson 树变为 `LinkedHashMap` / `List` / `String` /
 * `Boolean` / `Int` / `Double` / `null`, 由 `RhinoUtils.toJsValue` 在脚本线程上转成脚本对象; 契约的扁平目录 (每项带 `depth`) 在此重建为附录 A.2
 * 的 `{ title, href, children }` 树. 这里不触碰 Rhino, 因此由 JVM 测试覆盖.
 */
internal object EpubScriptValues {

    const val CHILDREN = "children"

    /** Parses [json] as a Gson tree; blank or invalid text becomes [JsonNull]. zh-CN: 把 [json] 解析为 Gson 树; 空白或非法文本成为 [JsonNull]. */
    fun parse(json: String?): JsonElement {
        if (json.isNullOrBlank()) return JsonNull.INSTANCE
        return try {
            JsonParser.parseString(json)
        } catch (_: JsonSyntaxException) {
            JsonNull.INSTANCE
        } catch (_: IllegalStateException) {
            JsonNull.INSTANCE
        }
    }

    /** The plain JVM value of [element]; objects keep their key order. zh-CN: [element] 的普通 JVM 值; 对象保持键序. */
    fun toPlain(element: JsonElement?): Any? = when {
        element == null || element.isJsonNull -> null
        element.isJsonObject -> LinkedHashMap<String, Any?>().also { map ->
            element.asJsonObject.entrySet().forEach { (key, value) -> map[key] = toPlain(value) }
        }
        element.isJsonArray -> element.asJsonArray.map(::toPlain)
        else -> primitive(element.asJsonPrimitive)
    }

    /** [toPlain] of the parsed [json]. zh-CN: 解析 [json] 后的 [toPlain]. */
    fun toPlain(json: String?): Any? = toPlain(parse(json))

    /** An object document as a map; anything else is an empty map. zh-CN: 对象文档为 map; 其它为一个空 map. */
    fun map(json: String?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return toPlain(json) as? Map<String, Any?> ?: LinkedHashMap()
    }

    /** An array document as a list; anything else is an empty list. zh-CN: 数组文档为 list; 其它为一个空 list. */
    fun list(json: String?): List<Any?> = toPlain(json) as? List<Any?> ?: emptyList()

    /**
     * The `{ title, href, children }` tree of a flattened table of contents: an entry becomes a
     * child of the nearest preceding entry with a smaller `depth`, so depth jumps (0 then 2) and
     * a first entry below depth 0 still produce a well-formed tree.
     * zh-CN: 扁平目录的 `{ title, href, children }` 树: 每项成为前面最近的 `depth` 更小者的子项, 因此深度跳跃 (0 之后是 2) 与首项不在 0 层
     * 也能得到良构的树.
     */
    fun tocTree(flat: List<Any?>): List<Map<String, Any?>> {
        val roots = ArrayList<Map<String, Any?>>()
        val stack = ArrayList<Pair<Int, MutableList<Map<String, Any?>>>>()
        for (entry in flat) {
            val source = entry as? Map<*, *> ?: continue
            val depth = (source[EpubContract.FIELD_DEPTH] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val children = ArrayList<Map<String, Any?>>()
            val node = LinkedHashMap<String, Any?>()
            node[EpubContract.FIELD_TITLE] = source[EpubContract.FIELD_TITLE]
            node[EpubContract.FIELD_HREF] = source[EpubContract.FIELD_HREF]
            node[CHILDREN] = children
            while (stack.isNotEmpty() && stack.last().first >= depth) stack.removeAt(stack.size - 1)
            if (stack.isEmpty()) roots += node else stack.last().second += node
            stack += depth to children
        }
        return roots
    }

    /** [tocTree] of the `toc` document. zh-CN: `toc` 文档的 [tocTree]. */
    fun tocTree(json: String?): List<Map<String, Any?>> = tocTree(list(json))

    private fun primitive(primitive: JsonPrimitive): Any = when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isNumber -> number(primitive)
        else -> primitive.asString
    }

    /** Integral values that fit a script integer stay `Int`; everything else is a `Double`. zh-CN: 能放进脚本整数的整值保持 `Int`; 其余为 `Double`. */
    private fun number(primitive: JsonPrimitive): Number {
        val text = primitive.asString
        val integral = text.toLongOrNull()
        if (integral != null && integral in Int.MIN_VALUE..Int.MAX_VALUE) return integral.toInt()
        return integral?.toDouble() ?: primitive.asDouble
    }
}
