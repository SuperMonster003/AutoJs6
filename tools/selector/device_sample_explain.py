"""Runs the accessibility samples of the host on a device as they ship and checks their console output (ROADMAP selector
P3.5, P4.4, P5.1): "选择器诊断与节点树导出" (`explain`: the tallies of auto.explain, the single result, the compass trail and the result
type of the auto.explain(selector, compass, resultType) form with the fields of its result, the dump forms and
auto.stats) and "选择器规范化与候选偏好" (`normalize`: normalize(...) in its overloads, options and forms, prefer(...) with
each key, the key order, the compass step k and the forms); for the release gate (P5.1) also the other samples of the
accessibility automation roadmap P6.4 ("流程 (Flow) 异步等待与链式动作", "工具集 表单填写与弹窗关闭", "工具集 列表滚动与采集",
"字符串选择器与惰性迭代", "事件驱动等待") and the developer smoke test sample/accessibility/smoke-legacy-api.js (its own
"N/N checks passed" summary). Prints PASS / FAIL per check (no node text: the samples work on floaty windows of their own
with synthetic texts, and only the check names are printed; the smoke test works on the main screen of AutoJs6).

Usage: py -3.12 -I tools/selector/device_sample_explain.py -s SERIAL [--samples explain normalize flow form list string
    events smoke] [--show-lines]
Needs the accessibility service of AutoJs6 running and the floaty permission; no taps (the samples go home first). The
smoke test clicks a node and scrolls on the main screen of AutoJs6, and restarts the accessibility service only when
AutoJs6 can do it unattended (root, secure settings or Shizuku), otherwise it skips those checks.
"""
import argparse
import os
import re
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

FLOW_CHECKS = [
    ("chain pending", ["1. 链已建立, 当前状态: pending"]),
    ("chain wait", ["1. 出现了: 加载完成"]),
    ("waitForStable", ["2. 计数已稳定: 计数 5"]),
    ("waitForHidden", ["3. \"加载完成\" 已消失"]),
    ("catch FlowError", ["4. FlowError TIMEOUT: 步骤 "]),
    ("all", ["5. all: 开始加载 | 计数 5"]),
    ("race and cancel", ["5. race: 定时器先到 ; 取消落败者: true"]),
    ("sync timeout", ["6. sync 限时到期: TIMEOUT sync ; 链仍在等待: pending"]),
    ("cancel", ["6. 取消: true 状态: "]),
    ("finished", ["完成"]),
]

FORM_CHECKS = [
    ("typeInto index", ["1. 用户名: alice"]),
    ("typeInto selector", ["2. 密码框上报的文本: "]),
    ("typeInto append", ["3. 追加后: alice@example.com"]),
    ("toggle", ["4. 记住我: true ", "; 再设一次: 无变化"]),
    ("smartClick verify", ["5. 登录按钮以 ", "; 结果: 欢迎, alice@example.com (已记住)"]),
    ("dismissPopups none", ["6. 没有弹窗时: null"]),
    ("dismissPopups", ["6. 关闭了候选 #0 (以后再说), 方式: "]),
    ("clickIfExists", ["7. clickIfExists: false true"]),
    ("FlowError", ["8. FlowError "]),
    ("flow typeInto", ["9. Flow 输入: bob"]),
    ("flow toggle", ["9. Flow 取消勾选: true"]),
    ("flow smartClick", ["9. Flow 结果: 欢迎, bob"]),
    ("finished", ["完成"]),
]

LIST_CHECKS = [
    ("collectList", ["1. 采集到 ", "第 1 项, 第 2 项, 第 3 项 ..."]),
    ("collectList until", ["2. 向上采集到 \"第 1 项\" 为止: ", "最后一项: 第 1 项"]),
    ("collectList maxItems", ["2. 最多 3 项: "]),
    ("scrollUntil down", ["3. 滚到了: 第 30 项"]),
    ("scrollUntil up", ["3. 回到顶部: 第 1 项"]),
    ("scrollUntil missing", ["4. ", " reason: "]),
    ("flow collectList", ["5. Flow 采集: "]),
    ("flow scrollUntil", ["5. Flow 滚到: 第 20 项"]),
    ("finished", ["完成"]),
]

