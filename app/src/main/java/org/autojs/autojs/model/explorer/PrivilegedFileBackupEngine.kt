package org.autojs.autojs.model.explorer

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine as SaveEngine
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine.Deadline
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine.Directory
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine.Failure
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine.Identity
import org.autojs.autojs.model.explorer.PrivilegedFileSaveEngine.Target
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Operates only on one explicitly selected save transaction beside its canonical source.
 * Parent/stage leases exclude running saves. Restores use the save engine's original-content
 * checks and atomic commit; cleanup never walks an arbitrary directory tree or follows a link.
 */
object PrivilegedFileBackupEngine {
    private const val MAX_REQUEST_CHARS = 32 * 1024
    private const val MAX_RECORD_BYTES = 64 * 1024
    private const val RETIRE_GRACE_MILLIS = 15 * 60_000L + 20_000L
    private val recordNames = setOf("request.json", "result.json", "deleted.json")

    fun execute(requestJson: String): String {
        var operation: Operation? = null
        return try {
            requireThat(requestJson.length <= MAX_REQUEST_CHARS, "INVALID_REQUEST", "Backup request is too large")
            val request = JSONObject(requestJson)
            if (request.optString("operation") == "backup_scan") return scan(request).toString()
            operation = Operation(request)
            operation.run().toString()
        } catch (error: Exception) {
            (operation?.failure(error) ?: errorResponse(error)).toString()
        }
    }

    private class Operation(private val request: JSONObject) {
        private val operation = request.getString("operation").also {
            requireThat(it in setOf("backup_inspect", "backup_delete", "backup_restore"), "INVALID_REQUEST", "Unknown backup operation")
        }
        private val source = SaveEngine.checkedPath(request.getString("source"))
        private val transactionId = SaveEngine.checkedTransactionId(request.getString("transactionId"))
        private val deadline = Deadline(request)
        private var sourcePath = source
        private var stagePath = "${java.io.File(source).parent}/.autojs6-save-$transactionId"
        private var stageExists = false
        private var partial = false
        private var removed = false
        private val recoveryPaths = linkedSetOf<String>()

        fun run(): JSONObject {
            deadline.check()
            Target.open(source).use { target ->
                sourcePath = target.actualPath
                stagePath = target.parent.actualChild(".autojs6-save-$transactionId")
                target.parent.acquireLease()
                deadline.check()
                target.checkParent()
                val stageEntry = target.parent.child(".autojs6-save-$transactionId")
                if (!SaveEngine.exists(stageEntry)) {
                    if (operation == "backup_restore") throw Failure("NOT_FOUND", "The selected backup no longer exists")
                    return if (operation == "backup_delete") deletionResult(retired = true) else info("missing")
                }
                stageExists = true
                requireThat(OsConstants.S_ISDIR(SaveEngine.identity(stageEntry).mode), "INVALID_BACKUP", "The backup container is not a directory")
                Directory.open(stageEntry, stagePath).use { stage ->
                    stage.acquireLease()
                    target.checkParent()
                    stage.checkAttached()
                    val state = inspect(stage, request.optBoolean("verifyContent") || operation != "backup_inspect")
                    return when (operation) {
                        "backup_inspect" -> state.info
                        "backup_restore" -> restore(target, stage, state)
                        else -> delete(target, stage, state)
                    }
                }
            }
        }

