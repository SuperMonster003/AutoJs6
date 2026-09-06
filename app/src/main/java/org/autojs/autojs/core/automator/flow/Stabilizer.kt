package org.autojs.autojs.core.automator.flow

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.UiObjectSummary

/**
 * The "unchanged for N milliseconds" detector of the stability waits (roadmap Appendix A.5): fed
 * one sample per poll (`null` when the target is missing), it reports when the sample has stayed
 * the same for [Stability.stableFor] milliseconds by the caller's clock. Samples are reduced to a
 * key by [keyOf] as soon as they are observed (a node's fingerprint, say) and keys are compared
 * by [same], so a node object the probe keeps mutating is still seen to change. A change restarts
 * the count; a missing target after a sighting restarts it ([MissingPolicy.RESET]) or fails at
 * once ([MissingPolicy.FAIL]); a target never seen yet is simply awaited. Time is passed in, so
 * the detector is exact under test and shares the poller's clock in production.
 * zh-CN: 稳定检测等待的 "连续 N 毫秒无变化" 检测器 (Roadmap 附录 A.5): 每次轮询喂入一个样本 (目标缺失时为 `null`), 当样本按调用方
 * 的时钟保持相同达 [Stability.stableFor] 毫秒时报告稳定. 样本在观察到的当时即由 [keyOf] 归约为键 (如节点的指纹), 键由 [same] 比较,
 * 因此探测持续修改的同一节点对象仍能被看出变化. 变化即重新计时; 曾看到的目标再次缺失时重新计时 ([MissingPolicy.RESET]) 或立即失败
 * ([MissingPolicy.FAIL]); 尚未看到过的目标只是继续等待. 时间由外部传入, 因此测试中精确, 生产中与轮询器共用时钟.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class Stabilizer(
    private val stability: Stability,
    private val keyOf: (Any) -> Any?,
    private val same: (Any?, Any?) -> Boolean = { previous, next -> previous == next },
) {

    sealed class Verdict {

        /** Keep polling. zh-CN: 继续轮询. */
        object Pending : Verdict()

        /** The target has been [sample] for long enough. zh-CN: 目标已保持为 [sample] 足够久. */
        data class Stable(val sample: Any) : Verdict()

        /** A target seen before is gone and the policy is [MissingPolicy.FAIL]. zh-CN: 曾看到的目标消失且策略为 [MissingPolicy.FAIL]. */
        object Missing : Verdict()

    }

    private var present = false

    private var lastKey: Any? = null

    private var stableSince = 0L

    /** Whether the target has been seen at least once. zh-CN: 是否至少看到过目标一次. */
    var seen = false
        private set

    /** Samples fed so far. zh-CN: 至今喂入的样本数. */
    var observations = 0L
        private set

    /** Why a wait that gives up now failed: `missing` when the target is absent, else `unstable`. zh-CN: 此刻放弃的等待失败的原因: 目标缺失为 `missing`, 否则 `unstable`. */
    val reason: String get() = if (present) "unstable" else "missing"

    fun observe(sample: Any?, now: Long): Verdict {
        observations += 1
        if (sample == null) {
            if (seen && stability.missing == MissingPolicy.FAIL) return Verdict.Missing
            present = false
            lastKey = null
            return Verdict.Pending
        }
        seen = true
        val key = keyOf(sample)
        if (!present || !same(lastKey, key)) {
            present = true
            lastKey = key
            stableSince = now
        }
        return when {
            now - stableSince >= stability.stableFor -> Verdict.Stable(sample)
            else -> Verdict.Pending
        }
    }

    companion object {

        /**
         * The value two samples are compared by under [compare] (other than [StabilityCompare.Custom],
         * whose comparator receives the samples themselves): a node's fingerprint, bounds or
         * content; any other sample is compared by `equals`.
         * zh-CN: [compare] 之下两个样本据以比较的值 ([StabilityCompare.Custom] 除外, 其比较器直接收到样本): 节点的指纹, 边界或内容;
         * 其它样本按 `equals` 比较.
         */
        fun keyOf(compare: StabilityCompare, sample: Any): Any = when (sample) {
            is UiNode<*> -> when (compare) {
                StabilityCompare.Fingerprint -> UiObjectSummary.fingerprint(sample)
                StabilityCompare.Bounds -> listOf(sample.left(), sample.top(), sample.right(), sample.bottom())
                StabilityCompare.Content -> sample.text() to sample.desc()
                is StabilityCompare.Custom -> sample
            }
            else -> sample
        }

        /** Whether a node counts as visible for `waitForVisible` / `waitForHidden`: visible to the user with non-empty bounds. zh-CN: 节点在 `waitForVisible` / `waitForHidden` 中是否算可见: 对用户可见且边界非空. */
        fun isVisible(sample: Any?): Boolean = when (sample) {
            null -> false
            is UiNode<*> -> sample.visibleToUser() && sample.width() > 0 && sample.height() > 0
            else -> true
        }

    }

}
