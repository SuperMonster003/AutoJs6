package org.autojs.plugin.mcp.server.fake.service

import android.util.Log
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One thread for every oneway callback, so the order the scenario plans is the order the host sees. */
internal class SerialCallbackLane : Closeable {

    private val executor: ExecutorService = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_PENDING_CALLBACKS),
        { runnable -> Thread(runnable, "mcp-fake-callback").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun dispatch(label: String, callback: () -> Unit) {
        try {
            executor.execute {
                runCatching(callback).onFailure { error ->
                    Log.w(TAG, "Fixture callback failed: $label", error)
                }
            }
        } catch (error: RejectedExecutionException) {
            Log.w(TAG, "Fixture callback rejected: $label", error)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "McpFakeCallbackLane"
        const val MAX_PENDING_CALLBACKS = 64
    }
}
