package org.autojs.autojs.core.automator.pick

/**
 * A single navigation step parsed from a compass string.
 * zh-CN: 从罗盘字符串解析出的单个导航步骤.
 *
 * Created by SuperMonster003 on Sep 4, 2026.
 */
sealed class CompassStep {

    /**
     * `p`, `p2`, `ppp`, `p4ppp12p`: go up [levels] levels.
     * zh-CN: 向上 [levels] 级.
     */
    data class Up(val levels: Int) : CompassStep()

    /**
     * `c0`, `c-1`, `c0>2>0`, `c0c2c0`: descend through children by index, negative indices count from the end.
     * zh-CN: 按索引逐级进入子节点, 负数索引从末尾计.
     */
    data class Children(val indices: List<Int>) : CompassStep()

    /**
     * `s2` / `s-2`: sibling by absolute index (negative from the end),
     * `s>2` / `s<2`: sibling by offset relative to the current index.
     * zh-CN: `s2` / `s-2` 为兄弟节点的绝对索引 (负数从末尾计), `s>2` / `s<2` 为相对当前索引的偏移.
     */
    data class Sibling(val mode: Mode, val value: Int) : CompassStep() {
        enum class Mode { ABSOLUTE, FORWARD, BACKWARD }
    }

    /**
     * `k`, `k2`: the first clickable node among the node itself and at most [maxLevels] ancestors.
     * zh-CN: 节点自身及至多 [maxLevels] 级祖先中首个可点击的节点.
     */
    data class Clickable(val maxLevels: Int) : CompassStep()

}

/**
 * Thrown when a compass string cannot be parsed, [rest] holds the unparsed remainder.
 * zh-CN: 罗盘字符串无法解析时抛出, [rest] 为未能解析的剩余部分.
 */
class CompassSyntaxException(val rest: String) : IllegalArgumentException("Invalid rest compass: $rest")

/**
 * What [CompassParser.tryParse] makes of a compass, as `UiObject.compass` would take it, without throwing.
 * zh-CN: [CompassParser.tryParse] 对罗盘的判定, 与 `UiObject.compass` 的取用方式一致, 不抛异常.
 */
sealed class CompassParse {

    /** [Compass.PASS_ON]: the node itself. zh-CN: [Compass.PASS_ON]: 节点自身. */
    object PassOn : CompassParse()

    /** The steps to walk (none for the empty string). zh-CN: 要行走的步骤 (空串为无步骤). */
    class Steps(val steps: List<CompassStep>) : CompassParse()

    /** Not the compass syntax at all (`UiObject.ensureCompass` refuses it). zh-CN: 根本不是罗盘语法 (`UiObject.ensureCompass` 拒绝). */
    class NotCompass(val compass: String) : CompassParse()

    /** The syntax, but [rest] does not parse (the [CompassSyntaxException] of [CompassParser.parse]). zh-CN: 符合语法, 但 [rest] 无法解析. */
    class BadRest(val rest: String) : CompassParse()

}

/**
 * Parser of the compass DSL used by `UiObject.compass`, free of Android dependencies.
 * zh-CN: `UiObject.compass` 所用罗盘 DSL 的解析器, 不依赖 Android.
 *
 * Grammar (segments are concatenated without separators):
 * - `p[p\d]*`: `p2` -> 2 levels, `ppp` -> 3 levels, `p4ppp12p` -> 4 + 2 + 12 + 1 = 19 levels
 * - `c-?\d+([>c]?-?\d+)*`: `c0>2>0` and `c0c2c0` both mean `.child(0).child(2).child(0)`
 * - `s[<>]?-?\d+`: `s2` absolute index, `s-2` from the end, `s>2` / `s<2` offset
 * - `k[k\d]*`: `k` / `k1` -> self or parent, `k2` -> self or up to 2 ancestors
 */
object CompassParser {

    private val SYNTAX = Regex("(([pkc>]|s[<>]?)-?\\d*)+")

    private val UP_SEGMENT = Regex("^p[p\\d]*")
    private val UP_TOKEN = Regex("p\\d+|p+(?!\\d)")

    private val CHILDREN_SEGMENT = Regex("^c-?\\d+([>c]?-?\\d+)*")
    private val NUMBER_TOKEN = Regex("-?\\d+")

    private val SIBLING_SEGMENT = Regex("^s[<>]?-?\\d+")

