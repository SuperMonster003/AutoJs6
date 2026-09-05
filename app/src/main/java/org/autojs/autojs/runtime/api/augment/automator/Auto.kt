package org.autojs.autojs.runtime.api.augment.automator

import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.EventKeyFunction
import org.autojs.autojs.core.accessibility.event.A11yEvent
import org.autojs.autojs.core.accessibility.event.A11yEventBus
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
        ::setMode.name,
        ::setFlags.name,
        ::getWindowRoot.name,
        ::setWindowFilter.name,
        ::launchSettings.name,
        ::clearCache.name,
        ::currentPackage.name,
        ::currentActivity.name,
        ::currentComponent.name,
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
            scriptRuntime.accessibilityBridge.getRootInCurrentWindow()?.let { UiObject.createRoot(it) }
        },
        "rootInActiveWindow" to Supplier {
            scriptRuntime.accessibilityBridge.getRootInActiveWindow()?.let { UiObject.createRoot(it) }
        },
        "windowRoots" to Supplier {
            scriptRuntime.accessibilityBridge.windowRoots().map { UiObject.createRoot(it) }.toNativeArray()
        },
        "state" to Supplier {
            newNativeObject(scriptRuntime.topLevelScope).also { o ->
                o.defineProp("hasInstance", accessibilityTool.hasInstance())
                o.defineProp("hasService", accessibilityTool.hasService())
                o.defineProp("isRunning", accessibilityTool.isRunning())
                o.defineProp("isOperational", accessibilityTool.isOperational())
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
        )

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

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitFor(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsAtMost(args, 1) {
            val (timeout) = it
            waitForRhino(timeout)
        }

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
            var flagsInt = 0
            when (it) {
                is NativeArray -> it
                is String -> listOf(it)
                else -> throw WrappedIllegalArgumentException("Unknown flags ($it) for auto.setFlags")
            }.forEach { s ->
                val flag = flags[s] ?: throw WrappedIllegalArgumentException("Unknown flag ($s) for auto.setFlags")
                flagsInt = flagsInt or flag
            }
            scriptRuntime.accessibilityBridge.flags = flagsInt
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getWindowRoot(scriptRuntime: ScriptRuntime, args: Array<out Any?>): UiObject? = ensureArgumentsOnlyOne(args) { window ->
            val windowInfo = window.jsUnwrapped()
            require(windowInfo is AccessibilityWindowInfo) {
                "Argument \"window\" ${window.jsBrief()} for auto.getWindowRoot must be an AccessibilityWindowInfo"
            }
            windowInfo.root?.let { UiObject.createRoot(it) }
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
                filter is Callable -> accessibilityBridge.setWindowFilter { info ->
                    Context.toBoolean(callFunction(scriptRuntime, filter, null as VarScope?, scriptRuntime.topLevelScope.globalThis, arrayOf(info)))
                }
                else -> throw WrappedIllegalArgumentException("Argument filter ($filter) is invalid for auto.setWindowFilter")
            }
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
