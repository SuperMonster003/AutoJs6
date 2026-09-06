package org.autojs.autojs.core.automator.event

/**
 * Detects a quiet period in an event stream (roadmap P5.2): [onEvent] stamps each event by type,
 * [await] blocks until no event (of the given types) has arrived for `quietFor` milliseconds or
 * the timeout passes. Like uiautomator's `waitForIdle`, the quiet period counts from the last
 * event, so a screen that has already been quiet long enough returns at once; before the first
 * event it counts from the creation of the waiter. Android-free.
 * zh-CN: 检测事件流中的安静期 (Roadmap P5.2): [onEvent] 按类型记录每个事件的时间, [await] 阻塞到 (给定类型的) 事件已有
 * `quietFor` 毫秒没有到达, 或超时. 同 uiautomator 的 `waitForIdle`, 安静期从最后一个事件算起, 已经安静足够久的屏幕立即返回;
 * 首个事件之前从本对象创建时算起. 不依赖 Android.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class IdleWaiter(private val clock: () -> Long) {

    /**
     * The outcome of one [await]: whether it ended quiet, the time it took, and the events (of the
     * requested types) seen meanwhile.
     * zh-CN: 一次 [await] 的结果: 是否以安静结束, 用时, 以及期间看到的 (所请求类型的) 事件数.
     */
    class Result(val quiet: Boolean, val elapsed: Long, val events: Long)

    private class Stamp(var last: Long, var count: Long)

    private val gate = EventGate()

    private val lock = Any()

    private val origin = clock()

    private val byType = HashMap<Int, Stamp>()

    private val all = Stamp(NEVER, 0)

    private var abortSerial = 0L

    private var abortCause: Throwable? = null

    /**
     * Ends the [await]s pending right now by throwing [cause] from them (roadmap P5.5); the waiter
     * stays usable, so a wait started afterwards is not affected. zh-CN: 让此刻待定的各 [await] 抛出 [cause] 结束
     * (Roadmap P5.5); 等待器仍可使用, 之后开始的等待不受影响.
     */
    fun abort(cause: Throwable) {
        synchronized(lock) {
            abortSerial += 1
            abortCause = cause
        }
        gate.signal()
    }

    private fun abortedSince(serial: Long): Throwable? = synchronized(lock) {
        if (abortSerial != serial) abortCause else null
    }

    /** Records an event of [type] at the current time. zh-CN: 记录当前时刻的一个 [type] 事件. */
    fun onEvent(type: Int) {
        val now = clock()
        synchronized(lock) {
            byType.getOrPut(type) { Stamp(NEVER, 0) }.let {
                it.last = now
                it.count += 1
            }
            all.last = now
            all.count += 1
        }
        gate.signal()
    }

    /** The time of the last event of [types] (of any type when `null`), or [NEVER]. zh-CN: [types] (为 `null` 时任意类型) 的最后一个事件的时间, 或 [NEVER]. */
    fun lastEventAt(types: Set<Int>? = null): Long = synchronized(lock) {
        when (types) {
            null -> all.last
            else -> types.maxOfOrNull { byType[it]?.last ?: NEVER } ?: NEVER
        }
    }

    /** Events of [types] (of any type when `null`) seen so far. zh-CN: 至今看到的 [types] (为 `null` 时任意类型) 事件数. */
    fun count(types: Set<Int>? = null): Long = synchronized(lock) {
        when (types) {
            null -> all.count
            else -> types.sumOf { byType[it]?.count ?: 0L }
        }
    }

    /**
     * Waits until no event of [types] (of any type when `null`) has arrived for [quietFor]
     * milliseconds, giving up after [timeout] milliseconds ([UNBOUNDED] for never).
     * zh-CN: 等待到 [types] (为 `null` 时任意类型) 的事件已有 [quietFor] 毫秒没有到达, [timeout] 毫秒后放弃 ([UNBOUNDED] 为不放弃).
     */
    @Throws(InterruptedException::class)
    fun await(quietFor: Long, timeout: Long, types: Set<Int>? = null): Result {
        require(quietFor >= 0) { "quietFor ($quietFor) cannot be negative" }
        require(timeout >= 0) { "timeout ($timeout) cannot be negative" }
        val start = clock()
        val countBefore = count(types)
        val serial = synchronized(lock) { abortSerial }
        while (true) {
            abortedSince(serial)?.let { throw it }
            val now = clock()
            val last = lastEventAt(types)
            val quiet = now - (if (last == NEVER) origin else last)
            if (quiet >= quietFor) return Result(true, now - start, count(types) - countBefore)
            val elapsed = now - start
            if (timeout != UNBOUNDED && elapsed >= timeout) return Result(false, elapsed, count(types) - countBefore)
            val untilQuiet = quietFor - quiet
            gate.await(if (timeout == UNBOUNDED) untilQuiet else minOf(untilQuiet, timeout - elapsed))
        }
    }

    companion object {

        /** No event so far. zh-CN: 至今没有事件. */
        const val NEVER = Long.MIN_VALUE

        const val UNBOUNDED = Long.MAX_VALUE

    }

}
