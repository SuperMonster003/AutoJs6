package org.autojs.autojs.runtime.api.installer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerErrorMapper
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.core.plugin.installer.InstallerSessionListener
import org.autojs.autojs.core.plugin.installer.InstallerSessionOutcome
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext

class InstallerOperationTest {

    private val tasks = InstallerTaskScope(EmptyCoroutineContext, maxRunning = 1, maxQueued = 3, dispatcher = Dispatchers.Default)
    private val operations = mutableListOf<InstallerOperation>()

    @After
    fun close() {
        operations.forEach { it.close() }
        tasks.close()
    }

    @Test
    fun `id is stable before start and listener runs before await resumes`() = runBlocking {
        val backend = FakeBackend()
        val delivered = AtomicBoolean()
        val operation = operation(backend, listener = object : InstallerSessionListener {
            override fun onCompleted(completion: InstallerJson.Completion) {
                delivered.set(true)
            }
        })
        val id = operation.id
        assertFalse(id.isBlank())
        assertEquals(InstallerContract.STAGE_PENDING, operation.state)
        assertEquals(0, backend.opens.get())
        operation.start()
        operation.start()
        backend.awaitOpen()
        backend.complete()
        assertTrue(withTimeout(2_000) { operation.await() }.allOk)
        assertTrue(delivered.get())
        assertEquals(id, operation.id)
        assertEquals(id, backend.id)
        assertEquals(1, backend.opens.get())
        assertEquals(InstallerContract.STAGE_COMPLETED, operation.state)
    }

    @Test
    fun `short wait times out without cancelling the underlying install`() = runBlocking {
        val backend = FakeBackend()
        val operation = operation(backend)
        operation.start()
        backend.awaitOpen()
        assertEquals(InstallerErrorCodes.TIMEOUT, failure { operation.await(15) }.code)
        assertFalse(operation.isTerminal)
        assertEquals(0, backend.session.cancels.get())
        backend.complete()
        assertTrue(withTimeout(2_000) { operation.await() }.allOk)
        assertEquals(0, backend.session.cancels.get())
    }

    @Test
    fun `configured deadline cancels and retains TIMEOUT through host close fallback`() = runBlocking {
        val backend = FakeBackend()
        val operation = operation(backend, timeout = 60)
        operation.start()
        backend.awaitOpen()
        assertEquals(InstallerErrorCodes.TIMEOUT, failure { withTimeout(2_000) { operation.await() } }.code)
        backend.awaitFinished()
        assertEquals(InstallerContract.STAGE_FAILED, operation.state)
        assertTrue(backend.session.cancels.get() > 0)
        assertEquals(1, backend.session.closes.get())
        assertEquals(InstallerErrorCodes.TIMEOUT, operation.failure?.code)
    }

    @Test
    fun `queued cancel opens no source and releases its reservation`() = runBlocking {
        val first = FakeBackend()
        operation(first).start()
        first.awaitOpen()
        val queued = FakeBackend()
        val operation = operation(queued)
        operation.start()
        assertTrue(operation.cancel())
        assertFalse(operation.cancel())
        assertEquals(InstallerErrorCodes.CANCELLED, failure { operation.await() }.code)
        queued.awaitFinished()
        assertEquals(0, queued.opens.get())
        assertEquals(1, queued.closes.get())
        assertEquals(1, tasks.pendingCount)
    }

    @Test
    fun `deadline includes time waiting for a running slot`() = runBlocking {
        val first = FakeBackend()
        operation(first).start()
        first.awaitOpen()
        val queued = FakeBackend()
        val operation = operation(queued, timeout = 30)
        operation.start()
        assertEquals(InstallerErrorCodes.TIMEOUT, failure { withTimeout(2_000) { operation.await() } }.code)
        queued.awaitFinished()
        assertEquals(0, queued.opens.get())
    }

    @Test
    fun `a session returned after close is cancelled and closed without events`() = runBlocking {
        val release = CountDownLatch(1)
        val backend = FakeBackend().apply { blockOpen = release }
        val events = AtomicInteger()
        val operation = operation(backend, listener = object : InstallerSessionListener {
            override fun onCompleted(completion: InstallerJson.Completion) { events.incrementAndGet() }
            override fun onFailed(error: InstallerError) { events.incrementAndGet() }
        })
        operation.start()
        backend.awaitOpen()
        operation.close()
        assertEquals(InstallerErrorCodes.CANCELLED, failure { operation.await() }.code)
        release.countDown()
        backend.awaitFinished()
        assertEquals(1, backend.session.closes.get())
        assertEquals(1, backend.closes.get())
        assertEquals(0, events.get())
    }

    @Test
    fun `observed real completion wins a concurrent cancel request`() = runBlocking {
        val backend = FakeBackend()
        val operation = operation(backend)
        operation.start()
        backend.awaitOpen()
        backend.complete(releaseWaiter = false)
        assertFalse(operation.cancel())
        backend.releaseSuccess()
        assertTrue(withTimeout(2_000) { operation.await() }.allOk)
        assertEquals(0, backend.session.cancels.get())
    }

    @Test
    fun `cancel request keeps real success when the plugin has already committed`() = runBlocking {
        val backend = FakeBackend()
        val operation = operation(backend)
        operation.start()
        backend.awaitOpen()
        backend.session.onCancel = { backend.complete() }
        assertTrue(operation.cancel())
        assertTrue(withTimeout(2_000) { operation.await() }.allOk)
        assertEquals(InstallerContract.STAGE_COMPLETED, operation.state)
        assertFalse(operation.cancel())
    }

