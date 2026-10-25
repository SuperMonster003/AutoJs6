package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginProviderFailureReason
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiPluginMessage
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
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
        val runner = FakeRunner(testPluginCompletion("exact answer", usage()))
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

        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.PROVIDER_FAILED))
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
        runner.emit(testPluginCompletion("first async answer", usage()))
        dispatchers.drain()
        assertEquals("first async answer", first.get())

        runtime.chat(request()).onResolve(callback(second::set))
        runner.emit(testPluginCompletion("second async answer", usage()))
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
        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.BUSY))
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
    fun providerDiagnosticsLeaveThePublicAskErrorUnchanged() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner()
        val runtime = AiPluginAskRuntime({ runner }, dispatchers::create)
        val rejected = AtomicReference<Any?>()

        runtime.ask(request()).onReject(callback(rejected::set))
        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.PROVIDER_FAILED,
            AiPluginProviderFailureReason.ONLINE_NETWORK_UNAVAILABLE))
        dispatchers.drain()

        val error = rejected.get() as Map<*, *>
        assertEquals("AiPluginRouteException", error["name"])
        assertEquals("AI plugin ask failed (PROVIDER_FAILED)", error["message"])
        assertEquals("PROVIDER_FAILED", error["code"])
        assertNull(error["providerReason"])
        assertNull(error["providerCode"])
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
        runner.emit(testPluginCompletion("late", usage()))
        runner.emit(AiPluginAskOutcome.Failed(AiPluginAskFailure.PROVIDER_FAILED))
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
        val usage = response["usage"] as Map<*, *>
        assertEquals(12L, usage["inputTokens"])
        assertEquals(3L, usage["outputTokens"])
        assertEquals(15L, usage["totalTokens"])
        assertNull(usage["reasoningTokens"])
        assertNull(usage["cachedInputTokens"])
        assertEquals(250L, usage["durationMillis"])
        assertNull(usage["raw"])
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

    private fun usage() = AiPluginUsage(
        inputTokens = 12,
        outputTokens = 3,
        totalTokens = 15,
        durationMillis = 250,
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
        private val synchronousOutcome: AiPluginAskOutcome? = null,
        terminalOnCancel: Boolean = false,
    ) : AiPluginAskRunner {
        val startCount = AtomicInteger()
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
            callback.set(onOutcome)
            synchronousOutcome?.let(onOutcome)
            return handle
        }

        fun emit(outcome: AiPluginAskOutcome) {
            checkNotNull(callback.get())(outcome)
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
