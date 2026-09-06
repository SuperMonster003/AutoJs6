package org.autojs.autojs.core.automator.query

/**
 * Runs a [PollPolicy]: repeats an attempt until it yields a result, the attempts are used up, or the
 * timeout has passed, sleeping between attempts. The clock and the sleep are injected so that the
 * loop is exact under test; the production sleep is the interruptible one of the script thread.
 * The order of checks is the historical one: attempt, then times, then timeout (measured after the
 * attempt, exceeded strictly), then sleep. An event-assisted policy sleeps through
 * [eventSleep] instead, which returns early when the screen changes (roadmap P5.2).
 * zh-CN: 执行 [PollPolicy]: 重复尝试直到得到结果, 次数用尽或超时, 尝试之间休眠. 时钟与休眠可注入, 使循环在测试中精确;
 * 生产环境的休眠为脚本线程的可中断休眠. 检查顺序沿用历史行为: 尝试, 然后次数, 然后超时 (在尝试之后测量, 严格超过才算),
 * 然后休眠. 事件辅助的计划改经 [eventSleep] 休眠, 屏幕变化时提前返回 (Roadmap P5.2).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class Poller(
    private val clock: () -> Long,
    private val sleep: (Long) -> Unit,
    /** Sleeps at most the given time, returning early on an accessibility event. zh-CN: 至多休眠给定时长, 无障碍事件到达时提前返回. */
    private val eventSleep: ((Long) -> Unit)?,
) {

    constructor(clock: () -> Long, sleep: (Long) -> Unit) : this(clock, sleep, null)

    /** The elapsed time and the remaining budget of one run. zh-CN: 一次执行的已用时间与剩余预算. */
    class Progress internal constructor(private val clock: () -> Long, private val policy: PollPolicy) {

        private val start = clock()

        /** Attempts made so far, including the current one. zh-CN: 至今的尝试次数, 含当前这次. */
        var round: Long = 0
            internal set

        val elapsed: Long get() = clock() - start

        /** Milliseconds left before the timeout, or [PollPolicy.UNBOUNDED]. zh-CN: 距超时剩余的毫秒数, 或 [PollPolicy.UNBOUNDED]. */
        val remaining: Long
            get() = when (policy.timeout) {
                PollPolicy.UNBOUNDED -> PollPolicy.UNBOUNDED
                else -> (policy.timeout - elapsed).coerceAtLeast(0)
            }

        internal val timedOut: Boolean get() = policy.timeout != PollPolicy.UNBOUNDED && elapsed > policy.timeout

    }

    /**
     * The first non-null result of [attempt], or `null` once [policy] gives up.
     * zh-CN: [attempt] 的首个非 null 结果; [policy] 放弃后为 `null`.
     */
    fun <T : Any> poll(policy: PollPolicy, attempt: (Progress) -> T?): T? {
        if (policy.isNoop) return null
        val progress = Progress(clock, policy)
        while (true) {
            progress.round += 1
            attempt(progress)?.let { return it }
            if (policy.times != PollPolicy.UNBOUNDED && progress.round >= policy.times) return null
            if (progress.timedOut) return null
            val nap = policy.intervalAfter(progress.round - 1)
            val eventSleep = eventSleep
            if (policy.eventAssisted && eventSleep != null) eventSleep(nap) else sleep(nap)
        }
    }

}
