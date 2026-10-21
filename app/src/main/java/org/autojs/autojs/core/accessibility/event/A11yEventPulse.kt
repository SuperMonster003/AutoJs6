package org.autojs.autojs.core.accessibility.event

import android.os.SystemClock
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.ScriptOwned
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry
import org.autojs.autojs.core.automator.event.EventGate
import org.autojs.autojs.core.automator.event.EventWaitAborted
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
 * is dropped by [close] when the script exits. The service going away (roadmap P5.5) aborts the
 * pending waits with [EventWaitAborted] rather than letting them time out.
 * zh-CN: 一个脚本眼中的无障碍事件流 (Roadmap P5.2): 在 [AccessibilityService.eventBus] 上的一个观察者优先级订阅 (首次使用时建立,
 * 全部事件类型, 不消费) 喂给一个 [IdleWaiter] (`waitForIdle`), `waitForEvent` 注册的各 [EventWaiter], 以及一个让事件辅助轮询的休眠
 * 提前结束的 [EventGate]. 处理器在服务主线程上运行且不做脚本工作: 记录空闲检测的时间戳, 有等待者接受该类型时复制一次事件, 再触发闩.
 * 脚本退出时由 [close] 取消订阅.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class A11yEventPulse(
    private val bus: A11yEventBus<A11yEvent>,
    /** Watched for the service going away, which aborts the pending waits (roadmap P5.5); `null` never aborts. zh-CN: 监视服务的消失, 届时中止待定的等待 (Roadmap P5.5); `null` 则从不中止. */
    private val registry: A11yServiceRegistry<AccessibilityService>? = null,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
) : AutoCloseable, ScriptOwned {

    private class Registered(val types: Set<Int>?, val waiter: EventWaiter<A11yEvent>) {
        fun accepts(type: Int) = types == null || type in types
    }

    private val gate = EventGate()

    /** Idle detection over the events seen since the subscription. zh-CN: 订阅以来的事件之上的空闲检测. */
    val idle = IdleWaiter(clock)

    private val waiters = CopyOnWriteArrayList<Registered>()

    /** Waiters fed by other sources (toasts, notifications), aborted together with [waiters]. zh-CN: 由其它来源喂事件的等待者 (toast, 通知), 与 [waiters] 一同中止. */
    private val abortables = CopyOnWriteArrayList<EventWaiter<*>>()

    private val serviceWatch = A11yServiceRegistry.StateListener<AccessibilityService> { previous, current ->
        if (previous.isBound && !current.isBound) onServiceLost()
    }

    private val lock = Any()

    /** The owner id of the script runtime whose bridge made this pulse (roadmap P4.2). zh-CN: 创建本脉冲的 bridge 所属脚本运行时的 owner id (ROADMAP P4.2). */
    @Volatile
    override var scriptOwnerId: String? = null

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
            registry?.addListener(this, serviceWatch)
        }
    }

    /**
     * The bound service instance went away: the pending waits can neither succeed nor honestly
     * time out, so they end with [EventWaitAborted] (the JS surface maps it to `A11Y_UNAVAILABLE`),
     * and an event-assisted poll is woken so that its next probe reports the outage.
     * zh-CN: 绑定的服务实例消失: 待定的等待既不能成功也不能诚实地超时, 因此以 [EventWaitAborted] 结束 (JS 表面映射为
     * `A11Y_UNAVAILABLE`), 事件辅助的轮询被唤醒, 使其下一次探测报告中断.
     */
    private fun onServiceLost() {
        val cause = EventWaitAborted("The accessibility service was unbound while waiting for its events")
        waiters.forEach { it.waiter.abort(cause) }
        abortables.forEach { it.abort(cause) }
        idle.abort(cause)
        gate.signal()
    }

    /**
     * Runs [block] while [waiter], fed by another source (the notification observer), is aborted
     * along with the pulse's own waiters should the service go away.
     * zh-CN: 运行 [block], 期间由其它来源 (通知观察者) 喂事件的 [waiter] 在服务消失时与本脉冲自己的等待者一同被中止.
     */
    fun <T> abortingOnServiceLoss(waiter: EventWaiter<*>, block: () -> T): T {
        ensureSubscribed()
        abortables += waiter
        try {
            return block()
        } finally {
            abortables -= waiter
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
        registry?.removeListener(this)
        waiters.clear()
        abortables.clear()
    }

    companion object {

        private fun sleepOrInterrupt(millis: Long) = try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            throw ScriptInterruptedException()
        }

    }

}
