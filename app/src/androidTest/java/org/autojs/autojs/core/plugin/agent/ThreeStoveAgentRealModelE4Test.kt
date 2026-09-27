package org.autojs.autojs.core.plugin.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLink
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLinkCallback
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelBroker
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentModelCallback
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentPlugin
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentRunCallback
import org.autojs.plugin.host.capability.api.HostCapabilityContract as H
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Explicitly opted-in real-model/device evidence collection, separate from conformance tests.
 * The real Agent owns every model decision, tool dispatch, confirmation and completion rule.
 * A successful instrumentation result means evidence was collected, not that the goal passed.
 * Private evidence can contain screen/user data; never print it to logcat or test output.
 */
class ThreeStoveAgentRealModelE4Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val control = File("/sdcard/autojs6-agent-e4")
    private val pluginPackage = "io.github.supermonster003.autojs6.plugin.three.stove.agent"
    private val terminalStates = setOf("completed", "partial", "failed", "blocked", "cancelled")

    /** Read-only shape evidence: distinguish hidden branches from genuinely oversized visible trees. */
    @Suppress("DEPRECATION")
    @Test fun accessibilityTreeShape() {
        optIn()
        instrumentation.runOnMainSync { AutoJs.instance.infoProvider }
        rebindConfiguredAccessibility()
        val root = checkNotNull(org.autojs.autojs.core.accessibility.AccessibilityService.instance?.rootInActiveWindow)
        var visited = 0
        var maxDepth = 0
        var visiblePathMaxDepth = 0
        var visiblePathNodes = 0
        var branchesBelow32 = 0
        var visibleBranchesBelow32 = 0
        var cut = false
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int, parentVisible: Boolean) {
            if (visited++ >= 4000) { cut = true; return }
            maxDepth = maxOf(maxDepth, depth)
            val visible = parentVisible && node.isVisibleToUser
            if (visible) { visiblePathNodes++; visiblePathMaxDepth = maxOf(visiblePathMaxDepth, depth) }
            if (depth == 32 && node.childCount > 0) {
                branchesBelow32++
                if (visible) visibleBranchesBelow32++
            }
            if (depth >= 64) { if (node.childCount > 0) cut = true; return }
            for (index in 0 until node.childCount) {
                if (visited >= 4000) { cut = true; break }
                val child = node.getChild(index) ?: continue
                try { walk(child, depth + 1, visible) } finally { child.recycle() }
            }
        }
        try { walk(root, 0, true) } finally { root.recycle() }
        write(File(context.filesDir, "agent-e4/tree-shape").apply { mkdirs() }, "shape-${System.currentTimeMillis()}.json",
            JSONObject().put("visited", visited).put("maxDepth", maxDepth).put("visiblePathNodes", visiblePathNodes)
                .put("visiblePathMaxDepth", visiblePathMaxDepth).put("branchesBelow32", branchesBelow32)
                .put("visibleBranchesBelow32", visibleBranchesBelow32).put("truncated", cut))
    }

    @Test fun publicCatalog() {
        optIn()
        val directory = File(context.filesDir, "agent-e4/catalog").apply { mkdirs() }
        val events = LinkedBlockingQueue<JSONObject>()
        ThreeStoveAgentModelBroker(context, Process.myUid(), ThreeStoveAgentGrant.default()).use { broker ->
            broker.listTargets(envelope(C.KEY_MODEL_REQUEST_JSON, JSONObject().put("requestId", "e4-catalog")),
                object : IThreeStoveAgentModelCallback.Stub() {
                    override fun onEvent(event: Bundle) { events.offer(JSONObject(event.getString(C.KEY_MODEL_EVENT_JSON)!!)) }
                })
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (SystemClock.elapsedRealtime() < deadline) {
                val event = events.poll(1, TimeUnit.SECONDS) ?: continue
                if (event.optString("type") in setOf("completed", "failed", "cancelled")) {
                    write(directory, "catalog.json", event)
                    assertTrue("Catalog failed; see private evidence", event.optString("type") == "completed")
                    return
                }
            }
            throw AssertionError("Catalog deadline")
        }
    }

    /** Two bounded model-only observations. Collection success is not a model/task success. */
    @Test fun modelFormatProbe() {
        optIn()
        val arguments = InstrumentationRegistry.getArguments()
        val target = arguments.getString("autojs.agent.e4.probeTarget").orEmpty()
        val caseId = arguments.getString("autojs.agent.e4.probeCase").orEmpty()
        require(target.startsWith("profile:") && target.length in 9..256 && target.none(Char::isISOControl))
        require(caseId.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        val directory = File(context.filesDir, "agent-e4/$caseId")
        require(!directory.exists()) { "Use a fresh probe case ID; never overwrite prior evidence" }
        check(directory.mkdirs())
        val prompt = "Reply with exactly this JSON object and no Markdown: {\"answer\":\"ok\"}"
        val expected = "{\"answer\":\"ok\"}"
        val maximumOutputTokens = 2048 // Matches the Agent RunOptions default, without repair calls.
        val roundTimeoutMs = 60_000L
        val results = JSONArray()
        val report = JSONObject().put("caseId", caseId).put("targetId", target)
            .put("prompt", prompt).put("expected", JSONObject(expected)).put("stream", true)
            .put("maximumOutputTokens", maximumOutputTokens).put("roundTimeoutMs", roundTimeoutMs)
            .put("maximumGenerations", 2).put("tools", JSONArray()).put("results", results)
            .put("collectionComplete", false)
        write(directory, "probe.json", report)
        var harnessFailed = false
        // Keep the same target, messages, output ceiling and stream mode. Only schema admission changes.
        for (structured in listOf(true, false)) {
            val mode = if (structured) "structured" else "plain"
            val requestId = "e4-probe-$mode-${UUID.randomUUID()}"
            val events = LinkedBlockingQueue<JSONObject>()
            val began = SystemClock.elapsedRealtime()
            val row = JSONObject().put("mode", mode).put("structuredJson", structured)
                .put("requestId", requestId).put("startedAt", System.currentTimeMillis())
                .put("terminal", "harness_timeout").put("expectedMatched", false)
            try {
                // This creates no Agent link/capability broker, reads no credentials and performs no UI actions.
                ThreeStoveAgentModelBroker(context, Process.myUid(), ThreeStoveAgentGrant.default()).use { broker ->
                    val request = JSONObject().put("requestId", requestId).put("targetId", target)
                        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                        .put("structuredJson", structured).put("stream", true)
                        .put("maximumOutputTokens", maximumOutputTokens).put("timeoutMs", roundTimeoutMs)
                    if (structured) request.put("responseSchema", JSONObject()
                        .put("type", "object").put("additionalProperties", false)
                        .put("properties", JSONObject().put("answer", JSONObject().put("type", "string")))
                        .put("required", JSONArray().put("answer")))
                    try {
                        broker.generate(envelope(C.KEY_MODEL_REQUEST_JSON, request), object : IThreeStoveAgentModelCallback.Stub() {
                            override fun onEvent(event: Bundle) {
                                // Ignore streaming text here; the bounded, verified terminal text is authoritative.
                                val value = runCatching { readModelProbeEvent(event) }
                                    .getOrElse { JSONObject().put("type", "harness_invalid_event") }
                                if (value.optString("type") != "chunk") events.offer(value)
                            }
                        })
                        while (true) {
                            val remaining = roundTimeoutMs - (SystemClock.elapsedRealtime() - began)
                            if (remaining <= 0L) break
                            val event = events.poll(remaining, TimeUnit.MILLISECONDS) ?: break
                            val type = event.optString("type")
                            check(type != "harness_invalid_event" && event.optString("requestId") == requestId)
                            if (type == "usage") row.put("usage", event.getJSONObject("usage"))
                            if (type !in setOf("completed", "failed", "cancelled")) continue
                            row.put("terminal", type)
                            if (type == "completed") {
                                val text = event.getString("text")
                                check(text.toByteArray(Charsets.UTF_8).size <= C.MAX_MODEL_OUTPUT_BYTES)
                                // The fixed prompt requests exact bytes. Do not accept JSONObject's lenient syntax.
                                val matched = text.trim() == expected
                                row.put("responseText", text).put("responseBytes", text.toByteArray(Charsets.UTF_8).size)
                                    .put("finishReason", event.getInt("finishReason"))
                                    .put("expectedMatched", matched).put("exactTextMatched", text.trim() == expected)
                            } else {
                                for (key in listOf("code", "reason")) event.optString(key).takeIf {
                                    it.matches(Regex("[A-Z][A-Z0-9_]{0,63}"))
                                }?.let { row.put(key, it) }
                            }
                            break
                        }
                    } finally {
                        runCatching { broker.cancel(envelope(C.KEY_MODEL_REF_JSON, JSONObject().put("requestId", requestId))) }
                    }
                }
            } catch (_: Exception) {
                row.put("terminal", "harness_error")
                harnessFailed = true
            }
            if (row.getString("terminal") == "harness_timeout") harnessFailed = true
            row.put("elapsedMs", SystemClock.elapsedRealtime() - began)
            results.put(row)
            write(directory, "probe.json", report)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("probeMode", mode); putString("probeTerminal", row.getString("terminal"))
                putBoolean("probeExpectedMatched", row.getBoolean("expectedMatched"))
                putInt("probeResponseBytes", row.optInt("responseBytes", 0))
                putLong("probeElapsedMs", row.getLong("elapsedMs"))
            })
        }
        report.put("collectionComplete", !harnessFailed)
        write(directory, "probe.json", report)
        assertTrue("Probe harness failed; see private evidence", !harnessFailed)
    }

    /** Broker events above the inline limit use a private-file FD, even for a bounded token request. */
    @Suppress("DEPRECATION")
    private fun readModelProbeEvent(event: Bundle): JSONObject {
        val descriptor = event.getParcelable<ParcelFileDescriptor>(C.KEY_PAYLOAD_FD)
        try {
            check(event.getInt(C.KEY_CONTRACT_VERSION) == C.CONTRACT_VERSION)
            val inline = event.getString(C.KEY_MODEL_EVENT_JSON)
            check((inline == null) != (descriptor == null))
            val text = if (inline != null) {
                check(inline.toByteArray(Charsets.UTF_8).size <= C.MAX_EVENT_JSON_BYTES)
                inline
            } else {
                // The production broker sends a regular private file; reject unsupported/unknown-size payloads.
                check(checkNotNull(descriptor).statSize in 0..C.MAX_MODEL_REQUEST_PAYLOAD_BYTES.toLong())
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= C.MAX_MODEL_REQUEST_PAYLOAD_BYTES)
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            }
            return JSONObject(text)
        } finally {
            descriptor?.close()
        }
    }

    @Test fun runRealGoal() {
        optIn()
        val configFile = File(control, "config.json")
        val config = JSONObject(configFile.readText())
        val modelPath = config.optString("modelPath", "auto")
        require(modelPath in setOf("auto", "json"))
        val caseId = config.getString("caseId")
        require(caseId.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        val directory = File(context.filesDir, "agent-e4/$caseId")
        require(!directory.exists()) { "Use a fresh case ID; never overwrite a prior run" }
        check(directory.mkdirs())
        check(configFile.delete())
        val autoPackages = config.optJSONArray("autoConfirmPackages")?.let { values ->
            (0 until values.length()).map { values.getString(it) }.toSet()
        }.orEmpty()
        require(autoPackages.all { it in setOf("com.android.settings", "org.fossify.math", "cz.hipercalc.pro") })
        // A shopping run has no automatic approvals, including when a model proposes app_launch.
        val goal = config.getString("goal")
        val events = LinkedBlockingQueue<JSONObject>()
        val started = SystemClock.elapsedRealtime()
        var link: IThreeStoveAgentLink? = null
        var brokers: ThreeStoveAgentLinkBrokers? = null
        var runId: String? = null
        var bound = false
        val connected = CountDownLatch(1)
        var api: IThreeStoveAgentPlugin? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                api = IThreeStoveAgentPlugin.Stub.asInterface(binder); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        fun record(event: JSONObject) {
            File(directory, "events.jsonl").appendText(JSONObject()
                .put("elapsedMs", SystemClock.elapsedRealtime() - started).put("event", event).toString() + "\n")
        }
        fun snapshot(): JSONObject = decode(checkNotNull(link).getRun(reference(checkNotNull(runId)))).also {
            write(directory, "snapshot.json", it)
        }
        fun reply(value: JSONObject) {
            require(value.getString("runId") == runId)
            val response = checkNotNull(link).respond(envelope(C.KEY_RUN_RESPONSE_JSON, value))
            val error = response.getString(C.KEY_ERROR_CODE)
            // A deadline may close a request after the operator reads it. Keep the terminal evidence.
            if (error in setOf(C.ERROR_RUN_NOT_FOUND, C.ERROR_RUN_NOT_INTERACTIVE, C.ERROR_INVALID_REQUEST)) {
                record(JSONObject().put("type", "harness_reply").put("requestId", value.getString("requestId"))
                    .put("accepted", false).put("error", error))
                return
            }
            val answer = decode(response)
            record(JSONObject().put("type", "harness_reply").put("requestId", value.getString("requestId"))
                .put("accepted", answer.getBoolean("accepted")).apply {
                    if (value.has("allowed")) put("allowed", value.getBoolean("allowed"))
                })
        }
        try {
            // Initialize the activity observer before any target application opens.
            instrumentation.runOnMainSync { AutoJs.instance.infoProvider }
            rebindConfiguredAccessibility()
            bound = context.bindService(Intent().setComponent(ComponentName(pluginPackage, "$pluginPackage.ThreeStoveAgentPluginService")),
                connection, Context.BIND_AUTO_CREATE)
            check(bound && connected.await(15, TimeUnit.SECONDS)) { "Agent bind failed" }
            val uid = context.packageManager.getApplicationInfo(pluginPackage, 0).uid
            brokers = ThreeStoveAgentLinkBrokers(context, "e4-${UUID.randomUUID()}", uid)
            val groups = config.optJSONArray("toolGroups") ?: JSONArray(listOf("observe", "act", "user"))
            val options = JSONObject().put("target", config.getString("target")).put("interaction", "script")
                .put("confirm", "cautious").put("memory", false).put("tools", groups).put("locale", "zh-CN")
            config.optJSONObject("budget")?.let { options.put("budget", it) }
            config.optString("context").takeIf { it.isNotBlank() }?.let { options.put("context", it) }
            link = checkNotNull(api).attach(envelope(C.KEY_LINK_CONFIG_JSON, JSONObject().put("locale", "zh-CN")
                .put("scriptRoots", JSONArray()).put("grantSummary", JSONObject().put("toolGroups", groups))),
                traceModel(brokers.modelBroker, directory, modelPath), traceCapabilities(brokers.capabilityBroker, directory), object : IThreeStoveAgentLinkCallback.Stub() {
                    override fun onStatus(status: Bundle?) = Unit
                    override fun onEvent(event: Bundle?) = Unit
                })
            runId = decode(link.startRun(envelope(C.KEY_RUN_REQUEST_JSON, JSONObject().put("goal", goal).put("options", options)),
                object : IThreeStoveAgentRunCallback.Stub() {
                    override fun onRunEvent(event: Bundle) { events.offer(JSONObject(event.getString(C.KEY_RUN_EVENT_JSON)!!)) }
                })).getString("runId")
            write(directory, "started.json", JSONObject().put("caseId", caseId).put("runId", runId)
                .put("startedAt", System.currentTimeMillis()).put("target", config.getString("target")).put("modelPath", modelPath))
            val deadline = started + (config.optJSONObject("budget")?.optLong("maxDurationMs", 600_000) ?: 600_000) + 30_000
            var lastSnapshot = 0L
            while (SystemClock.elapsedRealtime() < deadline) {
                val event = events.poll(250, TimeUnit.MILLISECONDS)
                if (event != null) {
                    record(event)
                    if (event.optString("type") in setOf("confirmation", "input")) {
                        write(directory, "pending.json", event)
                        snapshot()
                        if (event.optString("type") == "confirmation" && event.optString("risk") == "normal" && autoPackages.isNotEmpty()) {
                            val packageName = if (event.optString("tool") == "app_launch")
                                event.getJSONObject("arguments").optString("packageName") else foregroundPackage()
                            if (packageName in autoPackages) reply(JSONObject().put("runId", runId)
                                .put("requestId", event.getString("requestId")).put("allowed", true).put("scope", "once"))
                        }
                    }
                }
                val responseFile = File(control, "reply-$caseId.json")
                if (responseFile.exists()) {
                    val response = JSONObject(responseFile.readText())
                    check(responseFile.delete())
                    reply(response)
                }
                val cancelFile = File(control, "cancel-$caseId")
                if (cancelFile.exists()) { check(cancelFile.delete()); link.cancelRun(reference(runId)) }
                if (SystemClock.elapsedRealtime() - lastSnapshot >= 2_000 || event?.optString("type") == "done") {
                    val current = snapshot()
                    lastSnapshot = SystemClock.elapsedRealtime()
                    if (current.optString("state") in terminalStates && current.has("result")) {
                        write(directory, "final.json", current)
                        write(directory, "harness.json", JSONObject().put("caseId", caseId)
                            .put("elapsedMs", SystemClock.elapsedRealtime() - started).put("state", current.getString("state")))
                        return
                    }
                }
            }
            throw AssertionError("E4 harness deadline; see private evidence")
        } catch (failure: Throwable) {
            write(directory, "harness.json", JSONObject().put("caseId", caseId).put("runId", runId)
                .put("elapsedMs", SystemClock.elapsedRealtime() - started).put("error", failure.javaClass.simpleName))
            throw failure
        } finally {
            runCatching { if (runId != null) link?.cancelRun(reference(runId)) }
            val reason = envelope(H.KEY_REASON_JSON, JSONObject().put("reason", "e4-finished"))
            runCatching { link?.detach(reason) }
            brokers?.destroy(reason)
            if (bound) context.unbindService(connection)
        }
    }

    private fun optIn() = assumeTrue("Real models/devices: explicitly opt in with -e autojs.agent.e4 true",
        InstrumentationRegistry.getArguments().getString("autojs.agent.e4").toBoolean())
    private fun envelope(key: String, value: JSONObject) = Bundle().apply {
        putInt(C.KEY_CONTRACT_VERSION, C.CONTRACT_VERSION); putString(key, value.toString())
    }
    private fun decode(value: Bundle): JSONObject {
        check(value.getString(C.KEY_ERROR_CODE) == null) { value.getString(C.KEY_ERROR_CODE).orEmpty() }
        return JSONObject(value.getString(C.KEY_RUN_RESPONSE_JSON)!!)
    }
    private fun reference(id: String) = envelope(C.KEY_RUN_REF_JSON, JSONObject().put("runId", id))
    private fun rebindConfiguredAccessibility() {
        val key = android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val service = "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
        fun configured() = android.provider.Settings.Secure.getString(context.contentResolver, key)
            .orEmpty().split(':').filter { it.isNotBlank() }.toSet()
        // Instrumentation restarts the host process; some OEMs mark its existing service crashed.
        // Rebind only a service already enabled by the operator, preserving every other component.
        check(service in configured()) { "Enable host accessibility before starting E4" }
        if (org.autojs.autojs.core.accessibility.AccessibilityService.instance != null) return
        fun shell(command: String) {
            instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand(command).use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                }
        }
        fun setServices(values: Set<String>) {
            // UiAutomation executes argv without a shell: quotes would become part of the setting.
            check(values.all { it.matches(Regex("[\\p{L}\\p{M}\\p{N}_.$]+/[\\p{L}\\p{M}\\p{N}_.$]+")) })
            shell(if (values.isEmpty()) "settings delete secure $key"
                else "settings put secure $key ${values.joinToString(":")}")
        }
        setServices(configured() - service)
        SystemClock.sleep(400)
        setServices(configured() + service)
        shell("settings put secure accessibility_enabled 1")
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (org.autojs.autojs.core.accessibility.AccessibilityService.instance == null && SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(100)
        check(org.autojs.autojs.core.accessibility.AccessibilityService.instance != null) { "Host accessibility did not rebind" }
    }
    @Suppress("DEPRECATION")
    private fun foregroundPackage(): String? {
        val root = org.autojs.autojs.core.accessibility.AccessibilityService.instance?.rootInActiveWindow ?: return null
        return try { root.packageName?.toString() } finally { root.recycle() }
    }
    /** Observe only model replies, inside private opt-in evidence; credentials remain in the Provider. */
    private fun traceModel(delegate: IThreeStoveAgentModelBroker, directory: File, modelPath: String) = object : IThreeStoveAgentModelBroker.Stub() {
        override fun submitToolResults(request: Bundle?) {
            synchronized(lock) { File(directory, "model-continuations.jsonl").appendText(
                JSONObject().put("elapsedRealtime", SystemClock.elapsedRealtime()).toString() + "\n") }
            delegate.submitToolResults(request)
        }
        private val lock = Any()
        override fun getBrokerInfo() = Bundle(delegate.brokerInfo).apply {
            // Opt-in instrumentation only: emulate pre-extension negotiation with the SAME target,
            // host, plugin and grants. Never alter generation payloads or production preferences.
            if (modelPath == "json") getString(C.KEY_MODEL_BROKER_INFO_JSON)?.let { json ->
                putString(C.KEY_MODEL_BROKER_INFO_JSON, JSONObject(json).apply {
                    remove(C.MODEL_INFO_TOOL_CALLING_VERSION)
                }.toString())
            }
        }
        override fun listTargets(request: Bundle?, callback: IThreeStoveAgentModelCallback?) = delegate.listTargets(request, callback)
        override fun generate(request: Bundle?, callback: IThreeStoveAgentModelCallback?) {
            delegate.generate(request, object : IThreeStoveAgentModelCallback.Stub() {
                override fun onEvent(event: Bundle) {
                    event.getString(C.KEY_MODEL_EVENT_JSON)?.let { json ->
                        if (JSONObject(json).optString("type") in setOf("started", "tool_calls", "completed", "failed", "cancelled", "usage")) {
                            synchronized(lock) { File(directory, "model-events.jsonl").appendText(json + "\n") }
                        }
                    }
                    callback?.onEvent(event)
                }
            })
        }
        override fun cancel(reference: Bundle?) = delegate.cancel(reference)
        override fun destroy(reason: Bundle?) = delegate.destroy(reason)
    }
    /** Retain precise bridge failures for diagnosis without changing the response or consuming FDs. */
    private fun traceCapabilities(delegate: IHostCapabilityBroker, directory: File) = object : IHostCapabilityBroker.Stub() {
        private val lock = Any()
        override fun getBrokerInfo() = delegate.brokerInfo
        override fun dispatch(request: Bundle?, callback: IHostCapabilityCallback?) {
            val observeQuery = runCatching {
                val call = JSONObject(request?.getString(H.KEY_BRIDGE_REQUEST_JSON).orEmpty())
                call.optString("module") == "accessibility" && call.optString("method") in setOf("findOne", "findAll")
            }.getOrDefault(false)
            delegate.dispatch(request, object : IHostCapabilityCallback.Stub() {
                override fun onResponse(response: Bundle) {
                    response.getString(H.KEY_BRIDGE_RESPONSE_JSON)?.let { json ->
                        if (JSONObject(json).has("error")) {
                            synchronized(lock) { File(directory, "capability-errors.jsonl").appendText(json + "\n") }
                        } else if (observeQuery) {
                            synchronized(lock) { File(directory, "node-queries.jsonl").appendText(json + "\n") }
                        }
                    }
                    callback?.onResponse(response)
                }
            })
        }
        override fun destroy(reason: Bundle?) = delegate.destroy(reason)
    }
    private fun write(directory: File, name: String, value: JSONObject) {
        val temporary = File(directory, "$name.tmp")
        temporary.writeText(value.toString(2))
        check(temporary.renameTo(File(directory, name)))
    }
}
