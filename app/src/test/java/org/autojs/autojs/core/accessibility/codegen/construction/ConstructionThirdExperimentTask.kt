package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.FeatureExport
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
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min

/**
 * Explicit offline third experiment: a second teacher export with both the mean and the best
 * observed continuation label, per-scope sampling quotas and both frontier controllers, then a
 * frozen comparison of the policies trained on each label against the frozen second-round
 * policy, the fixed heuristic and the original search. Freeze identities before development;
 * select using only development results before opening the fresh holdout. Earlier experiments
 * remain immutable; their inspected cohorts are reported here as regression only.
 * zh-CN: 第三轮离线实验: 导出含均值与最佳后续两种标签的教师数据, 冻结协议后仅凭训练/验证结果
 * 决定是否打开新留出集. 前两轮已查看的任务只作回归.
 */
class ConstructionThirdExperimentTask {
    @Test fun runOfflineThirdConstructionExperiment() {
        val mode = System.getenv("CODEGEN_CONSTRUCTION_R3_MODE")
        assumeTrue("Set CODEGEN_CONSTRUCTION_R3_MODE=export, freeze, develop or holdout", mode != null)
        val selectedMode = requireNotNull(mode)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        val input = directory("CODEGEN_CONSTRUCTION_DATASET")
        when (selectedMode) {
            "export" -> ConstructionThirdExperiment.export(input, directory("CODEGEN_CONSTRUCTION_OUTPUT"),
                System.getenv("CODEGEN_CONSTRUCTION_R3_SINGLE")?.toInt() ?: 28,
                System.getenv("CODEGEN_CONSTRUCTION_R3_COLLECTION")?.toInt() ?: 28,
                System.getenv("CODEGEN_CONSTRUCTION_TEACHER_RUNS")?.toInt() ?: 4)
            "freeze" -> ConstructionThirdExperiment.freeze(
                input, directory("CODEGEN_CONSTRUCTION_R3_EXPORT"),
                linkedMapOf("fair_model_mean" to directory("CODEGEN_CONSTRUCTION_R3_FIT_MEAN"), "fair_model_best" to directory("CODEGEN_CONSTRUCTION_R3_FIT_BEST")),
                directory("CODEGEN_CONSTRUCTION_POLICY_DIR"), directory("CODEGEN_CONSTRUCTION_EXPORT"),
                directory("CODEGEN_CONSTRUCTION_R2_PROTOCOL"), directory("CODEGEN_CONSTRUCTION_R3_PROTOCOL"))
            "develop", "holdout" -> ConstructionThirdExperiment.evaluate(
                input, directory("CODEGEN_CONSTRUCTION_R3_EXPORT"),
                linkedMapOf("fair_model_mean" to directory("CODEGEN_CONSTRUCTION_R3_FIT_MEAN"), "fair_model_best" to directory("CODEGEN_CONSTRUCTION_R3_FIT_BEST")),
                directory("CODEGEN_CONSTRUCTION_POLICY_DIR"), directory("CODEGEN_CONSTRUCTION_R3_PROTOCOL"),
                directory("CODEGEN_CONSTRUCTION_OUTPUT"), selectedMode,
                if (selectedMode == "holdout") directory("CODEGEN_CONSTRUCTION_R3_DEVELOPMENT") else null)
            else -> error("Unknown third construction experiment mode")
        }
    }
}

internal object ConstructionThirdExperiment {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private const val SEED = 20261008L
    private const val DEADLINE_MS = 1000L
    private const val SEARCH_MS = 800L
    private const val EXPORT_FORMAT = 2
    private const val R2_POLICY_SHA256 = "ecb04d2f7086b6c1065ff949b821be3d5e37a06ca2e5c5deca63b478acaffa62"
    private const val RANKER_SHA256 = "9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17"
    private val budget = ConstructionBudget(maxMillis = SEARCH_MS)
    private val teacherBudget = ConstructionBudget(maxFrontier = 128, maxExpandedStates = 4000, maxActionScores = 24000, maxMillis = 30_000)
    private val candidateMethods = listOf("fair_model_mean", "fair_model_best")
    private val methods = listOf("baseline", "fair_heuristic", "fair_model_r2") + candidateMethods
    private val referenceMethod = "fair_model_r2"
    private val failedStops = setOf("FAILED", "HARD_DEADLINE", "DEADLINE", "POLICY_FAILURE")
    private val decisionMetrics = listOf("coverage", "bestSurvival", "top1Survival")
    /** Preregistered choice among the two new policies: lexicographic validation quality, ties prefer the best-continuation label. */
    private val selectionOrder = listOf("top1Survival", "bestSurvival", "coverage")
    private val labelSemantics = linkedMapOf(
        "label" to "mean survival of completed observed rollouts through the state/action; unknown edges omitted",
        "labelBest" to "maximum survival among completed observed rollouts through the state/action; an observed lower bound of the best continuation",
    )

