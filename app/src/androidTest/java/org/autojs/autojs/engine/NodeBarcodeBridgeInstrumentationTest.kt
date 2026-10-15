package org.autojs.autojs.engine

import android.graphics.Bitmap
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeBarcodeBridgeInstrumentationTest {

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun kotlinBarcodeBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("barcode", "detect"))
        assertTrue(NodeBridgeModules.supports("barcode", "detectAll"))
        assertTrue(NodeBridgeModules.supports("barcode", "recognizeText"))
        assertTrue(NodeBridgeModules.supports("barcode", "recognizeTexts"))
        assertFalse(NodeBridgeModules.supports("barcode", "rawScanner"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.BARCODE, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("barcode", "detect"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.BARCODE, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("barcode", "recognizeTexts"),
        )

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("barcode", request.module)
            assertEquals("detectAll", request.method)
            assertTrue("barcode" in request.permissions)
            assertTrue("image" in request.permissions)
            val args = JSONArray(request.argsJson)
            assertEquals("image-1", args.getJSONObject(0).getString("id"))
            assertEquals("QR_CODE", args.getJSONObject(1).getJSONArray("formats").getString(0))
            NodeBridgeResponse.success(
                request.id,
                JSONArray()
                    .put(barcodeSnapshot("https://example.com", "QR_CODE", "URL"))
                    .toString(),
            )
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "barcode-detect-all",
                module = "barcode",
                method = "detectAll",
                argsJson = """[{"id":"image-1","__autojs6ImageHandle":"image-1","width":10,"height":20},{"formats":["QR_CODE"],"maxResults":8}]""",
                timeoutMs = 30000L,
                permissions = listOf("barcode", "image"),
            ),
        ) { response -> responses += response }

        assertEquals(listOf("detectAll"), provider.requests.map { it.method })
        assertEquals(1, responses.size)
        assertTrue(responses.single().ok)
        val result = JSONArray(responses.single().resultJson.orEmpty()).getJSONObject(0)
        assertEquals("autojs6-node-barcode-snapshot-v1", result.getString("schema"))
        assertEquals("https://example.com", result.getString("rawValue"))
        assertEquals("QR_CODE", result.getString("formatName"))
    }

    @Test
    fun jsBarcodeBridgeUsesFakeTransportPathInputsAndBlocksShadowing() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node barcode bridge fake transport regression")

        val workDir = File(NodePluginTestHarness.targetContext.cacheDir, "node-bridge-barcode-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create barcode bridge working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true, detect: () => "shadow" };"""
        File(workDir, "node_modules/barcode").apply {
            check(mkdirs()) { "Failed to create barcode shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText(shadowPackageSource)

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              function imageHandle(id, width, height) {
                return { id, __autojs6ImageHandle: id, width, height };
              }
              function snapshot(rawValue) {
                return {
                  schema: "autojs6-node-barcode-snapshot-v1",
                  rawValue,
                  displayValue: rawValue,
                  format: 256,
                  formatName: "QR_CODE",
                  valueType: 8,
                  valueTypeName: "URL",
                  boundingBox: { left: 1, top: 2, right: 3, bottom: 4, width: 2, height: 2 },
                  cornerPoints: [{ x: 1, y: 2 }],
                  structured: { payloadType: "url", url: { title: "Example", url: rawValue } }
                };
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = null;
                  if (request.module === "images" && request.method === "readImage") {
                    result = imageHandle("read-" + request.args[0], 16, 16);
                  } else if (request.module === "images" && request.method === "captureScreen") {
                    result = imageHandle("capture-1", 16, 16);
                  } else if (request.module === "images" && request.method === "recycle") {
                    result = null;
                  } else if (request.module === "barcode") {
                    if (request.method === "detect") result = snapshot("https://example.com/one");
                    else if (request.method === "detectAll") result = [snapshot("https://example.com/all")];
                    else if (request.method === "recognizeText") result = "https://example.com/text";
                    else if (request.method === "recognizeTexts") result = ["https://example.com/a", "https://example.com/b"];
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({ id: request.id, ok: true, result })));
                }
              });

              const barcode = require("barcode");
              const compat = require("autojs6:compat");
              const handle = imageHandle("image-1", 10, 20);
              const one = await barcode.detect(handle, { formats: ["QR_CODE"], timeoutMs: 1201 });
              const all = await barcode.detectAll({ type: "path", path: "fixtures/qr.png" }, { format: "QR_CODE", maxResults: 1, timeoutMs: 1202 });
              const text = await barcode.recognizeText(handle, { timeoutMs: 1203 });
              const texts = await barcode.recognizeTexts({ type: "capture", requireExistingPermission: true }, { qrOnly: true, timeoutMs: 1204 });
              const compatOne = await compat.barcode.detect(handle, { format: "QR_CODE", timeoutMs: 1210 });
              const compatAll = await compat.barcode.detectAll("fixtures/qr-compat.png", { format: "QR_CODE", maxResults: 1, timeoutMs: 1211 });
              const compatText = await compat.barcode.recognizeText({ timeoutMs: 1212 });
              const compatTexts = await compat.qrcode.recognizeTexts(handle, { maxResults: 2, timeoutMs: 1213 });
              const compatQrOne = await compat.qrcode.detect(handle, { timeoutMs: 1214 });
              const compatQrCallable = await compat.qrcode(handle, { timeoutMs: 1215 });

              let qrOnlyReject = "";
              try {
                await barcode.detect(handle, { qrOnly: true, formats: ["EAN_13"] });
              } catch (error) {
                qrOnlyReject = error.autojs6Code || error.code;
              }
              let compatQrReject = "";
              try {
                await compat.qrcode.detect(handle, { formats: ["EAN_13"] });
              } catch (error) {
                compatQrReject = error.autojs6Code || error.code;
              }
              let captureReject = "";
              try {
                await barcode.detect({ type: "capture", requireExistingPermission: false });
              } catch (error) {
                captureReject = error.autojs6Code || error.code;
              }
              const compatInstall = compat.install({ globals: true });

              console.log("barcode.resolve=" + require.resolve("barcode"));
              console.log("barcode.shadow=" + (barcode.shadow === true));
              console.log("barcode.frozen=" + Object.isFrozen(barcode));
              console.log("barcode.methods=" + [typeof barcode.detect, typeof barcode.detectAll, typeof barcode.recognizeText, typeof barcode.recognizeTexts, typeof barcode.rawScanner].join("|"));
              console.log("barcode.snapshot=" + [Object.isFrozen(one), Object.isFrozen(one.boundingBox), one.rawValue, one.formatName, one.structured.url.url].join("|"));
              console.log("barcode.all=" + [Object.isFrozen(all), all.length, all[0].rawValue].join("|"));
              console.log("barcode.text=" + [text, texts.join(",")].join("|"));
              console.log("compat.barcode.methods=" + [Object.isFrozen(compat.barcode), Object.isFrozen(compat.qrcode), typeof compat.barcode.detect, typeof compat.qrcode.detectAll, typeof compat.qrcode.rawScanner].join("|"));
              console.log("compat.barcode.results=" + [Object.isFrozen(compatOne), Object.isFrozen(compatAll), compatOne.rawValue, compatAll[0].rawValue, compatText, compatTexts.join(","), compatQrOne.formatName, compatQrCallable.length].join("|"));
              console.log("compat.barcode.global=" + [globalThis.barcode === compat.barcode, globalThis.qrcode === compat.qrcode, compatInstall.globals.indexOf("barcode") >= 0, compatInstall.globals.indexOf("qrcode") >= 0, compat.status().implementedTasks.indexOf("P12-29") >= 0, compat.status().moduleNames.indexOf("qrcode") >= 0].join("|"));
              console.log("barcode.rejects=" + [qrOnlyReject, compatQrReject, captureReject].join("|"));
              console.log("barcode.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              const barcodeCall = requests.find((request) => request.module === "barcode" && request.method === "detectAll");
              console.log("barcode.request=" + [barcodeCall.module, barcodeCall.method, barcodeCall.args[0].id, barcodeCall.args[1].formats[0], barcodeCall.timeoutMs, barcodeCall.permissions.join(",")].join("|"));
              const qrcodeCall = requests.find((request) => request.module === "barcode" && request.method === "detect" && request.timeoutMs === 1214);
              console.log("qrcode.request=" + [qrcodeCall.module, qrcodeCall.method, qrcodeCall.args[0].id, qrcodeCall.args[1].qrOnly, qrcodeCall.args[1].formats[0], qrcodeCall.timeoutMs].join("|"));
              console.log("barcode.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-barcode-fake.js>",
            workingDirectory = workDir,
            moduleSources = mapOf("node_modules/barcode/index.js" to shadowPackageSource),
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("image", "barcode", "screen_capture")).toJson()),
        )

        NodePluginTestHarness.assertSucceeded("barcode fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.resolve=autojs6:barcode")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.shadow=false")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.frozen=true")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.methods=function|function|function|function|undefined")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.snapshot=true|true|https://example.com/one|QR_CODE|https://example.com/one")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.all=true|1|https://example.com/all")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.text=https://example.com/text|https://example.com/a,https://example.com/b")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "compat.barcode.methods=true|true|function|function|undefined")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "compat.barcode.results=true|true|https://example.com/one|https://example.com/all|https://example.com/text|https://example.com/a,https://example.com/b|QR_CODE|2")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "compat.barcode.global=true|true|true|true|true|true")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.rejects=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "images.readImage|barcode.detectAll|images.recycle")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "images.captureScreen|barcode.recognizeTexts|images.recycle")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.request=barcode|detectAll|read-fixtures/qr.png|QR_CODE|1202|barcode,image")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "qrcode.request=barcode|detect|image-1|true|QR_CODE|1214")
        NodePluginTestHarness.assertStdoutContains("barcode fake bridge", result, "barcode.pending=0")
    }

    @Test
    fun jsBarcodeRequiresManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node barcode manifest capability regression")

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
                await require("barcode").detect({ id: "image-1", width: 10, height: 20 }, { timeoutMs: 1000 });
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("barcode.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-barcode-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("barcode manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "barcode.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|barcode|0|0",
            ),
        )
    }

    @Test
    fun kotlinBarcodeLiveProviderScansMlKitImageHandleAndCleansUp() {
        val barcodeHealth = NodeBridgeProviderRegistry.androidHealth(NodePluginTestHarness.targetContext)
            .associateBy { it.definition.id }
            .getValue("barcode")
        assumeTrue("MLKit barcode provider must be available for live barcode gate", barcodeHealth.status == NodeBridgeProviderStatus.AVAILABLE)

        val workDir = File(NodePluginTestHarness.targetContext.cacheDir, "node-bridge-barcode-live").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create barcode live working directory: $absolutePath" }
        }
        writeBlankBarcodeFixture(File(workDir, "barcode-live.png"))
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "permissions": ["image", "barcode"]
              }
            }
            """.trimIndent(),
        )

        val provider = AndroidNodeBridgeCapabilityProvider(
            NodePluginTestHarness.targetContext,
            NodeBridgeEngineInfo.fromContext(
                NodePluginTestHarness.targetContext,
                id = "barcode-live-provider",
                sourceName = "barcode-live-provider.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val read = dispatch(
            provider,
            module = "image",
            method = "readImage",
            argsJson = """["barcode-live.png"]""",
            timeoutMs = 10_000L,
            permissions = listOf("image"),
        )
        assertTrue(read.toString(), read.ok)
        val handle = JSONObject(read.resultJson.orEmpty())
        assertTrue(handle.getString("__autojs6ImageHandle").startsWith("image-"))

        listOf("detectAll", "detect", "recognizeText", "recognizeTexts").forEach { method ->
            val response = dispatch(
                provider,
                module = "barcode",
                method = method,
                argsJson = JSONArray()
                    .put(handle)
                    .put(JSONObject().put("formats", JSONArray().put("QR_CODE")).put("maxResults", 4))
                    .toString(),
                timeoutMs = 30_000L,
                permissions = listOf("barcode", "image"),
            )
            assertTrue("$method failed: $response", response.ok)
            when (method) {
                "detectAll" -> assertEquals(0, JSONArray(response.resultJson.orEmpty()).length())
                "detect" -> assertEquals("null", response.resultJson)
                "recognizeText" -> assertEquals("null", response.resultJson)
                "recognizeTexts" -> assertEquals(0, JSONArray(response.resultJson.orEmpty()).length())
            }
        }

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
            module = "barcode",
            method = "detect",
            argsJson = JSONArray().put(handle).put(JSONObject()).toString(),
            timeoutMs = 1_000L,
            permissions = listOf("barcode", "image"),
        )
        assertFalse(recycled.toString(), recycled.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, recycled.error?.code)

        val diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.image_active_handle_count"])
        assertEquals("0", diagnostics["embedded_script.barcode_active_operation_count"])
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("4", diagnostics["embedded_script.barcode_live_dispatch_count"])
        assertEquals("0", diagnostics["embedded_script.barcode_live_dispatch_failed_count"])
        assertEquals("0", diagnostics["embedded_script.barcode_service_destroy_cancel_count"])
        provider.destroy(NodeBridgeError.processDead())
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

    private fun barcodeSnapshot(rawValue: String, formatName: String, valueTypeName: String): JSONObject =
        JSONObject()
            .put("schema", "autojs6-node-barcode-snapshot-v1")
            .put("rawValue", rawValue)
            .put("displayValue", rawValue)
            .put("format", 256)
            .put("formatName", formatName)
            .put("valueType", 8)
            .put("valueTypeName", valueTypeName)

    private fun writeBlankBarcodeFixture(file: File) {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        file.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Failed to write barcode live fixture" }
        }
        bitmap.recycle()
    }
}
