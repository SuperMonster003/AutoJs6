package org.autojs.autojs.ui.settings.codegen

import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.store.CurrentModelSelection
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogBuildTestMaterials
import org.autojs.autojs.core.accessibility.codegen.store.ModelDownloader
import org.autojs.autojs.core.accessibility.codegen.store.ModelSource
import org.autojs.autojs.core.accessibility.codegen.store.ModelStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/** Opt-in public download using the published P5.4 test key, real HTTP, package reader and store. */
class ModelDownloadIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun publishedTinyModelDownloadsAndInstallsThroughTheRealPipeline() {
        assumeTrue("Set CODEGEN_DOWNLOAD_INTEGRATION=1 for the public P5.4 release", System.getenv("CODEGEN_DOWNLOAD_INTEGRATION") == "1")
        val configuration = CodegenCatalogBuildConfiguration.DISTRIBUTION_TEST.configuration
        val repository = configuration.createRepository(temporary.root, Int.MAX_VALUE,
            ModelCatalogBuildTestMaterials.debugKey(), ModelCatalogBuildTestMaterials.debugBootstrap())
        val catalog = repository.refresh(force = true)
        val model = requireNotNull(catalog.find("autojs6.codegen.tiny-test"))
        val version = model.versions.single { it.versionCode == 1 }
        val selection = object : CurrentModelSelection { override var currentKey: String? = null }
        val store = ModelStore(configuration.modelDirectory(temporary.root), ModelPackageReader(Int.MAX_VALUE, temporary.newFolder()),
            { listOfNotNull(repository.current(), repository.bootstrap()) }, selection)
        ModelDownloader(store, repository).use { downloader ->
            val installed = downloader.enqueue(model.modelId, version).future.get(60, TimeUnit.SECONDS)
            assertEquals("9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17", installed.sha256)
            assertEquals(23633L, installed.sizeBytes)
            assertEquals(ModelSource.OFFICIAL, installed.source)
            assertEquals(1, store.listInstalled().size)
            assertNotNull(store.installedFile(installed.key))
            assertTrue(store.select(installed.key))
            assertEquals(installed.key, store.current()?.key)
            assertTrue(store.delete(installed.key))
            assertNull(selection.currentKey)
            assertTrue(store.listInstalled().isEmpty())
        }
    }
}
