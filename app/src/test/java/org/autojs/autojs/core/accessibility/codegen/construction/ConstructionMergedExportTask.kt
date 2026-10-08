package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.dataset.FeatureExport
import org.autojs.autojs.core.accessibility.codegen.dataset.TaskGroup
import org.autojs.autojs.core.accessibility.codegen.features.CandidateFeatures
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.search.SearchBudget
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Explicit offline export of the candidate distribution a merged pipeline ranks: the original
 * rule search's verified candidates united with a frozen construction policy's verified candidates,
 * deduplicated by canonical plan, labelled by later-state survival and featurized by the host's
 * CandidateFeatures within that union. The artifacts follow the P3 dataset contract so the
 * production ranker trainer fits them unchanged. Only train/valid groups are exported; the test
 * family stays reserved for holdouts. Nothing here enters the APK or changes the engine.
 * zh-CN: 导出合并流水线实际排序的候选分布 (规则搜索 ∪ 冻结构造策略, 按规范计划去重), 标签与特征沿用
 * P3 契约, 供排序器训练工具直接拟合. 只导出训练/验证族, 测试族保留给留出集.
 */
class ConstructionMergedExportTask {
    @Test fun exportMergedCandidateDataset() {
        val output = System.getenv("CODEGEN_CONSTRUCTION_MERGED_OUTPUT")
        assumeTrue("Set CODEGEN_CONSTRUCTION_MERGED_OUTPUT to a fresh directory", output != null)
        fun directory(name: String) = File(requireNotNull(System.getenv(name)) { "Missing $name" }).canonicalFile
        ConstructionMergedExport.export(
            directory("CODEGEN_CONSTRUCTION_DATASET"), directory("CODEGEN_CONSTRUCTION_R3_FIT_BEST"),
            File(requireNotNull(output)).canonicalFile,
            requireNotNull(System.getenv("CODEGEN_GENERATOR_COMMIT")) { "Missing CODEGEN_GENERATOR_COMMIT" },
            System.getenv("CODEGEN_CONSTRUCTION_MERGED_ID") ?: "cg-ds-2026.10-1-merged",
        )
    }
}

internal object ConstructionMergedExport {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    const val TOOL = "ConstructionMergedExportTask"
    /** Evaluation counters with a generous wall clock: the candidate distribution must not depend on desktop timing. */
    val constructionBudget = ConstructionBudget(maxMillis = 30_000)
    private val artifactNames = listOf("labels.jsonl", "rules.jsonl", "features.csv", "features.json", "split-map.json", "export-manifest.json", "dataset-manifest.json")

    private class Union(val candidate: Verified<SnapshotNode>, val sources: MutableSet<String>)

