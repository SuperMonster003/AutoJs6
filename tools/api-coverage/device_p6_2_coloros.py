#!/usr/bin/env python3
"""Device sampling of api-coverage P6.2 on ColorOS (OnePlus OPD2413, Android 15): the authorization, provider binding and
foreground service entries of the new runtime APIs, in Rhino, Node main, Node Worker, Bun main and Bun Worker.

ColorOS differs from the other roadmap devices in what happens around a call, not in the API: it refuses to bind a plugin
whose process is not running, it freezes cached processes, and the host's wake of a plugin only starts with the host in
front or allowed to display over other apps. So the phases below look at those states: granted and revoked authorizations,
plugins and runtimes stopped before or during a script, and the host in the background with and without its foreground
service. The harness scripts of P3 / P4 are not repeated here (P3.5 / P4.5 ran 78 cases in four forms on this device).

One line per item: PASS / FAIL / SKIP and short values; tokens, screen text and tool lists are never printed. Each phase
writes <state>/<phase>.json, summary turns those into the tables of the report. No dialog is answered here: the MediaProjection
dialogs of the capture phase and the install page of a package new to the device are the maintainer's, and the phases that
need them say so and wait. The revocations use appops, settings and pm, are recorded by prepare and written back by unprepare.

Phases (all take --serial; everything but list also --state, a private directory):
  list                       the sampling list
  record / install --apk P=PATH / restore / verify   as in device_bun_js_tools.py, for the host, its test APK, Node, Bun
  state                      the system authorizations, the running Shizuku server and the provider versions
  prepare / unprepare        record the authorizations; turn on the accessibility service, the notification listener, the Bun
                             grants and stay awake; unprepare writes everything back
  auth [--classes C ...] [--forms ...]   the granted run of each authorization class
  denied [--classes C ...]   the same entries with the authorization taken away: an error or false, never a hang
  rebind [--variant background|foreground|no-overlay|background-late|no-overlay-late]   providers and runtimes stopped before each
                             script; the late variants call the providers 25 s after the start, when the host's own activity no longer counts
  midrun                     a provider stopped during a script; a runtime and the host stopped during a script
  background [--variant fgs-off|fgs-on] [--screen on|off] [--a11y on|off] [--seconds N]   host calls of a script while the host is in the background
  freeze [--forms ...]       the host without a foreground service, accessibility and listener: its state after 40 s, and whether the script goes on with the host back in front
  capture                    MediaProjection per form, the maintainer answers each dialog
  instrument                 PluginAutoWakeDeviceTest
  a11y                       the accessibility service after the host was killed, and what brings it back
  prompt [--step auto|grant] the shared storage prompt of Q228 (b); grant waits for the maintainer to answer it
  concurrent [--rounds N]    two host calls at once as the first calls of a fresh script, Bun against Node
  summary                    the Markdown tables of all phases that ran

Typical run: record, install, prepare, auth, denied, rebind (three variants), background (four combinations), prompt, concurrent,
capture, midrun, instrument, unprepare, restore. midrun and instrument stop the host, so prepare runs again if anything follows them.
The host only stops in background when it has no activity of its own and no binding keeps it out of the cached state: stop it first
(am force-stop) for background --a11y off and freeze. A host that was just in front stays up, which looks like a pass.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time
import uuid

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import device_bun_js_tools as base  # noqa: E402
import device_bun_device_media as device_media  # noqa: E402
import device_plugin_family as family  # noqa: E402

sample = base.sample
HOST, HOST_TEST, NODE, BUN = base.HOST, sample.HOST_TEST, sample.NODE, sample.BUN
base.PACKAGES = [HOST, HOST_TEST, NODE, BUN]
base.DEVICE_DIR = "/sdcard/Download/aj6-p62"
base.GRANTS_FILE = "p62-grants.json"
NL = chr(10)
PLUGIN = "io.github.supermonster003.autojs6.plugin."
MAPLE, BATCH, MACAW, HANZI = PLUGIN + "three.maple.image", PLUGIN + "batch.rename", PLUGIN + "three.macaw.speech", PLUGIN + "three.hanzi.cc"
OCR_PACKAGES = [PLUGIN + "paddleocr.v3", PLUGIN + "paddleocr.v5", PLUGIN + "paddleocr.v6.tiny"]
TOOL_PACKAGES = [PLUGIN + "imagequant", PLUGIN + "three.stack.archive", PLUGIN + "yolo.ncnn"]
SHIZUKU = "moe.shizuku.privileged.api"
A11Y = "org.autojs.autojs6/org.autojs.autojs.core.accessibility.AccessibilityServiceUsher"
LISTENER = "org.autojs.autojs6/org.autojs.autojs.core.notification.NotificationListenerService"
MUSIC_SERVICE = "org.autojs.autojs.core.media.ScriptMusicService"
APP_SERVICE = "org.autojs.autojs.external.foreground.AppForegroundService"
CAPTURE_SERVICE = "org.autojs.autojs.core.image.capture.ScreenCapturerForegroundService"
WAKE_TEST = "org.autojs.autojs.core.plugin.PluginAutoWakeDeviceTest"
FORMS = ["rhino", "node", "node-worker", "bun", "bun-worker"]
SETTINGS = [("secure", "enabled_accessibility_services"), ("secure", "accessibility_enabled"), ("secure", "enabled_notification_listeners"),
            ("global", "stay_on_while_plugged_in")]
# Node capabilities of the project and Bun grants of the runs. The grants are host preferences, prepare sets them and records them.
CAPABILITIES = ["engines", "engines.exec", "files", "files.write", "files.delete", "accessibility", "accessibility.gesture", "accessibility.service",
                "ui", "ui.overlay", "floaty.raw", "notifications", "notifications.settings", "events", "events.notification", "device", "device.power", "media",
                "media.playback", "shell", "shell.shizuku", "app.launch", "app.query", "clipboard", "installer", "image", "screen_capture", "ocr",
                "opencc", "gallery", "batch_rename", "speech", "plugin_tools", "settings", "settings.write"]
GRANTS = ["engines.control", "engines.exec", "files.control", "accessibility.control", "accessibility.gesture", "accessibility.service", "floaty.control", "floaty.raw",
          "notifications.post", "notifications.settings", "events.observe", "events.notification", "device.control", "device.power", "media.control",
          "settings.control", "settings.write", "shell.control", "shell.shizuku", "app.control", "clipboard.control", "installer.control", "image.process",
          "image.files", "screen.capture", "ocr.control", "text.opencc", "gallery.control", "batchrename.control", "speech.control", "plugintools.control"]
NODE_MODULES = [("auto", "autojs6:auto"), ("automator", "autojs6:automator"), ("device", "autojs6:device"), ("engines", "autojs6:engines"),
                ("events", "autojs6:events"), ("floaty", "autojs6:floaty"), ("media", "autojs6:media"), ("notice", "autojs6:notifications"),
                ("powerManager", "autojs6:power_manager"), ("shizuku", "autojs6:shizuku"), ("app", "autojs6:app"), ("files", "autojs6:files"),
                ("system", "autojs6:system"), ("installer", "autojs6:installer"), ("plugins", "autojs6:plugins"), ("gallery", "autojs6:gallery"),
                ("speech", "autojs6:speech"), ("opencc", "autojs6:opencc"), ("images", "images"), ("ocr", "ocr"), ("fs", "node:fs")]
NODE_ENV = "{" + ", ".join(f"{name}: require('{module}')" for name, module in NODE_MODULES) + "}"
BUN_ENV = ("{auto: host.auto, automator: host.automator, device: host.device, engines: host.engines, events: host.events, floaty: host.floaty, media: host.media,"
           " notice: host.notifications, powerManager: host.powerManager, shizuku: host.shizuku, app: host.app, files: host.files, system: host.global,"
           " installer: host.installer, plugins: host.plugins, gallery: host.gallery, speech: host.speech, opencc: host.opencc, images: host.images, ocr: host.ocr,"
           " fs: await import('node:fs')}")

# Why each class is worth a run on ColorOS, and what the system side of it is.
CLASSES = {
    "engines.exec": "a script of the plugin process starts an engine in the host, which ColorOS may have frozen",
    "storage": "all files access per plugin UID (Q228): the plugin process has no in-app entry, ColorOS has its own permission pages",
    "accessibility": "ColorOS stops and unbinds services of apps it treats as background; gestures go through the bound service",
    "overlay": "the overlay permission also decides whether the host can wake a plugin from the background",
    "notifications": "POST_NOTIFICATIONS and channels on a system with its own notification management",
    "notification-listener": "the listener service binding, which the system may drop for a background app",
    "foreground-service": "mediaPlayback start from the host in the background, and whether the service really runs in the foreground",
    "keep-awake": "ColorOS power management may strip wake locks from background apps",
    "battery": "the value the script reads against the system's own list",
    "background-start": "starting an activity while the host is in the background",
    "clipboard": "clipboard access of a background app",
    "shizuku": "the Shizuku binder from the host while the plugin process asks for it",
    "installer": "the install authorizers (queries only: a real install has a confirmation page)",
}


def case(cls, cid, rhino, runtime, expect="SAME", bun=None, watch=None, child=None, needs=None):
    """expect: "SAME" (Rhino's value), "ANY" (any value), {"code": ...} (throws that code), "THROWS", or a value; bun replaces it for Bun."""
    return {"class": cls, "id": cid, "rhino": rhino, "runtime": runtime, "expect": expect, "bun": bun, "watch": watch or {}, "child": child, "needs": needs}


AUTH_CASES = [
    case("engines.exec", "engines.execScript", "(function () { return typeof engines.execScript('p62-child', childSource(8100, 'child')); })()",
         "engines.execScript('p62-child', childSource(8100, 'child')).then(r => typeof r)", "object", child=8100),
    case("storage", "files.write", "(function () { var p = D + '/f.txt'; files.write(p, 'p62'); var r = String(files.read(p)); files.remove(p); return r; })()",
         "(async () => { const p = D + '/f.txt'; await files.write(p, 'p62'); const r = String(await files.read(p)); await files.remove(p); return r; })()", "p62"),
    case("storage", "fs.writeFileSync", None, "(() => { const p = '/sdcard/aj6-p62-' + AJ6_RUN + '.txt'; fs.writeFileSync(p, 'p62'); const r = fs.readFileSync(p, 'utf8'); fs.unlinkSync(p); return r; })()",
         "p62", bun={"code": ["EACCES", "EPERM"]}),
    case("accessibility", "auto.state", "[auto.isRunning(), auto.service !== null, auto.root !== null]",
         "(async () => [await auto.isRunning(), (await auto.service) !== null, (await auto.root) !== null])()", [True, True, True]),
    case("accessibility", "automator.press", "press(1, 1, 50)", "automator.press(1, 1, 50)", True),
    case("overlay", "floaty.permission", "[floaty.checkPermission(), floaty.hasPermission()]", "Promise.all([floaty.checkPermission(), floaty.hasPermission()])", [True, True]),
    case("overlay", "floaty.window", "(function () { var w = floaty.window('<frame/>'); var r = [w.isAdjustEnabled()]; w.setAdjustEnabled(true); sleep(100);"
         " r.push(w.isAdjustEnabled()); w.close(); return r; })()",
         "(async () => { const w = await floaty.window('<frame/>'); const r = [await w.isAdjustEnabled()]; await w.setAdjustEnabled(true);"
         " r.push(await w.isAdjustEnabled()); await w.close(); return r; })()", [False, True]),
    case("overlay", "floaty.rawWindow", "(function () { var w = floaty.rawWindow('<frame><text id=\"p62\" text=\"p62\"/></frame>'); w.setPosition(12, 34); sleep(300);"
         " var r = [w.getX(), w.getY()]; w.close(); return r; })()",
         "(async () => { const w = await floaty.rawWindow('<frame><text id=\"p62\" text=\"p62\"/></frame>'); await w.setPosition(12, 34); await pause(300);"
         " const r = [await w.x, await w.y]; await w.close(); return r; })()", [12, 34]),
    case("notifications", "notice.isEnabled", "notice.isEnabled()", "notice.isEnabled()", True),
    case("notifications", "notice.channel", "(function () { var id = String(notice.channel.create('p62_channel', {name: 'P62'})); var c = notice.channel.contains('p62_channel');"
         " var r = notice.channel.remove('p62_channel'); return [id, c, r]; })()",
         "(async () => { const id = await notice.channel.create('p62_channel', {name: 'P62'}); const c = await notice.channel.contains('p62_channel');"
         " const r = await notice.channel.remove('p62_channel'); return [id, c, r]; })()", ["p62_channel", True, True]),
    case("notifications", "notice.post", "(function () { notice('P62 ' + AJ6_FORM, 'post', {notificationId: 62001}); sleep(300); mark(8400, 1); sleep(1500); notice.cancel(62001); return true; })()",
         "(async () => { const id = await notice.notify({id: 62001, title: 'P62 ' + AJ6_FORM, text: 'post'}); await pause(300); mark(8400, 1); await pause(1500);"
         " await notice.cancel(id); return true; })()", True, watch={8400: "posted"}),
    case("foreground-service", "media.playMusic", "(function () { media.playMusic(WAV, 0, false); sleep(1200); mark(8300, media.isMusicPlaying()); sleep(1200); media.stopMusic(); return true; })()",
         "(async () => { await media.playMusic(WAV, 0, false); await pause(1200); mark(8300, await media.isMusicPlaying()); await pause(1200); await media.stopMusic(); return true; })()",
         True, watch={8300: "music-service"}),
    case("keep-awake", "device.keepAwake", "(function () { device.keepAwake(1, 8000); sleep(500); mark(8200, 1); sleep(2500); device.cancelKeepingAwake(); return true; })()",
         "(async () => { await device.keepAwake(1, 8000); await pause(500); mark(8200, 1); await pause(2500); await device.cancelKeepingAwake(); return true; })()",
         True, watch={8200: "wake-lock"}),
    case("battery", "powerManager.isIgnoringBatteryOptimizations", "powerManager.isIgnoringBatteryOptimizations()", "powerManager.isIgnoringBatteryOptimizations()"),
    case("background-start", "device.roms.isBackgroundStartGranted", "device.roms.isBackgroundStartGranted(context)", "device.roms.isBackgroundStartGranted()"),
    case("background-start", "app.launchPackage", "(function () { var r = app.launchPackage('com.android.settings'); sleep(1500); mark(8500, 1); sleep(1000); return r; })()",
         "(async () => { const r = await app.launchPackage('com.android.settings'); await pause(1500); mark(8500, 1); await pause(1000); return r; })()",
         True, watch={8500: "settings-resumed"}),
    # A background app reads an empty clipboard (Android 10), so the value follows the focus of the host: compared in the report, not here.
    case("clipboard", "setClip", "(function () { setClip('p62-clip'); return String(getClip()); })()", "system.setClip('p62-clip').then(() => system.getClip()).then(String)", "ANY"),
    case("shizuku", "shizuku.state", "(function (s) { return [Boolean(s.isInstalled), Boolean(s.isRunning)]; })(shizuku.state)", "(async () => [await shizuku.isInstalled(), await shizuku.isRunning()])()"),
    case("shizuku", "shizuku.exec", "String(shizuku('echo p62').result).trim()", "shizuku.execCommand('echo p62').then(r => String(r.result).trim())", "p62", needs="shizuku"),
    case("installer", "installer.isAvailable", "installer.isAvailable()", "installer.isAvailable()"),
    # Last: Rhino's event callback runs on the UI thread and the cases after it would too.
    case("notification-listener", "events.observeNotification~",
         "function (done) { var title = 'P62L ' + AJ6_FORM; events.observeNotification(); events.onNotification(function (n) { if (String(n.getTitle()) === title) { notice.cancel(62002);"
         " done(true); } }); setTimeout(function () { notice(title, 'listener', {notificationId: 62002}); }, 1500); }",
         "done => { const title = 'P62L ' + AJ6_FORM; (async () => { await events.observeNotification(); events.on('notification', async n => {"
         " const seen = typeof n.getTitle === 'function' ? await n.getTitle() : n.title; if (String(seen) === title) { await notice.cancelAll(); await events.close(); done(true); } });"
         " await pause(1500); await notice.notify({id: 62002, title, text: 'listener'}); })().catch(e => done(String(e && e.message))); }", True),
]

# class -> [case id, expected in the revoked state]; the setup of the state is in REVOKE.
DENIED = {
    "overlay": [["floaty.permission", [False, False]], ["floaty.window", "THROWS"], ["floaty.rawWindow", "THROWS"]],
    "storage": [["fs.writeFileSync", {"code": ["EACCES", "EPERM"]}]],
    "accessibility": [["auto.state", [False, False, False]], ["automator.press", "THROWS"]],
    "notifications": [["notice.isEnabled", False]],
}
PROVIDERS = [
    ["rename", [BATCH], "String(files.batchRename.preview('IMG_0001.JPG', {search: 'IMG_', replace: 'Photo_'}).newName)",
     "files.batchRename.preview('IMG_0001.JPG', {search: 'IMG_', replace: 'Photo_'}).then(r => String(r.newName))"],
    ["gallery", [MAPLE], "String(gallery.convert(D + '/a.png', {format: 'jpeg', quality: 80, background: '#ffffff'}).name)",
     "gallery.convert(D + '/a.png', {format: 'jpeg', quality: 80, background: '#ffffff'}).then(r => String(r.name))"],
    ["opencc", [HANZI], "String(opencc.s2t('汉语'))", "opencc.s2t('汉语').then(String)"],
    ["speech", [MACAW], "speech.speaker.list().length", "speech.speaker.list().then(l => Array.from(l).length)"],
    ["ocr", OCR_PACKAGES, "(function () { var im = images.read(D + '/a.png'); var r = ocr.paddle.detect(im); im.recycle(); return r.length; })()",
     "(async () => { const im = await images.readImage(D + '/a.png'); const r = await ocr.paddle.detect(im); await images.recycle(im); return Array.from(r).length; })()"],
    ["tools", TOOL_PACKAGES, "plugins.tools.list().length > 0", "plugins.tools.list().then(l => l.length > 0)"],
]
LATE_MS = 25000
PROMPT = "PluginStorageAccessActivity"
REFUSED = ["EACCES", "EPERM"]
WRITE_JS = ("(() => { const p = '/sdcard/aj6-p62-' + AJ6_RUN + '.txt'; try { fs.writeFileSync(p, 'p62'); const r = fs.readFileSync(p, 'utf8'); fs.unlinkSync(p); return r; }"
            " catch (e) { console.error(e); return String(e.code); } })()")
PRINTED_JS = "(() => { console.error(\"EACCES: permission denied, open '/sdcard/aj6-p62-none.txt'\"); return 'printed'; })()"
CONCURRENT = [("getAccess", "[shizuku.isInstalled(), shizuku.isRunning()]"), ("isScreenOn", "[device.isScreenOn(), device.isScreenOn()]")]
TICK_RHINO = "(function () { var i = 0; for (; i < AJ6_TICKS; i++) { var t = Date.now(); device.isScreenOn(); mark(8600 + i, [Date.now(), Date.now() - t]); sleep(3000); } return i; })()"
TICK_RUNTIME = "(async () => { let i = 0; for (; i < AJ6_TICKS; i++) { const t = Date.now(); await device.isScreenOn(); mark(8600 + i, [Date.now(), Date.now() - t]); await pause(3000); } return i; })()"
CAPTURE_RHINO = ("(function () { mark(8700, 1); images.requestScreenCapture(); sleep(800); mark(8701, 1); sleep(1500); var im = images.captureScreen(); var r = [im.getWidth() > 0];"
                 " im.recycle(); images.stopScreenCapture(); return r; })()")
CAPTURE_RUNTIME = ("(async () => { mark(8700, 1); await images.requestScreenCapture({timeoutMs: 120000}); await pause(800); mark(8701, 1); const im = await images.captureScreen();"
                   " const r = [im.width > 0]; await images.recycle(im); await images.stopScreenCapture(); return r; })()")

HEADER_JS = """
function childSource(index, value) {
    return "console.log('" + "AJ6JST " + AJ6_RUN + " " + index + " 1/1 " + encodeURIComponent(JSON.stringify(value)) + " $');";
}
"""
# Rhino: the cases run one after the other on the script thread, so that a case with an id ending in "~" can wait for events.
RHINO_JS = """
function mark(index, value) { aj6Emit(AJ6_RUN, index, value); }
function aj6Step(i) {
    if (i === AJ6_TABLE.length) { aj6Emit(AJ6_RUN, -1, {done: i}); setTimeout(function () { exit(); }, 300); return; }
    var id = AJ6_TABLE[i][0], source = AJ6_TABLE[i][1], timer = null, settled = false;
    var finish = function (result) {
        if (settled) return;
        settled = true;
        if (timer !== null) clearTimeout(timer);
        aj6Emit(AJ6_RUN, i, result);
        setTimeout(function () { aj6Step(i + 1); }, 20);
    };
    if (source === null) { finish({skip: 1}); return; }
    try {
        if (id.charAt(id.length - 1) === '~') {
            timer = setTimeout(function () { finish({th: {name: 'CaseTimeout', code: null, message: 'no event'}}); }, AJ6_TIMEOUT);
            eval('(' + source + ')')(function (value) { finish({ok: value === undefined ? null : value}); });
        } else {
            var value = eval('(' + source + ')');
            finish({ok: value === undefined ? null : value});
        }
    } catch (e) {
        finish({th: {name: String(e.name), code: e.code === undefined || e.code === null ? null : String(e.code), message: String(e.message)}});
    }
}
aj6Step(0);
"""
RUNTIME_JS = """
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function aj6Run(modules, mark, emit) {
    const env = {...modules, D, WAV, AJ6_RUN, AJ6_FORM, AJ6_TICKS, childSource, mark, pause}, names = Object.keys(env), values = names.map(name => env[name]);
    for (let i = 0; i < AJ6_TABLE.length; i++) {
        const [id, source] = AJ6_TABLE[i];
        if (source === null) { emit(i, {skip: 1}); continue; }
        let result, timer;
        const timeout = new Promise((_, reject) => { timer = setTimeout(() => reject(Object.assign(new Error('no answer'), {name: 'CaseTimeout'})), AJ6_TIMEOUT); });
        try {
            const body = new Function(...names, 'return (' + source + ');');
            const value = id.endsWith('~') ? new Promise(done => body(...values)(done)) : body(...values);
            const answer = await Promise.race([value, timeout]);
            result = {ok: answer === undefined ? null : answer};
        } catch (e) {
            result = {th: {name: String(e && e.name), code: e && e.code !== undefined && e.code !== null ? String(e.code) : null, message: String(e && e.message)}};
        }
        clearTimeout(timer);
        emit(i, result);
    }
}
"""


def common(form, run, table, ticks, timeout):
    directory = f"{base.DEVICE_DIR}/{run}"
    keyword = "var" if form == "rhino" else "const"
    return (f"{keyword} AJ6_RUN = '{run}', AJ6_FORM = '{form}', D = '{directory}', WAV = D + '/p62.wav', AJ6_TICKS = {ticks}, AJ6_TIMEOUT = {timeout};" + NL
            + f"{keyword} AJ6_TABLE = {json.dumps(table, ensure_ascii=False)};" + NL + HEADER_JS)


def script(form, run, table, ticks=0, timeout=45000):
    """The script of one form: the expressions of [table] ([id, expression or None]) with their results as console lines."""
    head = common(form, run, table, ticks, timeout)
    if form == "rhino":
        return head + base.EMIT_JS + RHINO_JS
    done = f"{{done: {len(table)}}}"
    if form == "node":
        return ('"nodejs";' + NL + head + base.EMIT_JS + RUNTIME_JS + "const mark = (i, v) => aj6Emit(AJ6_RUN, i, v);" + NL
                + f"aj6Run({NODE_ENV}, mark, mark).catch(e => aj6Emit(AJ6_RUN, -2, {{error: String(e && e.stack || e)}})).finally(() => aj6Emit(AJ6_RUN, -1, {done}));" + NL)
    if form == "node-worker":
        inner = (head + RUNTIME_JS + "const {parentPort} = require('node:worker_threads'), post = (i, v) => parentPort.postMessage([i, v]);" + NL
                 + f"aj6Run({NODE_ENV}, post, post).catch(e => post(-2, String(e && e.stack || e))).then(() => post(-1, null));" + NL)
        return ('"nodejs";' + NL + f"const AJ6_RUN = '{run}';" + NL + base.EMIT_JS + "const {Worker} = require('node:worker_threads');" + NL
                + f"const worker = new Worker({json.dumps(inner)}, {{eval: true}});" + NL
                + "worker.on('message', ([i, r]) => { if (i === -1) worker.terminate(); else if (i === -2) aj6Emit(AJ6_RUN, -2, {error: r}); else aj6Emit(AJ6_RUN, i, r); });" + NL
                + "worker.on('error', e => aj6Emit(AJ6_RUN, -2, {error: String(e && e.stack || e)}));" + NL
                + f"worker.on('exit', () => aj6Emit(AJ6_RUN, -1, {done}));" + NL)
    client = "const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE); const host = await createHostClient();"
    if form == "bun":
        return ('"bun";' + NL + head + base.EMIT_JS + RUNTIME_JS + "const mark = (i, v) => aj6Emit(AJ6_RUN, i, v);" + NL
                + f"(async () => {{ try {{ {client} await aj6Run({BUN_ENV}, mark, mark); }} catch (e) {{ aj6Emit(AJ6_RUN, -2, {{error: String(e && e.stack || e)}}); }}" + NL
                + f"aj6Emit(AJ6_RUN, -1, {done}); }})();" + NL)
    inner = (head + RUNTIME_JS + "const post = (i, v) => postMessage([i, v]);" + NL
             + f"self.onmessage = async () => {{ try {{ {client} await aj6Run({BUN_ENV}, post, post); }} catch (e) {{ post(-2, String(e && e.stack || e)); }} post(-1, null); }};" + NL)
    return ('"bun";' + NL + f"const AJ6_RUN = '{run}';" + NL + base.EMIT_JS + "(async () => { await new Promise(resolve => {" + NL
            + f"const w = new Worker(URL.createObjectURL(new Blob([{json.dumps(inner)}], {{type: 'application/javascript'}})));" + NL
            + "w.onmessage = event => { const [i, r] = event.data; if (i === -1) { w.terminate(); resolve(); } else if (i === -2) aj6Emit(AJ6_RUN, -2, {error: r}); else aj6Emit(AJ6_RUN, i, r); };" + NL
            + "w.onerror = event => { aj6Emit(AJ6_RUN, -2, {error: String(event.message)}); resolve(); };" + NL
            + f"w.postMessage(1); }}); aj6Emit(AJ6_RUN, -1, {done}); }})();" + NL)


def run_form(args, form, table, watch=None, wait=None, ticks=0, timeout=45000, foreground=False):
    """Pushes a run directory with the script of [form] and its fixtures, starts it and returns the results; [watch] maps a marked
    index to a callback that runs on this device state while the script waits."""
    run = uuid.uuid4().hex[:8]
    local = HERE / "__pycache__" / f"p62_{run}"
    local.mkdir(parents=True)
    directory = f"{base.DEVICE_DIR}/{run}"
    answer = {"results": {}, "fired": {}, "seconds": None, "first": None, "arrived": {}}
    try:
        main = "main.js" if form.startswith("node") else f"aj6_{run}.js"
        (local / main).write_text(script(form, run, table, ticks, timeout), encoding="utf-8")
        if form.startswith("node"):
            (local / "project.json").write_text(json.dumps(family.project(CAPABILITIES)), encoding="utf-8")
        (local / "p62.wav").write_bytes(device_media.silent_wav())
        (local / "a.png").write_bytes(family.png(8, 6, (51, 102, 153, 255)))
        sample.adb(args.serial, "shell", "mkdir", "-p", base.DEVICE_DIR)
        sample.adb(args.serial, "push", str(local), directory, timeout=120)
        collector = base.Collector(args.serial, run)
        try:
            if foreground:
                sample.adb(args.serial, "shell", "monkey", "-p", HOST, "-c", "android.intent.category.LAUNCHER", "1", check=False)
                time.sleep(2)
            time.sleep(0.5)
            started = time.monotonic()
            sample.adb(args.serial, "shell", "am", "start", "-n", f"{HOST}/{base.RUN_ACTIVITY}", "-e", "path", f"{directory}/{main}")
            deadline = started + (wait or args.wait)
            while not collector.done.is_set() and time.monotonic() < deadline:
                for index in list(collector.results):
                    answer["arrived"].setdefault(index, round(time.monotonic() - started, 2))
                if answer["first"] is None and collector.results:
                    answer["first"] = min(answer["arrived"].values())
                for index, callback in (watch or {}).items():
                    if index in collector.results and index not in answer["fired"]:
                        answer["fired"][index] = callback()
                time.sleep(0.05)
            answer["seconds"] = round(time.monotonic() - started, 1)
            time.sleep(0.5)
            answer["results"] = dict(collector.results)
        finally:
            collector.close()
    finally:
        shutil.rmtree(local, ignore_errors=True)
        sample.adb(args.serial, "shell", "rm", "-rf", directory, check=False)
    if -2 in answer["results"]:
        print(f"{form}: runner error {str(answer['results'][-2].get('error', ''))[:400]}")
    return answer


def uid_of(serial, package):
    match = re.search(r"uid:(\d+)", sample.sh(serial, f"pm list packages -U {package}"))
    return match.group(1) if match else None


def wake_lock_count(args):
    uid = uid_of(args.serial, HOST)
    lines = [line for line in sample.sh(args.serial, "dumpsys power").splitlines() if "WAKE_LOCK" in line and f"uid={uid}" in line]
    return bool(lines), len(lines)


def service_state(args, name):
    out = sample.sh(args.serial, f"dumpsys activity services {HOST}/{name}")
    return "isForeground=true" in out, ("foreground" if "isForeground=true" in out else "running" if "ServiceRecord" in out else "absent")


def posted(args):
    out = sample.sh(args.serial, "dumpsys notification --noredact")
    count = len(re.findall(r"android\.title=String \(P62", out))
    return count > 0, count


def settings_resumed(args):
    page = resumed_activity(args)
    sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
    return "com.android.settings" in page or "com.oplus" in page, page[:80]


def checks(args):
    return {"wake-lock": lambda: wake_lock_count(args), "music-service": lambda: service_state(args, MUSIC_SERVICE), "posted": lambda: posted(args),
            "settings-resumed": lambda: settings_resumed(args)}


def short(result):
    if result is None:
        return "missing"
    if "skip" in result:
        return "skip"
    if "th" in result:
        return f"throws {result['th'].get('name')} {result['th'].get('code') or ''} {str(result['th'].get('message'))[:60]}".strip()
    return json.dumps(result.get("ok"), ensure_ascii=False)[:60]


def judge(item, form, rhino, answer):
    """'pass' or the reason of a failure of [item] in [form]."""
    actual = answer["results"].get(item["index"])
    if actual is None:
        return "missing"
    if "skip" in actual:
        return "pass"
    wanted = item["bun"] if item["bun"] is not None and form.startswith("bun") else item["expect"]
    if wanted == "SAME":
        wanted = (rhino or {}).get("ok")
    if "th" in actual:
        code = actual["th"].get("code")
        if wanted == "THROWS" or (isinstance(wanted, dict) and "code" in wanted and code in wanted["code"]):
            return "pass"
        return short(actual)
    if wanted not in ("ANY",) and actual.get("ok") != wanted:
        return f"expected {json.dumps(wanted, ensure_ascii=False)[:60]}, got {short(actual)}"
    for index, name in item["watch"].items():
        fired = answer["fired"].get(index)
        if not fired or not fired[0]:
            return f"{name}: {fired[1] if fired else 'never marked'}"
    if item["child"] is not None and item["child"] not in answer["results"]:
        return "the started engine reported nothing"
    return "pass"


def save(args, name, data):
    args.state.mkdir(parents=True, exist_ok=True)
    (args.state / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, indent=1) + NL, encoding="utf-8")


