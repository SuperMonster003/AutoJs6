package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeHttpAgentFacadeInstrumentationTest {

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
    fun limitedAgentShapeDoesNotExposeRawSockets() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node http agent facade focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network"))
        val workDir = File(context.cacheDir, "node-http-agent-facade-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create HTTP agent facade work dir: $absolutePath" }
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
                      const requests = [];

                      function codeOf(error) {
                        return error && (error.autojs6Code || error.code || error.name) || "";
                      }

                      function syncCode(fn) {
                        try {
                          fn();
                          return "allowed";
                        } catch (error) {
                          return codeOf(error);
                        }
                      }

                      function pathOf(url) {
                        const parsed = new URL(url);
                        return parsed.pathname + parsed.search;
                      }

                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          const payload = request.args[0];
                          const protocol = new URL(payload.url).protocol;
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: {
                              url: payload.url,
                              status: protocol === "https:" ? 202 : 200,
                              statusText: protocol === "https:" ? "Accepted" : "OK",
                              headers: [["content-type", "text/plain"], ["x-path", pathOf(payload.url)]],
                              bodyBase64: Buffer.from(protocol === "https:" ? "agent-https" : "agent-http").toString("base64")
                            }
                          })));
                        }
                      });

                      function requestWithAgent(mod, url, agent) {
                        return new Promise((resolve, reject) => {
                          let sameAgent = false;
                          const req = mod.request(url, { agent, timeout: 1000 }, (res) => {
                            const chunks = [];
                            res.setEncoding("utf8");
                            res.on("data", (chunk) => chunks.push(chunk));
                            res.on("end", () => resolve({
                              sameAgent,
                              statusCode: res.statusCode,
                              statusMessage: res.statusMessage,
                              path: res.headers["x-path"],
                              body: chunks.join("")
                            }));
                            res.on("error", reject);
                          });
                          sameAgent = req.agent === agent;
                          req.on("error", reject);
                          req.end();
                        });
                      }

                      const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 2, maxFreeSockets: 1 });
                      const httpsAgent = new https.Agent({ keepAlive: true, maxSockets: 3, maxFreeSockets: 2 });
                      const httpResult = await requestWithAgent(http, "http://example.invalid/agent-http", httpAgent);
                      const httpsResult = await requestWithAgent(https, "https://example.invalid/agent-https", httpsAgent);
                      const httpName = httpAgent.getName({ host: "agent.example.invalid", port: 8080 });
                      const httpsName = httpsAgent.getName({ host: "secure.example.invalid", port: 9443 });

                      const denialCodes = [
                        syncCode(() => http.request("http://example.invalid/deny", { createConnection() {} })),
                        syncCode(() => http.request("http://example.invalid/deny", { socketPath: "/tmp/autojs6.sock" })),
                        syncCode(() => new http.Agent({ createConnection() {} })),
                        syncCode(() => http.request("http://example.invalid/deny", { agent: { createConnection() {} } })),
                        syncCode(() => http.request("http://example.invalid/deny", { agent: { addRequest() {} } })),
                        syncCode(() => httpAgent.createConnection()),
                        syncCode(() => httpAgent.addRequest())
                      ];

                      httpAgent.destroy();
                      httpsAgent.destroy();

                      console.log("agent.same=" + [
                        http === nodeHttp,
                        http.globalAgent instanceof http.Agent,
                        https.globalAgent instanceof https.Agent
                      ].join("|"));
                      console.log("agent.http=" + [
                        httpAgent.keepAlive,
                        httpAgent.maxSockets,
                        httpAgent.maxFreeSockets,
                        httpAgent.defaultPort,
                        httpAgent.protocol,
                        httpName,
                        httpAgent.destroyed === true
                      ].join("|"));
                      console.log("agent.https=" + [
                        httpsAgent.keepAlive,
                        httpsAgent.maxSockets,
                        httpsAgent.maxFreeSockets,
                        httpsAgent.defaultPort,
                        httpsAgent.protocol,
                        httpsName,
                        httpsAgent.destroyed === true
                      ].join("|"));
                      console.log("agent.requests=" + [
                        httpResult.sameAgent,
                        httpResult.statusCode,
                        httpResult.statusMessage,
                        httpResult.path,
                        httpResult.body,
                        httpsResult.sameAgent,
                        httpsResult.statusCode,
                        httpsResult.statusMessage,
                        httpsResult.path,
                        httpsResult.body
                      ].join("|"));
                      console.log("agent.pools=" + [
                        Object.keys(httpAgent.requests).length,
                        Object.keys(httpAgent.sockets).length,
                        Object.keys(httpAgent.freeSockets).length
                      ].join("|"));
                      console.log("agent.denials=" + denialCodes.join("|"));
                      console.log("agent.permissions=" + requests.every((request) => request.permissions.indexOf("network") >= 0));
                      console.log("agent.pending=" + bridge.__test.pendingCount());
                    })().catch((error) => {
                      console.error("agent.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-http-agent-facade-s8-24.js>",
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node http agent facade", result)
            assertTrue(result.stdout, result.stdout.contains("agent.same=true|true|true"))
            assertTrue(
                result.stdout,
                result.stdout.contains("agent.http=true|2|1|80|http:|agent.example.invalid:8080::|true"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("agent.https=true|3|2|443|https:|secure.example.invalid:9443::|true"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "agent.requests=true|200|OK|/agent-http|agent-http|true|202|Accepted|/agent-https|agent-https",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("agent.pools=0|0|0"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "agent.denials=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_DISABLED|ERR_AUTOJS6_NETWORK_DISABLED",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("agent.permissions=true"))
            assertTrue(result.stdout, result.stdout.contains("agent.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
