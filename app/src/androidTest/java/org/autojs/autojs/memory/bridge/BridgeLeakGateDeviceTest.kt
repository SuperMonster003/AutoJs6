package org.autojs.autojs.memory.bridge

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.NodeJsRuntimePluginHost
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.memory.GateHeapPathAnalyzer
import org.autojs.autojs.memory.GateMetrics
import org.autojs.autojs.memory.scenarios.GateMode
import org.autojs.autojs.memory.scenarios.GateNativeProbe
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.memory.BridgeExecutionObserver
import org.autojs.autojs.runtime.memory.StaticRegistries
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bridge leak gate (docs/memory-phase2/ROADMAP.md T1-T4): the method of the first gate (RuntimeLeakGateDeviceTest) applied to
 * scripts of the plugin engines. For every scenario and mode the runner executes the script `warmups + iterations` times in this
 * process and keeps weak references only: to each run's engines and ScriptRuntime (EngineLifecycleCallback) and to the host-side
 * session objects of the bridge that [BridgeExecutionObserver] reports (T1 (a)). After every run it forces collection and checks
 * that all of them are gone, then compares the first gate's measurements plus the bridge registries and system-side bindings of
 * T1 (h) and the bridge threads of T1 (d). The PSS of the plugin processes is recorded only (T1 (i), R9). A retained object
 * triggers a heap dump and a shortest strong path analysis of the bridge classes (T4).
 *
 * Results go to the same three places as the first gate (instrumentation status [STATUS_KEY], logcat lines tagged [LOG_TAG],
 * a JSON file in getExternalFilesDir("memory-gate")). Drive it with `tools/memory/run_gate.py --suite bridge`, which starts one
 * process per scenario and mode, sets `hidden_api_policy 1` for the binding probe (D9) and grants the permissions of a scenario (D11).
 *
 * Arguments (all optional): those of the first gate (memoryGateRunId, memoryGateModes, memoryGateWarmups, memoryGateIterations,
 * memoryGatePollMs, memoryGateForceDelayMs, memoryGateKeepHprof, memoryGateAnalyze, memoryGateCapture, memoryGateMeminfo) and
 * memoryGatePluginCommit (the commit of the installed plugin build, written to the report header).
 *
 * zh-CN: 桥接泄漏门禁 (docs/memory-phase2/ROADMAP.md T1-T4): 把第一期门禁 (RuntimeLeakGateDeviceTest) 的方法用于插件引擎脚本. 对每个场景与模式,
 * 在本进程内执行脚本 `预热 + 计数` 次, 只保留弱引用: 每次运行的引擎与 ScriptRuntime (EngineLifecycleCallback), 以及 [BridgeExecutionObserver]
 * 报告的桥接宿主侧会话对象 (T1 (a)). 每次运行后强制回收并确认它们全部消失, 再比较第一期的度量, T1 (h) 的桥接注册表与系统侧绑定, 以及 T1 (d)
 * 的桥接线程. 插件进程 PSS 只记录 (T1 (i), R9). 有对象残留时转储堆并分析桥接类的最短强引用链 (T4). 结果输出与第一期相同.
 * 由 `tools/memory/run_gate.py --suite bridge` 驱动: 每个场景与模式各用一个进程, 为绑定探针设置 `hidden_api_policy 1` (D9), 并授予场景所需权限 (D11).
 */
