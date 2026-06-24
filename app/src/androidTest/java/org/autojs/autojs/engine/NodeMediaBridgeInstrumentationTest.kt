package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeMediaBridgeInstrumentationTest {

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
    fun kotlinMediaRegistryAndFakeProviderAreStable() {
        listOf("getAudioStreamVolume", "getAudioStreamMaxVolume", "getAudioStreamInfo").forEach { method ->
            assertTrue(NodeBridgeModules.supports("media", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_AUDIO),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("media", method),
            )
        }
        listOf("read", "get").forEach { method ->
            assertTrue(NodeBridgeModules.supports("mediainfo", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_METADATA),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("mediainfo", method),
            )
        }
        listOf("getStatus", "start", "stop").forEach { method ->
            assertTrue(NodeBridgeModules.supports("recorder", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_RECORDING),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("recorder", method),
            )
        }
        assertFalse(NodeBridgeModules.supports("media", "play"))
        assertEquals(NodeBridgePermissionManifest.MEDIA_METADATA, NodeBridgePermissionManifest.normalizeCapability("mediainfo"))
        assertEquals(NodeBridgePermissionManifest.MEDIA_RECORDING, NodeBridgePermissionManifest.normalizeCapability("recorder"))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            when (request.module to request.method) {
                "media" to "getAudioStreamVolume" -> NodeBridgeResponse.success(request.id, "3")
                "media" to "getAudioStreamMaxVolume" -> NodeBridgeResponse.success(request.id, "15")
                "media" to "getAudioStreamInfo" -> NodeBridgeResponse.success(
                    request.id,
                    """{"schema":"autojs6-node-media-audio-stream-v1","stream":"music","streamType":3,"volume":3,"maxVolume":15,"minVolume":0,"volumeFixed":false}""",
                )
                "mediainfo" to "read" -> NodeBridgeResponse.success(
                    request.id,
                    """{"schema":"autojs6-node-mediainfo-snapshot-v1","path":"sample.mp3","fileName":"sample.mp3","sizeBytes":4,"inform":"","sections":{}}""",
                )
                "mediainfo" to "get" -> NodeBridgeResponse.success(request.id, NodeMediaBridgeJsonValue.string("MPEG Audio"))
                "recorder" to "getStatus" -> NodeBridgeResponse.success(
                    request.id,
                    """{"schema":"autojs6-node-recorder-status-v1","recording":false,"providerAvailable":false,"microphonePermissionRequired":true,"microphonePermissionGranted":false,"foregroundDisclosureRequired":true,"reason":"blocked_until_privacy_reviewed_recorder_provider"}""",
                )
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val requests = listOf(
            NodeBridgeRequest("media-volume", "media", "getAudioStreamVolume", """["music"]""", 1000L, listOf("media", "media.audio")),
            NodeBridgeRequest("media-info", "media", "getAudioStreamInfo", """["music"]""", 1000L, listOf("media", "media.audio")),
            NodeBridgeRequest("mediainfo-read", "mediainfo", "read", """["sample.mp3"]""", 1000L, listOf("media", "media.metadata")),
            NodeBridgeRequest("recorder-status", "recorder", "getStatus", "[]", 1000L, listOf("media", "media.recording")),
        )
        val responses = mutableListOf<NodeBridgeResponse>()
        requests.forEach { request -> provider.dispatch(request) { response -> responses += response } }

        assertEquals(requests.size, provider.requests.size)
        assertTrue(responses.all { it.ok })
        assertTrue(provider.requests.filter { it.module == "media" }.all { "media.audio" in it.permissions })
        assertTrue(provider.requests.filter { it.module == "mediainfo" }.all { "media.metadata" in it.permissions })
        assertTrue(provider.requests.filter { it.module == "recorder" }.all { "media.recording" in it.permissions })
    }

    @Test
    fun jsMediaModulesUseFakeTransportCompatFacadeAndBlockShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node media bridge fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-media-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create media bridge working directory: $absolutePath" }
        }
        listOf("media", "mediainfo", "recorder").forEach { name ->
            File(workDir, "node_modules/$name").apply {
                check(mkdirs()) { "Failed to create $name shadow module directory: $absolutePath" }
            }.resolve("index.js").writeText("""module.exports = { shadow: true };""")
        }

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const compat = require("autojs6:compat");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let ok = true;
                  let result = null;
                  let error = null;
                  if (request.module === "media" && request.method === "getAudioStreamVolume") {
                    result = 3;
                  } else if (request.module === "media" && request.method === "getAudioStreamMaxVolume") {
                    result = 15;
                  } else if (request.module === "media" && request.method === "getAudioStreamInfo") {
                    result = { schema: "autojs6-node-media-audio-stream-v1", stream: "music", streamType: 3, volume: 3, maxVolume: 15, minVolume: 0, volumeFixed: false };
                  } else if (request.module === "mediainfo" && request.method === "read") {
                    result = { schema: "autojs6-node-mediainfo-snapshot-v1", path: request.args[0], fileName: "sample.mp3", sizeBytes: 4, inform: "", sections: {} };
                  } else if (request.module === "mediainfo" && request.method === "get") {
                    result = "MPEG Audio";
                  } else if (request.module === "recorder" && request.method === "getStatus") {
                    result = { schema: "autojs6-node-recorder-status-v1", recording: false, providerAvailable: false, microphonePermissionRequired: true, microphonePermissionGranted: false, foregroundDisclosureRequired: true, reason: "blocked_until_privacy_reviewed_recorder_provider" };
                  } else if (request.module === "recorder" && request.method === "start") {
                    ok = false;
                    error = { name: "Error", code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED", module: "recorder", method: "start", message: "recorder blocked" };
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({ id: request.id, ok, result, error })));
                }
              });

              const media = require("media");
              const mediainfo = require("mediainfo");
              const recorder = require("recorder");
              const volume = await media.getAudioStreamVolume("music", { timeoutMs: 1000 });
              const max = await media.getAudioStreamMaxVolume("music", { timeoutMs: 1000 });
              const info = await media.getAudioStreamInfo("music", { timeoutMs: 1000 });
              const compatInfo = await compat.media.getAudioStreamInfo("music", { timeoutMs: 1000 });
              const snapshot = await mediainfo.read("sample.mp3", { includeInform: false, timeoutMs: 1000 });
              const callable = await mediainfo("sample.mp3", { timeoutMs: 1000 });
              const format = await mediainfo.get("sample.mp3", "general", "Format", { timeoutMs: 1000 });
              const compatSnapshot = await compat.mediainfo.read("sample.mp3", { timeoutMs: 1000 });
              const status = await recorder.getStatus({ timeoutMs: 1000 });
              const compatStatus = await compat.recorder.getStatus({ timeoutMs: 1000 });
              let startCode = "";
              try {
                await recorder.start({ path: "record.m4a", timeoutMs: 1000 });
              } catch (error) {
                startCode = String(error && (error.autojs6Code || error.code || ""));
              }
              let invalidPath = "";
              try {
                await mediainfo.read("../escape.mp3", { timeoutMs: 1000 });
              } catch (error) {
                invalidPath = String(error && (error.autojs6Code || error.code || ""));
              }

              console.log("media.resolve=" + [require.resolve("media"), require.resolve("mediainfo"), require.resolve("recorder")].join("|"));
              console.log("media.shadow=" + [media.shadow === true, mediainfo.shadow === true, recorder.shadow === true].join("|"));
              console.log("media.frozen=" + [Object.isFrozen(media), Object.isFrozen(mediainfo), Object.isFrozen(recorder), Object.isFrozen(compat.media), Object.isFrozen(compat.mediainfo), Object.isFrozen(compat.recorder)].join("|"));
              console.log("media.methods=" + [typeof media.getAudioStreamInfo, typeof mediainfo.read, typeof mediainfo.get, typeof recorder.getStatus, typeof recorder.start, typeof recorder.stop].join("|"));
              console.log("media.values=" + [volume, max, info.schema, compatInfo.schema, snapshot.schema, callable.schema, format, compatSnapshot.schema, status.schema, compatStatus.schema, startCode, invalidPath].join("|"));
              console.log("media.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("media.permissions=" + [
                requests.filter((request) => request.module === "media").every((request) => request.permissions.join("|") === "media|media.audio"),
                requests.filter((request) => request.module === "mediainfo").every((request) => request.permissions.join("|") === "media|media.metadata"),
                requests.filter((request) => request.module === "recorder").every((request) => request.permissions.join("|") === "media|media.recording")
              ].join("|"));
              console.log("media.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-media-fake.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("media fake bridge", result)
        assertTrue(result.stdout.contains("media.resolve=media|mediainfo|recorder"))
        assertTrue(result.stdout.contains("media.shadow=false|false|false"))
        assertTrue(result.stdout.contains("media.frozen=true|true|true|true|true|true"))
        assertTrue(result.stdout.contains("media.methods=function|function|function|function|function|function"))
        assertTrue(
            result.stdout.contains(
                "media.values=3|15|autojs6-node-media-audio-stream-v1|autojs6-node-media-audio-stream-v1|autojs6-node-mediainfo-snapshot-v1|autojs6-node-mediainfo-snapshot-v1|MPEG Audio|autojs6-node-mediainfo-snapshot-v1|autojs6-node-recorder-status-v1|autojs6-node-recorder-status-v1|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}",
            ),
        )
        assertTrue(result.stdout.contains("media.requests=media.getAudioStreamVolume|media.getAudioStreamMaxVolume|media.getAudioStreamInfo|media.getAudioStreamInfo|mediainfo.read|mediainfo.read|mediainfo.get|mediainfo.read|recorder.getStatus|recorder.getStatus|recorder.start"))
        assertTrue(result.stdout.contains("media.permissions=true|true|true"))
        assertTrue(result.stdout.contains("media.pending=0"))
    }

    @Test
    fun jsMediaCapabilitiesRequireExactManifestBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node media exact manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("media"))
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
              async function denied(label, action) {
                let code = "";
                let missing = "";
                try {
                  await action();
                } catch (error) {
                  code = String(error && (error.autojs6Code || error.code || ""));
                  missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
                }
                console.log("media.manifest." + label + "=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
              }
              await denied("audio", () => require("media").getAudioStreamInfo("music", { timeoutMs: 1000 }));
              await denied("metadata", () => require("mediainfo").read("sample.mp3", { timeoutMs: 1000 }));
              await denied("recording", () => require("recorder").getStatus({ timeoutMs: 1000 }));
            })();
            """.trimIndent(),
            "<embedded-autojs-media-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("media exact manifest capability denial", result)
        assertTrue(result.stdout.contains("media.manifest.audio=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.audio|0|0"))
        assertTrue(result.stdout.contains("media.manifest.metadata=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.metadata|0|0"))
        assertTrue(result.stdout.contains("media.manifest.recording=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.recording|0|0"))
    }

    @Test
    fun androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderFailsClosed() {
        val workDir = File(context.cacheDir, "node-bridge-media-provider").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create media provider directory: $absolutePath" }
            resolve("project.json").writeText(
                """
                {
                  "type": "node",
                  "node": {
                    "permissions": ["media", "media.audio", "media.metadata", "media.recording"]
                  }
                }
                """.trimIndent(),
            )
            resolve("sample.mp3").writeBytes(byteArrayOf(0, 1, 2, 3))
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "media-provider",
                sourceName = "media.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val audio = dispatch(provider, "media", "getAudioStreamInfo", """["music"]""")
        assertTrue(audio.toString(), audio.ok)
        assertEquals("autojs6-node-media-audio-stream-v1", jsonObject(audio.resultJson.orEmpty()).getString("schema"))

        val snapshot = dispatch(provider, "mediainfo", "read", """["sample.mp3",{"includeInform":false}]""")
        assertTrue(snapshot.toString(), snapshot.ok)
        val snapshotJson = jsonObject(snapshot.resultJson.orEmpty())
        assertEquals("autojs6-node-mediainfo-snapshot-v1", snapshotJson.getString("schema"))
        assertEquals("sample.mp3", snapshotJson.getString("path"))
        assertEquals("", snapshotJson.getString("inform"))

        val format = dispatch(provider, "mediainfo", "get", """["sample.mp3","general","Format"]""")
        assertTrue(format.toString(), format.ok)
        assertTrue(format.resultJson != null)

        val status = dispatch(provider, "recorder", "getStatus", "[]")
        assertTrue(status.toString(), status.ok)
        val statusJson = jsonObject(status.resultJson.orEmpty())
        assertEquals("autojs6-node-recorder-status-v1", statusJson.getString("schema"))
        assertFalse(statusJson.getBoolean("providerAvailable"))
        assertTrue(statusJson.getBoolean("foregroundDisclosureRequired"))

        val start = dispatch(provider, "recorder", "start", """[{"path":"record.m4a"}]""")
        assertFalse(start.toString(), start.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, start.error?.code)
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        val request = NodeBridgeRequest(
            id = "$module-$method-${System.nanoTime()}",
            module = module,
            method = method,
            argsJson = argsJson,
            timeoutMs = 5_000L,
            permissions = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method),
        )
        provider.dispatch(request) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
    }

    private fun jsonObject(raw: String): JSONObject {
        return JSONTokener(raw).nextValue() as JSONObject
    }
}

private object NodeMediaBridgeJsonValue {
    fun string(value: String): String = JSONArray().put(value).toString().let { wrapped ->
        wrapped.substring(1, wrapped.length - 1)
    }
}
