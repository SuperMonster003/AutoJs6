package org.autojs.autojs.core.accessibility.codegen.dataset

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.features.CandidateFeatures
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.search.CandidateSearch
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CodeGenQueueExporterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun exportsOnlySingleStateRealGroupsWithTheSameKotlinFeaturesAndNoLabels() {
        val root = temporary.newFolder()
        val input = File(root, "tasks").apply { mkdir() }
        val real = task(input, "single", "real", 1)
        task(input, "labelled", "real", 3)
        task(input, "fixture", "fixture", 1)
        val output = File(root, "output")
        val summary = QueueCandidateExporter.export(input, output, root, "abc1234")
        assertEquals(1, summary.groups)
        assertEquals(1, summary.skippedAlreadyAnnotated)
        assertEquals(1, summary.skippedNonReal)
        assertTrue(summary.candidates > 0)
        val record = JsonParser.parseString(File(output, "candidates.jsonl").readText()).asJsonObject
        assertEquals(real.canonicalPath, record["task"].asString)
        assertEquals(64, record["taskSha256"].asString.length)
        assertEquals(64, record["captureSnapshotSha256"].asString.length)
        assertEquals(1, record["stateCount"].asInt)
        val candidates = record.getAsJsonArray("candidates").map { it.asJsonObject }
        assertEquals((candidates.indices).toList(), candidates.map { it["ruleRank"].asInt }.sorted())
        val group = TaskGroup.load(real)
        val capture = group.states[0]
        val request = group.request.forState(capture)
        val verifier = PlanVerifier(capture.root)
        val verified = CandidateSearch(capture.root, verifier = verifier).search(request).candidates
        val features = CandidateFeatures.prepare(request, verified, verifier.tree)
        candidates.forEach { candidate ->
            assertFalse(candidate.has("survival"))
            val vector = candidate.getAsJsonArray("features").map { it.asFloat }.toFloatArray()
            assertEquals(FeatureSchema.names.size, vector.size)
            assertArrayEquals(features.extract(verified.single { it.plan.planKey == candidate["planKey"].asString }), vector, 0f)
        }
        assertFalse(File(output, "labels.jsonl").exists())
    }

    @Test
    fun repeatedExportIsByteIdenticalAndNeverOverwritesAnExistingDirectory() {
        val root = temporary.newFolder()
        val input = File(root, "tasks").apply { mkdir() }
        task(input, "single", "real", 1)
        val first = File(root, "first")
        val second = File(root, "second")
        QueueCandidateExporter.export(input, first, root)
        QueueCandidateExporter.export(input, second, root)
        for (name in listOf("features.json", "candidates.jsonl", "queue-manifest.json")) {
            assertEquals(File(first, name).readText(), File(second, name).readText())
        }
        assertThrows(IllegalArgumentException::class.java) { QueueCandidateExporter.export(input, first, root) }
    }

    @Test
    fun missingCaptureTargetsAndPublicOutputsNeverProduceQueueArtifacts() {
        val root = temporary.newFolder()
        val input = File(root, "tasks").apply { mkdir() }
        val file = task(input, "single", "real", 1)
        val json = JsonParser.parseString(file.readText()).asJsonObject
        json.getAsJsonArray("states")[0].asJsonObject.add("targets", JsonArray())
        file.writeText(json.toString())
        val output = File(root, "output")
        assertThrows(IllegalArgumentException::class.java) { QueueCandidateExporter.export(input, output, root) }
        assertFalse(output.exists())
        assertFalse(root.listFiles()!!.any { it.name.startsWith(".codegen-queue-") })
        assertThrows(IllegalArgumentException::class.java) { QueueCandidateExporter.export(input, temporary.newFolder(), root) }
    }

    private fun task(input: File, name: String, source: String, states: Int): File {
        val fixture = File(requireNotNull(javaClass.getResource("/codegen/dataset/shuffle/shuffle.cgtask.json")).toURI())
        val directory = File(input, name)
        requireNotNull(fixture.parentFile).copyRecursively(directory)
        return File(directory, "shuffle.cgtask.json").also { file ->
            val json = JsonParser.parseString(file.readText()).asJsonObject
            json.addProperty("groupId", name)
            json.addProperty("source", source)
            val original = json.getAsJsonArray("states")
            json.add("states", JsonArray().apply { original.take(states).forEach(::add) })
            file.writeText(json.toString())
        }
    }
}
