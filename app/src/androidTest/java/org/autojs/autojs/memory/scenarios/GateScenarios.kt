package org.autojs.autojs.memory.scenarios

import org.json.JSONObject

/**
 * How a gate run ends (docs/memory/ROADMAP.md T2): NORMAL lets the script finish by itself,
 * FORCE calls ScriptEngine.forceStop on every engine of the run once the script reports that its
 * workload is active (gateReady) and the force delay has passed.
 * zh-CN: 门禁运行的结束方式 (docs/memory/ROADMAP.md T2): NORMAL 让脚本自行结束, FORCE 在脚本报告工作负载已就绪 (gateReady)
 * 且经过强制停止延迟后, 对本次运行的每个引擎调用 ScriptEngine.forceStop.
 */
enum class GateMode(val label: String, val code: String) {
    NORMAL("normal", "N"),
    FORCE("force", "F"),
    ;

    companion object {
        fun of(label: String) = entries.firstOrNull { it.label == label.trim() }
            ?: throw IllegalArgumentException("Unknown memory gate mode: $label")
    }
}

/**
 * Inputs prepared by the runner. Paths are absolute; URLs are null unless the scenario needs them.
 * zh-CN: 由运行器准备的输入. 路径为绝对路径; 场景不需要时 URL 为 null.
 */
class GateEnvironment(
    val workDir: String,
    val bigImage: String,
    val smallImage: String,
    val dexFile: String,
    val chainScript: String,
    val httpUrl: String?,
    val webSocketUrl: String?,
    val accessibilityRunning: Boolean,
    val pluginPackage: String,
)

class GateScenario(
    val id: String,
    val title: String,
    val hypotheses: String,
    val ui: Boolean = false,
    val capture: Boolean = false,
    val expectedEngines: Int = 1,
    val normalTimeoutMs: Long = 60_000,
    val notes: String = "",
    /** The scenario needs the host accessibility service bound to the test process (skipped otherwise). */
    val needsAccessibility: Boolean = false,
    /** Fixed warm-up / measured run counts for long scenarios, overriding the runner arguments. */
    val warmups: Int? = null,
    val iterations: Int? = null,
    /**
     * Largest allowed growth of these VMDebug instance counts (GateNativeProbe keys) from the end of the warm-ups to the end;
     * judged only where VMDebug is available (ROADMAP P4.4).
     */
    val instanceLimits: Map<String, Long> = emptyMap(),
    /** Largest allowed growth of these host counters (GateNativeProbe.counters keys) from the end of the warm-ups to the end. */
    val counterLimits: Map<String, Long> = emptyMap(),
    /**
     * Largest allowed median growth per measured run of these host counters: a leak grows them in every run, while a one-time
     * step (code of the platform, LeakCanary or the host used for the first time in the process) does not move the median.
     */
    val perRunCounterLimits: Map<String, Long> = emptyMap(),
    /** Script statistics (gateStat) that must stay 0 in every measured run: the script reports what it found wrong. */
    val failingStats: List<String> = emptyList(),
    private val body: (GateEnvironment, GateMode) -> String,
) {
    /**
     * The full script: an optional "ui" directive, the marker comment, the prelude and the body.
     * Force mode always ends with gateReady() and a keep-alive interval, so the script stays alive
     * until the runner stops it.
     * zh-CN: 完整脚本: 可选的 "ui" 指令, 标记注释, 前导与主体. 强制停止模式总以 gateReady() 与保活定时器结尾, 使脚本存活到运行器停止它.
     */
    fun source(environment: GateEnvironment, mode: GateMode, marker: String): String = buildString {
        if (ui) append("\"ui\";\n")
        append("// ").append(marker).append('\n')
        append(PRELUDE).append('\n')
        append("var FORCE = ").append(mode == GateMode.FORCE).append(";\n")
        append(body(environment, mode).trimIndent()).append('\n')
        if (mode == GateMode.FORCE) append("gateReady();\n").append(KEEP_ALIVE).append('\n')
    }

    companion object {
        const val READY_PROPERTY = "memory.gate.ready"
        const val STATS_PROPERTY = "memory.gate.stats"
        const val KEEP_ALIVE = "setInterval(function () {}, 1000);"
        private const val PRELUDE = "function gateReady() { var flag = runtime.getProperty('$READY_PROPERTY'); if (flag) flag.set(true); }\n" +
            "function gateStat(name, value) { var stats = runtime.getProperty('$STATS_PROPERTY'); if (stats) stats.put(String(name), java.lang.Double.valueOf(Number(value))); }"
    }
}

