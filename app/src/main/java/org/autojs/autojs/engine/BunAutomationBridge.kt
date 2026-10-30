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
        val abandoned: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false), val module: String = "")
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
                if (!response.ok && capability == Grants.AGENT_CONTROL)
                    return success(JSONObject().put("done", true).put("error", response.error?.toJsonObject()))
                return if (response.ok) success(JSONObject().put("done", true).put("value", org.json.JSONTokener(response.resultJson ?: "null").nextValue())) else response
            } catch (_: TimeoutException) {
                if (SystemClock.elapsedRealtime() < job.deadline) return success(JSONObject().put("done", false))
                synchronized(jobs) { jobs.remove(id) }; abandon(job)
                return NodeBridgeResponse.failure(id, NodeBridgeError.timeout(modules.getValue(capability), "poll"))
            }
        }
        require(action == "start" && request.keys().asSequence().all { it in setOf("action", "method", "args", "timeoutMs", "module") })
        val method = request.opt("method") as? String ?: throw IllegalArgumentException("method must be a string")
        // A grant may also answer a second Node module (the notice extras, powerManager), named by the request.
        val module = if (request.has("module")) (request.opt("module") as? String ?: throw IllegalArgumentException("module must be a string"))
            .also { require(extraModules[capability]?.containsKey(it) == true) { "Unsupported automation module: $it" } } else modules.getValue(capability)
        require(method in (extraModules[capability]?.get(module) ?: methods.getValue(capability))) { "Unsupported public automation method: $method" }
        if (method in gestureMethods && Grants.ACCESSIBILITY_GESTURE !in grants) throw SecurityException("Gestures require accessibility.gesture")
        val args = if (request.has("args")) request.optJSONArray("args") ?: throw IllegalArgumentException("args must be an array") else JSONArray()
        if (capability == Grants.ENGINES_CONTROL) checkEngines(method, args)
        if (capability == Grants.FLOW_CONTROL) {
            val available = nodeCapabilities(grants)
            require(NodeBridgePermissionManifest.ACCESSIBILITY in available) { "Flow tools require accessibility.control" }
            val missing = RuntimeFlowPolicy.capabilities(method, args.toString()).filter { it !in available }
            if (missing.isNotEmpty()) throw SecurityException("Flow tool requires additional grants: ${missing.joinToString()}")
        }
        val timeout = (request.opt("timeoutMs") ?: 10000) as? Number ?: throw IllegalArgumentException("timeoutMs must be an integer")
        require(timeout.toDouble().isFinite() && timeout.toDouble() == timeout.toLong().toDouble() && timeout.toLong() in 1..maxTimeout(capability))
        val id = UUID.randomUUID().toString()
        val job = Job(id, capability, method, args.toString(), SystemClock.elapsedRealtime() + timeout.toLong(), CompletableFuture(), module = module)
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
                    provider().dispatch(NodeBridgeRequest(id, module, method, args.toString(), timeout.toLong(),
                        permissions = permissions(module, method, args))) { job.future.complete(it) }
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

    /**
     * engines (api-coverage P4.1b): starting scripts needs engines.exec, which the Node engines capability would imply; the Agent
     * routes of engines (stop with an invocation, execScript with agentInvocationId) stay with agent.control.
     */
    private fun checkEngines(method: String, args: JSONArray) {
        if (method in engineStartMethods && Grants.ENGINES_EXEC !in grants) throw SecurityException("Starting scripts requires engines.exec")
        require(!(method == "stop" && args.opt(0) is JSONObject)) { "engines.stop takes an execution id" }
        require(!(method == "execScript" && args.optJSONObject(2)?.has("agentInvocationId") == true)) { "Agent invocations need agent.control" }
    }

    /**
     * The capabilities a request declares: those of the route, and for plugin tools (Q137 (a)) the optional tokens the
     * tool bridge looks for (USER_GRANTED providers, screen inputs, file inputs and outputs) when their grants are on.
     */
    private fun permissions(module: String, method: String, args: JSONArray): List<String> {
        val required = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method, args.toString())
        if (module != NodeBridgeModules.PLUGIN_TOOLS) return required
        return required + buildList {
            if (Grants.PLUGIN_TOOLS_USER_GRANTED in grants) add(NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED)
            if (Grants.SCREEN_CAPTURE in grants) add(NodeBridgePermissionManifest.SCREEN_CAPTURE)
            if (Grants.FILES_CONTROL in grants) addAll(listOf(NodeBridgePermissionManifest.FILES, NodeBridgePermissionManifest.FILES_WRITE))
        }
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
        val awake = job.capability == Grants.DEVICE_CONTROL && job.method in setOf("keepScreenOn", "keepAwake")
        val mail = job.capability == Grants.MAIL_CONTROL && job.method == "open"
        val flow = job.capability == Grants.FLOW_CONTROL && job.method == "open"
        if (job.capability == Grants.AGENT_CONTROL && job.method == "requestAccess")
            provider().closeAiAgentClient(JSONArray(job.argsJson).getString(0))
        val aiAgent = job.capability == Grants.AGENT_CONTROL && job.method == "open"
        val ai = job.capability == Grants.AI_CONTROL && job.method == "open"
        val epub = job.capability == Grants.EPUB_CONTROL && job.method == "open"
        val speech = job.capability == Grants.SPEECH_CONTROL && job.method == "open"
        val tts = job.capability == Grants.TTS_CONTROL && job.method == "open"
        val javaObject = job.capability == Grants.JAVA_INTEROP
        val androidObject=job.capability==Grants.ANDROID_OBJECTS && job.method in setOf("createPaint","createColorStateList")
        val task=job.capability==Grants.TASKS_CONTROL && job.method in setOf("addTimedTask","addIntentTask")
        if (!database && !sensor && !shell && !events && !notification && !awake && !androidObject && !task && !javaObject && !tts && !speech && !aiAgent && !ai && !epub && !mail && !flow) { job.future.cancel(false); return }
        // Preserve late creation replies so cancelled callers cannot strand native resources.
        job.future.whenComplete { response, _ ->
            if (response?.ok == true) runCatching {
                if (mail) { provider().closeMailClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
                if (flow) { provider().closeFlowClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
                if (aiAgent) { provider().closeAiAgentClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
                if (ai) { provider().closeAiClient(JSONObject(response.resultJson.orEmpty()).getString("id")); return@runCatching }
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
                        JSONArray().put(JSONArray(job.argsJson).optString(if (job.method == "keepAwake") 2 else 1)).toString(), 5000, permissions = listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER))) {}
                    return@runCatching
                }
                if (notification) {
                    val args = JSONArray().put(org.json.JSONTokener(response.resultJson.orEmpty()).nextValue()).put(JSONArray(job.argsJson).optString(1, ""))
                    provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.NOTIFICATIONS, "cancel", args.toString(), 5000, permissions = listOf(NodeBridgePermissionManifest.NOTIFICATIONS))) {}
                    return@runCatching
                }
                val id = JSONObject(response.resultJson.orEmpty()).getString("id")
                val module = if (database) NodeBridgeModules.DATABASE else if (shell) NodeBridgeModules.SHELL else if (events) NodeBridgeModules.EVENTS else NodeBridgeModules.SENSORS
                val args = if (ai || database || shell || events) JSONArray().put(id) else JSONArray().put(JSONObject().put("id", id))
                provider().dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), module, if (ai || database || shell || events) "close" else "unsubscribe",
                    args.toString(), 5000, permissions = listOf(if (database) NodeBridgePermissionManifest.DATABASE else if (shell) NodeBridgePermissionManifest.SHELL else if (events) NodeBridgePermissionManifest.EVENTS else NodeBridgePermissionManifest.SENSORS))) {}
            }
        }
    }

    private fun success(value: Any?) = NodeBridgeResponse.success("bun-automation", NodeBridgeJson.valueToJson(value))

    companion object {
        fun nodeCapabilities(grants: List<String>): List<String> = buildList {
            if (Grants.MAIL_CONTROL in grants) add(NodeBridgePermissionManifest.MAIL)
            if (Grants.AI_CONTROL in grants) add(NodeBridgePermissionManifest.AI)
            if (Grants.AGENT_CONTROL in grants) add(NodeBridgePermissionManifest.AI_AGENT)
            if (Grants.EPUB_CONTROL in grants) add(NodeBridgePermissionManifest.EPUB)
            if (Grants.SPEECH_CONTROL in grants) add(NodeBridgePermissionManifest.SPEECH)
            if (Grants.TTS_CONTROL in grants) add(NodeBridgePermissionManifest.TTS)
            if (Grants.MEDIAINFO_CONTROL in grants) addAll(listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_METADATA))
            // Rhino's media module (api-coverage P4.1c, Q152 (a)): playback and the media library scan of Node.
            if (Grants.MEDIA_CONTROL in grants) addAll(listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_PLAYBACK))
            if (Grants.MEDIA_LIBRARY in grants) addAll(listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_LIBRARY))
            if (Grants.JAVA_INTEROP in grants) add(NodeBridgePermissionManifest.JAVA_INTEROP)
            if (Grants.ANDROID_OBJECTS in grants) add(NodeBridgePermissionManifest.ANDROID_OBJECTS)
            if (Grants.TASKS_CONTROL in grants) add(NodeBridgePermissionManifest.TASKS)
            if (Grants.DEVICE_CONTROL in grants) add(NodeBridgePermissionManifest.DEVICE)
            if (Grants.DEVICE_POWER in grants) add(NodeBridgePermissionManifest.DEVICE_POWER)
            if (Grants.DEVICE_AUDIO in grants) addAll(listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_AUDIO))
            if (Grants.DEVICE_IDENTIFIERS in grants) add(NodeBridgePermissionManifest.DEVICE_IDENTIFIERS)
            if (Grants.DIALOGS_CONTROL in grants) add(NodeBridgePermissionManifest.DIALOGS)
            if (Grants.APP_CONTROL in grants) addAll(listOf(NodeBridgePermissionManifest.APP, NodeBridgePermissionManifest.APP_LAUNCH,
                NodeBridgePermissionManifest.APP_SETTINGS, NodeBridgePermissionManifest.APP_ACTIVITY, NodeBridgePermissionManifest.APP_QUERY))
            if (Grants.ACCESSIBILITY_CONTROL in grants) {
                add(NodeBridgePermissionManifest.ACCESSIBILITY)
                if (Grants.FLOW_CONTROL in grants) add(NodeBridgePermissionManifest.KEYS)
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
            // The system and engines family (api-coverage P4.1b, Q136 (a)): the Bun grant of each Node capability of Q133 (b).
            systemCapabilities.forEach { (grant, capability) -> if (grant in grants) add(capability) }
            // settings.export and plugin tool outputs write files as the files.write of Node, tool inputs read them as its
            // files; Bun's files.control already reads and writes host files.
            if (Grants.FILES_CONTROL in grants) addAll(listOf(NodeBridgePermissionManifest.FILES, NodeBridgePermissionManifest.FILES_WRITE))
        }
        private val systemCapabilities = listOf(
            Grants.AUTOJS_CONTROL to NodeBridgePermissionManifest.AUTOJS, Grants.AUTOJS_MANAGE to NodeBridgePermissionManifest.AUTOJS_CONTROL,
            Grants.SETTINGS_CONTROL to NodeBridgePermissionManifest.SETTINGS, Grants.SETTINGS_WRITE to NodeBridgePermissionManifest.SETTINGS_WRITE,
            Grants.SYSPROPS_CONTROL to NodeBridgePermissionManifest.SYSPROPS,
            Grants.MEMORY_CONTROL to NodeBridgePermissionManifest.MEMORY, Grants.MEMORY_DUMP to NodeBridgePermissionManifest.MEMORY_DUMP,
            Grants.INSTALLER_CONTROL to NodeBridgePermissionManifest.INSTALLER, Grants.INSTALLER_MUTATE to NodeBridgePermissionManifest.INSTALLER_MUTATE,
            Grants.ENGINES_CONTROL to NodeBridgePermissionManifest.ENGINES, Grants.ENGINES_EXEC to NodeBridgePermissionManifest.ENGINES_EXEC,
            Grants.PLUGIN_TOOLS_CONTROL to NodeBridgePermissionManifest.PLUGIN_TOOLS, Grants.PLUGIN_TOOLS_USER_GRANTED to NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED,
        )
        private val engineStartMethods = setOf("execScript", "execScriptFile", "execAutoFile")
        /** Installs and uninstalls wait for the user like Node's installer (15 min and 5 s); plugin tools run as long. */
        fun maxTimeout(capability: String): Long = if (capability == Grants.INSTALLER_CONTROL || capability == Grants.PLUGIN_TOOLS_CONTROL) 905_000L else 120_000L
        val gestureMethods = setOf("swipe", "gesture")
        val methods = mapOf(
            Grants.FLOW_CONTROL to RuntimeFlowBridge.methods,
            Grants.MAIL_CONTROL to RuntimeMailBridge.methods,
            Grants.AI_CONTROL to RuntimeAiBridge.methods,
            Grants.AGENT_CONTROL to RuntimeAgentBridge.methods,
            Grants.EPUB_CONTROL to RuntimeEpubBridge.methods,
            Grants.SPEECH_CONTROL to RuntimeSpeechBridge.methods,
            Grants.TTS_CONTROL to RuntimeTtsBridge.methods,
            Grants.MEDIAINFO_CONTROL to NodeBridgeModules.mediainfoMethods,
            Grants.MEDIA_CONTROL to NodeBridgeModules.mediaPlaybackMethods,
            Grants.MEDIA_LIBRARY to setOf("scanFile"),
            Grants.JAVA_INTEROP to ControlledJavaInteropPolicy.methods,
            Grants.ANDROID_OBJECTS to RuntimeAndroidObjects.methods,
            Grants.TASKS_CONTROL to RuntimeTasksBridge.methods,
            // Rhino's device members (api-coverage P4.1c): keepAwake / cancelVibration and the RuntimeDeviceBridge queries; the
            // identifiers also need device.identifiers, keepAwake and the pointer location switches device.power (Node capabilities).
            Grants.DEVICE_CONTROL to NodeBridgeModules.deviceMethods + NodeBridgeModules.deviceRhinoControls + RuntimeDeviceBridge.methods,
            Grants.DEVICE_AUDIO to setOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "setAudioStreamVolume"),
            Grants.DIALOGS_CONTROL to NodeBridgeModules.dialogsMethods,
            Grants.EVENTS_OBSERVE to setOf("observeNotification", "observeToast", "observeKey", "observeBroadcasts", "drainEvents", "close"),
            Grants.NOTIFICATIONS_POST to setOf("notify", "cancel", "cancelAll", "getPermissionStatus", "openSettings"),
            Grants.APP_CONTROL to setOf("launchPackage", "launchApp", "openAppSetting", "startActivity", "getPackageName", "getAppName", "isInstalled", "viewFile", "editFile", "startService", "sendBroadcast", "getInstalledApps", "getPackageInfo",
                "resolvePackage", "getUserProfiles", "getInstalledPackages", "getApkInfo", "getAppByAlias", "sendLocalBroadcastSync"),
            Grants.ACCESSIBILITY_CONTROL to setOf("isEnabled", "ensureEnabled", "click", "back", "home", "recentApps", "findByText", "clickText", "findOne", "findAll", "flowProbe", "flowForeground", "longClick", "setText", "scrollForward", "scrollBackward", "swipe", "gesture"),
            Grants.CLIPBOARD_CONTROL to setOf("getText", "setText", "hasText"),
            Grants.STORAGE_CONTROL to setOf("get", "put", "remove", "clear", "keys", "contains"),
            Grants.SHELL_CONTROL to setOf("exec", "spawn", "open", "drain", "write", "closeInput", "kill", "close", "getAccess", "requestShizukuPermission"),
            Grants.SENSORS_CONTROL to setOf("getAvailableSensors", "once", "subscribe", "unsubscribe", "drainEvents"),
            Grants.DATABASE_CONTROL to setOf("open", "exec", "run", "get", "all", "transaction", "close"),
            Grants.AUTOJS_CONTROL to RuntimeSystemBridge.methodsByModule.getValue(RuntimeSystemBridge.AUTOJS),
            Grants.SETTINGS_CONTROL to RuntimeSystemBridge.methodsByModule.getValue(RuntimeSystemBridge.SETTINGS),
            Grants.SYSPROPS_CONTROL to RuntimeSystemBridge.methodsByModule.getValue(RuntimeSystemBridge.SYSPROPS),
            Grants.MEMORY_CONTROL to RuntimeSystemBridge.methodsByModule.getValue(RuntimeSystemBridge.MEMORY),
            Grants.INSTALLER_CONTROL to RuntimeSystemBridge.methodsByModule.getValue(RuntimeSystemBridge.INSTALLER),
            Grants.ENGINES_CONTROL to NodeBridgeModules.enginesMethods,
            Grants.PLUGIN_TOOLS_CONTROL to NodeBridgeModules.pluginToolsMethods,
        )
        /** Second Node modules of a grant, with their methods: the notice extras of notifications.post, powerManager of device.control. */
        val extraModules = mapOf(
            Grants.NOTIFICATIONS_POST to mapOf(RuntimeSystemBridge.NOTICE to RuntimeSystemBridge.methodsByModule.getValue(RuntimeSystemBridge.NOTICE)),
            Grants.DEVICE_CONTROL to mapOf(RuntimeSystemBridge.POWER_MANAGER to RuntimeSystemBridge.methodsByModule.getValue(RuntimeSystemBridge.POWER_MANAGER)),
        )
        private val modules = mapOf(Grants.AGENT_CONTROL to NodeBridgeModules.AI_AGENT,Grants.AI_CONTROL to NodeBridgeModules.AI,Grants.FLOW_CONTROL to NodeBridgeModules.FLOW,Grants.MAIL_CONTROL to NodeBridgeModules.MAIL,Grants.EPUB_CONTROL to NodeBridgeModules.EPUB,Grants.SPEECH_CONTROL to NodeBridgeModules.SPEECH,Grants.TTS_CONTROL to NodeBridgeModules.TTS,Grants.MEDIAINFO_CONTROL to NodeBridgeModules.MEDIAINFO,Grants.MEDIA_CONTROL to NodeBridgeModules.MEDIA,Grants.MEDIA_LIBRARY to NodeBridgeModules.MEDIA_STORE,Grants.JAVA_INTEROP to NodeBridgeModules.JAVA,Grants.ANDROID_OBJECTS to NodeBridgeModules.ANDROID_OBJECTS,Grants.TASKS_CONTROL to NodeBridgeModules.TASKS,Grants.DEVICE_CONTROL to NodeBridgeModules.DEVICE, Grants.DEVICE_AUDIO to NodeBridgeModules.MEDIA, Grants.DIALOGS_CONTROL to NodeBridgeModules.DIALOGS, Grants.EVENTS_OBSERVE to NodeBridgeModules.EVENTS, Grants.NOTIFICATIONS_POST to NodeBridgeModules.NOTIFICATIONS, Grants.APP_CONTROL to NodeBridgeModules.APP, Grants.ACCESSIBILITY_CONTROL to NodeBridgeModules.ACCESSIBILITY, Grants.CLIPBOARD_CONTROL to NodeBridgeModules.CLIPBOARD, Grants.STORAGE_CONTROL to NodeBridgeModules.STORAGE, Grants.DATABASE_CONTROL to NodeBridgeModules.DATABASE, Grants.SENSORS_CONTROL to NodeBridgeModules.SENSORS, Grants.SHELL_CONTROL to NodeBridgeModules.SHELL,
            Grants.AUTOJS_CONTROL to RuntimeSystemBridge.AUTOJS, Grants.SETTINGS_CONTROL to RuntimeSystemBridge.SETTINGS, Grants.SYSPROPS_CONTROL to RuntimeSystemBridge.SYSPROPS,
            Grants.MEMORY_CONTROL to RuntimeSystemBridge.MEMORY, Grants.INSTALLER_CONTROL to RuntimeSystemBridge.INSTALLER, Grants.ENGINES_CONTROL to NodeBridgeModules.ENGINES,
            Grants.PLUGIN_TOOLS_CONTROL to NodeBridgeModules.PLUGIN_TOOLS)
    }
}
