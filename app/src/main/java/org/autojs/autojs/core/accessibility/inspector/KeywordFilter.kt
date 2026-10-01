package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.JsRegexUtils
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * The keyword mode of the layout inspector search (roadmap D10): one term matched against the
 * text, description, resource id and class name of a node, any hit counts. A plain keyword is a
 * case-insensitive substring; with the regex switch on, the keyword is compiled as a JavaScript
 * literal (`/re/flags`, flags honoured) or as a plain pattern, which is then case-insensitive like
 * the plain keyword so that flipping the switch does not silently change the case rule. A
 * malformed pattern fails at construction ([PatternSyntaxException]), not at the first node.
 *
 * [hit] tells which property matched first (in [KeywordProperty] order) for the row highlight and
 * for the selector export of roadmap D8, which never emits this filter as is; [toString] is only
 * the label used by `SelectorExplainer`.
 *
 * zh-CN: 布局分析搜索的关键词模式 (Roadmap D10): 一个词同时与节点的 text / desc / 资源 id / 类名比较, 任一命中即匹配.
 * 普通关键词按大小写不敏感的子串比较; 开启正则开关后, 关键词按 JavaScript 字面量 (`/re/flags`, 标志生效) 或普通模式编译,
 * 后者同样大小写不敏感, 以免切换开关时大小写规则悄然改变. 非法模式在构造时失败 ([PatternSyntaxException]), 而非首个节点.
 * [hit] 给出首个命中的属性 (按 [KeywordProperty] 顺序), 供行高亮与 Roadmap D8 的选择器导出使用 (导出不会原样输出本过滤器);
 * [toString] 只是 `SelectorExplainer` 使用的标签.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class KeywordFilter private constructor(
    val keyword: String,
    val isRegex: Boolean,
    private val regex: Regex?,
    val properties: List<KeywordProperty>,
) : Filter {

    override fun filter(node: UiNode<*>): Boolean = hit(node) != null

    /** The first property whose value matches, or null. zh-CN: 首个值匹配的属性, 无则为 null. */
    fun hit(node: UiNode<*>): KeywordProperty? = properties.firstOrNull { property -> property.read(node)?.let(::matches) == true }

    /** Whether [value] matches the keyword on its own. zh-CN: [value] 本身是否匹配关键词. */
    fun matches(value: CharSequence): Boolean = when (regex) {
        null -> value.contains(keyword, ignoreCase = true)
        else -> regex.containsMatchIn(value)
    }

    /**
     * The range of the first match inside [value] for highlighting, or null when it does not match.
     * zh-CN: [value] 中首个匹配的区间 (供高亮), 不匹配时为 null.
     */
    fun firstMatchRange(value: CharSequence): IntRange? = when (regex) {
        null -> value.indexOf(keyword, ignoreCase = true).takeIf { it >= 0 }?.let { it until it + keyword.length }
        else -> regex.find(value)?.range
    }

    override fun toString(): String = "keyword(${if (isRegex) JsRegexUtils.formatAsJsRegexLiteral(keyword) else "\"$keyword\""})"

    companion object {

        /**
         * Builds the filter; [PatternSyntaxException] when [regex] is set and [keyword] is not a
         * valid pattern. A blank keyword is the caller's concern (see [InspectorQuery]).
         * zh-CN: 构造过滤器; [regex] 开启且 [keyword] 不是合法模式时抛 [PatternSyntaxException]. 空白关键词由调用方处理 (见 [InspectorQuery]).
         */
        @Throws(PatternSyntaxException::class)
        fun of(keyword: String, regex: Boolean, properties: List<KeywordProperty> = KeywordProperty.DEFAULT): KeywordFilter {
            val compiled = if (regex) compile(keyword) else null
            return KeywordFilter(keyword, regex, compiled, properties)
        }

        private fun compile(keyword: String): Regex = when (JsRegexUtils.parseJsRegexLiteral(keyword)) {
            null -> Pattern.compile(keyword, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE).toRegex()
            else -> JsRegexUtils.compileJsOrPlainRegex(keyword)
        }

    }

}

/**
 * The node properties the keyword mode looks at, in hit order; [label] is the selector property
 * name used by the export (`textContains(...)` and so on).
 * zh-CN: 关键词模式比较的节点属性, 按命中顺序; [label] 为导出时使用的选择器属性名 (`textContains(...)` 等).
 */
enum class KeywordProperty(val label: String, val read: (UiNode<*>) -> String?) {

    TEXT("text", { it.text() }),
    DESC("desc", { it.desc() }),
    ID("id", { it.id() }),
    CLASS_NAME("className", { it.className() });

    companion object {

        val DEFAULT: List<KeywordProperty> = listOf(TEXT, DESC, ID, CLASS_NAME)

    }

}
