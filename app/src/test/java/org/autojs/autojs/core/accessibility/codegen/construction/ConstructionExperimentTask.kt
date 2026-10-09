package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.FeatureExport
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackage
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
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
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Explicit offline prototype: no construction weights, raw traces or snapshots enter the APK.
 * zh-CN: 显式运行的离线原型; 构造权重、原始轨迹与快照不进入 APK. */
class ConstructionExperimentTask {
    @Test fun runOfflineConstructionExperiment() {
        val mode = System.getenv("CODEGEN_CONSTRUCTION_MODE")
        assumeTrue("Set CODEGEN_CONSTRUCTION_MODE=export or evaluate", mode != null)
        val input = File(requireNotNull(System.getenv("CODEGEN_CONSTRUCTION_DATASET"))).canonicalFile
        val output = File(requireNotNull(System.getenv("CODEGEN_CONSTRUCTION_OUTPUT"))).canonicalFile
        when (requireNotNull(mode)) {
            "export" -> ConstructionExperiment.export(input, output,
                System.getenv("CODEGEN_CONSTRUCTION_PER_FAMILY")?.toInt() ?: 28,
                System.getenv("CODEGEN_CONSTRUCTION_TEACHER_RUNS")?.toInt() ?: 3)
            "evaluate" -> ConstructionExperiment.evaluate(input,
                File(requireNotNull(System.getenv("CODEGEN_CONSTRUCTION_EXPORT"))),
                File(requireNotNull(System.getenv("CODEGEN_CONSTRUCTION_POLICY_DIR"))), output)
            else -> error("Unknown construction experiment mode")
        }
    }
}

/** Fixed experiment protocol. Held-out results never choose budgets, weights or input groups. */
internal object ConstructionExperiment {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private const val SEED = 20261007L
    private const val DEADLINE_MS = 1000L
    private const val SEARCH_MS = 800L // reserve time for the frozen final ranker and renderer
    private val evaluationBudget = ConstructionBudget(maxMillis = SEARCH_MS)
    private val teacherBudget = ConstructionBudget(maxFrontier = 128, maxExpandedStates = 4000,
        maxActionScores = 24000, maxMillis = 30_000)

    private data class TaskRef(val file: File, val groupId: String, val family: String, val split: String)
    private class ObservedAction(val features: FloatArray) {
        var total = 0.0
        var count = 0
        fun add(vector: FloatArray, value: Double) {
            check(features.contentEquals(vector)) { "The same state/action changed its features" }
            total += value; count++
        }
    }
    private data class Candidate(val value: Verified<SnapshotNode>, val survival: Double)
    private data class Run(
        val name: String,
        val candidates: List<Verified<SnapshotNode>>,
        val order: List<String>,
        val ruleKey: String?,
        val elapsedMillis: Double,
        val searchMillis: Double,
        val attemptedCount: Int?,
        val stopReason: String,
        val construction: ConstructionResult<SnapshotNode>?,
        val error: String? = null,
    )

