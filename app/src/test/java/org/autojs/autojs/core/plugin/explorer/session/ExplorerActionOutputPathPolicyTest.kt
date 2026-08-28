package org.autojs.autojs.core.plugin.explorer.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExplorerActionOutputPathPolicyTest {

    @Test
    fun `output name accepts ordinary names and rejects unsafe names`() {
        assertEquals("archive.tar.gz", ExplorerActionOutputPathPolicy.normalizeName("archive.tar.gz"))

        listOf("", ".", "..", " spaced", "spaced ", "...", "folder/file", "folder\\file", "bad\u0000name")
            .forEach { name ->
                assertThrows(IllegalArgumentException::class.java) {
                    ExplorerActionOutputPathPolicy.normalizeName(name)
                }
            }
    }

    @Test
    fun `output relative path validates every segment and reserved marker`() {
        assertEquals(
            "folder/file.txt",
            ExplorerActionOutputPathPolicy.normalizeRelativePath("folder/file.txt"),
        )

        listOf(
            "/folder/file.txt",
            "folder/file.txt/",
            "folder//file.txt",
            "folder/../file.txt",
            "${ExplorerActionOutputPathPolicy.OUTPUT_TREE_MARKER_PREFIX}id/file.txt",
        ).forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                ExplorerActionOutputPathPolicy.normalizeRelativePath(path)
            }
        }
    }

    @Test
    fun `collision key is case insensitive and unicode normalized`() {
        assertEquals(
            ExplorerActionOutputPathPolicy.collisionKey("CAFÉ"),
            ExplorerActionOutputPathPolicy.collisionKey("cafe\u0301"),
        )
    }
}
