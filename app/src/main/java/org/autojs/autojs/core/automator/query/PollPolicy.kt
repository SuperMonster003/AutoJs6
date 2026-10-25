package org.autojs.autojs.core.automator.query

import kotlin.math.pow

/**
 * A polling plan: attempt at most [times] times, give up once more than [timeout] milliseconds
 * have passed, and sleep [interval] milliseconds between attempts (growing by [backoff] per
 * round, never above [maxInterval]). [UNBOUNDED] means no limit. The defaults reproduce the
 * selector queries as they have always been: a 50 ms interval and no limits. [eventAssisted]
 * lets a poller cut the sleep short when an accessibility event arrives (roadmap P5.2), yet no
 * sooner than [minSpacing] milliseconds after the attempt before (docs/selector/ROADMAP.md D9), so
 * that a burst of events polls no more often than the fixed 50 ms interval.
 * zh-CN: 轮询计划: 至多尝试 [times] 次, 经过的时间超过 [timeout] 毫秒后放弃, 两次尝试之间休眠 [interval] 毫秒 (每轮按
 * [backoff] 增长, 不超过 [maxInterval]). [UNBOUNDED] 表示不限. 默认值即选择器查询一贯的行为: 间隔 50 ms, 无上限.
 * [eventAssisted] 允许轮询器在无障碍事件到达时提前结束休眠 (Roadmap P5.2), 但不早于上一次尝试之后 [minSpacing] 毫秒 (D9),
 * 使连发的事件不会比固定的 50 ms 间隔轮询得更频繁.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
data class PollPolicy(
    val timeout: Long = UNBOUNDED,
    val interval: Long = DEFAULT_INTERVAL,
    val times: Long = UNBOUNDED,
    val backoff: Double = 1.0,
    val maxInterval: Long = interval,
    val eventAssisted: Boolean = false,
    val minSpacing: Long = EVENT_MIN_SPACING,
) {

    init {
        require(timeout >= 0) { "timeout ($timeout) cannot be negative" }
        require(interval >= 0) { "interval ($interval) cannot be negative" }
        require(times >= 0) { "times ($times) cannot be negative" }
        require(backoff >= 1.0) { "backoff ($backoff) must be at least 1" }
        require(maxInterval >= interval) { "maxInterval ($maxInterval) must be at least the interval ($interval)" }
        require(minSpacing >= 0) { "minSpacing ($minSpacing) cannot be negative" }
    }

    /** Whether the plan can only end by a successful attempt. zh-CN: 计划是否只能以一次成功的尝试结束. */
    val isUnbounded: Boolean get() = timeout == UNBOUNDED && times == UNBOUNDED

    /** Whether nothing is ever attempted. zh-CN: 是否不做任何尝试. */
    val isNoop: Boolean get() = times == 0L

    /** The sleep after attempt number [round] (0-based). zh-CN: 第 [round] 次 (从 0 计) 尝试之后的休眠时长. */
    fun intervalAfter(round: Long): Long {
        if (backoff == 1.0 || round <= 0) return interval
        val grown = interval * backoff.pow(round.toDouble())
        return if (grown >= maxInterval) maxInterval else grown.toLong()
    }

    /**
     * This plan woken early by accessibility events (roadmap P5.2): the sleeps between attempts end
     * as soon as the screen changes, and the interval (the fallback when nothing changes) is raised
     * to at least [minInterval] so that a quiet screen is polled less often.
     * zh-CN: 由无障碍事件提前唤醒的本计划 (Roadmap P5.2): 尝试之间的休眠在屏幕变化时即结束, 间隔 (无变化时的兜底) 提高到至少
     * [minInterval], 使安静的屏幕被轮询得更少.
     */
    fun assisted(minInterval: Long = EVENT_ASSISTED_INTERVAL): PollPolicy {
        val interval = maxOf(interval, minInterval)
        return copy(eventAssisted = true, interval = interval, maxInterval = maxOf(maxInterval, interval))
    }

    companion object {

        const val UNBOUNDED = Long.MAX_VALUE

        /** The interval `findOne(timeout)` and `untilFind()` have always polled with. zh-CN: `findOne(timeout)` 与 `untilFind()` 一贯的轮询间隔. */
        const val DEFAULT_INTERVAL = 50L

        /** The fallback interval of event-assisted polling. zh-CN: 事件辅助轮询的兜底间隔. */
        const val EVENT_ASSISTED_INTERVAL = 200L

        /**
         * The least time between two attempts of event-assisted polling, the fixed interval of the other plans (D9).
         * zh-CN: 事件辅助轮询两次尝试之间的最短时间, 即其他计划的固定间隔 (D9).
         */
        const val EVENT_MIN_SPACING = DEFAULT_INTERVAL

        /** A single attempt (`find()`, `findOnce()`, `exists()`). zh-CN: 单次尝试 (`find()`, `findOnce()`, `exists()`). */
        val ONCE = PollPolicy(times = 1)

        /** Until found (`untilFind()`, `untilFindOne()`). zh-CN: 直到找到 (`untilFind()`, `untilFindOne()`). */
        val FOREVER = PollPolicy()

        /**
         * The plan of `findOne(timeout)`: a timeout of 0 or less has always meant "forever".
         * zh-CN: `findOne(timeout)` 的计划: 0 或负数的超时一贯表示 "永久".
         */
        fun within(timeoutMillis: Long, interval: Long = DEFAULT_INTERVAL): PollPolicy = when {
            timeoutMillis > 0 -> PollPolicy(timeout = timeoutMillis, interval = interval)
            else -> PollPolicy(interval = interval)
        }

    }

}
