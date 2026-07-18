package org.autojs.autojs.engine

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Proves that the R6 required-plugin delivery path preserves the permanent Safe Node denials.
 *
 * The older security corpus targets the embedded runtime and is intentionally unavailable in a
 * nodePlugin build. This test exercises the external runtime directly so a release report cannot
 * claim the plugin topology while deriving its security result from a skipped embedded test.
 */
class NodeR6RequiredPluginSecurityInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalProperties: Map<String, String?> = emptyMap()

    @Before
    fun settleBefore() {
        originalProperties = MUTATED_SYSTEM_PROPERTIES.associateWith(System::getProperty)
        System.setProperty(NODE_BACKEND_PROPERTY, "plugin")
        System.setProperty(PLUGIN_RUNTIME_ENABLED_PROPERTY, "true")
        System.setProperty(PLUGIN_RUNTIME_REQUIRED_PROPERTY, "true")
        NodeJsRuntimePluginHost.invalidateForTest(context)
    }

    @After
    fun settleAfter() {
        originalProperties.forEach { (name, value) ->
            if (value == null) System.clearProperty(name) else System.setProperty(name, value)
        }
        NodeJsRuntimePluginHost.invalidateForTest(context)
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun requiredPluginPreservesPermanentSafeNodeDenials() {
        assertEquals("R6 security gate requires nodePlugin", "nodePlugin", BuildConfig.NODEJS_RELEASE_FLAVOR)
        assertEquals("R6 security gate requires the plugin backend", "plugin", BuildConfig.NODEJS_RUNTIME_BACKEND)
        assertTrue("R6 security gate did not select the plugin", NodeJsRuntimePluginBridge.isRequested())
        assertTrue("R6 security gate did not require the plugin", NodeJsRuntimePluginBridge.isRequired())

        val runtimeInfo = runBlocking { NodeJsRuntimePluginHost.probe(context) }
        NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)
        val pluginPid = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1)
        assertTrue("R6 security gate requires a remote plugin PID: $runtimeInfo", pluginPid > 0 && pluginPid != Process.myPid())

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
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = source,
                    sourceName = sourceFile.absolutePath,
                    workingDirectory = workDir.absolutePath,
                    sandboxRoot = workDir.absolutePath,
                    executionId = "r6-plugin-security-${UUID.randomUUID()}",
                    timeoutMs = TEST_TIMEOUT_MS,
                ),
            )

            NodeEmbeddedTestHarness.assertSucceeded("R6 required-plugin Safe Node denials", result)
            assertEquals("R6 security request did not execute in the probed plugin process", pluginPid, result.pid)
            assertEquals("R6 security diagnostics did not come from the plugin", "plugin", result.nativeValues[DIAGNOSTICS_SOURCE_KEY])
            assertEquals("R6 security execution retained bridge resources", "0", result.nativeValues[BRIDGE_ACTIVE_COUNT_KEY])
            assertEquals("R6 security execution teardown was not clean", "true", result.nativeValues[EXECUTION_TEARDOWN_KEY])
            assertTrue(result.stdout, result.stdout.contains("security.required_plugin.ready=true"))
            REQUIRED_OUTPUT_LABELS.forEach { label ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing required-plugin security output $label", result),
                    result.stdout.contains("security.$label="),
                )
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun permanentDenialSource(): String = """
        "nodejs";
        (async () => {
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
          for (const name of ["child_process", "worker_threads", "dgram", "inspector"]) {
            expectDenied("require." + name, () => require(name), "ERR_AUTOJS6_BUILTIN_DISABLED");
          }
          await expectDeniedAsync("fetch", () => fetch("https://example.invalid/"), "ERR_AUTOJS6_NETWORK_DISABLED");
          expectDenied("http.request", () => require("http").request("http://127.0.0.1/"), "ERR_AUTOJS6_NETWORK_DISABLED");
          expectDenied("net.connect", () => require("net").connect(80, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
          await expectDeniedAsync(
            "dns.resolve",
            () => require("dns/promises").resolve4("example.invalid"),
            "ERR_AUTOJS6_NETWORK_DISABLED"
          );
          for (const name of ["java", "Packages", "android", "context"]) {
            if (typeof globalThis[name] !== "undefined") {
              throw new Error("raw Android/Java global unexpectedly exposed: " + name);
            }
            console.log("security.global." + name + "=undefined");
          }
          console.log("security.required_plugin.ready=true");
        })().catch((error) => {
          console.error("security.required_plugin.error=" + (error && (error.stack || error.message) || error));
          process.exitCode = 1;
        });
    """.trimIndent()

    private companion object {
        private const val TEST_TIMEOUT_MS = 15_000L
        private const val NODE_BACKEND_PROPERTY = "autojs.nodejs.backend"
        private const val PLUGIN_RUNTIME_ENABLED_PROPERTY = "autojs.nodejs.runtime.plugin.enabled"
        private const val PLUGIN_RUNTIME_REQUIRED_PROPERTY = "autojs.nodejs.runtime.plugin.required"
        private const val DIAGNOSTICS_SOURCE_KEY = "embedded_script.runtime_plugin.diagnostics_source"
        private const val BRIDGE_ACTIVE_COUNT_KEY = "embedded_script.bridge_resource.active_count"
        private const val EXECUTION_TEARDOWN_KEY = "execution.teardown_clean"

        private val MUTATED_SYSTEM_PROPERTIES = listOf(
            NODE_BACKEND_PROPERTY,
            PLUGIN_RUNTIME_ENABLED_PROPERTY,
            PLUGIN_RUNTIME_REQUIRED_PROPERTY,
        )

        private val REQUIRED_OUTPUT_LABELS = listOf(
            "process.binding",
            "process.dlopen",
            "process._linkedBinding",
            "require.child_process",
            "require.worker_threads",
            "require.dgram",
            "require.inspector",
            "fetch",
            "http.request",
            "net.connect",
            "dns.resolve",
            "global.java",
            "global.Packages",
            "global.android",
            "global.context",
        )
    }
}
