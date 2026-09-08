package org.autojs.autojs.engine

import android.os.SystemClock
import android.app.Activity
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.notification.NotificationListenerService
import org.autojs.autojs.ui.log.LogActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeHostEventsInstrumentationTest {
    private var activity: Activity? = null
    @After fun closeActivity() { InstrumentationRegistry.getInstrumentation().runOnMainSync { activity?.finish() } }
    @Test fun batteryBroadcastUsesJniPush() = checkBroadcast("jni")
    @Test fun batteryBroadcastKeepsFileTransportCompatibility() = checkBroadcast("file")

    private fun permissions(vararg capabilities: String) = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to
        NodeBridgePermissionManifest.fromDeclaredCapabilities(capabilities.toList()).toJson())

    private fun checkBroadcast(transport: String) {
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const assert = require('node:assert/strict'), events = require('autojs6:events');
              assert.strictEqual(require('events'), require('node:events'));
              assert.equal(require('events').observeKey, undefined);
              let timeout;
              try {
                const battery = await new Promise((resolve, reject) => {
                  timeout = setTimeout(() => reject(new Error('battery broadcast did not arrive')), 5000);
                  events.once('battery_changed', resolve);
                  events.once('error', reject);
                });
                clearTimeout(timeout);
                assert.equal(battery.type, 'battery_changed');
                assert.equal(battery.source, 'broadcast');
                assert.ok(battery.percent >= 0 && battery.percent <= 100);
                assert.equal(typeof battery.isCharging, 'boolean');
                const subscription = await events.observeBroadcasts();
                assert.equal(subscription.closed, false);
                assert.equal(await subscription.close(), true);
                assert.equal(subscription.closed, true);
                assert.equal(await subscription.close(), false);
                // A closed source can be registered again by the same script.
                const again = new Promise(resolve => events.once('battery_changed', resolve));
                const fresh = await events.observeBroadcasts();
                const value = await again;
                assert.equal(value.type, 'battery_changed');
                assert.notEqual(fresh.id, subscription.id);
                console.log('m15.events.$transport=PASS percent=' + battery.percent);
              } finally { clearTimeout(timeout); await events.close(); }
            })().catch(error => { console.error(error.stack); process.exitCode = 1; });
            """.trimIndent(), "events-battery-$transport.cjs", timeoutMs = 15_000,
            runtimeModuleSources = permissions("events") + mapOf("autojs6:bridge-live-config" to """{"transport":"$transport"}"""),
        )
        NodePluginTestHarness.assertSucceeded("battery events $transport", result)
        assertEquals(transport, result.nativeValues["embedded_script.bridge_live_transport"])
        assertTrue(result.stdout, result.stdout.contains("m15.events.$transport=PASS"))
    }

    @Test fun notificationIdComesFromAndroidListenerOrReportsMissingPermission() {
        val context = NodePluginTestHarness.targetContext
        val granted = NotificationListenerService.isNotificationListenerEnabled(context)
        if (granted) {
            // Instrumentation force-stops the package. A visible activity lets Android reconnect listeners.
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            NotificationListenerService.requestRebindIfPossible(context)
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (NotificationListenerService.instance?.connected != true && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue("Granted notification listener did not connect", NotificationListenerService.instance?.connected == true)
        }
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const assert = require('node:assert/strict'), events = require('autojs6:events'), notifications = require('notifications');
              if (!$granted) {
                await assert.rejects(events.observeNotification(), error => error.category === 'permission-denied');
                console.log('m15.notification=PERMISSION_DENIED');
                return;
              }
              let timeout, id;
              try {
                const received = new Promise((resolve, reject) => {
                  timeout = setTimeout(() => reject(new Error('self notification was not observed')), 5000);
                  events.on('notification', event => { if (event.id === 151200 && event.title === 'Node event fixture') resolve(event); });
                  events.once('error', reject);
                });
                await events.observeNotification();
                id = await notifications.notify({id: 151200, title: 'Node event fixture', text: 'Roadmap M15.2', channel: 'node-events-test', priority: 'low'});
                const event = await received;
                assert.equal(event.id, id);
                assert.equal(event.packageName, '${context.packageName}');
                assert.equal(event.text, 'Roadmap M15.2');
                assert.ok(event.key && event.postTime > 0);
                console.log('m15.notification=PASS id=' + event.id);
              } finally {
                clearTimeout(timeout);
                if (id !== undefined) await notifications.cancel(id);
                await events.close();
              }
            })().catch(error => { console.error(error.stack); process.exitCode = 1; });
            """.trimIndent(), "events-notification.cjs", timeoutMs = 15_000,
            runtimeModuleSources = permissions("events", "events.notification", "notifications"),
        )
        NodePluginTestHarness.assertSucceeded("Android notification events", result)
        assertTrue(result.stdout, result.stdout.contains(if (granted) "m15.notification=PASS" else "m15.notification=PERMISSION_DENIED"))
        println(result.stdout)
    }

    @Test fun declaredKeyAndToastObservationChecksAndroidAccessibility() {
        val available = org.autojs.autojs.core.accessibility.AccessibilityService.registry.isOperational
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const assert = require('node:assert/strict'), events = require('autojs6:events');
              try {
                for (const method of ['observeToast', 'observeKey']) {
                  if ($available) {
                    const subscription = await events[method]();
                    assert.equal(subscription.closed, false);
                    await subscription.close();
                    assert.equal(subscription.closed, true);
                  } else await assert.rejects(events[method](), error => error.category === 'permission-denied');
                }
              } finally { await events.close(); }
              console.log('m15.events.accessibility=' + $available);
            })().catch(error => { console.error(error.stack); process.exitCode = 1; });
            """.trimIndent(), "events-accessibility.cjs", runtimeModuleSources = permissions("events", "events.key", "events.toast"),
        )
        NodePluginTestHarness.assertSucceeded("accessibility event source", result)
    }

    @Test fun undeclaredObservationCapabilitiesDoNotReachAndroid() {
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const assert = require('node:assert/strict'), events = require('autojs6:events');
              for (const method of ['observeNotification', 'observeToast', 'observeKey']) {
                await assert.rejects(events[method](), error => error.code === 'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED');
              }
            })().catch(error => { console.error(error.stack); process.exitCode = 1; });
            """.trimIndent(), "events-permissions.cjs", runtimeModuleSources = permissions("events"),
        )
        NodePluginTestHarness.assertSucceeded("event capability declarations", result)
    }
}
