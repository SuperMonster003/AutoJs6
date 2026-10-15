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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeStorageBridgeInstrumentationTest {

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
    fun kotlinStorageBridgeRegistryAndManifestAreStable() {
        listOf("storage", "storages").forEach { module ->
            listOf("get", "put", "remove", "clear", "keys", "contains").forEach { method ->
                assertTrue(NodeBridgeModules.supports(module, method))
                assertEquals(
                    listOf(NodeBridgePermissionManifest.STORAGE),
                    NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method),
                )
            }
        }
        assertFalse(NodeBridgeModules.supports("storage", "rawSharedPreferences"))
        assertEquals("storage", NodeBridgePermissionManifest.normalizeCapability("storages"))
    }

    @Test
    fun jsStorageBridgeUsesFakeTransportAndBlocksShadowing() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node storage bridge fake transport regression")

        val workDir = File(context.cacheDir, "node-bridge-storage-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create storage bridge working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted packages also travel as module-source overlays; otherwise the plugin never
        // sees them and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true, create: () => "shadow" };"""
        val shadowModules = listOf("storage", "storages")
        shadowModules.forEach { module ->
            File(workDir, "node_modules/$module").apply {
                check(mkdirs()) { "Failed to create storage shadow module directory: $absolutePath" }
            }.resolve("index.js").writeText(shadowPackageSource)
        }

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              const buckets = Object.create(null);
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const storeName = request.args[0];
                  const key = request.args[1];
                  const bucket = buckets[storeName] || (buckets[storeName] = Object.create(null));
                  let result = null;
                  if (request.method === "get") {
                    result = Object.prototype.hasOwnProperty.call(bucket, key)
                      ? { found: true, value: bucket[key] }
                      : { found: false };
                  } else if (request.method === "put") {
                    bucket[key] = request.args[2];
                  } else if (request.method === "remove") {
                    delete bucket[key];
                  } else if (request.method === "clear") {
                    Object.keys(bucket).forEach((entry) => delete bucket[entry]);
                  } else if (request.method === "keys") {
                    result = Object.keys(bucket).sort();
                  } else if (request.method === "contains") {
                    result = Object.prototype.hasOwnProperty.call(bucket, key);
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  })));
                }
              });

              const storage = require("storage");
              const storages = require("storages");
              const store = storage.create("settings");
              const alias = storages.open("settings");
              await store.clear({ timeoutMs: 1000 });
              await store.put("theme", { mode: "dark", count: 2 }, { timeoutMs: 1000 });
              const value = await alias.get("theme", null, { timeoutMs: 1000 });
              const missing = await store.get("missing", "fallback");
              const containsBefore = await store.contains("theme");
              const keysBefore = await store.keys();
              await store.remove("theme");
              const containsAfter = await alias.contains("theme");

              console.log("storage.resolve=" + [require.resolve("storage"), require.resolve("storages")].join("|"));
              console.log("storage.shadow=" + [storage.shadow === true, storages.shadow === true].join("|"));
              console.log("storage.frozen=" + [Object.isFrozen(storage), Object.isFrozen(storages), Object.isFrozen(store)].join("|"));
              console.log("storage.methods=" + [typeof storage.create, typeof storage.open, typeof store.get, typeof store.put, typeof store.keys].join("|"));
              console.log("storage.value=" + [value.mode, value.count, missing, containsBefore, keysBefore.join(","), containsAfter].join("|"));
              console.log("storage.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("storage.permissions=" + requests[0].permissions.join("|"));
              console.log("storage.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-storage-fake.js>",
            workingDirectory = workDir,
            moduleSources = shadowModules.associate { "node_modules/$it/index.js" to shadowPackageSource },
        )

        NodePluginTestHarness.assertSucceeded("storage fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.resolve=autojs6:storage|autojs6:storages")
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.shadow=false|false")
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.frozen=true|true|true")
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.methods=function|function|function|function|function")
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.value=dark|2|fallback|true|theme|false")
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.requests=storage.clear|storage.put|storages.get|storage.get|storage.contains|storage.keys|storage.remove|storages.contains")
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.permissions=storage")
        NodePluginTestHarness.assertStdoutContains("storage fake bridge", result, "storage.pending=0")
    }

    @Test
    fun jsStorageRequiresManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node storage manifest capability regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(emptyList())
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
                await require("storage").create("settings").get("theme");
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("storage.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-storage-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("storage manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "storage.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|storage|0|0",
            ),
        )
    }

    @Test
    fun androidStorageProviderPersistsAndScopesByWorkingDirectory() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node storage Android provider regression")

        val firstWorkDir = storageWorkDir("node-bridge-storage-android-one")
        val secondWorkDir = storageWorkDir("node-bridge-storage-android-two")

        val writeResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const store = require("storage").create("settings");
              await store.clear();
              await store.put("token", { value: "persist", count: 1 });
              console.log("storage.android.write=done");
            })();
            """.trimIndent(),
            "<embedded-autojs-storage-android-write.js>",
            workingDirectory = firstWorkDir,
        )
        NodePluginTestHarness.assertSucceeded("storage Android write", writeResult)
        assertTrue(writeResult.stdout.contains("storage.android.write=done"))
        NodePluginTestHarness.settlePluginRuntime()

        val readResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const store = require("storage").open("settings");
              const value = await store.get("token", null);
              const contains = await store.contains("token");
              const keys = await store.keys();
              console.log("storage.android.same=" + [value.value, value.count, contains, keys.join(",")].join("|"));
              await store.clear();
            })();
            """.trimIndent(),
            "<embedded-autojs-storage-android-read.js>",
            workingDirectory = firstWorkDir,
        )
        NodePluginTestHarness.assertSucceeded("storage Android read", readResult)
        assertTrue(readResult.stdout.contains("storage.android.same=persist|1|true|token"))
        NodePluginTestHarness.settlePluginRuntime()

        val aliasResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const store = require("storages").open("settings");
              await store.put("alias", "ok");
              console.log("storage.android.alias=" + await store.get("alias", "missing"));
              await store.remove("alias");
            })();
            """.trimIndent(),
            "<embedded-autojs-storage-android-alias.js>",
            workingDirectory = firstWorkDir,
        )
        NodePluginTestHarness.assertSucceeded("storage Android storages alias", aliasResult)
        assertTrue(aliasResult.stdout.contains("storage.android.alias=ok"))
        NodePluginTestHarness.settlePluginRuntime()

        val isolatedResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const store = require("storage").create("settings");
              const value = await store.get("token", "missing");
              console.log("storage.android.other=" + value);
            })();
            """.trimIndent(),
            "<embedded-autojs-storage-android-isolated.js>",
            workingDirectory = secondWorkDir,
        )
        NodePluginTestHarness.assertSucceeded("storage Android namespace isolation", isolatedResult)
        assertTrue(isolatedResult.stdout.contains("storage.android.other=missing"))
    }

    @Test
    fun androidStorageProviderEnforcesQuotaAndPackageIdentityIsolation() {
        val workDir = storageWorkDir("node-bridge-storage-live-package")
        val hostProvider = storageProvider(
            id = "storage-host-provider",
            workDir = workDir,
            packageName = context.packageName,
        )
        val packagedProvider = storageProvider(
            id = "storage-packaged-provider",
            workDir = workDir,
            packageName = "${context.packageName}.packaged.fixture",
        )

        assertOk(dispatch(hostProvider, "clear", JSONArray().put("settings").toString()))
        assertOk(dispatch(packagedProvider, "clear", JSONArray().put("settings").toString()))

        assertOk(dispatch(hostProvider, "put", JSONArray().put("settings").put("shared").put("host").toString()))
        val packagedInitial = dispatch(packagedProvider, "get", JSONArray().put("settings").put("shared").toString())
        assertTrue(packagedInitial.toString(), packagedInitial.ok)
        assertFalse(JSONObject(packagedInitial.resultJson.orEmpty()).getBoolean("found"))

        assertOk(dispatch(packagedProvider, "put", JSONArray().put("settings").put("shared").put("packaged").toString()))
        val hostRead = dispatch(hostProvider, "get", JSONArray().put("settings").put("shared").toString())
        val packagedRead = dispatch(packagedProvider, "get", JSONArray().put("settings").put("shared").toString())
        assertEquals("host", JSONObject(hostRead.resultJson.orEmpty()).getString("value"))
        assertEquals("packaged", JSONObject(packagedRead.resultJson.orEmpty()).getString("value"))

        val chunk = "x".repeat(24 * 1024)
        repeat(10) { index ->
            assertOk(dispatch(hostProvider, "put", JSONArray().put("quota").put("k$index").put(chunk).toString()))
        }
        val overQuota = dispatch(hostProvider, "put", JSONArray().put("quota").put("overflow").put("y".repeat(24 * 1024)).toString())
        assertFalse(overQuota.toString(), overQuota.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, overQuota.error?.code)

        assertOk(dispatch(hostProvider, "clear", JSONArray().put("quota").toString()))
        assertOk(dispatch(hostProvider, "clear", JSONArray().put("settings").toString()))
        assertOk(dispatch(packagedProvider, "clear", JSONArray().put("settings").toString()))
    }

    private fun storageWorkDir(name: String): File {
        return File(context.cacheDir, name).apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create storage provider working directory: $absolutePath" }
            resolve("project.json").writeText(
                """
                {
                  "type": "node",
                  "node": {
                    "permissions": ["storage"]
                  }
                }
                """.trimIndent(),
            )
        }
    }

    private fun storageProvider(id: String, workDir: File, packageName: String): AndroidNodeBridgeCapabilityProvider {
        return AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = id,
                sourceName = "$id.cjs",
                cwd = workDir.absolutePath,
            ).copy(packageName = packageName),
        )
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        method: String,
        argsJson: String,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "storage-${System.nanoTime()}",
                module = "storage",
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("storage"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for storage.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing storage.$method response" }
    }

    private fun assertOk(response: NodeBridgeResponse) {
        assertTrue(response.toString(), response.ok)
    }
}
