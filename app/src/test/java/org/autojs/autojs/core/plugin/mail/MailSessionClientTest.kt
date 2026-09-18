package org.autojs.autojs.core.plugin.mail

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MailSessionClientTest {

    private class FakePort : MailSessionPort {
        val calls = ArrayList<MailJson.Request>()
        val cancelled = ArrayList<String>()
        val listeners = HashMap<String, MailCallListener>()

        @Volatile
        var closed = false

        var handler: (MailJson.Request, MailCallListener) -> Unit = { request, listener ->
            listener.onResult(MailJson.Response(request.id, ok = true, result = """{"echo":"${request.op}"}""", error = null))
        }

        override fun status(): MailJson.Status = MailJson.Status(MailContract.STATE_OPEN, null, null, """{"state":"open"}""")

        override fun call(request: MailJson.Request, descriptors: Array<ParcelFileDescriptor>?, listener: MailCallListener) {
            calls += request
            listeners[request.id] = listener
            handler(request, listener)
        }

        override fun cancel(requestId: String) {
            cancelled += requestId
        }

        override fun watch(optionsJson: String, bridge: MailWatchBridge): MailWatchHandle = object : MailWatchHandle {
            override fun status(): MailJson.Status = MailJson.Status(MailContract.STATE_OPEN, null, null, "{}")
            override fun stop() = bridge.close()
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeOpener : MailSessionOpener {
        val ports = ArrayList<FakePort>()
        val accounts = ArrayList<MailAccountSpec>()
        var events: MailSessionEvents? = null
        var failWith: Throwable? = null
        var configure: (FakePort) -> Unit = {}

        override suspend fun open(account: MailAccountSpec, events: MailSessionEvents): MailSessionPort {
            failWith?.let { throw it }
            accounts += account
            this.events = events
            return FakePort().also { port ->
                configure(port)
                ports += port
            }
        }
    }

    private val account = MailAccountSpec.inline("""{"address":"alice@example.org"}""", password = "secret")

    private fun error(code: String, retryable: Boolean? = null): MailJson.Response {
        return MailJson.Response("id", ok = false, result = null, error = MailErrorMapper.normalize(code, "fake", retryable))
    }

    @Test
    fun callSubmitsTheEnvelopeAndReturnsTheResult() = runBlocking {
        val opener = FakeOpener()
        val client = MailSessionClient(account, opener, requestIds = { "fixed-id" })
        var submitted = 0
        val result = client.call(MailContract.OP_SESSION_TEST, """{"a":1}""", onSubmitted = { submitted++ })
        assertEquals("""{"echo":"session.test"}""", result)
        assertEquals(1, submitted)
        assertEquals(1, opener.ports.size)
        assertEquals(1, client.openCount)
        assertTrue(client.isOpen)
        val request = opener.ports.single().calls.single()
        assertEquals("fixed-id", request.id)
        assertEquals("""{"id":"fixed-id","op":"session.test","args":{"a":1}}""", request.json)
        assertEquals(account, opener.accounts.single())
        assertNotNull(client.status())
    }

    @Test
    fun secondCallReusesTheOpenSession() = runBlocking {
        val opener = FakeOpener()
        val client = MailSessionClient(account, opener)
        client.call(MailContract.OP_SESSION_TEST)
        client.call(MailContract.OP_FOLDERS_LIST)
        assertEquals(1, opener.ports.size)
        assertEquals(2, opener.ports.single().calls.size)
    }

    @Test
    fun errorResponseBecomesAnExceptionWithCodeAndRetryHint() = runBlocking {
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, listener -> listener.onResult(error(MailErrorCodes.FOLDER_NOT_FOUND)) } } }
        val client = MailSessionClient(account, opener)
        val exception = expectFailure { client.call(MailContract.OP_FOLDERS_STATUS, """{"folder":"Nope"}""") }
        assertEquals(MailErrorCodes.FOLDER_NOT_FOUND, exception.code)
        assertFalse(exception.retryable)
        assertEquals("fake", exception.error.message)
        assertTrue(client.isOpen)
    }

    @Test
    fun timeoutCancelsTheRequestOnThePluginSide() = runBlocking {
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, _ -> } } }
        val client = MailSessionClient(account, opener, requestIds = { "slow" })
        val exception = expectFailure { client.call(MailContract.OP_SESSION_TEST, timeoutMs = 50L) }
        assertEquals(MailErrorCodes.TIMEOUT, exception.code)
        assertTrue(exception.retryable)
        assertEquals(listOf("slow"), opener.ports.single().cancelled)
        assertTrue(client.isOpen)
    }

    @Test
    fun cancellationOfTheCallerCancelsTheRequest() = runBlocking {
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, _ -> } } }
        val client = MailSessionClient(account, opener, requestIds = { "pending" })
        val job = launch { client.call(MailContract.OP_MESSAGES_LIST) }
        withTimeout(5_000) {
            while (opener.ports.isEmpty() || opener.ports.single().calls.isEmpty()) yield()
        }
        job.cancelAndJoin()
        assertEquals(listOf("pending"), opener.ports.single().cancelled)
    }

    @Test
    fun progressIsForwardedBeforeTheResult() = runBlocking {
        val opener = FakeOpener().apply {
            configure = { port ->
                port.handler = { request, listener ->
                    listener.onProgress(MailJson.Progress(request.id, 10L, 20L))
                    listener.onProgress(MailJson.Progress(request.id, 20L, 20L))
                    listener.onResult(MailJson.Response(request.id, ok = true, result = "true", error = null))
                }
            }
        }
        val client = MailSessionClient(account, opener)
        val seen = ArrayList<Long>()
        assertEquals("true", client.call(MailContract.OP_ATTACHMENTS_DOWNLOAD, transferBytes = 20L, onProgress = { seen += it.transferred }))
        assertEquals(listOf(10L, 20L), seen)
    }

    @Test
    fun deathFailsInflightCallsAndTheNextCallReopens() = runBlocking {
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, _ -> } } }
        val client = MailSessionClient(account, opener)
        val pending = async { runCatching { client.call(MailContract.OP_MESSAGES_LIST) } }
        withTimeout(5_000) {
            while (opener.ports.isEmpty() || opener.ports.first().calls.isEmpty()) yield()
        }
        opener.events!!.onClosed(MailErrorMapper.normalize(MailErrorCodes.SESSION_CLOSED, "mail plugin process died", retryable = true))
        val failure = pending.await().exceptionOrNull() as MailPluginException
        assertEquals(MailErrorCodes.SESSION_CLOSED, failure.code)
        assertTrue(failure.retryable)
        assertFalse(client.isOpen)
        assertEquals("mail plugin process died", client.lastCloseReason!!.message)

        opener.configure = {}
        assertEquals("""{"echo":"session.test"}""", client.call(MailContract.OP_SESSION_TEST))
        assertEquals(2, opener.ports.size)
        assertTrue(opener.ports.first().closed)
        assertEquals(2, client.openCount)
        assertTrue(client.isOpen)
    }

    @Test
    fun sessionClosedResponseMarksTheSessionForReopening() = runBlocking {
        val first = AtomicInteger(0)
        val opener = FakeOpener().apply {
            configure = { port ->
                if (first.getAndIncrement() == 0) port.handler = { _, listener -> listener.onResult(error(MailErrorCodes.SESSION_CLOSED)) }
            }
        }
        val client = MailSessionClient(account, opener)
        assertEquals(MailErrorCodes.SESSION_CLOSED, expectFailure { client.call(MailContract.OP_SESSION_TEST) }.code)
        assertFalse(client.isOpen)
        client.call(MailContract.OP_SESSION_TEST)
        assertEquals(2, opener.ports.size)
    }

    @Test
    fun transportFailureOnCallMarksTheSessionForReopening() = runBlocking {
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, _ -> throw IllegalStateException("binder gone") } } }
        val client = MailSessionClient(account, opener)
        val exception = expectFailure { client.call(MailContract.OP_SESSION_TEST) }
        assertEquals(MailErrorCodes.INTERNAL, exception.code)
        assertTrue(client.isOpen)
        assertEquals(1, opener.ports.size)
    }

    @Test
    fun authFailureWithTokenProviderRefreshesReopensAndRetriesOnce() = runBlocking {
        val tokenAccount = MailAccountSpec.inline("""{"address":"alice@example.org","auth":"xoauth2"}""", accessToken = "old")
        val opener = FakeOpener().apply {
            configure = { port ->
                if (ports.isEmpty()) port.handler = { _, listener -> listener.onResult(error(MailErrorCodes.AUTH_FAILED)) }
            }
        }
        val refreshes = AtomicInteger(0)
        val client = MailSessionClient(tokenAccount, opener, tokenProvider = { "new-${refreshes.incrementAndGet()}" })
        assertEquals("""{"echo":"session.test"}""", client.call(MailContract.OP_SESSION_TEST))
        assertEquals(1, refreshes.get())
        assertEquals(2, opener.ports.size)
        assertEquals("old", opener.accounts[0].accessToken)
        assertEquals("new-1", opener.accounts[1].accessToken)
        assertEquals("new-1", client.account.accessToken)
        assertTrue(opener.ports.first().closed)
    }

    @Test
    fun authFailureAfterTheRefreshIsReportedWithoutFurtherRetries() = runBlocking {
        val tokenAccount = MailAccountSpec.inline("""{"address":"alice@example.org"}""", accessToken = "old")
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, listener -> listener.onResult(error(MailErrorCodes.AUTH_FAILED)) } } }
        val refreshes = AtomicInteger(0)
        val client = MailSessionClient(tokenAccount, opener, tokenProvider = { "new-${refreshes.incrementAndGet()}" })
        val exception = expectFailure { client.call(MailContract.OP_SESSION_TEST) }
        assertEquals(MailErrorCodes.AUTH_FAILED, exception.code)
        assertEquals(1, refreshes.get())
        assertEquals(2, opener.ports.size)
        assertEquals(2, opener.ports.sumOf { it.calls.size })
    }

    @Test
    fun authFailureWithoutTokenOrProviderIsNotRetried() = runBlocking {
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, listener -> listener.onResult(error(MailErrorCodes.AUTH_FAILED)) } } }
        val refreshes = AtomicInteger(0)
        val passwordClient = MailSessionClient(account, opener, tokenProvider = { "t-${refreshes.incrementAndGet()}" })
        assertEquals(MailErrorCodes.AUTH_FAILED, expectFailure { passwordClient.call(MailContract.OP_SESSION_TEST) }.code)
        assertEquals(0, refreshes.get())
        val tokenClient = MailSessionClient(MailAccountSpec.inline("{}", accessToken = "t"), opener)
        assertEquals(MailErrorCodes.AUTH_FAILED, expectFailure { tokenClient.call(MailContract.OP_SESSION_TEST) }.code)
        assertEquals(2, opener.ports.size)
    }

    @Test
    fun failingTokenRefreshIsReportedAsAuthFailure() = runBlocking {
        val tokenAccount = MailAccountSpec.inline("{}", accessToken = "old")
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, listener -> listener.onResult(error(MailErrorCodes.AUTH_FAILED)) } } }
        val client = MailSessionClient(tokenAccount, opener, tokenProvider = { throw IllegalStateException("provider down") })
        val exception = expectFailure { client.call(MailContract.OP_SESSION_TEST) }
        assertEquals(MailErrorCodes.AUTH_FAILED, exception.code)
        assertTrue(exception.error.message.contains("provider down"))
        assertEquals(1, opener.ports.size)
    }

    @Test
    fun closedClientRejectsCallsAndFailsPendingOnes() = runBlocking {
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, _ -> } } }
        val client = MailSessionClient(account, opener)
        val pending = async { runCatching { client.call(MailContract.OP_MESSAGES_LIST) } }
        withTimeout(5_000) {
            while (opener.ports.isEmpty() || opener.ports.first().calls.isEmpty()) yield()
        }
        client.close()
        val failure = pending.await().exceptionOrNull() as MailPluginException
        assertEquals(MailErrorCodes.SESSION_CLOSED, failure.code)
        assertFalse(failure.retryable)
        assertTrue(opener.ports.single().closed)
        assertFalse(client.isOpen)
        val rejected = expectFailure { client.call(MailContract.OP_SESSION_TEST) }
        assertEquals(MailErrorCodes.SESSION_CLOSED, rejected.code)
        assertFalse(rejected.retryable)
        assertEquals(1, opener.ports.size)
        assertNull(client.status())
    }

    @Test
    fun openFailuresMapToPluginUnavailableAndInvalidAccountsToInvalidArgument() = runBlocking {
        val opener = FakeOpener().apply { failWith = IllegalStateException("Plugin not authorized: x") }
        val unavailable = expectFailure { MailSessionClient(account, opener).call(MailContract.OP_SESSION_TEST) }
        assertEquals(MailErrorCodes.PLUGIN_UNAVAILABLE, unavailable.code)
        assertTrue(unavailable.error.message.contains("Plugin not authorized"))

        val invalid = expectFailure { MailSessionClient(MailAccountSpec.inline("[]"), FakeOpener()).open() }
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, invalid.code)
        val unknownOp = expectFailure { MailSessionClient(account, FakeOpener()).call("messages.purge") }
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, unknownOp.code)
    }

    @Test
    fun watchUsesTheOpenSessionAndWrapsTransportFailures() = runBlocking {
        val opener = FakeOpener()
        val client = MailSessionClient(account, opener)
        val events = ArrayList<String>()
        val sink = object : MailWatchSink {
            override fun onEvent(event: MailJson.Event) {
                events += event.type
            }

            override fun onDropped(reason: String, generation: Long, seq: Long) = Unit
        }
        val (bridge, handle) = client.watch(MailJson.WatchOptions("INBOX", null, null), 7L, sink)
        assertEquals(7L, bridge.generation)
        assertTrue(bridge.accept(7L, 1L, """{"type":"message"}"""))
        handle.stop()
        assertTrue(bridge.isClosed)
        assertEquals(listOf("message"), events)
        assertEquals(1, opener.ports.size)
    }

    @Test
    fun resultAfterTimeoutIsIgnored() = runBlocking {
        val late = CompletableDeferred<MailCallListener>()
        val opener = FakeOpener().apply { configure = { port -> port.handler = { _, listener -> late.complete(listener) } } }
        val client = MailSessionClient(account, opener, requestIds = { "late" })
        assertEquals(MailErrorCodes.TIMEOUT, expectFailure { client.call(MailContract.OP_SESSION_TEST, timeoutMs = 20L) }.code)
        late.await().onResult(MailJson.Response("late", ok = true, result = "1", error = null))
        delay(10)
        opener.ports.single().handler = { request, listener ->
            listener.onResult(MailJson.Response(request.id, ok = true, result = """{"echo":"${request.op}"}""", error = null))
        }
        assertEquals("""{"echo":"folders.list"}""", client.call(MailContract.OP_FOLDERS_LIST))
        assertEquals(1, opener.ports.size)
    }

    private suspend fun expectFailure(block: suspend () -> Unit): MailPluginException {
        try {
            block()
        } catch (e: MailPluginException) {
            return e
        }
        fail("expected MailPluginException")
        throw AssertionError()
    }
}
