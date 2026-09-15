package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8RetraceCodec
import org.autojs.plugin.r8compiler.api.R8RetraceError
import org.autojs.plugin.r8compiler.api.R8RetraceValidation
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal enum class R8RetraceHostFailureStage {
    MAIN_THREAD,
    SELECTION,
    BUSY,
    BINDING,
    NEGOTIATION,
    WORKSPACE,
    INPUT_SNAPSHOT,
    DESCRIPTOR,
    DISPATCH,
    CALLBACK,
    REMOTE_FAILURE,
    OUTPUT_ADOPTION,
    CLEANUP,
}

internal sealed class R8RetraceHostDispatchOutcome {
    data class NotSelected(
        val decision: R8CompilerSelectionDecision,
    ) : R8RetraceHostDispatchOutcome()

    data class Completed(
        val stackTrace: String,
    ) : R8RetraceHostDispatchOutcome()

    data class Failed(
        val stage: R8RetraceHostFailureStage,
        val cause: Throwable,
        val remoteError: R8RetraceError? = null,
    ) : R8RetraceHostDispatchOutcome()

    data class Cancelled(
        val cancellation: org.autojs.plugin.r8compiler.api.R8RetraceCancellation?,
    ) : R8RetraceHostDispatchOutcome()
}

/** Blocking fail-closed dispatcher for the explicit protocol 1.1 retrace script route. */
internal class R8RetraceHostDispatcher(
    private val controlPlane: R8CompilerHostControlPlane,
    private val workspaceRoot: File,
    private val descriptorFactory: R8CompilerDescriptorFactory,
    private val isMainThread: () -> Boolean,
    private val bindingTimeoutMillis: Long = R8CompilerHostDispatcher.DEFAULT_BINDING_TIMEOUT_MILLIS,
    private val requestIdFactory: () -> R8RequestId = {
        R8RequestId.fromUuid(UUID.randomUUID())
    },
) {
    private val active = AtomicBoolean(false)

    init {
        require(bindingTimeoutMillis > 0L)
    }

    fun retrace(spec: R8RetraceHostSpec): R8RetraceHostDispatchOutcome {
        if (isMainThread()) {
            return failure(
                R8RetraceHostFailureStage.MAIN_THREAD,
                IllegalStateException("R8 retrace cannot block the Android main thread"),
            )
        }
        val decision = try {
            controlPlane.select()
        } catch (error: Exception) {
            return failure(R8RetraceHostFailureStage.SELECTION, error)
        }
        when (decision) {
            R8CompilerSelectionDecision.Disabled,
            R8CompilerSelectionDecision.NoSelection,
            -> return R8RetraceHostDispatchOutcome.NotSelected(decision)
            is R8CompilerSelectionDecision.Unavailable -> return failure(
                R8RetraceHostFailureStage.SELECTION,
                IllegalStateException("Explicit R8 component is unavailable: ${decision.reason}"),
            )
            is R8CompilerSelectionDecision.Rejected -> return failure(
                R8RetraceHostFailureStage.SELECTION,
                SecurityException("Explicit R8 component was rejected: ${decision.reason}"),
            )
            is R8CompilerSelectionDecision.Selected -> return retraceSelected(decision.identity, spec)
        }
    }

    private fun retraceSelected(
        identity: R8CompilerPinnedIdentity,
        spec: R8RetraceHostSpec,
    ): R8RetraceHostDispatchOutcome {
        if (!active.compareAndSet(false, true)) {
            return failure(
                R8RetraceHostFailureStage.BUSY,
                IllegalStateException("Another explicit R8 retrace attempt is active"),
            )
        }
        val abortSignal = R8CompilerAttemptAbortSignal()
        val awaiter = BindingAwaiter(abortSignal)
        var binding: R8CompilerBinding? = null
        try {
            binding = try {
                controlPlane.bindExact(identity, awaiter)
            } catch (error: Exception) {
                return failure(R8RetraceHostFailureStage.BINDING, error)
            }
            val connection = try {
                awaiter.await(bindingTimeoutMillis)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                return failure(R8RetraceHostFailureStage.BINDING, error)
            } catch (error: Exception) {
                return failure(R8RetraceHostFailureStage.BINDING, error)
            }
            val negotiation = try {
                controlPlane.negotiateRetraceConnected(identity, connection)
            } catch (error: Exception) {
                return failure(R8RetraceHostFailureStage.NEGOTIATION, error)
            }
            return runTransaction(connection, negotiation, spec, abortSignal)
        } finally {
            try {
                binding?.close()
            } catch (error: VirtualMachineError) {
                throw error
            } catch (_: Exception) {
            } finally {
                active.set(false)
            }
        }
    }

    private fun runTransaction(
        connection: R8CompilerRemoteConnection,
        negotiation: R8RetraceNegotiation,
        spec: R8RetraceHostSpec,
        abortSignal: R8CompilerAttemptAbortSignal,
    ): R8RetraceHostDispatchOutcome {
        var workspace: R8RetraceTransactionWorkspace? = null
        var outcome: R8RetraceHostDispatchOutcome? = null
        try {
            workspace = try {
                R8RetraceTransactionWorkspace.create(workspaceRoot)
            } catch (error: Exception) {
                outcome = failure(R8RetraceHostFailureStage.WORKSPACE, error)
                null
            }
            workspace?.let { activeWorkspace ->
                outcome = runTransactionInWorkspace(
                    connection,
                    negotiation,
                    spec,
                    abortSignal,
                    activeWorkspace,
                )
            }
        } catch (error: VirtualMachineError) {
            throw error
        } catch (error: Throwable) {
            outcome = failure(R8RetraceHostFailureStage.CALLBACK, error)
        } finally {
            try {
                workspace?.close()
            } catch (error: VirtualMachineError) {
                throw error
            } catch (error: Throwable) {
                outcome = failure(R8RetraceHostFailureStage.CLEANUP, error)
            }
        }
        return outcome ?: failure(
            R8RetraceHostFailureStage.CALLBACK,
            IllegalStateException("R8 retrace transaction produced no outcome"),
        )
    }

    private fun runTransactionInWorkspace(
        connection: R8CompilerRemoteConnection,
        negotiation: R8RetraceNegotiation,
        spec: R8RetraceHostSpec,
        abortSignal: R8CompilerAttemptAbortSignal,
        workspace: R8RetraceTransactionWorkspace,
    ): R8RetraceHostDispatchOutcome {
        val request = try {
            R8RetraceInputSnapshotter.snapshot(
                spec,
                negotiation.capabilities,
                workspace,
                requestIdFactory(),
            )
        } catch (error: Exception) {
            return failure(R8RetraceHostFailureStage.INPUT_SNAPSHOT, error)
        }
        try {
            FileOutputStream(workspace.outputStackTraceFile, false).use { output -> output.fd.sync() }
        } catch (error: Exception) {
            return failure(R8RetraceHostFailureStage.WORKSPACE, error)
        }
        val callback = R8RetraceSessionCallbackGate(request, negotiation.capabilities)
        val abortRegistration = try {
            abortSignal.attach(callback::onTransportViolation)
        } catch (error: Exception) {
            return failure(R8RetraceHostFailureStage.CALLBACK, error)
        }
        var session: R8CompilerRemoteSession? = null
        var outcome: R8RetraceHostDispatchOutcome? = null
        try {
            if (callback.isTerminal) {
                outcome = failure(
                    R8RetraceHostFailureStage.CALLBACK,
                    IllegalStateException("R8 binding died before retrace dispatch"),
                )
            } else {
                session = try {
                    dispatch(
                        connection,
                        R8RetraceCodec.encodeRequest(request),
                        workspace.inputBundleFile,
                        workspace.outputStackTraceFile,
                        callback,
                    )
                } catch (error: Exception) {
                    outcome = failure(R8RetraceHostFailureStage.DISPATCH, error)
                    null
                }
            }

            var terminal: R8RetraceSessionTerminal? = null
            if (outcome == null && session != null) {
                val waitMillis = Math.addExact(request.timeoutMillis, spec.terminalGraceMillis)
                try {
                    terminal = callback.awaitTerminal(waitMillis)
                    if (terminal == null) {
                        outcome = failure(
                            R8RetraceHostFailureStage.CALLBACK,
                            TimeoutException("R8 retrace terminal callback timed out"),
                        )
                    }
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    outcome = R8RetraceHostDispatchOutcome.Cancelled(null)
                }
            }

            if (outcome == null && terminal != null) {
                val activeSession = checkNotNull(session)
                try {
                    activeSession.close()
                    session = null
                } catch (error: Exception) {
                    outcome = failure(R8RetraceHostFailureStage.CALLBACK, error)
                }
            }

            if (outcome == null && terminal != null) {
                outcome = adoptTerminal(terminal, workspace)
            }
        } finally {
            var cleanupFailure: Throwable? = null
            session?.let { activeSession ->
                cleanupFailure = invokeAndMerge(cleanupFailure, activeSession::cancel)
                cleanupFailure = invokeAndMerge(cleanupFailure, activeSession::close)
            }
            cleanupFailure = invokeAndMerge(cleanupFailure, abortRegistration::close)
            cleanupFailure?.let { error ->
                outcome = failure(
                    if (outcome == null) {
                        R8RetraceHostFailureStage.CALLBACK
                    } else {
                        R8RetraceHostFailureStage.CLEANUP
                    },
                    error,
                )
            }
        }
        return outcome ?: failure(
            R8RetraceHostFailureStage.CALLBACK,
            IllegalStateException("R8 retrace session produced no outcome"),
        )
    }

    private fun adoptTerminal(
        terminal: R8RetraceSessionTerminal,
        workspace: R8RetraceTransactionWorkspace,
    ): R8RetraceHostDispatchOutcome = when (terminal) {
        is R8RetraceSessionTerminal.Completed -> try {
            val file = workspace.outputStackTraceFile
            require(file.isFile && file.length() == terminal.result.outputSizeBytes) {
                "R8 retrace output size changed"
            }
            require(file.length() <= Int.MAX_VALUE.toLong()) {
                "R8 retrace output cannot be materialized"
            }
            val bytes = file.readBytes()
            R8RetraceValidation.validateOutputAgainst(bytes, terminal.result)
            R8RetraceHostDispatchOutcome.Completed(
                String(bytes, StandardCharsets.UTF_8),
            )
        } catch (error: Exception) {
            failure(R8RetraceHostFailureStage.OUTPUT_ADOPTION, error)
        }
        is R8RetraceSessionTerminal.Failed -> failure(
            R8RetraceHostFailureStage.REMOTE_FAILURE,
            IllegalStateException("R8 provider rejected or failed retrace"),
            terminal.error,
        )
        is R8RetraceSessionTerminal.Cancelled ->
            R8RetraceHostDispatchOutcome.Cancelled(terminal.cancellation)
        is R8RetraceSessionTerminal.Violation -> failure(
            R8RetraceHostFailureStage.CALLBACK,
            terminal.cause,
        )
    }

    private fun dispatch(
        connection: R8CompilerRemoteConnection,
        requestMetadata: ByteArray,
        inputBundle: File,
        outputStackTrace: File,
        callback: R8CompilerRemoteCallback,
    ): R8CompilerRemoteSession {
        var input: R8CompilerOwnedDescriptor? = null
        var output: R8CompilerOwnedDescriptor? = null
        var session: R8CompilerRemoteSession? = null
        var failure: Throwable? = null
        try {
            input = descriptorFactory.openReadOnly(inputBundle)
            output = descriptorFactory.openWriteOnly(outputStackTrace)
            session = connection.openRetraceSession(requestMetadata, input, output, callback)
        } catch (error: Throwable) {
            failure = error
        } finally {
            failure = closeAndMerge(failure, output)
            failure = closeAndMerge(failure, input)
        }
        failure?.let { error ->
            var merged = error
            session?.let { opened ->
                merged = invokeAndMerge(merged, opened::cancel) ?: merged
                merged = invokeAndMerge(merged, opened::close) ?: merged
            }
            if (merged is VirtualMachineError) throw merged
            throw IllegalStateException("R8 retrace dispatch failed", merged)
        }
        return checkNotNull(session) { "R8 retrace dispatch returned no session" }
    }

    private fun closeAndMerge(primary: Throwable?, closeable: AutoCloseable?): Throwable? =
        if (closeable == null) primary else invokeAndMerge(primary, closeable::close)

    private fun invokeAndMerge(primary: Throwable?, action: () -> Unit): Throwable? = try {
        action()
        primary
    } catch (error: Throwable) {
        if (primary == null) {
            error
        } else {
            if (error !== primary) primary.addSuppressed(error)
            primary
        }
    }

    private fun failure(
        stage: R8RetraceHostFailureStage,
        cause: Throwable,
        remoteError: R8RetraceError? = null,
    ) = R8RetraceHostDispatchOutcome.Failed(stage, cause, remoteError)

    private class BindingAwaiter(
        private val abortSignal: R8CompilerAttemptAbortSignal,
    ) : R8CompilerBindingCallback {
        private val latch = CountDownLatch(1)
        private val lock = Any()
        private var connection: R8CompilerRemoteConnection? = null
        private var failure: Throwable? = null

        override fun onConnected(connection: R8CompilerRemoteConnection) {
            synchronized(lock) {
                if (this.connection != null || failure != null) {
                    failLocked(SecurityException("R8 binding delivered more than one outcome"))
                } else {
                    this.connection = connection
                    latch.countDown()
                }
            }
        }

        override fun onBindingFailure(cause: Throwable?) {
            fail(cause ?: IllegalStateException("R8 binding failed without a cause"))
        }

        override fun onBinderDeath() {
            fail(IllegalStateException("R8 provider Binder died"))
        }

        fun await(timeoutMillis: Long): R8CompilerRemoteConnection {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                val timeout = TimeoutException("R8 exact-component binding timed out")
                fail(timeout)
                throw timeout
            }
            return synchronized(lock) {
                failure?.let { throw it }
                checkNotNull(connection) { "R8 binding completed without a connection" }
            }
        }

        private fun fail(cause: Throwable) {
            synchronized(lock) { failLocked(cause) }
        }

        private fun failLocked(cause: Throwable) {
            if (failure == null) failure = cause
            abortSignal.abort(cause)
            latch.countDown()
        }
    }
}
