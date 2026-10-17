package org.autojs.autojs.model.explorer

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * The same filesystem implementation runs inside the authorized Shizuku or Root process.
 * All paths are JSON strings, never shell programs. Callers must obtain an identity with stat
 * and include it as `expected` before a destructive operation. `deadlineMillis` is an absolute
 * wall-clock deadline; the engine converts it to a monotonic deadline when the request starts.
 *
 * Completed files are first staged beside the destination. link(2) publishes files without
 * replacement. On filesystems without hard links, O_EXCL reserves the destination before it is
 * written. Directories are reserved with mkdir and populated without replacement. Consequently
 * an entire directory (or a fallback file copy) is not atomic: failure reports partial=true and
 * the surviving destination/staging paths. A completed destination is never rolled back or
 * overwritten. Same-volume moves use native renameat2(RENAME_NOREPLACE), retaining ownership
 * and security labels. Cross-volume moves are limited to shared storage, whose permissions are
 * managed by Android, and never remove the source until the entire copy is verified.
 */
object PrivilegedFileEngine {
    private const val MAX_ENTRIES = 20_000
    private const val MAX_DEPTH = 128
    private const val MAX_REQUEST_CHARS = 32 * 1024
    private const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    private const val MAX_TIMEOUT_MILLIS = 15 * 60_000L
    private const val OWNER_FILE_MODE = 0x180 // 0600
    private const val OWNER_DIRECTORY_MODE = 0x1c0 // 0700
    private const val O_CLOEXEC_LINUX = 0x80000

    fun execute(requestJson: String): String {
        var operation: Operation? = null
        return try {
            val request = parseRequest(requestJson)
            operation = Operation(request)
            operation.run().toString()
        } catch (error: Exception) {
            JSONObject().put("ok", false)
                .put("code", errorCode(error))
                .put("message", error.message.orEmpty().take(4096))
                .put("partial", operation?.partial == true)
                .put("recoveryPaths", JSONArray(operation?.recoveryPaths?.toList().orEmpty()))
                .toString()
        }
    }

    /** Returns a read-only descriptor for a regular file, never for a symlink or special node. */
    @Throws(IOException::class)
    fun openReadDescriptor(requestJson: String): ParcelFileDescriptor {
        val request = parseRequest(requestJson)
        val operation = Operation(request)
        operation.checkDeadline()
        Target.open(checkedPath(request.getString("source"))).use { source ->
            val before = source.identity()
            requireExpected(request, before, required = false)
            failUnless(before.kind == Kind.FILE, "UNSUPPORTED_TYPE", "Only regular files can be opened")
            val descriptor = openRegular(source.path, before)
            try {
                operation.checkDeadline()
                source.checkParent()
                requireUnchanged(before, source.identity())
                return ParcelFileDescriptor.dup(descriptor)
            } finally {
                Os.close(descriptor)
            }
        }
    }

    private class Operation(private val request: JSONObject) {
        var partial = false
        val recoveryPaths = LinkedHashSet<String>()
        private val deadline: Long
        private var entries = 0
        private var copiedBytes = 0L

        init {
            val now = System.currentTimeMillis()
            val requested = request.optLong("deadlineMillis", now + DEFAULT_TIMEOUT_MILLIS)
            val remaining = if (requested <= now) 0L else (requested - now).coerceAtMost(MAX_TIMEOUT_MILLIS)
            deadline = System.nanoTime() + remaining * 1_000_000L
        }

        fun checkDeadline() {
            failUnless(!Thread.currentThread().isInterrupted, "CANCELLED", "File operation was cancelled")
            failUnless(System.nanoTime() < deadline, "TIMEOUT", "File operation timed out")
        }

