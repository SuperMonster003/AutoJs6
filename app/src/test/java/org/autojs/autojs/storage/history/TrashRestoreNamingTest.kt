package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashRestoreNamingTest {

    @Test
    fun fileKeepsExtension() {
        assertEquals("a-1.js", TrashRestoreNaming.nextIndexedName(listOf("a.js"), "a.js", isDirectory = false))
        assertEquals("a-2.js", TrashRestoreNaming.nextIndexedName(listOf("a.js", "a-1.js"), "a.js", isDirectory = false))
    }

    @Test
    fun directoryWithDotIsNotSplit() {
        assertEquals("my.project-1", TrashRestoreNaming.nextIndexedName(listOf("my.project"), "my.project", isDirectory = true))
        assertEquals("my.project-2", TrashRestoreNaming.nextIndexedName(listOf("my.project", "my.project-1"), "my.project", isDirectory = true))
    }

    @Test
    fun fileWithoutExtensionAndDotFiles() {
        assertEquals("README-1", TrashRestoreNaming.nextIndexedName(listOf("README"), "README", isDirectory = false))
        assertEquals(".env-1", TrashRestoreNaming.nextIndexedName(listOf(".env"), ".env", isDirectory = false))
        // Only the last extension is treated as the extension (same rule as the explorer's rename flow).
        assertEquals("archive.tar-1.gz", TrashRestoreNaming.nextIndexedName(listOf("archive.tar.gz"), "archive.tar.gz", isDirectory = false))
    }

    @Test
    fun firstGapIsUsedAndUnrelatedNamesAreIgnored() {
        val existing = listOf("a.js", "a-1.js", "a-3.js", "a-x.js", "a-2.txt", "b-2.js", "a-2")
        assertEquals("a-2.js", TrashRestoreNaming.nextIndexedName(existing, "a.js", isDirectory = false))
    }

    @Test
    fun regexSpecialCharactersInNamesAreLiteral() {
        assertEquals("a(b)+c-2.js", TrashRestoreNaming.nextIndexedName(listOf("a(b)+c-1.js"), "a(b)+c.js", isDirectory = false))
        assertEquals("a.b-1", TrashRestoreNaming.nextIndexedName(listOf("axb-1"), "a.b", isDirectory = true))
    }

    @Test
    fun splitNameRules() {
        assertEquals("a" to ".js", TrashRestoreNaming.splitName("a.js", false))
        assertEquals("a.b" to "", TrashRestoreNaming.splitName("a.b", true))
        assertEquals(".env" to "", TrashRestoreNaming.splitName(".env", false))
        assertEquals("noext" to "", TrashRestoreNaming.splitName("noext", false))
    }
}
