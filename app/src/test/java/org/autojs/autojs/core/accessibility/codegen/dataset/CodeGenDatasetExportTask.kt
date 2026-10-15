package org.autojs.autojs.core.accessibility.codegen.dataset

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.NodeTree
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.features.CandidateFeatures
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.search.SearchResult
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Explicitly enabled offline job; ordinary host tests neither scan nor export local layouts. */
class CodeGenDatasetExportTask {
    @Test
    fun exportDataset() {
        val directory = System.getenv("CODEGEN_DATASET_DIR")?.takeIf { it.isNotBlank() }
        assumeTrue("Set CODEGEN_DATASET_DIR to run the offline dataset exporter", directory != null)
        val input = File(requireNotNull(directory))
        val output = System.getenv("CODEGEN_DATASET_OUTPUT_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: input
        val splits = System.getenv("CODEGEN_SPLIT_MAP")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(input, "split-map.json")
        val summary = DatasetExporter.export(input, output, System.getenv("CODEGEN_GENERATOR_COMMIT"), splits)
        println("Codegen dataset: ${summary.groups} groups, ${summary.candidates} candidates, ${summary.groupsWithoutCandidates} groups without candidates")
    }
}

/** Test-source tooling deliberately stays out of the application APK. */
internal object DatasetExporter {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    private val artifactNames = listOf("labels.jsonl", "rules.jsonl", "features.csv", "features.json", "export-manifest.json")
    private val REAL_SPLITS = setOf("real-train", "real-test")

    data class FailedState(val stateId: String, val mutations: List<String>, val rejection: Rejected)
    data class Label(
        val candidate: Verified<SnapshotNode>,
        val survival: Double,
        val failedStates: List<FailedState>,
        val ruleSelected: Boolean,
    )
    data class LabeledGroup(val group: TaskGroup, val search: SearchResult<SnapshotNode>, val labels: List<Label>, val captureTree: NodeTree<SnapshotNode>)
    data class Summary(val groups: Int, val candidates: Int, val groupsWithoutCandidates: Int)
    private data class InputFile(val file: File, val sha256: String)

    fun label(group: TaskGroup): LabeledGroup {
        group.requireLabelable()
        val capture = group.states[group.captureState]
        val request = group.request.forState(capture)
        val captureVerifier = PlanVerifier(capture.root)
        val search = CandidateSearch(capture.root, verifier = captureVerifier).search(request)
        val ruleKey = RuleRanker().rank(request, search.candidates).firstOrNull()?.plan?.planKey
        val future = group.states.filterIndexed { index, _ -> index != group.captureState }.map { state ->
            Triple(state, group.request.forState(state), PlanVerifier(state.root))
        }
        val labels = search.candidates.sortedBy { it.plan.planKey }.map { candidate ->
            val failures = future.mapNotNull { (state, futureRequest, verifier) ->
                when (val outcome = verifier.verify(futureRequest, candidate.plan)) {
                    is Verified -> null
                    is Rejected -> FailedState(state.stateId, state.mutations, outcome)
                }
            }
            Label(candidate, (future.size - failures.size).toDouble() / future.size, failures, candidate.plan.planKey == ruleKey)
        }
        return LabeledGroup(group, search, labels, captureVerifier.tree)
    }

