package org.autojs.autojs.core.plugin.typescript

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

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
    val prepareElapsedMs: Long,
    val cacheLookupElapsedMs: Long,
    val bindAndPrewarmElapsedMs: Long?,
    val remoteRoundTripElapsedMs: Long?,
    val compilerElapsedMs: Long?,
    val outputProcessingElapsedMs: Long,
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
        require(prepareElapsedMs >= 0L)
        require(cacheLookupElapsedMs >= 0L)
        require(bindAndPrewarmElapsedMs == null || bindAndPrewarmElapsedMs >= 0L)
        require(remoteRoundTripElapsedMs == null || remoteRoundTripElapsedMs >= 0L)
        require(compilerElapsedMs == null || compilerElapsedMs >= 0L)
        require(outputProcessingElapsedMs >= 0L)
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
        append(" prepareMs=").append(prepareElapsedMs)
        append(" cacheLookupMs=").append(cacheLookupElapsedMs)
        append(" bindPrewarmMs=").append(bindAndPrewarmElapsedMs.asLogValue())
        append(" remoteRoundTripMs=").append(remoteRoundTripElapsedMs.asLogValue())
        append(" compilerMs=").append(compilerElapsedMs.asLogValue())
        append(" outputMs=").append(outputProcessingElapsedMs)
        append(" inputBytes=").append(inputArchiveBytes)
        append(" sourceFiles=").append(sourceFileCount)
        append(" hostHeapStartKiB=").append(hostHeapUsedStartKiB)
        append(" hostHeapEndKiB=").append(hostHeapUsedEndKiB)
        append(" hostHeapDeltaKiB=").append(hostHeapDeltaKiB)
    }

    companion object {
        const val SCHEMA_REVISION = 1
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
    private var prepareElapsedMs = 0L
    private var cacheLookupElapsedMs = 0L
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

    fun checkpoint(): Long = monotonicNanos()

    fun recordPrepared(inputArchiveBytes: Long, sourceFileCount: Int) {
        require(inputArchiveBytes >= 0L)
        require(sourceFileCount >= 0)
        this.inputArchiveBytes = inputArchiveBytes
        this.sourceFileCount = sourceFileCount
        prepareElapsedMs = elapsedSince(startedAtNanos)
    }

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

    fun recordCacheAvailability(available: Boolean) {
        if (cacheStatus != TypeScriptCompilationPerformanceSnapshot.CACHE_DISABLED) {
            cacheStatus = if (available) {
                TypeScriptCompilationPerformanceSnapshot.CACHE_MISS
            } else {
                TypeScriptCompilationPerformanceSnapshot.CACHE_UNAVAILABLE
            }
        }
    }

    fun recordCacheLookupSince(startedAtNanos: Long) {
        cacheLookupElapsedMs += elapsedSince(startedAtNanos)
    }

    fun recordCacheHit() {
        cacheStatus = TypeScriptCompilationPerformanceSnapshot.CACHE_HIT
    }

    fun recordBindAndPrewarmSince(startedAtNanos: Long) {
        bindAndPrewarmElapsedMs = elapsedSince(startedAtNanos)
    }

    fun recordRemoteRoundTripSince(startedAtNanos: Long) {
        remoteRoundTripElapsedMs = elapsedSince(startedAtNanos)
    }

    fun recordCompilerElapsed(elapsedMs: Long) {
        require(elapsedMs >= 0L)
        compilerElapsedMs = elapsedMs
    }

    fun recordOutputProcessingSince(startedAtNanos: Long) {
        outputProcessingElapsedMs += elapsedSince(startedAtNanos)
    }

    fun recordSuccess(output: TypeScriptCompilationOutput) {
        outcome = when (output) {
            is TypeScriptCompilationOutput.Emitted -> TypeScriptCompilerContract.OUTCOME_EMITTED
            is TypeScriptCompilationOutput.Diagnostics ->
                TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS
        }
        errorCode = null
    }

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

    fun finish() {
        if (reported) return
        reported = true
        val snapshot = TypeScriptCompilationPerformanceSnapshot(
            targetProfile = targetProfile,
            outcome = outcome,
            errorCode = errorCode,
            cacheStatus = cacheStatus,
            totalElapsedMs = elapsedSince(startedAtNanos),
            prepareElapsedMs = prepareElapsedMs,
            cacheLookupElapsedMs = cacheLookupElapsedMs,
            bindAndPrewarmElapsedMs = bindAndPrewarmElapsedMs,
            remoteRoundTripElapsedMs = remoteRoundTripElapsedMs,
            compilerElapsedMs = compilerElapsedMs,
            outputProcessingElapsedMs = outputProcessingElapsedMs,
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
}

private fun Long?.asLogValue(): String = this?.toString() ?: "na"

private fun currentUsedHeapKiB(): Long {
    val runtime = Runtime.getRuntime()
    return ((runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L) + 1023L) / 1024L
}
