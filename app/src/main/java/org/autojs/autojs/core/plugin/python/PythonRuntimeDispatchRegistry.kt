package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonExecutionSessionPolicy
import org.autojs.plugin.python.runtime.api.PythonRequestId
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/** Pure ownership gate around the asynchronous admission and one-way start boundary. */
internal class PythonRuntimeDispatchRegistry<T : Any> {

    class Reservation internal constructor(
        val requestId: PythonRequestId,
        internal val token: Long,
        internal val plan: PythonRuntimeHostRequestPlan,
    )

    sealed interface Opened<out T : Any> {
        data class Owned<T : Any>(
            val session: T,
            val policy: PythonExecutionSessionPolicy,
        ) : Opened<T>

        /** Every listed handle is caller-owned and must be best-effort closed. */
        class CloseLate<T : Any>(sessions: Collection<T>) : Opened<T> {
            val sessions: List<T> = Collections.unmodifiableList(ArrayList(sessions))
        }
    }

    sealed interface Start<out T : Any> {
        data class Dispatch<T : Any>(
            val session: T,
            val policy: PythonExecutionSessionPolicy,
        ) : Start<T>

        data object Rejected : Start<Nothing>
    }

    sealed interface Cancellation<out T : Any> {
        data object Unknown : Cancellation<Nothing>
        data object BeforeOpened : Cancellation<Nothing>
        data class BeforeStart<T : Any>(val session: T) : Cancellation<T>
        data class AfterStart<T : Any>(val session: T) : Cancellation<T>
    }

    enum class ReplayDisposition {
        SAFE_BEFORE_START,
        FORBIDDEN,
    }

    private data class Registration<T : Any>(
        val reservation: Reservation,
        var session: T? = null,
        var policy: PythonExecutionSessionPolicy? = null,
        var cancelled: Boolean = false,
        var startClaimed: Boolean = false,
    )

    private val lock = Any()
    private val nextToken = AtomicLong(0L)
    private val registrations = LinkedHashMap<PythonRequestId, Registration<T>>()

    fun reserve(plan: PythonRuntimeHostRequestPlan): Reservation? {
        val requestId = plan.request.requestId
        val reservation = Reservation(requestId, nextToken.incrementAndGet(), plan)
        return synchronized(lock) {
            if (registrations.containsKey(requestId)) {
                null
            } else {
                registrations[requestId] = Registration(reservation)
                reservation
            }
        }
    }

    fun acceptOpened(reservation: Reservation, session: T): Opened<T> = synchronized(lock) {
        val registration = registrations[reservation.requestId]
        if (registration?.reservation?.token != reservation.token || registration.cancelled) {
            return@synchronized Opened.CloseLate(listOf(session))
        }
        if (registration.session != null) {
            reservation.plan.admissionPlan.onOpened()
            val existing = checkNotNull(registration.session)
            registration.cancelled = true
            return@synchronized Opened.CloseLate(listOf(existing, session))
        }
        if (!reservation.plan.admissionPlan.onOpened()) {
            registration.cancelled = true
            return@synchronized Opened.CloseLate(listOf(session))
        }
        val policy = checkNotNull(reservation.plan.admissionPlan.sessionPolicy)
        registration.session = session
        registration.policy = policy
        Opened.Owned(session, policy)
    }

    /** Atomically forbids replay before the adapter performs the one-way `start()` call. */
    fun beginStart(reservation: Reservation): Start<T> = synchronized(lock) {
        val registration = registrations[reservation.requestId]
        if (registration?.reservation?.token != reservation.token ||
            registration.cancelled || registration.startClaimed
        ) {
            return@synchronized Start.Rejected
        }
        val session = registration.session ?: return@synchronized Start.Rejected
        val policy = registration.policy ?: return@synchronized Start.Rejected
        if (!policy.requestStart()) return@synchronized Start.Rejected
        registration.startClaimed = true
        Start.Dispatch(session, policy)
    }

    fun cancel(requestId: PythonRequestId): Cancellation<T> = synchronized(lock) {
        val registration = registrations[requestId] ?: return@synchronized Cancellation.Unknown
        if (registration.cancelled) return@synchronized Cancellation.Unknown
        registration.cancelled = true
        val session = registration.session
        val policy = registration.policy
        if (session == null || policy == null) {
            registration.reservation.plan.admissionPlan.abandonAdmission()
            Cancellation.BeforeOpened
        } else {
            policy.requestCancel()
            if (registration.startClaimed) Cancellation.AfterStart(session) else Cancellation.BeforeStart(session)
        }
    }

    fun replayDisposition(reservation: Reservation): ReplayDisposition = synchronized(lock) {
        val registration = registrations[reservation.requestId]
        if (registration?.reservation?.token == reservation.token &&
            !registration.cancelled && !registration.startClaimed
        ) {
            ReplayDisposition.SAFE_BEFORE_START
        } else {
            ReplayDisposition.FORBIDDEN
        }
    }

    /** Releases registry state and returns the Binder handle the adapter must close, if one was owned. */
    fun release(reservation: Reservation): T? = synchronized(lock) {
        val current = registrations[reservation.requestId]
        if (current?.reservation?.token != reservation.token) return@synchronized null
        current.cancelled = true
        current.policy?.close() ?: reservation.plan.admissionPlan.abandonAdmission()
        registrations.remove(reservation.requestId)
        current.session
    }
}
