package org.autojs.autojs.core.plugin.hostbroker

import org.autojs.autojs.engine.NodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeError
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded dispatch lifetime independent of Binder; late and duplicate replies are discarded. */
internal class HostCapabilityDispatchQueue(
    private val provider: NodeBridgeCapabilityProvider,
    private val maxConcurrentCalls: Int,
    private val onDiscardedResponse: (NodeBridgeResponse) -> Unit = {},
) {
    private val lock = Any()
    private var closed = false
    private val pending = LinkedHashMap<String, Pending>()
    private val worker = ThreadPoolExecutor(
        maxConcurrentCalls, maxConcurrentCalls, 30L, TimeUnit.SECONDS,
        ArrayBlockingQueue(maxConcurrentCalls),
        { task -> Thread(task, "host-capability").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }
    private val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "host-capability-timeouts").apply { isDaemon = true }
    }

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val call = Pending(request, callback)
        val rejection = synchronized(lock) {
            when {
                closed -> NodeBridgeError.processDead()
                request.id in pending -> NodeBridgeError.invalidRequest("A bridge request with this id is already pending.")
                pending.size >= maxConcurrentCalls -> NodeBridgeError.resourceLimit("The host capability grant concurrency limit was reached.")
                else -> {
                    pending[request.id] = call
                    null
                }
            }
        }
        if (rejection != null) {
            callback(NodeBridgeResponse.failure(request.id, rejection))
            return
        }
        try {
            call.timeout = timer.schedule({
                call.complete(NodeBridgeResponse.failure(request.id, NodeBridgeError.timeout(request.module, request.method)))
                call.work?.cancel(true)
            }, request.timeoutMs, TimeUnit.MILLISECONDS)
            call.work = worker.submit {
                if (call.finished.get()) return@submit
                try {
                    provider.dispatch(request, call::complete)
                } catch (error: Exception) {
                    call.complete(NodeBridgeResponse.failure(request.id, NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.simpleName,
                        module = request.module, method = request.method,
                    )))
                }
            }
            if (call.finished.get()) call.work?.cancel(true)
        } catch (_: RejectedExecutionException) {
            call.complete(NodeBridgeResponse.failure(request.id,
                if (synchronized(lock) { closed }) NodeBridgeError.processDead()
                else NodeBridgeError.resourceLimit("The host capability worker limit was reached.")))
        }
    }

    fun destroy(reason: NodeBridgeError) {
        val abandoned = synchronized(lock) {
            if (closed) return
            closed = true
            pending.values.toList()
        }
        abandoned.forEach { call ->
            call.complete(NodeBridgeResponse.failure(call.request.id, reason))
            call.work?.cancel(true)
        }
        worker.shutdownNow()
        timer.shutdownNow()
        provider.destroy(reason)
    }

    private inner class Pending(val request: NodeBridgeRequest, val callback: (NodeBridgeResponse) -> Unit) {
        val finished = AtomicBoolean(false)
        @Volatile var work: Future<*>? = null
        @Volatile var timeout: ScheduledFuture<*>? = null

        fun complete(response: NodeBridgeResponse) {
            if (response.event || !finished.compareAndSet(false, true)) {
                runCatching { onDiscardedResponse(response) }
                return
            }
            timeout?.cancel(false)
            synchronized(lock) { pending.remove(request.id, this) }
            // A dead or hostile callback must not prevent cleanup of other pending requests.
            runCatching { callback(response) }
        }
    }
}
