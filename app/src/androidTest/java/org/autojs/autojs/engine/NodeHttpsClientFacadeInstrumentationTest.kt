package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeHttpsClientFacadeInstrumentationTest {

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
    fun httpsClientFacadeKeepsTlsCustomizationDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node https client facade focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network"))
        val workDir = File(context.cacheDir, "node-https-client-facade-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create HTTPS client facade work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const bridge = require("autojs6:bridge");
                      const { Buffer } = require("buffer");
                      const https = require("https");
                      const nodeHttps = require("node:https");
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

                      function headerValue(headers, name) {
                        const lower = String(name).toLowerCase();
                        const found = (headers || []).find((entry) => String(entry[0]).toLowerCase() === lower);
                        return found ? String(found[1]) : "";
                      }

                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          requests.push(request);
                          const payload = request.args[0];
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: {
                              url: payload.url,
                              status: 203,
                              statusText: "Non-Authoritative Information",
                              headers: [["content-type", "text/plain"], ["x-secure", new URL(payload.url).protocol]],
                              bodyBase64: Buffer.from("secure-ok").toString("base64")
                            }
                          })));
                        }
                      });

                      const agent = new https.Agent({ keepAlive: true, maxSockets: 2, maxFreeSockets: 1 });
                      const response = await new Promise((resolve, reject) => {
                        const req = https.request("https://secure.example.invalid/secure?x=1", {
                          method: "POST",
                          headers: { "x-test": "yes" },
                          rejectUnauthorized: true,
                          agent,
                          timeout: 1000
                        }, (res) => {
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
                        req.end("payload");
                      });
                      agent.destroy();

                      const denialCodes = [
                        syncCode(() => https.request("https://secure.example.invalid/deny", { rejectUnauthorized: false })),
                        syncCode(() => https.request("https://secure.example.invalid/deny", { secureContext: {} })),
                        syncCode(() => https.request("https://secure.example.invalid/deny", { cert: "cert" })),
                        syncCode(() => https.request("https://secure.example.invalid/deny", { key: "key" })),
                        syncCode(() => https.request("https://secure.example.invalid/deny", { createConnection() {} })),
                        syncCode(() => new https.Agent({ rejectUnauthorized: false })),
                        syncCode(() => new https.Agent({ ca: "ca" }))
                      ];

                      const firstPayload = requests[0].args[0];
                      console.log("httpsfacade.same=" + (https === nodeHttps));
                      console.log("httpsfacade.success=" + [
                        response.statusCode,
                        response.statusMessage,
                        response.headers["x-secure"],
                        response.body
                      ].join("|"));
                      console.log("httpsfacade.payload=" + [
                        firstPayload.method,
                        new URL(firstPayload.url).protocol,
                        pathOf(firstPayload.url),
                        headerValue(firstPayload.headers, "x-test"),
                        Buffer.from(firstPayload.bodyBase64, "base64").toString("utf8")
                      ].join("|"));
                      console.log("httpsfacade.agent=" + [
                        agent.keepAlive,
                        agent.maxSockets,
                        agent.maxFreeSockets,
                        agent.destroyed === true
                      ].join("|"));
                      console.log("httpsfacade.denials=" + denialCodes.join("|"));
                      console.log("httpsfacade.permissions=" + requests.every((request) => request.permissions.indexOf("network") >= 0));
                      console.log("httpsfacade.pending=" + bridge.__test.pendingCount());
                    })().catch((error) => {
                      console.error("httpsfacade.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-https-client-facade-s8-22.js>",
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node https client facade", result)
            assertTrue(result.stdout, result.stdout.contains("httpsfacade.same=true"))
            assertTrue(
                result.stdout,
                result.stdout.contains("httpsfacade.success=203|Non-Authoritative Information|https:|secure-ok"),
            )
            assertTrue(result.stdout, result.stdout.contains("httpsfacade.payload=POST|https:|/secure?x=1|yes|payload"))
            assertTrue(result.stdout, result.stdout.contains("httpsfacade.agent=true|2|1|true"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "httpsfacade.denials=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("httpsfacade.permissions=true"))
            assertTrue(result.stdout, result.stdout.contains("httpsfacade.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
