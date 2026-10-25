package org.autojs.autojs.runtime.api.ai

import android.content.Context
import android.content.ContextWrapper
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.AiPluginStreamRunner
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.ai.AiStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Wrapper
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import sun.misc.Unsafe

class AiServicePluginRouteTest {

    @Test
    fun synchronousPluginChatCompletionReusesExactTargetAndReturnsNormalizedShape() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner(
            synchronousOutcome = testPluginCompletion("exact local chat answer", usage()),
        )
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        val resolveCount = AtomicInteger()
        val rejectCount = AtomicInteger()
        val service = service(runner, dispatchers)

        service.chat(pluginInput(timeoutMillis = 2_500L))
            .onResolve(callback { value ->
                resolveCount.incrementAndGet()
                resolved.set(value)
            })
            .onReject(callback { error ->
                rejectCount.incrementAndGet()
                rejected.set(error)
            })
        dispatchers.drain()

        assertPluginChatResponse(resolved.get(), "exact local chat answer")
        assertNull(rejected.get())
        assertEquals(1, resolveCount.get())
        assertEquals(0, rejectCount.get())
        assertEquals(1, runner.startCount.get())
        assertEquals("org.example.provider", runner.request.get()?.component?.packageName)
        assertEquals("org.example.provider.AiProviderService", runner.request.get()?.component?.className)
        assertEquals("example.provider", runner.request.get()?.providerId)
        assertEquals("local:example-model", runner.request.get()?.targetId)
        assertEquals("prompt", runner.request.get()?.messages?.single()?.content)
        assertEquals(2_500L, runner.request.get()?.timeoutMillis)
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()
        assertPluginChatResponse(resolved.get(), "exact local chat answer")
        assertNull(rejected.get())
        assertEquals(1, resolveCount.get())
        assertEquals(0, rejectCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        service.close()
    }

    @Test
    fun pluginChatFailureRejectsStableRouteCode() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner()
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        val resolveCount = AtomicInteger()
        val rejectCount = AtomicInteger()
        val service = service(runner, dispatchers)

