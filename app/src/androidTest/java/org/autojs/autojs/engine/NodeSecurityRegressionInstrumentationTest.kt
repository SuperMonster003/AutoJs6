package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
                const scopedFs = require("fs");
                if (process.getBuiltinModule("fs") !== scopedFs) {
                  throw new Error("process.getBuiltinModule did not return scoped fs wrapper");
                }
                expectDenied("process.getBuiltinModule.dgram", () => process.getBuiltinModule("dgram"), "ERR_AUTOJS6_BUILTIN_DISABLED");
              } else {
                console.log("security.process.getBuiltinModule=absent");
              }

              for (const name of ["child_process", "worker_threads", "dgram", "inspector"]) {
                expectDenied("require." + name, () => require(name), "ERR_AUTOJS6_BUILTIN_DISABLED");
              }
              const vm = require("vm");
              console.log("security.require.vm=" + [
                typeof vm.Script,
                require.resolve("node:vm")
              ].join("/"));

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
    fun phase7CapabilityBypassDeniesBeforeProviderDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 7 bridge capability security corpus")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(emptyList())
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                (async () => {
                  const bridge = require("autojs6:bridge");
                  const requests = [];
                  function codeOf(error) {
                    return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                  }
                  function missingOf(error) {
                    return Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
                  }
                  async function denied(label, action, missing) {
                    try {
                      await action();
                      return label + ":allowed";
                    } catch (error) {
                      return label + ":" + codeOf(error) + ":" + missingOf(error) + ":" + (missingOf(error) === missing);
                    }
                  }

                  bridge.__test.setTransport({
                    postMessage(message) {
                      const request = JSON.parse(message);
                      requests.push(request);
                      setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                        id: request.id,
                        ok: false,
                        error: {
                          code: "UNEXPECTED_DISPATCH",
                          message: "capability bypass reached provider"
                        }
                      })));
                    }
                  });

                  const checks = [];
                  checks.push(await denied("storage", () => require("storage").create("phase7").get("key", null, { timeoutMs: 1000 }), "storage"));
                  checks.push(await denied("notifications", () => require("notifications").notify({ id: 7401, title: "Denied", text: "Denied" }, { timeoutMs: 1000 }), "notifications"));
                  checks.push(await denied("ui", () => require("ui").showLayout({ type: "Text", id: "title", text: "Denied" }, { timeoutMs: 1000 }), "ui"));
                  checks.push(await denied("work_manager", () => require("work_manager").scheduleOnce({ scriptPath: "main.cjs", timeoutMs: 1000 }), "work_manager"));
                  checks.push(await denied("database", () => require("database").open("phase7", { timeoutMs: 1000 }), "database"));

                  console.log("security.phase7.capability_bypass=" + checks.join("|"));
                  console.log("security.phase7.capability_requests=" + requests.length);
                })().catch((error) => {
                  console.error("security.phase7.capability_bypass.error=" + (error && (error.stack || error.message) || error));
                  process.exitCode = 1;
                });
            """.trimIndent(),
            sourceName = "<node-security-phase7-capability-bypass.js>",
            timeoutMs = 10_000L,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node Phase 7 capability bypass security regression", result)
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "security.phase7.capability_bypass=" +
                        "storage:${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}:storage:true|" +
                        "notifications:${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}:notifications:true|" +
                        "ui:${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}:ui:true|" +
                        "work_manager:${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}:work_manager:true|" +
                        "database:${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}:database:true",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("security.phase7.capability_requests=0"))
    }

    @Test
    fun phase7ProviderPolicyAndPluginEscapesStayDeniedBeforeDispatch() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 7 provider policy security corpus")

        val workDir = freshWorkDir("node-security-phase7-provider-policy")
        try {
            writePhase5Plugin(
                workDir = workDir,
                id = "org.example.phase7escape",
                source = """module.exports = { escaped: true };""",
                entry = "../phase7-escape.cjs",
            )
            writePhase5Plugin(
                workDir = workDir,
                id = "org.example.phase7cap",
                source = """
                    globalThis.__autojs6Phase7CapPluginExecuted = true;
                    module.exports = { ready: true };
                """.trimIndent(),
                permissions = listOf("storage"),
            )
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
                listOf(
                    "database",
                    "work_manager",
                    "plugin.org.example.phase7escape",
                    "plugin.org.example.phase7cap",
                ),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      const bridge = require("autojs6:bridge");
                      const requests = [];
                      const codes = [];
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }
                      function detail(label, error) {
                        const missing = Array.isArray(error && error.missingCapabilities) ? error.missingCapabilities.join(",") : "";
                        codes.push(label + ":" + [error && error.code, error && error.autojs6Code, error && error.module, error && error.method, missing].join(","));
                      }
                      async function denied(label, action) {
                        try {
                          await action();
                          codes.push(label + ":allowed");
                        } catch (error) {
                          detail(label, error);
                        }
                      }
                      function deniedSync(label, action) {
                        try {
                          action();
                          codes.push(label + ":allowed");
                        } catch (error) {
                          detail(label, error);
                        }
                      }
                      function respond(request, ok, result, error) {
                        setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({ id: request.id, ok, result, error })));
                      }

                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          if (request.module === "database" && request.method === "open") {
                            respond(request, true, { id: "phase7-db-1", name: request.args[0].name });
                            return;
                          }
                          respond(request, false, null, {
                            code: "UNEXPECTED_DISPATCH",
                            message: "policy escape reached provider",
                            module: request.module,
                            method: request.method
                          });
                        }
                      });

                      const database = require("database");
                      const db = await database.open("phase7", { timeoutMs: 1000 });
                      const beforeDatabaseInvalid = requests.length;
                      await denied("database.attach", () => db.exec("ATTACH DATABASE '../escape.db' AS escape", [], { timeoutMs: 1000 }));
                      await denied("database.load_extension", () => db.get("SELECT load_extension(?)", ["x"], { timeoutMs: 1000 }));
                      await denied("database.transaction_detach", () => db.transaction([{ method: "exec", sql: "DETACH DATABASE main" }], { timeoutMs: 1000 }));
                      const databaseNoDispatch = requests.length === beforeDatabaseInvalid;

                      const work = require("work_manager");
                      const beforeWorkInvalid = requests.length;
                      await denied("work.relative_escape", () => work.scheduleOnce({ scriptPath: "../escape.cjs" }));
                      await denied("work.absolute", () => work.scheduleOnce({ scriptPath: "/sdcard/escape.cjs" }));
                      await denied("work.nul", () => work.scheduleOnce({ scriptPath: "bad" + String.fromCharCode(0) + "path.cjs" }));
                      await denied("work.packaged_entry_escape", () => work.scheduleOnce({ entry: "../escape.cjs", packaged: true }));
                      const workNoDispatch = requests.length === beforeWorkInvalid;

                      const plugins = require("plugins");
                      deniedSync("plugin.path_escape", () => plugins.load("org.example.phase7escape"));
                      deniedSync("plugin.capability_escape", () => plugins.load("org.example.phase7cap"));

                      console.log("security.phase7.provider_policy.codes=" + codes.join("|"));
                      console.log("security.phase7.provider_policy.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
                      console.log("security.phase7.provider_policy.no_dispatch=" + [databaseNoDispatch, workNoDispatch, bridge.__test.pendingCount()].join("|"));
                      console.log("security.phase7.provider_policy.plugin_executed=" + (globalThis.__autojs6Phase7CapPluginExecuted === true));
                    })().catch((error) => {
                      console.error("security.phase7.provider_policy.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node Phase 7 provider policy security regression", result)
            listOf(
                "database.attach:${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},database,exec,",
                "database.load_extension:${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},database,get,",
                "database.transaction_detach:${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},database,transaction,",
                "work.relative_escape:${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},work_manager,scheduleOnce,",
                "work.absolute:${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},work_manager,scheduleOnce,",
                "work.nul:${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},work_manager,scheduleOnce,",
                "work.packaged_entry_escape:${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED},work_manager,scheduleOnce,",
                "plugin.path_escape:EPERM,${NodeRuntimeErrorCodes.FS_PATH_ESCAPE},plugins,load,",
                "plugin.capability_escape:${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED},${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED},plugins,load,storage",
            ).forEach { marker ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing Phase 7 provider policy marker $marker", result),
                    result.stdout.contains(marker),
                )
            }
            assertTrue(result.stdout, result.stdout.contains("security.phase7.provider_policy.requests=database.open"))
            assertTrue(result.stdout, result.stdout.contains("security.phase7.provider_policy.no_dispatch=true|true|0"))
            assertTrue(result.stdout, result.stdout.contains("security.phase7.provider_policy.plugin_executed=false"))
        } finally {
            workDir.deleteRecursively()
        }
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
    fun phase7LongRunningResourceExhaustionTimesOutWithDiagnostics() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 7 long-running timeout security corpus")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                setInterval(() => {}, 25);
                console.log("security.phase7.long_running.started=true");
            """.trimIndent(),
            sourceName = "<node-security-phase7-long-running-timeout.js>",
            timeoutMs = 700L,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("Node Phase 7 long-running resource exhaustion", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.LEGACY_SCRIPT_TIMEOUT, result.errorCode)
        assertTrue(NodeEmbeddedTestHarness.describeResult("Node Phase 7 long-running timeout marker", result), result.timedOut)
        assertEquals(true, result.pendingTimers)
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

    @Test
    fun phase8Node2417SecurityFixProbesStaySafe() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node 24.17 security fix probe corpus")

        val workDir = freshWorkDir("node-security-phase8-2417")
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      const assert = require("assert/strict");
                      const crypto = require("crypto");
                      const fsp = require("fs/promises");

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }
                      function detailOf(error) {
                        return [
                          error && error.name,
                          error && error.code,
                          error && error.autojs6Code,
                          error && error.legacyCode
                        ].filter(Boolean).join(":");
                      }
                      function detailContains(result, code) {
                        return String(result.code || "") === code || String(result.detail || "").includes(code);
                      }
                      function isNetworkDisabled(result) {
                        return detailContains(result, "ERR_AUTOJS6_NETWORK_DISABLED") ||
                          detailContains(result, "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED");
                      }
                      function captureSync(label, action) {
                        try {
                          action();
                        } catch (error) {
                          const message = String(error && error.message || "");
                          const detail = detailOf(error);
                          console.log("security.phase8." + label + "=" + detail + "|leaked=" + /user:pass|secret/.test(message));
                          return { code: codeOf(error), detail, message };
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }
                      async function captureAsync(label, action) {
                        try {
                          await action();
                        } catch (error) {
                          const message = String(error && error.message || "");
                          const detail = detailOf(error);
                          console.log("security.phase8." + label + "=" + detail + "|leaked=" + /user:pass|secret/.test(message));
                          return { code: codeOf(error), detail, message };
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      const nulHostname = captureSync("nul_hostname", () => new URL("https://exa" + String.fromCharCode(0) + "mple.test/"));
                      assert.strictEqual(nulHostname.code, "ERR_INVALID_URL");

                      const httpsCredentials = captureSync(
                        "https_credentials_redacted",
                        () => require("https").request("https://user:pass@example.invalid/secret")
                      );
                      assert.ok(isNetworkDisabled(httpsCredentials), httpsCredentials.detail);
                      assert.strictEqual(/user:pass|secret/.test(httpsCredentials.message), false);

                      const tlsDenied = captureSync("tls_hostname_policy", () => require("tls").connect(443, "Example.COM"));
                      assert.ok(isNetworkDisabled(tlsDenied), tlsDenied.detail);

                      const http2Denied = captureSync("http2_origin_state", () => require("http2"));
                      assert.ok(
                        http2Denied.code === "ERR_AUTOJS6_BUILTIN_DISABLED" ||
                        http2Denied.code === "MODULE_NOT_FOUND" ||
                        http2Denied.code === "ERR_AUTOJS6_MODULE_NOT_FOUND"
                      );

                      const webcryptoQuota = captureSync(
                        "webcrypto_quota",
                        () => crypto.webcrypto.getRandomValues(new Uint8Array(65537))
                      );
                      assert.ok(
                        webcryptoQuota.code === "QuotaExceededError" ||
                        webcryptoQuota.code === 22 ||
                        detailContains(webcryptoQuota, "QuotaExceededError"),
                        webcryptoQuota.detail
                      );

                      const fileHandleEscape = await captureAsync("filehandle_escape", () => fsp.open("../escape.txt", "r"));
                      assert.strictEqual(fileHandleEscape.code, "ERR_AUTOJS6_FS_PATH_ESCAPE");

                      console.log("security.phase8.node2417_fix_probes.ready=true");
                    })().catch((error) => {
                      console.error("security.phase8.node2417_fix_probes.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = File(workDir, "phase8-node2417-security.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node 24.17 security fix probes", result)
            assertTrue(result.stdout, result.stdout.contains("security.phase8.node2417_fix_probes.ready=true"))
            listOf(
                "nul_hostname",
                "https_credentials_redacted",
                "tls_hostname_policy",
                "http2_origin_state",
                "webcrypto_quota",
                "filehandle_escape",
            ).forEach { label ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing Phase 8 security probe $label", result),
                    result.stdout.contains("security.phase8.$label="),
                )
            }
            assertTrue(result.stdout, result.stdout.contains("security.phase8.https_credentials_redacted="))
            assertTrue(result.stdout, result.stdout.contains("ERR_AUTOJS6_NETWORK_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("security.phase8.https_credentials_redacted=") && result.stdout.contains("|leaked=false"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun phase13RelaxedCapabilityCorpusCoversNetworkFsChildNativeInspectorAndWasi() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Phase 13 relaxed capability security corpus")
        assertFalse(BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED)
        assertFalse(BuildConfig.NODEJS_RAW_NETWORK_MODULES_EXPERIMENTAL_ENABLED)

        val workDir = freshWorkDir("node-security-p13-50-relaxed-corpus")
        val outsideDir = freshWorkDir("node-security-p13-50-outside")
        try {
            File(workDir, "safe.txt").writeText("safe", StandardCharsets.UTF_8)
            File(outsideDir, "secret.txt").writeText("secret", StandardCharsets.UTF_8)
            Files.createSymbolicLink(File(workDir, "outside-link").toPath(), outsideDir.toPath())
            File(workDir, "native.node").writeText("native-addon-placeholder\n", StandardCharsets.UTF_8)
            listOf("child_process", "inspector", "wasi").forEach { name ->
                File(workDir, "node_modules/$name").apply {
                    mkdirs()
                    File(this, "index.js").writeText(
                        """module.exports = { shadowed: "$name" };""" + "\n",
                        StandardCharsets.UTF_8,
                    )
                }
            }

            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network", "shell"))
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      const bridge = require("autojs6:bridge");
                      const fs = require("fs");
                      const fsp = require("fs/promises");
                      const profile = require("autojs6:profile");
                      const requests = [];

                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          if (request.module === "fetch") {
                            const payload = request.args[0];
                            setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                              id: request.id,
                              ok: true,
                              result: {
                                url: payload.url,
                                status: 200,
                                statusText: "OK",
                                headers: [["content-type", "text/plain"]],
                                bodyBase64: Buffer.from("fetch-ok").toString("base64")
                              }
                            })));
                            return;
                          }
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: { code: 0, stdout: "shell-ok\n", stderr: "" }
                          })));
                        }
                      });

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
                          const detail = detailOf(error);
                          if (expectedCode && detail.indexOf(expectedCode) < 0) {
                            throw new Error(label + " expected " + expectedCode + " but got " + detail + ": " + (error && error.message));
                          }
                          console.log("security.p13_50." + label + "=" + detail);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }
                      async function expectDeniedAsync(label, action, expectedCode) {
                        try {
                          await action();
                        } catch (error) {
                          const detail = detailOf(error);
                          if (expectedCode && detail.indexOf(expectedCode) < 0) {
                            throw new Error(label + " expected " + expectedCode + " but got " + detail + ": " + (error && error.message));
                          }
                          console.log("security.p13_50." + label + "=" + detail);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }
                      function shadowLoaded(name) {
                        return Object.keys(require.cache).some((filename) => {
                          return filename.replace(/\\/g, "/").includes("/node_modules/" + name + "/");
                        });
                      }

                      const selection = profile.profileSelection || {};
                      const networkRisk = ((selection.highRiskCapabilities || []).find((item) => item.id === "network")) || {};
                      console.log("security.p13_50.raw_network_positive=" + [
                        networkRisk.requiredProfile,
                        (selection.declaredAndroidPermissions || []).includes("android.permission.INTERNET"),
                        profile.highRiskCapabilities.network,
                        profile.featureFlags.network.enabled,
                        profile.featureFlags.network.defaultEnabled
                      ].join("|"));
                      const fetchResponse = await fetch("https://example.invalid/p13-50");
                      const fetchBody = await fetchResponse.text();
                      const fetchRequests = requests.filter((request) => request.module === "fetch");
                      console.log("security.p13_50.raw_network_controlled_fetch=" + [
                        fetchResponse.status,
                        fetchBody,
                        fetchRequests.length,
                        fetchRequests[0].permissions.join(",")
                      ].join("|"));
                      expectDenied("raw_network.net_connect", () => require("net").connect(80, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectDenied("raw_network.net_listen", () => require("net").createServer().listen(0), "ERR_AUTOJS6_NETWORK_DISABLED");
                      await expectDeniedAsync("raw_network.dns_resolve", () => require("dns/promises").resolve4("example.invalid"), "ERR_AUTOJS6_NETWORK_DISABLED");

                      await fsp.writeFile("safe-copy.txt", "safe-copy", "utf8");
                      console.log("security.p13_50.fs_positive=" + [
                        fs.readFileSync("safe.txt", "utf8"),
                        fs.readFileSync("safe-copy.txt", "utf8"),
                        profile.filesystemProfile.mode,
                        profile.filesystemProfile.safeProfileScoped,
                        profile.filesystemProfile.additionalRoots.length,
                        profile.filesystemProfile.userAuthorizedRoots,
                        profile.filesystemProfile.advancedApis.rawFd
                      ].join("|"));
                      expectDenied("fs_escape", () => fs.readFileSync("../secret.txt", "utf8"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
                      expectDenied("fs_proc", () => fs.readFileSync("/proc/version", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                      expectDenied("fs_symlink", () => fs.readFileSync("./outside-link/secret.txt", "utf8"), "ERR_AUTOJS6_FS_REALPATH_ESCAPE");

                      const shell = require("shell");
                      const shellResult = await shell.exec("echo p13-50", { timeoutMs: 778, cwd: "." });
                      const shellRequests = requests.filter((request) => request.module === "shell");
                      console.log("security.p13_50.child_positive=" + [
                        shellResult.code,
                        shellResult.stdout.trim(),
                        shellRequests.length,
                        shellRequests[0].module,
                        shellRequests[0].permissions.join(","),
                        typeof shell.exec,
                        typeof shell.spawn,
                        typeof shell.fork
                      ].join("|"));
                      expectDenied("child.require", () => require("child_process"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      expectDenied("child.resolve", () => require.resolve("child_process"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      console.log("security.p13_50.child_shadow=" + shadowLoaded("child_process"));

                      console.log("security.p13_50.native_positive=" + [
                        profile.nativeAddonProfile.signedPackagedOnly,
                        profile.nativeAddonProfile.hashAllowlist,
                        profile.nativeAddonProfile.crashIsolation,
                        profile.featureFlags.native_addon.releaseForbidden
                      ].join("|"));
                      expectDenied("native.require", () => require("./native.node"), "ERR_AUTOJS6_NATIVE_ADDON_DISABLED");
                      expectDenied("native.dlopen", () => process.dlopen({}, "./native.node"), "ERR_AUTOJS6_PROCESS_API_DISABLED");

                      console.log("security.p13_50.inspector_positive=" + [
                        profile.inspectorProfile.adbForwarding,
                        profile.inspectorProfile.explicitUserAction,
                        profile.inspectorProfile.releaseBuild,
                        process.features.inspector,
                        process.features.debug
                      ].join("|"));
                      expectDenied("inspector.require", () => require("inspector"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      expectDenied("inspector.resolve", () => require.resolve("inspector"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      console.log("security.p13_50.inspector_shadow=" + shadowLoaded("inspector"));

                      const wasmHeader = new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0]);
                      console.log("security.p13_50.wasi_positive=" + [
                        WebAssembly.validate(wasmHeader),
                        profile.wasiProfile.pureWebAssembly,
                        profile.wasiProfile.preopens,
                        profile.wasiProfile.rawHostPaths,
                        profile.wasiProfile.rawFdNumbers
                      ].join("|"));
                      expectDenied("wasi.require", () => require("wasi"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      expectDenied("wasi.resolve", () => require.resolve("wasi"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      console.log("security.p13_50.wasi_shadow=" + shadowLoaded("wasi"));

                      console.log("security.p13_50.ready=true");
                    })().catch((error) => {
                      console.error("security.p13_50.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = File(workDir, "phase13-relaxed-capability-security-corpus.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node Phase 13 relaxed capability security corpus", result)
            listOf(
                "security.p13_50.raw_network_positive=desktop_compat_opt_in|true|false|false|false",
                "security.p13_50.raw_network_controlled_fetch=200|fetch-ok|1|network",
                "security.p13_50.raw_network.net_connect=",
                "security.p13_50.raw_network.net_listen=",
                "security.p13_50.raw_network.dns_resolve=",
                "security.p13_50.fs_positive=safe|safe-copy|scoped_working_directory|true|0|false|false",
                "security.p13_50.fs_escape=",
                "security.p13_50.fs_proc=",
                "security.p13_50.fs_symlink=",
                "security.p13_50.child_positive=0|shell-ok|1|shell|shell|function|undefined|undefined",
                "security.p13_50.child.require=",
                "security.p13_50.child.resolve=",
                "security.p13_50.child_shadow=false",
                "security.p13_50.native_positive=required_before_enablement|required_before_enablement|required_before_enablement|true",
                "security.p13_50.native.require=",
                "security.p13_50.native.dlopen=",
                "security.p13_50.inspector_positive=required_before_enablement|required_before_enablement|denied|false|false",
                "security.p13_50.inspector.require=",
                "security.p13_50.inspector.resolve=",
                "security.p13_50.inspector_shadow=false",
                "security.p13_50.wasi_positive=true|available|scoped_or_authorized_roots_required|false|false",
                "security.p13_50.wasi.require=",
                "security.p13_50.wasi.resolve=",
                "security.p13_50.wasi_shadow=false",
                "security.p13_50.ready=true",
            ).forEach { token ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing P13-50 relaxed capability corpus token $token", result),
                    result.stdout.contains(token),
                )
            }
            listOf(
                NodeRuntimeErrorCodes.NETWORK_DISABLED,
                NodeRuntimeErrorCodes.FS_PATH_ESCAPE,
                NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED,
                NodeRuntimeErrorCodes.FS_REALPATH_ESCAPE,
                NodeRuntimeErrorCodes.BUILTIN_DISABLED,
                NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED,
                NodeRuntimeErrorCodes.PROCESS_API_DISABLED,
            ).forEach { code ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing P13-50 relaxed capability denial $code", result),
                    result.stdout.contains(code),
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
    fun phase13JavaInteropRelaxedCapabilityCorpusKeepsAllowlistAndDenials() {
        assertFalse(BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED)

        val disabledProvider = AndroidNodeBridgeCapabilityProvider(
            context,
            javaInteropExperimentalEnabled = false,
        )
        try {
            val disabled = dispatchJava(
                disabledProvider,
                "callStatic",
                """[{"className":"java.lang.Math","method":"max","args":[3,7]}]""",
            )
            assertFalse(disabled.ok)
            assertEquals(NodeRuntimeErrorCodes.JAVA_INTEROP_DISABLED, disabled.error?.code)
        } finally {
            disabledProvider.destroy()
        }

        val enabledProvider = AndroidNodeBridgeCapabilityProvider(
            context,
            javaInteropExperimentalEnabled = true,
        )
        try {
            val math = dispatchJava(
                enabledProvider,
                "callStatic",
                """[{"className":"java.lang.Math","method":"max","args":[3,7]}]""",
            )
            assertTrue(math.toString(), math.ok)
            assertEquals(7.0, (JSONTokener(math.resultJson.orEmpty()).nextValue() as Number).toDouble(), 0.0)

            val rect = dispatchJava(
                enabledProvider,
                "new",
                """[{"className":"android.graphics.Rect","args":[1,2,3,4]}]""",
            )
            assertTrue(rect.toString(), rect.ok)
            val rectHandle = JSONTokener(rect.resultJson.orEmpty())
                .nextValue()
                .let { it as org.json.JSONObject }
                .getString("__autojs6JavaHandle")
            assertTrue(rectHandle.startsWith("java-"))
            assertEquals("1", enabledProvider.nativeDiagnostics()["embedded_script.java_interop_handle_count"])

            val width = dispatchJava(
                enabledProvider,
                "call",
                """[{"handle":"$rectHandle","method":"width","args":[]}]""",
            )
            assertTrue(width.toString(), width.ok)
            assertEquals(2.0, (JSONTokener(width.resultJson.orEmpty()).nextValue() as Number).toDouble(), 0.0)

            val runtimeDenied = dispatchJava(
                enabledProvider,
                "callStatic",
                """[{"className":"java.lang.Runtime","method":"exec","args":["id"]}]""",
            )
            assertFalse(runtimeDenied.ok)
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, runtimeDenied.error?.code)

            val reflectionDenied = dispatchJava(
                enabledProvider,
                "type",
                """[{"className":"java.lang.reflect.Method"}]""",
            )
            assertFalse(reflectionDenied.ok)
            assertEquals(NodeRuntimeErrorCodes.JAVA_REFLECTION_DENIED, reflectionDenied.error?.code)

            val systemLoadLibraryDenied = dispatchJava(
                enabledProvider,
                "callStatic",
                """[{"className":"java.lang.System","method":"loadLibrary","args":["autojs6-node"]}]""",
            )
            assertFalse(systemLoadLibraryDenied.ok)
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, systemLoadLibraryDenied.error?.code)

            val released = dispatchJava(
                enabledProvider,
                "release",
                """[{"handle":"$rectHandle"}]""",
            )
            assertTrue(released.toString(), released.ok)
            assertEquals(true, JSONTokener(released.resultJson.orEmpty()).nextValue())
            assertEquals("0", enabledProvider.nativeDiagnostics()["embedded_script.java_interop_handle_count"])

            val afterRelease = dispatchJava(
                enabledProvider,
                "call",
                """[{"handle":"$rectHandle","method":"width","args":[]}]""",
            )
            assertFalse(afterRelease.ok)
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, afterRelease.error?.code)
        } finally {
            enabledProvider.destroy()
        }
    }

    private fun freshWorkDir(name: String): File =
        File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work dir: $absolutePath" }
        }

    private fun dispatchJava(provider: NodeBridgeCapabilityProvider, method: String, argsJson: String): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "p13-50-java-provider-${System.nanoTime()}",
                module = "java",
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("java_interop"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for P13-50 java provider response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing P13-50 java provider response" }
    }

    private fun writePhase5Plugin(
        workDir: File,
        id: String,
        source: String,
        permissions: List<String> = emptyList(),
        entry: String = "index.cjs",
    ) {
        val pluginDir = File(workDir, "plugins/$id").apply {
            check(mkdirs()) { "Failed to create plugin dir: $absolutePath" }
        }
        val permissionsJson = permissions.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        File(pluginDir, "package.json").writeText(
            """
            {
              "name": "$id",
              "version": "1.0.0",
              "main": "$entry",
              "autojs6": {
                "plugin": {
                  "id": "$id",
                  "permissions": $permissionsJson
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        File(pluginDir, entry).apply {
            parentFile?.mkdirs()
            writeText(source, StandardCharsets.UTF_8)
        }
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
