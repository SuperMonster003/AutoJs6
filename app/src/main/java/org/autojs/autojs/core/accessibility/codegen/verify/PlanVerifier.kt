package org.autojs.autojs.core.accessibility.codegen.verify

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.NodeTree
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.NodeRecyclePolicy
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassStep
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import java.util.Collections

sealed interface Verification<out N>

class Verified<N : UiNode<N>> internal constructor(
    val plan: LocatePlan,
    resolved: List<N>,
    val facts: VerificationFacts,
) : Verification<N> {
    val resolved: List<N> = Collections.unmodifiableList(ArrayList(resolved))

    override fun toString(): String = "Verified(plan=$plan, resolved=$resolved, facts=$facts)"
}

/** Counts concern the complete search, even when script semantics take only its first result.
 * [relativeMatchCount] is the number of entry matches a SELF_RELATIVE plan keeps, -1 for other families.
 * zh-CN: 计数对应完整搜索, 即使脚本语义只取首个结果. [relativeMatchCount] 为 SELF_RELATIVE 保留的入口匹配数, 其它族为 -1. */
data class VerificationFacts(
    val selectorMatchCount: Int,
    val anchorMatchCount: Int = 0,
    val index: Int? = null,
    val resolvedIndex: Int? = null,
    val pathSteps: Int = 0,
    val pathUpLevels: Int = 0,
    val pathChildSteps: Int = 0,
    val pathSiblingSteps: Int = 0,
    val pathMaxChildIndex: Int = 0,
    val anchorDistance: Int = 0,
    val receiverUpLevels: Int = 0,
    val dynamicText: Boolean = false,
    val dynamicDesc: Boolean = false,
    val conditionCount: Int = 0,
    val containerItemCount: Int = 0,
    val relativeMatchCount: Int = -1,
)

enum class RejectionReason {
    INVALID_REQUEST,
    TARGET_OUTSIDE_CAPTURE,
    CARDINALITY_MISMATCH,
    FORBIDDEN_ATTRIBUTE,
    INVALID_SELECTOR,
    EMPTY_RESULT,
    ANCHOR_NOT_FOUND,
    AMBIGUOUS_ANCHOR,
    INDEX_OUT_OF_RANGE,
    UNREACHABLE_PATH,
    WRONG_TARGET,
    EXTRA_TARGETS,
    MISSING_TARGETS,
    TARGET_SET_MISMATCH,
    RECEIVER_STRATEGY_MISMATCH,
    RECEIVER_NOT_FOUND,
    UNSUPPORTED_ACTION,
}

/** Structured evidence for the result dialog and the closest failed candidate.
 * zh-CN: 结果对话框与最接近的被拒候选所需的结构化证据. */
data class Rejected(
    val reason: RejectionReason,
    val extraCount: Int = 0,
    val missingCount: Int = 0,
    val detail: String? = null,
) : Verification<Nothing>

/**
 * Executes every plan with the script selector and compass implementations on the captured window.
 * It compares canonical node identities, not selector hit counts or wrapper object identities.
 * AUTO receivers must support the action; explicit SELF preserves the user's deliberate choice.
 * zh-CN: 在捕获窗口中使用脚本选择器与罗盘实现执行计划. 比较规范节点身份, 而非命中数或包装对象身份.
 * AUTO 接收者必须支持动作; 显式 SELF 保留用户的主动选择.
 */
