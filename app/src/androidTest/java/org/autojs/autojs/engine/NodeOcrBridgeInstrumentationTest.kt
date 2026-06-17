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

class NodeOcrBridgeInstrumentationTest {

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinOcrBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("ocr", "recognize"))
        assertTrue(NodeBridgeModules.supports("ocr", "recognizeText"))
        assertTrue(NodeBridgeModules.supports("ocr", "detectTextBounds"))
        assertFalse(NodeBridgeModules.supports("ocr", "detectRaw"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ocr", "recognize"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ocr", "recognizeText"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ocr", "detectTextBounds"),
        )

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("ocr", request.module)
            assertEquals("recognize", request.method)
            assertTrue("ocr" in request.permissions)
            assertTrue("image" in request.permissions)
            val args = JSONArray(request.argsJson)
            assertEquals("image-1", args.getJSONObject(0).getString("id"))
            assertEquals(10, args.getJSONObject(0).getInt("width"))
            assertEquals(20, args.getJSONObject(0).getInt("height"))
            assertEquals("auto", args.getJSONObject(1).getString("lang"))
            NodeBridgeResponse.success(
                request.id,
                JSONArray()
                    .put(
                        JSONObject()
                            .put("text", "hello")
                            .put("confidence", 0.91)
                            .put("bounds", JSONObject().put("left", 1).put("top", 2).put("right", 3).put("bottom", 4)),
                    )
                    .toString(),
            )
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "ocr-recognize",
                module = "ocr",
                method = "recognize",
                argsJson = """[{"id":"image-1","__autojs6ImageHandle":"image-1","width":10,"height":20},{"lang":"auto"}]""",
                timeoutMs = 30000L,
                permissions = listOf("ocr", "image"),
            ),
        ) { response -> responses += response }

        assertEquals(listOf("recognize"), provider.requests.map { it.method })
        assertEquals(1, responses.size)
        assertTrue(responses.single().ok)
        val result = JSONArray(responses.single().resultJson.orEmpty()).getJSONObject(0)
        assertEquals("hello", result.getString("text"))
        assertEquals(0.91, result.getDouble("confidence"), 0.0)
        assertEquals(4, result.getJSONObject("bounds").getInt("bottom"))
    }

    @Test
    fun jsOcrBridgeUsesFakeTransportAndBlocksShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node OCR bridge fake transport regression")

        val workDir = File(NodeEmbeddedTestHarness.targetContext.cacheDir, "node-bridge-ocr-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create OCR bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/ocr").apply {
            check(mkdirs()) { "Failed to create OCR shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, recognize: () => "shadow" };""")

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
                  setImmediate(() => {
                    const result = request.method === "recognizeText"
                      ? [
                          {
                            text: "hello",
                            confidence: 0.91,
                            bounds: { left: 1, top: 2, right: 3, bottom: 4 }
                          },
                          {
                            text: "world",
                            confidence: 0.92,
                            bounds: { left: 5, top: 6, right: 7, bottom: 8 }
                          }
                        ]
                      : [
                          {
                            text: "hello",
                            confidence: 0.91,
                            bounds: { left: 1, top: 2, right: 3, bottom: 4 }
                          }
                        ];
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result
                    }));
                  });
                }
              });

              const ocr = require("ocr");
              const handle = { id: "image-1", __autojs6ImageHandle: "image-1", width: 10, height: 20 };
              const results = await ocr.recognize(handle, {
                lang: "auto",
                region: { x: 1, y: 2, width: 3, height: 4 },
                timeoutMs: 1234
              });
              const text = await ocr.recognizeText(handle, { timeoutMs: 1235 });
              const bounds = await ocr.detectTextBounds(handle, { timeoutMs: 1236 });

              console.log("ocr.resolve=" + require.resolve("ocr"));
              console.log("ocr.shadow=" + (ocr.shadow === true));
              console.log("ocr.frozen=" + Object.isFrozen(ocr));
              console.log("ocr.methods=" + [typeof ocr.recognize, typeof ocr.recognizeText, typeof ocr.detectTextBounds, typeof ocr.detectRaw].join("|"));
              console.log("ocr.result.frozen=" + [Object.isFrozen(results), Object.isFrozen(results[0]), Object.isFrozen(results[0].bounds)].join("|"));
              console.log("ocr.result.value=" + [results[0].text, results[0].confidence, results[0].bounds.left, results[0].bounds.bottom].join("|"));
              console.log("ocr.aliases=" + [text.replace("\n", ","), bounds[0].text, bounds[0].bounds.bottom].join("|"));
              console.log("ocr.request=" + [requests[0].module, requests[0].method, requests[0].args[0].id, requests[0].args[1].lang, requests[0].timeoutMs].join("|"));
              console.log("ocr.requests=" + requests.map((request) => request.method).join("|"));
              console.log("ocr.permissions=" + requests[0].permissions.join("|"));
              console.log("ocr.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-ocr-fake.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("OCR fake bridge", result)
        assertTrue(result.stdout.contains("ocr.resolve=ocr"))
        assertTrue(result.stdout.contains("ocr.shadow=false"))
        assertTrue(result.stdout.contains("ocr.frozen=true"))
        assertTrue(result.stdout.contains("ocr.methods=function|function|function|undefined"))
        assertTrue(result.stdout.contains("ocr.result.frozen=true|true|true"))
        assertTrue(result.stdout.contains("ocr.result.value=hello|0.91|1|4"))
        assertTrue(result.stdout.contains("ocr.aliases=hello,world|hello|4"))
        assertTrue(result.stdout.contains("ocr.request=ocr|recognize|image-1|auto|1234"))
        assertTrue(result.stdout.contains("ocr.requests=recognize|recognizeText|detectTextBounds"))
        assertTrue(result.stdout.contains("ocr.permissions=ocr|image"))
        assertTrue(result.stdout.contains("ocr.pending=0"))
    }

    @Test
    fun jsOcrRejectsInvalidImageBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node OCR invalid argument regression")

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

              const ocr = require("ocr");
              const invalid = [];
              for (const item of [
                ["null", () => ocr.recognize(null)],
                ["empty", () => ocr.recognize({})],
                ["string", () => ocr.recognize("image-1")],
                ["text", () => ocr.recognizeText({})],
                ["bounds", () => ocr.detectTextBounds("image-1")]
              ]) {
                try {
                  await item[1]();
                  invalid.push(item[0] + ":unexpected");
                } catch (error) {
                  invalid.push(item[0] + ":" + (error.autojs6Code || error.code));
                }
              }
              console.log("ocr.invalid=" + invalid.join("|"));
              console.log("ocr.invalid.requests=" + requests.length);
            })();
            """.trimIndent(),
            "<embedded-autojs-ocr-invalid.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("OCR invalid image handles", result)
        assertFalse(result.stdout.contains(":unexpected"))
        assertTrue(result.stdout.contains("ocr.invalid=null:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|empty:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|string:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|text:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|bounds:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("ocr.invalid.requests=0"))
    }

    @Test
    fun jsOcrRequiresManifestCapabilityBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node OCR manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("image"))
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
                await require("ocr").detectTextBounds({ id: "image-1", width: 10, height: 20 }, { timeoutMs: 1000 });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("ocr.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-ocr-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("OCR manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "ocr.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|ocr|0|0",
            ),
        )
    }

    @Test
    fun androidOcrRejectsUntilProviderIsConnected() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node OCR Android rejection regression")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const ocr = require("ocr");
              let denied = "";
              try {
                await ocr.recognizeText({ id: "image-1", width: 10, height: 20 }, { timeoutMs: 1000 });
                denied = "unexpected";
              } catch (error) {
                denied = [error.code, error.autojs6Code, error.module, error.method].join("/");
              }
              console.log("ocr.android.denied=" + denied);
            })();
            """.trimIndent(),
            "<embedded-autojs-ocr-android.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("OCR Android rejection", result)
        assertFalse(result.stdout.contains("unexpected"))
        assertTrue(result.stdout.contains("ocr.android.denied=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ocr/recognizeText"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
    }
}
