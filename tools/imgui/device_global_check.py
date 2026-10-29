"""Checks the host module `imgui` (the ImGui plugin as a global object) on a device.

Usage: py -3.12 -I tools/imgui/device_global_check.py -s SERIAL [--expect-missing] [--no-window] [--timeout 90] [--keep]

Pushes one script and runs it through RunIntentActivity (as tools/augment/device_bench.py does), then prints one
line per check: PASS / FAIL with the key value. Without the plugin (not installed, disabled or untrusted) run it
with --expect-missing: the only check is that a member of imgui fails with the Plugin Center message. With the
plugin the checks are the module object (459 members before anything is loaded), __pluginId, version() (the first
member use loads the plugin), constant groups, PascalCase aliases, references, listExamples, getBackendCapabilities,
plugins.load(package) returning the same object, and (unless --no-window) a floating window rendered for 30 frames
(needs the overlay permission) whose first frame checks that every public member of the API object (`ui`) is
reachable on imgui. The pushed script is removed afterwards unless --keep is given.

zh-CN: 在设备上检查宿主模块 imgui (ImGui 插件作为全局对象). 经 RunIntentActivity 运行一个脚本, 每项检查打印一行 PASS / FAIL 与关键值.
没有插件 (未安装, 未启用或未授信) 时加 --expect-missing, 只检查成员调用以插件中心的提示失败; 有插件时检查模块对象 (加载前 459 个成员),
__pluginId, version() (首次调用成员加载插件), 常量组, PascalCase 别名, 引用, listExamples, getBackendCapabilities,
plugins.load(包名) 返回同一对象, 以及 (除非 --no-window) 渲染 30 帧的悬浮窗 (需要悬浮窗权限), 首帧检查 API 对象 (ui) 的每个公开成员都能经 imgui 访问.
"""
import argparse
import pathlib
import subprocess
import sys
import time

DEVICE_DIR = "/sdcard/Download/aj6-imgui-check"
RUN_ACTIVITY = "org.autojs.autojs.external.open.RunIntentActivity"
PACKAGE = "org.autojs.autojs6"
MARK = "AJ6IMGUI"
LOG_TAIL = 50000

SCRIPT = r"""
var MARK = 'AJ6IMGUI';
var PKG = 'io.github.supermonster003.autojs6.plugin.imgui';
var failed = 0;

function report(name, ok, detail) {
    console.log(MARK + ' ' + name + ' ' + (ok ? 'PASS' : 'FAIL') + ' ' + detail);
    if (!ok) failed++;
}

function assert(cond, msg) {
    if (!cond) throw new Error(msg);
}

function check(name, fn) {
    try {
        report(name, true, String(fn()));
    } catch (e) {
        report(name, false, String(e).slice(0, 200));
    }
}

function finish() {
    console.log(MARK + ' done failed=' + failed);
    exit();
}

check('type', function () {
    assert(typeof imgui === 'object', 'typeof imgui = ' + typeof imgui);
    return 'keys=' + Object.keys(imgui).length;
});
check('pluginId', function () {
    assert(imgui.__pluginId === PKG, 'got ' + imgui.__pluginId);
    return 'ok';
});

if (EXPECT_MISSING) {
    check('missing', function () {
        try {
            imgui.version();
        } catch (e) {
            return 'throws ' + String(e).slice(0, 200);
        }
        throw new Error('version() did not throw');
    });
    finish();
} else {
    check('version', function () {
        var v = imgui.version();
        assert(typeof v === 'string' && v.length > 0, 'got ' + v);
        return v;
    });
    check('constants', function () {
        assert(typeof imgui.Col === 'object' && typeof imgui.Col.Text === 'number', 'Col.Text = ' + (imgui.Col && imgui.Col.Text));
        assert(typeof imgui.WindowFlags.NoMove === 'number', 'WindowFlags.NoMove');
        assert(typeof imgui.Key === 'object' && typeof imgui.Limits === 'object', 'Key / Limits');
        return 'Col.Text=' + imgui.Col.Text + ' WindowFlags.NoMove=' + imgui.WindowFlags.NoMove;
    });
    check('aliases', function () {
        assert(typeof imgui.Begin === 'function' && typeof imgui.PushID === 'function', 'Begin / PushID');
        return 'Begin, PushID';
    });
    check('refs', function () {
        var r = imgui.ref(3);
        var b = imgui.bool(true);
        assert(r.value === 3, 'ref.value = ' + r.value);
        assert(b !== undefined && b !== null, 'bool');
        return 'ref.value=3';
    });
    check('examples', function () {
        var list = imgui.listExamples();
        assert(list && list.length === 6, 'length = ' + (list && list.length));
        return 'length=6';
    });
    check('backend', function () {
        var caps = imgui.getBackendCapabilities();
        assert(caps && typeof caps === 'object', 'caps = ' + caps);
        return Object.keys(caps).length + ' keys';
    });
    check('pluginsLoad', function () {
        var loaded = plugins.load(PKG);
        assert(loaded === imgui, 'plugins.load returned another object');
        return 'same object';
    });
    check('keysStable', function () {
        return 'keys=' + Object.keys(imgui).length;
    });
    if (!WINDOW) {
        finish();
    } else {
        var frames = 0;
        var session;
        try {
            session = imgui.createWindow(function (ui, s) {
                frames++;
                if (frames === 1) {
                    // Every public member of the plugin's API object is reachable on imgui, listed in ImguiNames or forwarded after the load.
                    var keys = Object.keys(ui).filter(function (k) { return k.charAt(0) !== '_'; });
                    var missing = keys.filter(function (k) { return typeof imgui[k] !== typeof ui[k]; });
                    report('forwarded', missing.length === 0, 'public=' + keys.length + ' missing=' + missing.length + (missing.length ? ' ' + missing.slice(0, 5).join(',') : ''));
                }
                ui.withWindow('AJ6 check', null, 0, function () {
                    ui.text('frame ' + frames);
                });
                if (frames === 30) s.close();
            }, { width: 240, height: 120 });
        } catch (e) {
            report('window', false, String(e).slice(0, 200));
            finish();
        }
        if (session) {
            var waited = 0;
            var timer = setInterval(function () {
                waited += 200;
                if (session.closed || waited >= 20000) {
                    clearInterval(timer);
                    report('window', session.closed && frames >= 30, 'frames=' + frames + ' closed=' + session.closed);
                    finish();
                }
            }, 200);
        }
    }
}
"""


