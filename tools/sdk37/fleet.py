#!/usr/bin/env python3
"""Plugin fleet helper for the SDK 37 roadmap (D21): scan, plan and apply the SDK bump per repository.

`scan` is read-only: for every `AutoJs6-Plugin-*` sibling of the host checkout it records the SDK values
in `version.properties`, hardcoded `compileSdk` / `targetSdk` literals in Gradle scripts, whether build
scripts consume the shared `versions.sdkVersion*` accessors, manifest exposure (permissions, services,
foreground service types, accessibility services), git dirtiness and the last commit. `table` renders
the scan as the Roadmap appendix E rows. `plan` lists, per repository, what the next step is under
D11 (clean tree required; exposed plugins go two-step). `apply` edits `version.properties` of ONE
repository (compile and / or target), optionally runs its Gradle checks, and never commits.
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

HOST_ROOT = Path(__file__).resolve().parents[2]
PROJECTS = HOST_ROOT.parent
PREFIX = "AutoJs6-Plugin-"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
SDK_LITERAL = re.compile(r"\b(compileSdk|targetSdk|compileSdkVersion|targetSdkVersion)\s*=\s*(\d{2})\b")
ACCESSOR = re.compile(r"versions\.sdkVersion(?:Compile|Target)|sdkVersion(?:Compile|Target)\b")
EXPOSURE_PERMISSIONS = {
    "android.permission.INTERNET": "INTERNET",
    "android.permission.SYSTEM_ALERT_WINDOW": "悬浮窗",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "MANAGE_EXTERNAL_STORAGE",
    "android.permission.WRITE_SECURE_SETTINGS": "WRITE_SECURE_SETTINGS",
    "android.permission.ACCESS_LOCAL_NETWORK": "ACCESS_LOCAL_NETWORK",
    "android.permission.RECORD_AUDIO": "RECORD_AUDIO",
    "android.permission.POST_NOTIFICATIONS": "POST_NOTIFICATIONS",
}
# Repositories whose manifest exposure requires the two-step rule of D11 even without a foreground service.
TWO_STEP_MARKERS = ("INTERNET", "悬浮窗", "前台服务", "无障碍服务", "WRITE_SECURE_SETTINGS", "MANAGE_EXTERNAL_STORAGE")


def run(command, cwd, timeout=60):
    completed = subprocess.run(command, cwd=str(cwd), capture_output=True, text=True, encoding="utf-8",
                               errors="replace", timeout=timeout)
    return completed.returncode, completed.stdout, completed.stderr


def read_properties(path):
    values = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, _, value = line.partition("=")
                values[key.strip()] = value.strip()
    return values


def gradle_scripts(repo):
    """Build scripts of the repository itself; build outputs and test fixtures are not its SDK source."""
    for path in repo.rglob("*.gradle.kts"):
        parts = path.relative_to(repo).parts[:-1]
        if "build" in parts or ".gradle" in parts or "test-fixtures" in parts:
            continue
        yield path


def manifests(repo):
    for path in repo.rglob("AndroidManifest.xml"):
        parts = path.relative_to(repo).parts
        if "build" in parts or "test" in parts or "androidTest" in parts:
            continue
        if "src" in parts and "main" in parts:
            yield path


def manifest_exposure(path):
    """Permission / service facts of one manifest, expressed with the appendix E vocabulary."""
    facts = set()
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return {"manifest error"}
    for element in root.iter("uses-permission"):
        name = element.get(ANDROID_NS + "name", "")
        if name in EXPOSURE_PERMISSIONS:
            facts.add(EXPOSURE_PERMISSIONS[name])
    for service in root.iter("service"):
        fgs = service.get(ANDROID_NS + "foregroundServiceType")
        if fgs:
            facts.add("前台服务 " + fgs)
        if service.get(ANDROID_NS + "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE":
            facts.add("无障碍服务 " + service.get(ANDROID_NS + "name", "").rsplit(".", 1)[-1])
    for element in root.iter("uses-feature"):
        if element.get(ANDROID_NS + "name", "").startswith("android.hardware.npu"):
            facts.add("NPU 特性声明")
    return facts


def scan_repo(repo):
    props = read_properties(repo / "version.properties")
    hardcoded = []
    accessor_files = 0
    for script in gradle_scripts(repo):
        text = script.read_text(encoding="utf-8", errors="replace")
        for match in SDK_LITERAL.finditer(text):
            hardcoded.append({"file": str(script.relative_to(repo)).replace("\\", "/"), "key": match[1], "value": int(match[2])})
        if ACCESSOR.search(text):
            accessor_files += 1
    exposure = set()
    for manifest in manifests(repo):
        exposure |= manifest_exposure(manifest)
    code, status, _ = run(["git", "status", "--short"], repo)
    dirty = len([line for line in status.splitlines() if line.strip()]) if code == 0 else None
    code, last, _ = run(["git", "log", "-1", "--format=%h %ad", "--date=short"], repo)
    return {
        "name": repo.name[len(PREFIX):],
        "path": str(repo),
        "compileSdk": int(props["COMPILE_SDK_VERSION"]) if props.get("COMPILE_SDK_VERSION", "").isdigit() else None,
        "targetSdk": int(props["TARGET_SDK_VERSION"]) if props.get("TARGET_SDK_VERSION", "").isdigit() else None,
        "minSdk": int(props["MIN_SDK_VERSION"]) if props.get("MIN_SDK_VERSION", "").isdigit() else None,
        "versionName": props.get("VERSION_NAME"),
        "versionBuild": props.get("VERSION_BUILD") or props.get("VERSION_CODE"),
        "hardcoded": hardcoded,
        "accessorFiles": accessor_files,
        "exposure": sorted(exposure),
        "dirty": dirty,
        "lastCommit": last.strip() if code == 0 else None,
    }


def scan(projects=PROJECTS):
    repos = sorted(p for p in projects.iterdir() if p.is_dir() and p.name.startswith(PREFIX) and (p / ".git").exists())
    return {"scannedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "projects": str(projects),
            "repositories": [scan_repo(repo) for repo in repos]}


def effective(entry, key):
    """Effective SDK value: a hardcoded literal in a Gradle script overrides version.properties."""
    literals = {h["value"] for h in entry["hardcoded"] if h["key"].lower().startswith(key.lower())}
    if literals:
        return max(literals), True
    return entry[key + "Sdk"], False


def plan_entry(entry):
    compile_sdk, compile_hardcoded = effective(entry, "compile")
    target_sdk, target_hardcoded = effective(entry, "target")
    two_step = any(fact.startswith(marker) for fact in entry["exposure"] for marker in TWO_STEP_MARKERS)
    steps = []
    if entry["dirty"]:
        steps.append(f"维护者先处理 {entry['dirty']} 个未提交文件 (D11)")
    if compile_hardcoded or target_hardcoded:
        steps.append("先把硬编码的 compileSdk / targetSdk 统一到 version.properties")
    if compile_sdk != 37:
        steps.append("compileSdk -> 37, 构建 + 单测 + 既有门禁")
    if target_sdk != 37:
        steps.append("targetSdk -> 37" + (" (独立提交, 先在 AVD_API_37 复测暴露项)" if two_step else " (可与 compileSdk 同一发布)"))
    if compile_sdk == 37 and target_sdk == 37 and not steps:
        steps.append("已是 37 / 37, 只需版本递增与 changelog")
    return {"name": entry["name"], "compileSdk": compile_sdk, "targetSdk": target_sdk, "twoStep": two_step, "steps": steps}


def table(report):
    lines = ["| 仓库 | compile / target / min | 版本 | 暴露 (manifest 扫描) | 硬编码 | 脏 | 最后提交 |",
             "| --- | --- | --- | --- | --- | --- | --- |"]
    for entry in report["repositories"]:
        compile_sdk, ch = effective(entry, "compile")
        target_sdk, th = effective(entry, "target")
        hard = ", ".join(sorted({f"{h['file']}:{h['key']}={h['value']}" for h in entry["hardcoded"]})) or "-"
        lines.append(f"| {entry['name']} | {compile_sdk} / {target_sdk} / {entry['minSdk']} | {entry['versionName']} ({entry['versionBuild']}) "
                     f"| {', '.join(entry['exposure']) or '-'} | {hard} | {entry['dirty']} | {entry['lastCommit']} |")
    return "\n".join(lines)


def apply(entry_name, compile_sdk, target_sdk, gradle_tasks, projects=PROJECTS):
    repo = projects / (PREFIX + entry_name)
    if not (repo / ".git").exists():
        raise SystemExit("Unknown repository: " + entry_name)
    entry = scan_repo(repo)
    if entry["dirty"]:
        raise SystemExit(f"{entry_name} has {entry['dirty']} uncommitted files; D11 requires a clean tree")
    if entry["hardcoded"]:
        raise SystemExit(f"{entry_name} hardcodes SDK versions in Gradle scripts; unify them first: {entry['hardcoded']}")
    path = repo / "version.properties"
    text = path.read_text(encoding="utf-8")
    for key, value in (("COMPILE_SDK_VERSION", compile_sdk), ("TARGET_SDK_VERSION", target_sdk)):
        if value is None:
            continue
        if not re.search(rf"^{key}=\d+$", text, re.MULTILINE):
            raise SystemExit(f"{key} missing in {path}")
        text = re.sub(rf"^{key}=\d+$", f"{key}={value}", text, flags=re.MULTILINE)
    path.write_text(text, encoding="utf-8")
    print(f"{entry_name}: version.properties -> compile {compile_sdk or 'unchanged'} / target {target_sdk or 'unchanged'}")
    if gradle_tasks:
        wrapper = repo / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
        command = [str(wrapper), "--offline", "--console=plain", *gradle_tasks]
        print("running:", " ".join(command))
        completed = subprocess.run(command, cwd=str(repo))
        return completed.returncode
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--projects", type=Path, default=PROJECTS, help="Directory containing the AutoJs6-Plugin-* checkouts")
    sub = parser.add_subparsers(dest="action", required=True)
    scan_parser = sub.add_parser("scan")
    scan_parser.add_argument("--output", type=Path, help="Write the JSON report here")
    sub.add_parser("table")
    sub.add_parser("plan")
    apply_parser = sub.add_parser("apply")
    apply_parser.add_argument("repo", help="Repository name without the AutoJs6-Plugin- prefix")
    apply_parser.add_argument("--compile", type=int)
    apply_parser.add_argument("--target", type=int)
    apply_parser.add_argument("--gradle", nargs="*", default=[], help="Gradle tasks to run afterwards, e.g. assembleDebug testDebugUnitTest")
    args = parser.parse_args()
    if args.action == "apply":
        if args.compile is None and args.target is None:
            parser.error("apply needs --compile and / or --target")
        return apply(args.repo, args.compile, args.target, args.gradle, args.projects)
    report = scan(args.projects)
    if args.action == "scan":
        text = json.dumps(report, ensure_ascii=False, indent=2)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(text + "\n", encoding="utf-8")
        print(text if not args.output else f"{len(report['repositories'])} repositories -> {args.output}")
    elif args.action == "table":
        print(table(report))
    else:
        for entry in report["repositories"]:
            plan = plan_entry(entry)
            print(f"{plan['name']}: {plan['compileSdk']} / {plan['targetSdk']}" + (" [two-step]" if plan["twoStep"] else "") + " -> " + "; ".join(plan["steps"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
