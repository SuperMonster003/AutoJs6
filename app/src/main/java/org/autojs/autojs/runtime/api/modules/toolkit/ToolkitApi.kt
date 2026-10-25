package org.autojs.autojs.runtime.api.modules.toolkit

import android.os.Looper
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.core.automator.toolkit.AppLauncher
import org.autojs.autojs.core.automator.toolkit.Humanize
import org.autojs.autojs.core.automator.toolkit.ListCollector
import org.autojs.autojs.core.automator.toolkit.PopupDismisser
import org.autojs.autojs.core.automator.toolkit.ScrollSearch
import org.autojs.autojs.core.automator.toolkit.SmartClick
import org.autojs.autojs.core.automator.toolkit.TextEntry
import org.autojs.autojs.core.automator.toolkit.Toggle
import org.autojs.autojs.core.automator.toolkit.Toolkit
import org.autojs.autojs.core.looper.Loopers
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.flow.FlowExecutor
import org.autojs.autojs.runtime.api.modules.flow.FlowJsErrors
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.VarScope
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The JavaScript surface of the toolkit (roadmap P4.2, Appendix B) over one [RuntimeToolkit]:
 * the synchronous functions of the `automator` module and the global scope (D10), the Flow steps
 * (`.smartClick()`, `.scrollUntil()`, ...) and the `flow.*` roots. One [Prepared] per call: the
 * arguments are parsed on the calling script thread (so a bad option fails right there), the
 * body runs on the calling thread for the synchronous forms and on the Flow worker for the
 * others. Results are plain JavaScript objects (see [Results]); every failure is a `FlowError`,
 * thrown into the script by the synchronous forms and rejecting the chain otherwise.
 * zh-CN: 一个 [RuntimeToolkit] 上的工具集 JavaScript 表面 (Roadmap P4.2, 附录 B): `automator` 模块与全局的同步函数 (D10), Flow 步骤
 * (`.smartClick()`, `.scrollUntil()`, ...) 与 `flow.*` 起点. 每次调用一个 [Prepared]: 参数在调用方脚本线程上解析 (错误的选项当场失败),
 * 主体在同步形式下于调用线程运行, 其余形式下于 Flow 工作线程运行. 结果为普通 JavaScript 对象 (见 [Results]); 一切失败都是 `FlowError`,
 * 同步形式抛入脚本, 其余形式使链拒绝.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal class ToolkitApi(private val owner: RuntimeToolkit) {

    private val scriptRuntime: ScriptRuntime get() = owner.scriptRuntime

    private val kit: Toolkit<UiObject> get() = owner.toolkit

    private val flow: FlowExecutor get() = FlowExecutor.of(scriptRuntime)

    private val scope: VarScope get() = scriptRuntime.topLevelScope

    val arguments: ToolkitArguments by lazy { ToolkitArguments(flow.defaults) { fn, args -> owner.bindings.callScript(fn, args) } }

    private val results = Results()

    /** Where a call comes from, which decides its positionals and defaults. zh-CN: 调用来自何处, 决定其位置参数与默认值. */
    enum class Mode {

        /** `automator.x()` / the global: single looks by default, no callbacks, blocks the calling thread. zh-CN: `automator.x()` / 全局: 默认单次查找, 无回调, 阻塞调用线程. */
        SYNC,

        /** `flow.x()`: the target is the first argument and is waited for by the flow defaults. zh-CN: `flow.x()`: 目标为首个参数, 按 flow 默认值等待. */
        ROOT,

        /** `.x()` on a Flow: the current value is the target. zh-CN: Flow 上的 `.x()`: 当前值即目标. */
        CHAINED,

    }

    /** A parsed call: the trace detail, the trailing callbacks and the body. zh-CN: 解析后的调用: 追踪细节, 尾随回调与主体. */
    class Prepared(val detail: String?, val onOk: Callable?, val onErr: Callable?, val run: (value: Any?, scope: FlowScope) -> Any?)

    // The synchronous surface (roadmap D10). zh-CN: 同步表面 (Roadmap D10).

    fun smartClick(args: Array<out Any?>): Any? = sync(SmartClick.STEP, args)

    fun smartClickBounds(args: Array<out Any?>): Any? = sync(SmartClick.STEP_BOUNDS, args)

    fun clickIfExists(args: Array<out Any?>): Any? = sync(Toolkit.STEP_CLICK_IF_EXISTS, args)

    fun clickBoundsIfExists(args: Array<out Any?>): Any? = sync(Toolkit.STEP_CLICK_BOUNDS_IF_EXISTS, args)

    fun clickAny(args: Array<out Any?>): Any? = sync(Toolkit.STEP_CLICK_ANY, args)

    fun clickBoundsAny(args: Array<out Any?>): Any? = sync(Toolkit.STEP_CLICK_BOUNDS_ANY, args)

    fun findAny(args: Array<out Any?>): Any? = sync(STEP_FIND_ANY, args)

    fun scrollUntil(args: Array<out Any?>): Any? = sync(ScrollSearch.STEP, args)

    fun typeInto(args: Array<out Any?>): Any? = sync(TextEntry.STEP, args)

    fun dismissPopups(args: Array<out Any?>): Any? = sync(PopupDismisser.STEP, args)

    fun collectList(args: Array<out Any?>): Any? = sync(ListCollector.STEP, args)

    fun launchAndWait(args: Array<out Any?>): Any? = sync(AppLauncher.STEP_LAUNCH, args)

    fun backUntil(args: Array<out Any?>): Any? = sync(AppLauncher.STEP_BACK, args)

    fun backToApp(args: Array<out Any?>): Any? = sync(AppLauncher.STEP_BACK_TO_APP, args)

    fun toggle(args: Array<out Any?>): Any? = sync(Toggle.STEP, args)

    fun retry(args: Array<out Any?>): Any? = sync(STEP_RETRY, args)

    /** Runs a tool on the calling thread; a `FlowError` becomes the script's `FlowError` object. zh-CN: 在调用线程上运行工具; `FlowError` 成为脚本的 `FlowError` 对象. */
    private fun sync(name: String, args: Array<out Any?>): Any? {
        require(!RhinoUtils.isUiThread()) { str(R.string.error_function_called_in_ui_thread, name) }
        val prepared = prepare(Mode.SYNC, name, args)
        return RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
            try {
                prepared.run(null, FlowScope.NONE)
            } catch (e: FlowError) {
                FlowJsErrors.throwInto(cx, scope, e)
            }
        }
    }

    // The Flow forms. zh-CN: Flow 形式.

    /** The step of a Flow form: parsed now on the script thread, run later on the worker. zh-CN: Flow 形式的步骤: 此刻在脚本线程解析, 稍后在工作线程运行. */
    fun step(name: String, args: Array<out Any?>, chained: Boolean): Pair<FlowStep.Tool, Pair<Callable?, Callable?>> {
        val prepared = prepare(if (chained) Mode.CHAINED else Mode.ROOT, name, args)
        // The body runs on a Flow worker, which has no top call of its own. zh-CN: 主体在 Flow 工作线程上运行, 它没有自己的顶层调用.
        return FlowStep.Tool(name, prepared.detail) { value, scope -> owner.bindings.withTopCall { prepared.run(value, scope) } } to (prepared.onOk to prepared.onErr)
    }

    private fun prepare(mode: Mode, name: String, args: Array<out Any?>): Prepared = when (name) {
        SmartClick.STEP, SmartClick.STEP_BOUNDS -> smartClick(mode, args, name)
        Toolkit.STEP_CLICK_IF_EXISTS, Toolkit.STEP_CLICK_BOUNDS_IF_EXISTS -> clickIfExists(mode, args, name)
        Toolkit.STEP_CLICK_ANY, Toolkit.STEP_CLICK_BOUNDS_ANY -> clickAny(mode, args, name)
        STEP_FIND_ANY -> findAny(mode, args)
        ScrollSearch.STEP -> scrollUntil(mode, args)
        TextEntry.STEP -> typeInto(mode, args)
        PopupDismisser.STEP -> dismissPopups(mode, args)
        ListCollector.STEP -> collectList(mode, args)
        AppLauncher.STEP_LAUNCH -> launchAndWait(mode, args)
        AppLauncher.STEP_BACK -> backUntil(mode, args)
        AppLauncher.STEP_BACK_TO_APP -> backToApp(mode, args)
        Toggle.STEP -> toggle(mode, args)
        STEP_RETRY -> retry(mode, args)
        else -> throw IllegalArgumentException("Unknown tool: $name")
    }

    // The tools. zh-CN: 各工具.

    /** `smartClick(target, options?)` / `.smartClick(options?)`: `{ ok, method, target, verified }`. */
    private fun smartClick(mode: Mode, args: Array<out Any?>, name: String): Prepared {
        val boundsOnly = name == SmartClick.STEP_BOUNDS
        val call = split(mode, name, args, positional = if (mode == Mode.CHAINED) 0 else 1)
        val r = arguments.reader(call, if (boundsOnly) ToolkitArguments.CLICK_BOUNDS_KEYS else ToolkitArguments.CLICK_KEYS)
        val target = if (mode == Mode.CHAINED) null else call.require(0, "target")
        val options = arguments.smartClick(r).copy(boundsOnly = boundsOnly)
        val locate = r.locate(defaultTimeout(mode))
        val root = r.root()
        return Prepared(detail(target), call.onOk, call.onErr) { value, scope ->
            val t = if (mode == Mode.CHAINED) targetOf(value, name) else target
            val effectiveRoot = root ?: rootOf(scope)
            val node = kit.locate(t, effectiveRoot, name, locate)
            results.smartClick(kit.smartClick.click(node, options, name, effectiveRoot, describe(t)))
        }
    }

    /** `clickIfExists(target, options? | timeout?)`: whether it was clicked. */
    private fun clickIfExists(mode: Mode, args: Array<out Any?>, name: String): Prepared {
        val boundsOnly = name == Toolkit.STEP_CLICK_BOUNDS_IF_EXISTS
        val call = split(mode, name, args, positional = 1)
        val r = arguments.reader(call, if (boundsOnly) ToolkitArguments.CLICK_BOUNDS_KEYS else ToolkitArguments.CLICK_KEYS, shorthandKey = "timeout")
        val target = call.require(0, "target")
        val options = arguments.smartClick(r).copy(boundsOnly = boundsOnly)
        val locate = r.locate(defaultTimeout(mode))
        val root = r.root()
        return Prepared(detail(target), call.onOk, call.onErr) { _, scope ->
            kit.clickIfExists(target, root ?: rootOf(scope), locate, options, name) != null
        }
    }

    /** `clickAny(targets, options? | timeout?)`: `{ index, target, node, method, verified }` or `null`. */
    private fun clickAny(mode: Mode, args: Array<out Any?>, name: String): Prepared {
        val boundsOnly = name == Toolkit.STEP_CLICK_BOUNDS_ANY
        val call = split(mode, name, args, positional = 1)
        val r = arguments.reader(call, if (boundsOnly) ToolkitArguments.CLICK_BOUNDS_KEYS else ToolkitArguments.CLICK_KEYS, shorthandKey = "timeout")
        val targets = arguments.targets(call.require(0, "targets"), call.name)
        val options = arguments.smartClick(r).copy(boundsOnly = boundsOnly)
        val locate = r.locate(defaultTimeout(mode))
        val root = r.root()
        return Prepared(detail(targets), call.onOk, call.onErr) { _, scope ->
            kit.clickAny(targets, root ?: rootOf(scope), locate, options, name)?.let { results.clicked(it) }
        }
    }

    /** `findAny(targets, options? | timeout?)`: `{ index, target, node }` or `null`. */
    private fun findAny(mode: Mode, args: Array<out Any?>): Prepared {
        val call = split(mode, STEP_FIND_ANY, args, positional = 1)
        val r = arguments.reader(call, ToolkitArguments.FIND_KEYS, shorthandKey = "timeout")
        val targets = arguments.targets(call.require(0, "targets"), call.name)
        val locate = r.locate(defaultTimeout(mode))
        val root = r.root()
        return Prepared(detail(targets), call.onOk, call.onErr) { _, scope ->
            kit.findAny(targets, root ?: rootOf(scope), locate)?.let { results.found(it) }
        }
    }

    /** `scrollUntil(target, options?)` / `.scrollUntil(target, options?)` (the value is the container): the node found. */
    private fun scrollUntil(mode: Mode, args: Array<out Any?>): Prepared {
        val call = split(mode, ScrollSearch.STEP, args, positional = 1)
        val r = arguments.reader(call, ToolkitArguments.SCROLL_KEYS)
        val target = call.require(0, "target")
        val container = r.raw("container")?.let { RhinoUtils.unwrap(it) }
        val options = arguments.scrollSearch(r, container, r.locate(defaultTimeout(mode)))
        val root = r.root()
        return Prepared(detail(target), call.onOk, call.onErr) { value, scope ->
            val effective = when {
                container != null || mode != Mode.CHAINED -> options
                else -> options.copy(container = value)
            }
            toJs(kit.scrollSearch.scrollUntil(target, effective, root ?: rootOf(scope), ScrollSearch.STEP).node)
        }
    }

    /** `typeInto(target, text, options?)` / `.typeInto(text, options?)`: the node typed into. */
    private fun typeInto(mode: Mode, args: Array<out Any?>): Prepared {
        val chained = mode == Mode.CHAINED
        val call = split(mode, TextEntry.STEP, args, positional = if (chained) 1 else 2)
        val r = arguments.reader(call, ToolkitArguments.TEXT_KEYS)
        val target = if (chained) null else call.positional(0)
        val text = Context.toString(call.require(if (chained) 0 else 1, "text"))
        val options = arguments.textEntry(r, r.locate(defaultTimeout(mode)))
        val root = r.root()
        return Prepared(detail(target ?: text), call.onOk, call.onErr) { value, scope ->
            val t = if (chained) value else target
            toJs(kit.textEntry.typeInto(editableTarget(t), text, options, root ?: rootOf(scope), TextEntry.STEP).node)
        }
    }

    /**
     * `dismissPopups(targets, options? | timeout?)`: with `once` (default) the popup closed or `null`,
     * with `once: false` all of them, with `watch: true` (synchronous only) a guard handle; the
     * chained form passes the value through after closing.
     * zh-CN: `dismissPopups(targets, options? | timeout?)`: `once` (默认) 时为关闭的弹窗或 `null`, `once: false` 时为全部, `watch: true`
     * (仅同步) 时为守护句柄; 链式形式关闭后透传值.
     */
    private fun dismissPopups(mode: Mode, args: Array<out Any?>): Prepared {
        val call = split(mode, PopupDismisser.STEP, args, positional = 1)
        val r = arguments.reader(call, ToolkitArguments.POPUP_KEYS, shorthandKey = "timeout")
        val targets = arguments.targets(call.require(0, "targets"), call.name)
        val once = r.bool("once", true)
        val watch = r.bool("watch", false)
        val onDismissed = r.fn("onDismissed")
        val onError = r.fn("onError")
        require(!watch || mode == Mode.SYNC) { "Option \"watch\" is only available on the synchronous dismissPopups; a Flow form runs once" }
        require(watch || onDismissed == null && onError == null) { "Options \"onDismissed\" / \"onError\" need \"watch: true\" for ${call.name}" }
        val options = arguments.popups(r, r.locate(defaultTimeout(mode)))
        val root = r.root()
        return Prepared(detail(targets), call.onOk, call.onErr) { value, scope ->
            val effectiveRoot = root ?: rootOf(scope)
            when {
                watch -> guard(kit.popups.watch(targets, options, effectiveRoot), onDismissed, onError)
                mode == Mode.CHAINED -> {
                    if (once) kit.popups.dismissOnce(targets, options, effectiveRoot) else kit.popups.dismissAll(targets, options, effectiveRoot)
                    value
                }
                once -> kit.popups.dismissOnce(targets, options, effectiveRoot)?.let { results.dismissed(it) }
                else -> toJs(kit.popups.dismissAll(targets, options, effectiveRoot).map { results.dismissed(it) })
            }
        }
    }

    /** `collectList(container, item, options?)` / `.collectList(item, options?)` (the value is the container): the items. */
    private fun collectList(mode: Mode, args: Array<out Any?>): Prepared {
        val chained = mode == Mode.CHAINED
        val call = split(mode, ListCollector.STEP, args, positional = if (chained) 1 else 2)
        val r = arguments.reader(call, ToolkitArguments.COLLECT_KEYS)
        val container = if (chained) null else call.positional(0)?.let { RhinoUtils.unwrap(it) }
        val item = call.require(if (chained) 0 else 1, "item")
        val options = arguments.listCollector(r, r.locate(defaultTimeout(mode)))
        val root = r.root()
        return Prepared(detail(item), call.onOk, call.onErr) { value, scope ->
            val c = if (chained) value else container
            toJs(kit.listCollector.collect(c, item, options, root ?: rootOf(scope), ListCollector.STEP).items)
        }
    }

    /** `launchAndWait(app, options?)`: `{ packageName, launched, elapsed, attempts }`. */
    private fun launchAndWait(mode: Mode, args: Array<out Any?>): Prepared {
        val call = split(mode, AppLauncher.STEP_LAUNCH, args, positional = 1)
        val r = arguments.reader(call, ToolkitArguments.LAUNCH_KEYS, shorthandKey = "timeout")
        val app = call.require(0, "app")
        val options = arguments.launch(r)
        return Prepared(detail(app), call.onOk, call.onErr) { _, _ ->
            results.launched(kit.apps.launchAndWait(app, options, AppLauncher.STEP_LAUNCH))
        }
    }

    /** `backUntil(cond, options? | maxTimes?)`: what the condition yielded. */
    private fun backUntil(mode: Mode, args: Array<out Any?>): Prepared {
        val call = split(mode, AppLauncher.STEP_BACK, args, positional = 1)
        val r = arguments.reader(call, ToolkitArguments.BACK_KEYS, shorthandKey = "maxTimes")
        val cond = call.require(0, "cond")
        val options = arguments.back(r)
        val root = r.root()
        return Prepared(detail(cond), call.onOk, call.onErr) { _, scope ->
            toJs(kit.apps.backUntil(cond, options, root ?: rootOf(scope), AppLauncher.STEP_BACK).value)
        }
    }

    /** `backToApp(app, options? | maxTimes?)`: the package name reached. */
    private fun backToApp(mode: Mode, args: Array<out Any?>): Prepared {
        val call = split(mode, AppLauncher.STEP_BACK_TO_APP, args, positional = 1)
        val r = arguments.reader(call, ToolkitArguments.BACK_KEYS, shorthandKey = "maxTimes")
        val app = call.require(0, "app")
        val options = arguments.back(r)
        return Prepared(detail(app), call.onOk, call.onErr) { _, _ ->
            toJs(kit.apps.backToApp(app, options, AppLauncher.STEP_BACK_TO_APP).value)
        }
    }

    /** `toggle(target, checked, options?)` / `.toggle(checked, options?)`: `{ node, changed, checked, method }`. */
    private fun toggle(mode: Mode, args: Array<out Any?>): Prepared {
        val chained = mode == Mode.CHAINED
        val call = split(mode, Toggle.STEP, args, positional = if (chained) 1 else 2)
        val r = arguments.reader(call, ToolkitArguments.TOGGLE_KEYS)
        val target = if (chained) null else call.require(0, "target")
        val checked = Context.toBoolean(call.require(if (chained) 0 else 1, "checked"))
        val options = arguments.toggle(r, r.locate(defaultTimeout(mode)))
        val root = r.root()
        return Prepared(detail(target), call.onOk, call.onErr) { value, scope ->
            val t = if (chained) targetOf(value, Toggle.STEP) else target
            results.toggled(kit.toggle.set(t, checked, options, root ?: rootOf(scope), Toggle.STEP))
        }
    }

    /** `retry(fn, options? | times?)` (synchronous only): what `fn(attempt)` returned. */
    private fun retry(mode: Mode, args: Array<out Any?>): Prepared {
        require(mode == Mode.SYNC) { "retry(fn) is synchronous; a Flow reruns its previous step with the retry(times, delay) step" }
        val call = split(mode, STEP_RETRY, args, positional = 1)
        val r = arguments.reader(call, ToolkitArguments.RETRY_KEYS, shorthandKey = "times")
        val fn = call.require(0, "fn") as? Callable ?: throw WrappedIllegalArgumentException("Argument \"fn\" must be a function for ${call.name}, got ${call.positional(0).jsBrief()}")
        val options = arguments.retry(r)
        return Prepared(null, null, null) { _, _ ->
            toJs(kit.retry(options) { attempt -> owner.bindings.callScript(fn, listOf(attempt.toDouble())) })
        }
    }

    // Helpers. zh-CN: 辅助.

    private fun split(mode: Mode, name: String, args: Array<out Any?>, positional: Int): ToolkitArguments.Call =
        arguments.split(if (mode == Mode.ROOT) "flow.$name" else name, args, positional, callbacks = mode != Mode.SYNC)

    /** A root form waits by the flow defaults; the others look once unless told otherwise. zh-CN: 起点形式按 flow 默认值等待; 其余形式默认只看一次. */
    private fun defaultTimeout(mode: Mode): Long = if (mode == Mode.ROOT) flow.defaults.timeout else 0L

    private fun rootOf(scope: FlowScope): UiObject? = scope.root as? UiObject

    /** The value of a chained step as the tool's target; `null` has none. zh-CN: 链式步骤的值作为工具的目标; `null` 即没有目标. */
    private fun targetOf(value: Any?, step: String): Any = value ?: throw FlowError.InvalidTarget(step, null, REASON_NO_TARGET)

    private fun describe(target: Any?): String? = target?.let { owner.bindings.describe(it) }

    private fun detail(target: Any?): String? = when (target) {
        null -> null
        is List<*> -> target.joinToString(", ", "[", "]") { describe(it) ?: "?" }
        else -> describe(target)
    }

    /** A JavaScript number is an editable index for `typeInto`; anything else is a target as is. zh-CN: `typeInto` 中 JavaScript 数字为可编辑控件索引; 其余为原样的目标. */
    private fun editableTarget(target: Any?): Any? = when (val t = RhinoUtils.unwrap(target)) {
        is Number -> t.toInt()
        else -> t
    }

    /** Inside the engine's Rhino context: a worker thread has none of its own, and a bare one cannot build arrays. zh-CN: 在引擎的 Rhino 上下文内: 工作线程没有自己的上下文, 而裸上下文无法构建数组. */
    private fun toJs(value: Any?): Any? = RhinoUtils.withRhinoContext(scriptRuntime) { _ -> RhinoUtils.toJsValue(scope, value) }

    /**
     * The handle of a running popup guard (`dismissPopups({ watch: true })`): `stop()`, `trigger()`,
     * `isRunning`, `count`, `passes`, `dismissed`. It keeps the script alive (a wait token) until
     * stopped, and is stopped with the runtime; `onDismissed` / `onError` are called on the creating
     * script thread.
     * zh-CN: 运行中弹窗守护的句柄 (`dismissPopups({ watch: true })`): `stop()`, `trigger()`, `isRunning`, `count`, `passes`, `dismissed`.
     * 停止前保持脚本存活 (等待令牌), 随运行时停止; `onDismissed` / `onError` 在创建它的脚本线程上调用.
     */
    private fun guard(guard: PopupDismisser.Guard<UiObject>, onDismissed: Callable?, onError: Callable?): Scriptable {
        val handle = GuardHandle(guard)
        val lane = runCatching { flow.scriptLane() }.getOrNull()
        guard.onDismissed = onDismissed?.let { fn ->
            { d -> lane?.post { owner.bindings.callScript(fn, listOf(results.dismissed(d))) } }
        }
        guard.onError = { e ->
            when {
                onError != null && lane != null -> lane.post {
                    RhinoUtils.withRhinoContext(scriptRuntime) { cx -> owner.bindings.callScript(onError, listOf(FlowJsErrors.toJs(cx, scope, e))) }
                }
                else -> scriptRuntime.console.warn("dismissPopups guard: ${e.message ?: e.toString()}")
            }
        }
        owner.register(handle)
        val attributes = DONTENUM or READONLY or PERMANENT
        return RhinoUtils.newNativeObject(scope).apply {
            defineProperty("stop", newBaseFunction(scope, "stop", { _ -> handle.close(); UNDEFINED }, NOT_CONSTRUCTABLE), attributes)
            defineProperty("trigger", newBaseFunction(scope, "trigger", { _ -> guard.trigger(); UNDEFINED }, NOT_CONSTRUCTABLE), attributes)
            defineProperty("isRunning", { guard.isRunning }, null, READONLY or PERMANENT)
            defineProperty("count", { guard.count.toDouble() }, null, READONLY or PERMANENT)
            defineProperty("passes", { guard.passes.toDouble() }, null, READONLY or PERMANENT)
            defineProperty("dismissed", { toJs(guard.dismissed.map { results.dismissed(it) }) }, null, READONLY or PERMANENT)
            defineProperty("toString", newBaseFunction(scope, "toString", { _ -> "PopupGuard(${if (guard.isRunning) "running" else "stopped"}, ${guard.count} dismissed)" }, NOT_CONSTRUCTABLE), attributes)
        }
    }

    /** A guard plus the wait token that keeps the script alive while it runs. zh-CN: 守护及其运行期间保持脚本存活的等待令牌. */
    private inner class GuardHandle(private val guard: PopupDismisser.Guard<UiObject>) : AutoCloseable {

        private val token: Loopers.WaitToken? = when {
            Looper.myLooper() == null -> null
            else -> runCatching { scriptRuntime.loopers.waitWhenIdleToken() }.getOrNull()
        }

        private val closed = AtomicBoolean()

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            guard.stop()
            token?.release()
            owner.unregister(this)
        }

    }

    /** The JavaScript shapes of the outcomes. zh-CN: 各结果的 JavaScript 形态. */
    private inner class Results {

        private fun obj(vararg pairs: Pair<String, Any?>): NativeObject = RhinoUtils.withRhinoContext(scriptRuntime) { _ ->
            RhinoUtils.newNativeObject(scope).apply {
                pairs.forEach { (key, value) -> put(key, this, js(value)) }
            }
        }

        private fun js(value: Any?): Any? = when (value) {
            null -> null
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            else -> toJs(value)
        }

        fun smartClick(o: SmartClick.Outcome<UiObject>): NativeObject = obj("ok" to true, "method" to o.method.label, "target" to o.target, "verified" to o.verified)

        fun found(f: Toolkit.Found<UiObject>): NativeObject = obj("index" to f.index, "target" to f.target, "node" to f.node)

        fun clicked(c: Toolkit.Clicked<UiObject>): NativeObject =
            obj("index" to c.found.index, "target" to c.found.target, "node" to c.found.node, "method" to c.outcome.method.label, "verified" to c.outcome.verified)

        fun dismissed(d: PopupDismisser.Dismissed<UiObject>): NativeObject =
            obj("index" to d.index, "target" to d.target, "node" to d.node, "method" to d.outcome.method.label, "verified" to d.outcome.verified)

        fun launched(l: AppLauncher.Launched): NativeObject = obj("packageName" to l.packageName, "launched" to l.launched, "elapsed" to l.elapsed, "attempts" to l.attempts)

        fun toggled(t: Toggle.Outcome<UiObject>): NativeObject = obj("node" to t.node, "changed" to t.changed, "checked" to t.checked, "method" to t.click?.method?.label)

    }

    companion object {

        const val STEP_FIND_ANY = "findAny"

        const val STEP_RETRY = "retry"

        /** A chained tool step got `null` as its value. zh-CN: 链式工具步骤得到的值为 `null`. */
        const val REASON_NO_TARGET = "noTarget"

        /** The chain methods of `Flow.prototype` (roadmap Appendix B). zh-CN: `Flow.prototype` 的链式方法 (Roadmap 附录 B). */
        val CHAIN_STEPS = listOf(SmartClick.STEP, SmartClick.STEP_BOUNDS, Toolkit.STEP_CLICK_IF_EXISTS, Toolkit.STEP_CLICK_BOUNDS_IF_EXISTS, Toolkit.STEP_CLICK_ANY, Toolkit.STEP_CLICK_BOUNDS_ANY, STEP_FIND_ANY, ScrollSearch.STEP, TextEntry.STEP, PopupDismisser.STEP, ListCollector.STEP, AppLauncher.STEP_LAUNCH, AppLauncher.STEP_BACK, AppLauncher.STEP_BACK_TO_APP, Toggle.STEP)

        /** The synchronous functions on `automator` and the global scope (D10). zh-CN: `automator` 与全局上的同步函数 (D10). */
        val SYNC_NAMES = (CHAIN_STEPS + listOf(Toolkit.STEP_CLICK_IF_EXISTS, Toolkit.STEP_CLICK_ANY, STEP_FIND_ANY, STEP_RETRY)).toSet()

        /** `{ offset: [x, y], delay: [min, max] }` of [humanize], for `flow.defaults()`. zh-CN: [humanize] 的 `{ offset: [x, y], delay: [min, max] }`, 供 `flow.defaults()`. */
        fun humanizeSnapshot(scope: VarScope, humanize: Humanize): NativeObject = RhinoUtils.newNativeObject(scope).apply {
            put("offset", this, RhinoUtils.toJsValue(scope, listOf(humanize.offsetX.toDouble(), humanize.offsetY.toDouble())))
            put("delay", this, RhinoUtils.toJsValue(scope, listOf(humanize.minDelay.toDouble(), humanize.maxDelay.toDouble())))
        }

    }

}
