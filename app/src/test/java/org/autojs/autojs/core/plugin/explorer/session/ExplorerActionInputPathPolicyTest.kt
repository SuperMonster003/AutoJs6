package org.autojs.autojs.core.plugin.explorer.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExplorerActionInputPathPolicyTest {

    @Test
    fun relatedFileNamesAreDerivedOnlyFromStrictSuffixes() {
        assertEquals(
            "release.apk.idsig",
            ExplorerActionRelatedFilePolicy.candidateName("release.apk", ".idsig"),
        )
        assertTrue(ExplorerActionRelatedFilePolicy.isValidSuffix(".proof.sig"))
        listOf("idsig", "../idsig", ".", ".bad/idsig", ".bad\\idsig", ".sig.").forEach { suffix ->
            assertFalse(ExplorerActionRelatedFilePolicy.isValidSuffix(suffix))
        }
    }

    @Test
    fun `relative path grammar rejects ambiguous or escaping segments`() {
        assertEquals("nested/file.txt", ExplorerActionInputPathPolicy.normalizeRelativePath("nested/file.txt"))

        listOf("/absolute", "trailing/", "nested//file", ".", "..", "nested/../file", "bad\u0000name")
            .forEach { path ->
                assertThrows(IllegalArgumentException::class.java) {
                    ExplorerActionInputPathPolicy.normalizeRelativePath(path)
                }
            }
    }

    @Test
    fun `directory resolution remains under the selected root`() {
        withTempDirectory { parent ->
            val root = File(parent, "root").apply { mkdir() }.canonicalFile
            val nested = File(root, "nested").apply { mkdir() }.canonicalFile

            assertEquals(root, ExplorerActionInputPathPolicy.resolve(root, ""))
            assertEquals(nested, ExplorerActionInputPathPolicy.resolve(root, "nested"))
            assertThrows(IllegalArgumentException::class.java) {
                ExplorerActionInputPathPolicy.resolve(root, "../outside")
            }
        }
    }

    @Test
    fun `file targets expose direct siblings only when capability allows it`() {
        withTempDirectory { parent ->
            val canonicalParent = parent.canonicalFile
            val target = File(canonicalParent, "target.txt").apply { writeText("target") }.canonicalFile
            val sibling = File(canonicalParent, "sibling.txt").apply { writeText("sibling") }.canonicalFile

            assertEquals(
                target,
                ExplorerActionInputPathPolicy.resolveInput(canonicalParent, target, "", false),
            )
            assertEquals(
                sibling,
                ExplorerActionInputPathPolicy.resolveInput(canonicalParent, target, sibling.name, true),
            )
            assertThrows(IllegalStateException::class.java) {
                ExplorerActionInputPathPolicy.resolveInput(canonicalParent, target, sibling.name, false)
            }
            assertThrows(IllegalArgumentException::class.java) {
                ExplorerActionInputPathPolicy.resolveInput(canonicalParent, target, "nested/file.txt", true)
            }
        }
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("explorer-input-policy").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
