package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream

class DexCompilerClasspathDecisionRunnerTest {

    @Test
    fun successfulPublishedDecisionReportsItsRouteAfterAdoption() {
        val retained = RecordingRetainedInput()
        val decision = DexCompilerRuntimeDecision.Published(
            RecordingArtifact(),
            retained,
            cacheHit = true,
        )
        var publishedCallbacks = 0
        var localStarts = 0

        val result = DexCompilerClasspathDecisionRunner.consume(
            decision = decision,
            loadPublished = { "cached" },
            loadLocal = {
                localStarts += 1
                error("Local D8 must not run")
            },
            checkInterrupted = {},
            onPublishedLoaded = { observed ->
                assertSame(decision, observed)
                publishedCallbacks += 1
            },
            cancelled = { error("Cancellation must not run") },
        )

        assertEquals("cached", result)
        assertEquals(1, publishedCallbacks)
        assertEquals(0, localStarts)
        assertEquals(1, retained.closeCount)
    }

    @Test
    fun localAndFailedPublishedDecisionsUseTheirSameRetainedInputAndCloseOnce() {
        listOf<(RecordingRetainedInput) -> DexCompilerRuntimeDecision>(
            { retained ->
                DexCompilerRuntimeDecision.UseLocal(
                    DexCompilerLocalReason.OPT_IN_DISABLED,
                    retained,
                )
            },
            { retained ->
                DexCompilerRuntimeDecision.Published(
                    RecordingArtifact(),
                    retained,
                    cacheHit = false,
                )
            },
        ).forEach { decisionFactory ->
            val retained = RecordingRetainedInput()
            val decision = decisionFactory(retained)
            val result = DexCompilerClasspathDecisionRunner.consume(
                decision = decision,
                loadPublished = { throw IOException("adoption failed") },
                loadLocal = { input ->
                    assertSame(retained, input)
                    "local"
                },
                checkInterrupted = {},
                cancelled = { error("Cancellation must not run") },
            )

            assertEquals("local", result)
            assertEquals(1, retained.closeCount)
            if (decision is DexCompilerRuntimeDecision.Published) {
                assertEquals(1, (decision.artifact as RecordingArtifact).invalidateCount)
            }
        }
    }

    @Test
    fun localRouteCallbacksRunOnlyAfterSuccessfulAdoption() {
        val retained = RecordingRetainedInput()
        val decision = DexCompilerRuntimeDecision.UseLocal(
            DexCompilerLocalReason.OPT_IN_DISABLED,
            retained,
        )
        val events = mutableListOf<String>()

        val result = DexCompilerClasspathDecisionRunner.consume(
            decision = decision,
            loadPublished = { error("Published loader must not run") },
            loadLocal = {
                events += "load"
                "local"
            },
            checkInterrupted = {},
            onUseLocal = { events += "selected" },
            onLocalLoaded = { observed ->
                assertSame(decision, observed)
                events += "loaded"
            },
            cancelled = { error("Cancellation must not run") },
        )

        assertEquals("local", result)
        assertEquals(listOf("selected", "load", "loaded"), events)
    }

    @Test
    fun failedLocalAdoptionDoesNotReportACompletedRoute() {
        val retained = RecordingRetainedInput()
        val decision = DexCompilerRuntimeDecision.UseLocal(
            DexCompilerLocalReason.OPT_IN_DISABLED,
            retained,
        )
        var completedRoutes = 0

        assertThrows(IOException::class.java) {
            DexCompilerClasspathDecisionRunner.consume(
                decision = decision,
                loadPublished = { error("Published loader must not run") },
                loadLocal = { throw IOException("local adoption failed") },
                checkInterrupted = {},
                onLocalLoaded = { completedRoutes += 1 },
                cancelled = { error("Cancellation must not run") },
            )
        }

        assertEquals(0, completedRoutes)
    }

    @Test
    fun cancellationIsTerminalAndNeverStartsLocalD8() {
        var localStarts = 0
        var cancellations = 0

        val result = DexCompilerClasspathDecisionRunner.consume(
            decision = DexCompilerRuntimeDecision.Cancelled,
            loadPublished = { error("Published loader must not run") },
            loadLocal = {
                localStarts += 1
                error("Local D8 must not run")
            },
            checkInterrupted = {},
            cancelled = {
                cancellations += 1
                "cancelled"
            },
        )

        assertEquals("cancelled", result)
        assertEquals(0, localStarts)
        assertEquals(1, cancellations)
    }

    @Test
    fun interruptedCloseIsReassertedAndBecomesTheTerminalOverOrdinaryFailure() {
        Thread.interrupted()
        val closeInterrupted = InterruptedException("close interrupted")
        val ordinaryFailure = IOException("local D8 failed")
        val retained = RecordingRetainedInput(closeFailure = closeInterrupted)
        val decision = DexCompilerRuntimeDecision.UseLocal(
            DexCompilerLocalReason.OPT_IN_DISABLED,
            retained,
        )

        try {
            val observed = assertThrows(InterruptedException::class.java) {
                DexCompilerClasspathDecisionRunner.consume(
                    decision = decision,
                    loadPublished = { error("Published loader must not run") },
                    loadLocal = { throw ordinaryFailure },
                    checkInterrupted = {},
                    cancelled = { error("Cancellation must not run") },
                )
            }
            assertSame(closeInterrupted, observed)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(observed.suppressed.contains(ordinaryFailure))
        } finally {
            Thread.interrupted()
        }
    }

    private class RecordingRetainedInput(
        private val closeFailure: Throwable? = null,
    ) : DexCompilerRetainedInput {
        var closeCount = 0

        override val sizeBytes: Long = 1L
        override val sha256: String = "00".repeat(32)
        override val inputSetSha256: String = "11".repeat(32)

        override fun resolve(): File? = null

        override fun close() {
            closeCount += 1
            closeFailure?.let { throw it }
        }
    }

    private class RecordingArtifact : DexCompilerVerifiedArtifact {
        var invalidateCount = 0

        override val cacheKeySha256: String = "22".repeat(32)
        override val outputSha256: String = "33".repeat(32)
        override val sizeBytes: Long = 1L

        override fun openRead(): InputStream? = null

        override fun invalidate(): Boolean {
            invalidateCount += 1
            return true
        }
    }

}
