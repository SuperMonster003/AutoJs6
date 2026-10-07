package org.autojs.autojs.core.accessibility.codegen.dataset

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.SnapshotNode
import org.autojs.autojs.core.accessibility.codegen.features.CandidateFeatures
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.rank.RuleRanker
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Explicit, private candidate export for active learning. No future truth or survival is guessed. */
class CodeGenQueueExportTask {
    @Test
    fun exportQueueCandidates() {
        val directory = System.getenv("CODEGEN_QUEUE_DIR")?.takeIf { it.isNotBlank() }
        assumeTrue("Set CODEGEN_QUEUE_DIR to export private single-state candidates", directory != null)
        val output = requireNotNull(System.getenv("CODEGEN_QUEUE_OUTPUT_DIR")?.takeIf { it.isNotBlank() }) {
            "Set CODEGEN_QUEUE_OUTPUT_DIR to a new directory under CODEGEN_PRIVATE_DIR"
        }
        val privateRoot = requireNotNull(System.getenv("CODEGEN_PRIVATE_DIR")?.takeIf { it.isNotBlank() }) { "Set CODEGEN_PRIVATE_DIR" }
        val summary = QueueCandidateExporter.export(File(requireNotNull(directory)), File(output), File(privateRoot), System.getenv("CODEGEN_GENERATOR_COMMIT"))
        println("Queue candidates: ${summary.groups} groups, ${summary.candidates} candidates, ${summary.skippedAlreadyAnnotated} already annotated")
    }
}

internal object QueueCandidateExporter {
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
    data class Summary(val groups: Int, val candidates: Int, val skippedAlreadyAnnotated: Int, val skippedNonReal: Int)
    private data class InputFile(val file: File, val sha256: String)

