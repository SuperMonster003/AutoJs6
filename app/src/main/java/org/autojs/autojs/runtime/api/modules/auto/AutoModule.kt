package org.autojs.autojs.runtime.api.modules.auto

import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.rhino.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.automator.A11yDiagnosticsApi
import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.flow.FlowExecutor
import org.autojs.autojs.runtime.api.augment.flow.WaitKind
import org.autojs.autojs.runtime.api.augment.util.Java
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.flow.FlowArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper

/**
 * The `auto` module on the new script module layer (roadmap P3.3): the accessibility service (callable as `auto(mode?,
 * forceRestart?)`), its state, events, windows and diagnostics, and `auto.wait` (the asynchronous wait of `flow`, D1), with
 * the eight getters of the current module (`service`, `services`, `windows`, `root`, `rootInActiveWindow`, `windowRoots`,
 * `stats`, `state`). The work is still done by the current module's functions (`augment/automator/Auto`, P5.2).
 *
 * Arguments follow the real signatures (T4): a count or a top-level type the documented signatures do not accept is a
 * TypeError, an unknown mode or flag a RangeError (D10; the current module threw IllegalArgumentException). The fields of
 * the options of `registerEvent`, `setWindowFilter`, `findWindows` and `dump` are still checked by the current parsers
 * (outside appendix C Q65, see Q66; appendix E "P3.3").
 *
 * zh-CN: 新脚本模块层上的 auto 模块 (P3.3): 无障碍服务 (可调用, auto(mode?, forceRestart?)), 其状态, 事件, 窗口与诊断, 以及 auto.wait (flow 的异步等待, D1),
 * 另有现有模块的 8 个 getter. 实际工作仍由现有模块的函数完成 (augment/automator/Auto, P5.2 迁出). 参数按真实签名 (T4): 文档签名不接受的个数或顶层类型为 TypeError,
 * 未知的模式或标志为 RangeError (D10; 现有模块抛 IllegalArgumentException). registerEvent, setWindowFilter, findWindows 与 dump 的选项字段仍由现有解析器检查
 * (不在附录 C Q65 的范围内, 见 Q66; 附录 E "P3.3").
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
    @ScriptFunction
    fun wait(vararg args: Any?): Any {
        FlowArgs.waitArgs("auto.wait", args, withDelay = false)
        return FlowExecutor.of(runtime).api.wait(null, WaitKind.WAIT, "auto.wait", args)
    }

    @ScriptFunction
    fun setMode(mode: Any) = Auto.setModeRhinoWithRuntime(runtime, modeOf("auto.setMode", "mode", mode))

    @ScriptFunction
    fun setFlags(flags: Any) {
        val names = when (flags) {
            is CharSequence -> listOf(flags)
            is NativeArray -> flags.toList()
            else -> throw GlobalValues.typeError("auto.setFlags", "flags", "string | string[]", flags)
        }
        names.forEachIndexed { i, flag ->
            if (flag !is CharSequence) throw GlobalValues.typeError("auto.setFlags", "flags[$i]", "string", flag)
            if (flag.toString() !in FLAGS) throw GlobalValues.rangeError("auto.setFlags", "flags[$i]", FLAGS.joinToString(" | ") { "\"$it\"" }, flag)
        }
        Auto.setFlags(runtime, arrayOf(if (flags is CharSequence) flags.toString() else names.map { it.toString() }.toNativeArray()))
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

    /** `auto.explain(selector, root?)`: the selector in any `pickup` form (checked by the selector parser), the root a `UiObject`. */
    @ScriptFunction
    fun explain(selector: Any, root: Any? = null): NativeObject {
        if (!isNullish(root) && unwrap(root) !is UiObject) throw GlobalValues.typeError("auto.explain", "root", "UiObject", root)
        return Auto.explain(runtime, if (isNullish(root)) arrayOf(selector) else arrayOf(selector, root))
    }

    /** `auto.dump(options?)`: a format name or an options object (whose fields the current parser checks). */
    @ScriptFunction
    fun dump(options: Any? = null): String {
        val o = unwrap(options)
        if (!isNullish(o) && o !is CharSequence && o !is NativeObject) throw GlobalValues.typeError("auto.dump", "options", "string | Object", options)
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
        if (mode.toString().lowercase() !in MODES) throw GlobalValues.rangeError(function, param, MODES.joinToString(" | ") { "\"$it\"" }, mode)
        return mode.toString()
    }

    private fun eventArgs(function: String, name: Any, listener: Any?, options: Any?): Array<Any?> {
        val l = unwrap(listener)
        if (!isNullish(l) && l !is AccessibilityEventCallback && l !is ScriptableObject) throw GlobalValues.typeError(function, "listener", "function | Object", listener)
        FlowArgs.optionsArg(function, "options", options)
        return if (isNullish(options)) arrayOf(name, listener) else arrayOf(name, listener, options)
    }

    private fun windowOptions(function: String, options: Any?): Array<Any?> {
        if (isNullish(options)) return NO_ARGS
        if (options !is NativeObject) throw GlobalValues.typeError(function, "options", "Object", options)
        return arrayOf(options)
    }

    private fun unwrap(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    private companion object {

        val ABSENT = Any()

        val NO_ARGS = emptyArray<Any?>()

        /** The modes of `auto.setMode`, as the current module accepts them (any case). */
        val MODES = listOf("normal", "fast")

        /** The flags of `auto.setFlags`. */
        val FLAGS = listOf("findOnUiThread", "useUsageStats", "useShell", "appWindowsFallback", "eventAssistedPolling")
    }

}
