package org.autojs.autojs.core.plugin.speech

import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.ServiceBindingLease
import org.autojs.plugin.speech.api.ISpeechPlugin
import org.autojs.plugin.speech.api.ISpeechSession
import org.autojs.plugin.speech.api.ISpeechSessionCallback
import org.autojs.plugin.speech.api.ISpeechWakeCallback
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

/**
 * A refused or abandoned open leaves no gate leading to the script (docs/memory/ROADMAP.md H30, Q50): the fake plugin keeps
 * the callbacks it was handed, as the plugin process keeps the callback Binders, so a gate that still held its sink would
 * pin the script runtime. A session or an operation the plugin opens after the timeout or after the cancellation of the
 * opener is cancelled and its binding released (Q55). The open helpers follow `SpeechPluginHost.openSession` /
 * `openWakeSession`; the Binder adapters themselves need a device, so plain forwarding callbacks stand in for them.
 * zh-CN: 被拒绝或被放弃的打开不留下通向脚本的闸门 (docs/memory/ROADMAP.md H30, Q50): 假插件保留交给它的回调, 正如插件进程保留回调 Binder,
 * 因此仍持有 sink 的闸门会钉住脚本运行时. 插件在超时或打开者被取消之后才打开的会话或操作被取消, 其绑定被释放 (Q55). 打开辅助函数照搬
 * `SpeechPluginHost.openSession` / `openWakeSession`; Binder 适配器本身需要设备, 由直接转发的回调代替.
 */
class SpeechSessionOpeningTest {

    private val received = ArrayList<SpeechEvent>()
    private val sink = SpeechEventSink { received += it }
    private val unbinds = AtomicInteger()
    private val bindingLease = ServiceBindingLease { unbinds.incrementAndGet() }.also { it.markRegistered() }

    @Test
    fun aRefusedOpenDiscardsTheGateAndRethrows() = runBlocking {
        val refusal = IllegalStateException("${SpeechErrorCodes.MODEL_MISSING}: no installed model")
        val plugin = FakePlugin { throw refusal }
        val gate = SpeechEventGate(sink)
        val thrown = expectFailure { openListen(plugin, gate) }
        assertSame("the caller gets the exception lease() made of the refusal", refusal, thrown.cause)
        assertEquals(SpeechErrorCodes.MODEL_MISSING, (thrown as SpeechPluginException).code)
        assertTrue(gate.isEnded)
        assertFalse("the gate no longer leads to the script", gate.holdsSink)
        plugin.fire("onError")
        plugin.fire("onEnd")
        assertTrue("nothing reaches the sink, not even a synthetic end", received.isEmpty())
        assertEquals(2, gate.droppedCount)
    }

    @Test
    fun aPluginThatOpensNoSessionDiscardsTheGate() = runBlocking {
        val plugin = FakePlugin { null }
        val gate = SpeechEventGate(sink)
        val thrown = expectFailure { openListen(plugin, gate) }
        assertEquals(SpeechErrorCodes.INTERNAL, (thrown as SpeechPluginException).code)
        assertEquals("${SpeechErrorCodes.INTERNAL}: the plugin opened no session", thrown.message)
        assertFalse(gate.holdsSink)
        plugin.fire("onEnd")
        assertTrue(received.isEmpty())
    }

    @Test
    fun aRefusedWakeOpenDiscardsBothGates() = runBlocking {
        val plugin = FakePlugin { throw IllegalStateException("${SpeechErrorCodes.MODEL_MISSING}: no installed model") }
        val wakeGate = SpeechEventGate(sink)
        val listenGate = SpeechEventGate(sink)
        val thrown = expectFailure { openWake(plugin, wakeGate, listenGate) }
        assertEquals(SpeechErrorCodes.MODEL_MISSING, (thrown as SpeechPluginException).code)
        assertEquals("the plugin kept the wake and the listen callback", 2, plugin.callbacks.size)
        assertFalse(wakeGate.holdsSink)
        assertFalse("the thenListen gate is discarded too", listenGate.holdsSink)
        plugin.fire("onReady")
        assertTrue(received.isEmpty())
    }

    @Test
    fun aSessionOpenedForACancelledOpenerIsCancelledAndReleased() = runBlocking {
        val session = FakeSession()
        lateinit var opener: Job
        val plugin = FakePlugin { opener.cancel(); session.api }
        val gate = SpeechEventGate(sink)
        var failure: Throwable? = null
        var proxy: SpeechSessionProxy? = null
        opener = launch(Dispatchers.Default, CoroutineStart.LAZY) {
            try {
                proxy = openListen(plugin, gate)
            } catch (t: Throwable) {
                failure = t
                throw t
            }
        }
        opener.join()
        assertNull("withContext discarded the opened proxy", proxy)
        assertTrue(failure is CancellationException)
        assertEquals("the orphaned plugin session is cancelled", 1, session.cancels.get())
        assertEquals("its binding is released", 1, unbinds.get())
        assertFalse(gate.holdsSink)
        plugin.fire("onEnd")
        assertTrue("a cancelled opener hears nothing", received.isEmpty())
    }

