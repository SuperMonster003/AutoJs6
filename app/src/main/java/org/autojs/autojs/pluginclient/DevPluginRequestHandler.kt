package org.autojs.autojs.pluginclient

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Handles `request` messages of dev plugin protocol v2 and replies with `response` messages.
 * zh-CN: 处理开发插件协议 v2 的 `request` 消息并以 `response` 消息应答.
 *
 * Request:  `{"id": 1, "type": "request", "data": {"method": "fs.list", "params": {"path": "/sdcard"}}}`
 * Response: `{"id": 1, "type": "response", "data": {"ok": true, "result": {...}}}`
 *           `{"id": 1, "type": "response", "data": {"ok": false, "error": "..."}}`
 *
 * All file methods are restricted to the external storage directory.
 * zh-CN: 所有文件方法均限制在外部存储目录内.
 */
class DevPluginRequestHandler(private val context: Context) {

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val storageRoot: File
        get() = EnvironmentUtils.externalStorageDirectory

    fun handle(socket: JsonSocket, message: JsonObject) {
        val id = message.get("id")
        val data = message.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val method = data?.get("method")?.takeIf { it.isJsonPrimitive }?.asString
        if (method == null) {
            socket.writeErrorResponse(id, "Invalid request: missing method")
            return
        }
        val params = data.get("params")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        executor.execute {
            try {
                val result = dispatch(method, params)
                socket.writeResponse(id, result)
            } catch (e: Throwable) {
                Log.w(TAG, "request \"$method\" failed: ${e.message}")
                socket.writeErrorResponse(id, e.message ?: e.toString())
            }
        }
    }

    private fun dispatch(method: String, params: JsonObject): JsonElement = when (method) {
        "sys.info" -> sysInfo()
        "fs.roots" -> roots()
        "fs.list" -> list(requireFile(params, "path"), params.optBoolean("hash", false))
        "fs.stat" -> stat(requireFile(params, "path"))
        "fs.read" -> read(requireFile(params, "path"), params.optString("encoding") ?: "utf8", params.optLong("offset"), params.optLong("length"))
        "fs.write" -> write(
            requireFile(params, "path"),
            params.optString("content") ?: "",
            params.optString("encoding") ?: "utf8",
            create = params.optBoolean("create", true),
            overwrite = params.optBoolean("overwrite", true),
            mkdirs = params.optBoolean("mkdirs", true),
            append = params.optBoolean("append", false),
        )
        "fs.mkdir" -> mkdir(requireFile(params, "path"))
        "fs.delete" -> delete(requireFile(params, "path"), params.optBoolean("recursive", false))
        "fs.rename" -> rename(requireFile(params, "path"), requireFile(params, "newPath"), params.optBoolean("overwrite", false))
        "script.run" -> runScript(requireFile(params, "path"))
        else -> throw IllegalArgumentException("Unknown method: $method")
    }

    /* Methods. zh-CN: 方法实现. */

    private fun sysInfo() = JsonObject().apply {
        addProperty("appVersion", BuildConfig.VERSION_NAME)
        addProperty("appVersionCode", BuildConfig.VERSION_CODE)
        addProperty("protocolVersion", JsonSocket.PROTOCOL_VERSION)
        add("features", JsonSocket.featuresJson())
        addProperty("deviceName", "${Build.BRAND} ${Build.MODEL}")
        addProperty("sdk", Build.VERSION.SDK_INT)
        addProperty("workingDir", WorkingDirectoryUtils.path)
        addProperty("externalStorage", storageRoot.path)
    }

