package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeDeveloperReplInstrumentationTest {

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
    fun replEvaluatesSimpleExpression() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node developer REPL")
        assertTrue("Developer REPL should be available in debug instrumentation builds.", BuildConfig.DEBUG)

        NodeDeveloperRepl.start(context).use { repl ->
            assertTrue("REPL working directory should exist.", repl.workingDirectory.isDirectory)

            val result = repl.evaluate(
                """
                    console.log("repl.console=ok");
                    1 + 2
                """.trimIndent(),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node developer REPL simple expression", result)
            assertEquals("3", result.resultText)
            assertTrue(result.stdout.contains("repl.console=ok"))
        }
    }

    @Test
    fun replAllowsAllowedBuiltinRequirePath() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node developer REPL builtin require")

        NodeDeveloperRepl.start(context).use { repl ->
            val result = repl.evaluate(
                """
                    const path = require("path");
                    path.join("alpha", "beta")
                """.trimIndent(),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node developer REPL require path", result)
            assertEquals("alpha/beta", result.resultText?.replace('\\', '/'))
        }
    }

    @Test
    fun replKeepsDisabledChildProcessDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node developer REPL child_process denial")

        NodeDeveloperRepl.start(context).use { repl ->
            val result = repl.evaluate(
                """
                    (() => {
                      try {
                        require("child_process");
                        return "allowed";
                      } catch (error) {
                        return [
                          error.code,
                          error.autojs6Code,
                          /child_process/.test(String(error && error.message || ""))
                        ].join("|");
                      }
                    })()
                """.trimIndent(),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node developer REPL child_process denial", result)
            assertEquals(
                "${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}|${NodeRuntimeErrorCodes.BUILTIN_DISABLED}|true",
                result.resultText,
            )
        }
    }

    @Test
    fun replBridgeModulesAreDenyByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node developer REPL bridge deny-by-default")

        NodeDeveloperRepl.start(context).use { repl ->
            val result = repl.evaluate(
                """
                    (async () => {
                      try {
                        await require("toast").showToast("blocked", { timeoutMs: 10 });
                        return "allowed";
                      } catch (error) {
                        return [
                          error.code,
                          error.autojs6Code,
                          error.module,
                          error.method,
                          Array.isArray(error.missingCapabilities) ? error.missingCapabilities.join(",") : ""
                        ].join("|");
                      }
                    })()
                """.trimIndent(),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node developer REPL bridge deny-by-default", result)
            assertEquals(
                "${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|" +
                        "${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|toast|showToast|toast",
                result.resultText,
            )
        }
    }

    @Test
    fun replBridgeModulesWorkWithExplicitCapability() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node developer REPL explicit bridge capability")

        NodeDeveloperRepl.start(
            context,
            NodeDeveloperRepl.Options(bridgeCapabilities = listOf(NodeBridgePermissionManifest.TOAST)),
        ).use { repl ->
            val result = repl.evaluate(
                """
                    (async () => {
                      const value = await require("toast").showToast("AutoJs6 Node REPL", { timeoutMs: 1000 });
                      return value === null;
                    })()
                """.trimIndent(),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node developer REPL explicit bridge capability", result)
            assertEquals("true", result.resultText)
        }
    }

    @Test
    fun replWorkingDirectoryIsTemporaryAndCleanedAfterClose() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node developer REPL cleanup")
        val repl = NodeDeveloperRepl.start(context)
        val workDir = repl.workingDirectory
        try {
            assertTrue("REPL working directory should exist before close.", workDir.isDirectory)
            val result = repl.evaluate(
                """
                    const fs = require("fs");
                    fs.writeFileSync("marker.txt", "ok", "utf8");
                    fs.existsSync("marker.txt")
                """.trimIndent(),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node developer REPL temporary working directory", result)
            assertEquals("true", result.resultText)
            assertTrue(File(workDir, "marker.txt").isFile)
        } finally {
            repl.close()
        }

        assertFalse("REPL working directory should be removed after close.", workDir.exists())
    }
}
