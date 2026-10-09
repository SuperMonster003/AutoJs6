package org.autojs.autojs.core.accessibility.codegen.search

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
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs.core.accessibility.codegen.verify.Verification
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.UiNode
import java.util.Collections

/** Hard search limits include verification attempts made while simplifying conditions.
 * zh-CN: 搜索硬上限包含精简条件时进行的验证尝试. */
data class SearchBudget(val maxAttempts: Int = 2_000, val maxVerified: Int = 200) {
    init {
        require(maxAttempts in 0..2_000)
        require(maxVerified in 1..200)
    }
}

/** A relation from the target to a nearby node whose content may identify it; [excluded] is the branch holding the target.
 * zh-CN: 从目标到某邻近节点的关系, 该节点的内容可能标识目标; [excluded] 为包含目标的分支. */
private class Relation<N>(val steps: List<PlanStep>, val reached: N, val excluded: N?)

data class SearchResult<N : UiNode<N>>(
    val candidates: List<Verified<N>>,
    val attemptedCount: Int,
    val budgetExhausted: Boolean,
    val closestRejection: Rejected?,
    val rejectionCounts: Map<RejectionReason, Int>,
    val verificationNanos: Long,
)

/**
 * Deterministic enumeration and simplification of the six plan families. Families and anchors
 * take turns so a large property combination space cannot consume the entire budget first.
 * All retained plans have passed the same verifier used by dataset evaluation.
 * zh-CN: 确定性枚举并精简六类计划. 各计划族与锚点轮流枚举, 避免属性组合先耗尽全部预算.
 * 保留的计划均经过与数据集评估共用的验证器.
 */
