package org.autojs.autojs.external.open

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.util.IntentUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * A project zip handed over by a plugin (3-Block Compose folder projects, the plugin's roadmap D64): `main.js` (or the
 * `project.json` that names the main script) on top, or inside the one folder everything is in. The entries are read
 * into memory and checked before anything is written: relative paths that stay inside the project, at most
 * [MAX_ENTRIES] files and [MAX_BYTES] bytes once unpacked.
 *
 * zh-CN: 插件交来的工程 zip (3-Block Compose 文件夹工程, 插件路线图 D64). 先整体读入内存并校验, 再写盘.
 */
internal object ProjectArchive {

    const val MAX_ENTRIES = 4_096
    const val MAX_BYTES = 128L * 1024L * 1024L
    private const val MAIN_SCRIPT = "main.js"
    private const val PROJECT_CONFIG = "project.json"
    private const val ZIP_EXTENSION = ".zip"

    /** The checked files of the zip behind [uri] by `/`-separated path. */
    fun read(context: Context, uri: Uri): Map<String, ByteArray> {
        val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        return input.use { read(it) }
    }

    /** The checked files of the zip [input]; an unsafe entry or a zip over the limits rejects it whole. */
    fun read(input: InputStream): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = entry.name.trimEnd('/')
                if (entry.isDirectory || path.isEmpty()) continue
                require(isSafePath(path)) { "Unsafe zip entry: ${entry.name}" }
                val out = ByteArrayOutputStream()
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BYTES) { "The project zip is too large" }
                    out.write(buffer, 0, read)
                }
                require(files.size < MAX_ENTRIES) { "The project zip has too many files" }
                files[path] = out.toByteArray()
            }
        }
        val kept = files.filterKeys { path -> path.split('/').none { it == "__MACOSX" } }
        val project = unwrap(kept)
        require(MAIN_SCRIPT in project || PROJECT_CONFIG in project) { "The project zip has no $MAIN_SCRIPT or $PROJECT_CONFIG" }
        return project
    }

    /** Without a main script on top and with every file inside one folder, that folder's content. */
    private fun unwrap(files: Map<String, ByteArray>): Map<String, ByteArray> {
        if (MAIN_SCRIPT in files || PROJECT_CONFIG in files) return files
        val tops = files.keys.map { it.substringBefore('/') }.toSet()
        if (tops.size != 1 || files.keys.any { '/' !in it }) return files
        val prefix = tops.single() + "/"
        return files.mapKeys { (path, _) -> path.removePrefix(prefix) }
    }

    /** A relative `/`-separated path that stays inside the project: no root, drive, backslash, `.` or `..` part. */
    fun isSafePath(path: String): Boolean =
        path.isNotEmpty() && !path.startsWith("/") && path.none { it.code == BACKSLASH || it == ':' } &&
            path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

    /** Writes [files] under [dir]; files with the same paths are replaced, other files there stay. */
    fun unpack(files: Map<String, ByteArray>, dir: File) {
        dir.mkdirs()
        val root = dir.canonicalFile
        for ((path, bytes) in files) {
            val target = File(root, path).canonicalFile
            require(target.path.startsWith(root.path + File.separator)) { "Unsafe zip entry: $path" }
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }

    /** The folder name for the zip [uri]: its display name without `.zip`, with what a file name cannot hold replaced. */
    fun folderNameOf(context: Context, uri: Uri): String {
        val name = (IntentUtils.getFileName(context, uri) ?: uri.lastPathSegment ?: "project").substringAfterLast('/')
        val stem = if (name.endsWith(ZIP_EXTENSION, ignoreCase = true)) name.dropLast(ZIP_EXTENSION.length) else name
        return stem.map { c -> if (c.code < 0x20 || c.code == BACKSLASH || c.code == QUOTE || c in "/:*?<>|") '_' else c }
            .joinToString("").trim().trim('.').ifEmpty { "project" }
    }

    /** [name] when no file of [parent] has it, else `name-2`, `name-3`, ... */
    fun freeFolder(parent: File, name: String): File {
        var candidate = File(parent, name)
        var counter = 2
        while (candidate.exists()) candidate = File(parent, "$name-${counter++}")
        return candidate
    }

    private const val BACKSLASH = 0x5C
    private const val QUOTE = 0x22
}

/**
 * Removes the cache folders project zips run in (`RunProjectIntentActivity`) once no script runs in them any more:
 * shortly after an execution there ends, and at every new run for folders an earlier process left behind.
 *
 * zh-CN: 工程 zip 运行用的缓存目录在其中没有脚本运行后删除.
 */
internal object ProjectRunCleaner {

    private const val DELAY_MS = 2_000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile
    private var installed = false

    fun install(service: ScriptEngineService, runs: File) {
        synchronized(this) {
            if (installed) return
            installed = true
        }
        service.registerGlobalScriptExecutionListener(object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) = finished(service, runs, execution)
            override fun onException(execution: ScriptExecution, e: Throwable) = finished(service, runs, execution)
        })
    }

    /** Removes every run folder no execution uses. */
    fun sweep(service: ScriptEngineService, runs: File) {
        runs.listFiles()?.filter { it.isDirectory && !inUse(service, runs, it) }?.forEach { it.deleteRecursively() }
    }

    private fun finished(service: ScriptEngineService, runs: File, execution: ScriptExecution) {
        val dir = runFolderOf(runs, execution.config.workingDirectory) ?: return
        main.postDelayed({ if (!inUse(service, runs, dir)) dir.deleteRecursively() }, DELAY_MS)
    }

    private fun inUse(service: ScriptEngineService, runs: File, dir: File): Boolean =
        service.scriptExecutions.any { runFolderOf(runs, it.config.workingDirectory) == dir }

    /** The run folder (a direct child of [runs]) [workingDirectory] is in, or null. */
    private fun runFolderOf(runs: File, workingDirectory: String?): File? {
        if (workingDirectory.isNullOrEmpty()) return null
        val root = runs.absoluteFile
        var current: File? = File(workingDirectory).absoluteFile
        while (current != null) {
            if (current.parentFile == root) return current
            current = current.parentFile
        }
        return null
    }
}
