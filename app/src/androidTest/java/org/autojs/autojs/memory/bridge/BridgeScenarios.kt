package org.autojs.autojs.memory.bridge

import org.autojs.autojs.memory.scenarios.GateMode

/**
 * One scenario of the bridge leak gate (docs/memory-phase2/ROADMAP.md T2): a script for a plugin engine plus what the runner
 * does around it. [body] runs inside an async function after the prelude of [BridgeScenarios.nodeSource].
 * zh-CN: 桥接泄漏门禁的一个场景 (docs/memory-phase2/ROADMAP.md T2): 插件引擎脚本, 以及运行器在其前后要做的事. [body] 在
 * [BridgeScenarios.nodeSource] 的前导代码之后, 于一个 async 函数中运行.
 */
internal class BridgeScenario(
    val id: String,
    val title: String,
    /** Suspect numbers of ROADMAP 1.3. zh-CN: 路线图 1.3 的疑点编号. */
    val hypotheses: String,
    val body: String,
    val notes: String = "",
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
)

internal object BridgeScenarios {

    const val NODE_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs"

    const val READY_LINE = "AJ6MEM_READY"
    const val STAT_PREFIX = "AJ6MEM_STAT "
    const val SKIP_PREFIX = "AJ6MEM_SKIP "
    const val ERROR_PREFIX = "AJ6MEM_ERROR "

    /**
     * Classes the heap analysis looks for after a retained run (T4).
     * zh-CN: 有残留时堆分析查找的类 (T4).
     */
    val NODE_HEAP_CLASSES = listOf(
        "org.autojs.autojs.engine.NodeJsHostCapabilityBroker",
        "org.autojs.autojs.engine.AndroidNodeBridgeCapabilityProvider",
        "org.autojs.autojs.engine.NodePluginJavaScriptEngine",
        "org.autojs.autojs.runtime.ScriptRuntime",
    )

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
    )

    fun byId(id: String): BridgeScenario = all.first { it.id == id }
}
