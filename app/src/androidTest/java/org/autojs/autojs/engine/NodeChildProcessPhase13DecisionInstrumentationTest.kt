package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeChildProcessPhase13DecisionInstrumentationTest {

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
    fun childProcessProfileCanDisableStableBuiltinWhileKeepingShellSeparate() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("stable child_process request-disable behavior")

        val workDir = File(context.cacheDir, "node-child-process-p13-40-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-40 child_process work dir: $absolutePath" }
        }
        try {
            File(workDir, "bin").apply {
                mkdirs()
                File(this, "tool.sh").writeText("#!/system/bin/sh\necho unused\n", StandardCharsets.UTF_8)
            }
            File(workDir, "node_modules/child_process").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val shellManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("shell"))
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      const bridge = require("autojs6:bridge");
                      const requests = [];
                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          const result = request.args[0].indexOf("bin/tool.sh") >= 0
                            ? { code: 0, stdout: "file-ok\n", stderr: "" }
                            : { code: 0, stdout: "exec-ok\n", stderr: "" };
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result
                          })));
                        }
                      });

                      const profile = require("autojs6:profile");
                      const childProcessProfile = profile.childProcessProfile || {};

                      function detailOf(error) {
                        return [
                          error && error.code,
                          error && error.autojs6Code,
                          error && error.legacyCode
                        ].filter(Boolean).join("/");
                      }
                      function expectDenied(label, action) {
                        try {
                          action();
                        } catch (error) {
                          console.log("p13child." + label + "=" + detailOf(error));
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      console.log("p13child.profile=" + [
                        childProcessProfile.status,
                        childProcessProfile.defaultEnabled,
                        Array.isArray(childProcessProfile.targetProfiles) && childProcessProfile.targetProfiles.join(","),
                        childProcessProfile.module,
                        childProcessProfile.nodePrefix,
                        childProcessProfile.implementation,
                        childProcessProfile.shellBridge,
                        childProcessProfile.androidShellProvider,
                        childProcessProfile.appPrivateBinaryAllowlist,
                        childProcessProfile.executablePolicy,
                        childProcessProfile.cwd,
                        childProcessProfile.env,
                        childProcessProfile.stdin,
                        childProcessProfile.stdout,
                        childProcessProfile.stderr,
                        childProcessProfile.timeout,
                        childProcessProfile.cancellation,
                        childProcessProfile.kill,
                        childProcessProfile.cleanup,
                        childProcessProfile.processHandles,
                        childProcessProfile.fdStreams,
                        childProcessProfile.detached,
                        childProcessProfile.shellOperators,
                        childProcessProfile.packagedBehavior,
                        childProcessProfile.rawProcessHandles,
                        childProcessProfile.rawSignals,
                        Object.isFrozen(childProcessProfile),
                        Object.isFrozen(childProcessProfile.targetProfiles)
                      ].join("|"));
                      console.log("p13child.boundary=" + [
                        profile.highRiskCapabilities.childProcess,
                        profile.featureFlags.child_process.enabled,
                        profile.featureFlags.child_process.defaultEnabled,
                        profile.featureFlags.child_process.projectJsonOverrideAllowed,
                        profile.featureFlags.child_process.packagedApkOverrideAllowed,
                        profile.featureFlags.child_process.releaseForbidden,
                        profile.disabledBuiltins.includes("child_process"),
                        typeof globalThis.child_process,
                        typeof globalThis.ChildProcess,
                        typeof process.spawn
                      ].join("|"));

                      expectDenied("requireBare", () => require("child_process"));
                      expectDenied("requireNodePrefix", () => require("node:child_process"));
                      expectDenied("resolveBare", () => require.resolve("child_process"));
                      expectDenied("resolveNodePrefix", () => require.resolve("node:child_process"));

                      const shell = require("shell");
                      const execResult = await shell.exec("echo scoped", {
                        timeoutMs: 777,
                        cwd: "bin",
                        env: { P13: "40" }
                      });
                      const fileResult = await shell.execFile("bin/tool.sh", ["--arg"], { timeoutMs: 778 });
                      console.log("p13child.shell=" + [
                        typeof shell.exec,
                        typeof shell.execFile,
                        typeof shell.spawn,
                        typeof shell.fork,
                        execResult.code,
                        fileResult.stdout.trim(),
                        requests.length,
                        requests[0].module,
                        requests[0].permissions.join(","),
                        requests[0].args[1].cwd,
                        requests[0].args[1].env.P13,
                        requests[1].args[0],
                        bridge.__test.pendingCount()
                      ].join("|"));
                      console.log("p13child.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                        return filename.replace(/\\/g, "/").includes("/node_modules/child_process/");
                      }));
                      console.log("p13child.ready=true");
                    })().catch((error) => {
                      console.error("p13child.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = "<node-child-process-phase13-p13-40.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                childProcessEnabled = false,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to shellManifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("stable child_process request-disable behavior", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13child.profile=disabled_by_request|true|safe_default|" +
                        "denied|denied|denied|separate_scoped_command_bridge|" +
                        "available_with_shell_capability|not_enforced_by_native_builtin|denied|denied|denied|" +
                        "deferred|denied|denied|required|required_before_enablement|" +
                        "required_before_enablement|required_before_enablement|denied|denied|denied|" +
                        "denied|disabled_by_request|false|false|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13child.boundary=false|false|true|false|false|false|true|undefined|undefined|undefined"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13child.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13child.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13child.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13child.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13child.shell=function|function|undefined|undefined|0|file-ok|2|shell|shell|bin|40|'bin/tool.sh' '--arg'|0"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13child.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("p13child.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun childProcessStableRequestExposesNativeBuiltin() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("stable child_process runtime")

        val workDir = File(context.cacheDir, "node-child-process-opt-in-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create child_process opt-in work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/child_process").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      const profile = require("autojs6:profile");
                      const moduleBuiltin = require("module");
                      const childProcess = require("child_process");
                      const childProcessNode = require("node:child_process");
                      const imported = await import("node:child_process");
                      const output = childProcess.execFileSync("/system/bin/sh", ["-c", "printf child-ok"], {
                        encoding: "utf8"
                      });
                      console.log("p13child.opt.profile=" + [
                        profile.childProcessProfile.status,
                        profile.childProcessProfile.module,
                        profile.childProcessProfile.nodePrefix,
                        profile.highRiskCapabilities.childProcess,
                        profile.featureFlags.child_process.enabled,
                        profile.disabledBuiltins.includes("child_process")
                      ].join("|"));
                      console.log("p13child.opt.require=" + [
                        childProcess === childProcessNode,
                        imported.default === childProcess,
                        typeof childProcess.execFile,
                        typeof childProcess.spawn,
                        require.resolve("child_process"),
                        require.resolve("node:child_process"),
                        moduleBuiltin.isBuiltin("child_process"),
                        moduleBuiltin.isBuiltin("node:child_process"),
                        output
                      ].join("|"));
                      console.log("p13child.opt.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                        return filename.replace(/\\/g, "/").includes("/node_modules/child_process/");
                      }));
                      console.log("p13child.opt.ready=true");
                    })().catch((error) => {
                      console.error("p13child.opt.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = "<node-child-process-phase13-opt-in.mjs>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                childProcessEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("stable child_process runtime", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13child.opt.profile=stable|native_available|native_available|true|true|false",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13child.opt.require=true|true|function|function|child_process|child_process|true|true|child-ok",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13child.opt.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("p13child.opt.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
