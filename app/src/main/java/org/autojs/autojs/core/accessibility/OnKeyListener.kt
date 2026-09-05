package org.autojs.autojs.core.accessibility

import android.view.KeyEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus

/**
 * Created by Stardust on Jul 18, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
interface OnKeyListener {

    fun onKeyEvent(keyCode: Int, event: KeyEvent)

    /**
     * Compatibility shell of the old listener list over the process-wide key event bus: listeners
     * are non-consuming subscriptions at [priority], keyed by the listener itself, and therefore
     * survive service restarts.
     * zh-CN: 旧监听者列表在进程级按键事件总线上的兼容外壳: 监听者是优先级为 [priority] 的非消费型订阅项,
     * 以监听者自身为键, 因此在服务重启后依然存在.
     */
    class Observer(private val bus: A11yEventBus<KeyEvent>, private val priority: Int) : OnKeyListener {

        override fun onKeyEvent(keyCode: Int, event: KeyEvent) {
            bus.dispatch(event)
        }

        fun addListener(listener: OnKeyListener) {
            bus.subscribe(owner = listener, priority = priority) { event ->
                listener.onKeyEvent(event.keyCode, event)
                false
            }
        }

        fun removeListener(listener: OnKeyListener): Boolean {
            return bus.unsubscribeAll(listener) > 0
        }

    }

}
