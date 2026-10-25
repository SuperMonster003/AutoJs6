package org.autojs.autojs.core.accessibility

import android.os.SystemClock
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.query.Poller
import org.autojs.autojs.core.automator.search.FastPath
import org.autojs.autojs.runtime.exception.ScriptInterruptedException

/**
 * A selector search repeated under a [PollPolicy]: `find()` / `findOnce()` / `exists()` are the
 * [PollPolicy.ONCE] case, `findOne(timeout)` is [PollPolicy.within], `untilFind*()` is
 * [PollPolicy.FOREVER]. Every attempt is a [UiSelector.find], told by [FastPath.Query] whether it
 * is an intermediate attempt, where an empty framework lookup may stand for the walk (P2.3); the
 * search posted to the UI thread under `FLAG_FIND_ON_UI_THREAD` is given the policy's timeout as its
 * budget. The sleep between attempts is interruptible, so stopping the script ends an unbounded query.
 * zh-CN: 在 [PollPolicy] 下重复进行的选择器搜索: `find()` / `findOnce()` / `exists()` 为 [PollPolicy.ONCE], `findOne(timeout)` 为
 * [PollPolicy.within], `untilFind*()` 为 [PollPolicy.FOREVER]. 每次尝试都是一次 [UiSelector.find], 由 [FastPath.Query] 告知是否为中间尝试
 * (中间尝试中框架查找为空可代替遍历, P2.3); `FLAG_FIND_ON_UI_THREAD` 下投递到 UI 线程的搜索以计划的超时为预算. 尝试之间的休眠可中断,
 * 因此停止脚本会结束无上限的查询.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class UiSelectorQuery internal constructor(
    private val selector: UiSelector,
    val policy: PollPolicy,
    val max: Int = Int.MAX_VALUE,
    private val poller: Poller = defaultPoller,
) {

    /** The nodes of the first attempt that found any, or an empty collection. zh-CN: 首次找到节点的尝试的结果, 或空集合. */
    fun collection(): UiObjectCollection {
        val start = SystemClock.uptimeMillis()
        val run = Run()
        val found = run.search(policy)
        selector.bridge?.stats?.recordQuery(selector.toString(), SystemClock.uptimeMillis() - start, run.attempts, found != null)
        return found ?: UiObjectCollection.EMPTY
    }

    /**
     * The first node found, or `null`; refreshed before it is returned, and searched for again within the rest of the
     * plan when it has left the window since the search (docs/selector/ROADMAP.md D6 (c), P3.2, [UiSelector.verified]).
     * zh-CN: 找到的首个节点, 或 `null`; 返回前刷新, 搜索之后已离开窗口时在计划的剩余部分内重新搜索 (D6 (c), P3.2, [UiSelector.verified]).
     */
    fun one(): UiObject? {
        val start = SystemClock.uptimeMillis()
        val run = Run()
        val found = selector.verified(run.search(policy)?.get(0), policy.isUnbounded) {
            run.search(rest(SystemClock.uptimeMillis() - start, run.attempts))?.get(0)
        }
        selector.bridge?.stats?.recordQuery(selector.toString(), SystemClock.uptimeMillis() - start, run.attempts, found != null)
        return found
    }

    /**
     * What remains of the plan after [elapsed] ms and [attempts] attempts, at least one attempt: a search again
     * after a stale result (P3.2). zh-CN: [elapsed] ms 与 [attempts] 次尝试之后计划的剩余部分, 至少一次尝试: 过期结果之后的重新搜索 (P3.2).
     */
    private fun rest(elapsed: Long, attempts: Long): PollPolicy = policy.copy(
        timeout = if (policy.timeout == PollPolicy.UNBOUNDED) PollPolicy.UNBOUNDED else (policy.timeout - elapsed).coerceAtLeast(0),
        times = if (policy.times == PollPolicy.UNBOUNDED) PollPolicy.UNBOUNDED else (policy.times - attempts).coerceAtLeast(1),
    )

    /** The searches of one call, their attempts counted together. zh-CN: 一次调用的各次搜索, 其尝试次数合计. */
    private inner class Run {

        var attempts = 0L
            private set

        /** The nodes of the first attempt of [plan] that found any, or null. zh-CN: [plan] 中首次找到节点的尝试的结果, 或 null. */
        fun search(plan: PollPolicy): UiObjectCollection? {
            // Which attempts the framework fast path may answer (docs/selector/ROADMAP.md P2.3, appendix C Q1), told by what
            // the lookups and the walks of this query cost (appendix C Q15 / Q17).
            // zh-CN: 框架快路径可以作答哪些尝试 (P2.3, 附录 C Q1), 依本查询的查找与遍历的开销而定 (附录 C Q15 / Q17).
            val fastPath = FastPath.Query(plan) { SystemClock.uptimeMillis() }
            val costs = FastPath.Costs()
            val before = attempts
            return poller.poll(plan) { progress ->
                attempts = before + progress.round
                val attempt = fastPath.attempt(progress.round, progress.remaining)
                costs.reset()
                val result = selector.findWithin(max, plan.timeout, attempt, costs)
                fastPath.record(attempt, costs)
                result.takeIf { it.isNotEmpty() }
            }
        }

    }

    companion object {

        /** The script thread's poller: uptime clock, interruptible sleep. zh-CN: 脚本线程的轮询器: uptime 时钟, 可中断休眠. */
        val defaultPoller: Poller by lazy { Poller({ SystemClock.uptimeMillis() }, ::sleepInterruptibly) }

        private fun sleepInterruptibly(millis: Long) {
            if (Thread.currentThread().isInterrupted) {
                throw ScriptInterruptedException()
            }
            try {
                Thread.sleep(millis)
            } catch (e: InterruptedException) {
                throw ScriptInterruptedException()
            }
        }

    }

}
