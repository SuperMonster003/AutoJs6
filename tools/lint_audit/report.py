#!/usr/bin/env python3
"""Classify Android lint XML reports and compare identities without line-number noise."""

import argparse
from collections import Counter
import csv
from dataclasses import dataclass
from fnmatch import fnmatchcase
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


GROUPS = {
    "翻译与文案": {"MissingTranslation", "ExtraTranslation", "PluralsCandidate", "TypographyEllipsis",
                  "TypographyDashes", "HardcodedText", "SetTextI18n", "Typos", "StringFormatInvalid", "StringFormatMatches"},
    "Android API 与平台兼容": {"NewApi", "InlinedApi", "ObsoleteSdkInt", "GestureBackNavigation",
                              "AppCompatCustomView", "UseCompatLoadingForDrawables", "UseCompatTextViewDrawableApis",
                              "UseSwitchCompatOrMaterialCode", "AppBundleLocaleChanges"},
    "资源整理": {"UnusedResources", "UnusedAttribute", "IconLocation", "IconDipSize", "IconDensities",
                "IconDuplicatesConfig", "IconDuplicates", "IconXmlAndPng", "IconColors", "PrivateResource"},
    "权限与安全": {"MissingPermission", "UnsafeDynamicallyLoadedCode", "SetJavaScriptEnabled",
                  "InsecureBaseConfiguration", "ExportedContentProvider", "SelectedPhotoAccess",
                  "IntentWithNullActionLaunch", "SdCardPath"},
    "生命周期与资源释放": {"Recycle", "CheckResult", "StaticFieldLeak", "ImplicitSamInstance", "MissingSuperCall"},
    "界面与无障碍": {"ContentDescription", "ClickableViewAccessibility", "RtlHardcoded", "RtlSymmetry",
                    "Overdraw", "LabelFor", "AlwaysShowAction", "UselessParent", "Autofill",
                    "UseCompoundDrawables", "TextFields", "ViewConstructor", "KeyboardInaccessibleWidget",
                    "DisableBaselineAlignment", "ButtonStyle", "PxUsage", "RedundantLabel",
                    "RedundantNamespace", "TooDeepLayout", "TooManyViews", "InternalInsetResource"},
    "依赖与建议用法": {"GradleDependency", "NewerVersionAvailable", "UseKtx", "NotifyDataSetChanged",
                      "ApplySharedPref", "ReportShortcutUsage"},
}


def group(issue_id):
    return next((name for name, ids in GROUPS.items() if issue_id in ids), "其他代码正确性与质量")


@dataclass(frozen=True)
class Issue:
    id: str
    severity: str
    message: str
    locations: tuple
    line: str = ""

    @property
    def identity(self):
        return self.id, self.severity, self.message, self.locations


def relative_path(value, repo_root):
    path = value.replace("\\", "/")
    prefix = str(repo_root.resolve()).replace("\\", "/").rstrip("/") + "/"
    return path[len(prefix):] if path.casefold().startswith(prefix.casefold()) else path


def read_report(path, repo_root):
    root = ET.parse(path).getroot()
    if root.tag != "issues":
        raise ValueError(f"Not an Android lint XML report: {path}")
    issues = []
    for item in root.findall("issue"):
        locations = item.findall("location")
        issues.append(Issue(item.attrib["id"], item.attrib["severity"], item.attrib["message"],
                            tuple(relative_path(loc.get("file", ""), repo_root) for loc in locations),
                            locations[0].get("line", "") if locations else ""))
    return issues


def legacy_paths(config):
    # Only the explicit path-based "all" compatibility exceptions count as frozen legacy scope.
    return ["app/" + node.attrib["path"] for item in ET.parse(config).getroot().findall("issue")
            if item.get("id") == "all" for node in item.findall("ignore") if "path" in node.attrib]


def legacy(issue, paths):
    return bool(issue.locations and any(fnmatchcase(issue.locations[0], pattern) for pattern in paths))


def counts(issues):
    return dict(sorted(Counter(issue.severity for issue in issues).items()))


