package org.autojs.autojs.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.AtomicFile
import android.util.Base64
import org.autojs.autojs.core.pref.SettingsJson
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blob page streams and native data/content/SAF transfers, without a JavaScript interface. The download states are the
 * JSON file store `JsonFileStores.RUNTIME_WEB_DOWNLOADS`, read and written with `SettingsJson` (settings P5.1).
 */
internal class RuntimeWebLocalDownloads(private val web: RuntimeWebView) {
    private val context = web.context.applicationContext
    private val pageJobs = LinkedHashSet<Job>()
    private val main = Handler(Looper.getMainLooper())
    private val pageScript by lazy { context.assets.open("runtime-web/download-stream.js").bufferedReader().use { it.readText() } }
    fun cancelPageJobs() { pageJobs.toList().forEach { it.fail(IllegalStateException("The source WebView was closed or navigated")) } }
    fun start(options: JSONObject): JSONObject {
        val url = options.getString("url"); val scheme = Uri.parse(url).scheme?.lowercase()
        require(scheme in setOf("blob", "data", "content", "http", "https")) { "Unsupported download URL" }
        require(!options.has("destinationUri") || !options.has("directoryUri")) { "Choose destinationUri or directoryUri" }
        if (scheme == "blob") check(web.settings.javaScriptEnabled && !web.disposed) { "Blob downloads require an active JavaScript-enabled WebView" }
        val name = options.optString("fileName").ifBlank { "download-${System.currentTimeMillis()}.bin" }.also(RuntimeWebDownloads::checkFileName)
        val timeoutMs = options.opt("timeoutMs")?.let { RuntimeViewAttributes.integer(it, 100, Int.MAX_VALUE).toLong() } ?: 120000L
        val job = Job(options, name)
        try {
            if (scheme == "content") job.hold(Uri.parse(url), false)
            for (key in listOf("destinationUri", "directoryUri")) if (options.has(key)) {
                val uri = Uri.parse(options.getString(key))
                if (key == "directoryUri") require(DocumentsContract.isTreeUri(uri)) { "directoryUri must be a selected document tree" }
                job.hold(uri, true)
            }
        } catch (error: Throwable) { job.release(); throw error }
        active[job.id] = job
        if (scheme == "blob") pageJobs.add(job)
        main.postDelayed(job.timeout, timeoutMs)
        io.execute { job.begin() }
        return JSONObject().put("id", job.id).put("url", url).put("fileName", name)
    }
    fun get(id: String): Any {
        active[id]?.let { return if (it.removed.get()) JSONObject.NULL else it.snapshot() }
        val file = metadata(context, id)
        if (!file.exists()) return JSONObject.NULL
        val value = SettingsJson.parseObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
        if (value.optString("status") == "running") {
            value.put("status", "failed").put("reason", 1000).put("error", "Download interrupted by process termination")
            File(context.cacheDir, "runtime-web-downloads/${uuid(id)}.part").delete()
            if (value.optBoolean("pendingOwned") && value.has("pendingUri")) runCatching {
                val uri = Uri.parse(value.getString("pendingUri"))
                if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri) else context.contentResolver.delete(uri, null, null)
            }
            save(file, value)
        }
        return value
    }
    fun remove(id: String): Boolean {
        active[id]?.let { it.remove(); return true }
        val value = get(id) as? JSONObject ?: return false
        if (value.optBoolean("managed") && !value.isNull("contentUri")) context.contentResolver.delete(Uri.parse(value.getString("contentUri")), null, null)
        AtomicFile(metadata(context, id)).delete(); return true
    }
    fun read(id: String, offset: Long, length: Int): JSONObject {
        val value = get(id) as? JSONObject ?: error("Unknown download")
        check(value.getString("status") == "successful") { "The download is not complete" }
        return requireNotNull(context.contentResolver.openInputStream(Uri.parse(value.getString("contentUri")))).use { RuntimeWebDownloads.readBytes(it, offset, length) }
    }

    private inner class Job(private val options: JSONObject, private val fileName: String) {
        val id = "web:${UUID.randomUUID()}"
        val removed = AtomicBoolean()
        private val stopped = AtomicBoolean()
        private val cleaned = AtomicBoolean()
        private val lock = Any()
        private val leases = LinkedHashSet<Uri>()
        private val url = options.getString("url")
        private val scheme = Uri.parse(url).scheme?.lowercase()
        private val temporary = File(context.cacheDir, "runtime-web-downloads/${uuid(id)}.part")
        private val key = "__autojs6_download_${uuid(id).replace('-', '_')}"
        private val managed = !options.has("destinationUri") && !options.has("directoryUri")
        private val value = JSONObject().put("id", id).put("url", url).put("fileName", fileName).put("status", "running").put("reason", 0)
            .put("bytesDownloaded", 0L).put("totalBytes", -1L).put("mimeType", options.optString("mimeType", "application/octet-stream"))
            .put("localUri", JSONObject.NULL).put("contentUri", JSONObject.NULL).put("managed", managed)
        private var output: FileOutputStream? = null
        @Volatile private var input: InputStream? = null
        private var published: Uri? = null
        private var ownsOutput = false
        private var destinationOpened = false
        private var lastSave = 0L
        val timeout = Runnable { fail(IllegalStateException("Download timed out")) }
        fun hold(uri: Uri, writable: Boolean) {
            RuntimeWebFileAccess.validate(context, uri, writable)
            if (uri !in leases) { RuntimeWebFileAccess.acquire(context, uri); leases.add(uri) }
        }
        fun release() { leases.forEach { runCatching { RuntimeWebFileAccess.release(context, it) } }; leases.clear() }
        fun snapshot() = synchronized(lock) { JSONObject(value.toString()) }
        private fun persist() { save(metadata(context, id), value); lastSave = System.currentTimeMillis() }
        fun begin() {
            try {
                synchronized(lock) { if (stopped.get()) return; check(temporary.parentFile!!.isDirectory || temporary.parentFile!!.mkdirs()); output = FileOutputStream(temporary); persist() }
                if (options.has("destinationUri")) {
                    val uri = Uri.parse(options.getString("destinationUri"))
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { require(it.read() == -1) { "destinationUri must be empty; existing contents are never overwritten" } }
                }
                if (scheme == "blob") { main.post { page("start") { pull() } }; return }
                var connection: HttpURLConnection? = null
                try {
                    val source = when (scheme) {
                        "data" -> {
                            val comma = url.indexOf(','); require(comma >= 5) { "Invalid data URL" }
                            val header = url.substring(5, comma)
                            if (!options.has("mimeType")) synchronized(lock) { value.put("mimeType", header.substringBefore(';').ifBlank { "text/plain" }) }
                            val bytes = decodeData(url.substring(comma + 1), header.split(';').last().equals("base64", true))
                            synchronized(lock) { value.put("totalBytes", bytes.size) }; ByteArrayInputStream(bytes)
                        }
                        "content" -> requireNotNull(context.contentResolver.openInputStream(Uri.parse(url)))
                        else -> {
                            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 20000; readTimeout = 20000
                                options.optJSONObject("headers")?.let { headers -> headers.keys().forEach { setRequestProperty(it, headers.getString(it)) } }
                                require(responseCode in 200..299) { "Download HTTP $responseCode" }
                            }
                            synchronized(lock) {
                                value.put("totalBytes", connection!!.contentLengthLong)
                                if (!options.has("mimeType")) value.put("mimeType", connection!!.contentType ?: "application/octet-stream")
                            }
                            connection!!.inputStream
                        }
                    }
                    input = source
                    source.use { val bytes = ByteArray(32768); while (!stopped.get()) { val size = it.read(bytes); if (size < 0) break; write(bytes, size) } }
                    input = null
                    if (!stopped.get()) finish()
                } finally { connection?.disconnect() }
            } catch (error: Throwable) { fail(error) }
        }
        private fun write(bytes: ByteArray, size: Int = bytes.size) = synchronized(lock) {
            if (stopped.get()) return@synchronized
            requireNotNull(output).write(bytes, 0, size)
            value.put("bytesDownloaded", value.getLong("bytesDownloaded") + size)
            if (System.currentTimeMillis() - lastSave >= 200) persist()
        }
        private fun script(action: String) = "$pageScript(${JSONObject.quote(action)},${JSONObject.quote(key)},${JSONObject.quote(url)})"
        private fun page(action: String, complete: (Any?) -> Unit) {
            if (stopped.get()) return
            try { web.evaluate(script(action), { result -> if (!stopped.get()) result.fold(complete, ::fail) }) }
            catch (error: Throwable) { fail(error) }
        }
        private fun pull() {
            page("pull") { raw ->
                try {
                    val chunk = raw as? JSONObject ?: error("The source page returned no download state")
                    if (chunk.has("error")) error(chunk.getString("error"))
                    io.execute {
                        try {
                            synchronized(lock) {
                                if (stopped.get()) return@execute
                                if (!options.has("mimeType") && chunk.optString("mimeType").isNotEmpty()) value.put("mimeType", chunk.getString("mimeType"))
                                if (chunk.has("totalBytes")) value.put("totalBytes", chunk.getLong("totalBytes"))
                            }
                            if (chunk.has("base64")) {
                                require(chunk.getString("base64").length <= 43692) { "Page download chunk too large" }
                                val bytes = Base64.decode(chunk.getString("base64"), Base64.NO_WRAP); require(bytes.size <= 32768); write(bytes)
                            }
                            if (chunk.optBoolean("done")) finish() else main.postDelayed({ pull() }, if (chunk.optBoolean("pending")) 10 else 0)
                        } catch (error: Throwable) { fail(error) }
                    }
                } catch (error: Throwable) { fail(error) }
            }
        }
        private fun finish() {
            try {
                synchronized(lock) {
                    if (stopped.get()) return
                    output?.close(); output = null
                    val mime = value.getString("mimeType").substringBefore(';')
                    val uri = when {
                        options.has("destinationUri") -> Uri.parse(options.getString("destinationUri"))
                        options.has("directoryUri") -> {
                            val tree = Uri.parse(options.getString("directoryUri")); ownsOutput = true
                            requireNotNull(DocumentsContract.createDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), mime, fileName))
                        }
                        Build.VERSION.SDK_INT >= 29 -> {
                            ownsOutput = true
                            requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }))
                        }
                        else -> {
                            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { check(isDirectory || mkdirs()) }
                            val file = File(dir, fileName); check(file.createNewFile()) { "The destination already exists" }; ownsOutput = true
                            val authority = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PROVIDERS).providers.orEmpty().single { it.name == androidx.core.content.FileProvider::class.java.name }.authority
                            androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                        }
                    }
                    published = uri; value.put("pendingUri", uri.toString()).put("pendingOwned", ownsOutput); persist(); destinationOpened = true
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { target -> temporary.inputStream().use { source ->
                        val bytes = ByteArray(32768)
                        while (true) { check(!stopped.get()) { "Download cancelled" }; val size = source.read(bytes); if (size < 0) break; target.write(bytes, 0, size) }
                    } }
                    if (managed && Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    if (!stopped.compareAndSet(false, true)) return
                    value.put("contentUri", uri.toString()).put("localUri", uri.toString()).put("status", "successful").put("totalBytes", value.getLong("bytesDownloaded"))
                    value.remove("pendingUri"); value.remove("pendingOwned")
                    persist()
                }
                cleanup(false)
            } catch (error: Throwable) {
                if (!stopped.get()) fail(error) else {
                    synchronized(lock) { value.put("status", "failed").put("reason", 1000).put("error", error.message); runCatching { if (!removed.get()) persist() } }
                    cleanup(true)
                }
            }
        }
        fun remove() {
            removed.set(true); fail(IllegalStateException("Download cancelled"))
            io.execute { synchronized(lock) {
                if (managed && value.optString("status") == "successful" && published != null) context.contentResolver.delete(published!!, null, null)
                AtomicFile(metadata(context, id)).delete()
            } }
        }
        fun fail(error: Throwable) {
            if (!stopped.compareAndSet(false, true)) return
            io.execute {
                try {
                    runCatching { input?.close() }; input = null
                    synchronized(lock) {
                        value.put("status", "failed").put("reason", 1000).put("error", error.message ?: "Download failed")
                        runCatching { if (!removed.get()) persist() }
                    }
                } finally { cleanup(true) }
            }
        }
        private fun cleanup(failed: Boolean) {
            if (!cleaned.compareAndSet(false, true)) return
            synchronized(lock) {
                runCatching { output?.close() }; output = null; temporary.delete()
                if (failed && published != null) runCatching {
                    if (ownsOutput) { if (DocumentsContract.isDocumentUri(context, published)) DocumentsContract.deleteDocument(context.contentResolver, published!!) else context.contentResolver.delete(published!!, null, null) }
                    else if (destinationOpened) context.contentResolver.openOutputStream(published!!, "wt")?.close()
                }
                if (removed.get()) AtomicFile(metadata(context, id)).delete()
            }
            release(); active.remove(id, this)
            main.post {
                main.removeCallbacks(timeout); pageJobs.remove(this)
                if (scheme == "blob" && !web.disposed) runCatching { web.evaluate(script("cancel"), {}) }
            }
        }
    }
    companion object {
        private val active = ConcurrentHashMap<String, Job>()
        private val io = Executors.newCachedThreadPool { task -> Thread(task, "runtime-web-download").apply { isDaemon = true } }
        private fun uuid(id: String): String { require(id.startsWith("web:")); return UUID.fromString(id.removePrefix("web:")).toString() }
        private fun metadata(context: Context, id: String) = File(context.filesDir, "runtime-web-downloads/${uuid(id)}.json")
        private fun save(file: File, value: JSONObject) {
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()); val atomic = AtomicFile(file); val stream = atomic.startWrite()
            try { stream.write(SettingsJson.stringify(value).toByteArray()); atomic.finishWrite(stream) } catch (error: Throwable) { atomic.failWrite(stream); throw error }
        }
        internal fun decodeData(payload: String, base64: Boolean): ByteArray {
            val raw = payload.toByteArray(Charsets.UTF_8); val bytes = java.io.ByteArrayOutputStream(); var i = 0
            while (i < raw.size) {
                if (raw[i].toInt() == 37) {
                    require(i + 2 < raw.size) { "Invalid data URL escape" }
                    val high = Character.digit(raw[i + 1].toInt().toChar(), 16); val low = Character.digit(raw[i + 2].toInt().toChar(), 16)
                    require(high >= 0 && low >= 0) { "Invalid data URL escape" }; bytes.write(high * 16 + low); i += 3
                } else bytes.write(raw[i++].toInt())
            }
            return if (base64) Base64.decode(bytes.toByteArray(), Base64.DEFAULT) else bytes.toByteArray()
        }
    }
}