        fun run(): JSONObject {
            checkDeadline()
            val operation = request.getString("operation")
            failUnless(operation in setOf("stat", "copy", "move", "rename", "delete", "export"), "INVALID_REQUEST", "Unknown file operation")
            Target.open(checkedPath(request.getString("source"))).use { source ->
                val identity = source.identity()
                requireExpected(request, identity, required = operation in setOf("move", "rename", "delete"))
                if (operation == "stat") return success().put("path", source.actualPath)
                    .put("kind", identity.kind.wireName).put("size", identity.size)
                    .put("modified", identity.modified).put("identity", identity.json())
                failUnless(identity.kind != Kind.SPECIAL, "UNSUPPORTED_TYPE", "Device, FIFO, and socket entries cannot be operated on")
                if (operation in setOf("move", "rename", "delete")) {
                    failUnless(identity.kind != Kind.DIRECTORY || identity.dev == source.parent.identity.dev,
                        "INVALID_PATH", "The root of a mounted filesystem cannot be moved or deleted")
                }
                if (operation == "export") {
                    failUnless(identity.kind == Kind.FILE, "UNSUPPORTED_TYPE", "Only regular files can be exported")
                }
                if (operation == "delete") {
                    val tree = scan(source.path, identity, 0)
                    verify(source.path, tree, hashes = false)
                    removeSource(source, tree)
                    return success().put("source", source.actualPath)
                }
                val requestedDestination = checkedPath(request.getString("destination"))
                Target.open(requestedDestination).use { destination ->
                    failUnless(destination.actualPath != source.actualPath &&
                        (identity.kind != Kind.DIRECTORY || !destination.actualPath.startsWith(source.actualPath + "/")),
                        "INVALID_PATH", "The destination cannot be the source or a child of the source")
                    if (operation == "rename") {
                        failUnless(source.parent.actualPath == destination.parent.actualPath, "INVALID_PATH", "Rename must keep the same parent directory")
                    }
                    requireAbsent(destination.path)
                    if (operation == "move" || operation == "rename") {
                        source.checkParent()
                        destination.checkParent()
                        requireUnchanged(identity, source.identity())
                        checkDeadline()
                        when (val errno = PrivilegedFileNative.renameNoReplace(source.path, destination.path)) {
                            0 -> {
                                partial = true
                                recoveryPaths += destination.actualPath
                                source.checkParent()
                                destination.checkParent()
                                val moved = destination.identity()
                                // Android FUSE can update a directory's mtime as part of rename.
                                // The native no-replace operation retains the directory itself.
                                if (identity.kind == Kind.DIRECTORY) {
                                    failUnless(identity.sameObject(moved), "SOURCE_CHANGED", "Moved directory was replaced")
                                } else {
                                    requireUnchanged(identity, moved, ignoreChanged = true)
                                }
                                partial = false
                                recoveryPaths.clear()
                                return success().put("source", source.actualPath).put("destination", destination.actualPath)
                                    .put("method", "rename").put("cleanupPending", false).put("recoveryPaths", JSONArray())
                            }
                            OsConstants.EXDEV -> failUnless(isSharedStorage(source.actualPath) && isSharedStorage(destination.actualPath),
                                "UNSUPPORTED_MOVE", "Moving private or system files across filesystems cannot preserve their security attributes")
                            OsConstants.ENOSYS, OsConstants.EINVAL, OsConstants.EOPNOTSUPP ->
                                throw Failure("UNSUPPORTED_RENAME", "This filesystem does not support moving without replacing existing files")
                            else -> throw ErrnoException("renameat2", errno)
                        }
                    }
                    val tree = scan(source.path, identity, 0)
                    val stagingName = ".autojs6-copy-${UUID.randomUUID()}"
                    destination.parent.checkAttached()
                    Os.mkdir(destination.parent.child(stagingName), OWNER_DIRECTORY_MODE)
                    val stagingActual = destination.parent.actualChild(stagingName)
                    recoveryPaths += stagingActual
                    Directory.open(destination.parent.child(stagingName), stagingActual).use { staging ->
                        copyToStage(source.path, tree, staging.child("payload"))
                        source.checkParent()
                        verify(source.path, tree, hashes = false)
                        destination.checkParent()
                        requireAbsent(destination.path)
                        publish(staging.child("payload"), destination.path, tree) {
                            partial = true
                            recoveryPaths += destination.actualPath
                        }
                        destination.checkParent()
                        verifyCopy(destination.path, tree)
                        restoreCopyMetadata(destination.path, tree, isSharedStorage(destination.actualPath))
                        destination.checkParent()
                        failUnless(requireNotNull(tree.publishedIdentity).sameObject(destination.identity(), checkMode = false),
                            "SOURCE_CHANGED", "Destination was replaced after publication")
                        if (operation == "move" || operation == "rename") {
                            // Hashes catch content changes even when size and second-resolution
                            // Android stat timestamps have not changed.
                            source.checkParent()
                            verify(source.path, tree, hashes = true)
                            removeSource(source, tree)
                        }
                        // The requested result is complete. A leftover temporary is reported as
                        // cleanupPending instead of pretending that the user's copy failed.
                        runCatching {
                            entries = 0
                            val staged = scan(staging.child("payload"), identity(staging.child("payload")), 0)
                            removeTree(staging.child("payload"), staged)
                            staging.checkAttached()
                            Os.remove(destination.parent.child(stagingName))
                            recoveryPaths.remove(stagingActual)
                        }
                    }
                    recoveryPaths.remove(destination.actualPath)
                    partial = false
                    return success().put("source", source.actualPath).put("destination", destination.actualPath)
                        .put("bytes", copiedBytes).put("entries", tree.count())
                        .put("cleanupPending", recoveryPaths.isNotEmpty())
                        .put("recoveryPaths", JSONArray(recoveryPaths.toList()))
                }
            }
        }