        private fun inspect(stage: Directory, verifyContent: Boolean): State {
            val records = recordNames.associateWith { readRecord(stage, it) }
            val manifest = records["request.json"]?.json
            val receipt = records["result.json"]?.json
            val tombstone = records["deleted.json"]?.json
            val validManifest = manifest != null && manifest.optString("source") == sourcePath &&
                manifest.optString("transactionId") == transactionId
            val completed = receipt?.optBoolean("completed") == true && receipt.optString("transactionId") == transactionId
            val committed = validManifest && completed && receipt?.optBoolean("ok") == true &&
                receipt.optString("commitState") == "committed" && !receipt.optBoolean("partial") &&
                receipt.optString("path") == sourcePath && receipt.optString("digest") == manifest?.optString("contentDigest")
            val validTombstone = tombstone?.optBoolean("deleted") == true && tombstone.optString("source") == sourcePath &&
                tombstone.optString("transactionId") == transactionId
            val createdAt = manifest?.optLong("createdAt")?.takeIf { it > 0 } ?: records["request.json"]?.identity?.modified ?: 0L
            val payloadName = when {
                SaveEngine.exists(stage.child("original")) -> "original"
                SaveEngine.exists(stage.child(".delete-payload")) -> ".delete-payload"
                else -> null
            }
            val unknownEntries = stage.names().filter { it !in recordNames && it != payloadName }
            val common = info("missing").put("createdAt", createdAt).put("completed", completed)
                .put("commitState", receipt?.optString("commitState", "unknown") ?: "unknown")
                .put("tombstone", validTombstone).put("unknownEntries", JSONArray(unknownEntries))
            if (payloadName == null) {
                if (unknownEntries.isNotEmpty()) {
                    common.put("status", "recovery").put("tombstone", false).put("canDelete", false)
                        .put("reasonCode", "BACKUP_RECOVERY_REQUIRED").put("recoveryPaths", JSONArray(listOf(stage.actualPath)))
                } else {
                    common.put("tombstone", validManifest || validTombstone)
                        .put("canDelete", validManifest || validTombstone).put("size", 0)
                }
                return State(common, records, validManifest, committed, null, null, null, unknownEntries, null)
            }
            common.put("backupPath", stage.actualChild(payloadName))
            val before = SaveEngine.identity(stage.child(payloadName))
            if (!OsConstants.S_ISREG(before.mode)) {
                common.put("status", "recovery").put("reasonCode", "UNSUPPORTED_TYPE")
                return State(common, records, validManifest, committed, before, null, null, unknownEntries, payloadName)
            }
            val actualDigest = if (verifyContent) {
                SaveEngine.openFile(stage.child(payloadName), before, writable = false).use { descriptor ->
                    SaveEngine.digest(descriptor, before, deadline).also {
                        stage.checkAttached()
                        SaveEngine.requireUnchanged(before, SaveEngine.identity(stage.child(payloadName)))
                    }
                }
            } else null
            val recordedDigest = receipt?.optString("backupDigest")?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
            val validBackupReceipt = payloadName == "original" && committed && !manifest!!.optBoolean("expectedMissing") &&
                receipt?.optString("backupPath") == stage.actualChild("original") &&
                recordedDigest != null && recordedDigest == manifest.optString("expectedDigest") && manifest.optJSONObject("expected") != null
            val unchanged = validBackupReceipt && matches(manifest?.optJSONObject("expected"), before, ignoreChanged = true) &&
                matches(receipt?.optJSONObject("backupIdentity"), before) &&
                (actualDigest == null || actualDigest == recordedDigest)
            val status = when {
                !validManifest || !validBackupReceipt -> "recovery"
                unchanged -> "available"
                else -> "changed"
            }
            common.put("status", status).put("identity", before.json()).put("size", before.size)
                .put("digest", actualDigest ?: recordedDigest).put("digestVerified", actualDigest != null)
                .put("canView", true).put("canPreview", true).put("canRestore", validBackupReceipt && before.links == 1L)
                .put("canDelete", validManifest).put("autoClean", unchanged && before.links == 1L && unknownEntries.isEmpty())
            if (!validManifest) common.put("reasonCode", "INVALID_BACKUP")
            else if (!validBackupReceipt) common.put("reasonCode", "BACKUP_RECOVERY_REQUIRED")
            return State(common, records, validManifest, validBackupReceipt, before, actualDigest, recordedDigest, unknownEntries, payloadName)
        }

