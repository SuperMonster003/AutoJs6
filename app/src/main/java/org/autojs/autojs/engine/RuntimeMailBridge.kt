package org.autojs.autojs.engine

import android.content.Context
import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.*
import org.autojs.autojs.core.plugin.mail.*
import org.autojs.autojs.runtime.api.augment.mail.MailWatch
import org.autojs.autojs.runtime.api.augment.mail.MailWatchRunner
import org.autojs.autojs.runtime.api.augment.mail.MailWatchTarget
import org.autojs.autojs.runtime.api.mail.*
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Portable clients reuse the mail protocol, argument validation, transfers and watch recovery. */
internal class RuntimeMailBridge(private val context: Context, private val cwd: String) : Closeable {
    private val owners = LinkedHashMap<String, Owner>()
    private var ended = false

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        try {
            val args = JSONArray(request.argsJson)
            val result: Any? = if (request.method == "open") synchronized(owners) {
                require(args.length() == 0)
                if (ended) throw closedError()
                if (owners.size >= 128) throw failure(MailErrorCodes.LIMIT_EXCEEDED, "Too many mail runtime clients")
                val id = UUID.randomUUID().toString()
                owners[id] = Owner()
                mapOf("id" to id)
            } else {
                val id = args.getString(0)
                if (request.method == "close") closeClient(id) else {
                    val owner = synchronized(owners) { owners[id] } ?: throw closedError()
                    when (request.method) {
                        "start" -> owner.start(args.getString(1), args.getJSONArray(2))
                        "poll" -> owner.poll(args.getJSONArray(1))
                        "cancel" -> owner.cancel(args.getString(1))
                        "answer" -> owner.answer(args.getString(1), args.optString(2), args.optBoolean(3))
                        else -> throw failure(MailErrorCodes.INVALID_ARGUMENT, "Unknown mail bridge method")
                    }
                }
            }
            reply(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(JSONObject.wrap(result))))
        } catch (error: Throwable) {
            val value = MailErrorMapper.fromThrowable(error, MailErrorMapper.Phase.CALL)
            reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(
                code = "ERR_AUTOJS6_MAIL_" + value.code, message = value.message,
                module = "mail", method = request.method,
            )))
        }
    }

    fun closeClient(id: String): Boolean {
        val owner = synchronized(owners) { owners.remove(id) } ?: return false
        owner.close()
        return true
    }

    override fun close() {
        val ids = synchronized(owners) { ended = true; owners.keys.toList() }
        ids.forEach(::closeClient)
    }

    private data class Event(val id: String, val bytes: ByteArray, var offset: Int = 0)
    private class Client(val session: MailSessionClient, val options: MailScriptOptions) {
        var closed = false
    }

    private inner class Owner : Closeable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val lock = Any()
        private val jobs = LinkedHashMap<String, Job>()
        private val clients = LinkedHashMap<String, Client>()
        private val watches = LinkedHashMap<String, WatchTarget>()
        private val tokens = LinkedHashMap<String, Pair<String, CompletableDeferred<String>>>()
        private val events = ArrayDeque<Event>()
        private val generations = AtomicLong()
        private var closed = false
        private var queuedBytes = 0L

        private fun ensureOpen() {
            if (closed) throw closedError()
        }

        fun start(method: String, args: JSONArray): Map<String, String> {
            require(method in operations) { "Unknown mail operation" }
            val id = UUID.randomUUID().toString()
            synchronized(lock) {
                ensureOpen()
                if (jobs.size >= 64) throw failure(MailErrorCodes.LIMIT_EXCEEDED, "Too many pending mail operations")
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val result = invoke(id, method, args)
                        currentCoroutineContext().ensureActive()
                        emit(id, "resolve", result)
                    } catch (error: Throwable) {
                        releaseCreated(id)
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

        private suspend fun invoke(id: String, method: String, args: JSONArray): Any? {
            when (method) {
                "providers.list", "providers.get", "providers.resolve" -> {
                    val catalog = MailProviderCatalog(MailPluginHost.listProviders(context))
                    return when (method) {
                        "providers.get" -> catalog.get(args.optString(0).takeIf { !args.isNull(0) })
                        "providers.resolve" -> catalog.resolve(args.optString(0).takeIf { !args.isNull(0) })
                        else -> catalog.list()
                    }
                }
                "accounts.list" -> return MailScriptValues.toPlain(MailPluginHost.listSavedAccounts(context))
                "connect" -> {
                    val raw = args.json(0)
                    val hasProvider = args.optBoolean(1)
                    val options = when {
                        raw?.isJsonPrimitive == true && raw.asJsonPrimitive.isString -> MailScriptOptions.alias(raw.asString)
                        raw?.isJsonObject == true -> {
                            val values = raw.asJsonObject.deepCopy()
                            if (hasProvider && !values.has(MailScriptOptions.ACCESS_TOKEN)) {
                                values.addProperty(MailScriptOptions.ACCESS_TOKEN, requestToken(id))
                            }
                            MailScriptOptions.inline(values, hasProvider)
                        }
                        else -> throw failure(MailErrorCodes.INVALID_ARGUMENT, "mail.connect expects options or an account alias")
                    }
                    val provider: (suspend () -> String)? = if (hasProvider) ({ requestToken(id) }) else null
                    val client = Client(MailPluginHost.newClient(context, options.spec, provider), options)
                    synchronized(lock) {
                        ensureOpen()
                        if (clients.size >= 32) throw failure(MailErrorCodes.LIMIT_EXCEEDED, "Too many open mail accounts")
                        clients[id] = client
                    }
                    withContext(NonCancellable) { client.session.open() }
                    if (!currentCoroutineContext().isActive || synchronized(lock) { closed || client.closed }) {
                        closeAccount(id)
                        throw closedError()
                    }
                    return mapOf("id" to id, "account" to MailScriptValues.toPlain(options.snapshot.toString()),
                        "isConnected" to client.session.isOpen)
                }
                "watch.stop" -> {
                    val watch = synchronized(lock) { watches[args.getString(0)] } ?: return false
                    watch.requestStop()
                    return true
                }
            }
            val key = args.getString(0)
            if (method == "client.close") return closeAccount(key)
            val client = synchronized(lock) { clients[key]?.takeUnless { it.closed } } ?: throw closedError()
            if (method == "client.watch") {
                val plan = MailScriptArguments.watch(args.json(1), args.json(2))
                val target = WatchTarget(id, key, plan.options.folder)
                val runner = MailWatchRunner(scope, client.session, plan.options, plan.reconnect,
                    generations::incrementAndGet, target,
                    onFinished = { synchronized(lock) { watches.remove(id) } })
                synchronized(lock) {
                    ensureOpen()
                    if (watches.size >= 32) throw failure(MailErrorCodes.LIMIT_EXCEEDED, "Too many mail watches")
                    watches[id] = target
                }
                withContext(NonCancellable) { runner.open() }
                if (!currentCoroutineContext().isActive || synchronized(lock) { closed || client.closed || target.isClosed }) {
                    target.requestStop()
                    throw closedError()
                }
                return target.snapshot()
            }
            suspend fun call(op: String, payload: JsonElement? = null): Any? =
                MailScriptValues.toPlain(client.session.call(op, payload?.toString(), onProgress = { value ->
                    progress(id, client, value)
                }))
            try {
                return when (method) {
                    "client.test" -> call(MailContract.OP_SESSION_TEST)
                    "client.folders" -> call(MailContract.OP_FOLDERS_LIST, MailScriptArguments.folders(args.json(1)))
                    "client.folderStatus" -> call(MailContract.OP_FOLDERS_STATUS, MailScriptArguments.folderStatus(args.json(1)))
                    "client.createFolder" -> call(MailContract.OP_FOLDERS_CREATE, MailScriptArguments.createFolder(args.json(1)))
                    "client.deleteFolder" -> call(MailContract.OP_FOLDERS_DELETE, MailScriptArguments.deleteFolder(args.json(1)))
                    "client.renameFolder" -> call(MailContract.OP_FOLDERS_RENAME, MailScriptArguments.renameFolder(args.json(1), args.json(2)))
                    "client.fetch" -> call(MailContract.OP_MESSAGES_LIST, MailScriptArguments.fetch(args.json(1)))
                    "client.search" -> call(MailContract.OP_MESSAGES_SEARCH, MailScriptArguments.search(args.json(1), args.json(2)))
                    "client.get", "client.fetchBody" -> call(MailContract.OP_MESSAGES_GET, MailScriptArguments.get(args.json(1), args.json(2)))
                    "client.setFlags" -> uids(call(MailContract.OP_MESSAGES_SET_FLAGS,
                        MailScriptArguments.flags(args.json(1), args.json(2), args.json(3), args.json(4))))
                    "client.markRead", "client.markUnread", "client.flag", "client.unflag" -> {
                        val flag = if (method == "client.markRead" || method == "client.markUnread") "seen" else "flagged"
                        val mode = if (method == "client.markUnread" || method == "client.unflag") "remove" else "add"
                        uids(call(MailContract.OP_MESSAGES_SET_FLAGS,
                            MailScriptArguments.flags(args.json(1), JsonPrimitive(flag), JsonPrimitive(mode), args.json(2))))
                    }
                    "client.move", "client.copy" -> {
                        val op = if (method == "client.move") MailContract.OP_MESSAGES_MOVE else MailContract.OP_MESSAGES_COPY
                        val result = call(op, MailScriptArguments.transfer(args.json(1), args.json(2), args.json(3)))
                        (result as? Map<*, *>)?.get("uids") ?: true
                    }
                    "client.delete" -> uids(call(MailContract.OP_MESSAGES_DELETE, MailScriptArguments.delete(args.json(1), args.json(2))))
                    "client.expunge" -> (call(MailContract.OP_MESSAGES_EXPUNGE, MailScriptArguments.expunge(args.json(1))) as? Map<*, *>)?.get("count")
                    "client.send", "client.append" -> {
                        val append = method == "client.append"
                        val plan = if (append) MailScriptArguments.append(args.json(1), args.json(2), args.json(3), ::path, ::sizeOf)
                            else MailScriptArguments.send(args.json(1), args.json(2), ::path, ::sizeOf)
                        val result = MailAttachmentSource.open(plan.attachmentPaths).use { source ->
                            MailScriptValues.toPlain(client.session.call(
                                if (append) MailContract.OP_MESSAGES_APPEND else MailContract.OP_MAIL_SEND,
                                plan.args.toString(), descriptors = source.descriptorArray.takeIf { it.isNotEmpty() },
                                transferBytes = source.totalBytes.takeIf { it > 0 },
                                onProgress = { progress(id, client, it) }))
                        }
                        if (append) (result as? Map<*, *>)?.get("uid") else result
                    }
                    "client.download", "client.raw" -> {
                        val raw = method == "client.raw"
                        val plan = if (raw) MailScriptArguments.raw(args.json(1), args.json(3))
                            else MailScriptArguments.download(args.json(1), args.json(3))
                        val destination = if (args.isNull(2)) cwd else path(args.getString(2))
                        val sink = MailAttachmentSink.create(destination, plan.fileName, expectedSize = null, overwrite = plan.overwrite)
                        MailDownloads.download(client.session,
                            if (raw) MailContract.OP_MESSAGES_RAW else MailContract.OP_ATTACHMENTS_DOWNLOAD,
                            plan.args.toString(), sink, plan.size) {
                                progress(id, client, it, plan.size)
                            }.path
                    }
                    else -> throw failure(MailErrorCodes.INVALID_ARGUMENT, "Unknown mail client operation")
                }
            } finally {
                emit(key, "state", mapOf("isConnected" to client.session.isOpen, "isClosed" to client.closed))
            }
        }

        private suspend fun requestToken(clientId: String): String {
            val id = UUID.randomUUID().toString()
            val answer = CompletableDeferred<String>()
            synchronized(lock) { ensureOpen(); tokens[id] = clientId to answer }
            try {
                emit(clientId, "token", mapOf("id" to id))
                return withTimeout(60_000L) { answer.await() }
            } finally {
                synchronized(lock) { tokens.remove(id) }
            }
        }

        private fun progress(id: String, client: Client, value: MailJson.Progress, totalHint: Long? = null) {
            if (client.options.debug && !value.debug.isNullOrEmpty()) emit(id, "debug", value.debug)
            if (value.hasTransfer) emit(id, "progress", mapOf("transferred" to value.transferred, "total" to (value.total ?: totalHint)))
        }

        fun answer(id: String, token: String, accepted: Boolean): Boolean = synchronized(lock) {
            ensureOpen()
            val pending = tokens.remove(id)?.second ?: return false
            if (!accepted || token.isBlank() || token.length > 256 * 1024) {
                pending.completeExceptionally(failure(MailErrorCodes.AUTH_FAILED, "Token provider did not supply a valid token"))
            } else pending.complete(token)
            true
        }

        private fun path(value: String): String {
            if (value.isBlank()) throw failure(MailErrorCodes.INVALID_ARGUMENT, "File path must not be blank")
            return File(value).let { if (it.isAbsolute) it else File(cwd, value) }.absolutePath
        }

        private fun sizeOf(value: String): Long? = File(value).takeIf { it.isFile }?.length()

        private suspend fun closeAccount(id: String): Boolean {
            val client = synchronized(lock) { clients.remove(id)?.also { it.closed = true } } ?: return false
            synchronized(lock) {
                val iterator = tokens.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value.first == id) { entry.value.second.cancel(); iterator.remove() }
                }
            }
            val active = synchronized(lock) { watches.values.filter { it.clientId == id } }
            active.forEach { it.requestStop(MailWatch.REASON_CLIENT_CLOSED) }
            withContext(NonCancellable) { withTimeoutOrNull(3_000L) { client.session.close() } }
            emit(id, "state", mapOf("isConnected" to false, "isClosed" to true))
            return true
        }

        private suspend fun releaseCreated(id: String) {
            closeAccount(id)
            synchronized(lock) { watches[id] }?.requestStop()
        }

        fun cancel(id: String): Boolean {
            val job = synchronized(lock) { jobs[id] }
            job?.cancel()
            // Cleanup runs outside the cancelled operation and also covers a late open result.
            scope.launch { releaseCreated(id) }
            synchronized(lock) {
                val iterator = events.iterator()
                while (iterator.hasNext()) {
                    val event = iterator.next()
                    if (event.id == id) { queuedBytes -= event.bytes.size; iterator.remove() }
                }
            }
            return job != null
        }

        private fun emit(id: String, type: String, value: Any?) {
            val bytes = NodeBridgeJson.valueToJson(JSONObject().put("type", type)
                .put("value", JSONObject.wrap(value))).toByteArray(Charsets.UTF_8)
            synchronized(lock) {
                if (closed) return
                if (queuedBytes + bytes.size > 64L * 1024 * 1024) {
                    throw failure(MailErrorCodes.LIMIT_EXCEEDED, "Mail results were not drained")
                }
                events.addLast(Event(id, bytes))
                queuedBytes += bytes.size
            }
        }

        fun poll(selected: JSONArray): JSONArray = synchronized(lock) {
            ensureOpen()
            require(selected.length() <= 256)
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
                if (event.offset == event.bytes.size) { queuedBytes -= event.bytes.size; iterator.remove() }
            }
            result
        }

        private inner class WatchTarget(private val id: String, val clientId: String, private val folder: String) : MailWatchTarget {
            private var modeValue: String? = null
            private var generationValue = 0L
            private var closedValue = false
            private var closeReason: String? = null
            private var stopAction: (() -> Unit)? = null
            override val currentMode: String? get() = synchronized(lock) { modeValue }
            override val isClosed: Boolean get() = synchronized(lock) { closedValue }

            fun snapshot(): Map<String, Any?> = synchronized(lock) {
                mapOf("id" to id, "folder" to folder, "mode" to modeValue, "generation" to generationValue,
                    "isClosed" to closedValue, "reason" to closeReason)
            }

            override fun bindStop(action: () -> Unit): MailWatchTarget = apply {
                val stopNow = synchronized(lock) { stopAction = action; closedValue || closed }
                if (stopNow) action()
            }
            override fun generation(generation: Long): MailWatchTarget = apply {
                synchronized(lock) { generationValue = generation }
                emit(id, "state", snapshot())
            }
            override fun initialMode(mode: String?): MailWatchTarget = apply { synchronized(lock) { modeValue = mode } }
            override fun message(plain: Any?, decorate: (Any?) -> Any?): Boolean = event("message", decorate(plain))
            override fun mode(mode: String): Boolean {
                synchronized(lock) { modeValue = mode }
                return event("mode", mode)
            }
            override fun resync(reason: String): Boolean = event("resync", reason)
            override fun error(error: MailError, convert: (MailError) -> Any?): Boolean = event("error", errorValue(error))

            private fun event(name: String, value: Any?): Boolean = synchronized(lock) {
                if (closed || closedValue) return false
                if (events.count { it.id == id } >= 1024) throw failure(MailErrorCodes.LIMIT_EXCEEDED, "Mail watch events were not drained")
                emit(id, "event", mapOf("name" to name, "value" to value))
                true
            }

            override fun close(reason: String): Boolean = synchronized(lock) {
                if (closedValue) return false
                closedValue = true
                closeReason = reason
                emit(id, "event", mapOf("name" to "close", "value" to reason))
                watches.remove(id)
                true
            }

            override fun fail(error: MailError, reason: String, convert: (MailError) -> Any?): Boolean {
                // Reserve terminal delivery by discarding undrained watch events after overflow.
                synchronized(lock) {
                    val iterator = events.iterator()
                    while (iterator.hasNext()) {
                        val event = iterator.next()
                        if (event.id == id) { queuedBytes -= event.bytes.size; iterator.remove() }
                    }
                }
                event("error", errorValue(error))
                return close(reason)
            }

            override fun cancelSilently(): Boolean = synchronized(lock) {
                if (closedValue) return false
                closedValue = true
                closeReason = MailWatch.REASON_EXIT
                watches.remove(id)
                true
            }

            fun requestStop(reason: String = MailWatch.REASON_STOPPED) {
                val action = synchronized(lock) { stopAction }
                close(reason)
                action?.invoke()
            }
        }

        override fun close() {
            val open: List<Client>
            val live: List<WatchTarget>
            synchronized(lock) {
                if (closed) return
                closed = true
                scope.cancel()
                open = clients.values.toList()
                open.forEach { it.closed = true }
                clients.clear()
                live = watches.values.toList()
                watches.clear()
                tokens.values.forEach { it.second.cancel() }
                tokens.clear()
                jobs.clear()
                events.clear()
                queuedBytes = 0
            }
            live.forEach { it.requestStop(MailWatch.REASON_EXIT) }
            runBlocking(Dispatchers.IO) { withTimeoutOrNull(3_000L) { open.forEach { runCatching { it.session.close() } } } }
        }
    }

    companion object {
        val methods = setOf("open", "start", "poll", "cancel", "answer", "close")
        val clientMethods = setOf("test", "send", "folders", "folderStatus", "createFolder", "deleteFolder", "renameFolder",
            "fetch", "search", "get", "fetchBody", "download", "raw", "setFlags", "markRead", "markUnread", "flag",
            "unflag", "move", "copy", "delete", "expunge", "append", "watch", "close")
        val operations = setOf("connect", "providers.list", "providers.get", "providers.resolve", "accounts.list", "watch.stop") +
            clientMethods.map { "client." + it }
        private fun JSONArray.json(index: Int): JsonElement? =
            if (isNull(index)) null else JsonParser.parseString(NodeBridgeJson.valueToJson(get(index)))
        private fun uids(result: Any?): Any = (result as? Map<*, *>)?.get("uids") ?: emptyList<Any?>()
        private fun failure(code: String, message: String) =
            MailPluginException(MailErrorMapper.normalize(code, message, retryable = false))
        private fun closedError() = failure(MailErrorCodes.SESSION_CLOSED, "Mail handle is closed or belongs to another client")
        private fun errorValue(error: Throwable) = errorValue(MailErrorMapper.fromThrowable(error, MailErrorMapper.Phase.CALL))
        private fun errorValue(error: MailError) = mapOf("code" to error.code, "message" to error.message,
            "retryable" to error.retryable, "details" to error.details)
    }
}
