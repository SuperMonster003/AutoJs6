package org.autojs.autojs.runtime.api.ai

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiTextPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.AiTextPluginStreamRunner
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.ai.AiStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Wrapper
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import sun.misc.Unsafe

class AiServicePluginRouteTest {

    @Test
    fun synchronousPluginChatCompletionReusesExactPromptRouteAndReturnsNormalizedShapeWithoutCloudFallback() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner(
            synchronousOutcome = AiTextPluginAskOutcome.Completed("exact local chat answer"),
        )
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        val resolveCount = AtomicInteger()
        val rejectCount = AtomicInteger()
        val httpCallCount = AtomicInteger()
        TestContext.preferenceCallCount.set(0)
        val service = service(runner, dispatchers, httpCallCount = httpCallCount)

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
        assertEquals("org.example.provider.AiTextService", runner.request.get()?.component?.className)
        assertEquals("example.provider", runner.request.get()?.providerId)
        assertEquals("example-model", runner.request.get()?.modelId)
        assertEquals("prompt", runner.request.get()?.prompt)
        assertEquals(2_500L, runner.request.get()?.timeoutMillis)
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(0, TestContext.preferenceCallCount.get())
        assertEquals(0, httpCallCount.get())

        runner.emit(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()
        assertPluginChatResponse(resolved.get(), "exact local chat answer")
        assertNull(rejected.get())
        assertEquals(1, resolveCount.get())
        assertEquals(0, rejectCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(0, httpCallCount.get())
        service.close()
    }

    @Test
    fun pluginChatFailureRejectsStableRouteCodeWithoutVaultHttpOrFallback() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner()
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        val resolveCount = AtomicInteger()
        val rejectCount = AtomicInteger()
        val httpCallCount = AtomicInteger()
        TestContext.preferenceCallCount.set(0)
        val service = service(runner, dispatchers, httpCallCount = httpCallCount)

        service.chat(pluginInput())
            .onResolve(callback { value ->
                resolveCount.incrementAndGet()
                resolved.set(value)
            })
            .onReject(callback { error ->
                rejectCount.incrementAndGet()
                rejected.set(error)
            })
        runner.emit(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.BINDER_DIED))
        dispatchers.drain()

        assertNull(resolved.get())
        assertEquals(0, resolveCount.get())
        assertEquals(1, rejectCount.get())
        val error = rejected.get() as Map<*, *>
        assertEquals("AiPluginRouteException", error["name"])
        assertEquals("AI plugin chat failed (BINDER_DIED)", error["message"])
        assertEquals("plugin", error["route"])
        assertEquals("BINDER_DIED", error["code"])
        assertEquals(0, TestContext.preferenceCallCount.get())
        assertEquals(0, httpCallCount.get())
        assertEquals(1, runner.startCount.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(AiTextPluginAskOutcome.Completed("late completion must not win"))
        dispatchers.drain()
        assertNull(resolved.get())
        assertEquals(error, rejected.get())
        assertEquals(0, resolveCount.get())
        assertEquals(1, rejectCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(0, httpCallCount.get())
        service.close()
    }

    @Test
    fun synchronousPluginCompletionResolvesExactTextAndClosesHandleOnce() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner(
            synchronousOutcome = AiTextPluginAskOutcome.Completed("exact local answer"),
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
        assertEquals("org.example.provider.AiTextService", runner.request.get()?.component?.className)
        assertEquals("example.provider", runner.request.get()?.providerId)
        assertEquals("example-model", runner.request.get()?.modelId)
        assertEquals("prompt", runner.request.get()?.prompt)
        assertEquals(2_500L, runner.request.get()?.timeoutMillis)
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()
        assertEquals("exact local answer", resolved.get())
        assertNull(rejected.get())
        assertEquals(1, runner.handle.closeCount.get())
        service.close()
    }

    @Test
    fun pluginFailureRejectsWithStableRouteCodeWithoutCloudFallback() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner()
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()
        TestContext.preferenceCallCount.set(0)
        val service = service(runner, dispatchers)

        service.ask(pluginInput())
            .onResolve(callback(resolved::set))
            .onReject(callback(rejected::set))
        runner.emit(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.BINDER_DIED))
        dispatchers.drain()

