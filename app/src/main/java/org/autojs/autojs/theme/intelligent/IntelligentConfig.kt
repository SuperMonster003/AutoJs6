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

    /** The first daily trigger; the editor allows only one. zh-CN: 第一个每日定时换组时机; 编辑器只允许一个. */
    val daily: RotationTrigger.Daily?
        get() = rotation.firstNotNullOfOrNull { it as? RotationTrigger.Daily }

    val hasUnsupportedItems: Boolean
        get() = stateSource is StateSource.Unsupported
                || rotation.any { it is RotationTrigger.Unsupported }
                || overrides.any { it.condition is DateCondition.Unsupported }

    /**
     * Rewrites the group references of the overrides through [mapping] (old id to new id), dropping overrides
     * whose group is not in it. Used when groups get new ids, as in cloning, saving new groups and importing.
     * zh-CN: 按 [mapping] (旧 id 到新 id) 改写覆盖层引用的组, 不在映射中的覆盖层被移除. 用于组获得新 id 的场合, 如克隆, 保存新组与导入.
     */
    fun remapGroupIds(mapping: Map<Long, Long>): IntelligentConfig {
        if (overrides.isEmpty()) return this
        return copy(overrides = overrides.mapNotNull { override -> mapping[override.groupId]?.let { override.copy(groupId = it) } })
    }

    companion object {
        const val VERSION = 1
    }

}

object StateKeys {
    const val DEFAULT = "default"
    const val DAY = "day"
    const val NIGHT = "night"
    const val OFF = "off"
    const val ON = "on"
    const val WORKDAY = "workday"
    const val WEEKEND = "weekend"

    fun period(index: Int) = "period:$index"

    /** ISO day of week, 1 (Monday) to 7 (Sunday). zh-CN: ISO 星期, 1 (星期一) 至 7 (星期日). */
    fun weekday(isoDay: Int) = "weekday:$isoDay"

    fun season(season: Season) = "season:${season.key}"

    fun battery(index: Int) = "battery:$index"

    /** By position, so renaming a script state keeps its colors. zh-CN: 按位置编号, 重命名脚本状态时其颜色保持不变. */
    fun script(index: Int) = "script:$index"
}

enum class Season(val key: String) {
    SPRING("spring"), SUMMER("summer"), AUTUMN("autumn"), WINTER("winter")
}

enum class Hemisphere(val key: String) {
    NORTH("north"), SOUTH("south");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** A place on Earth in degrees; north and east are positive. zh-CN: 以度表示的地点; 北纬与东经为正. */
data class GeoLocation(val latitude: Double, val longitude: Double) {
    val hemisphere get() = if (latitude < 0) Hemisphere.SOUTH else Hemisphere.NORTH
}

/**
 * Decides which color of a group is used. Its [stateKeys] are stable identifiers in display order.
 * Sources sharing a meaning share keys (day / night, off / on), so colors carry over when the source changes.
 * zh-CN: 决定使用组内哪个颜色. [stateKeys] 为按显示顺序排列的稳定标识.
 * 含义相同的状态源共用 key (日 / 夜, 关 / 开), 因此更换状态源时颜色可以沿用.
 */
sealed interface StateSource {

    val stateKeys: List<String>

    data object None : StateSource {
        override val stateKeys = listOf(StateKeys.DEFAULT)
    }

    data object NightMode : StateSource {
        override val stateKeys = listOf(StateKeys.DAY, StateKeys.NIGHT)
    }

    /**
     * The day split into periods starting at [starts] (minutes of the day, ascending, 2 to 6 of them); the last
     * period runs past midnight into the first start (roadmap P3.1).
     * zh-CN: 按 [starts] (一天中的分钟数, 升序, 2 至 6 个) 划分的时段; 最后一个时段跨过午夜持续到第一个起始时刻 (Roadmap P3.1).
     */
    data class TimePeriods(val starts: List<Int>) : StateSource {
        override val stateKeys get() = starts.indices.map(StateKeys::period)

