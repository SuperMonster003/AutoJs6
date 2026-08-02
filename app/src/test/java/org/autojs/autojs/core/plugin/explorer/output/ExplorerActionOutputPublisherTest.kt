package org.autojs.autojs.core.plugin.explorer.output

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.ArrayDeque

class ExplorerActionOutputPublisherTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun hardLinkPublishesHostChosenSiblingWithoutReplacingSource() {
        val directory = temporaryFolder.newFolder("images")
        val sourceBytes = byteArrayOf(9, 8, 7)
        val outputBytes = byteArrayOf(1, 2, 3, 4)
        val source = File(directory, "photo.png").apply { writeBytes(sourceBytes) }
        val snapshot = temporaryFolder.newFile("snapshot").apply { writeBytes(outputBytes) }
        val publisher = publisher(hardLinkCommitter())
        val plan = publisher.plan(source, "edited", ExplorerActionImageOutputValidator.MIME_PNG)

        val result = publisher.publish(snapshot, plan, sha256(outputBytes), 1024L)

        assertTrue(result.destination.isFile)
        assertEquals("photo_edited-${"a".repeat(32)}.png", result.destination.name)
        assertTrue(plan.temporary.name.contains("b".repeat(32)))
        assertFalse(plan.temporary.exists())
        assertEquals(ExplorerActionOutputCommitMode.HARD_LINK, result.commitMode)
        assertArrayEquals(outputBytes, result.destination.readBytes())
        assertArrayEquals(sourceBytes, source.readBytes())
    }

    @Test
    fun explicitlyUsesAtomicRenameWhenHardLinksAreUnavailable() {
        val directory = temporaryFolder.newFolder("rename-images")
        val source = File(directory, "photo.png").apply { writeBytes(byteArrayOf(9)) }
        val outputBytes = byteArrayOf(1, 2, 3, 4)
        val snapshot = temporaryFolder.newFile("rename-snapshot").apply { writeBytes(outputBytes) }
        val publisher = publisher(renameFallbackCommitter())
        val plan = publisher.plan(source, "edited", ExplorerActionImageOutputValidator.MIME_PNG)

        val result = publisher.publish(snapshot, plan, sha256(outputBytes), 1024L)

        assertEquals(ExplorerActionOutputCommitMode.ATOMIC_RENAME_FALLBACK, result.commitMode)
        assertArrayEquals(outputBytes, result.destination.readBytes())
        assertFalse(plan.temporary.exists())
    }

    @Test
    fun unsupportedDirectorySyncIsAnExplicitWeakDurabilitySuccess() {
        val directory = temporaryFolder.newFolder("weak-sync-images")
        val source = File(directory, "photo.png").apply { writeBytes(byteArrayOf(9)) }
        val outputBytes = byteArrayOf(1, 2, 3)
        val snapshot = temporaryFolder.newFile("weak-sync-snapshot").apply { writeBytes(outputBytes) }
        val base = renameFallbackCommitter()
        val weakSyncCommitter = object : ExplorerActionOutputNoReplaceCommitter by base {
            override fun syncDirectory(directory: File) = ExplorerActionOutputDirectorySync.UNSUPPORTED
        }
        val publisher = publisher(weakSyncCommitter)
        val plan = publisher.plan(source, "edited", ExplorerActionImageOutputValidator.MIME_PNG)

        val result = publisher.publish(snapshot, plan, sha256(outputBytes), 1024L)

        assertEquals(ExplorerActionOutputDirectorySync.UNSUPPORTED, result.directorySync)
        assertArrayEquals(outputBytes, result.destination.readBytes())
    }

    @Test
    fun refusesExistingDestinationAndDeletesOwnedTemporary() {
        val directory = temporaryFolder.newFolder("occupied-images")
        val source = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(5)) }
        val snapshot = temporaryFolder.newFile("occupied-snapshot").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val publisher = publisher(hardLinkCommitter())
        val plan = publisher.plan(source, "converted", ExplorerActionImageOutputValidator.MIME_JPEG)
        plan.destination.writeText("occupied")

        assertFails<ExplorerActionOutputPublisher.DestinationAlreadyExistsException> {
            publisher.publish(snapshot, plan, sha256(snapshot.readBytes()), 1024L)
        }

        assertEquals("occupied", plan.destination.readText())
        assertFalse(plan.temporary.exists())
    }

    @Test
    fun copyVerificationOrCommitFailureNeverCreatesFinalOutput() {
        val directory = temporaryFolder.newFolder("failed-images")
        val source = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(5)) }
        val snapshot = temporaryFolder.newFile("failed-snapshot").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val publisher = publisher(hardLinkCommitter())
        val plan = publisher.plan(source, "converted", ExplorerActionImageOutputValidator.MIME_JPEG)

        assertFails<IOException> {
            publisher.publish(snapshot, plan, "0".repeat(64), 1024L)
        }
        assertFalse(plan.destination.exists())
        assertFalse(plan.temporary.exists())

        val failingPublisher = publisher(object : ExplorerActionOutputNoReplaceCommitter {
            override fun commit(temporary: File, destination: File): ExplorerActionOutputCommitMode {
                throw IOException("injected commit failure")
            }

            override fun unlink(file: File) {
                Files.delete(file.toPath())
            }

            override fun syncDirectory(directory: File) = ExplorerActionOutputDirectorySync.SYNCED
        })
        val failingPlan = failingPublisher.plan(
            source,
            "converted",
            ExplorerActionImageOutputValidator.MIME_JPEG,
        )
        assertFails<IOException> {
            failingPublisher.publish(snapshot, failingPlan, sha256(snapshot.readBytes()), 1024L)
        }
        assertFalse(failingPlan.destination.exists())
        assertFalse(failingPlan.temporary.exists())
    }

    @Test
    fun truncatesLongAsciiAndCjkBaseNamesWithinUtf8NameLimit() {
        val directory = temporaryFolder.newFolder("long-names")
        val sources = listOf(
            File(directory, "${"a".repeat(240)}.png"),
            File(directory, "${"图".repeat(80)}.png"),
        )
        sources.forEach { source ->
            source.writeBytes(byteArrayOf(1))
            val plan = publisher(hardLinkCommitter()).plan(
                source,
                "edited",
                ExplorerActionImageOutputValidator.MIME_PNG,
            )

            assertTrue(plan.destination.name.toByteArray(Charsets.UTF_8).size <= 255)
            assertTrue(plan.destination.name.endsWith("_edited-${"a".repeat(32)}.png"))
            assertTrue(plan.destination.parentFile == directory.canonicalFile)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun publisher(committer: ExplorerActionOutputNoReplaceCommitter): ExplorerActionOutputPublisher {
        val tokens = ArrayDeque(listOf("a".repeat(32), "b".repeat(32)))
        return ExplorerActionOutputPublisher(
            tokenFactory = { tokens.removeFirst() },
            fileOpener = ExplorerActionOutputFileOpener { destination ->
                if (!destination.createNewFile()) {
                    throw ExplorerActionOutputPublisher.DestinationAlreadyExistsException(destination)
                }
                FileOutputStream(destination, false)
            },
            committer = committer,
            identityReader = constantIdentityReader,
        )
    }

    private fun hardLinkCommitter() = object : ExplorerActionOutputNoReplaceCommitter {
        override fun commit(temporary: File, destination: File): ExplorerActionOutputCommitMode {
            try {
                Files.createLink(destination.toPath(), temporary.toPath())
            } catch (error: FileAlreadyExistsException) {
                throw ExplorerActionOutputPublisher.DestinationAlreadyExistsException(destination)
            }
            return ExplorerActionOutputCommitMode.HARD_LINK
        }

        override fun unlink(file: File) {
            Files.delete(file.toPath())
        }

        override fun syncDirectory(directory: File) = ExplorerActionOutputDirectorySync.SYNCED
    }

    private fun renameFallbackCommitter() = object : ExplorerActionOutputNoReplaceCommitter {
        override fun commit(temporary: File, destination: File): ExplorerActionOutputCommitMode {
            if (destination.exists()) {
                throw ExplorerActionOutputPublisher.DestinationAlreadyExistsException(destination)
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return ExplorerActionOutputCommitMode.ATOMIC_RENAME_FALLBACK
        }

        override fun unlink(file: File) {
            Files.delete(file.toPath())
        }

        override fun syncDirectory(directory: File) = ExplorerActionOutputDirectorySync.SYNCED
    }

    private val constantIdentityReader = object : ExplorerActionFileIdentityReader {
        private val identity = ExplorerActionFileIdentity(1L, 1L)

        override fun stat(file: File) = identity

        override fun fstat(descriptor: FileDescriptor) = identity
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected ${T::class.java.name}, got $error", error is T)
    }
}
