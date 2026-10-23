package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassParser
import org.autojs.autojs.core.automator.pick.CompassSyntaxException
import org.autojs.autojs.core.automator.pick.Invoker
import org.autojs.autojs.core.automator.selector.SelectorArgType
import org.autojs.autojs.core.automator.selector.SelectorEntry
import org.autojs.autojs.core.automator.selector.SelectorParam
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs6.R

/**
 * The checks of [SelectorForms] that throw nothing (appendix C Q69 (2)): the values a condition refuses, the compasses
 * that do not parse and the result type methods whose arguments do not convert are told before the current readers
 * would throw for them, so that a refused form costs no Java exception on the way to its RangeError (roadmap R2).
 *
 * Unlike the rest of [SelectorForms] (Q66 (2)), these checks copy what the readers do, as far as their exceptions go;
 * each copy names its original, and roadmap P5.2 merges the two when the current module goes. What a copy cannot tell
 * is left to the reader as before: a pattern this check does not refuse is compiled, a compass with other than ASCII
 * characters is parsed, each in a `try` (the regex engines of the JVM and of Android differ there).
 *
 * zh-CN: [SelectorForms] 中不抛异常的检查 (附录 C Q69 (2)): 条件拒绝的值, 无法解析的罗盘, 参数无法转换的结果类型方法, 都在现有读取方抛出之前判定,
 * 被拒绝的形式在报 RangeError 之前不再经过 Java 异常 (R2). 与 [SelectorForms] 的其余部分 (Q66 (2)) 不同, 这些检查在异常相关的范围内复制了读取方的做法;
 * 每处复制都注明原位置, P5.2 删除现有模块时合并两份. 复制部分判断不了的仍交给读取方: 本检查未拒绝的正则照旧编译, 含非 ASCII 字符的罗盘照旧解析,
 * 都在 try 中进行 (JVM 与 Android 的正则引擎在这些地方有差异).
 */
internal object SelectorChecks {

    /** What a condition makes of its arguments. zh-CN: 条件对其参数的判定. */
    sealed class Verdict {

        /** An overload takes the arguments and builds nothing that throws. zh-CN: 有重载接受参数, 且构建过程不会抛出. */
        object Accepted : Verdict()

        /** No overload takes the arguments. zh-CN: 没有重载接受参数. */
        object NoOverload : Verdict()

        /**
         * The overload that takes the arguments refuses them, for [reason] (described when read: string resources).
         * zh-CN: 接受参数的重载拒绝了它们, 原因为 [reason] (读取时才生成: 可能取自字符串资源).
         */
        class Rejected(private val describe: () -> String) : Verdict() {
            val reason: String get() = describe()
        }

        /** The overload compiles a pattern this check does not refuse; compiling it tells. zh-CN: 重载会编译本检查未拒绝的正则, 由编译判定. */
        object Unsure : Verdict()

    }

    /** The message of `roundToInt` / `roundToLong` for NaN (kotlin.math). zh-CN: kotlin.math 的 roundToInt / roundToLong 对 NaN 的信息. */
    private const val NAN = "Cannot round NaN value."

    private const val ALGORITHM = "algorithm"

    /**
     * The conditions whose string argument is compiled as a pattern: `xxxMatches` and `xxxMatch` of
     * `SelectorRegistry.stringFamily` (`StringMatcher.matches` / `StringMatcher.match` with a string).
     */
    private val PATTERN_CONDITIONS: Set<String> = SelectorRegistry.names.filterTo(HashSet()) { it.endsWith("Matches") || it.endsWith("Match") }

    /** The escapes after which a pattern is no longer scanned (they take more than one character, or quote). */
    private const val OPAQUE_ESCAPES = "QEcpPNxuk"

