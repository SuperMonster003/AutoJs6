package org.autojs.autojs.memory.bridge

import org.autojs.autojs.memory.scenarios.GateMode

/**
 * The plugin engine of a scenario (docs/memory-phase2/ROADMAP.md T2): its plugin package, the classes the heap analysis looks for
 * after a retained run (T4), and the host-side session classes counted after every run (T1 (a)).
 * zh-CN: 场景所用的插件引擎 (docs/memory-phase2/ROADMAP.md T2): 插件包名, 有残留时堆分析查找的类 (T4), 以及每次运行后计数的宿主侧会话类 (T1 (a)).
 */
internal enum class BridgeEngine(
    val pluginPackage: String,
    val heapClasses: List<String>,
    /**
     * Session classes (kind -> class name) whose reachable instances VMDebug counts after every run, for engines whose session
     * objects BridgeExecutionObserver does not see in full: since P2.2 the BridgeExecutionScope of Bun and QuickJS reports their
     * broker and output callback (T6), while the automation bridge, the call executor and the provider of Bun are seen only through
     * this count. Node reports its broker and provider, so it counts nothing.
     * zh-CN: 会话类 (类别 -> 类名), 每次运行后经 VMDebug 计数其可达实例; 用于 BridgeExecutionObserver 看不全会话对象的引擎: P2.2 起 Bun 与
     * QuickJS 的 BridgeExecutionScope 报告其 broker 与输出回调 (T6), Bun 的 automation bridge, call executor 与 provider 仍只经此计数.
     * Node 会报告 broker 与 provider, 因此不计数.
     */
    val sessionClasses: Map<String, String>,
) {
    NODE(
        "io.github.supermonster003.autojs6.plugin.nodejs",
        listOf(
            "org.autojs.autojs.engine.NodeJsHostCapabilityBroker",
            "org.autojs.autojs.engine.AndroidNodeBridgeCapabilityProvider",
            "org.autojs.autojs.engine.NodePluginJavaScriptEngine",
            "org.autojs.autojs.runtime.ScriptRuntime",
        ),
        emptyMap(),
    ),
    BUN(
        "io.github.supermonster003.autojs6.plugin.bun.runtime",
        listOf(
            "org.autojs.autojs.engine.BunHostCapabilityBroker",
            "org.autojs.autojs.engine.BunPluginScriptEngine\$OutputCallback",
            "org.autojs.autojs.engine.BunAutomationBridge",
            "org.autojs.autojs.engine.BunHostCallExecutor",
            "org.autojs.autojs.engine.AndroidNodeBridgeCapabilityProvider",
            "org.autojs.autojs.engine.BunPluginScriptEngine",
        ),
        linkedMapOf(
            "bun.broker" to "org.autojs.autojs.engine.BunHostCapabilityBroker",
            "bun.callback" to "org.autojs.autojs.engine.BunPluginScriptEngine\$OutputCallback",
            "bun.automation" to "org.autojs.autojs.engine.BunAutomationBridge",
            "bun.callExecutor" to "org.autojs.autojs.engine.BunHostCallExecutor",
            "bun.provider" to "org.autojs.autojs.engine.AndroidNodeBridgeCapabilityProvider",
        ),
    ),
    QUICKJS(
        "io.github.supermonster003.autojs6.plugin.quickjs.runtime",
        listOf(
            "org.autojs.autojs.engine.QuickJsPluginScriptEngine\$OutputCallback",
            "org.autojs.autojs.engine.QuickJsPluginScriptEngine",
        ),
        linkedMapOf("quickjs.callback" to "org.autojs.autojs.engine.QuickJsPluginScriptEngine\$OutputCallback"),
    ),
}

/**
 * One scenario of the bridge leak gate (docs/memory-phase2/ROADMAP.md T2): a script for a plugin engine plus what the runner
 * does around it. [body] runs after the prelude of [BridgeScenarios.source]: inside an async function for Node and Bun, at the
 * top level inside try for QuickJS.
 * zh-CN: 桥接泄漏门禁的一个场景 (docs/memory-phase2/ROADMAP.md T2): 插件引擎脚本, 以及运行器在其前后要做的事. [body] 在
 * [BridgeScenarios.source] 的前导代码之后运行: Node 与 Bun 在一个 async 函数中, QuickJS 在顶层的 try 中.
 */