    fun export(input: File, policyDirectory: File, output: File, generatorCommit: String, datasetId: String) {
        require(input.isDirectory && policyDirectory.isDirectory)
        require(Regex("[0-9a-f]{40}").matches(generatorCommit.lowercase())) { "CODEGEN_GENERATOR_COMMIT must be a full commit hash" }
        require(!output.exists() && output.mkdirs()) { "Use a fresh merged export output directory" }
        val policyFile = File(policyDirectory, "policy.json")
        val policy = ConstructionPolicyModel.read(policyFile)
        require(policy.scoreSemantics == ConstructionPolicyModel.BEST_SCORE_SEMANTICS) { "The merged export uses the best-continuation construction policy" }
        val policySha256 = hash(policyFile)
        val inputs = sortedMapOf<String, String>()
        fun record(file: File) {
            val relative = relativeInside(input, file)
            inputs.getOrPut(relative) { hash(file) }
        }
        val splitFile = File(input, "split-map.json").also(::record)
        val split = readJson(splitFile)
        require(split["format"].asInt == 1)
        val assignment = split.getAsJsonObject("assignment").entrySet().associate { it.key to it.value.asString }
        val groupMetadata = split.getAsJsonArray("groups").map { it.asJsonObject }.associateBy { it["groupId"].asString }
        require(groupMetadata.keys == assignment.keys)
        val selected = groupMetadata.values.filter { it["split"].asString in setOf("train", "valid") }.sortedBy { it["groupId"].asString }
        require(selected.isNotEmpty() && selected.any { it["split"].asString == "train" } && selected.any { it["split"].asString == "valid" })
        val snapshots = object : LinkedHashMap<File, SnapshotNode>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
        }
        val statistics = linkedMapOf("train" to IntArray(3), "valid" to IntArray(3), "test" to IntArray(3), "real-test" to IntArray(3))
        val sourceCounts = linkedMapOf("rule" to 0, "construction" to 0, "both" to 0)
        var candidateCount = 0
        var baselineAttempts = 0L
        var constructionAttempts = 0L
        val constructionStops = linkedMapOf<String, Int>()
        val familySplits = HashMap<String, String>()
        val splitGroups = JsonArray()
        File(output, "features.json").writeText(gson.toJson(FeatureExport.schemaJson()) + "\n", Charsets.UTF_8)
        File(output, "features.csv").bufferedWriter(Charsets.UTF_8).use { features ->
            features.write(FeatureExport.csvRow(listOf("split", "groupId", "planKey", "label") + FeatureSchema.names))
            File(output, "labels.jsonl").bufferedWriter(Charsets.UTF_8).use { labels ->
                File(output, "rules.jsonl").bufferedWriter(Charsets.UTF_8).use { rules ->
                    selected.forEachIndexed { position, item ->
                        val groupId = item["groupId"].asString
                        val splitName = item["split"].asString
                        val taskFile = File(item["task"].asString).canonicalFile
                        record(taskFile)
                        val group = TaskGroup.load(taskFile) { file -> record(file); snapshots.getOrPut(file.canonicalFile) { SnapshotNode.load(file) } }
                        group.requireLabelable()
                        require(group.groupId == groupId && group.source == "fixture" && group.screenFamily == item["screenFamily"].asString)
                        val previous = familySplits.putIfAbsent(group.screenFamily, splitName)
                        require(previous == null || previous == splitName) { "A screen family must not cross dataset splits" }
                        val capture = group.states[group.captureState]
                        val request = group.request.forState(capture)
                        val verifier = PlanVerifier(capture.root)
                        val baseline = CandidateSearch(capture.root, verifier = verifier).search(request)
                        val construction = ConstructionSearch(capture.root, budget = constructionBudget, policy = policy, verifier = verifier,
                            frontierMode = ConstructionFrontierMode.ANCHOR_PHASE_FAIR).search(request)
                        require(construction.stopReason !in setOf(ConstructionStopReason.DEADLINE, ConstructionStopReason.POLICY_FAILURE)) {
                            "Construction must complete under deterministic counters: $groupId ${construction.stopReason}"
                        }
                        baselineAttempts += baseline.attemptedCount
                        constructionAttempts += construction.stats.attemptedCount
                        constructionStops[construction.stopReason.name] = (constructionStops[construction.stopReason.name] ?: 0) + 1
                        val union = LinkedHashMap<String, Union>()
                        for (candidate in baseline.candidates.sortedBy { it.plan.planKey }) {
                            union.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) { Union(candidate, linkedSetOf()) }.sources += "rule"
                        }
                        for (candidate in construction.candidates.sortedBy { it.plan.planKey }) {
                            union.getOrPut(ConstructionSearch.canonicalKey(candidate.plan)) { Union(candidate, linkedSetOf()) }.sources += "construction"
                        }
                        val candidates = union.values.map { it.candidate }
                        require(candidates.map { it.plan.planKey }.distinct().size == candidates.size) { "Canonical deduplication must keep distinct plan keys" }
                        union.values.forEach { entry ->
                            val key = if (entry.sources.size == 2) "both" else entry.sources.single()
                            sourceCounts[key] = sourceCounts.getValue(key) + 1
                        }
                        val future = group.states.filterIndexed { index, _ -> index != group.captureState }
                            .map { state -> Triple(state, group.request.forState(state), PlanVerifier(state.root)) }
                        val ruleKey = RuleRanker().rank(request, candidates).firstOrNull()?.plan?.planKey
                        val featureGroup = if (candidates.isEmpty()) null else CandidateFeatures.prepare(request, candidates, verifier.tree)
                        var ruleSurvival: Double? = null
                        for (entry in union.values) {
                            val candidate = entry.candidate
                            val failures = future.mapNotNull { (state, futureRequest, futureVerifier) ->
                                when (val outcome = futureVerifier.verify(futureRequest, candidate.plan)) {
                                    is Verified -> null
                                    is Rejected -> Triple(state.stateId, state.mutations, outcome)
                                }
                            }
                            val survival = (future.size - failures.size).toDouble() / future.size
                            if (candidate.plan.planKey == ruleKey) ruleSurvival = survival
                            labels.write(gson.toJson(JsonObject().apply {
                                addProperty("format", 1); addProperty("groupId", groupId); addProperty("planKey", candidate.plan.planKey)
                                addProperty("family", candidate.plan.family.name); addProperty("survival", survival)
                                addProperty("ruleSelected", candidate.plan.planKey == ruleKey)
                                add("sources", JsonArray().apply { entry.sources.forEach(::add) })
                                add("failedStates", JsonArray().apply { failures.forEach { (stateId, mutations, rejection) -> add(JsonObject().apply {
                                    addProperty("stateId", stateId); add("mutations", JsonArray().apply { mutations.forEach(::add) })
                                    addProperty("reason", rejection.reason.name); addProperty("extraCount", rejection.extraCount); addProperty("missingCount", rejection.missingCount)
                                }) } })
                            }))
                            labels.write("\n")
                            val vector = requireNotNull(featureGroup).extract(candidate)
                            require(vector.size == FeatureSchema.names.size && vector.all { it.isFinite() }) { "Invalid feature vector" }
                            features.write(FeatureExport.csvRow(listOf(splitName, groupId, candidate.plan.planKey, survival.toString()) + vector.map(Float::toString)))
                        }
                        rules.write(gson.toJson(JsonObject().apply {
                            addProperty("format", 1); addProperty("groupId", groupId); addProperty("source", group.source); addProperty("app", group.app)
                            addProperty("screenFamily", group.screenFamily); addProperty("captureState", capture.stateId)
                            addProperty("stateCount", group.states.size); addProperty("targetCount", capture.targets.size)
                            addProperty("candidateCount", candidates.size)
                            addProperty("attemptedCount", baseline.attemptedCount + construction.stats.attemptedCount)
                            addProperty("budgetExhausted", baseline.budgetExhausted)
                            addProperty("rulePlanKey", ruleKey)
                            if (ruleSurvival == null) add("ruleSurvival", JsonNull.INSTANCE) else addProperty("ruleSurvival", ruleSurvival)
                            addProperty("closestRejection", baseline.closestRejection?.reason?.name)
                            addProperty("ruleCandidates", baseline.candidates.size); addProperty("constructionCandidates", construction.candidates.size)
                            addProperty("sharedCandidates", union.values.count { it.sources.size == 2 })
                            addProperty("constructionStop", construction.stopReason.name)
                            addProperty("constructionAttempts", construction.stats.attemptedCount)
                        }))
                        rules.write("\n")
                        val counts = statistics.getValue(splitName)
                        counts[0]++; counts[1] += candidates.size; if (candidates.isEmpty()) counts[2]++
                        candidateCount += candidates.size
                        splitGroups.add(JsonObject().apply {
                            addProperty("groupId", groupId); addProperty("split", splitName); addProperty("source", group.source)
                            addProperty("app", group.app); addProperty("screenFamily", group.screenFamily); addProperty("task", taskFile.path)
                        })
                        if (position % 100 == 0 || position == selected.lastIndex) println("Merged candidate export ${position + 1}/${selected.size}: candidates=$candidateCount")
                    }
                }
            }
        }
        inputs.forEach { (path, digest) -> require(hash(File(input, path)) == digest) { "Dataset changed while exporting: $path" } }
        require(hash(policyFile) == policySha256)
        writeJson(File(output, "split-map.json"), JsonObject().apply {
            addProperty("format", 1); addProperty("datasetId", datasetId)
            add("assignment", JsonObject().apply { selected.forEach { addProperty(it["groupId"].asString, it["split"].asString) } })
            add("groups", splitGroups)
        })
        val exportManifest = JsonObject().apply {
            addProperty("format", 1); addProperty("tool", TOOL); addProperty("toolVersion", 1); addProperty("taskFormat", TaskGroup.FORMAT)
            addProperty("labelDefinition", "verified-other-states-v1"); addProperty("generatorCommit", generatorCommit.lowercase())
            addProperty("datasetId", datasetId); addProperty("sourceDatasetId", split["datasetId"].asString)
            addProperty("groups", selected.size); addProperty("groupsWithCandidates", selected.size - statistics.values.sumOf { it[2] })
            addProperty("groupsWithoutCandidates", statistics.values.sumOf { it[2] }); addProperty("candidates", candidateCount)
            addProperty("candidateDistribution", "union of CandidateSearch and ANCHOR_PHASE_FAIR construction candidates, deduplicated by canonical plan; group-relative features computed within the union")
            add("candidateSources", gson.toJsonTree(sourceCounts))
            addProperty("baselineAttempts", baselineAttempts); addProperty("constructionAttempts", constructionAttempts)
            add("constructionStops", gson.toJsonTree(constructionStops))
            addProperty("constructionPolicySha256", policySha256); addProperty("constructionPolicySemantics", policy.scoreSemantics)
            add("constructionBudget", gson.toJsonTree(constructionBudget)); add("baselineBudget", gson.toJsonTree(SearchBudget()))
            addProperty("labelsSha256", hash(File(output, "labels.jsonl"))); addProperty("rulesSha256", hash(File(output, "rules.jsonl")))
            addProperty("featuresSha256", hash(File(output, "features.csv"))); addProperty("featureSchemaSha256", hash(File(output, "features.json")))
            addProperty("splitMapSha256", hash(File(output, "split-map.json")))
            addProperty("featureSchemaVersion", FeatureSchema.featureSchemaVersion); addProperty("featureNamesHash", FeatureSchema.namesHash)
            add("inputs", gson.toJsonTree(inputs)); add("sourceSha256", sourceIdentity())
        }
        writeJson(File(output, "export-manifest.json"), exportManifest)
        fun artifact(name: String) = JsonObject().apply {
            addProperty("status", "complete"); addProperty("file", name)
            addProperty("bytes", File(output, name).length()); addProperty("sha256", hash(File(output, name)))
        }
        writeJson(File(output, "dataset-manifest.json"), JsonObject().apply {
            addProperty("format", 1); addProperty("datasetId", datasetId); addProperty("generatorCommit", generatorCommit.lowercase())
            addProperty("sourceDatasetId", split["datasetId"].asString); addProperty("groups", selected.size); addProperty("candidates", candidateCount)
            addProperty("status", "complete")
            add("splits", JsonObject().apply { statistics.forEach { (name, counts) -> add(name, JsonObject().apply {
                addProperty("groups", counts[0]); addProperty("candidates", counts[1]); addProperty("groupsWithoutCandidates", counts[2])
            }) } })
            add("artifacts", JsonObject().apply {
                add("labels", artifact("labels.jsonl")); add("rules", artifact("rules.jsonl")); add("features", artifact("features.csv"))
                add("featureSchema", artifact("features.json")); add("export", artifact("export-manifest.json")); add("splitMap", artifact("split-map.json"))
            })
            addProperty("note", "Merged rule + construction candidate distribution of the train/valid families; the test family is reserved for construction holdouts")
        })
        require(artifactNames.all { File(output, it).isFile })
        println("Merged candidate export: ${selected.size} groups, $candidateCount candidates, sources=$sourceCounts, constructionStops=$constructionStops")
    }

    fun sourceIdentity(): JsonObject {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val base = "app/src/test/java/org/autojs/autojs/core/accessibility/codegen/construction/"
        val paths = listOf("ConstructionGrammar.kt", "ConstructionFeatures.kt", "ConstructionSearch.kt", "ConstructionPolicyModel.kt",
            "ConstructionMergedExportTask.kt").map { base + it } +
            File(root, "app/src/main/java/org/autojs/autojs/core/accessibility/codegen").walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.parentFile?.name != "store" }
                .map { it.relativeTo(root).invariantSeparatorsPath }.toList() + listOf(
                "app/src/main/java/org/autojs/autojs/core/accessibility/NodeInfoSnapshot.kt",
                "app/src/main/java/org/autojs/autojs/core/automator/UiNode.kt")
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
    private fun readJson(file: File) = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
    private fun writeJson(file: File, value: JsonObject) = file.writeText(gson.toJson(value) + "\n", Charsets.UTF_8)
    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(65536)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