def forms_of(args):
    return args.forms.split(",")


def shizuku_running(args):
    return "shizuku_server" in sample.adb(args.serial, "shell", "ps -A", check=False)


def run_cases(args, cases, label):
    """Runs [cases] in every form and prints one line per case; the expression of a form is None where a case does not apply."""
    forms, check_table, answers = forms_of(args), checks(args), {}
    if "SAME" in [item["expect"] for item in cases] and "rhino" not in forms:
        forms = ["rhino"] + forms
    for item_index, item in enumerate(cases):
        item["index"] = item_index
    for form in forms:
        table = [[item["id"], item["rhino"] if form == "rhino" else item["runtime"]] for item in cases]
        watch = {}
        for item in cases:
            watch.update({index: check_table[name] for index, name in item["watch"].items()})
        sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
        answers[form] = run_form(args, form, table, watch, foreground=args.foreground)
        results = answers[form]["results"]
        print(f"{label} {form}: {len([i for i in results if 0 <= i < len(cases)])} / {len(cases)} in {answers[form]['seconds']} s, first answer {answers[form]['first']} s")
    rows, failed = [], 0
    for item in cases:
        rhino = answers.get("rhino", {}).get("results", {}).get(item["index"])
        verdicts = {form: judge(item, form, rhino, answers[form]) for form in forms}
        bad = {form: verdict for form, verdict in verdicts.items() if verdict != "pass"}
        failed += bool(bad)
        values = {form: short(answers[form]["results"].get(item["index"])) for form in forms}
        fired = {str(index): list(answers[form]["fired"].get(index, [])) for form in forms for index in item["watch"]}
        rows.append({"class": item["class"], "id": item["id"], "verdicts": verdicts, "values": values, "fired": fired})
        print(f"{'FAIL' if bad else 'PASS'} {item['class']} {item['id']}: " + " ".join(f"{form}={'ok' if verdict == 'pass' else 'FAIL'}" for form, verdict in verdicts.items())
              + f" value={values.get('node', values.get(forms[0]))}")
        for form, verdict in bad.items():
            print(f"  {form}: {verdict[:200]}")
    print(f"{label}: {len(rows) - failed} / {len(rows)} pass")
    return rows, failed