internal class BridgeScenario(
    val id: String,
    val title: String,
    /** Suspect numbers of ROADMAP 1.3. zh-CN: 路线图 1.3 的疑点编号. */
    val hypotheses: String,
    val body: String,
    val notes: String = "",
    val engine: BridgeEngine = BridgeEngine.NODE,
    val expectedEngines: Int = 1,
    val normalTimeoutMs: Long = 60_000,
    /** The runner kills the plugin process once the script reports gateReady (T2 X4), in both modes. zh-CN: 脚本报告 gateReady 后运行器杀掉插件进程 (T2 X4), 两种模式都是. */
    val killPluginOnReady: Boolean = false,
    /** The runner force-stops the plugin package before every run (ND13). zh-CN: 运行器在每次运行前强制停止插件包 (ND13). */
    val forceStopPluginBeforeRun: Boolean = false,
    /** Runs on ColorOS only (OPPO / OnePlus / realme); other devices skip and record. zh-CN: 只在 ColorOS 上运行, 其他设备跳过并记录. */
    val colorOsOnly: Boolean = false,
    val needsOverlay: Boolean = false,
    /** Needs memoryGateCapture=on (run_gate.py allows PROJECT_MEDIA). zh-CN: 需要 memoryGateCapture=on (run_gate.py 放行 PROJECT_MEDIA). */
    val capture: Boolean = false,
    /** Android permissions the host must hold; run_gate.py grants them (D11). zh-CN: 宿主须持有的 Android 权限; 由 run_gate.py 授予 (D11). */
    val permissions: List<String> = emptyList(),
    /** Script exceptions are the expected outcome (the plugin process dies), not errors. zh-CN: 脚本异常是预期结果 (插件进程死亡), 不算 error. */
    val expectsFailure: Boolean = false,
    /** Bridge capabilities declared in package.json (autojs6.node.permissions). zh-CN: 在 package.json 中声明的桥接能力 (autojs6.node.permissions). */
    val capabilities: List<String> = emptyList(),
    /**
     * Bun host capability grants the runner turns on for the scenario and restores afterwards (off by default, such as sensors.control).
     * zh-CN: 运行器为该场景打开并在结束后还原的 Bun 宿主能力授权 (默认关闭的, 如 sensors.control).
     */
    val bunGrants: List<String> = emptyList(),
    /**
     * File name of the script in the working directory; the runner writes the source there and runs a JavaScriptFileSource
     * instead of a string source.
     * zh-CN: 脚本在工作目录中的文件名; 运行器把源码写到该文件, 以 JavaScriptFileSource 代替字符串源运行.
     */
    val sourceFile: String? = null,
    /** Size of the GATE_DATA string literal in KiB (QuickJS only). zh-CN: GATE_DATA 字符串字面量的大小, 单位 KiB (仅 QuickJS). */
    val sourceDataKb: Int = 0,
)

internal object BridgeScenarios {

    const val READY_LINE = "AJ6MEM_READY"
    const val STAT_PREFIX = "AJ6MEM_STAT "
    const val SKIP_PREFIX = "AJ6MEM_SKIP "
    const val ERROR_PREFIX = "AJ6MEM_ERROR "

    /**
     * The source of a run for the engine of [scenario]. Bun and QuickJS print to the global console, which every execution
     * shares, so their gate lines start with [marker] and the runner keeps only those.
     * zh-CN: 按 [scenario] 的引擎生成一次运行的源码. Bun 与 QuickJS 输出到所有执行共用的全局控制台, 因此其门禁行以 [marker] 开头,
     * 运行器只读取这些行.
     */
    fun source(scenario: BridgeScenario, mode: GateMode, marker: String, png: String): String = when (scenario.engine) {
        BridgeEngine.NODE -> nodeSource(scenario, mode, marker, png)
        BridgeEngine.BUN -> bunSource(scenario, mode, marker, png)
        BridgeEngine.QUICKJS -> quickJsSource(scenario, mode, marker, png)
    }

