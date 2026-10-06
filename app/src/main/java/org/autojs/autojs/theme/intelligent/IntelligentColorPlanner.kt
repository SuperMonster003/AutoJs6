package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.color.OkColor
import java.time.ZoneId
import kotlin.random.Random

/**
 * Engine state that survives between evaluations (persisted by the engine glue).
 * zh-CN: 两次评估之间需要保留的引擎状态 (由引擎胶水层持久化).
 *
 * @property currentGroupId The rotating group; kept unchanged while a date override is shown.
 * zh-CN: 轮换中的当前组; 日期覆盖层生效期间保持不变.
 * @property overrideGroupId The group shown by the active date override, or null.
 * zh-CN: 当前生效的日期覆盖层所显示的组, 没有时为 null.
 */
data class IntelligentRuntimeState(
    val currentGroupId: Long? = null,
    val lastStateKey: String? = null,
    val overrideGroupId: Long? = null,
    val intervalAnchorAt: Long? = null,
    val nextDueAt: Long? = null,
    val lastRotatedAt: Long? = null,
)

enum class IntelligentEvent {

    /** The library becomes active, or its configuration changed. zh-CN: 库被激活, 或其配置发生变化. */
    ACTIVATE,

    /** Re-evaluate: a state may have changed or an interval may be due. zh-CN: 重新评估: 状态可能已变化, 或时间间隔可能已到期. */
    EVALUATE,

    /** First evaluation after a cold start. zh-CN: 冷启动后的首次评估. */
    APP_START,

    /** A script asked for the next group. zh-CN: 脚本请求切换到下一组. */
    SCRIPT_NEXT,

}

/**
 * @property stateKey The current state as reported by the state source glue.
 * zh-CN: 状态源胶水层报告的当前状态.
 * @property overrideGroupId Target of the first matching date override, resolved by the caller.
 * zh-CN: 第一个命中的日期覆盖层的目标组, 由调用方解析.
 * @property startGroupId The group chosen by the user on [IntelligentEvent.ACTIVATE].
 * zh-CN: [IntelligentEvent.ACTIVATE] 时用户选择的起始组.
 */
