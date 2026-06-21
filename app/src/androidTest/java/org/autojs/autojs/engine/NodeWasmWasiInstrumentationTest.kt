package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWasmWasiInstrumentationTest {

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
    fun webAssemblyCompileInstantiateAndFileAssetWork() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node WebAssembly basic gate")

        val workDir = File(context.cacheDir, "node-wasm-basic-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create WebAssembly basic work dir: $absolutePath" }
        }
        try {
            File(workDir, "add.wasm").writeBytes(ADD_WASM_BYTES)
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert");
                      const fs = require("fs");
                      const inlineBytes = new Uint8Array(${ADD_WASM_BYTES.joinToString(prefix = "[", postfix = "]") { (it.toInt() and 0xff).toString() }});
                      assert.strictEqual(typeof WebAssembly, "object");
                      assert.strictEqual(typeof WebAssembly.compile, "function");
                      assert.strictEqual(typeof WebAssembly.instantiate, "function");
                      assert.strictEqual(WebAssembly.validate(inlineBytes), true);

                      const module = await WebAssembly.compile(inlineBytes);
                      assert.strictEqual(module instanceof WebAssembly.Module, true);
                      const instance = await WebAssembly.instantiate(module, {});
                      assert.strictEqual(instance.exports.add(19, 23), 42);

                      const fileBytes = fs.readFileSync("./add.wasm");
                      assert.strictEqual(WebAssembly.validate(fileBytes), true);
                      const fileInstance = await WebAssembly.instantiate(fileBytes, {});
                      assert.strictEqual(fileInstance.instance.exports.add(7, 5), 12);

                      console.log("wasm.basic=true");
                    })();
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node WebAssembly basic gate", result)
            assertTrue(result.stdout, result.stdout.contains("wasm.basic=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun rawWasiBuiltinsRemainDisabledAndCannotBeShadowed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node raw WASI disabled gate")
        assertTrue(NodeRuntimeCapabilities.disabledBuiltinModuleNames.contains("wasi"))

        val workDir = File(context.cacheDir, "node-wasi-disabled-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create WASI disabled work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/wasi").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const assert = require("assert");
                    const disabledBuiltinCode = "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED";
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
                        assert.strictEqual(error.code, disabledBuiltinCode, label + " code");
                        assert.strictEqual(error.autojs6Code, "ERR_AUTOJS6_BUILTIN_DISABLED", label + " autojs6Code");
                        console.log("wasi." + label + "=" + detailOf(error));
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    const profile = require("autojs6:profile");
                    assert.strictEqual(profile.disabledBuiltins.includes("wasi"), true);
                    expectDenied("requireBare", () => require("wasi"));
                    expectDenied("requireNodePrefix", () => require("node:wasi"));
                    expectDenied("resolveBare", () => require.resolve("wasi"));
                    expectDenied("resolveNodePrefix", () => require.resolve("node:wasi"));
                    console.log("wasi.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").includes("/node_modules/wasi/");
                    }));
                    console.log("wasi.disabled=true");
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node raw WASI disabled gate", result)
            listOf("requireBare", "requireNodePrefix", "resolveBare", "resolveNodePrefix").forEach { label ->
                assertTrue(result.stdout, result.stdout.contains("wasi.$label=${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}"))
            }
            assertTrue(result.stdout, result.stdout.contains("wasi.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("wasi.disabled=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private companion object {
        private val ADD_WASM_BYTES = byteArrayOf(
            0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
            0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,
            0x03, 0x02, 0x01, 0x00,
            0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,
            0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b,
        )
    }
}
