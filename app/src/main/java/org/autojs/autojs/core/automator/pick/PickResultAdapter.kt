package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * The Rhino side of `pickup` / `detect` on a device (docs/selector/ROADMAP.md P1.3): normalizes the arguments with
 * [PickArguments.uiObject] on the bridge of the calling script (so that its window filter and blacklist apply and
 * the search keeps working after the service restarts), runs script functions of object selectors and the callback,
 * and gives [PickExecutor] the `UiObject` searches and script values. Everything else of a pick runs in the executor.
 *
 * zh-CN: 设备上 `pickup` / `detect` 的 Rhino 一侧 (P1.3): 以 [PickArguments.uiObject] 在调用方脚本的 bridge 上归一化参数 (使其窗口过滤器与黑名单生效,
 * 且服务重启后仍可搜索), 执行对象选择器中的脚本函数与回调, 并为 [PickExecutor] 提供 `UiObject` 的搜索与脚本值. 拾取的其余部分都在执行器中.
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
internal object PickResultAdapter {

    /** How the requests of [scriptRuntime] build their selectors. zh-CN: [scriptRuntime] 的请求如何构建选择器. */
    fun binding(scriptRuntime: ScriptRuntime) = PickArguments.Binding(
        newSelector = { UiSelector(scriptRuntime.accessibilityBridge) },
        filter = { func ->
            BooleanFilter.BooleanSupplier { node ->
                RhinoUtils.coerceBoolean(RhinoUtils.callFunction(scriptRuntime, func, null as VarScope?, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(node)))
            }
        },
    )

    /** `pickup` of the new script module layer, [args] unwrapped. zh-CN: 新脚本模块层的 `pickup`, [args] 已解包. */
    fun pickup(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? =
        execute(scriptRuntime, PickArguments.uiObject.pickup(args, binding(scriptRuntime)).requestOrThrow())

    /** `detect` of the new script module layer, [args] unwrapped. zh-CN: 新脚本模块层的 `detect`, [args] 已解包. */
    fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? =
        execute(scriptRuntime, PickArguments.uiObject.detect(args).requestOrThrow())

    /** `UiSelector.pickup`, the positions told. zh-CN: `UiSelector.pickup`, 位置已区分. */
    fun legacyPickup(scriptRuntime: ScriptRuntime, root: UiObject?, selector: Any?, compass: CharSequence?, resultType: Any?, callback: Callable?): Any? =
        execute(scriptRuntime, PickArguments.uiObject.legacyPickup(root, selector, compass, resultType, callback, binding(scriptRuntime)).requestOrThrow())

    /** `UiObject.detect`, the positions told. zh-CN: `UiObject.detect`, 位置已区分. */
    fun legacyDetect(scriptRuntime: ScriptRuntime, w: UiObject?, compass: CharSequence?, resultType: Any?, callback: Callable?): Any? =
        execute(scriptRuntime, PickArguments.uiObject.legacyDetect(w, compass, resultType, callback).requestOrThrow())

    private fun execute(scriptRuntime: ScriptRuntime, request: PickRequest<UiObject>): Any? = PickExecutor(request, Target(scriptRuntime, request)).execute()

    private class Target(private val scriptRuntime: ScriptRuntime, private val request: PickRequest<UiObject>) : PickTarget<UiObject> {

        // `detect` has no selector of its own: `@` is a new one and `?` asks it, as the former Detector had them.
        // zh-CN: `detect` 没有自己的选择器: `@` 为新建的选择器, `?` 询问它, 同原 Detector.
        private val selector: UiSelector by lazy { request.selector ?: UiSelector() }

        override fun one(): UiObject? = when (val sel = request.selector) {
            null -> request.root
            else -> request.root?.let { sel.findOneOf(it) } ?: if (request.root == null) sel.findOnce() else null
        }

        override fun all(): List<UiObject> {
            val sel = request.selector ?: return emptyList()
            return (request.root?.let { sel.findOf(it) } ?: sel.find()).nodes.filterNotNull()
        }

        override fun exists() = selector.exists()

        override fun selector(): Any = selector

        override fun collection(nodes: List<UiObject>): Any = RhinoUtils.wrap(UiObjectCollection.of(nodes))

        override fun array(values: List<Any?>): Any = values.toNativeArray()

        override fun point(node: UiObject): Any = node.point()

        override fun call(callback: Callable, value: Any?): Any? =
            RhinoUtils.callFunction(scriptRuntime, callback, null as VarScope?, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(value))

        override fun toScript(value: Any?): Any? = Context.javaToJS(value, scriptRuntime.topLevelScope)

    }

}
