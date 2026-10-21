package org.autojs.autojs.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.*
import org.autojs.autojs.runtime.api.ai.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID

/** Execution-owned asynchronous access to the same provider runners used by Rhino. */
internal class RuntimeAiBridge(private val context: Context) : Closeable {
    private val owners = LinkedHashMap<String, Owner>()
    private var ended = false

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        try {
            val args = JSONArray(request.argsJson)
            val result: Any? = if (request.method == "open") synchronized(owners) {
                require(args.length() == 0)
                check(!ended) { "AI execution has ended" }
                val id = UUID.randomUUID().toString()
                owners[id] = Owner()
                mapOf("id" to id)
            } else {
                val id = args.getString(0)
                if (request.method == "close") closeClient(id) else {
                    val owner = synchronized(owners) { owners[id] } ?: throw failure("SESSION_CLOSED")
                    when (request.method) {
                        "start" -> owner.start(args.getString(1), JsonParser.parseString(args.getJSONArray(2).toString()).asJsonArray)
                        "poll" -> owner.poll(args.getJSONArray(1))
                        "ack" -> owner.ack(args.getString(1))
                        "cancel" -> owner.cancel(args.getString(1))
                        else -> throw IllegalArgumentException("Unknown AI bridge method")
                    }
                }
            }
            reply(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(JSONObject.wrap(result))))
        } catch (error: Throwable) {
            val value = errorValue(error)
            reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(
                code = "ERR_AUTOJS6_AI_${value["code"]}", message = value["message"].toString(), module = "ai", method = request.method)))
        }
    }

    fun closeClient(id: String): Boolean {
        val owner = synchronized(owners) { owners.remove(id) } ?: return false
        owner.close()
        return true
    }

    override fun close() {
        val snapshot = synchronized(owners) { ended = true; owners.values.toList().also { owners.clear() } }
        snapshot.forEach(Owner::close)
    }

    private data class Event(val id: String, val bytes: ByteArray, var offset: Int = 0)
    private data class Session(val core: AiPluginSession, val request: AiPluginAskRequest)

    private inner class Owner : Closeable {
        private val lock = Any()
        private val tasks = LinkedHashMap<String, Task>()
        private val sessions = LinkedHashMap<String, Session>()
        private val events = ArrayDeque<Event>()
        private var queuedBytes = 0
        private var closed = false

        fun start(method: String, args: JsonArray): Map<String, String> {
            require(method in operations) { "Unknown AI operation" }
            val task = Task(UUID.randomUUID().toString())
            synchronized(lock) { ensureOpen(); tasks[task.id] = task }
            try { invoke(task, method, args) } catch (error: Throwable) { task.finish("reject", errorValue(error)) }
            return mapOf("id" to task.id)
        }

        private fun invoke(task: Task, method: String, args: JsonArray) {
            if (method == "hasAccess") { require(args.size() == 0); task.finish("resolve", hasAccess()); return }
            if (method == "requestAccess") {
                require(args.size() == 0)
                if (hasAccess()) { task.finish("resolve", true); return }
                Handler(Looper.getMainLooper()).post {
                    if (!task.isDone()) try {
                        val action = RuntimeWebActionActivity.mediaPermissions(context, "3-Stone AI",
                            arrayOf(ThreeStoneAiOfficialPlugin.RUNTIME_PERMISSION)) { task.finish("resolve", it) }
                        task.attach(object : AiPluginAskHandle {
                            override fun cancel(): Boolean { Handler(Looper.getMainLooper()).post { action.close() }; return true }
                            override fun close() { Handler(Looper.getMainLooper()).post { action.close() } }
                        })
                    } catch (error: Throwable) { task.finish("reject", errorValue(error)) }
                }
                return
            }
            if (method == "session.close") {
                require(args.size() == 1)
                val key = args[0].asString
                val session = synchronized(lock) { sessions.remove(key) }
                val turns = synchronized(lock) { tasks.values.filter { it.sessionId == key } }
                turns.forEach { it.event("sessionClosed", true); it.finish("reject", errorValue(failure("SESSION_CLOSED"))) }
                session?.core?.close()
                task.finish("resolve", session != null)
                return
            }
            if (method.startsWith("session.")) {
                require(args.size() == 2 && args[1].isJsonPrimitive && args[1].asJsonPrimitive.isString) {
                    "AI session turn expects a prompt string"
                }
                val session = synchronized(lock) { sessions[args[0].asString] } ?: throw failure("SESSION_CLOSED")
                if (session.core.isClosed) throw failure("SESSION_CLOSED")
                requireAccess()
                task.sessionId = args[0].asString
                val streaming = method == "session.stream"
                val handle = session.core.startTurn(AiPluginMessage(AiPluginMessageRole.USER, args[1].asString),
                    streaming, if (streaming) null else { outcome -> task.outcome(method, session.request, outcome) },
                    if (streaming) { event -> task.stream(session.request, event) } else null)
                task.attach(handle)
                return
            }
            requireAccess()
            val input = portableInput(when (method) {
                "session" -> RuntimeAiArguments.session(args)
                "catalog" -> RuntimeAiArguments.catalog(args)
                else -> RuntimeAiArguments.request(args)
            })
            if (method == "catalog") {
                val control = AiPluginAskInputMapper.requirePluginControl(input)
                require(control.targetId == null) { "AI catalog does not accept a target" }
                val selection = control.pluginSelection
                task.attach(AndroidAiPluginAskRunner.createCatalog(context).listCatalog(AiPluginCatalogRequest(
                    AiProviderComponent(selection.packageName, selection.className), selection.providerId,
                    control.timeoutMillis ?: DEFAULT_TIMEOUT)) { result ->
                    when (result) {
                        is AiPluginCatalogOutcome.Completed -> task.finish("resolve", result.catalog.toPublicValue())
                        is AiPluginCatalogOutcome.Failed -> task.finish("reject", errorValue(failure(result.failure.name)))
                    }
                })
                return
            }
            val request = AiPluginAskInputMapper.map(input, DEFAULT_TIMEOUT)
            task.attach(when (method) {
                "session" -> AndroidAiPluginAskRunner.createSession(context).openSession(request) { result ->
                    when (result) {
                        is AiPluginSessionOpenOutcome.Failed -> task.finish("reject", errorValue(failure(result.failure.name)))
                        is AiPluginSessionOpenOutcome.Completed -> {
                            val core = result.session
                            val accepted = synchronized(lock) {
                                if (closed || task.isDone()) false else {
                                    sessions[task.id] = Session(core, request)
                                    task.finish("resolve", mapOf("id" to task.id, "provider" to core.target.providerId,
                                        "model" to core.modelId, "target" to core.targetId, "profile" to core.target.profileId,
                                        "backend" to core.backendProfile)); true
                                }
                            }
                            if (!accepted) core.close()
                        }
                    }
                }
                "stream" -> AndroidAiPluginAskRunner.createStream(context).startStream(request) { task.stream(request, it) }
                else -> AndroidAiPluginAskRunner.create(context).start(request) { task.outcome(method, request, it) }
            })
        }

        private fun ensureOpen() { if (closed) throw failure("SESSION_CLOSED") }

        private inner class Task(val id: String) {
            var sessionId: String? = null
            private var done = false
            private var cancelled = false
            private var handle: AiPluginAskHandle? = null
            fun isDone() = synchronized(lock) { done || closed }
            fun attach(value: AiPluginAskHandle) {
                val clean = synchronized(lock) { handle = value; done || closed }
                if (clean) cleanHandle()
            }
            fun event(type: String, value: Any?) {
                synchronized(lock) { if (!done && !closed) enqueue(id, type, value) }
            }
            fun finish(type: String, value: Any?) {
                synchronized(lock) {
                    if (done || closed) return
                    enqueue(id, type, value)
                    done = true
                    // Retain ownership until JS accepts the result. An abort may win while a
                    // terminal packet is in transit, and must still close a persistent session.
                }
                cleanHandle()
            }
            fun cancel() {
                val session = synchronized(lock) {
                    cancelled = true; done = true; tasks.remove(id)
                    sessionId?.let(sessions::remove)
                }
                cleanHandle()
                session?.core?.close()
            }
            private fun cleanHandle() {
                val value = synchronized(lock) { handle.also { handle = null } } ?: return
                if (cancelled || closed) runCatching(value::cancel)
                runCatching(value::close)
            }
            private fun sessionState() {
                val removed = synchronized(lock) {
                    sessionId?.let { key -> sessions[key]?.takeIf { it.core.isClosed }?.also { sessions.remove(key) } }
                }
                if (removed != null) event("sessionClosed", true)
            }
            fun outcome(method: String, request: AiPluginAskRequest, result: AiPluginAskOutcome) {
                sessionState()
                when (result) {
                    is AiPluginAskOutcome.Completed -> finish("resolve", if (method.endsWith("ask")) result.text else
                        completion(request, result.text, result.reasoning, result.usage, result.finishReason, result.target))
                    is AiPluginAskOutcome.Failed -> finish("reject", errorValue(failure(result.failure.name)))
                }
            }
            fun stream(request: AiPluginAskRequest, value: AiPluginStreamEvent) {
                sessionState()
                when (value) {
                    is AiPluginStreamEvent.Opened -> event("open", mapOf("route" to "plugin", "provider" to value.target.providerId,
                        "model" to value.target.modelId, "target" to value.target.toPublicValue(),
                        "profile" to value.target.toProfilePublicValue(), "plugin" to request.toPluginIdentityPublicValue()))
                    is AiPluginStreamEvent.Chunk -> event("chunk", mapOf("text" to value.text, "reasoning" to value.reasoning,
                        "toolCalls" to emptyList<Any>(), "usage" to null, "finishReason" to null, "done" to false, "raw" to null))
                    is AiPluginStreamEvent.Usage -> event("usage", value.usage.toPluginPublicValue())
                    is AiPluginStreamEvent.Completed -> finish("resolve", completion(request, value.text, value.reasoning,
                        value.usage, value.finishReason, value.target))
                    is AiPluginStreamEvent.Failed -> finish("reject", errorValue(failure(value.failure.name)))
                    is AiPluginStreamEvent.ToolCalls -> { finish("reject", errorValue(failure("UNSUPPORTED_CAPABILITY"))); cancel() }
                }
            }
        }

        private fun enqueue(id: String, type: String, value: Any?) {
            val bytes = NodeBridgeJson.valueToJson(JSONObject.wrap(mapOf("type" to type, "value" to value))).toByteArray(Charsets.UTF_8)
            if (queuedBytes + bytes.size > MAX_QUEUE_BYTES) {
                discard(id)
                val task = tasks[id]
                task?.cancel()
                sessions.remove(id)?.core?.close()
                val error = NodeBridgeJson.valueToJson(JSONObject.wrap(mapOf("type" to "reject", "value" to
                    errorValue(failure("BACKPRESSURE"))))).toByteArray(Charsets.UTF_8)
                events.add(Event(id, error)); queuedBytes += error.size
                return
            }
            events.add(Event(id, bytes)); queuedBytes += bytes.size
        }

        fun poll(ids: JSONArray): List<Map<String, Any>> = synchronized(lock) {
            ensureOpen()
            val wanted = (0 until ids.length()).map { ids.getString(it) }.toSet()
            val output = ArrayList<Map<String, Any>>()
            var remaining = 48 * 1024
            val iterator = events.iterator()
            while (iterator.hasNext() && remaining > 0 && output.size < 32) {
                val event = iterator.next()
                if (event.id !in wanted) continue
                val count = minOf(12 * 1024, event.bytes.size - event.offset, remaining)
                val data = Base64.encodeToString(event.bytes, event.offset, count, Base64.NO_WRAP)
                event.offset += count; remaining -= count; queuedBytes -= count
                val last = event.offset == event.bytes.size
                output.add(mapOf("id" to event.id, "data" to data, "last" to last))
                if (last) iterator.remove()
            }
            output
        }

        private fun discard(id: String) {
            val iterator = events.iterator()
            while (iterator.hasNext()) {
                val event = iterator.next()
                if (event.id == id) { queuedBytes -= event.bytes.size - event.offset; iterator.remove() }
            }
        }

        fun cancel(id: String): Boolean {
            val pair = synchronized(lock) { discard(id); tasks[id] to sessions.remove(id) }
            pair.first?.cancel(); pair.second?.core?.close()
            return pair.first != null || pair.second != null
        }

        fun ack(id: String): Boolean = synchronized(lock) {
            val task = tasks[id] ?: return@synchronized false
            if (!task.isDone()) return@synchronized false
            tasks.remove(id)
            true
        }

        override fun close() {
            val pair = synchronized(lock) {
                if (closed) return
                closed = true
                (tasks.values.toList() to sessions.values.toList()).also {
                    tasks.clear(); sessions.clear(); events.clear(); queuedBytes = 0
                }
            }
            pair.first.forEach(Task::cancel)
            pair.second.forEach { runCatching(it.core::close) }
        }
    }

    private fun hasAccess() = context.packageName == HOST_PACKAGE ||
        context.checkSelfPermission(ThreeStoneAiOfficialPlugin.RUNTIME_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun requireAccess() { if (!hasAccess()) throw failure("PERMISSION_DENIED", "Call ai.requestAccess() to authorize this application's AI access") }

    private fun portableInput(input: AiRequestInput): AiRequestInput {
        if (context.packageName == HOST_PACKAGE) return input
        val options = input.options.deepCopy()
        val original = options.get("plugin")
        if (original == null || original.isJsonPrimitive && original.asJsonPrimitive.isBoolean && original.asBoolean ||
            original.isJsonObject && !original.asJsonObject.has("component")) {
            options.add("plugin", (original?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()).apply {
                if (!has("providerId") || get("providerId").isJsonNull) addProperty("providerId", ThreeStoneAiOfficialPlugin.PROVIDER_ID)
                add("component", JsonObject().apply {
                    addProperty("packageName", ThreeStoneAiOfficialPlugin.PACKAGE_NAME)
                    addProperty("className", ThreeStoneAiOfficialPlugin.RUNTIME_CLASS_NAME)
                })
            })
        }
        return AiRequestInput(input.messages, options)
    }

    companion object {
        val methods = setOf("open", "start", "poll", "ack", "cancel", "close")
        val operations = setOf("ask", "chat", "stream", "session", "catalog", "session.ask", "session.chat",
            "session.stream", "session.close", "hasAccess", "requestAccess")
        private const val HOST_PACKAGE = "org.autojs.autojs6"
        private const val DEFAULT_TIMEOUT = 120_000L
        private const val MAX_QUEUE_BYTES = 8 * 1024 * 1024
        private fun failure(code: String, message: String = "AI plugin request failed ($code)") = AiPluginRouteException(code, message)
        private fun errorValue(error: Throwable): Map<String, String> = mapOf("name" to "AiError", "route" to "plugin",
            "code" to if (error is AiPluginRouteException) error.code else if (error is IllegalArgumentException) "INVALID_REQUEST" else "INTERNAL_FAILURE",
            "message" to (error.message ?: "AI request failed"))
        private fun completion(request: AiPluginAskRequest, text: String, reasoning: String, usage: AiPluginUsage,
            finishReason: Int, target: AiPluginTargetSummary): Map<String, Any?> = linkedMapOf(
            "text" to text, "reasoning" to reasoning, "toolCalls" to emptyList<Any>(), "usage" to usage.toPluginPublicValue(),
            "finishReason" to finishReason.toPublicFinishReason(), "message" to null, "error" to null, "raw" to null,
            "profile" to target.toProfilePublicValue(), "target" to target.toPublicValue(), "plugin" to request.toPluginIdentityPublicValue(),
            "route" to "plugin", "provider" to target.providerId, "model" to target.modelId)
    }
}
