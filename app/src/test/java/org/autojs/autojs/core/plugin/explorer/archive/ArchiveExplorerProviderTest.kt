package org.autojs.autojs.core.plugin.explorer.archive

import org.autojs.autojs.model.explorer.ExplorerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExplorerProviderTest {

    @Test
    fun providerBuildsReadOnlyPagesAndStableAncestorChain() {
        val client = FakeClient()
        val provider = ArchiveExplorerProvider(client)
        val root = provider.rootPage

        val loadedRoot = provider.getExplorerPage(root).blockingGet() as ArchiveExplorerPage
        root.copyChildren(loadedRoot)
        val rootItems = root.toList()
        assertEquals(listOf("folder", "root.txt"), rootItems.map(ExplorerItem::getName))
        assertTrue(rootItems.first() is ArchiveExplorerPage)
        assertTrue(rootItems.last() is ArchiveExplorerFileItem)
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

    private class FakeClient : ExplorerArchiveSessionClient {
        override val sourcePath = "/storage/emulated/0/Archives/sample.zip"
        override val info = ExplorerArchiveSessionInfo(
            sessionId = "session",
            rootId = "root",
            displayName = "sample.zip",
            sourceSize = 100L,
            sourceLastModified = 200L,
        )
        var closed = false

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
