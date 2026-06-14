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
