package org.autojs.autojs.runtime.api.modules.auto

import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.pick.PickArguments
import org.autojs.autojs.rhino.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.automator.A11yDiagnosticsApi
import org.autojs.autojs.runtime.api.modules.automator.Auto
import org.autojs.autojs.runtime.api.modules.flow.FlowArgs
import org.autojs.autojs.runtime.api.modules.flow.FlowExecutor
import org.autojs.autojs.runtime.api.modules.flow.FlowOptions
import org.autojs.autojs.runtime.api.modules.flow.WaitKind
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.runtime.api.modules.util.Java
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * The `auto` module on the new script module layer (roadmap P3.3): the accessibility service (callable as `auto(mode?,
 * forceRestart?)`), its state, events, windows and diagnostics, and `auto.wait` (the asynchronous wait of `flow`, D1), with
 * the eight getters of the current module (`service`, `services`, `windows`, `root`, `rootInActiveWindow`, `windowRoots`,
 * `stats`, `state`). The work is still done by the current module's functions (`augment/automator/Auto`, P5.2).
 *
 * Arguments follow the real signatures (T4): a count or a top-level type the documented signatures do not accept is a
 * TypeError, an unknown mode or flag a RangeError (D10; the current module threw IllegalArgumentException), and so are the
 * fields of the options of `registerEvent`, `setWindowFilter`, `findWindows`, `findWindowRoots` and `dump` ([AutoOptions])
 * and the selector of `explain` (`PickArguments`, docs/selector/ROADMAP.md P1.4), appendix C Q66 (2).
 *
 * zh-CN: 新脚本模块层上的 auto 模块 (P3.3): 无障碍服务 (可调用, auto(mode?, forceRestart?)), 其状态, 事件, 窗口与诊断, 以及 auto.wait (flow 的异步等待, D1),
 * 另有现有模块的 8 个 getter. 实际工作仍由现有模块的函数完成 (augment/automator/Auto, P5.2 迁出). 参数按真实签名 (T4): 文档签名不接受的个数或顶层类型为 TypeError,
 * 未知的模式或标志为 RangeError (D10; 现有模块抛 IllegalArgumentException); registerEvent, setWindowFilter, findWindows, findWindowRoots 与 dump 的选项字段
 * ([AutoOptions]) 以及 explain 的选择器 (PickArguments, selector P1.4) 同样如此, 附录 C Q66 (2).
 */
@ScriptModule(name = "auto")
class AutoModule(private val runtime: ScriptRuntime) {

    /** `auto()` / `auto(forceRestart)` / `auto(mode)` / `auto(mode, forceRestart)`: sets the mode, then makes sure the service runs. */
    @ScriptInvoke
    fun invoke(first: Any? = null, second: Any? = ABSENT) {
        if (second !== ABSENT) {
            val mode = modeOf("auto", "mode", first)
            if (second !is Boolean) throw GlobalValues.typeError("auto", "forceRestart", "boolean", second)
            Auto.setModeRhinoWithRuntime(runtime, mode)
            runtime.accessibilityBridge.ensureServiceStarted(second)
            return
        }
        when {
            isNullish(first) -> runtime.accessibilityBridge.ensureServiceStarted(false)
            first is Boolean -> runtime.accessibilityBridge.ensureServiceStarted(first)
            first is CharSequence -> Auto.setModeRhinoWithRuntime(runtime, modeOf("auto", "mode", first))
            else -> throw GlobalValues.typeError("auto", "mode", "string | boolean", first)
        }
    }

    @ScriptFunction
    fun start() = Auto.start(runtime, NO_ARGS)

    @ScriptFunction
    fun stop() = Auto.stop(runtime, NO_ARGS)

    @ScriptFunction
    fun enable() = Auto.enable(runtime, NO_ARGS)

    @ScriptFunction
    fun disable() = Auto.disable(runtime, NO_ARGS)

    @ScriptFunction
    fun hasInstance(): Boolean = Auto.hasInstance(runtime, NO_ARGS)

    @ScriptFunction
    fun hasService(): Boolean = Auto.hasService(runtime, NO_ARGS)

    @ScriptFunction
    fun exists(): Boolean = Auto.exists(runtime, NO_ARGS)

    @ScriptFunction
    fun isRunning(): Boolean = Auto.isRunning(runtime, NO_ARGS)

    @ScriptFunction
    fun isOperational(): Boolean = Auto.isOperational(runtime, NO_ARGS)

    @ScriptFunction
    fun stateListener(listener: Any? = null) {
        val l = unwrap(listener)
        if (!isNullish(l) && l !is AccessibilityServiceCallback && l !is ScriptableObject) {
            throw GlobalValues.typeError("auto.stateListener", "listener", "Object", listener)
        }
        Auto.stateListener(runtime, arrayOf(listener))
    }

    @ScriptFunction
    fun registerEvent(name: Any, listener: Any?, options: Any? = null) = Auto.registerEvent(runtime, eventArgs("auto.registerEvent", name, listener, options))

