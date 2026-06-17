package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64

class NodeFetchBridgeInstrumentationTest {

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
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fetch bridge focused regression")

        val workDir = File(context.cacheDir, "node-bridge-fetch-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fetch bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/fetch").apply {
            check(mkdirs()) { "Failed to create fetch shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
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

        NodeEmbeddedTestHarness.assertSucceeded("fetch fake bridge", result)
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
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fetch partial surface regression")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
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

        NodeEmbeddedTestHarness.assertSucceeded("fetch partial surface", result)
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
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node fetch policy hardening regression")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
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
                for (let i = 0; i < 50; i += 1) {
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

              const holdPromises = [0, 1, 2, 3].map((index) =>
                fetch("https://example.invalid/hold?i=" + index, { timeoutMs: 1000 }).then((response) => response.text())
              );
              await waitFor(() => held.length === 4, "held requests");

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

        NodeEmbeddedTestHarness.assertSucceeded("fetch policy hardening", result)
        assertTrue(result.stdout.contains("fetch.policy=4|30000|1048576|5"))
        assertTrue(
            result.stdout.contains(
                "fetch.harden.codes=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_TIMEOUT|ERR_AUTOJS6_NETWORK_ABORTED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED",
            ),
        )
        assertTrue(
            result.stdout.contains(
                "fetch.harden.schemes=ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL|ERR_AUTOJS6_NETWORK_INVALID_URL",
            ),
        )
        assertTrue(result.stdout.contains("fetch.harden.redirect_requests=2"))
        assertTrue(result.stdout.contains("fetch.harden.holds=hold-0|hold-1|hold-2|hold-3"))
        assertTrue(result.stdout.contains("fetch.harden.diagnostics=0|4|1|7|1"))
        assertTrue(result.stdout.contains("fetch.harden.pending=0"))
    }

    @Test
    fun androidFetchProviderStaysDisabledInNormalBuilds() {
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
}
