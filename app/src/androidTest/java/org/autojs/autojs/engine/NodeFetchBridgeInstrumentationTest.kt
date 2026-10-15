package org.autojs.autojs.engine

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.TransactionTooLargeException
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NodeFetchBridgeInstrumentationTest {

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
    fun kotlinFetchBridgeRegistryAndFakeProviderAreStable() {
        assertTrue(NodeBridgeModules.supports("fetch", "request"))
        assertFalse(NodeBridgeModules.supports("fetch", "connect"))
        assertFalse(NodeBridgeModules.supports("fetch", "listen"))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("fetch", request.module)
            assertEquals("request", request.method)
            assertTrue("network" in request.permissions)
            val payload = JSONArray(request.argsJson).getJSONObject(0)
            assertEquals("https://example.invalid/get", payload.getString("url"))
            assertEquals("GET", payload.getString("method"))
            assertEquals(1024, payload.getInt("maxResponseBytes"))
            assertEquals(5, payload.getInt("maxRedirects"))
            NodeBridgeResponse.success(
                request.id,
                JSONObject().apply {
                    put("url", payload.getString("url"))
                    put("status", 200)
                    put("statusText", "OK")
                    put("headers", JSONArray().put(JSONArray().put("content-type").put("text/plain")))
                    put("bodyBase64", Base64.getEncoder().encodeToString("ok".toByteArray()))
                    put("bodyBytes", 2)
                }.toString(),
            )
        }

        val responses = mutableListOf<NodeBridgeResponse>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "fetch-request-1",
                module = "fetch",
                method = "request",
                argsJson = JSONArray().put(
                    JSONObject().apply {
                        put("url", "https://example.invalid/get")
                        put("method", "GET")
                        put("headers", JSONArray().put(JSONArray().put("x-test").put("yes")))
                        put("timeoutMs", 1000)
                        put("maxResponseBytes", 1024)
                        put("maxRedirects", 5)
                    },
                ).toString(),
                timeoutMs = 1000L,
                permissions = listOf("network"),
            ),
        ) { response -> responses += response }

        assertEquals(1, provider.requests.size)
        assertTrue(responses.single().ok)
        JSONObject(responses.single().resultJson.orEmpty()).also { result ->
            assertEquals(200, result.getInt("status"))
            assertEquals("ok", String(Base64.getDecoder().decode(result.getString("bodyBase64"))))
        }
    }

    @Test
    fun jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node fetch bridge focused regression")

        val workDir = File(context.cacheDir, "node-bridge-fetch-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fetch bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/fetch").apply {
            check(mkdirs()) { "Failed to create fetch shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const { Buffer } = require("buffer");
              const requests = [];
              async function readWebStream(webStream) {
                const reader = webStream.getReader();
                const chunks = [];
                while (true) {
                  const next = await reader.read();
                  if (next.done) break;
                  chunks.push(Buffer.from(next.value));
                }
                return Buffer.concat(chunks).toString("utf8");
              }
              function headerValue(headers, name) {
                const lower = String(name).toLowerCase();
                const found = (headers || []).find((entry) => String(entry[0]).toLowerCase() === lower);
                return found ? String(found[1]) : "";
              }
              function responseFor(payload) {
                if (payload.url.indexOf("/get") >= 0) {
                  return {
                    url: payload.url,
                    status: 200,
                    statusText: "OK",
                    headers: [["content-type", "text/plain"], ["x-echo", headerValue(payload.headers, "x-test")]],
                    bodyBase64: Buffer.from("hello").toString("base64"),
                    bodyBytes: 5
                  };
                }
                if (payload.url.indexOf("/post") >= 0) {
                  return {
                    url: payload.url,
                    status: 201,
                    statusText: "Created",
                    headers: [["content-type", "application/json"]],
                    bodyBase64: Buffer.from(JSON.stringify({
                      method: payload.method,
                      body: Buffer.from(payload.bodyBase64, "base64").toString("utf8"),
                      type: headerValue(payload.headers, "content-type")
                    })).toString("base64")
                  };
                }
                if (payload.url.indexOf("/large") >= 0) {
                  return {
                    url: payload.url,
                    status: 200,
                    statusText: "OK",
                    headers: [["content-type", "text/plain"]],
                    bodyBase64: Buffer.from("abcdef").toString("base64"),
                    bodyBytes: 6
                  };
                }
                return {
                  url: payload.url,
                  status: 404,
                  statusText: "Not Found",
                  headers: [],
                  bodyBase64: ""
                };
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const payload = request.args[0];
                  if (payload.url.indexOf("/timeout") >= 0 || payload.url.indexOf("/abort") >= 0) {
                    return;
                  }
                  if (payload.url.indexOf("/large") >= 0 && payload.maxResponseBytes < 6) {
                    // maxResponseBytes is the host provider's policy since M20.2 batch 11.
                    setImmediate(() => {
                      bridge.__test.receiveMessage(JSON.stringify({
                        id: request.id,
                        ok: false,
                        error: {
                          name: "Error",
                          message: "AutoJs6 controlled fetch response exceeded " + payload.maxResponseBytes + " bytes.",
                          code: "ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE",
                          module: "fetch",
                          method: "request"
                        }
                      }));
                    });
                    return;
                  }
                  setImmediate(() => {
                    bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: true,
                      result: responseFor(payload)
                    }));
                  });
                }
              });

              const fetchModule = require("fetch");
              const resolved = require.resolve("fetch");
              const get = await fetchModule("https://example.invalid/get", {
                headers: { "x-test": "yes" },
                timeoutMs: 1000,
                maxResponseBytes: 1024
              });
              // The controlled fetch returns a native Response: stream a clone, then read the text.
              const getBodyStreamText = await readWebStream(get.clone().body);
              const getText = await get.text();
              const post = await fetchModule("https://example.invalid/post", {
                method: "POST",
                headers: { "content-type": "text/plain" },
                body: Buffer.from("payload"),
                timeoutMs: 1000
              });
              const postJson = await post.json();

              let timeoutCode = "";
              try {
                await fetchModule("https://example.invalid/timeout", { timeoutMs: 25 });
              } catch (error) {
                timeoutCode = error && (error.autojs6Code || error.code) || "";
              }

              let abortCode = "";
              const controller = new AbortController();
              const abortPromise = fetchModule("https://example.invalid/abort", {
                signal: controller.signal,
                timeoutMs: 1000
              });
              setTimeout(() => controller.abort(), 0);
              try {
                await abortPromise;
              } catch (error) {
                abortCode = error && (error.autojs6Code || error.code) || "";
              }

              let largeCode = "";
              try {
                await fetchModule("https://example.invalid/large", {
                  timeoutMs: 1000,
                  maxResponseBytes: 3
                });
              } catch (error) {
                largeCode = error && (error.autojs6Code || error.code) || "";
              }

              // http is the controlled client here; net is the raw module that stays disabled.
              let rawNetCode = "";
              try {
                require("net").connect(80, "127.0.0.1");
              } catch (error) {
                rawNetCode = error && (error.autojs6Code || error.code) || "";
              }

              console.log("fetch.resolve=" + resolved);
              console.log("fetch.shadow=" + (fetchModule.shadow === true));
              console.log("fetch.frozen=" + Object.isFrozen(fetchModule));
              console.log("fetch.aliases=" + [typeof fetch, typeof fetchModule, fetchModule.fetch === fetchModule, fetchModule.default === fetchModule].join("|"));
              console.log("fetch.get=" + [get.status, get.statusText, get.ok, get.url, get.headers.get("x-echo"), getText].join("|"));
              console.log("fetch.get_body_stream=" + getBodyStreamText);
              console.log("fetch.post=" + [post.status, post.ok, postJson.method, postJson.body, postJson.type].join("|"));
              console.log("fetch.requests=" + requests.map((request) => request.args[0].method + ":" + request.args[0].url.split("/").pop()).join("|"));
              console.log("fetch.permissions=" + requests.every((request) => request.permissions.indexOf("network") >= 0));
              console.log("fetch.timeout=" + timeoutCode);
              console.log("fetch.abort=" + abortCode);
              console.log("fetch.large=" + largeCode);
              console.log("fetch.raw_net=" + rawNetCode);
              console.log("fetch.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-fetch-fake-source.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
            // Global fetch and raw http are native Node now; only the bridged facade is under test.
            rawNodeNetworkModulesEnabled = false,
        )

        NodePluginTestHarness.assertSucceeded("fetch fake bridge", result)
        assertTrue(result.stdout, result.stdout.contains("fetch.resolve=autojs6:fetch"))
        assertTrue(result.stdout, result.stdout.contains("fetch.shadow=false"))
        assertTrue(result.stdout, result.stdout.contains("fetch.frozen=true"))
        assertTrue(result.stdout, result.stdout.contains("fetch.aliases=function|function|true|true"))
        assertTrue(result.stdout, result.stdout.contains("fetch.get=200|OK|true|https://example.invalid/get|yes|hello"))
        assertTrue(result.stdout, result.stdout.contains("fetch.get_body_stream=hello"))
        assertTrue(result.stdout, result.stdout.contains("fetch.post=201|true|POST|payload|text/plain"))
        assertTrue(result.stdout, result.stdout.contains("fetch.requests=GET:get|POST:post|GET:timeout|GET:abort|GET:large"))
        assertTrue(result.stdout, result.stdout.contains("fetch.permissions=true"))
        assertTrue(result.stdout, result.stdout.contains("fetch.timeout=ERR_AUTOJS6_NETWORK_TIMEOUT"))
        assertTrue(result.stdout, result.stdout.contains("fetch.abort=ERR_AUTOJS6_NETWORK_ABORTED"))
        assertTrue(result.stdout, result.stdout.contains("fetch.large=ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE"))
        // Node runtime 1.6.0+ gates the raw network builtins at resolve time with ERR_AUTOJS6_BUILTIN_DISABLED when
        // rawNodeNetworkModulesEnabled=false; the restricted facade that answered ERR_AUTOJS6_NETWORK_DISABLED is gone.
        assertTrue(result.stdout, result.stdout.contains("fetch.raw_net=ERR_AUTOJS6_BUILTIN_DISABLED"))
        assertTrue(result.stdout, result.stdout.contains("fetch.pending=0"))
    }

    @Test
    fun jsFetchPartialSurfaceHandlesBodiesResponsesAbortAndRedirectModes() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node fetch partial surface regression")

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const { Buffer } = require("buffer");
              const fetchModule = require("fetch");
              const requests = [];
              function codeOf(error) {
                return error && (error.autojs6Code || error.code) || "";
              }
              function headerValue(headers, name) {
                const lower = String(name).toLowerCase();
                const found = (headers || []).find((entry) => String(entry[0]).toLowerCase() === lower);
                return found ? String(found[1]) : "";
              }
              function requestBody(payload) {
                if (payload.bodyBase64 !== undefined) {
                  return Buffer.from(payload.bodyBase64, "base64").toString("utf8");
                }
                return String(payload.bodyText || "");
              }
              function jsonResponse(payload, body) {
                return {
                  url: payload.url,
                  status: 200,
                  statusText: "OK",
                  headers: [["content-type", "application/json"]],
                  bodyBase64: Buffer.from(JSON.stringify(body)).toString("base64")
                };
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const payload = request.args[0];
                  if (payload.method === "GET" && (payload.bodyText !== undefined || payload.bodyBase64 !== undefined)) {
                    // The method/body rules are the host provider's since M20.2 batch 11.
                    setImmediate(() => {
                      bridge.__test.receiveMessage(JSON.stringify({
                        id: request.id,
                        ok: false,
                        error: {
                          name: "Error",
                          message: "fetch.request GET must not include a body.",
                          code: "ERR_AUTOJS6_NETWORK_PERMISSION_DENIED",
                          module: "fetch",
                          method: "request"
                        }
                      }));
                    });
                    return;
                  }
                  let result;
                  if (payload.url.indexOf("/string") >= 0) {
                    result = jsonResponse(payload, {
                      method: payload.method,
                      body: requestBody(payload),
                      bodyKind: payload.bodyText !== undefined ? "text" : "base64",
                      type: headerValue(payload.headers, "content-type"),
                      mode: headerValue(payload.headers, "x-mode")
                    });
                  } else if (payload.url.indexOf("/uint8") >= 0) {
                    result = jsonResponse(payload, {
                      method: payload.method,
                      body: requestBody(payload),
                      bodyKind: payload.bodyBase64 !== undefined ? "base64" : "text",
                      mode: headerValue(payload.headers, "x-mode")
                    });
                  } else if (payload.url.indexOf("/array") >= 0) {
                    result = {
                      url: payload.url,
                      status: 200,
                      statusText: "OK",
                      headers: [["content-type", "application/octet-stream"], ["x-array", headerValue(payload.headers, "x-array")]],
                      bodyBase64: Buffer.from([1, 2, 255]).toString("base64"),
                      bodyBytes: 3
                    };
                  } else if (payload.url.indexOf("/manual") >= 0) {
                    result = {
                      url: payload.url,
                      status: 302,
                      statusText: "Found",
                      headers: [["location", "/manual-target"], ["content-type", "text/plain"]],
                      bodyBase64: Buffer.from("manual").toString("base64"),
                      bodyBytes: 6
                    };
                  } else if (payload.url.indexOf("/error-redirect") >= 0) {
                    result = {
                      url: payload.url,
                      status: 302,
                      statusText: "Found",
                      headers: [["location", "/blocked"]],
                      bodyBase64: "",
                      bodyBytes: 0
                    };
                  } else {
                    result = {
                      url: payload.url,
                      status: 404,
                      statusText: "Not Found",
                      headers: [],
                      bodyBase64: "",
                      bodyBytes: 0
                    };
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

              const stringResponse = await fetchModule("https://example.invalid/string", {
                method: "POST",
                headers: [["content-type", "text/plain"], ["x-mode", "string"]],
                body: "alpha",
                timeoutMs: 1000
              });
              const stringJson = await stringResponse.json();

              const uint8Response = await fetchModule("https://example.invalid/uint8", {
                method: "POST",
                headers: { "x-mode": "uint8" },
                body: new Uint8Array([98, 105, 110]),
                timeoutMs: 1000
              });
              const uint8Json = await uint8Response.json();

              const arrayResponse = await fetchModule(new URL("https://example.invalid/array"), {
                headers: [["x-array", "yes"]],
                timeoutMs: 1000,
                maxResponseBytes: 8
              });
              const arrayHex = Buffer.from(new Uint8Array(await arrayResponse.arrayBuffer())).toString("hex");

              const manualResponse = await fetchModule("https://example.invalid/manual", {
                redirect: "manual",
                timeoutMs: 1000
              });
              const manualText = await manualResponse.text();

              let redirectErrorCode = "";
              try {
                await fetchModule("https://example.invalid/error-redirect", {
                  redirect: "error",
                  timeoutMs: 1000
                });
              } catch (error) {
                redirectErrorCode = codeOf(error);
              }

              let getBodyCode = "";
              try {
                await fetchModule("https://example.invalid/get-body", {
                  body: "blocked",
                  timeoutMs: 1000
                });
              } catch (error) {
                getBodyCode = codeOf(error);
              }

              const requestsBeforeAbort = requests.length;
              let abortBeforeCode = "";
              const controller = new AbortController();
              controller.abort();
              try {
                await fetchModule("https://example.invalid/abort-before", {
                  signal: controller.signal,
                  timeoutMs: 1000
                });
              } catch (error) {
                abortBeforeCode = codeOf(error);
              }
              const abortBeforeStarted = requests.length > requestsBeforeAbort;

              // node:https is the controlled client here; net is the raw module that stays disabled.
              let rawNodeNetCode = "";
              try {
                require("node:net").connect(443, "127.0.0.1");
              } catch (error) {
                rawNodeNetCode = codeOf(error);
              }

              console.log("fetch.surface.string=" + [
                stringJson.method,
                stringJson.body,
                stringJson.bodyKind,
                stringJson.type,
                stringJson.mode
              ].join("|"));
              console.log("fetch.surface.uint8=" + [
                uint8Json.method,
                uint8Json.body,
                uint8Json.bodyKind,
                uint8Json.mode
              ].join("|"));
              console.log("fetch.surface.arrayBuffer=" + [
                arrayResponse.status,
                arrayResponse.headers.get("x-array"),
                arrayHex
              ].join("|"));
              console.log("fetch.surface.redirectManual=" + [
                manualResponse.status,
                manualResponse.statusText,
                manualResponse.headers.get("location"),
                manualText
              ].join("|"));
              console.log("fetch.surface.denials=" + [
                redirectErrorCode,
                getBodyCode,
                abortBeforeCode,
                abortBeforeStarted,
                rawNodeNetCode
              ].join("|"));
              console.log("fetch.surface.requests=" + requests.map((request) => request.args[0].method + ":" + request.args[0].url.split("/").pop()).join("|"));
              console.log("fetch.surface.policyFrozen=" + (Object.isFrozen(fetchModule.policy) && Object.isFrozen(fetchModule.policy.allowedSchemes)));
              console.log("fetch.surface.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-fetch-partial-surface-source.js>",
            timeoutMs = 10_000L,
            rawNodeNetworkModulesEnabled = false,
        )

        NodePluginTestHarness.assertSucceeded("fetch partial surface", result)
        assertTrue(result.stdout, result.stdout.contains("fetch.surface.string=POST|alpha|text|text/plain|string"))
        assertTrue(result.stdout, result.stdout.contains("fetch.surface.uint8=POST|bin|base64|uint8"))
        assertTrue(result.stdout, result.stdout.contains("fetch.surface.arrayBuffer=200|yes|0102ff"))
        assertTrue(result.stdout, result.stdout.contains("fetch.surface.redirectManual=302|Found|/manual-target|manual"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "fetch.surface.denials=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_PERMISSION_DENIED|ERR_AUTOJS6_NETWORK_ABORTED|false|ERR_AUTOJS6_BUILTIN_DISABLED",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("fetch.surface.requests=POST:string|POST:uint8|GET:array|GET:manual|GET:error-redirect|GET:get-body"))
        assertTrue(result.stdout, result.stdout.contains("fetch.surface.policyFrozen=true"))
        assertTrue(result.stdout, result.stdout.contains("fetch.surface.pending=0"))
    }

    @Test
    fun jsFetchPolicyHardeningUsesFakeTransport() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node fetch policy hardening regression")

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const fetchModule = require("fetch");
              const { Buffer } = require("buffer");
              const requests = [];
              const held = [];
              function codeOf(error) {
                return error && (error.autojs6Code || error.code) || "";
              }
              function sleep(ms) {
                return new Promise((resolve) => setTimeout(resolve, ms));
              }
              async function waitFor(predicate, label) {
                for (let i = 0; i < 100; i += 1) {
                  if (predicate()) return;
                  await sleep(5);
                }
                throw new Error("timeout waiting for " + label);
              }
              function responseBody(text) {
                return Buffer.from(text).toString("base64");
              }
              function send(request, result) {
                setImmediate(() => {
                  bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result
                  }));
                });
              }
              function okResponse(payload, text) {
                return {
                  url: payload.url,
                  status: 200,
                  statusText: "OK",
                  headers: [["content-type", "text/plain"]],
                  bodyBase64: responseBody(text),
                  bodyBytes: Buffer.byteLength(text)
                };
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const payload = request.args[0];
                  if (payload.url.indexOf("/hold") >= 0) {
                    held.push(request);
                    return;
                  }
                  if (
                    payload.url.indexOf("/timeout") >= 0 ||
                    payload.url.indexOf("/abort") >= 0 ||
                    payload.url.indexOf("/destroy") >= 0
                  ) {
                    return;
                  }
                  if (payload.url.indexOf("/redirect-loop") >= 0) {
                    send(request, {
                      url: payload.url,
                      status: 302,
                      statusText: "Found",
                      headers: [["location", "/redirect-loop"]],
                      bodyBase64: "",
                      bodyBytes: 0
                    });
                    return;
                  }
                  if (payload.url.indexOf("/large") >= 0) {
                    // maxResponseBytes is the host provider's policy since M20.2 batch 11.
                    setImmediate(() => {
                      bridge.__test.receiveMessage(JSON.stringify({
                        id: request.id,
                        ok: false,
                        error: {
                          name: "Error",
                          message: "AutoJs6 controlled fetch response exceeded " + payload.maxResponseBytes + " bytes.",
                          code: "ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE",
                          module: "fetch",
                          method: "request"
                        }
                      }));
                    });
                    return;
                  }
                  send(request, okResponse(payload, "ok"));
                }
              });

              // The runtime no longer caps concurrency (M20.2 batch 11): every request beyond the
              // former 16 reaches the transport and the host provider applies its own window.
              const holdCount = 20;
              const holdPromises = Array.from({ length: holdCount }, (_, index) =>
                fetchModule("https://example.invalid/hold?i=" + index, { timeoutMs: 1000 }).then((response) => response.text())
              );
              await waitFor(() => held.length === holdCount, "held requests");
              const inFlight = bridge.__test.pendingCount();

              held.splice(0).forEach((request) => {
                const payload = request.args[0];
                send(request, okResponse(payload, "hold-" + payload.url.split("=").pop()));
              });
              const holdTexts = await Promise.all(holdPromises);

              const schemeCodes = [];
              for (const url of ["file:///tmp/a", "content://autojs6/a", "about:blank", "data:text/plain,hi"]) {
                try {
                  await fetchModule(url, { timeoutMs: 1000 });
                } catch (error) {
                  schemeCodes.push(codeOf(error));
                }
              }

              let timeoutCode = "";
              try {
                await fetchModule("https://example.invalid/timeout", { timeoutMs: 25 });
              } catch (error) {
                timeoutCode = codeOf(error);
              }

              let abortCode = "";
              const controller = new AbortController();
              const abortPromise = fetchModule("https://example.invalid/abort", {
                signal: controller.signal,
                timeoutMs: 1000
              });
              await waitFor(() => requests.some((request) => request.args[0].url.indexOf("/abort") >= 0), "abort request");
              controller.abort();
              try {
                await abortPromise;
              } catch (error) {
                abortCode = codeOf(error);
              }

              let redirectCode = "";
              try {
                await fetchModule("https://example.invalid/redirect-loop", { timeoutMs: 1000, maxRedirects: 1 });
              } catch (error) {
                redirectCode = codeOf(error);
              }

              let largeCode = "";
              try {
                await fetchModule("https://example.invalid/large", { timeoutMs: 1000, maxResponseBytes: 3 });
              } catch (error) {
                largeCode = codeOf(error);
              }

              let destroyCode = "";
              const destroyPromise = fetchModule("https://example.invalid/destroy", { timeoutMs: 1000 }).catch(codeOf);
              await waitFor(() => requests.some((request) => request.args[0].url.indexOf("/destroy") >= 0), "destroy request");
              bridge.__test.destroy("service destroy test");
              destroyCode = await destroyPromise;

              // https is the controlled client here; tls and net are the raw modules that stay disabled.
              let rawTlsCode = "";
              try {
                require("tls").connect(443, "127.0.0.1");
              } catch (error) {
                rawTlsCode = codeOf(error);
              }
              let rawNetCode = "";
              try {
                require("net").connect(443, "127.0.0.1");
              } catch (error) {
                rawNetCode = codeOf(error);
              }

              const diagnostics = fetchModule.diagnostics();
              console.log("fetch.policy=" + [
                fetchModule.policy.defaultTimeoutMs,
                fetchModule.policy.defaultMaxRedirects,
                fetchModule.policy.limitsEnforcedBy,
                typeof fetchModule.policy.maxConcurrentRequests,
                typeof fetchModule.policy.defaultMaxResponseBytes
              ].join("|"));
              console.log("fetch.harden.inFlight=" + inFlight);
              console.log("fetch.harden.codes=" + [
                timeoutCode,
                abortCode,
                redirectCode,
                largeCode,
                destroyCode,
                rawTlsCode,
                rawNetCode
              ].join("|"));
              console.log("fetch.harden.schemes=" + schemeCodes.join("|"));
              console.log("fetch.harden.redirect_requests=" + requests.filter((request) => request.args[0].url.indexOf("/redirect-loop") >= 0).length);
              console.log("fetch.harden.holds=" + holdTexts.join("|"));
              console.log("fetch.harden.diagnostics=" + [
                diagnostics.activeRequestCount,
                diagnostics.maxActiveRequestCount,
                diagnostics.timeoutCount,
                diagnostics.rejectedByPolicyCount,
                diagnostics.abortedCount
              ].join("|"));
              console.log("fetch.harden.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-fetch-policy-source.js>",
            timeoutMs = 10_000L,
            rawNodeNetworkModulesEnabled = false,
        )

        NodePluginTestHarness.assertSucceeded("fetch policy hardening", result)
        val resultDetails = NodePluginTestHarness.describeResult("fetch policy hardening output", result)
        assertTrue(resultDetails, result.stdout.contains("fetch.policy=30000|20|host_provider|undefined|undefined"))
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.inFlight=20"))
        assertTrue(
            resultDetails,
            result.stdout.contains(
                "fetch.harden.codes=ERR_AUTOJS6_NETWORK_TIMEOUT|ERR_AUTOJS6_NETWORK_ABORTED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD|ERR_AUTOJS6_BUILTIN_DISABLED|ERR_AUTOJS6_BUILTIN_DISABLED",
            ),
        )
        assertTrue(
            resultDetails,
            result.stdout.contains(
                "fetch.harden.schemes=ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL",
            ),
        )
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.redirect_requests=2"))
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.holds=hold-0|hold-1|hold-2|hold-3|hold-4|hold-5|hold-6|hold-7|hold-8|hold-9|hold-10|hold-11|hold-12|hold-13|hold-14|hold-15|hold-16|hold-17|hold-18|hold-19"))
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.diagnostics=0|20|1|6|1"))
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.pending=0"))
    }

    @Test
    fun jsAxiosCompatibilityLayerUsesControlledFetchOnly() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node axios controlled fetch compatibility regression")

        // No node_modules/axios here: axios is one of the shims that yield to an installed npm package of the
        // same name (see jsAxiosShimYieldsToInstalledNpmPackage), so a planted package would replace the shim.
        val workDir = File(context.cacheDir, "node-bridge-axios-controlled").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create axios bridge working directory: $absolutePath" }
        }

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const { Buffer } = require("buffer");
              const axios = require("axios");
              const requests = [];
              function codeOf(error) {
                return error && (error.autojs6Code || error.code) || "";
              }
              function headerValue(headers, name) {
                const lower = String(name).toLowerCase();
                const found = (headers || []).find((entry) => String(entry[0]).toLowerCase() === lower);
                return found ? String(found[1]) : "";
              }
              function requestBody(payload) {
                if (payload.bodyBase64 !== undefined) {
                  return Buffer.from(payload.bodyBase64, "base64").toString("utf8");
                }
                return String(payload.bodyText || "");
              }
              function json(payload, body, status, statusText) {
                return {
                  url: payload.url,
                  status: status || 200,
                  statusText: statusText || "OK",
                  headers: [["content-type", "application/json"], ["x-mode", headerValue(payload.headers, "x-mode")]],
                  bodyBase64: Buffer.from(JSON.stringify(body)).toString("base64")
                };
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const payload = request.args[0];
                  if (payload.url.indexOf("/timeout") >= 0) {
                    return;
                  }
                  let result;
                  if (payload.url.indexOf("/items") >= 0) {
                    result = json(payload, {
                      method: payload.method,
                      url: payload.url,
                      mode: headerValue(payload.headers, "x-mode")
                    });
                  } else if (payload.url.indexOf("/post") >= 0) {
                    result = json(payload, {
                      method: payload.method,
                      body: requestBody(payload),
                      contentType: headerValue(payload.headers, "content-type")
                    }, 201, "Created");
                  } else if (payload.url.indexOf("/text") >= 0) {
                    result = {
                      url: payload.url,
                      status: 200,
                      statusText: "OK",
                      headers: [["content-type", "text/plain"]],
                      bodyBase64: Buffer.from("plain-text").toString("base64"),
                      bodyBytes: 10
                    };
                  } else if (payload.url.indexOf("/array") >= 0) {
                    result = {
                      url: payload.url,
                      status: 200,
                      statusText: "OK",
                      headers: [["content-type", "application/octet-stream"]],
                      bodyBase64: Buffer.from([7, 8, 9]).toString("base64"),
                      bodyBytes: 3
                    };
                  } else if (payload.url.indexOf("/status") >= 0) {
                    result = json(payload, { message: "bad" }, 500, "Server Error");
                  } else {
                    result = {
                      url: payload.url,
                      status: 404,
                      statusText: "Not Found",
                      headers: [["content-type", "application/json"]],
                      bodyBase64: Buffer.from(JSON.stringify({ message: "missing" })).toString("base64")
                    };
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

              const resolved = require.resolve("axios");
              const get = await axios.get("items", {
                baseURL: "https://api.example.invalid/v1",
                params: { tag: ["a", "b"], q: "one two" },
                headers: { "x-mode": "get" },
                responseType: "json",
                timeout: 1000
              });
              const post = await axios.post("https://api.example.invalid/post", { hello: "world" }, {
                responseType: "json",
                timeout: 1000
              });
              const text = await axios({
                method: "GET",
                url: "https://api.example.invalid/text",
                responseType: "text",
                timeout: 1000
              });
              const array = await axios.request({
                method: "GET",
                url: "https://api.example.invalid/array",
                responseType: "arraybuffer",
                timeout: 1000
              });
              const arrayHex = Buffer.from(new Uint8Array(array.data)).toString("hex");

              let statusShape = "";
              try {
                await axios.get("https://api.example.invalid/status", {
                  responseType: "json",
                  timeout: 1000
                });
              } catch (error) {
                statusShape = [
                  axios.isAxiosError(error),
                  error.name,
                  error.code,
                  error.response && error.response.status,
                  error.response && error.response.data && error.response.data.message
                ].join("|");
              }

              let timeoutShape = "";
              try {
                await axios.get("https://api.example.invalid/timeout", { timeout: 25 });
              } catch (error) {
                timeoutShape = [
                  axios.isAxiosError(error),
                  error.name,
                  codeOf(error)
                ].join("|");
              }

              const policyCodes = [];
              for (const config of [
                { adapter: function customAdapter() {} },
                { httpAgent: {} },
                { proxy: { host: "127.0.0.1", port: 8080 } },
                { responseType: "stream" }
              ]) {
                try {
                  await axios.get("https://api.example.invalid/policy", Object.assign({ timeout: 1000 }, config));
                } catch (error) {
                  policyCodes.push(codeOf(error));
                }
              }

              // node:https is the controlled client here; net is the raw module that stays disabled.
              let rawNetCode = "";
              try {
                require("node:net").connect(443, "127.0.0.1");
              } catch (error) {
                rawNetCode = codeOf(error);
              }

              console.log("axios.resolve=" + resolved);
              console.log("axios.shadow=" + (axios.shadow === true));
              console.log("axios.surface=" + [
                typeof axios,
                typeof axios.get,
                typeof axios.post,
                typeof axios.request,
                axios.default === axios,
                Object.isFrozen(axios),
                axios.adapterName
              ].join("|"));
              console.log("axios.get=" + [
                get.status,
                get.data.method,
                get.data.url,
                get.data.mode,
                get.headers["x-mode"]
              ].join("|"));
              console.log("axios.post=" + [
                post.status,
                post.statusText,
                post.data.method,
                post.data.body,
                post.data.contentType
              ].join("|"));
              console.log("axios.responseTypes=" + [text.data, arrayHex].join("|"));
              console.log("axios.statusError=" + statusShape);
              console.log("axios.timeout=" + timeoutShape);
              console.log("axios.policy=" + policyCodes.join("|"));
              console.log("axios.requests=" + requests.map((request) => request.args[0].method + ":" + request.args[0].url.split("/").pop()).join("|"));
              console.log("axios.permissions=" + requests.every((request) => request.permissions.indexOf("network") >= 0));
              console.log("axios.raw_net=" + rawNetCode);
              console.log("axios.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-axios-controlled-fetch-source.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
            rawNodeNetworkModulesEnabled = false,
        )

        NodePluginTestHarness.assertSucceeded("axios controlled fetch", result)
        assertTrue(result.stdout, result.stdout.contains("axios.resolve=autojs6:axios"))
        assertTrue(result.stdout, result.stdout.contains("axios.shadow=false"))
        assertTrue(result.stdout, result.stdout.contains("axios.surface=function|function|function|function|true|true|autojs6-controlled-fetch"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "axios.get=200|GET|https://api.example.invalid/v1/items?tag=a&tag=b&q=one+two|get|get",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("""axios.post=201|Created|POST|{"hello":"world"}|application/json"""))
        assertTrue(result.stdout, result.stdout.contains("axios.responseTypes=plain-text|070809"))
        assertTrue(result.stdout, result.stdout.contains("axios.statusError=true|AxiosError|ERR_BAD_RESPONSE|500|bad"))
        assertTrue(result.stdout, result.stdout.contains("axios.timeout=true|AxiosError|ERR_AUTOJS6_NETWORK_TIMEOUT"))
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "axios.policy=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED",
            ),
        )
        assertTrue(
            result.stdout,
            result.stdout.contains(
                "axios.requests=GET:items?tag=a&tag=b&q=one+two|POST:post|GET:text|GET:array|GET:status|GET:timeout|GET:policy",
            ),
        )
        assertTrue(result.stdout, result.stdout.contains("axios.permissions=true"))
        assertTrue(result.stdout, result.stdout.contains("axios.raw_net=ERR_AUTOJS6_BUILTIN_DISABLED"))
        assertTrue(result.stdout, result.stdout.contains("axios.pending=0"))
    }

    @Test
    fun jsAxiosShimYieldsToInstalledNpmPackage() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node axios npm precedence regression")

        // Desktop Node semantics for the shims that stand in for npm packages (axios, colors, mime, nanoid, opencc,
        // undici): an installed package of the same name wins over the runtime shim for require and require.resolve.
        // A bare working directory only ships its explicit inputs (entry, env paths, module-source overlays) to the
        // plugin, so the package travels as a module-source overlay under node_modules.
        val workDir = File(context.cacheDir, "node-bridge-axios-npm-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create axios npm precedence working directory: $absolutePath" }
        }

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            const axios = require("axios");
            const resolved = require.resolve("axios");
            console.log("axios.npm.shadow=" + (axios.shadow === true));
            console.log("axios.npm.resolve=" + (resolved.indexOf("/node_modules/axios/") >= 0));
            console.log("axios.npm.resolved=" + resolved);
            """.trimIndent(),
            "<embedded-autojs-axios-npm-shadow.js>",
            workingDirectory = workDir,
            moduleSources = mapOf(
                "node_modules/axios/package.json" to """{"name":"axios","version":"0.0.0-test","main":"index.js"}""",
                "node_modules/axios/index.js" to """module.exports = { shadow: true };""",
            ),
        )

        NodePluginTestHarness.assertSucceeded("axios npm precedence", result)
        NodePluginTestHarness.assertStdoutContains("axios npm precedence", result, "axios.npm.shadow=true", "axios.npm.resolve=true")
    }

    @Test
    fun hostDeliversControlledFetchBodiesThroughTheBinaryTransport() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node controlled fetch binary body transport")

        ControlledFetchLoopbackServer().use { server ->
            val baseUrl = server.baseUrl
            val expectedHash = sha256Hex(patternBytes(LARGE_BODY_BYTES))
            val result = NodePluginTestHarness.runPluginScript(
                """
                "nodejs";
                (async function main() {
                  const fetchModule = require("fetch");
                  const crypto = require("crypto");
                  const base = "$baseUrl";
                  function codeOf(error) {
                    return error && (error.autojs6Code || error.code) || "";
                  }
                  const large = await fetchModule(base + "/large-bytes?n=$LARGE_BODY_BYTES", { timeoutMs: 20000 });
                  const bytes = Buffer.from(await large.arrayBuffer());
                  let pattern = bytes.length === $LARGE_BODY_BYTES;
                  for (let index = 0; pattern && index < bytes.length; index += 4099) {
                    pattern = bytes[index] === index % 251;
                  }
                  const hash = crypto.createHash("sha256").update(bytes).digest("hex");
                  const empty = await fetchModule(base + "/empty");
                  const emptyLength = (await empty.arrayBuffer()).byteLength;
                  const redirected = await fetchModule(base + "/redirect-with-body");
                  const redirectedJson = await redirected.json();
                  let tooLargeCode = "";
                  try {
                    await fetchModule(base + "/large-bytes?n=65536", { maxResponseBytes: 1024 });
                  } catch (error) {
                    tooLargeCode = codeOf(error);
                  }
                  const small = await (await fetchModule(base + "/get?via=pfd")).json();
                  const parallel = await Promise.all(Array.from({ length: 24 }, (_, index) =>
                    fetchModule(base + "/large-bytes?n=" + (4096 + index)).then((response) => response.arrayBuffer()).then((buffer) => buffer.byteLength)));
                  console.log("live.fetch.pfd=" + [
                    large.status,
                    bytes.length,
                    pattern,
                    hash,
                    empty.status,
                    emptyLength,
                    redirected.status,
                    redirectedJson.query,
                    tooLargeCode,
                    small.query,
                    parallel.every((length, index) => length === 4096 + index)
                  ].join("|"));
                })();
                """.trimIndent(),
                "<embedded-autojs-fetch-binary-transport.js>",
                timeoutMs = 40_000L,
            )

            NodePluginTestHarness.assertSucceeded("fetch binary transport", result)
            val resultDetails = NodePluginTestHarness.describeResult("fetch binary transport", result)
            assertTrue(
                resultDetails,
                result.stdout.contains(
                    "live.fetch.pfd=200|$LARGE_BODY_BYTES|true|$expectedHash|200|0|200|via=redirect-body|" +
                        "ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|via=pfd|true",
                ),
            )
        }
    }

    @Test
    fun hostBrokerReportsOversizedInlineRepliesInsteadOfSwallowingThem() {
        ControlledFetchLoopbackServer().use { server ->
            val broker = NodeJsHostCapabilityBroker(
                context,
                NodePluginScriptRequest(
                    source = "",
                    sourceName = "<broker-transaction-size>",
                    workingDirectory = context.cacheDir.absolutePath,
                    sandboxRoot = context.cacheDir.absolutePath,
                    executionId = "broker-transaction-size-${System.nanoTime()}",
                ),
                "instrumentation",
            )
            try {
                val replies = LinkedBlockingQueue<Bundle>()
                val oversized = AtomicInteger()
                val binarySummary = AtomicReference("")
                val callback = object : INodeJsHostCapabilityCallback.Stub() {
                    override fun onResponse(response: Bundle) {
                        val json = response.getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON).orEmpty()
                        if (json.length > SIMULATED_BINDER_LIMIT_CHARS) {
                            oversized.incrementAndGet()
                            throw TransactionTooLargeException()
                        }
                        @Suppress("DEPRECATION")
                        val descriptor = response.getParcelable<ParcelFileDescriptor>(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_PFD)
                        if (descriptor != null) {
                            // The broker closes the descriptor after onResponse returns, like the real Binder path.
                            val bytes = FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                            binarySummary.set(
                                "${bytes.size}|${sha256Hex(bytes)}|" +
                                    response.getLong(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_BYTE_COUNT, -1L),
                            )
                        }
                        replies.add(response)
                    }
                }

                fun fetchRequest(id: String, binary: Boolean): Bundle {
                    val request = JSONObject()
                        .put("id", id)
                        .put("module", "fetch")
                        .put("method", "request")
                        .put(
                            "args",
                            JSONArray().put(
                                JSONObject()
                                    .put("url", "${server.baseUrl}/large-bytes?n=$LARGE_BODY_BYTES")
                                    .put("timeoutMs", 10_000),
                            ),
                        )
                        .put("timeoutMs", 10_000)
                        .put("permissions", JSONArray().put("network"))
                    if (binary) {
                        request.put("binary", true)
                    }
                    return Bundle().apply {
                        putString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON, request.toString())
                    }
                }

                broker.dispatch(fetchRequest("inline-1", binary = false), callback)
                val inlineReply = replies.poll(20, TimeUnit.SECONDS)
                assertNotNull("inline reply missing", inlineReply)
                assertEquals(1, oversized.get())
                assertFalse(inlineReply!!.getBoolean(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_OK, true))
                val inlineParsed = NodeBridgeResponse.fromJson(
                    inlineReply.getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON).orEmpty(),
                )
                assertEquals("inline-1", inlineParsed.id)
                assertEquals(NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED, inlineParsed.error?.code)
                assertEquals(NodeBridgeErrorCategory.PROVIDER_FAILED, inlineParsed.error?.category)
                assertEquals("fetch", inlineParsed.error?.module)
                assertEquals("request", inlineParsed.error?.method)
                assertTrue(
                    inlineParsed.error?.message.orEmpty(),
                    inlineParsed.error?.message.orEmpty().contains("exceeded the Binder transaction size"),
                )
                assertEquals(
                    inlineParsed.error?.message,
                    inlineReply.getString(NodeJsRuntimeContract.KEY_BRIDGE_ERROR_MESSAGE),
                )

                broker.dispatch(fetchRequest("binary-1", binary = true), callback)
                val binaryReply = replies.poll(20, TimeUnit.SECONDS)
                assertNotNull("binary reply missing", binaryReply)
                assertEquals(1, oversized.get())
                assertTrue(binaryReply!!.getBoolean(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_OK, false))
                val binaryJson = binaryReply.getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON).orEmpty()
                val binaryResult = JSONObject(binaryJson).getJSONObject("result")
                assertEquals("pfd", binaryResult.getString("bodyTransport"))
                assertEquals(LARGE_BODY_BYTES.toLong(), binaryResult.getLong("bodyBytes"))
                assertFalse(binaryResult.has("bodyBase64"))
                assertEquals(200, binaryResult.getInt("status"))
                assertTrue(binaryJson.length < SIMULATED_BINDER_LIMIT_CHARS)
                assertEquals(
                    "$LARGE_BODY_BYTES|${sha256Hex(patternBytes(LARGE_BODY_BYTES))}|$LARGE_BODY_BYTES",
                    binarySummary.get(),
                )
                assertTrue(
                    "fetch body cache files must not survive delivery",
                    context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("node-fetch-body-") },
                )
            } finally {
                broker.destroy(null)
            }
        }
    }

    @Test
    fun hostStreamsControlledFetchRequestBodiesFromTheDescriptorTransport() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node controlled fetch request body descriptor transport")

        ControlledFetchLoopbackServer().use { server ->
            val baseUrl = server.baseUrl
            val expectedHash = sha256Hex(patternBytes(LARGE_BODY_BYTES))
            val result = NodePluginTestHarness.runPluginScript(
                """
                "nodejs";
                (async function main() {
                  const fetchModule = require("fetch");
                  const axios = require("axios");
                  const crypto = require("crypto");
                  const base = "$baseUrl";
                  const big = Buffer.alloc($LARGE_BODY_BYTES);
                  for (let index = 0; index < big.length; index += 1) big[index] = index % 251;
                  const text = "y".repeat($TEXT_BODY_BYTES);
                  const textHash = crypto.createHash("sha256").update(text, "utf8").digest("hex");
                  async function post(path, body, headers) {
                    const response = await fetchModule(base + path, { method: "POST", body, headers, timeoutMs: 20000 });
                    return Object.assign({ status: response.status }, await response.json());
                  }
                  const bytes = await post("/sha256", big, { "content-type": "application/octet-stream" });
                  const utf8 = await post("/sha256", text, { "content-type": "text/plain" });
                  const view = await post("/sha256", new Uint8Array(big.buffer, big.byteOffset + 1024, $VIEW_BODY_BYTES));
                  const small = await post("/sha256", "tiny");
                  const replayed = await post("/redirect-307", big);
                  const dropped = await post("/redirect-303", big);
                  const viaAxios = await axios.post(base + "/sha256", big, {
                    headers: { "content-type": "application/octet-stream" },
                    timeout: 20000,
                    responseType: "json"
                  });
                  const parallel = await Promise.all(Array.from({ length: 8 }, (_, index) =>
                    post("/sha256", Buffer.alloc($PARALLEL_BODY_BYTES + index, index)).then((reply) => reply.bytes)));
                  console.log("live.fetch.upload=" + [
                    bytes.status, bytes.method, bytes.bytes, bytes.sha256 === "$expectedHash", bytes.contentType,
                    utf8.bytes, utf8.sha256 === textHash, utf8.contentType,
                    view.bytes, small.bytes,
                    replayed.status, replayed.method, replayed.bytes, replayed.sha256 === "$expectedHash",
                    dropped.status, dropped.method, dropped.query,
                    viaAxios.status, viaAxios.data.bytes, viaAxios.data.sha256 === "$expectedHash",
                    parallel.every((length, index) => length === $PARALLEL_BODY_BYTES + index)
                  ].join("|"));
                })();
                """.trimIndent(),
                "<embedded-autojs-fetch-upload-transport.js>",
                timeoutMs = 60_000L,
            )

            NodePluginTestHarness.assertSucceeded("fetch upload transport", result)
            val resultDetails = NodePluginTestHarness.describeResult("fetch upload transport", result)
            assertTrue(
                resultDetails,
                result.stdout.contains(
                    "live.fetch.upload=200|POST|$LARGE_BODY_BYTES|true|application/octet-stream|" +
                        "$TEXT_BODY_BYTES|true|text/plain|$VIEW_BODY_BYTES|4|" +
                        "200|POST|$LARGE_BODY_BYTES|true|200|GET|via=303|200|$LARGE_BODY_BYTES|true|true",
                ),
            )
        }
    }

    @Test
    fun hostBrokerConsumesRequestDescriptorsAndClosesThem() {
        ControlledFetchLoopbackServer().use { server ->
            val broker = NodeJsHostCapabilityBroker(
                context,
                NodePluginScriptRequest(
                    source = "",
                    sourceName = "<broker-request-upload>",
                    workingDirectory = context.cacheDir.absolutePath,
                    sandboxRoot = context.cacheDir.absolutePath,
                    executionId = "broker-request-upload-${System.nanoTime()}",
                ),
                "instrumentation",
            )
            val payloadFile = File.createTempFile("node-upload-", ".bin", context.cacheDir)
            try {
                payloadFile.writeBytes(patternBytes(LARGE_BODY_BYTES))
                assertEquals(
                    NodeJsRuntimeContract.BRIDGE_REQUEST_BINARY_TRANSPORT_PFD,
                    broker.getBrokerInfo().getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_BINARY_TRANSPORT),
                )
                val replies = LinkedBlockingQueue<Bundle>()
                val callback = object : INodeJsHostCapabilityCallback.Stub() {
                    override fun onResponse(response: Bundle) {
                        replies.add(response)
                    }
                }

                fun descriptor(method: String, bodyBytes: Long): JSONObject = JSONObject()
                    .put("url", "${server.baseUrl}/sha256")
                    .put("method", method)
                    .put("headers", JSONArray().put(JSONArray().put("content-type").put("application/octet-stream")))
                    .put("bodyTransport", "pfd")
                    .put("bodyBytes", bodyBytes)
                    .put("timeoutMs", 20_000)

                fun dispatch(id: String, descriptor: JSONObject, byteCount: Long): ParcelFileDescriptor {
                    val upload = ParcelFileDescriptor.open(payloadFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val request = JSONObject()
                        .put("id", id)
                        .put("module", "fetch")
                        .put("method", "request")
                        .put("args", JSONArray().put(descriptor))
                        .put("timeoutMs", 20_000)
                        .put("permissions", JSONArray().put("network"))
                        .put("upload", JSONObject().put("byteCount", byteCount))
                    broker.dispatch(
                        Bundle().apply {
                            putString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON, request.toString())
                            putParcelable(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_BINARY_PFD, upload)
                            putLong(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_BINARY_BYTE_COUNT, byteCount)
                        },
                        callback,
                    )
                    return upload
                }

                fun reply(): Bundle {
                    val bundle = replies.poll(30, TimeUnit.SECONDS)
                    assertNotNull("broker reply missing", bundle)
                    return bundle!!
                }

                val streamed = dispatch("upload-1", descriptor("POST", LARGE_BODY_BYTES.toLong()), LARGE_BODY_BYTES.toLong())
                val streamedReply = reply()
                assertTrue(
                    streamedReply.getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON).orEmpty(),
                    streamedReply.getBoolean(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_OK, false),
                )
                val streamedResult = JSONObject(streamedReply.getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON).orEmpty())
                    .getJSONObject("result")
                val echoed = JSONObject(String(Base64.getDecoder().decode(streamedResult.getString("bodyBase64")), Charsets.UTF_8))
                assertEquals("POST", echoed.getString("method"))
                assertEquals(LARGE_BODY_BYTES, echoed.getInt("bytes"))
                assertEquals(sha256Hex(patternBytes(LARGE_BODY_BYTES)), echoed.getString("sha256"))
                assertEquals("application/octet-stream", echoed.getString("contentType"))
                // In-process the broker receives this very object, so its close is observable here.
                assertFalse("the broker closes the descriptor once the call has answered", streamed.fileDescriptor.valid())

                val mismatched = dispatch("upload-2", descriptor("POST", 12L), LARGE_BODY_BYTES.toLong())
                val mismatchedParsed = NodeBridgeResponse.fromJson(reply().getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON).orEmpty())
                assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, mismatchedParsed.error?.code)
                assertTrue(mismatchedParsed.error?.message.orEmpty(), mismatchedParsed.error?.message.orEmpty().contains("bodyBytes (12)"))
                assertFalse(mismatched.fileDescriptor.valid())

                val getWithBody = dispatch("upload-3", descriptor("GET", LARGE_BODY_BYTES.toLong()), LARGE_BODY_BYTES.toLong())
                val getParsed = NodeBridgeResponse.fromJson(reply().getString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON).orEmpty())
                assertEquals(NodeRuntimeErrorCodes.NETWORK_PERMISSION_DENIED, getParsed.error?.code)
                assertFalse(getWithBody.fileDescriptor.valid())
            } finally {
                payloadFile.delete()
                broker.destroy(null)
            }
        }
    }

    @Test
    fun jsFetchAndAxiosUseAndroidLiveProvider() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node controlled fetch live provider smoke")

        ControlledFetchLoopbackServer().use { server ->
            val baseUrl = server.baseUrl
            val result = NodePluginTestHarness.runPluginScript(
                """
                "nodejs";
                (async function main() {
                  const { Buffer } = require("buffer");
                  const fetchModule = require("fetch");
                  const axios = require("axios");
                  const http = require("http");
                  const base = "$baseUrl";
                  function codeOf(error) {
                    return error && (error.autojs6Code || error.code) || "";
                  }

                  function readHttpJson(url) {
                    return new Promise((resolve, reject) => {
                      const req = http.get(url, {
                        headers: { "x-test": "http" },
                        timeout: 2000
                      }, (res) => {
                        const chunks = [];
                        res.setEncoding("utf8");
                        res.on("data", (chunk) => chunks.push(chunk));
                        res.on("end", () => {
                          const body = JSON.parse(chunks.join(""));
                          resolve({
                            status: res.statusCode,
                            method: body.method,
                            query: body.query,
                            header: body.header
                          });
                        });
                        res.on("error", reject);
                      });
                      req.on("error", reject);
                    });
                  }

                  const getResponse = await fetchModule(base + "/get?via=fetch", {
                    headers: { "x-test": "live" },
                    timeoutMs: 2000,
                    maxResponseBytes: 2048
                  });
                  const getJson = await getResponse.json();

                  const postTextResponse = await fetchModule(base + "/echo", {
                    method: "POST",
                    headers: { "content-type": "text/plain" },
                    body: "payload",
                    timeoutMs: 2000,
                    maxResponseBytes: 2048
                  });
                  const postTextJson = await postTextResponse.json();

                  const postBytesResponse = await fetchModule(base + "/echo", {
                    method: "POST",
                    headers: { "x-body": "uint8" },
                    body: new Uint8Array([65, 66, 67]),
                    timeoutMs: 2000,
                    maxResponseBytes: 2048
                  });
                  const postBytesJson = await postBytesResponse.json();

                  const bytesResponse = await fetchModule(base + "/bytes", {
                    timeoutMs: 2000,
                    maxResponseBytes: 16
                  });
                  const bytesHex = Buffer.from(new Uint8Array(await bytesResponse.arrayBuffer())).toString("hex");

                  const redirectResponse = await fetchModule(base + "/redirect", {
                    timeoutMs: 2000,
                    maxRedirects: 1
                  });
                  const redirectJson = await redirectResponse.json();

                  let largeCode = "";
                  try {
                    await fetchModule(base + "/large", { timeoutMs: 2000, maxResponseBytes: 3 });
                  } catch (error) {
                    largeCode = codeOf(error);
                  }

                  let timeoutCode = "";
                  try {
                    await fetchModule(base + "/slow", { timeoutMs: 100, maxResponseBytes: 2048 });
                  } catch (error) {
                    timeoutCode = codeOf(error);
                  }

                  const axiosGet = await axios.get("/get", {
                    baseURL: base,
                    params: { via: "axios", tag: ["a", "b"] },
                    headers: { "x-test": "axios" },
                    timeout: 2000,
                    responseType: "json"
                  });
                  const axiosPost = await axios.post(base + "/echo", { hello: "axios" }, {
                    headers: { "x-test": "axios-post" },
                    timeout: 2000,
                    responseType: "json"
                  });
                  const httpGet = await readHttpJson(base + "/get?via=http");

                  // Raw http/net are native Node here (rawNodeNetworkModulesEnabled defaults to true);
                  // close what the probes open so the event loop can exit.
                  let httpListenCode = "";
                  try {
                    const server = http.createServer(() => {});
                    await new Promise((resolve, reject) => server.once("error", reject).listen(0, "127.0.0.1", resolve));
                    httpListenCode = typeof server.address().port === "number" ? "native" : "unknown";
                    await new Promise((resolve) => server.close(resolve));
                  } catch (error) {
                    httpListenCode = codeOf(error);
                  }
                  let rawNetCode = "";
                  try {
                    const socket = require("net").connect(Number(new URL(base).port), "127.0.0.1");
                    await new Promise((resolve, reject) => socket.once("connect", resolve).once("error", reject));
                    socket.destroy();
                    rawNetCode = "native";
                  } catch (error) {
                    rawNetCode = codeOf(error);
                  }

                  const diagnostics = fetchModule.diagnostics();
                  console.log("live.fetch.get=" + [
                    getResponse.status,
                    getJson.method,
                    getJson.query,
                    getJson.header
                  ].join("|"));
                  console.log("live.fetch.postText=" + [
                    postTextResponse.status,
                    postTextJson.method,
                    postTextJson.body,
                    postTextJson.contentType
                  ].join("|"));
                  console.log("live.fetch.postBytes=" + [
                    postBytesResponse.status,
                    postBytesJson.method,
                    postBytesJson.body,
                    postBytesJson.header
                  ].join("|"));
                  console.log("live.fetch.bytes=" + [bytesResponse.status, bytesHex].join("|"));
                  console.log("live.fetch.redirect=" + [redirectResponse.status, redirectJson.query].join("|"));
                  console.log("live.axios.get=" + [
                    axiosGet.status,
                    axiosGet.data.method,
                    axiosGet.data.query,
                    axiosGet.data.header
                  ].join("|"));
                  console.log("live.axios.post=" + [
                    axiosPost.status,
                    axiosPost.data.method,
                    axiosPost.data.body,
                    axiosPost.data.header
                  ].join("|"));
                  console.log("live.http.get=" + [
                    httpGet.status,
                    httpGet.method,
                    httpGet.query,
                    httpGet.header
                  ].join("|"));
                  console.log("live.fetch.denials=" + [largeCode, timeoutCode, httpListenCode, rawNetCode].join("|"));
                  console.log("live.fetch.diagnostics=" + [
                    diagnostics.activeRequestCount,
                    diagnostics.completedCount,
                    diagnostics.timeoutCount,
                    diagnostics.rejectedByPolicyCount
                  ].join("|"));
                })();
                """.trimIndent(),
                "<embedded-autojs-fetch-live-provider-source.js>",
                timeoutMs = 15_000L,
            )

            NodePluginTestHarness.assertSucceeded("fetch live provider", result)
            val resultDetails = NodePluginTestHarness.describeResult("fetch live provider", result)
            assertTrue(resultDetails, result.stdout.contains("live.fetch.get=200|GET|via=fetch|live"))
            assertTrue(resultDetails, result.stdout.contains("live.fetch.postText=201|POST|payload|text/plain"))
            assertTrue(resultDetails, result.stdout.contains("live.fetch.postBytes=201|POST|ABC|uint8"))
            assertTrue(resultDetails, result.stdout.contains("live.fetch.bytes=200|070809"))
            assertTrue(resultDetails, result.stdout.contains("live.fetch.redirect=200|via=redirect"))
            assertTrue(resultDetails, result.stdout.contains("live.axios.get=200|GET|via=axios&tag=a&tag=b|axios"))
            assertTrue(resultDetails, result.stdout.contains("""live.axios.post=201|POST|{"hello":"axios"}|axios-post"""))
            assertTrue(resultDetails, result.stdout.contains("live.http.get=200|GET|via=http|http"))
            assertTrue(
                resultDetails,
                result.stdout.contains(
                    "live.fetch.denials=ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|ERR_AUTOJS6_NETWORK_TIMEOUT|native|native",
                ),
            )
            assertTrue(resultDetails, result.stdout.contains("live.fetch.diagnostics=0|7|1|1"))
        }
    }

    @Test
    fun androidFetchProviderCancelsActiveLiveRequestOnDestroy() {
        ControlledFetchLoopbackServer().use { server ->
            val provider = AndroidNodeBridgeCapabilityProvider(context)
            val callbackLatch = CountDownLatch(1)
            var response: NodeBridgeResponse? = null

            provider.dispatch(
                NodeBridgeRequest(
                    id = "fetch-live-destroy-${System.nanoTime()}",
                    module = "fetch",
                    method = "request",
                    argsJson = JSONArray().put(
                        JSONObject().apply {
                            put("url", "${server.baseUrl}/slow")
                            put("method", "GET")
                            put("timeoutMs", 5_000)
                            put("maxResponseBytes", 1024)
                        },
                    ).toString(),
                    timeoutMs = 5_000L,
                    permissions = listOf("network"),
                ),
            ) { bridgeResponse ->
                response = bridgeResponse
                callbackLatch.countDown()
            }

            assertTrue("slow fetch request did not reach loopback server", server.awaitSlowRequest())
            provider.destroy(NodeBridgeError.processDead("fetch live destroy test"))
            assertTrue("destroy did not cancel the active fetch request", callbackLatch.await(5, TimeUnit.SECONDS))

            val failure = response ?: error("Missing fetch destroy response")
            assertFalse(failure.ok)
            assertEquals(NodeRuntimeErrorCodes.NETWORK_ABORTED, failure.error?.code)
            provider.nativeDiagnostics().also { diagnostics ->
                assertEquals("0", diagnostics["embedded_script.fetch_active_request_count"])
                assertEquals("1", diagnostics["embedded_script.fetch_service_destroy_cancellation_count"])
            }
        }
    }

    internal class ControlledFetchLoopbackServer : AutoCloseable {

        private val executor = Executors.newCachedThreadPool()
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val slowRequestStarted = CountDownLatch(1)

        @Volatile
        private var running = true

        val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"

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

        fun awaitSlowRequest(): Boolean = slowRequestStarted.await(2, TimeUnit.SECONDS)

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }

        private fun handle(socket: Socket) {
            socket.soTimeout = 5_000
            val input = socket.getInputStream()
            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 2) {
                writeResponse(socket, HttpResponse(400, "Bad Request", "text/plain", "bad request".toByteArray()))
                return
            }
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) {
                    break
                }
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }
            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val body = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(body, offset, contentLength - offset)
                if (read < 0) {
                    break
                }
                offset += read
            }

            val uri = URI(parts[1])
            val request = HttpRequest(
                method = parts[0],
                path = uri.path.ifBlank { "/" },
                query = uri.rawQuery.orEmpty(),
                headers = headers,
                body = if (offset == body.size) body else body.copyOf(offset),
            )
            writeResponse(socket, route(request))
        }

        private fun route(request: HttpRequest): HttpResponse {
            return when (request.path) {
                "/get" -> jsonResponse(
                    200,
                    "OK",
                    JSONObject()
                        .put("method", request.method)
                        .put("query", request.query)
                        .put("header", request.headers["x-test"].orEmpty()),
                )
                "/echo" -> jsonResponse(
                    201,
                    "Created",
                    JSONObject()
                        .put("method", request.method)
                        .put("body", request.body.toString(Charsets.UTF_8))
                        .put("contentType", request.headers["content-type"].orEmpty())
                        .put("header", request.headers["x-test"] ?: request.headers["x-body"].orEmpty()),
                )
                "/bytes" -> HttpResponse(200, "OK", "application/octet-stream", byteArrayOf(7, 8, 9))
                "/redirect" -> HttpResponse(
                    302,
                    "Found",
                    "text/plain",
                    ByteArray(0),
                    listOf("Location" to "/get?via=redirect"),
                )
                "/large" -> HttpResponse(200, "OK", "text/plain", "abcdef".toByteArray())
                "/large-bytes" -> HttpResponse(
                    200,
                    "OK",
                    "application/octet-stream",
                    patternBytes(request.query.substringAfter("n=", "").substringBefore('&').toIntOrNull() ?: 0),
                )
                "/empty" -> HttpResponse(200, "OK", "text/plain", ByteArray(0))
                "/sha256" -> jsonResponse(
                    200,
                    "OK",
                    JSONObject()
                        .put("method", request.method)
                        .put("bytes", request.body.size)
                        .put("sha256", sha256Hex(request.body))
                        .put("contentType", request.headers["content-type"].orEmpty()),
                )
                "/redirect-307" -> HttpResponse(307, "Temporary Redirect", "text/plain", ByteArray(0), listOf("Location" to "/sha256"))
                "/redirect-303" -> HttpResponse(303, "See Other", "text/plain", ByteArray(0), listOf("Location" to "/get?via=303"))
                "/redirect-with-body" -> HttpResponse(
                    302,
                    "Found",
                    "application/octet-stream",
                    patternBytes(4096),
                    listOf("Location" to "/get?via=redirect-body"),
                )
                "/slow" -> {
                    slowRequestStarted.countDown()
                    Thread.sleep(2_000)
                    HttpResponse(200, "OK", "text/plain", "slow".toByteArray())
                }
                else -> HttpResponse(404, "Not Found", "text/plain", "not found".toByteArray())
            }
        }

        private fun jsonResponse(status: Int, statusText: String, body: JSONObject): HttpResponse {
            return HttpResponse(status, statusText, "application/json", body.toString().toByteArray(Charsets.UTF_8))
        }

        private fun writeResponse(socket: Socket, response: HttpResponse) {
            val output = socket.getOutputStream()
            val headers = buildString {
                append("HTTP/1.1 ${response.status} ${response.statusText}\r\n")
                append("Content-Type: ${response.contentType}\r\n")
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n")
                response.headers.forEach { (name, value) ->
                    append("$name: $value\r\n")
                }
                append("\r\n")
            }.toByteArray(Charsets.ISO_8859_1)
            output.write(headers)
            output.write(response.body)
            output.flush()
        }

        private fun readAsciiLine(input: java.io.InputStream): String? {
            val output = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) {
                    return if (output.size() == 0) null else String(output.toByteArray(), Charsets.ISO_8859_1).trimEnd('\r')
                }
                if (value == '\n'.code) {
                    return String(output.toByteArray(), Charsets.ISO_8859_1).trimEnd('\r')
                }
                output.write(value)
            }
        }
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private data class HttpResponse(
        val status: Int,
        val statusText: String,
        val contentType: String,
        val body: ByteArray,
        val headers: List<Pair<String, String>> = emptyList(),
    )

    private companion object {
        const val LARGE_BODY_BYTES = 3 * 1024 * 1024
        const val SIMULATED_BINDER_LIMIT_CHARS = 1_000_000
        const val TEXT_BODY_BYTES = 400 * 1024
        const val VIEW_BODY_BYTES = 512 * 1024
        const val PARALLEL_BODY_BYTES = 300 * 1024

        fun patternBytes(count: Int): ByteArray = ByteArray(count.coerceAtLeast(0)) { index -> (index % 251).toByte() }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
