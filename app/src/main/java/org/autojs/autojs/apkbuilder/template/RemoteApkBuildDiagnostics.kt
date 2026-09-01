package org.autojs.autojs.apkbuilder.template

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

internal enum class RemoteApkBuildDiagnosticPhase {
    GATE,
    DISCOVERY,
    PREPARE,
    OPEN_SESSION,
    WAIT,
    TRANSFER,
    COMPLETE,
}

internal enum class RemoteApkBuildDiagnosticStatus {
    NOT_AVAILABLE,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNSUPPORTED,
    OTHER,
}

internal enum class RemoteApkBuildErrorCategory {
    NONE,
    HOST_SWITCH_DISABLED,
    PROVIDER_NOT_AVAILABLE,
    DISCOVERY_FAILED,
    PREPARATION_FAILED,
    SESSION_OPEN_FAILED,
    BINDER_LOST,
    NO_PROGRESS_TIMEOUT,
    TOTAL_TIMEOUT,
    REMOTE_UNSUPPORTED,
    REMOTE_FAILED,
    OUTPUT_METADATA_INVALID,
    OUTPUT_REJECTED,
    CANCELLED,
}

/**
 * One terminal, redacted remote-build event.
 *
 * This type deliberately has no free-form String field. Project paths, source text, provider
 * messages, signing credentials, and TypeScript staging secrets therefore cannot enter the event.
 */
internal data class RemoteApkBuildDiagnosticEvent(
    val sequence: Long,
    val protocolVersion: Int,
    val status: RemoteApkBuildDiagnosticStatus,
    val phase: RemoteApkBuildDiagnosticPhase,
    val elapsedMillis: Long,
    val outputSizeBytes: Long,
    val errorCategory: RemoteApkBuildErrorCategory,
) {
    fun safeLogLine(): String =
        "remote_apk_build seq=$sequence protocol=$protocolVersion status=${status.name} " +
            "phase=${phase.name} elapsed_ms=$elapsedMillis output_bytes=$outputSizeBytes " +
            "error=${errorCategory.name}"
}

/** Process-local, bounded diagnostics. Events are never persisted and never contain raw text. */
internal object RemoteApkBuildDiagnostics {

    private const val MAX_EVENTS = 32
    private const val UNKNOWN_OUTPUT_SIZE_BYTES = -1L

    private val nextSequence = AtomicLong(0L)
    private val lock = Any()
    private val events = ArrayDeque<RemoteApkBuildDiagnosticEvent>(MAX_EVENTS)

    fun record(
        protocolVersion: Int,
        status: RemoteApkBuildDiagnosticStatus,
        phase: RemoteApkBuildDiagnosticPhase,
        elapsedMillis: Long,
        outputSizeBytes: Long,
        errorCategory: RemoteApkBuildErrorCategory,
    ): RemoteApkBuildDiagnosticEvent {
        val event = RemoteApkBuildDiagnosticEvent(
            sequence = nextSequence.incrementAndGet(),
            protocolVersion = protocolVersion.coerceAtLeast(0),
            status = status,
            phase = phase,
            elapsedMillis = elapsedMillis.coerceAtLeast(0L),
            outputSizeBytes = outputSizeBytes.takeIf { it >= 0L } ?: UNKNOWN_OUTPUT_SIZE_BYTES,
            errorCategory = errorCategory,
        )
        synchronized(lock) {
            if (events.size == MAX_EVENTS) {
                events.removeFirst()
            }
            events.addLast(event)
        }
        return event
    }

    fun snapshot(): List<RemoteApkBuildDiagnosticEvent> = synchronized(lock) {
        events.toList()
    }

    internal fun resetForTest() {
        synchronized(lock) {
            events.clear()
            nextSequence.set(0L)
        }
    }
}