        private fun restore(target: Target, stage: Directory, state: State): JSONObject {
            requireThat(state.validManifest && state.committed, "BACKUP_RECOVERY_REQUIRED", "Only a completed save backup can be restored automatically")
            val before = requirePayload(state)
            SaveEngine.requireSingleLink(before)
            requireSelection(state, before)
            val restoreTransactionId = SaveEngine.checkedTransactionId(request.getString("restoreTransactionId"))
            requireThat(restoreTransactionId != transactionId, "INVALID_REQUEST", "Restoration requires a new transaction ID")
            val saveRequest = JSONObject().put("source", sourcePath).put("transactionId", restoreTransactionId)
                .put("contentSize", before.size).put("contentDigest", state.actualDigest)
                .apply {
                    // Preserve the outer request's replay lifetime. Inventing an absolute
                    // deadline here would incorrectly make a no-deadline request retireable.
                    if (request.has("deadlineMillis")) put("deadlineMillis", request.getLong("deadlineMillis"))
                }
            if (request.optBoolean("expectedMissing")) {
                requireThat(!SaveEngine.exists(target.path), "SOURCE_CHANGED", "A file appeared at the restore destination")
                saveRequest.put("expectedMissing", true)
            } else {
                saveRequest.put("expected", request.getJSONObject("expected"))
                    .put("expectedDigest", SaveEngine.checkedDigest(request.getString("expectedDigest")))
            }
            SaveEngine.openFile(stage.child("original"), before, writable = false).use { descriptor ->
                validatePayload(stage, descriptor, before, requireNotNull(state.actualDigest))
                Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
                ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { input ->
                    val result = JSONObject(SaveEngine.saveFromBackup(saveRequest.toString(), input, target.parent, descriptor) {
                        validatePayload(stage, descriptor, before, requireNotNull(state.actualDigest))
                    })
                    return result.put("restoredFromTransactionId", transactionId).put("restoredFromPath", stage.actualChild("original"))
                }
            }
        }

        private fun delete(target: Target, stage: Directory, state: State): JSONObject {
            requireThat(state.validManifest, "INVALID_BACKUP", "The transaction manifest does not match the selected file")
            val before = state.identity
            if (before == null) {
                requireThat(state.info.optBoolean("canDelete"), "BACKUP_RECOVERY_REQUIRED", "Unrecognized recovery files remain in this transaction")
                ensureTombstone(stage, state, null)
                return finishCleanup(target, stage, state)
            }
            requirePayload(state)
            requireSelection(state, before)
            if (request.optBoolean("auto")) {
                requireThat(state.info.optBoolean("autoClean") && state.actualDigest == state.recordedDigest,
                    "BACKUP_CHANGED", "Changed or unconfirmed recovery files cannot be cleaned automatically")
            }
            val payloadName = requireNotNull(state.payloadName)
            SaveEngine.openFile(stage.child(payloadName), before, writable = false).use { descriptor ->
                validatePayload(stage, descriptor, before, requireNotNull(state.actualDigest), payloadName)
                val quarantineName = ".delete-payload"
                val quarantine = stage.child(quarantineName)
                target.checkParent()
                stage.checkAttached()
                deadline.check()
                if (payloadName == "original") {
                    requireNative(PrivilegedFileNative.renameNoReplace(stage.child("original"), quarantine), "quarantine backup")
                }
                partial = true
                recoveryPaths += stage.actualChild(quarantineName)
                try {
                    SaveEngine.requireUnchanged(before, SaveEngine.identity(quarantine), ignoreChanged = true)
                    requireThat(SaveEngine.digest(descriptor, before, deadline, ignoreChanged = true) == state.actualDigest,
                        "BACKUP_CHANGED", "The selected backup changed while it was being removed")
                    SaveEngine.requireUnchanged(before, SaveEngine.identity(quarantine), ignoreChanged = true)
                    target.checkParent()
                    stage.checkAttached()
                    deadline.check()
                    Os.remove(quarantine)
                    removed = true
                    partial = false
                    recoveryPaths.clear()
                    stage.sync()
                } catch (error: Exception) {
                    if (removed) return deletionResult(retired = false).put("cleanupPending", true)
                        .put("cleanupCode", SaveEngine.errorCode(error)).put("recoveryPaths", JSONArray(listOf(stagePath)))
                    if (!removed && payloadName == "original") {
                        runCatching {
                            target.checkParent()
                            stage.checkAttached()
                            requireThat(before.sameObject(SaveEngine.identity(quarantine), checkMode = false), "BACKUP_CHANGED", "The quarantined backup was replaced")
                            requireNative(PrivilegedFileNative.renameNoReplace(quarantine, stage.child("original")), "restore backup after failed removal")
                            partial = false
                            recoveryPaths.clear()
                            stage.sync()
                        }
                    }
                    throw error
                }
            }
            // Payload deletion is the requested result. Failure to remove tiny metadata must not
            // misreport that the already-deleted backup content is still available to restore.
            return try {
                ensureTombstone(stage, state, before)
                finishCleanup(target, stage, state)
            } catch (error: Exception) {
                deletionResult(retired = false).put("cleanupPending", true)
                    .put("cleanupCode", SaveEngine.errorCode(error)).put("recoveryPaths", JSONArray(listOf(stagePath)))
            }
        }

