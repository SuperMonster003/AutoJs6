package org.autojs.autojs.runtime.api.augment.automator

import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.EventKeyFunction
import org.autojs.autojs.core.accessibility.event.A11yEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus
import org.autojs.autojs.core.accessibility.window.A11yWindowFacts
import org.autojs.autojs.core.automator.AccessibilityEventWrapper
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.flow.FlowExecutor
import org.autojs.autojs.runtime.api.augment.flow.WaitKind
import org.autojs.autojs.runtime.api.augment.util.Java
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.util.function.Supplier

@Suppress("unused", "UNUSED_PARAMETER")
class Auto(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::start.name,
        ::stop.name,
        ::enable.name,
        ::disable.name,
        ::hasInstance.name,
        ::hasService.name,
        ::exists.name,
        ::isRunning.name,
        ::isOperational.name,
        ::stateListener.name,
        ::registerEvent.name,
        ::registerEvents.name,
        ::removeEvent.name,
        ::removeEvents.name,
        ::waitFor.name,
        ::wait.name,
        ::setMode.name,
        ::setFlags.name,
        ::clearFlags.name,
        ::getWindowRoot.name,
        ::setWindowFilter.name,
        ::findWindows.name,
        ::findWindowRoots.name,
        ::launchSettings.name,
        ::clearCache.name,
        ::currentPackage.name,
        ::currentActivity.name,
        ::currentComponent.name,
        ::explain.name,
        ::dump.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "service" to Supplier {
            scriptRuntime.accessibilityBridge.service
        },
        "services" to Supplier {
            accessibilityTool.getServices().toNativeArray()
        },
        "windows" to Supplier {
            scriptRuntime.accessibilityBridge.service?.run { Java.toJsArrayRhino(windows, true) }
                ?: newNativeArray(scriptRuntime.topLevelScope)
        },
        "root" to Supplier {
            scriptRuntime.accessibilityBridge.getRootInCurrentWindow()?.let { UiObject.createRoot(it, null, scriptRuntime.accessibilityBridge) }
        },
        "rootInActiveWindow" to Supplier {
            scriptRuntime.accessibilityBridge.getRootInActiveWindow()?.let { UiObject.createRoot(it, null, scriptRuntime.accessibilityBridge) }
        },
        "windowRoots" to Supplier {
            scriptRuntime.accessibilityBridge.windowRoots().map { UiObject.createRoot(it, null, scriptRuntime.accessibilityBridge) }.toNativeArray()
        },
        "stats" to Supplier {
            A11yDiagnosticsApi(scriptRuntime).stats()
        },
        "state" to Supplier {
            newNativeObject(scriptRuntime.topLevelScope).also { o ->
                o.defineProp("hasInstance", accessibilityTool.hasInstance())
                o.defineProp("hasService", accessibilityTool.hasService())
                o.defineProp("isRunning", accessibilityTool.isRunning())
                o.defineProp("isOperational", accessibilityTool.isOperational())
                o.defineProp("adoptedByEvent", AccessibilityService.registry.adoptedByEvent)
            }
        }
    )

    override fun invoke(vararg args: Any?): Any = ensureArgumentsAtMost(args, 2) {
        when {
            it.isEmpty() -> invoke(null)
            it.size == 1 -> {
                val (o) = it
                when {
                    o.isJsNullish() -> invoke(false)
                    o is Boolean -> ensureA11yServiceStarted(scriptRuntime, o)
                    o is String -> setModeRhinoWithRuntime(scriptRuntime, o)
                    else -> throw WrappedIllegalArgumentException("Invalid argument ${Context.toString(o)} for auto()")
                }
            }
            else -> {
                val (mode, isForcibleRestart) = it
                setModeRhinoWithRuntime(scriptRuntime, mode)
                ensureA11yServiceStarted(scriptRuntime, isForcibleRestart)
            }
        }
        return@ensureArgumentsAtMost UNDEFINED
    }

    companion object : ArgumentGuards() {

        /** Default `within` window of a `distinct` option of `auto.registerEvent`. zh-CN: `auto.registerEvent` 的 `distinct` 选项默认的 `within` 窗口. */
        const val DEFAULT_DISTINCT_WITHIN_MILLIS = 500L

        /** The shared tool held by AutoJs; all JS entry points go through the same controller. zh-CN: AutoJs 持有的共享工具; 全部 JS 入口经同一个控制器. */
        val accessibilityTool: AccessibilityTool get() = AutoJs.instance.accessibilityTool

        private val modes = hashMapOf(
            "normal" to AccessibilityBridge.MODE_NORMAL,
            "fast" to AccessibilityBridge.MODE_FAST,
        )

        private val flags = hashMapOf(
            "findOnUiThread" to AccessibilityBridge.FLAG_FIND_ON_UI_THREAD,
            "useUsageStats" to AccessibilityBridge.FLAG_USE_USAGE_STATS,
            "useShell" to AccessibilityBridge.FLAG_USE_SHELL,
            "appWindowsFallback" to AccessibilityBridge.FLAG_APP_WINDOWS_FALLBACK,
            "eventAssistedPolling" to AccessibilityBridge.FLAG_EVENT_ASSISTED_POLLING,
            "noFastPath" to AccessibilityBridge.FLAG_NO_FAST_PATH,
            "noRefreshOnHit" to AccessibilityBridge.FLAG_NO_REFRESH_ON_HIT,
        )

        /** The prefix that clears a flag in `auto.setFlags` (docs/selector/ROADMAP.md appendix C Q2). zh-CN: `auto.setFlags` 中清除标志的前缀 (附录 C Q2). */
        private const val FLAG_NEGATION = "!"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun start(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.startService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun enable(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            start(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stop(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.stopService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun disable(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            stop(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hasInstance(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.hasInstance()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hasService(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.hasService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun exists(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.hasService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isRunning(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.isRunning()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isOperational(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.isOperational()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stateListener(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) {
            val (listener) = it
            scriptRuntime.accessibilityBridge.setAccessibilityListener(
                when {
                    listener.isJsNullish() -> null
                    listener is AccessibilityServiceCallback -> listener
                    listener is ScriptableObject -> {
                        val adapter = NativeJavaObject.createInterfaceAdapter(
                            AccessibilityServiceCallback::class.java, listener
                        ) as AccessibilityServiceCallback

                        object : AccessibilityServiceCallback {
                            override fun onConnected() = adapter.onConnected()
                            override fun onDisconnected() = adapter.onDisconnected()
                        }
                    }
                    else -> throw WrappedIllegalArgumentException("Argument listener ($listener) is invalid for auto.setWindowFilter")
                })
            UNDEFINED
        }

        /**
         * `auto.registerEvent(name, listener, options?)`. Options thin out repeated events and are off by default:
         * `distinct: 'window' | 'source' | true | (event) => key` drops an event whose key equals that of the
         * last delivered one within `within` ms (default 500; `'window'` = package + class, `'source'` adds the
         * source node, `true` = `'window'`); `debounce: ms` delivers only the last event of a burst once no
         * further event arrived for that long.
         * zh-CN: `auto.registerEvent(name, listener, options?)`. 选项用于稀释重复事件, 默认关闭:
         * `distinct: 'window' | 'source' | true | (event) => key` 在 `within` 毫秒内 (默认 500) 丢弃键与上次投递相同的事件
         * (`'window'` = 包名 + 类名, `'source'` 另加来源节点, `true` 即 `'window'`); `debounce: ms` 在该时长内没有新事件后只投递这一串中的最后一个.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun registerEvent(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 2..3) {
            val (name, listener, options) = it
            require(!name.isJsNullish()) { "Argument \"name\" for auto.registerEvent cannot be nullish" }
            scriptRuntime.automator.registerEvent(
                Context.toString(name), when {
                    listener.isJsNullish() -> null
                    listener is AccessibilityEventCallback -> listener
                    listener is ScriptableObject -> {
                        val adapter = NativeJavaObject.createInterfaceAdapter(
                            AccessibilityEventCallback::class.java, listener
                        ) as AccessibilityEventCallback
                        object : AccessibilityEventCallback {
                            override fun onAccessibilityEvent(event: AccessibilityEventWrapper) = adapter.onAccessibilityEvent(event)
                        }
                    }
                    else -> throw WrappedIllegalArgumentException("Argument listener ($listener) is invalid for auto.registerEvent")
                },
                parseEventOptions(options),
            )
        }

        private fun parseEventOptions(options: Any?): A11yEventBus.Options<A11yEvent> {
            if (options.isJsNullish()) return A11yEventBus.Options()
            require(options is ScriptableObject) { "Argument \"options\" for auto.registerEvent must be an object instead of ${options.jsBrief()}" }
            val distinct = options.prop("distinct")
            val within = options.prop("within")
            val debounce = options.prop("debounce")
            val distinctKey: ((A11yEvent) -> Any?)? = when {
                distinct.isJsNullish() || distinct == false -> null
                distinct == true -> { event -> windowKeyOf(event) }
                distinct is CharSequence -> when (val mode = distinct.toString()) {
                    "window" -> { event -> windowKeyOf(event) }
                    // Reading the source costs a node lookup per event; documented as such.
                    // zh-CN: 读取 source 每个事件多一次节点查询; 文档已说明.
                    "source" -> { event -> "${windowKeyOf(event)}#${event.wrapper.source?.hashCode()}" }
                    else -> throw WrappedIllegalArgumentException("Option \"distinct\" (\"$mode\") is invalid for auto.registerEvent")
                }
                distinct is ScriptableObject && distinct is Callable -> {
                    val adapter = NativeJavaObject.createInterfaceAdapter(EventKeyFunction::class.java, distinct) as EventKeyFunction
                    ({ event -> adapter.keyOf(event.wrapper) })
                }
                else -> throw WrappedIllegalArgumentException("Option \"distinct\" (${distinct.jsBrief()}) is invalid for auto.registerEvent")
            }
            val withinMillis = when {
                !within.isJsNullish() -> Context.toNumber(within).toLong()
                distinctKey != null -> DEFAULT_DISTINCT_WITHIN_MILLIS
                else -> 0L
            }
            val debounceMillis = if (debounce.isJsNullish()) 0L else Context.toNumber(debounce).toLong()
            require(withinMillis >= 0 && debounceMillis >= 0) { "Options \"within\" and \"debounce\" for auto.registerEvent cannot be negative" }
            return A11yEventBus.Options(distinctKey, withinMillis, debounceMillis)
        }

        private fun windowKeyOf(event: A11yEvent) = "${event.raw.packageName}/${event.raw.className}"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun registerEvents(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return registerEvent(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeEvent(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
            require(!it.isJsNullish()) { "Argument \"name\" for auto.removeEvent cannot be nullish" }
            scriptRuntime.automator.removeEvent(Context.toString(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeEvents(scriptRuntime: ScriptRuntime, args: Array<out Any?>) {
            return removeEvent(scriptRuntime, args)
        }

        /**
         * `auto.waitFor(timeout?)`: blocks the script until the accessibility service is running.
         * Not to be confused with [wait], the asynchronous wait for a condition (roadmap D1).
         * zh-CN: `auto.waitFor(timeout?)`: 阻塞脚本直到无障碍服务运行. 勿与 [wait] (条件的异步等待, Roadmap D1) 混淆.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitFor(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (timeout) = it
            waitForRhino(timeout)
        }

        /**
         * `auto.wait(cond, timeout?, interval?)` / `auto.wait(cond, options)`: the asynchronous,
         * chainable wait for a selector, function, node, flow or promise, returning a `Flow`; the
         * same as the global `waitAsync` / `wait.async` / `flow.wait` (roadmap D1, P3.2). See
         * [waitFor] for the service wait it is named after.
         * zh-CN: `auto.wait(cond, timeout?, interval?)` / `auto.wait(cond, options)`: 对选择器, 函数, 节点, Flow 或 Promise 的
         * 异步链式等待, 返回 `Flow`; 与全局 `waitAsync` / `wait.async` / `flow.wait` 相同 (Roadmap D1, P3.2). 服务等待见 [waitFor].
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun wait(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = FlowExecutor.of(scriptRuntime).api.wait(null, WaitKind.WAIT, "auto.wait", args)

        @JvmStatic
        @RhinoFunctionBody
        fun waitForRhino(timeout: Any?) {
            Automator.waitForServiceRhino(timeout)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setMode(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
            setModeRhinoWithRuntime(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setModeRhinoWithRuntime(scriptRuntime: ScriptRuntime, mode: Any?) {
            if (mode !is String) throw WrappedIllegalArgumentException("Argument mode must be of type String for auto.setMode")
            modes[mode.lowercase()]?.let {
                scriptRuntime.accessibilityBridge.setMode(it)
            } ?: throw WrappedIllegalArgumentException("Unknown mode ($mode) for auto.setMode")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setFlags(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
            // The flags given replace the current ones, on top of the defaults: a name sets its flag, a name prefixed
            // with "!" clears it (appendix C Q2; `!noFastPath` turns the fast path back on).
            // zh-CN: 给出的标志在默认值之上替换当前标志: 名字设置其标志, 带 "!" 前缀的名字清除它 (附录 C Q2; `!noFastPath` 重新开启快路径).
            var set = 0
            var cleared = 0
            flagNames(it, "setFlags").forEach { s ->
                val name = "$s"
                val negated = name.startsWith(FLAG_NEGATION)
                val flag = flags[name.removePrefix(FLAG_NEGATION)] ?: throw WrappedIllegalArgumentException("Unknown flag ($s) for auto.setFlags")
                if (negated) cleared = cleared or flag else set = set or flag
            }
            if (set and cleared != 0) {
                throw WrappedIllegalArgumentException("Flags both set and cleared (${flags.filterValues { v -> v and set and cleared != 0 }.keys.joinToString()}) for auto.setFlags")
            }
            scriptRuntime.accessibilityBridge.flags = (AccessibilityBridge.DEFAULT_FLAGS or set) and cleared.inv()
        }

        /**
         * `auto.clearFlags(names)` (appendix C Q2): clears the flags named, the others stay as they are.
         * zh-CN: `auto.clearFlags(names)` (附录 C Q2): 清除给出名字的标志, 其余保持不变.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clearFlags(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
            var cleared = 0
            flagNames(it, "clearFlags").forEach { s ->
                cleared = cleared or (flags["$s"] ?: throw WrappedIllegalArgumentException("Unknown flag ($s) for auto.clearFlags"))
            }
            val bridge = scriptRuntime.accessibilityBridge
            bridge.flags = bridge.flags and cleared.inv()
        }

        private fun flagNames(names: Any?, function: String): List<Any?> = when (names) {
            is NativeArray -> names
            is String -> listOf(names)
            else -> throw WrappedIllegalArgumentException("Unknown flags ($names) for auto.$function")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getWindowRoot(scriptRuntime: ScriptRuntime, args: Array<out Any?>): UiObject? = ensureArgumentsOnlyOne(args) { window ->
            val windowInfo = window.jsUnwrapped()
            require(windowInfo is AccessibilityWindowInfo) {
                "Argument \"window\" ${window.jsBrief()} for auto.getWindowRoot must be an AccessibilityWindowInfo"
            }
            windowInfo.root?.let { UiObject.createRoot(it, null, scriptRuntime.accessibilityBridge) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setWindowFilter(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (filter) = it
            val accessibilityBridge = scriptRuntime.accessibilityBridge
            when {
                filter.isJsNullish() -> accessibilityBridge.setWindowFilter { true }
                filter is Boolean -> accessibilityBridge.setWindowFilter { filter }
                filter is WindowFilter -> accessibilityBridge.setWindowFilter(filter)
                filter is NativeObject -> WindowMatchArguments.parse(filter, "auto.setWindowFilter").let { match ->
                    accessibilityBridge.setWindowFilter { info -> match.matches(info, A11yWindowFacts) }
                }
                filter is Callable -> accessibilityBridge.setWindowFilter { info ->
                    Context.toBoolean(callFunction(scriptRuntime, filter, null as VarScope?, scriptRuntime.topLevelScope.globalThis, arrayOf(info)))
                }
                else -> throw WrappedIllegalArgumentException("Argument filter ($filter) is invalid for auto.setWindowFilter")
            }
        }

        /**
         * The service's windows passing a filter object (roadmap P5.3), in the framework's order:
         * `auto.findWindows({ type: "application", packageName: "com.android.settings" })`; the keys are
         * `type` (a name or names: `application`, `inputMethod`, `system`, `accessibilityOverlay`,
         * `splitScreenDivider`, `magnificationOverlay`), `packageName` / `title` (a string or a regular
         * expression), `displayId`, `active`, `focused`, `id`, `layer`; without an argument, every window.
         * zh-CN: 通过过滤器对象 (Roadmap P5.3) 的服务窗口, 按框架的顺序: `auto.findWindows({ type: "application", packageName: "com.android.settings" })`;
         * 键为 `type` (一个或多个名称), `packageName` / `title` (字符串或正则), `displayId`, `active`, `focused`, `id`, `layer`; 不带参数时为全部窗口.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findWindows(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (options) = it
            val match = WindowMatchArguments.parse(options, "auto.findWindows")
            val windows = scriptRuntime.accessibilityBridge.windows().filter { window -> match.matches(window, A11yWindowFacts) }
            Java.toJsArrayRhino(windows, true)
        }

        /**
         * The root nodes of [findWindows] (roadmap P5.3): the windows passing the filter object that
         * have a root, as an array of `UiObject`; unlike `auto.windowRoots`, the window filter of
         * `auto.setWindowFilter` does not apply. zh-CN: [findWindows] 的根节点 (Roadmap P5.3): 通过过滤器对象且有根节点的窗口,
         * 为 `UiObject` 数组; 与 `auto.windowRoots` 不同, `auto.setWindowFilter` 的窗口过滤器不生效.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findWindowRoots(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (options) = it
            val match = WindowMatchArguments.parse(options, "auto.findWindowRoots")
            val bridge = scriptRuntime.accessibilityBridge
            bridge.windows()
                .filter { window -> match.matches(window, A11yWindowFacts) }
                .mapNotNull { window -> runCatching { window.root }.getOrNull() }
                .map { root -> UiObject.createRoot(root, null, bridge) }
                .toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun launchSettings(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.launchSettings()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clearCache(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            accessibilityTool.clearCache()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            scriptRuntime.info.latestPackage
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            scriptRuntime.info.latestActivity
        }

        /**
         * `auto.explain(selector, root?)`: the per-filter tallies of a selector over the window roots
         * (or under `root`); the result's `toString()` is the text report. See [A11yDiagnosticsApi].
         * zh-CN: `auto.explain(selector, root?)`: 选择器在各窗口根 (或 `root` 之下) 的逐过滤器计数; 结果的 `toString()` 为文本报告. 见 [A11yDiagnosticsApi].
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun explain(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..2) {
            A11yDiagnosticsApi(scriptRuntime).explain(it.getOrNull(0), it.getOrNull(1))
        }

        /**
         * `auto.dump(options?)`: the window roots (or `options.root`) as text / JSON / XML. See
         * [A11yDiagnosticsApi] and [DumpArguments].
         * zh-CN: `auto.dump(options?)`: 各窗口根 (或 `options.root`) 的文本 / JSON / XML. 见 [A11yDiagnosticsApi] 与 [DumpArguments].
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun dump(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) {
            A11yDiagnosticsApi(scriptRuntime).dump(it.getOrNull(0))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentComponent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            val latestPackage = scriptRuntime.info.latestPackage.takeUnless { it.isEmpty() } ?: return@ensureArgumentsIsEmpty ""
            val latestActivity = scriptRuntime.info.latestActivity.takeUnless { it.isEmpty() } ?: return@ensureArgumentsIsEmpty ""
            "$latestPackage/$latestActivity"
        }

        private fun ensureA11yServiceStarted(scriptRuntime: ScriptRuntime, isForcibleRestart: Any?) {
            if (isForcibleRestart !is Boolean) throw WrappedIllegalArgumentException("Argument isForcibleRestart must be of type Boolean")
            scriptRuntime.accessibilityBridge.ensureServiceStarted(isForcibleRestart)
        }

    }

}
