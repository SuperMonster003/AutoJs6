package org.autojs.autojs.model.explorer

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import org.autojs.autojs6.R
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Persistent catalog of this app's edit transactions, including failed and interrupted saves. */
object PrivilegedBackupManager {
    data class RetentionPolicy(val maxVersions: Int = 5, val maxBytes: Long = 64L * 1024 * 1024) {
        init { require(maxVersions in 1..50 && maxBytes in 1..1024L * 1024 * 1024) }
    }
    data class BackupEntry(
        val id: String, val source: String, val backupPath: String, val mode: PrivilegedAccessMode,
        val createdAt: Long, val size: Long, val status: String,
        val canPreview: Boolean, val canRestore: Boolean, val canDelete: Boolean, val message: String,
        internal val snapshot: JSONObject = JSONObject(),
    )
    data class RestorePlan(val entry: BackupEntry, val targetMissing: Boolean, internal val target: JSONObject?)
    data class OperationSummary(val processed: Int, val kept: Int, val failed: Int, val message: String)

    private const val DIRECTORY = "privileged-edit-backups"
    private const val PREFERENCES = "privileged_edit_backups"
    private val idPattern = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private val stateLock = Any()
    private val sourceLocks = ConcurrentHashMap<String, Any>()
    private val scheduled = ConcurrentHashMap.newKeySet<String>()
    private val cleaner = Executors.newSingleThreadExecutor { task -> Thread(task, "PrivilegedBackupRetention").apply { isDaemon = true } }

    fun policy(context: Context): RetentionPolicy {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return runCatching { RetentionPolicy(preferences.getInt("versions", 5), preferences.getLong("bytes", 64L * 1024 * 1024)) }
            .getOrDefault(RetentionPolicy())
    }

