package org.autojs.autojs.ui.main.scripts

import android.content.pm.PackageInstaller
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal data class PackageInstallStatusRecord(
    val sessionId: Int,
    val packageName: String,
    val createdAt: Long,
    val status: Int = INSTALLING,
    val generation: Int = 0,
    val detail: String = "",
    // Only the Intent's filter identity is persisted. Its payload stays in the system token.
    val confirmationKey: String? = null,
    val appLabel: String = packageName,
) {
    val isTerminal: Boolean get() = status >= PackageInstaller.STATUS_SUCCESS

    fun updated(status: Int, detail: String, confirmationKey: String?): PackageInstallStatusRecord? {
        if (isTerminal || status < PackageInstaller.STATUS_PENDING_USER_ACTION) return null
        return copy(status = status, generation = generation + 1, detail = detail,
            confirmationKey = confirmationKey.takeIf { status == PackageInstaller.STATUS_PENDING_USER_ACTION })
    }

    companion object {
        const val INSTALLING = -100
    }
}

/** One atomic, private ledger shared by installer workers, callbacks and the foreground UI. */
internal class PackageInstallStatusStore(file: File) {
    private val file = AtomicFile(file)

    @Synchronized
    fun records(): List<PackageInstallStatusRecord> {
        val bytes = try {
            if (file.baseFile.length() > MAX_BYTES) throw IOException("Installation status file is too large")
            file.readFully()
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        try {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.getInt("version") == 1) { "Unsupported installation status version" }
            val rows = root.getJSONArray("sessions")
            require(rows.length() <= MAX_RECORDS)
            return List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                PackageInstallStatusRecord(row.getInt("sessionId"), row.getString("packageName"),
                    row.getLong("createdAt"), row.getInt("status"), row.getInt("generation"),
                    row.optString("detail"), row.optString("confirmationKey").takeIf(String::isNotEmpty),
                    row.optString("appLabel", row.getString("packageName")))
                    .also { require(it.sessionId >= 0 && it.generation >= 0) }
            }.also { rows -> require(rows.map { it.sessionId }.distinct().size == rows.size) }
        } catch (error: Exception) {
            throw IOException("Unable to read installation status", error)
        }
    }

    @Synchronized
    fun register(sessionId: Int, packageName: String, appLabel: String = packageName) {
        val rows = records()
        require(sessionId >= 0 && rows.none { it.sessionId == sessionId })
        if (rows.size >= MAX_RECORDS) throw IOException("Too many unacknowledged installations")
        write(rows + PackageInstallStatusRecord(sessionId, packageName, System.currentTimeMillis(), appLabel = appLabel))
    }

    @Synchronized
    fun update(sessionId: Int, status: Int, detail: String, confirmationKey: String?): PackageInstallStatusRecord? {
        val rows = records()
        val prior = rows.find { it.sessionId == sessionId } ?: return null
        val next = prior.updated(status, detail, confirmationKey) ?: return null
        write(rows.map { if (it.sessionId == sessionId) next else it })
        return next
    }

    @Synchronized
    fun remove(sessionId: Int) {
        write(records().filterNot { it.sessionId == sessionId })
    }

    private fun write(rows: List<PackageInstallStatusRecord>) {
        val sessions = JSONArray()
        rows.forEach { row -> sessions.put(JSONObject().put("sessionId", row.sessionId)
            .put("packageName", row.packageName).put("createdAt", row.createdAt)
            .put("status", row.status).put("generation", row.generation).put("detail", row.detail)
            .put("confirmationKey", row.confirmationKey).put("appLabel", row.appLabel)) }
        val bytes = JSONObject().put("version", 1).put("sessions", sessions).toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw IOException("Installation status file is too large")
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    companion object {
        private const val MAX_BYTES = 512 * 1024
        private const val MAX_RECORDS = 256
    }
}
