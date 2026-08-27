package org.autojs.autojs.core.plugin.explorer.session

import android.content.Context
import android.util.AtomicFile
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class ExplorerActionOutputBatchMemberRecord(
    val transactionId: String,
    val targetPath: String,
    val stagingPath: String,
    val stagingDevice: Long,
    val stagingInode: Long,
    val stagingSize: Long,
    val stagingLastModifiedMillis: Long,
) {
    init {
        require(isCanonicalOutputBatchUuid(transactionId))
        require(targetPath.isNotBlank() && stagingPath.isNotBlank() && targetPath != stagingPath)
        require(stagingSize >= 0L && stagingLastModifiedMillis >= 0L)
    }
}

internal data class ExplorerActionOutputBatchRecord(
    val id: String,
    val pluginUid: Int,
    val processInstanceId: String,
    val parentPath: String,
    val parentDevice: Long,
    val parentInode: Long,
    val state: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val members: List<ExplorerActionOutputBatchMemberRecord>,
    val publishedCount: Int = 0,
) {
    init {
        require(isCanonicalOutputBatchUuid(id))
        require(pluginUid >= 0)
        require(processInstanceId.isNotBlank() && parentPath.isNotBlank())
        require(
            state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_PREPARED ||
                state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTING ||
                state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED ||
                state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED ||
                state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_RECOVERY_REQUIRED,
        )
        require(createdAtMillis >= 0L && updatedAtMillis >= createdAtMillis)
        require(members.size in 2..ExplorerActionProtocol.MAX_OUTPUT_BATCH_ENTRIES)
        require(members.map(ExplorerActionOutputBatchMemberRecord::transactionId).distinct().size == members.size)
        require(members.map(ExplorerActionOutputBatchMemberRecord::targetPath).distinct().size == members.size)
        require(members.map(ExplorerActionOutputBatchMemberRecord::stagingPath).distinct().size == members.size)
        require(publishedCount in 0..members.size)
    }
}

/** Durable, host-private journal for recoverable v15 multi-file output publication. */
internal class ExplorerActionOutputBatchStore(context: Context) {

    private val root = File(context.noBackupFilesDir, ROOT_DIRECTORY_NAME)

