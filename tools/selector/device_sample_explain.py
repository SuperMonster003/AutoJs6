"""Runs the selector samples of the host on a device as they ship and checks their console output (ROADMAP selector P3.5,
P4.4): "选择器诊断与节点树导出" (`explain`: the tallies of auto.explain, the single result, the compass trail and the result
type of the auto.explain(selector, compass, resultType) form with the fields of its result, the dump forms and
auto.stats) and "选择器规范化与候选偏好" (`normalize`: normalize(...) in its overloads, options and forms, prefer(...) with
each key, the key order, the compass step k and the forms); prints PASS / FAIL per check (no node text: the samples work
on floaty windows of their own with synthetic texts, and only the check names are printed).

Usage: py -3.12 -I tools/selector/device_sample_explain.py -s SERIAL [--samples explain normalize] [--show-lines]
Needs the accessibility service of AutoJs6 running and the floaty permission; no taps (the samples go home first).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _device as d  # noqa: E402

SAMPLE_DIR = d.HOST / "app/src/main/assets-app/sample/无障碍服务"

# (name, every one of these substrings in one console line)
EXPLAIN_CHECKS = [
    ("explain found", ["1. 找到: true 数量: 1"]),
    ("explain no match", ["2. 找到: false ; 失败于第 1 个过滤器"]),
    ("explain root", ["3. 限定在悬浮窗内: 1 个"]),
    ("single result", ["4. 单个结果: 第 1 个 (共 1 个)"]),
    # The parent of the check box: the `vertical` LinearLayout, or the frame where the service leaves out views that are
    # not important for accessibility (MIUI 22120RN86C: the window has 5 nodes); c3 reaches the button either way.
    ("compass step p1", ["罗盘 p1 -> android.widget."]),
    ("compass step c3", ["罗盘 c3 -> android.widget.Button"]),
    ("result kind", ["4. 结果类型: method ; 快路径: null ; 命中刷新: true"]),
    ("result fields", ["4. 目标: 同意条款 ; 偏好: prefer(\"visible\") ; 罗盘: \"p1c3\" ; 终点: 登录 ; 目标能否刷新: true"]),
    ("report single result", ["single result: ", "(match 1 of 1 by prefer(\"visible\"))"]),
    ("report compass", ["compass \"p1c3\":"]),
    ("report result", ["result \"text\": method UiObject.text(): String"]),
    ("report fast path", ["fast path: none, no exact id condition"]),
    ("report refresh", ["refresh on hit: on, the single result refreshes"]),
    ("report normalize", ["normalize: off"]),
    ("refused result type", ["4. 写错的结果类型: refused ; 说明: ", "; normalize: normalize({trim: true}) ; 找到: true"]),
    ("fast path key", ["4. 快路径的键: id(", "; 已开启: true"]),
    ("dump text", ["5. 文本形式:"]),
    ("dump json keys", ["5. JSON 形式的键:"]),
    ("dump xml", ["5. XML 形式 (1 层):"]),
    ("toJSON", ["6. {"]),
    ("stats", ["7. 搜索"]),
    ("finished", ["完成"]),
]

NORMALIZE_CHECKS = [
    ("normalize both sides", ["1. 不规范化: false ; normalize(): true ; 小写也匹配: true"]),
    ("normalize options", ["2. 大小写一致: true ; 大小写不同: false ; 加上 caseInsensitive: true"]),
    ("normalize setting", ["3. 写在条件之前: true ; normalize().normalize(false): false"]),
    ("normalize toString", ["3. toString: text(\"Hello World\").normalize({trim: true, caseInsensitive: true})"]),
    ("normalize conditions", ["4. textContains: true ; textStartsWith: true ; textMatch: true"]),
    ("normalize nfkc", ["5. 不规范化: false ; nfkc: true"]),
    ("normalize names", ["6. className: false ; 忽略大小写: true"]),
    ("normalize object", ["7. 对象选择器: true true"]),
    ("normalize string", ["7. 字符串选择器: true false"]),
    ("prefer clickable", ["8. 默认: android.widget.TextView ; prefer(clickable): android.widget.Button"]),
    ("prefer enabled", ["9. 默认: false ; prefer(enabled): true"]),
    ("prefer key order", ["9. prefer(top, enabled): false ; prefer(enabled, top): true"]),
    ("prefer visible", ["10. 默认: false ; prefer(visible): true ; pickup: true"]),
    ("prefer compass k", ["11. 罗盘 k: android.widget.", "; 可点击: true ; 可见: true ; 不加 prefer: false"]),
    ("prefer area / top", ["12. area 取大的: true ; top 取上方的: true"]),
    ("prefer center", ["12. center 取中心离屏幕中心近的: true"]),
    ("prefer forms", ["13. true true true"]),
    ("normalize refusal", ["14. ", "The options of normalize take booleans"]),
    ("prefer refusal", ["14. ", "Unknown key of prefer: \"left\""]),
    ("finished", ["完成"]),
]

SAMPLES = {
    "explain": ("选择器诊断与节点树导出 [v6.8.0+].js", EXPLAIN_CHECKS),
    "normalize": ("选择器规范化与候选偏好 [v6.8.0+].js", NORMALIZE_CHECKS),
}


def run_sample(serial, key, show_lines):
    name, checks = SAMPLES[key]
    print(f"sample {key}")
    remote = d.push(serial, SAMPLE_DIR / name, f"sample-{key}.js")
    lines = d.run_script(serial, remote, 90)
    d.adb(serial, "shell", "rm", "-f", remote)
    if show_lines:
        for line in lines:
            print("  | " + line)
    ok = True
    for check, parts in checks:
        hit = any(all(p in line for p in parts) for line in lines)
        ok = ok and hit
        print(f"  {check}: {'PASS' if hit else 'FAIL'}")
    errors = d.script_errors(lines)
    if errors:
        ok = False
        print(f"  errors: FAIL ({len(errors)} line(s), see --show-lines)")
    print(f"  {key}: {'PASS' if ok else 'FAIL'} ({len(checks)} checks)")
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--samples", nargs="+", choices=list(SAMPLES), default=list(SAMPLES))
    parser.add_argument("--show-lines", action="store_true", help="print the console lines (synthetic texts of the samples)")
    args = parser.parse_args()
    info = d.device_info(args.serial)
    print(f"device {info.get('model')} API {info.get('sdk')} versionCode {info.get('versionCode')}")
    if not d.a11y_enabled(args.serial):
        sys.exit("the accessibility service of AutoJs6 is not enabled")
    ok = True
    for key in args.samples:
        ok = run_sample(args.serial, key, args.show_lines) and ok
    print(f"overall: {'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
