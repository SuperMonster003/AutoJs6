package org.autojs.autojs.core.plugin.explorer.archive

import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveInputKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveInputValues
import org.autojs.plugin.explorer.api.IExplorerArchiveInputSession
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

/**
 * One frozen, operation-scoped view of files explicitly selected in the host Explorer.
 *
 * The remote side receives only grant-scoped IDs and leaf names. Host paths remain private, every
 * regular file is opened at most once, and publication is gated by a final full-tree identity
 * check after all files have been requested.
 */
internal class ExplorerArchiveInputSession private constructor(
    private val expectedUid: Int,
    private val archiveSessionId: String,
    private val operationId: String,
    selectedRoots: List<File>,
    private val checkCancelled: () -> Unit,
) : IExplorerArchiveInputSession.Stub() {

    private val lock = Any()
    private val grantId = UUID.randomUUID().toString()
    private val virtualRootId = UUID.randomUUID().toString()
    private val manifest = freezeSelection(selectedRoots)
    private val openedFileIds = LinkedHashSet<String>()
    private var closed = false

    internal val nodeCount: Int
        get() = manifest.nodesById.size

    override fun getInfo(): Bundle {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            Bundle().apply {
                putString(ExplorerArchiveInputKeys.GRANT_ID, grantId)
                putString(ExplorerArchiveInputKeys.ARCHIVE_SESSION_ID, archiveSessionId)
                putString(ExplorerArchiveInputKeys.OPERATION_ID, operationId)
                putString(ExplorerArchiveInputKeys.VIRTUAL_ROOT_ID, virtualRootId)
                putInt(ExplorerArchiveInputKeys.ROOT_COUNT, manifest.rootIds.size)
                putInt(ExplorerArchiveInputKeys.NODE_COUNT, manifest.nodesById.size)
                putInt(ExplorerArchiveInputKeys.FILE_COUNT, manifest.fileCount)
                putInt(ExplorerArchiveInputKeys.DIRECTORY_COUNT, manifest.directoryCount)
                putLong(ExplorerArchiveInputKeys.KNOWN_TOTAL_BYTES, manifest.knownTotalBytes)
            }
        }
    }

    override fun listChildren(parentId: String, offset: Int, limit: Int): Bundle {
        enforceCaller()
        require(parentId.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_INPUT_NODE_ID_LENGTH) {
            "Archive input parent ID is invalid"
        }
        require(offset >= 0) { "Archive input page offset is invalid" }
        require(limit in 1..ExplorerActionProtocol.MAX_ARCHIVE_INPUT_PAGE_SIZE) {
            "Archive input page size is invalid"
        }
        return synchronized(lock) {
            checkOpen()
            val children = requireNotNull(manifest.childrenByParentId[parentId]) {
                "Archive input directory does not exist"
            }
            require(offset <= children.size) { "Archive input page offset is out of range" }
            val end = (offset.toLong() + limit).coerceAtMost(children.size.toLong()).toInt()
            Bundle().apply {
                putParcelableArrayList(
                    ExplorerArchiveInputKeys.ITEMS,
                    ArrayList(children.subList(offset, end).map { id ->
                        manifest.nodesById.getValue(id).toBundle()
                    }),
                )
                putInt(ExplorerArchiveInputKeys.NEXT_OFFSET, end)
                putBoolean(ExplorerArchiveInputKeys.COMPLETE, end == children.size)
            }
        }
    }

    override fun openFile(itemId: String): ParcelFileDescriptor {
        enforceCaller()
        require(itemId.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_INPUT_NODE_ID_LENGTH) {
            "Archive input file ID is invalid"
        }
        return synchronized(lock) {
            checkOpen()
            val node = requireNotNull(manifest.nodesById[itemId]) {
                "Archive input file does not exist"
            }
            require(node.kind == ExplorerArchiveInputValues.KIND_FILE) {
                "Archive input node is not a regular file"
            }
            check(itemId !in openedFileIds) { "Archive input file was already opened" }
            requireFrozenParentIdentity(node)
            val before = lstat(node.file)
            check(node.identity.matches(before, node.file.lastModified())) {
                "Archive input file changed before open"
            }
            val descriptor = try {
                Os.open(
                    node.file.path,
                    OsConstants.O_RDONLY or O_CLOEXEC_LINUX or OsConstants.O_NOFOLLOW,
                    0,
                )
            } catch (error: ErrnoException) {
                throw IOException("Archive input file could not be opened", error)
            }
            try {
                val opened = Os.fstat(descriptor)
                check(node.identity.matches(opened, node.file.lastModified())) {
                    "Archive input file changed during open"
                }
                requireFrozenParentIdentity(node)
                ParcelFileDescriptor.dup(descriptor).also { openedFileIds += itemId }
            } finally {
                runCatching { Os.close(descriptor) }
            }
        }
    }

    override fun verifySnapshot(): Bundle {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            check(openedFileIds.size == manifest.fileCount) {
                "Archive input snapshot was verified before every file was opened"
            }
            Bundle().apply {
                putInt(
                    ExplorerArchiveInputKeys.VERIFICATION_STATUS,
                    if (snapshotMatches()) {
                        ExplorerArchiveInputValues.VERIFICATION_VALID
                    } else {
                        ExplorerArchiveInputValues.VERIFICATION_SOURCE_CHANGED
                    },
                )
            }
        }
    }

    override fun close() {
        enforceCaller()
        closeInternal()
    }

    internal fun closeFromHost() {
        closeInternal()
    }

    private fun closeInternal() {
        synchronized(lock) {
            if (closed) return
            closed = true
            openedFileIds.clear()
        }
    }

    private fun snapshotMatches(): Boolean = runCatching {
        manifest.nodesById.values.forEach { node ->
            check(node.identity.matches(lstat(node.file), node.file.lastModified()))
        }
        manifest.childrenByParentId.forEach { (parentId, expectedIds) ->
            if (parentId == virtualRootId) return@forEach
            val parent = manifest.nodesById.getValue(parentId)
            check(parent.kind == ExplorerArchiveInputValues.KIND_DIRECTORY)
            val actual = listDirectoryChildren(parent.file)
            val expectedNames = expectedIds.map { id -> manifest.nodesById.getValue(id).name }
            check(actual.map(File::getName) == expectedNames)
        }
        true
    }.getOrDefault(false)

    private fun freezeSelection(selectedRoots: List<File>): FrozenManifest {
        require(selectedRoots.size in 1..ExplorerActionProtocol.MAX_ARCHIVE_INPUT_ROOTS) {
            "Archive input root count is invalid"
        }
        val roots = selectedRoots.map(::validatedRoot)
        checkCancelled()
        require(roots.distinctBy(File::getPath).size == roots.size) {
            "Archive input selection contains duplicate roots"
        }
        roots.forEachIndexed { index, root ->
            roots.drop(index + 1).forEach { other ->
                require(!isAtOrBelow(root, other) && !isAtOrBelow(other, root)) {
                    "Archive input selection contains overlapping roots"
                }
            }
        }
        val nodesById = LinkedHashMap<String, FrozenNode>()
        val childrenByParentId = LinkedHashMap<String, List<String>>()
        val pending = ArrayDeque<PendingNode>()
        var cumulativeNameBytes = 0L
        var scheduledNodes = 0

        fun requireFrozenParentIdentity(parentId: String) {
            if (parentId == virtualRootId) return
            val parent = requireNotNull(nodesById[parentId]) {
                "Archive input parent was not frozen before its child"
            }
            check(parent.kind == ExplorerArchiveInputValues.KIND_DIRECTORY) {
                "Archive input parent is not a directory"
            }
            check(parent.identity.matches(lstat(parent.file), parent.file.lastModified())) {
                "Archive input parent changed while the tree was scanned"
            }
        }

        fun schedule(file: File, parentId: String, depth: Int): String {
            require(depth in 1..ExplorerActionProtocol.MAX_ARCHIVE_INPUT_DEPTH) {
                "Archive input tree exceeds the depth limit"
            }
            check(scheduledNodes < ExplorerActionProtocol.MAX_ARCHIVE_INPUT_NODES) {
                "Archive input tree exceeds the node limit"
            }
            val name = requireValidName(file.name)
            cumulativeNameBytes = Math.addExact(
                cumulativeNameBytes,
                name.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            )
            require(cumulativeNameBytes <= ExplorerActionProtocol.MAX_ARCHIVE_INPUT_NAME_BYTES) {
                "Archive input names exceed the text limit"
            }
            scheduledNodes += 1
            return UUID.randomUUID().toString().also { id ->
                pending += PendingNode(id, parentId, file, name, depth)
            }
        }

        val rootIds = roots.map { root -> schedule(root, virtualRootId, 1) }
        childrenByParentId[virtualRootId] = rootIds
        var fileCount = 0
        var directoryCount = 0
        var knownTotalBytes = 0L

        while (pending.isNotEmpty()) {
            checkCancelled()
            val next = pending.removeFirst()
            requireFrozenParentIdentity(next.parentId)
            val stat = lstat(next.file)
            require(!OsConstants.S_ISLNK(stat.st_mode)) {
                "Archive input contains a symbolic link"
            }
            val kind = when {
                OsConstants.S_ISREG(stat.st_mode) -> ExplorerArchiveInputValues.KIND_FILE
                OsConstants.S_ISDIR(stat.st_mode) -> ExplorerArchiveInputValues.KIND_DIRECTORY
                else -> throw IOException("Archive input contains a special file")
            }
            require(next.file.canRead()) { "Archive input contains an unreadable node" }
            val identity = FrozenIdentity.capture(stat, next.file.lastModified())
            val size = if (kind == ExplorerArchiveInputValues.KIND_FILE) stat.st_size else -1L
            val node = FrozenNode(
                id = next.id,
                parentId = next.parentId,
                name = next.name,
                kind = kind,
                size = size,
                lastModified = identity.modifiedMillis,
                file = next.file,
                identity = identity,
            )
            check(nodesById.put(node.id, node) == null)
            if (kind == ExplorerArchiveInputValues.KIND_FILE) {
                fileCount += 1
                knownTotalBytes = Math.addExact(knownTotalBytes, size)
            } else {
                directoryCount += 1
                val children = listDirectoryChildren(next.file)
                val childIds = children.map { child ->
                    schedule(child, node.id, next.depth + 1)
                }
                childrenByParentId[node.id] = childIds
                check(identity.matches(lstat(next.file), next.file.lastModified())) {
                    "Archive input directory changed while it was scanned"
                }
                requireFrozenParentIdentity(node.parentId)
            }
        }
        check(nodesById.size == scheduledNodes)
        return FrozenManifest(
            rootIds = rootIds,
            nodesById = nodesById.toMap(),
            childrenByParentId = childrenByParentId.toMap(),
            fileCount = fileCount,
            directoryCount = directoryCount,
            knownTotalBytes = knownTotalBytes,
        )
    }

    private fun validatedRoot(selected: File): File {
        val absolute = selected.absoluteFile
        val stat = lstat(absolute)
        require(!OsConstants.S_ISLNK(stat.st_mode)) { "Archive input root is a symbolic link" }
        requireNoUntrustedSymbolicLinkAncestors(absolute)
        val canonical = try {
            absolute.canonicalFile
        } catch (error: IOException) {
            throw IOException("Archive input root cannot be resolved", error)
        }
        return canonical
    }

    private fun requireNoUntrustedSymbolicLinkAncestors(file: File) {
        var ancestor = file.parentFile
        while (ancestor != null) {
            val stat = lstat(ancestor)
            require(!OsConstants.S_ISLNK(stat.st_mode) || stat.st_uid == ROOT_UID) {
                "Archive input root traverses a symbolic link"
            }
            ancestor = ancestor.parentFile
        }
    }

    private fun requireFrozenParentIdentity(node: FrozenNode) {
        if (node.parentId == virtualRootId) return
        val parent = requireNotNull(manifest.nodesById[node.parentId]) {
            "Archive input parent does not exist"
        }
        check(parent.kind == ExplorerArchiveInputValues.KIND_DIRECTORY) {
            "Archive input parent is not a directory"
        }
        check(parent.identity.matches(lstat(parent.file), parent.file.lastModified())) {
            "Archive input parent changed before file open"
        }
    }

    private fun listDirectoryChildren(directory: File): List<File> {
        val children = directory.listFiles()
            ?: throw IOException("Archive input directory cannot be listed")
        val result = children.sortedWith(
            compareBy<File>({ collisionKey(it.name) }, File::getName),
        )
        requireUnambiguousNames(result)
        return result
    }

    private fun requireUnambiguousNames(files: List<File>) {
        require(files.map { file -> collisionKey(requireValidName(file.name)) }.distinct().size == files.size) {
            "Archive input contains ambiguous sibling names"
        }
    }

    private fun requireValidName(value: String): String {
        require(value.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_DISPLAY_NAME_LENGTH) {
            "Archive input contains an invalid name"
        }
        require(value != "." && value != ".." && value.isNotBlank()) {
            "Archive input contains an invalid name"
        }
        require(value.none { character ->
            character == '/' ||
                character == '\\' ||
                character == '\u0000' ||
                character.code < 0x20 ||
                character.code == 0x7F
        }) { "Archive input contains an invalid name" }
        return value
    }

    private fun collisionKey(value: String): String = Normalizer.normalize(
        value,
        Normalizer.Form.NFC,
    ).lowercase(Locale.ROOT)

    private fun lstat(file: File): StructStat = try {
        Os.lstat(file.path)
    } catch (error: ErrnoException) {
        throw IOException("Archive input identity is unavailable", error)
    }

    private fun isAtOrBelow(root: File, candidate: File): Boolean =
        candidate == root || candidate.path.startsWith(root.path + File.separator)

    private fun enforceCaller() {
        if (Binder.getCallingUid() != expectedUid) {
            throw SecurityException("Archive input session caller UID does not match the archive plugin")
        }
    }

    private fun checkOpen() {
        check(!closed) { "Archive input session is closed" }
    }

    private data class FrozenManifest(
        val rootIds: List<String>,
        val nodesById: Map<String, FrozenNode>,
        val childrenByParentId: Map<String, List<String>>,
        val fileCount: Int,
        val directoryCount: Int,
        val knownTotalBytes: Long,
    )

    private data class FrozenNode(
        val id: String,
        val parentId: String,
        val name: String,
        val kind: Int,
        val size: Long,
        val lastModified: Long,
        val file: File,
        val identity: FrozenIdentity,
    ) {
        fun toBundle() = Bundle().apply {
            putString(ExplorerArchiveInputKeys.ID, id)
            putString(ExplorerArchiveInputKeys.PARENT_ID, parentId)
            putString(ExplorerArchiveInputKeys.NAME, name)
            putInt(ExplorerArchiveInputKeys.KIND, kind)
            putLong(ExplorerArchiveInputKeys.SIZE, size)
            putLong(ExplorerArchiveInputKeys.LAST_MODIFIED, lastModified)
        }
    }

    private data class FrozenIdentity(
        val device: Long,
        val inode: Long,
        val kind: Int,
        val size: Long,
        val modifiedMillis: Long,
        val modifiedSeconds: Long,
        val modifiedNanoseconds: Long,
        val changedSeconds: Long,
        val changedNanoseconds: Long,
    ) {
        fun matches(stat: StructStat, currentModifiedMillis: Long): Boolean =
            device == stat.st_dev &&
                inode == stat.st_ino &&
                kind == kindOf(stat) &&
                size == stat.st_size &&
                modifiedMillis == currentModifiedMillis &&
                modifiedSeconds == stat.st_mtime &&
                modifiedNanoseconds == modifiedNanosecondsOf(stat) &&
                changedSeconds == stat.st_ctime &&
                changedNanoseconds == changedNanosecondsOf(stat)

        companion object {
            fun capture(stat: StructStat, modifiedMillis: Long) = FrozenIdentity(
                device = stat.st_dev,
                inode = stat.st_ino,
                kind = kindOf(stat),
                size = stat.st_size,
                modifiedMillis = modifiedMillis,
                modifiedSeconds = stat.st_mtime,
                modifiedNanoseconds = modifiedNanosecondsOf(stat),
                changedSeconds = stat.st_ctime,
                changedNanoseconds = changedNanosecondsOf(stat),
            )

            private fun modifiedNanosecondsOf(stat: StructStat): Long =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    stat.st_mtim.tv_nsec
                } else {
                    NANOSECONDS_UNAVAILABLE
                }

            private fun changedNanosecondsOf(stat: StructStat): Long =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    stat.st_ctim.tv_nsec
                } else {
                    NANOSECONDS_UNAVAILABLE
                }

            private fun kindOf(stat: StructStat): Int = when {
                OsConstants.S_ISREG(stat.st_mode) -> ExplorerArchiveInputValues.KIND_FILE
                OsConstants.S_ISDIR(stat.st_mode) -> ExplorerArchiveInputValues.KIND_DIRECTORY
                else -> 0
            }

            private const val NANOSECONDS_UNAVAILABLE = -1L
        }
    }

    private data class PendingNode(
        val id: String,
        val parentId: String,
        val file: File,
        val name: String,
        val depth: Int,
    )

    companion object {

        private const val ROOT_UID = 0
        // Linux UAPI O_CLOEXEC. Android did not expose OsConstants.O_CLOEXEC until API 27.
        private const val O_CLOEXEC_LINUX = 0x80000

        fun create(
            expectedUid: Int,
            archiveSessionId: String,
            operationId: String,
            selectedRoots: List<File>,
            checkCancelled: () -> Unit = {},
        ): ExplorerArchiveInputSession {
            require(archiveSessionId.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_SESSION_ID_LENGTH) {
                "Archive session ID is invalid"
            }
            require(runCatching { UUID.fromString(operationId) }.getOrNull()?.toString() == operationId) {
                "Archive input operation ID is invalid"
            }
            return ExplorerArchiveInputSession(
                expectedUid = expectedUid,
                archiveSessionId = archiveSessionId,
                operationId = operationId,
                selectedRoots = selectedRoots,
                checkCancelled = checkCancelled,
            )
        }
    }
}