        service.chat(pluginInput())
            .onResolve(callback { value ->
                resolveCount.incrementAndGet()
                resolved.set(value)
            })
            .onReject(callback { error ->
                rejectCount.incrementAndGet()
                rejected.set(error)
            })
        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.BINDER_DIED))
        dispatchers.drain()

        assertNull(resolved.get())
        assertEquals(0, resolveCount.get())
        assertEquals(1, rejectCount.get())
        val error = rejected.get() as Map<*, *>
        assertEquals("AiPluginRouteException", error["name"])
        assertEquals("AI plugin chat failed (BINDER_DIED)", error["message"])
        assertEquals("plugin", error["route"])
        assertEquals("BINDER_DIED", error["code"])
        assertEquals(1, runner.startCount.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(testPluginCompletion("late completion must not win", usage()))
        dispatchers.drain()
        assertNull(resolved.get())
        assertEquals(error, rejected.get())
        assertEquals(0, resolveCount.get())
        assertEquals(1, rejectCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        service.close()
    }

    @Test
    fun synchronousPluginCompletionResolvesExactTextAndClosesHandleOnce() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner(
            synchronousOutcome = testPluginCompletion("exact local answer", usage()),
        )
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        val service = service(runner, dispatchers)

        val promise = service.ask(pluginInput(timeoutMillis = 2_500L))
            .onResolve(callback(resolved::set))
            .onReject(callback(rejected::set))
        dispatchers.drain()

        assertEquals("exact local answer", resolved.get())
        assertNull(rejected.get())
        assertEquals(1, runner.startCount.get())
        assertEquals("org.example.provider", runner.request.get()?.component?.packageName)
        assertEquals("org.example.provider.AiProviderService", runner.request.get()?.component?.className)
        assertEquals("example.provider", runner.request.get()?.providerId)
        assertEquals("local:example-model", runner.request.get()?.targetId)
        assertEquals("prompt", runner.request.get()?.messages?.single()?.content)
        assertEquals(2_500L, runner.request.get()?.timeoutMillis)
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()
        assertEquals("exact local answer", resolved.get())
        assertNull(rejected.get())
        assertEquals(1, runner.handle.closeCount.get())
        service.close()
    }

    @Test
    fun pluginFailureRejectsWithStableRouteCode() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner()
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        val service = service(runner, dispatchers)

        service.ask(pluginInput())
            .onResolve(callback(resolved::set))
            .onReject(callback(rejected::set))
        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.BINDER_DIED))
        dispatchers.drain()

        assertNull(resolved.get())
        val error = rejected.get() as Map<*, *>
        assertEquals("plugin", error["route"])
        assertEquals("BINDER_DIED", error["code"])
        assertEquals(1, runner.startCount.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        service.close()
    }

    @Test
    fun pluginAskForwardsSystemAndConversationMessagesInOrder() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner(
            synchronousOutcome = testPluginCompletion("system-aware answer", usage()),
        )
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        val service = service(runner, dispatchers)

        service.ask(
            pluginInput(
                messages = """[
                    {"role":"system","content":"Reply with the configured marker."},
                    {"role":"user","content":"first question"},
                    {"role":"assistant","content":"first answer"},
                    {"role":"user","content":"final question"}
                ]""",
            ),
        )
            .onResolve(callback(resolved::set))
            .onReject(callback(rejected::set))
        dispatchers.drain()

        assertEquals("system-aware answer", resolved.get())
        assertNull(rejected.get())
        val request = checkNotNull(runner.request.get())
        assertEquals(
            listOf(
                AiPluginMessageRole.SYSTEM,
                AiPluginMessageRole.USER,
                AiPluginMessageRole.ASSISTANT,
                AiPluginMessageRole.USER,
            ),
            request.messages.map { it.role },
        )
        assertEquals(
            listOf(
                "Reply with the configured marker.",
                "first question",
                "first answer",
                "final question",
            ),
            request.messages.map { it.content },
        )
        service.close()
    }

    @Test
    fun malformedPluginSelectionRejectsWithStableInvalidRequest() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner()
        val askRejected = AtomicReference<Any?>()
        val chatRejected = AtomicReference<Any?>()
        val service = service(runner, dispatchers)
        val input = AiRequestInput(
            messages = JsonParser.parseString(
                """[{"role":"user","content":"prompt"}]""",
            ).asJsonArray,
            options = JsonParser.parseString("""{"plugin":null}""").asJsonObject,
        )

        service.ask(input).onReject(callback(askRejected::set))
        service.chat(input).onReject(callback(chatRejected::set))
        dispatchers.drain()

        assertInvalidPluginRequest(askRejected.get())
        assertInvalidPluginRequest(chatRejected.get())
        assertEquals(0, runner.startCount.get())
        service.close()
    }

    @Test
    fun closeCancelsAndClosesActiveHandleOnceAndSuppressesLateTerminals() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner(terminalOnCancel = true)
        val resolveCount = AtomicInteger()
        val rejectCount = AtomicInteger()
        val service = service(runner, dispatchers)

        service.ask(pluginInput())
            .onResolve(callback { resolveCount.incrementAndGet() })
            .onReject(callback { rejectCount.incrementAndGet() })
        service.close()
        service.close()
        runner.emit(testPluginCompletion("late completion", usage()))
        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()

        assertEquals(1, runner.startCount.get())
        assertEquals(1, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(0, resolveCount.get())
        assertEquals(0, rejectCount.get())
    }

    @Test
    fun pluginStreamFailureUsesExactRoute() {
        val dispatchers = TestDispatchers()
        val askRunner = FakePluginAskRunner()
        val streamRunner = FakePluginStreamRunner()
        val errors = AtomicReference<Any?>()
        val service = service(askRunner, dispatchers, streamRunner)

        val stream = service.stream(pluginInput(timeoutMillis = 2_500L))
        stream.once(AiStream.EVENT_ERROR, streamCallback(errors::set))
        streamRunner.emit(AiPluginStreamEvent.Failed(AiPluginAskFailure.BINDER_DIED))
        dispatchers.drain()

        assertEquals("error", stream.state)
        assertEquals(true, stream.isDone)
        assertEquals(1, streamRunner.startCount.get())
        assertEquals("org.example.provider", streamRunner.request.get()?.component?.packageName)
        assertEquals("org.example.provider.AiProviderService", streamRunner.request.get()?.component?.className)
        assertEquals("example.provider", streamRunner.request.get()?.providerId)
        assertEquals("local:example-model", streamRunner.request.get()?.targetId)
        assertEquals("prompt", streamRunner.request.get()?.messages?.single()?.content)
        assertEquals(2_500L, streamRunner.request.get()?.timeoutMillis)
        val error = (errors.get() as Wrapper).unwrap() as Map<*, *>
        assertEquals("plugin", error["route"])
        assertEquals("BINDER_DIED", error["code"])
        assertEquals(0, askRunner.startCount.get())
        assertEquals(0, streamRunner.handle.cancelCount.get())
        assertEquals(1, streamRunner.handle.closeCount.get())

        streamRunner.emit(testPluginStreamCompletion("late terminal must not win", usage()))
        dispatchers.drain()
        assertEquals(1, streamRunner.handle.closeCount.get())
        service.close()
    }

    private fun service(
        runner: FakePluginAskRunner,
        dispatchers: TestDispatchers,
        streamRunner: AiPluginStreamRunner = FakePluginStreamRunner(),
    ) = AiService(
        context = testContext(),
        scriptRuntime = RhinoTestRuntime.create(TopLevelScope()),
        pluginAskRunner = runner,
        pluginDispatcherFactory = dispatchers::create,
        pluginStreamRunner = streamRunner,
    )

    private fun assertPluginChatResponse(value: Any?, expectedText: String) {
        val response = value as Map<*, *>
        assertEquals(
            setOf(
                "text",
                "reasoning",
                "toolCalls",
                "usage",
                "finishReason",
                "message",
                "error",
                "raw",
                "profile",
                "target",
                "plugin",
                "route",
                "provider",
                "model",
            ),
            response.keys.toSet(),
        )
        assertEquals(expectedText, response["text"])
        assertEquals("", response["reasoning"])
        assertEquals(emptyList<Any?>(), response["toolCalls"])
        assertEquals(
            mapOf(
                "inputTokens" to 12L,
                "outputTokens" to 3L,
                "totalTokens" to 15L,
                "reasoningTokens" to null,
                "cachedInputTokens" to null,
                "durationMillis" to 250L,
                "raw" to null,
            ),
            response["usage"],
        )
        assertEquals("stop", response["finishReason"])
        assertNull(response["message"])
        assertNull(response["error"])
        assertNull(response["raw"])
        assertNull(response["profile"])
        assertEquals("local:example-model", (response["target"] as Map<*, *>)["id"])
        assertEquals("example.provider", (response["plugin"] as Map<*, *>)["provider"])
        assertEquals("plugin", response["route"])
        assertEquals("example.provider", response["provider"])
        assertEquals("example-model", response["model"])
    }

    private fun assertInvalidPluginRequest(value: Any?) {
        val error = value as Map<*, *>
        assertEquals("plugin", error["route"])
        assertEquals("INVALID_REQUEST", error["code"])
        assertEquals("AI plugin request is invalid", error["message"])
    }

    private fun usage() = AiPluginUsage(
        inputTokens = 12L,
        outputTokens = 3L,
        totalTokens = 15L,
        durationMillis = 250L,
    )

    private fun testContext(): TestContext {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
        return unsafe.allocateInstance(TestContext::class.java) as TestContext
    }

    private fun pluginInput(
        timeoutMillis: Long? = null,
        messages: String = """[{"role":"user","content":"prompt"}]""",
    ): AiRequestInput {
        val timeout = timeoutMillis?.let { ", \"timeout\": $it" }.orEmpty()
        return AiRequestInput(
            messages = JsonParser.parseString(messages).asJsonArray,
            options = JsonParser.parseString(
                """
                {
                  "plugin": {
                    "component": {
                      "packageName": "org.example.provider",
                      "className": "org.example.provider.AiProviderService"
                    },
                    "providerId": "example.provider"
                  },
                  "target": "local:example-model"
                  $timeout
                }
                """,
            ).asJsonObject,
        )
    }

    private fun callback(action: (Any?) -> Unit) = object : ScriptPromiseAdapter.Callback {
        override fun call(arg: Any?) = action(arg)
    }

    private fun streamCallback(action: (Any?) -> Unit) = Callable { _, _, _, args ->
        action(args.firstOrNull())
        null
    }

    private class TestContext : ContextWrapper(null) {

        override fun getApplicationContext(): Context = this
    }

    private class TestDispatchers {
        private val scheduled = ArrayDeque<() -> Unit>()

        fun create() = AiAsyncDispatcher(
            valueMapper = { it },
            scheduler = scheduled::addLast,
            releaseWait = {},
        )

        fun drain() {
            while (scheduled.isNotEmpty()) scheduled.removeFirst().invoke()
        }
    }

    private class FakePluginAskRunner(
        private val synchronousOutcome: AiPluginAskOutcome? = null,
        terminalOnCancel: Boolean = false,
    ) : AiPluginAskRunner {
        val startCount = AtomicInteger()
        val request = AtomicReference<AiPluginAskRequest?>()
        private val callback = AtomicReference<((AiPluginAskOutcome) -> Unit)?>()
        val handle = RecordingHandle {
            if (terminalOnCancel) {
                emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.CANCELLED))
            }
        }

        override fun start(
            request: AiPluginAskRequest,
            onOutcome: (AiPluginAskOutcome) -> Unit,
        ): AiPluginAskHandle {
            startCount.incrementAndGet()
            this.request.set(request)
            callback.set(onOutcome)
            synchronousOutcome?.let(onOutcome)
            return handle
        }

        fun emit(outcome: AiPluginAskOutcome) {
            checkNotNull(callback.get())(outcome)
        }
    }

    private class FakePluginStreamRunner : AiPluginStreamRunner {
        val startCount = AtomicInteger()
        val request = AtomicReference<AiPluginAskRequest?>()
        private val callback = AtomicReference<((AiPluginStreamEvent) -> Unit)?>()
        val handle = RecordingHandle {}

        override fun startStream(
            request: AiPluginAskRequest,
            onEvent: (AiPluginStreamEvent) -> Unit,
        ): AiPluginAskHandle {
            startCount.incrementAndGet()
            this.request.set(request)
            callback.set(onEvent)
            return handle
        }

        fun emit(event: AiPluginStreamEvent) {
            checkNotNull(callback.get())(event)
        }
    }

    private class RecordingHandle(
        private val onCancel: () -> Unit,
    ) : AiPluginAskHandle {
        val cancelCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override fun cancel(): Boolean {
            cancelCount.incrementAndGet()
            onCancel()
            return true
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }
}
