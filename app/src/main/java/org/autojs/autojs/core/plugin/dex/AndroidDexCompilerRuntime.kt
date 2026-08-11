package org.autojs.autojs.core.plugin.dex

import android.content.Context
import android.os.Build
import android.os.Looper
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal enum class DexCompilerLocalReason {
    MAIN_THREAD,
    OPT_IN_DISABLED,
    OPT_IN_UNAVAILABLE,
    OPT_IN_REJECTED,
    CIRCUIT_OPEN,
    SNAPSHOT_FAILED,
    ATTEMPT_SETUP_FAILED,
    REMOTE_ATTEMPT_FAILED,
}

internal sealed class DexCompilerRuntimeDecision {
    data class Published(
        val artifact: DexCompilerVerifiedArtifact,
        val fallbackInput: DexCompilerRetainedInput,
        val cacheHit: Boolean,
    ) : DexCompilerRuntimeDecision()

    data class UseLocal(
        val reason: DexCompilerLocalReason,
        val fallbackInput: DexCompilerRetainedInput? = null,
        val experimentalFailure: DexCompilerExperimentalFailure? = null,
        val remoteFailure: DexCompilerExperimentalOutcome.RemoteFailure? = null,
    ) : DexCompilerRuntimeDecision()

    object Cancelled : DexCompilerRuntimeDecision()
}

/** Debug-only, process-scoped instrumentation seam for production-wiring Android tests. */
internal interface DexCompilerRuntimeAndroidTestProbe {
    /**
     * Debug-only fault seam at the exact production raw-JAR routing boundary.
     *
     * Returning null leaves the production coordinator untouched. A non-null decision is owned by
     * the normal [org.autojs.autojs.rhino.AndroidClassLoader] decision/cleanup path.
     */
    fun overrideCompileDecision(jar: File): DexCompilerRuntimeDecision? = null

    fun onLocalCompilerStarted(jar: File) = Unit
    fun onSingleFlightJoined(isLeader: Boolean) = Unit
    fun onSharedProducerStarted() = Unit
    fun onDispatchCommitted() = Unit
    fun onCallerCancelledAfterFallbackCloseAttempt() = Unit
    fun onFallbackCloseFailure() = Unit
}

internal object DexCompilerRuntimeAndroidTestInstrumentation {
    private val activeProbe = AtomicReference<DexCompilerRuntimeAndroidTestProbe?>()

    /** Release builds explicitly reject registration; concurrent/nested registrations fail closed. */
    fun installForAndroidTest(probe: DexCompilerRuntimeAndroidTestProbe): Closeable {
        check(BuildConfig.DEBUG) { "DEX compiler Android-test instrumentation is unavailable in release builds" }
        check(activeProbe.compareAndSet(null, probe)) { "DEX compiler Android-test instrumentation is already active" }
        val closed = AtomicBoolean(false)
        return Closeable {
            if (closed.compareAndSet(false, true)) {
                check(activeProbe.compareAndSet(probe, null)) {
                    "DEX compiler Android-test instrumentation ownership changed"
                }
            }
        }
    }

    fun singleFlightJoined(isLeader: Boolean) = notifyProbe { it.onSingleFlightJoined(isLeader) }
    fun compileDecisionOverrideOrNull(jar: File): DexCompilerRuntimeDecision? =
        queryProbe(
            action = { it.overrideCompileDecision(jar) },
            onDiscard = { decision ->
                when (decision) {
                    is DexCompilerRuntimeDecision.Published -> decision.fallbackInput.close()
                    is DexCompilerRuntimeDecision.UseLocal -> decision.fallbackInput?.close()
                    DexCompilerRuntimeDecision.Cancelled -> Unit
                }
            },
        )

