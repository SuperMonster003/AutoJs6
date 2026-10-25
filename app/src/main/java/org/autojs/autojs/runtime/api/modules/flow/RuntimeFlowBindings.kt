package org.autojs.autojs.runtime.api.modules.flow

import android.os.Bundle
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.flow.FlowBindings
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.flow.StabilityCompare
import org.autojs.autojs.core.automator.flow.Stabilizer
import org.autojs.autojs.core.automator.pick.PickResultType
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.modules.global.Global
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The [FlowBindings] of a script (roadmap P3.2): conditions are probed with `UiSelector.pickup`
 * (selector forms), a script call (predicates), `refresh()` (nodes) or a settlement (flows and
 * promises); callbacks are script calls whose exceptions propagate untouched; node actions go to
 * the `UiObject` methods of [FlowActions] (or to the gesture automator for a point), global actions
 * to the `automator` module; a flow or thenable returned by a callback is adopted. An accessibility
 * service that cannot be started fails a probe or an action with `A11Y_UNAVAILABLE`; a wait with
 * `recoverService` (roadmap P5.5) then waits quietly for the service through [awaitService].
 * zh-CN: 脚本的 [FlowBindings] (Roadmap P3.2): 条件以 `UiSelector.pickup` (选择器形式), 脚本调用 (断言函数), `refresh()` (节点)
 * 或结算结果 (Flow 与 Promise) 探测; 回调为脚本调用, 其异常原样传播; 节点动作交给 [FlowActions] 的 `UiObject` 方法 (坐标点则交给手势
 * 派发器), 全局动作交给 `automator` 模块; 回调返回的 Flow 或 thenable 被采纳. 无法启动的无障碍服务使探测或动作以 `A11Y_UNAVAILABLE`
 * 失败.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class RuntimeFlowBindings(private val scriptRuntime: ScriptRuntime, private val executor: FlowExecutor) : FlowBindings {

    override fun captureSourceStack(): List<StackTraceElement> = captureScriptStack()

    override fun probe(cond: Any?, scope: FlowScope): Any? = when (cond) {
        is FlowCondition.Selector -> {
            val effective = scope.overriddenBy(cond.scope)
            ensureService("wait", cond.description)
            found(UiSelector.pickup(scriptRuntime, effective.root as? UiObject, cond.selector, effective.compass as? CharSequence, effective.resultType ?: PickResultType.WIDGET, null))
        }
        is FlowCondition.Predicate -> satisfied(callScript(cond.fn, emptyList()))
        is FlowCondition.Native -> satisfied(cond.fn())
        is FlowCondition.Node -> cond.node.takeIf { it.refresh() }
        is FlowCondition.Settling -> when (val state = cond.state()) {
            null, is FlowNode.State.Pending -> null
            is FlowNode.State.Fulfilled -> state.value ?: UNDEFINED
            is FlowNode.State.Rejected -> throw state.error
        }
        else -> throw IllegalArgumentException("Not a flow condition: $cond")
    }

    override fun describe(cond: Any?): String? = (cond as? FlowCondition)?.description ?: cond?.toString()

    override fun invoke(fn: Any?, args: List<Any?>): Any? = when (fn) {
        is Callable -> RhinoUtils.unwrap(callScript(fn, args))
        is Function1<*, *> -> @Suppress("UNCHECKED_CAST") (fn as (List<Any?>) -> Any?)(args)
        is Function0<*> -> fn()
        else -> throw IllegalArgumentException("Not a flow callback: $fn")
    }

    override fun isTruthy(value: Any?): Boolean = Context.toBoolean(value)

    override fun perform(action: String, target: Any?, args: List<Any?>): Boolean? {
        val t = RhinoUtils.unwrap(target)
        return when {
            t is UiObject -> performAll(action, listOf(t), args)
            t is UiObjectCollection -> performAll(action, t.nodes.filterNotNull(), args)
            t is List<*> && t.isNotEmpty() && t.all { RhinoUtils.unwrap(it) is UiObject } -> performAll(action, t.map { RhinoUtils.unwrap(it) as UiObject }, args)
            else -> FlowActions.pointOf(t)?.let { (x, y) -> performPoint(action, x, y, args) }
        }
    }

    override fun performGlobal(action: String, args: List<Any?>): Boolean {
        ensureService(action, null)
        return FlowActions.performGlobal(scriptRuntime, action, args)
    }

    override fun adopt(value: Any?): FlowNode? = when {
        value is FlowNativeObject -> value.node.also { value.observed = true }
        value is FlowNode -> value
        executor.isThenable(value) -> executor.adoptThenable(value as org.mozilla.javascript.Scriptable)
        else -> null
    }

    override fun snapshot(value: Any?): Any? = FlowSnapshot.capture(value)

    override fun stabilityKey(compare: StabilityCompare, value: Any): Any? =
        Stabilizer.keyOf(compare, if (value is UiObjectCollection) value.nodes else value)

    /** Roadmap P5.5 (`recoverService`): the quiet wait of the service controller, never the settings page. zh-CN: Roadmap P5.5 (`recoverService`): 服务控制器的安静等待, 从不打开设置页. */
    override fun awaitService(timeoutMillis: Long): Boolean = scriptRuntime.accessibilityBridge.awaitServiceAvailable(timeoutMillis)

    /** The JavaScript value of [value]: errors per [FlowJsErrors], collections as bound arrays, the rest as usual. zh-CN: [value] 的 JavaScript 值: 错误按 [FlowJsErrors], 集合为绑定的数组, 其余照常. */
    fun toJs(cx: Context, scope: VarScope, value: Any?): Any? = when (value) {
        is Throwable -> FlowJsErrors.toJs(cx, scope, value)
        is UiObjectCollection -> RhinoUtils.wrap(value)
        else -> RhinoUtils.toJsValue(scope, value)
    }

    // Probing. zh-CN: 探测.

    /** The value a pickup found, or `null` for an empty result (nullish, `false`, `NaN`, an empty string, array or collection). zh-CN: 拾取到的值, 空结果 (空值, `false`, `NaN`, 空串, 空数组或空集合) 为 `null`. */
    private fun found(picked: Any?): Any? {
        val v = RhinoUtils.unwrap(picked)
        return when {
            v == null || v is Undefined -> null
            v == false -> null
            v is Double && v.isNaN() -> null
            v is CharSequence && v.isEmpty() -> null
            v is List<*> && v.isEmpty() -> null
            v is UiObjectCollection && v.isEmpty() -> null
            else -> v
        }
    }

    /** The value when it passes the truthiness rule of the synchronous `wait`, else `null`. zh-CN: 通过同步 `wait` 真值规则时的值, 否则 `null`. */
    private fun satisfied(checked: Any?): Any? = when {
        Global.isWaitConditionSatisfied(checked) -> RhinoUtils.unwrap(checked) ?: UNDEFINED
        else -> null
    }

    /**
     * Whether the loud path (the strategies, then the settings page for the user) already failed
     * during the current outage: a recovering wait probes again and again, and the user is asked
     * at most once per outage; the quiet attempts in between are [awaitService].
     * zh-CN: 当前中断期间 "响亮" 路径 (策略, 然后为用户打开设置页) 是否已失败过: 恢复中的等待会反复探测, 每次中断至多询问用户一次;
     * 其间的安静尝试是 [awaitService].
     */
    @Volatile
    private var loudEnsureFailed = false

    private fun ensureService(step: String, selector: String?) {
        val bridge = scriptRuntime.accessibilityBridge
        if (bridge.service != null) {
            loudEnsureFailed = false
            return
        }
        if (loudEnsureFailed) throw FlowError.A11yUnavailable(step, selector)
        try {
            bridge.ensureServiceStarted()
        } catch (e: ScriptInterruptedException) {
            throw e
        } catch (e: ScriptException) {
            loudEnsureFailed = true
            throw FlowError.A11yUnavailable(step, selector, e)
        }
        if (bridge.service == null) throw FlowError.A11yUnavailable(step, selector)
    }

    /** A script call on the calling thread, inside its Rhino context; exceptions propagate as thrown. zh-CN: 在调用线程的 Rhino 上下文内的脚本调用; 异常原样传播. */
    private fun callScript(fn: Callable, args: List<Any?>): Any? = RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
        val scope = scriptRuntime.topLevelScope
        val jsArgs = Array<Any?>(args.size) { toJs(cx, scope, args[it]) }
        val thisObj = Undefined.SCRIPTABLE_UNDEFINED
        when {
            RhinoScriptRuntime.hasTopCall(cx) -> fn.call(cx, scope, thisObj, jsArgs)
            else -> RhinoScriptRuntime.doTopCall(fn, cx, scope, thisObj, jsArgs, false)
        }
    }

    // Actions. zh-CN: 动作.

    private fun performAll(action: String, nodes: List<UiObject>, args: List<Any?>): Boolean? {
        if (nodes.isEmpty()) return null
        ensureService(action, null)
        return nodes.fold(true) { ok, node -> FlowActions.performNode(node, action, args) && ok }
    }

    private fun performPoint(action: String, x: Int, y: Int, args: List<Any?>): Boolean? {
        val automator = scriptRuntime.automator
        return when (action) {
            "click" -> automator.click(x, y)
            "longClick" -> automator.longClick(x, y)
            "press" -> automator.press(x, y, FlowActions.int(args, 0, "duration"))
            else -> null
        }
    }

    companion object {
        fun captureScriptStack(): List<StackTraceElement> = runCatching {
            if (Context.getCurrentContext() == null) emptyList()
            else EvaluatorException("Flow task creation").scriptStack.take(16).map {
                StackTraceElement("", it.functionName?.takeIf(String::isNotEmpty) ?: "<script>", it.fileName, it.lineNumber)
            }
        }.getOrDefault(emptyList())
    }

}

