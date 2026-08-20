import toast = require("toast");
import app = require("app");
import dialogs = require("dialogs");
import clipboard = require("clipboard");
import device = require("device");
import shell = require("shell");
import engines = require("engines");
import accessibility = require("accessibility");
import rhino = require("rhino");
import rhinoCompat = require("autojs6:compat");
import mediaProjection = require("media_projection");
import image = require("image");
import images = require("images");
import ocr = require("ocr");
import media = require("media");
import mediainfo = require("mediainfo");
import recorder = require("recorder");
import storage = require("storage");
import storages = require("storages");
import database = require("database");
import sqlite = require("sqlite");
import consoleModule = require("console");
import timers = require("timers");
import files = require("files");
import base64 = require("base64");
import colors = require("colors");
import formatter = require("formatter");
import converter = require("converter");
import s13n = require("s13n");
import mime = require("mime");
import nanoid = require("nanoid");
import util = require("util");
import opencc = require("opencc");
import pinyin = require("pinyin");
import pinyin4j = require("pinyin4j");
import jsox = require("jsox");
import arrayx = require("jsox.arrayx");
import numberx = require("jsox.numberx");
import notifications = require("notifications");
import sensors = require("sensors");
import ui = require("ui");
import overlay = require("ui.overlay");
import fetchModule = require("fetch");
import http = require("http");
import https = require("https");
import net = require("net");
import tls = require("tls");
import dns = require("dns");
import dnsPromises = require("dns/promises");
import axios = require("axios");
import websocket = require("websocket");
import workManager = require("work_manager");
import packageManager = require("package_manager");
import npm = require("npm");
import plugins = require("plugins");
import autojsProfile = require("autojs6:profile");
import java = require("java");

interface TypeSmokePlugin {
  readonly ready: boolean;
  run(): string;
}

