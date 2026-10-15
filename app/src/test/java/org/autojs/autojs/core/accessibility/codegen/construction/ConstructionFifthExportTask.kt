package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.FeatureExport
import org.autojs.autojs.core.accessibility.codegen.dataset.RealAppSplit
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Random
import kotlin.math.min

/**
 * Explicit offline engineering for the fifth construction round (roadmap plan of 2026-10-15). `export`
 * writes teacher state/action data of construction grammar 2 (feature schema 2) for controlled train/valid
 * groups and, optionally, maintainer-labelled groups of real-train applications (D25); for real-test
 * applications only the task header naming the app is read, never a snapshot. `compare` runs the grammar 1 and grammar 2 heuristics on the same
 * train/valid groups as a development regression. Neither freezes a protocol or admits anything, and the
 * reserved fixture holdout is not opened.
 * zh-CN: 第五轮构造实验的显式离线工程 (2026-10-15 路线图计划). `export` 为受控 train/valid 任务组及可选的
 * real-train 应用的维护者标注任务组 (D25) 导出构造语法 2 (特征 schema 2) 的教师状态/动作数据; real-test
 * 应用只读取标明应用的任务头, 从不加载快照. `compare` 在同一批 train/valid 任务组上对比语法 1 与语法 2 的启发式, 作为开发集回归.
 * 两者都不冻结协议, 不做准入, 也不打开预留的受控留出集.
 */
class ConstructionFifthExportTask {
    @Test fun runOfflineFifthConstructionExport() {
        val mode = System.getenv("CODEGEN_CONSTRUCTION_R5_MODE")
        assumeTrue("Set CODEGEN_CONSTRUCTION_R5_MODE=export or compare", mode != null)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        val input = directory("CODEGEN_CONSTRUCTION_DATASET")
        val single = System.getenv("CODEGEN_CONSTRUCTION_R5_SINGLE")?.toInt() ?: 28
        val collection = System.getenv("CODEGEN_CONSTRUCTION_R5_COLLECTION")?.toInt() ?: 28
        when (requireNotNull(mode)) {
            "export" -> ConstructionFifthExport.export(
                input, System.getenv("CODEGEN_CONSTRUCTION_R5_REAL")?.takeIf(String::isNotBlank)?.let { File(it).canonicalFile },
                directory("CODEGEN_CONSTRUCTION_OUTPUT"), single, collection,
                System.getenv("CODEGEN_CONSTRUCTION_TEACHER_RUNS")?.toInt() ?: 4,
            )
            "compare" -> ConstructionFifthExport.compare(input, directory("CODEGEN_CONSTRUCTION_OUTPUT"), single, collection)
            else -> error("Unknown fifth construction mode")
        }
    }
}

internal object ConstructionFifthExport {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private const val SEED = 20261015L
    private const val EXPORT_FORMAT = 2
    private val teacherBudget = ConstructionBudget(maxFrontier = 128, maxExpandedStates = 4000, maxActionScores = 24000, maxMillis = 30_000)
    /** Counter-limited like the teacher, so both grammars stop deterministically. zh-CN: 与教师相同只受计数限制, 两种语法均确定性停止. */
    private val compareBudget = ConstructionBudget(maxMillis = 30_000)
    private val labelSemantics = linkedMapOf(
        "label" to "mean survival of completed observed rollouts through the state/action; unknown edges omitted",
        "labelBest" to "maximum survival among completed observed rollouts through the state/action; an observed lower bound of the best continuation",
    )

    private data class TaskRef(val file: File, val root: File, val groupId: String, val family: String, val split: String, val source: String, val collection: Boolean)
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

    private fun teacherController(run: Int) = if (run % 2 == 0) ConstructionFrontierMode.FAMILY_SCORE else ConstructionFrontierMode.ANCHOR_PHASE_FAIR

