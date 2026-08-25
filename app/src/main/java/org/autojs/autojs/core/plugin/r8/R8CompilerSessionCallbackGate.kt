package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8Cancellation
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8Error
import org.autojs.plugin.r8compiler.api.R8Progress
import org.autojs.plugin.r8compiler.api.R8Result
import org.autojs.plugin.r8compiler.api.R8Started
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal sealed class R8CompilerSessionTerminal {
    data class Completed(val result: R8Result) : R8CompilerSessionTerminal()
    data class Failed(val error: R8Error) : R8CompilerSessionTerminal()
    data class Cancelled(val cancellation: R8Cancellation) : R8CompilerSessionTerminal()
    data class Violation(val cause: Throwable) : R8CompilerSessionTerminal()
}

/** Strict, synchronized callback law for one provider session. */
internal class R8CompilerSessionCallbackGate(
    private val request: R8CompileRequest,
    private val capabilities: R8CompilerCapabilities,
) : R8CompilerRemoteCallback {
    private val terminalLatch = CountDownLatch(1)
    private var started: R8Started? = null
    private var lastProgress: R8Progress? = null
    private var terminal: R8CompilerSessionTerminal? = null
    private var lateViolation: Throwable? = null

    val isTerminal: Boolean
        @Synchronized get() = terminal != null

    override fun onStarted(metadata: ByteArray) = deliver {
        requireMetadataBudget(metadata)
        val decoded = R8CompilerCodec.decodeStarted(metadata)
        R8CompilerValidation.validateStartedAgainst(decoded, request, capabilities)
        synchronized(this) {
            requireOpen("started")
            require(started == null) { "R8 provider repeated started" }
            started = decoded
        }
    }

    override fun onProgress(progress: ByteArray) = deliver {
        requireMetadataBudget(progress)
        val decoded = R8CompilerCodec.decodeProgress(progress)
        synchronized(this) {
            requireOpen("progress")
            val acceptedStarted = requireNotNull(started) { "R8 progress preceded started" }
            R8CompilerValidation.validateProgressAfterStarted(decoded, acceptedStarted, request, capabilities)
            val previous = lastProgress
            require(previous == null || decoded.sequence > previous.sequence) {
                "R8 progress sequence is not strictly increasing"
            }
            lastProgress = decoded
        }
    }

    override fun onCompleted(result: ByteArray) = deliver {
        requireMetadataBudget(result)
        val decoded = R8CompilerCodec.decodeResult(result)
        R8CompilerValidation.validateResultAgainst(decoded, request, capabilities)
        acceptTerminal(R8CompilerSessionTerminal.Completed(decoded))
    }

    override fun onFailed(error: ByteArray) = deliver {
        requireMetadataBudget(error)
        val decoded = R8CompilerCodec.decodeError(error)
        R8CompilerValidation.validateErrorAgainst(decoded, request, capabilities)
        acceptTerminal(R8CompilerSessionTerminal.Failed(decoded))
    }

    override fun onCancelled(cancellation: ByteArray) = deliver {
        requireMetadataBudget(cancellation)
        val decoded = R8CompilerCodec.decodeCancellation(cancellation)
        R8CompilerValidation.validateCancellationAgainst(decoded, request)
        acceptTerminal(R8CompilerSessionTerminal.Cancelled(decoded))
    }

    override fun onTransportViolation(cause: Throwable?) {
        acceptTerminal(
            R8CompilerSessionTerminal.Violation(
                cause ?: IllegalStateException("R8 transport failed without a cause"),
            ),
        )
    }

    fun awaitTerminal(timeoutMillis: Long): R8CompilerSessionTerminal? {
        require(timeoutMillis > 0L)
        if (!terminalLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) return null
        return synchronized(this) {
            lateViolation?.let { R8CompilerSessionTerminal.Violation(it) } ?: terminal
        }
    }

    private fun deliver(action: () -> Unit) {
        try {
            action()
        } catch (error: VirtualMachineError) {
            onTransportViolation(error)
            throw error
        } catch (error: Throwable) {
            onTransportViolation(error)
        }
    }

    @Synchronized
    private fun acceptTerminal(value: R8CompilerSessionTerminal) {
        if (terminal == null) {
            terminal = value
            terminalLatch.countDown()
        } else {
            lateViolation = IllegalStateException("R8 provider delivered more than one terminal callback")
        }
    }

    private fun requireOpen(label: String) {
        require(terminal == null) { "R8 provider delivered $label after terminal" }
    }

    private fun requireMetadataBudget(value: ByteArray) {
        require(value.size <= R8CompilerContract.MAX_METADATA_BYTES) { "R8 callback metadata is oversized" }
    }
}
