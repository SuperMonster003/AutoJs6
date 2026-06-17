package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeBridgeResourceLeakStressInstrumentationTest {

    private val arguments = InstrumentationRegistry.getArguments()
    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun jsBridgeModulesDrainPendingCallsAcrossStressLoop() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node v1.1 bridge resource stress fake transport")

        val iterations = stressIterations()
        val workDir = File(context.cacheDir, "node-bridge-resource-stress-js").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge resource stress JS working directory: $absolutePath" }
        }

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const buckets = Object.create(null);
              const requests = [];
              let nextNotificationId = 810000;
              let nextWebSocketId = 1;
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.module === "ocr") {
                    result = [{ text: "stress", confidence: 0.9, bounds: { left: 1, top: 2, right: 3, bottom: 4 } }];
                  } else if (request.module === "storage") {
                    const storeName = request.args[0];
                    const key = request.args[1];
                    const bucket = buckets[storeName] || (buckets[storeName] = Object.create(null));
                    if (request.method === "get") {
                      result = Object.prototype.hasOwnProperty.call(bucket, key) ? { found: true, value: bucket[key] } : { found: false };
                    } else if (request.method === "put") {
                      bucket[key] = request.args[2];
                    } else if (request.method === "remove") {
                      delete bucket[key];
                    } else if (request.method === "clear") {
                      Object.keys(bucket).forEach((entry) => delete bucket[entry]);
                    } else if (request.method === "keys") {
                      result = Object.keys(bucket).sort();
                    } else if (request.method === "contains") {
                      result = Object.prototype.hasOwnProperty.call(bucket, key);
                    }
                  } else if (request.module === "notifications") {
                    result = request.method === "notify" ? (request.args[0].id || nextNotificationId++) : null;
                  } else if (request.module === "sensors") {
                    if (request.method === "subscribe") {
                      result = {
                        id: "sensor-stress-" + request.id,
                        type: request.args[0].type,
                        samplingIntervalMs: request.args[0].samplingIntervalMs || 250,
                        maxQueueSize: request.args[0].maxQueueSize || 1
                      };
                    } else if (request.method === "once") {
                      result = { type: request.args[0].type, timestamp: 1, accuracy: 3, values: [1, 2, 3] };
                    } else if (request.method === "drainEvents") {
                      result = [];
                    }
                  } else if (request.module === "accessibility") {
                    result = request.method === "findAll" ? [] : true;
                  } else if (request.module === "image" || request.module === "images") {
                    if (request.method === "readImage") {
                      result = { id: "image:" + request.args[0], width: 10, height: 20 };
                    } else if (request.method === "grayscale") {
                      result = { id: request.args[0].id + ":gray", width: request.args[0].width, height: request.args[0].height };
                    } else if (request.method === "threshold") {
                      result = { id: request.args[0].id + ":binary", width: request.args[0].width, height: request.args[0].height };
                    } else if (request.method === "matchTemplate") {
                      result = [];
                    } else if (request.method === "findColor") {
                      result = { x: 1, y: 2 };
                    } else if (request.method === "findMultiColors") {
                      result = { x: 3, y: 4 };
                    } else if (request.method === "saveImage" || request.method === "recycle") {
                      result = true;
                    }
                  } else if (request.module === "ui") {
                    result = request.method === "showLayout" ? { id: "ui-stress-" + request.id } : null;
                  } else if (request.module === "shell") {
                    result = { code: 124, stdout: "", stderr: "timed out" };
                  } else if (request.module === "websocket") {
                    if (request.method === "connect") {
                      result = { id: "ws-stress-" + nextWebSocketId++, url: request.args[0].url, readyState: "open" };
                    } else if (request.method === "send") {
                      result = true;
                    } else if (request.method === "drainEvents") {
                      result = [];
                    }
                  }
                  setImmediate(() => {
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result
                    }));
                  });
                }
              });

              const ocr = require("ocr");
              const storage = require("storage");
              const notifications = require("notifications");
              const sensors = require("sensors");
              const accessibility = require("accessibility");
              const image = require("image");
              const ui = require("ui");
              const shell = require("shell");
              const websocket = require("websocket");

              for (let index = 0; index < $iterations; index += 1) {
                const handle = { id: "image-" + index, width: 10, height: 20 };
                await ocr.recognize(handle, { timeoutMs: 1000 });

                const store = storage.create("stress");
                await store.put("key-" + index, { index }, { timeoutMs: 1000 });
                await store.get("key-" + index, null, { timeoutMs: 1000 });
                await store.remove("key-" + index, { timeoutMs: 1000 });

                const notificationId = await notifications.notify({
                  id: 820000 + index,
                  title: "Stress",
                  text: "iteration " + index,
                  channel: "node-stress"
                }, { timeoutMs: 1000 });
                await notifications.cancel(notificationId, { timeoutMs: 1000 });

                const subscription = sensors.subscribe("accelerometer", () => {}, {
                  samplingIntervalMs: 250,
                  maxQueueSize: 1,
                  drainIntervalMs: 1000,
                  timeoutMs: 1000
                });
                await subscription.ready;
                await subscription.close({ timeoutMs: 1000 });

                await accessibility.findAll(accessibility.textContains("Stress"), { timeoutMs: 1000 });

                const source = await image.readImage("source-" + index + ".png", { timeoutMs: 1000 });
                const gray = await image.grayscale(source, { timeoutMs: 1000 });
                const binary = await image.threshold(gray, { threshold: 128, timeoutMs: 1000 });
                await image.matchTemplate(binary, source, { timeoutMs: 1000 });
                await image.findColor(binary, "#ff0000", { timeoutMs: 1000 });
                await image.recycle(binary, { timeoutMs: 1000 });
                await image.recycle(gray, { timeoutMs: 1000 });
                await image.recycle(source, { timeoutMs: 1000 });

                const layout = await ui.showLayout({ type: "Text", id: "title", text: "Stress " + index }, { timeoutMs: 1000 });
                await layout.close({ timeoutMs: 1000 });

                await shell.exec("sleep 1", { timeoutMs: 1 });

                const socket = await websocket.connect("wss://example.invalid/stress-" + index, { timeoutMs: 1000 });
                await socket.close(1000, "stress");
              }

              const modules = Array.from(new Set(requests.map((request) => request.module))).sort();
              console.log("bridge.stress.completed=" + $iterations);
              console.log("bridge.stress.modules=" + modules.join(","));
              console.log("bridge.stress.requests=" + requests.length);
              console.log("bridge.stress.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-resource-stress.js>",
            workingDirectory = workDir,
            timeoutMs = 60_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge resource stress fake transport", result)
        assertTrue(result.stdout, result.stdout.contains("bridge.stress.completed=$iterations"))
        assertTrue(
            result.stdout,
            result.stdout.contains("bridge.stress.modules=accessibility,image,notifications,ocr,sensors,shell,storage,ui,websocket"),
        )
        assertTrue(result.stdout, result.stdout.contains("bridge.stress.pending=0"))
    }

    @Test
    fun androidProviderBridgeResourcesReturnToZeroAcrossStressLoop() {
        val iterations = stressIterations()
        val workDir = File(context.cacheDir, "node-bridge-resource-stress-provider").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge resource stress provider directory: $absolutePath" }
            resolve("project.json").writeText(
                """
                {
                  "type": "node",
                  "node": {
                    "permissions": [
                      "ocr",
                      "image",
                      "storage",
                      "notifications",
                      "sensors",
                      "accessibility",
                      "ui",
                      "shell",
                      "network"
                    ]
                  }
                }
                """.trimIndent(),
            )
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "bridge-resource-stress-provider",
                sourceName = "bridge-resource-stress.cjs",
                cwd = workDir.absolutePath,
            ),
            bridgeLimits = NodeBridgeLimits.defaults.copy(
                maxShellCommands = 1,
                maxWebSocketConnections = 1,
                accessibilityQueriesPerSecond = 60,
            ),
        )

        repeat(iterations) { index ->
            val ocr = dispatch(
                provider,
                module = "ocr",
                method = "recognize",
                argsJson = """[{"id":"image-$index","width":10,"height":20},{"timeoutMs":1000}]""",
                permissions = listOf("ocr", "image"),
            )
            assertFalse("OCR provider is expected to fail closed in normal builds", ocr.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, ocr.error?.code)
            assertNoActiveBridgeResources(provider, "ocr-$index")

            storageRoundTrip(provider, index)
            assertNoActiveBridgeResources(provider, "storage-$index")

            notificationRoundTrip(provider, index)
            assertNoActiveBridgeResources(provider, "notifications-$index")

            sensorRoundTrip(provider)
            assertNoActiveBridgeResources(provider, "sensors-$index")

            val accessibility = dispatch(
                provider,
                module = "accessibility",
                method = "findAll",
                argsJson = """[{"type":"autojs6.accessibility.selector","version":1,"textContains":"Stress"},{"timeoutMs":1000}]""",
                permissions = listOf("accessibility"),
            )
            assertTrue(
                "Accessibility provider should either succeed with an enabled service or fail closed without resources: $accessibility",
                accessibility.ok || accessibility.error?.code == NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED,
            )
            assertNoActiveBridgeResources(provider, "accessibility-$index")

            val image = dispatch(
                provider,
                module = "image",
                method = "grayscale",
                argsJson = """[{"id":"image-$index","width":10,"height":20},{"timeoutMs":1000}]""",
                permissions = listOf("image"),
            )
            assertFalse("Image provider is expected to fail closed until connected", image.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, image.error?.code)
            assertNoActiveBridgeResources(provider, "image-$index")

            val ui = dispatch(
                provider,
                module = "ui",
                method = "showLayout",
                argsJson = """[{"type":"Text","text":"Stress $index"},{"timeoutMs":1000}]""",
                permissions = listOf("ui"),
            )
            assertFalse("UI provider is expected to fail closed until an Activity-owned provider is connected", ui.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, ui.error?.code)
            assertNoActiveBridgeResources(provider, "ui-$index")

            val shell = dispatch(
                provider,
                module = "shell",
                method = "exec",
                argsJson = """["sleep 1",{"timeoutMs":50}]""",
                permissions = listOf("shell"),
                awaitSeconds = 8L,
            )
            assertTrue(shell.toString(), shell.ok)
            assertEquals(124, JSONObject(shell.resultJson.orEmpty()).getInt("code"))
            assertNoActiveBridgeResources(provider, "shell-$index")
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.shell_active_command_count"])

            val websocket = dispatch(
                provider,
                module = "websocket",
                method = "connect",
                argsJson = """[{"url":"wss://example.invalid/stress-$index","timeoutMs":1000}]""",
                permissions = listOf("network"),
            )
            assertFalse("WebSocket provider should stay disabled in normal builds", websocket.ok)
            assertEquals(NodeRuntimeErrorCodes.NETWORK_DISABLED, websocket.error?.code)
            assertNoActiveBridgeResources(provider, "websocket-$index")
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.websocket_active_connection_count"])
        }

        provider.destroy(NodeBridgeError.processDead("bridge resource stress finished"))
        val diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("0", diagnostics["embedded_script.bridge_resource.leak_count"])
        assertEquals("0", diagnostics["embedded_script.bridge_resource.close_error_count"])
        assertEquals("0", diagnostics["embedded_script.shell_active_command_count"])
        assertEquals("0", diagnostics["embedded_script.websocket_active_connection_count"])
    }

    private fun storageRoundTrip(provider: AndroidNodeBridgeCapabilityProvider, index: Int) {
        val store = "stress"
        val key = "key-$index"
        assertOk(dispatch(provider, "storage", "put", """["$store","$key",{"index":$index}]""", listOf("storage")))
        val get = dispatch(provider, "storage", "get", """["$store","$key"]""", listOf("storage"))
        assertOk(get)
        val result = JSONObject(get.resultJson.orEmpty())
        assertTrue(result.toString(), result.getBoolean("found"))
        assertEquals(index, result.getJSONObject("value").getInt("index"))
        assertOk(dispatch(provider, "storage", "remove", """["$store","$key"]""", listOf("storage")))
    }

    private fun notificationRoundTrip(provider: AndroidNodeBridgeCapabilityProvider, index: Int) {
        val id = 830000 + index
        val notify = dispatch(
            provider,
            module = "notifications",
            method = "notify",
            argsJson = """[{"id":$id,"title":"AutoJs6 Node","text":"Stress $index","channel":"node-stress","ongoing":true}]""",
            permissions = listOf("notifications"),
        )
        if (!notify.ok) {
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, notify.error?.code)
            return
        }
        assertEquals(id, jsonInt(notify.resultJson.orEmpty()))
        assertEquals(1, activeTypeCount(provider, NodeBridgeResourceType.NOTIFICATION))
        assertOk(dispatch(provider, "notifications", "cancel", "[$id]", listOf("notifications")))
    }

    private fun sensorRoundTrip(provider: AndroidNodeBridgeCapabilityProvider) {
        val subscribe = dispatch(
            provider,
            module = "sensors",
            method = "subscribe",
            argsJson = """[{"type":"accelerometer","samplingIntervalMs":250,"maxQueueSize":1,"timeoutMs":1000}]""",
            permissions = listOf("sensors"),
        )
        if (!subscribe.ok) {
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, subscribe.error?.code)
            return
        }
        val subscription = JSONObject(subscribe.resultJson.orEmpty())
        assertTrue(subscription.getString("id").isNotBlank())
        assertEquals(1, activeTypeCount(provider, NodeBridgeResourceType.SENSOR_SUBSCRIPTION))
        assertOk(
            dispatch(
                provider,
                module = "sensors",
                method = "unsubscribe",
                argsJson = """[{"id":"${subscription.getString("id")}","timeoutMs":1000}]""",
                permissions = listOf("sensors"),
            ),
        )
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
        awaitSeconds: Long = 5L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "$module-$method-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method response", latch.await(awaitSeconds, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
    }

    private fun assertOk(response: NodeBridgeResponse) {
        assertTrue(response.toString(), response.ok)
    }

    private fun assertNoActiveBridgeResources(provider: AndroidNodeBridgeCapabilityProvider, label: String) {
        val diagnostics = provider.nativeDiagnostics()
        assertEquals("$label active_count diagnostics=$diagnostics", "0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("$label active_types diagnostics=$diagnostics", "{}", diagnostics["embedded_script.bridge_resource.active_types"])
        assertEquals("$label close_error_count diagnostics=$diagnostics", "0", diagnostics["embedded_script.bridge_resource.close_error_count"])
    }

    private fun activeTypeCount(provider: AndroidNodeBridgeCapabilityProvider, type: NodeBridgeResourceType): Int {
        return JSONObject(provider.nativeDiagnostics()["embedded_script.bridge_resource.active_types"].orEmpty()).optInt(type.name, 0)
    }

    private fun jsonInt(raw: String): Int {
        return (JSONTokener(raw).nextValue() as Number).toInt()
    }

    private fun stressIterations(): Int =
        (arguments.getString(ARG_ITERATIONS)?.toIntOrNull() ?: DEFAULT_ITERATIONS).coerceIn(1, MAX_ITERATIONS)

    private companion object {
        private const val DEFAULT_ITERATIONS = 8
        private const val MAX_ITERATIONS = 50
        private const val ARG_ITERATIONS = "autojs.nodejs.bridgeStress.iterations"
    }
}
