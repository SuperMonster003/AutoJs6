package org.autojs.autojs.core.automator.filter

/** Shared JavaScript string literals for selectors and generated code.
 * zh-CN: 选择器与生成代码共用的 JavaScript 字符串字面量. */
object JsLiteral {

    fun quote(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.isISOControl() || ch == '\u2028' || ch == '\u2029') {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }
}