def phase_auth(args):
    cases = [dict(item) for item in AUTH_CASES if (not args.classes or item["class"] in args.classes)]
    running = shizuku_running(args)
    for item in cases:
        if item["needs"] == "shizuku" and not running:
            item["rhino"] = item["runtime"] = None
    print(f"shizuku server {'running' if running else 'not running: shizuku.exec is skipped'}")
    rows, failed = run_cases(args, cases, "auth")
    save(args, "-".join(["auth", *args.classes]), {"rows": rows, "shizuku": running})
    return 1 if failed else 0


def set_state(args, cls, on):
    """Takes the authorization of [cls] away, or gives it back with the recorded value."""
    saved = load_auth(args)
    if cls == "overlay":
        sample.sh(args.serial, f"appops set {HOST} SYSTEM_ALERT_WINDOW {'deny' if not on else saved['overlay']}")
    elif cls == "storage":
        sample.sh(args.serial, f"appops set --uid {NODE} MANAGE_EXTERNAL_STORAGE {'deny' if not on else saved['nodeStorage']}")
    elif cls == "accessibility":
        services = saved["prepared/enabled_accessibility_services"] if on else saved["secure/enabled_accessibility_services"]
        sample.sh(args.serial, "settings delete secure enabled_accessibility_services" if services == "null" else f"settings put secure enabled_accessibility_services {services}")
        if on:
            sample.sh(args.serial, "settings put secure accessibility_enabled 1")
    elif cls == "bindings":
        # Either system binding keeps the host out of the cached state, which most scripts cannot count on.
        set_state(args, "accessibility", on)
        sample.sh(args.serial, f"cmd notification {'allow' if on else 'disallow'}_listener {LISTENER}")
    elif cls == "notifications":
        sample.sh(args.serial, f"pm {'grant' if on else 'revoke'} {HOST} android.permission.POST_NOTIFICATIONS")
        if on:
            # Revoking killed the host, see phase_a11y.
            rebind_accessibility(args)
    time.sleep(2)


