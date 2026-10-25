package org.autojs.autojs.runtime.api.modules.gallery

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.core.plugin.gallery.GalleryEvent
import org.autojs.autojs.core.plugin.gallery.GalleryPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Wrapper
import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The `GalleryViewSession` emitter with its runner, and the result waiter of `pick` / `edit`
 * (3-Maple Image roadmap M6, 10.1), on the pure-JVM dispatcher seam: event order and payloads, the
 * state properties, the single terminal `close`, `close()` through the bound port, overflow, the
 * silent exit, `viewAsync`'s open wait and the outcomes of a picker or editor session.
 */
class GalleryViewSessionTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    /** [queued] keeps the dispatched actions until [drain], like a busy script thread. */
    private class Harness(queued: Boolean = false, maxQueuedActions: Int = 1_000) {
        val released = AtomicInteger()
        val terminal = AtomicInteger()
        val scheduled = ArrayDeque<() -> Unit>()
        val dispatcher = ScriptAsyncDispatcher(
            valueMapper = { it },
            scheduler = if (queued) { action -> scheduled.addLast(action) } else { action -> action() },
            releaseWait = { released.incrementAndGet() },
            maxQueuedActions = maxQueuedActions,
        )
        val controller = GalleryViewSession.Controller(dispatcher, ScriptBridges(), listOf("/sdcard/a.jpg", "/sdcard/b.png", "/sdcard/c.webp")) { terminal.incrementAndGet() }
        val finished = ArrayList<GalleryViewRunner>()
        val runner = GalleryViewRunner(controller, onFinished = { finished += it })
        val session: GalleryViewSession get() = controller.session
        val events = ArrayList<Pair<String, List<Any?>>>()

        fun drain() {
            while (scheduled.isNotEmpty()) scheduled.removeFirst()()
        }
    }

    private class FakePort : GalleryViewSession.Port {
        val calls = ArrayList<String>()
        override fun close(finish: Boolean) {
            calls += "close($finish)"
        }
    }

    private fun Harness.record(vararg names: String) {
        names.forEach { name -> session.on(name, listener { args -> events += name to args.toList() }) }
    }

    private fun listener(action: (Array<out Any?>) -> Unit): Callable =
        RhinoUtils.newBaseFunction(scope, "listener", { args -> action(args) }, RhinoUtils.NOT_CONSTRUCTABLE)

    /** Listener arguments arrive through the bridges as Rhino wrappers; the tests look at the Java values. */
    private fun unwrap(value: Any?): Any? = (value as? Wrapper)?.unwrap() ?: value

    private fun payload(index: Int, events: List<Pair<String, List<Any?>>>): Map<*, *> = unwrap(events[index].second[0]) as Map<*, *>

    @Test
    fun eventsArriveInOrderAndCloseIsTerminal() {
        val h = Harness()
        h.record(GalleryViewSession.EVENT_OPEN, GalleryViewSession.EVENT_PAGE, GalleryViewSession.EVENT_ERROR, GalleryViewSession.EVENT_CLOSE)
        assertEquals("pending", h.session.state)
        assertEquals(3, h.session.count)
        assertEquals(GalleryViewSession.NO_PAGE, h.session.index)
        assertNull(h.session.path)
        assertEquals(listOf("/sdcard/a.jpg", "/sdcard/b.png", "/sdcard/c.webp"), h.session.paths)

        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_OPEN, kind = GalleryContract.KIND_VIEWER))
        assertEquals("open", h.session.state)
        assertTrue(h.session.isOpen)
        assertEquals(mapOf("count" to 3), payload(0, h.events))

        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_PAGE, index = 1))
        assertEquals(mapOf("index" to 1, "path" to "/sdcard/b.png"), payload(1, h.events))
        assertEquals(1, h.session.index)
        assertEquals("/sdcard/b.png", h.session.path)
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_PAGE, index = 7))
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_PAGE))
        assertEquals("pages outside the viewer are dropped", 2, h.events.size)

        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_ERROR, errorCode = GalleryErrorCodes.DECODE_FAILED, errorMessage = "truncated"))
        val error = unwrap(h.events[2].second[0]) as GalleryError
        assertEquals(GalleryErrorCodes.DECODE_FAILED, error.code)
        assertEquals("truncated", error.message)

        assertEquals(0, h.terminal.get())
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_USER))
        assertEquals(listOf("open", "page", "error", "close"), h.events.map { it.first })
        assertEquals(mapOf("reason" to "user"), payload(3, h.events))
        assertEquals("closed", h.session.state)
        assertTrue(h.session.isClosed)
        assertFalse(h.session.isOpen)
        assertEquals("user", h.session.reason)
        assertEquals(1, h.terminal.get())
        assertTrue(h.released.get() >= 1)
        assertTrue(h.runner.isFinished)
        assertEquals(listOf(h.runner), h.finished)

        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_PAGE, index = 2))
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_HOST))
        assertEquals(4, h.events.size)
        assertEquals(1, h.session.index)
        assertTrue(h.session.toString().contains("state: closed") && h.session.toString().contains("reason: user"))
    }

    @Test
    fun closeGoesThroughTheBoundPortAndFinishesByDefault() {
        val h = Harness()
        val port = FakePort()
        assertTrue(h.controller.bindPort(port))
        assertSame(h.session, h.session.close())
        h.session.close(evaluate("({ finish: false })"))
        assertEquals(listOf("close(true)", "close(false)"), port.calls)
        assertEquals(GalleryErrorCodes.INVALID_ARGUMENT, codeOf { h.session.close(evaluate("({ finish: 'yes' })")) })
        assertEquals(GalleryErrorCodes.INVALID_ARGUMENT, codeOf { h.session.close(evaluate("({ nope: true })")) })

        h.controller.close(GalleryContract.REASON_HOST)
        assertSame("close after close is a no-op", h.session, h.session.close())
        assertEquals(2, port.calls.size)
        assertFalse(h.controller.bindPort(FakePort()))
    }

    @Test
    fun overflowReplacesTheQueueWithErrorAndCloseOverflow() {
        val h = Harness(queued = true, maxQueuedActions = 2)
        h.record(GalleryViewSession.EVENT_OPEN, GalleryViewSession.EVENT_PAGE, GalleryViewSession.EVENT_ERROR, GalleryViewSession.EVENT_CLOSE)
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_OPEN))
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_PAGE, index = 0))
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_PAGE, index = 1))
        assertTrue(h.runner.isFinished)
        assertTrue(h.session.isClosed)
        h.drain()
        assertEquals(listOf("error", "close"), h.events.map { it.first })
        assertEquals(GalleryErrorCodes.LIMIT_EXCEEDED, (unwrap(h.events[0].second[0]) as GalleryError).code)
        assertEquals(mapOf("reason" to "overflow"), payload(1, h.events))
        assertEquals(1, h.terminal.get())
    }

    @Test
    fun cancelSilentlyEmitsNothingAndMarksTheSessionClosed() {
        val h = Harness()
        h.record(GalleryViewSession.EVENT_OPEN, GalleryViewSession.EVENT_CLOSE)
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_OPEN))
        h.runner.cancelSilently()
        assertEquals(listOf("open"), h.events.map { it.first })
        assertTrue(h.session.isClosed)
        assertEquals(GalleryViewSession.REASON_EXIT, h.session.reason)
        assertEquals(1, h.terminal.get())
        h.runner.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_USER))
        assertEquals(1, h.events.size)
        h.runner.cancelSilently()
        assertEquals(1, h.terminal.get())
        assertEquals(listOf(h.runner), h.finished)
    }

    @Test
    fun awaitOpenSettlesWithTheOpenEventOrFailsWhenTheViewerClosesFirst() = runBlocking {
        val shown = Harness()
        shown.runner.onEvent(GalleryEvent(GalleryContract.EVENT_OPEN))
        shown.runner.awaitOpen()

        val refused = Harness()
        refused.runner.onEvent(GalleryEvent(GalleryContract.EVENT_ERROR, errorCode = GalleryErrorCodes.FILE_UNREADABLE, errorMessage = "no pages"))
        refused.runner.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_ERROR))
        assertEquals(GalleryErrorCodes.FILE_UNREADABLE, suspendCodeOf { refused.runner.awaitOpen() })

        val late = Harness()
        late.runner.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_TIMEOUT))
        assertEquals(GalleryErrorCodes.TIMEOUT, suspendCodeOf { late.runner.awaitOpen() })

        val exited = Harness()
        exited.runner.cancelSilently()
        try {
            exited.runner.awaitOpen()
            fail("expected a cancellation")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun theWaiterAnswersTheResultACancelOrTheReportedFailure() = runBlocking {
        val chosen = GalleryResultWaiter()
        chosen.onEvent(GalleryEvent(GalleryContract.EVENT_OPEN, kind = GalleryContract.KIND_PICKER))
        chosen.onEvent(GalleryEvent(GalleryContract.EVENT_RESULT, itemsJson = "[]"))
        chosen.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_DONE))
        assertEquals("[]", chosen.await()?.itemsJson)

        val cancelled = GalleryResultWaiter()
        cancelled.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_USER))
        assertNull(cancelled.await())

        val denied = GalleryResultWaiter()
        denied.onEvent(GalleryEvent(GalleryContract.EVENT_ERROR, errorCode = GalleryErrorCodes.PERMISSION_REQUIRED, errorMessage = "refused"))
        denied.onEvent(GalleryEvent(GalleryContract.EVENT_ERROR, errorCode = GalleryErrorCodes.INTERNAL, errorMessage = "later"))
        denied.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_ERROR))
        assertEquals(GalleryErrorCodes.PERMISSION_REQUIRED, suspendCodeOf { denied.await() })

        val replaced = GalleryResultWaiter()
        replaced.onEvent(GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_REPLACED))
        assertEquals(GalleryErrorCodes.SESSION_REPLACED, suspendCodeOf { replaced.await() })

        val exited = GalleryResultWaiter()
        exited.cancelSilently()
        try {
            exited.await()
            fail("expected a cancellation")
        } catch (_: CancellationException) {
        }
    }

    private fun evaluate(source: String): Scriptable = context.evaluateString(scope, source, "test.js", 1, null) as Scriptable

    private inline fun codeOf(block: () -> Any?): String {
        try {
            block()
        } catch (e: GalleryPluginException) {
            return e.code
        }
        fail("expected a GalleryPluginException")
        throw AssertionError()
    }

    private suspend fun suspendCodeOf(block: suspend () -> Any?): String {
        try {
            block()
        } catch (e: GalleryPluginException) {
            return e.code
        }
        fail("expected a GalleryPluginException")
        throw AssertionError()
    }
}