async function smoke(): Promise<void> {
  await toast.showToast("hello", { duration: "short", log: true, timeoutMs: 1000 });
  await toast.toast("alias", { timeoutMs: 1000 });

  const launched: boolean = await app.launchPackage(app.packageName, { timeoutMs: 1000 });
  const launchByName: boolean = await app.launchApp("AutoJs6", { timeoutMs: 1000 });
  const packageByName: string | null = await app.getPackageName("AutoJs6", { timeoutMs: 1000 });
  const nameByPackage: string | null = await app.getAppName(app.packageName, { timeoutMs: 1000 });
  const installed: boolean = await app.isInstalled(app.packageName, { timeoutMs: 1000 });
  const viewedFile: boolean = await app.viewFile("README.md", { timeoutMs: 1000 });
  const editedFile: boolean = await app.editFile("README.md", { timeoutMs: 1000 });
  const launchAlias: boolean = await app.launch(app.packageName, { timeoutMs: 1000 });
  const parsedUri: app.UriDescriptor | null = app.parseUri("https://example.invalid/path?q=1#frag");
  const getUriForFileDenied: () => never = () => app.getUriForFile("README.md");
  const killDenied: Promise<never> = app.kill(app.packageName, { timeoutMs: 1000 });
  const launchDualDenied: Promise<never> = app.launchDualPackage(app.packageName, { timeoutMs: 1000 });
  const dualInstalledDenied: Promise<never> = app.isDualInstalled(app.packageName, { timeoutMs: 1000 });
  await app.openAppSetting(app.packageName, { timeoutMs: 1000 });
  const viewIntent: app.IntentDescriptor = app.intent({
    action: "VIEW",
    url: "example.invalid",
    category: ["BROWSABLE"],
    flags: [0x10000000],
    extras: { source: "typescript-smoke", retry: 1, active: true, tags: ["node", "app"] }
  });
  const shellIntent: string = app.intentToShell(viewIntent);
  await app.startActivity(viewIntent, { timeoutMs: 1000 });
  await app.startActivity("https://example.invalid", { timeoutMs: 1000 });
  await app.openUrl("example.invalid/path", { timeoutMs: 1000 });
  await app.sendEmail({
    to: ["dev@example.invalid"],
    subject: "AutoJs6",
    text: "Node app facade"
  }, { timeoutMs: 1000 });
  await app.uninstall("com.example.invalid", { timeoutMs: 1000 });
  await app.openAppSettings({ timeoutMs: 1000 });
  await app.launchSettings(app.packageName, { timeoutMs: 1000 });
  await app.launchAppDetailsSettings(app.packageName, { timeoutMs: 1000 });
  const broadcastDenied: Promise<never> = app.sendBroadcast({ action: "org.autojs6.TEST" }, { timeoutMs: 1000 });

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
  const batteryIgnored: boolean = await device.isIgnoringBatteryOptimizations({ timeoutMs: 1000 });
  const batterySettings: device.DevicePowerSettingsResult = await device.openBatteryOptimizationSettings({
    dryRun: true,
    timeoutMs: 1000
  });

  const shellResult: shell.ShellResult = await shell.exec("echo ok", {
    cwd: "sub",
    env: { AUTOJS6_TYPES: "ok" },
    timeoutMs: 1000
  });
  const shellDefaultTimeout: number = shell.setDefaultTimeout(2000);
  const shellAccess: shell.ShellAccessReport = shell.checkAccess({ root: true, shizuku: true });
  const shellFileResult: shell.ShellResult = await shell.execFile("scripts/run.sh", ["--dry-run"], {
    timeoutMs: 1000
  });
  const rootShellResult: shell.ShellResult = await shell.execRoot("id", { timeoutMs: 1000 });
  const shizukuShellDenied: Promise<never> = shell.execShizuku("id", { timeoutMs: 1000 });
  const compatLaunch: boolean = await rhinoCompat.app.launchPackage(app.packageName, { timeoutMs: 1000 });
  const compatIntent: app.IntentDescriptor = rhinoCompat.app.intent({
    action: "VIEW",
    url: "https://example.invalid",
    categories: "BROWSABLE"
  });
  const compatShellIntent: string = rhinoCompat.app.intentToShell(compatIntent);
  const compatParsedUri: app.UriDescriptor | null = rhinoCompat.app.parseUri("content://org.autojs6.example/item/1");
  const compatGetUriForFileDenied: () => never = () => rhinoCompat.app.getUriForFile("README.md");
  const compatKillDenied: Promise<never> = rhinoCompat.app.kill(app.packageName, { timeoutMs: 1000 });
  await rhinoCompat.app.openUrl("https://example.invalid", { timeoutMs: 1000 });
  const compatBroadcastDenied: Promise<never> = rhinoCompat.app.sendBroadcast("inspect_layout_bounds", { timeoutMs: 1000 });
  const compatScreenOn: boolean = await rhinoCompat.device.isScreenOn({ timeoutMs: 1000 });
  const compatBatteryIgnored: boolean = await rhinoCompat.device.isIgnoringBatteryOptimizations({ timeoutMs: 1000 });
  const audioVolume: number = await media.getAudioStreamVolume("music", { timeoutMs: 1000 });
  const audioMaxVolume: number = await media.getAudioStreamMaxVolume("music", { timeoutMs: 1000 });
  const audioInfo: media.AudioStreamInfo = await media.getAudioStreamInfo("music", { timeoutMs: 1000 });
  const compatAudioInfo: media.AudioStreamInfo = await rhinoCompat.media.getAudioStreamInfo("music", { timeoutMs: 1000 });
  const mediaInfoSnapshot: mediainfo.Snapshot = await mediainfo.read("sample.mp3", {
    includeInform: false,
    includeSections: true,
    timeoutMs: 1000
  });
  const mediaInfoCallableSnapshot: mediainfo.Snapshot = await mediainfo("sample.mp3", { timeoutMs: 1000 });
  const mediaInfoFormat: string = await mediainfo.get("sample.mp3", "general", "Format", { timeoutMs: 1000 });
  const compatMediaInfoSnapshot: mediainfo.Snapshot = await rhinoCompat.mediainfo.read("sample.mp3", { timeoutMs: 1000 });
  const recorderStatus: recorder.RecorderStatus = await recorder.getStatus({ timeoutMs: 1000 });
  const compatRecorderStatus: recorder.RecorderStatus = await rhinoCompat.recorder.getStatus({ timeoutMs: 1000 });
  const recorderStart: (options?: recorder.RecorderStartOptions) => Promise<never> = recorder.start;
  const recorderStop: (options?: AutoJs6Node.BridgeCallOptions) => Promise<never> = rhinoCompat.recorder.stop;
  const compatShellResult: shell.ShellResult = await rhinoCompat.shell("echo compat", { timeoutMs: 1000 });
  const compatRootShellResult: shell.ShellResult = await rhinoCompat.shell("id", true);
  const compatExecRootResult: shell.ShellResult = await rhinoCompat.shell.execRoot("id", { timeoutMs: 1000 });
  const compatShizukuDenied: Promise<never> = rhinoCompat.shell.execShizuku("id", { timeoutMs: 1000 });
  const compatShellAccess: shell.ShellAccessReport = rhinoCompat.shell.checkAccess({ shizuku: true });

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
  const scrollableSelector: accessibility.SelectorDescriptor = accessibility.scrollable(false);
  await accessibility.back({ timeoutMs: 1000 });
  await accessibility.home({ timeoutMs: 1000 });
  await accessibility.recentApps({ timeoutMs: 1000 });

  const rhinoSelector: rhinoCompat.RhinoSelectorHandle = rhinoCompat.text("OK").descriptionContains("Confirm").scrollable(false).clickable();
  const rhinoNode = await rhinoSelector.findOne(1000);
  const rhinoNodes: readonly accessibility.UiNodeSnapshot[] = await rhinoSelector.find({ timeoutMs: 1000 });
  const rhinoClicked: boolean = await rhinoSelector.click({ timeoutMs: 1000 });
  const rhinoExists: boolean = await rhinoCompat.selector.description("Submit").idContains("submit").classNameContains("Button").enabled().exists(1000);
  const rhinoPointClicked: boolean = await rhinoCompat.click(1, 2, { timeoutMs: 1000 });
  const rhinoEnabled: false = rhino.isEnabled();
  const rhinoStatus: rhino.Status = rhino.status();
  const rhinoRunDenied: Promise<never> = rhino.run({ source: "1 + 1", timeoutMs: 1000, args: [] });
  const rhinoRunPoc: Promise<rhino.RunResult<{ readonly sum: number }>> = rhino.run<{ readonly sum: number }>({
    explicit: true,
    source: "({ sum: args[0] + args[1] });",
    args: [2, 3],
    timeoutMs: 1000
  });
  const rhinoInstallDenied: () => never = rhino.install;
  const rhinoInstallResult: rhino.InstallResult = rhino.install({ explicit: true, target: {} });
  const rhinoImportClassDenied: () => never = rhino.importClass;
  const rhinoImportPackageDenied: () => never = rhino.importPackage;
  const rhinoJavaAdapterDenied: (...args: readonly unknown[]) => never = rhino.JavaAdapter;
  await rhinoCompat.back({ timeoutMs: 1000 });
  await rhinoCompat.home({ timeoutMs: 1000 });

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
  const compatTemplate = await rhinoCompat.images.read("template.png", { timeoutMs: 1000 });
  const compatClip = await rhinoCompat.images.clip(capture, [0, 0, 10, 10], { timeoutMs: 1000 });
  const compatResize = await rhinoCompat.images.resize(compatClip, { width: 5, height: 5 }, { timeoutMs: 1000 });
  const compatGray = await rhinoCompat.images.grayscale(compatResize, { timeoutMs: 1000 });
  const compatBinary = await rhinoCompat.images.threshold(compatGray, 128, 255, "BINARY");
  const compatFind = await rhinoCompat.images.findImage(compatBinary, compatTemplate, 0, 0, 5, 5, 0.8);
  const compatMatches: readonly image.ImageMatch[] = await rhinoCompat.images.matchTemplate(compatBinary, compatTemplate, { max: 2, timeoutMs: 1000 });
  const compatColor = await rhinoCompat.images.findColor(compatBinary, "#ff0000", 0, 0, 5, 5, 4);
  const compatMultiColor = await rhinoCompat.images.findMultiColors(compatBinary, 0xff0000, [[1, 0, "#00ff00"]], { threshold: 4, timeoutMs: 1000 });
  const compatSaved: boolean = await rhinoCompat.images.save(compatBinary, "out/rhino-compat.png", "png", 90);
  const compatScreenRequested: boolean = await rhinoCompat.requestScreenCapture(true);
  const compatScreenCapture = await rhinoCompat.captureScreen({ timeoutMs: 1000 });
  const compatImagesRequested: boolean = await rhinoCompat.images.requestScreenCapture({ orientation: "portrait", timeoutMs: 1000 });
  const compatCapturedToFile: boolean = await rhinoCompat.images.captureScreen("out/rhino-screen.png", { format: "png", timeoutMs: 1000 });
  const compatScreenStopped: boolean = await rhinoCompat.images.stopScreenCapture({ timeoutMs: 1000 });
  const ocrResults: readonly ocr.OcrResult[] = await ocr.recognize(capture, { lang: "auto", timeoutMs: 1000 });
  const ocrText: string = await ocr.recognizeText(capture, { timeoutMs: 1000 });
  const ocrBounds: readonly ocr.OcrResult[] = await ocr.detectTextBounds(capture, {
    region: [0, 0, 10, 10],
    timeoutMs: 1000
  });
  const compatOcrTexts: readonly string[] = await rhinoCompat.ocr.recognizeText(capture, {
    region: [0, 0, 10, 10],
    timeoutMs: 1000
  });
  const compatOcrPathTexts: readonly string[] = await rhinoCompat.ocr("fixtures/ocr.png", { timeoutMs: 1000 });
  const compatOcrBounds: readonly ocr.OcrResult[] = await rhinoCompat.ocr.detectTextBounds(capture, {
    region: { left: 0, top: 0, right: 10, bottom: 10 },
    timeoutMs: 1000
  });
  const compatOcrMode: "mlkit" = rhinoCompat.ocr.tap("mlkit");
  const store: storage.StorageStore = storage.create("settings");
  await store.put("enabled", true, { timeoutMs: 1000 });
  const storedEnabled: boolean = (await storages.open("settings").get("enabled", false, { timeoutMs: 1000 })) === true;
  const storageKeys: readonly string[] = await store.keys({ timeoutMs: 1000 });
  const storageContains: boolean = await store.contains("enabled", { timeoutMs: 1000 });
  await store.putSync("syncEnabled", true, { timeoutMs: 1000 });
  await store.removeSync("syncEnabled", { timeoutMs: 1000 });
  await store.remove("enabled", { timeoutMs: 1000 });
  await store.clear({ timeoutMs: 1000 });
  await store.clearSync({ timeoutMs: 1000 });
  await storages.remove("settings", { timeoutMs: 1000 });

  const db: database.DatabaseHandle = await database.open("types_smoke", { timeoutMs: 1000 });
  const sqliteDb: database.DatabaseHandle = await sqlite("types_smoke_sqlite", { timeoutMs: 1000 });
  await db.exec("create table if not exists kv (k text primary key, v text)", [], { timeoutMs: 1000 });
  const runResult: database.RunResult = await db.run("insert or replace into kv (k, v) values (?, ?)", ["a", "b"], { timeoutMs: 1000 });
  const oneRow: database.Row | null = await db.get("select v from kv where k = ?", ["a"], { timeoutMs: 1000 });
  const rows: readonly database.Row[] = await db.all("select k, v from kv", [], { timeoutMs: 1000 });
  const transactionResults: readonly database.TransactionResult[] = await sqliteDb.transaction([
    { type: "exec", sql: "create table if not exists kv (k text primary key, v text)" },
    { type: "run", sql: "insert or replace into kv (k, v) values (?, ?)", params: ["c", "d"] },
    { type: "get", sql: "select v from kv where k = ?", params: ["c"] }
  ], { timeoutMs: 1000 });
  const dbClosed: boolean = await db.close({ timeoutMs: 1000 });
  const sqliteClosed: boolean = await sqliteDb.close({ timeoutMs: 1000 });

  consoleModule.log("types smoke", rows.length);
  consoleModule.verbose("debug", oneRow);
  consoleModule.err("stderr", runResult.rowsAffected);
  consoleModule.show().setTitle("Types").setSize(320, 200).hide();
  const timeoutHandle: timers.TimerHandle = timers.setTimeout(() => undefined, 1);
  timers.clearTimeout(timeoutHandle);
  const intervalHandle: AutoJs6NodeTimers.TimerHandle = setInterval(() => undefined, 10);
  clearInterval(intervalHandle);
  const immediateHandle: timers.TimerHandle = timers.setImmediate(() => undefined);
  timers.clearImmediate(immediateHandle);

  const scopedPath: string | null = files.path("types.txt");
  const encoded: string = base64.encode("AutoJs6");
  const decoded: string = base64.decode(encoded);
  const color: number = colors.rgb(1, 2, 3);
  const hex: string = colors.toHex(color);
  const formattedBytes: string = formatter.bytes(1536);
  const convertedBytes: number = converter.bytes("1 KB", "B");
  const standardizedBytes: number = s13n.bytes("1 KB");
  const compatStandardizedBytes: number = rhinoCompat.s13n.bytes("2", "MB");
  const parsedMime: mime.JsMime = mime("text/plain; charset=utf-8");
  const nano: string = nanoid(8);
  const inspected: string = util.inspect({ encoded, decoded });
  const openccConvert: (value: unknown, type: opencc.ConversionType | string) => never = opencc.convert;
  const openccS2t: (value: unknown) => never = opencc.s2t;
  const pinyinText: string = pinyin.simple("\u4f60\u597d");
  const pinyinRows: pinyin.PinyinResult = pinyin.convert("\u4e2d\u56fd", { style: pinyin.STYLE_TONE2 });
  const pinyin4jText: string = pinyin4j.of("\u4e2d\u56fd", { separator: " ", tone: "WITH_TONE_NUMBER" });
  const mathxMean: number = jsox.mathx.mean([1, 2, 3, 4]);
  const mathxVariance: number = jsox.Mathx["var"]([1, 2, 3]);
  const mathxDistance: number = jsox.mathx.dist([0, 0], { x: 3, y: 4 });
  const mathxTarget = jsox.extend({} as { sum(...values: readonly unknown[]): number }, "Mathx");
  const mathxTargetSum: number = mathxTarget.sum(1, 2, 3);
  const compatMathxMedian: number = rhinoCompat.jsox.mathx.median([1, 4, 2, 3]);
  const arrayxDistinct: number[] = arrayx.distinct([1, 2, 1, 3]);
  const arrayxSortedBy: string[] = jsox.arrayx.sortedBy(["aaa", "b", "cc"], (value) => value.length);
  const arrayxTarget = jsox.extend({} as { union<T>(...arrays: readonly (readonly T[])[]): T[] }, "Arrayx");
  const arrayxTargetUnion: number[] = arrayxTarget.union([1, 2], [2, 3]);
  const compatArrayxIntersect: number[] = rhinoCompat.jsox.arrayx.intersect([1, 2, 3], [2, 3, 4]);
  const numberxClamp: number = numberx.clamp(20, [10, 30]);
  const numberxParsed: number = jsox.numberx.parseAny("18:9");
  const numberxTarget = jsox.extend({} as { toFixedNum(num: unknown, fraction?: unknown): number }, "Numberx");
  const numberxTargetFixed: number = numberxTarget.toFixedNum(123.456, 2);
  const compatNumberxPadded: string = rhinoCompat.jsox.numberx.padStart(5, 2, 0);

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
  const notificationStatus: notifications.NotificationPermissionStatus = await notifications.getPermissionStatus({ timeoutMs: 1000 });
  const notificationSettings: notifications.NotificationSettingsResult = await notifications.openSettings({
    dryRun: true,
    timeoutMs: 1000
  });
  const notificationSettingsAlias: notifications.NotificationSettingsResult = await notifications.openNotificationSettings({
    dryRun: true,
    timeoutMs: 1000
  });
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

  const uiHandle: ui.UiHandle = await ui.showLayout({
    type: "Vertical",
    children: [
      { id: "title", type: "Text", text: "Node UI" },
      { id: "ok", type: "Button", text: "OK" }
    ]
  }, { timeoutMs: 1000, drainIntervalMs: 50 });
  const unsubscribeUi: () => void = uiHandle.on("click", (event: ui.UiEvent) => {
    const eventId: string | undefined = event.id;
    void eventId;
  }, { drainIntervalMs: 50, timeoutMs: 1000 });
  await uiHandle.update({ id: "title", text: "Updated" }, { timeoutMs: 1000 });
  unsubscribeUi();
  await uiHandle.close({ timeoutMs: 1000 });

  const compatUi: rhinoCompat.RhinoUiCompatModule = rhinoCompat.ui;
  const compatUiHandle: ui.UiHandle = await compatUi.showLayout({
    type: "Column",
    children: [
      { id: "compatTitle", type: "Text", text: "Compat UI" },
      { id: "compatOk", type: "Button", text: "OK" }
    ]
  }, { timeoutMs: 1000, drainIntervalMs: 50 });
  const unsubscribeCompatUi: () => void = compatUiHandle.on("click", (event: ui.UiEvent) => {
    const eventType: string = event.type;
    void eventType;
  }, { drainIntervalMs: 50, timeoutMs: 1000 });
  await compatUiHandle.update({ id: "compatTitle", text: "Compat Updated" }, { timeoutMs: 1000 });
  await compatUiHandle.batchUpdate([{ id: "compatOk", text: "Done", enabled: false }], { timeoutMs: 1000 });
  unsubscribeCompatUi();
  await compatUiHandle.close({ timeoutMs: 1000 });

  const overlayPermission: overlay.OverlayPermissionSnapshot = await overlay.hasPermission({ timeoutMs: 1000 });
  const overlaySettings: overlay.OverlayPermissionSettingsResult = await overlay.openPermissionSettings({
    dryRun: true,
    timeoutMs: 1000
  });
  const overlayCloseSummary: overlay.OverlayCloseSummary = await overlay.closeAll({ timeoutMs: 1000 });
  const overlayShow: Promise<overlay.OverlayHandle> = overlay.show({
    content: { type: "Text", text: "Overlay" },
    window: { width: "wrap_content", height: "wrap_content", touchable: true, focusable: false, visible: true },
    disclosure: { title: "AutoJs6 overlay", text: "Script overlay is visible" }
  }, { timeoutMs: 1000 });
  const overlayHandlePromise: Promise<readonly overlay.OverlayEvent[]> = overlayShow.then((handle) =>
    handle.drainEvents({ maxEvents: overlay.policy.defaultDrainBatchSize, timeoutMs: 1000 }),
  );

  await rhinoCompat.images.recycle(compatTemplate, { timeoutMs: 1000 });
  await rhinoCompat.images.recycle(compatClip, { timeoutMs: 1000 });
  await rhinoCompat.images.recycle(compatResize, { timeoutMs: 1000 });
  await rhinoCompat.images.recycle(compatGray, { timeoutMs: 1000 });
  await rhinoCompat.images.recycle(compatBinary, { timeoutMs: 1000 });
  await rhinoCompat.images.recycle(compatScreenCapture, { timeoutMs: 1000 });
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
  const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 2 });
  const httpRequest: http.ClientRequest = http.request("http://example.invalid/client", {
    agent: httpAgent,
    headers: { "x-types": "yes" },
    maxRedirects: 1,
    timeout: 1000
  }, (incoming: http.IncomingMessage) => {
    incoming.setEncoding("utf8").on("data", (chunk) => void chunk);
  });
  httpRequest.setTimeout(1000).end("payload");
  const httpsAgent = new https.Agent({ keepAlive: true, rejectUnauthorized: true });
  const httpsRequest: http.ClientRequest = https.request({
    hostname: "example.invalid",
    path: "/secure",
    method: "POST",
    agent: httpsAgent,
    timeoutMs: 1000
  });
  const httpServer: http.Server = http.createServer();
  const httpListenDenied: (...args: readonly unknown[]) => never = httpServer.listen;
  httpsRequest.end("secure");
  httpAgent.destroy();
  httpsAgent.destroy();
  const socketAddress: net.SocketAddress = new net.SocketAddress({ address: "127.0.0.1", port: 9229 });
  const parsedSocketAddress: net.SocketAddress | undefined = net.SocketAddress.parse("127.0.0.1:9229");
  const blockList = new net.BlockList();
  blockList.addAddress("127.0.0.2");
  blockList.addSubnet("127.0.1.0", 24);
  const socketBlocked: boolean = blockList.check("127.0.0.2");
  const socket = new net.Socket({ allowHalfOpen: true });
  const destroyedSocket: net.Socket = socket.destroy();
  const netConnectDenied: (...args: readonly unknown[]) => never = net.connect;
  const netServer: net.Server = net.createServer();
  const netListenDenied: (...args: readonly unknown[]) => never = netServer.listen;
  const dnsLookup: void = dns.lookup("localhost", { all: true }, (error: Error | null, addresses: readonly dns.LookupAddress[]) => {
    void error;
    void addresses;
  });
  const dnsLookupPromise: Promise<dnsPromises.LookupAddress> = dnsPromises.lookup("localhost");
  const dnsResolveDenied: (hostname: string, callback: (error: Error | null, addresses?: readonly string[]) => void) => void = dns.resolve4;
  const dnsResolver = new dns.Resolver({ timeout: 100, tries: 1, maxTimeout: 250 });
  const dnsPromiseResolver = new dnsPromises.Resolver({ timeout: 100, tries: 1, maxTimeout: 250 });
  dns.resolve("example.invalid", "MX", (error: Error | null, records?: readonly dns.MxRecord[]) => {
    void error;
    const firstExchange: string | undefined = records?.[0]?.exchange;
    void firstExchange;
  });
  dns.resolve4("example.invalid", { ttl: true }, (error: Error | null, records?: readonly dns.ResolveAddressWithTtl[]) => {
    void error;
    const firstTtl: number | undefined = records?.[0]?.ttl;
    void firstTtl;
  });
  dnsResolver.resolveTlsa("example.invalid", (error: Error | null, records?: readonly dns.TlsaRecord[]) => {
    void error;
    const firstData: ArrayBuffer | undefined = records?.[0]?.data;
    void firstData;
  });
  const dnsMappedMx: dns.ResolveRecordFor<"MX"> = [{ exchange: "mx.example.invalid", priority: 10 }];
  const dnsAnyRecord: dns.AnyRecord = { type: "TLSA", certUsage: 3, selector: 1, match: 1, data: new ArrayBuffer(0) };
  const dnsCancelled: "ECANCELLED" = dns.CANCELLED;
  const dnsBadStr: "EBADSTR" = dns.BADSTR;
  const dnsPromiseMx: Promise<readonly dnsPromises.MxRecord[]> = dnsPromises.resolve("example.invalid", "MX");
  const dnsPromiseTlsa: Promise<readonly dnsPromises.TlsaRecord[]> = dnsPromiseResolver.resolveTlsa("example.invalid");
  const dnsPromiseTtl: Promise<readonly dnsPromises.ResolveAddressWithTtl[]> = dnsPromises.resolve4("example.invalid", { ttl: true });
  void dnsMappedMx;
  void dnsAnyRecord;
  void dnsCancelled;
  void dnsBadStr;
  void dnsPromiseMx;
  void dnsPromiseTlsa;
  void dnsPromiseTtl;
  const secureContext: tls.SecureContext = tls.createSecureContext({ minVersion: "TLSv1.2" });
  const tlsSocket: tls.TLSSocket = new tls.TLSSocket(socket, { servername: "example.invalid" });
  const tlsCipher: tls.CipherNameAndProtocol = tlsSocket.getCipher();
  const tlsServer: tls.Server = tls.createServer({ requestCert: true });
  const tlsConnectDenied: (...args: readonly unknown[]) => never = tls.connect;
  const tlsListenDenied: (...args: readonly unknown[]) => never = tlsServer.listen;
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
  };
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
  const compatDisposableId: string = await rhinoCompat.tasks.addDisposableTask({
    path: "main.cjs",
    delayMs: 1000,
    timeoutMs: 1000
  });
  const compatTimedTasks: readonly workManager.WorkTask[] = await rhinoCompat.tasks.queryTimedTasks({ timeoutMs: 1000 });
  const compatTimedTask: workManager.WorkTask | null = await rhinoCompat.tasks.getTimedTask(compatDisposableId, { timeoutMs: 1000 });
  const compatDailyDenied: Promise<never> = rhinoCompat.tasks.addDailyTask({ path: "main.cjs", time: "08:30" });
  const compatWeeklyDenied: Promise<never> = rhinoCompat.tasks.addWeeklyTask({ path: "main.cjs", time: "08:30", daysOfWeek: [1] });
  const compatUpdateDenied: Promise<never> = rhinoCompat.tasks.updateTask({ id: compatDisposableId });
  const compatIntentAddDenied: Promise<never> = rhinoCompat.tasks.addIntentTask({ path: "main.cjs", action: "org.autojs6.TEST" });
  const compatIntentQueryDenied: Promise<never> = rhinoCompat.tasks.queryIntentTasks({ action: "org.autojs6.TEST" });
  const compatIntentGetDenied: Promise<never> = rhinoCompat.tasks.getIntentTask("intent-1");
  const compatIntentRemoveDenied: Promise<never> = rhinoCompat.tasks.removeIntentTask("intent-1");

  const packageManagerStatus: packageManager.DiagnosticStatus = packageManager.status();
  const packageManagerPolicy: packageManager.DiagnosticPolicy = packageManager.policy();
  const packageManagerSourcePolicy: packageManager.SourcePolicy = packageManager.sourcePolicy();
  const packageManagerPlanInstall: (request: packageManager.PlanSourceRequest) => Promise<packageManager.PackagePlan> = packageManager.planInstall;
  const packageManagerPlanUpdate: (request: packageManager.PlanSourceRequest) => Promise<packageManager.PackagePlan> = packageManager.planUpdate;
  const packageManagerPlanRemove: (request: packageManager.PlanRemoveRequest) => Promise<packageManager.PackagePlan> = packageManager.planRemove;
  const packageManagerPlanRegistryInstall: (request: packageManager.PlanRegistryInstallRequest) => Promise<packageManager.RemotePackagePlan> = packageManager.planRegistryInstall;
  const packageManagerPlanTarballInstall: (request: packageManager.PlanTarballInstallRequest) => Promise<packageManager.RemotePackagePlan> = packageManager.planTarballInstall;
  const packageManagerPackument: packageManager.RegistryPackument = {
    name: "@autojs6/types-smoke",
    "dist-tags": {
      latest: "1.0.0"
    },
    versions: {
      "1.0.0": {
        name: "@autojs6/types-smoke",
        version: "1.0.0",
        dist: {
          tarball: "https://registry.npmjs.org/@autojs6/types-smoke/-/types-smoke-1.0.0.tgz",
          integrity: "sha512-types-smoke"
        }
      }
    }
  };
  const packageManagerRegistryRequest: packageManager.PlanRegistryInstallRequest = {
    name: "@autojs6/types-smoke",
    tag: "latest",
    metadata: packageManagerPackument
  };
  const packageManagerRangeRegistryRequest: packageManager.PlanRegistryInstallRequest = {
    name: "@autojs6/types-smoke",
    range: "^1.0.0",
    expectedIntegrity: "sha512-types-smoke",
    packument: packageManagerPackument
  };
  const packageManagerTarballRequest: packageManager.PlanTarballInstallRequest = {
    tarballUrl: "https://registry.npmjs.org/@autojs6/types-smoke/-/types-smoke-1.0.0.tgz",
    expectedIntegrity: "sha512-types-smoke"
  };
  const packageManagerRemoteResolution: packageManager.RemotePlanResolution = {} as packageManager.RemotePlanResolution;
  const packageManagerRemoteSelectorKind: packageManager.RemotePlanSelectorKind = "range";
  const packageManagerRemoteIntegrityStatus: packageManager.RemotePlanIntegrityStatus = "expected_integrity_matched";
  const packageManagerRemoteUrlStatus: packageManager.RemotePlanUrlStatus = "remote_url";
  const packageManagerInstall: (request: packageManager.MutationSourceRequest) => Promise<packageManager.OperationResult> = packageManager.install;
  const packageManagerUpdate: (request: packageManager.MutationSourceRequest) => Promise<packageManager.OperationResult> = packageManager.update;
  const packageManagerRemove: (request: packageManager.MutationRemoveRequest) => Promise<packageManager.OperationResult> = packageManager.remove;
  const packageManagerList: (options?: packageManager.ListOptions) => Promise<readonly packageManager.PackageRecord[]> = packageManager.list;
  const packageManagerVerify: (request: packageManager.VerifyRequest) => Promise<packageManager.OperationResult> = packageManager.verify;
  const packageManagerPrune: (options?: packageManager.PruneOptions) => Promise<packageManager.OperationResult> = packageManager.prune;
  const npmStatus: npm.NpmFacadeStatus = npm.status();
  const npmPolicy: npm.NpmFacadePolicy = npm.policy();
  const npmSourcePolicy: npm.SourcePolicy = npm.sourcePolicy();
  const npmPackageManager: packageManager.PackageManagerModule = npm.packageManager();
  const npmPackument: npm.RegistryPackument = packageManagerPackument;
  const npmRemoteResolution: npm.RemotePlanResolution = packageManagerRemoteResolution;
  const npmRemoteSelectorKind: npm.RemotePlanSelectorKind = packageManagerRemoteSelectorKind;
  const npmRemoteIntegrityStatus: npm.RemotePlanIntegrityStatus = packageManagerRemoteIntegrityStatus;
  const npmRemoteUrlStatus: npm.RemotePlanUrlStatus = packageManagerRemoteUrlStatus;
  const npmPlanRegistryInstall: (request: npm.PlanRegistryInstallRequest) => Promise<npm.RemotePackagePlan> = npm.planRegistryInstall;
  const npmPlanTarballInstall: (request: npm.PlanTarballInstallRequest) => Promise<npm.RemotePackagePlan> = npm.planTarballInstall;
  const npmInstall: (request: npm.MutationSourceRequest) => Promise<npm.OperationResult> = npm.install;
  const npmList: (options?: npm.ListOptions) => Promise<readonly npm.PackageRecord[]> = npm.list;
  const npmCliDenied: (...args: readonly unknown[]) => never = npm.cli;
  const pluginInfo: plugins.PluginManifest = plugins.manifest("org.example.types");
  const pluginEntry: string = plugins.resolve("org.example.types");
  const typedPlugin: TypeSmokePlugin = plugins.load<TypeSmokePlugin>("org.example.types");
  const pluginResult: string = typedPlugin.run();
  const profileEngine: string = autojsProfile.engineVersion;
  const globalProfile: AutoJs6Node.AutoJsProfile = $autojs.profile;
  const esmLoaderProfileStatus: string = autojsProfile.esmLoaderProfile.status;
  const esmLoaderProfileRequestEnabled: boolean = autojsProfile.esmLoaderProfile.requestEnabled;
  const esmLoaderProfileDynamicImport: string = autojsProfile.esmLoaderProfile.dynamicImport;
  const esmLoaderProfilePackageExports: string = autojsProfile.esmLoaderProfile.packageExports;
  const esmLoaderProfileLoaderHooks: string = autojsProfile.esmLoaderProfile.loaderHooks;
  const esmLoaderProfileRawNodeLoader: boolean = autojsProfile.esmLoaderProfile.rawNodeLoader;
  const packageManagerProfileStatus: string = autojsProfile.packageManagerProfile.status;
  const packageManagerProfileDefaultEnabled: boolean = autojsProfile.packageManagerProfile.defaultEnabled;
  const packageManagerProfileRuntimeInstall: string = autojsProfile.packageManagerProfile.androidRuntimeInstall;
  const packageManagerProfileSourceAuthority: string = autojsProfile.packageManagerProfile.sourceAuthority;
  const packageManagerProfileAllowedSources: readonly string[] = autojsProfile.packageManagerProfile.allowedSources;
  const packageManagerProfilePlanOperations: readonly string[] = autojsProfile.packageManagerProfile.planOperations;
  const packageManagerProfileMutationOperations: readonly string[] = autojsProfile.packageManagerProfile.mutationOperations;
  const packageManagerProfileNpmFacade: string = autojsProfile.packageManagerProfile.npmFacade;
  const packageManagerProfileRequireNpm: string = autojsProfile.packageManagerProfile.requireNpm;
  const packageManagerProfileNpmFacadeOperations: readonly string[] = autojsProfile.packageManagerProfile.npmFacadeOperations;
  const packageManagerProfileLifecycleScripts: string = autojsProfile.packageManagerProfile.lifecycleScripts;
  const packageManagerProfileBinLinks: string = autojsProfile.packageManagerProfile.binLinks;
  const packageManagerProfileRawRegistryAccess: boolean = autojsProfile.packageManagerProfile.rawRegistryAccess;
  const stdlibProfileStatus: string = autojsProfile.stdlibProfile.status;
  const stdlibProfileModules: readonly string[] = autojsProfile.stdlibProfile.conformanceModules;
  const stdlibProfileCrypto: string = autojsProfile.stdlibProfile.moduleStatus.crypto;
  const stdlibProfileAndroidUnsupported: readonly string[] = autojsProfile.stdlibProfile.androidUnsupported;
  const stdlibProfileProcessGetBuiltin: string = autojsProfile.stdlibProfile.processGetBuiltinModule;
  const stdlibProfileRawDesktopParity: boolean = autojsProfile.stdlibProfile.rawDesktopParity;
  const processParityProfileStatus: string = autojsProfile.processParityProfile.status;
  const processParityProfileArgv: string = autojsProfile.processParityProfile.argv;
  const processParityProfileEnvKeys: readonly string[] = autojsProfile.processParityProfile.envDefaultKeys;
  const processParityProfileExit: string = autojsProfile.processParityProfile.exit;
  const processParityProfileSignals: string = autojsProfile.processParityProfile.signals;
  const processParityProfileRawSignals: boolean = autojsProfile.processParityProfile.rawSignals;
  const packagedCapabilityProfileStatus: string = autojsProfile.packagedCapabilityProfile.status;
  const packagedCapabilityProfileSources: readonly string[] = autojsProfile.packagedCapabilityProfile.metadataSources;
  const packagedCapabilityProfileNetwork: string = autojsProfile.packagedCapabilityProfile.network;
  const packagedCapabilityProfileNativeAssets: boolean = autojsProfile.packagedCapabilityProfile.nativeAssetLoading;
  const packagedMetadataProfile: string = autojsProfile.bridgePermissions.packagedMetadata.profile;
  const packagedMetadataBuiltins: readonly string[] = autojsProfile.bridgePermissions.packagedMetadata.builtins;
  const packagedMetadataNetworkRequested: boolean = autojsProfile.bridgePermissions.packagedMetadata.network.requested;
  const packagedMetadataGrantsAuthority: boolean = autojsProfile.bridgePermissions.packagedMetadata.grantsAuthority;
  const pluginSystemProfileStatus: string = autojsProfile.pluginSystemProfile.status;
  const pluginSystemProfileOperations: readonly string[] = autojsProfile.pluginSystemProfile.operations;
  const pluginSystemProfilePermissions: string = autojsProfile.pluginSystemProfile.permissionModel;
  const pluginSystemProfileNamespacePermissions: string = autojsProfile.pluginSystemProfile.pluginNamespacePermissions;
  const pluginSystemProfilePackaged: string = autojsProfile.pluginSystemProfile.packagedDistribution;
  const pluginSystemProfileNative: boolean = autojsProfile.pluginSystemProfile.nativeAddonLoading;
  const packageIntegrityProfileStatus: string = autojsProfile.packageIntegrityProfile.status;
  const packageIntegrityProfileHash: string = autojsProfile.packageIntegrityProfile.hashAlgorithm;
  const packageIntegrityProfileRecoveryActions: readonly string[] = autojsProfile.packageIntegrityProfile.recoveryActions;
  const packageIntegrityProfileSubjects: readonly string[] = autojsProfile.packageIntegrityProfile.subjects;
  const packageIntegrityProfileUnsignedNative: boolean = autojsProfile.packageIntegrityProfile.unsignedNativeAddonLoad;
  const profileSelectionStatus: string = autojsProfile.profileSelection.status;
  const profileSelectionEffective: string = autojsProfile.profileSelection.effectiveProfile;
  const profileSelectionRequested: readonly string[] = autojsProfile.profileSelection.requestedProfiles;
  const profileSelectionHighRisk: readonly AutoJs6Node.ProfileSelectionHighRiskCapability[] =
    autojsProfile.profileSelection.highRiskCapabilities;
  const profileSelectionFailureHint: AutoJs6Node.ProfileSelectionFailureHint =
    autojsProfile.profileSelection.failureHints.capabilityNotDeclared;
  const profileSelectionDeclarationExamples: AutoJs6Node.ProfileSelectionDeclarationExamples =
    profileSelectionFailureHint.declarationExamples;
  const profileRollbackStatus: string = autojsProfile.profileRollbackPolicy.status;
  const profileRollbackTarget: string = autojsProfile.profileRollbackPolicy.rollbackTargetProfile;
  const profileRollbackProfileSwitches: Readonly<Record<string, string>> =
    autojsProfile.profileRollbackPolicy.profileKillSwitches;
  const profileRollbackCapabilitySwitches: Readonly<Record<string, string>> =
    autojsProfile.profileRollbackPolicy.capabilityKillSwitches;
  const profileRollbackStates: readonly AutoJs6Node.ProfileRollbackState[] =
    autojsProfile.profileRollbackPolicy.profileStates;
  const capabilityRollbackStates: readonly AutoJs6Node.CapabilityRollbackState[] =
    autojsProfile.profileRollbackPolicy.capabilityStates;
  const rollbackTrigger: AutoJs6Node.ProfileRollbackTrigger =
    autojsProfile.profileRollbackPolicy.rollbackTriggers[0];
  const packagedDowngrade: AutoJs6Node.ProfileRollbackPackagedDowngrade =
    autojsProfile.profileRollbackPolicy.packagedDowngrade;
  const packagedRemoteKillSwitchPolicy: string = packagedDowngrade.remoteKillSwitchPolicy;
  const rollbackReleasePolicy: AutoJs6Node.ProfileRollbackReleasePolicy =
    autojsProfile.profileRollbackPolicy.releasePolicy;
  const privacyDisclosureStatus: string = autojsProfile.privacyDisclosurePolicy.status;
  const privacyDisclosureSurfaces: readonly AutoJs6Node.PrivacyDisclosureSurface[] =
    autojsProfile.privacyDisclosurePolicy.surfaces;
  const privacyDisclosureChannel: AutoJs6Node.PrivacyDisclosureChannel =
    autojsProfile.privacyDisclosurePolicy.channels[0];
  const privacyDisclosurePackaged: AutoJs6Node.PrivacyDisclosurePackagedPolicy =
    autojsProfile.privacyDisclosurePolicy.packagedPolicy;
  const privacyDisclosureImplicitDenied: boolean =
    autojsProfile.privacyDisclosurePolicy.implicitHighPrivilegeDenied;
  const privacyDisclosureDoctorPrivacy: AutoJs6Node.PrivacyDisclosureDoctorPrivacy =
    autojsProfile.privacyDisclosurePolicy.doctorPrivacy;
  const privacyDisclosureAutomation: AutoJs6Node.PrivacyDisclosureAutomationImplications =
    autojsProfile.privacyDisclosurePolicy.automationImplications;
  const privacyDisclosureRemoteKillSwitch: string =
    privacyDisclosureAutomation.remotePackagedKillSwitch;
  const runtimeProfileId: AutoJs6Node.RuntimeProfileId = "safe_default";
  const proRuntimeProfileId: AutoJs6Node.RuntimeProfileId = "pro_compat_opt_in";
  const desktopRuntimeProfileId: AutoJs6Node.RuntimeProfileId = "desktop_compat_opt_in";
  const debugRuntimeProfileId: AutoJs6Node.RuntimeProfileId = "debug_unsafe_lab";
  const safeToastMode: AutoJs6Node.ProfileModuleModeFor<"toast", "safe_default"> = "available";
  const safeJavaMode: AutoJs6Node.ProfileModuleModeFor<"java", "safe_default"> = "available";
  const proJavaMode: AutoJs6Node.ProfileModuleModeFor<"java", "pro_compat_opt_in"> = "available";
  const proOverlayMode: AutoJs6Node.ProfileModuleModeFor<"ui.overlay", "pro_compat_opt_in"> = "profile_gated";
  const safeWorkerMode: AutoJs6Node.ProfileModuleModeFor<"worker_threads", "safe_default"> = "available";
  const desktopWorkerMode: AutoJs6Node.ProfileModuleModeFor<"worker_threads", "desktop_compat_opt_in"> = "available";
  const desktopHttpMode: AutoJs6Node.ProfileModuleModeFor<"http", "desktop_compat_opt_in"> = "available";
  const safeRawNetworkMode: AutoJs6Node.ProfileModuleModeFor<"raw_network", "safe_default"> = "available";
  const desktopRawNetworkMode: AutoJs6Node.ProfileModuleModeFor<"raw_network", "desktop_compat_opt_in"> = "available";
  const desktopWasiMode: AutoJs6Node.ProfileModuleModeFor<"wasi", "desktop_compat_opt_in"> = "profile_gated";
  const debugInspectorMode: AutoJs6Node.ProfileModuleModeFor<"inspector", "debug_unsafe_lab"> = "profile_gated";
  const safeInspectorMode: AutoJs6Node.ProfileModuleModeFor<"inspector", "safe_default"> = "denied";
  const packageManagerRequiredProfile: AutoJs6Node.ProfileRequiredFor<"package_manager"> = "desktop_compat_opt_in";
  const packageManagerSafeMode: AutoJs6Node.SafeDefaultModeFor<"package_manager"> = "partial";
  const packageManagerTargetMode: AutoJs6Node.TargetProfileModeFor<"package_manager"> = "partial";
  const packageManagerPackagedBehavior: AutoJs6Node.PackagedBehaviorFor<"package_manager"> = "partial";
  const autojsJavaPackagedBehavior: AutoJs6Node.PackagedBehaviorFor<"$autojs.java"> = "supported";
  const npmPureJsSafeMode: AutoJs6Node.SafeDefaultModeFor<"npm.pure_js"> = "partial";
  const workerRequiresDeclaration: AutoJs6Node.ProfileModuleRequiresDeclaration<"worker_threads"> = false;
  const toastRequiresDeclaration: AutoJs6Node.ProfileModuleRequiresDeclaration<"toast"> = false;
  const profileDeclarationMatrix: AutoJs6Node.ProfileDeclarationMatrix = {
    schema: "autojs6-node-profile-declaration-matrix-v1",
    status: "declaration_partial",
    profiles: [runtimeProfileId, proRuntimeProfileId, desktopRuntimeProfileId, debugRuntimeProfileId],
    modules: {} as AutoJs6Node.ProfileModuleCatalog,
    metadataOnly: true,
    grantsAuthority: false
  };
  const profileCatalogJava: AutoJs6Node.ProfileModuleFor<"java"> =
    profileDeclarationMatrix.modules.java;
  const profileCatalogWorker: AutoJs6Node.ProfileModuleFor<"worker_threads"> =
    profileDeclarationMatrix.modules.worker_threads;
  const profileCatalogRawNetwork: AutoJs6Node.ProfileModuleFor<"raw_network"> =
    profileDeclarationMatrix.modules.raw_network;
  const v12OverlayMode: AutoJs6Node.NodeProfileV12ModeFor<"ui.overlay", "pro_compat_opt_in"> = "profile_gated";
  const v12MediaProjectionMode: AutoJs6Node.NodeProfileV12ModeFor<"media_projection", "pro_compat_opt_in"> = "profile_gated";
  const v12WorkManagerMode: AutoJs6Node.NodeProfileV12ModeFor<"work_manager", "pro_compat_opt_in"> = "partial";
  const v12RecorderMode: AutoJs6Node.NodeProfileV12ModeFor<"recorder.session", "safe_default"> = "denied";
  const v12WorkerMode: AutoJs6Node.NodeProfileV12ModeFor<"worker_threads", "desktop_compat_opt_in"> = "available";
  const v12OverlayState: AutoJs6Node.NodeProfileV12StateFor<"ui.overlay"> = "provider_poc_not_promoted";
  const v12PackagedAggregateEvidence: AutoJs6Node.NodeProfileV12PackagedEvidenceFor<"packaged.aggregate"> = "host_contract_ready";
  const v12WorkerEvidence: AutoJs6Node.NodeProfileV12PackagedEvidenceFor<"worker_threads"> = "runtime_kit";
  const v12RecorderFutureOnly: AutoJs6Node.NodeProfileV12FutureOnlyFor<"recorder.session"> = true;
  const v12PackagedAggregateFutureOnly: AutoJs6Node.NodeProfileV12FutureOnlyFor<"packaged.aggregate"> = false;
  const v12FutureOnlyCapability: AutoJs6Node.NodeProfileV12FutureOnlyCapabilityName = "process_worker";
  const v12StableDeniedCapability: AutoJs6Node.NodeProfileV12StableDeniedCapabilityName = "media.playback";
  const v12DeclarationMatrix: AutoJs6Node.NodeProfileV12DeclarationMatrix = {
    schema: "autojs6-node-profile-v1-2-declaration-matrix-v1",
    profileVersion: "v1.2",
    status: "v1_2_type_declarations_ready",
    sourceGates: ["P14-07", "P14-10", "P14-11", "P14-13", "P14-14", "P14-15", "P14-17", "P14-19", "P14-21", "P14-22", "P14-23", "P14-25", "P14-26", "P14-31", "P14-32", "P14-34", "P14-35"],
    capabilities: {} as AutoJs6Node.NodeProfileV12CapabilityCatalog,
    metadataOnly: true,
    grantsAuthority: false
  };
  const v12CatalogOverlay: AutoJs6Node.NodeProfileV12CapabilityFor<"ui.overlay"> =
    v12DeclarationMatrix.capabilities["ui.overlay"];
  const v12CatalogWorker: AutoJs6Node.NodeProfileV12CapabilityFor<"worker_threads"> =
    v12DeclarationMatrix.capabilities.worker_threads;
  const v12CatalogPackagedAggregate: AutoJs6Node.NodeProfileV12CapabilityFor<"packaged.aggregate"> =
    v12DeclarationMatrix.capabilities["packaged.aggregate"];
  const v13OverlayMode: AutoJs6Node.NodeProfileV13ModeFor<"ui.overlay", "pro_compat_opt_in"> = "profile_gated";
  const v13MediaProjectionMode: AutoJs6Node.NodeProfileV13ModeFor<"media_projection", "pro_compat_opt_in"> = "profile_gated";
  const v13WorkManagerMode: AutoJs6Node.NodeProfileV13ModeFor<"work_manager", "pro_compat_opt_in"> = "partial";
  const v13RecorderSafeMode: AutoJs6Node.NodeProfileV13ModeFor<"recorder.session", "safe_default"> = "denied";
  const v13MediaPlaybackMode: AutoJs6Node.NodeProfileV13ModeFor<"media.playback", "pro_compat_opt_in"> = "denied";
  const v13OverlayState: AutoJs6Node.NodeProfileV13StateFor<"ui.overlay"> = "provider_poc_not_promoted";
  const v13SecurityState: AutoJs6Node.NodeProfileV13StateFor<"security.regression"> = "security_corpus_ready_no_authority";
  const v13PackagedAggregateEvidence: AutoJs6Node.NodeProfileV13PackagedEvidenceFor<"packaged.aggregate"> = "host_contract_ready";
  const v13SecurityEvidence: AutoJs6Node.NodeProfileV13PackagedEvidenceFor<"security.regression"> = "security_corpus_ready";
  const v13RecorderFutureOnly: AutoJs6Node.NodeProfileV13FutureOnlyFor<"recorder.session"> = true;
  const v13PackagedAggregateFutureOnly: AutoJs6Node.NodeProfileV13FutureOnlyFor<"packaged.aggregate"> = false;
  const v13FutureOnlyCapability: AutoJs6Node.NodeProfileV13FutureOnlyCapabilityName = "media.playback";
  const v13StableDeniedCapability: AutoJs6Node.NodeProfileV13StableDeniedCapabilityName = "mediastore";
  const v13DeclarationMatrix: AutoJs6Node.NodeProfileV13DeclarationMatrix = {
    schema: "autojs6-node-profile-v1-3-declaration-matrix-v1",
    profileVersion: "v1.3",
    status: "v1_3_type_declarations_ready",
    sourceGates: ["P14-35", "P15-11", "P15-22", "P15-23", "P15-24", "P15-25"],
    capabilities: {} as AutoJs6Node.NodeProfileV13CapabilityCatalog,
    metadataOnly: true,
    grantsAuthority: false
  };
  const v13CatalogOverlay: AutoJs6Node.NodeProfileV13CapabilityFor<"ui.overlay"> =
    v13DeclarationMatrix.capabilities["ui.overlay"];
  const v13CatalogSecurity: AutoJs6Node.NodeProfileV13CapabilityFor<"security.regression"> =
    v13DeclarationMatrix.capabilities["security.regression"];
  const v13CatalogPackagedAggregate: AutoJs6Node.NodeProfileV13CapabilityFor<"packaged.aggregate"> =
    v13DeclarationMatrix.capabilities["packaged.aggregate"];
  const typedCapabilityError = {} as AutoJs6Node.AutoJsBridgeCapabilityNotDeclaredError;
  const capabilityErrorProfiles: readonly string[] = typedCapabilityError.requiredProfiles;
  const capabilityErrorNodePermissions: readonly string[] = typedCapabilityError.requiredNodePermissions;
  const capabilityErrorAndroidPermissions: readonly string[] = typedCapabilityError.requiredAndroidPermissions;
  const filesystemProfileMode: string = autojsProfile.filesystemProfile.mode;
  const filesystemProfileRoots: readonly string[] = autojsProfile.filesystemProfile.additionalRoots;
  const filesystemAdvancedWatch: string = autojsProfile.filesystemProfile.advancedApis.watch;
  const filesystemRawFd: boolean = autojsProfile.filesystemProfile.advancedApis.rawFd;
  const filesystemWatcherLimit: number = autojsProfile.filesystemProfile.limits.watcherLimit;
  const workerThreadsProfileStatus: string = autojsProfile.workerThreadsProfile.status;
  const workerThreadsRequestEnabled: boolean = autojsProfile.workerThreadsProfile.requestEnabled;
  const workerThreadsMaxWorkers: number = autojsProfile.workerThreadsProfile.policy.maxWorkers;
  const workerThreadsBridgeModules: string = autojsProfile.workerThreadsProfile.bridgeModules;
  const processWorkerReplacementStatus: string = autojsProfile.processWorkerReplacementProfile.status;
  const processWorkerPreferredPrimitive: string = autojsProfile.processWorkerReplacementProfile.preferredPrimitive;
  const processWorkerMessageSizeBytes: number = autojsProfile.processWorkerReplacementProfile.messageSizeBytes;
  const processWorkerRawProcessHandles: boolean = autojsProfile.processWorkerReplacementProfile.rawProcessHandles;
  const vmProfileStatus: string = autojsProfile.vmProfile.status;
  const vmProfileTargetProfiles: readonly string[] = autojsProfile.vmProfile.targetProfiles;
  const vmProfileTimeout: string = autojsProfile.vmProfile.timeout;
  const vmProfileRawGlobalAccess: boolean = autojsProfile.vmProfile.rawGlobalAccess;
  const inspectorProfileStatus: string = autojsProfile.inspectorProfile.status;
  const inspectorProfileTargetProfiles: readonly string[] = autojsProfile.inspectorProfile.targetProfiles;
  const inspectorProfileAdbForwarding: string = autojsProfile.inspectorProfile.adbForwarding;
  const inspectorProfileRawSockets: boolean = autojsProfile.inspectorProfile.rawSockets;
  const wasiProfileStatus: string = autojsProfile.wasiProfile.status;
  const wasiProfileTargetProfiles: readonly string[] = autojsProfile.wasiProfile.targetProfiles;
  const wasiProfilePreopens: string = autojsProfile.wasiProfile.preopens;
  const wasiProfileRawFdNumbers: boolean = autojsProfile.wasiProfile.rawFdNumbers;
  const nativeAddonProfileStatus: string = autojsProfile.nativeAddonProfile.status;
  const nativeAddonProfileTargetProfiles: readonly string[] = autojsProfile.nativeAddonProfile.targetProfiles;
  const nativeAddonProfileAbiAllowlist: string = autojsProfile.nativeAddonProfile.abiAllowlist;
  const nativeAddonProfileHashAllowlist: string = autojsProfile.nativeAddonProfile.hashAllowlist;
  const nativeAddonProfileCrashIsolation: string = autojsProfile.nativeAddonProfile.crashIsolation;
  const nativeAddonProfileDownloadExec: boolean = autojsProfile.nativeAddonProfile.downloadExec;
  const childProcessProfileStatus: string = autojsProfile.childProcessProfile.status;
  const childProcessProfileTargetProfiles: readonly string[] = autojsProfile.childProcessProfile.targetProfiles;
  const childProcessProfileImplementation: string = autojsProfile.childProcessProfile.implementation;
  const childProcessProfileShellBridge: string = autojsProfile.childProcessProfile.shellBridge;
  const childProcessProfileAppPrivateBinaryAllowlist: string = autojsProfile.childProcessProfile.appPrivateBinaryAllowlist;
  const childProcessProfileRawHandles: boolean = autojsProfile.childProcessProfile.rawProcessHandles;
  const autojsVersionCode: number = $autojs.version.code;
  const autojsPackageName: string = $autojs.androidContext.packageName;
  const autojsContextAvailable: false = $autojs.androidContext.available;
  const autojsJavaEnabled: boolean = $autojs.java.enabled;
  const autojsJavaPolicy: AutoJs6Node.AutoJsJavaPolicy = $autojs.java.policy;
  const autojsJavaClass: AutoJs6Node.AutoJsJavaClassDescriptor = $autojs.java.findClass("java.lang.Math");
  const autojsJavaCall: Promise<unknown> = $autojs.java.callStatic(autojsJavaClass, "max", [1, 2], { timeoutMs: 1000 });
  const javaClass: AutoJs6Node.AutoJsJavaClassDescriptor = java.findClass("java.lang.Math");
  const javaCall: Promise<unknown> = java.callStatic(javaClass, "max", [1, 2], { timeoutMs: 1000 });
  const javaRect: Promise<AutoJs6Node.AutoJsJavaObjectHandle> = java.new("android.graphics.Rect", [0, 0, 4, 5], { timeoutMs: 1000 });
  const autojsJavaRect: Promise<AutoJs6Node.AutoJsJavaObjectHandle> = $autojs.java.new("android.graphics.Rect", [0, 0, 4, 5], { timeoutMs: 1000 });
  const autojsJavaWidth: Promise<unknown> = autojsJavaRect.then((rect) => rect.call("width", [], { timeoutMs: 1000 }));
  const autojsJavaField: Promise<unknown> = autojsJavaRect.then((rect) => $autojs.java.getField(rect, "left", { timeoutMs: 1000 }));
  const autojsJavaRelease: Promise<boolean> = autojsJavaRect.then((rect) => java.release(rect, { timeoutMs: 1000 }));
  const javaDefineClassDenied: (descriptor?: unknown) => never = java.defineClass;
  const autojsJavaDefineClassDenied: (descriptor?: unknown) => never = $autojs.java.defineClass;
  const autojsLoadDex: (dexFile: string) => never = $autojs.java.loadDex;

  void launched;
  void launchByName;
  void packageByName;
  void nameByPackage;
  void installed;
  void viewedFile;
  void editedFile;
  void launchAlias;
  void viewIntent;
  void shellIntent;
  void broadcastDenied;
  void compatIntent;
  void compatShellIntent;
  void compatBroadcastDenied;
  void confirmed;
  void input;
  void prompted;
  void selected;
  void hasClip;
  void sdk;
  void screenWidth;
  void density;
  void screenOn;
  void batteryIgnored;
  void batterySettings;
  void shellResult;
  void shellDefaultTimeout;
  void shellAccess;
  void shellFileResult;
  void rootShellResult;
  void shizukuShellDenied;
  void compatLaunch;
  void compatScreenOn;
  void compatBatteryIgnored;
  void audioVolume;
  void audioMaxVolume;
  void audioInfo;
  void compatAudioInfo;
  void mediaInfoSnapshot;
  void mediaInfoCallableSnapshot;
  void mediaInfoFormat;
  void compatMediaInfoSnapshot;
  void recorderStatus;
  void compatRecorderStatus;
  void recorderStart;
  void recorderStop;
  void compatShellResult;
  void compatRootShellResult;
  void compatExecRootResult;
  void compatShizukuDenied;
  void compatShellAccess;
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
  void rhinoSelector;
  void rhinoNode;
  void rhinoNodes;
  void rhinoClicked;
  void rhinoExists;
  void rhinoPointClicked;
  void rhinoEnabled;
  void rhinoStatus;
  void rhinoRunDenied;
  void rhinoRunPoc;
  void rhinoInstallDenied;
  void rhinoInstallResult;
  void rhinoImportClassDenied;
  void rhinoImportPackageDenied;
  void rhinoJavaAdapterDenied;
  void clickedText;
  void point;
  void matches;
  void colorPoint;
  void multiPoint;
  void size;
  void compatFind;
  void compatMatches;
  void compatColor;
  void compatMultiColor;
  void compatSaved;
  void compatScreenRequested;
  void compatImagesRequested;
  void compatCapturedToFile;
  void compatScreenStopped;
  void ocrResults;
  void ocrText;
  void ocrBounds;
  void compatOcrTexts;
  void compatOcrPathTexts;
  void compatOcrBounds;
  void compatOcrMode;
  void storedEnabled;
  void storageKeys;
  void storageContains;
  void runResult;
  void oneRow;
  void rows;
  void transactionResults;
  void dbClosed;
  void sqliteClosed;
  void timeoutHandle;
  void intervalHandle;
  void immediateHandle;
  void scopedPath;
  void encoded;
  void decoded;
  void hex;
  void formattedBytes;
  void convertedBytes;
  void standardizedBytes;
  void compatStandardizedBytes;
  void parsedMime;
  void nano;
  void inspected;
  void openccConvert;
  void openccS2t;
  void pinyinText;
  void pinyinRows;
  void pinyin4jText;
  void mathxMean;
  void mathxVariance;
  void mathxDistance;
  void mathxTargetSum;
  void compatMathxMedian;
  void arrayxDistinct;
  void arrayxSortedBy;
  void arrayxTargetUnion;
  void compatArrayxIntersect;
  void numberxClamp;
  void numberxParsed;
  void numberxTargetFixed;
  void compatNumberxPadded;
  void notificationId;
  void notificationStatus;
  void notificationSettings;
  void notificationSettingsAlias;
  void availableSensors;
  void sensorEvent;
  void sensorSub;
  void uiHandle;
  void unsubscribeUi;
  void compatUi;
  void compatUiHandle;
  void unsubscribeCompatUi;
  void overlayPermission;
  void overlaySettings;
  void overlayCloseSummary;
  void overlayShow;
  void overlayHandlePromise;
  void responseText;
  void responseJson;
  void fetchDiagnostics;
  void httpRequest;
  void httpsRequest;
  void httpListenDenied;
  void socketAddress;
  void parsedSocketAddress;
  void socketBlocked;
  void destroyedSocket;
  void netConnectDenied;
  void netListenDenied;
  void dnsLookup;
  void dnsLookupPromise;
  void dnsResolveDenied;
  void secureContext;
  void tlsSocket;
  void tlsCipher;
  void tlsConnectDenied;
  void tlsListenDenied;
  void axiosResponse;
  void axiosText;
  void axiosDiagnostics;
  void websocketDiagnostics;
  void periodicId;
  void tasks;
  void pluginInfo;
  void pluginEntry;
  void pluginResult;
  void profileEngine;
  void globalProfile;
  void esmLoaderProfileStatus;
  void esmLoaderProfileRequestEnabled;
  void esmLoaderProfileDynamicImport;
  void esmLoaderProfilePackageExports;
  void esmLoaderProfileLoaderHooks;
  void esmLoaderProfileRawNodeLoader;
  void packageManagerProfileStatus;
  void packageManagerProfileDefaultEnabled;
  void packageManagerProfileRuntimeInstall;
  void packageManagerProfileSourceAuthority;
  void packageManagerProfileAllowedSources;
  void packageManagerProfilePlanOperations;
  void packageManagerProfileMutationOperations;
  void packageManagerProfileNpmFacade;
  void packageManagerProfileRequireNpm;
  void packageManagerProfileNpmFacadeOperations;
  void packageManagerProfileLifecycleScripts;
  void packageManagerProfileBinLinks;
  void packageManagerProfileRawRegistryAccess;
  void stdlibProfileStatus;
  void stdlibProfileModules;
  void stdlibProfileCrypto;
  void stdlibProfileAndroidUnsupported;
  void stdlibProfileProcessGetBuiltin;
  void stdlibProfileRawDesktopParity;
  void processParityProfileStatus;
  void processParityProfileArgv;
  void processParityProfileEnvKeys;
  void processParityProfileExit;
  void processParityProfileSignals;
  void processParityProfileRawSignals;
  void packagedCapabilityProfileStatus;
  void packagedCapabilityProfileSources;
  void packagedCapabilityProfileNetwork;
  void packagedCapabilityProfileNativeAssets;
  void packagedMetadataProfile;
  void packagedMetadataBuiltins;
  void packagedMetadataNetworkRequested;
  void packagedMetadataGrantsAuthority;
  void pluginSystemProfileStatus;
  void pluginSystemProfileOperations;
  void pluginSystemProfilePermissions;
  void pluginSystemProfileNamespacePermissions;
  void pluginSystemProfilePackaged;
  void pluginSystemProfileNative;
  void packageIntegrityProfileStatus;
  void packageIntegrityProfileHash;
  void packageIntegrityProfileRecoveryActions;
  void packageIntegrityProfileSubjects;
  void packageIntegrityProfileUnsignedNative;
  void profileSelectionStatus;
  void profileSelectionEffective;
  void profileSelectionRequested;
  void profileSelectionHighRisk;
  void profileSelectionFailureHint;
  void profileSelectionDeclarationExamples;
  void profileRollbackStatus;
  void profileRollbackTarget;
  void profileRollbackProfileSwitches;
  void profileRollbackCapabilitySwitches;
  void profileRollbackStates;
  void capabilityRollbackStates;
  void rollbackTrigger;
  void packagedDowngrade;
  void packagedRemoteKillSwitchPolicy;
  void rollbackReleasePolicy;
  void privacyDisclosureStatus;
  void privacyDisclosureSurfaces;
  void privacyDisclosureChannel;
  void privacyDisclosurePackaged;
  void privacyDisclosureImplicitDenied;
  void privacyDisclosureDoctorPrivacy;
  void privacyDisclosureAutomation;
  void privacyDisclosureRemoteKillSwitch;
  void runtimeProfileId;
  void proRuntimeProfileId;
  void desktopRuntimeProfileId;
  void debugRuntimeProfileId;
  void safeToastMode;
  void safeJavaMode;
  void proJavaMode;
  void proOverlayMode;
  void safeWorkerMode;
  void desktopWorkerMode;
  void desktopHttpMode;
  void safeRawNetworkMode;
  void desktopRawNetworkMode;
  void desktopWasiMode;
  void debugInspectorMode;
  void safeInspectorMode;
  void packageManagerRequiredProfile;
  void packageManagerSafeMode;
  void packageManagerTargetMode;
  void packageManagerPackagedBehavior;
  void autojsJavaPackagedBehavior;
  void npmPureJsSafeMode;
  void workerRequiresDeclaration;
  void toastRequiresDeclaration;
  void profileDeclarationMatrix;
  void profileCatalogJava;
  void profileCatalogWorker;
  void profileCatalogRawNetwork;
  void v12OverlayMode;
  void v12MediaProjectionMode;
  void v12WorkManagerMode;
  void v12RecorderMode;
  void v12WorkerMode;
  void v12OverlayState;
  void v12PackagedAggregateEvidence;
  void v12WorkerEvidence;
  void v12RecorderFutureOnly;
  void v12PackagedAggregateFutureOnly;
  void v12FutureOnlyCapability;
  void v12StableDeniedCapability;
  void v12DeclarationMatrix;
  void v12CatalogOverlay;
  void v12CatalogWorker;
  void v12CatalogPackagedAggregate;
  void capabilityErrorProfiles;
  void capabilityErrorNodePermissions;
  void capabilityErrorAndroidPermissions;
  void filesystemProfileMode;
  void filesystemProfileRoots;
  void filesystemAdvancedWatch;
  void filesystemRawFd;
  void filesystemWatcherLimit;
  void workerThreadsProfileStatus;
  void workerThreadsRequestEnabled;
  void workerThreadsMaxWorkers;
  void workerThreadsBridgeModules;
  void processWorkerReplacementStatus;
  void processWorkerPreferredPrimitive;
  void processWorkerMessageSizeBytes;
  void processWorkerRawProcessHandles;
  void vmProfileStatus;
  void vmProfileTargetProfiles;
  void vmProfileTimeout;
  void vmProfileRawGlobalAccess;
  void inspectorProfileStatus;
  void inspectorProfileTargetProfiles;
  void inspectorProfileAdbForwarding;
  void inspectorProfileRawSockets;
  void wasiProfileStatus;
  void wasiProfileTargetProfiles;
  void wasiProfilePreopens;
  void wasiProfileRawFdNumbers;
  void nativeAddonProfileStatus;
  void nativeAddonProfileTargetProfiles;
  void nativeAddonProfileAbiAllowlist;
  void nativeAddonProfileHashAllowlist;
  void nativeAddonProfileCrashIsolation;
  void nativeAddonProfileDownloadExec;
  void childProcessProfileStatus;
  void childProcessProfileTargetProfiles;
  void childProcessProfileImplementation;
  void childProcessProfileShellBridge;
  void childProcessProfileAppPrivateBinaryAllowlist;
  void childProcessProfileRawHandles;
  void compatTimedTasks;
  void compatTimedTask;
  void compatDailyDenied;
  void compatWeeklyDenied;
  void compatUpdateDenied;
  void compatIntentAddDenied;
  void compatIntentQueryDenied;
  void compatIntentGetDenied;
  void compatIntentRemoveDenied;
  void packageManagerStatus;
  void packageManagerPolicy;
  void packageManagerSourcePolicy;
  void packageManagerPlanInstall;
  void packageManagerPlanUpdate;
  void packageManagerPlanRemove;
  void packageManagerPlanRegistryInstall;
  void packageManagerPlanTarballInstall;
  void packageManagerPackument;
  void packageManagerRegistryRequest;
  void packageManagerRangeRegistryRequest;
  void packageManagerTarballRequest;
  void packageManagerRemoteResolution;
  void packageManagerRemoteSelectorKind;
  void packageManagerRemoteIntegrityStatus;
  void packageManagerRemoteUrlStatus;
  void packageManagerInstall;
  void packageManagerUpdate;
  void packageManagerRemove;
  void packageManagerList;
  void packageManagerVerify;
  void packageManagerPrune;
  void npmStatus;
  void npmPolicy;
  void npmSourcePolicy;
  void npmPackageManager;
  void npmPackument;
  void npmRemoteResolution;
  void npmRemoteSelectorKind;
  void npmRemoteIntegrityStatus;
  void npmRemoteUrlStatus;
  void npmPlanRegistryInstall;
  void npmPlanTarballInstall;
  void npmInstall;
  void npmList;
  void npmCliDenied;
  void autojsVersionCode;
  void autojsPackageName;
  void autojsContextAvailable;
  void autojsJavaEnabled;
  void autojsJavaPolicy;
  void autojsJavaClass;
  void autojsJavaCall;
  void javaClass;
  void javaCall;
  void javaRect;
  void autojsJavaRect;
  void autojsJavaWidth;
  void autojsJavaField;
  void autojsJavaRelease;
  void javaDefineClassDenied;
  void autojsJavaDefineClassDenied;
  void autojsLoadDex;
}

void smoke;
