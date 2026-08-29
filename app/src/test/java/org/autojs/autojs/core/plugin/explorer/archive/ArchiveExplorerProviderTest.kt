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

    @Test
    fun providerExposesActualFormatAnnotationOnlyForMismatchedNames() {
        val mismatched = ArchiveExplorerProvider(
            FakeClient(
                formatDisplayName = "ZIP",
                displayNameMatchesFormat = false,
            ),
            temporaryFolder.newFolder("mismatched-format-cache"),
        )
        val matching = ArchiveExplorerProvider(
            FakeClient(
                formatDisplayName = "ZIP",
                displayNameMatchesFormat = true,
            ),
            temporaryFolder.newFolder("matching-format-cache"),
        )

        assertEquals("ZIP", mismatched.formatDisplayName)
        assertTrue(mismatched.shouldAnnotateFormat)
        assertFalse(matching.shouldAnnotateFormat)

        mismatched.close()
        matching.close()
    }

    @Test
    fun providerForwardsOnlySessionOwnedOpaqueExtractionIds() {
        val client = FakeClient()
        val provider = ArchiveExplorerProvider(client, temporaryFolder.newFolder("cache"))
        val root = provider.rootPage
        root.copyChildren(provider.getExplorerPage(root).blockingGet())
        val selected = root.toList().map { it as ArchiveExplorerItem }

        val operationId = ExplorerArchiveExtractionRequestOptions().use { options ->
            provider.startExtraction(selected, options, NoOpExtractionListener)
        }
        provider.cancelExtraction(operationId)

        assertEquals("operation", operationId)
        assertEquals(listOf("folder", "root-file"), client.extractionEntryIds)
        assertEquals("operation", client.cancelledOperationId)
        provider.close()
    }

    @Test
    fun providerKeepsPreV10SessionsNonExtractable() {
        val client = FakeClient(canExtractEntries = false)
        val provider = ArchiveExplorerProvider(client, temporaryFolder.newFolder("cache"))
        val root = provider.rootPage
        root.copyChildren(provider.getExplorerPage(root).blockingGet())

        assertFalse(provider.canExtractEntries)
        assertFalse(root.canExtractEntry)
        assertTrue(root.toList().all { item -> !(item as ArchiveExplorerItem).canExtractEntry })
        assertThrows(IllegalStateException::class.java) {
            ExplorerArchiveExtractionRequestOptions().use { options ->
                provider.startExtraction(listOf(root), options, NoOpExtractionListener)
            }
        }
        provider.close()
    }

    @Test
    fun sessionMutationCapabilitiesGateFileAndDirectoryItemCapabilities() {
        val client = FakeClient(
            canMutateEntries = false,
            itemCanMutateEntries = true,
        )
        val provider = ArchiveExplorerProvider(client, temporaryFolder.newFolder("capability-cache"))
        val root = provider.rootPage

        root.copyChildren(provider.getExplorerPage(root).blockingGet())

        assertTrue(root.toList().all { item -> !item.canDelete() && !item.canRename() })
        provider.close()
    }

    @Test
    fun providerForwardsOnlyWritableSessionOwnedMutationIds() {
        val client = FakeClient(canMutateEntries = true)
        val provider = ArchiveExplorerProvider(client, temporaryFolder.newFolder("mutation-cache"))
        val root = provider.rootPage
        root.copyChildren(provider.getExplorerPage(root).blockingGet())
        val items = root.map { it as ArchiveExplorerItem }

        assertTrue(items.all(ExplorerItem::canDelete))
        assertTrue(items.all(ExplorerItem::canRename))
        assertEquals("mutation", provider.startDeletion(items, NoOpMutationListener))
        assertEquals(
            ExplorerArchiveMutationRequest.Delete(listOf("folder", "root-file")),
            client.mutationRequest,
        )

        val file = items.filterIsInstance<ArchiveExplorerFileItem>().single()
        assertEquals("mutation", provider.startRename(file, "renamed.txt", NoOpMutationListener))
        assertEquals(
            ExplorerArchiveMutationRequest.Rename("root-file", "renamed.txt"),
            client.mutationRequest,
        )
        provider.cancelMutation("mutation")
        assertEquals("mutation", client.cancelledMutationId)
        provider.close()
    }

    @Test
    fun providerReindexesAndRestoresStablePageAndSelectionLocators() {
        val client = FakeClient(filenameCharsets = true)
        val provider = ArchiveExplorerProvider(client, temporaryFolder.newFolder("cache"))
        val root = provider.rootPage
        root.copyChildren(provider.getExplorerPage(root).blockingGet())
        val folder = root.filterIsInstance<ArchiveExplorerPage>().single()
        folder.copyChildren(provider.getExplorerPage(folder).blockingGet())
        val nested = folder.filterIsInstance<ArchiveExplorerFileItem>().single()
        val pageLocator = provider.locatorFor(folder)
        val selectionLocator = provider.locatorFor(nested)

        val updated = provider.reindexFilenameCharset("IBM437")
        val restoredPages = provider.restorePage(pageLocator)
        val restoredSelection = provider.restoreItem(selectionLocator)

        assertEquals("IBM437", updated.filenameCharsetOverride)
        assertEquals(listOf("root", "folder"), restoredPages.map(ArchiveExplorerItem::entryId))
        assertEquals("nested-file", restoredSelection?.entryId)
        assertEquals("IBM437", client.reindexedFilenameCharset)
        provider.close()
    }

    private class FakeClient(
        canOpenEntries: Boolean = true,
        canExtractEntries: Boolean = true,
        canMutateEntries: Boolean = false,
        itemCanMutateEntries: Boolean = canMutateEntries,
        filenameCharsets: Boolean = false,
        formatDisplayName: String? = null,
        displayNameMatchesFormat: Boolean = true,
    ) : ExplorerArchiveSessionClient {
        override val sourcePath = "/storage/emulated/0/Archives/sample.zip"
        override var info = ExplorerArchiveSessionInfo(
            sessionId = "session",
            rootId = "root",
            displayName = "sample.zip",
            sourceSize = 100L,
            sourceLastModified = 200L,
            canOpenEntries = canOpenEntries,
            canExtractEntries = canExtractEntries,
            canDeleteEntries = canMutateEntries,
            canRenameEntries = canMutateEntries,
            formatId = formatDisplayName?.lowercase(),
            formatDisplayName = formatDisplayName,
            displayNameMatchesFormat = displayNameMatchesFormat,
            filenameCharsetNames = if (filenameCharsets) listOf("UTF-8", "IBM437") else emptyList(),
            filenameCharsetName = if (filenameCharsets) "UTF-8" else null,
        )
        var closed = false
        var openEntryCalls = 0
        var extractionEntryIds: List<String>? = null
        var cancelledOperationId: String? = null
        var reindexedFilenameCharset: String? = null
        var mutationRequest: ExplorerArchiveMutationRequest? = null
        var cancelledMutationId: String? = null
        private val mutableEntries = itemCanMutateEntries

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

        override fun reindexFilenameCharset(filenameCharsetName: String?): ExplorerArchiveSessionInfo {
            reindexedFilenameCharset = filenameCharsetName
            return info.copy(
                filenameCharsetName = filenameCharsetName ?: "UTF-8",
                filenameCharsetOverride = filenameCharsetName,
            ).also { info = it }
        }

        override fun startExtraction(
            entryIds: List<String>,
            options: ExplorerArchiveExtractionRequestOptions,
            listener: ExplorerArchiveExtractionListener,
        ): String {
            extractionEntryIds = entryIds
            return "operation"
        }

        override fun cancelExtraction(operationId: String) {
            cancelledOperationId = operationId
        }

        override fun startMutation(
            request: ExplorerArchiveMutationRequest,
            listener: ExplorerArchiveMutationListener,
        ): String {
            mutationRequest = request
            return "mutation"
        }

        override fun cancelMutation(operationId: String) {
            cancelledMutationId = operationId
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
            canOpen = !isDirectory,
            canDelete = mutableEntries,
            canRename = mutableEntries,
        )
    }

    private object NoOpExtractionListener : ExplorerArchiveExtractionListener {
        override fun onProgress(progress: ExplorerArchiveExtractionProgress) = Unit
        override fun onCompleted(result: ExplorerArchiveExtractionResult) = Unit
        override fun onFailed(failure: ExplorerArchiveExtractionFailure) = Unit
    }

    private object NoOpMutationListener : ExplorerArchiveMutationListener {
        override fun onProgress(progress: ExplorerArchiveMutationProgress) = Unit
        override fun onCompleted(result: ExplorerArchiveMutationResult) = Unit
        override fun onFailed(failure: ExplorerArchiveMutationFailure) = Unit
    }
}
