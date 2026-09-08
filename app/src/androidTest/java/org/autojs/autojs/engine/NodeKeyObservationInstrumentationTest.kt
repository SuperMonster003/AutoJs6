package org.autojs.autojs.engine

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.ui.log.LogActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeKeyObservationInstrumentationTest {
    private var activity: Activity? = null
    @After fun closeActivity() { InstrumentationRegistry.getInstrumentation().runOnMainSync { activity?.finish() } }
    @Test fun observesAndroidKeyThroughTheGrantedAccessibilityService() {
        val mode = InstrumentationRegistry.getArguments().getString("keyObservation")
        assumeTrue("Use keyObservation=manual for a physical volume-up press, or emulator for an emulator console volume-up event.", mode in listOf("manual", "emulator"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!AccessibilityService.registry.isOperational && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Enable AutoJs6 accessibility first", AccessibilityService.registry.isOperational)
        val keyCode = KeyEvent.KEYCODE_VOLUME_UP
        val ready = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val executionId = UUID.randomUUID().toString()
        try {
            val future = executor.submit(Callable {
                NodeJsRuntimePluginBridge.runScript(instrumentation.targetContext, NodePluginScriptRequest(
                    source = """
                    (async () => {
                      const events = require('autojs6:events');
                      let timeout;
                      try {
                        const received = new Promise((resolve, reject) => {
                          timeout = setTimeout(() => reject(new Error('expected Android key did not arrive')), 40000);
                          events.on('key', event => { if (event.keyCode === $keyCode && event.action === 'down') resolve(event); });
                        });
                        await events.observeKey();
                        console.log('m15.key.ready');
                        const key = await received;
                        console.log('m15.key.$mode=PASS code=' + key.keyCode);
                      } finally { clearTimeout(timeout); await events.close(); }
                    })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                    """.trimIndent(),
                    sourceName = "android-key.cjs", workingDirectory = instrumentation.targetContext.cacheDir.absolutePath,
                    timeoutMs = 50_000, executionId = executionId,
                    runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                        .fromDeclaredCapabilities(listOf("events", "events.key")).toJson()),
                ), outputListener = { _, text -> if (text.contains("m15.key.ready")) ready.countDown() })
            })
            assertTrue("Node key subscription did not become ready", ready.await(10, TimeUnit.SECONDS))
            val serviceInfo = AccessibilityService.instance?.serviceInfo
            println("m15.key.ready.$mode flags=" + serviceInfo?.flags + " capabilities=" + serviceInfo?.capabilities
                + " listeners=" + AccessibilityService.keyBus.subscriptionCount + " lastKey=" + AccessibilityService.keyBus.lastDispatchAtMillis)
            val result = future.get(45, TimeUnit.SECONDS)
            NodePluginTestHarness.assertSucceeded("Android key $mode", result)
            assertTrue(result.stdout, result.stdout.contains("m15.key.$mode=PASS"))
            println(result.stdout)
        } finally {
            println("m15.key.finished.$mode lastKey=" + AccessibilityService.keyBus.lastDispatchAtMillis)
            NodeJsRuntimePluginBridge.cancelScript(instrumentation.targetContext, executionId)
            executor.shutdownNow()
        }
    }
}