    fun export(input: File, real: File?, output: File, perFamilySingle: Int, perFamilyCollection: Int, teacherRuns: Int) {
        require(perFamilySingle in 0..288 && perFamilyCollection in 0..48 && teacherRuns in 2..8)
        require(input.isDirectory)
        val inputs = sortedMapOf<String, String>()
        val realInputs = sortedMapOf<String, String>()
        val fixtures = developmentRefs(input, perFamilySingle, perFamilyCollection) { inputs.getOrPut(relativeInside(input, it)) { hash(it) } }
        val reals = real?.let { root ->
            require(root.isDirectory) { "Real task directory does not exist" }
            FeatureExport.requirePrivate(root)
            FeatureExport.requirePrivate(output)
            realTrainRefs(root) { realInputs.getOrPut(relativeInside(root, it)) { hash(it) } }
        }.orEmpty()
        val selected = (fixtures + reals).sortedWith(compareBy({ it.source }, { it.groupId }))
        require(selected.any { it.split == "train" || it.split == "real-train" } && selected.any { it.split == "valid" })
        requireFreshOutput(output)
        writeJson(File(output, "features.json"), schema())
        val groupMetadata = JsonArray()
        var actionRows = 0L
        var unknownEdges = 0L
        var terminalCount = 0L
        var validTerminalCount = 0L
        val snapshots = snapshotCache()
        val columns = listOf("split", "groupId", "stateKey", "actionKey", "label", "labelBest", "observations") + ConstructionFeaturesV2.names
        File(output, "actions.csv").bufferedWriter(Charsets.UTF_8).use { csv ->
            csv.write(FeatureExport.csvRow(columns))
            File(output, "teacher-traces.jsonl").bufferedWriter(Charsets.UTF_8).use { traces ->
                selected.forEachIndexed { position, ref ->
                    val record: (File) -> Unit = if (ref.source == "real") { file -> realInputs.getOrPut(relativeInside(ref.root, file)) { hash(file) } }
                        else { file -> inputs.getOrPut(relativeInside(input, file)) { hash(file) } }
                    val group = TaskGroup.load(ref.file) { file -> record(file); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
                    group.requireLabelable()
                    check(group.groupId == ref.groupId && group.source == ref.source)
                    val capture = group.states[group.captureState]
                    val request = group.request.forState(capture)
                    val future = future(group)
                    val returns = HashMap<String, Double>()
                    val observed = sortedMapOf<Pair<String, String>, ObservedAction>(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
                    val scoredEdges = HashSet<Pair<String, String>>()
                    var groupTerminals = 0
                    var groupValid = 0
                    var relativeValid = 0
                    val runMetadata = JsonArray()
                    repeat(teacherRuns) { runIndex ->
                        val random = Random(SEED xor group.groupId.hashCode().toLong() xor (runIndex.toLong() shl 32))
                        // Runs 0/1 are the plain grammar 2 heuristic under each controller; later runs add seeded noise.
                        // zh-CN: 前两次为两种控制器下的语法 2 纯启发式, 其后加入带种子的探索噪声.
                        val policy = if (runIndex < 2) ConstructionHeuristicPolicyV2 else ConstructionPolicy { vector ->
                            ConstructionHeuristicPolicyV2.score(vector) + (random.nextDouble() - .5) * 2.0
                        }
                        val result = ConstructionSearch(capture.root, budget = teacherBudget, policy = policy,
                            frontierMode = teacherController(runIndex), grammarVersion = ConstructionGrammar.GRAMMAR_V2).search(request)
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
                            if (verified?.plan?.family == Family.SELF_RELATIVE) relativeValid++
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
                    groupMetadata.add(metadata(group, ref.split).apply {
                        addProperty("task", relativeInside(ref.root, ref.file))
                        addProperty("rows", observed.size); addProperty("scoredEdges", scoredEdges.size)
                        addProperty("unknownEdges", scoredEdges.count { it !in observed })
                        addProperty("completedRollouts", groupTerminals); addProperty("validCompletedRollouts", groupValid)
                        addProperty("validRelativeRollouts", relativeValid)
                        add("runs", runMetadata)
                    })
                    actionRows += observed.size; unknownEdges += scoredEdges.count { it !in observed }
                    terminalCount += groupTerminals; validTerminalCount += groupValid
                    if (position % 14 == 0 || position == selected.lastIndex) {
                        println("Construction fifth export ${position + 1}/${selected.size}: rows=$actionRows, terminal=$terminalCount")
                    }
                }
            }
        }
        inputs.forEach { (path, digest) -> require(hash(File(input, path)) == digest) { "Dataset changed while exporting" } }
        real?.let { root -> realInputs.forEach { (path, digest) -> require(hash(File(root, path)) == digest) { "Real tasks changed while exporting" } } }
        val split = readJson(File(input, "split-map.json"))
        val manifest = JsonObject().apply {
            addProperty("format", EXPORT_FORMAT); addProperty("datasetId", split["datasetId"].asString + "-construction-fifth")
            addProperty("grammarVersion", ConstructionFeaturesV2.grammarVersion)
            addProperty("featuresSha256", hash(File(output, "features.json")))
            addProperty("actionsSha256", hash(File(output, "actions.csv")))
            addProperty("tracesSha256", hash(File(output, "teacher-traces.jsonl")))
            addProperty("sampling", "sha256:construction-r5-$SEED:groupId per original family and scope; first $perFamilySingle single and " +
                "$perFamilyCollection collection; fixture train/valid families only; every labelable real-train group (D25), never real-test")
            addProperty("perFamilySingle", perFamilySingle); addProperty("perFamilyCollection", perFamilyCollection)
            addProperty("teacherRuns", teacherRuns)
            add("teacherControllers", JsonArray().apply { repeat(teacherRuns) { add(teacherController(it).name + if (it >= 2) "+noise" else "") } })
            addProperty("traceFeatures", "Resolve (groupId,stateKey,actionKey) in actions.csv")
            add("labelSemantics", gson.toJsonTree(labelSemantics))
            addProperty("rows", actionRows); addProperty("observedEdges", actionRows); addProperty("unknownEdges", unknownEdges)
            addProperty("completedRollouts", terminalCount); addProperty("validCompletedRollouts", validTerminalCount)
            add("teacherBudget", gson.toJsonTree(teacherBudget))
            add("inputs", gson.toJsonTree(inputs)); add("realInputs", gson.toJsonTree(realInputs))
            add("sourceSha256", sourceIdentity()); add("groups", groupMetadata)
        }
        writeJson(File(output, "export-manifest.json"), manifest) // last: incomplete output cannot be trained
    }

    /**
     * Grammar 1 against grammar 2 with their fixed heuristics, the same requests, verifier and budgets, on the
     * same development groups. Survival is observed in the later states; no held-out task is read.
     * zh-CN: 在同一批开发任务组上以相同请求, 验证器与预算对比语法 1 与语法 2 的固定启发式; 存活由后续状态观测, 不读取任何留出任务.
     */
    fun compare(input: File, output: File, perFamilySingle: Int, perFamilyCollection: Int) {
        require(input.isDirectory)
        val refs = developmentRefs(input, perFamilySingle, perFamilyCollection) {}
        requireFreshOutput(output)
        val snapshots = snapshotCache()
        val groups = JsonArray()
        val totals = linkedMapOf("v1" to IntArray(3), "v2" to IntArray(3))
        var raised = 0
        var lowered = 0
        refs.forEachIndexed { position, ref ->
            val group = TaskGroup.load(ref.file) { file -> snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
            group.requireLabelable()
            val capture = group.states[group.captureState]
            val request = group.request.forState(capture)
            val future = future(group)
            val item = JsonObject().apply { addProperty("groupId", group.groupId); addProperty("split", ref.split); addProperty("collection", ref.collection) }
            val best = HashMap<String, Double>()
            for ((name, version, policy) in listOf(
                Triple("v1", ConstructionGrammar.GRAMMAR_V1, ConstructionHeuristicPolicy),
                Triple("v2", ConstructionGrammar.GRAMMAR_V2, ConstructionHeuristicPolicyV2),
            )) {
                val result = ConstructionSearch(capture.root, budget = compareBudget, policy = policy,
                    frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR, grammarVersion = version).search(request)
                val survivals = result.candidates.map { survival(it.plan, future) }
                val ceiling = survivals.maxOrNull() ?: 0.0
                best[name] = ceiling
                totals.getValue(name).apply {
                    if (result.candidates.isNotEmpty()) this[0]++
                    if (ceiling >= 1.0) this[1]++
                    if (result.candidates.any { it.plan.family == Family.SELF_RELATIVE }) this[2]++
                }
                item.add(name, JsonObject().apply {
                    addProperty("candidates", result.candidates.size)
                    addProperty("relativeCandidates", result.candidates.count { it.plan.family == Family.SELF_RELATIVE })
                    addProperty("ceiling", ceiling); addProperty("stopReason", result.stopReason.name)
                    add("counts", deterministicStats(result.stats))
                })
            }
            if (best.getValue("v2") > best.getValue("v1") + 1e-9) raised++
            if (best.getValue("v2") < best.getValue("v1") - 1e-9) lowered++
            groups.add(item)
            if (position % 28 == 0 || position == refs.lastIndex) println("Construction fifth compare ${position + 1}/${refs.size}: raised=$raised lowered=$lowered")
        }
        writeJson(File(output, "compare.json"), JsonObject().apply {
            addProperty("format", 1); addProperty("role", "construction-fifth-grammar-compare")
            addProperty("groups", refs.size)
            add("totals", JsonObject().apply { totals.forEach { (name, values) -> add(name, JsonObject().apply {
                addProperty("covered", values[0]); addProperty("ceilingOne", values[1]); addProperty("withRelative", values[2])
            }) } })
            addProperty("ceilingRaised", raised); addProperty("ceilingLowered", lowered)
            add("budget", gson.toJsonTree(compareBudget)); add("sourceSha256", sourceIdentity()); add("groupResults", groups)
        })
    }

    /** The controlled development cohort: train/valid families only, per-family quotas by a fixed hash order.
     * zh-CN: 受控开发集: 只取 train/valid 界面族, 每族按固定哈希顺序取配额. */
    private fun developmentRefs(input: File, perFamilySingle: Int, perFamilyCollection: Int, record: (File) -> Unit): List<TaskRef> {
        val split = readJson(File(input, "split-map.json").also(record))
        File(input, "dataset-manifest.json").also(record)
        val assignment = split.getAsJsonObject("assignment").entrySet().associate { it.key to it.value.asString }
        val refs = File(input, "groups").walkTopDown().filter { it.isFile && it.name.endsWith(".cgtask.json") }.map { file ->
            record(file)
            val json = readJson(file)
            require(json["source"].asString == "fixture") { "The controlled dataset accepts fixture captures only" }
            val id = json["groupId"].asString
            TaskRef(file.canonicalFile, input, id, json["screenFamily"].asString, assignment.getValue(id), "fixture",
                json.getAsJsonObject("request")["scope"].asString == "collection")
        }.toList()
        require(refs.map { it.groupId }.toSet() == assignment.keys && refs.map { it.groupId }.distinct().size == refs.size)
        require(refs.groupBy { it.family }.values.all { family -> family.map { it.split }.distinct().size == 1 })
        return refs.filter { it.split in setOf("train", "valid") }.groupBy { it.family }.toSortedMap().values.flatMap { family ->
            fun pick(collection: Boolean, quota: Int) = family.filter { it.collection == collection }
                .sortedWith(compareBy<TaskRef> { hash("construction-r5-$SEED:${it.groupId}".toByteArray(Charsets.UTF_8)) }.thenBy { it.groupId })
                .take(quota)
            pick(false, perFamilySingle) + pick(true, perFamilyCollection)
        }.sortedBy { it.groupId }
    }

    /** Every labelable real-train group below [root] (D25); real-test groups are skipped after reading their app name.
     * zh-CN: [root] 下全部可标注的 real-train 任务组 (D25); real-test 任务组读出应用名后即跳过. */
    private fun realTrainRefs(root: File, record: (File) -> Unit): List<TaskRef> {
        val refs = root.walkTopDown().filter { it.isFile && it.name.endsWith(".cgtask.json") }.mapNotNull { file ->
            FeatureExport.requirePrivate(file)
            val json = readJson(file)
            if (json["source"]?.asString != "real") return@mapNotNull null
            if (RealAppSplit.split(json["app"].asString) != RealAppSplit.REAL_TRAIN) return@mapNotNull null
            if (json.getAsJsonArray("states").size() < 2) return@mapNotNull null
            record(file)
            TaskRef(file.canonicalFile, root, json["groupId"].asString, json["screenFamily"].asString, RealAppSplit.REAL_TRAIN, "real",
                json.getAsJsonObject("request")["scope"].asString == "collection")
        }.toList()
        require(refs.map { it.groupId }.distinct().size == refs.size) { "Duplicate real groupId" }
        return refs.sortedBy { it.groupId }
    }

    private fun metadata(group: TaskGroup, split: String) = JsonObject().apply {
        addProperty("groupId", group.groupId); addProperty("screenFamily", group.screenFamily); addProperty("source", group.source)
        addProperty("split", split); addProperty("captureState", group.captureState)
        add("request", gson.toJsonTree(group.request))
        add("states", JsonArray().apply { group.states.forEach { state -> add(JsonObject().apply {
            addProperty("stateId", state.stateId); addProperty("snapshot", state.snapshot)
            add("mutations", gson.toJsonTree(state.mutations)); add("targetPaths", gson.toJsonTree(state.targetPaths))
            addProperty("snapshotContentSha256", hash(gson.toJson(state.root.snapshot).toByteArray(Charsets.UTF_8)))
        }) } })
    }

    private fun schema() = JsonObject().apply {
        addProperty("format", 1); addProperty("role", "construction-state-action")
        addProperty("grammarVersion", ConstructionFeaturesV2.grammarVersion); addProperty("featureSchemaVersion", ConstructionFeaturesV2.version)
        addProperty("namesHash", ConstructionFeaturesV2.namesHash)
        add("names", JsonArray().apply { ConstructionFeaturesV2.names.forEach(::add) })
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
            "ConstructionFifthExportTask.kt").map { base + it } +
            "app/src/test/java/org/autojs/autojs/core/accessibility/codegen/dataset/RealAppSplit.kt" +
            File(root, "app/src/main/java/org/autojs/autojs/core/accessibility/codegen").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.parentFile?.name != "store" }
                .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        return JsonObject().apply { paths.distinct().sorted().forEach { path ->
            addProperty(path, hash(File(root, path).readText(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)))
        } }
    }

    private fun snapshotCache() = object : LinkedHashMap<File, SnapshotNode>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
    }
    private fun relativeInside(root: File, file: File): String {
        val base = root.canonicalFile
        val path = file.canonicalFile
        require(path.toPath().startsWith(base.toPath()) && path != base) { "Input path escaped its root" }
        return path.relativeTo(base).invariantSeparatorsPath
    }
    private fun requireFreshOutput(output: File) {
        require(!output.exists()) { "Use a fresh fifth construction output directory" }
        require(output.mkdirs()) { "Cannot create the fifth construction output" }
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
