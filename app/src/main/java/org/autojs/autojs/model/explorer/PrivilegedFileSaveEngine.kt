package org.autojs.autojs.model.explorer

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Edits regular files through a separate inode, never by truncating the selected source.
 *
 * `statSnapshot` returns the identity and SHA-256 of the content actually read. `save` requires
 * that identity, `expectedDigest`, and the incoming stream's `contentSize`/`contentDigest`.
 * Supplying a UUID `transactionId` makes the recovery directory predictable even if the reply is lost;
 * its name is `.autojs6-save-<transactionId>` beside the canonical source. An existing directory is
 * never reused, so an interrupted request cannot accidentally execute twice.
 *
 * Linux has no pathname compare-and-swap against an inode/content version. We check before and
 * after RENAME_EXCHANGE, roll back only while the published content is still ours, and retain
 * the displaced original even on success. An unrelated writer holding an old descriptor can
 * continue writing that backup. Callers must retain/report its path, must not blindly retry an
 * unknown commit, and must not automatically delete backups on receipt of a successful reply.
 * This detects ordinary concurrent edits without claiming exclusion of uncooperative writers.
 */
object PrivilegedFileSaveEngine {
    private const val MAX_REQUEST_CHARS = 32 * 1024
    private const val MAX_CONTENT_BYTES = 1024L * 1024L * 1024L
    private const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    private const val MAX_TIMEOUT_MILLIS = 15 * 60_000L
    private const val O_CLOEXEC_LINUX = 0x80000
    private const val OWNER_FILE_MODE = 0x180 // 0600
    private const val OWNER_DIRECTORY_MODE = 0x1c0 // 0700
    private val identityKeys = listOf("dev", "ino", "mode", "size", "modified", "changed")
    private val additionalIdentityKeys = listOf("uid", "gid", "links")
    private val digestPattern = Regex("[0-9a-fA-F]{64}")

    fun statSnapshot(requestJson: String): String = try {
        val request = parseRequest(requestJson)
        val deadline = Deadline(request)
        deadline.check()
        Target.open(checkedPath(request.getString("source"))).use { target ->
            val before = target.identity()
            requireRegular(before)
            requireExpected(request, before, required = false)
            openFile(target.path, before, writable = false).use { descriptor ->
                val digest = digest(descriptor, before, deadline)
                target.checkParent()
                requireUnchanged(before, target.identity())
                successSnapshot(target.actualPath, before, digest).toString()
            }
        }
    } catch (error: Exception) {
        failure(error).toString()
    }

    /** The caller owns [input] and must close/cancel its transport when a request times out. */
    fun save(requestJson: String, input: InputStream): String = saveInternal(requestJson, input, null)

    /**
     * Proves an interrupted transaction can no longer mutate its original before allowing a
     * client to compare hashes and decide whether a new save is safe. A directory lease excludes
     * live saves; the completion entry fences out a request paused before acquiring its lease.
     * Reconciliation never writes, renames, or deletes the original file or its recovery copy.
     */
    fun reconcile(requestJson: String): String {
        var transactionId: String? = null
        var stagingPath: String? = null
        return try {
            val request = parseRequest(requestJson)
            transactionId = checkedTransactionId(request.getString("transactionId"))
            Deadline(request).check()
            Target.open(checkedPath(request.getString("source"))).use { target ->
                target.parent.acquireLease()
                target.checkParent()
                val name = ".autojs6-save-$transactionId"
                stagingPath = target.parent.actualChild(name)
                try {
                    Os.mkdir(target.parent.child(name), OWNER_DIRECTORY_MODE)
                } catch (error: ErrnoException) {
                    if (error.errno != OsConstants.EEXIST) throw error
                }
                Directory.open(target.parent.child(name), requireNotNull(stagingPath)).use { staging ->
                    staging.acquireLease()
                    target.checkParent()
                    staging.checkAttached()
                    val existing = completedResult(staging, requireNotNull(transactionId))
                    if (existing != null) return reconciled(requireNotNull(transactionId), existing).toString()
                    val result = JSONObject().put("ok", false).put("partial", true).put("completed", true)
                        .put("transactionId", transactionId).put("commitState", "unknown")
                        .put("code", "SAVE_STATE_UNKNOWN")
                        .put("message", "The previous save has stopped; compare the original and the draft before saving again")
                        .put("backupPath", staging.actualChild("original"))
                        .put("recoveryPaths", JSONArray(listOf(staging.actualPath, target.actualPath)))
                    if (!exists(staging.child("result.json"))) {
                        publishResult(staging, result, "reconcile-${UUID.randomUUID()}.pending")
                    }
                    // Even a malformed pre-existing completion file is a permanent fence. The
                    // lease proves no older writer is running, and save refuses any such entry.
                    return reconciled(requireNotNull(transactionId), result).toString()
                }
            }
        } catch (error: Exception) {
            failure(error).put("completed", false).put("partial", true).put("commitState", "unknown")
                .apply {
                    transactionId?.let { put("transactionId", it) }
                    stagingPath?.let { put("recoveryPaths", JSONArray(listOf(it))) }
                }.toString()
        }
    }

