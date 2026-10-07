package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Phase
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.State
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs.core.accessibility.codegen.verify.Verification
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.UiNode
import kotlin.math.abs

/** All limits remain finite, including in teacher-data collection. Verification attempts include
 * condition minimization, exactly as in CandidateSearch. Tree capture precedes this search session.
 * zh-CN: 离线采集也保留全部有限上限. 验证次数包含条件精简, 与 CandidateSearch 相同. */
data class ConstructionBudget(
    val maxAttempts: Int = 2_000,
    val maxVerified: Int = 200,
    val maxFrontier: Int = 64,
    val maxExpandedStates: Int = 2_000,
    val maxActionScores: Int = 12_000,
    val maxDepth: Int = 48,
    val maxPathSteps: Int = 4,
    val maxAnchors: Int = 128,
    val maxCapturedNodes: Int = 10_000,
    val maxMillis: Long = 1_000,
) {
    init {
        require(maxAttempts in 0..2_000)
        require(maxVerified in 1..200)
        require(maxFrontier in 4..4_096)
        require(maxExpandedStates in 0..20_000)
        require(maxActionScores in 0..120_000)
        require(maxDepth in 1..64)
        require(maxPathSteps in 1..4)
        require(maxAnchors in 1..10_000)
        require(maxCapturedNodes in 1..20_000)
        require(maxMillis in 1..60_000)
    }
}

enum class ConstructionStopReason {
    EXHAUSTED, VERIFICATION_BUDGET, RETAINED_BUDGET, EXPANSION_BUDGET, ACTION_SCORE_BUDGET,
    DEPTH_BUDGET, DEADLINE, POLICY_FAILURE, CAPTURE_LIMIT, TRACE_REFERENCE_BUDGET, INVALID_REQUEST,
}

data class ConstructionDecision(val stateKey: String, val actionKey: String, val features: FloatArray, val score: Double)
data class ConstructionExpansion(val stateKey: String, val legalActionKeys: List<String>, val scoredActions: List<ConstructionDecision>)
data class ConstructionCompletion<N : UiNode<N>>(
    val plan: LocatePlan,
    val verification: Verification<N>,
    val decisions: List<ConstructionDecision>,
    val simplified: Verified<N>?,
)
data class ConstructionStats(
    val attemptedCount: Int,
    val expandedStates: Int,
    val actionScores: Int,
    val policyCalls: Int,
    val peakFrontier: Int,
    val peakPendingPrefixes: Int,
    val maximumDepth: Int,
    val verificationNanos: Long,
    val featureNanos: Long,
    val policyNanos: Long,
    val elapsedNanos: Long,
    val prunedPrefixes: Int,
    val depthPrunedPrefixes: Int,
    val truncatedAnchors: Boolean,
    val traceReferences: Int,
    val hardDeadlineMillis: Long,
)
data class ConstructionResult<N : UiNode<N>>(
    val candidates: List<Verified<N>>,
    val completions: List<ConstructionCompletion<N>>,
    val expansions: List<ConstructionExpansion>,
    val stats: ConstructionStats,
    val stopReason: ConstructionStopReason,
    val closestRejection: Rejected?,
    val rejectionCounts: Map<RejectionReason, Int>,
    val policyFailure: String? = null,
)

/** Best-value search inside each family, with round-robin family expansion. Heuristic and learned
 * runs differ only in [policy]. Prefixes pruned by a budget have no completion/failed label.
 * zh-CN: 族内按价值搜索, 族间轮转. 启发式和学习策略仅 policy 不同; 未完成前缀不标失败. */
