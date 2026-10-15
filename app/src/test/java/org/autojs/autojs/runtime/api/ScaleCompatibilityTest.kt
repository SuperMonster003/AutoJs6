package org.autojs.autojs.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ScaleCompatibilityTest {

    @Test
    fun rejectedSecondAssignmentDoesNotMutateTheBase() {
        val scale = Scale()
        scale.baseX = 1080

        assertThrows(IllegalArgumentException::class.java) {
            scale.baseX = 1440
        }
        assertEquals(1080, scale.baseX)
    }

    @Test
    fun reusableBaseValidationRejectsNonPositiveValues() {
        assertEquals(1, Scale.ensureBase(1))
        assertThrows(IllegalArgumentException::class.java) { Scale.ensureBase(0) }
        assertThrows(IllegalArgumentException::class.java) { Scale.ensureBase(-1) }
    }

    @Test
    fun invalidBatchDoesNotMutateOrConsumeEitherBase() {
        for ((x, y) in listOf(0 to 1920, 1080 to 0, -1 to 1920, 1080 to -1)) {
            val scale = Scale()

            assertThrows(IllegalArgumentException::class.java) { scale.setBases(x, y) }
            assertEquals(720, scale.baseX)
            assertEquals(1280, scale.baseY)
            scale.ensureBasesConsistent()

            scale.setBases(1440, 2560)
            assertEquals(1440, scale.baseX)
            assertEquals(2560, scale.baseY)
        }
    }

    @Test
    fun rejectedBatchWithTheDefaultXValueDoesNotConsumeItsAssignment() {
        val scale = Scale()

        assertThrows(IllegalArgumentException::class.java) { scale.setBases(720, 0) }
        // Comparing the numbers alone would miss a consumed baseXState.
        assertEquals(720, scale.baseX)
        assertEquals(1280, scale.baseY)
        scale.ensureBasesConsistent()
        scale.baseX = 1080
        scale.baseY = 1920
        scale.ensureBasesConsistent()
    }

    @Test
    fun rejectedBatchAfterYWasAssignedLeavesXAvailable() {
        val scale = Scale()
        scale.baseY = 1920

        assertThrows(IllegalArgumentException::class.java) { scale.setBases(1080, 2560) }
        assertEquals(720, scale.baseX)
        assertEquals(1920, scale.baseY)
        scale.baseX = 1440
        scale.ensureBasesConsistent()
    }

    @Test
    fun rejectedBatchAfterXWasAssignedLeavesYAvailable() {
        val scale = Scale()
        scale.baseX = 1080

        assertThrows(IllegalArgumentException::class.java) { scale.setBases(1440, 2560) }
        assertEquals(1080, scale.baseX)
        assertEquals(1280, scale.baseY)
        scale.baseY = 1920
        scale.ensureBasesConsistent()
    }

    @Test
    fun validBatchCommitsAndConsumesBothAssignments() {
        val scale = Scale()
        scale.setBases(1080, 1920)

        assertEquals(1080, scale.baseX)
        assertEquals(1920, scale.baseY)
        scale.ensureBasesConsistent()
        assertThrows(IllegalArgumentException::class.java) { scale.baseX = 1440 }
        assertThrows(IllegalArgumentException::class.java) { scale.baseY = 2560 }
        assertThrows(IllegalArgumentException::class.java) { scale.setBases(1440, 2560) }
        assertEquals(1080, scale.baseX)
        assertEquals(1920, scale.baseY)
    }

    @Test
    fun batchCommitSerializesBeforeContendingSingleAxisAssignment() {
        val scale = Scale()
        val failure = runContendedAssignment(scale, { scale.baseY = 2560 }) {
            scale.setBases(1080, 1920)
        }

        assertTrue(failure is IllegalArgumentException)
        assertEquals(1080, scale.baseX)
        assertEquals(1920, scale.baseY)
        scale.ensureBasesConsistent()
    }

    @Test
    fun singleAxisCommitSerializesBeforeContendingBatchWithoutConsumingX() {
        val scale = Scale()
        val failure = runContendedAssignment(scale, { scale.setBases(1080, 1920) }) {
            scale.baseY = 2560
        }

        assertTrue(failure is IllegalArgumentException)
        assertEquals(720, scale.baseX)
        assertEquals(2560, scale.baseY)
        scale.baseX = 1440
        scale.ensureBasesConsistent()
    }

    @Test
    fun aSnapshotRetainsItsPairAfterTheBasesChange() {
        val scale = Scale()
        val before = scale.snapshotBases()

        scale.setBases(1080, 1920)

        assertEquals(720 to 1280, before)
        assertEquals(1080 to 1920, scale.snapshotBases())
    }

    @Test
    fun defaultAspectRatioRequiresConsistentAssignmentState() {
        val scale = Scale()
        assertEquals(9.0 / 16.0, scale.defaultAspectRatio(), 0.0)

        scale.baseX = 1280
        assertThrows(IllegalArgumentException::class.java) { scale.defaultAspectRatio() }
        // Reading a snapshot remains valid while only one axis is configured.
        assertEquals(1280 to 1280, scale.snapshotBases())

        scale.baseY = 720
        assertEquals(16.0 / 9.0, scale.defaultAspectRatio(), 0.0)
    }

    @Test
    fun contendingSnapshotReaderObservesTheWholeCommittedPair() {
        val scale = Scale()
        val observed = AtomicReference<Pair<Int, Int>>()
        val failure = runContendedAssignment(scale, { observed.set(scale.snapshotBases()) }) {
            scale.setBases(1280, 720)
        }

        assertNull(failure)
        assertEquals(1280 to 720, observed.get())
    }

    @Test
    fun contendingRatioReaderWaitsForConsistentValuesAndState() {
        val scale = Scale()
        scale.baseY = 720
        val observed = AtomicReference<Double>()
        val failure = runContendedAssignment(scale, { observed.set(scale.defaultAspectRatio()) }) {
            scale.baseX = 1280
        }

        assertNull(failure)
        assertEquals(16.0 / 9.0, observed.get(), 0.0)
    }

    /** Hold the shared monitor to choose the winner without timing sleeps or stress loops. */
    private fun runContendedAssignment(scale: Scale, contender: () -> Unit, first: () -> Unit): Throwable? {
        val attempting = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                attempting.countDown()
                contender()
            } catch (t: Throwable) {
                failure.set(t)
            } finally {
                finished.countDown()
            }
        }.apply { isDaemon = true }
        synchronized(scale) {
            worker.start()
            assertTrue("Contender did not start", attempting.await(5, TimeUnit.SECONDS))
            first()
        }
        assertTrue("Contender did not finish", finished.await(5, TimeUnit.SECONDS))
        worker.join(5_000)
        assertTrue("Contender is still running", !worker.isAlive)
        return failure.get()
    }

}
