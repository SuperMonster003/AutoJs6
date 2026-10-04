package org.autojs.autojs.ui.settings.codegen

import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogBuildTestMaterials
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogConfiguration
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogRepository
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogFetcher
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CodegenCatalogBuildConfigurationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun ordinaryDebugBuildUsesTheProductionResources() {
        assertSame(CodegenCatalogResources.PRODUCTION, CodegenCatalogBuildConfiguration.resources())
        assertEquals(R.raw.codegen_model_catalog_public_key_v1, CodegenCatalogBuildConfiguration.resources().publicKeyResource)
        assertEquals(R.raw.codegen_model_catalog_v1, CodegenCatalogBuildConfiguration.resources().bootstrapResource)
    }

    @Test
    fun explicitDebugSourceVerifiesThePublishedTestMaterialAndKeepsItsCacheSeparate() {
        val selection = CodegenCatalogBuildConfiguration.DISTRIBUTION_TEST
        val configuration = selection.configuration
        assertEquals(R.raw.codegen_model_catalog_debug_test_public_key_v1, selection.publicKeyResource)
        assertEquals(R.raw.codegen_model_catalog_debug_test_v1, selection.bootstrapResource)
        assertThrows(IllegalArgumentException::class.java) { configuration.requireProduction() }
        val (catalog, signature) = ModelCatalogBuildTestMaterials.material(ModelCatalogBuildTestMaterials.debugBootstrap())
        assertEquals("7a96afa530738232f521ee53d76dce628e960947cb8f97d7aa879ebd19796b43", ModelCatalogJson.digest(catalog))
        val urls = mutableListOf<String>()
        val repository = configuration.createRepository(
            temporary.root, Int.MAX_VALUE, ModelCatalogBuildTestMaterials.debugKey(), ModelCatalogBuildTestMaterials.debugBootstrap(),
            fetcher = FontCatalogFetcher { url, _ ->
                urls.add(url)
                when (url) {
                    configuration.catalogUrl -> catalog
                    configuration.signatureUrl -> signature
                    else -> error("Unexpected debug source: $url")
                }
            },
        )
        assertTrue(urls.isEmpty())
        val bootstrap = requireNotNull(repository.current())
        assertEquals("SuperMonster003/AutoJs6-Codegen-Distribution-Test", bootstrap.repository)
        assertFalse(bootstrap.canDownload("autojs6.codegen.tiny-test", 1))
        assertTrue(repository.refresh().canDownload("autojs6.codegen.tiny-test", 1))
        assertEquals(listOf(configuration.catalogUrl, configuration.signatureUrl), urls)
        assertTrue(File(configuration.cacheDirectory(temporary.root), ModelCatalogRepository.CACHE_FILE_NAME).isFile)
        assertFalse(ModelCatalogConfiguration.PRODUCTION.cacheDirectory(temporary.root).exists())
        val production = ModelCatalogConfiguration.PRODUCTION.createRepository(
            temporary.root, Int.MAX_VALUE, ModelCatalogBuildTestMaterials.productionKey(), ModelCatalogBuildTestMaterials.productionBootstrap(),
        )
        assertTrue(requireNotNull(production.current()).models.isEmpty())
        assertFalse(requireNotNull(production.current()).remoteArtifactsAllowed)
        assertTrue(requireNotNull(repository.loadCached()).canDownload("autojs6.codegen.tiny-test", 1))
    }
}
