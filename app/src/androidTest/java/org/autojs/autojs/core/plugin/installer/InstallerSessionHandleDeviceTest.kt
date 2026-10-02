package org.autojs.autojs.core.plugin.installer

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.plugin.installer.api.IInstallerSession
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/** Real Android Binder/PFD objects with a local fake session; no packages are installed. */
@RunWith(AndroidJUnit4::class)
class InstallerSessionHandleDeviceTest {

    @Test
    fun deadlineKeepsTimeoutWhenCancelImmediatelyCallsBackCancelled(): Unit = runBlocking {
        Fixture(timeoutMillis = 1).use { fixture ->
            val actual = fixture.handle.await()
            assertEquals(InstallerErrorCodes.TIMEOUT, (actual as InstallerSessionOutcome.Failed).error.code)
            assertEquals(InstallerErrorCodes.TIMEOUT, (fixture.callback.outcome.await() as InstallerSessionOutcome.Failed).error.code)
            assertEquals(listOf(InstallerErrorCodes.TIMEOUT), fixture.failures)
            assertEquals(1, fixture.session.cancels.get())
            assertEquals(0, fixture.session.closes.get())
        }
    }

    @Test
    fun completedCallbackWinsWhenParentDeadlineCancelsItsQueuedWaiter(): Unit = runBlocking {
        val deadline = try {
            withTimeout(0L) { error("an expired deadline must not execute its body") }
        } catch (timeout: TimeoutCancellationException) {
            timeout
        }
        val dispatcher = PausedDispatcher()
        val parent = Job()
        val returned = AtomicReference<InstallerSessionOutcome?>()
        Fixture().use { fixture ->
            try {
                val waiter = CoroutineScope(parent + dispatcher).launch {
                    returned.set(fixture.handle.await())
                }
                dispatcher.runNext()
                assertFalse(waiter.isCompleted)
                // The host callback has completed its Deferred, but its waiter cannot resume
                // until we drain. Expire the parent in exactly that gap, without a race/sleep.
                fixture.complete()
                parent.cancel(deadline)
                dispatcher.drain()
                assertTrue(waiter.isCompleted)
                val actual = returned.get()
                assertTrue("the completed outcome must survive the inherited deadline", actual is InstallerSessionOutcome.Completed)
                assertEquals("org.example.installer.fixture", (actual as InstallerSessionOutcome.Completed).completion.single.packageName)
                assertEquals(0, fixture.session.cancels.get())
                assertTrue(fixture.failures.isEmpty())
            } finally {
                parent.cancel()
                dispatcher.drain()
            }
        }
    }

