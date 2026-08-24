package org.autojs.plugin.ai.provider.fake

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object FakeBoundedExecutorFactory {
    const val WORKER_CORE_THREADS = 2
    const val WORKER_MAX_THREADS = 4
    const val WORKER_QUEUE_CAPACITY = 64
    const val CALLBACK_QUEUE_CAPACITY = 128

    fun worker(): ThreadPoolExecutor = create(
        coreThreads = WORKER_CORE_THREADS,
        maximumThreads = WORKER_MAX_THREADS,
        queueCapacity = WORKER_QUEUE_CAPACITY,
        keepAliveSeconds = 30L,
        threadName = "ai-provider-fake-worker",
    ).apply { allowCoreThreadTimeOut(true) }

    fun callbacks(): ThreadPoolExecutor = create(
        coreThreads = 1,
        maximumThreads = 1,
        queueCapacity = CALLBACK_QUEUE_CAPACITY,
        keepAliveSeconds = 0L,
        threadName = "ai-provider-fake-callback",
    )

    fun create(
        coreThreads: Int,
        maximumThreads: Int,
        queueCapacity: Int,
        keepAliveSeconds: Long,
        threadName: String,
    ): ThreadPoolExecutor {
        require(coreThreads > 0)
        require(maximumThreads >= coreThreads)
        require(queueCapacity > 0)
        require(keepAliveSeconds >= 0L)
        require(threadName.isNotBlank())
        return ThreadPoolExecutor(
            coreThreads,
            maximumThreads,
            keepAliveSeconds,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(queueCapacity),
            { runnable -> Thread(runnable, threadName).apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
    }
}
