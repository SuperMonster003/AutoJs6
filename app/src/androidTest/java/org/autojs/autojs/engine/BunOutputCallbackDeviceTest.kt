package org.autojs.autojs.engine

import android.os.Bundle
import android.os.Process
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Deterministic callback ordering checks; no plugin installation or fake script engine is needed. */
class BunOutputCallbackDeviceTest {
    @Test
    fun terminalReturnBeforeQueuedOutputDoesNotCloseTheCallback() = withCallback { callback, console, id ->
        val executor = Executors.newSingleThreadExecutor()
        val waiting = CountDownLatch(1)
        try {
            // Model the synchronous terminal reply arriving before the oneway callback queue drains.
            val returned = executor.submit<Boolean> {
                waiting.countDown()
                callback.awaitFinished(3_000)
            }
            assertTrue(waiting.await(1, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { returned.get(30, TimeUnit.MILLISECONDS) }
            callback.onEvent(event(id, BunRuntimeContract.EVENT_STDOUT, 0, "last stdout $id"))
            callback.onEvent(event(id, BunRuntimeContract.EVENT_STDERR, 1, "last stderr $id"))
            assertFalse(returned.isDone)
            callback.onEvent(event(id, BunRuntimeContract.EVENT_FINISHED, 2))
            assertTrue(returned.get(1, TimeUnit.SECONDS))
            assertEquals(listOf("last stdout $id", "last stderr $id"), console.snapshotLogEntries().map { it.content.toString() })
            callback.onEvent(event(id, BunRuntimeContract.EVENT_STDOUT, 3, "late output"))
            assertEquals(2, console.snapshotLogEntries().size)
        } finally { executor.shutdownNow() }
    }

    @Test
    fun timeoutAndCancellationDoNotPretendOutputWasDelivered() = withCallback { callback, console, id ->
        assertFalse(callback.awaitFinished(0))
        assertFalse(callback.awaitFinished(5_000) { true })
        callback.close()
        callback.onEvent(event(id, BunRuntimeContract.EVENT_STDOUT, 0, "cancelled output"))
        callback.onEvent(event(id, BunRuntimeContract.EVENT_FINISHED, 1))
        assertFalse(callback.awaitFinished(0))
        assertTrue(console.snapshotLogEntries().isEmpty())
    }

    @Test
    fun foreignExecutionCannotReleaseTheDrainOrWriteThisConsole() = withCallback { callback, console, id ->
        callback.onEvent(event("foreign-$id", BunRuntimeContract.EVENT_STDOUT, 0, "foreign output"))
        callback.onEvent(event("foreign-$id", BunRuntimeContract.EVENT_FINISHED, 1))
        assertFalse(callback.awaitFinished(0))
        assertTrue(console.snapshotLogEntries().isEmpty())
        callback.onEvent(event(id, BunRuntimeContract.EVENT_FINISHED, 0))
        assertTrue(callback.awaitFinished(0))
    }

    private fun withCallback(action: (BunPluginScriptEngine.OutputCallback, ConsoleImpl, String) -> Unit) {
        val global = AutoJs.instance.globalConsole
        val console = ConsoleImpl(global.uiHandler, global)
        val id = "bun-output-${UUID.randomUUID()}"
        val callback = BunPluginScriptEngine.OutputCallback(id, Process.myUid(), console)
        try { action(callback, console, id) } finally { callback.close(); console.onRuntimeExit() }
    }

    private fun event(id: String, type: String, sequence: Long, text: String? = null) = Bundle().apply {
        putInt(BunRuntimeContract.KEY_PROTOCOL_VERSION, BunRuntimeContract.PROTOCOL_VERSION)
        putString(BunRuntimeContract.KEY_EXECUTION_ID, id)
        putString(BunRuntimeContract.KEY_EVENT_TYPE, type)
        putLong(BunRuntimeContract.KEY_SEQUENCE, sequence)
        text?.let { putString(BunRuntimeContract.KEY_TEXT, it) }
    }
}
