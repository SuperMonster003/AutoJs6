package org.autojs.autojs.core.plugin.agent

import android.os.Binder
import android.os.Bundle
import android.os.Looper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLink
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentRunCallback
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Host-process callback ownership lets detached runs outlive individual script runtimes. */
internal class ThreeStoveAgentRunRegistry(private val controller: ThreeStoveAgentLinkController, private val pluginUid: () -> Int) {
    private val runs = ConcurrentHashMap<String, ThreeStoveAgentRunRecord>()
    private val poller = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "agent-run-observer").apply { isDaemon = true } }
    init { controller.observeLoss { code ->
        val previous = runs.values.toList(); runs.clear()
        previous.forEach { it.fail(code, "3-Stove Agent link was lost") }
    } }
    val available: Boolean get() = controller.status.value.state == "attached"

    fun start(goal: String, options: JsonObject): ThreeStoveAgentRunRecord {
        val detached = options["detached"]?.asBoolean == true
        val interaction = options["interaction"]?.asString ?: "plugin"
        var startedId: String? = null
        return try {
            ensureConnection()
            val channel = Channel(pluginUid())
            call { link ->
                val reply = decode(link.startRun(envelope(C.KEY_RUN_REQUEST_JSON, JsonObject().apply {
                    addProperty("goal", goal); add("options", options); addProperty("origin", "script")
                }), channel))
                val id = reply["runId"].asString.also { startedId = it }
                val snapshot = decode(link.getRun(reference(id)))
                ThreeStoveAgentRunRecord(id, goal, snapshot["startedAt"].asLong, detached, interaction).also {
                    runs[id] = it; channel.attach(it)
                }
            }
        } catch (failure: Exception) {
            startedId?.let { id -> runCatching { cancelId(id, "admission-failed") } }
            val control = failure as? ThreeStoveAgentControlException
            rejected(goal, detached, interaction, control?.code ?: C.ERROR_PLUGIN_UNAVAILABLE, control?.hint)
        }
    }

    fun get(id: String): ThreeStoveAgentRunRecord? {
        runs[id]?.let { return it }
        ensureConnection()
        return try { call { link ->
            runs[id]?.let { return@call it }
            val row = decode(link.getRun(reference(id)))
            val candidate = ThreeStoveAgentRunRecord(id, row["goal"].asString, row["startedAt"].asLong,
                row["detached"]?.asBoolean == true, row["interaction"]?.asString ?: "plugin")
            candidate.snapshot(row)
            if (candidate.settled) candidate else {
                runs[id] = candidate; poll(candidate); candidate
            }
        } } catch (error: ThreeStoveAgentControlException) { if (error.code == C.ERROR_RUN_NOT_FOUND) null else throw error }
    }

    /** Foreign/UI tasks have no callback on this host. Observe current snapshots without replaying actions. */
    private fun poll(record: ThreeStoveAgentRunRecord) {
        poller.schedule({
            if (runs[record.id] !== record || record.settled) { runs.remove(record.id, record); return@schedule }
            try { record.snapshot(call { decode(it.getRun(reference(record.id))) }) }
            catch (error: Exception) { record.fail((error as? ThreeStoveAgentControlException)?.code ?: C.ERROR_PLUGIN_UNAVAILABLE, "Unable to observe Agent task") }
            if (record.settled) runs.remove(record.id, record) else poll(record)
        }, 500, TimeUnit.MILLISECONDS)
    }

    fun respond(record: ThreeStoveAgentRunRecord, response: JsonObject): Boolean {
        val type = if (response.has("allowed")) "confirmation" else "input"
        val pending = record.pendingRequest(response["requestId"].asString, type)
        if (type == "confirmation") {
            require(response["allowed"].isJsonPrimitive && response["allowed"].asJsonPrimitive.isBoolean)
            require(response["scope"]?.asString != "run" || pending["allowRunScope"]?.asBoolean == true) { "This confirmation requires scope once" }
        }
        return call { decode(it.respond(envelope(C.KEY_RUN_RESPONSE_JSON, response.deepCopy().apply { addProperty("runId", record.id) })))["accepted"].asBoolean }
    }
    fun cancel(record: ThreeStoveAgentRunRecord, reason: String) { if (!record.settled) cancelId(record.id, reason) }
    /** Revocation only stops live tracked tasks; it must not reconnect to inspect historical IDs. */
    fun cancelTracked(ids: Set<String>, reason: String) {
        runs.values.filter { it.id in ids && !it.settled }.forEach { runCatching { cancel(it, reason) } }
    }
    private fun cancelId(id: String, reason: String) = call { it.cancelRun(envelope(C.KEY_RUN_REF_JSON, JsonObject().apply { addProperty("runId", id); addProperty("reason", reason) })) }
    fun list(limit: Int, offset: Int): JsonObject { ensureConnection(); return call { decode(it.listRuns(envelope(C.KEY_RUN_REQUEST_JSON, JsonObject().apply { addProperty("limit", limit); addProperty("offset", offset) }))) } }
    fun presets(): JsonObject { ensureConnection(); return call { decode(it.listPresets(envelope(C.KEY_RUN_REQUEST_JSON, JsonObject()))) } }
    fun status(): JsonObject = JsonObject().apply {
        val status = controller.status.value
        addProperty("state", status.state); addProperty("queuedCount", status.queuedCount)
        status.runningRunId?.let { addProperty("runningRunId", it) }
        status.errorCode?.let { addProperty("errorCode", it) }
    }
    /** Delegated applications see only their own active IDs and queue count; this never connects. */
    fun status(visible: (String) -> Boolean): JsonObject = status().apply {
        remove("runningRunId")
        val owned = runs.values.filter { !it.settled && visible(it.id) }
        addProperty("queuedCount", owned.count { it.state == "queued" })
        owned.firstOrNull { it.state != "queued" }?.let { addProperty("runningRunId", it.id) }
    }
    // Local catalog metadata remains available without a plugin. Extra roots are optional;
    // their discovery must not reject the local query.
    fun roots(): List<String> = runCatching {
        ensureConnection()
        call { link -> decode(link.status, C.KEY_STATUS_JSON)["scriptRoots"]?.asJsonArray?.map { it.asString }.orEmpty() }
    }.getOrDefault(emptyList())

    private fun ensureConnection() {
        // ServiceConnection callbacks arrive on the main looper; blocking it during a cold
        // bind would deadlock. Normal script execution and async queries use worker threads.
        val allowConnect = Looper.myLooper() != Looper.getMainLooper()
        check(available || allowConnect) {
            "Use a script worker thread for the initial Agent connection"
        }
        val state = runBlocking { controller.ensureConnected(allowConnect) }
        if (state != ThreeStoveAgentUiState.ATTACHED) throw ThreeStoveAgentControlException(C.ERROR_PLUGIN_UNAVAILABLE, ThreeStoveAgentUiPolicy.unavailableHint(state))
    }

    private fun <T> call(action: (IThreeStoveAgentLink) -> T): T {
        if (!available) throw ThreeStoveAgentControlException(C.ERROR_PLUGIN_UNAVAILABLE)
        return runBlocking { controller.withLink(action) }
    }
    private fun rejected(goal: String, detached: Boolean, interaction: String, code: String, hint: String?) =
        ThreeStoveAgentRunRecord(UUID.randomUUID().toString(), goal, System.currentTimeMillis(), detached, interaction).apply {
            fail(code, "3-Stove Agent could not accept the task", hint ?: if (code == C.ERROR_PLUGIN_UNAVAILABLE)
                "Check 3-Stove Agent in AutoJs6 Plugin Center; initial connection requires a script worker thread" else null)
        }

    private inner class Channel(private val ownerUid: Int) : IThreeStoveAgentRunCallback.Stub() {
        private var record: ThreeStoveAgentRunRecord? = null
        private val pending = ArrayDeque<JsonObject>()
        private var invalid = false
        @Synchronized override fun onRunEvent(event: Bundle?) {
            if (HostCapabilityBrokerCore.closeUnexpectedPayload(event)) return
            if (Binder.getCallingUid() != ownerUid) return
            try {
                val value = decode(event, C.KEY_RUN_EVENT_JSON)
                val target = record
                if (target == null) {
                    require(pending.size < 512); pending.addLast(value)
                } else {
                    target.accept(value)
                    if (target.settled) runs.remove(target.id, target)
                }
            } catch (_: Exception) { invalid = true; record?.fail(C.ERROR_INVALID_REQUEST, "Invalid Agent event") }
        }
        @Synchronized fun attach(value: ThreeStoveAgentRunRecord) {
            record = value
            if (invalid) value.fail(C.ERROR_INVALID_REQUEST, "Invalid Agent event")
            else pending.forEach(value::accept)
            pending.clear()
            if (value.settled) runs.remove(value.id, value)
        }
    }

    companion object {
        private fun envelope(key: String, json: JsonObject) = Bundle().apply { putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putString(key, json.toString()) }
        private fun reference(id: String) = envelope(C.KEY_RUN_REF_JSON, JsonObject().apply { addProperty("runId", id) })
        private fun decode(bundle: Bundle?, key: String = C.KEY_RUN_RESPONSE_JSON): JsonObject {
            require(!HostCapabilityBrokerCore.closeUnexpectedPayload(bundle)) { "Unexpected Agent descriptor" }
            require(bundle != null && bundle.getInt(C.KEY_CONTRACT_VERSION) == C.CONTRACT_VERSION)
            bundle.getString(C.KEY_ERROR_CODE)?.let { throw ThreeStoveAgentControlException(it) }
            val json = requireNotNull(bundle.getString(key))
            require(json.toByteArray(Charsets.UTF_8).size <= C.MAX_EVENT_JSON_BYTES)
            return JsonParser.parseString(json).asJsonObject
        }
    }
}

internal class ThreeStoveAgentControlException(val code: String, val hint: String? = null) : IllegalStateException(code)
