package org.autojs.autojs.core.plugin.compose

import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.ScriptDispatchQueueOverflowException
import org.autojs.plugin.compose.api.ComposeUiComponents
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiEvents
import org.autojs.plugin.compose.api.ComposeUiProps
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

class ComposeSessionControlTest {
    @Test fun saturatedUserPostsCannotLoseAFrameReceiptOrCommandFailureCleanup() {
        val fixture = Fixture()
        val outcomes = ArrayList<ComposeSession.CommitResult>()
        fixture.session.submitStableTree(tree("accepted"), outcomes::add)
        fixture.drainControl()
        fixture.saturateUserPosts()
        val command = fixture.session.registerCallback {}
        fixture.renderer.commandFailure = ComposeUiContractException(ComposeUiErrorCodes.RENDER_FAILED)
        fixture.session.execute(UiCommand.ShowSnackbar("message", callbackId = command))
        fixture.runMain()
        assertTrue(outcomes.isEmpty())
        fixture.drainControl()
        assertTrue(outcomes.single() is ComposeSession.CommitResult.Accepted)
        assertEquals(tree("accepted"), fixture.session.tree)
        assertEquals(0, fixture.session.callbackCount)
        assertEquals(1, fixture.errors.size)
        assertEquals(0, fixture.userDeliveries)
        fixture.close()
    }

    @Test fun nativeCloseBypassesAFullUserQueueAndCompletesPendingReceiptsExactlyOnce() {
        val fixture = Fixture()
        val previous = fixture.session.registerCallback {}
        fixture.session.submitStableTree(tree("published", previous))
        fixture.drainControl(); fixture.runMain(); fixture.drainControl()
        val outcomes = ArrayList<ComposeSession.CommitResult>()
        val first = fixture.session.registerCallback {}
        fixture.session.submitStableTree(tree("flight", first), outcomes::add)
        fixture.drainControl()
        val next = fixture.session.registerCallback {}
        fixture.session.submitStableTree(tree("pending", next), outcomes::add)
        fixture.saturateUserPosts()
        fixture.main.add(Runnable { fixture.session.close() })
        fixture.runMain()
        assertEquals(1, fixture.renderer.disposals)
        assertEquals(0, fixture.closed)
        assertTrue(outcomes.isEmpty())
        fixture.drainControl()
        assertEquals(1, fixture.closed)
        assertEquals(0, fixture.session.callbackCount)
        assertNull(fixture.session.tree)
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it is ComposeSession.CommitResult.Rejected && it.error.code == ComposeUiErrorCodes.SESSION_CLOSED })
        assertEquals(0, fixture.userDeliveries)
        fixture.session.close(); fixture.drainControl()
        assertEquals(1, fixture.closed)
        assertEquals(2, outcomes.size)
        fixture.close()
    }

    @Test fun failedOpenStopsQueuedNativeWorkAndReportsBeforeClosingThroughTheControlLane() {
        val fixture = Fixture(openFailure = ComposeUiContractException(ComposeUiErrorCodes.RENDER_FAILED, "open failed"))
        fixture.session.attach()
        fixture.session.submitStableTree(tree("never drawn"))
        fixture.drainControl()
        fixture.saturateUserPosts()
        fixture.runMain()
        assertEquals(0, fixture.renderer.attaches)
        assertTrue(fixture.renderer.batches.isEmpty())
        fixture.drainControl(); fixture.runMain()
        assertEquals(listOf("error", "close"), fixture.notifications)
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, fixture.errors.single().code)
        assertEquals(1, fixture.renderer.disposals)
        assertEquals(0, fixture.session.callbackCount)
        assertNull(fixture.session.tree)
        assertEquals(0, fixture.userDeliveries)
        fixture.close()
    }

    private class Fixture(openFailure: Throwable? = null) {
        private val origin = Thread.currentThread()
        private val userTimers = ConcurrentLinkedQueue<() -> Unit>()
        private val controlTimers = ConcurrentLinkedQueue<() -> Unit>()
        val main = ConcurrentLinkedQueue<Runnable>()
        private val user = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = { userTimers.add(it) }, releaseWait = {})
        private val native = ScriptAsyncDispatcher(valueMapper = { it }, scheduler = { controlTimers.add(it) }, releaseWait = {}, maxQueuedActions = 1)
        private val dispatcher = TimerScriptUiDispatcher(origin,
            enqueue = { action -> user.dispatchValues(emptyArray()) { action() } },
            cancel = { user.cancelSilently(); native.cancelSilently() },
            enqueueControl = { action -> native.dispatchValues(emptyArray()) { action() } })
        private val shared = object : ScriptUiDispatcher by dispatcher { override fun close() = Unit }
        val renderer = Renderer(openFailure)
        val errors = ArrayList<ComposeUiError>()
        val notifications = ArrayList<String>()
        var closed = 0
        var userDeliveries = 0
        val session = ComposeSession(1, shared, Executor { main.add(it) }, renderer, setOf(ComposeUiComponents.TEXT), {
            assertSame(origin, Thread.currentThread()); errors += it; notifications += "error"
        }, onClosed = {
            assertSame(origin, Thread.currentThread()); closed++; notifications += "close"
        })
        init { session.open() }
        fun saturateUserPosts() {
            repeat(512) { dispatcher.post { userDeliveries++ } }
            assertThrows(ScriptDispatchQueueOverflowException::class.java) { dispatcher.post {} }
        }
        fun drainControl() {
            var count = 0
            while (true) {
                val action = controlTimers.poll() ?: return
                check(count++ < 100) { "Control queue did not settle" }
                action()
            }
        }
        fun runMain() {
            val error = AtomicReference<Throwable>()
            val thread = Thread {
                try { while (true) (main.poll() ?: break).run() } catch (failure: Throwable) { error.set(failure) }
            }
            thread.start(); thread.join(5000)
            assertFalse(thread.isAlive)
            error.get()?.let { throw AssertionError("Native callback escaped", it) }
        }
        fun close() { session.close(); runMain(); dispatcher.close() }
    }

    private class Renderer(private val openFailure: Throwable?) : ComposeSessionRenderer {
        val batches = ArrayList<UiPatchBatch>()
        var commandFailure: Throwable? = null
        var attaches = 0
        var disposals = 0
        override fun open(sink: ComposeUiEventSink) { openFailure?.let { throw it } }
        override fun attach() { attaches++ }
        override fun detach() = Unit
        override fun apply(batch: UiPatchBatch) { batches += batch }
        override fun execute(command: UiCommand) { commandFailure?.let { throw it } }
        override fun setTheme(theme: ThemeSpec) = Unit
        override fun dispose() { disposals++ }
    }

    private companion object {
        fun tree(text: String, callback: Int? = null) = UiTree(11, listOf(UiNode(11, ComposeUiComponents.TEXT,
            props = mapOf(ComposeUiProps.TEXT to UiValue.Str(text)),
            callbacks = callback?.let { mapOf(ComposeUiEvents.CLICK to it) }.orEmpty())))
    }
}
