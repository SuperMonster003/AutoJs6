package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeJavaInteropBridgeInstrumentationTest {

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
    fun kotlinJavaInteropProviderIsDisabledByDefaultAndAllowlistedWhenEnabled() {
        assertTrue(NodeBridgeModules.supports("java", "type"))
        assertTrue(NodeBridgeModules.supports("java", "callStatic"))
        assertTrue(NodeBridgeModules.supports("java", "new"))
        assertFalse(NodeBridgeModules.supports("java", "reflect"))
        assertFalse(BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED)

        val disabledProvider = AndroidNodeBridgeCapabilityProvider(
            context,
            javaInteropExperimentalEnabled = false,
        )
        val disabled = dispatchJava(
            disabledProvider,
            "callStatic",
            """[{"className":"java.lang.Math","method":"max","args":[3,7]}]""",
        )
        assertFalse(disabled.ok)
        assertEquals(NodeRuntimeErrorCodes.JAVA_INTEROP_DISABLED, disabled.error?.code)

        val enabledProvider = AndroidNodeBridgeCapabilityProvider(
            context,
            javaInteropExperimentalEnabled = true,
        )

        val math = dispatchJava(
            enabledProvider,
            "callStatic",
            """[{"className":"java.lang.Math","method":"max","args":[3,7]}]""",
        )
        assertTrue(math.toString(), math.ok)
        assertEquals(7.0, (JSONTokener(math.resultJson.orEmpty()).nextValue() as Number).toDouble(), 0.0)

        val uuid = dispatchJava(
            enabledProvider,
            "callStatic",
            """[{"className":"java.util.UUID","method":"fromString","args":["00000000-0000-0000-0000-000000000001"]}]""",
        )
        assertTrue(uuid.toString(), uuid.ok)
        assertEquals("00000000-0000-0000-0000-000000000001", JSONTokener(uuid.resultJson.orEmpty()).nextValue())

        val runtimeDenied = dispatchJava(
            enabledProvider,
            "callStatic",
            """[{"className":"java.lang.Runtime","method":"exec","args":["id"]}]""",
        )
        assertFalse(runtimeDenied.ok)
        assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, runtimeDenied.error?.code)

        val classLoaderDenied = dispatchJava(
            enabledProvider,
            "type",
            """[{"className":"java.lang.ClassLoader"}]""",
        )
        assertFalse(classLoaderDenied.ok)
        assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, classLoaderDenied.error?.code)

        val processBuilderDenied = dispatchJava(
            enabledProvider,
            "type",
            """[{"className":"java.lang.ProcessBuilder"}]""",
        )
        assertFalse(processBuilderDenied.ok)
        assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, processBuilderDenied.error?.code)

        val systemLoadLibraryDenied = dispatchJava(
            enabledProvider,
            "callStatic",
            """[{"className":"java.lang.System","method":"loadLibrary","args":["autojs6-node"]}]""",
        )
        assertFalse(systemLoadLibraryDenied.ok)
        assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, systemLoadLibraryDenied.error?.code)

        val reflectionDenied = dispatchJava(
            enabledProvider,
            "type",
            """[{"className":"java.lang.reflect.Method"}]""",
        )
        assertFalse(reflectionDenied.ok)
        assertEquals(NodeRuntimeErrorCodes.JAVA_REFLECTION_DENIED, reflectionDenied.error?.code)

        val rect = dispatchJava(
            enabledProvider,
            "new",
            """[{"className":"android.graphics.Rect","args":[1,2,3,4]}]""",
        )
        assertTrue(rect.toString(), rect.ok)
        val rectJson = JSONObject(rect.resultJson.orEmpty())
        assertTrue(rectJson.getString("__autojs6JavaHandle").startsWith("java-"))
        assertEquals("android.graphics.Rect", rectJson.getString("className"))
        assertEquals(1, rectJson.getJSONObject("value").getInt("left"))
        assertEquals("1", enabledProvider.nativeDiagnostics()["embedded_script.java_interop_handle_count"])
        enabledProvider.destroy()
        assertEquals("0", enabledProvider.nativeDiagnostics()["embedded_script.java_interop_handle_count"])
    }

    @Test
    fun jsJavaInteropModuleIsDeniedByDefaultAndCannotBeShadowed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Java interop disabled gate")

        val workDir = File(context.cacheDir, "node-java-interop-disabled").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Java interop disabled working directory: $absolutePath" }
        }
        File(workDir, "node_modules/java").apply {
            check(mkdirs()) { "Failed to create Java interop shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            const profile = require("autojs6:profile");
            function codeOf(error) {
              return String(error && (error.autojs6Code || error.code || error.name || ""));
            }
            let resolveCode = "";
            let requireCode = "";
            try {
              require.resolve("java");
            } catch (error) {
              resolveCode = codeOf(error);
            }
            try {
              require("java");
            } catch (error) {
              requireCode = codeOf(error);
            }
            console.log("java.profile=" + [
              profile.featureFlags.java_interop.enabled,
              profile.featureFlags.java_interop.status,
              profile.featureFlags.java_interop.gradleProperty,
              profile.experimentalFlags.javaInterop
            ].join("|"));
            console.log("java.disabled=" + resolveCode + "|" + requireCode);
            """.trimIndent(),
            "<node-java-interop-disabled.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Java interop disabled gate", result)
        assertTrue(result.stdout, result.stdout.contains("java.profile=false|experimental|autojs.nodejs.javaInterop.experimental|false"))
        assertTrue(result.stdout, result.stdout.contains("java.disabled=ERR_AUTOJS6_JAVA_INTEROP_DISABLED|ERR_AUTOJS6_JAVA_INTEROP_DISABLED"))
    }

    @Test
    fun jsJavaInteropExperimentalModuleUsesBridgeAndEnforcesAllowlist() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Java interop experimental fake bridge")

        val workDir = File(context.cacheDir, "node-java-interop-enabled").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Java interop enabled working directory: $absolutePath" }
        }
        File(workDir, "node_modules/java").apply {
            check(mkdirs()) { "Failed to create Java interop shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
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
                  if (request.module !== "java") {
                    respond(request, false, null, {
                      code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                      message: "unexpected module"
                    });
                    return;
                  }
                  const descriptor = request.args[0] || {};
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
                      __autojs6JavaHandle: "java-fake-rect",
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
                  respond(request, false, null, {
                    code: "ERR_AUTOJS6_JAVA_METHOD_DENIED",
                    message: "unexpected java call"
                  });
                }
              });

              const profile = require("autojs6:profile");
              const java = require("java");
              const math = java.type("java.lang.Math");
              const max = await java.callStatic("java.lang.Math", "max", [3, 9], { timeoutMs: 1234 });
              const uuid = await java.callStatic("java.util.UUID", "fromString", ["00000000-0000-0000-0000-000000000002"]);
              const rect = await java.new("android.graphics.Rect", [1, 2, 3, 4]);

              const beforeInvalid = requests.length;
              let runtimeCode = "";
              try {
                await java.callStatic("java.lang.Runtime", "exec", ["id"]);
              } catch (error) {
                runtimeCode = codeOf(error);
              }
              let reflectionCode = "";
              try {
                java.type("java.lang.reflect.Method");
              } catch (error) {
                reflectionCode = codeOf(error);
              }
              let processBuilderCode = "";
              try {
                java.type("java.lang.ProcessBuilder");
              } catch (error) {
                processBuilderCode = codeOf(error);
              }
              let systemLoadLibraryCode = "";
              try {
                await java.callStatic("java.lang.System", "loadLibrary", ["autojs6-node"]);
              } catch (error) {
                systemLoadLibraryCode = codeOf(error);
              }

              console.log("java.resolve=" + require.resolve("java"));
              console.log("java.shadow=" + (java.shadow === true));
              console.log("java.frozen=" + Object.isFrozen(java));
              console.log("java.profile=" + [
                profile.featureFlags.java_interop.enabled,
                profile.featureFlags.java_interop.status,
                profile.experimentalFlags.javaInterop
              ].join("|"));
              console.log("java.methods=" + [typeof java.type, typeof java.callStatic, typeof java.new, typeof math.callStatic, typeof math.new].join("|"));
              console.log("java.results=" + [max, uuid, rect.__autojs6JavaHandle, rect.value.left, rect.value.bottom].join("|"));
              console.log("java.requests=" + requests.map((request) => request.method + ":" + request.args[0].className).join("|"));
              console.log("java.permissions=" + requests.every((request) => request.permissions.indexOf("java_interop") >= 0));
              console.log("java.timeout=" + requests[0].timeoutMs);
              console.log("java.invalid_no_request=" + (requests.length === beforeInvalid));
              console.log("java.codes=" + [
                runtimeCode,
                reflectionCode,
                processBuilderCode,
                systemLoadLibraryCode
              ].join("|"));
              console.log("java.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<node-java-interop-enabled.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
            javaInteropExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Java interop experimental fake bridge", result)
        assertTrue(result.stdout, result.stdout.contains("java.resolve=java"))
        assertTrue(result.stdout, result.stdout.contains("java.shadow=false"))
        assertTrue(result.stdout, result.stdout.contains("java.frozen=true"))
        assertTrue(result.stdout, result.stdout.contains("java.profile=true|experimental|true"))
        assertTrue(result.stdout, result.stdout.contains("java.methods=function|function|function|function|function"))
        assertTrue(result.stdout, result.stdout.contains("java.results=9|00000000-0000-0000-0000-000000000002|java-fake-rect|1|4"))
        assertTrue(result.stdout, result.stdout.contains("callStatic:java.lang.Math|callStatic:java.util.UUID|new:android.graphics.Rect"))
        assertTrue(result.stdout, result.stdout.contains("java.permissions=true"))
        assertTrue(result.stdout, result.stdout.contains("java.timeout=1234"))
        assertTrue(result.stdout, result.stdout.contains("java.invalid_no_request=true"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "java.codes=ERR_AUTOJS6_JAVA_CLASS_DENIED|ERR_AUTOJS6_JAVA_REFLECTION_DENIED|ERR_AUTOJS6_JAVA_CLASS_DENIED|ERR_AUTOJS6_JAVA_CLASS_DENIED",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("java.pending=0"))
    }

    private fun dispatchJava(provider: NodeBridgeCapabilityProvider, method: String, argsJson: String): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "java-provider-${System.nanoTime()}",
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
        assertTrue("Timed out waiting for java provider response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing java provider response" }
    }
}