    fun localCompilerStarted(jar: File) = notifyProbe { it.onLocalCompilerStarted(jar) }
    fun sharedProducerStarted() = notifyProbe { it.onSharedProducerStarted() }
    fun dispatchCommitted() = notifyProbe { it.onDispatchCommitted() }
    fun callerCancelledAfterFallbackCloseAttempt() =
        notifyProbe { it.onCallerCancelledAfterFallbackCloseAttempt() }
    fun fallbackCloseFailed() = notifyProbe { it.onFallbackCloseFailure() }

    private fun notifyProbe(action: (DexCompilerRuntimeAndroidTestProbe) -> Unit) {
        if (!BuildConfig.DEBUG) return
        val probe = activeProbe.get() ?: return
        val wasInterrupted = Thread.currentThread().isInterrupted
        var callbackWasInterrupted = false
        try {
            action(probe)
        } catch (error: Throwable) {
            callbackWasInterrupted = error is InterruptedException ||
                ScriptInterruptedException.causedByInterrupt(error)
            // Probe failures must never affect production ownership or terminal state.
        } finally {
            // A debug probe may wait on a latch. Preserve cancellation if that wait observes and
            // clears an interrupt; instrumentation must never turn cancellation into continuation.
            if (wasInterrupted || callbackWasInterrupted) Thread.currentThread().interrupt()
        }
    }

    private fun <T> queryProbe(
        action: (DexCompilerRuntimeAndroidTestProbe) -> T?,
        onDiscard: (T) -> Unit,
    ): T? {
        if (!BuildConfig.DEBUG) return null
        val probe = activeProbe.get() ?: return null
        if (Thread.currentThread().isInterrupted) return null
        var callbackWasInterrupted = false
        return try {
            action(probe)?.let { value ->
                if (Thread.currentThread().isInterrupted) {
                    callbackWasInterrupted = true
                    runCatching { onDiscard(value) }
                    null
                } else {
                    value
                }
            }
        } catch (error: Throwable) {
            callbackWasInterrupted = error is InterruptedException ||
                ScriptInterruptedException.causedByInterrupt(error)
            // A probe fault must fall through to the unmodified production coordinator.
            null
        } finally {
            if (callbackWasInterrupted) Thread.currentThread().interrupt()
        }
    }
}

