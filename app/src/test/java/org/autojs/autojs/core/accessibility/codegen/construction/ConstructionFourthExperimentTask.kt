package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackage
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.rank.ModelRanker
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.render.ClassicRenderer
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.search.SearchBudget
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Explicit offline fourth experiment: the merged pipeline of the roadmap (rule search candidates
 * united with a frozen construction policy's candidates, one verifier, one final ranker) against
 * the complete rule pipeline and the original search with either final ranker. The final ranker
 * under test was refitted on the merged candidate distribution. The reserved holdout of the third
 * protocol, never evaluated there, is reused unchanged; every earlier inspected cohort is reported
 * as regression only. Freeze identities before development; admit using only development results.
 * zh-CN: 第四轮离线实验: 规则搜索与冻结构造策略的候选合并后由重训的终局排序器排序, 对照完整规则流水线与
 * 原搜索 + 两种排序器. 复用第三轮未开启的留出集, 已查看的任务只作回归.
 */
class ConstructionFourthExperimentTask {
    @Test fun runOfflineFourthConstructionExperiment() {
        val mode = System.getenv("CODEGEN_CONSTRUCTION_R4_MODE")
        assumeTrue("Set CODEGEN_CONSTRUCTION_R4_MODE=freeze, develop or holdout", mode != null)
        val selectedMode = requireNotNull(mode)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        val inputs = ConstructionFourthExperiment.Inputs(
            dataset = directory("CODEGEN_CONSTRUCTION_DATASET"), policy = directory("CODEGEN_CONSTRUCTION_R3_FIT_BEST"),
            merged = directory("CODEGEN_CONSTRUCTION_MERGED"), ranker = directory("CODEGEN_CONSTRUCTION_RANKER"),
            thirdProtocol = directory("CODEGEN_CONSTRUCTION_R3_PROTOCOL"), protocol = directory("CODEGEN_CONSTRUCTION_R4_PROTOCOL"),
        )
        when (selectedMode) {
            "freeze" -> ConstructionFourthExperiment.freeze(inputs)
            "develop", "holdout" -> ConstructionFourthExperiment.evaluate(inputs, directory("CODEGEN_CONSTRUCTION_OUTPUT"), selectedMode,
                if (selectedMode == "holdout") directory("CODEGEN_CONSTRUCTION_R4_DEVELOPMENT") else null,
                System.getenv("CODEGEN_CONSTRUCTION_R3_DEVELOPMENT")?.let { File(it).canonicalFile })
            else -> error("Unknown fourth construction experiment mode")
        }
    }
}

internal object ConstructionFourthExperiment {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private const val SEED = 20261008L
    private const val DEADLINE_MS = 1000L
    private const val SEARCH_MS = 800L
    private const val POLICY_SHA256 = "48f1014a0f850027a18ac46f567262dfe6be63f75b4035947cd6603c285bc1dd"
    private const val TINY_SHA256 = "9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17"
    private val constructionBudget = ConstructionBudget(maxMillis = SEARCH_MS)
    private val sharedBaselineBudget = SearchBudget(maxAttempts = 1_000, maxVerified = 100)
    private val sharedConstructionBudget = ConstructionBudget(maxAttempts = 1_000, maxVerified = 100, maxMillis = SEARCH_MS)
    private val methods = listOf("rule_pipeline", "baseline_tiny", "baseline_ranker", "construction_ranker", "merged_rule", "merged_tiny", "merged_ranker", "merged_ranker_shared")
    private const val CHOSEN = "merged_ranker"
    private val noRegressionAgainst = listOf("rule_pipeline", "baseline_tiny", "baseline_ranker")
    private const val GAIN_REFERENCE = "baseline_ranker"
    private val failedStops = setOf("FAILED", "HARD_DEADLINE", "DEADLINE", "POLICY_FAILURE")
    private val decisionMetrics = listOf("coverage", "bestSurvival", "top1Survival", "top3BestSurvival")

    data class Inputs(val dataset: File, val policy: File, val merged: File, val ranker: File, val thirdProtocol: File, val protocol: File)
    private data class Task(val group: TaskGroup, val split: String, val cohort: String)
    private data class Candidate(val value: Verified<SnapshotNode>, val survival: Double)
    private data class Searches(
        val baseline: List<Verified<SnapshotNode>>, val baselineAttempts: Int, val baselineExhausted: Boolean, val baselineMillis: Double,
        val construction: ConstructionResult<SnapshotNode>?, val constructionMillis: Double,
        val sharedBaseline: List<Verified<SnapshotNode>>, val sharedBaselineAttempts: Int, val sharedBaselineMillis: Double,
        val sharedConstruction: ConstructionResult<SnapshotNode>?, val sharedConstructionMillis: Double,
        val failure: String?, val cancelled: Boolean,
    )
    private data class Ranking(val name: String, val candidates: List<Verified<SnapshotNode>>, val order: List<String>, val elapsedMillis: Double,
                               val searchMillis: Double, val attemptedCount: Int, val stopReason: String, val construction: ConstructionResult<SnapshotNode>?, val error: String?)

    fun freeze(inputs: Inputs) {
        val binding = binding(inputs)
        val third = readJson(File(inputs.thirdProtocol, "protocol.json"))
        val thirdSelection = readJson(File(inputs.thirdProtocol, "selection.json"))
        require(third["role"].asString == "construction-third-experiment")
        require(!thirdSelection["passed"].asBoolean) { "The third holdout was opened; reserve new tasks instead" }
        require(thirdSelection["protocolSha256"].asString == hash(File(inputs.thirdProtocol, "protocol.json")))
        val protocolInputs = JsonObject()
        fun record(file: File) {
            val path = relativeInside(inputs.dataset, file)
            val digest = hash(file)
            if (protocolInputs.has(path)) require(protocolInputs[path].asString == digest) else protocolInputs.addProperty(path, digest)
        }
        val assignment = readJson(File(inputs.dataset, "split-map.json")).getAsJsonObject("assignment").entrySet().associate { it.key to it.value.asString }
        val splitGroups = readJson(File(inputs.dataset, "split-map.json")).getAsJsonArray("groups").map { it.asJsonObject }.associateBy { it["groupId"].asString }
        val snapshots = snapshotCache()
        fun captured(groupId: String, split: String, cohort: String): JsonObject {
            val taskFile = File(splitGroups.getValue(groupId)["task"].asString).canonicalFile
            record(taskFile)
            val group = TaskGroup.load(taskFile) { file -> record(file); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
            group.requireLabelable()
            require(group.groupId == groupId && assignment.getValue(groupId) == (if (split == "regression") "test" else split))
            return metadata(group, split, cohort).apply { addProperty("task", relativeInside(inputs.dataset, taskFile)) }
        }
        // Development: the third export's 336 training tasks plus every validation-family task.
        val thirdDevelopment = third.getAsJsonArray("developmentGroups").map { it.asJsonObject }
        val trainIds = thirdDevelopment.filter { it["split"].asString == "train" }.map { it["groupId"].asString }
        require(trainIds.size == 336)
        val validIds = assignment.filterValues { it == "valid" }.keys.sorted()
        require(validIds.size == 336)
        val developmentGroups = JsonArray().apply {
            trainIds.sorted().forEach { add(captured(it, "train", "captured-development")) }
            validIds.forEach { add(captured(it, "valid", "captured-development")) }
        }
        val reservedCaptured = third.getAsJsonArray("capturedHoldoutGroups").map { it.asJsonObject }
        val reservedSynthetic = third.getAsJsonArray("syntheticHoldoutGroups").map { it.asJsonObject }
        require(reservedCaptured.size == 28 && reservedSynthetic.size == 24)
        val capturedHoldout = JsonArray().apply { reservedCaptured.forEach { item ->
            val frozen = captured(item["groupId"].asString, "test", "captured-construction-unseen")
            require(frozen == item) { "Reserved captured holdout changed: ${item["groupId"].asString}" }
            add(frozen)
        } }
        val thirdFixtures = ConstructionThirdSyntheticFixtures.groups().associateBy { it.groupId }
        val syntheticHoldout = JsonArray().apply { reservedSynthetic.forEach { item ->
            val group = thirdFixtures.getValue(item["groupId"].asString)
            group.requireLabelable()
            require(metadata(group, "test", "synthetic-holdout") == item) { "Reserved synthetic holdout changed" }
            add(item)
        } }
        val regression = JsonArray().apply { third.getAsJsonArray("regressionGroups").map { it.asJsonObject }.forEach { item ->
            if (item.has("task")) {
                val frozen = captured(item["groupId"].asString, item["split"].asString, item["cohort"].asString)
                require(frozen == item) { "Regression group changed: ${item["groupId"].asString}" }
            }
            add(item)
        } }
        require(regression.size() == 76)
        val allIds = listOf(developmentGroups, capturedHoldout, syntheticHoldout, regression).flatMap { groups -> groups.map { it.asJsonObject["groupId"].asString } }
        require(allIds.distinct().size == allIds.size)
        val protocol = JsonObject().apply {
            addProperty("format", 1); addProperty("role", "construction-fourth-experiment")
            addProperty("seed", SEED); addProperty("datasetId", binding["mergedDatasetId"].asString)
            addProperty("pipeline", "Rule search candidates united with frozen best-continuation construction candidates, one verifier, canonical deduplication, one final ranker")
            addProperty("constructionPolicySha256", POLICY_SHA256); addProperty("tinyRankerSha256", TINY_SHA256)
            addProperty("refittedRankerSha256", binding["rankerSha256"].asString)
            add("binding", binding); add("sourceSha256", sourceIdentity()); add("inputs", protocolInputs)
            add("methods", gson.toJsonTree(methods))
            add("constructionBudget", gson.toJsonTree(constructionBudget)); add("baselineBudget", gson.toJsonTree(SearchBudget()))
            add("sharedBaselineBudget", gson.toJsonTree(sharedBaselineBudget)); add("sharedConstructionBudget", gson.toJsonTree(sharedConstructionBudget))
            addProperty("hardDeadlineMillis", DEADLINE_MS)
            addProperty("timing", "Per task: rule search, then construction within the remaining 800 ms allocation, then every ranking; merged methods report the sum, baseline methods their own search plus ranking; a 1000 ms whole-task cancellation applies")
            addProperty("sharedBudgetNote", "merged_ranker_shared halves both searches (1,000 attempts / 100 retained each) so the pair stays within the D19 total of 2,000 verifications; it is informational, not the admission candidate")
            addProperty("thirdProtocolSha256", hash(File(inputs.thirdProtocol, "protocol.json")))
            addProperty("thirdSelectionSha256", hash(File(inputs.thirdProtocol, "selection.json")))
            addProperty("capturedHoldoutLimitation", "Reserved by the third protocol and never evaluated; same held-out list family; not new applications")
            addProperty("syntheticHoldoutLimitation", "Reserved authored scenarios; neither captured real applications nor a population sample")
            addProperty("regressionLimitation", "All regression cohorts were inspected by earlier experiments; regression only, never selection evidence")
            add("developmentGroups", developmentGroups); add("capturedHoldoutGroups", capturedHoldout)
            add("syntheticHoldoutGroups", syntheticHoldout); add("regressionGroups", regression)
            add("selectionCriteria", JsonObject().apply {
                addProperty("chosenMethod", CHOSEN); add("noRegressionAgainst", gson.toJsonTree(noRegressionAgainst))
                addProperty("strictGainAgainst", GAIN_REFERENCE); add("metrics", gson.toJsonTree(decisionMetrics))
                addProperty("noRegressionMeasure", "top1Survival"); add("strictGainMeasures", gson.toJsonTree(listOf("top1Survival", "top3BestSurvival")))
                addProperty("selectionSplit", "valid"); addProperty("allMethodsNoFailureOrDeadline", true)
                add("failedStops", gson.toJsonTree(failedStops.sorted()))
                addProperty("scope", "Development gate to authorize one holdout assessment of the merged pipeline; not a production quality gate")
            })
        }
        requireFreshOutput(inputs.protocol)
        writeJson(File(inputs.protocol, "protocol.json"), protocol)
        println("Frozen fourth construction protocol: ${hash(File(inputs.protocol, "protocol.json"))}; development=${developmentGroups.size()}, captured holdout=28, synthetic holdout=24, regression=76")
    }

    fun evaluate(inputs: Inputs, output: File, mode: String, developmentDirectory: File?, thirdDevelopment: File?) {
        require(mode == "develop" || mode == "holdout")
        val protocolFile = File(inputs.protocol, "protocol.json")
        val protocolSha256 = hash(protocolFile)
        val protocol = readJson(protocolFile)
        validateProtocol(inputs, protocol)
        if (mode == "holdout") validateSelection(protocolFile, protocol, requireNotNull(developmentDirectory))
        val selectionSha256 = if (mode == "holdout") hash(File(inputs.protocol, "selection.json")) else null
        val policy = ConstructionPolicyModel.read(File(inputs.policy, "policy.json"))
        require(policy.scoreSemantics == ConstructionPolicyModel.BEST_SCORE_SEMANTICS)
        val refitted = ExperimentRankerModel.read(File(inputs.ranker, "ranker.json")).model
        val tiny = (ModelPackageReader().read(tinyBytes().inputStream()) as ModelPackage).model
        val snapshots = snapshotCache()
        val splitGroups = readJson(File(inputs.dataset, "split-map.json")).getAsJsonArray("groups").map { it.asJsonObject }.associateBy { it["groupId"].asString }
        fun loadCaptured(item: JsonObject): Task {
            val taskFile = safeFile(inputs.dataset, item["task"].asString)
            require(hash(taskFile) == protocol.getAsJsonObject("inputs")[relativeInside(inputs.dataset, taskFile)].asString)
            val group = TaskGroup.load(taskFile) { file ->
                require(hash(file) == protocol.getAsJsonObject("inputs")[relativeInside(inputs.dataset, file)].asString)
                snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) }
            }
            require(metadata(group, item["split"].asString, item["cohort"].asString) == item.deepCopy().apply { remove("task") })
            return Task(group, item["split"].asString, item["cohort"].asString)
        }
        val developmentMetadata = protocol.getAsJsonArray("developmentGroups").map { it.asJsonObject }
        val warm = developmentMetadata.filter { it["split"].asString == "train" }.take(3).map(::loadCaptured)
        val tasks = if (mode == "develop") developmentMetadata.map(::loadCaptured) else {
            fun synthetic(groups: List<TaskGroup>, frozenItems: List<JsonObject>): List<Task> {
                val frozen = frozenItems.associateBy { it["groupId"].asString }
                require(groups.all { it.groupId in frozen })
                return groups.map { group ->
                    val item = frozen.getValue(group.groupId)
                    require(metadata(group, item["split"].asString, item["cohort"].asString) == item) { "Frozen synthetic input changed: ${group.groupId}" }
                    Task(group, item["split"].asString, item["cohort"].asString)
                }
            }
            val regression = protocol.getAsJsonArray("regressionGroups").map { it.asJsonObject }
            protocol.getAsJsonArray("capturedHoldoutGroups").map { loadCaptured(it.asJsonObject) } +
                synthetic(ConstructionThirdSyntheticFixtures.groups(), protocol.getAsJsonArray("syntheticHoldoutGroups").map { it.asJsonObject }) +
                synthetic(ConstructionBottleneckFixtures.groups() + ConstructionGeneralizationFixtures.groups(), regression.filter { !it.has("task") }) +
                regression.filter { it.has("task") }.map(::loadCaptured)
        }
        require(tasks.map { it.group.groupId }.distinct().size == tasks.size)
        // Optional cross-check: the construction policy's candidate sets recorded by the third experiment.
        val thirdSets = thirdDevelopment?.let { directory ->
            File(directory, "results.jsonl").useLines(Charsets.UTF_8) { lines ->
                lines.map { JsonParser.parseString(it).asJsonObject }.filter { it["method"].asString == "fair_model_best" }
                    .associate { row -> row["groupId"].asString to Pair(
                        row.getAsJsonArray("candidatesDetail").map { it.asJsonObject["canonicalKey"].asString }.toSet(),
                        row.getAsJsonObject("constructionCounts")?.let { counts -> listOf("attemptedCount", "expandedStates", "actionScores").map { counts[it].asInt } })
                    }
            }
        }
        var crossChecked = 0
        var identicalSets = 0
        var identicalCounters = 0
        requireFreshOutput(output)
        val timer = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "construction-fourth-deadline").apply { isDaemon = true } }
        val rows = ArrayList<JsonObject>()
        try {
            warm.forEach { task -> evaluateTask(task.group, policy, refitted, tiny, timer) }
            File(output, "results.jsonl").bufferedWriter(Charsets.UTF_8).use { sink ->
                tasks.forEachIndexed { index, task ->
                    val group = task.group
                    val rankings = evaluateTask(group, policy, refitted, tiny, timer)
                    val future = future(group)
                    val values = HashMap<String, Double>()
                    fun quality(candidate: Verified<SnapshotNode>): Double = values.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) { survival(candidate.plan, future) }
                    val candidates = rankings.associate { it.name to it.candidates.associate { c -> ConstructionSearch.canonicalKey(c.plan) to Candidate(c, quality(c)) } }
                    val unionBest = candidates.values.flatMap { it.values }.maxOfOrNull { it.survival } ?: 0.0
                    val baselineKeys = candidates.getValue("rule_pipeline").keys
                    val baselineBest = candidates.getValue("rule_pipeline").values.maxOfOrNull { it.survival } ?: 0.0
                    val baselineRule = rankings.first { it.name == "rule_pipeline" }.order.firstOrNull()?.let { candidates.getValue("rule_pipeline")[it]?.survival } ?: 0.0
                    for (ranking in rankings) {
                        val own = candidates.getValue(ranking.name)
                        val ordered = ranking.order.mapNotNull(own::get)
                        val top1 = ordered.firstOrNull()?.survival ?: 0.0
                        val top3 = ordered.take(3).maxOfOrNull { it.survival } ?: 0.0
                        val best = own.values.maxOfOrNull { it.survival } ?: 0.0
                        val row = JsonObject().apply {
                            addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily)
                            addProperty("source", group.source); addProperty("split", task.split); addProperty("cohort", task.cohort)
                            addProperty("method", ranking.name); addProperty("candidates", own.size); addProperty("coverage", if (own.isEmpty()) 0.0 else 1.0)
                            addProperty("bestSurvival", best); addProperty("top1Survival", top1); addProperty("top3BestSurvival", top3)
                            addProperty("commonBestSurvival", unionBest); addProperty("commonRegret", unionBest - top1)
                            addProperty("commonTop3Recall", if (ordered.isNotEmpty() && top3 >= unionBest) 1.0 else 0.0)
                            addProperty("novelCandidates", own.keys.count { it !in baselineKeys })
                            addProperty("betterThanBaselineBest", best > baselineBest); addProperty("baselineBestSurvival", baselineBest)
                            addProperty("baselineRuleTop1Survival", baselineRule); addProperty("worseThanBaselineRule", top1 < baselineRule)
                            addProperty("elapsedMillis", ranking.elapsedMillis); addProperty("searchMillis", ranking.searchMillis)
                            addProperty("attemptedCount", ranking.attemptedCount); addProperty("stopReason", ranking.stopReason); addProperty("error", ranking.error)
                            add("constructionCounts", ranking.construction?.stats?.let(gson::toJsonTree))
                            add("candidatesDetail", JsonArray().apply { own.forEach { (key, candidate) -> add(JsonObject().apply {
                                addProperty("canonicalKey", key); addProperty("survival", candidate.survival)
                                addProperty("topRank", ranking.order.indexOf(key)); addProperty("planKey", candidate.value.plan.planKey)
                            }) } })
                        }
                        sink.write(gson.toJson(row)); sink.newLine()
                        rows += row.deepCopy().apply { remove("candidatesDetail") }
                    }
                    thirdSets?.get(group.groupId)?.let { (keys, counters) ->
                        val construction = rankings.first { it.name == "construction_ranker" }
                        crossChecked++
                        if (construction.candidates.map { ConstructionSearch.canonicalKey(it.plan) }.toSet() == keys) identicalSets++
                        val stats = construction.construction?.stats
                        if (stats != null && counters == listOf(stats.attemptedCount, stats.expandedStates, stats.actionScores)) identicalCounters++
                    }
                    if (index % 28 == 0 || index == tasks.lastIndex) println("Construction fourth $mode ${index + 1}/${tasks.size}")
                }
            }
        } finally { timer.shutdownNow() }
        validateProtocol(inputs, protocol)
        require(hash(protocolFile) == protocolSha256) { "Protocol changed during evaluation" }
        if (selectionSha256 != null) require(hash(File(inputs.protocol, "selection.json")) == selectionSha256) { "Selection changed during evaluation" }
        writeJson(File(output, "metrics.json"), JsonObject().apply {
            addProperty("format", 1); addProperty("phase", mode); addProperty("datasetId", protocol["datasetId"].asString)
            addProperty("protocolSha256", protocolSha256); add("sourceSha256", protocol.getAsJsonObject("sourceSha256"))
            addProperty("constructionPolicySha256", POLICY_SHA256); addProperty("tinyRankerSha256", TINY_SHA256)
            addProperty("refittedRankerSha256", protocol["refittedRankerSha256"].asString)
            addProperty("hardDeadlineMillis", DEADLINE_MS); add("constructionBudget", gson.toJsonTree(constructionBudget))
            addProperty("realApplicationGroups", 0); addProperty("groups", tasks.size)
            addProperty("refittedRankerTrees", refitted.treeCount); addProperty("refittedRankerStorageBytes", refitted.storageBytes)
            if (thirdSets != null) add("constructionReproducesThird", JsonObject().apply {
                addProperty("compared", crossChecked); addProperty("identicalCandidateSets", identicalSets); addProperty("identicalCounters", identicalCounters)
            })
            add("summary", aggregate(rows)); add("pairwise", pairwise(rows))
            addProperty("resultsSha256", hash(File(output, "results.jsonl")))
            if (selectionSha256 != null) addProperty("selectionSha256", selectionSha256)
        })
    }

    /** One rule search, one construction, one shared-budget pair, then every ranking of every candidate set. */
    private fun evaluateTask(group: TaskGroup, policy: ConstructionPolicyModel, refitted: ObliviousTreeModel, tiny: ObliviousTreeModel, timer: ScheduledExecutorService): List<Ranking> {
        val capture = group.states[group.captureState]
        val request = group.request.forState(capture)
        val cancellation = CancellationSignal()
        val started = System.nanoTime()
        val deadline = timer.schedule({ cancellation.cancel() }, DEADLINE_MS, TimeUnit.MILLISECONDS)
        fun millis() = (System.nanoTime() - started) / 1e6
        val searches: Searches = try {
            val verifier = PlanVerifier(capture.root, cancellation)
            val baseline = CandidateSearch(capture.root, cancellation, SearchBudget(), verifier).search(request)
            val baselineMillis = millis()
            val remaining = (SEARCH_MS - baselineMillis.toLong()).coerceAtLeast(1)
            val construction = ConstructionSearch(capture.root, cancellation, constructionBudget.copy(maxMillis = remaining), policy, verifier,
                frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
            val constructionMillis = millis() - baselineMillis
            cancellation.throwIfCancelled()
            // The shared-budget pair runs after the main pair and does not count toward its timing.
            val sharedStart = millis()
            val sharedVerifier = PlanVerifier(capture.root, cancellation)
            val sharedBaseline = CandidateSearch(capture.root, cancellation, sharedBaselineBudget, sharedVerifier).search(request)
            val sharedBaselineMillis = millis() - sharedStart
            val sharedRemaining = (SEARCH_MS - sharedBaselineMillis.toLong()).coerceAtLeast(1)
            val sharedConstruction = ConstructionSearch(capture.root, cancellation, sharedConstructionBudget.copy(maxMillis = sharedRemaining), policy, sharedVerifier,
                frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
            val sharedConstructionMillis = millis() - sharedStart - sharedBaselineMillis
            Searches(baseline.candidates, baseline.attemptedCount, baseline.budgetExhausted, baselineMillis, construction, constructionMillis,
                sharedBaseline.candidates, sharedBaseline.attemptedCount, sharedBaselineMillis, sharedConstruction, sharedConstructionMillis, null, false)
        } catch (cancelled: CodeGenCancelledException) {
            if (!cancellation.isCancelled) throw cancelled
            Searches(emptyList(), 0, false, millis(), null, 0.0, emptyList(), 0, 0.0, null, 0.0, null, true)
        } catch (failure: Exception) {
            Searches(emptyList(), 0, false, millis(), null, 0.0, emptyList(), 0, 0.0, null, 0.0, failure.javaClass.simpleName + ": " + failure.message, false)
        } finally { deadline.cancel(false) }
        fun unique(vararg sets: List<Verified<SnapshotNode>>): List<Verified<SnapshotNode>> {
            val union = LinkedHashMap<String, Verified<SnapshotNode>>()
            for (set in sets) for (candidate in set) union.putIfAbsent(ConstructionSearch.canonicalKey(candidate.plan), candidate)
            return union.values.toList()
        }
        fun rank(name: String, candidates: List<Verified<SnapshotNode>>, ranker: String, searchMillis: Double, attempted: Int, stop: String, construction: ConstructionResult<SnapshotNode>?): Ranking {
            if (searches.cancelled) return Ranking(name, emptyList(), emptyList(), searches.baselineMillis, searches.baselineMillis, attempted, "HARD_DEADLINE", construction, null)
            if (searches.failure != null) return Ranking(name, emptyList(), emptyList(), searches.baselineMillis, searches.baselineMillis, attempted, "FAILED", construction, searches.failure)
            val rankStart = System.nanoTime()
            return try {
                val order = when (ranker) {
                    "rule" -> RuleRanker().rank(request, candidates)
                    "tiny" -> ModelRanker(tiny, budgetMs = 150).rank(request, candidates)
                    else -> ModelRanker(refitted, budgetMs = 150).rank(request, candidates)
                }.map { ConstructionSearch.canonicalKey(it.plan) }
                val byKey = candidates.associateBy { ConstructionSearch.canonicalKey(it.plan) }
                order.take(10).forEach { key -> ClassicRenderer().render(request, byKey.getValue(key).plan) }
                val elapsed = searchMillis + (System.nanoTime() - rankStart) / 1e6
                if (elapsed > DEADLINE_MS) Ranking(name, emptyList(), emptyList(), elapsed, searchMillis, attempted, "HARD_DEADLINE", construction, null)
                else Ranking(name, candidates, order, elapsed, searchMillis, attempted, stop, construction, null)
            } catch (failure: Exception) {
                Ranking(name, emptyList(), emptyList(), searchMillis + (System.nanoTime() - rankStart) / 1e6, searchMillis, attempted, "FAILED", construction, failure.javaClass.simpleName + ": " + failure.message)
            }
        }
        val baseline = unique(searches.baseline)
        val constructed = unique(searches.construction?.candidates.orEmpty())
        val merged = unique(searches.baseline, searches.construction?.candidates.orEmpty())
        val shared = unique(searches.sharedBaseline, searches.sharedConstruction?.candidates.orEmpty())
        val baselineStop = if (searches.baselineExhausted) "COUNTER_BUDGET" else "EXHAUSTED"
        val constructionStop = searches.construction?.stopReason?.name ?: "NONE"
        val mergedMillis = searches.baselineMillis + searches.constructionMillis
        val mergedAttempts = searches.baselineAttempts + (searches.construction?.stats?.attemptedCount ?: 0)
        val sharedMillis = searches.sharedBaselineMillis + searches.sharedConstructionMillis
        val sharedAttempts = searches.sharedBaselineAttempts + (searches.sharedConstruction?.stats?.attemptedCount ?: 0)
        return listOf(
            rank("rule_pipeline", baseline, "rule", searches.baselineMillis, searches.baselineAttempts, baselineStop, null),
            rank("baseline_tiny", baseline, "tiny", searches.baselineMillis, searches.baselineAttempts, baselineStop, null),
            rank("baseline_ranker", baseline, "refitted", searches.baselineMillis, searches.baselineAttempts, baselineStop, null),
            rank("construction_ranker", constructed, "refitted", mergedMillis, searches.construction?.stats?.attemptedCount ?: 0, constructionStop, searches.construction),
            rank("merged_rule", merged, "rule", mergedMillis, mergedAttempts, constructionStop, searches.construction),
            rank("merged_tiny", merged, "tiny", mergedMillis, mergedAttempts, constructionStop, searches.construction),
            rank("merged_ranker", merged, "refitted", mergedMillis, mergedAttempts, constructionStop, searches.construction),
            rank("merged_ranker_shared", shared, "refitted", sharedMillis, sharedAttempts, searches.sharedConstruction?.stopReason?.name ?: "NONE", searches.sharedConstruction),
        )
    }

    private fun binding(inputs: Inputs): JsonObject {
        val mergedManifest = readJson(File(inputs.merged, "export-manifest.json"))
        require(mergedManifest["tool"].asString == ConstructionMergedExport.TOOL)
        require(mergedManifest["constructionPolicySha256"].asString == POLICY_SHA256) { "The merged export used another construction policy" }
        require(hash(File(inputs.policy, "policy.json")) == POLICY_SHA256)
        val datasetManifest = readJson(File(inputs.merged, "dataset-manifest.json"))
        val artifacts = datasetManifest.getAsJsonObject("artifacts")
        val mergedHashes = JsonObject().apply {
            artifacts.entrySet().forEach { (name, artifact) ->
                val file = safeFile(inputs.merged, artifact.asJsonObject["file"].asString)
                val digest = hash(file)
                require(digest == artifact.asJsonObject["sha256"].asString) { "Merged dataset artifact changed: $name" }
                addProperty(name, digest)
            }
            addProperty("dataset-manifest.json", hash(File(inputs.merged, "dataset-manifest.json")))
        }
        val exportLog = readJson(File(inputs.ranker, "export-log.json"))
        require(exportLog["role"].asString == ExperimentRankerModel.ROLE && exportLog["datasetId"].asString == datasetManifest["datasetId"].asString)
        require(exportLog["modelSha256"].asString == hash(File(inputs.ranker, "ranker.json")))
        require(exportLog["paritySha256"].asString == hash(File(inputs.ranker, "parity.jsonl")))
        require(hash(tinyBytes()) == TINY_SHA256)
        return JsonObject().apply {
            addProperty("mergedDatasetId", datasetManifest["datasetId"].asString); add("mergedSha256", mergedHashes)
            addProperty("mergedExportSourceSha256", hash(gson.toJson(mergedManifest.getAsJsonObject("sourceSha256")).toByteArray(Charsets.UTF_8)))
            addProperty("rankerSha256", exportLog["modelSha256"].asString); addProperty("rankerExportLogSha256", hash(File(inputs.ranker, "export-log.json")))
            addProperty("rankerTrees", exportLog["treeCount"].asInt); addProperty("rankerFitTrainLogSha256", exportLog["fitTrainLogSha256"].asString)
            addProperty("policyTrainLogSha256", hash(File(inputs.policy, "train-log.json")))
        }
    }

    private fun validateProtocol(inputs: Inputs, protocol: JsonObject) {
        require(protocol["format"].asInt == 1 && protocol["role"].asString == "construction-fourth-experiment")
        require(protocol["seed"].asLong == SEED && protocol["hardDeadlineMillis"].asLong == DEADLINE_MS)
        require(protocol.getAsJsonObject("constructionBudget") == gson.toJsonTree(constructionBudget))
        require(protocol.getAsJsonArray("methods").map { it.asString } == methods)
        require(protocol.getAsJsonObject("sourceSha256") == sourceIdentity()) { "Source changed after freeze; use a new protocol and development run" }
        require(protocol["constructionPolicySha256"].asString == POLICY_SHA256 && protocol["tinyRankerSha256"].asString == TINY_SHA256)
        require(protocol.getAsJsonObject("binding") == binding(inputs)) { "Frozen binding changed" }
        require(protocol["thirdProtocolSha256"].asString == hash(File(inputs.thirdProtocol, "protocol.json")))
        checkHashes(inputs.dataset, protocol.getAsJsonObject("inputs"))
    }

    private fun validateSelection(protocolFile: File, protocol: JsonObject, development: File) {
        val selection = readJson(File(protocolFile.parentFile, "selection.json"))
        require(selection["format"].asInt == 1 && selection["chosenMethod"].asString == CHOSEN && selection["passed"].asBoolean)
        require(selection["protocolSha256"].asString == hash(protocolFile))
        require(selection.getAsJsonObject("sourceSha256") == protocol.getAsJsonObject("sourceSha256"))
        val metricsFile = File(development, "metrics.json")
        val resultsFile = File(development, "results.jsonl")
        require(selection["developmentMetricsSha256"].asString == hash(metricsFile))
        require(selection["developmentResultsSha256"].asString == hash(resultsFile))
        val metrics = readJson(metricsFile)
        require(metrics["phase"].asString == "develop" && metrics["protocolSha256"].asString == hash(protocolFile))
        require(metrics["resultsSha256"].asString == hash(resultsFile))
        require(metrics.getAsJsonObject("sourceSha256") == protocol.getAsJsonObject("sourceSha256"))
        val expected = protocol.getAsJsonArray("developmentGroups").map { it.asJsonObject }.associate { it["groupId"].asString to it["split"].asString }
        val rows = resultsFile.useLines(Charsets.UTF_8) { lines -> lines.map { JsonParser.parseString(it).asJsonObject }.toList() }
        require(rows.size == expected.size * methods.size)
        require(rows.map { it["groupId"].asString to it["method"].asString }.distinct().size == rows.size)
        rows.forEach { row ->
            require(row["method"].asString in methods && expected[row["groupId"].asString] == row["split"].asString)
            require(row["cohort"].asString == "captured-development" && row["split"].asString in setOf("train", "valid"))
            require(row["stopReason"].asString !in failedStops && row["error"].isJsonNull) { "Development contained a failed or late run" }
            decisionMetrics.forEach { metric -> require(row[metric].asDouble.isFinite() && row[metric].asDouble in 0.0..1.0) }
        }
        fun mean(split: String, method: String, metric: String) = rows.filter { it["split"].asString == split && it["method"].asString == method }.map { it[metric].asDouble }.average()
        require(noRegressionAgainst.all { mean("valid", CHOSEN, "top1Survival") >= mean("valid", it, "top1Survival") }) { "Validation top1 regressed against a baseline" }
        require(listOf("train", "valid").any { split -> listOf("top1Survival", "top3BestSurvival").any { mean(split, CHOSEN, it) > mean(split, GAIN_REFERENCE, it) } }) { "No strict construction gain" }
    }

    private fun aggregate(rows: List<JsonObject>): JsonArray = JsonArray().apply {
        rows.groupBy { listOf(it["cohort"].asString, it["split"].asString, it["method"].asString) }
            .toSortedMap(compareBy<List<String>> { it.joinToString("/") }).forEach { (key, members) -> add(JsonObject().apply {
                addProperty("cohort", key[0]); addProperty("split", key[1]); addProperty("method", key[2]); addProperty("groups", members.size)
                for (metric in listOf("candidates", "coverage", "bestSurvival", "top1Survival", "top3BestSurvival", "commonRegret", "commonTop3Recall", "baselineRuleTop1Survival", "attemptedCount")) {
                    addProperty(metric, members.map { it[metric].asDouble }.average())
                }
                addProperty("novelCandidates", members.sumOf { it["novelCandidates"].asInt })
                addProperty("groupsAboveBaselineBest", members.count { it["betterThanBaselineBest"].asBoolean })
                addProperty("groupsWorseThanBaselineRule", members.count { it["worseThanBaselineRule"].asBoolean })
                val times = members.map { it["elapsedMillis"].asDouble }.sorted()
                addProperty("elapsedP50Millis", times[(ceil(times.size * .5).toInt() - 1).coerceAtLeast(0)])
                addProperty("elapsedP95Millis", times[(ceil(times.size * .95).toInt() - 1).coerceAtLeast(0)])
                addProperty("elapsedMaxMillis", times.last())
                add("stopReasons", gson.toJsonTree(members.groupingBy { it["stopReason"].asString }.eachCount()))
                addProperty("errors", members.count { !it["error"].isJsonNull })
            }) }
    }

    private fun pairwise(rows: List<JsonObject>): JsonArray = JsonArray().apply {
        rows.groupBy { it["cohort"].asString to it["split"].asString }.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .forEach { (cohort, members) ->
                val groups = members.groupBy { it["groupId"].asString }
                for (reference in methods.filter { it != CHOSEN }) add(JsonObject().apply {
                    addProperty("cohort", cohort.first); addProperty("split", cohort.second)
                    addProperty("method", CHOSEN); addProperty("reference", reference); addProperty("groups", groups.size)
                    for (metric in listOf("coverage", "bestSurvival", "top1Survival", "top3BestSurvival")) {
                        val differences = groups.values.map { records ->
                            records.single { it["method"].asString == CHOSEN }[metric].asDouble - records.single { it["method"].asString == reference }[metric].asDouble
                        }
                        add(metric, JsonObject().apply {
                            addProperty("improved", differences.count { it > 0 }); addProperty("tied", differences.count { it == 0.0 })
                            addProperty("worsened", differences.count { it < 0 }); addProperty("meanDifference", differences.average())
                        })
                    }
                })
            }
    }

    private fun metadata(group: TaskGroup, split: String, cohort: String) = JsonObject().apply {
        addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily); addProperty("source", group.source)
        addProperty("split", split); addProperty("cohort", cohort); addProperty("captureState", group.captureState)
        add("request", gson.toJsonTree(group.request))
        add("states", JsonArray().apply { group.states.forEach { state -> add(JsonObject().apply {
            addProperty("stateId", state.stateId); addProperty("snapshot", state.snapshot)
            add("mutations", gson.toJsonTree(state.mutations)); add("targetPaths", gson.toJsonTree(state.targetPaths))
            addProperty("snapshotContentSha256", hash(gson.toJson(state.root.snapshot).toByteArray(Charsets.UTF_8)))
        }) } })
    }

    private fun future(group: TaskGroup) = group.states.filterIndexed { index, _ -> index != group.captureState }
        .map { group.request.forState(it) to PlanVerifier(it.root) }
    private fun survival(plan: LocatePlan, future: List<Pair<CodeGenRequest<SnapshotNode>, PlanVerifier<SnapshotNode>>>): Double =
        future.count { (request, verifier) -> verifier.verify(request, plan) is Verified }.toDouble() / future.size

    private fun sourceIdentity(): JsonObject {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val base = "app/src/test/java/org/autojs/autojs/core/accessibility/codegen/construction/"
        val paths = listOf("ConstructionGrammar.kt", "ConstructionFeatures.kt", "ConstructionSearch.kt", "ConstructionPolicyModel.kt",
            "ConstructionFourthExperimentTask.kt", "ConstructionMergedExportTask.kt", "ExperimentRankerModel.kt",
            "ConstructionThirdSyntheticFixtures.kt", "ConstructionGeneralizationFixtures.kt", "ConstructionBottleneckFixtures.kt").map { base + it } +
            File(root, "app/src/main/java/org/autojs/autojs/core/accessibility/codegen").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.parentFile?.name != "store" }
                .map { it.relativeTo(root).invariantSeparatorsPath }.toList() + listOf(
                "app/src/main/java/org/autojs/autojs/core/accessibility/NodeInfoSnapshot.kt",
                "app/src/main/java/org/autojs/autojs/core/automator/UiNode.kt",
                "tools/codegen-model/construction/select_fourth.py", "tools/codegen-model/construction/export_ranker.py")
        return JsonObject().apply { paths.distinct().sorted().forEach { path ->
            addProperty(path, hash(File(root, path).readText(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)))
        } }
    }

    private fun tinyBytes() = requireNotNull(javaClass.getResourceAsStream("/codegen/models/tiny-test-v1.ajcgm")).use { it.readBytes() }
    private fun snapshotCache() = object : LinkedHashMap<File, SnapshotNode>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
    }
    private fun relativeInside(root: File, file: File): String {
        val base = root.canonicalFile
        val path = file.canonicalFile
        require(path.toPath().startsWith(base.toPath()) && path != base) { "Input path escaped its root" }
        return path.relativeTo(base).invariantSeparatorsPath
    }
    private fun safeFile(root: File, relative: String) = File(root, relative).canonicalFile.also { require(relativeInside(root, it) == relative) }
    private fun checkHashes(root: File, expected: JsonObject) {
        expected.entrySet().forEach { (path, digest) -> require(hash(safeFile(root, path)) == digest.asString) { "Frozen input changed: $path" } }
    }
    private fun requireFreshOutput(output: File) {
        require(!output.exists()) { "Use a fresh fourth construction experiment output directory" }
        require(output.mkdirs()) { "Cannot create experiment output" }
    }
    private fun readJson(file: File) = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
    private fun writeJson(file: File, value: JsonObject) = file.writeText(gson.toJson(value) + "\n", Charsets.UTF_8)
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(65536)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
