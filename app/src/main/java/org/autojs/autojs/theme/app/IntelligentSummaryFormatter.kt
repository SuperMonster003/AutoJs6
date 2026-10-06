package org.autojs.autojs.theme.app

import android.content.Context
import org.autojs.autojs.theme.intelligent.GroupOrder
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntervalUnit
import org.autojs.autojs.theme.intelligent.RotationTrigger
import org.autojs.autojs.theme.intelligent.Season
import org.autojs.autojs.theme.intelligent.SortKey
import org.autojs.autojs.theme.intelligent.StateKeys
import org.autojs.autojs.theme.intelligent.StateSource
import org.autojs.autojs6.R
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Localized labels for intelligent libraries, and the card summary of roadmap 4.8,
 * e.g. `[Night mode + Every 1 hour | 3/4 groups | Sequential (Hue)]`.
 * zh-CN: 智能库的本地化标签, 以及 Roadmap 4.8 的卡片摘要, 例如 `[夜间模式 + 每 1 小时 | 3/4 组 | 顺序 (色相)]`.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object IntelligentSummaryFormatter {

    private const val MAX_LISTED_TRIGGERS = 2

    fun summary(context: Context, config: IntelligentConfig, groups: List<IntelligentGroup>): String {
        val source = stateSourceLabel(context, config.stateSource)
        // A state change is implied by the state source. zh-CN: 状态变化由状态源隐含.
        val triggers = config.rotation.filterNot { it == RotationTrigger.StateChange }.map { triggerLabel(context, it) }
        val listedTriggers = when {
            triggers.size <= MAX_LISTED_TRIGGERS -> triggers.joinToString(" + ")
            else -> triggers.take(MAX_LISTED_TRIGGERS).joinToString(" + ") + " +${triggers.size - MAX_LISTED_TRIGGERS}"
        }
        val head = when {
            source == null && triggers.isEmpty() -> context.getString(R.string.text_state_source_none)
            source == null -> listedTriggers
            triggers.isEmpty() -> source
            else -> "$source + $listedTriggers"
        }
        val enabled = groups.count { it.enabled }
        val groupCount = when (enabled) {
            groups.size -> context.resources.getQuantityString(R.plurals.text_n_groups, groups.size, groups.size)
            else -> context.getString(R.string.text_groups_enabled_of_total, enabled, groups.size)
        }
        val order = orderLabel(context, config.order)
        val overrides = config.overrides.size.takeIf { it > 0 }?.let { " + " + context.resources.getQuantityString(R.plurals.text_n_overrides, it, it) }.orEmpty()
        return "[$head | $groupCount | $order$overrides]"
    }

    /** Null for [StateSource.None]. zh-CN: [StateSource.None] 时为 null. */
    fun stateSourceLabel(context: Context, source: StateSource): String? = when (source) {
        StateSource.None -> null
        StateSource.NightMode -> context.getString(R.string.text_night_mode)
        is StateSource.TimePeriods -> context.getString(R.string.text_state_source_time_periods)
        is StateSource.SunriseSunset -> context.getString(R.string.text_state_source_sunrise_sunset)
        is StateSource.Weekday -> context.getString(if (source.workdaysOnly) R.string.text_state_source_workdays else R.string.text_state_source_weekday)
        is StateSource.Seasons -> context.getString(R.string.text_state_source_seasons)
        is StateSource.Battery -> context.getString(R.string.text_state_source_battery)
        StateSource.Charging -> context.getString(R.string.text_state_source_charging)
        StateSource.PowerSave -> context.getString(R.string.text_state_source_power_save)
        StateSource.AccessibilityService -> context.getString(R.string.text_state_source_accessibility_service)
        StateSource.ScriptRunning -> context.getString(R.string.text_state_source_script_running)
        is StateSource.Unsupported -> context.getString(R.string.text_unsupported)
    }

    fun triggerLabel(context: Context, trigger: RotationTrigger): String = when (trigger) {
        RotationTrigger.StateChange -> context.getString(R.string.text_rotation_state_change)
        RotationTrigger.AppStart -> context.getString(R.string.text_rotation_app_start)
        is RotationTrigger.Interval -> intervalLabel(context, trigger.every, trigger.unit)
        is RotationTrigger.Daily -> dailyLabel(context, trigger.times)
        is RotationTrigger.Unsupported -> context.getString(R.string.text_unsupported)
    }

    /** "Daily at 08:00, 20:00", or a count for more than two times. zh-CN: "每天 08:00, 20:00"; 超过两个时刻时显示数量. */
    fun dailyLabel(context: Context, times: List<Int>): String = when {
        times.size <= 2 -> context.getString(R.string.text_daily_at, times.joinToString(", ", transform = ::formatMinutes))
        else -> context.resources.getQuantityString(R.plurals.text_n_times_a_day, times.size, times.size)
    }

    /** Minutes of the day as `HH:mm`. zh-CN: 一天中的分钟数, 格式为 `HH:mm`. */
    fun formatMinutes(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun intervalLabel(context: Context, every: Int, unit: IntervalUnit): String {
        val pluralRes = when (unit) {
            IntervalUnit.SECOND -> R.plurals.text_every_n_seconds
            IntervalUnit.MINUTE -> R.plurals.text_every_n_minutes
            IntervalUnit.HOUR -> R.plurals.text_every_n_hours
        }
        return context.resources.getQuantityString(pluralRes, every, every)
    }

    fun orderLabel(context: Context, order: GroupOrder): String = when (order) {
        GroupOrder.Random -> context.getString(R.string.text_order_random)
        is GroupOrder.Sequential -> when (order.key) {
            SortKey.MANUAL -> context.getString(R.string.text_order_sequential)
            else -> context.getString(R.string.text_order_sequential_by, sortKeyLabel(context, order.key))
        }
    }

    fun sortKeyLabel(context: Context, key: SortKey): String = context.getString(
        when (key) {
            SortKey.MANUAL -> R.string.text_sort_key_manual
            SortKey.CREATED -> R.string.text_sort_key_created
            SortKey.MODIFIED -> R.string.text_sort_key_modified
            SortKey.NAME -> R.string.text_sort_key_name
            SortKey.LIGHTNESS -> R.string.text_sort_key_lightness
            SortKey.HUE -> R.string.text_sort_key_hue
            SortKey.CHROMA -> R.string.text_sort_key_chroma
        }
    )

    /**
     * The label of [stateKey] under [source], such as "06:00 - 18:00", "Monday", "20%-79%" or "Charging".
     * zh-CN: [source] 下 [stateKey] 的标签, 例如 "06:00 - 18:00", "星期一", "20%-79%" 或 "充电中".
     */
    fun stateLabel(context: Context, source: StateSource, stateKey: String): String {
        val index = stateKey.substringAfter(':', "").toIntOrNull()
        return when {
            stateKey == StateKeys.DAY -> context.getString(R.string.text_state_day)
            stateKey == StateKeys.NIGHT -> context.getString(R.string.text_state_night)
            stateKey == StateKeys.WORKDAY -> context.getString(R.string.text_state_workday)
            stateKey == StateKeys.WEEKEND -> context.getString(R.string.text_state_weekend)
            stateKey == StateKeys.ON || stateKey == StateKeys.OFF -> onOffLabel(context, source, stateKey == StateKeys.ON)
            stateKey.startsWith("period:") && index != null && source is StateSource.TimePeriods -> {
                val starts = source.starts
                if (index !in starts.indices) stateKey else "${formatMinutes(starts[index])} - ${formatMinutes(starts[(index + 1) % starts.size])}"
            }
            stateKey.startsWith("weekday:") && index != null && index in 1..7 -> {
                DayOfWeek.of(index).getDisplayName(TextStyle.FULL, context.resources.configuration.locales[0])
            }
            stateKey.startsWith("season:") -> Season.entries.firstOrNull { StateKeys.season(it) == stateKey }?.let { seasonLabel(context, it) } ?: stateKey
            stateKey.startsWith("battery:") && index != null && source is StateSource.Battery -> {
                val from = source.thresholds.getOrNull(index - 1) ?: 0
                val to = source.thresholds.getOrNull(index)?.minus(1) ?: 100
                "$from%-$to%"
            }
            else -> context.getString(R.string.text_state_color_single)
        }
    }

    private fun onOffLabel(context: Context, source: StateSource, on: Boolean): String = context.getString(
        when (source) {
            StateSource.Charging -> if (on) R.string.text_state_charging else R.string.text_state_not_charging
            StateSource.AccessibilityService, StateSource.ScriptRunning -> if (on) R.string.text_state_running else R.string.text_not_running
            else -> if (on) R.string.text_enabled else R.string.text_disabled
        }
    )

    fun seasonLabel(context: Context, season: Season): String = context.getString(
        when (season) {
            Season.SPRING -> R.string.text_season_spring
            Season.SUMMER -> R.string.text_season_summer
            Season.AUTUMN -> R.string.text_season_autumn
            Season.WINTER -> R.string.text_season_winter
        }
    )

    /**
     * Colors of [group] in state order, falling back like the engine does; used for swatches.
     * zh-CN: 按状态顺序排列的 [group] 颜色, 回退规则与引擎一致; 用于色块显示.
     */
    fun stateColors(config: IntelligentConfig, group: IntelligentGroup): List<Int> {
        return config.stateKeys.mapNotNull { key -> group.resolve(key, config.stateKeys)?.primary }.distinct().ifEmpty {
            listOfNotNull(group.states.values.firstNotNullOfOrNull { it.primary })
        }
    }

    fun groupName(config: IntelligentConfig, group: IntelligentGroup): String {
        return group.name?.takeIf { it.isNotBlank() }
            ?: stateColors(config, group).joinToString(" / ") { String.format("#%06X", 0xFFFFFF and it) }.ifEmpty { "-" }
    }

}
