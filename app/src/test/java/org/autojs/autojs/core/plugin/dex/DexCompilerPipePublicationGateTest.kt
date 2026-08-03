package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DexCompilerPipePublicationGateTest {

    private val summary = DexCompilerStreamSummary(4, "ab".repeat(32))

    @Test
    fun providerThenDrainClaimsOnePublication() {
        val gate = DexCompilerPipePublicationGate()

        assertTrue(gate.acceptProviderCompletion(summary) === DexCompilerPipePublicationGate.Decision.Waiting)
        val decision = gate.acceptDrainSuccess(summary)

        assertEquals(DexCompilerPipePublicationGate.Decision.Publish(summary), decision)
        assertEquals(DexCompilerPipePublicationGate.Phase.PUBLISHING, gate.snapshot().phase)
        assertTrue(gate.abort() === DexCompilerPipePublicationGate.Decision.Ignored)
        gate.publicationSucceeded()
        assertEquals(DexCompilerPipePublicationGate.Phase.PUBLISHED, gate.snapshot().phase)
    }

    @Test
    fun drainThenProviderClaimsOnePublication() {
        val gate = DexCompilerPipePublicationGate()

        assertTrue(gate.acceptDrainSuccess(summary) === DexCompilerPipePublicationGate.Decision.Waiting)
        assertEquals(
            DexCompilerPipePublicationGate.Decision.Publish(summary),
            gate.acceptProviderCompletion(summary),
        )
    }

    @Test
    fun mismatchedSummaryClaimsRollbackInsteadOfPublication() {
        val gate = DexCompilerPipePublicationGate()
        val wrong = DexCompilerStreamSummary(summary.sizeBytes + 1, summary.sha256)

        gate.acceptDrainSuccess(summary)
        val decision = gate.acceptProviderCompletion(wrong)

        assertEquals(
            DexCompilerPipePublicationGate.Decision.Rollback(
                DexCompilerPipePublicationGate.Rejection.SUMMARY_MISMATCH,
            ),
            decision,
        )
        assertTrue(gate.rollbackFinished())
        assertEquals(DexCompilerPipePublicationGate.Phase.ROLLED_BACK, gate.snapshot().phase)
    }

    @Test
    fun drainFailureAndHostAbortEachClaimRollbackOnce() {
        listOf<(DexCompilerPipePublicationGate) -> DexCompilerPipePublicationGate.Decision>(
            { it.acceptDrainFailure() },
            { it.abort() },
        ).forEach { fail ->
            val gate = DexCompilerPipePublicationGate()
            assertTrue(fail(gate) is DexCompilerPipePublicationGate.Decision.Rollback)
            assertTrue(fail(gate) === DexCompilerPipePublicationGate.Decision.Ignored)
            assertTrue(gate.rollbackFinished())
            assertFalse(gate.rollbackFinished())
        }
    }

    @Test
    fun duplicatePreResolutionTerminalIsRejected() {
        val providerDuplicate = DexCompilerPipePublicationGate()
        providerDuplicate.acceptProviderCompletion(summary)
        assertEquals(
            DexCompilerPipePublicationGate.Decision.Rollback(
                DexCompilerPipePublicationGate.Rejection.DUPLICATE_PROVIDER_COMPLETION,
            ),
            providerDuplicate.acceptProviderCompletion(summary),
        )

        val drainDuplicate = DexCompilerPipePublicationGate()
        drainDuplicate.acceptDrainSuccess(summary)
        assertEquals(
            DexCompilerPipePublicationGate.Decision.Rollback(
                DexCompilerPipePublicationGate.Rejection.DUPLICATE_DRAIN_TERMINAL,
            ),
            drainDuplicate.acceptDrainSuccess(summary),
        )
    }

    @Test
    fun publicationFailureHandsOffToOneRollback() {
        val gate = DexCompilerPipePublicationGate()
        gate.acceptProviderCompletion(summary)
        gate.acceptDrainSuccess(summary)

        assertEquals(
            DexCompilerPipePublicationGate.Decision.Rollback(
                DexCompilerPipePublicationGate.Rejection.PUBLICATION_FAILED,
            ),
            gate.publicationFailed(),
        )
        assertTrue(gate.publicationFailed() === DexCompilerPipePublicationGate.Decision.Ignored)
        assertTrue(gate.rollbackFinished())
    }

    @Test
    fun concurrentMatchingSignalsHaveExactlyOnePublisher() {
        val gate = DexCompilerPipePublicationGate()
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = listOf(
            executor.submit<DexCompilerPipePublicationGate.Decision> {
                ready.countDown()
                release.await()
                gate.acceptProviderCompletion(summary)
            },
            executor.submit<DexCompilerPipePublicationGate.Decision> {
                ready.countDown()
                release.await()
                gate.acceptDrainSuccess(summary)
            },
        )
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        release.countDown()
        val decisions = futures.map { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, decisions.count { it is DexCompilerPipePublicationGate.Decision.Publish })
        assertEquals(1, decisions.count { it === DexCompilerPipePublicationGate.Decision.Waiting })
    }
}
