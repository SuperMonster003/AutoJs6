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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Posthoc regression diagnostic of a frozen third protocol whose preregistered admission was
 * rejected. It evaluates the five frozen methods only on the cohorts that earlier experiments had
 * already inspected (the first bottleneck tasks, the second generalization tasks and the second
 * captured holdout). It never loads the reserved captured or synthetic holdout groups, changes no
 * weights, budgets or task memberships, and its results are regression evidence about the
 * diagnosed cases, not independent generalization evidence or an admission.
 * zh-CN: 第三轮协议准入被拒后的事后回归诊断. 只在前两轮已查看的回归集上运行冻结方法,
 * 不加载新留出集, 不改权重/预算/任务; 结果只说明已诊断案例是否改变, 不作为独立泛化证据或准入.
 */
class ConstructionThirdRegressionDiagnosticsTask {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private val deadlineMillis = 1000L
    private val searchMillis = 800L
    private val budget = ConstructionBudget(maxMillis = searchMillis)
    private val methods = listOf("baseline", "fair_heuristic", "fair_model_r2", "fair_model_mean", "fair_model_best")
    private val candidateMethods = listOf("fair_model_mean", "fair_model_best")

    private data class Task(val group: TaskGroup, val split: String, val cohort: String)
    private data class Candidate(val value: Verified<SnapshotNode>, val survival: Double)
    private data class Run(
        val name: String, val candidates: List<Verified<SnapshotNode>>, val order: List<String>, val ruleOrder: List<String>,
        val elapsedMillis: Double, val searchMillis: Double, val attemptedCount: Int?, val stopReason: String,
        val construction: ConstructionResult<SnapshotNode>?, val error: String? = null,
    )