        assertNull(resolved.get())
        val error = rejected.get() as Map<*, *>
        assertEquals("plugin", error["route"])
        assertEquals("BINDER_DIED", error["code"])
        assertEquals(0, TestContext.preferenceCallCount.get())
        assertEquals(1, runner.startCount.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        service.close()
    }

    @Test
    fun malformedPluginSelectionRejectsWithStableInvalidRequestWithoutCloudFallback() {
        val dispatchers = TestDispatchers()
        val runner = FakePluginAskRunner()
        val askRejected = AtomicReference<Any?>()
        val chatRejected = AtomicReference<Any?>()
        val httpCallCount = AtomicInteger()
        TestContext.preferenceCallCount.set(0)
        val service = service(runner, dispatchers, httpCallCount = httpCallCount)
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
        assertEquals(0, TestContext.preferenceCallCount.get())
        assertEquals(0, httpCallCount.get())
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
        runner.emit(AiTextPluginAskOutcome.Completed("late completion"))
        runner.emit(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()

        assertEquals(1, runner.startCount.get())
        assertEquals(1, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(0, resolveCount.get())
        assertEquals(0, rejectCount.get())
    }

    @Test
    fun pluginStreamFailureUsesExactRouteWithoutVaultOrCloudFallback() {
        val dispatchers = TestDispatchers()
        val askRunner = FakePluginAskRunner()
        val streamRunner = FakePluginStreamRunner()
        val errors = AtomicReference<Any?>()
        TestContext.preferenceCallCount.set(0)
        val service = service(askRunner, dispatchers, streamRunner)

        val stream = service.stream(pluginInput(timeoutMillis = 2_500L))
        stream.once(AiStream.EVENT_ERROR, streamCallback(errors::set))
        streamRunner.emit(AiTextPluginStreamEvent.Failed(AiTextPluginAskFailure.BINDER_DIED))
        dispatchers.drain()

        assertEquals("error", stream.state)
        assertEquals(true, stream.isDone)
        assertEquals(1, streamRunner.startCount.get())
        assertEquals("org.example.provider", streamRunner.request.get()?.component?.packageName)
        assertEquals("org.example.provider.AiTextService", streamRunner.request.get()?.component?.className)
        assertEquals("example.provider", streamRunner.request.get()?.providerId)
        assertEquals("example-model", streamRunner.request.get()?.modelId)
        assertEquals("prompt", streamRunner.request.get()?.prompt)
        assertEquals(2_500L, streamRunner.request.get()?.timeoutMillis)
        val error = (errors.get() as Wrapper).unwrap() as Map<*, *>
        assertEquals("plugin", error["route"])
        assertEquals("BINDER_DIED", error["code"])
        assertEquals(0, TestContext.preferenceCallCount.get())
        assertEquals(0, askRunner.startCount.get())
        assertEquals(0, streamRunner.handle.cancelCount.get())
        assertEquals(1, streamRunner.handle.closeCount.get())

        streamRunner.emit(AiTextPluginStreamEvent.Completed("late cloud fallback must not win"))
        dispatchers.drain()
        assertEquals(1, streamRunner.handle.closeCount.get())
        service.close()
    }

    private fun service(
        runner: FakePluginAskRunner,
        dispatchers: TestDispatchers,
        streamRunner: AiTextPluginStreamRunner = FakePluginStreamRunner(),
        httpCallCount: AtomicInteger = AtomicInteger(),
    ) = AiService(
        context = testContext(),
        scriptRuntime = RhinoTestRuntime.create(TopLevelScope()),
        sourceClient = OkHttpClient(),
        pluginAskRunner = runner,
        pluginDispatcherFactory = dispatchers::create,
        pluginStreamRunner = streamRunner,
    ).also { service ->
        // AiHttpClientFactory intentionally strips source-client interceptors. Install this on the
        // actual per-service client so a cloud fallback cannot turn a zero counter into a false pass.
        val clientField = AiService::class.java.getDeclaredField("client").apply {
            isAccessible = true
        }
        val client = clientField.get(service) as OkHttpClient
        clientField.set(
            service,
            client.newBuilder()
                .addInterceptor { chain ->
                    httpCallCount.incrementAndGet()
                    throw AssertionError("Unexpected cloud HTTP request: ${chain.request().url}")
                }
                .build(),
        )
    }

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
                "route",
                "provider",
                "model",
            ),
            response.keys.toSet(),
        )
        assertEquals(expectedText, response["text"])
        assertEquals("", response["reasoning"])
        assertEquals(emptyList<Any?>(), response["toolCalls"])
        assertNull(response["usage"])
        assertNull(response["finishReason"])
        assertNull(response["message"])
        assertNull(response["error"])
        assertNull(response["raw"])
        assertNull(response["profile"])
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

