package org.autojs.autojs.engine

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeAppDialogExpansionInstrumentationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun permissions(vararg names: String) = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to
        NodeBridgePermissionManifest.fromDeclaredCapabilities(names.toList()).toJson())

    @Test fun appQueriesServiceBroadcastAndSystemActionPermissions() {
        val received = CountDownLatch(1)
        var receivedText: String? = null
        var receivedNumber = 0
        val action = context.packageName + ".NODE_APP_M15_TEST"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                receivedText = intent.getStringExtra("text")
                receivedNumber = intent.getIntExtra("count", 0)
                received.countDown()
            }
        }
        val filter = IntentFilter(action).apply { addDataScheme("autojs6-test"); addDataType("text/plain") }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        val visible = instrumentation.startActivitySync(Intent(context, org.autojs.autojs.ui.log.LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val enabled = AccessibilityService.registry.isOperational
            val result = NodePluginTestHarness.runPluginScript("""
              (async () => {
                const assert = require('node:assert/strict'), app = require('app'), keys = require('keys');
                const installed = await app.getInstalledApps();
                const own = installed.find(value => value.packageName === '${context.packageName}');
                assert(own && own.appName.length > 0 && own.versionCode > 0);
                assert.equal(typeof own.enabled, 'boolean');
                assert.equal(typeof own.system, 'boolean');
                assert(own.firstInstallTime > 0 && own.lastUpdateTime > 0);
                const info = await app.getPackageInfo('${context.packageName}');
                assert.equal(info.versionName, own.versionName);
                assert.equal(await app.getPackageInfo('org.autojs.nonexistent.m15'), null);
                const userApps = await app.getInstalledApps({includeSystem:false});
                assert(userApps.every(value => value.system === false));
                const descriptor = app.intent({action:'$action', packageName:'${context.packageName}',
                  data:'autojs6-test://node/15', type:'text/plain', flags:32, extras:{text:'Roadmap M15.3',count:15}});
                assert.equal(descriptor.data, 'autojs6-test://node/15');
                assert.equal(descriptor.type, 'text/plain');
                assert.equal(descriptor.flags, 32);
                await app.sendBroadcast(descriptor);
                // This existing host dispatch service rejects the empty UI request and stops itself.
                const service = await app.startService({packageName:'${context.packageName}',
                  className:'org.autojs.autojs.engine.NodeBridgeUiDispatchService'});
                assert(service.endsWith('/org.autojs.autojs.engine.NodeBridgeUiDispatchService'));
                for (const method of ['notifications','quickSettings','lockScreen','takeScreenshot','splitScreen','powerDialog']) {
                  assert.equal(typeof keys[method], 'function');
                  if (!$enabled) await assert.rejects(keys[method](), error => error.category === 'permission-denied');
                }
                console.log('m15.app=PASS installed=' + installed.length + ' accessibility=' + $enabled);
              })().catch(error => { console.error(error.stack); process.exitCode=1; });
            """.trimIndent(), "app-expansion.cjs", timeoutMs = 25_000, runtimeModuleSources = permissions("app", "app.query", "app.activity", "keys"))
            assertTrue(result.errorMessage + "\n" + result.stderr.take(2500), result.succeeded)
            assertTrue(result.stdout, result.stdout.contains("m15.app=PASS"))
            assertTrue("Android broadcast did not arrive", received.await(5, TimeUnit.SECONDS))
            assertEquals("Roadmap M15.3", receivedText)
            assertEquals(15, receivedNumber)
            println(result.stdout)
        } finally { context.unregisterReceiver(receiver); instrumentation.runOnMainSync { visible.finish() } }
    }

    @Test fun realDialogInputChoicesAndProgressFollowScriptLifetime() {
        val executor = Executors.newSingleThreadExecutor()
        val executionId = UUID.randomUUID().toString()
        val clicked = mutableSetOf<String>()
        try {
            val future = executor.submit(Callable {
                NodePluginTestHarness.runPluginScript("""
                  (async () => {
                    const assert = require('node:assert/strict'), dialogs = require('dialogs');
                    await dialogs.alert('m15.alert', 'A real Android dialog');
                    assert.equal(await dialogs.confirm('m15.confirm', 'Continue'), true);
                    assert.equal(await dialogs.input('m15.input', 'before'), 'after');
                    assert.equal(await dialogs.rawInput('m15.rawInput', '1+1'), '1+1');
                    assert.equal(await dialogs.select('m15.select', ['a','b']), 1);
                    assert.equal(await dialogs.singleChoice('m15.singleChoice', ['a','b'], 0), 1);
                    assert.deepEqual(await dialogs.multiChoice('m15.multiChoice', ['a','b','c'], [0]), [0,2]);
                    assert.equal(await dialogs.singleChoice('m15.cancel', ['a','b'], 0), -1);
                    const progress = await dialogs.progress.show({title:'m15.progress', message:'Working', max:100, value:0});
                    assert(progress.id && progress.shown);
                    const updated = await progress.update({value:50, message:'Half', indeterminate:false});
                    assert.equal(updated.value, 50);
                    assert.equal(updated.message, 'Half');
                    assert.equal(updated.max, 100);
                    assert.equal(await progress.dismiss(), true);
                    assert.equal(await dialogs.progress.dismiss(progress.id), false);
                    await assert.rejects(dialogs.progress.update(progress.id, {value:60}), error => error.category === 'invalid-request');
                    // Leave one progress window open. Provider teardown must close it when this script exits.
                    await dialogs.progress.show({title:'m15.cleanup', indeterminate:true});
                    console.log('m15.dialogs=PASS');
                  })().catch(error => { console.error(error.stack); process.exitCode=1; });
                """.trimIndent(), "dialogs-expansion.cjs", timeoutMs = 60_000, executionId = executionId,
                    runtimeModuleSources = permissions("dialogs"))
            })
            val deadline = SystemClock.elapsedRealtime() + 55_000
            while (!future.isDone && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    val owner = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<NodeDialogActivity>().firstOrNull()
                    val dialog = owner?.dialog
                    if (dialog != null && dialog.isShowing) {
                        val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
                        val title = dialog.findViewById<android.widget.TextView>(titleId)?.text?.toString().orEmpty()
                        if (title.startsWith("m15.") && !title.contains("progress") && !title.contains("cleanup") && clicked.add(title)) {
                            when (title) {
                                "m15.cancel" -> owner.finish()
                                "m15.input" -> dialog.window?.decorView?.findViewWithTag<EditText>("node-dialog-input")?.setText("after")
                                "m15.select", "m15.singleChoice" -> dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
                                "m15.multiChoice" -> dialog.listView.performItemClick(dialog.listView.getChildAt(2), 2, 2)
                            }
                            if (title != "m15.select" && title != "m15.cancel") dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
                        }
                    }
                }
                SystemClock.sleep(40)
            }
            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.errorMessage + "\n" + result.stderr.take(2500), result.succeeded)
            assertTrue(result.stdout, result.stdout.contains("m15.dialogs=PASS"))
            assertEquals(8, clicked.size)
            instrumentation.waitForIdleSync()
            assertTrue("A dialog session leaked after the script exited", NodeDialogActivity.sessions.isEmpty())
            println(result.stdout)
        } finally {
            NodeJsRuntimePluginBridge.cancelScript(context, executionId)
            executor.shutdownNow()
        }
    }
}