    @ScriptFunction
    fun registerEvents(name: Any, listener: Any?, options: Any? = null) = Auto.registerEvents(runtime, eventArgs("auto.registerEvents", name, listener, options))

    @ScriptFunction
    fun removeEvent(name: Any) = Auto.removeEvent(runtime, arrayOf(name))

    @ScriptFunction
    fun removeEvents(name: Any) = Auto.removeEvents(runtime, arrayOf(name))

    /** `auto.waitFor(timeout?)`: blocks until the service runs; no timeout (or a negative one) waits without a limit, as before. */
    @ScriptFunction
    fun waitFor(timeout: Any? = null) {
        if (!isNullish(timeout)) {
            if (timeout !is Number) throw GlobalValues.typeError("auto.waitFor", "timeout", "number", timeout)
            if (timeout.toDouble().isNaN()) throw GlobalValues.rangeError("auto.waitFor", "timeout", "number", timeout)
        }
        Auto.waitFor(runtime, if (isNullish(timeout)) NO_ARGS else arrayOf(timeout))
    }

    /** `auto.wait(cond, ...)`: the asynchronous wait of `flow.wait` (D1); not the service wait [waitFor]. */
    @ScriptFunction(arity = [1, 5])
    fun wait(vararg args: Any?): Any {
        FlowArgs.waitArgs("auto.wait", args, withDelay = false)
        return FlowExecutor.of(runtime).api.wait(null, WaitKind.WAIT, "auto.wait", args)
    }

    @ScriptFunction
    fun setMode(mode: Any) = Auto.setModeRhinoWithRuntime(runtime, modeOf("auto.setMode", "mode", mode))

    /** `auto.setFlags(flags)`: a name prefixed with `!` clears its flag (docs/selector/ROADMAP.md appendix C Q2). */
    @ScriptFunction
    fun setFlags(flags: Any) {
        val names = flagNames("auto.setFlags", flags, negation = true)
        val set = names.filterNot { it.startsWith("!") }.toSet()
        names.forEachIndexed { i, name ->
            if (name.startsWith("!") && name.substring(1) in set) throw GlobalValues.cannotBe("auto.setFlags", "flags[$i]", "\"$name\" together with \"${name.substring(1)}\"")
        }
        Auto.setFlags(runtime, arrayOf(if (flags is CharSequence) flags.toString() else names.toNativeArray()))
    }

    /** `auto.clearFlags(flags)`: clears the flags named, the others stay (appendix C Q2). */
    @ScriptFunction
    fun clearFlags(flags: Any) {
        val names = flagNames("auto.clearFlags", flags, negation = false)
        Auto.clearFlags(runtime, arrayOf(if (flags is CharSequence) flags.toString() else names.toNativeArray()))
    }

    // The names of a flag argument (a string or an array of strings), each one checked against FLAGS ("!" allowed for setFlags).
    private fun flagNames(function: String, flags: Any, negation: Boolean): List<String> {
        val names = when (flags) {
            is CharSequence -> listOf(flags)
            is NativeArray -> flags.toList()
            else -> throw GlobalValues.typeError(function, "flags", "string | string[]", flags)
        }
        return names.mapIndexed { i, flag ->
            if (flag !is CharSequence) throw GlobalValues.typeError(function, "flags[$i]", "string", flag)
            val name = flag.toString()
            val bare = if (negation) name.removePrefix("!") else name
            if (bare !in FLAGS.set) throw GlobalValues.rangeError(function, "flags[$i]", if (negation) FLAGS_NEGATABLE else FLAGS.quoted, flag)
            name
        }
    }

    @ScriptFunction
    fun getWindowRoot(window: Any): UiObject? {
        if (unwrap(window) !is AccessibilityWindowInfo) throw GlobalValues.typeError("auto.getWindowRoot", "window", "AccessibilityWindowInfo", window)
        return Auto.getWindowRoot(runtime, arrayOf(window))
    }

    @ScriptFunction
    fun setWindowFilter(filter: Any? = null) {
        val f = unwrap(filter)
        if (!isNullish(f) && f !is Boolean && f !is WindowFilter && f !is NativeObject && f !is Callable) {
            throw GlobalValues.typeError("auto.setWindowFilter", "filter", "boolean | Object | function", filter)
        }
        if (f is NativeObject && f !is WindowFilter) AutoOptions.windowFilter("auto.setWindowFilter", "filter", f)
        Auto.setWindowFilter(runtime, if (isNullish(filter)) NO_ARGS else arrayOf(filter))
    }

    @ScriptFunction
    fun findWindows(options: Any? = null): Any? = Auto.findWindows(runtime, windowOptions("auto.findWindows", options))

    @ScriptFunction
    fun findWindowRoots(options: Any? = null): Any? = Auto.findWindowRoots(runtime, windowOptions("auto.findWindowRoots", options))

    @ScriptFunction
    fun launchSettings() = Auto.launchSettings(runtime, NO_ARGS)

    @ScriptFunction
    fun clearCache(): Boolean = Auto.clearCache(runtime, NO_ARGS)

    @ScriptFunction
    fun currentPackage(): String = Auto.currentPackage(runtime, NO_ARGS)

