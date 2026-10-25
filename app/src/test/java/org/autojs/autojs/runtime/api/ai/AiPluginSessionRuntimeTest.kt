package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginAskHandle
import org.autojs.autojs.core.plugin.ai.AiPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginAskRequest
import org.autojs.autojs.core.plugin.ai.AiPluginMessage
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiPluginSession
import org.autojs.autojs.core.plugin.ai.AiPluginSessionOpenOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginSessionRunner
import org.autojs.autojs.core.plugin.ai.AiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiPluginSessionRuntimeTest {

    @Test
    fun synchronousOpenAttachesOnceAndResolvesPublicTarget() {
        val dispatchers = TestDispatchers()
        val session = FakeSession()
        val runner = FakeRunner(AiPluginSessionOpenOutcome.Completed(session))
        val target = FakeTarget()
        val runtime = AiPluginSessionRuntime({ runner }, dispatchers::create)
        val resolved = AtomicReference<Any?>()
        val rejected = AtomicReference<Any?>()

        runtime.open(request(), target)
            .onResolve(callback(resolved::set))
            .onReject(callback(rejected::set))
        dispatchers.drain()

        assertEquals("public-session", resolved.get())
        assertNull(rejected.get())
        assertEquals(session, target.session.get())
        assertEquals(1, target.attachCount.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(0, session.closeCount.get())
    }

    @Test
    fun failureClosesTargetAndUsesStablePluginError() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner()
        val target = FakeTarget()
        val runtime = AiPluginSessionRuntime({ runner }, dispatchers::create)
        val rejected = AtomicReference<Any?>()

        runtime.open(request(), target).onReject(callback(rejected::set))
        runner.emit(AiPluginSessionOpenOutcome.Failed(AiPluginAskFailure.BUSY))
        dispatchers.drain()

        val error = rejected.get() as Map<*, *>
        assertEquals("AiPluginRouteException", error["name"])
        assertEquals("BUSY", error["code"])
        assertEquals("plugin", error["route"])
        assertEquals(1, target.closeCount.get())
        assertEquals(0, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
    }

    @Test
    fun closeCancelsOpeningAndClosesLateSessionWithoutPublishing() {
        val dispatchers = TestDispatchers()
        val runner = FakeRunner()
        val target = FakeTarget()
        val runtime = AiPluginSessionRuntime({ runner }, dispatchers::create)
        val resolved = AtomicInteger()
        val rejected = AtomicInteger()

        runtime.open(request(), target)
            .onResolve(callback { resolved.incrementAndGet() })
            .onReject(callback { rejected.incrementAndGet() })
        runtime.close()
        val lateSession = FakeSession()
        runner.emit(AiPluginSessionOpenOutcome.Completed(lateSession))
        dispatchers.drain()

        assertEquals(0, resolved.get())
        assertEquals(0, rejected.get())
        assertEquals(1, target.closeCount.get())
        assertEquals(1, runner.handle.cancelCount.get())
        assertEquals(1, runner.handle.closeCount.get())
        assertEquals(1, lateSession.closeCount.get())
    }

    private fun request() = AiPluginAskRequest(
        component = AiProviderComponent("org.example", "org.example.Provider"),
        providerId = "example.provider",
        targetId = "local:example-model",
        messages = listOf(AiPluginMessage(AiPluginMessageRole.USER, "-")),
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
        private val synchronous: AiPluginSessionOpenOutcome? = null,
    ) : AiPluginSessionRunner {
        private val callback = AtomicReference<((AiPluginSessionOpenOutcome) -> Unit)?>()
        val handle = RecordingHandle()

        override fun openSession(
            request: AiPluginAskRequest,
            onOutcome: (AiPluginSessionOpenOutcome) -> Unit,
        ): AiPluginAskHandle {
            callback.set(onOutcome)
            synchronous?.let(onOutcome)
            return handle
        }

        fun emit(outcome: AiPluginSessionOpenOutcome) = checkNotNull(callback.get())(outcome)
    }

    private class FakeTarget : AiPluginSessionTarget {
        override val publicValue: Any = "public-session"
        val session = AtomicReference<AiPluginSession?>()
        val attachCount = AtomicInteger()
        val closeCount = AtomicInteger()
        private val closed = AtomicBoolean(false)

        override fun attach(value: AiPluginSession): Boolean {
            attachCount.incrementAndGet()
            return !closed.get() && session.compareAndSet(null, value)
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            closeCount.incrementAndGet()
            session.getAndSet(null)?.close()
        }
    }

    private class FakeSession : AiPluginSession {
        override val providerId = "example.provider"
        override val target = testPluginTarget()
        override val backendProfile: String? = null
        override val isClosed: Boolean
            get() = closeCount.get() > 0
        val closeCount = AtomicInteger()

        override fun startTurn(
            message: AiPluginMessage,
            streaming: Boolean,
            onOutcome: ((AiPluginAskOutcome) -> Unit)?,
            onStreamEvent: ((AiPluginStreamEvent) -> Unit)?,
        ): AiPluginAskHandle = RecordingHandle()

        override fun close() {
            closeCount.incrementAndGet()
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