class ConstructionSearch<N : UiNode<N>>(
    root: N,
    private val cancellation: CancellationSignal = CancellationSignal(),
    private val budget: ConstructionBudget = ConstructionBudget(),
    private val policy: ConstructionPolicy = ConstructionHeuristicPolicy,
    private val verifier: PlanVerifier<N> = PlanVerifier(root, cancellation),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun search(request: CodeGenRequest<N>): ConstructionResult<N> = Session(request).run()

    private class Stop(val reason: ConstructionStopReason) : RuntimeException(null, null, false, false)
    private sealed interface ScoredPrefix {
        val state: State
        val score: Double
        val actionKey: String
    }
    private data class Prefix(
        override val state: State,
        val decisions: List<ConstructionDecision>,
        override val score: Double,
        override val actionKey: String,
    ) : ScoredPrefix
    /** Share the parent trace until this scored successor survives the batch's beam cut. */
    private data class PendingPrefix(
        override val state: State,
        val parent: Prefix,
        val decision: ConstructionDecision,
    ) : ScoredPrefix {
        override val score: Double get() = decision.score
        override val actionKey: String get() = decision.actionKey
        fun retain() = Prefix(state, parent.decisions + decision, score, actionKey)
    }

    private inner class Session(private val request: CodeGenRequest<N>) {
        private val started = nanoTime()
        private val accepted = LinkedHashMap<String, Verified<N>>()
        private val checked = LinkedHashMap<String, Verification<N>>(16, 0.75f, true)
        private var checkedReferences = 0
        private val completions = ArrayList<ConstructionCompletion<N>>()
        private val expansions = ArrayList<ConstructionExpansion>()
        private val frontier = ArrayList<Prefix>()
        // The grammar key retains the complete ordered prefix, so each state has one parent.
        // This is not a closed set for a future graph grammar with converging construction paths.
        // zh-CN: 当前 key 保留完整有序前缀, 每个状态只有一个父状态; 不适用于路径汇合的图语法.
        private val visited = HashSet<String>()
        private val rejections = LinkedHashMap<RejectionReason, Int>()
        private var closest: Rejected? = null
        private var attempts = 0
        private var expanded = 0
        private var scores = 0
        private var policyCalls = 0
        private var peak = 0
        private var peakPending = 0
        private var maxDepth = 0
        private var verificationNanos = 0L
        private var featureNanos = 0L
        private var policyNanos = 0L
        private var pruned = 0
        private var depthPruned = 0
        private var traceReferences = 0
        private var stopReason = ConstructionStopReason.EXHAUSTED
        private var policyFailure: String? = null
        private var familyCursor = -1
        private lateinit var grammar: ConstructionGrammar<N>

        fun run(): ConstructionResult<N> {
            try {
                checkpoint()
                if (verifier.tree.nodes.size > budget.maxCapturedNodes) throw Stop(ConstructionStopReason.CAPTURE_LIMIT)
                if (budget.maxAttempts == 0) throw Stop(ConstructionStopReason.VERIFICATION_BUDGET)
                grammar = ConstructionGrammar(request, verifier, budget, cancellation, ::checkpoint)
                grammar.initialRejection?.let {
                    closest = Rejected(it)
                    rejections[it] = 1
                    throw Stop(ConstructionStopReason.INVALID_REQUEST)
                }
                frontier += Prefix(State(), emptyList(), 0.0, "")
                peak = 1
                while (frontier.isNotEmpty()) {
                    checkpoint()
                    if (attempts >= budget.maxAttempts) throw Stop(ConstructionStopReason.VERIFICATION_BUDGET)
                    if (accepted.size >= budget.maxVerified) throw Stop(ConstructionStopReason.RETAINED_BUDGET)
                    val prefix = takeNext()
                    if (prefix.state.phase == Phase.DONE) {
                        finish(prefix)
                        continue
                    }
                    if (prefix.state.depth >= budget.maxDepth) { depthPruned++; pruned++; continue }
                    if (expanded >= budget.maxExpandedStates) throw Stop(ConstructionStopReason.EXPANSION_BUDGET)
                    expanded++
                    val actions = grammar.legal(prefix.state)
                    val decisions = ArrayList<ConstructionDecision>()
                    val pending = ArrayList<PendingPrefix>()
                    try {
                        for (action in actions) {
                            checkpoint()
                            if (scores >= budget.maxActionScores) throw Stop(ConstructionStopReason.ACTION_SCORE_BUDGET)
                            scores++
                            val next = grammar.apply(prefix.state, action)
                            val featureStart = nanoTime()
                            val features = try { ConstructionFeatures.extract(grammar, prefix.state, action, next) }
                                finally { featureNanos += (nanoTime() - featureStart).coerceAtLeast(0) }
                            checkpoint()
                            policyCalls++
                            val policyStart = nanoTime()
                            val value = try {
                                policy.score(features.copyOf()).also { require(it.isFinite()) { "Non-finite policy score" } }
                            } catch (e: CodeGenCancelledException) {
                                throw e
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                throw CodeGenCancelledException()
                            } catch (e: Exception) {
                                cancellation.throwIfCancelled()
                                policyFailure = e.javaClass.simpleName
                                throw Stop(ConstructionStopReason.POLICY_FAILURE)
                            } finally {
                                policyNanos += (nanoTime() - policyStart).coerceAtLeast(0)
                            }
                            checkpoint()
                            val decision = ConstructionDecision(prefix.state.key, action.key, features, value)
                            decisions += decision
                            maxDepth = maxOf(maxDepth, next.depth)
                            if (visited.add(next.key)) pending += PendingPrefix(next, prefix, decision)
                            peakPending = maxOf(peakPending, pending.size)
                        }
                        enqueueBatch(pending)
                    } finally {
                        expansions += ConstructionExpansion(prefix.state.key, actions.map { it.key }, decisions.toList())
                    }
                }
                checkpoint()
                if (depthPruned > 0) stopReason = ConstructionStopReason.DEPTH_BUDGET
            } catch (e: Stop) {
                stopReason = e.reason
            }
            cancellation.throwIfCancelled()
            return ConstructionResult(
                accepted.values.toList(), completions.toList(), expansions.toList(),
                ConstructionStats(attempts, expanded, scores, policyCalls, peak, peakPending, maxDepth, verificationNanos, featureNanos, policyNanos,
                    (nanoTime() - started).coerceAtLeast(0), pruned, depthPruned,
                    if (::grammar.isInitialized) grammar.truncatedAnchors else false,
                    traceReferences, budget.maxMillis),
                stopReason, closest, rejections.toMap(), policyFailure,
            )
        }

        private fun checkpoint() {
            cancellation.throwIfCancelled()
            if (nanoTime() - started >= budget.maxMillis * 1_000_000L) throw Stop(ConstructionStopReason.DEADLINE)
        }

        private fun enqueueBatch(pending: List<PendingPrefix>) {
            val families = if (request.scope == CodeGenRequest.Scope.SINGLE) 4 else 2
            val quota = budget.maxFrontier / families
            // Near-tie bands depend on the group's maximum. Trimming after every sibling would
            // discard an entry that belongs to the winning band once later siblings are scored.
            // Pending successors are bounded by maxActionScores and reported separately; only
            // winners allocate their parent + decision trace and enter the bounded frontier.
            // zh-CN: 同分组依赖最高分, 必须整批评分后统一裁剪. 待裁剪后继受动作评分上限约束,
            // 单独统计峰值; 只有胜出的后继才分配完整轨迹并进入有界 frontier.
            for ((family, incoming) in pending.groupBy { it.state.family }) {
                checkpoint()
                val existing = frontier.filter { it.state.family == family }
                val choices: List<ScoredPrefix> = existing + incoming
                val retained = orderPrefixes(choices).take(quota)
                checkpoint()
                frontier.removeAll(existing.toSet())
                frontier += retained.map {
                    when (it) {
                        is Prefix -> it
                        is PendingPrefix -> it.retain()
                    }
                }
                pruned += choices.size - retained.size
                check(frontier.size <= budget.maxFrontier)
                peak = maxOf(peak, frontier.size)
            }
        }

        private fun takeNext(): Prefix {
            val groups = frontier.map { it.state.family?.ordinal ?: -1 }.distinct().sorted()
            val group = groups.firstOrNull { it > familyCursor } ?: groups.first()
            familyCursor = group
            val candidates = frontier.filter { (it.state.family?.ordinal ?: -1) == group }
            return orderPrefixes(candidates).first().also { frontier.remove(it) }
        }

        private fun finish(prefix: Prefix) {
            // Bound references retained by audit traces, as well as the verifier's own caches.
            // zh-CN: 除验证器缓存外, 同时限制审计轨迹持有的节点引用.
            if (traceReferences.toLong() + 2L * request.targets.size > MAX_TRACE_REFERENCES) {
                throw Stop(ConstructionStopReason.TRACE_REFERENCE_BUDGET)
            }
            val plan = grammar.complete(prefix.state)
            val outcome = checkPlan(plan) ?: return
            var minimal = outcome as? Verified<N>
            try {
                if (minimal != null) minimal = simplify(minimal)
            } finally {
                // Retain a just-verified plan even if minimization reaches the wall-clock limit.
                minimal?.let { accepted.putIfAbsent(canonicalKey(it.plan), it) }
                traceReferences += ((outcome as? Verified)?.resolved?.size ?: 0) + (minimal?.resolved?.size ?: 0)
                completions += ConstructionCompletion(plan, outcome, prefix.decisions, minimal)
            }
        }

        private fun checkPlan(plan: LocatePlan): Verification<N>? {
            checkpoint()
            checked[plan.planKey]?.let { return it }
            if (attempts >= budget.maxAttempts) { stopReason = ConstructionStopReason.VERIFICATION_BUDGET; return null }
            attempts++
            val start = nanoTime()
            val outcome = try { verifier.verify(request, plan) } finally { verificationNanos += (nanoTime() - start).coerceAtLeast(0) }
            val references = (outcome as? Verified)?.resolved?.size ?: 0
            while (checked.isNotEmpty() && checkedReferences + references > MAX_CACHED_REFERENCES) {
                val iterator = checked.entries.iterator()
                checkedReferences -= (iterator.next().value as? Verified)?.resolved?.size ?: 0
                iterator.remove()
            }
            if (references <= MAX_CACHED_REFERENCES) { checked[plan.planKey] = outcome; checkedReferences += references }
            if (outcome is Rejected) {
                rejections[outcome.reason] = (rejections[outcome.reason] ?: 0) + 1
                val previous = closest
                if (previous == null || compareValuesBy(outcome, previous, ::failureStage, { it.extraCount + it.missingCount }) < 0) closest = outcome
            }
            return outcome
        }

        private fun simplify(initial: Verified<N>): Verified<N> {
            var current = initial
            for (i in current.plan.selector.conditions.indices.reversed()) {
                val remaining = current.plan.selector.conditions.toMutableList().apply { removeAt(i) }
                val reduced = checkPlan(copy(current.plan, selector = SelectorSpec(remaining)))
                if (reduced is Verified) current = reduced
                if (attempts >= budget.maxAttempts) return current
            }
            val search = current.plan.steps.singleOrNull() as? PlanStep.Search ?: return current
            for (i in search.selector.conditions.indices.reversed()) {
                val inner = (current.plan.steps.single() as PlanStep.Search).selector.conditions.toMutableList().apply { removeAt(i) }
                val reduced = checkPlan(copy(current.plan, steps = listOf(PlanStep.Search(SelectorSpec(inner)))))
                if (reduced is Verified) current = reduced
                if (attempts >= budget.maxAttempts) return current
            }
            return current
        }
    }

    companion object {
        private const val MAX_CACHED_REFERENCES = 200_000
        private const val MAX_TRACE_REFERENCES = 200_000
        const val SCORE_TIE_EPSILON = 1e-6

        /** Canonicalize condition order only: different families/paths/index signs remain distinct. */
        fun canonicalKey(plan: LocatePlan): String {
            fun spec(value: SelectorSpec) = SelectorSpec(value.conditions.sortedWith(compareBy({ it.attr }, { it.value.toString() })))
            return copy(plan, selector = spec(plan.selector), steps = plan.steps.map {
                if (it is PlanStep.Search) PlanStep.Search(spec(it.selector)) else it
            }).planKey
        }

        /** Anchored score bands avoid a non-transitive pairwise epsilon comparator. */
        fun orderDecisions(decisions: List<ConstructionDecision>): List<ConstructionDecision> = orderBands(
            decisions, { it.score }, compareBy({ it.actionKey }, { it.stateKey }),
        )

        private fun <T : ScoredPrefix> orderPrefixes(prefixes: List<T>): List<T> = orderBands(
            prefixes, { it.score }, compareBy({ it.actionKey }, { it.state.key }),
        )

        private fun <T> orderBands(values: List<T>, score: (T) -> Double, ties: Comparator<T>): List<T> {
            val sorted = values.sortedByDescending(score)
            return buildList {
                var start = 0
                while (start < sorted.size) {
                    val anchor = score(sorted[start])
                    var end = start + 1
                    while (end < sorted.size && abs(anchor - score(sorted[end])) <= SCORE_TIE_EPSILON) end++
                    addAll(sorted.subList(start, end).sortedWith(ties))
                    start = end
                }
            }
        }

        private fun copy(plan: LocatePlan, selector: SelectorSpec = plan.selector, steps: List<PlanStep> = plan.steps) =
            LocatePlan(plan.family, selector, steps, plan.cardinality, plan.receiver)

        private fun failureStage(rejected: Rejected): Int = when (rejected.reason) {
            RejectionReason.RECEIVER_STRATEGY_MISMATCH, RejectionReason.RECEIVER_NOT_FOUND, RejectionReason.UNSUPPORTED_ACTION -> 0
            RejectionReason.WRONG_TARGET, RejectionReason.EXTRA_TARGETS, RejectionReason.MISSING_TARGETS, RejectionReason.TARGET_SET_MISMATCH -> 1
            RejectionReason.AMBIGUOUS_ANCHOR -> 2
            else -> 3
        }
    }
}
