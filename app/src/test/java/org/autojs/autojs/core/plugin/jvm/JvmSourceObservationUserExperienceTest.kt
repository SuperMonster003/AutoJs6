package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmObservationCacheMissReason
import org.autojs.plugin.jvmsource.api.JvmObservationCacheOutcome
import org.autojs.plugin.jvmsource.api.JvmObservationPhase
import org.autojs.plugin.jvmsource.api.JvmObservationProcess
import org.autojs.plugin.jvmsource.api.JvmObservationStartProfile
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourceResourceObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JvmSourceObservationUserExperienceTest {

    @Test
    fun formatsFixedPathFreeHostSummaryAndAggregatesResourcePeaks() {
        val observation = JvmSourceObservation(
            requestId = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT) { it.toByte() }),
            sessionElapsedMillis = 1_209L,
            workerStartupDurationMillis = 283L,
            compilerStartProfile = JvmObservationStartProfile.WARM,
            workerStartProfile = JvmObservationStartProfile.COLD,
            compileDurationMillis = 224L,
            d8DurationMillis = 480L,
            loadDurationMillis = 5L,
            runDurationMillis = 2L,
            terminationDurationMillis = 18L,
            cacheOutcome = JvmObservationCacheOutcome.MISS,
            cacheMissReason = JvmObservationCacheMissReason.CACHE_DISABLED,
            resources = listOf(
                JvmSourceResourceObservation(
                    JvmObservationProcess.COMPILER,
                    JvmObservationPhase.COMPILE,
                    rssBytes = 64L,
                    openFileDescriptorCount = 10,
                    temporaryStorageBytes = 128L,
                    outputBytes = 0L,
                ),
                JvmSourceResourceObservation(
                    JvmObservationProcess.WORKER,
                    JvmObservationPhase.RUN,
                    rssBytes = 96L,
                    openFileDescriptorCount = 8,
                    temporaryStorageBytes = 32L,
                    outputBytes = 16L,
                ),
            ),
        )

        val projected = JvmSourcePublicObservationPolicy.project(observation)
        val formatted = JvmSourcePublicObservationFormatter.format(projected)

        assertEquals(96L, projected.peakRssBytes)
        assertEquals(10, projected.maximumOpenFileDescriptorCount)
        assertEquals(
            "JVM observation: total=1209 ms, worker-start=283 ms, compile=224 ms, " +
                "d8=480 ms, load=5 ms, run=2 ms, cleanup=18 ms, cache=MISS/CACHE_DISABLED, " +
                "starts=compiler:WARM worker:COLD, samples=2, peak-rss=96 B, max-fd=10, " +
                "max-temp=128 B, max-output=16 B",
            formatted,
        )
        assertFalse(formatted.contains(observation.requestId.toString()))
        assertFalse(formatted.contains("Main.java"))
        assertFalse(formatted.contains("classes.dex"))
    }

    @Test
    fun formatsUnavailableOptionalMeasurementsWithoutSentinels() {
        val observation = JvmSourceObservation(
            requestId = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT) { 7 }),
            sessionElapsedMillis = 0L,
            workerStartupDurationMillis = null,
            compilerStartProfile = JvmObservationStartProfile.COLD,
            workerStartProfile = null,
            compileDurationMillis = null,
            d8DurationMillis = null,
            loadDurationMillis = null,
            runDurationMillis = null,
            terminationDurationMillis = null,
            cacheOutcome = JvmObservationCacheOutcome.NOT_EVALUATED,
            cacheMissReason = null,
            resources = emptyList(),
        )

        assertEquals(
            "JVM observation: total=0 ms, worker-start=n/a, compile=n/a, d8=n/a, load=n/a, " +
                "run=n/a, cleanup=n/a, cache=NOT_EVALUATED, starts=compiler:COLD worker:n/a, " +
                "samples=0, peak-rss=n/a, max-fd=n/a, max-temp=n/a, max-output=n/a",
            JvmSourcePublicObservationFormatter.format(
                JvmSourcePublicObservationPolicy.project(observation),
            ),
        )
    }
}
