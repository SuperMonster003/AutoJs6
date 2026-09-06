package org.autojs.autojs.core.automator.event

/**
 * Thrown out of a pending [EventWaiter.await] / [IdleWaiter.await] that was aborted from outside
 * (roadmap P5.5): the accessibility service went away while a script waited for its events, so
 * the wait can neither succeed nor honestly time out. Android-free; the JS surface turns it into
 * the script's `FlowError` (`A11Y_UNAVAILABLE`).
 * zh-CN: 从外部中止的待定 [EventWaiter.await] / [IdleWaiter.await] 抛出 (Roadmap P5.5): 脚本等待事件期间无障碍服务消失, 等待既不能
 * 成功也不能诚实地超时. 不依赖 Android; JS 表面把它转为脚本的 `FlowError` (`A11Y_UNAVAILABLE`).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class EventWaitAborted(reason: String) : RuntimeException(reason)
