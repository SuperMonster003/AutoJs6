package org.autojs.autojs.model.explorer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Durable editor documents whose bytes are private to this app. Each opening has its own baseline;
 * editing a second window must never silently rebase the first window onto someone else's save.
 * The provider is read-only. Only [save] may conditionally replace an authorized original.
 */
object PrivilegedEditSessions {
    private const val DIRECTORY = "privileged-edit-sessions"
    private const val SAVE_TIMEOUT_MS = 180_000L
    private const val MAX_BYTES = 1024L * 1024 * 1024
    private const val CLEAN_SESSION_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    private val tokens = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private val locks = ConcurrentHashMap<String, Any>()
    private val activeEditors = ConcurrentHashMap<String, Int>()

    fun attach(context: Context, uri: Uri) {
        if (isSessionUri(context, uri)) activeEditors.compute(uri.toString()) { _, count -> (count ?: 0) + 1 }
    }

    fun detach(context: Context, uri: Uri) {
        if (isSessionUri(context, uri)) activeEditors.computeIfPresent(uri.toString()) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    /** Only an explicit clean close makes a session eligible for later space reclamation. */
    fun markCleanlyClosed(context: Context, uri: Uri) = synchronized(lock(uri.toString())) {
        if (activeEditors.containsKey(uri.toString())) return@synchronized
        val directory = sessionDirectory(context, uri)
        val metadata = readMetadata(directory)
        if (!metadata.has("pending") && !metadata.has("attempt")) {
            metadata.put("closedCleanlyAt", System.currentTimeMillis())
            writeMetadata(directory, metadata)
        }
    }

    /** Explicit discard/Save as resolves the draft but never erases an uncertain remote request. */
    fun discardDraft(context: Context, uri: Uri) = synchronized(lock(uri.toString())) {
        val directory = sessionDirectory(context, uri)
        val metadata = readMetadata(directory)
        metadata.remove("pending")
        metadata.put("draftResolved", true)
        metadata.put("closedCleanlyAt", System.currentTimeMillis())
        if (metadata.has("baselineCharset")) metadata.put("charset", metadata.getString("baselineCharset")) else metadata.remove("charset")
        writeMetadata(directory, metadata)
        cleanupUnreferencedContents(directory, metadata)
    }

    fun isSessionUri(context: Context, uri: Uri): Boolean =
        uri.scheme == "content" && uri.authority == authority(context)

    fun create(context: Context, mode: PrivilegedAccessMode, path: String): Uri {
        cleanupClosedSessions(context)
        findRecoverable(context, path)?.let { uri ->
            try { changeAccessMode(context, uri, mode) } catch (error: Exception) {
                detach(context, uri)
                throw error
            }
            return uri
        }
        val uri = Uri.Builder().scheme("content").authority(authority(context)).appendPath(UUID.randomUUID().toString()).build()
        val directory = sessionDirectory(context, uri, create = true)
        var preview: File? = null
        try {
            val before = PrivilegedFileOperations.snapshot(context, mode, path)
            preview = PrivilegedFileOperations.materialize(context, mode, path)
            val digest = digest(preview)
            val after = PrivilegedFileOperations.snapshot(context, mode, path)
            if (!sameSnapshot(before, after) || digest != before.getString("digest")) {
                throw PrivilegedFileOperationException("SOURCE_CHANGED", "File changed while opening")
            }
            val baseline = File(directory, "content-${UUID.randomUUID()}")
            preview.inputStream().use { input ->
                baseline.outputStream().use { output -> input.copyTo(output); output.fd.sync() }
            }
            writeMetadata(directory, JSONObject()
                .put("version", 1).put("source", path).put("canonicalSource", before.getString("path")).put("mode", mode.name)
                .put("identity", before.getJSONObject("identity")).put("digest", digest)
                .put("baseline", baseline.name).put("recovery", JSONArray()))
            return uri
        } catch (error: Exception) {
            // This UUID directory was created by this invocation and has never been published.
            directory.listFiles().orEmpty().forEach { it.delete() }
            directory.delete()
            throw localized(context, error)
        } finally {
            preview?.let { it.delete(); it.parentFile?.delete() }
        }
    }

    fun originalPath(context: Context, uri: Uri): String? =
        runCatching { readMetadata(sessionDirectory(context, uri)).getString("source") }.getOrNull()

    fun hasPendingDraft(context: Context, uri: Uri): Boolean =
        runCatching { readMetadata(sessionDirectory(context, uri)).has("pending") }.getOrDefault(false)

    /** Retain the chosen charset across failed saves and process recreation, including legacy text. */
    fun pendingCharset(context: Context, uri: Uri): Charset? = runCatching {
        readMetadata(sessionDirectory(context, uri)).optString("charset").takeIf(String::isNotEmpty)?.let(Charset::forName)
    }.getOrNull()

    fun accessMode(context: Context, uri: Uri): PrivilegedAccessMode =
        PrivilegedAccessMode.valueOf(readMetadata(sessionDirectory(context, uri)).getString("mode"))

    fun changeAccessMode(context: Context, uri: Uri, mode: PrivilegedAccessMode) = synchronized(lock(uri.toString())) {
        val directory = sessionDirectory(context, uri)
        writeMetadata(directory, readMetadata(directory).put("mode", mode.name))
    }

    fun consumeSaveNotice(context: Context, uri: Uri): String? = synchronized(lock(uri.toString())) {
        val directory = sessionDirectory(context, uri)
        val metadata = readMetadata(directory)
        val path = metadata.optString("notice").takeIf(String::isNotEmpty) ?: return@synchronized null
        metadata.remove("notice")
        writeMetadata(directory, metadata)
        context.getString(R.string.text_privileged_edit_backup, path)
    }

    fun save(context: Context, uri: Uri, bytes: ByteArray, charsetName: String, hadBom: Boolean) = synchronized(lock(uri.toString())) {
        val directory = sessionDirectory(context, uri)
        var metadata = readMetadata(directory)
        val path = metadata.getString("source")
        try {
            Charset.forName(charsetName)
            if (bytes.size > MAX_BYTES || bytes.size > directory.usableSpace - 32L * 1024 * 1024) {
                throw PrivilegedFileOperationException("NO_SPACE", "Not enough space for an editing draft")
            }
            // Persist before waiting for another editor's save, IPC, or original-file validation.
            // Permission settings may kill the app even while this session is waiting for its turn.
            val pending = File(directory, "content-${UUID.randomUUID()}")
            pending.outputStream().use { it.write(bytes); it.fd.sync() }
            metadata.put("pending", pending.name).put("charset", charsetName).put("hadBom", hadBom).remove("draftResolved")
            writeMetadata(directory, metadata)
            cleanupUnreferencedContents(directory, metadata)
            synchronized(lock("source:${metadata.optString("canonicalSource", path)}")) {
                val mode = PrivilegedAccessMode.valueOf(metadata.getString("mode"))
                assertNoOtherActiveSave(context, directory, metadata.optString("canonicalSource", path), mode)
                reconcilePreviousSave(context, directory, metadata, mode)
                metadata = readMetadata(directory)
                val transaction = UUID.randomUUID().toString()
                val request = JSONObject()
                    .put("operation", "save").put("source", path)
                    .put("expected", metadata.getJSONObject("identity")).put("expectedDigest", metadata.getString("digest"))
                    .put("contentSize", bytes.size).put("contentDigest", digest(bytes))
                    .put("transactionId", transaction).put("deadlineMillis", System.currentTimeMillis() + SAVE_TIMEOUT_MS)
                val attempt = JSONObject(request.toString()).put("content", pending.name).put("charset", charsetName)
                    .put("backupPath", File(File(metadata.optString("canonicalSource", path)).parentFile, ".autojs6-save-$transaction/original").path)
                metadata.put("attempt", attempt)
                writeMetadata(directory, metadata)
                val result = try {
                    PrivilegedFileOperations.save(context, mode, request, pending)
                } catch (error: Exception) {
                    var failure = error as? PrivilegedFileOperationException
                    if (failure == null || failure.code == "OUTCOME_UNKNOWN" || failure.partial) {
                        val paths = (failure?.recoveryPaths.orEmpty() + attempt.getString("backupPath")).distinct()
                        failure = PrivilegedFileOperationException(failure?.code ?: "OUTCOME_UNKNOWN", error.message.orEmpty(), failure?.partial ?: false, paths, error)
                    }
                    recordRecovery(metadata, failure.recoveryPaths)
                    if (!failure.partial && failure.code !in listOf("OUTCOME_UNKNOWN", "SAVE_STATE_UNKNOWN")) {
                        metadata.remove("attempt")
                    } else if (failure.code == "SAVE_STATE_UNKNOWN") {
                        // A structured response has finished; unlike a lost reply, there is no remote
                        // call still in flight. Reconciliation is allowed on the next explicit save.
                        attempt.put("deadlineMillis", 0)
                        attempt.put("replyReceived", true)
                    }
                    runCatching { writeMetadata(directory, metadata) }
                    throw checkNotNull(failure)
                }
                val savedDigest = result.getString("digest")
                if (savedDigest != request.getString("contentDigest") || result.getLong("size") != bytes.size.toLong()) {
                    throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", "The save acknowledgement did not match the draft")
                }
                adoptResult(metadata, result, pending.name)
                metadata.put("baselineCharset", charsetName)
                metadata.remove("pending")
                metadata.remove("attempt")
                try { writeMetadata(directory, metadata) } catch (error: Exception) {
                    throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", "The save completed but its local record could not be committed", cause = error)
                }
                cleanupUnreferencedContents(directory, metadata)
            }
        } catch (error: Exception) {
            throw localized(context, error)
        }
    }

    private fun reconcilePreviousSave(context: Context, directory: File, metadata: JSONObject, mode: PrivilegedAccessMode) {
        val attempt = metadata.optJSONObject("attempt") ?: return
        recordRecovery(metadata, listOf(attempt.getString("backupPath")))
        if (!attempt.optBoolean("replyReceived") && completedAttempt(context, mode, attempt) == null) {
            throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", "The earlier save may still be running", recoveryPaths = listOf(attempt.getString("backupPath")))
        }
        val current = PrivilegedFileOperations.snapshot(context, mode, metadata.getString("source"))
        if (current.getString("digest") == attempt.getString("contentDigest") && current.getLong("size") == attempt.getLong("contentSize")) {
            // A deadline alone does not prove completion: a blocked fsync could return late. The
            // remote completion record is written only after commit/rollback and all source IO end.
            // Then compare the full content with this exact, durably recorded attempt.
            adoptResult(metadata, current, attempt.getString("content"))
            attempt.optString("charset").takeIf(String::isNotEmpty)?.let { metadata.put("baselineCharset", it) }
            metadata.put("notice", attempt.getString("backupPath"))
            metadata.remove("attempt")
            writeMetadata(directory, metadata)
        } else if (current.getString("digest") == metadata.getString("digest") && sameIdentity(current.getJSONObject("identity"), metadata.getJSONObject("identity"))) {
            metadata.remove("attempt")
            writeMetadata(directory, metadata)
        } else {
            throw PrivilegedFileOperationException("SOURCE_CHANGED", "The original no longer matches the open document or the pending save")
        }
    }

    private fun assertNoOtherActiveSave(context: Context, ownDirectory: File, source: String, mode: PrivilegedAccessMode) {
        ownDirectory.parentFile!!.listFiles().orEmpty().filter { it != ownDirectory && tokens.matches(it.name) }.forEach { other ->
            val otherMeta = runCatching { readMetadata(other) }.getOrNull() ?: return@forEach
            if (otherMeta.optString("canonicalSource", otherMeta.optString("source")) == source) {
                val attempt = otherMeta.optJSONObject("attempt")
                if (attempt != null && !attempt.optBoolean("replyReceived") && completedAttempt(context, mode, attempt) == null) {
                    throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", context.getString(R.string.text_privileged_edit_unknown))
                }
            }
        }
    }

    private fun completedAttempt(context: Context, mode: PrivilegedAccessMode, attempt: JSONObject): JSONObject? {
        // The remote lease also handles a killed process that could not write result.json. It
        // retires the old transaction before reporting completion, preventing any late execution.
        val result = PrivilegedFileOperations.reconcileSave(context, mode, attempt)
        return result.takeIf { it.optString("transactionId") == attempt.getString("transactionId") && it.optBoolean("completed") }
    }

    private fun adoptResult(metadata: JSONObject, result: JSONObject, baseline: String) {
        metadata.put("identity", result.getJSONObject("identity")).put("digest", result.getString("digest")).put("baseline", baseline)
        val recovery = result.optJSONArray("recoveryPaths")
        recordRecovery(metadata, if (recovery == null) emptyList() else (0 until recovery.length()).map(recovery::getString))
        result.optString("backupPath").takeIf(String::isNotEmpty)?.let { metadata.put("notice", it) }
    }

    private fun recordRecovery(metadata: JSONObject, paths: List<String>) {
        val previous = metadata.optJSONArray("recovery") ?: JSONArray()
        val all = (0 until previous.length()).map(previous::getString).toMutableSet().apply { addAll(paths) }
        metadata.put("recovery", JSONArray(all.toList()))
    }

    internal fun readFile(context: Context, uri: Uri): File {
        val directory = sessionDirectory(context, uri)
        val metadata = readMetadata(directory)
        val name = metadata.optString("pending").ifEmpty { metadata.getString("baseline") }
        check(name.startsWith("content-") && tokens.matches(name.removePrefix("content-")))
        return File(directory, name).also { check(OsConstants.S_ISREG(Os.lstat(it.path).st_mode)) }
    }

    internal fun openReadDescriptor(context: Context, uri: Uri): ParcelFileDescriptor {
        val directory = sessionDirectory(context, uri)
        return synchronized(lock("metadata:${directory.path}")) {
            ParcelFileDescriptor.open(readFile(context, uri), ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    private fun sessionDirectory(context: Context, uri: Uri, create: Boolean = false): File {
        require(isSessionUri(context, uri) && uri.pathSegments.size == 1 && uri.query == null && uri.fragment == null)
        val token = uri.pathSegments.single()
        require(tokens.matches(token))
        val root = File(context.filesDir, DIRECTORY)
        check(root.isDirectory || root.mkdir())
        check(OsConstants.S_ISDIR(Os.lstat(root.path).st_mode))
        return File(root, token).also {
            if (create) check(it.mkdir())
            check(OsConstants.S_ISDIR(Os.lstat(it.path).st_mode))
        }
    }

    private fun authority(context: Context) = "${context.packageName}.privileged-editor"

    private fun cleanupClosedSessions(context: Context) {
        val cutoff = System.currentTimeMillis() - CLEAN_SESSION_RETENTION_MS
        File(context.filesDir, DIRECTORY).listFiles().orEmpty().filter { tokens.matches(it.name) }.forEach { directory ->
            val uri = Uri.Builder().scheme("content").authority(authority(context)).appendPath(directory.name).build()
            if (activeEditors.containsKey(uri.toString())) return@forEach
            val candidate = runCatching { readMetadata(directory) }.getOrNull() ?: return@forEach
            if (candidate.has("pending") || candidate.has("attempt") || candidate.optLong("closedCleanlyAt") !in 1 until cutoff) return@forEach
            synchronized(lock(uri.toString())) {
                if (activeEditors.containsKey(uri.toString())) return@synchronized
                val metadata = runCatching { readMetadata(directory) }.getOrNull() ?: return@synchronized
                val closedAt = metadata.optLong("closedCleanlyAt")
                if (closedAt in 1 until cutoff && !metadata.has("pending") && !metadata.has("attempt")) {
                    // Session directories are flat and UUID-scoped. Never follow a nested link or
                    // remove an unresolved draft merely because it has not been opened recently.
                    directory.listFiles().orEmpty().forEach { it.delete() }
                    directory.delete()
                }
            }
        }
    }

    private fun findRecoverable(context: Context, source: String): Uri? {
        return File(context.filesDir, DIRECTORY).listFiles().orEmpty()
            .filter { tokens.matches(it.name) }
            .sortedByDescending { File(it, "session.json").lastModified() }
            .firstNotNullOfOrNull { directory ->
                val uri = Uri.Builder().scheme("content").authority(authority(context)).appendPath(directory.name).build()
                if (activeEditors.containsKey(uri.toString())) return@firstNotNullOfOrNull null
                val metadata = runCatching { readMetadata(directory) }.getOrNull() ?: return@firstNotNullOfOrNull null
                uri.takeIf {
                    metadata.optString("source") == source && metadata.has("pending") && !metadata.optBoolean("draftResolved") &&
                        activeEditors.putIfAbsent(uri.toString(), 0) == null
                }
            }
    }
    private fun lock(key: String) = locks.getOrPut(key) { Any() }
    private fun readMetadata(directory: File): JSONObject = synchronized(lock("metadata:${directory.path}")) {
        JSONObject(AtomicFile(File(directory, "session.json")).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun writeMetadata(directory: File, metadata: JSONObject) = synchronized(lock("metadata:${directory.path}")) {
        val atomic = AtomicFile(File(directory, "session.json"))
        val output = atomic.startWrite()
        try {
            output.write(metadata.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun cleanupUnreferencedContents(directory: File, metadata: JSONObject) = synchronized(lock("metadata:${directory.path}")) {
        val retained = setOf(metadata.optString("baseline"), metadata.optString("pending"), metadata.optJSONObject("attempt")?.optString("content"))
        directory.listFiles().orEmpty().filter { it.name.startsWith("content-") && tokens.matches(it.name.removePrefix("content-")) && it.name !in retained }
            .forEach { it.delete() }
    }

    private fun sameSnapshot(a: JSONObject, b: JSONObject) =
        a.getString("digest") == b.getString("digest") && sameIdentity(a.getJSONObject("identity"), b.getJSONObject("identity"))

    private fun sameIdentity(a: JSONObject, b: JSONObject) =
        listOf("dev", "ino", "mode", "size", "modified", "changed", "uid", "gid", "links").all { a.optLong(it, -1) == b.optLong(it, -1) }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun localized(context: Context, error: Exception): PrivilegedFileOperationException {
        val source = error as? PrivilegedFileOperationException
        val code = source?.code ?: "IO"
        val message = when (code) {
            "SOURCE_CHANGED", "NOT_FOUND" -> context.getString(R.string.text_privileged_edit_conflict)
            "ACCESS_UNAVAILABLE", "PERMISSION_DENIED" -> context.getString(R.string.text_privileged_edit_permission)
            "OUTCOME_UNKNOWN", "SAVE_STATE_UNKNOWN", "TRANSACTION_BUSY" -> context.getString(R.string.text_privileged_edit_unknown)
            "UNSUPPORTED_TYPE", "UNSUPPORTED_SAVE", "UNSUPPORTED_METADATA", "UNSUPPORTED_REPLACE", "UNSUPPORTED_HARD_LINK", "UNSUPPORTED_LOCK" -> context.getString(R.string.text_privileged_edit_unsupported)
            "READ_ONLY" -> context.getString(R.string.text_explorer_privileged_read_only_storage)
            "NO_SPACE" -> context.getString(R.string.text_explorer_privileged_no_space)
            else -> context.getString(R.string.text_privileged_edit_failed, error.message ?: error.javaClass.simpleName)
        }
        val paths = source?.recoveryPaths.orEmpty()
        val detail = if (paths.isEmpty()) message else message + "\n\n" + context.getString(R.string.text_privileged_edit_recovery, paths.joinToString("\n"))
        return PrivilegedFileOperationException(code, detail, source?.partial ?: false, paths, error)
    }
}