def adb(serial, *args, check=False):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", check=check)


def console_lines(serial):
    """The GlobalConsole lines of logcat that carry the marker, from the marker on: the console log format of the
    device may put a time and level prefix ("18:06:43.674/D: ") before the message.

    zh-CN: logcat 中带标记的 GlobalConsole 行, 从标记起截取: 设备的控制台日志格式可能在消息前加时间与级别前缀 ("18:06:43.674/D: ").
    """
    out = adb(serial, "logcat", "-d", "-t", str(LOG_TAIL)).stdout
    lines = []
    for line in out.splitlines():
        if "GlobalConsole: " not in line:
            continue
        text = line.split("GlobalConsole: ", 1)[1].rstrip()
        at = text.find(MARK + " ")
        if at >= 0:
            lines.append(text[at:])
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--expect-missing", action="store_true")
    parser.add_argument("--no-window", action="store_true")
    parser.add_argument("--timeout", type=float, default=90)
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()

    flags = "var EXPECT_MISSING = %s; var WINDOW = %s;" % (
        "true" if args.expect_missing else "false", "false" if args.no_window else "true")
    local = pathlib.Path(__file__).resolve().parent / "device_global_check.generated.js"
    local.write_text(flags + chr(10) + SCRIPT, encoding="utf-8")
    remote = DEVICE_DIR + "/check.js"
    try:
        adb(args.serial, "shell", "mkdir", "-p", DEVICE_DIR, check=True)
        adb(args.serial, "push", str(local), remote, check=True)
        adb(args.serial, "logcat", "-c")
        adb(args.serial, "shell", "am", "start", "-n", PACKAGE + "/" + RUN_ACTIVITY, "-e", "path", remote, check=True)
        seen = []
        deadline = time.time() + args.timeout
        done = False
        while time.time() < deadline and not done:
            time.sleep(0.5)
            for line in console_lines(args.serial):
                if not line.startswith(MARK + " ") or line in seen:
                    continue
                seen.append(line)
                print(line[len(MARK) + 1:])
                if line.startswith(MARK + " done"):
                    done = True
        if not done:
            print("TIMEOUT after %.0f s (%d lines seen)" % (args.timeout, len(seen)))
        failed = [line for line in seen if " FAIL " in line]
        return 0 if done and not failed else 1
    finally:
        if not args.keep:
            adb(args.serial, "shell", "rm", "-r", DEVICE_DIR)
            local.unlink(missing_ok=True)


if __name__ == "__main__":
    sys.exit(main())