/**
 * The action steps of a Flow (roadmap Appendix A.3) and their arity, shared by the JavaScript
 * surface and the bindings: node actions are `UiObject` methods, global actions the `automator`
 * module's functions.
 * zh-CN: Flow 的动作步骤 (Roadmap 附录 A.3) 及其参数个数, 由 JavaScript 表面与 bindings 共用: 节点动作即 `UiObject` 方法, 全局动作即
 * `automator` 模块的函数.
 */
internal object FlowActions {

    /** Node actions and the accepted number of arguments (callbacks excluded). zh-CN: 节点动作及接受的参数个数 (不含回调). */
    val NODE_ACTIONS: Map<String, IntRange> = linkedMapOf(
        "click" to 0..0,
        "longClick" to 0..0,
        "clickBounds" to 0..2,
        "setText" to 1..1,
        "appendText" to 1..1,
        "clear" to 0..0,
        "paste" to 0..0,
        "copy" to 0..0,
        "cut" to 0..0,
        "select" to 0..0,
        "focus" to 0..0,
        "clearFocus" to 0..0,
        "scrollForward" to 0..0,
        "scrollBackward" to 0..0,
        "scrollUp" to 0..0,
        "scrollDown" to 0..0,
        "scrollLeft" to 0..0,
        "scrollRight" to 0..0,
        "expand" to 0..0,
        "collapse" to 0..0,
        "dismiss" to 0..0,
        "show" to 0..0,
        "contextClick" to 0..0,
        "imeEnter" to 0..0,
        "performAction" to 1..2,
    )

