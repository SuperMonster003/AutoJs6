package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8Cancellation
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8Error
import org.autojs.plugin.r8compiler.api.R8RequestId
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeoutException

internal enum class R8CompilerTransactionFailureStage {
    WORKSPACE,
    INPUT_SNAPSHOT,
    REQUEST,
    CACHE_LOOKUP,
    DESCRIPTOR,
    DISPATCH,
    CALLBACK,
    REMOTE_FAILURE,
    OUTPUT_ADOPTION,
    CACHE_PUBLICATION,
    CLEANUP,
}

internal class R8CompilerTransactionException(
    val stage: R8CompilerTransactionFailureStage,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal sealed class R8CompilerTransactionOutcome {
    data class Published(
        val artifact: R8CompilerStoredArtifact,
        val cacheHit: Boolean,
    ) : R8CompilerTransactionOutcome()

    data class Failed(
        val stage: R8CompilerTransactionFailureStage,
        val cause: Throwable,
        val remoteError: R8Error?,
        val directive: R8CompilerFailureDirective,
    ) : R8CompilerTransactionOutcome()

    data class Cancelled(
        val cancellation: R8Cancellation?,
        val directive: R8CompilerFailureDirective,
    ) : R8CompilerTransactionOutcome()
}

/** One negotiated R8 attempt: snapshot, optional R8-cache hit, one dispatch, adoption, and commit. */
internal class R8CompilerTransactionRunner(
    private val workspaceRoot: File,
    private val descriptorFactory: R8CompilerDescriptorFactory,
    private val artifactStore: R8CompilerArtifactStore,
    private val requestIdFactory: () -> R8RequestId = {
        R8RequestId.fromUuid(UUID.randomUUID())
    },
) {
    fun run(
        connection: R8CompilerRemoteConnection,
        negotiation: R8CompilerNegotiation,
        spec: R8CompilerHostCompileSpec,
        firewall: R8CompilerDispatchFirewall,
        abortSignal: R8CompilerAttemptAbortSignal,
    ): R8CompilerTransactionOutcome {
        var workspace: R8CompilerTransactionWorkspace? = null
        var provisional: Provisional? = null
        var fatal: VirtualMachineError? = null
        try {
            workspace = try {
                R8CompilerTransactionWorkspace.create(workspaceRoot)
            } catch (error: Exception) {
                throw failure(R8CompilerTransactionFailureStage.WORKSPACE, "Unable to create R8 workspace", error)
            }
            provisional = runInWorkspace(connection, negotiation, spec, firewall, abortSignal, workspace)
        } catch (error: VirtualMachineError) {
            fatal = error
        } catch (error: R8CompilerTransactionException) {
            provisional = Provisional.Failed(error.stage, error, null)
        } catch (error: Throwable) {
            provisional = Provisional.Failed(R8CompilerTransactionFailureStage.CALLBACK, error, null)
        } finally {
            try {
                workspace?.close()
            } catch (error: VirtualMachineError) {
                fatal = fatal?.also { it.addSuppressed(error) } ?: error
            } catch (error: Throwable) {
                if (provisional is Provisional.Published || provisional == null) {
                    provisional = Provisional.Failed(
                        R8CompilerTransactionFailureStage.CLEANUP,
                        failure(R8CompilerTransactionFailureStage.CLEANUP, "Unable to clean R8 workspace", error),
                        null,
                    )
                } else {
                    provisionalFailure(provisional)?.addSuppressed(error)
                }
            }
        }
        fatal?.let {
            try {
                firewall.failed()
            } catch (firewallError: Throwable) {
                if (firewallError !== it) it.addSuppressed(firewallError)
            }
            throw it
        }
        return when (val terminal = checkNotNull(provisional)) {
            is Provisional.Published -> {
                if (terminal.cacheHit) firewall.completedFromCache() else firewall.completed()
                R8CompilerTransactionOutcome.Published(terminal.artifact, terminal.cacheHit)
            }
            is Provisional.Failed -> R8CompilerTransactionOutcome.Failed(
                terminal.stage,
                terminal.cause,
                terminal.remoteError,
                firewall.failed(),
            )
            is Provisional.Cancelled -> R8CompilerTransactionOutcome.Cancelled(
                terminal.cancellation,
                firewall.failed(),
            )
        }
    }

    private fun runInWorkspace(
        connection: R8CompilerRemoteConnection,
        negotiation: R8CompilerNegotiation,
        spec: R8CompilerHostCompileSpec,
        firewall: R8CompilerDispatchFirewall,
        abortSignal: R8CompilerAttemptAbortSignal,
        workspace: R8CompilerTransactionWorkspace,
    ): Provisional {
        val snapshot = try {
            R8CompilerInputSnapshotter.snapshot(spec.inputs, negotiation.capabilities, workspace)
        } catch (error: Exception) {
            throw failure(R8CompilerTransactionFailureStage.INPUT_SNAPSHOT, "Unable to snapshot R8 inputs", error)
        }
        val request = try {
            R8CompilerRequestFactory.create(spec, snapshot, negotiation.capabilities, requestIdFactory())
        } catch (error: Exception) {
            throw failure(R8CompilerTransactionFailureStage.REQUEST, "Unable to construct R8 request", error)
        }
        val cacheKey = R8CompilerSemanticCacheKey.sha256(
            request,
            negotiation.capabilities,
            negotiation.identity,
        )
        val cached = try {
            artifactStore.lookup(cacheKey, request, negotiation.capabilities)
        } catch (error: Exception) {
            throw failure(R8CompilerTransactionFailureStage.CACHE_LOOKUP, "R8 cache lookup failed", error)
        }
        if (cached != null) return Provisional.Published(cached, cacheHit = true)

        FileOutputStream(workspace.outputBundleFile, false).use { it.fd.sync() }
        val callback = R8CompilerSessionCallbackGate(request, negotiation.capabilities)
        val abortRegistration = abortSignal.attach(callback::onTransportViolation)
        var session: R8CompilerRemoteSession? = null
        var operationFailure: Throwable? = null
        try {
            if (callback.isTerminal) {
                throw failure(R8CompilerTransactionFailureStage.CALLBACK, "R8 binding died before dispatch")
            }
            session = dispatch(
                connection,
                requestMetadata = R8CompilerCodec.encodeRequest(request),
                inputBundle = snapshot.inputBundleFile,
                outputBundle = workspace.outputBundleFile,
                callback = callback,
                firewall = firewall,
            )
            val waitMillis = Math.addExact(request.timeoutMillis, spec.terminalGraceMillis)
            val terminal = try {
                callback.awaitTerminal(waitMillis)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                return Provisional.Cancelled(null)
            } ?: run {
                throw failure(
                    R8CompilerTransactionFailureStage.CALLBACK,
                    "R8 provider did not deliver a terminal callback",
                    TimeoutException("R8 terminal callback timeout"),
                )
            }
            try {
                session.close()
                session = null
            } catch (error: Exception) {
                throw failure(R8CompilerTransactionFailureStage.CALLBACK, "Unable to close R8 session", error)
            }
            return when (terminal) {
                is R8CompilerSessionTerminal.Completed -> publishCompleted(
                    cacheKey,
                    terminal,
                    request,
                    negotiation,
                    workspace,
                )
                is R8CompilerSessionTerminal.Failed -> Provisional.Failed(
                    R8CompilerTransactionFailureStage.REMOTE_FAILURE,
                    failure(
                        R8CompilerTransactionFailureStage.REMOTE_FAILURE,
                        "R8 provider rejected or failed the request",
                    ),
                    terminal.error,
                )
                is R8CompilerSessionTerminal.Cancelled -> Provisional.Cancelled(terminal.cancellation)
                is R8CompilerSessionTerminal.Violation -> Provisional.Failed(
                    R8CompilerTransactionFailureStage.CALLBACK,
                    failure(
                        R8CompilerTransactionFailureStage.CALLBACK,
                        "R8 callback or transport violated the session contract",
                        terminal.cause,
                    ),
                    null,
                )
            }
        } catch (error: Throwable) {
            operationFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            session?.let { activeSession ->
                cleanupFailure = invokeAndMerge(cleanupFailure, activeSession::cancel)
                cleanupFailure = closeAndMerge(cleanupFailure, activeSession)
            }
            cleanupFailure = closeAndMerge(cleanupFailure, abortRegistration)
            cleanupFailure?.let { cleanup ->
                val primary = operationFailure
                if (primary == null) {
                    if (cleanup is VirtualMachineError) throw cleanup
                    throw failure(
                        R8CompilerTransactionFailureStage.CALLBACK,
                        "Unable to dispose the R8 session",
                        cleanup,
                    )
                }
                val merged = mergeFailures(primary, cleanup)
                if (merged !== primary) throw merged
            }
        }
    }

    private fun dispatch(
        connection: R8CompilerRemoteConnection,
        requestMetadata: ByteArray,
        inputBundle: File,
        outputBundle: File,
        callback: R8CompilerRemoteCallback,
        firewall: R8CompilerDispatchFirewall,
    ): R8CompilerRemoteSession {
        var input: R8CompilerOwnedDescriptor? = null
        var output: R8CompilerOwnedDescriptor? = null
        var operationFailure: Throwable? = null
        var result: R8CompilerRemoteSession? = null
        try {
            input = descriptorFactory.openReadOnly(inputBundle)
            output = descriptorFactory.openWriteOnly(outputBundle)
            firewall.dispatched()
            result = connection.openSession(requestMetadata, input, output, callback)
        } catch (error: Throwable) {
            operationFailure = error
        } finally {
            operationFailure = closeAndMerge(operationFailure, output)
            operationFailure = closeAndMerge(operationFailure, input)
        }
        operationFailure?.let {
            var operationError = it
            result?.let { openedSession ->
                operationError = invokeAndMerge(operationError, openedSession::cancel) ?: operationError
                operationError = closeAndMerge(operationError, openedSession) ?: operationError
            }
            if (operationError is VirtualMachineError) throw operationError
            throw failure(R8CompilerTransactionFailureStage.DISPATCH, "R8 dispatch failed", operationError)
        }
        return checkNotNull(result) { "R8 dispatch returned no session" }
    }

    private fun publishCompleted(
        cacheKey: String,
        terminal: R8CompilerSessionTerminal.Completed,
        request: org.autojs.plugin.r8compiler.api.R8CompileRequest,
        negotiation: R8CompilerNegotiation,
        workspace: R8CompilerTransactionWorkspace,
    ): Provisional.Published {
        val verified = try {
            R8CompilerArtifactAdopter.adopt(
                workspace.outputBundleFile,
                terminal.result,
                request,
                negotiation.capabilities,
                workspace,
            )
        } catch (error: Exception) {
            throw failure(R8CompilerTransactionFailureStage.OUTPUT_ADOPTION, "R8 output adoption failed", error)
        }
        val stored = try {
            artifactStore.publishVerified(cacheKey, verified, request, negotiation.capabilities)
        } catch (error: Exception) {
            throw failure(R8CompilerTransactionFailureStage.CACHE_PUBLICATION, "R8 cache publication failed", error)
        }
        return Provisional.Published(stored, cacheHit = false)
    }

    private fun closeAndMerge(primary: Throwable?, closeable: AutoCloseable?): Throwable? {
        if (closeable == null) return primary
        return invokeAndMerge(primary) {
            closeable.close()
        }
    }

    private fun invokeAndMerge(primary: Throwable?, action: () -> Unit): Throwable? = try {
        action()
        primary
    } catch (error: Throwable) {
        mergeFailures(primary, error)
    }

    private fun mergeFailures(primary: Throwable?, secondary: Throwable): Throwable {
        if (primary == null) return secondary
        if (secondary is VirtualMachineError && primary !is VirtualMachineError) {
            if (secondary !== primary) secondary.addSuppressed(primary)
            return secondary
        }
        if (secondary !== primary) primary.addSuppressed(secondary)
        return primary
    }

    private fun failure(
        stage: R8CompilerTransactionFailureStage,
        message: String,
        cause: Throwable? = null,
    ) = R8CompilerTransactionException(stage, message, cause)

    private fun provisionalFailure(value: Provisional?): Throwable? = when (value) {
        is Provisional.Failed -> value.cause
        else -> null
    }

    private sealed class Provisional {
        data class Published(
            val artifact: R8CompilerStoredArtifact,
            val cacheHit: Boolean,
        ) : Provisional()

        data class Failed(
            val stage: R8CompilerTransactionFailureStage,
            val cause: Throwable,
            val remoteError: R8Error?,
        ) : Provisional()

        data class Cancelled(val cancellation: R8Cancellation?) : Provisional()
    }
}
