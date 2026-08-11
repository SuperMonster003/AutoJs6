package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexProtocolVersion
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An attempt-only coalescing key for one fixed provider identity.
 *
 * `requestId` is deliberately absent because it identifies a caller, not compilation semantics.
 * This is not the persistent artifact cache key: the negotiated compiler version and effective
 * runtime-library fingerprint are unavailable until the leader completes its handshake.
 */
internal data class DexCompilerSingleFlightAttemptKey(
    val inputSizeBytes: Long,
    val inputSha256: String,
    val protocolVersion: DexProtocolVersion,
    val inputFormat: DexCompilerInputFormat,
    val outputFormat: DexCompilerOutputFormat,
    val mode: DexCompilerMode,
    val minApi: Int,
    val maximumOutputBytes: Long,
    val runtimeLibraryModel: DexRuntimeLibraryModel,
    val expectedRuntimeLibraryFingerprint: String,
    val canonicalizationPolicyVersion: Int,
    val diagnosticByteLimit: Int,
    val timeoutMillis: Long,
    val providerIdentity: DexCompilerPinnedIdentity,
    val hostRuntimeApi: Int,
) {
    init {
        require(inputSizeBytes > 0L)
        require(inputSha256.isNormalizedSha256())
        require(maximumOutputBytes > 0L)
        require(expectedRuntimeLibraryFingerprint.isNormalizedSha256())
        require(hostRuntimeApi > 0)
    }

    companion object {
        fun from(
            request: DexCompileRequest,
            providerIdentity: DexCompilerPinnedIdentity,
            hostRuntimeApi: Int,
        ): DexCompilerSingleFlightAttemptKey = DexCompilerSingleFlightAttemptKey(
            inputSizeBytes = request.inputBundleSizeBytes ?: request.programSizeBytes,
            inputSha256 = request.inputSetFingerprint?.toHexString() ?: request.programSha256.toHexString(),
            protocolVersion = request.protocolVersion,
            inputFormat = request.inputFormat,
            outputFormat = request.outputFormat,
            mode = request.mode,
            minApi = request.minApi,
            maximumOutputBytes = request.maxOutputBytes,
            runtimeLibraryModel = request.runtimeLibraryModel,
            expectedRuntimeLibraryFingerprint = request.expectedRuntimeLibraryFingerprint.toHexString(),
            canonicalizationPolicyVersion = request.canonicalizationPolicyVersion,
            diagnosticByteLimit = request.diagnosticByteLimit,
            timeoutMillis = request.timeoutMillis,
            providerIdentity = providerIdentity,
            hostRuntimeApi = hostRuntimeApi,
        )
    }
}

/**
 * Owns the shared successful terminal while joined callers obtain independent capabilities.
 *
 * Every [acquireForCaller] result must remain usable after [close] and must be independently
 * releasable or invalidatable by its consumer. A future artifact-store adapter should therefore
 * return a fresh generation-bound lease (or perform a fresh verified cache lookup), never the
 * leader's one-shot [DexCompilerVerifiedArtifact] instance. Caller-private fallback snapshots do
 * not belong here.
 */
internal interface DexCompilerSingleFlightValueSource<out V : Any> : AutoCloseable {
    fun acquireForCaller(): V
    override fun close()
}

/** A leader terminal. Terminals are shared only by callers that joined before completion. */
internal sealed class DexCompilerSingleFlightTerminal<out V : Any> {
    class Value<V : Any>(val source: DexCompilerSingleFlightValueSource<V>) :
        DexCompilerSingleFlightTerminal<V>()

    class Failure(val cause: Throwable) : DexCompilerSingleFlightTerminal<Nothing>()
    object Cancelled : DexCompilerSingleFlightTerminal<Nothing>()
}

/** A caller-owned view of a shared terminal. */
internal sealed class DexCompilerSingleFlightOutcome<out V : Any> {
    class Value<V : Any>(val value: V) : DexCompilerSingleFlightOutcome<V>()
    class Failure(val cause: Throwable) : DexCompilerSingleFlightOutcome<Nothing>()
    object Cancelled : DexCompilerSingleFlightOutcome<Nothing>()
}

