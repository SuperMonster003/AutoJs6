"""
Device check of the upgrade from an old build (docs/settings/ROADMAP.md appendix D item 8, P14.3, evidence level E3).

Usage: py -3.12 -I tools/settings/device_upgrade_check.py -s SERIAL --old APK --new APK --out DIR [--settle SECONDS]
       [--wait SECONDS] [--allow-device]

Meant for a read-only emulator instance (emulator -read-only), whose changes are dropped when it shuts down: the check
installs --old over whatever is installed, seeds settings and does not put the previous data back. A serial that is not
an emulator is refused unless --allow-device is given.

1. --old is installed (adb install -r -d) and started once (the launcher activity, then --settle seconds), so that it
   writes its own first-run values; device/upgrade_seed.js writes the values of PLAN with plain SharedPreferences and
   device_store_check.py lists-before records the three list keys of P1.4 (state in DIR).
2. Every file of shared_prefs is read with run-as (snapshot "before", DIR/before.json); --new is installed over it
   (adb install -r) and started once; snapshot "after" (DIR/after.json).
3. Every key of "before" must be in "after" with the same type and value (the two update check times, which every
   start rewrites, with the same type; the attention record of an old format in PLAN, which the new build may rewrite
   in its own format, with the same content, appendix C Q40); keys the new build adds are counted, not compared. Only
   the names of keys that differ are printed, never values (R6); the snapshots stay in DIR.
4. device_store_check.py domains (every domain of core/pref/keys), lists-after and interop run on the new build.

Output is one line per check (PASS / FAIL and counts); the exit status is 1 when a check fails.

zh-CN: 旧版本升级的真机检查 (附录 D 第 8 项). 供只读模拟器实例使用 (关机即丢弃改动), 不还原原有数据; 非模拟器序列号需加
--allow-device. 旧版本安装并启动一次后写入 PLAN 的值并记录 P1.4 的三个列表键, 读取 shared_prefs 全部文件作 "升级前" 快照;
覆盖安装新版本并启动一次后作 "升级后" 快照, "升级前" 的每个键须以相同类型与值保留 (新版本可能以新格式改写的旧格式置顶记录
比较内容, 附录 C Q40; 只打印不一致的键名); 最后在新版本上运行 domains, lists-after 与 interop.
"""

import argparse
import json
import pathlib
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import device_store_check as base  # noqa: E402

KEYS_DIR = HERE.parent.parent / "app" / "src" / "main" / "java" / "org" / "autojs" / "autojs" / "core" / "pref" / "keys"

# Values a user could set on the old build: item 1 of appendix D (theme color, night mode, editor text size, plugin
# center sort), the credential, and one key of each stored type; "res" names a key resource, "name" a literal key.
# zh-CN: 旧版本上用户可能改过的值: 附录 D 第 1 项的四项, 凭据, 以及每种存储类型各一个键.
PLAN = [
    {"store": "default", "res": "key_theme_color_primary", "type": "int", "value": -14575885},
    {"store": "default", "res": "key_theme_color_primary_dark", "type": "int", "value": -15108398},
    {"store": "default", "res": "key_theme_color_accent", "type": "int", "value": -49023},
    {"store": "default", "res": "key_night_mode", "type": "string", "valueRes": "key_night_mode_always_on"},
    {"store": "default", "res": "key_night_mode_enabled", "type": "bool", "value": True},
    {"store": "default", "res": "key_auto_night_mode_enabled", "type": "bool", "value": False},
    {"store": "default", "res": "key_editor_text_size", "type": "int", "value": 21},
    {"store": "plugin_center_sort_state", "name": "key_$_plugin_center_sort_state", "type": "int", "value": 2},
    {"store": "github_auth", "name": "github_token", "type": "string", "value": "aj6-upgrade-check-token"},
    {"store": "default", "res": "key_hidden_files", "type": "string", "valueRes": "key_hidden_files_show"},
    {"store": "default", "res": "key_keep_screen_on_when_in_foreground", "type": "string",
     "valueRes": "key_keep_screen_on_when_in_foreground_all_pages"},
    {"store": "default", "res": "key_documentation_source", "type": "string", "valueRes": "key_documentation_source_online"},
    {"store": "default", "res": "key_history_max_total_bytes", "type": "long", "value": "123456789"},
    {"store": "default", "name": "org.autojs.autojs.ui.floating.CircularMenuWindow.position.x_percent", "type": "float",
     "value": 0.25},
    {"store": "default", "res": "key_script_module_debug_print_disabled_modules", "type": "stringSet", "value": ["console"]},
    # An attention record written by org.json, a format that appendix C Q40 replaced. The file manager history, whose
    # pages Q40 also changed, is not seeded: MainActivity clears the file manager state whenever it is created without
    # a saved state (ExplorerViewStateLifecyclePolicy), the first start after an upgrade included, so an upgrade never
    # reads it; unit tests cover its old text.
    # zh-CN: org.json 写的置顶记录 (附录 C Q40 替换掉的格式). 文件管理器历史 (Q40 同样改了其页面格式) 不写入: MainActivity 在没有
    # 保存状态时创建 (包括升级后的首次启动) 都会清除文件管理器状态 (ExplorerViewStateLifecyclePolicy), 升级不会读到它; 其旧格式
    # 文本由单测覆盖.
    {"store": "plugin_center_attention", "name": "known_first_install_times", "type": "string",
     "value": "{\"org.example.aj6.upgrade\":1700000000000}"},
]

