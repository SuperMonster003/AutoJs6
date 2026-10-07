package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Posthoc audit only: unchanged frozen policy, grammar, features and counters. A zero clock removes
 * wall-time noise from candidate-set reproduction; these repetitions are not benchmark results.
 * The output excludes selector literals, feature vectors and raw plan/state/action keys.
 * zh-CN: 仅事后诊断, 不调参或回流训练. 零时钟用于复现计数器结果, 不作为性能测量. */
class ConstructionSecondDiagnosticsTask {
    private data class ReplayStep(val state: State, val operation: Operation, val score: Double)
    private data class Replay(val steps: List<ReplayStep>, val complete: LocatePlan?, val failure: String?)
    private val gson = GsonBuilder().serializeNulls().create()
    private val budget = ConstructionBudget(maxMillis = 800)

    @Test fun diagnoseFrozenSecondExperimentCoverageLoss() {
        val directory = System.getenv("CODEGEN_CONSTRUCTION_R2_DIAGNOSTICS")
        assumeTrue("Set CODEGEN_CONSTRUCTION_R2_DIAGNOSTICS to the frozen holdout directory", directory != null)
        val input = File(requireNotNull(directory)).canonicalFile
        val metricsFile = File(input, "metrics.json")
        val resultsFile = File(input, "results.jsonl")
        val metrics = JsonParser.parseString(metricsFile.readText()).asJsonObject
        val metricsHash = hash(metricsFile.readBytes())
        assertEquals("holdout", metrics["phase"].asString)
        assertEquals(metrics["resultsSha256"].asString, hash(resultsFile.readBytes()))
        val policyFile = File(requireNotNull(System.getenv("CODEGEN_CONSTRUCTION_POLICY_DIR")), "policy.json")
        val policyHash = "ecb04d2f7086b6c1065ff949b821be3d5e37a06ca2e5c5deca63b478acaffa62"
        assertEquals(policyHash, metrics["policySha256"].asString)
        assertEquals(policyHash, hash(policyFile.readBytes()))
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        metrics.getAsJsonObject("sourceSha256").entrySet().forEach { (path, digest) ->
            assertEquals("Frozen source changed: $path", digest.asString, hash(File(root, path).readText().replace("\r\n", "\n").toByteArray(Charsets.UTF_8)))
        }
        val saved = resultsFile.useLines { lines -> lines.map { JsonParser.parseString(it).asJsonObject }
            .filter { it["cohort"].asString == "synthetic-holdout" }.toList() }.groupBy { it["groupId"].asString }
        val losses = saved.filterValues { rows ->
            rows.single { it["method"].asString == "fair_model" }["candidates"].asInt == 0 &&
                rows.single { it["method"].asString == "fair_heuristic" }["candidates"].asInt > 0
        }
        assertEquals(9, losses.size)
        val groups = ConstructionGeneralizationFixtures.groups().filter { it.groupId in losses }
        assertEquals(losses.keys, groups.map { it.groupId }.toSet())
        val model = ConstructionPolicyModel.read(policyFile)
        val summaries = ArrayList<JsonObject>()
        var referenceCount = 0
        for (group in groups) {
            assertEquals(CodeGenRequest.Scope.COLLECTION, group.request.scope)
            val capture = group.states[group.captureState]
            val request = group.request.forState(capture)
            val policies = listOf("fair_heuristic" to ConstructionHeuristicPolicy, "fair_model" to model)
            val runs = policies.associate { (name, policy) ->
                val result = ConstructionSearch(capture.root, budget = budget, policy = policy, nanoTime = { 0L },
                    frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
                val recorded = losses.getValue(group.groupId).single { it["method"].asString == name }
                assertEquals("Frozen and diagnostic stops must match", recorded["stopReason"].asString, result.stopReason.name)
                assertEquals(ConstructionStopReason.EXHAUSTED, result.stopReason)
                val savedKeys = recorded.getAsJsonArray("candidatesDetail").map { it.asJsonObject["canonicalKey"].asString }.toSet()
                assertEquals("Frozen candidate set changed: ${group.groupId} $name", savedKeys,
                    result.candidates.map { ConstructionSearch.canonicalKey(it.plan) }.toSet())
                name to result
            }
            val references = runs.getValue("fair_heuristic").candidates
            referenceCount += references.size
            for ((name, policy) in policies) {
                val result = runs.getValue(name)
                val grammar = ConstructionGrammar(request, PlanVerifier(capture.root), budget, CancellationSignal()) {}
                val expansions = result.expansions.associateBy { it.stateKey }
                for (candidate in references) {
                    val replay = replay(grammar, candidate.plan, policy)
                    val missing = replay.steps.indexOfFirst { it.state.key !in expansions }
                    val unscored = replay.steps.indexOfFirst { step ->
                        expansions[step.state.key]?.scoredActions?.none { it.actionKey == step.operation.key } == true
                    }
                    val retained = result.candidates.any { ConstructionSearch.canonicalKey(it.plan) == ConstructionSearch.canonicalKey(candidate.plan) }
                    val incoming = if (missing >= 0) replay.steps.getOrNull(missing - 1) else if (!retained) replay.steps.lastOrNull() else null
                    val valid = replay.complete?.let { grammar.verifier.verify(request, it) is Verified } == true
                    val samePlan = replay.complete?.let { ConstructionSearch.canonicalKey(it) == ConstructionSearch.canonicalKey(candidate.plan) } == true
                    // CONTAINER_ITEMS has ANCHOR(-1), plus OUTER/INNER/DONE for each legal anchor.
                    // With fewer buckets than family slots, every bucket gets a slot; loss then
                    // requires competition among prefixes sharing the same anchor and phase.
                    val familySlots = budget.maxFrontier / grammar.legal(State()).size
                    val bucketBound = if (candidate.plan.family == Family.CONTAINER_ITEMS)
                        1 + 3 * grammar.legal(grammar.apply(State(), Operation.ChooseFamily(candidate.plan.family))).size
                    else if (candidate.plan.family == Family.SELF) 2 else Int.MAX_VALUE
                    val classification = when {
                        replay.failure != null -> "GRAMMAR_UNREPRESENTABLE"
                        !valid || !samePlan -> "REPLAY_MISMATCH"
                        retained -> "RETAINED_POSSIBLY_VIA_SIMPLIFICATION"
                        unscored >= 0 -> "UNEXPECTED_UNSCORED_ACTION"
                        bucketBound <= familySlots -> "WITHIN_ANCHOR_PHASE_PRUNED"
                        else -> "BEAM_PRUNED"
                    }
                    val row = JsonObject().apply {
                        addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily)
                        addProperty("policy", name); addProperty("family", candidate.plan.family.name)
                        addProperty("classification", classification); addProperty("grammarReplayLegal", replay.failure == null)
                        addProperty("replayedPlanVerified", valid); addProperty("replayedCanonicalPlanEqualsReference", samePlan)
                        addProperty("familySlots", familySlots); addProperty("familyBucketUpperBound", bucketBound)
                        addProperty("prunedPrefixes", result.stats.prunedPrefixes)
                        addProperty("firstMissingPhase", replay.steps.getOrNull(missing)?.state?.phase?.name ?: if (!retained) "DONE" else null)
                        addProperty("firstUnscoredPhase", replay.steps.getOrNull(unscored)?.state?.phase?.name)
                        addProperty("incomingOperation", incoming?.operation?.let(::operationLabel)); addProperty("incomingScore", incoming?.score)
                        addProperty("grammarFailure", replay.failure)
                        add("replay", JsonArray().apply { replay.steps.forEach { step -> add(JsonObject().apply {
                            val expansion = expansions[step.state.key]
                            val actual = expansion?.scoredActions?.firstOrNull { it.actionKey == step.operation.key }
                            if (actual != null) assertEquals("Frozen score changed", actual.score, step.score, 0.0)
                            addProperty("phase", step.state.phase.name); addProperty("operation", operationLabel(step.operation))
                            addProperty("score", step.score); addProperty("stateExpanded", expansion != null); addProperty("actionScored", actual != null)
                        }) } })
                    }
                    summaries += row
                }
            }
        }
        assertEquals(9, referenceCount)
        val output = JsonObject().apply {
            addProperty("format", 1); addProperty("role", "posthoc-second-construction-coverage-diagnostic")
            addProperty("holdoutMetricsSha256", metricsHash); addProperty("holdoutResultsSha256", metrics["resultsSha256"].asString)
            addProperty("protocolSha256", metrics["protocolSha256"].asString); addProperty("policySha256", policyHash)
            addProperty("timing", "Zero clock; unchanged counter budgets; diagnostic repetition, not a benchmark")
            addProperty("groups", groups.size); addProperty("referencePlans", referenceCount); addProperty("replays", summaries.size)
            addProperty("candidateSetsExactlyReproduced", groups.size * 2)
            add("classifications", gson.toJsonTree(summaries.groupingBy { it["classification"].asString }.eachCount()))
            val modelRows = summaries.filter { it["policy"].asString == "fair_model" }
            for (field in listOf("firstMissingPhase", "incomingOperation")) add(field + "Counts", gson.toJsonTree(
                modelRows.groupingBy { if (it[field].isJsonNull) "NONE" else it[field].asString }.eachCount()))
            add("details", gson.toJsonTree(summaries))
        }
        assertEquals(metricsHash, hash(metricsFile.readBytes()))
        assertEquals(metrics["resultsSha256"].asString, hash(resultsFile.readBytes()))
        val destination = System.getenv("CODEGEN_CONSTRUCTION_R2_DIAGNOSTICS_OUTPUT")?.let { File(it).canonicalFile }
        if (destination != null) {
            require(!destination.exists() && destination.mkdirs()) { "Use a fresh diagnostic output directory" }
            File(destination, "diagnostics.json").writeText(gson.toJson(output) + "\n", Charsets.UTF_8)
        }
        println("Construction second coverage diagnosis: " + gson.toJson(output))
    }

