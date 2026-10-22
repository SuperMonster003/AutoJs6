package org.autojs.autojs.engine

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local execution lifecycle gate. Unknown IDs are never materialized, while a
 * caller-owned reservation keeps a pre-dispatch cancellation tombstone bounded.
 */
internal class NodeRuntimePluginExecutionRegistry<T : Any> {

    class Reservation internal constructor(
        val executionId: String,
        internal val token: Long,
    )

    sealed interface Cancellation<out T : Any> {
        data object Unknown : Cancellation<Nothing>
        data object BeforeDispatch : Cancellation<Nothing>
        data class AfterDispatch<T : Any>(val value: T) : Cancellation<T>
    }

    private data class Registration<T : Any>(
        val reservation: Reservation,
        var cancelled: Boolean = false,
        var dispatched: T? = null,
    )

    private val lock = Any()
    private val nextToken = AtomicLong(0L)
    private val registrations = LinkedHashMap<String, Registration<T>>()

    /** Live registrations, for the bridge leak gate (docs/memory-phase2/ROADMAP.md T1 (h)). zh-CN: 存活的登记数, 供桥接泄漏门禁使用 (docs/memory-phase2/ROADMAP.md T1 (h)). */
    fun size(): Int = synchronized(lock) { registrations.size }

    fun reserve(executionId: String): Reservation? {
        if (executionId.isBlank()) return null
        val reservation = Reservation(executionId, nextToken.incrementAndGet())
        return synchronized(lock) {
            if (registrations.containsKey(executionId)) {
                null
            } else {
                registrations[executionId] = Registration(reservation)
                reservation
            }
        }
    }

    fun isActive(reservation: Reservation): Boolean = synchronized(lock) {
        registrations[reservation.executionId]?.reservation?.token == reservation.token
    }

    fun markDispatched(reservation: Reservation, value: T): Boolean = synchronized(lock) {
        val registration = registrations[reservation.executionId]
        if (registration?.reservation?.token != reservation.token || registration.cancelled) {
            false
        } else {
            registration.dispatched = value
            true
        }
    }

    fun cancel(executionId: String): Cancellation<T> = synchronized(lock) {
        val registration = registrations[executionId] ?: return@synchronized Cancellation.Unknown
        registration.cancelled = true
        registration.dispatched?.let { Cancellation.AfterDispatch(it) } ?: Cancellation.BeforeDispatch
    }

    fun dispatched(executionId: String): T? = synchronized(lock) {
        registrations[executionId]?.takeUnless { it.cancelled }?.dispatched
    }

    fun release(reservation: Reservation) {
        synchronized(lock) {
            val current = registrations[reservation.executionId]
            if (current?.reservation?.token == reservation.token) {
                registrations.remove(reservation.executionId)
            }
        }
    }
}