def accessibility_state(args):
    out = sample.sh(args.serial, "dumpsys accessibility")
    bound = re.search(r"Bound services:\{(.*?)\}", out)
    crashed = re.search(r"Crashed services:\{(.*)", out)
    return {"bound": bool(bound and bound.group(1).strip()), "crashed": bool(crashed and "AccessibilityServiceUsher" in crashed.group(1)),
            "enabledFlag": sample.sh(args.serial, "settings get secure accessibility_enabled")}


def rebind_accessibility(args):
    """Takes the host's service off the enabled list and puts it back, which is what binds it again."""
    saved = load_auth(args)
    sample.sh(args.serial, "settings delete secure enabled_accessibility_services")
    time.sleep(1)
    sample.sh(args.serial, f"settings put secure enabled_accessibility_services {saved['prepared/enabled_accessibility_services']}")
    sample.sh(args.serial, "settings put secure accessibility_enabled 1")
    time.sleep(2.5)


def auto_running(args):
    return short(run_form(args, "rhino", [["running", "auto.isRunning()"]], wait=30)["results"].get(0))


def phase_a11y(args):
    """The accessibility service after the host's process was killed in three ways, then after rewriting the setting."""
    rows, failed = [], 0
    for label, command in (("crash", f"am crash {HOST}"), ("force-stop", f"am force-stop {HOST}"), ("permission revoked", f"pm revoke {HOST} android.permission.POST_NOTIFICATIONS")):
        sample.adb(args.serial, "shell", "monkey", "-p", HOST, "-c", "android.intent.category.LAUNCHER", "1", check=False)
        time.sleep(3)
        sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
        rebind_accessibility(args)
        before = accessibility_state(args)["bound"]
        sample.adb(args.serial, "shell", command, check=False)
        time.sleep(3)
        if label == "permission revoked":
            sample.sh(args.serial, f"pm grant {HOST} android.permission.POST_NOTIFICATIONS")
        time.sleep(5)
        running = auto_running(args)
        time.sleep(6)
        after = accessibility_state(args)
        running = auto_running(args)
        rebind_accessibility(args)
        restored, again = accessibility_state(args)["bound"], auto_running(args)
        ok = before and restored and again == "true"
        failed += not ok
        rows.append({"kill": label, "boundBefore": before, "boundAfter14s": after["bound"], "crashedList": after["crashed"], "enabledFlag": after["enabledFlag"], "autoRunning": running,
                     "boundAfterRewrite": restored, "autoRunningAfterRewrite": again})
        print(f"{'PASS' if ok else 'FAIL'} a11y {label}: bound before {before}, 14 s after the kill {after['bound']} (crashed list {after['crashed']}, flag {after['enabledFlag']}, auto.isRunning {running}),"
              f" after rewriting the setting {restored} ({again})")
    sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
    save(args, "a11y", {"rows": rows})
    return 1 if failed else 0