    @Test
    fun aSuccessfulOpenKeepsTheGateUntilTheEnd() = runBlocking {
        val session = FakeSession()
        val plugin = FakePlugin { session.api }
        val gate = SpeechEventGate(sink)
        val proxy = openListen(plugin, gate)
        assertFalse(proxy.isReleased)
        assertFalse(proxy.isEnded)
        assertTrue(gate.holdsSink)
        assertFalse(gate.isEnded)
        assertEquals(0, session.cancels.get())
        assertEquals(0, unbinds.get())
        plugin.fire("onReady")
        plugin.fire("onEnd")
        assertEquals(listOf(SpeechContract.EVENT_READY, SpeechContract.EVENT_END), received.map { it.type })
        assertFalse("the end dropped the sink (H23)", gate.holdsSink)
        proxy.release()
        assertEquals(1, unbinds.get())
    }

    @Test
    fun aSessionOpenedAfterTheTimeoutIsCancelledAndReleased() = runBlocking {
        val session = FakeSession()
        val plugin = FakePlugin { Thread.sleep(LATE_MS); session.api }
        val gate = SpeechEventGate(sink)
        val thrown = expectFailure { openListenWithin(plugin, gate, TIMEOUT_MS) }
        assertEquals("the script still gets TIMEOUT", SpeechErrorCodes.TIMEOUT, (thrown as SpeechPluginException).code)
        assertEquals("the late plugin session is cancelled", 1, session.cancels.get())
        assertEquals("its binding is released", 1, unbinds.get())
        assertFalse(gate.holdsSink)
        plugin.fire("onEnd")
        assertTrue(received.isEmpty())
    }

    @Test
    fun aSessionOpenedAfterTheOpenerIsCancelledIsCancelledAndReleased() = runBlocking {
        val session = FakeSession()
        lateinit var opener: Job
        val plugin = FakePlugin { opener.cancel(); session.api }
        val gate = SpeechEventGate(sink)
        var failure: Throwable? = null
        var proxy: SpeechSessionProxy? = null
        opener = launch(Dispatchers.Default, CoroutineStart.LAZY) {
            try {
                proxy = openListenWithin(plugin, gate, LONG_TIMEOUT_MS)
            } catch (t: Throwable) {
                failure = t
                throw t
            }
        }
        opener.join()
        assertNull(proxy)
        assertTrue(failure is CancellationException)
        assertEquals("the late plugin session is cancelled", 1, session.cancels.get())
        assertEquals("its binding is released", 1, unbinds.get())
        assertFalse(gate.holdsSink)
    }

    @Test
    fun anOperationStartedAfterTheTimeoutIsCancelledAndReleased() = runBlocking {
        val abandoned = ArrayList<Int>()
        val thrown = expectFailure {
            withContext(Dispatchers.IO) {
                SpeechSessionOpening.lease(TIMEOUT_MS, { opId: Int -> abandoned += opId }) {
                    delay(1)
                    Thread.sleep(LATE_MS)
                    AidlPluginHost.LeasedCall(7, bindingLease)
                }
            }
        }
        assertTrue(thrown is TimeoutCancellationException)
        assertEquals("the late operation is cancelled in the plugin", listOf(7), abandoned)
        assertEquals("its binding is released", 1, unbinds.get())
    }

    @Test
    fun aSessionOpenedInTimeIsLeftToTheCaller() = runBlocking {
        val session = FakeSession()
        val plugin = FakePlugin { session.api }
        val gate = SpeechEventGate(sink)
        val proxy = openListenWithin(plugin, gate, LONG_TIMEOUT_MS)
        assertFalse(proxy.isReleased)
        assertTrue(gate.holdsSink)
        assertEquals(0, session.cancels.get())
        assertEquals(0, unbinds.get())
        proxy.release()
        assertEquals(1, unbinds.get())
    }

    @Test
    fun discardEndsTheChannelWithoutDelivering() {
        val gate = SpeechEventGate(sink)
        gate.discard()
        gate.closeLocally(SpeechContract.REASON_CANCELLED)
        assertFalse(gate.accept(SpeechEvent(SpeechContract.EVENT_END)))
        assertTrue(gate.isEnded)
        assertFalse(gate.holdsSink)
        assertTrue(received.isEmpty())
    }

    // The steps of SpeechPluginHost.openSession after the feature checks, with lease() mapping a failure for the OPEN phase.
    // zh-CN: SpeechPluginHost.openSession 在特性检查之后的步骤, lease() 按 OPEN 阶段映射失败.
    private suspend fun openListen(plugin: FakePlugin, gate: SpeechEventGate): SpeechSessionProxy =
        SpeechSessionOpening.open(listOf(gate)) { opened ->
            withContext(Dispatchers.IO) {
                val callback = forwarding<ISpeechSessionCallback>(gate)
                val session = lease { SpeechSessionOpening.requireOpened(plugin.api.openSession(null, callback), "session") }
                SpeechSessionProxy(AidlPluginHost.LeasedCall(session, bindingLease), gate, external = false).also(opened)
            }
        }