        private fun scan(path: String, before: Identity, depth: Int): Node {
            checkDeadline()
            failUnless(depth <= MAX_DEPTH && ++entries <= MAX_ENTRIES, "LIMIT_EXCEEDED", "File operation exceeds the depth or entry limit")
            failUnless(before.kind != Kind.SPECIAL, "UNSUPPORTED_TYPE", "Device, FIFO, and socket entries cannot be operated on")
            val node = Node(before)
            when (before.kind) {
                Kind.DIRECTORY -> Directory.open(path).use { directory ->
                    requireUnchanged(before, directory.identity)
                    for (name in directory.names()) {
                        val child = directory.child(name)
                        val childIdentity = identity(child)
                        failUnless(childIdentity.kind != Kind.DIRECTORY || childIdentity.dev == before.dev,
                            "UNSUPPORTED_TYPE", "Nested mount points must be operated on separately")
                        node.children[name] = scan(child, childIdentity, depth + 1)
                    }
                    requireUnchanged(before, identity(path))
                }
                Kind.SYMLINK -> node.linkTarget = Os.readlink(path)
                else -> Unit
            }
            requireUnchanged(before, identity(path))
            return node
        }

        private fun copyToStage(source: String, node: Node, destination: String) {
            checkDeadline()
            requireUnchanged(node.identity, identity(source))
            when (node.identity.kind) {
                Kind.FILE -> {
                    node.digest = copyFile(source, destination, node.identity)
                    copiedBytes = Math.addExact(copiedBytes, node.identity.size)
                }
                Kind.SYMLINK -> {
                    failUnless(Os.readlink(source) == node.linkTarget, "SOURCE_CHANGED", "Symbolic link changed")
                    Os.symlink(requireNotNull(node.linkTarget), destination)
                }
                Kind.DIRECTORY -> {
                    Os.mkdir(destination, OWNER_DIRECTORY_MODE)
                    Directory.open(source).use { from ->
                        requireUnchanged(node.identity, from.identity)
                        Directory.open(destination).use { to ->
                            requireNames(from, node)
                            node.children.forEach { (name, child) -> copyToStage(from.child(name), child, to.child(name)) }
                        }
                    }
                }
                Kind.SPECIAL -> throw Failure("UNSUPPORTED_TYPE", "Unsupported file type")
            }
            requireUnchanged(node.identity, identity(source))
        }

