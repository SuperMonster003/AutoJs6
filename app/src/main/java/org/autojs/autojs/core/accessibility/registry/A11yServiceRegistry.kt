package org.autojs.autojs.core.accessibility.registry

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Single source of truth for the process-side state of the accessibility service:
 * which service instance is bound, whether it has become operational (received its first event),
 * and who wants to be told about transitions.
 *
 * The service type is a type parameter so that this class stays free of Android dependencies
 * and can be unit-tested. "Enabled in system settings" is a separate axis that cannot be observed
 * from inside the process and therefore lives in [org.autojs.autojs.core.accessibility.AccessibilityTool].
 *
 * zh-CN: 无障碍服务进程内状态的单一事实来源: 当前绑定的服务实例, 是否已可操作 (收到首个事件), 以及状态变更的监听者.
 * 服务类型为泛型参数, 使本类不依赖 Android 并可进行单元测试.
 * "系统设置中已启用" 无法在进程内观测, 因此保留在 `AccessibilityTool` 中.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
open class A11yServiceRegistry<S : Any>(
    private val onListenerError: (message: String, error: Throwable) -> Unit = { _, error -> error.printStackTrace() },
) {

    enum class State {
        /** No service instance is bound in this process. zh-CN: 进程内没有绑定的服务实例. */
        UNBOUND,

        /** A service instance is bound (connected) but has not received any event yet. zh-CN: 服务实例已绑定, 但尚未收到事件. */
        CONNECTED,

        /** The bound service instance has received at least one event. zh-CN: 已绑定的服务实例至少收到过一个事件. */
        OPERATIONAL,
    }

    /** Immutable view of the registry at one point in time. zh-CN: 注册表某一时刻的不可变快照. */
    data class Snapshot<S : Any>(
        val state: State,
        val instance: S?,
        /** Whether [instance] was adopted from its first event, `onServiceConnected` never having been seen (roadmap P5.5). zh-CN: [instance] 是否由其首个事件采纳, 而 `onServiceConnected` 从未到来 (Roadmap P5.5). */
        val adoptedByEvent: Boolean = false,
    ) {
        val isBound get() = state != State.UNBOUND
        val isOperational get() = state == State.OPERATIONAL
    }

    fun interface StateListener<S : Any> {
        fun onStateChanged(previous: Snapshot<S>, current: Snapshot<S>)
    }

    private val lock = ReentrantLock()
    private val stateChanged = lock.newCondition()

    private val listenersLock = Any()
    private val listeners = LinkedHashMap<Any, StateListener<S>>()

    @Volatile
    var snapshot: Snapshot<S> = Snapshot(State.UNBOUND, null)
        private set

    val state: State get() = snapshot.state

    val instance: S? get() = snapshot.instance

    val isBound: Boolean get() = snapshot.isBound

    val isOperational: Boolean get() = snapshot.isOperational

    /** Whether the bound instance was adopted from an event without `onServiceConnected` (the platform issue of roadmap P5.5). zh-CN: 已绑定实例是否由事件采纳而未经 `onServiceConnected` (Roadmap P5.5 所指的平台问题). */
    val adoptedByEvent: Boolean get() = snapshot.adoptedByEvent

    /**
     * Called from `onServiceConnected`. Operational state is reset because a freshly connected
     * instance has not delivered any event yet.
     * zh-CN: 由 `onServiceConnected` 调用. 新连接的实例尚未投递事件, 因此重置可操作态.
     */
    fun onConnected(service: S) = transition { Snapshot(State.CONNECTED, service) }

    /**
     * Called for every delivered event. Also adopts [service] as the bound instance, which covers
     * the platform quirk where `onServiceConnected` is never invoked although events flow.
     * zh-CN: 每个事件到达时调用. 同时把 [service] 采纳为绑定实例, 以覆盖 `onServiceConnected` 未回调但事件照常到达的平台问题.
     */
    fun onEvent(service: S) = transition { current ->
        when {
            current.state == State.OPERATIONAL && current.instance === service -> current
            current.instance === service -> current.copy(state = State.OPERATIONAL)
            else -> Snapshot(State.OPERATIONAL, service, adoptedByEvent = true)
        }
    }

    /**
     * Called when [service] is unbound or destroyed.
     * Returns whether it was the bound instance (a stale instance being disposed changes nothing).
     * zh-CN: 服务解绑或销毁时调用. 返回它是否为当前绑定实例 (过期实例的销毁不改变状态).
     */
    fun onDisposed(service: S): Boolean {
        var wasCurrent = false
        transition { current ->
            if (current.instance === service) {
                wasCurrent = true
                Snapshot(State.UNBOUND, null)
            } else {
                current
            }
        }
        return wasCurrent
    }

    /** Drops the operational flag while keeping the bound instance. zh-CN: 撤销可操作态, 保留绑定实例. */
    fun resetOperational() = transition { current ->
        if (current.state == State.OPERATIONAL) current.copy(state = State.CONNECTED) else current
    }

    /** Marks the bound instance operational without an event (compatibility path). zh-CN: 不经事件直接标记可操作 (兼容路径). */
    fun markOperational() = transition { current ->
        if (current.state == State.CONNECTED) current.copy(state = State.OPERATIONAL) else current
    }

    /**
     * Blocks until the state reaches [target] or better. A negative [timeoutMillis] waits forever.
     * Returns false on timeout or interruption; the interrupt flag is preserved so that callers
     * relying on interruption to stop a script still observe it.
     * zh-CN: 阻塞直到状态达到 [target] 或更高. [timeoutMillis] 为负表示无限等待.
     * 超时或被中断时返回 false, 中断标志会被保留, 以便依赖中断停止脚本的调用方仍能感知.
     */
    fun await(target: State, timeoutMillis: Long): Boolean = awaitSnapshot(timeoutMillis) { it.state >= target }

    /**
     * Blocks until no instance is bound, with the same timeout and interruption rules as [await].
     * Used to let the system finish unbinding before the service is enabled again.
     * zh-CN: 阻塞直到没有绑定实例, 超时与中断规则同 [await]. 用于在重新启用服务前等待系统完成解绑.
     */
    fun awaitUnbound(timeoutMillis: Long): Boolean = awaitSnapshot(timeoutMillis) { !it.isBound }

    private inline fun awaitSnapshot(timeoutMillis: Long, satisfied: (Snapshot<S>) -> Boolean): Boolean {
        if (satisfied(snapshot)) return true
        lock.lock()
        try {
            if (timeoutMillis < 0) {
                while (!satisfied(snapshot)) stateChanged.await()
                return true
            }
            var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (!satisfied(snapshot)) {
                if (remainingNanos <= 0L) return false
                remainingNanos = stateChanged.awaitNanos(remainingNanos)
            }
            return true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        } finally {
            lock.unlock()
        }
    }

    /**
     * Registers [listener] for [owner], replacing any listener previously registered by the same owner.
     * zh-CN: 为 [owner] 注册 [listener], 同一 owner 之前注册的监听者会被替换.
     */
    fun addListener(owner: Any, listener: StateListener<S>) {
        synchronized(listenersLock) { listeners[owner] = listener }
    }

    fun removeListener(owner: Any): Boolean {
        return synchronized(listenersLock) { listeners.remove(owner) != null }
    }

    fun hasListener(owner: Any): Boolean {
        return synchronized(listenersLock) { listeners.containsKey(owner) }
    }

    val listenerCount: Int get() = synchronized(listenersLock) { listeners.size }

    /** Listeners whose owner [predicate] accepts. zh-CN: 属主被 [predicate] 接受的监听者数. */
    fun countListeners(predicate: (Any) -> Boolean): Int = synchronized(listenersLock) { listeners.keys.count(predicate) }

    /** Removes the listeners whose owner [predicate] accepts; returns how many. zh-CN: 移除属主被 [predicate] 接受的监听者, 返回数量. */
    fun removeListenersIf(predicate: (Any) -> Boolean): Int = synchronized(listenersLock) {
        val owners = listeners.keys.filter(predicate)
        owners.forEach(listeners::remove)
        owners.size
    }

    private inline fun transition(next: (current: Snapshot<S>) -> Snapshot<S>) {
        val previous: Snapshot<S>
        val current: Snapshot<S>
        lock.lock()
        try {
            previous = snapshot
            current = next(previous)
            if (current == previous) return
            snapshot = current
            stateChanged.signalAll()
        } finally {
            lock.unlock()
        }
        notifyListeners(previous, current)
    }

    private fun notifyListeners(previous: Snapshot<S>, current: Snapshot<S>) {
        val targets = synchronized(listenersLock) { listeners.values.toList() }
        targets.forEach { listener ->
            try {
                listener.onStateChanged(previous, current)
            } catch (error: Throwable) {
                onListenerError("Unhandled exception in accessibility state listener: $listener", error)
            }
        }
    }

}
