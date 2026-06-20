package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeHttpStreamSemanticsInstrumentationTest {

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
    fun clientRequestAndIncomingMessageStreamSemanticsStayControlled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node http stream semantics focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network"))
        val workDir = File(context.cacheDir, "node-http-stream-semantics-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create HTTP stream semantics work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const bridge = require("autojs6:bridge");
                      const { Buffer } = require("buffer");
                      const http = require("http");
                      const requests = [];

                      function codeOf(error) {
                        return error && (error.autojs6Code || error.code || error.name) || "";
                      }

                      function pathOf(url) {
                        const parsed = new URL(url);
                        return parsed.pathname + parsed.search;
                      }

                      function requestBody(payload) {
                        if (payload.bodyBase64 !== undefined) {
                          return Buffer.from(payload.bodyBase64, "base64").toString("utf8");
                        }
                        return String(payload.bodyText || "");
                      }

                      function response(payload) {
                        const path = pathOf(payload.url);
                        if (path.indexOf("/post") === 0) {
                          const body = requestBody(payload);
                          return {
                            url: payload.url,
                            status: 201,
                            statusText: "Created",
                            headers: [["content-type", "text/plain"], ["x-echo", "post"]],
                            bodyBase64: Buffer.from(body).toString("base64"),
                            bodyBytes: Buffer.byteLength(body)
                          };
                        }
                        if (path.indexOf("/pipe") === 0) {
                          return {
                            url: payload.url,
                            status: 200,
                            statusText: "OK",
                            headers: [["content-type", "text/plain"]],
                            bodyBase64: Buffer.from("stream-body").toString("base64"),
                            bodyBytes: 11
                          };
                        }
                        if (path.indexOf("/large") === 0) {
                          return {
                            url: payload.url,
                            status: 200,
                            statusText: "OK",
                            headers: [["content-type", "text/plain"]],
                            bodyBase64: Buffer.from("too-large").toString("base64"),
                            bodyBytes: 9
                          };
                        }
                        return {
                          url: payload.url,
                          status: 200,
                          statusText: "OK",
                          headers: [["content-type", "text/plain"]],
                          bodyBase64: Buffer.from("ok").toString("base64"),
                          bodyBytes: 2
                        };
                      }

                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          const payload = request.args[0];
                          const path = pathOf(payload.url);
                          if (path.indexOf("/abort-signal") === 0 || path.indexOf("/destroy") === 0) {
                            return;
                          }
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: response(payload)
                          })));
                        }
                      });

                      function waitFor(predicate, label) {
                        const started = Date.now();
                        return new Promise((resolve, reject) => {
                          function tick() {
                            if (predicate()) {
                              resolve();
                              return;
                            }
                            if (Date.now() - started > 1000) {
                              reject(new Error("timeout waiting for " + label));
                              return;
                            }
                            setTimeout(tick, 5);
                          }
                          tick();
                        });
                      }

                      function readWithDataEvents(path, options, body) {
                        return new Promise((resolve, reject) => {
                          const events = [];
                          const req = http.request("http://example.invalid" + path, Object.assign({
                            timeout: 1000
                          }, options || {}), (res) => {
                            const chunks = [];
                            res.setEncoding("utf8");
                            res.on("data", (chunk) => {
                              events.push("data");
                              chunks.push(chunk);
                            });
                            res.on("end", () => events.push("end"));
                            res.on("close", () => {
                              events.push("close");
                              resolve({
                                statusCode: res.statusCode,
                                statusMessage: res.statusMessage,
                                headers: res.headers,
                                body: chunks.join(""),
                                events: events.join(",")
                              });
                            });
                            res.on("error", reject);
                          });
                          req.on("error", reject);
                          if (body !== undefined) {
                            req.write(body.slice(0, 6));
                            req.end(body.slice(6));
                          } else {
                            req.end();
                          }
                        });
                      }

                      function pipeWithBackpressureSmoke() {
                        return new Promise((resolve, reject) => {
                          const req = http.get("http://example.invalid/pipe", { timeout: 1000 }, (res) => {
                            const events = [];
                            const destination = {
                              chunks: [],
                              write(chunk) {
                                events.push("write:false");
                                this.chunks.push(Buffer.from(chunk).toString("utf8"));
                                return false;
                              },
                              end() {
                                events.push("dest:end");
                                setImmediate(() => resolve({
                                  body: this.chunks.join(""),
                                  events: events.join(",")
                                }));
                              }
                            };
                            res.on("data", () => events.push("data"));
                            res.on("end", () => events.push("end"));
                            res.on("close", () => events.push("close"));
                            res.on("error", reject);
                            res.pipe(destination);
                          });
                          req.on("error", reject);
                        });
                      }

                      function requestErrorFor(path, options) {
                        return new Promise((resolve) => {
                          const events = [];
                          let errorCode = "";
                          const req = http.request("http://example.invalid" + path, Object.assign({
                            timeout: 1000
                          }, options || {}), () => {});
                          req.on("error", (error) => {
                            events.push("error");
                            errorCode = codeOf(error);
                          });
                          req.on("close", () => {
                            events.push("close");
                            resolve(errorCode + "@" + events.join(","));
                          });
                          req.end();
                        });
                      }

                      const post = await readWithDataEvents("/post", { method: "POST" }, "chunked-body");
                      const pipe = await pipeWithBackpressureSmoke();

                      const abortController = new AbortController();
                      const abortPromise = requestErrorFor("/abort-signal", { signal: abortController.signal });
                      await waitFor(() => requests.some((request) => pathOf(request.args[0].url).indexOf("/abort-signal") === 0), "abort-signal dispatch");
                      abortController.abort();
                      const abortSignal = await abortPromise;

                      const preAbortController = new AbortController();
                      preAbortController.abort();
                      const preAbort = await requestErrorFor("/pre-abort", { signal: preAbortController.signal });

                      const large = await requestErrorFor("/large", { maxResponseBytes: 3 });

                      const destroyPromise = requestErrorFor("/destroy", { timeout: 1000 });
                      await waitFor(() => requests.some((request) => pathOf(request.args[0].url).indexOf("/destroy") === 0), "destroy dispatch");
                      bridge.__test.destroy("http stream service destroy");
                      const destroy = await destroyPromise;

                      console.log("httpstream.post=" + [
                        post.statusCode,
                        post.statusMessage,
                        post.headers["x-echo"],
                        post.body,
                        post.events
                      ].join("|"));
                      console.log("httpstream.pipe=" + [pipe.body, pipe.events].join("|"));
                      console.log("httpstream.codes=" + [abortSignal, preAbort, large, destroy].join("|"));
                      console.log("httpstream.requests=" + requests.map((request) => pathOf(request.args[0].url)).join("|"));
                      console.log("httpstream.pending=" + bridge.__test.pendingCount());
                    })().catch((error) => {
                      console.error("httpstream.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-http-stream-semantics-s8-23.js>",
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node http stream semantics", result)
            assertTrue(result.stdout, result.stdout.contains("httpstream.post=201|Created|post|chunked-body|data,end,close"))
            assertTrue(result.stdout, result.stdout.contains("httpstream.pipe=stream-body|data,write:false,end,dest:end,close"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "httpstream.codes=ERR_AUTOJS6_NETWORK_ABORTED@error,close|ERR_AUTOJS6_NETWORK_ABORTED@error,close|ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE@error,close|ERR_AUTOJS6_BRIDGE_PROCESS_DEAD@error,close",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("httpstream.requests=/post|/pipe|/abort-signal|/large|/destroy"))
            assertTrue(result.stdout, result.stdout.contains("httpstream.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
