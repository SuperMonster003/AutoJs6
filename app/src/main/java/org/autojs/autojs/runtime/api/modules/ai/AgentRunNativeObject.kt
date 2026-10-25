package org.autojs.autojs.runtime.api.modules.ai

import android.os.Looper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentRunRecord
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentRunRegistry
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.ai.AiAgentArguments
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeObject
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Events, Promise settlement, and all Rhino conversion stay on the creating script thread. */
internal class AgentRunNativeObject(
    private val runtime: ScriptRuntime,
    private val record: ThreeStoveAgentRunRecord,
    private val registry: ThreeStoveAgentRunRegistry,
    private val ownsRun: Boolean,
) : NativeObject(), Closeable {
    private val originThread = Thread.currentThread()
    private val closed = AtomicBoolean()
    private val localFailure = java.util.concurrent.atomic.AtomicReference<JsonObject?>()
    private val dispatcher = AiAsyncDispatcher(runtime, keepAlive = !record.detached)
    private val emitter = EventEmitter(runtime.bridges)
    private val adapter = ScriptPromiseAdapter()
    private var releaseResultWait: (() -> Unit)? = null
    private val promise by lazy {
        if (record.detached && !record.settled) releaseResultWait = runtime.loopers.waitWhenIdleToken()::release
        adapter.toJsPromise(runtime)
    }
    private val subscription: Closeable
    private var joining = false

    init {
        RhinoUtils.initNativeObjectPrototype(runtime.topLevelScope, this)
        defineProperty("id", { record.id }, null, READONLY or PERMANENT)
        defineProperty("state", { if (localFailure.get() == null) record.state else "failed" }, null, READONLY or PERMANENT)
        defineProperty("goal", { record.goal }, null, READONLY or PERMANENT)
        defineProperty("startedAt", { record.startedAt.toDouble() }, null, READONLY or PERMANENT)
        defineProperty("detached", { record.detached }, null, READONLY or PERMANENT)
        defineProperty("result", { promise }, null, READONLY or PERMANENT)
        defineProperty("error", { (localFailure.get() ?: record.failure)?.let { js_json_parse(it.toString()) } }, null, READONLY or PERMANENT)
        for (method in listOf("on", "once", "off")) function(method) { args ->
            require(args.size == 2 && args[0] is CharSequence && args[1] is Callable) { "$method requires an event name and listener" }
            val event = args[0].toString(); require(event in EVENTS) { "Unknown Agent event" }
            val listener = args[1] as Callable
            when (method) { "on" -> emitter.on(event, listener); "once" -> emitter.once(event, listener); else -> emitter.removeListener(event, listener) }
            this
        }
        function("respond") { args ->
            require(args.size == 2)
            val id = text(args[0], "requestId", 128)
            val value = JsonParser.parseString(js_json_stringify(args[1]) as? String ?: throw IllegalArgumentException("Answer must be JSON"))
            // A text or boolean answers ask requests; a plan review (kind "plan") is answered with 1 to 8 non-blank steps of at most 200 characters.
            require(value.isJsonPrimitive && (value.asJsonPrimitive.isString || value.asJsonPrimitive.isBoolean) || value.isJsonArray && value.asJsonArray.size() in 1..8 &&
                value.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() && it.asString.trim().codePointCount(0, it.asString.trim().length) <= 200 }) {
                "Answer must be text, boolean or an array of 1 to 8 plan steps"
            }
            require(value.toString().toByteArray(Charsets.UTF_8).size <= 4096)
            registry.respond(record, JsonObject().apply { addProperty("requestId", id); add("value", value) })
        }
        function("confirm") { args ->
            require(args.size in 2..3 && args[1] is Boolean)
            val scope = if (args.size == 3) text(args[2], "scope", 8) else "once"
            require(scope in setOf("once", "run"))
            registry.respond(record, JsonObject().apply {
                addProperty("requestId", text(args[0], "requestId", 128)); addProperty("allowed", args[1] as Boolean); addProperty("scope", scope)
            })
        }
        function("cancel") { args ->
            require(args.size <= 1)
            registry.cancel(record, if (args.isEmpty()) "script-request" else text(args[0], "reason", 256)); this
        }
        function("join") { args ->
            require(args.size <= 1)
            val timeout = if (args.isEmpty()) null else {
                require(args[0] is Number)
                AiAgentArguments.integer(com.google.gson.JsonPrimitive(args[0] as Number), "join timeout", 1, 3_600_000)
            }
            join(timeout)
        }
        subscription = record.observe(::receive)
    }

    override fun getClassName() = "AgentRun"
    private fun function(name: String, body: (Array<out Any?>) -> Any?) = defineProperty(name,
        newBaseFunction(runtime.topLevelScope, name, { args ->
            AiAgentJsErrors.guard {
                check(Thread.currentThread() === originThread) { "AgentRun belongs to its creating script thread" }
                check(!closed.get()) { "AgentRun script has stopped" }
                body(args)
            }
        }, NOT_CONSTRUCTABLE), READONLY or PERMANENT)

    private fun receive(event: JsonObject) {
        if (closed.get() || localFailure.get() != null) return
        val type = event["type"].asString
        val json = event.toString()
        val deliver: (Array<Any?>) -> Unit = { values ->
            val value = JsonParser.parseString(values[0] as String).asJsonObject
            value.remove("type"); value.remove("sequence"); value.remove("runId")
            try {
                when (type) {
                    "handle_error" -> {
                        val error = js_json_parse(value["error"].toString())
                        try { emitter.emit("error", error) } finally { adapter.reject(error) }
                    }
                    "done" -> {
                        val result = js_json_parse(value.toString())
                        try { emitter.emit("done", result) } finally { adapter.resolve(js_json_parse(value.toString())) }
                    }
                    else -> {
                        if (type in setOf("input", "confirmation")) value.addProperty("readOnly", record.interaction != "script")
                        emitter.emit(type, js_json_parse(value.toString()))
                    }
                }
            } finally { if (type in setOf("done", "handle_error")) {
                subscription.close(); emitter.removeAllListeners(); releaseResultWait?.invoke(); releaseResultWait = null
            } }
        }
        try {
            if (type in setOf("done", "handle_error")) dispatcher.terminalValues(arrayOf(json), deliver)
            else dispatcher.dispatchValues(arrayOf(json), deliver)
        } catch (_: AiDispatchQueueOverflowException) {
            val error = JsonObject().apply { addProperty("code", "LIMIT_EXCEEDED"); addProperty("message", "Agent script event queue exceeded its limit") }
            localFailure.set(error)
            subscription.close()
            dispatcher.replaceQueuedWithTerminalValues(arrayOf(error.toString())) { values ->
                val value = js_json_parse(values[0] as String)
                try { emitter.emit("error", value) } finally {
                    adapter.reject(value); emitter.removeAllListeners(); releaseResultWait?.invoke(); releaseResultWait = null
                    // Never make a Binder call while receiving an event under the shared record lock.
                    if (ownsRun && !record.detached) runCatching { registry.cancel(record, "script-event-overflow") }
                }
            }
        }
    }

    private fun join(timeoutMs: Long?): Any? {
        check(Looper.myLooper() != Looper.getMainLooper()) { "AgentRun.join cannot block the UI thread" }
        check(!joining) { "AgentRun.join cannot be called recursively from its listeners" }
        joining = true
        val started = System.nanoTime()
        try {
            while (!record.settled && localFailure.get() == null) {
                dispatcher.drainOnOriginatingThread()
                if (record.settled || localFailure.get() != null) break
                if (timeoutMs != null && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= timeoutMs) throw TimeoutException("AgentRun.join timed out; task is still running")
                Thread.sleep(10)
            }
            dispatcher.drainOnOriginatingThread()
            (localFailure.get() ?: record.failure)?.let { throw org.autojs.autojs.core.plugin.agent.ThreeStoveAgentControlException(it["code"].asString, it["hint"]?.asString) }
            return js_json_parse(checkNotNull(record.result).toString())
        } finally { joining = false }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        subscription.close(); dispatcher.cancelSilently()
        releaseResultWait?.invoke(); releaseResultWait = null
        emitter.removeAllListeners()
        if (ownsRun && !record.detached) runCatching { registry.cancel(record, "script-stopped") }
    }
    companion object {
        private val EVENTS = setOf("state", "progress", "step", "input", "confirmation", "done", "error")
        private fun text(value: Any?, name: String, maximum: Int): String {
            require(value is CharSequence) { "$name must be a string" }
            return AiAgentArguments.text(value.toString(), name, maximum)
        }
    }
}
