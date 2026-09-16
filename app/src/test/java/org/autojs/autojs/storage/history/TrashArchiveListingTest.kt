package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TrashArchiveListingTest {

    private fun sampleZip(): File {
        val file = File.createTempFile("trash-listing", ".zip").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("sub/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/a.js"))
            zos.write(ByteArray(10))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("b.txt"))
            zos.write(ByteArray(25))
            zos.closeEntry()
        }
        return file
    }

    @Test
    fun listsEntriesWithSizesFromCentralDirectory() {
        val listing = TrashArchiveListing.read(sampleZip())
        assertEquals(2, listing.fileCount)
        assertEquals(1, listing.dirCount)
        assertEquals(35L, listing.totalBytes)
        assertFalse(listing.truncated)
        assertEquals(listOf("sub/", "sub/a.js", "b.txt"), listing.entries.map { it.relativePath })
        assertEquals(listOf(0L, 10L, 25L), listing.entries.map { it.sizeBytes })
        assertEquals(listOf(true, false, false), listing.entries.map { it.isDirectory })
    }

    @Test
    fun truncationKeepsCountsComplete() {
        val listing = TrashArchiveListing.read(sampleZip(), maxEntries = 1)
        assertTrue(listing.truncated)
        assertEquals(1, listing.entries.size)
        assertEquals(2, listing.fileCount)
        assertEquals(1, listing.dirCount)
        assertEquals(35L, listing.totalBytes)
    }

    @Test
    fun emptyAndDirectoryOnlyArchives() {
        val empty = File.createTempFile("trash-listing-empty", ".zip").apply { deleteOnExit() }
        ZipOutputStream(empty.outputStream()).use { zos ->
            // A zip needs at least one entry to be well-formed; a directory entry keeps it "empty" of files.
            zos.putNextEntry(ZipEntry("only/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("only/nested/"))
            zos.closeEntry()
        }
        val listing = TrashArchiveListing.read(empty)
        assertEquals(0, listing.fileCount)
        assertEquals(2, listing.dirCount)
        assertEquals(0L, listing.totalBytes)
        assertFalse(listing.truncated)
        assertEquals(listOf("only/", "only/nested/"), listing.entries.map { it.relativePath })
        assertTrue(listing.entries.all { it.isDirectory })
    }

    @Test
    fun truncationAtExactEntryCountIsNotTruncated() {
        val listing = TrashArchiveListing.read(sampleZip(), maxEntries = 3)
        assertFalse(listing.truncated)
        assertEquals(3, listing.entries.size)
    }

    @Test
    fun binarySniff() {
        assertFalse(TrashArchiveListing.looksBinary("console.log('hi');\n".toByteArray()))
        assertFalse(TrashArchiveListing.looksBinary(ByteArray(0)))
        assertTrue(TrashArchiveListing.looksBinary(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)))
    }
}