@RunWith(AndroidJUnit4::class)
class BridgeLeakGateDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val arguments: Bundle = InstrumentationRegistry.getArguments()
    private val service get() = AutoJs.instance.scriptEngineService
    private val tracker = BridgeTracker()

    private val runId by lazy {
        (arguments.getString("memoryGateRunId") ?: "manual-${System.currentTimeMillis()}").also {
            require(it.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "memoryGateRunId must be a plain label" }
        }
    }
    private val modes by lazy {
        (arguments.getString("memoryGateModes") ?: "normal,force").split(',').filter { it.isNotBlank() }.map(GateMode::of).distinct()
    }
    private val warmups by lazy { integer("memoryGateWarmups", 2, 0, 5) }
    private val iterations by lazy { integer("memoryGateIterations", 20, 1, 100) }
    private val pollMs by lazy { integer("memoryGatePollMs", 10_000, 500, 60_000).toLong() }
    private val forceDelayMs by lazy { integer("memoryGateForceDelayMs", 1_000, 0, 30_000).toLong() }
    private val keepHprof by lazy { arguments.getString("memoryGateKeepHprof") == "true" }
    private val analyzeHeap by lazy { arguments.getString("memoryGateAnalyze") != "false" }
    private val captureAllowed by lazy { arguments.getString("memoryGateCapture") == "on" }
    private val meminfoMode by lazy {
        (arguments.getString("memoryGateMeminfo") ?: "phases").also { require(it in setOf("off", "phases", "runs")) { "memoryGateMeminfo must be off, phases or runs" } }
    }
    private val pluginCommit by lazy {
        arguments.getString("memoryGatePluginCommit")?.also { require(it.matches(Regex("[0-9a-f]{4,40}(-dirty)?"))) { "memoryGatePluginCommit must be a commit hash" } }
    }

    @Before
    fun register() {
        service.registerEngineLifecycleCallback(tracker)
        BridgeExecutionObserver.listener = tracker
    }

    @After
    fun unregister() {
        BridgeExecutionObserver.listener = null
        service.unregisterEngineLifecycleCallback(tracker)
    }

    @Test fun nd01EmptyScript() = gate("ND1")
    @Test fun nd02HostCapabilities() = gate("ND2")
    @Test fun nd03Callbacks() = gate("ND3")
    @Test fun nd04PluginKilled() = gate("ND4")
    @Test fun nd05UiSession() = gate("ND5")
    @Test fun nd06Compose() = gate("ND6")
    @Test fun nd07aWebPageAndAction() = gate("ND7a")
    @Test fun nd07bWebDownload() = gate("ND7b")
    @Test fun nd07cWebMedia() = gate("ND7c")
    @Test fun nd08ScreenCapture() = gate("ND8")
    @Test fun nd09Overlay() = gate("ND9")
    @Test fun nd10Dialog() = gate("ND10")
    @Test fun nd11ImageHandles() = gate("ND11")
    @Test fun nd12AudioRecording() = gate("ND12")
    @Test fun nd13ColorOsRebind() = gate("ND13")

    private fun gate(id: String) {
        val scenario = BridgeScenarios.byId(id)
        val reports = modes.map { mode -> runScenario(scenario, mode).also(::emit) }
        val skipped = reports.filter { it.optString("status") == "skipped" }
        assumeFalse("Skipped: " + skipped.joinToString("; ") { texts(it.optJSONArray("reasons")) },
            skipped.size == reports.size)
        val failed = reports.filter { it.optString("status") in setOf("fail", "error") }
        if (failed.isNotEmpty()) {
            fail(failed.joinToString("; ") { report ->
                "${report.optString("scenario")} ${report.optString("mode")} ${report.optString("status")}: " +
                    texts(report.optJSONArray("reasons"))
            })
        }
    }

    // region Scenario run

    private fun runScenario(scenario: BridgeScenario, mode: GateMode): JSONObject {
        val report = header(scenario, mode)
        skipReason(scenario)?.let { reason ->
            return report.put("status", "skipped").put("reasons", JSONArray().put(reason))
        }
        report.put("environment", JSONObject()
            .put("keyguardLocked", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            .put("interactive", context.getSystemService(PowerManager::class.java).isInteractive)
            .put("overlayPermission", Settings.canDrawOverlays(context))
            .put("permissions", JSONObject(scenario.permissions.associateWith { granted(it) })))
        // Node scripts declare their bridge capabilities in package.json of the working directory (NodeBridgePermissionManifest).
        // zh-CN: Node 脚本在工作目录的 package.json 中声明桥接能力 (NodeBridgePermissionManifest).
        val workDir = File(context.filesDir, "memory-gate-bridge/${scenario.id}").apply {
            mkdirs()
            val manifest = JSONObject().put("autojs6", JSONObject().put("node", JSONObject().put("permissions", JSONArray(scenario.capabilities))))
            File(this, "package.json").writeText(manifest.toString())
        }.path
        val meminfo = meminfoMode != "off"
        // The first UiAutomation call connects it, so it happens before the first sample.
        // zh-CN: UiAutomation 首次调用会建立连接, 因此放在首个采样之前.
        BridgeTracker.shell(instrumentation, "true")
        warmRenderPipeline()
        val pssStart = BridgeTracker.pluginPss(instrumentation, BridgeScenarios.NODE_PACKAGE)
        val start = GateMetrics.sample()
        val nativeStart = nativeSnapshot(meminfo, instances = true)
        val bridgeStart = BridgeTracker.hostRegistries()
        val bindingsStart = BridgeTracker.systemBindings(context, PLUGIN_PACKAGES)
        var baseline = start
        var nativeBaseline = nativeStart
        var registriesBaseline = StaticRegistries.counts()
        var threadsBaseline = BridgeTracker.bridgeThreads()
        var pssSecond: JSONObject? = null
        val runs = mutableListOf<RunRecord>()
        val samples = JSONArray()
        var budget = pollMs
        var retainedWithinBudget = 0
        var scriptSkip: String? = null
        for (index in 0 until warmups + iterations) {
            val warmup = index < warmups
            val sampler = GateNativeProbe.PeakSampler().apply { start() }
            val record = runOnce(scenario, mode, index, warmup, workDir)
            record.nativePeakBytes = sampler.stop()
            record.clearMs = GateMetrics.awaitCleared(record.references(), budget)
            if (record.clearMs == null && ++retainedWithinBudget >= REDUCED_BUDGET_AFTER) budget = REDUCED_BUDGET_MS
            val sample = GateMetrics.sample()
            val every = meminfoMode == "runs"
            val native = if (index == warmups - 1) nativeSnapshot(meminfo, instances = true) else nativeSnapshot(meminfo && every, instances = every)
            if (index == warmups - 1) {
                baseline = sample
                nativeBaseline = native
                registriesBaseline = StaticRegistries.counts()
                threadsBaseline = BridgeTracker.bridgeThreads()
            }
            // T1 (i): the plugin PSS after the second measured run and after the last one (recorded only).
            // zh-CN: T1 (i): 第 2 次计数运行后与最后一次运行后的插件 PSS (只记录).
            if (index == warmups + 1) pssSecond = BridgeTracker.pluginPss(instrumentation, BridgeScenarios.NODE_PACKAGE)
            samples.put(record.json().put("after", sample.json()).put("native", native)
                .put("bridge", JSONObject(BridgeTracker.hostRegistries()))
                .put("bindings", BridgeTracker.systemBindings(context, PLUGIN_PACKAGES)))
            runs += record
            record.skip?.let { scriptSkip = it }
            if (scriptSkip != null) break
        }
        scriptSkip?.let { reason ->
            return report.put("status", "skipped").put("reasons", JSONArray().put("script: $reason")).put("samples", samples)
        }
        val measured = runs.filter { !it.warmup }
        GateMetrics.awaitCleared(measured.flatMap { it.references() }, pollMs)
        val final = GateMetrics.sample()
        val nativeFinal = nativeSnapshot(meminfo, instances = true)
        val registriesFinal = StaticRegistries.counts()
        val bridgeFinal = BridgeTracker.hostRegistries()
        val bindingsFinal = BridgeTracker.systemBindings(context, PLUGIN_PACKAGES)
        val threadsFinal = BridgeTracker.bridgeThreads()
        val pssFinal = BridgeTracker.pluginPss(instrumentation, BridgeScenarios.NODE_PACKAGE)
        val retainedRuns = measured.filter { it.retained() }
        val retainedWarmups = runs.filter { it.warmup && it.retained() }
        val frameBytes = if (scenario.capture) context.resources.displayMetrics.let { it.widthPixels.toLong() * it.heightPixels * 4 } else 0L

        val metrics = JSONObject()
            .put("retained", retainedRuns.size)
            .put("retainedWarmups", retainedWarmups.size)
            .put("retainedObjects", retainedObjects(retainedRuns + retainedWarmups))
            .put("javaHeapDeltaBytes", final.javaHeapBytes - baseline.javaHeapBytes)
            .put("nativeHeapDeltaBytes", final.nativeHeapBytes - baseline.nativeHeapBytes)
            .put("scriptThreadDelta", final.scriptThreads - start.scriptThreads)
            .put("allThreadDelta", final.allThreads - start.allThreads)
            .put("rhinoContextDelta", if (final.rhinoContexts < 0 || start.rhinoContexts < 0) JSONObject.NULL else final.rhinoContexts - start.rhinoContexts)
            .put("engines", final.engines)
            .put("enginesBefore", start.engines)
            .put("notRemovedEngines", measured.sumOf { it.notRemoved })
            .put("scriptThreadNames", JSONArray(GateMetrics.liveThreads().filter(GateMetrics::isScriptThread).map { it.name }.distinct().take(20)))
            .put("readyReached", runs.count { it.readyReached })
            .put("pluginKilled", runs.count { it.pluginKilled })
        val native = JSONObject()
            .put("imagesLiveDelta", nativeFinal.getInt("imagesLive") - nativeStart.getInt("imagesLive"))
            .put("openMediaImages", nativeFinal.getInt("openMediaImages"))
            .put("matsLiveDelta", nativeFinal.getLong("matsLive") - nativeBaseline.getLong("matsLive"))
            .put("nativePeakMaxBytes", measured.maxOfOrNull { it.nativePeakBytes } ?: 0L)
        meminfoDelta(nativeBaseline.optJSONObject("meminfo"), nativeFinal.optJSONObject("meminfo"))?.let { native.put("meminfoDeltaKb", it) }
        instanceDeltas(nativeBaseline.optJSONObject("instances"), nativeFinal.optJSONObject("instances"))?.let { native.put("instanceDeltasFromBaseline", it) }
        metrics.put("native", native)
        // First gate registries (T7 of the first roadmap): back where they were at the end of the warm-ups.
        // zh-CN: 第一期注册表 (第一期路线图 T7): 回到预热结束时的值.
        val registriesDelta = JSONObject()
        registriesFinal.forEach { (name, count) ->
            val delta = count - (registriesBaseline[name] ?: 0)
            if (delta != 0) registriesDelta.put(name, delta)
        }
        metrics.put("registries", JSONObject(registriesFinal)).put("registriesDelta", registriesDelta)
        // T1 (h) host side: back where they were before the first run; a pooled engine may keep one idle connection.
        // zh-CN: T1 (h) 宿主侧: 回到首次运行前的值; 带连接池的引擎可保留 1 个空闲连接.
        val bridgeDelta = JSONObject()
        bridgeFinal.forEach { (name, count) ->
            val delta = count - (bridgeStart[name] ?: 0)
            if (delta != 0) bridgeDelta.put(name, delta)
        }
        metrics.put("bridgeRegistries", JSONObject(bridgeFinal)).put("bridgeRegistriesDelta", bridgeDelta)
        // T1 (h) system side: connections to the plugin packages, at most one more than before the first run.
        // zh-CN: T1 (h) 系统侧: 到插件包的连接, 最多比首次运行前多 1 个.
        val bindings = JSONObject().put("start", bindingsStart).put("final", bindingsFinal)
        val bindingDelta = if (bindingsStart.has("pluginConnections") && bindingsFinal.has("pluginConnections")) {
            bindingsFinal.getInt("pluginConnections") - bindingsStart.getInt("pluginConnections")
        } else null
        bindings.put("pluginConnectionsDelta", bindingDelta ?: JSONObject.NULL)
        bindings.put("probe", if (bindingDelta == null) "unavailable" else "available")
        metrics.put("bindings", bindings)
        // T1 (d): bridge threads against the end of the warm-ups.
        // zh-CN: T1 (d): 桥接线程相对预热结束时.
        val newThreads = threadsFinal.groupingBy { it }.eachCount().flatMap { (name, count) ->
            List((count - threadsBaseline.count { it == name }).coerceAtLeast(0)) { name }
        }
        metrics.put("bridgeThreads", threadsFinal.size).put("bridgeThreadDelta", threadsFinal.size - threadsBaseline.size)
            .put("bridgeThreadsNew", JSONArray(newThreads.distinct().take(20)))
        // T1 (i), recorded only (R9).
        // zh-CN: T1 (i), 只记录 (R9).
        val pss = JSONObject().put("start", pssStart ?: JSONObject.NULL).put("afterSecondRun", pssSecond ?: JSONObject.NULL)
            .put("final", pssFinal ?: JSONObject.NULL)
        val second = pssSecond?.optLong("totalPssKb", -1L) ?: -1L
        val last = pssFinal?.optLong("totalPssKb", -1L) ?: -1L
        pss.put("deltaKb", if (second > 0 && last > 0) last - second else JSONObject.NULL)
        metrics.put("pluginPss", pss)

        val reasons = JSONArray()
        if (retainedRuns.isNotEmpty()) reasons.put("retained runs ${retainedRuns.size}/${measured.size} (" + retainedObjectsText(retainedRuns) + ")")
        if (metrics.getLong("javaHeapDeltaBytes") > JAVA_HEAP_LIMIT) reasons.put("java heap +${mb(metrics.getLong("javaHeapDeltaBytes"))} MB")
        if (metrics.getLong("nativeHeapDeltaBytes") > NATIVE_HEAP_LIMIT + frameBytes) reasons.put("native heap +${mb(metrics.getLong("nativeHeapDeltaBytes"))} MB")
        if (metrics.getInt("scriptThreadDelta") > 0) reasons.put("script threads +${metrics.getInt("scriptThreadDelta")}")
        if (final.rhinoContexts >= 0 && start.rhinoContexts >= 0 && final.rhinoContexts > start.rhinoContexts) reasons.put("rhino contexts +${final.rhinoContexts - start.rhinoContexts}")
        if (final.engines > start.engines) reasons.put("live engines ${final.engines} (before ${start.engines})")
        if (native.getInt("imagesLiveDelta") > 0) reasons.put("images not recycled +${native.getInt("imagesLiveDelta")}")
        if (native.getInt("openMediaImages") > 0) reasons.put("open media images ${native.getInt("openMediaImages")}")
        if (native.getLong("matsLiveDelta") > 0) reasons.put("live mats +${native.getLong("matsLiveDelta")}")
        registriesDelta.keys().forEach { name -> registriesDelta.getInt(name).takeIf { it > 0 }?.let { reasons.put("registry $name +$it") } }
        bridgeFinal.forEach { (name, count) ->
            val before = bridgeStart[name] ?: 0
            val allowed = if (name in POOLED_REGISTRIES) maxOf(before, 1) else before
            if (count > allowed) reasons.put("bridge registry $name $count (before $before, allowed $allowed)")
        }
        if (bindingDelta != null && bindingDelta > BINDING_SLACK) reasons.put("plugin bindings +$bindingDelta (allowed +$BINDING_SLACK)")
        if (newThreads.isNotEmpty()) reasons.put("bridge threads +${newThreads.size}: " + newThreads.distinct().take(3).joinToString(", "))
        val errors = runs.flatMap { it.errors }.distinct()
        val stopErrors = runs.flatMap { it.stopErrors }.distinct()
        val scriptErrors = runs.flatMap { it.scriptErrors }.distinct()
        val unstarted = runs.count { !it.started }
        val timedOut = runs.count { it.forcedCleanup }
        val unexpected = if (scenario.expectsFailure) emptyList() else errors
        if (unexpected.isNotEmpty()) reasons.put("script errors: " + (scriptErrors.ifEmpty { unexpected }).take(3).joinToString(" | "))
        if (unstarted > 0) reasons.put("runs not started in time $unstarted")
        if (timedOut > 0) reasons.put("runs that did not end in time $timedOut")
        val status = when {
            unexpected.isNotEmpty() || unstarted > 0 -> "error"
            reasons.length() > 0 -> "fail"
            else -> "pass"
        }
        if (scenario.expectsFailure) {
            report.put("expectedErrors", JSONArray(errors.take(5))).put("runsEndedByError", runs.count { it.errors.isNotEmpty() })
        }
        if (stopErrors.isNotEmpty()) report.put("stopErrors", JSONArray(stopErrors.take(5)))
        if (scriptErrors.isNotEmpty()) report.put("scriptErrors", JSONArray(scriptErrors.take(5)))
        report.put("interruptedRuns", runs.count { it.interrupted > 0 })
        val thresholds = JSONObject().put("retained", 0).put("javaHeapDeltaBytes", JAVA_HEAP_LIMIT)
            .put("nativeHeapDeltaBytes", NATIVE_HEAP_LIMIT + frameBytes).put("scriptThreadDelta", 0)
            .put("rhinoContextDelta", 0).put("engines", start.engines).put("imagesLiveDelta", 0).put("openMediaImages", 0)
            .put("matsLiveDelta", 0).put("registriesDelta", 0).put("bridgeRegistriesDelta", 0)
            .put("pooledRegistries", JSONArray(POOLED_REGISTRIES)).put("pluginConnectionsDelta", BINDING_SLACK).put("bridgeThreadDelta", 0)
        report.put("status", status).put("reasons", reasons).put("metrics", metrics).put("thresholds", thresholds)
            .put("start", start.json()).put("baseline", baseline.json()).put("final", final.json()).put("samples", samples)
            .put("nativeStart", nativeStart).put("nativeBaseline", nativeBaseline).put("nativeFinal", nativeFinal)
        if (retainedRuns.isNotEmpty() || retainedWarmups.isNotEmpty()) report.put("heap", heapEvidence(scenario, mode))
        return report
    }

    private fun runOnce(scenario: BridgeScenario, mode: GateMode, index: Int, warmup: Boolean, workDir: String): RunRecord {
        if (scenario.forceStopPluginBeforeRun) {
            BridgeTracker.shell(instrumentation, "am force-stop ${BridgeScenarios.NODE_PACKAGE}")
            SystemClock.sleep(PLUGIN_STOP_SETTLE_MS)
        }
        val window = BridgeWindow()
        tracker.window = window
        val begun = SystemClock.elapsedRealtime()
        val started = CountDownLatch(1)
        val outcome = RunOutcome()
        val errors = outcome.errors
        try {
            launch(scenario, mode, index, started, outcome, workDir)
            val startedInTime = started.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            var forceTriggered = false
            var readyReached = false
            var pluginKilled = false
            if ((mode == GateMode.FORCE || scenario.killPluginOnReady) && startedInTime && errors.isEmpty()) {
                readyReached = awaitCondition(READY_TIMEOUT_MS) {
                    outcome.ready.get() && window.created() >= scenario.expectedEngines
                } || outcome.ready.get()
                val endedEarly = outcome.skip != null || (window.created() >= scenario.expectedEngines && window.allRemoved())
                if (scenario.killPluginOnReady && readyReached) {
                    BridgeTracker.shell(instrumentation, "am force-stop ${BridgeScenarios.NODE_PACKAGE}")
                    pluginKilled = true
                }
                if (mode == GateMode.FORCE && !endedEarly) {
                    SystemClock.sleep(forceDelayMs)
                    outcome.stopping.set(true)
                    window.forceStopAlive()
                    forceTriggered = true
                }
            }
            val timeout = if (mode == GateMode.FORCE) FORCE_END_TIMEOUT_MS else scenario.normalTimeoutMs
            var ended = awaitCondition(timeout) {
                window.allRemoved() && (window.created() >= scenario.expectedEngines || errors.isNotEmpty() || !startedInTime)
            }
            var forcedCleanup = false
            if (!ended) {
                forcedCleanup = true
                outcome.stopping.set(true)
                window.forceStopAlive()
                ended = awaitCondition(FORCE_END_TIMEOUT_MS) { window.allRemoved() }
            }
            return RunRecord(
                index = index, warmup = warmup, started = startedInTime, readyReached = readyReached, pluginKilled = pluginKilled,
                forceTriggered = forceTriggered, forcedCleanup = forcedCleanup, ended = ended,
                durationMs = SystemClock.elapsedRealtime() - begun, created = window.created(), notRemoved = window.notRemoved(),
                errors = errors.toList(), stopErrors = outcome.stopErrors.toList(), scriptErrors = outcome.scriptErrors.toList(),
                interrupted = outcome.interrupted.get(), skip = outcome.skip,
                runtimes = window.runtimeReferences(), engines = window.engineReferences(), sessions = window.sessionReferences(),
                executionIds = window.executionIds(), stats = outcome.stats.toMap(),
            )
        } finally {
            tracker.window = null
        }
    }

    /** Kept in its own frame so that no local of the test method references the execution. */
    private fun launch(scenario: BridgeScenario, mode: GateMode, index: Int, started: CountDownLatch, outcome: RunOutcome, workDir: String) {
        val marker = "memory-gate-${scenario.id}-${mode.label}-$index-${UUID.randomUUID()}"
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                // The console keeps listeners as weak references; the run's outcome holds this one.
                // zh-CN: 控制台以弱引用保存监听器; 由本次运行的 outcome 持有它.
                (execution.engine as? JavaScriptEngine)?.runtime?.console?.addLogListener(outcome)
                started.countDown()
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                val text = (e.javaClass.simpleName + ": " + e.message).take(300)
                when {
                    ScriptInterruptedException.causedByInterrupt(e) -> outcome.interrupted.incrementAndGet()
                    outcome.stopping.get() -> outcome.stopErrors += text
                    else -> outcome.errors += text
                }
                started.countDown()
            }
        }
        val config = ExecutionConfig().apply { workingDirectory = workDir }
        service.execute(StringScriptSource(marker, BridgeScenarios.nodeSource(scenario, mode, marker, fixturePng)), listener, config)
    }

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    /** See RuntimeLeakGateDeviceTest.warmRenderPipeline. zh-CN: 见 RuntimeLeakGateDeviceTest.warmRenderPipeline. */
    private fun warmRenderPipeline() {
        if (renderPipelineWarmed) return
        renderPipelineWarmed = true
        instrumentation.runOnMainSync { Toast.makeText(context, RENDER_WARMUP_TEXT, Toast.LENGTH_SHORT).show() }
        SystemClock.sleep(RENDER_WARMUP_MS)
    }

    private fun nativeSnapshot(meminfo: Boolean, instances: Boolean): JSONObject {
        val snapshot = GateNativeProbe.counters()
        if (meminfo) GateNativeProbe.meminfo(instrumentation)?.let { snapshot.put("meminfo", it) }
        if (instances) snapshot.put("instances", GateNativeProbe.instanceCounts())
        return snapshot
    }

    private fun meminfoDelta(before: JSONObject?, after: JSONObject?): JSONObject? {
        if (before == null || after == null) return null
        val deltas = JSONObject()
        after.keys().forEach { key -> if (key.endsWith("Kb") && before.has(key)) deltas.put(key, after.getLong(key) - before.getLong(key)) }
        return deltas
    }

    private fun instanceDeltas(first: JSONObject?, last: JSONObject?): JSONObject? {
        if (first == null || last == null) return null
        val deltas = JSONObject()
        last.keys().forEach { key ->
            val a = first.optLong(key, -1)
            val b = last.optLong(key, -1)
            deltas.put(key, if (a < 0 || b < 0) JSONObject.NULL else b - a)
        }
        return deltas
    }

    /** Reachable objects per kind over [runs] (engine, runtime, node.broker, node.provider). zh-CN: [runs] 中各类仍可达的对象数. */
    private fun retainedObjects(runs: List<RunRecord>): JSONObject {
        val result = JSONObject()
        runs.forEach { run ->
            run.reachable().forEach { (kind, count) -> result.put(kind, result.optInt(kind) + count) }
        }
        return result
    }

    private fun retainedObjectsText(runs: List<RunRecord>): String {
        val objects = retainedObjects(runs)
        return objects.keys().asSequence().joinToString(", ") { "$it ${objects.getInt(it)}" }
    }

    // endregion

    // region Scenario requirements and fixtures

    private fun skipReason(scenario: BridgeScenario): String? {
        if (scenario.colorOsOnly && Build.MANUFACTURER.lowercase() !in COLOR_OS_MANUFACTURERS) {
            return "${scenario.id} runs on ColorOS only (OPD2413); this device is ${Build.MANUFACTURER} ${Build.MODEL}"
        }
        if (scenario.capture && !captureAllowed) return "screen capture needs memoryGateCapture=on (run_gate.py --capture on allows PROJECT_MEDIA)"
        if (scenario.needsOverlay && !Settings.canDrawOverlays(context)) return "the host has no overlay permission"
        scenario.permissions.firstOrNull { !granted(it) }?.let { return "the host lacks $it (run_gate.py --suite bridge grants it, ROADMAP D11)" }
        return pluginSkipReason()
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * The Node plugin must be installed, enabled, authorized and answer a probe; the probe binds the plugin, so it runs before
     * the first sample.
     * zh-CN: Node 插件须已安装, 已启用, 已授权并能响应探测; 探测会绑定插件, 因此在首个采样之前进行.
     */
    private fun pluginSkipReason(): String? {
        val name = BridgeScenarios.NODE_PACKAGE
        try {
            context.packageManager.getPackageInfo(name, 0)
        } catch (_: Exception) {
            return "plugin $name is not installed"
        }
        if (!PluginEnableStore.isEnabled(context, name, defaultEnabled = true)) return "plugin $name is disabled in Plugin Center"
        if (!PluginTrustManager.isAuthorized(context, name)) return "plugin $name is not authorized"
        val probe = runCatching { runBlocking { NodeJsRuntimePluginHost.probe(context) } }
        return probe.exceptionOrNull()?.let { "the Node plugin did not answer the probe: " + (it.javaClass.simpleName + ": " + it.message).take(300) }
    }

    /** A 64x64 PNG as Base64, the image of the round trips in ND2 / ND11. zh-CN: 64x64 的 PNG (Base64), 用于 ND2 / ND11 的往返. */
    private val fixturePng: String by lazy {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (y in 0 until 64 step 8) for (x in 0 until 64 step 8) {
            paint.color = Color.rgb((x * 4 + y) % 256, (y * 3 + x) % 256, (x + y * 2) % 256)
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 8f, y + 8f, paint)
        }
        val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // endregion

    // region Heap evidence and output

    private fun heapEvidence(scenario: BridgeScenario, mode: GateMode): JSONObject {
        val result = JSONObject()
        val hprof = File(outputDir(), "$runId-${scenario.id}-${mode.label}.hprof")
        try {
            GateMetrics.forceGc()
            val dumpStarted = SystemClock.elapsedRealtime()
            Debug.dumpHprofData(hprof.path)
            result.put("dumpMs", SystemClock.elapsedRealtime() - dumpStarted).put("hprof", hprof.path)
            if (analyzeHeap) {
                result.put("analysis", GateHeapPathAnalyzer.analyzeClasses(hprof, BridgeScenarios.NODE_HEAP_CLASSES))
            } else {
                result.put("analysis", JSONObject().put("skipped", "memoryGateAnalyze=false"))
            }
        } catch (error: Throwable) {
            result.put("error", (error.javaClass.simpleName + ": " + error.message).take(600))
        } finally {
            if (!keepHprof) {
                hprof.delete()
                result.put("hprofKept", false)
            } else {
                result.put("hprofKept", hprof.isFile)
            }
        }
        return result
    }

    private fun header(scenario: BridgeScenario, mode: GateMode): JSONObject {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("suite", "bridge-gate")
            .put("runId", runId)
            .put("scenario", scenario.id)
            .put("title", scenario.title)
            .put("hypotheses", scenario.hypotheses)
            .put("notes", scenario.notes)
            .put("mode", mode.label)
            .put("modeCode", mode.code)
            .put("warmups", warmups)
            .put("iterations", iterations)
            .put("pollMs", pollMs)
            .put("forceDelayMs", forceDelayMs)
            .put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("api", Build.VERSION.SDK_INT).put("release", Build.VERSION.RELEASE)
                .put("lowRam", activityManager.isLowRamDevice).put("memoryClassMb", activityManager.memoryClass)
                .put("largeMemoryClassMb", activityManager.largeMemoryClass).put("process64Bit", Process.is64Bit()))
            .put("host", packageJson(context.packageName))
            .put("plugin", packageJson(BridgeScenarios.NODE_PACKAGE).put("commit", pluginCommit ?: JSONObject.NULL))
            .put("pid", Process.myPid())
            .put("recordedWallMs", System.currentTimeMillis())
            .put("methodology", METHODOLOGY)
    }

    private fun packageJson(name: String): JSONObject {
        val result = JSONObject().put("package", name)
        val info = runCatching { context.packageManager.getPackageInfo(name, 0) }.getOrNull() ?: return result.put("installed", false)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return result.put("versionName", info.versionName).put("versionCode", versionCode)
    }

    private fun emit(report: JSONObject) {
        val text = report.toString()
        val id = "${report.optString("scenario")}-${report.optString("mode")}"
        instrumentation.sendStatus(STATUS_CODE, Bundle().apply { putString(STATUS_KEY, text) })
        val metrics = report.optJSONObject("metrics")
        Log.i(LOG_TAG, "SUMMARY $runId $id ${report.optString("status")} " + (metrics?.let {
            "retained=${it.opt("retained")} javaHeap=${it.opt("javaHeapDeltaBytes")} nativeHeap=${it.opt("nativeHeapDeltaBytes")} " +
                "threads=${it.opt("scriptThreadDelta")} engines=${it.opt("engines")} bridge=${it.opt("bridgeRegistriesDelta")} " +
                "bindings=${it.optJSONObject("bindings")?.opt("pluginConnectionsDelta")}"
        } ?: texts(report.optJSONArray("reasons"))))
        val chunks = text.chunked(LOG_CHUNK)
        chunks.forEachIndexed { i, chunk -> Log.i(LOG_TAG, "JSON $runId $id ${i + 1}/${chunks.size} $chunk") }
        runCatching { File(outputDir(), "$runId-$id.json").writeText(report.toString(2)) }
            .onFailure { Log.w(LOG_TAG, "Cannot write the report file", it) }
    }

    private fun outputDir(): File = (context.getExternalFilesDir("memory-gate") ?: File(context.filesDir, "memory-gate-out")).apply { mkdirs() }

    private fun integer(name: String, fallback: Int, minimum: Int, maximum: Int): Int =
        (arguments.getString(name)?.toIntOrNull() ?: fallback).also { require(it in minimum..maximum) { "$name is out of range" } }

    private fun texts(array: JSONArray?): String =
        if (array == null) "" else (0 until array.length()).joinToString(", ") { array.optString(it) }

    private fun mb(bytes: Long) = String.format(java.util.Locale.ROOT, "%.2f", bytes / 1048576.0)

    // endregion

    // region Run state

    /**
     * What one run reports: listener callbacks, and the gate lines the script prints (gateReady / gateStat / gateSkip / errors),
     * read from the host console as a weakly held [ConsoleImpl.LogListener].
     * zh-CN: 一次运行的结果: 监听器回调, 以及脚本打印的门禁行 (gateReady / gateStat / gateSkip / 错误), 作为被弱引用的 [ConsoleImpl.LogListener] 从宿主控制台读取.
     */
    private class RunOutcome : ConsoleImpl.LogListener {
        val errors = ConcurrentLinkedQueue<String>()
        val stopErrors = ConcurrentLinkedQueue<String>()
        val scriptErrors = ConcurrentLinkedQueue<String>()
        val interrupted = AtomicInteger()
        val stopping = AtomicBoolean(false)
        val ready = AtomicBoolean(false)
        val stats = ConcurrentHashMap<String, Double>()

        @Volatile
        var skip: String? = null

        override fun onNewLog(logEntry: ConsoleImpl.LogEntry) {
            logEntry.content.toString().lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.endsWith(BridgeScenarios.READY_LINE) -> ready.set(true)
                    BridgeScenarios.STAT_PREFIX in line -> {
                        val parts = line.substringAfter(BridgeScenarios.STAT_PREFIX).split(' ')
                        parts.getOrNull(1)?.toDoubleOrNull()?.let { stats[parts[0]] = it }
                    }
                    BridgeScenarios.SKIP_PREFIX in line -> skip = line.substringAfter(BridgeScenarios.SKIP_PREFIX).take(300)
                    BridgeScenarios.ERROR_PREFIX in line -> scriptErrors += line.substringAfter(BridgeScenarios.ERROR_PREFIX).take(300)
                }
            }
        }

        override fun onLogClear() = Unit
    }

    private class RunRecord(
        val index: Int,
        val warmup: Boolean,
        val started: Boolean,
        val readyReached: Boolean,
        val pluginKilled: Boolean,
        val forceTriggered: Boolean,
        val forcedCleanup: Boolean,
        val ended: Boolean,
        val durationMs: Long,
        val created: Int,
        val notRemoved: Int,
        val errors: List<String>,
        val stopErrors: List<String>,
        val scriptErrors: List<String>,
        val interrupted: Int,
        val skip: String?,
        val runtimes: List<WeakReference<*>>,
        val engines: List<WeakReference<*>>,
        val sessions: Map<String, List<WeakReference<*>>>,
        val executionIds: List<String>,
        val stats: Map<String, Double>,
    ) {
        var clearMs: Long? = null
        var nativePeakBytes = 0L

        fun references(): List<WeakReference<*>> = runtimes + engines + sessions.values.flatten()

        fun reachable(): Map<String, Int> = (mapOf("engine" to engines, "runtime" to runtimes) + sessions)
            .mapValues { (_, references) -> references.count { it.get() != null } }.filterValues { it > 0 }

        fun retained() = references().any { it.get() != null }

        fun json(): JSONObject = JSONObject()
            .put("index", index)
            .put("warmup", warmup)
            .put("started", started)
            .put("readyReached", readyReached)
            .put("pluginKilled", pluginKilled)
            .put("forceTriggered", forceTriggered)
            .put("forcedCleanup", forcedCleanup)
            .put("ended", ended)
            .put("durationMs", durationMs)
            .put("engines", created)
            .put("notRemoved", notRemoved)
            .put("sessions", JSONObject(sessions.mapValues { it.value.size }))
            .put("executionIds", executionIds.size)
            .put("clearedMs", clearMs ?: JSONObject.NULL)
            .put("reachable", JSONObject(reachable()))
            .put("errors", JSONArray(errors))
            .put("stopErrors", JSONArray(stopErrors))
            .put("scriptErrors", JSONArray(scriptErrors))
            .put("interrupted", interrupted)
            .put("nativePeakBytes", nativePeakBytes)
            .put("stats", JSONObject(stats))
    }

    // endregion

    companion object {
        const val STATUS_KEY = "aj6mem_json"
        const val STATUS_CODE = 2
        const val LOG_TAG = "AJ6MEM"
        private const val SCHEMA_VERSION = 1
        private const val LOG_CHUNK = 3000
        private const val JAVA_HEAP_LIMIT = 2L * 1024 * 1024
        private const val NATIVE_HEAP_LIMIT = 4L * 1024 * 1024
        private const val START_TIMEOUT_MS = 30_000L
        private const val READY_TIMEOUT_MS = 20_000L
        private const val FORCE_END_TIMEOUT_MS = 30_000L
        private const val REDUCED_BUDGET_AFTER = 3
        private const val REDUCED_BUDGET_MS = 1_000L
        private const val PLUGIN_STOP_SETTLE_MS = 500L
        private const val RENDER_WARMUP_TEXT = "AutoJs6 memory gate"
        private const val RENDER_WARMUP_MS = 2_000L

        /** T1 (h): system-side connections allowed above the start (one pooled idle connection). zh-CN: 系统侧连接相对开始允许多出的数量 (1 个池化空闲连接). */
        private const val BINDING_SLACK = 1

        /** Host registries of pooled engines, which may keep one idle connection (T1 (h)). zh-CN: 带连接池的引擎的宿主注册表, 可保留 1 个空闲连接 (T1 (h)). */
        private val POOLED_REGISTRIES = listOf("NodeJsRuntimePluginHost.connectionPool")
        private val PLUGIN_PACKAGES = setOf(BridgeScenarios.NODE_PACKAGE)
        private val COLOR_OS_MANUFACTURERS = setOf("oppo", "oneplus", "realme")

        @Volatile
        private var renderPipelineWarmed = false

        private const val METHODOLOGY = "Same-process runs of real plugin engine scripts (docs/memory-phase2/ROADMAP.md T1-T4), " +
            "the method of the first gate: engines and ScriptRuntime are tracked from EngineLifecycleCallback through weak references, " +
            "and so are the host-side bridge session objects that BridgeExecutionObserver reports at creation (T1 (a)). After every " +
            "run the runner collects and polls up to pollMs (1 s once 3 runs stayed reachable); a final collection decides 'retained'. " +
            "Heap deltas compare the sample after the last warm-up with the final sample; thread, Rhino context, engine and bridge " +
            "registry deltas compare with the sample before the first run (the plugin probe already bound the plugin), bridge " +
            "threads with the end of the warm-ups. Bridge registries must return to the start except the Node connection pool, " +
            "which may keep one idle connection; system-side connections of this process to the plugin packages " +
            "(LoadedApk.mServices, hidden_api_policy 1) may be one above the start. Plugin process PSS is recorded after the " +
            "second measured run and at the end, never judged (R9). Force mode waits for gateReady(), sleeps forceDelayMs and " +
            "calls forceStop on every live engine of the run on the main thread."
    }
}