    private fun roots() = JsonObject().apply {
        addProperty("workingDir", WorkingDirectoryUtils.path)
        addProperty("externalStorage", storageRoot.path)
        add("roots", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", context.getString(R.string.text_working_directory))
                addProperty("path", WorkingDirectoryUtils.path)
            })
            add(JsonObject().apply {
                addProperty("name", context.getString(R.string.text_external_storage))
                addProperty("path", storageRoot.path)
            })
        })
    }

    /**
     * List a directory; with `hash` every regular file up to `MAX_HASH_SIZE` also carries its `md5` so the extension can
     * compare device files with local ones without downloading them (pull project changes).
     * zh-CN: 列出目录; `hash` 为 true 时不超过 `MAX_HASH_SIZE` 的普通文件附带 `md5`, 使插件无需下载即可比较设备文件与本地文件 (拉取项目变更).
     */
    private fun list(dir: File, hash: Boolean = false): JsonElement {
        if (!dir.exists()) throw IOException("Not found: ${dir.path}")
        if (!dir.isDirectory) throw IOException("Not a directory: ${dir.path}")
        val children = dir.listFiles() ?: throw IOException("Access denied: ${dir.path}")
        return JsonObject().apply {
            addProperty("path", dir.path)
            add("entries", JsonArray().apply {
                children
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    .forEach { add(entryOf(it, hash)) }
            })
        }
    }

    private fun stat(file: File) = JsonObject().apply {
        addProperty("path", file.path)
        addProperty("exists", file.exists())
        if (file.exists()) {
            addProperty("type", if (file.isDirectory) "directory" else "file")
            addProperty("size", if (file.isDirectory) 0 else file.length())
            addProperty("mtime", file.lastModified())
        }
    }

    /**
     * Read a whole file, or the piece `[offset, offset + length)` when either is given (chunked transfer, extension
     * 1.1.0+). The response carries the total `size`, the piece `offset`/`length` and `eof`.
     * zh-CN: 读取整个文件, 或在给出 offset / length 之一时读取 `[offset, offset + length)` 片段 (分块传输, 插件 1.1.0+).
     * 响应包含文件总大小 `size`, 片段 `offset`/`length` 与 `eof`.
     */
    private fun read(file: File, encoding: String, offset: Long?, length: Long?): JsonElement {
        if (!file.exists()) throw IOException("Not found: ${file.path}")
        if (file.isDirectory) throw IOException("Is a directory: ${file.path}")
        val total = file.length()
        val chunked = offset != null || length != null
        val start = (offset ?: 0L).coerceIn(0L, total)
        val count = (length ?: (total - start)).coerceIn(0L, total - start)
        if (count > MAX_READ_SIZE) throw IOException("File too large ($count bytes, max $MAX_READ_SIZE); read it in pieces with offset/length")
        val bytes: ByteArray = if (!chunked) file.readBytes() else RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(count.toInt())
            var read = 0
            while (read < buffer.size) {
                val n = raf.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            if (read == buffer.size) buffer else buffer.copyOf(read)
        }
        return JsonObject().apply {
            addProperty("path", file.path)
            addProperty("size", total)
            addProperty("offset", start)
            addProperty("length", bytes.size)
            addProperty("eof", start + bytes.size >= total)
            if (encoding.equals("base64", ignoreCase = true)) {
                addProperty("encoding", "base64")
                addProperty("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            } else {
                addProperty("encoding", "utf8")
                addProperty("content", String(bytes, Charsets.UTF_8))
            }
        }
    }

    /**
     * Write a file; with `append` the bytes are added to an existing file (later pieces of a chunked upload).
     * zh-CN: 写入文件; `append` 为 true 时追加到已有文件 (分块上传的后续片段).
     */
    private fun write(file: File, content: String, encoding: String, create: Boolean, overwrite: Boolean, mkdirs: Boolean, append: Boolean = false): JsonElement {
        if (file.isDirectory) throw IOException("Is a directory: ${file.path}")
        if (append) {
            if (!file.exists()) throw IOException("Not found (cannot append): ${file.path}")
        } else {
            if (file.exists() && !overwrite) throw IOException("Already exists: ${file.path}")
            if (!file.exists() && !create) throw IOException("Not found: ${file.path}")
        }
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            if (!mkdirs) throw IOException("Not found: ${parent.path}")
            if (!parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create directory: ${parent.path}")
        }
        val bytes = if (encoding.equals("base64", ignoreCase = true)) Base64.decode(content, Base64.DEFAULT) else content.toByteArray(Charsets.UTF_8)
        if (append) file.appendBytes(bytes) else file.writeBytes(bytes)
        return JsonObject().apply {
            addProperty("path", file.path)
            addProperty("size", file.length())
            addProperty("written", bytes.size)
            addProperty("mtime", file.lastModified())
        }
    }

    private fun mkdir(dir: File): JsonElement {
        if (dir.exists()) {
            if (dir.isDirectory) return JsonObject().apply { addProperty("path", dir.path) }
            throw IOException("Already exists: ${dir.path}")
        }
        if (!dir.mkdirs()) throw IOException("Cannot create directory: ${dir.path}")
        return JsonObject().apply { addProperty("path", dir.path) }
    }

    private fun delete(file: File, recursive: Boolean): JsonElement {
        if (!file.exists()) throw IOException("Not found: ${file.path}")
        if (file.canonicalFile == storageRoot.canonicalFile) throw IOException("Access denied: ${file.path}")
        val ok = if (file.isDirectory) {
            if (!recursive && (file.list()?.isNotEmpty() == true)) throw IOException("Directory not empty: ${file.path}")
            file.deleteRecursively()
        } else {
            file.delete()
        }
        if (!ok) throw IOException("Cannot delete: ${file.path}")
        return JsonObject().apply { addProperty("path", file.path) }
    }

    private fun rename(file: File, newFile: File, overwrite: Boolean): JsonElement {
        if (!file.exists()) throw IOException("Not found: ${file.path}")
        if (newFile.exists()) {
            if (!overwrite) throw IOException("Already exists: ${newFile.path}")
            if (newFile.isDirectory) newFile.deleteRecursively() else newFile.delete()
        }
        newFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (!file.renameTo(newFile)) {
            // Fall back to copy + delete across mount points. zh-CN: 跨挂载点时回退为复制加删除.
            file.copyRecursively(newFile, overwrite = true)
            file.deleteRecursively()
        }
        return JsonObject().apply { addProperty("path", newFile.path) }
    }

    private fun runScript(file: File): JsonElement {
        if (!file.exists()) throw IOException("Not found: ${file.path}")
        if (file.isDirectory) throw IOException("Is a directory: ${file.path}")
        val latch = CountDownLatch(1)
        var error: Throwable? = null
        var started = false
        mainHandler.post {
            try {
                started = Scripts.run(context, ScriptFile(file.path)) != null
            } catch (e: Throwable) {
                error = e
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(10, TimeUnit.SECONDS)) throw IOException("Timed out while starting script")
        error?.let { throw it }
        if (!started) throw IOException("Failed to start script: ${file.path}")
        return JsonObject().apply { addProperty("path", file.path) }
    }

    /* Helpers. zh-CN: 辅助方法. */

    private fun entryOf(file: File, hash: Boolean = false) = JsonObject().apply {
        addProperty("name", file.name)
        addProperty("path", file.path)
        addProperty("type", if (file.isDirectory) "directory" else "file")
        addProperty("size", if (file.isDirectory) 0 else file.length())
        addProperty("mtime", file.lastModified())
        if (hash && file.isFile && file.length() <= MAX_HASH_SIZE) {
            md5Of(file)?.let { addProperty("md5", it) }
        }
    }

    private fun md5Of(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /**
     * Resolve a path parameter and make sure it stays inside the external storage directory.
     * zh-CN: 解析路径参数并确保位于外部存储目录内.
     */
    private fun requireFile(params: JsonObject, key: String): File {
        val raw = params.optString(key)?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Missing parameter: $key")
        val file = if (raw.startsWith("/")) File(raw) else File(storageRoot, raw)
        val canonical = try {
            file.canonicalPath
        } catch (e: IOException) {
            file.absolutePath
        }
        val root = storageRoot.canonicalPath
        if (canonical != root && !canonical.startsWith(root + File.separator)) {
            throw IOException("Access denied: $raw")
        }
        return File(canonical)
    }

    private fun JsonObject.optString(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.optLong(key: String): Long? = get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

    private fun JsonObject.optBoolean(key: String, default: Boolean): Boolean = get(key)?.takeIf { it.isJsonPrimitive }?.let {
        runCatching { it.asBoolean }.getOrDefault(default)
    } ?: default

    companion object {
        private const val TAG = "DevPluginRequestHandler"
        private const val MAX_READ_SIZE = 32L * 1024 * 1024
        private const val MAX_HASH_SIZE = 16L * 1024 * 1024
    }

}
