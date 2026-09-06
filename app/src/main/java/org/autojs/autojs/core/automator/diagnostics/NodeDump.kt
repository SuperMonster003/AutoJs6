package org.autojs.autojs.core.automator.diagnostics

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.toolkit.UiNodes

/**
 * The output form of a node dump: indented text lines, pretty-printed JSON, or the XML of
 * `uiautomator dump`.
 * zh-CN: 节点快照的输出形式: 缩进文本行, 带缩进的 JSON, 或 `uiautomator dump` 风格的 XML.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
enum class DumpFormat(val key: String) {

    TEXT("text"),
    JSON("json"),
    XML("xml");

    companion object {

        fun parse(key: String): DumpFormat = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown dump format \"$key\"; expected one of ${entries.joinToString(", ") { "\"${it.key}\"" }}")

    }

}

/**
 * What a dump includes: the form, the depth limit under the root (`0` = the root only,
 * [UNLIMITED] = everything), the properties printed per node (`null` = the form's default set,
 * see [NodeDump.defaultProperties]), whether nodes not visible to the user are skipped together
 * with their subtrees, the node budget of the walk, and the indent unit.
 * zh-CN: 快照包含什么: 形式, 根节点之下的深度上限 (`0` = 仅根节点, [UNLIMITED] = 全部), 每个节点打印的属性 (`null` = 该形式的默认集合,
 * 见 [NodeDump.defaultProperties]), 是否连同子树跳过用户不可见的节点, 遍历的节点预算, 以及缩进单位.
 */
class DumpOptions(
    val format: DumpFormat = DumpFormat.TEXT,
    val maxDepth: Int = UNLIMITED,
    val properties: List<String>? = null,
    val visibleOnly: Boolean = false,
    val maxNodes: Int = UiNodes.DEFAULT_MAX_NODES,
    val indent: String = "  ",
) {

    init {
        require(maxDepth >= 0 || maxDepth == UNLIMITED) { "maxDepth must be >= 0 (or $UNLIMITED for unlimited), got $maxDepth" }
        require(maxNodes > 0) { "maxNodes must be > 0, got $maxNodes" }
        properties?.forEach(NodeDump::requireProperty)
    }

    /** The property names this dump prints. zh-CN: 本快照打印的属性名. */
    val propertyNames: List<String> get() = properties ?: NodeDump.defaultProperties(format)

    companion object {

        const val UNLIMITED = -1

    }

}