    /**
     * [args] of [entry] as `SelectorEntry.resolve` resolves them, and as the overload it finds builds the condition:
     * only an integer argument that is NaN, the name of an unknown algorithm and a pattern that does not compile throw
     * there (appendix E "P3.3"). [lenient] as for the elements of an array in an object selector.
     *
     * zh-CN: 按 SelectorEntry.resolve 的方式解析 [entry] 的 [args], 并按其找到的重载构建条件: 那里会抛出的只有整数参数为 NaN, 未知的算法名与无法编译的正则.
     */
    fun condition(entry: SelectorEntry, args: List<Any?>, lenient: Boolean): Verdict {
        // SelectorEntry.resolve and SelectorOverload.coerce (core/automator/selector/SelectorEntry.kt), the first overload that fits.
        // zh-CN: 复制自 SelectorEntry.resolve 与 SelectorOverload.coerce, 取首个符合的重载.
        for (overload in entry.overloads) {
            if (overload.isVararg) {
                if (args.none { it == null }) return Verdict.Accepted
                continue
            }
            val params = overload.params
            if (args.size != params.size) continue
            var fits = true
            for (i in params.indices) {
                val type = params[i].type
                // SelectorArgType.INT rounds a number with JsArgumentCoercion.toInt, whose roundToInt throws for NaN.
                // zh-CN: SelectorArgType.INT 用 JsArgumentCoercion.toInt 取整, 其中 roundToInt 遇 NaN 抛出.
                if (type == SelectorArgType.INT && isNaN(args[i])) return Verdict.Rejected { NAN }
                if (type.coerce(args[i], lenient) == null) {
                    fits = false
                    break
                }
            }
            if (fits) return built(entry, params, args)
        }
        return Verdict.NoOverload
    }

    private fun isNaN(value: Any?) = value is Double && value.isNaN() || value is Float && value.isNaN()

    /** What building the condition of [entry] from the overload of [params] makes of [args]. */
    private fun built(entry: SelectorEntry, params: List<SelectorParam>, args: List<Any?>): Verdict {
        if (entry.name == ALGORITHM) {
            // UiSelector.useAlgorithm (core/accessibility/UiSelector.kt).
            val name = args[0].toString()
            return when {
                name.equals("BFS", true) || name.equals("DFS", true) -> Verdict.Accepted
                // The message of str(R.string.error_unknown_algorithm_selector_param, name), through the cached texts of the module layer.
                else -> Verdict.Rejected { ScriptMessages.resource(R.string.error_unknown_algorithm_selector_param, "Unknown algorithm selector param: %s", name) }
            }
        }
        var verdict: Verdict = Verdict.Accepted
        for (i in params.indices) {
            val compiled = when (params[i].type) {
                SelectorArgType.REGEX -> true
                SelectorArgType.STRING -> entry.name in PATTERN_CONDITIONS
                else -> false
            }
            if (!compiled) continue
            // StringMatcher compiles a RegExp from its string form, `/source/flags`, and a string as it is.
            // zh-CN: StringMatcher 由 RegExp 的字符串形式 (/source/flags) 编译, 字符串则原样编译.
            patternRefusal(args[i].toString())?.let { return Verdict.Rejected { it } }
            verdict = Verdict.Unsure
        }
        return verdict
    }

