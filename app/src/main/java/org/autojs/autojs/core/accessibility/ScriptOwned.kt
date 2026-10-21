package org.autojs.autojs.core.accessibility

/**
 * An owner of subscriptions or state listeners on the process-wide accessibility buses that belongs to one script runtime
 * (docs/memory/ROADMAP.md T7 / P4.2). Each module removes its own registrations when the script exits; whatever is left is
 * then released by the runtime's owner id ([AccessibilityService.Companion.releaseOwner]).
 * zh-CN: 进程级无障碍总线上属于某个脚本运行时的订阅或状态监听者的属主 (docs/memory/ROADMAP.md T7 / P4.2). 脚本结束时各模块移除自己的登记;
 * 剩余的再按运行时的 owner id 释放 ([AccessibilityService.Companion.releaseOwner]).
 */
interface ScriptOwned {

    /** The owner id of the script runtime (`ScriptRuntime.ownerId`), or null when not bound to one. zh-CN: 脚本运行时的 owner id, 未绑定时为 null. */
    val scriptOwnerId: String?
}
