package org.autojs.autojs.core.plugin.typescript

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal fun interface TypeScriptCompilationPerformanceReporter {

    fun report(snapshot: TypeScriptCompilationPerformanceSnapshot)

    companion object {
        val LOGCAT = TypeScriptCompilationPerformanceReporter { snapshot ->
            Log.i(TypeScriptCompilationPerformanceSnapshot.LOG_TAG, snapshot.toLogLine())
        }
    }
}

internal data class TypeScriptCompilationPerformanceSnapshot(
    val targetProfile: String,
    val outcome: String,
    val errorCode: String?,
    val cacheStatus: String,
    val totalElapsedMs: Long,
    val awaitPrewarmElapsedMs: Long,
    val discoveryElapsedMs: Long,
    val prepareElapsedMs: Long,
    val cacheLookupElapsedMs: Long,
    val bindAndPrewarmElapsedMs: Long?,
    val remoteRoundTripElapsedMs: Long?,
    val compilerElapsedMs: Long?,
    val compilerBeforeIngressElapsedMs: Long?,
    val compilerIngressElapsedMs: Long?,
    val compilerInterPhaseElapsedMs: Long?,
    val compilerParseElapsedMs: Long?,
    val compilerSemanticElapsedMs: Long?,
    val compilerEmitElapsedMs: Long?,
    val compilerAfterEmitElapsedMs: Long?,
    val outputProcessingElapsedMs: Long,
    val cachePublishElapsedMs: Long,
    val inputArchiveBytes: Long,
    val sourceFileCount: Int,
    val hostHeapUsedStartKiB: Long,
    val hostHeapUsedEndKiB: Long,
) {

    init {
        require(targetProfile in TypeScriptCompilerContract.TARGET_PROFILES)
        require(outcome.isNotBlank())
        require(cacheStatus in CACHE_STATUSES)
        require(totalElapsedMs >= 0L)
        require(awaitPrewarmElapsedMs >= 0L)
        require(discoveryElapsedMs >= 0L)
        require(prepareElapsedMs >= 0L)
        require(cacheLookupElapsedMs >= 0L)
        require(bindAndPrewarmElapsedMs == null || bindAndPrewarmElapsedMs >= 0L)
        require(remoteRoundTripElapsedMs == null || remoteRoundTripElapsedMs >= 0L)
        require(compilerElapsedMs == null || compilerElapsedMs >= 0L)
        require(
            compilerBeforeIngressElapsedMs == null || compilerBeforeIngressElapsedMs >= 0L,
        )
        require(compilerIngressElapsedMs == null || compilerIngressElapsedMs >= 0L)
        require(compilerInterPhaseElapsedMs == null || compilerInterPhaseElapsedMs >= 0L)
        require(compilerParseElapsedMs == null || compilerParseElapsedMs >= 0L)
        require(compilerSemanticElapsedMs == null || compilerSemanticElapsedMs >= 0L)
        require(compilerEmitElapsedMs == null || compilerEmitElapsedMs >= 0L)
        require(compilerAfterEmitElapsedMs == null || compilerAfterEmitElapsedMs >= 0L)
        require(outputProcessingElapsedMs >= 0L)
        require(cachePublishElapsedMs >= 0L)
        require(inputArchiveBytes >= 0L)
        require(sourceFileCount >= 0)
        require(hostHeapUsedStartKiB >= 0L)
        require(hostHeapUsedEndKiB >= 0L)
    }

    val hostHeapDeltaKiB: Long
        get() = hostHeapUsedEndKiB - hostHeapUsedStartKiB

    fun toLogLine(): String = buildString {
        append("typescript-compile-perf")
        append(" schema=").append(SCHEMA_REVISION)
        append(" profile=").append(targetProfile)
        append(" outcome=").append(outcome)
        append(" error=").append(errorCode ?: "none")
        append(" cache=").append(cacheStatus)
        append(" totalMs=").append(totalElapsedMs)
        append(" awaitPrewarmMs=").append(awaitPrewarmElapsedMs)
        append(" discoveryMs=").append(discoveryElapsedMs)
        append(" prepareMs=").append(prepareElapsedMs)
        append(" cacheLookupMs=").append(cacheLookupElapsedMs)
        append(" bindPrewarmMs=").append(bindAndPrewarmElapsedMs.asLogValue())
        append(" remoteRoundTripMs=").append(remoteRoundTripElapsedMs.asLogValue())
        append(" compilerMs=").append(compilerElapsedMs.asLogValue())
        append(" compilerBeforeIngressMs=")
            .append(compilerBeforeIngressElapsedMs.asLogValue())
        append(" compilerIngressMs=").append(compilerIngressElapsedMs.asLogValue())
        append(" compilerInterPhaseMs=")
            .append(compilerInterPhaseElapsedMs.asLogValue())
        append(" compilerParseMs=").append(compilerParseElapsedMs.asLogValue())
        append(" compilerSemanticMs=").append(compilerSemanticElapsedMs.asLogValue())
        append(" compilerEmitMs=").append(compilerEmitElapsedMs.asLogValue())
        append(" compilerAfterEmitMs=").append(compilerAfterEmitElapsedMs.asLogValue())
        append(" outputMs=").append(outputProcessingElapsedMs)
        append(" cachePublishMs=").append(cachePublishElapsedMs)
        append(" inputBytes=").append(inputArchiveBytes)
        append(" sourceFiles=").append(sourceFileCount)
        append(" hostHeapStartKiB=").append(hostHeapUsedStartKiB)
        append(" hostHeapEndKiB=").append(hostHeapUsedEndKiB)
        append(" hostHeapDeltaKiB=").append(hostHeapDeltaKiB)
    }

    companion object {
        /**
         * Revision 3 adds `awaitPrewarmMs`, `discoveryMs` and `cachePublishMs`, and narrows
         * `prepareMs` to archive preparation only (revision 2 counted everything before the
         * cache lookup, including plugin discovery). `outputMs` no longer includes the
         * persistent cache publish.
         */
        const val SCHEMA_REVISION = 3
        const val LOG_TAG = "TypeScriptCompiler"
        const val CACHE_DISABLED = "disabled"
        const val CACHE_NOT_CHECKED = "not_checked"
        const val CACHE_UNAVAILABLE = "unavailable"
        const val CACHE_MISS = "miss"
        const val CACHE_HIT = "hit"
        val CACHE_STATUSES = setOf(
            CACHE_DISABLED,
            CACHE_NOT_CHECKED,
            CACHE_UNAVAILABLE,
            CACHE_MISS,
            CACHE_HIT,
        )
    }
}

