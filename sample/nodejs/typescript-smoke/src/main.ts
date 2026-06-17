import toast = require("toast");
import app = require("app");
import dialogs = require("dialogs");
import clipboard = require("clipboard");
import device = require("device");
import shell = require("shell");
import engines = require("engines");
import accessibility = require("accessibility");
import mediaProjection = require("media_projection");
import image = require("image");
import images = require("images");
import ocr = require("ocr");
import storage = require("storage");
import storages = require("storages");
import notifications = require("notifications");
import sensors = require("sensors");
import fetchModule = require("fetch");
import axios = require("axios");
import websocket = require("websocket");
import workManager = require("work_manager");

async function smoke(): Promise<void> {
  await toast.showToast("hello", { duration: "short", log: true, timeoutMs: 1000 });
  await toast.toast("alias", { timeoutMs: 1000 });

  const launched: boolean = await app.launchPackage(app.packageName, { timeoutMs: 1000 });
  const launchByName: boolean = await app.launchApp("AutoJs6", { timeoutMs: 1000 });
  await app.openAppSetting(app.packageName, { timeoutMs: 1000 });
  await app.startActivity({ action: "android.intent.action.VIEW", data: "https://example.invalid" }, { timeoutMs: 1000 });

  await dialogs.alert("Title", "Message", { timeoutMs: 1000 });
  const confirmed: boolean = await dialogs.confirm("Confirm", "Message", { timeoutMs: 1000 });
  const input: string | null = await dialogs.input("Input", "prefill", { timeoutMs: 1000 });
  const prompted: string | null = await dialogs.prompt("Prompt", "prefill", { timeoutMs: 1000 });
  const selected: number = await dialogs.select("Select", ["a", 2], { timeoutMs: 1000 });

  const clipText: string = await clipboard.getText({ timeoutMs: 1000 });
  await clipboard.setText(clipText, { timeoutMs: 1000 });
  const hasClip: boolean = await clipboard.hasText({ timeoutMs: 1000 });

  const sdk: number = device.sdkInt;
  const screenWidth: number = device.width;
  const density: number = device.density;
  const screenOn: boolean = await device.isScreenOn({ timeoutMs: 1000 });
  await device.wakeUp({ timeoutMs: 1000 });
  await device.vibrate(25, { timeoutMs: 1000 });

  const shellResult: shell.ShellResult = await shell.exec("echo ok", {
    cwd: "sub",
    env: { AUTOJS6_TYPES: "ok" },
    timeoutMs: 1000
  });

  const currentEngine: engines.EngineSnapshot = engines.myEngine();
  const allEngines: readonly engines.EngineSnapshot[] = await engines.all({ timeoutMs: 1000 });
  const childEngine: engines.EngineSnapshot = await engines.execScript("child", "\"rhino\";", {
    timeoutMs: 1000,
    cwd: ".",
    arguments: { token: "ok" }
  });
  const fileEngine: engines.EngineSnapshot = await engines.execScriptFile("child.js", { timeoutMs: 1000 });
  const stoppedCount: number = await engines.stopAll({ timeoutMs: 1000 });
  const stoppedSelf: boolean = await engines.stopSelf({ timeoutMs: 1000 });

  const selector: accessibility.SelectorDescriptor = accessibility.text("OK");
  const enabled: boolean = await accessibility.isEnabled({ timeoutMs: 1000 });
  const ensured: boolean = await accessibility.ensureEnabled({ prompt: false, timeoutMs: 1000 });
  const clickedPoint: boolean = await accessibility.click(1, 2, { timeoutMs: 1000 });
  const clickedSelector: boolean = await accessibility.click(accessibility.desc("Confirm"), { timeoutMs: 1000 });
  const foundText = await accessibility.findByText("OK", { timeoutMs: 1000 });
  const foundOne = await accessibility.findOne(selector, { timeoutMs: 1000 });
  const foundAll: readonly accessibility.UiNodeSnapshot[] = await accessibility.findAll(accessibility.textContains("O"), { timeoutMs: 1000 });
  const matchedOne = await accessibility.findOne(accessibility.textMatches(/OK/i), { timeoutMs: 1000 });
  const boundedAll = await accessibility.findAll(accessibility.boundsContains({ left: 0, top: 0, right: 100, bottom: 100 }), { timeoutMs: 1000 });
  const clickedText: boolean = await accessibility.clickText("OK", { timeoutMs: 1000 });
  const longClicked: boolean = await accessibility.longClick(accessibility.classNameMatches(/Button$/), { timeoutMs: 1000 });
  const typed: boolean = await accessibility.setText(accessibility.idMatches(".*:id/input"), "typed", { timeoutMs: 1000 });
  const scrolledForward: boolean = await accessibility.scrollForward(accessibility.clickable(true), { timeoutMs: 1000 });
  const scrolledBackward: boolean = await accessibility.scrollBackward(accessibility.enabled(false), { timeoutMs: 1000 });
  const insideSelector: accessibility.SelectorDescriptor = accessibility.boundsInside(0, 0, 100, 100);
  const depthSelector: accessibility.SelectorDescriptor = accessibility.depth(2);
  await accessibility.back({ timeoutMs: 1000 });
  await accessibility.home({ timeoutMs: 1000 });
  await accessibility.recentApps({ timeoutMs: 1000 });

  const capturer: mediaProjection.ScreenCapturer = await mediaProjection.requestScreenCapture({
    requireExistingPermission: true,
    timeoutMs: 1000
  });
  const frame = await capturer.nextImage({ timeoutMs: 1000 });
  await capturer.stop({ timeoutMs: 1000 });

  const capture = await image.captureScreen({ requireExistingPermission: true, timeoutMs: 1000 });
  const template = await images.readImage("template.png", { timeoutMs: 1000 });
  await image.saveImage(capture, "out/result.png", { format: "png", timeoutMs: 1000 });
  await image.saveImage(capture, "out/result-copy.png", "png", { timeoutMs: 1000 });
  const clipped = await image.clip(capture, 0, 0, 10, 10, { timeoutMs: 1000 });
  const resized = await images.resize(clipped, 5, 5, { timeoutMs: 1000 });
  const gray = await image.grayscale(resized, { timeoutMs: 1000 });
  const binary = await image.threshold(gray, { threshold: 128, maxValue: 255, type: "binary", timeoutMs: 1000 });
  const point = await image.findImage(resized, template, { threshold: 0.8, timeoutMs: 1000 });
  const matches: readonly image.ImageMatch[] = await image.matchTemplate(binary, template, { threshold: 0.8, limit: 3, timeoutMs: 1000 });
  const colorPoint = await image.findColor(binary, "#ff0000", { threshold: 4, timeoutMs: 1000 });
  const multiPoint = await image.findMultiColors(binary, 0xff0000, [[1, 0, "#00ff00"], { x: 0, y: 1, color: "#0000ff" }], { timeoutMs: 1000 });
  const size: image.ImageSize = image.getSize(binary);
  const ocrResults: readonly ocr.OcrResult[] = await ocr.recognize(capture, { lang: "auto", timeoutMs: 1000 });
  const ocrText: string = await ocr.recognizeText(capture, { timeoutMs: 1000 });
  const ocrBounds: readonly ocr.OcrResult[] = await ocr.detectTextBounds(capture, {
    region: [0, 0, 10, 10],
    timeoutMs: 1000
  });
  const store: storage.StorageStore = storage.create("settings");
  await store.put("enabled", true, { timeoutMs: 1000 });
  const storedEnabled: boolean = await storages.open("settings").get("enabled", false, { timeoutMs: 1000 });
  const storageKeys: readonly string[] = await store.keys({ timeoutMs: 1000 });
  const storageContains: boolean = await store.contains("enabled", { timeoutMs: 1000 });
  await store.remove("enabled", { timeoutMs: 1000 });
  await store.clear({ timeoutMs: 1000 });
  const notificationId: notifications.NotificationId = await notifications.notify({
    id: 710003,
    title: "AutoJs6 Node",
    text: "Type smoke",
    channel: "node-types",
    ongoing: true,
    priority: "low"
  }, { timeoutMs: 1000 });
  await notifications.cancel(notificationId, { timeoutMs: 1000 });
  await notifications.cancelAll({ timeoutMs: 1000 });
  const availableSensors: readonly sensors.SensorInfo[] = await sensors.getAvailableSensors({ timeoutMs: 1000 });
  const sensorEvent: sensors.SensorEvent = await sensors.once("accelerometer", {
    samplingIntervalMs: sensors.policy.minSamplingIntervalMs,
    timeoutMs: 1000
  });
  const sensorSub: sensors.SensorSubscription = sensors.subscribe("light", (event) => {
    const values: readonly number[] = event.values;
    void values;
  }, { samplingIntervalMs: 250, drainIntervalMs: 100, timeoutMs: 1000 });
  await sensorSub.ready;
  await sensorSub.close({ timeoutMs: 1000 });
  await image.recycle(template, { timeoutMs: 1000 });
  await images.recycle(frame, { timeoutMs: 1000 });

  const response: fetchModule.Response = await fetchModule("https://example.invalid/get", {
    headers: { "x-test": "yes" },
    timeoutMs: 1000,
    maxResponseBytes: fetchModule.policy.defaultMaxResponseBytes
  });
  const responseText: string = await response.text();
  const responseJson: unknown = await fetchModule.default("https://example.invalid/json", { timeoutMs: 1000 }).then((item) => item.json());
  const fetchDiagnostics: fetchModule.Diagnostics = fetchModule.diagnostics();
  const axiosResponse: axios.Response<{ ok: boolean }> = await axios.get("https://example.invalid/axios", {
    baseURL: "https://example.invalid",
    params: { q: "types" },
    responseType: "json",
    timeout: 1000
  });
  const axiosText: axios.Response<string> = await axios.post("https://example.invalid/axios", "payload", {
    headers: { "content-type": "text/plain" },
    responseType: "text",
    timeout: 1000
  });
  const axiosDiagnostics: axios.Diagnostics = axios.diagnostics();

  const connection: websocket.Connection = await websocket.connect("wss://example.invalid/socket", {
    headers: { "x-mode": "types" },
    protocols: ["json"],
    timeoutMs: websocket.policy.defaultTimeoutMs,
    maxMessageBytes: websocket.policy.defaultMaxMessageBytes,
    maxQueueSize: websocket.policy.defaultMaxQueueSize
  });
  connection.onmessage = (message) => {
    void message.type;
  };
  connection.onclose = (event) => {
    void event.code;
  });
  connection.on("message", (message) => {
    const maybeText: string | undefined = message.text;
    void maybeText;
  });
  await connection.send("hello");
  await connection.close(1000, "done");
  const websocketDiagnostics: websocket.Diagnostics = websocket.diagnostics();

  const onceId: string = await workManager.scheduleOnce({
    scriptPath: "main.cjs",
    delayMs: 1000,
    constraints: { network: true },
    timeoutMs: 1000
  });
  const periodicId: string = await workManager.schedulePeriodic({
    entry: "main.cjs",
    intervalMs: workManager.policy.minPeriodicIntervalMs,
    packaged: true,
    constraints: { charging: true },
    timeoutMs: 1000
  });
  const tasks: readonly workManager.WorkTask[] = await workManager.list({ timeoutMs: 1000 });
  await workManager.cancel(onceId, { timeoutMs: 1000 });

  void launched;
  void launchByName;
  void confirmed;
  void input;
  void prompted;
  void selected;
  void hasClip;
  void sdk;
  void screenWidth;
  void density;
  void screenOn;
  void shellResult;
  void currentEngine;
  void allEngines;
  void childEngine;
  void fileEngine;
  void stoppedCount;
  void stoppedSelf;
  void enabled;
  void ensured;
  void clickedPoint;
  void clickedSelector;
  void foundText;
  void foundOne;
  void foundAll;
  void clickedText;
  void point;
  void ocrResults;
  void ocrText;
  void ocrBounds;
  void storedEnabled;
  void storageKeys;
  void storageContains;
  void notificationId;
  void availableSensors;
  void sensorEvent;
  void sensorSub;
  void responseText;
  void responseJson;
  void fetchDiagnostics;
  void axiosResponse;
  void axiosText;
  void axiosDiagnostics;
  void websocketDiagnostics;
  void periodicId;
  void tasks;
}

void smoke;