    private suspend fun openWake(plugin: FakePlugin, gate: SpeechEventGate, listenGate: SpeechEventGate): SpeechWakeSessionProxy =
        SpeechSessionOpening.open(listOfNotNull(gate, listenGate)) { opened ->
            withContext(Dispatchers.IO) {
                val wakeCallback = forwarding<ISpeechWakeCallback>(gate)
                val listenCallback = forwarding<ISpeechSessionCallback>(listenGate)
                val session = lease {
                    SpeechSessionOpening.requireOpened(plugin.api.openWakeSession(null, wakeCallback, listenCallback), "wake session")
                }
                SpeechWakeSessionProxy(AidlPluginHost.LeasedCall(session, bindingLease), gate, listenGate).also(opened)
            }
        }

    // openListen through SpeechSessionOpening.lease with the error mapping of SpeechPluginHost.lease(); the call suspends
    // before the plugin answers, as it does while the dedicated binding connects.
    // zh-CN: 经 SpeechSessionOpening.lease 的 openListen, 错误映射同 SpeechPluginHost.lease(); 调用在插件应答前挂起, 正如专用绑定连接期间.
    private suspend fun openListenWithin(plugin: FakePlugin, gate: SpeechEventGate, timeoutMs: Long): SpeechSessionProxy =
        SpeechSessionOpening.open(listOf(gate)) { opened ->
            withContext(Dispatchers.IO) {
                val callback = forwarding<ISpeechSessionCallback>(gate)
                val leased = try {
                    SpeechSessionOpening.lease(timeoutMs, ISpeechSession::cancel) {
                        delay(1)
                        AidlPluginHost.LeasedCall(SpeechSessionOpening.requireOpened(plugin.api.openSession(null, callback), "session"), bindingLease)
                    }
                } catch (e: TimeoutCancellationException) {
                    throw SpeechErrorMapper.failure(SpeechErrorCodes.TIMEOUT, "the speech plugin did not open the request within $timeoutMs ms", e)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    throw SpeechErrorMapper.wrap(t, SpeechErrorMapper.Phase.OPEN)
                }
                SpeechSessionProxy(leased, gate, external = false).also(opened)
            }
        }

    private inline fun <T> lease(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw SpeechErrorMapper.wrap(t, SpeechErrorMapper.Phase.OPEN)
    }

    private suspend fun expectFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (t: Throwable) {
            return t
        }
        fail("the open should have failed")
        throw AssertionError()
    }

    /** Keeps every callback it is handed, as the plugin process keeps the callback Binders. zh-CN: 保留交给它的每个回调, 正如插件进程保留回调 Binder. */
    private class FakePlugin(private val answer: () -> ISpeechSession?) {

        val callbacks = ArrayList<Any>()

        val api: ISpeechPlugin = fake { name, args ->
            when (name) {
                "openSession", "openWakeSession" -> {
                    callbacks.addAll(args.drop(1).filterNotNull())
                    answer()
                }
                else -> throw UnsupportedOperationException(name)
            }
        }

        /** Calls [method] on every kept callback, as a late plugin would. zh-CN: 像迟到的插件那样对每个保留的回调调用 [method]. */
        fun fire(method: String) {
            callbacks.forEach { callback ->
                callback.javaClass.interfaces.first().methods.first { it.name == method }.let { it.invoke(callback, *arrayOfNulls(it.parameterCount)) }
            }
        }
    }

    private class FakeSession {

        val cancels = AtomicInteger()

        private val binder: IBinder = fake { name, _ ->
            when (name) {
                "unlinkToDeath", "isBinderAlive", "pingBinder" -> true
                else -> null
            }
        }

        val api: ISpeechSession = fake { name, _ ->
            when (name) {
                "asBinder" -> binder
                "cancel" -> cancels.incrementAndGet().let { null }
                "isActive" -> true
                else -> null
            }
        }
    }

    private companion object {

        const val TIMEOUT_MS = 50L
        const val LONG_TIMEOUT_MS = 10_000L

        /** How long a late plugin takes to answer, well past [TIMEOUT_MS]. zh-CN: 迟到的插件应答所用的时长, 远超 [TIMEOUT_MS]. */
        const val LATE_MS = 300L

        inline fun <reified T> fake(crossinline handler: (name: String, args: Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    "toString" -> "fake ${T::class.java.simpleName}"
                    else -> handler(method.name, args ?: emptyArray())
                }
            } as T

        // Stands in for SpeechSessionCallbackBinder / SpeechWakeCallbackBinder: onReady -> ready, onEnd -> end, and so on.
        // zh-CN: 代替 SpeechSessionCallbackBinder / SpeechWakeCallbackBinder: onReady -> ready, onEnd -> end 等.
        inline fun <reified T> forwarding(gate: SpeechEventGate): T = fake<T> { name, _ ->
            gate.accept(SpeechEvent(name.removePrefix("on").lowercase()))
            null
        }
    }
}
