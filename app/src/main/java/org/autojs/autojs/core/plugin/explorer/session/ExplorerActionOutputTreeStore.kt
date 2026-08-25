package org.autojs.autojs.core.plugin.explorer.session

import android.content.Context
import android.util.AtomicFile
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class ExplorerActionOutputTreeRecord(
    val id: String,
    val pluginUid: Int,
    val processInstanceId: String,
    val parentPath: String,
    val parentDevice: Long,
    val parentInode: Long,
    val targetPath: String,
    val targetDevice: Long,
    val targetInode: Long,
    val displayName: String,
    val markerName: String,
    val state: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val fileCount: Int = 0,
    val directoryCount: Int = 0,
    val outputBytes: Long = 0L,
) {
    init {
        require(runCatching { UUID.fromString(id) }.getOrNull()?.toString() == id.lowercase())
        require(pluginUid >= 0)
        require(processInstanceId.isNotBlank())
        require(parentPath.isNotBlank() && targetPath.isNotBlank())
        require(displayName.isNotBlank() && markerName.isNotBlank())
        require(
                state == ExplorerActionHostSessionValues.OUTPUT_STATE_PREPARED ||
                state == ExplorerActionHostSessionValues.OUTPUT_STATE_WRITING ||
                state == ExplorerActionHostSessionValues.OUTPUT_STATE_VERIFYING ||
                state == ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED ||
                state == ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
        )
        require(createdAtMillis >= 0L && updatedAtMillis >= createdAtMillis)
        require(fileCount >= 0 && directoryCount >= 0 && outputBytes >= 0L)
    }
}

/** Durable, host-private journal for recoverable v9 directory output transactions. */
internal class ExplorerActionOutputTreeStore(context: Context) {

    private val root = File(context.noBackupFilesDir, ROOT_DIRECTORY_NAME)

    @Synchronized
    fun write(record: ExplorerActionOutputTreeRecord) {
        ensureRoot()
        val atomic = AtomicFile(recordFile(record.id))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toJson().toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw IOException("Explorer Action output tree journal could not be written", error)
        }
    }

    @Synchronized
    fun read(transactionId: String): ExplorerActionOutputTreeRecord? {
        if (!isCanonicalUuid(transactionId)) return null
        val file = recordFile(transactionId)
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8).toRecord() }.getOrNull()
    }

    @Synchronized
    fun list(): List<ExplorerActionOutputTreeRecord> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == RECORD_EXTENSION }
            .mapNotNull { file ->
                runCatching { file.readText(Charsets.UTF_8).toRecord() }.getOrNull()
            }
            .sortedBy(ExplorerActionOutputTreeRecord::createdAtMillis)
            .toList()
    }

    @Synchronized
    fun remove(transactionId: String): Boolean {
        if (!isCanonicalUuid(transactionId)) return false
        val file = recordFile(transactionId)
        return !file.exists() || file.delete()
    }

    private fun ensureRoot() {
        if (root.isDirectory) return
        if (!root.mkdirs() && !root.isDirectory) {
            throw IOException("Explorer Action output tree journal directory is unavailable")
        }
    }

    private fun recordFile(transactionId: String): File = File(root, "$transactionId.$RECORD_EXTENSION")

    private fun ExplorerActionOutputTreeRecord.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_PLUGIN_UID, pluginUid)
        put(KEY_PROCESS_INSTANCE_ID, processInstanceId)
        put(KEY_PARENT_PATH, parentPath)
        put(KEY_PARENT_DEVICE, parentDevice)
        put(KEY_PARENT_INODE, parentInode)
        put(KEY_TARGET_PATH, targetPath)
        put(KEY_TARGET_DEVICE, targetDevice)
        put(KEY_TARGET_INODE, targetInode)
        put(KEY_DISPLAY_NAME, displayName)
        put(KEY_MARKER_NAME, markerName)
        put(KEY_STATE, state)
        put(KEY_CREATED_AT, createdAtMillis)
        put(KEY_UPDATED_AT, updatedAtMillis)
        put(KEY_FILE_COUNT, fileCount)
        put(KEY_DIRECTORY_COUNT, directoryCount)
        put(KEY_OUTPUT_BYTES, outputBytes)
    }

    private fun String.toRecord(): ExplorerActionOutputTreeRecord {
        val json = JSONObject(this)
        return ExplorerActionOutputTreeRecord(
            id = json.getString(KEY_ID),
            pluginUid = json.getInt(KEY_PLUGIN_UID),
            processInstanceId = json.getString(KEY_PROCESS_INSTANCE_ID),
            parentPath = json.getString(KEY_PARENT_PATH),
            parentDevice = json.getLong(KEY_PARENT_DEVICE),
            parentInode = json.getLong(KEY_PARENT_INODE),
            targetPath = json.getString(KEY_TARGET_PATH),
            targetDevice = json.getLong(KEY_TARGET_DEVICE),
            targetInode = json.getLong(KEY_TARGET_INODE),
            displayName = json.getString(KEY_DISPLAY_NAME),
            markerName = json.getString(KEY_MARKER_NAME),
            state = json.getInt(KEY_STATE),
            createdAtMillis = json.getLong(KEY_CREATED_AT),
            updatedAtMillis = json.getLong(KEY_UPDATED_AT),
            fileCount = json.optInt(KEY_FILE_COUNT, 0),
            directoryCount = json.optInt(KEY_DIRECTORY_COUNT, 0),
            outputBytes = json.optLong(KEY_OUTPUT_BYTES, 0L),
        )
    }

    private fun isCanonicalUuid(value: String): Boolean {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull() ?: return false
        return parsed.toString().equals(value, ignoreCase = true)
    }

    private companion object {
        const val ROOT_DIRECTORY_NAME = "explorer-action-output-trees"
        const val RECORD_EXTENSION = "json"
        const val KEY_ID = "id"
        const val KEY_PLUGIN_UID = "pluginUid"
        const val KEY_PROCESS_INSTANCE_ID = "processInstanceId"
        const val KEY_PARENT_PATH = "parentPath"
        const val KEY_PARENT_DEVICE = "parentDevice"
        const val KEY_PARENT_INODE = "parentInode"
        const val KEY_TARGET_PATH = "targetPath"
        const val KEY_TARGET_DEVICE = "targetDevice"
        const val KEY_TARGET_INODE = "targetInode"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_MARKER_NAME = "markerName"
        const val KEY_STATE = "state"
        const val KEY_CREATED_AT = "createdAtMillis"
        const val KEY_UPDATED_AT = "updatedAtMillis"
        const val KEY_FILE_COUNT = "fileCount"
        const val KEY_DIRECTORY_COUNT = "directoryCount"
        const val KEY_OUTPUT_BYTES = "outputBytes"
    }
}
