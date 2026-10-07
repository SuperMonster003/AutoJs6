package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageRejectionReason
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageTestFixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ModelStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Selection(initialKey: String? = null) : CurrentModelSelection {
        @Volatile private var stored: String? = initialKey
        var persistedKey: String? = initialKey
            private set
        var failWrites = false
        var failAfterMemoryWrite = false
        override var currentKey: String?
            get() = stored
            set(value) {
                if (failWrites) throw IOException("Preference storage unavailable")
                stored = value
                if (failAfterMemoryWrite) throw IOException("Preference disk write failed after updating memory")
                persistedKey = value
            }
    }

    private fun store(
        root: File = temporary.newFolder(),
        selection: Selection = Selection(),
        catalogs: () -> List<ModelCatalog> = { emptyList() },
        hostVersionCode: Int = 100,
    ): ModelStore = ModelStore(root, ModelPackageReader(hostVersionCode, temporary.root), catalogs, selection, { 1234L })

    private fun packageBytes(version: Int = 1, publisher: String = "AutoJs6"): ByteArray = ModelPackageTestFixtures.archive(
        mutateManifest = { it.addProperty("versionCode", version); it.addProperty("versionName", "$version.0.0"); it.addProperty("publisher", publisher) },
    )

    private fun assertNoStages(root: File) {
        assertTrue(File(root, "staging").listFiles().orEmpty().isEmpty())
    }

    private fun packageFiles(root: File): List<File> = root.walkTopDown().filter { it.name == "model.ajcgm" }.toList()

    @Test
    fun validPackageAndEveryManifestSummaryFieldSurviveRecreation() {
        val root = temporary.newFolder()
        val selection = Selection()
        val bytes = packageBytes()
        val installed = store(root, selection).install(bytes.inputStream(), "downloaded-model.ajcgm")
        assertEquals(ModelSource.CUSTOM, installed.source)
        assertEquals(1234L, installed.installedAtMillis)
        assertEquals("downloaded-model.ajcgm", installed.origin)
        assertEquals(bytes.size.toLong(), installed.sizeBytes)
        assertEquals(ModelCatalogJson.digest(bytes), installed.sha256)
        val expectedFile = File(root, "${installed.modelId}/1-${installed.sha256.take(8)}/model.ajcgm")
        assertArrayEquals(bytes, expectedFile.readBytes())
        val recreated = store(root, selection)
        assertEquals(listOf(installed), recreated.listInstalled())
        assertEquals(expectedFile.canonicalFile, recreated.installedFile(installed.key))
        val raw = JsonParser.parseString(File(root, "registry.json").readText()).asJsonObject["models"].asJsonArray.single().asJsonObject
        assertEquals(installed.manifest.versionName, raw["versionName"].asString)
        assertEquals(installed.manifest.featureSchemaVersion, raw["featureSchemaVersion"].asInt)
        assertEquals(installed.manifest.training.trainedAt, raw["manifest"].asJsonObject["training"].asJsonObject["trainedAt"].asString)
        assertNoStages(root)
    }

    @Test
    fun localImportIsOfficialWhenAnyVerifiedSnapshotContainsItsExactDigest() {
        val bytes = packageBytes()
        val raw = ModelCatalogTestFixtures.catalog(versions = listOf(ModelCatalogTestFixtures.version(digest = ModelCatalogJson.digest(bytes))))
        val matching = ModelCatalogParser(100).parse(ModelCatalogTestFixtures.bytes(raw))
        val unrelated = ModelCatalogParser(100).parse(ModelCatalogTestFixtures.bytes(ModelCatalogTestFixtures.catalog()))
        // Origin labels and model names never grant official provenance; only the complete hash does.
        for (catalogs in listOf(listOf(matching), listOf(unrelated, matching), listOf(matching.copy(remoteArtifactsAllowed = false)))) {
            val installed = store(catalogs = { catalogs }).install(bytes.inputStream(), "local.ajcgm")
            assertEquals(ModelSource.OFFICIAL, installed.source)
        }
        assertEquals(ModelSource.CUSTOM, store(catalogs = { listOf(unrelated) }).install(bytes.inputStream(), "official catalog").source)
    }

    @Test
    fun badArchiveAndInvalidInternalChecksumAreRejectedWithoutResidue() {
        val root = temporary.newFolder()
        val store = store(root)
        val invalidArchives = listOf(
            "not a ZIP archive".toByteArray(),
            ModelPackageTestFixtures.archive(mutateManifest = { it["model"].asJsonObject.addProperty("sha256", "0".repeat(64)) }),
        )
        for (bytes in invalidArchives) {
            val rejection = assertThrows(ModelInstallException::class.java) { store.install(bytes.inputStream(), "invalid.ajcgm") }
            assertNotNull(rejection.rejection)
            assertTrue(store.listInstalled().isEmpty())
            assertTrue(packageFiles(root).isEmpty())
            assertNoStages(root)
        }
    }

    @Test
    fun injectedPackageReaderEnforcesHostCompatibility() {
        val root = temporary.newFolder()
        val bytes = ModelPackageTestFixtures.archive(mutateManifest = { it.addProperty("minHostVersionCode", 101) })
        val rejected = assertThrows(ModelInstallException::class.java) { store(root, hostVersionCode = 100).install(bytes.inputStream(), "future.ajcgm") }
        assertEquals(ModelPackageRejectionReason.HOST_TOO_OLD, rejected.rejection?.reason)
        assertNoStages(root)
    }

    @Test
    fun importStopsAtOneHundredMiBAndDoesNotCloseCallerInput() {
        val root = temporary.newFolder()
        var closed = false
        var supplied = 0L
        val input = object : InputStream() {
            override fun read(): Int { supplied++; return 0 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { supplied += length; return length }
            override fun close() { closed = true }
        }
        assertThrows(ModelInstallException::class.java) { store(root).install(input, "too-large.ajcgm") }
        assertTrue(supplied > ModelStore.MAX_PACKAGE_BYTES)
        assertTrue(supplied <= ModelStore.MAX_PACKAGE_BYTES + 32 * 1024)
        assertFalse(closed)
        assertTrue(packageFiles(root).isEmpty())
        assertNoStages(root)
    }

    @Test
    fun inputReturningZeroStillCopiesTheActualBytes() {
        val root = temporary.newFolder()
        val bytes = packageBytes()
        val source = bytes.inputStream()
        val input = object : InputStream() {
            override fun read(): Int = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        val store = store(root)
        val installed = store.install(input, "zero-reads.ajcgm")
        assertArrayEquals(bytes, checkNotNull(store.installedFile(installed.key)).readBytes())
        assertNoStages(root)
    }

    @Test
    fun interruptedStreamCleansItsStageAndPreservesTheInterruptFlag() {
        val root = temporary.newFolder()
        val input = object : InputStream() {
            override fun read(): Int {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Import interrupted")
            }
        }
        try {
            assertThrows(InterruptedIOException::class.java) { store(root).install(input, "interrupted.ajcgm") }
            assertTrue(Thread.currentThread().isInterrupted)
            assertNoStages(root)
            assertTrue(packageFiles(root).isEmpty())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun expectedHashSizeAndManifestAreCheckedBeforeAnythingIsInstalled() {
        val root = temporary.newFolder()
        val store = store(root)
        val bytes = packageBytes()
        assertThrows(ModelInstallException::class.java) {
            store.install(bytes.inputStream(), "catalog-v1", expectedSha256 = "f".repeat(64))
        }
        assertThrows(ModelInstallException::class.java) {
            store.install(bytes.inputStream(), "catalog-v1", expectedSizeBytes = bytes.size + 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.install(bytes.inputStream(), "catalog-v1", expectedSha256 = ModelCatalogJson.digest(bytes), expectedSizeBytes = bytes.size.toLong()) {
                require(it.versionCode == 99) { "Signed version does not match package" }
            }
        }
        assertTrue(store.listInstalled().isEmpty())
        assertTrue(packageFiles(root).isEmpty())
        assertNoStages(root)
    }

    @Test
    fun privateSnapshotKeepsTheVerifiedBytesWhenPublicStagingChanges() {
        val root = temporary.newFolder()
        val store = store(root)
        val bytes = packageBytes()
        val staging = store.beginStaging()
        staging.file.writeBytes(bytes)
        val installed = store.installStaged(staging, "local.ajcgm") { staging.file.writeText("changed after validation") }
        assertArrayEquals(bytes, checkNotNull(store.installedFile(installed.key)).readBytes())
        assertNoStages(root)
    }

    @Test
    fun versionsAndDifferentBytesAtTheSameVersionCanCoexistWithoutChangingSelection() {
        val root = temporary.newFolder()
        val selection = Selection()
        val store = store(root, selection)
        val first = store.install(packageBytes().inputStream(), "v1.ajcgm")
        assertTrue(store.select(first.key))
        val otherBytes = store.install(packageBytes(publisher = "Local publisher").inputStream(), "v1-custom.ajcgm")
        val second = store.install(packageBytes(2).inputStream(), "v2.ajcgm")
        assertEquals(setOf(first, otherBytes, second), store.listInstalled().toSet())
        assertEquals(first, store.current())
        assertEquals(first.key, selection.currentKey)
        assertNotEquals(first.key, otherBytes.key)
        assertEquals(3, packageFiles(root).size)
    }

    @Test
    fun replacingCurrentSelectsNewVersionAndDeletesOnlyTheChosenVersion() {
        val root = temporary.newFolder()
        val selection = Selection()
        val store = store(root, selection)
        val first = store.install(packageBytes().inputStream(), "v1.ajcgm")
        val retained = store.install(packageBytes(2).inputStream(), "v2.ajcgm")
        store.select(first.key)
        val replacement = store.install(packageBytes(3).inputStream(), "v3.ajcgm", replaceKey = first.key)
        assertEquals(replacement.key, selection.currentKey)
        assertEquals(replacement, store.current())
        assertEquals(setOf(retained, replacement), store.listInstalled().toSet())
        assertNull(store.installedFile(first.key))
        assertEquals(2, packageFiles(root).size)
        assertEquals(replacement, store(root, selection).current())
    }

    @Test
    fun replacingANoncurrentVersionPreservesTheCurrentChoice() {
        val store = store()
        val first = store.install(packageBytes().inputStream(), "v1.ajcgm")
        val current = store.install(packageBytes(2).inputStream(), "v2.ajcgm")
        store.select(current.key)
        store.install(packageBytes(3).inputStream(), "v3.ajcgm", replaceKey = first.key)
        assertEquals(current, store.current())
    }

    @Test
    fun deletingCurrentClearsSelectionWhileDeletingOtherVersionsDoesNot() {
        val selection = Selection()
        val store = store(selection = selection)
        val first = store.install(packageBytes().inputStream(), "v1.ajcgm")
        val second = store.install(packageBytes(2).inputStream(), "v2.ajcgm")
        store.select(first.key)
        assertTrue(store.delete(second.key))
        assertEquals(first.key, selection.currentKey)
        assertTrue(store.delete(first.key))
        assertNull(selection.currentKey)
        assertNull(store.current())
        assertFalse(store.delete(first.key))
        assertFalse(store.select(first.key))
        assertTrue(store.select(null))
    }

    @Test
    fun identicalBytesReturnExistingInstallationWithoutChangingItsOriginOrDate() {
        val root = temporary.newFolder()
        val store = store(root)
        val bytes = packageBytes()
        val existing = store.install(bytes.inputStream(), "original.ajcgm")
        val repeated = store.install(bytes.inputStream(), "again.ajcgm")
        assertEquals(existing, repeated)
        assertEquals(listOf(existing), store.listInstalled())
        assertEquals(1, packageFiles(root).size)
        assertNoStages(root)
    }

    @Test
    fun realShaPrefixCollisionIsRejectedWithoutOverwritingTheExistingPackage() {
        val root = temporary.newFolder()
        val store = store(root, hostVersionCode = Int.MAX_VALUE)
        val original = checkNotNull(javaClass.getResourceAsStream("/codegen/models/tiny-test-v1.ajcgm")).use { it.readBytes() }
        // These independently found nonces add legal 8-byte ZIP comments to the committed fixture.
        // The complete digests differ but both really begin 9196b6ae; no hashing stub is involved.
        fun commented(nonce: Long): ByteArray {
            assertArrayEquals(byteArrayOf(0x50, 0x4b, 0x05, 0x06), original.copyOfRange(original.size - 22, original.size - 18))
            val prefix = original.copyOf().apply { this[size - 2] = 8; this[size - 1] = 0 }
            return prefix + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(nonce).array()
        }
        val firstBytes = commented(75250)
        val secondBytes = commented(115752)
        assertEquals("9196b6ae", ModelCatalogJson.digest(firstBytes).take(8))
        assertEquals(ModelCatalogJson.digest(firstBytes).take(8), ModelCatalogJson.digest(secondBytes).take(8))
        assertNotEquals(ModelCatalogJson.digest(firstBytes), ModelCatalogJson.digest(secondBytes))
        val existing = store.install(firstBytes.inputStream(), "collision-a.ajcgm")
        assertThrows(ModelInstallException::class.java) { store.install(secondBytes.inputStream(), "collision-b.ajcgm") }
        assertArrayEquals(firstBytes, checkNotNull(store.installedFile(existing.key)).readBytes())
        assertEquals(listOf(existing), store.listInstalled())
        assertNoStages(root)
    }

    @Test
    fun failedRegistryCommitPreservesTheOldPackageSelectionAndRegistry() {
        val root = temporary.newFolder()
        val selection = Selection()
        val store = store(root, selection)
        val old = store.install(packageBytes().inputStream(), "old.ajcgm")
        store.select(old.key)
        val previousRegistry = File(root, "registry.json").readBytes()
        store.beforeRegistryCommit = { throw IOException("Disk full at registry commit") }
        assertThrows(IOException::class.java) { store.install(packageBytes(2).inputStream(), "new.ajcgm", replaceKey = old.key) }
        assertArrayEquals(previousRegistry, File(root, "registry.json").readBytes())
        assertEquals(listOf(old), store.listInstalled())
        assertEquals(old.key, selection.currentKey)
        assertArrayEquals(packageBytes(), checkNotNull(store.installedFile(old.key)).readBytes())
        assertEquals(1, packageFiles(root).size)
        assertNoStages(root)
    }

    @Test
    fun committedReplacementReplaysPreferenceChangeAfterAWriteFailure() {
        val root = temporary.newFolder()
        val selection = Selection()
        val store = store(root, selection)
        val old = store.install(packageBytes().inputStream(), "old.ajcgm")
        store.select(old.key)
        selection.failWrites = true
        assertThrows(IOException::class.java) { store.install(packageBytes(2).inputStream(), "new.ajcgm", replaceKey = old.key) }
        assertEquals(old.key, selection.currentKey)
        assertNoStages(root)
        selection.failWrites = false
        val reopened = store(root, selection)
        val current = checkNotNull(reopened.current())
        assertEquals(2, current.versionCode)
        assertEquals(current.key, selection.currentKey)
        assertEquals(listOf(current), reopened.listInstalled())
        assertEquals(1, packageFiles(root).size)
    }

    @Test
    fun replacementRetriesPreferencePersistenceEvenWhenItsMemoryValueAlreadyChanged() {
        val root = temporary.newFolder()
        val selection = Selection()
        val store = store(root, selection)
        val old = store.install(packageBytes().inputStream(), "old.ajcgm")
        store.select(old.key)
        selection.failAfterMemoryWrite = true
        assertThrows(IOException::class.java) { store.install(packageBytes(2).inputStream(), "new.ajcgm", replaceKey = old.key) }
        assertNotEquals(old.key, selection.currentKey)
        assertEquals(old.key, selection.persistedKey)
        selection.failAfterMemoryWrite = false
        val expected = selection.currentKey
        // This later registry commit must not discard the replacement journal until Pref is durable.
        store.install(packageBytes(3).inputStream(), "another.ajcgm")
        assertEquals(expected, selection.persistedKey)
        val restartedSelection = Selection(selection.persistedKey)
        assertEquals(2, store(root, restartedSelection).current()?.versionCode)
    }

    @Test
    fun explicitSelectionIsJournaledAcrossAnInMemoryOnlyPreferenceWrite() {
        val root = temporary.newFolder()
        val selection = Selection()
        val store = store(root, selection)
        val first = store.install(packageBytes().inputStream(), "first.ajcgm")
        val second = store.install(packageBytes(2).inputStream(), "second.ajcgm")
        store.select(first.key)
        selection.failAfterMemoryWrite = true
        assertThrows(IOException::class.java) { store.select(second.key) }
        assertEquals(second.key, selection.currentKey)
        assertEquals(first.key, selection.persistedKey)
        selection.failAfterMemoryWrite = false
        val restartedSelection = Selection(selection.persistedKey)
        assertEquals(second, store(root, restartedSelection).current())
        assertEquals(second.key, restartedSelection.persistedKey)
    }

    @Test
    fun startupRemovesMissingRecordsAndOrphansWithoutTouchingCatalogCache() {
        val root = temporary.newFolder()
        val selection = Selection()
        val store = store(root, selection)
        val installed = store.install(packageBytes().inputStream(), "local.ajcgm")
        store.select(installed.key)
        assertTrue(checkNotNull(checkNotNull(store.installedFile(installed.key)).parentFile).deleteRecursively())
        val orphan = File(root, "autojs6.codegen.orphan/2-aaaaaaaa/model.ajcgm").apply { checkNotNull(parentFile).mkdirs(); writeBytes(packageBytes(2)) }
        val partial = File(root, "staging/interrupted.part").apply { checkNotNull(parentFile).mkdirs(); writeText("partial") }
        val orphanTransaction = File(root, "staging/install-interrupted/model.ajcgm").apply { checkNotNull(parentFile).mkdirs(); writeBytes(packageBytes()) }
        val catalogCache = File(root, "catalog/verified.json").apply { checkNotNull(parentFile).mkdirs(); writeText("retain catalog") }
        val debugCatalogCache = File(root, "catalog-debug-test/verified.json").apply { checkNotNull(parentFile).mkdirs(); writeText("retain debug catalog") }
        val reopened = store(root, selection)
        assertTrue(reopened.listInstalled().isEmpty())
        assertNull(selection.currentKey)
        assertFalse(orphan.exists())
        assertFalse(partial.exists())
        assertFalse(orphanTransaction.exists())
        assertEquals("retain catalog", catalogCache.readText())
        assertEquals("retain debug catalog", debugCatalogCache.readText())
        assertNoStages(root)
    }

    @Test
    fun anotherInstanceNeverCleansAnActivelyLeasedStage() {
        val root = temporary.newFolder()
        val first = store(root)
        val stage = first.beginStaging()
        stage.file.writeBytes(packageBytes())
        val second = store(root)
        assertTrue(stage.file.isFile)
        val installed = second.installStaged(stage, "active.ajcgm")
        assertEquals(listOf(installed), first.listInstalled())
        stage.close()
        assertNoStages(root)
    }

    @Test
    fun cleanupNeverOpensAnotherDescriptorForAnInProcessActiveLease() {
        val root = temporary.newFolder()
        val first = store(root)
        val cleaner = store(root)
        val opened = ArrayList<File>()
        cleaner.beforeCleanupLeaseOpen = { opened += it }
        first.beginStaging().use { stage ->
            stage.file.writeBytes(packageBytes())
            cleaner.reconcile()
            assertTrue(stage.file.exists())
            assertTrue(opened.isEmpty())
        }
        val abandoned = File(root, "staging/model-abandoned.part").apply { writeText("partial") }
        val abandonedLease = File(checkNotNull(abandoned.parentFile), abandoned.name + ".lease").apply { writeText("") }
        cleaner.reconcile()
        assertEquals(listOf(abandonedLease), opened)
        assertNoStages(root)
    }

    @Test
    fun cleanupRemovesAnAbandonedStageAndUnlockedLease() {
        val root = temporary.newFolder()
        val part = File(root, "staging/model-abandoned.part").apply { checkNotNull(parentFile).mkdirs(); writeText("partial") }
        File(part.parentFile, part.name + ".lease").writeText("")
        File(part.parentFile, "model-missing.part.lease").writeText("")
        store(root)
        assertNoStages(root)
    }

    @Test
    fun corruptedRegistryFailsClosedAndPreservesEveryInstalledPackage() {
        val root = temporary.newFolder()
        val store = store(root)
        val bytes = packageBytes()
        val installed = store.install(bytes.inputStream(), "local.ajcgm")
        val file = checkNotNull(store.installedFile(installed.key))
        File(root, "registry.json").writeText("{broken registry")
        assertThrows(ModelStoreException::class.java) { store(root) }
        assertThrows(ModelStoreException::class.java) { store.listInstalled() }
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun registryRecoveryUsesBackupWhenMainFileIsMissing() {
        val root = temporary.newFolder()
        val store = store(root)
        val installed = store.install(packageBytes().inputStream(), "local.ajcgm")
        assertTrue(File(root, "registry.json").renameTo(File(root, "registry.json.bak")))
        assertEquals(listOf(installed), store(root).listInstalled())
        assertTrue(File(root, "registry.json").isFile)
    }

    @Test
    fun unreadableBackupNeverTurnsAnExistingInstallationIntoAnEmptyRegistry() {
        val root = temporary.newFolder()
        val store = store(root)
        val bytes = packageBytes()
        val installed = store.install(bytes.inputStream(), "local.ajcgm")
        val file = checkNotNull(store.installedFile(installed.key))
        assertTrue(File(root, "registry.json").delete())
        assertTrue(File(root, "registry.json.bak").mkdir())
        assertThrows(ModelStoreException::class.java) { store(root) }
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun installedFileRejectsSameSizeContentChangesAndCallerPaths() {
        val root = temporary.newFolder()
        val store = store(root)
        val installed = store.install(packageBytes().inputStream(), "local.ajcgm")
        val file = checkNotNull(store.installedFile(installed.key))
        val timestamp = file.lastModified()
        file.writeBytes(file.readBytes().apply { this[0] = (this[0].toInt() xor 1).toByte() })
        file.setLastModified(timestamp)
        assertNull(store.installedFile(installed.key))
        assertNull(store.installedFile("../../outside/model.ajcgm"))
        assertFalse(store.delete("../../outside/model.ajcgm"))
    }

    @Test
    fun reservedRootNamesCannotBeInstalledAsModelIds() {
        val root = temporary.newFolder()
        val store = store(root)
        for (modelId in listOf("catalog", "catalog-debug-test", "staging", "registry.json", "nul", "model.")) {
            val bytes = ModelPackageTestFixtures.archive(mutateManifest = { it.addProperty("modelId", modelId) })
            assertThrows(IllegalArgumentException::class.java) { store.install(bytes.inputStream(), "reserved.ajcgm") }
            assertNoStages(root)
        }
        assertTrue(store.listInstalled().isEmpty())
    }

    @Test
    fun simultaneousInstancesMergeRegistryCommitsInsteadOfLosingAVersion() {
        val root = temporary.newFolder()
        val first = store(root)
        val second = store(root)
        val executor = Executors.newFixedThreadPool(2)
        val staged = CountDownLatch(2)
        try {
            val futures = listOf(first, second).mapIndexed { index, store -> executor.submit<InstalledModel> {
                store.beginStaging().use { stage ->
                    stage.file.writeBytes(packageBytes(index + 1))
                    staged.countDown()
                    assertTrue(staged.await(5, TimeUnit.SECONDS))
                    store.installStaged(stage, "concurrent-${index + 1}.ajcgm")
                }
            } }
            val installed = futures.map { it.get(10, TimeUnit.SECONDS) }.toSet()
            assertEquals(installed, first.listInstalled().toSet())
            assertEquals(2, packageFiles(root).size)
            assertNoStages(root)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun catalogProviderRunsOutsideTheStoreLock() {
        val root = temporary.newFolder()
        val other = store(root)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val installing = store(root, catalogs = {
                executor.submit<List<InstalledModel>> { other.listInstalled() }.get(5, TimeUnit.SECONDS)
                emptyList()
            })
            assertNotNull(installing.install(packageBytes().inputStream(), "local.ajcgm"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun linkedStagingDirectoryIsRejectedWithoutChangingTheOutsideTarget() {
        val root = temporary.newFolder()
        val outside = temporary.newFolder()
        val marker = File(outside, "keep.txt").apply { writeText("outside") }
        createSymbolicLink(File(root, "staging"), outside)
        assertThrows(ModelStoreException::class.java) { store(root) }
        assertEquals("outside", marker.readText())
        assertEquals(listOf("keep.txt"), outside.list().orEmpty().toList())
    }

    @Test
    fun replacingAStagingFileWithALinkCannotImportOrDeleteTheOutsideFile() {
        val root = temporary.newFolder()
        val outside = temporary.newFile().apply { writeBytes(packageBytes()) }
        val store = store(root)
        val stage = store.beginStaging()
        try {
            assertTrue(stage.file.delete())
            createSymbolicLink(stage.file, outside)
            assertThrows(ModelStoreException::class.java) { store.installStaged(stage, "linked.ajcgm") }
            assertArrayEquals(packageBytes(), outside.readBytes())
            assertNoStages(root)
        } finally {
            stage.close()
        }
    }

    private fun createSymbolicLink(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (failure: IOException) {
            // Windows without Developer Mode may forbid symlink creation; other tests still run.
            assumeNoException(failure)
        } catch (failure: UnsupportedOperationException) {
            assumeNoException(failure)
        }
    }
}
