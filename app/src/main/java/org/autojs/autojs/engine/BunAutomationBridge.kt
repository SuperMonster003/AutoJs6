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
    private data class Job(val id:String, val capability: String, val method: String, val argsJson: String, val deadline: Long, val future: CompletableFuture<NodeBridgeResponse>,
        val abandoned: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false))
    private val jobs = LinkedHashMap<String, Job>()
    private val executor = Executors.newFixedThreadPool(2)
    private var closed = false

    fun invoke(capability: String, request: JSONObject): NodeBridgeResponse {
        require(capability in methods) { "Unsupported automation capability" }
        if (capability !in grants) throw SecurityException("Automation capability is not granted")
        val action = request.optString("action", "start")
        if (action == "callback") {
            require(capability==Grants.JAVA_INTEROP && request.keys().asSequence().all { it in setOf("action","method","args") })
            val method=request.getString("method");require(method in RuntimeJavaSession.controlMethods)
            val args=request.getJSONArray("args");val future=CompletableFuture<NodeBridgeResponse>()
            provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),NodeBridgeModules.JAVA,method,args.toString(),2000,
                permissions=listOf(NodeBridgePermissionManifest.JAVA_INTEROP))){future.complete(it)}
            return future.get(2500,TimeUnit.MILLISECONDS)
        }
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
        val job = Job(id, capability, method, args.toString(), SystemClock.elapsedRealtime() + timeout.toLong(), CompletableFuture())
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
                        permissions = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method, args.toString()))) { job.future.complete(it) }
                    if(job.abandoned.get() && capability==Grants.SHELL_CONTROL)provider().cancelShellRequest(id)
                    if(job.abandoned.get() && capability==Grants.DIALOGS_CONTROL)provider().cancelDialogRequest(id)
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
        if(job.capability==Grants.SHELL_CONTROL && job.method=="exec")provider().cancelShellRequest(job.id)
        val database = job.capability == Grants.DATABASE_CONTROL && job.method == "open"
        val shell = job.capability == Grants.SHELL_CONTROL && job.method in setOf("spawn","open")
        val sensor = job.capability == Grants.SENSORS_CONTROL && job.method == "subscribe"
        val events = job.capability == Grants.EVENTS_OBSERVE && job.method.startsWith("observe")
        val notification = job.capability == Grants.NOTIFICATIONS_POST && job.method == "notify"
        if (job.capability == Grants.DIALOGS_CONTROL) provider().cancelDialogRequest(job.id)
        val awake = job.capability == Grants.DEVICE_CONTROL && job.method == "keepScreenOn"
        val mail = job.capability == Grants.MAIL_CONTROL && job.method == "open"
        val epub = job.capability == Grants.EPUB_CONTROL && job.method == "open"
        val speech = job.capability == Grants.SPEECH_CONTROL && job.method == "open"
        val tts = job.capability == Grants.TTS_CONTROL && job.method == "open"
        val javaObject = job.capability == Grants.JAVA_INTEROP
        val androidObject=job.capability==Grants.ANDROID_OBJECTS && job.method in setOf("createPaint","createColorStateList")
        val task=job.capability==Grants.TASKS_CONTROL && job.method in setOf("addTimedTask","addIntentTask")
        if (!database && !sensor && !shell && !events && !notification && !awake && !androidObject && !task && !javaObject && !tts && !speech && !epub && !mail) { job.future.cancel(false); return }
        // Preserve late creation replies so cancelled callers cannot strand native resources.
        job.future.whenComplete { response, _ ->
            if (response?.ok == true) runCatching {
                if (mail) { provider().closeMailClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
                if (epub) { provider().closeEpubClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
                if (speech) { provider().closeSpeechClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
                if (tts) { provider().closeTtsClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
                if (javaObject) { provider().releaseJavaResult(response.resultJson); return@runCatching }
                if(androidObject || task) {
                    val value=JSONObject(response.resultJson.orEmpty())
                    provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(),if(task)NodeBridgeModules.TASKS else NodeBridgeModules.ANDROID_OBJECTS,
                        if(task)if(value.getString("kind")=="intent")"removeIntentTask" else "removeTimedTask" else "close",JSONArray().put(value.get("id")).toString(),5000,
                        permissions=listOf(if(task)NodeBridgePermissionManifest.TASKS else NodeBridgePermissionManifest.ANDROID_OBJECTS))){}
                    return@runCatching
                }
                if (awake) {
                    provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.DEVICE, "cancelKeepingAwake",
                        JSONArray().put(JSONArray(job.argsJson).optString(1)).toString(), 5000, permissions = listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER))) {}
                    return@runCatching
                }
                if (notification) {
                    val args = JSONArray().put(org.json.JSONTokener(response.resultJson.orEmpty()).nextValue()).put(JSONArray(job.argsJson).optString(1, ""))
                    provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.NOTIFICATIONS, "cancel", args.toString(), 5000, permissions = listOf(NodeBridgePermissionManifest.NOTIFICATIONS))) {}
                    return@runCatching
                }
                val id = JSONObject(response.resultJson.orEmpty()).getString("id")
                val module = if (database) NodeBridgeModules.DATABASE else if (shell) NodeBridgeModules.SHELL else if (events) NodeBridgeModules.EVENTS else NodeBridgeModules.SENSORS
                val args = if (database || shell || events) JSONArray().put(id) else JSONArray().put(JSONObject().put("id", id))
                provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), module, if (database || shell || events) "close" else "unsubscribe",
                    args.toString(), 5000, permissions = listOf(if (database) NodeBridgePermissionManifest.DATABASE else if (shell) NodeBridgePermissionManifest.SHELL else if (events) NodeBridgePermissionManifest.EVENTS else NodeBridgePermissionManifest.SENSORS))) {}
            }
        }
    }

    private fun success(value: Any?) = NodeBridgeResponse.success("bun-automation", NodeBridgeJson.valueToJson(value))

    companion object {
        fun nodeCapabilities(grants: List<String>): List<String> = buildList {
            if (Grants.MAIL_CONTROL in grants) add(NodeBridgePermissionManifest.MAIL)
            if (Grants.EPUB_CONTROL in grants) add(NodeBridgePermissionManifest.EPUB)
            if (Grants.SPEECH_CONTROL in grants) add(NodeBridgePermissionManifest.SPEECH)
            if (Grants.TTS_CONTROL in grants) add(NodeBridgePermissionManifest.TTS)
            if (Grants.JAVA_INTEROP in grants) add(NodeBridgePermissionManifest.JAVA_INTEROP)
            if (Grants.ANDROID_OBJECTS in grants) add(NodeBridgePermissionManifest.ANDROID_OBJECTS)
            if (Grants.TASKS_CONTROL in grants) add(NodeBridgePermissionManifest.TASKS)
            if (Grants.DEVICE_CONTROL in grants) add(NodeBridgePermissionManifest.DEVICE)
            if (Grants.DEVICE_POWER in grants) add(NodeBridgePermissionManifest.DEVICE_POWER)
            if (Grants.DEVICE_AUDIO in grants) addAll(listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_AUDIO))
            if (Grants.DIALOGS_CONTROL in grants) add(NodeBridgePermissionManifest.DIALOGS)
            if (Grants.APP_CONTROL in grants) addAll(listOf(NodeBridgePermissionManifest.APP, NodeBridgePermissionManifest.APP_LAUNCH,
                NodeBridgePermissionManifest.APP_SETTINGS, NodeBridgePermissionManifest.APP_ACTIVITY, NodeBridgePermissionManifest.APP_QUERY))
            if (Grants.ACCESSIBILITY_CONTROL in grants) {
                add(NodeBridgePermissionManifest.ACCESSIBILITY)
                if (Grants.ACCESSIBILITY_GESTURE in grants) add(NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE)
            }
            if (Grants.CLIPBOARD_CONTROL in grants) add(NodeBridgePermissionManifest.CLIPBOARD)
            if (Grants.STORAGE_CONTROL in grants) add(NodeBridgePermissionManifest.STORAGE)
            if (Grants.DATABASE_CONTROL in grants) add(NodeBridgePermissionManifest.DATABASE)
            if (Grants.SHELL_CONTROL in grants) add(NodeBridgePermissionManifest.SHELL)
            if (Grants.SHELL_ROOT in grants) add(NodeBridgePermissionManifest.SHELL_ROOT)
            if (Grants.SHELL_SHIZUKU in grants) add(NodeBridgePermissionManifest.SHELL_SHIZUKU)
            if (Grants.SENSORS_CONTROL in grants) add(NodeBridgePermissionManifest.SENSORS)
            if (Grants.EVENTS_OBSERVE in grants) add(NodeBridgePermissionManifest.EVENTS)
            if (Grants.EVENTS_NOTIFICATION in grants) add(NodeBridgePermissionManifest.EVENTS_NOTIFICATION)
            if (Grants.EVENTS_TOAST in grants) add(NodeBridgePermissionManifest.EVENTS_TOAST)
            if (Grants.EVENTS_KEY in grants) add(NodeBridgePermissionManifest.EVENTS_KEY)
            if (Grants.NOTIFICATIONS_POST in grants) add(NodeBridgePermissionManifest.NOTIFICATIONS)
            if (Grants.NOTIFICATIONS_SETTINGS in grants) add(NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS)
        }
        val gestureMethods = setOf("swipe", "gesture")
        val methods = mapOf(
            Grants.MAIL_CONTROL to RuntimeMailBridge.methods,
            Grants.EPUB_CONTROL to RuntimeEpubBridge.methods,
            Grants.SPEECH_CONTROL to RuntimeSpeechBridge.methods,
            Grants.TTS_CONTROL to RuntimeTtsBridge.methods,
            Grants.JAVA_INTEROP to ControlledJavaInteropPolicy.methods,
            Grants.ANDROID_OBJECTS to RuntimeAndroidObjects.methods,
            Grants.TASKS_CONTROL to RuntimeTasksBridge.methods,
            Grants.DEVICE_CONTROL to NodeBridgeModules.deviceMethods,
            Grants.DEVICE_AUDIO to setOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "setAudioStreamVolume"),
            Grants.DIALOGS_CONTROL to NodeBridgeModules.dialogsMethods,
            Grants.EVENTS_OBSERVE to setOf("observeNotification", "observeToast", "observeKey", "observeBroadcasts", "drainEvents", "close"),
            Grants.NOTIFICATIONS_POST to setOf("notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings"),
            Grants.APP_CONTROL to setOf("launchPackage", "launchApp", "openAppSetting", "startActivity", "getPackageName", "getAppName", "isInstalled", "viewFile", "editFile", "startService", "sendBroadcast", "getInstalledApps", "getPackageInfo"),
            Grants.ACCESSIBILITY_CONTROL to setOf("isEnabled", "ensureEnabled", "click", "back", "home", "recentApps", "findByText", "clickText", "findOne", "findAll", "longClick", "setText", "scrollForward", "scrollBackward", "swipe", "gesture"),
            Grants.CLIPBOARD_CONTROL to setOf("getText", "setText", "hasText"),
            Grants.STORAGE_CONTROL to setOf("get", "put", "remove", "clear", "keys", "contains"),
            Grants.SHELL_CONTROL to setOf("exec", "spawn", "open", "drain", "write", "closeInput", "kill", "close", "getAccess", "requestShizukuPermission"),
            Grants.SENSORS_CONTROL to setOf("getAvailableSensors", "once", "subscribe", "unsubscribe", "drainEvents"),
            Grants.DATABASE_CONTROL to setOf("open", "exec", "run", "get", "all", "transaction", "close"),
        )
        private val modules = mapOf(Grants.MAIL_CONTROL to NodeBridgeModules.MAIL,Grants.EPUB_CONTROL to NodeBridgeModules.EPUB,Grants.SPEECH_CONTROL to NodeBridgeModules.SPEECH,Grants.TTS_CONTROL to NodeBridgeModules.TTS,Grants.JAVA_INTEROP to NodeBridgeModules.JAVA,Grants.ANDROID_OBJECTS to NodeBridgeModules.ANDROID_OBJECTS,Grants.TASKS_CONTROL to NodeBridgeModules.TASKS,Grants.DEVICE_CONTROL to NodeBridgeModules.DEVICE, Grants.DEVICE_AUDIO to NodeBridgeModules.MEDIA, Grants.DIALOGS_CONTROL to NodeBridgeModules.DIALOGS, Grants.EVENTS_OBSERVE to NodeBridgeModules.EVENTS, Grants.NOTIFICATIONS_POST to NodeBridgeModules.NOTIFICATIONS, Grants.APP_CONTROL to NodeBridgeModules.APP, Grants.ACCESSIBILITY_CONTROL to NodeBridgeModules.ACCESSIBILITY, Grants.CLIPBOARD_CONTROL to NodeBridgeModules.CLIPBOARD, Grants.STORAGE_CONTROL to NodeBridgeModules.STORAGE, Grants.DATABASE_CONTROL to NodeBridgeModules.DATABASE, Grants.SENSORS_CONTROL to NodeBridgeModules.SENSORS, Grants.SHELL_CONTROL to NodeBridgeModules.SHELL)
    }
}
