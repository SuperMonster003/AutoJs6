package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Attr
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Receiver
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs.core.automator.UiNode
import java.util.BitSet

/** Offline prototype only. No complete LocatePlan is created until the FINISH action is chosen.
 * zh-CN: 仅离线原型使用. 只有选中 FINISH 动作后才创建完整 LocatePlan. */
internal class ConstructionGrammar<N : UiNode<N>>(
    val request: CodeGenRequest<N>,
    val verifier: PlanVerifier<N>,
    val budget: ConstructionBudget,
    private val cancellation: CancellationSignal,
    private val checkpoint: () -> Unit,
) {
    enum class Phase { FAMILY, ANCHOR, OUTER, INDEX, PATH, INNER, READY, DONE }

    data class State(
        val phase: Phase = Phase.FAMILY,
        val family: Family? = null,
        val anchor: Int = -1,
        val outer: List<Int> = emptyList(),
        val inner: List<Int> = emptyList(),
        val path: List<PlanStep> = emptyList(),
        val cursor: Int = -1,
        val index: Int? = null,
        val depth: Int = 0,
    ) {
        val key: String get() = listOf(
            "g1", phase.name, family?.name.orEmpty(), anchor, outer.joinToString(","),
            inner.joinToString(","), path.joinToString(",") {
                when (it) {
                    is PlanStep.Up -> "u${it.levels}"
                    is PlanStep.Child -> "c${it.index}"
                    else -> error("Non-path step in a partial path")
                }
            }, cursor, index ?: "",
        ).joinToString("|")
    }

    sealed interface Operation {
        val key: String
        data class ChooseFamily(val family: Family) : Operation { override val key = "family:${family.name}" }
        data class ChooseAnchor(val position: Int) : Operation { override val key = "anchor:$position" }
        data class AddCondition(val position: Int, val inner: Boolean, val attr: String) : Operation {
            override val key = "${if (inner) "inner" else "outer"}:$position:$attr"
        }
        data object EndOuter : Operation { override val key = "end-outer" }
        data class Index(val index: Int) : Operation { override val key = "index:$index" }
        data class Up(val levels: Int) : Operation { override val key = "up:$levels" }
        data class Child(val index: Int) : Operation { override val key = "child:$index" }
        data object Finish : Operation { override val key = "finish" }
    }

    val tree = verifier.tree
    val positions = tree.nodes.withIndex().associate { it.value to it.index }
    /** Exclusive end of each node's subtree in captured pre-order; subtrees are contiguous. */
    private val subtreeEnd = IntArray(tree.nodes.size)
    private class ConditionIndex(val positions: IntArray, val bits: BitSet)
    private val conditionIndexes = HashMap<Condition, ConditionIndex>()
    private data class MatchKey(val start: Int, val spec: SelectorSpec)
    private val matchCache = object : LinkedHashMap<MatchKey, List<N>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MatchKey, List<N>>?): Boolean = size > MAX_CACHED_SPECS
    }
    var singleConditionScans = 0
        private set
    val targets = request.targets.mapNotNull(tree::canonical).distinct()
    val target: N? = targets.firstOrNull()
    val cardinality = if (request.scope == Scope.SINGLE) Cardinality.ONE else Cardinality.ALL
    private val idPackages = HashMap<String, MutableSet<String>>()
    private val nodeConditions = HashMap<Int, List<Condition>>()
    private val ancestorCandidates = ArrayList<Int>()
    private val pathCandidates = ArrayList<Int>()
    private var receiverFailure: RejectionReason? = null
    val receiver: PlanReceiver?
    val initialRejection: RejectionReason?
    val targetConditions: List<Condition>
    val truncatedAnchors: Boolean

    init {
        for (index in tree.nodes.indices.reversed()) {
            var end = index + 1
            for (child in tree.children(tree.nodes[index])) end = maxOf(end, subtreeEnd[positions.getValue(child)])
            subtreeEnd[index] = end
        }
        for (node in tree.nodes) {
            checkpoint()
            node.id()?.takeIf { it.isNotEmpty() }?.let { fullId ->
                val entry = fullId.substringAfterLast(":id/")
                val owner = if (":id/" in fullId) fullId.substringBefore(":id/") else node.packageName().orEmpty()
                idPackages.getOrPut(entry, ::linkedSetOf).add(owner)
            }
        }
        receiver = receiverForTargets()
        initialRejection = when {
            request.validate().isNotEmpty() -> RejectionReason.INVALID_REQUEST
            targets.size != request.targets.distinct().size -> RejectionReason.TARGET_OUTSIDE_CAPTURE
            receiver == null -> receiverFailure ?: RejectionReason.UNSUPPORTED_ACTION
            else -> null
        }
        targetConditions = targets.firstOrNull()?.let { first ->
            val others = targets.drop(1).map { conditions(it).toSet() }
            conditions(first).filter { condition -> others.all { condition in it } }
        }.orEmpty()
        var ancestor = target?.let(tree::parent)
        repeat(6) {
            checkpoint()
            val current = ancestor ?: return@repeat
            if (targets.all { within(it, current) }) ancestorCandidates += positions.getValue(current)
            ancestor = tree.parent(current)
        }
        // Ancestors then captured DFS order, just as the frozen baseline considers anchors.
        // zh-CN: 先祖先再捕获树 DFS 顺序, 与冻结基线考虑锚点的顺序一致.
        val eligible = LinkedHashSet<Int>()
        eligible.addAll(ancestorCandidates)
        for (node in tree.nodes) {
            checkpoint()
            if (node != target && reachableByBaselinePath(node)) eligible += positions.getValue(node)
        }
        truncatedAnchors = eligible.size > budget.maxAnchors
        pathCandidates += eligible.take(budget.maxAnchors)
    }

    fun legal(state: State): List<Operation> {
        checkpoint()
        return when (state.phase) {
            Phase.FAMILY -> if (request.scope == Scope.SINGLE) listOf(
                Family.SELF, Family.SELF_INDEX, Family.ANCHOR_PATH, Family.ANCHOR_SEARCH,
            ).map { Operation.ChooseFamily(it) } else listOf(Family.SELF, Family.CONTAINER_ITEMS).map { Operation.ChooseFamily(it) }
            Phase.ANCHOR -> (if (state.family == Family.ANCHOR_PATH) pathCandidates else ancestorCandidates)
                .map { Operation.ChooseAnchor(it) }
            Phase.OUTER -> buildList {
                val source = outerSource(state)
                for (i in (state.outer.lastOrNull()?.plus(1) ?: 0) until source.size) {
                    add(Operation.AddCondition(i, false, source[i].attr))
                }
                add(if (state.family == Family.SELF) Operation.Finish else Operation.EndOuter)
            }
            Phase.INDEX -> {
                val found = matches(outerSpec(state))
                val at = found.indexOf(target)
                if (at < 0) emptyList() else buildList {
                    add(Operation.Index(at))
                    val negative = at - found.size
                    if (-negative <= at) add(Operation.Index(negative))
                }
            }
            Phase.PATH -> buildList {
                if (state.path.isNotEmpty()) add(Operation.Finish)
                if (state.path.size >= budget.maxPathSteps) return@buildList
                val cursor = tree.nodes[state.cursor]
                // One optional Up, then Child steps. This forbids cycles and repeated parent steps.
                // zh-CN: 一个可选 Up 后接 Child, 禁止环路和重复父步骤.
                if (state.path.isEmpty()) {
                    for (level in 1..minOf(6, tree.depth(cursor))) add(Operation.Up(level))
                }
                for (child in tree.children(cursor)) {
                    checkpoint()
                    if (state.path.any { it is PlanStep.Up } && within(tree.nodes[state.anchor], child)) continue
                    val index = tree.childIndex(child)
                    add(Operation.Child(index))
                    val negative = index - cursor.childCount()
                    if (-negative <= index) add(Operation.Child(negative))
                }
            }
            Phase.INNER -> buildList {
                for (i in (state.inner.lastOrNull()?.plus(1) ?: 0) until targetConditions.size) {
                    add(Operation.AddCondition(i, true, targetConditions[i].attr))
                }
                add(Operation.Finish)
            }
            Phase.READY -> listOf(Operation.Finish)
            Phase.DONE -> emptyList()
        }
    }

    fun apply(state: State, operation: Operation): State {
        checkpoint()
        val next = when (operation) {
            is Operation.ChooseFamily -> state.copy(
                family = operation.family,
                phase = if (operation.family == Family.SELF || operation.family == Family.SELF_INDEX) Phase.OUTER else Phase.ANCHOR,
            )
            is Operation.ChooseAnchor -> state.copy(phase = Phase.OUTER, anchor = operation.position, cursor = operation.position)
            is Operation.AddCondition -> if (operation.inner) state.copy(inner = state.inner + operation.position)
                else state.copy(outer = state.outer + operation.position)
            Operation.EndOuter -> state.copy(phase = when (state.family) {
                Family.SELF_INDEX -> Phase.INDEX
                Family.ANCHOR_PATH -> Phase.PATH
                Family.ANCHOR_SEARCH, Family.CONTAINER_ITEMS -> Phase.INNER
                else -> error("Illegal end-outer")
            })
            is Operation.Index -> state.copy(phase = Phase.READY, index = operation.index)
            is Operation.Up -> {
                var cursor = tree.nodes[state.cursor]
                repeat(operation.levels) { cursor = requireNotNull(tree.parent(cursor)) }
                state.copy(path = state.path + PlanStep.Up(operation.levels), cursor = positions.getValue(cursor))
            }
            is Operation.Child -> {
                val child = requireNotNull(tree.child(tree.nodes[state.cursor], operation.index))
                state.copy(path = state.path + PlanStep.Child(operation.index), cursor = positions.getValue(child))
            }
            Operation.Finish -> state.copy(phase = Phase.DONE)
        }
        return next.copy(depth = state.depth + 1)
    }

    fun complete(state: State): LocatePlan {
        require(state.phase == Phase.DONE)
        val family = requireNotNull(state.family)
        val steps = when (family) {
            Family.SELF -> emptyList()
            Family.SELF_INDEX -> listOf(PlanStep.Index(requireNotNull(state.index)))
            Family.ANCHOR_PATH -> state.path
            Family.ANCHOR_SEARCH, Family.CONTAINER_ITEMS -> listOf(PlanStep.Search(innerSpec(state)))
            Family.SELF_RELATIVE -> error("SELF_RELATIVE is outside the construction grammar")
        }
        return LocatePlan(family, outerSpec(state), steps, cardinality, requireNotNull(receiver))
    }

    fun outerSource(state: State): List<Condition> = if (state.anchor >= 0) conditions(tree.nodes[state.anchor]) else targetConditions
    fun outerSpec(state: State) = SelectorSpec(state.outer.map(outerSource(state)::get))
    fun innerSpec(state: State) = SelectorSpec(state.inner.map(targetConditions::get))
    fun condition(state: State, operation: Operation): Condition? = (operation as? Operation.AddCondition)?.let {
        (if (it.inner) targetConditions else outerSource(state))[it.position]
    }

    /**
     * Exactly the verifier's `match` (DFS pre-order within [within], inclusive), computed from one
     * verifier scan per distinct single condition instead of one scan per selector. A selector is a
     * conjunction of per-node predicates, so its matches are the intersection of the single-condition
     * matches restricted to the contiguous pre-order range of the subtree. Large captures previously
     * spent most of the search allocation rescanning the tree for every scored prefix.
     * zh-CN: 与验证器 match 完全一致: 每个单条件只扫描一次, 多条件选择器取交集并限制在子树的连续先序区间内,
     * 避免大捕获上对每个评分前缀重新扫描整棵树.
     */
    fun matches(spec: SelectorSpec, within: N = tree.root): List<N> {
        checkpoint()
        if (spec.conditions.isEmpty()) return verifier.match(spec, within)
        val start = positions[tree.canonical(within)] ?: return emptyList()
        val key = MatchKey(start, spec)
        matchCache[key]?.let { return it }
        val indexes = spec.conditions.map(::conditionIndex)
        val smallest = indexes.minBy { it.positions.size }
        val end = subtreeEnd[start]
        val result = ArrayList<N>()
        var at = lowerBound(smallest.positions, start)
        while (at < smallest.positions.size) {
            val position = smallest.positions[at++]
            if (position >= end) break
            if (indexes.all { it === smallest || it.bits.get(position) }) result += tree.nodes[position]
            if (result.size % 256 == 0) checkpoint()
        }
        checkpoint()
        matchCache[key] = result
        return result
    }

    private fun conditionIndex(condition: Condition): ConditionIndex = conditionIndexes.getOrPut(condition) {
        singleConditionScans++
        val found = verifier.match(SelectorSpec(listOf(condition)), tree.root)
        val bits = BitSet(tree.nodes.size)
        val positions = IntArray(found.size)
        found.forEachIndexed { index, node ->
            val position = this.positions.getValue(node)
            positions[index] = position
            bits.set(position)
        }
        // The verifier returns pre-order, so positions are already ascending.
        check(positions.asList().zipWithNext().all { (a, b) -> a < b }) { "Verifier matches must be in pre-order" }
        ConditionIndex(positions, bits)
    }

    private fun lowerBound(values: IntArray, target: Int): Int {
        var low = 0
        var high = values.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (values[middle] < target) low = middle + 1 else high = middle
        }
        return low
    }

    fun distance(a: N, b: N): Int {
        var left = a
        var right = b
        var distance = 0
        while (left !== right) {
            checkpoint()
            if (tree.depth(left) >= tree.depth(right)) left = tree.parent(left) ?: break
            else right = tree.parent(right) ?: break
            distance++
        }
        return distance
    }

    private fun within(node: N, ancestor: N): Boolean {
        var current: N? = node
        while (current != null) {
            checkpoint()
            if (current == ancestor) return true
            current = tree.parent(current)
        }
        return false
    }

    private fun reachableByBaselinePath(anchor: N): Boolean {
        var common: N? = anchor
        var up = 0
        while (common != null && up <= 6) {
            checkpoint()
            var descendant: N? = target
            var down = 0
            while (descendant != null && down <= 6) {
                checkpoint()
                if (descendant == common) return (if (up > 0) 1 else 0) + down in 1..budget.maxPathSteps
                descendant = tree.parent(descendant)
                down++
            }
            common = tree.parent(common)
            up++
        }
        return false
    }

    companion object {
        private const val MAX_CACHED_SPECS = 4_096
    }

    private fun receiverForTargets(): PlanReceiver? {
        if (request.receiver == Receiver.SELF || request.action == Action.NONE) return PlanReceiver.Self
        val levels = targets.map { target ->
            var node: N? = target
            var level = 0
            while (node != null && !PlanVerifier.supports(node, request.action)) {
                cancellation.throwIfCancelled()
                checkpoint()
                node = tree.parent(node)
                level++
            }
            if (node == null) { receiverFailure = RejectionReason.UNSUPPORTED_ACTION; return null }
            level
        }.distinct()
        if (levels.size != 1) { receiverFailure = RejectionReason.RECEIVER_STRATEGY_MISMATCH; return null }
        return levels.single().let { if (it == 0) PlanReceiver.Self else PlanReceiver.Up(it) }
    }

    private fun conditions(node: N): List<Condition> = nodeConditions.getOrPut(positions.getValue(node)) {
        checkpoint()
        buildList {
            if (Attr.ID in request.attributes) node.id()?.takeIf(String::isNotEmpty)?.let { fullId ->
                val entry = fullId.substringAfterLast(":id/")
                add(Condition("id", if ((idPackages[entry]?.size ?: 0) > 1) fullId else entry))
            }
            if (Attr.DESC in request.attributes) node.desc()?.takeIf(String::isNotEmpty)?.let { add(Condition("desc", it)) }
            if (Attr.TEXT in request.attributes) node.text().takeIf(String::isNotEmpty)?.let { add(Condition("text", it)) }
            node.className()?.takeIf(String::isNotEmpty)?.let { add(Condition("className", it)) }
            node.packageName()?.takeIf(String::isNotEmpty)?.let { add(Condition("packageName", it)) }
            for ((name, value) in listOf(
                "clickable" to node.clickable(), "longClickable" to node.longClickable(),
                "editable" to node.editable(), "scrollable" to node.scrollable(),
                "checkable" to node.checkable(), "checked" to node.checked(),
                "selected" to node.selected(), "enabled" to node.enabled(),
                "focusable" to node.focusable(), "focused" to node.focused(),
                "visibleToUser" to node.visibleToUser(), "accessibilityFocused" to node.accessibilityFocused(),
                "contextClickable" to node.contextClickable(), "dismissable" to node.dismissable(),
            )) add(Condition(name, value))
            add(Condition("depth", node.depth()))
        }
    }
}
