package org.autojs.autojs.memory

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.memory.scenarios.GateEnvironment
import org.autojs.autojs.memory.scenarios.GateMode
import org.autojs.autojs.memory.scenarios.GateScenario
import org.autojs.autojs.memory.scenarios.GateScenarios
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime leak gate (docs/memory/ROADMAP.md D3, T1-T4). For every scenario and mode the runner
 * executes the script `warmups + iterations` times in this process, keeps only weak references to
 * each run's engines and ScriptRuntime (taken in EngineLifecycleCallback.onEngineRemove), forces
 * collection and checks that every runtime is gone, then compares the six measurements of T1.
 * A retained runtime triggers a heap dump and a shortest strong path analysis with shark.
 *
 * Results go to three places: an instrumentation status (key [STATUS_KEY]), logcat lines tagged
 * [LOG_TAG], and a JSON file in getExternalFilesDir("memory-gate"). Drive it with
 * tools/memory/run_gate.py, which starts one process per scenario and mode.
 *
 * Arguments (all optional): memoryGateRunId, memoryGateModes (normal,force), memoryGateWarmups (2),
 * memoryGateIterations (20), memoryGatePollMs (10000), memoryGateForceDelayMs (1000),
 * memoryGateKeepHprof (false), memoryGateAnalyze (true), memoryGateCapture (off).
 *
 * zh-CN: 运行时泄漏门禁 (docs/memory/ROADMAP.md D3, T1-T4). 对每个场景与模式, 在本进程内执行脚本 `预热 + 计数` 次, 只保留每次运行的引擎与
 * ScriptRuntime 的弱引用 (在 EngineLifecycleCallback.onEngineRemove 中取得), 强制回收并确认 runtime 全部消失, 再比较 T1 的六项度量.
 * 有 runtime 残留时转储堆并用 shark 分析最短强引用链. 结果写到三处: instrumentation status, logcat 标记行与 memory-gate 目录的 JSON 文件.
 * 由 tools/memory/run_gate.py 驱动, 每个场景与模式各用一个进程.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeLeakGateDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val arguments: Bundle = InstrumentationRegistry.getArguments()
    private val service get() = AutoJs.instance.scriptEngineService
    private val tracker = EngineTracker()
    private var loopback: GateLoopbackServer? = null

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

    @Before
    fun register() {
        service.registerEngineLifecycleCallback(tracker)
    }

    @After
    fun unregister() {
        service.unregisterEngineLifecycleCallback(tracker)
        loopback?.close()
        loopback = null
    }

    @Test fun s01EmptyScript() = gate("S1")
    @Test fun s02ThreadsAndPool() = gate("S2")
    @Test fun s03NestedTimers() = gate("S3")
    @Test fun s04aCaptureScreen() = gate("S4a")
    @Test fun s04bReadAndFindImage() = gate("S4b")
    @Test fun s04cCopyClipResize() = gate("S4c")
    @Test fun s04dMatchTemplateOneShot() = gate("S4d")
    @Test fun s04eThreadedImages() = gate("S4e")
    @Test fun s05UiWithWebView() = gate("S5")
    @Test fun s06Floaty() = gate("S6")
    @Test fun s07EventsKeysSensors() = gate("S7")
    @Test fun s08JavaAdapterAndDex() = gate("S8")
    @Test fun s09ExecScriptFileChain() = gate("S9")
    @Test fun s10HttpAndWebSocket() = gate("S10")
    @Test fun s11DeviceReceivers() = gate("S11")
    @Test fun s12PluginBinding() = gate("S12")

    private fun gate(id: String) {
        val scenario = GateScenarios.byId(id)
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

    private fun runScenario(scenario: GateScenario, mode: GateMode): JSONObject {
        val report = header(scenario, mode)
        skipReason(scenario)?.let { reason ->
            return report.put("status", "skipped").put("reasons", JSONArray().put(reason))
        }
        val environment = environment(scenario)
        report.put("environment", JSONObject().put("accessibilityRunning", environment.accessibilityRunning)
            .put("keyguardLocked", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            .put("interactive", context.getSystemService(PowerManager::class.java).isInteractive))
        val start = GateMetrics.sample()
        var baseline = start
        val runs = mutableListOf<RunRecord>()
        val samples = JSONArray()
        var budget = pollMs
        var retainedWithinBudget = 0
        for (index in 0 until warmups + iterations) {
            val warmup = index < warmups
            val record = runOnce(scenario, environment, mode, index, warmup)
            val references = record.runtimes + record.engines
            record.clearMs = GateMetrics.awaitCleared(references, budget)
            if (record.clearMs == null && ++retainedWithinBudget >= REDUCED_BUDGET_AFTER) budget = REDUCED_BUDGET_MS
            val sample = GateMetrics.sample()
            if (index == warmups - 1) baseline = sample
            samples.put(record.json().put("after", sample.json()))
            runs += record
        }
        val measured = runs.filter { !it.warmup }
        GateMetrics.awaitCleared(measured.flatMap { it.runtimes + it.engines }, pollMs)
        val final = GateMetrics.sample()
        val retainedRuns = measured.filter { it.retained() }
        val retainedWarmups = runs.filter { it.warmup && it.retained() }
        val frameBytes = if (scenario.capture) context.resources.displayMetrics.let { it.widthPixels.toLong() * it.heightPixels * 4 } else 0L
        val thresholds = JSONObject().put("retained", 0).put("javaHeapDeltaBytes", JAVA_HEAP_LIMIT)
            .put("nativeHeapDeltaBytes", NATIVE_HEAP_LIMIT + frameBytes).put("scriptThreadDelta", 0)
            .put("rhinoContextDelta", 0).put("engines", start.engines)
        val metrics = JSONObject()
            .put("retained", retainedRuns.size)
            .put("retainedWarmups", retainedWarmups.size)
            .put("javaHeapDeltaBytes", final.javaHeapBytes - baseline.javaHeapBytes)
            .put("nativeHeapDeltaBytes", final.nativeHeapBytes - baseline.nativeHeapBytes)
            .put("scriptThreadDelta", final.scriptThreads - start.scriptThreads)
            .put("allThreadDelta", final.allThreads - start.allThreads)
            .put("rhinoContextDelta", if (final.rhinoContexts < 0 || start.rhinoContexts < 0) JSONObject.NULL else final.rhinoContexts - start.rhinoContexts)
            .put("engines", final.engines)
            .put("enginesBefore", start.engines)
            .put("notRemovedEngines", measured.sumOf { it.notRemoved })
            .put("scriptThreadNames", JSONArray(GateMetrics.liveThreads().filter(GateMetrics::isScriptThread).map { it.name }.distinct().take(20)))
        val reasons = JSONArray()
        if (retainedRuns.isNotEmpty()) reasons.put("retained runtimes ${retainedRuns.size}/${measured.size}")
        if (metrics.getLong("javaHeapDeltaBytes") > JAVA_HEAP_LIMIT) reasons.put("java heap +${mb(metrics.getLong("javaHeapDeltaBytes"))} MB")
        if (metrics.getLong("nativeHeapDeltaBytes") > NATIVE_HEAP_LIMIT + frameBytes) reasons.put("native heap +${mb(metrics.getLong("nativeHeapDeltaBytes"))} MB")
        if (metrics.getInt("scriptThreadDelta") > 0) reasons.put("script threads +${metrics.getInt("scriptThreadDelta")}")
        if (final.rhinoContexts >= 0 && start.rhinoContexts >= 0 && final.rhinoContexts > start.rhinoContexts) reasons.put("rhino contexts +${final.rhinoContexts - start.rhinoContexts}")
        if (final.engines > start.engines) reasons.put("live engines ${final.engines} (before ${start.engines})")
        val errors = runs.flatMap { it.errors }.distinct()
        val stopErrors = runs.flatMap { it.stopErrors }.distinct()
        val unstarted = runs.count { !it.started }
        val timedOut = runs.count { it.forcedCleanup }
        if (errors.isNotEmpty()) reasons.put("script errors: " + errors.take(3).joinToString(" | "))
        if (unstarted > 0) reasons.put("runs not started in time $unstarted")
        if (timedOut > 0) reasons.put("runs that did not end in time $timedOut")
        val status = when {
            errors.isNotEmpty() || unstarted > 0 -> "error"
            reasons.length() > 0 -> "fail"
            else -> "pass"
        }
        if (stopErrors.isNotEmpty()) report.put("stopErrors", JSONArray(stopErrors.take(5)))
        report.put("interruptedRuns", runs.count { it.interrupted > 0 })
        report.put("status", status).put("reasons", reasons).put("metrics", metrics).put("thresholds", thresholds)
            .put("start", start.json()).put("baseline", baseline.json()).put("final", final.json()).put("samples", samples)
        if (retainedRuns.isNotEmpty() || retainedWarmups.isNotEmpty()) report.put("heap", heapEvidence(scenario, mode))
        return report
    }

    private fun runOnce(scenario: GateScenario, environment: GateEnvironment, mode: GateMode, index: Int, warmup: Boolean): RunRecord {
        val window = IterationWindow()
        tracker.window = window
        val begun = SystemClock.elapsedRealtime()
        val ready = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val outcome = RunOutcome()
        val errors = outcome.errors
        try {
            launch(scenario, environment, mode, index, ready, started, outcome)
            val startedInTime = started.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            var forceTriggered = false
            if (mode == GateMode.FORCE && startedInTime && errors.isEmpty()) {
                awaitCondition(READY_TIMEOUT_MS) { ready.get() && window.created() >= scenario.expectedEngines }
                SystemClock.sleep(forceDelayMs)
                outcome.stopping.set(true)
                window.forceStopAlive()
                forceTriggered = true
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
                index = index, warmup = warmup, started = startedInTime, forceTriggered = forceTriggered,
                forcedCleanup = forcedCleanup, ended = ended, durationMs = SystemClock.elapsedRealtime() - begun,
                created = window.created(), notRemoved = window.notRemoved(), errors = errors.toList(),
                stopErrors = outcome.stopErrors.toList(), interrupted = outcome.interrupted.get(),
                runtimes = window.runtimeReferences(), engines = window.engineReferences(),
            )
        } finally {
            tracker.window = null
        }
    }

    /** Kept in its own frame so that no local of the test method references the execution. */
    private fun launch(
        scenario: GateScenario, environment: GateEnvironment, mode: GateMode, index: Int,
        ready: AtomicBoolean, started: CountDownLatch, outcome: RunOutcome,
    ) {
        val marker = "memory-gate-${scenario.id}-${mode.label}-$index-${UUID.randomUUID()}"
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                (execution.engine as? JavaScriptEngine)?.runtime?.putProperty(GateScenario.READY_PROPERTY, ready)
                started.countDown()
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                // An interrupt is how exit() and forceStop end a script; exceptions raised while the runner
                // is stopping the script are shutdown noise, kept apart from errors that invalidate the run.
                // zh-CN: 中断是 exit() 与 forceStop 结束脚本的方式; 运行器停止脚本期间抛出的异常属于停止过程的噪声, 与使本次运行无效的错误分开记录.
                val text = (e.javaClass.simpleName + ": " + e.message).take(300)
                when {
                    ScriptInterruptedException.causedByInterrupt(e) -> outcome.interrupted.incrementAndGet()
                    outcome.stopping.get() -> outcome.stopErrors += text
                    else -> outcome.errors += text
                }
                started.countDown()
            }
        }
        val config = ExecutionConfig().apply {
            workingDirectory = environment.workDir
            if (scenario.ui) intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        service.execute(StringScriptSource(marker, scenario.source(environment, mode, marker)), listener, config)
    }

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    // endregion

    // region Scenario requirements and fixtures

    private fun skipReason(scenario: GateScenario): String? = when (scenario.id) {
        "S4a" -> if (captureAllowed) null else "screen capture needs MediaProjection consent for every run; " +
            "pass memoryGateCapture=on once the maintainer has agreed how consent is given"
        "S6" -> if (Settings.canDrawOverlays(context)) null else "the host has no overlay permission"
        "S12" -> pluginSkipReason()
        else -> null
    }

    private fun pluginSkipReason(): String? {
        val name = GateScenarios.PLUGIN_PACKAGE
        try {
            context.packageManager.getPackageInfo(name, 0)
        } catch (_: Exception) {
            return "official plugin $name is not installed"
        }
        if (!PluginEnableStore.isEnabled(context, name, defaultEnabled = true)) return "plugin $name is disabled in Plugin Center"
        if (!PluginTrustManager.isAuthorized(context, name)) return "plugin $name is not authorized"
        return null
    }

    private fun environment(scenario: GateScenario): GateEnvironment {
        val dir = File(context.filesDir, "memory-gate").apply { mkdirs() }
        val big = File(dir, "big.png")
        val small = File(dir, "small.png")
        if (!big.isFile || !small.isFile) writeFixtureImages(big, small)
        val dex = File(dir, "probe.dex").apply { if (!isFile) writeBytes(Base64.decode(PROBE_DEX, Base64.DEFAULT)) }
        val chain = File(dir, "chain.js").apply { writeText(GateScenarios.chainScript(path)) }
        if (scenario.id == "S10" && loopback == null) loopback = GateLoopbackServer()
        return GateEnvironment(
            workDir = dir.path, bigImage = big.path, smallImage = small.path, dexFile = dex.path, chainScript = chain.path,
            httpUrl = loopback?.httpUrl, webSocketUrl = loopback?.webSocketUrl,
            accessibilityRunning = runCatching { AccessibilityService.hasInstance() }.getOrDefault(false),
            pluginPackage = GateScenarios.PLUGIN_PACKAGE,
        )
    }

    private fun writeFixtureImages(big: File, small: File) {
        val bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (y in 0 until 480 step 16) for (x in 0 until 480 step 16) {
            paint.color = Color.rgb((x * 7 + y) % 256, (y * 5 + x / 2) % 256, (x + y * 3) % 256)
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 16f, y + 16f, paint)
        }
        val crop = Bitmap.createBitmap(bitmap, 208, 208, 48, 48)
        big.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        small.outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle()
        bitmap.recycle()
    }

    // endregion

    // region Heap evidence and output

    private fun heapEvidence(scenario: GateScenario, mode: GateMode): JSONObject {
        val result = JSONObject()
        val dir = outputDir()
        val hprof = File(dir, "$runId-${scenario.id}-${mode.label}.hprof")
        try {
            GateMetrics.forceGc()
            val dumpStarted = SystemClock.elapsedRealtime()
            Debug.dumpHprofData(hprof.path)
            result.put("dumpMs", SystemClock.elapsedRealtime() - dumpStarted).put("hprof", hprof.path)
            if (analyzeHeap) {
                result.put("analysis", GateHeapPathAnalyzer.analyze(hprof))
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

    private fun header(scenario: GateScenario, mode: GateMode): JSONObject {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("suite", "memory-gate")
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
            .put("host", JSONObject().put("package", context.packageName).put("versionName", info.versionName).put("versionCode", versionCode))
            .put("pid", Process.myPid())
            .put("recordedWallMs", System.currentTimeMillis())
            .put("methodology", METHODOLOGY)
    }

    private fun emit(report: JSONObject) {
        val text = report.toString()
        val id = "${report.optString("scenario")}-${report.optString("mode")}"
        instrumentation.sendStatus(STATUS_CODE, Bundle().apply { putString(STATUS_KEY, text) })
        val metrics = report.optJSONObject("metrics")
        Log.i(LOG_TAG, "SUMMARY $runId $id ${report.optString("status")} " + (metrics?.let {
            "retained=${it.opt("retained")} javaHeap=${it.opt("javaHeapDeltaBytes")} nativeHeap=${it.opt("nativeHeapDeltaBytes")} " +
                "threads=${it.opt("scriptThreadDelta")} contexts=${it.opt("rhinoContextDelta")} engines=${it.opt("engines")}"
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

    // region Engine tracking

    /** Receives engine lifecycle events for the run in progress; holds no strong reference to an engine. */
    private class EngineTracker : ScriptEngineManager.EngineLifecycleCallback {
        @Volatile
        var window: IterationWindow? = null

        override fun onEngineCreate(engine: ScriptEngine<out ScriptSource>) {
            window?.created(engine)
        }

        override fun onEngineRemove(engine: ScriptEngine<out ScriptSource>) {
            window?.removed(engine)
        }
    }

    private class IterationWindow {
        private class Tracked(engine: ScriptEngine<*>) {
            val engine = WeakReference<ScriptEngine<*>>(engine)

            @Volatile
            var removed = false

            @Volatile
            var runtime: WeakReference<ScriptRuntime>? = null
        }

        private val entries = CopyOnWriteArrayList<Tracked>()

        fun created(engine: ScriptEngine<*>) {
            entries += Tracked(engine)
        }

        fun removed(engine: ScriptEngine<*>) {
            val tracked = entries.firstOrNull { it.engine.get() === engine } ?: return
            tracked.runtime = (engine as? JavaScriptEngine)?.runtime?.let { WeakReference(it) }
            tracked.removed = true
        }

        fun created() = entries.size
        fun allRemoved() = entries.all { it.removed }
        fun notRemoved() = entries.count { !it.removed }
        fun runtimeReferences(): List<WeakReference<*>> = entries.mapNotNull { it.runtime }
        fun engineReferences(): List<WeakReference<*>> = entries.map { it.engine }

        /** Stops the engines still alive on the main thread, as the task list's stop button does. */
        fun forceStopAlive() {
            val alive = entries.filter { !it.removed }.map { it.engine }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                alive.forEach { reference -> reference.get()?.let { runCatching { it.forceStop() } } }
            }
        }
    }

    private class RunOutcome {
        val errors = ConcurrentLinkedQueue<String>()
        val stopErrors = ConcurrentLinkedQueue<String>()
        val interrupted = java.util.concurrent.atomic.AtomicInteger()
        val stopping = AtomicBoolean(false)
    }

    private class RunRecord(
        val index: Int,
        val warmup: Boolean,
        val started: Boolean,
        val forceTriggered: Boolean,
        val forcedCleanup: Boolean,
        val ended: Boolean,
        val durationMs: Long,
        val created: Int,
        val notRemoved: Int,
        val errors: List<String>,
        val stopErrors: List<String>,
        val interrupted: Int,
        val runtimes: List<WeakReference<*>>,
        val engines: List<WeakReference<*>>,
    ) {
        var clearMs: Long? = null

        fun retained() = runtimes.any { it.get() != null } || engines.any { it.get() != null }

        fun json(): JSONObject = JSONObject()
            .put("index", index)
            .put("warmup", warmup)
            .put("started", started)
            .put("forceTriggered", forceTriggered)
            .put("forcedCleanup", forcedCleanup)
            .put("ended", ended)
            .put("durationMs", durationMs)
            .put("engines", created)
            .put("notRemoved", notRemoved)
            .put("clearedMs", clearMs ?: JSONObject.NULL)
            .put("errors", JSONArray(errors))
            .put("stopErrors", JSONArray(stopErrors))
            .put("interrupted", interrupted)
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

        private const val METHODOLOGY = "Same-process runs of real Rhino scripts. Each run's engines are tracked from " +
            "EngineLifecycleCallback.onEngineCreate to onEngineRemove through weak references only. After every run the " +
            "runner calls Runtime.gc and System.runFinalization 3 times and polls up to pollMs (1 s once 3 runs stayed " +
            "reachable); a final collection decides 'retained'. Heap deltas compare the sample after the last warm-up " +
            "with the final sample; thread, Rhino context and engine deltas compare with the sample before the first run. " +
            "Force mode waits for gateReady() (and all expected engines), sleeps forceDelayMs and calls forceStop on every " +
            "live engine of the run on the main thread. Measurements are the temporary test implementation of ROADMAP P0.2."

        // tools/sdk37/fixtures/DexProbe.java (org.autojs.sdk37.fixture.DexProbe.value() == "SDK37_DEX_OK"),
        // the same bytes as AndroidClassLoaderRawDexDeviceTest.
        private val PROBE_DEX = """
            ZGV4CjAzNwCmdjGtDCqEC/aJmOshGxRh7Qcummb5hhXkAgAAcAAAAHhWNBIAAAAAAAAAAFwCAAAKAAAAcAAAAAQAAACYAAAA
            AgAAAKgAAAAAAAAAAAAAAAMAAADAAAAAAQAAANgAAADsAQAA+AAAADABAAA4AQAARwEAAEoBAABeAQAAcgEAAJcBAAClAQAA
            qAEAAK8BAAADAAAABAAAAAUAAAAHAAAAAgAAAAEAAAAAAAAABwAAAAMAAAAAAAAAAAABAAAAAAACAAEAAAAAAAIAAAAIAAAA
            AgAAABEAAAAAAAAAAAAAAAEAAAAAAAAATgIAAAAAAAABAAAAAAAAACgBAAADAAAAGgAGABEAAAABAAEAAQAAACwBAAAEAAAA
            cBAAAAAADgAFAA4AAwAOAAY8aW5pdD4ADURleFByb2JlLmphdmEAAUwAEkxqYXZhL2xhbmcvT2JqZWN0OwASTGphdmEvbGFu
            Zy9TdHJpbmc7ACNMb3JnL2F1dG9qcy9zZGszNy9maXh0dXJlL0RleFByb2JlOwAMU0RLMzdfREVYX09LAAFWAAV2YWx1ZQCc
            AX5+RDh7ImJhY2tlbmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2RlIjoiZGVidWciLCJoYXMtY2hlY2tzdW1zIjpmYWxzZSwi
            bWluLWFwaSI6MjQsInNoYS0xIjoiMDg0YTg5MTI2YWUwNTk2YzU5OTE0M2RmNTdhNjU0NjQ2YWYyODMxMyIsInZlcnNpb24i
            OiI5LjIuNC1kZXYifQAAAAIAAYGABJACAQn4AQsAAAAAAAAAAQAAAAAAAAABAAAACgAAAHAAAAACAAAABAAAAJgAAAADAAAA
            AgAAAKgAAAAFAAAAAwAAAMAAAAAGAAAAAQAAANgAAAABIAAAAgAAAPgAAAADIAAAAgAAACgBAAACIAAACgAAADABAAAAIAAA
            AQAAAE4CAAAAEAAAAQAAAFwCAAA=
        """.trimIndent()
    }
}
