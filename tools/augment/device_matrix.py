"""The device matrix of ROADMAP P5.1 on one device: every item in turn, one line per item (pass / fail and key numbers).

Usage:
  py -3.12 -I -u tools/augment/device_matrix.py -s SERIAL --out DIR [--items ITEM ...] [--cases DIR ...]
      [--backup DIR] [--install APK] [--test-apk APK] [--restore DIR]

--backup DIR pulls the installed host and test packages to DIR and records their md5 and the stored state of the
switch of the new script module layer (absent, true or false); --install / --test-apk then install the build under
test (adb install -r -d -t, data kept). --restore DIR puts back what --backup saved (packages, md5 check, switch state)
and removes the bench directory; it runs last, after the items.

Items, in this order (default: all of them):
  default       removes the stored switch so the build's default applies and checks what scripts get: the declared
                value (on since P5.1), and the new layer itself (base64.encode() without arguments is a TypeError of
                the new layer's argument check, D10; the old module reports another error)
  eval-default  every case file (default app/build/script-modules-device, one script per file, device_eval.py
                --strict-errors) in the default state: agree / differ / informational, files without all results
  eval-off      the same files with the switch off and without --strict-errors, so a difference is a value that
                differs from the new layer (two failures differing only in the error name are D10, not counted);
                every difference goes to DIR/eval-off-differences.txt for the comparison with appendix E. The cases of
                OFF_SKIPPED are left out (side effects of the old modules recorded in appendix E), and "extend
                JavaScript built-in objects" is off for the run (appendix E P3.6: with it on, Numberx / jsox cases hang
                the old modules), then restored
  surface       surface snapshots of both states (--extend-builtins on --roots Array.prototype Number.prototype
                --global-classes) and their comparison (surface_compare.py --allow-length, DIR/surface-compare.txt);
                differences of KNOWN_SURFACE are counted apart from the others
  bootstrap     device_bootstrap_check.py --probe in both states (the default state compared with the off state) and
                --smoke coroutine listener suspend images automator in the default state
  instrumented  the instrumented tests of tools/rhino/device_rhino_check.py and EXTRA_CLASSES (needs the test APK of
                the same build, :app:assembleAppDebugAndroidTest)

The tools write their temporary scripts under tools/augment/__pycache__, so to run several devices at the same time
run each from its own copy of tools/augment (and tools/rhino). Logs of every step are kept in DIR. The switch is left
not stored (the default) after the items unless --restore puts back another state.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tools/rhino"))

import device_bench as db  # noqa: E402

PACKAGE = db.PACKAGE
TEST_PACKAGE = "org.autojs.autojs6.test"
EXTEND_BUILTINS_KEY = "key_$_extending_js_build_in_objects"
MARK = "AJ6MATRIX"
ITEMS = ["default", "eval-default", "eval-off", "surface", "bootstrap", "instrumented"]

# Cases left out of the off state: the old modules act on the device (appendix E "P3.6 真机关态补登" and P3.4 / P3.2).
OFF_SKIPPED = [
    (re.compile(r"setPointerLocation\(\s*[^)\s]"), "turns on the system pointer location display (appendix E P3.6)"),
    (re.compile(r"sendBroadcast\('no_such_form'\)"), "crashes the host (appendix E P3.4)"),
    (re.compile(r"^(dialogs\.)?(rawInput|input|prompt|alert|confirm|select|singleChoice|multiChoice)\("),
     "the old module may show the dialog and block the script (appendix E P3.2 / P3.6)"),
]

# Surface differences of the two states recorded before (appendix A "P4.1", appendix E P3.0 / P3.2 / P3.4), matched
# against the path without its leading "global." / "globalThis." parts.
KNOWN_SURFACE = [
    (re.compile(r"^\$?auto\.stats\b"), "auto.stats timing values"),
    (re.compile(r"^@lookup\.Image\b"), "@lookup.Image (appendix E P3.0)"),
    (re.compile(r"^\$?files\.batchRename\.apply\b"), "files.batchRename.apply (appendix E P3.4)"),
    (re.compile(r"^(clearConsole|err|launchConsole|openConsole|showConsole)(\.name)?$"), "console global function names (appendix E P3.2)"),
]

EXTRA_CLASSES = [
    "org.autojs.autojs.runtime.api.augment.global.CoordinateScaleDeviceTest",
    "org.autojs.autojs.runtime.api.augment.device.DevicePageSizeInstrumentationTest",
]


def adb(serial, *args, check=False, timeout=900):
    done = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if check and done.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {(done.stderr or done.stdout).strip()[:300]}")
    return done


def tool(name, *args, log, timeout=3600):
    """Runs tools/augment/NAME with [args], writes its output to [log] and returns (exit code, output)."""
    command = [sys.executable, "-I", "-u", str(HERE / name), *map(str, args)]
    try:
        done = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        out, code = done.stdout + done.stderr, done.returncode
    except subprocess.TimeoutExpired as e:
        out, code = (e.stdout or b"").decode("utf-8", "replace") if isinstance(e.stdout, bytes) else (e.stdout or ""), -1
        out += f"\n(timed out after {timeout} s)"
    log.write_text(out, encoding="utf-8")
    return code, out


def report(item, ok, detail):
    print(f"{'PASS' if ok else 'FAIL'} {item}: {detail}", flush=True)
    return ok


def run_js(serial, name, lines, wait=30):
    """Runs a small script and returns its console lines that carry MARK (without the mark)."""
    local = HERE / "__pycache__" / name
    local.parent.mkdir(exist_ok=True)
    local.write_text("var pref = org.autojs.autojs.core.pref.Pref.INSTANCE;\n" + "\n".join(lines) + "\n", encoding="utf-8")
    adb(serial, "shell", "mkdir", "-p", db.DEVICE_DIR, check=True)
    adb(serial, "push", str(local), f"{db.DEVICE_DIR}/{name}", check=True)
    out = []
    for line in db.run_script(serial, f"{db.DEVICE_DIR}/{name}", wait):
        start = line.find(MARK + " ")
        if start >= 0:
            out.append(line[start + len(MARK) + 1:])
    return out


def switch_state(serial):
    """The stored state of the switch: "absent", "true" or "false"."""
    key = db.SWITCH_KEY
    got = run_js(serial, "aj6_matrix_state.js", [f"console.log('{MARK} ' + (pref.containsKey('{key}') ? String(pref.getBoolean('{key}', false)) : 'absent'));"])
    if not got:
        raise RuntimeError("no switch state from the device")
    return got[0].strip()


def set_switch_state(serial, state):
    """Stores [state] ("absent" removes the key) with commit, through the default preferences of any build."""
    key = db.SWITCH_KEY
    edit = f"edit.remove('{key}')" if state == "absent" else f"edit.putBoolean('{key}', {state})"
    got = run_js(serial, "aj6_matrix_set.js", [
        "var sp = android.preference.PreferenceManager.getDefaultSharedPreferences(context);",
        f"var edit = sp.edit(); {edit}; edit.commit();",
        f"console.log('{MARK} ' + (sp.contains('{key}') ? String(sp.getBoolean('{key}', false)) : 'absent'));",
    ])
    if not got or got[0].strip() != state:
        raise RuntimeError(f"the switch state did not become {state}: {got}")


def package_paths(serial, package):
    out = adb(serial, "shell", "pm", "path", package).stdout
    return [line.split(":", 1)[1].strip() for line in out.splitlines() if line.startswith("package:")]


def md5_of(serial, package):
    paths = package_paths(serial, package)
    if not paths:
        return "not installed"
    return " ".join(adb(serial, "shell", "md5sum", p).stdout.split()[0][:8] for p in sorted(paths))


def backup(serial, folder):
    folder.mkdir(parents=True, exist_ok=True)
    state = {"switch": switch_state(serial)}
    for package in (PACKAGE, TEST_PACKAGE):
        paths = sorted(package_paths(serial, package))
        files = []
        for index, path in enumerate(paths):
            local = folder / f"{package}-{index}-{pathlib.PurePosixPath(path).name}"
            adb(serial, "pull", path, str(local), check=True)
            files.append(local.name)
        state[package] = {"md5": md5_of(serial, package), "files": files}
    (folder / "state.json").write_text(json.dumps(state, indent=1), encoding="utf-8")
    return report("backup", True, f"host {state[PACKAGE]['md5']}, test {state[TEST_PACKAGE]['md5']}, switch {state['switch']}")


def install(serial, apk):
    adb(serial, "install", "-r", "-d", "-t", str(apk), check=True)


def restore(serial, folder):
    state = json.loads((folder / "state.json").read_text(encoding="utf-8"))
    for package in (PACKAGE, TEST_PACKAGE):
        files = [folder / f for f in state[package]["files"]]
        if not files:
            if package_paths(serial, package):
                adb(serial, "uninstall", package)
            continue
        if len(files) == 1:
            install(serial, files[0])
        else:
            adb(serial, "install-multiple", "-r", "-d", "-t", *map(str, files), check=True)
    set_switch_state(serial, state["switch"])
    adb(serial, "shell", "rm", "-rf", db.DEVICE_DIR)
    host, test = md5_of(serial, PACKAGE), md5_of(serial, TEST_PACKAGE)
    ok = host == state[PACKAGE]["md5"] and test == state[TEST_PACKAGE]["md5"]
    return report("restore", ok, f"host {host} (was {state[PACKAGE]['md5']}), test {test} (was {state[TEST_PACKAGE]['md5']}), switch {state['switch']}")


def item_default(serial, out):
    set_switch_state(serial, "absent")
    got = run_js(serial, "aj6_matrix_default.js", [
        "var declared = String(org.autojs.autojs.core.pref.keys.DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.get());",
        "var marker; try { base64.encode(); marker = 'no error'; } catch (e) { marker = String(e.name); }",
        f"console.log('{MARK} ' + declared + ' ' + pref.containsKey('{db.SWITCH_KEY}') + ' ' + marker);",
    ])
    if not got:
        return report("default", False, "no output from the device")
    declared, stored, marker = got[0].split()[:3]
    return report("default", declared == "true" and stored == "false" and marker == "TypeError",
                  f"declared {declared}, stored {stored}, base64.encode() {marker}")


EVAL_RE = re.compile(r"(\d+) agree with the JVM Kotlin value, (\d+) differ, (\d+) informational")


def case_files(paths):
    files = []
    for path in paths:
        files += sorted(path.glob("*.json")) if path.is_dir() else [path]
    return files


def eval_files(serial, files, out, label, extra):
    """Runs every case file in its own script and returns the totals, the files still without all results, the FAIL
    entries as (file, expression, jvm, device) and the cases that ended their script as (file, expression).

    A case the old modules answer with an error no script can catch (appendix E P3.6: wait.async, util.morseCode('#'),
    an InvocationTargetException of the old Kotlin) ends the script, and the cases after it get no result; they run
    again in a new script without it, until every case has a result or a script brings none.
    """
    folder = out / label
    folder.mkdir(parents=True, exist_ok=True)
    totals = [0, 0, 0]
    short, entries, enders = [], [], []
    for file in files:
        cases = json.loads(file.read_text(encoding="utf-8"))
        part, attempt, empty = file, 0, 0
        while True:
            log = folder / (f"{file.stem}.txt" if attempt == 0 else f"{file.stem}-rest{attempt}.txt")
            _, text = tool("device_eval.py", "-s", serial, "--cases", part, *extra, log=log, timeout=900)
            m = EVAL_RE.search(text)
            if not m:
                short.append(f"{file.stem} (no summary)")
                break
            missing = set(re.findall(r"^(?:FAIL|INFO) [^:]+: (.*)\n(?:\s+jvm-kt=.*\n)?\s+device=<no result>$", text, re.M))
            for fail in re.finditer(r"^FAIL [^:]+: (.*)\n\s+jvm-kt=(.*)\n\s+device=(.*)$", text, re.M):
                if fail.group(3) != "<no result>":
                    entries.append((file.stem, fail.group(1), fail.group(2), fail.group(3)))
            first = next((i for i, c in enumerate(cases) if c["expression"] in missing), None)
            answered = cases if first is None else cases[:first]
            for c in answered:
                if c["kt"] is None:
                    totals[2] += 1
                elif not any(e[0] == file.stem and e[1] == c["expression"] for e in entries):
                    totals[0] += 1
                else:
                    totals[1] += 1
            if first is None:
                break
            # A script without any result three times in a row: the host is not answering, stop this file.
            empty = empty + 1 if first == 0 else 0
            if empty >= 3:
                short.append(f"{file.stem} ({len(cases)} without a result)")
                break
            enders.append((file.stem, cases[first]["expression"]))
            cases = cases[first + 1:]
            if not cases:
                break
            attempt += 1
            part = folder / f"{file.stem}-rest{attempt}.json"
            part.write_text(json.dumps(cases, ensure_ascii=True, indent=1), encoding="utf-8")
    return totals, short, entries, enders


def item_eval_default(serial, files, out):
    set_switch_state(serial, "absent")
    (agree, differ, info), short, _, enders = eval_files(serial, files, out, "eval-default", ["--strict-errors"])
    return report("eval-default", differ == 0 and not short and not enders,
                  f"{len(files)} files, agree {agree}, differ {differ}, info {info}" + (f", short {' '.join(short)}" if short else "")
                  + (f", ended the script: {'; '.join(e for _, e in enders)}" if enders else ""))


def off_cases(files, out):
    """Copies of the case files without OFF_SKIPPED; returns the copies and the skipped cases."""
    folder = out / "cases-off"
    folder.mkdir(parents=True, exist_ok=True)
    copies, skipped = [], []
    for file in files:
        kept = []
        for entry in json.loads(file.read_text(encoding="utf-8")):
            reason = next((r for p, r in OFF_SKIPPED if p.search(entry["expression"])), None)
            if reason:
                skipped.append((file.stem, entry["expression"], reason))
            else:
                kept.append(entry)
        if kept:
            copy = folder / file.name
            copy.write_text(json.dumps(kept, ensure_ascii=True, indent=1), encoding="utf-8")
            copies.append(copy)
    return copies, skipped


def set_pref_boolean(serial, key, value):
    got = run_js(serial, "aj6_matrix_pref.js", [
        f"var before = {db.pref_value_js(key)};",
        f"pref.putBooleanSync('{key}', {'true' if value else 'false'});",
        f"console.log('{MARK} ' + before);",
    ])
    if not got:
        raise RuntimeError(f"no output setting {key}")
    return got[0].strip() == "true"


def item_eval_off(serial, files, out):
    copies, skipped = off_cases(files, out)
    extend_before = set_pref_boolean(serial, EXTEND_BUILTINS_KEY, False)
    set_switch_state(serial, "false")
    try:
        (agree, differ, info), short, entries, enders = eval_files(serial, copies, out, "eval-off", [])
    finally:
        set_switch_state(serial, "absent")
        if extend_before:
            set_pref_boolean(serial, EXTEND_BUILTINS_KEY, True)
    with (out / "eval-off-differences.txt").open("w", encoding="utf-8") as f:
        for test, expression, jvm, device in entries:
            f.write(f"{test}\t{expression}\n    new {jvm}\n    off {device}\n")
        for test, expression in enders:
            f.write(f"ENDED {test}\t{expression}\n")
        for test, expression, reason in skipped:
            f.write(f"SKIPPED {test}\t{expression}\t{reason}\n")
    return report("eval-off", not short, f"{len(copies)} files, agree {agree}, differ {differ} (DIR/eval-off-differences.txt), "
                  f"info {info}, ended the script {len(enders)}, skipped {len(skipped)}" + (f", short {' '.join(short)}" if short else ""))


def surface_path(line):
    m = re.match(r"^(?:only after|only before|changed)\s+(\S+)", line)
    return m.group(1) if m else None


def item_surface(serial, out):
    folder = out / "surface"
    common = ["--out", folder, "--extend-builtins", "on", "--roots", "Array.prototype", "Number.prototype", "--global-classes"]
    set_switch_state(serial, "absent")
    for tag, extra in (("off", ["--new-modules", "off"]), ("default", [])):
        code, text = tool("device_surface_snapshot.py", "-s", serial, *common, "--tag", tag, *extra, log=out / f"surface-{tag}.log", timeout=1800)
        if code != 0:
            return report("surface", False, f"snapshot {tag} failed (exit {code})")
    set_switch_state(serial, "absent")
    off = next(folder.glob("surface-*-off.txt"))
    default = next(folder.glob("surface-*-default.txt"))
    _, text = tool("surface_compare.py", off, default, "--allow-length", log=out / "surface-compare.txt")
    known, other = {}, []
    for line in text.splitlines():
        path = surface_path(line)
        if path is None:
            continue
        bare = re.sub(r"^(?:(?:global|globalThis)\.)+", "", path)
        reason = next((r for p, r in KNOWN_SURFACE if p.search(bare)), None)
        if reason:
            known[reason] = known.get(reason, 0) + 1
        else:
            other.append(path)
    summary = text.strip().splitlines()[-1] if text.strip() else "no output"
    (out / "surface-other.txt").write_text("\n".join(other) + "\n", encoding="utf-8")
    return report("surface", not other, f"{summary}; known {sum(known.values())} ({', '.join(f'{k} {v}' for k, v in known.items())}), "
                  f"other {len(other)}")


def item_bootstrap(serial, out):
    saved = out / "bootstrap-off.json"
    set_switch_state(serial, "false")
    try:
        tool("device_bootstrap_check.py", "-s", serial, "--probe", "--save", saved, log=out / "bootstrap-probe-off.txt")
    finally:
        set_switch_state(serial, "absent")
    _, probe = tool("device_bootstrap_check.py", "-s", serial, "--probe", "--compare", saved, log=out / "bootstrap-probe-default.txt")
    m = re.search(r"compare: (\d+) same, (\d+) different", probe)
    code, smoke = tool("device_bootstrap_check.py", "-s", serial, "--smoke", "coroutine", "listener", "suspend", "images", "automator",
                       log=out / "bootstrap-smoke.txt")
    lines = [l for l in smoke.splitlines() if re.match(r"^(coroutine|listener|suspend|images|automator)", l)]
    ok = code == 0 and m is not None
    return report("bootstrap", ok, (f"probe {m.group(1)} same / {m.group(2)} different" if m else "probe: no comparison")
                  + f"; smoke exit {code}, {len(lines)} lines")


def item_instrumented(serial, out):
    import device_rhino_check
    classes = device_rhino_check.DEFAULT_CLASSES + EXTRA_CLASSES
    command = [sys.executable, "-I", "-u", str(ROOT / "tools/rhino/device_rhino_check.py"), "-s", serial]
    for name in classes:
        command += ["--class", name]
    done = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=5400)
    text = done.stdout + done.stderr
    (out / "instrumented.txt").write_text(text, encoding="utf-8")
    m = re.search(r"summary \S+: (.*)", text)
    return report("instrumented", done.returncode == 0, m.group(1) if m else "no summary")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--items", nargs="*", choices=ITEMS, default=ITEMS)
    parser.add_argument("--cases", nargs="*", type=pathlib.Path, default=[ROOT / "app/build/script-modules-device"])
    parser.add_argument("--backup", type=pathlib.Path)
    parser.add_argument("--install", type=pathlib.Path)
    parser.add_argument("--test-apk", type=pathlib.Path)
    parser.add_argument("--restore", type=pathlib.Path)
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    s = args.serial
    args.out.mkdir(parents=True, exist_ok=True)
    ok = True
    if args.backup:
        ok &= backup(s, args.backup)
    for apk in filter(None, (args.install, args.test_apk)):
        install(s, apk)
    if args.install or args.test_apk:
        print(f"installed: host {md5_of(s, PACKAGE)}, test {md5_of(s, TEST_PACKAGE)}", flush=True)
    files = case_files(args.cases)
    for item in args.items:
        started = time.time()
        try:
            if item == "default":
                result = item_default(s, args.out)
            elif item == "eval-default":
                result = item_eval_default(s, files, args.out)
            elif item == "eval-off":
                result = item_eval_off(s, files, args.out)
            elif item == "surface":
                result = item_surface(s, args.out)
            elif item == "bootstrap":
                result = item_bootstrap(s, args.out)
            else:
                result = item_instrumented(s, args.out)
        except Exception as e:  # one item failing must not keep the others (and the restore) from running
            result = report(item, False, f"{type(e).__name__}: {e}")
        ok &= result
        print(f"     ({item} took {time.time() - started:.0f} s)", flush=True)
    if args.restore:
        ok &= restore(s, args.restore)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
