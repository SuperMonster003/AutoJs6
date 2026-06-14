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
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinDeviceBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("device", "isScreenOn"))
        assertTrue(NodeBridgeModules.supports("device", "wakeUp"))
        assertTrue(NodeBridgeModules.supports("device", "vibrate"))
        assertFalse(NodeBridgeModules.supports("device", "imei"))
        assertFalse(NodeBridgeModules.supports("device", "androidId"))
        assertFalse(NodeBridgeModules.supports("device", "serial"))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("device", request.module)
            when (request.method) {
                "isScreenOn" -> NodeBridgeResponse.success(request.id, "false")
                "wakeUp", "vibrate" -> NodeBridgeResponse.success(request.id, "null")
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        listOf(
            NodeBridgeRequest("device-screen-1", "device", "isScreenOn", "[]", 1000L, listOf("device")),
            NodeBridgeRequest("device-wake-1", "device", "wakeUp", "[]", 1000L, listOf("device")),
            NodeBridgeRequest("device-vibrate-1", "device", "vibrate", "[42]", 1000L, listOf("device")),
        ).forEach { request ->
            provider.dispatch(request) { response -> responses += response }
        }

        assertEquals(3, provider.requests.size)
        assertEquals(listOf("isScreenOn", "wakeUp", "vibrate"), provider.requests.map { it.method })
        assertEquals("[42]", provider.requests.last().argsJson)
        assertTrue(provider.requests.all { "device" in it.permissions })
        assertTrue(responses.all { it.ok })
        assertEquals("false", responses[0].resultJson)
        assertEquals("null", responses[1].resultJson)
        assertEquals("null", responses[2].resultJson)
    }

    @Test
    fun jsDeviceModuleUsesFakeTransportAndKeepsSensitiveFieldsAbsent() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node device bridge focused regression")

        val workDir = File(NodeEmbeddedTestHarness.targetContext.cacheDir, "node-bridge-device-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create device bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/device").apply {
            check(mkdirs()) { "Failed to create device shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, imei: "shadow" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
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
                typeof device.watch
              ].join("|"));
              console.log("device.values=" + [screenOn, String(wakeResult), String(vibrateResult), invalidCode].join("|"));
              console.log("device.requests=" + requests.map(r => r.method).join("|"));
              console.log("device.vibrate.arg=" + requests.find(r => r.method === "vibrate").args[0]);
              console.log("device.timeouts=" + requests.map(r => r.timeoutMs).join("|"));
              console.log("device.permissions=" + requests.every(r => r.permissions.indexOf("device") >= 0));
              console.log("device.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-device-fake-source.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("device fake bridge", result)
        assertTrue(result.stdout.contains("device.resolve=device"))
        assertTrue(result.stdout.contains("device.shadow=false"))
        assertTrue(result.stdout.contains("device.frozen=true"))
        assertTrue(result.stdout.contains("device.metrics=true"))
        assertTrue(result.stdout.contains("device.sensitive_absent=true"))
        assertTrue(result.stdout.contains("device.methods=function|function|function|undefined"))
        assertTrue(result.stdout.contains("device.values=false|undefined|undefined|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("device.requests=isScreenOn|wakeUp|vibrate"))
        assertTrue(result.stdout.contains("device.vibrate.arg=42"))
        assertTrue(result.stdout.contains("device.timeouts=1234|1235|1236"))
        assertTrue(result.stdout.contains("device.permissions=true"))
        assertTrue(result.stdout.contains("device.pending=0"))
    }
}