/** A caller-private fallback paired with its view of the shared attempt. */
internal sealed class DexCompilerSingleFlightCallerOutcome<out V : Any, out F : AutoCloseable> {
    class Value<V : Any, F : AutoCloseable>(
        val value: V,
        val fallback: F,
    ) : DexCompilerSingleFlightCallerOutcome<V, F>()

    class Failure<F : AutoCloseable>(
        val cause: Throwable,
        val fallback: F,
    ) : DexCompilerSingleFlightCallerOutcome<Nothing, F>()

    object Cancelled : DexCompilerSingleFlightCallerOutcome<Nothing, Nothing>()
}

/**
 * Adds caller-private fallback ownership to [DexCompilerSingleFlight].
 *
 * Success and shared failure return the exact fallback supplied by that caller. Shared or caller
 * cancellation closes it and never exposes it as a local-fallback option. Producer resources are
 * delegated to the single-flight election independently from this caller fallback.
 */
internal class DexCompilerSingleFlightCallerCoordinator<K : Any, V : Any, F : AutoCloseable>(
    private val singleFlight: DexCompilerSingleFlight<K, V>,
    private val onFallbackCloseFailure: (Throwable) -> Unit = {},
) {
    @Throws(InterruptedException::class)
    fun execute(
        key: K,
        fallback: F,
        releaseProducerResources: () -> Unit,
        onJoined: (isLeader: Boolean) -> Unit = {},
        requestProducerCancellation: () -> Unit = {},
        onProducerResourcesTransferred: () -> Unit = {},
        producer: () -> DexCompilerSingleFlightTerminal<V>,
    ): DexCompilerSingleFlightCallerOutcome<V, F> {
        val outcome = try {
            singleFlight.execute(
                key = key,
                releaseProducerResources = releaseProducerResources,
                onJoined = onJoined,
                requestProducerCancellation = requestProducerCancellation,
                onProducerResourcesTransferred = onProducerResourcesTransferred,
                producer = producer,
            )
        } catch (failure: Throwable) {
            closeFallback(fallback)?.let { closeFailure ->
                if (closeFailure is VirtualMachineError) {
                    if (failure is InterruptedException) Thread.currentThread().interrupt()
                    addSuppressedSafely(closeFailure, failure)
                    throw closeFailure
                }
                addSuppressedSafely(failure, closeFailure)
            }
            throw failure
        }
        return when (outcome) {
            is DexCompilerSingleFlightOutcome.Value ->
                DexCompilerSingleFlightCallerOutcome.Value(outcome.value, fallback)
            is DexCompilerSingleFlightOutcome.Failure ->
                DexCompilerSingleFlightCallerOutcome.Failure(outcome.cause, fallback)
            DexCompilerSingleFlightOutcome.Cancelled -> {
                closeFallback(fallback)?.let { closeFailure ->
                    if (closeFailure is VirtualMachineError) throw closeFailure
                }
                DexCompilerSingleFlightCallerOutcome.Cancelled
            }
        }
    }

    private fun closeFallback(fallback: F): Throwable? = try {
        fallback.close()
        null
    } catch (failure: Throwable) {
        try {
            onFallbackCloseFailure(failure)
        } catch (_: Throwable) {
            // Cancellation ownership must not depend on diagnostic reporting.
        }
        failure
    }
}

/** One caller's fresh generation-bound view of a successfully shared publication. */
internal data class DexCompilerSingleFlightPublishedArtifact(
    val artifact: DexCompilerVerifiedArtifact,
    val cacheHit: Boolean,
)

/**
 * Reopens the leader's committed semantic key for every joined caller.
 *
 * The leader's artifact wrapper is deliberately not shared. [cache] must return a fresh
 * exact-generation capability on every successful lookup; an eviction between completion and a
 * caller acquisition is reported only to that caller and can safely use its private fallback.
 */
