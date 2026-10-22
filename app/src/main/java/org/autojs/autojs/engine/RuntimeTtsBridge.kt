package org.autojs.autojs.engine

import android.content.Context
import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.autojs.core.tts.TtsTextChunker
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.tts.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** The same TTS actor as Rhino, with execution-local clients and JSON event delivery. */
internal class RuntimeTtsBridge(private val context: Context, private val cwd: String) : Closeable {
    private data class Session(val client: Client, val service: TtsService,
        val pending: ConcurrentHashMap<String, () -> Unit> = ConcurrentHashMap())
    private val sessions = LinkedHashMap<String, Session>()
    private var closed = false
    private val polling = Executors.newCachedThreadPool { action -> Thread(action, "Runtime-TTS-events").apply { isDaemon = true } }

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        val replied = AtomicBoolean(false)
        val cleanup = AtomicReference<() -> Unit>({})
        fun deliver(response: NodeBridgeResponse) { if (replied.compareAndSet(false, true)) { cleanup.get().invoke(); reply(response) } }
        fun success(value: Any?) = deliver(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(JSONObject.wrap(value))))
        fun failure(error: Throwable) = deliver(NodeBridgeResponse.failure(request.id,
            if (error is IllegalArgumentException || error is org.json.JSONException) NodeBridgeError.invalidRequest(error.message ?: "Invalid TTS request", "tts", request.method)
            else NodeBridgeError(message = error.message ?: "TTS failed", code = "ERR_AUTOJS6_TTS_${(error as? TtsException)?.code ?: "FAILED"}", module = "tts", method = request.method)))
        try {
            val args = JSONArray(request.argsJson)
            if (request.method == "open") {
                require(args.length() == 0)
                val id = "tts-client-${UUID.randomUUID()}"
                synchronized(sessions) {
                    check(!closed) { "TTS execution has ended" }
                    val client = Client()
                    sessions[id] = Session(client, TtsService(context, client))
                }
                success(JSONObject().put("id", id)); return
            }
            val id = args.getString(0)
            if (request.method == "close") { success(closeSession(id)); return }
            val session = synchronized(sessions) { sessions[id] } ?: throw IllegalArgumentException("TTS client is closed or belongs to another execution")
            when (request.method) {
                "drain" -> {
                    val selected = args.optJSONArray(1)?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
                    polling.execute { try { success(session.client.drain(750, selected)) } catch (error: Throwable) { failure(error) } }
                }
                "stopTask" -> success(session.client.stopTask(args.getString(1)))
                "invoke" -> {
                    require(args.length() == 3)
                    val method = args.getString(1); val input = args.getJSONArray(2); val service = session.service
                    fun options(index: Int): JsonObject = if (input.isNull(index)) JsonObject() else JsonParser.parseString(input.getJSONObject(index).toString()).asJsonObject
                    fun engine(index: Int = 0, default: Boolean = true): String? = if (input.length() <= index && default) service.defaults()["engine"] as? String
                        else if (input.isNull(index)) null else normalizeTtsEngineName(input.getString(index))
                    fun text(): String = (input.opt(0) as? String ?: throw IllegalArgumentException("TTS text must be a string")).also {
                        require(it.isNotBlank() && it.length <= TtsTextChunker.MAX_TEXT_LENGTH) { "TTS text is blank or too long" }
                    }
                    val value: Any? = when (method) {
                        "utterance", "enqueueUtterance" -> service.speechTask(text(), options(1), if (method == "enqueueUtterance") TtsQueueMode.ADD else null)
                        "synthesis" -> {
                            require(input.get(1) is String && input.getString(1).isNotBlank()) { "TTS output path must be a nonempty string" }
                            val target = File(input.getString(1)); val path = if (target.isAbsolute) target.path else File(cwd.ifBlank { context.filesDir.path }, target.path).absolutePath
                            service.synthesis(text(), path, options(2), observable = true)
                        }
                        "silenceTask" -> {
                            val duration = input.get(0) as? Number ?: throw IllegalArgumentException("TTS silence requires an integer duration")
                            require(duration.toDouble().isFinite() && duration.toDouble() == duration.toLong().toDouble())
                            service.silence(duration.toLong(), options(1), observable = true)
                        }
                        "ready" -> service.ready(engine())
                        "engines" -> service.engines(engine(default = false))
                        "languages" -> service.languages(engine())
                        "voices" -> {
                            val filter = if (input.isNull(0)) JSONObject() else input.getJSONObject(0)
                            require(filter.keys().asSequence().all { it in setOf("engine", "locale", "offlineOnly", "installedOnly") })
                            for (key in listOf("offlineOnly", "installedOnly")) require(filter.isNull(key) || filter.get(key) is Boolean) { "TTS $key must be a boolean" }
                            service.voices(if (filter.has("engine")) if (filter.isNull("engine")) null else normalizeTtsEngineName(filter.getString("engine")) else service.defaults()["engine"] as? String,
                                if (filter.isNull("locale")) null else normalizeTtsLanguageTag(filter.getString("locale").replace('_', '-')),
                                if (filter.isNull("offlineOnly")) false else filter.getBoolean("offlineOnly"), if (filter.isNull("installedOnly")) false else filter.getBoolean("installedOnly"))
                        }
                        "isLanguageAvailable" -> service.isLanguageAvailable(normalizeTtsLanguageTag(input.getString(0).replace('_', '-')), engine(1))
                        "languageAvailability" -> service.languageAvailability(normalizeTtsLanguageTag(input.getString(0).replace('_', '-')), engine(1))
                        "stop" -> service.stopAll()
                        "reset" -> service.reset()
                        "isSpeaking" -> service.isSpeaking()
                        "isBusy" -> service.isBusy()
                        "status" -> service.status()
                        "defaults" -> service.defaults()
                        "configure" -> service.configure(options(0))
                        "openSettings" -> service.openSettings()
                        "installData" -> service.installData(engine())
                        else -> throw IllegalArgumentException("Unknown TTS operation: $method")
                    }
                    when (value) {
                        is TtsTask -> success(JSONObject().put("id", value.id).put("operation", value.operation).put("text", value.text ?: JSONObject.NULL).put("state", value.state))
                        is ScriptPromiseAdapter -> {
                            cleanup.set { session.pending.remove(request.id) }
                            synchronized(sessions) {
                                if (sessions[id] !== session) throw TtsException("CLOSED", "TTS client has closed")
                                session.pending[request.id] = { failure(TtsException("CLOSED", "TTS client has closed")) }
                            }
                            synchronized(value) {
                            value.onResolve(object : ScriptPromiseAdapter.Callback { override fun call(arg: Any?) { success(arg) } })
                            value.onReject(object : ScriptPromiseAdapter.Callback { override fun call(arg: Any?) { failure(arg as? Throwable ?: IllegalStateException(arg.toString())) } })
                            }
                        }
                        else -> success(value)
                    }
                }
                else -> throw IllegalArgumentException("Unknown TTS bridge method")
            }
        } catch (error: Throwable) { failure(error) }
    }

    fun closeSession(id: String): Boolean {
        val session = synchronized(sessions) { sessions.remove(id) } ?: return false
        session.pending.values.concurrentSnapshot().forEach { it() }
        session.service.close(); session.client.close(); return true
    }
    override fun close() {
        val ids = synchronized(sessions) { closed = true; sessions.keys.toList() }
        ids.forEach(::closeSession)
        polling.shutdownNow()
    }

    internal class Client : TtsClient, Closeable {
        private val lock = java.lang.Object()
        private val tasks = LinkedHashMap<String, Task>()
        private val events = ArrayDeque<JSONObject>()
        private var bytes = 0
        private var closed = false
        override fun createTask(id: String, operation: String, text: String?, requestStop: (String) -> Unit, onTerminal: () -> Unit): TtsTaskController = synchronized(lock) {
            check(!closed); Task(id, operation, text, requestStop, onTerminal).also { tasks[id] = it }
        }
        fun task(id: String): TtsTask = synchronized(lock) { tasks[id] } ?: throw IllegalArgumentException("TTS task has finished or is unknown")
        fun stopTask(id: String): Boolean {
            val task = synchronized(lock) { tasks[id] } ?: return false
            task.stop(); return true
        }
        fun drain(waitMillis: Long = 0, selected: Set<String>? = null): JSONArray = synchronized(lock) {
            if (events.none { selected == null || it.getString("id") in selected } && !closed && waitMillis > 0) lock.wait(waitMillis)
            val result = JSONArray(); var length = 0
            val iterator = events.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (selected != null && next.getString("id") !in selected) continue
                val size = next.toString().toByteArray(Charsets.UTF_8).size
                if (result.length() > 0 && length + size > 192 * 1024) break
                iterator.remove(); bytes -= size; length += size; result.put(next)
                if (next.optBoolean("terminal")) tasks.remove(next.getString("id"))
            }
            result
        }
        override fun close() = synchronized(lock) { closed = true; tasks.clear(); events.clear(); bytes = 0; lock.notifyAll() }
        override fun createResultDispatcher() = object : TtsResultDispatcher {
            private val terminal = AtomicBoolean(false)
            override val isTerminal get() = terminal.get()
            override fun resolve(adapter: ScriptPromiseAdapter, value: Any?, terminal: () -> Unit) {
                if (this.terminal.compareAndSet(false, true)) try { synchronized(adapter) { adapter.resolve(value) } } finally { terminal() }
            }
            override fun reject(adapter: ScriptPromiseAdapter, error: Throwable, terminal: () -> Unit) {
                if (this.terminal.compareAndSet(false, true)) try { synchronized(adapter) { adapter.reject(error) } } finally { terminal() }
            }
            override fun cancelSilently(terminal: () -> Unit) { if (this.terminal.compareAndSet(false, true)) terminal() }
        }
        private inner class Task(override val id: String, override val operation: String, override val text: String?,
            private val requestStop: (String) -> Unit, private val onTerminal: () -> Unit) : TtsTask, TtsTaskController {
            override val task: TtsTask get() = this
            override val result: Any get() = id
            @Volatile override var state = "created"; private set
            private val done = AtomicBoolean(false)
            override val isDone get() = done.get()
            override fun stop(): TtsTask { if (!isDone) requestStop(id); return this }
            private fun emit(type: String, value: Any?, terminal: Boolean = false): Boolean = synchronized(lock) {
                if (closed || (!terminal && isDone)) return false
                val mapped = if (type == "audio") (value as Map<*, *>).entries.associate { it.key.toString() to if (it.value is ByteArray) Base64.encodeToString(it.value as ByteArray, Base64.NO_WRAP) else it.value } else value
                val event = JSONObject().put("id", id).put("event", type).put("state", state).put("value", if (mapped == null) JSONObject.NULL else org.json.JSONTokener(NodeBridgeJson.valueToJson(JSONObject.wrap(mapped))).nextValue()).put("terminal", terminal)
                val size = event.toString().toByteArray(Charsets.UTF_8).size
                if (!terminal && (size > 192 * 1024 || bytes + size > 1024 * 1024)) throw TtsException("AUDIO_BACKPRESSURE", "TTS client is not draining progress events")
                events.addLast(event); bytes += size; lock.notifyAll(); true
            }
            private fun finish(type: String, next: String, value: Any?): Boolean {
                if (!done.compareAndSet(false, true)) return false
                state = next
                return try { emit(type, value, true) } finally { onTerminal() }
            }
            private fun stateEvent(type: String, next: String, info: Any?): Boolean = synchronized(lock) {
                if (isDone) return false
                state = next; emit(type, info)
            }
            override fun queued(info: Any?) = stateEvent("queued", "queued", info)
            override fun start(info: Any?) = stateEvent("start", "running", info)
            override fun chunk(info: Any?) = emit("chunk", info)
            override fun range(info: Any?) = emit("range", info)
            override fun synthesis(info: Any?) = emit("synthesis", info)
            override fun audio(info: Any?) = emit("audio", info)
            override fun complete(value: Any?) = finish("done", "done", value)
            private fun errorValue(error: Throwable) = mapOf("code" to ((error as? TtsException)?.code ?: "FAILED"), "message" to error.message, "errorCode" to (error as? TtsException)?.errorCode)
            override fun fail(error: Throwable) = finish("error", "error", errorValue(error))
            override fun stop(info: Any?, error: Throwable, cancelled: Boolean) = finish(if (cancelled) "cancelled" else "stop", if (cancelled) "cancelled" else "stopped", errorValue(error) + ("info" to info))
            override fun cancelSilently(): Boolean { if (!done.compareAndSet(false, true)) return false; state = "cancelled"; onTerminal(); return true }
        }
    }

    companion object {
        val methods = setOf("open", "invoke", "drain", "stopTask", "close")
    }
}
