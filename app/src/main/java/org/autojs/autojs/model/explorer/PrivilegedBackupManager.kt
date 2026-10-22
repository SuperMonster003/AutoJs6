package org.autojs.autojs.model.explorer

import android.content.Context
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Persistent catalog of this app's edit transactions, including failed and interrupted saves. The records are the JSON
 * file store `JsonFileStores.PRIVILEGED_EDIT_BACKUP_RECORDS`, kept as plain values of `SettingsJson` (settings P5.1 /
 * P5.3); the requests and results of [PrivilegedFileOperations] stay org.json objects only at the process boundary.
 */
object PrivilegedBackupManager {
    data class RetentionPolicy(val maxVersions: Int = 5, val maxBytes: Long = 64L * 1024 * 1024) {
        init { require(maxVersions in 1..50 && maxBytes in 1..1024L * 1024 * 1024) }
    }
    data class BackupEntry(
        val id: String, val source: String, val backupPath: String, val mode: PrivilegedAccessMode,
        val createdAt: Long, val size: Long, val status: String,
        val canPreview: Boolean, val canRestore: Boolean, val canDelete: Boolean, val message: String,
        internal val snapshot: Map<String, Any?> = emptyMap(),
    )
    data class RestorePlan(val entry: BackupEntry, val targetMissing: Boolean, internal val target: Map<String, Any?>?)
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
    internal fun registerAttempt(context: Context, mode: PrivilegedAccessMode, request: Map<String, Any?>, canonicalSource: String = request.requireString("source")) {
        importLegacySessions(context)
        val id = request.requireString("transactionId")
        write(context, id, attemptRecord(id, request, canonicalSource, mode, System.currentTimeMillis()))
    }

    internal fun recordResult(context: Context, id: String, result: Map<String, Any?>) {
        update(context, id) { record -> settle(record, result) }
    }

    internal fun recordFailure(context: Context, id: String, error: Exception) {
        val failure = error as? PrivilegedFileOperationException
        if (failure != null && !failure.partial && failure.recoveryPaths.none { it.contains(".autojs6-save-$id") } &&
            failure.code !in listOf("OUTCOME_UNKNOWN", "SAVE_STATE_UNKNOWN")) {
            synchronized(stateLock) { recordFile(context, id).delete() }
        } else update(context, id) { it["error"] = failure?.code ?: "OUTCOME_UNKNOWN" }
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
        val response = backupOperation(context, mode, linkedMapOf<String, Any?>("operation" to "backup_scan", "source" to directory))
        val entries = response.optArray("entries") ?: return
        for (element in entries) {
            @Suppress("UNCHECKED_CAST")
            val entry = element as? Map<String, Any?> ?: throw SettingsJson.SettingsJsonException("Backup scan entry is not an object")
            val id = entry.requireString("transactionId")
            val source = entry.optString("source", entry.optString("sourcePath"))
            if (!idPattern.matches(id) || !source.startsWith('/')) continue
            synchronized(stateLock) {
                write(context, id, discoveredRecord(read(context, id), id, source, entry, mode))
            }
        }
    }

    fun list(context: Context, sourcePath: String? = null): List<BackupEntry> {
        importLegacySessions(context)
        return records(context).filter { !it.optBoolean("removed") && matchesSource(it, sourcePath) }
            .map { inspect(context, it, false) }
            .filterNot { it.snapshot.optBoolean("tombstone") && !it.snapshot.containsKey("identity") && it.canDelete }
            .sortedWith(compareByDescending<BackupEntry> { it.createdAt }.thenByDescending { it.id })
    }

