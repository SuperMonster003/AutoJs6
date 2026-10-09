package org.autojs.autojs.core.plugin.typescript

import android.util.Log

/**
 * One structured logcat line per TypeScript script launch, emitted after the engine exists.
 *
 * `typescript-compile-perf` stops when the compiler client returns; this line covers the rest of
 * the user-visible wait: the complete `prepare` call (which includes that compilation), the
 * optional hand-off to the UI thread, and script engine creation. `userWaitMs` is their sum.
 * The line never contains source text, paths or execution identifiers.
 */
internal object TypeScriptExecutionPerformance {

    const val LOG_TAG = "TypeScriptCompiler"
    const val SCHEMA_REVISION = 1
    const val ENTRY_THREAD = "thread"
    const val ENTRY_ACTIVITY = "activity"

    fun logLine(
        entry: String,
        prepareMs: Long,
        dispatchMs: Long,
        engineCreateMs: Long,
    ): String {
        require(entry.isNotBlank() && entry.none(Char::isWhitespace))
        val prepare = prepareMs.coerceAtLeast(0L)
        val dispatch = dispatchMs.coerceAtLeast(0L)
        val engineCreate = engineCreateMs.coerceAtLeast(0L)
        return buildString {
            append("typescript-execution-perf")
            append(" schema=").append(SCHEMA_REVISION)
            append(" entry=").append(entry)
            append(" prepareMs=").append(prepare)
            append(" dispatchMs=").append(dispatch)
            append(" engineCreateMs=").append(engineCreate)
            append(" userWaitMs=").append(prepare + dispatch + engineCreate)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun report(
        entry: String,
        prepareMs: Long,
        engineCreateMs: Long,
        dispatchMs: Long = 0L,
    ) {
        runCatching {
            Log.i(LOG_TAG, logLine(entry, prepareMs, dispatchMs, engineCreateMs))
        }
    }
}
