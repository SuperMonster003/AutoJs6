package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

class DexCompilerPersistentCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun committedEntrySurvivesRecoveryAndRevalidatesBytes() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val bytes = "verified-dex-output".toByteArray()
        val key = "11".repeat(32)
        val first = cache(root, ops).publishVerified(key, sha256(bytes), bytes.size.toLong(), verified(root, ops, bytes))

        val recovered = cache(root, ops).lookup(key)

        assertNotNull(recovered)
        assertEquals(first.generation, recovered!!.generation)
        assertArrayEquals(bytes, cache(root, ops).openRead(recovered)!!.use { it.readBytes() })
        assertTrue(ops.isOwnerReadOnlyRegularFile(artifact(root, key)))
        assertTrue(ops.isOwnerReadOnlyRegularFile(manifest(root, key)))
    }

    @Test
    fun corruptArtifactEvictsOnlyItsExactEntry() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val firstKey = "21".repeat(32)
        val secondKey = "22".repeat(32)
        val firstBytes = "first-output".toByteArray()
        val secondBytes = "second-output".toByteArray()
        val cache = cache(root, ops)
        cache.publishVerified(firstKey, sha256(firstBytes), firstBytes.size.toLong(), verified(root, ops, firstBytes))
        cache.publishVerified(secondKey, sha256(secondBytes), secondBytes.size.toLong(), verified(root, ops, secondBytes))
        artifact(root, firstKey).writeBytes("broken-outp".toByteArray())

        assertNull(cache.lookup(firstKey))
        val retained = cache.lookup(secondKey)
        assertNotNull(retained)
        assertArrayEquals(secondBytes, cache.openRead(retained!!)!!.use { it.readBytes() })
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("$firstKey.") })
        assertTrue(root.listFiles().orEmpty().any { it.name.startsWith("$secondKey.") })
    }

    @Test
    fun staleHandleCannotInvalidateReplacementGeneration() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val key = "31".repeat(32)
        val firstBytes = "generation-one".toByteArray()
        val secondBytes = "generation-two".toByteArray()
        val cache = cache(root, ops)
        val stale = cache.publishVerified(
            key,
            sha256(firstBytes),
            firstBytes.size.toLong(),
            verified(root, ops, firstBytes),
        )
        val current = cache.publishVerified(
            key,
            sha256(secondBytes),
            secondBytes.size.toLong(),
            verified(root, ops, secondBytes),
        )

        assertFalse(cache.invalidate(stale))
        assertEquals(current.generation, cache.lookup(key)!!.generation)
        assertArrayEquals(secondBytes, cache.openRead(current)!!.use { it.readBytes() })
    }

    @Test
    fun openReadRevalidatesTheOpenedDescriptorAfterLookup() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val key = "39".repeat(32)
        val bytes = "descriptor-before".toByteArray()
        val cache = cache(root, ops)
        cache.publishVerified(key, sha256(bytes), bytes.size.toLong(), verified(root, ops, bytes))
        val lookedUp = cache.lookup(key)!!
        artifact(root, key).writeBytes("descriptor-after!".toByteArray())

        assertNull(cache.openRead(lookedUp))
        assertNull(cache.lookup(key))
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("$key.") })
    }

    @Test
    fun failedManifestCommitPreservesPreviousGeneration() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val key = "41".repeat(32)
        val firstBytes = "stable-generation".toByteArray()
        val replacement = "failed-replacement".toByteArray()
        val cache = cache(root, ops)
        val first = cache.publishVerified(
            key,
            sha256(firstBytes),
            firstBytes.size.toLong(),
            verified(root, ops, firstBytes),
        )
        ops.failNextManifestMove = true

        try {
            cache.publishVerified(
                key,
                sha256(replacement),
                replacement.size.toLong(),
                verified(root, ops, replacement),
            )
            fail("Manifest commit failure must fail publication")
        } catch (_: TestFileOps.InjectedMoveFailure) {
            Unit
        }

        val retained = cache.lookup(key)
        assertNotNull(retained)
        assertEquals(first.generation, retained!!.generation)
        assertArrayEquals(firstBytes, cache.openRead(retained)!!.use { it.readBytes() })
        assertEquals(1, root.listFiles().orEmpty().count { it.name.endsWith(".dex.zip") })
    }

    @Test
    fun manifestDirectorySyncFailurePropagatesAndPreservesPreviousGeneration() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val key = "49".repeat(32)
        val original = "sync-stable".toByteArray()
        val replacement = "sync-failed".toByteArray()
        val cache = cache(root, ops)
        val first = cache.publishVerified(
            key,
            sha256(original),
            original.size.toLong(),
            verified(root, ops, original),
        )
        ops.directorySyncCalls = 0
        ops.failOnDirectorySyncCall = 2

        try {
            cache.publishVerified(
                key,
                sha256(replacement),
                replacement.size.toLong(),
                verified(root, ops, replacement),
            )
            fail("Manifest directory sync failure must propagate")
        } catch (_: TestFileOps.InjectedSyncFailure) {
            Unit
        }

        val retained = cache.lookup(key)!!
        assertEquals(first.generation, retained.generation)
        assertArrayEquals(original, cache.openRead(retained)!!.use { it.readBytes() })
    }

    @Test
    fun recoveryRemovesOnlyKnownCrashLeftoversAndOrphanArtifacts() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val key = "51".repeat(32)
        val bytes = "retained".toByteArray()
        cache(root, ops).publishVerified(key, sha256(bytes), bytes.size.toLong(), verified(root, ops, bytes))
        val manifestTemporary = File(root, "cache-index-${UUID.randomUUID()}.partial").apply { writeText("partial") }
        val legacy = File(root, "${"52".repeat(32)}.dex.zip").apply { writeText("legacy") }
        val orphan = File(root, "${"53".repeat(32)}.${UUID.randomUUID()}.dex.zip").apply { writeText("orphan") }
        ops.makeOwnerReadOnly(orphan)
        val unknown = File(root, "user-note.txt").apply { writeText("leave me") }

        val recovered = cache(root, ops)

        assertNotNull(recovered.lookup(key))
        assertFalse(manifestTemporary.exists())
        assertFalse(legacy.exists())
        assertFalse(orphan.exists())
        assertTrue(unknown.isFile)
    }

    @Test
    fun lastAccessAndStableTieBreakersDriveBoundedEviction() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        var now = 1L
        val limits = DexCompilerPersistentCache.Limits(
            maximumEntryBytes = 4,
            maximumTotalBytes = 8,
            maximumEntries = 2,
        )
        val cache = cache(root, ops, limits) { now }
        val firstKey = "61".repeat(32)
        val secondKey = "62".repeat(32)
        val thirdKey = "63".repeat(32)
        cache.publishVerified(firstKey, sha256(byteArrayOf(1, 1)), 2, verified(root, ops, byteArrayOf(1, 1)))
        now = 2
        cache.publishVerified(secondKey, sha256(byteArrayOf(2, 2)), 2, verified(root, ops, byteArrayOf(2, 2)))
        now = 3
        assertNotNull(cache.lookup(firstKey))
        now = 4
        cache.publishVerified(thirdKey, sha256(byteArrayOf(3, 3)), 2, verified(root, ops, byteArrayOf(3, 3)))

        assertNotNull(cache.lookup(firstKey))
        assertNull(cache.lookup(secondKey))
        assertNotNull(cache.lookup(thirdKey))
        assertEquals(2, root.listFiles().orEmpty().count { it.name.endsWith(".entry") })
        assertEquals(2, root.listFiles().orEmpty().count { it.name.endsWith(".dex.zip") })
    }

    @Test
    fun totalByteLimitEvictsOldestCompleteEntry() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        var now = 1L
        val limits = DexCompilerPersistentCache.Limits(
            maximumEntryBytes = 5,
            maximumTotalBytes = 6,
            maximumEntries = 5,
        )
        val cache = cache(root, ops, limits) { now }
        val oldestKey = "71".repeat(32)
        val currentKey = "72".repeat(32)
        val bytes = byteArrayOf(1, 2, 3, 4)
        cache.publishVerified(oldestKey, sha256(bytes), bytes.size.toLong(), verified(root, ops, bytes))
        now = 2
        cache.publishVerified(currentKey, sha256(bytes), bytes.size.toLong(), verified(root, ops, bytes))

        assertNull(cache.lookup(oldestKey))
        assertNotNull(cache.lookup(currentKey))
    }

    @Test
    fun corruptedManifestIsRejectedWithoutAffectingAnotherKey() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val firstKey = "81".repeat(32)
        val secondKey = "82".repeat(32)
        val bytes = "manifest-test".toByteArray()
        cache(root, ops).apply {
            publishVerified(firstKey, sha256(bytes), bytes.size.toLong(), verified(root, ops, bytes))
            publishVerified(secondKey, sha256(bytes), bytes.size.toLong(), verified(root, ops, bytes))
        }
        val corrupt = manifest(root, firstKey)
        val encoded = corrupt.readBytes()
        encoded[0] = (encoded[0].toInt() xor 1).toByte()
        corrupt.writeBytes(encoded)

        val recovered = cache(root, ops)

        assertNull(recovered.lookup(firstKey))
        assertNotNull(recovered.lookup(secondKey))
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("$firstKey.") })
    }

    @Test
    fun malformedKeyIsRejectedBeforeAnyPathIsDerived() {
        val root = temporaryFolder.newFolder("cache")
        val ops = TestFileOps()
        val outside = temporaryFolder.newFile("outside.txt").apply { writeText("preserve") }

        try {
            cache(root, ops).lookup("../${"aa".repeat(32)}")
            fail("Traversal-shaped cache key must be rejected")
        } catch (_: IllegalArgumentException) {
            Unit
        }

        assertEquals("preserve", outside.readText())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    private fun cache(
        root: File,
        ops: TestFileOps,
        limits: DexCompilerPersistentCache.Limits = DexCompilerPersistentCache.Limits(),
        clock: () -> Long = { 100L },
    ) = DexCompilerPersistentCache(root, ops, limits, clock)

    private fun verified(root: File, ops: TestFileOps, bytes: ByteArray): File =
        File(root, "verified-${UUID.randomUUID()}.partial").apply {
            FileOutputStream(this).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            ops.makeOwnerReadOnly(this)
        }

    private fun artifact(root: File, key: String): File =
        root.listFiles().orEmpty().single { it.name.startsWith("$key.") && it.name.endsWith(".dex.zip") }

    private fun manifest(root: File, key: String): File =
        root.listFiles().orEmpty().single { it.name.startsWith("$key.") && it.name.endsWith(".entry") }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class TestFileOps : DexCompilerPersistentCache.FileOps {
        private val readOnly = mutableSetOf<String>()
        var failNextManifestMove = false
        var directorySyncCalls = 0
        var failOnDirectorySyncCall: Int? = null

        override fun makeOwnerReadWrite(file: File) {
            readOnly.remove(file.canonicalPath)
        }

        override fun makeOwnerReadOnly(file: File) {
            readOnly += file.canonicalPath
        }

        override fun isOwnerReadOnlyRegularFile(file: File): Boolean =
            file.isFile && file.canonicalPath in readOnly

        override fun isOwnerReadOnlyRegularFile(file: File, opened: FileInputStream): Boolean =
            isOwnerReadOnlyRegularFile(file) && opened.fd.valid()

        override fun atomicReplace(source: File, destination: File) {
            if (failNextManifestMove && destination.name.endsWith(".entry")) {
                failNextManifestMove = false
                throw InjectedMoveFailure()
            }
            val sourcePath = source.canonicalPath
            val wasReadOnly = sourcePath in readOnly
            try {
                Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            readOnly.remove(sourcePath)
            if (wasReadOnly) readOnly += destination.canonicalPath else readOnly.remove(destination.canonicalPath)
        }

        override fun syncDirectory(directory: File) {
            directorySyncCalls += 1
            if (directorySyncCalls == failOnDirectorySyncCall) {
                failOnDirectorySyncCall = null
                throw InjectedSyncFailure()
            }
        }

        class InjectedMoveFailure : RuntimeException()

        class InjectedSyncFailure : RuntimeException()
    }
}
