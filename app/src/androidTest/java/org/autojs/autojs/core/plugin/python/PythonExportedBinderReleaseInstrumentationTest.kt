package org.autojs.autojs.core.plugin.python

import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.runtime.memory.MemoryDiagnostics
import org.autojs.autojs.script.PythonFileSource
import org.autojs.plugin.python.runtime.api.IPythonHostCapabilityBroker
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * The broker Binder a Python execution exports to the plugin process stays reachable until that process collects its proxy.
 * Once the execution ends it must stop reaching the broker, its dispatcher and capability actions
 * (docs/memory-phase2/ROADMAP.md C42).
 * zh-CN: Python 执行导出给插件进程的 broker Binder 在该进程回收代理之前一直可达. 执行结束后它不得再指向 broker, 其分发器与能力动作
 * (docs/memory-phase2/ROADMAP.md C42).
 */
class PythonExportedBinderReleaseInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun closedBrokerIsNotReachableFromItsExportedBinder() {
        val (binder, brokerRef) = createAndCloseBroker()

        // A late call still fails, which the plugin reports to the script as an unavailable broker.
        // zh-CN: 迟到的调用仍然失败, 插件将其作为 broker 不可用报告给脚本.
        try {
            IPythonHostCapabilityBroker.Stub.asInterface(binder.asBinder()).dispatch(ByteArray(1))
            fail("Expected a closed broker to refuse the call")
        } catch (_: IllegalStateException) {
        }
        assertTrue("closed broker is still reachable from its exported Binder", awaitCleared(brokerRef))
    }

    private fun createAndCloseBroker(): Pair<IPythonHostCapabilityBroker, WeakReference<Any>> {
        val broker = PythonHostCapabilityBroker(
            context = context,
            executionId = "python-exported-broker-${UUID.randomUUID()}",
            expectedPluginUid = Process.myUid(),
            fileRoot = context.cacheDir,
            source = PythonFileSource(File(context.cacheDir, "python-exported-broker.py")),
            engineId = 0,
            engineStartedAtMillis = System.currentTimeMillis(),
            stopSelf = {},
            globalConsole = AutoJs.instance.globalConsole,
            interactiveInputController = null,
            onViolation = {},
        )
        val binder = broker.binder
        broker.close()
        return binder to WeakReference(broker)
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