    fun changeAccessMode(context: Context, entry: BackupEntry, mode: PrivilegedAccessMode) {
        update(context, entry.id) {
            it["mode"] = mode.name
            it.remove("error")
        }
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
        val target = try { currentSnapshot(context, current.mode, current.source) }
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
        val request = baseRequest("backup_restore", entry).apply {
            put("restoreTransactionId", id)
            put("expectedMissing", plan.targetMissing)
            put("backupExpected", entry.snapshot.requireObject("identity"))
            put("backupDigest", entry.snapshot.requireString("digest"))
            plan.target?.let { put("expected", it.requireObject("identity")); put("expectedDigest", it.requireString("digest")) }
        }
        try {
            val mode = currentMode(context, entry)
            registerAttempt(context, mode, LinkedHashMap(request).apply { put("transactionId", id) }, entry.source)
            val result = try { backupOperation(context, mode, request) }
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
            if (entry.snapshot.containsKey("identity") || current.snapshot.containsKey("identity")) {
                requireSameBackup(entry, current, compareDigest = entry.snapshot.containsKey("digest"))
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
        val sources = records(context).filter { matchesSource(it, sourcePath) }.map { it.requireString("source") }.distinct()
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
                    runCatching { update(context, entry.id) { it["error"] = (error as? PrivilegedFileOperationException)?.code ?: "IO" } }
                }
            }
            // Small completion fences outlive their payload while an old request can still arrive.
            // The backend removes them only after its deadline/lease checks prove that safe.
            records(context).filter { it.optBoolean("removed") && it.optString("source") == source }.forEach { record ->
                runCatching {
                    val result = backupOperation(context, mode(record), linkedMapOf<String, Any?>(
                        "operation" to "backup_delete", "source" to source, "transactionId" to record.requireString("id"), "auto" to true))
                    if (result.optBoolean("retired")) synchronized(stateLock) { recordFile(context, record.requireString("id")).delete() }
                }
            }
        }
        setCleanupWarning(context, errors.isNotEmpty())
        return summary(context, processed, kept, errors)
    }

    private fun deleteVerified(context: Context, entry: BackupEntry, automatic: Boolean): Map<String, Any?> {
        val request = baseRequest("backup_delete", entry).apply { put("auto", automatic) }
        entry.snapshot.optObject("identity")?.let { request["backupExpected"] = it }
        entry.snapshot.optString("digest").takeIf(String::isNotEmpty)?.let { request["backupDigest"] = it }
        val result = backupOperation(context, currentMode(context, entry), request)
        if (result.optBoolean("retired")) synchronized(stateLock) { recordFile(context, entry.id).delete() }
        else update(context, entry.id) {
            it["removed"] = result.optBoolean("tombstone", true)
            it["cleanup"] = result
            it.remove("error")
        }
        return result
    }

    private fun inspect(context: Context, record: Map<String, Any?>, verifyContent: Boolean): BackupEntry {
        val id = record.requireString("id")
        val source = record.requireString("source")
        val mode = mode(record)
        return try {
            val result = backupOperation(context, mode, linkedMapOf<String, Any?>(
                "operation" to "backup_inspect", "source" to source, "transactionId" to id, "verifyContent" to verifyContent))
            var status = result.requireString("status")
            if (result.optBoolean("tombstone") && !result.containsKey("identity") && result.optBoolean("canDelete")) {
                update(context, id) { it["removed"] = true }
            }
            if (!verifyContent && record.optString("error") in listOf("SOURCE_CHANGED", "BACKUP_CHANGED") && status == "available") status = "changed"
            BackupEntry(id, source, result.optString("backupPath", backupPath(source, id)), mode,
                result.optLong("createdAt", record.optLong("createdAt")), result.optLong("size"), status,
                result.optBoolean("canPreview", result.containsKey("identity")), result.optBoolean("canRestore"), result.optBoolean("canDelete"),
                statusMessage(context, status), result)
        } catch (error: Exception) {
            BackupEntry(id, source, backupPath(source, id), mode, record.optLong("createdAt"), record.optObject("result")?.optObject("backupIdentity")?.optLong("size") ?: 0,
                "unavailable", false, false, false, statusMessage(context, "unavailable") + "\n" + (error.message ?: ""))
        }
    }

    private fun refresh(context: Context, entry: BackupEntry, verify: Boolean): BackupEntry =
        inspect(context, read(context, entry.id) ?: throw PrivilegedFileOperationException("NOT_FOUND", "Backup entry no longer exists"), verify)

    private fun requireSameBackup(before: BackupEntry, after: BackupEntry, compareDigest: Boolean = true) {
        val old = before.snapshot.optObject("identity")
        val actual = after.snapshot.optObject("identity")
        if (old == null || actual == null || old.keys.any { !actual.containsKey(it) || old[it] != actual[it] } ||
            compareDigest && before.snapshot.optString("digest") != after.snapshot.optString("digest")) {
            throw PrivilegedFileOperationException("SOURCE_CHANGED", "The selected backup changed")
        }
    }

    private fun checkAvailable(available: Boolean, entry: BackupEntry) {
        if (!available) throw PrivilegedFileOperationException(
            if (entry.status == "unavailable") "ACCESS_UNAVAILABLE" else if (entry.status == "busy") "TRANSACTION_BUSY" else "SOURCE_CHANGED", entry.message)
    }

    private fun baseRequest(operation: String, entry: BackupEntry) =
        linkedMapOf<String, Any?>("operation" to operation, "source" to entry.source, "transactionId" to entry.id)
    private fun backupPath(source: String, id: String) = File(File(source).parentFile, ".autojs6-save-$id/original").path
    private fun mode(record: Map<String, Any?>) = PrivilegedAccessMode.valueOf(record.requireString("mode"))
    private fun currentMode(context: Context, entry: BackupEntry) = read(context, entry.id)?.let(::mode) ?: entry.mode
    private fun normalized(path: String) = runCatching { File(path).canonicalPath }.getOrDefault(path)
    private fun matchesSource(record: Map<String, Any?>, source: String?) = source == null ||
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
    private fun read(context: Context, id: String): MutableMap<String, Any?>? = synchronized(stateLock) {
        runCatching { recordFile(context, id).openRead().bufferedReader().use { decodeRecord(it.readText()) } }.getOrNull()
    }
    private fun write(context: Context, id: String, value: Map<String, Any?>) = synchronized(stateLock) {
        val atomic = recordFile(context, id)
        val stream = atomic.startWrite()
        try { stream.write(encodeRecord(value).toByteArray(Charsets.UTF_8)); stream.fd.sync(); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
    private fun update(context: Context, id: String, change: (MutableMap<String, Any?>) -> Unit) = synchronized(stateLock) {
        val record = read(context, id) ?: return@synchronized
        change(record)
        write(context, id, record)
    }
    private fun records(context: Context): List<MutableMap<String, Any?>> = synchronized(stateLock) {
        root(context).listFiles().orEmpty().filter { it.extension == "json" && idPattern.matches(it.nameWithoutExtension) }
            .mapNotNull { read(context, it.nameWithoutExtension) }
    }

    internal fun encodeRecord(record: Map<String, Any?>): String = SettingsJson.stringify(record)

    internal fun decodeRecord(text: String): MutableMap<String, Any?> = SettingsJson.parseMap(text)

    /** The record that [registerAttempt] writes before the mutation [request] is sent, in the member order it has always had. */
    internal fun attemptRecord(id: String, request: Map<String, Any?>, canonicalSource: String, mode: PrivilegedAccessMode, createdAt: Long): MutableMap<String, Any?> =
        linkedMapOf<String, Any?>(
            "id" to id, "source" to canonicalSource, "requestedSource" to request.requireString("source"),
            "mode" to mode.name, "createdAt" to createdAt, "request" to copy(request),
        )

    /** Takes the confirmed [result] of a mutation into its [record], as [recordResult] does. */
    internal fun settle(record: MutableMap<String, Any?>, result: Map<String, Any?>) {
        record["result"] = result
        record["settled"] = true
        result.optString("path").takeIf(String::isNotEmpty)?.let { record["source"] = it }
        if (result.optBoolean("missingTargetRestored") || result.optBoolean("unchanged")) record["removed"] = true
        record.remove("error")
    }

    /** The record of a scanned backup [entry]: the [existing] record if there is one, marked removed for a tombstone. */
    internal fun discoveredRecord(existing: MutableMap<String, Any?>?, id: String, source: String, entry: Map<String, Any?>, mode: PrivilegedAccessMode): MutableMap<String, Any?> =
        (existing ?: linkedMapOf<String, Any?>("id" to id, "source" to source, "createdAt" to entry.optLong("createdAt"), "mode" to mode.name)).apply {
            if (entry.optBoolean("tombstone")) put("removed", true)
        }

    internal fun ensureImported(context: Context) = importLegacySessions(context)

    /** Old sessions retained paths but had no central catalog. Migration never reads file contents. */
    private fun importLegacySessions(context: Context) = synchronized(stateLock) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean("imported_sessions_v1", false)) return@synchronized
        PrivilegedEditSessions.legacyBackupMetadata(context).forEach { session ->
            legacyRecords(session).forEach { (id, record) -> if (read(context, id) == null) write(context, id, record) }
        }
        check(preferences.edit().putBoolean("imported_sessions_v1", true).commit())
    }

    /**
     * The records that the legacy [session] metadata (as `PrivilegedEditSessions.legacyBackupMetadata` gives it) refers to,
     * by transaction id: one for each `.autojs6-save-<id>` recovery path and one for its unsettled attempt.
     */
    internal fun legacyRecords(session: Map<String, Any?>): Map<String, MutableMap<String, Any?>> {
        val source = session.optString("canonicalSource", session.optString("source"))
        val mode = session.optString("mode")
        if (!source.startsWith('/') || mode !in PrivilegedAccessMode.entries.map { it.name }) return emptyMap()
        val ids = session.optArray("recovery").orEmpty().mapNotNull { path ->
            val parts = (path as? String).orEmpty().split('/')
            parts.lastOrNull { it.startsWith(".autojs6-save-") }?.removePrefix(".autojs6-save-")?.takeIf(idPattern::matches)
        }.toMutableSet()
        session.optObject("attempt")?.optString("transactionId")?.takeIf(idPattern::matches)?.let(ids::add)
        return ids.associateWith { id ->
            linkedMapOf<String, Any?>("id" to id, "source" to source, "mode" to mode, "createdAt" to session.optLong("_sessionUpdatedAt"), "legacy" to true)
        }
    }

    /** A backup operation; its request and result are org.json objects only at the process boundary (settings P5.3). */
    private fun backupOperation(context: Context, mode: PrivilegedAccessMode, request: Map<String, Any?>): MutableMap<String, Any?> =
        SettingsJson.normalizeObject(PrivilegedFileOperations.backup(context, mode, SettingsJson.toOrgObject(request)))

    /** A snapshot of the original; the result of [PrivilegedFileOperations.snapshot] is an org.json object only at the process boundary. */
    private fun currentSnapshot(context: Context, mode: PrivilegedAccessMode, path: String): MutableMap<String, Any?> =
        SettingsJson.normalizeObject(PrivilegedFileOperations.snapshot(context, mode, path))

    // The members that the code read with org.json's `getString` / `getJSONObject`, which fail for a missing member (and
    // a member of another type) instead of giving a fallback.

    private fun Map<String, Any?>.requireString(key: String): String {
        if (!containsKey(key)) throw SettingsJson.SettingsJsonException("No value for $key")
        return this[key].let { it as? String ?: SettingsJson.stringify(it) }
    }

    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        optObject(key) ?: throw SettingsJson.SettingsJsonException("No object for $key")

    /** A deep copy as `JSONObject(value.toString())` made it. */
    private fun copy(value: Map<String, Any?>): MutableMap<String, Any?> = SettingsJson.parseMap(SettingsJson.stringify(value))
}
