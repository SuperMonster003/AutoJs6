package org.autojs.autojs.model.explorer

import android.content.Context
import android.util.AtomicFile
import org.autojs.autojs.core.pref.SettingsJson
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A permission change may kill the app while the authorized process is still doing IO. The journal entries are the
 * JSON file store `JsonFileStores.PRIVILEGED_FILE_OPERATIONS`, read and written with `SettingsJson` (settings P5.1).
 */
internal object PrivilegedFileOperationJournal {
    private val active = ConcurrentHashMap.newKeySet<String>()

    fun begin(context: Context, request: JSONObject): File {
        val directory = File(context.filesDir, "privileged-file-operations").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.json")
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(SettingsJson.stringify(request).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        active += file.name
        return file
    }

    fun finish(file: File) {
        AtomicFile(file).delete()
        active -= file.name
    }

    fun preserve(file: File, error: PrivilegedFileOperationException?) {
        if (error != null) runCatching {
            val atomic = AtomicFile(file)
            val value = SettingsJson.parseObject(atomic.openRead().bufferedReader().use { it.readText() })
                .put("code", error.code).put("message", error.message)
                .put("recoveryPaths", org.json.JSONArray(error.recoveryPaths))
            val output = atomic.startWrite()
            try {
                output.write(SettingsJson.stringify(value).toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (failure: Exception) {
                atomic.failWrite(output)
                throw failure
            }
        }
        active -= file.name
    }

    /** Take once for presentation. Never replay an interrupted mutation automatically. */
    @Synchronized
    fun interrupted(context: Context): List<Pair<File, JSONObject>> {
        val directory = File(context.filesDir, "privileged-file-operations")
        return directory.listFiles().orEmpty().filter { it.extension == "json" && it.name !in active }.mapNotNull { file ->
            runCatching {
                file to SettingsJson.parseObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
            }.getOrNull()
        }
    }
}
