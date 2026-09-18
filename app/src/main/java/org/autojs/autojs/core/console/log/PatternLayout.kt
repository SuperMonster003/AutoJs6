package org.autojs.autojs.core.console.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One log line before layout. zh-CN: 布局前的一条日志. */
class LogEvent(
    val level: Level,
    val message: String,
    val loggerName: String,
    /** What `%C` renders; the logger's class when it was created from one (no stack walking). */
    val className: String = loggerName,
    val threadName: String = Thread.currentThread().name,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * The subset of log4j 1.2 `PatternLayout` the console documentation points to: `%d` / `%d{fmt}` (with the named
 * `ISO8601`, `ABSOLUTE`, `DATE` formats), `%p`, `%c` / `%c{n}`, `%C` / `%C{n}`, `%m`, `%n`, `%t`, `%r`, `%%`, the
 * location conversions `%F` `%l` `%L` `%M` (rendered as `?`, no caller lookup), `%x` / `%X{key}` (empty) and the
 * format modifiers `%-5p`, `%20c`, `%.30c`. An unknown conversion is written out as it appears in the pattern.
 *
 * zh-CN: 控制台文档所指 log4j 1.2 `PatternLayout` 的子集: `%d` / `%d{fmt}` (含具名格式 `ISO8601`, `ABSOLUTE`, `DATE`),
 * `%p`, `%c` / `%c{n}`, `%C` / `%C{n}`, `%m`, `%n`, `%t`, `%r`, `%%`, 位置转换符 `%F` `%l` `%L` `%M` (输出 `?`, 不查调用栈),
 * `%x` / `%X{key}` (空) 以及宽度修饰 `%-5p`, `%20c`, `%.30c`. 未知转换符按模式中的原样输出.
 */
class PatternLayout(val pattern: String = DEFAULT_PATTERN) {

    private val segments: List<Segment> = parse(pattern)

    fun format(event: LogEvent): String {
        val out = StringBuilder(64)
        segments.forEach { it.appendTo(out, event) }
        return out.toString()
    }

    private sealed class Segment {
        abstract fun appendTo(out: StringBuilder, event: LogEvent)

        class Literal(private val text: String) : Segment() {
            override fun appendTo(out: StringBuilder, event: LogEvent) {
                out.append(text)
            }
        }

        class Conversion(
            private val conversion: Char,
            private val option: String?,
            private val minWidth: Int,
            private val maxWidth: Int,
            private val leftAlign: Boolean,
        ) : Segment() {
            private val dateFormat: SimpleDateFormat? = if (conversion == 'd') SimpleDateFormat(dateFormatPattern(option), Locale.ROOT) else null

            override fun appendTo(out: StringBuilder, event: LogEvent) {
                var text = when (conversion) {
                    'c' -> precision(event.loggerName, option)
                    'C' -> precision(event.className, option)
                    'd' -> synchronized(this) { dateFormat!!.format(Date(event.timestamp)) }
                    'm' -> event.message
                    'n' -> LINE_SEPARATOR
                    'p' -> event.level.toString()
                    'r' -> (event.timestamp - START_TIME).toString()
                    't' -> event.threadName
                    'F', 'l', 'L', 'M' -> "?"
                    'x', 'X' -> ""
                    else -> throw IllegalStateException("Unsupported conversion $conversion")
                }
                if (maxWidth > 0 && text.length > maxWidth) {
                    // log4j truncates from the beginning, keeping the tail.
                    // zh-CN: log4j 从开头截断, 保留结尾.
                    text = text.substring(text.length - maxWidth)
                }
                val padding = minWidth - text.length
                if (padding <= 0) {
                    out.append(text)
                } else if (leftAlign) {
                    out.append(text)
                    repeat(padding) { out.append(' ') }
                } else {
                    repeat(padding) { out.append(' ') }
                    out.append(text)
                }
            }

            private fun precision(name: String, option: String?): String {
                val count = option?.toIntOrNull() ?: return name
                if (count <= 0) return name
                var index = name.length
                repeat(count) {
                    index = name.lastIndexOf('.', index - 1)
                    if (index < 0) return name
                }
                return name.substring(index + 1)
            }
        }
    }

    companion object {
        const val DEFAULT_PATTERN = "%m%n"
        const val ISO8601_PATTERN = "yyyy-MM-dd HH:mm:ss,SSS"
        const val ABSOLUTE_PATTERN = "HH:mm:ss,SSS"
        const val DATE_PATTERN = "dd MMM yyyy HH:mm:ss,SSS"
        private const val LINE_SEPARATOR = "\n"
        private const val CONVERSIONS = "cCdmnprtFlLMxX"
        private val START_TIME = System.currentTimeMillis()

        private fun dateFormatPattern(option: String?): String {
            val name = option?.trim().orEmpty()
            return when (name) {
                "", "ISO8601" -> ISO8601_PATTERN
                "ABSOLUTE" -> ABSOLUTE_PATTERN
                "DATE" -> DATE_PATTERN
                else -> runCatching { SimpleDateFormat(name, Locale.ROOT); name }.getOrDefault(ISO8601_PATTERN)
            }
        }

        private fun parse(pattern: String): List<Segment> {
            val segments = ArrayList<Segment>()
            val literal = StringBuilder()
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                if (c != '%') {
                    literal.append(c)
                    i++
                    continue
                }
                val start = i
                i++
                if (i >= pattern.length) {
                    literal.append('%')
                    break
                }
                if (pattern[i] == '%') {
                    literal.append('%')
                    i++
                    continue
                }
                var leftAlign = false
                if (pattern[i] == '-') {
                    leftAlign = true
                    i++
                }
                var minWidth = 0
                while (i < pattern.length && pattern[i].isDigit()) {
                    minWidth = minWidth * 10 + (pattern[i] - '0')
                    i++
                }
                var maxWidth = 0
                if (i < pattern.length && pattern[i] == '.') {
                    i++
                    while (i < pattern.length && pattern[i].isDigit()) {
                        maxWidth = maxWidth * 10 + (pattern[i] - '0')
                        i++
                    }
                }
                if (i >= pattern.length || pattern[i] !in CONVERSIONS) {
                    // Unknown conversion: keep the pattern text as it is.
                    // zh-CN: 未知转换符: 保留模式原文.
                    val end = if (i < pattern.length) i + 1 else pattern.length
                    literal.append(pattern, start, end)
                    i = end
                    continue
                }
                val conversion = pattern[i]
                i++
                var option: String? = null
                if (i < pattern.length && pattern[i] == '{' && conversion in "cCdX") {
                    val close = pattern.indexOf('}', i + 1)
                    if (close > 0) {
                        option = pattern.substring(i + 1, close)
                        i = close + 1
                    }
                }
                if (literal.isNotEmpty()) {
                    segments.add(Segment.Literal(literal.toString()))
                    literal.setLength(0)
                }
                segments.add(Segment.Conversion(conversion, option, minWidth, maxWidth, leftAlign))
            }
            if (literal.isNotEmpty()) segments.add(Segment.Literal(literal.toString()))
            return segments
        }
    }

}
