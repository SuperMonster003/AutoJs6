package org.autojs.autojs.engine

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeBridgePushInstrumentationTest {
    private fun permissions(vararg names: String) = mapOf(
        NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest.fromDeclaredCapabilities(names.toList()).toJson(),
    )

    @Test
    fun overlayPushKeepsShownUpdateAndDrainEvents() {
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const overlay = await require('ui.overlay').show({
                content:{type:'Text', id:'label', text:'Push test'},
                window:{width:'wrap_content', height:'wrap_content'},
                disclosure:{title:'AutoJs6 bridge test', text:'Event push verification'}
              });
              const shown = await new Promise(resolve => overlay.once('shown', resolve));
              const update = new Promise(resolve => overlay.once('update', resolve));
              await overlay.update({id:'label', text:'Updated'});
              await update;
              const queue = await overlay.drainEvents();
              if (!overlay.subscriptionId || shown.overlayId !== overlay.id || queue.length < 2) throw new Error('overlay event mismatch');
              await overlay.close();
              console.log('m12.overlay=' + overlay.closed);
            })().catch(e => { console.error(e); process.exitCode = 1; });
            """.trimIndent(),
            "m12-overlay-push.cjs",
            runtimeModuleSources = permissions("ui.overlay"),
        )
        NodePluginTestHarness.assertSucceeded("overlay push", result)
        assertTrue(result.stdout, result.stdout.contains("m12.overlay=true"))
        assertEquals("4", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertTrue(result.nativeValues["embedded_script.bridge_live_event_count"]!!.toInt() >= 2)
    }

    @Test
    fun subscriptionAcknowledgementPrecedesBufferedEvents() {
        val replies = mutableListOf<NodeBridgeResponse>()
        val stream = NodeBridgeEventStream("request", "subscription", replies::add)
        repeat(1000) { stream.publish(org.json.JSONObject().put("type", "event").put("index", it)) }
        assertTrue(replies.isEmpty())
        stream.acknowledge(org.json.JSONObject().put("id", "subscription"))
        assertEquals(129, replies.size)
        assertTrue(!replies.first().event)
        assertEquals("subscription", org.json.JSONObject(replies.first().resultJson!!).getString("subscriptionId"))
        assertEquals(872, org.json.JSONObject(replies[1].resultJson!!).getInt("index"))
        assertEquals(999, org.json.JSONObject(replies.last().resultJson!!).getInt("index"))
        assertEquals(replies.last(), NodeBridgeResponse.fromJson(replies.last().toJson()))
    }

    @Test
    fun accelerometerPushArrivesWithoutDrainAndCloseReleasesTheLoop() {
        // SensorEvent uses BOOTTIME; Node hrtime uses MONOTONIC. Account for suspend time.
        val bootOffsetNs = LongArray(7) { android.os.SystemClock.elapsedRealtimeNanos() - System.nanoTime() }.sorted()[3]
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const sensors = require('sensors');
              const start = process.hrtime.bigint();
              const subscription = sensors.subscribe('accelerometer', undefined, {samplingIntervalMs:20});
              let removedCalls = 0;
              const removed = () => { removedCalls++; };
              subscription.on('event', removed).off('event', removed);
              const first = new Promise(resolve => subscription.once('event', resolve));
              await subscription.ready;
              const readyMs = Number(process.hrtime.bigint() - start) / 1e6;
              const event = await first;
              const received = process.hrtime.bigint();
              const ms = Number(received - start) / 1e6;
              const deliveryMs = Number(received - (BigInt(Math.trunc(event.timestamp)) - BigInt('$bootOffsetNs'))) / 1e6;
              if (removedCalls !== 0 || event.values.length !== 3) throw new Error('sensor event mismatch');
              await subscription.close();
              console.log('m12.sensor.firstMs=' + ms + ' readyMs=' + readyMs + ' deliveryMs=' + deliveryMs);
              console.log('m12.sensor.closed=' + subscription.closed);
            })().catch(e => { console.error(e); process.exitCode = 1; });
            """.trimIndent(),
            "m12-sensor-push.cjs",
            timeoutMs = 10_000,
            runtimeModuleSources = permissions("sensors"),
        )
        NodePluginTestHarness.assertSucceeded("live sensor push", result)
        assertTrue(result.stdout, result.stdout.contains("m12.sensor.closed=true"))
        assertEquals("jni", result.nativeValues["embedded_script.bridge_live_transport"])
        assertEquals("2", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertTrue(result.nativeValues["embedded_script.bridge_live_event_count"]!!.toInt() > 0)
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_event_dropped_count"])
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "\n" + result.stdout) })
    }

    @Test
    fun websocketPushAndFileFallbackBothEchoAndClose() {
        NodeWebSocketBridgeInstrumentationTest.ControlledWebSocketLoopbackServer().use { server ->
            for (transport in listOf("jni", "file")) {
                val result = NodePluginTestHarness.runPluginScript(
                    """
                    (async () => {
                      const socket = await require('websocket').connect('${server.baseUrl}/socket');
                      let removedCalls = 0;
                      const removed = () => { removedCalls++; };
                      socket.once('message', removed).off('message', removed);
                      const echo = new Promise(resolve => {
                        const listener = message => { if (message.text === 'echo:push') { socket.off('message', listener); resolve(message.text); } };
                        socket.on('message', listener);
                      });
                      await socket.send('push');
                      console.log('m12.ws=' + await echo);
                      if (removedCalls !== 0) throw new Error('off did not remove once listener');
                      await socket.close();
                      console.log('m12.ws.closed=' + socket.readyState);
                    })().catch(e => { console.error(e); process.exitCode = 1; });
                    """.trimIndent(),
                    "m12-websocket-$transport.cjs",
                    timeoutMs = 10_000,
                    runtimeModuleSources = permissions("network") + ("autojs6:bridge-live-config" to """{"transport":"$transport"}"""),
                )
                NodePluginTestHarness.assertSucceeded("WebSocket $transport", result)
                assertTrue(result.stdout, result.stdout.contains("m12.ws=echo:push"))
                assertTrue(result.stdout, result.stdout.contains("m12.ws.closed=closed"))
                assertEquals(transport, result.nativeValues["embedded_script.bridge_live_transport"])
                if (transport == "jni") {
                    assertEquals("3", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
                    assertTrue(result.nativeValues["embedded_script.bridge_live_event_count"]!!.toInt() >= 2)
                } else {
                    assertEquals("0", result.nativeValues["embedded_script.bridge_live_event_count"])
                    assertTrue(result.nativeValues["embedded_script.bridge_live_dispatch_count"]!!.toInt() > 3)
                }
            }
        }
    }

    @Test
    fun inputObserverPushRetainsExplicitDrainCompatibility() {
        val result = NodePluginTestHarness.runPluginScript(
            """
            (async () => {
              const sub = await require('input_observer').observeKeys({keys:['volume_up'], actions:['down'], source:'fake'});
              const first = await new Promise(resolve => sub.once('key', resolve));
              const queued = await sub.drainEvents();
              if (first.key !== 'volume_up' || queued.length !== 1 || queued[0].key !== first.key) throw new Error('push/drain mismatch');
              await sub.close();
              console.log('m12.input=' + sub.closed);
            })().catch(e => { console.error(e); process.exitCode = 1; });
            """.trimIndent(),
            "m12-input-push.cjs",
            runtimeModuleSources = permissions("input_observer", "input_observer.keys"),
        )
        NodePluginTestHarness.assertSucceeded("input observer push", result)
        assertTrue(result.stdout, result.stdout.contains("m12.input=true"))
        assertEquals("3", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
    }
}