STRING_CHECKS = [
    ("key=value and flag", ["1. 确定"]),
    ("regex and !flag", ["1. 已禁用"]),
    ("!password", ["1. 输入框 2 个, 其中非密码框 1 个"]),
    ("append and ~=", ["2. 确定, 取消"]),
    ("quoted values", ["3. true false"]),
    # e.message carries the Java class name in front (org.autojs...SelectorStringException: Invalid selector string ...).
    ("syntax error", ["4. ", "Invalid selector string at 8 in ", "unknown property", "did you mean clickable?"]),
    ("findIterator stop", ["5. 找到并停止于: 取消"]),
    ("findIterator counts", ["5. 已访问节点 ", "; 已关闭: "]),
    ("for...of break", ["6. 第 2 项"]),
    ("find(max)", ["7. 前两项: 第 1 项, 第 2 项 ; 最后一项: 第 3 项"]),
    ("collection methods", ["7. first / last / at(-1) / slice(1): 第 1 项 第 3 项 第 3 项 2"]),
    ("content and select", ["8. 确定 | 取消"]),
    ("finished", ["完成"]),
]

EVENTS_CHECKS = [
    ("waitForIdle", ["1. 安静了 "]),
    ("waitForEvent flow", ["2. 事件: TYPE_WINDOW", " 来自 org.autojs.autojs6 "]),
    ("waitForEvent sync", ["3. 点击事件的类名: android.widget.Button"]),
    ("waitForToast timeout", ["4. FlowError TIMEOUT "]),
    ("waitForNotification", ["5. 通知: 示例通知 - 事件驱动等待"]),
    ("chained notification", ["6. Flow: 链式步骤"]),
    ("finished", ["完成"]),
]

SMOKE_SUMMARY = re.compile(r"(\d+)/(\d+) checks passed")


def smoke_passed(lines):
    for line in lines:
        m = SMOKE_SUMMARY.search(line)
        if m:
            return m.group(1) == m.group(2) and m.group(1) != "0"
    return False


# The smoke test prints [PASS] / [FAIL] per check and the summary; [FAIL] lines go to console.error (checked as errors).
SMOKE_CHECKS = [
    ("all checks passed", smoke_passed),
    ("no failed check", lambda lines: not any("[FAIL]" in line for line in lines)),
]

SAMPLES = {
    "explain": ("选择器诊断与节点树导出 [v6.8.0+].js", EXPLAIN_CHECKS),
    "normalize": ("选择器规范化与候选偏好 [v6.8.0+].js", NORMALIZE_CHECKS),
    "flow": ("流程 (Flow) 异步等待与链式动作 [v6.8.0+].js", FLOW_CHECKS),
    "form": ("工具集 表单填写与弹窗关闭 [v6.8.0+].js", FORM_CHECKS),
    "list": ("工具集 列表滚动与采集 [v6.8.0+].js", LIST_CHECKS),
    "string": ("字符串选择器与惰性迭代 [v6.8.0+].js", STRING_CHECKS),
    "events": ("事件驱动等待 [v6.8.0+].js", EVENTS_CHECKS),
    "smoke": (d.HOST / "sample/accessibility/smoke-legacy-api.js", SMOKE_CHECKS),
}

# Seconds to wait for a run; the smoke test waits for window events and may restart the service.
WAIT = {"smoke": 240}


def run_sample(serial, key, show_lines):
    name, checks = SAMPLES[key]
    print(f"sample {key}")
    remote = d.push(serial, SAMPLE_DIR / name, f"sample-{key}.js")
    lines = d.run_script(serial, remote, WAIT.get(key, 90))
    d.adb(serial, "shell", "rm", "-f", remote)
    if show_lines:
        for line in lines:
            print("  | " + line)
    ok = True
    for check, parts in checks:
        hit = parts(lines) if callable(parts) else any(all(p in line for p in parts) for line in lines)
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
