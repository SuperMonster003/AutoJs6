package org.autojs.plugin.jvmsource.api

/**
 * Stable entry point implemented by a compiled Java or Kotlin source file.
 *
 * This surface intentionally contains no Android, Binder, host-runtime, or
 * class-loader types. Implementations run in the provider's disposable worker
 * process and interact with the host only through [JvmScriptContext].
 */
fun interface AutoJsJvmEntry {
    @Throws(Exception::class)
    fun run(context: JvmScriptContext): Any?
}

interface JvmScriptContext {
    fun app(): JvmAppApi

    fun cancellation(): JvmCancellation

    fun isCancellationRequested(): Boolean = cancellation().isCancellationRequested()

    @Throws(JvmCancellationException::class)
    fun throwIfCancellationRequested() = cancellation().throwIfCancellationRequested()
}

interface JvmAppApi {
    /** Requests that the host launch an already-installed application package. */
    fun launch(packageName: String): Boolean
}

interface JvmCancellation {
    fun isCancellationRequested(): Boolean

    @Throws(JvmCancellationException::class)
    fun throwIfCancellationRequested()
}

class JvmCancellationException @JvmOverloads constructor(
    message: String = "JVM source execution was cancelled",
    cause: Throwable? = null,
) : Exception(message, cause)
