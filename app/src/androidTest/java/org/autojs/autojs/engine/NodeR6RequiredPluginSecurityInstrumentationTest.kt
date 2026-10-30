package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Proves that the external runtime plugin keeps the request-level builtin gates the host turns off.
 *
 * The older security corpus targets the embedded runtime. This test exercises the external runtime directly so a
 * release report cannot claim the plugin topology while deriving its security result from a different engine.
 * process.binding / dlopen / _linkedBinding are Node's own since plugin 1.6.0 (M23.2 / M23.4: Android decides what
 * they can do), so only the gates a request can still close are checked.
 */
class NodeR6RequiredPluginSecurityInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before
    fun settleBefore() {
        NodeJsRuntimePluginHost.invalidateForTest(context)
    }

    @After
    fun settleAfter() {
        NodeJsRuntimePluginHost.invalidateForTest(context)
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun requiredPluginKeepsRequestLevelBuiltinGates() {
        assertTrue("R6 security gate did not select the plugin", NodeJsRuntimePluginBridge.isRequested())
        assertTrue("R6 security gate did not require the plugin", NodeJsRuntimePluginBridge.isRequired())

        val runtimeInfo = runBlocking { NodeJsRuntimePluginHost.probe(context) }
        NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)
        NodePluginTestHarness.assertRemotePluginProcess("R6 security gate", runtimeInfo)

        val workDir = File(context.cacheDir, "node-r6-plugin-security-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create R6 plugin security directory: $absolutePath" }
        }
        try {
            val sourceFile = File(workDir, "r6-plugin-security.js")
            val source = permanentDenialSource()
            sourceFile.writeText(source)
            val result = NodeJsRuntimePluginBridge.runScript(
                context,
                NodePluginScriptRequest(
                    source = source,
                    sourceName = sourceFile.absolutePath,
                    workingDirectory = workDir.absolutePath,
                    sandboxRoot = workDir.absolutePath,
                    executionId = "r6-plugin-security-${UUID.randomUUID()}",
                    timeoutMs = TEST_TIMEOUT_MS,
                    rawNodeNetworkModulesEnabled = false,
                    inspectorEnabled = false,
                    workerThreadsEnabled = false,
                    childProcessEnabled = false,
                ),
            )

            NodePluginTestHarness.assertSucceeded("R6 required-plugin builtin gates", result)
            NodePluginTestHarness.assertRanInRuntimeSlot("R6 security request", result.pid)
            assertEquals("R6 security diagnostics did not come from the plugin", "plugin", result.nativeValues[DIAGNOSTICS_SOURCE_KEY])
            assertEquals("R6 security execution retained bridge resources", "0", result.nativeValues[BRIDGE_ACTIVE_COUNT_KEY])
            assertEquals("R6 security execution teardown was not clean", "true", result.nativeValues[EXECUTION_TEARDOWN_KEY])
            assertTrue(result.stdout, result.stdout.contains("security.required_plugin.ready=true"))
            REQUIRED_OUTPUT_LABELS.forEach { label ->
                assertTrue(
                    NodePluginTestHarness.describeResult("missing required-plugin security output $label", result),
                    result.stdout.contains("security.$label="),
                )
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun permanentDenialSource(): String = """
        "nodejs";
        (() => {
          function codeOf(error) {
            return String(error && (error.autojs6Code || error.code || error.name || ""));
          }
          function detailOf(error) {
            return [error && error.code, error && error.autojs6Code, error && error.legacyCode]
              .filter(Boolean).join("/");
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
          for (const name of ["child_process", "worker_threads", "dgram", "net"]) {
            expectDenied("require." + name, () => require(name), "ERR_AUTOJS6_BUILTIN_DISABLED");
          }
          const inspector = require("inspector");
          expectDenied("inspector.open", () => inspector.open(0, "127.0.0.1", false), "ERR_AUTOJS6_BUILTIN_DISABLED");
          expectDenied("inspector.Session", () => new inspector.Session(), "ERR_AUTOJS6_BUILTIN_DISABLED");
          for (const name of ["java", "Packages", "android", "context"]) {
            if (typeof globalThis[name] !== "undefined") {
              throw new Error("raw Android/Java global unexpectedly exposed: " + name);
            }
            console.log("security.global." + name + "=undefined");
          }
          console.log("security.required_plugin.ready=true");
        })();
    """.trimIndent()

    private companion object {
        private const val TEST_TIMEOUT_MS = 15_000L
        private const val DIAGNOSTICS_SOURCE_KEY = "embedded_script.runtime_plugin.diagnostics_source"
        private const val BRIDGE_ACTIVE_COUNT_KEY = "embedded_script.bridge_resource.active_count"
        private const val EXECUTION_TEARDOWN_KEY = "execution.teardown_clean"

        private val REQUIRED_OUTPUT_LABELS = listOf(
            "require.child_process",
            "require.worker_threads",
            "require.dgram",
            "require.net",
            "inspector.open",
            "inspector.Session",
            "global.java",
            "global.Packages",
            "global.android",
            "global.context",
        )
    }
}
