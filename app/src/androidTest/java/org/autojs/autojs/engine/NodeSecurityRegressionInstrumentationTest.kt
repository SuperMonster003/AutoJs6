package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class NodeSecurityRegressionInstrumentationTest {

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
    fun dangerousApisAndNetworkOperationsStayDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node security regression corpus")
        assertFalse(BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED)

        val source = """
            "nodejs";
            (async () => {
              function codeOf(error) {
                return String(error && (error.autojs6Code || error.code || error.name || ""));
              }
              function detailOf(error) {
                return [
                  error && error.code,
                  error && error.autojs6Code,
                  error && error.legacyCode
                ].filter(Boolean).join("/");
              }
              function expectDenied(label, action, expectedCode) {
                try {
                  action();
                } catch (error) {
                  const actual = codeOf(error);
                  if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                    throw new Error(label + " expected " + expectedCode + " but got " + detailOf(error));
                  }
                  console.log("security." + label + "=" + detailOf(error));
                  return;
                }
                throw new Error(label + " was unexpectedly allowed");
              }
              async function expectDeniedAsync(label, action, expectedCode) {
                try {
                  await action();
                } catch (error) {
                  const actual = codeOf(error);
                  if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                    throw new Error(label + " expected " + expectedCode + " but got " + detailOf(error));
                  }
                  console.log("security." + label + "=" + detailOf(error));
                  return;
                }
                throw new Error(label + " was unexpectedly allowed");
              }

              expectDenied("process.binding", () => process.binding("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              expectDenied("process.dlopen", () => process.dlopen({}, "native.node"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              expectDenied("process._linkedBinding", () => process._linkedBinding("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              if (typeof process.getBuiltinModule === "function") {
                expectDenied("process.getBuiltinModule", () => process.getBuiltinModule("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              } else {
                console.log("security.process.getBuiltinModule=absent");
              }

              for (const name of ["child_process", "worker_threads", "dgram", "vm", "inspector"]) {
                expectDenied("require." + name, () => require(name), "ERR_AUTOJS6_BUILTIN_DISABLED");
              }

              await expectDeniedAsync("fetch", () => fetch("https://example.invalid/"), "ERR_AUTOJS6_NETWORK_DISABLED");
              expectDenied("http.request", () => require("http").request("http://127.0.0.1/"), "ERR_AUTOJS6_NETWORK_DISABLED");
              expectDenied("https.request", () => require("https").request("https://127.0.0.1/"), "ERR_AUTOJS6_NETWORK_DISABLED");
              expectDenied("net.connect", () => require("net").connect(80, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
              expectDenied("net.listen", () => require("net").createServer().listen(0), "ERR_AUTOJS6_NETWORK_DISABLED");
              expectDenied("tls.connect", () => require("tls").connect(443, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
              await expectDeniedAsync(
                "dns.resolve",
                () => require("dns/promises").resolve4("example.invalid"),
                "ERR_AUTOJS6_NETWORK_DISABLED"
              );

              console.log("security.dangerous_apis.ready=true");
            })().catch((error) => {
              console.error("security.dangerous_apis.error=" + (error && (error.stack || error.message) || error));
              process.exitCode = 1;
            });
        """.trimIndent()

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = source,
            sourceName = "<node-security-dangerous-apis.js>",
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node security dangerous API regression", result)
        assertTrue(result.stdout, result.stdout.contains("security.dangerous_apis.ready=true"))
        listOf(
            "process.binding",
            "process.dlopen",
            "process._linkedBinding",
            "require.child_process",
            "require.worker_threads",
            "require.dgram",
            "require.vm",
            "require.inspector",
            "fetch",
            "http.request",
            "https.request",
            "net.connect",
            "net.listen",
            "tls.connect",
            "dns.resolve",
        ).forEach { label ->
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing security output $label", result),
                result.stdout.contains("security.$label="),
            )
        }
    }

    @Test
    fun scopedFilesystemAndPackageEscapesStayDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node scoped filesystem security corpus")

        val workDir = File(context.cacheDir, "node-security-fs-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create security fs work dir: $absolutePath" }
        }
        val outsideDir = File(context.cacheDir, "node-security-outside-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create security outside dir: $absolutePath" }
        }
        try {
            File(workDir, "safe.txt").writeText("safe", StandardCharsets.UTF_8)
            File(outsideDir, "secret.txt").writeText("secret", StandardCharsets.UTF_8)
            Files.createSymbolicLink(File(workDir, "outside-link").toPath(), outsideDir.toPath())
            File(workDir, "native.node").writeText("native-addon-placeholder", StandardCharsets.UTF_8)
            writePackageEscapeFixtures(workDir)

            val absoluteSecret = File(outsideDir, "secret.txt").absolutePath.replace("\\", "\\\\")
            val source = """
                "nodejs";
                (async () => {
                  const fs = require("fs");
                  const fsp = require("fs/promises");
                  function codeOf(error) {
                    return String(error && (error.autojs6Code || error.code || error.name || ""));
                  }
                  function detailOf(error) {
                    return [
                      error && error.code,
                      error && error.autojs6Code,
                      error && error.legacyCode
                    ].filter(Boolean).join("/");
                  }
                  function expectDenied(label, action, expectedCode, messageToken) {
                    try {
                      action();
                    } catch (error) {
                      const actual = codeOf(error);
                      const message = String(error && error.message || "");
                      if (expectedCode && actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                        throw new Error(label + " expected " + expectedCode + " but got " + detailOf(error));
                      }
                      if (messageToken && !message.includes(messageToken)) {
                        throw new Error(label + " message did not contain " + messageToken + ": " + message);
                      }
                      console.log("security." + label + "=" + detailOf(error));
                      return;
                    }
                    throw new Error(label + " was unexpectedly allowed");
                  }
                  async function expectDeniedAsync(label, action, expectedCode) {
                    try {
                      await action();
                    } catch (error) {
                      const actual = codeOf(error);
                      if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                        throw new Error(label + " expected " + expectedCode + " but got " + detailOf(error));
                      }
                      console.log("security." + label + "=" + detailOf(error));
                      return;
                    }
                    throw new Error(label + " was unexpectedly allowed");
                  }
                  function expectStreamDenied(label, action, expectedCode) {
                    return new Promise((resolve, reject) => {
                      let stream;
                      try {
                        stream = action();
                      } catch (error) {
                        const actual = codeOf(error);
                        if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                          reject(new Error(label + " expected " + expectedCode + " but got " + detailOf(error)));
                          return;
                        }
                        console.log("security." + label + "=" + detailOf(error));
                        resolve();
                        return;
                      }
                      stream.once("error", (error) => {
                        const actual = codeOf(error);
                        if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                          reject(new Error(label + " expected " + expectedCode + " but got " + detailOf(error)));
                          return;
                        }
                        console.log("security." + label + "=" + detailOf(error));
                        resolve();
                      });
                      stream.once("open", () => reject(new Error(label + " was unexpectedly allowed")));
                    });
                  }

                  expectDenied("fs.absolute", () => fs.readFileSync("$absoluteSecret", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.escape", () => fs.readFileSync("../secret.txt", "utf8"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
                  expectDenied("fs.nul", () => fs.readFileSync("bad" + String.fromCharCode(0) + "path", "utf8"), "ERR_AUTOJS6_FS_NUL_BYTE");
                  expectDenied("fs.proc", () => fs.readFileSync("/proc/version", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.sys", () => fs.readFileSync("/sys/kernel", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.dev", () => fs.readFileSync("/dev/null", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.realpath_escape", () => fs.realpathSync("./outside-link/secret.txt"), "ERR_AUTOJS6_FS_REALPATH_ESCAPE");
                  expectDenied("fs.symlink_escape", () => fs.readFileSync("./outside-link/secret.txt", "utf8"), "ERR_AUTOJS6_FS_REALPATH_ESCAPE");
                  await expectDeniedAsync("fsp.escape", () => fsp.readFile("../secret.txt", "utf8"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
                  await expectStreamDenied("fs_stream.read_escape", () => fs.createReadStream("../secret.txt"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
                  await expectStreamDenied("fs_stream.write_escape", () => fs.createWriteStream("../secret.txt"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
                  expectDenied("process.chdir.absolute", () => process.chdir("$absoluteSecret"), "EPERM");
                  expectDenied("process.chdir.escape", () => process.chdir(".."), "EPERM");
                  expectDenied("native_addon", () => require("./native.node"), "ERR_AUTOJS6_NATIVE_ADDON_DISABLED", ".node");
                  expectDenied("package.exports.escape", () => require("bad-exports"), "ERR_INVALID_PACKAGE_TARGET");
                  expectDenied("package.imports.escape", () => require("bad-imports"), "ERR_INVALID_PACKAGE_TARGET");

                  console.log("security.scoped_fs.ready=true");
                })().catch((error) => {
                  console.error("security.scoped_fs.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-security-scoped-fs.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node scoped filesystem security regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing scoped filesystem security ready output", result),
                result.stdout.contains("security.scoped_fs.ready=true"),
            )
            listOf(
                "fs.absolute",
                "fs.escape",
                "fs.nul",
                "fs.proc",
                "fs.sys",
                "fs.dev",
                "fs.realpath_escape",
                "fs.symlink_escape",
                "fsp.escape",
                "fs_stream.read_escape",
                "fs_stream.write_escape",
                "process.chdir.absolute",
                "process.chdir.escape",
                "native_addon",
                "package.exports.escape",
                "package.imports.escape",
            ).forEach { label ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing security output $label", result),
                    result.stdout.contains("security.$label="),
                )
            }
            assertEquals(false, result.pendingScopedFsStreams)
            assertEquals(0, result.pendingScopedFsStreamCount ?: -1)
        } finally {
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun phase5EsmAndDynamicImportEscapesStayDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 5 ESM security corpus")

        val parent = freshWorkDir("node-security-phase5-esm-parent")
        val workDir = File(parent, "project").apply {
            check(mkdirs()) { "Failed to create ESM security project dir: $absolutePath" }
        }
        try {
            File(parent, "outside.mjs").writeText("""export const value = "outside";""", StandardCharsets.UTF_8)

            val staticEscape = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """import { value } from "../outside.mjs"; console.log(value);""",
                sourceName = File(workDir, "static-main.mjs").absolutePath,
                workingDirectory = workDir,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("Phase 5 ESM static import escape", staticEscape), staticEscape.succeeded)
            assertEquals(NodeRuntimeErrorCodes.FS_PATH_ESCAPE, staticEscape.errorCode)

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            val dynamicEscape = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """await import("../outside.mjs");""",
                sourceName = File(workDir, "dynamic-main.mjs").absolutePath,
                workingDirectory = workDir,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("Phase 5 dynamic import escape", dynamicEscape), dynamicEscape.succeeded)
            assertEquals(NodeRuntimeErrorCodes.FS_PATH_ESCAPE, dynamicEscape.errorCode)

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            val dynamicNetwork = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """await import("https://example.invalid/dep.mjs");""",
                sourceName = File(workDir, "dynamic-network-main.mjs").absolutePath,
                workingDirectory = workDir,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("Phase 5 dynamic import network URL", dynamicNetwork), dynamicNetwork.succeeded)
            assertEquals(NodeRuntimeErrorCodes.NETWORK_DISABLED, dynamicNetwork.errorCode)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun phase5BridgeNetworkUiAndShellBoundariesStayDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 5 bridge security corpus")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async () => {
                  const bridge = require("autojs6:bridge");
                  const requests = [];
                  function codeOf(error) {
                    return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                  }
                  function respond(request, result) {
                    setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result
                    })));
                  }

                  bridge.__test.setTransport({
                    postMessage(message) {
                      const request = JSON.parse(message);
                      requests.push(request);
                      if (request.module === "ui" && request.method === "showLayout") {
                        respond(request, { id: "phase5-ui-1" });
                        return;
                      }
                      if (request.module === "ui" && request.method === "close") {
                        respond(request, null);
                        return;
                      }
                      if (request.module === "shell" && request.method === "exec") {
                        respond(request, { code: 0, stdout: "ok\n", stderr: "" });
                        return;
                      }
                      setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                        id: request.id,
                        ok: false,
                        error: {
                          code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                          message: "unexpected phase5 security request",
                          module: request.module,
                          method: request.method
                        }
                      })));
                    }
                  });

                  const fetchSchemeCodes = [];
                  for (const url of ["file:///tmp/a", "content://autojs6/a", "data:text/plain,hi"]) {
                    try {
                      await fetch(url, { timeoutMs: 1000 });
                    } catch (error) {
                      fetchSchemeCodes.push(codeOf(error));
                    }
                  }

                  let websocketSchemeCode = "";
                  try {
                    await require("websocket").connect("https://example.invalid/not-websocket", { timeoutMs: 1000 });
                  } catch (error) {
                    websocketSchemeCode = codeOf(error);
                  }

                  const ui = require("ui");
                  const handle = await ui.showLayout({ type: "Text", id: "title", text: "Security" }, { timeoutMs: 1000 });
                  const uiNoRawView = handle.view === undefined && handle.androidView === undefined && Object.isFrozen(handle);
                  await handle.close({ timeoutMs: 1000 });

                  let childProcessCode = "";
                  try {
                    require("child_process").exec("echo denied");
                  } catch (error) {
                    childProcessCode = codeOf(error);
                  }
                  const shellResult = await require("shell").exec("echo ok", { timeoutMs: 1000 });

                  console.log("security.phase5.fetch_schemes=" + fetchSchemeCodes.join("|"));
                  console.log("security.phase5.websocket_scheme=" + websocketSchemeCode);
                  console.log("security.phase5.ui_cleanup=" + [uiNoRawView, bridge.__test.pendingCount()].join("|"));
                  console.log("security.phase5.shell_child_process=" + [childProcessCode, shellResult.code].join("|"));
                  console.log("security.phase5.bridge_requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
                  console.log("security.phase5.bridge_permissions=" + requests.map((request) => request.permissions.join(",")).join("|"));
                  console.log("security.phase5.bridge.ready=true");
                })().catch((error) => {
                  console.error("security.phase5.bridge.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent(),
            sourceName = "<node-security-phase5-bridge.js>",
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node Phase 5 bridge security regression", result)
        assertTrue(result.stdout.contains("security.phase5.bridge.ready=true"))
        assertTrue(
            result.stdout.contains(
                "security.phase5.fetch_schemes=${NodeRuntimeErrorCodes.NETWORK_INVALID_URL}|${NodeRuntimeErrorCodes.NETWORK_INVALID_URL}|${NodeRuntimeErrorCodes.NETWORK_INVALID_URL}",
            ),
        )
        assertTrue(result.stdout.contains("security.phase5.websocket_scheme=${NodeRuntimeErrorCodes.NETWORK_INVALID_URL}"))
        assertTrue(result.stdout.contains("security.phase5.ui_cleanup=true|0"))
        assertTrue(result.stdout.contains("security.phase5.shell_child_process=${NodeRuntimeErrorCodes.BUILTIN_DISABLED}|0"))
        assertTrue(result.stdout.contains("security.phase5.bridge_requests=ui.showLayout|ui.close|shell.exec"))
        assertTrue(result.stdout.contains("security.phase5.bridge_permissions=ui|ui|shell"))
    }

    @Test
    fun phase5PluginAndWorkerBoundariesStayDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 5 plugin and worker security corpus")

        val workDir = freshWorkDir("node-security-phase5-plugin-worker")
        try {
            writePhase5Plugin(
                workDir = workDir,
                id = "org.example.native",
                source = """module.exports = { ready: true };""",
            )
            File(workDir, "plugins/org.example.native/addon.node").writeText(
                "native-addon-placeholder\n",
                StandardCharsets.UTF_8,
            )
            File(workDir, "restricted-worker.js").writeText(
                """
                    const { parentPort } = require("worker_threads");
                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.name || ""));
                    }
                    function denied(label, action) {
                      try {
                        action();
                        parentPort.postMessage({ label, code: "allowed" });
                      } catch (error) {
                        parentPort.postMessage({ label, code: codeOf(error) });
                      }
                    }
                    denied("toast", () => require("toast"));
                    denied("accessibility", () => require("accessibility"));
                    denied("shell", () => require("shell"));
                    parentPort.postMessage({ label: "done", code: "done" });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("plugin.org.example.native"))
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }
                      let pluginCode = "";
                      try {
                        require("plugins").load("org.example.native");
                        pluginCode = "allowed";
                      } catch (error) {
                        pluginCode = [codeOf(error), error && error.autojs6Code].join("|");
                      }
                      console.log("security.phase5.plugin_native=" + pluginCode);

                      try {
                        const { Worker } = require("worker_threads");
                        const worker = new Worker("./restricted-worker.js");
                        const messages = await new Promise((resolve, reject) => {
                          const values = [];
                          worker.on("message", (message) => {
                            values.push(message);
                            if (message && message.label === "done") {
                              resolve(values);
                            }
                          });
                          worker.once("error", reject);
                        });
                        const exitCode = await new Promise((resolve) => worker.once("exit", resolve));
                        const codes = Object.create(null);
                        for (const message of messages) {
                          codes[message.label] = message.code;
                        }
                        console.log("security.phase5.worker_bridge=" + [codes.toast, codes.accessibility, codes.shell, exitCode].join("|"));
                      } catch (error) {
                        console.log("security.phase5.worker_bridge_unavailable=" + codeOf(error));
                      }

                      console.log("security.phase5.plugin_worker.ready=true");
                    })().catch((error) => {
                      console.error("security.phase5.plugin_worker.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 15_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node Phase 5 plugin and worker security regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing Phase 5 plugin/worker ready marker", result),
                result.stdout.contains("security.phase5.plugin_worker.ready=true"),
            )
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing Phase 5 plugin native denial", result),
                result.stdout.contains(
                    "security.phase5.plugin_native=${NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED}|${NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED}",
                ),
            )
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing Phase 5 worker bridge denial", result),
                result.stdout.contains(
                    "security.phase5.worker_bridge=${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|0",
                ) || result.stdout.contains("security.phase5.worker_bridge_unavailable=${NodeRuntimeErrorCodes.WORKER_DISABLED}"),
            )
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun phase5ZlibStreamErrorsCleanUpResources() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 5 zlib stream cleanup security corpus")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async () => {
                  const stream = require("stream");
                  const { pipeline } = require("stream/promises");
                  const zlib = require("zlib");
                  let code = "";
                  try {
                    await pipeline(
                      stream.Readable.from([Buffer.from("not gzip")]),
                      zlib.createGunzip(),
                      new stream.Writable({
                        write(chunk, encoding, callback) {
                          callback();
                        }
                      })
                    );
                    code = "allowed";
                  } catch (error) {
                    code = error instanceof Error ? "error" : "other";
                  }
                  console.log("security.phase5.zlib_cleanup=" + code);
                })().catch((error) => {
                  console.error("security.phase5.zlib_cleanup.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent(),
            sourceName = "<node-security-phase5-zlib-cleanup.js>",
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node Phase 5 zlib stream cleanup regression", result)
        assertTrue(result.stdout.contains("security.phase5.zlib_cleanup=error"))
        assertEquals(false, result.pendingZlibStreams)
        assertEquals(0, result.pendingZlibStreamCount ?: -1)
    }

    private fun freshWorkDir(name: String): File =
        File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work dir: $absolutePath" }
        }

    private fun writePhase5Plugin(
        workDir: File,
        id: String,
        source: String,
    ) {
        val pluginDir = File(workDir, "plugins/$id").apply {
            check(mkdirs()) { "Failed to create plugin dir: $absolutePath" }
        }
        File(pluginDir, "package.json").writeText(
            """
            {
              "name": "$id",
              "version": "1.0.0",
              "main": "index.cjs",
              "autojs6": {
                "plugin": {
                  "id": "$id",
                  "permissions": []
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        File(pluginDir, "index.cjs").writeText(source, StandardCharsets.UTF_8)
    }

    private fun writePackageEscapeFixtures(workDir: File) {
        File(workDir, "node_modules/bad-exports").apply {
            mkdirs()
            File(this, "package.json").writeText(
                """{"name":"bad-exports","exports":{"." : "../outside.js"}}""",
                StandardCharsets.UTF_8,
            )
        }
        File(workDir, "node_modules/bad-imports").apply {
            mkdirs()
            File(this, "package.json").writeText(
                """{"name":"bad-imports","main":"index.js","imports":{"#escape":"../outside.js"}}""",
                StandardCharsets.UTF_8,
            )
            File(this, "index.js").writeText(
                """module.exports = require("#escape");""" + "\n",
                StandardCharsets.UTF_8,
            )
        }
    }
}
