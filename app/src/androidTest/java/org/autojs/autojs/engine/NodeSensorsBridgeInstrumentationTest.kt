package org.autojs.autojs.engine

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeSensorsBridgeInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
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
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node sensors bridge fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-sensors-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create sensors bridge working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true, once: () => "shadow" };"""
        File(workDir, "node_modules/sensors").apply {
            check(mkdirs()) { "Failed to create sensors shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText(shadowPackageSource)

        val result = NodePluginTestHarness.runPluginScript(
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
            moduleSources = mapOf("node_modules/sensors/index.js" to shadowPackageSource),
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to
                NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("sensors")).toJson()),
            timeoutMs = 30_000L,
        )

        NodePluginTestHarness.assertSucceeded("sensors fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.resolve=autojs6:sensors")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.shadow=false")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.frozen=true")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.methods=function|function|function")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.available=accelerometer,light|true")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.once=accelerometer|1,2,3|true|true")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.sub=sensor-fake-1|light|true|true|light:44:true")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.invalid=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}|${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.requests=sensors.getAvailableSensors|sensors.once|sensors.subscribe|sensors.drainEvents|sensors.unsubscribe")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.permissions=sensors")
        NodePluginTestHarness.assertStdoutContains("sensors fake bridge", result, "sensors.pending=0")
    }

    @Test
    fun jsSensorsRequiresManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node sensors manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(emptyList())
        val result = NodePluginTestHarness.runPluginScript(
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

        NodePluginTestHarness.assertSucceeded("sensors manifest capability denial", result)
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

    @Test
    fun androidProviderOnceObservesLiveSensorEventWhenAvailable() {
        val provider = sensorProvider("sensors-once-provider")
        val type = firstAvailableSensorType(provider)
        assumeTrue("No supported Android sensor is available for live sensors once gate.", type != null)
        val sensorType = checkNotNull(type)

        val once = dispatch(
            provider,
            "once",
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", sensorType)
                        .put("samplingIntervalMs", 100)
                        .put("timeoutMs", 5_000),
                )
                .toString(),
        )

        assertTrue(once.toString(), once.ok)
        val event = JSONObject(once.resultJson.orEmpty())
        assertEquals(sensorType, event.getString("type"))
        assertTrue(event.toString(), event.getLong("timestamp") > 0L)
        assertTrue(event.toString(), event.getJSONArray("values").length() >= 1)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
    }

    @Test
    fun androidProviderSubscribeDrainsEventsAndUnsubscribesWhenAvailable() {
        val provider = sensorProvider("sensors-subscribe-provider")
        val type = firstAvailableSensorType(provider)
        assumeTrue("No supported Android sensor is available for live sensors subscription gate.", type != null)
        val sensorType = checkNotNull(type)

        val subscribe = dispatch(
            provider,
            "subscribe",
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", sensorType)
                        .put("samplingIntervalMs", 1)
                        .put("maxQueueSize", 2)
                        .put("timeoutMs", 1_000),
                )
                .toString(),
        )

        if (!subscribe.ok) {
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, subscribe.error?.code)
            return
        }
        val subscription = JSONObject(subscribe.resultJson.orEmpty())
        assertEquals(sensorType, subscription.getString("type"))
        assertEquals(20, subscription.getInt("samplingIntervalMs"))
        assertEquals(2, subscription.getInt("maxQueueSize"))
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])

        val event = waitForSensorEvent(provider, subscription.getString("id"))
        assertEquals(sensorType, event.getString("type"))
        assertTrue(event.toString(), event.getLong("timestamp") > 0L)
        assertTrue(event.toString(), event.getJSONArray("values").length() >= 1)

        val unsubscribe = dispatch(
            provider,
            "unsubscribe",
            JSONArray().put(JSONObject().put("id", subscription.getString("id"))).toString(),
        )
        assertTrue(unsubscribe.toString(), unsubscribe.ok)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
    }

    @Test
    fun androidProviderEnforcesLiveSubscriptionLimitWhenAvailable() {
        val provider = sensorProvider("sensors-limit-provider")
        val type = firstAvailableSensorType(provider)
        assumeTrue("No supported Android sensor is available for live sensors limit gate.", type != null)
        val sensorType = checkNotNull(type)
        val subscriptionIds = mutableListOf<String>()

        try {
            repeat(4) { index ->
                val subscribe = dispatch(
                    provider,
                    "subscribe",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", sensorType)
                                .put("samplingIntervalMs", 250)
                                .put("maxQueueSize", 1)
                                .put("timeoutMs", 1_000 + index),
                        )
                        .toString(),
                )
                assertTrue(subscribe.toString(), subscribe.ok)
                subscriptionIds += JSONObject(subscribe.resultJson.orEmpty()).getString("id")
            }

            val overLimit = dispatch(
                provider,
                "subscribe",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", sensorType)
                            .put("samplingIntervalMs", 250)
                            .put("maxQueueSize", 1)
                            .put("timeoutMs", 1_000),
                    )
                    .toString(),
            )
            assertFalse(overLimit.toString(), overLimit.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, overLimit.error?.code)
            assertEquals("4", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        } finally {
            subscriptionIds.forEach { id ->
                dispatch(provider, "unsubscribe", JSONArray().put(JSONObject().put("id", id)).toString())
            }
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        }
    }

    @Test
    fun androidProviderReportsUnavailableWhenSensorManagerMissing() {
        val provider = sensorProvider("sensors-missing-manager-provider", contextWithoutSensors())

        val available = dispatch(provider, "getAvailableSensors", "[]")
        assertTrue(available.toString(), available.ok)
        assertEquals(0, JSONArray(available.resultJson.orEmpty()).length())

        val once = dispatch(
            provider,
            "once",
            JSONArray()
                .put(
                    JSONObject()
                        .put("type", "accelerometer")
                        .put("samplingIntervalMs", 100)
                        .put("timeoutMs", 1_000),
                )
                .toString(),
        )
        assertFalse(once.toString(), once.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, once.error?.code)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
    }

    private fun sensorProvider(id: String, providerContext: Context = context): AndroidNodeBridgeCapabilityProvider {
        val workDir = File(context.cacheDir, "node-bridge-$id").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create sensors provider directory: $absolutePath" }
        }
        return AndroidNodeBridgeCapabilityProvider(
            providerContext,
            NodeBridgeEngineInfo.fromContext(
                providerContext,
                id = id,
                sourceName = "$id.cjs",
                cwd = workDir.absolutePath,
            ),
        )
    }

    private fun contextWithoutSensors(): Context {
        return object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getSystemService(name: String): Any? {
                return if (name == Context.SENSOR_SERVICE) {
                    null
                } else {
                    super.getSystemService(name)
                }
            }
        }
    }

    private fun firstAvailableSensorType(provider: NodeBridgeCapabilityProvider): String? {
        val available = dispatch(provider, "getAvailableSensors", "[]")
        assertTrue(available.toString(), available.ok)
        val sensors = JSONArray(available.resultJson.orEmpty())
        val types = (0 until sensors.length()).map { index -> sensors.getJSONObject(index).getString("type") }.toSet()
        return listOf("accelerometer", "gyroscope", "light").firstOrNull { it in types }
    }

    private fun waitForSensorEvent(provider: NodeBridgeCapabilityProvider, subscriptionId: String): JSONObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val drained = dispatch(
                provider,
                "drainEvents",
                JSONArray().put(JSONObject().put("id", subscriptionId)).toString(),
            )
            assertTrue(drained.toString(), drained.ok)
            val events = JSONArray(drained.resultJson.orEmpty())
            for (index in 0 until events.length()) {
                val item = events.getJSONObject(index)
                if (item.optString("type") == "event") {
                    return item.getJSONObject("event")
                }
            }
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for live sensor event from $subscriptionId")
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
