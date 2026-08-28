package org.autojs.autojs.core.plugin.explorer.session

import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.autojs.runtime.api.Mime
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Read-only side of an Explorer Action host capability.
 *
 * Output transactions have a separate lifecycle in [ExplorerActionHostSession]. This component
 * keeps input path containment, directory paging and playback-history access together instead of
 * mixing them into that transaction state machine.
 */
internal class ExplorerActionHostInputAccess(
    commonParent: File,
    targets: List<ExplorerActionHostSessionTarget>,
    private val allowReadSiblings: Boolean,
    private val allowPlaybackProgress: Boolean,
    private val playbackHistoryStore: ExplorerPlaybackHistoryStore,
    private val requireSessionOpen: () -> Unit,
) {

    val parent: File = commonParent.canonicalFile

    val targetsById: Map<String, ExplorerActionHostTargetState> = targets.associate { target ->
        require(target.id.isNotBlank() && target.id.length <= ExplorerActionProtocol.MAX_TARGET_ID_LENGTH) {
            "Explorer Action host session target ID is invalid"
        }
        val file = target.file.canonicalFile
        target.id to ExplorerActionHostTargetState(file, ExplorerActionFileIdentity.capture(file))
    }

    init {
        require(parent.isDirectory) { "Explorer Action host session parent is not a directory" }
        require(targetsById.isNotEmpty() && targetsById.size == targets.size) {
            "Explorer Action host session targets are empty or duplicated"
        }
        require(targetsById.values.all { target -> target.file.parentFile?.canonicalFile == parent }) {
            "Explorer Action host session targets do not share the declared parent"
        }
    }

    fun listChildren(targetId: String, relativePath: String, offset: Int, limit: Int): Bundle {
        requireSessionOpen()
        require(offset >= 0) { "Explorer Action child offset is invalid" }
        require(limit in 1..ExplorerActionProtocol.MAX_SESSION_PAGE_SIZE) {
            "Explorer Action child page size is invalid"
        }
        val root = target(targetId)
        val directory = if (root.isFile) {
            check(allowReadSiblings) { "Explorer Action does not have direct-sibling access" }
            require(ExplorerActionInputPathPolicy.normalizeRelativePath(relativePath).isEmpty()) {
                "Explorer Action file target can list only its direct parent"
            }
            parent
        } else {
            ExplorerActionInputPathPolicy.resolve(root, relativePath)
        }
        require(directory.isDirectory) { "Explorer Action session path is not a directory" }
        val children = directory.listFiles()
            ?.sortedWith(compareBy<File>({ it.name.lowercase(Locale.ROOT) }, File::getName))
            ?: throw IOException("Explorer Action session cannot list the directory")
        require(offset <= children.size) { "Explorer Action child offset exceeds the directory size" }
        val page = children.drop(offset).take(limit)
        val items = ArrayList<Bundle>(page.size)
        page.forEach { child ->
            val childRelativePath = if (root.isFile) {
                ExplorerActionInputPathPolicy.normalizeRelativePath(child.name)
            } else {
                ExplorerActionInputPathPolicy.joinRelativePath(relativePath, child.name)
            }
            val metadataRoot = if (root.isFile) directory else root
            items += childMetadata(metadataRoot, child, childRelativePath)
        }
        val nextOffset = offset + page.size
        return Bundle().apply {
            putParcelableArrayList(ExplorerActionHostSessionKeys.ITEMS, items)
            putInt(ExplorerActionHostSessionKeys.NEXT_OFFSET, nextOffset)
            putBoolean(ExplorerActionHostSessionKeys.COMPLETE, nextOffset >= children.size)
        }
    }

    fun openFile(targetId: String, relativePath: String): ParcelFileDescriptor {
        requireSessionOpen()
        val file = resolveInput(targetId, relativePath)
        require(file.isFile && file.canRead()) { "Explorer Action session file is not readable" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    fun getPlaybackProgress(targetId: String, relativePath: String): Bundle {
        requireSessionOpen()
        check(allowPlaybackProgress) { "Explorer Action does not have playback-progress access" }
        val file = resolveInput(targetId, relativePath)
        require(file.isFile && file.canRead()) { "Explorer Action playback target is not readable" }
        val enabled = playbackHistoryStore.enabled
        val record = if (enabled) playbackHistoryStore.get(file) else null
        return Bundle().apply {
            putBoolean(ExplorerActionHostSessionKeys.PLAYBACK_HISTORY_ENABLED, enabled)
            putBoolean(ExplorerActionHostSessionKeys.PLAYBACK_PROGRESS_PRESENT, record != null)
            record?.let {
                putLong(ExplorerActionHostSessionKeys.PLAYBACK_POSITION_MILLIS, it.positionMillis)
                putLong(ExplorerActionHostSessionKeys.PLAYBACK_DURATION_MILLIS, it.durationMillis)
                putLong(ExplorerActionHostSessionKeys.PLAYBACK_UPDATED_AT_MILLIS, it.updatedAtMillis)
            }
        }
    }

    fun reportPlaybackProgress(
        targetId: String,
        relativePath: String,
        positionMillis: Long,
        durationMillis: Long,
        reportState: Int,
    ) {
        requireSessionOpen()
        check(allowPlaybackProgress) { "Explorer Action does not have playback-progress access" }
        val file = resolveInput(targetId, relativePath)
        require(file.isFile && file.canRead()) { "Explorer Action playback target is not readable" }
        val completed = when (reportState) {
            ExplorerActionHostSessionValues.PLAYBACK_REPORT_PROGRESS -> false
            ExplorerActionHostSessionValues.PLAYBACK_REPORT_COMPLETED -> true
            else -> throw IllegalArgumentException("Explorer Action playback report state is invalid")
        }
        playbackHistoryStore.report(file, positionMillis, durationMillis, completed)
    }

    fun targetState(targetId: String): ExplorerActionHostTargetState {
        return targetsById[targetId]
            ?: throw IllegalArgumentException("Explorer Action host session target is unknown")
    }

    fun requireCurrentIdentity(target: ExplorerActionHostTargetState, errorMessage: String) {
        check(ExplorerActionFileIdentity.capture(target.file) == target.identity) {
            errorMessage
        }
    }

    private fun target(targetId: String): File = targetState(targetId).file

    private fun resolveInput(targetId: String, rawRelativePath: String): File {
        val root = target(targetId)
        return ExplorerActionInputPathPolicy.resolveInput(
            parent = parent,
            root = root,
            rawRelativePath = rawRelativePath,
            allowReadSiblings = allowReadSiblings,
        )
    }

    private fun childMetadata(root: File, child: File, relativePath: String): Bundle {
        val absolute = child.absoluteFile
        val canonical = runCatching { absolute.canonicalFile }.getOrNull()
        val symbolicLink = canonical == null || absolute != canonical
        val withinRoot = canonical?.let { ExplorerActionInputPathPolicy.isWithinRoot(root, it) } == true
        val kind = if (child.isDirectory) {
            ExplorerActionValues.TARGET_DIRECTORY
        } else {
            ExplorerActionValues.TARGET_FILE
        }
        return Bundle().apply {
            putString(ExplorerActionHostSessionKeys.RELATIVE_PATH, relativePath)
            putString(
                ExplorerActionHostSessionKeys.DISPLAY_NAME,
                child.name.take(ExplorerActionProtocol.MAX_TARGET_DISPLAY_NAME_LENGTH),
            )
            putInt(ExplorerActionHostSessionKeys.KIND, kind)
            putString(
                ExplorerActionHostSessionKeys.MIME_TYPE,
                if (kind == ExplorerActionValues.TARGET_DIRECTORY) DIRECTORY_MIME_TYPE else Mime.fromFileOrWildcard(child.path),
            )
            putLong(ExplorerActionHostSessionKeys.SIZE, if (child.isFile) child.length() else SIZE_UNKNOWN)
            putLong(ExplorerActionHostSessionKeys.LAST_MODIFIED, child.lastModified())
            putBoolean(ExplorerActionHostSessionKeys.READABLE, child.canRead() && withinRoot && !symbolicLink)
            putBoolean(ExplorerActionHostSessionKeys.SYMBOLIC_LINK, symbolicLink)
        }
    }

    private companion object {
        private const val DIRECTORY_MIME_TYPE = "inode/directory"
        private const val SIZE_UNKNOWN = -1L
    }
}

/** Pure path policy shared by input operations and their unit tests. */
internal object ExplorerActionInputPathPolicy {

    fun resolve(root: File, rawRelativePath: String): File {
        val relativePath = normalizeRelativePath(rawRelativePath)
        if (relativePath.isEmpty()) return root
        require(root.isDirectory) { "Explorer Action file target cannot have a relative path" }
        val unresolved = File(root, relativePath.replace('/', File.separatorChar)).absoluteFile
        val resolved = unresolved.canonicalFile
        require(unresolved == resolved) { "Explorer Action symbolic-link traversal is not allowed" }
        require(isWithinRoot(root, resolved)) { "Explorer Action session path escapes its target" }
        return resolved
    }

    fun resolveInput(
        parent: File,
        root: File,
        rawRelativePath: String,
        allowReadSiblings: Boolean,
    ): File {
        val relativePath = normalizeRelativePath(rawRelativePath)
        if (root.isDirectory || relativePath.isEmpty()) return resolve(root, relativePath)
        check(allowReadSiblings) { "Explorer Action file target cannot open sibling files" }
        require('/' !in relativePath) { "Explorer Action sibling path must have one segment" }
        val unresolved = File(parent, relativePath).absoluteFile
        val resolved = unresolved.canonicalFile
        require(unresolved == resolved) { "Explorer Action symbolic-link traversal is not allowed" }
        require(resolved.parentFile?.canonicalFile == parent) {
            "Explorer Action sibling path escapes the direct parent"
        }
        return resolved
    }

    fun normalizeRelativePath(value: String): String {
        require(value.length <= ExplorerActionProtocol.MAX_SESSION_RELATIVE_PATH_LENGTH) {
            "Explorer Action relative path is too long"
        }
        if (value.isEmpty()) return value
        require(!value.startsWith('/') && !value.endsWith('/')) {
            "Explorer Action relative path is not relative"
        }
        val segments = value.split('/')
        require(segments.none { it.isEmpty() || it == "." || it == ".." || '\u0000' in it }) {
            "Explorer Action relative path contains an unsafe segment"
        }
        return value
    }

    fun joinRelativePath(parentPath: String, childName: String): String {
        val normalizedParent = normalizeRelativePath(parentPath)
        return normalizeRelativePath(if (normalizedParent.isEmpty()) childName else "$normalizedParent/$childName")
    }

    fun isWithinRoot(root: File, candidate: File): Boolean {
        return candidate == root || candidate.path.startsWith(root.path + File.separator)
    }
}
