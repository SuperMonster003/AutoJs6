package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeShellBridgeInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun kotlinShellBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("shell", "exec"))
        assertFalse(NodeBridgeModules.supports("shell", "spawn"))
        assertFalse(NodeBridgeModules.supports("shell", "open"))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("shell", request.module)
            when (request.method) {
                "exec" -> NodeBridgeResponse.success(
                    request.id,
                    JSONObject().apply {
                        put("code", 0)
                        put("stdout", "ok\n")
                        put("stderr", "")
                    }.toString(),
                )
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "shell-exec-1",
                module = "shell",
                method = "exec",
                argsJson = """["echo ok",{"root":false,"timeoutMs":1000,"cwd":"","env":{"A":"B"}}]""",
                timeoutMs = 2000L,
                permissions = listOf("shell"),
            ),
        ) { response -> responses += response }

        assertEquals(1, provider.requests.size)
        assertEquals("exec", provider.requests.single().method)
        assertTrue("shell" in provider.requests.single().permissions)
        assertTrue(responses.single().ok)
        val result = JSONObject(responses.single().resultJson.orEmpty())
        assertEquals(0, result.getInt("code"))
        assertEquals("ok\n", result.getString("stdout"))
        assertEquals("", result.getString("stderr"))
    }

    @Test
    fun jsShellModuleUsesFakeTransportAndKeepsChildProcessDisabled() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node shell bridge focused regression")

        val workDir = File(context.cacheDir, "node-bridge-shell-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create shell bridge working directory: $absolutePath" }
        }
        File(workDir, "sub").apply {
            check(mkdirs()) { "Failed to create shell bridge cwd directory: $absolutePath" }
        }
        File(workDir, "scripts").apply {
            check(mkdirs()) { "Failed to create shell bridge scripts directory: $absolutePath" }
        }
        File(workDir, "node_modules/shell").apply {
            check(mkdirs()) { "Failed to create shell shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, exec: () => "shadow" };""")

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let result = { code: 0, stdout: "ok\n", stderr: "" };
                  if (request.module === "shell" && request.method === "exec" && request.args[1].root === true) {
                    result = { code: 0, stdout: "root-ok\n", stderr: "" };
                  } else if (request.module === "shell" && request.method === "exec" && request.args[0].indexOf("scripts/run.sh") >= 0) {
                    result = { code: 0, stdout: "file-ok\n", stderr: "" };
                  } else if (request.module === "shell" && request.method === "exec" && request.args[0] === "echo default") {
                    result = { code: 0, stdout: "default-ok\n", stderr: "" };
                  }
                  setImmediate(() => {
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result
                    }));
                  });
                }
              });

              const shell = require("shell");
              const resolved = require.resolve("shell");
              const normal = await shell.exec("echo ok", {
                timeoutMs: 1234,
                cwd: "sub",
                env: { AUTOJS6_SHELL_TEST: "ok" }
              });
              const root = await shell.exec("id", { root: true, timeoutMs: 1235 });
              const defaultTimeout = shell.setDefaultTimeout(4321);
              const access = shell.checkAccess({ root: true, shizuku: true });
              const file = await shell.execFile("scripts/run.sh", ["--dry-run", "value with space"], { timeoutMs: 1236 });
              const defaulted = await shell.exec("echo default");
              let cwdEscapeCode = "";
              try {
                await shell.exec("echo denied", { cwd: "../escape" });
              } catch (error) {
                cwdEscapeCode = error && (error.autojs6Code || error.code) || "";
              }
              let execFileEscapeCode = "";
              try {
                await shell.execFile("../escape.sh");
              } catch (error) {
                execFileEscapeCode = error && (error.autojs6Code || error.code) || "";
              }
              let execFileArgsCode = "";
              try {
                await shell.execFile("scripts/run.sh", [1]);
              } catch (error) {
                execFileArgsCode = error && (error.autojs6Code || error.code) || "";
              }
              let defaultTimeoutCode = "";
              try {
                shell.setDefaultTimeout(0);
              } catch (error) {
                defaultTimeoutCode = error && (error.autojs6Code || error.code) || "";
              }
              let childProcessCode = "";
              try {
                require("child_process");
              } catch (error) {
                childProcessCode = error && (error.autojs6Code || error.code) || "";
              }
              console.log("shell.resolve=" + resolved);
              console.log("shell.shadow=" + (shell.shadow === true));
              console.log("shell.frozen=" + Object.isFrozen(shell));
              console.log("shell.methods=" + [typeof shell.exec, typeof shell.execFile, typeof shell.checkAccess, typeof shell.setDefaultTimeout, typeof shell.spawn, typeof shell.open].join("|"));
              console.log("shell.normal=" + [normal.code, normal.stdout, normal.stderr].join("|"));
              console.log("shell.root=" + [root.code, root.stdout, root.stderr].join("|"));
              console.log("shell.execFile=" + [file.code, file.stdout, file.stderr].join("|"));
              console.log("shell.defaulted=" + [defaulted.code, defaulted.stdout, defaulted.stderr, defaultTimeout].join("|"));
              console.log("shell.access=" + [access.shell, access.root, access.shizuku, access.missingCapabilities.join(",")].join("|"));
              console.log("shell.requests=" + requests.map(r => r.method).join("|"));
              console.log("shell.command=" + requests[0].args[0]);
              console.log("shell.execFile.command=" + requests[2].args[0]);
              console.log("shell.cwd=" + requests[0].args[1].cwd);
              console.log("shell.env=" + requests[0].args[1].env.AUTOJS6_SHELL_TEST);
              console.log("shell.root.flag=" + requests[1].args[1].root);
              console.log("shell.timeouts=" + requests.map(r => r.args[1].timeoutMs + "/" + r.timeoutMs).join("|"));
              console.log("shell.permissions=" + requests.map(r => r.permissions.join(",")).join("|"));
              console.log("shell.cwd_escape=" + cwdEscapeCode);
              console.log("shell.execFile_escape=" + execFileEscapeCode);
              console.log("shell.execFile_args=" + execFileArgsCode);
              console.log("shell.default_timeout_invalid=" + defaultTimeoutCode);
              console.log("shell.child_process=" + childProcessCode);
              console.log("shell.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-shell-fake-source.js>",
            workingDirectory = workDir,
        )

        NodePluginTestHarness.assertSucceeded("shell fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.resolve=autojs6:shell")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.shadow=false")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.frozen=true")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.methods=function|function|function|function|undefined|undefined")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.normal=0|ok")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.root=0|root-ok")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.execFile=0|file-ok")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.defaulted=0|default-ok")
        assertTrue(result.stdout, result.stdout.contains("shell.access=true|true|false|"))
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.requests=exec|exec|exec|exec")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.command=echo ok")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.execFile.command='scripts/run.sh' '--dry-run' 'value with space'")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.cwd=sub")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.env=ok")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.root.flag=true")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.timeouts=1234/2234|1235/2235|1236/2236|4321/5321")
        assertTrue(result.stdout, result.stdout.contains("shell.permissions=shell|shell,shell.root|shell|shell"))
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.cwd_escape=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.execFile_escape=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.execFile_args=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.default_timeout_invalid=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.child_process=ERR_AUTOJS6_BUILTIN_DISABLED")
        NodePluginTestHarness.assertStdoutContains("shell fake bridge", result, "shell.pending=0")
    }

    @Test
    fun jsShellModuleIsChildProcessAlternativeAndRequiresDeclaredCapability() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node shell child_process alternative policy")

        val shellManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("shell"))
        val allowed = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result: { code: 0, stdout: "allowed\n", stderr: "" }
                  })));
                }
              });

              let childProcessCode = "";
              try {
                require("child_process").exec("echo denied");
              } catch (error) {
                childProcessCode = String(error && (error.autojs6Code || error.code || ""));
              }

              const shell = require("shell");
              const access = shell.checkAccess({ root: true, shizuku: true });
              const result = await shell.exec("echo allowed", { timeoutMs: 1234 });
              let rootDeniedCode = "";
              let rootMissing = "";
              try {
                await shell.exec("id", { root: true, timeoutMs: 1234 });
              } catch (error) {
                rootDeniedCode = String(error && (error.autojs6Code || error.code || ""));
                rootMissing = Array.isArray(error && error.missingCapabilities)
                  ? error.missingCapabilities.join("|")
                  : "";
              }

              console.log("shell.policy.child_process=" + childProcessCode);
              console.log("shell.policy.exec=" + result.code + "/" + result.stdout.trim() + "/" + requests.length);
              console.log("shell.policy.access=" + [access.shell, access.root, access.shizuku, access.missingCapabilities.join("|")].join("|"));
              console.log("shell.policy.permissions=" + requests[0].permissions.join("|"));
              console.log("shell.policy.root_denied=" + rootDeniedCode + "/" + rootMissing + "/" + requests.length);
              console.log("shell.policy.methods=" + [typeof shell.exec, typeof shell.execFile, typeof shell.checkAccess, typeof shell.setDefaultTimeout, typeof shell.spawn, typeof shell.open].join("|"));
              console.log("shell.policy.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-shell-child-process-policy.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to shellManifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("shell child_process alternative declared capability", allowed)
        assertTrue(allowed.stdout.contains("shell.policy.child_process=ERR_AUTOJS6_BUILTIN_DISABLED"))
        assertTrue(allowed.stdout.contains("shell.policy.exec=0/allowed/1"))
        assertTrue(allowed.stdout.contains("shell.policy.access=true|false|false|shell.root|shell.shizuku"))
        assertTrue(allowed.stdout.contains("shell.policy.permissions=shell"))
        assertTrue(
            allowed.stdout.contains(
                "shell.policy.root_denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}/shell.root/1",
            ),
        )
        assertTrue(allowed.stdout.contains("shell.policy.methods=function|function|function|function|undefined|undefined"))
        assertTrue(allowed.stdout.contains("shell.policy.pending=0"))

        NodePluginTestHarness.settlePluginRuntime()

        val toastOnlyManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("toast"))
        val denied = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const profile = require("autojs6:profile");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: false,
                    error: {
                      name: "Error",
                      message: "shell request unexpectedly reached transport",
                      code: "ERR_TEST_SHELL_TRANSPORT_REACHED",
                      module: request.module,
                      method: request.method
                    }
                  })));
                }
              });

              let deniedCode = "";
              let missing = "";
              try {
                await require("shell").exec("echo denied", { timeoutMs: 1000 });
              } catch (error) {
                deniedCode = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities)
                  ? error.missingCapabilities.join("|")
                  : "";
              }

              console.log("shell.policy.manifest_denied=" + deniedCode + "/" + missing + "/" + requests.length);
              console.log("shell.policy.manifest_report=" + profile.bridgePermissions.enforced + "/" + profile.bridgePermissions.normalizedPermissions.join("|"));
              console.log("shell.policy.manifest_pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-shell-missing-capability-policy.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to toastOnlyManifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("shell child_process alternative missing capability", denied)
        assertTrue(
            denied.stdout.contains(
                "shell.policy.manifest_denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}/shell/0",
            ),
        )
        assertTrue(denied.stdout.contains("shell.policy.manifest_report=true/toast"))
        assertTrue(denied.stdout.contains("shell.policy.manifest_pending=0"))
    }

    @Test
    fun abortSignalCancelsInFlightShellExecAndClearsPendingBridgeCall() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node shell AbortSignal cancellation")

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const shell = require("shell");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  requests.push(JSON.parse(message));
                }
              });

              const controller = new AbortController();
              const shellPromise = shell.exec("sleep 30", {
                timeoutMs: 10000,
                signal: controller.signal
              });
              const requestCountAtStart = requests.length;
              controller.abort();

              let code = "";
              let moduleName = "";
              let methodName = "";
              try {
                await shellPromise;
              } catch (error) {
                code = String(error && (error.autojs6Code || error.code || ""));
                moduleName = String(error && error.module || "");
                methodName = String(error && error.method || "");
              }

              console.log("shell.abort.request_count=" + requestCountAtStart);
              console.log("shell.abort.code=" + code);
              console.log("shell.abort.module_method=" + moduleName + "/" + methodName);
              console.log("shell.abort.pending=" + bridge.__test.pendingCount());
              console.log("shell.abort.timeout=" + requests[0].args[1].timeoutMs + "/" + requests[0].timeoutMs);
              console.log("shell.abort.permissions=" + requests[0].permissions.join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-shell-abort-signal.js>",
        )

        NodePluginTestHarness.assertSucceeded("shell AbortSignal cancellation", result)
        assertTrue(result.stdout.contains("shell.abort.request_count=1"))
        assertTrue(result.stdout.contains("shell.abort.code=${NodeRuntimeErrorCodes.BRIDGE_CANCELLED}"))
        assertTrue(result.stdout.contains("shell.abort.module_method=shell/exec"))
        assertTrue(result.stdout.contains("shell.abort.pending=0"))
        assertTrue(result.stdout.contains("shell.abort.timeout=10000/11000"))
        assertTrue(result.stdout.contains("shell.abort.permissions=shell"))
    }

    @Test
    fun androidShellProviderRunsNonRootCommandAndEnforcesPolicy() {
        val workDir = File(context.cacheDir, "node-bridge-shell-provider").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create shell provider working directory: $absolutePath" }
        }
        File(workDir, "sub").apply {
            check(mkdirs()) { "Failed to create shell provider cwd directory: $absolutePath" }
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, cwd = workDir.absolutePath),
        )

        val echo = dispatch(provider, """["echo ${"$"}AUTOJS6_SHELL_TEST",{"timeoutMs":3000,"cwd":"sub","env":{"AUTOJS6_SHELL_TEST":"provider-ok"}}]""")
        assertTrue(echo.ok)
        JSONObject(echo.resultJson.orEmpty()).also { result ->
            assertEquals(0, result.getInt("code"))
            assertEquals("provider-ok", result.getString("stdout").trim())
            assertEquals("", result.getString("stderr"))
        }

        val nonZero = dispatch(provider, """["exit 7",{"timeoutMs":3000}]""")
        assertTrue(nonZero.ok)
        assertEquals(7, JSONObject(nonZero.resultJson.orEmpty()).getInt("code"))

        val timeout = dispatch(provider, """["sleep 2",{"timeoutMs":100}]""")
        assertTrue(timeout.ok)
        JSONObject(timeout.resultJson.orEmpty()).also { result ->
            assertEquals(124, result.getInt("code"))
            assertTrue(result.getString("stderr").contains("timed out"))
        }

        val deniedCwd = dispatch(provider, """["echo denied",{"cwd":"../escape","timeoutMs":1000}]""")
        assertFalse(deniedCwd.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, deniedCwd.error?.code)
        assertEquals("shell", deniedCwd.error?.module)
        assertEquals("exec", deniedCwd.error?.method)
    }

    private fun dispatch(provider: NodeBridgeCapabilityProvider, argsJson: String): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "shell-provider-${System.nanoTime()}",
                module = "shell",
                method = "exec",
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("shell"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for shell provider response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing shell provider response" }
    }
}
