package org.autojs.autojs.theme.app

import android.content.Context
import org.autojs.autojs.theme.intelligent.GroupOrder
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntervalUnit
import org.autojs.autojs.theme.intelligent.RotationTrigger
import org.autojs.autojs.theme.intelligent.SortKey
import org.autojs.autojs.theme.intelligent.StateKeys
import org.autojs.autojs.theme.intelligent.StateSource
import org.autojs.autojs6.R

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
        return "[$head | $groupCount | ${orderLabel(context, config.order)}]"
    }

    /** Null for [StateSource.None]. zh-CN: [StateSource.None] 时为 null. */
    fun stateSourceLabel(context: Context, source: StateSource): String? = when (source) {
        StateSource.None -> null
        StateSource.NightMode -> context.getString(R.string.text_night_mode)
        is StateSource.Unsupported -> context.getString(R.string.text_unsupported)
    }

    fun triggerLabel(context: Context, trigger: RotationTrigger): String = when (trigger) {
        RotationTrigger.StateChange -> context.getString(R.string.text_rotation_state_change)
        RotationTrigger.AppStart -> context.getString(R.string.text_rotation_app_start)
        is RotationTrigger.Interval -> intervalLabel(context, trigger.every, trigger.unit)
        is RotationTrigger.Unsupported -> context.getString(R.string.text_unsupported)
    }

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

    fun stateLabel(context: Context, stateKey: String): String = when (stateKey) {
        StateKeys.DAY -> context.getString(R.string.text_state_day)
        StateKeys.NIGHT -> context.getString(R.string.text_state_night)
        else -> context.getString(R.string.text_state_source_none)
    }

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
