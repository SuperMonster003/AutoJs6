package org.autojs.autojs.engine

/**
 * Bounded tail selection over console log entries, shared by the Node Bridge `console.tail`
 * method and the `captureConsole` option of `engines.execScript` / `engines.execScriptFile`
 * (MCP roadmap P1.3). Android-free so the policy is unit-testable; the bridge maps
 * `ConsoleImpl.LogEntry` to [Entry]. Selection keeps the newest entries that fit the line and
 * byte budgets, in id order.
 *
 * zh-CN: 控制台日志条目的有界尾部选择, 供 Node Bridge `console.tail` 与 `engines.execScript` / `engines.execScriptFile` 的 `captureConsole`
 * 选项共用 (MCP 路线图 P1.3). Android-free 以便单元测试; bridge 把 `ConsoleImpl.LogEntry` 映射为 [Entry]. 选择结果按 id 顺序保留符合行数与字节预算的最新条目.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
object NodeBridgeConsoleTail {

    const val MAX_LINES = 500
    const val DEFAULT_LINES = 200
    const val MAX_TEXT_BYTES = 256 * 1024
    const val NO_ID = -1

    /** Mirrors `android.util.Log` priorities without depending on Android. zh-CN: 与 `android.util.Log` 的优先级一致, 但不依赖 Android. */
    const val LEVEL_VERBOSE = 2
    const val LEVEL_DEBUG = 3
    const val LEVEL_INFO = 4
    const val LEVEL_WARN = 5
    const val LEVEL_ERROR = 6
    const val LEVEL_ASSERT = 7

    class Entry(val id: Int, val level: Int, val text: String, val timestamp: Long = 0L)

    /**
     * @property truncated entries were dropped by the line or byte budget (or the newest entry was cut).
     * @property evicted entries newer than `sinceId` were already dropped by the console's soft cap or cleared.
     */
    class Selection(val entries: List<Entry>, val truncated: Boolean, val evicted: Boolean, val bytes: Int) {
        val firstId: Int get() = entries.firstOrNull()?.id ?: NO_ID
        val lastId: Int get() = entries.lastOrNull()?.id ?: NO_ID
    }

    fun levelName(level: Int): String = when (level) {
        LEVEL_VERBOSE -> "verbose"
        LEVEL_DEBUG -> "debug"
        LEVEL_INFO -> "info"
        LEVEL_WARN -> "warn"
        LEVEL_ERROR -> "error"
        LEVEL_ASSERT -> "assert"
        else -> "unknown"
    }

    /** A priority 2..7 or a level name (`verbose`, `debug`, `info`, `warn`, `error`, `assert` or their initials); null when unrecognised. */
    fun parseLevel(value: Any?): Int? = when (value) {
        is Number -> value.toInt().takeIf { it in LEVEL_VERBOSE..LEVEL_ASSERT }
        is String -> when (value.trim().lowercase()) {
            "verbose", "v" -> LEVEL_VERBOSE
            "debug", "d" -> LEVEL_DEBUG
            "info", "i" -> LEVEL_INFO
            "warn", "warning", "w" -> LEVEL_WARN
            "error", "e" -> LEVEL_ERROR
            "assert", "a", "wtf" -> LEVEL_ASSERT
            else -> value.trim().toIntOrNull()?.takeIf { it in LEVEL_VERBOSE..LEVEL_ASSERT }
        }
        else -> null
    }

    /**
     * Keeps entries with `sinceId < id <= untilId` and `level >= minLevel`, then the newest [lines]
     * of them that fit into [maxBytes] of UTF-8; a lone newest entry above the budget is cut.
     * zh-CN: 保留 `sinceId < id <= untilId` 且 `level >= minLevel` 的条目, 再取其中符合 [maxBytes] UTF-8 预算的最新 [lines] 条; 单条超出预算时截断该条.
     */
    fun select(
        entries: List<Entry>,
        lines: Int = DEFAULT_LINES,
        sinceId: Int = NO_ID,
        untilId: Int = Int.MAX_VALUE,
        minLevel: Int = LEVEL_VERBOSE,
        maxBytes: Int = MAX_TEXT_BYTES,
    ): Selection {
        val limit = lines.coerceIn(1, MAX_LINES)
        val budget = maxBytes.coerceIn(1, MAX_TEXT_BYTES)
        val evicted = sinceId != NO_ID && entries.isNotEmpty() && entries.first().id > sinceId + 1
        val window = entries.filter { it.id > sinceId && it.id <= untilId && it.level >= minLevel }
        var truncated = window.size > limit
        var chosen: List<Entry> = if (truncated) window.subList(window.size - limit, window.size) else window
        var bytes = 0
        var start = chosen.size
        while (start > 0) {
            val size = utf8Length(chosen[start - 1].text)
            if (bytes + size > budget) break
            bytes += size
            start--
        }
        if (start > 0) {
            truncated = true
            if (start == chosen.size) {
                val newest = chosen.last()
                val text = capUtf8(newest.text, budget).first
                chosen = listOf(Entry(newest.id, newest.level, text))
                bytes = utf8Length(text)
            } else {
                chosen = chosen.subList(start, chosen.size)
            }
        }
        return Selection(chosen, truncated, evicted, bytes)
    }

    fun utf8Length(text: String): Int {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            bytes += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                Character.isHighSurrogate(char) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1]) -> {
                    index++
                    4
                }
                else -> 3
            }
            index++
        }
        return bytes
    }

    /** Cuts [text] to at most [maxBytes] of UTF-8 on a code point boundary; the flag tells whether anything was cut. */
    fun capUtf8(text: String, maxBytes: Int): Pair<String, Boolean> {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val pair = Character.isHighSurrogate(char) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1])
            val size = when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                pair -> 4
                else -> 3
            }
            if (bytes + size > maxBytes) return text.substring(0, index) to true
            bytes += size
            index += if (pair) 2 else 1
        }
        return text to false
    }
}
