package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test

class CallbackRegistryTest {
    private fun tree(id: Int) = UiTree(10, listOf(UiNode(10, ComposeUiComponents.BUTTON, callbacks = mapOf(ComposeUiEvents.CLICK to id))))
    @Test fun callbacksBecomeVisibleOnlyAtCommitAndOldGenerationsAreDropped() {
        val registry = CallbackRegistry()
        val first = registry.register { }
        assertNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, first))
        registry.commit(tree(first), 1)
        assertNotNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, first))
        assertNull(registry.resolve(1, 11, ComposeUiEvents.CLICK, first))
        assertNull(registry.resolve(1, 10, ComposeUiEvents.LONG_CLICK, first))
        val second = registry.register { }
        registry.commit(tree(second), 2)
        assertNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, first))
        assertNotNull(registry.resolve(2, 10, ComposeUiEvents.CLICK, second))
        assertEquals(1, registry.size)
    }
    @Test fun pendingCallbacksSurviveAckButCloseAndUnmountReleaseAllReferences() {
        val registry = CallbackRegistry()
        val first = registry.register { }; val next = registry.register { }
        registry.commit(tree(first), 1, tree(next))
        assertEquals(2, registry.size)
        registry.commit(tree(next), 2)
        assertEquals(1, registry.size)
        registry.commit(UiTree(10, listOf(UiNode(10, ComposeUiComponents.BUTTON))), 3)
        assertEquals(0, registry.size)
        registry.close()
        assertNull(registry.resolve(2, 10, ComposeUiEvents.CLICK, next))
        try { registry.register { }; fail() } catch (e: ComposeUiContractException) { assertEquals(ComposeUiErrorCodes.SESSION_CLOSED, e.code) }
    }

    @Test fun commandCallbacksKeepTheirCapturedGenerationAcrossUnrelatedTreeCommits() {
        val registry = CallbackRegistry()
        val callback = registry.register { }
        val command = registry.reserveCommand(callback)
        assertNull(registry.resolve(3, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.ACTION, callback))
        command.publishGeneration(3)
        registry.commit(UiTree(10, listOf(UiNode(10, ComposeUiComponents.BUTTON))), 4)
        registry.retain(null)
        assertEquals(1, registry.size)
        // Wrong node, kind and generation must not consume the command.
        assertNull(registry.resolve(3, 10, ComposeUiEvents.ACTION, callback))
        assertNull(registry.resolve(3, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.CLICK, callback))
        assertNull(registry.resolve(4, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.ACTION, callback))
        assertNotNull(registry.resolve(3, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.ACTION, callback))
        assertEquals(0, registry.size)
        assertNull(registry.resolve(3, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, callback))
    }

    @Test fun dismissalAndRollbackReleaseCommandReferencesWithoutRemovingNodeBindings() {
        val registry = CallbackRegistry()
        val shared = registry.register { }
        registry.commit(tree(shared), 1)
        val command = registry.reserveCommand(shared)
        command.publishGeneration(1)
        assertNotNull(registry.resolve(1, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, shared))
        assertEquals(1, registry.size)
        assertNotNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, shared))
        val rejected = registry.register { }
        val pending = registry.reserveCommand(rejected)
        registry.releaseCommand(pending)
        pending.publishGeneration(1)
        assertEquals(1, registry.size)
        assertNull(registry.resolve(1, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, rejected))
        registry.close()
        assertEquals(0, registry.size)
    }

    @Test fun commandsRequireRegisteredOneShotIdsAndShareTheCallbackBudget() {
        val registry = CallbackRegistry()
        fun invalid(action: () -> Unit) {
            try { action(); fail("Expected invalid callback") }
            catch (failure: ComposeUiContractException) { assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT, failure.code) }
        }
        invalid { registry.reserveCommand(1) }
        val callback = registry.register { }
        registry.commit(tree(callback), 1)
        val command = registry.reserveCommand(callback)
        invalid { registry.reserveCommand(callback) }
        command.publishGeneration(1)
        registry.resolve(1, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, callback)
        // A node-retained callback cannot be reused for a command: an old terminal event would be ambiguous.
        invalid { registry.reserveCommand(callback) }
        repeat(ComposeUiLimits.MAX_CALLBACKS_PER_SESSION - 1) {
            registry.reserveCommand(registry.register { })
        }
        try { registry.register { }; fail("Expected callback limit") }
        catch (failure: ComposeUiContractException) { assertEquals(ComposeUiErrorCodes.LIMIT_EXCEEDED, failure.code) }
        registry.retain(null)
        assertEquals(ComposeUiLimits.MAX_CALLBACKS_PER_SESSION - 1, registry.size)
        registry.close()
        assertEquals(0, registry.size)
    }

    @Test fun aCommandResultCannotRemoveCallbacksFromATreeAwaitingCommit() {
        val registry = CallbackRegistry()
        val callback = registry.register { }
        val command = registry.reserveCommand(callback)
        command.publishGeneration(0)
        registry.stage(null, tree(callback))
        assertNotNull(registry.resolve(0, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, callback))
        assertEquals(1, registry.size)
        registry.commit(tree(callback), 1)
        assertNotNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, callback))
        registry.retain(null)
        assertEquals(0, registry.size)
    }

    @Test fun evictedTerminalResultsReleaseReferencesWithoutCallingScriptOrTouchingOtherCommands() {
        val registry = CallbackRegistry()
        val evicted = CommandResultEvictions()
        val first = registry.register { fail("Evicted events cannot invoke scripts") }
        val second = registry.register { }
        registry.reserveCommand(first).publishGeneration(3)
        registry.reserveCommand(second).publishGeneration(4)
        evicted.add(3, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, first)
        evicted.add(3, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, second) // Stale generation.
        evicted.add(4, 10, ComposeUiEvents.DISMISS, second) // Wrong node.
        evicted.add(4, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.CLICK, second) // Not terminal.
        evicted.drain(registry)
        assertEquals(1, registry.size)
        assertNull(registry.resolve(3, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, first))
        assertNotNull(registry.resolve(4, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.ACTION, second))
        assertEquals(0, registry.size)
    }

    @Test fun overflowCleanupHasABoundedFallbackAndKeepsLiveNodeCallbacks() {
        val registry = CallbackRegistry()
        val evicted = CommandResultEvictions()
        val shared = registry.register { }
        registry.commit(tree(shared), 1)
        registry.reserveCommand(shared).publishGeneration(1)
        val commandOnly = registry.register { fail("Overflow cleanup cannot invoke scripts") }
        registry.reserveCommand(commandOnly).publishGeneration(1)
        // More distinct invalid headers than registered callbacks must not retain unlimited metadata.
        repeat(ComposeUiLimits.MAX_CALLBACKS_PER_SESSION + 1) { index ->
            evicted.add(index.toLong(), ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, commandOnly)
        }
        evicted.drain(registry)
        assertEquals(1, registry.size)
        assertNotNull(registry.resolve(1, 10, ComposeUiEvents.CLICK, shared))
        assertNull(registry.resolve(1, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, shared))
        // A later drain is empty and cannot accidentally clear a newly submitted command.
        val later = registry.register { }
        registry.reserveCommand(later).publishGeneration(1)
        evicted.drain(registry)
        assertNotNull(registry.resolve(1, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.ACTION, later))
    }

    @Test fun commandTicketPublicationIsVisibleToAnAlreadyQueuedDrainAndCannotReviveReleasedBindings() {
        val registry = CallbackRegistry()
        val callback = registry.register { }
        val ticket = registry.reserveCommand(callback)
        // An unrelated node event queued this drain before the main executor started the command.
        val queuedDrain = {
            assertNotNull(registry.resolve(7, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, callback))
        }
        assertNull(ticket.generation)
        ticket.publishGeneration(7)
        queuedDrain()
        assertEquals(0, registry.size)
        ticket.publishGeneration(8) // A stale executor can only mutate its detached ticket.
        assertNull(registry.resolve(8, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.DISMISS, callback))
        val pending = registry.reserveCommand(registry.register { })
        registry.close()
        pending.publishGeneration(8)
        assertEquals(0, registry.size)
        assertNull(registry.resolve(8, ComposeUiContract.NO_NODE_ID, ComposeUiEvents.ACTION, pending.callbackId))
    }
}