    private fun replay(grammar: ConstructionGrammar<SnapshotNode>, plan: LocatePlan, policy: ConstructionPolicy): Replay {
        val steps = ArrayList<ReplayStep>()
        var state = State()
        fun perform(operation: Operation): Boolean {
            if (operation !in grammar.legal(state)) return false
            val next = grammar.apply(state, operation)
            steps += ReplayStep(state, operation, policy.score(ConstructionFeatures.extract(grammar, state, operation, next)))
            state = next
            return true
        }
        fun addConditions(conditions: List<Condition>, inner: Boolean): Boolean {
            val source = if (inner) grammar.targetConditions else grammar.outerSource(state)
            val positions = conditions.map { source.indexOf(it) }
            if (positions.any { it < 0 }) return false
            return positions.sorted().all { perform(Operation.AddCondition(it, inner, source[it].attr)) }
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
            Family.CONTAINER_ITEMS, Family.ANCHOR_SEARCH -> {
                if (!perform(Operation.EndOuter)) return failed("END_OUTER")
                if (!addConditions((plan.steps.single() as PlanStep.Search).selector.conditions, true)) return failed("INNER_CONDITION_NOT_LEGAL")
            }
            else -> return failed("NOT_A_COLLECTION_PLAN")
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
        Operation.Finish -> "FINISH"
        else -> operation.javaClass.simpleName
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
