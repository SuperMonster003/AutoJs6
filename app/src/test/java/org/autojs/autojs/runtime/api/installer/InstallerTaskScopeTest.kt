package org.autojs.autojs.runtime.api.installer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext

class InstallerTaskScopeTest {

    @Test
    fun `running calls and waiting reservations both have enforced bounds`() = runBlocking {
        val tasks = InstallerTaskScope(EmptyCoroutineContext, maxRunning = 2, maxQueued = 1, dispatcher = Dispatchers.Default)
        val running = AtomicInteger()
        val bothRunning = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val calls = List(3) {
                tasks.prepare {
                    if (running.incrementAndGet() == 2) bothRunning.complete(Unit)
                    release.await()
                }.also { it.start() }
            }
            withTimeout(2_000) { bothRunning.await() }
            assertEquals(2, running.get())
            val rejected = runCatching { tasks.prepare {} }.exceptionOrNull()
            assertTrue(rejected is InstallerPluginException)
            assertEquals(InstallerErrorCodes.INVALID_ARGUMENT, (rejected as InstallerPluginException).code)
            tasks.close()
            withTimeout(2_000) { calls.joinAll() }
            assertTrue(calls.all { it.isCancelled })
            assertEquals(0, tasks.pendingCount)
            assertTrue(runCatching { tasks.prepare {} }.exceptionOrNull() is InstallerPluginException)
        } finally {
            tasks.close()
        }
    }
}
