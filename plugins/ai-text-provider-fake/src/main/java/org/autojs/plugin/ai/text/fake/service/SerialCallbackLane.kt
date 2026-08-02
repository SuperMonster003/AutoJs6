package org.autojs.plugin.ai.text.fake.service

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import org.autojs.plugin.ai.text.fake.FakeBoundedExecutorFactory

internal class SerialCallbackLane : Closeable {
    private val executor: ExecutorService = FakeBoundedExecutorFactory.callbacks()

    fun dispatch(callback: () -> Unit, onFailure: (Throwable) -> Unit) {
        try {
            executor.execute {
                runCatching(callback).onFailure(onFailure)
            }
        } catch (error: RejectedExecutionException) {
            onFailure(error)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
