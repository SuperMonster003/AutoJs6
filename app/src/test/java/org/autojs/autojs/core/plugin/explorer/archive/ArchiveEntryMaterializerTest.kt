package org.autojs.autojs.core.plugin.explorer.archive

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveEntryMaterializerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun materializesExactBytesUnderTheSessionCacheAndCleansThemOnClose() {
        val bytes = "preview".toByteArray()
        val materializer = ArchiveEntryMaterializer(temporaryFolder.root, "session/../opaque")

        val file = materializer.materialize(
            entryId = "entry/../opaque",
            displayName = "../${"预览".repeat(200)}?.MD",
            expectedSize = bytes.size.toLong(),
            maxBytes = 1_024L,
            input = ByteArrayInputStream(bytes),
        )

        assertTrue(file.isFile)
        assertTrue(file.canonicalPath.startsWith(temporaryFolder.root.canonicalPath))
        assertTrue(file.name == "entry.md")
        assertArrayEquals(bytes, file.readBytes())

        materializer.close()
        assertFalse(file.exists())
    }

    @Test
    fun rejectsEntriesOverTheCallerPreviewBudgetBeforeReading() {
        val materializer = ArchiveEntryMaterializer(temporaryFolder.root, "budget-session")

        val error = assertThrows(ArchiveEntryTooLargeException::class.java) {
            materializer.materialize(
                entryId = "large-entry",
                displayName = "large.bin",
                expectedSize = 2_048L,
                maxBytes = 1_024L,
                input = ByteArrayInputStream(ByteArray(0)),
            )
        }

        assertTrue(error.limitBytes == 1_024L)
        materializer.close()
    }

    @Test
    fun rejectsPluginStreamsThatExceedDeclaredSize() {
        val materializer = ArchiveEntryMaterializer(temporaryFolder.root, "overflow-session")

        assertThrows(IOException::class.java) {
            materializer.materialize(
                entryId = "overflow-entry",
                displayName = "overflow.txt",
                expectedSize = 3L,
                maxBytes = 10L,
                input = ByteArrayInputStream("four".toByteArray()),
            )
        }

        materializer.close()
    }
}
