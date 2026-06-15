package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWebSocketBridgeInstrumentationTest {

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
    fun websocketMvpUsesControlledBridgeAndKeepsRawSocketsDisabled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("websocket bridge MVP")
        assertFalse(BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED)
        assertTrue(NodeBridgeModules.supports("websocket", "connect"))
        assertTrue(NodeBridgeModules.supports("websocket", "send"))
        assertTrue(NodeBridgeModules.supports("websocket", "close"))
        assertTrue(NodeBridgeModules.supports("websocket", "drainEvents"))
        assertFalse(NodeBridgeModules.supports("websocket", "listen"))

        val websocketFlag = NodeRuntimeCapabilities.featureFlagsById["websocket"]
        assertEquals("experimental", websocketFlag?.status)
        assertEquals("high", websocketFlag?.securityLevel)
        assertFalse(websocketFlag?.defaultEnabled ?: true)

        val workDir = File(context.cacheDir, "node-websocket-bridge-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create websocket bridge work dir: $absolutePath" }
        }
        File(workDir, "node_modules/websocket").apply {
            mkdirs()
            File(this, "index.js").writeText(
                """module.exports = { shadowed: true };""" + "\n",
                StandardCharsets.UTF_8,
            )
        }

        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const bridge = require("autojs6:bridge");
                      const websocket = require("websocket");
                      const requests = [];
                      const sessions = new Map();
                      let sequence = 0;

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }
                      function sleep(ms) {
                        return new Promise((resolve) => setTimeout(resolve, ms));
                      }
                      async function waitFor(predicate, label) {
                        for (let i = 0; i < 80; i += 1) {
                          if (predicate()) return;
                          await sleep(5);
                        }
                        throw new Error("timeout waiting for " + label);
                      }
                      function respond(request, ok, result, error) {
                        setImmediate(() => {
                          bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok,
                            result,
                            error
                          }));
                        });
                      }
                      function sessionFor(id) {
                        if (!sessions.has(id)) {
                          sessions.set(id, { events: [] });
                        }
                        return sessions.get(id);
                      }

                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          if (request.module !== "websocket") {
                            respond(request, false, null, {
                              code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                              message: "unexpected module"
                            });
                            return;
                          }
                          const payload = request.args[0] || {};
                          if (request.method === "connect") {
                            if (payload.url.indexOf("/timeout") >= 0 || payload.url.indexOf("/destroy") >= 0) {
                              return;
                            }
                            sequence += 1;
                            const id = "fake-ws-" + sequence;
                            const session = sessionFor(id);
                            session.events.push({ type: "open" });
                            if (payload.url.indexOf("/socket") >= 0) {
                              session.events.push({ type: "message", message: { type: "text", text: "server-ready" } });
                            }
                            respond(request, true, { id, url: payload.url, readyState: "open", maxMessageBytes: payload.maxMessageBytes });
                            return;
                          }
                          if (request.method === "drainEvents") {
                            const session = sessionFor(payload.id);
                            const events = session.events.splice(0);
                            respond(request, true, events);
                            return;
                          }
                          if (request.method === "send") {
                            const session = sessionFor(payload.id);
                            session.events.push({ type: "message", message: { type: "text", text: "echo:" + (payload.text || payload.dataBase64 || "") } });
                            respond(request, true, true);
                            return;
                          }
                          if (request.method === "close") {
                            sessionFor(payload.id).events.push({ type: "close", code: payload.code || 1000, reason: payload.reason || "" });
                            respond(request, true, null);
                            return;
                          }
                          respond(request, false, null, {
                            code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                            message: "unexpected method"
                          });
                        }
                      });

                      const profile = require("autojs6:profile");
                      const resolved = require.resolve("websocket");
                      const connection = await websocket.connect("wss://example.invalid/socket", {
                        timeoutMs: 1000,
                        maxMessageBytes: 64
                      });
                      const firstMessage = await new Promise((resolve) => connection.on("message", resolve));
                      const echoPromise = new Promise((resolve) => connection.on("message", (message) => {
                        if (message.text && message.text.indexOf("echo:") === 0) resolve(message);
                      }));
                      await connection.send("hello");
                      const echoMessage = await echoPromise;
                      await connection.close(1000, "done");

                      const a = await websocket.connect("wss://example.invalid/a", { timeoutMs: 1000 });
                      const b = await websocket.connect("wss://example.invalid/b", { timeoutMs: 1000 });
                      let concurrentCode = "";
                      try {
                        await websocket.connect("wss://example.invalid/c", { timeoutMs: 1000 });
                      } catch (error) {
                        concurrentCode = codeOf(error);
                      }
                      await a.close();
                      await b.close();

                      let schemeCode = "";
                      try {
                        await websocket.connect("https://example.invalid/not-websocket", { timeoutMs: 1000 });
                      } catch (error) {
                        schemeCode = codeOf(error);
                      }

                      let largeCode = "";
                      const tiny = await websocket.connect("wss://example.invalid/tiny", { timeoutMs: 1000, maxMessageBytes: 3 });
                      try {
                        await tiny.send("abcd");
                      } catch (error) {
                        largeCode = codeOf(error);
                      }
                      await tiny.close();

                      let timeoutCode = "";
                      try {
                        await websocket.connect("wss://example.invalid/timeout", { timeoutMs: 25 });
                      } catch (error) {
                        timeoutCode = codeOf(error);
                      }

                      let rawNetCode = "";
                      try {
                        require("net").connect(80, "127.0.0.1");
                      } catch (error) {
                        rawNetCode = codeOf(error);
                      }
                      let rawTlsCode = "";
                      try {
                        require("tls").connect(443, "127.0.0.1");
                      } catch (error) {
                        rawTlsCode = codeOf(error);
                      }

                      let destroyCode = "";
                      const destroyPromise = websocket.connect("wss://example.invalid/destroy", { timeoutMs: 1000 }).catch(codeOf);
                      await waitFor(() => requests.some((request) => request.method === "connect" && request.args[0].url.indexOf("/destroy") >= 0), "destroy connect");
                      bridge.__test.destroy("websocket service destroy");
                      destroyCode = await destroyPromise;

                      const diagnostics = websocket.diagnostics();
                      console.log("websocket.profile=" + [
                        profile.featureFlags.websocket.enabled,
                        profile.featureFlags.websocket.defaultEnabled,
                        profile.featureFlags.websocket.status,
                        profile.highRiskCapabilities.webSocket
                      ].join("|"));
                      console.log("websocket.resolve=" + resolved);
                      console.log("websocket.shadow=" + (websocket.shadowed === true));
                      console.log("websocket.frozen=" + Object.isFrozen(websocket));
                      console.log("websocket.policy=" + [
                        websocket.policy.maxConnections,
                        websocket.policy.defaultTimeoutMs,
                        websocket.policy.defaultMaxMessageBytes
                      ].join("|"));
                      console.log("websocket.messages=" + [firstMessage.text, echoMessage.text].join("|"));
                      console.log("websocket.codes=" + [
                        concurrentCode,
                        schemeCode,
                        largeCode,
                        timeoutCode,
                        rawNetCode,
                        rawTlsCode,
                        destroyCode
                      ].join("|"));
                      console.log("websocket.diagnostics=" + [
                        diagnostics.activeConnectionCount,
                        diagnostics.maxActiveConnectionCount,
                        diagnostics.timeoutCount,
                        diagnostics.rejectedByPolicyCount
                      ].join("|"));
                      console.log("websocket.pending=" + bridge.__test.pendingCount());
                    })();
                """.trimIndent(),
                sourceName = "<node-websocket-bridge-mvp.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("websocket bridge MVP", result)
            assertTrue(result.stdout, result.stdout.contains("websocket.profile=false|false|experimental|false"))
            assertTrue(result.stdout, result.stdout.contains("websocket.resolve=websocket"))
            assertTrue(result.stdout, result.stdout.contains("websocket.shadow=false"))
            assertTrue(result.stdout, result.stdout.contains("websocket.frozen=true"))
            assertTrue(result.stdout, result.stdout.contains("websocket.policy=2|10000|65536"))
            assertTrue(result.stdout, result.stdout.contains("websocket.messages=server-ready|echo:hello"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "websocket.codes=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|ERR_AUTOJS6_NETWORK_TIMEOUT|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("websocket.diagnostics=0|2|1|3"))
            assertTrue(result.stdout, result.stdout.contains("websocket.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun androidWebSocketProviderStaysDisabledInNormalBuilds() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val responses = mutableListOf<NodeBridgeResponse>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "websocket-disabled-${System.nanoTime()}",
                module = "websocket",
                method = "connect",
                argsJson = """[{"url":"wss://example.invalid/socket","timeoutMs":1000,"maxMessageBytes":1024}]""",
                timeoutMs = 1000L,
                permissions = listOf("network"),
            ),
        ) { response -> responses += response }

        assertEquals(1, responses.size)
        assertFalse(responses.single().ok)
        assertEquals(NodeRuntimeErrorCodes.NETWORK_DISABLED, responses.single().error?.code)
        assertEquals("websocket", responses.single().error?.module)
        assertEquals("connect", responses.single().error?.method)
    }
}