def phase_denied(args):
    rows, failed = [], 0
    for cls, wanted in DENIED.items():
        if args.classes and cls not in args.classes:
            continue
        by_id = {item["id"]: item for item in AUTH_CASES}
        cases = [{**by_id[cid], "expect": value, "bun": ({"code": ["EACCES", "EPERM"]} if cls == "storage" else None), "watch": {}, "child": None} for cid, value in wanted]
        set_state(args, cls, False)
        try:
            rows_, failed_ = run_cases(args, cases, f"denied {cls}")
        finally:
            set_state(args, cls, True)
        rows += rows_
        failed += failed_
    save(args, "-".join(["denied", *args.classes]), {"rows": rows})
    return 1 if failed else 0


def stop_packages(args, packages):
    for package in packages:
        sample.sh(args.serial, f"am force-stop {package}")


def wake_log(args):
    """The PluginAutoWake lines of the logcat since the run began: packages woken, wakes that failed and bindings given up."""
    out = sample.adb(args.serial, "logcat", "-d", "-v", "raw", "-s", "PluginAutoWake:*", check=False)
    return {"woke": re.findall(r"woke (\S+) after a refused binding", out), "failed": re.findall(r"could not wake (\S+)", out),
            "gaveUp": re.findall(r"binding (\S+) was still refused", out)}


def pid_of(args, process):
    return sample.adb(args.serial, "shell", f"pidof {process}", check=False).strip()


def phase_rebind(args):
    """Each form: stop the providers, then call each of them; on ColorOS the first call only binds through a wake."""
    forms, table = forms_of(args), PROVIDERS if not args.providers else [row for row in PROVIDERS if row[0] in args.providers]
    denied = args.variant.startswith("no-overlay")
    late = args.variant.endswith("-late")
    if denied:
        set_state(args, "overlay", False)
    if args.variant == "no-overlay-late":
        # A bound accessibility service also lets the host start activities from the background.
        set_state(args, "bindings", False)
    rows, failed, rhino = [], 0, {}
    try:
        for form in forms:
            for _, packages, _, _ in table:
                stop_packages(args, packages)
            if form.startswith("node"):
                stop_packages(args, [NODE])
            elif form.startswith("bun"):
                stop_packages(args, [BUN])
            sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
            time.sleep(1)
            sources = [[row[0], row[2] if form == "rhino" else row[3]] for row in table]
            if late:
                # The host may start activities for some seconds after its own activity: the wake has to come after that.
                sources.insert(0, ["late", f"sleep({LATE_MS})" if form == "rhino" else f"pause({LATE_MS})"])
            answer = run_form(args, form, sources, wait=args.wait, foreground=args.variant == "foreground")
            log = wake_log(args)
            for index, (name, packages, _, _) in enumerate(table, 1 if late else 0):
                result = answer["results"].get(index)
                if form == "rhino":
                    rhino[name] = result
                expected_ok = (rhino.get(name) or {}).get("ok")
                good = result is not None and "skip" not in result and "th" not in result and (form == "rhino" or result.get("ok") == expected_ok)
                woke = sum(package in log["woke"] for package in packages)
                state = "pass" if good else "fail"
                failed += not good and not denied
                rows.append({"form": form, "provider": name, "state": state, "value": short(result), "woke": woke, "failedWake": sum(package in log["failed"] for package in packages),
                             "gaveUp": sum(package in log["gaveUp"] for package in packages)})
                print(f"{'PASS' if good else 'DENIED' if denied else 'FAIL'} {args.variant} {form} {name}: woke {woke} value={short(result)}")
            runtime = NODE if form.startswith("node") else BUN if form.startswith("bun") else None
            if runtime:
                print(f"  {form}: runtime woken {runtime in log['woke']}, first answer {answer['first']} s, total {answer['seconds']} s")
                rows.append({"form": form, "provider": "runtime", "state": "pass" if answer["first"] is not None else "fail", "value": f"first answer {answer['first']} s",
                             "woke": int(runtime in log["woke"]), "failedWake": 0, "gaveUp": 0})
    finally:
        if denied:
            set_state(args, "overlay", True)
        if args.variant == "no-overlay-late":
            set_state(args, "bindings", True)
    save(args, f"rebind-{args.variant}", {"rows": rows})
    print(f"rebind {args.variant}: {len([row for row in rows if row['state'] == 'pass'])} / {len(rows)} pass")
    return 1 if failed else 0


def script_stops(args, form, runtime):
    """A runtime stopped during a script: the execution ends, no engine is left and the next script of that runtime runs."""
    table = [["loop", "(async () => { mark(8800, 1); for (let i = 0; i < 40; i++) { await device.isScreenOn(); await pause(500); } return 'survived'; })()"]]
    answer = run_form(args, form, table, {8800: lambda: stop_packages(args, [runtime]) or (True, "stopped")}, wait=25)
    survived = 0 in answer["results"]
    left = run_form(args, "rhino", [["engines", "engines.all().length"]], wait=30)["results"].get(0)
    again = run_form(args, form, [["again", "device.isScreenOn().then(() => 'again')"]], wait=60)["results"].get(0)
    ok = not survived and (left or {}).get("ok") == 1 and (again or {}).get("ok") == "again"
    print(f"{'PASS' if ok else 'FAIL'} midrun runtime stopped {form}: script ended {not survived}, engines left {short(left)}, next script {short(again)}")
    return {"item": f"runtime stopped ({form})", "ok": ok, "value": f"engines left {short(left)}, next script {short(again)}"}


