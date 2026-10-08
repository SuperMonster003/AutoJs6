package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CancellationSignal
import org.autojs.autojs.core.accessibility.codegen.CodeGenCancelledException
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackage
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
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
 * Posthoc regression diagnostic of a frozen fourth protocol whose preregistered admission was
 * rejected. It evaluates the eight frozen methods only on the cohorts earlier experiments had
 * already inspected, never loads the reserved holdout, and changes no weights, budgets or task
 * memberships. Its results are regression evidence about the diagnosed cases, not independent
 * generalization evidence and not an admission.
 * zh-CN: 第四轮准入被拒后的事后回归诊断, 只在已查看的回归集上运行冻结方法, 不加载新留出集.
 */
class ConstructionFourthRegressionDiagnosticsTask {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private val deadlineMillis = 1000L
    private val searchMillis = 800L
    private val policySha256 = "48f1014a0f850027a18ac46f567262dfe6be63f75b4035947cd6603c285bc1dd"
    private val tinySha256 = "9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17"
    private val constructionBudget = ConstructionBudget(maxMillis = searchMillis)
    private val sharedBaselineBudget = SearchBudget(maxAttempts = 1_000, maxVerified = 100)
    private val sharedConstructionBudget = ConstructionBudget(maxAttempts = 1_000, maxVerified = 100, maxMillis = searchMillis)
    private val methods = listOf("rule_pipeline", "baseline_tiny", "baseline_ranker", "construction_ranker", "merged_rule", "merged_tiny", "merged_ranker", "merged_ranker_shared")
    private val chosen = "merged_ranker"

    private data class Task(val group: TaskGroup, val split: String, val cohort: String)
    private data class Candidate(val value: Verified<SnapshotNode>, val survival: Double)
    private data class Ranking(val name: String, val candidates: List<Verified<SnapshotNode>>, val order: List<String>, val elapsedMillis: Double,
                               val attemptedCount: Int, val stopReason: String, val construction: ConstructionResult<SnapshotNode>?, val error: String?)

