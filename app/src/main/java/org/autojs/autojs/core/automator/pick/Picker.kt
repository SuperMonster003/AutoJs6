package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.selector.SelectorMapException
import org.autojs.autojs.core.automator.selector.SelectorMapParser
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.regexp.NativeRegExp
import java.math.BigInteger

/**
 * `pickup(root?, selector, compass?, resultType?, callback?)`: the [Builder] turns the selector
 * argument (string / regex for content, `UiSelector`, map, list, number) into one [UiSelector] on
 * the calling script's bridge, and [pick] searches it (under [root] when given) and shapes the
 * result with a [Detector].
 * zh-CN: `pickup(root?, selector, compass?, resultType?, callback?)`: [Builder] 把选择器参数 (字符串 / 正则为 content,
 * `UiSelector`, 集合, 列表, 数字) 转换为调用方脚本 bridge 上的一个 [UiSelector], [pick] 搜索它 (给定 [root] 时在其下)
 * 并以 [Detector] 整形结果.
 *
 * Created by SuperMonster003 on May 12, 2022.
 * Moved out of UiSelector on Sep 6, 2026.
 */
internal class Picker private constructor(
    val root: UiObject?,
    val selector: UiSelector?,
    val compass: CharSequence?,
    val resultType: Any?,
    val callback: Callable? = null,
) {

    internal fun pick(scriptRuntime: ScriptRuntime) = Detector(compass, object : Detector.Source(resultType) {

        override fun byOne(): UiObject? {
            return selector?.let { sel ->
                root?.let { return sel.findOneOf(it) } ?: sel.findOnce()
            }
        }

        override fun byAll(): UiObjectCollection {
            return selector?.let { sel ->
                root?.let { return sel.findOf(it) } ?: sel.find()
            } ?: UiObjectCollection.EMPTY
        }

    }, callback, selector).detect(scriptRuntime)

    // The picker searches with the bridge of the calling script runtime, so that its window
    // filter and blacklist apply and the search keeps working after the service restarts.
    // zh-CN: 拾取器使用调用方脚本运行时的 bridge 进行搜索, 使其窗口过滤器与黑名单生效, 且服务重启后仍可搜索.
    internal class Builder(accessibilityBridge: AccessibilityBridge? = null) {

        private var mRoot: UiObject? = null
        private var mSelector = UiSelector(accessibilityBridge)

        /** The selector built so far (for `auto.explain`). zh-CN: 目前为止构建的选择器 (供 `auto.explain`). */
        internal val selector: UiSelector get() = mSelector
        private var mCompass: CharSequence? = Compass.PASS_ON
        private var mResultType: Any = PickResultType.WIDGET
        private var mCallback: Callable? = null

        fun setRoot(root: UiObject?) = also { mRoot = root }

        fun setSelector(scriptRuntime: ScriptRuntime, selector: Any?) = also {
            when (selector) {
                null -> return@also
                is String -> setSelector(selector)
                is NativeRegExp -> setSelector(selector)
                is UiSelector -> setSelector(selector)
                is Map<*, *> -> setSelector(scriptRuntime, selector)
                is List<*> -> setSelector(scriptRuntime, selector)
                is BigInteger -> setSelector(selector.toString())
                is Double -> setSelector(selector.toInt().toString())
                is NativeJavaMethod -> throw SelectorMethodWithoutCallingException(selector)
                else -> throw IllegalArgumentException(str(R.string.error_unknown_picker_selector_type, selector.toString(), selector::class.java))
            }
        }

        private fun setSelector(selector: UiSelector) = also {
            mSelector.append(selector)
        }

        private fun setSelector(selector: String) = also {
            mSelector.append(UiSelector().content(selector))
        }

        private fun setSelector(selector: NativeRegExp) = also {
            mSelector.append(UiSelector().contentMatch(selector))
        }

        private fun setSelector(scriptRuntime: ScriptRuntime, selector: List<*>): Builder = also {
            selector.filterNotNull().forEach { setSelector(scriptRuntime, it) }
        }

        private fun setSelector(scriptRuntime: ScriptRuntime, selector: Map<*, *>) = also {
            val parser = SelectorMapParser(SelectorRegistry) { value ->
                (value as? Callable)?.let { func ->
                    BooleanFilter.BooleanSupplier { node ->
                        RhinoUtils.coerceBoolean(RhinoUtils.callFunction(scriptRuntime, func, null as VarScope?, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(node)))
                    }
                }
            }
            try {
                parser.parse(selector, UiSelector())
            } catch (e: SelectorMapException.NoSuchProperty) {
                throw IllegalArgumentException(str(R.string.error_no_such_selector_method, e.key, e.value.toString(), e.value?.let { it::class.java } ?: "null"))
            } catch (e: SelectorMapException.IllegalValue) {
                throw IllegalMapSelectorException(e.key, e.value ?: "null")
            }.let { mSelector.append(it) }
        }

        fun setCompass(compass: CharSequence?) = also { compass?.let { mCompass = it } }

        fun setResultType(resultType: Any?) = also { resultType?.let { mResultType = it } }

        fun setCallback(callback: Callable?) = also { callback?.let { mCallback = it } }

        fun build() = Picker(mRoot, mSelector, mCompass, mResultType, mCallback)

    }

    internal class IllegalMapSelectorException(key: String, value: Any) : IllegalArgumentException(
        str(R.string.error_invalid_selector_map_element, key, value.run {
            when (this) {
                is NativeArray -> "[${joinToString { it.toString() }}]"
                else -> toString()
            }
        }, value::class.java)
    )

    internal class SelectorMethodWithoutCallingException(selector: NativeJavaMethod) : IllegalArgumentException(
        str(R.string.error_selector_method_without_calling, selector.toString())
    )

}
