package org.autojs.autojs.core.plugin.explorer.archive

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
) : ExplorerItem {

    override fun getName(): String = displayName

    override fun getParent(): ExplorerPage? = parentPage

    override fun getPath(): String = syntheticPath

    override fun lastModified(): Long = modifiedTime

    override fun getSize(): Long = uncompressedSize

    override fun canMove(): Boolean = false

    override fun canCopy(): Boolean = false

    override fun canRename(): Boolean = false

    override fun canDelete(): Boolean = false

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
) : ArchiveExplorerItem(
    entryId = entryId,
    displayName = name,
    parentPage = parent,
    syntheticPath = path,
    modifiedTime = modifiedTime,
    uncompressedSize = size,
    compressedSize = -1L,
    canExtractEntry = canExtract,
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
) : ExplorerProvider {

    private val itemInfoById = LinkedHashMap<String, ExplorerArchiveItemInfo>()
    private val pageById = LinkedHashMap<String, ArchiveExplorerPage>()
    private val pageByPath = LinkedHashMap<String, ArchiveExplorerPage>()

    val rootPage = ArchiveExplorerPage(
        entryId = connection.info.rootId,
        parentId = null,
        name = connection.info.displayName,
        parent = null,
        path = syntheticPath(connection.info.rootId),
        modifiedTime = connection.info.sourceLastModified,
        size = connection.info.sourceSize,
        canExtract = true,
    ).also(::rememberPage)

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
                    canExtract = info.canExtract,
                ).also {
                    loaded.addChild(it)
                    rememberPage(it)
                }
            } else {
                loaded.addChild(ArchiveExplorerFileItem(info, parent, path))
            }
        }
        loaded
    }

    fun pageForPath(path: String): ArchiveExplorerPage? = pageByPath[path]

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

    fun close() = connection.close()

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
