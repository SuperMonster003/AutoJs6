package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewConfiguration
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.automator.GlobalActionAutomator
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.*
import org.autojs.autojs.runtime.api.augment.flow.FlowActions
import org.autojs.autojs.util.App
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable

/** Native Toolkit algorithms with private nodes and callbacks in the owning JS runtime. */
internal class RuntimeFlowToolkit(private val context: Context, private val work: RuntimeFlowBridge.Work) : Closeable {
    private fun service(): AccessibilityService {
        work.check()
        if (!AccessibilityService.registry.isOperational) throw FlowError.A11yUnavailable(work.method)
        return AccessibilityService.instance ?: throw FlowError.A11yUnavailable(work.method)
    }
    private val arena = RuntimeFlowNodes(work::check) {
        service()
        WindowRootProvider.default.activeRoot(A11yWindowRootPolicy())?.let { UiObject.createRoot(it) }
    }
    private val bindings = object : ToolkitBindings<RuntimeFlowNode> {
        override fun find(target: Any?, root: RuntimeFlowNode?): RuntimeFlowNode? = findAll(target, root, false).firstOrNull()
        override fun findAll(target: Any?, root: RuntimeFlowNode?) = findAll(target, root, true)
        fun findAll(target: Any?, root: RuntimeFlowNode?, all: Boolean): List<RuntimeFlowNode> {
            service()
            val value = RuntimeFlowJson.nullable(target)
            return when {
                value is RuntimeFlowNode -> if (refresh(value)) listOf(value) else emptyList()
                RuntimeFlowJson.callback(value) != null -> nodes(work.invoke(value))
                value is JSONObject && value.has("__flowNode") -> listOf(arena.reference(value.getJSONObject("__flowNode")))
                else -> arena.search(selector(value), root, all)
            }
        }
        private fun nodes(value: Any?): List<RuntimeFlowNode> = when (value) {
            null, JSONObject.NULL -> emptyList()
            is JSONArray -> RuntimeFlowJson.list(value).flatMap(::nodes)
            is JSONObject -> listOf(arena.reference(value.optJSONObject("__flowNode") ?: throw FlowError.InvalidTarget(work.method, reason = "callback must return node snapshots")))
            else -> throw FlowError.InvalidTarget(work.method, reason = "callback must return node snapshots")
        }
        override fun probe(cond: Any?, root: RuntimeFlowNode?): Any? {
            service()
            return when {
                RuntimeFlowJson.callback(cond) != null -> work.invoke(cond).takeIf { RuntimeFlowJson.truthy(it, wait = true) }
                cond is String || cond is JSONObject || cond is RuntimeFlowNode -> find(cond, root)
                else -> cond.takeIf { RuntimeFlowJson.truthy(it, wait = true) }
            }
        }
        override fun activeRoot() = arena.root()
        override fun refresh(node: RuntimeFlowNode): Boolean { service(); return node.value.refresh() }
        override fun pick(node: RuntimeFlowNode, resultType: Any?): Any? {
            work.check()
            if (RuntimeFlowJson.callback(resultType) != null) return work.invoke(resultType, listOf(node))
            val snapshot = RuntimeFlowQuery.snapshot(node.value)
            return when (resultType) {
                null, "#" -> node
                "$" -> listOf(node.text(), node.desc(), node.id()).firstOrNull { !it.isNullOrEmpty() }.orEmpty()
                "text", "desc", "id", "className", "packageName", "bounds", "depth", "indexInParent", "clickable", "enabled", "scrollable", "visibleToUser", "checked", "checkable", "editable", "focused", "selected" -> snapshot.opt(resultType.toString())
                else -> throw IllegalArgumentException("Unsupported portable resultType: $resultType")
            }
        }
        override fun currentPackage(): String? { service(); return RuntimeFlowForeground.read().packageName.takeIf { it.isNotEmpty() } }
        override fun resolvePackage(app: Any?): String? {
            require(app is String && app.isNotBlank()) { "app must be a package, label or alias" }
            work.check()
            val alias = App.getAppByAlias(app)?.packageName
            val pm = context.packageManager
            if (alias != null && runCatching { pm.getApplicationInfo(alias, 0) }.isSuccess) return alias
            if (runCatching { pm.getApplicationInfo(app, 0) }.isSuccess) return app
            return pm.getInstalledApplications(0).firstOrNull { pm.getApplicationLabel(it).toString() == app }?.packageName
        }
        override fun screenWidth() = context.resources.displayMetrics.widthPixels
        override fun screenHeight() = context.resources.displayMetrics.heightPixels
        override fun watchWindows(debounceMillis: Long, onChange: Runnable): ToolkitBindings.Watch = error("Flow popup watch is synchronous-only")
    }
    private fun gesture() = GlobalActionAutomator(context, Handler(Looper.getMainLooper())) { service() }
    private val actions = object : ActionPerformer<RuntimeFlowNode> {
        override fun click(node: RuntimeFlowNode) = act(node, "click")
        override fun longClick(node: RuntimeFlowNode) = act(node, "longClick")
        override fun focus(node: RuntimeFlowNode) = act(node, "focus")
        override fun setText(node: RuntimeFlowNode, text: String) = act(node, "setText", listOf(text))
        override fun imeEnter(node: RuntimeFlowNode) = act(node, "imeEnter")
        override fun scroll(node: RuntimeFlowNode, direction: ScrollDirection): Boolean {
            service()
            val n = node.value
            return when {
                n.hasAction("SCROLL_${direction.name}") -> when (direction) { ScrollDirection.UP -> n.scrollUp(); ScrollDirection.DOWN -> n.scrollDown(); ScrollDirection.LEFT -> n.scrollLeft(); ScrollDirection.RIGHT -> n.scrollRight() }
                n.hasAction("PAGE_${direction.name}") -> when (direction) { ScrollDirection.UP -> n.pageUp(); ScrollDirection.DOWN -> n.pageDown(); ScrollDirection.LEFT -> n.pageLeft(); ScrollDirection.RIGHT -> n.pageRight() }
                direction.isForward -> n.scrollForward()
                else -> n.scrollBackward()
            }
        }
        override fun tap(x: Int, y: Int): Boolean { service(); return gesture().click(x, y) }
        override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMillis: Long): Boolean { service(); return gesture().swipe(x1, y1, x2, y2, durationMillis) }
        override fun back() = service().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        override fun launch(packageName: String): Boolean {
            work.check()
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
    }
    private val kit = Toolkit(bindings, actions, clock = SystemClock::uptimeMillis, sleep = work::sleep, fatal = { true })
    private fun act(node: RuntimeFlowNode, name: String, args: List<Any?> = emptyList()): Boolean {
        service()
        return if (name == "clickBounds") {
            require(args.all { it is Number && it.toDouble().isFinite() && it.toDouble() in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }) { "clickBounds offsets must be finite coordinates" }
            if (!node.value.refresh()) throw FlowError.InvalidTarget(name, reason = "stale")
            val x = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            val y = (args.getOrNull(1) as? Number)?.toInt() ?: 0
            actions.tap(node.centerX() + x, node.centerY() + y)
        } else FlowActions.performNode(node.value, name, args)
    }
    fun run(name: String, args: JSONArray, options: JSONObject): Any {
        work.check()
        if (name in RuntimeFlowEvents.METHODS) return RuntimeFlowJson.encode(RuntimeFlowEvents(work).run(name, options))
        val o = RuntimeFlowOptions(options, if (name == "nodeAction" || name == "globalAction") "action" else name)
        fun arg(i: Int) = RuntimeFlowJson.nullable(args.opt(i))
        fun targets() = RuntimeFlowJson.list(arg(0)).also { require(it.isNotEmpty()) }
        val root = o.raw("root")?.let {
            val snapshot = (it as? JSONObject)?.optJSONObject("__flowNode")
            if (snapshot != null) arena.reference(snapshot, movingRoot = true)
            else bindings.find(it, null) ?: throw FlowError.InvalidTarget(name, reason = "root missing")
        }
        val bounds = "Bounds" in name
        val value: Any? = when (name) {
            "smartClick", "smartClickBounds" -> smart(kit.smartClick.click(kit.locate(arg(0), root, name, o.locate()), o.click(bounds), name, root))
            "clickIfExists", "clickBoundsIfExists" -> kit.clickIfExists(arg(0), root, o.locate(), o.click(bounds), name) != null
            "findAny" -> kit.findAny(targets(), root, o.locate())?.let(::found)
            "clickAny", "clickBoundsAny" -> kit.clickAny(targets(), root, o.locate(), o.click(bounds), name)?.let { found(it.found) + mapOf("method" to it.outcome.method.label, "verified" to it.outcome.verified) }
            "scrollUntil" -> kit.scrollSearch.scrollUntil(arg(0), ScrollSearch.Options(o.raw("container"), o.direction(), o.count("maxSteps", 20), o.method(), o.bool("stopAtEnd", true), o.time("settle", 300), o.time("gestureDuration", 400), o.locate(), o.humanize()), root).node
            "typeInto" -> {
                require(arg(1) is String) { "text must be a string" }
                kit.textEntry.typeInto(arg(0), arg(1) as String, TextEntry.Options(o.bool("clear", true), o.bool("submit", false), o.bool("verify", true), o.time("verifyTimeout", 1000), o.time("verifyInterval", 100), o.locate(), o.humanize()), root).node
            }
            "dismissPopups" -> {
                require(!o.bool("watch", false) && o.raw("onDismissed") == null && o.raw("onError") == null) { "Flow dismissPopups does not support watch callbacks" }
                val plan = PopupDismisser.Options(o.locate(), o.time("settle", 300), o.count("maxRounds", 10), o.nestedClick(), o.time("debounce", 300))
                if (o.bool("once", true)) kit.popups.dismissOnce(targets(), plan, root)?.let(::dismissed) else kit.popups.dismissAll(targets(), plan, root).map(::dismissed)
            }
            "collectList" -> {
                val until = o.raw("until")?.let { fn -> require(RuntimeFlowJson.callback(fn) != null); { v: Any? -> RuntimeFlowJson.truthy(work.invoke(fn, listOf(v))) } }
                kit.listCollector.collect(arg(0), arg(1), ListCollector.Options(o.direction(), o.count("maxSteps", 20), o.method(), o.raw("resultType") ?: "$", o.bool("dedupe", true), until, o.count("maxItems", Int.MAX_VALUE), o.time("settle", 300), o.time("gestureDuration", 400), o.locate(), o.humanize()), root).items
            }
            "launchAndWait" -> kit.apps.launchAndWait(arg(0), AppLauncher.LaunchOptions(o.time("timeout", 10000), o.time("interval", 200), o.bool("bringToFront", true))).let { mapOf("packageName" to it.packageName, "launched" to it.launched, "elapsed" to it.elapsed, "attempts" to it.attempts) }
            "backUntil" -> kit.apps.backUntil(arg(0), o.back(), root).value
            "backToApp" -> kit.apps.backToApp(arg(0), o.back()).value
            "toggle" -> {
                require(arg(1) is Boolean) { "checked must be boolean" }
                kit.toggle.set(arg(0), arg(1) as Boolean, Toggle.Options(o.locate(), o.time("verifyTimeout", 1000), o.time("verifyInterval", 100), o.nestedClick()), root).let { mapOf("node" to it.node, "changed" to it.changed, "checked" to it.checked, "method" to it.click?.method?.label) }
            }
            "nodeAction" -> {
                val action = args.getString(1)
                val values = RuntimeFlowJson.list(arg(2)).map(RuntimeFlowJson::plain)
                require(values.size in (FlowActions.NODE_ACTIONS[action] ?: error("Unknown node action")))
                val many = arg(0) is JSONArray
                val targets = if (many) RuntimeFlowJson.list(arg(0)) else listOf(arg(0))
                if (targets.isEmpty()) throw FlowError.InvalidTarget(action)
                val nodes = targets.map { bindings.find(it, null) ?: throw FlowError.InvalidTarget(action) }
                val humanize = o.humanize()
                kit.pause(humanize)
                val effective = if (action == "clickBounds") {
                    require(values.all { it is Number && it.toDouble().isFinite() }) { "clickBounds offsets must be finite coordinates" }
                    listOf(((values.getOrNull(0) as? Number)?.toDouble() ?: 0.0) + humanize.offsetX(kit.random),
                        ((values.getOrNull(1) as? Number)?.toDouble() ?: 0.0) + humanize.offsetY(kit.random))
                } else values
                var succeeded = true
                for (node in nodes) {
                    val performed = act(node, action, effective)
                    if (!performed && o.bool("strict", true)) throw FlowError.ActionFailed(action)
                    succeeded = performed && succeeded
                    node.value.refresh()
                }
                if (!succeeded) false else if (many) nodes else nodes.single()
            }
            "globalAction" -> {
                val action = args.getString(0)
                val strokes = if (action in RuntimeFlowGestures.names) {
                    RuntimeFlowGestures.parse(action, RuntimeFlowJson.list(arg(1)).map(RuntimeFlowJson::plain), ViewConfiguration.getTapTimeout().toLong()).map { stroke ->
                        val path = Path().apply {
                            stroke.points.forEachIndexed { index, (x, y) ->
                                if (index == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                            }
                        }
                        GestureDescription.StrokeDescription(path, stroke.start, stroke.duration)
                    }.toTypedArray()
                } else null
                val code = if (strokes == null) GLOBAL_ACTIONS[action] ?: error("Unsupported global action") else null
                kit.pause(o.humanize())
                service()
                val succeeded = if (strokes != null) gesture().gestures(*strokes) else service().performGlobalAction(code!!)
                if (!succeeded && o.bool("strict", true)) throw FlowError.ActionFailed(action)
                true
            }
            else -> throw IllegalArgumentException("Unknown Flow tool: $name")
        }
        work.check()
        return RuntimeFlowJson.encode(value) // Serialize before the private arena releases nodes.
    }
    override fun close() = arena.close()
    companion object {
        val GLOBAL_ACTIONS = mapOf("back" to 1, "home" to 2, "recents" to 3, "notifications" to 4, "quickSettings" to 5, "powerDialog" to 6, "splitScreen" to 7, "lockScreen" to 8, "takeScreenshot" to 9, "dismissNotificationShade" to 15)
        private fun selector(value: Any?): JSONObject = when (value) {
            is String -> JSONObject().put("content", value)
            is JSONObject -> if (value.has("pattern")) JSONObject().put("contentMatches", value) else value
            else -> throw IllegalArgumentException("Expected a Flow selector or node snapshot")
        }
        private fun smart(value: SmartClick.Outcome<RuntimeFlowNode>) = mapOf("ok" to true, "method" to value.method.label, "target" to value.target, "verified" to value.verified)
        private fun found(value: Toolkit.Found<RuntimeFlowNode>) = mapOf("index" to value.index, "target" to value.target, "node" to value.node)
        private fun dismissed(value: PopupDismisser.Dismissed<RuntimeFlowNode>) = mapOf("index" to value.index, "target" to value.target, "node" to value.node, "method" to value.outcome.method.label, "verified" to value.outcome.verified)
    }
}
