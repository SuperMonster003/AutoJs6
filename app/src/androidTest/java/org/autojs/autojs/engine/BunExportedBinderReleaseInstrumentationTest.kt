package org.autojs.autojs.engine

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.autojs.runtime.memory.MemoryDiagnostics
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.autojs.plugin.bun.runtime.api.IBunHostCapabilityBroker
import org.autojs.plugin.bun.runtime.api.IBunHostCapabilityCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Binders a Bun execution exports to the plugin process stay reachable until that process collects its proxies.
 * Once the execution ends they must stop reaching the broker and the output callback (docs/memory-phase2/ROADMAP.md C37).
 * zh-CN: Bun 执行导出给插件进程的 Binder 在该进程回收代理之前一直可达. 执行结束后它们不得再指向 broker 与输出回调
 * (docs/memory-phase2/ROADMAP.md C37).
 */
class BunExportedBinderReleaseInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun closedBrokerIsNotReachableFromItsExportedBinder() {
        val executionId = "bun-exported-broker-${UUID.randomUUID()}"
        val (binder, brokerRef) = createAndCloseBroker(executionId)
        val stub = IBunHostCapabilityBroker.Stub.asInterface(binder)

        // A late call is dropped as before: the plugin fails its pending calls itself when the run ends.
        // zh-CN: 迟到的调用与此前一样被丢弃: 插件在运行结束时自行让未完成的调用失败.
        val responses = CopyOnWriteArrayList<Bundle>()
        stub.invoke(Bundle(), object : IBunHostCapabilityCallback.Stub() {
            override fun onResult(result: Bundle?) {
                result?.let(responses::add)
            }
        })

        assertTrue("closed broker is still reachable from its exported Binder", awaitCleared(brokerRef))
        assertTrue(responses.isEmpty())
        assertNotNull(binder)
    }

    @Test
    fun exportedCallbackForwardsUntilCloseAndThenLetsGoOfTheCallback() {
        val global = AutoJs.instance.globalConsole
        val console = ConsoleImpl(global.uiHandler, global)
        val executionId = "bun-exported-callback-${UUID.randomUUID()}"
        try {
            val (binder, callbackRef) = createCallback(executionId, console)

            binder.onEvent(event(executionId, BunRuntimeContract.EVENT_STDOUT, 0, "before close"))
            assertEquals(listOf("before close"), console.snapshotLogEntries().map { it.content.toString() })
            (callbackRef.get() as BunPluginScriptEngine.OutputCallback).close()
            binder.onEvent(event(executionId, BunRuntimeContract.EVENT_STDOUT, 1, "after close"))

            assertEquals(1, console.snapshotLogEntries().size)
            assertTrue("closed output callback is still reachable from its exported Binder", awaitCleared(callbackRef))
        } finally {
            console.onRuntimeExit()
        }
    }

    private fun createAndCloseBroker(executionId: String): Pair<android.os.IBinder, WeakReference<Any>> {
        val broker = BunHostCapabilityBroker(
            context,
            Process.myUid(),
            executionId,
            listOf(BunHostCapabilityGrants.DEVICE_CONTROL),
            { throw IllegalStateException("Unused console") },
            context.cacheDir.path,
        )
        val binder = broker.binder
        broker.close()
        return binder to WeakReference(broker)
    }

    private fun createCallback(
        executionId: String,
        console: ConsoleImpl,
    ): Pair<org.autojs.plugin.bun.runtime.api.IBunRuntimeCallback, WeakReference<Any>> {
        val callback = BunPluginScriptEngine.OutputCallback(executionId, Process.myUid(), console)
        return callback.binder to WeakReference(callback)
    }

    private fun event(id: String, type: String, sequence: Long, text: String? = null) = Bundle().apply {
        putInt(BunRuntimeContract.KEY_PROTOCOL_VERSION, BunRuntimeContract.PROTOCOL_VERSION)
        putString(BunRuntimeContract.KEY_EXECUTION_ID, id)
        putString(BunRuntimeContract.KEY_EVENT_TYPE, type)
        putLong(BunRuntimeContract.KEY_SEQUENCE, sequence)
        text?.let { putString(BunRuntimeContract.KEY_TEXT, it) }
    }

    private fun awaitCleared(reference: WeakReference<*>): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (reference.get() != null && SystemClock.elapsedRealtime() < deadline) {
            MemoryDiagnostics.forceGc()
            SystemClock.sleep(50L)
        }
        return reference.get() == null
    }
}
