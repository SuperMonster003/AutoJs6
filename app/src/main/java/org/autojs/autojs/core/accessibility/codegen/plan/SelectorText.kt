package org.autojs.autojs.core.accessibility.codegen.plan

import org.autojs.autojs.core.automator.filter.JsLiteral

/** The sole selector-text renderer for plans. zh-CN: 定位计划的唯一选择器文本出口. */
object SelectorText {
    fun render(spec: SelectorSpec): String = spec.conditions.joinToString(".") { condition ->
        condition.attr + "(" + when (val value = condition.value) {
            is String -> JsLiteral.quote(value)
            is Boolean, is Int -> value.toString()
            else -> error("Unsupported selector literal type")
        } + ")"
    }.ifEmpty { "selector()" }
}
