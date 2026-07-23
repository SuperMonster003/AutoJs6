package org.autojs.autojs.core.plugin

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class AidlDedicatedBindingLease(
    private val onUnbind: () -> Unit,
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val unbindClaimed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    fun markRegistered() {
        registered.set(true)
        unbindIfReady()
    }

    override fun close() {
        closed.set(true)
        unbindIfReady()
    }

    private fun unbindIfReady() {
        if (closed.get() && registered.get() && unbindClaimed.compareAndSet(false, true)) {
            onUnbind()
        }
    }
}
