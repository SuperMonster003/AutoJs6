package org.autojs.autojs.core.automator.event

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A wake-up latch between an event thread and a waiting thread (roadmap P5.2). [signal] releases
 * the current [await]; a signal that arrives while nobody waits is kept until the next [await]
 * consumes it, so an event between two checks is never lost. Android-free.
 * zh-CN: 事件线程与等待线程之间的唤醒闩 (Roadmap P5.2). [signal] 释放当前的 [await]; 无人等待时到达的信号保留到下一次 [await]
 * 消费, 两次检查之间的事件不会丢失. 不依赖 Android.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class EventGate {

    private val lock = ReentrantLock()

    private val arrived = lock.newCondition()

    private var pending = false

    /** Whether a signal is waiting to be consumed. zh-CN: 是否有尚未消费的信号. */
    val isPending: Boolean get() = lock.withLock { pending }

    /** Releases the current wait, or the next one. zh-CN: 释放当前的等待, 或下一次等待. */
    fun signal() = lock.withLock {
        pending = true
        arrived.signalAll()
    }

    /** Drops a pending signal. zh-CN: 丢弃尚未消费的信号. */
    fun clear() = lock.withLock { pending = false }

    /**
     * Waits at most [millis] milliseconds for a signal (a pending one returns at once) and tells
     * whether one arrived; a non-positive [millis] only consumes a pending signal.
     * zh-CN: 至多等待 [millis] 毫秒的信号 (已有的信号立即返回), 返回是否等到; [millis] 不为正时只消费已有的信号.
     */
    @Throws(InterruptedException::class)
    fun await(millis: Long): Boolean = lock.withLock {
        var left = TimeUnit.MILLISECONDS.toNanos(millis)
        while (!pending && left > 0) {
            left = arrived.awaitNanos(left)
        }
        val arrived = pending
        pending = false
        arrived
    }

}