/**
 * A readable snapshot of a node tree, for `auto.dump(options)` and `UiObject.dumpSubtree()`:
 * every node in pre-order with the chosen properties, as indented text (one line per node, the
 * simple class name, `[l,t][r,b]` bounds, quoted strings, the boolean properties that are set),
 * as JSON (one object per node, `children` nested) or as the XML `uiautomator dump` writes
 * (`<hierarchy>` / `<node index="" text="" resource-id="" class="" ... bounds="" />`).
 * The walk never recycles a node.
 * zh-CN: 节点树的可读快照, 供 `auto.dump(options)` 与 `UiObject.dumpSubtree()`: 先序的每个节点带所选属性, 形式为缩进文本
 * (每节点一行: 简单类名, `[l,t][r,b]` 边界, 加引号的字符串, 已置位的布尔属性), JSON (每节点一个对象, `children` 嵌套) 或
 * `uiautomator dump` 写出的那种 XML (`<hierarchy>` / `<node index="" text="" resource-id="" class="" ... bounds="" />`).
 * 遍历从不回收节点.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
object NodeDump {

    /** Every property a dump can print, named after the `UiObject` methods. zh-CN: 快照可打印的全部属性, 以 `UiObject` 的方法命名. */
    val PROPERTIES: List<String> = listOf(
        "className", "id", "idHex", "text", "desc", "bounds", "packageName", "indexInParent", "depth", "childCount", "drawingOrder",
        "clickable", "longClickable", "scrollable", "checkable", "checked", "enabled", "focusable", "focused", "selected", "editable",
        "visibleToUser", "password", "dismissable", "contextClickable", "accessibilityFocused", "multiLine", "contentInvalid",
        "row", "column", "rowSpan", "columnSpan", "rowCount", "columnCount", "actions",
    )

    /** The text form's default properties. zh-CN: 文本形式的默认属性. */
    val DEFAULT_TEXT: List<String> = listOf(
        "className", "bounds", "id", "text", "desc",
        "clickable", "longClickable", "scrollable", "checkable", "checked", "enabled", "focusable", "focused", "selected", "editable", "visibleToUser",
    )

    /** The JSON form's default properties, also what `UiObject.toJSON()` carries. zh-CN: JSON 形式的默认属性, 也是 `UiObject.toJSON()` 携带的内容. */
    val DEFAULT_JSON: List<String> = listOf(
        "className", "id", "text", "desc", "bounds", "packageName", "indexInParent", "depth", "childCount",
        "clickable", "longClickable", "scrollable", "checkable", "checked", "enabled", "focusable", "focused", "selected", "editable", "visibleToUser",
    )

    /** The XML form's default properties, in the attribute order of `uiautomator dump`. zh-CN: XML 形式的默认属性, 按 `uiautomator dump` 的属性顺序. */
    val DEFAULT_XML: List<String> = listOf(
        "indexInParent", "text", "id", "className", "packageName", "desc",
        "checkable", "checked", "clickable", "enabled", "focusable", "focused", "scrollable", "longClickable", "password", "selected", "visibleToUser", "bounds",
    )

    private val XML_ATTRIBUTES: Map<String, String> = mapOf(
        "indexInParent" to "index", "id" to "resource-id", "idHex" to "resource-id-hex", "className" to "class", "packageName" to "package", "desc" to "content-desc",
        "longClickable" to "long-clickable", "visibleToUser" to "visible-to-user", "contextClickable" to "context-clickable", "accessibilityFocused" to "accessibility-focused",
        "childCount" to "child-count", "drawingOrder" to "drawing-order", "multiLine" to "multi-line", "contentInvalid" to "content-invalid",
        "rowSpan" to "row-span", "columnSpan" to "column-span", "rowCount" to "row-count", "columnCount" to "column-count",
    )

    /** Boolean properties whose `false` is worth a word in the text form. zh-CN: 其 `false` 值在文本形式中值得一提的布尔属性. */
    private val NOTABLE_WHEN_FALSE = setOf("enabled", "visibleToUser")

    private const val MAX_TEXT_LENGTH = 80

    fun defaultProperties(format: DumpFormat): List<String> = when (format) {
        DumpFormat.TEXT -> DEFAULT_TEXT
        DumpFormat.JSON -> DEFAULT_JSON
        DumpFormat.XML -> DEFAULT_XML
    }

    fun requireProperty(name: String) {
        if (name !in PROPERTIES) {
            throw IllegalArgumentException("Unknown node property \"$name\"; expected one of ${PROPERTIES.joinToString(", ")}")
        }
    }

    /**
     * The value of the property [name] of [node]: strings (nullable), integers, booleans,
     * `bounds` as `[left, top, right, bottom]`, `actions` as the action names.
     * zh-CN: [node] 的属性 [name] 的值: 字符串 (可为 null), 整数, 布尔值, `bounds` 为 `[left, top, right, bottom]`, `actions` 为动作名.
     */
    fun value(node: UiNode<*>, name: String): Any? = when (name) {
        "className" -> node.className()
        "id" -> node.id()
        "idHex" -> node.idHex()
        "text" -> node.text()
        "desc" -> node.desc()
        "bounds" -> listOf(node.left(), node.top(), node.right(), node.bottom())
        "packageName" -> node.packageName()
        "indexInParent" -> node.indexInParent()
        "depth" -> node.depth()
        "childCount" -> node.childCount()
        "drawingOrder" -> node.drawingOrder()
        "clickable" -> node.clickable()
        "longClickable" -> node.longClickable()
        "scrollable" -> node.scrollable()
        "checkable" -> node.checkable()
        "checked" -> node.checked()
        "enabled" -> node.enabled()
        "focusable" -> node.focusable()
        "focused" -> node.focused()
        "selected" -> node.selected()
        "editable" -> node.editable()
        "visibleToUser" -> node.visibleToUser()
        "password" -> node.password()
        "dismissable" -> node.dismissable()
        "contextClickable" -> node.contextClickable()
        "accessibilityFocused" -> node.accessibilityFocused()
        "multiLine" -> node.multiLine()
        "contentInvalid" -> node.contentInvalid()
        "row" -> node.row()
        "column" -> node.column()
        "rowSpan" -> node.rowSpan()
        "columnSpan" -> node.columnSpan()
        "rowCount" -> node.rowCount()
        "columnCount" -> node.columnCount()
        "actions" -> node.actionNames()
        else -> throw IllegalArgumentException("Unknown node property \"$name\"; expected one of ${PROPERTIES.joinToString(", ")}")
    }

    /** The properties [names] of [node] in order, as a map. zh-CN: [node] 的属性 [names], 按顺序, 作为一个 map. */
    fun properties(node: UiNode<*>, names: List<String> = DEFAULT_JSON): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        for (name in names) {
            map[name] = value(node, name)
        }
        return map
    }

    /**
     * One line of the text form: the simple class name, `[l,t][r,b]`, `name="value"` for the
     * strings that are set, the names of the boolean properties that are `true` (except
     * `enabled` / `visibleToUser`, which appear as `!enabled` / `!visibleToUser` only when
     * `false`), `name=value` for integers.
     * zh-CN: 文本形式的一行: 简单类名, `[l,t][r,b]`, 已设置的字符串为 `name="value"`, 为 `true` 的布尔属性只写名字
     * (`enabled` / `visibleToUser` 例外: 只在为 `false` 时写 `!enabled` / `!visibleToUser`), 整数为 `name=value`.
     */
    fun line(node: UiNode<*>, names: List<String> = DEFAULT_TEXT): String = buildString {
        for (name in names) {
            val piece = when (name) {
                "className" -> node.className()?.substringAfterLast('.')?.takeIf { it.isNotEmpty() } ?: "?"
                "bounds" -> bounds(node)
                "actions" -> "actions=[${node.actionNames().joinToString(", ")}]"
                else -> when (val v = value(node, name)) {
                    null -> null
                    is String -> if (v.isEmpty()) null else "$name=${quote(v)}"
                    is Boolean -> when {
                        name in NOTABLE_WHEN_FALSE -> if (v) null else "!$name"
                        v -> name
                        else -> null
                    }
                    else -> "$name=$v"
                }
            } ?: continue
            if (isNotEmpty()) append(' ')
            append(piece)
        }
    }

    /** The bounds as `[l,t][r,b]`. zh-CN: 边界, 形如 `[l,t][r,b]`. */
    fun bounds(node: UiNode<*>): String = "[${node.left()},${node.top()}][${node.right()},${node.bottom()}]"

    /** A dump: the text, how many nodes it holds and whether the node budget cut it short. zh-CN: 一次快照: 文本, 包含的节点数, 以及节点预算是否截断了它. */
    class Result(val text: String, val nodes: Int, val truncated: Boolean) {

        override fun toString(): String = text

    }

    /** The subtree of [root] alone (a JSON object, not an array). zh-CN: 仅 [root] 的子树 (JSON 为对象而非数组). */
    fun <N : UiNode<N>> dump(root: N, options: DumpOptions = DumpOptions()): Result = dump(listOf(root), options, asList = false)

    /**
     * The subtrees of [roots]; with [asList] the JSON form is an array of one object per root
     * (the form `auto.dump()` uses for the window roots), otherwise the single object of the
     * first root. The text form separates several roots by a `[root i/n]` line, the XML form
     * nests them all in `<hierarchy>`.
     * zh-CN: [roots] 的各子树; [asList] 时 JSON 形式为每个根一个对象的数组 (`auto.dump()` 对窗口根的形式), 否则为首个根的单个对象.
     * 文本形式以 `[root i/n]` 行分隔多个根, XML 形式把它们都嵌在 `<hierarchy>` 内.
     */
    fun <N : UiNode<N>> dump(roots: List<N>, options: DumpOptions = DumpOptions(), asList: Boolean = true): Result {
        val writer: Writer = when (options.format) {
            DumpFormat.TEXT -> TextWriter(options)
            DumpFormat.JSON -> JsonWriter(options)
            DumpFormat.XML -> XmlWriter(options)
        }
        val walk = Walk()
        writer.begin(asList)
        roots.forEachIndexed { index, root ->
            writer.beforeRoot(index, roots.size)
            writer.write(root, 0, walk)
        }
        writer.end(walk)
        return Result(writer.toString(), walk.count, walk.truncated)
    }

    private class Walk {

        var count = 0

        var truncated = false

    }

    private abstract class Writer(val options: DumpOptions) {

        protected val out = StringBuilder()

        protected val names: List<String> = options.propertyNames

        protected val unit: String = options.indent

        open fun begin(asList: Boolean) {}

        open fun beforeRoot(index: Int, total: Int) {}

        abstract fun open(node: UiNode<*>, level: Int, leaf: Boolean, hiddenChildren: Int)

        abstract fun close(node: UiNode<*>, level: Int, leaf: Boolean)

        open fun end(walk: Walk) {}

        fun <N : UiNode<N>> write(node: N, level: Int, walk: Walk) {
            if (walk.truncated) return
            if (options.visibleOnly && !node.visibleToUser()) return
            if (walk.count >= options.maxNodes) {
                walk.truncated = true
                return
            }
            walk.count += 1
            val depthLimited = options.maxDepth != DumpOptions.UNLIMITED && level >= options.maxDepth
            val children: List<N> = when {
                depthLimited -> emptyList()
                else -> (0 until node.childCount()).mapNotNull { node.child(it) }.filter { !options.visibleOnly || it.visibleToUser() }
            }
            val leaf = children.isEmpty()
            open(node, level, leaf, if (depthLimited) node.childCount() else 0)
            for (child in children) {
                write(child, level + 1, walk)
            }
            close(node, level, leaf)
        }

        override fun toString(): String = out.toString()

    }

    private class TextWriter(options: DumpOptions) : Writer(options) {

        override fun beforeRoot(index: Int, total: Int) {
            if (total > 1) {
                if (index > 0) out.append('\n')
                out.append("[root ${index + 1}/$total]\n")
            }
        }

        override fun open(node: UiNode<*>, level: Int, leaf: Boolean, hiddenChildren: Int) {
            out.append(unit.repeat(level)).append(line(node, names))
            if (hiddenChildren > 0) out.append(" (+").append(hiddenChildren).append(if (hiddenChildren == 1) " child)" else " children)")
            out.append('\n')
        }

        override fun close(node: UiNode<*>, level: Int, leaf: Boolean) {}

        override fun end(walk: Walk) {
            if (walk.truncated) out.append("... truncated at ").append(options.maxNodes).append(" nodes\n")
            while (out.isNotEmpty() && out.last() == '\n') out.setLength(out.length - 1)
        }

    }

    private class JsonWriter(options: DumpOptions) : Writer(options) {

        private var asList = false

        private var rootsWritten = 0

        private val childCounts = ArrayDeque<Int>()

        override fun begin(asList: Boolean) {
            this.asList = asList
            if (asList) out.append('[')
        }

        override fun open(node: UiNode<*>, level: Int, leaf: Boolean, hiddenChildren: Int) {
            if (level == 0) {
                if (asList) {
                    if (rootsWritten > 0) out.append(',')
                    out.append('\n')
                    rootsWritten += 1
                }
            } else {
                val written = childCounts.removeLast()
                if (written > 0) out.append(',')
                out.append('\n')
                childCounts.addLast(written + 1)
            }
            val base = pad(level)
            out.append(base).append("{\n")
            for (name in names) {
                out.append(base).append(unit).append(quoteJson(name)).append(": ").append(json(value(node, name))).append(",\n")
            }
            out.append(base).append(unit).append("\"children\": [")
            childCounts.addLast(0)
        }

        override fun close(node: UiNode<*>, level: Int, leaf: Boolean) {
            val written = childCounts.removeLast()
            if (written > 0) out.append('\n').append(pad(level)).append(unit)
            out.append("]\n").append(pad(level)).append('}')
        }

        override fun end(walk: Walk) {
            if (asList) {
                if (rootsWritten > 0) out.append('\n')
                out.append(']')
            }
        }

        private fun pad(level: Int): String = unit.repeat((if (asList) 1 else 0) + level * 2)

        private fun json(value: Any?): String = when (value) {
            null -> "null"
            is String -> quoteJson(value)
            is Boolean, is Int, is Long -> value.toString()
            is List<*> -> value.joinToString(", ", "[", "]") { json(it) }
            else -> quoteJson(value.toString())
        }

    }

    private class XmlWriter(options: DumpOptions) : Writer(options) {

        override fun begin(asList: Boolean) {
            out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<hierarchy>")
        }

        override fun open(node: UiNode<*>, level: Int, leaf: Boolean, hiddenChildren: Int) {
            out.append('\n').append(unit.repeat(level + 1)).append("<node")
            for (name in names) {
                out.append(' ').append(XML_ATTRIBUTES[name] ?: name).append("=\"").append(xml(value(node, name))).append('"')
            }
            out.append(if (leaf) " />" else ">")
        }

        override fun close(node: UiNode<*>, level: Int, leaf: Boolean) {
            if (!leaf) out.append('\n').append(unit.repeat(level + 1)).append("</node>")
        }

        override fun end(walk: Walk) {
            if (walk.truncated) out.append('\n').append(unit).append("<!-- truncated at ").append(options.maxNodes).append(" nodes -->")
            out.append("\n</hierarchy>")
        }

        private fun xml(value: Any?): String = when (value) {
            null -> ""
            is String -> escapeXml(value)
            is Boolean, is Int, is Long -> value.toString()
            is List<*> -> when {
                value.size == 4 && value.all { it is Int } -> "[${value[0]},${value[1]}][${value[2]},${value[3]}]"
                else -> escapeXml(value.joinToString(","))
            }
            else -> escapeXml(value.toString())
        }

    }

    /** `"..."` with `"` / `\` / line breaks escaped and long values cut at [MAX_TEXT_LENGTH]. zh-CN: `"..."`, 转义 `"` / `\` / 换行, 过长的值在 [MAX_TEXT_LENGTH] 处截断. */
    private fun quote(s: String): String {
        val cut = if (s.length > MAX_TEXT_LENGTH) s.substring(0, MAX_TEXT_LENGTH - 3) + "..." else s
        val sb = StringBuilder(cut.length + 2).append('"')
        for (c in cut) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun quoteJson(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun escapeXml(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\n' -> sb.append("&#10;")
                c == '\r' -> sb.append("&#13;")
                c == '\t' -> sb.append("&#9;")
                c < ' ' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

}
