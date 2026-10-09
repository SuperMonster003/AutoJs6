package org.autojs.autojs.core.accessibility.codegen.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.rank.ModelScoreOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

/** CatBoost reference vectors are checked by the real Kotlin parser, interpreter and tie order. */
class ModelParityTest {
    private data class Row(val groupId: String, val planKey: String, val ruleRank: Int, val groupSize: Int, val score: Double, val expectedTop: Boolean)

    @Test
    fun bundledTinyPackageMatchesCatBoostOnCompleteGroups() {
        val metadata = resource("parity-manifest.json")
        val manifest = JsonParser.parseString(metadata.toString(Charsets.UTF_8)).asJsonObject
        assertEquals("tiny-test-v1.ajcgm", manifest["packageFile"].asString)
        val bytes = resource("tiny-test-v1.ajcgm")
        assertTrue("Bundled tiny package must stay below 64 KiB", bytes.size < 64 * 1024)
        val parsed = ModelPackageReader().read(bytes.inputStream())
        assertTrue("Tiny package rejected: $parsed", parsed is ModelPackage)
        val model = parsed as ModelPackage
        verify(manifest, bytes, resource("parity.jsonl"), model, "bundled")
    }

    @Test
    fun externalPackageMatchesTheCompleteExportedReferenceGroups() {
        val directory = System.getenv("CODEGEN_PARITY_DIR")?.takeIf { it.isNotBlank() }
        assumeTrue("Set CODEGEN_PARITY_DIR for the full trained-model parity run", directory != null)
        val root = File(requireNotNull(directory)).canonicalFile
        val manifest = JsonParser.parseString(File(root, "parity-manifest.json").readText(Charsets.UTF_8)).asJsonObject
        val name = manifest["packageFile"].asString
        require(name.endsWith(".ajcgm") && '/' !in name && '\\' !in name && ':' !in name) { "Package filename must be local" }
        val file = File(root, name).canonicalFile
        require(file.parentFile == root) { "Package must stay inside CODEGEN_PARITY_DIR" }
        val parsed = ModelPackageReader().read(file)
        assertTrue("Trained package rejected: $parsed", parsed is ModelPackage)
        verify(manifest, file.readBytes(), File(root, "parity.jsonl").readBytes(), parsed as ModelPackage, "external")
    }

    private fun verify(manifest: JsonObject, bytes: ByteArray, parity: ByteArray, model: ModelPackage, label: String) {
        assertEquals(1, manifest["format"].asInt)
        val schema = manifest["featureSchemaVersion"].asInt
        assertTrue("Parity schema must be supported by the host", schema in FeatureSchema.SUPPORTED_SCHEMA)
        assertEquals(model.manifest.featureSchemaVersion, schema)
        assertEquals(FeatureSchema.namesHash(schema), manifest["featureNamesHash"].asString)
        assertEquals(sha256(bytes), manifest["packageSha256"].asString)
        assertEquals(sha256(parity), manifest["paritySha256"].asString)
        assertEquals(1e-4, manifest["scoreTolerance"].asDouble, 0.0)
        val groups = LinkedHashMap<String, MutableList<Row>>()
        var maximumError = 0.0
        var count = 0
        parity.toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank).forEach { line ->
            val record = JsonParser.parseString(line).asJsonObject
            assertEquals(1, record["format"].asInt)
            val features = record.getAsJsonArray("features").map { it.asFloat }.toFloatArray()
            assertEquals(FeatureSchema.names(schema).size, features.size)
            assertTrue(features.all(Float::isFinite))
            val expected = record["expectedScore"].asDouble
            assertTrue(expected.isFinite())
            val actual = model.model.score(features)
            val difference = abs(expected - actual)
            maximumError = max(maximumError, difference)
            assertEquals("Score mismatch in ${record["groupId"].asString}", expected, actual, 1e-4)
            val row = Row(record["groupId"].asString, record["planKey"].asString, record["ruleRank"].asInt,
                record["groupSize"].asInt, actual, record["expectedTop"].asBoolean)
            groups.getOrPut(row.groupId, ::ArrayList).add(row)
            count++
        }
        assertTrue("Reference must contain candidates", count > 0)
        assertEquals(manifest["candidates"].asInt, count)
        assertEquals(manifest["groups"].asInt, groups.size)
        for ((groupId, rows) in groups) {
            assertTrue("Truncated group $groupId", rows.all { it.groupSize == rows.size })
            assertEquals(rows.size, rows.map { it.planKey }.toSet().size)
            assertEquals(rows.indices.toList(), rows.map { it.ruleRank }.sorted())
            assertEquals("Every group needs exactly one reference top choice", 1, rows.count { it.expectedTop })
            val order = ModelScoreOrder.order(rows.map { it.score }.toDoubleArray(), rows.map { it.ruleRank }.toIntArray())
            assertEquals("Top choice differs for $groupId", rows.single { it.expectedTop }.planKey, rows[order[0]].planKey)
        }
        println("Model parity $label: $count candidates, ${groups.size} complete groups, max score difference=$maximumError, top choices identical")
    }

    private fun resource(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/codegen/models/$name")) {
        "Missing bundled parity resource $name"
    }.use { it.readBytes() }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
