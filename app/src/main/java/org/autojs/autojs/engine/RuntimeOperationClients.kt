package org.autojs.autojs.engine

import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID

/**
 * Execution-owned clients of a plugin family whose calls outlast one transport request (api-coverage P3.5 / P4.5: the gallery
 * and batch rename modules; the shape of [RuntimeEpubBridge]). Every transport request stays short: `open` makes a client,
 * `start` launches an operation in a cancellable coroutine, `poll` drains its events in fragments that fit the Node and Bun
 * JSON transports, `cancel` ends one operation and `close` ends the client. [S] is what one client owns; closing the client
 * closes it.
 *
 * zh-CN: 调用会超过一次传输请求时长的插件族的执行级客户端 (api-coverage P3.5 / P4.5: gallery 与 batch_rename 模块, 形态同 [RuntimeEpubBridge]). 每个传输请求都很短: open 建客户端,
 * start 在可取消协程里启动操作, poll 按 Node 与 Bun JSON 传输能容纳的分片取事件, cancel 结束一个操作, close 结束客户端. [S] 是一个客户端拥有的对象, 随客户端关闭.
 */
internal abstract class RuntimeOperationClients<S : Closeable>(private val module: String, private val errorPrefix: String) : Closeable {

    private val clients = LinkedHashMap<String, Client>()
    private var closed = false

    protected abstract val operations: Set<String>

    protected abstract fun newSession(): S

    /** The `{ code, message }` of a failure. zh-CN: 失败的 `{ code, message }`. */
    protected abstract fun errorOf(error: Throwable): Pair<String, String>

    protected abstract suspend fun invoke(client: Client, id: String, method: String, args: JSONArray): Any?

    /** An operation was cancelled: release what it created. zh-CN: 操作被取消: 释放它创建的对象. */
    protected open fun onCancel(client: Client, id: String) = Unit

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        try {
            val args = JSONArray(request.argsJson)
            val value: Any? = if (request.method == "open") {
                require(args.length() == 0) { "$module.open takes no arguments" }
                synchronized(clients) {
                    check(!closed) { "$module execution has ended" }
                    val id = UUID.randomUUID().toString()
                    clients[id] = Client()
                    mapOf("id" to id)
                }
            } else {
                val id = args.getString(0)
                if (request.method == "close") closeClient(id) else {
                    val client = synchronized(clients) { clients[id] } ?: throw IllegalStateException("$module client is closed or belongs to another execution")
                    when (request.method) {
                        "start" -> client.start(args.getString(1), args.getJSONArray(2))
                        "poll" -> client.poll(args.getJSONArray(1))
                        "cancel" -> client.cancel(args.getString(1))
                        else -> throw IllegalArgumentException("Unknown $module bridge method")
                    }
                }
            }
            reply(NodeBridgeResponse.success(request.id, NodeBridgeJson.valueToJson(JSONObject.wrap(value))))
        } catch (error: Throwable) {
            val (code, message) = errorOf(error)
            reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(code = "ERR_AUTOJS6_${errorPrefix}_$code", message = message, module = module, method = request.method)))
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

    private class Event(val id: String, val bytes: ByteArray, var offset: Int = 0)

    inner class Client : Closeable {
        val session: S = newSession()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val lock = Any()
        private val jobs = LinkedHashMap<String, Job>()
        private val events = ArrayDeque<Event>()
        private var ended = false

        fun start(method: String, args: JSONArray): Map<String, String> {
            require(method in operations) { "Unknown $module operation: $method" }
            val id = UUID.randomUUID().toString()
            synchronized(lock) {
                check(!ended) { "$module client has closed" }
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val result = invoke(this@Client, id, method, args)
                        currentCoroutineContext().ensureActive()
                        emit(id, "resolve", result)
                    } catch (error: Throwable) {
                        // A cancelled operation was settled by its caller; a plugin timeout is a failure of a live job.
                        if (currentCoroutineContext().isActive) {
                            val (code, message) = errorOf(error)
                            emit(id, "reject", mapOf("code" to code, "message" to message))
                        }
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

        fun cancel(id: String): Boolean {
            val job = synchronized(lock) { jobs[id] }
            job?.cancel()
            onCancel(this, id)
            synchronized(lock) { events.removeAll { it.id == id } }
            return job != null
        }

        fun emit(id: String, type: String, value: Any?) {
            val bytes = NodeBridgeJson.valueToJson(JSONObject().put("type", type).put("value", JSONObject.wrap(value))).toByteArray(Charsets.UTF_8)
            synchronized(lock) {
                if (!ended) events.addLast(Event(id, bytes))
            }
        }

        /** The undrained events of [id]. zh-CN: [id] 未取走的事件数. */
        fun pending(id: String): Int = synchronized(lock) { events.count { it.id == id } }

        fun poll(selected: JSONArray): JSONArray = synchronized(lock) {
            check(!ended) { "$module client has closed" }
            val ids = (0 until selected.length()).map { selected.getString(it) }.toSet()
            val result = JSONArray()
            val iterator = events.iterator()
            while (iterator.hasNext() && result.length() < MAX_FRAGMENTS) {
                val event = iterator.next()
                if (event.id !in ids) continue
                while (event.offset < event.bytes.size && result.length() < MAX_FRAGMENTS) {
                    val size = minOf(FRAGMENT_BYTES, event.bytes.size - event.offset)
                    val data = Base64.encodeToString(event.bytes, event.offset, size, Base64.NO_WRAP)
                    event.offset += size
                    result.put(JSONObject().put("id", event.id).put("data", data).put("last", event.offset == event.bytes.size))
                }
                if (event.offset == event.bytes.size) iterator.remove()
            }
            result
        }

        override fun close() {
            synchronized(lock) {
                if (ended) return
                ended = true
                scope.cancel()
                jobs.clear()
                events.clear()
            }
            session.close()
        }
    }

    companion object {
        val METHODS = setOf("open", "start", "poll", "cancel", "close")

        /** One operation or session keeps at most this many undrained events. zh-CN: 一个操作或会话最多保留这么多未取走的事件. */
        const val MAX_PENDING_EVENTS = 1024

        private const val MAX_FRAGMENTS = 3
        private const val FRAGMENT_BYTES = 12 * 1024

        fun JSONArray.json(index: Int): JsonElement? =
            if (isNull(index)) null else JsonParser.parseString(NodeBridgeJson.valueToJson(get(index)))
    }
}
