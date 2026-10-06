package org.autojs.autojs.theme.intelligent

/**
 * Checks an intelligent library for the situations listed in roadmap 4.7. Only
 * [Issue.NO_AVAILABLE_GROUPS] blocks activation; the rest are hints for the editor.
 * zh-CN: 检查智能库是否存在 Roadmap 4.7 列出的情形. 只有 [Issue.NO_AVAILABLE_GROUPS] 阻止激活, 其余为编辑器提示.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object IntelligentConfigValidator {

    const val SHORT_INTERVAL_MILLIS = 60_000L

    enum class Issue {

        /** No enabled group has a color, and no date override can show one. zh-CN: 没有带颜色的启用组, 也没有可显示颜色的日期覆盖层. */
        NO_AVAILABLE_GROUPS,

        /** One state, at most one available group and no overrides: the library never changes. zh-CN: 只有一个状态, 至多一个可用组且无覆盖层: 库永远不会变化. */
        NOT_INTELLIGENT,

        /** Rotation triggers that can never change anything. zh-CN: 永远不会产生变化的换组时机. */
        ROTATION_WITHOUT_EFFECT,

        /** Some enabled groups lack colors for some states; the first colored state fills in. zh-CN: 部分启用组缺少某些状态的颜色, 由首个有颜色的状态补齐. */
        MISSING_STATE_COLORS,

        /** Enabled groups without any color; they are skipped. zh-CN: 没有任何颜色的启用组, 会被跳过. */
        GROUPS_WITHOUT_COLOR,

        /** An interval shorter than one minute. zh-CN: 时间间隔短于 1 分钟. */
        SHORT_INTERVAL,

        /** Items this app version does not understand; they have no effect. zh-CN: 当前版本无法识别的条目, 不会生效. */
        UNSUPPORTED_ITEMS,

        /** A date override points to a group that does not exist or has no color. zh-CN: 日期覆盖层指向不存在或没有颜色的组. */
        MISSING_OVERRIDE_TARGET,

    }

    data class Result(
        val issues: Set<Issue>,
        val groupsMissingStates: Map<Long, List<String>> = emptyMap(),
        val groupsWithoutColor: List<Long> = emptyList(),
    ) {
        val canActivate: Boolean
            get() = Issue.NO_AVAILABLE_GROUPS !in issues
    }

    fun validate(config: IntelligentConfig, groups: List<IntelligentGroup>): Result {
        val issues = LinkedHashSet<Issue>()
        val stateKeys = config.stateKeys
        val enabled = groups.filter { it.enabled }
        val available = enabled.filter { it.hasColor }
        val groupsById = groups.associateBy { it.id }
        val validOverrides = config.overrides.filter { groupsById[it.groupId]?.hasColor == true }

        if (available.isEmpty() && validOverrides.isEmpty()) {
            issues += Issue.NO_AVAILABLE_GROUPS
        }
        if (available.size == 1 && stateKeys.size == 1 && config.overrides.isEmpty()) {
            issues += Issue.NOT_INTELLIGENT
        }

        val effectiveTriggers = config.rotation.filter { it !is RotationTrigger.Unsupported }
        val stateChangeUseless = RotationTrigger.StateChange in effectiveTriggers && stateKeys.size == 1
        if (effectiveTriggers.isNotEmpty() && (available.size == 1 || stateChangeUseless)) {
            issues += Issue.ROTATION_WITHOUT_EFFECT
        }

        val groupsMissingStates = when {
            stateKeys.size < 2 -> emptyMap()
            else -> available.associate { group -> group.id to stateKeys.filter { group.states[it]?.primary == null } }.filterValues { it.isNotEmpty() }
        }
        if (groupsMissingStates.isNotEmpty()) issues += Issue.MISSING_STATE_COLORS

        val groupsWithoutColor = enabled.filterNot { it.hasColor }.map { it.id }
        if (groupsWithoutColor.isNotEmpty()) issues += Issue.GROUPS_WITHOUT_COLOR

        if (config.rotation.any { it is RotationTrigger.Interval && it.periodMillis < SHORT_INTERVAL_MILLIS }) {
            issues += Issue.SHORT_INTERVAL
        }
        if (config.hasUnsupportedItems) issues += Issue.UNSUPPORTED_ITEMS
        if (validOverrides.size != config.overrides.size) issues += Issue.MISSING_OVERRIDE_TARGET

        return Result(issues, groupsMissingStates, groupsWithoutColor)
    }

}
