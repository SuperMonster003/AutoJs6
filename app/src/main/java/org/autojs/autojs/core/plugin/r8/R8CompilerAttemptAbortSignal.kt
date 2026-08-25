package org.autojs.autojs.core.plugin.r8

import java.io.Closeable

/** One binding/Binder failure source can be attached to exactly one active transaction. */
internal class R8CompilerAttemptAbortSignal {
    private val lock = Any()
    private var failure: Throwable? = null
    private var listener: ((Throwable) -> Unit)? = null

    fun abort(cause: Throwable) {
        val target = synchronized(lock) {
            if (failure == null) failure = cause
            listener
        }
        target?.invoke(cause)
    }

    fun attach(target: (Throwable) -> Unit): Closeable {
        val existing = synchronized(lock) {
            check(listener == null) { "R8 abort signal already has a transaction" }
            listener = target
            failure
        }
        existing?.let(target)
        return Closeable {
            synchronized(lock) {
                if (listener === target) listener = null
            }
        }
    }
}
