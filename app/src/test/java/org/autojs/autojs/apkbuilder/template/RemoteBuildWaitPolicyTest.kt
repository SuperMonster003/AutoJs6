package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteBuildWaitPolicyTest {

    @Test
    fun normalNineMinuteBuildWithoutProgressRemainsAllowed() {
        assertEquals(
            RemoteBuildWaitPolicy.Decision.CONTINUE,
            RemoteBuildWaitPolicy.evaluate(
                nowElapsedMillis = minutes(9),
                startedAtElapsedMillis = 0L,
                lastActivityElapsedMillis = 0L,
                binderAlive = true,
            ),
        )
    }

    @Test
    fun noProgressTimeoutTriggersAtBoundaryButNotOneMillisecondBefore() {
        val timeout = RemoteBuildWaitPolicy.NO_PROGRESS_TIMEOUT_MILLIS
        assertEquals(
            RemoteBuildWaitPolicy.Decision.CONTINUE,
            RemoteBuildWaitPolicy.evaluate(timeout - 1L, 0L, 0L, true),
        )
        assertEquals(
            RemoteBuildWaitPolicy.Decision.NO_PROGRESS_TIMEOUT,
            RemoteBuildWaitPolicy.evaluate(timeout, 0L, 0L, true),
        )
    }

    @Test
    fun recentProgressExtendsIdleWindowWithoutExtendingTotalDeadline() {
        assertEquals(
            RemoteBuildWaitPolicy.Decision.CONTINUE,
            RemoteBuildWaitPolicy.evaluate(
                nowElapsedMillis = minutes(20),
                startedAtElapsedMillis = 0L,
                lastActivityElapsedMillis = minutes(11),
                binderAlive = true,
            ),
        )
        assertEquals(
            RemoteBuildWaitPolicy.Decision.TOTAL_TIMEOUT,
            RemoteBuildWaitPolicy.evaluate(
                nowElapsedMillis = RemoteBuildWaitPolicy.TOTAL_TIMEOUT_MILLIS,
                startedAtElapsedMillis = 0L,
                lastActivityElapsedMillis = RemoteBuildWaitPolicy.TOTAL_TIMEOUT_MILLIS - 1L,
                binderAlive = true,
            ),
        )
    }

    @Test
    fun binderLossTakesPrecedenceOverTimeouts() {
        assertEquals(
            RemoteBuildWaitPolicy.Decision.BINDER_LOST,
            RemoteBuildWaitPolicy.evaluate(
                nowElapsedMillis = RemoteBuildWaitPolicy.TOTAL_TIMEOUT_MILLIS,
                startedAtElapsedMillis = 0L,
                lastActivityElapsedMillis = 0L,
                binderAlive = false,
            ),
        )
    }

    private fun minutes(value: Long): Long = value * 60L * 1_000L
}
