package org.autojs.autojs.core.accessibility

import android.view.KeyEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus

/**
 * Created by Stardust on Feb 27, 2018.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
interface KeyInterceptor {

    fun onInterceptKeyEvent(event: KeyEvent): Boolean

    /**
     * Compatibility shell of the old interceptor list over the process-wide key event bus:
     * interceptors are consuming subscriptions at [priority] (after the key observers, so that
     * observers still see intercepted keys), keyed by the interceptor itself.
     * zh-CN: 旧拦截器列表在进程级按键事件总线上的兼容外壳: 拦截器是优先级为 [priority] 的消费型订阅项
     * (排在按键观察者之后, 使观察者仍能看到被拦截的按键), 以拦截器自身为键.
     */
    class Observer(private val bus: A11yEventBus<KeyEvent>, private val priority: Int) : KeyInterceptor {

        override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
            return bus.dispatch(event)
        }

        fun addKeyInterrupter(interrupter: KeyInterceptor) {
            bus.subscribe(owner = interrupter, priority = priority, consumable = true) { event ->
                interrupter.onInterceptKeyEvent(event)
            }
        }

        fun removeKeyInterrupter(interrupter: KeyInterceptor): Boolean {
            return bus.unsubscribeAll(interrupter) > 0
        }

    }

}
