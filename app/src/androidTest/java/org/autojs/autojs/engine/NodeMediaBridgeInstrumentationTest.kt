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

    @Test
    fun realMediainfoQueriesPreservePathsAndSelectSecondAudioTrack() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("MediaInfo query integration")
        val workDir = File(context.cacheDir, "mediainfo-query-${System.nanoTime()}").apply { check(mkdirs()) }
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("mediainfo-two-audio.mka").use { input ->
                File(workDir, "two-audio.mka").outputStream().use { input.copyTo(it) }
            }
            File(workDir, "project.json").writeText("""{"type":"node","node":{"permissions":["media","media.metadata"]}}""")
            val result = NodePluginTestHarness.runPluginScript(
                """
                "nodejs";
                (async () => {
                  const assert = require("node:assert/strict");
                  const mi = require("mediainfo");
                  const compat = require("autojs6:compat").mediainfo;
                  assert.equal(await mi.countGet("two-audio.mka", "audio"), 2);
                  assert.equal(await mi.get("two-audio.mka", "audio", "SamplingRate"), "8000");
                  assert.equal(await mi.get("two-audio.mka", "audio", "SamplingRate", {streamNumber: 1}), "16000");
                  assert.equal((await compat.get("two-audio.mka", "audio", "SamplingRate", {streamNumber: 1, infoKind: "MEASURE"})).trim(), "Hz");
                  assert.equal(await compat.countGet("two-audio.mka", "audio"), 2);
                  await assert.rejects(mi.get("two-audio.mka", "audio", "Format", {streamNumber: 0.5}), /streamNumber/);
                  await assert.rejects(mi.get("two-audio.mka", "audio", "Format", {infoKind: "MAX"}), /infoKind/);
                  const snapshot = await mi.read("two-audio.mka");
                  assert.equal(snapshot.fileName, "two-audio.mka");
                  assert.equal(await mi.get("two-audio.mka", "general", "CompleteName"), ${JSONObject.quote(File(workDir, "two-audio.mka").absolutePath)});
                  assert.ok(snapshot.inform.includes(${JSONObject.quote(File(workDir, "two-audio.mka").absolutePath)}), snapshot.inform);
                  assert.ok(!snapshot.inform.includes("/proc/self/fd/"));
                  const bridge = require("autojs6:bridge");
                  let queries = 0;
                  bridge.__test.setTransport({postMessage(message) {
                    const request = JSON.parse(message);
                    if (request.method === "get" || request.method === "countGet") queries++;
                    setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({id: request.id, ok: true, result: {}})));
                  }});
                  await assert.rejects(mi.get("two-audio.mka", "audio", "Format", {streamNumber: 1}), /MEDIAINFO_QUERY_UNSUPPORTED/);
                  await assert.rejects(mi.countGet("two-audio.mka", "audio"), /MEDIAINFO_QUERY_UNSUPPORTED/);
                  assert.equal(queries, 0);
                  console.log("mediainfo.queries=PASS second-track,unit,count,compat,path,validation,old-host");
                })();
                """.trimIndent(),
                "mediainfo-query.cjs", workingDirectory = workDir, timeoutMs = 60_000,
            )
            NodePluginTestHarness.assertSucceeded("MediaInfo query integration", result)
            assertTrue(result.stdout, result.stdout.contains("mediainfo.queries=PASS"))
        } finally { workDir.deleteRecursively() }
    }

    @Test
    fun oldPluginSnapshotsAreNormalizedBeforeReachingTheDetailsPage() {
        val raw = JSONObject()
            .put("fileName", "movie.mkv")
            .put("inform", "File\r\nComplete name : /data/user/0/plugin/cache/autojs6-mediainfo-123.mkv\r\n")
            .put("sections", JSONObject().put("file", JSONArray().put(JSONObject().put("completeName", "/proc/self/fd/42"))))
        val path = "/storage/emulated/0/Movies/movie.mkv"
        val result = JSONObject(org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost.normalizeSnapshotIdentity(raw.toString(), path))
        assertTrue(result.getString("inform").contains(path))
        assertEquals(path, result.getJSONObject("sections").getJSONArray("file").getJSONObject(0).getString("completeName"))
        assertEquals("movie.mkv", result.getString("fileName"))
    }

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
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
        listOf("read", "get", "capabilities").forEach { method ->
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
        listOf("play", "pause", "resume", "stop", "seekTo", "getPlaybackStatus").forEach { method ->
            assertTrue(NodeBridgeModules.supports("media", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_PLAYBACK),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("media", method),
            )
        }
        listOf("capabilities", "query", "get", "scanFile", "exportFile").forEach { method ->
            assertTrue(NodeBridgeModules.supports("media_store", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_LIBRARY),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("media_store", method),
            )
        }
        listOf("insert", "update", "delete").forEach { method ->
            assertTrue(NodeBridgeModules.supports("media_store", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_LIBRARY, NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("media_store", method),
            )
        }
        assertFalse(NodeBridgeModules.supports("media", "playlist"))
        assertFalse(NodeBridgeModules.supports("media_store", "openFile"))
        // Playback, the library and mutation are explicit capabilities; "media" alone never implies them.
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA_PLAYBACK),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_AUDIO),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_PLAYBACK),
            ),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_LIBRARY),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_LIBRARY, NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE),
            ),
        )
        assertEquals(NodeBridgePermissionManifest.MEDIA_PLAYBACK, NodeBridgePermissionManifest.normalizeCapability("media-playback"))
        assertEquals(NodeBridgePermissionManifest.MEDIA_LIBRARY, NodeBridgePermissionManifest.normalizeCapability("media_store"))
        assertEquals(NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE, NodeBridgePermissionManifest.normalizeCapability("media_store.mutate"))
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
                "mediainfo" to "capabilities" -> NodeBridgeResponse.success(
                    request.id,
                    """{"schema":"autojs6-node-mediainfo-capabilities-v1","snapshotSchemas":["autojs6-plugin-mediainfo-snapshot-v1","autojs6-plugin-mediainfo-snapshot-v2"],"defaultSnapshotSchema":"autojs6-plugin-mediainfo-snapshot-v1","engineVersion":"MediaInfoLib - v26.05"}""",
                )
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
            NodeBridgeRequest("mediainfo-capabilities", "mediainfo", "capabilities", "[]", 1000L, listOf("media", "media.metadata")),
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
    fun jsMediaModulesUseFakeTransportAndCompatFacade() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node media bridge fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-media-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create media bridge working directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "permissions": ["media", "media.audio", "media.metadata", "media.recording"]
              }
            }
            """.trimIndent(),
        )

        val result = NodePluginTestHarness.runPluginScript(
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
                    const requestedSchema = request.args[1] && request.args[1].schema;
                    result = requestedSchema === "autojs6-plugin-mediainfo-snapshot-v2"
                      ? { schema: requestedSchema, file: { name: "sample.mp3", sizeBytes: 4 }, engine: { name: "MediaInfoLib", version: "26.05" }, inform: "", tracks: { audio: [{ fields: { Format: "MPEG Audio" } }] } }
                      : { schema: "autojs6-node-mediainfo-snapshot-v1", path: request.args[0], fileName: "sample.mp3", sizeBytes: 4, inform: "", sections: {} };
                  } else if (request.module === "mediainfo" && request.method === "get") {
                    result = "MPEG Audio";
                  } else if (request.module === "mediainfo" && request.method === "capabilities") {
                    result = { schema: "autojs6-node-mediainfo-capabilities-v1", snapshotSchemas: ["autojs6-plugin-mediainfo-snapshot-v1", "autojs6-plugin-mediainfo-snapshot-v2"], defaultSnapshotSchema: "autojs6-plugin-mediainfo-snapshot-v1", engineVersion: "MediaInfoLib - v26.05" };
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
              const snapshotV2 = await mediainfo.read("sample.mp3", { schema: "autojs6-plugin-mediainfo-snapshot-v2", includeInform: false, timeoutMs: 1000 });
              const capabilities = await mediainfo.capabilities({ timeoutMs: 1000 });
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
                await mediainfo.read(String.fromCharCode(0), { timeoutMs: 1000 });
              } catch (error) {
                invalidPath = String(error && (error.autojs6Code || error.code || ""));
              }

              console.log("media.resolve=" + [require.resolve("media"), require.resolve("mediainfo"), require.resolve("recorder")].join("|"));
              console.log("media.shadow=" + [media.shadow === true, mediainfo.shadow === true, recorder.shadow === true].join("|"));
              console.log("media.frozen=" + [Object.isFrozen(media), Object.isFrozen(mediainfo), Object.isFrozen(recorder), Object.isFrozen(compat.media), Object.isFrozen(compat.mediainfo), Object.isFrozen(compat.recorder)].join("|"));
              console.log("media.methods=" + [typeof media.getAudioStreamInfo, typeof mediainfo.read, typeof mediainfo.get, typeof mediainfo.capabilities, typeof recorder.getStatus, typeof recorder.start, typeof recorder.stop].join("|"));
              console.log("media.values=" + [volume, max, info.schema, compatInfo.schema, snapshot.schema, snapshotV2.schema, snapshotV2.tracks.audio[0].fields.Format, capabilities.schema, capabilities.engineVersion, callable.schema, format, compatSnapshot.schema, status.schema, compatStatus.schema, startCode, invalidPath].join("|"));
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

        NodePluginTestHarness.assertSucceeded("media fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("media fake bridge", result, "media.resolve=autojs6:media|autojs6:mediainfo|autojs6:recorder")
        NodePluginTestHarness.assertStdoutContains("media fake bridge", result, "media.shadow=false|false|false")
        NodePluginTestHarness.assertStdoutContains("media fake bridge", result, "media.frozen=true|true|true|true|true|true")
        NodePluginTestHarness.assertStdoutContains("media fake bridge", result, "media.methods=function|function|function|function|function|function|function")
        assertTrue(
            result.stdout.contains(
                "media.values=3|15|autojs6-node-media-audio-stream-v1|autojs6-node-media-audio-stream-v1|autojs6-node-mediainfo-snapshot-v1|autojs6-plugin-mediainfo-snapshot-v2|MPEG Audio|autojs6-node-mediainfo-capabilities-v1|MediaInfoLib - v26.05|autojs6-node-mediainfo-snapshot-v1|MPEG Audio|autojs6-node-mediainfo-snapshot-v1|autojs6-node-recorder-status-v1|autojs6-node-recorder-status-v1|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}",
            ),
        )
        NodePluginTestHarness.assertStdoutContains("media fake bridge", result, "media.requests=media.getAudioStreamVolume|media.getAudioStreamMaxVolume|media.getAudioStreamInfo|media.getAudioStreamInfo|mediainfo.read|mediainfo.read|mediainfo.capabilities|mediainfo.read|mediainfo.get|mediainfo.read|recorder.getStatus|recorder.getStatus|recorder.start")
        NodePluginTestHarness.assertStdoutContains("media fake bridge", result, "media.permissions=true|true|true")
        NodePluginTestHarness.assertStdoutContains("media fake bridge", result, "media.pending=0")
    }

    @Test
    fun jsMediaCapabilitiesRequireExactManifestBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node media exact manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("media"))
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

        NodePluginTestHarness.assertSucceeded("media exact manifest capability denial", result)
        assertTrue(result.stdout.contains("media.manifest.audio=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.audio|0|0"))
        assertTrue(result.stdout.contains("media.manifest.metadata=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.metadata|0|0"))
        assertTrue(result.stdout.contains("media.manifest.recording=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.recording|0|0"))
    }

    @Test
    fun androidProviderReturnsReadOnlyMediaSnapshotsAndRecorderPermissionStatus() {
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
            resolve("sample.wav").writeBytes(minimalPcmWave())
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

        val snapshot = dispatch(provider, "mediainfo", "read", """["sample.wav",{"includeInform":false}]""")
        assertTrue(snapshot.toString(), snapshot.ok)
        val snapshotJson = jsonObject(snapshot.resultJson.orEmpty())
        assertEquals("autojs6-node-mediainfo-snapshot-v1", snapshotJson.getString("schema"))
        assertEquals("sample.wav", snapshotJson.getString("path"))
        assertEquals("", snapshotJson.getString("inform"))

        val snapshotV2 = dispatch(
            provider,
            "mediainfo",
            "read",
            """["sample.wav",{"schema":"autojs6-plugin-mediainfo-snapshot-v2","includeInform":false}]""",
        )
        assertTrue(snapshotV2.toString(), snapshotV2.ok)
        val snapshotV2Json = jsonObject(snapshotV2.resultJson.orEmpty())
        assertEquals("autojs6-plugin-mediainfo-snapshot-v2", snapshotV2Json.getString("schema"))
        assertEquals("sample.wav", snapshotV2Json.getJSONObject("file").getString("name"))
        assertTrue(snapshotV2Json.getJSONObject("tracks").getJSONArray("audio").length() > 0)

        val capabilities = dispatch(provider, "mediainfo", "capabilities", "[]")
        assertTrue(capabilities.toString(), capabilities.ok)
        val capabilitiesJson = jsonObject(capabilities.resultJson.orEmpty())
        assertEquals("autojs6-node-mediainfo-capabilities-v1", capabilitiesJson.getString("schema"))
        assertTrue(
            capabilitiesJson.getJSONArray("snapshotSchemas")
                .toString()
                .contains("autojs6-plugin-mediainfo-snapshot-v2"),
        )
        assertTrue(capabilitiesJson.optString("engineVersion").isNotBlank())

        val format = dispatch(provider, "mediainfo", "get", """["sample.wav","general","Format"]""")
        assertTrue(format.toString(), format.ok)
        assertTrue(format.resultJson != null)

        val status = dispatch(provider, "recorder", "getStatus", "[]")
        assertTrue(status.toString(), status.ok)
        val statusJson = jsonObject(status.resultJson.orEmpty())
        assertEquals("autojs6-node-recorder-status-v1", statusJson.getString("schema"))
        assertTrue(statusJson.getBoolean("providerAvailable"))
        assertTrue(statusJson.getBoolean("foregroundDisclosureRequired"))

        // Recording is opt-in; an ordinary regression run must never open the microphone.
        if (!statusJson.getBoolean("microphonePermissionGranted")) {
            val start = dispatch(provider, "recorder", "start", """[{"path":"record.m4a"}]""")
            assertFalse(start.toString(), start.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, start.error?.code)
        }
        provider.destroy()
    }

    @Test
    fun androidProviderUsesAndroidFileAccessOutsideTheWorkingDirectory() {
        val root = File(context.cacheDir, "node-media-paths-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val workDir = root.resolve("project").apply { check(mkdirs()) }
        val wave = root.resolve("media..take:01.wav").apply { writeBytes(minimalPcmWave()) }
        val image = root.resolve("image..take:01.png")
        android.graphics.Bitmap.createBitmap(16, 12, android.graphics.Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(android.graphics.Color.MAGENTA)
            image.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        android.system.Os.symlink(wave.absolutePath, workDir.resolve("linked.wav").absolutePath)
        val provider = AndroidNodeBridgeCapabilityProvider(context,
            NodeBridgeEngineInfo.fromContext(context, id = "media-paths", sourceName = "main.cjs", cwd = workDir.absolutePath))
        try {
            for (path in listOf(wave.absolutePath, "../${wave.name}", "linked.wav")) {
                val response = dispatch(provider, "mediainfo", "read", JSONArray().put(path).put(JSONObject().put("includeInform", false)).toString())
                assertTrue("Android-readable media path $path: $response", response.ok)
                assertTrue(JSONObject(response.resultJson.orEmpty()).getJSONObject("sections").getJSONArray("audio").length() > 0)
            }
            val format = dispatch(provider, "mediainfo", "get", JSONArray().put(wave.absolutePath).put("general").put("Format").toString())
            assertTrue("absolute mediainfo.get: $format", format.ok)
            for ((index, path) in listOf(image.absolutePath, "../${image.name}").withIndex()) {
                val read = dispatch(provider, "image", "readImage", JSONArray().put(path).toString())
                assertTrue("Android-readable image path $path: $read", read.ok)
                val handle = JSONObject(read.resultJson.orEmpty())
                val output = root.resolve("saved-$index.png")
                val outputPath = if (index == 0) output.absolutePath else "../${output.name}"
                val saved = dispatch(provider, "image", "saveImage", JSONArray().put(handle).put(outputPath).toString())
                assertTrue("Android-writable image path $outputPath: $saved", saved.ok)
                assertTrue(output.length() > 0)
                val reread = android.graphics.BitmapFactory.decodeFile(output.absolutePath)
                assertEquals(16, reread.width)
                assertEquals(12, reread.height)
                reread.recycle()
                assertTrue(dispatch(provider, "image", "recycle", JSONArray().put(handle).toString()).ok)
            }
            val script = NodePluginTestHarness.runPluginScript(
                """
                (async () => {
                  const mediainfo = require('mediainfo'), images = require('images');
                  for (const path of ${JSONArray(listOf(wave.absolutePath, "../${wave.name}", "linked.wav"))}) {
                    const snapshot = await mediainfo.read(path, {includeInform: false});
                    if (!snapshot.sections.audio.length) throw new Error('No audio track: ' + path);
                  }
                  for (const path of ${JSONArray(listOf(image.absolutePath, "../${image.name}"))}) {
                    const handle = await images.readImage(path);
                    try {
                      await images.saveImage(handle, ${JSONObject.quote(root.resolve("script-output.png").absolutePath)});
                    } finally { await images.recycle(handle); }
                  }
                  console.log('m20.facade-file-paths=PASS');
                })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                """.trimIndent(),
                "media-paths.cjs", workingDirectory = workDir, timeoutMs = 60_000,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to
                    NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("media", "media.metadata", "image")).toJson()),
            )
            NodePluginTestHarness.assertSucceeded("media/image paths through the plugin", script)
            assertTrue(script.stdout, script.stdout.contains("m20.facade-file-paths=PASS"))
            assertTrue(root.resolve("script-output.png").length() > 0)
            android.system.Os.chmod(wave.absolutePath, 0)
            try {
                val denied = dispatch(provider, "mediainfo", "read", JSONArray().put(wave.absolutePath).toString())
                assertFalse("Android must deny opening a mode-000 fixture: $denied", denied.ok)
                assertTrue(denied.toString(), denied.error?.message.orEmpty().contains("Permission denied", ignoreCase = true)
                    || denied.error?.message.orEmpty().contains("EACCES"))
            } finally { android.system.Os.chmod(wave.absolutePath, 0x180) }
            println("m20.media-image-paths=PASS absolute,parent,symlink,filename,android-denial")
        } finally {
            provider.destroy()
            root.deleteRecursively()
        }
    }

    @Test
    fun androidProviderPlaysWorkspaceAudioAsSessionsWithControls() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        // Foreground media playback needs a visible activity (or another Android exemption); the host test brings one up.
        val activity = instrumentation.startActivitySync(
            android.content.Intent(context, org.autojs.autojs.ui.log.LogActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val workDir = File(context.cacheDir, "node-bridge-media-playback").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create media playback directory: $absolutePath" }
            resolve("silence.wav").writeBytes(silentPcmWave(seconds = 5))
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "media-playback", sourceName = "play.cjs", cwd = workDir.absolutePath),
        )
        try {
            val missing = dispatch(provider, "media", "play", """["missing.wav"]""")
            assertFalse(missing.toString(), missing.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, missing.error?.code)

            val played = dispatch(provider, "media", "play", """["silence.wav",{"volume":0.2}]""", timeoutMs = 40_000L)
            assertTrue(played.toString(), played.ok)
            val session = jsonObject(played.resultJson.orEmpty())
            assertEquals(NodeMediaPlayback.SCHEMA, session.getString("schema"))
            val id = session.getString("id")
            assertTrue(id.startsWith("media-playback-"))
            assertEquals(workDir.resolve("silence.wav").absolutePath, session.getString("path"))
            assertTrue(session.toString(), session.getBoolean("active"))
            assertTrue(session.toString(), session.getBoolean("playing"))
            assertFalse(session.getBoolean("ended"))
            assertFalse(session.getBoolean("looping"))
            assertEquals(0.2, session.getDouble("volume"), 1e-9)
            assertTrue(session.toString(), session.getInt("durationMs") in 4_000..6_000)
            assertTrue(session.toString(), session.getInt("positionMs") >= 0)

            val paused = jsonObject(dispatch(provider, "media", "pause", """[{"id":"$id"}]""").resultJson.orEmpty())
            assertTrue(paused.getBoolean("active"))
            assertFalse(paused.toString(), paused.getBoolean("playing"))

            val sought = jsonObject(dispatch(provider, "media", "seekTo", """[3000,{"id":"$id"}]""").resultJson.orEmpty())
            assertTrue(sought.toString(), sought.getInt("positionMs") in 2_500..3_500)

            val resumed = jsonObject(dispatch(provider, "media", "resume", """[{"id":"$id"}]""").resultJson.orEmpty())
            assertTrue(resumed.toString(), resumed.getBoolean("playing"))

            val stale = jsonObject(dispatch(provider, "media", "pause", """[{"id":"media-playback-stale"}]""").resultJson.orEmpty())
            assertFalse(stale.getBoolean("active"))
            assertEquals("media-playback-stale", stale.getString("id"))
            val still = jsonObject(dispatch(provider, "media", "getPlaybackStatus", "[]").resultJson.orEmpty())
            assertTrue("a stale id must not pause the current session: $still", still.getBoolean("playing"))

            val stopped = jsonObject(dispatch(provider, "media", "stop", """[{"id":"$id"}]""").resultJson.orEmpty())
            assertFalse(stopped.getBoolean("active"))
            assertEquals(id, stopped.getString("id"))
            val after = jsonObject(dispatch(provider, "media", "getPlaybackStatus", """[{"id":"$id"}]""").resultJson.orEmpty())
            assertFalse(after.getBoolean("active"))
            assertEquals(-1, after.getInt("positionMs"))

            val looping = jsonObject(dispatch(provider, "media", "play", """["silence.wav",{"looping":true,"volume":0}]""", timeoutMs = 40_000L).resultJson.orEmpty())
            assertTrue(looping.getBoolean("looping"))
            assertTrue(looping.getBoolean("playing"))
        } finally {
            provider.destroy()
            instrumentation.runOnMainSync { activity.finish() }
            workDir.deleteRecursively()
        }
        assertTrue("execution cleanup must release the script music lease", ScriptMusicPlayerProbe.isIdle(context))
    }

    @Test
    fun androidProviderRoundTripsAppOwnedMediaStoreItems() {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            org.junit.Assume.assumeTrue(
                "MediaStore mutation below Android 10 needs WRITE_EXTERNAL_STORAGE",
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
        }
        val workDir = File(context.cacheDir, "node-bridge-media-store").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create media store directory: $absolutePath" }
            resolve("clip.wav").writeBytes(silentPcmWave(seconds = 1))
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "media-store", sourceName = "library.cjs", cwd = workDir.absolutePath),
        )
        val displayName = "autojs6-node-${java.util.UUID.randomUUID()}.wav"
        var insertedId: Long? = null
        try {
            val capabilities = okJson(dispatch(provider, "media_store", "capabilities", "[]"))
            assertEquals(NodeMediaStore.CAPABILITIES_SCHEMA, capabilities.getString("schema"))
            assertTrue(capabilities.getJSONArray("collections").toString().contains("\"audio\""))
            assertEquals("structured_json_filters_only", capabilities.getString("filterPolicy"))

            val badCollection = dispatch(provider, "media_store", "query", """["playlists"]""")
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, badCollection.error?.code)
            val badColumn = dispatch(provider, "media_store", "query", """["audio",{"filter":{"_data":"x"}}]""")
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, badColumn.error?.code)

            val inserted = dispatch(
                provider, "media_store", "insert",
                """["audio",{"displayName":"$displayName","mimeType":"audio/wav","source":"clip.wav","relativePath":"Music/AutoJs6NodeTest/"}]""",
            )
            assertTrue(inserted.toString(), inserted.ok)
            val item = jsonObject(inserted.resultJson.orEmpty())
            assertEquals(NodeMediaStore.ITEM_SCHEMA, item.getString("schema"))
            assertEquals("audio", item.getString("collection"))
            assertEquals(displayName, item.getString("displayName"))
            // MediaStore may normalise audio/wav to audio/x-wav depending on the platform MIME map.
            assertTrue(item.getString("mimeType"), item.getString("mimeType") in setOf("audio/wav", "audio/x-wav"))
            assertTrue(item.getString("uri").startsWith("content://media/"))
            insertedId = item.getLong("id")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                assertEquals(context.packageName, item.getString("ownerPackageName"))
                assertFalse(item.getBoolean("isPending"))
            }

            val queried = jsonObject(
                dispatch(provider, "media_store", "query", """["audio",{"filter":{"displayName":{"startsWith":"autojs6-node-"},"ownedOnly":true},"sort":"-dateAdded","limit":10,"columns":["displayName","size","mimeType"]}]""").resultJson.orEmpty(),
            )
            assertEquals(NodeMediaStore.QUERY_SCHEMA, queried.getString("schema"))
            val items = queried.getJSONArray("items")
            assertTrue(queried.toString(), (0 until items.length()).any { items.getJSONObject(it).getLong("id") == insertedId })
            assertTrue(items.getJSONObject(0).has("size"))
            assertFalse(items.getJSONObject(0).has("title"))

            val got = okJson(dispatch(provider, "media_store", "get", """["audio",$insertedId]"""))
            assertEquals(displayName, got.getString("displayName"))
            assertEquals(workDir.resolve("clip.wav").length(), got.getLong("size"))

            val renamedName = displayName.replace(".wav", "-renamed.wav")
            val updated = okJson(dispatch(provider, "media_store", "update", """["audio",$insertedId,{"displayName":"$renamedName"}]"""))
            assertEquals(renamedName, updated.getString("displayName"))
            // MediaProvider derives TITLE from metadata, so the bridge rejects it instead of pretending the write happened.
            val rejected = dispatch(provider, "media_store", "update", """["audio",$insertedId,{"title":"x"}]""")
            assertFalse(rejected.toJson(), rejected.ok)
            assertTrue(rejected.toJson(), rejected.toJson().contains("ERR_AUTOJS6_BRIDGE_INVALID_REQUEST"))

            val exported = okJson(dispatch(provider, "media_store", "exportFile", """["audio",$insertedId,"exported/clip-copy.wav"]"""))
            assertEquals(workDir.resolve("exported/clip-copy.wav").absolutePath, exported.getString("path"))
            assertEquals(workDir.resolve("clip.wav").length(), exported.getLong("sizeBytes"))
            assertTrue(workDir.resolve("exported/clip-copy.wav").readBytes().contentEquals(workDir.resolve("clip.wav").readBytes()))

            val scanned = okJson(dispatch(provider, "media_store", "scanFile", """["clip.wav",{"mimeType":"audio/wav"}]""", timeoutMs = 40_000L))
            assertEquals(NodeMediaStore.SCAN_SCHEMA, scanned.getString("schema"))
            assertEquals(workDir.resolve("clip.wav").absolutePath, scanned.getString("path"))
            assertTrue(scanned.has("scanned"))

            val deleted = okJson(dispatch(provider, "media_store", "delete", """["audio",$insertedId]"""))
            assertTrue(deleted.getBoolean("deleted"))
            insertedId = null
            assertEquals("null", dispatch(provider, "media_store", "get", """["audio",${deleted.getLong("id")}]""").resultJson)
        } finally {
            insertedId?.let { dispatch(provider, "media_store", "delete", """["audio",$it]""") }
            provider.destroy()
            workDir.deleteRecursively()
        }
    }

    @Test
    fun jsMediaPlaybackSessionsAndMediaStoreThroughThePlugin() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node media playback and media_store live bridge regression")
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            android.content.Intent(context, org.autojs.autojs.ui.log.LogActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val workDir = File(context.cacheDir, "node-bridge-media-live").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create media live directory: $absolutePath" }
            resolve("silence.wav").writeBytes(silentPcmWave(seconds = 3))
            resolve("project.json").writeText(
                """
                {
                  "type": "node",
                  "node": {
                    "permissions": ["media", "media.playback", "media.library"]
                  }
                }
                """.trimIndent(),
            )
        }
        try {
            val result = NodePluginTestHarness.runPluginScript(
                """
                "nodejs";
                (async function main() {
                  const media = require("media");
                  const store = require("media_store");
                  const compat = require("autojs6:compat");
                  const session = await media.play("silence.wav", { volume: 0 });
                  const started = await session.status();
                  await session.pause();
                  const paused = await session.status();
                  await session.seekTo(1000);
                  await session.resume();
                  const resumed = await session.status();
                  const stopped = await session.stop();
                  const after = await media.getPlaybackStatus();
                  console.log("media.session=" + [typeof session.id, started.active, started.playing, paused.playing, resumed.playing, resumed.positionMs >= 900, stopped.active, after.active].join("|"));
                  const capabilities = await store.capabilities();
                  const query = await store.query("audio", { filter: { ownedOnly: true }, limit: 5 });
                  console.log("media.store=" + [capabilities.schema, Array.isArray(capabilities.collections), query.schema, Array.isArray(query.items), query.limit].join("|"));
                  let mutate = "allowed";
                  try { await store.delete("audio", 1); } catch (error) { mutate = error.code + "|" + error.missingCapabilities; }
                  console.log("media.mutate=" + mutate);
                  console.log("media.compat=" + [typeof compat.media.playMusic, typeof compat.media.pauseMusic, typeof compat.media.stopMusic, typeof compat.media.isMusicPlaying, typeof compat.media.scanFile].join("|"));
                })().catch(function(error) { console.error(error && error.stack || String(error)); process.exitCode = 1; });
                """.trimIndent(),
                "media-live.cjs",
                workingDirectory = workDir,
                timeoutMs = 90_000L,
            )
            NodePluginTestHarness.assertSucceeded("media playback and media_store through the plugin", result)
            assertTrue(result.stdout, result.stdout.contains("media.session=string|true|true|false|true|true|false|false"))
            assertTrue(result.stdout, result.stdout.contains("media.store=${NodeMediaStore.CAPABILITIES_SCHEMA}|true|${NodeMediaStore.QUERY_SCHEMA}|true|5"))
            assertTrue(result.stdout, result.stdout.contains("media.mutate=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|media.library.mutate"))
            assertTrue(result.stdout, result.stdout.contains("media.compat=function|function|function|function|function"))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            workDir.deleteRecursively()
        }
    }

    private fun silentPcmWave(seconds: Int): ByteArray {
        val sampleRate = 8_000
        val dataSize = sampleRate * 2 * seconds
        val buffer = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataSize).put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2).putShort(16).put("data".toByteArray(Charsets.US_ASCII)).putInt(dataSize)
        return buffer.array()
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        timeoutMs: Long = 5_000L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        val request = NodeBridgeRequest(
            id = "$module-$method-${System.nanoTime()}",
            module = module,
            method = method,
            argsJson = argsJson,
            timeoutMs = timeoutMs,
            permissions = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method),
        )
        provider.dispatch(request) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method response", latch.await(timeoutMs, TimeUnit.MILLISECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
    }

    private fun okJson(response: NodeBridgeResponse): JSONObject {
        assertTrue(response.toJson(), response.ok)
        return jsonObject(response.resultJson.orEmpty())
    }

    private fun jsonObject(raw: String): JSONObject {
        return JSONTokener(raw).nextValue() as JSONObject
    }

    private fun minimalPcmWave(): ByteArray {
        val pcm = byteArrayOf(0, 0, 0, 0)
        return ByteArray(44 + pcm.size).also { bytes ->
            fun putAscii(offset: Int, value: String) {
                value.toByteArray(Charsets.US_ASCII).copyInto(bytes, offset)
            }
            fun putShortLe(offset: Int, value: Int) {
                bytes[offset] = (value and 0xff).toByte()
                bytes[offset + 1] = (value ushr 8 and 0xff).toByte()
            }
            fun putIntLe(offset: Int, value: Int) {
                repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8) and 0xff).toByte() }
            }
            putAscii(0, "RIFF")
            putIntLe(4, 36 + pcm.size)
            putAscii(8, "WAVE")
            putAscii(12, "fmt ")
            putIntLe(16, 16)
            putShortLe(20, 1)
            putShortLe(22, 1)
            putIntLe(24, 8_000)
            putIntLe(28, 16_000)
            putShortLe(32, 2)
            putShortLe(34, 16)
            putAscii(36, "data")
            putIntLe(40, pcm.size)
            pcm.copyInto(bytes, 44)
        }
    }
}

/** The script music notification is the observable lease; it is gone once the execution released playback. */
private object ScriptMusicPlayerProbe {
    fun isIdle(context: android.content.Context): Boolean {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            if (manager.activeNotifications.none { it.id == org.autojs.autojs.core.media.ScriptMusicService.NOTIFICATION_ID }) return true
            Thread.sleep(200)
        }
        return false
    }
}

private object NodeMediaBridgeJsonValue {
    fun string(value: String): String = JSONArray().put(value).toString().let { wrapped ->
        wrapped.substring(1, wrapped.length - 1)
    }
}
