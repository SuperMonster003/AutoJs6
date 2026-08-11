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
        val rejected = AtomicReference<Any?>()
        TestContext.preferenceCallCount.set(0)
        val service = service(runner, dispatchers)
        val input = AiRequestInput(
            messages = JsonParser.parseString(
                """[{"role":"user","content":"prompt"}]""",
            ).asJsonArray,
            options = JsonParser.parseString("""{"plugin":null}""").asJsonObject,
        )

        service.ask(input).onReject(callback(rejected::set))
        dispatchers.drain()

        val error = rejected.get() as Map<*, *>
        assertEquals("plugin", error["route"])
        assertEquals("INVALID_REQUEST", error["code"])
        assertEquals("AI plugin request is invalid", error["message"])
        assertEquals(0, runner.startCount.get())
        assertEquals(0, TestContext.preferenceCallCount.get())
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
    ) = AiService(
        context = testContext(),
        scriptRuntime = RhinoTestRuntime.create(TopLevelScope()),
        sourceClient = OkHttpClient(),
        pluginAskRunner = runner,
        pluginDispatcherFactory = dispatchers::create,
        pluginStreamRunner = streamRunner,
    )

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
