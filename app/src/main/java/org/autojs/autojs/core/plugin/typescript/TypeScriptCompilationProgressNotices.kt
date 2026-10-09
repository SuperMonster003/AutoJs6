package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import android.os.SystemClock
import org.autojs.autojs.AutoJs
import org.autojs.autojs6.R

/**
 * User-visible progress for a remote TypeScript compilation (startup-latency L1-8).
 *
 * A cache miss means the script thread is about to wait for the compiler plugin, possibly
 * including its cold start, so one line goes to the global console as soon as that is known and
 * one more when the compiled output is back. Cache hits stay silent, and nothing here can fail
 * the compilation: the global console may not exist yet in some processes.
 */
internal object TypeScriptCompilationProgressNotices {

    class Token internal constructor(
        internal val startedAtElapsedRealtime: Long,
    )

    fun compilationStarted(context: Context): Token {
        runCatching {
            AutoJs.instance.globalConsole.log(
                context.getString(R.string.text_typescript_compiling),
            )
        }
        return Token(SystemClock.elapsedRealtime())
    }

    fun compilationFinished(context: Context, token: Token) {
        runCatching {
            val elapsedMs = (SystemClock.elapsedRealtime() - token.startedAtElapsedRealtime)
                .coerceAtLeast(0L)
            AutoJs.instance.globalConsole.log(
                context.getString(R.string.text_typescript_compilation_finished, elapsedMs),
            )
        }
    }
}