        private fun publish(staged: String, destination: String, node: Node, onCreated: () -> Unit) {
            checkDeadline()
            when (node.identity.kind) {
                Kind.FILE -> {
                    val stagedIdentity = identity(staged)
                    try {
                        Os.link(staged, destination)
                        node.publishedIdentity = stagedIdentity
                        onCreated()
                    } catch (error: ErrnoException) {
                        // Some Android SELinux policies reject hard links through /proc/self/fd
                        // with EACCES while allowing ordinary exclusive file creation.
                        if (error.errno !in setOf(OsConstants.EACCES, OsConstants.EPERM, OsConstants.EOPNOTSUPP, OsConstants.EXDEV, OsConstants.ENOSYS)) throw error
                        val digest = copyFile(staged, destination, stagedIdentity) { created ->
                            node.publishedIdentity = created
                            onCreated()
                        }
                        failUnless(digest == node.digest, "SOURCE_CHANGED", "Staged file changed before publication")
                    }
                }
                Kind.SYMLINK -> {
                    Os.symlink(requireNotNull(node.linkTarget), destination)
                    node.publishedIdentity = identity(destination)
                    onCreated()
                }
                Kind.DIRECTORY -> {
                    // mkdir fails even if another process creates an empty destination directory.
                    Os.mkdir(destination, OWNER_DIRECTORY_MODE)
                    node.publishedIdentity = identity(destination)
                    onCreated()
                    Directory.open(staged).use { from ->
                        Directory.open(destination).use { to ->
                            node.children.forEach { (name, child) -> publish(from.child(name), to.child(name), child, onCreated) }
                            to.checkAttached()
                        }
                    }
                }
                Kind.SPECIAL -> throw Failure("UNSUPPORTED_TYPE", "Unsupported file type")
            }
        }

