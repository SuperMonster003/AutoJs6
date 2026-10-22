package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.flow.CallbackKind
import org.autojs.autojs.core.automator.flow.CombinatorKind
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.flow.FlowApi
import org.autojs.autojs.runtime.api.augment.flow.FlowExecutor
import org.autojs.autojs.runtime.api.augment.flow.WaitKind
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.runtime.api.modules.toolkit.ToolkitArgs
import org.mozilla.javascript.Callable
import org.mozilla.javascript.ScriptableObject

/**
 * The `flow` module on the new script module layer (roadmap P3.3): the asynchronous, chainable waits of the current module
 * (callable as `flow.wait`), with the same names, aliases and global entries (`waitAsync`, `waitThenClick` / `clickWait`,
 * `waitForStable` and the other wait forms, defined by the install hook in the current module's order, and `wait.async`
 * on the synchronous global `wait`), the toolkit roots, the event-driven waits, `of` / `all` / `race` /
 * `any`, `defaults` / `trace` / `cancelAll` and the getters `Flow` / `pending` / `workers`. The executor, the steps and the
 * returned flow objects are the current ones (`augment/flow`, to be moved out by P5.2).
 *
 * Arguments are checked before the current parser runs ([FlowArgs], [ToolkitArgs]): a count or a top-level shape the
 * documented signatures do not accept is a TypeError, a timeout, interval or delay that is NaN or negative a RangeError
 * (D10; the current module threw IllegalArgumentException). The fields of an options object and the grammar of a
 * condition or target are still checked by the current parser (appendix C Q65, appendix E "P3.3").
 *
 * zh-CN: 新脚本模块层上的 flow 模块 (P3.3): 现有模块的异步链式等待 (可调用, 同 flow.wait), 名称, 别名与全局入口相同 (waitAsync, waitThenClick / clickWait,
 * waitForStable 等等待形式由安装钩子按现有模块的顺序定义, 另有同步全局 wait 上的 wait.async), 另有工具起点, 事件驱动等待, of / all / race / any,
 * defaults / trace / cancelAll 与 getter Flow / pending / workers. 执行器, 步骤与返回的 Flow 对象沿用现有实现 (augment/flow, 由 P5.2 迁出).
 * 参数在现有解析器之前检查 (FlowArgs, ToolkitArgs): 文档签名不接受的个数或顶层形态为 TypeError, NaN 或负数的超时, 间隔与延迟为 RangeError (D10; 现有模块抛
 * IllegalArgumentException). 选项对象的字段与条件或目标的语法仍由现有解析器检查 (附录 C Q65, 附录 E "P3.3").
 */
@ScriptModule(name = "flow")
class FlowModule(private val runtime: ScriptRuntime) {

    private val api: FlowApi get() = FlowExecutor.of(runtime).api