    @ScriptFunction
    fun currentActivity(): String = Auto.currentActivity(runtime, NO_ARGS)

    @ScriptFunction
    fun currentComponent(): String = Auto.currentComponent(runtime, NO_ARGS)

    /** `auto.explain(selector, root?)`: the selector in any `pickup` form (undefined: no condition), the root a `UiObject`. */
    @ScriptFunction
    fun explain(selector: Any, root: Any? = null): NativeObject {
        PickArguments.uiObject.selectorError("auto.explain", "selector", RhinoUtils.unwrap(selector).takeUnless { Undefined.isUndefined(it) })?.let { throw it.exception() }
        if (!isNullish(root) && unwrap(root) !is UiObject) throw GlobalValues.typeError("auto.explain", "root", "UiObject", root)
        return Auto.explain(runtime, if (isNullish(root)) arrayOf(selector) else arrayOf(selector, root))
    }

    /** `auto.dump(options?)`: a format name or an options object. */
    @ScriptFunction
    fun dump(options: Any? = null): String {
        val o = unwrap(options)
        if (!isNullish(o) && o !is CharSequence && o !is NativeObject) throw GlobalValues.typeError("auto.dump", "options", "string | Object", options)
        AutoOptions.dump("auto.dump", options)
        return Auto.dump(runtime, if (isNullish(options)) NO_ARGS else arrayOf(options))
    }

    @ScriptGetter
    val service: Any?
        get() = runtime.accessibilityBridge.service

    @ScriptGetter
    val services: Any
        get() = Auto.accessibilityTool.getServices().toNativeArray()

    @ScriptGetter
    val windows: Any
        get() = runtime.accessibilityBridge.service?.run { Java.toJsArrayRhino(windows, true) } ?: RhinoUtils.newNativeArray(runtime.topLevelScope)

    @ScriptGetter
    val root: UiObject?
        get() = runtime.accessibilityBridge.getRootInCurrentWindow()?.let { UiObject.createRoot(it, null, runtime.accessibilityBridge) }

    @ScriptGetter
    val rootInActiveWindow: UiObject?
        get() = runtime.accessibilityBridge.getRootInActiveWindow()?.let { UiObject.createRoot(it, null, runtime.accessibilityBridge) }

    @ScriptGetter
    val windowRoots: Any
        get() = runtime.accessibilityBridge.windowRoots().map { UiObject.createRoot(it, null, runtime.accessibilityBridge) }.toNativeArray()

    @ScriptGetter
    val stats: NativeObject
        get() = A11yDiagnosticsApi(runtime).stats()

    @ScriptGetter
    val state: Any
        get() = RhinoUtils.newNativeObject(runtime.topLevelScope).also { o ->
            val tool = Auto.accessibilityTool
            o.defineProp("hasInstance", tool.hasInstance())
            o.defineProp("hasService", tool.hasService())
            o.defineProp("isRunning", tool.isRunning())
            o.defineProp("isOperational", tool.isOperational())
            o.defineProp("adoptedByEvent", AccessibilityService.registry.adoptedByEvent)
        }

    /** A mode of `auto` / `auto.setMode`: a string naming one of [MODES] in any case. */
    private fun modeOf(function: String, param: String, mode: Any?): String {
        if (mode !is CharSequence) throw GlobalValues.typeError(function, param, "string", mode)
        if (mode.toString().lowercase() !in MODES.set) throw GlobalValues.rangeError(function, param, MODES.quoted, mode)
        return mode.toString()
    }

    private fun eventArgs(function: String, name: Any, listener: Any?, options: Any?): Array<Any?> {
        val l = unwrap(listener)
        if (!isNullish(l) && l !is AccessibilityEventCallback && l !is ScriptableObject) throw GlobalValues.typeError(function, "listener", "function | Object", listener)
        FlowArgs.optionsArg(function, "options", options)
        AutoOptions.event(function, options)
        return if (isNullish(options)) arrayOf(name, listener) else arrayOf(name, listener, options)
    }

    private fun windowOptions(function: String, options: Any?): Array<Any?> {
        if (isNullish(options)) return NO_ARGS
        if (options !is NativeObject) throw GlobalValues.typeError(function, "options", "Object", options)
        AutoOptions.windowFilter(function, "options", options)
        return arrayOf(options)
    }

    private fun unwrap(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    private companion object {

        val ABSENT = Any()

        val NO_ARGS = emptyArray<Any?>()

        /** The modes of `auto.setMode`, as the current module accepts them (any case). */
        val MODES = FlowOptions.Words(listOf("normal", "fast"))

        /** The flags of `auto.setFlags` / `auto.clearFlags`. */
        val FLAGS = FlowOptions.Words(listOf("findOnUiThread", "useUsageStats", "useShell", "appWindowsFallback", "eventAssistedPolling", "noFastPath", "noRefreshOnHit"))

        /** What `auto.setFlags` accepts: a flag, or a flag prefixed with `!` (appendix C Q2). */
        val FLAGS_NEGATABLE = "${FLAGS.quoted} (or one of them prefixed with \"!\")"
    }

}
