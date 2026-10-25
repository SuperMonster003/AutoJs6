package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.selector.SelectorStringException
import org.autojs.autojs.core.automator.selector.SelectorStringParser
import java.util.regex.PatternSyntaxException

/**
 * What the user typed into the inspector search bar: the [mode] (roadmap D2), the [text] and, for
 * the keyword mode, the regex switch. [compile] turns it into the filters a [TreeMatch] evaluates;
 * blank text compiles to no filters (nothing is filtered). The field mode goes through
 * [SelectorStringParser] (D11), the selector mode through [SelectorCallParser] (D12); both report
 * [InspectorQueryError.Kind.SYNTAX] with position / term / candidates, and an unfinished selector
 * expression reports [InspectorQueryError.Kind.INCOMPLETE] instead.
 *
 * Free of Android types; the UI maps [InspectorQueryError.Kind] to localized messages.
 *
 * zh-CN: 用户在搜索栏输入的内容: [mode] (Roadmap D2), [text], 以及关键词模式的正则开关. [compile] 把它转为 [TreeMatch]
 * 求值的过滤器列表; 空白文本编译为空列表 (不筛选). 字段模式经 [SelectorStringParser] (D11), 选择器模式经 [SelectorCallParser]
 * (D12); 两者都以 [InspectorQueryError.Kind.SYNTAX] 报告位置 / 条件 / 候选, 未结束的选择器表达式改报
 * [InspectorQueryError.Kind.INCOMPLETE]. 不含 Android 类型; 界面层把 [InspectorQueryError.Kind] 映射为本地化消息.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
data class InspectorQuery(
    val mode: Mode = Mode.KEYWORD,
    val text: String = "",
    val regex: Boolean = false,
) {

    enum class Mode { KEYWORD, FIELD, SELECTOR }

    val isBlank: Boolean get() = text.isBlank()

    /** The filters, or the error that keeps the query from compiling. zh-CN: 过滤器列表, 或阻止编译的错误. */
    fun compile(): Compiled {
        if (isBlank) return Compiled.Filters(emptyList())
        return when (mode) {
            Mode.KEYWORD -> try {
                Compiled.Filters(listOf(KeywordFilter.of(text.trim(), regex)))
            } catch (e: PatternSyntaxException) {
                Compiled.Error(InspectorQueryError(InspectorQueryError.Kind.INVALID_REGEX, e.description ?: e.message, e.index))
            }
            Mode.FIELD -> try {
                val selector = parser.parse(text.trim(), UiSelector(null as AccessibilityBridge?))
                Compiled.Filters(selector.selector.effectiveFilters)
            } catch (e: SelectorStringException) {
                Compiled.Error(InspectorQueryError(InspectorQueryError.Kind.SYNTAX, e.detail, e.position, e.term, e.candidates))
            }
            Mode.SELECTOR -> try {
                val selector = callParser.parse(text.trim(), UiSelector(null as AccessibilityBridge?))
                Compiled.Filters(selector.selector.effectiveFilters)
            } catch (e: SelectorCallException) {
                val kind = if (e.incomplete) InspectorQueryError.Kind.INCOMPLETE else InspectorQueryError.Kind.SYNTAX
                Compiled.Error(InspectorQueryError(kind, e.detail, e.position, e.term, e.candidates))
            }
        }
    }

    companion object {

        private val parser by lazy { SelectorStringParser() }

        private val callParser by lazy { SelectorCallParser() }

    }

    sealed class Compiled {

        class Filters(val filters: List<Filter>) : Compiled() {
            val isEmpty: Boolean get() = filters.isEmpty()
        }

        class Error(val error: InspectorQueryError) : Compiled()

    }

}

/**
 * Why a query did not compile: the [kind] selects the message, [detail] is the parser's own text,
 * [position] the offending character offset (-1 when unknown), [term] the offending condition and
 * [candidates] property names close to an unknown key (field mode, roadmap P4.1).
 * zh-CN: 查询未能编译的原因: [kind] 决定消息, [detail] 为解析器自身的文本, [position] 为出错字符偏移 (未知时为 -1), [term] 为出错的条件,
 * [candidates] 为与未知键相近的属性名 (字段模式, Roadmap P4.1).
 */
class InspectorQueryError(
    val kind: Kind,
    val detail: String? = null,
    val position: Int = -1,
    val term: String? = null,
    val candidates: List<String> = emptyList(),
) {

    /**
     * [INCOMPLETE] is the selector mode's "expression not finished" (roadmap P5.1): not a mistake,
     * just an expression that stops mid-call while the user is still typing.
     * zh-CN: [INCOMPLETE] 为选择器模式的 "表达式未结束" (Roadmap P5.1): 不是错误, 只是用户仍在输入时停在调用中途的表达式.
     */
    enum class Kind { INVALID_REGEX, SYNTAX, INCOMPLETE }

    override fun toString(): String = buildString {
        append(kind.name)
        if (position >= 0) append(" @").append(position)
        if (!term.isNullOrEmpty()) append(" [").append(term).append("]")
        if (!detail.isNullOrEmpty()) append(": ").append(detail)
        if (candidates.isNotEmpty()) append(" (").append(candidates.joinToString(", ")).append(")")
    }

}
