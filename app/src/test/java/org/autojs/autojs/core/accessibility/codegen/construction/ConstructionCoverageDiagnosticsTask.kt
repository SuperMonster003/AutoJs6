package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Operation
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.Phase
import org.autojs.autojs.core.accessibility.codegen.construction.ConstructionGrammar.State
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Post-evaluation diagnosis only. Does not modify the frozen search, policies, fixtures, feature
 * schema or evaluation; it never supplies training rows or selects parameters from this holdout.
 * Output contains operation/attribute names and scores, never selector literals or raw state keys.
 * zh-CN: 仅诊断已冻结实验的退化, 不改变实现或选择参数, 不回流训练; 输出不含属性原文. */
class ConstructionCoverageDiagnosticsTask {
    private data class ReplayStep(
        val state: State,
        val operation: Operation,
        val next: State,
        val score: Double,
    )
    private data class Replay(val steps: List<ReplayStep>, val complete: LocatePlan?, val failure: String?)
    private val gson = GsonBuilder().serializeNulls().create()
    private val budget = ConstructionBudget(maxMillis = 800)

    @Test
    fun diagnosePreviouslyObservedCollectionCoverageLoss() {
        val directory = System.getenv("CODEGEN_CONSTRUCTION_DIAGNOSTICS_DIR")
        assumeTrue("Set CODEGEN_CONSTRUCTION_DIAGNOSTICS_DIR to the frozen policy directory", directory != null)
        val model = ConstructionPolicyModel.read(File(requireNotNull(directory), "policy.json"))
        val groups = ConstructionBottleneckFixtures.groups().filter {
            it.screenFamily.endsWith("dynamic-attributes") && it.request.scope == CodeGenRequest.Scope.COLLECTION
        }
        assertEquals(3, groups.size)
        // Warm unchanged engines before checking their deterministic exhausted searches. These
        // diagnostic repetitions are not additional benchmark observations or revised metrics.
        val warmCapture = groups.first().states.first()
        val warmRequest = groups.first().request.forState(warmCapture)
        repeat(2) {
            ConstructionSearch(warmCapture.root, budget = budget).search(warmRequest)
            ConstructionSearch(warmCapture.root, budget = budget, policy = model).search(warmRequest)
        }
        val summaries = JsonArray()
        for (group in groups) {
            val capture = group.states[group.captureState]
            val request = group.request.forState(capture)
            val baseline = CandidateSearch(capture.root).search(request)
            assertTrue("The frozen baseline must still find the previously reported candidates", baseline.candidates.isNotEmpty())
            for ((name, policy) in listOf("heuristic" to ConstructionHeuristicPolicy, "model" to model)) {
                val result = ConstructionSearch(capture.root, budget = budget, policy = policy).search(request)
                val grammar = ConstructionGrammar(request, PlanVerifier(capture.root), budget, CancellationSignal()) {}
                val expansions = result.expansions.associateBy { it.stateKey }
                for (candidate in baseline.candidates) {
                    val replay = replay(grammar, candidate.plan, policy)
                    val firstMissing = replay.steps.indexOfFirst { it.state.key !in expansions }
                    val firstUnscored = replay.steps.indexOfFirst { step ->
                        expansions[step.state.key]?.scoredActions?.none { it.actionKey == step.operation.key } == true
                    }
                    val completed = replay.complete?.let { wanted -> result.completions.any { it.plan.planKey == wanted.planKey } } == true
                    val missingStep = replay.steps.getOrNull(firstMissing)
                    val incoming = if (firstMissing >= 0) replay.steps.getOrNull(firstMissing - 1)
                        else if (!completed) replay.steps.lastOrNull() else null
                    val currentVerified = replay.complete?.let { grammar.verifier.verify(request, it) is Verified } == true
                    val classification = when {
                        replay.failure != null -> "GRAMMAR_UNREPRESENTABLE"
                        !currentVerified -> "REPLAY_VERIFICATION_FAILED"
                        completed -> "COMPLETED"
                        result.stopReason != ConstructionStopReason.EXHAUSTED -> "SEARCH_STOPPED_${result.stopReason.name}"
                        firstUnscored >= 0 -> "UNEXPECTED_UNSCORED_ACTION"
                        firstMissing >= 0 || replay.complete != null -> "BEAM_PRUNED"
                        else -> "UNCLASSIFIED"
                    }
                    val row = JsonObject().apply {
                        addProperty("groupId", group.groupId)
                        addProperty("policy", name)
                        addProperty("family", candidate.plan.family.name)
                        add("outerAttributes", strings(candidate.plan.selector.conditions.map { it.attr }))
                        add("innerAttributes", strings(candidate.plan.steps.filterIsInstance<PlanStep.Search>().flatMap { it.selector.conditions }.map { it.attr }))
                        addProperty("grammarReplayLegal", replay.failure == null)
                        addProperty("replayedPlanVerified", currentVerified)
                        addProperty("replayedCanonicalPlanEqualsBaseline", replay.complete?.let {
                            ConstructionSearch.canonicalKey(it) == ConstructionSearch.canonicalKey(candidate.plan)
                        } == true)
                        addProperty("classification", classification)
                        addProperty("searchStopReason", result.stopReason.name)
                        addProperty("searchCandidates", result.candidates.size)
                        addProperty("prunedPrefixes", result.stats.prunedPrefixes)
                        addProperty("firstMissingPhase", missingStep?.state?.phase?.name ?: if (!completed) "DONE" else null)
                        addProperty("incomingOperation", incoming?.operation?.let(::operationLabel))
                        addProperty("incomingScore", incoming?.score)
                        addProperty("grammarFailure", replay.failure)
                        add("replay", JsonArray().apply {
                            for (step in replay.steps) add(JsonObject().apply {
                                val expansion = expansions[step.state.key]
                                val actual = expansion?.scoredActions?.firstOrNull { it.actionKey == step.operation.key }
                                if (actual != null) assertEquals("Frozen policy score must match its audited decision", actual.score, step.score, 0.0)
                                addProperty("phase", step.state.phase.name)
                                addProperty("operation", operationLabel(step.operation))
                                addProperty("score", step.score)
                                addProperty("stateExpanded", expansion != null)
                                addProperty("actionScored", actual != null)
                            })
                        })
                    }
                    summaries.add(row)
                    println("Construction coverage diagnostic: " + gson.toJson(row))
                }
            }
        }
        val counts = summaries.map { it.asJsonObject }.groupingBy { it["classification"].asString }.eachCount()
        println("Construction coverage classifications: " + gson.toJson(counts))
    }

