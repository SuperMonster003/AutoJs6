package org.autojs.autojs.engine

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeClipboardBridgeInstrumentationTest {

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun kotlinClipboardBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("clipboard", "getText"))
        assertTrue(NodeBridgeModules.supports("clipboard", "setText"))
        assertTrue(NodeBridgeModules.supports("clipboard", "hasText"))
        assertFalse(NodeBridgeModules.supports("clipboard", "watch"))

        var storedText = "initial"
        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("clipboard", request.module)
            when (request.method) {
                "getText" -> NodeBridgeResponse.success(request.id, JSONArray().put(storedText).toString().let { it.substring(1, it.length - 1) })
                "setText" -> {
                    storedText = JSONArray(request.argsJson).optString(0, "")
                    NodeBridgeResponse.success(request.id, "null")
                }
                "hasText" -> NodeBridgeResponse.success(request.id, storedText.isNotEmpty().toString())
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        listOf(
            NodeBridgeRequest("clip-get-1", "clipboard", "getText", "[]", 1000L, listOf("clipboard")),
            NodeBridgeRequest("clip-has-1", "clipboard", "hasText", "[]", 1000L, listOf("clipboard")),
            NodeBridgeRequest("clip-set-1", "clipboard", "setText", """["updated"]""", 1000L, listOf("clipboard")),
            NodeBridgeRequest("clip-get-2", "clipboard", "getText", "[]", 1000L, listOf("clipboard")),
        ).forEach { request ->
            provider.dispatch(request) { response -> responses += response }
        }

        assertEquals(4, provider.requests.size)
        assertEquals(listOf("getText", "hasText", "setText", "getText"), provider.requests.map { it.method })
        assertTrue(responses.all { it.ok })
        assertEquals("initial", JSONArray("[${responses[0].resultJson}]").getString(0))
        assertEquals("true", responses[1].resultJson)
        assertEquals("null", responses[2].resultJson)
        assertEquals("updated", JSONArray("[${responses[3].resultJson}]").getString(0))
    }

    @Test
    fun jsClipboardModuleUsesFakeTransportAndBlocksShadowing() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node clipboard bridge focused regression")

        val workDir = File(NodePluginTestHarness.targetContext.cacheDir, "node-bridge-clipboard-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create clipboard bridge working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true, getText: () => "shadow" };"""
        File(workDir, "node_modules/clipboard").apply {
            check(mkdirs()) { "Failed to create clipboard shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText(shadowPackageSource)

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              let storedText = "initial";
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.module === "clipboard" && request.method === "getText") {
                    result = storedText;
                  } else if (request.module === "clipboard" && request.method === "setText") {
                    storedText = String(request.args[0]);
                  } else if (request.module === "clipboard" && request.method === "hasText") {
                    result = storedText.length > 0;
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
              const clipboard = require("clipboard");
              const resolved = require.resolve("clipboard");
              const beforeText = await clipboard.getText({ timeoutMs: 1234 });
              const beforeHas = await clipboard.hasText({ timeoutMs: 1235 });
              const setResult = await clipboard.setText("updated", { timeoutMs: 1236 });
              const afterText = await clipboard.getText({ timeoutMs: 1237 });
              const afterHas = await clipboard.hasText({ timeoutMs: 1238 });
              console.log("clipboard.resolve=" + resolved);
              console.log("clipboard.shadow=" + (clipboard.shadow === true));
              console.log("clipboard.frozen=" + Object.isFrozen(clipboard));
              console.log("clipboard.methods=" + [typeof clipboard.getText, typeof clipboard.setText, typeof clipboard.hasText, typeof clipboard.watch].join("|"));
              console.log("clipboard.values=" + [beforeText, beforeHas, String(setResult), afterText, afterHas].join("|"));
              console.log("clipboard.requests=" + requests.map(r => r.method).join("|"));
              console.log("clipboard.set.args=" + requests.find(r => r.method === "setText").args[0]);
              console.log("clipboard.timeouts=" + requests.map(r => r.timeoutMs).join("|"));
              console.log("clipboard.permissions=" + requests.every(r => r.permissions.indexOf("clipboard") >= 0));
              console.log("clipboard.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-clipboard-fake-source.js>",
            workingDirectory = workDir,
            moduleSources = mapOf("node_modules/clipboard/index.js" to shadowPackageSource),
            // The Node runtime plugin ships every bare script with an enforced bridge-permission manifest that declares
            // only "network" (runtime:stable-network), so the facade rejects any other capability with
            // ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED before the request reaches the (fake) transport; declare what
            // the script uses, exactly as a project.json would.
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("clipboard")).toJson()),
        )

        NodePluginTestHarness.assertSucceeded("clipboard fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.resolve=autojs6:clipboard")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.shadow=false")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.frozen=true")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.methods=function|function|function|undefined")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.values=initial|true|undefined|updated|true")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.requests=getText|hasText|setText|getText|hasText")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.set.args=updated")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.timeouts=1234|1235|1236|1237|1238")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.permissions=true")
        NodePluginTestHarness.assertStdoutContains("clipboard fake bridge", result, "clipboard.pending=0")
    }
}
