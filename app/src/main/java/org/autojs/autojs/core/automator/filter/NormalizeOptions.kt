package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode
import java.util.Locale
import java.text.Normalizer as TextNormalizer

/**
 * The text normalization of a selector, `normalize(...)` (docs/selector/ROADMAP.md D10, P3.3, appendix C Q4): applied
 * to every string condition of the selector it is part of, wherever it stands among them. A literal comparison
 * (`text`, `textStartsWith`, `textEndsWith`, `textContains` ...) normalizes both the value of the node and its
 * argument; a pattern (`textMatch`, `textMatches`, a RegExp) normalizes the value only, and [caseInsensitive] makes it
 * ignore the case instead of lowering the value (appendix C Q17). The texts (`text`, `desc`, `content`) take every
 * option chosen, the names (`id`, `idHex`, `className`, `packageName`) only [trim] and [caseInsensitive] ([of]). The
 * options apply in the order [nfkc], [fullWidth], [whitespace], [trim], [caseInsensitive].
 *
 * zh-CN: 选择器的文本规范化 `normalize(...)` (D10, P3.3, 附录 C Q4): 作用于其所在选择器的每个字符串条件, 与它在各条件中的位置无关. 字面比较
 * (`text`, `textStartsWith`, `textEndsWith`, `textContains` ...) 同时规范化节点的值与参数; 模式 (`textMatch`, `textMatches`, RegExp) 只规范化值,
 * [caseInsensitive] 使其忽略大小写而不是把值转为小写 (附录 C Q17). 文本属性 (`text`, `desc`, `content`) 采用选中的全部选项, 名称属性
 * (`id`, `idHex`, `className`, `packageName`) 只采用 [trim] 与 [caseInsensitive] ([of]). 选项按 [nfkc], [fullWidth], [whitespace], [trim],
 * [caseInsensitive] 的顺序施加.
 *
 * Created by SuperMonster003 on Oct 26, 2026.
 */