        private fun requirePayload(state: State): Identity {
            val before = state.identity ?: throw Failure("NOT_FOUND", "The selected backup no longer exists")
            requireThat(OsConstants.S_ISREG(before.mode), "UNSUPPORTED_TYPE", "Backup operations do not follow symbolic links or special files")
            return before
        }

        private fun requireSelection(state: State, before: Identity) {
            requireThat(matches(request.optJSONObject("backupExpected"), before), "BACKUP_CHANGED", "The selected backup changed after it was shown")
            requireThat(SaveEngine.checkedDigest(request.getString("backupDigest")) == state.actualDigest,
                "BACKUP_CHANGED", "The selected backup contents changed after confirmation")
        }

        private fun validatePayload(stage: Directory, descriptor: ParcelFileDescriptor, before: Identity, expectedDigest: String, payloadName: String = "original") {
            try {
                stage.checkAttached()
                SaveEngine.requireUnchanged(before, SaveEngine.identity(stage.child(payloadName)))
                requireThat(SaveEngine.digest(descriptor, before, deadline) == expectedDigest,
                    "BACKUP_CHANGED", "The selected backup contents changed during the operation")
                SaveEngine.requireUnchanged(before, SaveEngine.identity(stage.child(payloadName)))
            } catch (error: Failure) {
                if (error.code == "SOURCE_CHANGED") throw Failure("BACKUP_CHANGED", error.message.orEmpty())
                throw error
            }
        }

        private fun ensureTombstone(stage: Directory, state: State, deletedIdentity: Identity?) {
            if (SaveEngine.exists(stage.child("deleted.json"))) {
                val existing = readRecord(stage, "deleted.json")?.json
                requireThat(existing?.optBoolean("deleted") == true && existing.optString("source") == sourcePath &&
                    existing.optString("transactionId") == transactionId, "INVALID_BACKUP", "Unexpected backup tombstone")
                return
            }
            val record = JSONObject().put("source", sourcePath).put("transactionId", transactionId).put("deleted", true)
                .put("deletedAt", System.currentTimeMillis()).put("createdAt", state.info.optLong("createdAt"))
                .put("identity", deletedIdentity?.json()).put("digest", state.actualDigest)
            writeRecord(stage, "deleted.json", record)
        }

        private fun finishCleanup(target: Target, stage: Directory, state: State): JSONObject {
            val manifest = state.records["request.json"]?.json ?: return deletionResult(retired = false)
            val createdAt = manifest.optLong("createdAt", 0)
            val originalDeadline = manifest.optLong("deadlineMillis", 0)
            val now = System.currentTimeMillis()
            // Old requests without an absolute deadline could start a fresh relative timeout if
            // replayed later. Their small directory fence must therefore be kept permanently.
            val expired = createdAt > 0 && originalDeadline > 0 && now > createdAt &&
                now - createdAt > RETIRE_GRACE_MILLIS && now > originalDeadline && now - originalDeadline > 20_000L
            val unknown = stage.names().filter { it !in recordNames }
            if (unknown.isNotEmpty()) return deletionResult(retired = false).put("tombstone", false).put("status", "recovery")
                .put("cleanupPending", true).put("unknownEntries", JSONArray(unknown)).put("recoveryPaths", JSONArray(listOf(stagePath)))
            if (!expired) return deletionResult(retired = false)
            val records = stage.names().associateWith { name ->
                val record = readRecord(stage, name) ?: throw Failure("INVALID_BACKUP", "Backup metadata changed before cleanup")
                requireThat(record.json.optString("transactionId") == transactionId &&
                    (name == "result.json" || record.json.optString("source") == sourcePath), "INVALID_BACKUP", "Unrelated backup metadata")
                requireThat(record.identity.links == 1L, "INVALID_BACKUP", "Backup metadata has unexpected hard links")
                record
            }
            deadline.check()
            target.checkParent()
            stage.checkAttached()
            // Keep request.json and the completion entry until last, minimizing incomplete
            // cleanup after process death. Any surviving directory remains a replay fence.
            for (name in listOf("deleted.json", "result.json", "request.json")) {
                val record = records[name] ?: continue
                verifyRecord(stage, name, record)
                Os.remove(stage.child(name))
            }
            target.checkParent()
            stage.checkAttached()
            Os.remove(target.parent.child(".autojs6-save-$transactionId"))
            target.parent.sync()
            stageExists = false
            return deletionResult(retired = true)
        }

