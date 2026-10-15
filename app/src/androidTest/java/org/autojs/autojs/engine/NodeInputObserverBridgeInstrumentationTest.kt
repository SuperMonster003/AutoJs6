package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeInputObserverBridgeInstrumentationTest {

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
    fun kotlinInputObserverRegistryManifestAndFakeProviderAreStable() {
        listOf("observeKeys", "drainEvents", "close", "getAvailableSources").forEach { method ->
            assertTrue(NodeBridgeModules.supports("input_observer", method))
        }
        assertEquals(
            listOf(NodeBridgePermissionManifest.INPUT_OBSERVER),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("input_observer", "getAvailableSources"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("input_observer", "observeKeys"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("input_observer", "drainEvents"),
        )
        assertFalse(NodeBridgeModules.supports("input_observer", "observeTouch"))
        assertEquals(emptyList<String>(), NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf("input_observer")))

        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val observe = dispatch(
            provider,
            method = "observeKeys",
            argsJson = """[{"type":"key","keys":["volume_up"],"actions":["down"],"source":"fake"},{"maxQueueSize":4}]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        assertTrue(observe.toString(), observe.ok)
        val snapshot = JSONObject(observe.resultJson.orEmpty())
        assertEquals("key", snapshot.getString("type"))
        assertEquals("fake", snapshot.getString("source"))
        assertEquals("volume_up", snapshot.getJSONArray("keys").getString(0))

        val id = snapshot.getString("id")
        val drain = dispatch(
            provider,
            method = "drainEvents",
            argsJson = """["$id",{"maxEvents":16}]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        assertTrue(drain.toString(), drain.ok)
        val events = JSONArray(drain.resultJson.orEmpty())
        assertEquals("volume_up", events.getJSONObject(0).getString("key"))
        assertEquals(24, events.getJSONObject(0).getInt("keyCode"))
        assertEquals("down", events.getJSONObject(0).getString("action"))

        val close = dispatch(
            provider,
            method = "close",
            argsJson = """["$id"]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        assertTrue(close.toString(), close.ok)
        assertEquals("true", close.resultJson)

        val secondClose = dispatch(
            provider,
            method = "close",
            argsJson = """["$id"]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        assertTrue(secondClose.toString(), secondClose.ok)
        assertEquals("false", secondClose.resultJson)
    }

    @Test
    fun kotlinInputObserverAccessibilityLiveSourceHasStableGate() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val sources = dispatch(
            provider,
            method = "getAvailableSources",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER),
        )
        assertTrue(sources.toString(), sources.ok)
        val sourceArray = JSONArray(sources.resultJson.orEmpty())
        val accessibility = (0 until sourceArray.length())
            .map { sourceArray.getJSONObject(it) }
            .first { it.getString("id") == "accessibility" }

        val observe = dispatch(
            provider,
            method = "observeKeys",
            argsJson = """[{"type":"key","keys":["volumeUp"],"actions":["down"],"source":"accessibility"},{"maxQueueSize":4}]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )

        if (accessibility.getBoolean("available")) {
            assertTrue(observe.toString(), observe.ok)
            val snapshot = JSONObject(observe.resultJson.orEmpty())
            assertEquals("accessibility", snapshot.getString("source"))
            assertEquals("volume_up", snapshot.getJSONArray("keys").getString(0))
            val close = dispatch(
                provider,
                method = "close",
                argsJson = """["${snapshot.getString("id")}"]""",
                permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
            )
            assertTrue(close.toString(), close.ok)
        } else {
            assertFalse(observe.toString(), observe.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, observe.error?.code)
            assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, observe.error?.category)
        }
    }

    @Test
    fun kotlinInputObserverCleanupDiagnosticsAndQueueOverflowAreBounded() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val observe = dispatch(
            provider,
            method = "observeKeys",
            argsJson = """[{"type":"key","keys":["volume_up","volume_down"],"actions":["down","up"],"source":"fake"},{"maxQueueSize":1}]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        assertTrue(observe.toString(), observe.ok)
        val snapshot = JSONObject(observe.resultJson.orEmpty())
        assertEquals(1, snapshot.getInt("pendingEventCount"))
        assertEquals(3, snapshot.getInt("droppedCount"))
        assertEquals(0, snapshot.getInt("liveListenerCount"))

        val beforeDestroy = provider.nativeDiagnostics()
        assertEquals("1", beforeDestroy.getValue("embedded_script.input_observer_active_subscription_count"))
        assertEquals("1", beforeDestroy.getValue("embedded_script.input_observer_pending_event_count"))
        assertEquals("3", beforeDestroy.getValue("embedded_script.input_observer_dropped_event_count"))
        assertEquals("0", beforeDestroy.getValue("embedded_script.input_observer_live_listener_count"))

        provider.destroy(NodeBridgeError.processDead())
        val afterDestroy = provider.nativeDiagnostics()
        assertEquals("0", afterDestroy.getValue("embedded_script.input_observer_active_subscription_count"))
        assertEquals("0", afterDestroy.getValue("embedded_script.input_observer_pending_event_count"))
        assertEquals("0", afterDestroy.getValue("embedded_script.input_observer_dropped_event_count"))
        assertEquals("0", afterDestroy.getValue("embedded_script.input_observer_live_listener_count"))
    }

    @Test
    fun kotlinInputObserverRejectsInterceptionAndChecksAccessibilityForScheduledSource() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)

        val intercept = dispatch(
            provider,
            method = "observeKeys",
            argsJson = """[{"type":"key","keys":["volume_up"],"actions":["down"],"source":"fake","intercept":true}]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        assertFalse(intercept.toString(), intercept.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, intercept.error?.code)
        assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, intercept.error?.category)

        val unknownSource = dispatch(
            provider,
            method = "observeKeys",
            argsJson = """[{"type":"key","keys":["volume_up"],"actions":["down"],"source":"shell"}]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        assertFalse(unknownSource.toString(), unknownSource.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, unknownSource.error?.code)
        assertFalse(NodeBridgeModules.supports("input_observer", "observeTouch"))

        val scheduledProvider = AndroidNodeBridgeCapabilityProvider(
            context,
            engineInfo = NodeBridgeEngineInfo.fromContext(
                context,
                executionMode = NodeExecutionModePolicy.MODE_SCHEDULED,
                launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            ),
        )
        val scheduledLive = dispatch(
            scheduledProvider,
            method = "observeKeys",
            argsJson = """[{"type":"key","keys":["volume_up"],"actions":["down"],"source":"accessibility"}]""",
            permissions = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
        )
        if (org.autojs.autojs.core.accessibility.AccessibilityService.registry.isOperational) {
            assertTrue(scheduledLive.toString(), scheduledLive.ok)
        } else {
            assertFalse(scheduledLive.toString(), scheduledLive.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, scheduledLive.error?.code)
            assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, scheduledLive.error?.category)
        }
        scheduledProvider.destroy()
        provider.destroy()
    }

    @Test
    fun jsInputObserverUsesFakeTransportAndBlocksShadowing() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node input_observer fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-input-observer-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create input_observer bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/input_observer").apply {
            check(mkdirs()) { "Failed to create input_observer shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, observeKeys: () => "shadow" };""")

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
                  if (request.method === "getAvailableSources") {
                    result = [
                      { id: "fake", type: "key", available: true, live: false },
                      { id: "accessibility", type: "key", available: false, live: true, reason: "not promoted" }
                    ];
                  } else if (request.method === "observeKeys") {
                    result = {
                      id: "input-observer-fake-1",
                      type: "key",
                      source: request.args[0].source,
                      keys: request.args[0].keys,
                      actions: request.args[0].actions,
                      ready: true,
                      closed: false,
                      createdAt: 123,
                      droppedCount: 0
                    };
                  } else if (request.method === "drainEvents") {
                    result = drained ? [] : [{
                      type: "key",
                      source: "fake",
                      key: "volume_up",
                      keyCode: 24,
                      action: "down",
                      eventTime: 456,
                      repeatCount: 0
                    }];
                    drained = true;
                  } else if (request.method === "close") {
                    result = true;
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  })));
                }
              });

              const inputObserver = require("input_observer");
              const sources = await inputObserver.getAvailableSources({ timeoutMs: 1000 });
              const sub = await inputObserver.observeKeys({
                keys: ["volume_up"],
                actions: ["down"],
                source: "fake"
              }, { maxQueueSize: 4, timeoutMs: 1000 });
              const events = await sub.drainEvents({ maxEvents: 16, timeoutMs: 1000 });
              const closed = await sub.close({ timeoutMs: 1000 });
              const closedAgain = await sub.close({ timeoutMs: 1000 });

              let invalid = "";
              try {
                await inputObserver.observeKeys({ keys: [], source: "fake" });
              } catch (error) {
                invalid = String(error && (error.autojs6Code || error.code || ""));
              }

              console.log("input_observer.resolve=" + require.resolve("input_observer"));
              console.log("input_observer.shadow=" + (inputObserver.shadow === true));
              console.log("input_observer.frozen=" + Object.isFrozen(inputObserver));
              console.log("input_observer.sources=" + sources.map((source) => source.id + ":" + source.available + ":" + source.live).join(",") + "|" + Object.isFrozen(sources));
              console.log("input_observer.sub=" + [sub.id, sub.type, sub.source, sub.ready, sub.closed, Object.isFrozen(sub)].join("|"));
              console.log("input_observer.events=" + [events[0].key, events[0].keyCode, events[0].action, Object.isFrozen(events), Object.isFrozen(events[0])].join("|"));
              console.log("input_observer.close=" + [closed, closedAgain, sub.closed].join("|"));
              console.log("input_observer.invalid=" + invalid);
              console.log("input_observer.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("input_observer.permissions=" + requests.map((request) => request.permissions.join("+")).join("|"));
              console.log("input_observer.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-input-observer-fake.js>",
            workingDirectory = workDir,
            timeoutMs = 30_000L,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("input_observer", "input_observer.keys")).toJson()),
        )

        NodePluginTestHarness.assertSucceeded("input_observer fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.resolve=autojs6:input_observer")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.shadow=false")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.frozen=true")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.sources=fake:true:false,accessibility:false:true|true")
        assertTrue(result.stdout, result.stdout.contains("input_observer.sub=input-observer-fake-1|key|fake|true|true|true"))
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.events=volume_up|24|down|true|true")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.close=true|false|true")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.invalid=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.requests=input_observer.getAvailableSources|input_observer.observeKeys|input_observer.drainEvents|input_observer.close")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.permissions=input_observer|input_observer+input_observer.keys|input_observer+input_observer.keys|input_observer+input_observer.keys")
        NodePluginTestHarness.assertStdoutContains("input_observer fake bridge", result, "input_observer.pending=0")
    }

    @Test
    fun jsInputObserverRequiresManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node input_observer manifest capability regression")

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
                await require("input_observer").observeKeys({ keys: ["volume_up"], source: "fake" }, { timeoutMs: 1000 });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("input_observer.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-input-observer-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("input_observer manifest denial", result)
        assertTrue(
            result.stdout,
            result.stdout.contains("input_observer.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|input_observer,input_observer.keys|0|0"),
        )
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        method: String,
        argsJson: String = "[]",
        permissions: List<String> = emptyList(),
    ): NodeBridgeResponse {
        lateinit var response: NodeBridgeResponse
        provider.dispatch(
            NodeBridgeRequest(
                id = "input-observer-$method",
                module = "input_observer",
                method = method,
                argsJson = argsJson,
                timeoutMs = 1000L,
                permissions = permissions,
            ),
        ) { response = it }
        return response
    }
}
