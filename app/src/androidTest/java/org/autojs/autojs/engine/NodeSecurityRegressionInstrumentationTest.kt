package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
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

                  expectDenied("fs.absolute", () => fs.readFileSync("$absoluteSecret", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.escape", () => fs.readFileSync("../secret.txt", "utf8"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
                  expectDenied("fs.nul", () => fs.readFileSync("bad" + String.fromCharCode(0) + "path", "utf8"), "ERR_AUTOJS6_FS_NUL_BYTE");
                  expectDenied("fs.proc", () => fs.readFileSync("/proc/version", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.sys", () => fs.readFileSync("/sys/kernel", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.dev", () => fs.readFileSync("/dev/null", "utf8"), "ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED");
                  expectDenied("fs.realpath_escape", () => fs.realpathSync("./outside-link/secret.txt"), "ERR_AUTOJS6_FS_REALPATH_ESCAPE");
                  expectDenied("fs.symlink_escape", () => fs.readFileSync("./outside-link/secret.txt", "utf8"), "ERR_AUTOJS6_FS_REALPATH_ESCAPE");
                  await expectDeniedAsync("fsp.escape", () => fsp.readFile("../secret.txt", "utf8"), "ERR_AUTOJS6_FS_PATH_ESCAPE");
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
        } finally {
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
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