/** Process-scoped coordinator. R1.2 adds authenticated semantic-cache lookup before dispatch. */
internal class AndroidDexCompilerJarLoadCoordinator private constructor(
    private val applicationContext: Context,
    private val releaseBuild: Boolean,
    private val runtimeApi: Int,
) {
    private val circuitOpen = AtomicBoolean(false)
    private val backendLock = Any()

    @Volatile
    private var backend: Backend? = null

    private val transport by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidDexCompilerExperimentalTransport(applicationContext)
    }

    fun compile(jar: File): DexCompilerRuntimeDecision {
        if (Thread.currentThread().isInterrupted) return DexCompilerRuntimeDecision.Cancelled
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.MAIN_THREAD)
        }
        if (circuitOpen.get()) {
            return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.CIRCUIT_OPEN)
        }

        val options = try {
            DexCompilerOptInStore.read()
        } catch (_: Exception) {
            DexCompilerOptInOptions()
        }
        val optIn = DexCompilerOptInPolicy.decide(
            options = options,
            releaseBuild = releaseBuild,
            discoverCandidates = {
                transport.discoverExactAction(DexCompilerContract.SERVICE_ACTION)
            },
        )
        val identity = when (optIn) {
            DexCompilerOptInDecision.Disabled,
            DexCompilerOptInDecision.NoSelection ->
                return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.OPT_IN_DISABLED)
            is DexCompilerOptInDecision.Unavailable ->
                return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.OPT_IN_UNAVAILABLE)
            is DexCompilerOptInDecision.Rejected ->
                return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.OPT_IN_REJECTED)
            is DexCompilerOptInDecision.Selected -> optIn.identity
        }
        return compileSelected(jar, identity)
    }

    private fun compileSelected(
        jar: File,
        identity: DexCompilerPinnedIdentity,
    ): DexCompilerRuntimeDecision {
        val backend = try {
            backend()
        } catch (_: Exception) {
            return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.ATTEMPT_SETUP_FAILED)
        }
        val snapshot = try {
            FileInputStream(jar).use { input ->
                backend.artifactStore.snapshot(
                    input = input,
                    maximumBytes = DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES,
                    isCancelled = { Thread.currentThread().isInterrupted },
                )
            }
        } catch (_: DexCompilerStreamCancelledException) {
            return DexCompilerRuntimeDecision.Cancelled
        } catch (_: Exception) {
            return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.SNAPSHOT_FAILED)
        }

        val retainedInput = try {
            snapshot.retainForLocalFallback()
        } catch (_: Exception) {
            snapshot.close()
            return DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.SNAPSHOT_FAILED)
        }
        val request = try {
            DexCompilerRuntimeRequestFactory.create(snapshot, runtimeApi)
        } catch (_: Exception) {
            snapshot.close()
            return DexCompilerRuntimeDecision.UseLocal(
                reason = DexCompilerLocalReason.ATTEMPT_SETUP_FAILED,
                fallbackInput = retainedInput,
            )
        }
        val attemptKey = try {
            DexCompilerSingleFlightAttemptKey.from(request, identity, runtimeApi)
        } catch (_: Exception) {
            snapshot.close()
            return DexCompilerRuntimeDecision.UseLocal(
                reason = DexCompilerLocalReason.ATTEMPT_SETUP_FAILED,
                fallbackInput = retainedInput,
            )
        }

        val sharedOutcome = try {
            backend.singleFlight.execute(
                key = attemptKey,
                fallback = retainedInput,
                releaseProducerResources = snapshot::close,
                onJoined = DexCompilerRuntimeAndroidTestInstrumentation::singleFlightJoined,
            ) {
                runSharedAttempt(
                    backend = backend,
                    snapshot = snapshot,
                    request = request,
                    identity = identity,
                )
            }
        } catch (_: InterruptedException) {
            DexCompilerRuntimeAndroidTestInstrumentation.callerCancelledAfterFallbackCloseAttempt()
            Thread.currentThread().interrupt()
            return DexCompilerRuntimeDecision.Cancelled
        }

        return when (sharedOutcome) {
            is DexCompilerSingleFlightCallerOutcome.Value -> DexCompilerRuntimeDecision.Published(
                artifact = sharedOutcome.value.artifact,
                fallbackInput = sharedOutcome.fallback,
                cacheHit = sharedOutcome.value.cacheHit,
            )
            is DexCompilerSingleFlightCallerOutcome.Failure -> {
                val sharedFailure = sharedOutcome.cause as? DexCompilerSharedAttemptFailure
                if (sharedFailure == null) {
                    DexCompilerRuntimeDecision.UseLocal(
                        reason = DexCompilerLocalReason.ATTEMPT_SETUP_FAILED,
                        fallbackInput = sharedOutcome.fallback,
                    )
                } else {
                    DexCompilerRuntimeDecision.UseLocal(
                        reason = DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED,
                        fallbackInput = sharedOutcome.fallback,
                        experimentalFailure = sharedFailure.failure,
                        remoteFailure = sharedFailure.remoteFailure,
                    )
                }
            }
            DexCompilerSingleFlightCallerOutcome.Cancelled -> DexCompilerRuntimeDecision.Cancelled
        }
    }

    private fun runSharedAttempt(
        backend: Backend,
        snapshot: DexCompilerInputSnapshot,
        request: DexCompileRequest,
        identity: DexCompilerPinnedIdentity,
    ): DexCompilerSingleFlightTerminal<DexCompilerSingleFlightPublishedArtifact> {
        DexCompilerRuntimeAndroidTestInstrumentation.sharedProducerStarted()
        val output = try {
            backend.artifactStore.outputTransaction()
        } catch (error: Exception) {
            return DexCompilerSingleFlightTerminal.Failure(error)
        }
        val invocation = try {
            DexCompilerExperimentalInvocation(
                request = request,
                inputSnapshot = snapshot,
                outputTransaction = output,
                releaseBuild = releaseBuild,
                invocationKind = DexCompilerInvocationKind.SYNCHRONOUS,
                preferredComponent = identity.component,
                requestFinalization = DexCompilerRequestFinalization.CAPABILITY_BOUND,
                requiredIdentity = identity,
                dispatchObserver = DexCompilerDispatchObserver(
                    DexCompilerRuntimeAndroidTestInstrumentation::dispatchCommitted,
                ),
            )
        } catch (error: Exception) {
            closeUnusedOutput(output)
            return DexCompilerSingleFlightTerminal.Failure(error)
        }

        val outcome = try {
            backend.runner.run(invocation)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            closeUnusedOutput(output)
            return DexCompilerSingleFlightTerminal.Cancelled
        } catch (error: Exception) {
            closeUnusedOutput(output)
            return DexCompilerSingleFlightTerminal.Failure(error)
        }

        return when (outcome) {
            is DexCompilerExperimentalOutcome.Published -> publishedTerminal(
                backend = backend,
                cacheKeySha256 = outcome.cacheKeySha256,
                cacheHit = false,
            )
            is DexCompilerExperimentalOutcome.CacheHit -> publishedTerminal(
                backend = backend,
                cacheKeySha256 = outcome.cacheKeySha256,
                cacheHit = true,
            )
            is DexCompilerExperimentalOutcome.UseLocalCompiler -> {
                DexCompilerRuntimeDiagnostics.record(outcome)
                maybeOpenCircuit(outcome.failure, backend.blockingExecutor)
                DexCompilerSingleFlightTerminal.Failure(
                    DexCompilerSharedAttemptFailure(
                        failure = outcome.failure,
                        remoteFailure = outcome.remoteFailure,
                    ),
                )
            }
            DexCompilerExperimentalOutcome.Cancelled -> DexCompilerSingleFlightTerminal.Cancelled
        }
    }

    private fun publishedTerminal(
        backend: Backend,
        cacheKeySha256: String,
        cacheHit: Boolean,
    ): DexCompilerSingleFlightTerminal<DexCompilerSingleFlightPublishedArtifact> =
        DexCompilerSingleFlightTerminal.Value(
            DexCompilerSingleFlightArtifactSource(
                cacheKeySha256 = cacheKeySha256,
                cacheHit = cacheHit,
                cache = DexCompilerVerifiedCache(backend.artifactStore::lookupVerified),
            ),
        )

    private fun closeUnusedOutput(output: DexCompilerOutputTransaction) {
        runCatching { output.rollback() }
        runCatching { output.close() }
    }

    private fun maybeOpenCircuit(
        failure: DexCompilerExperimentalFailure,
        blockingExecutor: ThreadPoolExecutor,
    ) {
        val blockingCallStillActive = blockingExecutor.activeCount > 0
        if (failure == DexCompilerExperimentalFailure.BLOCKING_EXECUTOR_REJECTED ||
            failure == DexCompilerExperimentalFailure.WORKER_REJECTED ||
            failure == DexCompilerExperimentalFailure.TIMEOUT && blockingCallStillActive
        ) {
            circuitOpen.set(true)
        }
    }

    private fun backend(): Backend {
        backend?.let { return it }
        return synchronized(backendLock) {
            backend ?: createBackend().also { backend = it }
        }
    }

    private fun createBackend(): Backend {
        val threadFactory = DexCompilerThreadFactory()
        val worker = singleBoundedExecutor("control", threadFactory)
        val drain = singleBoundedExecutor("drain", threadFactory)
        val orchestration = singleBoundedExecutor("orchestration", threadFactory)
        val blocking = ThreadPoolExecutor(
            0,
            1,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            threadFactory.named("binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val scheduler = ScheduledThreadPoolExecutor(1, threadFactory.named("deadline")).apply {
            removeOnCancelPolicy = true
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        }
        return try {
            val artifactStore = AndroidDexCompilerArtifactStore(applicationContext, drain)
            val harness = DexCompilerExperimentalHarness(
                transport = transport,
                workerExecutor = worker,
                blockingExecutor = blocking,
                deadlineScheduler = DexCompilerDeadlineScheduler { delayMillis, action ->
                    val future = scheduler.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
                    java.io.Closeable { future.cancel(false) }
                },
                threadProbe = DexCompilerThreadProbe { Looper.myLooper() == Looper.getMainLooper() },
                hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                hostRuntimeApi = runtimeApi,
                verifiedCache = DexCompilerVerifiedCache(artifactStore::lookupVerified),
            )
            Backend(
                artifactStore = artifactStore,
                runner = DexCompilerSynchronousAttemptRunner(harness::start),
                blockingExecutor = blocking,
                singleFlight = DexCompilerSingleFlightCallerCoordinator<
                    DexCompilerSingleFlightAttemptKey,
                    DexCompilerSingleFlightPublishedArtifact,
                    DexCompilerRetainedInput,
                >(
                    DexCompilerSingleFlight<
                        DexCompilerSingleFlightAttemptKey,
                        DexCompilerSingleFlightPublishedArtifact,
                    >(orchestration),
                    onFallbackCloseFailure = {
                        DexCompilerRuntimeAndroidTestInstrumentation.fallbackCloseFailed()
                    },
                ),
            )
        } catch (error: Exception) {
            listOf(worker, drain, orchestration, blocking, scheduler).forEach { it.shutdownNow() }
            throw error
        }
    }

    companion object {
        fun create(context: Context): AndroidDexCompilerJarLoadCoordinator =
            AndroidDexCompilerJarLoadCoordinator(
                applicationContext = context.applicationContext,
                releaseBuild = !BuildConfig.DEBUG,
                runtimeApi = Build.VERSION.SDK_INT,
            )

        private fun singleBoundedExecutor(
            role: String,
            threadFactory: DexCompilerThreadFactory,
        ): ThreadPoolExecutor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            threadFactory.named(role),
            ThreadPoolExecutor.AbortPolicy(),
        )
    }

    private data class Backend(
        val artifactStore: AndroidDexCompilerArtifactStore,
        val runner: DexCompilerSynchronousAttemptRunner,
        val blockingExecutor: ThreadPoolExecutor,
        val singleFlight: DexCompilerSingleFlightCallerCoordinator<
            DexCompilerSingleFlightAttemptKey,
            DexCompilerSingleFlightPublishedArtifact,
            DexCompilerRetainedInput,
        >,
    )
}

private class DexCompilerSharedAttemptFailure(
    val failure: DexCompilerExperimentalFailure,
    val remoteFailure: DexCompilerExperimentalOutcome.RemoteFailure?,
) : RuntimeException("Shared DEX compiler attempt failed: $failure")

internal object AndroidDexCompilerRuntime {
    private val lock = Any()

    @Volatile
    private var coordinator: AndroidDexCompilerJarLoadCoordinator? = null

    fun initialize(context: Context, eligibleProcess: Boolean) {
        if (!eligibleProcess || coordinator != null) return
        synchronized(lock) {
            if (coordinator == null) {
                coordinator = try {
                    AndroidDexCompilerJarLoadCoordinator.create(context.applicationContext)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun coordinatorOrNull(): AndroidDexCompilerJarLoadCoordinator? = coordinator
}

private class DexCompilerThreadFactory(
    private val role: String = "runtime",
    private val counter: AtomicInteger = AtomicInteger(),
) : ThreadFactory {
    override fun newThread(runnable: Runnable): Thread =
        Thread(runnable, "AutoJs6-DexCompiler-$role-${counter.incrementAndGet()}")

    fun named(role: String): ThreadFactory = DexCompilerThreadFactory(role, counter)
}
