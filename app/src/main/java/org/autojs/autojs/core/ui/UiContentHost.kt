package org.autojs.autojs.core.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Window
import org.autojs.autojs.core.ui.inflater.AttributeOutcome
import org.autojs.autojs.execution.ScriptExecuteActivity
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Where `ui.layout(...)` content goes: the script's [ScriptExecuteActivity] window, or an embedded /
 * offscreen root owned by the ui-mode preview service (3-Block Compose roadmap P10.2, D46).
 *
 * The `ui` module resolves its host with [of]: a host attached to the engine as the tag [ENGINE_TAG]
 * takes precedence, otherwise the script's global `activity` (a [ScriptExecuteActivity]) is the host.
 * Functions that need a window ([hostWindow] is null for a preview root) are no-ops on a preview host
 * and report themselves through [onWindowFunctionIgnored], so a script that is previewed and a script
 * that runs in an Activity see the same `ui.layout` semantics while window-level calls degrade visibly.
 *
 * zh-CN: `ui.layout(...)` 内容的承载: 脚本的 [ScriptExecuteActivity] 窗口, 或 ui 模式预览服务持有的嵌入 / 离屏根容器
 * (3-Block Compose 路线图 P10.2, D46). `ui` 模块经 [of] 取承载: 以 [ENGINE_TAG] 附着在引擎上的承载优先, 否则全局 `activity`
 * 即承载. 需要窗口的函数在预览承载下空操作并经 [onWindowFunctionIgnored] 报告.
 */
interface UiContentHost {

    /** The themed context layouts are inflated with. */
    val inflaterContext: Context

    /** The parent passed to the inflater for layout params; content is not attached to it by the inflater. */
    val contentParent: ViewGroup

    /** The root that holds the content after [showContent], the value of `ui.root`. */
    val contentRoot: ViewGroup?

    /** The host window, or null when the host has no window (window-level `ui` functions are then ignored). */
    val hostWindow: Window?

    /** Makes [view] the content (`Activity.setContentView` for the Activity host); called on the main thread. */
    fun showContent(view: View)

    /** Called instead of a window-level `ui` function ([functionName]) that has no effect on this host. */
    fun onWindowFunctionIgnored(functionName: String) {
        /* Empty default: the Activity host never ignores anything. */
    }

    /**
     * A `{{ expression }}` of [attrName] failed to evaluate. Returning true applies the raw attribute text
     * instead and lets the inflation continue; false rethrows [error] (the script behavior).
     */
    fun onBindingFailed(attrName: String, rawValue: String, error: Throwable): Boolean = false

    /**
     * An attribute of a native Android XML layout had no effect and was skipped (host roadmap
     * `docs/dev/android-xml-attributes-roadmap.md` A4): [outcome] is [AttributeOutcome.UNSUPPORTED] or
     * [AttributeOutcome.UNKNOWN], [message] the warning the script's console got.
     */
    fun onAttributeIgnored(attrName: String, outcome: AttributeOutcome, message: String) {
        /* Empty default: the Activity host only has the console warning. */
    }

    /** The engine of this host is being force-stopped (host task list, `engines.stopAll()`). */
    fun onEngineForceStop() {
        /* Empty default. */
    }

    companion object {

        /** Engine tag of a host that replaces the Activity, set before the engine is initialized. */
        const val ENGINE_TAG = "ui.content.host"

        /** The host attached to the engine as [ENGINE_TAG], or null. */
        @JvmStatic
        fun attached(scriptRuntime: ScriptRuntime): UiContentHost? = try {
            scriptRuntime.engines.myEngine().getTag(ENGINE_TAG) as? UiContentHost
        } catch (e: Throwable) {
            null
        }

        /**
         * The attached host, otherwise the script's global `activity` when it is a [ScriptExecuteActivity];
         * null when neither exists. A global `activity` of another type is left to the caller's own check.
         */
        @JvmStatic
        fun of(scriptRuntime: ScriptRuntime): UiContentHost? {
            attached(scriptRuntime)?.let { return it }
            val activity = scriptRuntime.topLevelScope.prop("activity").jsUnwrapped()
            if (activity.isJsNullish()) return null
            return activity as? ScriptExecuteActivity
        }
    }
}
