package org.autojs.autojs.core.plugin.explorer.session

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExplorerActionHostSessionInstrumentationTest {

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
}
