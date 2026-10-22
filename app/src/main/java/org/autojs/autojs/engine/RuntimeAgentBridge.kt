package org.autojs.autojs.engine

import android.content.Context
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentControlException
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentLinkController
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentRunRecord
import org.autojs.autojs.execution.AgentScriptExecutionState
import org.autojs.autojs.project.AgentScriptCatalog
import org.autojs.autojs.runtime.api.ai.AiAgentArguments
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Runtime-owned handles observe the existing Agent registry; the plugin still owns execution. */
internal class RuntimeAgentBridge(
    private val context: Context,
    private val workingDirectory: String,
    private val executionState: () -> AgentScriptExecutionState? = { null },
    private val access: RuntimeAgentAccess? = null,
) : Closeable {
    private val owners = linkedMapOf<String, Owner>()
    private var closed = false
    private val registry by lazy { ThreeStoveAgentLinkController.get(context).scriptRuns }
    private val remote by lazy { RuntimeAgentRemote(context) }
    private val actions = linkedMapOf<String, Closeable>()

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        try {
            val args = JSONArray(request.argsJson)
            if (request.method == "requestAccess") {
                val owner = args.getString(0)
                if (context.packageName == HOST) { success(request, true, reply); return }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        synchronized(actions) {
                            check(!closed)
                            check(!actions.containsKey(owner)) { "Agent access request is already open" }
                            actions[owner] = RuntimeWebActionActivity.agentAccess(context) { granted ->
                                synchronized(actions) { actions.remove(owner) }
                                success(request, granted && remote.hasAccess(), reply)
                            }
                        }
                    } catch (error: Throwable) { reply(failed(request, error)) }
                }
                return
            }
            val value: Any? = when (request.method) {
                "close" -> { require(args.length() == 1); closeClient(args.getString(0)) }
                "context" -> { require(args.length() == 0); executionState()?.contextJson()?.let(::JSONObject) }
                "result" -> { require(args.length() == 1); executionState()?.reportResult(NodeBridgeJson.valueToJson(args.get(0))) ?: false }
                "hasAccess" -> { require(args.length() == 0); if (context.packageName == HOST) access?.allowed() ?: true else remote.hasAccess() }
                else -> {
                    if (context.packageName != HOST) { remote.dispatch(request, reply); return }
                    when (request.method) {
                        "open" -> synchronized(owners) {
                            require(args.length() == 0); check(!closed)
                            val id = UUID.randomUUID().toString(); owners[id] = Owner(); mapOf("id" to id)
                        }
                        else -> {
                            if (access?.allowed() == false) throw ThreeStoveAgentControlException("PERMISSION_DENIED")
                            val owner = synchronized(owners) { owners[args.getString(0)] }
                                ?: throw ThreeStoveAgentControlException("SESSION_CLOSED")
                            when (request.method) {
                                "start" -> owner.start(args.getString(1), JsonParser.parseString(args.getJSONArray(2).toString()).asJsonArray)
                                "poll" -> owner.poll(args.getJSONArray(1))
                                "ack" -> owner.ack(args.getString(1))
                                "cancel" -> owner.cancel(args.getString(1))
                                "release" -> { require(args.length() == 3); owner.release(args.getString(1), args.getBoolean(2)); true }
                                else -> throw IllegalArgumentException("Unknown Agent bridge method")
                            }
                        }
                    }
                }
            }
            success(request, value, reply)
        } catch (error: Throwable) { reply(failed(request, error)) }
    }

    fun closeClient(id: String): Boolean {
        synchronized(actions) { actions.remove(id)?.let { action -> android.os.Handler(android.os.Looper.getMainLooper()).post { action.close() } } }
        if (context.packageName != HOST) return remote.closeClient(id)
        val owner = synchronized(owners) { owners.remove(id) } ?: return false
        owner.close(); return true
    }

    override fun close() {
        val previous = synchronized(owners) { closed = true; owners.values.toList().also { owners.clear() } }
        previous.forEach(Owner::close)
        val pending = synchronized(actions) { actions.values.toList().also { actions.clear() } }
        android.os.Handler(android.os.Looper.getMainLooper()).post { pending.forEach { it.close() } }
        if (context.packageName != HOST) remote.close()
    }

    private data class Packet(val id: String, val bytes: ByteArray, var offset: Int = 0)
    private data class Handle(val record: ThreeStoveAgentRunRecord, val owned: Boolean, var subscription: Closeable? = null)
    private inner class Owner : Closeable {
        private val lock = Any()
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "runtime-agent").apply { isDaemon = true } }
        private val tasks = linkedMapOf<String, Task>()
        private val handles = linkedMapOf<String, Handle>()
        private val events = ArrayDeque<Packet>()
        private var queuedBytes = 0
        private var ended = false
        private inner class Task(val id: String) {
            var done = false
            var abandoned = false
            var cancelDetached = false
            var handle: String? = null
            fun finish(type: String, value: Any?) = synchronized(lock) {
                if (!done && !abandoned && !ended) { done = true; enqueue(id, type, value) }
            }
        }
        fun start(method: String, args: JsonArray): Map<String, String> {
            require(method in operations)
            val task = Task(UUID.randomUUID().toString())
            synchronized(lock) { check(!ended); tasks[task.id] = task }
            try { executor.execute {
                if (synchronized(lock) { ended || task.abandoned }) return@execute
                try { task.finish("resolve", invoke(task, method, args)) }
                catch (error: Throwable) { task.finish("reject", errorValue(error)) }
            } } catch (error: Throwable) { task.finish("reject", errorValue(error)) }
            return mapOf("id" to task.id)
        }
        private fun invoke(task: Task, method: String, args: JsonArray): Any? {
            if (access?.allowed() == false) throw ThreeStoveAgentControlException("PERMISSION_DENIED")
            return when (method) {
                "run" -> {
                    require(args.size() == 3)
                    val goal = AiAgentArguments.goal(string(args[0]))
                    val options = AiAgentArguments.merge(args[1].asJsonObject, args[2].asJsonObject)
                    synchronized(lock) { check(handles.size < 256) { "Too many AgentRun handles in one script" } }
                    val record = registry.start(goal, options)
                    try { access?.created(record.id) } catch (error: Throwable) {
                        runCatching { registry.cancel(record, "application-access-revoked") }; throw error
                    }
                    register(task, record, true)
                }
                "get" -> {
                    require(args.size() == 1); val id = AiAgentArguments.runId(string(args[0]))
                    if (access?.owns(id) == false) null else registry.get(id)?.let { register(task, it, false) }
                }
                "list" -> {
                    require(args.size() == 1); val filter = AiAgentArguments.filter(args[0].asJsonObject)
                    val result = JsonArray(); val limit = filter["limit"]?.asInt ?: 50
                    var offset = 0
                    while (result.size() < limit && offset <= 1000 && !synchronized(lock) { ended || task.abandoned }) {
                        val page = registry.list(50, offset); val rows = page.getAsJsonArray("runs")
                        for (row in rows) if (result.size() < limit && access?.owns(row.asJsonObject["runId"].asString) != false &&
                            AiAgentArguments.matches(row.asJsonObject, filter)) result.add(row.deepCopy().apply { asJsonObject.add("id", asJsonObject.remove("runId")) })
                        if (rows.size() < 50 || offset + rows.size() >= page["total"].asInt) break
                        offset += rows.size()
                    }
                    result
                }
                "presets" -> { require(args.size() == 0); JsonArray().apply { registry.presets().getAsJsonArray("presets").forEach { add(it.asJsonObject["id"]) } } }
                "status" -> { require(args.size() == 0); if (access == null) registry.status() else registry.status(access::owns) }
                "catalog" -> {
                    require(args.size() == 1)
                    val query = if (args[0].isJsonNull) null else AiAgentArguments.text(string(args[0]), "catalog query", 4096, blank = true)
                    val catalog = AgentScriptCatalog(File(workingDirectory), listOf(android.os.Environment.getExternalStorageDirectory()), registry.roots().map(::File))
                    JsonArray().apply { catalog.listScripts(query = query).forEach { add(it.toJsonObject()) } }
                }
                else -> control(method, args)
            }
        }
        private fun register(task: Task, record: ThreeStoveAgentRunRecord, owned: Boolean): JsonObject {
            val id = UUID.randomUUID().toString()
            val handle = Handle(record, owned)
            val accepted = try { synchronized(lock) {
                if (ended || task.abandoned) false else {
                    check(handles.size < 256) { "Too many AgentRun handles in one script" }
                    handles[id] = handle; task.handle = id; true
                }
            } } catch (error: Throwable) {
                if (owned && !record.detached) runCatching { registry.cancel(record, "script-handle-limit") }
                throw error
            }
            if (!accepted) {
                if (owned && (!record.detached || task.cancelDetached)) registry.cancel(record, "script-stopped")
                throw ThreeStoveAgentControlException("SESSION_CLOSED")
            }
            val subscription = record.observe { event -> synchronized(lock) {
                if (handles[id] === handle && !ended) enqueue(id, "event", event)
            } }
            val retained = synchronized(lock) { (handles[id] === handle).also { if (it) handle.subscription = subscription } }
            if (!retained) subscription.close()
            return JsonObject().apply {
                addProperty("handle", id); addProperty("id", record.id); addProperty("goal", record.goal)
                addProperty("state", record.state); addProperty("startedAt", record.startedAt); addProperty("detached", record.detached)
                addProperty("interaction", record.interaction)
            }
        }
        private fun control(method: String, args: JsonArray): Any {
            val id = string(args[0])
            val handle = synchronized(lock) { handles[id] } ?: throw ThreeStoveAgentControlException("SESSION_CLOSED")
            val record = handle.record
            return when (method) {
                "run.cancel" -> {
                    require(args.size() == 2); registry.cancel(record, AiAgentArguments.text(string(args[1]), "reason", 256)); true
                }
                "run.respond" -> {
                    require(args.size() == 3)
                    val value = args[2]
                    require(value.isJsonPrimitive && (value.asJsonPrimitive.isString || value.asJsonPrimitive.isBoolean) ||
                        value.isJsonArray && value.asJsonArray.size() in 1..8 && value.asJsonArray.all {
                            it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.trim().isNotEmpty() &&
                                it.asString.trim().codePointCount(0, it.asString.trim().length) <= 200 }) { "Invalid Agent answer" }
                    require(value.toString().toByteArray(Charsets.UTF_8).size <= 4096)
                    registry.respond(record, JsonObject().apply {
                        addProperty("requestId", AiAgentArguments.text(string(args[1]), "requestId", 128)); add("value", value)
                    })
                }
                "run.confirm" -> {
                    require(args.size() == 4 && args[2].isJsonPrimitive && args[2].asJsonPrimitive.isBoolean)
                    val scope = string(args[3]); require(scope in setOf("once", "run"))
                    registry.respond(record, JsonObject().apply {
                        addProperty("requestId", AiAgentArguments.text(string(args[1]), "requestId", 128))
                        add("allowed", args[2]); addProperty("scope", scope)
                    })
                }
                "run.release" -> { require(args.size() == 1); release(id, false); true }
                else -> throw IllegalArgumentException("Unknown Agent control")
            }
        }
        private fun enqueue(id: String, type: String, value: Any?) {
            val json = JsonObject().apply { addProperty("type", type); add("value", jsonValue(value)) }.toString().toByteArray(Charsets.UTF_8)
            if (queuedBytes + json.size > 8 * 1024 * 1024) {
                discard(id)
                val failure = JsonObject().apply { addProperty("type", "reject"); add("value", jsonValue(errorValue(ThreeStoveAgentControlException("LIMIT_EXCEEDED")))) }.toString().toByteArray()
                events.add(Packet(id, failure)); queuedBytes += failure.size
                val task = tasks[id]
                if (task != null) {
                    task.abandoned = true; task.cancelDetached = true
                    val handle = task.handle
                    executor.execute { handle?.let { release(it, true, forceDetached = true) } }
                } else executor.execute { release(id, true, discardEvents = false) }
            } else { events.add(Packet(id, json)); queuedBytes += json.size }
        }
        fun poll(ids: JSONArray): List<Map<String, Any>> = synchronized(lock) {
            check(!ended)
            val wanted = (0 until ids.length()).map(ids::getString).toSet()
            val result = arrayListOf<Map<String, Any>>(); var remaining = 48 * 1024
            val iterator = events.iterator()
            while (iterator.hasNext() && remaining > 0 && result.size < 32) {
                val event = iterator.next(); if (event.id !in wanted) continue
                val count = minOf(12 * 1024, remaining, event.bytes.size - event.offset)
                val data = Base64.encodeToString(event.bytes, event.offset, count, Base64.NO_WRAP)
                event.offset += count; remaining -= count; queuedBytes -= count
                val last = event.offset == event.bytes.size
                result.add(mapOf("id" to event.id, "data" to data, "last" to last)); if (last) iterator.remove()
            }
            result
        }
        private fun discard(id: String) {
            val iterator = events.iterator()
            while (iterator.hasNext()) { val packet = iterator.next(); if (packet.id == id) {
                queuedBytes -= packet.bytes.size - packet.offset; iterator.remove()
            } }
        }
        fun ack(id: String): Boolean = synchronized(lock) {
            if (tasks[id]?.done != true) false else { tasks.remove(id); true }
        }
        fun cancel(id: String): Boolean {
            val task = synchronized(lock) { tasks.remove(id)?.also { it.abandoned = true; it.cancelDetached = true; discard(id) } } ?: return false
            task.handle?.let { release(it, true, forceDetached = true) }; return true
        }
        fun release(id: String, cancel: Boolean, forceDetached: Boolean = false, discardEvents: Boolean = true) {
            val handle = synchronized(lock) { handles.remove(id).also { if (discardEvents) discard(id) } } ?: return
            handle.subscription?.close()
            if (cancel && handle.owned && (forceDetached || !handle.record.detached)) runCatching { registry.cancel(handle.record, "script-stopped") }
        }
        override fun close() {
            val ids = synchronized(lock) {
                if (ended) return
                ended = true; tasks.values.forEach { it.abandoned = true }; tasks.clear(); events.clear(); queuedBytes = 0
                handles.keys.toList()
            }
            ids.forEach { release(it, true) }; executor.shutdown()
        }
    }

    companion object {
        const val HOST = "org.autojs.autojs6"
        val methods = setOf("open", "start", "poll", "ack", "cancel", "release", "close", "context", "result", "hasAccess", "requestAccess")
        val operations = setOf("run", "get", "list", "catalog", "presets", "status", "run.respond", "run.confirm", "run.cancel", "run.release")
        private fun string(value: JsonElement): String { require(value.isJsonPrimitive && value.asJsonPrimitive.isString); return value.asString }
        private fun jsonValue(value: Any?): JsonElement = when (value) {
            null -> JsonNull.INSTANCE
            is JsonElement -> value
            else -> JsonParser.parseString(NodeBridgeJson.valueToJson(JSONObject.wrap(value)))
        }
        private fun success(request: NodeBridgeRequest, value: Any?, reply: (NodeBridgeResponse) -> Unit) =
            reply(NodeBridgeResponse.success(request.id, jsonValue(value).toString()))
        private fun errorValue(error: Throwable): Map<String, String> = buildMap {
            put("name", "AgentError")
            put("code", when (error) {
                is ThreeStoveAgentControlException -> error.code
                is SecurityException -> "PERMISSION_DENIED"
                else -> error.message?.takeIf { it in org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.ERROR_CODES } ?: "INVALID_REQUEST"
            })
            put("message", error.message ?: "Agent request failed")
            (error as? ThreeStoveAgentControlException)?.hint?.let { put("hint", it) }
        }
        private fun failed(request: NodeBridgeRequest, error: Throwable): NodeBridgeResponse {
            val value = errorValue(error)
            return NodeBridgeResponse.failure(request.id, NodeBridgeError(code = "ERR_AUTOJS6_AGENT_${value["code"]}", message = value.getValue("message"), module = "aiAgent", method = request.method))
        }
    }
}

/** A delegated application's consent and persistent run ownership are enforced by the host. */
internal interface RuntimeAgentAccess {
    fun allowed(): Boolean
    fun owns(id: String): Boolean
    fun created(id: String)
}
