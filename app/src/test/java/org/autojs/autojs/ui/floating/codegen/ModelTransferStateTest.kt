package org.autojs.autojs.ui.floating.codegen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTransferStateTest {
    @Test
    fun buttonBackDismissAndOwnerDisposalOnlyCancelOnce() {
        val state = ModelTransferState()
        assertTrue(state.close())
        assertFalse(state.close())
        assertFalse(state.close())
        assertFalse(state.acceptsProgress)
    }

    @Test
    fun completedTransferNeverCancelsOnDoneOrOwnerDisposal() {
        val state = ModelTransferState()
        assertTrue(state.succeed())
        assertFalse(state.acceptsProgress)
        assertFalse(state.close())
        assertFalse(state.close())
    }

    @Test
    fun failedTransferNeverCancelsOnClose() {
        val state = ModelTransferState()
        assertTrue(state.fail())
        assertFalse(state.acceptsProgress)
        assertFalse(state.close())
    }

    @Test
    fun cancelledDialogCannotBeReopenedByLateCompletionOrFailure() {
        val state = ModelTransferState()
        assertTrue(state.close())
        assertFalse(state.succeed())
        assertFalse(state.fail())
        assertFalse(state.acceptsProgress)
    }

    @Test
    fun firstTerminalOutcomeWins() {
        val succeeded = ModelTransferState()
        assertTrue(succeeded.succeed())
        assertFalse(succeeded.fail())
        assertFalse(succeeded.succeed())
        val failed = ModelTransferState()
        assertTrue(failed.fail())
        assertFalse(failed.succeed())
        assertFalse(failed.fail())
    }
}
