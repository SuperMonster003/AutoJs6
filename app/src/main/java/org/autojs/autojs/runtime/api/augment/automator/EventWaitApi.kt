package org.autojs.autojs.runtime.api.augment.automator

import android.os.SystemClock
import org.autojs.autojs.core.notification.Notification
import org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.NotificationListener
import org.autojs.autojs.core.accessibility.event.A11yEvent
import org.autojs.autojs.core.automator.event.EventWaitAborted
import org.autojs.autojs.core.automator.event.EventWaiter
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.automator.EventWaitArguments.Filter
import org.autojs.autojs.runtime.api.modules.flow.FlowExecutor
import org.autojs.autojs.runtime.api.modules.flow.FlowJsErrors
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The event-driven waits (roadmap P5.2) behind the globals `waitForIdle` / `waitForEvent` /
 * `waitForToast` / `waitForNotification` and the Flow roots and chain steps of the same names.
 * Each wait is parsed on the calling thread ([prepare]) and then blocks the calling thread ([sync],
 * refused on the UI thread) or a Flow worker ([step]) until what it waits for arrives or the
 * timeout passes, which throws a [FlowError.Timeout] (the script's `FlowError`). Events come from
 * the script's `A11yEventPulse`; toasts and notifications from the notification observer of the
 * accessibility service. Script filters run on the waiting thread, never on the service thread.
 * zh-CN: 全局函数 `waitForIdle` / `waitForEvent` / `waitForToast` / `waitForNotification` 以及同名 Flow 起点与链式步骤背后的事件驱动
 * 等待 (Roadmap P5.2). 每个等待在调用线程上解析 ([prepare]), 然后阻塞调用线程 ([sync], UI 线程上拒绝) 或 Flow 工作线程 ([step]),
 * 直到等待的对象到达或超时 (抛出 [FlowError.Timeout], 即脚本的 `FlowError`). 事件来自脚本的 `A11yEventPulse`; toast 与通知来自
 * 无障碍服务的通知观察者. 脚本过滤器在等待线程上运行, 不在服务线程上运行.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class EventWaitApi(private val scriptRuntime: ScriptRuntime) {

    private val bridge: AccessibilityBridge get() = scriptRuntime.accessibilityBridge

    private val scope get() = scriptRuntime.topLevelScope

    private val defaultTimeout: Long get() = FlowExecutor.of(scriptRuntime).defaults.timeout

    private val clock: () -> Long = { SystemClock.uptimeMillis() }

    /** A parsed wait: [detail] for traces and errors; [run] blocks the calling thread. zh-CN: 解析后的等待: [detail] 用于追踪与错误; [run] 阻塞调用线程. */
    class Prepared(val name: String, val detail: String, private val block: () -> Any?) {
        /** Runs the wait; one aborted by the service going away (roadmap P5.5) is the script's `A11Y_UNAVAILABLE`. zh-CN: 运行等待; 因服务消失而中止的等待 (Roadmap P5.5) 即脚本的 `A11Y_UNAVAILABLE`. */
        fun run(): Any? = try {
            block()
        } catch (e: EventWaitAborted) {
            throw FlowError.A11yUnavailable(name, detail, e)
        }
    }

    fun prepare(name: String, args: Array<out Any?>): Prepared = when (name) {
        STEP_IDLE -> EventWaitArguments.idle(args, defaultTimeout, ::eventType).let { Prepared(name, it.detail) { idle(it) } }
        STEP_EVENT -> EventWaitArguments.event(args, defaultTimeout, ::eventType).let { Prepared(name, it.detail) { event(it) } }
        STEP_TOAST -> EventWaitArguments.toast(args, defaultTimeout).let { Prepared(name, it.detail) { toast(it) } }
        STEP_NOTIFICATION -> EventWaitArguments.notification(args, defaultTimeout).let { Prepared(name, it.detail) { notification(it) } }
        else -> throw IllegalArgumentException("Unknown event wait: $name")
    }

    /**
     * The synchronous global: blocks the calling (non-UI) thread; a timeout becomes the script's
     * `FlowError`, an interruption ends the script.
     * zh-CN: 同步全局函数: 阻塞调用线程 (不能是 UI 线程); 超时成为脚本的 `FlowError`, 中断即结束脚本.
     */
    fun sync(name: String, args: Array<out Any?>): Any? {
        require(!RhinoUtils.isUiThread()) { str(R.string.error_function_called_in_ui_thread, name) }
        val prepared = prepare(name, args)
        return RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
            try {
                toJs(prepared.run())
            } catch (e: FlowError) {
                FlowJsErrors.throwInto(cx, scope, e)
            } catch (_: InterruptedException) {
                throw ScriptInterruptedException()
            }
        }
    }

    /**
     * The Flow step: fulfills with what arrived; a chained `waitForIdle` passes the incoming value
     * through instead.
     * zh-CN: Flow 步骤: 以到达的对象 fulfill; 链式的 `waitForIdle` 则透传输入值.
     */
    fun step(name: String, args: Array<out Any?>, chained: Boolean): FlowStep.Tool {
        val prepared = prepare(name, args)
        return FlowStep.Tool(name, prepared.detail) { value, _ ->
            val result = toJs(prepared.run())
            if (chained && name == STEP_IDLE) value else result
        }
    }

    private fun eventType(name: String): Int = AccessibilityService.eventTypeOf(name)

    private fun idle(a: EventWaitArguments.Idle): Any {
        bridge.ensureServiceStarted()
        val result = bridge.pulse.awaitIdle(a.quietFor, a.timeout, a.types)
        if (!result.quiet) throw FlowError.Timeout(STEP_IDLE, a.detail, result.elapsed, result.events, "the screen kept changing")
        return linkedMapOf(
            "quietFor" to a.quietFor.toDouble(),
            "elapsed" to result.elapsed.toDouble(),
            "events" to result.events.toDouble(),
        )
    }

    private fun event(a: EventWaitArguments.Event): Any {
        bridge.ensureServiceStarted()
        val result = bridge.pulse.withWaiter(a.types) { waiter ->
            waiter.await(a.timeout) { event -> accepts(a.filter, event.wrapper) { key -> eventProperty(event, key) } }
        }
        val event = result.event ?: throw FlowError.Timeout(STEP_EVENT, a.detail, result.elapsed, result.seen.toLong(), "no matching event")
        return event.wrapper
    }

    private fun eventProperty(event: A11yEvent, key: String): String? = when (key) {
        EventWaitArguments.KEY_PACKAGE_NAME -> event.wrapper.packageName?.toString()
        EventWaitArguments.KEY_CLASS_NAME -> event.wrapper.className?.toString()
        EventWaitArguments.KEY_TEXT -> event.raw.text.joinToString(" ")
        else -> null
    }

    private fun toast(a: EventWaitArguments.Toast): Any {
        bridge.ensureServiceStarted()
        val observer = bridge.notificationObserver
        val waiter = EventWaiter<AccessibilityNotificationObserver.Toast>(clock)
        val listener = object : AccessibilityNotificationObserver.ToastListener {
            override fun onToast(toast: AccessibilityNotificationObserver.Toast) = waiter.offer(toast)
        }
        observer.addToastListener(listener)
        val result = try {
            waiter.awaitUnlessServiceLost(a.timeout) { toast ->
                accepts(a.filter, toast) { key ->
                    when (key) {
                        EventWaitArguments.KEY_PACKAGE_NAME -> toast.packageName
                        EventWaitArguments.KEY_TEXT -> toast.text
                        else -> null
                    }
                }
            }
        } finally {
            observer.removeToastListener(listener)
        }
        return result.event ?: throw FlowError.Timeout(STEP_TOAST, a.detail, result.elapsed, result.seen.toLong(), "no matching toast")
    }

    private fun notification(a: EventWaitArguments.Notification): Any {
        bridge.ensureServiceStarted()
        val observer = bridge.notificationObserver
        val waiter = EventWaiter<Notification>(clock)
        val listener = object : NotificationListener {
            override fun onNotification(notification: Notification) = waiter.offer(notification)
        }
        observer.addNotificationListener(listener)
        val result = try {
            waiter.awaitUnlessServiceLost(a.timeout) { notification ->
                accepts(a.filter, notification) { key ->
                    when (key) {
                        EventWaitArguments.KEY_PACKAGE_NAME -> notification.packageName
                        EventWaitArguments.KEY_TITLE -> notification.title
                        EventWaitArguments.KEY_TEXT -> notification.text
                        else -> null
                    }
                }
            }
        } finally {
            observer.removeNotificationListener(listener)
        }
        return result.event ?: throw FlowError.Timeout(STEP_NOTIFICATION, a.detail, result.elapsed, result.seen.toLong(), "no matching notification")
    }

    /** [EventWaiter.await] that the service going away aborts (roadmap P5.5). zh-CN: 服务消失即中止的 [EventWaiter.await] (Roadmap P5.5). */
    private fun <E : Any> EventWaiter<E>.awaitUnlessServiceLost(timeout: Long, accept: (E) -> Boolean) =
        bridge.pulse.abortingOnServiceLoss(this) { await(timeout, accept) }

    private fun accepts(filter: Filter?, candidate: Any, property: (String) -> String?): Boolean = when (filter) {
        null -> true
        is Filter.Script -> RhinoScriptRuntime.toBoolean(callScript(filter.fn, candidate))
        is Filter.Properties -> filter.matches(property)
        is Filter.Text -> filter.matches(property)
    }

    /** Calls a script function on the current (waiting) thread. zh-CN: 在当前 (等待) 线程上调用脚本函数. */
    private fun callScript(fn: Callable, arg: Any?): Any? = RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
        val thisObj = Undefined.SCRIPTABLE_UNDEFINED
        val args = arrayOf(Context.javaToJS(arg, scope))
        when {
            RhinoScriptRuntime.hasTopCall(cx) -> fn.call(cx, scope, thisObj, args)
            else -> RhinoScriptRuntime.doTopCall(fn, cx, scope, thisObj, args, false)
        }
    }

    private fun toJs(value: Any?): Any? = RhinoUtils.withRhinoContext(scriptRuntime) {
        when (value) {
            is Map<*, *> -> RhinoUtils.toJsValue(scope, value)
            else -> Context.javaToJS(value, scope)
        }
    }

    companion object {

        const val STEP_IDLE = "waitForIdle"
        const val STEP_EVENT = "waitForEvent"
        const val STEP_TOAST = "waitForToast"
        const val STEP_NOTIFICATION = "waitForNotification"

        /** The step names, also the globals and the Flow roots. zh-CN: 步骤名, 同时是全局函数名与 Flow 起点名. */
        val STEPS = listOf(STEP_IDLE, STEP_EVENT, STEP_TOAST, STEP_NOTIFICATION)

        /** The globals whose `FlowError`s pass through the augment layer untouched. zh-CN: 其 `FlowError` 原样穿过 augment 层的全局函数. */
        val SYNC_NAMES: Set<String> = STEPS.toSet()

    }

}