    private fun testContext(): TestContext {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
        return unsafe.allocateInstance(TestContext::class.java) as TestContext
    }

    private fun pluginInput(timeoutMillis: Long? = null): AiRequestInput {
        val timeout = timeoutMillis?.let { ", \"timeout\": $it" }.orEmpty()
        return AiRequestInput(
            messages = JsonParser.parseString(
                """[{"role":"user","content":"prompt"}]""",
            ).asJsonArray,
            options = JsonParser.parseString(
                """
                {
                  "plugin": {
                    "component": {
                      "packageName": "org.example.provider",
                      "className": "org.example.provider.AiTextService"
                    },
                    "providerId": "example.provider",
                    "modelId": "example-model"
                  }
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

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            return Proxy.newProxyInstance(
                SharedPreferences::class.java.classLoader,
                arrayOf(SharedPreferences::class.java),
            ) { _, method, _ ->
                preferenceCallCount.incrementAndGet()
                throw AssertionError("Unexpected cloud vault access: ${method.name}")
            } as SharedPreferences
        }

        companion object {
            val preferenceCallCount = AtomicInteger()
        }
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
        private val synchronousOutcome: AiTextPluginAskOutcome? = null,
        terminalOnCancel: Boolean = false,
    ) : AiTextPluginAskRunner {
        val startCount = AtomicInteger()
        val request = AtomicReference<AiTextPluginAskRequest?>()
        private val callback = AtomicReference<((AiTextPluginAskOutcome) -> Unit)?>()
        val handle = RecordingHandle {
            if (terminalOnCancel) {
                emit(AiTextPluginAskOutcome.Failed(AiTextPluginAskFailure.CANCELLED))
            }
        }

        override fun start(
            request: AiTextPluginAskRequest,
            onOutcome: (AiTextPluginAskOutcome) -> Unit,
        ): AiTextPluginAskHandle {
            startCount.incrementAndGet()
            this.request.set(request)
            callback.set(onOutcome)
            synchronousOutcome?.let(onOutcome)
            return handle
        }

        fun emit(outcome: AiTextPluginAskOutcome) {
            checkNotNull(callback.get())(outcome)
        }
    }

    private class FakePluginStreamRunner : AiTextPluginStreamRunner {
        val startCount = AtomicInteger()
        val request = AtomicReference<AiTextPluginAskRequest?>()
        private val callback = AtomicReference<((AiTextPluginStreamEvent) -> Unit)?>()
        val handle = RecordingHandle {}

        override fun startStream(
            request: AiTextPluginAskRequest,
            onEvent: (AiTextPluginStreamEvent) -> Unit,
        ): AiTextPluginAskHandle {
            startCount.incrementAndGet()
            this.request.set(request)
            callback.set(onEvent)
            return handle
        }

        fun emit(event: AiTextPluginStreamEvent) {
            checkNotNull(callback.get())(event)
        }
    }

    private class RecordingHandle(
        private val onCancel: () -> Unit,
    ) : AiTextPluginAskHandle {
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
