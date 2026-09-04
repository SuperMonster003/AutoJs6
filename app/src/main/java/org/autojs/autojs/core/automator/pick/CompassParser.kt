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
    fun isCompass(s: CharSequence): Boolean = s.isEmpty() || SYNTAX.matches(s)

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

    private fun String.toIntOrThrow(segment: String): Int = toIntOrNull() ?: throw CompassSyntaxException(segment)

}