internal class DexCompilerSingleFlightArtifactSource(
    private val cacheKeySha256: String,
    private val cacheHit: Boolean,
    private val cache: DexCompilerVerifiedCache,
) : DexCompilerSingleFlightValueSource<DexCompilerSingleFlightPublishedArtifact> {
    private val closed = AtomicBoolean(false)

    init {
        require(cacheKeySha256.isNormalizedSha256())
    }

    override fun acquireForCaller(): DexCompilerSingleFlightPublishedArtifact {
        check(!closed.get()) { "DEX compiler single-flight artifact source is already closed" }
        val artifact = cache.lookup(cacheKeySha256)
            ?: throw IllegalStateException("Committed DEX compiler artifact is unavailable")
        check(artifact.cacheKeySha256 == cacheKeySha256) {
            "Committed DEX compiler artifact changed its semantic key"
        }
        return DexCompilerSingleFlightPublishedArtifact(
            artifact = artifact,
            cacheHit = cacheHit,
        )
    }

    override fun close() {
        check(closed.compareAndSet(false, true)) {
            "DEX compiler single-flight artifact source was closed more than once"
        }
    }
}

/**
 * Process-local, non-caching single-flight coordination.
 *
 * Exactly one producer runs for overlapping calls with the same key. Producer work is dispatched
 * away from caller threads, so interrupting one waiting caller only detaches that caller while any
 * other waiter remains. When the last waiter detaches before a terminal, the flight is removed and
 * its leader-owned cooperative cancellation hook is requested without interrupting the producer
 * thread. Every terminal removes its map entry before waiters are released: failures can be retried
 * immediately and [DexCompilerSingleFlightTerminal.Cancelled] is never cached.
 *
 * Production must provide a dedicated bounded orchestration executor. It must not reuse the
 * harness control, Binder, deadline, or output-drain executors because a producer may synchronously
 * wait for work running on those lanes.
 */
