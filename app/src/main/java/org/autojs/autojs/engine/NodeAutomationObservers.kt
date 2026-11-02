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
import org.autojs.autojs.util.RhinoUtils
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

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

    /** A JSON value of a broadcast sent from a JS runtime as a value of the broadcast list (objects and arrays stay JSON, see [BroadcastEmitter.Json]). */
    fun javaValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject, is JSONArray -> BroadcastEmitter.Json(value.toString())
        else -> value
    }

    /**
     * A broadcast argument as the JSON value a JS runtime receives. Rhino objects and arrays follow JSON.stringify: functions
     * and undefined are left out of objects and are null in arrays, a toJSON method is used, and so on. A whole argument that
     * has no JSON text (a function, undefined, NaN, the infinities) is null, as an element of an array would be. So is one
     * that JSON.stringify refuses (a cycle, a throwing toJSON): the sender is another script, which a listener must not fail.
     * zh-CN: 广播参数转为 JS 运行时收到的 JSON 值. Rhino 对象与数组遵循 JSON.stringify: 函数与 undefined 在对象里省略, 在数组里为 null, 会使用
     * toJSON 方法, 等等. 没有 JSON 文本的整个参数 (函数, undefined, NaN, 无穷大) 为 null, 与数组元素一样. JSON.stringify 拒绝的参数 (循环引用, 抛出异常的
     * toJSON) 同样为 null: 发送方是另一个脚本, 监听方不能让它失败.
     */
    fun broadcastJson(value: Any?): Any = when (value) {
        null, is Undefined -> JSONObject.NULL
        is BroadcastEmitter.Json -> NodeBridgeJson.parseJsonValue(value.text)
        is Scriptable -> (runCatching { RhinoUtils.js_json_stringify(value) }.getOrNull() as? String)?.let(NodeBridgeJson::parseJsonValue) ?: JSONObject.NULL
        is Number -> if (value.toDouble().isFinite()) value else JSONObject.NULL
        else -> NodeBridgeJson.valueToJson(value).let { text -> runCatching { NodeBridgeJson.parseJsonValue(text) }.getOrDefault(text) }
    }

    private fun eventJson(name: String, event: AccessibilityEventWrapper): JSONObject = JSONObject()
        .put("type", "accessibility").put("name", name).put("eventType", event.eventType)
        .put("packageName", event.packageName?.toString() ?: JSONObject.NULL).put("className", event.className?.toString() ?: JSONObject.NULL)
        .put("eventTime", event.eventTime).put("action", event.action).put("isFullScreen", event.isFullScreen)
        .put("source", runCatching { event.source?.let(RuntimeFlowQuery::snapshot) }.getOrNull() ?: JSONObject.NULL)

    /** A broadcast listener of one session: what other scripts (and this one) emit is published, not run on a Rhino timer. */
    private class SessionBroadcastEmitter(private val session: NodeEventObservers.Session) : BroadcastEmitter(null, null) {
        override fun onBroadcast(eventName: String, vararg args: Any?): Boolean {
            session.publish(JSONObject().put("type", "broadcast").put("name", eventName).put("args", JSONArray(args.map(::broadcastJson))))
            return true
        }
    }
}
