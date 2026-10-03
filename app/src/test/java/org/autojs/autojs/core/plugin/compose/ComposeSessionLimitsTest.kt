package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class ComposeSessionLimitsTest {
    private class Dispatcher : ScriptUiDispatcher {
        val queue = ArrayDeque<() -> Unit>()
        var current = true
        var closed = false
        override fun isDispatchThread() = current
        override fun post(action: () -> Unit) { if (!closed) queue.add(action) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }
        override fun close() { closed = true; queue.clear() }
    }
    private class Renderer : ComposeSessionRenderer {
        val batches = ArrayList<UiPatchBatch>()
        val commands = ArrayList<UiCommand>()
        var reject = false
        var rejectCommands = false
        var disposals = 0
        override fun open(sink: ComposeUiEventSink) = Unit
        override fun attach() = Unit
        override fun detach() = Unit
        override fun apply(batch: UiPatchBatch) { if (reject) throw ComposeUiContractException(ComposeUiErrorCodes.RENDER_FAILED); batches += batch }
        override fun execute(command: UiCommand) {
            if (rejectCommands) throw ComposeUiContractException(ComposeUiErrorCodes.RENDER_FAILED)
            commands += command
        }
        override fun setTheme(theme: ThemeSpec) = Unit
        override fun dispose() { disposals++ }
    }
    private fun text(value: String) = UiTree(1, listOf(UiNode(1, ComposeUiComponents.TEXT, props = mapOf(ComposeUiProps.TEXT to UiValue.Str(value)))))
    private fun session(dispatcher: Dispatcher, renderer: Renderer, errors: MutableList<ComposeUiError> = mutableListOf(), main: Executor = Executor { it.run() }) =
        ComposeSession(1, dispatcher, main, renderer, setOf(ComposeUiComponents.TEXT, ComposeUiComponents.COLUMN), errors::add).also { it.open() }
    private fun limited(action: () -> Unit) {
        try { action(); fail("Expected budget rejection") } catch (e: ComposeUiContractException) { assertEquals(ComposeUiErrorCodes.LIMIT_EXCEEDED, e.code) }
    }

    @Test fun oneTurnCoalescesRendersAndFailedBatchPreservesPublishedTreeAndGeneration() {
        val script = Dispatcher(); val renderer = Renderer(); val errors = ArrayList<ComposeUiError>()
        val session = session(script, renderer, errors)
        session.render(text("a")); session.render(text("b")); session.render(text("c"))
        assertTrue(renderer.batches.isEmpty()); script.drain()
        assertEquals(1, renderer.batches.size); assertEquals(1L, session.generation)
        val previous = session.tree
        renderer.reject = true; session.render(text("d")); script.drain()
        assertSame(previous, session.tree); assertEquals(1L, session.generation)
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, errors.single().code)
        renderer.reject = false; session.render(text("e")); script.drain()
        assertEquals(2L, session.generation)
        session.close(); session.close(); assertEquals(1, renderer.disposals); assertNull(session.tree)
    }

    @Test fun nonUiDispatchNeverWaitsForMainAndSerializesPendingTransactions() {
        val script = Dispatcher(); val renderer = Renderer(); val tasks = ArrayDeque<Runnable>()
        val session = session(script, renderer, main = Executor(tasks::add))
        fun main() { script.current = false; while (tasks.isNotEmpty()) tasks.removeFirst().run(); script.current = true }
        main(); session.render(text("a")); script.drain()
        session.render(text("b")); session.render(text("c")); script.drain()
        assertEquals(1, tasks.size); assertNull(session.tree)
        main(); assertNull(session.tree); script.drain(); assertEquals(1L, session.generation)
        assertEquals(1, tasks.size); main(); script.drain(); assertEquals(2L, session.generation)
        session.close(); main(); assertTrue(script.closed); assertEquals(1, renderer.disposals)
    }

    @Test fun callbackAndEngineSessionBudgetsAreEnforcedBeforePublication() {
        val registry = CallbackRegistry()
        repeat(ComposeUiLimits.MAX_CALLBACKS_PER_SESSION) { registry.register { } }
        limited { registry.register { } }
        val scope = ComposeSessionScope()
        repeat(ComposeUiLimits.MAX_SESSIONS_PER_ENGINE) { scope.add(session(Dispatcher(), Renderer())) }
        limited { scope.add(session(Dispatcher(), Renderer())) }
        scope.close()
    }

    @Test fun nodeDepthStringAndBatchLimitsAreEnforcedAtTheBoundary() {
        limited { UiValue.Str("x".repeat(ComposeUiLimits.MAX_STRING_CHARS + 1)) }
        assertEquals(ComposeUiLimits.MAX_STRING_CHARS, (text("x".repeat(ComposeUiLimits.MAX_STRING_CHARS)).nodes[0].props[ComposeUiProps.TEXT] as UiValue.Str).value.length)
        fun chain(size: Int) = UiTree(1, (1..size).map { UiNode(it, ComposeUiComponents.COLUMN, children = if (it < size) listOf(it + 1) else emptyList()) })
        chain(ComposeUiLimits.MAX_DEPTH)
        limited { chain(ComposeUiLimits.MAX_DEPTH + 1) }
        limited { UiTree(1, (1..ComposeUiLimits.MAX_NODES_PER_SESSION + 1).map { UiNode(it, ComposeUiComponents.TEXT) }) }
        limited { UiPatchBatch(1, 1, List(ComposeUiLimits.MAX_PATCHES_PER_BATCH + 1) { UiPatch.SetProps(1, emptyMap()) }) }
        // A legal 4001-node tree can still exceed the patch budget on one update.
        fun wide(value: String) = UiTree(1, listOf(UiNode(1, ComposeUiComponents.COLUMN, children = listOf(2, 3)),
            UiNode(2, ComposeUiComponents.COLUMN, children = (4..2003).toList()), UiNode(3, ComposeUiComponents.COLUMN, children = (2004..4001).toList())) +
            (4..4001).map { UiNode(it, ComposeUiComponents.TEXT, props = mapOf(ComposeUiProps.TEXT to UiValue.Str(value))) })
        val reconciler = TreeReconciler(); val before = reconciler.reconcile(null, wide("a")).tree
        limited { reconciler.reconcile(before, wide("b")) }
    }

    @Test fun snackbarCallbacksRemainAliveWhenTreesChangeAndCloseReleasesThem() {
        val script = Dispatcher(); val renderer = Renderer()
        val session = session(script, renderer)
        val callback = session.registerCallback { }
        session.execute(UiCommand.ShowSnackbar("status", callbackId = callback))
        session.render(text("first")); script.drain()
        session.render(text("second")); script.drain()
        assertEquals(1, session.callbackCount)
        assertEquals(1, renderer.commands.size)
        assertEquals(2L, session.generation)
        session.close()
        assertEquals(0, session.callbackCount)
    }

    @Test fun rejectedSnackbarReleasesItsBindingOnTheScriptDispatcher() {
        val script = Dispatcher(); val renderer = Renderer(); val errors = ArrayList<ComposeUiError>()
        val tasks = ArrayDeque<Runnable>()
        val session = session(script, renderer, errors, Executor(tasks::add))
        fun main() { script.current = false; while (tasks.isNotEmpty()) tasks.removeFirst().run(); script.current = true }
        main()
        renderer.rejectCommands = true
        val callback = session.registerCallback { }
        session.execute(UiCommand.ShowSnackbar("status", callbackId = callback))
        main()
        assertEquals(1, session.callbackCount)
        assertTrue(errors.isEmpty())
        script.drain()
        assertEquals(0, session.callbackCount)
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, errors.single().code)
        assertTrue(renderer.commands.isEmpty())
    }

    @Test fun unregisteredAndDuplicateCommandCallbacksFailBeforeRendererSubmission() {
        val script = Dispatcher(); val renderer = Renderer()
        val session = session(script, renderer)
        fun invalid(action: () -> Unit) {
            try { action(); fail("Expected invalid callback") }
            catch (failure: ComposeUiContractException) { assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT, failure.code) }
        }
        invalid { session.execute(UiCommand.ShowSnackbar("status", callbackId = 123)) }
        assertTrue(renderer.commands.isEmpty())
        val callback = session.registerCallback { }
        session.execute(UiCommand.ShowSnackbar("first", callbackId = callback))
        invalid { session.execute(UiCommand.ShowSnackbar("duplicate", callbackId = callback)) }
        assertEquals(1, renderer.commands.size)
        assertEquals(1, session.callbackCount)
    }

    @Test fun failedPendingApplyDoesNotPruneACommandWaitingBehindIt() {
        val script = Dispatcher(); val renderer = Renderer(); val errors = ArrayList<ComposeUiError>()
        val tasks = ArrayDeque<Runnable>()
        val session = session(script, renderer, errors, Executor(tasks::add))
        fun main() { script.current = false; while (tasks.isNotEmpty()) tasks.removeFirst().run(); script.current = true }
        main()
        renderer.reject = true
        session.render(text("rejected")); script.drain()
        val callback = session.registerCallback { }
        session.execute(UiCommand.ShowSnackbar("still active", callbackId = callback))
        main(); script.drain()
        assertNull(session.tree)
        assertEquals(0L, session.generation)
        assertEquals(1, session.callbackCount)
        assertEquals(1, renderer.commands.size)
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, errors.single().code)
        // A second, failed command is released while the first command remains held.
        renderer.rejectCommands = true
        session.execute(UiCommand.ShowSnackbar("rejected command", callbackId = session.registerCallback { }))
        main(); script.drain()
        assertEquals(1, session.callbackCount)
        assertEquals(2, errors.size)
        session.close(); main()
        assertEquals(0, session.callbackCount)
    }
}
