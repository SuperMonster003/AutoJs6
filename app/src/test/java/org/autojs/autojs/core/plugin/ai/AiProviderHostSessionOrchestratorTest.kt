package org.autojs.autojs.core.plugin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiProviderHostSessionOrchestratorTest {

    @Test
    fun happyPathRequiresDispatchStartAndAcceptsOneCompletion() {
        val session = AiProviderHostSessionOrchestrator()

        assertFalse(session.acceptStarted())
        assertFalse(session.acceptDataEvent())
        assertTrue(session.beginDispatch())
        assertFalse(session.beginDispatch())
        assertNotNull(session.attachRemote())
        assertNull(session.attachRemote())
        assertTrue(session.acceptStarted())
        assertTrue(session.acceptDataEvent())
        assertTrue(session.acceptCompleted())
        assertFalse(session.acceptCompleted())
        assertFalse(session.acceptDataEvent())
        assertEquals(AiProviderHostSessionOrchestrator.CallbackState.COMPLETED, session.snapshot().callbackState)
    }

    @Test
    fun cancellationBeforeRemoteAttachmentIsClaimedExactlyOnce() {
        val session = AiProviderHostSessionOrchestrator()
        assertTrue(session.beginDispatch())

        assertEquals(
            AiProviderHostSessionOrchestrator.CancellationDecision.DEFER_UNTIL_ATTACHED,
            session.requestCancel(),
        )
        assertEquals(AiProviderHostSessionOrchestrator.CancellationDecision.IGNORED, session.requestCancel())
        assertFalse(session.acceptStarted())
        assertFalse(session.acceptCompleted())

        val attach = session.attachRemote()
        assertNotNull(attach)
        assertTrue(attach!!.forwardCancellation)
        assertFalse(attach.forwardClose)
        assertNull(session.attachRemote())
        assertTrue(session.acceptCancelled())
        assertFalse(session.acceptFailed())
        assertTrue(session.snapshot().cancellationForwarded)
    }

    @Test
    fun cancellationBeforeDispatchCompletesLocallyWithoutWaitingForARemote() {
        val session = AiProviderHostSessionOrchestrator()

        assertEquals(
            AiProviderHostSessionOrchestrator.CancellationDecision.COMPLETE_LOCALLY,
            session.requestCancel(),
        )
        assertFalse(session.beginDispatch())
        assertNull(session.attachRemote())
        assertTrue(session.snapshot().isTerminal)
        assertEquals(
            AiProviderHostSessionOrchestrator.CallbackState.CANCELLED,
            session.snapshot().callbackState,
        )
    }

    @Test
    fun cancellationAfterAttachmentIsForwardedImmediately() {
        val session = startedSession()

        assertEquals(AiProviderHostSessionOrchestrator.CancellationDecision.FORWARD_NOW, session.requestCancel())
        assertFalse(session.acceptDataEvent())
        assertFalse(session.acceptCompleted())
        assertTrue(session.acceptFailed())
        assertFalse(session.acceptCancelled())
    }

    @Test
    fun completionAndCancellationHaveDeterministicFirstWinnerSemantics() {
        val completionFirst = startedSession()
        assertTrue(completionFirst.acceptCompleted())
        assertEquals(
            AiProviderHostSessionOrchestrator.CancellationDecision.IGNORED,
            completionFirst.requestCancel(),
        )

        val cancellationFirst = startedSession()
        assertEquals(
            AiProviderHostSessionOrchestrator.CancellationDecision.FORWARD_NOW,
            cancellationFirst.requestCancel(),
        )
        assertFalse(cancellationFirst.acceptCompleted())
        assertTrue(cancellationFirst.acceptCancelled())
    }

    @Test
    fun closeBeforeAttachmentIsForwardedWhenTheHandleArrives() {
        val session = AiProviderHostSessionOrchestrator()
        assertTrue(session.beginDispatch())
        assertEquals(AiProviderHostSessionOrchestrator.CloseDecision.DEFER_UNTIL_ATTACHED, session.close())
        assertEquals(AiProviderHostSessionOrchestrator.CloseDecision.IGNORED, session.close())
        assertFalse(session.acceptStarted())
        assertFalse(session.acceptFailed())

        val attach = session.attachRemote()
        assertNotNull(attach)
        assertFalse(attach!!.forwardCancellation)
        assertTrue(attach.forwardClose)
        assertTrue(session.snapshot().closeForwarded)
    }

    @Test
    fun closeBeforeDispatchCompletesLocallyWithoutWaitingForARemote() {
        val session = AiProviderHostSessionOrchestrator()

        assertEquals(AiProviderHostSessionOrchestrator.CloseDecision.CLOSE_LOCALLY, session.close())
        assertFalse(session.beginDispatch())
        assertNull(session.attachRemote())
        assertFalse(session.snapshot().closeForwarded)
    }

    @Test
    fun cancellationAndCloseDeferredTogetherPreserveControlOrder() {
        val session = AiProviderHostSessionOrchestrator()
        assertTrue(session.beginDispatch())
        assertEquals(
            AiProviderHostSessionOrchestrator.CancellationDecision.DEFER_UNTIL_ATTACHED,
            session.requestCancel(),
        )
        assertEquals(AiProviderHostSessionOrchestrator.CloseDecision.DEFER_UNTIL_ATTACHED, session.close())

        val attach = session.attachRemote()
        assertNotNull(attach)
        assertTrue(attach!!.forwardCancellation)
        assertTrue(attach.forwardClose)
        assertFalse(session.acceptCancelled())
    }

    @Test
    fun closeAfterAttachmentIsForwardedOnlyOnceEvenAfterTerminal() {
        val session = startedSession()
        assertTrue(session.acceptCompleted())

        assertEquals(AiProviderHostSessionOrchestrator.CloseDecision.FORWARD_NOW, session.close())
        assertEquals(AiProviderHostSessionOrchestrator.CloseDecision.IGNORED, session.close())
        assertTrue(session.snapshot().closeForwarded)
    }

    @Test
    fun hostileTerminalBeforeDispatchIsRejected() {
        val session = AiProviderHostSessionOrchestrator()

        assertFalse(session.acceptFailed())
        assertFalse(session.acceptCancelled())
        assertTrue(session.beginDispatch())
        assertTrue(session.acceptFailed())
        assertFalse(session.acceptCancelled())
    }

    @Test
    fun onlyOneConcurrentTerminalCallbackWins() {
        val session = startedSession()
        val ready = CountDownLatch(18)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(18)
        val results = (0 until 18).map { index ->
            executor.submit<Boolean> {
                ready.countDown()
                release.await()
                when (index % 3) {
                    0 -> session.acceptCompleted()
                    1 -> session.acceptFailed()
                    else -> session.acceptCancelled()
                }
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        release.countDown()
        assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue(session.snapshot().isTerminal)
    }

    @Test
    fun concurrentCancellationAndCompletionNeverBothWin() {
        repeat(100) {
            val session = startedSession()
            val ready = CountDownLatch(2)
            val release = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val completion = executor.submit<Boolean> {
                ready.countDown()
                release.await()
                session.acceptCompleted()
            }
            val cancellation = executor.submit<AiProviderHostSessionOrchestrator.CancellationDecision> {
                ready.countDown()
                release.await()
                session.requestCancel()
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            release.countDown()
            val completionWon = completion.get(5, TimeUnit.SECONDS)
            val cancellationWon = cancellation.get(5, TimeUnit.SECONDS) !=
                AiProviderHostSessionOrchestrator.CancellationDecision.IGNORED
            assertTrue(completionWon xor cancellationWon)
            if (cancellationWon) assertTrue(session.acceptCancelled())
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun startedSession(): AiProviderHostSessionOrchestrator =
        AiProviderHostSessionOrchestrator().apply {
            assertTrue(beginDispatch())
            assertNotNull(attachRemote())
            assertTrue(acceptStarted())
        }
}