        private fun deletionResult(retired: Boolean) = JSONObject().put("ok", true).put("transactionId", transactionId)
            .put("sourcePath", sourcePath).put("backupPath", "$stagePath/original").put("stagePath", stagePath)
            .put("removed", removed).put("retired", retired).put("tombstone", !retired)
            .put("stageExists", stageExists).put("fenceRetained", !retired).put("partial", false)

        private fun info(status: String) = JSONObject().put("ok", true).put("transactionId", transactionId)
            .put("sourcePath", sourcePath).put("stagePath", stagePath).put("backupPath", "$stagePath/original")
            .put("status", status).put("stageExists", stageExists).put("canView", false).put("canPreview", false)
            .put("canRestore", false).put("canDelete", false).put("autoClean", false).put("size", 0)
            .put("digestVerified", false).put("tombstone", false)

        fun failure(error: Exception): JSONObject {
            if (operation == "backup_inspect" && SaveEngine.errorCode(error) == "TRANSACTION_BUSY") return info("busy")
                .put("stageExists", true).put("stageExistenceKnown", false)
            return errorResponse(error).put("transactionId", transactionId).put("partial", partial).put("removed", removed)
                .put("stageExists", stageExists).put("sourcePath", sourcePath).put("backupPath", "$stagePath/original")
                .put("recoveryPaths", JSONArray(recoveryPaths.toList()))
        }

        private fun readRecord(stage: Directory, name: String): Record? = readJsonRecord(stage, name, deadline)

        private fun verifyRecord(stage: Directory, name: String, before: Record) {
            val after = readRecord(stage, name) ?: throw Failure("INVALID_BACKUP", "Backup metadata changed during cleanup")
            SaveEngine.requireUnchanged(before.identity, after.identity)
            requireThat(before.digest == after.digest, "INVALID_BACKUP", "Backup metadata changed during cleanup")
        }

        private fun writeRecord(stage: Directory, name: String, value: JSONObject) {
            val pending = ".$name-${UUID.randomUUID()}.pending"
            SaveEngine.openExclusive(stage.child(pending)).use { descriptor ->
                ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { output ->
                    output.write(value.toString().toByteArray(Charsets.UTF_8))
                    output.flush()
                    Os.fsync(descriptor.fileDescriptor)
                }
            }
            stage.checkAttached()
            requireNative(PrivilegedFileNative.renameNoReplace(stage.child(pending), stage.child(name)), "publish backup metadata")
            stage.sync()
        }
    }

    private data class Record(val json: JSONObject, val identity: Identity, val digest: String)
    private data class State(
        val info: JSONObject,
        val records: Map<String, Record?>,
        val validManifest: Boolean,
        val committed: Boolean,
        val identity: Identity?,
        val actualDigest: String?,
        val recordedDigest: String?,
        val unknownEntries: List<String>,
        val payloadName: String?,
    )

