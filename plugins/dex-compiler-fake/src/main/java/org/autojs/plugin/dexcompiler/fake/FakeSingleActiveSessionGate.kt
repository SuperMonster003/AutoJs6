package org.autojs.plugin.dexcompiler.fake

import java.util.concurrent.atomic.AtomicReference

/** Process-scoped, identity-based ownership for the fixture's single advertised session. */
internal class FakeSingleActiveSessionGate<T : Any> {
    private val active = AtomicReference<T?>()

    fun tryAcquire(session: T): Boolean = active.compareAndSet(null, session)

    fun release(session: T): Boolean = active.compareAndSet(session, null)
}
