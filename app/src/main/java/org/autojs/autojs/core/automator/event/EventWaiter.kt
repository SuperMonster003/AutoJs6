package org.autojs.autojs.core.automator.event

/**
 * A bounded mailbox between an event thread and one waiting thread (roadmap P5.2): the event
 * thread [offer]s events, the waiting thread [await]s the first one a predicate accepts. The
 * predicate runs on the waiting thread, so a script callback never runs on the event thread and
 * never blocks it. The queue keeps the newest [capacity] events; older ones are counted in
 * [dropped]. Android-free.
 * zh-CN: 事件线程与一个等待线程之间的有界信箱 (Roadmap P5.2): 事件线程 [offer] 事件, 等待线程 [await] 谓词接受的首个事件.
 * 谓词在等待线程上运行, 脚本回调不会在事件线程上运行, 也不会阻塞它. 队列保留最新的 [capacity] 个事件, 更早的计入 [dropped].
 * 不依赖 Android.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class EventWaiter<E : Any>(private val clock: () -> Long, val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity >= 1) { "capacity ($capacity) must be at least 1" }
    }

    /**
     * The outcome of one [await]: the accepted event (`null` on timeout), the time it took and the
     * events examined meanwhile.
     * zh-CN: 一次 [await] 的结果: 被接受的事件 (超时为 `null`), 用时, 以及期间检查过的事件数.
     */
    class Result<E>(val event: E?, val elapsed: Long, val seen: Int) {
        val found: Boolean get() = event != null
    }

    private val gate = EventGate()

    private val lock = Any()

    private val queue = ArrayDeque<E>()

    /** Events offered so far. zh-CN: 至今投递的事件数. */
    @Volatile
    var offered: Long = 0
        private set

    /** Events discarded because the queue was full. zh-CN: 因队列已满而丢弃的事件数. */
    @Volatile
    var dropped: Long = 0
        private set

    /** Whether events are queued. zh-CN: 是否有排队的事件. */
    val hasPending: Boolean get() = synchronized(lock) { queue.isNotEmpty() }

    /** Queues [event] and wakes the waiting thread. zh-CN: 将 [event] 入队并唤醒等待线程. */
    fun offer(event: E) {
        synchronized(lock) {
            offered += 1
            if (queue.size >= capacity) {
                queue.removeFirst()
                dropped += 1
            }
            queue.addLast(event)
        }
        gate.signal()
    }

    /**
     * Waits at most [timeout] milliseconds ([UNBOUNDED] for ever) for the first queued event
     * [accept] returns true for, examining events in arrival order.
     * zh-CN: 至多等待 [timeout] 毫秒 ([UNBOUNDED] 为不限), 直到队列中出现 [accept] 返回 true 的事件; 按到达顺序检查.
     */
    @Throws(InterruptedException::class)
    fun await(timeout: Long, accept: (E) -> Boolean): Result<E> {
        require(timeout >= 0) { "timeout ($timeout) cannot be negative" }
        val start = clock()
        var seen = 0
        while (true) {
            var next = synchronized(lock) { queue.removeFirstOrNull() }
            while (next != null) {
                seen += 1
                if (accept(next)) return Result(next, clock() - start, seen)
                next = synchronized(lock) { queue.removeFirstOrNull() }
            }
            val elapsed = clock() - start
            if (timeout != UNBOUNDED && elapsed >= timeout) return Result(null, elapsed, seen)
            gate.await(if (timeout == UNBOUNDED) UNBOUNDED else timeout - elapsed)
        }
    }

    companion object {

        const val DEFAULT_CAPACITY = 64

        const val UNBOUNDED = Long.MAX_VALUE

    }

}
