package org.autojs.autojs.core.accessibility

import android.os.SystemClock
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.query.Poller
import org.autojs.autojs.runtime.exception.ScriptInterruptedException

/**
 * A selector search repeated under a [PollPolicy]: `find()` / `findOnce()` / `exists()` are the
 * [PollPolicy.ONCE] case, `findOne(timeout)` is [PollPolicy.within], `untilFind*()` is
 * [PollPolicy.FOREVER]. Every attempt is a full [UiSelector.find]; the search posted to the UI
 * thread under `FLAG_FIND_ON_UI_THREAD` is given the policy's timeout as its budget. The sleep
 * between attempts is interruptible, so stopping the script ends an unbounded query.
 * zh-CN: 在 [PollPolicy] 下重复进行的选择器搜索: `find()` / `findOnce()` / `exists()` 为 [PollPolicy.ONCE], `findOne(timeout)` 为
 * [PollPolicy.within], `untilFind*()` 为 [PollPolicy.FOREVER]. 每次尝试都是一次完整的 [UiSelector.find]; `FLAG_FIND_ON_UI_THREAD`
 * 下投递到 UI 线程的搜索以计划的超时为预算. 尝试之间的休眠可中断, 因此停止脚本会结束无上限的查询.
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
        return poller.poll(policy) { selector.findWithin(max, policy.timeout).takeIf { it.isNotEmpty() } } ?: UiObjectCollection.EMPTY
    }

    /** The first node found, or `null`. zh-CN: 找到的首个节点, 或 `null`. */
    fun one(): UiObject? = collection().takeIf { it.isNotEmpty() }?.get(0)

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
