package org.autojs.autojs.core.plugin.explorer.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExplorerActionOutputTransactionStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun preparePersistsCanonicalSourceAndPrivateStagingFile() {
        val fixture = fixture()
        val transaction = fixture.store.prepare(fixture.request)

        assertEquals(TRANSACTION_ID, transaction.id)
        assertEquals(ExplorerActionOutputTransactionState.PREPARED, transaction.state)
        assertEquals(fixture.source.canonicalPath, transaction.sourcePath)
        assertEquals(requireNotNull(fixture.source.parentFile).canonicalPath, transaction.sourceParentPath)
        assertTrue(fixture.store.stagingFile(transaction.id).isFile)
        assertEquals(transaction, fixture.store.load(transaction.id))
        assertEquals(listOf(transaction), fixture.store.list())
    }

    @Test
    fun transitionGraphRejectsSkippedStates() {
        val fixture = fixture()
        val transaction = fixture.store.prepare(fixture.request)

        assertFails<IllegalArgumentException> {
            fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.VALIDATED)
        }
        assertEquals(
            ExplorerActionOutputTransactionState.LAUNCHING,
            fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.LAUNCHING).state,
        )
        assertEquals(
            ExplorerActionOutputTransactionState.LAUNCHED,
            fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.LAUNCHED).state,
        )
    }

    @Test
    fun permitsOnlyOneActiveOutputTransaction() {
        val fixture = fixture()
        val transaction = fixture.store.prepare(fixture.request)

        assertFails<IllegalStateException> { fixture.store.prepare(fixture.request) }

        fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.ROLLED_BACK)
        fixture.store.cleanupArtifacts(transaction.id)
        assertEquals(ExplorerActionOutputTransactionState.PREPARED, fixture.store.prepare(fixture.request).state)
    }

    @Test
    fun snapshotIsDurableAndMovesTransactionForward() {
        val fixture = fixture()
        val transaction = fixture.store.prepare(fixture.request)
        fixture.store.stagingFile(transaction.id).writeBytes(byteArrayOf(1, 2, 3, 4))
        fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.LAUNCHING)
        fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.LAUNCHED)
        fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.RESULT_RECEIVED)

        val snapshot = fixture.store.createSnapshot(transaction.id)

        assertEquals(listOf<Byte>(1, 2, 3, 4), snapshot.readBytes().toList())
        assertEquals(
            ExplorerActionOutputTransactionState.SNAPSHOTTED,
            fixture.store.load(transaction.id)?.state,
        )
    }

    @Test
    fun snapshotRejectsOutputBeyondTheDeclaredLimitBeforeCopying() {
        val fixture = fixture(maxOutputBytes = 3L)
        val transaction = fixture.store.prepare(fixture.request)
        fixture.store.stagingFile(transaction.id).writeBytes(byteArrayOf(1, 2, 3, 4))
        fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.LAUNCHING)
        fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.LAUNCHED)
        fixture.store.transition(transaction.id, ExplorerActionOutputTransactionState.RESULT_RECEIVED)

        assertFails<java.io.IOException> { fixture.store.createSnapshot(transaction.id) }

        assertFalse(fixture.store.snapshotFile(transaction.id).exists())
        assertEquals(
            ExplorerActionOutputTransactionState.RESULT_RECEIVED,
            fixture.store.load(transaction.id)?.state,
        )
    }

    @Test
    fun loadsThePreviousJournalAcrossTheAtomicReplacementWindow() {
        val fixture = fixture()
        val transaction = fixture.store.prepare(fixture.request)
        val journalDirectory = temporaryFolder.root
            .resolve("journal")
            .resolve(transaction.id)
        val journal = journalDirectory.resolve("transaction.properties")
        val backup = journalDirectory.resolve("transaction.properties.bak")
        assertTrue(journal.renameTo(backup))

        assertEquals(transaction, fixture.store.load(transaction.id))
        assertEquals(listOf(transaction), fixture.store.list())
        assertTrue(journal.isFile)
        assertFalse(backup.exists())
    }

    @Test
    fun restoresAValidBackupWhenThePrimaryJournalIsDamaged() {
        val fixture = fixture()
        val transaction = fixture.store.prepare(fixture.request)
        val journalDirectory = temporaryFolder.root
            .resolve("journal")
            .resolve(transaction.id)
        val journal = journalDirectory.resolve("transaction.properties")
        val backup = journalDirectory.resolve("transaction.properties.bak")
        backup.writeBytes(journal.readBytes())
        journal.writeText("not a transaction")

        assertEquals(transaction, fixture.store.load(transaction.id))
        assertEquals(transaction, fixture.store.load(transaction.id))
        assertTrue(journal.isFile)
        assertFalse(backup.exists())
    }

    @Test
    fun cleanupRemovesOnlyTransactionOwnedArtifacts() {
        val fixture = fixture()
        val transaction = fixture.store.prepare(fixture.request)
        val unrelated = File(fixture.source.parentFile, "unrelated.png").apply { writeText("keep") }

        fixture.store.cleanupArtifacts(transaction.id)

        assertFalse(fixture.store.stagingFile(transaction.id).exists())
        assertEquals(null, fixture.store.load(transaction.id))
        assertTrue(unrelated.isFile)
        assertNotNull(unrelated.readText())
    }

    private fun fixture(maxOutputBytes: Long = 1024L): Fixture {
        val sourceDirectory = temporaryFolder.newFolder("source")
        val source = File(sourceDirectory, "photo.png").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val journalRoot = temporaryFolder.newFolder("journal")
        val stagingRoot = temporaryFolder.newFolder("staging")
        var now = 1_000L
        val store = ExplorerActionOutputTransactionStore(
            journalRoot = journalRoot,
            stagingRoot = stagingRoot,
            clock = { now++ },
            idFactory = { TRANSACTION_ID },
        )
        return Fixture(
            source = source,
            store = store,
            request = ExplorerActionOutputPrepareRequest(
                pluginPackageName = "com.example.image",
                pluginActivityClassName = "com.example.image.OutputActivity",
                pluginUid = 12_345,
                pluginVersionCode = 7L,
                pluginLastUpdateTime = 999L,
                pluginSigningDigests = setOf("a".repeat(64)),
                actionId = "edit-image",
                protocolVersion = 3,
                sourcePath = source.path,
                sourceParentDevice = 1L,
                sourceParentInode = 2L,
                sourceDisplayName = source.name,
                sourceMimeType = "image/png",
                sourceSurface = "main",
                allowedOutputMimeTypes = setOf("image/png", "image/jpeg"),
                outputNameSuffix = "edited",
                maxOutputBytes = maxOutputBytes,
            ),
        )
    }

    private data class Fixture(
        val source: File,
        val store: ExplorerActionOutputTransactionStore,
        val request: ExplorerActionOutputPrepareRequest,
    )

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected ${T::class.java.name}, got $error", error is T)
    }

    private companion object {
        const val TRANSACTION_ID = "123e4567-e89b-42d3-a456-426614174000"
    }
}
