package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsUndefined
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * Turns what a [Source] finds into the value a `pickup` / `detect` result type asks for: walks the
 * [compass] from the found node(s), shapes the result per [PickResultType], runs the optional
 * [callback] on it, and hands the value to the script. `pickup` feeds a selector search in,
 * `detect` a single node (see [detect]).
 * zh-CN: 把 [Source] 找到的内容转换为 `pickup` / `detect` 结果类型所要求的值: 从找到的节点起走 [compass], 按
 * [PickResultType] 整形, 对结果执行可选的 [callback], 再交给脚本. `pickup` 送入选择器搜索, `detect` 送入单个节点
 * (见 [detect]).
 *
 * Created by SuperMonster003 on May 12, 2022.
 * Moved out of UiObject on Sep 6, 2026.
 */
internal class Detector(
    private val compass: CharSequence? = Compass.PASS_ON,
    private val source: Source,
    val callback: Callable? = null,
    private val selector: UiSelector? = UiSelector(),
) {

    /** What the result is drawn from: one node, or all of them. zh-CN: 结果的来源: 单个节点, 或全部节点. */
    internal abstract class Source(val type: Any? = PickResultType.WIDGET) {

        abstract fun byOne(): UiObject?

        abstract fun byAll(): UiObjectCollection

    }

    fun detect(scriptRuntime: ScriptRuntime): Any? {
        val resultType = source.type.takeUnless { it == "" } ?: return null
        return when (val type = PickResultType.of(resultType)) {
            PickResultType.Nothing -> null
            PickResultType.Widget -> getUiObject()
            PickResultType.WidgetCollection -> getUiObjectCollection { it?.compass(compass) }
            PickResultType.Widgets -> getUiObjectArray { it?.compass(compass) }

            PickResultType.Content -> getUiObject()?.content() ?: ""
            PickResultType.Contents -> getUiObjectArray { it?.compass(compass)?.content() }

            PickResultType.Point -> getUiObject()?.point()
            PickResultType.Points -> getUiObjectArray { it?.compass(compass)?.point() }

            PickResultType.Selector -> when (compass) {
                null, Compass.PASS_ON -> selector
                else -> throw IllegalArgumentException(str(R.string.error_selector_result_type_with_compass))
            }

            PickResultType.Existence -> when (compass) {
                null, Compass.PASS_ON -> selector?.exists() == true
                else -> getUiObject() != null
            }

            is PickResultType.Invocation -> when (resultType) {
                is List<*> -> invoke(type)
                else -> invoke(type.name, emptyArray())
            }

            is PickResultType.Unsupported -> null
        }.let { result ->
            if (callback.isJsNullish()) result else {
                detectWithCallback(scriptRuntime, callback, result).let {
                    if (it.isJsUndefined()) result else it
                }
            }
        }.let { Context.javaToJS(it, scriptRuntime.topLevelScope) }
    }

    private fun invoke(name: String, args: Array<Any?>) = Invoker(getUiObject(), name, args).invoke()

    // A one-element list whose head is no method of the node falls back to the array form of the
    // symbol (`["#"]` = widgets); with arguments the failure propagates.
    // zh-CN: 首项不是节点方法的单元素列表回退到符号的数组形式 (`["#"]` = 控件数组); 带参数时失败原样抛出.
    private fun invoke(invocation: PickResultType.Invocation) = try {
        invoke(invocation.name, invocation.arguments.toTypedArray())
    } catch (e: Exception) {
        when {
            invocation.arguments.isEmpty() -> invokeAsUiObjectArray(invocation.name, e)
            else -> throw e
        }
    }

    private fun invokeAsUiObjectArray(head: String, e: Exception) = when (PickResultType.arrayFormOf(head)) {
        PickResultType.Widgets -> getUiObjectArray { it?.compass(compass) }
        PickResultType.Contents -> getUiObjectArray { it?.compass(compass)?.content() }
        PickResultType.Points -> getUiObjectArray { it?.compass(compass)?.point() }
        else -> throw e
    }

    private fun getUiObject() = source.byOne()?.compass(compass)

    private fun getUiObjectCollection(transformer: (UiObject?) -> UiObject?) = UiObjectCollection
        .transform(source.byAll(), transformer)
        .let { RhinoUtils.wrap(it) }

    private fun getUiObjectArray(transformer: (UiObject?) -> Any?) = UiObjectCollection
        .mapNotNull(source.byAll(), transformer)
        .toNativeArray()

    private fun detectWithCallback(scriptRuntime: ScriptRuntime, callback: Callable?, args: Array<Any?>) = callback?.let {
        RhinoUtils.callFunction(
            scriptRuntime,
            it,
            null as VarScope?,
            Undefined.SCRIPTABLE_UNDEFINED,
            args,
        )
    }

    private fun detectWithCallback(scriptRuntime: ScriptRuntime, callback: Callable?, arg: Any?) = callback?.let {
        detectWithCallback(scriptRuntime, it, arrayOf(arg))
    }

    companion object {

        /**
         * `detect(w, compass, resultType, callback)`: the result type applied to one node; the
         * plural forms are not available and fail with a readable error.
         * zh-CN: `detect(w, compass, resultType, callback)`: 对单个节点应用结果类型; 复数形式不可用, 报可读错误.
         */
        fun detect(scriptRuntime: ScriptRuntime, w: UiObject?, compass: CharSequence?, resultType: Any?, callback: Callable? = null): Any? {
            return Detector(compass, object : Source(resultType) {

                override fun byOne() = w

                override fun byAll() = throw Invoker.IllegalResultTypeException(resultType.toString())

            }, callback).detect(scriptRuntime)
        }

    }

}
