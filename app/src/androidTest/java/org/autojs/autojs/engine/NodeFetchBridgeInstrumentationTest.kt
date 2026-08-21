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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
              const get = await fetch("https://example.invalid/get", {
                headers: { "x-test": "yes" },
                timeoutMs: 1000,
                maxResponseBytes: 1024
              });
              const getBodyStreamText = await readWebStream(get.body.stream());
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
                await fetch("https://example.invalid/timeout", { timeoutMs: 25 });
              } catch (error) {
                timeoutCode = error && (error.autojs6Code || error.code) || "";
              }

              let abortCode = "";
              const controller = new AbortController();
              const abortPromise = fetch("https://example.invalid/abort", {
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
                await fetch("https://example.invalid/large", {
                  timeoutMs: 1000,
                  maxResponseBytes: 3
                });
              } catch (error) {
                largeCode = error && (error.autojs6Code || error.code) || "";
              }

              let rawHttpCode = "";
              try {
                require("http").request("http://example.invalid");
              } catch (error) {
                rawHttpCode = error && (error.autojs6Code || error.code) || "";
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
              console.log("fetch.raw_http=" + rawHttpCode);
              console.log("fetch.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-fetch-fake-source.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodePluginTestHarness.assertSucceeded("fetch fake bridge", result)
        assertTrue(result.stdout.contains("fetch.resolve=fetch"))
        assertTrue(result.stdout.contains("fetch.shadow=false"))
        assertTrue(result.stdout.contains("fetch.frozen=true"))
        assertTrue(result.stdout.contains("fetch.aliases=function|function|true|true"))
        assertTrue(result.stdout.contains("fetch.get=200|OK|true|https://example.invalid/get|yes|hello"))
        assertTrue(result.stdout.contains("fetch.get_body_stream=hello"))
        assertTrue(result.stdout.contains("fetch.post=201|true|POST|payload|text/plain"))
        assertTrue(result.stdout.contains("fetch.requests=GET:get|POST:post|GET:timeout|GET:abort|GET:large"))
        assertTrue(result.stdout.contains("fetch.permissions=true"))
        assertTrue(result.stdout.contains("fetch.timeout=ERR_AUTOJS6_NETWORK_TIMEOUT"))
        assertTrue(result.stdout.contains("fetch.abort=ERR_AUTOJS6_NETWORK_ABORTED"))
        assertTrue(result.stdout.contains("fetch.large=ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE"))
        assertTrue(result.stdout.contains("fetch.raw_http=ERR_AUTOJS6_NETWORK_DISABLED"))
        assertTrue(result.stdout.contains("fetch.pending=0"))
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

              const stringResponse = await fetch("https://example.invalid/string", {
                method: "POST",
                headers: [["content-type", "text/plain"], ["x-mode", "string"]],
                body: "alpha",
                timeoutMs: 1000
              });
              const stringJson = await stringResponse.json();

              const uint8Response = await fetch("https://example.invalid/uint8", {
                method: "POST",
                headers: { "x-mode": "uint8" },
                body: new Uint8Array([98, 105, 110]),
                timeoutMs: 1000
              });
              const uint8Json = await uint8Response.json();

              const arrayResponse = await fetch(new URL("https://example.invalid/array"), {
                headers: [["x-array", "yes"]],
                timeoutMs: 1000,
                maxResponseBytes: 8
              });
              const arrayHex = Buffer.from(new Uint8Array(await arrayResponse.arrayBuffer())).toString("hex");

              const manualResponse = await fetch("https://example.invalid/manual", {
                redirect: "manual",
                timeoutMs: 1000
              });
              const manualText = await manualResponse.text();

              let redirectErrorCode = "";
              try {
                await fetch("https://example.invalid/error-redirect", {
                  redirect: "error",
                  timeoutMs: 1000
                });
              } catch (error) {
                redirectErrorCode = codeOf(error);
              }

              let getBodyCode = "";
              try {
                await fetch("https://example.invalid/get-body", {
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
                await fetch("https://example.invalid/abort-before", {
                  signal: controller.signal,
                  timeoutMs: 1000
                });
              } catch (error) {
                abortBeforeCode = codeOf(error);
              }
              const abortBeforeStarted = requests.length > requestsBeforeAbort;

              let rawNodeHttpsCode = "";
              try {
                require("node:https").get("https://example.invalid/");
              } catch (error) {
                rawNodeHttpsCode = codeOf(error);
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
                rawNodeHttpsCode
              ].join("|"));
              console.log("fetch.surface.requests=" + requests.map((request) => request.args[0].method + ":" + request.args[0].url.split("/").pop()).join("|"));
              console.log("fetch.surface.policyFrozen=" + (Object.isFrozen(fetchModule.policy) && Object.isFrozen(fetchModule.policy.allowedSchemes)));
              console.log("fetch.surface.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-fetch-partial-surface-source.js>",
            timeoutMs = 10_000L,
        )

        NodePluginTestHarness.assertSucceeded("fetch partial surface", result)
        assertTrue(result.stdout.contains("fetch.surface.string=POST|alpha|text|text/plain|string"))
        assertTrue(result.stdout.contains("fetch.surface.uint8=POST|bin|base64|uint8"))
        assertTrue(result.stdout.contains("fetch.surface.arrayBuffer=200|yes|0102ff"))
        assertTrue(result.stdout.contains("fetch.surface.redirectManual=302|Found|/manual-target|manual"))
        assertTrue(
            result.stdout.contains(
                "fetch.surface.denials=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_ABORTED|false|ERR_AUTOJS6_NETWORK_DISABLED",
            ),
        )
        assertTrue(result.stdout.contains("fetch.surface.requests=POST:string|POST:uint8|GET:array|GET:manual|GET:error-redirect"))
        assertTrue(result.stdout.contains("fetch.surface.policyFrozen=true"))
        assertTrue(result.stdout.contains("fetch.surface.pending=0"))
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
                    send(request, {
                      url: payload.url,
                      status: 200,
                      statusText: "OK",
                      headers: [["content-type", "text/plain"]],
                      bodyBase64: responseBody("abcdef"),
                      bodyBytes: 6
                    });
                    return;
                  }
                  send(request, okResponse(payload, "ok"));
                }
              });

              const holdCount = fetchModule.policy.maxConcurrentRequests;
              const holdPromises = Array.from({ length: holdCount }, (_, index) =>
                fetch("https://example.invalid/hold?i=" + index, { timeoutMs: 1000 }).then((response) => response.text())
              );
              await waitFor(() => held.length === holdCount, "held requests");

              let concurrentCode = "";
              try {
                await fetch("https://example.invalid/hold?overflow=true", { timeoutMs: 1000 });
              } catch (error) {
                concurrentCode = codeOf(error);
              }

              held.splice(0).forEach((request) => {
                const payload = request.args[0];
                send(request, okResponse(payload, "hold-" + payload.url.split("=").pop()));
              });
              const holdTexts = await Promise.all(holdPromises);

              const schemeCodes = [];
              for (const url of ["file:///tmp/a", "content://autojs6/a", "about:blank", "data:text/plain,hi"]) {
                try {
                  await fetch(url, { timeoutMs: 1000 });
                } catch (error) {
                  schemeCodes.push(codeOf(error));
                }
              }

              let timeoutCode = "";
              try {
                await fetch("https://example.invalid/timeout", { timeoutMs: 25 });
              } catch (error) {
                timeoutCode = codeOf(error);
              }

              let abortCode = "";
              const controller = new AbortController();
              const abortPromise = fetch("https://example.invalid/abort", {
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
                await fetch("https://example.invalid/redirect-loop", { timeoutMs: 1000, maxRedirects: 1 });
              } catch (error) {
                redirectCode = codeOf(error);
              }

              let largeCode = "";
              try {
                await fetch("https://example.invalid/large", { timeoutMs: 1000, maxResponseBytes: 3 });
              } catch (error) {
                largeCode = codeOf(error);
              }

              let destroyCode = "";
              const destroyPromise = fetch("https://example.invalid/destroy", { timeoutMs: 1000 }).catch(codeOf);
              await waitFor(() => requests.some((request) => request.args[0].url.indexOf("/destroy") >= 0), "destroy request");
              bridge.__test.destroy("service destroy test");
              destroyCode = await destroyPromise;

              let rawHttpsCode = "";
              try {
                require("https").request("https://example.invalid/");
              } catch (error) {
                rawHttpsCode = codeOf(error);
              }
              let rawNetCode = "";
              try {
                require("net").connect(443, "127.0.0.1");
              } catch (error) {
                rawNetCode = codeOf(error);
              }

              const diagnostics = fetchModule.diagnostics();
              console.log("fetch.policy=" + [
                fetchModule.policy.maxConcurrentRequests,
                fetchModule.policy.defaultTimeoutMs,
                fetchModule.policy.defaultMaxResponseBytes,
                fetchModule.policy.defaultMaxRedirects
              ].join("|"));
              console.log("fetch.harden.codes=" + [
                concurrentCode,
                timeoutCode,
                abortCode,
                redirectCode,
                largeCode,
                destroyCode,
                rawHttpsCode,
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
        )

        NodePluginTestHarness.assertSucceeded("fetch policy hardening", result)
        val resultDetails = NodePluginTestHarness.describeResult("fetch policy hardening output", result)
        assertTrue(resultDetails, result.stdout.contains("fetch.policy=16|30000|33554432|5"))
        assertTrue(
            resultDetails,
            result.stdout.contains(
                "fetch.harden.codes=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_TIMEOUT|ERR_AUTOJS6_NETWORK_ABORTED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD||ERR_AUTOJS6_NETWORK_DISABLED",
            ),
        )
        assertTrue(
            resultDetails,
            result.stdout.contains(
                "fetch.harden.schemes=ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL",
            ),
        )
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.redirect_requests=2"))
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.holds=hold-0|hold-1|hold-2|hold-3|hold-4|hold-5|hold-6|hold-7|hold-8|hold-9|hold-10|hold-11|hold-12|hold-13|hold-14|hold-15"))
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.diagnostics=0|16|1|7|1"))
        assertTrue(resultDetails, result.stdout.contains("fetch.harden.pending=0"))
    }

    @Test
    fun jsAxiosCompatibilityLayerUsesControlledFetchOnly() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node axios controlled fetch compatibility regression")

        val workDir = File(context.cacheDir, "node-bridge-axios-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create axios bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/axios").apply {
            check(mkdirs()) { "Failed to create axios shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

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

              let rawHttpsCode = "";
              try {
                require("node:https").request("https://example.invalid/");
              } catch (error) {
                rawHttpsCode = codeOf(error);
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
              console.log("axios.raw_https=" + rawHttpsCode);
              console.log("axios.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-axios-controlled-fetch-source.js>",
            workingDirectory = workDir,
            timeoutMs = 10_000L,
        )

        NodePluginTestHarness.assertSucceeded("axios controlled fetch", result)
        assertTrue(result.stdout.contains("axios.resolve=axios"))
        assertTrue(result.stdout.contains("axios.shadow=false"))
        assertTrue(result.stdout.contains("axios.surface=function|function|function|function|true|true|autojs6-controlled-fetch"))
        assertTrue(
            result.stdout.contains(
                "axios.get=200|GET|https://api.example.invalid/v1/items?tag=a&tag=b&q=one+two|get|get",
            ),
        )
        assertTrue(result.stdout.contains("""axios.post=201|Created|POST|{"hello":"world"}|application/json"""))
        assertTrue(result.stdout.contains("axios.responseTypes=plain-text|070809"))
        assertTrue(result.stdout.contains("axios.statusError=true|AxiosError|ERR_BAD_RESPONSE|500|bad"))
        assertTrue(result.stdout.contains("axios.timeout=true|AxiosError|ERR_AUTOJS6_NETWORK_TIMEOUT"))
        assertTrue(
            result.stdout.contains(
                "axios.policy=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED",
            ),
        )
        assertTrue(
            result.stdout.contains(
                "axios.requests=GET:items?tag=a&tag=b&q=one+two|POST:post|GET:text|GET:array|GET:status|GET:timeout|GET:policy",
            ),
        )
        assertTrue(result.stdout.contains("axios.permissions=true"))
        assertTrue(result.stdout.contains("axios.raw_https=ERR_AUTOJS6_NETWORK_DISABLED"))
        assertTrue(result.stdout.contains("axios.pending=0"))
    }

    @Test
    fun jsFetchAndAxiosUseAndroidLiveProviderWhenNetworkEnabled() {
        assumeTrue(
            "Build with -Pautojs.nodejs.network.stable=true to run the live controlled fetch provider smoke.",
            BuildConfig.NODEJS_NETWORK_ENABLED,
        )
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

                  const getResponse = await fetch(base + "/get?via=fetch", {
                    headers: { "x-test": "live" },
                    timeoutMs: 2000,
                    maxResponseBytes: 2048
                  });
                  const getJson = await getResponse.json();

                  const postTextResponse = await fetch(base + "/echo", {
                    method: "POST",
                    headers: { "content-type": "text/plain" },
                    body: "payload",
                    timeoutMs: 2000,
                    maxResponseBytes: 2048
                  });
                  const postTextJson = await postTextResponse.json();

                  const postBytesResponse = await fetch(base + "/echo", {
                    method: "POST",
                    headers: { "x-body": "uint8" },
                    body: new Uint8Array([65, 66, 67]),
                    timeoutMs: 2000,
                    maxResponseBytes: 2048
                  });
                  const postBytesJson = await postBytesResponse.json();

                  const bytesResponse = await fetch(base + "/bytes", {
                    timeoutMs: 2000,
                    maxResponseBytes: 16
                  });
                  const bytesHex = Buffer.from(new Uint8Array(await bytesResponse.arrayBuffer())).toString("hex");

                  const redirectResponse = await fetch(base + "/redirect", {
                    timeoutMs: 2000,
                    maxRedirects: 1
                  });
                  const redirectJson = await redirectResponse.json();

                  let largeCode = "";
                  try {
                    await fetch(base + "/large", { timeoutMs: 2000, maxResponseBytes: 3 });
                  } catch (error) {
                    largeCode = codeOf(error);
                  }

                  let timeoutCode = "";
                  try {
                    await fetch(base + "/slow", { timeoutMs: 100, maxResponseBytes: 2048 });
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

                  let httpListenCode = "";
                  try {
                    http.createServer(() => {}).listen(0);
                  } catch (error) {
                    httpListenCode = codeOf(error);
                  }
                  let rawNetCode = "";
                  try {
                    require("net").connect(80, "127.0.0.1");
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
                    "live.fetch.denials=ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|ERR_AUTOJS6_NETWORK_TIMEOUT|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED",
                ),
            )
            assertTrue(resultDetails, result.stdout.contains("live.fetch.diagnostics=0|8|1|1"))
        }
    }

    @Test
    fun androidFetchProviderCancelsActiveLiveRequestOnDestroy() {
        assumeTrue(
            "Build with -Pautojs.nodejs.network.stable=true to run the live controlled fetch cancellation smoke.",
            BuildConfig.NODEJS_NETWORK_ENABLED,
        )

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

    @Test
    fun androidFetchProviderStaysDisabledInNormalBuilds() {
        assumeFalse(
            "This default-denial regression is only meaningful when autojs.nodejs.network.stable=false.",
            BuildConfig.NODEJS_NETWORK_ENABLED,
        )

        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val responses = mutableListOf<NodeBridgeResponse>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "fetch-disabled-${System.nanoTime()}",
                module = "fetch",
                method = "request",
                argsJson = JSONArray().put(
                    JSONObject().apply {
                        put("url", "https://example.invalid/")
                        put("method", "GET")
                        put("timeoutMs", 1000)
                        put("maxResponseBytes", 1024)
                    },
                ).toString(),
                timeoutMs = 1000L,
                permissions = listOf("network"),
            ),
        ) { response -> responses += response }

        assertEquals(1, responses.size)
        assertFalse(responses.single().ok)
        assertEquals(NodeRuntimeErrorCodes.NETWORK_DISABLED, responses.single().error?.code)
        assertEquals("fetch", responses.single().error?.module)
        assertEquals("request", responses.single().error?.method)
    }

    private class ControlledFetchLoopbackServer : AutoCloseable {

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
}
