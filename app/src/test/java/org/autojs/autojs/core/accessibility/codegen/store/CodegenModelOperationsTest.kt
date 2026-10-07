package org.autojs.autojs.core.accessibility.codegen.store

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.CancellationException

class CodegenModelOperationsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun actions(f: ModelManagementTestFixtures) = CodegenModelOperations(f.store, f.repository, Int.MAX_VALUE, temporary.root)

    @Test fun reviewDoesNotInstallAndClosingItDiscardsItsLeaseWithoutClosingTheInput() {
        val f = ModelManagementTestFixtures(temporary.root)
        var closed = false
        val input = object : ByteArrayInputStream(f.v1) { override fun close() { closed = true; super.close() } }
        actions(f).prepareImport(input, "tiny.ajcgm").use { prepared ->
            assertFalse(closed)
            assertEquals(ModelSource.OFFICIAL, prepared.source)
            assertTrue(f.store.listInstalled().isEmpty())
            assertFalse(f.noStages())
        }
        assertTrue(f.noStages())
    }

    @Test fun unknownLocalContentIsCustomAndIdenticalContentIsDetectedBeforeInstallation() {
        val f = ModelManagementTestFixtures(temporary.root, withCatalog = false)
        val actions = actions(f)
        val installed = actions.prepareImport(f.v1.inputStream(), "local.ajcgm").use { prepared ->
            assertEquals(ModelSource.CUSTOM, prepared.source)
            assertSame(DuplicateCheck.None, actions.duplicates(prepared))
            actions.install(prepared)
        }
        actions.prepareImport(f.v1.inputStream(), "again.ajcgm").use { prepared ->
            assertEquals(installed.key, (actions.duplicates(prepared) as DuplicateCheck.Identical).existing.key)
        }
        assertTrue(f.noStages())
        assertEquals(1, f.store.listInstalled().size)
    }

    @Test fun replacementUsesTheReviewedVersionAndSwitchesCurrent() {
        val f = ModelManagementTestFixtures(temporary.root)
        val old = f.installed(1)
        f.store.select(old.key)
        val actions = actions(f)
        val next = actions.prepareImport(f.v2.inputStream(), "next.ajcgm").use { prepared ->
            val duplicate = actions.duplicates(prepared) as DuplicateCheck.SameModel
            assertEquals(old.key, duplicate.compareWith.key)
            actions.install(prepared, duplicate.compareWith.key)
        }
        assertEquals(next.key, f.store.current()?.key)
        assertEquals(listOf(next), f.store.listInstalled())
        assertTrue(f.noStages())
    }

    @Test fun changedPreviewBytesAreRejectedWithoutOverwritingTheCurrentVersion() {
        val f = ModelManagementTestFixtures(temporary.root)
        val old = f.installed(1)
        f.store.select(old.key)
        val actions = actions(f)
        actions.prepareImport(f.v2.inputStream(), "next.ajcgm").use { prepared ->
            prepared.stage.file.appendBytes(byteArrayOf(1))
            assertThrows(ModelInstallException::class.java) { actions.install(prepared, old.key) }
        }
        assertEquals(old.key, f.store.current()?.key)
        assertEquals(listOf(old), f.store.listInstalled())
        assertTrue(f.noStages())
    }

    @Test fun badAndIncompatiblePackagesHaveStableReasonsAndNoStagingResidue() {
        val f = ModelManagementTestFixtures(temporary.root)
        val actions = actions(f)
        assertEquals(ModelImportException.Reason.INVALID_PACKAGE,
            assertThrows(ModelImportException::class.java) { actions.prepareImport("bad zip".byteInputStream(), "bad.ajcgm") }.reason)
        val oldHost = CodegenModelOperations(f.store, f.repository, 1, temporary.root)
        assertEquals(ModelImportException.Reason.INCOMPATIBLE,
            assertThrows(ModelImportException::class.java) { oldHost.prepareImport(f.v1.inputStream(), "newer.ajcgm") }.reason)
        assertTrue(f.noStages())
    }

    @Test fun oversizedInputIsStoppedBeforeParsingAndCleaned() {
        val f = ModelManagementTestFixtures(temporary.root)
        val input = object : InputStream() {
            var remaining = ModelStore.MAX_PACKAGE_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining == 0L) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                remaining -= count
                return count
            }
        }
        assertEquals(ModelImportException.Reason.TOO_LARGE,
            assertThrows(ModelImportException::class.java) { actions(f).prepareImport(input, "huge.ajcgm") }.reason)
        assertTrue(f.noStages())
    }

    @Test fun cancellationDiscardsPartiallyCopiedInput() {
        val f = ModelManagementTestFixtures(temporary.root)
        val input = object : ByteArrayInputStream(f.v1) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, minOf(length, 16))
                Thread.currentThread().interrupt()
                return count
            }
        }
        try { assertThrows(CancellationException::class.java) { actions(f).prepareImport(input, "cancelled.ajcgm") } }
        finally { Thread.interrupted() }
        assertTrue(f.noStages())
    }

    @Test fun modelCardIsPlainTextAndChangedInstalledBytesAreNotDisplayed() {
        val f = ModelManagementTestFixtures(temporary.root)
        val installed = f.installed(1)
        val actions = actions(f)
        assertTrue(actions.readModelCard(installed.key).startsWith("# AutoJs6"))
        requireNotNull(f.store.installedFile(installed.key)).appendBytes(byteArrayOf(1))
        assertThrows(FileNotFoundException::class.java) { actions.readModelCard(installed.key) }
    }
}
