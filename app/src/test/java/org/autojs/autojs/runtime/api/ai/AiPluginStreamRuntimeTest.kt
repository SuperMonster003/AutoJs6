package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginStreamRunner
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginMessage
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.OnDeviceAiProviderComponent
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.ai.AiStream
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiPluginStreamRuntimeTest {

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

    @Test
    fun synchronousEventsStayOrderedCompleteOnceAndCloseLateHandleOnce() {
        val scheduled = ArrayDeque<() -> Unit>()
        val expectedText = (0 until 10).joinToString(separator = "") { "piece-$it|" }
        val runner = FakeStreamRunner(
            synchronousEvents = buildList {
                add(OnDeviceAiPluginStreamEvent.Opened)
                repeat(10) { add(OnDeviceAiPluginStreamEvent.Chunk("piece-$it|")) }
                add(OnDeviceAiPluginStreamEvent.Completed(expectedText))
            },
        )
        val runtime = runtime(runner, scheduler = scheduled::addLast)
        val observed = mutableListOf<String>()
        val chunkText = StringBuilder()
        val deltaText = StringBuilder()
        val done = AtomicReference<Scriptable?>()

        val stream = runtime.stream(request())
        stream.on(AiStream.EVENT_OPEN, listener { args ->
            val metadata = args[0] as Scriptable
            assertEquals("plugin", metadata["route"])
            assertEquals("example.provider", metadata["provider"])
            assertEquals("example-model", metadata["model"])
            observed += "open"
        })
        stream.on(AiStream.EVENT_CHUNK, listener { args ->
            val chunk = args[0] as Scriptable
            assertEquals("", chunk["reasoning"])
            assertEquals(0L, (chunk["toolCalls"] as NativeArray).length)
            assertNull(chunk["usage"])
            assertEquals(false, chunk["done"])
            assertNull(chunk["finishReason"])
            assertNull(chunk["raw"])
            chunkText.append(chunk["text"])
            observed += "chunk"
        })
        stream.on(AiStream.EVENT_DELTA, listener { args ->
            deltaText.append(args[0])
            observed += "delta"
        })
        stream.once(AiStream.EVENT_DONE, listener { args ->
            done.set(args[0] as Scriptable)
            observed += "done"
        })

        assertEquals(1, scheduled.size)
        while (scheduled.isNotEmpty()) scheduled.removeFirst().invoke()

        assertEquals("done", stream.state)
        assertTrue(stream.isDone)
        assertEquals(expectedText, chunkText.toString())
        assertEquals(expectedText, deltaText.toString())
        assertEquals(expectedText, done.get()?.get("text"))
        assertEquals("", done.get()?.get("reasoning"))
        assertEquals(0L, (done.get()?.get("toolCalls") as NativeArray).length)
        assertNull(done.get()?.get("usage"))
        assertNull(done.get()?.get("finishReason"))
        assertNull(done.get()?.get("message"))
        assertNull(done.get()?.get("error"))
        assertNull(done.get()?.get("raw"))
        assertNull(done.get()?.get("profile"))
        assertEquals("plugin", done.get()?.get("route"))
        assertEquals("example.provider", done.get()?.get("provider"))
        assertEquals("example-model", done.get()?.get("model"))
        assertEquals(1, observed.count { it == "open" })
        assertEquals(10, observed.count { it == "chunk" })
        assertEquals(10, observed.count { it == "delta" })
        assertEquals(1, observed.count { it == "done" })
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(OnDeviceAiPluginStreamEvent.Failed(OnDeviceAiPluginAskFailure.PROVIDER_FAILED))
        assertTrue(scheduled.isEmpty())
        assertEquals(1, runner.handle.closeCount.get())
        runtime.close()
    }

    @Test
    fun runnerIsLazySharedAndOwnerIsReusableAfterNaturalTerminal() {
        val scheduled = ArrayDeque<() -> Unit>()
        val runner = FakeStreamRunner()
        val providerCalls = AtomicInteger()
        val runtime = AiPluginStreamRuntime(
            runnerProvider = {
                providerCalls.incrementAndGet()
                runner
            },
            dispatcherFactory = {
                AiAsyncDispatcher(
                    valueMapper = { value -> RhinoUtils.toJsValue(scope, value) },
                    scheduler = scheduled::addLast,
                    releaseWait = {},
                )
            },
            bridges = ScriptBridges(),
        )
        val first = AtomicReference<String?>()
        val second = AtomicReference<String?>()

        assertEquals(0, providerCalls.get())
        runtime.stream(request()).once(AiStream.EVENT_DONE, listener { args ->
            first.set((args[0] as Scriptable)["text"] as String)
        })
        runner.emit(OnDeviceAiPluginStreamEvent.Completed("first"))
        scheduled.removeFirst().invoke()
        assertEquals("first", first.get())

        runtime.stream(request()).once(AiStream.EVENT_DONE, listener { args ->
            second.set((args[0] as Scriptable)["text"] as String)
        })
        runner.emit(OnDeviceAiPluginStreamEvent.Completed("second"))
        scheduled.removeFirst().invoke()

        assertEquals("second", second.get())
        assertEquals(1, providerCalls.get())
        assertEquals(2, runner.startCount.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(2, runner.handle.closeCount.get())
        runtime.close()
    }

    @Test
    fun publicCancelWinsPausedHookVersusCompletionAndStillCancelsAndClosesHandleOnce() {
        val hookEntered = CountDownLatch(1)
        val releaseHook = CountDownLatch(1)
        val scheduled = ArrayDeque<() -> Unit>()
        val runner = FakeStreamRunner()
        val runtime = runtime(
            runner = runner,
            scheduler = scheduled::addLast,
            cancellationActionWrapper = { action ->
                {
                    hookEntered.countDown()
                    check(releaseHook.await(5, TimeUnit.SECONDS)) { "Timed out releasing cancellation hook" }
                    action()
                }
            },
        )
        val cancelled = AtomicInteger()
        val done = AtomicInteger()
        val errors = AtomicInteger()
        val stream = runtime.stream(request())
        stream.once(AiStream.EVENT_CANCELLED, listener { cancelled.incrementAndGet() })
        stream.once(AiStream.EVENT_DONE, listener { done.incrementAndGet() })
        stream.once(AiStream.EVENT_ERROR, listener { errors.incrementAndGet() })

        val cancelFailure = AtomicReference<Throwable?>()
        val cancelThread = Thread(
            {
                runCatching(stream::cancel).onFailure(cancelFailure::set)
            },
            "ai-plugin-stream-cancel-race-test",
        ).apply { start() }
        assertTrue("Cancellation hook was not entered", hookEntered.await(5, TimeUnit.SECONDS))
        assertEquals(1, scheduled.size)
        scheduled.removeFirst().invoke()

        try {
            runner.emit(OnDeviceAiPluginStreamEvent.Completed("natural completion lost the race"))
            assertEquals(1, runner.handle.cancelCount.get())
            assertEquals(1, runner.handle.closeCount.get())
        } finally {
            releaseHook.countDown()
            cancelThread.join(5_000L)
        }
        assertFalse("Cancellation thread remained blocked", cancelThread.isAlive)
        cancelFailure.get()?.let { throw AssertionError("Public cancellation failed", it) }

        assertEquals("cancelled", stream.state)
        assertTrue(stream.isDone)
        assertEquals(1, cancelled.get())
        assertEquals(0, done.get())
        assertEquals(0, errors.get())
        assertEquals(1, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(OnDeviceAiPluginStreamEvent.Failed(OnDeviceAiPluginAskFailure.PROVIDER_FAILED))
        runtime.close()
        assertEquals(1, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
    }

    @Test
    fun runtimeCloseIsSilentScopedAndSuppressesLateEvents() {
        val runner = FakeStreamRunner()
        val runtime = runtime(runner, scheduler = { it() })
        val cancelled = AtomicInteger()
        val done = AtomicInteger()
        val errors = AtomicInteger()
        val stream = runtime.stream(request())
        stream.once(AiStream.EVENT_CANCELLED, listener { cancelled.incrementAndGet() })
        stream.once(AiStream.EVENT_DONE, listener { done.incrementAndGet() })
        stream.once(AiStream.EVENT_ERROR, listener { errors.incrementAndGet() })

        runtime.close()
        runtime.close()
        runner.emit(OnDeviceAiPluginStreamEvent.Completed("late"))
        runner.emit(OnDeviceAiPluginStreamEvent.Failed(OnDeviceAiPluginAskFailure.PROVIDER_FAILED))

        assertEquals("cancelled", stream.state)
        assertTrue(stream.isDone)
        assertEquals(0, cancelled.get())
        assertEquals(0, done.get())
        assertEquals(0, errors.get())
        assertEquals(1, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
    }

    private fun runtime(
        runner: FakeStreamRunner,
        scheduler: ((() -> Unit) -> Unit),
        cancellationActionWrapper: ((() -> Unit) -> () -> Unit) = { it },
    ) = AiPluginStreamRuntime(
        runnerProvider = { runner },
        dispatcherFactory = {
            AiAsyncDispatcher(
                valueMapper = { value -> RhinoUtils.toJsValue(scope, value) },
                scheduler = scheduler,
                releaseWait = {},
            )
        },
        bridges = ScriptBridges(),
        cancellationActionWrapper = cancellationActionWrapper,
    )

    private fun request() = OnDeviceAiPluginAskRequest(
        component = OnDeviceAiProviderComponent(
            packageName = "org.example.provider",
            className = "org.example.provider.OnDeviceAiService",
        ),
        providerId = "example.provider",
        modelId = "example-model",
        messages = listOf(OnDeviceAiPluginMessage(OnDeviceAiPluginMessageRole.USER, "prompt")),
        timeoutMillis = 5_000L,
    )

    private fun listener(action: (Array<out Any?>) -> Unit): Callable =
        RhinoUtils.newBaseFunction(
            scope,
            "listener",
            { args -> action(args) },
            RhinoUtils.NOT_CONSTRUCTABLE,
        )

    private operator fun Scriptable.get(name: String): Any? =
        ScriptableObject.getProperty(this, name)

    private class FakeStreamRunner(
        private val synchronousEvents: List<OnDeviceAiPluginStreamEvent> = emptyList(),
    ) : OnDeviceAiPluginStreamRunner {
        private val callback = AtomicReference<((OnDeviceAiPluginStreamEvent) -> Unit)?>()
        val startCount = AtomicInteger()
        val handle = RecordingHandle()

        override fun startStream(
            request: OnDeviceAiPluginAskRequest,
            onEvent: (OnDeviceAiPluginStreamEvent) -> Unit,
        ): OnDeviceAiPluginAskHandle {
            startCount.incrementAndGet()
            callback.set(onEvent)
            synchronousEvents.forEach(onEvent)
            return handle
        }

        fun emit(event: OnDeviceAiPluginStreamEvent) {
            checkNotNull(callback.get())(event)
        }
    }

    private class RecordingHandle : OnDeviceAiPluginAskHandle {
        val cancelCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun cancel(): Boolean {
            cancelCount.incrementAndGet()
            return true
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }
}
