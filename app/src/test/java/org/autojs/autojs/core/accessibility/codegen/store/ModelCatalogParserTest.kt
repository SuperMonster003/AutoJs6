package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogParserTest {
    private val parser = ModelCatalogParser(100)
    private fun catalog() = ModelCatalogTestFixtures.catalog()
    private fun version(raw: JsonObject) = raw.getAsJsonArray("models")[0].asJsonObject.getAsJsonArray("versions")[0].asJsonObject
    private fun model(raw: JsonObject) = raw.getAsJsonArray("models")[0].asJsonObject

    @Test
    fun parsesAllVersionMetadataAndFreezesNestedCollections() {
        val result = parser.parse(ModelCatalogTestFixtures.bytes(catalog()))
        val model = result.models.single()
        val version = model.versions.single()
        assertEquals("模型", model.displayName("zh-CN"))
        assertEquals("Model", model.displayName("fr"))
        assertEquals(1, model.recommendedVersionCode)
        assertEquals(ModelCatalogCompatibility.COMPATIBLE, version.compatibility)
        assertTrue(result.canDownload(model.modelId, 1))
        assertEquals(150, version.metrics.test.groups)
        assertEquals(null, version.metrics.realTest.top1Survival)
        assertThrows(UnsupportedOperationException::class.java) { (result.models as MutableList<*>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (model.displayName as MutableMap<*, *>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (version.artifact.urls as MutableList<*>).clear() }
    }

    @Test
    fun hostAndFeatureCompatibilityDisableDownloadsWithoutDroppingVersions() {
        val changes = listOf<Pair<(JsonObject) -> Unit, ModelCatalogCompatibility>>(
            ({ value: JsonObject -> value.addProperty("minHostVersionCode", 101) }) to ModelCatalogCompatibility.NEEDS_HOST_UPDATE,
            ({ value: JsonObject -> value.addProperty("featureSchemaVersion", FeatureSchema.SUPPORTED_SCHEMA.last + 1) }) to ModelCatalogCompatibility.INCOMPATIBLE,
            ({ value: JsonObject -> value.addProperty("featureNamesHash", "0".repeat(16)) }) to ModelCatalogCompatibility.INCOMPATIBLE,
        )
        for ((change, compatibility) in changes) {
            val raw = catalog().also { change(version(it)) }
            val parsed = parser.parse(raw.toString())
            assertEquals(compatibility, parsed.models.single().versions.single().compatibility)
            assertFalse(parsed.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        }
    }

    @Test
    fun revocationAndDeprecationRetainMetadataAndAllowNullRecommendation() {
        val revoked = parser.parse(ModelCatalogTestFixtures.catalog(versions = listOf(ModelCatalogTestFixtures.version(revoked = true))).toString())
        assertEquals(null, revoked.models.single().recommendedVersionCode)
        assertTrue(revoked.models.single().versions.single().revoked)
        assertFalse(revoked.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        val deprecated = parser.parse(ModelCatalogTestFixtures.catalog(versions = listOf(ModelCatalogTestFixtures.version(deprecated = true))).toString())
        assertEquals(null, deprecated.models.single().recommendedVersionCode)
        assertTrue(deprecated.models.single().versions.single().deprecated)
        assertTrue(deprecated.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        assertFalse(deprecated.copy(remoteArtifactsAllowed = false).canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
    }

    @Test
    fun unknownFieldsAreIgnoredIncludingVersionFingerprinting() {
        val baseline = parser.parse(catalog().toString()).models.single().versions.single().immutableFingerprint
        val raw = catalog().apply { addProperty("futureCatalogField", "ignored") }
        model(raw).addProperty("futureModelField", "ignored")
        version(raw).addProperty("futureVersionField", "ignored")
        version(raw).getAsJsonObject("artifact").addProperty("futureArtifactField", "ignored")
        version(raw).getAsJsonObject("license").addProperty("futureLicenseField", "ignored")
        assertEquals(baseline, parser.parse(raw.toString()).models.single().versions.single().immutableFingerprint)
    }

    @Test
    fun requiredFieldsTypesAndIdentityBoundariesAreStrict() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.remove("repository") }, { it.addProperty("schemaVersion", 2) }, { it.addProperty("catalogVersion", true) },
            { it.addProperty("catalogVersion", 1.0) }, { it.addProperty("releaseTag", "ace-fonts-v1") },
            { it.addProperty("repository", "other/repository") }, { model(it).addProperty("modelId", "../unsafe") },
            { model(it).remove("recommendedVersionCode") }, { model(it).addProperty("recommendedVersionCode", 9) },
            { model(it).getAsJsonObject("displayName").remove("en") }, { version(it).addProperty("versionCode", 0) },
            { version(it).addProperty("featureNamesHash", "bad") }, { version(it).addProperty("revoked", "false") },
            { version(it).getAsJsonObject("artifact").addProperty("size", 100L * 1024 * 1024) },
            { version(it).getAsJsonObject("artifact").addProperty("sha256", "a".repeat(63)) },
            { version(it).getAsJsonObject("artifact").addProperty("fileName", "other.ajcgm") },
            { version(it).getAsJsonObject("metrics").getAsJsonObject("realTest").addProperty("regret", 0) },
            { version(it).getAsJsonObject("metrics").getAsJsonObject("test").add("regret", JsonNull.INSTANCE) },
            { version(it).addProperty("datasetId", "bad\nidentifier") },
        )
        for (mutation in mutations) assertThrows(ModelCatalogParseException::class.java) { parser.parse(catalog().also(mutation).toString()) }
    }

    @Test
    fun URLsMustMatchSignedRepositoryReleaseNamespaceAndContentAddress() {
        val original = version(catalog()).getAsJsonObject("artifact")["url"].asString
        for (url in listOf(original.replace("https:", "http:"), original.replace("github.com", "example.com"),
            original.replace("codegen-models-v1", "ace-fonts-v1"), original.replace("codegen-models-v1", "codegen-models-v2"),
            original + "?token=x", original + "#fragment", original.replace("/SuperMonster003/", "/other/"),
            original.replace("github.com", "user@github.com"), original.replace("github.com", "github.com:443"))) {
            val raw = catalog()
            val artifact = version(raw).getAsJsonObject("artifact")
            artifact.addProperty("url", url)
            artifact.getAsJsonArray("urls").set(0, com.google.gson.JsonPrimitive(url))
            assertThrows(ModelCatalogParseException::class.java) { parser.parse(raw.toString()) }
        }
    }

    @Test
    fun timestampsDuplicatesAndMalformedUTF8AreRejected() {
        for (value in listOf("2026-10-03", "2026-10-03T00:00:00", "2026-13-03T00:00:00Z")) {
            assertThrows(ModelCatalogParseException::class.java) { parser.parse(catalog().apply { addProperty("generatedAt", value) }.toString()) }
        }
        val future = catalog().also { version(it).addProperty("releasedAt", "2027-01-01T00:00:00Z") }
        assertThrows(ModelCatalogParseException::class.java) { parser.parse(future.toString()) }
        assertThrows(ModelCatalogParseException::class.java) { parser.parse(ModelCatalogTestFixtures.catalog(versions = listOf(ModelCatalogTestFixtures.version(), ModelCatalogTestFixtures.version())).toString()) }
        assertThrows(ModelCatalogParseException::class.java) { parser.parse(catalog().toString().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")) }
        assertThrows(ModelCatalogParseException::class.java) { parser.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertThrows(ModelCatalogParseException::class.java) { parser.parse(ByteArray(ModelCatalogParser.MAX_CATALOG_BYTES + 1)) }
    }

    @Test
    fun base64DecoderPreservesExactBytesAndRejectsNoncanonicalPadding() {
        assertEquals("f", ModelCatalogJson.base64("Zg==", 100).toString(Charsets.UTF_8))
        assertEquals("fo", ModelCatalogJson.base64("Zm8=", 100).toString(Charsets.UTF_8))
        assertEquals("foo", ModelCatalogJson.base64("Zm9v", 100).toString(Charsets.UTF_8))
        for (value in listOf("Zh==", "Zm9=", "Zg==\n", "=m9v", "Zm=v", "Zm9v=", "")) {
            assertThrows(IllegalArgumentException::class.java) { ModelCatalogJson.base64(value, 100) }
        }
    }
}