    @Test fun evaluateFrozenMethodsOnInspectedRegressionCohortsOnly() {
        val outputName = System.getenv("CODEGEN_CONSTRUCTION_R4_REGRESSION_OUTPUT")
        assumeTrue("Set CODEGEN_CONSTRUCTION_R4_REGRESSION_OUTPUT to a fresh directory", outputName != null)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        val input = directory("CODEGEN_CONSTRUCTION_DATASET")
        val protocolDirectory = directory("CODEGEN_CONSTRUCTION_R4_PROTOCOL")
        val policyFile = File(directory("CODEGEN_CONSTRUCTION_R3_FIT_BEST"), "policy.json")
        val rankerFile = File(directory("CODEGEN_CONSTRUCTION_RANKER"), "ranker.json")
        val output = File(requireNotNull(outputName)).canonicalFile
        val protocolFile = File(protocolDirectory, "protocol.json")
        val protocol = readJson(protocolFile)
        val protocolSha256 = hash(protocolFile)
        assertEquals("construction-fourth-experiment", protocol["role"].asString)
        assertEquals(methods, protocol.getAsJsonArray("methods").map { it.asString })
        assertEquals(gson.toJsonTree(constructionBudget), protocol.getAsJsonObject("constructionBudget"))
        assertEquals("Frozen sources changed; this diagnostic explains the frozen protocol only", protocol.getAsJsonObject("sourceSha256"), sourceIdentity())
        val selectionFile = File(protocolDirectory, "selection.json")
        val selection = readJson(selectionFile)
        assertEquals(protocolSha256, selection["protocolSha256"].asString)
        val selectionPassed = selection["passed"].asBoolean
        assertEquals(policySha256, hash(policyFile))
        assertEquals(protocol["refittedRankerSha256"].asString, hash(rankerFile))
        val policy = ConstructionPolicyModel.read(policyFile)
        val refitted = ExperimentRankerModel.read(rankerFile).model
        val tinyBytes = requireNotNull(javaClass.getResourceAsStream("/codegen/models/tiny-test-v1.ajcgm")).use { it.readBytes() }
        assertEquals(tinySha256, hash(tinyBytes))
        val tiny = (ModelPackageReader().read(tinyBytes.inputStream()) as ModelPackage).model
        val inputs = protocol.getAsJsonObject("inputs")
        val snapshots = object : LinkedHashMap<File, SnapshotNode>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
        }
        val regression = protocol.getAsJsonArray("regressionGroups").map { it.asJsonObject }
        val reserved = (protocol.getAsJsonArray("capturedHoldoutGroups") + protocol.getAsJsonArray("syntheticHoldoutGroups")).map { it.asJsonObject["groupId"].asString }.toSet()
        val synthetic = (ConstructionBottleneckFixtures.groups() + ConstructionGeneralizationFixtures.groups()).associateBy { it.groupId }
        val tasks = regression.map { item ->
            val groupId = item["groupId"].asString
            assertFalse("Reserved holdout group must not be evaluated here", groupId in reserved)
            val group = if (item.has("task")) {
                val taskFile = safeFile(input, item["task"].asString)
                assertEquals(inputs[relativeInside(input, taskFile)].asString, hash(taskFile))
                TaskGroup.load(taskFile) { file -> assertEquals(inputs[relativeInside(input, file)].asString, hash(file)); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
            } else requireNotNull(synthetic[groupId]) { "Unknown synthetic regression group $groupId" }
            assertEquals("Frozen regression input changed: $groupId", item.deepCopy().apply { remove("task") }, metadata(group, item["split"].asString, item["cohort"].asString))
            Task(group, item["split"].asString, item["cohort"].asString)
        }
        assertEquals(76, tasks.size)
        assertTrue(ConstructionThirdSyntheticFixtures.groups().none { fixture -> tasks.any { it.group.groupId == fixture.groupId } })
        require(!output.exists() && output.mkdirs()) { "Use a fresh regression diagnostic output directory" }
        val timer = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "construction-fourth-regression-deadline").apply { isDaemon = true } }
        val rows = ArrayList<JsonObject>()
        try {
            methods.size.let { evaluateTask(tasks.first().group, policy, refitted, tiny, timer) }
            File(output, "results.jsonl").bufferedWriter(Charsets.UTF_8).use { sink ->
                tasks.forEachIndexed { index, task ->
                    val group = task.group
                    val rankings = evaluateTask(group, policy, refitted, tiny, timer)
                    val future = group.states.filterIndexed { stateIndex, _ -> stateIndex != group.captureState }.map { group.request.forState(it) to PlanVerifier(it.root) }
                    val values = HashMap<String, Double>()
                    fun quality(candidate: Verified<SnapshotNode>): Double = values.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) {
                        future.count { (request, verifier) -> verifier.verify(request, candidate.plan) is Verified }.toDouble() / future.size
                    }
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
                            addProperty("elapsedMillis", ranking.elapsedMillis); addProperty("attemptedCount", ranking.attemptedCount)
                            addProperty("stopReason", ranking.stopReason); addProperty("error", ranking.error)
                            add("constructionCounts", ranking.construction?.stats?.let(gson::toJsonTree))
                            add("candidatesDetail", JsonArray().apply { own.forEach { (key, candidate) -> add(JsonObject().apply {
                                addProperty("canonicalKey", key); addProperty("survival", candidate.survival)
                                addProperty("topRank", ranking.order.indexOf(key)); addProperty("planKey", candidate.value.plan.planKey)
                            }) } })
                        }
                        sink.write(gson.toJson(row)); sink.newLine()
                        rows += row.deepCopy().apply { remove("candidatesDetail") }
                    }
                    if (index % 14 == 0 || index == tasks.lastIndex) println("Construction fourth regression diagnostic ${index + 1}/${tasks.size}")
                }
            }
        } finally { timer.shutdownNow() }
        assertEquals(protocolSha256, hash(protocolFile))
        val metrics = JsonObject().apply {
            addProperty("format", 1); addProperty("phase", "regression-diagnostic"); addProperty("role", "posthoc-fourth-construction-regression-diagnostic")
            addProperty("datasetId", protocol["datasetId"].asString); addProperty("protocolSha256", protocolSha256)
            addProperty("selectionSha256", hash(selectionFile)); addProperty("selectionPassed", selectionPassed)
            addProperty("holdoutOpened", false); addProperty("chosenMethod", chosen)
            addProperty("scope", "Inspected regression cohorts only; not independent generalization evidence, not an admission, and no reserved holdout group was loaded")
            add("sourceSha256", protocol.getAsJsonObject("sourceSha256")); addProperty("groups", tasks.size)
            add("summary", aggregate(rows)); add("pairwise", pairwise(rows))
            addProperty("resultsSha256", hash(File(output, "results.jsonl")))
        }
        File(output, "metrics.json").writeText(gson.toJson(metrics) + "\n", Charsets.UTF_8)
        println("Construction fourth regression diagnostic: " + gson.toJson(metrics.getAsJsonArray("summary")))
    }

    private fun evaluateTask(group: TaskGroup, policy: ConstructionPolicyModel, refitted: ObliviousTreeModel, tiny: ObliviousTreeModel, timer: ScheduledExecutorService): List<Ranking> {
        val capture = group.states[group.captureState]
        val request = group.request.forState(capture)
        val cancellation = CancellationSignal()
        val started = System.nanoTime()
        val deadline = timer.schedule({ cancellation.cancel() }, deadlineMillis, TimeUnit.MILLISECONDS)
        fun millis() = (System.nanoTime() - started) / 1e6
        var baseline: List<Verified<SnapshotNode>> = emptyList()
        var baselineAttempts = 0
        var baselineExhausted = false
        var baselineMillis = 0.0
        var construction: ConstructionResult<SnapshotNode>? = null
        var constructionMillis = 0.0
        var shared: List<Verified<SnapshotNode>> = emptyList()
        var sharedAttempts = 0
        var sharedMillis = 0.0
        var sharedConstruction: ConstructionResult<SnapshotNode>? = null
        var failure: String? = null
        var cancelled = false
        try {
            val verifier = PlanVerifier(capture.root, cancellation)
            val search = CandidateSearch(capture.root, cancellation, SearchBudget(), verifier).search(request)
            baseline = search.candidates; baselineAttempts = search.attemptedCount; baselineExhausted = search.budgetExhausted; baselineMillis = millis()
            val remaining = (searchMillis - baselineMillis.toLong()).coerceAtLeast(1)
            construction = ConstructionSearch(capture.root, cancellation, constructionBudget.copy(maxMillis = remaining), policy, verifier,
                frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
            constructionMillis = millis() - baselineMillis
            cancellation.throwIfCancelled()
            val sharedStart = millis()
            val sharedVerifier = PlanVerifier(capture.root, cancellation)
            val sharedSearch = CandidateSearch(capture.root, cancellation, sharedBaselineBudget, sharedVerifier).search(request)
            val sharedBaselineMillis = millis() - sharedStart
            sharedConstruction = ConstructionSearch(capture.root, cancellation, sharedConstructionBudget.copy(maxMillis = (searchMillis - sharedBaselineMillis.toLong()).coerceAtLeast(1)), policy, sharedVerifier,
                frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
            shared = sharedSearch.candidates + sharedConstruction.candidates
            sharedAttempts = sharedSearch.attemptedCount + sharedConstruction.stats.attemptedCount
            sharedMillis = millis() - sharedStart
        } catch (e: CodeGenCancelledException) {
            if (!cancellation.isCancelled) throw e
            cancelled = true
        } catch (e: Exception) {
            failure = e.javaClass.simpleName + ": " + e.message
        } finally { deadline.cancel(false) }
        fun unique(vararg sets: List<Verified<SnapshotNode>>): List<Verified<SnapshotNode>> {
            val union = LinkedHashMap<String, Verified<SnapshotNode>>()
            for (set in sets) for (candidate in set) union.putIfAbsent(ConstructionSearch.canonicalKey(candidate.plan), candidate)
            return union.values.toList()
        }
        fun rank(name: String, candidates: List<Verified<SnapshotNode>>, ranker: String, searchTime: Double, attempted: Int, stop: String, result: ConstructionResult<SnapshotNode>?): Ranking {
            if (cancelled) return Ranking(name, emptyList(), emptyList(), baselineMillis, attempted, "HARD_DEADLINE", result, null)
            if (failure != null) return Ranking(name, emptyList(), emptyList(), baselineMillis, attempted, "FAILED", result, failure)
            val rankStart = System.nanoTime()
            return try {
                val order = when (ranker) {
                    "rule" -> RuleRanker().rank(request, candidates)
                    "tiny" -> ModelRanker(tiny, budgetMs = 150).rank(request, candidates)
                    else -> ModelRanker(refitted, budgetMs = 150).rank(request, candidates)
                }.map { ConstructionSearch.canonicalKey(it.plan) }
                val byKey = candidates.associateBy { ConstructionSearch.canonicalKey(it.plan) }
                order.take(10).forEach { key -> ClassicRenderer().render(request, byKey.getValue(key).plan) }
                val elapsed = searchTime + (System.nanoTime() - rankStart) / 1e6
                if (elapsed > deadlineMillis) Ranking(name, emptyList(), emptyList(), elapsed, attempted, "HARD_DEADLINE", result, null)
                else Ranking(name, candidates, order, elapsed, attempted, stop, result, null)
            } catch (e: Exception) {
                Ranking(name, emptyList(), emptyList(), searchTime, attempted, "FAILED", result, e.javaClass.simpleName + ": " + e.message)
            }
        }
        val constructed = unique(construction?.candidates.orEmpty())
        val merged = unique(baseline, construction?.candidates.orEmpty())
        val baselineStop = if (baselineExhausted) "COUNTER_BUDGET" else "EXHAUSTED"
        val constructionStop = construction?.stopReason?.name ?: "NONE"
        val mergedMillis = baselineMillis + constructionMillis
        val mergedAttempts = baselineAttempts + (construction?.stats?.attemptedCount ?: 0)
        return listOf(
            rank("rule_pipeline", unique(baseline), "rule", baselineMillis, baselineAttempts, baselineStop, null),
            rank("baseline_tiny", unique(baseline), "tiny", baselineMillis, baselineAttempts, baselineStop, null),
            rank("baseline_ranker", unique(baseline), "refitted", baselineMillis, baselineAttempts, baselineStop, null),
            rank("construction_ranker", constructed, "refitted", mergedMillis, construction?.stats?.attemptedCount ?: 0, constructionStop, construction),
            rank("merged_rule", merged, "rule", mergedMillis, mergedAttempts, constructionStop, construction),
            rank("merged_tiny", merged, "tiny", mergedMillis, mergedAttempts, constructionStop, construction),
            rank("merged_ranker", merged, "refitted", mergedMillis, mergedAttempts, constructionStop, construction),
            rank("merged_ranker_shared", unique(shared), "refitted", sharedMillis, sharedAttempts, sharedConstruction?.stopReason?.name ?: "NONE", sharedConstruction),
        )
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
                for (reference in methods.filter { it != chosen }) add(JsonObject().apply {
                    addProperty("cohort", cohort.first); addProperty("split", cohort.second)
                    addProperty("method", chosen); addProperty("reference", reference); addProperty("groups", groups.size)
                    for (metric in listOf("coverage", "bestSurvival", "top1Survival", "top3BestSurvival")) {
                        val differences = groups.values.map { records ->
                            records.single { it["method"].asString == chosen }[metric].asDouble - records.single { it["method"].asString == reference }[metric].asDouble
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

    /** The same path list as the frozen fourth experiment; the protocol's hashes must still match. */
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
