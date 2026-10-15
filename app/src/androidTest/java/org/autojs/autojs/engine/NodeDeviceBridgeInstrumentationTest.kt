package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeDeviceBridgeInstrumentationTest {

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun kotlinDeviceBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("device", "isScreenOn"))
        assertTrue(NodeBridgeModules.supports("device", "wakeUp"))
        assertTrue(NodeBridgeModules.supports("device", "vibrate"))
        assertTrue(NodeBridgeModules.supports("device", "isIgnoringBatteryOptimizations"))
        assertTrue(NodeBridgeModules.supports("device", "openBatteryOptimizationSettings"))
        assertFalse(NodeBridgeModules.supports("device", "imei"))
        assertFalse(NodeBridgeModules.supports("device", "androidId"))
        assertFalse(NodeBridgeModules.supports("device", "serial"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("device", "isIgnoringBatteryOptimizations"),
        )
        assertEquals(NodeBridgePermissionManifest.DEVICE_POWER, NodeBridgePermissionManifest.normalizeCapability("power-manager"))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("device", request.module)
            when (request.method) {
                "isScreenOn" -> NodeBridgeResponse.success(request.id, "false")
                "isIgnoringBatteryOptimizations" -> NodeBridgeResponse.success(request.id, "true")
                "openBatteryOptimizationSettings" -> NodeBridgeResponse.success(
                    request.id,
                    """{"schema":"autojs6-node-battery-optimization-settings-v1","sdkInt":35,"ignoringBatteryOptimizations":true,"canOpenSettings":true,"opened":false,"dryRun":true,"action":"android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS","reason":"settings-intent-dry-run"}""",
                )
                "wakeUp", "vibrate" -> NodeBridgeResponse.success(request.id, "null")
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        listOf(
            NodeBridgeRequest("device-screen-1", "device", "isScreenOn", "[]", 1000L, listOf("device")),
            NodeBridgeRequest("device-wake-1", "device", "wakeUp", "[]", 1000L, listOf("device")),
            NodeBridgeRequest("device-vibrate-1", "device", "vibrate", "[42]", 1000L, listOf("device")),
            NodeBridgeRequest("device-power-1", "device", "isIgnoringBatteryOptimizations", "[]", 1000L, listOf("device", "device.power")),
            NodeBridgeRequest("device-power-settings-1", "device", "openBatteryOptimizationSettings", """[{"dryRun":true}]""", 1000L, listOf("device", "device.power")),
        ).forEach { request ->
            provider.dispatch(request) { response -> responses += response }
        }

        assertEquals(5, provider.requests.size)
        assertEquals(
            listOf("isScreenOn", "wakeUp", "vibrate", "isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings"),
            provider.requests.map { it.method },
        )
        assertEquals("[42]", provider.requests[2].argsJson)
        assertTrue(provider.requests.all { "device" in it.permissions })
        assertTrue(provider.requests.takeLast(2).all { "device.power" in it.permissions })
        assertTrue(responses.all { it.ok })
        assertEquals("false", responses[0].resultJson)
        assertEquals("null", responses[1].resultJson)
        assertEquals("null", responses[2].resultJson)
        assertEquals("true", responses[3].resultJson)
        assertTrue(responses[4].resultJson.orEmpty().contains("settings-intent-dry-run"))
    }

    @Test
    fun jsDeviceModuleUsesFakeTransportAndKeepsSensitiveFieldsAbsent() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node device bridge focused regression")

        val workDir = File(NodePluginTestHarness.targetContext.cacheDir, "node-bridge-device-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create device bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/device").apply {
            check(mkdirs()) { "Failed to create device shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, imei: "shadow" };""")

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.module === "device" && request.method === "isScreenOn") {
                    result = false;
                  } else if (request.module === "device" && request.method === "isIgnoringBatteryOptimizations") {
                    result = true;
                  } else if (request.module === "device" && request.method === "openBatteryOptimizationSettings") {
                    result = {
                      schema: "autojs6-node-battery-optimization-settings-v1",
                      sdkInt: 35,
                      ignoringBatteryOptimizations: true,
                      canOpenSettings: true,
                      opened: false,
                      dryRun: request.args[0].dryRun === true,
                      action: "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
                      reason: "settings-intent-dry-run"
                    };
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

              const device = require("device");
              const resolved = require.resolve("device");
              const screenOn = await device.isScreenOn({ timeoutMs: 1234 });
              const wakeResult = await device.wakeUp({ timeoutMs: 1235 });
              const vibrateResult = await device.vibrate(42.8, { timeoutMs: 1236 });
              const ignoringBattery = await device.isIgnoringBatteryOptimizations({ timeoutMs: 1237 });
              const batterySettings = await device.openBatteryOptimizationSettings({ dryRun: true, timeoutMs: 1238 });
              let invalidCode = "";
              try {
                await device.vibrate(-1);
              } catch (error) {
                invalidCode = error && (error.autojs6Code || error.code) || "";
              }
              const metricsOk = Number.isInteger(device.sdkInt) &&
                Number.isInteger(device.width) &&
                Number.isInteger(device.height) &&
                typeof device.density === "number" &&
                device.sdkInt >= 0 &&
                device.width >= 0 &&
                device.height >= 0 &&
                device.density >= 0;
              const sensitiveAbsent = [
                "imei",
                "androidId",
                "serial",
                "phoneNumber",
                "accounts",
                "macAddress",
                "ipAddress",
                "fingerprint",
                "brand",
                "manufacturer"
              ].every(name => typeof device[name] === "undefined");
              console.log("device.resolve=" + resolved);
              console.log("device.shadow=" + (device.shadow === true));
              console.log("device.frozen=" + Object.isFrozen(device));
              console.log("device.metrics=" + metricsOk);
              console.log("device.sensitive_absent=" + sensitiveAbsent);
              console.log("device.methods=" + [
                typeof device.isScreenOn,
                typeof device.wakeUp,
                typeof device.vibrate,
                typeof device.isIgnoringBatteryOptimizations,
                typeof device.openBatteryOptimizationSettings,
                typeof device.watch
              ].join("|"));
              console.log("device.values=" + [screenOn, String(wakeResult), String(vibrateResult), ignoringBattery, batterySettings.schema, batterySettings.dryRun, invalidCode].join("|"));
              console.log("device.requests=" + requests.map(r => r.method).join("|"));
              console.log("device.vibrate.arg=" + requests.find(r => r.method === "vibrate").args[0]);
              console.log("device.timeouts=" + requests.map(r => r.timeoutMs).join("|"));
              console.log("device.permissions=" + requests.every(r => r.permissions.indexOf("device") >= 0));
              console.log("device.power_permissions=" + requests.filter(r => r.method.indexOf("Battery") >= 0).every(r => r.permissions.indexOf("device.power") >= 0));
              console.log("device.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-device-fake-source.js>",
            workingDirectory = workDir,
        )

        NodePluginTestHarness.assertSucceeded("device fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.resolve=autojs6:device")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.shadow=false")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.frozen=true")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.metrics=true")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.sensitive_absent=true")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.methods=function|function|function|function|function|undefined")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.values=false|undefined|undefined|true|autojs6-node-battery-optimization-settings-v1|true|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.requests=isScreenOn|wakeUp|vibrate|isIgnoringBatteryOptimizations|openBatteryOptimizationSettings")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.vibrate.arg=42")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.timeouts=1234|1235|1236|1237|1238")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.permissions=true")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.power_permissions=true")
        NodePluginTestHarness.assertStdoutContains("device fake bridge", result, "device.pending=0")
    }

    @Test
    fun jsDevicePowerRequiresExplicitManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node device power manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("device"))
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
                await require("device").isIgnoringBatteryOptimizations({ timeoutMs: 1000 });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("device.power.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-device-power-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("device power manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "device.power.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|device.power|0|0",
            ),
        )
    }
}
