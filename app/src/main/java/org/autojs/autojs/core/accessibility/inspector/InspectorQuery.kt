package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.filter.Filter
import java.util.regex.PatternSyntaxException

/**
 * What the user typed into the inspector search bar: the [mode] (roadmap D2), the [text] and, for
 * the keyword mode, the regex switch. [compile] turns it into the filters a [TreeMatch] evaluates;
 * blank text compiles to no filters (nothing is filtered). The field and selector modes are wired
 * in roadmap P4 / P5 and report [InspectorQueryError.Kind.UNSUPPORTED_MODE] until then.
 *
 * Free of Android types; the UI maps [InspectorQueryError.Kind] to localized messages.
 *
 * zh-CN: 用户在搜索栏输入的内容: [mode] (Roadmap D2), [text], 以及关键词模式的正则开关. [compile] 把它转为 [TreeMatch]
 * 求值的过滤器列表; 空白文本编译为空列表 (不筛选). 字段与选择器模式在 Roadmap P4 / P5 接入, 此前报告
 * [InspectorQueryError.Kind.UNSUPPORTED_MODE]. 不含 Android 类型; 界面层把 [InspectorQueryError.Kind] 映射为本地化消息.
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
            Mode.FIELD, Mode.SELECTOR -> Compiled.Error(InspectorQueryError(InspectorQueryError.Kind.UNSUPPORTED_MODE, mode.name))
        }
    }

    sealed class Compiled {

        class Filters(val filters: List<Filter>) : Compiled() {
            val isEmpty: Boolean get() = filters.isEmpty()
        }

        class Error(val error: InspectorQueryError) : Compiled()

    }

}

/**
 * Why a query did not compile: the [kind] selects the message, [detail] is the parser's own text
 * and [position] the offending character offset (-1 when unknown).
 * zh-CN: 查询未能编译的原因: [kind] 决定消息, [detail] 为解析器自身的文本, [position] 为出错字符偏移 (未知时为 -1).
 */
class InspectorQueryError(val kind: Kind, val detail: String? = null, val position: Int = -1) {

    enum class Kind { INVALID_REGEX, UNSUPPORTED_MODE, SYNTAX }

    override fun toString(): String = buildString {
        append(kind.name)
        if (position >= 0) append(" @").append(position)
        if (!detail.isNullOrEmpty()) append(": ").append(detail)
    }

}
