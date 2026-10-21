package org.autojs.autojs.engine

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import org.autojs.autojs.core.automator.flow.FlowError
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.FutureTask
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Interruptible toolkit jobs, scoped to a runtime (and to each worker's facade). */
internal class RuntimeFlowBridge(private val context: Context) : Closeable {
    companion object { val methods = setOf("open", "start", "poll", "cancel", "answer", "close") }
    private val owners = LinkedHashMap<String, Owner>()
    private var closed = false
    private val executor = ThreadPoolExecutor(0, 128, 10, TimeUnit.SECONDS, SynchronousQueue()) { run ->
        Thread(run, "portable-flow").apply { isDaemon = true }
    }

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        try {
            val args = JSONArray(request.argsJson)
            val result: Any? = if (request.method == "open") synchronized(owners) {
                require(args.length() == 0)
                check(!closed) { "Flow runtime closed" }
                require(owners.size < 128) { "Too many Flow clients" }
                val id = UUID.randomUUID().toString()
                owners[id] = Owner()
                mapOf("id" to id)
            } else {
                val id = args.getString(0)
                if (request.method == "close") closeClient(id) else {
                    val owner = synchronized(owners) { owners[id] } ?: error("Flow client closed")
                    when (request.method) {
                        "start" -> owner.start(args.getString(1), args.getString(2), args.getJSONArray(3), args.getJSONObject(4), args.opt(5))
                        "poll" -> owner.poll(args.getJSONArray(1))
                        "cancel" -> owner.cancel(args.getString(1))
                        "answer" -> owner.answer(args.getString(1), args.getString(2), args.getBoolean(3), args.opt(4))
                        else -> throw IllegalArgumentException("Unknown Flow bridge method")
                    }
                }
            }
            reply(NodeBridgeResponse.success(request.id, RuntimeFlowJson.encode(result).toString()))
        } catch (error: Throwable) {
            reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(
                code = if (error is IllegalArgumentException) "ERR_AUTOJS6_INVALID_REQUEST" else "ERR_AUTOJS6_FLOW",
                message = error.message ?: "Flow bridge failed", module = "flow", method = request.method,
            )))
        }
    }

    fun closeClient(id: String): Boolean {
        val owner = synchronized(owners) { owners.remove(id) } ?: return false
        owner.close()
        return true
    }
    override fun close() {
        val all = synchronized(owners) { closed = true; owners.keys.toList() }
        all.forEach(::closeClient)
        executor.shutdownNow()
    }

    internal class Work(
        val method: String, timeout: Long,
        private val emit: (String, Any?) -> Unit,
        private val callback: (String, List<Any?>, Work) -> Any?,
    ) {
        private val began = SystemClock.uptimeMillis()
        private val deadline = if (timeout == Long.MAX_VALUE) Long.MAX_VALUE else began + timeout
        @Volatile var cancelled = false
        fun check() {
            if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedException("Flow cancelled")
            if (deadline != Long.MAX_VALUE && SystemClock.uptimeMillis() > deadline) throw timeout()
        }
        fun timeout() = FlowError.Timeout(method, null, SystemClock.uptimeMillis() - began, 0, "operation")
        fun remaining(): Long { check(); return if (deadline == Long.MAX_VALUE) Long.MAX_VALUE else (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0) }
        fun sleep(ms: Long) { check(); Thread.sleep(minOf(ms, remaining())); check() }
        fun invoke(reference: Any?, args: List<Any?> = emptyList()): Any? {
            check()
            val id = RuntimeFlowJson.callback(reference) ?: throw IllegalArgumentException("Expected a Flow callback")
            return callback(id, args, this).also { check() }
        }
        fun ready() { check(); emit("ready", null) }
    }

    private class Packet(val id: String, val bytes: ByteArray, val terminal: Boolean, var offset: Int = 0)
    private class Job(val work: Work) { lateinit var future: FutureTask<Unit> }
    private class Answer(val operation: String, val value: CompletableFuture<Any?> = CompletableFuture())
    private inner class Owner : Closeable {
        private val lock = Any()
        private var ended = false
        private val jobs = LinkedHashMap<String, Job>()
        private val cancelledStarts = LinkedHashSet<String>()
        private val answers = LinkedHashMap<String, Answer>()
        private val packets = ArrayDeque<Packet>()
        private var queued = 0L

        fun start(id: String, method: String, args: JSONArray, options: JSONObject, budget: Any?): Boolean = synchronized(lock) {
            check(!ended) { "Flow client closed" }
            require(id.length in 1..128 && id !in jobs) { "Invalid or duplicate Flow operation ID" }
            if (cancelledStarts.remove(id)) return false
            require(jobs.size < 64) { "Too many pending Flow operations" }
            val timeout = RuntimeFlowJson.millis(budget, 10000, "timeout")
            val work = Work(method, timeout, { kind, value -> emit(id, kind, value) }, { cb, values, scope -> callback(id, cb, values, scope) })
            val job = Job(work)
            job.future = FutureTask {
                try {
                    val result = RuntimeFlowToolkit(context, work).use { it.run(method, args, options) }
                    work.check()
                    emit(id, "resolve", result)
                } catch (_: InterruptedException) {
                    if (!work.cancelled) emit(id, "reject", RuntimeFlowJson.error(FlowError.Cancelled(method)))
                } catch (error: Throwable) {
                    if (!work.cancelled) emit(id, "reject", RuntimeFlowJson.error(error))
                } finally {
                    synchronized(lock) {
                        answers.filterValues { it.operation == id }.keys.toList().forEach { key -> answers.remove(key)?.value?.cancel(true) }
                        if (work.cancelled || ended) { jobs.remove(id); discard(id) }
                    }
                }
            }
            jobs[id] = job
            try { executor.execute(job.future) } catch (error: Throwable) { jobs.remove(id); throw error }
            true
        }
        private fun emit(id: String, kind: String, value: Any?) = synchronized(lock) {
            if (ended || jobs[id]?.work?.cancelled != false) return@synchronized
            val terminal = kind == "resolve" || kind == "reject"
            val bytes = JSONObject().put("type", kind).put("value", RuntimeFlowJson.encode(value)).toString().toByteArray(Charsets.UTF_8)
            if (kind != "reject" && queued + bytes.size > 64L * 1024 * 1024) {
                discard(id)
                throw IllegalStateException("Flow result queue limit exceeded")
            }
            packets.add(Packet(id, bytes, terminal)); queued += bytes.size
        }
        fun poll(ids: JSONArray): List<Map<String, Any>> = synchronized(lock) {
            check(!ended) { "Flow client closed" }
            require(ids.length() <= 64)
            val selected = RuntimeFlowJson.list(ids).map { it as String }.toSet()
            val result = ArrayList<Map<String, Any>>()
            val iterator = packets.iterator()
            while (iterator.hasNext() && result.size < 3) {
                val p = iterator.next()
                if (p.id !in selected) continue
                val length = minOf(12 * 1024, p.bytes.size - p.offset)
                val encoded = Base64.encodeToString(p.bytes, p.offset, length, Base64.NO_WRAP)
                p.offset += length; queued -= length
                val last = p.offset == p.bytes.size
                result += mapOf("id" to p.id, "data" to encoded, "last" to last)
                if (last) { iterator.remove(); if (p.terminal) jobs.remove(p.id) }
            }
            result
        }
        private fun callback(id: String, reference: String, values: List<Any?>, work: Work): Any? {
            val token = UUID.randomUUID().toString()
            val answer = Answer(id)
            synchronized(lock) { work.check(); answers[token] = answer }
            try {
                emit(id, "callback", mapOf("token" to token, "callback" to reference, "args" to values))
                return try { answer.value.get(work.remaining(), TimeUnit.MILLISECONDS) }
                catch (_: TimeoutException) { throw work.timeout() }
                catch (error: java.util.concurrent.ExecutionException) { throw error.cause ?: error }
            } finally { synchronized(lock) { answers.remove(token) } }
        }
        fun answer(id: String, token: String, ok: Boolean, value: Any?): Boolean = synchronized(lock) {
            val pending = answers[token]?.takeIf { it.operation == id } ?: return false
            if (ok) pending.value.complete(RuntimeFlowJson.nullable(value)) else pending.value.completeExceptionally(RuntimeFlowCallbackError(token))
        }
        fun cancel(id: String): Boolean = synchronized(lock) {
            require(id.length in 1..128)
            val job = jobs.remove(id)
            if (job == null) rememberCancelledStart(id) else { job.work.cancelled = true; job.future.cancel(true) }
            answers.filterValues { it.operation == id }.keys.toList().forEach { answers.remove(it)?.value?.cancel(true) }
            discard(id)
            true
        }
        private fun rememberCancelledStart(id: String) {
            // Never forget cancellation IDs while this client can still receive delayed starts.
            if (cancelledStarts.size >= 4096 && id !in cancelledStarts) {
                ended = true
                jobs.values.forEach { it.work.cancelled = true; it.future.cancel(true) }
                jobs.clear()
                answers.values.forEach { it.value.cancel(true) }; answers.clear()
                packets.clear(); queued = 0
                throw IllegalStateException("Too many abandoned Flow starts")
            }
            cancelledStarts += id
        }
        private fun discard(id: String) {
            val iterator = packets.iterator()
            while (iterator.hasNext()) { val packet = iterator.next(); if (packet.id == id) { queued -= packet.bytes.size - packet.offset; iterator.remove() } }
        }
        override fun close() = synchronized(lock) {
            ended = true
            jobs.values.forEach { it.work.cancelled = true; it.future.cancel(true) }; jobs.clear()
            answers.values.forEach { it.value.cancel(true) }
            packets.clear(); answers.clear(); cancelledStarts.clear(); queued = 0
        }
    }
}