    @Test
    fun `real terminal callback wins even when the handle await races the deadline`() = runBlocking {
        val backend = FakeBackend()
        val operation = operation(backend, timeout = 60)
        operation.start()
        backend.awaitOpen()
        backend.complete(releaseWaiter = false)
        assertTrue(withTimeout(2_000) { operation.await() }.allOk)
        assertEquals(0, backend.session.cancels.get())
        assertEquals(InstallerContract.STAGE_COMPLETED, operation.state)
    }

    @Test
    fun `source open failure closes local ownership and reports mapped error`() = runBlocking {
        val backend = FakeBackend().apply { openFailure = IOException("unreadable") }
        val operation = operation(backend)
        assertEquals(InstallerErrorCodes.SOURCE_UNREADABLE, failure { withTimeout(2_000) { operation.await() } }.code)
        backend.awaitFinished()
        assertEquals(1, backend.closes.get())
        assertEquals(0, backend.session.closes.get())
    }

    @Test
    fun `terminal result is processed before publication and its lists are immutable`() = runBlocking {
        val backend = FakeBackend().apply {
            transform = { value -> value.copy(results = value.results.map { it.copy(sourceDeleted = true, notes = listOf("deleted")) }) }
        }
        val operation = operation(backend, listener = object : InstallerSessionListener {
            override fun onCompleted(completion: InstallerJson.Completion) {
                assertEquals(true, completion.single.sourceDeleted)
            }
        })
        operation.start()
        backend.awaitOpen()
        backend.complete()
        val completed = withTimeout(2_000) { operation.await() }
        assertEquals(true, completed.single.sourceDeleted)
        assertEquals(listOf("deleted"), completed.single.notes)
        val failure = runCatching { (completed.results as MutableList).clear() }.exceptionOrNull()
        assertTrue(failure is UnsupportedOperationException)
    }

    @Test
    fun `listener exception cannot leave waiters hanging and explicit authorizer is retained`() = runBlocking {
        val backend = FakeBackend()
        val operation = operation(backend, listener = object : InstallerSessionListener {
            override fun onFailed(error: InstallerError) { throw IllegalStateException("listener failed") }
        })
        operation.start()
        backend.awaitOpen()
        backend.fail(InstallerErrorMapper.error(InstallerErrorCodes.AUTHORIZER_DENIED, "denied"))
        val error = failure { withTimeout(2_000) { operation.await() } }
        assertEquals(InstallerErrorCodes.AUTHORIZER_DENIED, error.code)
        assertEquals(InstallerContract.AUTHORIZER_SHIZUKU, error.authorizer)
        assertNotNull(operation.failure)
    }

    private fun operation(
        backend: FakeBackend,
        timeout: Long = 5_000,
        listener: InstallerSessionListener = object : InstallerSessionListener {},
    ): InstallerOperation = InstallerOperation(false, timeout, InstallerContract.AUTHORIZER_SHIZUKU, tasks, backend, listener) {
        backend.finished.complete(Unit)
    }.also { operations += it }

    private suspend fun failure(block: suspend () -> Any?): InstallerError = try {
        block()
        throw AssertionError("expected InstallerPluginException")
    } catch (failure: InstallerPluginException) {
        failure.error
    }

    private class FakeBackend : InstallerOperationBackend {
        val opened = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val opens = AtomicInteger()
        val closes = AtomicInteger()
        val closed = AtomicBoolean()
        val session = FakeSession()
        var blockOpen: CountDownLatch? = null
        var openFailure: Throwable? = null
        var transform: (InstallerJson.Completion) -> InstallerJson.Completion = { it }
        lateinit var listener: InstallerSessionListener
        lateinit var id: String

        override suspend fun open(id: String, listener: InstallerSessionListener): InstallerOperationSession {
            this.id = id
            this.listener = listener
            session.listener = listener
            opens.incrementAndGet()
            opened.complete(Unit)
            blockOpen?.let { check(it.await(2, TimeUnit.SECONDS)) }
            openFailure?.let { throw it }
            return session
        }

        override fun cancelOpening() = Unit
        override fun complete(value: InstallerJson.Completion): InstallerJson.Completion = transform(value)
        override fun close() { if (closed.compareAndSet(false, true)) closes.incrementAndGet() }

        suspend fun awaitOpen() = withTimeout(2_000) { opened.await() }
        suspend fun awaitFinished() = withTimeout(2_000) { finished.await() }

        fun complete(releaseWaiter: Boolean = true) {
            listener.onCompleted(SUCCESS)
            if (releaseWaiter) releaseSuccess()
        }

        fun releaseSuccess() { session.outcome.complete(InstallerSessionOutcome.Completed(SUCCESS)) }

        fun fail(error: InstallerError) {
            listener.onFailed(error)
            session.outcome.complete(InstallerSessionOutcome.Failed(error))
        }
    }

    private class FakeSession : InstallerOperationSession {
        val cancels = AtomicInteger()
        val closes = AtomicInteger()
        val outcome = CompletableDeferred<InstallerSessionOutcome>()
        lateinit var listener: InstallerSessionListener
        var onCancel: (() -> Unit)? = null

        override suspend fun await(): InstallerSessionOutcome = outcome.await()
        override fun cancel(): Boolean {
            cancels.incrementAndGet()
            onCancel?.invoke()
            return true
        }

        override fun close() {
            closes.incrementAndGet()
            if (!outcome.isCompleted) {
                val error = InstallerErrorMapper.error(InstallerErrorCodes.CANCELLED, "closed")
                listener.onFailed(error)
                outcome.complete(InstallerSessionOutcome.Failed(error))
            }
        }
    }

    private companion object {
        val SUCCESS = InstallerJson.decodeCompletion("""{"ok":true,"packageName":"org.example.app","sourceDeleted":false}""")
    }
}