    @Synchronized
    fun write(record: ExplorerActionOutputBatchRecord) {
        ensureRoot()
        val atomic = AtomicFile(recordFile(record.id))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toJson().toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw IOException("Explorer Action output batch journal could not be written", error)
        }
    }

    @Synchronized
    fun read(batchId: String): ExplorerActionOutputBatchRecord? {
        if (!isCanonicalOutputBatchUuid(batchId)) return null
        val file = recordFile(batchId)
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8).toRecord() }.getOrNull()
    }

    @Synchronized
    fun list(): List<ExplorerActionOutputBatchRecord> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == RECORD_EXTENSION }
            .mapNotNull { file ->
                runCatching { file.readText(Charsets.UTF_8).toRecord() }.getOrNull()
            }
            .sortedBy(ExplorerActionOutputBatchRecord::createdAtMillis)
            .toList()
    }

    @Synchronized
    fun remove(batchId: String): Boolean {
        if (!isCanonicalOutputBatchUuid(batchId)) return false
        val file = recordFile(batchId)
        return !file.exists() || file.delete()
    }

    private fun ensureRoot() {
        if (root.isDirectory) return
        if (!root.mkdirs() && !root.isDirectory) {
            throw IOException("Explorer Action output batch journal directory is unavailable")
        }
    }

    private fun recordFile(batchId: String): File = File(root, "$batchId.$RECORD_EXTENSION")

    private fun ExplorerActionOutputBatchRecord.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_PLUGIN_UID, pluginUid)
        put(KEY_PROCESS_INSTANCE_ID, processInstanceId)
        put(KEY_PARENT_PATH, parentPath)
        put(KEY_PARENT_DEVICE, parentDevice)
        put(KEY_PARENT_INODE, parentInode)
        put(KEY_STATE, state)
        put(KEY_CREATED_AT, createdAtMillis)
        put(KEY_UPDATED_AT, updatedAtMillis)
        put(KEY_PUBLISHED_COUNT, publishedCount)
        put(
            KEY_MEMBERS,
            JSONArray().apply {
                members.forEach { member ->
                    put(
                        JSONObject().apply {
                            put(KEY_TRANSACTION_ID, member.transactionId)
                            put(KEY_TARGET_PATH, member.targetPath)
                            put(KEY_STAGING_PATH, member.stagingPath)
                            put(KEY_STAGING_DEVICE, member.stagingDevice)
                            put(KEY_STAGING_INODE, member.stagingInode)
                            put(KEY_STAGING_SIZE, member.stagingSize)
                            put(KEY_STAGING_LAST_MODIFIED, member.stagingLastModifiedMillis)
                        },
                    )
                }
            },
        )
    }

    private fun String.toRecord(): ExplorerActionOutputBatchRecord {
        val json = JSONObject(this)
        val memberArray = json.getJSONArray(KEY_MEMBERS)
        require(memberArray.length() <= ExplorerActionProtocol.MAX_OUTPUT_BATCH_ENTRIES)
        val members = ArrayList<ExplorerActionOutputBatchMemberRecord>(memberArray.length())
        repeat(memberArray.length()) { index ->
            val member = memberArray.getJSONObject(index)
            members += ExplorerActionOutputBatchMemberRecord(
                transactionId = member.getString(KEY_TRANSACTION_ID),
                targetPath = member.getString(KEY_TARGET_PATH),
                stagingPath = member.getString(KEY_STAGING_PATH),
                stagingDevice = member.getLong(KEY_STAGING_DEVICE),
                stagingInode = member.getLong(KEY_STAGING_INODE),
                stagingSize = member.getLong(KEY_STAGING_SIZE),
                stagingLastModifiedMillis = member.getLong(KEY_STAGING_LAST_MODIFIED),
            )
        }
        return ExplorerActionOutputBatchRecord(
            id = json.getString(KEY_ID),
            pluginUid = json.getInt(KEY_PLUGIN_UID),
            processInstanceId = json.getString(KEY_PROCESS_INSTANCE_ID),
            parentPath = json.getString(KEY_PARENT_PATH),
            parentDevice = json.getLong(KEY_PARENT_DEVICE),
            parentInode = json.getLong(KEY_PARENT_INODE),
            state = json.getInt(KEY_STATE),
            createdAtMillis = json.getLong(KEY_CREATED_AT),
            updatedAtMillis = json.getLong(KEY_UPDATED_AT),
            members = members,
            publishedCount = json.optInt(KEY_PUBLISHED_COUNT, 0),
        )
    }

    private companion object {
        const val ROOT_DIRECTORY_NAME = "explorer-action-output-batches"
        const val RECORD_EXTENSION = "json"
        const val KEY_ID = "id"
        const val KEY_PLUGIN_UID = "pluginUid"
        const val KEY_PROCESS_INSTANCE_ID = "processInstanceId"
        const val KEY_PARENT_PATH = "parentPath"
        const val KEY_PARENT_DEVICE = "parentDevice"
        const val KEY_PARENT_INODE = "parentInode"
        const val KEY_STATE = "state"
        const val KEY_CREATED_AT = "createdAtMillis"
        const val KEY_UPDATED_AT = "updatedAtMillis"
        const val KEY_PUBLISHED_COUNT = "publishedCount"
        const val KEY_MEMBERS = "members"
        const val KEY_TRANSACTION_ID = "transactionId"
        const val KEY_TARGET_PATH = "targetPath"
        const val KEY_STAGING_PATH = "stagingPath"
        const val KEY_STAGING_DEVICE = "stagingDevice"
        const val KEY_STAGING_INODE = "stagingInode"
        const val KEY_STAGING_SIZE = "stagingSize"
        const val KEY_STAGING_LAST_MODIFIED = "stagingLastModifiedMillis"
    }
}

private fun isCanonicalOutputBatchUuid(value: String): Boolean {
    val parsed = runCatching { UUID.fromString(value) }.getOrNull() ?: return false
    return parsed.toString().equals(value, ignoreCase = true)
}