        private fun copyFile(source: String, destination: String, expected: Identity, onCreated: (Identity) -> Unit = {}): String {
            val inputDescriptor = openRegular(source, expected)
            FileInputStream(inputDescriptor).use { input ->
                val outputDescriptor = Os.open(destination,
                    OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW or O_CLOEXEC_LINUX,
                    OWNER_FILE_MODE)
                FileOutputStream(outputDescriptor).use { output ->
                    val outputIdentity = Identity.from(Os.fstat(outputDescriptor))
                    onCreated(outputIdentity)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(128 * 1024)
                    var size = 0L
                    while (true) {
                        checkDeadline()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size = Math.addExact(size, count.toLong())
                        failUnless(size <= expected.size, "SOURCE_CHANGED", "Source file grew while copying")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                    failUnless(size == expected.size, "SOURCE_CHANGED", "Source file size changed while copying")
                    requireUnchanged(expected, Identity.from(Os.fstat(inputDescriptor)))
                    requireUnchanged(expected, identity(source))
                    Os.fsync(outputDescriptor)
                    failUnless(outputIdentity.sameObject(identity(destination)), "SOURCE_CHANGED", "Destination changed while copying")
                    return digest.digest().toHex()
                }
            }
        }

        private fun verify(path: String, node: Node, hashes: Boolean, renamedTree: Boolean = false) {
            checkDeadline()
            val actual = identity(path)
            if (renamedTree && actual.kind == Kind.DIRECTORY) {
                // FUSE updates directory times and descendant ctimes during a parent rename.
                // Validate every inode/name instead; copied file contents are also rehashed.
                failUnless(node.identity.sameObject(actual), "SOURCE_CHANGED", "Selected directory was replaced")
            } else {
                requireUnchanged(node.identity, actual, ignoreChanged = renamedTree)
            }
            when (actual.kind) {
                Kind.FILE -> if (hashes) {
                    failUnless(digestFile(path, actual, ignoreChanged = renamedTree) == node.digest, "SOURCE_CHANGED", "Source contents changed after copying")
                }
                Kind.SYMLINK -> failUnless(Os.readlink(path) == node.linkTarget, "SOURCE_CHANGED", "Symbolic link changed")
                Kind.DIRECTORY -> Directory.open(path).use { directory ->
                    requireUnchanged(actual, directory.identity, ignoreChanged = renamedTree)
                    requireNames(directory, node)
                    node.children.forEach { (name, child) -> verify(directory.child(name), child, hashes, renamedTree) }
                    if (renamedTree) {
                        failUnless(actual.sameObject(identity(path)), "SOURCE_CHANGED", "Selected directory was replaced")
                    } else {
                        requireUnchanged(actual, identity(path))
                    }
                }
                Kind.SPECIAL -> throw Failure("UNSUPPORTED_TYPE", "Unsupported file type")
            }
        }

        private fun verifyCopy(path: String, node: Node) {
            checkDeadline()
            val actual = identity(path)
            failUnless(requireNotNull(node.publishedIdentity).sameObject(actual), "SOURCE_CHANGED", "Published entry was replaced")
            failUnless(actual.kind == node.identity.kind, "SOURCE_CHANGED", "Published file type changed")
            when (actual.kind) {
                Kind.FILE -> failUnless(actual.size == node.identity.size && digestFile(path, actual) == node.digest,
                    "SOURCE_CHANGED", "Published file contents do not match the source")
                Kind.SYMLINK -> failUnless(Os.readlink(path) == node.linkTarget, "SOURCE_CHANGED", "Published symbolic link changed")
                Kind.DIRECTORY -> Directory.open(path).use { directory ->
                    requireUnchanged(actual, directory.identity)
                    requireNames(directory, node)
                    node.children.forEach { (name, child) -> verifyCopy(directory.child(name), child) }
                    requireUnchanged(actual, identity(path))
                }
                Kind.SPECIAL -> throw Failure("UNSUPPORTED_TYPE", "Unsupported file type")
            }
        }

        private fun digestFile(path: String, expected: Identity, ignoreChanged: Boolean = false): String {
            val descriptor = openRegular(path, expected, ignoreChanged)
            FileInputStream(descriptor).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(128 * 1024)
                var size = 0L
                while (true) {
                    checkDeadline()
                    val count = input.read(buffer)
                    if (count < 0) break
                    size = Math.addExact(size, count.toLong())
                    failUnless(size <= expected.size, "SOURCE_CHANGED", "File grew during verification")
                    digest.update(buffer, 0, count)
                }
                failUnless(size == expected.size, "SOURCE_CHANGED", "File changed during verification")
                requireUnchanged(expected, Identity.from(Os.fstat(descriptor)), ignoreChanged)
                requireUnchanged(expected, identity(path), ignoreChanged)
                return digest.digest().toHex()
            }
        }

        private fun restoreCopyMetadata(path: String, node: Node, androidManagedPermissions: Boolean) {
            checkDeadline()
            failUnless(requireNotNull(node.publishedIdentity).sameObject(identity(path)), "SOURCE_CHANGED", "Published entry was replaced before restoring metadata")
            // Ownership and security labels are intentionally not fabricated for copies. Setuid
            // and setgid are never transferred to a new owner. Shared-storage mode bits are
            // virtual: Android can reject chmod or report a different mode after a successful call.
            val permissionBits = node.identity.mode and 0x3ff
            when (node.identity.kind) {
                Kind.DIRECTORY -> Directory.open(path).use { directory ->
                    requireNames(directory, node)
                    node.children.forEach { (name, child) -> restoreCopyMetadata(directory.child(name), child, androidManagedPermissions) }
                    directory.restoreMetadata(permissionBits, node.identity.modified, androidManagedPermissions)
                }
                Kind.FILE -> {
                    val before = identity(path)
                    val descriptor = openRegular(path, before)
                    try {
                        ParcelFileDescriptor.dup(descriptor).use { parcel ->
                            restoreMetadata(parcel, permissionBits, node.identity.modified, androidManagedPermissions)
                        }
                        failUnless(before.sameObject(identity(path), checkMode = false), "SOURCE_CHANGED", "Destination changed while restoring metadata")
                    } finally {
                        Os.close(descriptor)
                    }
                }
                Kind.SYMLINK -> Unit // Never chmod/utime through the link to its target.
                Kind.SPECIAL -> throw Failure("UNSUPPORTED_TYPE", "Unsupported file type")
            }
        }

