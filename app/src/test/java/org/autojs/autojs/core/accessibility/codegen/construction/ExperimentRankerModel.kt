package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.model.ObliviousTreeModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Offline experiment reader for a candidate ranker exported by `construction/export_ranker.py`:
 * the flat symmetric-tree JSON of the construction policy, but declared for the host's complete
 * candidate feature schema. It is not an installable `.ajcgm` package and is never signed.
 * zh-CN: 离线实验用的候选排序器读取器, 与构造策略共用扁平对称树 JSON, 但按宿主完整候选特征 schema 校验.
 */
class ExperimentRankerModel private constructor(val model: ObliviousTreeModel, val scoreSemantics: String, val featureSchemaVersion: Int) {
    companion object {
        const val ROLE = "candidate-ranker"
        const val SCORE_SEMANTICS = "yetirank-raw-v1"
        private const val MAX_FILE_BYTES = 32 * 1024 * 1024

        fun read(file: File): ExperimentRankerModel {
            require(file.length() in 1..MAX_FILE_BYTES.toLong()) { "Ranker file size is outside the experiment limit" }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(file.readBytes())).toString()
            val document = JsonParser.parseString(text).asJsonObject
            require(document.keySet() == setOf("format", "role", "featureSchemaVersion", "featureNamesHash", "featureCount", "scoreSemantics", "model")) {
                "Unexpected ranker fields: ${document.keySet()}"
            }
            require(document["format"].asInt == 1) { "Unsupported ranker format" }
            require(document["role"].asString == ROLE) { "Model does not declare the candidate-ranker role" }
            val version = document["featureSchemaVersion"].asInt
            require(version in FeatureSchema.SUPPORTED_SCHEMA) { "Ranker feature schema version is not supported" }
            require(document["featureNamesHash"].asString == FeatureSchema.namesHash(version)) { "Ranker feature names hash differs" }
            require(document["featureCount"].asInt == FeatureSchema.names(version).size) { "Ranker feature count differs" }
            require(document["scoreSemantics"].asString == SCORE_SEMANTICS) { "Ranker score semantics are not supported" }
            val model = document.getAsJsonObject("model")
            require(model.keySet() == setOf("depths", "splitOffsets", "leafOffsets", "featureIndices", "thresholds", "leafValues", "bias", "scale"))
            fun ints(name: String) = model.getAsJsonArray(name).map { it.asInt }.toIntArray()
            fun floats(name: String) = model.getAsJsonArray(name).map { it.asDouble.toFloat().also { value -> require(value.isFinite()) } }.toFloatArray()
            val depths = ints("depths").map { require(it in 1..8); it.toByte() }.toByteArray()
            val bias = model["bias"].asDouble
            val scale = model["scale"].asDouble
            require(bias.isFinite() && scale.isFinite())
            return ExperimentRankerModel(ObliviousTreeModel(FeatureSchema.names(version).size, depths, ints("splitOffsets"), ints("leafOffsets"),
                ints("featureIndices"), floats("thresholds"), floats("leafValues"), bias, scale), SCORE_SEMANTICS, version)
        }
    }
}

class ExperimentRankerModelTest {
    @Test
    fun rejectsConstructionPolicyRoleAndForeignSchemas() {
        val policy = File.createTempFile("policy", ".json").apply { deleteOnExit() }
        policy.writeText("""{"format":1,"role":"construction-policy","featureSchemaVersion":${FeatureSchema.featureSchemaVersion},"featureNamesHash":"${FeatureSchema.namesHash}","featureCount":${FeatureSchema.names.size},"scoreSemantics":"yetirank-raw-v1","model":{"depths":[1],"splitOffsets":[0],"leafOffsets":[0],"featureIndices":[0],"thresholds":[0.0],"leafValues":[1.0,2.0],"bias":0.0,"scale":1.0}}""")
        assertThrows(IllegalArgumentException::class.java) { ExperimentRankerModel.read(policy) }
        val ranker = File.createTempFile("ranker", ".json").apply { deleteOnExit() }
        ranker.writeText("""{"format":1,"role":"candidate-ranker","featureSchemaVersion":${FeatureSchema.featureSchemaVersion},"featureNamesHash":"${FeatureSchema.namesHash}","featureCount":${FeatureSchema.names.size},"scoreSemantics":"yetirank-raw-v1","model":{"depths":[1],"splitOffsets":[0],"leafOffsets":[0],"featureIndices":[0],"thresholds":[0.0],"leafValues":[1.0,2.0],"bias":0.5,"scale":2.0}}""")
        val model = ExperimentRankerModel.read(ranker)
        assertEquals(0.5 + 2.0 * 2.0, model.model.score(FloatArray(FeatureSchema.names.size).also { it[0] = 1f }), 0.0)
        assertEquals(0.5 + 2.0 * 1.0, model.model.score(FloatArray(FeatureSchema.names.size)), 0.0)
        ranker.writeText(ranker.readText().replace("\"featureNamesHash\":\"${FeatureSchema.namesHash}\"", "\"featureNamesHash\":\"0000000000000000\""))
        assertThrows(IllegalArgumentException::class.java) { ExperimentRankerModel.read(ranker) }
    }

    @Test
    fun externalRankerMatchesEveryExportedCandidateAndGroupFirstChoice() {
        val configured = System.getenv("CODEGEN_EXPERIMENT_RANKER_DIR")?.takeIf(String::isNotBlank)
        assumeTrue("Set CODEGEN_EXPERIMENT_RANKER_DIR for exported ranker parity", configured != null)
        val directory = File(requireNotNull(configured)).canonicalFile
        val log = JsonParser.parseString(File(directory, "export-log.json").readText(Charsets.UTF_8)).asJsonObject
        val modelFile = File(directory, "ranker.json")
        val parityFile = File(directory, "parity.jsonl")
        assertEquals(log["modelSha256"].asString, sha256(modelFile))
        assertEquals(log["paritySha256"].asString, sha256(parityFile))
        val ranker = ExperimentRankerModel.read(modelFile)
        val groups = LinkedHashMap<String, MutableList<Triple<String, Double, Double>>>()
        var rows = 0
        var maximum = 0.0
        parityFile.useLines(Charsets.UTF_8) { lines ->
            lines.forEach { line ->
                val record = JsonParser.parseString(line).asJsonObject
                val features = record.getAsJsonArray("features").map { it.asFloat }.toFloatArray()
                assertEquals(FeatureSchema.names.size, features.size)
                val expected = record["expectedScore"].asDouble
                val actual = ranker.model.score(features)
                maximum = maxOf(maximum, abs(expected - actual))
                assertEquals(expected, actual, 1e-4)
                groups.getOrPut(record["groupId"].asString, ::ArrayList).add(Triple(record["planKey"].asString, expected, actual))
                rows++
            }
        }
        assertEquals(log["parityRows"].asInt, rows)
        assertEquals(log["parityGroups"].asInt, groups.size)
        for ((group, members) in groups) {
            val expectedBest = members.maxOf { it.second }
            val actualBest = members.maxOf { it.third }
            assertEquals("Group first choice differs in $group", members.filter { it.second == expectedBest }.map { it.first }.toSet(),
                members.filter { it.third == actualBest }.map { it.first }.toSet())
        }
        assertTrue(groups.isNotEmpty())
        println("Experiment ranker parity: $rows candidates, ${groups.size} groups, max score difference=$maximum, first choices identical")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
