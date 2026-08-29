package org.autojs.autojs.core.plugin.explorer.archive

import android.os.ParcelFileDescriptor
import io.reactivex.Single
import java.io.File
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.ExplorerProvider
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.util.FileUtils

internal sealed class ArchiveExplorerItem(
    val entryId: String,
    private val displayName: String,
    private val parentPage: ArchiveExplorerPage?,
    private val syntheticPath: String,
    private val modifiedTime: Long,
    private val uncompressedSize: Long,
    val compressedSize: Long,
    val canExtractEntry: Boolean,
    val canOpenEntry: Boolean,
    val canDeleteEntry: Boolean,
    val canRenameEntry: Boolean,
    val canCreateChildren: Boolean,
    val canAddChildren: Boolean,
) : ExplorerItem {

    override fun getName(): String = displayName

    override fun getParent(): ExplorerPage? = parentPage

    override fun getPath(): String = syntheticPath

    override fun lastModified(): Long = modifiedTime

    override fun getSize(): Long = uncompressedSize

    override fun canMove(): Boolean = false

    override fun canCopy(): Boolean = false

    override fun canRename(): Boolean = canRenameEntry

    override fun canDelete(): Boolean = canDeleteEntry

    override fun canBuildApk(): Boolean = false

    override fun canSetAsWorkingDir(): Boolean = false

    override fun isExecutable(): Boolean = false

    override fun isInstallable(): Boolean = false

    override fun isTextEditable(): Boolean = false

    override fun isExternalEditable(): Boolean = false

    override fun isImageViewable(): Boolean = false

    override fun isAudioPlayable(): Boolean = false

    override fun isVideoPlayable(): Boolean = false

    override fun isMediaPlayable(): Boolean = false

    override fun isMediaMenu(): Boolean = false

    override fun isPreviewable(): Boolean = false

    override fun getType(): FileUtils.TYPE = FileUtils.TYPE.determineBy(File(displayName))

    override fun toScriptFile(): ScriptFile = ScriptFile(syntheticPath)

    override fun equals(other: Any?): Boolean =
        this === other || other is ArchiveExplorerItem && syntheticPath == other.syntheticPath

    override fun hashCode(): Int = syntheticPath.hashCode()
}

internal class ArchiveExplorerFileItem(
    info: ExplorerArchiveItemInfo,
    parent: ArchiveExplorerPage,
    path: String,
) : ArchiveExplorerItem(
    entryId = info.id,
    displayName = info.name,
    parentPage = parent,
    syntheticPath = path,
    modifiedTime = info.lastModified,
    uncompressedSize = info.size,
    compressedSize = info.compressedSize,
    canExtractEntry = info.canExtract,
    canOpenEntry = info.canOpen,
    canDeleteEntry = info.canDelete,
    canRenameEntry = info.canRename,
    canCreateChildren = false,
    canAddChildren = false,
)

internal class ArchiveExplorerPage internal constructor(
    entryId: String,
    val parentId: String?,
    name: String,
    parent: ArchiveExplorerPage?,
    path: String,
    modifiedTime: Long,
    size: Long,
    canExtract: Boolean,
    canDelete: Boolean,
    canRename: Boolean,
    canCreateChildren: Boolean,
    canAddChildren: Boolean,
) : ArchiveExplorerItem(
    entryId = entryId,
    displayName = name,
    parentPage = parent,
    syntheticPath = path,
    modifiedTime = modifiedTime,
    uncompressedSize = size,
    compressedSize = -1L,
    canExtractEntry = canExtract,
    canOpenEntry = false,
    canDeleteEntry = canDelete,
    canRenameEntry = canRename,
    canCreateChildren = canCreateChildren,
    canAddChildren = canAddChildren,
), ExplorerPage {

    private var children: MutableList<ExplorerItem> = mutableListOf()

    override fun copyChildren(page: ExplorerPage) {
        if (page === this) return
        require(page is ArchiveExplorerPage) { "Archive page type changed" }
        children = page.children.toMutableList()
    }

    override fun updateChild(oldItem: ExplorerItem, newItem: ExplorerItem) {
        val index = children.indexOf(oldItem)
        if (index >= 0) children[index] = newItem
    }

    override fun removeChild(item: ExplorerItem) {
        children.remove(item)
    }

    override fun addChild(item: ExplorerItem) {
        children += item
    }

    override fun iterator(): MutableIterator<ExplorerItem> = children.iterator()
}

