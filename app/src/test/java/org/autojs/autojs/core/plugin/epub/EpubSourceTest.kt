package org.autojs.autojs.core.plugin.epub

import org.autojs.plugin.epub.api.EpubErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun codeOf(block: () -> Unit): String = try {
        block()
        fail("expected an EpubPluginException")
        ""
    } catch (e: EpubPluginException) {
        e.code
    }

    @Test
    fun blankMissingAndDirectoryPathsAreRefusedWithTheirCodes() {
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubSource.validate("   ") })
        assertEquals(EpubErrorCodes.FILE_NOT_FOUND, codeOf { EpubSource.validate(File(folder.root, "missing.epub").path) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { EpubSource.validate(folder.newFolder("dir.epub").path) })
    }

    @Test
    fun anEpubNameOrAZipSignatureIsRequired() {
        val text = folder.newFile("notes.txt").apply { writeText("plain text") }
        assertEquals(EpubErrorCodes.NOT_EPUB, codeOf { EpubSource.validate(text.path) })
        val zip = folder.newFile("book.bin").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)) }
        assertEquals(zip, EpubSource.validate(zip.path))
        val named = folder.newFile("book.EPUB").apply { writeText("not really a zip") }
        assertEquals(named, EpubSource.validate(named.path))
        val empty = folder.newFile("empty.bin")
        assertEquals(EpubErrorCodes.NOT_EPUB, codeOf { EpubSource.validate(empty.path) })
    }

    @Test
    fun theNameCheckIsCaseInsensitiveAndNeedsTheExtension() {
        assertTrue(EpubSource.isEpubName("a.epub"))
        assertTrue(EpubSource.isEpubName("A.EpUb"))
        assertFalse(EpubSource.isEpubName("epub"))
        assertFalse(EpubSource.isEpubName("a.epub.zip"))
        assertFalse(EpubSource.isEpubName(".epub.txt"))
    }

    @Test
    fun theZipSignatureCheckReadsTheFirstFourBytesOnly() {
        assertTrue(EpubSource.hasZipSignature(folder.newFile("z").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }))
        assertFalse(EpubSource.hasZipSignature(folder.newFile("s").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03)) }))
        assertFalse(EpubSource.hasZipSignature(folder.newFile("t").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) }))
        assertFalse(EpubSource.hasZipSignature(File(folder.root, "none")))
    }
}
