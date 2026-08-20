package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeRhinoShimDesignGateInstrumentationTest {

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
    fun rhinoShimFacadeIsDisabledByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Rhino shim disabled gate")

        val workDir = File(context.cacheDir, "node-rhino-shim-design-gate-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Rhino shim design gate work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/rhino").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val source = """
                "nodejs";
                (async function main() {
                  const assert = require("assert/strict");
                  const rhino = require("rhino");
                  const profile = require("autojs6:profile");

                  function detailOf(error) {
                    return [
                      error && error.code,
                      error && error.autojs6Code,
                      error && error.legacyCode,
                      error && error.module,
                      error && error.api,
                      error && error.category
                    ].filter(Boolean).join("/");
                  }
                  async function expectAsyncDenied(label, promise, code) {
                    try {
                      await promise;
                    } catch (error) {
                      assert.strictEqual(error && error.code, code, label + " code");
                      assert.strictEqual(error && error.autojs6Code, code, label + " autojs6Code");
                      assert.strictEqual(error && error.module, "rhino", label + " module");
                      console.log("rhino." + label + "=" + detailOf(error));
                      return;
                    }
                    throw new Error(label + " unexpectedly resolved");
                  }
                  function expectSyncDenied(label, action, code) {
                    try {
                      action();
                    } catch (error) {
                      assert.strictEqual(error && error.autojs6Code, code, label + " autojs6Code");
                      console.log("rhino." + label + "=" + detailOf(error));
                      return;
                    }
                    throw new Error(label + " unexpectedly succeeded");
                  }

                  const status = rhino.status();
                  assert.strictEqual(Object.isFrozen(rhino), true);
                  assert.strictEqual(Object.isFrozen(status), true);
                  assert.strictEqual(require.resolve("rhino"), "rhino");
                  assert.strictEqual(rhino.name, "rhino");
                  assert.strictEqual(rhino.mode, "disabled");
                  assert.strictEqual(rhino.enabled, false);
                  assert.strictEqual(rhino.isEnabled(), false);
                  assert.strictEqual(status.schema, "autojs6-rhino-json-only-shim-disabled-v1");
                  assert.strictEqual(status.enabled, false);
                  assert.strictEqual(status.run, "disabled");
                  assert.strictEqual(status.install, "unsupported");
                  assert.strictEqual(status.globalsInstalled, false);
                  assert.strictEqual(status.nodeAlias, false);
                  assert.strictEqual(status.shadowing, "reserved");
                  assert.strictEqual(rhino.errorCodes.shimDisabled, "ERR_AUTOJS6_RHINO_SHIM_DISABLED");
                  assert.strictEqual(rhino.errorCodes.installUnsupported, "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED");
                  assert.strictEqual(rhino.errorCodes.importUnsupported, "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED");
                  assert.strictEqual(rhino.errorCodes.javaAdapterUnsupported, "ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED");
                  assert.strictEqual(profile.errorCodes.rhinoShimDisabled, "ERR_AUTOJS6_RHINO_SHIM_DISABLED");
                  assert.strictEqual(profile.errorCodes.rhinoInstallUnsupported, "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED");
                  assert.strictEqual(profile.errorCodes.rhinoImportUnsupported, "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED");
                  assert.strictEqual(profile.errorCodes.rhinoJavaAdapterUnsupported, "ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED");
                  assert.strictEqual(globalThis.rhino, undefined);
                  assert.strictEqual(globalThis.importClass, undefined);
                  assert.strictEqual(globalThis.importPackage, undefined);
                  assert.strictEqual(globalThis.Packages, undefined);
                  assert.strictEqual(globalThis.JavaAdapter, undefined);
                  assert.notStrictEqual(rhino.shadowed, true);

                  await expectAsyncDenied(
                    "run",
                    rhino.run({ source: "1 + 1", timeoutMs: 1000, args: [] }),
                    "ERR_AUTOJS6_RHINO_SHIM_DISABLED"
                  );
                  expectSyncDenied(
                    "install",
                    function() { rhino.install(); },
                    "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED"
                  );
                  expectSyncDenied(
                    "importClass",
                    function() { rhino.importClass("android.graphics.Rect"); },
                    "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED"
                  );
                  expectSyncDenied(
                    "importPackage",
                    function() { rhino.importPackage("android.graphics"); },
                    "ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED"
                  );
                  expectSyncDenied(
                    "JavaAdapter",
                    function() { rhino.JavaAdapter(function() {}, {}); },
                    "ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED"
                  );
                  expectSyncDenied(
                    "nodePrefix",
                    function() { require("node:rhino"); },
                    "ERR_AUTOJS6_BUILTIN_DISABLED"
                  );

                  console.log("rhino.ready=true");
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-rhino-shim-design-gate.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Rhino shim disabled gate", result)
            assertTrue(result.stdout, result.stdout.contains("rhino.run=ERR_AUTOJS6_RHINO_SHIM_DISABLED/ERR_AUTOJS6_RHINO_SHIM_DISABLED/rhino/run/disabled"))
            assertTrue(result.stdout, result.stdout.contains("rhino.install=ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED/ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED/rhino/install/unsupported"))
            assertTrue(result.stdout, result.stdout.contains("rhino.importClass=ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED/ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED/rhino/importClass/unsupported"))
            assertTrue(result.stdout, result.stdout.contains("rhino.importPackage=ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED/ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED/rhino/importPackage/unsupported"))
            assertTrue(result.stdout, result.stdout.contains("rhino.JavaAdapter=ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED/ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED/rhino/JavaAdapter/unsupported"))
            assertTrue(result.stdout, result.stdout.contains("rhino.nodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("rhino.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun rhinoJsonOnlyPocRunsBehindExplicitCompatibilityRequest() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Rhino JSON-only POC")

        val workDir = File(context.cacheDir, "node-rhino-json-only-poc-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Rhino JSON-only POC work dir: $absolutePath" }
        }
        try {
            File(workDir, "legacy.rhino.js").writeText(
                """
                    console.warn("path", args[0].label);
                    ({ fromPath: true, label: args[0].label });
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )

            val source = """
                "nodejs";
                (async function main() {
                  const assert = require("assert/strict");
                  const rhino = require("rhino");

                  const inline = await rhino.run({
                    explicit: true,
                    name: "inline.rhino.js",
                    source: 'console.log("source", args[0], args[1]); ({ sum: args[0] + args[1], nested: { ok: true } });',
                    args: [2, 3],
                    timeoutMs: 5000
                  });
                  assert.strictEqual(Object.isFrozen(inline), true);
                  assert.strictEqual(Object.isFrozen(inline.value), true);
                  assert.strictEqual(inline.ok, true);
                  assert.strictEqual(inline.value.sum, 5);
                  assert.strictEqual(inline.value.nested.ok, true);
                  assert.strictEqual(inline.lifecycle.destroyed, true);
                  assert.strictEqual(inline.console.lines[0].level, "log");
                  assert.match(inline.console.lines[0].message, /source 2 3/);

                  const fromPath = await rhino.run({
                    explicit: true,
                    path: "legacy.rhino.js",
                    args: [{ label: "ok" }],
                    timeoutMs: 5000
                  });
                  assert.strictEqual(fromPath.ok, true);
                  assert.deepStrictEqual(fromPath.value, { fromPath: true, label: "ok" });
                  assert.strictEqual(fromPath.console.lines[0].level, "warn");
                  assert.match(fromPath.sourceName, /legacy\.rhino\.js$/);

                  const thrown = await rhino.run({
                    explicit: true,
                    source: 'throw new Error("boom");',
                    timeoutMs: 5000
                  });
                  assert.strictEqual(thrown.ok, false);
                  assert.strictEqual(thrown.error.code, "ERR_AUTOJS6_RHINO_EXECUTION_ERROR");
                  assert.strictEqual(thrown.error.category, "rhino-exception");
                  assert.match(thrown.error.message, /boom/);

                  const unsafe = await rhino.run({
                    explicit: true,
                    source: "runtime;",
                    timeoutMs: 5000
                  });
                  assert.strictEqual(unsafe.ok, false);
                  assert.strictEqual(unsafe.error.code, "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT");

                  const timedOut = await rhino.run({
                    explicit: true,
                    source: "sleep(5000); 1;",
                    timeoutMs: 100
                  });
                  assert.strictEqual(timedOut.ok, false);
                  assert.strictEqual(timedOut.timedOut, true);
                  assert.strictEqual(timedOut.error.code, "ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT");
                  assert.strictEqual(timedOut.lifecycle.forceStopRequested, true);
                  assert.strictEqual(timedOut.lifecycle.destroyed, true);

                  console.log("rhino.poc.inline=" + inline.value.sum);
                  console.log("rhino.poc.path=" + fromPath.value.label);
                  console.log("rhino.poc.error=" + thrown.error.code);
                  console.log("rhino.poc.unsafe=" + unsafe.error.code);
                  console.log("rhino.poc.timeout=" + timedOut.error.code);
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-rhino-json-only-poc.js>",
                workingDirectory = workDir,
                timeoutMs = 20_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Rhino JSON-only POC", result)
            assertTrue(result.stdout, result.stdout.contains("rhino.poc.inline=5"))
            assertTrue(result.stdout, result.stdout.contains("rhino.poc.path=ok"))
            assertTrue(result.stdout, result.stdout.contains("rhino.poc.error=ERR_AUTOJS6_RHINO_EXECUTION_ERROR"))
            assertTrue(result.stdout, result.stdout.contains("rhino.poc.unsafe=ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT"))
            assertTrue(result.stdout, result.stdout.contains("rhino.poc.timeout=ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun rhinoInstallExplicitJavaProxyUsesAllowlistWhenJavaInteropEnabled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Rhino install Java proxy POC")

        val workDir = File(context.cacheDir, "node-rhino-install-java-proxy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Rhino install Java proxy work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/rhino").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val source = """
                "nodejs";
                (async function main() {
                  const assert = require("assert/strict");
                  const bridge = require("autojs6:bridge");
                  const rhino = require("rhino");
                  const requests = [];
                  const releasedHandles = new Set();

                  function codeOf(error) {
                    return String(error && (error.autojs6Code || error.code || error.name || ""));
                  }
                  function respond(request, ok, result, error) {
                    setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok,
                      result,
                      error
                    })));
                  }
                  bridge.__test.setTransport({
                    postMessage(message) {
                      const request = JSON.parse(message);
                      requests.push(request);
                      const descriptor = request.args[0] || {};
                      if (request.module !== "java") {
                        respond(request, false, null, {
                          code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                          message: "unexpected module"
                        });
                        return;
                      }
                      if (request.method === "callStatic" && descriptor.className === "java.lang.Math" && descriptor.method === "max") {
                        respond(request, true, Math.max(descriptor.args[0], descriptor.args[1]));
                        return;
                      }
                      if (request.method === "callStatic" && descriptor.className === "java.util.UUID" && descriptor.method === "fromString") {
                        respond(request, true, String(descriptor.args[0]).toLowerCase());
                        return;
                      }
                      if (request.method === "new" && descriptor.className === "android.graphics.Rect") {
                        respond(request, true, {
                          __autojs6JavaHandle: "java-rhino-rect",
                          className: "android.graphics.Rect",
                          value: {
                            left: descriptor.args[0],
                            top: descriptor.args[1],
                            right: descriptor.args[2],
                            bottom: descriptor.args[3]
                          }
                        });
                        return;
                      }
                      if (request.method === "call" && descriptor.handle === "java-rhino-rect" && descriptor.method === "width") {
                        respond(request, true, 5);
                        return;
                      }
                      if (request.method === "getField" && descriptor.handle === "java-rhino-rect" && descriptor.field === "left") {
                        respond(request, true, 1);
                        return;
                      }
                      if (request.method === "release" && descriptor.handle === "java-rhino-rect") {
                        const firstRelease = !releasedHandles.has(descriptor.handle);
                        releasedHandles.add(descriptor.handle);
                        respond(request, true, firstRelease);
                        return;
                      }
                      respond(request, false, null, {
                        code: "ERR_AUTOJS6_JAVA_METHOD_DENIED",
                        message: "unexpected java proxy call"
                      });
                    }
                  });

                  assert.strictEqual(require.resolve("rhino"), "rhino");
                  assert.notStrictEqual(rhino.shadowed, true);
                  assert.strictEqual(globalThis.Packages, undefined);
                  assert.strictEqual(globalThis.java, undefined);
                  assert.strictEqual(globalThis.android, undefined);

                  let defaultInstallCode = "";
                  try {
                    rhino.install();
                  } catch (error) {
                    defaultInstallCode = codeOf(error);
                  }
                  assert.strictEqual(defaultInstallCode, "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED");

                  const installed = rhino.install({ explicit: true, globals: true });
                  assert.strictEqual(Object.isFrozen(installed), true);
                  assert.deepStrictEqual(installed.installed, ["Packages", "java", "android"]);
                  assert.strictEqual(installed.globalsInstalled, true);
                  assert.strictEqual(installed.importClass, false);
                  assert.strictEqual(installed.importPackage, false);
                  assert.strictEqual(installed.JavaAdapter, false);
                  assert.strictEqual(globalThis.importClass, undefined);
                  assert.strictEqual(globalThis.importPackage, undefined);
                  assert.strictEqual(globalThis.JavaAdapter, undefined);

                  const max = await Packages.java.lang.Math.max(6, 9);
                  const uuid = await java.util.UUID.fromString("00000000-0000-0000-0000-000000000004");
                  const rect = await new android.graphics.Rect(1, 2, 6, 8);
                  const width = await rect.call("width");
                  const left = await rect.getField("left");
                  const released = await rect.release();

                  const beforeInvalid = requests.length;
                  let runtimeCode = "";
                  try {
                    await Packages.java.lang.Runtime.exec("id");
                  } catch (error) {
                    runtimeCode = codeOf(error);
                  }

                  console.log("rhino.install.default=" + defaultInstallCode);
                  console.log("rhino.install.result=" + [
                    installed.schema,
                    installed.mode,
                    installed.globalsInstalled,
                    installed.installed.join("|"),
                    installed.importClass,
                    installed.importPackage,
                    installed.JavaAdapter
                  ].join("/"));
                  console.log("rhino.install.globals=" + [
                    typeof Packages,
                    typeof java,
                    typeof android,
                    typeof importClass,
                    typeof importPackage,
                    typeof JavaAdapter
                  ].join("|"));
                  console.log("rhino.install.results=" + [
                    max,
                    uuid,
                    rect.__autojs6JavaHandle,
                    rect.value.left,
                    rect.value.bottom,
                    width,
                    left,
                    released
                  ].join("|"));
                  console.log("rhino.install.requests=" + requests.map((request) => {
                    const descriptor = request.args[0] || {};
                    return request.method + ":" + (descriptor.className || descriptor.handle || descriptor.field);
                  }).join("|"));
                  console.log("rhino.install.permissions=" + requests.every((request) => request.permissions.indexOf("java_interop") >= 0));
                  console.log("rhino.install.denied=" + runtimeCode + "|" + (requests.length === beforeInvalid));
                  console.log("rhino.install.pending=" + bridge.__test.pendingCount());
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-rhino-install-java-proxy.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                javaInteropEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Rhino install Java proxy POC", result)
            assertTrue(result.stdout, result.stdout.contains("rhino.install.default=ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED"))
            assertTrue(
                result.stdout,
                result.stdout.contains("rhino.install.result=autojs6-rhino-install-java-proxy-v1/java-allowlist-proxy/true/Packages|java|android/false/false/false"),
            )
            assertTrue(result.stdout, result.stdout.contains("rhino.install.globals=function|function|function|undefined|undefined|undefined"))
            assertTrue(
                result.stdout,
                result.stdout.contains("rhino.install.results=9|00000000-0000-0000-0000-000000000004|java-rhino-rect|1|8|5|1|true"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("rhino.install.requests=callStatic:java.lang.Math|callStatic:java.util.UUID|new:android.graphics.Rect|call:java-rhino-rect|getField:java-rhino-rect|release:java-rhino-rect"),
            )
            assertTrue(result.stdout, result.stdout.contains("rhino.install.permissions=true"))
            assertTrue(result.stdout, result.stdout.contains("rhino.install.denied=ERR_AUTOJS6_JAVA_CLASS_DENIED|true"))
            assertTrue(result.stdout, result.stdout.contains("rhino.install.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
