package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.rhino.JsArgumentCoercion
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R

/**
 * The "method by name" result type of `pickup` / `detect` ([PickResultType.Invocation]): calls
 * `name(args)` on the node reflectively, then `getName(args)`, coercing the arguments of the
 * methods listed in [Interceptor] to their Java parameter types. Null when there is no node.
 * zh-CN: `pickup` / `detect` 的 "按名称调用方法" 结果类型 ([PickResultType.Invocation]): 以反射调用节点的 `name(args)`,
 * 其次 `getName(args)`; [Interceptor] 中列出的方法的参数按其 Java 参数类型转换. 没有节点时为 null.
 *
 * Created by Stardust on Mar 9, 2017.
 * Moved out of UiObject by SuperMonster003 on Sep 6, 2026.
 */
internal class Invoker(private val w: UiObject?, private val name: String, private val params: Array<Any?>?) {

    fun invoke(): Any? = when {
        w == null -> null
        name.isEmpty() -> null
        else -> Interceptor(name, params).let { interceptor ->
            try {
                invokeMethod(w, interceptor.name, interceptor)
            } catch (e: Exception) {
                e.printStackTrace()
                if (isIgnoredException(e)) return null
                try {
                    val getterName = "get${interceptor.name.replaceFirstChar { s -> s.uppercase() }}"
                    invokeMethod(w, getterName, interceptor)
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (isIgnoredException(e)) return null
                    throw IllegalResultTypeException(name, params)
                }
            }
        }
    }

    private fun invokeMethod(w: UiObject, getterName: String, interceptor: Interceptor): Any? = w.javaClass.getMethod(getterName, *interceptor.classes).invoke(w, *interceptor.args)

    private fun isIgnoredException(e: Exception): Boolean {
        return e.cause is NullPointerException ||
                e.cause is InterruptedException ||
                e.cause?.cause is InterruptedException
    }

    /**
     * The parameter types of the node methods scripts may name, so that JS numbers reach `int` /
     * `float` parameters and strings reach `String` ones; unlisted methods take the arguments as is.
     * zh-CN: 脚本可按名称调用的节点方法的参数类型, 使 JS 数值能到达 `int` / `float` 参数, 字符串能到达 `String` 参数;
     * 未列出的方法按原样传参.
     */
    internal class Interceptor(val name: String, params: Array<Any?>?) {

        var classes: Array<out Class<*>> = emptyArray()
        var args: Array<Any?> = emptyArray()

        init {
            if (params != null) {
                if (has(name)) {
                    classes = interceptedMap[name]!!.args
                    args = params.mapIndexed { i, param ->
                        classes.getOrNull(i)?.let { JsArgumentCoercion.coerce(it, param) } ?: param
                    }.toTypedArray()
                } else {
                    classes = params.map { params.javaClass }.toTypedArray()
                    args = params
                }
            }
        }

        companion object {

            private val cFloatPrim = Float::class.javaPrimitiveType!!
            private val cIntPrim: Class<Int> = Int::class.javaPrimitiveType!!
            private val cString = String::class.java
            private val cCharSequence = CharSequence::class.java
            private val cBoolean = Boolean::class.java

            val interceptedMap: Map<String, Intercepted> by lazy {
                val numberClassMap: Map<Array<out Class<*>>, Array<String>> = mapOf(
                    arrayOf(cIntPrim) to arrayOf(
                        "child", "getChild", "performAction", "setDrawingOrder", "setInputType",
                        "setLiveRegion", "setMaxTextLength", "setMovementGranularities",
                    ),
                    // `compass` declares CharSequence; listing it under String made `["compass", "p"]` unresolvable.
                    // zh-CN: `compass` 声明的是 CharSequence; 原先列在 String 下使 `["compass", "p"]` 无法解析.
                    arrayOf(cCharSequence) to arrayOf("compass"),
                    arrayOf(cString) to arrayOf(
                        "findAccessibilityNodeInfosByText",
                        "findAccessibilityNodeInfosByViewId",
                        "setClassName",
                        "setContentDescription",
                        "setError",
                        "setHintText",
                        "setPackageName",
                        "setPaneTitle",
                        "setRoleDescription",
                        "setStateDescription",
                        "setText",
                        "setTooltipText",
                        "setViewIdResourceName",
                    ),
                    arrayOf(cBoolean) to arrayOf(
                        "setAccessibilityFocused",
                        "setCanOpenPopup",
                        "setCheckable",
                        "setChecked",
                        "setClickable",
                        "setEditable",
                        "setEnabled",
                        "setFocusable",
                        "setFocused",
                        "setHeading",
                        "setImportantForAccessibility",
                        "setLongClickable",
                        "setMultiLine",
                        "setPassword",
                        "setScreenReaderFocusable",
                        "setScrollable",
                        "setSelected",
                        "setShowingHintText",
                        "setTextEntryKey",
                        "setVisibleToUser",
                        "setContentInvalid",
                        "setContextClickable",
                        "setDismissable",
                    ),
                    arrayOf(cFloatPrim) to arrayOf("setProgress"),
                    arrayOf(cIntPrim, cIntPrim) to arrayOf("setTextSelection", "setSelection", "scrollTo"),
                )
                numberClassMap.entries.map { entry: Map.Entry<Array<out Class<*>>, Array<String>> ->
                    entry.value.map { Intercepted(it, *entry.key) }
                }.flatten().associateBy { it.name }
            }

            fun has(name: String) = interceptedMap.containsKey(name)

        }

    }

    internal class Intercepted(val name: String, vararg val args: Class<*>)

    internal class IllegalResultTypeException : IllegalArgumentException {

        constructor(s: String) : super(str(R.string.error_illegal_ui_object_result_type, s))

        constructor(resultType: String, resultParams: Array<Any?>?) : super(
            when (resultParams?.isNotEmpty()) {
                true -> str(
                    R.string.error_unknown_picker_result_type_with_params,
                    resultType,
                    "[${resultParams.joinToString { it.toString() }}]"
                )
                else -> str(R.string.error_unknown_picker_result_type, resultType)
            }
        )

    }

}
