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

class NodeAccessibilitySelectorBridgeInstrumentationTest {

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinAccessibilitySelectorRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("accessibility", "findOne"))
        assertTrue(NodeBridgeModules.supports("accessibility", "findAll"))
        assertTrue(NodeBridgeModules.supports("accessibility", "click"))
        assertFalse(NodeBridgeModules.supports("accessibility", "pickup"))

        val node = JSONObject()
            .put("text", "OK")
            .put("desc", "Confirm")
            .put("id", "android:id/button1")
            .put("className", "android.widget.Button")
            .put("bounds", JSONObject().put("left", 1).put("top", 2).put("right", 3).put("bottom", 4))
            .put("clickable", true)
            .put("enabled", true)

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("accessibility", request.module)
            val args = JSONArray(request.argsJson)
            val selector = args.getJSONObject(0)
            assertEquals("autojs6.accessibility.selector", selector.getString("type"))
            assertEquals(1, selector.getInt("version"))
            when (request.method) {
                "findOne" -> {
                    assertEquals("OK", selector.getString("text"))
                    NodeBridgeResponse.success(request.id, node.toString())
                }
                "findAll" -> {
                    assertEquals("O", selector.getString("textContains"))
                    NodeBridgeResponse.success(request.id, JSONArray().put(node).put(node).toString())
                }
                "click" -> {
                    assertEquals("Confirm", selector.getString("desc"))
                    NodeBridgeResponse.success(request.id, "true")
                }
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        listOf(
            NodeBridgeRequest(
                "a11y-find-one",
                "accessibility",
                "findOne",
                """[{"type":"autojs6.accessibility.selector","version":1,"text":"OK"},{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
            NodeBridgeRequest(
                "a11y-find-all",
                "accessibility",
                "findAll",
                """[{"type":"autojs6.accessibility.selector","version":1,"textContains":"O"},{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
            NodeBridgeRequest(
                "a11y-click",
                "accessibility",
                "click",
                """[{"type":"autojs6.accessibility.selector","version":1,"desc":"Confirm"},{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
        ).forEach { request ->
            provider.dispatch(request) { response -> responses += response }
        }

        assertEquals(3, provider.requests.size)
        assertEquals(listOf("findOne", "findAll", "click"), provider.requests.map { it.method })
        assertTrue(provider.requests.all { "accessibility" in it.permissions })
        assertTrue(responses.all { it.ok })
        assertEquals("OK", JSONObject(responses[0].resultJson.orEmpty()).getString("text"))
        assertEquals(2, JSONArray(responses[1].resultJson.orEmpty()).length())
        assertEquals("true", responses[2].resultJson)
    }

    @Test
    fun jsAccessibilitySelectorV1UsesFakeTransportAndBlocksShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node accessibility selector bridge focused regression")

        val workDir = File(NodeEmbeddedTestHarness.targetContext.cacheDir, "node-bridge-accessibility-selector-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create accessibility selector working directory: $absolutePath" }
        }
        File(workDir, "node_modules/accessibility").apply {
            check(mkdirs()) { "Failed to create accessibility shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, text: () => ({ shadow: true }) };""")

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
                  if (request.module === "accessibility" && request.method === "findOne") {
                    result = {
                      text: "OK",
                      desc: "Confirm",
                      id: "android:id/button1",
                      className: "android.widget.Button",
                      bounds: { left: 1, top: 2, right: 3, bottom: 4 },
                      clickable: true,
                      enabled: true
                    };
                  } else if (request.module === "accessibility" && request.method === "findAll") {
                    result = [
                      { text: "OK", bounds: { left: 1, top: 2, right: 3, bottom: 4 }, clickable: true, enabled: true },
                      { text: "Other", bounds: { left: 5, top: 6, right: 7, bottom: 8 }, clickable: false, enabled: false },
                      null
                    ];
                  } else if (request.module === "accessibility" && request.method === "click") {
                    result = true;
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

              const accessibility = require("accessibility");
              const resolved = require.resolve("accessibility");
              const exact = accessibility.text("OK");
              const contains = accessibility.textContains("O");
              const byDesc = accessibility.desc("Confirm");
              const one = await accessibility.findOne(exact, { timeoutMs: 1234 });
              const all = await accessibility.findAll(contains, { timeoutMs: 1235 });
              const clicked = await accessibility.click(byDesc, { timeoutMs: 1236 });

              console.log("a11y.selector.resolve=" + resolved);
              console.log("a11y.selector.shadow=" + (accessibility.shadow === true));
              console.log("a11y.selector.frozen=" + Object.isFrozen(accessibility));
              console.log("a11y.selector.builders=" + [
                typeof accessibility.text,
                typeof accessibility.desc,
                typeof accessibility.id,
                typeof accessibility.className,
                typeof accessibility.textContains,
                typeof accessibility.descContains,
                typeof accessibility.pickup
              ].join("|"));
              console.log("a11y.selector.shape=" + JSON.stringify(exact));
              console.log("a11y.selector.selectorFrozen=" + Object.isFrozen(exact));
              console.log("a11y.selector.one=" + one.text + "/" + one.desc + "/" + one.id + "/" + one.bounds.left + "/" + Object.isFrozen(one) + "/" + Object.isFrozen(one.bounds));
              console.log("a11y.selector.all=" + all.length + "/" + all[0].text + "/" + all[1].enabled + "/" + Object.isFrozen(all) + "/" + Object.isFrozen(all[0]));
              console.log("a11y.selector.clicked=" + clicked);
              console.log("a11y.selector.requests=" + requests.map(r => r.method).join("|"));
              console.log("a11y.selector.findOne.args=" + requests[0].args[0].type + "/" + requests[0].args[0].version + "/" + requests[0].args[0].text + "/" + requests[0].args[1].timeoutMs);
              console.log("a11y.selector.findAll.args=" + requests[1].args[0].textContains + "/" + requests[1].args[1].timeoutMs);
              console.log("a11y.selector.click.args=" + requests[2].args[0].desc + "/" + requests[2].args[1].timeoutMs);
              console.log("a11y.selector.timeouts=" + requests.map(r => r.timeoutMs).join("|"));
              console.log("a11y.selector.permissions=" + requests.every(r => r.permissions.indexOf("accessibility") >= 0));
              console.log("a11y.selector.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-accessibility-selector-fake.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("accessibility selector fake bridge", result)
        assertTrue(result.stdout.contains("a11y.selector.resolve=accessibility"))
        assertTrue(result.stdout.contains("a11y.selector.shadow=false"))
        assertTrue(result.stdout.contains("a11y.selector.frozen=true"))
        assertTrue(result.stdout.contains("a11y.selector.builders=function|function|function|function|function|function|undefined"))
        assertTrue(result.stdout.contains("""a11y.selector.shape={"type":"autojs6.accessibility.selector","version":1,"text":"OK"}"""))
        assertTrue(result.stdout.contains("a11y.selector.selectorFrozen=true"))
        assertTrue(result.stdout.contains("a11y.selector.one=OK/Confirm/android:id/button1/1/true/true"))
        assertTrue(result.stdout.contains("a11y.selector.all=2/OK/false/true/true"))
        assertTrue(result.stdout.contains("a11y.selector.clicked=true"))
        assertTrue(result.stdout.contains("a11y.selector.requests=findOne|findAll|click"))
        assertTrue(result.stdout.contains("a11y.selector.findOne.args=autojs6.accessibility.selector/1/OK/1234"))
        assertTrue(result.stdout.contains("a11y.selector.findAll.args=O/1235"))
        assertTrue(result.stdout.contains("a11y.selector.click.args=Confirm/1236"))
        assertTrue(result.stdout.contains("a11y.selector.timeouts=1234|1235|1236"))
        assertTrue(result.stdout.contains("a11y.selector.permissions=true"))
        assertTrue(result.stdout.contains("a11y.selector.pending=0"))
    }

    @Test
    fun jsAccessibilitySelectorV1RejectsInvalidDescriptorsBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node accessibility selector invalid descriptor regression")

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
              const accessibility = require("accessibility");
              const invalid = [];
              try {
                accessibility.text("");
              } catch (error) {
                invalid.push("empty:" + (error.autojs6Code || error.code));
              }
              try {
                accessibility.descContains("x" + String.fromCharCode(0));
              } catch (error) {
                invalid.push("nul:" + (error.autojs6Code || error.code));
              }
              try {
                await accessibility.findOne({ type: "autojs6.accessibility.selector", version: 1 });
              } catch (error) {
                invalid.push("missingField:" + (error.autojs6Code || error.code));
              }
              try {
                await accessibility.findAll({ type: "wrong", version: 1, text: "OK" });
              } catch (error) {
                invalid.push("wrongType:" + (error.autojs6Code || error.code));
              }
              try {
                await accessibility.click({ type: "autojs6.accessibility.selector", version: 1 }, { timeoutMs: 1000 });
              } catch (error) {
                invalid.push("clickMissingField:" + (error.autojs6Code || error.code));
              }
              console.log("a11y.selector.invalid=" + invalid.join("|"));
              console.log("a11y.selector.invalid.requests=" + requests.length);
            })();
            """.trimIndent(),
            "<embedded-autojs-accessibility-selector-invalid.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("accessibility selector invalid descriptors", result)
        assertTrue(result.stdout.contains("a11y.selector.invalid=empty:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|nul:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|missingField:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|wrongType:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|clickMissingField:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("a11y.selector.invalid.requests=0"))
    }

    @Test
    fun androidAccessibilitySelectorV1RejectsUntilProviderIsConnected() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node accessibility selector Android rejection regression")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const accessibility = require("accessibility");
              const selector = accessibility.text("OK");
              const enabled = await accessibility.isEnabled({ timeoutMs: 1000 });
              const ensured = await accessibility.ensureEnabled({ prompt: false, timeoutMs: 1000 });
              const denied = [];
              for (const action of [
                ["findOne", () => accessibility.findOne(selector, { timeoutMs: 1000 })],
                ["findAll", () => accessibility.findAll(selector, { timeoutMs: 1000 })],
                ["click", () => accessibility.click(selector, { timeoutMs: 1000 })]
              ]) {
                try {
                  await action[1]();
                  denied.push(action[0] + ":unexpected");
                } catch (error) {
                  denied.push(action[0] + ":" + error.code + "/" + error.autojs6Code + "/" + error.module + "/" + error.method);
                }
              }
              console.log("a11y.selector.android.enabled=" + enabled);
              console.log("a11y.selector.android.ensured=" + ensured);
              console.log("a11y.selector.android.denied=" + denied.join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-accessibility-selector-android.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("accessibility selector Android rejection", result)
        assertTrue(result.stdout.contains("a11y.selector.android.enabled=false"))
        assertTrue(result.stdout.contains("a11y.selector.android.ensured=false"))
        assertFalse(result.stdout.contains(":unexpected"))
        assertTrue(result.stdout.contains("a11y.selector.android.denied=findOne:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/findOne|findAll:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/findAll|click:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/click"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("5", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("3", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
    }
}
