package org.autojs.autojs.engine

import android.content.Context
import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.autojs.autojs.core.plugin.epub.*
import org.autojs.autojs.runtime.api.epub.EpubBookOperations
import org.autojs.autojs.runtime.api.epub.EpubScriptArguments
import org.autojs.autojs.runtime.api.epub.EpubScriptValues
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID

/** Portable EPUB operations share the Rhino argument/operation layer and the real plugin. */
internal class RuntimeEpubBridge(private val context: Context, private val cwd: String) : Closeable {
    private val clients = LinkedHashMap<String, Client>()
    private var closed = false

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        try {
            val args = JSONArray(request.argsJson)
            val result = if (request.method == "open") synchronized(clients) {
                require(args.length() == 0)
                check(!closed) { "EPUB execution has ended" }
                val id = UUID.randomUUID().toString()
                clients[id] = Client()
                mapOf("id" to id)
            } else {
                val id = args.getString(0)
                if (request.method == "close") closeClient(id) else {
                    val client = synchronized(clients) { clients[id] } ?: throw closedError()
                    when (request.method) {
                        "start" -> client.start(args.getString(1), args.getJSONArray(2))
                        "poll" -> client.poll(args.getJSONArray(1))
                        "cancel" -> client.cancel(args.getString(1))
                        else -> throw EpubScriptArguments.invalid("Unknown EPUB bridge method")
                    }
                }
            }
            reply(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(JSONObject.wrap(result))))
        } catch (error: Throwable) {
            val failure = EpubErrorMapper.fromThrowable(error, EpubErrorMapper.Phase.CALL)
            reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(
                code = "ERR_AUTOJS6_EPUB_${failure.code}", message = failure.message,
                module = "epub", method = request.method,
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

    private data class Event(val id: String, val bytes: ByteArray, var offset: Int = 0)

    private inner class Client : Closeable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val lock = Any()
        private val jobs = LinkedHashMap<String, Job>()
        private val books = LinkedHashMap<String, EpubBookOperations>()
        private val readers = LinkedHashMap<String, Reader>()
        private val events = ArrayDeque<Event>()
        private var ended = false

        private fun ensureOpen() {
            if (ended) throw closedError()
        }

        fun start(method: String, args: JSONArray): Map<String, String> {
            require(method in operations) { "Unknown EPUB operation: $method" }
            val id = UUID.randomUUID().toString()
            synchronized(lock) {
                ensureOpen()
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val result = invoke(id, method, args)
                        currentCoroutineContext().ensureActive()
                        emit(id, "resolve", result)
                    } catch (error: Throwable) {
                        releaseCreated(id, finish = true)
                        emit(id, "reject", errorValue(error))
                    } finally {
                        synchronized(lock) { jobs.remove(id) }
                    }
                }
                jobs[id] = job
                job.invokeOnCompletion { synchronized(lock) { jobs.remove(id) } }
                job.start()
            }
            return mapOf("id" to id)
        }

        private fun path(value: JsonElement?, name: String = "path"): String {
            val parsed = EpubScriptArguments.path(value, name)
            return File(parsed).let { if (it.isAbsolute) it else File(cwd, parsed) }.absolutePath
        }

        private suspend fun invoke(id: String, method: String, args: JSONArray): Any? {
            if (method == "isAvailable") return try {
                EpubPluginHost.getCapabilities(context); true
            } catch (_: EpubPluginException) { false }
            if (method == "openBook" || method == "read") {
                val request = EpubScriptArguments.open(args.json(0))
                val source = path(com.google.gson.JsonPrimitive(request.path))
                if (method == "read") {
                    val options = EpubScriptArguments.read(args.json(1))
                    val reader = Reader(id)
                    synchronized(lock) { ensureOpen(); readers[id] = reader }
                    // Receive late Binder results even after cancellation so their lease can be closed.
                    val proxy = withContext(NonCancellable) {
                        EpubPluginHost.openReader(context, source, EpubReaderOptions(
                            request.displayName, options.locatorJson, options.href,
                            options.progression, options.preferencesJson,
                        ), reader)
                    }
                    reader.attach(proxy)
                    return mapOf("id" to id, "path" to source)
                }
                val client = withContext(NonCancellable) { EpubPluginHost.openBook(context, source, request.displayName) }
                val book = EpubBookOperations(client, source)
                val active = currentCoroutineContext().isActive
                synchronized(lock) {
                    if (ended || !active) {
                        client.close()
                        throw closedError()
                    }
                    books[id] = book
                }
                return mapOf("id" to id, "path" to source, "metadata" to book.metadata(),
                    "toc" to book.toc(), "readingOrder" to book.readingOrder(), "positions" to book.positions())
            }
            val handle = args.getString(0)
            if (method.startsWith("book.")) {
                if (method == "book.close") {
                    val book = synchronized(lock) { books.remove(handle) } ?: return false
                    book.closeQuietly()
                    return true
                }
                val book = synchronized(lock) { books[handle] } ?: throw closedError()
                return when (method) {
                    "book.text" -> book.text(EpubScriptArguments.text(args.json(1), args.json(2)))
                    "book.textAll" -> book.text(EpubScriptArguments.textAll(args.json(1)))
                    "book.search" -> book.search(EpubScriptArguments.search(args.json(1), args.json(2)))
                    "book.annotations" -> book.annotations()
                    "book.cover" -> {
                        val options = EpubScriptArguments.export(args.json(2))
                        val href = book.coverHref()
                        if (args.isNull(1)) mapOf("base64" to Base64.encodeToString(book.bytes(href), Base64.NO_WRAP))
                        else book.save(href, path(args.json(1), "outputPath"), options.overwrite)
                    }
                    "book.resource" -> book.save(EpubScriptArguments.href(args.json(1), "href"),
                        path(args.json(2), "outputPath"), EpubScriptArguments.export(args.json(3)).overwrite)
                    else -> throw EpubScriptArguments.invalid("Unknown book operation")
                }
            }
            if (method == "reader.close") {
                val reader = synchronized(lock) { readers[handle] } ?: return false
                reader.close(EpubScriptArguments.close(args.json(1)).finish)
                return true
            }
            val reader = synchronized(lock) { readers[handle] } ?: throw closedError()
            val proxy = reader.proxy.await()
            return when (method) {
                "reader.goTo" -> EpubScriptArguments.goTo(args.json(1)).let {
                    proxy.goTo(it.locatorJson, it.href, it.progression); true
                }
                "reader.next" -> { proxy.navigate(EpubContract.DIRECTION_NEXT_PAGE); true }
                "reader.prev" -> { proxy.navigate(EpubContract.DIRECTION_PREVIOUS_PAGE); true }
                "reader.nextChapter" -> { proxy.navigate(EpubContract.DIRECTION_NEXT_CHAPTER); true }
                "reader.prevChapter" -> { proxy.navigate(EpubContract.DIRECTION_PREVIOUS_CHAPTER); true }
                "reader.setPreferences" -> { proxy.setPreferences(EpubScriptArguments.preferences(args.json(1))); true }
                "reader.bookmarks" -> JSONArray(proxy.bookmarks())
                else -> throw EpubScriptArguments.invalid("Unknown reader operation")
            }
        }

        fun cancel(id: String): Boolean {
            val job = synchronized(lock) { jobs[id] }
            job?.cancel()
            releaseCreated(id, finish = true)
            synchronized(lock) { events.removeAll { it.id == id } }
            return job != null
        }

        private fun releaseCreated(id: String, finish: Boolean) {
            val book = synchronized(lock) { books.remove(id) }
            book?.client?.close()
            val reader = synchronized(lock) { readers[id] }
            reader?.close(finish)
        }

        private fun emit(id: String, type: String, value: Any?) {
            val bytes = NodeBridgeJson.valueToJson(JSONObject().put("type", type)
                .put("value", JSONObject.wrap(value))).toByteArray(Charsets.UTF_8)
            synchronized(lock) {
                if (!ended) events.addLast(Event(id, bytes))
            }
        }

        fun poll(selected: JSONArray): JSONArray = synchronized(lock) {
            ensureOpen()
            val ids = (0 until selected.length()).map { selected.getString(it) }.toSet()
            val result = JSONArray()
            val iterator = events.iterator()
            while (iterator.hasNext() && result.length() < 3) {
                val event = iterator.next()
                if (event.id !in ids) continue
                while (event.offset < event.bytes.size && result.length() < 3) {
                    val size = minOf(12 * 1024, event.bytes.size - event.offset)
                    val data = Base64.encodeToString(event.bytes, event.offset, size, Base64.NO_WRAP)
                    event.offset += size
                    result.put(JSONObject().put("id", event.id).put("data", data).put("last", event.offset == event.bytes.size))
                }
                if (event.offset == event.bytes.size) iterator.remove()
            }
            result
        }

        private inner class Reader(private val id: String) : EpubReaderSink {
            val proxy = CompletableDeferred<EpubReaderClient>()
            private var finished = false
            private var finishWhenAttached = false

            fun attach(value: EpubReaderClient) {
                synchronized(lock) {
                    proxy.complete(value)
                    if (ended || finished) value.close(finishWhenAttached)
                }
            }

            override fun onEvent(event: EpubReaderEvent) {
                synchronized(lock) {
                    if (ended || finished) return
                    // Pending UI events are bounded; ordinary operation results retain native text/resource limits.
                    if (events.count { it.id == id } >= 1024) {
                        emit(id, "event", mapOf("type" to "error", "code" to EpubErrorCodes.LIMIT_EXCEEDED,
                            "message" to "EPUB reader events were not drained"))
                        close(true, "overflow")
                        return
                    }
                    emit(id, "event", eventValue(event))
                    if (event.type == EpubContract.EVENT_CLOSE) finish(false)
                }
            }

            override fun onDropped(reason: String, generation: Long, seq: Long) = Unit

            private fun finish(finish: Boolean) {
                finished = true
                finishWhenAttached = finish
                readers.remove(id)
                if (proxy.isCompleted && !proxy.isCancelled) runCatching {
                    @OptIn(ExperimentalCoroutinesApi::class)
                    proxy.getCompleted().close(finish)
                }
            }

            fun close(finish: Boolean, reason: String = EpubContract.REASON_HOST) = synchronized(lock) {
                if (!finished) {
                    emit(id, "event", mapOf("type" to "close", "reason" to reason))
                    finish(finish)
                }
            }

        }

        override fun close() {
            val openBooks: List<EpubBookOperations>
            val openReaders: List<Reader>
            synchronized(lock) {
                if (ended) return
                ended = true
                scope.cancel()
                openBooks = books.values.toList(); books.clear()
                openReaders = readers.values.toList(); readers.clear()
                jobs.clear(); events.clear()
            }
            openBooks.forEach { it.client.close() }
            // Match Rhino: script exit releases the session while leaving the user's reader open.
            openReaders.forEach { it.close(false) }
        }
    }

    companion object {
        val methods = setOf("open", "start", "poll", "cancel", "close")
        val operations = setOf("isAvailable", "openBook", "read", "book.text", "book.textAll", "book.cover",
            "book.resource", "book.search", "book.annotations", "book.close", "reader.goTo", "reader.next",
            "reader.prev", "reader.nextChapter", "reader.prevChapter", "reader.setPreferences", "reader.bookmarks", "reader.close")

        private fun JSONArray.json(index: Int): JsonElement? =
            if (isNull(index)) null else JsonParser.parseString(NodeBridgeJson.valueToJson(get(index)))

        private fun closedError() = EpubPluginException(EpubErrorMapper.normalize(
            EpubErrorCodes.SESSION_CLOSED, "EPUB handle is closed or belongs to another client"))

        private fun errorValue(error: Throwable) = EpubErrorMapper.fromThrowable(error, EpubErrorMapper.Phase.CALL)
            .let { mapOf("code" to it.code, "message" to it.message) }

        internal fun eventValue(event: EpubReaderEvent): Map<String, Any?> {
            val fields: Map<String, Any?> = when (event.type) {
                EpubContract.EVENT_OPEN -> linkedMapOf("locator" to EpubScriptValues.toPlain(event.locatorJson),
                    "title" to event.title, "href" to event.href, "positions" to (event.positions ?: 0))
                EpubContract.EVENT_PROGRESS -> linkedMapOf("locator" to EpubScriptValues.toPlain(event.locatorJson),
                    "totalProgression" to event.progression, "chapterTitle" to event.title, "href" to event.href)
                EpubContract.EVENT_BOOKMARK, EpubContract.EVENT_HIGHLIGHT -> {
                    val highlight = event.type == EpubContract.EVENT_HIGHLIGHT
                    val source = EpubScriptValues.list(if (highlight) event.annotationsJson else event.bookmarksJson).firstOrNull() as? Map<*, *>
                    val names = if (highlight) listOf("id", "style", "color", "note", "quote", "title", "locator", "createdAt", "updatedAt")
                        else listOf("locator", "createdAt", "title", "text")
                    linkedMapOf<String, Any?>("action" to (event.change ?: "unknown")).apply {
                        names.forEach { put(it, source?.get(it)) }
                    }
                }
                EpubContract.EVENT_ERROR -> EpubErrorMapper.normalize(event.errorCode, event.errorMessage)
                    .let { mapOf("code" to it.code, "message" to it.message) }
                else -> mapOf("reason" to (event.reason ?: EpubContract.REASON_ERROR))
            }
            return linkedMapOf<String, Any?>("type" to event.type).apply { putAll(fields) }
        }
    }
}
