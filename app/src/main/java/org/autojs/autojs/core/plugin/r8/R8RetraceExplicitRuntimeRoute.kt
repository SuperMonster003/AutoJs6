package org.autojs.autojs.core.plugin.r8

import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

internal class R8RetraceExplicitException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Script boundary for provider-only retrace; no provider or any provider failure is terminal. */
internal object R8RetraceExplicitRuntimeRoute {
    fun dispatch(
        obfuscatedStackTrace: String,
        mappingPath: String,
        retraceMetadataPath: String,
        resolvePath: (String) -> String,
        retrace: (R8RetraceHostSpec) -> R8RetraceHostDispatchOutcome,
    ): String {
        require(obfuscatedStackTrace.isNotBlank()) { "R8 obfuscated stack trace must not be blank" }
        val mapping = File(resolvePath(mappingPath)).asSource("mapping")
        val metadata = File(resolvePath(retraceMetadataPath)).asSource("retrace metadata")
        val spec = R8RetraceHostSpec(mapping, metadata, obfuscatedStackTrace)
        return when (val outcome = retrace(spec)) {
            is R8RetraceHostDispatchOutcome.Completed -> outcome.stackTrace
            is R8RetraceHostDispatchOutcome.NotSelected -> throw R8RetraceExplicitException(
                "Explicit R8 provider is disabled or has no exact component selection; " +
                    "no local retrace fallback was attempted",
            )
            is R8RetraceHostDispatchOutcome.Failed -> {
                if (ScriptInterruptedException.causedByInterrupt(outcome.cause) ||
                    Thread.currentThread().isInterrupted
                ) {
                    Thread.currentThread().interrupt()
                    throw ScriptInterruptedException(outcome.cause)
                }
                throw R8RetraceExplicitException(
                    "Explicit R8 retrace failed at ${outcome.stage}; no fallback was attempted",
                    outcome.cause,
                )
            }
            is R8RetraceHostDispatchOutcome.Cancelled -> throw ScriptInterruptedException(
                R8RetraceExplicitException(
                    "Explicit R8 retrace was cancelled; no fallback was attempted",
                ),
            )
        }
    }

    private fun File.asSource(label: String) = R8CompilerHostSource {
        if (!isFile || !canRead()) {
            throw FileNotFoundException("Explicit R8 $label is not a readable file: $path")
        }
        FileInputStream(this)
    }
}