    @VisibleForTesting
    internal fun saveWithCommitObserver(requestJson: String, input: InputStream, observer: (String) -> Unit): String =
        saveInternal(requestJson, input, observer)

    private fun saveInternal(requestJson: String, input: InputStream, observer: ((String) -> Unit)?): String {
        var operation: Save? = null
        val result = try {
            operation = Save(parseRequest(requestJson), input, observer)
            operation.run()
        } catch (error: Exception) {
            operation?.failure(error) ?: failure(error)
        }
        // All source/staging descriptors and every rollback attempt have finished before this
        // marker can become visible. A timeout alone is never evidence that a blocked syscall
        // has finished: a client with a lost reply must find this matching completed record.
        result.put("completed", true)
        operation?.recordResult(result)
        return result.toString()
    }

    private class Save(
        private val request: JSONObject,
        private val input: InputStream,
        private val observer: ((String) -> Unit)?,
    ) {
        private val deadline = Deadline(request)
        private val expectedDigest = checkedDigest(request.getString("expectedDigest"))
        private val contentDigest = checkedDigest(request.getString("contentDigest"))
        private val contentSize = request.getLong("contentSize").also {
            requireThat(it in 0..MAX_CONTENT_BYTES, "LIMIT_EXCEEDED", "Edited files are limited to 1 GiB")
        }
        private val transactionId = request.optString("transactionId").takeIf(String::isNotEmpty)
            ?.let(::checkedTransactionId) ?: UUID.randomUUID().toString()
        private var commitState = "unchanged"
        private val recoveryPaths = LinkedHashSet<String>()
        private var backupPath: String? = null
        private var rollbackError: String? = null
        private var completedDirectoryPath: String? = null
        private var completedDirectoryIdentity: Identity? = null

        fun run(): JSONObject {
            deadline.check()
            Target.open(checkedPath(request.getString("source"))).use { target ->
                target.parent.acquireLease()
                target.checkParent()
                val original = target.identity()
                requireRegular(original)
                requireSingleLink(original)
                requireExpected(request, original, required = true)
                // Opening O_RDWR is an explicit DAC/SELinux write-access check without writing.
                openFile(target.path, original, writable = true).use { originalFd ->
                    requireDigest(originalFd, original, expectedDigest)
                    target.checkParent()
                    requireUnchanged(original, target.identity())
                    val stagingName = ".autojs6-save-$transactionId"
                    Os.mkdir(target.parent.child(stagingName), OWNER_DIRECTORY_MODE)
                    val stagingPath = target.parent.actualChild(stagingName)
                    recoveryPaths += stagingPath
                    Directory.open(target.parent.child(stagingName), stagingPath).use { staging ->
                        staging.acquireLease()
                        requireThat(!exists(staging.child("result.json")), "TRANSACTION_FINISHED", "This save transaction was already completed or reconciled")
                        completedDirectoryPath = stagingPath
                        completedDirectoryIdentity = staging.openedIdentity
                        try {
                            writeManifest(staging, target.actualPath)
                            val stagedPath = staging.child("original")
                            openExclusive(stagedPath).use { stagedFd ->
                                transfer(stagedFd)
                                requireMetadata(PrivilegedFileNative.copySaveMetadata(originalFd.fd, stagedFd.fd))
                                Os.fsync(stagedFd.fileDescriptor)
                                val stagingPrepared = staging.sync()
                                val parentPrepared = target.parent.sync()
                                val replacement = Identity.from(Os.fstat(stagedFd.fileDescriptor))
                                requireSingleLink(replacement)
                                requireDigest(stagedFd, replacement, contentDigest)
                                requireUnchanged(replacement, identity(stagedPath))
                                // Re-read the original, including same-size/same-mtime modifications,
                                // after all potentially long staging and attribute work has finished.
                                requireDigest(originalFd, original, expectedDigest)
                                target.checkParent()
                                staging.checkAttached()
                                requireUnchanged(original, target.identity())
                                requireMetadata(PrivilegedFileNative.compareSaveMetadata(originalFd.fd, stagedFd.fd))
                                requireUnchanged(replacement, identity(stagedPath))
                                deadline.check()
                                requireExchange(PrivilegedFileNative.exchange(target.path, stagedPath))
                                commitState = "unknown"
                                backupPath = staging.actualChild("original")
                                recoveryPaths += target.actualPath
                                try {
                                    observer?.invoke(requireNotNull(backupPath))
                                    target.checkParent()
                                    staging.checkAttached()
                                    requireUnchanged(original, identity(stagedPath), ignoreChanged = true)
                                    requireUnchanged(replacement, target.identity(), ignoreChanged = true)
                                    requireDigest(originalFd, original, expectedDigest, ignoreChanged = true)
                                    requireDigest(stagedFd, replacement, contentDigest, ignoreChanged = true)
                                    requireMetadata(PrivilegedFileNative.compareSaveMetadata(originalFd.fd, stagedFd.fd))
                                    requireUnchanged(original, identity(stagedPath), ignoreChanged = true)
                                    requireUnchanged(replacement, target.identity(), ignoreChanged = true)
                                    val backupIdentity = identity(stagedPath)
                                    val resultIdentity = target.identity()
                                    Os.fsync(originalFd.fileDescriptor)
                                    Os.fsync(stagedFd.fileDescriptor)
                                    val stagingSynced = staging.sync()
                                    val parentSynced = target.parent.sync()
                                    deadline.check()
                                    target.checkParent()
                                    staging.checkAttached()
                                    requireUnchanged(resultIdentity, target.identity())
                                    requireUnchanged(backupIdentity, identity(stagedPath))
                                    commitState = "committed"
                                    recoveryPaths.remove(target.actualPath)
                                    return successSnapshot(target.actualPath, resultIdentity, contentDigest)
                                        .put("transactionId", transactionId).put("commitState", commitState)
                                        .put("backupPath", backupPath).put("backupIdentity", backupIdentity.json())
                                        .put("backupDigest", expectedDigest).put("cleanupPending", true)
                                        .put("directorySync", stagingPrepared && parentPrepared && stagingSynced && parentSynced)
                                        .put("recoveryPaths", JSONArray(recoveryPaths.toList()))
                                } catch (error: Exception) {
                                    rollback(target, staging, originalFd, stagedFd, original, replacement)
                                    throw error
                                }
                            }
                        } finally {
                            // A racing rename of an ancestor must not leave only its stale pathname
                            // in the recovery report. These descriptors are still open here.
                            val currentStaging = staging.currentPath()
                            completedDirectoryPath = currentStaging
                            if (currentStaging != stagingPath) {
                                recoveryPaths.remove(stagingPath)
                                recoveryPaths += currentStaging
                                if (backupPath != null) backupPath = "$currentStaging/original"
                            }
                            if (commitState == "unknown") recoveryPaths += target.currentPath()
                        }
                    }
                }
            }
        }

        /** Best effort, and deliberately called only after [run] has closed all source FDs. */
        fun recordResult(result: JSONObject) {
            val path = completedDirectoryPath ?: return
            val expectedDirectory = completedDirectoryIdentity ?: return
            runCatching {
                Directory.open(path).use { staging ->
                    staging.acquireLease()
                    requireThat(expectedDirectory.sameObject(staging.openedIdentity),
                        "SOURCE_CHANGED", "Recovery directory was replaced before recording completion")
                    publishResult(staging, result, "result.pending")
                }
            }
        }

        private fun writeManifest(staging: Directory, source: String) {
            val manifest = JSONObject().put("source", source).put("transactionId", transactionId)
                .put("expected", request.getJSONObject("expected")).put("expectedDigest", expectedDigest)
                .put("contentSize", contentSize).put("contentDigest", contentDigest)
            openExclusive(staging.child("request.json")).use { descriptor ->
                ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { output ->
                    output.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    output.flush()
                    Os.fsync(descriptor.fileDescriptor)
                }
            }
            staging.sync()
        }

        private fun transfer(descriptor: ParcelFileDescriptor) {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            val buffer = ByteArray(128 * 1024)
            ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { output ->
                while (true) {
                    deadline.check()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    count = Math.addExact(count, read.toLong())
                    requireThat(count <= contentSize, "TRANSFER_MISMATCH", "Incoming edit is longer than its declared size")
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
                requireThat(count == contentSize && digest.digest().toHex() == contentDigest,
                    "TRANSFER_MISMATCH", "Incoming edit was interrupted or does not match its checksum")
                output.flush()
                Os.fsync(descriptor.fileDescriptor)
            }
        }

        private fun requireDigest(descriptor: ParcelFileDescriptor, expected: Identity, expectedHash: String, ignoreChanged: Boolean = false) {
            requireThat(digest(descriptor, expected, deadline, ignoreChanged) == expectedHash,
                "SOURCE_CHANGED", "The file contents changed; the original and the edit have been retained")
        }

        private fun rollback(
            target: Target,
            staging: Directory,
            originalFd: ParcelFileDescriptor,
            stagedFd: ParcelFileDescriptor,
            original: Identity,
            replacement: Identity,
        ) {
            // Recovery has a fresh short deadline: an expired/cancelled request must still get a
            // chance to put the displaced file back. It never truncates/deletes either inode.
            val recoveryDeadline = Deadline(JSONObject().put("deadlineMillis", System.currentTimeMillis() + 15_000), ignoreInterrupt = true)
            try {
                target.checkParent()
                staging.checkAttached()
                val displaced = identity(staging.child("original"))
                requireThat(original.sameObject(displaced, checkMode = false),
                    "SOURCE_CHANGED", "The recovery copy was replaced")
                // If someone edited the newly published file, keep it at its current path. The
                // old original remains available in the recovery directory for manual resolution.
                requireUnchanged(replacement, target.identity(), ignoreChanged = true)
                requireThat(digest(stagedFd, replacement, recoveryDeadline, ignoreChanged = true) == contentDigest,
                    "SOURCE_CHANGED", "The published file was edited before rollback")
                requireThat(Identity.from(Os.fstat(originalFd.fileDescriptor)).sameObject(displaced, checkMode = false),
                    "SOURCE_CHANGED", "The displaced source was replaced")
                requireExchange(PrivilegedFileNative.exchange(target.path, staging.child("original")))
                requireThat(original.sameObject(target.identity(), checkMode = false) &&
                    replacement.sameObject(identity(staging.child("original")), checkMode = false),
                    "SOURCE_CHANGED", "A racing rename prevented rollback verification")
                target.checkParent()
                staging.checkAttached()
                staging.sync()
                target.parent.sync()
                commitState = "rolled_back"
                recoveryPaths.remove(target.actualPath)
            } catch (error: Exception) {
                commitState = "unknown"
                rollbackError = error.message.orEmpty().take(1024)
            }
        }

        fun failure(error: Exception): JSONObject = PrivilegedFileSaveEngine.failure(error)
            .put("transactionId", transactionId).put("commitState", commitState).put("partial", commitState == "unknown")
            .put("code", if (commitState == "unknown") "SAVE_STATE_UNKNOWN" else errorCode(error))
            .put("recoveryPaths", JSONArray(recoveryPaths.toList()))
            .apply {
                backupPath?.let { put("backupPath", it) }
                rollbackError?.let { put("rollbackError", it) }
            }
    }

    private class Deadline(request: JSONObject, private val ignoreInterrupt: Boolean = false) {
        private val end: Long

        init {
            val now = System.currentTimeMillis()
            val requested = request.optLong("deadlineMillis", now + DEFAULT_TIMEOUT_MILLIS)
            val remaining = if (requested <= now) 0L else (requested - now).coerceAtMost(MAX_TIMEOUT_MILLIS)
            end = System.nanoTime() + remaining * 1_000_000L
        }

        fun check() {
            requireThat(ignoreInterrupt || !Thread.currentThread().isInterrupted, "CANCELLED", "File save was cancelled")
            requireThat(System.nanoTime() < end, "TIMEOUT", "File save timed out")
        }
    }

    private class Directory private constructor(
        private val descriptor: ParcelFileDescriptor,
        private val attachedPath: String,
        val actualPath: String,
        private val before: Identity,
    ) : Closeable {
        private val path = "/proc/self/fd/${descriptor.fd}"
        val openedIdentity get() = before
        fun acquireLease() {
            when (val errno = PrivilegedFileNative.tryLock(descriptor.fd)) {
                0 -> Unit
                OsConstants.EAGAIN -> throw Failure("TRANSACTION_BUSY", "Another save or recovery check in this directory is still running")
                OsConstants.ENOSYS, OsConstants.EOPNOTSUPP, OsConstants.EINVAL ->
                    throw Failure("UNSUPPORTED_LOCK", "This filesystem cannot safely coordinate editor save transactions")
                else -> throw ErrnoException("lock save directory", errno)
            }
        }
        fun child(name: String) = "$path/${checkedName(name)}"
        fun actualChild(name: String) = "${actualPath.trimEnd('/')}/${checkedName(name)}"
        fun currentPath() = runCatching { Os.readlink(path).takeUnless { it.endsWith(" (deleted)") } }.getOrNull() ?: actualPath
        fun checkAttached() = requireThat(before.sameObject(identity(attachedPath), checkMode = false),
            "SOURCE_CHANGED", "Parent directory was replaced")
        fun sync(): Boolean = try {
            Os.fsync(descriptor.fileDescriptor)
            true
        } catch (error: ErrnoException) {
            if (error.errno !in setOf(OsConstants.EINVAL, OsConstants.EOPNOTSUPP)) throw error
            false // Some shared-storage filesystems implement file fsync but not directory fsync.
        }
        override fun close() = descriptor.close()

        companion object {
            fun open(path: String, actualPath: String = path): Directory {
                val before = identity(path)
                requireThat(OsConstants.S_ISDIR(before.mode), "INVALID_PATH", "Parent is not a directory")
                val raw = Os.open(path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or O_CLOEXEC_LINUX, 0)
                try {
                    val opened = Identity.from(Os.fstat(raw))
                    requireThat(before.sameObject(opened), "SOURCE_CHANGED", "Parent directory changed while opening")
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
        fun currentPath() = "${parent.currentPath().trimEnd('/')}/$name"
        fun identity() = identity(path)
        fun checkParent() = parent.checkAttached()
        override fun close() = parent.close()

        companion object {
            fun open(path: String): Target {
                val file = File(path)
                val parentPath = requireNotNull(file.parentFile).canonicalPath
                return Target(Directory.open(parentPath), checkedName(file.name))
            }
        }
    }

    private data class Identity(
        val dev: Long, val ino: Long, val mode: Int, val size: Long,
        val modified: Long, val changed: Long, val uid: Int, val gid: Int, val links: Long,
    ) {
        fun sameObject(other: Identity, checkMode: Boolean = true) = dev == other.dev && ino == other.ino &&
            (!checkMode || mode == other.mode)
        fun json() = JSONObject().put("dev", dev).put("ino", ino).put("mode", mode)
            .put("size", size).put("modified", modified).put("changed", changed)
            .put("uid", uid).put("gid", gid).put("links", links)

        companion object {
            fun from(stat: StructStat) = Identity(stat.st_dev, stat.st_ino, stat.st_mode, stat.st_size,
                Math.multiplyExact(stat.st_mtime, 1000L), Math.multiplyExact(stat.st_ctime, 1000L),
                stat.st_uid, stat.st_gid, stat.st_nlink)
        }
    }

    private class Failure(val code: String, message: String) : IOException(message)

    private fun identity(path: String) = Identity.from(Os.lstat(path))
    private fun requireRegular(identity: Identity) = requireThat(OsConstants.S_ISREG(identity.mode),
        "UNSUPPORTED_TYPE", "Only regular files can be edited; symbolic links and special files are not followed")
    private fun requireSingleLink(identity: Identity) = requireThat(identity.links == 1L,
        "UNSUPPORTED_HARD_LINK", "Replacing a file with multiple hard links would break its link relationships")

    private fun openFile(path: String, expected: Identity, writable: Boolean): ParcelFileDescriptor {
        requireRegular(expected)
        val raw = Os.open(path, (if (writable) OsConstants.O_RDWR else OsConstants.O_RDONLY) or
            OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or O_CLOEXEC_LINUX, 0)
        try {
            requireUnchanged(expected, Identity.from(Os.fstat(raw)))
            return ParcelFileDescriptor.dup(raw)
        } finally {
            Os.close(raw)
        }
    }

    private fun openExclusive(path: String): ParcelFileDescriptor {
        val raw = Os.open(path, OsConstants.O_RDWR or OsConstants.O_CREAT or OsConstants.O_EXCL or
            OsConstants.O_NOFOLLOW or O_CLOEXEC_LINUX, OWNER_FILE_MODE)
        try {
            return ParcelFileDescriptor.dup(raw)
        } finally {
            Os.close(raw)
        }
    }

    private fun digest(descriptor: ParcelFileDescriptor, expected: Identity, deadline: Deadline, ignoreChanged: Boolean = false): String {
        deadline.check()
        requireRegular(expected)
        requireThat(expected.size in 0..MAX_CONTENT_BYTES, "LIMIT_EXCEEDED", "Edited files are limited to 1 GiB")
        requireUnchanged(expected, Identity.from(Os.fstat(descriptor.fileDescriptor)), ignoreChanged)
        Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        val buffer = ByteArray(128 * 1024)
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { input ->
            while (true) {
                deadline.check()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                count = Math.addExact(count, read.toLong())
                requireThat(count <= expected.size, "SOURCE_CHANGED", "File grew while being checked")
                digest.update(buffer, 0, read)
            }
        }
        requireThat(count == expected.size, "SOURCE_CHANGED", "File changed while being checked")
        requireUnchanged(expected, Identity.from(Os.fstat(descriptor.fileDescriptor)), ignoreChanged)
        return digest.digest().toHex()
    }

    private fun requireExpected(request: JSONObject, actual: Identity, required: Boolean) {
        val expected = request.optJSONObject("expected")
        requireThat(!required || expected != null, "INVALID_REQUEST", "A saved source identity is required")
        if (expected == null) return
        val actualJson = actual.json()
        identityKeys.forEach { key ->
            requireThat(expected.has(key), "INVALID_REQUEST", "Incomplete source identity")
            requireThat(expected.getLong(key) == actualJson.getLong(key), "SOURCE_CHANGED", "The original file changed after opening")
        }
        additionalIdentityKeys.filter(expected::has).forEach { key ->
            requireThat(expected.getLong(key) == actualJson.getLong(key), "SOURCE_CHANGED", "The original file attributes changed after opening")
        }
    }

    private fun requireUnchanged(expected: Identity, actual: Identity, ignoreChanged: Boolean = false) = requireThat(
        expected.sameObject(actual) && expected.size == actual.size && expected.modified == actual.modified &&
            (ignoreChanged || expected.changed == actual.changed) && expected.uid == actual.uid &&
            expected.gid == actual.gid && expected.links == actual.links,
        "SOURCE_CHANGED", "The original file or its attributes changed during the save",
    )

    private fun requireMetadata(errno: Int) {
        when (errno) {
            0 -> Unit
            OsConstants.ENOSYS, OsConstants.EOPNOTSUPP, OsConstants.E2BIG ->
                throw Failure("UNSUPPORTED_METADATA", "Ownership, permissions, or security attributes cannot be preserved")
            OsConstants.EAGAIN, OsConstants.ENODATA -> throw Failure("SOURCE_CHANGED", "File security attributes changed during the save")
            else -> throw ErrnoException("preserve file attributes", errno)
        }
    }

    private fun requireExchange(errno: Int) {
        when (errno) {
            0 -> Unit
            OsConstants.ENOSYS, OsConstants.EINVAL, OsConstants.EOPNOTSUPP, OsConstants.EXDEV ->
                throw Failure("UNSUPPORTED_REPLACE", "This filesystem cannot atomically exchange a file with its recovery copy")
            else -> throw ErrnoException("renameat2", errno)
        }
    }

    private fun successSnapshot(path: String, identity: Identity, digest: String) = JSONObject()
        .put("ok", true).put("partial", false).put("path", path).put("identity", identity.json())
        .put("digest", digest).put("size", identity.size).put("modified", identity.modified)

    private fun reconciled(transactionId: String, result: JSONObject) = JSONObject().put("ok", true)
        .put("completed", true).put("transactionId", transactionId).put("result", result)

    private fun publishResult(staging: Directory, result: JSONObject, temporaryName: String) {
        openExclusive(staging.child(temporaryName)).use { descriptor ->
            ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { output ->
                output.write(result.toString().toByteArray(Charsets.UTF_8))
                output.flush()
                Os.fsync(descriptor.fileDescriptor)
            }
        }
        staging.checkAttached()
        val errno = PrivilegedFileNative.renameNoReplace(staging.child(temporaryName), staging.child("result.json"))
        if (errno != 0) throw ErrnoException("publish save completion", errno)
        staging.sync()
    }

    private fun completedResult(staging: Directory, transactionId: String): JSONObject? {
        if (!exists(staging.child("result.json"))) return null
        return runCatching {
            val before = identity(staging.child("result.json"))
            requireThat(before.size in 0..64 * 1024, "INVALID_REQUEST", "Save completion record is too large")
            openFile(staging.child("result.json"), before, writable = false).use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        requireThat(output.size() + count <= 64 * 1024, "INVALID_REQUEST", "Save completion record grew while reading")
                        output.write(buffer, 0, count)
                    }
                    requireUnchanged(before, identity(staging.child("result.json")))
                    JSONObject(output.toString(Charsets.UTF_8.name())).takeIf {
                        it.optBoolean("completed") && it.optString("transactionId") == transactionId
                    }
                }
            }
        }.getOrNull()
    }

    private fun exists(path: String): Boolean = try {
        Os.lstat(path)
        true
    } catch (error: ErrnoException) {
        if (error.errno != OsConstants.ENOENT) throw error
        false
    }

    private fun failure(error: Exception) = JSONObject().put("ok", false).put("partial", false)
        .put("code", errorCode(error)).put("message", error.message.orEmpty().take(4096))
        .put("commitState", "unchanged").put("recoveryPaths", JSONArray())

    private fun parseRequest(json: String): JSONObject {
        requireThat(json.length <= MAX_REQUEST_CHARS, "INVALID_REQUEST", "Save request is too large")
        return JSONObject(json)
    }

    private fun checkedDigest(value: String): String {
        requireThat(digestPattern.matches(value), "INVALID_REQUEST", "A SHA-256 digest is required")
        return value.lowercase()
    }

    private fun checkedTransactionId(value: String): String {
        requireThat(runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false),
            "INVALID_REQUEST", "transactionId must be a UUID")
        return value
    }

    private fun checkedPath(path: String): String {
        requireThat(path.startsWith('/') && '\u0000' !in path && path.toByteArray(Charsets.UTF_8).size <= 4096,
            "INVALID_PATH", "An absolute path of at most 4096 bytes without NUL is required")
        val components = path.split('/').filter(String::isNotEmpty)
        requireThat(components.isNotEmpty() && components.none { it == "." || it == ".." },
            "INVALID_PATH", "Filesystem root and dot path components are not valid save targets")
        return "/" + components.joinToString("/")
    }

    private fun checkedName(name: String): String {
        requireThat(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name,
            "INVALID_PATH", "Invalid directory entry name")
        return name
    }

    private fun requireThat(condition: Boolean, code: String, message: String) {
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
