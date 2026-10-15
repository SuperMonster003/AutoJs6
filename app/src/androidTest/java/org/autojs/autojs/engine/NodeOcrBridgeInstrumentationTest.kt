package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import com.baidu.paddle.lite.ocr.R as PaddleOcrR
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeOcrBridgeInstrumentationTest {

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
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
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node OCR bridge fake transport regression")

        val workDir = File(NodePluginTestHarness.targetContext.cacheDir, "node-bridge-ocr-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create OCR bridge working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true, recognize: () => "shadow" };"""
        File(workDir, "node_modules/ocr").apply {
            check(mkdirs()) { "Failed to create OCR shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText(shadowPackageSource)

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
            moduleSources = mapOf("node_modules/ocr/index.js" to shadowPackageSource),
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("image", "ocr")).toJson()),
        )

        NodePluginTestHarness.assertSucceeded("OCR fake bridge", result)
        // Node runtime 1.6.0+ serves runtime modules through Node's own loader hooks: require.resolve returns the
        // canonical "autojs6:<name>" url (the require.cache key), and a workspace node_modules package only shadows
        // the shims that stand in for npm packages (axios, colors, mime, nanoid, opencc, undici), never a capability
        // facade such as ocr.
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.resolve=autojs6:ocr")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.shadow=false")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.frozen=true")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.methods=function|function|function|undefined")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.result.frozen=true|true|true")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.result.value=hello|0.91|1|4")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.aliases=hello,world|hello|4")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.request=ocr|recognize|image-1|auto|1234")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.requests=recognize|recognizeText|detectTextBounds")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.permissions=ocr|image")
        NodePluginTestHarness.assertStdoutContains("OCR fake bridge", result, "ocr.pending=0")
    }

    @Test
    fun jsOcrRejectsInvalidImageBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node OCR invalid argument regression")

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

        NodePluginTestHarness.assertSucceeded("OCR invalid image handles", result)
        assertFalse(result.stdout.contains(":unexpected"))
        assertTrue(result.stdout.contains("ocr.invalid=null:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|empty:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|string:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|text:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|bounds:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("ocr.invalid.requests=0"))
    }

    @Test
    fun jsOcrRequiresManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node OCR manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("image"))
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

        NodePluginTestHarness.assertSucceeded("OCR manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "ocr.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|ocr|0|0",
            ),
        )
    }

    @Test
    fun kotlinOcrLiveProviderRecognizesMlKitImageHandle() {
        val ocrHealth = NodeBridgeProviderRegistry.androidHealth(NodePluginTestHarness.targetContext)
            .associateBy { it.definition.id }
            .getValue("ocr")
        assumeTrue("OCR provider must be available for live OCR gate", ocrHealth.status == NodeBridgeProviderStatus.AVAILABLE)
        assumeTrue(
            "ML Kit OCR plugin must be enabled for the ML Kit live OCR gate",
            "mlkit-ocr" in OcrPluginHost.availableEngines(NodePluginTestHarness.targetContext),
        )

        val workDir = File(NodePluginTestHarness.targetContext.cacheDir, "node-bridge-ocr-live").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create OCR live working directory: $absolutePath" }
        }
        writeLiveOcrFixture(File(workDir, "ocr-live.png"))
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "permissions": ["image", "ocr"]
              }
            }
            """.trimIndent(),
        )

        val provider = AndroidNodeBridgeCapabilityProvider(
            NodePluginTestHarness.targetContext,
            NodeBridgeEngineInfo.fromContext(
                NodePluginTestHarness.targetContext,
                id = "ocr-live-provider",
                sourceName = "ocr-live-provider.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val read = dispatch(
            provider,
            module = "image",
            method = "readImage",
            argsJson = """["ocr-live.png"]""",
            timeoutMs = 10_000L,
            permissions = listOf("image"),
        )
        assertTrue(read.toString(), read.ok)
        val handle = JSONObject(read.resultJson.orEmpty())
        assertTrue(handle.getString("__autojs6ImageHandle").startsWith("image-"))

        val recognize = dispatch(
            provider,
            module = "ocr",
            method = "recognize",
            argsJson = JSONArray().put(handle).put(JSONObject().put("engine", "mlkit-ocr").put("lang", "zh-CN")).toString(),
            timeoutMs = 30_000L,
            permissions = listOf("ocr", "image"),
        )
        assertTrue(recognize.toString(), recognize.ok)
        val results = JSONArray(recognize.resultJson.orEmpty())
        val recognizedText = (0 until results.length()).joinToString("") { index ->
            results.getJSONObject(index).getString("text")
        }.replace("\\s+".toRegex(), "")
        assertTrue("Expected OCR text to contain 测试, result=${recognize.resultJson}", recognizedText.contains("测试"))
        assertTrue("Expected at least one OCR result with bounds: ${recognize.resultJson}", results.getJSONObject(0).has("bounds"))

        val recognizeText = dispatch(
            provider,
            module = "ocr",
            method = "recognizeText",
            argsJson = JSONArray().put(handle).put(JSONObject().put("engine", "mlkit-ocr").put("lang", "zh-CN")).toString(),
            timeoutMs = 30_000L,
            permissions = listOf("ocr", "image"),
        )
        assertTrue(recognizeText.toString(), recognizeText.ok)
        val textResults = JSONArray(recognizeText.resultJson.orEmpty())
        val joinedText = (0 until textResults.length()).joinToString("") { index ->
            textResults.getJSONObject(index).getString("text")
        }.replace("\\s+".toRegex(), "")
        assertTrue("Expected recognizeText OCR text to contain 测试, result=${recognizeText.resultJson}", joinedText.contains("测试"))

        val bounds = dispatch(
            provider,
            module = "ocr",
            method = "detectTextBounds",
            argsJson = JSONArray()
                .put(handle)
                .put(JSONObject().put("engine", "mlkit-ocr").put("lang", "zh-CN").put("region", JSONArray().put(0).put(0).put(handle.getInt("width")).put(handle.getInt("height"))))
                .toString(),
            timeoutMs = 30_000L,
            permissions = listOf("ocr", "image"),
        )
        assertTrue(bounds.toString(), bounds.ok)
        assertTrue(JSONArray(bounds.resultJson.orEmpty()).length() > 0)

        val recycle = dispatch(
            provider,
            module = "image",
            method = "recycle",
            argsJson = JSONArray().put(handle).toString(),
            timeoutMs = 1_000L,
            permissions = listOf("image"),
        )
        assertTrue(recycle.toString(), recycle.ok)

        val recycled = dispatch(
            provider,
            module = "ocr",
            method = "recognizeText",
            argsJson = JSONArray().put(handle).put(JSONObject()).toString(),
            timeoutMs = 1_000L,
            permissions = listOf("ocr", "image"),
        )
        assertFalse(recycled.toString(), recycled.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, recycled.error?.code)

        val diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.image_active_handle_count"])
        assertEquals("0", diagnostics["embedded_script.ocr_active_operation_count"])
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("3", diagnostics["embedded_script.ocr_live_dispatch_count"])
        assertEquals("0", diagnostics["embedded_script.ocr_live_dispatch_failed_count"])
        assertEquals("0", diagnostics["embedded_script.ocr_service_destroy_cancel_count"])
        provider.destroy(NodeBridgeError.processDead())
    }

    @Test
    fun kotlinOcrLiveProviderSelectsEnabledEngineAutomatically() {
        val context = NodePluginTestHarness.targetContext
        val availableEngines = OcrPluginHost.availableEngines(context)
        assumeTrue("At least one OCR plugin must be enabled for the automatic selection gate", availableEngines.isNotEmpty())

        val ocrHealth = NodeBridgeProviderRegistry.androidHealth(context).associateBy { it.definition.id }.getValue("ocr")
        assertEquals(NodeBridgeProviderStatus.AVAILABLE, ocrHealth.status)
        assertEquals("auto", ocrHealth.details.getValue("defaultEngine"))
        assertEquals(availableEngines.joinToString(","), ocrHealth.details.getValue("availableEngines"))

        val workDir = File(context.cacheDir, "node-bridge-ocr-auto").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create OCR auto-selection working directory: $absolutePath" }
        }
        writeLiveOcrFixture(File(workDir, "ocr-auto.png"))
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "ocr-auto-provider", sourceName = "ocr-auto-provider.cjs", cwd = workDir.absolutePath),
        )
        val read = dispatch(provider, module = "image", method = "readImage", argsJson = """["ocr-auto.png"]""", timeoutMs = 10_000L, permissions = listOf("image"))
        assertTrue(read.toString(), read.ok)
        val handle = JSONObject(read.resultJson.orEmpty())

        fun recognizeWith(options: JSONObject): NodeBridgeResponse = dispatch(
            provider,
            module = "ocr",
            method = "recognizeText",
            argsJson = JSONArray().put(handle).put(options).toString(),
            timeoutMs = 60_000L,
            permissions = listOf("ocr", "image"),
        )

        // No engine: the first enabled plugin in ML Kit / Paddle / Rapid order answers, whichever it is.
        val automatic = recognizeWith(JSONObject())
        assertTrue("automatic selection with $availableEngines: $automatic", automatic.ok)
        assertTrue("automatic selection returned no text: ${automatic.resultJson}", JSONArray(automatic.resultJson.orEmpty()).length() > 0)

        // Explicit engines still filter: enabled ones answer, missing ones report an unavailable provider.
        OcrPluginHost.AUTOMATIC_ENGINE_ORDER.forEach { engine ->
            val explicit = recognizeWith(JSONObject().put("engine", engine))
            if (engine in availableEngines) {
                assertTrue("engine=$engine: $explicit", explicit.ok)
                assertTrue("engine=$engine returned no text: ${explicit.resultJson}", JSONArray(explicit.resultJson.orEmpty()).length() > 0)
            } else {
                assertFalse("engine=$engine should be unavailable: $explicit", explicit.ok)
                assertEquals("engine=$engine: $explicit", NodeBridgeErrorCategory.UNAVAILABLE, explicit.error?.category)
            }
        }

        val recycle = dispatch(provider, module = "image", method = "recycle", argsJson = JSONArray().put(handle).toString(), permissions = listOf("image"))
        assertTrue(recycle.toString(), recycle.ok)
        provider.destroy(NodeBridgeError.processDead())
    }

    @Test
    fun androidOcrRejectsUnknownImageHandleWithoutLeakingResources() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node OCR Android unknown-handle regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("ocr", "image"))
        val result = NodePluginTestHarness.runPluginScript(
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
            "<embedded-autojs-ocr-unknown-handle.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("OCR Android unknown handle rejection", result)
        assertFalse(result.stdout.contains("unexpected"))
        assertTrue(result.stdout.contains("ocr.android.denied=ERR_AUTOJS6_BRIDGE_INVALID_REQUEST/ERR_AUTOJS6_BRIDGE_INVALID_REQUEST/ocr/recognizeText"))
        result.nativeValues["embedded_script.bridge_live_enabled"]?.let { assertEquals("true", it) }
        result.nativeValues["embedded_script.bridge_live_dispatch_count"]?.let { assertEquals("1", it) }
        result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"]?.let { assertEquals("1", it) }
        result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"]?.let { assertEquals("0", it) }
        result.nativeValues["embedded_script.bridge_resource.active_count"]?.let { assertEquals("0", it) }
        result.nativeValues["embedded_script.bridge_live_dispatch_status"]?.let { assertEquals("done", it) }
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        timeoutMs: Long = 1_000L,
        permissions: List<String> = emptyList(),
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "$module-$method-${System.nanoTime()}",
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
        assertTrue("Timed out waiting for $module.$method", latch.await(timeoutMs + 2_000L, TimeUnit.MILLISECONDS))
        return checkNotNull(response) { "Missing response for $module.$method" }
    }

    private fun writeLiveOcrFixture(file: File) {
        val source = BitmapFactory.decodeResource(NodePluginTestHarness.targetContext.resources, PaddleOcrR.drawable.paddle_ocr_test)
            ?: error("Failed to decode OCR fixture resource")
        val scaled = Bitmap.createScaledBitmap(source, source.width * 4, source.height * 4, false)
        file.outputStream().use { out ->
            check(scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Failed to write OCR live fixture" }
        }
        if (scaled !== source) {
            scaled.recycle()
        }
        source.recycle()
    }
}
