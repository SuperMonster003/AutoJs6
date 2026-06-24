package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeHttpDesktopParityInstrumentationTest {

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
    fun httpHttpsClientDesktopParityStaysControlledAndFailClosed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-29 HTTP desktop parity focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network"))
        val workDir = File(context.cacheDir, "node-http-desktop-parity-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create HTTP desktop parity work dir: $absolutePath" }
        }
        try {
            writeShadowModule(workDir, "http")
            writeShadowModule(workDir, "https")
            writeShadowModule(workDir, "axios")

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const bridge = require("autojs6:bridge");
                      const { Buffer } = require("buffer");
                      const http = require("http");
                      const nodeHttp = require("node:http");
                      const https = require("https");
                      const nodeHttps = require("node:https");
                      const axios = require("axios");
                      const fetchModule = require("fetch");
                      const requests = [];

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }

                      function syncCode(action) {
                        try {
                          action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      async function asyncCode(action) {
                        try {
                          await action();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      function pathOf(url) {
                        const parsed = new URL(url);
                        return parsed.pathname + parsed.search;
                      }

                      function requestError(mod, url, options) {
                        return new Promise((resolve) => {
                          const req = mod.request(url, Object.assign({ timeout: 1000 }, options || {}), () => {});
                          req.on("error", (error) => resolve(codeOf(error)));
                          req.end();
                        });
                      }

                      function read(mod, url, options, body) {
                        return new Promise((resolve, reject) => {
                          const req = mod.request(url, Object.assign({ timeout: 1000 }, options || {}), (res) => {
                            const chunks = [];
                            res.setEncoding("utf8");
                            res.on("data", (chunk) => chunks.push(chunk));
                            res.on("end", () => resolve({
                              statusCode: res.statusCode,
                              statusMessage: res.statusMessage,
                              headers: res.headers,
                              body: chunks.join("")
                            }));
                            res.on("error", reject);
                          });
                          req.on("error", reject);
                          if (body !== undefined) {
                            req.write(body);
                          }
                          req.end();
                        });
                      }

                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          const payload = request.args[0];
                          const path = pathOf(payload.url);
                          if (path.indexOf("/timeout") === 0) {
                            return;
                          }
                          if (path.indexOf("/tls-error") === 0) {
                            setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                              id: request.id,
                              ok: false,
                              error: {
                                name: "Error",
                                message: "fake TLS handshake failed",
                                code: "ERR_AUTOJS6_NETWORK_ERROR",
                                category: "network-error",
                                module: "fetch",
                                method: "request"
                              }
                            })));
                            return;
                          }
                          if (path.indexOf("/redirect") === 0) {
                            setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                              id: request.id,
                              ok: true,
                              result: {
                                url: payload.url,
                                status: 302,
                                statusText: "Found",
                                headers: [["location", "/target"]],
                                bodyBase64: Buffer.from("manual").toString("base64")
                              }
                            })));
                            return;
                          }
                          if (path.indexOf("/target") === 0) {
                            setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                              id: request.id,
                              ok: true,
                              result: {
                                url: payload.url,
                                status: 200,
                                statusText: "OK",
                                headers: [["content-type", "text/plain"]],
                                bodyBase64: Buffer.from("followed").toString("base64")
                              }
                            })));
                            return;
                          }
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: {
                              url: payload.url,
                              status: new URL(payload.url).protocol === "https:" ? 202 : 200,
                              statusText: new URL(payload.url).protocol === "https:" ? "Accepted" : "OK",
                              headers: [["content-type", "text/plain"], ["x-method", payload.method]],
                              bodyBase64: Buffer.from(path + "|" + String(payload.method)).toString("base64")
                            }
                          })));
                        }
                      });

                      const httpOk = await read(http, "http://example.invalid/client?x=1");
                      const httpsOk = await read(https, "https://example.invalid/client-secure", { method: "POST" }, "payload");
                      const manualRedirect = await read(http, "http://example.invalid/redirect");
                      const followedRedirect = await read(http, "http://example.invalid/redirect", { maxRedirects: 1 });
                      const timeoutCode = await requestError(http, "http://example.invalid/timeout", { timeout: 25 });
                      const tlsCode = await requestError(https, "https://example.invalid/tls-error");

                      const denials = [
                        syncCode(() => http.createServer().listen(0)),
                        syncCode(() => https.createServer().listen(0)),
                        syncCode(() => http.request("http://example.invalid/proxy", { proxy: { host: "127.0.0.1", port: 8080 } })),
                        syncCode(() => http.request("http://example.invalid/proxy-url", { proxyUrl: "http://127.0.0.1:8080" })),
                        syncCode(() => https.request("https://example.invalid/tunnel", { proxyTunnel: true })),
                        syncCode(() => http.request("http://example.invalid/connect", { method: "CONNECT" })),
                        syncCode(() => http.request("http://example.invalid/upgrade", { upgrade: true })),
                        syncCode(() => https.request("https://example.invalid/tls", { rejectUnauthorized: false }))
                      ];

                      const axiosProxy = await asyncCode(() => axios.get("https://example.invalid/axios", {
                        proxy: { host: "127.0.0.1", port: 8080 }
                      }));
                      const fetchResolved = require.resolve("fetch");
                      const axiosResolved = require.resolve("axios");

                      console.log("p13http.resolve=" + [
                        require.resolve("http"),
                        require.resolve("node:http"),
                        require.resolve("https"),
                        require.resolve("node:https"),
                        fetchResolved,
                        axiosResolved
                      ].join("|"));
                      console.log("p13http.same=" + [http === nodeHttp, https === nodeHttps, http.shadow === true, https.shadow === true, axios.shadow === true].join("|"));
                      console.log("p13http.client=" + [
                        httpOk.statusCode,
                        httpOk.statusMessage,
                        httpOk.headers["x-method"],
                        httpOk.body,
                        httpsOk.statusCode,
                        httpsOk.statusMessage,
                        httpsOk.headers["x-method"],
                        httpsOk.body
                      ].join("|"));
                      console.log("p13http.redirect=" + [
                        manualRedirect.statusCode,
                        manualRedirect.headers.location,
                        manualRedirect.body,
                        followedRedirect.statusCode,
                        followedRedirect.body
                      ].join("|"));
                      console.log("p13http.codes=" + [timeoutCode, tlsCode, axiosProxy].join("|"));
                      console.log("p13http.denials=" + denials.join("|"));
                      console.log("p13http.boundary=" + [
                        typeof fetchModule,
                        typeof axios.get,
                        requests.every((request) => request.permissions.indexOf("network") >= 0),
                        bridge.__test.pendingCount()
                      ].join("|"));
                    })().catch((error) => {
                      console.error("p13http.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-http-desktop-parity-p13-29.js>",
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-29 HTTP desktop parity", result)
            assertTrue(result.stdout, result.stdout.contains("p13http.resolve=http|http|https|https|fetch|axios"))
            assertTrue(result.stdout, result.stdout.contains("p13http.same=true|true|false|false|false"))
            assertTrue(
                result.stdout,
                result.stdout.contains("p13http.client=200|OK|GET|/client?x=1|GET|202|Accepted|POST|/client-secure|POST"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13http.redirect=302|/target|manual|200|followed"))
            assertTrue(result.stdout, result.stdout.contains("p13http.codes=ERR_AUTOJS6_NETWORK_TIMEOUT|ERR_AUTOJS6_NETWORK_ERROR|ERR_AUTOJS6_NETWORK_POLICY_DENIED"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13http.denials=ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13http.boundary=function|function|true|0"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeShadowModule(workDir: File, moduleName: String) {
        File(workDir, "node_modules/$moduleName/index.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true, id: "$moduleName-shadow" };""" + "\n", StandardCharsets.UTF_8)
        }
    }
}