    fun setPolicy(context: Context, policy: RetentionPolicy) {
        check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putInt("versions", policy.maxVersions).putLong("bytes", policy.maxBytes).commit())
    }

    /** Called before sending a mutation, so process death cannot orphan its recovery entry. */
    internal fun registerAttempt(context: Context, mode: PrivilegedAccessMode, request: JSONObject, canonicalSource: String = request.getString("source")) {
        importLegacySessions(context)
        val id = request.getString("transactionId")
        write(context, id, JSONObject().put("id", id).put("source", canonicalSource)
            .put("requestedSource", request.getString("source")).put("mode", mode.name)
            .put("createdAt", System.currentTimeMillis()).put("request", JSONObject(request.toString())))
    }

    internal fun recordResult(context: Context, id: String, result: JSONObject) {
        update(context, id) { record ->
            record.put("result", result).put("settled", true)
            result.optString("path").takeIf(String::isNotEmpty)?.let { record.put("source", it) }
            if (result.optBoolean("missingTargetRestored") || result.optBoolean("unchanged")) record.put("removed", true)
            record.remove("error")
        }
    }

    internal fun recordFailure(context: Context, id: String, error: Exception) {
        val failure = error as? PrivilegedFileOperationException
        if (failure != null && !failure.partial && failure.recoveryPaths.none { it.contains(".autojs6-save-$id") } &&
            failure.code !in listOf("OUTCOME_UNKNOWN", "SAVE_STATE_UNKNOWN")) {
            synchronized(stateLock) { recordFile(context, id).delete() }
        } else update(context, id) { it.put("error", failure?.code ?: "OUTCOME_UNKNOWN") }
    }

    internal fun scheduleCleanup(context: Context, source: String) {
        val key = "${context.filesDir.absolutePath}:$source"
        if (!scheduled.add(key)) return
        cleaner.execute {
            // Remove before working: a new save can queue one further pass while this pass runs.
            scheduled.remove(key)
            runCatching { cleanup(context, source) }.onFailure { setCleanupWarning(context, true) }
        }
    }

    internal fun awaitIdle(timeoutSeconds: Long = 60) { cleaner.submit {}.get(timeoutSeconds, TimeUnit.SECONDS) }

    fun cleanupWarning(context: Context): String? = context.getString(R.string.text_privileged_backup_cleanup_failed)
        .takeIf { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean("cleanup_warning", false) }

    private fun setCleanupWarning(context: Context, failed: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean("cleanup_warning", failed).apply()
    }

    fun discoverDirectory(context: Context, mode: PrivilegedAccessMode, directory: String) {
        importLegacySessions(context)
        val response = PrivilegedFileOperations.backup(context, mode, JSONObject().put("operation", "backup_scan").put("source", directory))
        val entries = response.optJSONArray("entries") ?: return
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val id = entry.getString("transactionId")
            val source = entry.optString("source", entry.optString("sourcePath"))
            if (!idPattern.matches(id) || !source.startsWith('/')) continue
            synchronized(stateLock) {
                val existing = read(context, id)
                val record = existing ?: JSONObject().put("id", id).put("source", source)
                    .put("createdAt", entry.optLong("createdAt")).put("mode", mode.name)
                if (entry.optBoolean("tombstone")) record.put("removed", true)
                write(context, id, record)
            }
        }
    }

    fun list(context: Context, sourcePath: String? = null): List<BackupEntry> {
        importLegacySessions(context)
        return records(context).filter { !it.optBoolean("removed") && matchesSource(it, sourcePath) }
            .map { inspect(context, it, false) }
            .filterNot { it.snapshot.optBoolean("tombstone") && !it.snapshot.has("identity") && it.canDelete }
            .sortedWith(compareByDescending<BackupEntry> { it.createdAt }.thenByDescending { it.id })
    }

    fun changeAccessMode(context: Context, entry: BackupEntry, mode: PrivilegedAccessMode) {
        update(context, entry.id) { it.put("mode", mode.name).remove("error") }
    }

    fun view(context: Context, entry: BackupEntry): File = try {
        val current = refresh(context, entry, true)
        checkAvailable(current.canPreview, current)
        val preview = PrivilegedFileOperations.materialize(context, current.mode, current.backupPath)
        try {
            val after = refresh(context, current, true)
            requireSameBackup(current, after)
            val displayFile = File(preview.parentFile, File(current.source).name)
            if (preview.name != displayFile.name && !preview.renameTo(displayFile)) throw java.io.IOException("Unable to name the preview")
            displayFile
        } catch (error: Exception) {
            preview.delete(); preview.parentFile?.delete()
            throw error
        }
    } catch (error: Exception) { throw failure(context, error) }

    fun prepareRestore(context: Context, entry: BackupEntry): RestorePlan = try {
        val current = refresh(context, entry, true)
        checkAvailable(current.canRestore, current)
        val target = try { PrivilegedFileOperations.snapshot(context, current.mode, current.source) }
        catch (error: PrivilegedFileOperationException) {
            if (error.code != "NOT_FOUND") throw error
            val parent = File(current.source).parent ?: throw error
            if (PrivilegedFileOperations.stat(context, current.mode, parent).optString("kind") != "directory") throw error
            null
        }
        RestorePlan(current, target == null, target)
    } catch (error: Exception) { throw failure(context, error, restoring = true) }

    fun prepareDelete(context: Context, entry: BackupEntry): BackupEntry = try {
        refresh(context, entry, true).also { checkAvailable(it.canDelete, it) }
    } catch (error: Exception) { throw failure(context, error) }

    fun restore(context: Context, plan: RestorePlan): OperationSummary = synchronized(sourceLock(context, plan.entry.source)) {
        val entry = plan.entry
        val id = UUID.randomUUID().toString()
        val request = baseRequest("backup_restore", entry)
            .put("restoreTransactionId", id).put("expectedMissing", plan.targetMissing)
            .put("backupExpected", entry.snapshot.getJSONObject("identity")).put("backupDigest", entry.snapshot.getString("digest"))
            .apply { plan.target?.let { put("expected", it.getJSONObject("identity")); put("expectedDigest", it.getString("digest")) } }
        try {
            val mode = currentMode(context, entry)
            registerAttempt(context, mode, JSONObject(request.toString()).put("transactionId", id), entry.source)
            val result = try { PrivilegedFileOperations.backup(context, mode, request) }
            catch (error: Exception) { runCatching { recordFailure(context, id, error) }; throw error }
            runCatching { recordResult(context, id, result) }
            scheduleCleanup(context, entry.source)
            summary(context, 1, 0, emptyList())
        } catch (error: Exception) { throw failure(context, error, restoring = true) }
    }

    fun delete(context: Context, entry: BackupEntry): OperationSummary = synchronized(sourceLock(context, entry.source)) {
        try {
            // The user's confirmation refers to the listed snapshot, not a silently newer file.
            val current = refresh(context, entry, true)
            if (entry.snapshot.has("identity") || current.snapshot.has("identity")) {
                requireSameBackup(entry, current, compareDigest = entry.snapshot.has("digest"))
            }
            checkAvailable(current.canDelete, current)
            val result = deleteVerified(context, current, automatic = false)
            summary(context, 1, 0, if (result.optBoolean("cleanupPending")) listOf(context.getString(R.string.text_privileged_backup_cleanup_failed)) else emptyList())
        } catch (error: Exception) { throw failure(context, error) }
    }

    fun cleanup(context: Context, sourcePath: String? = null): OperationSummary {
        importLegacySessions(context)
        var processed = 0
        var kept = 0
        val errors = mutableListOf<String>()
        val sources = records(context).filter { matchesSource(it, sourcePath) }.map { it.getString("source") }.distinct()
        for (source in sources) synchronized(sourceLock(context, source)) {
            val entries = list(context, source)
            entries.firstOrNull { it.status == "unavailable" }?.let { errors += "$source: ${it.message}" }
            val policy = policy(context)
            val expired = PrivilegedBackupRetention.expired(entries.map {
                PrivilegedBackupRetention.Version(it.id, it.source, it.createdAt, it.size,
                    it.status == "available" && it.snapshot.optBoolean("autoClean"), it.canRestore)
            }, policy.maxVersions, policy.maxBytes)
            for (entry in entries) {
                if (entry.id !in expired) { kept++; continue }
                try {
                    deleteVerified(context, entry, automatic = true)
                    processed++
                } catch (error: Exception) {
                    errors += "${entry.source}: ${failure(context, error).message}"
                    runCatching { update(context, entry.id) { it.put("error", (error as? PrivilegedFileOperationException)?.code ?: "IO") } }
                }
            }
            // Small completion fences outlive their payload while an old request can still arrive.
            // The backend removes them only after its deadline/lease checks prove that safe.
            records(context).filter { it.optBoolean("removed") && it.optString("source") == source }.forEach { record ->
                runCatching {
                    val result = PrivilegedFileOperations.backup(context, mode(record), JSONObject()
                        .put("operation", "backup_delete").put("source", source).put("transactionId", record.getString("id")).put("auto", true))
                    if (result.optBoolean("retired")) synchronized(stateLock) { recordFile(context, record.getString("id")).delete() }
                }
            }
        }
        setCleanupWarning(context, errors.isNotEmpty())
        return summary(context, processed, kept, errors)
    }

    private fun deleteVerified(context: Context, entry: BackupEntry, automatic: Boolean): JSONObject {
        val request = baseRequest("backup_delete", entry).put("auto", automatic)
        entry.snapshot.optJSONObject("identity")?.let { request.put("backupExpected", it) }
        entry.snapshot.optString("digest").takeIf(String::isNotEmpty)?.let { request.put("backupDigest", it) }
        val result = PrivilegedFileOperations.backup(context, currentMode(context, entry), request)
        if (result.optBoolean("retired")) synchronized(stateLock) { recordFile(context, entry.id).delete() }
        else update(context, entry.id) { it.put("removed", result.optBoolean("tombstone", true)).put("cleanup", result).remove("error") }
        return result
    }

    private fun inspect(context: Context, record: JSONObject, verifyContent: Boolean): BackupEntry {
        val id = record.getString("id")
        val source = record.getString("source")
        val mode = mode(record)
        return try {
            val result = PrivilegedFileOperations.backup(context, mode, JSONObject().put("operation", "backup_inspect")
                .put("source", source).put("transactionId", id).put("verifyContent", verifyContent))
            var status = result.getString("status")
            if (result.optBoolean("tombstone") && !result.has("identity") && result.optBoolean("canDelete")) {
                update(context, id) { it.put("removed", true) }
            }
            if (!verifyContent && record.optString("error") in listOf("SOURCE_CHANGED", "BACKUP_CHANGED") && status == "available") status = "changed"
            BackupEntry(id, source, result.optString("backupPath", backupPath(source, id)), mode,
                result.optLong("createdAt", record.optLong("createdAt")), result.optLong("size"), status,
                result.optBoolean("canPreview", result.has("identity")), result.optBoolean("canRestore"), result.optBoolean("canDelete"),
                statusMessage(context, status), result)
        } catch (error: Exception) {
            BackupEntry(id, source, backupPath(source, id), mode, record.optLong("createdAt"), record.optJSONObject("result")?.optJSONObject("backupIdentity")?.optLong("size") ?: 0,
                "unavailable", false, false, false, statusMessage(context, "unavailable") + "\n" + (error.message ?: ""))
        }
    }

    private fun refresh(context: Context, entry: BackupEntry, verify: Boolean): BackupEntry =
        inspect(context, read(context, entry.id) ?: throw PrivilegedFileOperationException("NOT_FOUND", "Backup entry no longer exists"), verify)

    private fun requireSameBackup(before: BackupEntry, after: BackupEntry, compareDigest: Boolean = true) {
        val old = before.snapshot.optJSONObject("identity")
        val actual = after.snapshot.optJSONObject("identity")
        if (old == null || actual == null || old.keys().asSequence().any { old.opt(it) != actual.opt(it) } ||
            compareDigest && before.snapshot.optString("digest") != after.snapshot.optString("digest")) {
            throw PrivilegedFileOperationException("SOURCE_CHANGED", "The selected backup changed")
        }
    }

    private fun checkAvailable(available: Boolean, entry: BackupEntry) {
        if (!available) throw PrivilegedFileOperationException(
            if (entry.status == "unavailable") "ACCESS_UNAVAILABLE" else if (entry.status == "busy") "TRANSACTION_BUSY" else "SOURCE_CHANGED", entry.message)
    }

    private fun baseRequest(operation: String, entry: BackupEntry) = JSONObject()
        .put("operation", operation).put("source", entry.source).put("transactionId", entry.id)
    private fun backupPath(source: String, id: String) = File(File(source).parentFile, ".autojs6-save-$id/original").path
    private fun mode(record: JSONObject) = PrivilegedAccessMode.valueOf(record.getString("mode"))
    private fun currentMode(context: Context, entry: BackupEntry) = read(context, entry.id)?.let(::mode) ?: entry.mode
    private fun normalized(path: String) = runCatching { File(path).canonicalPath }.getOrDefault(path)
    private fun matchesSource(record: JSONObject, source: String?) = source == null ||
        normalized(record.optString("source")) == normalized(source) || normalized(record.optString("requestedSource")) == normalized(source)
    private fun sourceLock(context: Context, source: String) = sourceLocks.getOrPut("${context.filesDir.absolutePath}:$source") { Any() }

    private fun statusMessage(context: Context, status: String) = context.getString(when (status) {
        "available" -> R.string.text_privileged_backup_status_available
        "changed" -> R.string.text_privileged_backup_status_changed
        "recovery" -> R.string.text_privileged_backup_status_recovery
        "busy" -> R.string.text_privileged_backup_status_busy
        "missing" -> R.string.text_privileged_backup_status_missing
        else -> R.string.text_privileged_backup_status_unavailable
    })

    private fun failure(context: Context, error: Exception, restoring: Boolean = false): PrivilegedFileOperationException {
        val original = error as? PrivilegedFileOperationException
        val detail = when (original?.code) {
            "ACCESS_UNAVAILABLE", "PERMISSION_DENIED" -> context.getString(R.string.text_privileged_backup_status_unavailable)
            "SOURCE_CHANGED", "DESTINATION_EXISTS" -> context.getString(if (restoring) R.string.text_privileged_edit_conflict else R.string.text_privileged_backup_changed_warning)
            "BACKUP_CHANGED" -> context.getString(R.string.text_privileged_backup_changed_warning)
            "OUTCOME_UNKNOWN", "SAVE_STATE_UNKNOWN", "TRANSACTION_BUSY" -> context.getString(R.string.text_privileged_edit_unknown)
            else -> error.message ?: error.javaClass.simpleName
        }
        return PrivilegedFileOperationException(original?.code ?: "IO", context.getString(R.string.text_privileged_backup_operation_failed, detail),
            original?.partial ?: false, original?.recoveryPaths.orEmpty(), error)
    }

    private fun summary(context: Context, processed: Int, kept: Int, errors: List<String>) = OperationSummary(processed, kept, errors.size,
        context.getString(R.string.text_privileged_backup_summary, processed, kept, errors.size) +
            if (errors.isEmpty()) "" else "\n\n" + errors.take(5).joinToString("\n"))

    private fun root(context: Context) = File(context.filesDir, DIRECTORY).apply {
        check(isDirectory || mkdir())
        check(OsConstants.S_ISDIR(Os.lstat(path).st_mode))
    }
    private fun recordFile(context: Context, id: String): AtomicFile {
        require(idPattern.matches(id))
        return AtomicFile(File(root(context), "$id.json"))
    }
    private fun read(context: Context, id: String): JSONObject? = synchronized(stateLock) {
        runCatching { recordFile(context, id).openRead().bufferedReader().use { JSONObject(it.readText()) } }.getOrNull()
    }
    private fun write(context: Context, id: String, value: JSONObject) = synchronized(stateLock) {
        val atomic = recordFile(context, id)
        val stream = atomic.startWrite()
        try { stream.write(value.toString().toByteArray(Charsets.UTF_8)); stream.fd.sync(); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
    private fun update(context: Context, id: String, change: (JSONObject) -> Unit) = synchronized(stateLock) {
        val record = read(context, id) ?: return@synchronized
        change(record)
        write(context, id, record)
    }
    private fun records(context: Context): List<JSONObject> = synchronized(stateLock) {
        root(context).listFiles().orEmpty().filter { it.extension == "json" && idPattern.matches(it.nameWithoutExtension) }
            .mapNotNull { read(context, it.nameWithoutExtension) }
    }

    internal fun ensureImported(context: Context) = importLegacySessions(context)

    /** Old sessions retained paths but had no central catalog. Migration never reads file contents. */
    private fun importLegacySessions(context: Context) = synchronized(stateLock) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean("imported_sessions_v1", false)) return@synchronized
        PrivilegedEditSessions.legacyBackupMetadata(context).forEach { session ->
            val source = session.optString("canonicalSource", session.optString("source"))
            val mode = session.optString("mode")
            if (!source.startsWith('/') || mode !in PrivilegedAccessMode.entries.map { it.name }) return@forEach
            val paths = session.optJSONArray("recovery")
            val ids = if (paths == null) mutableSetOf() else (0 until paths.length()).mapNotNull { index ->
                val parts = paths.optString(index).split('/')
                parts.lastOrNull { it.startsWith(".autojs6-save-") }?.removePrefix(".autojs6-save-")?.takeIf(idPattern::matches)
            }.toMutableSet()
            session.optJSONObject("attempt")?.optString("transactionId")?.takeIf(idPattern::matches)?.let(ids::add)
            ids.forEach { id ->
                if (read(context, id) == null) write(context, id, JSONObject().put("id", id).put("source", source).put("mode", mode)
                    .put("createdAt", session.optLong("_sessionUpdatedAt")).put("legacy", true))
            }
        }
        check(preferences.edit().putBoolean("imported_sessions_v1", true).commit())
    }
}
