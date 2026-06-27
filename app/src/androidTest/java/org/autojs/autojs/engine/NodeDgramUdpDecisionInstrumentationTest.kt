package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeDgramUdpDecisionInstrumentationTest {

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
    fun dgramUdpCapabilityStaysDisabledAndCannotBeShadowed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-31 dgram UDP decision regression")

        val workDir = File(context.cacheDir, "node-dgram-udp-decision-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create dgram UDP decision work dir: $absolutePath" }
        }

        try {
            writeShadowModule(workDir, "dgram")
            File(workDir, "node_modules/needs-dgram/index.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = require("dgram");""" + "\n", StandardCharsets.UTF_8)
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      const codes = [
                        "require:" + syncCode(() => require("dgram")),
                        "node:" + syncCode(() => require("node:dgram")),
                        "resolve:" + syncCode(() => require.resolve("dgram")),
                        "resolveNode:" + syncCode(() => require.resolve("node:dgram")),
                        "transitive:" + syncCode(() => require("needs-dgram"))
                      ];

                      if (typeof process.getBuiltinModule === "function") {
                        codes.push("getBuiltin:" + syncCode(() => process.getBuiltinModule("dgram")));
                      } else {
                        codes.push("getBuiltin:absent");
                      }

                      console.log("p13dgram.codes=" + codes.join("|"));
                      console.log("p13dgram.shadow=" + (globalThis.__autojs6DgramShadowLoaded === true));
                      console.log("p13dgram.ready=true");
                    })().catch((error) => {
                      console.error("p13dgram.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = "<node-dgram-udp-decision-p13-31.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-31 dgram UDP decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13dgram.codes=require:ERR_AUTOJS6_BUILTIN_DISABLED|node:ERR_AUTOJS6_BUILTIN_DISABLED|resolve:ERR_AUTOJS6_BUILTIN_DISABLED|resolveNode:ERR_AUTOJS6_BUILTIN_DISABLED|transitive:ERR_AUTOJS6_BUILTIN_DISABLED|",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13dgram.shadow=false"))
            assertTrue(result.stdout, result.stdout.contains("p13dgram.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeShadowModule(workDir: File, moduleName: String) {
        File(workDir, "node_modules/$moduleName/index.js").apply {
            parentFile?.mkdirs()
            writeText(
                """
                    globalThis.__autojs6DgramShadowLoaded = true;
                    module.exports = { shadow: true, id: "$moduleName-shadow" };
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
    }
}
