package org.autojs.autojs.core.automator.filter

import java.util.regex.Pattern

internal object JsRegexUtils {

    // Compile JavaScript regex literal "/pattern/flags" or plain pattern string to Kotlin Regex.
    // zh-CN: 将 JavaScript 正则字面量 "/pattern/flags" 或普通模式字符串编译为 Kotlin 的 Regex.
    fun compileJsOrPlainRegex(input: String): Regex {
        parseJsRegexLiteral(input)?.let { (pattern, flags) ->
            val javaFlags = toJavaPatternFlags(flags)
            return Pattern.compile(pattern, javaFlags).toRegex()
        }
        return input.toRegex()
    }

    // Parse JavaScript regex literal by scanning delimiter '/' while respecting escapes and character classes.
    // zh-CN: 通过扫描分隔符 '/' 解析 JavaScript 正则字面量, 同时处理转义与字符类.
    fun parseJsRegexLiteral(input: String): Pair<String, String>? {
        if (input.length < 2 || input[0] != '/') return null

        var escaped = false
        var inCharClass = false

        for (i in 1 until input.length) {
            val ch = input[i]
            when {
                escaped -> escaped = false
                ch == '\\' -> escaped = true
                ch == '[' -> inCharClass = true
                ch == ']' && inCharClass -> inCharClass = false
                ch == '/' && !inCharClass -> {
                    val pattern = input.substring(1, i)
                    val flags = input.substring(i + 1)
                    return pattern to flags
                }
            }
        }
        return null
    }

    // Map JavaScript flags to java.util.regex.Pattern flags. Unknown flags are ignored.
    // zh-CN: 将 JavaScript flags 映射到 java.util.regex.Pattern flags, 未知 flags 将被忽略.
    fun toJavaPatternFlags(flags: String): Int {
        var f = 0
        if (flags.contains('i')) f = f or Pattern.CASE_INSENSITIVE
        if (flags.contains('m')) f = f or Pattern.MULTILINE
        if (flags.contains('s')) f = f or Pattern.DOTALL

        // Java "u" is not identical to JavaScript "u", but enabling Unicode-aware behavior is usually closer.
        // zh-CN: Java 的 "u" 与 JavaScript 的 "u" 并不完全等价, 但开启 Unicode 相关行为通常更接近预期.
        if (flags.contains('u')) {
            f = f or Pattern.UNICODE_CASE
            f = f or Pattern.UNICODE_CHARACTER_CLASS
        }

        // JavaScript "g/y" affects iteration/sticky behavior rather than "does it match", so it is ignored here.
        // zh-CN: JavaScript 的 "g/y" 影响的是迭代/粘连行为, 而不是 "是否匹配", 因此这里忽略.

        return f
    }

    // Format input regex as a JavaScript regex literal string for debugging display.
    // zh-CN: 将输入正则格式化为 JavaScript 正则字面量字符串, 用于调试展示.
    fun formatAsJsRegexLiteral(input: String): String {
        val (pattern, flags) = parseJsRegexLiteral(input) ?: (input to "")
        val safePattern = when (pattern.isEmpty()) {
            true -> "(?:)"
            else -> pattern.toRegex().toString().replace("/", "\\/")
        }
        return "/$safePattern/$flags"
    }

}
