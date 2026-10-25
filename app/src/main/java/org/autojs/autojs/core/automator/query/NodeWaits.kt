package org.autojs.autojs.core.automator.query

import org.autojs.autojs.core.automator.flow.Stabilizer
import org.autojs.autojs.core.automator.flow.Stability

/**
 * The node-level waits of `UiObject` (roadmap P5.3): `waitUntilGone()` and `waitForStable()` as
 * plain poll loops over a [Poller], so that the loops are exact under test and share the script's
 * poller (event-assisted under the flag `eventAssistedPolling`, on by default since 6.8.0) in production.
 * Android-free.
 * zh-CN: `UiObject` 的节点级等待 (Roadmap P5.3): 以 [Poller] 之上的普通轮询循环实现的 `waitUntilGone()` 与 `waitForStable()`,
 * 使循环在测试中精确, 在生产中共用脚本的轮询器 (标志 `eventAssistedPolling` 下为事件辅助, 自 6.8.0 起默认开启). 不依赖 Android.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
object NodeWaits {

    /**
     * Polls [isGone] under [policy]; `true` as soon as it holds, `false` once the policy gives up.
     * zh-CN: 按 [policy] 轮询 [isGone]; 一旦成立为 `true`, 计划放弃后为 `false`.
     */
    fun untilGone(poller: Poller, policy: PollPolicy, isGone: () -> Boolean): Boolean {
        return poller.poll(policy) { if (isGone()) true else null } == true
    }

    /**
     * Polls [sample] under [policy] until the samples stay the same (compared as [Stabilizer] does
     * for [stability]) for `stability.stableFor` milliseconds by [clock]; `false` once the policy
     * gives up, or at once when a target seen before goes missing under [org.autojs.autojs.core.automator.flow.MissingPolicy.FAIL].
     * zh-CN: 按 [policy] 轮询 [sample], 直到样本 (按 [Stabilizer] 对 [stability] 的比较方式) 按 [clock] 保持相同达 `stability.stableFor`
     * 毫秒; 计划放弃后为 `false`, 曾看到的目标在 [org.autojs.autojs.core.automator.flow.MissingPolicy.FAIL] 下消失时立即为 `false`.
     */
    fun untilStable(poller: Poller, policy: PollPolicy, clock: () -> Long, stability: Stability, sample: () -> Any?): Boolean {
        val stabilizer = Stabilizer(stability, keyOf = { Stabilizer.keyOf(stability.compare, it) })
        return poller.poll(policy) {
            when (stabilizer.observe(sample(), clock())) {
                is Stabilizer.Verdict.Stable -> true
                Stabilizer.Verdict.Missing -> false
                Stabilizer.Verdict.Pending -> null
            }
        } == true
    }

}
