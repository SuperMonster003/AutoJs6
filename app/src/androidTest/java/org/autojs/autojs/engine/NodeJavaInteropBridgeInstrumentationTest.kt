package org.autojs.autojs.engine

import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Java interop: the host owns the allowlist as a declarative table (M20.2 batch 15), executes the
 * admitted members by reflection and publishes the table through the broker info; the plugin
 * runtime forwards calls and shows the table as `java.policy`. The disabled gate, the module
 * shadowing rule and the fake-transport contract predate batch 15 and still hold.
 */
class NodeJavaInteropBridgeInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun hostBrokerPublishesTheJavaInteropAllowlist() {
        val broker = NodeJsHostCapabilityBroker(
            context,
            NodePluginScriptRequest(
                source = "",
                sourceName = "<java-interop-policy>",
                workingDirectory = context.cacheDir.absolutePath,
                sandboxRoot = context.cacheDir.absolutePath,
                executionId = "java-interop-policy-${System.nanoTime()}",
            ),
            "instrumentation",
        )
        try {
            val info = broker.getBrokerInfo()
            assertTrue(info.getStringArray(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES)!!.contains("java"))
            val policy = JSONObject(
                info.getString(NodeJsRuntimeContract.KEY_JAVA_INTEROP_POLICY_JSON)
                    ?: error("broker info does not publish the Java interop policy"),
            )
            assertEquals("host_provider", policy.getString("limitsEnforcedBy"))
            assertEquals(
                listOf(
                    "android.graphics.Color",
                    "android.graphics.Point",
                    "android.graphics.Rect",
                    "android.os.Build",
                    "android.os.Build\$VERSION",
                    "java.lang.Math",
                    "java.util.Locale",
                    "java.util.UUID",
                    "java.util.concurrent.TimeUnit",
                ),
                policy.getJSONArray("allowedClasses").toList(),
            )
            assertTrue(policy.getJSONArray("deniedClassPrefixes").toList().contains("java.lang.reflect."))
            val classes = policy.getJSONObject("classes")
            val rect = classes.getJSONObject("android.graphics.Rect")
            assertTrue(rect.getBoolean("constructable"))
            assertTrue(rect.getJSONArray("instanceMethods").toList().contains("width"))
            assertEquals(listOf("bottom", "left", "right", "top"), rect.getJSONArray("instanceFields").toList())
            assertEquals(listOf("intersects", "unflattenFromString"), rect.getJSONArray("staticMethods").toList())
            val math = classes.getJSONObject("java.lang.Math")
            assertFalse(math.getBoolean("constructable"))
            assertEquals(listOf("E", "PI"), math.getJSONArray("staticFields").toList())
            assertTrue(math.getJSONArray("staticMethods").toList().contains("floorDiv"))
            assertTrue(classes.getJSONObject("java.util.concurrent.TimeUnit").getJSONArray("staticFields").toList().contains("SECONDS"))
        } finally {
            broker.destroy(null)
        }
        for (method in listOf("type", "callStatic", "new", "call", "callInstance", "getField", "getStatic", "release", "dispose")) {
            assertTrue(method, NodeBridgeModules.supports("java", method))
        }
        assertFalse(NodeBridgeModules.supports("java", "reflect"))
    }

    @Test
    fun androidJavaProviderExecutesTheAllowlistByReflection() {
        fun dispatch(provider: NodeBridgeCapabilityProvider, method: String, payload: JSONObject): NodeBridgeResponse {
            val latch = CountDownLatch(1)
            var response: NodeBridgeResponse? = null
            provider.dispatch(
                NodeBridgeRequest(
                    id = "java-$method-${System.nanoTime()}",
                    module = "java",
                    method = method,
                    argsJson = JSONArray().put(payload).toString(),
                    timeoutMs = 5_000L,
                    permissions = listOf("java_interop"),
                ),
            ) { bridgeResponse ->
                response = bridgeResponse
                latch.countDown()
            }
            assertTrue("java.$method did not complete", latch.await(10, TimeUnit.SECONDS))
            return response ?: error("Missing java.$method response")
        }

        fun statik(className: String, method: String, vararg args: Any?) =
            JSONObject().put("className", className).put("method", method).put("args", JSONArray(args.toList()))

        // The explicit denial still wins before any table lookup.
        val disabledProvider = AndroidNodeBridgeCapabilityProvider(context, javaInteropEnabled = false)
        dispatch(disabledProvider, "callStatic", statik("java.lang.Math", "max", 3, 7)).also { disabled ->
            assertFalse(disabled.ok)
            assertEquals(NodeRuntimeErrorCodes.JAVA_INTEROP_DISABLED, disabled.error?.code)
        }
        disabledProvider.destroy()

        val provider = AndroidNodeBridgeCapabilityProvider(context)
        try {
            fun ok(method: String, payload: JSONObject): String {
                val response = dispatch(provider, method, payload)
                assertTrue("java.$method failed: ${response.error}", response.ok)
                return response.resultJson ?: "null"
            }

            fun code(method: String, payload: JSONObject): String {
                val response = dispatch(provider, method, payload)
                assertFalse("java.$method unexpectedly succeeded: ${response.resultJson}", response.ok)
                return response.error?.code.orEmpty()
            }

            fun number(json: String): Double = JSONArray("[$json]").getDouble(0)
            fun ctor(className: String, vararg args: Any?) =
                JSONObject().put("className", className).put("args", JSONArray(args.toList()))
            fun call(handle: JSONObject, method: String, vararg args: Any?) =
                JSONObject().put("handle", handle.getString("__autojs6JavaHandle")).put("method", method).put("args", JSONArray(args.toList()))
            fun field(handle: JSONObject, field: String) =
                JSONObject().put("handle", handle.getString("__autojs6JavaHandle")).put("field", field)
            fun staticField(className: String, field: String) = JSONObject().put("className", className).put("field", field)
            fun handleCount(): String = provider.nativeDiagnostics()["embedded_script.java_interop_handle_count"].orEmpty()

            // Static methods with overload selection: max(int,int) vs max(long,long)/max(double,double).
            assertEquals("7", ok("callStatic", statik("java.lang.Math", "max", 3, 7)))
            assertEquals(1024.0, number(ok("callStatic", statik("java.lang.Math", "pow", 2, 10))), 0.0)
            assertEquals(2.5, number(ok("callStatic", statik("java.lang.Math", "abs", -2.5))), 0.0)
            assertEquals("3", ok("callStatic", statik("java.lang.Math", "round", 2.5)))
            assertEquals(Math.PI, number(ok("getStatic", staticField("java.lang.Math", "PI"))), 0.0)
            assertEquals("-65536", ok("callStatic", statik("android.graphics.Color", "rgb", 255, 0, 0)))
            assertEquals("-65536", ok("callStatic", statik("android.graphics.Color", "parseColor", "#FF0000")))
            assertEquals("-65536", ok("getStatic", staticField("android.graphics.Color", "RED")))

            // Exceptions thrown by the member itself surface as JAVA_CALL_FAILED, not as a denial.
            dispatch(provider, "callStatic", statik("java.lang.Math", "floorDiv", 1, 0)).also { response ->
                assertFalse(response.ok)
                assertEquals(NodeRuntimeErrorCodes.JAVA_CALL_FAILED, response.error?.code)
                assertTrue(response.error?.message.orEmpty(), response.error?.message.orEmpty().contains("ArithmeticException"))
            }
            assertEquals(NodeRuntimeErrorCodes.JAVA_CALL_FAILED, code("callStatic", statik("android.graphics.Color", "parseColor", "not-a-color")))

            // Constructors, instance members, fields and handle arguments.
            assertEquals("0", handleCount())
            val rect = JSONObject(ok("new", ctor("android.graphics.Rect", 1, 2, 11, 22)))
            assertEquals("1", handleCount())
            assertTrue(rect.getString("__autojs6JavaHandle").startsWith("java-"))
            assertEquals("android.graphics.Rect", rect.getString("className"))
            assertEquals(1, rect.getJSONObject("value").getInt("left"))
            assertEquals(22, rect.getJSONObject("value").getInt("bottom"))
            assertEquals("Rect(1, 2 - 11, 22)", rect.getJSONObject("value").getString("string"))
            assertEquals("10", ok("call", call(rect, "width")))
            assertEquals("2", ok("getField", field(rect, "top")))
            assertEquals("true", ok("call", call(rect, "contains", 5, 5)))
            assertEquals("true", ok("callInstance", call(rect, "contains", 5, 5)))
            assertEquals("null", ok("call", call(rect, "offset", 1, 1)))
            assertEquals("2", ok("getField", field(rect, "left")))
            assertEquals("\"Rect(2, 3 - 12, 23)\"", ok("call", call(rect, "toString")))
            val other = JSONObject(ok("new", ctor("android.graphics.Rect", 0, 0, 5, 5)))
            assertEquals("true", ok("callStatic", statik("android.graphics.Rect", "intersects", rect, other)))
            assertEquals("true", ok("call", call(rect, "intersect", other)))
            assertEquals("5", ok("getField", field(rect, "right")))
            val point = JSONObject(ok("new", ctor("android.graphics.Point", 3, 4)))
            assertEquals(3, point.getJSONObject("value").getInt("x"))
            assertEquals("null", ok("call", call(point, "negate")))
            assertEquals("-3", ok("getField", field(point, "x")))
            assertEquals("true", ok("call", call(point, "equals", point)))

            // Static fields that are objects come back as handles; enums keep their name.
            val us = JSONObject(ok("getStatic", staticField("java.util.Locale", "US")))
            assertEquals("java.util.Locale", us.getString("className"))
            assertEquals("en_US", us.getJSONObject("value").getString("string"))
            assertEquals("\"en\"", ok("call", call(us, "getLanguage")))
            val chinese = JSONObject(ok("callStatic", statik("java.util.Locale", "forLanguageTag", "zh-CN")))
            assertEquals("\"zh-CN\"", ok("call", call(chinese, "toLanguageTag")))
            assertTrue(ok("call", call(chinese, "getDisplayName", us)).length > 2)
            val seconds = JSONObject(ok("getStatic", staticField("java.util.concurrent.TimeUnit", "SECONDS")))
            assertEquals("SECONDS", seconds.getJSONObject("value").getString("name"))
            assertEquals("5000", ok("call", call(seconds, "toMillis", 5)))
            val hours = JSONObject(ok("getStatic", staticField("java.util.concurrent.TimeUnit", "HOURS")))
            assertEquals("3600", ok("call", call(seconds, "convert", 1, hours)))
            assertEquals("\"SECONDS\"", ok("call", call(seconds, "name")))
            val uuid = JSONObject(ok("callStatic", statik("java.util.UUID", "fromString", "123e4567-e89b-12d3-a456-426614174000")))
            assertEquals("1", ok("call", call(uuid, "version")))
            assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", ok("call", call(uuid, "toString")))
            assertEquals("4", ok("call", call(JSONObject(ok("callStatic", statik("java.util.UUID", "randomUUID"))), "version")))
            assertTrue(ok("getStatic", staticField("android.os.Build", "MODEL")).startsWith("\""))
            assertTrue(JSONArray(ok("getStatic", staticField("android.os.Build", "SUPPORTED_ABIS"))).length() >= 1)
            assertTrue(number(ok("getStatic", staticField("android.os.Build\$VERSION", "SDK_INT"))) >= 24.0)

            // The class descriptor is the table entry.
            val described = JSONObject(ok("type", JSONObject().put("className", "java.lang.Math")))
            assertTrue(described.getJSONArray("staticMethods").toList().contains("max"))
            assertFalse(described.getBoolean("constructable"))

            // Denials stay host-side: unlisted classes, loaders and processes, reflection, unlisted members, wrong arity.
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, code("type", JSONObject().put("className", "java.lang.Runtime")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, code("callStatic", statik("java.lang.Runtime", "exec", "id")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, code("type", JSONObject().put("className", "java.lang.ClassLoader")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, code("type", JSONObject().put("className", "java.lang.ProcessBuilder")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, code("callStatic", statik("java.lang.System", "loadLibrary", "autojs6-node")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_CLASS_DENIED, code("callStatic", statik("java.io.File", "listRoots")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_REFLECTION_DENIED, code("type", JSONObject().put("className", "java.lang.reflect.Method")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_REFLECTION_DENIED, code("callStatic", statik("java.lang.reflect.Array", "newInstance")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("callStatic", statik("java.lang.Math", "getClass")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("new", ctor("java.lang.Math")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("getField", field(rect, "bogus")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("call", call(rect, "set", 1)))
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("call", call(rect, "getClass")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("getStatic", staticField("java.lang.Math", "getClass")))
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("callStatic", statik("java.lang.Math", "max", "a", "b")))

            // Released handles are gone for good; the registry keeps the count.
            val before = handleCount().toInt()
            assertEquals("true", ok("release", JSONObject().put("handle", rect.getString("__autojs6JavaHandle"))))
            assertEquals((before - 1).toString(), handleCount())
            assertEquals(NodeRuntimeErrorCodes.JAVA_METHOD_DENIED, code("call", call(rect, "width")))
            assertEquals("false", ok("release", JSONObject().put("handle", rect.getString("__autojs6JavaHandle"))))
            assertTrue("java_interop_handle_count=${handleCount()}", handleCount().toInt() >= 6)
        } finally {
            provider.destroy(NodeBridgeError.processDead("java interop provider test"))
        }
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.java_interop_handle_count"])
    }

    @Test
    fun jsJavaInteropModuleCanBeExplicitlyDeniedAndCannotBeShadowed() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node Java interop disabled gate")

        val workDir = File(context.cacheDir, "node-java-interop-disabled").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Java interop disabled working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true };"""
        File(workDir, "node_modules/java").apply {
            check(mkdirs()) { "Failed to create Java interop shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText(shadowPackageSource)

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            const profile = require("autojs6:profile");
            function codeOf(error) {
              return String(error && (error.autojs6Code || error.code || error.name || ""));
            }
            let resolveOutcome = "";
            let requireCode = "";
            try {
              resolveOutcome = require.resolve("java");
            } catch (error) {
              resolveOutcome = codeOf(error);
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
              profile.featureStates.javaInterop
            ].join("|"));
            console.log("java.disabled=" + resolveOutcome + "|" + requireCode);
            """.trimIndent(),
            "<node-java-interop-disabled.js>",
            workingDirectory = workDir,
            moduleSources = mapOf("node_modules/java/index.js" to shadowPackageSource),
            timeoutMs = 10_000L,
            javaInteropEnabled = false,
        )

        NodePluginTestHarness.assertSucceeded("Java interop disabled gate", result)
        assertTrue(result.stdout, result.stdout.contains("java.profile=false|stable||false"))
        // Node runtime 1.6.0+: require.resolve only maps the name to its canonical "autojs6:java" url; the interop
        // gate fires when the module is loaded.
        assertTrue(result.stdout, result.stdout.contains("java.disabled=autojs6:java|ERR_AUTOJS6_JAVA_INTEROP_DISABLED"))
    }

    @Test
    fun jsJavaInteropStableModuleUsesBridgeAndForwardsToTheHostAllowlist() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node Java interop stable fake bridge")

        val workDir = File(context.cacheDir, "node-java-interop-enabled").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Java interop enabled working directory: $absolutePath" }
        }
        // java_interop is a critical capability that bare scripts do not get; the project declares it.
        // (No node_modules/java shadow here: under a project, M2.4 npm precedence lets an installed
        // package of any bare name win over the runtime module, so the shadow check only holds for
        // bare scripts, see jsJavaInteropModuleCanBeExplicitlyDeniedAndCannotBeShadowed.)
        File(workDir, "project.json").writeText(
            """{"type":"node","node":{"permissions":["java_interop"]}}""" + "\n",
            StandardCharsets.UTF_8,
        )

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
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
              // The fake host mirrors the batch-15 host: class decisions are made here, not in the runtime.
              const deniedClasses = ["java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.System", "java.lang.ClassLoader"];
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
                  if (typeof descriptor.className === "string" && descriptor.className.indexOf("java.lang.reflect.") === 0) {
                    respond(request, false, null, {
                      code: "ERR_AUTOJS6_JAVA_REFLECTION_DENIED",
                      message: "reflection denied by host"
                    });
                    return;
                  }
                  if (deniedClasses.indexOf(descriptor.className) >= 0) {
                    respond(request, false, null, {
                      code: "ERR_AUTOJS6_JAVA_CLASS_DENIED",
                      message: "denied by host"
                    });
                    return;
                  }
                  if (request.method === "type" && descriptor.className === "java.lang.Math") {
                    respond(request, true, { className: "java.lang.Math", explicit: true, constructable: false, staticMethods: ["max"], staticFields: ["PI"] });
                    return;
                  }
                  if (request.method === "getStatic" && descriptor.className === "java.lang.Math" && descriptor.field === "PI") {
                    respond(request, true, Math.PI);
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
                  if (request.method === "callStatic" && descriptor.className === "android.graphics.Rect" && descriptor.method === "intersects") {
                    const handles = descriptor.args.map((arg) => arg && arg.__autojs6JavaHandle);
                    respond(request, true, handles.join(",") === "java-fake-rect,java-fake-rect");
                    return;
                  }
                  if (request.method === "call" && descriptor.handle === "java-fake-rect") {
                    if (releasedHandles.has(descriptor.handle)) {
                      respond(request, false, null, {
                        code: "ERR_AUTOJS6_JAVA_METHOD_DENIED",
                        message: "released handle"
                      });
                      return;
                    }
                    if (descriptor.method === "width") {
                      respond(request, true, 2);
                      return;
                    }
                    if (descriptor.method === "height") {
                      respond(request, true, 2);
                      return;
                    }
                    if (descriptor.method === "centerX") {
                      respond(request, true, 2);
                      return;
                    }
                    if (descriptor.method === "contains") {
                      respond(request, true, descriptor.args[0] >= 1 && descriptor.args[0] < 3 && descriptor.args[1] >= 2 && descriptor.args[1] < 4);
                      return;
                    }
                  }
                  if (request.method === "getField" && descriptor.handle === "java-fake-rect") {
                    const value = { left: 1, top: 2, right: 3, bottom: 4 }[descriptor.field];
                    if (value !== undefined) {
                      respond(request, true, value);
                      return;
                    }
                  }
                  if (request.method === "release" && descriptor.handle === "java-fake-rect") {
                    const firstRelease = !releasedHandles.has(descriptor.handle);
                    releasedHandles.add(descriptor.handle);
                    respond(request, true, firstRelease);
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
              const autojsJava = ${'$'}autojs.java;
              const math = java.type("java.lang.Math");
              const max = await java.callStatic("java.lang.Math", "max", [3, 9], { timeoutMs: 1234 });
              const uuid = await java.callStatic("java.util.UUID", "fromString", ["00000000-0000-0000-0000-000000000002"]);
              const rect = await java.new("android.graphics.Rect", [1, 2, 3, 4]);
              const width = await java.call(rect, "width");
              const height = await rect.call("height");
              const contains = await rect.callInstance("contains", [2, 3]);
              const left = await java.getField(rect, "left");
              const right = await rect.getField("right");
              const centerX = await autojsJava.call(rect, "centerX");
              const autojsMax = await autojsJava.callStatic(autojsJava.findClass("java.lang.Math"), "max", [2, 8]);
              const intersects = await java.callStatic("android.graphics.Rect", "intersects", [rect, rect]);
              const pi = await math.getStatic("PI");
              const described = await autojsJava.describe("java.lang.Math");
              const released = await rect.release();

              const beforeDenials = requests.length;
              let runtimeCode = "";
              try {
                await java.callStatic("java.lang.Runtime", "exec", ["id"]);
              } catch (error) {
                runtimeCode = codeOf(error);
              }
              let reflectionCode = "";
              try {
                await java.describe("java.lang.reflect.Method");
              } catch (error) {
                reflectionCode = codeOf(error);
              }
              let processBuilderCode = "";
              try {
                await java.type("java.lang.ProcessBuilder").describe();
              } catch (error) {
                processBuilderCode = codeOf(error);
              }
              let systemLoadLibraryCode = "";
              try {
                await java.callStatic("java.lang.System", "loadLibrary", ["autojs6-node"]);
              } catch (error) {
                systemLoadLibraryCode = codeOf(error);
              }
              const forwardedDenials = requests.length - beforeDenials;
              let defineClassCode = "";
              try {
                java.defineClass({ name: "org.autojs6.node.GeneratedListener", interfaces: ["java.lang.Runnable"] });
              } catch (error) {
                defineClassCode = codeOf(error);
              }
              let autojsDefineClassCode = "";
              try {
                autojsJava.defineClass({ name: "org.autojs6.node.GeneratedListener", interfaces: ["java.lang.Runnable"] });
              } catch (error) {
                autojsDefineClassCode = codeOf(error);
              }
              let emptyClassCode = "";
              try {
                await java.callStatic("", "max");
              } catch (error) {
                emptyClassCode = codeOf(error);
              }
              let badArgumentCode = "";
              try {
                await java.callStatic("java.lang.Math", "max", [{ nested: true }]);
              } catch (error) {
                badArgumentCode = codeOf(error);
              }
              const localRejections = requests.length - beforeDenials - forwardedDenials;

              console.log("java.resolve=" + require.resolve("java"));
              console.log("java.frozen=" + Object.isFrozen(java));
              console.log("java.rect_frozen=" + Object.isFrozen(rect));
              console.log("java.profile=" + [
                profile.featureFlags.java_interop.enabled,
                profile.featureFlags.java_interop.status,
                profile.featureStates.javaInterop
              ].join("|"));
              console.log("java.policy=" + [
                java.policy.limitsEnforcedBy,
                java.policy.published,
                java.policy.allowedClasses.length,
                Object.isFrozen(java.policy.classes),
                autojsJava.policy.published
              ].join("|"));
              console.log("java.methods=" + [
                typeof java.findClass,
                typeof java.type,
                typeof java.callStatic,
                typeof java.new,
                typeof java.call,
                typeof java.getField,
                typeof java.getStatic,
                typeof java.describe,
                typeof java.release,
                typeof java.dispose,
                typeof java.defineClass,
                typeof autojsJava.defineClass,
                typeof autojsJava.getStatic,
                typeof autojsJava.describe,
                typeof math.callStatic,
                typeof math.new,
                typeof math.create,
                typeof math.getStatic,
                typeof math.describe,
                typeof rect.call,
                typeof rect.callInstance,
                typeof rect.getField,
                typeof rect.release,
                typeof rect.dispose
              ].join("|"));
              console.log("java.results=" + [
                max,
                uuid,
                rect.__autojs6JavaHandle,
                rect.value.left,
                rect.value.bottom,
                width,
                height,
                contains,
                left,
                right,
                centerX,
                autojsMax,
                intersects,
                pi.toFixed(4),
                described.staticMethods.join(","),
                Object.isFrozen(described),
                released
              ].join("|"));
              console.log("java.requests=" + requests.slice(0, 15).map((request) => {
                const descriptor = request.args[0] || {};
                return request.method + ":" + (descriptor.className || descriptor.handle || descriptor.field);
              }).join("|"));
              console.log("java.permissions=" + requests.every((request) => request.permissions.indexOf("java_interop") >= 0));
              console.log("java.timeout=" + requests[0].timeoutMs);
              console.log("java.forwarded_denials=" + forwardedDenials + "|" + localRejections);
              console.log("java.codes=" + [
                runtimeCode,
                reflectionCode,
                processBuilderCode,
                systemLoadLibraryCode,
                defineClassCode,
                autojsDefineClassCode,
                emptyClassCode,
                badArgumentCode
              ].join("|"));
              console.log("java.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<node-java-interop-enabled.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
            javaInteropEnabled = true,
        )

        NodePluginTestHarness.assertSucceeded("Java interop stable fake bridge", result)
        assertTrue(result.stdout, result.stdout.contains("java.resolve=autojs6:java"))
        assertTrue(result.stdout, result.stdout.contains("java.frozen=true"))
        assertTrue(result.stdout, result.stdout.contains("java.rect_frozen=true"))
        assertTrue(result.stdout, result.stdout.contains("java.profile=true|stable|true"))
        assertTrue(result.stdout, result.stdout.contains("java.policy=host_provider|true|9|true|true"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "java.methods=" + List(24) { "function" }.joinToString("|"),
            ),
        )
        assertTrue(
            result.stdout,
            result.stdout.contains("java.results=9|00000000-0000-0000-0000-000000000002|java-fake-rect|1|4|2|2|true|1|3|2|8|true|3.1416|max|true|true"),
        )
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "java.requests=callStatic:java.lang.Math|callStatic:java.util.UUID|new:android.graphics.Rect|call:java-fake-rect|call:java-fake-rect|call:java-fake-rect|" +
                    "getField:java-fake-rect|getField:java-fake-rect|call:java-fake-rect|callStatic:java.lang.Math|callStatic:android.graphics.Rect|getStatic:java.lang.Math|" +
                    "type:java.lang.Math|release:java-fake-rect|callStatic:java.lang.Runtime",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("java.permissions=true"))
        assertTrue(result.stdout, result.stdout.contains("java.timeout=1234"))
        // Runtime / reflect / ProcessBuilder / System now reach the host; defineClass, an empty class
        // name and a non-JSON argument are still rejected before any request is made.
        assertTrue(result.stdout, result.stdout.contains("java.forwarded_denials=4|0"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "java.codes=ERR_AUTOJS6_JAVA_CLASS_DENIED|ERR_AUTOJS6_JAVA_REFLECTION_DENIED|ERR_AUTOJS6_JAVA_CLASS_DENIED|ERR_AUTOJS6_JAVA_CLASS_DENIED|" +
                    "ERR_AUTOJS6_JAVA_METHOD_DENIED|ERR_AUTOJS6_JAVA_METHOD_DENIED|ERR_AUTOJS6_JAVA_CLASS_DENIED|ERR_AUTOJS6_JAVA_METHOD_DENIED",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("java.pending=0"))
    }

    @Test
    fun javaModuleForwardsToTheHostAllowlist() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Java interop host allowlist")
        // java_interop is a critical capability: bare scripts do not get it, the project must declare it.
        val workDir = File(context.cacheDir, "node-java-interop-live-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Java interop work dir: $absolutePath" }
            File(this, "project.json").writeText(
                """{"type":"node","node":{"permissions":["java_interop"]}}""" + "\n",
                StandardCharsets.UTF_8,
            )
        }
        val result = NodePluginTestHarness.runPluginScript(
            workingDirectory = workDir,
            source = """
                "nodejs";
                (async function main() {
                  const java = require("java");
                  function codeOf(error) {
                    return String(error && (error.autojs6Code || error.code || error.name || ""));
                  }
                  async function failCode(promise) {
                    try {
                      await promise;
                      return "ok";
                    } catch (error) {
                      return codeOf(error);
                    }
                  }
                  const policy = java.policy;
                  console.log("java.live.policy=" + [
                    policy.enabled,
                    policy.limitsEnforcedBy,
                    policy.allowedClasses.length,
                    policy.allowedClasses.indexOf("android.os.Build${'$'}VERSION") >= 0,
                    policy.classes && policy.classes["android.graphics.Rect"] ? policy.classes["android.graphics.Rect"].constructable : "none"
                  ].join("|"));
                  const math = java.type("java.lang.Math");
                  const max = await math.callStatic("max", [3, 7]);
                  const pi = await java.getStatic("java.lang.Math", "PI");
                  const rect = await java.type("android.graphics.Rect").new([1, 2, 11, 22]);
                  const width = await rect.call("width");
                  const top = await rect.getField("top");
                  const other = await java.new("android.graphics.Rect", [5, 5, 30, 30]);
                  const intersects = await java.callStatic("android.graphics.Rect", "intersects", [rect, other]);
                  const seconds = await java.getStatic("java.util.concurrent.TimeUnit", "SECONDS");
                  const millis = await seconds.call("toMillis", [5]);
                  const sdk = await java.getStatic("android.os.Build${'$'}VERSION", "SDK_INT");
                  const described = await java.describe("java.lang.Math");
                  const released = await rect.release();
                  console.log("java.live.values=" + [
                    max,
                    pi.toFixed(4),
                    width,
                    top,
                    rect.className,
                    rect.value.left,
                    intersects,
                    seconds.value.name,
                    millis,
                    sdk >= 24,
                    described.staticMethods.indexOf("max") >= 0,
                    released
                  ].join("|"));
                  console.log("java.live.codes=" + [
                    await failCode(java.callStatic("java.lang.Runtime", "getRuntime")),
                    await failCode(java.describe("java.lang.reflect.Method")),
                    await failCode(math.callStatic("getClass")),
                    await failCode(java.callStatic("java.lang.Math", "floorDiv", [1, 0])),
                    await failCode(rect.call("width")),
                    await failCode(java.new("java.lang.Math"))
                  ].join("|"));
                })();
            """.trimIndent(),
            sourceName = "<node-java-interop-live.js>",
            timeoutMs = 15_000L,
        )
        workDir.deleteRecursively()
        NodePluginTestHarness.assertSucceeded("java interop live", result)
        val details = NodePluginTestHarness.describeResult("java interop live", result)
        assertTrue(details, result.stdout.contains("java.live.policy=true|host_provider|9|true|true"))
        assertTrue(details, result.stdout.contains("java.live.values=7|3.1416|10|2|android.graphics.Rect|1|true|SECONDS|5000|true|true|true"))
        assertTrue(
            details,
            result.stdout.contains(
                "java.live.codes=ERR_AUTOJS6_JAVA_CLASS_DENIED|ERR_AUTOJS6_JAVA_REFLECTION_DENIED|ERR_AUTOJS6_JAVA_METHOD_DENIED|" +
                    "ERR_AUTOJS6_JAVA_CALL_FAILED|ERR_AUTOJS6_JAVA_METHOD_DENIED|ERR_AUTOJS6_JAVA_METHOD_DENIED",
            ),
        )
    }

    private fun JSONArray.toList(): List<Any?> = List(length()) { index -> opt(index) }
}
