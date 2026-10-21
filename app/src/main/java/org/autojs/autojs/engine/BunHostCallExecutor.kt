package org.autojs.autojs.engine

import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A oneway Binder entry returns before a UI/event wait can hold up the next request. */
internal class BunHostCallExecutor : Closeable {
    private val capacity = BunRuntimeContract.MAX_CONCURRENT_HOST_CALLS
    private val executor = ThreadPoolExecutor(capacity, capacity, 10, TimeUnit.SECONDS, ArrayBlockingQueue(capacity)) { task ->
        Thread(task, "BunHostCall").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    fun submit(action: () -> Unit): Boolean = try {
        executor.execute { if (!executor.isShutdown) action() }
        true
    } catch (_: RejectedExecutionException) { false }

    override fun close() { executor.shutdownNow() }
}
