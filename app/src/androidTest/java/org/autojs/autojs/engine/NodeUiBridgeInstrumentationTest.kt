package org.autojs.autojs.engine

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class NodeUiBridgeInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeBridgeUiSessionStore.closeAll("ui-test-before")
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeBridgeUiSessionStore.closeAll("ui-test-after")
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
    fun jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node ui live provider regression")

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(NodeBridgeUiActivity::class.java.name, null, false)
        val resultRef = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val latch = CountDownLatch(1)
        val workDir = File(context.cacheDir, "node-bridge-ui-live-js").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create ui live working directory: $absolutePath" }
        }
        try {
            thread(name = "AutoJs6NodeUiLiveJsTest", isDaemon = true) {
                resultRef.set(
                    NodeEmbeddedTestHarness.runEmbeddedScript(
                        """
                        "nodejs";
                        (async function main() {
                          const ui = require("ui");
                          const handle = await ui.showLayout({
                            type: "Column",
                            children: [
                              { type: "Text", id: "title", text: "Starting" },
                              { type: "Input", id: "name", hint: "Name" },
                              { type: "Button", id: "ok", text: "OK" }
                            ]
                          }, { drainIntervalMs: 10, timeoutMs: 5000 });
                          let clicked = "";
                          let input = "";
                          let inputFrozen = false;
                          let clickFrozen = false;
                          let offClick = function() {};
                          let offInput = function() {};
                          const done = new Promise((resolve) => {
                            function maybeDone() {
                              if (clicked && input) {
                                offClick();
                                offInput();
                                resolve();
                              }
                            }
                            offClick = handle.on("click", (event) => {
                              clicked = event.id + ":" + event.handleId;
                              clickFrozen = Object.isFrozen(event);
                              maybeDone();
                            }, { drainIntervalMs: 10, timeoutMs: 5000 });
                            offInput = handle.on("input", (event) => {
                              input = event.value || event.text || "";
                              inputFrozen = Object.isFrozen(event);
                              maybeDone();
                            }, { drainIntervalMs: 10, timeoutMs: 5000 });
                            setTimeout(resolve, 8000);
                          });
                          await handle.update({ id: "title", text: "Ready" }, { timeoutMs: 5000 });
                          await done;
                          await handle.close({ timeoutMs: 5000 });
                          console.log("ui.live.clicked=" + clicked + "|" + clickFrozen);
                          console.log("ui.live.input=" + input + "|" + inputFrozen);
                          console.log("ui.live.no_raw_view=" + (handle.view === undefined && handle.androidView === undefined));
                        })();
                        """.trimIndent(),
                        "<embedded-autojs-ui-live.js>",
                        workingDirectory = workDir,
                        timeoutMs = 30_000L,
                    ),
                )
                latch.countDown()
            }

            val activity = monitor.waitForActivityWithTimeout(10_000L) as? NodeBridgeUiActivity
            assertNotNull("Node UI Activity did not open for JS live test", activity)
            assertTrue("Node UI Activity did not become ready", waitUntil {
                var ready = false
                instrumentation.runOnMainSync {
                    val title = activity!!.window.decorView.findViewWithTag<View>("title") as? TextView
                    ready = title?.text?.toString() == "Ready"
                }
                ready
            })
            instrumentation.runOnMainSync {
                val root = activity!!.window.decorView
                (root.findViewWithTag<View>("ok") as Button).performClick()
                (root.findViewWithTag<View>("name") as EditText).setText("Bob")
            }
            assertTrue("Timed out waiting for JS live ui result", latch.await(35, TimeUnit.SECONDS))
            val result = checkNotNull(resultRef.get()) { "Missing JS live ui result" }
            NodeEmbeddedTestHarness.assertSucceeded("ui live bridge", result)
            assertTrue(Regex("""ui\.live\.clicked=ok:ui-[^\r\n|]+\|true""").containsMatchIn(result.stdout))
            assertTrue(result.stdout.contains("ui.live.input=Bob|true"))
            assertTrue(result.stdout.contains("ui.live.no_raw_view=true"))
        } finally {
            instrumentation.removeMonitor(monitor)
            NodeBridgeUiSessionStore.closeAll("ui-live-js-test-cleanup")
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    @Test
    fun androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(NodeBridgeUiActivity::class.java.name, null, false)
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "ui-live-provider-1"),
        )
        try {
            val show = dispatch(
                provider,
                method = "showLayout",
                argsJson = """
                    [{
                      "type":"Column",
                      "children":[
                        {"type":"Text","id":"title","text":"Node UI"},
                        {"type":"Row","children":[
                          {"type":"Input","id":"name","hint":"Name"},
                          {"type":"Button","id":"ok","text":"OK"}
                        ]},
                        {"type":"ScrollView","id":"scroll","children":[
                          {"type":"Image","id":"logo","alt":"Logo"}
                        ]}
                      ]
                    }]
                """.trimIndent(),
            )
            assertTrue(show.toString(), show.ok)
            val handleId = JSONObject(show.resultJson.orEmpty()).getString("id")
            assertTrue(handleId.startsWith("ui-"))
            assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.UI_SESSION))

            val activity = monitor.waitForActivityWithTimeout(5_000L) as? NodeBridgeUiActivity
            assertNotNull("Node UI Activity did not open", activity)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val root = activity!!.window.decorView
                assertEquals("Node UI", (root.findViewWithTag<View>("title") as TextView).text.toString())
                (root.findViewWithTag<View>("ok") as Button).performClick()
                val input = root.findViewWithTag<View>("name") as EditText
                input.setText("Alice")
            }

            val events = waitForUiEvents(provider, handleId) { collected ->
                collected.hasEvent("click", "ok") && collected.hasEvent("input", "name")
            }
            val click = events.firstEvent("click", "ok")
            assertEquals(handleId, click.getString("handleId"))
            assertEquals("OK", click.getString("text"))
            val input = events.firstEvent("input", "name")
            assertEquals("Alice", input.getString("value"))
            assertEquals(
                "Alice",
                input
                    .getJSONObject("snapshot")
                    .getJSONObject("state")
                    .getJSONObject("name")
                    .getString("value"),
            )

            val update = dispatch(provider, method = "update", argsJson = """["$handleId",{"id":"title","text":"Updated"}]""")
            assertTrue(update.toString(), update.ok)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(
                    "Updated",
                    (activity!!.window.decorView.findViewWithTag<View>("title") as TextView).text.toString(),
                )
            }

            val close = dispatch(provider, method = "close", argsJson = """["$handleId"]""")
            assertTrue(close.toString(), close.ok)
            assertTrue("UI session was not removed after close", waitUntil { NodeBridgeUiSessionStore.get(handleId) == null })
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
            assertEquals(0, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.UI_SESSION))

            val destroyShow = dispatch(
                provider,
                method = "showLayout",
                argsJson = """[{"type":"Column","children":[{"type":"Button","id":"destroy","text":"Destroy"}]}]""",
            )
            assertTrue(destroyShow.toString(), destroyShow.ok)
            val destroyHandleId = JSONObject(destroyShow.resultJson.orEmpty()).getString("id")
            assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.UI_SESSION))
            provider.destroy()
            assertTrue("Provider destroy did not close UI", waitUntil { NodeBridgeUiSessionStore.get(destroyHandleId) == null })
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        } finally {
            provider.destroy()
            instrumentation.removeMonitor(monitor)
            NodeBridgeUiSessionStore.closeAll("ui-live-test-cleanup")
        }
    }

    @Test
    fun androidUiProviderRejectsUnsupportedDescriptorsAndForeignHandles() {
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "ui-live-provider-negative"),
        )
        val response = dispatch(
            provider,
            method = "showLayout",
            argsJson = """[{"type":"RawView","id":"raw"}]""",
        )

        assertFalse(response.toString(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, response.error?.code)
        assertEquals("ui", response.error?.module)
        assertEquals("showLayout", response.error?.method)
        assertTrue(response.error?.message.orEmpty().contains("unsupported component type"))
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])

        val foreignUpdate = dispatch(
            provider,
            method = "update",
            argsJson = """["ui-foreign",{"id":"title","text":"Nope"}]""",
        )
        assertFalse(foreignUpdate.toString(), foreignUpdate.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, foreignUpdate.error?.code)
        assertTrue(foreignUpdate.error?.message.orEmpty().contains("owned by this execution"))

        val limitProvider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "ui-live-provider-limit"),
        )
        val handles = mutableListOf<String>()
        try {
            repeat(4) { index ->
                val show = dispatch(
                    limitProvider,
                    method = "showLayout",
                    argsJson = """[{"type":"Text","id":"title-$index","text":"Limit $index"}]""",
                )
                assertTrue(show.toString(), show.ok)
                handles += JSONObject(show.resultJson.orEmpty()).getString("id")
            }
            val tooMany = dispatch(
                limitProvider,
                method = "showLayout",
                argsJson = """[{"type":"Text","id":"too-many","text":"Too many"}]""",
            )
            assertFalse(tooMany.toString(), tooMany.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, tooMany.error?.code)
            assertTrue(tooMany.error?.message.orEmpty().contains("active UI session limit"))
        } finally {
            limitProvider.destroy()
            assertTrue("UI limit sessions were not removed", waitUntil { handles.all { NodeBridgeUiSessionStore.get(it) == null } })
        }
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

    private fun waitForUiEvents(
        provider: NodeBridgeCapabilityProvider,
        handleId: String,
        predicate: (JSONArray) -> Boolean,
    ): JSONArray {
        val collected = JSONArray()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val response = dispatch(provider, method = "drainEvents", argsJson = """["$handleId",{"maxEvents":16}]""")
            assertTrue(response.toString(), response.ok)
            val batch = JSONArray(response.resultJson.orEmpty())
            for (index in 0 until batch.length()) {
                collected.put(batch.getJSONObject(index))
            }
            if (predicate(collected)) {
                return collected
            }
            Thread.sleep(50L)
        }
        return collected
    }

    private fun JSONArray.hasEvent(type: String, id: String): Boolean {
        return (0 until length()).any { index ->
            val event = getJSONObject(index)
            event.optString("type") == type && event.optString("id") == id
        }
    }

    private fun JSONArray.firstEvent(type: String, id: String): JSONObject {
        for (index in 0 until length()) {
            val event = getJSONObject(index)
            if (event.optString("type") == type && event.optString("id") == id) {
                return event
            }
        }
        throw AssertionError("Missing UI event $type/$id in $this")
    }

    private fun activeTypeCount(diagnostics: Map<String, String>, type: NodeBridgeResourceType): Int {
        return JSONObject(diagnostics["embedded_script.bridge_resource.active_types"].orEmpty()).optInt(type.name, 0)
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (predicate()) {
                return true
            }
            Thread.sleep(50L)
        }
        return predicate()
    }
}
