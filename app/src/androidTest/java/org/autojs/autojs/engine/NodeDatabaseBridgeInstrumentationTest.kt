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

class NodeDatabaseBridgeInstrumentationTest {

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
    fun kotlinDatabaseBridgeRegistryAndManifestAreStable() {
        listOf("database", "sqlite").forEach { module ->
            listOf("open", "exec", "run", "get", "all", "transaction", "close").forEach { method ->
                assertTrue(NodeBridgeModules.supports(module, method))
                assertEquals(
                    listOf(NodeBridgePermissionManifest.DATABASE),
                    NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method),
                )
            }
        }
        assertFalse(NodeBridgeModules.supports("database", "loadExtension"))
        assertFalse(NodeBridgeModules.supports("node:sqlite", "open"))
        assertEquals("database", NodeBridgePermissionManifest.normalizeCapability("sqlite"))
    }

    @Test
    fun jsDatabaseBridgeUsesFakeTransportAndBlocksShadowing() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node database bridge fake transport regression")

        val workDir = databaseWorkDir("node-bridge-database-shadow")
        listOf("database", "sqlite").forEach { module ->
            File(workDir, "node_modules/$module").apply {
                check(mkdirs()) { "Failed to create database shadow module directory: $absolutePath" }
            }.resolve("index.js").writeText("""module.exports = { shadow: true, open: () => "shadow" };""")
        }

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
                  let result = null;
                  if (request.method === "open") {
                    result = { id: request.module + "-" + request.args[0].name, name: request.args[0].name };
                  } else if (request.method === "run") {
                    result = { rowsAffected: 1, insertId: 11 };
                  } else if (request.method === "get") {
                    result = { count: 2, name: "alpha" };
                  } else if (request.method === "all") {
                    result = [{ name: "alpha", value: 1 }, { name: "beta", value: 2 }];
                  } else if (request.method === "transaction") {
                    result = request.args[1].map((operation, index) => (
                      operation.type === "run" ? { rowsAffected: 1, insertId: index + 1 } : null
                    ));
                  } else if (request.method === "close") {
                    result = true;
                  }
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  })));
                }
              });

              const database = require("database");
              const sqlite = require("sqlite");
              let nodeSqliteNative = "";
              try {
                nodeSqliteNative = typeof require("node:sqlite").DatabaseSync;
              } catch (error) {
                nodeSqliteNative = String(error && (error.autojs6Code || error.code || ""));
              }
              const db = await database.open("settings", { timeoutMs: 1000 });
              await db.exec("CREATE TABLE items(name TEXT, value INTEGER)", [], { timeoutMs: 1000 });
              const run = await db.run("INSERT INTO items(name,value) VALUES(?,?)", ["alpha", 1], { timeoutMs: 1000 });
              const one = await db.get("SELECT COUNT(*) AS count FROM items", [], { timeoutMs: 1000 });
              const all = await db.all("SELECT name,value FROM items", [], { timeoutMs: 1000 });
              const tx = await db.transaction([
                { type: "exec", sql: "DELETE FROM items" },
                { type: "run", sql: "INSERT INTO items(name,value) VALUES(?,?)", params: ["beta", 2] }
              ], { timeoutMs: 1000 });
              const closed = await db.close({ timeoutMs: 1000 });
              const alias = await sqlite.open({ name: "alias" }, { timeoutMs: 1000 });
              await alias.close({ timeoutMs: 1000 });

              console.log("database.resolve=" + [require.resolve("database"), require.resolve("sqlite")].join("|"));
              console.log("database.shadow=" + [database.shadow === true, sqlite.shadow === true].join("|"));
              console.log("database.node_sqlite=" + nodeSqliteNative);
              console.log("database.frozen=" + [Object.isFrozen(database), Object.isFrozen(sqlite), Object.isFrozen(db)].join("|"));
              console.log("database.methods=" + [typeof database.open, typeof db.exec, typeof db.run, typeof db.transaction, typeof db.close].join("|"));
              console.log("database.value=" + [db.name, run.rowsAffected, one.count, all.length, tx[1].rowsAffected, closed, alias.name].join("|"));
              console.log("database.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("database.permissions=" + requests[0].permissions.join("|"));
              console.log("database.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-database-fake.js>",
            workingDirectory = workDir,
        )

        NodePluginTestHarness.assertSucceeded("database fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.resolve=autojs6:database|autojs6:sqlite")
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.shadow=false|false")
        // Node runtime 1.6.0+ serves node:sqlite as Node's native module next to the bridge-backed database facade.
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.node_sqlite=function")
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.frozen=true|true|true")
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.methods=function|function|function|function|function")
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.value=settings|1|2|2|1|true|alias")
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.requests=database.open|database.exec|database.run|database.get|database.all|database.transaction|database.close|sqlite.open|sqlite.close")
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.permissions=database")
        NodePluginTestHarness.assertStdoutContains("database fake bridge", result, "database.pending=0")
    }

    @Test
    fun jsDatabaseRequiresManifestCapabilityBeforeDispatch() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node database manifest capability regression")

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
                await require("database").open("settings");
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
              }
              console.log("database.manifest.denied=" + [code, missing, requests.length, bridge.__test.pendingCount()].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-database-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("database manifest capability denial", result)
        assertTrue(
            result.stdout.contains(
                "database.manifest.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|database|0|0",
            ),
        )
    }

    @Test
    fun androidDatabaseProviderPersistsTransactionsAndDeniesEscapes() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node database Android provider regression")

        val workDir = databaseWorkDir("node-bridge-database-live")
        val writeResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const database = require("database");
              const db = await database.open("s7_35_live");
              await db.exec("DROP TABLE IF EXISTS items");
              await db.exec("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, value INTEGER)");
              const tx = await db.transaction([
                { type: "run", sql: "INSERT INTO items(name,value) VALUES(?,?)", params: ["alpha", 1] },
                { type: "run", sql: "INSERT INTO items(name,value) VALUES(?,?)", params: ["beta", 2] }
              ]);
              const count = await db.get("SELECT COUNT(*) AS count FROM items");
              const rows = await db.all("SELECT name,value FROM items ORDER BY value");
              await db.close();
              console.log("database.android.write=" + [tx.length, count.count, rows[0].name, rows[1].value].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-database-android-write.js>",
            workingDirectory = workDir,
        )
        NodePluginTestHarness.assertSucceeded("database Android write", writeResult)
        assertTrue(writeResult.stdout.contains("database.android.write=2|2|alpha|2"))
        NodePluginTestHarness.settlePluginRuntime()

        val readResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const db = await require("sqlite").open({ name: "s7_35_live" });
              const row = await db.get("SELECT name,value FROM items WHERE value=?", [2]);
              await db.exec("DELETE FROM items");
              await db.close();
              console.log("database.android.read=" + [row.name, row.value].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-database-android-read.js>",
            workingDirectory = workDir,
        )
        NodePluginTestHarness.assertSucceeded("database Android read", readResult)
        assertTrue(readResult.stdout.contains("database.android.read=beta|2"))
        NodePluginTestHarness.settlePluginRuntime()

        val deniedResult = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const database = require("database");
              async function denied(fn) {
                try {
                  await fn();
                  return "NO_DENY";
                } catch (error) {
                  return String(error && (error.autojs6Code || error.code || ""));
                }
              }
              const path = await denied(() => database.open("../escape"));
              const absolute = await denied(() => database.open("/sdcard/escape"));
              const db = await database.open("s7_35_policy");
              const attach = await denied(() => db.exec("ATTACH DATABASE '../escape.db' AS escape"));
              const extension = await denied(() => db.get("SELECT load_extension(?)", ["x"]));
              await db.close();
              console.log("database.android.denied=" + [path, absolute, attach, extension].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-database-android-denied.js>",
            workingDirectory = workDir,
        )
        NodePluginTestHarness.assertSucceeded("database Android policy denial", deniedResult)
        assertTrue(
            deniedResult.stdout.contains(
                "database.android.denied=${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}",
            ),
        )
    }

    @Test
    fun androidDatabaseProviderRegistersAndClosesResources() {
        val workDir = databaseWorkDir("node-bridge-database-direct")
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "database-direct-provider",
                sourceName = "database-direct.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val open = dispatch(provider, "open", JSONArray().put(JSONObject().put("name", "direct")).toString())
        assertTrue(open.toString(), open.ok)
        val handleId = JSONObject(open.resultJson.orEmpty()).getString("id")
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.database_active_handle_count"])
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])

        val close = dispatch(provider, "close", JSONArray().put(handleId).toString())
        assertTrue(close.toString(), close.ok)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.database_active_handle_count"])
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        provider.destroy()
    }

    private fun databaseWorkDir(name: String): File {
        return File(context.cacheDir, name).apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create database bridge working directory: $absolutePath" }
            resolve("project.json").writeText(
                """
                {
                  "type": "node",
                  "node": {
                    "permissions": ["database"]
                  }
                }
                """.trimIndent(),
            )
        }
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
                id = "database-${System.nanoTime()}",
                module = "database",
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("database"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for database.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing database.$method response" }
    }
}
