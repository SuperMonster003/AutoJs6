package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonParser
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogFetcher
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogSignatureException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class ModelCatalogConfigurationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun actualProductionResourcesVerifyWithoutNetworkAndUseTheNoBackupCatalogDirectory() {
        val production = ModelCatalogConfiguration.PRODUCTION
        val repository = production.createRepository(
            noBackupDirectory = temporary.root,
            hostVersionCode = Int.MAX_VALUE,
            publicKeyBytes = ModelCatalogBuildTestMaterials.productionKey(),
            bootstrapEnvelopeBytes = ModelCatalogBuildTestMaterials.productionBootstrap(),
            fetcher = FontCatalogFetcher { _, _ -> error("Constructing or reading a bootstrap must not fetch") },
        )
        assertSame(production, production.requireProduction())
        assertEquals(File(temporary.root, "codegen_models/catalog"), production.cacheDirectory(temporary.root))
        val bootstrap = requireNotNull(repository.current())
        assertEquals(ModelCatalogParser.OFFICIAL_REPOSITORY, bootstrap.repository)
        assertEquals(1, bootstrap.catalogVersion)
        assertTrue(bootstrap.models.isEmpty())
        assertFalse(bootstrap.remoteArtifactsAllowed)
        assertEquals("caceae92e1274a45c94cff35f6480e6c43db5bef5b6d6e29924ded6bc0191a7e",
            ModelCatalogJson.digest(ModelCatalogBuildTestMaterials.material(ModelCatalogBuildTestMaterials.productionBootstrap()).first))
    }

    @Test
    fun productionRejectsThePublishedTestKeyAndBootstrap() {
        val production = ModelCatalogConfiguration.PRODUCTION
        assertThrows(ModelCatalogSignatureException::class.java) {
            production.signatureVerifier(ModelCatalogBuildTestMaterials.debugKey())
        }
        assertThrows(ModelCatalogSignatureException::class.java) {
            production.createRepository(
                temporary.root, Int.MAX_VALUE, ModelCatalogBuildTestMaterials.productionKey(),
                ModelCatalogBuildTestMaterials.debugBootstrap(),
            )
        }
        val (testCatalog, _) = ModelCatalogBuildTestMaterials.material(ModelCatalogBuildTestMaterials.debugBootstrap())
        assertThrows(ModelCatalogParseException::class.java) {
            ModelCatalogParser(Int.MAX_VALUE, expectedRepository = production.expectedRepository).parse(testCatalog)
        }
    }

    @Test
    fun productionRejectsAnUnexpectedEnvelopeKeyIdAndCatalogName() {
        val verifier = ModelCatalogConfiguration.PRODUCTION.signatureVerifier(ModelCatalogBuildTestMaterials.productionKey())
        val (catalog, signature) = ModelCatalogBuildTestMaterials.material(ModelCatalogBuildTestMaterials.productionBootstrap())
        for ((field, value) in listOf("keyId" to "p256-fc44588d1cb789dc", "catalog" to "catalog-other.json")) {
            val modified = JsonParser.parseString(signature.toString(Charsets.UTF_8)).asJsonObject.apply { addProperty(field, value) }
            assertThrows(FontCatalogSignatureException::class.java) {
                verifier.verify(catalog, modified.toString().toByteArray(Charsets.UTF_8))
            }
        }
    }

    @Test
    fun productionNamespaceCannotBeReboundToADebugKeyOrCache() {
        val production = ModelCatalogConfiguration.PRODUCTION
        val overrides = listOf(
            Triple("p256-fc44588d1cb789dc", production.publicKeySha256, "catalog"),
            Triple(production.expectedKeyId, "fc44588d1cb789dc3ac0002821b8f698b663b44529606a2937910c0a80ccb7c3", "catalog"),
            Triple(production.expectedKeyId, production.publicKeySha256, "catalog-debug-test"),
        )
        for ((id, pin, directory) in overrides) {
            assertThrows(IllegalArgumentException::class.java) {
                ModelCatalogConfiguration(production.expectedRepository, id, pin, directory)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogConfiguration(production.expectedRepository.lowercase(), production.expectedKeyId, production.publicKeySha256, "catalog")
        }
    }

    @Test
    fun debugSourcesCannotShareProductionCacheOrEscapeTheModelDirectory() {
        for (directory in listOf("catalog", "../catalog", "/catalog", "catalog-debug-../catalog", "catalog-debug-..\\catalog")) {
            assertThrows(IllegalArgumentException::class.java) {
                debugConfiguration(directory)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { debugConfiguration().requireProduction() }
    }

    @Test
    fun debugProfilesIsolateInstalledModelsAndCurrentSelectionWithoutMovingCatalogCaches() {
        val production = ModelCatalogConfiguration.PRODUCTION
        val debug = debugConfiguration()
        val anotherDebug = debugConfiguration("catalog-debug-another-source")
        val preferenceKey = "key_\$_codegen_model_current"
        assertEquals(File(temporary.root, "codegen_models"), production.modelDirectory(temporary.root))
        assertEquals(preferenceKey, production.selectionPreferenceKey(preferenceKey))
        assertEquals(File(temporary.root, "codegen_models-debug-distribution-test"), debug.modelDirectory(temporary.root))
        assertEquals("${preferenceKey}_debug_distribution-test", debug.selectionPreferenceKey(preferenceKey))
        assertNotEquals(debug.modelDirectory(temporary.root), anotherDebug.modelDirectory(temporary.root))
        assertNotEquals(debug.selectionPreferenceKey(preferenceKey), anotherDebug.selectionPreferenceKey(preferenceKey))
        assertEquals(File(temporary.root, "codegen_models/catalog"), production.cacheDirectory(temporary.root))
        assertEquals(File(temporary.root, "codegen_models/catalog-debug-distribution-test"), debug.cacheDirectory(temporary.root))
        assertEquals(ModelCatalogRepository.DEFAULT_CATALOG_URL, production.catalogUrl)
        assertEquals("https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Codegen-Distribution-Test/main/codegen-models/catalogs/catalog-v1.json", debug.catalogUrl)
    }

    @Test
    fun configuredProductionEndpointsAreUsedOnlyForExplicitRefresh() {
        val production = ModelCatalogConfiguration.PRODUCTION
        val (catalog, signature) = ModelCatalogBuildTestMaterials.material(ModelCatalogBuildTestMaterials.productionBootstrap())
        val requested = mutableListOf<String>()
        val repository = production.createRepository(
            temporary.root, Int.MAX_VALUE, ModelCatalogBuildTestMaterials.productionKey(), ModelCatalogBuildTestMaterials.productionBootstrap(),
            fetcher = FontCatalogFetcher { url, _ ->
                requested.add(url)
                when (url) {
                    ModelCatalogRepository.DEFAULT_CATALOG_URL -> catalog
                    ModelCatalogRepository.DEFAULT_SIGNATURE_URL -> signature
                    else -> error("Unexpected catalog endpoint: $url")
                }
            },
        )
        assertTrue(requested.isEmpty())
        assertTrue(repository.refresh().remoteArtifactsAllowed)
        assertEquals(listOf(ModelCatalogRepository.DEFAULT_CATALOG_URL, ModelCatalogRepository.DEFAULT_SIGNATURE_URL), requested)
        assertTrue(File(production.cacheDirectory(temporary.root), ModelCatalogRepository.CACHE_FILE_NAME).isFile)
        assertTrue(requireNotNull(repository.loadCached()).remoteArtifactsAllowed)
    }

    private fun debugConfiguration(directory: String = "catalog-debug-distribution-test") = ModelCatalogConfiguration(
        "SuperMonster003/AutoJs6-Codegen-Distribution-Test", "p256-fc44588d1cb789dc",
        "fc44588d1cb789dc3ac0002821b8f698b663b44529606a2937910c0a80ccb7c3", directory,
    )
}

/** Reads shipped resources directly: duplicated test fixtures must not hide a build pin mismatch. */
internal object ModelCatalogBuildTestMaterials {
    fun productionKey() = resource("main", "codegen_model_catalog_public_key_v1.der")
    fun productionBootstrap() = resource("main", "codegen_model_catalog_v1.json")
    fun debugKey() = resource("debug", "codegen_model_catalog_debug_test_public_key_v1.der")
    fun debugBootstrap() = resource("debug", "codegen_model_catalog_debug_test_v1.json")

    fun material(bootstrap: ByteArray): Pair<ByteArray, ByteArray> {
        val envelope = JsonParser.parseString(bootstrap.toString(Charsets.UTF_8)).asJsonObject
        return Base64.getDecoder().decode(envelope["catalogBase64"].asString) to
            envelope["signature"].toString().toByteArray(Charsets.UTF_8)
    }

    private fun resource(variant: String, name: String): ByteArray = sequenceOf(
        File("src/$variant/res/raw/$name"), File("app/src/$variant/res/raw/$name"),
    ).firstOrNull { it.isFile }?.readBytes() ?: error("Missing shipped $variant resource $name")
}