    private val CLICKABLE_SEGMENT = Regex("^k[k\\d]*")
    private val CLICKABLE_TOKEN = Regex("k\\d*")

    private val rules: List<Pair<Regex, (segment: String) -> CompassStep>> = listOf(
        UP_SEGMENT to { segment ->
            CompassStep.Up(UP_TOKEN.findAll(segment).sumOf { token ->
                token.value.let { if (it.length > 1 && it[1].isDigit()) it.substring(1).toIntOrThrow(segment) else it.length }
            })
        },
        CHILDREN_SEGMENT to { segment ->
            CompassStep.Children(NUMBER_TOKEN.findAll(segment).map { it.value.toIntOrThrow(segment) }.toList())
        },
        SIBLING_SEGMENT to { segment ->
            val mode = when {
                segment.contains('>') -> CompassStep.Sibling.Mode.FORWARD
                segment.contains('<') -> CompassStep.Sibling.Mode.BACKWARD
                else -> CompassStep.Sibling.Mode.ABSOLUTE
            }
            val value = NUMBER_TOKEN.find(segment)?.value?.toIntOrThrow(segment) ?: throw CompassSyntaxException(segment)
            CompassStep.Sibling(mode, value)
        },
        CLICKABLE_SEGMENT to { segment ->
            CompassStep.Clickable(CLICKABLE_TOKEN.findAll(segment).sumOf { token ->
                token.value.substring(1).let { if (it.isEmpty()) 1 else it.toIntOrThrow(segment) }
            })
        },
    )

    /**
     * Whether [s] is lexically a compass string. An empty string is a valid (no-op) compass.
     * zh-CN: [s] 是否在词法上是合法的罗盘字符串, 空字符串视为合法 (无操作).
     */
    fun isCompass(s: CharSequence): Boolean = s.isEmpty() || if (isAscii(s)) isSyntax(s) else SYNTAX.matches(s)

    /**
     * Parses [compass] into steps, throwing [CompassSyntaxException] at the first unparsable remainder.
     * zh-CN: 将 [compass] 解析为步骤列表, 遇到无法解析的剩余部分时抛出 [CompassSyntaxException].
     */
    @Throws(CompassSyntaxException::class)
    fun parse(compass: CharSequence): List<CompassStep> {
        val steps = ArrayList<CompassStep>()
        var rest = compass.toString()
        while (rest.isNotEmpty()) {
            val (segment, step) = rules.firstNotNullOfOrNull { (regex, factory) ->
                regex.find(rest)?.value?.let { segment -> segment to factory(segment) }
            } ?: throw CompassSyntaxException(rest)
            steps += step
            rest = rest.substring(segment.length)
        }
        return steps
    }

    /**
     * [compass] as `UiObject.compass` takes it ([Compass.PASS_ON], then [isCompass], then [parse]), told without an
     * exception (docs/selector/ROADMAP.md P1.1, P1.4: the compass scan of the former `SelectorChecks`). An ASCII string
     * is scanned by hand with the same segments, numbers and remainders as the regular expressions; any other string
     * goes to them (`\d` of the ICU engine of Android takes every Unicode digit, that of the JVM only ASCII ones).
     * zh-CN: 按 `UiObject.compass` 的取用方式 ([Compass.PASS_ON], 再 [isCompass], 再 [parse]) 判定 [compass], 不抛异常 (P1.1, P1.4:
     * 原 `SelectorChecks` 的罗盘扫描). ASCII 字符串手工扫描, 段落, 数值与剩余部分都与正则一致; 其他字符串交给正则 (Android ICU 引擎的 \d 匹配任意
     * Unicode 数字, JVM 的只匹配 ASCII 数字).
     */
    fun tryParse(compass: CharSequence): CompassParse {
        if (compass == Compass.PASS_ON) return CompassParse.PassOn
        if (!isAscii(compass)) {
            if (!isCompass(compass)) return CompassParse.NotCompass(compass.toString())
            return try {
                CompassParse.Steps(parse(compass))
            } catch (e: CompassSyntaxException) {
                CompassParse.BadRest(e.rest)
            }
        }
        val s = compass.toString()
        if (!isSyntax(s)) return CompassParse.NotCompass(s)
        val steps = ArrayList<CompassStep>()
        var i = 0
        while (i < s.length) {
            val end = segmentEnd(s, i)
            if (end < 0) return CompassParse.BadRest(s.substring(i))
            steps += step(s, i, end) ?: return CompassParse.BadRest(s.substring(i, end))
            i = end
        }
        return CompassParse.Steps(steps)
    }

