package org.autojs.autojs.core.plugin.explorer.session

import android.content.Context
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.util.DialogUtils
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

internal enum class ExplorerActionTargetTrashItemState {
    PENDING,
    MOVING,
    MOVED,
    NOT_MOVED,
    RESTORING,
    RESTORED,
    STALE,
    RECOVERY_REQUIRED,
}

/** Which protocol path created a batch; older records without a kind are v21 archive batches. */
internal enum class ExplorerActionTargetTrashBatchKind {
    /** v16 / v21: the complete selection after its archive outputs were committed. */
    ARCHIVE_SOURCES,

    /** v24: request targets a read-only action moved one user deletion at a time. */
    SELECTED_TARGETS,
}

internal data class ExplorerActionTargetTrashBatchSource(
    val targetId: String,
    val file: File,
    val identity: ExplorerActionFileIdentity,
)

internal data class ExplorerActionTargetTrashItemRecord(
    val targetId: String,
    val displayName: String,
    val originalPath: String,
    val originalIdentity: ExplorerActionFileIdentity,
    val trashId: String,
    val state: ExplorerActionTargetTrashItemState,
) {
    init {
        require(targetId.length in 1..ExplorerActionProtocol.MAX_TARGET_ID_LENGTH)
        require(displayName.length in 1..ExplorerActionProtocol.MAX_TARGET_DISPLAY_NAME_LENGTH)
        require(originalPath.isNotBlank())
        require(isCanonicalTargetTrashUuid(trashId))
    }

    val protocolState: Int
        get() = when (state) {
            ExplorerActionTargetTrashItemState.MOVED ->
                ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_MOVED
            ExplorerActionTargetTrashItemState.NOT_MOVED ->
                ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_NOT_MOVED
            ExplorerActionTargetTrashItemState.RESTORED ->
                ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_RESTORED
            ExplorerActionTargetTrashItemState.STALE ->
                ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_STALE
            ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED ->
                ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_RECOVERY_REQUIRED
            ExplorerActionTargetTrashItemState.PENDING,
            ExplorerActionTargetTrashItemState.MOVING,
            ExplorerActionTargetTrashItemState.RESTORING,
            -> ExplorerActionHostSessionValues.TARGET_TRASH_ITEM_STATE_UNKNOWN
        }

    val crossedTrashBoundary: Boolean
        get() = state != ExplorerActionTargetTrashItemState.PENDING &&
            state != ExplorerActionTargetTrashItemState.MOVING &&
            state != ExplorerActionTargetTrashItemState.NOT_MOVED
}

internal data class ExplorerActionTargetTrashBatchRecord(
    val id: String,
    val pluginUid: Int,
    val parentPath: String,
    val parentDevice: Long,
    val parentInode: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val items: List<ExplorerActionTargetTrashItemRecord>,
    val kind: ExplorerActionTargetTrashBatchKind = ExplorerActionTargetTrashBatchKind.ARCHIVE_SOURCES,
) {
    init {
        require(isCanonicalTargetTrashUuid(id))
        require(pluginUid >= 0)
        require(parentPath.isNotBlank())
        require(createdAtMillis >= 0L && updatedAtMillis >= createdAtMillis)
        require(items.size in 1..ExplorerActionProtocol.MAX_TARGET_TRASH_ITEM_RESULTS)
        require(items.map(ExplorerActionTargetTrashItemRecord::targetId).distinct().size == items.size)
        require(items.map(ExplorerActionTargetTrashItemRecord::originalPath).distinct().size == items.size)
        require(items.map(ExplorerActionTargetTrashItemRecord::trashId).distinct().size == items.size)
    }

    val protocolState: Int
        get() {
            if (items.any { item ->
                    item.state == ExplorerActionTargetTrashItemState.PENDING ||
                        item.state == ExplorerActionTargetTrashItemState.MOVING ||
                        item.state == ExplorerActionTargetTrashItemState.RESTORING
                }
            ) {
                return ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_UNKNOWN
            }
            val moved = items.count { it.state == ExplorerActionTargetTrashItemState.MOVED }
            if (moved > 0 && items.any { it.state != ExplorerActionTargetTrashItemState.MOVED }) {
                return ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_PARTIAL
            }
            if (moved > 0) {
                return ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_AVAILABLE
            }
            if (items.any { it.state == ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED }) {
                return ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RECOVERY_REQUIRED
            }
            if (items.any { it.state == ExplorerActionTargetTrashItemState.STALE }) {
                return ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_STALE
            }
            if (items.any { it.state == ExplorerActionTargetTrashItemState.RESTORED }) {
                return ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED
            }
            return ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_FAILED
        }

    val movedCount: Int
        get() = items.count(ExplorerActionTargetTrashItemRecord::crossedTrashBoundary)

    val restoredCount: Int
        get() = items.count { it.state == ExplorerActionTargetTrashItemState.RESTORED }

    val recoveryCount: Int
        get() = items.count {
            it.state == ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED ||
                it.state == ExplorerActionTargetTrashItemState.STALE
        }

    val canUndo: Boolean
        get() = items.any { it.state == ExplorerActionTargetTrashItemState.MOVED }

    val canBeAutomaticallyRemoved: Boolean
        get() = protocolState != ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_UNKNOWN &&
            protocolState != ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RECOVERY_REQUIRED

    val canBeEvictedForCapacity: Boolean
        get() = if (canUndo) {
            // v24: undoing a viewer deletion is a short-lived convenience. Dropping its record keeps
            // every moved item in host Trash, where the Trash page still restores it, so such a
            // record may make room as long as no item needs recovery.
            // zh-CN: v24 查看器删除的撤销只是短时便利. 丢弃其记录时已移动的条目仍在宿主回收站中可恢复, 因此在没有待恢复条目时可让出空间.
            kind == ExplorerActionTargetTrashBatchKind.SELECTED_TARGETS && items.none { item ->
                item.state == ExplorerActionTargetTrashItemState.PENDING ||
                    item.state == ExplorerActionTargetTrashItemState.MOVING ||
                    item.state == ExplorerActionTargetTrashItemState.RESTORING ||
                    item.state == ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED
            }
        } else {
            protocolState == ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_RESTORED ||
                protocolState == ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_STALE ||
                protocolState == ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_FAILED
        }
}

