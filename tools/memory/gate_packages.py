"""Back up, swap and restore the packages a gate run replaces on a device (docs/memory-phase2/ROADMAP.md: install the
gate builds, put the original packages back afterwards and compare md5).

  backup  -s SERIAL --dir DIR PACKAGE...  pull the installed APKs (all splits) of each package into DIR, with md5,
                                          versionCode and lastUpdateTime in DIR/packages.json; absent packages are recorded
  install -s SERIAL APK...                adb install -r -t -d each APK, then compare the installed base.apk with the file
  restore -s SERIAL --dir DIR             reinstall every backed-up package (install-multiple for splits), uninstall the
                                          ones that were absent before, and compare the device md5 with the backup

Prints one line per package: PASS / FAIL, versionCode and md5 prefixes. A signature mismatch stops with a message;
only packages named in --allow-uninstall are uninstalled first (an uninstall drops the package's data).
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "sdk37"))
import physical_device  # noqa: E402


def md5_file(path):
    digest = hashlib.md5()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def installed(device, package):
    """{versionCode, lastUpdateTime, files: {name: (path, md5)}} of an installed package, or None."""
    paths = [line.split(":", 1)[1].strip() for line in device.shell("pm", "path", package)["stdout"].splitlines()
             if line.startswith("package:")]
    if not paths:
        return None
    dump = device.shell("dumpsys", "package", package)["stdout"]
    version = re.search(r"versionCode=(\d+)", dump)
    updated = re.search(r"lastUpdateTime=([^\r\n]+)", dump)
    files = {}
    for path in paths:
        found = re.match(r"([0-9a-f]{32})\s", device.shell("md5sum", path)["stdout"])
        files[Path(path).name] = (path, found[1] if found else None)
    return {"versionCode": int(version[1]) if version else None, "lastUpdateTime": updated[1].strip() if updated else None,
            "files": files}


def base_md5(state):
    return ((state or {}).get("files") or {}).get("base.apk", (None, None))[1]


def short(md5):
    return (md5 or "-")[:8]


def apk_package(apk):
    """Package name of an APK through aapt2 from the newest build-tools of the SDK."""
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    tools = sorted((sdk / "build-tools").glob("*/aapt2*"), key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.parent.name)])
    if not tools:
        raise RuntimeError("aapt2 not found under ANDROID_HOME/build-tools")
    result = physical_device.run([str(tools[-1]), "dump", "packagename", str(apk)], timeout=60)
    name = result["stdout"].strip()
    if result["exitCode"] != 0 or not re.fullmatch(r"[A-Za-z0-9_.]+", name):
        raise RuntimeError(f"aapt2 cannot read {apk}: {(result['stdout'] + result['stderr']).strip()[:200]}")
    return name


def install(device, files, allow_uninstall, package):
    command = ("install-multiple" if len(files) > 1 else "install", "-r", "-t", "-d", *map(str, files))
    result = device.call(*command, timeout=900)
    text = (result["stdout"] + result["stderr"]).strip()
    if "Success" in text:
        return None
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in text and package in allow_uninstall:
        device.call("uninstall", package, timeout=120)
        result = device.call(*command, timeout=900)
        text = (result["stdout"] + result["stderr"]).strip()
        if "Success" in text:
            return None
    return text.splitlines()[-1][:200] if text else f"exit {result['exitCode']}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=("backup", "install", "restore"))
    parser.add_argument("items", nargs="*", help="backup: package names; install: APK files")
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--dir", type=Path, help="Backup directory (backup / restore)")
    parser.add_argument("--allow-uninstall", default="", help="Comma list of packages that may be uninstalled on a signature mismatch")
    args = parser.parse_args()
    allow_uninstall = {item.strip() for item in args.allow_uninstall.split(",") if item.strip()}
    device = physical_device.Device(args.adb, args.serial)
    device.require_online()
    failed = 0

    if args.action == "backup":
        if not args.dir or not args.items:
            parser.error("backup needs --dir and package names")
        args.dir.mkdir(parents=True, exist_ok=True)
        manifest = {"serial": args.serial, "taken": physical_device.utc_now(), "packages": {}}
        for package in map(physical_device.package_name, args.items):
            state = installed(device, package)
            if state is None:
                manifest["packages"][package] = None
                print(f"PASS backup {package}: not installed")
                continue
            target = args.dir / package
            target.mkdir(exist_ok=True)
            ok = True
            for name, (path, md5) in state["files"].items():
                pulled = device.call("pull", path, str(target / name), timeout=900)
                ok = ok and pulled["exitCode"] == 0 and md5 is not None and md5_file(target / name) == md5
            manifest["packages"][package] = {**state, "files": {name: md5 for name, (_, md5) in state["files"].items()}}
            failed += not ok
            print(f"{'PASS' if ok else 'FAIL'} backup {package}: versionCode {state['versionCode']}, base {short(base_md5(state))}, "
                  f"{len(state['files'])} file(s)")
        physical_device.write_json(args.dir / "packages.json", manifest)

    elif args.action == "install":
        for apk in map(Path, args.items):
            package = apk_package(apk)
            error = install(device, [apk], allow_uninstall, package)
            state = installed(device, package)
            ok = error is None and base_md5(state) == md5_file(apk)
            failed += not ok
            print(f"{'PASS' if ok else 'FAIL'} install {package}: versionCode {(state or {}).get('versionCode')}, "
                  f"base {short(base_md5(state))}" + (f" ({error})" if error else ""))

    else:
        if not args.dir:
            parser.error("restore needs --dir")
        manifest = json.loads((args.dir / "packages.json").read_text(encoding="utf-8"))
        if manifest.get("serial") != args.serial:
            parser.error(f"{args.dir} holds the backup of {manifest.get('serial')}")
        for package, saved in manifest["packages"].items():
            if saved is None:
                if installed(device, package) is not None:
                    device.call("uninstall", package, timeout=120)
                ok = installed(device, package) is None
                failed += not ok
                print(f"{'PASS' if ok else 'FAIL'} restore {package}: absent as before")
                continue
            names = sorted(saved["files"], key=lambda name: name != "base.apk")
            error = install(device, [args.dir / package / name for name in names], allow_uninstall, package)
            state = installed(device, package)
            now = {name: md5 for name, (_, md5) in ((state or {}).get("files") or {}).items()}
            ok = error is None and now == saved["files"] and (state or {}).get("versionCode") == saved["versionCode"]
            failed += not ok
            print(f"{'PASS' if ok else 'FAIL'} restore {package}: versionCode {(state or {}).get('versionCode')} "
                  f"(backup {saved['versionCode']}), base {short(now.get('base.apk'))} (backup {short(saved['files'].get('base.apk'))})"
                  + (f" ({error})" if error else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
