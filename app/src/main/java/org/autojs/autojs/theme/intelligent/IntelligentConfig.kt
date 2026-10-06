package org.autojs.autojs.theme.intelligent

/**
 * Configuration of an intelligent color library: exactly one state source, any number of rotation
 * triggers, a group order and ordered date overrides. Groups are kept separately (see [IntelligentGroup]).
 * zh-CN: 智能颜色库的配置: 恰好一个状态源, 任意个换组时机, 组的切换方式, 以及有序的日期覆盖层. 组单独保存 (见 [IntelligentGroup]).
 *
 * Types unknown to this app version (from a newer version or an import) are kept as `Unsupported`
 * values carrying their raw JSON, so they survive a round trip and can be reported to the user.
 * zh-CN: 当前版本不认识的类型 (来自更新的版本或导入文件) 以携带原始 JSON 的 `Unsupported` 值保留,
 * 保证往返不丢失, 并可提示给用户.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
data class IntelligentConfig(
    val stateSource: StateSource = StateSource.None,
    val rotation: List<RotationTrigger> = emptyList(),
    val order: GroupOrder = GroupOrder.Sequential(),
    val overrides: List<DateOverride> = emptyList(),
) {

    val stateKeys: List<String>
        get() = stateSource.stateKeys

    /** The first interval trigger; the editor allows only one. zh-CN: 第一个时间间隔换组时机; 编辑器只允许一个. */
    val interval: RotationTrigger.Interval?
        get() = rotation.firstNotNullOfOrNull { it as? RotationTrigger.Interval }

    val hasUnsupportedItems: Boolean
        get() = stateSource is StateSource.Unsupported
                || rotation.any { it is RotationTrigger.Unsupported }
                || overrides.any { it.condition is DateCondition.Unsupported }

    companion object {
        const val VERSION = 1
    }

}

object StateKeys {
    const val DEFAULT = "default"
    const val DAY = "day"
    const val NIGHT = "night"
}

/**
 * Decides which color of a group is used. Its [stateKeys] are stable identifiers in display order.
 * zh-CN: 决定使用组内哪个颜色. [stateKeys] 为按显示顺序排列的稳定标识.
 */
sealed interface StateSource {

    val stateKeys: List<String>

    data object None : StateSource {
        override val stateKeys = listOf(StateKeys.DEFAULT)
    }

    data object NightMode : StateSource {
        override val stateKeys = listOf(StateKeys.DAY, StateKeys.NIGHT)
    }

    data class Unsupported(val type: String, val raw: String) : StateSource {
        override val stateKeys = listOf(StateKeys.DEFAULT)
    }

}

/**
 * Decides when the library moves on to the next group.
 * zh-CN: 决定何时切换到下一组.
 */
sealed interface RotationTrigger {

    data object StateChange : RotationTrigger

    data object AppStart : RotationTrigger

    /**
     * Fires every [every] [unit]s on a grid that starts at [anchor] (see [IntervalSchedule]).
     * zh-CN: 以 [anchor] 为起点, 每 [every] 个 [unit] 触发一次 (见 [IntervalSchedule]).
     */
    data class Interval(val every: Int, val unit: IntervalUnit, val anchor: IntervalAnchor = IntervalAnchor.NOW) : RotationTrigger {
        val periodMillis: Long
            get() = every.coerceAtLeast(1) * unit.millis
    }

    data class Unsupported(val type: String, val raw: String) : RotationTrigger

}

enum class IntervalUnit(val key: String, val millis: Long) {
    SECOND("second", 1_000L),
    MINUTE("minute", 60_000L),
    HOUR("hour", 3_600_000L);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * When the first interval trigger happens after arming: one period from now, or the next whole minute / hour.
 * zh-CN: 启用后第一次触发的时机: 从现在起一个周期后, 或下一个整分 / 整时.
 */
enum class IntervalAnchor(val key: String) {
    NOW("now"),
    NEXT_MINUTE("nextMinute"),
    NEXT_HOUR("nextHour");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

sealed interface GroupOrder {

    /**
     * Cycles through groups sorted by [key]. Color keys read the color of [referenceState]
     * (the first state when null or unknown).
     * zh-CN: 按 [key] 排序后循环切换. 色值类排序键读取 [referenceState] 状态的颜色 (为空或未知时取首个状态).
     */
    data class Sequential(
        val key: SortKey = SortKey.MANUAL,
        val referenceState: String? = null,
        val descending: Boolean = false,
    ) : GroupOrder

    /** Picks uniformly among available groups, never the current one when there is a choice. zh-CN: 在可用组中等概率选取, 有其他选择时不选当前组. */
    data object Random : GroupOrder

}

enum class SortKey(val key: String) {
    MANUAL("manual"),
    CREATED("created"),
    MODIFIED("modified"),
    NAME("name"),
    LIGHTNESS("lightness"),
    HUE("hue"),
    CHROMA("chroma");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * While [condition] holds, the library shows [groupId] instead of its current group.
 * zh-CN: [condition] 成立期间, 库改用 [groupId] 而非当前组.
 */
data class DateOverride(val condition: DateCondition, val groupId: Long)

sealed interface DateCondition {

    /**
     * An inclusive month-day range that wraps across the year end when the start is after the end.
     * Repeats every year when [year] is null; otherwise applies once, starting in [year].
     * zh-CN: 包含两端的月-日区间, 起点晚于终点时跨年. [year] 为空时每年重复, 否则只在从 [year] 开始的那一次生效.
     */
    data class DateRange(
        val fromMonth: Int,
        val fromDay: Int,
        val toMonth: Int,
        val toDay: Int,
        val year: Int? = null,
    ) : DateCondition

    data class Unsupported(val type: String, val raw: String) : DateCondition

}

/**
 * A group of an intelligent library: one color set per state. A state without a primary color
 * uses the first state, in state order, that has one.
 * zh-CN: 智能库中的一个组: 每个状态一组颜色. 没有主色的状态, 使用按状态顺序第一个有主色的状态.
 */
data class IntelligentGroup(
    val id: Long,
    val name: String? = null,
    val enabled: Boolean = true,
    val position: Int = 0,
    val createdTime: Long = 0L,
    val modifiedTime: Long = 0L,
    val states: Map<String, StateColors> = emptyMap(),
) {

    val hasColor: Boolean
        get() = states.values.any { it.primary != null }

    fun resolve(stateKey: String, stateOrder: List<String>): EffectiveColor? {
        val colors = states[stateKey]?.takeIf { it.primary != null }
            ?: stateOrder.firstNotNullOfOrNull { key -> states[key]?.takeIf { it.primary != null } }
            ?: states.values.firstOrNull { it.primary != null }
            ?: return null
        return EffectiveColor(colors.primary!!, colors.accent, colors.dark)
    }

}

data class StateColors(val primary: Int?, val accent: Int? = null, val dark: Int? = null)

/**
 * Colors chosen by the engine. Null [accent] / [dark] defer to the global derivation setting.
 * zh-CN: 引擎选出的颜色. [accent] / [dark] 为空时交由全局派生配色设置决定.
 */
data class EffectiveColor(val primary: Int, val accent: Int? = null, val dark: Int? = null)
