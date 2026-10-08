package org.autojs.autojs.runtime.api.augment.compose

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.open.RunIntentActivity
import org.autojs.autojs.script.StringScriptSource
import org.autojs.plugin.compose.api.ComposeUiIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Opt-in observations of real scripts, not a microbenchmark or a release performance gate.
 * Semantics detection includes UiAutomation latency. Pixel confirmation crosses two display
 * callbacks and verifies a solid color rendered in the same tree as the label. Its timestamp
 * is an instrumented visibility upper bound, not a presentation timestamp or pure Compose time.
 * Run each cold sample in a separate process using .python/compose_performance.py.
 */
@RunWith(AndroidJUnit4::class)
class ComposePerformanceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val service get() = AutoJs.instance.scriptEngineService
    private val samples get() = integerArgument("composePerfSamples", 10, 1, 50)
    private val warmups get() = integerArgument("composePerfWarmups", 2, 1, 5)
    private val pollMs get() = integerArgument("composePerfPollMs", 8, 1, 100).toLong()
    private val runId get() = arguments.getString("composePerfRunId") ?: "manual"
    private var prepared = false
    private var previousEnabled = false
    private var previousFlags = 0

    @Before fun prepare() {
        assumeTrue("Performance collection is opt-in", arguments.getString("composePerfEnabled") == "true")
        assertTrue("Only the isolated spike host may be benchmarked", context.packageName.endsWith(".compose.spike"))
        assertTrue("The run ID is a plain label", runId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        context.packageManager.getPackageInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0)
        previousEnabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        instrumentation.uiAutomation.serviceInfo.also { info ->
            previousFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            instrumentation.uiAutomation.serviceInfo = info
        }
        prepared = true
    }

    @After fun restore() {
        if (!prepared) return
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, previousEnabled)
        instrumentation.uiAutomation.serviceInfo.also { info ->
            info.flags = previousFlags
            instrumentation.uiAutomation.serviceInfo = info
        }
    }

    @Test fun coldUiFirstFrame() {
        requireFreshProcess()
        Running(Kind.UI_COUNTER).use { running ->
            val observed = running.observe(0)
            emit("first_frame", integerArgument("composePerfColdIndex", 0, 0, 99), running.firstFrame(observed)
                .put("temperature", "process_cold_first_compose_mount")
                .put("coldDefinition", "Runner stopped the isolated host before instrumentation; first Compose use by this harness. APK/disk caches are not cleared."))
        }
    }

    @Test fun warmUiFirstFrames() {
        repeat(warmups) { Running(Kind.UI_COUNTER).use { it.observe(0) } }
        repeat(samples) { index ->
            Running(Kind.UI_COUNTER).use { running ->
                emit("first_frame", index, running.firstFrame(running.observe(0))
                    .put("temperature", "same_process_new_engine_after_warmups").put("warmups", warmups))
            }
        }
    }

    @Test fun stateUpdatesAndBatchedRenders() {
        assertTrue("Grant the isolated host overlay permission before collecting floaty observations", Settings.canDrawOverlays(context))
        for (kind in listOf(Kind.UI_COUNTER, Kind.FLOATY_COUNTER)) {
            if (kind == Kind.FLOATY_COUNTER) goHome()
            Running(kind).use { running ->
                running.observe(0)
                var value = 0
                repeat(warmups) { running.click("increment"); running.observe(++value) }
                repeat(samples) { index ->
                    val before = running.renders.get()
                    val clickStarted = now()
                    running.click("increment")
                    val clickReturned = now()
                    val observed = running.observe(++value)
                    emit("state_update", index, observed.json(running.mutationStarted.get())
                        .put("surface", kind.surface).put("renderCount", running.renders.get() - before)
                        .put("clickActionNs", clickReturned - clickStarted)
                        .put("clickToPixelConfirmedNs", observed.confirmedAtNs - clickStarted)
                        .put("mutationLoopNs", running.mutationFinished.get() - running.mutationStarted.get())
                        .put("lastRenderBuildNs", running.lastRenderBuildNs.get()))
                }
                repeat(samples) { index ->
                    val before = running.renders.get()
                    running.click("batch")
                    value += 100
                    val observed = running.observe(value)
                    emit("batch_100", index, observed.json(running.mutationStarted.get())
                        .put("surface", kind.surface).put("assignmentsInOneCallback", 100)
                        .put("renderCount", running.renders.get() - before)
                        .put("mutationLoopNs", running.mutationFinished.get() - running.mutationStarted.get())
                        .put("lastRenderBuildNs", running.lastRenderBuildNs.get()))
                }
            }
        }
    }

    @Test fun thousandItemListFirstFrameAndScrollFrames() {
        repeat(warmups) { Running(Kind.UI_LIST).use { it.observe(0) } }
        repeat(samples) { index ->
            Running(Kind.UI_LIST).use { running ->
                val observation = running.observe(0)
                emit("list_first_frame", index, running.firstFrame(observation)
                    .put("items", 1000).put("temperature", "same_process_new_engine_after_list_warmups")
                    .put("warmups", warmups))
                // One reproducible gesture trace is collected per list mount; no screenshot or
                // accessibility-tree polling takes place inside this frame-collection interval.
                val activity = requireNotNull(running.engine.getTag("activity") as? Activity)
                val listBounds = requireNotNull(snapshot(running.tag("list"))).bounds
                val swipes = integerArgument("composePerfScrollSwipes", 12, 1, 40)
                val firstIndexBefore = running.firstVisible.get()
                FrameCollector(activity.window).use { frames ->
                    val started = now()
                    frames.start()
                    repeat(swipes) { swipe(listBounds); SystemClock.sleep(120) }
                    SystemClock.sleep(750)
                    val data = frames.stop()
                    assertTrue("Real scrolling produced frame metrics", data.getJSONArray("frames").length() > 0)
                    assertTrue("The gestures moved the real lazy list", running.firstVisible.get() > firstIndexBefore)
                    emit("list_scroll", index, data.put("durationNs", now() - started).put("items", 1000)
                        .put("swipes", swipes).put("nominalSwipeMs", 360).put("pauseBetweenSwipesMs", 120)
                        .put("flingTailMs", 750).put("firstVisibleBefore", firstIndexBefore)
                        .put("firstVisibleAfter", running.firstVisible.get()))
                }
            }
        }
    }

    /**
     * F.6 decision input: one visible state change while a 1000-row list stays mounted.
     * The render layer rebuilds the whole tree in JavaScript and relies on host snapshot
     * comparison; the handle layer updates the two observed nodes directly. Both trees
     * contain the same label, swatch, button and rows, and no row changes in either path.
     */
    @Test fun thousandItemListStateUpdates() {
        for (kind in listOf(Kind.UI_LIST_RENDER_UPDATE, Kind.UI_LIST_HANDLE_UPDATE)) {
            Running(kind).use { running ->
                running.observe(0)
                var value = 0
                repeat(warmups) { running.click("increment"); running.observe(++value) }
                repeat(samples) { index ->
                    val before = running.renders.get()
                    val clickStarted = now()
                    running.click("increment")
                    val clickReturned = now()
                    val observed = running.observe(++value)
                    emit(requireNotNull(kind.updateMetric), index, observed.json(running.mutationStarted.get())
                        .put("surface", kind.surface).put("items", 1000).put("warmups", warmups)
                        .put("renderCount", running.renders.get() - before)
                        .put("clickActionNs", clickReturned - clickStarted)
                        .put("clickToPixelConfirmedNs", observed.confirmedAtNs - clickStarted)
                        .put("mutationLoopNs", running.mutationFinished.get() - running.mutationStarted.get())
                        .put("lastRenderBuildNs", running.lastRenderBuildNs.get()))
                }
            }
        }
    }

    @Test fun hostPssAcrossFreshLoaderAndOwnedSessions() {
        requireFreshProcess()
        assertTrue("Grant the isolated host overlay permission before collecting PSS", Settings.canDrawOverlays(context))
        // Avoid screenshots in this process so bitmap capture buffers do not distort the delta.
        memory("instrumented_host_before_script")
        Running(Kind.EMPTY_UI).use { running ->
            running.await("The baseline UI engine has a native content view") {
                var attached = false
                instrumentation.runOnMainSync { attached = running.engine.runtime.ui.view?.isAttachedToWindow == true }
                attached
            }
            crossDisplayFrames()
            memory("empty_native_ui_engine")
        }
        memory("after_empty_ui_close")
        Running(Kind.UI_COUNTER).use { running -> running.observe(0, pixels = false); memory("first_compose_ui") }
        memory("after_compose_ui_close")
        goHome()
        Running(Kind.FLOATY_COUNTER).use { running -> running.observe(0, pixels = false); memory("warm_compose_floaty") }
        memory("after_floaty_close")
        Running(Kind.UI_LIST).use { running -> running.observe(0, pixels = false); memory("warm_compose_1000_item_list") }
        memory("after_list_close")
    }

    private fun memory(stage: String) {
        val settleMs = integerArgument("composePerfMemorySettleMs", 500, 0, 5000).toLong()
        SystemClock.sleep(settleMs)
        repeat(integerArgument("composePerfMemorySamples", 5, 1, 20)) { index ->
            val info = Debug.MemoryInfo()
            val started = now()
            Debug.getMemoryInfo(info)
            emit("host_pss", index, JSONObject().put("stage", stage).put("settleMs", settleMs)
                .put("totalPssKb", info.totalPss).put("dalvikPssKb", info.dalvikPss)
                .put("nativePssKb", info.nativePss).put("otherPssKb", info.otherPss)
                .put("privateDirtyKb", info.totalPrivateDirty).put("privateCleanKb", info.totalPrivateClean)
                .put("measurementNs", now() - started).put("forcedGc", false)
                .put("interpretation", "Whole instrumented host PSS; includes engine, retained classloader, native/managed allocations and runtime noise. Post-close PSS is not a leak measurement."))
            SystemClock.sleep(100)
        }
    }

    private fun requireFreshProcess() {
        assertEquals("Use the runner for a fresh host process", "true", arguments.getString("composePerfFreshProcess"))
        assertTrue("Only one cold or PSS entry is allowed per process", claimedFreshProcess.compareAndSet(false, true))
    }

    private enum class Kind(val surface: String, val updateMetric: String? = null) {
        UI_COUNTER("ui"), FLOATY_COUNTER("floaty"), UI_LIST("ui"), EMPTY_UI("ui"),
        UI_LIST_RENDER_UPDATE("ui", "list_render_update"), UI_LIST_HANDLE_UPDATE("ui", "list_handle_update"),
    }

    private inner class Running(private val kind: Kind) : Closeable {
        private val marker = "compose-perf-${UUID.randomUUID()}"
        // Give each mount its own colors: the preceding Activity's transition/task snapshot
        // must not satisfy a new mount's first-frame confirmation with an identical swatch.
        private val colorSalt = UUID.randomUUID().hashCode() and 0x00FFFFFF
        private val palette = PALETTE.map { color ->
            String.format(Locale.ROOT, "#%06X", (Color.parseColor(color) xor colorSalt) and 0x00FFFFFF)
        }.toTypedArray()
        private val started = CountDownLatch(1)
        private val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        private val failure = AtomicReference<Throwable?>()
        private val stopping = AtomicBoolean()
        private val logs = ConcurrentLinkedQueue<String>()
        private var registered = false
        private var execution: ScriptExecution? = null
        val mountStarted = AtomicLong()
        val mountReturned = AtomicLong()
        val mutationStarted = AtomicLong()
        val mutationFinished = AtomicLong()
        val renders = AtomicInteger()
        val firstVisible = AtomicInteger()
        val lastRenderBuildNs = AtomicLong()
        val engine get() = requireNotNull(engineRef.get())
        val launchStarted: Long
        private val logListener = object : ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) { logs += logEntry.content.toString() }
            override fun onLogClear() = Unit
        }
        private val listener = object : SimpleScriptExecutionListener() {
            private fun owned(execution: ScriptExecution) = (execution.source as? StringScriptSource)?.script?.contains(marker) == true
            override fun onStart(execution: ScriptExecution) {
                if (!owned(execution)) return
                val candidate = execution.engine as? RhinoJavaScriptEngine
                if (candidate == null) failure.set(AssertionError("The benchmark script did not use Rhino")) else {
                    engineRef.set(candidate)
                    mapOf("mountStarted" to mountStarted, "mountReturned" to mountReturned,
                        "mutationStarted" to mutationStarted, "mutationFinished" to mutationFinished,
                        "renders" to renders, "firstVisible" to firstVisible, "renderBuildNs" to lastRenderBuildNs)
                        .forEach { (key, value) -> candidate.runtime.putProperty("compose.perf.$key", value) }
                    (candidate.runtime.console as? ConsoleImpl)?.addLogListener(logListener)
                }
                started.countDown()
            }
            override fun onException(execution: ScriptExecution, error: Throwable) {
                if (!owned(execution)) return
                if (!stopping.get()) failure.compareAndSet(null, error)
                started.countDown()
            }
        }

        init {
            val source = source(kind, marker, palette)
            launchStarted = now()
            try {
                if (kind != Kind.FLOATY_COUNTER) {
                    registered = true
                    service.registerGlobalScriptExecutionListener(listener)
                    instrumentation.runOnMainSync {
                        context.startActivity(Intent(context, RunIntentActivity::class.java)
                            .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, source)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else execution = service.execute(StringScriptSource(marker, source), listener, ExecutionConfig())
                assertTrue("The owned script engine starts", started.await(45, TimeUnit.SECONDS))
                healthy()
                assertNotNull(engineRef.get())
            } catch (error: Throwable) { close(); throw error }
        }

        fun tag(suffix: String) = "$marker-$suffix"
        fun click(suffix: String) {
            val node = requireNotNull(findNode(tag(suffix))) { "Missing benchmark control $suffix" }
            assertTrue("The real benchmark control accepts click", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        fun healthy() {
            failure.get()?.let { throw AssertionError("Benchmark script failed", it) }
            logs.firstOrNull { it.startsWith("Compose UI: ") }?.let { throw AssertionError(it) }
        }
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 45000
            do {
                healthy()
                if (condition()) return
                SystemClock.sleep(pollMs)
            } while (SystemClock.uptimeMillis() < deadline)
            fail("$message; logs=${logs.joinToString(" | ")}")
        }
        fun observe(value: Int, pixels: Boolean = true): Observation {
            var queries = 0
            var queryNs = 0L
            var semanticsAt = 0L
            await("The new value is exposed by the actual renderer") {
                val probeStart = now()
                val current = snapshot(tag("value"))
                queryNs += now() - probeStart
                queries++
                (current?.visible == true && current.text == "Value: $value").also { if (it) semanticsAt = now() }
            }
            val framesStarted = now()
            crossDisplayFrames()
            val framesNs = now() - framesStarted
            var captureNs = 0L
            var captures = 0
            if (pixels) {
                val expected = Color.parseColor(palette[value % palette.size])
                await("The same rendered value reaches a screenshot pixel") {
                    val probeStart = now()
                    val swatch = snapshot(tag("swatch"))
                    queryNs += now() - probeStart
                    queries++
                    if (swatch?.visible != true || swatch.bounds.isEmpty) false else {
                        val captureStart = now()
                        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        val matches = try {
                            val x = swatch.bounds.centerX()
                            val y = swatch.bounds.centerY()
                            x in 0 until bitmap.width && y in 0 until bitmap.height && colorsMatch(expected, bitmap.getPixel(x, y))
                        } finally { bitmap.recycle() }
                        captureNs += now() - captureStart
                        captures++
                        matches
                    }
                }
            }
            return Observation(semanticsAt, now(), queries, queryNs, framesNs, captureNs, captures, pixels)
        }
        fun firstFrame(observed: Observation): JSONObject = observed.json(mountStarted.get())
            .put("surface", kind.surface).put("launchToSemanticsNs", observed.semanticsAtNs - launchStarted)
            .put("launchToPixelConfirmedNs", observed.confirmedAtNs - launchStarted)
            .put("mountCallNs", mountReturned.get() - mountStarted.get())
            .put("lastRenderBuildNs", lastRenderBuildNs.get()).put("renderCount", renders.get())
            .put("swatchPalette", JSONArray(palette.toList()))

        override fun close() {
            try {
                stopping.set(true)
                val owned = engineRef.get() ?: execution?.engine as? RhinoJavaScriptEngine
                if (owned != null) {
                    if (!owned.isDestroyed) {
                        val activity = owned.getTag("activity") as? Activity
                        if (activity != null) instrumentation.runOnMainSync { activity.finish() } else owned.forceStop()
                        val deadline = SystemClock.uptimeMillis() + 15000
                        while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
                        if (!owned.isDestroyed) owned.forceStop()
                    }
                    (owned.runtime.console as? ConsoleImpl)?.removeLogListener(logListener)
                    owned.runtime.floaty.closeAll()
                }
            } finally {
                if (registered) service.unregisterGlobalScriptExecutionListener(listener)
                instrumentation.waitForIdleSync()
            }
        }
    }

    private data class Observation(val semanticsAtNs: Long, val confirmedAtNs: Long, val queries: Int,
        val queryNs: Long, val displayBarrierNs: Long, val screenshotNs: Long, val screenshots: Int, val pixelConfirmed: Boolean) {
        fun json(start: Long): JSONObject {
            check(start > 0 && semanticsAtNs >= start)
            return JSONObject().put("startElapsedNs", start).put("semanticsObservedElapsedNs", semanticsAtNs)
                .put("pixelConfirmedElapsedNs", confirmedAtNs).put("toSemanticsNs", semanticsAtNs - start)
                .put("toPixelConfirmedNs", confirmedAtNs - start).put("semanticsQueries", queries)
                .put("semanticsQueryNs", queryNs).put("twoDisplayCallbacksNs", displayBarrierNs)
                .put("screenshotNs", screenshotNs).put("screenshots", screenshots).put("pixelConfirmed", pixelConfirmed)
        }
    }

    private fun source(kind: Kind, marker: String, palette: Array<String>): String {
        if (kind == Kind.EMPTY_UI) return "'ui';\n// $marker\nui.layout(<frame bg=\"#FFFFFF\"/>);"
        if (kind == Kind.UI_LIST_HANDLE_UPDATE) return handleListSource(marker, palette)
        val prefix = if (kind == Kind.FLOATY_COUNTER) "" else "'ui';\n"
        val density = context.resources.displayMetrics.density
        val width = (260 * density).roundToInt().coerceAtMost(context.resources.displayMetrics.widthPixels - (32 * density).roundToInt())
        val mount = if (kind == Kind.FLOATY_COUNTER) "compose.floaty(render, {raw:true,x:${(16 * density).roundToInt()},y:${(80 * density).roundToInt()},width:$width,height:${(240 * density).roundToInt()}})" else "compose.mount(render)"
        val content = if (kind == Kind.UI_LIST) """
            var rows = [];
            for (var i = 0; i < 1000; i++) rows.push(compose.Text({key:'item-' + i,text:'Item ' + i,
                modifier:compose.modifier().fillMaxWidth().height(48).padding(8)}));
            var contents = compose.Column({padding:12,spacing:8,modifier:compose.modifier().fillMaxSize()},
                label, swatch, compose.LazyColumn({key:'list',testTag:'$marker-list',
                    modifier:compose.modifier().weight(1).fillMaxWidth(),onScroll:function (index) { firstVisible.set(index); }}, rows));
        """ else if (kind == Kind.UI_LIST_RENDER_UPDATE) """
            var rows = [];
            for (var i = 0; i < 1000; i++) rows.push(compose.Text({key:'item-' + i,text:'Item ' + i,
                modifier:compose.modifier().fillMaxWidth().height(48).padding(8)}));
            var contents = compose.Column({padding:12,spacing:8,modifier:compose.modifier().fillMaxSize()},
                label, swatch, compose.Row({key:'controls',spacing:8},
                    compose.Button({key:'increment',testTag:'$marker-increment',onClick:function () { update(1); }}, 'Update')),
                compose.LazyColumn({key:'list',testTag:'$marker-list',
                    modifier:compose.modifier().weight(1).fillMaxWidth()}, rows));
        """ else """
            var contents = compose.Column({padding:16,spacing:8}, label, swatch,
                compose.Row({key:'controls',spacing:8},
                    compose.Button({key:'increment',testTag:'$marker-increment',onClick:function () { update(1); }}, 'Update'),
                    compose.Button({key:'batch',testTag:'$marker-batch',onClick:function () { update(100); }}, '100 updates')));
        """
        return prefix + """
            // $marker
            var mountStarted = runtime.getProperty('compose.perf.mountStarted');
            var mountReturned = runtime.getProperty('compose.perf.mountReturned');
            var mutationStarted = runtime.getProperty('compose.perf.mutationStarted');
            var mutationFinished = runtime.getProperty('compose.perf.mutationFinished');
            var renders = runtime.getProperty('compose.perf.renders');
            var firstVisible = runtime.getProperty('compose.perf.firstVisible');
            var renderBuildNs = runtime.getProperty('compose.perf.renderBuildNs');
            function nowNs() { return android.os.SystemClock.elapsedRealtimeNanos(); }
            var value = compose.state(0);
            var palette = ${JSONArray(palette.toList())};
            function update(count) {
                mutationStarted.set(nowNs());
                for (var i = 0; i < count; i++) value.value++;
                mutationFinished.set(nowNs());
            }
            function render() {
                var began = nowNs();
                renders.incrementAndGet();
                var current = value.value;
                var label = compose.Text({key:'value',testTag:'$marker-value',text:'Value: ' + current});
                var swatch = compose.Box({key:'swatch',testTag:'$marker-swatch',contentDescription:'Frame ' + current,
                    w:32,h:32,bg:palette[current % palette.length]});
                $content
                var root = compose.Surface({modifier:compose.modifier().fillMaxSize()}, contents);
                renderBuildNs.set(nowNs() - began);
                return root;
            }
            mountStarted.set(nowNs());
            var session = $mount;
            mountReturned.set(nowNs());
        """.trimIndent()
    }

    /** The same 1000-row tree as [Kind.UI_LIST_RENDER_UPDATE], built once and updated through handles. */
    private fun handleListSource(marker: String, palette: Array<String>): String = """
        'ui';
        // $marker
        var mountStarted = runtime.getProperty('compose.perf.mountStarted');
        var mountReturned = runtime.getProperty('compose.perf.mountReturned');
        var mutationStarted = runtime.getProperty('compose.perf.mutationStarted');
        var mutationFinished = runtime.getProperty('compose.perf.mutationFinished');
        function nowNs() { return android.os.SystemClock.elapsedRealtimeNanos(); }
        var value = 0;
        var palette = ${JSONArray(palette.toList())};
        var label = compose.Text({key:'value',testTag:'$marker-value',text:'Value: 0'});
        var swatch = compose.Box({key:'swatch',testTag:'$marker-swatch',contentDescription:'Frame 0',
            w:32,h:32,bg:palette[0]});
        function update(count) {
            mutationStarted.set(nowNs());
            value += count;
            label.set({text:'Value: ' + value});
            swatch.set({contentDescription:'Frame ' + value,bg:palette[value % palette.length]});
            mutationFinished.set(nowNs());
        }
        var rows = [];
        for (var i = 0; i < 1000; i++) rows.push(compose.Text({key:'item-' + i,text:'Item ' + i,
            modifier:compose.modifier().fillMaxWidth().height(48).padding(8)}));
        var contents = compose.Column({padding:12,spacing:8,modifier:compose.modifier().fillMaxSize()},
            label, swatch, compose.Row({key:'controls',spacing:8},
                compose.Button({key:'increment',testTag:'$marker-increment',onClick:function () { update(1); }}, 'Update')),
            compose.LazyColumn({key:'list',testTag:'$marker-list',
                modifier:compose.modifier().weight(1).fillMaxWidth()}, rows));
        var root = compose.Surface({modifier:compose.modifier().fillMaxSize()}, contents);
        mountStarted.set(nowNs());
        var session = compose.mount(root);
        mountReturned.set(nowNs());
    """.trimIndent()

    /** Android's dropCount is lost metric reports, not dropped display frames. */
    private inner class FrameCollector(private val window: Window) : Closeable {
        private val thread = HandlerThread("compose-performance-frame-metrics").apply { start() }
        private val handler = Handler(thread.looper)
        private val collecting = AtomicBoolean()
        private val listenerRegistered = AtomicBoolean()
        private val closed = AtomicBoolean()
        private val recorded = ConcurrentLinkedQueue<Pair<FrameMetrics, Int>>()
        private val recordCount = AtomicInteger()
        private val overflow = AtomicInteger()
        private val refreshRate: Double
        private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, droppedReports ->
            if (collecting.get()) {
                if (recordCount.getAndIncrement() < 10000) recorded.add(FrameMetrics(metrics) to droppedReports) else overflow.incrementAndGet()
            }
        }
        init {
            var rate = 60.0
            instrumentation.runOnMainSync {
                @Suppress("DEPRECATION")
                rate = window.windowManager.defaultDisplay.refreshRate.toDouble()
                window.addOnFrameMetricsAvailableListener(listener, handler)
                listenerRegistered.set(true)
            }
            refreshRate = rate
        }
        fun start() { collecting.set(true) }
        fun stop(): JSONObject {
            removeListener()
            val drained = CountDownLatch(1)
            handler.post { collecting.set(false); drained.countDown() }
            assertTrue("Frame metric delivery drains", drained.await(10, TimeUnit.SECONDS))
            val frames = JSONArray()
            recorded.forEach { (metrics, dropped) ->
                frames.put(JSONObject().put("totalNs", metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                    .put("drawNs", metrics.getMetric(FrameMetrics.DRAW_DURATION))
                    .put("layoutMeasureNs", metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
                    .put("unknownDelayNs", metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION))
                    .put("firstDraw", metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L)
                    .put("deadlineNs", if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L)
                    .put("metricReportsDropped", dropped))
            }
            return JSONObject().put("frames", frames).put("refreshRateHz", refreshRate)
                .put("collectionOverflow", overflow.get()).put("noScreenshotsDuringTrace", true)
                .put("interpretation", "FrameMetrics TOTAL_DURATION is render-and-issue time. Deadline misses or refresh-period excess estimate rendering jank; they are not measured presentation drops. Lost metric reports are separate.")
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            collecting.set(false)
            try { removeListener() } finally {
                thread.quitSafely()
                thread.join(5000)
            }
        }
        private fun removeListener() {
            instrumentation.runOnMainSync {
                if (listenerRegistered.compareAndSet(true, false)) window.removeOnFrameMetricsAvailableListener(listener)
            }
        }
    }

    private fun swipe(bounds: Rect) {
        val x = bounds.exactCenterX()
        val startY = bounds.top + bounds.height() * 0.85f
        val endY = bounds.top + bounds.height() * 0.20f
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float, sync: Boolean) {
            val item = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            item.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue("A real list gesture is accepted", instrumentation.uiAutomation.injectInputEvent(item, sync)) }
            finally { item.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, startY, true)
        for (step in 1..30) {
            val wait = down + step * 12 - SystemClock.uptimeMillis()
            if (wait > 0) SystemClock.sleep(wait)
            event(MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 30f, false)
        }
        event(MotionEvent.ACTION_UP, endY, true)
    }

    private data class NodeSnapshot(val text: String?, val bounds: Rect, val visible: Boolean)
    private fun snapshot(tag: String): NodeSnapshot? = findNode(tag)?.let { node ->
        NodeSnapshot(node.text?.toString(), Rect().also(node::getBoundsInScreen), node.isVisibleToUser)
    }
    private fun findNode(tag: String): AccessibilityNodeInfo? {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.windows.mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.forEach(queue::add)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.refresh()) continue
            if (node.viewIdResourceName == tag) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
        }
        return null
    }
    private fun crossDisplayFrames() {
        val frames = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            Choreographer.getInstance().postFrameCallback {
                Choreographer.getInstance().postFrameCallback { frames.countDown() }
            }
        }
        assertTrue("Two public display callbacks complete", frames.await(10, TimeUnit.SECONDS))
    }
    private fun goHome() {
        assertTrue("The overlay benchmark uses the desktop", instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        instrumentation.waitForIdleSync()
    }
    private fun integerArgument(name: String, fallback: Int, minimum: Int, maximum: Int): Int =
        (arguments.getString(name)?.toIntOrNull() ?: fallback).also { require(it in minimum..maximum) { "$name is out of range" } }
    private fun emit(metric: String, sample: Int, fields: JSONObject) {
        val data = JSONObject().put("schemaVersion", 1).put("suite", "compose-ui-p5.3").put("metric", metric)
            .put("sample", sample).put("runId", runId).put("pid", Process.myPid()).put("recordedWallMs", System.currentTimeMillis())
            .put("api", Build.VERSION.SDK_INT).put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())).put("process64Bit", Process.is64Bit())
            .put("host", packageVersion(context.packageName)).put("plugin", packageVersion(ComposeUiIds.PLUGIN_PACKAGE_NAME))
            .put("pollMs", pollMs).put("fields", fields)
            .put("methodology", "Real Rhino scripts and installed plugin. Timing includes atomic stamps, host validation, rendering and instrument observation. Pixel timestamps include tree polling, two display callbacks and screenshot capture; not pure renderer cost. No performance gate is enforced.")
        instrumentation.sendStatus(0, Bundle().apply { putString("compose_perf_json", data.toString()) })
    }
    private fun packageVersion(name: String): JSONObject {
        val info = context.packageManager.getPackageInfo(name, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val debuggable = (info.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        return JSONObject().put("package", name).put("versionName", info.versionName).put("versionCode", code)
            .put("debuggable", debuggable)
    }
    private fun colorsMatch(expected: Int, actual: Int): Boolean = abs(Color.red(expected) - Color.red(actual)) <= 3 &&
        abs(Color.green(expected) - Color.green(actual)) <= 3 && abs(Color.blue(expected) - Color.blue(actual)) <= 3

    companion object {
        private val claimedFreshProcess = AtomicBoolean()
        private val PALETTE = arrayOf("#B3261E", "#006C4C", "#005AC1", "#7D5260", "#625B71", "#9A4523", "#006874")
        private fun now() = SystemClock.elapsedRealtimeNanos()
    }
}
