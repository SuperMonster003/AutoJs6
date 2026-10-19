package org.autojs.plugin.three.stove.agent.fake

import android.os.*
import org.autojs.plugin.three.stove.agent.api.*
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker
import org.autojs.plugin.common.api.PluginInfo
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Intentionally faulty peer, reachable only through the signature-protected test driver. */
internal class FakeAgentEndpoint(private val enforce: () -> Unit) : IThreeStoveAgentPlugin.Stub(), AutoCloseable {
    private val links = CopyOnWriteArrayList<Link>()
    private val worker = Executors.newSingleThreadExecutor()
    override fun getInfo(): PluginInfo { enforce(); return PluginInfo().apply {
        name = "3-Stove Agent Conformance"; id = "three-stove-agent"; engine = "three-stove-agent"; variant = "default"
        versionName = "1.0.0"; versionCode = 1; supportedAbis = emptyArray()
    } }
    override fun getCapabilities(): Bundle { enforce(); return Bundle().apply { putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION) } }
    override fun attach(config: Bundle, model: IThreeStoveAgentModelBroker, capability: IHostCapabilityBroker, callback: IThreeStoveAgentLinkCallback): IThreeStoveAgentLink {
        enforce()
        return Link(JSONObject(config.getString(C.KEY_LINK_CONFIG_JSON)!!).optString("fixtureMode", "hold"), callback).also { links += it }
    }
    fun control(operation: String, generation: Int, payload: Bundle): Bundle {
        if (operation == "kill") {
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 100)
            return Bundle()
        }
        val link = links[generation]
        try {
            when (operation) {
                "status" -> link.callback.onStatus(payload)
                "event" -> link.callback.onEvent(payload)
                "run" -> {
                    val id = payload.getString("fixture.runId")!!
                    payload.remove("fixture.runId")
                    checkNotNull(link.callbacks[id]).onRunEvent(payload)
                }
                "stats" -> return Bundle().apply {
                    putInt("starts", link.starts.get()); putInt("detaches", link.detaches.get())
                    putInt("attachments", links.size); putInt("uid", Process.myUid()); putInt("pid", Process.myPid())
                }
                else -> error("Unknown fixture operation")
            }
            return Bundle()
        } finally {
            // Sending a descriptor duplicates it in the receiver; release our test-side copy.
            payload.keySet().forEach { key -> @Suppress("DEPRECATION") (payload.get(key) as? ParcelFileDescriptor)?.close() }
        }
    }
    private inner class Link(val mode: String, val callback: IThreeStoveAgentLinkCallback) : IThreeStoveAgentLink.Stub() {
        val starts = AtomicInteger(); val detaches = AtomicInteger()
        val rows = ConcurrentHashMap<String, String>()
        val callbacks = ConcurrentHashMap<String, IThreeStoveAgentRunCallback>()
        override fun getStatus(): Bundle { enforce(); return envelope(C.KEY_STATUS_JSON, JSONObject().put("state", "attached").put("queuedCount", 0)) }
        override fun startRun(request: Bundle, callback: IThreeStoveAgentRunCallback): Bundle {
            enforce(); starts.incrementAndGet()
            val value = JSONObject(request.getString(C.KEY_RUN_REQUEST_JSON)!!)
            val id = UUID.randomUUID().toString()
            rows[id] = JSONObject().put("runId", id).put("goal", value.getString("goal")).put("startedAt", System.currentTimeMillis())
                .put("state", "queued").put("sequence", 0).toString()
            callbacks[id] = callback
            if (mode == "echo") worker.submit {
                callback.onRunEvent(envelope(C.KEY_RUN_EVENT_JSON, JSONObject().put("runId", id).put("sequence", 1)
                    .put("type", "done").put("status", "completed").put("summary", value.getString("goal"))))
            }.get(5, TimeUnit.SECONDS) // Deliberately deliver before the synchronous admission reply.
            return envelope(C.KEY_RUN_RESPONSE_JSON, JSONObject().put("runId", id).put("state", "queued"))
        }
        override fun getRun(reference: Bundle): Bundle { enforce(); return envelope(C.KEY_RUN_RESPONSE_JSON,
            JSONObject(checkNotNull(rows[JSONObject(reference.getString(C.KEY_RUN_REF_JSON)!!).getString("runId")])) ) }
        override fun listRuns(query: Bundle): Bundle { enforce(); return envelope(C.KEY_RUN_RESPONSE_JSON, JSONObject().put("runs", org.json.JSONArray())) }
        override fun listPresets(query: Bundle): Bundle { enforce(); return envelope(C.KEY_RUN_RESPONSE_JSON, JSONObject()) }
        override fun respond(response: Bundle): Bundle { enforce(); return envelope(C.KEY_RUN_RESPONSE_JSON, JSONObject().put("accepted", true)) }
        override fun cancelRun(reference: Bundle) { enforce() }
        override fun updateConfig(config: Bundle) { enforce() }
        override fun detach(reason: Bundle) { enforce(); detaches.incrementAndGet() }
    }
    override fun close() { worker.shutdownNow() }
    private fun envelope(key: String, value: JSONObject) = Bundle().apply {
        putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putString(key, value.toString())
    }
}