    @Test fun evaluateFrozenMethodsOnInspectedRegressionCohortsOnly() {
        val outputName = System.getenv("CODEGEN_CONSTRUCTION_R3_REGRESSION_OUTPUT")
        assumeTrue("Set CODEGEN_CONSTRUCTION_R3_REGRESSION_OUTPUT to a fresh directory", outputName != null)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        val input = directory("CODEGEN_CONSTRUCTION_DATASET")
        val protocolDirectory = directory("CODEGEN_CONSTRUCTION_R3_PROTOCOL")
        val fits = linkedMapOf("fair_model_r2" to directory("CODEGEN_CONSTRUCTION_POLICY_DIR"),
            "fair_model_mean" to directory("CODEGEN_CONSTRUCTION_R3_FIT_MEAN"), "fair_model_best" to directory("CODEGEN_CONSTRUCTION_R3_FIT_BEST"))
        val output = File(requireNotNull(outputName)).canonicalFile
        val protocolFile = File(protocolDirectory, "protocol.json")
        val protocol = readJson(protocolFile)
        val protocolSha256 = hash(protocolFile)
        assertEquals("construction-third-experiment", protocol["role"].asString)
        assertEquals(methods, protocol.getAsJsonArray("methods").map { it.asString })
        assertEquals(gson.toJsonTree(budget), protocol.getAsJsonObject("constructionBudget"))
        assertEquals(deadlineMillis, protocol["hardDeadlineMillis"].asLong)
        assertEquals("Frozen sources changed; this diagnostic explains the frozen protocol only", protocol.getAsJsonObject("sourceSha256"), sourceIdentity())
        val selectionFile = File(protocolDirectory, "selection.json")
        val selection = readJson(selectionFile)
        assertEquals(protocolSha256, selection["protocolSha256"].asString)
        // The admission result is recorded as it stands; a rejected decision does not open the holdout.
        val selectionPassed = selection["passed"].asBoolean
        val fitHashes = protocol.getAsJsonObject("binding").getAsJsonObject("fitSha256")
        val policies = LinkedHashMap<String, ConstructionPolicyModel>()
        for ((name, directory) in fits) {
            val policyFile = File(directory, "policy.json")
            val expected = if (name == "fair_model_r2") protocol["referencePolicySha256"].asString
                else fitHashes.getAsJsonObject(name)["policy.json"].asString
            assertEquals("Frozen policy changed: $name", expected, hash(policyFile))
            policies[name] = ConstructionPolicyModel.read(policyFile)
        }
        val fixedBytes = requireNotNull(javaClass.getResourceAsStream("/codegen/models/tiny-test-v1.ajcgm")).use { it.readBytes() }
        assertEquals(protocol["fixedRankerSha256"].asString, hash(fixedBytes))
        val ranker = (ModelPackageReader().read(fixedBytes.inputStream()) as ModelPackage).model
        val inputs = protocol.getAsJsonObject("inputs")
        val snapshots = object : LinkedHashMap<File, SnapshotNode>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
        }
        val regression = protocol.getAsJsonArray("regressionGroups").map { it.asJsonObject }
        val reserved = (protocol.getAsJsonArray("capturedHoldoutGroups") + protocol.getAsJsonArray("syntheticHoldoutGroups"))
            .map { it.asJsonObject["groupId"].asString }.toSet()
        assertTrue(regression.none { it["groupId"].asString in reserved })
        val synthetic = (ConstructionBottleneckFixtures.groups() + ConstructionGeneralizationFixtures.groups()).associateBy { it.groupId }
        val tasks = regression.map { item ->
            val groupId = item["groupId"].asString
            assertFalse("Reserved holdout group must not be evaluated here", groupId in reserved)
            val group = if (item.has("task")) {
                val taskFile = safeFile(input, item["task"].asString)
                assertEquals(inputs[relativeInside(input, taskFile)].asString, hash(taskFile))
                TaskGroup.load(taskFile) { file ->
                    assertEquals(inputs[relativeInside(input, file)].asString, hash(file))
                    snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) }
                }
            } else requireNotNull(synthetic[groupId]) { "Unknown synthetic regression group $groupId" }
            assertEquals("Frozen regression input changed: $groupId", item.deepCopy().apply { remove("task") },
                metadata(group, item["split"].asString, item["cohort"].asString))
            Task(group, item["split"].asString, item["cohort"].asString)
        }
        assertEquals(regression.size, tasks.map { it.group.groupId }.distinct().size)
        assertTrue(ConstructionThirdSyntheticFixtures.groups().none { it.groupId in tasks.map { task -> task.group.groupId } })
        require(!output.exists() && output.mkdirs()) { "Use a fresh regression diagnostic output directory" }
        val timer = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "construction-third-regression-deadline").apply { isDaemon = true } }
        val rows = ArrayList<JsonObject>()
        try {
            // Warm every engine on the first regression task; its measurements are not reported.
            methods.forEach { run(tasks.first().group, it, policies, ranker, timer) }
            File(output, "results.jsonl").bufferedWriter(Charsets.UTF_8).use { sink ->
                tasks.forEachIndexed { index, task ->
                    val group = task.group
                    val rotated = methods.indices.map { methods[(it + index) % methods.size] }
                    val runs = rotated.map { run(group, it, policies, ranker, timer) }.associateBy { it.name }
                    val future = group.states.filterIndexed { stateIndex, _ -> stateIndex != group.captureState }
                        .map { group.request.forState(it) to PlanVerifier(it.root) }
                    val values = HashMap<String, Double>()
                    fun quality(candidate: Verified<SnapshotNode>): Double = values.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) {
                        future.count { (request, verifier) -> verifier.verify(request, candidate.plan) is Verified }.toDouble() / future.size
                    }
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
                    }
                    if (index % 14 == 0 || index == tasks.lastIndex) println("Construction third regression diagnostic ${index + 1}/${tasks.size}")
                }
            }
        } finally { timer.shutdownNow() }
        assertEquals(protocolSha256, hash(protocolFile))
        assertEquals("Frozen sources changed during the diagnostic", protocol.getAsJsonObject("sourceSha256"), sourceIdentity())
        val metrics = JsonObject().apply {
            addProperty("format", 1); addProperty("phase", "regression-diagnostic"); addProperty("role", "posthoc-third-construction-regression-diagnostic")
            addProperty("datasetId", protocol["datasetId"].asString); addProperty("protocolSha256", protocolSha256)
            addProperty("selectionSha256", hash(selectionFile)); addProperty("selectionPassed", selectionPassed)
            addProperty("holdoutOpened", false); addProperty("chosenMethod", selection["chosenMethod"].asString)
            addProperty("scope", "Inspected regression cohorts only; not independent generalization evidence, not an admission, and no reserved holdout group was loaded")
            add("sourceSha256", protocol.getAsJsonObject("sourceSha256")); addProperty("groups", tasks.size)
            add("policies", JsonObject().apply { policies.forEach { (name, policy) -> add(name, JsonObject().apply {
                addProperty("scoreSemantics", policy.scoreSemantics); addProperty("trees", policy.model.treeCount)
            }) } })
            add("summary", aggregate(rows)); add("pairwise", pairwise(rows))
            addProperty("resultsSha256", hash(File(output, "results.jsonl")))
        }
        File(output, "metrics.json").writeText(gson.toJson(metrics) + "\n", Charsets.UTF_8)
        println("Construction third regression diagnostic: " + gson.toJson(metrics.getAsJsonArray("summary")))
    }

    private fun run(group: TaskGroup, name: String, policies: Map<String, ConstructionPolicyModel>, rankingModel: ObliviousTreeModel, timer: ScheduledExecutorService): Run {
        val capture = group.states[group.captureState]
        val request = group.request.forState(capture)
        val cancellation = CancellationSignal()
        val started = System.nanoTime()
        val deadline = timer.schedule({ cancellation.cancel() }, deadlineMillis, TimeUnit.MILLISECONDS)
        var construction: ConstructionResult<SnapshotNode>? = null
        var attempted: Int? = null
        var searched = 0.0
        return try {
            val verifier = PlanVerifier(capture.root, cancellation)
            val candidates: List<Verified<SnapshotNode>>
            val stop: String
            if (name == "baseline") {
                val baseline = CandidateSearch(capture.root, cancellation, SearchBudget(), verifier).search(request)
                candidates = baseline.candidates; attempted = baseline.attemptedCount
                stop = if (baseline.budgetExhausted) "COUNTER_BUDGET" else "EXHAUSTED"
            } else {
                val remaining = (searchMillis - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
                val policy = if (name == "fair_heuristic") ConstructionHeuristicPolicy else policies.getValue(name)
                construction = ConstructionSearch(capture.root, cancellation, budget.copy(maxMillis = remaining), policy, verifier,
                    frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
                candidates = construction.candidates; attempted = construction.stats.attemptedCount; stop = construction.stopReason.name
            }
            searched = (System.nanoTime() - started) / 1e6
            cancellation.throwIfCancelled()
            val unique = candidates.associateBy { ConstructionSearch.canonicalKey(it.plan) }.values.toList()
            val ruleOrder = RuleRanker().rank(request, unique).map { ConstructionSearch.canonicalKey(it.plan) }
            cancellation.throwIfCancelled()
            val remaining = (deadlineMillis - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
            val order = ModelRanker(rankingModel, budgetMs = remaining, featureSchemaVersion = 1).rank(request, unique).map { ConstructionSearch.canonicalKey(it.plan) }
            unique.associateBy { ConstructionSearch.canonicalKey(it.plan) }.let { byKey ->
                order.take(10).forEach { key -> ClassicRenderer().render(request, byKey.getValue(key).plan) }
            }
            cancellation.throwIfCancelled()
            val elapsed = (System.nanoTime() - started) / 1e6
            if (elapsed > deadlineMillis) Run(name, emptyList(), emptyList(), emptyList(), elapsed, searched, attempted, "HARD_DEADLINE", construction)
            else Run(name, unique, order, ruleOrder, elapsed, searched, attempted, stop, construction)
        } catch (cancelled: CodeGenCancelledException) {
            if (!cancellation.isCancelled) throw cancelled
            val elapsed = (System.nanoTime() - started) / 1e6
            Run(name, emptyList(), emptyList(), emptyList(), elapsed, if (searched == 0.0) elapsed else searched, attempted, "HARD_DEADLINE", construction)
        } catch (failure: Exception) {
            Run(name, emptyList(), emptyList(), emptyList(), (System.nanoTime() - started) / 1e6, searched, attempted, "FAILED", construction,
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
                for (candidate in candidateMethods) for (reference in listOf("fair_model_r2", "fair_heuristic", "baseline")) add(JsonObject().apply {
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

    /** The same path list as the frozen third experiment; the protocol's hashes must still match. */
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

    private fun relativeInside(root: File, file: File): String {
        val base = root.canonicalFile
        val path = file.canonicalFile
        require(path.toPath().startsWith(base.toPath()) && path != base) { "Input path escaped its root" }
        return path.relativeTo(base).invariantSeparatorsPath
    }
    private fun safeFile(root: File, relative: String) = File(root, relative).canonicalFile.also { require(relativeInside(root, it) == relative) }
    private fun readJson(file: File) = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
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