/** Durable host-private v21 / v24 history for one source-to-Trash operation. */
internal class ExplorerActionTargetTrashBatchStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val root = File(context.noBackupFilesDir, ROOT_DIRECTORY_NAME)

    fun recover(repository: TrashRepository) = synchronized(PROCESS_LOCK) {
        recoverLocked(repository)
    }

    /**
     * [recover] for a new session. The file manager creates sessions on the main thread, where Room refuses the
     * Trash database that recovery reads, so there it runs in the background; every later store operation recovers
     * again first, so nothing has to wait for it.
     */
    fun recoverForNewSession(repository: TrashRepository) {
        if (Looper.myLooper() != Looper.getMainLooper()) return recover(repository)
        RECOVERY_EXECUTOR.execute {
            runCatching { recover(repository) }.onFailure { error ->
                Log.w(TAG, "Explorer Action target Trash history recovery failed", error)
            }
        }
    }

    private fun recoverLocked(repository: TrashRepository) {
        if (!root.isDirectory) return
        listRecordsLocked()
            .filterNot { record -> record.id in ACTIVE_BATCH_IDS }
            .forEach { record -> recoverRecordLocked(record, repository) }
        cleanupExpiredLocked(clock().coerceAtLeast(0L))
    }

    fun prepare(
        pluginUid: Int,
        parent: File,
        sources: List<ExplorerActionTargetTrashBatchSource>,
        repository: TrashRepository,
        kind: ExplorerActionTargetTrashBatchKind = ExplorerActionTargetTrashBatchKind.ARCHIVE_SOURCES,
    ): ExplorerActionTargetTrashBatchRecord = synchronized(PROCESS_LOCK) {
        require(pluginUid >= 0)
        require(sources.size in 1..ExplorerActionProtocol.MAX_TARGET_TRASH_ITEM_RESULTS)
        recoverLocked(repository)
        makeRoomLocked()
        ensureRootLocked()
        val canonicalParent = parent.canonicalFile
        val parentIdentity = ExplorerActionFileIdentity.capture(canonicalParent)
        val now = clock().coerceAtLeast(0L)
        val record = ExplorerActionTargetTrashBatchRecord(
            id = UUID.randomUUID().toString(),
            pluginUid = pluginUid,
            parentPath = canonicalParent.path,
            parentDevice = parentIdentity.device,
            parentInode = parentIdentity.inode,
            createdAtMillis = now,
            updatedAtMillis = now,
            items = sources.map { source ->
                val canonicalSource = source.file.canonicalFile
                require(canonicalSource.parentFile == canonicalParent) {
                    "Explorer Action target Trash source escapes its parent"
                }
                require(ExplorerActionFileIdentity.capture(canonicalSource) == source.identity) {
                    "Explorer Action target Trash source changed before history preparation"
                }
                ExplorerActionTargetTrashItemRecord(
                    targetId = source.targetId,
                    displayName = canonicalSource.name,
                    originalPath = canonicalSource.path,
                    originalIdentity = source.identity,
                    trashId = repository.newTrashId(),
                    state = ExplorerActionTargetTrashItemState.PENDING,
                )
            },
            kind = kind,
        )
        writeLocked(record)
        check(ACTIVE_BATCH_IDS.add(record.id)) {
            "Explorer Action target Trash batch is already active"
        }
        record
    }

    fun updateItem(
        batchId: String,
        itemIndex: Int,
        state: ExplorerActionTargetTrashItemState,
    ): ExplorerActionTargetTrashBatchRecord = synchronized(PROCESS_LOCK) {
        check(batchId in ACTIVE_BATCH_IDS) {
            "Explorer Action target Trash batch is not active"
        }
        val record = requireNotNull(readLocked(batchId)) {
            "Explorer Action target Trash batch is unknown"
        }
        require(itemIndex in record.items.indices)
        val items = record.items.toMutableList()
        items[itemIndex] = items[itemIndex].copy(state = state)
        val updated = record.copy(
            updatedAtMillis = nextTimestamp(record),
            items = items,
        )
        writeLocked(updated)
        updated
    }

    fun finish(batchId: String) = synchronized(PROCESS_LOCK) {
        require(isCanonicalTargetTrashUuid(batchId)) {
            "Explorer Action target Trash batch ID is invalid"
        }
        ACTIVE_BATCH_IDS.remove(batchId)
    }

    fun query(
        pluginUid: Int,
        parent: File,
        batchId: String,
        repository: TrashRepository,
    ): ExplorerActionTargetTrashBatchRecord? = synchronized(PROCESS_LOCK) {
        recoverLocked(repository)
        if (batchId in ACTIVE_BATCH_IDS) return@synchronized null
        return readLocked(batchId)?.takeIf { record -> belongsTo(record, pluginUid, parent) }
    }

    fun list(
        pluginUid: Int,
        parent: File,
        repository: TrashRepository,
    ): List<ExplorerActionTargetTrashBatchRecord> = synchronized(PROCESS_LOCK) {
        recoverLocked(repository)
        return listRecordsLocked()
            .filterNot { record -> record.id in ACTIVE_BATCH_IDS }
            .filter { record -> belongsTo(record, pluginUid, parent) }
            .sortedByDescending(ExplorerActionTargetTrashBatchRecord::createdAtMillis)
            .take(ExplorerActionProtocol.MAX_TARGET_TRASH_BATCH_RESULTS)
    }

    fun undo(
        pluginUid: Int,
        parent: File,
        batchId: String,
        repository: TrashRepository,
    ): ExplorerActionTargetTrashBatchRecord {
        var record = synchronized(PROCESS_LOCK) {
            val available = requireNotNull(query(pluginUid, parent, batchId, repository)) {
                "Explorer Action target Trash batch is unknown"
            }
            check(available.canUndo) { "Explorer Action target Trash batch cannot be restored" }
            check(ACTIVE_BATCH_IDS.add(batchId)) {
                "Explorer Action target Trash batch is already active"
            }
            available
        }
        try {
            record.items.indices.forEach { index ->
                val currentItem = record.items[index]
                if (currentItem.state != ExplorerActionTargetTrashItemState.MOVED) return@forEach
                val trashItem = repository.findTrashItem(currentItem.trashId)
                if (trashItem == null || trashItem.originalPath != currentItem.originalPath) {
                    record = updateItem(record.id, index, ExplorerActionTargetTrashItemState.STALE)
                    return@forEach
                }
                val destination = originalFile(record, currentItem)
                if (destination.exists()) {
                    record = updateItem(record.id, index, ExplorerActionTargetTrashItemState.STALE)
                    return@forEach
                }
                record = updateItem(record.id, index, ExplorerActionTargetTrashItemState.RESTORING)
                try {
                    repository.restoreTrashItemToPathWithoutOverwrite(
                        item = trashItem,
                        dest = destination,
                        operationController = DialogUtils.OperationController(),
                    )
                    record = updateItem(record.id, index, ExplorerActionTargetTrashItemState.RESTORED)
                } catch (_: Exception) {
                    val trashStillExists = repository.findTrashItem(currentItem.trashId) != null
                    val destinationExists = destination.exists()
                    val recoveredState = when {
                        !trashStillExists && destinationExists ->
                            ExplorerActionTargetTrashItemState.RESTORED
                        trashStillExists && !destinationExists ->
                            ExplorerActionTargetTrashItemState.MOVED
                        else -> ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED
                    }
                    record = updateItem(record.id, index, recoveredState)
                }
            }
            return record
        } finally {
            finish(batchId)
        }
    }

    fun remove(batchId: String): Boolean = synchronized(PROCESS_LOCK) {
        if (!isCanonicalTargetTrashUuid(batchId) || batchId in ACTIVE_BATCH_IDS) {
            return@synchronized false
        }
        val file = recordFile(batchId)
        val removed = listOf(
            file,
            File(file.path + ATOMIC_BACKUP_SUFFIX),
            File(file.path + ATOMIC_NEW_SUFFIX),
        ).all { candidate -> !candidate.exists() || candidate.delete() }
        removed
    }

    private fun recoverRecordLocked(
        original: ExplorerActionTargetTrashBatchRecord,
        repository: TrashRepository,
    ): ExplorerActionTargetTrashBatchRecord {
        var changed = false
        val recoveredItems = original.items.map { item ->
            val recoveredState = recoverItemState(item, repository)
            if (recoveredState != item.state) changed = true
            item.copy(state = recoveredState)
        }
        if (!changed) return original
        return original.copy(
            updatedAtMillis = nextTimestamp(original),
            items = recoveredItems,
        ).also(::writeLocked)
    }

    private fun recoverItemState(
        item: ExplorerActionTargetTrashItemRecord,
        repository: TrashRepository,
    ): ExplorerActionTargetTrashItemState {
        val trashItem = repository.findTrashItem(item.trashId)
        val sourceIdentity = captureIdentityOrNull(File(item.originalPath))
        return when (item.state) {
            ExplorerActionTargetTrashItemState.PENDING ->
                ExplorerActionTargetTrashItemState.NOT_MOVED
            ExplorerActionTargetTrashItemState.MOVING -> when {
                trashItem != null && sourceIdentity == null ->
                    ExplorerActionTargetTrashItemState.MOVED
                trashItem != null && sourceIdentity == item.originalIdentity -> {
                    if (runCatching { repository.deleteTrashItem(trashItem) }.isSuccess) {
                        ExplorerActionTargetTrashItemState.NOT_MOVED
                    } else {
                        ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED
                    }
                }
                trashItem == null && sourceIdentity == item.originalIdentity ->
                    ExplorerActionTargetTrashItemState.NOT_MOVED
                else -> ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED
            }
            ExplorerActionTargetTrashItemState.MOVED -> when {
                trashItem == null || sourceIdentity != null ->
                    ExplorerActionTargetTrashItemState.STALE
                else -> item.state
            }
            ExplorerActionTargetTrashItemState.RESTORING -> when {
                trashItem == null && sourceIdentity != null ->
                    ExplorerActionTargetTrashItemState.RESTORED
                trashItem != null && sourceIdentity == null ->
                    ExplorerActionTargetTrashItemState.MOVED
                else -> ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED
            }
            ExplorerActionTargetTrashItemState.NOT_MOVED,
            ExplorerActionTargetTrashItemState.RESTORED,
            ExplorerActionTargetTrashItemState.STALE,
            ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED,
            -> item.state
        }
    }

    private fun originalFile(
        record: ExplorerActionTargetTrashBatchRecord,
        item: ExplorerActionTargetTrashItemRecord,
    ): File {
        val parent = File(record.parentPath).canonicalFile
        val original = File(item.originalPath).canonicalFile
        require(original.parentFile == parent) {
            "Explorer Action target Trash restore path escapes its parent"
        }
        return original
    }

    private fun belongsTo(
        record: ExplorerActionTargetTrashBatchRecord,
        pluginUid: Int,
        parent: File,
    ): Boolean {
        val canonicalParent = runCatching { parent.canonicalFile }.getOrNull() ?: return false
        val identity = captureIdentityOrNull(canonicalParent) ?: return false
        return record.pluginUid == pluginUid &&
            record.parentPath == canonicalParent.path &&
            record.parentDevice == identity.device &&
            record.parentInode == identity.inode
    }

    private fun captureIdentityOrNull(file: File): ExplorerActionFileIdentity? =
        runCatching { ExplorerActionFileIdentity.capture(file) }.getOrNull()

    private fun makeRoomLocked() {
        cleanupExpiredLocked(clock().coerceAtLeast(0L))
        var records = listRecordsLocked()
        if (records.size < MAX_HISTORY_RECORDS) return
        records.asSequence()
            .filterNot { record -> record.id in ACTIVE_BATCH_IDS }
            .filter(ExplorerActionTargetTrashBatchRecord::canBeEvictedForCapacity)
            .sortedWith(
                compareBy(
                    ExplorerActionTargetTrashBatchRecord::canUndo,
                    ExplorerActionTargetTrashBatchRecord::updatedAtMillis,
                ),
            )
            .take(records.size - MAX_HISTORY_RECORDS + 1)
            .forEach { record -> remove(record.id) }
        records = listRecordsLocked()
        check(records.size < MAX_HISTORY_RECORDS) {
            "Explorer Action target Trash history requires recovery before another batch"
        }
    }

    private fun cleanupExpiredLocked(now: Long) {
        listRecordsLocked()
            .filter { record ->
                record.canBeAutomaticallyRemoved &&
                    now - record.updatedAtMillis >= HISTORY_RETENTION_MILLIS
            }
            .forEach { record -> remove(record.id) }
    }

    private fun nextTimestamp(record: ExplorerActionTargetTrashBatchRecord): Long =
        maxOf(record.createdAtMillis, record.updatedAtMillis, clock())

    private fun ensureRootLocked() {
        if (root.isDirectory) return
        if (!root.mkdirs() && !root.isDirectory) {
            throw IOException("Explorer Action target Trash history directory is unavailable")
        }
    }

    private fun writeLocked(record: ExplorerActionTargetTrashBatchRecord) {
        ensureRootLocked()
        val atomic = AtomicFile(recordFile(record.id))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toJson().toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw IOException("Explorer Action target Trash history could not be written", error)
        }
    }

    private fun readLocked(batchId: String): ExplorerActionTargetTrashBatchRecord? {
        if (!isCanonicalTargetTrashUuid(batchId)) return null
        val file = recordFile(batchId)
        if (
            !file.exists() &&
            !File(file.path + ATOMIC_BACKUP_SUFFIX).exists() &&
            !File(file.path + ATOMIC_NEW_SUFFIX).exists()
        ) {
            return null
        }
        return runCatching {
            AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText().toRecord()
            }
        }.getOrNull()
    }

    private fun listRecordsLocked(): List<ExplorerActionTargetTrashBatchRecord> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isFile)
            .mapNotNull { file -> batchIdFromJournalName(file.name) }
            .distinct()
            .mapNotNull(::readLocked)
            .toList()
    }

    private fun batchIdFromJournalName(name: String): String? {
        val suffix = ".$RECORD_EXTENSION"
        val id = when {
            name.endsWith(suffix) -> name.removeSuffix(suffix)
            name.endsWith(suffix + ATOMIC_BACKUP_SUFFIX) ->
                name.removeSuffix(suffix + ATOMIC_BACKUP_SUFFIX)
            name.endsWith(suffix + ATOMIC_NEW_SUFFIX) ->
                name.removeSuffix(suffix + ATOMIC_NEW_SUFFIX)
            else -> return null
        }
        return id.takeIf(::isCanonicalTargetTrashUuid)
    }

    private fun recordFile(batchId: String): File = File(root, "$batchId.$RECORD_EXTENSION")

    private fun ExplorerActionTargetTrashBatchRecord.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_PLUGIN_UID, pluginUid)
        put(KEY_PARENT_PATH, parentPath)
        put(KEY_PARENT_DEVICE, parentDevice)
        put(KEY_PARENT_INODE, parentInode)
        put(KEY_CREATED_AT, createdAtMillis)
        put(KEY_UPDATED_AT, updatedAtMillis)
        put(KEY_KIND, kind.name)
        put(
            KEY_ITEMS,
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put(KEY_TARGET_ID, item.targetId)
                            put(KEY_DISPLAY_NAME, item.displayName)
                            put(KEY_ORIGINAL_PATH, item.originalPath)
                            put(KEY_TRASH_ID, item.trashId)
                            put(KEY_STATE, item.state.name)
                            putIdentity(KEY_ORIGINAL_IDENTITY, item.originalIdentity)
                        },
                    )
                }
            },
        )
    }

    private fun JSONObject.putIdentity(prefix: String, identity: ExplorerActionFileIdentity) {
        put("${prefix}Device", identity.device)
        put("${prefix}Inode", identity.inode)
        put("${prefix}Size", identity.size)
        put("${prefix}LastModified", identity.lastModifiedMillis)
    }

    private fun String.toRecord(): ExplorerActionTargetTrashBatchRecord {
        val json = JSONObject(this)
        val itemArray = json.getJSONArray(KEY_ITEMS)
        require(itemArray.length() in 1..ExplorerActionProtocol.MAX_TARGET_TRASH_ITEM_RESULTS)
        val items = ArrayList<ExplorerActionTargetTrashItemRecord>(itemArray.length())
        repeat(itemArray.length()) { index ->
            val item = itemArray.getJSONObject(index)
            items += ExplorerActionTargetTrashItemRecord(
                targetId = item.getString(KEY_TARGET_ID),
                displayName = item.getString(KEY_DISPLAY_NAME),
                originalPath = item.getString(KEY_ORIGINAL_PATH),
                originalIdentity = item.identity(KEY_ORIGINAL_IDENTITY),
                trashId = item.getString(KEY_TRASH_ID),
                state = ExplorerActionTargetTrashItemState.valueOf(item.getString(KEY_STATE)),
            )
        }
        return ExplorerActionTargetTrashBatchRecord(
            id = json.getString(KEY_ID),
            pluginUid = json.getInt(KEY_PLUGIN_UID),
            parentPath = json.getString(KEY_PARENT_PATH),
            parentDevice = json.getLong(KEY_PARENT_DEVICE),
            parentInode = json.getLong(KEY_PARENT_INODE),
            createdAtMillis = json.getLong(KEY_CREATED_AT),
            updatedAtMillis = json.getLong(KEY_UPDATED_AT),
            items = items,
            kind = json.optString(KEY_KIND).takeIf(String::isNotEmpty)
                ?.let(ExplorerActionTargetTrashBatchKind::valueOf)
                ?: ExplorerActionTargetTrashBatchKind.ARCHIVE_SOURCES,
        )
    }

    private fun JSONObject.identity(prefix: String): ExplorerActionFileIdentity =
        ExplorerActionFileIdentity(
            device = getLong("${prefix}Device"),
            inode = getLong("${prefix}Inode"),
            size = getLong("${prefix}Size"),
            lastModifiedMillis = getLong("${prefix}LastModified"),
        )

    private companion object {
        private val PROCESS_LOCK = Any()
        private val ACTIVE_BATCH_IDS = HashSet<String>()
        private val RECOVERY_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ExplorerActionTargetTrashRecovery").apply { isDaemon = true }
        }
        const val TAG = "ExplorerActionTrash"
        const val ROOT_DIRECTORY_NAME = "explorer-action-target-trash-batches"
        const val RECORD_EXTENSION = "json"
        const val ATOMIC_BACKUP_SUFFIX = ".bak"
        const val ATOMIC_NEW_SUFFIX = ".new"
        const val MAX_HISTORY_RECORDS = ExplorerActionProtocol.MAX_TARGET_TRASH_BATCH_RESULTS
        const val HISTORY_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val KEY_ID = "id"
        const val KEY_PLUGIN_UID = "pluginUid"
        const val KEY_PARENT_PATH = "parentPath"
        const val KEY_PARENT_DEVICE = "parentDevice"
        const val KEY_PARENT_INODE = "parentInode"
        const val KEY_CREATED_AT = "createdAtMillis"
        const val KEY_UPDATED_AT = "updatedAtMillis"
        const val KEY_KIND = "kind"
        const val KEY_ITEMS = "items"
        const val KEY_TARGET_ID = "targetId"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_ORIGINAL_PATH = "originalPath"
        const val KEY_ORIGINAL_IDENTITY = "original"
        const val KEY_TRASH_ID = "trashId"
        const val KEY_STATE = "state"
    }
}

private fun isCanonicalTargetTrashUuid(value: String): Boolean {
    if (value.length > ExplorerActionProtocol.MAX_TARGET_TRASH_BATCH_ID_LENGTH) return false
    val parsed = runCatching { UUID.fromString(value) }.getOrNull() ?: return false
    return parsed.toString().equals(value, ignoreCase = true)
}
