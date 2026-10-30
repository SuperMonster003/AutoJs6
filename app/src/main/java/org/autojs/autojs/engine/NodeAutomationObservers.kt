package org.autojs.autojs.engine

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.KeyInterceptor
import org.autojs.autojs.core.accessibility.SimpleActionAutomator
import org.autojs.autojs.core.accessibility.event.A11yEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus
import org.autojs.autojs.core.automator.AccessibilityEventWrapper
import org.autojs.autojs.core.broadcast.BroadcastEmitter
import org.autojs.autojs.core.inputevent.InputEventObserver
import org.autojs.autojs.core.inputevent.TouchObserver
import org.json.JSONArray
import org.json.JSONObject

/**
 * The event sources of Rhino's automation family for the JS runtimes (api-coverage P3.4), as execution-owned sessions of
 * [NodeEventObservers]: accessibility events of `auto.registerEvent` (one bus owner per session), the service state of
 * `auto.stateListener`, touches of `events.observeTouch` (the global input event observer, which needs Root or Shizuku),
 * the key interception of `events.setKeyInterceptionEnabled` (a consuming interceptor while the session lives) and Rhino's
 * cross-script `events.broadcast` (an emitter in the process-wide broadcast list). Closing a session (script end included)
 * removes what it registered.
 *
 * zh-CN: JS 运行时的 Rhino 自动化族事件源 (api-coverage P3.4), 作为 [NodeEventObservers] 的执行内会话: auto.registerEvent 的无障碍事件
 * (每个会话一个总线 owner), auto.stateListener 的服务状态, events.observeTouch 的触摸 (全局输入事件观察器, 需 Root 或 Shizuku),
 * events.setKeyInterceptionEnabled 的按键拦截 (会话存活期间的消费型拦截器) 以及 Rhino 跨脚本的 events.broadcast (进程级广播表中的一个发射器).
 * 关闭会话 (含脚本结束) 即移除其登记的内容.
 */
internal object NodeAutomationObservers {

    private fun requireService(): AccessibilityService {
        if (!AccessibilityService.registry.isOperational) throw SecurityException("Enable AutoJs6 accessibility to observe accessibility events or intercept keys.")
        return AccessibilityService.instance ?: throw SecurityException("AutoJs6 accessibility is not connected.")
    }

    /** `params.names`: event names of AccessibilityEvent.TYPE_*; `params.distinctWithin` / `params.debounce` in milliseconds. */
    fun attachAccessibilityEvents(session: NodeEventObservers.Session, params: JSONObject) {
        requireService()
        val names = params.optJSONArray("names") ?: JSONArray()
        require(names.length() > 0) { "At least one accessibility event name is required." }
        val options = A11yEventBus.Options<A11yEvent>(
            distinctKey = if (params.optLong("distinctWithin") > 0) { event -> event.wrapper.let { "${it.eventType}|${it.packageName}|${it.className}" } } else null,
            distinctWithinMillis = params.optLong("distinctWithin").coerceAtLeast(0L),
            debounceMillis = params.optLong("debounce").coerceAtLeast(0L),
        )
        session.detach = { AccessibilityService.removeAllAccessibilityEventCallbacks(session.id) }
        for (i in 0 until names.length()) {
            val name = names.getString(i)
            AccessibilityService.addAccessibilityEventCallback(session.id, name, object : SimpleActionAutomator.Companion.AccessibilityEventCallback {
                override fun onAccessibilityEvent(event: AccessibilityEventWrapper) {
                    session.publish(eventJson(name, event))
                }
            }, options)
        }
    }

    fun attachServiceState(session: NodeEventObservers.Session) {
        val callback = object : AccessibilityServiceCallback {
            override fun onConnected() = session.publish(JSONObject().put("type", "connected"))
            override fun onDisconnected() = session.publish(JSONObject().put("type", "disconnected"))
        }
        session.detach = { AccessibilityService.setCallback(session, null) }
        AccessibilityService.setCallback(session, callback)
    }

    /** Rhino's onTouch: one event per position change, at most one per `params.timeout` ms (Rhino's touch event timeout, 10 by default). */
    fun attachTouches(context: Context, session: NodeEventObservers.Session, params: JSONObject) {
        val timeout = params.optLong("timeout", 10L).coerceAtLeast(0L)
        var last = 0L
        val observer = TouchObserver(InputEventObserver.getGlobal(context))
        observer.setOnTouchEventListener { x, y ->
            val now = SystemClock.uptimeMillis()
            if (now - last >= timeout) {
                last = now
                session.publish(JSONObject().put("type", "touch").put("x", x).put("y", y))
            }
        }
        session.detach = { observer.stop() }
        observer.observe()
    }

    /** `params.all`: intercept every key; `params.keys`: lower-case key names (KEYCODE_ without the prefix) to intercept. */
    fun attachKeyInterception(session: NodeEventObservers.Session, params: JSONObject) {
        val service = requireService()
        val all = params.optBoolean("all")
        val keys = params.optJSONArray("keys")?.let { array -> (0 until array.length()).map { array.getString(it).lowercase() }.toSet() } ?: emptySet()
        val interceptor = object : KeyInterceptor {
            override fun onInterceptKeyEvent(event: KeyEvent): Boolean =
                all || KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_").lowercase() in keys
        }
        session.detach = { service.keyInterrupterObserver.removeKeyInterrupter(interceptor) }
        service.keyInterrupterObserver.addKeyInterrupter(interceptor)
    }

    fun attachScriptBroadcasts(session: NodeEventObservers.Session) {
        val emitter = SessionBroadcastEmitter(session)
        session.detach = { emitter.unregister() }
    }

    /** A JSON value of a broadcast sent from a JS runtime as the Java value Rhino listeners receive (objects as maps, arrays as lists). */
    fun javaValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWithTo(LinkedHashMap()) { javaValue(value.opt(it)) }
        is JSONArray -> (0 until value.length()).mapTo(ArrayList()) { javaValue(value.opt(it)) }
        else -> value
    }

    private fun eventJson(name: String, event: AccessibilityEventWrapper): JSONObject = JSONObject()
        .put("type", "accessibility").put("name", name).put("eventType", event.eventType)
        .put("packageName", event.packageName?.toString() ?: JSONObject.NULL).put("className", event.className?.toString() ?: JSONObject.NULL)
        .put("eventTime", event.eventTime).put("action", event.action).put("isFullScreen", event.isFullScreen)
        .put("source", runCatching { event.source?.let(RuntimeFlowQuery::snapshot) }.getOrNull() ?: JSONObject.NULL)

    /** A broadcast listener of one session: what other scripts (and this one) emit is published, not run on a Rhino timer. */
    private class SessionBroadcastEmitter(private val session: NodeEventObservers.Session) : BroadcastEmitter(null, null) {
        override fun onBroadcast(eventName: String, vararg args: Any?): Boolean {
            session.publish(JSONObject().put("type", "broadcast").put("name", eventName).put("args", JSONArray(args.map(NodeBridgeJson::valueToJson).map(::jsonValue))))
            return true
        }

        private fun jsonValue(text: String): Any = runCatching { org.json.JSONTokener(text).nextValue() }.getOrDefault(text)
    }
}
