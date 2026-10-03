package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class ComposeSessionSubmissionTest {
    private class Dispatcher : ScriptUiDispatcher {
        val tasks = ArrayDeque<() -> Unit>()
        var current = true
        var closed = false
        var dropPosts = false
        var closeCalls = 0
        override fun isDispatchThread() = current
        override fun post(action: () -> Unit) { if (!closed && !dropPosts) tasks.add(action) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst()() }
        override fun close() { closeCalls++; closed = true; tasks.clear() }
    }

    private class Renderer : ComposeSessionRenderer {
        val batches = ArrayList<UiPatchBatch>()
        var reject = false
        var disposals = 0
        override fun open(sink: ComposeUiEventSink) = Unit
        override fun attach() = Unit
        override fun detach() = Unit
        override fun apply(batch: UiPatchBatch) {
            if (reject) throw ComposeUiContractException(ComposeUiErrorCodes.RENDER_FAILED)
            batches += batch
        }
        override fun execute(command: UiCommand) = Unit
        override fun setTheme(theme: ThemeSpec) = Unit
        override fun dispose() { disposals++ }
    }

    private class Fixture(onClosed: () -> Unit = {}) {
        val script = Dispatcher()
        val renderer = Renderer()
        val main = ArrayDeque<Runnable>()
        val errors = ArrayList<ComposeUiError>()
        val session = ComposeSession(1, script, Executor(main::add), renderer, setOf(ComposeUiComponents.TEXT), errors::add,
            onClosed = onClosed)
        init { session.open(); main() }
        fun main() {
            script.current = false
            try { while (main.isNotEmpty()) main.removeFirst().run() } finally { script.current = true }
        }
        fun settle() { script.drain(); main(); script.drain() }
        fun accepted(text: String, callback: Int? = null) { session.submitStableTree(tree(text, callback)); settle() }
    }

    companion object {
        private fun tree(text: String, callback: Int? = null) = UiTree(701, listOf(UiNode(701, ComposeUiComponents.TEXT,
            props = mapOf(ComposeUiProps.TEXT to UiValue.Str(text)),
            callbacks = callback?.let { mapOf(ComposeUiEvents.CLICK to it) }.orEmpty())))
    }

    @Test fun stableSubmissionKeepsHandleIdsAndCompletesCoalescedTargetsExactlyOnce() {
        val fixture = Fixture()
        val results = ArrayList<Pair<String, ComposeSession.CommitResult>>()
        fixture.session.submitStableTree(tree("first")) { results += "first" to it }
        fixture.session.submitStableTree(tree("last")) { results += "last" to it }
        assertEquals(listOf("first" to ComposeSession.CommitResult.Superseded), results)
        assertNull(fixture.session.tree)
        fixture.settle()
        assertEquals(701, fixture.session.tree!!.rootId)
        assertEquals(1, fixture.renderer.batches.size)
        val accepted = results.last().second as ComposeSession.CommitResult.Accepted
        assertEquals(tree("last"), accepted.tree)
        assertEquals(1L, accepted.generation)
        assertEquals(2, results.size)
    }

    @Test fun unchangedSubmissionAcknowledgesExistingGenerationWithoutAnExtraRendererBatch() {
        val fixture = Fixture()
        fixture.accepted("same")
        val results = ArrayList<ComposeSession.CommitResult>()
        fixture.session.submitStableTree(tree("same"), results::add)
        assertTrue(results.isEmpty())
        fixture.script.drain()
        assertEquals(1, fixture.renderer.batches.size)
        val accepted = results.single() as ComposeSession.CommitResult.Accepted
        assertEquals(1L, accepted.generation)
        assertEquals(tree("same"), accepted.tree)
        assertTrue(fixture.main.isEmpty())
    }

    @Test fun rendererRejectionRejectsFlightAndDependentTargetOnScriptThreadWithoutLosingPublishedState() {
        val fixture = Fixture()
        val oldCallback = fixture.session.registerCallback { }
        fixture.accepted("published", oldCallback)
        val published = fixture.session.tree
        val outcomes = ArrayList<ComposeSession.CommitResult>()
        val completed: (ComposeSession.CommitResult) -> Unit = {
            assertTrue(fixture.script.current)
            outcomes += it
        }
        val nextCallback = fixture.session.registerCallback { }
        fixture.session.submitStableTree(tree("flight", nextCallback), completed)
        fixture.script.drain()
        val pendingCallback = fixture.session.registerCallback { }
        fixture.session.submitStableTree(tree("dependent", pendingCallback), completed)
        fixture.renderer.reject = true
        fixture.main()
        assertTrue(outcomes.isEmpty())
        fixture.script.drain()
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it is ComposeSession.CommitResult.Rejected && it.error.code == ComposeUiErrorCodes.RENDER_FAILED })
        assertSame(published, fixture.session.tree)
        assertEquals(1L, fixture.session.generation)
        assertTrue(fixture.session.containsCallback(oldCallback))
        assertFalse(fixture.session.containsCallback(nextCallback))
        assertFalse(fixture.session.containsCallback(pendingCallback))
        assertEquals(1, fixture.errors.size)
    }

    @Test fun callbackCleanupPreservesPublishedFlightPendingAndCommandReferences() {
        val fixture = Fixture()
        val published = fixture.session.registerCallback { }
        fixture.accepted("published", published)
        val flight = fixture.session.registerCallback { }
        fixture.session.submitStableTree(tree("flight", flight))
        fixture.script.drain()
        val pending = fixture.session.registerCallback { }
        fixture.session.submitStableTree(tree("pending", pending))
        val command = fixture.session.registerCallback { }
        fixture.session.execute(UiCommand.ShowSnackbar("message", callbackId = command))
        val orphan = fixture.session.registerCallback { }
        fixture.session.discardUnusedCallbacks()
        for (callback in listOf(published, flight, pending, command)) assertTrue(fixture.session.containsCallback(callback))
        assertFalse(fixture.session.containsCallback(orphan))
        fixture.main(); fixture.script.drain()
        assertFalse(fixture.session.containsCallback(published))
        assertTrue(fixture.session.containsCallback(flight))
        assertTrue(fixture.session.containsCallback(pending))
        assertTrue(fixture.session.containsCallback(command))
        fixture.session.close(); fixture.main()
        assertEquals(0, fixture.session.callbackCount)
    }

    @Test fun closeRejectsEveryOutstandingSubmissionOnceAndLateMainWorkCannotAcceptIt() {
        val fixture = Fixture()
        val outcomes = ArrayList<ComposeSession.CommitResult>()
        fixture.session.submitStableTree(tree("flight"), outcomes::add)
        fixture.script.drain()
        fixture.session.submitStableTree(tree("pending"), outcomes::add)
        fixture.session.close()
        fixture.main(); fixture.script.drain()
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it is ComposeSession.CommitResult.Rejected && it.error.code == ComposeUiErrorCodes.SESSION_CLOSED })
        assertTrue(fixture.renderer.batches.isEmpty())
        assertNull(fixture.session.tree)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test fun synchronousValidationFailureDoesNotSupersedeAValidPendingSubmission() {
        val fixture = Fixture()
        val outcomes = ArrayList<ComposeSession.CommitResult>()
        fixture.session.submitStableTree(tree("valid"), outcomes::add)
        val orphan = fixture.session.registerCallback { }
        try {
            fixture.session.submitStableTree(UiTree(702, listOf(UiNode(702, "Unknown")))) { fail("Unstaged tree cannot complete") }
            fail("Unknown component must be rejected")
        } catch (failure: ComposeUiContractException) {
            assertEquals(ComposeUiErrorCodes.UNKNOWN_COMPONENT, failure.code)
        }
        fixture.session.discardUnusedCallbacks()
        assertFalse(fixture.session.containsCallback(orphan))
        fixture.settle()
        assertTrue(outcomes.single() is ComposeSession.CommitResult.Accepted)
        assertEquals(tree("valid"), fixture.session.tree)
    }

    @Test fun ownerCloseRetriesCleanupDroppedByAQuittingScriptDispatcher() {
        var closed = 0
        val fixture = Fixture { closed++ }
        val published = fixture.session.registerCallback {}
        fixture.accepted("published", published)
        val outcomes = ArrayList<ComposeSession.CommitResult>()
        val flight = fixture.session.registerCallback {}
        fixture.session.submitStableTree(tree("flight", flight)) {
            assertTrue(fixture.script.current)
            outcomes += it
        }
        fixture.script.drain()
        val pending = fixture.session.registerCallback {}
        fixture.session.submitStableTree(tree("pending", pending)) {
            assertTrue(fixture.script.current)
            outcomes += it
        }
        fixture.script.dropPosts = true
        fixture.script.current = false
        fixture.session.close()
        assertEquals(ComposeSession.State.CLOSED, fixture.session.state)
        assertEquals(0, closed)
        assertTrue(outcomes.isEmpty())
        assertEquals(3, fixture.session.callbackCount)
        assertNotNull(fixture.session.tree)
        fixture.main()
        assertEquals(1, fixture.renderer.disposals)

        fixture.session.close()
        assertEquals(0, fixture.session.callbackCount)
        assertNull(fixture.session.tree)
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it is ComposeSession.CommitResult.Rejected && it.error.code == ComposeUiErrorCodes.SESSION_CLOSED })
        assertEquals(1, closed)
        assertEquals(1, fixture.script.closeCalls)
        fixture.session.close(); fixture.main()
        assertEquals(2, outcomes.size)
        assertEquals(1, closed)
        assertEquals(1, fixture.renderer.disposals)
        assertEquals(1, fixture.script.closeCalls)
    }

    @Test fun aLateCleanupDeliveryCannotRepeatOwnerRecoveryOrSubmissionReceipts() {
        var closed = 0
        val fixture = Fixture { closed++ }
        val outcomes = ArrayList<ComposeSession.CommitResult>()
        fixture.session.submitStableTree(tree("pending"), outcomes::add)
        fixture.script.current = false
        fixture.session.close()
        val lateCleanup = fixture.script.tasks.removeFirst()
        fixture.script.current = true
        fixture.session.close()
        assertEquals(1, outcomes.size)
        assertEquals(1, closed)
        lateCleanup()
        fixture.main()
        assertEquals(1, outcomes.size)
        assertEquals(1, closed)
        assertEquals(1, fixture.script.closeCalls)
        assertEquals(1, fixture.renderer.disposals)
    }
}