    fun export(
        inputDirectory: File,
        outputDirectory: File = inputDirectory,
        generatorCommit: String? = null,
        splitFile: File = File(inputDirectory, "split-map.json"),
    ): Summary {
        val input = inputDirectory.canonicalFile
        require(input.isDirectory) { "CODEGEN_DATASET_DIR must be an existing directory" }
        val commit = generatorCommit?.takeIf { it.isNotBlank() }
        require(commit == null || Regex("[0-9a-fA-F]{7,64}").matches(commit)) { "CODEGEN_GENERATOR_COMMIT must be a commit hash" }
        val inputs = sortedMapOf<String, InputFile>()
        fun recordInput(file: File) {
            val relative = file.relativeTo(input).invariantSeparatorsPath
            inputs.getOrPut(relative) { InputFile(file, sha256(file)) }
        }
        val tasks = input.walkTopDown().filter { it.isFile && it.name.endsWith(".cgtask.json") }.map { file ->
            require(file.canonicalFile.toPath().startsWith(input.toPath())) { "Task file must stay inside the dataset directory" }
            recordInput(file)
            TaskGroup.readGroupId(file) to file
        }.toList().sortedBy { it.first }
        require(tasks.isNotEmpty()) { "Dataset has no .cgtask.json tasks" }
        require(tasks.map { it.first }.distinct().size == tasks.size) { "Dataset groupId values must be unique" }
        require(splitFile.isFile) { "Create split-map.json with dataset.split before exporting features, or set CODEGEN_SPLIT_MAP" }
        val splitSha256 = sha256(splitFile)
        val splitJson = JsonParser.parseString(splitFile.readText(Charsets.UTF_8)).asJsonObject
        require(splitJson["format"]?.toString() == "1") { "Unknown split-map format" }
        val datasetId = splitJson["datasetId"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        require(!datasetId.isNullOrBlank()) { "split-map requires datasetId" }
        val assignmentJson = splitJson.getAsJsonObject("assignment") ?: error("split-map requires assignment")
        val assignment = assignmentJson.entrySet().associate { (id, split) ->
            require(split.isJsonPrimitive && split.asJsonPrimitive.isString && split.asString in setOf("train", "valid", "test", "real-train", "real-test")) { "Unknown split for $id" }
            id to split.asString
        }
        require(assignment.keys == tasks.map { it.first }.toSet()) { "split-map must contain exactly the exported task groups" }
        val output = outputDirectory.canonicalFile
        require(output.isDirectory || output.mkdirs()) { "Cannot create dataset output directory" }
        artifactNames.forEach { name -> require(!File(output, name).exists() || File(output, name).isFile) { "Output $name is not a regular file" } }
        val staging = Files.createTempDirectory(output.toPath(), ".codegen-export-").toFile()
        var preserveStaging = false
        try {
            val snapshots = object : LinkedHashMap<File, SnapshotNode>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
            }
            var candidateCount = 0
            var groupsWithoutCandidates = 0
            val familySplits = HashMap<String, String>()
            File(staging, "features.json").writeText(gson.toJson(FeatureExport.schemaJson()) + "\n", Charsets.UTF_8)
            File(staging, "features.csv").bufferedWriter(Charsets.UTF_8).use { features ->
                features.write(FeatureExport.csvRow(listOf("split", "groupId", "planKey", "label") + FeatureSchema.names))
                File(staging, "labels.jsonl").bufferedWriter(Charsets.UTF_8).use { labels ->
                    File(staging, "rules.jsonl").bufferedWriter(Charsets.UTF_8).use { rules ->
                        tasks.forEach { (groupId, file) ->
                            val group = TaskGroup.load(file) { snapshot ->
                                recordInput(snapshot)
                                snapshots.getOrPut(snapshot) { SnapshotNode.load(snapshot) }
                            }
                            require(group.groupId == groupId) { "Task identity changed during export" }
                            val split = assignment.getValue(groupId)
                            // Real applications are split by app into real-train and real-test (roadmap D25).
                            // zh-CN: 真实应用按应用划分为 real-train 与 real-test (路线图 D25).
                            if (group.source == "real") {
                                require(split in REAL_SPLITS) { "Real application groups belong only in real-train or real-test" }
                                FeatureExport.requirePrivate(file)
                                FeatureExport.requirePrivate(output)
                            } else {
                                require(split !in REAL_SPLITS) { "Fixture groups cannot enter real-train or real-test" }
                                val previous = familySplits.putIfAbsent(group.screenFamily, split)
                                require(previous == null || previous == split) { "A screen family must not cross dataset splits" }
                            }
                            val labeled = label(group)
                            val captureRequest = group.request.forState(group.states[group.captureState])
                            val featureGroup = if (labeled.labels.isEmpty()) null else CandidateFeatures.prepare(captureRequest, labeled.search.candidates, labeled.captureTree)
                            candidateCount += labeled.labels.size
                            if (labeled.labels.isEmpty()) groupsWithoutCandidates++
                            labeled.labels.forEach { label ->
                                labels.write(gson.toJson(labelJson(group, label)))
                                labels.write("\n")
                                val vector = requireNotNull(featureGroup).extract(label.candidate)
                                require(vector.size == FeatureSchema.names.size && vector.all { it.isFinite() }) { "Invalid feature vector" }
                                features.write(FeatureExport.csvRow(listOf(split, groupId, label.candidate.plan.planKey, label.survival.toString()) + vector.map(Float::toString)))
                            }
                            rules.write(gson.toJson(ruleJson(labeled)))
                            rules.write("\n")
                        }
                    }
                }
            }
            val summary = Summary(tasks.size, candidateCount, groupsWithoutCandidates)
            val inputDigest = MessageDigest.getInstance("SHA-256")
            inputs.forEach { (path, entry) ->
                require(sha256(entry.file) == entry.sha256) { "Dataset input changed during export: $path" }
                inputDigest.update(path.toByteArray(Charsets.UTF_8))
                inputDigest.update(0.toByte())
                inputDigest.update(entry.sha256.toByteArray(Charsets.US_ASCII))
                inputDigest.update('\n'.code.toByte())
            }
            require(sha256(splitFile) == splitSha256) { "Dataset split changed during export" }
            val manifest = JsonObject().apply {
                addProperty("format", 1)
                addProperty("tool", "CodeGenDatasetExportTask")
                addProperty("toolVersion", 1)
                addProperty("taskFormat", TaskGroup.FORMAT)
                addProperty("labelDefinition", "verified-other-states-v1")
                addProperty("generatorCommit", commit?.lowercase())
                addProperty("datasetId", datasetId)
                addProperty("groups", summary.groups)
                addProperty("groupsWithCandidates", summary.groups - summary.groupsWithoutCandidates)
                addProperty("groupsWithoutCandidates", summary.groupsWithoutCandidates)
                addProperty("candidates", summary.candidates)
                addProperty("snapshotFiles", inputs.size - tasks.size)
                addProperty("inputSha256", inputDigest.digest().hex())
                addProperty("labelsSha256", sha256(File(staging, "labels.jsonl")))
                addProperty("rulesSha256", sha256(File(staging, "rules.jsonl")))
                addProperty("featuresSha256", sha256(File(staging, "features.csv")))
                addProperty("featureSchemaSha256", sha256(File(staging, "features.json")))
                addProperty("splitMapSha256", splitSha256)
                addProperty("featureSchemaVersion", FeatureSchema.featureSchemaVersion)
                addProperty("featureNamesHash", FeatureSchema.namesHash)
                add("searchBudget", JsonObject().apply {
                    addProperty("maxAttempts", 2_000)
                    addProperty("maxVerified", 200)
                })
            }
            File(staging, "export-manifest.json").writeText(gson.toJson(manifest) + "\n", Charsets.UTF_8)
            val backups = ArrayList<String>()
            val installed = ArrayList<String>()
            try {
                artifactNames.forEach { name ->
                    val destination = File(output, name)
                    if (destination.exists()) {
                        move(destination, File(staging, "$name.previous"))
                        backups.add(name)
                    }
                    move(File(staging, name), destination)
                    installed.add(name)
                }
            } catch (failure: Exception) {
                try {
                    installed.asReversed().forEach { Files.deleteIfExists(File(output, it).toPath()) }
                    backups.asReversed().forEach { move(File(staging, "$it.previous"), File(output, it)) }
                } catch (rollback: Exception) {
                    preserveStaging = true
                    failure.addSuppressed(IllegalStateException("Retained recovery files at $staging", rollback))
                }
                throw failure
            }
            return summary
        } finally {
            // Only this exporter-created directory is removed. On rollback failure keep backups.
            if (!preserveStaging) staging.deleteRecursively()
        }
    }