        private fun removeSource(source: Target, tree: Node) {
            checkDeadline()
            source.checkParent()
            requireUnchanged(tree.identity, source.identity())
            val quarantineName = ".autojs6-delete-${UUID.randomUUID()}"
            Os.mkdir(source.parent.child(quarantineName), OWNER_DIRECTORY_MODE)
            val quarantineActual = source.parent.actualChild(quarantineName)
            recoveryPaths += quarantineActual
            Directory.open(source.parent.child(quarantineName), quarantineActual).use { quarantine ->
                checkDeadline()
                source.checkParent()
                requireUnchanged(tree.identity, source.identity())
                quarantine.checkAttached()
                // This rename only targets an unpublished slot inside a fresh private container;
                // user destinations always use the no-replacement publication path above.
                requireAbsent(quarantine.child("payload"))
                Os.rename(source.path, quarantine.child("payload"))
                partial = true
                verify(quarantine.child("payload"), tree,
                    hashes = request.getString("operation") in setOf("move", "rename"), renamedTree = true)
                removeTree(quarantine.child("payload"), tree, renamedTree = true)
                quarantine.checkAttached()
                Os.remove(source.parent.child(quarantineName))
                recoveryPaths.remove(quarantineActual)
            }
            partial = false
        }

        private fun removeTree(path: String, node: Node, renamedTree: Boolean = false) {
            checkDeadline()
            if (renamedTree && node.identity.kind == Kind.DIRECTORY) {
                failUnless(node.identity.sameObject(identity(path)), "SOURCE_CHANGED", "Selected directory was replaced")
            } else {
                requireUnchanged(node.identity, identity(path), ignoreChanged = renamedTree)
            }
            if (node.identity.kind == Kind.DIRECTORY) {
                Directory.open(path).use { directory ->
                    failUnless(node.identity.sameObject(directory.identity), "SOURCE_CHANGED", "Selected directory was replaced")
                    requireNames(directory, node)
                    node.children.forEach { (name, child) -> removeTree(directory.child(name), child, renamedTree) }
                    // Removing children changes the directory's own size and timestamps. Its
                    // inode must still be the selected inode, and rmdir refuses new children.
                    failUnless(node.identity.sameObject(identity(path)), "SOURCE_CHANGED", "Directory was replaced while deleting")
                    Os.remove(path)
                }
            } else {
                failUnless(node.identity.kind != Kind.SPECIAL, "UNSUPPORTED_TYPE", "Unsupported file type")
                if (node.identity.kind == Kind.SYMLINK) {
                    failUnless(Os.readlink(path) == node.linkTarget, "SOURCE_CHANGED", "Symbolic link changed before deletion")
                }
                Os.remove(path)
            }
        }

        private fun requireNames(directory: Directory, node: Node) {
            failUnless(directory.names().toSet() == node.children.keys, "SOURCE_CHANGED", "Directory contents changed")
        }

        private fun success() = JSONObject().put("ok", true).put("partial", false)
    }

