package org.autojs.autojs.core.plugin.explorer.archive

import android.os.ParcelFileDescriptor
import org.autojs.autojs.model.explorer.ExplorerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveExplorerProviderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun providerBuildsReadOnlyPagesAndStableAncestorChain() {
        val client = FakeClient()
        val provider = ArchiveExplorerProvider(client, temporaryFolder.newFolder("cache"))
        val root = provider.rootPage

        val loadedRoot = provider.getExplorerPage(root).blockingGet() as ArchiveExplorerPage
        root.copyChildren(loadedRoot)
        val rootItems = root.toList()
        assertEquals(listOf("folder", "root.txt"), rootItems.map(ExplorerItem::getName))
        assertTrue(rootItems.first() is ArchiveExplorerPage)
        assertTrue(rootItems.last() is ArchiveExplorerFileItem)
        assertTrue((rootItems.last() as ArchiveExplorerFileItem).canOpenEntry)
        rootItems.forEach { item ->
            assertFalse(item.canCopy())
            assertFalse(item.canMove())
            assertFalse(item.canRename())
            assertFalse(item.canDelete())
            assertFalse(item.canBuildApk())
            assertFalse(item.canSetAsWorkingDir())
        }

        val folder = rootItems.first() as ArchiveExplorerPage
        assertEquals(listOf("sample.zip", "folder"), provider.ancestors(folder).map(ExplorerItem::getName))
        assertEquals(folder, provider.pageForPath(folder.path))

        val loadedFolder = provider.getExplorerPage(folder).blockingGet() as ArchiveExplorerPage
        folder.copyChildren(loadedFolder)
        assertEquals(listOf("nested.txt"), folder.toList().map(ExplorerItem::getName))

        provider.close()
        assertTrue(client.closed)
    }

    @Test
    fun providerKeepsV5EntriesNonOpenable() {
        val client = FakeClient(canOpenEntries = false)
        val provider = ArchiveExplorerProvider(client, temporaryFolder.newFolder("cache"))
        val root = provider.rootPage

        root.copyChildren(provider.getExplorerPage(root).blockingGet())
        val file = root.toList().last() as ArchiveExplorerFileItem

        assertFalse(file.canOpenEntry)
        assertThrows(IllegalStateException::class.java) {
            provider.materializeEntry(file, maxBytes = 1024L)
        }
        assertEquals(0, client.openEntryCalls)

        provider.close()
    }

    private class FakeClient(
        canOpenEntries: Boolean = true,
    ) : ExplorerArchiveSessionClient {
        override val sourcePath = "/storage/emulated/0/Archives/sample.zip"
        override val info = ExplorerArchiveSessionInfo(
            sessionId = "session",
            rootId = "root",
            displayName = "sample.zip",
            sourceSize = 100L,
            sourceLastModified = 200L,
            canOpenEntries = canOpenEntries,
        )
        var closed = false
        var openEntryCalls = 0

        override fun listAllChildren(parentId: String): List<ExplorerArchiveItemInfo> = when (parentId) {
            "root" -> listOf(
                item("folder", "root", "folder", isDirectory = true, size = 6L),
                item("root-file", "root", "root.txt", isDirectory = false, size = 4L),
            )
            "folder" -> listOf(
                item("nested-file", "folder", "nested.txt", isDirectory = false, size = 6L),
            )
            else -> error("Unknown parent: $parentId")
        }

        override fun openEntry(entryId: String): ParcelFileDescriptor {
            openEntryCalls += 1
            error("Entry streaming is not used by this provider test: $entryId")
        }

        override fun close() {
            closed = true
        }

        private fun item(
            id: String,
            parentId: String,
            name: String,
            isDirectory: Boolean,
            size: Long,
        ) = ExplorerArchiveItemInfo(
            id = id,
            parentId = parentId,
            name = name,
            isDirectory = isDirectory,
            size = size,
            compressedSize = if (isDirectory) -1L else size,
            lastModified = 300L,
            canExtract = true,
        )
    }
}
