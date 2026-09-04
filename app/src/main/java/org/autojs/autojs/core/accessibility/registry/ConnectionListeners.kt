package org.autojs.autojs.core.accessibility.registry

import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.Snapshot
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.StateListener

/**
 * Adapters from the legacy connected / disconnected callback shape to registry state listeners.
 * zh-CN: 把旧的 "已连接 / 已断开" 回调形态适配为注册表状态监听者.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object ConnectionListeners {

    /** Whether the bound instance changed, including bind / unbind and replacement by another instance. */
    fun <S : Any> isBindingChanged(previous: Snapshot<S>, current: Snapshot<S>): Boolean {
        return previous.isBound != current.isBound || previous.instance !== current.instance
    }

    /**
     * `onConnected` fires whenever an instance becomes bound (also when the bound instance is replaced,
     * and when an instance is adopted from its first event without `onServiceConnected`),
     * `onDisconnected` fires when the bound instance goes away.
     * zh-CN: 实例进入绑定态 (含被替换, 含未经 `onServiceConnected` 而由首个事件采纳) 时触发 `onConnected`,
     * 绑定实例消失时触发 `onDisconnected`.
     */
    fun <S : Any> connectionCallback(callback: AccessibilityServiceCallback): StateListener<S> = StateListener { previous, current ->
        if (current.isBound && (!previous.isBound || previous.instance !== current.instance)) callback.onConnected()
        if (previous.isBound && !current.isBound) callback.onDisconnected()
    }

    /** Only `onDisconnected` is forwarded. zh-CN: 仅转发 `onDisconnected`. */
    fun <S : Any> disconnectCallback(callback: AccessibilityServiceCallback): StateListener<S> = StateListener { previous, current ->
        if (previous.isBound && !current.isBound) callback.onDisconnected()
    }

}
