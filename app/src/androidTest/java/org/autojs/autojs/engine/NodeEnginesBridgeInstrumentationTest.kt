package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeEnginesBridgeInstrumentationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        settlePluginRuntime()
    }

    @Test
    fun kotlinEnginesBridgeRegistryIncludesControlledExecMethods() {
        assertTrue(NodeBridgeModules.supports("engines", "myEngine"))
        assertTrue(NodeBridgeModules.supports("engines", "execScript"))
        assertTrue(NodeBridgeModules.supports("engines", "execScriptFile"))
        assertFalse(NodeBridgeModules.supports("engines", "execAutoFile"))
    }

    @Test
    fun jsEnginesExecUsesFakeTransportAndRejectsInvalidInputs() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node engines bridge fake transport")

        val workDir = File(context.cacheDir, "node-bridge-engines-exec-fake").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create engines exec fake working directory: $absolutePath" }
        }
        File(workDir, "sub").apply {
            check(mkdirs()) { "Failed to create engines exec fake sub directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true };"""
        File(workDir, "node_modules/engines").apply {
            check(mkdirs()) { "Failed to create engines shadow module directory: $absolutePath" }
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
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result: {
                        id: request.method === "execScript" ? 41 : 42,
                        engineName: "rhino",
                        sourceName: request.args[0],
                        workingDirectory: "/fake/work",
                        spawnDepth: 1
                      }
                    }));
                  });
                }
              });

              const engines = require("engines");
              const resolved = require.resolve("engines");
              const scriptExec = await engines.execScript(
                "child-rhino",
                "\"rhino\";\nvar ok = true;",
                { timeoutMs: 1234, cwd: "sub", arguments: { token: "ok" } }
              );
              const fileExec = await engines.execScriptFile("child.js", { timeoutMs: 1235 });

              let missingTimeoutCode = "";
              try {
                await engines.execScript("missing-timeout", "var x = 1;");
              } catch (e) {
                missingTimeoutCode = e.autojs6Code || e.code || e.name;
              }

              let cwdEscapeCode = "";
              try {
                await engines.execScript("cwd-escape", "var x = 1;", { timeoutMs: 1000, cwd: "../escape" });
              } catch (e) {
                cwdEscapeCode = e.autojs6Code || e.code || e.name;
              }

              let pathEscapeCode = "";
              try {
                await engines.execScriptFile("../child.js", { timeoutMs: 1000 });
              } catch (e) {
                pathEscapeCode = e.autojs6Code || e.code || e.name;
              }

              console.log("engines.exec.resolve=" + resolved);
              console.log("engines.exec.shadow=" + (engines.shadow === true));
              console.log("engines.exec.methods=" + [
                typeof engines.execScript,
                typeof engines.execScriptFile,
                typeof engines.execAutoFile
              ].join("|"));
              console.log("engines.exec.script=" + scriptExec.id + "|" + scriptExec.sourceName + "|" + scriptExec.spawnDepth);
              console.log("engines.exec.file=" + fileExec.id + "|" + fileExec.sourceName + "|" + fileExec.spawnDepth);
              console.log("engines.exec.requests=" + requests.map(r => r.method).join("|"));
              console.log("engines.exec.script.args=" + requests[0].args[0] + "|" + requests[0].args[2].cwd + "|" + requests[0].args[2].arguments.token);
              console.log("engines.exec.file.args=" + requests[1].args[0] + "|" + requests[1].args[1].cwd);
              console.log("engines.exec.timeouts=" + requests.map(r => r.args[r.method === "execScript" ? 2 : 1].timeoutMs + "/" + r.timeoutMs).join("|"));
              console.log("engines.exec.permissions=" + requests.map(r => r.permissions.join(",")).join("|"));
              console.log("engines.exec.missingTimeout=" + missingTimeoutCode);
              console.log("engines.exec.cwdEscape=" + cwdEscapeCode);
              console.log("engines.exec.pathEscape=" + pathEscapeCode);
              console.log("engines.exec.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-engines-exec-fake.js>",
            workingDirectory = workDir,
            moduleSources = mapOf("node_modules/engines/index.js" to shadowPackageSource),
            // The Node runtime plugin ships every bare script with an enforced bridge-permission manifest that declares
            // only "network" (runtime:stable-network), so the facade rejects any other capability with
            // ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED before the request reaches the (fake) transport; declare what
            // the script uses, exactly as a project.json would.
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("engines", "engines.exec")).toJson()),
        )

        NodePluginTestHarness.assertSucceeded("engines exec fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.resolve=autojs6:engines")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.shadow=false")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.methods=function|function|undefined")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.script=41|child-rhino|1")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.file=42|child.js|1")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.requests=execScript|execScriptFile")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.script.args=child-rhino|sub|ok")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.file.args=child.js|.")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.timeouts=1234/2234|1235/2235")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.permissions=engines,engines:exec|engines,engines:exec")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.missingTimeout=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.cwdEscape=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.pathEscape=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("engines exec fake bridge", result, "engines.exec.pending=0")
    }

    @Test
    fun jsEnginesExecRejectsRecursiveSpawnAtDepthOne() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node engines recursive spawn denial")

        val result = NodePluginTestHarness.runPluginScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const engines = require("engines");
                      const mine = engines.myEngine();
                      let code = "";
                      try {
                        await engines.execScript("recursive", "var x = 1;", { timeoutMs: 1000 });
                      } catch (e) {
                        code = e.autojs6Code || e.code || e.name;
                      }
                      console.log("engines.recursive.depth=" + mine.spawnDepth);
                      console.log("engines.recursive.code=" + code);
                    })();
                """.trimIndent(),
                sourceName = "<node-plugin-autojs-engines-recursive.js>",
                workingDirectory = context.cacheDir,
                timeoutMs = 5000L,
                spawnDepth = 1,
        )

        NodePluginTestHarness.assertSucceeded("engines recursive spawn denial", result)
        assertTrue(result.stdout.contains("engines.recursive.depth=1"))
        assertTrue(result.stdout.contains("engines.recursive.code=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.nativeValues["embedded_script.bridge_live_dispatch_count"].orEmpty() != "1")
    }

    @Test
    fun androidEnginesProviderStartsScopedRhinoScriptsAndDeniesNodeChild() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node engines live exec bridge")

        val workDir = File(context.cacheDir, "node-bridge-engines-exec-live").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create engines exec live working directory: $absolutePath" }
        }
        File(workDir, "child.js").writeText(
            """
            "rhino";
            var autojs6Task35Child = 1 + 1;
            """.trimIndent(),
        )

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const engines = require("engines");
              const scriptExec = await engines.execScript(
                "task35-rhino-child",
                "\"rhino\";\nvar task35RhinoChild = true;",
                { timeoutMs: 3000 }
              );
              const fileExec = await engines.execScriptFile("child.js", { timeoutMs: 3000 });

              let nodeDenied = "";
              try {
                await engines.execScript("task35-node-child", "\"nodejs\";\nconsole.log('nested node');", { timeoutMs: 3000 });
              } catch (e) {
                nodeDenied = e.autojs6Code || e.code || e.name;
              }

              let fileEscape = "";
              try {
                await engines.execScriptFile("../child.js", { timeoutMs: 3000 });
              } catch (e) {
                fileEscape = e.autojs6Code || e.code || e.name;
              }

              console.log("engines.live.script.idType=" + typeof scriptExec.id);
              console.log("engines.live.script.source=" + scriptExec.sourceName);
              console.log("engines.live.script.depth=" + scriptExec.spawnDepth);
              console.log("engines.live.file.source=" + fileExec.sourceName);
              console.log("engines.live.file.depth=" + fileExec.spawnDepth);
              console.log("engines.live.nodeDenied=" + nodeDenied);
              console.log("engines.live.fileEscape=" + fileEscape);
            })();
            """.trimIndent(),
            "<embedded-autojs-engines-exec-live.js>",
            workingDirectory = workDir,
            timeoutMs = 10000L,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                .fromDeclaredCapabilities(listOf("engines", "engines.exec")).toJson()),
        )

        NodePluginTestHarness.assertSucceeded("engines live exec bridge", result)
        assertTrue(result.stdout.contains("engines.live.script.idType=number"))
        assertTrue(result.stdout.contains("engines.live.script.source=task35-rhino-child"))
        assertTrue(result.stdout.contains("engines.live.script.depth=1"))
        assertTrue(result.stdout.contains("engines.live.file.source=child"))
        assertTrue(result.stdout.contains("engines.live.file.depth=1"))
        assertTrue(result.stdout.contains("engines.live.nodeDenied=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("engines.live.fileEscape=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
    }

    private fun settlePluginRuntime() {
        NodePluginTestHarness.settlePluginRuntime()
        context.stopService(Intent(context, NodeBridgeEngineDispatchService::class.java))
    }
}
