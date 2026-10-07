package org.autojs.autojs.core.accessibility.codegen.construction

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.abs

class ConstructionPolicyModelTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun goldenTreesPreserveStrictThresholdsFloatLeavesAndUnclippedScores() {
        val policy = parse(document())
        val cases = listOf(
            Triple(0f, 1f, 12.5),
            Triple(-2f, 1f, -2.5),
            Triple(1f, 2f, 5.5),
            Triple(0f, 2f, 7.5),
            Triple(1f, 1f, 8.5),
        )
        for ((first, second, expected) in cases) {
            val features = FloatArray(ConstructionFeatures.names.size).also { it[0] = first; it[1] = second }
            assertEquals(expected, policy.score(features), 0.0)
            assertEquals(policy.model.score(features), policy.score(features), 0.0)
        }
        assertEquals(2, policy.model.treeCount)
        assertEquals(2, policy.model.maxDepth)
        assertEquals(ConstructionFeatures.names.size, policy.model.featureCount)

        val fractional = document().apply {
            getAsJsonObject("model").apply {
                add("leafValues", floats(.1, .2, .3, .4, .5, .6))
                addProperty("bias", .1234567890123)
                addProperty("scale", .9876543210987)
            }
        }
        val features = FloatArray(ConstructionFeatures.names.size)
        val expected = .1234567890123 + .9876543210987 * (.1f.toDouble() + .6f.toDouble())
        assertEquals(expected, parse(fractional).score(features), 0.0)
    }

    @Test
    fun inputVectorsKeepTheSharedInterpreterValidation() {
        val policy = parse(document())
        assertThrows(IllegalArgumentException::class.java) { policy.score(FloatArray(1)) }
        assertThrows(IllegalArgumentException::class.java) {
            policy.score(FloatArray(ConstructionFeatures.names.size).also { it[0] = Float.NaN })
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.score(FloatArray(ConstructionFeatures.names.size).also { it[1] = Float.POSITIVE_INFINITY })
        }
    }

    @Test
    fun rejectsWrongRoleGrammarSchemaNamesCountAndScoreMeaning() {
        val changes = listOf<JsonObject.() -> Unit>(
            { addProperty("format", 2) },
            { addProperty("role", "ranker") },
            { addProperty("grammarVersion", ConstructionFeatures.grammarVersion + 1) },
            { addProperty("featureSchemaVersion", ConstructionFeatures.version + 1) },
            { addProperty("featureNamesHash", "0".repeat(64)) },
            { addProperty("featureCount", ConstructionFeatures.names.size + 1) },
            { addProperty("scoreSemantics", "sum-of-action-logits") },
            { remove("role") },
            { addProperty("unrecognized", true) },
            { addProperty("format", "1") },
            { addProperty("format", 1.5) },
        )
        changes.forEach { change ->
            assertThrows(IllegalArgumentException::class.java) { parse(document().apply(change)) }
        }
    }

    @Test
    fun rejectsMalformedTreeArraysAndBoundsBeforeNarrowingIntegers() {
        val changes = listOf<JsonObject.() -> Unit>(
            { add("depths", ints()) },
            { add("depths", ints(0, 1)) },
            { add("depths", ints(9, 1)) },
            { add("depths", ints(257, 1)) },
            { add("depths", ints(*IntArray(4097) { 1 })) },
            { add("splitOffsets", ints(0)) },
            { add("splitOffsets", ints(0, 1)) },
            { add("leafOffsets", ints(0, 3)) },
            { add("featureIndices", ints(0, -1, 0)) },
            { add("featureIndices", ints(0, ConstructionFeatures.names.size, 0)) },
            { add("thresholds", floats(0.0, 1.0)) },
            { add("leafValues", floats(1.0, 2.0)) },
            { addProperty("depths", 2) },
            { add("depths", floats(2.5, 1.0)) },
            { addProperty("extra", "unused") },
            { remove("scale") },
        )
        changes.forEach { change ->
            assertThrows(IllegalArgumentException::class.java) {
                parse(document().apply { getAsJsonObject("model").apply(change) })
            }
        }
    }

    @Test
    fun rejectsNonfiniteNumbersAndValuesThatOverflowFloatPayloads() {
        val changes = listOf<JsonObject.() -> Unit>(
            { addProperty("bias", Double.POSITIVE_INFINITY) },
            { addProperty("scale", Double.NaN) },
            { add("thresholds", floats(0.0, 1e100, -2.0)) },
            { add("leafValues", floats(-3.0, 5.0, Double.NaN, 11.0, 13.0, -17.0)) },
            { add("leafValues", floats(-3.0, 5.0, Double.NEGATIVE_INFINITY, 11.0, 13.0, -17.0)) },
            { addProperty("bias", "0.0") },
        )
        changes.forEach { change ->
            assertThrows(IllegalArgumentException::class.java) {
                parse(document().apply { getAsJsonObject("model").apply(change) })
            }
        }
        val overflow = document().toString().replace("\"bias\":2.5", "\"bias\":1e9999")
        assertThrows(IllegalArgumentException::class.java) { ConstructionPolicyModel.read(overflow.toByteArray()) }
    }

    @Test
    fun rejectsDuplicateFieldsTrailingDocumentsMalformedUtf8AndOversizedFiles() {
        val text = document().toString()
        val malformed = listOf(
            text.replaceFirst("{", "{\"format\":1,"),
            text.replace("\"bias\":2.5", "\"bias\":2.5,\"bias\":2.5"),
            "$text {}",
            "/* comment */$text",
            text.replace("\"format\":1", "\"format\":01"),
            text.dropLast(1),
        )
        malformed.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { ConstructionPolicyModel.read(value.toByteArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) { ConstructionPolicyModel.read(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertThrows(IllegalArgumentException::class.java) { ConstructionPolicyModel.read(byteArrayOf()) }
        val oversized = temporary.newFile("oversized.json")
        RandomAccessFile(oversized, "rw").use { it.setLength(ConstructionPolicyModel.MAX_FILE_BYTES + 1L) }
        assertThrows(IllegalArgumentException::class.java) { ConstructionPolicyModel.read(oversized) }

        val valid = temporary.newFile("policy.json")
        valid.writeText(text, Charsets.UTF_8)
        assertEquals(12.5, ConstructionPolicyModel.read(valid).score(FloatArray(ConstructionFeatures.names.size)), 0.0)
    }

    @Test
    fun externalPolicyMatchesEveryExportedVectorAndCompleteStateActionOrder() {
        val configured = System.getenv("CODEGEN_CONSTRUCTION_POLICY_DIR")?.takeIf(String::isNotBlank)
        assumeTrue("Set CODEGEN_CONSTRUCTION_POLICY_DIR for trained construction-policy parity", configured != null)
        val directory = File(requireNotNull(configured)).canonicalFile
        val logFile = File(directory, "train-log.json")
        require(logFile.length() in 1..(1024 * 1024)) { "Construction training log size is invalid" }
        val log = JsonParser.parseString(logFile.readText(Charsets.UTF_8)).asJsonObject
        assertEquals("policy.json", log["modelFile"].asString)
        assertEquals("parity.jsonl", log["parityFile"].asString)
        val modelFile = File(directory, "policy.json").canonicalFile
        val parityFile = File(directory, "parity.jsonl").canonicalFile
        require(modelFile.parentFile == directory && parityFile.parentFile == directory) { "Parity files must stay in the policy directory" }
        require(parityFile.length() in 1..(256L * 1024 * 1024)) { "Construction parity file size is invalid" }
        assertEquals(log["modelSha256"].asString, sha256(modelFile))
        assertEquals(log["paritySha256"].asString, sha256(parityFile))
        assertEquals(1e-4, log["scoreTolerance"].asDouble, 0.0)
        val policy = ConstructionPolicyModel.read(modelFile)
        val states = LinkedHashMap<Pair<String, String>, MutableList<ParityRow>>()
        var rows = 0
        var maximumDifference = 0.0
        parityFile.useLines(Charsets.UTF_8) { lines ->
            lines.forEach { line ->
                require(line.isNotBlank() && line.length <= 1024 * 1024) { "Malformed construction parity row" }
                val record = JsonParser.parseString(line).asJsonObject
                assertEquals(1, record["format"].asInt)
                val groupId = record["groupId"].asString.also { require(it.isNotBlank()) }
                val stateKey = record["stateKey"].asString.also { require(it.isNotBlank()) }
                val actionKey = record["actionKey"].asString.also { require(it.isNotBlank()) }
                val features = record.getAsJsonArray("features").map {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "Parity feature must be numeric" }
                    it.asFloat
                }.toFloatArray()
                assertEquals(ConstructionFeatures.names.size, features.size)
                assertTrue(features.all(Float::isFinite))
                val expected = record["expectedScore"].asDouble
                assertTrue(expected.isFinite())
                val actual = policy.score(features)
                maximumDifference = maxOf(maximumDifference, abs(expected - actual))
                assertEquals("Construction score differs for $groupId / $stateKey / $actionKey", expected, actual, 1e-4)
                states.getOrPut(groupId to stateKey, ::ArrayList).add(ParityRow(actionKey, expected, actual))
                rows++
            }
        }
        assertTrue("Construction parity needs complete state groups", states.isNotEmpty())
        assertEquals(log["parityRows"].asInt, rows)
        assertEquals(log["parityStates"].asInt, states.size)
        for ((key, stateRows) in states) {
            assertEquals("Duplicate state/action reference in $key", stateRows.size, stateRows.map { it.actionKey }.toSet().size)
            assertEquals("Construction action order differs for $key",
                orderedActions(stateRows) { it.expected }, orderedActions(stateRows) { it.actual })
        }
        println("Construction policy parity: $rows vectors, ${states.size} states, max score difference=$maximumDifference, all action orders identical")
    }

    private data class ParityRow(val actionKey: String, val expected: Double, val actual: Double)

    /** Exercise the searcher's actual anchored-band tie order on both reference and Kotlin scores. */
    private fun orderedActions(rows: List<ParityRow>, score: (ParityRow) -> Double): List<String> =
        ConstructionSearch.orderDecisions(rows.map {
            ConstructionDecision("parity-state", it.actionKey, FloatArray(0), score(it))
        }).map { it.actionKey }

    private fun parse(document: JsonObject) = ConstructionPolicyModel.read(document.toString().toByteArray(Charsets.UTF_8))

    private fun document() = JsonObject().apply {
        addProperty("format", 1)
        addProperty("role", "construction-policy")
        addProperty("grammarVersion", ConstructionFeatures.grammarVersion)
        addProperty("featureSchemaVersion", ConstructionFeatures.version)
        addProperty("featureNamesHash", ConstructionFeatures.namesHash)
        addProperty("featureCount", ConstructionFeatures.names.size)
        addProperty("scoreSemantics", ConstructionPolicyModel.SCORE_SEMANTICS)
        add("model", JsonObject().apply {
            add("depths", ints(2, 1))
            add("splitOffsets", ints(0, 2))
            add("leafOffsets", ints(0, 4))
            add("featureIndices", ints(0, 1, 0))
            add("thresholds", floats(0.0, 1.0, -2.0))
            add("leafValues", floats(-3.0, 5.0, 7.0, 11.0, 13.0, -17.0))
            addProperty("bias", 2.5)
            addProperty("scale", -.5)
        })
    }

    private fun ints(vararg values: Int) = JsonArray().apply { values.forEach(::add) }
    private fun floats(vararg values: Double) = JsonArray().apply { values.forEach(::add) }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
