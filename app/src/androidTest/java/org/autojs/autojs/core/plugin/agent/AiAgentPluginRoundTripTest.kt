package org.autojs.autojs.core.plugin.agent

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.hostbroker.*
import org.autojs.plugin.ai.agent.api.*
import org.autojs.plugin.ai.agent.api.AiAgentContract as C
import org.autojs.plugin.host.capability.api.*
import org.autojs.plugin.host.capability.api.HostCapabilityContract as H
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in installed-plugin conformance. Runs as the real host UID, with no production authentication bypass. */
class AiAgentPluginRoundTripTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pluginPackage = "io.github.supermonster003.autojs6.plugin.ai.agent"
    private fun bundle(key: String, json: JSONObject) = Bundle().apply { putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putString(key, json.toString()) }
    private fun decode(bundle: Bundle, key: String = C.KEY_RUN_RESPONSE_JSON): JSONObject {
        assertNull(bundle.getString(C.KEY_ERROR_CODE)); return JSONObject(bundle.getString(key)!!)
    }
    private fun tool(name: String, args: JSONObject = JSONObject()) = JSONObject().put("kind", "tool").put("tool", name).put("arguments", args)
    private fun done(orderStatus: String? = null) = JSONObject().put("kind", "done").put("done", JSONObject().put("status", "completed").put("summary", "Verified by test").put("evidence", JSONArray().put("Observed final state")).apply { orderStatus?.let { put("orderStatus", it) } })
    private fun ref(id: String) = bundle(C.KEY_RUN_REF_JSON, JSONObject().put("runId", id))
    private fun waitUntil(message: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(30)
        assertTrue(message, condition())
    }
    private fun withPlugin(action: (IAiAgentPlugin) -> Unit) {
        assumeTrue("Opt in with -e autojs.agent.plugin true", InstrumentationRegistry.getArguments().getString("autojs.agent.plugin").toBoolean())
        val latch = CountDownLatch(1)
        var api: IAiAgentPlugin? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) { api = IAiAgentPlugin.Stub.asInterface(service); latch.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName(pluginPackage, "$pluginPackage.AiAgentPluginService")), connection, Context.BIND_AUTO_CREATE))
        try { assertTrue("Plugin bind", latch.await(15, TimeUnit.SECONDS)); action(checkNotNull(api)) }
        finally { context.unbindService(connection) }
    }
    private inner class ScriptedModel(private val zeroUsage: Boolean = false, private val decision: (Int, JSONObject) -> JSONObject?) : IAiAgentModelBroker.Stub() {
        val calls = AtomicInteger()
        val cancelled = AtomicInteger()
        @Volatile var pending: Pair<String, IAiAgentModelCallback>? = null
        @Volatile var failure: Throwable? = null
        fun finishPending(value: JSONObject = done()) { pending?.let { (id, callback) -> send(callback, id, "completed", 2, JSONObject().put("text", value.toString()).put("targetId", "conformance:scripted").put("finishReason", 0)) } }
        override fun getBrokerInfo() = bundle(C.KEY_MODEL_BROKER_INFO_JSON, JSONObject().put("available", true).put("providerId", "conformance")
            .put("maximumInputBytes", 131072).put("maximumOutputBytes", 65536).put("maximumResponseSchemaBytes", 16384)).apply {
            putString(H.KEY_GRANT_JSON, JSONObject().put("maxInputBytesPerRequest", 131072).put("maxTotalTokens", 1000000).put("consumedTokens", 0).toString())
        }
        private fun send(callback: IAiAgentModelCallback, id: String, type: String, sequence: Int, extra: JSONObject = JSONObject()) {
            extra.put("requestId", id).put("type", type).put("sequence", sequence)
            callback.onEvent(bundle(C.KEY_MODEL_EVENT_JSON, extra))
        }
        override fun listTargets(request: Bundle, callback: IAiAgentModelCallback) {
            val id = JSONObject(request.getString(C.KEY_MODEL_REQUEST_JSON)!!).getString("requestId")
            send(callback, id, "started", 1)
            val target = JSONObject().put("targetId", "conformance:scripted").put("displayName", "Scripted")
                .put("locality", 2).put("configured", true).put("available", true).put("maximumContextBytes", 131072)
                .put("capabilityIds", JSONArray()).put("supportedControls", JSONArray().put("maximum-output-tokens"))
            send(callback, id, "completed", 2, JSONObject().put("targets", JSONArray().put(target)))
        }
        override fun generate(request: Bundle, callback: IAiAgentModelCallback) {
            val value = JSONObject(request.getString(C.KEY_MODEL_REQUEST_JSON)!!)
            val id = value.getString("requestId")
            send(callback, id, "started", 1)
            pending = id to callback
            val text = try { decision(calls.getAndIncrement(), value) } catch (error: Throwable) {
                failure = error; send(callback, id, "failed", 2, JSONObject().put("code", C.ERROR_MODEL_FAILED)); return
            } ?: return
            if (zeroUsage) send(callback, id, "usage", 2, JSONObject().put("usage", JSONObject().put("inputTokens", 0).put("outputTokens", 0).put("totalTokens", 0)))
            send(callback, id, "completed", if (zeroUsage) 3 else 2, JSONObject().put("text", text.toString()).put("targetId", "conformance:scripted").put("finishReason", 0))
        }
        override fun cancel(reference: Bundle?) { cancelled.incrementAndGet() }
        override fun destroy(reason: Bundle?) = Unit
    }
    private fun attach(api: IAiAgentPlugin, model: IAiAgentModelBroker, broker: IHostCapabilityBroker, config: JSONObject = JSONObject()): IAiAgentLink =
        api.attach(bundle(C.KEY_LINK_CONFIG_JSON, config), model, broker, object : IAiAgentLinkCallback.Stub() {
            override fun onStatus(status: Bundle?) = Unit
            override fun onEvent(event: Bundle?) = Unit
        })
    private fun core() = HostCapabilityBrokerCore(context, "agent-plugin-test", AiAgentGrant.default(), sourceName = "ai-agent", engineName = "ai-agent")
    private fun broker(core: HostCapabilityBrokerCore) = HostCapabilityBrokerStub(core, context.packageManager.getApplicationInfo(pluginPackage, 0).uid)
    private fun start(link: IAiAgentLink, events: IAiAgentRunCallback? = null, goal: String = "conformance", confirm: String = "default"): String {
        val started = SystemClock.elapsedRealtime()
        val answer = link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", goal).put("options", JSONObject().put("interaction", "script").put("confirm", confirm))), events)
        assertTrue("startRun must enqueue within 200 ms", SystemClock.elapsedRealtime() - started < 200)
        return decode(answer).getString("runId")
    }
    private fun detach(link: IAiAgentLink) = link.detach(bundle(H.KEY_REASON_JSON, JSONObject().put("reason", "test-finished")))

    @Test fun absentOcrIsHiddenFromTheRealPluginModel() = optionalOcrRoundTrip(false)
    @Test fun availableOcrIsAdmittedAndMergedByTheRealPlugin() = optionalOcrRoundTrip(true)
    private fun optionalOcrRoundTrip(available: Boolean) = withPlugin { api ->
        val core = core(); val delegate = broker(core); val calls = AtomicInteger()
        val capabilities = object : IHostCapabilityBroker.Stub() {
            override fun getBrokerInfo() = delegate.brokerInfo.apply {
                putStringArray(H.KEY_AVAILABLE_OPTIONAL_METHODS, if (available) arrayOf("accessibility.readScreenText") else emptyArray())
            }
            override fun destroy(reason: Bundle?) = delegate.destroy(reason)
            override fun dispatch(request: Bundle, callback: IHostCapabilityCallback) {
                val value = JSONObject(request.getString(H.KEY_BRIDGE_REQUEST_JSON)!!)
                if (value.optString("method") != "readScreenText") { delegate.dispatch(request, callback); return }
                assertTrue(available); calls.incrementAndGet()
                fun word(text: String, x: Int) = JSONObject().put("text", text).put("confidence", 0.9)
                    .put("bounds", JSONObject().put("left", x).put("top", 5).put("right", x + 20).put("bottom", 15))
                val result = JSONArray().put(word("Hello", 10)).put(word("screen", 35))
                callback.onResponse(Bundle().apply {
                    putBoolean(H.KEY_BRIDGE_RESPONSE_OK, true)
                    putString(H.KEY_BRIDGE_RESPONSE_JSON, JSONObject().put("id", value.getString("id")).put("ok", true).put("result", result).toString())
                })
            }
        }
        val model = ScriptedModel { step, request ->
            if (step == 0) {
                assertEquals(available, request.getJSONArray("messages").getJSONObject(0).getString("content").contains("ocr_screen"))
                if (available) tool("ocr_screen") else done()
            } else {
                val lines = observation(request).getJSONObject("result").getJSONArray("lines")
                assertEquals(1, lines.length()); assertEquals("Hello screen", lines.getJSONObject(0).getString("text"))
                assertEquals(55, lines.getJSONObject(0).getJSONObject("bounds").getInt("right")); done()
            }
        }
        val link = attach(api, model, capabilities)
        try {
            val id = start(link)
            waitUntil("OCR discovery run finishes") { decode(link.getRun(ref(id))).optString("state") in setOf("completed", "failed", "blocked") }
            model.failure?.let { throw AssertionError("OCR fixture failed", it) }
            assertEquals("completed", decode(link.getRun(ref(id))).getString("state")); assertEquals(if (available) 1 else 0, calls.get())
        } finally { detach(link); core.destroy(null) }
    }

    @Test fun readOnlyScreenObservationsRoundTripThroughRealAccessibility() = withPlugin { api ->
        assumeTrue("Private AVD only: -e autojs.agent.observe true", InstrumentationRegistry.getArguments().getString("autojs.agent.observe").toBoolean())
        val service = "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        val previous = shell("settings get secure enabled_accessibility_services").trim().takeUnless { it == "null" }.orEmpty().split(':').filter { it.isNotBlank() }.toSet()
        val others = previous - service
        shell(if (others.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services ${others.joinToString(":")}")
        SystemClock.sleep(400)
        shell("settings put secure enabled_accessibility_services ${(others + service).joinToString(":")}")
        shell("settings put secure accessibility_enabled 1")
        assertTrue(org.autojs.autojs.core.accessibility.AccessibilityService.waitForOperational(10_000))
        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(1000)
        var snapshot: String? = null
        val core = core()
        val model = ScriptedModel { step, request ->
            val result = if (step == 0) null else observation(request).getJSONObject("result")
            when (step) {
                0 -> tool("app_current")
                1 -> { assertEquals("com.android.settings", result!!.getString("packageName")); tool("ui_dump") }
                2 -> { snapshot = result!!.getString("snapshotId"); assertTrue(result.getInt("nodeCount") > 0); assertTrue(result.getJSONObject("changes").getBoolean("baseline")); tool("ui_dump") }
                3 -> { assertEquals(snapshot, result!!.getJSONObject("changes").getString("previousSnapshotId")); assertFalse(result.getJSONObject("changes").getBoolean("baseline")); tool("ui_find", JSONObject().put("selector", JSONObject().put("className", "android.widget.TextView")).put("limit", 3)) }
                4 -> { assertTrue(result!!.getJSONArray("nodes").length() in 1..3); tool("ui_wait_for", JSONObject().put("selector", JSONObject().put("className", "android.widget.TextView")).put("state", "appear")) }
                5 -> { assertTrue(result!!.getBoolean("matched")); tool("ui_wait_for", JSONObject().put("selector", JSONObject().put("text", "AUTOJS6_P41_NO_SUCH_NODE")).put("state", "disappear")) }
                6 -> { assertTrue(result!!.getBoolean("matched")); tool("screen_state") }
                7 -> { assertTrue(result!!.getBoolean("screenOn")); tool("device_info") }
                8 -> { assertTrue(result!!.length() > 0); tool("console_tail") }
                else -> { assertEquals("global-window", result!!.getString("consoleCaptureMode")); assertTrue(result.has("lines")); done() }
            }
        }
        val link = attach(api, model, broker(core))
        try {
            val id = start(link)
            waitUntil("Read-only screen workflow finishes") { decode(link.getRun(ref(id))).optString("state") in setOf("completed", "failed", "blocked") }
            model.failure?.let { throw AssertionError("Observation fixture failed", it) }
            assertEquals("completed", decode(link.getRun(ref(id))).getString("state")); assertEquals(10, model.calls.get())
        } finally {
            detach(link); core.destroy(null)
            shell(if (previous.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services ${previous.joinToString(":")}")
        }
    }

    @Test fun registeredScriptsAreRankedQueriedCachedRefreshedAndScopedThroughTheRealHost() = withPlugin { api ->
        val oldWork = org.autojs.autojs.util.WorkingDirectoryUtils.path
        val root = java.io.File(Environment.getExternalStorageDirectory(), "AgentP31Catalog-${System.nanoTime()}")
        val work = java.io.File(root, "work"); val extra = java.io.File(root, "extra")
        assertTrue(work.mkdirs()); assertTrue(extra.mkdirs())
        val extraRoots = listOf(extra) + (1..31).map { java.io.File(root, "extra-$it").apply { assertTrue(mkdirs()) } }
        fun script(id: String, description: String) = java.io.File(extra, "$id.js").writeText(
            "/**\n * @agent\n * @description $description\n * @risk readonly\n */\nthrow new Error('SCRIPT_SOURCE_MUST_NOT_APPEAR');")
        try {
            org.autojs.autojs.util.WorkingDirectoryUtils.path = work.path
            repeat(29) { script("entry-${it.toString().padStart(2, '0')}", if (it == 28) "uniquearchive" else "unrelated utility") }
            script("coffee", "星巴克拿铁咖啡")
            val catalogCalls = AtomicInteger()
            val core = core(); core.prepareAgentScriptRoots(extraRoots)()
            val delegate = broker(core)
            val counted = object : IHostCapabilityBroker.Stub() {
                override fun getBrokerInfo() = delegate.brokerInfo
                override fun dispatch(request: Bundle, callback: IHostCapabilityCallback) {
                    val call = JSONObject(request.getString(H.KEY_BRIDGE_REQUEST_JSON)!!)
                    if (call.optString("module") == "agent" && call.optString("method") == "listScripts") catalogCalls.incrementAndGet()
                    delegate.dispatch(request, callback)
                }
                override fun destroy(reason: Bundle?) = delegate.destroy(reason)
            }
            fun scripts(request: JSONObject): JSONObject {
                val content = request.getJSONArray("messages").getJSONObject(0).getString("content")
                assertFalse(content.contains("SCRIPT_SOURCE_MUST_NOT_APPEAR")); assertFalse(content.contains(extra.path))
                return JSONObject(content.lineSequence().last { it.startsWith('{') })
            }
            val model = ScriptedModel { step, request ->
                when (step) {
                    0 -> {
                        val data = scripts(request)
                        assertEquals(24, data.getJSONArray("scripts").length())
                        assertEquals("coffee", data.getJSONArray("scripts").getJSONObject(0).getString("id"))
                        assertEquals(30, data.getInt("total")); assertEquals(1, catalogCalls.get())
                        tool("script_catalog", JSONObject().put("query", "uniquearchive"))
                    }
                    1 -> {
                        val messages = request.getJSONArray("messages")
                        val observation = (messages.length() - 1 downTo 0).mapNotNull { index ->
                            val content = messages.getJSONObject(index).getString("content")
                            runCatching { JSONObject(content.substring(content.indexOf('{'))) }.getOrNull()
                        }.first { it.optString("section") == "observation" && it.optJSONObject("data")?.has("result") == true }
                            .getJSONObject("data").getJSONObject("result")
                        assertEquals(1, observation.getJSONArray("scripts").length())
                        assertEquals("entry-28", observation.getJSONArray("scripts").getJSONObject(0).getString("id"))
                        assertEquals("Query must reuse the link cache", 1, catalogCalls.get())
                        script("fresh", "newfreshmarker")
                        done()
                    }
                    2 -> {
                        assertEquals("fresh", scripts(request).getJSONArray("scripts").getJSONObject(0).getString("id"))
                        assertEquals(2, catalogCalls.get()); done()
                    }
                    3 -> {
                        assertEquals(0, scripts(request).getJSONArray("scripts").length())
                        assertEquals(3, catalogCalls.get()); done()
                    }
                    else -> throw AssertionError("Unexpected model call $step")
                }
            }
            val link = attach(api, model, counted, JSONObject().put("scriptRoots", JSONArray(extraRoots.map { it.path })))
            try {
                val first = start(link, goal = "请找星巴克拿铁咖啡")
                fun completed(id: String) {
                    waitUntil("Catalog run reaches a terminal state") { decode(link.getRun(ref(id))).optString("state") in setOf("completed", "failed", "blocked") }
                    model.failure?.let { throw AssertionError("Script catalog fixture failed", it) }
                    val row = decode(link.getRun(ref(id)))
                    assertEquals(row.toString(), "completed", row.getString("state"))
                    val steps = row.getJSONArray("steps")
                    for (i in 0 until steps.length()) assertFalse(steps.getJSONObject(i).has("error"))
                }
                completed(first)
                completed(start(link, goal = "newfreshmarker"))
                val scoped = decode(link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", "work only")
                    .put("options", JSONObject().put("interaction", "script").put("scriptRoots", JSONArray()))), null)).getString("runId")
                completed(scoped)
                assertEquals(4, model.calls.get())
                assertEquals(C.ERROR_INVALID_REQUEST, link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", "denied")
                    .put("options", JSONObject().put("scriptRoots", JSONArray().put(root.path)))), null).getString(C.KEY_ERROR_CODE))
            } finally { detach(link); core.destroy(null) }
        } finally { org.autojs.autojs.util.WorkingDirectoryUtils.path = oldWork; root.deleteRecursively() }
    }

    private fun observation(request: JSONObject): JSONObject {
        val messages = request.getJSONArray("messages")
        return (messages.length() - 1 downTo 0).mapNotNull { index ->
            val content = messages.getJSONObject(index).getString("content")
            runCatching { JSONObject(content.substring(content.indexOf('{'))) }.getOrNull()
        }.first { it.optString("section") == "observation" }.getJSONObject("data")
    }

    private fun withExecutionRoot(action: (java.io.File) -> Unit) {
        val oldWork = org.autojs.autojs.util.WorkingDirectoryUtils.path
        val root = java.io.File(Environment.getExternalStorageDirectory(), "AgentP33-${System.nanoTime()}").apply { check(mkdirs()) }
        try { org.autojs.autojs.util.WorkingDirectoryUtils.path = root.path; action(root) }
        finally { org.autojs.autojs.util.WorkingDirectoryUtils.path = oldWork; root.deleteRecursively() }
    }

    @Test fun singleFileScriptAsksConfirmsAndReportsResultThroughTheRealHost() = registeredExecutionRoundTrip(false)
    @Test fun projectCleanupAsksConfirmsAndReportsResultThroughTheRealHost() = registeredExecutionRoundTrip(true)

    private fun registeredExecutionRoundTrip(project: Boolean) = withPlugin { api -> withExecutionRoot { root ->
        val downloads = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AgentP33-${System.nanoTime()}").apply { check(mkdirs()) }
        val oldApk = java.io.File(downloads, "old.apk").apply { writeText("fixture") }
        // Legacy emulated storage can reject utime even for a writable file. Verify its actual age.
        val aged = oldApk.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(40))
        val expectedRemoved = if (aged) 1 else 0
        assertEquals(aged, oldApk.lastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
        val keep = java.io.File(downloads, "keep.txt").apply { writeText("keep") }
        val fresh = java.io.File(downloads, "fresh.apk").apply { writeText("fresh") }
        val id: String
        val path: java.io.File
        if (project) {
            path = java.io.File(root, "cleanup").apply { check(mkdirs()) }
            for (name in listOf("project.json", "main.js")) context.assets.open("sample/agent/清理下载目录旧安装包/$name").use { input -> java.io.File(path, name).outputStream().use(input::copyTo) }
            id = "clean-download-installers"
        } else {
            path = java.io.File(root, "count.js").apply { writeText("""/**
                * @agent
                * @description Count the requested fixture items
                * @param {integer} count Item count
                * @risk normal
                * @confirm before-run
                * @timeout 10000
                */
                let c = ai.agent.context();
                c.parameters.count = 999;
                if (ai.agent.context().parameters.count !== engines.myEngine().execArgv.count) throw Error('context leaked mutation');
                for (let i = 0; i < 65; i++) console.log('line-' + i + ' token=fixture-secret');
                ai.agent.result({count: engines.myEngine().execArgv.count, runId: ai.agent.context().runId});
            """.trimIndent()) }
            id = "count"
        }
        val parameters = if (project) JSONObject().put("days", 30).put("directory", downloads.path).put("dryRun", false) else JSONObject().put("count", 7)
        val core = core()
        val model = ScriptedModel { step, request -> when (step) {
            0 -> tool("script_run", JSONObject().put("id", id).put("parameters", JSONObject()))
            1 -> {
                assertTrue(observation(request).toString().contains("SCRIPT_PARAMETERS_MISSING"))
                JSONObject().put("kind", "ask").put("ask", JSONObject().put("kind", "text").put("question", if (project) "保留最近多少天?" else "统计多少项?"))
            }
            2 -> tool("script_run", JSONObject().put("id", id).put("parameters", parameters))
            3 -> {
                val result = observation(request).getJSONObject("result")
                assertEquals(result.toString(), "success", result.getString("outcome"))
                assertTrue(result.getBoolean("resultReported")); assertTrue(result.getJSONArray("consoleTail").length() <= 40)
                assertFalse(result.toString().contains("fixture-secret"))
                if (project) assertEquals(expectedRemoved, result.getJSONObject("result").getInt("removed"))
                else assertEquals(7, result.getJSONObject("result").getInt("count"))
                done()
            }
            else -> throw AssertionError("Unexpected execution turn $step")
        } }
        val link = attach(api, model, broker(core)); val events = LinkedBlockingQueue<JSONObject>()
        try {
            val run = start(link, object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) } },
                if (project) "请帮我清理测试下载目录中的旧安装包" else "请调用登记脚本统计测试项数")
            for (expected in listOf("input", "confirmation", "done")) {
                var event: JSONObject
                do { event = events.poll(25, TimeUnit.SECONDS) ?: throw AssertionError("Missing $expected; model error: ${model.failure}") } while (event.getString("type") !in setOf("input", "confirmation", "done"))
                model.failure?.let { throw AssertionError("Registered script model fixture failed", it) }
                assertEquals(event.toString(), expected, event.getString("type"))
                if (expected != "done") {
                    if (project) assertTrue("Execution must wait for confirmation", oldApk.exists())
                    val reply = JSONObject().put("runId", run).put("requestId", event.getString("requestId"))
                    if (expected == "input") reply.put("value", if (project) "30" else "7")
                    else reply.put("allowed", true).put("scope", "once")
                    assertTrue(decode(link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, reply))).getBoolean("accepted"))
                }
            }
            val row = decode(link.getRun(ref(run)))
            assertEquals(row.toString(), "completed", row.getString("state"))
            val script = row.getJSONObject("result").getJSONObject("script")
            assertEquals(id, script.getString("id")); assertEquals(path.canonicalPath, script.getString("path"))
            assertTrue(script.getInt("executionId") >= 0)
            if (project) { assertEquals(expectedRemoved, script.getJSONObject("result").getInt("removed")); assertEquals(!aged, oldApk.exists()) }
            else assertEquals(run, script.getJSONObject("result").getString("runId"))
            assertTrue(keep.exists()); assertTrue(fresh.exists()); assertEquals(4, model.calls.get())
        } finally { detach(link); core.destroy(null); downloads.deleteRecursively() }
    } }

    @Test fun registeredScriptTimeoutStopsTheActualEngineAndReturnsFeedback() = stoppingExecutionRoundTrip(false)
    @Test fun cancellingAnAgentTaskStopsItsRunningRegisteredScript() = stoppingExecutionRoundTrip(true)

    private fun stoppingExecutionRoundTrip(cancel: Boolean) = withPlugin { api -> withExecutionRoot { root ->
        val ready = java.io.File(root, "ready.txt")
        java.io.File(root, "stall.js").writeText("""/**
            * @agent
            * @description Wait for cancellation
            * @risk readonly
            * @confirm never
            * @timeout ${if (cancel) 15000 else 5000}
            */
            files.write(${JSONObject.quote(ready.path)}, String(engines.myEngine().id));
            while (true) sleep(20);
        """.trimIndent())
        val core = core(); val model = ScriptedModel { step, request ->
            if (step == 0) tool("script_run", JSONObject().put("id", "stall").put("parameters", JSONObject()))
            else { assertFalse(cancel); assertTrue(observation(request).toString().contains("SCRIPT_TIMEOUT")); done() }
        }
        val stops = AtomicInteger(); val delegate = broker(core)
        val counted = object : IHostCapabilityBroker.Stub() {
            override fun getBrokerInfo() = delegate.brokerInfo
            override fun dispatch(request: Bundle, callback: IHostCapabilityCallback) {
                val call = JSONObject(request.getString(H.KEY_BRIDGE_REQUEST_JSON)!!)
                if (call.optString("module") == "engines" && call.optString("method") == "stop") stops.incrementAndGet()
                delegate.dispatch(request, callback)
            }
            override fun destroy(reason: Bundle?) = delegate.destroy(reason)
        }
        val link = attach(api, model, counted)
        try {
            val run = start(link, goal = if (cancel) "运行等待脚本, 然后停止任务" else "运行等待脚本并检查超时")
            waitUntil("Script acknowledges startup", 10000) { ready.exists() && ready.length() > 0 }
            val engineId = ready.readText().toInt()
            if (cancel) link.cancelRun(ref(run))
            waitUntil("Task terminates", 20000) { decode(link.getRun(ref(run))).optString("state") in setOf("completed", "cancelled", "failed", "blocked") }
            model.failure?.let { throw AssertionError("Timeout fixture failed", it) }
            assertEquals(if (cancel) "cancelled" else "completed", decode(link.getRun(ref(run))).getString("state"))
            waitUntil("Owned engine is stopped") { org.autojs.autojs.AutoJs.instance.scriptEngineService.engines.none { it.id == engineId } }
            assertEquals(1, stops.get())
            assertEquals(if (cancel) 1 else 2, model.calls.get())
        } finally { detach(link); core.destroy(null) }
    } }

    @Test fun realHostIdentityRunsDeviceInfoAndPreservesPrivateStepsAfterCallbackFailure() = withPlugin { api ->
        assertEquals(C.CONTRACT_VERSION, api.capabilities.getInt(AiAgentCapabilityKeys.CONTRACT_VERSION))
        val core = core()
        val model = ScriptedModel { step, _ -> if (step == 0) tool("device_info") else done() }
        val link = attach(api, model, broker(core))
        try {
            val id = start(link, object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle?) { throw DeadObjectException() } })
            waitUntil("Task completes despite lost observer") { decode(link.getRun(ref(id))).optString("state") == "completed" }
            val row = decode(link.getRun(ref(id)))
            assertEquals(2, row.getJSONArray("steps").length())
            assertFalse(row.getJSONArray("steps").getJSONObject(0).has("error"))
            assertEquals(1, row.getJSONObject("result").getInt("toolCalls"))
            assertEquals(2, model.calls.get())
            assertTrue(decode(link.listRuns(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject()))).getJSONArray("runs").length() > 0)
            assertEquals("default", decode(link.listPresets(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject()))).getJSONArray("presets").getJSONObject(0).getString("id"))
        } finally { detach(link); core.destroy(null) }
        assertEquals("detached", decode(link.status, C.KEY_STATUS_JSON).getString("state"))
        assertEquals(C.ERROR_LINK_DETACHED, link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", "late")), null).getString(C.KEY_ERROR_CODE))
    }

    @Test fun queueCancellationDetachAndInvalidRequestsAreBounded() = withPlugin { api ->
        val core = core()
        val model = ScriptedModel { _, _ -> null }
        val link = attach(api, model, broker(core))
        val events = CopyOnWriteArrayList<JSONObject>()
        val callback = object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) } }
        try {
            assertEquals(C.ERROR_INVALID_REQUEST, link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", "bad").put("options", JSONObject().put("confirm", "never"))), callback).getString(C.KEY_ERROR_CODE))
            val ids = (0..8).map { start(link, callback) }
            waitUntil("First model admitted") { model.calls.get() == 1 }
            assertEquals(C.ERROR_QUEUE_FULL, link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", "overflow")), callback).getString(C.KEY_ERROR_CODE))
            link.cancelRun(ref(ids.first()))
            waitUntil("Cancellation persisted") { decode(link.getRun(ref(ids.first()))).getString("state") == "cancelled" }
            detach(link)
            waitUntil("Remaining tasks blocked") { ids.drop(1).all { decode(link.getRun(ref(it))).getString("state") == "blocked" } }
            waitUntil("Exactly one terminal event per task") { events.count { it.optString("type") == "done" } == 9 }
            assertTrue(events.all { it.toString().toByteArray().size <= 32768 })
            ids.forEach { id -> val own = events.filter { it.getString("runId") == id }; assertEquals((1..own.size).toList(), own.map { it.getInt("sequence") }) }
            assertTrue(model.cancelled.get() > 0)
        } finally { detach(link); core.destroy(null) }
    }

    @Test fun inputAndCautiousActionConfirmationAreDeliveredAndAnswered() = withPlugin { api ->
        val core = core()
        val model = ScriptedModel { step, _ -> when (step) {
            0 -> JSONObject().put("kind", "ask").put("ask", JSONObject().put("kind", "choice").put("question", "Choose").put("choices", JSONArray().put("one").put("two")))
            1 -> tool("clipboard_set", JSONObject().put("text", "agent conformance"))
            else -> done()
        } }
        val link = attach(api, model, broker(core))
        val events = LinkedBlockingQueue<JSONObject>()
        try {
            val id = start(link, object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) } }, confirm = "cautious")
            for (expected in listOf("input", "confirmation", "done")) {
                var event: JSONObject
                do { event = events.poll(15, TimeUnit.SECONDS) ?: throw AssertionError("Missing $expected") } while (event.getString("type") !in setOf("input", "confirmation", "done"))
                assertEquals(expected, event.getString("type"))
                if (expected == "done") continue
                val reply = JSONObject().put("runId", id).put("requestId", event.getString("requestId"))
                if (expected == "input") {
                    assertEquals(C.ERROR_INVALID_REQUEST, link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, JSONObject(reply.toString()).put("value", "invalid"))).getString(C.KEY_ERROR_CODE))
                    reply.put("value", "one")
                } else { assertTrue(event.getBoolean("allowRunScope")); reply.put("allowed", true).put("scope", "once") }
                assertTrue(decode(link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, reply))).getBoolean("accepted"))
            }
            val row = decode(link.getRun(ref(id)))
            assertEquals("completed", row.getString("state"))
            assertFalse(row.getJSONArray("steps").getJSONObject(1).has("error"))
        } finally { detach(link); core.destroy(null) }
    }

    private fun withActionFixture(block: (org.autojs.autojs.engine.AgentActionSmokeActivity) -> Unit) {
        assumeTrue("Private AVD only: -e autojs.agent.actions true", InstrumentationRegistry.getArguments().getString("autojs.agent.actions").toBoolean())
        val service = "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        val previous = shell("settings get secure enabled_accessibility_services").trim().takeUnless { it == "null" }.orEmpty().split(':').filter { it.isNotBlank() }.toSet()
        val others = previous - service
        shell(if (others.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services ${others.joinToString(":")}")
        SystemClock.sleep(400)
        shell("settings put secure enabled_accessibility_services ${(others + service).joinToString(":")}")
        shell("settings put secure accessibility_enabled 1")
        assertTrue(org.autojs.autojs.core.accessibility.AccessibilityService.waitForOperational(10_000))
        val activity = instrumentation.startActivitySync(Intent(context, org.autojs.autojs.engine.AgentActionSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as org.autojs.autojs.engine.AgentActionSmokeActivity
        try { instrumentation.waitForIdleSync(); SystemClock.sleep(700); block(activity) }
        finally {
            instrumentation.runOnMainSync { activity.finish() }
            shell(if (previous.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services ${previous.joinToString(":")}")
        }
    }
    private fun actionEvents(link: IAiAgentLink, events: LinkedBlockingQueue<JSONObject>, id: String, onConfirm: (JSONObject) -> Unit = {}): List<JSONObject> {
        val received = ArrayList<JSONObject>()
        while (true) {
            val event = events.poll(30, TimeUnit.SECONDS) ?: throw AssertionError("Missing action event: ${decode(link.getRun(ref(id))).optString("state")}")
            received += event
            when (event.getString("type")) {
                "confirmation" -> {
                    onConfirm(event)
                    val reply = JSONObject().put("runId", id).put("requestId", event.getString("requestId")).put("allowed", true).put("scope", "once")
                    assertTrue(decode(link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, reply))).getBoolean("accepted"))
                }
                "done", "error" -> return received
            }
        }
    }
    @Test fun inspectedNodeActionsAppendPasswordsAndStaleConfirmationUseTheRealHost() = withPlugin { api -> withActionFixture { activity ->
        fun selector(desc: String) = JSONObject().put("selector", JSONObject().put("desc", desc))
        instrumentation.runOnMainSync { assertTrue("Fixture password node must be marked as password", activity.password.createAccessibilityNodeInfo().isPassword) }
        val secret = "P42_PRIVATE_INPUT"
        val core = core()
        val model = ScriptedModel { step, request ->
            if (step > 0 && step !in setOf(1, 9, 10)) assertTrue(observation(request).getJSONObject("result").getBoolean("ok"))
            when (step) {
                0 -> tool("ui_dump")
                1 -> {
                    val dump = observation(request).getJSONObject("result")
                    val row = dump.getString("text").lineSequence().firstOrNull { it.contains("desc=\"agent_click\"") }
                        ?: throw AssertionError("Fixture button missing: ${dump.getString("text").lineSequence().first()}")
                    tool("ui_click", JSONObject().put("nodeRef", row.substringBefore(' ')).put("snapshotId", dump.getString("snapshotId")))
                }
                2 -> { assertEquals(1, activity.clicks.get()); tool("ui_long_click", selector("agent_click")) }
                3 -> { assertEquals(1, activity.longClicks.get()); tool("ui_set_text", selector("agent_input").put("text", "hello")) }
                4 -> tool("ui_set_text", selector("agent_input").put("text", " world").put("append", true))
                5 -> { instrumentation.runOnMainSync { assertEquals("hello world", activity.input.text.toString()) }; tool("ui_scroll", selector("agent_scroll").put("direction", "forward").put("times", 2)) }
                6 -> tool("ui_scroll", selector("agent_scroll").put("direction", "backward"))
                7 -> tool("ui_set_text", selector("agent_password").put("text", secret))
                8 -> {
                    instrumentation.runOnMainSync { assertEquals(secret, activity.password.text.toString()) }
                    val messages = request.getJSONArray("messages")
                    val affected = (0 until messages.length()).filter { messages.getJSONObject(it).getString("content").contains(secret) }
                    assertTrue("Secret present in message indices $affected", affected.isEmpty()); tool("ui_dump")
                }
                9 -> {
                    assertTrue(observation(request).getJSONObject("result").has("sinceLastAction"))
                    instrumentation.runOnMainSync { activity.button.text = "Pay now" }
                    SystemClock.sleep(350)
                    tool("ui_click", selector("agent_click"))
                }
                else -> {
                    assertEquals("NODE_REF_STALE", observation(request).getString("error"))
                    assertEquals(1, activity.clicks.get()); done(orderStatus = "none")
                }
            }
        }
        val link = attach(api, model, broker(core), JSONObject().put("grantSummary", JSONObject().put("toolGroups", JSONArray().put("observe").put("act"))))
        val events = LinkedBlockingQueue<JSONObject>()
        try {
            val id = start(link, object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) } })
            val received = actionEvents(link, events, id) { event ->
                if (event.optString("tool") == "ui_click") {
                    assertFalse(event.getBoolean("allowRunScope"))
                    instrumentation.runOnMainSync { activity.button.text = "Changed after confirmation" }
                    SystemClock.sleep(350)
                }
            }
            model.failure?.let { throw AssertionError("Node action fixture", it) }
            assertEquals("completed", decode(link.getRun(ref(id))).getString("state"))
            assertEquals(11, model.calls.get()); assertFalse(received.joinToString().contains(secret))
        } finally { detach(link); core.destroy(null) }
    } }

    @Test fun changedChildLabelCannotAuthorizeThePreviouslyInspectedParent() = withPlugin { api -> withActionFixture { activity ->
        val core = core()
        val model = ScriptedModel { step, request -> when (step) {
            0 -> tool("ui_click", JSONObject().put("selector", JSONObject().put("desc", "agent_label")))
            1 -> {
                assertTrue(observation(request).getJSONObject("result").getBoolean("ok")); assertEquals(1, activity.parentClicks.get())
                instrumentation.runOnMainSync { activity.label.text = "Pay now" }; SystemClock.sleep(350)
                tool("ui_click", JSONObject().put("selector", JSONObject().put("desc", "agent_label")))
            }
            else -> {
                assertEquals("NODE_REF_STALE", observation(request).getString("error"))
                assertEquals(1, activity.parentClicks.get()); done(orderStatus = "none")
            }
        } }
        val link = attach(api, model, broker(core), JSONObject().put("grantSummary", JSONObject().put("toolGroups", JSONArray().put("observe").put("act"))))
        val events = LinkedBlockingQueue<JSONObject>(); var confirmations = 0
        try {
            val id = start(link, object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) } })
            actionEvents(link, events, id) { event ->
                assertFalse(event.getBoolean("allowRunScope"))
                if (++confirmations == 2) { instrumentation.runOnMainSync { activity.label.text = "Changed after confirmation" }; SystemClock.sleep(350) }
            }
            model.failure?.let { throw AssertionError("Ancestor action fixture", it) }
            assertEquals("completed", decode(link.getRun(ref(id))).getString("state")); assertEquals(3, model.calls.get()); assertEquals(2, confirmations)
        } finally { detach(link); core.destroy(null) }
    } }

    @Test fun globalClipboardAndGestureActionsRoundTripThroughTheRealHost() = withPlugin { api -> withActionFixture { activity ->
        var center = intArrayOf(0, 0)
        instrumentation.runOnMainSync { center = activity.center(activity.surface) }
        val cases = listOf(
            tool("clipboard_set", JSONObject().put("text", "P42 clipboard")), tool("clipboard_get"),
            tool("ui_click_xy", JSONObject().put("x", center[0]).put("y", center[1])),
            tool("ui_swipe", JSONObject().put("x1", center[0]).put("y1", center[1] + 30).put("x2", center[0]).put("y2", center[1] - 30).put("durationMs", 200)),
            tool("ui_gesture", JSONObject().put("durationMs", 200).put("points", JSONArray().put(JSONArray().put(center[0]).put(center[1] + 30)).put(JSONArray().put(center[0]).put(center[1] - 30)))),
            tool("app_launch", JSONObject().put("packageName", "com.android.settings")),
            tool("app_launch", JSONObject().put("appName", context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo("com.android.settings", 0)).toString())),
        ) + listOf("notifications", "quick_settings", "recents", "home", "back").map { tool("ui_press_key", JSONObject().put("key", it)) }
        for (decision in cases) {
            context.startActivity(Intent(context, org.autojs.autojs.engine.AgentActionSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            SystemClock.sleep(500)
            val core = core()
            val model = ScriptedModel { step, request ->
                if (step == 0) decision else {
                    val result = observation(request).getJSONObject("result")
                    assertTrue(decision.toString(), result.getBoolean("ok")); assertTrue(result.has("windowChanged"))
                    if (decision.getString("tool") == "clipboard_get") assertEquals("P42 clipboard", result.getString("actionResult"))
                    else { assertTrue(result.has("stability")); assertTrue(result.getJSONObject("stability").getLong("waitedMs") >= 500) }
                    done()
                }
            }
            val link = attach(api, model, broker(core), JSONObject().put("grantSummary", JSONObject().put("toolGroups", JSONArray().put("observe").put("act").put("gesture"))))
            val events = LinkedBlockingQueue<JSONObject>()
            try {
                val id = start(link, object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) } })
                val received = actionEvents(link, events, id)
                model.failure?.let { throw AssertionError("Global action fixture: ${decision.getString("tool")}", it) }
                assertEquals("completed", decode(link.getRun(ref(id))).getString("state"))
                if (decision.getString("tool") in setOf("ui_click_xy", "ui_swipe", "ui_gesture")) assertEquals(1, received.count { it.optString("type") == "confirmation" })
            } finally { detach(link); core.destroy(null) }
        }
        assertTrue("Real gesture callbacks", activity.touches.get() >= 3)
    } }

    @Test fun disabledGestureIsRejectedWithoutAnyCoordinateDispatch() = withPlugin { api ->
        val core = core(); val delegate = broker(core); val dispatches = AtomicInteger()
        val counted = object : IHostCapabilityBroker.Stub() {
            override fun getBrokerInfo() = delegate.brokerInfo
            override fun destroy(reason: Bundle?) = Unit
            override fun dispatch(request: Bundle, callback: IHostCapabilityCallback) {
                if (JSONObject(request.getString(H.KEY_BRIDGE_REQUEST_JSON)!!).optString("method") in setOf("swipe", "gesture")) dispatches.incrementAndGet()
                delegate.dispatch(request, callback)
            }
        }
        val model = ScriptedModel { step, request -> if (step == 0) tool("ui_click_xy", JSONObject().put("x", 1).put("y", 1)) else {
            assertTrue(request.toString().contains("TOOL_DISABLED")); done()
        } }
        val link = attach(api, model, counted)
        try {
            val id = start(link)
            waitUntil("Disabled gesture feedback") { decode(link.getRun(ref(id))).optString("state") in setOf("completed", "failed", "blocked") }
            model.failure?.let { throw AssertionError(it) }
            assertEquals("completed", decode(link.getRun(ref(id))).getString("state")); assertEquals(0, dispatches.get())
        } finally { detach(link); core.destroy(null) }
    }

    private fun shell(command: String): String = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command).use { fd ->
        ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
    @Test fun foregroundServiceExistsOnlyWhileATaskIsActive() = withPlugin { api ->
        val core = core(); val model = ScriptedModel { _, _ -> null }; val link = attach(api, model, broker(core))
        try {
            val id = start(link)
            waitUntil("Foreground preparation finished") { model.calls.get() == 1 }
            val services = shell("dumpsys activity services $pluginPackage")
            assertTrue(services.contains("AiAgentTaskForegroundService")); assertTrue(services.contains("isForeground=true"))
            link.cancelRun(ref(id))
            waitUntil("Task foreground service stopped") { !shell("dumpsys activity services $pluginPackage").contains("AiAgentTaskForegroundService") }
        } finally { detach(link); core.destroy(null) }
    }

    @Test fun realSettingsWifiToggleReadsBackThroughTheHostCapabilityBroker() = withPlugin { api ->
        assumeTrue("Private AVD only: -e autojs.agent.accessibility true", InstrumentationRegistry.getArguments().getString("autojs.agent.accessibility").toBoolean())
        assumeTrue("This AVD must expose Wi-Fi hardware", context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI))
        val service = "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        val enabled = shell("settings get secure enabled_accessibility_services").trim().takeUnless { it == "null" }.orEmpty().split(':').filter { it.isNotBlank() }.toSet() + service
        // Instrumentation restarts the host; API 37 keeps that binding in its crashed-service set.
        // Toggle only this fixture's service to ask the platform for a fresh real connection.
        val others = enabled - service
        shell(if (others.isEmpty()) "settings delete secure enabled_accessibility_services" else "settings put secure enabled_accessibility_services ${others.joinToString(":")}")
        SystemClock.sleep(400)
        shell("settings put secure enabled_accessibility_services ${enabled.joinToString(":")}")
        shell("settings put secure accessibility_enabled 1")
        assertTrue(org.autojs.autojs.core.accessibility.AccessibilityService.waitForOperational(10_000))
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val initial = wifi.isWifiEnabled
        context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(1500)
        if (Build.VERSION.SDK_INT >= 34) {
            val root = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).rootInActiveWindow
            val switches = root?.findAccessibilityNodeInfosByViewId("com.android.settings:id/switchWidget").orEmpty()
            // UiAutomation can inspect protected nodes; the real automation service cannot.
            // Never change isAccessibilityTool or use UiAutomation to execute the task.
            val protected = switches.any { it.isAccessibilityDataSensitive }
            @Suppress("DEPRECATION")
            switches.forEach { it.recycle() }
            @Suppress("DEPRECATION")
            root?.recycle()
            assumeTrue("System Wi-Fi switch is accessibilityDataSensitive; use an AVD with an accessible switch", !protected)
        }
        fun observation(request: JSONObject): JSONObject {
            val messages = request.getJSONArray("messages")
            for (i in messages.length() - 1 downTo 0) {
                val content = messages.getJSONObject(i).getString("content")
                val start = content.indexOf('{')
                if (start < 0) continue
                val record = runCatching { JSONObject(content.substring(start)) }.getOrNull() ?: continue
                if (record.optString("section") == "observation" && record.optJSONObject("data")?.has("result") == true)
                    return record.getJSONObject("data").getJSONObject("result")
            }
            throw AssertionError("Missing real observation")
        }
        fun switchLine(dump: JSONObject) = dump.getString("text").lineSequence().firstOrNull { it.contains("Switch") && it.contains("checkable") }
        val core = core()
        var phase = 0
        var readinessRetries = 0
        val model = ScriptedModel { _, request -> when (phase) {
            0 -> { phase++; tool("ui_dump") }
            1 -> {
                val dump = observation(request); val line = switchLine(dump)
                if (line == null && readinessRetries++ < 3) { SystemClock.sleep(100); tool("ui_dump") }
                else {
                    checkNotNull(line) { "Settings did not expose its Wi-Fi switch" }
                    assertEquals(initial, line.contains(" checked ")); phase++
                    tool("ui_click", JSONObject().put("nodeRef", Regex("^#n[1-9][0-9]*").find(line)!!.value).put("snapshotId", dump.getString("snapshotId")))
                }
            }
            // The frozen selector subset has no checked predicate. Wait for the widget,
            // then verify its checked state in a fresh dump and in WifiManager below.
            2 -> { phase++; tool("ui_wait_for", JSONObject().put("selector", JSONObject().put("classNameMatches", ".*Switch.*")).put("state", "appear").put("timeoutMs", 10000)) }
            3 -> { phase++; tool("ui_dump") }
            else -> {
                assertEquals(!initial, checkNotNull(switchLine(observation(request))).contains(" checked "))
                assertEquals(!initial, wifi.isWifiEnabled); done()
            }
        } }
        val link = attach(api, model, broker(core))
        val events = LinkedBlockingQueue<JSONObject>()
        try {
            val id = start(link, object : IAiAgentRunCallback.Stub() { override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) } }, "Toggle Wi-Fi in system settings and verify the switch")
            while (true) {
                val event = events.poll(20, TimeUnit.SECONDS) ?: throw AssertionError("Wi-Fi task did not complete")
                if (event.getString("type") == "confirmation") {
                    assertEquals("ui_click", event.getString("tool")); assertFalse(event.getBoolean("allowRunScope"))
                    decode(link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, JSONObject().put("runId", id).put("requestId", event.getString("requestId")).put("allowed", true).put("scope", "once"))))
                }
                if (event.getString("type") == "done") {
                    model.failure?.let { throw AssertionError("Scripted model fixture failed", it) }
                    assertEquals(event.toString(), "completed", event.getString("status")); break
                }
            }
            val row = decode(link.getRun(ref(id)))
            assertEquals(5 + readinessRetries, row.getJSONArray("steps").length()); assertEquals(4 + readinessRetries, row.getJSONObject("result").getInt("toolCalls"))
            repeat(4 + readinessRetries) { assertFalse(row.getJSONArray("steps").getJSONObject(it).has("error")) }
            assertEquals(5 + readinessRetries, model.calls.get()); assertEquals(!initial, wifi.isWifiEnabled)
            val result = row.getJSONObject("result")
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Wi-Fi fixture metrics: steps=${row.getJSONArray("steps").length()}, modelCalls=${model.calls.get()}, toolCalls=${result.getInt("toolCalls")}, durationMs=${result.optLong("durationMs")}, usage=${result.optJSONObject("usage")}\n") })
        } finally { detach(link); core.destroy(null); shell("svc wifi ${if (initial) "enable" else "disable"}") }
    }

    private fun lifecycleOptIn() {
        assumeTrue("Private unlocked AVD only: -e autojs.agent.lifecycle true",
            InstrumentationRegistry.getArguments().getString("autojs.agent.lifecycle").toBoolean())
        assertTrue("Do not run destructive lifecycle cases on real devices", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
    }

    /** Includes both Binder directions, real accessibility traversal and plugin context preparation. */
    @Test fun performanceBaselineForTwoHundredNodesAndOneTaskMemory() {
        assumeTrue("Opt in with -e autojs.agent.performance true", InstrumentationRegistry.getArguments().getString("autojs.agent.performance").toBoolean())
        assertTrue("Private AVD only", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        shell("am force-stop $pluginPackage")
        withPlugin { api -> withActionFixture { activity ->
            val cells = mutableListOf<android.widget.TextView>()
            instrumentation.runOnMainSync {
                val grid = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, 100, 0, 100)
                }
                repeat(20) { row ->
                    val line = android.widget.LinearLayout(activity)
                    repeat(10) { column ->
                        val cell = android.widget.TextView(activity).apply {
                            text = "Cell ${row * 10 + column}"; textSize = 9f
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }
                        cells += cell
                        line.addView(cell, android.widget.LinearLayout.LayoutParams(0, -1, 1f))
                    }
                    grid.addView(line, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
                }
                activity.setContentView(grid)
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(500)
            val pid = shell("pidof $pluginPackage:agent").trim().toInt()
            fun memory(): JSONObject {
                val status = shell("run-as $pluginPackage cat /proc/$pid/status")
                fun kb(name: String): Long = Regex("(?m)^$name:\\s+(\\d+)\\s+kB").find(status)?.groupValues?.get(1)?.toLong()
                    ?: throw AssertionError("Missing process memory metric $name")
                val info = shell("dumpsys meminfo $pid")
                val pss = Regex("TOTAL PSS:\\s+(\\d+)").find(info)?.groupValues?.get(1)?.toLong()
                    ?: Regex("(?m)^\\s*TOTAL\\s+(\\d+)").find(info)?.groupValues?.get(1)?.toLong()
                    ?: throw AssertionError("Missing PSS")
                return JSONObject().put("rssKb", kb("VmRSS")).put("hwmKb", kb("VmHWM")).put("pssKb", pss)
            }
            val baselineMemory = memory()
            val memorySamples = mutableListOf(baselineMemory)
            val roundTrips = mutableListOf<Double>()
            val hostTimes = java.util.Collections.synchronizedList(mutableListOf<Double>())
            val core = core(); val delegate = broker(core)
            val callbackFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
            val measuredBroker = object : IHostCapabilityBroker.Stub() {
                override fun getBrokerInfo() = delegate.brokerInfo
                override fun destroy(reason: Bundle?) = delegate.destroy(reason)
                override fun dispatch(request: Bundle, callback: IHostCapabilityCallback) {
                    val value = JSONObject(request.getString(H.KEY_BRIDGE_REQUEST_JSON)!!)
                    if (value.optString("module") != "accessibility" || value.optString("method") != "dump") { delegate.dispatch(request, callback); return }
                    val began = SystemClock.elapsedRealtimeNanos()
                    delegate.dispatch(request, object : IHostCapabilityCallback.Stub() {
                        override fun onResponse(response: Bundle) {
                            hostTimes.add((SystemClock.elapsedRealtimeNanos() - began) / 1e6)
                            try {
                                assertTrue(response.getBoolean(H.KEY_BRIDGE_RESPONSE_OK))
                                val result = JSONObject(response.getString(H.KEY_BRIDGE_RESPONSE_JSON)!!).getJSONObject("result")
                                assertEquals(200, result.getInt("nodeCount"))
                                assertEquals(context.packageName, result.getString("packageName"))
                            } catch (failure: Throwable) { callbackFailure.set(failure) }
                            callback.onResponse(response)
                        }
                    })
                }
            }
            var began = 0L
            val totalDumps = 35 // One cold sample, four further warmups, thirty measured steps.
            val model = ScriptedModel(zeroUsage = true) { step, request ->
                if (step > 0) {
                    roundTrips += (SystemClock.elapsedRealtimeNanos() - began) / 1e6
                    callbackFailure.get()?.let { throw AssertionError("Real host dump", it) }
                    val current = observation(request).getJSONObject("result")
                    assertEquals(200, current.getInt("nodeCount"))
                    assertTrue("Dump must observe this frame", current.getString("text").contains("Cell ${step - 1}_0"))
                }
                if (step % 5 == 0) memorySamples += memory()
                if (step == totalDumps) done() else {
                    // Pacing and fixture mutation are outside the measured interval. Keep the
                    // production query grant and let Android deliver content-change events.
                    instrumentation.runOnMainSync { cells.forEachIndexed { index, cell -> cell.text = "Cell ${step}_$index" } }
                    SystemClock.sleep(120)
                    began = SystemClock.elapsedRealtimeNanos()
                    tool("ui_dump", JSONObject().put("maxNodes", 200))
                }
            }
            val link = attach(api, model, measuredBroker)
            try {
                val id = decode(link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", "Read the fixture cells repeatedly and report completion")
                    .put("options", JSONObject().put("interaction", "script"))), null)).getString("runId")
                waitUntil("Performance task terminates", 180_000) { decode(link.getRun(ref(id))).optString("state") in setOf("completed", "failed", "blocked") }
                model.failure?.let { throw AssertionError("Performance fixture", it) }
                callbackFailure.get()?.let { throw AssertionError("Real host dump", it) }
                assertEquals("completed", decode(link.getRun(ref(id))).getString("state"))
                assertEquals(totalDumps, roundTrips.size); assertEquals(totalDumps, hostTimes.size)
                assertEquals(pid.toString(), shell("pidof $pluginPackage:agent").trim())
                memorySamples += memory()
                fun timings(values: List<Double>): JSONObject {
                    val samples = values.drop(5).sorted()
                    return JSONObject().put("coldMs", values.first()).put("samples", samples.size)
                        .put("meanMs", samples.average()).put("p50Ms", samples[14]).put("p95Ms", samples[28]).put("maxMs", samples.last())
                }
                val report = JSONObject().put("nodeCount", 200).put("roundTrip", timings(roundTrips)).put("hostDispatch", timings(hostTimes))
                    .put("memory", JSONObject().put("baseline", baselineMemory).put("final", memorySamples.last())
                        .put("samples", memorySamples.size).put("sampledPeakPssKb", memorySamples.maxOf { it.getLong("pssKb") })
                        .put("processPeakRssKb", memorySamples.maxOf { it.getLong("hwmKb") }))
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "P7_PERFORMANCE $report\n") })
                assertTrue("Warm p95 must stay below 300 ms: $report", report.getJSONObject("roundTrip").getDouble("p95Ms") < 300)
            } finally { detach(link); core.destroy(null) }
        } }
    }
    private fun awaitDurableRuns(ids: List<String>) {
        ids.forEach { id -> waitUntil("Task journal durable") {
            runCatching { JSONObject(shell("run-as $pluginPackage cat files/runs/$id.json")).getJSONObject("run").getString("runId") == id }.getOrDefault(false)
        } }
    }
    private fun assertIdle(link: IAiAgentLink) {
        waitUntil("No queued task or task foreground service") {
            val status = decode(link.status, C.KEY_STATUS_JSON)
            status.getInt("queuedCount") == 0 && status.isNull("runningRunId") &&
                !shell("dumpsys activity services $pluginPackage").contains("AiAgentTaskForegroundService")
        }
    }
    @Test fun cancellationDuringModelToolAndConfirmationFencesLateBinderReplies() = withPlugin { api ->
        for (phase in listOf("model", "tool", "confirmation")) {
            val core = core(); val delegate = broker(core)
            val toolCalls = AtomicInteger()
            val heldTool = java.util.concurrent.atomic.AtomicReference<Pair<String, IHostCapabilityCallback>?>()
            val capabilities = object : IHostCapabilityBroker.Stub() {
                override fun getBrokerInfo() = delegate.brokerInfo
                override fun destroy(reason: Bundle?) = delegate.destroy(reason)
                override fun dispatch(request: Bundle, callback: IHostCapabilityCallback) {
                    val call = JSONObject(request.getString(H.KEY_BRIDGE_REQUEST_JSON)!!)
                    when ("${call.getString("module")}.${call.getString("method")}") {
                        "device.info" -> { toolCalls.incrementAndGet(); heldTool.set(call.getString("id") to callback) }
                        "clipboard.setText" -> { toolCalls.incrementAndGet(); throw AssertionError("Cancelled confirmation must not write clipboard") }
                        else -> delegate.dispatch(request, callback)
                    }
                }
            }
            val model = ScriptedModel { step, _ -> if (step != 0 || phase == "model") null else
                if (phase == "tool") tool("device_info") else tool("clipboard_set", JSONObject().put("text", "cancelled fixture")) }
            val events = CopyOnWriteArrayList<JSONObject>()
            val link = attach(api, model, capabilities)
            try {
                val id = start(link, object : IAiAgentRunCallback.Stub() {
                    override fun onRunEvent(event: Bundle) { events.add(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) }
                }, confirm = "cautious")
                waitUntil("$phase reached") { when (phase) {
                    "tool" -> heldTool.get() != null
                    "confirmation" -> events.any { it.optString("type") == "confirmation" }
                    else -> model.calls.get() == 1
                } }
                link.cancelRun(ref(id)); link.cancelRun(ref(id))
                waitUntil("Cancellation settled once") { events.count { it.optString("type") == "done" } == 1 }
                val settled = decode(link.getRun(ref(id))).toString()
                assertEquals("cancelled", JSONObject(settled).getString("state"))
                assertFalse(JSONObject(settled).has("pending"))
                repeat(2) {
                    model.finishPending(tool("device_info"))
                    heldTool.get()?.let { (requestId, callback) -> callback.onResponse(Bundle().apply {
                        putBoolean(H.KEY_BRIDGE_RESPONSE_OK, true)
                        putString(H.KEY_BRIDGE_RESPONSE_JSON, JSONObject().put("id", requestId).put("ok", true).put("result", JSONObject().put("fixture", true)).toString())
                    }) }
                }
                if (phase == "confirmation") {
                    val pending = events.first { it.optString("type") == "confirmation" }
                    assertNotNull(link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, JSONObject().put("runId", id)
                        .put("requestId", pending.getString("requestId")).put("allowed", true).put("scope", "once"))).getString(C.KEY_ERROR_CODE))
                }
                assertIdle(link)
                SystemClock.sleep(150) // Give already-sent oneway late replies a chance to cross Binder.
                assertEquals(settled, decode(link.getRun(ref(id))).toString())
                assertEquals(1, events.count { it.optString("type") == "done" })
                assertEquals(1, model.calls.get()); assertEquals(if (phase == "tool") 1 else 0, toolCalls.get())
                if (phase == "model") waitUntil("Model cancellation reached broker") { model.cancelled.get() == 1 }
                model.failure?.let { throw AssertionError("Cancellation fixture", it) }
            } finally { detach(link); core.destroy(null) }
        }
    }

    @Test fun screenOffAndKeyguardReturnObservationsAndRequireAnExplicitAnswerAfterUnlock() = withPlugin { api ->
        lifecycleOptIn()
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        assertFalse("No credential changes or secure-device unlocks", keyguard.isDeviceSecure)
        val disabled = shell("locksettings get-disabled").trim().also { assertTrue(it in setOf("true", "false")) }
        try { withActionFixture {
            for (locked in listOf(false, true)) {
                shell("locksettings set-disabled false"); shell("input keyevent 223")
                waitUntil("Screen off") { !power.isInteractive }
                if (locked) { shell("input keyevent 224"); waitUntil("Screen on with keyguard") { power.isInteractive && keyguard.isKeyguardLocked } }
                val core = core()
                val model = ScriptedModel { step, request -> when (step) {
                    0 -> tool("ui_dump")
                    1 -> { assertTrue(observation(request).toString().contains("SCREEN_LOCKED")); tool("app_launch", JSONObject().put("packageName", "com.android.settings")) }
                    2 -> { assertTrue(observation(request).toString().contains("SCREEN_LOCKED")); JSONObject().put("kind", "ask").put("ask", JSONObject().put("kind", "confirm").put("question", "Unlock fixture, then continue?")) }
                    3 -> tool("ui_dump")
                    else -> { assertTrue(observation(request).getJSONObject("result").getInt("nodeCount") > 0); done() }
                } }
                val link = attach(api, model, broker(core))
                try {
                    val id = start(link)
                    waitUntil("Locked observation reaches an input request") { decode(link.getRun(ref(id))).getString("state") == "waiting_input" }
                    model.failure?.let { throw AssertionError("Locked fixture", it) }
                    val pending = decode(link.getRun(ref(id))).getJSONObject("pending")
                    assertEquals(3, model.calls.get())
                    shell("input keyevent 224"); shell("wm dismiss-keyguard")
                    waitUntil("Unlocked") { power.isInteractive && !keyguard.isKeyguardLocked }
                    assertEquals("waiting_input", decode(link.getRun(ref(id))).getString("state"))
                    decode(link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, JSONObject().put("runId", id).put("requestId", pending.getString("requestId")).put("value", true))))
                    waitUntil("Fresh observation completes after response") { decode(link.getRun(ref(id))).getString("state") in setOf("completed", "failed") }
                    model.failure?.let { throw AssertionError("Unlocked fixture", it) }
                    assertEquals("completed", decode(link.getRun(ref(id))).getString("state")); assertEquals(5, model.calls.get())
                } finally { detach(link); core.destroy(null) }
            }
        } } finally { shell("input keyevent 224"); shell("wm dismiss-keyguard"); shell("locksettings set-disabled $disabled") }
    }

    @Test fun userWindowSwitchCanBeAnsweredByLaunchingTheTargetAgain() = windowSwitchRoundTrip(true)
    @Test fun userWindowSwitchCanBeAnsweredByAskingBeforeContinuing() = windowSwitchRoundTrip(false)
    private fun windowSwitchRoundTrip(relaunch: Boolean) = withPlugin { api -> withActionFixture {
        val core = core()
        val model = ScriptedModel { step, request -> when (step) {
            0 -> tool("ui_dump")
            1 -> {
                assertTrue(observation(request).getJSONObject("result").getJSONObject("changes").getBoolean("baseline"))
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitUntil("User has switched foreground app") { org.autojs.autojs.core.accessibility.AccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
                tool("ui_dump")
            }
            2 -> {
                assertTrue(observation(request).getJSONObject("result").getJSONObject("changes").getBoolean("windowChanged"))
                if (relaunch) tool("app_launch", JSONObject().put("packageName", context.packageName))
                else JSONObject().put("kind", "ask").put("ask", JSONObject().put("kind", "confirm").put("question", "The foreground app changed. Continue?"))
            }
            3 -> if (relaunch) { assertTrue(observation(request).getJSONObject("result").getBoolean("ok")); tool("app_current") } else done()
            else -> { assertEquals(context.packageName, observation(request).getJSONObject("result").getString("packageName")); done() }
        } }
        val link = attach(api, model, broker(core))
        try {
            val id = start(link)
            if (!relaunch) {
                waitUntil("Model asks about changed window") { decode(link.getRun(ref(id))).getString("state") in setOf("waiting_input", "failed") }
                model.failure?.let { throw AssertionError("Window fixture", it) }
                val row = decode(link.getRun(ref(id))); assertEquals("waiting_input", row.getString("state"))
                assertEquals("com.android.settings", org.autojs.autojs.core.accessibility.AccessibilityService.instance?.rootInActiveWindow?.packageName?.toString())
                decode(link.respond(bundle(C.KEY_RUN_RESPONSE_JSON, JSONObject().put("runId", id).put("requestId", row.getJSONObject("pending").getString("requestId")).put("value", true))))
            }
            waitUntil("Window decision completes") { decode(link.getRun(ref(id))).getString("state") in setOf("completed", "failed") }
            model.failure?.let { throw AssertionError("Window fixture", it) }
            assertEquals("completed", decode(link.getRun(ref(id))).getString("state"))
            assertEquals(if (relaunch) 5 else 4, model.calls.get())
        } finally { detach(link); core.destroy(null) }
    } }

    /** External driver intentionally kills the host after the durable checkpoint, then runs verifyHostDeathCheckpoint. */
    @Test fun prepareHostDeathCheckpoint() = withPlugin { api ->
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.agent.hostDeathPhase") == "prepare")
        lifecycleOptIn()
        val core = core(); val model = ScriptedModel { _, _ -> null }; val link = attach(api, model, broker(core))
        try {
            val ids = (0..2).map { start(link) }
            waitUntil("Model in flight") { model.calls.get() == 1 }; awaitDurableRuns(ids)
            java.io.File(context.cacheDir, "agent-p7-host-death.json").writeText(JSONObject().put("hostPid", Process.myPid()).put("ids", JSONArray(ids)).toString())
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "P7_HOST_DEATH_READY\n") })
            // The driver kills this process; a timeout is an explicit setup failure.
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            fail("External driver did not kill the host")
        } finally { detach(link); core.destroy(null) }
    }
    @Test fun verifyHostDeathCheckpoint() = withPlugin { api ->
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.agent.hostDeathPhase") == "verify")
        lifecycleOptIn()
        val file = java.io.File(context.cacheDir, "agent-p7-host-death.json")
        val checkpoint = JSONObject(file.readText()); assertNotEquals(Process.myPid(), checkpoint.getInt("hostPid"))
        val rows = checkpoint.getJSONArray("ids"); val ids = (0 until rows.length()).map(rows::getString)
        val core = core(); val model = ScriptedModel { _, _ -> done() }; val link = attach(api, model, broker(core))
        try {
            waitUntil("Host death history is blocked") { ids.all { decode(link.getRun(ref(it))).getString("state") == "blocked" } }
            ids.forEach { id ->
                val row = decode(link.getRun(ref(id))); assertFalse(row.has("pending"))
                assertEquals("HOST_UNAVAILABLE", row.getJSONObject("result").getJSONObject("error").getString("code"))
            }
            assertIdle(link); assertEquals(0, model.calls.get())
            val fresh = start(link)
            waitUntil("Explicit new task succeeds after host restart") { decode(link.getRun(ref(fresh))).getString("state") == "completed" }
            assertEquals(1, model.calls.get())
            assertTrue(file.delete())
        } finally { detach(link); core.destroy(null) }
    }

    private fun <T> withDeathService(kind: String = "run", action: (IBinder) -> T): T {
        val ready = CountDownLatch(1); var binder: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) { binder = service; ready.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent(kind).setComponent(ComponentName(instrumentation.context, AgentCallbackDeathService::class.java)), connection, Context.BIND_AUTO_CREATE))
        try { assertTrue(ready.await(10, TimeUnit.SECONDS)); return action(checkNotNull(binder)) } finally { context.unbindService(connection) }
    }
    @Test fun callbackProcessDeathDoesNotCancelTheOwnedRun() = withPlugin { api -> withDeathService { observer ->
        val core = core(); val model = ScriptedModel { _, _ -> null }; val link = attach(api, model, broker(core))
        try {
            val id = start(link, IAiAgentRunCallback.Stub.asInterface(observer))
            waitUntil("Model started") { model.calls.get() == 1 }
            val died = CountDownLatch(1); observer.linkToDeath({ died.countDown() }, 0)
            val signal = Parcel.obtain()
            try { observer.transact(AgentCallbackDeathService.KILL_PROCESS, signal, null, IBinder.FLAG_ONEWAY) } finally { signal.recycle() }
            assertTrue(died.await(5, TimeUnit.SECONDS))
            model.finishPending()
            waitUntil("Observer death preserves the task") { decode(link.getRun(ref(id))).getString("state") == "completed" }
            assertEquals(1, decode(link.getRun(ref(id))).getJSONArray("steps").length())
        } finally { detach(link); core.destroy(null) }
    } }

    @Test fun pluginProcessRecreationFailsInterruptedHistoryAndClearsTheQueue() = withPlugin { api ->
        lifecycleOptIn()
        val core = core(); val model = ScriptedModel { _, _ -> null }; val link = attach(api, model, broker(core))
        val ids = (0..2).map { start(link) }
        waitUntil("Task active") { model.calls.get() == 1 }
        awaitDurableRuns(ids)
        val oldPid = shell("pidof $pluginPackage:agent").trim().also { assertTrue(it.matches(Regex("[0-9]+"))) }
        val died = CountDownLatch(1); link.asBinder().linkToDeath({ died.countDown() }, 0)
        // SIGKILL simulates abrupt process loss without marking the whole package force-stopped.
        shell("run-as $pluginPackage kill -9 $oldPid")
        assertTrue("Real plugin Binder death", died.await(10, TimeUnit.SECONDS))
        core.destroy(null)
        withPlugin { nextApi ->
            val nextCore = core(); val nextModel = ScriptedModel { _, _ -> done() }; val nextLink = attach(nextApi, nextModel, broker(nextCore))
            try {
                waitUntil("Interrupted tasks recovered") { ids.all { runCatching { decode(nextLink.getRun(ref(it))).getString("state") == "failed" }.getOrDefault(false) } }
                assertNotEquals(oldPid, shell("pidof $pluginPackage:agent").trim())
                ids.forEach { id ->
                    val row = decode(nextLink.getRun(ref(id)))
                    assertEquals("process-died", row.getJSONObject("result").getString("error")); assertFalse(row.has("pending"))
                }
                assertIdle(nextLink)
                assertEquals(0, nextModel.calls.get())
                val fresh = start(nextLink)
                waitUntil("Only an explicit new task runs") { decode(nextLink.getRun(ref(fresh))).getString("state") == "completed" }
                assertEquals(1, nextModel.calls.get())
            } finally { detach(nextLink); nextCore.destroy(null) }
        }
    }
    @Test fun hostLinkCallbackDeathBlocksAllTasksAndNeverReplaysThem() = withPlugin { api -> withDeathService("link") { observer ->
        val core = core(); val model = ScriptedModel { _, _ -> null }
        val link = api.attach(bundle(C.KEY_LINK_CONFIG_JSON, JSONObject()), model, broker(core), IAiAgentLinkCallback.Stub.asInterface(observer))
        try {
            val ids = (0..2).map { start(link) }
            waitUntil("First task active") { model.calls.get() == 1 }
            val signal = Parcel.obtain()
            try { observer.transact(AgentCallbackDeathService.KILL_PROCESS, signal, null, IBinder.FLAG_ONEWAY) } finally { signal.recycle() }
            waitUntil("Host link loss blocks every admitted task") { ids.all { decode(link.getRun(ref(it))).getString("state") == "blocked" } }
            assertEquals("host_unavailable", decode(link.status, C.KEY_STATUS_JSON).getString("state"))
            assertEquals(1, model.calls.get())
            assertEquals(C.ERROR_HOST_UNAVAILABLE, link.startRun(bundle(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", "late")), null).getString(C.KEY_ERROR_CODE))
        } finally { core.destroy(null) }
    } }
}