/**
 * Scenario matrix S1-S16 (docs/memory/ROADMAP.md T2; S4 is split into S4a-S4f as in appendix A; S13 / S14 were added by P4.1 / P4.2,
 * S15 / S16 by P4.4). Scripts use ES5 syntax so that every Rhino language level accepts them.
 * zh-CN: 场景矩阵 S1-S16 (docs/memory/ROADMAP.md T2; S4 按附录 A 拆为 S4a-S4f; S13 / S14 由 P4.1 / P4.2 新增, S15 / S16 由 P4.4 新增).
 * 脚本使用 ES5 语法, 以便任何 Rhino 语言级别都能接受.
 */
object GateScenarios {

    const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.imgui"

    private fun q(value: String?) = JSONObject.quote(value.orEmpty())

    val ALL: List<GateScenario> = listOf(
        GateScenario("S1", "empty script", "noise baseline") { _, _ ->
            """
            var gate = 1;
            """
        },
        GateScenario(
            "S2", "threads.start x 10 + threads.pool", "H1 / H2 / H15",
            notes = "N: workers sleep 50 ms and the pool is shut down after queueing; F: workers sleep 60 s and the pool stays open",
        ) { _, mode ->
            """
            var HOLD = ${if (mode == GateMode.FORCE) 60000 else 50};
            for (var i = 0; i < 10; i++) {
                threads.start(function () { sleep(HOLD); });
            }
            var pool = threads.pool({ corePoolSize: 2, maxPoolSize: 2 });
            for (var j = 0; j < 4; j++) {
                pool.execute(function () { sleep(HOLD); });
            }
            if (!FORCE) pool.shutdown();
            """
        },
        GateScenario("S3", "nested timers on main and child threads", "H1 / H2") { _, _ ->
            """
            var ticks = 0;
            var interval = setInterval(function () {
                if (++ticks >= 5 && !FORCE) clearInterval(interval);
            }, 30);
            threads.start(function () {
                setTimeout(function () {
                    setTimeout(function () {}, 30);
                    if (FORCE) setInterval(function () {}, 50);
                }, 30);
            });
            setTimeout(function () { setImmediate(function () {}); }, 30);
            """
        },
        GateScenario(
            "S4a", "captureScreen x 20", "H5 / H11 / H14", capture = true, normalTimeoutMs = 120_000,
            notes = "needs MediaProjection consent for every run, see memoryGateCapture",
        ) { _, _ ->
            """
            if (!images.requestScreenCapture()) throw new Error('memory gate: screen capture was not granted');
            gateReady();
            var frames = 0;
            while (FORCE || frames < 20) {
                var shot = images.captureScreen();
                frames++;
                if (FORCE) sleep(50);
            }
            """
        },
        GateScenario("S4b", "images.read + findImage", "H11 / H14") { env, _ ->
            """
            var big = images.read(${q(env.bigImage)});
            var small = images.read(${q(env.smallImage)});
            if (!big || !small) throw new Error('memory gate: fixture images are missing');
            gateReady();
            for (var i = 0; FORCE || i < 5; i++) {
                var point = images.findImage(big, small);
                if (FORCE) sleep(20);
            }
            """
        },
        GateScenario("S4c", "images.copy / clip / resize chain", "H11 / H14") { env, _ ->
            """
            var source = images.read(${q(env.bigImage)});
            if (!source) throw new Error('memory gate: fixture image is missing');
            gateReady();
            for (var i = 0; FORCE || i < 10; i++) {
                var copied = images.copy(source);
                var clipped = images.clip(copied, 0, 0, 100, 100);
                var resized = images.resize(clipped, [50, 50]);
                if (FORCE) sleep(20);
            }
            """
        },
        GateScenario("S4d", "matchTemplate + oneShot", "H11 / H14") { env, _ ->
            """
            var small = images.read(${q(env.smallImage)});
            if (!small) throw new Error('memory gate: fixture image is missing');
            gateReady();
            for (var i = 0; FORCE || i < 5; i++) {
                var result = images.matchTemplate(images.read(${q(env.bigImage)}).oneShot(), small, { max: 3 });
                if (FORCE) sleep(20);
            }
            """
        },
        GateScenario(
            "S4e", "threaded copy + findImage + recycle (issue #372 / #200 pattern)", "H11 / H14 / H1",
            notes = "finite version: 4 threads x 10 rounds on a fixture image instead of screen frames",
        ) { env, _ ->
            """
            var big = images.read(${q(env.bigImage)});
            var small = images.read(${q(env.smallImage)});
            if (!big || !small) throw new Error('memory gate: fixture images are missing');
            for (var t = 0; t < 4; t++) {
                threads.start(function () {
                    for (var i = 0; FORCE || i < 10; i++) {
                        var copied = images.copy(big);
                        images.findImage(copied, small);
                        copied.recycle();
                        if (FORCE) sleep(10);
                    }
                });
            }
            """
        },
        GateScenario(
            "S4f", "captureScreen x 200 without recycle (issue #204)", "H5 / H14 / issue #204", capture = true,
            normalTimeoutMs = 900_000, warmups = 1, iterations = 3,
            notes = "N: 200 frames per run, none recycled explicitly; the script fails when a single capture takes longer than 15 s " +
                "or when more than one media Image is open after a capture; F: capture until stopped",
        ) { _, _ ->
            """
            if (!images.requestScreenCapture()) throw new Error('memory gate: screen capture was not granted');
            gateReady();
            var Wrapper = Packages.org.autojs.autojs.core.image.ImageWrapper;
            var frames = 0, nulls = 0, slowest = 0, maxOpen = 0, total = Date.now();
            while (FORCE || frames < 200) {
                var begun = Date.now();
                var shot = images.captureScreen();
                var took = Date.now() - begun;
                frames++;
                if (!shot) nulls++;
                if (took > slowest) slowest = took;
                var open = Wrapper.openMediaImageCount();
                if (open > maxOpen) maxOpen = open;
                gateStat('frames', frames);
                gateStat('nullFrames', nulls);
                gateStat('slowestCaptureMs', slowest);
                gateStat('maxOpenMediaImages', maxOpen);
                if (took > 15000) throw new Error('memory gate: captureScreen took ' + took + ' ms (issue #204)');
                if (open > 1) throw new Error('memory gate: ' + open + ' media images are open after a capture');
            }
            gateStat('totalMs', Date.now() - total);
            """
        },
        GateScenario(
            "S5", "ui script with button, text and webview", "H10 / H13", ui = true,
            notes = "N: the script finishes its Activity; the run ends at ScriptExecuteActivity.onDestroy",
        ) { _, _ ->
            """
            ui.layout(
                <vertical>
                    <button id="gateButton" text="memory gate"/>
                    <text id="gateText" text="memory gate"/>
                    <webview id="gateWeb" h="120"/>
                </vertical>
            );
            ui.gateWeb.loadData('<html><body>memory gate</body></html>', 'text/html', 'utf-8');
            gateReady();
            if (!FORCE) setTimeout(function () { activity.finish(); }, 300);
            """
        },
        GateScenario("S6", "floaty.window + floaty.rawWindow", "H13 / FloatyService.windows") { _, _ ->
            """
            var window = floaty.window(<frame><text id="gateText" text="memory gate"/></frame>);
            var raw = floaty.rawWindow(<frame><text text="memory gate raw"/></frame>);
            ui.run(function () {
                window.setPosition(0, 200);
                raw.setPosition(0, 400);
            });
            gateReady();
            if (!FORCE) setTimeout(function () { window.close(); raw.close(); }, 300);
            """
        },
        GateScenario(
            "S7", "events / key observer / sensors / accessibility events", "H9 / H16",
            notes = "N: the script ends with exit() because the listeners keep it resident; key and accessibility parts need the host accessibility service",
        ) { env, _ ->
            """
            events.on('exit', function () {});
            var emitter = events.emitter();
            emitter.on('gate', function () {});
            emitter.emit('gate');
            var sensor = sensors.register('accelerometer', sensors.delay.normal);
            if (sensor) sensor.on('change', function () {});
            if (${env.accessibilityRunning}) {
                auto.registerEvent('window_state_changed', function (event) {});
                events.observeKey();
                events.onKeyDown('volume_up', function (event) {});
            }
            gateReady();
            if (!FORCE) setTimeout(function () { exit(); }, 300);
            """
        },
        GateScenario(
            "S7b", "className(...).find() loop on the accessibility tree", "H14 (AccessibilityNodeInfo)", needsAccessibility = true,
            notes = "needs the host accessibility service; N: 30 rounds of find / findOnce / children / parent; F: rounds until stopped",
        ) { _, _ ->
            """
            var nodes = 0;
            gateReady();
            for (var round = 0; FORCE || round < 30; round++) {
                var found = className('android.widget.FrameLayout').find();
                nodes += found.size();
                var first = className('android.widget.FrameLayout').findOnce();
                if (first) {
                    var children = first.children();
                    nodes += children.size();
                    if (first.parent()) nodes++;
                }
                var texts = className('android.widget.TextView').find();
                nodes += texts.size();
                gateStat('nodes', nodes);
                if (FORCE) sleep(20);
            }
            """
        },
        GateScenario(
            "S8", "JavaAdapter / interface adapters x 100 + runtime.loadDex of a fresh copy", "H3",
            notes = "since P4.4 every run loads its own copy of the DEX (a new path, as a script loading changing files would); " +
                "DexClassLoader instances and the host's registered loaders must not grow, loaded classes not in most runs",
            instanceLimits = mapOf("dexClassLoader" to 0L),
            counterLimits = mapOf("hostDexLoaders" to 0L),
            perRunCounterLimits = mapOf("loadedClasses" to 0L),
        ) { env, _ ->
            """
            for (var i = 0; i < 100; i++) {
                new java.lang.Runnable({ run: function () {} }).run();
                new JavaAdapter(java.lang.Runnable, { run: function () {} }).run();
            }
            var dexSource = new java.io.File(${q(env.dexFile)});
            var dexCopy = new java.io.File(dexSource.getParentFile(), 's8-' + java.lang.System.nanoTime() + '-' + dexSource.getName());
            files.copy(dexSource.getPath(), dexCopy.getPath());
            runtime.loadDex(dexCopy.getPath());
            dexCopy.delete();
            var probe = Packages.org.autojs.sdk37.fixture.DexProbe.value();
            if (String(probe) !== 'SDK37_DEX_OK') throw new Error('memory gate: dex probe returned ' + probe);
            """
        },
        GateScenario(
            "S9", "engines.execScriptFile restarting itself 3 levels", "H6 / issue #548", expectedEngines = 4,
            notes = "the root starts chain.js, which starts itself until depth 3; F stops all four engines",
        ) { env, _ ->
            """
            engines.execScriptFile(${q(env.chainScript)}, { arguments: { depth: 1, force: FORCE } });
            """
        },
        GateScenario("S10", "http.get + WebSocket on a loopback server", "CoreWebSocket / okhttp") { env, _ ->
            """
            var keep = setInterval(function () {}, 1000);
            var response = http.get(${q(env.httpUrl)});
            var body = response.body.string();
            if (String(body) !== 'memory-gate') throw new Error('memory gate: unexpected http body ' + body);
            var socket = web.newWebSocket(${q(env.webSocketUrl)});
            socket.on('open', function (res, ws) { ws.send('memory-gate'); });
            socket.on('text', function (text, ws) {
                gateReady();
                if (!FORCE) ws.close(1000, 'done');
            });
            socket.on('closed', function () { if (!FORCE) clearInterval(keep); });
            socket.on('failure', function () { clearInterval(keep); });
            if (!FORCE) setTimeout(function () { clearInterval(keep); }, 5000);
            """
        },
        GateScenario("S11", "device.* reads, vibrate and keepScreenOn", "H7") { _, _ ->
            """
            var readings = [device.getBattery(), device.isCharging(), device.getTotalMem(), device.getAvailMem(),
                device.width, device.height, device.sdkInt, device.getMusicVolume(), device.isScreenOn()];
            device.vibrate(20);
            device.keepScreenOn(300);
            device.cancelKeepingAwake();
            """
        },
        GateScenario(
            "S12", "plugins.load of an installed official plugin", "H8",
            notes = "uses 3-ImGui ($PLUGIN_PACKAGE), the official plugin that keeps the plugins.load registry",
        ) { env, _ ->
            """
            var plugin = plugins.load(${q(env.pluginPackage)});
            if (!plugin) throw new Error('memory gate: plugins.load returned nothing');
            """
        },
        GateScenario(
            "S13", "speech.listen session open when the script ends", "H23",
            notes = "external source, 200 ms of silence fed after ready, no microphone; N cancels the session, F stops the script with it open",
        ) { _, _ ->
            """
            var session = speech.listen({ source: 'external' });
            var guard = FORCE ? null : setTimeout(function () { session.cancel(); }, 10000);
            session.on('ready', function () {
                gateStat('ready', 1);
                session.feed(new Int16Array(3200));
                if (!FORCE) session.cancel();
            });
            session.on('error', function () { gateStat('errors', 1); });
            session.on('end', function () { if (guard !== null) clearTimeout(guard); });
            """
        },
        GateScenario(
            "S15", "web.newInjectableWebView not attached to a window when the script ends", "H10 / Q42",
            notes = "the WebView is created on the main thread (ui.run) and loads a small page; the runtime destroys it at exit (P4.4)",
            instanceLimits = mapOf("webView" to 0L),
        ) { _, _ ->
            """
            var gateView = ui.run(function () {
                var view = web.newInjectableWebView(context, null);
                view.loadData('<html><body>memory gate</body></html>', 'text/html', 'utf-8');
                return view;
            });
            gateStat('webViews', gateView ? 1 : 0);
            gateReady();
            sleep(300);
            """
        },
        GateScenario(
            "S16", "a UI script starting a UI script that ends first", "H27 (main thread Context)", ui = true, expectedEngines = 2,
            normalTimeoutMs = 90_000,
            notes = "both run on the main thread and share its Rhino Context; the outer one checks with memory.collect() that the " +
                "ended inner runtime is reclaimed while it still runs (P4.4); F stops both",
            failingStats = listOf("innerRetainedWhileOuterRuns"),
        ) { _, _ ->
            """
            ui.layout(<frame><text text="memory gate outer"/></frame>);
            // The outer Activity is the root of the task the runner started, so a plain NEW_TASK start of the same Activity would only
            // bring that task to the front; MULTIPLE_TASK gives the inner script an Activity of its own (both still on the main
            // thread). No XML literal in the inner source: the XML preprocessing of the outer script would rewrite it inside the string.
            var innerConfig = new org.autojs.autojs.execution.ExecutionConfig();
            innerConfig.setIntentFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            var inner = runtime.engines.execScript('memory-gate-inner', '"ui";\nsetTimeout(function () { activity.finish(); }, 200);', innerConfig);
            threads.start(function () {
                var engine = null;
                for (var i = 0; i < 400 && !(engine = inner.getEngine()); i++) sleep(25);
                if (!engine) return;
                for (var j = 0; j < 800 && !engine.isDestroyed(); j++) sleep(25);
                engine = null;
                memory.collect();
                var retained = memory.retained(), held = 0;
                for (var k = 0; k < retained.length; k++) if (String(retained[k].name).indexOf('memory-gate-inner') >= 0) held++;
                gateStat('innerChecks', 1);
                gateStat('innerRetainedWhileOuterRuns', held);
                // ui.post would wait on this thread's timer, which ends with the thread.
                if (!FORCE) activity.runOnUiThread(function () { activity.finish(); });
            });
            """
        },
        GateScenario(
            "S14", "console.show with exitOnClose, then the script ends", "ConsoleImpl.sShowingConsoles",
            notes = "the console window closes 500 ms after the script ends (exitOnClose); P4.2 coverage of appendix B.1",
        ) { _, _ ->
            """
            console.setExitOnClose(500);
            console.show();
            console.log('memory gate');
            sleep(300);
            """
        },
    )

    /** Written to the work directory for S9; it restarts itself through engines.execScriptFile. */
    fun chainScript(path: String) = """
        var argv = engines.myEngine().execArgv || {};
        var depth = Number(argv.depth) || 1;
        if (depth < 3) engines.execScriptFile(${q(path)}, { arguments: { depth: depth + 1, force: !!argv.force } });
        if (argv.force) setInterval(function () {}, 1000);
    """.trimIndent()

    fun byId(id: String) = ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown memory gate scenario: $id")
}