def compare(before, after, paths):
    old = Counter(issue.identity for issue in before)
    new = Counter(issue.identity for issue in after)
    removed = old - new
    added = new - old
    frozen = Counter(issue.identity for issue in before if legacy(issue, paths))
    suppressed = removed & frozen
    return removed, added, suppressed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--output", type=Path, required=True, help="Output directory for Markdown, JSON and CSV")
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--before-root", type=Path, help="Original checkout root, when different from --repo-root")
    parser.add_argument("--after-root", type=Path, help="Candidate checkout root, e.g. an isolated worktree")
    parser.add_argument("--fail-on-new-errors", action="store_true")
    args = parser.parse_args()
    before = read_report(args.before, args.before_root or args.repo_root)
    after = read_report(args.after, args.after_root or args.repo_root)
    paths = legacy_paths(args.repo_root / "app/lint.xml")
    removed, added, suppressed = compare(before, after, paths)
    args.output.mkdir(parents=True, exist_ok=True)
    new_errors = sum(n for identity, n in added.items() if identity[1] in {"Error", "Fatal"})
    summaries = []
    for severity, issue_id in sorted({(i.severity, i.id) for i in before + after}):
        summaries.append({
            "severity": severity, "id": issue_id, "group": group(issue_id),
            "before": sum(i.severity == severity and i.id == issue_id for i in before),
            "after": sum(i.severity == severity and i.id == issue_id for i in after),
            "legacy_suppressed": sum(n for (id_, level, _, _), n in suppressed.items()
                                     if id_ == issue_id and level == severity),
            "added": sum(n for (id_, level, _, _), n in added.items() if id_ == issue_id and level == severity),
        })
    grouped = []
    for name in sorted({group(i.id) for i in before + after}):
        grouped.append({
            "group": name,
            "before": counts([i for i in before if group(i.id) == name]),
            "after": counts([i for i in after if group(i.id) == name]),
            "legacy_suppressed": sum(n for (id_, _, _, _), n in suppressed.items() if group(id_) == name),
        })
    payload = {"before": counts(before), "after": counts(after), "new_errors": new_errors,
               "removed": sum(removed.values()), "added": sum(added.values()),
               "legacy_suppressed": sum(suppressed.values()), "groups": grouped, "rules": summaries}
    (args.output / "summary.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    with (args.output / "issues.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["state", "group", "severity", "id", "file", "line", "message", "all_locations"])
        pending_added = added.copy()
        for issue in after:
            state = "added" if pending_added[issue.identity] else "remaining"
            pending_added[issue.identity] -= state == "added"
            writer.writerow([state, group(issue.id), issue.severity, issue.id,
                             issue.locations[0] if issue.locations else "", issue.line, issue.message,
                             json.dumps(issue.locations, ensure_ascii=False)])
        pending_removed = removed.copy()
        for issue in before:
            if not pending_removed[issue.identity]:
                continue
            pending_removed[issue.identity] -= 1
            writer.writerow(["legacy_suppressed" if legacy(issue, paths) else "removed",
                             group(issue.id), issue.severity, issue.id,
                             issue.locations[0] if issue.locations else "", issue.line, issue.message,
                             json.dumps(issue.locations, ensure_ascii=False)])

    lines = ["# AutoJs6 lint 分类与对比", "", f"- 修复前: `{counts(before)}`", f"- 当前: `{counts(after)}`",
             f"- 新增 Error/Fatal: {new_errors}", f"- 按旧版兼容路径压制的诊断: {sum(suppressed.values())}", "",
             "按规则 ID、级别、消息及全部文件位置比较, 忽略行号变化并保留重复次数。",
             "非兼容范围内消失的诊断也可能来自经过核查的局部注解或其他并行任务, 不能全部视为运行时缺陷修复。",
             "原始 XML 的生成时间、成功执行日志及源码状态应与本报告一起保留; 本工具不会运行 lint。", "",
             "## 分类汇总", "", "数量格式为 错误/警告/提示, 错误包含 Error/Fatal。", "",
             "| 分类 | 修复前 | 当前 | 旧版路径压制 |", "|---|---:|---:|---:|"]
    for row in grouped:
        totals = [f"{row[key].get('Error', 0) + row[key].get('Fatal', 0)}/{row[key].get('Warning', 0)}/{row[key].get('Hint', 0)}"
                  for key in ("before", "after")]
        lines.append(f"| {row['group']} | {totals[0]} | {totals[1]} | {row['legacy_suppressed']} |")
    lines += ["", "## 规则明细", "", "| 分类 | 级别 | 规则 | 修复前 | 当前 | 旧版路径压制 | 新增 |",
              "|---|---|---|---:|---:|---:|---:|"]
    for row in summaries:
        lines.append(f"| {row['group']} | {row['severity']} | {row['id']} | {row['before']} | {row['after']} | {row['legacy_suppressed']} | {row['added']} |")
    lines += ["", "全部诊断与位置见同目录 `issues.csv`, 机器可读统计见 `summary.json`。", ""]
    (args.output / "report.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({key: value for key, value in payload.items() if key not in {"groups", "rules"}}, ensure_ascii=True))
    return 1 if args.fail_on_new_errors and new_errors else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ET.ParseError, KeyError, ValueError) as error:
        print(f"Cannot classify lint reports: {error}", file=sys.stderr)
        sys.exit(2)
