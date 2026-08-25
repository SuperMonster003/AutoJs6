package org.autojs.autojs.core.plugin.explorer.session

import android.os.ParcelFileDescriptor
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExplorerActionHostSessionInstrumentationTest {

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
            assertFalse(testRoot.listFiles().orEmpty().any { it.name.endsWith(".part") })
        } finally {
            session.close()
            testRoot.deleteRecursively()
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
        const val CLIENT_DEATH_TIMEOUT_MILLIS = 5_000L
        const val DEEP_OUTPUT_TREE_DEPTH = 1_024
    }
}