def file_size(args, path):
    out = sample.adb(args.serial, "shell", f"stat -c %s {path}", check=False).strip()
    return int(out) if out.isdigit() else None


def host_stops(args, form, process, runtime):
    """The host stopped during an endless script that appends to a file: does the script go on without its host, and does the next script run?"""
    path = f"{base.DEVICE_DIR}/tick-{form}.txt"
    table = [["loop", f"(async () => {{ mark(8801, 1); for (;;) {{ fs.appendFileSync('{path}', 'x'); await Promise.race([device.isScreenOn().catch(() => null), pause(1000)]); await pause(500); }} }})()"]]
    seen = {}

    def stop():
        before = pid_of(args, process)
        sample.sh(args.serial, f"am force-stop {HOST}")
        time.sleep(5)
        seen["at5s"] = file_size(args, path)
        time.sleep(60)
        seen.update(at65s=file_size(args, path), sameProcess=pid_of(args, process) == before)
        return True, None
    run_form(args, form, table, {8801: stop}, wait=85)
    again = run_form(args, form, [["again", "device.isScreenOn().then(() => 'again')"]], wait=60)["results"].get(0)
    time.sleep(10)
    seen.update(afterNextScript=file_size(args, path), again=short(again), process=process_state(args, process))
    stop_packages(args, [runtime])
    sample.adb(args.serial, "shell", f"rm -f {path}", check=False)
    ok = (again or {}).get("ok") == "again"
    print(f"{'PASS' if ok else 'FAIL'} midrun host stopped {form}: next script {seen['again']}, ticks of the old script 5 s after the kill {seen['at5s']}, 65 s {seen['at65s']},"
          f" after the next script {seen['afterNextScript']}, same process {seen['sameProcess']}")
    return {"item": f"host stopped ({form})", "ok": ok, "value": seen}


def phase_midrun(args):
    rows, failed = [], 0
    rename_rhino, rename_runtime = PROVIDERS[0][2], PROVIDERS[0][3]
    for form in [] if args.classes else forms_of(args):
        source = (f"(function () {{ var a = {rename_rhino}; mark(8802, 1); sleep(3000); return [a, {rename_rhino}]; }})()" if form == "rhino"
                  else f"(async () => {{ const a = await {rename_runtime}; mark(8802, 1); await pause(3000); return [a, await {rename_runtime}]; }})()")
        answer = run_form(args, form, [["provider", source]], {8802: lambda: stop_packages(args, [BATCH]) or (True, "stopped")}, wait=60)
        result = answer["results"].get(0)
        ok = result is not None and "th" not in result and len(set(result.get("ok") or [])) == 1
        failed += not ok
        rows.append({"item": f"provider stopped ({form})", "ok": ok, "value": short(result)})
        print(f"{'PASS' if ok else 'FAIL'} midrun provider stopped {form}: {short(result)}")
    for form, runtime in [] if args.classes else (("node", NODE), ("bun", BUN)):
        rows.append(script_stops(args, form, runtime))
        failed += not rows[-1]["ok"]
    for form, process, runtime in (("node", f"{NODE}:nodejs_runtime", NODE), ("bun", f"{BUN}:bun_runtime", BUN)):
        rows.append(host_stops(args, form, process, runtime))
        failed += not rows[-1]["ok"]
    save(args, "midrun" if not args.classes else "midrun-host", {"rows": rows})
    return 1 if failed else 0


FGS_JS = """
var service = new org.autojs.autojs.service.ForegroundService(context);
if (AJ6_ON) service.start(); else service.stop();
sleep(1000);
aj6Emit(AJ6_RUN, 0, {running: service.isRunning()});
aj6Emit(AJ6_RUN, -1, {done: 1});
"""


def foreground_service(args, on):
    run = uuid.uuid4().hex[:8]
    text = f"var AJ6_RUN = '{run}';{NL}var AJ6_ON = {'true' if on else 'false'};{NL}" + base.EMIT_JS + FGS_JS
    answer = base.run_script(args.serial, f"aj6_p62_fgs_{run}.js", text, run, 30).get(0)
    print(f"host foreground service {'on' if on else 'off'}: {answer}, {service_state(args, APP_SERVICE)[1]}")


def phase_background(args):
    """Host calls of a running script with the host in the background: the time between ticks shows whether it was frozen."""
    fgs, ticks, rows, failed = args.variant == "fgs-on", args.seconds // 3, [], 0
    foreground_service(args, fgs)
    if args.a11y == "off":
        set_state(args, "bindings", False)
    try:
        for form in forms_of(args):
            sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
            time.sleep(20 if args.a11y == "off" else 1)
            source = TICK_RHINO if form == "rhino" else TICK_RUNTIME

            def first_tick():
                if args.screen == "off":
                    sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_SLEEP", check=False)
                return True, args.screen
            answer = run_form(args, form, [["ticks", source]], {8600: first_tick}, wait=args.seconds + 30, ticks=ticks, timeout=(args.seconds + 20) * 1000)
            sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
            marks = sorted((index, value) for index, value in answer["results"].items() if 8600 <= index < 8600 + ticks)
            times = [value[0] for _, value in marks]
            gaps = [round((b - a) / 1000, 1) for a, b in zip(times, times[1:])]
            slowest = max((value[1] for _, value in marks), default=None)
            frozen = bool(gaps) and max(gaps) > 9
            ok = len(marks) == ticks and not frozen
            failed += not ok
            rows.append({"variant": args.variant, "screen": args.screen, "a11y": args.a11y, "form": form, "ticks": len(marks), "of": ticks, "maxGap": max(gaps) if gaps else None,
                         "slowestCallMs": slowest, "ok": ok, "log": freeze_lines(args)})
            print(f"{'PASS' if ok else 'FAIL'} background {args.variant} screen {args.screen} a11y {args.a11y} {form}: ticks {len(marks)} / {ticks}, longest gap {max(gaps) if gaps else None} s,"
                  f" slowest call {slowest} ms", flush=True)
    finally:
        foreground_service(args, False)
        if args.a11y == "off":
            set_state(args, "bindings", True)
    save(args, f"background-{args.variant}-{args.screen}-a11y-{args.a11y}", {"rows": rows})
    return 1 if failed else 0


def process_state(args, process):
    """STAT and WCHAN of [process] as ps reports them, which the shell may read for every app."""
    pid = pid_of(args, process).split()
    if not pid:
        return "absent"
    lines = sample.adb(args.serial, "shell", f"ps -o STAT,WCHAN -p {pid[0]}", check=False).strip().splitlines()
    return " ".join(lines[-1].split()) if len(lines) > 1 else "unknown"


def freeze_lines(args):
    """Counts by log tag of what the system logged about freezing since the logcat was cleared."""
    out = sample.adb(args.serial, "logcat", "-d", "-v", "tag", check=False, timeout=60) or ""
    tags, uid = {}, uid_of(args.serial, HOST)
    for line in out.splitlines():
        if re.search(r"hans|freez|frozen", line, re.I) and (HOST in line or uid in line):
            tag = line.split(":", 1)[0].split("/")[-1].strip()
            tags.setdefault(tag, []).append(line[:200])
    return {tag: {"lines": len(lines), "first": lines[0], "last": lines[-1]} for tag, lines in tags.items()}


def phase_freeze(args):
    """The state of the stopped host, and whether its script goes on when the host comes back to the front."""
    rows, failed, ticks = [], 0, args.seconds // 3
    runtimes = {"node": f"{NODE}:nodejs_runtime", "bun": f"{BUN}:bun_runtime"}
    set_state(args, "bindings", False)
    try:
        for form in forms_of(args):
            sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
            time.sleep(20)
            seen = {}

            def later():
                time.sleep(40)
                seen.update(host=process_state(args, HOST), runtime=process_state(args, runtimes[form.split("-")[0]]) if form != "rhino" else None, log=freeze_lines(args))
                sample.adb(args.serial, "shell", "monkey", "-p", HOST, "-c", "android.intent.category.LAUNCHER", "1", check=False)
            thread = threading.Thread(target=later)
            thread.start()
            source = TICK_RHINO if form == "rhino" else TICK_RUNTIME
            answer = run_form(args, form, [["ticks", source]], wait=args.seconds + 50, ticks=ticks, timeout=(args.seconds + 40) * 1000)
            thread.join()
            times = [value[0] for index, value in sorted(answer["results"].items()) if 8600 <= index < 8600 + ticks]
            gaps = [round((b - a) / 1000, 1) for a, b in zip(times, times[1:])]
            ok = len(times) == ticks
            failed += not ok
            rows.append({"form": form, "ticks": len(times), "of": ticks, "longestGap": max(gaps) if gaps else None, "hostAt40s": seen["host"], "runtimeAt40s": seen["runtime"], "log": seen["log"]})
            print(f"{'PASS' if ok else 'FAIL'} freeze {form}: ticks {len(times)} / {ticks} after the host came back, longest gap {rows[-1]['longestGap']} s, host at 40 s {seen['host']},"
                  f" runtime {seen['runtime']}, log lines {sum(item['lines'] for item in seen['log'].values())}", flush=True)
    finally:
        sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
        set_state(args, "bindings", True)
    save(args, "freeze", {"rows": rows})
    return 1 if failed else 0


