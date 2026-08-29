package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayDeque

class PluginCenterFrameChunkedListSubmitterTest {

    @Test
    fun coldViewportGrowsOneItemPerFrameBeforePublishingTheRemainder() {
        val fixture = Fixture<Int>()

        fixture.submitter.submit((1..12).toList())
        fixture.runAllFrames()

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 12), fixture.submittedSizes)
    }

    @Test
    fun latestTargetReplacesAnOlderTargetWhileFramesArePending() {
        val fixture = Fixture<Int>()

        fixture.submitter.submit((1..10).toList())
        fixture.submitter.submit((21..32).toList())
        fixture.runAllFrames()

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 12), fixture.submittedSizes)
        assertEquals((21..32).toList(), fixture.submissions.last())
    }

    @Test
    fun shortListRemainsGuardedForLaterStructuralGrowth() {
        val fixture = Fixture<Int>()

        fixture.submitter.submit(listOf(1, 2, 3))
        fixture.runAllFrames()
        assertEquals(listOf(1, 2, 3), fixture.submittedSizes)

        fixture.submitter.submit((1..10).toList())
        fixture.runAllFrames()

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 10), fixture.submittedSizes)
    }

    @Test
    fun sameSizeRefreshUsesOneDiffAfterShortListCatchesUp() {
        val fixture = Fixture<String>()

        fixture.submitter.submit(listOf("a", "b", "c"))
        fixture.runAllFrames()
        fixture.submitter.submit(listOf("A", "B", "C"))
        fixture.runAllFrames()

        assertEquals(listOf(1, 2, 3, 3), fixture.submittedSizes)
        assertEquals(listOf("A", "B", "C"), fixture.submissions.last())
    }

    @Test
    fun cancellationRemovesPendingFrameAndIgnoresLaterSnapshots() {
        val fixture = Fixture<Int>()

        fixture.submitter.submit((1..10).toList())
        fixture.submitter.cancel()
        fixture.submitter.submit((21..30).toList())
        fixture.runAllFrames()

        assertEquals(listOf(1), fixture.submittedSizes)
    }

    private class Fixture<T> {
        val submissions = mutableListOf<List<T>>()
        val submittedSizes get() = submissions.map(List<T>::size)
        private val frames = ArrayDeque<Runnable>()

        val submitter = PluginCenterFrameChunkedListSubmitter<T>(
            submitList = { items, onCommitted ->
                submissions += items
                onCommitted?.invoke()
            },
            postNextFrame = frames::addLast,
            removeFrameCallback = frames::remove,
        )

        fun runAllFrames() {
            while (frames.isNotEmpty()) {
                frames.removeFirst().run()
            }
        }
    }
}
