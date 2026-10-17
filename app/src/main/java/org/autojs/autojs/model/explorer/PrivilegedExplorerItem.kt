package org.autojs.autojs.model.explorer

import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.util.FileUtils

/** Items whose paths are accessible through an authorized reader, not through the app's File API. */
interface PrivilegedExplorerItem : ExplorerItem {
    val accessMode: PrivilegedAccessMode
}

abstract class PrivilegedExplorerEntryItem internal constructor(
    private val entry: PrivilegedExplorerEntry,
    private val absolutePath: String,
    private val parentPage: ExplorerPage?,
    final override val accessMode: PrivilegedAccessMode,
) : PrivilegedExplorerItem {
    private val cachedType by lazy { if (entry.directory) FileUtils.TYPE.DIRECTORY else typeFromName(entry.name) }

    final override fun getName() = entry.name
    final override fun getPath() = absolutePath
    final override fun getParent() = parentPage
    final override fun lastModified() = entry.modified
    final override fun getSize() = entry.size
    final override fun getType() = cachedType
    final override fun canCopy() = false
    final override fun canMove() = false
    final override fun canRename() = false
    final override fun canDelete() = false
    final override fun canBuildApk() = false
    final override fun canSetAsWorkingDir() = false
    final override fun isExecutable() = false
    final override fun isInstallable() = false
    final override fun isTextEditable() = false
    final override fun isExternalEditable() = false
    final override fun isImageViewable() = false
    final override fun isAudioPlayable() = false
    final override fun isVideoPlayable() = false
    final override fun isMediaPlayable() = false
    final override fun isMediaMenu() = false
    final override fun isPreviewable() = false
    final override fun toScriptFile() = ScriptFile(absolutePath)

    final override fun equals(other: Any?): Boolean =
        this === other || other is PrivilegedExplorerItem && path == other.path && accessMode == other.accessMode

    final override fun hashCode() = 31 * absolutePath.hashCode() + accessMode.hashCode()

    private fun typeFromName(name: String): FileUtils.TYPE = FileUtils.TYPE.entries
        .filter { type ->
            val extension = type.extension
            extension.isNotEmpty() && type != FileUtils.TYPE.DIRECTORY && when {
                extension.startsWith(FileUtils.TYPE.TYPE_NAME_PREFIX_REGEX) -> name.contains(
                    Regex("\\b(${extension.removePrefix(FileUtils.TYPE.TYPE_NAME_PREFIX_REGEX)})$", RegexOption.IGNORE_CASE),
                )
                else -> name.contains(Regex("\\b(${Regex.escape(extension)})$", RegexOption.IGNORE_CASE))
            }
        }
        .maxWithOrNull(compareBy<FileUtils.TYPE> { it.extension.length }.thenBy { it.priority })
        ?: FileUtils.TYPE.UNKNOWN
}

class PrivilegedExplorerFileItem internal constructor(
    entry: PrivilegedExplorerEntry,
    path: String,
    parent: ExplorerPage,
    mode: PrivilegedAccessMode,
) : PrivilegedExplorerEntryItem(entry, path, parent, mode)

class PrivilegedExplorerPage internal constructor(
    entry: PrivilegedExplorerEntry,
    path: String,
    parent: ExplorerPage?,
    mode: PrivilegedAccessMode,
) : PrivilegedExplorerEntryItem(entry, path, parent, mode), ExplorerPage {
    private val children = ArrayList<ExplorerItem>()

    override fun copyChildren(page: ExplorerPage) {
        if (page === this) return
        require(page is PrivilegedExplorerPage && page.accessMode == accessMode) { "Privileged directory access mode changed" }
        children.clear()
        children.addAll(page.children)
    }

    override fun addChild(item: ExplorerItem) { children += item }
    override fun removeChild(item: ExplorerItem) { children.remove(item) }
    override fun updateChild(oldItem: ExplorerItem, newItem: ExplorerItem) {
        val index = children.indexOf(oldItem)
        if (index >= 0) children[index] = newItem
    }
    override fun iterator(): MutableIterator<ExplorerItem> = children.iterator()
}
