package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.rank.Explanation
import org.autojs.autojs.core.accessibility.codegen.rank.Ranker
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.render.ClassicRenderer
import org.autojs.autojs.core.accessibility.codegen.render.FlowRenderer
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.search.SearchBudget
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.automator.UiNode
import java.util.Collections

sealed interface CodeGenOutcome<out N>

data class CodeGenTimings(
    val searchNanos: Long = 0,
    val verificationNanos: Long = 0,
    val rankingNanos: Long = 0,
    val renderingNanos: Long = 0,
    val totalNanos: Long = 0,
)

class CodeGenCandidate<N : UiNode<N>> internal constructor(
    val plan: LocatePlan,
    val code: String,
    val explanation: List<Explanation.Line>,
    val resolved: List<N>,
) {
    val matchedCount: Int get() = resolved.size
}

class CodeGenResult<N : UiNode<N>> internal constructor(
    candidates: List<CodeGenCandidate<N>>,
    val timings: CodeGenTimings,
    val attemptedCandidates: Int,
    val budgetExhausted: Boolean,
) : CodeGenOutcome<N> {
    val candidates: List<CodeGenCandidate<N>> = Collections.unmodifiableList(ArrayList(candidates))
    val preferred: CodeGenCandidate<N> get() = candidates.first()
    val alternatives: List<CodeGenCandidate<N>> get() = candidates.drop(1)
}

data class CodeGenFailure(
    val reason: Reason,
    val requestErrors: List<CodeGenRequest.Error> = emptyList(),
    val closestRejection: Rejected? = null,
    val timings: CodeGenTimings = CodeGenTimings(),
) : CodeGenOutcome<Nothing> {
    enum class Reason { INVALID_REQUEST, NO_CANDIDATES, ALL_REJECTED, CANCELLED, BUDGET_EXCEEDED }
}

/**
 * Runs on the calling thread; the UI supplies its worker and cancellation signal. Only verified
 * candidates reach ranking/rendering. Timings are returned and sent to the optional logging sink.
 * zh-CN: 在调用线程运行, 由界面提供工作线程与取消信号. 只有通过验证的候选进入排序与渲染.
 * 各阶段耗时随结果返回, 并交给可选日志接收器.
 */
class CodeGenEngine(
    private val ranker: Ranker = RuleRanker(),
    private val budget: SearchBudget = SearchBudget(),
    private val logTimings: (CodeGenTimings) -> Unit = {},
) {
    fun <N : UiNode<N>> generate(
        root: N,
        request: CodeGenRequest<N>,
        cancellation: CancellationSignal = CancellationSignal(),
    ): CodeGenOutcome<N> {
        val started = System.nanoTime()
        var searchNanos = 0L
        var verificationNanos = 0L
        var rankingNanos = 0L
        var renderingNanos = 0L
        fun timings() = CodeGenTimings(
            searchNanos.coerceAtLeast(0), verificationNanos, rankingNanos, renderingNanos,
            (System.nanoTime() - started).coerceAtLeast(0),
        ).also(logTimings)
        try {
            cancellation.throwIfCancelled()
            val stableRequest = request.copy(
                targets = Collections.unmodifiableList(ArrayList(request.targets)),
                attributes = Collections.unmodifiableSet(LinkedHashSet(request.attributes)),
            )
            val errors = stableRequest.validate()
            if (errors.isNotEmpty()) return CodeGenFailure(CodeGenFailure.Reason.INVALID_REQUEST, errors, timings = timings())
            val searchStarted = System.nanoTime()
            val search = try {
                CandidateSearch(root, cancellation, budget).search(stableRequest)
            } finally {
                searchNanos = System.nanoTime() - searchStarted
            }
            verificationNanos = search.verificationNanos
            searchNanos -= verificationNanos
            cancellation.throwIfCancelled()
            if (search.candidates.isEmpty()) {
                val reason = when {
                    search.budgetExhausted -> CodeGenFailure.Reason.BUDGET_EXCEEDED
                    search.attemptedCount == 0 -> CodeGenFailure.Reason.NO_CANDIDATES
                    else -> CodeGenFailure.Reason.ALL_REJECTED
                }
                return CodeGenFailure(reason, closestRejection = search.closestRejection, timings = timings())
            }
            val rankingStarted = System.nanoTime()
            val ranked = try {
                ranker.rank(stableRequest, search.candidates)
            } finally {
                rankingNanos = System.nanoTime() - rankingStarted
            }
            cancellation.throwIfCancelled()
            // The ranker contract is a permutation of the verified input, never a source of plans.
            // zh-CN: 排序器只能重排通过验证的输入, 不能成为计划来源.
            val verifiedByKey = search.candidates.associateBy { it.plan.planKey }
            check(ranked.size == verifiedByKey.size && ranked.map { it.plan.planKey }.toSet() == verifiedByKey.keys) {
                "Ranker must preserve the verified candidate set"
            }
            val renderStarted = System.nanoTime()
            val rendered = try {
                ranked.take(MAX_RESULTS).map { rankedCandidate ->
                    cancellation.throwIfCancelled()
                    val verified = verifiedByKey.getValue(rankedCandidate.plan.planKey)
                    val code = when (stableRequest.style) {
                        CodeGenRequest.Style.CLASSIC -> ClassicRenderer().render(stableRequest, verified.plan)
                        CodeGenRequest.Style.FLOW -> FlowRenderer().render(stableRequest, verified.plan)
                    }
                    CodeGenCandidate(verified.plan, code, Explanation.describe(stableRequest, verified), verified.resolved)
                }
            } finally {
                renderingNanos = System.nanoTime() - renderStarted
            }
            cancellation.throwIfCancelled()
            return CodeGenResult(rendered, timings(), search.attemptedCount, search.budgetExhausted)
        } catch (_: CodeGenCancelledException) {
            return CodeGenFailure(CodeGenFailure.Reason.CANCELLED, timings = timings())
        }
    }

    companion object {
        const val MAX_RESULTS = 10
    }
}
