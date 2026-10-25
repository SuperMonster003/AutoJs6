package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.AiPluginStreamRunner
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.autojs.core.plugin.ai.AiPluginMessage
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.ai.AiStream
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
                add(AiPluginStreamEvent.Opened(testPluginTarget()))
                repeat(10) { add(AiPluginStreamEvent.Chunk("piece-$it|")) }
                add(AiPluginStreamEvent.Usage(usage()))
                add(testPluginStreamCompletion(expectedText, usage()))
            },
        )
        val runtime = runtime(runner, scheduler = scheduled::addLast)
        val observed = mutableListOf<String>()
        val chunkText = StringBuilder()
        val deltaText = StringBuilder()
        val reportedUsage = AtomicReference<Scriptable?>()
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
        stream.once(AiStream.EVENT_USAGE, listener { args ->
            reportedUsage.set(args[0] as Scriptable)
            observed += "usage"
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
        assertPluginUsage(checkNotNull(reportedUsage.get()))
        assertPluginUsage(done.get()?.get("usage") as Scriptable)
        assertEquals("stop", done.get()?.get("finishReason"))
        assertNull(done.get()?.get("message"))
        assertNull(done.get()?.get("error"))
        assertNull(done.get()?.get("raw"))
        assertNull(done.get()?.get("profile"))
        assertEquals("local:example-model", (done.get()?.get("target") as Scriptable)["id"])
        assertEquals("example.provider", (done.get()?.get("plugin") as Scriptable)["provider"])
        assertEquals("plugin", done.get()?.get("route"))
        assertEquals("example.provider", done.get()?.get("provider"))
        assertEquals("example-model", done.get()?.get("model"))
        assertEquals(1, observed.count { it == "open" })
        assertEquals(10, observed.count { it == "chunk" })
        assertEquals(10, observed.count { it == "delta" })
        assertEquals(1, observed.count { it == "usage" })
        assertEquals(1, observed.count { it == "done" })
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(AiPluginStreamEvent.Failed(AiPluginAskFailure.PROVIDER_FAILED))
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
        runner.emit(testPluginStreamCompletion("first", usage()))
        scheduled.removeFirst().invoke()
        assertEquals("first", first.get())

        runtime.stream(request()).once(AiStream.EVENT_DONE, listener { args ->
            second.set((args[0] as Scriptable)["text"] as String)
        })
        runner.emit(testPluginStreamCompletion("second", usage()))
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
            runner.emit(testPluginStreamCompletion("natural completion lost the race", usage()))
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

        runner.emit(AiPluginStreamEvent.Failed(AiPluginAskFailure.PROVIDER_FAILED))
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
        runner.emit(testPluginStreamCompletion("late", usage()))
        runner.emit(AiPluginStreamEvent.Failed(AiPluginAskFailure.PROVIDER_FAILED))

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

    private fun request() = AiPluginAskRequest(
        component = AiProviderComponent(
            packageName = "org.example.provider",
            className = "org.example.provider.AiProviderService",
        ),
        providerId = "example.provider",
        targetId = "local:example-model",
        messages = listOf(AiPluginMessage(AiPluginMessageRole.USER, "prompt")),
        timeoutMillis = 5_000L,
    )

    private fun assertPluginUsage(usage: Scriptable) {
        assertEquals(12L, (usage["inputTokens"] as Number).toLong())
        assertEquals(3L, (usage["outputTokens"] as Number).toLong())
        assertEquals(15L, (usage["totalTokens"] as Number).toLong())
        assertNull(usage["reasoningTokens"])
        assertNull(usage["cachedInputTokens"])
        assertEquals(250L, (usage["durationMillis"] as Number).toLong())
        assertNull(usage["raw"])
    }

    private fun usage() = AiPluginUsage(
        inputTokens = 12,
        outputTokens = 3,
        totalTokens = 15,
        durationMillis = 250,
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
        private val synchronousEvents: List<AiPluginStreamEvent> = emptyList(),
    ) : AiPluginStreamRunner {
        private val callback = AtomicReference<((AiPluginStreamEvent) -> Unit)?>()
        val startCount = AtomicInteger()
        val handle = RecordingHandle()

        override fun startStream(
            request: AiPluginAskRequest,
            onEvent: (AiPluginStreamEvent) -> Unit,
        ): AiPluginAskHandle {
            startCount.incrementAndGet()
            callback.set(onEvent)
            synchronousEvents.forEach(onEvent)
            return handle
        }

        fun emit(event: AiPluginStreamEvent) {
            checkNotNull(callback.get())(event)
        }
    }

    private class RecordingHandle : AiPluginAskHandle {
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
