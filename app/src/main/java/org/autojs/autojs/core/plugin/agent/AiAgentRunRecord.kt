package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonObject
import org.autojs.plugin.ai.agent.api.AiAgentContract as C
import java.io.Closeable

/** JVM-only run state. No Rhino objects or script runtimes may be retained here. */
internal class AiAgentRunRecord(
    val id: String,
    val goal: String,
    val startedAt: Long,
    val detached: Boolean,
    val interaction: String,
) {
    private val observers = linkedSetOf<(JsonObject) -> Unit>()
    private var sequence = 0L
    private var pending: JsonObject? = null
    private var resultValue: JsonObject? = null
    private var failureValue: JsonObject? = null
    @Volatile var state = "queued"; private set
    val settled: Boolean @Synchronized get() = resultValue != null || failureValue != null
    val result: JsonObject? @Synchronized get() = resultValue?.deepCopy()
    val failure: JsonObject? @Synchronized get() = failureValue?.deepCopy()

    @Synchronized fun observe(observer: (JsonObject) -> Unit): Closeable {
        observers.add(observer)
        if (state != "queued") observer(event("state").apply { addProperty("from", "queued"); addProperty("to", state) })
        pending?.let { observer(it.deepCopy()) }
        failureValue?.let { observer(event("handle_error").apply { add("error", it.deepCopy()) }) }
            ?: resultValue?.let { observer(event("done").apply { it.entrySet().forEach { (key, value) -> add(key, value.deepCopy()) } }) }
        return Closeable { synchronized(this) { observers.remove(observer) } }
    }

    @Synchronized fun accept(value: JsonObject) {
        if (settled || value["runId"]?.asString != id) return
        val next = value["sequence"]?.asLong ?: return
        if (next <= sequence) return
        val type = value["type"]?.asString ?: return
        require(type in setOf("state", "progress", "step", "input", "confirmation", "done", "error"))
        when (type) {
            "state" -> {
                val to = value["to"].asString
                require(to in C.RUN_STATES)
                if (C.isTerminalRunState(state)) return
                state = to; pending = null
            }
            "input", "confirmation" -> pending = value.deepCopy()
            "done" -> {
                require(value["status"]?.asString in C.TERMINAL_RUN_STATES)
                state = value["status"].asString; pending = null
                resultValue = value.deepCopy().apply { remove("type"); remove("sequence"); remove("runId"); addProperty("id", id) }
            }
        }
        sequence = next
        val payload = if (type == "done") event("done").apply { resultValue!!.entrySet().forEach { (k, v) -> add(k, v.deepCopy()) } } else value.deepCopy()
        observers.toList().forEach { it(payload.deepCopy()) }
    }

    /** Current pending/result is replayed on get; old progress and steps are not replayed. */
    @Synchronized fun snapshot(value: JsonObject) {
        if (settled) return
        require(value["runId"]?.asString == id)
        val next = value["sequence"]?.asLong ?: 0
        if (next < sequence) return
        val nextState = value["state"].asString.also { require(it in C.RUN_STATES) }
        val old = state
        state = nextState
        if (old != state) broadcast(event("state").apply { addProperty("from", old); addProperty("to", state) })
        val current = value.getAsJsonObject("pending")?.takeUnless { it["submitted"]?.asBoolean == true }
        if (current?.get("requestId") != pending?.get("requestId")) {
            pending = current?.deepCopy()
            current?.let { broadcast(it) }
        }
        sequence = next
        if (C.isTerminalRunState(state)) value.getAsJsonObject("result")?.let { raw ->
            resultValue = raw.deepCopy().apply {
                addProperty("id", id)
                // Legacy crash records contain only status/error. Keep the failure truthful and typed.
                if (get("error")?.isJsonPrimitive == true) add("error", JsonObject().apply { addProperty("code", raw["error"].asString); addProperty("message", "Agent task was interrupted") })
            }
            pending = null
            broadcast(event("done").apply { resultValue!!.entrySet().forEach { (k, v) -> add(k, v.deepCopy()) } })
        }
    }

    @Synchronized fun fail(code: String, message: String, hint: String? = null) {
        if (settled) return
        val old = state; state = "failed"; pending = null
        failureValue = JsonObject().apply { addProperty("code", code); addProperty("message", message); hint?.let { addProperty("hint", it) } }
        broadcast(event("state").apply { addProperty("from", old); addProperty("to", state) })
        broadcast(event("handle_error").apply { add("error", failureValue!!.deepCopy()) })
    }

    @Synchronized fun pendingRequest(id: String, type: String): JsonObject {
        check(interaction == "script") { C.ERROR_RUN_NOT_INTERACTIVE }
        return checkNotNull(pending?.takeIf { it["requestId"]?.asString == id && it["type"]?.asString == type }) { C.ERROR_RUN_NOT_INTERACTIVE }.deepCopy()
    }
    @Synchronized private fun broadcast(value: JsonObject) { observers.toList().forEach { it(value.deepCopy()) } }
    private fun event(type: String) = JsonObject().apply { addProperty("runId", id); addProperty("type", type) }
}
