package org.autojs.autojs.engine

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeSensorsBridgeInstrumentationTest {

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
    fun kotlinSensorsBridgeRegistryAndManifestAreStable() {
        listOf("getAvailableSensors", "once", "subscribe", "unsubscribe", "drainEvents").forEach { method ->
            assertTrue(NodeBridgeModules.supports("sensors", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.SENSORS),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("sensors", method),
            )
        }
        assertFalse(NodeBridgeModules.supports("sensors", "registerRawListener"))
        assertEquals(emptyList<String>(), NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf("sensors")))
    }

    @Test
    fun jsSensorsBridgeUsesFakeTransportAndBlocksShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node sensors bridge fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-sensors-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create sensors bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/sensors").apply {
            check(mkdirs()) { "Failed to create sensors shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, once: () => "shadow" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              let drained = false;
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.method === "getAvailableSensors") {
                    result = [
                      { type: "accelerometer", name: "Fake Accelerometer", vendor: "AutoJs6", version: 1 },
                      { type: "light", name: "Fake Light", vendor: "AutoJs6", version: 1 }
                    ];
                  } else if (request.method === "once") {
                    result = { type: request.args[0].type, timestamp: 11, accuracy: 3, values: [1, 2, 3] };
                  } else if (request.method === "subscribe") {
                    result = { id: "sensor-fake-1", type: request.args[0].type, samplingIntervalMs: request.args[0].samplingIntervalMs, maxQueueSize: request.args[0].maxQueueSize };
                  } else if (request.method === "drainEvents") {
                    result = drained ? [] : [{ type: "event", event: { type: "light", timestamp: 12, accuracy: 3, values: [44] } }];
                    drained = true;
                  } else if (request.method === "unsubscribe") {
                    result = null;
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  })));
                }
              });

              const sensors = require("sensors");
              const available = await sensors.getAvailableSensors({ timeoutMs: 1000 });
              const once = await sensors.once("accelerometer", { samplingIntervalMs: 100, timeoutMs: 1000 });
              const callbacks = [];
              let callbackReadyResolve;
              const callbackReady = new Promise((resolve) => { callbackReadyResolve = resolve; });
              const sub = sensors.subscribe("light", (event) => {
                callbacks.push(event.type + ":" + event.values.join(",") + ":" + Object.isFrozen(event.values));
                callbackReadyResolve();
              }, { samplingIntervalMs: 125, maxQueueSize: 3, drainIntervalMs: 1, timeoutMs: 1000 });
              await sub.ready;
              await callbackReady;
              await sub.close({ timeoutMs: 1000 });

              let invalidType = "";
              let invalidCallback = "";
              try {
                await sensors.once("pressure");
              } catch (error) {
                invalidType = String(error && (error.autojs6Code || error.code || ""));
              }
              try {
                sensors.subscribe("light", null);
              } catch (error) {
                invalidCallback = String(error && (error.autojs6Code || error.code || ""));
              }

              console.log("sensors.resolve=" + require.resolve("sensors"));
              console.log("sensors.shadow=" + (sensors.shadow === true));
              console.log("sensors.frozen=" + Object.isFrozen(sensors));
              console.log("sensors.methods=" + [typeof sensors.getAvailableSensors, typeof sensors.once, typeof sensors.subscribe].join("|"));
              console.log("sensors.available=" + available.map((item) => item.type).join(",") + "|" + Object.isFrozen(available));
              console.log("sensors.once=" + [once.type, once.values.join(","), Object.isFrozen(once), Object.isFrozen(once.values)].join("|"));
              console.log("sensors.sub=" + [sub.id, sub.type, sub.closed, Object.isFrozen(sub), callbacks.join(";")].join("|"));
              console.log("sensors.invalid=" + [invalidType, invalidCallback].join("|"));
              console.log("sensors.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("sensors.permissions=" + requests[0].permissions.join("|"));
              console.log("sensors.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-sensors-fake.js>",
            workingDirectory = workDir,
            timeoutMs = 30_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("sensors fake bridge", result)
        assertTrue(result.stdout.contains("sensors.resolve=sensors"))
        assertTrue(result.stdout.contains("sensors.shadow=false"))
        assertTrue(result.stdout.contains("sensors.frozen=true"))
        assertTrue(result.stdout.contains("sensors.methods=function|function|function"))
        assertTrue(result.stdout.contains("sensors.available=accelerometer,light|true"))
        assertTrue(result.stdout.contains("sensors.once=accelerometer|1,2,3|true|true"))
        assertTrue(result.stdout.contains("sensors.sub=sensor-fake-1|light|true|true|light:44:true"))
        assertTrue(result.stdout.contains("sensors.invalid=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}|${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}"))
        assertTrue(result.stdout.contains("sensors.requests=sensors.getAvailableSensors|sensors.once|sensors.subscribe|sensors.drainEvents|sensors.unsubscribe"))
        assertTrue(result.stdout.contains("sensors.permissions=sensors"))
        assertTrue(result.stdout.contains("sensors.pending=0"))
    }

    @Test
    fun jsSensorsRequiresManifestCapabilityBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node sensors manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(emptyList())
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  requests.push(JSON.parse(message));
                }
              });
              let code = "";
              let missing = "";
              try {
                await require("sensors").once("accelerometer", { timeoutMs: 1000 });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("sensors.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-sensors-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("sensors manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "sensors.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|sensors|0|0",
            ),
        )
    }

    @Test
    fun androidProviderListsSensorsAndCleansUpLiveSubscriptionWhenAvailable() {
        val workDir = File(context.cacheDir, "node-bridge-sensors-provider").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create sensors provider directory: $absolutePath" }
            resolve("project.json").writeText(
                """
                {
                  "type": "node",
                  "node": {
                    "permissions": ["sensors"]
                  }
                }
                """.trimIndent(),
            )
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "sensors-provider",
                sourceName = "sensors.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val available = dispatch(provider, "getAvailableSensors", "[]")
        assertTrue(available.toString(), available.ok)

        val subscribe = dispatch(
            provider,
            "subscribe",
            """[{"type":"accelerometer","samplingIntervalMs":250,"maxQueueSize":4,"timeoutMs":1000}]""",
        )
        if (!subscribe.ok) {
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, subscribe.error?.code)
            return
        }
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.SENSOR_SUBSCRIPTION))
        provider.destroy(NodeBridgeError.processDead("sensors provider destroy"))
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.leak_count"])
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        method: String,
        argsJson: String,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "sensors-${System.nanoTime()}",
                module = "sensors",
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("sensors"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for sensors.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing sensors.$method response" }
    }

    private fun activeTypeCount(diagnostics: Map<String, String>, type: NodeBridgeResourceType): Int {
        return JSONObject(diagnostics["embedded_script.bridge_resource.active_types"].orEmpty()).optInt(type.name, 0)
    }
}
