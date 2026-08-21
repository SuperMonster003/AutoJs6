package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginMessage
import org.autojs.autojs.core.plugin.ai.OnDeviceAiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.OnDeviceAiProviderComponent
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiPluginAskRuntimeTest {

    @Test
    fun synchronousCompletionClosesHandleOnceAndLateFailureIsIgnored() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner(OnDeviceAiPluginAskOutcome.Completed("exact answer"))
        val runtime = AiPluginAskRuntime({ runner }, dispatchers::create)
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()

        runtime.ask(request())
            .onResolve(callback(resolved::set))
            .onReject(callback(rejected::set))
        dispatchers.drain()

        assertEquals("exact answer", resolved.get())
        assertNull(rejected.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())

        runner.emit(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()
        assertEquals("exact answer", resolved.get())
        assertNull(rejected.get())
        assertEquals(1, runner.handle.closeCount.get())
    }

    @Test
    fun runnerIsLazySharedAndAsynchronousCompletionResolvesExactly() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner()
        val providerCalls = AtomicInteger()
        val runtime = AiPluginAskRuntime(
            runnerProvider = {
                providerCalls.incrementAndGet()
                runner
            },
            dispatcherFactory = dispatchers::create,
        )
        val rejectedLocally = AtomicReference<Any?>()
        val first = AtomicReference<Any?>()
        val second = AtomicReference<Any?>()

        assertEquals(0, providerCalls.get())
        runtime.reject(IllegalArgumentException("local rejection"))
            .onReject(callback(rejectedLocally::set))
        dispatchers.drain()
        val localError = rejectedLocally.get() as Map<*, *>
        assertEquals("IllegalArgumentException", localError["name"])
        assertEquals("local rejection", localError["message"])
        assertEquals(0, providerCalls.get())

        runtime.ask(request()).onResolve(callback(first::set))
        assertEquals(1, providerCalls.get())
        runner.emit(OnDeviceAiPluginAskOutcome.Completed("first async answer"))
        dispatchers.drain()
        assertEquals("first async answer", first.get())

        runtime.chat(request()).onResolve(callback(second::set))
        runner.emit(OnDeviceAiPluginAskOutcome.Completed("second async answer"))
        dispatchers.drain()
        assertPluginChatResponse(second.get(), "second async answer")
        assertEquals(1, providerCalls.get())
        assertEquals(2, runner.startCount.get())
        assertEquals(2, runner.handle.closeCount.get())
    }

    @Test
    fun runnerFailureUsesExactBoundedCodeAndDoesNotCancelTerminalHandle() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner()
        val runtime = AiPluginAskRuntime({ runner }, dispatchers::create)
        val rejected = AtomicReference<Any?>()

        runtime.ask(request()).onReject(callback(rejected::set))
        runner.emit(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.BUSY))
        dispatchers.drain()

        val error = rejected.get() as Map<*, *>
        assertEquals("AiPluginRouteException", error["name"])
        assertEquals("AI plugin ask failed (BUSY)", error["message"])
        assertEquals("plugin", error["route"])
        assertEquals("BUSY", error["code"])
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
    }

    @Test
    fun closeCancelsAndClosesOnceWhileReentrantAndLateTerminalsStaySilent() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner(terminalOnCancel = true)
        val runtime = AiPluginAskRuntime({ runner }, dispatchers::create)
        val resolveCount = AtomicInteger()
        val rejectCount = AtomicInteger()

        runtime.chat(request())
            .onResolve(callback { resolveCount.incrementAndGet() })
            .onReject(callback { rejectCount.incrementAndGet() })
        runtime.close()
        runtime.close()
        runner.emit(OnDeviceAiPluginAskOutcome.Completed("late"))
        runner.emit(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.PROVIDER_FAILED))
        dispatchers.drain()

        assertEquals(1, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(0, resolveCount.get())
        assertEquals(0, rejectCount.get())
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

    private fun callback(action: (Any?) -> Unit) = object : ScriptPromiseAdapter.Callback {
        override fun call(arg: Any?) = action(arg)
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

    private class FakeRunner(
        private val synchronousOutcome: OnDeviceAiPluginAskOutcome? = null,
        terminalOnCancel: Boolean = false,
    ) : OnDeviceAiPluginAskRunner {
        val startCount = AtomicInteger()
        private val callback = AtomicReference<((OnDeviceAiPluginAskOutcome) -> Unit)?>()
        val handle = RecordingHandle {
            if (terminalOnCancel) {
                emit(OnDeviceAiPluginAskOutcome.Failed(OnDeviceAiPluginAskFailure.CANCELLED))
            }
        }

        override fun start(
            request: OnDeviceAiPluginAskRequest,
            onOutcome: (OnDeviceAiPluginAskOutcome) -> Unit,
        ): OnDeviceAiPluginAskHandle {
            startCount.incrementAndGet()
            callback.set(onOutcome)
            synchronousOutcome?.let(onOutcome)
            return handle
        }

        fun emit(outcome: OnDeviceAiPluginAskOutcome) {
            checkNotNull(callback.get())(outcome)
        }
    }

    private class RecordingHandle(
        private val onCancel: () -> Unit,
    ) : OnDeviceAiPluginAskHandle {
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