    /**
     * The Node source of a run: the engine directive, the gate helpers and [body] in an async function whose rejection prints
     * an AJ6MEM_ERROR line and sets a failing exit code. Output lines reach the host console one by one (stdout events), which
     * is how the runner reads gateReady / gateStat / gateSkip.
     * zh-CN: 一次运行的 Node 源码: 引擎指令, 门禁辅助函数, 以及放在 async 函数中的 [body]; 函数被拒绝时打印 AJ6MEM_ERROR 行并设置失败退出码.
     * 输出行逐行到达宿主控制台 (stdout 事件), 运行器由此读取 gateReady / gateStat / gateSkip.
     */
    fun nodeSource(scenario: BridgeScenario, mode: GateMode, marker: String, png: String): String = """
        |"nodejs";
        |const GATE_MODE = "${mode.label}";
        |const GATE_MARKER = "$marker";
        |const GATE_PNG = "$png";
        |function gateReady() { console.log("$READY_LINE"); }
        |function gateStat(name, value) { console.log("$STAT_PREFIX" + name + " " + value); }
        |function gateSkip(reason) { console.log("$SKIP_PREFIX" + reason); }
        |function gateSleep(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
        |async function gateHold() {
        |    if (GATE_MODE !== "force") return;
        |    gateReady();
        |    setInterval(() => {}, 1000);
        |    await new Promise(() => {});
        |}
        |(async () => {
        |${scenario.body.trimIndent()}
        |})().catch((e) => {
        |    console.error("$ERROR_PREFIX" + String((e && e.stack) || e).split("\n").slice(0, 4).join(" | "));
        |    process.exitCode = 1;
        |});
        |""".trimMargin()

    /**
     * The Bun source of a run: as [nodeSource], with marked gate lines and gateHost(), which returns the host client of
     * AUTOJS6_HOST_CLIENT_MODULE (null when the plugin does not provide one).
     * zh-CN: 一次运行的 Bun 源码: 同 [nodeSource], 门禁行带标记; gateHost() 返回 AUTOJS6_HOST_CLIENT_MODULE 的宿主客户端 (插件未提供时为 null).
     */
    fun bunSource(scenario: BridgeScenario, mode: GateMode, marker: String, png: String): String = """
        |"bun";
        |const GATE_MODE = "${mode.label}";
        |const GATE_MARKER = "$marker";
        |const GATE_PNG = "$png";
        |function gateReady() { console.log(GATE_MARKER + " $READY_LINE"); }
        |function gateStat(name, value) { console.log(GATE_MARKER + " $STAT_PREFIX" + name + " " + value); }
        |function gateSkip(reason) { console.log(GATE_MARKER + " $SKIP_PREFIX" + reason); }
        |function gateSleep(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
        |async function gateHost() {
        |    const module = process.env.AUTOJS6_HOST_CLIENT_MODULE;
        |    if (!module) return null;
        |    const { createHostClient } = await import(module);
        |    return await createHostClient();
        |}
        |async function gateHold() {
        |    if (GATE_MODE !== "force") return;
        |    gateReady();
        |    setInterval(() => {}, 1000);
        |    await new Promise(() => {});
        |}
        |(async () => {
        |${scenario.body.trimIndent()}
        |})().catch((e) => {
        |    console.error(GATE_MARKER + " $ERROR_PREFIX" + String((e && e.stack) || e).split("\n").slice(0, 4).join(" | "));
        |    process.exitCode = 1;
        |});
        |""".trimMargin()

    /**
     * The QuickJS source of a run. The plugin has console and print only (no timers, no host API), so gateBusy waits by spinning
     * (the interrupt handler stops it on forceStop) and gateHold of F spins until the stop. [body] runs at the top level inside
     * try (a module when the source name ends with .mjs); GATE_DATA holds [BridgeScenario.sourceDataKb] KiB of text.
     * zh-CN: 一次运行的 QuickJS 源码. 插件只有 console 与 print (没有定时器与宿主 API), 因此 gateBusy 以空转等待 (forceStop 时由中断处理器停止),
     * F 的 gateHold 空转直到被停止. [body] 在顶层的 try 中运行 (源名以 .mjs 结尾时为模块); GATE_DATA 为 [BridgeScenario.sourceDataKb] KiB 的文本.
     */
    fun quickJsSource(scenario: BridgeScenario, mode: GateMode, marker: String, png: String): String {
        val data = if (scenario.sourceDataKb <= 0) "" else png.repeat(scenario.sourceDataKb * 1024 / png.length + 1).take(scenario.sourceDataKb * 1024)
        return """
            |"quickjs";
            |const GATE_MODE = "${mode.label}";
            |const GATE_MARKER = "$marker";
            |const GATE_PNG = "$png";
            |const GATE_DATA = "$data";
            |function gateReady() { console.log(GATE_MARKER + " $READY_LINE"); }
            |function gateStat(name, value) { console.log(GATE_MARKER + " $STAT_PREFIX" + name + " " + value); }
            |function gateSkip(reason) { console.log(GATE_MARKER + " $SKIP_PREFIX" + reason); }
            |function gateError(e) { console.error(GATE_MARKER + " $ERROR_PREFIX" + String((e && e.stack) || e).split("\n").slice(0, 4).join(" | ")); }
            |function gateBusy(ms) { const end = Date.now() + ms; while (Date.now() < end) {} }
            |function gateHold() {
            |    if (GATE_MODE !== "force") return;
            |    gateReady();
            |    for (;;) gateBusy(1000);
            |}
            |try {
            |${scenario.body.trimIndent()}
            |} catch (e) {
            |    gateError(e);
            |    throw e;
            |}
            |""".trimMargin()
    }