    private fun replay(grammar: ConstructionGrammar<SnapshotNode>, plan: LocatePlan, policy: ConstructionPolicy): Replay {
        val steps = ArrayList<ReplayStep>()
        var state = State()
        fun perform(operation: Operation): Boolean {
            if (operation !in grammar.legal(state)) return false
            val next = grammar.apply(state, operation)
            val score = policy.score(ConstructionFeatures.extract(grammar, state, operation, next))
            steps += ReplayStep(state, operation, next, score)
            state = next
            return true
        }
        fun addConditions(conditions: List<Condition>, inner: Boolean): Boolean {
            val source = if (inner) grammar.targetConditions else grammar.outerSource(state)
            val positions = conditions.map { source.indexOf(it) }
            if (positions.any { it < 0 }) return false
            for (position in positions.sorted()) {
                if (!perform(Operation.AddCondition(position, inner, source[position].attr))) return false
            }
            return true
        }
        fun failed(reason: String) = Replay(steps, null, reason)
        if (!perform(Operation.ChooseFamily(plan.family))) return failed("FAMILY")
        if (plan.family !in setOf(Family.SELF, Family.SELF_INDEX)) {
            val anchor = grammar.matches(plan.selector).singleOrNull() ?: return failed("ANCHOR_NOT_UNIQUE")
            if (!perform(Operation.ChooseAnchor(grammar.positions.getValue(anchor)))) return failed("ANCHOR_NOT_LEGAL")
        }
        if (!addConditions(plan.selector.conditions, false)) return failed("OUTER_CONDITION_NOT_LEGAL")
        when (plan.family) {
            Family.SELF -> Unit
            Family.SELF_INDEX -> {
                if (!perform(Operation.EndOuter)) return failed("END_OUTER")
                if (!perform(Operation.Index((plan.steps.single() as PlanStep.Index).index))) return failed("INDEX_NOT_LEGAL")
            }
            Family.ANCHOR_PATH -> {
                if (!perform(Operation.EndOuter)) return failed("END_OUTER")
                for (step in plan.steps) {
                    val operation = when (step) {
                        is PlanStep.Up -> Operation.Up(step.levels)
                        is PlanStep.Child -> Operation.Child(step.index)
                        else -> return failed("PATH_SHAPE")
                    }
                    if (!perform(operation)) return failed("PATH_STEP_NOT_LEGAL")
                }
            }
            Family.SELF_RELATIVE -> return failed("FAMILY_OUTSIDE_GRAMMAR")
            Family.ANCHOR_SEARCH, Family.CONTAINER_ITEMS -> {
                if (!perform(Operation.EndOuter)) return failed("END_OUTER")
                if (!addConditions((plan.steps.single() as PlanStep.Search).selector.conditions, true)) return failed("INNER_CONDITION_NOT_LEGAL")
            }
        }
        if (!perform(Operation.Finish)) return failed("FINISH_NOT_LEGAL")
        check(state.phase == Phase.DONE)
        return Replay(steps, grammar.complete(state), null)
    }

    private fun operationLabel(operation: Operation): String = when (operation) {
        is Operation.ChooseFamily -> "CHOOSE_FAMILY"
        is Operation.ChooseAnchor -> "CHOOSE_ANCHOR"
        is Operation.AddCondition -> (if (operation.inner) "INNER:" else "OUTER:") + operation.attr
        Operation.EndOuter -> "END_OUTER"
        is Operation.Index -> "INDEX"
        is Operation.Up -> "UP"
        is Operation.Child -> "CHILD"
        Operation.Finish -> "FINISH"
    }
    private fun strings(values: List<String>) = JsonArray().apply { values.forEach(::add) }
}