    private fun scan(request: JSONObject): JSONObject {
        val raw = request.getString("source")
        val requestedPath = if (raw == "/") "/" else SaveEngine.checkedPath(raw)
        val directoryPath = java.io.File(requestedPath).canonicalPath
        val deadline = Deadline(request)
        val limit = request.optInt("limit", 1000).coerceIn(1, 1000)
        val entries = JSONArray()
        deadline.check()
        Directory.open(directoryPath).use { directory ->
            try {
                directory.acquireLease()
            } catch (error: Failure) {
                if (error.code != "TRANSACTION_BUSY") throw error
                return JSONObject().put("ok", true).put("sourcePath", directoryPath).put("entries", entries)
                    .put("busy", true).put("truncated", false)
            }
            directory.checkAttached()
            val candidates = directory.names(20_000).filter { name ->
                name.startsWith(".autojs6-save-") && runCatching {
                    SaveEngine.checkedTransactionId(name.removePrefix(".autojs6-save-"))
                }.isSuccess
            }
            var examined = 0
            for (name in candidates) {
                deadline.check()
                if (examined++ >= limit) break
                val child = directory.child(name)
                if (!OsConstants.S_ISDIR(SaveEngine.identity(child).mode)) continue
                Directory.open(child, directory.actualChild(name)).use { stage ->
                    try {
                        stage.acquireLease()
                    } catch (error: Failure) {
                        if (error.code != "TRANSACTION_BUSY") throw error
                        return@use
                    }
                    val manifest = readJsonRecord(stage, "request.json", deadline) ?: return@use
                    val id = name.removePrefix(".autojs6-save-")
                    if (manifest.json.optString("transactionId") != id) return@use
                    val source = runCatching { SaveEngine.checkedPath(manifest.json.getString("source")) }.getOrNull() ?: return@use
                    // Compare the recorded canonical parent as data. Do not resolve or open a
                    // manifest-supplied source path outside this explicitly selected directory.
                    if (java.io.File(source).parent != directoryPath) return@use
                    val payload = when {
                        SaveEngine.exists(stage.child("original")) -> "original"
                        SaveEngine.exists(stage.child(".delete-payload")) -> ".delete-payload"
                        else -> null
                    }
                    val regular = payload != null && OsConstants.S_ISREG(SaveEngine.identity(stage.child(payload)).mode)
                    val unknownEntries = stage.names().filter { it !in recordNames && it != payload }
                    val createdAt = manifest.json.optLong("createdAt").takeIf { it > 0 } ?: manifest.identity.modified
                    entries.put(JSONObject().put("source", source).put("transactionId", id).put("createdAt", createdAt)
                        .put("backupPath", stage.actualChild(payload ?: "original")).put("stagePath", stage.actualPath)
                        .put("tombstone", payload == null && unknownEntries.isEmpty()).put("canView", regular)
                        .put("status", if (payload == null && unknownEntries.isNotEmpty()) "recovery" else "unchecked"))
                    stage.checkAttached()
                }
            }
            directory.checkAttached()
            return JSONObject().put("ok", true).put("sourcePath", directoryPath).put("entries", entries)
                .put("busy", false).put("truncated", candidates.size > limit)
        }
    }

    private fun readJsonRecord(stage: Directory, name: String, deadline: Deadline): Record? {
        return try {
            if (!SaveEngine.exists(stage.child(name))) return null
            val before = SaveEngine.identity(stage.child(name))
            requireThat(OsConstants.S_ISREG(before.mode) && before.links == 1L && before.size in 0..MAX_RECORD_BYTES,
                "INVALID_BACKUP", "Invalid backup metadata")
            val bytes = SaveEngine.openFile(stage.child(name), before, writable = false).use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        deadline.check()
                        val count = input.read(buffer)
                        if (count < 0) break
                        requireThat(output.size() + count <= MAX_RECORD_BYTES, "INVALID_BACKUP", "Backup metadata grew while reading")
                        output.write(buffer, 0, count)
                    }
                    SaveEngine.requireUnchanged(before, SaveEngine.identity(stage.child(name)))
                    output.toByteArray()
                }
            }
            Record(JSONObject(bytes.toString(Charsets.UTF_8)), before, hash(bytes))
        } catch (error: Exception) {
            when {
                error is org.json.JSONException -> null
                error is Failure && error.code == "INVALID_BACKUP" -> null
                error is ErrnoException && error.errno == OsConstants.ENOENT -> null
                else -> throw error
            }
        }
    }

    private fun matches(expected: JSONObject?, actual: Identity, ignoreChanged: Boolean = false): Boolean {
        if (expected == null) return false
        val current = actual.json()
        return runCatching {
            listOf("dev", "ino", "mode", "size", "modified", "changed").all {
                (ignoreChanged && it == "changed") || (expected.has(it) && expected.getLong(it) == current.getLong(it))
            } && listOf("uid", "gid", "links").filter(expected::has).all { expected.getLong(it) == current.getLong(it) }
        }.getOrDefault(false)
    }

    private fun requireNative(errno: Int, operation: String) { if (errno != 0) throw ErrnoException(operation, errno) }
    private fun requireThat(condition: Boolean, code: String, message: String) { if (!condition) throw Failure(code, message) }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun errorResponse(error: Exception) = JSONObject().put("ok", false).put("code", SaveEngine.errorCode(error))
        .put("message", error.message.orEmpty().take(4096)).put("partial", false).put("recoveryPaths", JSONArray())
}
