package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        assumeFalse(
            "The fake-provider WebSocket MVP regression is only meaningful when autojs.nodejs.network.stable=false.",
            BuildConfig.NODEJS_NETWORK_ENABLED,
        )
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("websocket bridge MVP")
        assertTrue(NodeBridgeModules.supports("websocket", "connect"))
        assertTrue(NodeBridgeModules.supports("websocket", "send"))
        assertTrue(NodeBridgeModules.supports("websocket", "close"))
        assertTrue(NodeBridgeModules.supports("websocket", "drainEvents"))
        assertFalse(NodeBridgeModules.supports("websocket", "listen"))

        val websocketFlag = NodeRuntimeCapabilities.featureFlagsById["websocket"]
        assertEquals("stable", websocketFlag?.status)
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
                      function headerValue(headers, name) {
                        const lower = String(name).toLowerCase();
                        const found = (headers || []).find((entry) => String(entry[0]).toLowerCase() === lower);
                        return found ? String(found[1]) : "";
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
                            if (payload.text === "trigger-error") {
                              session.events.push({ type: "error", error: { code: "ERR_FAKE_WS", message: "fake websocket error" } });
                            } else if (payload.dataBase64) {
                              session.events.push({ type: "message", message: { type: "binary", dataBase64: payload.dataBase64 } });
                            } else {
                              session.events.push({ type: "message", message: { type: "text", text: "echo:" + (payload.text || "") } });
                            }
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
                        headers: { "x-mode": "bridge" },
                        protocols: ["json", "events"],
                        timeoutMs: 1000,
                        maxMessageBytes: 64,
                        maxQueueSize: 4
                      });
                      const socketConnect = requests.find((request) =>
                        request.method === "connect" && request.args[0].url.indexOf("/socket") >= 0
                      ).args[0];
                      const propertyMessages = [];
                      const propertyErrors = [];
                      const closeEvents = [];
                      const openEvents = [];
                      let offCount = 0;
                      connection.onopen = () => {
                        openEvents.push("prop");
                      };
                      connection.onmessage = (message) => {
                        propertyMessages.push(message.type + ":" + (message.text || message.dataBase64 || ""));
                      };
                      connection.onerror = (error) => {
                        propertyErrors.push(codeOf(error));
                      };
                      connection.onclose = (event) => {
                        closeEvents.push("prop:" + event.code + ":" + event.reason);
                      };
                      const openPromise = new Promise((resolve) => connection.addEventListener("open", () => {
                        openEvents.push("listener");
                        resolve(undefined);
                      }));
                      const firstMessagePromise = new Promise((resolve) => connection.on("message", resolve));
                      const firstMessage = await firstMessagePromise;
                      await openPromise;
                      const removedListener = () => { offCount += 1; };
                      connection.addEventListener("message", removedListener);
                      connection.removeEventListener("message", removedListener);
                      const echoPromise = new Promise((resolve) => connection.on("message", (message) => {
                        if (message.text && message.text.indexOf("echo:") === 0) resolve(message);
                      }));
                      await connection.send("hello");
                      const echoMessage = await echoPromise;
                      const binaryPromise = new Promise((resolve) => connection.on("message", (message) => {
                        if (message.dataBase64) resolve(message);
                      }));
                      await connection.send(new Uint8Array([1, 2, 3]));
                      const binaryMessage = await binaryPromise;
                      const errorPromise = new Promise((resolve) => connection.on("error", (error) => resolve(codeOf(error))));
                      await connection.send("trigger-error");
                      const errorCode = await errorPromise;
                      const closePromise = new Promise((resolve) => connection.on("close", (event) => {
                        closeEvents.push("listener:" + event.code + ":" + event.reason);
                        resolve(event);
                      }));
                      await connection.close(1000, "done");
                      await closePromise;

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
                      console.log("websocket.connect_options=" + [
                        headerValue(socketConnect.headers, "x-mode"),
                        socketConnect.protocols.join(","),
                        socketConnect.maxQueueSize,
                        socketConnect.maxMessageBytes
                      ].join("|"));
                      console.log("websocket.messages=" + [firstMessage.text, echoMessage.text].join("|"));
                      console.log("websocket.events=" + [
                        openEvents.join(","),
                        propertyMessages.join(","),
                        binaryMessage.dataBase64,
                        propertyErrors.join(","),
                        errorCode,
                        closeEvents.join(","),
                        offCount
                      ].join("|"));
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
            assertTrue(result.stdout, result.stdout.contains("websocket.profile=false|false|stable|false"))
            assertTrue(result.stdout, result.stdout.contains("websocket.resolve=websocket"))
            assertTrue(result.stdout, result.stdout.contains("websocket.shadow=false"))
            assertTrue(result.stdout, result.stdout.contains("websocket.frozen=true"))
            assertTrue(result.stdout, result.stdout.contains("websocket.policy=2|10000|65536"))
            assertTrue(result.stdout, result.stdout.contains("websocket.connect_options=bridge|json,events|4|64"))
            assertTrue(result.stdout, result.stdout.contains("websocket.messages=server-ready|echo:hello"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "websocket.events=prop,listener|text:server-ready,text:echo:hello,binary:AQID|AQID|ERR_FAKE_WS|ERR_FAKE_WS|prop:1000:done,listener:1000:done|0",
                ),
            )
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
    fun jsWebSocketUsesAndroidLiveProviderWhenNetworkEnabled() {
        assumeTrue(
            "Build with -Pautojs.nodejs.network.stable=true to run the live controlled WebSocket provider smoke.",
            BuildConfig.NODEJS_NETWORK_ENABLED,
        )
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node controlled WebSocket live provider smoke")

        ControlledWebSocketLoopbackServer().use { server ->
            val baseUrl = server.baseUrl
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const websocket = require("websocket");
                      const base = "$baseUrl";

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }
                      function waitForEvent(connection, eventName, label) {
                        return new Promise((resolve, reject) => {
                          const timer = setTimeout(() => {
                            connection.off(eventName, listener);
                            reject(new Error("timeout waiting for " + label));
                          }, 5000);
                          function listener(event) {
                            clearTimeout(timer);
                            connection.off(eventName, listener);
                            resolve(event);
                          }
                          connection.on(eventName, listener);
                        });
                      }
                      function waitForMessage(connection, predicate, label) {
                        return new Promise((resolve, reject) => {
                          const timer = setTimeout(() => {
                            connection.off("message", listener);
                            reject(new Error("timeout waiting for " + label));
                          }, 5000);
                          function listener(message) {
                            if (predicate(message)) {
                              clearTimeout(timer);
                              connection.off("message", listener);
                              resolve(message);
                            }
                          }
                          connection.on("message", listener);
                        });
                      }

                      const connection = await websocket.connect(base + "/socket?mode=live", {
                        headers: { "x-test": "live" },
                        protocols: ["json"],
                        timeoutMs: 2000,
                        maxMessageBytes: 64,
                        maxQueueSize: 8
                      });
                      const ready = await waitForMessage(connection, (message) =>
                        !!message.text && message.text.indexOf("server-ready:") === 0,
                        "ready message"
                      );

                      const echoPromise = waitForMessage(connection, (message) =>
                        message.text === "echo:hello",
                        "text echo"
                      );
                      await connection.send("hello");
                      const echo = await echoPromise;

                      const binaryPromise = waitForMessage(connection, (message) =>
                        message.dataBase64 === "AQID",
                        "binary echo"
                      );
                      await connection.send(new Uint8Array([1, 2, 3]));
                      const binary = await binaryPromise;

                      const closePromise = waitForEvent(connection, "close", "normal close");
                      await connection.close(1000, "done");
                      const close = await closePromise;

                      const large = await websocket.connect(base + "/large", {
                        timeoutMs: 2000,
                        maxMessageBytes: 3
                      });
                      const largeErrorPromise = waitForEvent(large, "error", "large message error").then(codeOf);
                      const largeClosePromise = waitForEvent(large, "close", "large message close");
                      const largeCode = await largeErrorPromise;
                      await largeClosePromise;

                      let timeoutCode = "";
                      try {
                        await websocket.connect(base + "/slow", { timeoutMs: 100 });
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

                      const diagnostics = websocket.diagnostics();
                      console.log("live.websocket.ready=" + [ready.text, connection.maxMessageBytes].join("|"));
                      console.log("live.websocket.echo=" + [echo.text, binary.dataBase64].join("|"));
                      console.log("live.websocket.close=" + [close.code, close.reason].join("|"));
                      console.log("live.websocket.codes=" + [largeCode, timeoutCode, rawNetCode, rawTlsCode].join("|"));
                      console.log("live.websocket.diagnostics=" + [
                        diagnostics.activeConnectionCount,
                        diagnostics.messageCount,
                        diagnostics.timeoutCount,
                        diagnostics.rejectedByPolicyCount
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-websocket-live-provider.js>",
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("websocket live provider", result)
            val resultDetails = NodeEmbeddedTestHarness.describeResult("websocket live provider", result)
            assertTrue(resultDetails, result.stdout.contains("live.websocket.ready=server-ready:live:json:mode=live|64"))
            assertTrue(resultDetails, result.stdout.contains("live.websocket.echo=echo:hello|AQID"))
            assertTrue(resultDetails, result.stdout.contains("live.websocket.close=1000|done"))
            assertTrue(
                resultDetails,
                result.stdout.contains(
                    "live.websocket.codes=ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|ERR_AUTOJS6_NETWORK_TIMEOUT|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED",
                ),
            )
            assertTrue(resultDetails, result.stdout.contains("live.websocket.diagnostics=0|3|1|0"))
            result.nativeValues["embedded_script.bridge_live_enabled"]?.let { assertEquals("true", it) }
            result.nativeValues["embedded_script.bridge_live_dispatch_count"]?.toIntOrNull()?.let {
                assertTrue(resultDetails, it >= 10)
            }
            result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"]?.let { assertEquals("1", it) }
            result.nativeValues["embedded_script.websocket_active_connection_count"]?.let { assertEquals("0", it) }
        }
    }

    @Test
    fun androidWebSocketProviderClosesActiveLiveConnectionOnDestroy() {
        assumeTrue(
            "Build with -Pautojs.nodejs.network.stable=true to run the live controlled WebSocket destroy smoke.",
            BuildConfig.NODEJS_NETWORK_ENABLED,
        )

        ControlledWebSocketLoopbackServer().use { server ->
            val provider = AndroidNodeBridgeCapabilityProvider(context)
            val callbackLatch = CountDownLatch(1)
            var response: NodeBridgeResponse? = null

            provider.dispatch(
                NodeBridgeRequest(
                    id = "websocket-live-destroy-${System.nanoTime()}",
                    module = "websocket",
                    method = "connect",
                    argsJson = JSONArray().put(
                        JSONObject().apply {
                            put("url", "${server.baseUrl}/hold")
                            put("timeoutMs", 2_000)
                            put("maxMessageBytes", 1024)
                            put("maxQueueSize", 8)
                        },
                    ).toString(),
                    timeoutMs = 2_000L,
                    permissions = listOf("network"),
                ),
            ) { bridgeResponse ->
                response = bridgeResponse
                callbackLatch.countDown()
            }

            assertTrue("live WebSocket connect did not complete", callbackLatch.await(5, TimeUnit.SECONDS))
            val success = response ?: error("Missing WebSocket destroy response")
            assertTrue(success.error?.toString().orEmpty(), success.ok)
            assertTrue("hold WebSocket did not open on loopback server", server.awaitHoldOpen())

            provider.destroy(NodeBridgeError.processDead("websocket live destroy test"))
            assertTrue("provider destroy did not close the loopback WebSocket", server.awaitHoldClose())
            provider.nativeDiagnostics().also { diagnostics ->
                assertEquals("0", diagnostics["embedded_script.websocket_active_connection_count"])
                assertEquals("1", diagnostics["embedded_script.websocket_service_destroy_close_count"])
            }
        }
    }

    @Test
    fun androidWebSocketProviderStaysDisabledInNormalBuilds() {
        assumeFalse(
            "This default-denial regression is only meaningful when autojs.nodejs.network.stable=false.",
            BuildConfig.NODEJS_NETWORK_ENABLED,
        )

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

    private class ControlledWebSocketLoopbackServer : AutoCloseable {

        private val executor = Executors.newCachedThreadPool()
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val holdOpen = CountDownLatch(1)
        private val holdClose = CountDownLatch(1)

        @Volatile
        private var running = true

        val baseUrl: String = "ws://127.0.0.1:${serverSocket.localPort}"

        init {
            executor.execute {
                while (running) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (error: Exception) {
                        if (running) {
                            throw error
                        }
                        return@execute
                    }
                    executor.execute {
                        socket.use { accepted ->
                            runCatching { handle(accepted) }
                        }
                    }
                }
            }
        }

        fun awaitHoldOpen(): Boolean = holdOpen.await(5, TimeUnit.SECONDS)

        fun awaitHoldClose(): Boolean = holdClose.await(5, TimeUnit.SECONDS)

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }

        private fun handle(socket: Socket) {
            socket.soTimeout = 5_000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 2) {
                return
            }
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: return
                if (line.isEmpty()) {
                    break
                }
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }

            val uri = URI(parts[1])
            if (uri.path == "/slow") {
                Thread.sleep(2_000)
                return
            }

            writeHandshake(output, headers)
            when (uri.path) {
                "/socket" -> runEchoSession(input, output, headers, uri.rawQuery.orEmpty())
                "/large" -> runLargeMessageSession(input, output)
                "/hold" -> runHoldSession(input, output)
                else -> writeCloseFrame(output, 1008, "unsupported")
            }
        }

        private fun runEchoSession(input: InputStream, output: OutputStream, headers: Map<String, String>, query: String) {
            val protocol = headers["sec-websocket-protocol"]?.split(',')?.firstOrNull()?.trim().orEmpty()
            val ready = listOf(
                "server-ready",
                headers["x-test"].orEmpty(),
                protocol,
                query,
            ).joinToString(":")
            writeTextFrame(output, ready)
            while (true) {
                when (val frame = readFrame(input) ?: return) {
                    is WebSocketFrame.Text -> writeTextFrame(output, "echo:${frame.text}")
                    is WebSocketFrame.Binary -> writeBinaryFrame(output, frame.bytes)
                    is WebSocketFrame.Close -> {
                        writeCloseFrame(output, frame.code, frame.reason)
                        return
                    }
                    WebSocketFrame.Ping -> writePongFrame(output)
                    WebSocketFrame.Pong -> Unit
                }
            }
        }

        private fun runLargeMessageSession(input: InputStream, output: OutputStream) {
            writeTextFrame(output, "abcd")
            while (true) {
                when (val frame = readFrame(input) ?: return) {
                    is WebSocketFrame.Close -> {
                        writeCloseFrame(output, frame.code, frame.reason)
                        return
                    }
                    else -> Unit
                }
            }
        }

        private fun runHoldSession(input: InputStream, output: OutputStream) {
            holdOpen.countDown()
            writeTextFrame(output, "server-ready")
            while (true) {
                when (val frame = readFrame(input) ?: return) {
                    is WebSocketFrame.Close -> {
                        holdClose.countDown()
                        writeCloseFrame(output, frame.code, frame.reason)
                        return
                    }
                    WebSocketFrame.Ping -> writePongFrame(output)
                    else -> Unit
                }
            }
        }

        private fun writeHandshake(output: OutputStream, headers: Map<String, String>) {
            val key = headers["sec-websocket-key"].orEmpty()
            val accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + WEB_SOCKET_ACCEPT_GUID).toByteArray(StandardCharsets.ISO_8859_1)),
            )
            val protocol = headers["sec-websocket-protocol"]?.split(',')?.firstOrNull()?.trim().orEmpty()
            val response = buildString {
                append("HTTP/1.1 101 Switching Protocols\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Accept: $accept\r\n")
                if (protocol.isNotBlank()) {
                    append("Sec-WebSocket-Protocol: $protocol\r\n")
                }
                append("\r\n")
            }.toByteArray(StandardCharsets.ISO_8859_1)
            output.write(response)
            output.flush()
        }

        private fun readFrame(input: InputStream): WebSocketFrame? {
            val first = input.read()
            if (first < 0) {
                return null
            }
            val second = input.read()
            if (second < 0) {
                return null
            }
            val opcode = first and 0x0f
            val masked = (second and 0x80) != 0
            var length = (second and 0x7f).toLong()
            if (length == 126L) {
                length = readUnsignedShort(input).toLong()
            } else if (length == 127L) {
                length = readLong(input)
            }
            if (length > 1_048_576L) {
                return null
            }
            val mask = if (masked) readExact(input, 4) else null
            val payload = readExact(input, length.toInt())
            if (mask != null) {
                payload.indices.forEach { index ->
                    payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
                }
            }
            return when (opcode) {
                0x1 -> WebSocketFrame.Text(payload.toString(StandardCharsets.UTF_8))
                0x2 -> WebSocketFrame.Binary(payload)
                0x8 -> WebSocketFrame.Close(
                    code = if (payload.size >= 2) ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff) else 1000,
                    reason = if (payload.size > 2) payload.copyOfRange(2, payload.size).toString(StandardCharsets.UTF_8) else "",
                )
                0x9 -> WebSocketFrame.Ping
                0xA -> WebSocketFrame.Pong
                else -> null
            }
        }

        private fun writeTextFrame(output: OutputStream, text: String) {
            writeFrame(output, 0x1, text.toByteArray(StandardCharsets.UTF_8))
        }

        private fun writeBinaryFrame(output: OutputStream, bytes: ByteArray) {
            writeFrame(output, 0x2, bytes)
        }

        private fun writePongFrame(output: OutputStream) {
            writeFrame(output, 0xA, ByteArray(0))
        }

        private fun writeCloseFrame(output: OutputStream, code: Int, reason: String) {
            val reasonBytes = reason.toByteArray(StandardCharsets.UTF_8)
            val payload = ByteArray(2 + reasonBytes.size)
            payload[0] = ((code ushr 8) and 0xff).toByte()
            payload[1] = (code and 0xff).toByte()
            reasonBytes.copyInto(payload, destinationOffset = 2)
            writeFrame(output, 0x8, payload)
        }

        private fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
            output.write(0x80 or opcode)
            when {
                payload.size <= 125 -> output.write(payload.size)
                payload.size <= 65_535 -> {
                    output.write(126)
                    output.write((payload.size ushr 8) and 0xff)
                    output.write(payload.size and 0xff)
                }
                else -> {
                    output.write(127)
                    for (shift in 56 downTo 0 step 8) {
                        output.write(((payload.size.toLong() ushr shift) and 0xff).toInt())
                    }
                }
            }
            output.write(payload)
            output.flush()
        }

        private fun readAsciiLine(input: InputStream): String? {
            val output = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) {
                    return if (output.size() == 0) null else String(output.toByteArray(), StandardCharsets.ISO_8859_1).trimEnd('\r')
                }
                if (value == '\n'.code) {
                    return String(output.toByteArray(), StandardCharsets.ISO_8859_1).trimEnd('\r')
                }
                output.write(value)
            }
        }

        private fun readUnsignedShort(input: InputStream): Int {
            val bytes = readExact(input, 2)
            return ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
        }

        private fun readLong(input: InputStream): Long {
            return readExact(input, 8).fold(0L) { result, byte ->
                (result shl 8) or (byte.toLong() and 0xff)
            }
        }

        private fun readExact(input: InputStream, length: Int): ByteArray {
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(bytes, offset, length - offset)
                if (count < 0) {
                    throw java.io.EOFException("Unexpected end of WebSocket frame")
                }
                offset += count
            }
            return bytes
        }

        companion object {
            private const val WEB_SOCKET_ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        }
    }

    private sealed class WebSocketFrame {
        data class Text(val text: String) : WebSocketFrame()
        data class Binary(val bytes: ByteArray) : WebSocketFrame()
        data class Close(val code: Int, val reason: String) : WebSocketFrame()
        data object Ping : WebSocketFrame()
        data object Pong : WebSocketFrame()
    }
}
