package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.filter.JsLiteral.quote
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * How a string property is compared: the six selector forms (`text("a")`, `textStartsWith`,
 * `textEndsWith`, `textContains`, `textMatches`, `textMatch`), each with its plain-string and regex
 * argument. [suffix] is the builder suffix and [argument] the argument as the selector's string
 * form renders it (`"a"` or `/a/i`).
 *
 * Plain strings are compared literally by `equals`, `startsWith`, `endsWith` and `contains` (the
 * latter as a substring; from 6.7.0 Alpha13 until roadmap decision D15 it compiled the string as a
 * pattern, so `textContains(".")` matched any non-empty text and `textContains("(")` failed);
 * `matches` and `match` compile them as patterns, and a string in JavaScript literal form
 * (`"/re/flags"`) keeps its flags there. Regex arguments match the whole value in `equals` and
 * `matches`, and anywhere in `contains` and `match`. Patterns compile when the matcher is built, so
 * a malformed one fails the builder call rather than the first search.
 *
 * zh-CN: 字符串属性的比较方式: 六种选择器形式 (`text("a")`, `textStartsWith`, `textEndsWith`, `textContains`, `textMatches`,
 * `textMatch`), 各有普通字符串与正则两种参数. [suffix] 为 builder 后缀, [argument] 为选择器字符串形式中的参数 (`"a"` 或 `/a/i`).
 *
 * `equals` / `startsWith` / `endsWith` / `contains` 按字面比较普通字符串 (后者为子串; 自 6.7.0 Alpha13 至 Roadmap 决定 D15 之前
 * 它把字符串编译为模式, 因此 `textContains(".")` 匹配任意非空文本, `textContains("(")` 报错); `matches` / `match` 把它编译为模式,
 * 且 JavaScript 字面量形式的字符串 (`"/re/flags"`) 在其中保留其标志. 正则参数在 `equals` 与 `matches` 中须匹配整个值, 在
 * `contains` 与 `match` 中匹配任意位置. 模式在构造时编译, 因此写错的模式在 builder 调用时即失败, 而非首次查找时.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
sealed class StringMatcher(val suffix: String, val argument: String) {

    abstract fun matches(value: CharSequence): Boolean

    /** The string form of a filter over [property] with this matcher, e.g. `textContains("a")`. zh-CN: 以本比较方式作用于 [property] 的过滤器的字符串形式. */
    fun describe(property: Any): String = "$property$suffix($argument)"

    override fun toString() = describe("")

    /**
     * The value an exact comparison with a plain string expects, null for the other forms (the framework fast path of
     * docs/selector/ROADMAP.md P2.3). zh-CN: 与普通字符串精确比较时期望的值, 其他形式为 null (P2.3 的框架快路径).
     */
    internal open val exact: String? get() = null

    /** Whether the form matches a pattern (the filter order of P2.2). zh-CN: 该形式是否按模式匹配 (P2.2 的过滤器顺序). */
    internal open val isPattern: Boolean get() = false

    private class Equals(private val expected: String) : StringMatcher("", quote(expected)) {
        override fun matches(value: CharSequence) = value.contentEquals(expected)
        override val exact get() = expected
    }

    private class StartsWith(private val prefix: String) : StringMatcher("StartsWith", quote(prefix)) {
        override fun matches(value: CharSequence) = value.startsWith(prefix)
    }

    private class EndsWith(private val ending: String) : StringMatcher("EndsWith", quote(ending)) {
        override fun matches(value: CharSequence) = value.endsWith(ending)
    }

    private class Contains(private val part: String) : StringMatcher("Contains", quote(part)) {
        override fun matches(value: CharSequence) = value.contains(part)
    }

    /** A regex form: the whole value must match, or a match must occur somewhere in it. zh-CN: 正则形式: 整个值须匹配, 或值中任意位置出现匹配. */
    private class Pattern(suffix: String, argument: String, private val regex: Regex, private val whole: Boolean) : StringMatcher(suffix, argument) {
        override fun matches(value: CharSequence) = if (whole) value.matches(regex) else value.contains(regex)
        override val isPattern get() = true
    }

    companion object {

        fun equals(s: String): StringMatcher = Equals(s)

        fun equals(regex: NativeRegExp): StringMatcher = Pattern("", literal(regex), compile(regex), whole = true)

        fun startsWith(prefix: String): StringMatcher = StartsWith(prefix)

        fun endsWith(suffix: String): StringMatcher = EndsWith(suffix)

        fun contains(s: String): StringMatcher = Contains(s)

        fun contains(regex: NativeRegExp): StringMatcher = Pattern("Contains", literal(regex), compile(regex), whole = false)

        fun matches(s: String): StringMatcher = Pattern("Matches", quote(s), compile(s), whole = true)

        fun matches(regex: NativeRegExp): StringMatcher = Pattern("Matches", literal(regex), compile(regex), whole = true)

        fun match(s: String): StringMatcher = Pattern("Match", quote(s), compile(s), whole = false)

        fun match(regex: NativeRegExp): StringMatcher = Pattern("Match", literal(regex), compile(regex), whole = false)

        private fun compile(s: String) = JsRegexUtils.compileJsOrPlainRegex(s)

        private fun compile(regex: NativeRegExp) = JsRegexUtils.compileJsOrPlainRegex(regex.toString())

        private fun literal(regex: NativeRegExp) = JsRegexUtils.formatAsJsRegexLiteral(regex.toString())

    }

}
