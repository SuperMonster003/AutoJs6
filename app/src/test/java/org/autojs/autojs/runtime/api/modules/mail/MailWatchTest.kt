package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.core.plugin.mail.MailError
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.ScriptDispatchQueueOverflowException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.mail.api.MailErrorCodes
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
import org.autojs.autojs.rhino.TopLevelScope
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/** The `MailWatch` emitter (mail roadmap P5) on the pure-JVM dispatcher seam: event order, the single terminal `close`, `stop`, overflow and the silent exit. */
class MailWatchTest {

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
        val controller = MailWatch.Controller(dispatcher, ScriptBridges(), "INBOX", null) { terminal.incrementAndGet() }
        val watch: MailWatch get() = controller.watch
        val events = ArrayList<Pair<String, List<Any?>>>()

        fun drain() {
            while (scheduled.isNotEmpty()) scheduled.removeFirst()()
        }
    }

    private fun Harness.record(scope: TopLevelScope, vararg names: String) {
        names.forEach { name -> watch.on(name, listener(scope) { args -> events += name to args.toList() }) }
    }

    private fun listener(scope: TopLevelScope, action: (Array<out Any?>) -> Unit): Callable =
        RhinoUtils.newBaseFunction(scope, "listener", { args -> action(args) }, RhinoUtils.NOT_CONSTRUCTABLE)

    private fun error(code: String = MailErrorCodes.CONNECT_FAILED) = MailError(code, "fake", retryable = true)

    @Test
    fun eventsArriveInOrderAndCloseIsTheSingleTerminalEvent() {
        val h = Harness()
        h.record(scope, MailWatch.EVENT_MESSAGE, MailWatch.EVENT_MODE, MailWatch.EVENT_RESYNC, MailWatch.EVENT_ERROR, MailWatch.EVENT_CLOSE)
        assertTrue(h.watch.isActive)
        assertEquals("active", h.watch.state)
        assertNull(h.watch.mode)
        h.controller.generation(3L)
        h.controller.initialMode("idle")
        assertEquals("idle", h.watch.mode)
        assertEquals(3L, h.watch.generation)

        assertTrue(h.controller.message(mapOf("uid" to 1)) { plain -> "decorated:$plain" })
        assertTrue(h.controller.mode("poll"))
        assertEquals("poll", h.watch.mode)
        assertTrue(h.controller.resync("uidvalidity-changed"))
        assertTrue(h.controller.error(error()) { "converted:${it.code}" })
        assertTrue(h.controller.close("stopped"))
        assertFalse(h.controller.close("again"))
        assertFalse(h.controller.message(mapOf("uid" to 2)))
        assertFalse(h.controller.error(error()))

        assertEquals(listOf("message", "mode", "resync", "error", "close"), h.events.map { it.first })
        assertEquals("decorated:{uid=1}", h.events[0].second[0])
        assertSame(h.watch, (h.events[0].second[1] as? org.mozilla.javascript.Wrapper)?.unwrap() ?: h.events[0].second[1])
        assertEquals("poll", h.events[1].second[0])
        assertEquals("uidvalidity-changed", h.events[2].second[0])
        assertEquals("converted:CONNECT_FAILED", h.events[3].second[0])
        assertEquals("stopped", h.events[4].second[0])
        assertTrue(h.watch.isClosed)
        assertEquals("closed", h.watch.state)
        assertEquals("stopped", h.watch.reason)
        assertEquals(1, h.released.get())
        assertEquals(1, h.terminal.get())
        assertTrue(h.watch.toString().contains("state: closed"))
    }

    @Test
    fun stopRunsTheBoundActionOnceAndIsANoOpAfterClose() {
        val h = Harness()
        h.record(scope, MailWatch.EVENT_CLOSE)
        val stops = AtomicInteger()
        h.controller.bindStop {
            stops.incrementAndGet()
            h.controller.close(MailWatch.REASON_STOPPED)
        }
        assertSame(h.watch, h.watch.stop())
        h.watch.stop()
        assertEquals(1, stops.get())
        assertEquals(listOf("close"), h.events.map { it.first })
        assertEquals(1, h.terminal.get())
    }

    @Test
    fun stopWithoutABoundActionClosesDirectlyAndALateBindingRunsAtOnce() {
        val h = Harness()
        h.record(scope, MailWatch.EVENT_CLOSE)
        h.watch.stop()
        assertEquals("stopped", h.watch.reason)
        val late = AtomicInteger()
        h.controller.bindStop { late.incrementAndGet() }
        assertEquals(1, late.get())
        assertEquals(1, h.events.size)
    }

    @Test
    fun failReplacesTheQueuedEventsWithErrorAndClose() {
        val h = Harness(queued = true)
        h.record(scope, MailWatch.EVENT_MESSAGE, MailWatch.EVENT_ERROR, MailWatch.EVENT_CLOSE)
        assertTrue(h.controller.message(mapOf("uid" to 1)))
        assertTrue(h.controller.message(mapOf("uid" to 2)))
        assertTrue(h.controller.fail(error(MailErrorCodes.LIMIT_EXCEEDED), MailWatch.REASON_OVERFLOW))
        h.drain()
        assertEquals(listOf("error", "close"), h.events.map { it.first })
        assertEquals(MailWatch.REASON_OVERFLOW, h.events[1].second[0])
        assertEquals(1, h.released.get())
        assertEquals(1, h.terminal.get())
    }

    @Test
    fun anOverflowingQueueThrowsToTheProducer() {
        val h = Harness(queued = true, maxQueuedActions = 2)
        h.record(scope, MailWatch.EVENT_MESSAGE)
        assertTrue(h.controller.message(mapOf("uid" to 1)))
        assertTrue(h.controller.message(mapOf("uid" to 2)))
        try {
            h.controller.message(mapOf("uid" to 3))
            fail("the third event must overflow")
        } catch (_: ScriptDispatchQueueOverflowException) {
        }
        assertTrue(h.controller.fail(error(MailErrorCodes.LIMIT_EXCEEDED), MailWatch.REASON_OVERFLOW))
        h.drain()
        assertTrue(h.events.none { it.first == "message" })
    }

    @Test
    fun cancelSilentlyEmitsNothingAndReleasesTheScript() {
        val h = Harness(queued = true)
        h.record(scope, MailWatch.EVENT_MESSAGE, MailWatch.EVENT_CLOSE)
        assertTrue(h.controller.message(mapOf("uid" to 1)))
        assertTrue(h.controller.cancelSilently())
        assertFalse(h.controller.cancelSilently())
        assertFalse(h.controller.close("late"))
        h.drain()
        assertTrue(h.events.isEmpty())
        assertTrue(h.watch.isClosed)
        assertEquals(MailWatch.REASON_EXIT, h.watch.reason)
        assertEquals(1, h.released.get())
        assertEquals(1, h.terminal.get())
    }

}