    /**
     * Why [input] does not compile as `JsRegexUtils.compileJsOrPlainRegex` compiles it, for the common slips only, or null
     * when it is not refused here (it may still not compile). A refusal holds for the engines of both the JVM and Android
     * (ICU): a quantifier with nothing to repeat at the start or after `(` / `|`, a trailing backslash, a `[` without a
     * `]` after it, a `)` without its `(` and a `(` without its `)`. The parentheses are only counted before the first
     * `[`, and nothing is refused after an escape that takes more than one character or a group construct other than
     * `(?:`, `(?=`, `(?!`, `(?<` and `(?>` (comments, quotes and flags differ between the engines). The descriptions are
     * those of `java.util.regex.PatternSyntaxException`.
     *
     * zh-CN: [input] 按 JsRegexUtils.compileJsOrPlainRegex 的方式无法编译的原因 (只覆盖常见笔误); 此处不拒绝时为 null (仍可能无法编译). 拒绝对 JVM 与
     * Android (ICU) 两种引擎都成立: 开头或 ( / | 之后没有可重复内容的量词, 结尾的反斜杠, 之后没有 ] 的 [, 没有对应 ( 的 ) 与没有对应 ) 的 (. 括号只在首个 [
     * 之前计数; 遇到占用多个字符的转义, 或 (?:, (?=, (?!, (?<, (?> 以外的组构造 (注释, 引用与标志在两种引擎间有差异) 后不再拒绝. 描述沿用
     * java.util.regex.PatternSyntaxException 的写法.
     */
    fun patternRefusal(input: String): String? {
        // JsRegexUtils.compileJsOrPlainRegex compiles the source of a literal (`/source/flags`), any other string as it is.
        val pattern = JsRegexUtils.parseJsRegexLiteral(input)?.first ?: input
        val n = pattern.length
        if (n == 0) return null
        if (pattern[0] == '*' || pattern[0] == '+' || pattern[0] == '?') return dangling(pattern, 0)
        var lastOpen = -1
        var closedAfter = false
        var depth = 0
        var i = 0
        while (i < n) {
            when (pattern[i]) {
                '\\' -> {
                    if (i + 1 == n) return "Unescaped trailing backslash near index $n"
                    if (pattern[i + 1] in OPAQUE_ESCAPES) return null
                    i += 2
                    continue
                }
                '[' -> {
                    lastOpen = i
                    closedAfter = false
                }
                ']' -> if (lastOpen >= 0) closedAfter = true
                // Before the first `[`, nothing is in a character class.
                '(' -> if (lastOpen < 0) {
                    when (pattern.getOrNull(i + 1)) {
                        '*', '+' -> return dangling(pattern, i + 1)
                        '?' -> if (pattern.getOrNull(i + 2).let { it == null || it !in ":=!<>" }) return null
                    }
                    depth++
                }
                // The index is that of the character before, as `java.util.regex.Pattern` reports it.
                ')' -> if (lastOpen < 0 && --depth < 0) return if (i > 0) "Unmatched closing ')' near index ${i - 1}" else "Unmatched closing ')'"
                '|' -> if (lastOpen < 0) {
                    when (pattern.getOrNull(i + 1)) {
                        '*', '+', '?' -> return dangling(pattern, i + 1)
                    }
                }
            }
            i++
        }
        return when {
            lastOpen >= 0 -> if (closedAfter) null else "Unclosed character class near index ${n - 1}"
            depth > 0 -> "Unclosed group near index $n"
            else -> null
        }
    }

    private fun dangling(pattern: String, index: Int) = "Dangling meta character '${pattern[index]}' near index $index"

    /**
     * Whether [value] is a compass string as `Compass.isCompass` tells it: [Compass.PASS_ON], the empty string or the
     * syntax `CompassParser.SYNTAX` (`(([pkc>]|s[<>]?)-?\d*)+`), scanned for an ASCII string.
     *
     * zh-CN: 按 Compass.isCompass 判断 [value] 是否为罗盘字符串: [Compass.PASS_ON], 空串或 CompassParser.SYNTAX 的语法, ASCII 字符串逐字符扫描.
     */
    fun isCompass(value: Any?): Boolean {
        if (value !is CharSequence) return false
        if (value == Compass.PASS_ON) return true
        return if (isAscii(value)) isCompassSyntax(value) else CompassParser.isCompass(value)
    }

    /**
     * Whether `UiObject.compass` takes [value] (no [Compass.PASS_ON]): `Compass.isCompass` and `CompassParser.parse`,
     * scanned for an ASCII string. `\d` of the regex engine of Android takes any Unicode digit, that of the JVM only
     * ASCII digits, so other strings go to the parser itself.
     *
     * zh-CN: UiObject.compass 是否接受 [value] (不含 [Compass.PASS_ON]): 即 Compass.isCompass 与 CompassParser.parse, ASCII 字符串逐字符扫描.
     * Android 正则引擎的 \d 匹配任意 Unicode 数字, JVM 的只匹配 ASCII 数字, 所以其他字符串交给解析器本身.
     */
    fun compassParses(value: CharSequence): Boolean {
        if (!isAscii(value)) {
            return Compass.isCompass(value) && try {
                CompassParser.parse(value)
                true
            } catch (e: CompassSyntaxException) {
                false
            }
        }
        return isCompassSyntax(value) && compassSegments(value)
    }

    private fun isAscii(s: CharSequence): Boolean {
        for (i in s.indices) if (s[i].code >= 0x80) return false
        return true
    }

    private fun isDigit(c: Char) = c in '0'..'9'

