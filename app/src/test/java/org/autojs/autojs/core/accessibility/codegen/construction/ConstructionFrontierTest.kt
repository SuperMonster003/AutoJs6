package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionFrontierTest {
    private val budget = ConstructionBudget(maxFrontier = 16, maxExpandedStates = 48, maxMillis = 30_000)

    @Test
    fun defaultRetainsTheOriginalControllerWithoutChangingTracesOrCounters() {
        val root = nestedCollection()
        val request = collection(root)
        val original = ConstructionSearch(root, budget = budget, nanoTime = { 0L }).search(request)
        val explicit = ConstructionSearch(root, budget = budget, nanoTime = { 0L },
            frontierMode = ConstructionFrontierMode.FAMILY_SCORE).search(request)
        assertEquals(original.stats, explicit.stats)
        assertEquals(0, original.stats.peakFairCursorAnchors)
        assertEquals(original.stopReason, explicit.stopReason)
        assertEquals(trace(original), trace(explicit))
        assertEquals(original.candidates.map { it.plan.planKey }, explicit.candidates.map { it.plan.planKey })
    }

    @Test
    fun aHighScoringAnchorCannotCrowdOtherRetainedAnchorsOutOfExpansion() {
        val root = nestedCollection()
        val policy = ConstructionPolicy {
            1_000.0 * ConstructionFeatures.value(it, "anchor_depth") + ConstructionFeatures.value(it, "outer_count")
        }
        val legacy = search(root, collection(root), policy, ConstructionFrontierMode.FAMILY_SCORE)
        val fair = search(root, collection(root), policy)
        fun expandedAnchors(result: ConstructionResult<FakeUiNode>) = result.expansions
            .map { it.stateKey.split('|') }
            .filter { it[1] == "OUTER" && it[2] == "CONTAINER_ITEMS" }
            .map { it[3].toInt() }.toSet()
        // The tree has three common ancestor anchors: root, outer panel, and inner panel.
        // Legacy scoring spends its quota on descendants of the deepest anchor alone.
        assertEquals(setOf(2), expandedAnchors(legacy))
        assertEquals(setOf(0, 1, 2), expandedAnchors(fair))
        assertTrue(fair.stats.peakFrontier <= budget.maxFrontier)
        assertTrue(fair.stats.peakFairCursorAnchors in 1..budget.maxFrontier)
    }

    @Test
    fun lowScoringStageTransitionsAndTerminalsReceiveTurnsDespiteOuterPrefixPressure() {
        val root = flatCollection()
        val request = collection(root)
        val policy = ConstructionPolicy {
            1_000.0 * ConstructionFeatures.value(it, "outer_count") -
                1_000_000.0 * (ConstructionFeatures.value(it, "operation_end_outer") + ConstructionFeatures.value(it, "operation_finish"))
        }
        val legacy = search(root, request, policy, ConstructionFrontierMode.FAMILY_SCORE)
        val fair = search(root, request, policy)
        assertFalse(legacy.expansions.any { it.stateKey.startsWith("g1|INNER|CONTAINER_ITEMS|") })
        assertTrue(fair.expansions.any { it.stateKey.startsWith("g1|INNER|CONTAINER_ITEMS|") })
        assertTrue(fair.completions.any { it.plan.family == Family.CONTAINER_ITEMS })
        assertTrue(fair.stats.prunedPrefixes > 0)
    }

    @Test
    fun thePolicyStillSelectsBetweenAlternativesInTheSameAnchorAndPhase() {
        val root = FakeUiNode.root {
            child("target") { id = "example:id/send"; text = "Send"; className = "Button" }
            child("peer") { text = "Cancel"; className = "Button" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        fun firstAddedCondition(preference: String): String {
            val result = search(root, request, ConstructionPolicy {
                20.0 * ConstructionFeatures.value(it, preference) - ConstructionFeatures.value(it, "outer_count")
            })
            return result.expansions.map { it.stateKey.split('|') }
                .first { it[1] == "OUTER" && it[2] == "SELF" && it[4].isNotEmpty() }[4]
        }
        assertEquals("0", firstAddedCondition("prefix_id"))
        assertEquals("1", firstAddedCondition("prefix_text"))
        assertNotEquals(firstAddedCondition("prefix_id"), firstAddedCondition("prefix_text"))
    }

    @Test
    fun bucketOverflowRemainsDeterministicWithinTheOriginalHardCaps() {
        val root = FakeUiNode.root {
            repeat(40) { index -> child("peer$index") { text = "Choice $index"; className = "Text" } }
            child("target") { text = "Choose"; className = "Button" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        val limits = budget.copy(maxFrontier = 9, maxAttempts = 40, maxVerified = 8, maxActionScores = 450, maxExpandedStates = 60)
        fun run() = search(root, request, ConstructionHeuristicPolicy, limits = limits)
        val first = run()
        val second = run()
        assertEquals(first.stats, second.stats)
        assertEquals(first.stopReason, second.stopReason)
        assertEquals(trace(first), trace(second))
        assertEquals(first.candidates.map { it.plan.planKey }, second.candidates.map { it.plan.planKey })
        assertTrue(first.stats.prunedPrefixes > 0)
        // Four fixed family quotas of floor(9 / 4) cannot borrow a hidden ninth slot.
        assertTrue(first.stats.peakFrontier <= 8)
        assertTrue(first.stats.peakFairCursorAnchors in 1..8)
        assertTrue(first.stats.attemptedCount <= limits.maxAttempts)
        assertTrue(first.stats.actionScores <= limits.maxActionScores)
        assertTrue(first.stats.peakPendingPrefixes <= limits.maxActionScores)
        assertTrue(first.stats.expandedStates <= limits.maxExpandedStates)
        assertTrue(first.candidates.size <= limits.maxVerified)
        assertTrue(first.candidates.all { PlanVerifier(root).verify(request, it.plan) is Verified })
    }

    @Test
    fun fairModePropagatesCancellationAndChecksTheSameDeadline() {
        val root = flatCollection()
        val request = collection(root)
        val signal = CancellationSignal()
        var calls = 0
        val policy = ConstructionPolicy {
            if (++calls == 8) signal.cancel()
            1.0
        }
        assertThrows(CodeGenCancelledException::class.java) {
            ConstructionSearch(root, signal, budget, policy, nanoTime = { 0L },
                frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
        }
        assertEquals(8, calls)
        var clock = 0L
        val timed = ConstructionSearch(root, budget = budget.copy(maxMillis = 1),
            nanoTime = { clock.also { clock += 1_000_000L } },
            frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
        assertEquals(ConstructionStopReason.DEADLINE, timed.stopReason)
        assertTrue(timed.completions.isEmpty())
    }

    private fun search(
        root: FakeUiNode,
        request: CodeGenRequest<FakeUiNode>,
        policy: ConstructionPolicy,
        mode: ConstructionFrontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR,
        limits: ConstructionBudget = budget,
    ) = ConstructionSearch(root, budget = limits, policy = policy, nanoTime = { 0L }, frontierMode = mode).search(request)

    private fun trace(result: ConstructionResult<FakeUiNode>) = result.expansions.map { expansion ->
        expansion.stateKey to expansion.scoredActions.map { it.actionKey to it.score }
    }

    private fun collection(root: FakeUiNode) = CodeGenRequest(listOf(root.find("first"), root.find("second")), Scope.COLLECTION)

    private fun nestedCollection() = FakeUiNode.root {
        id = "example:id/root"
        child("outer") {
            id = "example:id/outer"
            child("inner") {
                id = "example:id/inner"
                child("first") { className = "Button"; text = "First" }
                child("second") { className = "Button"; text = "Second" }
            }
        }
    }

    private fun flatCollection() = FakeUiNode.root {
        id = "example:id/root"
        child("first") { className = "Button"; text = "First" }
        child("second") { className = "Button"; text = "Second" }
    }
}
