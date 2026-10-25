package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.JsLiteral.quote
import org.autojs.autojs.core.automator.filter.JsRegexUtils

/**
 * "Copy as selector" (roadmap P4.2, D8): renders the conditions in effect as a chained script
 * selector. The keyword query becomes the `Contains` / `Match` call of the property the current
 * match actually hit (text > desc > id > className); field (and later selector) queries print their
 * own filters; the profile and region filters follow. Filters the registry cannot express print as
 * `/* ... */` comments which are appended after the chain instead of being dropped; [ActionFilter]
 * is re-rendered with quoted names because its own `toString()` is not script syntax.
 *
 * zh-CN: "复制为选择器" (Roadmap P4.2, D8): 把生效中的条件渲染为链式脚本选择器. 关键词查询按当前匹配节点实际命中的属性
 * (text > desc > id > className) 输出 `Contains` / `Match` 调用; 字段 (以及之后的选择器) 查询直接打印其过滤器; 其后为配置与区域
 * 过滤器. 注册表无法表达的过滤器打印为 `/* ... */` 注释, 附在链尾而不是丢弃; [ActionFilter] 因自身 `toString()` 不是脚本语法而
 * 重新以带引号的名称渲染.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
object SelectorExport {

    private const val COMMENT_PREFIX = "/*"

    /** What goes to the clipboard: the chain or a `pickup` call (selector P3.5). zh-CN: 写入剪贴板的形式: 链式或 `pickup` 调用 (selector P3.5). */
    enum class Form { CHAIN, PICKUP }

    /** The chain [calls] and the trailing [comments]; [text] is what goes to the clipboard. zh-CN: 链式 [calls] 与尾随 [comments]; [text] 为写入剪贴板的内容. */
    class Rendered(val calls: List<String>, val comments: List<String>) {

        val isEmpty: Boolean get() = calls.isEmpty() && comments.isEmpty()

        private val chain: String get() = calls.joinToString(".").ifEmpty { "selector()" }

        val text: String get() = text(Form.CHAIN)

        /**
         * The [Form.PICKUP] form puts the calls in the object selector of `pickup`, `pickup({text: "OK", boundsInside: [0, 0, 100, 200]})`:
         * each call is an entry whose value is null without arguments, the argument itself for one and an array for more (as
         * `SelectorMapParser` spreads it). Calls naming the same property twice, or not of the form `name(arguments)`, keep the
         * chain inside, `pickup(text("a").text("b"))`.
         * zh-CN: [Form.PICKUP] 形式把调用放进 `pickup` 的对象选择器: 每个调用为一项, 无参数时值为 null, 一个参数时为参数本身, 多个时为数组
         * (`SelectorMapParser` 会将其展开). 有调用重复同一属性, 或不是 `name(参数)` 形式时, 改为在 pickup 中保留链式, 如 `pickup(text("a").text("b"))`.
         */
        fun text(form: Form): String = buildString {
            when (form) {
                Form.CHAIN -> append(chain)
                Form.PICKUP -> append("pickup(").append(objectSelector() ?: chain).append(')')
            }
            for (comment in comments) append(' ').append(comment)
        }

        private fun objectSelector(): String? {
            val entries = calls.map { CallSyntax.parse(it) ?: return null }
            if (entries.distinctBy { it.first }.size < entries.size) return null
            return entries.joinToString(", ", "{", "}") { (name, args) -> "$name: ${CallSyntax.valueOf(args)}" }
        }

        override fun toString() = text

    }

    /**
     * @param query the query in effect; a blank one contributes nothing.
     * @param queryFilters the filters [query] compiled to (field / selector modes print them; the keyword mode ignores them).
     * @param hit the keyword property the current match hit, or null to fall back to text.
     * @param extraFilters the profile and region filters, in order.
     * zh-CN: [query] 为生效中的查询 (空白时不输出); [queryFilters] 为其编译出的过滤器 (字段 / 选择器模式打印它们, 关键词模式忽略);
     * [hit] 为当前匹配命中的关键词属性, null 时回退到 text; [extraFilters] 为配置与区域过滤器.
     */
    fun render(query: InspectorQuery, queryFilters: List<Filter>, hit: KeywordProperty?, extraFilters: List<Filter>): Rendered {
        val calls = ArrayList<String>()
        val comments = ArrayList<String>()
        if (!query.isBlank) {
            when (query.mode) {
                InspectorQuery.Mode.KEYWORD -> calls += keywordCall(query.text.trim(), query.regex, hit ?: KeywordProperty.TEXT)
                InspectorQuery.Mode.FIELD, InspectorQuery.Mode.SELECTOR -> queryFilters.forEach { place(it, calls, comments) }
            }
        }
        extraFilters.forEach { place(it, calls, comments) }
        return Rendered(calls, comments)
    }

    private fun keywordCall(keyword: String, regex: Boolean, property: KeywordProperty): String = when (regex) {
        true -> "${property.label}Match(${JsRegexUtils.formatAsJsRegexLiteral(keyword)})"
        false -> "${property.label}Contains(${quote(keyword)})"
    }

    private fun place(filter: Filter, calls: MutableList<String>, comments: MutableList<String>) {
        val rendered = when (filter) {
            is ActionFilter -> "action(${filter.actions.joinToString(", ") { quote(it.toString()) }})"
            else -> filter.toString()
        }
        if (rendered.startsWith(COMMENT_PREFIX)) comments += rendered else calls += rendered
    }

}

/**
 * Splits a rendered call, `name(a, "b, c", /d,e/i, [1, 2])`, into its name and the source of each argument (commas inside
 * strings, regular expression literals and brackets do not split); used by the [SelectorExport.Form.PICKUP] form.
 * zh-CN: 把渲染出的调用拆为名称与每个参数的源码 (字符串, 正则字面量与括号内的逗号不拆分); 供 [SelectorExport.Form.PICKUP] 形式使用.
 */
internal object CallSyntax {

    private val NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

    /** The name and the arguments of [call], or null when it is not of the form `name(arguments)`. zh-CN: [call] 的名称与参数, 不是 `name(参数)` 形式时为 null. */
    fun parse(call: String): Pair<String, List<String>>? {
        val open = call.indexOf('(')
        if (open <= 0 || !call.endsWith(')')) return null
        val name = call.substring(0, open)
        if (!NAME.matches(name)) return null
        val args = split(call.substring(open + 1, call.length - 1)) ?: return null
        return name to args
    }

    /**
     * The value of an object selector entry for [args]: null, the argument, or an array; a single argument that is itself an
     * array or null goes in an array so that it is not spread or taken as no argument.
     * zh-CN: [args] 对应的对象选择器项的值: null, 参数本身或数组; 单个参数本身为数组或 null 时放进数组, 以免被展开或当作无参数.
     */
    fun valueOf(args: List<String>): String = when {
        args.isEmpty() -> "null"
        args.size == 1 && !args[0].startsWith("[") && args[0] != "null" -> args[0]
        else -> args.joinToString(", ", "[", "]")
    }

    /** The top level arguments of [source], or null when its brackets or literals do not close. zh-CN: [source] 的顶层参数, 括号或字面量未闭合时为 null. */
    fun split(source: String): List<String>? {
        val args = ArrayList<String>()
        var depth = 0
        var start = 0
        var operand = true
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '"' || c == '\'' -> {
                    i = skipString(source, i) ?: return null
                    operand = false
                    continue
                }
                c == '/' && operand -> {
                    i = skipRegex(source, i) ?: return null
                    operand = false
                    continue
                }
                c == '(' || c == '[' || c == '{' -> {
                    depth++
                    operand = true
                }
                c == ')' || c == ']' || c == '}' -> {
                    if (--depth < 0) return null
                    operand = false
                }
                c == ',' && depth == 0 -> {
                    args += source.substring(start, i).trim()
                    start = i + 1
                    operand = true
                }
                c == ',' || c == ':' -> operand = true
                !c.isWhitespace() -> operand = false
            }
            i++
        }
        if (depth != 0) return null
        val last = source.substring(start).trim()
        return when {
            last.isNotEmpty() -> args + last
            args.isEmpty() -> args
            else -> null
        }
    }

    private fun skipString(source: String, from: Int): Int? {
        val quote = source[from]
        var i = from + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i++
                quote -> return i + 1
            }
            i++
        }
        return null
    }

    private fun skipRegex(source: String, from: Int): Int? {
        var i = from + 1
        var inClass = false
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i++
                '[' -> inClass = true
                ']' -> inClass = false
                '/' -> if (!inClass) {
                    i++
                    while (i < source.length && source[i].isLetter()) i++
                    return i
                }
            }
            i++
        }
        return null
    }

}