    @Test
    fun completedWaitKeepsOwnedDescriptorOpenForDeletionUntilExplicitClose(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "installer-handle-${UUID.randomUUID()}.apk").apply { writeText("owned fixture bytes") }
        try {
            val source = InstallerSource.fromFile(file)
            Fixture(sources = listOf(source)).use { fixture ->
                fixture.complete()
                assertTrue(fixture.handle.await() is InstallerSessionOutcome.Completed)
                assertEquals(0, fixture.session.cancels.get())
                val descriptor = source.descriptor.fileDescriptor
                assertTrue(descriptor.valid())
                assertTrue(source.deleteOwnedFileIfUnchanged())
                assertFalse(file.exists())
                fixture.handle.close()
                assertFalse(descriptor.valid())
                assertTrue(fixture.leaseClosed.get())
                assertEquals(1, fixture.session.closes.get())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun earlyCompletionCarriesOnlyTheLocalSourceProfileNegotiation(): Unit = runBlocking {
        for (negotiated in listOf(false, true)) {
            val observed = AtomicReference<InstallerJson.Completion?>()
            val callback = InstallerSessionCallbackBinder(ID, object : InstallerSessionListener {
                override fun onCompleted(completion: InstallerJson.Completion) { observed.set(completion) }
            }).apply { sourceProfilesEnabled = negotiated }
            // A remote can synchronously finish openSession before the handle exists. A JSON
            // value cannot forge (or erase) the host's negotiation decision in either direction.
            callback.onCompleted(ID, Bundle().apply {
                putString(InstallerContract.KEY_RESULT_JSON,
                    """{"ok":true,"sourceProfilesEnabled":${!negotiated},"sourceDeleteRequested":true}""")
            })
            InstallerSessionHandle(ID, FakeSession(callback), Closeable {}, callback, emptyList(), 5_000).use { handle ->
                val result = (handle.await() as InstallerSessionOutcome.Completed).completion
                assertEquals(negotiated, result.sourceProfilesEnabled)
                assertEquals(negotiated, observed.get()?.sourceProfilesEnabled)
                assertEquals(true, result.single.sourceDeleteRequested)
            }
        }
    }

    @Test
    fun hostUiHonorsNegotiatedCleanupBeforeClosingOwnedDescriptors(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (negotiated in listOf(false, true)) {
            val file = File(context.cacheDir, "installer-ui-cleanup-${UUID.randomUUID()}.apk").apply { writeText("owned fixture") }
            try {
                val source = InstallerSource.fromFile(file)
                Fixture(sources = listOf(source)).use { fixture ->
                    fixture.callback.sourceProfilesEnabled = negotiated
                    fixture.callback.onCompleted(ID, Bundle().apply {
                        putString(InstallerContract.KEY_RESULT_JSON, """{"ok":true,"sourceDeleteRequested":true}""")
                    })
                    val result = PackageInstallRouter.awaitAndCleanOwnedSources(fixture.handle, listOf(source))
                    assertTrue(result is InstallerSessionOutcome.Completed)
                    assertEquals(!negotiated, file.exists())
                    assertTrue(source.descriptor.fileDescriptor.valid())
                    assertEquals(0, fixture.session.closes.get())
                }
            } finally {
                file.delete()
            }
        }
    }

    private class Fixture(
        timeoutMillis: Long = 5_000,
        sources: List<InstallerSource> = emptyList(),
    ) : Closeable {
        val failures = mutableListOf<String>()
        val leaseClosed = AtomicBoolean()
        val callback = InstallerSessionCallbackBinder(ID, object : InstallerSessionListener {
            override fun onFailed(error: InstallerError) { failures += error.code }
        })
        val session = FakeSession(callback)
        val handle = InstallerSessionHandle(ID, session, Closeable { leaseClosed.set(true) }, callback, sources, timeoutMillis)

        fun complete() {
            callback.onCompleted(ID, Bundle().apply {
                putString(InstallerContract.KEY_RESULT_JSON, """{"ok":true,"packageName":"org.example.installer.fixture"}""")
            })
        }

        override fun close() = handle.close()
    }

    private class FakeSession(private val callback: InstallerSessionCallbackBinder) : IInstallerSession.Stub() {
        val cancels = AtomicInteger()
        val closes = AtomicInteger()

        override fun getId(): String = ID
        override fun getStatus(): Bundle = Bundle().apply {
            putString(InstallerContract.KEY_STATUS_JSON, """{"state":"pending"}""")
        }

        override fun cancel(): Boolean {
            cancels.incrementAndGet()
            callback.onFailed(ID, Bundle().apply {
                putString(InstallerContract.KEY_ERROR_JSON, """{"code":"CANCELLED","message":"immediate cancellation callback"}""")
            })
            return true
        }

        override fun close() { closes.incrementAndGet() }
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        private val queued = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queued.add(block) }

        fun runNext() {
            requireNotNull(queued.poll(2, TimeUnit.SECONDS)) { "the test coroutine did not dispatch" }.run()
        }

        fun drain() {
            while (true) (queued.poll() ?: return).run()
        }
    }

    private companion object {
        const val ID = "installer-handle-device-fixture"
    }
}
