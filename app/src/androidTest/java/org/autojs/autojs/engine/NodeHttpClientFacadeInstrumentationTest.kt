package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeHttpClientFacadeInstrumentationTest {

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
    fun httpAndHttpsClientFacadeUseControlledFetchTransport() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node http client facade focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network"))
        val workDir = File(context.cacheDir, "node-http-client-facade-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create HTTP client facade work dir: $absolutePath" }
        }
        try {
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
                      const requests = [];

                      function codeOf(error) {
                        return error && (error.autojs6Code || error.code || error.name) || "";
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

                      function pathOf(url) {
                        const parsed = new URL(url);
                        return parsed.pathname + parsed.search;
                      }

                      function response(payload) {
                        const path = pathOf(payload.url);
                        if (path.indexOf("/get") === 0) {
                          return {
                            url: payload.url,
                            status: 200,
                            statusText: "OK",
                            headers: [["content-type", "text/plain"], ["x-reply", headerValue(payload.headers, "x-test")]],
                            bodyBase64: Buffer.from("hello-get").toString("base64"),
                            bodyBytes: 9
                          };
                        }
                        if (path.indexOf("/post") === 0) {
                          return {
                            url: payload.url,
                            status: 201,
                            statusText: "Created",
                            headers: [["content-type", "application/json"], ["x-reply", "post"]],
                            bodyBase64: Buffer.from(JSON.stringify({
                              method: payload.method,
                              body: requestBody(payload),
                              type: headerValue(payload.headers, "content-type")
                            })).toString("base64")
                          };
                        }
                        if (path.indexOf("/pipe") === 0) {
                          return {
                            url: payload.url,
                            status: 200,
                            statusText: "OK",
                            headers: [["content-type", "text/plain"]],
                            bodyBase64: Buffer.from("pipe-body").toString("base64"),
                            bodyBytes: 9
                          };
                        }
                        if (path.indexOf("/secure") === 0) {
                          return {
                            url: payload.url,
                            status: 202,
                            statusText: "Accepted",
                            headers: [["content-type", "text/plain"], ["x-secure", "yes"]],
                            bodyBase64: Buffer.from("HTTPS").toString("base64"),
                            bodyBytes: 5
                          };
                        }
                        if (path.indexOf("/redirect-follow") === 0) {
                          return {
                            url: payload.url,
                            status: 302,
                            statusText: "Found",
                            headers: [["location", "/redirect-target"]],
                            bodyBase64: Buffer.from("redirect-follow").toString("base64")
                          };
                        }
                        if (path.indexOf("/redirect-target") === 0) {
                          return {
                            url: payload.url,
                            status: 200,
                            statusText: "OK",
                            headers: [["content-type", "text/plain"]],
                            bodyBase64: Buffer.from("followed").toString("base64")
                          };
                        }
                        if (path.indexOf("/redirect") === 0) {
                          return {
                            url: payload.url,
                            status: 302,
                            statusText: "Found",
                            headers: [["location", "/redirect-target"]],
                            bodyBase64: Buffer.from("manual-redirect").toString("base64")
                          };
                        }
                        return {
                          url: payload.url,
                          status: 404,
                          statusText: "Not Found",
                          headers: [["content-type", "text/plain"]],
                          bodyBase64: Buffer.from("missing").toString("base64")
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
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: response(payload)
                          })));
                        }
                      });

                      function readWithDataEvents(mod, url, options, body) {
                        return new Promise((resolve, reject) => {
                          const req = mod.request(url, options, (res) => {
                            const chunks = [];
                            res.setEncoding("utf8");
                            res.on("data", (chunk) => chunks.push(chunk));
                            res.on("end", () => resolve({
                              statusCode: res.statusCode,
                              statusMessage: res.statusMessage,
                              headers: res.headers,
                              body: chunks.join("")
                            }));
                          });
                          req.on("error", reject);
                          if (body !== undefined) {
                            req.write(body.slice(0, 3));
                            req.end(body.slice(3));
                          } else {
                            req.end();
                          }
                        });
                      }

                      function getWithPipe(url) {
                        return new Promise((resolve, reject) => {
                          const req = http.get(url, { timeout: 1000 }, (res) => {
                            const chunks = [];
                            res.pipe({
                              write(chunk) {
                                chunks.push(Buffer.from(chunk).toString("utf8"));
                              },
                              end() {
                                resolve(chunks.join(""));
                              }
                            });
                          });
                          req.on("error", reject);
                        });
                      }

                      const getResult = await new Promise((resolve, reject) => {
                        const req = http.get("http://example.invalid/get?x=1", {
                          headers: { "x-test": "yes" },
                          timeout: 1000
                        }, (res) => {
                          const chunks = [];
                          res.setEncoding("utf8");
                          res.on("data", (chunk) => chunks.push(chunk));
                          res.on("end", () => resolve({
                            statusCode: res.statusCode,
                            statusMessage: res.statusMessage,
                            reply: res.headers["x-reply"],
                            body: chunks.join("")
                          }));
                        });
                        req.on("error", reject);
                      });

                      const postResult = await readWithDataEvents(
                        http,
                        "http://example.invalid/post",
                        {
                          method: "POST",
                          headers: { "content-type": "text/plain" },
                          timeout: 1000
                        },
                        "payload"
                      );
                      const postJson = JSON.parse(postResult.body);
                      const pipeResult = await getWithPipe("http://example.invalid/pipe");
                      const httpsResult = await readWithDataEvents(
                        https,
                        new URL("https://example.invalid/secure"),
                        { method: "POST", timeout: 1000 },
                        "secure"
                      );
                      const redirectResult = await readWithDataEvents(
                        http,
                        "http://example.invalid/redirect",
                        { timeout: 1000 }
                      );
                      const followResult = await readWithDataEvents(
                        http,
                        "http://example.invalid/redirect-follow",
                        { timeout: 1000, maxRedirects: 1 }
                      );

                      const timeoutResult = await new Promise((resolve) => {
                        let timeoutSeen = false;
                        const req = http.get("http://example.invalid/timeout", { timeout: 25 }, () => {});
                        req.setTimeout(25, () => {
                          timeoutSeen = true;
                        });
                        req.on("timeout", () => {
                          timeoutSeen = true;
                        });
                        req.on("error", (error) => resolve(codeOf(error) + "/" + timeoutSeen));
                      });

                      const abortResult = await new Promise((resolve) => {
                        const req = http.request("http://example.invalid/abort", { timeout: 1000 }, () => {});
                        req.on("error", (error) => resolve(codeOf(error)));
                        req.end();
                        setImmediate(() => req.abort());
                      });

                      console.log("httpfacade.same=" + (http === nodeHttp && https === nodeHttps));
                      console.log("httpfacade.get=" + [
                        getResult.statusCode,
                        getResult.statusMessage,
                        getResult.reply,
                        getResult.body
                      ].join("|"));
                      console.log("httpfacade.post=" + [
                        postResult.statusCode,
                        postResult.statusMessage,
                        postJson.method,
                        postJson.body,
                        postJson.type,
                        postResult.headers["x-reply"]
                      ].join("|"));
                      console.log("httpfacade.pipe=" + pipeResult);
                      console.log("httpfacade.https=" + [
                        httpsResult.statusCode,
                        httpsResult.statusMessage,
                        httpsResult.headers["x-secure"],
                        httpsResult.body
                      ].join("|"));
                      console.log("httpfacade.redirect=" + [
                        redirectResult.statusCode,
                        redirectResult.statusMessage,
                        redirectResult.headers.location,
                        redirectResult.body
                      ].join("|"));
                      console.log("httpfacade.follow=" + [
                        followResult.statusCode,
                        followResult.statusMessage,
                        followResult.body
                      ].join("|"));
                      console.log("httpfacade.events=" + [timeoutResult, abortResult].join("|"));
                      console.log("httpfacade.requests=" + requests.map((request) => {
                        const payload = request.args[0];
                        return payload.method + ":" + pathOf(payload.url);
                      }).join("|"));
                      console.log("httpfacade.permissions=" + requests.every((request) => request.permissions.indexOf("network") >= 0));
                      console.log("httpfacade.pending=" + bridge.__test.pendingCount());
                    })().catch((error) => {
                      console.error("httpfacade.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-http-client-facade-s8-21.js>",
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node http client facade", result)
            assertTrue(result.stdout, result.stdout.contains("httpfacade.same=true"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.get=200|OK|yes|hello-get"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.post=201|Created|POST|payload|text/plain|post"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.pipe=pipe-body"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.https=202|Accepted|yes|HTTPS"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.redirect=302|Found|/redirect-target|manual-redirect"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.follow=200|OK|followed"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.events=ERR_AUTOJS6_NETWORK_TIMEOUT/true|ERR_AUTOJS6_NETWORK_ABORTED"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "httpfacade.requests=GET:/get?x=1|POST:/post|GET:/pipe|POST:/secure|GET:/redirect|GET:/redirect-follow|GET:/redirect-target|GET:/timeout|GET:/abort",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("httpfacade.permissions=true"))
            assertTrue(result.stdout, result.stdout.contains("httpfacade.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
