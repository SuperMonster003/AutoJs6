package org.autojs.autojs.model.explorer

import android.content.Context
import android.util.AtomicFile
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.putOrRemove
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A permission change may kill the app while the authorized process is still doing IO. The journal entries are the
 * JSON file store `JsonFileStores.PRIVILEGED_FILE_OPERATIONS`: the operation request as plain values of `SettingsJson`
 * (settings P5.1 / P5.3), plus the code, message and recovery paths of an operation that did not end cleanly.
 */
internal object PrivilegedFileOperationJournal {
    private val active = ConcurrentHashMap.newKeySet<String>()

    fun begin(context: Context, request: Map<String, Any?>): File {
        val directory = File(context.filesDir, "privileged-file-operations").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "${UUID.randomUUID()}.json")
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(encode(request).toByteArray(Charsets.UTF_8))
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
            val value = preserved(decode(atomic.openRead().bufferedReader().use { it.readText() }), error)
            val output = atomic.startWrite()
            try {
                output.write(encode(value).toByteArray(Charsets.UTF_8))
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
    fun interrupted(context: Context): List<Pair<File, Map<String, Any?>>> {
        val directory = File(context.filesDir, "privileged-file-operations")
        return directory.listFiles().orEmpty().filter { it.extension == "json" && it.name !in active }.mapNotNull { file ->
            runCatching {
                file to decode(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
            }.getOrNull()
        }
    }

    internal fun encode(record: Map<String, Any?>): String = SettingsJson.stringify(record)

    internal fun decode(text: String): MutableMap<String, Any?> = SettingsJson.parseMap(text)

    /** The record of an operation that ended with [error]: the error code, its message and the paths kept for recovery. */
    internal fun preserved(record: MutableMap<String, Any?>, error: PrivilegedFileOperationException): MutableMap<String, Any?> = record.apply {
        put("code", error.code)
        putOrRemove("message", error.message)
        put("recoveryPaths", ArrayList(error.recoveryPaths))
    }
}