    private data class TaskRef(val file: File, val groupId: String, val family: String, val split: String, val collection: Boolean, val snapshots: Set<File>)
    private data class Task(val group: TaskGroup, val split: String, val cohort: String)
    private data class Candidate(val value: Verified<SnapshotNode>, val survival: Double)
    private class ObservedAction(val features: FloatArray) {
        var total = 0.0
        var count = 0
        var best = 0.0
        fun add(vector: FloatArray, value: Double) {
            check(features.contentEquals(vector)) { "The same state/action changed its features" }
            total += value; count++; best = maxOf(best, value)
        }
        /** Rounded sums can exceed the maximum by one unit in the last place; the mean never exceeds the best. */
        val mean: Double get() = min(total / count, best)
    }
    private data class Run(
        val name: String, val candidates: List<Verified<SnapshotNode>>, val order: List<String>, val ruleOrder: List<String>,
        val elapsedMillis: Double, val searchMillis: Double, val attemptedCount: Int?, val stopReason: String,
        val construction: ConstructionResult<SnapshotNode>?, val error: String? = null,
    )

    private fun teacherController(run: Int) = if (run % 2 == 0) ConstructionFrontierMode.FAMILY_SCORE else ConstructionFrontierMode.ANCHOR_PHASE_FAIR

    fun export(input: File, output: File, perFamilySingle: Int, perFamilyCollection: Int, teacherRuns: Int) {
        require(perFamilySingle in 1..288 && perFamilyCollection in 0..48 && teacherRuns in 2..8)
        require(input.isDirectory)
        requireFreshOutput(output)
        val inputs = sortedMapOf<String, String>()
        fun record(file: File) {
            val path = relativeInside(input, file)
            inputs.getOrPut(path) { hash(file) }
        }
        val splitFile = File(input, "split-map.json").also(::record)
        val originalManifest = File(input, "dataset-manifest.json").also(::record)
        val split = readJson(splitFile)
        val assignment = split.getAsJsonObject("assignment").entrySet().associate { it.key to it.value.asString }
        val refs = taskRefs(input, assignment, ::record)
        // Separate quotas per scope: collection intents were one seventh of the first export, yet
        // every fresh coverage loss of the second experiment was a collection request.
        // zh-CN: 按范围分别配额; 首轮集合任务仅占七分之一, 而第二轮全部新覆盖损失都是集合请求.
        val selected = refs.filter { it.split in setOf("train", "valid") }.groupBy { it.family }.toSortedMap().values.flatMap { family ->
            fun pick(collection: Boolean, quota: Int) = family.filter { it.collection == collection }
                .sortedWith(compareBy<TaskRef> { hash("construction-r3-$SEED:${it.groupId}".toByteArray(Charsets.UTF_8)) }.thenBy { it.groupId })
                .take(quota)
            pick(false, perFamilySingle) + pick(true, perFamilyCollection)
        }.sortedBy { it.groupId }
        require(selected.isNotEmpty() && selected.any { it.split == "train" } && selected.any { it.split == "valid" })
        writeJson(File(output, "features.json"), schema())
        val groupMetadata = JsonArray()
        var actionRows = 0L
        var unknownEdges = 0L
        var terminalCount = 0L
        var validTerminalCount = 0L
        val snapshots = snapshotCache()
        val columns = listOf("split", "groupId", "stateKey", "actionKey", "label", "labelBest", "observations") + ConstructionFeatures.names
        File(output, "actions.csv").bufferedWriter(Charsets.UTF_8).use { csv ->
            csv.write(FeatureExport.csvRow(columns))
            File(output, "teacher-traces.jsonl").bufferedWriter(Charsets.UTF_8).use { traces ->
                selected.forEachIndexed { position, ref ->
                    val group = TaskGroup.load(ref.file) { file -> record(file); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
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
                        // Runs 0/1 are the plain heuristic under each controller; later runs add seeded
                        // exploration noise. Labels are computed afterward from future states only.
                        // zh-CN: 前两次为两种控制器下的纯启发式, 其后加入带种子的探索噪声; 标签事后由未来状态计算.
                        val policy = if (runIndex < 2) ConstructionHeuristicPolicy else ConstructionPolicy { vector ->
                            ConstructionHeuristicPolicy.score(vector) + (random.nextDouble() - .5) * 2.0
                        }
                        val result = ConstructionSearch(capture.root, budget = teacherBudget, policy = policy,
                            frontierMode = teacherController(runIndex)).search(request)
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
                                addProperty("run", runIndex); addProperty("controller", teacherController(runIndex).name)
                                addProperty("validAtCapture", verified != null); addProperty("observedReturn", value)
                                addProperty("rawPlanKey", completion.plan.planKey)
                                addProperty("canonicalKey", verified?.plan?.let { ConstructionSearch.canonicalKey(it) })
                                add("decisions", JsonArray().apply { completion.decisions.forEach { decision -> add(JsonObject().apply {
                                    addProperty("stateKey", decision.stateKey); addProperty("actionKey", decision.actionKey); addProperty("score", decision.score)
                                }) } })
                            }
                            traces.write(gson.toJson(trace)); traces.newLine()
                        }
                        runMetadata.add(JsonObject().apply {
                            addProperty("run", runIndex); addProperty("controller", teacherController(runIndex).name)
                            addProperty("exploration", runIndex >= 2); addProperty("stopReason", result.stopReason.name)
                            add("counts", deterministicStats(result.stats))
                        })
                    }
                    observed.forEach { (edge, value) ->
                        csv.write(FeatureExport.csvRow(listOf(ref.split, group.groupId, edge.first, edge.second,
                            value.mean.toString(), value.best.toString(), value.count.toString()) + value.features.map(Float::toString)))
                    }
                    groupMetadata.add(metadata(group, ref.split, "captured-development").apply {
                        addProperty("task", relativeInside(input, ref.file))
                        addProperty("rows", observed.size); addProperty("scoredEdges", scoredEdges.size)
                        addProperty("unknownEdges", scoredEdges.count { it !in observed })
                        addProperty("completedRollouts", groupTerminals); addProperty("validCompletedRollouts", groupValid)
                        add("runs", runMetadata)
                    })
                    actionRows += observed.size; unknownEdges += scoredEdges.count { it !in observed }
                    terminalCount += groupTerminals; validTerminalCount += groupValid
                    if (position % 14 == 0 || position == selected.lastIndex) {
                        println("Construction third export ${position + 1}/${selected.size}: rows=$actionRows, terminal=$terminalCount")
                    }
                }
            }
        }
        inputs.forEach { (path, digest) -> require(hash(File(input, path)) == digest) { "Dataset changed while exporting" } }
        val manifest = JsonObject().apply {
            addProperty("format", EXPORT_FORMAT); addProperty("datasetId", split["datasetId"].asString + "-construction-third")
            addProperty("sourceManifestSha256", hash(originalManifest))
            addProperty("featuresSha256", hash(File(output, "features.json")))
            addProperty("actionsSha256", hash(File(output, "actions.csv")))
            addProperty("tracesSha256", hash(File(output, "teacher-traces.jsonl")))
            addProperty("sampling", "sha256:construction-r3-$SEED:groupId per original family and scope; first $perFamilySingle single and $perFamilyCollection collection; train/valid families only")
            addProperty("perFamilySingle", perFamilySingle); addProperty("perFamilyCollection", perFamilyCollection)
            addProperty("teacherRuns", teacherRuns)
            add("teacherControllers", JsonArray().apply { repeat(teacherRuns) { add(teacherController(it).name + if (it >= 2) "+noise" else "") } })
            addProperty("traceFeatures", "Resolve (groupId,stateKey,actionKey) in actions.csv")
            add("labelSemantics", gson.toJsonTree(labelSemantics))
            addProperty("rows", actionRows); addProperty("observedEdges", actionRows); addProperty("unknownEdges", unknownEdges)
            addProperty("completedRollouts", terminalCount); addProperty("validCompletedRollouts", validTerminalCount)
            add("teacherBudget", gson.toJsonTree(teacherBudget)); add("evaluationBudget", gson.toJsonTree(budget))
            addProperty("evaluationHardDeadlineMillis", DEADLINE_MS)
            add("inputs", gson.toJsonTree(inputs)); add("sourceSha256", sourceIdentity()); add("groups", groupMetadata)
        }
        writeJson(File(output, "export-manifest.json"), manifest) // last: incomplete output cannot be trained
    }

    fun freeze(input: File, export: File, fits: Map<String, File>, r2Fit: File, r1Export: File, r2Protocol: File, output: File) {
        require(input.isDirectory && export.isDirectory && r2Fit.isDirectory && r1Export.isDirectory && r2Protocol.isDirectory)
        require(fits.keys == candidateMethods.toSet())
        val manifest = readJson(File(export, "export-manifest.json"))
        val binding = binding(input, export, fits, r2Fit, manifest)
        val inputs = manifest.getAsJsonObject("inputs").deepCopy()
        fun record(file: File) {
            val path = relativeInside(input, file)
            val digest = hash(file)
            if (inputs.has(path)) require(inputs[path].asString == digest) { "Frozen input changed: $path" }
            else inputs.addProperty(path, digest)
        }
        val assignment = readJson(File(input, "split-map.json")).getAsJsonObject("assignment").entrySet().associate { it.key to it.value.asString }
        val refs = taskRefs(input, assignment, ::record)
        val byId = refs.associateBy { it.groupId }
        val exported = manifest.getAsJsonArray("groups").map { it.asJsonObject }
        require(exported.map { it["groupId"].asString }.distinct().size == exported.size)
        exported.forEach { item ->
            val ref = byId.getValue(item["groupId"].asString)
            require(ref.split == item["split"].asString && ref.family == item["screenFamily"].asString && ref.split in setOf("train", "valid"))
            require(relativeInside(input, ref.file) == item["task"].asString)
        }
        val development = exported.map { byId.getValue(it["groupId"].asString) }.sortedBy { it.groupId }
        // Clusters inspected by the first export (its test slice) and the second captured holdout are
        // excluded as whole snapshot clusters, not only by group ID.
        // zh-CN: 排除首轮导出与第二轮留出已查看的整个快照簇, 不只按任务 ID 排除.
        val r1Manifest = readJson(File(r1Export, "export-manifest.json"))
        val r2ProtocolJson = readJson(File(r2Protocol, "protocol.json"))
        require(r2ProtocolJson["role"].asString == "construction-second-experiment")
        val r2Captured = r2ProtocolJson.getAsJsonArray("capturedHoldoutGroups").map { it.asJsonObject }
        val inspectedIds = (r1Manifest.getAsJsonArray("groups").map { it.asJsonObject["groupId"].asString } +
            r2Captured.map { it["groupId"].asString } + exported.map { it["groupId"].asString }).toSet()
        val inspectedSnapshots = inspectedIds.flatMap { byId.getValue(it).snapshots }.toSet()
        val unseen = refs.filter { it.split == "test" && it.groupId !in inspectedIds && it.snapshots.none(inspectedSnapshots::contains) }
            .sortedWith(compareBy<TaskRef> { hash("construction-r3-unused-clusters-$SEED:${it.groupId}".toByteArray(Charsets.UTF_8)) }.thenBy { it.groupId })
            .take(28).sortedBy { it.groupId }
        require(unseen.size == 28) { "Need 28 construction-unseen tasks with disjoint snapshot clusters" }
        val snapshots = snapshotCache()
        fun capturedMetadata(ref: TaskRef, split: String, cohort: String): JsonObject {
            val group = TaskGroup.load(ref.file) { file -> record(file); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
            group.requireLabelable()
            require(group.groupId == ref.groupId && group.screenFamily == ref.family)
            return metadata(group, split, cohort).apply { addProperty("task", relativeInside(input, ref.file)) }
        }
        val developmentGroups = JsonArray().apply { development.forEach { add(capturedMetadata(it, it.split, "captured-development")) } }
        val capturedHoldout = JsonArray().apply { unseen.forEach { add(capturedMetadata(it, "test", "captured-construction-unseen")) } }
        val syntheticHoldout = JsonArray().apply { ConstructionThirdSyntheticFixtures.groups().forEach {
            it.requireLabelable(); add(metadata(it, "test", "synthetic-holdout"))
        } }
        val regression = JsonArray().apply {
            ConstructionBottleneckFixtures.groups().forEach { it.requireLabelable(); add(metadata(it, "regression", "seen-bottleneck-regression")) }
            ConstructionGeneralizationFixtures.groups().forEach { it.requireLabelable(); add(metadata(it, "regression", "seen-generalization-regression")) }
            r2Captured.forEach { item ->
                val ref = byId.getValue(item["groupId"].asString)
                val frozen = capturedMetadata(ref, "regression", "seen-captured-regression")
                require(frozen.getAsJsonArray("states").map { it.asJsonObject["snapshotContentSha256"].asString } ==
                    item.getAsJsonArray("states").map { it.asJsonObject["snapshotContentSha256"].asString }) { "Second-round captured holdout changed" }
                add(frozen)
            }
        }
        require(syntheticHoldout.size() == 24 && regression.size() == 24 + 24 + r2Captured.size)
        val allIds = listOf(developmentGroups, capturedHoldout, syntheticHoldout, regression).flatMap { groups -> groups.map { it.asJsonObject["groupId"].asString } }
        require(allIds.distinct().size == allIds.size)
        val protocol = JsonObject().apply {
            addProperty("format", 1); addProperty("role", "construction-third-experiment")
            addProperty("seed", SEED); addProperty("datasetId", manifest["datasetId"].asString)
            addProperty("policies", "Frozen second-round 59-tree policy versus policies fitted on the third export with the mean and the best observed continuation label")
            addProperty("fixedRanker", "P3 tiny v1 unchanged weights for all five methods")
            addProperty("referencePolicySha256", R2_POLICY_SHA256); addProperty("fixedRankerSha256", RANKER_SHA256)
            add("binding", binding); add("sourceSha256", sourceIdentity()); add("inputs", inputs)
            add("methods", gson.toJsonTree(methods)); add("candidateMethods", gson.toJsonTree(candidateMethods))
            addProperty("referenceMethod", referenceMethod)
            add("constructionBudget", gson.toJsonTree(budget)); add("baselineBudget", gson.toJsonTree(SearchBudget()))
            addProperty("hardDeadlineMillis", DEADLINE_MS)
            addProperty("baselineTiming", "Original counter-limited CandidateSearch, with the same 1000 ms whole-run cancellation as earlier experiments")
            addProperty("constructorTiming", "800 ms search allocation including verifier construction; ranking/rendering share the 1000 ms whole-run deadline")
            addProperty("controller", "ANCHOR_PHASE_FAIR for every constructor; FAMILY_SCORE variants were not better in the second experiment and are not repeated")
            addProperty("sampling", "sha256:construction-r3-unused-clusters-$SEED:groupId; first 28 after excluding every snapshot cluster of the first export, the second captured holdout and this export")
            addProperty("capturedHoldoutLimitation", "Unseen by every construction experiment; same held-out list family. Prior P3 ranking evaluation used this dataset. Not new applications or independent app generalization.")
            addProperty("syntheticHoldoutLimitation", "New authored synthetic scenarios; neither captured real applications nor a population sample")
            addProperty("regressionLimitation", "All regression cohorts were inspected by earlier experiments; regression only, never selection evidence")
            add("developmentGroups", developmentGroups); add("capturedHoldoutGroups", capturedHoldout)
            add("syntheticHoldoutGroups", syntheticHoldout); add("regressionGroups", regression)
            add("selectionCriteria", JsonObject().apply {
                add("candidateMethods", gson.toJsonTree(candidateMethods)); addProperty("referenceMethod", referenceMethod)
                add("metrics", gson.toJsonTree(decisionMetrics)); add("selectionOrder", gson.toJsonTree(selectionOrder))
                addProperty("selectionSplit", "valid"); addProperty("tieBreak", "fair_model_best")
                addProperty("validNoMeanRegressionAgainstReference", true); addProperty("trainOrValidStrictGainAgainstReference", true)
                addProperty("allMethodsNoFailureOrDeadline", true)
                add("failedStops", gson.toJsonTree(failedStops.sorted()))
                addProperty("scope", "Development gate to authorize one holdout assessment of the chosen policy; not a production quality gate")
            })
        }
        requireFreshOutput(output)
        writeJson(File(output, "protocol.json"), protocol)
        println("Frozen third construction protocol: ${hash(File(output, "protocol.json"))}; development=${development.size}, captured construction-unseen=28, synthetic holdout=24, regression=${regression.size()}")
    }

    fun evaluate(input: File, export: File, fits: Map<String, File>, r2Fit: File, protocolDirectory: File, output: File, mode: String, developmentDirectory: File?) {
        require(mode == "develop" || mode == "holdout")
        val protocolFile = File(protocolDirectory, "protocol.json")
        val protocolSha256 = hash(protocolFile)
        val protocol = readJson(protocolFile)
        validateProtocol(input, export, fits, r2Fit, protocol)
        val chosen = if (mode == "holdout") validateSelection(protocolFile, protocol, requireNotNull(developmentDirectory)) else null
        val selectionSha256 = if (mode == "holdout") hash(File(protocolDirectory, "selection.json")) else null
        val policies = LinkedHashMap<String, ConstructionPolicyModel>()
        val loadMillis = LinkedHashMap<String, Double>()
        for ((name, directory) in listOf(referenceMethod to r2Fit) + fits.entries.map { it.key to it.value }) {
            val started = System.nanoTime()
            policies[name] = ConstructionPolicyModel.read(File(directory, "policy.json"))
            loadMillis[name] = (System.nanoTime() - started) / 1e6
        }
        require(policies.getValue(referenceMethod).model.treeCount == 59)
        require(policies.getValue("fair_model_mean").scoreSemantics == ConstructionPolicyModel.SCORE_SEMANTICS)
        require(policies.getValue("fair_model_best").scoreSemantics == ConstructionPolicyModel.BEST_SCORE_SEMANTICS)
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
            val syntheticRegression = regression.filter { !it.has("task") }
            val capturedRegression = regression.filter { it.has("task") }
            require(syntheticRegression.size == 48 && capturedRegression.size + 48 == regression.size)
            protocol.getAsJsonArray("capturedHoldoutGroups").map { loadCaptured(it.asJsonObject) } +
                synthetic(ConstructionThirdSyntheticFixtures.groups(), protocol.getAsJsonArray("syntheticHoldoutGroups").map { it.asJsonObject }) +
                synthetic(ConstructionBottleneckFixtures.groups() + ConstructionGeneralizationFixtures.groups(), syntheticRegression) +
                capturedRegression.map(::loadCaptured)
        }
        require(tasks.map { it.group.groupId }.distinct().size == tasks.size)
        requireFreshOutput(output)
        val timer = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "construction-third-deadline").apply { isDaemon = true } }
        val rows = ArrayList<JsonObject>()
        try {
            warm.forEach { task -> methods.forEach { run(task.group, it, policies, ranker, timer) } }
            File(output, "results.jsonl").bufferedWriter(Charsets.UTF_8).use { sink ->
                tasks.forEachIndexed { index, task ->
                    val group = task.group
                    val rotated = methods.indices.map { methods[(it + index) % methods.size] }
                    val runs = rotated.map { run(group, it, policies, ranker, timer) }.associateBy { it.name }
                    val future = future(group)
                    val values = HashMap<String, Double>()
                    fun quality(candidate: Verified<SnapshotNode>): Double = values.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) { survival(candidate.plan, future) }
                    val candidates = runs.mapValues { (_, run) -> run.candidates.associate { ConstructionSearch.canonicalKey(it.plan) to Candidate(it, quality(it)) } }
                    val unionBest = candidates.values.flatMap { it.values }.maxOfOrNull { it.survival } ?: 0.0
                    val baselineKeys = candidates.getValue("baseline").keys
                    val baselineBest = candidates.getValue("baseline").values.maxOfOrNull { it.survival } ?: 0.0
                    val baselineRule = runs.getValue("baseline").ruleOrder.firstOrNull()?.let { candidates.getValue("baseline")[it]?.survival } ?: 0.0
                    for (kind in methods) {
                        val run = runs.getValue(kind)
                        val own = candidates.getValue(kind)
                        val ordered = run.order.mapNotNull(own::get)
                        val ruleOrdered = run.ruleOrder.mapNotNull(own::get)
                        val top1 = ordered.firstOrNull()?.survival ?: 0.0
                        val top3 = ordered.take(3).maxOfOrNull { it.survival } ?: 0.0
                        val best = own.values.maxOfOrNull { it.survival } ?: 0.0
                        val row = JsonObject().apply {
                            addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily)
                            addProperty("source", group.source); addProperty("split", task.split); addProperty("cohort", task.cohort)
                            addProperty("method", kind); addProperty("candidates", own.size); addProperty("coverage", if (own.isEmpty()) 0.0 else 1.0)
                            addProperty("bestSurvival", best); addProperty("top1Survival", top1); addProperty("top3BestSurvival", top3)
                            // The same candidates ordered by the deterministic RuleRanker: a second, model-free
                            // view of whether constructed candidates reach the recommendation.
                            // zh-CN: 同一候选按规则排序器排序, 作为不依赖终局模型的第二视角.
                            addProperty("ruleTop1Survival", ruleOrdered.firstOrNull()?.survival ?: 0.0)
                            addProperty("ruleTop3BestSurvival", ruleOrdered.take(3).maxOfOrNull { it.survival } ?: 0.0)
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
                                addProperty("topRank", run.order.indexOf(key)); addProperty("ruleRank", run.ruleOrder.indexOf(key))
                                addProperty("planKey", candidate.value.plan.planKey)
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
                    if (index % 14 == 0 || index == tasks.lastIndex) println("Construction third $mode ${index + 1}/${tasks.size}")
                }
            }
        } finally { timer.shutdownNow() }
        validateProtocol(input, export, fits, r2Fit, protocol)
        require(hash(protocolFile) == protocolSha256) { "Protocol changed during evaluation" }
        if (selectionSha256 != null) require(hash(File(protocolDirectory, "selection.json")) == selectionSha256) { "Selection changed during evaluation" }
        writeJson(File(output, "metrics.json"), JsonObject().apply {
            addProperty("format", 1); addProperty("phase", mode); addProperty("datasetId", protocol["datasetId"].asString)
            addProperty("protocolSha256", protocolSha256); add("sourceSha256", protocol.getAsJsonObject("sourceSha256"))
            add("policySha256", JsonObject().apply {
                addProperty(referenceMethod, R2_POLICY_SHA256)
                fits.forEach { (name, directory) -> addProperty(name, hash(File(directory, "policy.json"))) }
            })
            addProperty("fixedRankerSha256", RANKER_SHA256)
            addProperty("hardDeadlineMillis", DEADLINE_MS); add("constructionBudget", gson.toJsonTree(budget))
            addProperty("realApplicationGroups", 0)
            add("policies", JsonObject().apply { policies.forEach { (name, policy) -> add(name, JsonObject().apply {
                addProperty("scoreSemantics", policy.scoreSemantics); addProperty("trees", policy.model.treeCount)
                addProperty("storageBytes", policy.model.storageBytes); addProperty("loadMillis", loadMillis.getValue(name))
            }) } })
            addProperty("groups", tasks.size)
            add("summary", aggregate(rows)); add("pairwise", pairwise(rows))
            addProperty("resultsSha256", hash(File(output, "results.jsonl")))
            if (chosen != null) addProperty("chosenMethod", chosen)
            if (selectionSha256 != null) addProperty("selectionSha256", selectionSha256)
        })
    }

    private fun binding(input: File, export: File, fits: Map<String, File>, r2Fit: File, manifest: JsonObject): JsonObject {
        require(manifest["format"].asInt == EXPORT_FORMAT) { "The third experiment needs a format 2 construction export" }
        require(manifest.getAsJsonObject("labelSemantics") == gson.toJsonTree(labelSemantics)) { "Export label semantics differ" }
        checkHashes(input, manifest.getAsJsonObject("inputs"))
        val exportHashes = JsonObject().apply {
            for (name in listOf("export-manifest.json", "features.json", "actions.csv", "teacher-traces.jsonl")) addProperty(name, hash(File(export, name)))
        }
        for ((name, field) in listOf("features.json" to "featuresSha256", "actions.csv" to "actionsSha256", "teacher-traces.jsonl" to "tracesSha256")) {
            require(manifest[field].asString == exportHashes[name].asString) { "Third export artifact changed: $name" }
        }
        val fitHashes = JsonObject()
        for ((method, directory) in fits) {
            val training = readJson(File(directory, "train-log.json"))
            training.getAsJsonObject("inputSha256").entrySet().forEach { (name, digest) ->
                require(exportHashes[name].asString == digest.asString) { "$method was trained from a different export: $name" }
            }
            val label = if (method == "fair_model_best") "best" else "mean"
            val semantics = if (method == "fair_model_best") ConstructionPolicyModel.BEST_SCORE_SEMANTICS else ConstructionPolicyModel.SCORE_SEMANTICS
            require(training["labelSelection"].asString == label && training["scoreSemantics"].asString == semantics) { "$method declares another label" }
            require(training["exportFormat"].asInt == EXPORT_FORMAT)
            require(training.getAsJsonArray("splitsUsed").map { it.asString }.toSet() == setOf("train", "valid"))
            require(training["modelSha256"].asString == hash(File(directory, "policy.json")))
            fitHashes.add(method, JsonObject().apply {
                addProperty("train-log.json", hash(File(directory, "train-log.json")))
                training.getAsJsonObject("artifacts").entrySet().sortedBy { it.key }.forEach { (name, artifact) ->
                    val digest = hash(safeFile(directory, name))
                    require(digest == artifact.asJsonObject["sha256"].asString) { "$method artifact changed: $name" }
                    addProperty(name, digest)
                }
                addProperty("treeCount", training["treeCount"].asInt)
            })
        }
        val r2Training = readJson(File(r2Fit, "train-log.json"))
        require(r2Training["modelSha256"].asString == R2_POLICY_SHA256 && hash(File(r2Fit, "policy.json")) == R2_POLICY_SHA256)
        require(r2Training["treeCount"].asInt == 59)
        require(hash(fixedRankerBytes()) == RANKER_SHA256)
        return JsonObject().apply {
            add("exportSha256", exportHashes); add("fitSha256", fitHashes)
            addProperty("referencePolicyTrainLogSha256", hash(File(r2Fit, "train-log.json")))
        }
    }

    private fun validateProtocol(input: File, export: File, fits: Map<String, File>, r2Fit: File, protocol: JsonObject) {
        require(protocol["format"].asInt == 1 && protocol["role"].asString == "construction-third-experiment")
        require(protocol["seed"].asLong == SEED && protocol["hardDeadlineMillis"].asLong == DEADLINE_MS)
        require(protocol.getAsJsonObject("constructionBudget") == gson.toJsonTree(budget))
        require(protocol.getAsJsonArray("methods").map { it.asString } == methods)
        require(protocol.getAsJsonArray("candidateMethods").map { it.asString } == candidateMethods)
        require(protocol["referenceMethod"].asString == referenceMethod)
        require(protocol.getAsJsonObject("sourceSha256") == sourceIdentity()) { "Source changed after freeze; use a new protocol and development run" }
        require(protocol["referencePolicySha256"].asString == R2_POLICY_SHA256 && protocol["fixedRankerSha256"].asString == RANKER_SHA256)
        val binding = binding(input, export, fits, r2Fit, readJson(File(export, "export-manifest.json")))
        require(protocol.getAsJsonObject("binding") == binding) { "Frozen export/fit binding changed" }
        checkHashes(input, protocol.getAsJsonObject("inputs"))
    }

    /** Independent recheck of the Python selection: membership, hashes, preregistered choice and admission. */
    private fun validateSelection(protocolFile: File, protocol: JsonObject, development: File): String {
        val selection = readJson(File(protocolFile.parentFile, "selection.json"))
        val chosen = selection["chosenMethod"].asString
        require(selection["format"].asInt == 1 && chosen in candidateMethods && selection["passed"].asBoolean)
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
        val preregistered = candidateMethods.sortedWith(
            compareByDescending<String> { mean("valid", it, selectionOrder[0]) }
                .thenByDescending { mean("valid", it, selectionOrder[1]) }
                .thenByDescending { mean("valid", it, selectionOrder[2]) }
                .thenByDescending { it == "fair_model_best" }
        ).first()
        require(preregistered == chosen) { "Selection differs from the preregistered choice: $preregistered" }
        require(decisionMetrics.all { mean("valid", chosen, it) >= mean("valid", referenceMethod, it) }) { "Validation quality regressed against the reference policy" }
        require(listOf("train", "valid").any { split -> decisionMetrics.any { mean(split, chosen, it) > mean(split, referenceMethod, it) } }) { "No strict development gain" }
        return chosen
    }

    private fun run(group: TaskGroup, name: String, policies: Map<String, ConstructionPolicyModel>, rankingModel: ObliviousTreeModel, timer: ScheduledExecutorService): Run {
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
                val policy = if (name == "fair_heuristic") ConstructionHeuristicPolicy else policies.getValue(name)
                construction = ConstructionSearch(capture.root, cancellation, budget.copy(maxMillis = remaining), policy, verifier,
                    frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
                candidates = construction.candidates; attempted = construction.stats.attemptedCount; stop = construction.stopReason.name
            }
            searchMillis = (System.nanoTime() - started) / 1e6
            cancellation.throwIfCancelled()
            val unique = candidates.associateBy { ConstructionSearch.canonicalKey(it.plan) }.values.toList()
            val ruleOrder = RuleRanker().rank(request, unique).map { ConstructionSearch.canonicalKey(it.plan) }
            cancellation.throwIfCancelled()
            val remaining = (DEADLINE_MS - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
            val order = ModelRanker(rankingModel, budgetMs = remaining).rank(request, unique).map { ConstructionSearch.canonicalKey(it.plan) }
            unique.associateBy { ConstructionSearch.canonicalKey(it.plan) }.let { byKey ->
                order.take(10).forEach { key -> ClassicRenderer().render(request, byKey.getValue(key).plan) }
            }
            cancellation.throwIfCancelled()
            val elapsed = (System.nanoTime() - started) / 1e6
            if (elapsed > DEADLINE_MS) Run(name, emptyList(), emptyList(), emptyList(), elapsed, searchMillis, attempted, "HARD_DEADLINE", construction)
            else Run(name, unique, order, ruleOrder, elapsed, searchMillis, attempted, stop, construction)
        } catch (cancelled: CodeGenCancelledException) {
            if (!cancellation.isCancelled) throw cancelled
            val elapsed = (System.nanoTime() - started) / 1e6
            Run(name, emptyList(), emptyList(), emptyList(), elapsed, if (searchMillis == 0.0) elapsed else searchMillis, attempted, "HARD_DEADLINE", construction)
        } catch (failure: Exception) {
            Run(name, emptyList(), emptyList(), emptyList(), (System.nanoTime() - started) / 1e6, searchMillis, attempted, "FAILED", construction,
                failure.javaClass.simpleName + ": " + failure.message)
        } finally { deadline.cancel(false) }
    }

    private fun aggregate(rows: List<JsonObject>): JsonArray = JsonArray().apply {
        rows.groupBy { listOf(it["cohort"].asString, it["split"].asString, it["method"].asString) }
            .toSortedMap(compareBy<List<String>> { it.joinToString("/") }).forEach { (key, members) -> add(JsonObject().apply {
                addProperty("cohort", key[0]); addProperty("split", key[1]); addProperty("method", key[2]); addProperty("groups", members.size)
                for (metric in listOf("candidates", "coverage", "bestSurvival", "top1Survival", "top3BestSurvival", "ruleTop1Survival", "ruleTop3BestSurvival",
                    "commonRegret", "commonTop3Recall", "baselineRuleTop1Survival")) {
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
                for (candidate in candidateMethods) for (reference in listOf(referenceMethod, "fair_heuristic", "baseline")) add(JsonObject().apply {
                    addProperty("cohort", cohort.first); addProperty("split", cohort.second)
                    addProperty("method", candidate); addProperty("reference", reference); addProperty("groups", groups.size)
                    for (metric in listOf("coverage", "bestSurvival", "top1Survival", "top3BestSurvival", "ruleTop1Survival")) {
                        val differences = groups.values.map { records ->
                            records.single { it["method"].asString == candidate }[metric].asDouble -
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

    private fun taskRefs(input: File, assignment: Map<String, String>, record: (File) -> Unit): List<TaskRef> {
        val refs = File(input, "groups").walkTopDown().filter { it.isFile && it.name.endsWith(".cgtask.json") }.map { file ->
            record(file)
            val json = readJson(file)
            require(json["source"].asString == "fixture") { "Construction experiments accept controlled captures only" }
            val id = json["groupId"].asString
            val taskDirectory = requireNotNull(file.parentFile)
            val snapshots = json.getAsJsonArray("states").map { state ->
                val relative = state.asJsonObject["snapshot"].asString
                require(!relative.startsWith('/') && '\\' !in relative && ':' !in relative)
                require(relative.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
                File(taskDirectory, relative).canonicalFile.also { relativeInside(taskDirectory, it) }
            }.toSet()
            TaskRef(file.canonicalFile, id, json["screenFamily"].asString, assignment.getValue(id),
                json.getAsJsonObject("request")["scope"].asString == "collection", snapshots)
        }.toList()
        require(refs.map { it.groupId }.toSet() == assignment.keys && refs.map { it.groupId }.distinct().size == refs.size)
        require(refs.all { it.split in setOf("train", "valid", "test") })
        require(refs.groupBy { it.family }.values.all { family -> family.map { it.split }.distinct().size == 1 })
        return refs
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

    private fun schema() = JsonObject().apply {
        addProperty("format", 1); addProperty("role", "construction-state-action")
        addProperty("grammarVersion", ConstructionFeatures.grammarVersion); addProperty("featureSchemaVersion", ConstructionFeatures.version)
        addProperty("namesHash", ConstructionFeatures.namesHash)
        add("names", JsonArray().apply { ConstructionFeatures.names.forEach(::add) })
    }
    private fun deterministicStats(stats: ConstructionStats) = gson.toJsonTree(stats).asJsonObject.apply {
        remove("elapsedNanos"); remove("verificationNanos"); remove("featureNanos"); remove("policyNanos")
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
            "ConstructionThirdExperimentTask.kt", "ConstructionThirdSyntheticFixtures.kt", "ConstructionGeneralizationFixtures.kt",
            "ConstructionBottleneckFixtures.kt").map { base + it } +
            File(root, "app/src/main/java/org/autojs/autojs/core/accessibility/codegen").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.parentFile?.name != "store" }
                .map { it.relativeTo(root).invariantSeparatorsPath }.toList() + listOf(
                "app/src/main/java/org/autojs/autojs/core/accessibility/NodeInfoSnapshot.kt",
                "app/src/main/java/org/autojs/autojs/core/automator/UiNode.kt",
                "tools/codegen-model/construction/select_third.py")
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
        require(!output.exists()) { "Use a fresh third construction experiment output directory" }
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
