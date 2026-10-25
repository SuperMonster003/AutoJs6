package org.autojs.autojs.runtime.api.modules.ai

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiStreamTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply {
            initStandardObjects(context, false)
        }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun mapEventIsConvertedBeforeEmissionAndTerminalIsExactlyOnce() {
        val releaseCount = AtomicInteger()
        val terminalCount = AtomicInteger()
        val receivedUsage = AtomicReference<Any?>()
        val receivedDone = AtomicReference<Any?>()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { value -> RhinoUtils.toJsValue(scope, value) },
            scheduler = { it() },
            releaseWait = { releaseCount.incrementAndGet() },
        )
        val controller = AiStream.Controller(dispatcher, ScriptBridges()) {
            terminalCount.incrementAndGet()
        }
        val stream = controller.stream
        stream.on(AiStream.EVENT_USAGE, listener { args -> receivedUsage.set(args[0]) })
        stream.once(AiStream.EVENT_DONE, listener { args -> receivedDone.set(args[0]) })

        assertEquals("created", stream.state)
        assertFalse(stream.isDone)
        assertTrue(controller.usage(mapOf("inputTokens" to 12)))
        assertEquals("open", stream.state)
        assertTrue(receivedUsage.get() is NativeObject)
        assertEquals(12, Context.toNumber((receivedUsage.get() as Scriptable)["inputTokens"]).toInt())

        assertTrue(controller.complete(mapOf("text" to "ready")))
        assertFalse(controller.fail(IllegalStateException("late")))
        assertSame(stream, stream.cancel())

        assertEquals("done", stream.state)
        assertTrue(stream.isDone)
        assertEquals("ready", Context.toString((receivedDone.get() as Scriptable)["text"]))
        assertEquals(1, releaseCount.get())
        assertEquals(1, terminalCount.get())
    }

    @Test
    fun userCancellationInvokesBoundPluginCancellationExactlyOnce() {
        val cancelledEvents = AtomicInteger()
        val transportCancellations = AtomicInteger()
        val releaseCount = AtomicInteger()
        val controller = AiStream.Controller(
            dispatcher = immediateDispatcher(releaseCount),
            bridges = ScriptBridges(),
            onTerminal = null,
        )
        val stream = controller.stream
        stream.on(AiStream.EVENT_CANCELLED, listener { cancelledEvents.incrementAndGet() })
        controller.bindCancellation { transportCancellations.incrementAndGet() }

        assertSame(stream, stream.cancel())
        assertSame(stream, stream.cancel())

        assertEquals("cancelled", stream.state)
        assertTrue(stream.isDone)
        assertEquals(1, cancelledEvents.get())
        assertEquals(1, transportCancellations.get())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun openEventCanOnlyBePublishedOnce() {
        val opens = AtomicInteger()
        val controller = AiStream.Controller(
            dispatcher = immediateDispatcher(AtomicInteger()),
            bridges = ScriptBridges(),
            onTerminal = null,
        )
        val stream = controller.stream
        stream.on(AiStream.EVENT_OPEN, listener { opens.incrementAndGet() })

        assertTrue(controller.open(mapOf("provider" to "test")))
        assertFalse(controller.open(mapOf("provider" to "test-again")))

        assertEquals("open", stream.state)
        assertEquals(1, opens.get())
    }

    @Test
    fun normalizedTextChunkEmitsChunkThenDeltaInOneDispatcherAction() {
        val scheduled = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val normalized = mapOf("text" to "piece")
        val controller = AiStream.Controller(
            dispatcher = AiAsyncDispatcher(
                valueMapper = { value -> RhinoUtils.toJsValue(scope, value) },
                scheduler = scheduled::addLast,
                releaseWait = {},
            ),
            bridges = ScriptBridges(),
            onTerminal = null,
        )
        controller.stream.on(AiStream.EVENT_CHUNK, listener { args ->
            val chunk = args[0] as Scriptable
            assertEquals("piece", chunk["text"])
            events += "chunk"
        })
        controller.stream.on(AiStream.EVENT_DELTA, listener { args ->
            assertEquals("piece", args[0])
            val chunk = args[1] as Scriptable
            assertEquals("piece", chunk["text"])
            events += "delta"
        })

        assertTrue(controller.textChunk("piece", normalized))
        assertEquals(1, scheduled.size)
        scheduled.removeFirst().invoke()

        assertEquals(listOf("chunk", "delta"), events)
        assertEquals("open", controller.stream.state)
    }

    @Test
    fun runtimeCancellationIsSilent() {
        val cancelledEvents = AtomicInteger()
        val releaseCount = AtomicInteger()
        val controller = AiStream.Controller(
            dispatcher = immediateDispatcher(releaseCount),
            bridges = ScriptBridges(),
            onTerminal = null,
        )
        val stream = controller.stream
        stream.on(AiStream.EVENT_CANCELLED, listener { cancelledEvents.incrementAndGet() })

        assertTrue(controller.cancelSilently())
        assertFalse(controller.cancelSilently())

        assertEquals("cancelled", stream.state)
        assertEquals(0, cancelledEvents.get())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun userCancellationPurgesQueuedStreamEventsBeforePublishingCancelled() {
        val scheduled = ArrayDeque<() -> Unit>()
        val deltas = AtomicInteger()
        val chunks = AtomicInteger()
        val cancelled = AtomicInteger()
        val terminalCount = AtomicInteger()
        val releaseCount = AtomicInteger()
        val transportCancellations = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = { releaseCount.incrementAndGet() },
        )
        val controller = AiStream.Controller(dispatcher, ScriptBridges()) {
            terminalCount.incrementAndGet()
        }
        val stream = controller.stream
        stream.on(AiStream.EVENT_DELTA, listener { deltas.incrementAndGet() })
        stream.on(AiStream.EVENT_CHUNK, listener { chunks.incrementAndGet() })
        stream.on(AiStream.EVENT_CANCELLED, listener { cancelled.incrementAndGet() })
        controller.bindCancellation { transportCancellations.incrementAndGet() }
        controller.delta("queued")
        controller.chunk(mapOf("text" to "queued"))

        assertSame(stream, stream.cancel())
        assertEquals(1, scheduled.size)
        scheduled.removeFirst().invoke()

        assertEquals("cancelled", stream.state)
        assertTrue(stream.isDone)
        assertEquals(1, transportCancellations.get())
        assertEquals(0, deltas.get())
        assertEquals(0, chunks.get())
        assertEquals(1, cancelled.get())
        assertEquals(1, terminalCount.get())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun userCancellationStillCancelsPluginTransportWhenTerminalSchedulingFails() {
        val expected = IllegalStateException("scheduler unavailable")
        val releaseCount = AtomicInteger()
        val terminalCount = AtomicInteger()
        val transportCancellations = AtomicInteger()
        val dispatcher = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = { throw expected },
            releaseWait = { releaseCount.incrementAndGet() },
        )
        val controller = AiStream.Controller(dispatcher, ScriptBridges()) {
            terminalCount.incrementAndGet()
        }
        val stream = controller.stream
        controller.bindCancellation { transportCancellations.incrementAndGet() }

        val actual = assertThrows(IllegalStateException::class.java) {
            stream.cancel()
        }

        assertSame(expected, actual)
        assertEquals("cancelled", stream.state)
        assertTrue(stream.isDone)
        assertEquals(1, transportCancellations.get())
        assertEquals(1, terminalCount.get())
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun rhinoVisibleObjectDoesNotExposeTransportStateTransitions() {
        val publicMethodNames = AiStream::class.java.methods.map { it.name }

        listOf(
            "bindCancellation",
            "open",
            "delta",
            "chunk",
            "textChunk",
            "usage",
            "complete",
            "fail",
            "cancelSilently",
        ).forEach { forbiddenPrefix ->
            assertFalse(
                "Unexpected script-visible method: $forbiddenPrefix",
                publicMethodNames.any { it.startsWith(forbiddenPrefix) },
            )
        }
    }

    private fun immediateDispatcher(releaseCount: AtomicInteger): AiAsyncDispatcher {
        return AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = { it() },
            releaseWait = { releaseCount.incrementAndGet() },
        )
    }

    private fun listener(action: (Array<out Any?>) -> Unit): Callable {
        return RhinoUtils.newBaseFunction(
            scope,
            "listener",
            { args -> action(args) },
            RhinoUtils.NOT_CONSTRUCTABLE,
        )
    }

    private operator fun Scriptable.get(name: String): Any? {
        return ScriptableObject.getProperty(this, name)
    }

}