        companion object {
            const val MIN_PERIODS = 2
            const val MAX_PERIODS = 6
            val DEFAULT = TimePeriods(listOf(6 * 60, 18 * 60))

            /** Sorted, distinct and within a day, or null when fewer than 2 remain. zh-CN: 排序, 去重并限定在一天之内; 不足 2 个时返回 null. */
            fun normalized(starts: List<Int>): TimePeriods? {
                val valid = starts.filter { it in 0 until 24 * 60 }.distinct().sorted().take(MAX_PERIODS)
                return if (valid.size >= MIN_PERIODS) TimePeriods(valid) else null
            }
        }
    }

    /**
     * Daytime or night by sunrise and sunset at [location]; null infers it from the time zone (D20, roadmap P3.1).
     * zh-CN: 按 [location] 的日出日落区分白天与夜晚; 为 null 时由时区推断 (D20, Roadmap P3.1).
     */
    data class SunriseSunset(val location: GeoLocation? = null) : StateSource {
        override val stateKeys = listOf(StateKeys.DAY, StateKeys.NIGHT)
    }

    /**
     * Each day of the week, or workdays (Monday to Friday) and the weekend (roadmap P3.2).
     * zh-CN: 一周中的每一天, 或工作日 (星期一至星期五) 与周末 (Roadmap P3.2).
     */
    data class Weekday(val workdaysOnly: Boolean = false) : StateSource {
        override val stateKeys
            get() = if (workdaysOnly) listOf(StateKeys.WORKDAY, StateKeys.WEEKEND) else (1..7).map(StateKeys::weekday)
    }

    /**
     * Meteorological seasons (spring is March to May in the north); null [hemisphere] infers it from the time zone (D20, roadmap P3.2).
     * zh-CN: 气象季节 (北半球春季为 3 至 5 月); [hemisphere] 为 null 时由时区推断 (D20, Roadmap P3.2).
     */
    data class Seasons(val hemisphere: Hemisphere? = null) : StateSource {
        override val stateKeys = Season.entries.map(StateKeys::season)
    }

    /**
     * Battery level ranges split at [thresholds] (percentages, ascending, 1 to 4 of them), with 2 points of
     * hysteresis when leaving a range (roadmap P3.3).
     * zh-CN: 以 [thresholds] (百分比, 升序, 1 至 4 个) 划分的电量区间, 离开当前区间时有 2 个百分点的迟滞 (Roadmap P3.3).
     */
    data class Battery(val thresholds: List<Int>) : StateSource {
        override val stateKeys get() = (0..thresholds.size).map(StateKeys::battery)

        companion object {
            const val MAX_THRESHOLDS = 4
            val DEFAULT = Battery(listOf(20))

            fun normalized(thresholds: List<Int>): Battery? {
                val valid = thresholds.filter { it in 1..99 }.distinct().sorted().take(MAX_THRESHOLDS)
                return if (valid.isNotEmpty()) Battery(valid) else null
            }
        }
    }

    /** Whether the device is charging (roadmap P3.3). zh-CN: 设备是否正在充电 (Roadmap P3.3). */
    data object Charging : StateSource {
        override val stateKeys = listOf(StateKeys.OFF, StateKeys.ON)
    }

    /** Whether battery saver is on (roadmap P3.3). zh-CN: 省电模式是否开启 (Roadmap P3.3). */
    data object PowerSave : StateSource {
        override val stateKeys = listOf(StateKeys.OFF, StateKeys.ON)
    }

    /** Whether the AutoJs6 accessibility service is running (roadmap P3.3). zh-CN: AutoJs6 无障碍服务是否正在运行 (Roadmap P3.3). */
    data object AccessibilityService : StateSource {
        override val stateKeys = listOf(StateKeys.OFF, StateKeys.ON)
    }

    /** Whether any script is running (roadmap P3.3). zh-CN: 是否有脚本正在运行 (Roadmap P3.3). */
    data object ScriptRunning : StateSource {
        override val stateKeys = listOf(StateKeys.OFF, StateKeys.ON)
    }

    /**
     * Named states (2 to 8) that scripts switch with `colors.theme.state(name)`; the first one applies until a script
     * sets another (roadmap P6.2).
     * zh-CN: 由脚本通过 `colors.theme.state(name)` 切换的命名状态 (2 至 8 个); 脚本设置其他状态之前使用第一个 (Roadmap P6.2).
     */
    data class ScriptStates(val names: List<String>) : StateSource {
        override val stateKeys get() = names.indices.map(StateKeys::script)