internal class DexCompilerSingleFlight<K : Any, V : Any>(
    private val leaderExecutor: Executor,
    private val onValueSourceCloseFailure: (Throwable) -> Unit = {},
) {
    private val lock = Any()
    private val flights = mutableMapOf<K, Flight>()

    /**
     * Joins or starts an attempt and waits for its terminal.
     *
     * The producer and [requestProducerCancellation] hook supplied by a follower are ignored.
     * [InterruptedException] detaches only this caller. If it was the final waiter, the abandoned
     * flight is made unjoinable before its leader-owned cancellation hook is requested.
     *
     * [releaseProducerResources] transfers caller-created producer resources to this coordinator.
     * A follower releases them immediately. A leader releases them only after its producer returns,
     * or after executor rejection, so interrupting the leader caller cannot close resources still
     * needed by the shared producer.
     */
    @Throws(InterruptedException::class)
    fun execute(
        key: K,
        releaseProducerResources: () -> Unit = {},
        onJoined: (isLeader: Boolean) -> Unit = {},
        requestProducerCancellation: () -> Unit = {},
        onProducerResourcesTransferred: () -> Unit = {},
        producer: () -> DexCompilerSingleFlightTerminal<V>,
    ): DexCompilerSingleFlightOutcome<V> {
        onProducerResourcesTransferred()
        if (Thread.currentThread().isInterrupted) {
            val interrupted = InterruptedException("DEX compiler single-flight caller was already interrupted")
            releaseProducerResourcesSafely(releaseProducerResources)?.let { releaseFailure ->
                addSuppressedSafely(interrupted, releaseFailure)
            }
            throw interrupted
        }
        val joined = try {
            synchronized(lock) {
                val existing = flights[key]
                if (existing != null) {
                    existing.waiterCount++
                    JoinedFlight(existing, isLeader = false)
                } else {
                    val created = Flight(key, requestProducerCancellation).apply { waiterCount = 1 }
                    flights[key] = created
                    JoinedFlight(created, isLeader = true)
                }
            }
        } catch (failure: Throwable) {
            releaseProducerResourcesSafely(releaseProducerResources)?.let { releaseFailure ->
                addSuppressedSafely(failure, releaseFailure)
            }
            throw failure
        }
        notifyJoinedSafely(onJoined, joined.isLeader)
        if (joined.isLeader) {
            dispatchLeader(joined.flight, releaseProducerResources, producer)
        } else {
            releaseProducerResourcesSafely(releaseProducerResources)?.let { failure ->
                detach(joined.flight)
                return DexCompilerSingleFlightOutcome.Failure(failure)
            }
        }
        return awaitCallerOutcome(joined.flight)
    }

    private fun dispatchLeader(
        flight: Flight,
        releaseProducerResources: () -> Unit,
        producer: () -> DexCompilerSingleFlightTerminal<V>,
    ) {
        val taskStarted = AtomicBoolean(false)
        try {
            leaderExecutor.execute {
                taskStarted.set(true)
                var fatal: VirtualMachineError? = null
                var terminal = try {
                    producer()
                } catch (failure: VirtualMachineError) {
                    fatal = failure
                    DexCompilerSingleFlightTerminal.Failure(failure)
                } catch (failure: Throwable) {
                    DexCompilerSingleFlightTerminal.Failure(failure)
                }
                releaseProducerResourcesSafely(releaseProducerResources)?.let { releaseFailure ->
                    terminal = mergeReleaseFailure(terminal, releaseFailure)
                }
                complete(flight, terminal)
                fatal?.let { throw it }
            }
        } catch (fatal: VirtualMachineError) {
            if (!taskStarted.get()) {
                releaseProducerResourcesSafely(releaseProducerResources)?.let { releaseFailure ->
                    addSuppressedSafely(fatal, releaseFailure)
                }
                complete(flight, DexCompilerSingleFlightTerminal.Failure(fatal))
            }
            throw fatal
        } catch (failure: Throwable) {
            releaseProducerResourcesSafely(releaseProducerResources)?.let { releaseFailure ->
                addSuppressedSafely(failure, releaseFailure)
            }
            complete(flight, DexCompilerSingleFlightTerminal.Failure(failure))
        }
    }

    private fun mergeReleaseFailure(
        terminal: DexCompilerSingleFlightTerminal<V>,
        releaseFailure: Throwable,
    ): DexCompilerSingleFlightTerminal<V> = when (terminal) {
        is DexCompilerSingleFlightTerminal.Value -> {
            closeValueSource(terminal.source)
            DexCompilerSingleFlightTerminal.Failure(releaseFailure)
        }
        is DexCompilerSingleFlightTerminal.Failure -> {
            addSuppressedSafely(terminal.cause, releaseFailure)
            terminal
        }
        DexCompilerSingleFlightTerminal.Cancelled ->
            DexCompilerSingleFlightTerminal.Failure(releaseFailure)
    }

    private fun releaseProducerResourcesSafely(release: () -> Unit): Throwable? = try {
        release()
        null
    } catch (failure: Throwable) {
        failure
    }

    private fun notifyJoinedSafely(observer: (Boolean) -> Unit, isLeader: Boolean) {
        val wasInterrupted = Thread.currentThread().isInterrupted
        var observerWasInterrupted = false
        try {
            observer(isLeader)
        } catch (error: Throwable) {
            observerWasInterrupted = error is InterruptedException
            // Diagnostics must not affect election, resource ownership, or terminal delivery.
        } finally {
            if (wasInterrupted || observerWasInterrupted) Thread.currentThread().interrupt()
        }
    }

    @Throws(InterruptedException::class)
    private fun awaitCallerOutcome(flight: Flight): DexCompilerSingleFlightOutcome<V> {
        try {
            flight.completed.await()
        } catch (interrupted: InterruptedException) {
            try {
                detach(flight)
            } catch (fatal: VirtualMachineError) {
                Thread.currentThread().interrupt()
                addSuppressedSafely(fatal, interrupted)
                throw fatal
            }
            throw interrupted
        }
        return try {
            when (val terminal = checkNotNull(flight.terminal)) {
                is DexCompilerSingleFlightTerminal.Value -> try {
                    DexCompilerSingleFlightOutcome.Value(terminal.source.acquireForCaller())
                } catch (fatal: VirtualMachineError) {
                    throw fatal
                } catch (failure: Throwable) {
                    DexCompilerSingleFlightOutcome.Failure(failure)
                }
                is DexCompilerSingleFlightTerminal.Failure -> {
                    if (terminal.cause is VirtualMachineError) throw terminal.cause
                    DexCompilerSingleFlightOutcome.Failure(terminal.cause)
                }
                DexCompilerSingleFlightTerminal.Cancelled -> DexCompilerSingleFlightOutcome.Cancelled
            }
        } finally {
            detach(flight)
        }
    }

    private fun complete(
        flight: Flight,
        terminal: DexCompilerSingleFlightTerminal<V>,
    ) {
        var sourceToClose: DexCompilerSingleFlightValueSource<V>? = null
        synchronized(lock) {
            if (flight.terminal != null) {
                sourceToClose = (terminal as? DexCompilerSingleFlightTerminal.Value)?.source
            } else {
                flight.terminal = terminal
                if (flights[flight.key] === flight) {
                    flights.remove(flight.key)
                }
                if (flight.waiterCount == 0) {
                    sourceToClose = claimSourceForCloseLocked(flight)
                }
            }
        }
        flight.completed.countDown()
        sourceToClose?.let(::closeValueSource)
    }

    private fun detach(flight: Flight) {
        var producerCancellation: (() -> Unit)? = null
        val sourceToClose = synchronized(lock) {
            check(flight.waiterCount > 0) { "DEX compiler single-flight waiter count underflow" }
            flight.waiterCount--
            if (flight.waiterCount == 0) {
                if (flight.terminal == null && !flight.producerCancellationRequested) {
                    flight.producerCancellationRequested = true
                    if (flights[flight.key] === flight) {
                        flights.remove(flight.key)
                    }
                    producerCancellation = flight.requestProducerCancellation
                }
                claimSourceForCloseLocked(flight)
            } else {
                null
            }
        }
        producerCancellation?.let(::requestProducerCancellationSafely)
        sourceToClose?.let(::closeValueSource)
    }

    private fun requestProducerCancellationSafely(request: () -> Unit) {
        val wasInterrupted = Thread.currentThread().isInterrupted
        var requestWasInterrupted = false
        try {
            request()
        } catch (fatal: VirtualMachineError) {
            throw fatal
        } catch (failure: Throwable) {
            requestWasInterrupted = failure is InterruptedException
            // A caller cancellation must not turn into fallback or depend on producer diagnostics.
        } finally {
            if (wasInterrupted || requestWasInterrupted) Thread.currentThread().interrupt()
        }
    }

    private fun claimSourceForCloseLocked(flight: Flight): DexCompilerSingleFlightValueSource<V>? {
        if (flight.sourceCloseClaimed) return null
        val source = (flight.terminal as? DexCompilerSingleFlightTerminal.Value)?.source ?: return null
        flight.sourceCloseClaimed = true
        return source
    }

    private fun closeValueSource(source: DexCompilerSingleFlightValueSource<V>) {
        try {
            source.close()
        } catch (failure: Throwable) {
            try {
                onValueSourceCloseFailure(failure)
            } catch (_: Throwable) {
                // Terminal delivery and map cleanup must not be undone by diagnostic reporting.
            }
        }
    }

    private inner class Flight(
        val key: K,
        val requestProducerCancellation: () -> Unit,
    ) {
        val completed = CountDownLatch(1)
        var waiterCount: Int = 0
        var producerCancellationRequested: Boolean = false
        var sourceCloseClaimed: Boolean = false

        @Volatile
        var terminal: DexCompilerSingleFlightTerminal<V>? = null
    }

    private data class JoinedFlight<F>(val flight: F, val isLeader: Boolean)
}

private fun addSuppressedSafely(primary: Throwable, suppressed: Throwable) {
    if (primary === suppressed) return
    try {
        primary.addSuppressed(suppressed)
    } catch (_: Throwable) {
        // Failure delivery must not depend on diagnostic suppression support.
    }
}

private fun String.isNormalizedSha256(): Boolean =
    length == 64 && all { it in '0'..'9' || it in 'a'..'f' }
