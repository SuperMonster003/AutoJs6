package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeUndiciFacadeInstrumentationTest {

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
    fun undiciFacadeUsesControlledFetchAndSafeDispatcherShapes() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node undici facade focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network"))
        val workDir = File(context.cacheDir, "node-undici-facade-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Undici facade work dir: $absolutePath" }
        }
        File(workDir, "node_modules/undici").apply {
            check(mkdirs()) { "Failed to create local undici shadow dir: $absolutePath" }
            File(this, "index.js").writeText("module.exports = { shadow: true };\n")
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const bridge = require("autojs6:bridge");
                      const { Buffer } = require("buffer");
                      const undici = require("undici");
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
                          const path = pathOf(payload.url);
                          const status = path === "/request" ? 201 : 200;
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: {
                              url: payload.url,
                              status,
                              statusText: status === 201 ? "Created" : "OK",
                              headers: [["content-type", "text/plain"], ["x-path", path], ["x-reply", headerValue(payload.headers, "x-test")]],
                              bodyBase64: Buffer.from(path === "/request" ? "request-ok" : "undici-ok").toString("base64")
                            }
                          })));
                        }
                      });

                      const fetchResponse = await undici.fetch("https://example.invalid/undici", {
                        method: "POST",
                        headers: new undici.Headers({ "x-test": "yes" }),
                        body: "payload",
                        timeoutMs: 1000
                      });
                      const fetchBody = await fetchResponse.text();
                      const requestResult = await undici.request("https://example.invalid/request", {
                        timeoutMs: 1000
                      });

                      const agent = new undici.Agent({ connections: 2, pipelining: 1, keepAliveTimeout: 1234 });
                      const dispatcher = new undici.Dispatcher({ pipelining: 1 });
                      const defaultGlobalIsAgent = undici.getGlobalDispatcher() instanceof undici.Agent;
                      undici.setGlobalDispatcher(agent);
                      const globalSame = undici.getGlobalDispatcher() === agent;
                      await agent.close();
                      const agentClosed = agent.closed === true;
                      agent.destroy();
                      const agentDestroyed = agent.destroyed === true;
                      const dispatcherCallbackClosed = await new Promise((resolve) => {
                        const local = new undici.Dispatcher();
                        local.close((error) => resolve(error === null && local.closed === true));
                      });

                      const mockAgent = new undici.MockAgent();
                      const pool = mockAgent.disableNetConnect().get("https://mock.invalid");
                      const interceptor = pool
                        .intercept({ path: "/mock", method: "GET" })
                        .reply(200, "mock-ok", { headers: { "x-mock": "yes" } })
                        .times(2)
                        .persist()
                        .delay(1);

                      const decoratorEvents = [];
                      class JsdomLikeDecompressHandler extends undici.DecoratorHandler {
                        onRequestStart(controller, context) {
                          return super.onRequestStart(controller, context);
                        }
                        onResponseStart(controller, statusCode, headers, statusMessage) {
                          return super.onResponseStart(controller, statusCode, headers, statusMessage);
                        }
                        onResponseData(controller, chunk) {
                          return super.onResponseData(controller, chunk);
                        }
                        onResponseEnd(controller, trailers) {
                          return super.onResponseEnd(controller, trailers);
                        }
                      }
                      const decorator = new JsdomLikeDecompressHandler({
                        onRequestStart(controller, context) {
                          decoratorEvents.push("request:" + context.kind);
                          return "request";
                        },
                        onResponseStart(controller, statusCode, headers, statusMessage) {
                          decoratorEvents.push("start:" + statusCode + ":" + statusMessage);
                          return "started";
                        },
                        onResponseData(controller, chunk) {
                          decoratorEvents.push("data:" + chunk.toString());
                          return false;
                        },
                        onResponseEnd(controller, trailers) {
                          decoratorEvents.push("end:" + Object.keys(trailers).length);
                          return "ended";
                        }
                      });
                      const decoratorRequest = decorator.onRequestStart({}, { kind: "new" });
                      const decoratorStart = decorator.onResponseStart({}, 200, { "content-type": "text/plain" }, "OK");
                      const decoratorData = decorator.onResponseData({}, Buffer.from("x"));
                      const decoratorEnd = decorator.onResponseEnd({}, { "x-trailer": "done" });

                      const oldEvents = [];
                      const oldController = {
                        paused: false,
                        pause() {
                          oldEvents.push("pause");
                          this.paused = true;
                        },
                        resume() {
                          oldEvents.push("resume");
                          this.paused = false;
                        },
                        abort(reason) {
                          oldEvents.push("abort:" + reason);
                        }
                      };
                      const oldDecorator = new undici.DecoratorHandler({
                        onConnect(abort, context) {
                          oldEvents.push("connect:" + context.kind);
                          abort("stop");
                        },
                        onHeaders(statusCode, rawHeaders, resume, statusMessage) {
                          oldEvents.push("headers:" + statusCode + ":" + statusMessage + ":" + rawHeaders.length);
                          resume();
                          return false;
                        },
                        onData(chunk) {
                          oldEvents.push("data:" + chunk.toString());
                          return false;
                        },
                        onComplete(rawTrailers) {
                          oldEvents.push("complete:" + rawTrailers.length);
                        }
                      });
                      oldDecorator.onRequestStart(oldController, { kind: "old" });
                      oldDecorator.onResponseStart(oldController, 202, { "x-old": "yes" }, "Accepted");
                      oldDecorator.onResponseData(oldController, Buffer.from("legacy"));
                      oldDecorator.onResponseEnd(oldController, { "x-trailer": "done" });

                      const denialCodes = [
                        syncCode(() => new undici.Agent({ connect() {} })),
                        syncCode(() => new undici.Agent({ factory() {} })),
                        syncCode(() => new undici.Agent({ socketPath: "/tmp/undici.sock" })),
                        syncCode(() => undici.fetch("https://example.invalid/deny", { dispatcher: { dispatch() {} } })),
                        syncCode(() => undici.setGlobalDispatcher({ dispatch() {} })),
                        syncCode(() => undici.connect("https://example.invalid/")),
                        syncCode(() => undici.stream("https://example.invalid/")),
                        syncCode(() => dispatcher.dispatch())
                      ];

                      console.log("undici.surface=" + [
                        undici.shadow === true,
                        Object.isFrozen(undici),
                        undici.default === undici,
                        typeof undici.fetch,
                        undici.Headers === Headers,
                        undici.Request === Request,
                        undici.Response === Response
                      ].join("|"));
                      console.log("undici.fetch=" + [
                        fetchResponse.status,
                        fetchResponse.statusText,
                        fetchResponse.headers.get("x-path"),
                        fetchResponse.headers.get("x-reply"),
                        fetchBody
                      ].join("|"));
                      console.log("undici.request=" + [
                        requestResult.statusCode,
                        requestResult.statusText,
                        requestResult.headers["x-path"],
                        typeof requestResult.body
                      ].join("|"));
                      console.log("undici.dispatcher=" + [
                        agent instanceof undici.Agent,
                        agent instanceof undici.Dispatcher,
                        defaultGlobalIsAgent,
                        globalSame,
                        agent.connections,
                        agent.pipelining,
                        agent.keepAliveTimeout,
                        agentClosed,
                        agentDestroyed,
                        dispatcherCallbackClosed
                      ].join("|"));
                      console.log("undici.mock=" + [
                        mockAgent instanceof undici.MockAgent,
                        mockAgent instanceof undici.Dispatcher,
                        mockAgent.netConnectDisabled,
                        pool.origin,
                        pool.interceptors.length,
                        interceptor.replyStatusCode,
                        interceptor.replyData,
                        interceptor.remaining,
                        interceptor.persisted
                      ].join("|"));
                      console.log("undici.decorator=" + [
                        typeof undici.DecoratorHandler,
                        Object.isFrozen(undici.DecoratorHandler),
                        Object.isFrozen(undici.DecoratorHandler.prototype),
                        decorator instanceof undici.DecoratorHandler,
                        decoratorRequest,
                        decoratorStart,
                        decoratorData,
                        decoratorEnd,
                        decoratorEvents.join(","),
                        oldController.paused,
                        oldEvents.join(",")
                      ].join("|"));
                      console.log("undici.denials=" + denialCodes.join("|"));
                      console.log("undici.requests=" + requests.map((request) => {
                        const payload = request.args[0];
                        return payload.method + ":" + pathOf(payload.url);
                      }).join("|"));
                      console.log("undici.permissions=" + requests.every((request) => request.permissions.indexOf("network") >= 0));
                      console.log("undici.pending=" + bridge.__test.pendingCount());
                    })().catch((error) => {
                      console.error("undici.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-undici-facade-s8-25.js>",
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node undici facade", result)
            assertTrue(result.stdout, result.stdout.contains("undici.surface=false|true|true|function|true|true|true"))
            assertTrue(result.stdout, result.stdout.contains("undici.fetch=200|OK|/undici|yes|undici-ok"))
            assertTrue(result.stdout, result.stdout.contains("undici.request=201|Created|/request|object"))
            assertTrue(result.stdout, result.stdout.contains("undici.dispatcher=true|true|true|true|2|1|1234|true|true|true"))
            assertTrue(
                result.stdout,
                result.stdout.contains("undici.mock=true|true|true|https://mock.invalid|1|200|mock-ok|2|true"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "undici.decorator=function|true|true|true|request|started|false|ended|request:new,start:200:OK,data:x,end:1|true|connect:old,abort:stop,headers:202:Accepted:2,resume,pause,data:legacy,pause,complete:2",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "undici.denials=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("undici.requests=POST:/undici|GET:/request"))
            assertTrue(result.stdout, result.stdout.contains("undici.permissions=true"))
            assertTrue(result.stdout, result.stdout.contains("undici.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
