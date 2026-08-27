package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8RetraceCancellation
import org.autojs.plugin.r8compiler.api.R8RetraceCapabilities
import org.autojs.plugin.r8compiler.api.R8RetraceCodec
import org.autojs.plugin.r8compiler.api.R8RetraceError
import org.autojs.plugin.r8compiler.api.R8RetraceProgress
import org.autojs.plugin.r8compiler.api.R8RetraceRequest
import org.autojs.plugin.r8compiler.api.R8RetraceResult
import org.autojs.plugin.r8compiler.api.R8RetraceStarted
import org.autojs.plugin.r8compiler.api.R8RetraceValidation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal sealed class R8RetraceSessionTerminal {
    data class Completed(val result: R8RetraceResult) : R8RetraceSessionTerminal()
    data class Failed(val error: R8RetraceError) : R8RetraceSessionTerminal()
    data class Cancelled(val cancellation: R8RetraceCancellation) : R8RetraceSessionTerminal()
    data class Violation(val cause: Throwable) : R8RetraceSessionTerminal()
}

/** Strict, synchronized callback law for one protocol 1.1 retrace session. */
internal class R8RetraceSessionCallbackGate(
    private val request: R8RetraceRequest,
    private val capabilities: R8RetraceCapabilities,
) : R8CompilerRemoteCallback {
    private val terminalLatch = CountDownLatch(1)
    private var started: R8RetraceStarted? = null
    private var lastProgress: R8RetraceProgress? = null
    private var terminal: R8RetraceSessionTerminal? = null
    private var lateViolation: Throwable? = null

    val isTerminal: Boolean
        @Synchronized get() = terminal != null

    override fun onStarted(metadata: ByteArray) = deliver {
        requireMetadataBudget(metadata)
        val decoded = R8RetraceCodec.decodeStarted(metadata)
        R8RetraceValidation.validateStartedAgainst(decoded, request, capabilities)
        synchronized(this) {
            requireOpen("retrace started")
            require(started == null) { "R8 provider repeated retrace started" }
            started = decoded
        }
    }

    override fun onProgress(progress: ByteArray) = deliver {
        requireMetadataBudget(progress)
        val decoded = R8RetraceCodec.decodeProgress(progress)
        synchronized(this) {
            requireOpen("retrace progress")
            val acceptedStarted = requireNotNull(started) {
                "R8 retrace progress preceded started"
            }
            R8RetraceValidation.validateProgressAgainst(decoded, request)
            require(decoded.sequence > acceptedStarted.sequence) {
                "R8 retrace progress did not follow started"
            }
            val previous = lastProgress
            require(previous == null || decoded.sequence > previous.sequence) {
                "R8 retrace progress sequence is not strictly increasing"
            }
            lastProgress = decoded
        }
    }

    override fun onCompleted(result: ByteArray) = deliver {
        requireMetadataBudget(result)
        val decoded = R8RetraceCodec.decodeResult(result)
        R8RetraceValidation.validateResultAgainst(decoded, request, capabilities)
        acceptTerminal(R8RetraceSessionTerminal.Completed(decoded))
    }

    override fun onFailed(error: ByteArray) = deliver {
        requireMetadataBudget(error)
        val decoded = R8RetraceCodec.decodeError(error)
        R8RetraceValidation.validateErrorAgainst(decoded, request, capabilities)
        acceptTerminal(R8RetraceSessionTerminal.Failed(decoded))
    }

    override fun onCancelled(cancellation: ByteArray) = deliver {
        requireMetadataBudget(cancellation)
        val decoded = R8RetraceCodec.decodeCancellation(cancellation)
        R8RetraceValidation.validateCancellationAgainst(decoded, request)
        acceptTerminal(R8RetraceSessionTerminal.Cancelled(decoded))
    }

    override fun onTransportViolation(cause: Throwable?) {
        acceptTerminal(
            R8RetraceSessionTerminal.Violation(
                cause ?: IllegalStateException("R8 retrace transport failed without a cause"),
            ),
        )
    }

    fun awaitTerminal(timeoutMillis: Long): R8RetraceSessionTerminal? {
        require(timeoutMillis > 0L)
        if (!terminalLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) return null
        return synchronized(this) {
            lateViolation?.let { R8RetraceSessionTerminal.Violation(it) } ?: terminal
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
    private fun acceptTerminal(value: R8RetraceSessionTerminal) {
        if (terminal == null) {
            terminal = value
            terminalLatch.countDown()
        } else {
            lateViolation = IllegalStateException(
                "R8 provider delivered more than one retrace terminal callback",
            )
        }
    }

    private fun requireOpen(label: String) {
        require(terminal == null) { "R8 provider delivered $label after terminal" }
    }

    private fun requireMetadataBudget(value: ByteArray) {
        require(value.size <= R8CompilerContract.MAX_METADATA_BYTES) {
            "R8 retrace callback metadata is oversized"
        }
    }
}