def phase_capture(args):
    rows, failed = [], 0
    for form in forms_of(args):
        print(f"{form}: answer the MediaProjection dialog on the phone", flush=True)
        foreground = {}

        def service():
            foreground["state"] = service_state(args, CAPTURE_SERVICE)
            return foreground["state"]
        answer = run_form(args, form, [["capture", CAPTURE_RHINO if form == "rhino" else CAPTURE_RUNTIME]], {8701: service}, wait=args.wait, foreground=True)
        result = answer["results"].get(0)
        ok = result is not None and result.get("ok") == [True] and foreground.get("state", (False,))[0]
        failed += not ok
        rows.append({"form": form, "ok": bool(ok), "value": short(result), "service": foreground.get("state", (False, "absent"))[1]})
        print(f"{'PASS' if ok else 'FAIL'} capture {form}: {short(result)}, service {foreground.get('state', (False, 'absent'))[1]}")
    save(args, "capture", {"rows": rows})
    return 1 if failed else 0


def device_time(args):
    return sample.adb(args.serial, "shell", "date '+%m-%d %H:%M:%S.000'").strip()


def prompt_starts(args, mark):
    """Starts of the storage prompt in logcat since [mark]; the system logs each start once."""
    out = sample.adb(args.serial, "logcat", "-d", "-T", mark, "-s", "ActivityTaskManager:I", check=False, timeout=30) or ""
    return sum(PROMPT in line and "START" in line for line in out.splitlines())


def resumed_activity(args):
    out = sample.adb(args.serial, "shell", "dumpsys activity activities", check=False, timeout=30) or ""
    lines = [line for line in out.splitlines() if "topResumedActivity" in line or "mResumedActivity" in line or "ResumedActivity:" in line]
    return " ".join(sorted(set(re.findall(r"(\S+/\S+)", " ".join(lines)))))


def close_prompt(args):
    """The task of the prompt goes away as a whole: nothing on it is pressed."""
    tasks = sample.adb(args.serial, "shell", "am stack list", check=False, timeout=30) or ""
    for task in re.findall(r"taskId=(\d+): \S*" + PROMPT, tasks):
        sample.adb(args.serial, "shell", f"am stack remove {task}", check=False)


