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

class NodeImageBridgeExpansionInstrumentationTest {

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinImageBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("image", "captureScreen"))
        assertTrue(NodeBridgeModules.supports("image", "saveImage"))
        assertTrue(NodeBridgeModules.supports("image", "clip"))
        assertTrue(NodeBridgeModules.supports("image", "resize"))
        assertTrue(NodeBridgeModules.supports("images", "captureScreen"))
        assertTrue(NodeBridgeModules.supports("images", "resize"))
        assertFalse(NodeBridgeModules.supports("image", "loadNativeBitmap"))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            val args = JSONArray(request.argsJson)
            when (request.method) {
                "captureScreen" -> {
                    assertTrue("image" in request.permissions)
                    assertTrue("media_projection" in request.permissions)
                    assertTrue(args.getJSONObject(0).getBoolean("requireExistingPermission"))
                    NodeBridgeResponse.success(request.id, imageJson("capture-1", 1080, 2400).toString())
                }
                "saveImage" -> {
                    assertEquals("capture-1", args.getJSONObject(0).getString("id"))
                    assertEquals("out.png", args.getString(1))
                    NodeBridgeResponse.success(request.id, "null")
                }
                "clip" -> {
                    assertEquals("capture-1", args.getJSONObject(0).getString("id"))
                    assertEquals(10, args.getJSONObject(1).getInt("width"))
                    NodeBridgeResponse.success(request.id, imageJson("clip-1", 10, 20).toString())
                }
                "resize" -> {
                    assertEquals("clip-1", args.getJSONObject(0).getString("id"))
                    assertEquals(5, args.getJSONObject(1).getInt("width"))
                    NodeBridgeResponse.success(request.id, imageJson("resize-1", 5, 6).toString())
                }
                "findImage" -> {
                    assertEquals("resize-1", args.getJSONObject(0).getString("id"))
                    assertEquals("template-1", args.getJSONObject(1).getString("id"))
                    NodeBridgeResponse.success(request.id, JSONObject().put("x", 7).put("y", 8).toString())
                }
                "recycle" -> {
                    assertEquals("template-1", args.getJSONObject(0).getString("id"))
                    NodeBridgeResponse.success(request.id, "true")
                }
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val requests = listOf(
            NodeBridgeRequest("image-capture", "image", "captureScreen", """[{"requireExistingPermission":true}]""", 1000L, listOf("image", "media_projection")),
            NodeBridgeRequest("image-save", "image", "saveImage", """[{"id":"capture-1","width":1080,"height":2400},"out.png",{}]""", 1000L, listOf("image")),
            NodeBridgeRequest("image-clip", "image", "clip", """[{"id":"capture-1","width":1080,"height":2400},{"x":1,"y":2,"width":10,"height":20}]""", 1000L, listOf("image")),
            NodeBridgeRequest("image-resize", "image", "resize", """[{"id":"clip-1","width":10,"height":20},{"width":5,"height":6}]""", 1000L, listOf("image")),
            NodeBridgeRequest("image-find", "image", "findImage", """[{"id":"resize-1","width":5,"height":6},{"id":"template-1","width":5,"height":6},{}]""", 1000L, listOf("image")),
            NodeBridgeRequest("image-recycle", "image", "recycle", """[{"id":"template-1","width":5,"height":6}]""", 1000L, listOf("image")),
        )
        val responses = mutableListOf<NodeBridgeResponse>()
        requests.forEach { request -> provider.dispatch(request) { response -> responses += response } }

        assertEquals(requests.map { it.method }, provider.requests.map { it.method })
        assertTrue(responses.all { it.ok })
        assertEquals("capture-1", JSONObject(responses[0].resultJson.orEmpty()).getString("id"))
        assertEquals("null", responses[1].resultJson)
        assertEquals("clip-1", JSONObject(responses[2].resultJson.orEmpty()).getString("id"))
        assertEquals("resize-1", JSONObject(responses[3].resultJson.orEmpty()).getString("id"))
        assertEquals(7, JSONObject(responses[4].resultJson.orEmpty()).getInt("x"))
        assertEquals("true", responses[5].resultJson)
    }

    @Test
    fun jsImageExpansionUsesFakeTransportAndBlocksShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node image bridge expansion focused regression")

        val workDir = File(NodeEmbeddedTestHarness.targetContext.cacheDir, "node-bridge-image-expansion-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create image bridge expansion working directory: $absolutePath" }
        }
        listOf("image", "images").forEach { moduleName ->
            File(workDir, "node_modules/$moduleName").apply {
                check(mkdirs()) { "Failed to create $moduleName shadow module directory: $absolutePath" }
            }.resolve("index.js").writeText("""module.exports = { shadow: true, captureScreen: () => "shadow" };""")
        }

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
                  if ((request.module === "image" || request.module === "images") && request.method === "captureScreen") {
                    result = { id: request.module + ":capture", width: 1080, height: 2400 };
                  } else if ((request.module === "image" || request.module === "images") && request.method === "readImage") {
                    result = { id: request.module + ":" + request.args[0], width: 10, height: 20 };
                  } else if ((request.module === "image" || request.module === "images") && request.method === "clip") {
                    result = { id: request.args[0].id + ":clip", width: request.args[1].width, height: request.args[1].height };
                  } else if ((request.module === "image" || request.module === "images") && request.method === "resize") {
                    result = { id: request.args[0].id + ":resize", width: request.args[1].width, height: request.args[1].height };
                  } else if ((request.module === "image" || request.module === "images") && request.method === "findImage") {
                    result = { x: 7, y: 8 };
                  } else if ((request.module === "image" || request.module === "images") && (request.method === "saveImage" || request.method === "recycle")) {
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

              const image = require("image");
              const images = require("images");
              const capture = await image.captureScreen({ orientation: "landscape", timeoutMs: 1234 });
              const template = await image.readImage("template.png", { timeoutMs: 1235 });
              const saveResult = await image.saveImage(capture, "out/result.png", { format: "png", timeoutMs: 1236 });
              const clipped = await image.clip(capture, 1.8, 2.2, 30.9, 40.1, { timeoutMs: 1237 });
              const resized = await image.resize(clipped, 15.9, 20.1, { timeoutMs: 1238 });
              const point = await image.findImage(resized, template, { threshold: 0.8, timeoutMs: 1239 });
              const recycleOnce = await image.recycle(template, { timeoutMs: 1240 });
              const recycleTwice = await image.recycle(template, { timeoutMs: 1241 });
              const aliasResize = await images.resize({ id: "alias-source", width: 4, height: 5 }, 2, 3, { timeoutMs: 1242 });

              console.log("image.expand.resolve=" + require.resolve("image") + "/" + require.resolve("images"));
              console.log("image.expand.shadow=" + (image.shadow === true) + "/" + (images.shadow === true));
              console.log("image.expand.frozen=" + Object.isFrozen(image) + "/" + Object.isFrozen(images));
              console.log("image.expand.methods=" + [
                typeof image.captureScreen,
                typeof image.readImage,
                typeof image.saveImage,
                typeof image.clip,
                typeof image.resize,
                typeof image.findImage,
                typeof image.recycle,
                typeof image.loadNativeBitmap
              ].join("|"));
              console.log("image.expand.handles=" + [capture.__autojs6ImageHandle, template.__autojs6ImageHandle, clipped.__autojs6ImageHandle, resized.__autojs6ImageHandle, aliasResize.__autojs6ImageHandle].join("|"));
              console.log("image.expand.sizes=" + [capture.width + "x" + capture.height, clipped.width + "x" + clipped.height, resized.width + "x" + resized.height, aliasResize.width + "x" + aliasResize.height].join("|"));
              console.log("image.expand.point=" + point.x + "," + point.y);
              console.log("image.expand.voids=" + String(saveResult) + "/" + String(recycleOnce) + "/" + String(recycleTwice));
              console.log("image.expand.requests=" + requests.map(r => r.module + ":" + r.method).join("|"));
              console.log("image.expand.capture.args=" + requests[0].args[0].orientation + "/" + requests[0].args[0].requireExistingPermission);
              console.log("image.expand.save.args=" + requests[2].args[0].id + "/" + requests[2].args[1] + "/" + requests[2].args[2].format);
              console.log("image.expand.clip.args=" + requests[3].args[1].x + "," + requests[3].args[1].y + "," + requests[3].args[1].width + "," + requests[3].args[1].height);
              console.log("image.expand.resize.args=" + requests[4].args[1].width + "x" + requests[4].args[1].height);
              console.log("image.expand.recycle.count=" + requests.filter(r => r.method === "recycle").length);
              console.log("image.expand.timeouts=" + requests.map(r => r.timeoutMs).join("|"));
              console.log("image.expand.permissions=" + requests.every(r => r.permissions.indexOf("image") >= 0) + "/" + requests[0].permissions.indexOf("media_projection"));
              console.log("image.expand.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-image-expansion-fake.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("image expansion fake bridge", result)
        assertTrue(result.stdout.contains("image.expand.resolve=image/images"))
        assertTrue(result.stdout.contains("image.expand.shadow=false/false"))
        assertTrue(result.stdout.contains("image.expand.frozen=true/true"))
        assertTrue(result.stdout.contains("image.expand.methods=function|function|function|function|function|function|function|undefined"))
        assertTrue(result.stdout.contains("image.expand.handles=image:capture|image:template.png|image:capture:clip|image:capture:clip:resize|alias-source:resize"))
        assertTrue(result.stdout.contains("image.expand.sizes=1080x2400|30x40|15x20|2x3"))
        assertTrue(result.stdout.contains("image.expand.point=7,8"))
        assertTrue(result.stdout.contains("image.expand.voids=undefined/undefined/undefined"))
        assertTrue(result.stdout.contains("image.expand.requests=image:captureScreen|image:readImage|image:saveImage|image:clip|image:resize|image:findImage|image:recycle|images:resize"))
        assertTrue(result.stdout.contains("image.expand.capture.args=landscape/true"))
        assertTrue(result.stdout.contains("image.expand.save.args=image:capture/out/result.png/png"))
        assertTrue(result.stdout.contains("image.expand.clip.args=1,2,30,40"))
        assertTrue(result.stdout.contains("image.expand.resize.args=15x20"))
        assertTrue(result.stdout.contains("image.expand.recycle.count=1"))
        assertTrue(result.stdout.contains("image.expand.timeouts=1234|1235|1236|1237|1238|1239|1240|1242"))
        assertTrue(result.stdout.contains("image.expand.permissions=true/"))
        assertTrue(result.stdout.contains("image.expand.pending=0"))
    }

    @Test
    fun jsImageExpansionRejectsInvalidPathsAndHandlesBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node image bridge expansion invalid argument regression")

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

              const image = require("image");
              const handle = { id: "handle-1", width: 10, height: 20 };
              const invalid = [];
              for (const item of [
                ["readEscape", () => image.readImage("../secret.png")],
                ["readAbsolute", () => image.readImage("/sdcard/secret.png")],
                ["saveEscape", () => image.saveImage(handle, "../out.png")],
                ["saveAbsolute", () => image.saveImage(handle, "C:\\secret.png")],
                ["clipHandle", () => image.clip({}, 0, 0, 1, 1)],
                ["clipSize", () => image.clip(handle, 0, 0, 0, 1)],
                ["resizeHandle", () => image.resize({}, 1, 1)],
                ["resizeSize", () => image.resize(handle, 1, 0)],
                ["findHandle", () => image.findImage({}, handle)],
                ["recycleHandle", () => image.recycle({})]
              ]) {
                try {
                  await item[1]();
                  invalid.push(item[0] + ":unexpected");
                } catch (error) {
                  invalid.push(item[0] + ":" + (error.autojs6Code || error.code));
                }
              }
              console.log("image.expand.invalid=" + invalid.join("|"));
              console.log("image.expand.invalid.requests=" + requests.length);
            })();
            """.trimIndent(),
            "<embedded-autojs-image-expansion-invalid.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("image expansion invalid arguments", result)
        assertFalse(result.stdout.contains(":unexpected"))
        assertTrue(result.stdout.contains("readEscape:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("readAbsolute:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("saveEscape:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("saveAbsolute:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("clipHandle:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("clipSize:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("resizeHandle:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("resizeSize:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("findHandle:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("recycleHandle:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("image.expand.invalid.requests=0"))
    }

    @Test
    fun androidImageExpansionRejectsUntilProviderIsConnected() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node image bridge expansion Android rejection regression")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const image = require("image");
              const handle = { id: "handle-1", width: 10, height: 20 };
              const denied = [];
              for (const item of [
                ["captureScreen", () => image.captureScreen({ timeoutMs: 1000 })],
                ["saveImage", () => image.saveImage(handle, "out.png", { timeoutMs: 1000 })],
                ["clip", () => image.clip(handle, 0, 0, 1, 1, { timeoutMs: 1000 })],
                ["resize", () => image.resize(handle, 1, 1, { timeoutMs: 1000 })]
              ]) {
                try {
                  await item[1]();
                  denied.push(item[0] + ":unexpected");
                } catch (error) {
                  denied.push(item[0] + ":" + error.code + "/" + error.autojs6Code + "/" + error.module + "/" + error.method);
                }
              }
              console.log("image.expand.android.denied=" + denied.join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-image-expansion-android.js>",
        )

        NodeEmbeddedTestHarness.assertSucceeded("image expansion Android rejection", result)
        assertFalse(result.stdout.contains(":unexpected"))
        assertTrue(result.stdout.contains("image.expand.android.denied=captureScreen:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/image/captureScreen|saveImage:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/image/saveImage|clip:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/image/clip|resize:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/image/resize"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("4", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("4", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
    }

    private fun imageJson(id: String, width: Int, height: Int): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("__autojs6ImageHandle", id)
            .put("width", width)
            .put("height", height)
    }
}
