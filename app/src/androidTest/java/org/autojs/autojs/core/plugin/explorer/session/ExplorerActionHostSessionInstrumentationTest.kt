package org.autojs.autojs.core.plugin.explorer.session

import android.os.ParcelFileDescriptor
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import androidx.core.os.BundleCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.TrashBlobStore
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs6.R
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExplorerActionHostSessionInstrumentationTest {

    @Test
    fun selectedExtractionDirectoryDoesNotChangeSourceAccessOrAllowSourceTrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "archive-destination-${System.nanoTime()}").apply { mkdirs() }
        val sourceParent = File(root, "source").apply { mkdirs() }
        val destination = File(root, "destination").apply { mkdirs() }
        val source = File(sourceParent, "archive.zip").apply { writeText("source") }
        val sibling = File(destination, "private.txt").apply { writeText("unrelated") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = sourceParent,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
            selectedOutputDirectory = destination,
            onOutputCommitted = {},
        )
        try {
            val prepared = session.prepareOutputTree("unpacked", ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME)
            val id = requireNotNull(prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID))
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutputFile(id, "readme.txt")).use { it.write("content".toByteArray()) }
            session.commitOutput(id)
            assertEquals("content", File(destination, "unpacked/readme.txt").readText())
            assertFalse(File(sourceParent, "unpacked").exists())
            assertEquals("source", source.readText())
            assertEquals("unrelated", sibling.readText())
            assertTrue(runCatching { session.openFile("source", "../destination/private.txt") }.isFailure)
            assertTrue(runCatching { session.prepareOutput("file.zip", "application/zip", 0) }.isFailure)
            assertTrue(runCatching { session.moveTargetsToTrash(arrayListOf("source"), arrayListOf(id)) }.isFailure)
            assertTrue(runCatching { session.listTargetTrashBatches() }.isFailure)
        } finally {
            session.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun clientBinderDeathRollsBackAnUncommittedOutputTree() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v9-death-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.zip").apply { writeText("source") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        val outputTreeStore = ExplorerActionOutputTreeStore(context)
        val clientToken = SyntheticDeathToken()
        var transactionId: String? = null

        try {
            session.attachClient(clientToken)
            val prepared = session.prepareOutputTree(
                "interrupted",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME,
            )
            val activeTransactionId = requireNotNull(
                prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            transactionId = activeTransactionId
            ParcelFileDescriptor.AutoCloseOutputStream(
                session.openOutputFile(activeTransactionId, "partial.bin"),
            ).use { it.write(byteArrayOf(1, 2, 3)) }
            val outputRoot = File(testRoot, "interrupted")
            assertTrue(outputRoot.isDirectory)

            clientToken.die()
            val deadline = SystemClock.elapsedRealtime() + CLIENT_DEATH_TIMEOUT_MILLIS
            var durableState = outputTreeStore.read(activeTransactionId)?.state
            while (
                (outputRoot.exists() ||
                    durableState != ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED) &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(10L)
                durableState = outputTreeStore.read(activeTransactionId)?.state
            }

            assertFalse(outputRoot.exists())
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                durableState,
            )
        } finally {
            runCatching { session.close() }
            transactionId?.let(outputTreeStore::remove)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun outputTreeCommitsNestedContentAndRemainsQueryable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v9-tree-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "archive.zip").apply { writeText("source") }
        val notifiedOutput = AtomicReference<File>()
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = true,
            onOutputCommitted = notifiedOutput::set,
        )
        var recoverySession: ExplorerActionHostSession? = null
        var transactionId: String? = null

        try {
            val prepared = session.prepareOutputTree(
                "archive",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME,
            )
            transactionId = requireNotNull(
                prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_KIND_DIRECTORY_TREE,
                prepared.getInt(ExplorerActionHostSessionKeys.OUTPUT_KIND),
            )
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_PREPARED,
                session.queryOutput(transactionId).getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )

            session.createOutputDirectory(transactionId, "folder")
            val expected = "nested extraction output".encodeToByteArray()
            ParcelFileDescriptor.AutoCloseOutputStream(
                session.openOutputFile(transactionId, "folder/file.txt"),
            ).use { it.write(expected) }
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_WRITING,
                session.queryOutput(transactionId).getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )
            @Suppress("DEPRECATION")
            val listed = session.listOutputs()
                .getParcelableArrayList<android.os.Bundle>(ExplorerActionHostSessionKeys.OUTPUTS)
            assertNotNull(listed?.firstOrNull {
                it.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID) == transactionId
            })

            val committed = session.commitOutput(transactionId)
            val outputRoot = File(testRoot, "archive")
            assertTrue(outputRoot.isDirectory)
            assertTrue(explorerItemForCreatedOutput(outputRoot) is ExplorerDirPage)
            assertArrayEquals(expected, File(outputRoot, "folder/file.txt").readBytes())
            assertEquals(outputRoot.canonicalFile, notifiedOutput.get()?.canonicalFile)
            assertEquals(1, committed.getInt(ExplorerActionHostSessionKeys.OUTPUT_FILE_COUNT))
            assertEquals(1, committed.getInt(ExplorerActionHostSessionKeys.OUTPUT_DIRECTORY_COUNT))
            assertEquals(expected.size.toLong(), committed.getLong(ExplorerActionHostSessionKeys.OUTPUT_BYTES))
            assertFalse(outputRoot.listFiles().orEmpty().any { it.name.startsWith(".autojs6-explorer-") })
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED,
                session.queryOutput(transactionId).getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )

            session.close()
            recoverySession = ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = context.packageName,
                commonParent = testRoot,
                targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
                allowCreateOutput = true,
            )
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED,
                recoverySession.queryOutput(transactionId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )
        } finally {
            recoverySession?.close()
            runCatching { session.close() }
            transactionId?.let { ExplorerActionOutputTreeStore(context).remove(it) }
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun deeplyNestedOutputTreesCommitAndAbortWithoutRecursiveTraversal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v9-deep-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.zip").apply { writeText("source") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        val transactionIds = mutableListOf<String>()
        val committedDirectories = ArrayList<File>(DEEP_OUTPUT_TREE_DEPTH)

        try {
            val commitPrepared = session.prepareOutputTree(
                "deep-commit",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME,
            )
            val commitId = requireNotNull(
                commitPrepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            ).also(transactionIds::add)
            val commitRoot = File(testRoot, "deep-commit")
            var relativePath = ""
            repeat(DEEP_OUTPUT_TREE_DEPTH) {
                relativePath = if (relativePath.isEmpty()) "d" else "$relativePath/d"
                session.createOutputDirectory(commitId, relativePath)
                committedDirectories += File(commitRoot, relativePath)
            }

            val committed = session.commitOutput(commitId)
            assertEquals(
                DEEP_OUTPUT_TREE_DEPTH,
                committed.getInt(ExplorerActionHostSessionKeys.OUTPUT_DIRECTORY_COUNT),
            )
            assertEquals(0, committed.getInt(ExplorerActionHostSessionKeys.OUTPUT_FILE_COUNT))
            assertTrue(committedDirectories.last().isDirectory)

            val abortPrepared = session.prepareOutputTree(
                "deep-abort",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME,
            )
            val abortId = requireNotNull(
                abortPrepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            ).also(transactionIds::add)
            relativePath = ""
            repeat(DEEP_OUTPUT_TREE_DEPTH) {
                relativePath = if (relativePath.isEmpty()) "d" else "$relativePath/d"
                session.createOutputDirectory(abortId, relativePath)
            }
            session.abortOutput(abortId)

            assertFalse(File(testRoot, "deep-abort").exists())
        } finally {
            runCatching { session.close() }
            transactionIds.forEach { ExplorerActionOutputTreeStore(context).remove(it) }
            committedDirectories.asReversed().forEach { directory -> directory.delete() }
            File(testRoot, "deep-commit").delete()
            source.delete()
            testRoot.delete()
        }
    }

    @Test
    fun outputTreeNeverReusesExistingRootAndAbortRollsBackOnlyItsRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v9-abort-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.zip").apply { writeText("source") }
        val existing = File(testRoot, "output").apply {
            assertTrue(mkdir())
            File(this, "sentinel.txt").writeText("keep")
        }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        var transactionId: String? = null

        try {
            val prepared = session.prepareOutputTree(
                "output",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME,
            )
            transactionId = requireNotNull(
                prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            assertEquals("output (2)", prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_NAME))
            session.createOutputDirectory(transactionId, "Folder")
            expectIllegalState { session.createOutputDirectory(transactionId, "folder") }
            expectIllegalArgument { session.createOutputDirectory(transactionId, "../escape") }
            ParcelFileDescriptor.AutoCloseOutputStream(
                session.openOutputFile(transactionId, "Folder/partial.bin"),
            ).use { it.write(byteArrayOf(1, 2, 3)) }

            session.abortOutput(transactionId)

            assertFalse(File(testRoot, "output (2)").exists())
            assertEquals("keep", File(existing, "sentinel.txt").readText())
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                session.queryOutput(transactionId).getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )
        } finally {
            runCatching { session.close() }
            transactionId?.let { ExplorerActionOutputTreeStore(context).remove(it) }
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun abandonedOutputTreeJournalIsRolledBackByTheNextHostSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v9-recovery-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.zip").apply { writeText("source") }
        val transactionId = UUID.randomUUID().toString()
        val outputRoot = File(testRoot, "recovered-output").apply { assertTrue(mkdir()) }
        val markerName = ".autojs6-explorer-transaction-$transactionId"
        File(outputRoot, markerName).writeText(transactionId)
        File(outputRoot, "partial.bin").writeBytes(byteArrayOf(4, 5, 6))
        val parentStat = Os.stat(testRoot.path)
        val targetStat = Os.stat(outputRoot.path)
        val now = System.currentTimeMillis()
        val store = ExplorerActionOutputTreeStore(context)
        store.write(
            ExplorerActionOutputTreeRecord(
                id = transactionId,
                pluginUid = context.applicationInfo.uid,
                processInstanceId = "dead-host-process",
                parentPath = testRoot.canonicalPath,
                parentDevice = parentStat.st_dev,
                parentInode = parentStat.st_ino,
                targetPath = outputRoot.canonicalPath,
                targetDevice = targetStat.st_dev,
                targetInode = targetStat.st_ino,
                displayName = outputRoot.name,
                markerName = markerName,
                state = ExplorerActionHostSessionValues.OUTPUT_STATE_WRITING,
                createdAtMillis = now - 1_000L,
                updatedAtMillis = now - 1_000L,
            ),
        )

        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        try {
            assertFalse(outputRoot.exists())
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                session.queryOutput(transactionId).getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )
        } finally {
            session.close()
            store.remove(transactionId)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun missingMarkerIsCommittedOnlyAfterTheDurableVerifyingState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v9-boundary-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.zip").apply { writeText("source") }
        val parentStat = Os.stat(testRoot.path)
        val store = ExplorerActionOutputTreeStore(context)
        val now = System.currentTimeMillis()
        val writingId = UUID.randomUUID().toString()
        val writingRoot = File(testRoot, "externally-tampered").apply {
            assertTrue(mkdir())
            File(this, "partial.bin").writeBytes(byteArrayOf(1))
        }
        val verifyingId = UUID.randomUUID().toString()
        val verifyingRoot = File(testRoot, "commit-boundary-crossed").apply {
            assertTrue(mkdir())
            File(this, "complete.bin").writeBytes(byteArrayOf(2))
        }

        fun record(id: String, root: File, state: Int): ExplorerActionOutputTreeRecord {
            val targetStat = Os.stat(root.path)
            return ExplorerActionOutputTreeRecord(
                id = id,
                pluginUid = context.applicationInfo.uid,
                processInstanceId = "dead-host-process",
                parentPath = testRoot.canonicalPath,
                parentDevice = parentStat.st_dev,
                parentInode = parentStat.st_ino,
                targetPath = root.canonicalPath,
                targetDevice = targetStat.st_dev,
                targetInode = targetStat.st_ino,
                displayName = root.name,
                markerName = ".autojs6-explorer-transaction-$id",
                state = state,
                createdAtMillis = now - 1_000L,
                updatedAtMillis = now - 1_000L,
            )
        }
        store.write(
            record(
                writingId,
                writingRoot,
                ExplorerActionHostSessionValues.OUTPUT_STATE_WRITING,
            ),
        )
        store.write(
            record(
                verifyingId,
                verifyingRoot,
                ExplorerActionHostSessionValues.OUTPUT_STATE_VERIFYING,
            ),
        )

        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        try {
            assertTrue(writingRoot.isDirectory)
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                session.queryOutput(writingId).getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )
            assertTrue(verifyingRoot.isDirectory)
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED,
                session.queryOutput(verifyingId).getInt(ExplorerActionHostSessionKeys.OUTPUT_STATE),
            )
        } finally {
            session.close()
            store.remove(writingId)
            store.remove(verifyingId)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun targetReplacementRequiresVerificationAndRefreshesChangedItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v8-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "archive.zip").apply { writeText("original archive") }
        val notifiedReplacement = AtomicReference<File>()
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = false,
            allowReplaceTarget = true,
            onTargetReplaced = notifiedReplacement::set,
        )

        try {
            val prepared = session.prepareTargetReplacement("archive")
            val transactionId = requireNotNull(
                prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            val expected = "verified replacement archive".encodeToByteArray()
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
                it.write(expected)
            }

            expectIllegalState {
                session.commitOutput(transactionId)
            }
            val pending = ParcelFileDescriptor.AutoCloseInputStream(
                session.openPendingOutput(transactionId),
            ).use { it.readBytes() }
            assertArrayEquals(expected, pending)

            val committed = session.commitOutput(transactionId)
            assertArrayEquals(expected, source.readBytes())
            assertEquals(source.canonicalFile, notifiedReplacement.get()?.canonicalFile)
            assertEquals(expected.size.toLong(), committed.getLong(ExplorerActionHostSessionKeys.SIZE))
            assertFalse(
                committed.containsKey(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID),
            )
            expectIllegalState { session.queryTargetReplacement("archive") }
            assertFalse(testRoot.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun replacementHistoryRestoresThePreviousArchiveExactlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v18-undo-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val original = "original archive version".encodeToByteArray()
        val replacement = "verified replacement archive".encodeToByteArray()
        val source = File(testRoot, "archive.zip").apply { writeBytes(original) }
        val notifications = AtomicInteger()
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = false,
            allowReplaceTarget = true,
            allowReplacementUndo = true,
            onTargetReplaced = { notifications.incrementAndGet() },
        )

        try {
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_NONE,
                session.queryTargetReplacement("archive")
                    .getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
            val transactionId = requireNotNull(
                session.prepareTargetReplacement("archive")
                    .getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
                it.write(replacement)
            }
            ParcelFileDescriptor.AutoCloseInputStream(
                session.openPendingOutput(transactionId),
            ).use { assertArrayEquals(replacement, it.readBytes()) }

            val committed = session.commitOutput(transactionId)
            val historyId = requireNotNull(
                committed.getString(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID),
            )
            assertArrayEquals(replacement, source.readBytes())
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_AVAILABLE,
                committed.getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
            assertEquals(
                original.size.toLong(),
                committed.getLong(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_PREVIOUS_SIZE),
            )
            assertEquals(
                historyId,
                session.queryTargetReplacement("archive")
                    .getString(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID),
            )

            val restored = session.undoTargetReplacement("archive", historyId)
            assertArrayEquals(original, source.readBytes())
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_RESTORED,
                restored.getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
            assertEquals(original.size.toLong(), restored.getLong(ExplorerActionHostSessionKeys.SIZE))
            assertEquals(2, notifications.get())
            expectIllegalState { session.undoTargetReplacement("archive", historyId) }
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_RESTORED,
                session.queryTargetReplacement("archive")
                    .getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
        } finally {
            session.close()
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun externallyChangedReplacementBecomesStaleAndIsNeverOverwrittenByUndo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v18-stale-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "archive.zip").apply { writeText("original archive") }
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = false,
            allowReplaceTarget = true,
            allowReplacementUndo = true,
        )

        try {
            val transactionId = requireNotNull(
                session.prepareTargetReplacement("archive")
                    .getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
                it.write("replacement archive".encodeToByteArray())
            }
            ParcelFileDescriptor.AutoCloseInputStream(
                session.openPendingOutput(transactionId),
            ).use { it.readBytes() }
            val historyId = requireNotNull(
                session.commitOutput(transactionId)
                    .getString(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID),
            )

            val external = "external archive update that must remain untouched"
            source.writeText(external)
            val queried = session.queryTargetReplacement("archive")
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_STALE,
                queried.getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
            expectIllegalState { session.undoTargetReplacement("archive", historyId) }
            assertEquals(external, source.readText())
        } finally {
            session.close()
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun corruptedReplacementBackupRequiresRecoveryAndLeavesNoRestoreTemp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v18-corrupt-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "archive.zip").apply { writeText("original archive") }
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = false,
            allowReplaceTarget = true,
            allowReplacementUndo = true,
        )

        try {
            val transactionId = requireNotNull(
                session.prepareTargetReplacement("archive")
                    .getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
                it.write("replacement archive".encodeToByteArray())
            }
            ParcelFileDescriptor.AutoCloseInputStream(
                session.openPendingOutput(transactionId),
            ).use { it.readBytes() }
            val historyId = requireNotNull(
                session.commitOutput(transactionId)
                    .getString(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID),
            )
            val history = requireNotNull(
                historyStore.queryLatest(context.applicationInfo.uid, source),
            )
            val backup = File(
                context.noBackupFilesDir,
                "explorer-action-replacement-history/${history.backupRelativePath}",
            )
            val corrupted = backup.readBytes().also { bytes ->
                bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
            }
            backup.writeBytes(corrupted)

            expectIllegalState { session.undoTargetReplacement("archive", historyId) }
            assertEquals("replacement archive", source.readText())
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_RECOVERY_REQUIRED,
                session.queryTargetReplacement("archive")
                    .getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
            assertTrue(
                testRoot.listFiles().orEmpty().none { file ->
                    file.name.startsWith(".autojs6-explorer-") && file.name.endsWith(".part")
                },
            )
        } finally {
            session.close()
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun preparedReplacementHistoryRecoversAfterAtomicRenameInterruption() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v18-recovery-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val original = "original archive before interruption".encodeToByteArray()
        val replacement = "replacement archive after interruption".encodeToByteArray()
        val source = File(testRoot, "archive.zip").apply { writeBytes(original) }
        val candidate = File(testRoot, ".autojs6-explorer-${UUID.randomUUID()}.part").apply {
            writeBytes(replacement)
        }
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        val record = historyStore.prepare(
            transactionId = UUID.randomUUID().toString(),
            pluginUid = context.applicationInfo.uid,
            parent = testRoot,
            target = source,
            previousIdentity = ExplorerActionFileIdentity.captureRegularFile(source),
            replacementIdentity = ExplorerActionFileIdentity.captureRegularFile(candidate),
        )
        Os.rename(candidate.path, source.path)
        var session: ExplorerActionHostSession? = null

        try {
            session = ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = context.packageName,
                commonParent = testRoot,
                targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
                allowCreateOutput = false,
                allowReplaceTarget = true,
                allowReplacementUndo = true,
            )
            val recovered = session.queryTargetReplacement("archive")
            assertEquals(record.id, recovered.getString(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID))
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_AVAILABLE,
                recovered.getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
            session.undoTargetReplacement("archive", record.id)
            assertArrayEquals(original, source.readBytes())
        } finally {
            session?.close()
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            candidate.delete()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun interruptedAtomicJournalWriteRecoversPreparedReplacementHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v18-journal-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val original = "original archive before journal interruption".encodeToByteArray()
        val replacement = "replacement archive after journal interruption".encodeToByteArray()
        val source = File(testRoot, "archive.zip").apply { writeBytes(original) }
        val candidate = File(testRoot, "replacement.part").apply { writeBytes(replacement) }
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        val record = historyStore.prepare(
            transactionId = UUID.randomUUID().toString(),
            pluginUid = context.applicationInfo.uid,
            parent = testRoot,
            target = source,
            previousIdentity = ExplorerActionFileIdentity.captureRegularFile(source),
            replacementIdentity = ExplorerActionFileIdentity.captureRegularFile(candidate),
        )
        Os.rename(candidate.path, source.path)
        val journal = File(
            context.noBackupFilesDir,
            "explorer-action-replacement-history/records/${record.id}.json",
        )
        val atomicBackup = File(journal.path + ".bak")
        assertTrue(journal.renameTo(atomicBackup))

        try {
            historyStore.recover()
            val recovered = requireNotNull(
                historyStore.queryLatest(context.applicationInfo.uid, source),
            )
            assertEquals(record.id, recovered.id)
            assertEquals(ExplorerActionReplacementHistoryState.AVAILABLE, recovered.state)
            assertTrue(journal.isFile)
            assertFalse(atomicBackup.exists())
            historyStore.undo(context.applicationInfo.uid, source, record.id)
            assertArrayEquals(original, source.readBytes())
        } finally {
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            candidate.delete()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun replacementHistoryToleratesAClockThatMovesBackwards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v18-clock-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val original = "original archive before clock rollback".encodeToByteArray()
        val replacement = "replacement archive after clock rollback".encodeToByteArray()
        val source = File(testRoot, "archive.zip").apply { writeBytes(original) }
        val candidate = File(testRoot, "replacement.part").apply { writeBytes(replacement) }
        val clock = AtomicLong(2_000L)
        val historyStore = ExplorerActionReplacementHistoryStore(context, clock::get)
        val record = historyStore.prepare(
            transactionId = UUID.randomUUID().toString(),
            pluginUid = context.applicationInfo.uid,
            parent = testRoot,
            target = source,
            previousIdentity = ExplorerActionFileIdentity.captureRegularFile(source),
            replacementIdentity = ExplorerActionFileIdentity.captureRegularFile(candidate),
        )
        Os.rename(candidate.path, source.path)
        clock.set(1_000L)

        try {
            val available = historyStore.markAvailable(record.id)
            assertEquals(ExplorerActionReplacementHistoryState.AVAILABLE, available.state)
            assertEquals(2_000L, available.createdAtMillis)
            assertEquals(2_000L, available.updatedAtMillis)
            val restored = historyStore.undo(context.applicationInfo.uid, source, record.id)
            assertEquals(ExplorerActionReplacementHistoryState.RESTORED, restored.record.state)
            assertEquals(2_000L, restored.record.updatedAtMillis)
            assertArrayEquals(original, source.readBytes())
        } finally {
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            candidate.delete()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun unresolvedReplacementRecoveryIsPreservedAndBlocksAnotherReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v18-unresolved-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "archive.zip").apply { writeText("original archive") }
        val firstCandidate = File(testRoot, "first-candidate.part").apply {
            writeText("first replacement")
        }
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        val record = historyStore.prepare(
            transactionId = UUID.randomUUID().toString(),
            pluginUid = context.applicationInfo.uid,
            parent = testRoot,
            target = source,
            previousIdentity = ExplorerActionFileIdentity.captureRegularFile(source),
            replacementIdentity = ExplorerActionFileIdentity.captureRegularFile(firstCandidate),
        )

        try {
            source.writeText("unrelated external archive content")
            val secondCandidate = File(testRoot, "second-candidate.part").apply {
                writeText("second replacement")
            }
            expectIllegalState {
                historyStore.prepare(
                    transactionId = UUID.randomUUID().toString(),
                    pluginUid = context.applicationInfo.uid,
                    parent = testRoot,
                    target = source,
                    previousIdentity = ExplorerActionFileIdentity.captureRegularFile(source),
                    replacementIdentity =
                        ExplorerActionFileIdentity.captureRegularFile(secondCandidate),
                )
            }
            val unresolved = requireNotNull(
                historyStore.queryLatest(context.applicationInfo.uid, source),
            )
            assertEquals(record.id, unresolved.id)
            assertEquals(
                ExplorerActionReplacementHistoryState.RECOVERY_REQUIRED,
                unresolved.state,
            )
            assertEquals("unrelated external archive content", source.readText())
        } finally {
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun realReplacementRecoveryRequiresExternalTwoPhaseHarness() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments()
            .getString(REPLACEMENT_PROCESS_DEATH_PHASE_ARGUMENT)
            .orEmpty()
        assumeTrue(
            "This replacement recovery gate runs only through the external two-phase harness",
            phase in REPLACEMENT_PROCESS_DEATH_PHASES,
        )
        val context = instrumentation.targetContext
        val testRoot = File(context.cacheDir, REPLACEMENT_PROCESS_DEATH_ROOT_NAME)
        val source = File(testRoot, "archive.zip")
        val historyIdFile = File(
            context.filesDir,
            REPLACEMENT_PROCESS_DEATH_HISTORY_ID_FILE_NAME,
        )
        val readyFile = File(context.filesDir, REPLACEMENT_PROCESS_DEATH_READY_FILE_NAME)
        val historyStore = ExplorerActionReplacementHistoryStore(context)

        fun cleanup() {
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            testRoot.deleteRecursively()
            historyIdFile.delete()
            readyFile.delete()
        }

        when (phase) {
            REPLACEMENT_PROCESS_DEATH_COMMIT_PREPARE -> {
                cleanup()
                assertTrue(testRoot.mkdirs())
                source.writeText(REPLACEMENT_PROCESS_DEATH_ORIGINAL)
                val blockUntilKilled = CountDownLatch(1)
                val session = ExplorerActionHostSession.create(
                    context = context,
                    pluginPackageName = context.packageName,
                    commonParent = testRoot,
                    targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
                    allowCreateOutput = false,
                    allowReplaceTarget = true,
                    allowReplacementUndo = true,
                    afterReplacementPublished = {
                        writeDurableText(readyFile, "commit-ready")
                        blockUntilKilled.await()
                    },
                )
                val transactionId = prepareVerifiedReplacement(
                    session,
                    REPLACEMENT_PROCESS_DEATH_REPLACEMENT.encodeToByteArray(),
                )
                session.commitOutput(transactionId)
                throw AssertionError("External harness did not terminate the host process")
            }
            REPLACEMENT_PROCESS_DEATH_COMMIT_VERIFY -> {
                assertEquals("commit-ready", readyFile.readText())
                assertEquals(REPLACEMENT_PROCESS_DEATH_REPLACEMENT, source.readText())
                val session = ExplorerActionHostSession.create(
                    context = context,
                    pluginPackageName = context.packageName,
                    commonParent = testRoot,
                    targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
                    allowCreateOutput = false,
                    allowReplaceTarget = true,
                    allowReplacementUndo = true,
                )
                try {
                    val recovered = session.queryTargetReplacement("archive")
                    assertEquals(
                        ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_AVAILABLE,
                        recovered.getInt(
                            ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE,
                        ),
                    )
                    val historyId = requireNotNull(
                        recovered.getString(
                            ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID,
                        ),
                    )
                    session.undoTargetReplacement("archive", historyId)
                    assertEquals(REPLACEMENT_PROCESS_DEATH_ORIGINAL, source.readText())
                } finally {
                    session.close()
                    cleanup()
                }
            }
            REPLACEMENT_PROCESS_DEATH_RESTORE_PREPARE -> {
                cleanup()
                assertTrue(testRoot.mkdirs())
                source.writeText(REPLACEMENT_PROCESS_DEATH_ORIGINAL)
                val blockUntilKilled = CountDownLatch(1)
                val session = ExplorerActionHostSession.create(
                    context = context,
                    pluginPackageName = context.packageName,
                    commonParent = testRoot,
                    targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
                    allowCreateOutput = false,
                    allowReplaceTarget = true,
                    allowReplacementUndo = true,
                    afterReplacementRestored = {
                        writeDurableText(readyFile, "restore-ready")
                        blockUntilKilled.await()
                    },
                )
                val transactionId = prepareVerifiedReplacement(
                    session,
                    REPLACEMENT_PROCESS_DEATH_REPLACEMENT.encodeToByteArray(),
                )
                val historyId = requireNotNull(
                    session.commitOutput(transactionId).getString(
                        ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID,
                    ),
                )
                writeDurableText(historyIdFile, historyId)
                session.undoTargetReplacement("archive", historyId)
                throw AssertionError("External harness did not terminate the host process")
            }
            REPLACEMENT_PROCESS_DEATH_RESTORE_VERIFY -> {
                assertEquals("restore-ready", readyFile.readText())
                val historyId = historyIdFile.readText().trim()
                assertTrue(historyId.isNotEmpty())
                assertEquals(REPLACEMENT_PROCESS_DEATH_ORIGINAL, source.readText())
                val session = ExplorerActionHostSession.create(
                    context = context,
                    pluginPackageName = context.packageName,
                    commonParent = testRoot,
                    targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
                    allowCreateOutput = false,
                    allowReplaceTarget = true,
                    allowReplacementUndo = true,
                )
                try {
                    val recovered = session.queryTargetReplacement("archive")
                    assertEquals(
                        historyId,
                        recovered.getString(
                            ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID,
                        ),
                    )
                    assertEquals(
                        ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_RESTORED,
                        recovered.getInt(
                            ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE,
                        ),
                    )
                    expectIllegalState {
                        session.undoTargetReplacement("archive", historyId)
                    }
                } finally {
                    session.close()
                    cleanup()
                }
            }
        }
    }

    @Test
    fun realLowStorageRejectsReplacementBeforePublishingTheTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val enabled = InstrumentationRegistry.getArguments()
            .getString(REPLACEMENT_LOW_STORAGE_ARGUMENT)
            .orEmpty()
        assumeTrue(
            "This low-storage gate runs only through the external storage harness",
            enabled == REPLACEMENT_LOW_STORAGE_RUN,
        )
        val context = instrumentation.targetContext
        val testRoot = File(context.cacheDir, REPLACEMENT_LOW_STORAGE_ROOT_NAME)
        val source = File(testRoot, "archive.zip")
        val readyFile = File(context.filesDir, REPLACEMENT_LOW_STORAGE_READY_FILE_NAME)
        val fillFile = File(context.filesDir, REPLACEMENT_LOW_STORAGE_FILL_FILE_NAME)
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        historyStore.removeAllForTarget(context.applicationInfo.uid, source)
        testRoot.deleteRecursively()
        readyFile.delete()
        deleteLowStorageFill(fillFile)
        assertTrue(testRoot.mkdirs())
        val original = ByteArray(REPLACEMENT_LOW_STORAGE_SOURCE_BYTES) { index ->
            (index and 0xff).toByte()
        }
        source.writeBytes(original)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = false,
            allowReplaceTarget = true,
            allowReplacementUndo = true,
        )
        val transactionId = prepareVerifiedReplacement(
            session,
            "replacement must remain unpublished".encodeToByteArray(),
        )

        try {
            writeDurableText(readyFile, "ready")
            val requiredBytes = original.size.toLong() + REPLACEMENT_LOW_STORAGE_RESERVE_BYTES
            val deadline = SystemClock.elapsedRealtime() + REPLACEMENT_LOW_STORAGE_TIMEOUT_MILLIS
            while (
                testRoot.usableSpace >= requiredBytes &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(REPLACEMENT_LOW_STORAGE_POLL_MILLIS)
            }
            assertTrue(
                "External harness did not create the low-storage condition",
                fillFile.isFile && testRoot.usableSpace < requiredBytes,
            )
            expectIOException { session.commitOutput(transactionId) }
            assertArrayEquals(original, source.readBytes())
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_NONE,
                session.queryTargetReplacement("archive")
                    .getInt(ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE),
            )
        } finally {
            runCatching { session.abortOutput(transactionId) }
            session.close()
            readyFile.delete()
            historyStore.removeAllForTarget(context.applicationInfo.uid, source)
            testRoot.deleteRecursively()
            deleteLowStorageFill(fillFile)
        }
    }

    @Test
    fun changedReplacementTargetIsPreservedAndPendingOutputCanBeAborted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v8-race-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "archive.zip").apply { writeText("original archive") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = false,
            allowReplaceTarget = true,
        )

        try {
            val transactionId = requireNotNull(
                session.prepareTargetReplacement("archive")
                    .getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
                it.write("candidate replacement".encodeToByteArray())
            }
            ParcelFileDescriptor.AutoCloseInputStream(
                session.openPendingOutput(transactionId),
            ).use { it.readBytes() }

            val external = "external update that must survive"
            source.writeText(external)
            source.setLastModified(System.currentTimeMillis() + 2_000L)
            expectIllegalState {
                session.commitOutput(transactionId)
            }
            assertEquals(external, source.readText())

            session.abortOutput(transactionId)
            assertEquals(external, source.readText())
            assertFalse(testRoot.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun targetReplacementIsRejectedWithoutExplicitAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v8-denied-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "archive.zip").apply { writeText("original archive") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("archive", source)),
            allowCreateOutput = false,
        )

        try {
            expectIllegalState {
                session.prepareTargetReplacement("archive")
            }
            assertEquals("original archive", source.readText())
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun pendingOutputCanBeReadOnceBeforeAtomicCommit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v7-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val notifiedOutput = AtomicReference<File>()
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
            onOutputCommitted = notifiedOutput::set,
        )

        try {
            val prepared = session.prepareOutput(
                "verified.bin",
                "application/octet-stream",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
            )
            val transactionId = requireNotNull(
                prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            val expected = "pending output verification".encodeToByteArray()

            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
                it.write(expected)
            }
            val actual = ParcelFileDescriptor.AutoCloseInputStream(
                session.openPendingOutput(transactionId),
            ).use { it.readBytes() }

            assertArrayEquals(expected, actual)
            expectIllegalState {
                session.openPendingOutput(transactionId)
            }

            val committed = session.commitOutput(transactionId)
            val target = File(testRoot, "verified.bin")
            assertTrue(target.isFile)
            assertArrayEquals(expected, target.readBytes())
            assertEquals(target.canonicalFile, notifiedOutput.get()?.canonicalFile)
            assertEquals(expected.size.toLong(), committed.getLong(ExplorerActionHostSessionKeys.SIZE))
            assertFalse(testRoot.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun protocolV4StyleClientCanStillCommitWithoutVerification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v4-compat-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )

        try {
            val prepared = session.prepareOutput(
                "legacy.bin",
                "application/octet-stream",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
            )
            val transactionId = requireNotNull(
                prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
            )
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
                it.write(byteArrayOf(1, 2, 3))
            }

            session.commitOutput(transactionId)

            assertArrayEquals(byteArrayOf(1, 2, 3), File(testRoot, "legacy.bin").readBytes())
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun verifiedFileBatchPublishesEveryMemberBeforeRefreshingExplorer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v15-batch-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val notified = mutableListOf<File>()
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
            onOutputCommitted = { output -> notified += output },
        )
        val store = ExplorerActionOutputBatchStore(context)
        var batchId: String? = null

        try {
            val first = prepareVerifiedOutput(session, "first.zip", "first archive".encodeToByteArray())
            val second = prepareVerifiedOutput(session, "second.7z", "second archive".encodeToByteArray())
            val preparedBatch = session.prepareOutputBatch(mutableListOf(first, second))
            batchId = requireNotNull(
                preparedBatch.getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID),
            )

            assertFalse(File(testRoot, "first.zip").exists())
            assertFalse(File(testRoot, "second.7z").exists())
            assertTrue(notified.isEmpty())
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_PREPARED,
                preparedBatch.getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )

            val committed = session.commitOutputBatch(batchId)

            assertEquals("first archive", File(testRoot, "first.zip").readText())
            assertEquals("second archive", File(testRoot, "second.7z").readText())
            assertEquals(listOf("first.zip", "second.7z"), notified.map(File::getName))
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED,
                committed.getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertEquals(2, committed.getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_PUBLISHED_COUNT))
            @Suppress("DEPRECATION")
            val outputs = committed.getParcelableArrayList<Bundle>(
                ExplorerActionHostSessionKeys.OUTPUTS,
            )
            assertEquals(2, outputs?.size)
            assertTrue(testRoot.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            runCatching { session.close() }
            batchId?.let(store::remove)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun failedBatchPublicationRollsBackAlreadyPublishedMembers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v15-rollback-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
            afterBatchMemberPublished = { publishedCount ->
                if (publishedCount == 1) throw IOException("Injected publication failure")
            },
        )
        val store = ExplorerActionOutputBatchStore(context)
        var batchId: String? = null

        try {
            val first = prepareVerifiedOutput(session, "first.zip", byteArrayOf(1, 2, 3))
            val second = prepareVerifiedOutput(session, "second.zip", byteArrayOf(4, 5, 6))
            batchId = requireNotNull(
                session.prepareOutputBatch(mutableListOf(first, second))
                    .getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID),
            )

            expectIOException { session.commitOutputBatch(batchId) }

            assertFalse(File(testRoot, "first.zip").exists())
            assertFalse(File(testRoot, "second.zip").exists())
            assertTrue(testRoot.listFiles().orEmpty().none { it.name.endsWith(".part") })
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED,
                session.queryOutputBatch(batchId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
        } finally {
            runCatching { session.close() }
            batchId?.let(store::remove)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun interruptedBatchJournalRollsBackPublishedAndStagedMembersOnNextSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v15-recovery-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val batchId = UUID.randomUUID().toString()
        val firstId = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val published = File(testRoot, "first.zip").apply { writeText("published before crash") }
        val staging = File(testRoot, ".autojs6-explorer-${UUID.randomUUID()}.part").apply {
            writeText("still staged")
        }
        val parentStat = Os.stat(testRoot.path)
        val publishedStat = Os.lstat(published.path)
        val stagingStat = Os.lstat(staging.path)
        val now = System.currentTimeMillis()
        val store = ExplorerActionOutputBatchStore(context)
        store.write(
            ExplorerActionOutputBatchRecord(
                id = batchId,
                pluginUid = context.applicationInfo.uid,
                processInstanceId = "dead-host-process",
                parentPath = testRoot.canonicalPath,
                parentDevice = parentStat.st_dev,
                parentInode = parentStat.st_ino,
                state = ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTING,
                createdAtMillis = now - 1_000L,
                updatedAtMillis = now - 1_000L,
                members = listOf(
                    ExplorerActionOutputBatchMemberRecord(
                        transactionId = firstId,
                        targetPath = published.canonicalPath,
                        stagingPath = File(
                            testRoot,
                            ".autojs6-explorer-${UUID.randomUUID()}.part",
                        ).canonicalPath,
                        stagingDevice = publishedStat.st_dev,
                        stagingInode = publishedStat.st_ino,
                        stagingSize = publishedStat.st_size,
                        stagingLastModifiedMillis = published.lastModified(),
                    ),
                    ExplorerActionOutputBatchMemberRecord(
                        transactionId = secondId,
                        targetPath = File(testRoot, "second.zip").canonicalPath,
                        stagingPath = staging.canonicalPath,
                        stagingDevice = stagingStat.st_dev,
                        stagingInode = stagingStat.st_ino,
                        stagingSize = stagingStat.st_size,
                        stagingLastModifiedMillis = staging.lastModified(),
                    ),
                ),
                publishedCount = 0,
            ),
        )

        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        try {
            assertFalse(published.exists())
            assertFalse(staging.exists())
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED,
                session.queryOutputBatch(batchId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
        } finally {
            session.close()
            store.remove(batchId)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun outputBatchRejectsUnverifiedMembersWithoutReservingThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v15-unverified-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )

        try {
            val unverified = session.prepareOutput(
                "unverified.zip",
                "application/zip",
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
            ).getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID).let(::requireNotNull)
            ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(unverified)).use {
                it.write(byteArrayOf(1, 2, 3))
            }
            val verified = prepareVerifiedOutput(session, "verified.zip", byteArrayOf(4, 5, 6))

            expectIllegalState {
                session.prepareOutputBatch(mutableListOf(unverified, verified))
            }

            session.abortOutput(unverified)
            session.abortOutput(verified)
            assertFalse(File(testRoot, "unverified.zip").exists())
            assertFalse(File(testRoot, "verified.zip").exists())
            assertTrue(testRoot.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun outputBatchMembersCanOnlyBeCommittedOrAbortedAsAGroup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v15-group-only-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        val store = ExplorerActionOutputBatchStore(context)
        var batchId: String? = null

        try {
            val first = prepareVerifiedOutput(session, "first.zip", byteArrayOf(1))
            val second = prepareVerifiedOutput(session, "second.zip", byteArrayOf(2))
            batchId = session.prepareOutputBatch(mutableListOf(first, second))
                .getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID)
                .let(::requireNotNull)

            expectIllegalState { session.commitOutput(first) }
            expectIllegalState { session.abortOutput(second) }
            @Suppress("DEPRECATION")
            val listed = session.listOutputBatches().getParcelableArrayList<Bundle>(
                ExplorerActionHostSessionKeys.OUTPUT_BATCHES,
            )
            assertEquals(listOf(batchId), listed?.map {
                it.getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID)
            })

            session.abortOutputBatch(batchId)

            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED,
                session.queryOutputBatch(batchId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertFalse(File(testRoot, "first.zip").exists())
            assertFalse(File(testRoot, "second.zip").exists())
        } finally {
            runCatching { session.close() }
            batchId?.let(store::remove)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun batchCommitToleratesAClockThatMovesBackwards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v15-clock-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        var nowMillis = 10_000L
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
            clock = { nowMillis },
        )
        val store = ExplorerActionOutputBatchStore(context)
        var batchId: String? = null
        var recoverySession: ExplorerActionHostSession? = null

        try {
            val first = prepareVerifiedOutput(session, "first.zip", byteArrayOf(1))
            val second = prepareVerifiedOutput(session, "second.zip", byteArrayOf(2))
            batchId = session.prepareOutputBatch(mutableListOf(first, second))
                .getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID)
                .let(::requireNotNull)
            nowMillis = 1L

            val committed = session.commitOutputBatch(batchId)

            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED,
                committed.getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertTrue(File(testRoot, "first.zip").isFile)
            assertTrue(File(testRoot, "second.zip").isFile)

            session.close()
            recoverySession = ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = context.packageName,
                commonParent = testRoot,
                targets = listOf(ExplorerActionHostSessionTarget("source", source)),
                allowCreateOutput = true,
                clock = { nowMillis },
            )
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED,
                recoverySession.queryOutputBatch(batchId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
        } finally {
            runCatching { recoverySession?.close() }
            runCatching { session.close() }
            batchId?.let(store::remove)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun recoveryPreservesExternallyChangedFilesAndReportsManualRecovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v15-identity-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val source = File(testRoot, "source.txt").apply { writeText("source") }
        val batchId = UUID.randomUUID().toString()
        val changed = File(testRoot, "first.zip").apply { writeText("original") }
        val changedStat = Os.lstat(changed.path)
        val changedLastModified = changed.lastModified()
        changed.writeText("external replacement with different content")
        val staging = File(testRoot, ".autojs6-explorer-${UUID.randomUUID()}.part").apply {
            writeText("still staged")
        }
        val stagingStat = Os.lstat(staging.path)
        val parentStat = Os.stat(testRoot.path)
        val now = System.currentTimeMillis()
        val store = ExplorerActionOutputBatchStore(context)
        store.write(
            ExplorerActionOutputBatchRecord(
                id = batchId,
                pluginUid = context.applicationInfo.uid,
                processInstanceId = "dead-host-process",
                parentPath = testRoot.canonicalPath,
                parentDevice = parentStat.st_dev,
                parentInode = parentStat.st_ino,
                state = ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTING,
                createdAtMillis = now,
                updatedAtMillis = now,
                members = listOf(
                    ExplorerActionOutputBatchMemberRecord(
                        transactionId = UUID.randomUUID().toString(),
                        targetPath = changed.canonicalPath,
                        stagingPath = File(
                            testRoot,
                            ".autojs6-explorer-${UUID.randomUUID()}.part",
                        ).canonicalPath,
                        stagingDevice = changedStat.st_dev,
                        stagingInode = changedStat.st_ino,
                        stagingSize = changedStat.st_size,
                        stagingLastModifiedMillis = changedLastModified,
                    ),
                    ExplorerActionOutputBatchMemberRecord(
                        transactionId = UUID.randomUUID().toString(),
                        targetPath = File(testRoot, "second.zip").canonicalPath,
                        stagingPath = staging.canonicalPath,
                        stagingDevice = stagingStat.st_dev,
                        stagingInode = stagingStat.st_ino,
                        stagingSize = stagingStat.st_size,
                        stagingLastModifiedMillis = staging.lastModified(),
                    ),
                ),
            ),
        )

        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        try {
            assertEquals("external replacement with different content", changed.readText())
            assertFalse(staging.exists())
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_RECOVERY_REQUIRED,
                session.queryOutputBatch(batchId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertTrue(changed.delete())
            session.abortOutputBatch(batchId)
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED,
                session.queryOutputBatch(batchId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
        } finally {
            session.close()
            store.remove(batchId)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun realProcessDeathRecoveryRequiresExternalTwoPhaseHarness() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments()
            .getString(PROCESS_DEATH_PHASE_ARGUMENT)
            .orEmpty()
        assumeTrue(
            "This recovery gate runs only through the external two-phase harness",
            phase == PROCESS_DEATH_PHASE_PREPARE || phase == PROCESS_DEATH_PHASE_VERIFY,
        )
        val context = instrumentation.targetContext
        val testRoot = File(context.cacheDir, PROCESS_DEATH_ROOT_NAME)
        val batchIdFile = File(context.filesDir, PROCESS_DEATH_BATCH_ID_FILE_NAME)
        val readyFile = File(context.filesDir, PROCESS_DEATH_READY_FILE_NAME)
        val store = ExplorerActionOutputBatchStore(context)

        if (phase == PROCESS_DEATH_PHASE_PREPARE) {
            batchIdFile.takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotEmpty)?.let {
                store.remove(it)
            }
            testRoot.deleteRecursively()
            batchIdFile.delete()
            readyFile.delete()
            assertTrue(testRoot.mkdirs())
            val source = File(testRoot, "source.txt").apply { writeText("source") }
            val blockUntilKilled = CountDownLatch(1)
            val session = ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = context.packageName,
                commonParent = testRoot,
                targets = listOf(ExplorerActionHostSessionTarget("source", source)),
                allowCreateOutput = true,
                afterBatchMemberPublished = { publishedCount ->
                    if (publishedCount == 1) {
                        writeDurableText(readyFile, "ready")
                        blockUntilKilled.await()
                    }
                },
            )
            val first = prepareVerifiedOutput(session, "first.zip", byteArrayOf(1, 2, 3))
            val second = prepareVerifiedOutput(session, "second.zip", byteArrayOf(4, 5, 6))
            val batchId = session.prepareOutputBatch(mutableListOf(first, second))
                .getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID)
                .let(::requireNotNull)
            writeDurableText(batchIdFile, batchId)
            session.commitOutputBatch(batchId)
            throw AssertionError("External harness did not terminate the host process")
        }

        val batchId = batchIdFile.readText().trim()
        assertTrue(batchId.isNotEmpty())
        assertTrue(readyFile.isFile)
        val source = File(testRoot, "source.txt")
        assertTrue(source.isFile)
        val recoverySession = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        try {
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED,
                recoverySession.queryOutputBatch(batchId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertFalse(File(testRoot, "first.zip").exists())
            assertFalse(File(testRoot, "second.zip").exists())
            assertEquals(
                listOf("source.txt"),
                testRoot.listFiles().orEmpty().map(File::getName).sorted(),
            )
        } finally {
            recoverySession.close()
            store.remove(batchId)
            testRoot.deleteRecursively()
            batchIdFile.delete()
            readyFile.delete()
        }
    }

    @Test
    fun terminalOutputBatchExpiryRequiresExternalRestartHarness() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments()
            .getString(TERMINAL_EXPIRY_PHASE_ARGUMENT)
            .orEmpty()
        assumeTrue(
            "This terminal expiry gate runs only through the external two-phase harness",
            phase == TERMINAL_EXPIRY_PHASE_PREPARE || phase == TERMINAL_EXPIRY_PHASE_VERIFY,
        )
        val context = instrumentation.targetContext
        val testRoot = File(context.cacheDir, TERMINAL_EXPIRY_ROOT_NAME)
        val stateFile = File(context.filesDir, TERMINAL_EXPIRY_STATE_FILE_NAME)
        val store = ExplorerActionOutputBatchStore(context)

        if (phase == TERMINAL_EXPIRY_PHASE_PREPARE) {
            store.list()
                .filter { record -> record.parentPath == testRoot.canonicalPath }
                .forEach { record -> store.remove(record.id) }
            testRoot.deleteRecursively()
            stateFile.delete()
            assertTrue(testRoot.mkdirs())
            val source = File(testRoot, "source.txt").apply { writeText("source") }
            val session = ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = context.packageName,
                commonParent = testRoot,
                targets = listOf(ExplorerActionHostSessionTarget("source", source)),
                allowCreateOutput = true,
            )

            val expiredCommittedId: String
            val expiredAbortedId: String
            val freshCommittedId: String
            try {
                fun prepareBatch(prefix: String, first: ByteArray, second: ByteArray): String {
                    val firstId = prepareVerifiedOutput(session, "$prefix-a.zip", first)
                    val secondId = prepareVerifiedOutput(session, "$prefix-b.zip", second)
                    return session.prepareOutputBatch(mutableListOf(firstId, secondId))
                        .getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID)
                        .let(::requireNotNull)
                }

                expiredCommittedId = prepareBatch(
                    prefix = "expired-committed",
                    first = byteArrayOf(1, 2, 3),
                    second = byteArrayOf(4, 5, 6),
                ).also(session::commitOutputBatch)
                expiredAbortedId = prepareBatch(
                    prefix = "expired-aborted",
                    first = byteArrayOf(7, 8, 9),
                    second = byteArrayOf(10, 11, 12),
                ).also(session::abortOutputBatch)
                freshCommittedId = prepareBatch(
                    prefix = "fresh-committed",
                    first = byteArrayOf(13, 14, 15),
                    second = byteArrayOf(16, 17, 18),
                ).also(session::commitOutputBatch)
            } finally {
                session.close()
            }

            val expiredAtMillis = (System.currentTimeMillis() - EXPIRED_TERMINAL_AGE_MILLIS)
                .coerceAtLeast(0L)
            listOf(expiredCommittedId, expiredAbortedId).forEach { batchId ->
                val record = requireNotNull(store.read(batchId))
                store.write(
                    record.copy(
                        createdAtMillis = expiredAtMillis,
                        updatedAtMillis = expiredAtMillis,
                    ),
                )
            }
            writeDurableText(
                stateFile,
                listOf(expiredCommittedId, expiredAbortedId, freshCommittedId).joinToString("\n"),
            )

            assertTrue(File(testRoot, "expired-committed-a.zip").isFile)
            assertTrue(File(testRoot, "expired-committed-b.zip").isFile)
            assertFalse(File(testRoot, "expired-aborted-a.zip").exists())
            assertFalse(File(testRoot, "expired-aborted-b.zip").exists())
            assertTrue(File(testRoot, "fresh-committed-a.zip").isFile)
            assertTrue(File(testRoot, "fresh-committed-b.zip").isFile)
            assertTrue(stateFile.isFile)
            return
        }

        val batchIds = stateFile.readLines(Charsets.UTF_8)
            .map(String::trim)
            .filter(String::isNotEmpty)
        assertEquals(3, batchIds.size)
        val expiredCommittedId = batchIds[0]
        val expiredAbortedId = batchIds[1]
        val freshCommittedId = batchIds[2]
        val source = File(testRoot, "source.txt")
        assertTrue(source.isFile)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        try {
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_UNKNOWN,
                session.queryOutputBatch(expiredCommittedId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_UNKNOWN,
                session.queryOutputBatch(expiredAbortedId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED,
                session.queryOutputBatch(freshCommittedId)
                    .getInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE),
            )
            assertNull(store.read(expiredCommittedId))
            assertNull(store.read(expiredAbortedId))
            assertEquals(
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED,
                requireNotNull(store.read(freshCommittedId)).state,
            )
            @Suppress("DEPRECATION")
            val listed = requireNotNull(
                session.listOutputBatches().getParcelableArrayList<Bundle>(
                    ExplorerActionHostSessionKeys.OUTPUT_BATCHES,
                ),
            )
            assertEquals(
                listOf(freshCommittedId),
                listed.map { batch ->
                    batch.getString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID)
                },
            )
            assertArrayEquals(
                byteArrayOf(1, 2, 3),
                File(testRoot, "expired-committed-a.zip").readBytes(),
            )
            assertArrayEquals(
                byteArrayOf(4, 5, 6),
                File(testRoot, "expired-committed-b.zip").readBytes(),
            )
            assertFalse(File(testRoot, "expired-aborted-a.zip").exists())
            assertFalse(File(testRoot, "expired-aborted-b.zip").exists())
            assertArrayEquals(
                byteArrayOf(13, 14, 15),
                File(testRoot, "fresh-committed-a.zip").readBytes(),
            )
            assertArrayEquals(
                byteArrayOf(16, 17, 18),
                File(testRoot, "fresh-committed-b.zip").readBytes(),
            )
            assertTrue(testRoot.listFiles().orEmpty().none { file -> file.name.endsWith(".part") })
        } finally {
            session.close()
            batchIds.forEach(store::remove)
            testRoot.deleteRecursively()
            stateFile.delete()
        }
    }

    @Test
    fun committedArchiveOutputCanMoveTheCompleteSelectionToTrashAndRestoreIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v16-trash-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val sourceFileBytes = "v16 source file".encodeToByteArray()
        val nestedBytes = "v16 nested source".encodeToByteArray()
        val sourceFile = File(testRoot, "source.txt").apply { writeBytes(sourceFileBytes) }
        val sourceDirectory = File(testRoot, "folder").apply {
            assertTrue(mkdir())
            File(this, "nested.txt").writeBytes(nestedBytes)
            File(this, "empty").also { assertTrue(it.mkdir()) }
        }
        val refreshCount = AtomicInteger()
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(
                ExplorerActionHostSessionTarget("file", sourceFile),
                ExplorerActionHostSessionTarget("directory", sourceDirectory),
            ),
            allowCreateOutput = true,
            onSourcesTrashed = { refreshCount.incrementAndGet() },
        )
        val repository = TrashRepository(context.applicationContext)
        var trashIds: List<String> = emptyList()
        var targetTrashBatchId: String? = null

        try {
            val unknown = session.queryTargetTrash()
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_STATE_UNKNOWN,
                unknown.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
            )
            val outputBytes = "verified archive output".encodeToByteArray()
            val outputId = prepareVerifiedOutput(session, "sources.zip", outputBytes)
            session.commitOutput(outputId)

            val result = session.moveTargetsToTrash(
                mutableListOf("file", "directory"),
                mutableListOf(outputId),
            )
            targetTrashBatchId = requireNotNull(
                result.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID),
            )
            assertEquals(
                targetTrashBatchId,
                UUID.fromString(targetTrashBatchId).toString(),
            )
            trashIds = result.getStringArrayList(
                ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_IDS,
            ).orEmpty()
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_STATE_COMMITTED,
                result.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
            )
            assertEquals(2, result.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_MOVED_COUNT))
            assertEquals(0, result.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RECOVERY_COUNT))
            assertEquals(2, trashIds.size)
            assertFalse(sourceFile.exists())
            assertFalse(sourceDirectory.exists())
            assertArrayEquals(outputBytes, File(testRoot, "sources.zip").readBytes())
            assertEquals(1, refreshCount.get())

            val available = session.queryTargetTrashBatch(targetTrashBatchId)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_AVAILABLE,
                available.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertEquals(2, available.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_MOVED_COUNT))
            assertEquals(0, available.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RESTORED_COUNT))
            assertEquals(
                mapOf(
                    sourceFile.name to ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_MOVED,
                    sourceDirectory.name to ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_MOVED,
                ),
                targetTrashItemStates(available),
            )
            assertTrue(
                BundleCompat.getParcelableArrayList(
                    session.listTargetTrashBatches(),
                    ExplorerActionHostSessionKeys.TARGET_TRASH_BATCHES,
                    Bundle::class.java,
                )
                    .orEmpty()
                    .any { batch ->
                        batch.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID) ==
                            targetTrashBatchId
                    },
            )

            val repeated = session.moveTargetsToTrash(
                mutableListOf("file", "directory"),
                mutableListOf(outputId),
            )
            assertEquals(
                trashIds,
                repeated.getStringArrayList(ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_IDS),
            )
            assertEquals(1, refreshCount.get())
            assertEquals(
                result.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
                session.queryTargetTrash().getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
            )

            val restored = session.undoTargetTrashBatch(targetTrashBatchId)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED,
                restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertEquals(2, restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_MOVED_COUNT))
            assertEquals(2, restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RESTORED_COUNT))
            assertEquals(0, restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RECOVERY_COUNT))
            assertArrayEquals(sourceFileBytes, sourceFile.readBytes())
            assertArrayEquals(nestedBytes, File(sourceDirectory, "nested.txt").readBytes())
            assertTrue(File(sourceDirectory, "empty").isDirectory)
            assertArrayEquals(outputBytes, File(testRoot, "sources.zip").readBytes())
            assertEquals(2, refreshCount.get())
            assertTrue(
                HistoryDatabase.getInstance(context.applicationContext)
                    .historyDao()
                    .listTrashItemsDesc()
                    .none { item -> item.trashId in trashIds },
            )
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED,
                session.queryTargetTrashBatch(targetTrashBatchId)
                    .getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            expectIllegalState { session.undoTargetTrashBatch(targetTrashBatchId) }
            trashIds = emptyList()
        } finally {
            val remaining = HistoryDatabase.getInstance(context.applicationContext)
                .historyDao()
                .listTrashItemsDesc()
                .filter { item -> item.trashId in trashIds }
            remaining.forEach(repository::deleteTrashItem)
            runCatching { session.close() }
            targetTrashBatchId?.let { batchId ->
                ExplorerActionTargetTrashBatchStore(context).remove(batchId)
            }
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun targetTrashBatchSurvivesSessionCloseAndRestoresFromANewParentSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v21-trash-reopen-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val sourceBytes = "v21 durable source".encodeToByteArray()
        val outputBytes = "v21 durable archive".encodeToByteArray()
        val source = File(testRoot, "source.txt").apply { writeBytes(sourceBytes) }
        val output = File(testRoot, "sources.zip")
        val firstSession = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("source", source)),
            allowCreateOutput = true,
        )
        val repository = TrashRepository(context.applicationContext)
        var reopenedSession: ExplorerActionHostSession? = null
        var targetTrashBatchId: String? = null
        var trashIds: List<String> = emptyList()

        try {
            val outputId = prepareVerifiedOutput(firstSession, output.name, outputBytes)
            firstSession.commitOutput(outputId)
            val moved = firstSession.moveTargetsToTrash(
                mutableListOf("source"),
                mutableListOf(outputId),
            )
            targetTrashBatchId = requireNotNull(
                moved.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID),
            )
            trashIds = moved.getStringArrayList(
                ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_IDS,
            ).orEmpty()
            assertFalse(source.exists())
            assertArrayEquals(outputBytes, output.readBytes())
            firstSession.close()

            val refreshCount = AtomicInteger()
            val reopened = ExplorerActionHostSession.create(
                context = context,
                pluginPackageName = context.packageName,
                commonParent = testRoot,
                targets = listOf(ExplorerActionHostSessionTarget("created-output", output)),
                allowCreateOutput = true,
                onSourcesTrashed = { refreshCount.incrementAndGet() },
            )
            reopenedSession = reopened
            val listedIds = BundleCompat.getParcelableArrayList(
                reopened.listTargetTrashBatches(),
                ExplorerActionHostSessionKeys.TARGET_TRASH_BATCHES,
                Bundle::class.java,
            )
                .orEmpty()
                .mapNotNull { batch ->
                    batch.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID)
                }
            assertTrue(targetTrashBatchId in listedIds)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_AVAILABLE,
                reopened.queryTargetTrashBatch(targetTrashBatchId)
                    .getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )

            val restored = reopened.undoTargetTrashBatch(targetTrashBatchId)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED,
                restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertEquals(1, restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RESTORED_COUNT))
            assertArrayEquals(sourceBytes, source.readBytes())
            assertArrayEquals(outputBytes, output.readBytes())
            assertEquals(1, refreshCount.get())
            trashIds = emptyList()
        } finally {
            runCatching { reopenedSession?.close() }
            runCatching { firstSession.close() }
            HistoryDatabase.getInstance(context.applicationContext)
                .historyDao()
                .listTrashItemsDesc()
                .filter { item -> item.trashId in trashIds }
                .forEach(repository::deleteTrashItem)
            targetTrashBatchId?.let { batchId ->
                ExplorerActionTargetTrashBatchStore(context).remove(batchId)
            }
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun targetTrashBatchUndoNeverOverwritesRecreatedSourceNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v21-trash-conflict-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val first = File(testRoot, "first.txt").apply { writeText("original first") }
        val second = File(testRoot, "second.txt").apply { writeText("original second") }
        val outputBytes = "v21 conflict archive".encodeToByteArray()
        val output = File(testRoot, "sources.zip")
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(
                ExplorerActionHostSessionTarget("first", first),
                ExplorerActionHostSessionTarget("second", second),
            ),
            allowCreateOutput = true,
        )
        val repository = TrashRepository(context.applicationContext)
        var targetTrashBatchId: String? = null
        var trashIds: List<String> = emptyList()

        try {
            val outputId = prepareVerifiedOutput(session, output.name, outputBytes)
            session.commitOutput(outputId)
            val moved = session.moveTargetsToTrash(
                mutableListOf("first", "second"),
                mutableListOf(outputId),
            )
            targetTrashBatchId = requireNotNull(
                moved.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID),
            )
            trashIds = moved.getStringArrayList(
                ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_IDS,
            ).orEmpty()
            first.writeText("new occupant")

            val partiallyAvailable = session.queryTargetTrashBatch(targetTrashBatchId)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_PARTIAL,
                partiallyAvailable.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertEquals(1, partiallyAvailable.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RECOVERY_COUNT))

            val restored = session.undoTargetTrashBatch(targetTrashBatchId)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_STALE,
                restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE),
            )
            assertEquals(2, restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_MOVED_COUNT))
            assertEquals(1, restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RESTORED_COUNT))
            assertEquals(1, restored.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RECOVERY_COUNT))
            assertEquals("new occupant", first.readText())
            assertEquals("original second", second.readText())
            assertArrayEquals(outputBytes, output.readBytes())
            assertEquals(
                mapOf(
                    first.name to ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_STALE,
                    second.name to ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_RESTORED,
                ),
                targetTrashItemStates(restored),
            )
            assertEquals(
                1,
                HistoryDatabase.getInstance(context.applicationContext)
                    .historyDao()
                    .listTrashItemsDesc()
                    .count { item -> item.trashId in trashIds },
            )
            expectIllegalState { session.undoTargetTrashBatch(targetTrashBatchId) }
        } finally {
            HistoryDatabase.getInstance(context.applicationContext)
                .historyDao()
                .listTrashItemsDesc()
                .filter { item -> item.trashId in trashIds }
                .forEach(repository::deleteTrashItem)
            runCatching { session.close() }
            targetTrashBatchId?.let { batchId ->
                ExplorerActionTargetTrashBatchStore(context).remove(batchId)
            }
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun activeTargetTrashBatchIsHiddenFromConcurrentRecoveryUntilMoveCompletes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(
            context.cacheDir,
            "explorer-action-v21-trash-concurrent-${System.nanoTime()}",
        )
        assertTrue(testRoot.mkdirs())
        val sourceBytes = "v21 concurrent recovery source".encodeToByteArray()
        val source = File(testRoot, "source.txt").apply { writeBytes(sourceBytes) }
        val repository = TrashRepository(context.applicationContext)
        val movingStore = ExplorerActionTargetTrashBatchStore(context.applicationContext)
        val recoveringStore = ExplorerActionTargetTrashBatchStore(context.applicationContext)
        val record = movingStore.prepare(
            pluginUid = context.applicationInfo.uid,
            parent = testRoot,
            sources = listOf(
                ExplorerActionTargetTrashBatchSource(
                    targetId = "source",
                    file = source,
                    identity = ExplorerActionFileIdentity.capture(source.canonicalFile),
                ),
            ),
            repository = repository,
        )
        val trashId = record.items.single().trashId
        val reachedDeleteBoundary = CountDownLatch(1)
        val continueMove = CountDownLatch(1)
        val moveCompleted = CountDownLatch(1)
        val moveFailure = AtomicReference<Throwable?>()

        movingStore.updateItem(record.id, 0, ExplorerActionTargetTrashItemState.MOVING)
        Thread {
            try {
                repository.moveToTrashWithProgress(
                    path = source.canonicalPath,
                    progressCallback = null,
                    fileCallback = null,
                    operationController = DialogUtils.OperationController(),
                    beforeSourceDelete = {
                        reachedDeleteBoundary.countDown()
                        check(continueMove.await(CONCURRENT_MOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            "Concurrent target Trash move was not released"
                        }
                    },
                    trashId = trashId,
                )
                // Deliberately omit the MOVED journal update. Finishing the in-process marker below
                // simulates a process restart immediately after the durable Trash move completed.
            } catch (error: Throwable) {
                moveFailure.set(error)
            } finally {
                moveCompleted.countDown()
            }
        }.apply {
            name = "ExplorerActionTargetTrashConcurrentTest"
            start()
        }

        try {
            assertTrue(
                reachedDeleteBoundary.await(CONCURRENT_MOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            recoveringStore.recover(repository)
            assertNull(
                recoveringStore.query(
                    pluginUid = context.applicationInfo.uid,
                    parent = testRoot,
                    batchId = record.id,
                    repository = repository,
                ),
            )
            assertTrue(
                recoveringStore.list(
                    pluginUid = context.applicationInfo.uid,
                    parent = testRoot,
                    repository = repository,
                ).none { batch -> batch.id == record.id },
            )

            continueMove.countDown()
            assertTrue(moveCompleted.await(CONCURRENT_MOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            moveFailure.get()?.let { error -> throw AssertionError("Target Trash move failed", error) }
            assertFalse(source.exists())
            assertNotNull(repository.findTrashItem(trashId))

            movingStore.finish(record.id)
            val recovered = requireNotNull(
                recoveringStore.query(
                    pluginUid = context.applicationInfo.uid,
                    parent = testRoot,
                    batchId = record.id,
                    repository = repository,
                ),
            )
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_AVAILABLE,
                recovered.protocolState,
            )
            assertEquals(ExplorerActionTargetTrashItemState.MOVED, recovered.items.single().state)

            val restored = recoveringStore.undo(
                pluginUid = context.applicationInfo.uid,
                parent = testRoot,
                batchId = record.id,
                repository = repository,
            )
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED,
                restored.protocolState,
            )
            assertArrayEquals(sourceBytes, source.readBytes())
        } finally {
            continueMove.countDown()
            moveCompleted.await(CONCURRENT_MOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            movingStore.finish(record.id)
            repository.findTrashItem(trashId)?.let(repository::deleteTrashItem)
            recoveringStore.remove(record.id)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun targetTrashRejectsMissingProofAndChangedOutputWithoutTouchingSources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v16-trash-proof-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val first = File(testRoot, "first.txt").apply { writeText("first source") }
        val second = File(testRoot, "second.txt").apply { writeText("second source") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(
                ExplorerActionHostSessionTarget("first", first),
                ExplorerActionHostSessionTarget("second", second),
            ),
            allowCreateOutput = true,
        )

        try {
            val outputId = prepareVerifiedOutput(
                session,
                "sources.zip",
                "original output".encodeToByteArray(),
            )
            session.commitOutput(outputId)

            expectIllegalArgument {
                session.moveTargetsToTrash(mutableListOf("first"), mutableListOf(outputId))
            }
            File(testRoot, "sources.zip").appendText("externally changed")
            expectIllegalState {
                session.moveTargetsToTrash(
                    mutableListOf("first", "second"),
                    mutableListOf(outputId),
                )
            }

            assertTrue(first.isFile)
            assertTrue(second.isFile)
            assertEquals(
                ExplorerActionHostSessionValues.TARGET_TRASH_STATE_UNKNOWN,
                session.queryTargetTrash().getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE),
            )
            assertTrue(
                HistoryDatabase.getInstance(context.applicationContext)
                    .historyDao()
                    .listTrashItemsDesc()
                    .none { item -> item.originalPath == first.path || item.originalPath == second.path },
            )
        } finally {
            runCatching { session.close() }
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun trashSourceRevalidationFailureKeepsTheSourceAndRemovesUnusedRecoveryData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v16-trash-revalidate-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val sourceBytes = "source must remain authoritative".encodeToByteArray()
        val source = File(testRoot, "source.txt").apply { writeBytes(sourceBytes) }
        val canonicalSourcePath = source.canonicalPath
        val database = HistoryDatabase.getInstance(context.applicationContext)
        val blobDirectory = File(context.filesDir, TrashBlobStore.TRASH_BLOB_DIR_REL)
        val initialBlobNames = blobDirectory.listFiles().orEmpty().map(File::getName).toSet()

        try {
            expectIllegalState {
                TrashRepository(context.applicationContext).moveToTrashWithProgress(
                    path = canonicalSourcePath,
                    progressCallback = null,
                    fileCallback = null,
                    operationController = DialogUtils.OperationController(),
                    beforeSourceDelete = { error("simulated source identity change") },
                )
            }

            assertArrayEquals(sourceBytes, source.readBytes())
            assertTrue(
                database.historyDao().listTrashItemsDesc().none { item ->
                    item.originalPath == canonicalSourcePath
                },
            )
            assertEquals(
                initialBlobNames,
                blobDirectory.listFiles().orEmpty().map(File::getName).toSet(),
            )
        } finally {
            testRoot.deleteRecursively()
        }
    }

    private fun prepareVerifiedOutput(
        session: ExplorerActionHostSession,
        displayName: String,
        bytes: ByteArray,
    ): String {
        val prepared = session.prepareOutput(
            displayName,
            "application/octet-stream",
            ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL,
        )
        val transactionId = requireNotNull(
            prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
        )
        ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
            it.write(bytes)
        }
        val verified = ParcelFileDescriptor.AutoCloseInputStream(
            session.openPendingOutput(transactionId),
        ).use { it.readBytes() }
        assertArrayEquals(bytes, verified)
        return transactionId
    }

    private fun prepareVerifiedReplacement(
        session: ExplorerActionHostSession,
        bytes: ByteArray,
    ): String {
        val transactionId = requireNotNull(
            session.prepareTargetReplacement("archive")
                .getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID),
        )
        ParcelFileDescriptor.AutoCloseOutputStream(session.openOutput(transactionId)).use {
            it.write(bytes)
        }
        val verified = ParcelFileDescriptor.AutoCloseInputStream(
            session.openPendingOutput(transactionId),
        ).use { it.readBytes() }
        assertArrayEquals(bytes, verified)
        return transactionId
    }

    private fun writeDurableText(file: File, value: String) {
        FileOutputStream(file, false).use { output ->
            output.write(value.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun deleteLowStorageFill(file: File) {
        if (!file.exists() || file.delete()) return
        runCatching {
            FileOutputStream(file, false).use { output -> output.fd.sync() }
        }
        check(!file.exists() || file.delete()) {
            "Low-storage harness fill file could not be removed"
        }
    }

    @Test
    fun directSiblingAccessListsAndOpensOnlyOneParentLevel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v12-siblings-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val selected = File(testRoot, "episode02.mkv").apply { writeText("selected") }
        val sibling = File(testRoot, "episode01.mkv").apply { writeText("sibling") }
        File(testRoot, "episode02.srt").writeText("subtitle")
        File(testRoot, "nested").apply {
            assertTrue(mkdir())
            File(this, "secret.mkv").writeText("secret")
        }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("selected", selected)),
            allowCreateOutput = false,
            allowReadSiblings = true,
        )

        try {
            val page = session.listChildren("selected", "", 0, 100)
            @Suppress("DEPRECATION")
            val items = requireNotNull(
                page.getParcelableArrayList<android.os.Bundle>(ExplorerActionHostSessionKeys.ITEMS),
            )
            assertEquals(
                listOf("episode01.mkv", "episode02.mkv", "episode02.srt", "nested"),
                items.map { it.getString(ExplorerActionHostSessionKeys.RELATIVE_PATH) },
            )
            assertTrue(page.getBoolean(ExplorerActionHostSessionKeys.COMPLETE))
            val actual = ParcelFileDescriptor.AutoCloseInputStream(
                session.openFile("selected", sibling.name),
            ).use { it.readBytes().decodeToString() }
            assertEquals("sibling", actual)
            expectIllegalArgument { session.openFile("selected", "nested/secret.mkv") }
            expectIllegalArgument { session.listChildren("selected", "nested", 0, 100) }
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun mediaSessionExtensionsAreDeniedWithoutExplicitCatalogCapabilities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v12-denied-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val selected = File(testRoot, "episode02.mkv").apply { writeText("selected") }
        val sibling = File(testRoot, "episode01.mkv").apply { writeText("sibling") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("selected", selected)),
            allowCreateOutput = false,
        )

        try {
            assertEquals(
                "selected",
                ParcelFileDescriptor.AutoCloseInputStream(session.openFile("selected", ""))
                    .use { it.readBytes().decodeToString() },
            )
            expectIllegalState { session.listChildren("selected", "", 0, 100) }
            expectIllegalState { session.openFile("selected", sibling.name) }
            expectIllegalState { session.getPlaybackProgress("selected", "") }
            expectIllegalState {
                session.reportPlaybackProgress(
                    "selected",
                    "",
                    30_000L,
                    120_000L,
                    ExplorerActionHostSessionValues.PLAYBACK_REPORT_PROGRESS,
                )
            }
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun playbackHistoryRequiresOptInAndCompletionClearsProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousEnabled = ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.get()
        val testRoot = File(context.cacheDir, "explorer-action-v12-history-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val selected = File(testRoot, "episode01.mkv").apply { writeText("video") }
        ExplorerPlaybackHistoryStore.clearAll(context)
        Pref.putBooleanSync(R.string.key_explorer_playback_history, false)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("selected", selected)),
            allowCreateOutput = false,
            allowPlaybackProgress = true,
            clock = { 42L },
        )

        try {
            val disabled = session.getPlaybackProgress("selected", "")
            assertFalse(disabled.getBoolean(ExplorerActionHostSessionKeys.PLAYBACK_HISTORY_ENABLED))
            session.reportPlaybackProgress(
                "selected",
                "",
                30_000L,
                120_000L,
                ExplorerActionHostSessionValues.PLAYBACK_REPORT_PROGRESS,
            )
            assertEquals(0, ExplorerPlaybackHistoryStore.entryCount(context))

            Pref.putBooleanSync(R.string.key_explorer_playback_history, true)
            session.reportPlaybackProgress(
                "selected",
                "",
                30_000L,
                120_000L,
                ExplorerActionHostSessionValues.PLAYBACK_REPORT_PROGRESS,
            )
            val stored = session.getPlaybackProgress("selected", "")
            assertTrue(stored.getBoolean(ExplorerActionHostSessionKeys.PLAYBACK_HISTORY_ENABLED))
            assertTrue(stored.getBoolean(ExplorerActionHostSessionKeys.PLAYBACK_PROGRESS_PRESENT))
            assertEquals(30_000L, stored.getLong(ExplorerActionHostSessionKeys.PLAYBACK_POSITION_MILLIS))
            assertEquals(120_000L, stored.getLong(ExplorerActionHostSessionKeys.PLAYBACK_DURATION_MILLIS))
            assertEquals(42L, stored.getLong(ExplorerActionHostSessionKeys.PLAYBACK_UPDATED_AT_MILLIS))

            session.reportPlaybackProgress(
                "selected",
                "",
                120_000L,
                120_000L,
                ExplorerActionHostSessionValues.PLAYBACK_REPORT_COMPLETED,
            )
            assertFalse(
                session.getPlaybackProgress("selected", "")
                    .getBoolean(ExplorerActionHostSessionKeys.PLAYBACK_PROGRESS_PRESENT),
            )
        } finally {
            session.close()
            ExplorerPlaybackHistoryStore.clearAll(context)
            Pref.putBooleanSync(R.string.key_explorer_playback_history, previousEnabled)
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun relatedFileAccessOpensOnlyTheFrozenHostDerivedCandidate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v22-related-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val selected = File(testRoot, "release.apk").apply { writeText("apk") }
        val idsig = File(testRoot, "release.apk.idsig").apply { writeText("idsig") }
        val unrelated = File(testRoot, "release.apk.sig").apply { writeText("secret") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("selected", selected)),
            allowCreateOutput = false,
            relatedFileSuffixes = setOf(".idsig"),
        )

        try {
            val actual = ParcelFileDescriptor.AutoCloseInputStream(
                requireNotNull(session.openRelatedFile("selected", ".idsig")),
            ).use { it.readBytes().decodeToString() }
            assertEquals("idsig", actual)
            expectIllegalArgument { session.openRelatedFile("selected", ".sig") }
            expectIllegalState { session.listChildren("selected", "", 0, 100) }
            expectIllegalState { session.openFile("selected", unrelated.name) }

            assertTrue(idsig.delete())
            idsig.writeText("replacement")
            expectIllegalState { session.openRelatedFile("selected", ".idsig") }
        } finally {
            session.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun relatedFileAccessDoesNotAdoptLateOrSymbolicLinkCandidates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "explorer-action-v22-absent-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val selected = File(testRoot, "release.apk").apply { writeText("apk") }
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("selected", selected)),
            allowCreateOutput = false,
            relatedFileSuffixes = setOf(".idsig"),
        )

        try {
            assertNull(session.openRelatedFile("selected", ".idsig"))
            File(testRoot, "release.apk.idsig").writeText("late")
            assertNull(session.openRelatedFile("selected", ".idsig"))
        } finally {
            session.close()
        }

        val real = File(testRoot, "real.idsig").apply { writeText("real") }
        val link = File(testRoot, "release.apk.idsig")
        assertTrue(link.delete())
        Os.symlink(real.path, link.path)
        val linkSession = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("selected", selected)),
            allowCreateOutput = false,
            relatedFileSuffixes = setOf(".idsig"),
        )
        try {
            assertNull(linkSession.openRelatedFile("selected", ".idsig"))
        } finally {
            linkSession.close()
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun playbackHistoryKeepsOnlyTheMostRecentlyOpenedVideo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousEnabled = ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.get()
        val testRoot = File(context.cacheDir, "explorer-action-v12-single-history-${System.nanoTime()}")
        assertTrue(testRoot.mkdirs())
        val selected = File(testRoot, "episode01.mkv").apply { writeText("first") }
        val sibling = File(testRoot, "episode02.mkv").apply { writeText("second") }
        ExplorerPlaybackHistoryStore.clearAll(context)
        Pref.putBooleanSync(R.string.key_explorer_playback_history, true)
        val session = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = context.packageName,
            commonParent = testRoot,
            targets = listOf(ExplorerActionHostSessionTarget("selected", selected)),
            allowCreateOutput = false,
            allowReadSiblings = true,
            allowPlaybackProgress = true,
            clock = { 42L },
        )

        try {
            session.reportPlaybackProgress(
                "selected",
                "",
                30_000L,
                120_000L,
                ExplorerActionHostSessionValues.PLAYBACK_REPORT_PROGRESS,
            )
            assertEquals(1, ExplorerPlaybackHistoryStore.entryCount(context))

            val differentVideo = session.getPlaybackProgress("selected", sibling.name)
            assertFalse(
                differentVideo.getBoolean(ExplorerActionHostSessionKeys.PLAYBACK_PROGRESS_PRESENT),
            )
            assertEquals(0, ExplorerPlaybackHistoryStore.entryCount(context))

            session.reportPlaybackProgress(
                "selected",
                sibling.name,
                40_000L,
                120_000L,
                ExplorerActionHostSessionValues.PLAYBACK_REPORT_PROGRESS,
            )
            assertEquals(1, ExplorerPlaybackHistoryStore.entryCount(context))
            val sameVideo = session.getPlaybackProgress("selected", sibling.name)
            assertTrue(sameVideo.getBoolean(ExplorerActionHostSessionKeys.PLAYBACK_PROGRESS_PRESENT))
            assertEquals(
                40_000L,
                sameVideo.getLong(ExplorerActionHostSessionKeys.PLAYBACK_POSITION_MILLIS),
            )

            session.reportPlaybackProgress(
                "selected",
                sibling.name,
                120_000L,
                120_000L,
                ExplorerActionHostSessionValues.PLAYBACK_REPORT_COMPLETED,
            )
            assertEquals(0, ExplorerPlaybackHistoryStore.entryCount(context))
        } finally {
            session.close()
            ExplorerPlaybackHistoryStore.clearAll(context)
            Pref.putBooleanSync(R.string.key_explorer_playback_history, previousEnabled)
            testRoot.deleteRecursively()
        }
    }

    private fun targetTrashItemStates(batch: Bundle): Map<String, Int> =
        BundleCompat.getParcelableArrayList(
            batch,
            ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ITEMS,
            Bundle::class.java,
        ).orEmpty().associate { item ->
            requireNotNull(
                item.getString(ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_DISPLAY_NAME),
            ) to item.getInt(ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_STATE)
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

    private fun expectIOException(block: () -> Unit) {
        try {
            block()
        } catch (_: IOException) {
            return
        }
        throw AssertionError("Expected IOException")
    }

    private class SyntheticDeathToken : Binder() {
        private var recipient: IBinder.DeathRecipient? = null

        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
            this.recipient = recipient
        }

        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean {
            if (this.recipient !== recipient) return false
            this.recipient = null
            return true
        }

        fun die() {
            requireNotNull(recipient).binderDied()
        }
    }

    private companion object {
        const val CONCURRENT_MOVE_TIMEOUT_SECONDS = 10L
        const val CLIENT_DEATH_TIMEOUT_MILLIS = 5_000L
        const val DEEP_OUTPUT_TREE_DEPTH = 1_024
        const val PROCESS_DEATH_PHASE_ARGUMENT = "archiveManagerV15ProcessDeathPhase"
        const val PROCESS_DEATH_PHASE_PREPARE = "prepare"
        const val PROCESS_DEATH_PHASE_VERIFY = "verify"
        const val PROCESS_DEATH_ROOT_NAME = "explorer-action-v15-real-process-death"
        const val PROCESS_DEATH_BATCH_ID_FILE_NAME = "explorer-action-v15-process-death-batch-id"
        const val PROCESS_DEATH_READY_FILE_NAME = "explorer-action-v15-process-death-ready"
        const val TERMINAL_EXPIRY_PHASE_ARGUMENT = "archiveManagerV15TerminalExpiryPhase"
        const val TERMINAL_EXPIRY_PHASE_PREPARE = "prepare"
        const val TERMINAL_EXPIRY_PHASE_VERIFY = "verify"
        const val TERMINAL_EXPIRY_ROOT_NAME = "explorer-action-v15-terminal-expiry"
        const val TERMINAL_EXPIRY_STATE_FILE_NAME = "explorer-action-v15-terminal-expiry-state"
        const val EXPIRED_TERMINAL_AGE_MILLIS = 25L * 60L * 60L * 1000L
        const val REPLACEMENT_PROCESS_DEATH_PHASE_ARGUMENT =
            "archiveManagerV18ReplacementProcessDeathPhase"
        const val REPLACEMENT_PROCESS_DEATH_COMMIT_PREPARE = "commit-prepare"
        const val REPLACEMENT_PROCESS_DEATH_COMMIT_VERIFY = "commit-verify"
        const val REPLACEMENT_PROCESS_DEATH_RESTORE_PREPARE = "restore-prepare"
        const val REPLACEMENT_PROCESS_DEATH_RESTORE_VERIFY = "restore-verify"
        val REPLACEMENT_PROCESS_DEATH_PHASES = setOf(
            REPLACEMENT_PROCESS_DEATH_COMMIT_PREPARE,
            REPLACEMENT_PROCESS_DEATH_COMMIT_VERIFY,
            REPLACEMENT_PROCESS_DEATH_RESTORE_PREPARE,
            REPLACEMENT_PROCESS_DEATH_RESTORE_VERIFY,
        )
        const val REPLACEMENT_PROCESS_DEATH_ROOT_NAME =
            "explorer-action-v18-real-process-death"
        const val REPLACEMENT_PROCESS_DEATH_HISTORY_ID_FILE_NAME =
            "explorer-action-v18-process-death-history-id"
        const val REPLACEMENT_PROCESS_DEATH_READY_FILE_NAME =
            "explorer-action-v18-process-death-ready"
        const val REPLACEMENT_PROCESS_DEATH_ORIGINAL = "original archive before process death"
        const val REPLACEMENT_PROCESS_DEATH_REPLACEMENT =
            "replacement archive before process death"
        const val REPLACEMENT_LOW_STORAGE_ARGUMENT = "archiveManagerV18LowStorage"
        const val REPLACEMENT_LOW_STORAGE_RUN = "run"
        const val REPLACEMENT_LOW_STORAGE_ROOT_NAME = "explorer-action-v18-real-low-storage"
        const val REPLACEMENT_LOW_STORAGE_READY_FILE_NAME =
            "explorer-action-v18-low-storage-ready"
        const val REPLACEMENT_LOW_STORAGE_FILL_FILE_NAME =
            "explorer-action-v18-low-storage-fill"
        const val REPLACEMENT_LOW_STORAGE_SOURCE_BYTES = 1024 * 1024
        const val REPLACEMENT_LOW_STORAGE_RESERVE_BYTES = 128L * 1024L * 1024L
        const val REPLACEMENT_LOW_STORAGE_TIMEOUT_MILLIS = 60_000L
        const val REPLACEMENT_LOW_STORAGE_POLL_MILLIS = 100L
    }
}