def storage_write(args, label, form, source, expect, shown, wait=45):
    """One script with [source]: the answer against [expect], the prompt starts against [shown] (None only records them)."""
    sample.adb(args.serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
    time.sleep(1)
    mark = device_time(args)
    result = run_form(args, form, [["write", source]], wait=wait)["results"].get(0) or {}
    value = result.get("ok")
    deadline, count = time.monotonic() + 8, 0
    while time.monotonic() < deadline and not count:
        time.sleep(0.5)
        count = prompt_starts(args, mark)
    time.sleep(1.5)
    count, top = prompt_starts(args, mark), PROMPT in resumed_activity(args)
    ok = (value in expect) and (shown is None or (count == 1 and top) == shown and (shown or count == 0))
    print(f"{'PASS' if ok else 'FAIL'} prompt {label}: answer {value if result else 'missing'}, prompt starts {count}, on top {top}", flush=True)
    return {"item": label, "form": form, "ok": ok, "answer": value, "starts": count, "onTop": top}


def phase_prompt(args):
    """The Q228 (b) prompt: --step auto runs without the maintainer, --step grant waits for the grant on the phone."""
    rows, serial = [], args.serial
    if args.step == "grant":
        print("on the phone: press the authorize button of the prompt, then turn on the all files access of the Bun plugin", flush=True)
        rows.append(storage_write(args, "bun refused, for the grant", "bun", WRITE_JS, REFUSED, True))
        pages, deadline = [], time.monotonic() + 600
        while time.monotonic() < deadline and "allow" not in (appop(serial, BUN, "MANAGE_EXTERNAL_STORAGE", True), appop(serial, BUN, "MANAGE_EXTERNAL_STORAGE")):
            page = resumed_activity(args)
            if page and page not in pages:
                pages.append(page)
            time.sleep(1)
        print(f"pages on the way: {pages}", flush=True)
        granted = "allow" in (appop(serial, BUN, "MANAGE_EXTERNAL_STORAGE", True), appop(serial, BUN, "MANAGE_EXTERNAL_STORAGE"))
        rows.append({"item": "pages on the way", "form": "bun", "ok": granted and any(PROMPT not in page for page in pages), "answer": pages, "starts": None, "onTop": None})
        if granted:
            rows.append(storage_write(args, "bun after the grant", "bun", WRITE_JS, ["p62"], False))
        else:
            print("FAIL prompt: no grant within 600 s", flush=True)
        sample.sh(serial, f"appops set --uid {BUN} MANAGE_EXTERNAL_STORAGE default")
        sample.sh(serial, f"appops set {BUN} MANAGE_EXTERNAL_STORAGE default")
    else:
        set_state(args, "storage", False)
        try:
            rows.append(storage_write(args, "node refused", "node", WRITE_JS, REFUSED, True))
            rows.append(storage_write(args, "bun refused, replacing the node prompt", "bun", WRITE_JS, REFUSED, True))
            close_prompt(args)
        finally:
            set_state(args, "storage", True)
        # The grant check reads the appop of another package, which a system may refuse: then this line would bring a prompt.
        rows.append(storage_write(args, "node granted, printed refusal", "node", PRINTED_JS, ["printed"], False))
        set_state(args, "storage", False)
        set_state(args, "overlay", False)
        set_state(args, "bindings", False)
        try:
            time.sleep(20)
            for form in ("node", "bun"):
                # The host may be frozen by then, and its answer and prompt come only when something wakes it: recorded, not judged.
                rows.append(storage_write(args, "late, no overlay, no accessibility", form, f"pause({LATE_MS}).then(() => {WRITE_JS})", [*REFUSED, None], None, wait=70))
                close_prompt(args)
        finally:
            set_state(args, "storage", True)
            set_state(args, "overlay", True)
            set_state(args, "bindings", True)
    close_prompt(args)
    sample.adb(serial, "shell", "input", "keyevent", "KEYCODE_HOME", check=False)
    save(args, f"prompt-{args.step}", {"rows": rows})
    return 0 if all(row["ok"] for row in rows) else 1


def phase_concurrent(args):
    """Two host calls at once as the first calls of a fresh script: how many of them time out, Bun against Node."""
    rows, failed = [], 0
    for form in ("node", "bun"):
        for name, calls in CONCURRENT:
            source = (f"(async () => {{ const t = Date.now(); const r = await Promise.allSettled({calls}); return [Date.now() - t,"
                      " r.filter(x => x.status !== 'fulfilled').map(x => String(x.reason && (x.reason.code || x.reason.name)))]; })()")
            times, errors, missing = [], [], 0
            for _ in range(args.rounds):
                value = (run_form(args, form, [["calls", source]], wait=30)["results"].get(0) or {}).get("ok")
                if value is None:
                    missing += 1
                    continue
                times.append(value[0])
                errors += value[1]
            ok = not errors and not missing
            failed += not ok
            rows.append({"form": form, "calls": name, "rounds": args.rounds, "errors": len(errors), "codes": sorted(set(errors)), "missing": missing,
                         "medianMs": sorted(times)[len(times) // 2] if times else None, "slowestMs": max(times, default=None)})
            print(f"{'PASS' if ok else 'FAIL'} concurrent {form} {name}: {len(errors)} errors {sorted(set(errors))} in {args.rounds} scripts, {missing} without an answer,"
                  f" median {rows[-1]['medianMs']} ms, slowest {rows[-1]['slowestMs']} ms", flush=True)
    save(args, "concurrent", {"rows": rows})
    return 1 if failed else 0


def phase_instrument(args):
    sample.adb(args.serial, "logcat", "-c")
    out = sample.adb(args.serial, "shell", f"am instrument -w -r -e class {WAKE_TEST} {sample.RUNNER}", timeout=600, check=False)
    ok = re.search(r"OK \((\d+) tests?\)", out)
    failures = re.findall(r"INSTRUMENTATION_STATUS: test=(\S+)[\s\S]{0,300}?INSTRUMENTATION_STATUS_CODE: -2", out)
    log = wake_log(args)
    print(f"{'PASS' if ok else 'FAIL'} instrument {WAKE_TEST.rsplit('.', 1)[-1]}: {ok.group(0) if ok else 'failed ' + ','.join(failures)}, wakes {len(log['woke'])}")
    save(args, "instrument", {"ok": bool(ok), "summary": ok.group(0) if ok else None, "failures": failures, "wakes": len(log["woke"])})
    return 0 if ok else 1


def auth_path(args):
    return args.state / "auth-record.json"


def load_auth(args):
    return json.loads(auth_path(args).read_text(encoding="utf-8"))


def appop(serial, package, name, uid=False):
    out = sample.sh(serial, f"cmd appops get {'--uid ' if uid else ''}{package} {name}")
    match = re.search(rf"{name}: (\w+)", out)
    return match.group(1) if match else "default"


def phase_state(args):
    serial = args.serial
    print(f"ColorOS {sample.sh(serial, 'getprop ro.build.version.oplusrom')}, {sample.sh(serial, 'getprop ro.build.display.id')}, API {sample.sh(serial, 'getprop ro.build.version.sdk')}")
    print(f"overlay host {appop(serial, HOST, 'SYSTEM_ALERT_WINDOW')}, all files host {appop(serial, HOST, 'MANAGE_EXTERNAL_STORAGE', True)}, node {appop(serial, NODE, 'MANAGE_EXTERNAL_STORAGE', True)}")
    print(f"accessibility {sample.sh(serial, 'settings get secure accessibility_enabled')}, host listed {A11Y in sample.sh(serial, 'settings get secure enabled_accessibility_services')},"
          f" listener listed {LISTENER in sample.sh(serial, 'settings get secure enabled_notification_listeners')}")
    print(f"battery whitelist host {HOST in sample.sh(serial, 'dumpsys deviceidle whitelist')}, shizuku server {shizuku_running(args)}, app service {service_state(args, APP_SERVICE)[1]}")
    for package in [HOST, NODE, BUN, MAPLE, BATCH, MACAW, HANZI, *OCR_PACKAGES, *TOOL_PACKAGES, SHIZUKU, PLUGIN + "mcp.server"]:
        info = base.package_state(serial, package)
        print(f"{package.rsplit('.', 2)[-1] if package.startswith(PLUGIN) else package}: " + (f"{info['versionName']} ({info['versionCode']})" if info else "not installed"))
    return 0


def phase_prepare(args):
    serial = args.serial
    args.state.mkdir(parents=True, exist_ok=True)
    if not auth_path(args).exists():
        record = {f"{ns}/{key}": sample.sh(serial, f"settings get {ns} {key}") for ns, key in SETTINGS}
        record.update(overlay=appop(serial, HOST, "SYSTEM_ALERT_WINDOW"), nodeStorage=appop(serial, NODE, "MANAGE_EXTERNAL_STORAGE", True),
                      appService=service_state(args, APP_SERVICE)[1])
        services = [item for item in record["secure/enabled_accessibility_services"].split(":") if item and item != "null"]
        record["prepared/enabled_accessibility_services"] = ":".join(services + [A11Y] if A11Y not in services else services)
        auth_path(args).write_text(json.dumps(record, indent=1), encoding="utf-8")
    record = load_auth(args)
    rebind_accessibility(args)
    sample.sh(serial, f"cmd notification allow_listener {LISTENER}")
    sample.sh(serial, "settings put global stay_on_while_plugged_in 3")
    sample.sh(serial, "input keyevent KEYCODE_WAKEUP")
    if args.bun_grants:
        args.only, args.set = GRANTS, "on"
        base.grant(args)
    print("prepared: accessibility on, listener allowed, stay awake on" + (", Bun grants on" if args.bun_grants else ""))
    return 0


def phase_unprepare(args):
    record = load_auth(args)
    if args.bun_grants:
        args.only, args.set = GRANTS, "restore"
        base.grant(args)
    for ns, key in SETTINGS:
        value = record[f"{ns}/{key}"]
        sample.sh(args.serial, f"settings delete {ns} {key}" if value == "null" else f"settings put {ns} {key} {value}")
    # The setting alone leaves the listener approved in the notification service, which writes it back.
    if LISTENER not in record["secure/enabled_notification_listeners"]:
        sample.sh(args.serial, f"cmd notification disallow_listener {LISTENER}")
    sample.sh(args.serial, f"appops set {HOST} SYSTEM_ALERT_WINDOW {record['overlay']}")
    sample.sh(args.serial, f"appops set --uid {NODE} MANAGE_EXTERNAL_STORAGE {record['nodeStorage']}")
    print("written back: " + ", ".join(f"{ns}/{key}" for ns, key in SETTINGS) + ", overlay, node all files access" + (", Bun grants" if args.bun_grants else ""))
    return 0


def phase_summary(args):
    for path in sorted(args.state.glob("*.json")):
        if path.stem in ("auth-record", "state", "p62-grants"):
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        print(f"### {path.stem}" + NL)
        rows = data.get("rows", [data])
        if path.stem.startswith(("auth", "denied")):
            forms = list(rows[0]["verdicts"]) if rows else []
            print("| 类 | 入口 | " + " | ".join(forms) + " | 关键数值 |")
            print("|---|---|" + "---|" * (len(forms) + 1))
            for row in rows:
                print(f"| {row['class']} | `{row['id']}` | " + " | ".join("通过" if row["verdicts"][form] == "pass" else "失败" for form in forms)
                      + f" | `{row['values'].get('node', row['values'].get(forms[0], '')).replace('|', '/')}` |")
        else:
            keys = list(rows[0]) if rows else []
            print("| " + " | ".join(keys) + " |")
            print("|" + "---|" * len(keys))
            for row in rows:
                print("| " + " | ".join(str(row[key]).replace("|", "/") for key in keys) + " |")
        print()
    return 0


def phase_list(args):
    for cls, why in CLASSES.items():
        print(f"{cls}: {why}")
        for item in AUTH_CASES:
            if item["class"] == cls:
                print(f"    {item['id']}" + (" (needs the Shizuku server)" if item["needs"] else ""))
    print("denied: " + ", ".join(DENIED))
    print("rebind providers: " + ", ".join(row[0] for row in PROVIDERS))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("phase", choices=["list", "record", "install", "restore", "verify", "state", "prepare", "unprepare", "auth", "denied", "rebind", "midrun",
                                          "background", "capture", "instrument", "a11y", "freeze", "prompt", "concurrent", "summary"])
    parser.add_argument("--serial", default="63751f63")
    parser.add_argument("--state", type=Path, help="private state directory (APK copies, recorded authorizations, results)")
    parser.add_argument("--apk", action="append", default=[], help="install: package=path")
    parser.add_argument("--forms", default=",".join(FORMS))
    parser.add_argument("--classes", nargs="*", default=[], help="auth / denied: only these classes; midrun: anything here leaves only the host stops")
    parser.add_argument("--providers", nargs="*", default=[], help="rebind: only these providers")
    parser.add_argument("--variant", default=None, help="rebind: background, foreground, no-overlay, background-late or no-overlay-late; background: fgs-off or fgs-on")
    parser.add_argument("--screen", choices=["on", "off"], default="on", help="background: the screen after the first tick")
    parser.add_argument("--seconds", type=int, default=60, help="background: the length of a run")
    parser.add_argument("--a11y", choices=["on", "off"], default="on", help="background: the host's accessibility service and notification listener during the runs")
    parser.add_argument("--step", choices=["auto", "grant"], default="auto", help="prompt: without the maintainer, or waiting for the grant on the phone")
    parser.add_argument("--rounds", type=int, default=10, help="concurrent: fresh scripts per form and pair of calls")
    parser.add_argument("--wait", type=int, default=240)
    parser.add_argument("--foreground", action="store_true", help="auth / denied: bring the host's launcher activity to the front before each script")
    parser.add_argument("--bun-grants", action=argparse.BooleanOptionalAction, default=True, help="prepare / unprepare: also the Bun grants (off without a Bun plugin)")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="backslashreplace")
    if args.phase == "list":
        return phase_list(args)
    if not args.state:
        parser.error("--state is required")
    if args.phase in ("record", "install", "restore", "verify"):
        return getattr(base, args.phase)(args)
    if args.phase == "rebind":
        args.variant = args.variant or "background"
    if args.phase == "background":
        args.variant = args.variant or "fgs-off"
    return {"state": phase_state, "prepare": phase_prepare, "unprepare": phase_unprepare, "auth": phase_auth, "denied": phase_denied, "rebind": phase_rebind,
            "midrun": phase_midrun, "background": phase_background, "capture": phase_capture, "instrument": phase_instrument, "a11y": phase_a11y, "freeze": phase_freeze,
            "prompt": phase_prompt, "concurrent": phase_concurrent, "summary": phase_summary}[args.phase](args)


if __name__ == "__main__":
    sys.exit(main())
