package org.autojs.autojs.engine

import android.content.Context
import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.autojs.autojs.core.plugin.speech.*
import org.autojs.autojs.runtime.api.speech.SpeechScriptArguments
import org.autojs.autojs.runtime.api.speech.SpeechScriptValues
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.plugin.speech.api.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Execution-owned speech clients. Long plugin operations run in cancellable coroutines; every
 * transport request is short. Audio and result fragments fit both the Node and Bun JSON transports.
 * Recognition, model validation and license decisions remain in the existing speech plugin path.
 */
internal class RuntimeSpeechBridge(private val context: Context, private val cwd: String) : Closeable {
    private val clients = LinkedHashMap<String, Client>()
    private var closed = false

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        try {
            val args = JSONArray(request.argsJson)
            val value: Any? = if (request.method == "open") {
                require(args.length() == 0)
                synchronized(clients) {
                    check(!closed) { "Speech execution has ended" }
                    val id = UUID.randomUUID().toString()
                    clients[id] = Client()
                    mapOf("id" to id)
                }
            } else {
                val id = args.getString(0)
                if (request.method == "close") closeClient(id) else {
                    val client = synchronized(clients) { clients[id] }
                        ?: throw SpeechErrorMapper.failure(SpeechErrorCodes.SESSION_CLOSED, "speech client is closed or belongs to another execution")
                    when (request.method) {
                        "start" -> client.start(args.getString(1), args.getJSONArray(2))
                        "poll" -> client.poll(args.getJSONArray(1))
                        "cancel" -> client.cancel(args.getString(1))
                        "uploadBegin" -> client.uploadBegin(args.getLong(1))
                        "uploadChunk" -> client.uploadChunk(args.getString(1), args.getLong(2), args.getString(3))
                        "uploadDiscard" -> client.uploadDiscard(args.getString(1))
                        else -> throw IllegalArgumentException("Unknown speech bridge method")
                    }
                }
            }
            reply(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(JSONObject.wrap(value))))
        } catch (error: Throwable) {
            val failure = SpeechErrorMapper.fromThrowable(error, SpeechErrorMapper.Phase.CALL)
            reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(
                code = "ERR_AUTOJS6_SPEECH_${failure.code}", message = failure.message, module = "speech", method = request.method,
            )))
        }
    }

    fun closeClient(id: String): Boolean {
        val client = synchronized(clients) { clients.remove(id) } ?: return false
        client.close()
        return true
    }

    override fun close() {
        val ids = synchronized(clients) { closed = true; clients.keys.toList() }
        ids.forEach(::closeClient)
    }

    private data class Upload(val file: File, val size: Long, var written: Long = 0)
    private data class Event(val id: String, val bytes: ByteArray, val progress: Boolean, var offset: Int = 0)

    private inner class Client : Closeable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val lock = Any()
        private val jobs = LinkedHashMap<String, Job>()
        private val sessions = LinkedHashMap<String, Session>()
        private val uploads = LinkedHashMap<String, Upload>()
        private val events = ArrayDeque<Event>()
        private var queuedBytes = 0
        private var ended = false
        private val ttsClient by lazy { RuntimeTtsBridge.Client() }
        private val tts = lazy { TtsService(context, ttsClient) }

        private fun ensureOpen() {
            if (ended) throw SpeechErrorMapper.failure(SpeechErrorCodes.SESSION_CLOSED, "speech client has closed")
        }

        fun start(method: String, args: JSONArray): Map<String, Any?> {
            require(method in operations) { "Unknown speech operation: $method" }
            val id = UUID.randomUUID().toString()
            val listen = if (method == "listen") SpeechScriptArguments.listen(args.json(0)) else null
            val wake = if (method == "wake") SpeechScriptArguments.wake(args.json(0), args.json(1)) else null
            val session = if (listen != null || wake != null) Session(id) else null
            val startedAt = System.currentTimeMillis()
            synchronized(lock) {
                ensureOpen()
                if (session != null) sessions[id] = session
                val completed = java.util.concurrent.atomic.AtomicBoolean(false)
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        if (session != null) {
                            // Opening owns a dedicated Binder lease. Always receive and attach it, even
                            // after cancellation, so a late answer cannot strand the plugin microphone.
                            val proxy = withContext(NonCancellable) {
                                if (listen != null) SpeechPluginHost.openSession(context, listen, session.sink(false))
                                else SpeechPluginHost.openWakeSession(context, wake!!, session.sink(false),
                                    if (wake.thenListen == null) null else session.sink(true))
                            }
                            session.attach(proxy)
                            completed.set(true)
                        } else {
                            val result = invoke(id, method, args)
                            completed.set(true)
                            emit(id, "resolve", result, terminal = true)
                        }
                    } catch (error: Throwable) {
                        if (completed.compareAndSet(false, true)) {
                            if (session != null) session.fail(error) else emit(id, "reject", errorValue(error), terminal = true)
                        }
                    } finally {
                        synchronized(lock) { jobs.remove(id) }
                    }
                }
                jobs[id] = job
                job.invokeOnCompletion { error ->
                    if (error != null && completed.compareAndSet(false, true)) {
                        if (session != null) session.fail(error) else emit(id, "reject", errorValue(error), terminal = true)
                    }
                    synchronized(lock) { jobs.remove(id) }
                }
                job.start()
            }
            return mapOf("id" to id, "kind" to if (wake != null) "wake" else if (listen != null) "listen" else "operation",
                "source" to (listen?.source ?: "mic"), "keywords" to wake?.keywords?.map { it.keyword }, "startedAt" to startedAt)
        }

        private suspend fun invoke(id: String, method: String, args: JSONArray): Any? {
            fun model(i: Int = 0) = SpeechScriptArguments.modelId(args.json(i), "id")
            fun kind() = SpeechScriptArguments.kind(args.json(0), "kind")
            val progress: (Long, Long) -> Unit = { done, total -> emit(id, "progress", listOf(done, total), progress = true) }
            return when (method) {
                "isAvailable" -> SpeechPluginHost.isAvailable(context)
                "capabilities" -> SpeechScriptValues.capabilities(SpeechPluginHost.capabilities(context))
                "models.list" -> SpeechPluginHost.listModels(context, SpeechScriptArguments.modelQuery(args.json(0)))
                "models.install", "models.installAsync" -> SpeechPluginHost.installModel(context, SpeechScriptArguments.install(args.json(0), args.json(1)), progress)
                "models.importFile" -> SpeechPluginHost.importModel(context, path(args.json(0)), SpeechScriptArguments.importModel(args.json(1)), progress)
                "models.remove" -> SpeechPluginHost.removeModel(context, model())
                "models.setDefault" -> SpeechPluginHost.setDefaultModel(context, kind(), model(1))
                "models.getDefault" -> SpeechPluginHost.listModels(context, SpeechModelQuery(kind = kind(), installed = true)).firstOrNull(SpeechBundles::isDefault)
                "models.refreshCatalog" -> SpeechPluginHost.refreshCatalog(context)
                "recognize", "recognizeAsync" -> {
                    val parsed = SpeechScriptArguments.recognize(args.json(1))
                    withAudio(args, parsed.sampleRate) { SpeechPluginHost.recognizeFile(context, it, parsed.request) }
                }
                "detectVoice" -> {
                    val parsed = SpeechScriptArguments.detectVoice(args.json(1))
                    withAudio(args, parsed.sampleRate) { SpeechPluginHost.detectVoiceFile(context, it, parsed.request) }
                }
                "diarize", "diarizeAsync" -> SpeechPluginHost.diarizeFile(context, path(args.json(0)), SpeechScriptArguments.diarize(args.json(1)), progress)
                "detectLanguage", "detectLanguageAsync" -> SpeechPluginHost.detectLanguage(context, path(args.json(0)), SpeechScriptArguments.detectLanguage(args.json(1)), progress)
                "tag", "tagAsync" -> SpeechPluginHost.tagAudio(context, path(args.json(0)), SpeechScriptArguments.tag(args.json(1)), progress)
                "speaker.enroll", "speaker.enrollAsync" -> {
                    val parsed = SpeechScriptArguments.enroll(args.json(0), args.json(1), args.json(2))
                    SpeechPluginHost.enrollSpeaker(context, parsed.paths.map { path(com.google.gson.JsonPrimitive(it)) }, parsed.request)
                }
                "speaker.verify", "speaker.verifyAsync" -> SpeechPluginHost.verifySpeaker(context, path(args.json(1)), SpeechScriptArguments.verify(args.json(0), args.json(2)))
                "speaker.identify", "speaker.identifyAsync" -> SpeechPluginHost.identifySpeaker(context, path(args.json(0)), SpeechScriptArguments.identify(args.json(1)))
                "speaker.list" -> SpeechPluginHost.listSpeakers(context)
                "speaker.remove" -> SpeechPluginHost.removeSpeaker(context, SpeechScriptArguments.speakerName(args.json(0), "name"))
                "synthesize", "synthesizeAsync" -> SpeechPluginHost.synthesizeToFile(context,
                    SpeechScriptArguments.synthesize(args.json(0), args.json(2)), path(args.json(1)))
                "speak" -> speak(args)
                "session.stop", "session.cancel", "session.feed" -> {
                    val session = synchronized(lock) { sessions[args.getString(0)] }
                    if (session == null) {
                        if (method == "session.feed") throw SpeechErrorMapper.failure(SpeechErrorCodes.SESSION_CLOSED, "session has ended")
                        false
                    } else {
                        val proxy = session.proxy.await()
                        val round = args.optInt(2, -1)
                        when {
                            method == "session.feed" -> {
                                val upload = takeUpload(args.getString(1))
                                try {
                                    if (upload.size > SpeechLimits.MAX_FEED_BYTES) throw SpeechScriptArguments.limit("one feed call takes at most 1 MiB")
                                    upload.file.inputStream().use { input ->
                                        check(input.skip(SpeechAudio.WAV_HEADER_BYTES.toLong()) == SpeechAudio.WAV_HEADER_BYTES.toLong())
                                        val buffer = ByteArray(32 * 1024)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            currentCoroutineContext().ensureActive()
                                            proxy.feed(buffer.copyOf(count))
                                        }
                                    }
                                } finally { upload.file.delete() }
                            }
                            round >= 0 -> {
                                if (session.activeRound == round) (proxy as SpeechWakeSessionProxy).finishRound(method == "session.cancel")
                            }
                            method == "session.stop" -> proxy.finish()
                            else -> proxy.cancel()
                        }
                        true
                    }
                }
                else -> throw IllegalArgumentException("Unknown speech operation: $method")
            }
        }

        private suspend fun speak(args: JSONArray): Any? {
            val text = args.opt(0) as? String ?: throw SpeechScriptArguments.invalid("text must be a string")
            val parsed = SpeechScriptArguments.speak(args.json(1), text)
            if (!SpeechPluginHost.isTtsEngineInstalled(context, SpeechPluginIds.OFFICIAL_PACKAGE_NAME)) throw SpeechPluginHost.missingPlugin(context)
            val options = parsed.ttsOptions.deepCopy().apply {
                addProperty(SpeechScriptArguments.ENGINE, SpeechPluginIds.OFFICIAL_PACKAGE_NAME)
                parsed.speed?.let { addProperty(SpeechScriptArguments.RATE, it) }
            }
            val task = synchronized(lock) { ensureOpen(); tts.value.speechTask(text, options, selection = parsed.selection) }
            try {
                while (currentCoroutineContext().isActive) {
                    val batch = ttsClient.drain(0, setOf(task.id))
                    for (i in 0 until batch.length()) {
                        val event = batch.getJSONObject(i)
                        if (event.optBoolean("terminal")) {
                            if (event.getString("event") == "done") return event.get("value")
                            throw SpeechErrorMapper.failure(SpeechErrorCodes.ENGINE_FAILED, event.getJSONObject("value").optString("message"))
                        }
                    }
                    delay(25)
                }
                throw CancellationException("speech stopped")
            } finally { if (!task.isDone) task.stop() }
        }

        private fun path(value: JsonElement?): File {
            val path = File(SpeechScriptArguments.path(value, "path"))
            return if (path.isAbsolute) path else File(cwd.ifBlank { context.filesDir.path }, path.path)
        }

        private suspend fun <T> withAudio(args: JSONArray, rate: Int, block: suspend (File) -> T): T {
            if (args.opt(0) is String) return block(path(args.json(0)))
            val upload = takeUpload(args.getJSONObject(0).getString("upload"))
            try {
                val header = java.io.ByteArrayOutputStream()
                SpeechAudio.writeWavHeader(header, rate, upload.size.toInt())
                RandomAccessFile(upload.file, "rw").use { it.write(header.toByteArray()) }
                return block(upload.file)
            } finally { upload.file.delete() }
        }

        fun uploadBegin(size: Long): Map<String, String> = synchronized(lock) {
            ensureOpen()
            if (size < 0 || size % 2 != 0L) throw SpeechScriptArguments.invalid("PCM16 must have an even byte count")
            SpeechAudio.requireInputSize(size + SpeechAudio.WAV_HEADER_BYTES, "PCM input")
            val id = UUID.randomUUID().toString()
            val file = File.createTempFile("runtime-speech-", ".wav", context.cacheDir)
            try {
                file.outputStream().use { SpeechAudio.writeWavHeader(it, 16000, size.toInt()) }
                uploads[id] = Upload(file, size)
                mapOf("id" to id)
            } catch (error: Throwable) { file.delete(); throw error }
        }

        fun uploadChunk(id: String, offset: Long, encoded: String): Boolean = synchronized(lock) {
            ensureOpen()
            val upload = uploads[id] ?: throw SpeechScriptArguments.invalid("unknown PCM upload")
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(offset == upload.written && upload.written + bytes.size <= upload.size) { "PCM upload offset or size mismatch" }
            upload.file.appendBytes(bytes)
            upload.written += bytes.size
            true
        }

        fun uploadDiscard(id: String): Boolean = synchronized(lock) { uploads.remove(id)?.file?.delete() ?: false }
        private fun takeUpload(id: String): Upload = synchronized(lock) {
            ensureOpen()
            val upload = uploads.remove(id) ?: throw SpeechScriptArguments.invalid("unknown PCM upload")
            if (upload.written != upload.size) {
                upload.file.delete()
                throw SpeechScriptArguments.invalid("incomplete PCM upload")
            }
            upload
        }

        fun cancel(id: String): Boolean = synchronized(lock) {
            sessions[id]?.close()
            jobs[id]?.let { it.cancel(); true } ?: false
        }

        private fun emit(id: String, type: String, value: Any?, terminal: Boolean = false, progress: Boolean = false, round: Int? = null) {
            val bytes = NodeBridgeJson.valueToJson(JSONObject().put("type", type).put("value", JSONObject.wrap(value))
                .put("terminal", terminal).put("round", round)).toByteArray(Charsets.UTF_8)
            synchronized(lock) {
                if (ended) return
                if (progress) {
                    val iterator = events.iterator()
                    while (iterator.hasNext()) {
                        val event = iterator.next()
                        if (event.id == id && event.progress && event.offset == 0) { iterator.remove(); queuedBytes -= event.bytes.size }
                    }
                }
                if (!terminal && queuedBytes + bytes.size > SpeechLimits.MAX_EVENT_BYTES * 4) {
                    throw SpeechScriptArguments.limit("speech client did not drain its events")
                }
                events.addLast(Event(id, bytes, progress)); queuedBytes += bytes.size
            }
        }

        fun poll(selected: JSONArray): JSONArray = synchronized(lock) {
            ensureOpen()
            val ids = (0 until selected.length()).map { selected.getString(it) }.toSet()
            val result = JSONArray()
            val iterator = events.iterator()
            while (iterator.hasNext() && result.length() < 8) {
                val event = iterator.next()
                if (event.id !in ids) continue
                while (event.offset < event.bytes.size && result.length() < 8) {
                    val count = minOf(12 * 1024, event.bytes.size - event.offset)
                    val encoded = Base64.encodeToString(event.bytes, event.offset, count, Base64.NO_WRAP)
                    event.offset += count; queuedBytes -= count
                    result.put(JSONObject().put("id", event.id).put("data", encoded).put("last", event.offset == event.bytes.size))
                }
                if (event.offset == event.bytes.size) iterator.remove()
            }
            result
        }

        private inner class Session(private val id: String) : Closeable {
            val proxy = CompletableDeferred<SpeechSessionProxy>()
            private var finished = false
            @Volatile var activeRound = -1; private set

            fun attach(value: SpeechSessionProxy) {
                synchronized(lock) {
                    proxy.complete(value)
                    if (ended || finished) value.close()
                }
            }

            fun sink(roundChannel: Boolean) = SpeechEventSink { event ->
                synchronized(lock) {
                    if (ended || finished) return@SpeechEventSink
                    try {
                        if (roundChannel && event.type == SpeechContract.EVENT_READY) activeRound = event.round ?: 0
                        if (roundChannel && event.type == SpeechContract.EVENT_END && activeRound == event.round) activeRound = -1
                        val terminal = !roundChannel && event.type == SpeechContract.EVENT_END
                        emit(id, event.type, event.error?.let { mapOf("code" to it.code, "message" to it.message) }
                            ?: SpeechScriptValues.withoutRound(event.payload), terminal,
                            round = if (roundChannel) event.round ?: 0 else if (event.type == SpeechContract.EVENT_WAKE) SpeechScriptValues.roundOf(event.payload) else null)
                        if (terminal) finish(false)
                    } catch (error: Throwable) { fail(error) }
                }
            }

            fun fail(error: Throwable) = synchronized(lock) {
                if (ended || finished) return@synchronized
                val failure = errorValue(error)
                emit(id, "error", failure, terminal = true)
                emit(id, "end", mapOf("reason" to if (failure["code"] == SpeechErrorCodes.LIMIT_EXCEEDED) "overflow" else "error"), terminal = true)
                proxy.completeExceptionally(error)
                finish(true)
            }

            private fun finish(cancel: Boolean) {
                if (finished) return
                finished = true; sessions.remove(id)
                if (proxy.isCompleted && !proxy.isCancelled) runCatching {
                    @OptIn(ExperimentalCoroutinesApi::class)
                    val value = proxy.getCompleted()
                    if (cancel) value.close() else value.release()
                }
            }

            override fun close() = synchronized(lock) {
                if (!finished) {
                    emit(id, "end", mapOf("reason" to "cancelled"), terminal = true)
                    finish(true)
                }
            }
        }

        override fun close() {
            synchronized(lock) {
                if (ended) return
                ended = true
                sessions.values.toList().forEach { it.close() }; sessions.clear()
                scope.cancel(); jobs.clear()
                uploads.values.forEach { it.file.delete() }; uploads.clear()
                events.clear(); queuedBytes = 0
                if (tts.isInitialized()) { tts.value.close(); ttsClient.close() }
            }
        }
    }

    companion object {
        val methods = setOf("open", "start", "poll", "cancel", "uploadBegin", "uploadChunk", "uploadDiscard", "close")
        val operations = setOf("isAvailable", "capabilities", "recognize", "recognizeAsync", "detectVoice", "listen", "wake",
            "synthesize", "synthesizeAsync", "speak", "models.list", "models.install", "models.installAsync", "models.importFile",
            "models.remove", "models.setDefault", "models.getDefault", "models.refreshCatalog", "session.stop", "session.cancel", "session.feed",
            "diarize", "diarizeAsync", "detectLanguage", "detectLanguageAsync", "tag", "tagAsync", "speaker.enroll", "speaker.enrollAsync",
            "speaker.verify", "speaker.verifyAsync", "speaker.identify", "speaker.identifyAsync", "speaker.list", "speaker.remove")

        private fun JSONArray.json(index: Int): JsonElement? =
            if (isNull(index)) null else JsonParser.parseString(NodeBridgeJson.valueToJson(get(index)))

        private fun errorValue(error: Throwable): Map<String, String> =
            SpeechErrorMapper.fromThrowable(error, SpeechErrorMapper.Phase.CALL).let { mapOf("code" to it.code, "message" to it.message) }
    }
}
