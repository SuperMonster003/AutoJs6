package org.autojs.autojs.core.plugin.r8

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal enum class R8CompilerHostFailureStage {
    MAIN_THREAD,
    SELECTION,
    BUSY,
    BINDING,
    NEGOTIATION,
    TRANSACTION,
}

internal sealed class R8CompilerHostDispatchOutcome {
    data class NotSelected(
        val decision: R8CompilerSelectionDecision,
    ) : R8CompilerHostDispatchOutcome()

    data class Published(
        val transaction: R8CompilerTransactionOutcome.Published,
    ) : R8CompilerHostDispatchOutcome()

    data class Failed(
        val stage: R8CompilerHostFailureStage,
        val cause: Throwable,
        val directive: R8CompilerFailureDirective,
        val transaction: R8CompilerTransactionOutcome.Failed? = null,
    ) : R8CompilerHostDispatchOutcome()

    data class Cancelled(
        val transaction: R8CompilerTransactionOutcome.Cancelled,
    ) : R8CompilerHostDispatchOutcome()
}

/**
 * Blocking internal dispatcher for an explicit R8 selection. It contains no D8/dx route and must
 * run away from the Android main thread. Disabled/no-selection are the only non-attempt outcomes.
 */
internal class R8CompilerHostDispatcher(
    private val controlPlane: R8CompilerHostControlPlane,
    private val transactionRunner: R8CompilerTransactionRunner,
    private val isMainThread: () -> Boolean,
    private val bindingTimeoutMillis: Long = DEFAULT_BINDING_TIMEOUT_MILLIS,
) {
    private val active = AtomicBoolean(false)

    init {
        require(bindingTimeoutMillis > 0L)
    }

    fun compile(spec: R8CompilerHostCompileSpec): R8CompilerHostDispatchOutcome {
        if (isMainThread()) {
            return localFailure(
                R8CompilerHostFailureStage.MAIN_THREAD,
                IllegalStateException("R8 compilation cannot block the Android main thread"),
            )
        }
        val decision = try {
            controlPlane.select()
        } catch (error: Exception) {
            return localFailure(R8CompilerHostFailureStage.SELECTION, error)
        }
        when (decision) {
            R8CompilerSelectionDecision.Disabled,
            R8CompilerSelectionDecision.NoSelection,
            -> return R8CompilerHostDispatchOutcome.NotSelected(decision)
            is R8CompilerSelectionDecision.Unavailable -> return localFailure(
                R8CompilerHostFailureStage.SELECTION,
                IllegalStateException("Explicit R8 component is unavailable: ${decision.reason}"),
            )
            is R8CompilerSelectionDecision.Rejected -> return localFailure(
                R8CompilerHostFailureStage.SELECTION,
                SecurityException("Explicit R8 component was rejected: ${decision.reason}"),
            )
            is R8CompilerSelectionDecision.Selected -> return compileSelected(decision.identity, spec)
        }
    }

    private fun compileSelected(
        identity: R8CompilerPinnedIdentity,
        spec: R8CompilerHostCompileSpec,
    ): R8CompilerHostDispatchOutcome {
        val firewall = R8CompilerDispatchFirewall()
        if (!active.compareAndSet(false, true)) {
            return failure(
                firewall,
                R8CompilerHostFailureStage.BUSY,
                IllegalStateException("Another explicit R8 host attempt is active"),
            )
        }
        val abortSignal = R8CompilerAttemptAbortSignal()
        val awaiter = BindingAwaiter(abortSignal)
        var binding: R8CompilerBinding? = null
        try {
            binding = try {
                controlPlane.bindExact(identity, awaiter)
            } catch (error: Exception) {
                return failure(firewall, R8CompilerHostFailureStage.BINDING, error)
            }
            val connection = try {
                awaiter.await(bindingTimeoutMillis)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                return failure(firewall, R8CompilerHostFailureStage.BINDING, error)
            } catch (error: Exception) {
                return failure(firewall, R8CompilerHostFailureStage.BINDING, error)
            }
            val negotiation = try {
                controlPlane.negotiateConnected(identity, connection, spec.minApi)
            } catch (error: Exception) {
                return failure(firewall, R8CompilerHostFailureStage.NEGOTIATION, error)
            }
            firewall.negotiated()
            return when (val transaction = transactionRunner.run(
                connection,
                negotiation,
                spec,
                firewall,
                abortSignal,
            )) {
                is R8CompilerTransactionOutcome.Published ->
                    R8CompilerHostDispatchOutcome.Published(transaction)
                is R8CompilerTransactionOutcome.Failed -> R8CompilerHostDispatchOutcome.Failed(
                    R8CompilerHostFailureStage.TRANSACTION,
                    transaction.cause,
                    transaction.directive,
                    transaction,
                )
                is R8CompilerTransactionOutcome.Cancelled ->
                    R8CompilerHostDispatchOutcome.Cancelled(transaction)
            }
        } finally {
            try {
                binding?.close()
            } catch (error: VirtualMachineError) {
                throw error
            } catch (_: Exception) {
                // Android binding cleanup is best-effort after the R8 outcome is already fixed.
            } finally {
                active.set(false)
            }
        }
    }

    private fun localFailure(
        stage: R8CompilerHostFailureStage,
        cause: Throwable,
    ): R8CompilerHostDispatchOutcome.Failed = failure(R8CompilerDispatchFirewall(), stage, cause)

    private fun failure(
        firewall: R8CompilerDispatchFirewall,
        stage: R8CompilerHostFailureStage,
        cause: Throwable,
    ): R8CompilerHostDispatchOutcome.Failed = R8CompilerHostDispatchOutcome.Failed(
        stage,
        cause,
        firewall.failed(),
    )

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

    companion object {
        const val DEFAULT_BINDING_TIMEOUT_MILLIS = 15_000L
    }
}