internal class TypeScriptCompilationPerformanceRecorder(
    private val targetProfile: String,
    cacheEnabled: Boolean,
    private val reporter: TypeScriptCompilationPerformanceReporter,
    private val monotonicNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val usedHeapKiB: () -> Long = ::currentUsedHeapKiB,
) {

    private val startedAtNanos = monotonicNanos()
    private val hostHeapUsedStartKiB = usedHeapKiB().coerceAtLeast(0L)
    private var awaitPrewarmElapsedMs = 0L
    private var discoveryElapsedMs = 0L
    private var prepareElapsedMs = 0L
    private var cacheLookupElapsedMs = 0L
    private var cachePublishElapsedMs = 0L
    private var bindAndPrewarmElapsedMs: Long? = null
    private var remoteRoundTripElapsedMs: Long? = null
    private var compilerElapsedMs: Long? = null
    private var outputProcessingElapsedMs = 0L
    private var inputArchiveBytes = 0L
    private var sourceFileCount = 0
    private var cacheStatus = if (cacheEnabled) {
        TypeScriptCompilationPerformanceSnapshot.CACHE_NOT_CHECKED
    } else {
        TypeScriptCompilationPerformanceSnapshot.CACHE_DISABLED
    }
    private var outcome = TypeScriptCompilerContract.OUTCOME_FAILED
    private var errorCode: String? = null
    private var reported = false
    private val compilerPhaseStartedAtNanos =
        LongArray(COMPILER_PHASES.size) { UNOBSERVED_NANOS }
    private val compilerPhaseCompletedAtNanos =
        LongArray(COMPILER_PHASES.size) { UNOBSERVED_NANOS }
    private var nextCompilerPhaseIndex = 0
    private var activeCompilerPhaseIndex = NO_ACTIVE_PHASE
    private var lastCompilerProgressAtNanos = UNOBSERVED_NANOS

    fun checkpoint(): Long = monotonicNanos()

    @Synchronized
    fun recordAwaitPrewarmSince(startedAtNanos: Long) {
        awaitPrewarmElapsedMs += elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordDiscoverySince(startedAtNanos: Long) {
        discoveryElapsedMs += elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordPreparedSince(
        startedAtNanos: Long,
        inputArchiveBytes: Long,
        sourceFileCount: Int,
    ) {
        require(inputArchiveBytes >= 0L)
        require(sourceFileCount >= 0)
        this.inputArchiveBytes = inputArchiveBytes
        this.sourceFileCount = sourceFileCount
        prepareElapsedMs = elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordAdditionalPreparationSince(
        startedAtNanos: Long,
        inputArchiveBytes: Long,
        sourceFileCount: Int,
    ) {
        require(inputArchiveBytes >= 0L)
        require(sourceFileCount >= 0)
        this.inputArchiveBytes = inputArchiveBytes
        this.sourceFileCount = sourceFileCount
        prepareElapsedMs += elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordCacheAvailability(available: Boolean) {
        if (cacheStatus != TypeScriptCompilationPerformanceSnapshot.CACHE_DISABLED) {
            cacheStatus = if (available) {
                TypeScriptCompilationPerformanceSnapshot.CACHE_MISS
            } else {
                TypeScriptCompilationPerformanceSnapshot.CACHE_UNAVAILABLE
            }
        }
    }

    @Synchronized
    fun recordCacheLookupSince(startedAtNanos: Long) {
        cacheLookupElapsedMs += elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordCacheHit() {
        cacheStatus = TypeScriptCompilationPerformanceSnapshot.CACHE_HIT
    }

    @Synchronized
    fun recordBindAndPrewarmSince(startedAtNanos: Long) {
        bindAndPrewarmElapsedMs = elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordRemoteRoundTripSince(startedAtNanos: Long) {
        remoteRoundTripElapsedMs = elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordCompilerElapsed(elapsedMs: Long) {
        require(elapsedMs >= 0L)
        compilerElapsedMs = elapsedMs
    }

    /**
     * Accepts one already decoded public phase event without allowing telemetry to affect the
     * compilation. Unknown, duplicated, or out-of-order events are ignored and reported as false.
     */
    @Synchronized
    fun recordCompilerPhase(
        phase: String,
        state: String,
        elapsedNanosSinceRequestStart: Long,
    ): Boolean {
        if (reported || elapsedNanosSinceRequestStart < 0L) return false
        if (
            lastCompilerProgressAtNanos != UNOBSERVED_NANOS &&
            elapsedNanosSinceRequestStart < lastCompilerProgressAtNanos
        ) {
            return false
        }
        val phaseIndex = COMPILER_PHASES.indexOf(phase)
        if (phaseIndex < 0) return false
        when (state) {
            TypeScriptR2Contract.PHASE_STATE_STARTED -> {
                if (
                    phaseIndex != nextCompilerPhaseIndex ||
                    activeCompilerPhaseIndex != NO_ACTIVE_PHASE ||
                    compilerPhaseStartedAtNanos[phaseIndex] != UNOBSERVED_NANOS
                ) {
                    return false
                }
                compilerPhaseStartedAtNanos[phaseIndex] = elapsedNanosSinceRequestStart
                activeCompilerPhaseIndex = phaseIndex
            }
            TypeScriptR2Contract.PHASE_STATE_COMPLETED -> {
                val startedAt = compilerPhaseStartedAtNanos[phaseIndex]
                if (
                    phaseIndex != activeCompilerPhaseIndex ||
                    startedAt == UNOBSERVED_NANOS ||
                    compilerPhaseCompletedAtNanos[phaseIndex] != UNOBSERVED_NANOS ||
                    elapsedNanosSinceRequestStart < startedAt
                ) {
                    return false
                }
                compilerPhaseCompletedAtNanos[phaseIndex] = elapsedNanosSinceRequestStart
                activeCompilerPhaseIndex = NO_ACTIVE_PHASE
                nextCompilerPhaseIndex += 1
            }
            else -> return false
        }
        lastCompilerProgressAtNanos = elapsedNanosSinceRequestStart
        return true
    }

    @Synchronized
    fun recordOutputProcessingSince(startedAtNanos: Long) {
        outputProcessingElapsedMs += elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordCachePublishSince(startedAtNanos: Long) {
        cachePublishElapsedMs += elapsedSince(startedAtNanos)
    }

    @Synchronized
    fun recordSuccess(output: TypeScriptCompilationOutput) {
        outcome = when (output) {
            is TypeScriptCompilationOutput.Emitted -> TypeScriptCompilerContract.OUTCOME_EMITTED
            is TypeScriptCompilationOutput.Diagnostics ->
                TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS
        }
        errorCode = null
    }

    @Synchronized
    fun recordFailure(error: Throwable) {
        val compilerError = error as? TypeScriptCompilerException
        outcome = compilerError?.outcome
            ?: if (error is CancellationException) {
                TypeScriptCompilerContract.OUTCOME_CANCELLED
            } else {
                TypeScriptCompilerContract.OUTCOME_FAILED
            }
        errorCode = compilerError?.code
    }

    @Synchronized
    fun finish() {
        if (reported) return
        reported = true
        val phaseTimings = compilerPhaseTimings()
        val snapshot = TypeScriptCompilationPerformanceSnapshot(
            targetProfile = targetProfile,
            outcome = outcome,
            errorCode = errorCode,
            cacheStatus = cacheStatus,
            totalElapsedMs = elapsedSince(startedAtNanos),
            awaitPrewarmElapsedMs = awaitPrewarmElapsedMs,
            discoveryElapsedMs = discoveryElapsedMs,
            prepareElapsedMs = prepareElapsedMs,
            cacheLookupElapsedMs = cacheLookupElapsedMs,
            bindAndPrewarmElapsedMs = bindAndPrewarmElapsedMs,
            remoteRoundTripElapsedMs = remoteRoundTripElapsedMs,
            compilerElapsedMs = compilerElapsedMs,
            compilerBeforeIngressElapsedMs = phaseTimings.beforeIngressElapsedMs,
            compilerIngressElapsedMs = phaseTimings.ingressElapsedMs,
            compilerInterPhaseElapsedMs = phaseTimings.interPhaseElapsedMs,
            compilerParseElapsedMs = phaseTimings.parseElapsedMs,
            compilerSemanticElapsedMs = phaseTimings.semanticElapsedMs,
            compilerEmitElapsedMs = phaseTimings.emitElapsedMs,
            compilerAfterEmitElapsedMs = phaseTimings.afterEmitElapsedMs,
            outputProcessingElapsedMs = outputProcessingElapsedMs,
            cachePublishElapsedMs = cachePublishElapsedMs,
            inputArchiveBytes = inputArchiveBytes,
            sourceFileCount = sourceFileCount,
            hostHeapUsedStartKiB = hostHeapUsedStartKiB,
            hostHeapUsedEndKiB = usedHeapKiB().coerceAtLeast(0L),
        )
        runCatching { reporter.report(snapshot) }
            .onFailure { error ->
                Log.w(
                    TypeScriptCompilationPerformanceSnapshot.LOG_TAG,
                    "TypeScript compilation performance reporting failed.",
                    error,
                )
            }
    }

    private fun elapsedSince(startedAtNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(
            (monotonicNanos() - startedAtNanos).coerceAtLeast(0L),
        )

    private fun compilerPhaseTimings(): CompilerPhaseTimings {
        fun durationAt(index: Int): Long? {
            val startedAt = compilerPhaseStartedAtNanos[index]
            val completedAt = compilerPhaseCompletedAtNanos[index]
            return if (startedAt == UNOBSERVED_NANOS || completedAt == UNOBSERVED_NANOS) {
                null
            } else {
                nanosToMillis(completedAt - startedAt)
            }
        }

        val interPhaseElapsedMs = if (
            compilerPhaseCompletedAtNanos.dropLast(1).all { it != UNOBSERVED_NANOS } &&
            compilerPhaseStartedAtNanos.drop(1).all { it != UNOBSERVED_NANOS }
        ) {
            nanosToMillis(
                (1 until COMPILER_PHASES.size).sumOf { index ->
                    (
                        compilerPhaseStartedAtNanos[index] -
                            compilerPhaseCompletedAtNanos[index - 1]
                        ).coerceAtLeast(0L)
                },
            )
        } else {
            null
        }
        val emitCompletedAt = compilerPhaseCompletedAtNanos[EMIT_PHASE_INDEX]
        return CompilerPhaseTimings(
            beforeIngressElapsedMs =
                compilerPhaseStartedAtNanos[INGRESS_PHASE_INDEX]
                    .takeIf { it != UNOBSERVED_NANOS }
                    ?.let(::nanosToMillis),
            ingressElapsedMs = durationAt(INGRESS_PHASE_INDEX),
            interPhaseElapsedMs = interPhaseElapsedMs,
            parseElapsedMs = durationAt(PARSE_PHASE_INDEX),
            semanticElapsedMs = durationAt(SEMANTIC_PHASE_INDEX),
            emitElapsedMs = durationAt(EMIT_PHASE_INDEX),
            afterEmitElapsedMs = if (emitCompletedAt == UNOBSERVED_NANOS) {
                null
            } else {
                compilerElapsedMs?.let { elapsedMs ->
                    (elapsedMs - nanosToMillis(emitCompletedAt)).coerceAtLeast(0L)
                }
            },
        )
    }

    private fun nanosToMillis(nanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(nanos.coerceAtLeast(0L))

    private data class CompilerPhaseTimings(
        val beforeIngressElapsedMs: Long?,
        val ingressElapsedMs: Long?,
        val interPhaseElapsedMs: Long?,
        val parseElapsedMs: Long?,
        val semanticElapsedMs: Long?,
        val emitElapsedMs: Long?,
        val afterEmitElapsedMs: Long?,
    )

    private companion object {
        const val UNOBSERVED_NANOS = -1L
        const val NO_ACTIVE_PHASE = -1
        const val INGRESS_PHASE_INDEX = 0
        const val PARSE_PHASE_INDEX = 1
        const val SEMANTIC_PHASE_INDEX = 2
        const val EMIT_PHASE_INDEX = 3
        val COMPILER_PHASES = listOf(
            TypeScriptR2Contract.PHASE_INGRESS,
            TypeScriptR2Contract.PHASE_PARSE,
            TypeScriptR2Contract.PHASE_SEMANTIC,
            TypeScriptR2Contract.PHASE_EMIT,
        )
    }
}

private fun Long?.asLogValue(): String = this?.toString() ?: "na"

private fun currentUsedHeapKiB(): Long {
    val runtime = Runtime.getRuntime()
    return ((runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L) + 1023L) / 1024L
}