        companion object {
            const val MIN_STATES = 2
            const val MAX_STATES = 8
            const val MAX_NAME_LENGTH = 32
            val DEFAULT = ScriptStates(listOf("idle", "busy"))

            /** Trimmed, distinct and not blank, or null when fewer than 2 remain. zh-CN: 去除首尾空白, 去重且非空; 不足 2 个时返回 null. */
            fun normalized(names: List<String>): ScriptStates? {
                val valid = names.map { it.trim() }.filter { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }.distinct().take(MAX_STATES)
                return if (valid.size >= MIN_STATES) ScriptStates(valid) else null
            }
        }
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
     * Fires when a script starts running (roadmap P6.2); `colors.theme.next()` rotates without it.
     * zh-CN: 在脚本开始运行时触发 (Roadmap P6.2); `colors.theme.next()` 不依赖此时机.
     */
    data object ScriptStart : RotationTrigger

    /**
     * Fires every [every] [unit]s on a grid that starts at [anchor] (see [IntervalSchedule]).
     * zh-CN: 以 [anchor] 为起点, 每 [every] 个 [unit] 触发一次 (见 [IntervalSchedule]).
     */
    data class Interval(val every: Int, val unit: IntervalUnit, val anchor: IntervalAnchor = IntervalAnchor.NOW) : RotationTrigger {
        val periodMillis: Long
            get() = every.coerceAtLeast(1) * unit.millis

        companion object {

            const val MAX_EVERY = 9999

            /**
             * Clamps [every] to what the editor accepts: at least [IntervalUnit.minimumEvery] (5 seconds, D19) and at most [MAX_EVERY].
             * zh-CN: 将 [every] 限制在编辑器接受的范围内: 不小于 [IntervalUnit.minimumEvery] (5 秒, D19), 不大于 [MAX_EVERY].
             */
            fun normalizedEvery(every: Int, unit: IntervalUnit) = every.coerceIn(unit.minimumEvery, MAX_EVERY)

        }
    }

    /**
     * Fires every day at [times] (minutes of the day, ascending, 1 to [MAX_TIMES] of them), local time (roadmap P3.4).
     * zh-CN: 每天在 [times] (一天中的分钟数, 升序, 1 至 [MAX_TIMES] 个) 的本地时刻触发 (Roadmap P3.4).
     */
    data class Daily(val times: List<Int>) : RotationTrigger {
        companion object {
            const val MAX_TIMES = 12

            fun normalized(times: List<Int>): Daily? {
                val valid = times.filter { it in 0 until 24 * 60 }.distinct().sorted().take(MAX_TIMES)
                return if (valid.isNotEmpty()) Daily(valid) else null
            }
        }
    }

    data class Unsupported(val type: String, val raw: String) : RotationTrigger

}

enum class IntervalUnit(val key: String, val millis: Long) {
    SECOND("second", 1_000L),
    MINUTE("minute", 60_000L),
    HOUR("hour", 3_600_000L);

    /** The smallest count of this unit not below [IntervalSchedule.MIN_PERIOD_MILLIS]. zh-CN: 不低于 [IntervalSchedule.MIN_PERIOD_MILLIS] 的最小数量. */
    val minimumEvery: Int
        get() = ((IntervalSchedule.MIN_PERIOD_MILLIS + millis - 1) / millis).toInt().coerceAtLeast(1)

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

    /**
     * A holiday of `HolidayCalendar` by [id], widened by [daysBefore] and [daysAfter] (roadmap P3.5).
     * zh-CN: `HolidayCalendar` 中以 [id] 标识的节日, 向前扩展 [daysBefore] 天, 向后扩展 [daysAfter] 天 (Roadmap P3.5).
     */
    data class Holiday(val id: String, val daysBefore: Int = 0, val daysAfter: Int = 0) : DateCondition {
        companion object {
            const val MAX_EXTRA_DAYS = 30
        }
    }

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
