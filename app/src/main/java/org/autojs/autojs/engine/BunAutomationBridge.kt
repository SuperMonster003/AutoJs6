package org.autojs.autojs.engine

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Narrow public automation routes, with bounded polling for gestures and service activation. */
internal class BunAutomationBridge(
    private val context: Context,
    private val grants: List<String>,
    private val provider: () -> AndroidNodeBridgeCapabilityProvider,
) : Closeable {
    private data class Job(val capability: String, val method: String, val deadline: Long, val future: CompletableFuture<NodeBridgeResponse>,
        val abandoned: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false))
    private val jobs = LinkedHashMap<String, Job>()
    private val executor = Executors.newFixedThreadPool(2)
    private var closed = false

    fun invoke(capability: String, request: JSONObject): NodeBridgeResponse {
        require(capability in methods) { "Unsupported automation capability" }
        if (capability !in grants) throw SecurityException("Automation capability is not granted")
        val action = request.optString("action", "start")
        if (action == "identity") {
            require(capability == Grants.APP_CONTROL && request.length() == 1)
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION") val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            return success(JSONObject().put("packageName", context.packageName).put("versionName", info.versionName.orEmpty()).put("versionCode", version))
        }
        if (action == "poll" || action == "cancel") {
            require(request.keys().asSequence().all { it in setOf("action", "id") })
            val id = request.opt("id") as? String ?: throw IllegalArgumentException("id must be a string")
            val job = synchronized(jobs) { jobs[id]?.takeIf { it.capability == capability } }
                ?: throw IllegalArgumentException("Automation task is closed or belongs to another capability")
            if (action == "cancel") {
                synchronized(jobs) { jobs.remove(id) }; abandon(job)
                return success(true)
            }
            val remaining = job.deadline - SystemClock.elapsedRealtime()
            try {
                val response = job.future.get(remaining.coerceIn(0L, 750L), TimeUnit.MILLISECONDS)
                synchronized(jobs) { jobs.remove(id) }
                return if (response.ok) success(JSONObject().put("done", true).put("value", org.json.JSONTokener(response.resultJson ?: "null").nextValue())) else response
            } catch (_: TimeoutException) {
                if (SystemClock.elapsedRealtime() < job.deadline) return success(JSONObject().put("done", false))
                synchronized(jobs) { jobs.remove(id) }; abandon(job)
                return NodeBridgeResponse.failure(id, NodeBridgeError.timeout(modules.getValue(capability), "poll"))
            }
        }
        require(action == "start" && request.keys().asSequence().all { it in setOf("action", "method", "args", "timeoutMs") })
        val method = request.opt("method") as? String ?: throw IllegalArgumentException("method must be a string")
        require(method in methods.getValue(capability)) { "Unsupported public automation method: $method" }
        if (method in gestureMethods && Grants.ACCESSIBILITY_GESTURE !in grants) throw SecurityException("Gestures require accessibility.gesture")
        val args = if (request.has("args")) request.optJSONArray("args") ?: throw IllegalArgumentException("args must be an array") else JSONArray()
        val timeout = (request.opt("timeoutMs") ?: 10000) as? Number ?: throw IllegalArgumentException("timeoutMs must be an integer")
        require(timeout.toDouble().isFinite() && timeout.toDouble() == timeout.toLong().toDouble() && timeout.toLong() in 1..120000)
        val id = UUID.randomUUID().toString()
        val job = Job(capability, method, SystemClock.elapsedRealtime() + timeout.toLong(), CompletableFuture())
        synchronized(jobs) {
            check(!closed) { "Automation execution is closed" }
            // Completed calls still belong to their caller until drained, but abandoned timeouts do not fill the map.
            jobs.entries.removeAll { (_, value) -> if (SystemClock.elapsedRealtime() >= value.deadline) { abandon(value); true } else false }
            if (jobs.size >= 4) return NodeBridgeResponse.failure(id, NodeBridgeError.resourceLimit(
                "At most four automation tasks may be outstanding", modules.getValue(capability), method))
            jobs[id] = job
            executor.execute {
                if (job.abandoned.get() || SystemClock.elapsedRealtime() >= job.deadline) { job.future.cancel(false); return@execute }
                if (!job.future.isDone && SystemClock.elapsedRealtime() < job.deadline) try {
                    val module = modules.getValue(capability)
                    provider().dispatch(NodeBridgeRequest(id, module, method, args.toString(), timeout.toLong(),
                        permissions = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method))) { job.future.complete(it) }
                } catch (error: Exception) {
                    job.future.complete(NodeBridgeResponse.failure(id, if (error is IllegalArgumentException || error is org.json.JSONException)
                        NodeBridgeError.invalidRequest(error.message ?: "Invalid automation arguments", modules.getValue(capability), method)
                    else NodeBridgeError.providerFailed(error.message ?: "Automation failed", modules.getValue(capability), method)))
                }
            }
        }
        return success(JSONObject().put("id", id))
    }

    override fun close() {
        synchronized(jobs) { closed = true; jobs.values.forEach(::abandon); jobs.clear(); executor.shutdownNow() }
    }
    private fun abandon(job: Job) {
        if (!job.abandoned.compareAndSet(false, true)) return
        if (job.capability != Grants.DATABASE_CONTROL || job.method != "open") { job.future.cancel(false); return }
        // A cancelled caller cannot receive its newly opened database. Keep that completion
        // observable so the handle can be released, including when open finishes after cancel.
        job.future.whenComplete { response, _ ->
            if (response?.ok == true) runCatching {
                val id = JSONObject(response.resultJson.orEmpty()).getString("id")
                provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.DATABASE, "close",
                    JSONArray().put(id).toString(), 5000, permissions = listOf(NodeBridgePermissionManifest.DATABASE))) {}
            }
        }
    }
    private fun success(value: Any?) = NodeBridgeResponse.success("bun-automation", NodeBridgeJson.valueToJson(value))

    companion object {
        fun nodeCapabilities(grants: List<String>): List<String> = buildList {
            if (Grants.APP_CONTROL in grants) addAll(listOf(NodeBridgePermissionManifest.APP, NodeBridgePermissionManifest.APP_LAUNCH,
                NodeBridgePermissionManifest.APP_SETTINGS, NodeBridgePermissionManifest.APP_ACTIVITY, NodeBridgePermissionManifest.APP_QUERY))
            if (Grants.ACCESSIBILITY_CONTROL in grants) {
                add(NodeBridgePermissionManifest.ACCESSIBILITY)
                if (Grants.ACCESSIBILITY_GESTURE in grants) add(NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE)
            }
            if (Grants.CLIPBOARD_CONTROL in grants) add(NodeBridgePermissionManifest.CLIPBOARD)
            if (Grants.STORAGE_CONTROL in grants) add(NodeBridgePermissionManifest.STORAGE)
            if (Grants.DATABASE_CONTROL in grants) add(NodeBridgePermissionManifest.DATABASE)
        }
        val gestureMethods = setOf("swipe", "gesture")
        val methods = mapOf(
            Grants.APP_CONTROL to setOf("launchPackage", "launchApp", "openAppSetting", "startActivity", "getPackageName", "getAppName", "isInstalled", "viewFile", "editFile", "startService", "sendBroadcast", "getInstalledApps", "getPackageInfo"),
            Grants.ACCESSIBILITY_CONTROL to setOf("isEnabled", "ensureEnabled", "click", "back", "home", "recentApps", "findByText", "clickText", "findOne", "findAll", "longClick", "setText", "scrollForward", "scrollBackward", "swipe", "gesture"),
            Grants.CLIPBOARD_CONTROL to setOf("getText", "setText", "hasText"),
            Grants.STORAGE_CONTROL to setOf("get", "put", "remove", "clear", "keys", "contains"),
            Grants.DATABASE_CONTROL to setOf("open", "exec", "run", "get", "all", "transaction", "close"),
        )
        private val modules = mapOf(Grants.APP_CONTROL to NodeBridgeModules.APP, Grants.ACCESSIBILITY_CONTROL to NodeBridgeModules.ACCESSIBILITY, Grants.CLIPBOARD_CONTROL to NodeBridgeModules.CLIPBOARD, Grants.STORAGE_CONTROL to NodeBridgeModules.STORAGE, Grants.DATABASE_CONTROL to NodeBridgeModules.DATABASE)
    }
}