    /**
     * The global forms and `wait.async(...)` on the synchronous global `wait` (D1). The global forms are lazy functions of
     * their own (named as the current module names them, `clickWait` is a `waitThenClick`), defined here because they come
     * in an order of their own, not the module's; reading the global `wait` resolves only that slot.
     *
     * zh-CN: 全局形式与同步全局 wait 上的 wait.async(...) (D1). 全局形式为独立的惰性函数 (名称同现有模块, clickWait 即 waitThenClick), 因其顺序与模块成员不同而在此定义;
     * 读取全局 wait 只解析该槽.
     */
    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) {
        globalForms().forEach { (name, function, call) ->
            installation.defineLazy(name, ScriptAttributes.PERMANENT) { installation.function(function, 0) { _, _, _, args -> call(args) } }
        }
        val wait = installation.global.get("wait", installation.global) as? ScriptableObject ?: return
        val async = installation.function("async", 0) { _, _, _, args -> waitAsync(args) }
        wait.defineProperty("async", async, ScriptAttributes.READONLY or ScriptAttributes.DONTENUM or ScriptAttributes.PERMANENT)
    }

    @ScriptInvoke
    fun invoke(vararg args: Any?): Any = wait(*args)

    @ScriptFunction(aliases = ["waitAsync"])
    fun wait(vararg args: Any?): Any = waitOf(WaitKind.WAIT, "flow.wait", args)

    @ScriptFunction
    fun waitUntil(vararg args: Any?): Any = waitOf(WaitKind.UNTIL, "flow.waitUntil", args)

    @ScriptFunction
    fun waitWhile(vararg args: Any?): Any = waitOf(WaitKind.WHILE, "flow.waitWhile", args)

    @ScriptFunction
    fun whenPresent(cond: Any, handler: Callable, options: Any? = null): Any {
        FlowArgs.optionsArg("flow.whenPresent", "options", options)
        return api.whenPresent(null, if (isNullish(options)) arrayOf(cond, handler) else arrayOf(cond, handler, options))
    }

    @ScriptFunction
    fun repeatUntil(action: Callable, cond: Any, options: Any? = null): Any {
        FlowArgs.optionsArg("flow.repeatUntil", "options", options)
        return api.repeatUntil(null, if (isNullish(options)) arrayOf(action, cond) else arrayOf(action, cond, options))
    }

    @ScriptFunction
    fun waitForStable(vararg args: Any?): Any = waitOf(WaitKind.STABLE, "flow.waitForStable", args)

    @ScriptFunction
    fun waitForVisible(vararg args: Any?): Any = waitOf(WaitKind.VISIBLE, "flow.waitForVisible", args)

    @ScriptFunction(aliases = ["waitForGone"])
    fun waitForHidden(vararg args: Any?): Any = waitOf(WaitKind.HIDDEN, "flow.waitForHidden", args)

    @ScriptFunction
    fun waitForActivity(vararg args: Any?): Any {
        FlowArgs.nativeWaitArgs("flow.waitForActivity", args)
        return api.wait(null, WaitKind.ACTIVITY, "flow.waitForActivity", args)
    }

    @ScriptFunction
    fun waitForPackage(vararg args: Any?): Any {
        FlowArgs.nativeWaitArgs("flow.waitForPackage", args)
        return api.wait(null, WaitKind.PACKAGE, "flow.waitForPackage", args)
    }

    @ScriptFunction(aliases = ["clickWait"])
    fun waitThenClick(vararg args: Any?): Any = waitThen(WaitKind.WAIT, "flow.waitThenClick", args, "click", after = false)

    @ScriptFunction(aliases = ["clickBoundsWait"])
    fun waitThenClickBounds(vararg args: Any?): Any = waitThen(WaitKind.WAIT, "flow.waitThenClickBounds", args, "clickBounds", after = false)

    @ScriptFunction
    fun waitThenLongClick(vararg args: Any?): Any = waitThen(WaitKind.WAIT, "flow.waitThenLongClick", args, "longClick", after = false)

    @ScriptFunction(aliases = ["clickWhenStable"])
    fun waitForStableThenClick(vararg args: Any?): Any = waitThen(WaitKind.STABLE, "flow.waitForStableThenClick", args, "click", after = false)

    @ScriptFunction(aliases = ["clickBoundsWhenStable"])
    fun waitForStableThenClickBounds(vararg args: Any?): Any = waitThen(WaitKind.STABLE, "flow.waitForStableThenClickBounds", args, "clickBounds", after = false)

    @ScriptFunction
    fun clickWhenStableAfter(vararg args: Any?): Any = waitThen(WaitKind.STABLE, "flow.clickWhenStableAfter", args, "click", after = true)

    @ScriptFunction
    fun clickBoundsWhenStableAfter(vararg args: Any?): Any = waitThen(WaitKind.STABLE, "flow.clickBoundsWhenStableAfter", args, "clickBounds", after = true)

    @ScriptFunction(aliases = ["delay"])
    fun sleep(millis: Any, millisMax: Any? = null): Any {
        FlowArgs.sleepArgs("flow.sleep", millis, millisMax)
        return api.sleep(null, "flow.sleep", if (isNullish(millisMax)) arrayOf(millis) else arrayOf(millis, millisMax))
    }

    @ScriptFunction
    fun run(fn: Callable): Any = api.callback(null, CallbackKind.RUN, "flow.run", arrayOf(fn))

    @ScriptFunction
    fun of(value: Any? = null): Any = api.of(value)

    @ScriptFunction
    fun all(vararg sources: Any?): Any = combinator(CombinatorKind.ALL, "flow.all", sources)

    @ScriptFunction
    fun race(vararg sources: Any?): Any = combinator(CombinatorKind.RACE, "flow.race", sources)

    @ScriptFunction
    fun any(vararg sources: Any?): Any = combinator(CombinatorKind.ANY, "flow.any", sources)

    @ScriptFunction
    fun cancelAll(): Int = FlowExecutor.of(runtime).cancelAll()

    @ScriptFunction
    fun defaults(options: Any? = null): Any {
        FlowArgs.optionsArg("flow.defaults", "options", options)
        return api.defaults(if (isNullish(options)) emptyArray() else arrayOf(options))
    }

    @ScriptFunction
    fun trace(on: Any? = null): Boolean = api.trace(if (isNullish(on)) emptyArray() else arrayOf(on))

    // Toolkit roots (P4.2 of the flow roadmap): the target is the first argument, waited for by the flow defaults.
    // zh-CN: 工具起点: 目标为首个参数, 按 flow 默认值等待.

    @ScriptFunction
    fun smartClick(vararg args: Any?): Any = tool("smartClick", args)

    @ScriptFunction
    fun smartClickBounds(vararg args: Any?): Any = tool("smartClickBounds", args)

    @ScriptFunction
    fun clickIfExists(vararg args: Any?): Any = tool("clickIfExists", args)

    @ScriptFunction
    fun clickBoundsIfExists(vararg args: Any?): Any = tool("clickBoundsIfExists", args)

    @ScriptFunction
    fun clickAny(vararg args: Any?): Any = tool("clickAny", args)

    @ScriptFunction
    fun clickBoundsAny(vararg args: Any?): Any = tool("clickBoundsAny", args)

    @ScriptFunction
    fun findAny(vararg args: Any?): Any = tool("findAny", args)

    // Event-driven waits: `{ quietFor, elapsed, events }`, the event, the toast or the notification; a timeout rejects with a FlowError.
    // zh-CN: 事件驱动等待: resolve 为 { quietFor, elapsed, events }, 事件, toast 或通知; 超时以 FlowError reject.

    @ScriptFunction
    fun waitForIdle(vararg args: Any?): Any {
        ToolkitArgs.idleArgs("flow.waitForIdle", args)
        return api.eventWait(null, "waitForIdle", args)
    }

    @ScriptFunction
    fun waitForEvent(vararg args: Any?): Any {
        ToolkitArgs.eventArgs("flow.waitForEvent", args)
        return api.eventWait(null, "waitForEvent", args)
    }

    @ScriptFunction
    fun waitForToast(vararg args: Any?): Any {
        ToolkitArgs.textAndTimeoutArgs("flow.waitForToast", args)
        return api.eventWait(null, "waitForToast", args)
    }

    @ScriptFunction
    fun waitForNotification(vararg args: Any?): Any {
        ToolkitArgs.textAndTimeoutArgs("flow.waitForNotification", args)
        return api.eventWait(null, "waitForNotification", args)
    }

    @ScriptFunction
    fun scrollUntil(vararg args: Any?): Any = tool("scrollUntil", args)

    @ScriptFunction
    fun typeInto(vararg args: Any?): Any = tool("typeInto", args)

    @ScriptFunction
    fun dismissPopups(vararg args: Any?): Any = tool("dismissPopups", args)

    @ScriptFunction
    fun collectList(vararg args: Any?): Any = tool("collectList", args)

    @ScriptFunction
    fun launchAndWait(vararg args: Any?): Any = tool("launchAndWait", args)

    @ScriptFunction
    fun backUntil(vararg args: Any?): Any = tool("backUntil", args)

    @ScriptFunction
    fun backToApp(vararg args: Any?): Any = tool("backToApp", args)

    @ScriptFunction
    fun toggle(vararg args: Any?): Any = tool("toggle", args)

    @ScriptGetter(name = "Flow")
    val flowConstructor: Any
        get() = FlowExecutor.of(runtime).prototype.constructor

    @ScriptGetter
    val pending: Int
        get() = FlowExecutor.of(runtime).pendingCount

    @ScriptGetter
    val workers: Int
        get() = FlowExecutor.of(runtime).workerCount

    /** The global `waitAsync` (also `wait.async`): `flow.wait` under its own name. */
    private fun waitAsync(args: Array<out Any?>): Any = waitOf(WaitKind.WAIT, "waitAsync", args)

    /** The global forms in the order of the current module: global name, function name, call. */
    private fun globalForms(): List<Triple<String, String, (Array<out Any?>) -> Any>> = listOf(
        Triple("waitAsync", "waitAsync", ::waitAsync),
        Triple("waitThenClick", "waitThenClick") { args -> waitThenClick(*args) },
        Triple("clickWait", "waitThenClick") { args -> waitThenClick(*args) },
        Triple("waitThenClickBounds", "waitThenClickBounds") { args -> waitThenClickBounds(*args) },
        Triple("clickBoundsWait", "waitThenClickBounds") { args -> waitThenClickBounds(*args) },
        Triple("waitForStable", "waitForStable") { args -> waitForStable(*args) },
        Triple("waitForStableThenClick", "waitForStableThenClick") { args -> waitForStableThenClick(*args) },
        Triple("clickWhenStable", "waitForStableThenClick") { args -> waitForStableThenClick(*args) },
        Triple("waitForStableThenClickBounds", "waitForStableThenClickBounds") { args -> waitForStableThenClickBounds(*args) },
        Triple("clickBoundsWhenStable", "waitForStableThenClickBounds") { args -> waitForStableThenClickBounds(*args) },
        Triple("waitForVisible", "waitForVisible") { args -> waitForVisible(*args) },
        Triple("waitForHidden", "waitForHidden") { args -> waitForHidden(*args) },
        Triple("waitForGone", "waitForHidden") { args -> waitForHidden(*args) },
        Triple("clickWhenStableAfter", "clickWhenStableAfter") { args -> clickWhenStableAfter(*args) },
        Triple("clickBoundsWhenStableAfter", "clickBoundsWhenStableAfter") { args -> clickBoundsWhenStableAfter(*args) },
    )

    private fun waitOf(kind: WaitKind, function: String, args: Array<out Any?>): Any {
        FlowArgs.waitArgs(function, args, withDelay = false)
        return api.wait(null, kind, function, args)
    }

    private fun waitThen(kind: WaitKind, function: String, args: Array<out Any?>, action: String, after: Boolean): Any {
        FlowArgs.waitArgs(function, args, withDelay = after)
        return api.waitThen(null, kind, function, args, action, after)
    }

    private fun combinator(kind: CombinatorKind, function: String, sources: Array<out Any?>): Any {
        FlowArgs.sourcesArgs(function, sources)
        return api.combinator(kind, function, sources)
    }

    private fun tool(name: String, args: Array<out Any?>): Any {
        ToolkitArgs.toolArgs("flow.$name", name, args, callbacks = true)
        return api.tool(null, name, args)
    }

}