    /** `CompassParser.isCompass` (core/automator/pick/CompassParser.kt) for an ASCII string. */
    private fun isCompassSyntax(s: CharSequence): Boolean {
        val n = s.length
        var i = 0
        // One or more `([pkc>]|s[<>]?)-?\d*`: no character but these starts a unit, and `-` and digits belong to the unit before.
        while (i < n) {
            when (s[i]) {
                'p', 'k', 'c', '>' -> i++
                's' -> {
                    i++
                    if (i < n && (s[i] == '<' || s[i] == '>')) i++
                }
                else -> return false
            }
            if (i < n && s[i] == '-') i++
            while (i < n && isDigit(s[i])) i++
        }
        return true
    }

    /**
     * `CompassParser.parse` (core/automator/pick/CompassParser.kt) for an ASCII string: the segments `p[p\d]*`,
     * `c-?\d+([>c]?-?\d+)*`, `s[<>]?-?\d+` and `k[k\d]*` one after another, each number an `Int` (`toIntOrNull`).
     */
    private fun compassSegments(s: CharSequence): Boolean {
        val n = s.length
        var i = 0
        while (i < n) {
            when (s[i]) {
                'p', 'k' -> {
                    val letter = s[i]
                    i++
                    while (i < n) {
                        i = when {
                            s[i] == letter -> i + 1
                            isDigit(s[i]) -> number(s, i, i).takeIf { it >= 0 } ?: return false
                            else -> break
                        }
                    }
                }
                'c' -> {
                    i = number(s, i + 1, signed(s, i + 1)).takeIf { it >= 0 } ?: return false
                    while (i < n) {
                        val at = if (s[i] == '>' || s[i] == 'c') i + 1 else i
                        val digits = signed(s, at)
                        if (digits >= n || !isDigit(s[digits])) break
                        i = number(s, at, digits).takeIf { it >= 0 } ?: return false
                    }
                }
                's' -> {
                    val at = if (i + 1 < n && (s[i + 1] == '<' || s[i + 1] == '>')) i + 2 else i + 1
                    i = number(s, at, signed(s, at)).takeIf { it >= 0 } ?: return false
                }
                else -> return false
            }
        }
        return true
    }

    /** The index of the digits of a number at [at], after its `-` if any. */
    private fun signed(s: CharSequence, at: Int) = if (at < s.length && s[at] == '-') at + 1 else at

    /**
     * The end of the number from [start] (its `-`, if any) with its digits from [digits], or -1 when there are no
     * digits or the number is no `Int`.
     */
    private fun number(s: CharSequence, start: Int, digits: Int): Int {
        val limit = if (digits > start) 2147483648L else 2147483647L
        var value = 0L
        var i = digits
        while (i < s.length && isDigit(s[i])) {
            value = value * 10 + (s[i] - '0')
            if (value > limit) return -1
            i++
        }
        return if (i > digits) i else -1
    }

    /**
     * Whether `Invoker.Interceptor` throws converting [arguments] for a result type method [name]: `coerceNumber` of
     * `JsArgumentCoercion` (core/../rhino/JsArgumentCoercion.kt) rounds a number for an integer parameter, and its
     * `roundToInt` / `roundToLong` throw for NaN.
     *
     * zh-CN: Invoker.Interceptor 为结果类型方法 [name] 转换 [arguments] 时是否抛出: JsArgumentCoercion.coerceNumber 为整数参数取整, 其 roundToInt /
     * roundToLong 遇 NaN 抛出.
     */
    fun interceptorThrows(name: String, arguments: List<Any?>): Boolean {
        val types = Invoker.Interceptor.interceptedMap[name]?.args ?: return false
        return arguments.indices.any { i ->
            val value = arguments[i]
            value is Double && value.isNaN() && rounds(types.getOrNull(i))
        }
    }

    /** The parameter types `JsArgumentCoercion.coerceNumber` rounds a number for. zh-CN: coerceNumber 为之取整的参数类型. */
    private fun rounds(type: Class<*>?) = when (type) {
        Long::class.java, Long::class.javaObjectType,
        Int::class.java, Int::class.javaObjectType,
        Short::class.java, Short::class.javaObjectType,
        Byte::class.java, Byte::class.javaObjectType -> true
        else -> false
    }

}
