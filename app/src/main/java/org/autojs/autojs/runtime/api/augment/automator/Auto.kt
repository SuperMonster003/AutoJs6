package org.autojs.autojs.runtime.api.augment.automator

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityBridge.WindowFilter
import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.SimpleActionAutomator.Companion.AccessibilityEventCallback
import org.autojs.autojs.core.automator.AccessibilityEventWrapper
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.util.Java
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.function.Supplier

@Suppress("unused", "UNUSED_PARAMETER")
class Auto(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "service" to Supplier {
            scriptRuntime.accessibilityBridge.service
        },
        "services" to Supplier {
            accessibilityTool.getServices().toNativeArray()
        },
        "windows" to Supplier {
            scriptRuntime.accessibilityBridge.service?.run { Java.toJsArrayRhino(windows, true) } ?: newNativeArray()
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
    )

    override val selfAssignmentFunctions = listOf(
        ::start.name,
        ::stop.name,
        ::isRunning.name,
        ::exists.name,
        ::stateListener.name,
        ::registerEvent.name,
        ::registerEvents.name,
        ::removeEvent.name,
        ::removeEvents.name,
        ::waitFor.name,
        ::setMode.name,
        ::setFlags.name,
        ::setWindowFilter.name,
        ::launchSettings.name,
        ::clearCache.name,
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

    companion object : FlexibleArray() {

        val accessibilityTool by lazy { AccessibilityTool(globalContext) }

        private val modes = hashMapOf(
            "normal" to AccessibilityBridge.MODE_NORMAL,
            "fast" to AccessibilityBridge.MODE_FAST,
        )

        private val flags = hashMapOf(
            "findOnUiThread" to AccessibilityBridge.FLAG_FIND_ON_UI_THREAD,
            "useUsageStats" to AccessibilityBridge.FLAG_USE_USAGE_STATS,
            "useShell" to AccessibilityBridge.FLAG_USE_SHELL,
        )

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun start(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.startService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stop(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.stopService()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isRunning(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.isServiceRunning()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun exists(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            accessibilityTool.serviceExists()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stateListener(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) {
            val (listener) = it
            scriptRuntime.accessibilityBridge.setAccessibilityListener(when {
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

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun registerEvent(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsLength(args, 2) {
            val (name, listener) = it
            require(!name.isJsNullish()) { "Argument \"name\" for auto.registerEvent cannot be nullish" }
            scriptRuntime.automator.registerEvent(Context.toString(name), when {
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
            })
        }

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
            scriptRuntime.accessibilityBridge.setFlags(flagsInt)
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
                filter is BaseFunction -> accessibilityBridge.setWindowFilter { info ->
                    Context.toBoolean(callFunction(scriptRuntime, filter, null, scriptRuntime.topLevelScope, arrayOf(info)))
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

        private fun ensureA11yServiceStarted(scriptRuntime: ScriptRuntime, isForcibleRestart: Any?) {
            if (isForcibleRestart !is Boolean) throw WrappedIllegalArgumentException("Argument isForcibleRestart must be of type Boolean")
            scriptRuntime.accessibilityBridge.ensureServiceStarted(isForcibleRestart)
        }

    }

}