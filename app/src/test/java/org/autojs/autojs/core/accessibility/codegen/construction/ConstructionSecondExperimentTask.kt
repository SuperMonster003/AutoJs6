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

/** Explicit offline second experiment. Freeze identities before development; select using only
 * development results before opening the fresh holdout. The first experiment remains immutable.
 * zh-CN: 第二轮离线实验. 开发前冻结输入, 仅凭训练/验证结果决定是否打开新留出集. */
class ConstructionSecondExperimentTask {
    @Test fun runOfflineSecondConstructionExperiment() {
        val mode = System.getenv("CODEGEN_CONSTRUCTION_R2_MODE")
        assumeTrue("Set CODEGEN_CONSTRUCTION_R2_MODE=freeze, develop or holdout", mode != null)
        val selectedMode = requireNotNull(mode)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        val input = directory("CODEGEN_CONSTRUCTION_DATASET")
        val export = directory("CODEGEN_CONSTRUCTION_EXPORT")
        val fit = directory("CODEGEN_CONSTRUCTION_POLICY_DIR")
        val protocol = directory("CODEGEN_CONSTRUCTION_R2_PROTOCOL")
        when (selectedMode) {
            "freeze" -> ConstructionSecondExperiment.freeze(input, export, fit, protocol)
            "develop", "holdout" -> ConstructionSecondExperiment.evaluate(input, export, fit, protocol,
                directory("CODEGEN_CONSTRUCTION_OUTPUT"), selectedMode,
                if (selectedMode == "holdout") directory("CODEGEN_CONSTRUCTION_R2_DEVELOPMENT") else null)
            else -> error("Unknown second construction experiment mode")
        }
    }
}

internal object ConstructionSecondExperiment {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private const val SEED = 20261007L
    private const val DEADLINE_MS = 1000L
    private const val SEARCH_MS = 800L
    private const val POLICY_SHA256 = "ecb04d2f7086b6c1065ff949b821be3d5e37a06ca2e5c5deca63b478acaffa62"
    private const val RANKER_SHA256 = "9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17"
    private val budget = ConstructionBudget(maxMillis = SEARCH_MS)
    private val methods = listOf("baseline", "legacy_heuristic", "legacy_model", "fair_heuristic", "fair_model")
    private val failedStops = setOf("FAILED", "HARD_DEADLINE", "DEADLINE", "POLICY_FAILURE")
    private val decisionMetrics = listOf("coverage", "bestSurvival", "top1Survival")

    private data class TaskRef(val file: File, val groupId: String, val family: String, val split: String, val snapshots: Set<File>)
    private data class Task(val group: TaskGroup, val split: String, val cohort: String)
    private data class Candidate(val value: Verified<SnapshotNode>, val survival: Double)
    private data class Run(
        val name: String, val candidates: List<Verified<SnapshotNode>>, val order: List<String>, val ruleKey: String?,
        val elapsedMillis: Double, val searchMillis: Double, val attemptedCount: Int?, val stopReason: String,
        val construction: ConstructionResult<SnapshotNode>?, val error: String? = null,
    )