# Times the app writes whenever it starts and checks for updates, the old build as well: only their type is compared.
# zh-CN: 应用每次启动检查更新时都会改写的时间 (旧版本同样如此): 只比较类型.
REWRITTEN_AT_START = {"key_$_last_updates_auto_checked", "key_$_last_updates_checked"}


def same_content(key, old, new):
    """True when the new build rewrote a text of an old format in PLAN with the same content (appendix C Q40)."""
    if new is None or old[0] != "string" or new[0] != "string":
        return False
    try:
        old_value, new_value = json.loads(old[1]), json.loads(new[1])
    except ValueError:
        return False
    if key == "known_first_install_times":
        # The plugin center may add the packages it sees. zh-CN: 插件中心可能补入它看到的包.
        return isinstance(new_value, dict) and all(new_value.get(name) == time for name, time in old_value.items())
    return False


def install(serial, apk, downgrade):
    result = base.adb(serial, "install", "-r", *(["-d"] if downgrade else []), str(apk))
    ok = result.returncode == 0 and "Success" in result.stdout
    print(f"{'PASS' if ok else 'FAIL'} install {apk.name} ({base.host_version(serial) if ok else result.stdout.strip()[-120:]})")
    return ok


def start_once(serial, settle):
    base.adb(serial, "shell", "monkey", "-p", base.PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(settle)


def snapshot(serial):
    """{file: {key: [type, value]}} of every file of shared_prefs; a set is a sorted list."""
    names = base.adb(serial, "shell", "run-as", base.PACKAGE, "ls", "shared_prefs").stdout.split()
    files = {}
    for name in sorted(n for n in names if n.endswith(".xml")):
        text = base.adb(serial, "exec-out", "run-as", base.PACKAGE, "cat", f"shared_prefs/{name}").stdout
        entries = {}
        for node in ElementTree.fromstring(text):
            if node.tag == "string":
                entries[node.get("name")] = ["string", node.text or ""]
            elif node.tag == "set":
                entries[node.get("name")] = ["set", sorted(child.text or "" for child in node)]
            else:
                entries[node.get("name")] = [node.tag, node.get("value")]
        files[name] = entries
    return files


def compare(before, after):
    kept, differ, added, rewritten = 0, [], 0, []
    for name, entries in before.items():
        now = after.get(name, {})
        for key, value in entries.items():
            if now.get(key) == value or (key in REWRITTEN_AT_START and now.get(key, [None])[0] == value[0]):
                kept += 1
            elif same_content(key, value, now.get(key)):
                kept += 1
                rewritten.append(f"{name}:{key}")
            else:
                differ.append(f"{name}:{key} {'removed' if key not in now else 'changed'}")
        added += sum(1 for key in now if key not in entries)
    added += sum(len(entries) for name, entries in after.items() if name not in before)
    print(f"{'PASS' if not differ else 'FAIL'} upgrade keeps the stored settings: {kept} kept in {len(before)} files, "
          f"{len(differ)} differ, {added} added by the new build")
    for line in rewritten:
        print(f"INFO rewritten in the new format with the same content: {line}")
    for line in differ:
        print(f"INFO differs {line}")
    return len(differ)


def store_check(serial, *args):
    """Runs device_store_check.py; prints its FAIL lines and its summary line, returns 1 when it fails."""
    result = subprocess.run([sys.executable, "-I", str(HERE / "device_store_check.py"), "-s", serial, *args],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    lines = result.stdout.splitlines()
    for line in lines:
        if line.startswith("FAIL"):
            print(line[:200])
    summary = next((line for line in reversed(lines) if line.startswith(f"{args[0]}: ")), f"{args[0]}: no summary")
    passed = sum(1 for line in lines if line.startswith("PASS"))
    print(f"{'PASS' if result.returncode == 0 else 'FAIL'} {summary} ({passed} passed)")
    return 1 if result.returncode else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--old", type=pathlib.Path, required=True, help="APK of the old build")
    parser.add_argument("--new", type=pathlib.Path, required=True, help="APK of the new build")
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--settle", type=int, default=15, help="seconds the app runs after each start")
    parser.add_argument("--wait", type=int, default=120, help="seconds to wait for each script on the old build and the first one on the new build")
    parser.add_argument("--allow-device", action="store_true", help="also run on a serial that is not an emulator")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-") and not args.allow_device:
        parser.error("not an emulator; the check does not restore the data (use --allow-device to run anyway)")
    args.out.mkdir(parents=True, exist_ok=True)
    model = base.adb(args.serial, "shell", "getprop", "ro.product.model").stdout.strip()
    sdk = base.adb(args.serial, "shell", "getprop", "ro.build.version.sdk").stdout.strip()
    print(f"device {args.serial}: {model} (API {sdk})")

    failed = 0
    if not install(args.serial, args.old, True):
        return 1
    start_once(args.serial, args.settle)
    script = (HERE / "device" / "upgrade_seed.js").read_text(encoding="utf-8")
    # The first script after installing an old build on an emulator may take a minute to start (dex optimization).
    # zh-CN: 在模拟器上装好旧版本后的第一个脚本可能要一分钟才开始运行 (dex 优化).
    lines = base.run_script(args.serial, f"var AJ6_PLAN = {json.dumps(PLAN)};\n" + script, args.wait)
    for line in lines:
        if line.startswith(("PASS", "FAIL")):
            print(line)
            failed += line.startswith("FAIL")
    if "done" not in lines:
        print(f"FAIL seed: the script did not finish within {args.wait} s (last line: {lines[-1][:80] if lines else 'none'})")
        failed += 1
    lists_state = args.out / "lists-state.json"
    failed += store_check(args.serial, "lists-before", "--state", str(lists_state), "--wait", str(args.wait))
    # No "am force-stop" here: on Android 7 it also disables the accessibility service of the app, which then stores
    # key_$_a11y_service as false at its next start. adb install stops the app anyway; the scripts commit their writes.
    # zh-CN: 此处不用 "am force-stop": 在 Android 7 上它会同时停用应用的无障碍服务, 应用下次启动时把 key_$_a11y_service
    # 存为 false. adb install 本身会停止应用, 脚本的写入都已 commit.
    before = snapshot(args.serial)
    (args.out / "before.json").write_text(json.dumps(before, ensure_ascii=True, indent=1), encoding="utf-8")

    if not install(args.serial, args.new, False):
        return 1
    start_once(args.serial, args.settle)
    after = snapshot(args.serial)
    (args.out / "after.json").write_text(json.dumps(after, ensure_ascii=True, indent=1), encoding="utf-8")
    failed += compare(before, after)

    domains = sorted(p.stem for p in KEYS_DIR.glob("*Keys.kt") if p.stem != "KeyDeclarations")
    # The first script on the new build may wait for dex optimization as well. zh-CN: 新版本的第一个脚本同样可能等待 dex 优化.
    failed += store_check(args.serial, "domains", "--domains", ",".join(domains), "--wait", str(args.wait))
    failed += store_check(args.serial, "lists-after", "--state", str(lists_state))
    failed += store_check(args.serial, "interop")
    print(f"upgrade: {'FAIL' if failed else 'PASS'} ({failed} failed)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
