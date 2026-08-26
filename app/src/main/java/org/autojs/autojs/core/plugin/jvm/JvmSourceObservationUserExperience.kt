package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmObservationCacheMissReason
import org.autojs.plugin.jvmsource.api.JvmObservationCacheOutcome
import org.autojs.plugin.jvmsource.api.JvmObservationStartProfile
import org.autojs.plugin.jvmsource.api.JvmSourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourceValidation

internal data class JvmSourcePublicObservation(
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
    val resourceSampleCount: Int,
    val peakRssBytes: Long?,
    val maximumOpenFileDescriptorCount: Int?,
    val maximumTemporaryStorageBytes: Long?,
    val maximumOutputBytes: Long?,
)

/** Independent Host projection: request IDs and all provider/process identities are dropped. */
internal object JvmSourcePublicObservationPolicy {
    fun project(value: JvmSourceObservation): JvmSourcePublicObservation {
        JvmSourceValidation.validateObservation(value)
        return JvmSourcePublicObservation(
            sessionElapsedMillis = value.sessionElapsedMillis,
            workerStartupDurationMillis = value.workerStartupDurationMillis,
            compilerStartProfile = value.compilerStartProfile,
            workerStartProfile = value.workerStartProfile,
            compileDurationMillis = value.compileDurationMillis,
            d8DurationMillis = value.d8DurationMillis,
            loadDurationMillis = value.loadDurationMillis,
            runDurationMillis = value.runDurationMillis,
            terminationDurationMillis = value.terminationDurationMillis,
            cacheOutcome = value.cacheOutcome,
            cacheMissReason = value.cacheMissReason,
            resourceSampleCount = value.resources.size,
            peakRssBytes = value.resources.mapNotNull { it.rssBytes }.maxOrNull(),
            maximumOpenFileDescriptorCount =
                value.resources.mapNotNull { it.openFileDescriptorCount }.maxOrNull(),
            maximumTemporaryStorageBytes =
                value.resources.mapNotNull { it.temporaryStorageBytes }.maxOrNull(),
            maximumOutputBytes = value.resources.mapNotNull { it.outputBytes }.maxOrNull(),
        )
    }
}

internal object JvmSourcePublicObservationFormatter {
    fun format(value: JvmSourcePublicObservation): String = buildString(256) {
        append("JVM observation: total=").appendMillis(value.sessionElapsedMillis)
        append(", worker-start=").appendMillis(value.workerStartupDurationMillis)
        append(", compile=").appendMillis(value.compileDurationMillis)
        append(", d8=").appendMillis(value.d8DurationMillis)
        append(", load=").appendMillis(value.loadDurationMillis)
        append(", run=").appendMillis(value.runDurationMillis)
        append(", cleanup=").appendMillis(value.terminationDurationMillis)
        append(", cache=").append(cacheOutcome(value.cacheOutcome))
        value.cacheMissReason?.let { append('/').append(cacheMissReason(it)) }
        append(", starts=compiler:").append(startProfile(value.compilerStartProfile))
        append(" worker:").append(value.workerStartProfile?.let(::startProfile) ?: "n/a")
        append(", samples=").append(value.resourceSampleCount)
        append(", peak-rss=").appendBytes(value.peakRssBytes)
        append(", max-fd=").append(value.maximumOpenFileDescriptorCount ?: "n/a")
        append(", max-temp=").appendBytes(value.maximumTemporaryStorageBytes)
        append(", max-output=").appendBytes(value.maximumOutputBytes)
    }

    private fun StringBuilder.appendMillis(value: Long?) {
        if (value == null) append("n/a") else append(value).append(" ms")
    }

    private fun StringBuilder.appendBytes(value: Long?) {
        if (value == null) append("n/a") else append(value).append(" B")
    }

    private fun startProfile(value: JvmObservationStartProfile): String = when (value) {
        JvmObservationStartProfile.COLD -> "COLD"
        JvmObservationStartProfile.WARM -> "WARM"
    }

    private fun cacheOutcome(value: JvmObservationCacheOutcome): String = when (value) {
        JvmObservationCacheOutcome.NOT_EVALUATED -> "NOT_EVALUATED"
        JvmObservationCacheOutcome.HIT -> "HIT"
        JvmObservationCacheOutcome.MISS -> "MISS"
    }

    private fun cacheMissReason(value: JvmObservationCacheMissReason): String = when (value) {
        JvmObservationCacheMissReason.NOT_FOUND -> "NOT_FOUND"
        JvmObservationCacheMissReason.INVALID_OR_EXPIRED -> "INVALID_OR_EXPIRED"
        JvmObservationCacheMissReason.CACHE_UNAVAILABLE -> "CACHE_UNAVAILABLE"
        JvmObservationCacheMissReason.MATERIALIZATION_FAILED -> "MATERIALIZATION_FAILED"
        JvmObservationCacheMissReason.CACHE_DISABLED -> "CACHE_DISABLED"
        JvmObservationCacheMissReason.PROVIDER_IDENTITY_UNAVAILABLE -> "PROVIDER_IDENTITY_UNAVAILABLE"
        JvmObservationCacheMissReason.PROVIDER_IDENTITY_DRIFTED -> "PROVIDER_IDENTITY_DRIFTED"
    }
}