    /** Global actions and the accepted number of arguments. zh-CN: 全局动作及接受的参数个数. */
    val GLOBAL_ACTIONS: Map<String, IntRange> = linkedMapOf(
        "back" to 0..0,
        "home" to 0..0,
        "recents" to 0..0,
        "notifications" to 0..0,
        "quickSettings" to 0..0,
        "powerDialog" to 0..0,
        "splitScreen" to 0..0,
        "lockScreen" to 0..0,
        "takeScreenshot" to 0..0,
        "dismissNotificationShade" to 0..0,
        "press" to 1..3,
        "swipe" to 2..5,
        "gesture" to 2..Int.MAX_VALUE,
        "gestures" to 1..Int.MAX_VALUE,
    )

    fun performNode(node: UiObject, action: String, args: List<Any?>): Boolean = when (action) {
        "click" -> node.click()
        "longClick" -> node.longClick()
        "clickBounds" -> node.clickBounds(double(args, 0, "offsetX"), double(args, 1, "offsetY"))
        "setText" -> node.setText(string(args, 0, "text"))
        "appendText" -> node.setText(node.text() + string(args, 0, "text"))
        "clear" -> node.setText("")
        "paste" -> node.paste()
        "copy" -> node.copy()
        "cut" -> node.cut()
        "select" -> node.select()
        "focus" -> node.focus()
        "clearFocus" -> node.clearFocus()
        "scrollForward" -> node.scrollForward()
        "scrollBackward" -> node.scrollBackward()
        "scrollUp" -> node.scrollUp()
        "scrollDown" -> node.scrollDown()
        "scrollLeft" -> node.scrollLeft()
        "scrollRight" -> node.scrollRight()
        "expand" -> node.expand()
        "collapse" -> node.collapse()
        "dismiss" -> node.dismiss()
        "show" -> node.show()
        "contextClick" -> node.contextClick()
        "imeEnter" -> node.imeEnter()
        "performAction" -> when (val extra = args.getOrNull(1)) {
            null, is Undefined -> node.performAction(int(args, 0, "action"))
            is Map<*, *> -> node.performAction(int(args, 0, "action"), bundleOf(extra))
            else -> throw IllegalArgumentException("Argument \"arguments\" of performAction must be an object, got $extra")
        }
        else -> throw IllegalArgumentException("Unknown node action \"$action\"")
    }

