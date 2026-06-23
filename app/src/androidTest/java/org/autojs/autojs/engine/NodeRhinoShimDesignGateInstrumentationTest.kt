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
                  assert.strictEqual(profile.errorCodes.rhinoShimDisabled, "ERR_AUTOJS6_RHINO_SHIM_DISABLED");
                  assert.strictEqual(profile.errorCodes.rhinoInstallUnsupported, "ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED");
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
            assertTrue(result.stdout, result.stdout.contains("rhino.nodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("rhino.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun rhinoJsonOnlyPocRunsBehindExplicitExperimentalRequest() {
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
                    experimental: true,
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
                    experimental: true,
                    path: "legacy.rhino.js",
                    args: [{ label: "ok" }],
                    timeoutMs: 5000
                  });
                  assert.strictEqual(fromPath.ok, true);
                  assert.deepStrictEqual(fromPath.value, { fromPath: true, label: "ok" });
                  assert.strictEqual(fromPath.console.lines[0].level, "warn");
                  assert.match(fromPath.sourceName, /legacy\.rhino\.js$/);

                  const thrown = await rhino.run({
                    experimental: true,
                    source: 'throw new Error("boom");',
                    timeoutMs: 5000
                  });
                  assert.strictEqual(thrown.ok, false);
                  assert.strictEqual(thrown.error.code, "ERR_AUTOJS6_RHINO_EXECUTION_ERROR");
                  assert.strictEqual(thrown.error.category, "rhino-exception");
                  assert.match(thrown.error.message, /boom/);

                  const unsafe = await rhino.run({
                    experimental: true,
                    source: "runtime;",
                    timeoutMs: 5000
                  });
                  assert.strictEqual(unsafe.ok, false);
                  assert.strictEqual(unsafe.error.code, "ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT");

                  const timedOut = await rhino.run({
                    experimental: true,
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
}