    fun freeze(input: File, export: File, fit: File, output: File) {
        require(input.isDirectory && export.isDirectory && fit.isDirectory)
        val manifest = readJson(File(export, "export-manifest.json"))
        val binding = originalBinding(input, export, fit, manifest)
        val inputs = manifest.getAsJsonObject("inputs").deepCopy()
        fun record(file: File) {
            val path = relativeInside(input, file)
            val digest = hash(file)
            if (inputs.has(path)) require(inputs[path].asString == digest) { "Frozen input changed: $path" }
            else inputs.addProperty(path, digest)
        }
        val assignment = readJson(File(input, "split-map.json")).getAsJsonObject("assignment")
            .entrySet().associate { it.key to it.value.asString }
        val refs = File(input, "groups").walkTopDown().filter { it.isFile && it.name.endsWith(".cgtask.json") }
            .map { file ->
                record(file)
                val json = readJson(file)
                require(json["source"].asString == "fixture")
                val id = json["groupId"].asString
                val taskDirectory = requireNotNull(file.parentFile)
                val snapshots = json.getAsJsonArray("states").map { state ->
                    val relative = state.asJsonObject["snapshot"].asString
                    require(!relative.startsWith('/') && '\\' !in relative && ':' !in relative)
                    require(relative.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
                    File(taskDirectory, relative).canonicalFile.also { relativeInside(taskDirectory, it) }
                }.toSet()
                TaskRef(file.canonicalFile, id, json["screenFamily"].asString, assignment.getValue(id), snapshots)
            }.toList()
        require(refs.map { it.groupId }.toSet() == assignment.keys && refs.map { it.groupId }.distinct().size == refs.size)
        require(refs.all { it.split in setOf("train", "valid", "test") })
        val byId = refs.associateBy { it.groupId }
        val original = manifest.getAsJsonArray("groups").map { it.asJsonObject }
        require(original.size == 224 && original.map { it["groupId"].asString }.distinct().size == 224)
        original.forEach { metadata ->
            val ref = byId.getValue(metadata["groupId"].asString)
            require(ref.split == metadata["split"].asString && ref.family == metadata["screenFamily"].asString)
            require(relativeInside(input, ref.file) == metadata["task"].asString)
        }
        val development = original.filter { it["split"].asString in setOf("train", "valid") }
            .map { byId.getValue(it["groupId"].asString) }.sortedBy { it.groupId }
        require(development.count { it.split == "train" } == 168 && development.count { it.split == "valid" } == 28)
        // Seven requests of a seed share layout states. Exclude whole snapshot clusters, not just IDs.
        val originalSnapshots = original.flatMap { byId.getValue(it["groupId"].asString).snapshots }.toSet()
        val originalIds = original.map { it["groupId"].asString }.toSet()
        val unseen = refs.filter { it.split == "test" && it.groupId !in originalIds && it.snapshots.none(originalSnapshots::contains) }
            .sortedWith(compareBy<TaskRef> { hash("construction-r2-unused-clusters-$SEED:${it.groupId}".toByteArray(Charsets.UTF_8)) }.thenBy { it.groupId })
            .take(28).sortedBy { it.groupId }
        require(unseen.size == 28) { "Need 28 construction-unseen tasks with disjoint snapshot clusters" }
        val snapshots = snapshotCache()
        fun capturedMetadata(ref: TaskRef, cohort: String): JsonObject {
            val group = TaskGroup.load(ref.file) { file -> record(file); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
            group.requireLabelable()
            require(group.groupId == ref.groupId && group.screenFamily == ref.family)
            return metadata(group, ref.split, cohort).apply { addProperty("task", relativeInside(input, ref.file)) }
        }
        val developmentGroups = JsonArray().apply { development.forEach { add(capturedMetadata(it, "captured-development")) } }
        val capturedHoldout = JsonArray().apply { unseen.forEach { add(capturedMetadata(it, "captured-construction-unseen")) } }
        val syntheticHoldout = JsonArray().apply { ConstructionGeneralizationFixtures.groups().forEach {
            it.requireLabelable(); add(metadata(it, "test", "synthetic-holdout"))
        } }
        val regression = JsonArray().apply { ConstructionBottleneckFixtures.groups().forEach {
            it.requireLabelable(); add(metadata(it, "regression", "seen-synthetic-regression"))
        } }
        require(syntheticHoldout.size() == 24 && regression.size() == 24)
        val allIds = listOf(developmentGroups, capturedHoldout, syntheticHoldout, regression).flatMap { groups -> groups.map { it.asJsonObject["groupId"].asString } }
        require(allIds.distinct().size == allIds.size)
        val protocol = JsonObject().apply {
            addProperty("format", 1); addProperty("role", "construction-second-experiment")
            addProperty("seed", SEED); addProperty("datasetId", manifest["datasetId"].asString)
            addProperty("policy", "Frozen first-experiment 59-tree construction policy; no retraining")
            addProperty("fixedRanker", "P3 tiny v1 unchanged weights for all five constructors")
            addProperty("policySha256", POLICY_SHA256); addProperty("fixedRankerSha256", RANKER_SHA256)
            add("originalBinding", binding); add("sourceSha256", sourceIdentity()); add("inputs", inputs)
            add("methods", gson.toJsonTree(methods)); add("constructionBudget", gson.toJsonTree(budget))
            add("baselineBudget", gson.toJsonTree(SearchBudget())); addProperty("hardDeadlineMillis", DEADLINE_MS)
            addProperty("baselineTiming", "Original counter-limited CandidateSearch, with the same 1000 ms whole-run cancellation as experiment one")
            addProperty("constructorTiming", "800 ms search allocation including verifier construction; ranking/rendering share the 1000 ms whole-run deadline")
            addProperty("sampling", "sha256:construction-r2-unused-clusters-$SEED:groupId; first 28 after excluding every original export snapshot cluster")
            addProperty("capturedHoldoutLimitation", "Unseen by construction experiment one; same held-out list family. Prior P3 ranking evaluation used this dataset. Not new applications or independent app generalization.")
            addProperty("syntheticHoldoutLimitation", "New authored synthetic scenarios; neither captured real applications nor a population sample")
            addProperty("regressionLimitation", "All 24 first-experiment synthetic cases were already inspected; regression only")
            add("developmentGroups", developmentGroups); add("capturedHoldoutGroups", capturedHoldout)
            add("syntheticHoldoutGroups", syntheticHoldout); add("regressionGroups", regression)
            add("selectionCriteria", JsonObject().apply {
                addProperty("chosenMethod", "fair_model"); addProperty("referenceMethod", "legacy_model")
                add("metrics", gson.toJsonTree(decisionMetrics))
                addProperty("validNoMeanRegression", true); addProperty("trainOrValidStrictGain", true)
                addProperty("allMethodsNoFailureOrDeadline", true)
                add("failedStops", gson.toJsonTree(failedStops.sorted()))
                addProperty("scope", "Development gate to authorize one holdout assessment; not a production quality gate")
            })
        }
        requireFreshOutput(output)
        writeJson(File(output, "protocol.json"), protocol)
        println("Frozen second construction protocol: ${hash(File(output, "protocol.json"))}; development=196, captured construction-unseen=28, synthetic holdout=24, seen regression=24")
    }

    fun evaluate(input: File, export: File, fit: File, protocolDirectory: File, output: File, mode: String, developmentDirectory: File?) {
        require(mode == "develop" || mode == "holdout")
        val protocolFile = File(protocolDirectory, "protocol.json")
        val protocolSha256 = hash(protocolFile)
        val protocol = readJson(protocolFile)
        validateProtocol(input, export, fit, protocol)
        if (mode == "holdout") validateSelection(protocolFile, protocol, requireNotNull(developmentDirectory))
        val selectionSha256 = if (mode == "holdout") hash(File(protocolDirectory, "selection.json")) else null
        val loadStarted = System.nanoTime()
        val model = ConstructionPolicyModel.read(File(fit, "policy.json"))
        val loadMillis = (System.nanoTime() - loadStarted) / 1e6
        require(model.model.treeCount == 59)
        val fixedBytes = fixedRankerBytes()
        val ranker = (ModelPackageReader().read(fixedBytes.inputStream()) as ModelPackage).model
        val snapshots = snapshotCache()
        fun loadCaptured(item: JsonObject): Task {
            val taskFile = safeFile(input, item["task"].asString)
            require(hash(taskFile) == protocol.getAsJsonObject("inputs")[relativeInside(input, taskFile)].asString)
            val group = TaskGroup.load(taskFile) { file ->
                require(hash(file) == protocol.getAsJsonObject("inputs")[relativeInside(input, file)].asString)
                snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) }
            }
            require(metadata(group, item["split"].asString, item["cohort"].asString) == item.deepCopy().apply { remove("task") })
            return Task(group, item["split"].asString, item["cohort"].asString)
        }
        val developmentMetadata = protocol.getAsJsonArray("developmentGroups").map { it.asJsonObject }
        val warm = developmentMetadata.filter { it["split"].asString == "train" }.take(3).map(::loadCaptured)
        // This branch is deliberate: development never constructs a held-out TaskGroup or tree.
        val tasks = if (mode == "develop") developmentMetadata.map(::loadCaptured) else {
            fun synthetic(name: String, groups: List<TaskGroup>): List<Task> {
                val frozen = protocol.getAsJsonArray(name).map { it.asJsonObject }.associateBy { it["groupId"].asString }
                require(frozen.size == groups.size && groups.map { it.groupId }.toSet() == frozen.keys)
                return groups.map { group ->
                    val item = frozen.getValue(group.groupId)
                    require(metadata(group, item["split"].asString, item["cohort"].asString) == item) { "Frozen synthetic input changed" }
                    Task(group, item["split"].asString, item["cohort"].asString)
                }
            }
            protocol.getAsJsonArray("capturedHoldoutGroups").map { loadCaptured(it.asJsonObject) } +
                synthetic("syntheticHoldoutGroups", ConstructionGeneralizationFixtures.groups()) +
                synthetic("regressionGroups", ConstructionBottleneckFixtures.groups())
        }
        requireFreshOutput(output)
        val timer = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "construction-second-deadline").apply { isDaemon = true } }
        val rows = ArrayList<JsonObject>()
        try {
            warm.forEach { task -> methods.forEach { run(task.group, it, model, ranker, timer) } }
            File(output, "results.jsonl").bufferedWriter(Charsets.UTF_8).use { sink ->
                tasks.forEachIndexed { index, task ->
                    val group = task.group
                    val rotated = methods.indices.map { methods[(it + index) % methods.size] }
                    val runs = rotated.map { run(group, it, model, ranker, timer) }.associateBy { it.name }
                    val future = future(group)
                    val values = HashMap<String, Double>()
                    fun quality(candidate: Verified<SnapshotNode>): Double = values.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) { survival(candidate.plan, future) }
                    val candidates = runs.mapValues { (_, run) -> run.candidates.associate { ConstructionSearch.canonicalKey(it.plan) to Candidate(it, quality(it)) } }
                    val unionBest = candidates.values.flatMap { it.values }.maxOfOrNull { it.survival } ?: 0.0
                    val baselineKeys = candidates.getValue("baseline").keys
                    val baselineBest = candidates.getValue("baseline").values.maxOfOrNull { it.survival } ?: 0.0
                    val baselineRule = runs.getValue("baseline").ruleKey?.let { candidates.getValue("baseline")[it]?.survival } ?: 0.0
                    for (kind in methods) {
                        val run = runs.getValue(kind)
                        val own = candidates.getValue(kind)
                        val ordered = run.order.mapNotNull(own::get)
                        val top1 = ordered.firstOrNull()?.survival ?: 0.0
                        val top3 = ordered.take(3).maxOfOrNull { it.survival } ?: 0.0
                        val best = own.values.maxOfOrNull { it.survival } ?: 0.0
                        val row = JsonObject().apply {
                            addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily)
                            addProperty("source", group.source); addProperty("split", task.split); addProperty("cohort", task.cohort)
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
                        sink.write(gson.toJson(row)); sink.newLine()
                        rows += row.deepCopy().apply { remove("candidatesDetail") }
                        run.construction?.completions?.firstOrNull { it.simplified != null }?.let { completion ->
                            writeJson(File(output, "trace-${hash((group.groupId + kind).toByteArray(Charsets.UTF_8)).take(16)}.json"), JsonObject().apply {
                                addProperty("groupId", group.groupId); addProperty("method", kind)
                                addProperty("planKey", completion.simplified!!.plan.planKey)
                                add("decisions", gson.toJsonTree(completion.decisions))
                            })
                        }
                    }
                    if (index % 14 == 0 || index == tasks.lastIndex) println("Construction second $mode ${index + 1}/${tasks.size}")
                }
            }
        } finally { timer.shutdownNow() }
        // Catch accidental source/input edits during a long evaluation before publishing its metrics.
        validateProtocol(input, export, fit, protocol)
        require(hash(protocolFile) == protocolSha256) { "Protocol changed during evaluation" }
        if (selectionSha256 != null) require(hash(File(protocolDirectory, "selection.json")) == selectionSha256) { "Selection changed during evaluation" }
        writeJson(File(output, "metrics.json"), JsonObject().apply {
            addProperty("format", 1); addProperty("phase", mode); addProperty("datasetId", protocol["datasetId"].asString)
            addProperty("protocolSha256", protocolSha256); add("sourceSha256", protocol.getAsJsonObject("sourceSha256"))
            addProperty("policySha256", POLICY_SHA256); addProperty("fixedRankerSha256", RANKER_SHA256)
            addProperty("hardDeadlineMillis", DEADLINE_MS); add("constructionBudget", gson.toJsonTree(budget))
            addProperty("realApplicationGroups", 0); addProperty("policyLoadMillis", loadMillis)
            addProperty("policyStorageBytes", model.model.storageBytes); addProperty("policyTrees", model.model.treeCount)
            addProperty("policyFileBytes", File(fit, "policy.json").length()); addProperty("groups", tasks.size)
            add("summary", aggregate(rows)); add("pairwise", pairwise(rows))
            addProperty("resultsSha256", hash(File(output, "results.jsonl")))
            if (selectionSha256 != null) addProperty("selectionSha256", selectionSha256)
        })
    }

    private fun originalBinding(input: File, export: File, fit: File, manifest: JsonObject): JsonObject {
        checkHashes(input, manifest.getAsJsonObject("inputs"))
        val exportHashes = JsonObject().apply {
            for (name in listOf("export-manifest.json", "features.json", "actions.csv", "teacher-traces.jsonl")) addProperty(name, hash(File(export, name)))
        }
        for ((name, field) in listOf("features.json" to "featuresSha256", "actions.csv" to "actionsSha256", "teacher-traces.jsonl" to "tracesSha256")) {
            require(manifest[field].asString == exportHashes[name].asString) { "Original export artifact changed: $name" }
        }
        val training = readJson(File(fit, "train-log.json"))
        training.getAsJsonObject("inputSha256").entrySet().forEach { (name, digest) -> require(exportHashes[name].asString == digest.asString) }
        require(training["modelSha256"].asString == POLICY_SHA256 && hash(File(fit, "policy.json")) == POLICY_SHA256)
        require(training["treeCount"].asInt == 59 && training.getAsJsonArray("splitsUsed").map { it.asString }.toSet() == setOf("train", "valid"))
        val fitHashes = JsonObject().apply {
            addProperty("train-log.json", hash(File(fit, "train-log.json")))
            training.getAsJsonObject("artifacts").entrySet().sortedBy { it.key }.forEach { (name, artifact) ->
                val file = safeFile(fit, name)
                val digest = hash(file)
                require(digest == artifact.asJsonObject["sha256"].asString) { "Original fit artifact changed: $name" }
                addProperty(name, digest)
            }
        }
        require(hash(fixedRankerBytes()) == RANKER_SHA256)
        return JsonObject().apply { add("exportSha256", exportHashes); add("fitSha256", fitHashes) }
    }

    private fun validateProtocol(input: File, export: File, fit: File, protocol: JsonObject) {
        require(protocol["format"].asInt == 1 && protocol["role"].asString == "construction-second-experiment")
        require(protocol["seed"].asLong == SEED && protocol["hardDeadlineMillis"].asLong == DEADLINE_MS)
        require(protocol.getAsJsonObject("constructionBudget") == gson.toJsonTree(budget))
        require(protocol.getAsJsonArray("methods").map { it.asString } == methods)
        require(protocol.getAsJsonObject("sourceSha256") == sourceIdentity()) { "Source changed after freeze; use a new protocol and development run" }
        require(protocol["policySha256"].asString == POLICY_SHA256 && protocol["fixedRankerSha256"].asString == RANKER_SHA256)
        val binding = originalBinding(input, export, fit, readJson(File(export, "export-manifest.json")))
        require(protocol.getAsJsonObject("originalBinding") == binding) { "Frozen original export/fit binding changed" }
        checkHashes(input, protocol.getAsJsonObject("inputs"))
    }

    private fun validateSelection(protocolFile: File, protocol: JsonObject, development: File) {
        val selection = readJson(File(protocolFile.parentFile, "selection.json"))
        require(selection["format"].asInt == 1 && selection["chosenMethod"].asString == "fair_model" && selection["passed"].asBoolean)
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
        val expected = protocol.getAsJsonArray("developmentGroups").map { it.asJsonObject }
            .associate { it["groupId"].asString to it["split"].asString }
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
        require(decisionMetrics.all { mean("valid", "fair_model", it) >= mean("valid", "legacy_model", it) }) { "Validation quality regressed" }
        require(listOf("train", "valid").any { split -> decisionMetrics.any { mean(split, "fair_model", it) > mean(split, "legacy_model", it) } }) { "No strict development gain" }
    }

    private fun run(group: TaskGroup, name: String, policy: ConstructionPolicy, rankingModel: ObliviousTreeModel, timer: ScheduledExecutorService): Run {
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
                val frontierMode = if (name.startsWith("fair_")) ConstructionFrontierMode.ANCHOR_PHASE_FAIR else ConstructionFrontierMode.FAMILY_SCORE
                construction = ConstructionSearch(capture.root, cancellation, budget.copy(maxMillis = remaining),
                    if (name.endsWith("_model")) policy else ConstructionHeuristicPolicy, verifier, frontierMode = frontierMode).search(request)
                candidates = construction.candidates; attempted = construction.stats.attemptedCount; stop = construction.stopReason.name
            }
            searchMillis = (System.nanoTime() - started) / 1e6
            cancellation.throwIfCancelled()
            val unique = candidates.associateBy { ConstructionSearch.canonicalKey(it.plan) }.values.toList()
            val ruleKey = RuleRanker().rank(request, unique).firstOrNull()?.plan?.let { ConstructionSearch.canonicalKey(it) }
            cancellation.throwIfCancelled()
            val remaining = (DEADLINE_MS - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
            val order = ModelRanker(rankingModel, budgetMs = remaining, featureSchemaVersion = 1).rank(request, unique).map { ConstructionSearch.canonicalKey(it.plan) }
            unique.associateBy { ConstructionSearch.canonicalKey(it.plan) }.let { byKey ->
                order.take(10).forEach { key -> ClassicRenderer().render(request, byKey.getValue(key).plan) }
            }
            cancellation.throwIfCancelled()
            val elapsed = (System.nanoTime() - started) / 1e6
            if (elapsed > DEADLINE_MS) Run(name, emptyList(), emptyList(), null, elapsed, searchMillis, attempted, "HARD_DEADLINE", construction)
            else Run(name, unique, order, ruleKey, elapsed, searchMillis, attempted, stop, construction)
        } catch (cancelled: CodeGenCancelledException) {
            if (!cancellation.isCancelled) throw cancelled
            val elapsed = (System.nanoTime() - started) / 1e6
            Run(name, emptyList(), emptyList(), null, elapsed, if (searchMillis == 0.0) elapsed else searchMillis, attempted, "HARD_DEADLINE", construction)
        } catch (failure: Exception) {
            Run(name, emptyList(), emptyList(), null, (System.nanoTime() - started) / 1e6, searchMillis, attempted, "FAILED", construction,
                failure.javaClass.simpleName + ": " + failure.message)
        } finally { deadline.cancel(false) }
    }

    private fun aggregate(rows: List<JsonObject>): JsonArray = JsonArray().apply {
        rows.groupBy { listOf(it["cohort"].asString, it["split"].asString, it["method"].asString) }
            .toSortedMap(compareBy<List<String>> { it.joinToString("/") }).forEach { (key, members) -> add(JsonObject().apply {
                addProperty("cohort", key[0]); addProperty("split", key[1]); addProperty("method", key[2]); addProperty("groups", members.size)
                for (metric in listOf("candidates", "coverage", "bestSurvival", "top1Survival", "top3BestSurvival", "commonRegret", "commonTop3Recall", "baselineRuleTop1Survival")) {
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
                for (reference in listOf("legacy_model", "fair_heuristic", "baseline")) add(JsonObject().apply {
                    addProperty("cohort", cohort.first); addProperty("split", cohort.second)
                    addProperty("method", "fair_model"); addProperty("reference", reference); addProperty("groups", groups.size)
                    for (metric in listOf("coverage", "bestSurvival", "top1Survival", "top3BestSurvival")) {
                        val differences = groups.values.map { records ->
                            records.single { it["method"].asString == "fair_model" }[metric].asDouble -
                                records.single { it["method"].asString == reference }[metric].asDouble
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
            // Snapshot excludes wrapper parent pointers; the full tree and all exposed values are bound.
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
            "ConstructionSecondExperimentTask.kt", "ConstructionGeneralizationFixtures.kt", "ConstructionBottleneckFixtures.kt").map { base + it } +
            File(root, "app/src/main/java/org/autojs/autojs/core/accessibility/codegen").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.parentFile?.name != "store" }
                .map { it.relativeTo(root).invariantSeparatorsPath }.toList() + listOf(
                "app/src/main/java/org/autojs/autojs/core/accessibility/NodeInfoSnapshot.kt",
                "app/src/main/java/org/autojs/autojs/core/automator/UiNode.kt",
                "tools/codegen-model/construction/select_frontier.py")
        return JsonObject().apply { paths.distinct().sorted().forEach { path ->
            addProperty(path, hash(File(root, path).readText(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)))
        } }
    }

    private fun fixedRankerBytes() = requireNotNull(javaClass.getResourceAsStream("/codegen/models/tiny-test-v1.ajcgm")).use { it.readBytes() }
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
        require(!output.exists()) { "Use a fresh second construction experiment output directory" }
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
