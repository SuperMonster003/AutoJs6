package org.autojs.autojs.core.plugin.compose

import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.ScriptDispatchQueueOverflowException
import org.autojs.plugin.compose.api.ComposeUiComponents
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiEvents
import org.autojs.plugin.compose.api.ComposeUiProps
import org.autojs.plugin.compose.api.ComposeUiLimits
import org.autojs.plugin.compose.api.dialog.DialogInteropV1
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

    @Test fun dialogDismissSurvivesANewerFrameAndBothSaturatedQueuesWithoutCallingFromTheSinkStack() {
        val fixture = Fixture(closeEventType = DialogInteropV1.DISMISS_REQUEST)
        fixture.session.submitStableTree(tree("before"))
        fixture.drainControl(); fixture.runMain(); fixture.drainControl()
        fixture.session.submitStableTree(tree("after"))
        fixture.drainControl()
        fixture.saturateUserPosts()
        fixture.main.add(Runnable {
            // Generation zero may have been captured before an animated hide and this new frame.
            fixture.session.enqueueLifecycle(dismiss(generation = 0), null)
            repeat(ComposeUiLimits.EVENT_QUEUE_CAPACITY + 1) {
                fixture.session.enqueue(event(1, 2, 11, ComposeUiEvents.CLICK, 99))
            }
            assertEquals(0, fixture.closed)
        })
        fixture.runMain()
        assertEquals(0, fixture.closed)
        fixture.drainControl(); fixture.runMain()
        assertEquals(listOf("close"), fixture.notifications)
        assertEquals(1, fixture.renderer.disposals)
        assertEquals(0, fixture.userDeliveries)
        assertNull(fixture.session.tree)
        fixture.close()
    }

    @Test fun dialogFatalErrorAndCloseAreOneReservedDeliveryAndUnknownCodesAreNormalized() {
        val fixture = Fixture(closeEventType = DialogInteropV1.DISMISS_REQUEST)
        fixture.session.submitStableTree(tree("dialog"))
        fixture.drainControl(); fixture.runMain(); fixture.drainControl()
        fixture.saturateUserPosts()
        fixture.main.add(Runnable {
            // Bundle decoding has a separate Android gate; exercise the decoded lifecycle policy.
            fixture.session.enqueueLifecycle(dismiss(), ComposeUiError.presentationFailure("UNRECOGNIZED_PROVIDER_CODE", "window"))
            fixture.session.enqueueLifecycle(dismiss(), null)
            repeat(ComposeUiLimits.EVENT_QUEUE_CAPACITY + 1) {
                fixture.session.enqueue(event(1, 1, 11, ComposeUiEvents.CLICK, 99))
            }
            assertTrue(fixture.notifications.isEmpty())
        })
        fixture.runMain(); fixture.drainControl(); fixture.runMain()
        assertEquals(listOf("error", "close"), fixture.notifications)
        assertEquals(ComposeUiErrorCodes.RENDER_FAILED, fixture.errors.single().code)
        assertEquals("window", fixture.errors.single().prop)
        fixture.session.enqueueLifecycle(dismiss(), ComposeUiError.presentationFailure(ComposeUiErrorCodes.PERMISSION_REQUIRED))
        fixture.drainControl()
        assertEquals(1, fixture.closed)
        assertEquals(1, fixture.errors.size)
        assertEquals(0, fixture.userDeliveries)
        assertEquals(ComposeUiErrorCodes.PERMISSION_REQUIRED, ComposeUiError.presentationFailure(ComposeUiErrorCodes.PERMISSION_REQUIRED).code)
        fixture.close()
    }

    @Test fun dialogControlRequiresTheNegotiatedSessionAndSystemEnvelope() {
        val fixture = Fixture(closeEventType = DialogInteropV1.DISMISS_REQUEST)
        fixture.session.enqueueLifecycle(event(2, 0, -1, DialogInteropV1.DISMISS_REQUEST, 0), null)
        fixture.session.enqueueLifecycle(event(1, 0, 11, DialogInteropV1.DISMISS_REQUEST, 0), null)
        fixture.session.enqueueLifecycle(event(1, 0, -1, DialogInteropV1.DISMISS_REQUEST, 1), null)
        fixture.drainControl()
        assertEquals(0, fixture.closed)
        fixture.session.enqueueLifecycle(dismiss(), null)
        assertEquals(0, fixture.closed) // UI dispatch also always queues the lifecycle callback.
        fixture.drainControl(); fixture.runMain()
        assertEquals(1, fixture.closed)
        fixture.close()
        val plain = Fixture()
        plain.session.enqueue(dismiss()); plain.drainControl()
        assertEquals(0, plain.closed)
        plain.close()
    }

    private class Fixture(openFailure: Throwable? = null, closeEventType: String? = null) {
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
        }, closeEventType = closeEventType)
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
        fun dismiss(generation: Long = 1) = event(1, generation, -1, DialogInteropV1.DISMISS_REQUEST, 0)
        /**
         * The repository's RhinoTestRuntime uses this same JVM allocation seam. Supply only
         * validated envelope fields here; real Bundle snapshot/decoding belongs to device tests.
         */
        fun event(sessionId: Int, generation: Long, nodeId: Int, type: String, callbackId: Int): UiEvent {
            val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true; get(null) as sun.misc.Unsafe
            }
            val result = unsafe.allocateInstance(UiEvent::class.java) as UiEvent
            mapOf<String, Any>("sessionId" to sessionId, "generation" to generation, "nodeId" to nodeId,
                "type" to type, "callbackId" to callbackId, "payloadValues" to emptyMap<String, Any?>()).forEach { (name, value) ->
                UiEvent::class.java.getDeclaredField(name).run { isAccessible = true; set(result, value) }
            }
            return result
        }
        fun tree(text: String, callback: Int? = null) = UiTree(11, listOf(UiNode(11, ComposeUiComponents.TEXT,
            props = mapOf(ComposeUiProps.TEXT to UiValue.Str(text)),
            callbacks = callback?.let { mapOf(ComposeUiEvents.CLICK to it) }.orEmpty())))
    }
}