data class PlannerInput(
    val config: IntelligentConfig,
    val groups: List<IntelligentGroup>,
    val stateKey: String,
    val now: Long,
    val event: IntelligentEvent,
    val runtime: IntelligentRuntimeState = IntelligentRuntimeState(),
    val overrideGroupId: Long? = null,
    val startGroupId: Long? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * @property groupId The group being shown (the override group while one is active), or null when nothing is available.
 * zh-CN: 正在显示的组 (覆盖层生效时为覆盖组); 没有可用组时为 null.
 */
data class IntelligentPlan(
    val groupId: Long?,
    val stateKey: String,
    val color: EffectiveColor?,
    val rotated: Boolean,
    val runtime: IntelligentRuntimeState,
)

/**
 * Pure decision core of the intelligent color engine (roadmap 4.3): given the configuration, the groups,
 * the current state and an event, decides which group to show and which color applies.
 * zh-CN: 智能颜色引擎的纯决策核心 (Roadmap 4.3): 根据配置, 组, 当前状态与事件, 决定显示哪个组以及使用哪个颜色.
 *
 * Rules:
 * - Several triggers firing in one evaluation rotate once. A state change rotates first, then the new state's color is used.
 * - Disabled groups and groups without any color never take part in rotation.
 * - While a date override matches, its group is shown and rotation is suspended; afterwards the previous group returns without advancing.
 * zh-CN: 规则:
 * - 一次评估中多个换组时机同时触发只换一组. 状态变化时先换组, 再取新状态的颜色.
 * - 禁用组与没有任何颜色的组不参与轮换.
 * - 日期覆盖层命中期间显示其指定组并暂停轮换; 离开后回到之前的组, 不推进.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class IntelligentColorPlanner(
    private val random: Random = Random.Default,
    private val nameComparator: Comparator<String> = naturalOrder(),
) {

    fun plan(input: PlannerInput): IntelligentPlan {
        val config = input.config
        val stateKeys = config.stateKeys
        val stateKey = input.stateKey.takeIf { it in stateKeys } ?: stateKeys.first()
        val runtime = input.runtime
        val ordered = orderedGroups(config, input.groups)
        val available = ordered.filter { it.isAvailable }
        val activating = input.event == IntelligentEvent.ACTIVATE

        // Interval grid. zh-CN: 时间间隔网格.
        val interval = config.interval
        var anchorAt: Long? = null
        var nextDueAt: Long? = null
        var intervalFired = false
        if (interval != null) {
            val armed = !activating && runtime.intervalAnchorAt != null && runtime.nextDueAt != null
            if (armed) {
                anchorAt = runtime.intervalAnchorAt
                intervalFired = input.now >= runtime.nextDueAt!!
                nextDueAt = if (intervalFired) IntervalSchedule.nextDue(anchorAt!!, interval.periodMillis, input.now) else runtime.nextDueAt
            } else {
                anchorAt = IntervalSchedule.anchorAt(input.now, interval.anchor, input.zone)
                nextDueAt = IntervalSchedule.nextDue(anchorAt, interval.periodMillis, input.now)
            }
        }

        val stateChanged = runtime.lastStateKey != null && runtime.lastStateKey != stateKey
        val fired = !activating && (
                (stateChanged && RotationTrigger.StateChange in config.rotation)
                        || (input.event == IntelligentEvent.APP_START && RotationTrigger.AppStart in config.rotation)
                        || intervalFired
                        || input.event == IntelligentEvent.SCRIPT_NEXT
                )

        val overrideGroupId = input.overrideGroupId?.takeIf { id -> input.groups.any { it.id == id && it.hasColor } }
        val leavingOverride = !activating && overrideGroupId == null && runtime.overrideGroupId != null

        var currentGroupId = when {
            activating -> input.startGroupId?.takeIf { id -> available.any { it.id == id } } ?: available.firstOrNull()?.id
            else -> runtime.currentGroupId
        }
        var rotated = false
        when {
            overrideGroupId != null || leavingOverride -> {
                currentGroupId = currentGroupId.takeIf { id -> available.any { it.id == id } } ?: nextAvailable(ordered, currentGroupId)
            }
            fired -> {
                val next = when (config.order) {
                    is GroupOrder.Random -> randomAvailable(available, currentGroupId)
                    is GroupOrder.Sequential -> nextAvailable(ordered, currentGroupId)
                }
                rotated = next != null
                currentGroupId = next
            }
            else -> {
                if (available.none { it.id == currentGroupId }) {
                    currentGroupId = nextAvailable(ordered, currentGroupId)
                }
            }
        }

        val shownGroupId = overrideGroupId ?: currentGroupId
        val color = input.groups.firstOrNull { it.id == shownGroupId }?.resolve(stateKey, stateKeys)

        return IntelligentPlan(
            groupId = shownGroupId,
            stateKey = stateKey,
            color = color,
            rotated = rotated,
            runtime = IntelligentRuntimeState(
                currentGroupId = currentGroupId,
                lastStateKey = stateKey,
                overrideGroupId = overrideGroupId,
                intervalAnchorAt = anchorAt,
                nextDueAt = nextDueAt,
                lastRotatedAt = if (rotated) input.now else runtime.lastRotatedAt,
            ),
        )
    }

    /**
     * All groups (including unavailable ones) in rotation order. Random order falls back to manual order,
     * which decides where a disabled current group continues from.
     * zh-CN: 按轮换顺序排列的全部组 (含不可用组). 随机切换时按手动顺序排列, 用于决定已禁用的当前组之后从哪里继续.
     */
    fun orderedGroups(config: IntelligentConfig, groups: List<IntelligentGroup>): List<IntelligentGroup> {
        val stateKeys = config.stateKeys
        val order = config.order as? GroupOrder.Sequential ?: GroupOrder.Sequential()
        val referenceState = order.referenceState?.takeIf { it in stateKeys } ?: stateKeys.first()
        fun metric(group: IntelligentGroup, select: (Int) -> Double): Double {
            return group.resolve(referenceState, stateKeys)?.let { select(it.primary) } ?: Double.MAX_VALUE
        }
        val primary: Comparator<IntelligentGroup> = when (order.key) {
            SortKey.MANUAL -> compareBy { it.position }
            SortKey.CREATED -> compareBy { it.createdTime }
            SortKey.MODIFIED -> compareBy { it.modifiedTime }
            SortKey.NAME -> Comparator { a, b -> nameComparator.compare(displayName(a, referenceState, stateKeys), displayName(b, referenceState, stateKeys)) }
            SortKey.LIGHTNESS -> compareBy { metric(it) { color -> OkColor.toOkLch(color).l } }
            SortKey.HUE -> compareBy { metric(it) { color -> OkColor.toOkLch(color).h } }
            SortKey.CHROMA -> compareBy { metric(it) { color -> OkColor.toOkLch(color).c } }
        }
        val tieBreaker = compareBy<IntelligentGroup>({ it.position }, { it.id })
        return groups.sortedWith((if (order.descending) primary.reversed() else primary).then(tieBreaker))
    }

    private fun displayName(group: IntelligentGroup, stateKey: String, stateKeys: List<String>): String {
        return group.name?.takeIf { it.isNotBlank() }
            ?: group.resolve(stateKey, stateKeys)?.let { "#%06X".format(it.primary and 0xFFFFFF) }
            ?: ""
    }

    /**
     * The available group after [currentId] in [ordered], wrapping around; the first available group
     * when [currentId] is unknown.
     * zh-CN: [ordered] 中位于 [currentId] 之后的可用组 (循环); [currentId] 未知时返回第一个可用组.
     */
    private fun nextAvailable(ordered: List<IntelligentGroup>, currentId: Long?): Long? {
        val index = ordered.indexOfFirst { it.id == currentId }
        if (index < 0) return ordered.firstOrNull { it.isAvailable }?.id
        for (offset in 1..ordered.size) {
            val candidate = ordered[(index + offset) % ordered.size]
            if (candidate.isAvailable) return candidate.id
        }
        return null
    }

    private fun randomAvailable(available: List<IntelligentGroup>, currentId: Long?): Long? {
        if (available.isEmpty()) return null
        val candidates = available.filter { it.id != currentId }.ifEmpty { available }
        return candidates[random.nextInt(candidates.size)].id
    }

    private val IntelligentGroup.isAvailable: Boolean
        get() = enabled && hasColor

}
