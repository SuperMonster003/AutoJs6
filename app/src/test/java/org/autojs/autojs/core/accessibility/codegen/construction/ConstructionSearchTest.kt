package org.autojs.autojs.core.accessibility.codegen.construction

import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Operation
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.State
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConstructionSearchTest {
    private fun fixture(name: String) = SnapshotNode.load(File(requireNotNull(javaClass.getResource("/codegen/$name.layout.json")).toURI()))
    private val budget = ConstructionBudget(maxMillis = 30_000)

    @Test
    fun completionRequiresAuditablePrePlanActionsAndRetainsFailedTerminals() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!), action = Action.CLICK)
        val result = ConstructionSearch(root, budget = budget).search(request)
        assertTrue(result.candidates.isNotEmpty())
        assertTrue(result.completions.any { it.verification is Verified })
        val failureExploration = ConstructionSearch(root, budget = budget.copy(maxAttempts = 20), policy = ConstructionPolicy {
            100.0 * ConstructionFeatures.value(it, "operation_finish") - ConstructionFeatures.value(it, "outer_count")
        }).search(request)
        assertTrue(failureExploration.completions.any { it.verification is Rejected })
        val expansions = result.expansions.associateBy { it.stateKey }
        for (completion in result.completions) {
            assertTrue(completion.decisions.size >= 2)
            assertTrue(completion.decisions.first().actionKey.startsWith("family:"))
            assertEquals("finish", completion.decisions.last().actionKey)
            for (decision in completion.decisions) {
                assertTrue(decision.actionKey in expansions.getValue(decision.stateKey).legalActionKeys)
                assertEquals(ConstructionFeatures.count, decision.features.size)
                assertTrue(decision.features.all(Float::isFinite))
                assertTrue(decision.score.isFinite())
            }
        }
        assertTrue(result.candidates.all { it.resolved == request.targets })
    }

    @Test
    fun changingPolicyChangesConstructionBeforeFinalCandidateSelection() {
        val root = FakeUiNode.root {
            child("target") { id = "demo:id/submit"; text = "Continue"; className = "Button" }
            child("other") { text = "Other"; className = "Button" }
        }
        val request = CodeGenRequest(listOf(root.find("target")))
        fun search(prefer: String) = ConstructionSearch(root,
            budget = budget.copy(maxVerified = 1),
            policy = ConstructionPolicy { features ->
                20.0 * ConstructionFeatures.value(features, prefer) -
                    2.0 * ConstructionFeatures.value(features, "outer_count") +
                    4.0 * ConstructionFeatures.value(features, "operation_finish")
            },
        ).search(request)
        val byId = search("prefix_id")
        val byText = search("prefix_text")
        assertEquals(1, byId.candidates.size)
        assertEquals(1, byText.candidates.size)
        assertNotEquals(byId.candidates.single().plan.planKey, byText.candidates.single().plan.planKey)
        val idTrace = byId.completions.first { it.verification is Verified }.decisions.map { it.actionKey }
        val textTrace = byText.completions.first { it.verification is Verified }.decisions.map { it.actionKey }
        assertNotEquals(idTrace, textTrace)
        assertTrue(idTrace.any { it.endsWith(":id") })
        assertTrue(textTrace.any { it.endsWith(":text") })
    }

    @Test
    fun fiveFamiliesUseTheExistingVerifierAndExactCollectionSemantics() {
        val login = fixture("login")
        val single = ConstructionSearch(login, budget = budget).search(CodeGenRequest(listOf(login.child(2)!!)))
        assertEquals(setOf(Family.SELF, Family.SELF_INDEX, Family.ANCHOR_PATH, Family.ANCHOR_SEARCH), single.candidates.map { it.plan.family }.toSet())
        val root = fixture("list-duplicates")
        val targets = (0 until 20).map { root.child(it)!!.child(1)!! }
        val collection = ConstructionSearch(root, budget = budget).search(CodeGenRequest(targets, Scope.COLLECTION, action = Action.CLICK))
        assertEquals(setOf(Family.SELF, Family.CONTAINER_ITEMS), collection.candidates.map { it.plan.family }.toSet())
        assertTrue(collection.candidates.all { it.resolved.toSet() == targets.toSet() })
        assertTrue(collection.completions.none { it.plan.family == Family.SELF_INDEX || it.plan.family == Family.ANCHOR_PATH })
    }

    @Test
    fun attributesAndNearestActionReceiverCannotBeChangedByPolicy() {
        val root = FakeUiNode.root {
            child("button", clickable = true) {
                id = "demo:id/button"; text = "dynamic 123"; className = "Button"
                child("label") { text = "Tap 123"; desc = "description"; className = "Text" }
            }
        }
        val result = ConstructionSearch(root, budget = budget).search(CodeGenRequest(
            listOf(root.find("label")), action = Action.CLICK, attributes = emptySet(),
        ))
        assertTrue(result.candidates.isNotEmpty())
        for (candidate in result.candidates) {
            assertEquals(PlanReceiver.Up(1), candidate.plan.receiver)
            val conditions = candidate.plan.selector.conditions + candidate.plan.steps.filterIsInstance<PlanStep.Search>().flatMap { it.selector.conditions }
            assertTrue(conditions.none { it.attr in setOf("id", "text", "desc") })
        }
        assertFalse(root.find("label").isRecycled)
    }

    @Test
    fun crossPackageIdCollisionCopiesTheQualifiedCapturedId() {
        val root = fixture("cross-package-id")
        val result = ConstructionSearch(root, budget = budget).search(CodeGenRequest(listOf(root.child(1)!!)))
        val conditions = result.completions.flatMap {
            it.plan.selector.conditions + it.plan.steps.filterIsInstance<PlanStep.Search>().flatMap { step -> step.selector.conditions }
        }.filter { it.attr == "id" }
        assertTrue(conditions.isNotEmpty())
        assertTrue(conditions.all { ":id/" in it.value as String })
    }

    @Test
    fun hardCountersIncludeSimplificationAndDoNotLabelUnexploredPrefixes() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!))
        val limited = budget.copy(maxAttempts = 12, maxVerified = 3, maxFrontier = 8)
        val first = ConstructionSearch(root, budget = limited).search(request)
        val second = ConstructionSearch(root, budget = limited).search(request)
        assertTrue(first.stats.attemptedCount <= 12)
        assertTrue(first.candidates.size <= 3)
        assertTrue(first.stats.peakFrontier <= 8)
        assertTrue(first.stats.peakPendingPrefixes <= limited.maxActionScores)
        assertTrue(first.stats.actionScores <= limited.maxActionScores)
        assertTrue(first.stats.maximumDepth <= limited.maxDepth)
        assertEquals(first.candidates.map { it.plan.planKey }, second.candidates.map { it.plan.planKey })
        assertEquals(first.stats.attemptedCount, second.stats.attemptedCount)
        val unexpanded = ConstructionSearch(root, budget = budget.copy(maxExpandedStates = 0)).search(request)
        assertEquals(ConstructionStopReason.EXPANSION_BUDGET, unexpanded.stopReason)
        assertTrue(unexpanded.completions.isEmpty())
        assertEquals(0, unexpanded.stats.attemptedCount)
        val unscored = ConstructionSearch(root, budget = budget.copy(maxActionScores = 0)).search(request)
        assertEquals(ConstructionStopReason.ACTION_SCORE_BUDGET, unscored.stopReason)
        assertTrue(unscored.completions.isEmpty())
        assertTrue(unscored.expansions.single().legalActionKeys.isNotEmpty())
        assertTrue(unscored.expansions.single().scoredActions.isEmpty())
        val unverified = ConstructionSearch(root, budget = budget.copy(maxAttempts = 0)).search(request)
        assertEquals(ConstructionStopReason.VERIFICATION_BUDGET, unverified.stopReason)
        assertEquals(0, unverified.stats.attemptedCount)
    }

    @Test
    fun deadlineAndDepthHaveExplicitStopReasons() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!))
        var clock = 0L
        val deadline = ConstructionSearch(root, budget = budget.copy(maxMillis = 1), nanoTime = { clock.also { clock += 1_000_000L } }).search(request)
        assertEquals(ConstructionStopReason.DEADLINE, deadline.stopReason)
        assertEquals(1L, deadline.stats.hardDeadlineMillis)
        assertTrue(deadline.completions.isEmpty())
        val depth = ConstructionSearch(root, budget = budget.copy(maxDepth = 1)).search(request)
        assertEquals(ConstructionStopReason.DEPTH_BUDGET, depth.stopReason)
        assertTrue(depth.stats.depthPrunedPrefixes > 0)
        assertTrue(depth.completions.isEmpty())
    }

    @Test
    fun modelFailureRetainsVerifiedCandidatesAndCancellationPropagates() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!))
        var calls = 0
        val failing = ConstructionPolicy { features ->
            if (++calls == 800) error("injected policy failure")
            ConstructionHeuristicPolicy.score(features)
        }
        val result = ConstructionSearch(root, budget = budget, policy = failing).search(request)
        assertEquals(ConstructionStopReason.POLICY_FAILURE, result.stopReason)
        assertTrue(result.candidates.isNotEmpty())
        assertEquals(800, result.stats.policyCalls)
        assertTrue(result.candidates.all { PlanVerifier(root).verify(request, it.plan) is Verified })
        val signal = CancellationSignal()
        val cancelling = ConstructionPolicy { signal.cancel(); 1.0 }
        assertThrows(CodeGenCancelledException::class.java) {
            ConstructionSearch(root, signal, budget, cancelling).search(request)
        }
        assertThrows(CodeGenCancelledException::class.java) { ConstructionSearch(root, signal, budget).search(request) }
    }

    @Test
    fun nonFinitePolicyAndOversizedCaptureCannotEscapeBounds() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!))
        val bad = ConstructionSearch(root, budget = budget, policy = ConstructionPolicy { Double.NaN }).search(request)
        assertEquals(ConstructionStopReason.POLICY_FAILURE, bad.stopReason)
        assertEquals(1, bad.stats.policyCalls)
        val oversized = ConstructionSearch(root, budget = budget.copy(maxCapturedNodes = 1)).search(request)
        assertEquals(ConstructionStopReason.CAPTURE_LIMIT, oversized.stopReason)
        assertEquals(0, oversized.stats.actionScores)
    }

    @Test
    fun interruptedPolicyRemainsCancellationEvenIfABlockingCallClearedItsFlag() {
        val root = fixture("login")
        val request = CodeGenRequest(listOf(root.child(2)!!))
        try {
            assertThrows(CodeGenCancelledException::class.java) {
                ConstructionSearch(root, budget = budget, policy = ConstructionPolicy { throw InterruptedException() }).search(request)
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun canonicalKeysNormalizeOnlyConditionsAndTieBandsAreTransitive() {
        val a = Condition("className", "Button")
        val b = Condition("text", "Continue")
        val first = LocatePlan(Family.SELF, SelectorSpec(a, b))
        val second = LocatePlan(Family.SELF, SelectorSpec(b, a))
        assertNotEquals(first.planKey, second.planKey)
        assertEquals(ConstructionSearch.canonicalKey(first), ConstructionSearch.canonicalKey(second))
        assertNotEquals(ConstructionSearch.canonicalKey(first), ConstructionSearch.canonicalKey(LocatePlan(Family.SELF_INDEX, SelectorSpec(a, b), listOf(PlanStep.Index(0)))))
        fun decision(key: String, value: Double) = ConstructionDecision("state", key, floatArrayOf(), value)
        val ordered = ConstructionSearch.orderDecisions(listOf(decision("a", 1.0), decision("b", 1.0000007), decision("c", 1.0000014)))
        assertEquals(listOf("b", "c", "a"), ordered.map { it.actionKey })
    }

    @Test
    fun featureSchemaIsIndependentStableAndDoesNotContainLiteralValues() {
        assertEquals(ConstructionFeatures.names.size, ConstructionFeatures.names.toSet().size)
        // Keep historical goldens unchanged. A new schema must add a new versioned entry.
        // zh-CN: 历史 golden 不覆盖; 新 schema 必须增加对应版本的记录.
        val goldens = mapOf(1 to Triple(1, 87, "f2369debb29eaa54"))
        assertEquals(goldens.getValue(ConstructionFeatures.version),
            Triple(ConstructionFeatures.grammarVersion, ConstructionFeatures.count, ConstructionFeatures.namesHash))
        assertFalse(ConstructionFeatures.names.any { "future" in it || "survival" in it || "truth" in it })
        val root = FakeUiNode.root { child("target") { id = "demo:id/private_value_8675309"; text = "Secret 8675309" } }
        val result = ConstructionSearch(root, budget = budget.copy(maxActionScores = 200)).search(CodeGenRequest(listOf(root.find("target"))))
        assertTrue(result.expansions.flatMap { it.scoredActions }.all { "8675309" !in it.stateKey && "8675309" !in it.actionKey })
    }

    @Test
    fun beamCutsNearTiesAfterScoringAllSiblings() {
        val root = FakeUiNode.root {
            child("target") { id = "demo:id/action"; desc = "Ready"; text = "Continue" }
        }
        val policy = ConstructionPolicy { vector ->
            fun f(name: String) = ConstructionFeatures.value(vector, name)
            when {
                f("operation_finish") == 1f && f("outer_count") > 0f -> 10.0
                f("operation_outer") == 1f && f("outer_count") == 1f -> when {
                    f("condition_id") == 1f -> 1.0
                    f("condition_desc") == 1f -> 1.0000007
                    f("condition_text") == 1f -> 1.0000014
                    else -> -10.0
                }
                else -> -10.0
            }
        }
        // A family quota of one exposes the issue: the full winning band is {desc, text},
        // whose action-key tie-break picks desc. Incremental id/desc/text trimming picked text.
        val result = ConstructionSearch(root, budget = budget.copy(maxFrontier = 4, maxVerified = 1), policy = policy)
            .search(CodeGenRequest(listOf(root.find("target"))))
        assertEquals(1, result.candidates.size)
        assertEquals(listOf(Condition("desc", "Ready")), result.candidates.single().plan.selector.conditions)
        assertTrue(result.stats.peakFrontier <= 4)
        assertTrue(result.stats.peakPendingPrefixes > 4)
        val decisions = listOf(
            ConstructionDecision("state", "a", floatArrayOf(), 1.0),
            ConstructionDecision("state", "b", floatArrayOf(), 1.0000007),
            ConstructionDecision("state", "c", floatArrayOf(), 1.0000014),
        )
        for (order in listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))) {
            assertEquals(listOf("b", "c", "a"), ConstructionSearch.orderDecisions(order.map(decisions::get)).map { it.actionKey })
        }
    }

    @Test
    fun completePrefixKeysHaveOneIncomingConstructionAction() {
        val root = FakeUiNode.root {
            child("row", clickable = true) {
                id = "demo:id/row"
                child("heading") { text = "Heading" }
                child("target") { text = "Open"; className = "Button" }
            }
        }
        val request = CodeGenRequest(listOf(root.find("target")), action = Action.CLICK)
        val grammar = ConstructionGrammar(request, PlanVerifier(root), budget, CancellationSignal()) {}
        val pending = ArrayDeque<State>().apply { add(State()) }
        val parents = HashMap<String, Pair<String, String>>()
        while (pending.isNotEmpty()) {
            val state = pending.removeFirst()
            if (state.depth == 5) continue
            val actions = grammar.legal(state)
            assertEquals(actions.size, actions.map { it.key }.distinct().size)
            for (action in actions) {
                val next = grammar.apply(state, action)
                val incoming = state.key to action.key
                val previous = parents.putIfAbsent(next.key, incoming)
                if (previous == null) pending += next else assertEquals(previous, incoming)
                assertTrue("Bound this grammar invariant check", parents.size <= 20_000)
            }
        }
        assertTrue(parents.size > 100)
    }

    @Test
    fun reviewedVectorSnapshotsDistinguishMissingAnchorAndScopedTextAction() {
        val root = FakeUiNode.root {
            child("panel", clickable = true) {
                id = "demo:id/panel"; className = "Panel"
                child("target") { id = "demo:id/action"; text = "Step 12"; desc = "Hidden"; className = "Button" }
            }
            child("peer") { text = "Other"; className = "Button" }
        }
        val request = CodeGenRequest(listOf(root.find("target")), mode = CodeGenRequest.Mode.WAIT_TIMEOUT(2_000), action = Action.CLICK,
            attributes = setOf(CodeGenRequest.Attr.ID, CodeGenRequest.Attr.TEXT), style = CodeGenRequest.Style.FLOW)
        val grammar = ConstructionGrammar(request, PlanVerifier(root), budget, CancellationSignal()) {}
        var state = State()
        val family = Operation.ChooseFamily(Family.ANCHOR_SEARCH)
        val selected = grammar.apply(state, family)
        // Four captured nodes; the target is at depth two and its clickable parent receives the
        // action. No anchor has been selected, so all outer/inner match statistics are still zero.
        assertSnapshot(ConstructionFeatures.extract(grammar, state, family, selected), mapOf(
            "phase_family" to 1f, "operation_family" to 1f, "family_anchor_search" to 1f,
            "prefix_depth" to 1f, "target_depth" to 2f, "tree_size_log" to 1.609438f,
            "target_count_log" to 0.6931472f, "receiver_up_levels" to 1f,
            "request_action_click" to 1f, "request_attr_id" to 1f, "request_attr_text" to 1f,
            "request_receiver_auto" to 1f, "request_mode_timeout" to 1f, "request_style_flow" to 1f,
        ))
        state = selected
        state = grammar.apply(state, Operation.ChooseAnchor(grammar.positions.getValue(root.find("panel"))))
        state = grammar.apply(state, grammar.legal(state).filterIsInstance<Operation.AddCondition>().single { it.attr == "id" })
        state = grammar.apply(state, Operation.EndOuter)
        val text = grammar.legal(state).filterIsInstance<Operation.AddCondition>().single { it.attr == "text" }
        val next = grammar.apply(state, text)
        // The panel id is unique, "Step 12" finds exactly the selected child, and two of its seven
        // characters are digits. Hidden description is forbidden and contributes no feature.
        assertSnapshot(ConstructionFeatures.extract(grammar, state, text, next), mapOf(
            "phase_inner" to 1f, "operation_inner" to 1f, "family_anchor_search" to 1f,
            "prefix_depth" to 5f, "outer_count" to 1f, "inner_count" to 1f,
            "prefix_id" to 1f, "prefix_text" to 1f, "prefix_dynamic_text" to 1f,
            "outer_matches_log" to 0.6931472f, "outer_unique" to 1f, "outer_first_desired" to 1f, "outer_desired_present" to 1f,
            "inner_matches_log" to 0.6931472f, "inner_first_target" to 1f, "inner_target_coverage" to 1f,
            "path_remaining_distance" to 1f, "anchor_distance" to 1f, "anchor_depth" to 1f,
            "target_depth" to 2f, "tree_size_log" to 1.609438f, "target_count_log" to 0.6931472f,
            "receiver_up_levels" to 1f, "condition_text" to 1f, "condition_value_length_log" to 2.0794415f,
            "condition_digit_fraction" to 0.2857143f, "request_action_click" to 1f,
            "request_attr_id" to 1f, "request_attr_text" to 1f, "request_receiver_auto" to 1f,
            "request_mode_timeout" to 1f, "request_style_flow" to 1f,
        ))
    }

    @Test
    fun reviewedVectorSnapshotPreservesNegativeIndexAndAmbiguousOuterSelector() {
        val root = FakeUiNode.root {
            child("peer") { text = "Shared" }
            child("target") { text = "Shared" }
        }
        val request = CodeGenRequest(listOf(root.find("target")), attributes = setOf(CodeGenRequest.Attr.TEXT))
        val grammar = ConstructionGrammar(request, PlanVerifier(root), budget, CancellationSignal()) {}
        var state = grammar.apply(State(), Operation.ChooseFamily(Family.SELF_INDEX))
        state = grammar.apply(state, grammar.legal(state).filterIsInstance<Operation.AddCondition>().single { it.attr == "text" })
        state = grammar.apply(state, Operation.EndOuter)
        val action = grammar.legal(state).filterIsInstance<Operation.Index>().single { it.index == -1 }
        val next = grammar.apply(state, action)
        // The first text match is the peer, but findOnce(-1) selects the last of two matches.
        assertSnapshot(ConstructionFeatures.extract(grammar, state, action, next), mapOf(
            "phase_index" to 1f, "operation_index" to 1f, "family_self_index" to 1f,
            "prefix_depth" to 4f, "outer_count" to 1f, "prefix_text" to 1f,
            "outer_matches_log" to 1.0986123f, "outer_desired_present" to 1f,
            "target_depth" to 1f, "tree_size_log" to 1.3862944f, "target_count_log" to 0.6931472f,
            "index_absolute_log" to 0.6931472f, "index_negative" to 1f,
            "request_action_none" to 1f, "request_attr_text" to 1f, "request_receiver_auto" to 1f,
        ))
    }

    private fun assertSnapshot(actual: FloatArray, nonzero: Map<String, Float>) {
        assertEquals(87, actual.size)
        assertTrue(nonzero.keys.all { it in ConstructionFeatures.names })
        ConstructionFeatures.names.forEachIndexed { index, name ->
            assertEquals(name, nonzero[name] ?: 0f, actual[index], 0.000001f)
        }
    }

    @Test
    fun impossibleRequestKeepsStructuredRejectionWithoutFabricatedTerminal() {
        val root = FakeUiNode.root { child("label") { text = "No click action" } }
        val result = ConstructionSearch(root, budget = budget).search(CodeGenRequest(listOf(root.find("label")), action = Action.CLICK))
        assertEquals(ConstructionStopReason.INVALID_REQUEST, result.stopReason)
        assertEquals(RejectionReason.UNSUPPORTED_ACTION, result.closestRejection!!.reason)
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.completions.isEmpty())
    }
}
