package org.autojs.autojs.core.plugin.explorer.session

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Protocol v24: a read-only `targetTrash` action moves request targets into host Trash one deletion at a time. */
@RunWith(AndroidJUnit4::class)
class ExplorerActionTargetTrashV24InstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository by lazy { TrashRepository(context.applicationContext) }

    @Test
    fun readOnlyTargetTrashMovesAnySubsetAndRestoresIt() {
        val testRoot = newRoot("subset")
        val bytes = listOf("first image", "second image", "third image").map(String::encodeToByteArray)
        val files = listOf("a.png", "b.png", "c.png").mapIndexed { index, name ->
            File(testRoot, name).apply { writeBytes(bytes[index]) }
        }
        val refreshCount = AtomicInteger()
        val session = viewerSession(testRoot, files, onSourcesTrashed = { refreshCount.incrementAndGet() })
        val trashIds = ArrayList<String>()
        val batchIds = ArrayList<String>()

        try {
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_STATE_UNKNOWN,
                session.queryTargetTrash().getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
            )

            // The viewer deletes the page it shows: one target, no output proof.
            val first = session.moveTargetsToTrash(mutableListOf("b"), mutableListOf())
            val firstBatch = committedBatch(first, movedCount = 1, trashIds, batchIds)
            assertFalse(files[1].exists())
            assertTrue(files[0].exists() && files[2].exists())
            assertEquals(1, refreshCount.get())
            assertEquals(
                firstBatch,
                session.queryTargetTrash().getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID),
            )

            // A client whose response was lost repeats the call and receives the same result.
            val repeated = session.moveTargetsToTrash(mutableListOf("b"), mutableListOf())
            assertEquals(firstBatch, repeated.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID))
            assertEquals(1, refreshCount.get())
            assertEquals(
                mapOf("b.png" to ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_MOVED),
                itemStates(session.queryTargetTrashBatch(firstBatch)),
            )

            // A later deletion in the same session records its own batch.
            val second = session.moveTargetsToTrash(mutableListOf("a", "c"), mutableListOf())
            val secondBatch = committedBatch(second, movedCount = 2, trashIds, batchIds)
            assertNotEquals(firstBatch, secondBatch)
            assertFalse(files[0].exists() || files[2].exists())
            assertEquals(2, refreshCount.get())
            assertTrue(listedBatchIds(session).containsAll(listOf(firstBatch, secondBatch)))
            expectIllegalState { session.moveTargetsToTrash(mutableListOf("a"), mutableListOf()) }

            // Undo restores the exact bytes, and the restored target can be deleted again.
            val restored = session.undoTargetTrashBatch(firstBatch)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED,
                restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertArrayEquals(bytes[1], files[1].readBytes())
            assertEquals(3, refreshCount.get())
            val third = session.moveTargetsToTrash(mutableListOf("b"), mutableListOf())
            val thirdBatch = committedBatch(third, movedCount = 1, trashIds, batchIds)
            assertNotEquals(firstBatch, thirdBatch)
            assertFalse(files[1].exists())

            listOf(secondBatch, thirdBatch).forEach { batchId ->
                assertEquals(
                    ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED,
                    session.undoTargetTrashBatch(batchId)
                        .getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
                )
            }
            files.forEachIndexed { index, file -> assertArrayEquals(bytes[index], file.readBytes()) }
            assertTrue(trashIds.none { trashId -> repository.findTrashItem(trashId) != null })
        } finally {
            cleanUp(session, testRoot, trashIds, batchIds)
        }
    }

    @Test
    fun readOnlyTargetTrashRejectsProofOrderAndChangedTargetsWithoutTouchingThem() {
        val testRoot = newRoot("reject")
        val first = File(testRoot, "first.png").apply { writeText("first") }
        val second = File(testRoot, "second.png").apply { writeText("second") }
        val session = viewerSession(testRoot, listOf(first, second))

        try {
            val proof = mutableListOf(UUID.randomUUID().toString())
            expectIllegalArgument { session.moveTargetsToTrash(mutableListOf("first"), proof) }
            expectIllegalArgument { session.moveTargetsToTrash(mutableListOf(), mutableListOf()) }
            expectIllegalArgument { session.moveTargetsToTrash(mutableListOf("unknown"), mutableListOf()) }
            expectIllegalArgument { session.moveTargetsToTrash(mutableListOf("first", "first"), mutableListOf()) }
            expectIllegalArgument { session.moveTargetsToTrash(mutableListOf("second", "first"), mutableListOf()) }

            second.writeText("second, edited after the viewer opened it")
            expectIllegalState { session.moveTargetsToTrash(mutableListOf("second"), mutableListOf()) }

            assertEquals("first", first.readText())
            assertEquals("second, edited after the viewer opened it", second.readText())
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_STATE_UNKNOWN,
                session.queryTargetTrash().getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
            )
            assertTrue(listedBatchIds(session).isEmpty())
        } finally {
            cleanUp(session, testRoot, emptyList(), emptyList())
        }
    }

    @Test
    fun sessionsWithoutTargetTrashKeepTheV16Contract() {
        val testRoot = newRoot("v16")
        val target = File(testRoot, "photo.png").apply { writeText("photo") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("photo", target)),
            allowCreateOutput = false,
            allowReadSiblings = true,
        )

        try {
            expectIllegalArgument { session.moveTargetsToTrash(mutableListOf("photo"), mutableListOf()) }
            expectIllegalState { session.listTargetTrashBatches() }
            expectIllegalState { session.queryTargetTrashBatch(UUID.randomUUID().toString()) }
            assertEquals("photo", target.readText())
            expectIllegalArgument {
                ExplorerActionHostSession.create(
                    context = context,
                    pluginPackageName = context.packageName,
                    commonParent = testRoot,
                    targets = listOf(ExplorerActionHostSessionTarget("photo", target)),
                    allowCreateOutput = true,
                    allowTargetTrash = true,
                )
            }
        } finally {
            cleanUp(session, testRoot, emptyList(), emptyList())
        }
    }

    @Test
    fun viewerDeletionHistoryMakesRoomWithoutLosingTrashItemsOrArchiveHistory() {
        val testRoot = newRoot("capacity")
        val archiveSource = File(testRoot, "archive-source.txt").apply { writeText("archive source") }
        val archiveSession = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive-source", archiveSource)),
            allowCreateOutput = true,
        )
        val count = ExplorerActionProtocol.MAX_TARGET_TRASH_BATCH_RESULTS + 1
        val images = (0 until count).map { index -> File(testRoot, "image-$index.png").apply { writeText("image $index") } }
        val viewer = viewerSession(testRoot, images, idOf = { file -> file.nameWithoutExtension })
        val trashIds = ArrayList<String>()
        val batchIds = ArrayList<String>()

        try {
            // A v21 archive batch that can still be restored must never be evicted for room.
            val outputId = prepareVerifiedOutput(archiveSession, "sources.zip", "archive".encodeToByteArray())
            archiveSession.commitOutput(outputId)
            val archive = archiveSession.moveTargetsToTrash(mutableListOf("archive-source"), mutableListOf(outputId))
            val archiveBatch = committedBatch(archive, movedCount = 1, trashIds, batchIds)

            val viewerBatches = images.map { image ->
                val result = viewer.moveTargetsToTrash(mutableListOf(image.nameWithoutExtension), mutableListOf())
                committedBatch(result, movedCount = 1, trashIds, batchIds)
            }
            // The oldest viewer batch made room: its undo record is gone, its Trash item is not.
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_UNKNOWN,
                viewer.queryTargetTrashBatch(viewerBatches.first())
                    .getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertNotNull(repository.findTrashItem(trashIds[1]))
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_AVAILABLE,
                viewer.queryTargetTrashBatch(viewerBatches.last())
                    .getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_AVAILABLE,
                archiveSession.queryTargetTrashBatch(archiveBatch)
                    .getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
        } finally {
            runCatching { archiveSession.close() }
            cleanUp(viewer, testRoot, trashIds, batchIds)
        }
    }

    @Test
    fun theFileManagerCanCreateSessionsOnTheMainThreadOnceTrashHistoryExists() {
        // Recovering the history reads the Trash database, which Room refuses on the main thread; a real host
        // failed every plugin action launch after the first viewer deletion (2026-10-15).
        val testRoot = newRoot("main-thread")
        val bytes = listOf("first image", "second image").map(String::encodeToByteArray)
        val files = listOf("a.png", "b.png").mapIndexed { index, name ->
            File(testRoot, name).apply { writeBytes(bytes[index]) }
        }
        val first = viewerSession(testRoot, files)
        val trashIds = ArrayList<String>()
        val batchIds = ArrayList<String>()
        var second: ExplorerActionHostSession? = null
        try {
            committedBatch(first.moveTargetsToTrash(mutableListOf("a"), mutableListOf()), 1, trashIds, batchIds)

            var failure: Throwable? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runCatching { viewerSession(testRoot, listOf(files[1])) }
                    .onSuccess { second = it }
                    .onFailure { failure = it }
            }
            failure?.let { throw AssertionError("Session creation on the main thread failed", it) }

            // The deferred recovery holds nothing up: the new session deletes and restores at once.
            val session = requireNotNull(second)
            val moved = session.moveTargetsToTrash(mutableListOf("b"), mutableListOf())
            val batchId = committedBatch(moved, 1, trashIds, batchIds)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED,
                session.undoTargetTrashBatch(batchId).getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertArrayEquals(bytes[1], files[1].readBytes())
        } finally {
            second?.let { runCatching { it.close() } }
            cleanUp(first, testRoot, trashIds, batchIds)
        }
    }

    private fun newRoot(name: String): File =
        File(context.cacheDir, "explorer-action-v24-$name-${System.nanoTime()}").apply { assertTrue(mkdirs()) }

    private fun viewerSession(
        root: File,
        files: List<File>,
        idOf: (File) -> String = { file -> file.nameWithoutExtension },
        onSourcesTrashed: () -> Unit = {},
    ): ExplorerActionHostSession = ExplorerActionHostSession.create(
        context = context,
        pluginPackageName = context.packageName,
        commonParent = root,
        targets = files.map { file -> ExplorerActionHostSessionTarget(idOf(file), file) },
        allowCreateOutput = false,
        allowTargetTrash = true,
        allowReadSiblings = files.size == 1,
        onSourcesTrashed = onSourcesTrashed,
    )

    private fun committedBatch(
        result: Bundle,
        movedCount: Int,
        trashIds: MutableList<String>,
        batchIds: MutableList<String>,
    ): String {
        assertEquals(
            ExplorerActionHostSessionValues.TARGET_TRASH_STATE_COMMITTED,
            result.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
        )
        assertEquals(movedCount, result.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_MOVED_COUNT))
        assertEquals(0, result.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RECOVERY_COUNT))
        trashIds += result.getStringArrayList(ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_IDS).orEmpty()
        val batchId = requireNotNull(result.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID))
        assertEquals(batchId, UUID.fromString(batchId).toString())
        batchIds += batchId
        return batchId
    }

    private fun listedBatchIds(session: ExplorerActionHostSession): List<String> =
        BundleCompat.getParcelableArrayList(
            session.listTargetTrashBatches(),
            ExplorerActionHostSessionKeys.TARGET_TRASH_BATCHES,
            Bundle::class.java,
        ).orEmpty().mapNotNull { batch -> batch.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID) }

    private fun itemStates(batch: Bundle): Map<String, Int> =
        BundleCompat.getParcelableArrayList(
            batch,
            ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ITEMS,
            Bundle::class.java,
        ).orEmpty().associate { item ->
            requireNotNull(item.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_DISPLAY_NAME)) to
                item.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_STATE)
        }

    private fun prepareVerifiedOutput(
        session: ExplorerActionHostSession,
        displayName: String,
        bytes: ByteArray,
    ): String {
        val transactionId = requireNotNull(
            session.prepareOutput(
                displayName,
                "application/octet-stream",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
            ).getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
        )
        ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use { it.write(bytes) }
        ParcelFileDescriptor.AutoCloseInputStream(session.openPendingOutput(transactionId)).use { it.readBytes() }
        return transactionId
    }

    private fun cleanUp(
        session: ExplorerActionHostSession,
        root: File,
        trashIds: List<String>,
        batchIds: List<String>,
    ) {
        runCatching { session.close() }
        trashIds.mapNotNull(repository::findTrashItem).forEach(repository::deleteTrashItem)
        val store = ExplorerActionTargetTrashBatchStore(context)
        batchIds.forEach(store::remove)
        root.deleteRecursively()
    }

    private fun expectIllegalState(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            return
        }
        throw AssertionError("Expected IllegalStateException")
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}