    fun export(input: File, output: File, perFamily: Int, teacherRuns: Int) {
        require(perFamily in 1..336 && teacherRuns in 1..8)
        require(input.isDirectory)
        requireFreshOutput(output)
        val inputs = sortedMapOf<String, String>()
        fun record(file: File) {
            val path = file.canonicalFile
            require(path.toPath().startsWith(input.toPath()))
            inputs.getOrPut(path.relativeTo(input).invariantSeparatorsPath) { hash(path) }
        }
        val splitFile = File(input, "split-map.json").also(::record)
        val originalManifest = File(input, "dataset-manifest.json").also(::record)
        val split = JsonParser.parseString(splitFile.readText()).asJsonObject
        val assignment = split.getAsJsonObject("assignment").entrySet().associate { it.key to it.value.asString }
        val refs = File(input, "groups").walkTopDown().filter { it.isFile && it.name.endsWith(".cgtask.json") }.map { file ->
            record(file)
            val json = JsonParser.parseString(file.readText()).asJsonObject
            require(json["source"].asString == "fixture") { "This first prototype accepts controlled captures only" }
            TaskRef(file, json["groupId"].asString, json["screenFamily"].asString,
                assignment.getValue(json["groupId"].asString))
        }.toList()
        require(refs.map { it.groupId }.toSet() == assignment.keys)
        require(refs.map { it.groupId }.distinct().size == refs.size)
        require(refs.all { it.split in setOf("train", "valid", "test") })
        require(refs.groupBy { it.family }.values.all { family -> family.map { it.split }.distinct().size == 1 })
        val selected = refs.groupBy { it.family }.toSortedMap().values.flatMap { family ->
            family.sortedBy { hash("construction-pilot-$SEED:${it.groupId}".toByteArray()) }.take(perFamily)
        }.sortedBy { it.groupId }
        writeJson(File(output, "features.json"), schema())
        val groupMetadata = JsonArray()
        var actionRows = 0L
        var observedEdges = 0L
        var unknownEdges = 0L
        var terminalCount = 0L
        var validTerminalCount = 0L
        val snapshots = snapshotCache()
        File(output, "actions.csv").bufferedWriter(Charsets.UTF_8).use { csv ->
            csv.write(FeatureExport.csvRow(listOf("split", "groupId", "stateKey", "actionKey", "label", "observations") + ConstructionFeatures.names))
            File(output, "teacher-traces.jsonl").bufferedWriter(Charsets.UTF_8).use { traces ->
                selected.forEachIndexed { position, ref ->
                    val group = TaskGroup.load(ref.file) { file ->
                        record(file); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) }
                    }
                    group.requireLabelable()
                    check(group.groupId == ref.groupId && group.screenFamily == ref.family)
                    val capture = group.states[group.captureState]
                    val request = group.request.forState(capture)
                    val future = future(group)
                    val returns = HashMap<String, Double>()
                    val observed = sortedMapOf<Pair<String, String>, ObservedAction>(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
                    val scoredEdges = HashSet<Pair<String, String>>()
                    var groupTerminals = 0
                    var groupValid = 0
                    val runMetadata = JsonArray()
                    repeat(teacherRuns) { runIndex ->
                        val random = Random(SEED xor group.groupId.hashCode().toLong() xor (runIndex.toLong() shl 32))
                        val policy = if (runIndex == 0) ConstructionHeuristicPolicy else ConstructionPolicy { vector ->
                            // Explore independently of future states. Labels are computed afterward.
                            ConstructionHeuristicPolicy.score(vector) + (random.nextDouble() - .5) * 2.0
                        }
                        val result = ConstructionSearch(capture.root, budget = teacherBudget, policy = policy).search(request)
                        require(result.stopReason !in setOf(ConstructionStopReason.DEADLINE, ConstructionStopReason.POLICY_FAILURE)) {
                            "Teacher must complete under deterministic counters: ${group.groupId} ${result.stopReason}"
                        }
                        result.expansions.forEach { expansion -> expansion.scoredActions.forEach { scoredEdges += it.stateKey to it.actionKey } }
                        for (completion in result.completions) {
                            val verified = completion.simplified ?: (completion.verification as? Verified<SnapshotNode>)
                            val value = if (verified == null) 0.0 else returns.getOrPut(ConstructionSearch.canonicalKey(verified.plan)) {
                                survival(verified.plan, future)
                            }
                            groupTerminals++
                            if (verified != null) groupValid++
                            completion.decisions.forEach { decision ->
                                observed.getOrPut(decision.stateKey to decision.actionKey) { ObservedAction(decision.features.copyOf()) }
                                    .add(decision.features, value)
                            }
                            val trace = JsonObject().apply {
                                addProperty("groupId", group.groupId); addProperty("split", ref.split)
                                addProperty("run", runIndex); addProperty("validAtCapture", verified != null)
                                addProperty("observedReturn", value)
                                addProperty("rawPlanKey", completion.plan.planKey)
                                addProperty("canonicalKey", verified?.plan?.let { ConstructionSearch.canonicalKey(it) })
                                add("decisions", traceDecisions(completion.decisions))
                            }
                            traces.write(gson.toJson(trace)); traces.newLine()
                        }
                        runMetadata.add(JsonObject().apply {
                            addProperty("run", runIndex); addProperty("stopReason", result.stopReason.name)
                            add("counts", deterministicStats(result.stats))
                        })
                    }
                    observed.forEach { (edge, value) ->
                        csv.write(FeatureExport.csvRow(listOf(ref.split, group.groupId, edge.first, edge.second,
                            (value.total / value.count).toString(), value.count.toString()) + value.features.map(Float::toString)))
                    }
                    val metadata = JsonObject().apply {
                        addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily)
                        addProperty("source", group.source); addProperty("split", ref.split)
                        addProperty("task", ref.file.relativeTo(input).invariantSeparatorsPath)
                        addProperty("rows", observed.size); addProperty("scoredEdges", scoredEdges.size)
                        addProperty("unknownEdges", scoredEdges.count { it !in observed })
                        addProperty("completedRollouts", groupTerminals); addProperty("validCompletedRollouts", groupValid)
                        add("runs", runMetadata)
                    }
                    groupMetadata.add(metadata)
                    actionRows += observed.size; observedEdges += observed.size
                    unknownEdges += scoredEdges.count { it !in observed }; terminalCount += groupTerminals; validTerminalCount += groupValid
                    if (position % 14 == 0 || position == selected.lastIndex) {
                        println("Construction export ${position + 1}/${selected.size}: rows=$actionRows, terminal=$terminalCount")
                    }
                }
            }
        }
        inputs.forEach { (path, digest) -> require(hash(File(input, path)) == digest) { "Dataset changed while exporting" } }
        val manifest = JsonObject().apply {
            addProperty("format", 1); addProperty("datasetId", split["datasetId"].asString + "-construction-pilot")
            addProperty("sourceManifestSha256", hash(originalManifest))
            addProperty("featuresSha256", hash(File(output, "features.json")))
            addProperty("actionsSha256", hash(File(output, "actions.csv")))
            addProperty("tracesSha256", hash(File(output, "teacher-traces.jsonl")))
            addProperty("sampling", "sha256:construction-pilot-$SEED:groupId, first $perFamily per original family")
            addProperty("teacherRuns", teacherRuns); addProperty("traceFeatures", "Resolve (groupId,stateKey,actionKey) in actions.csv")
            addProperty("labelSemantics", "mean of observed completed rollout survival; unknown edges omitted")
            addProperty("rows", actionRows); addProperty("observedEdges", observedEdges); addProperty("unknownEdges", unknownEdges)
            addProperty("completedRollouts", terminalCount); addProperty("validCompletedRollouts", validTerminalCount)
            add("teacherBudget", gson.toJsonTree(teacherBudget)); add("evaluationBudget", gson.toJsonTree(evaluationBudget))
            addProperty("evaluationHardDeadlineMillis", DEADLINE_MS)
            add("inputs", gson.toJsonTree(inputs)); add("sourceSha256", sourceIdentity()); add("groups", groupMetadata)
        }
        writeJson(File(output, "export-manifest.json"), manifest) // last: incomplete output cannot be trained
    }

    fun evaluate(input: File, export: File, policyDirectory: File, output: File) {
        requireFreshOutput(output)
        val manifest = JsonParser.parseString(File(export, "export-manifest.json").readText()).asJsonObject
        manifest.getAsJsonObject("inputs").entrySet().forEach { (path, digest) ->
            val file = File(input, path).canonicalFile
            require(file.toPath().startsWith(input.canonicalFile.toPath()))
            require(hash(file) == digest.asString) { "Frozen evaluation input changed: $path" }
        }
        require(manifest.getAsJsonObject("sourceSha256") == sourceIdentity()) { "Construction source changed after the frozen export" }
        require(manifest.getAsJsonObject("evaluationBudget") == gson.toJsonTree(evaluationBudget))
        require(manifest["evaluationHardDeadlineMillis"].asLong == DEADLINE_MS)
        val training = JsonParser.parseString(File(policyDirectory, "train-log.json").readText()).asJsonObject
        for (name in listOf("features.json", "actions.csv", "export-manifest.json")) {
            require(training.getAsJsonObject("inputSha256")[name].asString == hash(File(export, name))) { "Policy was trained from a different export" }
        }
        require(training["modelSha256"].asString == hash(File(policyDirectory, "policy.json")))
        val loadStarted = System.nanoTime()
        val model = ConstructionPolicyModel.read(File(policyDirectory, "policy.json"))
        val policyLoadMillis = (System.nanoTime() - loadStarted) / 1e6
        val frozenRankerBytes = requireNotNull(javaClass.getResourceAsStream("/codegen/models/tiny-test-v1.ajcgm")).use { it.readBytes() }
        val fixed = ModelPackageReader().read(frozenRankerBytes.inputStream()) as ModelPackage
        val ranker = fixed.model
        val snapshots = snapshotCache()
        val tasks = manifest.getAsJsonArray("groups").map { metadata ->
            val item = metadata.asJsonObject
            val group = TaskGroup.load(File(input, item["task"].asString)) { file -> snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
            Triple(group, item["split"].asString, "captured-fixture")
        } + ConstructionBottleneckFixtures.groups().map { Triple(it, "test", "synthetic-bottleneck") }
        require(tasks.map { it.first.groupId }.distinct().size == tasks.size)
        val timer = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "construction-prototype-deadline").apply { isDaemon = true } }
        val rows = ArrayList<JsonObject>()
        try {
            // Warm all engines with training captures only; these measurements are not reported.
            tasks.filter { it.second == "train" }.take(3).forEach { (group, _, _) ->
                for (kind in listOf("baseline", "heuristic", "model")) run(group, kind, model, ranker, timer)
            }
            File(output, "results.jsonl").bufferedWriter(Charsets.UTF_8).use { sink ->
                tasks.forEachIndexed { index, (group, split, cohort) ->
                    val kinds = listOf("baseline", "heuristic", "model")
                    val rotated = kinds.indices.map { kinds[(it + index) % kinds.size] }
                    val runs = rotated.map { run(group, it, model, ranker, timer) }.associateBy { it.name }
                    val future = future(group)
                    val values = HashMap<String, Double>()
                    fun quality(candidate: Verified<SnapshotNode>): Double = values.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) { survival(candidate.plan, future) }
                    val candidates = runs.mapValues { (_, run) -> run.candidates.associate { ConstructionSearch.canonicalKey(it.plan) to Candidate(it, quality(it)) } }
                    val unionBest = candidates.values.flatMap { it.values }.maxOfOrNull { it.survival } ?: 0.0
                    val baselineKeys = candidates.getValue("baseline").keys
                    val baselineBest = candidates.getValue("baseline").values.maxOfOrNull { it.survival } ?: 0.0
                    val baselineRule = runs.getValue("baseline").ruleKey?.let { candidates.getValue("baseline")[it]?.survival } ?: 0.0
                    for (kind in kinds) {
                        val run = runs.getValue(kind)
                        val own = candidates.getValue(kind)
                        val ordered = run.order.mapNotNull(own::get)
                        val top1 = ordered.firstOrNull()?.survival ?: 0.0
                        val top3 = ordered.take(3).maxOfOrNull { it.survival } ?: 0.0
                        val best = own.values.maxOfOrNull { it.survival } ?: 0.0
                        val record = JsonObject().apply {
                            addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily)
                            addProperty("source", group.source); addProperty("split", split); addProperty("cohort", cohort)
                            addProperty("method", kind); addProperty("candidates", own.size); addProperty("coverage", if (own.isEmpty()) 0.0 else 1.0)
                            addProperty("bestSurvival", best); addProperty("top1Survival", top1); addProperty("top3BestSurvival", top3)
                            addProperty("commonBestSurvival", unionBest); addProperty("commonRegret", unionBest - top1)
                            addProperty("commonTop3Recall", if (ordered.isNotEmpty() && top3 >= unionBest) 1.0 else 0.0)
                            addProperty("novelCandidates", own.keys.count { it !in baselineKeys })
                            addProperty("betterThanBaselineBest", best > baselineBest); addProperty("baselineBestSurvival", baselineBest)
                            addProperty("baselineRuleTop1Survival", baselineRule); addProperty("worseThanBaselineRule", top1 < baselineRule)
                            addProperty("elapsedMillis", run.elapsedMillis); addProperty("searchMillis", run.searchMillis)
                            addProperty("attemptedCount", run.attemptedCount); addProperty("stopReason", run.stopReason); addProperty("error", run.error)
                            add("constructionCounts", run.construction?.stats?.let(gson::toJsonTree))
                            add("candidatesDetail", JsonArray().apply { own.forEach { (key, candidate) -> add(JsonObject().apply {
                                addProperty("canonicalKey", key); addProperty("survival", candidate.survival)
                                addProperty("topRank", run.order.indexOf(key)); addProperty("planKey", candidate.value.plan.planKey)
                            }) } })
                        }
                        sink.write(gson.toJson(record)); sink.newLine(); rows += record.deepCopy().apply { remove("candidatesDetail") }
                        // One complete auditable trace per group/method; not a feature or training input.
                        run.construction?.completions?.firstOrNull { it.simplified != null }?.let { completion ->
                            val traceFile = File(output, "trace-${hash((group.groupId + kind).toByteArray()).take(16)}.json")
                            writeJson(traceFile, JsonObject().apply {
                                addProperty("groupId", group.groupId); addProperty("method", kind)
                                addProperty("planKey", completion.simplified!!.plan.planKey)
                                add("decisions", gson.toJsonTree(completion.decisions))
                            })
                        }
                    }
                    if (index % 14 == 0 || index == tasks.lastIndex) println("Construction evaluation ${index + 1}/${tasks.size}")
                }
            }
        } finally { timer.shutdownNow() }
        writeJson(File(output, "metrics.json"), JsonObject().apply {
            addProperty("format", 1); addProperty("datasetId", manifest["datasetId"].asString)
            addProperty("exportManifestSha256", hash(File(export, "export-manifest.json")))
            addProperty("policySha256", hash(File(policyDirectory, "policy.json")))
            addProperty("fixedRankerSha256", hash(frozenRankerBytes)); addProperty("fixedRanker", "P3 tiny v1 unchanged weights for all three constructors")
            addProperty("hardDeadlineMillis", DEADLINE_MS); add("constructionBudget", gson.toJsonTree(evaluationBudget))
            addProperty("realApplicationGroups", 0); addProperty("policyLoadMillis", policyLoadMillis)
            addProperty("policyStorageBytes", model.model.storageBytes)
            addProperty("policyTrees", model.model.treeCount); addProperty("policyFileBytes", File(policyDirectory, "policy.json").length())
            add("summary", aggregate(rows)); addProperty("resultsSha256", hash(File(output, "results.jsonl")))
        })
    }

    private fun run(group: TaskGroup, name: String, policy: ConstructionPolicy, rankingModel: ObliviousTreeModel,
                    timer: java.util.concurrent.ScheduledExecutorService): Run {
        val capture = group.states[group.captureState]
        val request = group.request.forState(capture)
        val cancellation = CancellationSignal()
        val started = System.nanoTime()
        val deadline = timer.schedule({ cancellation.cancel() }, DEADLINE_MS, TimeUnit.MILLISECONDS)
        var construction: ConstructionResult<SnapshotNode>? = null
        var attempted: Int? = null
        var searchMillis = 0.0
        return try {
            val verifier = PlanVerifier(capture.root, cancellation)
            val candidates: List<Verified<SnapshotNode>>
            val stop: String
            if (name == "baseline") {
                val baseline = CandidateSearch(capture.root, cancellation, SearchBudget(), verifier).search(request)
                candidates = baseline.candidates; attempted = baseline.attemptedCount
                stop = if (baseline.budgetExhausted) "COUNTER_BUDGET" else "EXHAUSTED"
            } else {
                val remaining = (SEARCH_MS - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
                construction = ConstructionSearch(capture.root, cancellation, evaluationBudget.copy(maxMillis = remaining),
                    if (name == "model") policy else ConstructionHeuristicPolicy, verifier).search(request)
                candidates = construction.candidates; attempted = construction.stats.attemptedCount; stop = construction.stopReason.name
            }
            searchMillis = (System.nanoTime() - started) / 1e6
            cancellation.throwIfCancelled()
            val unique = candidates.associateBy { ConstructionSearch.canonicalKey(it.plan) }.values.toList()
            val ruleKey = RuleRanker().rank(request, unique).firstOrNull()?.plan?.let { ConstructionSearch.canonicalKey(it) }
            cancellation.throwIfCancelled()
            val remainingRankingMillis = (DEADLINE_MS - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
            val order = ModelRanker(rankingModel, budgetMs = remainingRankingMillis, featureSchemaVersion = 1).rank(request, unique).map { ConstructionSearch.canonicalKey(it.plan) }
            unique.associateBy { ConstructionSearch.canonicalKey(it.plan) }.let { byKey ->
                order.take(10).forEach { key -> ClassicRenderer().render(request, byKey.getValue(key).plan) }
            }
            cancellation.throwIfCancelled()
            val elapsed = (System.nanoTime() - started) / 1e6
            if (elapsed > DEADLINE_MS) Run(name, emptyList(), emptyList(), null, elapsed, searchMillis, attempted, "HARD_DEADLINE", construction)
            else Run(name, unique, order, ruleKey, elapsed, searchMillis, attempted, stop, construction)
        } catch (cancelled: CodeGenCancelledException) {
            if (!cancellation.isCancelled) throw cancelled
            Run(name, emptyList(), emptyList(), null, (System.nanoTime() - started) / 1e6,
                if (searchMillis == 0.0) (System.nanoTime() - started) / 1e6 else searchMillis, attempted, "HARD_DEADLINE", construction)
        } catch (failure: Exception) {
            Run(name, emptyList(), emptyList(), null, (System.nanoTime() - started) / 1e6, searchMillis, attempted, "FAILED", construction,
                failure.javaClass.simpleName + ": " + failure.message)
        } finally { deadline.cancel(false) }
    }

    private fun aggregate(rows: List<JsonObject>): JsonArray = JsonArray().apply {
        rows.groupBy { listOf(it["cohort"].asString, it["split"].asString, it["method"].asString) }.toSortedMap(compareBy<List<String>> { it.joinToString("/") })
            .forEach { (key, members) -> add(JsonObject().apply {
                addProperty("cohort", key[0]); addProperty("split", key[1]); addProperty("method", key[2]); addProperty("groups", members.size)
                for (metric in listOf("coverage", "bestSurvival", "top1Survival", "top3BestSurvival", "commonRegret", "commonTop3Recall", "baselineRuleTop1Survival")) {
                    addProperty(metric, members.map { it[metric].asDouble }.average())
                }
                addProperty("novelCandidates", members.sumOf { it["novelCandidates"].asInt })
                addProperty("groupsAboveBaselineBest", members.count { it["betterThanBaselineBest"].asBoolean })
                addProperty("groupsWorseThanBaselineRule", members.count { it["worseThanBaselineRule"].asBoolean })
                val times = members.map { it["elapsedMillis"].asDouble }.sorted()
                addProperty("elapsedP50Millis", times[(ceil(times.size * .5).toInt() - 1).coerceAtLeast(0)])
                addProperty("elapsedP95Millis", times[(ceil(times.size * .95).toInt() - 1).coerceAtLeast(0)])
                add("stopReasons", gson.toJsonTree(members.groupingBy { it["stopReason"].asString }.eachCount()))
            }) }
    }

    private fun future(group: TaskGroup) = group.states.filterIndexed { index, _ -> index != group.captureState }
        .map { group.request.forState(it) to PlanVerifier(it.root) }
    private fun survival(plan: LocatePlan, future: List<Pair<org.autojs.autojs.core.accessibility.codegen.CodeGenRequest<SnapshotNode>, PlanVerifier<SnapshotNode>>>): Double =
        future.count { (request, verifier) -> verifier.verify(request, plan) is Verified }.toDouble() / future.size

    private fun traceDecisions(decisions: List<ConstructionDecision>) = JsonArray().apply {
        // Each observed vector is stored once in actions.csv; these keys resolve it exactly.
        decisions.forEach { decision -> add(JsonObject().apply {
            addProperty("stateKey", decision.stateKey); addProperty("actionKey", decision.actionKey)
            addProperty("score", decision.score)
        }) }
    }
    private fun sourceIdentity(): JsonObject {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val base = "app/src/test/java/org/autojs/autojs/core/accessibility/codegen/construction/"
        val paths = listOf("ConstructionGrammar.kt", "ConstructionFeatures.kt", "ConstructionSearch.kt",
            "ConstructionExperimentTask.kt", "ConstructionBottleneckFixtures.kt", "ConstructionPolicyModel.kt").map { base + it } + listOf(
            "app/src/main/java/org/autojs/autojs/core/accessibility/codegen/search/CandidateSearch.kt",
            "app/src/main/java/org/autojs/autojs/core/accessibility/codegen/verify/PlanVerifier.kt",
            "app/src/main/java/org/autojs/autojs/core/accessibility/codegen/features/CandidateFeatures.kt")
        return JsonObject().apply {
            paths.sorted().forEach { path -> addProperty(path, hash(File(root, path).readText(Charsets.UTF_8)
                .replace("\r\n", "\n").toByteArray(Charsets.UTF_8))) }
        }
    }

    private fun schema() = JsonObject().apply {
        addProperty("format", 1); addProperty("role", "construction-state-action")
        addProperty("grammarVersion", ConstructionFeatures.grammarVersion); addProperty("featureSchemaVersion", ConstructionFeatures.version)
        addProperty("namesHash", ConstructionFeatures.namesHash)
        add("names", JsonArray().apply { ConstructionFeatures.names.forEach(::add) })
    }
    private fun deterministicStats(stats: ConstructionStats) = gson.toJsonTree(stats).asJsonObject.apply {
        remove("elapsedNanos"); remove("verificationNanos"); remove("featureNanos"); remove("policyNanos")
    }
    private fun snapshotCache() = object : LinkedHashMap<File, SnapshotNode>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
    }
    private fun requireFreshOutput(output: File) {
        require(!output.exists()) { "Use a fresh construction experiment output directory" }
        require(output.mkdirs()) { "Cannot create experiment directory" }
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(65536)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun writeJson(file: File, value: JsonObject) = file.writeText(gson.toJson(value) + "\n", Charsets.UTF_8)
}
