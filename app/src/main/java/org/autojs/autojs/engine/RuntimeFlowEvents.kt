package org.autojs.autojs.engine

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityNotificationObserver
import org.autojs.autojs.core.accessibility.NotificationListener
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry
import org.autojs.autojs.core.automator.event.EventWaiter
import org.autojs.autojs.core.automator.event.IdleWaiter
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.notification.Notification
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Copies primitive event data on the service thread; user filters run on the job thread. */
internal class RuntimeFlowEvents(private val work: RuntimeFlowBridge.Work) {
    fun run(name: String, options: JSONObject): Any {
        require(options.keys().asSequence().all { it in setOf("timeout", "quietFor", "eventTypes", "filter") }) { "Unknown event wait option" }
        val filter = filter(options.opt("filter"), name)
        val types = types(options.opt("eventTypes"), name == "waitForIdle")
        val quietFor = RuntimeFlowJson.millis(options.opt("quietFor"), 500, "quietFor")
        val waiter = EventWaiter<JSONObject>(SystemClock::uptimeMillis)
        val idle = IdleWaiter(SystemClock::uptimeMillis)
        val cleanups = ArrayList<() -> Unit>()
        val lost = AtomicBoolean()
        val thread = Thread.currentThread()
        val registry = AccessibilityService.registry
        val owner = Any()
        try {
            registry.addListener(owner, A11yServiceRegistry.StateListener<AccessibilityService> { previous, current ->
                if (previous.isBound && !current.isBound) { lost.set(true); thread.interrupt() }
            })
            cleanups += { registry.removeListener(owner) }
            requireService(name)
            when (name) {
                "waitForEvent", "waitForIdle" -> {
                    val bus = AccessibilityService.eventBus
                    val subscription = bus.subscribe(owner = owner, types = types, priority = AccessibilityService.PRIORITY_OBSERVER) { event ->
                        if (name == "waitForIdle") idle.onEvent(event.type) else {
                            val raw = event.raw
                            waiter.offer(JSONObject().put("type", AccessibilityEvent.eventTypeToString(raw.eventType))
                                .put("eventType", raw.eventType).put("packageName", raw.packageName?.toString().orEmpty())
                                .put("className", raw.className?.toString().orEmpty()).put("text", raw.text.joinToString(" "))
                                .put("contentDescription", raw.contentDescription?.toString().orEmpty()).put("eventTime", raw.eventTime)
                                .put("windowId", raw.windowId).put("contentChangeTypes", raw.contentChangeTypes))
                        }
                        false
                    }
                    cleanups += { bus.unsubscribe(subscription) }
                }
                "waitForToast" -> {
                    val observer = AutoJs.instance.notificationObserver
                    val listener = object : AccessibilityNotificationObserver.ToastListener {
                        override fun onToast(toast: AccessibilityNotificationObserver.Toast) {
                            waiter.offer(JSONObject().put("type", "toast").put("packageName", toast.packageName).put("text", toast.text.orEmpty()))
                        }
                    }
                    observer.addToastListener(listener)
                    cleanups += { observer.removeToastListener(listener) }
                }
                "waitForNotification" -> {
                    val observer = AutoJs.instance.notificationObserver
                    val listener = object : NotificationListener {
                        override fun onNotification(notification: Notification) {
                            waiter.offer(JSONObject().put("type", "notification").put("packageName", notification.packageName)
                                .put("title", notification.title.orEmpty()).put("text", notification.text.orEmpty()))
                        }
                    }
                    observer.addNotificationListener(listener)
                    cleanups += { observer.removeNotificationListener(listener) }
                }
            }
            requireService(name)
            work.ready()
            if (name == "waitForIdle") {
                val result = idle.await(quietFor, work.remaining(), types)
                work.check()
                if (!result.quiet) throw FlowError.Timeout(name, null, result.elapsed, result.events, "the screen kept changing")
                return mapOf("quietFor" to quietFor, "elapsed" to result.elapsed, "events" to result.events)
            }
            val result = waiter.await(work.remaining()) { event -> work.check(); filter(event) }
            work.check()
            return result.event ?: throw FlowError.Timeout(name, null, result.elapsed, result.seen.toLong(), "no matching event")
        } catch (error: InterruptedException) {
            if (lost.get()) throw FlowError.A11yUnavailable(name, cause = error)
            throw error
        } finally {
            cleanups.asReversed().forEach { it() }
        }
    }
    private fun requireService(name: String) {
        work.check()
        if (!AccessibilityService.registry.isOperational || AccessibilityService.instance == null) throw FlowError.A11yUnavailable(name)
    }
    private fun types(raw: Any?, idle: Boolean): Set<Int>? {
        val value = RuntimeFlowJson.nullable(raw) ?: return if (idle) setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED) else null
        if (value == "*") return null
        val values = if (value is JSONArray) RuntimeFlowJson.list(value) else listOf(value)
        require(values.isNotEmpty()) { "eventTypes must not be empty" }
        return values.map { item ->
            when (item) {
                is String -> AccessibilityService.eventTypeOf(item)
                is Number -> item.toInt().also { require(it > 0 && it.toDouble() == item.toDouble()) }
                else -> throw IllegalArgumentException("Invalid event type")
            }
        }.toSet()
    }
    private fun filter(raw: Any?, name: String): (JSONObject) -> Boolean {
        val value = RuntimeFlowJson.nullable(raw) ?: return { true }
        if (RuntimeFlowJson.callback(value) != null) return { candidate -> RuntimeFlowJson.truthy(work.invoke(value, listOf(candidate))) }
        if (value is String || value is JSONObject && value.has("pattern")) {
            val matcher = matcher(value, false)
            return { matcher(it.optString("text")) || name == "waitForNotification" && matcher(it.optString("title")) }
        }
        require(value is JSONObject) { "Expected an event filter object or function" }
        val keys = if (name == "waitForNotification") setOf("packageName", "title", "text") else if (name == "waitForToast") setOf("packageName", "text") else setOf("packageName", "className", "text")
        require(value.keys().asSequence().all { it in keys }) { "Unsupported event filter property" }
        val tests = value.keys().asSequence().map { key -> key to matcher(value.get(key), key == "packageName" || key == "className") }.toList()
        return { event -> tests.all { (key, test) -> test(event.optString(key)) } }
    }
    private fun matcher(value: Any, exact: Boolean): (String) -> Boolean {
        if (value is String) return { if (exact) it == value else it.contains(value) }
        require(value is JSONObject && value.keys().asSequence().all { it == "pattern" || it == "flags" }) { "Expected string or regex descriptor" }
        val flags = value.optString("flags")
        require(flags.all { it in "ims" } && flags.toSet().size == flags.length)
        val pattern = Regex(value.getString("pattern"), buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        })
        return pattern::containsMatchIn
    }
    companion object { val METHODS = setOf("waitForIdle", "waitForEvent", "waitForToast", "waitForNotification") }
}
