package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceSessionLifecycleTest {
    @Test
    fun callbacksRequireStartedAndOnlyOneTerminalWins() {
        val lifecycle = JvmSourceSessionLifecycle(16)
        assertFalse(lifecycle.acceptCompleted())
        assertFalse(lifecycle.acceptFailed())
        assertFalse(lifecycle.acceptCancelled())
        assertTrue(lifecycle.acceptStarted())
        assertTrue(lifecycle.acceptDiagnostic(8))
        assertFalse(lifecycle.acceptDiagnostic(9))
        assertTrue(lifecycle.acceptCompleted())
        assertFalse(lifecycle.acceptFailed())
        assertFalse(lifecycle.acceptCancelled())
        assertEquals(JvmSourceSessionLifecycle.State.COMPLETED, lifecycle.snapshot().state)
    }

    @Test
    fun requestedCancellationRejectsSuccessAndFailureButAcceptsRemoteCancelled() {
        val lifecycle = JvmSourceSessionLifecycle(16)
        assertTrue(lifecycle.acceptStarted())
        assertTrue(lifecycle.requestCancel())
        assertFalse(lifecycle.requestCancel())
        assertTrue(lifecycle.acceptDiagnostic(1))
        assertFalse(lifecycle.acceptCompleted())
        assertFalse(lifecycle.acceptFailed())
        assertTrue(lifecycle.acceptCancelled())
        assertEquals(JvmSourceSessionLifecycle.State.CANCELLED, lifecycle.snapshot().state)
    }

    @Test
    fun cancellationBeforeStartedStillAcceptsTheMandatoryStartedCancelledPrefix() {
        val lifecycle = JvmSourceSessionLifecycle(16)
        assertTrue(lifecycle.requestCancel())
        assertTrue(lifecycle.acceptStarted())
        assertTrue(lifecycle.acceptDiagnostic(1))
        assertFalse(lifecycle.acceptCompleted())
        assertFalse(lifecycle.acceptFailed())
        assertTrue(lifecycle.acceptCancelled())
        assertEquals(JvmSourceSessionLifecycle.State.CANCELLED, lifecycle.snapshot().state)
    }

    @Test
    fun closeAndAbortAreIdempotentAndFailClosed() {
        val lifecycle = JvmSourceSessionLifecycle(16)
        assertTrue(lifecycle.acceptStarted())
        assertTrue(lifecycle.abort())
        assertFalse(lifecycle.abort())
        assertTrue(lifecycle.close())
        assertFalse(lifecycle.close())
        assertFalse(lifecycle.acceptCancelled())
    }
}