internal class ArchiveExplorerProvider(
    private val connection: ExplorerArchiveSessionClient,
    private val cacheDirectory: File,
) : ExplorerProvider {

    val canExtractEntries: Boolean
        get() = connection.info.canExtractEntries

    val canDeleteEntries: Boolean
        get() = connection.info.canDeleteEntries

    val canRenameEntries: Boolean
        get() = connection.info.canRenameEntries

    val canCreateDirectory: Boolean
        get() = connection.info.canCreateDirectory

    val canAddEntries: Boolean
        get() = connection.info.canAddEntries

    val formatDisplayName: String?
        get() = connection.info.formatDisplayName

    val shouldAnnotateFormat: Boolean
        get() = !connection.info.displayNameMatchesFormat && formatDisplayName != null

    val filenameCharsetNames: List<String>
        get() = connection.info.filenameCharsetNames

    val filenameCharsetName: String?
        get() = connection.info.filenameCharsetName

    val filenameCharsetOverride: String?
        get() = connection.info.filenameCharsetOverride

    private var entryMaterializer = ArchiveEntryMaterializer(
        cacheDirectory = cacheDirectory,
        sessionId = connection.info.sessionId,
    )

    private val itemInfoById = LinkedHashMap<String, ExplorerArchiveItemInfo>()
    private val pageById = LinkedHashMap<String, ArchiveExplorerPage>()
    private val pageByPath = LinkedHashMap<String, ArchiveExplorerPage>()
    private val restorationLoadedPageIds = HashSet<String>()

    var rootPage = createRootPage()
        private set

    override fun getExplorerPage(parent: ExplorerPage): Single<out ExplorerPage> = Single.fromCallable {
        require(parent is ArchiveExplorerPage) { "Archive provider received a filesystem page" }
        val loaded = ArchiveExplorerPage(
            entryId = parent.entryId,
            parentId = parent.parentId,
            name = parent.name,
            parent = parent.parent as? ArchiveExplorerPage,
            path = parent.path,
            modifiedTime = parent.lastModified(),
            size = parent.size,
            canExtract = parent.canExtractEntry,
            canDelete = parent.canDeleteEntry,
            canRename = parent.canRenameEntry,
            canCreateChildren = parent.canCreateChildren,
            canAddChildren = parent.canAddChildren,
        )
        connection.listAllChildren(parent.entryId).forEach { info ->
            check(itemInfoById.putIfAbsent(info.id, info)?.let { it == info } != false) {
                "Archive session changed metadata for an existing entry"
            }
            val path = syntheticPath(info.id)
            if (info.isDirectory) {
                ArchiveExplorerPage(
                    entryId = info.id,
                    parentId = info.parentId,
                    name = info.name,
                    parent = parent,
                    path = path,
                    modifiedTime = info.lastModified,
                    size = info.size,
                    canExtract = connection.info.canExtractEntries && info.canExtract,
                    canDelete = connection.info.canDeleteEntries && info.canDelete,
                    canRename = connection.info.canRenameEntries && info.canRename,
                    canCreateChildren =
                        connection.info.canCreateDirectory && info.canCreateChildren,
                    canAddChildren = connection.info.canAddEntries && info.canAddChildren,
                ).also {
                    loaded.addChild(it)
                    rememberPage(it)
                }
            } else {
                loaded.addChild(
                    ArchiveExplorerFileItem(
                        info.copy(
                            canExtract = connection.info.canExtractEntries && info.canExtract,
                            canOpen = connection.info.canOpenEntries && info.canOpen,
                            canDelete = connection.info.canDeleteEntries && info.canDelete,
                            canRename = connection.info.canRenameEntries && info.canRename,
                        ),
                        parent,
                        path,
                    ),
                )
            }
        }
        loaded
    }

    fun pageForPath(path: String): ArchiveExplorerPage? = pageByPath[path]

    fun locatorFor(item: ArchiveExplorerItem): List<String> {
        val result = ArrayList<String>()
        val visited = HashSet<String>()
        var current: ArchiveExplorerItem? = item
        while (current != null) {
            check(visited.add(current.entryId)) {
                "Archive session returned a cyclic entry hierarchy"
            }
            result += current.entryId
            current = current.parent as? ArchiveExplorerPage
        }
        val locator = result.asReversed()
        check(locator.firstOrNull() == connection.info.rootId) {
            "Archive entry does not belong to this session"
        }
        return locator
    }

    fun reindexFilenameCharset(filenameCharsetName: String?): ExplorerArchiveSessionInfo {
        val updated = connection.reindexFilenameCharset(filenameCharsetName)
        itemInfoById.clear()
        pageById.clear()
        pageByPath.clear()
        restorationLoadedPageIds.clear()
        entryMaterializer.close()
        entryMaterializer = ArchiveEntryMaterializer(
            cacheDirectory = cacheDirectory,
            sessionId = updated.sessionId,
        )
        rootPage = createRootPage()
        return updated
    }

    fun reloadAfterMutation(): ExplorerArchiveSessionInfo {
        val updated = connection.info
        itemInfoById.clear()
        pageById.clear()
        pageByPath.clear()
        restorationLoadedPageIds.clear()
        entryMaterializer.close()
        entryMaterializer = ArchiveEntryMaterializer(
            cacheDirectory = cacheDirectory,
            sessionId = updated.sessionId,
        )
        rootPage = createRootPage()
        return updated
    }

    fun restorePage(locator: List<String>): List<ArchiveExplorerPage> {
        val restored = arrayListOf(rootPage)
        if (locator.firstOrNull() != rootPage.entryId) return restored
        var current = rootPage
        locator.drop(1).forEach { entryId ->
            ensurePageLoaded(current)
            val child = current
                .filterIsInstance<ArchiveExplorerPage>()
                .firstOrNull { page -> page.entryId == entryId }
                ?: return restored
            restored += child
            current = child
        }
        return restored
    }

    fun restoreItem(locator: List<String>): ArchiveExplorerItem? {
        if (locator.firstOrNull() != rootPage.entryId || locator.size < 2) return null
        var current = rootPage
        locator.drop(1).forEachIndexed { index, entryId ->
            ensurePageLoaded(current)
            val child = current
                .filterIsInstance<ArchiveExplorerItem>()
                .firstOrNull { item -> item.entryId == entryId }
                ?: return null
            if (index == locator.size - 2) return child
            current = child as? ArchiveExplorerPage ?: return null
        }
        return null
    }

    fun startExtraction(
        items: List<ArchiveExplorerItem>,
        options: ExplorerArchiveExtractionRequestOptions,
        listener: ExplorerArchiveExtractionListener,
    ): String {
        check(canExtractEntries) { "Archive session does not support host extraction" }
        require(items.isNotEmpty()) { "Archive extraction selection is empty" }
        val entryIds = items.map { item ->
            check(item.canExtractEntry) { "Archive entry cannot be extracted" }
            val knownPath = pageById[item.entryId]?.path
                ?: itemInfoById[item.entryId]?.let { syntheticPath(it.id) }
            check(knownPath == item.path) { "Archive entry does not belong to this session" }
            item.entryId
        }
        return connection.startExtraction(entryIds, options, listener)
    }

    fun cancelExtraction(operationId: String) {
        connection.cancelExtraction(operationId)
    }

    fun startDeletion(
        items: List<ArchiveExplorerItem>,
        listener: ExplorerArchiveMutationListener,
    ): String {
        check(canDeleteEntries) { "Archive session does not support deletion" }
        require(items.isNotEmpty()) { "Archive deletion selection is empty" }
        val entryIds = items.map { item ->
            check(item.canDeleteEntry) { "Archive entry cannot be deleted" }
            requireKnownItem(item)
            item.entryId
        }
        return connection.startMutation(ExplorerArchiveMutationRequest.Delete(entryIds), listener)
    }

    fun startRename(
        item: ArchiveExplorerItem,
        newName: String,
        listener: ExplorerArchiveMutationListener,
    ): String {
        check(canRenameEntries && item.canRenameEntry) { "Archive entry cannot be renamed" }
        requireKnownItem(item)
        return connection.startMutation(
            ExplorerArchiveMutationRequest.Rename(item.entryId, newName),
            listener,
        )
    }

    fun startCreateDirectory(
        parent: ArchiveExplorerPage,
        newName: String,
        listener: ExplorerArchiveMutationListener,
    ): String {
        check(canCreateDirectory && parent.canCreateChildren) {
            "Archive directory does not support child creation"
        }
        requireKnownItem(parent)
        return connection.startMutation(
            ExplorerArchiveMutationRequest.CreateDirectory(parent.entryId, newName),
            listener,
        )
    }

    fun startAddEntries(
        parent: ArchiveExplorerPage,
        selectedRoots: List<File>,
        checkCancelled: () -> Unit,
        listener: ExplorerArchiveMutationListener,
    ): String {
        check(canAddEntries && parent.canAddChildren) {
            "Archive directory does not support adding entries"
        }
        require(selectedRoots.isNotEmpty()) { "Archive input selection is empty" }
        requireKnownItem(parent)
        return connection.startMutation(
            ExplorerArchiveMutationRequest.AddEntries(
                parent.entryId,
                selectedRoots,
                checkCancelled,
            ),
            listener,
        )
    }

    fun cancelMutation(operationId: String) {
        connection.cancelMutation(operationId)
    }

    fun materializeEntry(item: ArchiveExplorerFileItem, maxBytes: Long): File {
        check(item.canOpenEntry) { "Archive entry cannot be opened" }
        val info = requireNotNull(itemInfoById[item.entryId]) { "Archive entry metadata is unavailable" }
        check(!info.isDirectory && info.name == item.name && info.size == item.size) {
            "Archive entry metadata changed"
        }
        val input = ParcelFileDescriptor.AutoCloseInputStream(connection.openEntry(item.entryId))
        return input.use { stream ->
            entryMaterializer.materialize(
                entryId = item.entryId,
                displayName = item.name,
                expectedSize = item.size,
                maxBytes = maxBytes,
                input = stream,
            )
        }
    }

    fun ancestors(page: ArchiveExplorerPage): List<ArchiveExplorerPage> {
        val result = ArrayList<ArchiveExplorerPage>()
        val visitedEntryIds = HashSet<String>()
        var current: ArchiveExplorerPage? = page
        while (current != null) {
            check(visitedEntryIds.add(current.entryId)) {
                "Archive session returned a cyclic directory hierarchy"
            }
            result += current
            current = current.parentId?.let(pageById::get)
        }
        return result.asReversed()
    }

    fun close() {
        connection.close()
        entryMaterializer.close()
    }

    private fun createRootPage() = ArchiveExplorerPage(
        entryId = connection.info.rootId,
        parentId = null,
        name = connection.info.displayName,
        parent = null,
        path = syntheticPath(connection.info.rootId),
        modifiedTime = connection.info.sourceLastModified,
        size = connection.info.sourceSize,
        canExtract = connection.info.canExtractEntries,
        canDelete = false,
        canRename = false,
        canCreateChildren = connection.info.canCreateDirectory,
        canAddChildren = connection.info.canAddEntries,
    ).also(::rememberPage)

    private fun requireKnownItem(item: ArchiveExplorerItem) {
        val knownPath = pageById[item.entryId]?.path
            ?: itemInfoById[item.entryId]?.let { syntheticPath(it.id) }
        check(knownPath == item.path) { "Archive entry does not belong to this session" }
    }

    private fun ensurePageLoaded(page: ArchiveExplorerPage) {
        if (!restorationLoadedPageIds.add(page.entryId)) return
        page.copyChildren(getExplorerPage(page).blockingGet())
    }

    private fun rememberPage(page: ArchiveExplorerPage) {
        pageById[page.entryId] = page
        pageByPath[page.path] = page
    }

    private fun syntheticPath(entryId: String): String =
        "$SCHEME${connection.info.sessionId}/$entryId"

    private companion object {
        const val SCHEME = "archive-session://"
    }
}