    /** An opened parent anchors every child lookup, even if a pathname ancestor is replaced. */
    private class Directory private constructor(
        private val descriptor: ParcelFileDescriptor,
        private val attachedPath: String,
        val actualPath: String,
        val identity: Identity,
    ) : Closeable {
        private val path = "/proc/self/fd/${descriptor.fd}"

        fun child(name: String): String = "$path/${checkedName(name)}"
        fun actualChild(name: String): String = "${actualPath.trimEnd('/')}/${checkedName(name)}"
        fun names(): List<String> = (File(path).list() ?: throw Failure("PERMISSION_DENIED", "Unable to list directory: $actualPath"))
            .also { failUnless(it.size <= MAX_ENTRIES, "LIMIT_EXCEEDED", "Directory contains too many entries") }
            .map(::checkedName).sorted()

        fun checkAttached() {
            failUnless(identity.sameObject(identity(attachedPath)), "SOURCE_CHANGED", "Parent directory was replaced")
        }

        fun restoreMetadata(mode: Int, modified: Long, androidManagedPermissions: Boolean) {
            checkAttached()
            restoreMetadata(descriptor, mode, modified, androidManagedPermissions)
            failUnless(identity.sameObject(identity(attachedPath), checkMode = false), "SOURCE_CHANGED", "Destination directory changed while restoring metadata")
        }

        override fun close() = descriptor.close()

        companion object {
            fun open(path: String, actualPath: String = path): Directory {
                val before = identity(path)
                failUnless(before.kind == Kind.DIRECTORY, "INVALID_PATH", "Parent is not a directory")
                val raw = Os.open(path, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or OsConstants.O_NOFOLLOW or O_CLOEXEC_LINUX, 0)
                try {
                    val opened = Identity.from(Os.fstat(raw))
                    failUnless(before.sameObject(opened), "SOURCE_CHANGED", "Directory changed while opening")
                    return Directory(ParcelFileDescriptor.dup(raw), path, actualPath, opened)
                } finally {
                    Os.close(raw)
                }
            }
        }
    }

    private class Target(val parent: Directory, private val name: String) : Closeable {
        val path get() = parent.child(name)
        val actualPath get() = parent.actualChild(name)
        fun identity() = identity(path)
        fun checkParent() = parent.checkAttached()
        override fun close() = parent.close()

        companion object {
            fun open(path: String): Target {
                val file = File(path)
                val parentPath = requireNotNull(file.parentFile).canonicalPath
                return Target(Directory.open(parentPath, parentPath), checkedName(file.name))
            }
        }
    }

    private enum class Kind(val wireName: String) { FILE("file"), DIRECTORY("directory"), SYMLINK("symlink"), SPECIAL("special") }

    private data class Identity(val dev: Long, val ino: Long, val mode: Int, val size: Long, val modified: Long, val changed: Long) {
        val kind get() = when {
            OsConstants.S_ISREG(mode) -> Kind.FILE
            OsConstants.S_ISDIR(mode) -> Kind.DIRECTORY
            OsConstants.S_ISLNK(mode) -> Kind.SYMLINK
            else -> Kind.SPECIAL
        }

        fun sameObject(other: Identity, checkMode: Boolean = true) = dev == other.dev && ino == other.ino &&
            (if (checkMode) mode == other.mode else kind == other.kind)
        fun json() = JSONObject().put("dev", dev).put("ino", ino).put("mode", mode)
            .put("size", size).put("modified", modified).put("changed", changed)

        companion object {
            fun from(stat: StructStat) = Identity(stat.st_dev, stat.st_ino, stat.st_mode, stat.st_size,
                Math.multiplyExact(stat.st_mtime, 1000L), Math.multiplyExact(stat.st_ctime, 1000L))
        }
    }

    private class Node(val identity: Identity) {
        val children = LinkedHashMap<String, Node>()
        var linkTarget: String? = null
        var digest: String? = null
        var publishedIdentity: Identity? = null
        fun count(): Int = 1 + children.values.sumOf { it.count() }
    }

    class Failure(val code: String, message: String) : IOException(message)

    private fun identity(path: String) = Identity.from(Os.lstat(path))

    private fun isSharedStorage(path: String) = listOf("/storage", "/mnt/media_rw", "/mnt/runtime")
        .any { path.startsWith("$it/") }

    private fun restoreMetadata(descriptor: ParcelFileDescriptor, mode: Int, modified: Long, androidManagedPermissions: Boolean) {
        try {
            Os.fchmod(descriptor.fileDescriptor, mode)
        } catch (error: ErrnoException) {
            if (!androidManagedPermissions || error.errno !in setOf(OsConstants.EPERM, OsConstants.EACCES, OsConstants.EOPNOTSUPP)) throw error
        }
        failUnless(File("/proc/self/fd/${descriptor.fd}").setLastModified(modified), "IO", "Unable to restore the copied entry's modification time")
        val stat = Os.fstat(descriptor.fileDescriptor)
        if (!androidManagedPermissions) {
            failUnless((stat.st_mode and 0x3ff) == mode, "IO", "Unable to restore the copied entry's permissions")
        }
    }

