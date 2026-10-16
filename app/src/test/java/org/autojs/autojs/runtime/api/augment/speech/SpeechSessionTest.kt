package org.autojs.autojs.runtime.api.augment.speech

import org.autojs.autojs.core.plugin.speech.SpeechError
import org.autojs.autojs.core.plugin.speech.SpeechErrorMapper
import org.autojs.autojs.core.plugin.speech.SpeechEvent
import org.autojs.autojs.core.plugin.speech.SpeechPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechResultKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Wrapper
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * The `SpeechSession` and `SpeechWakeSession` emitters with their runners (3-Macaw Speech roadmap P1.4), on the pure-JVM
 * dispatcher seam: the session state machine (`pending` -> `active` -> `ended`), event order and payloads, the single
 * terminal `end`, idempotent `stop` / `cancel` through the bound port, `feed` rules, overflow, the silent script exit,
 * and the dictation rounds of a wake session (created by whichever channel arrives first, ended with the session).
 * zh-CN: 在纯 JVM 分发器接缝上测试 `SpeechSession` 与 `SpeechWakeSession` 发射器及其运行器 (3-Macaw Speech 路线图 P1.4): 会话状态机
 * (`pending` -> `active` -> `ended`), 事件顺序与载荷, 唯一的终结 `end`, 经绑定端口的幂等 `stop` / `cancel`, `feed` 规则, 溢出, 静默的脚本退出,
 * 以及唤醒会话的听写轮次 (由先到的通道创建, 随会话结束).
 */
class SpeechSessionTest {

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

    private class Dispatching(queued: Boolean = false, maxQueuedActions: Int = 1_000) {
        val released = AtomicInteger()
        val scheduled = ArrayDeque<() -> Unit>()
        val dispatcher = ScriptAsyncDispatcher(
            valueMapper = { it },
            scheduler = if (queued) { action -> scheduled.addLast(action) } else { action -> action() },
            releaseWait = { released.incrementAndGet() },
            maxQueuedActions = maxQueuedActions,
        )

        fun drain() {
            while (scheduled.isNotEmpty()) scheduled.removeFirst()()
        }
    }

    private class FakePort : SpeechSession.Port {
        val calls = ArrayList<String>()
        override fun stop() {
            calls += "stop"
        }

        override fun cancel() {
            calls += "cancel"
        }

        override fun feed(pcm: ByteArray) {
            calls += "feed(${pcm.size})"
        }
    }

    private fun listener(action: (Array<out Any?>) -> Unit): Callable =
        RhinoUtils.newBaseFunction(scope, "listener", { args -> action(args) }, RhinoUtils.NOT_CONSTRUCTABLE)

    private fun unwrap(value: Any?): Any? = (value as? Wrapper)?.unwrap() ?: value

    private fun record(emitter: org.autojs.autojs.core.eventloop.EventEmitter, events: MutableList<Pair<String, Any?>>, vararg names: String) {
        names.forEach { name -> emitter.on(name, listener { args -> events += name to args.firstOrNull()?.let(::unwrap) }) }
    }

    private val sessionEvents = arrayOf("ready", "partial", "final", "endpoint", "vad", "warning", "error", "end")

