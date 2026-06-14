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
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
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
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node shell bridge focused regression")

        val workDir = File(context.cacheDir, "node-bridge-shell-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create shell bridge working directory: $absolutePath" }
        }
        File(workDir, "sub").apply {
            check(mkdirs()) { "Failed to create shell bridge cwd directory: $absolutePath" }
        }
        File(workDir, "node_modules/shell").apply {
            check(mkdirs()) { "Failed to create shell shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, exec: () => "shadow" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
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
              let cwdEscapeCode = "";
              try {
                await shell.exec("echo denied", { cwd: "../escape" });
              } catch (error) {
                cwdEscapeCode = error && (error.autojs6Code || error.code) || "";
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
              console.log("shell.methods=" + [typeof shell.exec, typeof shell.spawn, typeof shell.open].join("|"));
              console.log("shell.normal=" + [normal.code, normal.stdout, normal.stderr].join("|"));
              console.log("shell.root=" + [root.code, root.stdout, root.stderr].join("|"));
              console.log("shell.requests=" + requests.map(r => r.method).join("|"));
              console.log("shell.command=" + requests[0].args[0]);
              console.log("shell.cwd=" + requests[0].args[1].cwd);
              console.log("shell.env=" + requests[0].args[1].env.AUTOJS6_SHELL_TEST);
              console.log("shell.root.flag=" + requests[1].args[1].root);
              console.log("shell.timeouts=" + requests.map(r => r.args[1].timeoutMs + "/" + r.timeoutMs).join("|"));
              console.log("shell.permissions=" + requests[0].permissions.join(",") + "|" + requests[1].permissions.join(","));
              console.log("shell.cwd_escape=" + cwdEscapeCode);
              console.log("shell.child_process=" + childProcessCode);
              console.log("shell.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-shell-fake-source.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("shell fake bridge", result)
        assertTrue(result.stdout.contains("shell.resolve=shell"))
        assertTrue(result.stdout.contains("shell.shadow=false"))
        assertTrue(result.stdout.contains("shell.frozen=true"))
        assertTrue(result.stdout.contains("shell.methods=function|undefined|undefined"))
        assertTrue(result.stdout.contains("shell.normal=0|ok"))
        assertTrue(result.stdout.contains("shell.root=0|root-ok"))
        assertTrue(result.stdout.contains("shell.requests=exec|exec"))
        assertTrue(result.stdout.contains("shell.command=echo ok"))
        assertTrue(result.stdout.contains("shell.cwd=sub"))
        assertTrue(result.stdout.contains("shell.env=ok"))
        assertTrue(result.stdout.contains("shell.root.flag=true"))
        assertTrue(result.stdout.contains("shell.timeouts=1234/2234|1235/2235"))
        assertTrue(result.stdout.contains("shell.permissions=shell|shell,shell:root"))
        assertTrue(result.stdout.contains("shell.cwd_escape=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"))
        assertTrue(result.stdout.contains("shell.child_process=ERR_AUTOJS6_BUILTIN_DISABLED"))
        assertTrue(result.stdout.contains("shell.pending=0"))
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
