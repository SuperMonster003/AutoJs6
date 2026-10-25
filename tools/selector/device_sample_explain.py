"""Runs the sample "选择器诊断与节点树导出" of the host (ROADMAP selector P3.5) on a device as it ships and checks its console
output: the tallies of auto.explain, the single result, the compass trail and the result type of the
auto.explain(selector, compass, resultType) form, the dump forms and auto.stats; prints PASS / FAIL per check (no node
text: the sample works on a floaty window of its own with synthetic texts, and only the check names are printed).

Usage: py -3.12 -I tools/selector/device_sample_explain.py -s SERIAL [--show-lines]
Needs the accessibility service of AutoJs6 running and the floaty permission; no taps (the sample goes home first).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

SAMPLE = d.HOST / "app/src/main/assets-app/sample/无障碍服务/选择器诊断与节点树导出 [v6.8.0+].js"

# (name, every one of these substrings in one console line)
CHECKS = [
    ("explain found", ["1. 找到: true 数量: 1"]),
    ("explain no match", ["2. 找到: false ; 失败于第 1 个过滤器"]),
    ("explain root", ["3. 限定在悬浮窗内: 1 个"]),
    ("single result", ["4. 单个结果: 第 1 个 (共 1 个)"]),
    # The parent of the check box: the `vertical` LinearLayout, or the frame where the service leaves out views that are
    # not important for accessibility (MIUI 22120RN86C: the window has 5 nodes); c3 reaches the button either way.
    ("compass step p1", ["罗盘 p1 -> android.widget."]),
    ("compass step c3", ["罗盘 c3 -> android.widget.Button"]),
    ("result kind", ["4. 结果类型: method ; 快路径: null ; 命中刷新: true"]),
    ("report single result", ["single result: ", "(match 1 of 1 by prefer(\"visible\"))"]),
    ("report compass", ["compass \"p1c3\":"]),
    ("report result", ["result \"text\": method UiObject.text(): String"]),
    ("report fast path", ["fast path: none, no exact id condition"]),
    ("report refresh", ["refresh on hit: on, the single result refreshes"]),
    ("report normalize", ["normalize: off"]),
    ("dump text", ["5. 文本形式:"]),
    ("dump json keys", ["5. JSON 形式的键:"]),
    ("dump xml", ["5. XML 形式 (1 层):"]),
    ("toJSON", ["6. {"]),
    ("stats", ["7. 搜索"]),
    ("finished", ["完成"]),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--show-lines", action="store_true", help="print the console lines (synthetic texts of the sample)")
    args = parser.parse_args()
    info = d.device_info(args.serial)
    print(f"device {info.get('model')} API {info.get('sdk')} versionCode {info.get('versionCode')}")
    if not d.a11y_enabled(args.serial):
        sys.exit("the accessibility service of AutoJs6 is not enabled")
    remote = d.push(args.serial, SAMPLE, "sample-explain.js")
    lines = d.run_script(args.serial, remote, 90)
    d.adb(args.serial, "shell", "rm", "-f", remote)
    if args.show_lines:
        for line in lines:
            print("  | " + line)
    ok = True
    for name, parts in CHECKS:
        hit = any(all(p in line for p in parts) for line in lines)
        ok = ok and hit
        print(f"  {name}: {'PASS' if hit else 'FAIL'}")
    errors = d.script_errors(lines)
    if errors:
        ok = False
        print(f"  errors: FAIL ({len(errors)} line(s), see --show-lines)")
    print(f"overall: {'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