    private fun openRegular(path: String, expected: Identity, ignoreChanged: Boolean = false): FileDescriptor {
        failUnless(expected.kind == Kind.FILE, "UNSUPPORTED_TYPE", "Only regular files can be read")
        // O_NONBLOCK prevents a file swapped for a FIFO from blocking before fstat can reject it.
        val descriptor = Os.open(path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or O_CLOEXEC_LINUX, 0)
        try {
            requireUnchanged(expected, Identity.from(Os.fstat(descriptor)), ignoreChanged)
            return descriptor
        } catch (error: Exception) {
            Os.close(descriptor)
            throw error
        }
    }

    private fun requireExpected(request: JSONObject, actual: Identity, required: Boolean) {
        val expected = request.optJSONObject("expected")
        failUnless(!required || expected != null, "INVALID_REQUEST", "A stat identity is required before changing or deleting a source")
        if (expected == null) return
        for (key in listOf("dev", "ino", "mode", "size", "modified", "changed")) {
            failUnless(expected.has(key), "INVALID_REQUEST", "Incomplete source identity")
            failUnless(expected.getLong(key) == actual.json().getLong(key), "SOURCE_CHANGED", "Source changed; refresh and select it again")
        }
    }

    private fun requireUnchanged(expected: Identity, actual: Identity, ignoreChanged: Boolean = false) {
        failUnless(expected.sameObject(actual) && expected.size == actual.size && expected.modified == actual.modified &&
            (ignoreChanged || expected.changed == actual.changed), "SOURCE_CHANGED", "File changed during the operation")
    }

    private fun requireAbsent(path: String) {
        try {
            Os.lstat(path)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) return
            throw error
        }
        throw Failure("DESTINATION_EXISTS", "Destination already exists; choose a different name")
    }

    private fun parseRequest(json: String): JSONObject {
        failUnless(json.length <= MAX_REQUEST_CHARS, "INVALID_REQUEST", "File operation request is too large")
        return JSONObject(json)
    }

    private fun checkedPath(path: String): String {
        failUnless(path.startsWith('/') && '\u0000' !in path && path.toByteArray(Charsets.UTF_8).size <= 4096,
            "INVALID_PATH", "An absolute path of at most 4096 bytes without NUL is required")
        val components = path.split('/').filter(String::isNotEmpty)
        failUnless(components.isNotEmpty() && components.none { it == "." || it == ".." },
            "INVALID_PATH", "Filesystem root and dot path components are not valid operation targets")
        return "/" + components.joinToString("/")
    }

    private fun checkedName(name: String): String {
        failUnless(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name,
            "INVALID_PATH", "Invalid directory entry name")
        return name
    }

    private fun failUnless(condition: Boolean, code: String, message: String) {
        if (!condition) throw Failure(code, message)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun errorCode(error: Exception): String = when (error) {
        is Failure -> error.code
        is SecurityException -> "PERMISSION_DENIED"
        is InterruptedException -> "CANCELLED"
        is ErrnoException -> when (error.errno) {
            OsConstants.EACCES, OsConstants.EPERM -> "PERMISSION_DENIED"
            OsConstants.EROFS -> "READ_ONLY"
            OsConstants.ENOENT -> "NOT_FOUND"
            OsConstants.EEXIST, OsConstants.ENOTEMPTY -> "DESTINATION_EXISTS"
            OsConstants.ENOSPC, OsConstants.EDQUOT -> "NO_SPACE"
            OsConstants.ELOOP -> "SOURCE_CHANGED"
            else -> "IO"
        }
        is IllegalArgumentException, is org.json.JSONException -> "INVALID_REQUEST"
        else -> "IO"
    }
}
