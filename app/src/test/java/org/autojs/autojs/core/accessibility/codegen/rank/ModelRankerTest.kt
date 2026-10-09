package org.autojs.autojs.core.accessibility.codegen.rank

import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.random.Random

class ModelRankerTest {
    private val featureCount = FeatureSchema.names.size
    private val ruleIndex = FeatureSchema.names.indexOf("rule_rank_norm")
    private fun model(features: Int = featureCount) = ObliviousTreeModel(
        features, byteArrayOf(1), intArrayOf(0), intArrayOf(0), intArrayOf(0), floatArrayOf(0f), floatArrayOf(0f, 0f), 0.0, 1.0,
    )

    private fun example(): Pair<CodeGenRequest<FakeUiNode>, List<Verified<FakeUiNode>>> {
        val root = FakeUiNode.root {
            child("target") { id = "app:id/target"; desc = "Target"; text = "Target"; className = "android.widget.Button" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val verifier = PlanVerifier(root)
        val plans = listOf(Condition("id", "app:id/target"), Condition("desc", "Target"), Condition("text", "Target"))
            .map { LocatePlan(LocatePlan.Family.SELF, SelectorSpec(it)) }
        return request to plans.map { verifier.verify(request, it) as Verified<FakeUiNode> }
    }

    private fun ranker(clock: () -> Long = { 0L }, score: (FloatArray) -> Double) =
        ModelRanker(model(), RuleRanker(), 150L, clock, score)

    @Test
    fun modelChangesOrderButKeepsEveryOriginalVerifiedIdentity() {
        val (request, candidates) = example()
        val ranker = ranker { it[ruleIndex].toDouble() }
        val expected = RuleRanker().rank(request, candidates).reversed().map { it.verified }
        repeat(5) { seed ->
            val input = candidates.shuffled(Random(seed))
            val result = ranker.rank(request, input)
            assertEquals(expected.map { it.plan.planKey }, result.map { it.plan.planKey })
            result.forEachIndexed { index, ranked -> assertSame(expected[index], ranked.verified) }
            assertEquals(input, candidates.shuffled(Random(seed)))
            assertThrows(UnsupportedOperationException::class.java) { (result as MutableList<*>).clear() }
        }
    }

    @Test
    fun constantRealModelReproducesRuleOrderingOnAllThreeCapturedFixtureGroups() {
        for (name in listOf("login", "list-duplicates", "cross-package-id")) {
            val root = SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/$name.layout.json")).toURI()))
            val target = if (name == "list-duplicates") root.child(1)!!.child(1)!! else root.child(1)!!
            val request = CodeGenRequest(listOf(target))
            val candidates = CandidateSearch(root).search(request).candidates
            assertTrue(candidates.isNotEmpty())
            val expected = RuleRanker().rank(request, candidates)
            val result = ModelRanker(model(), budgetMs = 60_000).rank(request, candidates.reversed())
            assertEquals(expected.map { it.plan.planKey }, result.map { it.plan.planKey })
            result.forEach { ranked -> assertSame(candidates.single { it.plan == ranked.plan }, ranked.verified) }
        }
    }

    @Test
    fun exactAndNearTiesUseRuleOrder() {
        val (request, candidates) = example()
        for (scores in listOf(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, .25e-6, .5e-6))) {
            val ranker = ranker { scores[(it[ruleIndex] * candidates.size).toInt()] }
            assertEquals(candidates, ranker.rank(request, candidates.reversed()).map { it.verified })
        }
    }

    @Test
    fun nearTieChainMatchesPythonAnchoredBatchesWithoutComparatorCycles() {
        val (request, candidates) = example()
        val scores = doubleArrayOf(0.0, .75e-6, 1.5e-6)
        val ranker = ranker { scores[(it[ruleIndex] * candidates.size).toInt()] }
        val expected = listOf(candidates[1], candidates[2], candidates[0])
        repeat(5) { assertEquals(expected, ranker.rank(request, candidates.shuffled(Random(it))).map { result -> result.verified }) }
    }

    @Test
    fun budgetCoversFeaturePreparationScoringAndFinalSorting() {
        val (request, candidates) = example()
        // Checkpoint 2 follows preparation; checkpoint 11 is in the shared sorting stage.
        for (timeoutCall in listOf(2, 5, 11)) {
            var calls = 0
            val ranker = ranker(clock = { if (calls++ >= timeoutCall) 151_000_000L else 0L }) { 0.0 }
            assertEquals(RankerUnavailable.Reason.TIMEOUT, assertThrows(RankerUnavailable::class.java) {
                ranker.rank(request, candidates)
            }.reason)
        }
        var now = 0L
        val ranker = ranker(clock = { now }) { now = 151_000_000L; 0.0 }
        assertEquals(RankerUnavailable.Reason.TIMEOUT, assertThrows(RankerUnavailable::class.java) {
            ranker.rank(request, candidates)
        }.reason)
    }

    @Test
    fun scoringErrorsAndNonfiniteScoresExposeStructuredFailure() {
        val (request, candidates) = example()
        val cause = IllegalArgumentException("inference failed")
        val failure = assertThrows(RankerUnavailable::class.java) { ranker { throw cause }.rank(request, candidates) }
        assertEquals(RankerUnavailable.Reason.SCORING_FAILED, failure.reason)
        assertSame(cause, failure.cause)
        for (score in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(RankerUnavailable.Reason.SCORING_FAILED,
                assertThrows(RankerUnavailable::class.java) { ranker { score }.rank(request, candidates) }.reason)
        }
    }

    @Test
    fun incompatibleDimensionsAndInvalidGroupsAreNotSilentlyScored() {
        val (request, candidates) = example()
        assertEquals(RankerUnavailable.Reason.INCOMPATIBLE_FEATURES,
            assertThrows(RankerUnavailable::class.java) { ModelRanker(model(1)).rank(request, candidates) }.reason)
        assertEquals(RankerUnavailable.Reason.FEATURE_EXTRACTION_FAILED,
            assertThrows(RankerUnavailable::class.java) { ModelRanker(model()).rank(request, candidates + candidates[0]) }.reason)
    }

    @Test
    fun cancellationAndFatalErrorsAreNeverTurnedIntoFallback() {
        val (request, candidates) = example()
        val cancelled = CancellationException("cancelled")
        assertSame(cancelled, assertThrows(CancellationException::class.java) { ranker { throw cancelled }.rank(request, candidates) })
        val signal = CodeGenCancelledException()
        assertSame(signal, assertThrows(CodeGenCancelledException::class.java) { ranker { throw signal }.rank(request, candidates) })
        val fatal = AssertionError("fatal")
        assertSame(fatal, assertThrows(AssertionError::class.java) { ranker { throw fatal }.rank(request, candidates) })
        try {
            val interrupted = InterruptedException("interrupted")
            assertSame(interrupted, assertThrows(InterruptedException::class.java) { ranker { throw interrupted }.rank(request, candidates) })
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        try {
            Thread.currentThread().interrupt()
            assertThrows(CodeGenCancelledException::class.java) { ModelRanker(model()).rank(request, candidates) }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun legacySchemaModelsReceiveProjectedVectorsAndNeverScoreUnseenFamilies() {
        val (request, candidates) = example()
        val legacyRuleIndex = FeatureSchema.names(1).indexOf("rule_rank_norm")
        val legacy = ModelRanker(model(FeatureSchema.names(1).size), RuleRanker(), 150L, { 0L }, { vector ->
            assertEquals(FeatureSchema.names(1).size, vector.size)
            -vector[legacyRuleIndex].toDouble()
        }, featureSchemaVersion = 1)
        assertEquals(RuleRanker().rank(request, candidates).map { it.plan.planKey }, legacy.rank(request, candidates).map { it.plan.planKey })
        assertEquals(RankerUnavailable.Reason.INCOMPATIBLE_FEATURES,
            assertThrows(RankerUnavailable::class.java) { ModelRanker(model(), featureSchemaVersion = 1).rank(request, candidates) }.reason)
        assertThrows(IllegalArgumentException::class.java) { ModelRanker(model(), featureSchemaVersion = 3) }

        val root = FakeUiNode.root {
            repeat(2) { index ->
                child("row$index") {
                    child("icon$index") { id = "app:id/icon" }
                    child("title$index") { text = "Title $index" }
                }
            }
        }
        val relativeRequest = CodeGenRequest(listOf(root.find("icon1")))
        val verifier = PlanVerifier(root)
        val plans = listOf(
            LocatePlan(LocatePlan.Family.SELF_RELATIVE, SelectorSpec(Condition("id", "app:id/icon")),
                listOf(PlanStep.Up(1), PlanStep.Match(SelectorSpec(Condition("text", "Title 1"))))),
            LocatePlan(LocatePlan.Family.SELF_INDEX, SelectorSpec(Condition("id", "app:id/icon")), listOf(PlanStep.Index(1))),
        )
        val mixed = plans.map { verifier.verify(relativeRequest, it) as Verified<FakeUiNode> }
        assertEquals(RankerUnavailable.Reason.INCOMPATIBLE_FEATURES,
            assertThrows(RankerUnavailable::class.java) { legacy.rank(relativeRequest, mixed) }.reason)
        assertEquals(2, ModelRanker(model(), budgetMs = 60_000).rank(relativeRequest, mixed).size)
        assertEquals(1, legacy.rank(relativeRequest, mixed.drop(1)).size)
    }

    @Test
    fun emptyGroupDoesNotComputeFeaturesAndBudgetMustBeValid() {
        val (request, _) = example()
        assertTrue(ranker { error("No candidates to score") }.rank(request.copy(targets = emptyList()), emptyList()).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { ModelRanker(model(), budgetMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { ModelRanker(model(), budgetMs = Long.MAX_VALUE) }
    }

    @Test
    fun sharedScoreOrderingRejectsIncompleteRuleRanksAndNonfiniteScores() {
        assertThrows(IllegalArgumentException::class.java) { ModelScoreOrder.order(doubleArrayOf(1.0), intArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { ModelScoreOrder.order(doubleArrayOf(1.0, 2.0), intArrayOf(0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { ModelScoreOrder.order(doubleArrayOf(1.0, 2.0), intArrayOf(0, 2)) }
        assertThrows(IllegalArgumentException::class.java) { ModelScoreOrder.order(doubleArrayOf(Double.NaN), intArrayOf(0)) }
        assertTrue(ModelScoreOrder.order(doubleArrayOf(), intArrayOf()).isEmpty())
    }
}
