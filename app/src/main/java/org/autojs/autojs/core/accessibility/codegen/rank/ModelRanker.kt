package org.autojs.autojs.core.accessibility.codegen.rank

import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.features.CandidateFeatures
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.UiNode
import java.util.Collections
import java.util.concurrent.CancellationException

/** Reorders the original verified objects, using one shared feature context for the group.
 * Near-score ties use disjoint batches anchored at each descending batch maximum, matching
 * tools/codegen-model/train/data.py. Pairwise epsilon comparison would not be transitive.
 * A model of an earlier feature schema receives projected vectors and is unavailable for any
 * group containing a family that schema has not seen.
 * zh-CN: 使用整组共用特征上下文重排原始已验证对象; 近似同分以各批最高分为锚点分组, 与 Python 一致.
 * 旧 schema 的模型接收投影后的向量, 遇到其 schema 未见过的计划族时整组不可用. */
class ModelRanker internal constructor(
    private val model: ObliviousTreeModel,
    private val ruleRanker: RuleRanker,
    budgetMs: Long,
    private val nowNanos: () -> Long,
    private val inference: (FloatArray) -> Double,
    private val featureSchemaVersion: Int = FeatureSchema.featureSchemaVersion,
) : Ranker {
    constructor(model: ObliviousTreeModel, ruleRanker: RuleRanker = RuleRanker(), budgetMs: Long = 150, featureSchemaVersion: Int = FeatureSchema.featureSchemaVersion) :
        this(model, ruleRanker, budgetMs, System::nanoTime, model::score, featureSchemaVersion)

    private val budgetNanos = budgetMs.also { require(it in 1..Long.MAX_VALUE / 1_000_000L) { "Invalid model ranking budget" } } * 1_000_000L
    private val schemaNames = FeatureSchema.names(featureSchemaVersion)
    private val knownFamilies = FeatureSchema.families(featureSchemaVersion)

    override fun <N : UiNode<N>> rank(request: CodeGenRequest<N>, verified: List<Verified<N>>): List<Ranked<N>> {
        if (verified.isEmpty()) return emptyList()
        var failure = RankerUnavailable.Reason.FEATURE_EXTRACTION_FAILED
        try {
            val started = nowNanos()
            fun checkBudget() {
                if (Thread.currentThread().isInterrupted) throw CodeGenCancelledException()
                if (nowNanos() - started > budgetNanos) throw RankerUnavailable(RankerUnavailable.Reason.TIMEOUT)
            }
            checkBudget()
            if (model.featureCount != schemaNames.size) throw RankerUnavailable(RankerUnavailable.Reason.INCOMPATIBLE_FEATURES)
            if (verified.any { it.plan.family !in knownFamilies }) throw RankerUnavailable(RankerUnavailable.Reason.INCOMPATIBLE_FEATURES)
            val candidates = ArrayList(verified)
            val features = CandidateFeatures.prepare(request, candidates)
            checkBudget()
            val ruleOrder = ruleRanker.rank(request, candidates).withIndex().associate { it.value.plan.planKey to it.index }
            checkBudget()
            val scores = DoubleArray(candidates.size)
            val ruleRanks = IntArray(candidates.size)
            for ((index, candidate) in candidates.withIndex()) {
                failure = RankerUnavailable.Reason.FEATURE_EXTRACTION_FAILED
                val vector = FeatureSchema.project(features.extract(candidate), featureSchemaVersion)
                checkBudget()
                failure = RankerUnavailable.Reason.SCORING_FAILED
                val score = inference(vector)
                check(score.isFinite()) { "Model score must be finite" }
                scores[index] = score
                ruleRanks[index] = ruleOrder.getValue(candidate.plan.planKey)
                checkBudget()
            }
            failure = RankerUnavailable.Reason.SORTING_FAILED
            val order = ModelScoreOrder.order(scores, ruleRanks, ::checkBudget)
            val result = order.map { Ranked(candidates[it], scores[it]) }
            checkBudget()
            return Collections.unmodifiableList(result)
        } catch (error: CodeGenCancelledException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: RankerUnavailable) {
            throw error
        } catch (error: Exception) {
            throw RankerUnavailable(failure, error)
        }
    }

    companion object {
        const val SCORE_EPSILON = 1e-6
    }
}
