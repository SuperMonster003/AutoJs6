package org.autojs.autojs.core.looper

import android.app.Activity
import android.app.UiAutomation
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.StringScriptSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ScriptLooperCompatibilityDeviceTest {
    @Test fun timersAndEventsFinishAfterCallbackRemoval() = runCase("timers")
    @Test fun cancellingFutureCallbacksLetsTheScriptExit() = runCase("cancelled-timers")
    @Test fun cancellationFromAWorkerWakesTheMainLooper() = runCase("cross-thread-cancel")
    @Test fun uiTimersKeepTheScriptAliveUntilTheirCallbacksFinish() = runCase("ui-pending")
    @Test fun directPostsAndSelfCancellingIntervalsFinish() = runCase("callback-lifecycle")
    @Test fun scriptThreadPoolInitializesWorkersInRun() = runCase("thread-pool")
    @Test fun workerTimersAndSleepingThreadCanBeStopped() = runCase("threads")
    @Test fun shutdownInterruptsBlockingAccessibilityFind() = runCase("shutdown-finder")
    @Test fun exitCancelsChildrenAndEmitsExitEvent() = runCase("exit", expectInterrupt = true)
    @Test fun mainThreadProxyInterruptStopsExecutingScript() = runCase("main-interrupt", expectInterrupt = true)
    @Test fun uiAndOverlayCallbacksFinishAndClose() = runCase("ui-overlay")
    @Test fun intervalsRemainLiveDuringSustainedDispatch() = runCase("soak")

    private fun runCase(name: String, expectInterrupt: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        if (name == "shutdown-finder") {
            assertTrue("Enable and bind AutoJs6 accessibility on the test device", AccessibilityService.waitForOperational(10_000L))
        }
        val soakMillis = InstrumentationRegistry.getArguments().getString("sdk37.soakMillis")?.toLong() ?: 5_000L
        require(soakMillis in 1_000L..300_000L)
        val source = instrumentation.context.assets.open("sdk37/looper/$name.js").bufferedReader().use { it.readText() }
        val prepared = if (name == "soak") "var SDK37_SOAK_MILLIS = $soakMillis;\n$source" else source
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) { completed.countDown() }
            override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error); completed.countDown() }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("sdk37-$name", prepared), listener, ExecutionConfig(),
        )
        try {
            val timeout = if (name == "soak") soakMillis + 20_000 else 20_000L
            assertTrue("$name did not finish", completed.await(timeout, TimeUnit.MILLISECONDS))
            val engine = execution.engine as RhinoJavaScriptEngine
            if (engine.thread !== android.os.Looper.getMainLooper().thread) {
                engine.thread.join(5_000)
                assertFalse("$name left its script thread alive", engine.thread.isAlive)
            }
            val error = failure.get()
            if (error != null && !(expectInterrupt && ScriptInterruptedException.causedByInterrupt(error))) {
                throw AssertionError("$name failed", error)
            }
            assertTrue("$name did not reach its assertions", engine.runtime.getProperty("sdk37.completed") == true)
            assertFalse("$name continued after cancellation", engine.runtime.getProperty("sdk37.unreachable") == true)
            (engine.runtime.getProperty("sdk37.worker") as? Thread)?.let { worker ->
                worker.join(5_000)
                assertFalse("$name left its child thread alive", worker.isAlive)
            }
            if (name == "exit") assertTrue(engine.runtime.getProperty("sdk37.exitEvent") == true)
            Log.i("SDK37-Looper", "PASS script=$name, soakMillis=$soakMillis")
        } finally {
            execution.engine?.let { engine ->
                if (engine is RhinoJavaScriptEngine && engine.thread.isAlive && engine.thread !== android.os.Looper.getMainLooper().thread) engine.forceStop()
                (engine.getTag("activity") as? Activity)?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
            }
        }
    }
}