    fun performGlobal(scriptRuntime: ScriptRuntime, action: String, args: List<Any?>): Boolean {
        val automator = scriptRuntime.automator
        val array = args.toTypedArray()
        return when (action) {
            "back" -> automator.back()
            "home" -> automator.home()
            "recents" -> automator.recents()
            "notifications" -> automator.notifications()
            "quickSettings" -> automator.quickSettings()
            "powerDialog" -> automator.powerDialog()
            "splitScreen" -> automator.splitScreen()
            "lockScreen" -> automator.lockScreen()
            "takeScreenshot" -> automator.takeScreenshot()
            "dismissNotificationShade" -> automator.dismissNotificationShade()
            "press" -> Automator.press(scriptRuntime, array)
            "swipe" -> Automator.swipe(scriptRuntime, array)
            "gesture" -> Automator.gesture(scriptRuntime, array)
            "gestures" -> Automator.gestures(scriptRuntime, array)
            else -> throw IllegalArgumentException("Unknown global action \"$action\"")
        }
    }

    /** A point target: `Point` (Android or OpenCV), `[x, y]`, or `{ x, y }`. zh-CN: 坐标点目标: `Point` (Android 或 OpenCV), `[x, y]` 或 `{ x, y }`. */
    fun pointOf(value: Any?): Pair<Int, Int>? = when (value) {
        is android.graphics.Point -> value.x to value.y
        is org.opencv.core.Point -> value.x.toInt() to value.y.toInt()
        is List<*> -> when {
            value.size == 2 && value.all { RhinoUtils.unwrap(it) is Number } -> Context.toNumber(value[0]).toInt() to Context.toNumber(value[1]).toInt()
            else -> null
        }
        is Map<*, *> -> {
            val x = value["x"]
            val y = value["y"]
            when {
                x is Number && y is Number -> x.toInt() to y.toInt()
                else -> null
            }
        }
        else -> null
    }

    fun int(args: List<Any?>, index: Int, what: String): Int {
        val n = Context.toNumber(args.getOrNull(index))
        require(!n.isNaN()) { "Argument \"$what\" must be a number, got ${args.getOrNull(index)}" }
        return n.toInt()
    }

    private fun double(args: List<Any?>, index: Int, what: String): Double {
        val value = args.getOrNull(index)
        if (value == null || value is Undefined) return 0.0
        val n = Context.toNumber(value)
        require(!n.isNaN()) { "Argument \"$what\" must be a number, got $value" }
        return n
    }

    private fun string(args: List<Any?>, index: Int, what: String): String {
        val value = args.getOrNull(index)
        require(value != null && value !is Undefined) { "Argument \"$what\" is required" }
        return Context.toString(value)
    }

    private fun bundleOf(map: Map<*, *>): Bundle = Bundle().apply {
        map.forEach { (key, value) ->
            val name = key.toString()
            when (value) {
                is Boolean -> putBoolean(name, value)
                is Number -> if (value.toDouble() == value.toDouble().toInt().toDouble()) putInt(name, value.toInt()) else putFloat(name, value.toFloat())
                is CharSequence -> putCharSequence(name, value)
                else -> throw IllegalArgumentException("Unsupported argument \"$name\" of performAction: $value")
            }
        }
    }

}
