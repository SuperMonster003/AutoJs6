#!/usr/bin/env python3
"""Plugin fleet helper for the SDK 37 roadmap (D21): scan, plan and apply the SDK bump per repository.

`scan` is read-only: for every `AutoJs6-Plugin-*` sibling of the host checkout it records the SDK values
in `version.properties`, hardcoded `compileSdk` / `targetSdk` literals in Gradle scripts, whether build
scripts consume the shared `versions.sdkVersion*` accessors, manifest exposure (permissions, services,
foreground service types, accessibility services), git dirtiness and the last commit. `table` renders
the scan as the Roadmap appendix E rows. `plan` lists, per repository, what the next step is under
D11 (clean tree required; exposed plugins go two-step). `apply` edits `version.properties` of ONE
repository (compile and / or target), optionally runs its Gradle checks, and never commits. `bump` is
the per-repository release step of P5.0: SDK values, next patch VERSION_NAME, VERSION_BUILD = commit
count + 1, one release-note line in all ten changelogs, the repository's markdown generator and
checks, its Gradle build, an optional AVD install + instrumentation run, and (with --commit) one
Conventional Commits commit that is verified against the commit count afterwards.
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


BUMP_MESSAGE = "chore(sdk): raise compileSdk and targetSdk to 37 (Android 17)"
# Release-note line for repositories whose behaviour does not depend on the new target (Roadmap P5.0 item 5);
# exposed repositories pass their own wording through --note-file.
BUMP_NOTES = {
    "en": "Raise compileSdk and targetSdk to 37 (Android 17); the plugin's behavior does not depend on the new target",
    "zh-Hans": "将 compileSdk 与 targetSdk 提升到 37 (Android 17), 插件行为不受新目标版本影响",
    "zh-Hant-HK": "將 compileSdk 與 targetSdk 提升到 37 (Android 17), 插件行為不受新目標版本影響",
    "zh-Hant-TW": "將 compileSdk 與 targetSdk 提升到 37 (Android 17), 外掛程式行為不受新目標版本影響",
    "ja": "compileSdk と targetSdk を 37 (Android 17) に引き上げ, プラグインの動作は新しいターゲットの影響を受けない",
    "ko": "compileSdk 와 targetSdk 를 37 (Android 17) 로 올리며, 플러그인 동작은 새 대상 버전의 영향을 받지 않음",
    "ru": "Подняты compileSdk и targetSdk до 37 (Android 17); поведение плагина не зависит от нового целевого уровня",
    "fr": "compileSdk et targetSdk passent à 37 (Android 17) ; le comportement du plugin ne dépend pas de la nouvelle cible",
    "es": "compileSdk y targetSdk suben a 37 (Android 17); el comportamiento del plugin no depende del nuevo objetivo",
    "ar": "رفع compileSdk و targetSdk إلى 37 (Android 17), لا يعتمد سلوك المكون الإضافي على الهدف الجديد",
}
# First step of the two-step path (D11): compileSdk only, targetSdk stays until the exposed behaviour is verified.
BUMP_MESSAGE_COMPILE_ONLY = "chore(sdk): raise compileSdk to 37 (Android 17)"
BUMP_NOTES_COMPILE_ONLY = {
    "en": "Raise compileSdk to 37 (Android 17); targetSdk stays at 36 until the behavior that depends on the target is verified",
    "zh-Hans": "将 compileSdk 提升到 37 (Android 17), targetSdk 保持 36, 待依赖目标版本的行为验证后再提升",
    "zh-Hant-HK": "將 compileSdk 提升到 37 (Android 17), targetSdk 保持 36, 待依賴目標版本的行為驗證後再提升",
    "zh-Hant-TW": "將 compileSdk 提升到 37 (Android 17), targetSdk 保持 36, 待依賴目標版本的行為驗證後再提升",
    "ja": "compileSdk を 37 (Android 17) に引き上げ, targetSdk はターゲット依存の動作を検証するまで 36 のまま",
    "ko": "compileSdk 를 37 (Android 17) 로 올리며, targetSdk 는 대상 버전에 의존하는 동작을 검증할 때까지 36 으로 유지",
    "ru": "Поднят compileSdk до 37 (Android 17); targetSdk остаётся 36 до проверки поведения, зависящего от целевого уровня",
    "fr": "compileSdk passe à 37 (Android 17) ; targetSdk reste à 36 jusqu'à la vérification du comportement dépendant de la cible",
    "es": "compileSdk sube a 37 (Android 17); targetSdk se mantiene en 36 hasta verificar el comportamiento que depende del objetivo",
    "ar": "رفع compileSdk إلى 37 (Android 17), يبقى targetSdk عند 36 حتى يتم التحقق من السلوك المعتمد على الهدف",
}
# Second step of the two-step path, or repositories that were already compileSdk 37: targetSdk only.
BUMP_MESSAGE_TARGET_ONLY = "chore(sdk): raise targetSdk to 37 (Android 17)"
BUMP_NOTES_TARGET_ONLY = {
    "en": "Raise targetSdk to 37 (Android 17) after compileSdk; the plugin's behavior does not depend on the new target",
    "zh-Hans": "继 compileSdk 之后将 targetSdk 提升到 37 (Android 17), 插件行为不受新目标版本影响",
    "zh-Hant-HK": "繼 compileSdk 之後將 targetSdk 提升到 37 (Android 17), 插件行為不受新目標版本影響",
    "zh-Hant-TW": "繼 compileSdk 之後將 targetSdk 提升到 37 (Android 17), 外掛程式行為不受新目標版本影響",
    "ja": "compileSdk に続き targetSdk を 37 (Android 17) に引き上げ, プラグインの動作は新しいターゲットの影響を受けない",
    "ko": "compileSdk 에 이어 targetSdk 를 37 (Android 17) 로 올리며, 플러그인 동작은 새 대상 버전의 영향을 받지 않음",
    "ru": "Вслед за compileSdk поднят targetSdk до 37 (Android 17); поведение плагина не зависит от нового целевого уровня",
    "fr": "Après compileSdk, targetSdk passe à 37 (Android 17) ; le comportement du plugin ne dépend pas de la nouvelle cible",
    "es": "Tras compileSdk, targetSdk sube a 37 (Android 17); el comportamiento del plugin no depende del nuevo objetivo",
    "ar": "بعد compileSdk, رفع targetSdk إلى 37 (Android 17), لا يعتمد سلوك المكون الإضافي على الهدف الجديد",
}
# Files a bump may leave modified; anything else means the repository generator did more than expected.
BUMP_FILE_PREFIXES = ("version.properties", ".changelog/", ".readme/", "README", "CHANGELOG", "app/src/main/assets/doc/")
TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"


def bump_version_name(name):
    """Next patch version, e.g. 1.0.2 -> 1.0.3; a suffix such as 0.8.1-m9 or 0.2.1-provider-dev is kept, never promoted."""
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?", name.strip())
    if not match:
        raise ValueError("VERSION_NAME is not a semantic version: " + name)
    major, minor, patch = (int(part) for part in match.groups()[:3])
    return f"{major}.{minor}.{patch + 1}" + (match.group(4) or "")


def changelog_version(new_name, previous_name, previous_key):
    """The changelog key for `new_name`, following the repository's own precedent for milestone suffixes.

    Java Runtime keeps VERSION_NAME 0.8.1-m9 but files its changelog under v0.8.1; a repository whose
    top entry drops the suffix keeps dropping it, everyone else gets the plain "v" + VERSION_NAME.
    """
    stem, _, suffix = previous_name.strip().partition("-")
    if suffix and previous_key == "v" + stem:
        return new_name.partition("-")[0]
    return new_name


def set_properties(text, updates):
    """Replace `KEY=value` lines of a properties file; every key must already exist."""
    for key, value in updates.items():
        pattern = re.compile(rf"^{re.escape(key)}=.*$", re.MULTILINE)
        if not pattern.search(text):
            raise ValueError(key + " missing in version.properties")
        text = pattern.sub(f"{key}={value}", text, count=1)
    return text


def insert_changelog_entry(path, version, released_date, entries):
    """Prepend one version to a `.changelog/lang_*.json` file, keeping its indentation and final newline."""
    raw = path.read_text(encoding="utf-8")
    data = json.loads(raw)
    key = "v" + version
    if key in data["$data"]:
        raise ValueError(f"{path.name} already has {key}")
    data["$data"] = {key: {"released_date": released_date, **entries}, **data["$data"]}
    indent = 4 if raw.startswith("{\n    ") else 2
    path.write_text(json.dumps(data, ensure_ascii=False, indent=indent) + ("\n" if raw.endswith("\n") else ""), encoding="utf-8")


STALE_SDK_MENTION = re.compile(r"(?:\b(?:SDK|API)|Sdk) 36\b|\bAndroid 16\b")


def update_readme_common(text, compile_sdk, target_sdk, version_name=None, version_build=None):
    """Refresh the SDK numbers (and, where a repository keeps them there, the version) in `.readme/common.json`.

    Returns the new text and the keys that changed; keys the file does not have are left alone, and the
    file's formatting is preserved because only the quoted value is rewritten.
    """
    changed = []
    for key, value in (("android_compile_sdk", compile_sdk), ("android_target_sdk", target_sdk),
                       ("version_name", version_name), ("version_build", version_build)):
        if value is None:
            continue
        pattern = re.compile(r'("' + key + r'"\s*:\s*")([^"]*)(")')
        match = pattern.search(text)
        if match and match.group(2) != str(value):
            text = pattern.sub(lambda m: m.group(1) + str(value) + m.group(3), text, count=1)
            changed.append(key)
    return text, changed


def refresh_readme_keys(repo, keys, old, new):
    """Rewrite the bare number `old` -> `new` inside the named values of every `.readme/lang_*.json`.

    A few README templates spell the target level out in prose ("target SDK 36", "SDK objetivo 36",
    "ターゲットSDKは36です"); only the number changes, so the ten translations stay intact and the file
    layout is untouched. Returns {file name: replacements}.
    """
    number = re.compile("(?<![0-9])" + str(old) + "(?![0-9])")
    changed = {}
    for path in sorted((repo / ".readme").glob("lang_*.json")):
        text = path.read_text(encoding="utf-8")
        total = 0
        for key in keys:
            pattern = re.compile(r'("' + re.escape(key) + r'"\s*:\s*")((?:[^"\\]|\\.)*)(")')
            match = pattern.search(text)
            if not match:
                continue
            value, count = number.subn(str(new), match.group(2))
            if count:
                text = text[:match.start(2)] + value + text[match.end(2):]
                total += count
        if total:
            path.write_text(text, encoding="utf-8")
            changed[path.name] = total
    return changed


def stale_sdk_mentions(repo):
    """README template lines that still talk about the previous SDK level; reported, not fixed."""
    hits = []
    for path in sorted((repo / ".readme").glob("*.json")):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if STALE_SDK_MENTION.search(line):
                hits.append(f"{path.name}:{number}: {line.strip()[:120]}")
    return hits


def debug_apk_dirs(repo):
    """(flavor, debug APK dir, androidTest APK dir) candidates of the app module: the plain build, else product flavors.

    Flavored apps (Lua Runtime: provider / nativeTest / faultTest) keep their APKs under apk/<flavor>/debug; flavors
    named after a test harness come last so the shipped one is installed.
    """
    base = repo / "app" / "build" / "outputs" / "apk"
    if (base / "debug").is_dir():
        return [("", base / "debug", base / "androidTest" / "debug")]
    flavors = sorted(p.parent.name for p in base.glob("*/debug") if p.parent.name != "androidTest")
    ordered = [f for f in flavors if "test" not in f.lower()] + [f for f in flavors if "test" in f.lower()]
    return [(f, base / f / "debug", base / "androidTest" / f / "debug") for f in ordered]


def device_check(repo, adb, serial, sdk_root, steps, instrument_args=()):
    """Install the debug APK on the AVD, confirm its installed target, and run the androidTest APK if one was built.

    `instrument_args` are (key, value) pairs passed as `-e key value`, e.g. ("notClass", "...") to leave out a
    test whose golden is pinned to another environment; the step detail names them so the evidence shows it.
    """
    from physical_device import Device  # noqa: E402  (same directory)
    from run_gate import describe_apk, find_aapt2  # noqa: E402

    device = Device(adb, serial)
    aapt2 = find_aapt2(sdk_root)
    debug_apks, test_dir = [], None
    for flavor, debug_dir, candidate_test_dir in debug_apk_dirs(repo):
        debug_apks = sorted(debug_dir.glob("*.apk"))
        if debug_apks:
            test_dir = candidate_test_dir
            break
    if not debug_apks:
        steps.append({"name": "device install", "ok": False, "detail": "no debug APK under app/build/outputs/apk/[<flavor>/]debug"})
        return False
    apk = next((p for p in debug_apks if "universal" in p.name), debug_apks[0])
    info = describe_apk(aapt2, apk)
    installed = device.call("install", "-r", str(apk), timeout=600)
    dump = device.shell("dumpsys", "package", info["package"])["stdout"]
    target = re.search(r"\btargetSdk=(\d+)\b", dump)
    ok = "Success" in installed["stdout"] + installed["stderr"] and bool(target) and target[1] == str(info["targetSdk"])
    steps.append({"name": "device install " + apk.name, "ok": ok,
                  "detail": f"{info['package']} badging target {info['targetSdk']}, installed target {target[1] if target else '?'}, serial {serial}"})
    print(("[PASS] " if ok else "[FAIL] ") + "device install " + apk.name, flush=True)
    test_apks = sorted(test_dir.glob("*.apk"))
    if ok and test_apks:
        test_package = info["package"] + ".test"
        installed = device.call("install", "-r", str(test_apks[0]), timeout=600)
        extra = [part for key, value in instrument_args for part in ("-e", key, value)]
        result = device.shell("am", "instrument", "-w", "-r", *extra, test_package + "/" + TEST_RUNNER, timeout=1800)
        output = result["stdout"] + result["stderr"]
        summary = re.search(r"OK \((\d+) tests?\)", output)
        test_ok = "Success" in installed["stdout"] + installed["stderr"] and bool(summary) and "FAILURES!!!" not in output \
            and "INSTRUMENTATION_STATUS_CODE: -3" not in output and "Process crashed" not in output
        steps.append({"name": "device instrumentation", "ok": test_ok,
                      "detail": (summary[0] if summary else output.strip()[-300:]) + f" ({test_package}"
                      + (", " + " ".join(extra) if extra else "") + ")"})
        print(("[PASS] " if test_ok else "[FAIL] ") + "device instrumentation", flush=True)
        device.shell("pm", "uninstall", test_package, timeout=120)
        ok = test_ok
    return ok


def bump(entry_name, compile_sdk, target_sdk, version_name, notes, gradle_tasks, commit, adb, serial, sdk_root, log_path,
         projects=PROJECTS, readme_keys=(), instrument_args=(), replacements=()):
    """One repository, one release step: SDK values, version, ten changelogs, generated docs, checks, build, device, commit.

    `replacements` are (relative path, old text, new text) triples for repository-specific files that mirror the
    SDK level (e.g. Python Runtime's `locks/python-runtime.lock`); each old text must occur exactly once.
    """
    repo = projects / (PREFIX + entry_name)
    if not (repo / ".git").exists():
        raise SystemExit("Unknown repository: " + entry_name)
    entry = scan_repo(repo)
    if entry["dirty"]:
        raise SystemExit(f"{entry_name} has {entry['dirty']} uncommitted files; D11 requires a clean tree")
    if entry["hardcoded"]:
        raise SystemExit(f"{entry_name} hardcodes SDK versions in Gradle scripts; unify them first: {entry['hardcoded']}")
    changelogs = sorted((repo / ".changelog").glob("lang_*.json"))
    if len(changelogs) != 10:
        raise SystemExit(f"{entry_name}: expected ten changelog files, found {len(changelogs)}")
    missing = [p.stem[len("lang_"):] for p in changelogs if p.stem[len("lang_"):] not in notes]
    if missing:
        raise SystemExit(f"{entry_name}: no release note for {missing}")
    props = read_properties(repo / "version.properties")
    count = int(run(["git", "rev-list", "--count", "HEAD"], repo)[1].strip())
    new_name = version_name or bump_version_name(props["VERSION_NAME"])
    updates = {"VERSION_NAME": new_name, "VERSION_BUILD": count + 1}
    if compile_sdk is not None:
        updates["COMPILE_SDK_VERSION"] = compile_sdk
    if target_sdk is not None:
        updates["TARGET_SDK_VERSION"] = target_sdk
    properties_path = repo / "version.properties"
    properties_path.write_text(set_properties(properties_path.read_text(encoding="utf-8"), updates), encoding="utf-8")
    today = datetime.date.today().strftime("%Y/%m/%d")
    top_key = next(iter(json.loads(changelogs[0].read_text(encoding="utf-8"))["$data"]))
    entry_version = changelog_version(new_name, props["VERSION_NAME"], top_key)
    for path in changelogs:
        insert_changelog_entry(path, entry_version, today, {"improvement": [notes[path.stem[len("lang_"):]]]})
    print(f"{entry_name}: {props.get('VERSION_NAME')} ({props.get('VERSION_BUILD')}) -> {new_name} ({count + 1}), "
          f"compile {updates.get('COMPILE_SDK_VERSION', props.get('COMPILE_SDK_VERSION'))} / "
          f"target {updates.get('TARGET_SDK_VERSION', props.get('TARGET_SDK_VERSION'))}", flush=True)

    steps = []

    def step(name, command, timeout=3600):
        code, out, err = run(command, repo, timeout)
        steps.append({"name": name, "ok": code == 0, "detail": (out + err).strip()[-400:]})
        print(("[PASS] " if code == 0 else "[FAIL] ") + name, flush=True)
        return code == 0

    common = repo / ".readme" / "common.json"
    if common.is_file():
        text, changed = update_readme_common(common.read_text(encoding="utf-8"), compile_sdk, target_sdk, new_name, count + 1)
        if changed:
            common.write_text(text, encoding="utf-8")
            steps.append({"name": "readme common.json", "ok": True, "detail": "updated " + ", ".join(changed)})
            print("[PASS] readme common.json: " + ", ".join(changed), flush=True)
    ok = True
    # The level the named template keys spell out: the target when it changes, otherwise the compile level.
    old_level, new_level = (entry["targetSdk"], target_sdk) if target_sdk is not None else (entry["compileSdk"], compile_sdk)
    if readme_keys and new_level is not None and old_level != new_level:
        # The keys are named by the caller, so a run that changes nothing is a wrong key, not a no-op.
        refreshed = refresh_readme_keys(repo, readme_keys, old_level, new_level)
        detail = ", ".join(f"{name} x{count}" for name, count in refreshed.items()) or "nothing to change"
        ok = bool(refreshed)
        steps.append({"name": "readme templates", "ok": ok, "detail": f"{old_level} -> {new_level} in {', '.join(readme_keys)}: {detail}"})
        print(("[PASS] " if ok else "[FAIL] ") + "readme templates: " + detail, flush=True)
    for relative, old_text, new_text in replacements:
        target_file = repo / relative
        text = target_file.read_text(encoding="utf-8") if target_file.is_file() else ""
        ok = ok and text.count(old_text) == 1
        if text.count(old_text) == 1:
            target_file.write_text(text.replace(old_text, new_text), encoding="utf-8")
        steps.append({"name": "replace " + relative, "ok": text.count(old_text) == 1,
                      "detail": f"{old_text!r} -> {new_text!r}" + ("" if text.count(old_text) == 1 else f" ({text.count(old_text)} occurrences)")})
        print(("[PASS] " if text.count(old_text) == 1 else "[FAIL] ") + "replace " + relative, flush=True)
    stale = stale_sdk_mentions(repo)
    if stale:
        steps.append({"name": "readme stale SDK mentions", "ok": True, "detail": "; ".join(stale)[:400]})
        print("[WARN] README templates still mention the previous SDK: " + "; ".join(stale), flush=True)
    if ok and (repo / ".python" / "generate_markdown.py").is_file():
        ok = step("generate_markdown", [sys.executable, ".python/generate_markdown.py"]) \
            and step("generate_markdown --check", [sys.executable, ".python/generate_markdown.py", "--check"])
    if ok and (repo / ".python" / "check_repository.py").is_file():
        ok = step("check_repository --pending-commit", [sys.executable, ".python/check_repository.py", "--pending-commit"])
    if ok and gradle_tasks:
        wrapper = repo / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
        ok = step("gradle " + " ".join(gradle_tasks), [str(wrapper), "--offline", "--console=plain", *gradle_tasks])
    if ok and serial:
        ok = device_check(repo, adb, serial, sdk_root, steps, instrument_args)
    ok = step("git diff --check", ["git", "diff", "--check"]) and ok
    result = {"repo": entry_name, "versionName": new_name, "versionBuild": count + 1,
              "compileSdk": updates.get("COMPILE_SDK_VERSION", props.get("COMPILE_SDK_VERSION")),
              "targetSdk": updates.get("TARGET_SDK_VERSION", props.get("TARGET_SDK_VERSION")),
              "steps": steps, "committed": None, "finished": utc_now()}
    if ok and commit:
        changed = [line[3:].strip() for line in run(["git", "status", "--short"], repo)[1].splitlines() if line.strip()]
        allowed = BUMP_FILE_PREFIXES + tuple(relative for relative, _, _ in replacements)
        unexpected = [f for f in changed if not f.startswith(allowed)]
        if unexpected:
            steps.append({"name": "commit", "ok": False, "detail": "unexpected files: " + ", ".join(unexpected)})
            ok = False
        else:
            run(["git", "add", "--", *changed], repo)
            message = BUMP_MESSAGE if compile_sdk is not None and target_sdk is not None \
                else (BUMP_MESSAGE_COMPILE_ONLY if target_sdk is None else BUMP_MESSAGE_TARGET_ONLY)
            code, out, err = run(["git", "commit", "-q", "-m", message], repo)
            new_count = int(run(["git", "rev-list", "--count", "HEAD"], repo)[1].strip())
            committed = code == 0 and new_count == count + 1
            steps.append({"name": "commit", "ok": committed, "detail": (out + err).strip()[-300:] or f"{len(changed)} files, build {new_count}"})
            print(("[PASS] " if committed else "[FAIL] ") + "commit", flush=True)
            ok = committed
            if committed:
                result["committed"] = run(["git", "log", "-1", "--format=%h"], repo)[1].strip()
                if (repo / ".python" / "check_repository.py").is_file():
                    ok = step("check_repository", [sys.executable, ".python/check_repository.py"])
    result["ok"] = ok
    if log_path:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        history = json.loads(log_path.read_text(encoding="utf-8")) if log_path.is_file() else []
        history.append(result)
        log_path.write_text(json.dumps(history, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{entry_name}: {'DONE' if ok else 'FAILED'}" + (f", committed {result['committed']}" if result["committed"] else ""), flush=True)
    return 0 if ok else 1


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def main():
    # Windows consoles default to the ANSI code page; notes and README warnings carry the ten changelog languages.
    for stream in (sys.stdout, sys.stderr):
        if (stream.encoding or "").lower().replace("-", "") != "utf8":
            stream.reconfigure(encoding="utf-8")
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
    bump_parser = sub.add_parser("bump", help="SDK values + version + changelogs + generated docs + checks + build + device + commit for ONE clean repository")
    bump_parser.add_argument("repo", help="Repository name without the AutoJs6-Plugin- prefix")
    bump_parser.add_argument("--compile", type=int)
    bump_parser.add_argument("--target", type=int)
    bump_parser.add_argument("--version-name", help="Explicit new VERSION_NAME (default: next patch version)")
    bump_parser.add_argument("--note-file", type=Path, help="JSON object language -> release-note line (default: the built-in no-exposure wording)")
    bump_parser.add_argument("--readme-keys", nargs="*", default=[], metavar="KEY",
                             help="README template keys (.readme/lang_*.json) whose prose spells out the old target level; the number is rewritten")
    bump_parser.add_argument("--instrument-arg", nargs=2, action="append", default=[], metavar=("KEY", "VALUE"),
                             help="Extra `-e KEY VALUE` for am instrument, e.g. notClass <test pinned to another environment>")
    bump_parser.add_argument("--replace", nargs=3, action="append", default=[], metavar=("PATH", "OLD", "NEW"),
                             help="Repository file that mirrors the SDK level (e.g. a lock file): replace OLD (exactly once) with NEW")
    bump_parser.add_argument("--replace-json", type=Path, help="JSON list of [PATH, OLD, NEW] triples (for non-ASCII texts the shell would mangle)")
    bump_parser.add_argument("--gradle", nargs="*", default=["assembleDebug", "testDebugUnitTest", "assembleDebugAndroidTest"])
    bump_parser.add_argument("--adb", default="adb")
    bump_parser.add_argument("--serial", help="Install the debug APK there and run its androidTest APK (D21: an AVD)")
    bump_parser.add_argument("--sdk-root", type=Path, help="Android SDK root for aapt2 (defaults to ANDROID_HOME / ANDROID_SDK_ROOT)")
    bump_parser.add_argument("--commit", action="store_true", help="Commit with the fixed Conventional Commits message when every step passed")
    bump_parser.add_argument("--log", type=Path, help="JSON list to append the per-repository result to")
    args = parser.parse_args()
    if args.action == "bump":
        if args.compile is None and args.target is None:
            parser.error("bump needs --compile and / or --target")
        if args.note_file:
            notes = json.loads(args.note_file.read_text(encoding="utf-8"))
        elif args.compile == 37 and args.target == 37:
            notes = BUMP_NOTES
        elif args.compile == 37 and args.target is None:
            notes = BUMP_NOTES_COMPILE_ONLY
        elif args.compile is None and args.target == 37:
            notes = BUMP_NOTES_TARGET_ONLY
        else:
            parser.error("the built-in release notes describe compile 37 + target 37, compile 37 alone or target 37 alone; pass --note-file otherwise")
        return bump(args.repo, args.compile, args.target, args.version_name, notes, args.gradle, args.commit, args.adb, args.serial,
                    args.sdk_root, args.log, args.projects, args.readme_keys, [tuple(pair) for pair in args.instrument_arg],
                    [tuple(triple) for triple in args.replace]
                    + ([tuple(t) for t in json.loads(args.replace_json.read_text(encoding="utf-8"))] if args.replace_json else []))
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
