package org.autojs.autojs.core.plugin.explorer.session

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.autojs.autojs.runtime.api.Mime
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

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
    relatedFileSuffixes: Set<String>,
    private val playbackHistoryStore: ExplorerPlaybackHistoryStore,
    private val requireSessionOpen: () -> Unit,
    /** Remember what listChildren returned below directory targets (protocol v25 `targetRename`). */
    private val recordListings: Boolean = false,
) {

    /** A descendant as listChildren returned it. zh-CN: listChildren 返回时的后代条目. */
    data class ListedEntry(val directory: Boolean, val lastModified: Long, val length: Long)

    private val listedEntries = HashMap<String, HashMap<String, ListedEntry>>()
    private var listedEntryCount = 0

    val parent: File = commonParent.canonicalFile

    val targetsById: Map<String, ExplorerActionHostTargetState> = targets.associate { target ->
        require(target.id.isNotBlank() && target.id.length <= ExplorerActionProtocol.MAX_TARGET_ID_LENGTH) {
            "Explorer Action host session target ID is invalid"
        }
        val file = target.file.canonicalFile
        target.id to ExplorerActionHostTargetState(file, ExplorerActionFileIdentity.capture(file))
    }
    private val allowedRelatedFileSuffixes = relatedFileSuffixes.toSet()
    private val relatedFileOpenRequests = AtomicInteger(0)
    private val relatedFiles = buildMap {
        targetsById.forEach { (targetId, target) ->
            allowedRelatedFileSuffixes.forEach { suffix ->
                val displayName = ExplorerActionRelatedFilePolicy.candidateName(
                    target.file.name,
                    suffix,
                )
                captureRelatedFile(parent, displayName)?.let { candidate ->
                    put(RelatedFileKey(targetId, suffix), candidate)
                }
            }
        }
    }

    init {
        require(parent.isDirectory) { "Explorer Action host session parent is not a directory" }
        require(targetsById.isNotEmpty() && targetsById.size == targets.size) {
            "Explorer Action host session targets are empty or duplicated"
        }
        require(targetsById.values.all { target -> target.file.parentFile?.canonicalFile == parent }) {
            "Explorer Action host session targets do not share the declared parent"
        }
        require(
            allowedRelatedFileSuffixes.size <= ExplorerActionProtocol.MAX_RELATED_FILE_SUFFIXES &&
                allowedRelatedFileSuffixes.all(ExplorerActionRelatedFilePolicy::isValidSuffix),
        ) {
            "Explorer Action related-file suffixes are invalid"
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
            if (recordListings && root.isDirectory) recordListing(targetId, childRelativePath, child)
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

    fun openRelatedFile(targetId: String, suffix: String): ParcelFileDescriptor? {
        requireSessionOpen()
        targetState(targetId)
        require(suffix in allowedRelatedFileSuffixes) {
            "Explorer Action related-file suffix is not part of this session"
        }
        check(
            relatedFileOpenRequests.incrementAndGet() <=
                ExplorerActionProtocol.MAX_RELATED_FILE_OPEN_REQUESTS,
        ) {
            "Explorer Action related-file open request limit was exceeded"
        }
        val candidate = relatedFiles[RelatedFileKey(targetId, suffix)] ?: return null
        candidate.requireUnchanged(parent)
        val descriptor = ParcelFileDescriptor.open(
            candidate.file,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        return try {
            check(candidate.identity.matchesDescriptor(descriptor.fileDescriptor)) {
                "Explorer Action related file changed while it was being opened"
            }
            descriptor
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
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

    /**
     * What listChildren returned for [relativePath] below the directory target [targetId], or null when this session
     * never listed it.
     * zh-CN: listChildren 为目录目标 [targetId] 下的 [relativePath] 返回的内容; 本会话从未列出时为 null.
     */
    fun listedEntry(targetId: String, relativePath: String): ListedEntry? = synchronized(listedEntries) {
        listedEntries[targetId]?.get(relativePath)
    }

    /**
     * Follows renames: [relocate] maps an absolute path as it was before to where it is now. Targets adopt their new
     * paths and identities, and listing records move with their entries; a record whose entry left its target is
     * dropped, and where two records meet the moved one wins (it replaced the other).
     * zh-CN: 跟随改名: [relocate] 把此前的绝对路径映射到当前位置. 目标采用新路径与新身份, 列出记录随条目移动; 条目离开所属目标的
     * 记录被丢弃, 两条记录重合时保留移动过来的一条 (它替换了另一条).
     */
    fun followRenames(relocate: (String) -> String) = synchronized(listedEntries) {
        targetsById.forEach { (targetId, target) ->
            val oldRoot = target.file
            val newRoot = File(relocate(oldRoot.path))
            if (newRoot != oldRoot) {
                target.file = newRoot
                runCatching { ExplorerActionFileIdentity.capture(newRoot) }.onSuccess { target.identity = it }
            }
            val entries = listedEntries[targetId] ?: return@forEach
            val prefix = newRoot.path + File.separator
            val moved = HashMap<String, ListedEntry>(entries.size)
            entries.forEach { (relativePath, entry) ->
                val before = oldRoot.path + File.separator + relativePath.replace('/', File.separatorChar)
                val now = relocate(before)
                if (!now.startsWith(prefix)) return@forEach
                val key = now.removePrefix(prefix).replace(File.separatorChar, '/')
                if (key == relativePath) moved.putIfAbsent(key, entry) else moved[key] = entry
            }
            listedEntries[targetId] = moved
        }
        listedEntryCount = listedEntries.values.sumOf { it.size }
    }

    private fun recordListing(targetId: String, relativePath: String, child: File) = synchronized(listedEntries) {
        val entries = listedEntries.getOrPut(targetId) { HashMap() }
        if (relativePath !in entries && listedEntryCount >= MAX_RECORDED_LISTINGS) return@synchronized
        if (entries.put(relativePath, ListedEntry(child.isDirectory, child.lastModified(), if (child.isDirectory) -1L else child.length())) == null) {
            listedEntryCount++
        }
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

        /** Listed descendants a session remembers for renames; later ones cannot be renamed. */
        private const val MAX_RECORDED_LISTINGS = 50_000

        private fun captureRelatedFile(parent: File, displayName: String): RelatedFileCandidate? {
            val unresolved = File(parent, displayName).absoluteFile
            val linkStat = try {
                Os.lstat(unresolved.path)
            } catch (_: ErrnoException) {
                return null
            }
            if (!OsConstants.S_ISREG(linkStat.st_mode) || OsConstants.S_ISLNK(linkStat.st_mode)) {
                return null
            }
            val candidate = try {
                unresolved.canonicalFile
            } catch (_: IOException) {
                return null
            }
            if (
                candidate != unresolved ||
                candidate.parentFile != parent ||
                !candidate.isFile ||
                !candidate.canRead()
            ) {
                return null
            }
            val identity = try {
                RelatedFileIdentity.capture(candidate)
            } catch (_: IOException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
            return RelatedFileCandidate(candidate, identity)
        }
    }

    private data class RelatedFileKey(val targetId: String, val suffix: String)

    private data class RelatedFileCandidate(
        val file: File,
        val identity: RelatedFileIdentity,
    ) {
        fun requireUnchanged(parent: File) {
            check(captureRelatedFile(parent, file.name) == this) {
                "Explorer Action related file changed after the session was opened"
            }
        }
    }

    private data class RelatedFileIdentity(
        val device: Long,
        val inode: Long,
        val size: Long,
        val modifiedSeconds: Long,
        val lastModifiedMillis: Long,
    ) {
        fun matchesDescriptor(descriptor: FileDescriptor): Boolean {
            val stat = try {
                Os.fstat(descriptor)
            } catch (error: ErrnoException) {
                throw IOException("Explorer Action related-file descriptor cannot be inspected", error)
            }
            return OsConstants.S_ISREG(stat.st_mode) &&
                stat.st_dev == device &&
                stat.st_ino == inode &&
                stat.st_size == size &&
                stat.st_mtime == modifiedSeconds
        }

        companion object {
            fun capture(file: File): RelatedFileIdentity {
                val stat = try {
                    Os.stat(file.path)
                } catch (error: ErrnoException) {
                    throw IOException("Explorer Action related-file identity is unavailable", error)
                }
                require(OsConstants.S_ISREG(stat.st_mode)) {
                    "Explorer Action related file is not a regular file"
                }
                return RelatedFileIdentity(
                    device = stat.st_dev,
                    inode = stat.st_ino,
                    size = stat.st_size,
                    modifiedSeconds = stat.st_mtime,
                    lastModifiedMillis = file.lastModified(),
                )
            }
        }
    }
}

/** Pure declaration policy for exact host-derived related-file names. */
internal object ExplorerActionRelatedFilePolicy {
    private val suffixPattern = Regex(
        "\\.[a-z0-9][a-z0-9+_-]*(?:\\.[a-z0-9][a-z0-9+_-]*)*",
    )

    fun isValidSuffix(suffix: String): Boolean =
        suffix.length in 2..ExplorerActionProtocol.MAX_RELATED_FILE_SUFFIX_LENGTH &&
            suffixPattern.matches(suffix)

    fun candidateName(targetDisplayName: String, suffix: String): String {
        require(isValidSuffix(suffix)) { "Explorer Action related-file suffix is invalid" }
        return targetDisplayName + suffix
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
