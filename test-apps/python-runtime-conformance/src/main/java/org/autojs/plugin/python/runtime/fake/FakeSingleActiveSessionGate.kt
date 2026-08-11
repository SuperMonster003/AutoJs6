package org.autojs.plugin.python.runtime.fake

import java.util.concurrent.atomic.AtomicReference

/** Process-wide identity gate: one active session, no provider-side queue. */
class FakeSingleActiveSessionGate<T : Any> {
    private val active = AtomicReference<T?>()

    fun tryAcquire(session: T): Boolean = active.compareAndSet(null, session)

    fun release(session: T): Boolean = active.compareAndSet(session, null)

    fun current(): T? = active.get()
}