    private val all = listOf(
        BridgeScenario(
            "ND1", "Empty script", "C1 C2 C13",
            notes = "X1: one line, then the script ends; the smallest bind / release path and the noise baseline",
            body = """
                console.log("memory gate " + GATE_MARKER);
                await gateHold();
            """,
        ),
        BridgeScenario(
            "ND2", "Host capabilities through the broker", "C1 C2 C5",
            notes = "X2: device.info + toast.showToast + a 64x64 PNG round trip (image.fromBase64 -> toBytes -> recycle)",
            capabilities = listOf("device", "toast", "image"),
            body = """
                const device = require("device");
                const toast = require("toast");
                const image = require("image");
                const info = await device.info();
                gateStat("deviceInfoKeys", Object.keys(info || {}).length);
                await toast.showToast("memory gate ND2");
                const handle = await image.fromBase64(GATE_PNG);
                const bytes = await image.toBytes(handle, "png");
                gateStat("pngBytes", (bytes && (bytes.byteLength || bytes.length)) || 0);
                await gateHold();
                await image.recycle(handle);
            """,
        ),
        BridgeScenario(
            "ND3", "Pushed callbacks", "C1 C2 C13",
            notes = "X3: autojs6:events battery_changed (sticky, pushed once) + accelerometer subscription; " +
                "N waits for 3 sensor events (5 s at most), F stops while the subscription is open",
            capabilities = listOf("events", "sensors"),
            body = """
                const events = require("autojs6:events");
                const sensors = require("sensors");
                let battery = 0;
                events.once("battery_changed", () => { battery++; });
                let count = 0;
                let done;
                const three = new Promise((resolve) => { done = resolve; });
                const subscription = sensors.subscribe("accelerometer", () => { if (++count >= 3) done(); }, { samplingIntervalMs: 50 });
                await Promise.race([three, gateSleep(5000)]);
                gateStat("sensorEvents", count);
                await gateHold();
                await subscription.close();
                gateStat("batteryEvents", battery);
                await events.close();
            """,
        ),
        BridgeScenario(
            "ND4", "Plugin process killed", "C1 C2 C16",
            notes = "X4: the runner force-stops the plugin package once the script is ready; the host takes the binderDied path " +
                "and the run ends with an exception (expected); F stops forceDelayMs after the kill",
            normalTimeoutMs = 90_000, killPluginOnReady = true, expectsFailure = true,
            body = """
                gateReady();
                await gateSleep(5000);
            """,
        ),
        BridgeScenario(
            "ND5", "UI session", "C7",
            notes = "NodeBridgeUiProvider / NodeBridgeUiActivity: ui.showLayout of a Column with one Text, then close",
            capabilities = listOf("ui"),
            body = """
                const ui = require("ui");
                const handle = await ui.showLayout({ type: "Column", children: [{ type: "Text", text: "memory gate ND5" }] });
                await gateSleep(300);
                await gateHold();
                await handle.close();
            """,
        ),
        BridgeScenario(
            "ND6", "Compose", "C6 C7",
            notes = "RuntimeComposeBridge / RuntimeComposeActivity: compose.mount(Text), then close; skipped when compose.isAvailable() is false",
            capabilities = listOf("ui"),
            body = """
                const compose = require("compose");
                if (!(await compose.isAvailable())) { gateSkip("compose.isAvailable() is false (Block Compose plugin missing or disabled)"); return; }
                const session = await compose.mount(compose.Text({ text: "memory gate ND6" }));
                await gateSleep(300);
                await gateHold();
                await session.close();
            """,
        ),
        BridgeScenario(
            "ND7a", "Web page and action", "C4 C7",
            notes = "RuntimeWebView with a local page + one RuntimeWebActionActivity action (chooseFiles, cancelled by cancelFileChooser)",
            capabilities = listOf("ui"),
            body = """
                const ui = require("ui");
                const web = await ui.createView("webview");
                const window = await ui.showLayout(web);
                await web.loadHtml("<html><body><p>memory gate ND7a</p></body></html>");
                const chooser = web.chooseFiles().catch(() => null);
                await gateSleep(500);
                await gateHold();
                await web.cancelFileChooser();
                const chosen = await chooser;
                gateStat("chooserCancelled", chosen == null ? 1 : 0);
                await window.close();
                await web.dispose();
            """,
        ),
        BridgeScenario(
            "ND7b", "Web local download", "C4",
            notes = "RuntimeWebLocalDownloads: download of a data: URL, then cancelDownload (removes the job and its file)",
            capabilities = listOf("ui"),
            body = """
                const ui = require("ui");
                const web = await ui.createView("webview");
                const window = await ui.showLayout(web);
                await web.loadHtml("<html><body><p>memory gate ND7b</p></body></html>");
                const job = await web.download({ url: "data:text/plain;base64,SGk=", fileName: "aj6mem-" + GATE_MARKER + ".txt", timeoutMs: 10000 });
                await gateSleep(300);
                const state = await web.getDownload(job.id);
                gateStat("downloadDone", state && state.status === "successful" ? 1 : 0);
                await gateHold();
                await web.cancelDownload(job.id);
                await window.close();
                await web.dispose();
            """,
        ),
        BridgeScenario(
            "ND7c", "Web media session", "C3 C4",
            notes = "RuntimeWebMediaService through WebView background recording (camera, 1 s, no audio); needs the CAMERA permission",
            permissions = listOf("android.permission.CAMERA"),
            capabilities = listOf("ui"),
            body = """
                const ui = require("ui");
                const web = await ui.createView("webview");
                const window = await ui.showLayout(web);
                await web.loadHtml("<html><body><p>memory gate ND7c</p></body></html>");
                // The host starts only while the WebView is shown and its window has focus, which comes a moment after showLayout.
                for (let attempt = 1; ; attempt++) {
                    try {
                        await web.startBackgroundRecording({ audio: false, width: 320, height: 240, durationLimitSeconds: 5 });
                        gateStat("startAttempts", attempt);
                        break;
                    } catch (e) {
                        if (attempt >= 20 || !String(e && e.message).includes("WebView is visible")) throw e;
                        await gateSleep(250);
                    }
                }
                await gateSleep(1000);
                await gateHold();
                await web.stopBackgroundRecording().catch(() => null);
                await web.stopMediaCapture();
                await window.close();
                await web.dispose();
            """,
        ),
        BridgeScenario(
            "ND8", "Screen capture", "C6",
            notes = "NodeScreenCaptureSession: requestScreenCapture, 5 frames (each recycled), stopScreenCapture; memoryGateCapture=on",
            capture = true,
            capabilities = listOf("image", "screen_capture"),
            body = """
                const image = require("image");
                if (!(await image.requestScreenCapture())) throw new Error("requestScreenCapture returned false");
                for (let i = 0; i < 5; i++) {
                    const frame = await image.captureScreen();
                    await image.recycle(frame);
                }
                await gateHold();
                await image.stopScreenCapture();
            """,
        ),
        BridgeScenario(
            "ND9", "Overlay", "C6",
            notes = "ControlledOverlaySession: ui.overlay.show of one Text, then close; needs the overlay permission (as S6)",
            needsOverlay = true,
            capabilities = listOf("ui.overlay"),
            body = """
                const overlayModule = require("ui.overlay");
                const overlay = overlayModule.default || overlayModule;
                const handle = await overlay.show({
                    content: { type: "Text", text: "memory gate ND9" },
                    disclosure: { title: "AutoJs6 memory gate", text: "ND9 overlay" },
                });
                await gateSleep(300);
                await gateHold();
                await handle.close();
            """,
        ),
        BridgeScenario(
            "ND10", "Dialog", "C7",
            notes = "NodeDialogs: dialogs.progress.show, then dismiss by the script",
            capabilities = listOf("dialogs"),
            body = """
                const dialogs = require("dialogs");
                const progress = await dialogs.progress.show({ title: "memory gate ND10" });
                await gateSleep(300);
                await gateHold();
                await progress.dismiss();
            """,
        ),
        BridgeScenario(
            "ND11", "Image handles", "C5",
            notes = "imageHandles: 20 handles from a PNG, one OCR input, 10 recycled and 10 left to the end of the execution",
            capabilities = listOf("image", "ocr"),
            body = """
                const image = require("image");
                const handles = [];
                for (let i = 0; i < 20; i++) handles.push(await image.fromBase64(GATE_PNG));
                const ocr = require("ocr");
                const text = await ocr.recognizeText(handles[0]).then(() => 1, () => 0);
                gateStat("ocrDone", text);
                for (let i = 0; i < 10; i++) await image.recycle(handles[i]);
                await gateHold();
            """,
        ),
        BridgeScenario(
            "ND12", "Audio recording", "C3",
            notes = "NodeAudioRecordingService: recorder.start, 1 s, stop; RECORD_AUDIO granted by run_gate.py (D11)",
            permissions = listOf("android.permission.RECORD_AUDIO"),
            capabilities = listOf("media", "media.recording"),
            body = """
                const recorder = require("recorder");
                await recorder.start({ path: "aj6mem-nd12.m4a", maxDurationMs: 5000 });
                await gateSleep(1000);
                await gateHold();
                await recorder.stop();
            """,
        ),
        BridgeScenario(
            "ND13", "ColorOS bind refusal loop", "C1 C16",
            notes = "OPD2413 only: the runner force-stops the Node plugin before every run, so the first bindService is refused and " +
                "PluginAutoWake binds again (phase one Q46); other devices skip and record",
            colorOsOnly = true, forceStopPluginBeforeRun = true, normalTimeoutMs = 90_000,
            capabilities = listOf("device"),
            body = """
                const device = require("device");
                await device.info();
                await gateHold();
            """,
        ),
        BridgeScenario(
            "BN1", "Empty script", "C13",
            notes = "X1: one line, then the script ends; the payload file, the output callback and the broker of one execution",
            engine = BridgeEngine.BUN,
            body = """
                console.log(GATE_MARKER + " memory gate");
                await gateHold();
            """,
        ),
        BridgeScenario(
            "BN2", "Host capabilities through the host client", "C13",
            notes = "X2: device.info + toast + a 64x64 PNG round trip (images.fromBase64 -> readPixels 4x4 -> toBytes png -> recycle) " +
                "through the host client of AUTOJS6_HOST_CLIENT_MODULE",
            engine = BridgeEngine.BUN,
            body = """
                const host = await gateHost();
                if (!host) { gateSkip("AUTOJS6_HOST_CLIENT_MODULE is not set"); return; }
                const info = await host.device.info();
                gateStat("deviceInfoKeys", Object.keys(info || {}).length);
                await host.toast("memory gate BN2");
                const image = await host.images.fromBase64(GATE_PNG);
                try {
                    const pixels = await host.images.readPixels(image, { region: [0, 0, 4, 4] });
                    gateStat("pixels", (pixels && pixels.data && pixels.data.length) || 0);
                    const bytes = await host.images.toBytes(image, "png");
                    gateStat("pngBytes", (bytes && (bytes.byteLength || bytes.length)) || 0);
                    await gateHold();
                } finally {
                    await host.images.recycle(image);
                }
            """,
        ),
        BridgeScenario(
            "BN3", "Sensor callbacks", "C13",
            notes = "X3: accelerometer subscription of the host client (sensors.control granted by the runner, off by default); " +
                "N waits for 3 events (5 s at most), F stops while the subscription is open",
            engine = BridgeEngine.BUN,
            bunGrants = listOf("sensors.control"),
            body = """
                const host = await gateHost();
                if (!host) { gateSkip("AUTOJS6_HOST_CLIENT_MODULE is not set"); return; }
                if (!host.hasCapability("sensors.control")) { gateSkip("sensors.control is not granted"); return; }
                let count = 0;
                let done;
                const three = new Promise((resolve) => { done = resolve; });
                const subscription = host.sensors.subscribe("accelerometer", () => { if (++count >= 3) done(); }, { samplingIntervalMs: 50, drainIntervalMs: 200 });
                try {
                    await subscription.ready;
                    await Promise.race([three, gateSleep(5000)]);
                    gateStat("sensorEvents", count);
                    await gateHold();
                } finally {
                    await subscription.close();
                }
            """,
        ),
        BridgeScenario(
            "BN4", "Plugin process killed", "C13 C16",
            notes = "X4: device.info, then the runner force-stops the Bun plugin package once the script is ready; the run ends " +
                "with an exception (expected); F stops forceDelayMs after the kill",
            engine = BridgeEngine.BUN,
            normalTimeoutMs = 90_000, killPluginOnReady = true, expectsFailure = true,
            body = """
                const host = await gateHost();
                if (host) await host.device.info();
                gateReady();
                await gateSleep(5000);
            """,
        ),
        BridgeScenario(
            "BN5", "ColorOS bind refusal loop", "C13 C16",
            notes = "OPD2413 only: the runner force-stops the Bun plugin before every run, so the first bindService is refused and " +
                "PluginAutoWake binds again (as ND13); other devices skip and record",
            engine = BridgeEngine.BUN,
            colorOsOnly = true, forceStopPluginBeforeRun = true, normalTimeoutMs = 90_000,
            body = """
                const host = await gateHost();
                if (host) await host.device.info();
                await gateHold();
            """,
        ),
        BridgeScenario(
            "QJ1", "Empty script", "C14",
            notes = "X1: one line, then the script ends; the smallest bind / release path of QuickJS",
            engine = BridgeEngine.QUICKJS,
            body = """
                console.log(GATE_MARKER + " memory gate");
                gateHold();
            """,
        ),
        BridgeScenario(
            "QJ2", "File source and output", "C14",
            notes = "X2: QuickJS has no host capabilities, so a 64 KiB .quickjs.mjs file source (host snapshot + descriptor, module " +
                "with top-level await) and chunked stdout / stderr stand in for the data round trip",
            engine = BridgeEngine.QUICKJS,
            sourceFile = "aj6mem-qj2.quickjs.mjs", sourceDataKb = 64,
            body = """
                let checksum = 0;
                for (let i = 0; i < GATE_DATA.length; i += 7) checksum = (checksum * 31 + GATE_DATA.charCodeAt(i)) % 1000003;
                await Promise.resolve();
                gateStat("sourceDataChars", GATE_DATA.length);
                gateStat("checksum", checksum);
                for (let i = 0; i < 4; i++) console.log(GATE_MARKER + " chunk " + i + " " + GATE_DATA.substring(i * 1024, (i + 1) * 1024));
                console.error(GATE_MARKER + " stderr " + GATE_DATA.substring(0, 512));
                gateHold();
            """,
        ),
        BridgeScenario(
            "QJ3", "Output callbacks from Promise jobs", "C14",
            notes = "X3: three Promise jobs, each spinning 300 ms and printing one line (one output event each); F keeps a job " +
                "printing a line every 100 ms, so the stop lands while output events are in flight",
            engine = BridgeEngine.QUICKJS,
            body = """
                let ticks = 0;
                const tick = () => { gateBusy(300); ticks++; console.log(GATE_MARKER + " tick " + ticks); };
                Promise.resolve().then(tick).then(tick).then(tick).then(() => {
                    gateStat("ticks", ticks);
                    if (GATE_MODE !== "force") return;
                    gateReady();
                    for (let i = 0; ; i++) { gateBusy(100); console.log(GATE_MARKER + " flight " + i); }
                }).catch(gateError);
            """,
        ),
        BridgeScenario(
            "QJ4", "Plugin process killed", "C14 C16",
            notes = "X4: the runner force-stops the QuickJS plugin package once the script is ready; the run ends with an exception " +
                "(expected); F stops forceDelayMs after the kill",
            engine = BridgeEngine.QUICKJS,
            normalTimeoutMs = 90_000, killPluginOnReady = true, expectsFailure = true,
            body = """
                gateReady();
                gateBusy(5000);
            """,
        ),
        BridgeScenario(
            "QJ5", "ColorOS bind refusal loop", "C14 C16",
            notes = "OPD2413 only: the runner force-stops the QuickJS plugin before every run, so the first bindService is refused " +
                "and PluginAutoWake binds again (as ND13); other devices skip and record",
            engine = BridgeEngine.QUICKJS,
            colorOsOnly = true, forceStopPluginBeforeRun = true, normalTimeoutMs = 90_000,
            body = """
                console.log(GATE_MARKER + " memory gate");
                gateHold();
            """,
        ),
    )

    fun byId(id: String): BridgeScenario = all.first { it.id == id }
}