    fun export(inputDirectory: File, outputDirectory: File, privateRoot: File, generatorCommit: String? = null): Summary {
        val input = inputDirectory.canonicalFile
        val output = outputDirectory.canonicalFile
        FeatureExport.requirePrivate(input, privateRoot)
        FeatureExport.requirePrivate(output, privateRoot)
        require(input.isDirectory) { "Queue input must be an existing task directory" }
        require(!output.exists()) { "Queue output already exists; choose a new directory" }
        require(generatorCommit == null || Regex("[0-9a-fA-F]{7,64}").matches(generatorCommit)) { "Invalid generator commit" }
        val inputs = sortedMapOf<String, InputFile>()
        fun record(file: File): String {
            val canonical = file.canonicalFile
            require(canonical.toPath().startsWith(input.toPath())) { "Queue input must stay inside its task directory" }
            FeatureExport.requirePrivate(canonical, privateRoot)
            return inputs.getOrPut(canonical.relativeTo(input).invariantSeparatorsPath) { InputFile(canonical, sha256(canonical)) }.sha256
        }
        val visited = HashSet<File>()
        val files = input.walkTopDown().onEnter { directory ->
            directory.canonicalFile.let { it.toPath().startsWith(input.toPath()) && visited.add(it) }
        }.filter { it.isFile && it.name.endsWith(".cgtask.json") }.map { file ->
            record(file)
            TaskGroup.readGroupId(file) to file.canonicalFile
        }.toList().sortedBy { it.first }
        require(files.isNotEmpty()) { "No tasks with confirmed capture targets were found" }
        require(files.map { it.first }.distinct().size == files.size) { "Queue groupId values must be unique" }
        val parent = requireNotNull(output.parentFile)
        require(parent.isDirectory || parent.mkdirs()) { "Cannot create queue output parent" }
        val staging = Files.createTempDirectory(parent.toPath(), ".codegen-queue-").toFile()
        try {
            var groupCount = 0
            var candidateCount = 0
            var skippedAnnotated = 0
            var skippedNonReal = 0
            val snapshots = object : LinkedHashMap<File, SnapshotNode>(16, .75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, SnapshotNode>?): Boolean = size > 16
            }
            File(staging, "features.json").writeText(gson.toJson(FeatureExport.schemaJson()) + "\n", Charsets.UTF_8)
            File(staging, "candidates.jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
                files.forEach { (groupId, file) ->
                    val group = TaskGroup.load(file) { snapshot ->
                        record(snapshot)
                        snapshots.getOrPut(snapshot) { SnapshotNode.load(snapshot) }
                    }
                    require(group.groupId == groupId) { "Queue task identity changed during export" }
                    if (group.source != "real") {
                        skippedNonReal++
                        return@forEach
                    }
                    // TaskGroup states always have confirmed targets. Two states already suffice
                    // for survival labeling, so they do not belong in the collection-priority queue.
                    if (group.states.size > 1) {
                        skippedAnnotated++
                        return@forEach
                    }
                    val capture = group.states[group.captureState]
                    val request = group.request.forState(capture)
                    val verifier = PlanVerifier(capture.root)
                    val search = CandidateSearch(capture.root, verifier = verifier).search(request)
                    val ruleOrder = RuleRanker().rank(request, search.candidates).withIndex().associate { it.value.plan.planKey to it.index }
                    val prepared = if (search.candidates.isEmpty()) null else CandidateFeatures.prepare(request, search.candidates, verifier.tree)
                    val recordJson = JsonObject().apply {
                        addProperty("format", 1)
                        addProperty("groupId", group.groupId)
                        addProperty("source", group.source)
                        addProperty("app", group.app)
                        addProperty("screenFamily", group.screenFamily)
                        addProperty("task", file.absolutePath)
                        addProperty("taskSha256", record(file))
                        addProperty("captureSnapshotSha256", record(File(requireNotNull(file.parentFile), capture.snapshot)))
                        addProperty("stateCount", 1)
                        addProperty("candidateCount", search.candidates.size)
                        add("candidates", JsonArray().apply {
                            search.candidates.sortedBy { it.plan.planKey }.forEach { candidate ->
                                add(JsonObject().apply {
                                    addProperty("planKey", candidate.plan.planKey)
                                    addProperty("ruleRank", ruleOrder.getValue(candidate.plan.planKey))
                                    val vector = requireNotNull(prepared).extract(candidate)
                                    require(vector.size == FeatureSchema.names.size && vector.all(Float::isFinite)) { "Invalid queue feature vector" }
                                    add("features", JsonArray().apply { vector.forEach(::add) })
                                })
                            }
                        })
                    }
                    writer.write(gson.toJson(recordJson))
                    writer.write("\n")
                    groupCount++
                    candidateCount += search.candidates.size
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            inputs.forEach { (path, entry) ->
                require(sha256(entry.file) == entry.sha256) { "Queue input changed during export" }
                digest.update(path.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(entry.sha256.toByteArray(Charsets.US_ASCII))
                digest.update('\n'.code.toByte())
            }
            val summary = Summary(groupCount, candidateCount, skippedAnnotated, skippedNonReal)
            File(staging, "queue-manifest.json").writeText(gson.toJson(JsonObject().apply {
                addProperty("format", 1)
                addProperty("tool", "CodeGenQueueExportTask")
                addProperty("toolVersion", 1)
                addProperty("generatorCommit", generatorCommit)
                addProperty("groups", groupCount)
                addProperty("candidates", candidateCount)
                addProperty("skippedAlreadyAnnotated", skippedAnnotated)
                addProperty("skippedNonReal", skippedNonReal)
                addProperty("featureSchemaVersion", FeatureSchema.featureSchemaVersion)
                addProperty("featureNamesHash", FeatureSchema.namesHash)
                addProperty("candidatesSha256", sha256(File(staging, "candidates.jsonl")))
                addProperty("featureSchemaSha256", sha256(File(staging, "features.json")))
                addProperty("inputSha256", digest.digest().hex())
            }) + "\n", Charsets.UTF_8)
            // Output is required to be new; a successful rename publishes all artifacts together.
            Files.move(staging.toPath(), output.toPath())
            return summary
        } finally {
            if (staging.exists()) {
                check(staging.canonicalFile.parentFile == parent) { "Unexpected queue staging location" }
                staging.deleteRecursively()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().hex()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}
