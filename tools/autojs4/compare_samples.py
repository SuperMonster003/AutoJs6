# -*- coding: utf-8 -*-
"""Principle-A comparison driver (autojs4-runtime-plugin-roadmap.md P4.3).

Runs the selected sample scripts twice on a connected device, with the AutoJs6 engine (the explorer's run
button) and through the explorer menu "使用 AutoJs4 运行" (AutoJs4 Runtime plugin), captures the host console
lines from logcat and writes a diff report. The device is driven through `uiautomator dump` and `input tap`,
so the AutoJs6 file manager must be reachable and the device language must be Simplified Chinese (the menu
and tab titles are matched by text).

Usage (from the repository root):
  py tools/autojs4/compare_samples.py push [serial]        # copy the samples to /sdcard/Scripts/autojs4-compare/<group>/
  py tools/autojs4/compare_samples.py run [serial] [group] # run every (or one) group on both engines
  py tools/autojs4/compare_samples.py report               # rebuild tools/autojs4/compare/report.md

zh-CN: 原则 A 对照驱动 (roadmap P4.3). 在已连接设备上把选定示例分别用 AutoJs6 引擎 (文件管理器运行按钮) 与 "使用 AutoJs4 运行"
各跑一次, 从 logcat 采集宿主控制台行并生成差异报告. 通过 uiautomator dump 与 input tap 驾驭界面, 设备语言需为简体中文.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

SERIAL = os.environ.get("ANDROID_SERIAL", "")
HOST_PKG = "org.autojs.autojs6"
SAMPLE_ROOT = Path(__file__).resolve().parents[2] / "app/src/main/assets-app/sample"
DEVICE_ROOT = "/sdcard/Scripts/autojs4-compare"
OUT = Path(__file__).resolve().parent / "compare"
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")

# (group, source relative to SAMPLE_ROOT, seconds to wait for completion)
SAMPLES = [
    ("a", "JavaScript/HelloWorld.js", 4),
    ("a", "JavaScript/数字.js", 4),
    ("a", "JavaScript/E4X.js", 6),
    ("a", "Java API/liveConnect.js", 4),
    ("a", "控制台/产生随机数.js", 12),
    ("a", "控制台/打印不同级别.js", 4),
    ("b", "控制台/格式化输出.js", 4),
    ("b", "控制台/调整控制台大小位置.js", 8),
    ("b", "文件读写/写入文本文件.js", 4),
    ("b", "文件读写/读取文本文件.js", 4),
    ("b", "文件读写/读写文本文件.js", 4),
    ("b", "文件读写/文件编码转换.js", 4),
    ("c", "本地存储/保存数组和复杂对象.js", 4),
    ("c", "本地存储/保存整数等简单数据.js", 4),
    ("c", "多线程/原子变量.js", 4),
    ("c", "多线程/线程启动与关闭.js", 10),
    ("c", "多线程/多线程简单示例.js", 36),
    ("c", "HTTP/获取网页.js", 15),
    ("d", "定时器/循环执行.js", 26),
    ("d", "控制台/保存日志.js", 5),
]


def adb(*args, text=True, check=False):
    kwargs = {"encoding": "utf-8", "errors": "replace"} if text else {}
    target = ["-s", SERIAL] if SERIAL else []
    return subprocess.run(["adb", *target, *args], capture_output=True, env=ENV, check=check, **kwargs)


def shell(cmd):
    return adb("shell", cmd).stdout


def device_name(index, source):
    return f"{index:02d}-{Path(source).name}"


def push():
    adb("shell", f"rm -rf {DEVICE_ROOT}")
    for index, (group, source, _) in enumerate(SAMPLES, 1):
        adb("shell", f"mkdir -p {DEVICE_ROOT}/{group}")
        local = SAMPLE_ROOT / source
        assert local.is_file(), local
        r = adb("push", str(local), f"{DEVICE_ROOT}/{group}/{device_name(index, source)}", text=False)
        assert r.returncode == 0, r.stderr
    print(shell(f"find {DEVICE_ROOT} -type f | sort"))


# ---------------------------------------------------------------- UI helpers

def dump():
    adb("shell", "rm -f /sdcard/ui-dump.xml")
    adb("shell", "uiautomator dump /sdcard/ui-dump.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui-dump.xml", text=False).stdout
    try:
        root = ET.fromstring(raw.decode("utf-8", "replace"))
    except ET.ParseError:
        return []
    nodes = []
    for node in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not m:
            continue
        l, t, r, b = map(int, m.groups())
        nodes.append({
            "text": node.get("text", ""), "id": node.get("resource-id", "").split("/")[-1],
            "desc": node.get("content-desc", ""), "cls": node.get("class", ""), "center": ((l + r) // 2, (t + b) // 2),
        })
    return nodes


def tap(x, y):
    adb("shell", f"input tap {x} {y}")


def find(nodes, **kw):
    return [n for n in nodes if all(n.get(k) == v for k, v in kw.items())]


def tap_first(retries=6, **kw):
    for _ in range(retries):
        hits = find(dump(), **kw)
        if hits:
            tap(*hits[0]["center"])
            return hits[0]
        time.sleep(1)
    raise RuntimeError(f"not found: {kw}")


def tap_menu_item(text):
    """Tap an item of the explorer's more-menu popup, scrolling the popup when the item is clipped off screen."""
    for _ in range(4):
        nodes = dump()
        hits = find(nodes, text=text, id="title")
        if hits:
            tap(*hits[0]["center"])
            return
        titles = [n for n in nodes if n["id"] == "title" and n["text"]]
        if not titles:
            time.sleep(1)
            continue
        x = titles[-1]["center"][0]
        bottom = max(n["center"][1] for n in titles)
        top = min(n["center"][1] for n in titles)
        adb("shell", f"input swipe {x} {bottom} {x} {max(top, bottom - 900)} 300")
        time.sleep(1)
    raise RuntimeError(f"menu item not found: {text}")