data class NormalizeOptions(
    /** Leading and trailing white space removed (NBSP included). zh-CN: 去掉首尾空白 (含 NBSP). */
    val trim: Boolean = false,
    /** Letters compared regardless of their case. zh-CN: 字母比较时不区分大小写. */
    val caseInsensitive: Boolean = false,
    /** Full-width ASCII letters, digits and punctuation (U+FF01-FF5E) and the ideographic space as their ASCII forms. zh-CN: 全角 ASCII 字母, 数字与标点 (U+FF01-FF5E) 及全角空格转为 ASCII 形式. */
    val fullWidth: Boolean = false,
    /** Runs of white space (NBSP included) as one space, zero-width characters removed. zh-CN: 连续空白 (含 NBSP) 折叠为一个空格, 移除零宽字符. */
    val whitespace: Boolean = false,
    /** Unicode normalization form NFKC. zh-CN: Unicode 规范化形式 NFKC. */
    val nfkc: Boolean = false,
) {

    /** Whether no option is on. zh-CN: 是否没有任何选项开启. */
    val isOff: Boolean get() = !(trim || caseInsensitive || fullWidth || whitespace || nfkc)

    /** The options that apply to the values of [property]. zh-CN: 作用于 [property] 之值的选项. */
    fun of(property: StringProperty): NormalizeOptions = when (property) {
        StringProperty.TEXT, StringProperty.DESC, StringProperty.CONTENT -> this
        else -> NormalizeOptions(trim = trim, caseInsensitive = caseInsensitive)
    }

    /** [value] normalized; [lowerCase]: whether to lower its case under [caseInsensitive]. zh-CN: 规范化后的 [value]; [lowerCase]: [caseInsensitive] 时是否转为小写. */
    fun apply(value: CharSequence, lowerCase: Boolean = true): String {
        var s = value.toString()
        if (nfkc) s = TextNormalizer.normalize(s, TextNormalizer.Form.NFKC)
        if (fullWidth) s = halfWidth(s)
        if (whitespace) s = collapse(s)
        if (trim) s = s.trim()
        if (caseInsensitive && lowerCase) s = s.lowercase(Locale.ROOT)
        return s
    }

    /** As the selector string renders it: `normalize()`, `normalize(false)`, or the options on. zh-CN: 选择器字符串中的写法. */
    override fun toString(): String = when (this) {
        ALL -> "normalize()"
        NONE -> "normalize(false)"
        else -> "normalize({" + names().filter { it.second }.joinToString(", ") { "${it.first}: true" } + "})"
    }

    private fun names() = listOf(TRIM to trim, CASE_INSENSITIVE to caseInsensitive, FULL_WIDTH to fullWidth, WHITESPACE to whitespace, NFKC to nfkc)

    companion object {

        const val TRIM = "trim"
        const val CASE_INSENSITIVE = "caseInsensitive"
        const val FULL_WIDTH = "fullWidth"
        const val WHITESPACE = "whitespace"
        const val NFKC = "nfkc"

        /** The option names, in the order of the selector string. zh-CN: 选项名, 按选择器字符串中的顺序. */
        val NAMES = listOf(TRIM, CASE_INSENSITIVE, FULL_WIDTH, WHITESPACE, NFKC)

        /** `normalize()` / `normalize(true)`. */
        val ALL = NormalizeOptions(trim = true, caseInsensitive = true, fullWidth = true, whitespace = true, nfkc = true)

        /** `normalize(false)`: off, also for the conditions of the selector a `normalize` before it covered. zh-CN: 关闭, 同样覆盖其前面的 `normalize`. */
        val NONE = NormalizeOptions()

        /**
         * The options of an object `{ trim: true, ... }`: [values] by name, each a boolean; null when a name is unknown
         * or a value not a boolean (the caller tells which). zh-CN: 对象 `{ trim: true, ... }` 的选项; 名称未知或值不是布尔值时为 null.
         */
        fun of(values: Map<*, *>): NormalizeOptions? {
            if (values.keys.any { it !is CharSequence || it.toString() !in NAMES } || values.values.any { it !is Boolean }) return null
            fun on(name: String) = values[name] == true
            return NormalizeOptions(on(TRIM), on(CASE_INSENSITIVE), on(FULL_WIDTH), on(WHITESPACE), on(NFKC))
        }

        private const val FULL_WIDTH_FIRST = '\uFF01'
        private const val FULL_WIDTH_LAST = '\uFF5E'
        private const val FULL_WIDTH_OFFSET = 0xFEE0
        private const val IDEOGRAPHIC_SPACE = '\u3000'

        /** Zero width space, non-joiner, joiner, word joiner, byte order mark. zh-CN: 零宽空格, 零宽非连接符, 零宽连接符, 词连接符, 字节序标记. */
        private val ZERO_WIDTH = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

        private fun halfWidth(s: String): String {
            if (s.none { it in FULL_WIDTH_FIRST..FULL_WIDTH_LAST || it == IDEOGRAPHIC_SPACE }) return s
            val out = StringBuilder(s.length)
            for (c in s) out.append(
                when (c) {
                    in FULL_WIDTH_FIRST..FULL_WIDTH_LAST -> c - FULL_WIDTH_OFFSET
                    IDEOGRAPHIC_SPACE -> ' '
                    else -> c
                }
            )
            return out.toString()
        }

        private fun collapse(s: String): String {
            val out = StringBuilder(s.length)
            var space = false
            for (c in s) {
                when {
                    c in ZERO_WIDTH -> Unit
                    c.isWhitespace() -> space = true
                    else -> {
                        if (space) out.append(' ')
                        space = false
                        out.append(c)
                    }
                }
            }
            if (space) out.append(' ')
            return out.toString()
        }

    }

}

/**
 * `normalize(...)` among the filters of a selector: passes every node, and tells the selector how to compare its string
 * conditions ([Selector] applies the last one of them). zh-CN: 选择器过滤器中的 `normalize(...)`: 接受每个节点, 并告诉选择器如何比较其字符串条件
 * ([Selector] 采用其中最后一个).
 */
class NormalizeFilter(val options: NormalizeOptions) : Filter {

    override fun filter(node: UiNode<*>) = true

    override val cost: Int get() = Filter.COST_PROPERTY

    override fun toString() = options.toString()

}
