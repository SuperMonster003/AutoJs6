package org.autojs.autojs.core.accessibility.event

import android.os.SystemClock
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.automator.event.EventGate
import org.autojs.autojs.core.automator.event.EventWaiter
import org.autojs.autojs.core.automator.event.IdleWaiter
import org.autojs.autojs.core.automator.query.Poller
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One script's view of the accessibility event stream (roadmap P5.2): a single observer-priority
 * subscription on [AccessibilityService.eventBus] (made on first use, all event types, never
 * consuming) feeds an [IdleWaiter] (`waitForIdle`), the [EventWaiter]s registered by
 * `waitForEvent`, and an [EventGate] that cuts the sleeps of event-assisted polling short. The
 * handler runs on the service's main thread and does no script work: it stamps the idle waiter,
 * copies the event once when a waiter accepts its type, and signals the gate. The subscription
 * is dropped by [close] when the script exits.
 * zh-CN: 一个脚本眼中的无障碍事件流 (Roadmap P5.2): 在 [AccessibilityService.eventBus] 上的一个观察者优先级订阅 (首次使用时建立,
 * 全部事件类型, 不消费) 喂给一个 [IdleWaiter] (`waitForIdle`), `waitForEvent` 注册的各 [EventWaiter], 以及一个让事件辅助轮询的休眠
 * 提前结束的 [EventGate]. 处理器在服务主线程上运行且不做脚本工作: 记录空闲检测的时间戳, 有等待者接受该类型时复制一次事件, 再触发闩.
 * 脚本退出时由 [close] 取消订阅.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class A11yEventPulse(
    private val bus: A11yEventBus<A11yEvent>,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
) : AutoCloseable {

    private class Registered(val types: Set<Int>?, val waiter: EventWaiter<A11yEvent>) {
        fun accepts(type: Int) = types == null || type in types
    }

    private val gate = EventGate()

    /** Idle detection over the events seen since the subscription. zh-CN: 订阅以来的事件之上的空闲检测. */
    val idle = IdleWaiter(clock)

    private val waiters = CopyOnWriteArrayList<Registered>()

    private val lock = Any()

    private var subscription: A11yEventBus.Subscription<A11yEvent>? = null

    /** Events observed since the subscription. zh-CN: 订阅以来观察到的事件数. */
    @Volatile
    var events: Long = 0
        private set

    /** The poller of event-assisted selector queries. zh-CN: 事件辅助的选择器查询所用的轮询器. */
    val poller: Poller = Poller(clock, ::sleepOrInterrupt) { millis -> awaitEventOrInterrupt(millis) }

    /** Subscribes on first use. zh-CN: 首次使用时订阅. */
    fun ensureSubscribed() = synchronized(lock) {
        if (subscription == null) {
            subscription = bus.subscribe(owner = this, types = null, priority = AccessibilityService.PRIORITY_OBSERVER) { event ->
                onEvent(event)
                false
            }
        }
    }

    private fun onEvent(event: A11yEvent) {
        events += 1
        idle.onEvent(event.type)
        val accepting = waiters.filter { it.accepts(event.type) }
        if (accepting.isNotEmpty()) {
            val kept = event.copied()
            accepting.forEach { it.waiter.offer(kept) }
        }
        gate.signal()
    }

    /**
     * Runs [block] with a waiter that receives (copies of) the events of [types], all types when
     * `null`, for as long as [block] runs.
     * zh-CN: 以一个在 [block] 运行期间接收 [types] (为 `null` 时全部类型) 事件 (的副本) 的等待者运行 [block].
     */
    fun <T> withWaiter(types: Set<Int>?, block: (EventWaiter<A11yEvent>) -> T): T {
        ensureSubscribed()
        val registered = Registered(types, EventWaiter(clock))
        waiters += registered
        try {
            return block(registered.waiter)
        } finally {
            waiters -= registered
        }
    }

    /** `waitForIdle`: see [IdleWaiter.await]. zh-CN: `waitForIdle`: 见 [IdleWaiter.await]. */
    @Throws(InterruptedException::class)
    fun awaitIdle(quietFor: Long, timeout: Long, types: Set<Int>?): IdleWaiter.Result {
        ensureSubscribed()
        return idle.await(quietFor, timeout, types)
    }

    /** Waits at most [millis] milliseconds for the next event; whether one arrived. zh-CN: 至多等待 [millis] 毫秒的下一个事件; 返回是否等到. */
    @Throws(InterruptedException::class)
    fun awaitEvent(millis: Long): Boolean {
        ensureSubscribed()
        return gate.await(millis)
    }

    /** [awaitEvent] on the script thread: an interruption ends the script. zh-CN: 脚本线程上的 [awaitEvent]: 中断即结束脚本. */
    fun awaitEventOrInterrupt(millis: Long): Boolean = try {
        awaitEvent(millis)
    } catch (_: InterruptedException) {
        throw ScriptInterruptedException()
    }

    override fun close() = synchronized(lock) {
        subscription?.let { bus.unsubscribe(it) }
        subscription = null
        waiters.clear()
    }

    companion object {

        private fun sleepOrInterrupt(millis: Long) = try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            throw ScriptInterruptedException()
        }

    }

}