def go_home_folder():
    """Return to the Scripts root of the file manager."""
    adb("shell", f"am force-stop {HOST_PKG}")
    adb("shell", f"am start -n {HOST_PKG}/org.autojs.autojs.ui.main.MainActivity")
    time.sleep(4)
    tap_first(desc="文件")
    time.sleep(1)


def open_folder(name):
    tap_first(text=name, id="name")
    time.sleep(2)


def row_button(nodes, file_name, button_id):
    name_node = find(nodes, text=file_name, id="name")
    assert name_node, f"row not visible: {file_name}"
    y = name_node[0]["center"][1]
    buttons = [n for n in nodes if n["id"] == button_id and abs(n["center"][1] - y) < 60]
    assert buttons, f"{button_id} not found for {file_name}"
    return buttons[0]


def close_console_windows():
    """The AutoJs6 console.show() leaves a floating console; close it through its own controls."""
    for _ in range(3):
        nodes = dump()
        hits = [n for n in nodes if n["id"] in ("close", "btn_close", "action_close") or n["desc"] in ("关闭", "Close")]
        if not hits:
            return
        tap(*hits[0]["center"])
        time.sleep(1)


def host_pid():
    return shell(f"pidof {HOST_PKG}").strip().split()[0]


def capture(timeout):
    """Collect the host process's console lines until '运行结束' appears or the timeout elapses."""
    pid = host_pid()
    deadline = time.time() + timeout
    lines = []
    while time.time() < deadline:
        time.sleep(1)
        out = adb("logcat", "-d").stdout
        lines = []
        for l in out.splitlines():
            if "GlobalConsole:" not in l:
                continue
            fields = l.split()
            if len(fields) > 2 and fields[2] != pid:
                continue
            lines.append(re.sub(r"^.*GlobalConsole: ", "", l))
        if any("运行结束" in l for l in lines):
            break
    return lines


def normalize(lines, autojs4):
    result = []
    for l in lines:
        l = re.sub(r"^\d{2}:\d{2}:\d{2}\.\d{3}/", "", l)
        if autojs4:
            if l.startswith("V: 开始运行") or l.startswith("W: runtime-init") or l.startswith("W: script-init"):
                continue
            l = l.replace("[AutoJs4] ", "", 1)
            if l.startswith("V: Script Running["):
                continue
        else:
            if l.startswith("W: runtime-init") or l.startswith("W: script-init") or l.startswith("V: 开始运行"):
                continue
        l = re.sub(r"\(用时 [\d.]+ 秒\)", "(用时 N 秒)", l)
        result.append(l.rstrip())
    return result


def run_group(group):
    items = [(i, s, t) for i, (g, s, t) in enumerate(SAMPLES, 1) if g == group]
    OUT.mkdir(exist_ok=True)
    go_home_folder()
    open_folder("autojs4-compare")
    open_folder(group)
    for index, source, wait in items:
        name = device_name(index, source)
        for engine in ("aj6", "aj4"):
            adb("logcat", "-c")
            nodes = dump()
            if engine == "aj6":
                tap(*row_button(nodes, name, "run")["center"])
            else:
                tap(*row_button(nodes, name, "more")["center"])
                time.sleep(1.5)
                tap_menu_item("使用 AutoJs4 运行")
            lines = capture(wait)
            if engine == "aj6":
                close_console_windows()
            (OUT / f"{name}.{engine}.txt").write_text("\n".join(normalize(lines, engine == "aj4")) + "\n", encoding="utf-8")
            print(f"{name} [{engine}] lines={len(lines)}")
            time.sleep(1)
            # make sure the file list is in front again
            if not find(dump(), text=name, id="name"):
                adb("shell", "input keyevent KEYCODE_BACK")
                time.sleep(1.5)
                if not find(dump(), text=name, id="name"):
                    go_home_folder()
                    open_folder("autojs4-compare")
                    open_folder(group)


def report():
    rows = []
    details = []
    for index, (group, source, _) in enumerate(SAMPLES, 1):
        name = device_name(index, source)
        a = OUT / f"{name}.aj6.txt"
        b = OUT / f"{name}.aj4.txt"
        if not (a.is_file() and b.is_file()):
            rows.append((source, "not run", ""))
            continue
        la = a.read_text(encoding="utf-8").splitlines()
        lb = b.read_text(encoding="utf-8").splitlines()
        if la == lb:
            rows.append((source, "identical", f"{len(la)} lines"))
        else:
            import difflib
            diff = list(difflib.unified_diff(la, lb, "AutoJs6", "AutoJs4", lineterm="", n=0))
            rows.append((source, "differs", f"{len(la)} vs {len(lb)} lines"))
            details.append((source, diff))
    md = ["| sample | result | note |", "| --- | --- | --- |"]
    md += [f"| `{s}` | {r} | {n} |" for s, r, n in rows]
    for source, diff in details:
        md.append(f"\n### `{source}`\n\n```diff\n" + "\n".join(diff[:80]) + "\n```")
    (OUT / "report.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    print("\n".join(md[:len(rows) + 2]))


if __name__ == "__main__":
    args = sys.argv[1:]
    cmd = args[0] if args else "report"
    rest = args[1:]
    if rest and re.fullmatch(r"[A-Za-z0-9._:-]{6,}", rest[0]) and rest[0] not in {g for g, _, _ in SAMPLES}:
        SERIAL = rest.pop(0)
    if cmd == "push":
        push()
    elif cmd == "run":
        for g in rest or sorted({g for g, _, _ in SAMPLES}):
            run_group(g)
        report()
    else:
        report()
