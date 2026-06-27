package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeBridgeConcurrencyLimitsInstrumentationTest {

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
    fun kotlinBridgeLimitPolicyAcceptsSafeOverridesAndRejectsUnsafeOnes() {
        val policy = NodeBridgeLimits.fromJsonSources(
            projectJsonText = """
                {
                  "type": "node",
                  "node": {
                    "bridgeLimits": {
                      "maxPendingBridgeCalls": 2,
                      "maxImageHandles": 1,
                      "maxShellCommands": 99,
                      "accessibilityQueriesPerSecond": 3
                    }
                  }
                }
            """.trimIndent(),
            packageJsonText = """
                {
                  "autojs6": {
                    "node": {
                      "bridgeLimits": {
                        "maxNetworkRequests": 24,
                        "maxWebSocketConnections": 9
                      }
                    }
                  }
                }
            """.trimIndent(),
        )

        assertEquals(2, policy.maxPendingBridgeCalls)
        assertEquals(1, policy.maxImageHandles)
        assertEquals(2, policy.maxShellCommands)
        assertEquals(24, policy.maxNetworkRequests)
        assertEquals(2, policy.maxWebSocketConnections)
        assertEquals(3, policy.accessibilityQueriesPerSecond)
        assertEquals(16, NodeBridgeLimits.defaults.maxNetworkRequests)
        assertEquals(32, NodeBridgeLimits.defaults.toJsonObject().getJSONObject("hardMax").getInt("maxNetworkRequests"))
        assertTrue(policy.sources.contains("project.json:node.bridgeLimits"))
        assertTrue(policy.sources.contains("package.json:autojs6.node.bridgeLimits"))
        assertTrue(policy.warnings.any { it.contains("maxShellCommands") && it.contains("outside") })
        assertTrue(policy.warnings.any { it.contains("maxWebSocketConnections") && it.contains("outside") })
    }

    @Test
    fun androidProviderAppliesAccessibilityRateAndShellResourceLimits() {
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "bridge-limits-provider"),
            bridgeLimits = NodeBridgeLimits.defaults.copy(
                accessibilityQueriesPerSecond = 2,
                maxShellCommands = 0,
            ),
        )

        val first = dispatch(provider, "accessibility", "isEnabled", "[]", listOf("accessibility"))
        val second = dispatch(provider, "accessibility", "isEnabled", "[]", listOf("accessibility"))
        val third = dispatch(provider, "accessibility", "isEnabled", "[]", listOf("accessibility"))
        assertTrue(first.ok)
        assertTrue(second.ok)
        assertFalse(third.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_RATE_LIMITED, third.error?.code)

        val shell = dispatch(
            provider,
            "shell",
            "exec",
            """["echo denied",{"timeoutMs":1000}]""",
            listOf("shell"),
        )
        assertFalse(shell.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, shell.error?.code)

        val diagnostics = provider.nativeDiagnostics()
        assertEquals("1", diagnostics["embedded_script.bridge_rate_limited_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource_limit_rejection_count"])
        assertEquals("0", diagnostics["embedded_script.shell_active_command_count"])
    }

    @Test
    fun jsBridgePendingCallLimitRejectsBeforeDispatchFlood() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge pending limit regression")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const profile = require("autojs6:profile");
              const bridge = require("autojs6:bridge");
              function codeOf(error) {
                return String(error && (error.autojs6Code || error.code || error.name || ""));
              }
              bridge.__test.setTransport({
                postMessage() {
                  // Keep the first request pending until explicit destroy.
                }
              });
              const first = bridge.callAutoJs("toast", "showToast", ["first"], {
                timeoutMs: 2000,
                permissions: ["toast"]
              }).catch((error) => error);
              await Promise.resolve();
              let secondCode = "";
              try {
                await bridge.callAutoJs("toast", "showToast", ["second"], {
                  timeoutMs: 2000,
                  permissions: ["toast"]
                });
              } catch (error) {
                secondCode = codeOf(error);
              }
              const pendingBeforeDestroy = bridge.__test.pendingCount();
              bridge.__test.destroy();
              const firstCode = codeOf(await first);
              console.log("bridge.limit.pending=" + [
                profile.bridgeLimits.maxPendingBridgeCalls,
                secondCode,
                pendingBeforeDestroy,
                bridge.__test.pendingCount(),
                firstCode
              ].join("|"));
            })();
            """.trimIndent(),
            "<node-bridge-pending-limit.js>",
            runtimeModuleSources = mapOf(
                NodeBridgeLimits.RUNTIME_MODULE_NAME to NodeBridgeLimits.defaults.copy(maxPendingBridgeCalls = 1).toJson(),
            ),
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge pending call limit", result)
        assertTrue(result.stdout, result.stdout.contains("bridge.limit.pending=1|${NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT}|1|0|${NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD}"))
    }

    @Test
    fun jsImageHandleLimitAllowsRecycleThenReuse() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge image handle limit regression")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const profile = require("autojs6:profile");
              const bridge = require("autojs6:bridge");
              const requests = [];
              function codeOf(error) {
                return String(error && (error.autojs6Code || error.code || error.name || ""));
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = true;
                  if (request.method === "readImage") {
                    result = { id: "image:" + request.args[0], width: 10, height: 20 };
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  })));
                }
              });

              const image = require("image");
              const first = await image.readImage("one.png");
              let secondCode = "";
              try {
                await image.readImage("two.png");
              } catch (error) {
                secondCode = codeOf(error);
              }
              await image.recycle(first);
              const third = await image.readImage("three.png");
              console.log("bridge.limit.image=" + [
                profile.bridgeLimits.maxImageHandles,
                first.__autojs6ImageHandle,
                secondCode,
                third.__autojs6ImageHandle,
                requests.map(r => r.method + ":" + (r.args[0].id || r.args[0])).join(","),
                bridge.__test.pendingCount()
              ].join("|"));
            })();
            """.trimIndent(),
            "<node-bridge-image-limit.js>",
            runtimeModuleSources = mapOf(
                NodeBridgeLimits.RUNTIME_MODULE_NAME to NodeBridgeLimits.defaults.copy(maxImageHandles = 1).toJson(),
            ),
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge image handle limit", result)
        assertTrue(result.stdout, result.stdout.contains("bridge.limit.image=1|image:one.png|${NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT}|image:three.png|readImage:one.png,recycle:image:one.png,readImage:three.png|0"))
    }

    @Test
    fun projectJsonBridgeLimitOverridesAreInjectedIntoProfile() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge limit project config regression")

        val workDir = File(context.cacheDir, "node-bridge-limit-project").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge limit project directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "bridgeLimits": {
                  "maxPendingBridgeCalls": 2,
                  "maxImageHandles": 1,
                  "maxShellCommands": 99
                }
              }
            }
            """.trimIndent(),
        )

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            const profile = require("autojs6:profile");
            console.log("bridge.limit.project=" + [
              profile.bridgeLimits.maxPendingBridgeCalls,
              profile.bridgeLimits.maxImageHandles,
              profile.bridgeLimits.maxShellCommands,
              profile.bridgeLimits.sources.join(","),
              profile.bridgeLimits.warnings.some((item) => item.indexOf("maxShellCommands") >= 0)
            ].join("|"));
            """.trimIndent(),
            "<node-bridge-limit-project.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge limit project config", result)
        assertTrue(result.stdout, result.stdout.contains("bridge.limit.project=2|1|2|project.json:node.bridgeLimits|true"))
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
        timeoutMs: Long = 5_000L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "bridge-limit-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = timeoutMs,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
    }
}
