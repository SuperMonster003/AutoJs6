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
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs.core.automator.UiNode
import java.util.BitSet

/** Offline prototype only. No complete LocatePlan is created until the FINISH action is chosen.
 * Grammar 1 is the frozen five-family grammar of the first four rounds. Grammar 2 (round five) adds
 * SELF_RELATIVE (target conditions, then an upward and/or sibling relation, then a distinctive content
 * node of the reached subtree, D22/D23) and first-line prefix conditions (D24); grammar 1 is unchanged.
 * zh-CN: 仅离线原型使用. 只有选中 FINISH 动作后才创建完整 LocatePlan. 语法 1 为前四轮冻结的五族语法;
 * 语法 2 (第五轮) 加入 SELF_RELATIVE (目标条件, 向上和/或兄弟关系, 关系可达子树中具区分度的内容节点, D22/D23)
 * 与首行前缀条件 (D24), 语法 1 保持不变. */
internal class ConstructionGrammar<N : UiNode<N>>(
    val request: CodeGenRequest<N>,
    val verifier: PlanVerifier<N>,
    val budget: ConstructionBudget,
    private val cancellation: CancellationSignal,
    val version: Int = GRAMMAR_V1,
    private val checkpoint: () -> Unit,
) {
    /** Grammar 2 phases follow DONE so that the ordinals of grammar 1 phases stay unchanged.
     * zh-CN: 语法 2 的阶段排在 DONE 之后, 保持语法 1 各阶段的序号不变. */
    enum class Phase { FAMILY, ANCHOR, OUTER, INDEX, PATH, INNER, READY, DONE, RELATION, CONTENT, MATCH }

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
        val grammar: Int = GRAMMAR_V1,
        val relation: List<PlanStep> = emptyList(),
        val content: Int = -1,
        val match: List<Int> = emptyList(),
    ) {
        val key: String get() = (listOf(
            "g$grammar", phase.name, family?.name.orEmpty(), anchor, outer.joinToString(","),
            inner.joinToString(","), path.joinToString(",") {
                when (it) {
                    is PlanStep.Up -> "u${it.levels}"
                    is PlanStep.Child -> "c${it.index}"
                    else -> error("Non-path step in a partial path")
                }
            }, cursor, index ?: "",
        ) + if (grammar < GRAMMAR_V2) emptyList() else listOf(
            relation.joinToString(",") {
                when (it) {
                    is PlanStep.Up -> "u${it.levels}"
                    is PlanStep.Sibling -> "s${it.offset}"
                    else -> error("Non-relation step in a partial relation")
                }
            }, content, match.joinToString(","),
        )).joinToString("|")
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
        // Grammar 2 only. zh-CN: 仅语法 2.
        data class RelationUp(val levels: Int) : Operation { override val key = "relation-up:$levels" }
        data class Sibling(val offset: Int) : Operation { override val key = "sibling:$offset" }
        data object EndRelation : Operation { override val key = "end-relation" }
        data class ChooseContent(val position: Int) : Operation { override val key = "content:$position" }
        data class AddMatch(val position: Int, val attr: String) : Operation { override val key = "match:$position:$attr" }
    }

    fun initialState() = State(grammar = version)

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
    private val targetSet = targets.toSet()
    private val keptCache = object : LinkedHashMap<Triple<SelectorSpec, List<PlanStep>, SelectorSpec>, List<N>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<SelectorSpec, List<PlanStep>, SelectorSpec>, List<N>>?): Boolean =
            size > MAX_CACHED_SPECS
    }
    private val contentCache = HashMap<List<PlanStep>, List<Int>>()
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
            Phase.FAMILY -> families().map { Operation.ChooseFamily(it) }
            Phase.ANCHOR -> (if (state.family == Family.ANCHOR_PATH) pathCandidates else ancestorCandidates)
                .map { Operation.ChooseAnchor(it) }
            Phase.OUTER -> buildList {
                val source = outerSource(state)
                for (i in (state.outer.lastOrNull()?.plus(1) ?: 0) until source.size) {
                    if (impliedPrefix(source, state.outer, i)) continue
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
                    if (impliedPrefix(targetConditions, state.inner, i)) continue
                    add(Operation.AddCondition(i, true, targetConditions[i].attr))
                }
                add(Operation.Finish)
            }
            Phase.READY -> listOf(Operation.Finish)
            Phase.DONE -> emptyList()
            Phase.RELATION -> buildList {
                val origin = target ?: return@buildList
                val last = state.relation.lastOrNull()
                if (last == null) {
                    for (level in 1..minOf(LocatePlan.MAX_RELATION_STEPS, tree.depth(origin))) add(Operation.RelationUp(level))
                    addSiblings(origin)
                } else if (last is PlanStep.Up && last.levels < LocatePlan.MAX_RELATION_STEPS) {
                    // Exactly the rule search's relations: an ancestor, or a sibling of the target or of an ancestor.
                    // zh-CN: 与规则搜索的关系完全一致: 祖先, 或目标及其祖先的兄弟.
                    addSiblings(tree.nodes[state.cursor])
                }
                if (last != null) add(Operation.EndRelation)
            }
            Phase.CONTENT -> contentCandidates(state).map { Operation.ChooseContent(it) }
            Phase.MATCH -> buildList {
                val source = contentConditions(tree.nodes[state.content])
                for (i in (state.match.lastOrNull()?.plus(1) ?: 0) until source.size) {
                    if (impliedPrefix(source, state.match, i)) continue
                    add(Operation.AddMatch(i, source[i].attr))
                }
                // D23: a nested content condition that also matches a target carries no identity.
                // zh-CN: D23: 同时匹配目标的嵌套内容条件不携带身份信息.
                if (state.match.isNotEmpty() && distinctive(matchSpec(state))) add(Operation.Finish)
            }
        }
    }

    private fun families(): List<Family> = buildList {
        if (request.scope == Scope.SINGLE) addAll(listOf(Family.SELF, Family.SELF_INDEX, Family.ANCHOR_PATH, Family.ANCHOR_SEARCH))
        else addAll(listOf(Family.SELF, Family.CONTAINER_ITEMS))
        if (version >= GRAMMAR_V2) add(Family.SELF_RELATIVE)
    }

    private fun MutableList<Operation>.addSiblings(node: N) {
        val parent = tree.parent(node) ?: return
        val index = tree.childIndex(node)
        for (distance in 1..CandidateSearch.SIBLING_RADIUS) for (sign in intArrayOf(1, -1)) {
            checkpoint()
            val at = index + sign * distance
            if (at >= 0 && tree.child(parent, at) != null) add(Operation.Sibling(sign * distance))
        }
    }

    /** An exact value already implies its own first-line prefix (D24). zh-CN: 精确值已蕴含其首行前缀 (D24). */
    private fun impliedPrefix(source: List<Condition>, chosen: List<Int>, candidate: Int): Boolean {
        val attr = source[candidate].attr
        return attr.endsWith(PREFIX) && chosen.any { source[it].attr == attr.removeSuffix(PREFIX) }
    }

    fun apply(state: State, operation: Operation): State {
        checkpoint()
        val next = when (operation) {
            is Operation.ChooseFamily -> state.copy(
                family = operation.family,
                phase = if (operation.family in SELF_FAMILIES) Phase.OUTER else Phase.ANCHOR,
            )
            is Operation.ChooseAnchor -> state.copy(phase = Phase.OUTER, anchor = operation.position, cursor = operation.position)
            is Operation.AddCondition -> if (operation.inner) state.copy(inner = state.inner + operation.position)
                else state.copy(outer = state.outer + operation.position)
            Operation.EndOuter -> if (state.family == Family.SELF_RELATIVE) {
                state.copy(phase = Phase.RELATION, cursor = positions.getValue(requireNotNull(target)))
            } else state.copy(phase = when (state.family) {
                Family.SELF_INDEX -> Phase.INDEX
                Family.ANCHOR_PATH -> Phase.PATH
                Family.ANCHOR_SEARCH, Family.CONTAINER_ITEMS -> Phase.INNER
                else -> error("Illegal end-outer")
            })
            is Operation.RelationUp -> state.copy(
                relation = state.relation + PlanStep.Up(operation.levels),
                cursor = positions.getValue(requireNotNull(ancestor(tree.nodes[state.cursor], operation.levels))),
            )
            is Operation.Sibling -> state.copy(
                relation = state.relation + PlanStep.Sibling(operation.offset),
                cursor = positions.getValue(requireNotNull(sibling(tree.nodes[state.cursor], operation.offset))),
            )
            Operation.EndRelation -> state.copy(phase = Phase.CONTENT)
            is Operation.ChooseContent -> state.copy(phase = Phase.MATCH, content = operation.position)
            is Operation.AddMatch -> state.copy(match = state.match + operation.position)
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
            Family.SELF_RELATIVE -> {
                check(version >= GRAMMAR_V2) { "SELF_RELATIVE is outside grammar 1" }
                state.relation + PlanStep.Match(matchSpec(state))
            }
        }
        return LocatePlan(family, outerSpec(state), steps, cardinality, requireNotNull(receiver))
    }

    fun outerSource(state: State): List<Condition> = if (state.anchor >= 0) conditions(tree.nodes[state.anchor]) else targetConditions
    fun outerSpec(state: State) = SelectorSpec(state.outer.map(outerSource(state)::get))
    fun innerSpec(state: State) = SelectorSpec(state.inner.map(targetConditions::get))
    /** The nested content selector of a SELF_RELATIVE state; empty before content is chosen.
     * zh-CN: SELF_RELATIVE 状态的嵌套内容选择器; 选定内容节点前为空. */
    fun matchSpec(state: State): SelectorSpec =
        if (state.content < 0) SelectorSpec() else SelectorSpec(state.match.map(contentConditions(tree.nodes[state.content])::get))
    fun condition(state: State, operation: Operation): Condition? = when (operation) {
        is Operation.AddCondition -> (if (operation.inner) targetConditions else outerSource(state))[operation.position]
        is Operation.AddMatch -> contentConditions(tree.nodes[state.content])[operation.position]
        else -> null
    }

    /** Whether no target matches [spec] anywhere in the capture (D23). zh-CN: 捕获中没有目标匹配 [spec] (D23). */
    fun distinctive(spec: SelectorSpec): Boolean = spec.conditions.isNotEmpty() && matches(spec).none { it in targetSet }

    /**
     * The entry matches a SELF_RELATIVE state keeps: those whose relation reaches a node whose subtree contains
     * a match of the nested content selector, evaluated like the verifier on the captured tree. Cached by prefix.
     * zh-CN: SELF_RELATIVE 状态保留的入口匹配项: 关系到达的节点子树含嵌套内容匹配者, 与验证器在捕获树上的判定一致, 按前缀缓存.
     */
    fun relativeKept(state: State): List<N> {
        val outer = outerSpec(state)
        val match = matchSpec(state)
        if (match.conditions.isEmpty()) return emptyList()
        return keptCache.getOrPut(Triple(outer, state.relation, match)) {
            val contents = matches(match).map(positions::getValue).sorted().toIntArray()
            matches(outer).filter { entry ->
                checkpoint()
                val reached = navigate(entry, state.relation) ?: return@filter false
                val start = positions.getValue(reached)
                val at = lowerBound(contents, start)
                at < contents.size && contents[at] < subtreeEnd[start]
            }
        }
    }

    fun navigate(start: N, relation: List<PlanStep>): N? {
        var current = start
        for (step in relation) {
            current = when (step) {
                is PlanStep.Up -> ancestor(current, step.levels)
                is PlanStep.Sibling -> sibling(current, step.offset)
                else -> error("Non-relation step")
            } ?: return null
        }
        return current
    }

    private fun ancestor(node: N, levels: Int): N? {
        var current: N? = node
        repeat(levels) { current = current?.let(tree::parent) }
        return current
    }

    private fun sibling(node: N, offset: Int): N? {
        val parent = tree.parent(node) ?: return null
        val at = tree.childIndex(node) + offset
        return if (at < 0) null else tree.child(parent, at)
    }

    fun contentConditions(node: N): List<Condition> = conditions(node).filter { it.attr in CONTENT_ATTRS }

    /**
     * Content nodes of a finished relation, as in the rule search: the nearest nodes with id, description or
     * text in the reached subtree, outside the branch that holds the target for a purely upward relation.
     * zh-CN: 关系完成后的内容节点, 与规则搜索一致: 到达子树中最近的带 id, 描述或文本的节点; 纯向上关系跳过包含目标的分支.
     */
    fun contentCandidates(state: State): List<Int> = contentCache.getOrPut(state.relation) {
        val origin = requireNotNull(target)
        val reached = tree.nodes[state.cursor]
        val start = positions.getValue(reached)
        val excluded = (state.relation.singleOrNull() as? PlanStep.Up)?.let { up -> ancestor(origin, up.levels - 1) }
        val skipStart = excluded?.let(positions::getValue) ?: -1
        val skipEnd = if (skipStart >= 0) subtreeEnd[skipStart] else -1
        val found = ArrayList<Pair<Int, Int>>()
        var index = start
        while (index < subtreeEnd[start]) {
            checkpoint()
            if (index == skipStart) { index = skipEnd; continue }
            if (contentConditions(tree.nodes[index]).isNotEmpty()) found += index to distance(tree.nodes[index], origin)
            index++
        }
        found.sortedWith(compareBy({ it.second }, { it.first })).take(CandidateSearch.CONTENT_LIMIT).map { it.first }
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
        const val GRAMMAR_V1 = 1
        const val GRAMMAR_V2 = 2
        private const val PREFIX = "StartsWith"
        private val CONTENT_ATTRS = setOf("id", "desc", "descStartsWith", "text", "textStartsWith")
        private val SELF_FAMILIES = setOf(Family.SELF, Family.SELF_INDEX, Family.SELF_RELATIVE)
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
            if (Attr.DESC in request.attributes) node.desc()?.takeIf(String::isNotEmpty)?.let { desc ->
                add(Condition("desc", desc))
                if (version >= GRAMMAR_V2) CandidateSearch.firstLine(desc)?.let { add(Condition("descStartsWith", it)) }
            }
            if (Attr.TEXT in request.attributes) node.text().takeIf(String::isNotEmpty)?.let { text ->
                add(Condition("text", text))
                if (version >= GRAMMAR_V2) CandidateSearch.firstLine(text)?.let { add(Condition("textStartsWith", it)) }
            }
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
