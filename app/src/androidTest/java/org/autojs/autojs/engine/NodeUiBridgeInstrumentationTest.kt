package org.autojs.autojs.engine

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Switch as AndroidSwitch
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

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeBridgeUiSessionStore.closeAll("ui-test-before")
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodeBridgeUiSessionStore.closeAll("ui-test-after")
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun kotlinUiBridgeRegistryManifestAndFakeProviderAreStable() {
        listOf("showLayout", "update", "batchUpdate", "close", "drainEvents").forEach { method ->
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
                "batchUpdate" -> {
                    val args = JSONArray(request.argsJson)
                    assertEquals("fake-ui-1", args.getString(0))
                    assertEquals("Batch", args.getJSONArray(1).getJSONObject(0).getString("text"))
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
        val batch = dispatch(provider, method = "batchUpdate", argsJson = """["fake-ui-1",[{"id":"title","text":"Batch"}]]""")
        val drain = dispatch(provider, method = "drainEvents", argsJson = """["fake-ui-1",{"maxEvents":16}]""")
        val close = dispatch(provider, method = "close", argsJson = """["fake-ui-1"]""")

        assertTrue(show.toString(), show.ok)
        assertEquals("fake-ui-1", JSONObject(show.resultJson.orEmpty()).getString("id"))
        assertTrue(update.toString(), update.ok)
        assertTrue(batch.toString(), batch.ok)
        assertTrue(drain.toString(), drain.ok)
        assertEquals("ok", JSONArray(drain.resultJson.orEmpty()).getJSONObject(0).getString("id"))
        assertTrue(close.toString(), close.ok)
        assertEquals(listOf("showLayout", "update", "batchUpdate", "drainEvents", "close"), provider.requests.map { it.method })
    }

    @Test
    fun uiSessionTerminalCloseRemainsLastAfterPostTerminalEventFlood() {
        val session = NodeBridgeUiSession(
            id = "ui-terminal-event-order",
            descriptor = JSONObject().put("type", "Text").put("id", "title").put("text", "Before close"),
        )
        session.recordState("before", JSONObject().put("value", "kept"))
        session.emitComponentEvent("click", "before")
        assertTrue(session.update(JSONObject().put("id", "title").put("text", "Updated before close")))

        session.close("terminal_test")
        repeat(256) { index ->
            session.recordState("late-$index", JSONObject().put("value", index))
            session.emitComponentEvent("input", "late-$index", JSONObject().put("value", index))
            session.markShown()
            assertFalse(session.update(JSONObject().put("id", "title").put("text", "Late $index")))
            assertFalse(session.batchUpdate(listOf(JSONObject().put("id", "title").put("text", "Late batch $index"))))
        }
        session.close("duplicate_terminal_test")

        val events = session.drainEvents(512)
        assertEquals(3, events.length())
        assertEquals(1, events.eventTypeCount("close"))
        val terminal = events.getJSONObject(events.length() - 1)
        assertEquals("close", terminal.getString("type"))
        assertEquals("terminal_test", terminal.getString("reason"))
        assertTrue(terminal.getJSONObject("snapshot").getBoolean("closed"))

        val snapshot = session.snapshot()
        assertFalse(snapshot.getBoolean("shown"))
        assertEquals(1, snapshot.getJSONObject("state").length())
        assertTrue(snapshot.getJSONObject("state").has("before"))
        assertEquals(0, session.drainEvents(512).length())
    }

    @Test
    fun jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node ui bridge protocol regression")

        val workDir = File(context.cacheDir, "node-bridge-ui-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create ui bridge working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted package also travels as a module-source overlay; otherwise the plugin never
        // sees it and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true, showLayout: () => "shadow" };"""
        File(workDir, "node_modules/ui").apply {
            check(mkdirs()) { "Failed to create ui shadow module directory: $absolutePath" }
        }.resolve("index.js").writeText(shadowPackageSource)

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("ui"))
        val result = NodePluginTestHarness.runPluginScript(
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
                  } else if (request.method === "batchUpdate") {
                    respond(request, { handleId: "ui-fake-1" });
                  } else {
                    respond(request, null);
                  }
                }
              });

              const ui = require("ui");
              const model = ui.state({ title: "Node UI", enabled: true, accepted: false, progress: 25 });
              const handle = await ui.showLayout({
                type: "Vertical",
                children: [
                  { type: "Text", id: "title", text: ui.bind(model, "title") },
                  { type: "Switch", id: "enabled", text: "Enabled", checked: ui.bind(model, "enabled") },
                  { type: "Checkbox", id: "accepted", text: "Accepted", checked: ui.bind(model, "accepted") },
                  { type: "Progress", id: "progress", progress: ui.bind(model, "progress"), max: 100 },
                  { type: "List", id: "choices", items: ["One", { text: "Two", value: 2 }] },
                  { type: "Tabs", id: "tabs", tabs: [
                    { id: "first", title: "First", content: { type: "Text", id: "tab-label", text: "Tab" } },
                    { id: "second", title: "Second", content: { type: "Text", id: "tab-label-2", text: "Tab 2" } }
                  ] },
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
              model.set("title", "Bound");
              model.set("enabled", false);
              model.set("progress", 60);
              await new Promise((resolve) => setTimeout(resolve, 30));
              await handle.batchUpdate([
                { id: "title", text: "Batch" },
                { id: "progress", progress: 75 }
              ], { timeoutMs: 779 });
              await handle.update({ id: "title", text: "Updated" }, { timeoutMs: 779 });
              await handle.close({ timeoutMs: 780 });
              const beforeInvalid = requests.length;
              let invalidCode = "";
              try {
                await ui.showLayout({ type: "Text", bad: function() {} });
              } catch (error) {
                invalidCode = codeOf(error);
              }
              const showDescriptor = requests[0].args[0];
              const batchSizes = requests
                .filter((request) => request.method === "batchUpdate")
                .map((request) => request.args[1].length)
                .join(",");

              console.log("ui.resolve=" + require.resolve("ui"));
              console.log("ui.shadow=" + (ui.shadow === true));
              console.log("ui.frozen=" + Object.isFrozen(ui));
              console.log("ui.exports=" + [typeof ui.showLayout, typeof ui.state, typeof ui.bind].join("|"));
              console.log("ui.handle=" + [handle.id, typeof handle.on, typeof handle.update, typeof handle.batchUpdate, typeof handle.close, Object.isFrozen(handle)].join("|"));
              console.log("ui.clicked=" + clicked + "|" + eventFrozen);
              console.log("ui.bound_initial=" + [
                showDescriptor.children[0].text,
                showDescriptor.children[1].checked,
                showDescriptor.children[2].checked,
                showDescriptor.children[3].progress
              ].join("|"));
              console.log("ui.batch_sizes=" + batchSizes);
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
            moduleSources = mapOf("node_modules/ui/index.js" to shadowPackageSource),
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            timeoutMs = 30_000L,
        )

        NodePluginTestHarness.assertSucceeded("ui fake bridge", result)
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.resolve=autojs6:ui")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.shadow=false")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.frozen=true")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.exports=function|function|function")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.handle=ui-fake-1|function|function|function|function|true")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.clicked=ok:ui-fake-1|true")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.bound_initial=Node UI|true|false|25")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.batch_sizes=3,2")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.requests=ui.showLayout|ui.drainEvents|ui.batchUpdate|ui.batchUpdate|ui.update|ui.close")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.permissions=true")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.timeout=777")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.no_raw_view=true")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.invalid_no_request=true")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.invalid=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}")
        NodePluginTestHarness.assertStdoutContains("ui fake bridge", result, "ui.pending=0")
    }

    @Test
    fun jsUiModuleUsesAndroidLiveProviderForButtonAndInputEvents() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node ui live provider regression")

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(NodeBridgeUiActivity::class.java.name, null, false)
        val resultRef = AtomicReference<NodePluginScriptResult?>()
        val latch = CountDownLatch(1)
        val workDir = File(context.cacheDir, "node-bridge-ui-live-js").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create ui live working directory: $absolutePath" }
        }
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("ui"))
        try {
            thread(name = "AutoJs6NodeUiLiveJsTest", isDaemon = true) {
                resultRef.set(
                    NodePluginTestHarness.runPluginScript(
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
                        runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                        timeoutMs = 30_000L,
                    ),
                )
                latch.countDown()
            }

            val activity = monitor.waitForActivityWithTimeout(10_000L) as? NodeBridgeUiActivity
            if (activity == null) {
                latch.await(5, TimeUnit.SECONDS)
                val result = resultRef.get()
                assertNotNull(
                    "Node UI Activity did not open for JS live test" + (
                        result?.let { "\n${NodePluginTestHarness.describeResult("ui live bridge", it)}" }
                            ?: "\nMissing JS live ui result"
                        ),
                    activity,
                )
            }
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
            NodePluginTestHarness.assertSucceeded("ui live bridge", result)
            assertTrue(Regex("""ui\.live\.clicked=ok:ui-[^\r\n|]+\|true""").containsMatchIn(result.stdout))
            assertTrue(result.stdout.contains("ui.live.input=Bob|true"))
            assertTrue(result.stdout.contains("ui.live.no_raw_view=true"))
            assertEquals("jni", result.nativeValues["embedded_script.bridge_live_transport"])
            assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_poll_count"])
            assertTrue(result.nativeValues["embedded_script.bridge_live_event_count"]!!.toInt() >= 2)

        } finally {
            instrumentation.removeMonitor(monitor)
            NodeBridgeUiSessionStore.closeAll("ui-live-js-test-cleanup")
            NodePluginTestHarness.settlePluginRuntime()
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
                        ]},
                        {"type":"Switch","id":"enabled","text":"Enabled","checked":false},
                        {"type":"Checkbox","id":"accepted","text":"Accepted","checked":false},
                        {"type":"Progress","id":"progress","progress":25,"max":100},
                        {"type":"List","id":"choices","items":["One",{"text":"Two","value":2}]},
                        {"type":"Tabs","id":"tabs","tabs":[
                          {"id":"first","title":"First","content":{"type":"Text","id":"tab-first","text":"First tab"}},
                          {"id":"second","title":"Second","content":{"type":"Text","id":"tab-second","text":"Second tab"}}
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
                (root.findViewWithTag<View>("enabled") as AndroidSwitch).isChecked = true
                (root.findViewWithTag<View>("accepted") as CheckBox).performClick()
                val list = root.findViewWithTag<View>("choices") as ListView
                list.performItemClick(null, 1, 1L)
                val tabs = root.findViewWithTag<View>("tabs") as LinearLayout
                val tabRow = tabs.getChildAt(0) as LinearLayout
                (tabRow.getChildAt(1) as Button).performClick()
            }

            val events = waitForUiEvents(provider, handleId) { collected ->
                collected.hasEvent("click", "ok") &&
                    collected.hasEvent("input", "name") &&
                    collected.hasEvent("change", "enabled") &&
                    collected.hasEvent("change", "accepted") &&
                    collected.hasEvent("select", "choices") &&
                    collected.hasEvent("tab", "tabs")
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
            assertTrue(events.firstEvent("change", "enabled").getBoolean("checked"))
            assertTrue(events.firstEvent("change", "accepted").getBoolean("checked"))
            val select = events.firstEvent("select", "choices")
            assertEquals(1, select.getInt("selectedIndex"))
            assertEquals("Two", select.getString("text"))
            assertEquals(2, select.getInt("value"))
            val tab = events.firstEvent("tab", "tabs")
            assertEquals(1, tab.getInt("selectedIndex"))
            assertEquals("second", tab.getString("tabId"))

            val batchUpdate = dispatch(
                provider,
                method = "batchUpdate",
                argsJson = """["$handleId",[{"id":"progress","progress":90},{"id":"title","text":"Batch Updated"}]]""",
            )
            assertTrue(batchUpdate.toString(), batchUpdate.ok)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(90, (activity!!.window.decorView.findViewWithTag<View>("progress") as ProgressBar).progress)
                assertEquals(
                    "Batch Updated",
                    (activity.window.decorView.findViewWithTag<View>("title") as TextView).text.toString(),
                )
            }

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
            val terminalBeforeDestroy = checkNotNull(NodeBridgeUiSessionStore.get(destroyHandleId)) {
                "Missing UI session for closed-before-provider-destroy cleanup"
            }
            terminalBeforeDestroy.close("terminal_before_provider_destroy")
            assertTrue(terminalBeforeDestroy.isClosed())
            assertTrue(NodeBridgeUiSessionStore.get(destroyHandleId) === terminalBeforeDestroy)
            provider.destroy()
            assertTrue("Provider destroy did not remove the already-closed UI session", waitUntil {
                NodeBridgeUiSessionStore.get(destroyHandleId) == null
            })
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
            assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.destroy_closed_count"])
        } finally {
            provider.destroy()
            instrumentation.removeMonitor(monitor)
            NodeBridgeUiSessionStore.closeAll("ui-live-test-cleanup")
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun androidUiProviderRecordsBackActivityDestroyLifecycleEvent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(NodeBridgeUiActivity::class.java.name, null, false)
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "ui-live-provider-back"),
        )
        var handleId = ""
        try {
            val show = dispatch(
                provider,
                method = "showLayout",
                argsJson = """[{"type":"Column","children":[{"type":"Text","id":"title","text":"Back lifecycle"}]}]""",
            )
            assertTrue(show.toString(), show.ok)
            handleId = JSONObject(show.resultJson.orEmpty()).getString("id")
            val session = checkNotNull(NodeBridgeUiSessionStore.get(handleId)) { "Missing Back lifecycle UI session" }
            assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.UI_SESSION))

            val activity = monitor.waitForActivityWithTimeout(5_000L) as? NodeBridgeUiActivity
            assertNotNull("Node UI Activity did not open for Back lifecycle evidence", activity)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                activity!!.onBackPressed()
            }
            assertTrue("Back did not finish the Node UI Activity", waitUntil {
                activity!!.isFinishing || activity.isDestroyed
            })
            assertTrue("Activity destroy did not make the Node UI session terminal", waitUntil {
                NodeBridgeUiSessionStore.get(handleId)?.isClosed() == true
            })

            val terminalUpdate = dispatch(
                provider,
                method = "update",
                argsJson = """["$handleId",{"id":"title","text":"Must not apply"}]""",
            )
            assertFalse(terminalUpdate.toString(), terminalUpdate.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, terminalUpdate.error?.code)
            assertTrue(terminalUpdate.error?.message.orEmpty().contains("active provider-owned UI handle"))

            session.close("duplicate_after_activity_destroy")

            val events = waitForUiEvents(provider, handleId) { collected ->
                collected.hasEventType("close")
            }
            val close = events.firstEventType("close")
            assertEquals("ui.lifecycle.back.reason=activity_destroyed", "activity_destroyed", close.getString("reason"))
            assertEquals(handleId, close.getString("handleId"))
            assertTrue(close.getJSONObject("snapshot").getBoolean("closed"))
            assertEquals("Activity destroy must emit exactly one terminal close event", 1, events.eventTypeCount("close"))
            assertTrue("Terminal drain did not remove the provider UI session", waitUntil {
                NodeBridgeUiSessionStore.get(handleId) == null
            })

            val diagnostics = provider.nativeDiagnostics()
            assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
            assertEquals("1", diagnostics["embedded_script.bridge_resource.created_count"])
            assertEquals("1", diagnostics["embedded_script.bridge_resource.closed_count"])
            assertEquals("0", diagnostics["embedded_script.bridge_resource.destroy_closed_count"])
            assertEquals(0, activeTypeCount(diagnostics, NodeBridgeResourceType.UI_SESSION))

            val staleUpdate = dispatch(
                provider,
                method = "update",
                argsJson = """["$handleId",{"id":"title","text":"Still must not apply"}]""",
            )
            assertFalse(staleUpdate.toString(), staleUpdate.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, staleUpdate.error?.code)
            assertTrue(staleUpdate.error?.message.orEmpty().contains("owned by this execution"))
        } finally {
            provider.destroy()
            instrumentation.removeMonitor(monitor)
            if (handleId.isNotBlank()) {
                assertTrue("Back lifecycle UI session was not removed during cleanup", waitUntil {
                    NodeBridgeUiSessionStore.get(handleId) == null
                })
            }
            NodeBridgeUiSessionStore.closeAll("ui-back-lifecycle-test-cleanup")
        }
    }

    @Test
    fun androidUiProviderRecordsRecentsTaskRemovalLifecycleEvent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(NodeBridgeUiActivity::class.java.name, null, false)
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "ui-live-provider-recents"),
        )
        var handleId = ""
        try {
            val show = dispatch(
                provider,
                method = "showLayout",
                argsJson = """[{"type":"Column","children":[{"type":"Text","id":"title","text":"Recents task removal"}]}]""",
            )
            assertTrue(show.toString(), show.ok)
            handleId = JSONObject(show.resultJson.orEmpty()).getString("id")
            val session = checkNotNull(NodeBridgeUiSessionStore.get(handleId)) { "Missing Recents UI session" }
            assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.UI_SESSION))

            val activity = monitor.waitForActivityWithTimeout(5_000L) as? NodeBridgeUiActivity
            assertNotNull("Node UI Activity did not open for Recents/task-removal lifecycle evidence", activity)
            instrumentation.waitForIdleSync()
            var movedToBack = false
            instrumentation.runOnMainSync {
                movedToBack = activity!!.moveTaskToBack(true)
                activity.finishAndRemoveTask()
            }
            assertTrue("Node UI Activity task did not move to back before task removal", movedToBack)
            assertTrue("Task removal did not finish the Node UI Activity", waitUntil {
                activity!!.isFinishing || activity.isDestroyed
            })

            assertTrue("Task removal did not make the Node UI session terminal", waitUntil {
                NodeBridgeUiSessionStore.get(handleId)?.isClosed() == true
            })

            session.close("duplicate_after_task_removal")

            val firstDrain = dispatch(
                provider,
                method = "drainEvents",
                argsJson = """["$handleId",{"maxEvents":1}]""",
            )
            assertTrue(firstDrain.toString(), firstDrain.ok)
            val firstEvents = JSONArray(firstDrain.resultJson.orEmpty())
            assertEquals(1, firstEvents.length())
            assertTrue(firstEvents.hasEventType("shown"))
            assertFalse(firstEvents.hasEventType("close"))
            assertTrue("Non-terminal drain removed the closed UI session too early", NodeBridgeUiSessionStore.get(handleId) === session)
            assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.UI_SESSION))

            val terminalDrain = dispatch(
                provider,
                method = "drainEvents",
                argsJson = """["$handleId",{"maxEvents":1}]""",
            )
            assertTrue(terminalDrain.toString(), terminalDrain.ok)
            val terminalEvents = JSONArray(terminalDrain.resultJson.orEmpty())
            assertEquals(1, terminalEvents.length())
            val close = terminalEvents.firstEventType("close")
            assertEquals("ui.lifecycle.recents_task_removed.reason=activity_destroyed", "activity_destroyed", close.getString("reason"))
            assertEquals(handleId, close.getString("handleId"))
            assertTrue(close.getJSONObject("snapshot").getBoolean("closed"))
            assertEquals("Task removal must emit exactly one terminal close event", 1, terminalEvents.eventTypeCount("close"))
            assertTrue("Terminal drain did not remove the Recents UI session", waitUntil {
                NodeBridgeUiSessionStore.get(handleId) == null
            })

            val diagnostics = provider.nativeDiagnostics()
            assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
            assertEquals("1", diagnostics["embedded_script.bridge_resource.created_count"])
            assertEquals("1", diagnostics["embedded_script.bridge_resource.closed_count"])
            assertEquals("0", diagnostics["embedded_script.bridge_resource.destroy_closed_count"])
            assertEquals(0, activeTypeCount(diagnostics, NodeBridgeResourceType.UI_SESSION))

            val staleUpdate = dispatch(
                provider,
                method = "update",
                argsJson = """["$handleId",{"id":"title","text":"Must not apply after task removal"}]""",
            )
            assertFalse(staleUpdate.toString(), staleUpdate.ok)
            assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, staleUpdate.error?.code)
            assertTrue(staleUpdate.error?.message.orEmpty().contains("owned by this execution"))
        } finally {
            provider.destroy()
            instrumentation.removeMonitor(monitor)
            if (handleId.isNotBlank()) {
                assertTrue("Recents/task-removal lifecycle UI session was not removed during cleanup", waitUntil {
                    NodeBridgeUiSessionStore.get(handleId) == null
                })
            }
            NodeBridgeUiSessionStore.closeAll("ui-recents-task-removal-test-cleanup")
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun androidUiActivityKeepsConfigurationChangesAsDestroyPolicy() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, NodeBridgeUiActivity::class.java),
            0,
        )
        val configurationMask = ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN

        assertEquals(
            "ui.configuration_change_policy=destroy_recreate_no_survival_claim",
            0,
            info.configChanges and configurationMask,
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, info.screenOrientation)
        assertFalse(info.exported)
        assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
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

        val webView = dispatch(
            provider,
            method = "showLayout",
            argsJson = """[{"type":"WebView","id":"web"}]""",
        )
        assertFalse(webView.toString(), webView.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, webView.error?.code)
        assertTrue(webView.error?.message.orEmpty().contains("separate security gate"))
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

    private fun JSONArray.hasEventType(type: String): Boolean {
        return (0 until length()).any { index ->
            getJSONObject(index).optString("type") == type
        }
    }

    private fun JSONArray.eventTypeCount(type: String): Int {
        return (0 until length()).count { index ->
            getJSONObject(index).optString("type") == type
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

    private fun JSONArray.firstEventType(type: String): JSONObject {
        for (index in 0 until length()) {
            val event = getJSONObject(index)
            if (event.optString("type") == type) {
                return event
            }
        }
        throw AssertionError("Missing UI event $type in $this")
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
