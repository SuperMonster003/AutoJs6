package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.flow.FlowError

/**
 * One scroll at a time over a container, shared by [ScrollSearch] and [ListCollector]: the
 * accessibility action of the container ([ScrollMethod.ACTION]) or a swipe across the scrolled
 * area ([ScrollMethod.GESTURE]; the container, else the root, else the active window, else the
 * screen), a settle pause, a refresh of the container, and end detection: the action refusing to
 * scroll, or the area's [NodeFingerprints.subtree] unchanged by two consecutive scrolls, means
 * the end has been reached ([atEnd]); nothing latches, so a caller that keeps scrolling past the
 * end (`stopAtEnd = false`) notices new content arriving.
 * zh-CN: 在容器上逐次滚动, 由 [ScrollSearch] 与 [ListCollector] 共用: 容器的无障碍动作 ([ScrollMethod.ACTION]) 或跨滚动区域的滑动手势
 * ([ScrollMethod.GESTURE]; 区域为容器, 无则根节点, 无则活动窗口, 无则屏幕), 之后停顿, 刷新容器, 并检测末尾: 动作拒绝滚动, 或区域的
 * [NodeFingerprints.subtree] 连续两次滚动无变化, 即视为到底 ([atEnd]); 状态不锁定, 因此越过末尾继续滚动的调用方 (`stopAtEnd = false`)
 * 能注意到新加载的内容.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class Scroller<N : UiNode<N>> internal constructor(
    private val kit: Toolkit<N>,
    /** The container scrolled, `null` for gesture scrolling without one. zh-CN: 被滚动的容器, 无容器的手势滚动为 `null`. */
    val container: N?,
    private val root: N?,
    private val direction: ScrollDirection,
    private val method: ScrollMethod,
    private val settleMillis: Long,
    private val gestureDuration: Long,
    private val humanize: Humanize,
    private val detectEnd: Boolean,
    private val step: String,
) {

    /** Scrolls made so far. zh-CN: 至今滚动的次数. */
    var steps = 0
        private set

    /** Whether the last scroll found the end. zh-CN: 上一次滚动是否发现已到底. */
    var atEnd = false
        private set

    /** Why the end was declared: [REASON_ACTION] or [REASON_UNCHANGED]; `null` while not at the end. zh-CN: 判定到底的原因: [REASON_ACTION] 或 [REASON_UNCHANGED]; 未到底为 `null`. */
    var endReason: String? = null
        private set

    private var unchanged = 0

    /**
     * Scrolls once and reports whether that was progress; `false` means the end has been reached
     * (see [endReason]).
     * zh-CN: 滚动一次并报告是否有进展; `false` 表示已到底 (见 [endReason]).
     *
     * @throws FlowError `INVALID_TARGET` (reason `containerGone`) when the container cannot be
     * refreshed any more, `ACTION_FAILED` (reason `swipe`) when a swipe was not dispatched.
     */
    fun scroll(): Boolean {
        val area = container ?: root ?: kit.bindings.activeRoot()
        val before = if (detectEnd) area?.let { NodeFingerprints.subtree(it) } else null
        kit.pause(humanize)
        val moved = when (method) {
            ScrollMethod.ACTION -> container?.let { kit.actions.scroll(it, direction) } ?: false
            ScrollMethod.GESTURE -> swipe(area)
        }
        steps += 1
        if (settleMillis > 0) kit.sleep(settleMillis)
        if (container != null && !kit.bindings.refresh(container)) {
            throw FlowError.InvalidTarget(step, null, REASON_CONTAINER_GONE, attempts = steps.toLong())
        }
        atEnd = false
        endReason = null
        if (!moved) {
            if (method == ScrollMethod.GESTURE) throw FlowError.ActionFailed(step, null, attempts = steps.toLong(), reason = REASON_SWIPE)
            return end(REASON_ACTION)
        }
        if (before != null) {
            val after = area?.let { NodeFingerprints.subtree(it) }
            if (after == before) {
                unchanged += 1
                if (unchanged >= 2) return end(REASON_UNCHANGED)
            } else {
                unchanged = 0
            }
        }
        return true
    }

    private fun end(reason: String): Boolean {
        atEnd = true
        endReason = reason
        return false
    }

    private fun swipe(area: N?): Boolean {
        val left = area?.left() ?: 0
        val top = area?.top() ?: 0
        val right = area?.right() ?: kit.bindings.screenWidth()
        val bottom = area?.bottom() ?: kit.bindings.screenHeight()
        val width = right - left
        val height = bottom - top
        val centerX = left + width / 2
        val centerY = top + height / 2
        val near = SWIPE_NEAR
        val far = SWIPE_FAR
        return when (direction) {
            ScrollDirection.DOWN -> kit.actions.swipe(centerX, top + (height * far).toInt(), centerX, top + (height * near).toInt(), gestureDuration)
            ScrollDirection.UP -> kit.actions.swipe(centerX, top + (height * near).toInt(), centerX, top + (height * far).toInt(), gestureDuration)
            ScrollDirection.RIGHT -> kit.actions.swipe(left + (width * far).toInt(), centerY, left + (width * near).toInt(), centerY, gestureDuration)
            ScrollDirection.LEFT -> kit.actions.swipe(left + (width * near).toInt(), centerY, left + (width * far).toInt(), centerY, gestureDuration)
        }
    }

    companion object {

        /** The scroll action reported that it could not scroll. zh-CN: 滚动动作报告无法滚动. */
        const val REASON_ACTION = "action"

        /** Two consecutive scrolls changed nothing. zh-CN: 连续两次滚动没有变化. */
        const val REASON_UNCHANGED = "unchanged"

        const val REASON_CONTAINER_GONE = "containerGone"

        const val REASON_SWIPE = "swipe"

        /** A swipe runs from 80 % to 20 % of the area along the scroll axis. zh-CN: 滑动沿滚动轴从区域的 80 % 处到 20 % 处. */
        private const val SWIPE_FAR = 0.8

        private const val SWIPE_NEAR = 0.2

    }

}
