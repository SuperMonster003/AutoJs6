package org.autojs.autojs.core.accessibility.event

/**
 * Dispatches one kind of event to owner-keyed subscriptions, in priority order, with optional
 * consumption and per-subscription dedupe / debounce. One instance per event kind (accessibility
 * events, key events, gestures) is held by [org.autojs.autojs.core.accessibility.AccessibilityService]
 * in its companion object, so subscriptions survive service restarts.
 *
 * Dispatch order: ascending [Subscription.priority], insertion order among equals. A handler that
 * returns `true` on a [Subscription.consumable] subscription consumes the event and ends the chain.
 * Handler exceptions are logged and never abort the chain. Subscriptions are read as a snapshot, so
 * subscribing or unsubscribing from inside a handler is safe and takes effect from the next event on.
 *
 * The event type is a type parameter and every environment touch point (clock, deferred scheduler,
 * how to retain an event for deferred delivery) is injected, so the class is Android-free and
 * unit-tested.
 *
 * zh-CN: 把一种事件按优先级分发给以 owner 为键的订阅项, 支持消费与订阅项级的去重 / 去抖. 每种事件 (无障碍事件, 按键, 手势)
 * 一个实例, 由 `AccessibilityService` 的伴生对象持有, 因此订阅在服务重启后依然存在.
 * 分发顺序: [Subscription.priority] 升序, 相同优先级按注册顺序. [Subscription.consumable] 的订阅项处理器返回 `true`
 * 即消费事件并终止链. 处理器异常只记录, 不影响链. 分发读取的是订阅快照, 因此在处理器内订阅 / 退订是安全的, 从下一个事件起生效.
 * 事件类型为泛型参数, 所有环境触点 (时钟, 延迟调度器, 延迟投递时如何保留事件) 均注入, 因此本类不依赖 Android 并有单元测试.
 *
 * @param typeOf the integer type of an event, matched against [Subscription.types].
 * @param clock milliseconds, used for dedupe windows and quiet-window detection.
 * @param scheduler runs debounced deliveries; required only when a subscription asks for a debounce.
 * @param retain makes an event safe to keep past the current dispatch (deferred deliveries only).
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class A11yEventBus<E : Any>(
    private val typeOf: (E) -> Int,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scheduler: DeferredScheduler? = null,
    private val retain: (E) -> E = { it },
    private val log: (message: String, error: Throwable?) -> Unit = { _, error -> error?.printStackTrace() },
) {

    fun interface Handler<E> {
        /** Returns whether the event was consumed; only honoured for consumable subscriptions. zh-CN: 返回事件是否被消费; 仅对可消费的订阅项有效. */
        fun handle(event: E): Boolean
    }

    /** Runs a task later; the returned handle cancels it. zh-CN: 延后运行任务; 返回的句柄用于取消. */
    fun interface DeferredScheduler {

        fun schedule(delayMillis: Long, task: Runnable): Cancellable

        fun interface Cancellable {
            fun cancel()
        }

    }

    /**
     * How a subscription thins out repeated events. Both filters are off by default.
     *
     * - Distinct (leading edge): an event is dropped when its [distinctKey] equals the key of the
     *   last delivered event and, if [distinctWithinMillis] > 0, less than that many milliseconds
     *   have passed since that delivery. Without a key function every event shares one key, which
     *   turns the filter into a plain throttle; without a window equal keys are dropped for as long
     *   as the key does not change.
     * - Debounce (trailing edge): delivery waits until no matching event has arrived for
     *   [debounceMillis]; only the latest event of the burst is delivered. Needs a scheduler.
     *
     * zh-CN: 订阅项如何稀释重复事件, 默认均关闭.
     * 去重 (前沿): 事件的 [distinctKey] 与上一次投递的相同, 且 (若 [distinctWithinMillis] > 0) 距该次投递不足该毫秒数时丢弃;
     * 无键函数时所有事件共用一个键, 即纯节流; 无窗口时相同键持续丢弃直到键变化.
     * 去抖 (后沿): 直到 [debounceMillis] 内没有新的匹配事件才投递, 且只投递这一串中的最后一个; 需要调度器.
     */
    class Options<E>(
        val distinctKey: ((E) -> Any?)? = null,
        val distinctWithinMillis: Long = 0L,
        val debounceMillis: Long = 0L,
    ) {

        val isDistinct: Boolean get() = distinctKey != null || distinctWithinMillis > 0

        val isDebounced: Boolean get() = debounceMillis > 0

        companion object {

            fun <E> none(): Options<E> = Options()

            fun <E> distinct(withinMillis: Long = 0L, key: ((E) -> Any?)? = null): Options<E> = Options(key, withinMillis)

            fun <E> debounce(millis: Long): Options<E> = Options(debounceMillis = millis)

        }

    }

    class Subscription<E> internal constructor(
        val owner: Any,
        /** Accepted event types, null for all. zh-CN: 接受的事件类型, null 为全部. */
        val types: Set<Int>?,
        val priority: Int,
        val consumable: Boolean,
        val options: Options<E>,
        internal val handler: Handler<E>,
    ) {

        /** [clock] time of the last event that matched [types], before any dedupe; 0 until then. zh-CN: 最近一个匹配 [types] 的事件的时刻 (去重之前); 此前为 0. */
        @Volatile
        var lastMatchedAtMillis: Long = 0L
            internal set

        @Volatile
        var isActive: Boolean = true
            internal set

        internal val stateLock = Any()
        internal var hasDelivered = false
        internal var lastDeliveredKey: Any? = null
        internal var lastDeliveredAtMillis = 0L
        internal var pending: E? = null
        internal var pendingTask: DeferredScheduler.Cancellable? = null

        fun accepts(type: Int): Boolean = types == null || types.contains(type)

        override fun toString() = "Subscription(owner=$owner, types=$types, priority=$priority, consumable=$consumable)"

    }

    private val subscriptionsLock = Any()

    // Copy-on-write, kept sorted by priority (stable): dispatch iterates without locking.
    // zh-CN: 写时复制, 按优先级稳定排序: 分发时无锁遍历.
    @Volatile
    private var subscriptions: List<Subscription<E>> = emptyList()

    /** [clock] time of the last dispatched event of any type; 0 until then. zh-CN: 最近一次分发 (任何类型) 的时刻; 此前为 0. */
    @Volatile
    var lastDispatchAtMillis: Long = 0L
        private set

    val subscriptionCount: Int get() = subscriptions.size

    fun count(predicate: (Subscription<E>) -> Boolean): Int = subscriptions.count(predicate)

    fun subscriptionsOf(owner: Any): List<Subscription<E>> = subscriptions.filter { it.owner == owner }

    /** Whether any subscription accepts [type]; lets callers skip building the event. zh-CN: 是否有订阅项接受 [type]; 供调用方省去构建事件. */
    fun hasSubscribers(type: Int): Boolean = subscriptions.any { it.accepts(type) }

    fun subscribe(
        owner: Any,
        types: Set<Int>? = null,
        priority: Int = 0,
        consumable: Boolean = false,
        options: Options<E> = Options(),
        handler: Handler<E>,
    ): Subscription<E> {
        require(!options.isDebounced || scheduler != null) { "A debounced subscription needs a scheduler" }
        val subscription = Subscription(owner, types, priority, consumable, options, handler)
        synchronized(subscriptionsLock) {
            val index = subscriptions.indexOfLast { it.priority <= priority } + 1
            subscriptions = ArrayList(subscriptions).apply { add(index, subscription) }
        }
        return subscription
    }

    fun unsubscribe(subscription: Subscription<E>): Boolean {
        synchronized(subscriptionsLock) {
            if (subscription !in subscriptions) return false
            subscriptions = subscriptions.filter { it !== subscription }
        }
        deactivate(subscription)
        return true
    }

    /** Removes every subscription [predicate] accepts; returns how many. zh-CN: 移除 [predicate] 接受的全部订阅项, 返回数量. */
    fun unsubscribeIf(predicate: (Subscription<E>) -> Boolean): Int {
        val removed: List<Subscription<E>>
        synchronized(subscriptionsLock) {
            removed = subscriptions.filter(predicate)
            if (removed.isEmpty()) return 0
            val gone = removed.toSet()
            subscriptions = subscriptions.filter { it !in gone }
        }
        removed.forEach(::deactivate)
        return removed.size
    }

    /** Removes every subscription of [owner]; returns how many. zh-CN: 移除 [owner] 的全部订阅项, 返回数量. */
    fun unsubscribeAll(owner: Any): Int {
        val removed: List<Subscription<E>>
        synchronized(subscriptionsLock) {
            removed = subscriptions.filter { it.owner == owner }
            if (removed.isEmpty()) return 0
            subscriptions = subscriptions.filter { it.owner != owner }
        }
        removed.forEach(::deactivate)
        return removed.size
    }

    /**
     * Delivers [event] to the matching subscriptions in order and returns whether one of them
     * consumed it. Debounced subscriptions only record the event here; their delivery happens later
     * on the scheduler and can never consume.
     * zh-CN: 按顺序把 [event] 投递给匹配的订阅项, 返回是否被消费. 去抖的订阅项此处只记录事件, 稍后由调度器投递, 且不能消费.
     */
    fun dispatch(event: E): Boolean {
        val type = typeOf(event)
        val now = clock()
        lastDispatchAtMillis = now
        for (subscription in subscriptions) {
            if (!subscription.accepts(type)) continue
            subscription.lastMatchedAtMillis = now
            if (subscription.options.isDebounced) {
                defer(subscription, event, now)
                continue
            }
            if (!passesDistinct(subscription, event, now)) continue
            if (invoke(subscription, event) && subscription.consumable) return true
        }
        return false
    }

    private fun invoke(subscription: Subscription<E>, event: E): Boolean = try {
        subscription.handler.handle(event)
    } catch (e: Throwable) {
        log("Unhandled exception in event subscription $subscription", e)
        false
    }

    private fun passesDistinct(subscription: Subscription<E>, event: E, now: Long): Boolean {
        val options = subscription.options
        if (!options.isDistinct) return true
        val key = try {
            options.distinctKey?.invoke(event)
        } catch (e: Throwable) {
            log("Unhandled exception in distinct key of $subscription, the event passes", e)
            return true
        }
        synchronized(subscription.stateLock) {
            val withinWindow = options.distinctWithinMillis <= 0 || now - subscription.lastDeliveredAtMillis <= options.distinctWithinMillis
            if (subscription.hasDelivered && key == subscription.lastDeliveredKey && withinWindow) return false
            subscription.hasDelivered = true
            subscription.lastDeliveredKey = key
            subscription.lastDeliveredAtMillis = now
            return true
        }
    }

    private fun defer(subscription: Subscription<E>, event: E, now: Long) {
        val scheduler = scheduler ?: return
        val retained = try {
            retain(event)
        } catch (e: Throwable) {
            log("Unable to retain an event for the debounced $subscription", e)
            return
        }
        synchronized(subscription.stateLock) {
            subscription.pendingTask?.cancel()
            subscription.pending = retained
            subscription.pendingTask = scheduler.schedule(subscription.options.debounceMillis) { flush(subscription) }
        }
    }

    private fun flush(subscription: Subscription<E>) {
        val event = synchronized(subscription.stateLock) {
            subscription.pendingTask = null
            subscription.pending.also { subscription.pending = null }
        } ?: return
        if (!subscription.isActive) return
        // The debounced burst is over; the distinct filter still applies to what is delivered.
        // zh-CN: 去抖的这一串已结束; 投递内容仍受去重过滤.
        if (!passesDistinct(subscription, event, clock())) return
        invoke(subscription, event)
    }

    private fun deactivate(subscription: Subscription<E>) {
        subscription.isActive = false
        synchronized(subscription.stateLock) {
            subscription.pendingTask?.cancel()
            subscription.pendingTask = null
            subscription.pending = null
        }
    }

}
