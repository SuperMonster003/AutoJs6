package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.plugin.python.runtime.api.PythonEntryMode
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Resolves explicitly supplied, finite inputs for one Python execution. */
internal object PythonExecutionInputPolicy {

    /**
     * Optional [ExecutionConfig] argument. A [ByteArray] is transported verbatim; a
     * [CharSequence] is snapshotted as strict UTF-8. No other value type is accepted.
     */
    const val STDIN_SNAPSHOT_ARGUMENT = "autojs6.python.stdinSnapshot"

    /** Optional [ExecutionConfig] argument. Missing means the backward-compatible file mode. */
    const val ENTRY_MODE_ARGUMENT = "autojs6.python.entryMode"

    fun resolveEntryMode(config: ExecutionConfig?): PythonEntryMode {
        if (config == null || !config.arguments.containsKey(ENTRY_MODE_ARGUMENT)) return PythonEntryMode.FILE
        val value = config.getArgument(ENTRY_MODE_ARGUMENT) as? CharSequence
            ?: invalidEntryMode(
                "ExecutionConfig argument $ENTRY_MODE_ARGUMENT must be the string 'file' or 'module'",
            )
        return when (value.toString()) {
            "file" -> PythonEntryMode.FILE
            "module" -> PythonEntryMode.MODULE
            else -> invalidEntryMode(
                "ExecutionConfig argument $ENTRY_MODE_ARGUMENT must be exactly 'file' or 'module'",
            )
        }
    }

    fun resolveStdinSnapshot(config: ExecutionConfig?): ByteArray? {
        if (config == null || !config.arguments.containsKey(STDIN_SNAPSHOT_ARGUMENT)) return null
        return when (val value = config.getArgument(STDIN_SNAPSHOT_ARGUMENT)) {
            is ByteArray -> value.copyForStdin()
            is CharSequence -> value.encodeUtf8ForStdin()
            else -> invalidStdin(
                "ExecutionConfig argument $STDIN_SNAPSHOT_ARGUMENT must be a ByteArray or CharSequence",
            )
        }
    }

    private fun ByteArray.copyForStdin(): ByteArray {
        requireWithinProtocolLimit(size)
        return copyOf()
    }

    private fun CharSequence.encodeUtf8ForStdin(): ByteArray {
        if (length.toLong() > PythonRuntimeContract.MAX_STDIN_BYTES) {
            invalidStdin("Python stdin snapshot exceeds the protocol byte limit")
        }
        val encoded = try {
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(this))
        } catch (error: Exception) {
            invalidStdin("Python stdin snapshot is not valid Unicode text", error)
        }
        requireWithinProtocolLimit(encoded.remaining())
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    private fun requireWithinProtocolLimit(sizeBytes: Int) {
        if (sizeBytes.toLong() > PythonRuntimeContract.MAX_STDIN_BYTES) {
            invalidStdin("Python stdin snapshot exceeds the protocol byte limit")
        }
    }

    private fun invalidStdin(message: String, cause: Throwable? = null): Nothing =
        throw PythonRuntimeHostException(PythonRuntimeHostError.STDIN_INVALID, message, cause)

    private fun invalidEntryMode(message: String): Nothing =
        throw PythonRuntimeHostException(PythonRuntimeHostError.ENTRY_MODE_INVALID, message)
}
