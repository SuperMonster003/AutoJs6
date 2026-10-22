package org.autojs.autojs.model.explorer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optArray
import org.autojs.autojs.core.pref.optBoolean
import org.autojs.autojs.core.pref.optLong
import org.autojs.autojs.core.pref.optObject
import org.autojs.autojs.core.pref.optString
import org.autojs.autojs6.R
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Durable editor documents whose bytes are private to this app. Each opening has its own baseline;
 * editing a second window must never silently rebase the first window onto someone else's save.
 * The provider is read-only. Only [save] may conditionally replace an authorized original.
 * The session metadata is the JSON file store `JsonFileStores.PRIVILEGED_EDIT_SESSIONS`, kept as plain values of
 * `SettingsJson` (settings P5.1 / P5.3); the requests and results of [PrivilegedFileOperations] stay org.json objects
 * only at the process boundary.
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
        if (!metadata.containsKey("pending") && !metadata.containsKey("attempt")) {
            metadata["closedCleanlyAt"] = System.currentTimeMillis()
            writeMetadata(directory, metadata)
        }
    }

    /** Explicit discard/Save as resolves the draft but never erases an uncertain remote request. */
    fun discardDraft(context: Context, uri: Uri) = synchronized(lock(uri.toString())) {
        val directory = sessionDirectory(context, uri)
        val metadata = readMetadata(directory)
        metadata.remove("pending")
        metadata["draftResolved"] = true
        metadata["closedCleanlyAt"] = System.currentTimeMillis()
        if (metadata.containsKey("baselineCharset")) metadata.put("charset", metadata.requireString("baselineCharset")) else metadata.remove("charset")
        writeMetadata(directory, metadata)
        cleanupUnreferencedContents(directory, metadata)
    }

    fun isSessionUri(context: Context, uri: Uri): Boolean =
        uri.scheme == "content" && uri.authority == authority(context)

    fun create(context: Context, mode: PrivilegedAccessMode, path: String): Uri {
        PrivilegedBackupManager.ensureImported(context)
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
            val before = currentSnapshot(context, mode, path)
            preview = PrivilegedFileOperations.materialize(context, mode, path)
            val digest = digest(preview)
            val after = currentSnapshot(context, mode, path)
            if (!sameSnapshot(before, after) || digest != before.requireString("digest")) {
                throw PrivilegedFileOperationException("SOURCE_CHANGED", "File changed while opening")
            }
            val baseline = File(directory, "content-${UUID.randomUUID()}")
            preview.inputStream().use { input ->
                baseline.outputStream().use { output -> input.copyTo(output); output.fd.sync() }
            }
            writeMetadata(directory, newMetadata(path, mode, before, digest, baseline.name))
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
        runCatching { readMetadata(sessionDirectory(context, uri)).requireString("source") }.getOrNull()

    fun hasPendingDraft(context: Context, uri: Uri): Boolean =
        runCatching { readMetadata(sessionDirectory(context, uri)).containsKey("pending") }.getOrDefault(false)

    /** Retain the chosen charset across failed saves and process recreation, including legacy text. */
    fun pendingCharset(context: Context, uri: Uri): Charset? = runCatching {
        readMetadata(sessionDirectory(context, uri)).optString("charset").takeIf(String::isNotEmpty)?.let(Charset::forName)
    }.getOrNull()

    fun accessMode(context: Context, uri: Uri): PrivilegedAccessMode =
        PrivilegedAccessMode.valueOf(readMetadata(sessionDirectory(context, uri)).requireString("mode"))

    fun changeAccessMode(context: Context, uri: Uri, mode: PrivilegedAccessMode) = synchronized(lock(uri.toString())) {
        val directory = sessionDirectory(context, uri)
        writeMetadata(directory, readMetadata(directory).apply { put("mode", mode.name) })
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
        val path = metadata.requireString("source")
        try {
            Charset.forName(charsetName)
            if (bytes.size > MAX_BYTES || bytes.size > directory.usableSpace - 32L * 1024 * 1024) {
                throw PrivilegedFileOperationException("NO_SPACE", "Not enough space for an editing draft")
            }
            // Persist before waiting for another editor's save, IPC, or original-file validation.
            // Permission settings may kill the app even while this session is waiting for its turn.
            val pending = File(directory, "content-${UUID.randomUUID()}")
            pending.outputStream().use { it.write(bytes); it.fd.sync() }
            metadata["pending"] = pending.name
            metadata["charset"] = charsetName
            metadata["hadBom"] = hadBom
            metadata.remove("draftResolved")
            writeMetadata(directory, metadata)
            cleanupUnreferencedContents(directory, metadata)
            synchronized(lock("source:${metadata.optString("canonicalSource", path)}")) sourceSave@{
                val mode = PrivilegedAccessMode.valueOf(metadata.requireString("mode"))
                assertNoOtherActiveSave(context, directory, metadata.optString("canonicalSource", path), mode)
                reconcilePreviousSave(context, directory, metadata, mode)
                metadata = readMetadata(directory)
                val contentDigest = digest(bytes)
                if (contentDigest == metadata.requireString("digest")) {
                    // Repeated Ctrl+S still checks for external changes, but creates no new version.
                    val current = currentSnapshot(context, mode, path)
                    if (current.requireString("digest") != contentDigest || !sameIdentity(metadata.requireObject("identity"), current.requireObject("identity"))) {
                        throw PrivilegedFileOperationException("SOURCE_CHANGED", "The original changed after opening")
                    }
                    metadata["baseline"] = pending.name
                    metadata["baselineCharset"] = charsetName
                    metadata.remove("pending")
                    writeMetadata(directory, metadata)
                    cleanupUnreferencedContents(directory, metadata)
                    return@sourceSave
                }
                val transaction = UUID.randomUUID().toString()
                val request = saveRequest(metadata, path, bytes.size, contentDigest, transaction, System.currentTimeMillis() + SAVE_TIMEOUT_MS)
                val attempt = saveAttempt(request, pending.name, charsetName,
                    File(File(metadata.optString("canonicalSource", path)).parentFile, ".autojs6-save-$transaction/original").path)
                metadata["attempt"] = attempt
                writeMetadata(directory, metadata)
                PrivilegedBackupManager.registerAttempt(context, mode, request, metadata.optString("canonicalSource", path))
                val result = try {
                    // The request and the result are org.json objects only at the process boundary (settings P5.3).
                    SettingsJson.normalizeObject(PrivilegedFileOperations.save(context, mode, SettingsJson.toOrgObject(request), pending))
                } catch (error: Exception) {
                    runCatching { PrivilegedBackupManager.recordFailure(context, transaction, error) }
                    var failure = error as? PrivilegedFileOperationException
                    if (failure == null || failure.code == "OUTCOME_UNKNOWN" || failure.partial) {
                        val paths = (failure?.recoveryPaths.orEmpty() + attempt.requireString("backupPath")).distinct()
                        failure = PrivilegedFileOperationException(failure?.code ?: "OUTCOME_UNKNOWN", error.message.orEmpty(), failure?.partial ?: false, paths, error)
                    }
                    recordRecovery(metadata, failure.recoveryPaths)
                    if (!failure.partial && failure.code !in listOf("OUTCOME_UNKNOWN", "SAVE_STATE_UNKNOWN")) {
                        metadata.remove("attempt")
                    } else if (failure.code == "SAVE_STATE_UNKNOWN") {
                        // A structured response has finished; unlike a lost reply, there is no remote
                        // call still in flight. Reconciliation is allowed on the next explicit save.
                        attempt["deadlineMillis"] = 0
                        attempt["replyReceived"] = true
                    }
                    runCatching { writeMetadata(directory, metadata) }
                    throw checkNotNull(failure)
                }
                val savedDigest = result.requireString("digest")
                if (savedDigest != request.requireString("contentDigest") || result.requireLong("size") != bytes.size.toLong()) {
                    throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", "The save acknowledgement did not match the draft")
                }
                adoptResult(metadata, result, pending.name)
                metadata["baselineCharset"] = charsetName
                metadata.remove("pending")
                metadata.remove("attempt")
                try { writeMetadata(directory, metadata) } catch (error: Exception) {
                    throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", "The save completed but its local record could not be committed", cause = error)
                }
                // The original is already confirmed saved. A catalog/retention problem must not
                // report that write as failed; the pre-dispatch record can be inspected later.
                runCatching { PrivilegedBackupManager.recordResult(context, transaction, result) }
                PrivilegedBackupManager.scheduleCleanup(context, metadata.optString("canonicalSource", path))
                cleanupUnreferencedContents(directory, metadata)
            }
        } catch (error: Exception) {
            throw localized(context, error)
        }
    }

    private fun reconcilePreviousSave(context: Context, directory: File, metadata: MutableMap<String, Any?>, mode: PrivilegedAccessMode) {
        val attempt = metadata.optObject("attempt") ?: return
        recordRecovery(metadata, listOf(attempt.requireString("backupPath")))
        if (!attempt.optBoolean("replyReceived") && completedAttempt(context, mode, attempt) == null) {
            throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", "The earlier save may still be running", recoveryPaths = listOf(attempt.requireString("backupPath")))
        }
        val current = currentSnapshot(context, mode, metadata.requireString("source"))
        if (current.requireString("digest") == attempt.requireString("contentDigest") && current.requireLong("size") == attempt.requireLong("contentSize")) {
            // A deadline alone does not prove completion: a blocked fsync could return late. The
            // remote completion record is written only after commit/rollback and all source IO end.
            // Then compare the full content with this exact, durably recorded attempt.
            adoptResult(metadata, current, attempt.requireString("content"))
            attempt.optString("charset").takeIf(String::isNotEmpty)?.let { metadata["baselineCharset"] = it }
            metadata["notice"] = attempt.requireString("backupPath")
            metadata.remove("attempt")
            writeMetadata(directory, metadata)
        } else if (current.requireString("digest") == metadata.requireString("digest") && sameIdentity(current.requireObject("identity"), metadata.requireObject("identity"))) {
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
                val attempt = otherMeta.optObject("attempt")
                if (attempt != null && !attempt.optBoolean("replyReceived") && completedAttempt(context, mode, attempt) == null) {
                    throw PrivilegedFileOperationException("OUTCOME_UNKNOWN", context.getString(R.string.text_privileged_edit_unknown))
                }
            }
        }
    }

    private fun completedAttempt(context: Context, mode: PrivilegedAccessMode, attempt: Map<String, Any?>): Map<String, Any?>? {
        // The remote lease also handles a killed process that could not write result.json. It
        // retires the old transaction before reporting completion, preventing any late execution.
        val result = SettingsJson.normalizeObject(PrivilegedFileOperations.reconcileSave(context, mode, SettingsJson.toOrgObject(attempt)))
        return result.takeIf { it.optString("transactionId") == attempt.requireString("transactionId") && it.optBoolean("completed") }
    }

    /**
     * The metadata of a session that [create] has just opened from the snapshot [before] of the original, in the
     * member order that `session.json` has always had.
     */
    internal fun newMetadata(source: String, mode: PrivilegedAccessMode, before: Map<String, Any?>, digest: String, baseline: String): MutableMap<String, Any?> =
        linkedMapOf<String, Any?>(
            "version" to 1, "source" to source, "canonicalSource" to before.requireString("path"), "mode" to mode.name,
            "identity" to before.requireObject("identity"), "digest" to digest,
            "baseline" to baseline, "recovery" to ArrayList<Any?>(),
        )

    /** The request of a save to the original that the session [metadata] has opened, in the member order it has always had. */
    internal fun saveRequest(metadata: Map<String, Any?>, source: String, contentSize: Int, contentDigest: String, transactionId: String, deadlineMillis: Long): MutableMap<String, Any?> =
        linkedMapOf<String, Any?>(
            "operation" to "save", "source" to source,
            "canonicalSource" to metadata.optString("canonicalSource", source),
            "expected" to metadata.requireObject("identity"), "expectedDigest" to metadata.requireString("digest"),
            "contentSize" to contentSize, "contentDigest" to contentDigest,
            "transactionId" to transactionId, "deadlineMillis" to deadlineMillis,
        )

    /** The `attempt` member recorded before a save is sent: a copy of the [request] with the draft, its charset and the backup path. */
    internal fun saveAttempt(request: Map<String, Any?>, content: String, charset: String, backupPath: String): MutableMap<String, Any?> =
        copy(request).apply {
            put("content", content)
            put("charset", charset)
            put("backupPath", backupPath)
        }

    /** Takes the identity, digest, recovery paths and backup notice of a confirmed save [result] (or a snapshot) into [metadata]. */
    internal fun adoptResult(metadata: MutableMap<String, Any?>, result: Map<String, Any?>, baseline: String) {
        metadata["identity"] = result.requireObject("identity")
        metadata["digest"] = result.requireString("digest")
        metadata["baseline"] = baseline
        recordRecovery(metadata, texts(result.optArray("recoveryPaths")))
        result.optString("backupPath").takeIf(String::isNotEmpty)?.let { metadata["notice"] = it }
    }

    /** Adds [paths] to the `recovery` member of [metadata], each path once, in the order they were first recorded. */
    internal fun recordRecovery(metadata: MutableMap<String, Any?>, paths: List<String>) {
        val all = texts(metadata.optArray("recovery")).toMutableSet().apply { addAll(paths) }
        metadata["recovery"] = ArrayList<Any?>(all)
    }

    internal fun readFile(context: Context, uri: Uri): File {
        val directory = sessionDirectory(context, uri)
        val metadata = readMetadata(directory)
        val name = metadata.optString("pending").ifEmpty { metadata.requireString("baseline") }
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

    internal fun legacyBackupMetadata(context: Context): List<Map<String, Any?>> =
        File(context.filesDir, DIRECTORY).listFiles().orEmpty().filter { tokens.matches(it.name) }.mapNotNull { directory ->
            runCatching { legacyBackupMetadata(readMetadata(directory), File(directory, "session.json").lastModified()) }.getOrNull()
        }

    /** The [metadata] of one session as [legacyBackupMetadata] gives it: with the time of its last write as `_sessionUpdatedAt`. */
    internal fun legacyBackupMetadata(metadata: MutableMap<String, Any?>, updatedAt: Long): MutableMap<String, Any?> =
        metadata.apply { put("_sessionUpdatedAt", updatedAt) }

    private fun cleanupClosedSessions(context: Context) {
        val cutoff = System.currentTimeMillis() - CLEAN_SESSION_RETENTION_MS
        File(context.filesDir, DIRECTORY).listFiles().orEmpty().filter { tokens.matches(it.name) }.forEach { directory ->
            val uri = Uri.Builder().scheme("content").authority(authority(context)).appendPath(directory.name).build()
            if (activeEditors.containsKey(uri.toString())) return@forEach
            val candidate = runCatching { readMetadata(directory) }.getOrNull() ?: return@forEach
            if (candidate.containsKey("pending") || candidate.containsKey("attempt") || candidate.optLong("closedCleanlyAt") !in 1 until cutoff) return@forEach
            synchronized(lock(uri.toString())) {
                if (activeEditors.containsKey(uri.toString())) return@synchronized
                val metadata = runCatching { readMetadata(directory) }.getOrNull() ?: return@synchronized
                val closedAt = metadata.optLong("closedCleanlyAt")
                if (closedAt in 1 until cutoff && !metadata.containsKey("pending") && !metadata.containsKey("attempt")) {
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
                    metadata.optString("source") == source && metadata.containsKey("pending") && !metadata.optBoolean("draftResolved") &&
                        activeEditors.putIfAbsent(uri.toString(), 0) == null
                }
            }
    }
    private fun lock(key: String) = locks.getOrPut(key) { Any() }
    private fun readMetadata(directory: File): MutableMap<String, Any?> = synchronized(lock("metadata:${directory.path}")) {
        decodeMetadata(AtomicFile(File(directory, "session.json")).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun writeMetadata(directory: File, metadata: Map<String, Any?>) = synchronized(lock("metadata:${directory.path}")) {
        val atomic = AtomicFile(File(directory, "session.json"))
        val output = atomic.startWrite()
        try {
            output.write(encodeMetadata(metadata).toByteArray(Charsets.UTF_8))
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    internal fun encodeMetadata(metadata: Map<String, Any?>): String = SettingsJson.stringify(metadata)

    internal fun decodeMetadata(text: String): MutableMap<String, Any?> = SettingsJson.parseMap(text)

    private fun cleanupUnreferencedContents(directory: File, metadata: Map<String, Any?>) = synchronized(lock("metadata:${directory.path}")) {
        val retained = setOf(metadata.optString("baseline"), metadata.optString("pending"), metadata.optObject("attempt")?.optString("content"))
        directory.listFiles().orEmpty().filter { it.name.startsWith("content-") && tokens.matches(it.name.removePrefix("content-")) && it.name !in retained }
            .forEach { it.delete() }
    }

    /** A snapshot of the original; the result of [PrivilegedFileOperations.snapshot] is an org.json object only at the process boundary. */
    private fun currentSnapshot(context: Context, mode: PrivilegedAccessMode, path: String): MutableMap<String, Any?> =
        SettingsJson.normalizeObject(PrivilegedFileOperations.snapshot(context, mode, path))

    private fun sameSnapshot(a: Map<String, Any?>, b: Map<String, Any?>) =
        a.requireString("digest") == b.requireString("digest") && sameIdentity(a.requireObject("identity"), b.requireObject("identity"))

    private fun sameIdentity(a: Map<String, Any?>, b: Map<String, Any?>) =
        listOf("dev", "ino", "mode", "size", "modified", "changed", "uid", "gid", "links").all { a.optLong(it, -1) == b.optLong(it, -1) }

    // The members that the code read with org.json's `getString` / `getJSONObject` / `getLong`, which fail for a missing
    // member (and a member of another type) instead of giving a fallback.

    private fun Map<String, Any?>.requireString(key: String): String {
        if (!containsKey(key)) throw SettingsJson.SettingsJsonException("No value for $key")
        return this[key].let { it as? String ?: SettingsJson.stringify(it) }
    }

    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        optObject(key) ?: throw SettingsJson.SettingsJsonException("No object for $key")

    private fun Map<String, Any?>.requireLong(key: String): Long = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
        else -> null
    } ?: throw SettingsJson.SettingsJsonException("No number for $key")

    /** org.json's `getString` of every element: a string as it is, another value as its JSON text. */
    private fun texts(values: List<Any?>?): List<String> = values.orEmpty().map { it as? String ?: SettingsJson.stringify(it) }

    /** A deep copy as `JSONObject(value.toString())` made it. */
    private fun copy(value: Map<String, Any?>): MutableMap<String, Any?> = SettingsJson.parseMap(SettingsJson.stringify(value))

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
