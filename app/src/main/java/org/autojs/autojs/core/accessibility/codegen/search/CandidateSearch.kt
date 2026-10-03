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

data class SearchResult<N : UiNode<N>>(
    val candidates: List<Verified<N>>,
    val attemptedCount: Int,
    val budgetExhausted: Boolean,
    val closestRejection: Rejected?,
    val rejectionCounts: Map<RejectionReason, Int>,
    val verificationNanos: Long,
)

/**
 * Deterministic enumeration and simplification of the five plan families. Families and anchors
 * take turns so a large property combination space cannot consume the entire budget first.
 * All retained plans have passed the same verifier used by dataset evaluation.
 * zh-CN: 确定性枚举并精简五类计划. 各计划族与锚点轮流枚举, 避免属性组合先耗尽全部预算.
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
            val search = current.plan.steps.singleOrNull() as? PlanStep.Search ?: return current
            for (i in search.selector.conditions.indices.reversed()) {
                val inner = (current.plan.steps.single() as PlanStep.Search).selector.conditions.toMutableList().apply { removeAt(i) }
                val reduced = checkPlan(copy(current.plan, steps = listOf(PlanStep.Search(SelectorSpec(inner)))))
                if (reduced is Verified) current = reduced
                if (limited) return current
            }
            return current
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
    }
}
