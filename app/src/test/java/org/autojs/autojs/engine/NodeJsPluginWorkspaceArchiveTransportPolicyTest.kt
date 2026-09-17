package org.autojs.autojs.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class NodeJsPluginWorkspaceArchiveTransportPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun unchangedSnapshotFilesIncludeEmptyAndMultiBufferBinaryContent() {
        for (size in listOf(0, 7, 65_543)) {
            val bytes = ByteArray(size) { (it % 251).toByte() }
            assertSnapshotComparison(bytes, bytes, expectedMatch = true)
        }
    }

    @Test
    fun changedSnapshotFilesAreNotSkippedEvenWhenLengthMatches() {
        val bytes = ByteArray(65_543) { (it % 251).toByte() }
        val changed = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex] + 1).toByte() }
        assertSnapshotComparison(bytes, changed, expectedMatch = false)
        assertSnapshotComparison(bytes, bytes.copyOf(bytes.size - 1), expectedMatch = false)
        assertSnapshotComparison(bytes, bytes.copyOf(bytes.size + 1), expectedMatch = false)
    }

    private fun assertSnapshotComparison(expected: ByteArray, actual: ByteArray, expectedMatch: Boolean) {
        val archive = temporaryFolder.newFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("dependency.bin"))
            zip.write(expected)
            zip.closeEntry()
        }
        val target = temporaryFolder.newFile().apply { writeBytes(actual) }
        ZipFile(archive).use { zip ->
            val matches = NodeJsPluginWorkspaceArchiveTransport.archiveEntryMatchesFile(zip, "dependency.bin", target)
            if (expectedMatch) assertTrue(matches) else assertFalse(matches)
            assertFalse(NodeJsPluginWorkspaceArchiveTransport.archiveEntryMatchesFile(zip, "missing.bin", target))
        }
    }

    @Test
    fun suppressesRuntimeOnlyModuleSourceFilesAndTheirReplacementTrees() {
        val ephemeral = setOf("main.cjs", "src/generated.mjs")

        assertTrue(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "main.cjs",
                ephemeral,
            ),
        )
        assertTrue(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "main.cjs/replacement.txt",
                ephemeral,
            ),
        )
        assertTrue(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "src/generated.mjs",
                ephemeral,
            ),
        )
        assertFalse(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "main.cjs.map",
                ephemeral,
            ),
        )
        assertFalse(
            NodeJsPluginWorkspaceArchiveTransport.isEphemeralModuleSourceOutput(
                "src/generated-other.mjs",
                ephemeral,
            ),
        )
    }
}