    private fun isAscii(s: CharSequence): Boolean {
        for (i in s.indices) if (s[i].code >= 0x80) return false
        return true
    }

    private fun isDigit(c: Char) = c in '0'..'9'

    /** [SYNTAX] (`(([pkc>]|s[<>]?)-?\d*)+`) for an ASCII string. zh-CN: ASCII 字符串的 [SYNTAX] 判断. */
    private fun isSyntax(s: CharSequence): Boolean {
        val n = s.length
        var i = 0
        // One or more units: no character but these starts a unit, and `-` and digits belong to the unit before.
        // zh-CN: 一个或多个单元: 只有这些字符能开始一个单元, `-` 与数字属于前一个单元.
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

    private fun digitsEnd(s: String, from: Int): Int {
        var i = from
        while (i < s.length && isDigit(s[i])) i++
        return i
    }

    /**
     * The end of the segment that starts at [i] as the anchored rules match it (`p[p\d]*`, `c-?\d+([>c]?-?\d+)*`,
     * `s[<>]?-?\d+`, `k[k\d]*`), or -1 when none matches there.
     */
    private fun segmentEnd(s: String, i: Int): Int {
        val n = s.length
        return when (s[i]) {
            'p', 'k' -> {
                var j = i + 1
                while (j < n && (s[j] == s[i] || isDigit(s[j]))) j++
                j
            }
            'c' -> {
                var j = if (i + 1 < n && s[i + 1] == '-') i + 2 else i + 1
                if (j >= n || !isDigit(s[j])) return -1
                j = digitsEnd(s, j)
                while (true) {
                    var k = if (j < n && (s[j] == '>' || s[j] == 'c')) j + 1 else j
                    if (k < n && s[k] == '-') k++
                    if (k >= n || !isDigit(s[k])) break
                    j = digitsEnd(s, k)
                }
                j
            }
            's' -> {
                var j = if (i + 1 < n && (s[i + 1] == '<' || s[i + 1] == '>')) i + 2 else i + 1
                if (j < n && s[j] == '-') j++
                if (j >= n || !isDigit(s[j])) -1 else digitsEnd(s, j)
            }
            else -> -1
        }
    }

    /** The step of the segment [start] until [end] as the factories of [rules] make it, or null where they throw. */
    private fun step(s: String, start: Int, end: Int): CompassStep? {
        // The numbers of a segment are its `-?\d+` tokens (a `p` / `k` token takes the digits after its letter).
        // zh-CN: 段落中的数值是其 -?\d+ 记号 (p / k 记号取其字母之后的数字).
        fun number(from: Int, to: Int): Int? = s.substring(from, to).toIntOrNull()
        return when (s[start]) {
            'p', 'k' -> {
                var sum = 0
                var j = start
                while (j < end) {
                    j++
                    val digits = digitsEnd(s, j)
                    sum += if (digits > j) number(j, digits) ?: return null else 1
                    j = digits
                }
                if (s[start] == 'p') CompassStep.Up(sum) else CompassStep.Clickable(sum)
            }
            'c' -> {
                val indices = ArrayList<Int>()
                var j = start + 1
                while (j < end) {
                    if (!isDigit(s[j])) {
                        j++
                        continue
                    }
                    val from = if (s[j - 1] == '-') j - 1 else j
                    val digits = digitsEnd(s, j)
                    indices += number(from, digits) ?: return null
                    j = digits
                }
                CompassStep.Children(indices)
            }
            else -> {
                val mode = when (s[start + 1]) {
                    '>' -> CompassStep.Sibling.Mode.FORWARD
                    '<' -> CompassStep.Sibling.Mode.BACKWARD
                    else -> CompassStep.Sibling.Mode.ABSOLUTE
                }
                val first = (start + 1 until end).first { isDigit(s[it]) }
                CompassStep.Sibling(mode, number(if (s[first - 1] == '-') first - 1 else first, end) ?: return null)
            }
        }
    }

    private fun String.toIntOrThrow(segment: String): Int = toIntOrNull() ?: throw CompassSyntaxException(segment)

}
