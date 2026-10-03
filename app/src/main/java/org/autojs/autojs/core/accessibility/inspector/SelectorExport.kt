package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.Filter
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

    /** The chain [calls] and the trailing [comments]; [text] is what goes to the clipboard. zh-CN: 链式 [calls] 与尾随 [comments]; [text] 为写入剪贴板的内容. */
    class Rendered(val calls: List<String>, val comments: List<String>) {

        val isEmpty: Boolean get() = calls.isEmpty() && comments.isEmpty()

        val text: String
            get() = buildString {
                append(calls.joinToString(".").ifEmpty { "selector()" })
                for (comment in comments) append(' ').append(comment)
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

    /** A double-quoted JS string literal. zh-CN: 双引号 JS 字符串字面量. */
    fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

}