class PlanVerifier<N : UiNode<N>>(
    root: N,
    private val cancellation: CancellationSignal = CancellationSignal(),
) {
    val tree = NodeTree(root, cancellation)
    val root: N get() = tree.root
    val nodes: List<N> get() = tree.nodes

    private data class MatchKey<N>(val root: N, val spec: SelectorSpec)
    private val matches = LinkedHashMap<MatchKey<N>, List<N>>(16, 0.75f, true)
    private var cachedReferences = 0
    private val lifecycle = NodeLifecycle(NodeRecyclePolicy.NO_OP)

    fun canonical(node: N): N? = tree.canonical(node)

    /** Cached, non-recycling DFS pre-order, including [within] itself. The cache is per capture.
     * zh-CN: 有缓存且不回收节点的 DFS 先序搜索, 包含 [within] 自身. 缓存仅属于本次捕获. */
    fun match(spec: SelectorSpec, within: N = root): List<N> {
        cancellation.throwIfCancelled()
        val anchor = canonical(within) ?: return emptyList()
        val key = MatchKey(anchor, spec)
        matches[key]?.let { return it }
        val selector = UiSelector(null)
        // First filter checks cancellation even when later predicates reject every node.
        // zh-CN: 首个过滤器检查取消, 即使后续谓词拒绝全部节点也生效.
        selector.addFilter(object : Filter {
            override fun filter(node: UiNode<*>): Boolean {
                cancellation.throwIfCancelled()
                return true
            }
        })
        for (condition in spec.conditions) {
            check(SelectorRegistry.apply(selector, condition.attr, listOf(condition.value)) == SelectorRegistry.Outcome.APPLIED)
        }
        val found = selector.searchIn(anchor, lifecycle = lifecycle).mapNotNull(::canonical)
        cancellation.throwIfCancelled()
        val result: List<N> = Collections.unmodifiableList(found)
        if (result.size <= MAX_CACHED_REFERENCES) {
            while (matches.isNotEmpty() && (matches.size >= MAX_CACHED_SEARCHES || cachedReferences + result.size > MAX_CACHED_REFERENCES)) {
                val iterator = matches.entries.iterator()
                cachedReferences -= iterator.next().value.size
                iterator.remove()
            }
            matches[key] = result
            cachedReferences += result.size
        }
        return result
    }

    fun verify(request: CodeGenRequest<N>, plan: LocatePlan): Verification<N> {
        cancellation.throwIfCancelled()
        val errors = request.validate()
        if (errors.isNotEmpty()) return Rejected(RejectionReason.INVALID_REQUEST, detail = errors.joinToString())
        val targets = request.targets.map { canonical(it) ?: return Rejected(RejectionReason.TARGET_OUTSIDE_CAPTURE) }
        val expectedCardinality = if (request.scope == CodeGenRequest.Scope.SINGLE) Cardinality.ONE else Cardinality.ALL
        if (plan.cardinality != expectedCardinality) return Rejected(RejectionReason.CARDINALITY_MISMATCH)

        val conditions = plan.selector.conditions + plan.steps.flatMap { step ->
            when (step) {
                is PlanStep.Search -> step.selector.conditions
                is PlanStep.Match -> step.selector.conditions
                else -> emptyList()
            }
        }
        conditions.firstOrNull { !allowed(it, request.attributes) }?.let {
            return Rejected(RejectionReason.FORBIDDEN_ATTRIBUTE, detail = it.attr)
        }
        if ((request.receiver == CodeGenRequest.Receiver.SELF || request.action == CodeGenRequest.Action.NONE) && plan.receiver != PlanReceiver.Self) {
            return Rejected(RejectionReason.RECEIVER_STRATEGY_MISMATCH)
        }

        return try {
            resolve(request, plan, targets, conditions)
        } catch (e: IllegalArgumentException) {
            Rejected(RejectionReason.INVALID_SELECTOR, detail = e.message)
        }
    }

    private fun resolve(request: CodeGenRequest<N>, plan: LocatePlan, targets: List<N>, conditions: List<Condition>): Verification<N> {
        val entryMatches = match(plan.selector)
        var selectorMatchCount = entryMatches.size
        var anchorMatchCount = 0
        var index: Int? = null
        var resolvedIndex: Int? = null
        var anchor: N? = null
        var relativeMatchCount = -1
        val resolved: List<N> = when (plan.family) {
            Family.SELF -> if (plan.cardinality == Cardinality.ONE) entryMatches.take(1) else entryMatches
            Family.SELF_INDEX -> {
                index = (plan.steps.single() as PlanStep.Index).index
                // Long arithmetic prevents a negative index from overflowing into a valid slot.
                // zh-CN: 使用 Long 运算, 防止负序号溢出成有效位置.
                val actual = if (index < 0) entryMatches.size.toLong() + index else index.toLong()
                if (actual !in 0L until entryMatches.size.toLong()) return Rejected(RejectionReason.INDEX_OUT_OF_RANGE)
                resolvedIndex = actual.toInt()
                listOf(entryMatches[resolvedIndex])
            }
            Family.SELF_RELATIVE -> {
                if (entryMatches.isEmpty()) return Rejected(RejectionReason.EMPTY_RESULT, missingCount = targets.toSet().size)
                val relation = plan.relation
                val relative = requireNotNull(plan.relativeMatch)
                // The relative match is one capture-wide search, then a pre-order range test per entry match.
                // zh-CN: 相对匹配只做一次整窗搜索, 之后对每个入口匹配项做先序区间判断.
                val positions = match(relative).map(tree::position).filter { it >= 0 }.sorted()
                val kept = ArrayList<N>()
                for (candidate in entryMatches) {
                    cancellation.throwIfCancelled()
                    val reached = navigate(candidate, relation) ?: continue
                    if (containsAny(positions, tree.position(reached), tree.subtreeEnd(reached))) kept += candidate
                }
                relativeMatchCount = kept.size
                if (plan.cardinality == Cardinality.ONE) kept.take(1) else kept
            }
            Family.ANCHOR_PATH, Family.ANCHOR_SEARCH, Family.CONTAINER_ITEMS -> {
                anchorMatchCount = entryMatches.size
                if (entryMatches.isEmpty()) return Rejected(RejectionReason.ANCHOR_NOT_FOUND)
                if (entryMatches.size != 1) return Rejected(RejectionReason.AMBIGUOUS_ANCHOR, extraCount = entryMatches.size - 1)
                val locatedAnchor = entryMatches.single()
                anchor = locatedAnchor
                if (plan.family == Family.ANCHOR_PATH) {
                    val current = navigate(locatedAnchor, plan.steps) ?: return Rejected(RejectionReason.UNREACHABLE_PATH)
                    selectorMatchCount = 1
                    listOf(current)
                } else {
                    val scoped = match((plan.steps.single() as PlanStep.Search).selector, locatedAnchor)
                    selectorMatchCount = scoped.size
                    if (plan.cardinality == Cardinality.ONE) scoped.take(1) else scoped
                }
            }
        }
        if (resolved.isEmpty()) return Rejected(RejectionReason.EMPTY_RESULT, missingCount = targets.toSet().size)
        if (request.scope == CodeGenRequest.Scope.SINGLE) {
            if (resolved.single() !== targets.single()) return Rejected(RejectionReason.WRONG_TARGET, extraCount = 1, missingCount = 1)
        } else {
            val expected = targets.toSet()
            val actual = resolved.toSet()
            val extra = (actual - expected).size
            val missing = (expected - actual).size
            val reason = when {
                extra > 0 && missing > 0 -> RejectionReason.TARGET_SET_MISMATCH
                extra > 0 -> RejectionReason.EXTRA_TARGETS
                missing > 0 -> RejectionReason.MISSING_TARGETS
                else -> null
            }
            if (reason != null) return Rejected(reason, extra, missing)
        }

        val receiverLevels = (plan.receiver as? PlanReceiver.Up)?.levels ?: 0
        if (request.action != CodeGenRequest.Action.NONE && request.receiver == CodeGenRequest.Receiver.AUTO) {
            for (target in resolved) {
                cancellation.throwIfCancelled()
                if (receiverLevels > tree.depth(target)) return Rejected(RejectionReason.RECEIVER_NOT_FOUND)
                val receiver = if (receiverLevels == 0) target else Compass.navigate(target, listOf(CompassStep.Up(receiverLevels)))?.let(::canonical)
                if (receiver == null) return Rejected(RejectionReason.RECEIVER_NOT_FOUND)
                if (!supports(receiver, request.action)) return Rejected(RejectionReason.UNSUPPORTED_ACTION)
            }
        }
        val facts = VerificationFacts(
            selectorMatchCount = selectorMatchCount,
            anchorMatchCount = anchorMatchCount,
            index = index,
            resolvedIndex = resolvedIndex,
            pathSteps = plan.steps.count(PlanStep::isRelation),
            pathUpLevels = plan.steps.filterIsInstance<PlanStep.Up>().sumOf { it.levels },
            pathChildSteps = plan.steps.count { it is PlanStep.Child },
            pathSiblingSteps = plan.steps.count { it is PlanStep.Sibling },
            pathMaxChildIndex = plan.steps.maxOfOrNull { step ->
                val ordinal = when (step) {
                    is PlanStep.Child -> step.index
                    is PlanStep.Sibling -> step.offset
                    else -> 0
                }
                kotlin.math.abs(ordinal.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } ?: 0,
            anchorDistance = anchor?.let { distance(it, resolved.first()) } ?: 0,
            receiverUpLevels = receiverLevels,
            dynamicText = conditions.any { it.attr.startsWith("text") && dynamic(it.value) },
            dynamicDesc = conditions.any { it.attr.startsWith("desc") && dynamic(it.value) },
            conditionCount = conditions.size,
            containerItemCount = if (plan.family == Family.CONTAINER_ITEMS) resolved.size else 0,
            relativeMatchCount = relativeMatchCount,
        )
        return Verified(plan, resolved, facts)
    }

    /** Follows relation steps with the script compass; null when a step is unreachable.
     * zh-CN: 以脚本罗盘执行关系步骤, 步骤无法到达时返回 null. */
    private fun navigate(start: N, steps: List<PlanStep>): N? {
        var current = start
        for (step in steps) {
            cancellation.throwIfCancelled()
            val navigation = when (step) {
                is PlanStep.Up -> {
                    if (step.levels > tree.depth(current)) return null
                    CompassStep.Up(step.levels)
                }
                is PlanStep.Child -> CompassStep.Children(listOf(step.index))
                is PlanStep.Sibling -> CompassStep.Sibling(
                    if (step.offset > 0) CompassStep.Sibling.Mode.FORWARD else CompassStep.Sibling.Mode.BACKWARD,
                    kotlin.math.abs(step.offset),
                )
                else -> error("Invalid path step")
            }
            current = Compass.navigate(current, listOf(navigation))?.let(::canonical) ?: return null
        }
        return current
    }

    /** Whether any sorted position lies in [start]..[end]. zh-CN: 有序位置中是否有落在 [start]..[end] 内的. */
    private fun containsAny(positions: List<Int>, start: Int, end: Int): Boolean {
        if (start < 0 || positions.isEmpty()) return false
        var low = 0
        var high = positions.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (positions[middle] < start) low = middle + 1 else high = middle
        }
        return low < positions.size && positions[low] <= end
    }

    private fun distance(from: N, to: N): Int {
        var a = from
        var b = to
        var distance = 0
        while (a !== b) {
            cancellation.throwIfCancelled()
            if (tree.depth(a) >= tree.depth(b)) a = tree.parent(a) ?: break else b = tree.parent(b) ?: break
            distance++
        }
        return distance
    }

    private fun allowed(condition: Condition, attrs: Set<CodeGenRequest.Attr>): Boolean = when {
        condition.attr.startsWith("id") -> CodeGenRequest.Attr.ID in attrs
        condition.attr.startsWith("text") -> CodeGenRequest.Attr.TEXT in attrs
        condition.attr.startsWith("desc") -> CodeGenRequest.Attr.DESC in attrs
        // content falls back from description to text and must obey both switches.
        // zh-CN: content 从描述退回文本, 必须遵守两个开关.
        condition.attr.startsWith("content") && condition.value is String -> CodeGenRequest.Attr.TEXT in attrs && CodeGenRequest.Attr.DESC in attrs
        else -> true
    }

    private fun dynamic(value: Any): Boolean = value is String && value.any { it.isDigit() }

    companion object {
        private const val MAX_CACHED_SEARCHES = 512
        private const val MAX_CACHED_REFERENCES = 200_000

        fun supports(node: UiNode<*>, action: CodeGenRequest.Action): Boolean = when (action) {
            CodeGenRequest.Action.NONE -> true
            CodeGenRequest.Action.CLICK -> node.clickable()
            CodeGenRequest.Action.LONG_CLICK -> node.longClickable()
            is CodeGenRequest.Action.SET_TEXT -> node.editable()
            CodeGenRequest.Action.SCROLL_FORWARD, CodeGenRequest.Action.SCROLL_BACKWARD -> node.scrollable()
        }
    }
}
