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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeUiBridgeInstrumentationTest {

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
    fun kotlinUiBridgeRegistryManifestAndFakeProviderAreStable() {
        listOf("showLayout", "update", "close", "drainEvents").forEach { method ->
            assertTrue(NodeBridgeModules.supports("ui", method))
            assertEquals(
                listOf(NodeBridgePermissionManifest.UI),
                NodeBridgePermissionManifest.requiredCapabilitiesFor("ui", method),
            )
        }
        assertFalse(NodeBridgeModules.supports("ui", "rawView"))
        assertEquals(emptyList<String>(), NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf("ui")))

        val provider = FakeNodeBridgeCapabilityProvider { request ->
            assertEquals("ui", request.module)
            assertTrue("ui" in request.permissions)
            when (request.method) {
                "showLayout" -> {
                    val layout = JSONArray(request.argsJson).getJSONObject(0)
                    assertEquals("Vertical", layout.getString("type"))
                    assertEquals("title", layout.getJSONArray("children").getJSONObject(0).getString("id"))
                    NodeBridgeResponse.success(request.id, JSONObject().put("id", "fake-ui-1").toString())
                }
                "update" -> {
                    val args = JSONArray(request.argsJson)
                    assertEquals("fake-ui-1", args.getString(0))
                    assertEquals("Updated", args.getJSONObject(1).getString("text"))
                    NodeBridgeResponse.success(request.id, "null")
                }
                "drainEvents" -> NodeBridgeResponse.success(
                    request.id,
                    JSONArray().put(JSONObject().put("type", "click").put("id", "ok")).toString(),
                )
                "close" -> {
                    assertEquals("fake-ui-1", JSONArray(request.argsJson).getString(0))
                    NodeBridgeResponse.success(request.id, "null")
                }
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        }

        val show = dispatch(
            provider,
            method = "showLayout",
            argsJson = """[{"type":"Vertical","children":[{"type":"Text","id":"title","text":"Hello"}]}]""",
        )
        val update = dispatch(provider, method = "update", argsJson = """["fake-ui-1",{"id":"title","text":"Updated"}]""")
        val drain = dispatch(provider, method = "drainEvents", argsJson = """["fake-ui-1",{"maxEvents":16}]""")
        val close = dispatch(provider, method = "close", argsJson = """["fake-ui-1"]""")

        assertTrue(show.toString(), show.ok)
        assertEquals("fake-ui-1", JSONObject(show.resultJson.orEmpty()).getString("id"))
        assertTrue(update.toString(), update.ok)
        assertTrue(drain.toString(), drain.ok)
        assertEquals("ok", JSONArray(drain.resultJson.orEmpty()).getJSONObject(0).getString("id"))
        assertTrue(close.toString(), close.ok)
        assertEquals(listOf("showLayout", "update", "drainEvents", "close"), provider.requests.map { it.method })
    }

    @Test
    fun jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node ui bridge protocol regression")

        val workDir = File(context.cacheDir, "node-bridge-ui-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create ui bridge working directory: $absolutePath" }
        }
        File(workDir, "node_modules/ui").apply {
            check(mkdirs()) { "Failed to create ui shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText("""module.exports = { shadow: true, showLayout: () => "shadow" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              let drained = false;
              function codeOf(error) {
                return String(error && (error.autojs6Code || error.code || error.name || ""));
              }
              function respond(request, result) {
                setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                  id: request.id,
                  ok: true,
                  result
                })));
              }
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  if (request.module !== "ui") {
                    setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                      id: request.id,
                      ok: false,
                      error: {
                        code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                        message: "unexpected module",
                        module: request.module,
                        method: request.method
                      }
                    })));
                    return;
                  }
                  if (request.method === "showLayout") {
                    respond(request, { id: "ui-fake-1" });
                  } else if (request.method === "drainEvents") {
                    respond(request, drained ? [] : [{ type: "click", id: "ok", text: "OK" }]);
                    drained = true;
                  } else {
                    respond(request, null);
                  }
                }
              });

              const ui = require("ui");
              const handle = await ui.showLayout({
                type: "Vertical",
                children: [
                  { type: "Text", id: "title", text: "Node UI" },
                  { type: "Button", id: "ok", text: "OK" }
                ]
              }, { timeoutMs: 777, drainIntervalMs: 1 });
              let clicked = "";
              let eventFrozen = false;
              let off = function() {};
              const ready = new Promise((resolve) => {
                off = handle.on("click", (event) => {
                  clicked = event.id + ":" + event.handleId;
                  eventFrozen = Object.isFrozen(event);
                  off();
                  resolve();
                }, { drainIntervalMs: 1, timeoutMs: 778 });
              });
              await ready;
              await handle.update({ id: "title", text: "Updated" }, { timeoutMs: 779 });
              await handle.close({ timeoutMs: 780 });
              const beforeInvalid = requests.length;
              let invalidCode = "";
              try {
                await ui.showLayout({ type: "Text", bad: function() {} });
              } catch (error) {
                invalidCode = codeOf(error);
              }

              console.log("ui.resolve=" + require.resolve("ui"));
              console.log("ui.shadow=" + (ui.shadow === true));
              console.log("ui.frozen=" + Object.isFrozen(ui));
              console.log("ui.handle=" + [handle.id, typeof handle.on, typeof handle.update, typeof handle.close, Object.isFrozen(handle)].join("|"));
              console.log("ui.clicked=" + clicked + "|" + eventFrozen);
              console.log("ui.requests=" + requests.map((request) => request.module + "." + request.method).join("|"));
              console.log("ui.permissions=" + requests.every((request) => request.permissions.indexOf("ui") >= 0));
              console.log("ui.timeout=" + requests[0].timeoutMs);
              console.log("ui.no_raw_view=" + (handle.view === undefined && handle.androidView === undefined));
              console.log("ui.invalid_no_request=" + (requests.length === beforeInvalid));
              console.log("ui.invalid=" + invalidCode);
              console.log("ui.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-ui-fake.js>",
            workingDirectory = workDir,
            timeoutMs = 30_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("ui fake bridge", result)
        assertTrue(result.stdout.contains("ui.resolve=ui"))
        assertTrue(result.stdout.contains("ui.shadow=false"))
        assertTrue(result.stdout.contains("ui.frozen=true"))
        assertTrue(result.stdout.contains("ui.handle=ui-fake-1|function|function|function|true"))
        assertTrue(result.stdout.contains("ui.clicked=ok:ui-fake-1|true"))
        assertTrue(result.stdout.contains("ui.requests=ui.showLayout|ui.drainEvents|ui.update|ui.close"))
        assertTrue(result.stdout.contains("ui.permissions=true"))
        assertTrue(result.stdout.contains("ui.timeout=777"))
        assertTrue(result.stdout.contains("ui.no_raw_view=true"))
        assertTrue(result.stdout.contains("ui.invalid_no_request=true"))
        assertTrue(result.stdout.contains("ui.invalid=${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}"))
        assertTrue(result.stdout.contains("ui.pending=0"))
    }

    @Test
    fun androidUiProviderFailsClosedUntilActivityProviderExists() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val response = dispatch(
            provider,
            method = "showLayout",
            argsJson = """[{"type":"Text","id":"title","text":"Hello"}]""",
        )

        assertFalse(response.toString(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, response.error?.code)
        assertEquals("ui", response.error?.module)
        assertEquals("showLayout", response.error?.method)
        assertTrue(response.error?.message.orEmpty().contains("Activity-owned UI capability provider"))
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        method: String,
        argsJson: String,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "ui-${System.nanoTime()}",
                module = "ui",
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = listOf("ui"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for ui.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing ui.$method response" }
    }
}
