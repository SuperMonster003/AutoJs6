package org.autojs.autojs.core.plugin

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns one Context.bindService registration and releases it exactly once.
 *
 * Cancellation can close a lease before bindService returns. In that race, the
 * eventual successful registration is released as soon as [markRegistered] runs.
 *
 * zh-CN: 持有一次 Context.bindService 注册并确保只释放一次.
 * 取消可能先于 bindService 返回; 此时后续成功注册会在 [markRegistered] 时立即释放.
 */
class ServiceBindingLease(
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