class CandidateSearch<N : UiNode<N>>(
    root: N,
    private val cancellation: CancellationSignal = CancellationSignal(),
    private val budget: SearchBudget = SearchBudget(),
    private val verifier: PlanVerifier<N> = PlanVerifier(root, cancellation),
) {
    fun search(request: CodeGenRequest<N>): SearchResult<N> = Session(request).run()

    private inner class Session(private val request: CodeGenRequest<N>) {
        private val tree = verifier.tree
        private val accepted = LinkedHashMap<String, Verified<N>>()
        private val checked = LinkedHashMap<String, Verification<N>>(16, 0.75f, true)
        private var cachedReferences = 0
        private val rejections = LinkedHashMap<RejectionReason, Int>()
        private var closest: Rejected? = null
        private var attempts = 0
        private var limited = false
        private var verificationNanos = 0L
        private val cardinality = if (request.scope == Scope.SINGLE) Cardinality.ONE else Cardinality.ALL
        private val positions = tree.nodes.withIndex().associate { it.value to it.index }
        private val subtreeEnds = IntArray(tree.nodes.size) { it }
        private val idPackages = HashMap<String, MutableSet<String>>()
        private val target = request.targets.firstOrNull()?.let(tree::canonical)
        private var receiverFailure: RejectionReason? = null
        private val receiver = receiverForTargets()

        init {
            for (i in tree.nodes.indices.reversed()) {
                cancellation.throwIfCancelled()
                val node = tree.nodes[i]
                tree.parent(node)?.let { parent ->
                    positions[parent]?.let { parentIndex -> subtreeEnds[parentIndex] = maxOf(subtreeEnds[parentIndex], subtreeEnds[i]) }
                }
                node.id()?.takeIf { it.isNotEmpty() }?.let { fullId ->
                    val entry = fullId.substringAfterLast(":id/")
                    val owner = if (":id/" in fullId) fullId.substringBefore(":id/") else node.packageName().orEmpty()
                    idPackages.getOrPut(entry, ::linkedSetOf).add(owner)
                }
            }
        }

        fun run(): SearchResult<N> {
            cancellation.throwIfCancelled()
            if (budget.maxAttempts == 0) {
                limited = true
                return result()
            }
            if (target == null || request.targets.any { !tree.contains(it) } || request.validate().isNotEmpty()) {
                checkPlan(LocatePlan(Family.SELF, SelectorSpec(), cardinality = cardinality))
                return result()
            }
            if (receiver == null) {
                val reason = receiverFailure ?: RejectionReason.UNSUPPORTED_ACTION
                attempts = 1
                closest = Rejected(reason)
                rejections[reason] = 1
                return result()
            }
            val targetSpecs = specs(commonConditions(request.targets)).toList()
            val families = listOf(
                selfPlans(targetSpecs),
                indexPlans(targetSpecs),
                anchorPaths(),
                ancestorSearches(targetSpecs),
                relativePlans(targetSpecs),
            )
            val iterator = interleave(families).iterator()
            while (iterator.hasNext()) {
                cancellation.throwIfCancelled()
                if (attempts >= budget.maxAttempts || accepted.size >= budget.maxVerified) {
                    limited = true
                    break
                }
                val plan = iterator.next()
                val verified = checkPlan(plan) as? Verified<N> ?: continue
                val minimal = simplify(verified)
                accepted.putIfAbsent(minimal.plan.planKey, minimal)
            }
            return result()
        }

        private fun result() = SearchResult(
            Collections.unmodifiableList(ArrayList(accepted.values)),
            attempts,
            limited,
            closest,
            Collections.unmodifiableMap(LinkedHashMap(rejections)),
            verificationNanos,
        )

        private fun checkPlan(plan: LocatePlan): Verification<N>? {
            cancellation.throwIfCancelled()
            accepted[plan.planKey]?.let { return it }
            checked[plan.planKey]?.let { return it }
            if (attempts >= budget.maxAttempts) {
                limited = true
                return null
            }
            attempts++
            val start = System.nanoTime()
            val outcome = try {
                verifier.verify(request, plan)
            } finally {
                verificationNanos += System.nanoTime() - start
            }
            val references = (outcome as? Verified)?.resolved?.size ?: 0
            if (references <= MAX_CACHED_REFERENCES) {
                while (checked.isNotEmpty() && cachedReferences + references > MAX_CACHED_REFERENCES) {
                    val iterator = checked.entries.iterator()
                    cachedReferences -= (iterator.next().value as? Verified)?.resolved?.size ?: 0
                    iterator.remove()
                }
                checked[plan.planKey] = outcome
                cachedReferences += references
            }
            if (outcome is Rejected) {
                rejections[outcome.reason] = (rejections[outcome.reason] ?: 0) + 1
                val previous = closest
                if (previous == null || compareValuesBy(outcome, previous, ::failureStage, { it.extraCount + it.missingCount }) < 0) closest = outcome
            }
            return outcome
        }

        private fun matches(spec: SelectorSpec): List<N> {
            val start = System.nanoTime()
            return try {
                verifier.match(spec)
            } finally {
                verificationNanos += System.nanoTime() - start
            }
        }

        private fun failureStage(rejected: Rejected): Int = when (rejected.reason) {
            RejectionReason.RECEIVER_STRATEGY_MISMATCH, RejectionReason.RECEIVER_NOT_FOUND, RejectionReason.UNSUPPORTED_ACTION -> 0
            RejectionReason.WRONG_TARGET, RejectionReason.EXTRA_TARGETS, RejectionReason.MISSING_TARGETS, RejectionReason.TARGET_SET_MISMATCH -> 1
            RejectionReason.AMBIGUOUS_ANCHOR -> 2
            else -> 3
        }

        private fun simplify(initial: Verified<N>): Verified<N> {
            var current = initial
            // Preserve earlier, cheaper properties when either one can distinguish the target.
            // zh-CN: 多个属性都能区分目标时, 优先保留靠前且代价较低的属性.
            for (i in current.plan.selector.conditions.indices.reversed()) {
                val remaining = current.plan.selector.conditions.toMutableList().apply { removeAt(i) }
                val plan = copy(current.plan, selector = SelectorSpec(remaining))
                val reduced = checkPlan(plan)
                if (reduced is Verified) current = reduced
                if (limited) return current
            }
            val nested = when (val last = current.plan.steps.lastOrNull()) {
                is PlanStep.Search -> last.selector
                is PlanStep.Match -> last.selector
                else -> return current
            }
            for (i in nested.conditions.indices.reversed()) {
                val remaining = nestedSelector(current.plan).conditions.toMutableList().apply { removeAt(i) }
                // A relative match keeps at least one condition; an empty one would keep every entry match.
                // zh-CN: 相对匹配至少保留一个条件, 空条件会保留全部入口匹配项.
                if (remaining.isEmpty() && current.plan.family == Family.SELF_RELATIVE) continue
                val reduced = checkPlan(copy(current.plan, steps = withNestedSelector(current.plan, SelectorSpec(remaining))))
                if (reduced is Verified) current = reduced
                if (limited) return current
            }
            return current
        }

        private fun nestedSelector(plan: LocatePlan): SelectorSpec = when (val last = plan.steps.last()) {
            is PlanStep.Search -> last.selector
            is PlanStep.Match -> last.selector
            else -> error("No nested selector")
        }

        private fun withNestedSelector(plan: LocatePlan, selector: SelectorSpec): List<PlanStep> = plan.steps.dropLast(1) + when (plan.steps.last()) {
            is PlanStep.Search -> PlanStep.Search(selector)
            is PlanStep.Match -> PlanStep.Match(selector)
            else -> error("No nested selector")
        }

        private fun copy(plan: LocatePlan, selector: SelectorSpec = plan.selector, steps: List<PlanStep> = plan.steps) =
            LocatePlan(plan.family, selector, steps, plan.cardinality, plan.receiver)

        private fun selfPlans(specs: List<SelectorSpec>) = sequence {
            for (spec in specs) yield(LocatePlan(Family.SELF, spec, cardinality = cardinality, receiver = receiver!!))
        }

        private fun indexPlans(specs: List<SelectorSpec>) = sequence {
            if (request.scope != Scope.SINGLE) return@sequence
            for (spec in specs) {
                cancellation.throwIfCancelled()
                val found = matches(spec)
                val index = found.indexOf(target)
                if (index < 0) continue
                yield(LocatePlan(Family.SELF_INDEX, spec, listOf(PlanStep.Index(index)), receiver = receiver!!))
                val negative = index - found.size
                if (-negative <= index) yield(LocatePlan(Family.SELF_INDEX, spec, listOf(PlanStep.Index(negative)), receiver = receiver))
            }
        }

        private fun ancestors(): List<N> {
            val result = ArrayList<N>()
            var node = target
            repeat(6) {
                node = node?.let(tree::parent)
                node?.let(result::add) ?: return result
            }
            return result
        }

        private fun ancestorSearches(targetSpecs: List<SelectorSpec>): Sequence<LocatePlan> {
            val family = if (request.scope == Scope.COLLECTION) Family.CONTAINER_ITEMS else Family.ANCHOR_SEARCH
            val sources = ancestors().filter { parent -> request.targets.all { isWithin(it, parent) } }.map { ancestor ->
                sequence {
                    val anchorSpecs = specs(conditions(ancestor)).toList()
                    // Diagonals try both simple anchor and simple target conditions early.
                    // zh-CN: 对角枚举, 让简单锚点条件与简单目标条件都能尽早得到尝试.
                    for (sum in 0 until anchorSpecs.size + targetSpecs.size - 1) {
                        for (i in maxOf(0, sum - targetSpecs.lastIndex)..minOf(sum, anchorSpecs.lastIndex)) {
                            cancellation.throwIfCancelled()
                            yield(LocatePlan(family, anchorSpecs[i], listOf(PlanStep.Search(targetSpecs[sum - i])), cardinality, receiver!!))
                        }
                    }
                }
            }
            return interleave(sources)
        }

        private fun anchorPaths(): Sequence<LocatePlan> {
            if (request.scope != Scope.SINGLE) return emptySequence()
            val targetAncestors = LinkedHashMap<N, Int>()
            var node = target
            for (level in 0..6) {
                val current = node ?: break
                targetAncestors[current] = level
                node = tree.parent(current)
            }
            val candidates = LinkedHashMap<N, List<PlanStep>>()
            for (anchor in ancestors() + tree.nodes) {
                cancellation.throwIfCancelled()
                if (anchor == target || candidates.containsKey(anchor)) continue
                path(anchor, targetAncestors)?.let { candidates[anchor] = it }
            }
            return interleave(candidates.map { (anchor, steps) ->
                sequence {
                    for (spec in specs(conditions(anchor))) {
                        yield(LocatePlan(Family.ANCHOR_PATH, spec, steps, receiver = receiver!!))
                    }
                }
            })
        }

        private fun path(anchor: N, targetAncestors: Map<N, Int>): List<PlanStep>? {
            var common: N? = anchor
            var up = 0
            while (common != null && common !in targetAncestors && up < 6) {
                common = tree.parent(common)
                up++
            }
            val ancestor = common?.takeIf { it in targetAncestors } ?: return null
            val down = ArrayList<PlanStep>()
            var descendant = target!!
            while (descendant != ancestor) {
                val index = tree.childIndex(descendant)
                if (index < 0) return null
                down.add(PlanStep.Child(index))
                descendant = tree.parent(descendant) ?: return null
            }
            val steps = buildList {
                if (up > 0) add(PlanStep.Up(up))
                addAll(down.asReversed())
            }
            return steps.takeIf { it.isNotEmpty() && it.size <= 4 }
        }

        private fun isWithin(node: N, ancestor: N): Boolean {
            val index = positions[node] ?: return false
            val start = positions[ancestor] ?: return false
            return index in start..subtreeEnds[start]
        }

        /**
         * Relative plans identify the target through the content of a nearby control: the entry
         * selector may match many nodes, and the relation (each ancestor within reach, or a sibling of
         * the target or of one of its ancestors) must lead to a node whose subtree contains the content.
         * Content comes from the nearest nodes with id, description or text, outside the target's own branch.
         * zh-CN: 相对计划通过邻近控件的内容确定目标: 入口选择器可匹配多个节点, 关系 (可达范围内的各级祖先,
         * 或目标及其祖先的兄弟) 须到达一个子树包含该内容的节点. 内容取自目标自身分支之外最近的带 id, 描述或文本的节点.
         */
        private fun relativePlans(targetSpecs: List<SelectorSpec>): Sequence<LocatePlan> {
            val origin = target ?: return emptySequence()
            val chain = ArrayList<N>().apply { add(origin) }
            val relations = ArrayList<Relation<N>>()
            for (level in 1..LocatePlan.MAX_RELATION_STEPS) {
                val ancestor = tree.parent(chain.last()) ?: break
                chain += ancestor
                relations += Relation(listOf(PlanStep.Up(level)), ancestor, chain[level - 1])
            }
            for (level in 0 until minOf(chain.size, LocatePlan.MAX_RELATION_STEPS)) {
                val node = chain[level]
                val parent = tree.parent(node) ?: break
                val index = tree.childIndex(node)
                for (distance in 1..SIBLING_RADIUS) for (sign in intArrayOf(1, -1)) {
                    cancellation.throwIfCancelled()
                    // A negative position is not a sibling; child(-n) would count from the end instead.
                    // zh-CN: 负位置不是兄弟节点; child(-n) 会改为从末尾计数.
                    val at = index + sign * distance
                    if (at < 0) continue
                    val sibling = tree.child(parent, at) ?: continue
                    val steps = buildList { if (level > 0) add(PlanStep.Up(level)); add(PlanStep.Sibling(sign * distance)) }
                    relations += Relation(steps, sibling, null)
                }
            }
            val sources = relations.map { relation ->
                sequence {
                    val relativeSpecs = contentNodes(relation.reached, relation.excluded)
                        .flatMap { content -> specs(contentConditions(content)).filter { it.conditions.isNotEmpty() }.toList() }.distinct()
                    for (sum in 0 until relativeSpecs.size + targetSpecs.size - 1) {
                        for (i in maxOf(0, sum - targetSpecs.lastIndex)..minOf(sum, relativeSpecs.lastIndex)) {
                            cancellation.throwIfCancelled()
                            yield(LocatePlan(Family.SELF_RELATIVE, targetSpecs[sum - i], relation.steps + PlanStep.Match(relativeSpecs[i]), cardinality, receiver!!))
                        }
                    }
                }
            }
            return interleave(sources)
        }

        /** Nearest content-bearing nodes in the subtree of [root], skipping the subtree of [excluded].
         * zh-CN: [root] 子树中最近的带内容节点, 跳过 [excluded] 的子树. */
        private fun contentNodes(root: N, excluded: N?): List<N> {
            val start = positions[root] ?: return emptyList()
            val skipStart = excluded?.let { positions[it] } ?: -1
            val skipEnd = if (skipStart >= 0) subtreeEnds[skipStart] else -1
            val found = ArrayList<Pair<N, Int>>()
            var index = start
            while (index <= subtreeEnds[start]) {
                cancellation.throwIfCancelled()
                if (index == skipStart) {
                    index = skipEnd + 1
                    continue
                }
                val node = tree.nodes[index]
                if (contentConditions(node).isNotEmpty()) found += node to distance(node)
                index++
            }
            return found.sortedWith(compareBy({ it.second }, { positions.getValue(it.first) })).take(CONTENT_LIMIT).map { it.first }
        }

        private fun distance(node: N): Int {
            var a = node
            var b = target!!
            var distance = 0
            while (a != b) {
                cancellation.throwIfCancelled()
                if (tree.depth(a) >= tree.depth(b)) a = tree.parent(a) ?: break else b = tree.parent(b) ?: break
                distance++
            }
            return distance
        }

        private fun contentConditions(node: N): List<Condition> = conditions(node).filter { it.attr in CONTENT_ATTRS }

        private fun receiverForTargets(): PlanReceiver? {
            if (request.receiver == Receiver.SELF || request.action == Action.NONE) return PlanReceiver.Self
            val levels = request.targets.map { target ->
                var node = tree.canonical(target)
                var level = 0
                while (node != null && !PlanVerifier.supports(node, request.action)) {
                    cancellation.throwIfCancelled()
                    node = tree.parent(node)
                    level++
                }
                if (node == null) {
                    receiverFailure = RejectionReason.UNSUPPORTED_ACTION
                    return null
                }
                level
            }.distinct()
            // The current IR has one receiver path shared by the collection.
            // zh-CN: 当前 IR 的集合共用一条动作接收者路径.
            if (levels.size != 1) {
                receiverFailure = RejectionReason.RECEIVER_STRATEGY_MISMATCH
                return null
            }
            return levels.single().let { if (it == 0) PlanReceiver.Self else PlanReceiver.Up(it) }
        }

        private fun commonConditions(targets: List<N>): List<Condition> {
            if (targets.isEmpty()) return emptyList()
            val conditions = targets.map { conditions(it).toSet() }
            return conditions.first().filter { condition -> conditions.all { condition in it } }
        }

        private fun conditions(node: N): List<Condition> = buildList {
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

        private fun specs(conditions: List<Condition>): Sequence<SelectorSpec> = sequence {
            val emitted = HashSet<SelectorSpec>()
            suspend fun SequenceScope<SelectorSpec>.emit(indices: List<Int>) {
                val spec = SelectorSpec(indices.map(conditions::get))
                if (emitted.add(spec)) yield(spec)
            }
            for (i in conditions.indices) emit(listOf(i))
            emit(conditions.indices.toList())
            for (i in conditions.indices) for (j in i + 1 until conditions.size) emit(listOf(i, j))
            for (i in conditions.indices) for (j in i + 1 until conditions.size) for (k in j + 1 until conditions.size) emit(listOf(i, j, k))
            if (emitted.add(SelectorSpec())) yield(SelectorSpec())
        }.take(budget.maxAttempts.coerceAtLeast(1))

        private fun <T> interleave(sources: List<Sequence<T>>): Sequence<T> = sequence {
            val iterators = sources.map { it.iterator() }.toMutableList()
            while (iterators.isNotEmpty()) {
                val iterator = iterators.iterator()
                while (iterator.hasNext()) {
                    cancellation.throwIfCancelled()
                    val source = iterator.next()
                    if (source.hasNext()) yield(source.next()) else iterator.remove()
                }
            }
        }
    }

    companion object {
        private const val MAX_CACHED_REFERENCES = 200_000
        /** Sibling offsets tried on each side of the target and its ancestors. zh-CN: 目标及其祖先两侧各尝试的兄弟偏移数. */
        internal const val SIBLING_RADIUS = 3
        /** Content nodes considered per relation. zh-CN: 每个关系考虑的内容节点数. */
        internal const val CONTENT_LIMIT = 8
        private val CONTENT_ATTRS = setOf("id", "desc", "text")
    }
}