    @Test
    fun theStateMachineRunsPendingActiveEnded() {
        val d = Dispatching()
        val terminal = AtomicInteger()
        val controller = SpeechSession.Controller(d.dispatcher, ScriptBridges(), SpeechContract.SOURCE_MIC) { terminal.incrementAndGet() }
        val finished = ArrayList<SpeechSessionRunner>()
        val runner = SpeechSessionRunner(controller, onFinished = { finished += it })
        val session = controller.session
        val events = ArrayList<Pair<String, Any?>>()
        record(session, events, *sessionEvents)

        assertEquals("pending", session.state)
        assertTrue(session.isActive())
        assertNull(session.id)
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_READY, mapOf(SpeechResultKeys.SESSION_ID to "s1", SpeechResultKeys.MODEL_ID to "asr.zh")))
        assertEquals("active", session.state)
        assertEquals("s1", session.id)
        assertEquals("asr.zh", session.modelId)
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_VAD, mapOf(SpeechResultKeys.ACTIVE to true)))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_PARTIAL, mapOf(SpeechResultKeys.TEXT to "ni", SpeechResultKeys.SEGMENT_INDEX to 0)))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_FINAL, mapOf(SpeechResultKeys.TEXT to "hello", SpeechResultKeys.SEGMENT_INDEX to 0)))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_ENDPOINT))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_FINAL, mapOf(SpeechResultKeys.TEXT to "world", SpeechResultKeys.SEGMENT_INDEX to 1)))
        assertEquals("hello world", session.result())
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_WARNING, mapOf("code" to SpeechErrorCodes.INVALID_OPTIONS, "message" to "hotwords ignored")))
        assertEquals(0, terminal.get())
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_STOPPED, SpeechResultKeys.TEXT to "hello world!")))

        assertEquals(listOf("ready", "vad", "partial", "final", "endpoint", "final", "warning", "end"), events.map { it.first })
        assertEquals(mapOf(SpeechResultKeys.SESSION_ID to "s1", SpeechResultKeys.MODEL_ID to "asr.zh"), events[0].second)
        assertEquals(mapOf(SpeechResultKeys.REASON to "stopped", SpeechResultKeys.TEXT to "hello world!"), events.last().second)
        assertEquals("ended", session.state)
        assertTrue(session.isEnded)
        assertFalse(session.isActive())
        assertEquals("stopped", session.reason)
        assertEquals("hello world!", session.result())
        assertEquals(1, terminal.get())
        assertEquals(1, d.released.get())
        assertTrue(runner.isFinished)
        assertEquals(listOf(runner), finished)

        runner.onEvent(SpeechEvent(SpeechContract.EVENT_FINAL, mapOf(SpeechResultKeys.TEXT to "late")))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_ERROR)))
        assertEquals(8, events.size)
        assertEquals(1, terminal.get())
    }

    @Test
    fun anErrorPrecedesTheEndAndKeepsItsCode() {
        val d = Dispatching()
        val controller = SpeechSession.Controller(d.dispatcher, ScriptBridges(), SpeechContract.SOURCE_MIC)
        val runner = SpeechSessionRunner(controller)
        val events = ArrayList<Pair<String, Any?>>()
        record(controller.session, events, *sessionEvents)
        val denied = SpeechErrorMapper.normalize(SpeechErrorCodes.PERMISSION_DENIED, "no microphone permission")
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_ERROR, error = denied))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_ERROR)))
        assertEquals(listOf("error", "end"), events.map { it.first })
        assertEquals(SpeechErrorCodes.PERMISSION_DENIED, (events[0].second as SpeechError).code)
        assertEquals("error", controller.session.reason)
        assertEquals("", controller.session.result())
    }

    @Test
    fun stopAndCancelGoThroughThePortAndAreIdempotentOnceEnded() {
        val d = Dispatching()
        val controller = SpeechSession.Controller(d.dispatcher, ScriptBridges(), SpeechContract.SOURCE_EXTERNAL)
        val session = controller.session
        assertSame("no port yet: a no-op", session, session.stop())
        val port = FakePort()
        assertTrue(controller.bindPort(port))
        session.stop()
        session.cancel()
        session.feed(ByteArray(4))
        assertEquals(listOf("stop", "cancel", "feed(4)"), port.calls)
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, codeOf { session.feed("not audio") })

        controller.end(SpeechContract.REASON_CANCELLED, null)
        session.stop()
        session.cancel()
        assertEquals(3, port.calls.size)
        assertEquals(SpeechErrorCodes.SESSION_CLOSED, codeOf { session.feed(ByteArray(2)) })
        assertFalse(controller.bindPort(FakePort()))
    }

    @Test
    fun microphoneSessionsRefuseFeed() {
        val controller = SpeechSession.Controller(Dispatching().dispatcher, ScriptBridges(), SpeechContract.SOURCE_MIC)
        controller.bindPort(FakePort())
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, codeOf { controller.session.feed(ByteArray(2)) })
    }

    @Test
    fun overflowReplacesTheQueueWithErrorAndEndOverflow() {
        val d = Dispatching(queued = true, maxQueuedActions = 2)
        val controller = SpeechSession.Controller(d.dispatcher, ScriptBridges(), SpeechContract.SOURCE_MIC)
        val runner = SpeechSessionRunner(controller)
        val events = ArrayList<Pair<String, Any?>>()
        record(controller.session, events, *sessionEvents)
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_READY))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_PARTIAL, mapOf(SpeechResultKeys.TEXT to "a")))
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_PARTIAL, mapOf(SpeechResultKeys.TEXT to "ab")))
        assertTrue(runner.isFinished)
        d.drain()
        assertEquals(listOf("error", "end"), events.map { it.first })
        assertEquals(SpeechErrorCodes.LIMIT_EXCEEDED, (events[0].second as SpeechError).code)
        assertEquals(SpeechSession.REASON_OVERFLOW, controller.session.reason)
    }

    @Test
    fun scriptExitIsSilent() {
        val d = Dispatching()
        val terminal = AtomicInteger()
        val controller = SpeechSession.Controller(d.dispatcher, ScriptBridges(), SpeechContract.SOURCE_MIC) { terminal.incrementAndGet() }
        val runner = SpeechSessionRunner(controller)
        val events = ArrayList<Pair<String, Any?>>()
        record(controller.session, events, *sessionEvents)
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_READY))
        runner.cancelSilently()
        runner.cancelSilently()
        runner.onEvent(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_CANCELLED)))
        assertEquals(listOf("ready"), events.map { it.first })
        assertEquals(SpeechSession.REASON_EXIT, controller.session.reason)
        assertEquals("ended", controller.session.state)
        assertEquals(1, terminal.get())
        assertEquals(1, d.released.get())
    }

    @Test
    fun wakeHitsCarryTheirRoundSessionsWhicheverChannelComesFirst() {
        val d = Dispatching()
        val wakeController = SpeechWakeSession.Controller(d.dispatcher, ScriptBridges(), listOf("hey macaw"))
        val runner = SpeechWakeRunner(
            wakeController,
            thenListen = true,
            newRound = { index -> SpeechSession.Controller(d.dispatcher, ScriptBridges(), SpeechContract.SOURCE_MIC, round = index, ownsDispatcher = false) },
        )
        val wake = wakeController.session
        val wakeEvents = ArrayList<Pair<String, Any?>>()
        record(wake, wakeEvents, "ready", "wake", "warning", "error", "end")

        runner.wakeSink.onEvent(SpeechEvent(SpeechContract.EVENT_READY, mapOf(SpeechResultKeys.SESSION_ID to "w1")))
        assertEquals("active", wake.state)
        // Round 0: the hit arrives before the round's events.
        runner.wakeSink.onEvent(SpeechEvent(SpeechContract.EVENT_WAKE, mapOf(SpeechResultKeys.KEYWORD to "hey macaw", SpeechResultKeys.SCORE to 0.9, SpeechResultKeys.ROUND to 0)))
        val first = (wakeEvents.last().second as Map<*, *>)[SpeechWakeSession.FIELD_LISTEN] as SpeechSession
        assertEquals(0, first.round)
        val firstEvents = ArrayList<Pair<String, Any?>>()
        record(first, firstEvents, *sessionEvents)
        runner.listenSink.onEvent(SpeechEvent(SpeechContract.EVENT_READY, mapOf(SpeechResultKeys.ROUND to 0), round = 0))
        runner.listenSink.onEvent(SpeechEvent(SpeechContract.EVENT_FINAL, mapOf(SpeechResultKeys.TEXT to "open the door", SpeechResultKeys.ROUND to 0), round = 0))
        runner.listenSink.onEvent(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_STOPPED, SpeechResultKeys.ROUND to 0), round = 0))
        assertEquals(listOf("ready", "final", "end"), firstEvents.map { it.first })
        assertEquals(mapOf(SpeechResultKeys.TEXT to "open the door"), firstEvents[1].second)
        assertTrue(first.isEnded)
        assertFalse("a round end is not terminal", wake.isEnded)

        // Round 1: the round's first event arrives before the hit.
        runner.listenSink.onEvent(SpeechEvent(SpeechContract.EVENT_READY, mapOf(SpeechResultKeys.ROUND to 1), round = 1))
        runner.wakeSink.onEvent(SpeechEvent(SpeechContract.EVENT_WAKE, mapOf(SpeechResultKeys.KEYWORD to "hey macaw", SpeechResultKeys.ROUND to 1)))
        val second = (wakeEvents.last().second as Map<*, *>)[SpeechWakeSession.FIELD_LISTEN] as SpeechSession
        assertEquals(1, second.round)
        assertEquals("active", second.state)
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, codeOf { second.feed(ByteArray(2)) })
        assertEquals("the round's stop reaches the wake proxy, which is not bound in this test", SpeechErrorCodes.SESSION_CLOSED, codeOf { second.stop() })

        runner.wakeSink.onEvent(SpeechEvent(SpeechContract.EVENT_END, mapOf(SpeechResultKeys.REASON to SpeechContract.REASON_STOPPED)))
        assertTrue(second.isEnded)
        assertEquals("stopped", second.reason)
        assertTrue(wake.isEnded)
        assertEquals(listOf("ready", "wake", "wake", "end"), wakeEvents.map { it.first })
        assertEquals(1, d.released.get())
        assertTrue(runner.isFinished)
    }

    @Test
    fun wakeSessionsWithoutThenListenCarryNoListenField() {
        val d = Dispatching()
        val wakeController = SpeechWakeSession.Controller(d.dispatcher, ScriptBridges(), listOf("a", "b"))
        val runner = SpeechWakeRunner(wakeController, thenListen = false, newRound = { fail("no rounds"); throw AssertionError() })
        val events = ArrayList<Pair<String, Any?>>()
        record(wakeController.session, events, "wake", "end")
        runner.wakeSink.onEvent(SpeechEvent(SpeechContract.EVENT_WAKE, mapOf(SpeechResultKeys.KEYWORD to "b", SpeechResultKeys.TIMESTAMP_MILLIS to 7)))
        assertEquals(mapOf(SpeechResultKeys.KEYWORD to "b", SpeechResultKeys.SCORE to null, SpeechResultKeys.TIMESTAMP_MILLIS to 7), events[0].second)
        runner.cancelSilently()
        assertEquals(SpeechSession.REASON_EXIT, wakeController.session.reason)
        assertEquals(listOf("wake"), events.map { it.first })
    }

    private inline fun codeOf(block: () -> Any?): String {
        try {
            block()
        } catch (e: SpeechPluginException) {
            return e.code
        }
        fail("expected a SpeechPluginException")
        throw AssertionError()
    }
}
