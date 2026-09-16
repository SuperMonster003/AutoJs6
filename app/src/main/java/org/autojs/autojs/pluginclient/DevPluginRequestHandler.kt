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
        "fs.list" -> list(requireFile(params, "path"))
        "fs.stat" -> stat(requireFile(params, "path"))
        "fs.read" -> read(requireFile(params, "path"), params.optString("encoding") ?: "utf8")
        "fs.write" -> write(
            requireFile(params, "path"),
            params.optString("content") ?: "",
            params.optString("encoding") ?: "utf8",
            create = params.optBoolean("create", true),
            overwrite = params.optBoolean("overwrite", true),
            mkdirs = params.optBoolean("mkdirs", true),
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

    private fun list(dir: File): JsonElement {
        if (!dir.exists()) throw IOException("Not found: ${dir.path}")
        if (!dir.isDirectory) throw IOException("Not a directory: ${dir.path}")
        val children = dir.listFiles() ?: throw IOException("Access denied: ${dir.path}")
        return JsonObject().apply {
            addProperty("path", dir.path)
            add("entries", JsonArray().apply {
                children
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    .forEach { add(entryOf(it)) }
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

    private fun read(file: File, encoding: String): JsonElement {
        if (!file.exists()) throw IOException("Not found: ${file.path}")
        if (file.isDirectory) throw IOException("Is a directory: ${file.path}")
        if (file.length() > MAX_READ_SIZE) throw IOException("File too large (${file.length()} bytes, max $MAX_READ_SIZE)")
        val bytes = file.readBytes()
        return JsonObject().apply {
            addProperty("path", file.path)
            addProperty("size", bytes.size)
            if (encoding.equals("base64", ignoreCase = true)) {
                addProperty("encoding", "base64")
                addProperty("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            } else {
                addProperty("encoding", "utf8")
                addProperty("content", String(bytes, Charsets.UTF_8))
            }
        }
    }

    private fun write(file: File, content: String, encoding: String, create: Boolean, overwrite: Boolean, mkdirs: Boolean): JsonElement {
        if (file.isDirectory) throw IOException("Is a directory: ${file.path}")
        if (file.exists() && !overwrite) throw IOException("Already exists: ${file.path}")
        if (!file.exists() && !create) throw IOException("Not found: ${file.path}")
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            if (!mkdirs) throw IOException("Not found: ${parent.path}")
            if (!parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create directory: ${parent.path}")
        }
        val bytes = if (encoding.equals("base64", ignoreCase = true)) Base64.decode(content, Base64.DEFAULT) else content.toByteArray(Charsets.UTF_8)
        file.writeBytes(bytes)
        return JsonObject().apply {
            addProperty("path", file.path)
            addProperty("size", bytes.size)
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

    private fun entryOf(file: File) = JsonObject().apply {
        addProperty("name", file.name)
        addProperty("path", file.path)
        addProperty("type", if (file.isDirectory) "directory" else "file")
        addProperty("size", if (file.isDirectory) 0 else file.length())
        addProperty("mtime", file.lastModified())
    }

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

    private fun JsonObject.optBoolean(key: String, default: Boolean): Boolean = get(key)?.takeIf { it.isJsonPrimitive }?.let {
        runCatching { it.asBoolean }.getOrDefault(default)
    } ?: default

    companion object {
        private const val TAG = "DevPluginRequestHandler"
        private const val MAX_READ_SIZE = 32L * 1024 * 1024
    }

}
