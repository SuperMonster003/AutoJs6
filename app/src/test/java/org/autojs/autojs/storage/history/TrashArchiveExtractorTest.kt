package org.autojs.autojs.storage.history

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class TrashArchiveExtractorTest {

    private fun tempDir(): File = Files.createTempDirectory("trash-extract").toFile().apply { deleteOnExit() }

    /** null content = directory entry. */
    private fun zipWith(vararg entries: Pair<String, ByteArray?>): File {
        val file = File.createTempFile("trash-extract", ".zip").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                if (bytes != null) zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun extractsNestedEntriesAndEmptyDirectories() {
        val dest = tempDir()
        val zip = zipWith(
            "sub/" to null,
            "sub/a.js" to "console.log(1)".toByteArray(),
            "b.txt" to ByteArray(3) { it.toByte() },
            "empty/" to null,
        )

        val report = TrashArchiveExtractor.extract(zip, dest)

        assertEquals(4, report.restoredEntries)
        assertTrue(report.skippedUnsafe.isEmpty())
        assertFalse(report.hasFailures)
        assertEquals("console.log(1)", File(dest, "sub/a.js").readText())
        assertArrayEquals(ByteArray(3) { it.toByte() }, File(dest, "b.txt").readBytes())
        assertTrue(File(dest, "empty").isDirectory)
    }

    @Test
    fun entriesEscapingTheDestinationAreSkipped() {
        val parent = tempDir()
        val dest = File(parent, "dest")
        val zip = zipWith(
            "../evil.txt" to "x".toByteArray(),
            "/abs.txt" to "x".toByteArray(),
            "ok/../../evil2.txt" to "x".toByteArray(),
            "good.txt" to "y".toByteArray(),
        )

        val report = TrashArchiveExtractor.extract(zip, dest)

        assertEquals(listOf("../evil.txt", "/abs.txt", "ok/../../evil2.txt"), report.skippedUnsafe)
        assertEquals(1, report.restoredEntries)
        assertFalse(report.hasFailures)
        assertEquals("y", File(dest, "good.txt").readText())
        assertFalse(File(parent, "evil.txt").exists())
        assertFalse(File(parent, "evil2.txt").exists())
        assertFalse(File(dest, "abs.txt").exists())
    }

    @Test
    fun resolveEntryRules() {
        val dest = tempDir()
        assertNotNull(TrashArchiveExtractor.resolveEntry(dest, "a/b.txt"))
        assertNotNull(TrashArchiveExtractor.resolveEntry(dest, "a/./b.txt"))
        assertNotNull(TrashArchiveExtractor.resolveEntry(dest, "dir/"))
        assertNull(TrashArchiveExtractor.resolveEntry(dest, ""))
        assertNull(TrashArchiveExtractor.resolveEntry(dest, "   "))
        assertNull(TrashArchiveExtractor.resolveEntry(dest, "../x"))
        assertNull(TrashArchiveExtractor.resolveEntry(dest, "a/../b"))
        assertNull(TrashArchiveExtractor.resolveEntry(dest, "/x"))
        assertNull(TrashArchiveExtractor.resolveEntry(dest, "..\\x"))
    }

    @Test
    fun truncatedArchiveKeepsEarlierEntriesAndReportsTheRest() {
        val dest = tempDir()
        val big = Random(42).nextBytes(200 * 1024)
        val zip = zipWith(
            "a.txt" to "first".toByteArray(),
            "b.bin" to big,
        )
        RandomAccessFile(zip, "rw").use { it.setLength(it.length() - 4096) }

        val report = TrashArchiveExtractor.extract(zip, dest)

        assertEquals("first", File(dest, "a.txt").readText())
        assertEquals(1, report.restoredEntries)
        assertTrue(report.hasFailures)
        assertEquals("b.bin", report.failed.first())
        assertFalse("half-written file must be removed", File(dest, "b.bin").exists())
    }

    @Test
    fun checkpointExceptionPropagates() {
        val dest = tempDir()
        val zip = zipWith("a.txt" to "first".toByteArray())
        assertThrows(IllegalStateException::class.java) {
            TrashArchiveExtractor.extract(zip, dest) { throw IllegalStateException("aborted") }
        }
    }
}
