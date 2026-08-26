package org.autojs.plugin.jvmsource.api

import java.util.Collections

enum class JvmObservationStartProfile(val wireCode: Int) {
    COLD(1),
    WARM(2),
}

enum class JvmObservationProcess(val wireCode: Int) {
    COMPILER(1),
    WORKER(2),
}

enum class JvmObservationPhase(val wireCode: Int) {
    COMPILE(1),
    D8(2),
    LOAD(3),
    RUN(4),
    TERMINATION(5),
}

enum class JvmObservationCacheOutcome(val wireCode: Int) {
    NOT_EVALUATED(1),
    HIT(2),
    MISS(3),
}

enum class JvmObservationCacheMissReason(val wireCode: Int) {
    NOT_FOUND(1),
    INVALID_OR_EXPIRED(2),
    CACHE_UNAVAILABLE(3),
    MATERIALIZATION_FAILED(4),
    CACHE_DISABLED(5),
    PROVIDER_IDENTITY_UNAVAILABLE(6),
    PROVIDER_IDENTITY_DRIFTED(7),
}

/**
 * One path-free, identity-free resource sample. Every value is a coarse numeric measurement;
 * unavailable probes are represented by null rather than a sentinel.
 */
data class JvmSourceResourceObservation(
    val process: JvmObservationProcess,
    val phase: JvmObservationPhase,
    val rssBytes: Long? = null,
    val openFileDescriptorCount: Int? = null,
    val temporaryStorageBytes: Long? = null,
    val outputBytes: Long? = null,
)

/**
 * Protocol 1.5 terminal observation. It deliberately cannot represent paths, source content,
 * package/component names, signatures, Binder identities, UIDs, PIDs, or diagnostic text.
 */
class JvmSourceObservation(
    val requestId: JvmRequestId,
    val sessionElapsedMillis: Long,
    val workerStartupDurationMillis: Long?,
    val compilerStartProfile: JvmObservationStartProfile,
    val workerStartProfile: JvmObservationStartProfile?,
    val compileDurationMillis: Long?,
    val d8DurationMillis: Long?,
    val loadDurationMillis: Long?,
    val runDurationMillis: Long?,
    val terminationDurationMillis: Long?,
    val cacheOutcome: JvmObservationCacheOutcome,
    val cacheMissReason: JvmObservationCacheMissReason?,
    resources: Collection<JvmSourceResourceObservation>,
) {
    val resources: List<JvmSourceResourceObservation> =
        Collections.unmodifiableList(ArrayList(resources))

    fun copy(
        requestId: JvmRequestId = this.requestId,
        sessionElapsedMillis: Long = this.sessionElapsedMillis,
        workerStartupDurationMillis: Long? = this.workerStartupDurationMillis,
        compilerStartProfile: JvmObservationStartProfile = this.compilerStartProfile,
        workerStartProfile: JvmObservationStartProfile? = this.workerStartProfile,
        compileDurationMillis: Long? = this.compileDurationMillis,
        d8DurationMillis: Long? = this.d8DurationMillis,
        loadDurationMillis: Long? = this.loadDurationMillis,
        runDurationMillis: Long? = this.runDurationMillis,
        terminationDurationMillis: Long? = this.terminationDurationMillis,
        cacheOutcome: JvmObservationCacheOutcome = this.cacheOutcome,
        cacheMissReason: JvmObservationCacheMissReason? = this.cacheMissReason,
        resources: Collection<JvmSourceResourceObservation> = this.resources,
    ): JvmSourceObservation = JvmSourceObservation(
        requestId,
        sessionElapsedMillis,
        workerStartupDurationMillis,
        compilerStartProfile,
        workerStartProfile,
        compileDurationMillis,
        d8DurationMillis,
        loadDurationMillis,
        runDurationMillis,
        terminationDurationMillis,
        cacheOutcome,
        cacheMissReason,
        resources,
    )

    override fun equals(other: Any?): Boolean = other is JvmSourceObservation &&
        requestId == other.requestId &&
        sessionElapsedMillis == other.sessionElapsedMillis &&
        workerStartupDurationMillis == other.workerStartupDurationMillis &&
        compilerStartProfile == other.compilerStartProfile &&
        workerStartProfile == other.workerStartProfile &&
        compileDurationMillis == other.compileDurationMillis &&
        d8DurationMillis == other.d8DurationMillis &&
        loadDurationMillis == other.loadDurationMillis &&
        runDurationMillis == other.runDurationMillis &&
        terminationDurationMillis == other.terminationDurationMillis &&
        cacheOutcome == other.cacheOutcome &&
        cacheMissReason == other.cacheMissReason &&
        resources == other.resources

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + sessionElapsedMillis.hashCode()
        result = 31 * result + (workerStartupDurationMillis?.hashCode() ?: 0)
        result = 31 * result + compilerStartProfile.hashCode()
        result = 31 * result + (workerStartProfile?.hashCode() ?: 0)
        result = 31 * result + (compileDurationMillis?.hashCode() ?: 0)
        result = 31 * result + (d8DurationMillis?.hashCode() ?: 0)
        result = 31 * result + (loadDurationMillis?.hashCode() ?: 0)
        result = 31 * result + (runDurationMillis?.hashCode() ?: 0)
        result = 31 * result + (terminationDurationMillis?.hashCode() ?: 0)
        result = 31 * result + cacheOutcome.hashCode()
        result = 31 * result + (cacheMissReason?.hashCode() ?: 0)
        return 31 * result + resources.hashCode()
    }

    override fun toString(): String =
        "JvmSourceObservation(requestId=$requestId, sessionElapsedMillis=$sessionElapsedMillis, " +
            "workerStartupDurationMillis=$workerStartupDurationMillis, " +
            "compilerStartProfile=$compilerStartProfile, workerStartProfile=$workerStartProfile, " +
            "compileDurationMillis=$compileDurationMillis, d8DurationMillis=$d8DurationMillis, " +
            "loadDurationMillis=$loadDurationMillis, runDurationMillis=$runDurationMillis, " +
            "terminationDurationMillis=$terminationDurationMillis, cacheOutcome=$cacheOutcome, " +
            "cacheMissReason=$cacheMissReason, resources=$resources)"
}