    private fun labelJson(group: TaskGroup, label: Label) = JsonObject().apply {
        addProperty("format", 1)
        addProperty("groupId", group.groupId)
        addProperty("planKey", label.candidate.plan.planKey)
        addProperty("family", label.candidate.plan.family.name)
        addProperty("survival", label.survival)
        addProperty("ruleSelected", label.ruleSelected)
        add("failedStates", JsonArray().apply {
            label.failedStates.forEach { failed ->
                add(JsonObject().apply {
                    addProperty("stateId", failed.stateId)
                    add("mutations", JsonArray().apply { failed.mutations.forEach(::add) })
                    addProperty("reason", failed.rejection.reason.name)
                    addProperty("extraCount", failed.rejection.extraCount)
                    addProperty("missingCount", failed.rejection.missingCount)
                })
            }
        })
    }

    private fun ruleJson(labeled: LabeledGroup) = JsonObject().apply {
        val group = labeled.group
        val selected = labeled.labels.singleOrNull { it.ruleSelected }
        addProperty("format", 1)
        addProperty("groupId", group.groupId)
        addProperty("source", group.source)
        addProperty("app", group.app)
        addProperty("screenFamily", group.screenFamily)
        addProperty("captureState", group.states[group.captureState].stateId)
        addProperty("stateCount", group.states.size)
        addProperty("targetCount", group.states[group.captureState].targets.size)
        addProperty("candidateCount", labeled.labels.size)
        addProperty("attemptedCount", labeled.search.attemptedCount)
        addProperty("budgetExhausted", labeled.search.budgetExhausted)
        addProperty("rulePlanKey", selected?.candidate?.plan?.planKey)
        if (selected == null) add("ruleSurvival", JsonNull.INSTANCE) else addProperty("ruleSurvival", selected.survival)
        addProperty("closestRejection", labeled.search.closestRejection?.reason?.name)
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}
