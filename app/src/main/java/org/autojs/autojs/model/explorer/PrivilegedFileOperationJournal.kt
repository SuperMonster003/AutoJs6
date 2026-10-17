package org.autojs.autojs.model.explorer

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A permission change may kill the app while the authorized process is still doing IO. */
internal object PrivilegedFileOperationJournal {
    private val active = ConcurrentHashMap.newKeySet<String>()

    fun begin(context: Context, request: JSONObject): File {
        val directory = File(context.filesDir, "privileged-file-operations").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.json")
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(request.toString().toByteArray(Charsets.UTF_8))
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
            val value = JSONObject(atomic.openRead().bufferedReader().use { it.readText() })
                .put("code", error.code).put("message", error.message)
                .put("recoveryPaths", org.json.JSONArray(error.recoveryPaths))
            val output = atomic.startWrite()
            try {
                output.write(value.toString().toByteArray(Charsets.UTF_8))
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
                file to JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
            }.getOrNull()
        }
    }
}
