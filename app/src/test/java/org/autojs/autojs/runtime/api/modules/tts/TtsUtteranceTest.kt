package org.autojs.autojs.runtime.api.modules.tts

import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TtsUtteranceTest {

    @Test
    fun preservesProgressOrderingBeforeSuccessfulTerminalDelivery() {
        val fixture = Fixture()
        val events = mutableListOf<String>()
        listOf(
            TtsUtterance.EVENT_QUEUED,
            TtsUtterance.EVENT_START,
            TtsUtterance.EVENT_CHUNK,
            TtsUtterance.EVENT_DONE,
        ).forEach { event ->
            fixture.utterance.on(event, listener { events += event })
        }

        assertTrue(fixture.controller.queued(mapOf("stage" to "queued")))
        assertTrue(fixture.controller.start(mapOf("stage" to "start")))
        assertEquals("running", fixture.utterance.state)
        assertTrue(fixture.controller.chunk(mapOf("stage" to "chunk")))
        assertTrue(fixture.controller.complete(mapOf("status" to "done")))
        assertEquals("done", fixture.utterance.state)
        assertTrue(fixture.utterance.isDone)

        fixture.drain()

        assertEquals(listOf("queued", "start", "chunk", "done"), events)
        assertEquals(1, fixture.resolvedCount.get())
        assertEquals(0, fixture.rejectedCount.get())
        assertEquals(1, fixture.terminalCount.get())
        assertEquals(1, fixture.releaseCount.get())
    }

    @Test
    fun visibleCancellationPurgesQueuedProgress() {
        val fixture = Fixture()
        val events = mutableListOf<String>()
        listOf(
            TtsUtterance.EVENT_QUEUED,
            TtsUtterance.EVENT_START,
            TtsUtterance.EVENT_STOP,
            TtsUtterance.EVENT_CANCELLED,
        ).forEach { event ->
            fixture.utterance.on(event, listener { events += event })
        }

        fixture.controller.queued(null)
        fixture.controller.start(null)
        assertTrue(
            fixture.controller.stop(
                mapOf("reason" to "cancelled"),
                IllegalStateException("cancelled"),
                cancelled = true,
            ),
        )
        fixture.drain()

        assertEquals(listOf("stop", "cancelled"), events)
        assertEquals(0, fixture.resolvedCount.get())
        assertEquals(1, fixture.rejectedCount.get())
        assertEquals("cancelled", fixture.utterance.state)
    }

    @Test
    fun runtimeExitCanPurgeAnAlreadyQueuedTerminalAction() {
        val fixture = Fixture()
        val doneEvents = AtomicInteger()
        fixture.utterance.on(
            TtsUtterance.EVENT_DONE,
            listener { doneEvents.incrementAndGet() },
        )

        assertTrue(fixture.controller.complete("done"))
        assertTrue(fixture.controller.cancelSilently())
        fixture.drain()

        assertEquals("done", fixture.utterance.state)
        assertEquals(0, doneEvents.get())
        assertEquals(0, fixture.resolvedCount.get())
        assertEquals(1, fixture.terminalCount.get())
        assertEquals(1, fixture.releaseCount.get())
        assertFalse(fixture.controller.cancelSilently())
    }

    @Test
    fun javaInteropAcceptsProxyWrappedOnceListenerWithoutAmbiguity() {
        val context = Context.enter()
        try {
            val scope = context.initStandardObjects()
            val fixture = Fixture()
            val wrapped = Context.javaToJS(fixture.utterance, scope) as Scriptable
            ScriptableObject.putProperty(scope, "utterance", wrapped)
            assertEquals("boolean", context.evaluateString(scope, "typeof utterance.isDone", "tts-is-done-type", 1, null))
            assertEquals(false, context.evaluateString(scope, "utterance.isDone", "tts-is-done", 1, null))

            context.evaluateString(
                scope,
                """
                var calls = 0;
                var listener = new Proxy(function() {
                    calls++;
                    receiver = this;
                }, {});
                utterance.once("start", listener);
                """.trimIndent(),
                "tts-utterance-proxy-listener",
                1,
                null,
            )

            fixture.controller.start(null)
            fixture.drain()

            assertEquals(
                1,
                context.evaluateString(scope, "calls", "tts-calls", 1, null),
            )
            assertEquals(0, fixture.utterance.listenerCount(TtsUtterance.EVENT_START))
            assertTrue(
                context.evaluateString(
                    scope,
                    "receiver === utterance",
                    "tts-receiver",
                    1,
                    null,
                ) as Boolean,
            )
        } finally {
            Context.exit()
        }
    }

    private inner class Fixture {

        val scheduled = ArrayDeque<() -> Unit>()
        val releaseCount = AtomicInteger()
        val terminalCount = AtomicInteger()
        val resolvedCount = AtomicInteger()
        val rejectedCount = AtomicInteger()
        val stopId = AtomicReference<String?>()
        val dispatcher = ScriptAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )
        val adapter = ScriptPromiseAdapter()
            .onResolve(callback(resolvedCount))
            .onReject(callback(rejectedCount))
        val utterance = TtsUtterance(
            id = "tts-test",
            operation = "speech",
            text = "hello",
            dispatcher = dispatcher,
            bridges = ScriptBridges(),
            promiseAdapter = adapter,
            result = Any(),
            requestStop = stopId::set,
            onTerminal = { terminalCount.incrementAndGet() },
        )
        val controller = TtsUtterance.Controller(utterance)

        fun drain() {
            while (scheduled.isNotEmpty()) {
                scheduled.removeFirst().invoke()
            }
        }
    }

    private fun listener(action: () -> Unit): BaseFunction =
        object : BaseFunction(), EventEmitter.IListener {

            override fun onEvent(args: Array<out Any?>) {
                action()
            }
        }

    private fun callback(counter: AtomicInteger) =
        object : ScriptPromiseAdapter.Callback {

            override fun call(arg: Any?) {
                counter.incrementAndGet()
            }
        }
}
