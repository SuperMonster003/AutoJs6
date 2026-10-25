package org.autojs.autojs.runtime.api.modules.mail

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.mail.MailAccountSpec
import org.autojs.autojs.core.plugin.mail.MailCallListener
import org.autojs.autojs.core.plugin.mail.MailError
import org.autojs.autojs.core.plugin.mail.MailErrorMapper
import org.autojs.autojs.core.plugin.mail.MailJson
import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.autojs.core.plugin.mail.MailSessionClient
import org.autojs.autojs.core.plugin.mail.MailSessionEvents
import org.autojs.autojs.core.plugin.mail.MailSessionOpener
import org.autojs.autojs.core.plugin.mail.MailSessionPort
import org.autojs.autojs.core.plugin.mail.MailWatchBridge
import org.autojs.autojs.core.plugin.mail.MailWatchHandle
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.autojs.autojs.rhino.TopLevelScope
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * `MailWatchRunner` (mail roadmap P5) against a fake session port: plugin events become emitter
 * events, `stop` ends with one `close`, a plugin death reports `PLUGIN_UNAVAILABLE` and watches
 * again with the next generation (or closes with `reconnect: false`), re-watch failures retry
 * while retryable, `AUTH_FAILED` with a token provider refreshes and watches again, and a script
 * that stops draining gets `LIMIT_EXCEEDED` and `close(overflow)`.
 */
class MailWatchRunnerTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private val coroutines = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        coroutines.cancel()
        Context.exit()
    }

    private class FakeWatch(val optionsJson: String, val bridge: MailWatchBridge) {
        @Volatile
        var stopped = false
    }

    private class FakePort(val statusJson: String) : MailSessionPort {
        val watches = ArrayList<FakeWatch>()
        var refuseWith: MailError? = null

        @Volatile
        var closed = false

        override fun status(): MailJson.Status = MailJson.Status(MailContract.STATE_OPEN, null, null, """{"state":"open"}""")

        override fun call(request: MailJson.Request, descriptors: Array<ParcelFileDescriptor>?, listener: MailCallListener) {
            listener.onResult(MailJson.Response(request.id, ok = true, result = "{}", error = null))
        }

        override fun cancel(requestId: String) = Unit

        override fun watch(optionsJson: String, bridge: MailWatchBridge): MailWatchHandle {
            refuseWith?.let { throw MailPluginException(it) }
            val watch = FakeWatch(optionsJson, bridge)
            synchronized(watches) { watches += watch }
            return object : MailWatchHandle {
                override fun status(): MailJson.Status = MailJson.Status(MailContract.STATE_OPEN, null, null, statusJson)
                override fun stop() {
                    bridge.close()
                    watch.stopped = true
                }
            }
        }

        override fun close() {
            closed = true
        }

        fun last(): FakeWatch = synchronized(watches) { watches.last() }
    }

    private class FakeOpener(private val statusJson: String = """{"state":"open","mode":"idle"}""") : MailSessionOpener {
        val ports = ArrayList<FakePort>()
        val failures = ArrayDeque<Throwable>()

        @Volatile
        var events: MailSessionEvents? = null

        override suspend fun open(account: MailAccountSpec, events: MailSessionEvents): MailSessionPort {
            synchronized(failures) { failures.pollFirst() }?.let { throw it }
            this.events = events
            return FakePort(statusJson).also { synchronized(ports) { ports += it } }
        }

        fun port(index: Int): FakePort = synchronized(ports) { ports[index] }

        val portCount: Int get() = synchronized(ports) { ports.size }

        /** The plugin process died: what `BinderMailSessionPort` reports through the death recipient. */
        fun die() {
            port(portCount - 1).closed = true
            requireNotNull(events).onClosed(MailErrorMapper.normalize(MailErrorCodes.SESSION_CLOSED, "mail plugin process died", retryable = true))
        }
    }

    private inner class Harness(
        opener: FakeOpener = FakeOpener(),
        tokenProvider: (suspend () -> String)? = null,
        account: MailAccountSpec = MailAccountSpec.inline("""{"address":"alice@example.org"}""", password = "secret"),
        reconnect: Boolean = true,
        queued: Boolean = false,
        maxQueuedActions: Int = 1_000,
    ) {
        val opener = opener
        val client = MailSessionClient(account, opener, tokenProvider)
        val generations = AtomicLong(0L)
        val released = AtomicInteger()
        val scheduled = ArrayDeque<() -> Unit>()
        val dispatcher = ScriptAsyncDispatcher(
            valueMapper = { it },
            scheduler = if (queued) { action -> synchronized(scheduled) { scheduled.addLast(action) } } else { action -> action() },
            releaseWait = { released.incrementAndGet() },
            maxQueuedActions = maxQueuedActions,
        )
        val controller = MailWatch.Controller(dispatcher, ScriptBridges(), "INBOX", null)
        val finished = AtomicInteger()
        val events = java.util.Collections.synchronizedList(ArrayList<Pair<String, Any?>>())
        val runner = MailWatchRunner(
            scope = coroutines,
            session = client,
            options = MailJson.WatchOptions("INBOX", null, null),
            reconnect = reconnect,
            generations = generations::incrementAndGet,
            controller = controller,
            decorate = { plain -> "message:${(plain as? Map<*, *>)?.get("uid")}" },
            convertError = { error -> "error:${error.code}" },
            onFinished = { finished.incrementAndGet() },
            retryDelayMs = { 5L },
        )

        init {
            listOf(MailWatch.EVENT_MESSAGE, MailWatch.EVENT_MODE, MailWatch.EVENT_RESYNC, MailWatch.EVENT_ERROR, MailWatch.EVENT_CLOSE).forEach { name ->
                controller.watch.on(name, listener { args -> events += name to args[0] })
            }
        }

        fun open() = runBlocking { runner.open() }

        fun types(): List<String> = events.toList().map { it.first }

        fun drain() {
            while (true) {
                val action = synchronized(scheduled) { scheduled.pollFirst() } ?: return
                action()
            }
        }
    }

    private fun listener(action: (Array<out Any?>) -> Unit): Callable =
        RhinoUtils.newBaseFunction(scope, "listener", { args -> action(args) }, RhinoUtils.NOT_CONSTRUCTABLE)

    private fun await(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!condition()) {
            if (System.nanoTime() > deadline) fail("$what did not happen within $timeoutMs ms")
            Thread.sleep(5)
        }
    }

    private fun event(type: String, seq: Long, generation: Long, extra: String = ""): String =
        """{"type":"$type","seq":$seq,"generation":$generation,"folder":"INBOX"$extra}"""

    @Test
    fun pluginEventsBecomeEmitterEventsAndStopEndsWithOneClose() {
        val h = Harness()
        h.open()
        val port = h.opener.port(0)
        val watch = port.last()
        assertEquals(1L, watch.bridge.generation)
        assertEquals(1L, h.controller.watch.generation)
        assertEquals("idle", h.controller.watch.mode)
        assertTrue(watch.optionsJson.contains("\"generation\":1"))
        assertTrue(h.events.isEmpty())

        assertTrue(watch.bridge.accept(1L, 1L, event("message", 1, 1, ""","message":{"uid":41,"subject":"hi"}""")))
        assertTrue(watch.bridge.accept(1L, 2L, event("mode", 2, 1, ""","mode":"poll"""")))
        assertTrue(watch.bridge.accept(1L, 3L, event("resync", 3, 1, ""","reason":"uidvalidity-changed"""")))
        assertTrue(watch.bridge.accept(1L, 4L, event("error", 4, 1, ""","error":{"code":"CONNECT_FAILED","message":"x","retryable":true}""")))
        assertEquals(listOf("message", "mode", "resync", "error"), h.types())
        assertEquals("message:41", h.events[0].second)
        assertEquals("poll", h.events[1].second)
        assertEquals("poll", h.controller.watch.mode)
        assertEquals("error:CONNECT_FAILED", h.events[3].second)

        h.controller.watch.stop()
        assertTrue(watch.stopped)
        assertEquals("stopped", h.controller.watch.reason)
        assertTrue(h.runner.isFinished)
        assertEquals(1, h.finished.get())
        // the plugin's own closed event after stop is dropped by the closed bridge
        assertFalse(watch.bridge.accept(1L, 5L, event("closed", 5, 1, ""","reason":"stopped"""")))
        assertEquals(listOf("message", "mode", "resync", "error", "close"), h.types())
        assertEquals(1, h.released.get())
        h.controller.watch.stop()
        assertEquals(1, h.finished.get())
    }

    @Test
    fun thePluginsClosedEventClosesTheEmitterWithItsReason() {
        val h = Harness()
        h.open()
        val watch = h.opener.port(0).last()
        assertTrue(watch.bridge.accept(1L, 1L, event("closed", 1, 1, ""","reason":"closed"""")))
        assertEquals(listOf("close"), h.types())
        assertEquals("closed", h.events[0].second)
        assertTrue(h.runner.isFinished)
        assertFalse(watch.stopped)
    }

    @Test
    fun aPluginDeathReportsPluginUnavailableAndWatchesAgainWithTheNextGeneration() {
        val h = Harness()
        h.open()
        val first = h.opener.port(0).last()
        assertTrue(first.bridge.accept(1L, 1L, event("message", 1, 1, ""","message":{"uid":1}""")))

        h.opener.die()
        await("the second watch") { h.opener.portCount == 2 && h.opener.port(1).watches.isNotEmpty() && h.runner.openCount == 2 }
        await("the resync event") { h.types().contains("resync") }
        val second = h.opener.port(1).last()
        assertEquals(2L, second.bridge.generation)
        assertEquals(2L, h.controller.watch.generation)
        assertEquals(listOf("message", "error", "resync"), h.types())
        assertEquals("error:PLUGIN_UNAVAILABLE", h.events[1].second)
        assertEquals(MailWatch.RESYNC_REWATCHED, h.events[2].second)
        assertTrue(first.bridge.isClosed)
        // stale deliveries of the dead generation are dropped, the new generation flows
        assertFalse(first.bridge.accept(1L, 2L, event("message", 2, 1, ""","message":{"uid":2}""")))
        assertTrue(second.bridge.accept(2L, 1L, event("message", 1, 2, ""","message":{"uid":3}""")))
        assertEquals("message:3", h.events.last().second)
        assertTrue(h.controller.watch.isActive)

        h.controller.watch.stop()
        assertTrue(second.stopped)
        assertEquals("close", h.types().last())
    }

    @Test
    fun withoutReconnectAPluginDeathClosesTheWatch() {
        val h = Harness(reconnect = false)
        h.open()
        h.opener.die()
        assertEquals(listOf("error", "close"), h.types())
        assertEquals(MailWatch.REASON_PLUGIN_DIED, h.events[1].second)
        assertEquals(1, h.opener.portCount)
        assertTrue(h.runner.isFinished)
    }

    @Test
    fun reWatchFailuresRetryWhileRetryableAndCloseOtherwise() {
        val retrying = Harness()
        retrying.open()
        retrying.opener.failures += MailPluginException(MailErrorMapper.normalize(MailErrorCodes.PLUGIN_UNAVAILABLE, "binding", retryable = true))
        retrying.opener.die()
        await("the retried watch") { retrying.runner.openCount == 2 }
        await("the resync event") { retrying.types().contains("resync") }
        assertEquals(listOf("error", "error", "resync"), retrying.types())
        assertEquals("error:PLUGIN_UNAVAILABLE", retrying.events[1].second)
        assertTrue(retrying.controller.watch.isActive)
        retrying.controller.watch.stop()

        val fatal = Harness()
        fatal.open()
        fatal.opener.failures += MailPluginException(MailErrorMapper.normalize(MailErrorCodes.PLUGIN_UNAVAILABLE, "uninstalled", retryable = false))
        fatal.opener.die()
        // The runner marks itself finished before dispatching close to the observer.
        await("the close event") { fatal.types().contains("close") }
        assertTrue(fatal.runner.isFinished)
        assertEquals(listOf("error", "error", "close"), fatal.types())
        assertEquals(MailWatch.REASON_ERROR, fatal.events[2].second)
        assertEquals(1, fatal.runner.openCount)
    }

    @Test
    fun authFailedWithATokenProviderRefreshesAndWatchesAgain() {
        val tokens = AtomicInteger()
        val h = Harness(
            tokenProvider = { "token-${tokens.incrementAndGet()}" },
            account = MailAccountSpec.inline("""{"address":"alice@example.org","auth":"xoauth2"}""", accessToken = "token-0"),
        )
        h.open()
        val first = h.opener.port(0).last()
        assertTrue(first.bridge.accept(1L, 1L, event("error", 1, 1, ""","error":{"code":"AUTH_FAILED","message":"expired","retryable":false}""")))
        assertTrue(first.bridge.accept(1L, 2L, event("closed", 2, 1, ""","reason":"error"""")))
        await("the re-watch") { h.runner.openCount == 2 }
        await("the resync event") { h.types().contains("resync") }
        assertEquals(1, tokens.get())
        assertEquals("token-1", h.client.account.accessToken)
        assertEquals(2, h.opener.portCount)
        assertEquals(2L, h.opener.port(1).last().bridge.generation)
        assertEquals(listOf("error", "resync"), h.types())
        assertTrue(h.controller.watch.isActive)
        h.controller.watch.stop()

        val plain = Harness()
        plain.open()
        val watch = plain.opener.port(0).last()
        assertTrue(watch.bridge.accept(1L, 1L, event("error", 1, 1, ""","error":{"code":"AUTH_FAILED","message":"wrong","retryable":false}""")))
        assertTrue(watch.bridge.accept(1L, 2L, event("closed", 2, 1, ""","reason":"error"""")))
        assertEquals(listOf("error", "close"), plain.types())
        assertEquals(MailWatch.REASON_ERROR, plain.events[1].second)
        assertEquals(1, plain.opener.portCount)
    }

    @Test
    fun aScriptThatStopsDrainingGetsLimitExceededAndCloseOverflow() {
        val h = Harness(queued = true, maxQueuedActions = 2)
        h.open()
        val watch = h.opener.port(0).last()
        assertTrue(watch.bridge.accept(1L, 1L, event("message", 1, 1, ""","message":{"uid":1}""")))
        assertTrue(watch.bridge.accept(1L, 2L, event("message", 2, 1, ""","message":{"uid":2}""")))
        assertTrue(watch.bridge.accept(1L, 3L, event("message", 3, 1, ""","message":{"uid":3}""")))
        assertTrue(watch.stopped)
        assertTrue(h.runner.isFinished)
        h.drain()
        assertEquals(listOf("error", "close"), h.types())
        assertEquals("error:LIMIT_EXCEEDED", h.events[0].second)
        assertEquals(MailWatch.REASON_OVERFLOW, h.events[1].second)
        assertEquals(1, h.released.get())
    }

    @Test
    fun cancelSilentlyStopsNothingOnThePluginAndEmitsNothing() {
        val h = Harness(queued = true)
        h.open()
        val watch = h.opener.port(0).last()
        assertTrue(watch.bridge.accept(1L, 1L, event("message", 1, 1, ""","message":{"uid":1}""")))
        h.runner.cancelSilently()
        h.drain()
        assertTrue(h.events.isEmpty())
        assertFalse(watch.stopped)
        assertTrue(watch.bridge.isClosed)
        assertEquals(MailWatch.REASON_EXIT, h.controller.watch.reason)
        assertEquals(1, h.released.get())
        h.opener.die()
        assertTrue(h.events.isEmpty())
        assertEquals(1, h.opener.portCount)
    }

    @Test
    fun aRefusedWatchPropagatesThePluginsError() {
        val opener = FakeOpener()
        val h = Harness(opener = opener)
        runBlocking { h.client.open() }
        opener.port(0).refuseWith = MailErrorMapper.normalize(MailErrorCodes.LIMIT_EXCEEDED, "the session already has 4 watches", retryable = false)
        try {
            h.open()
            fail("the refusal must propagate")
        } catch (e: MailPluginException) {
            assertEquals(MailErrorCodes.LIMIT_EXCEEDED, e.code)
        }
        assertFalse(h.runner.isFinished)
        h.controller.cancelSilently()
    }
}
