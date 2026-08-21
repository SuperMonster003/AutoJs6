package org.autojs.plugin.yolo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloSessionLifecyclePolicyTest {
    @Test
    fun sessionAllowsOneActiveDetectionAndNeverQueuesTheBusyRequest() {
        val policy = YoloSessionLifecyclePolicy()
        val first = YoloFixtures.detectRequest()
        val busy = YoloFixtures.detectRequest(YoloFixtures.OTHER_DETECT_REQUEST_ID, sequence = 2L)

        assertEquals(YoloSessionLifecyclePolicy.BeginDisposition.ACCEPTED, policy.beginDetect(first))
        assertEquals(YoloSessionLifecyclePolicy.BeginDisposition.BUSY, policy.beginDetect(busy))
        assertEquals(busy.sequence, policy.lastSeenSequence)
        assertEquals(0, policy.queuedDetectionCount)
        assertEquals(first.requestId, policy.activeRequestId)

        val firstResult = YoloFixtures.result(first)
        assertTrue(policy.onDetected(firstResult))
        assertFalse(policy.onDetected(firstResult))
        assertEquals(YoloSessionLifecyclePolicy.Terminal.DETECTED, policy.lastTerminal)
        assertEquals(
            YoloSessionLifecyclePolicy.BeginDisposition.REPLAYED_OR_OUT_OF_ORDER,
            policy.beginDetect(busy),
        )
        assertEquals(busy.sequence, policy.lastSeenSequence)
        assertEquals(
            YoloSessionLifecyclePolicy.BeginDisposition.ACCEPTED,
            policy.beginDetect(busy.copy(sequence = 3L)),
        )
    }

    @Test
    fun cancellationAndTerminalCallbacksAreIdempotent() {
        val policy = YoloSessionLifecyclePolicy()
        val request = YoloFixtures.detectRequest()
        policy.beginDetect(request)
        val cancel = YoloCancelRequest(
            request.requestId,
            request.sequence,
            YoloCancellationReason.REQUESTED,
        )

        assertTrue(policy.requestCancel(cancel))
        assertFalse(policy.requestCancel(cancel))
        assertFalse(policy.onDetected(YoloFixtures.result(request)))
        val cancellation = YoloCancellation(
            request.requestId,
            request.sequence,
            YoloCancellationReason.REQUESTED,
            4L,
        )
        assertTrue(policy.onCancelled(cancellation))
        assertFalse(policy.onCancelled(cancellation))
        assertEquals(YoloSessionLifecyclePolicy.Terminal.CANCELLED, policy.lastTerminal)
    }

    @Test
    fun closeIsIdempotentAndReturnsTheActiveRequestForOneCancellation() {
        val policy = YoloSessionLifecyclePolicy()
        val request = YoloFixtures.detectRequest()
        policy.beginDetect(request)

        val firstClose = policy.close()
        assertTrue(firstClose.changed)
        assertEquals(request.requestId, firstClose.cancelledRequestId)
        assertEquals(request.sequence, firstClose.cancelledSequence)
        assertEquals(YoloSessionLifecyclePolicy.State.CLOSED, policy.state)
        assertEquals(
            YoloSessionLifecyclePolicy.BeginDisposition.CLOSED,
            policy.beginDetect(
                YoloFixtures.detectRequest(YoloFixtures.OTHER_DETECT_REQUEST_ID, sequence = 2L),
            ),
        )

        val repeatedClose = policy.close()
        assertFalse(repeatedClose.changed)
        assertNull(repeatedClose.cancelledRequestId)
        assertNull(repeatedClose.cancelledSequence)
    }

    @Test
    fun reusedRequestIdCannotLetALateOldTerminalCompleteANewerSequence() {
        val policy = YoloSessionLifecyclePolicy()
        val oldRequest = YoloFixtures.detectRequest(sequence = 10L)
        assertEquals(
            YoloSessionLifecyclePolicy.BeginDisposition.ACCEPTED,
            policy.beginDetect(oldRequest),
        )
        assertTrue(policy.onDetected(YoloFixtures.result(oldRequest)))

        val newerRequestWithSameId = oldRequest.copy(sequence = 11L)
        assertEquals(
            YoloSessionLifecyclePolicy.BeginDisposition.ACCEPTED,
            policy.beginDetect(newerRequestWithSameId),
        )
        assertFalse(policy.onDetected(YoloFixtures.result(oldRequest)))
        assertEquals(newerRequestWithSameId.requestId, policy.activeRequestId)
        assertTrue(policy.onDetected(YoloFixtures.result(newerRequestWithSameId)))
    }
}
