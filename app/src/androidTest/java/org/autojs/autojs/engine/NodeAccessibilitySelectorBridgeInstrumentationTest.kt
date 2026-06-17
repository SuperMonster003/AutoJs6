package org.autojs.autojs.engine

import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
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
        assertTrue(NodeBridgeModules.supports("accessibility", "longClick"))
        assertTrue(NodeBridgeModules.supports("accessibility", "setText"))
        assertTrue(NodeBridgeModules.supports("accessibility", "scrollForward"))
        assertTrue(NodeBridgeModules.supports("accessibility", "scrollBackward"))
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
                    assertEquals("OK", selector.getJSONObject("textMatches").getString("pattern"))
                    assertEquals("i", selector.getJSONObject("textMatches").getString("flags"))
                    assertTrue(selector.getBoolean("clickable"))
                    assertEquals(2, selector.getInt("depth"))
                    assertEquals(100, selector.getJSONObject("boundsInside").getInt("right"))
                    NodeBridgeResponse.success(request.id, node.toString())
                }
                "findAll" -> {
                    assertEquals("Conf.*", selector.getJSONObject("descMatches").getString("pattern"))
                    assertFalse(selector.getBoolean("enabled"))
                    assertEquals(1, selector.getJSONObject("boundsContains").getInt("left"))
                    NodeBridgeResponse.success(request.id, JSONArray().put(node).put(node).toString())
                }
                "click" -> {
                    assertEquals("Confirm", selector.getString("desc"))
                    NodeBridgeResponse.success(request.id, "true")
                }
                "longClick" -> {
                    assertEquals(".*Button", selector.getJSONObject("classNameMatches").getString("pattern"))
                    NodeBridgeResponse.success(request.id, "true")
                }
                "setText" -> {
                    assertEquals(".*:id/input", selector.getJSONObject("idMatches").getString("pattern"))
                    assertEquals("Typed", args.getString(1))
                    NodeBridgeResponse.success(request.id, "true")
                }
                "scrollForward" -> {
                    assertTrue(selector.getBoolean("clickable"))
                    NodeBridgeResponse.success(request.id, "true")
                }
                "scrollBackward" -> {
                    assertFalse(selector.getBoolean("enabled"))
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
                """[{"type":"autojs6.accessibility.selector","version":1,"textMatches":{"pattern":"OK","flags":"i"},"clickable":true,"depth":2,"boundsInside":{"left":0,"top":0,"right":100,"bottom":100}},{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
            NodeBridgeRequest(
                "a11y-find-all",
                "accessibility",
                "findAll",
                """[{"type":"autojs6.accessibility.selector","version":1,"descMatches":{"pattern":"Conf.*","flags":"i"},"enabled":false,"boundsContains":{"left":1,"top":2,"right":3,"bottom":4}},{"timeoutMs":1000}]""",
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
            NodeBridgeRequest(
                "a11y-long-click",
                "accessibility",
                "longClick",
                """[{"type":"autojs6.accessibility.selector","version":1,"classNameMatches":{"pattern":".*Button","flags":""}},{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
            NodeBridgeRequest(
                "a11y-set-text",
                "accessibility",
                "setText",
                """[{"type":"autojs6.accessibility.selector","version":1,"idMatches":{"pattern":".*:id/input","flags":""}},"Typed",{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
            NodeBridgeRequest(
                "a11y-scroll-forward",
                "accessibility",
                "scrollForward",
                """[{"type":"autojs6.accessibility.selector","version":1,"clickable":true},{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
            NodeBridgeRequest(
                "a11y-scroll-backward",
                "accessibility",
                "scrollBackward",
                """[{"type":"autojs6.accessibility.selector","version":1,"enabled":false},{"timeoutMs":1000}]""",
                1000L,
                listOf("accessibility"),
            ),
        ).forEach { request ->
            provider.dispatch(request) { response -> responses += response }
        }

        assertEquals(7, provider.requests.size)
        assertEquals(
            listOf("findOne", "findAll", "click", "longClick", "setText", "scrollForward", "scrollBackward"),
            provider.requests.map { it.method },
        )
        assertTrue(provider.requests.all { "accessibility" in it.permissions })
        assertTrue(responses.all { it.ok })
        assertEquals("OK", JSONObject(responses[0].resultJson.orEmpty()).getString("text"))
        assertEquals(2, JSONArray(responses[1].resultJson.orEmpty()).length())
        assertEquals("true", responses[2].resultJson)
        assertEquals("true", responses[3].resultJson)
        assertEquals("true", responses[4].resultJson)
        assertEquals("true", responses[5].resultJson)
        assertEquals("true", responses[6].resultJson)
    }

    @Test
    fun jsAccessibilitySelectorV2UsesFakeTransportAndBlocksShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node accessibility selector bridge focused regression")

        val workDir = File(NodeEmbeddedTestHarness.targetContext.cacheDir, "node-bridge-accessibility-selector-v2-shadow").apply {
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
              const assert = require("assert");
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.module === "accessibility" && request.method === "findOne") {
                    const selector = request.args[0];
                    assert.strictEqual(selector.textMatches.pattern, "OK");
                    assert.strictEqual(selector.textMatches.flags, "i");
                    assert.strictEqual(selector.clickable, true);
                    assert.strictEqual(selector.depth, 2);
                    assert.strictEqual(selector.boundsInside.right, 100);
                    result = {
                      text: "OK",
                      desc: "Confirm",
                      id: "android:id/button1",
                      className: "android.widget.Button",
                      bounds: { left: 1, top: 2, right: 3, bottom: 4 },
                      clickable: true,
                      enabled: true,
                      raw: { leaked: true },
                      nodeInfo: "raw-node",
                      children: [{ text: "child" }]
                    };
                  } else if (request.module === "accessibility" && request.method === "findAll") {
                    const selector = request.args[0];
                    assert.strictEqual(selector.descMatches.pattern, "Conf.*");
                    assert.strictEqual(selector.descMatches.flags, "i");
                    assert.strictEqual(selector.enabled, false);
                    assert.strictEqual(selector.boundsContains.left, 1);
                    result = [
                      { text: "OK", bounds: { left: 1, top: 2, right: 3, bottom: 4 }, clickable: true, enabled: true },
                      { text: "Other", bounds: { left: 5, top: 6, right: 7, bottom: 8 }, clickable: false, enabled: false },
                      null
                    ];
                  } else if (request.module === "accessibility" && request.method === "click") {
                    result = true;
                  } else if (request.module === "accessibility" && request.method === "longClick") {
                    assert.strictEqual(request.args[0].classNameMatches.pattern, ".*Button$");
                    result = true;
                  } else if (request.module === "accessibility" && request.method === "setText") {
                    assert.strictEqual(request.args[0].idMatches.pattern, ".*:id/input");
                    assert.strictEqual(request.args[1], "Typed");
                    result = true;
                  } else if (request.module === "accessibility" && request.method === "scrollForward") {
                    assert.strictEqual(request.args[0].clickable, true);
                    result = true;
                  } else if (request.module === "accessibility" && request.method === "scrollBackward") {
                    assert.strictEqual(request.args[0].enabled, false);
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
              const advanced = Object.freeze(Object.assign(
                {},
                accessibility.textMatches(/OK/i),
                accessibility.clickable(true),
                accessibility.depth(2),
                accessibility.boundsInside(0, 0, 100, 100)
              ));
              const descAdvanced = Object.freeze(Object.assign(
                {},
                accessibility.descMatches({ pattern: "Conf.*", flags: "i" }),
                accessibility.enabled(false),
                accessibility.boundsContains({ left: 1, top: 2, right: 3, bottom: 4 })
              ));
              const classRegex = accessibility.classNameMatches(/.*Button$/);
              const idRegex = accessibility.idMatches(".*:id/input");
              const one = await accessibility.findOne(advanced, { timeoutMs: 1234 });
              const all = await accessibility.findAll(descAdvanced, { timeoutMs: 1235 });
              const clicked = await accessibility.click(byDesc, { timeoutMs: 1236 });
              const longClicked = await accessibility.longClick(classRegex, { timeoutMs: 1237 });
              const textSet = await accessibility.setText(idRegex, "Typed", { timeoutMs: 1238 });
              const scrolledForward = await accessibility.scrollForward(accessibility.clickable(), { timeoutMs: 1239 });
              const scrolledBackward = await accessibility.scrollBackward(accessibility.enabled(false), { timeoutMs: 1240 });

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
                typeof accessibility.textMatches,
                typeof accessibility.descMatches,
                typeof accessibility.idMatches,
                typeof accessibility.classNameMatches,
                typeof accessibility.clickable,
                typeof accessibility.enabled,
                typeof accessibility.depth,
                typeof accessibility.boundsInside,
                typeof accessibility.boundsContains,
                typeof accessibility.pickup
              ].join("|"));
              console.log("a11y.selector.shape=" + JSON.stringify(exact));
              console.log("a11y.selector.selectorFrozen=" + Object.isFrozen(exact));
              console.log("a11y.selector.v2=" + [
                advanced.textMatches.pattern,
                advanced.textMatches.flags,
                advanced.clickable,
                advanced.depth,
                advanced.boundsInside.right,
                Object.isFrozen(advanced.textMatches),
                Object.isFrozen(advanced.boundsInside)
              ].join("|"));
              console.log("a11y.selector.one=" + one.text + "/" + one.desc + "/" + one.id + "/" + one.bounds.left + "/" + Object.isFrozen(one) + "/" + Object.isFrozen(one.bounds));
              console.log("a11y.selector.rawLeak=" + [one.raw === undefined, one.nodeInfo === undefined, one.children === undefined, Object.keys(one).indexOf("raw")].join("|"));
              console.log("a11y.selector.all=" + all.length + "/" + all[0].text + "/" + all[1].enabled + "/" + Object.isFrozen(all) + "/" + Object.isFrozen(all[0]));
              console.log("a11y.selector.clicked=" + clicked);
              console.log("a11y.selector.actions=" + [longClicked, textSet, scrolledForward, scrolledBackward].join("|"));
              console.log("a11y.selector.requests=" + requests.map(r => r.method).join("|"));
              console.log("a11y.selector.findOne.args=" + requests[0].args[0].type + "/" + requests[0].args[0].version + "/" + requests[0].args[0].textMatches.pattern + "/" + requests[0].args[0].textMatches.flags + "/" + requests[0].args[0].clickable + "/" + requests[0].args[0].depth + "/" + requests[0].args[0].boundsInside.right + "/" + requests[0].args[1].timeoutMs);
              console.log("a11y.selector.findAll.args=" + requests[1].args[0].descMatches.pattern + "/" + requests[1].args[0].enabled + "/" + requests[1].args[0].boundsContains.left + "/" + requests[1].args[1].timeoutMs);
              console.log("a11y.selector.click.args=" + requests[2].args[0].desc + "/" + requests[2].args[1].timeoutMs);
              console.log("a11y.selector.setText.args=" + requests[4].args[0].idMatches.pattern + "/" + requests[4].args[1] + "/" + requests[4].args[2].timeoutMs);
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
        assertTrue(result.stdout.contains("a11y.selector.builders=function|function|function|function|function|function|function|function|function|function|function|function|function|function|function|undefined"))
        assertTrue(result.stdout.contains("""a11y.selector.shape={"type":"autojs6.accessibility.selector","version":1,"text":"OK"}"""))
        assertTrue(result.stdout.contains("a11y.selector.selectorFrozen=true"))
        assertTrue(result.stdout.contains("a11y.selector.v2=OK|i|true|2|100|true|true"))
        assertTrue(result.stdout.contains("a11y.selector.one=OK/Confirm/android:id/button1/1/true/true"))
        assertTrue(result.stdout.contains("a11y.selector.rawLeak=true|true|true|-1"))
        assertTrue(result.stdout.contains("a11y.selector.all=2/OK/false/true/true"))
        assertTrue(result.stdout.contains("a11y.selector.clicked=true"))
        assertTrue(result.stdout.contains("a11y.selector.actions=true|true|true|true"))
        assertTrue(result.stdout.contains("a11y.selector.requests=findOne|findAll|click|longClick|setText|scrollForward|scrollBackward"))
        assertTrue(result.stdout.contains("a11y.selector.findOne.args=autojs6.accessibility.selector/1/OK/i/true/2/100/1234"))
        assertTrue(result.stdout.contains("a11y.selector.findAll.args=Conf.*/false/1/1235"))
        assertTrue(result.stdout.contains("a11y.selector.click.args=Confirm/1236"))
        assertTrue(result.stdout.contains("a11y.selector.setText.args=.*:id/input/Typed/1238"))
        assertTrue(result.stdout.contains("a11y.selector.timeouts=1234|1235|1236|1237|1238|1239|1240"))
        assertTrue(result.stdout.contains("a11y.selector.permissions=true"))
        assertTrue(result.stdout.contains("a11y.selector.pending=0"))
    }

    @Test
    fun jsAccessibilitySelectorV2RejectsInvalidDescriptorsBeforeDispatch() {
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
                accessibility.textMatches("");
              } catch (error) {
                invalid.push("emptyRegex:" + (error.autojs6Code || error.code));
              }
              try {
                accessibility.depth(-1);
              } catch (error) {
                invalid.push("badDepth:" + (error.autojs6Code || error.code));
              }
              try {
                accessibility.boundsInside(10, 0, 0, 20);
              } catch (error) {
                invalid.push("badBounds:" + (error.autojs6Code || error.code));
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
              try {
                await accessibility.setText(accessibility.text("OK"), "bad" + String.fromCharCode(0), { timeoutMs: 1000 });
              } catch (error) {
                invalid.push("setTextNul:" + (error.autojs6Code || error.code));
              }
              console.log("a11y.selector.invalid=" + invalid.join("|"));
              console.log("a11y.selector.invalid.requests=" + requests.length);
            })();
            """.trimIndent(),
            "<embedded-autojs-accessibility-selector-invalid.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("accessibility selector invalid descriptors", result)
        assertTrue(result.stdout.contains("a11y.selector.invalid=empty:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|nul:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|emptyRegex:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|badDepth:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|badBounds:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|missingField:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|wrongType:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|clickMissingField:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|setTextNul:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("a11y.selector.invalid.requests=0"))
    }

    @Test
    fun jsAccessibilitySelectorV2HonorsTimeoutAndCancellation() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node accessibility selector timeout/cancellation regression")

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
              let timeoutCode = "";
              try {
                await accessibility.findOne(accessibility.text("Never"), { timeoutMs: 25 });
              } catch (error) {
                timeoutCode = String(error && (error.autojs6Code || error.code || ""));
              }

              const controller = new AbortController();
              controller.abort();
              let cancelledCode = "";
              try {
                await accessibility.findAll(accessibility.text("Abort"), { timeoutMs: 1000, signal: controller.signal });
              } catch (error) {
                cancelledCode = String(error && (error.autojs6Code || error.code || ""));
              }

              const lateAccepted = requests[0] ? bridge.__test.receiveMessage(JSON.stringify({
                id: requests[0].id,
                ok: true,
                result: null
              })) : false;

              console.log("a11y.selector.timeout_cancel=" + [timeoutCode, cancelledCode, requests.length, bridge.__test.pendingCount(), lateAccepted].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-accessibility-selector-timeout-cancel.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("accessibility selector timeout/cancellation", result)
        assertTrue(
            result.stdout.contains(
                "a11y.selector.timeout_cancel=${NodeRuntimeErrorCodes.BRIDGE_TIMEOUT}|${NodeRuntimeErrorCodes.BRIDGE_CANCELLED}|1|0|false",
            ),
        )
    }

    @Test
    fun androidAccessibilitySelectorV2RejectsUntilProviderIsConnected() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node accessibility selector Android rejection regression")
        assumeTrue(
            "This rejection-path regression applies when the accessibility provider is not connected in the current process.",
            !AutoJsAccessibilityService.hasInstance() || !AutoJsAccessibilityService.hasOperationalState,
        )

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
                ["click", () => accessibility.click(selector, { timeoutMs: 1000 })],
                ["longClick", () => accessibility.longClick(selector, { timeoutMs: 1000 })],
                ["setText", () => accessibility.setText(selector, "Typed", { timeoutMs: 1000 })],
                ["scrollForward", () => accessibility.scrollForward(selector, { timeoutMs: 1000 })],
                ["scrollBackward", () => accessibility.scrollBackward(selector, { timeoutMs: 1000 })]
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
        assertTrue(result.stdout.contains("a11y.selector.android.denied=findOne:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/findOne|findAll:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/findAll|click:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/click|longClick:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/longClick|setText:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/setText|scrollForward:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/scrollForward|scrollBackward:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/scrollBackward"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("9", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("7", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
    }
}
