"""Save and put back an app's package and databases around a round trip on a real device (ROADMAP P4.1).

roundtrip.py and room_pages.py leave marker rows, future columns and tables, and renamed files in databases/, and the
build under test replaces the installed one. On a device the maintainer keeps using, both go back afterwards:

  save     Pulls every APK of each --package (base and splits) with its md5, force-stops it and stores databases/ as a tar
           (run-as, so the installed build must be debuggable). Writes <dir>/state.json.
  restore  For each saved package: force-stops it, replaces databases/ with the saved tar while the build under test is
           still installed and stopped, then installs the saved APKs with install -r -d (install-multiple for splits) and
           checks the md5 of the installed files and the restored file list. Putting the files back before the old build
           returns matters: an old build without the fix crashes on the databases the new build has upgraded (ROADMAP 1.4).
           A package that was not installed at save time is uninstalled.

The tar and the APKs stay on the PC (no database content is printed, R4); device files under /data/local/tmp are removed.

Usage: py -3.12 -I tools/db-downgrade/device_state.py save|restore -s SERIAL --dir <folder> --package org.autojs.autojs6 [--package ...]
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tarfile

DEVICE_TMP = "/data/local/tmp/aj6-db-downgrade-state"


class StateError(Exception):
    pass


def adb(serial, *args, timeout=120, binary=False):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, timeout=timeout)
    if binary:
        return result.returncode, result.stdout, result.stderr
    return (result.returncode, result.stdout.decode("utf-8", "replace").replace("\r\n", "\n"),
            result.stderr.decode("utf-8", "replace").replace("\r\n", "\n"))


def md5(path):
    return hashlib.md5(Path(path).read_bytes()).hexdigest()


def apk_paths(serial, package):
    _, out, _ = adb(serial, "shell", f"pm path {package}")
    return [line.split(":", 1)[1].strip() for line in out.splitlines() if line.startswith("package:")]


def run_as(serial, package, command, timeout=120):
    return adb(serial, "shell", f"run-as {package} sh -c '{command}'", timeout=timeout)


def save(args):
    folder = Path(args.dir)
    folder.mkdir(parents=True, exist_ok=True)
    state = {"serial": args.serial, "packages": {}}
    for package in args.package:
        paths = apk_paths(args.serial, package)
        if not paths:
            state["packages"][package] = {"installed": False}
            print(f"{package}: not installed, will be uninstalled on restore")
            continue
        apks = []
        for remote in paths:
            local = folder / f"{package}-{Path(remote).name}"
            code, _, err = adb(args.serial, "pull", remote, str(local), timeout=300)
            if code != 0:
                raise StateError(f"Unable to pull {remote}: {err.strip()[:200]}")
            apks.append({"file": local.name, "md5": md5(local), "remote": Path(remote).name})
        adb(args.serial, "shell", f"am force-stop {package}")
        # A test APK has no databases/ (and may not be debuggable): only its APKs are kept.
        # zh-CN: 测试 APK 没有 databases/ (也可能不可调试): 只保存其 APK.
        code, out, _ = run_as(args.serial, package, "[ -d databases ] && echo yes")
        if code != 0 or out.strip() != "yes":
            state["packages"][package] = {"installed": True, "apks": apks, "databases": None, "files": []}
            print(f"{package}: {len(apks)} APK(s) ({', '.join(apk['md5'][:8] for apk in apks)}), no databases/")
            continue
        code, data, err = adb(args.serial, "exec-out", "run-as", package, "tar", "-cf", "-", "databases", timeout=300, binary=True)
        if code != 0 or not data:
            raise StateError(f"Unable to save databases/ of {package}: {err.decode('utf-8', 'replace').strip()[:200]}")
        archive = folder / f"{package}-databases.tar"
        archive.write_bytes(data)
        with tarfile.open(archive) as tar:
            names = sorted(member.name for member in tar.getmembers() if member.isfile())
        state["packages"][package] = {"installed": True, "apks": apks, "databases": archive.name, "files": names}
        print(f"{package}: {len(apks)} APK(s) ({', '.join(apk['md5'][:8] for apk in apks)}), databases/ {len(names)} file(s)")
    (folder / "state.json").write_text(json.dumps(state, indent=2), encoding="utf-8")
    return True


def device_files(serial, package):
    code, out, _ = run_as(serial, package, "ls -a databases 2>/dev/null")
    return sorted(f"databases/{line.strip()}" for line in out.splitlines() if code == 0 and line.strip() not in ("", ".", ".."))


def restore(args):
    folder = Path(args.dir)
    state = json.loads((folder / "state.json").read_text(encoding="utf-8"))
    ok = True
    for package in args.package:
        saved = state["packages"].get(package)
        if saved is None:
            raise StateError(f"{package} was not saved in {folder}")
        adb(args.serial, "shell", f"am force-stop {package}")
        if not saved["installed"]:
            code, out, _ = adb(args.serial, "uninstall", package, timeout=180)
            gone = not apk_paths(args.serial, package)
            ok &= gone
            print(f"{package}: {'uninstalled' if gone else 'still installed: ' + out.strip()[-120:]}")
            continue
        # databases/ first, while the build under test is installed and stopped (see the module docstring).
        # zh-CN: 先放回 databases/, 此时被测构建仍在且已停止 (见模块说明).
        restored = True
        if saved["databases"]:
            remote = f"{DEVICE_TMP}/{package}-databases.tar"
            adb(args.serial, "shell", f"mkdir -p {DEVICE_TMP} && chmod 777 {DEVICE_TMP}")
            code, _, err = adb(args.serial, "push", str(folder / saved["databases"]), remote, timeout=300)
            if code != 0:
                raise StateError(f"Unable to push the databases/ tar of {package}: {err.strip()[:200]}")
            adb(args.serial, "shell", f"chmod 644 {remote}")
            code, _, err = run_as(args.serial, package, f"rm -rf databases && tar -xf {remote}")
            adb(args.serial, "shell", f"rm -f {remote}")
            if code != 0:
                raise StateError(f"Unable to restore databases/ of {package}: {err.strip()[:200]}")
            restored = device_files(args.serial, package) == saved["files"]
        local = [str(folder / apk["file"]) for apk in saved["apks"]]
        command = ["install", "-r", "-d", *local] if len(local) == 1 else ["install-multiple", "-r", "-d", *local]
        code, out, err = adb(args.serial, *command, timeout=300)
        installed = code == 0 and "Success" in out
        remote_md5 = {}
        for path in apk_paths(args.serial, package):
            _, out_md5, _ = adb(args.serial, "shell", f"md5sum {path}")
            remote_md5[Path(path).name] = out_md5.split()[0] if out_md5.strip() else ""
        same = sorted(remote_md5.values()) == sorted(apk["md5"] for apk in saved["apks"])
        files_after = not saved["databases"] or device_files(args.serial, package) == saved["files"]
        # The reinstalled build may start at once (package replaced) and add journal files, so the list after it is only shown.
        # zh-CN: 重装后的构建可能随即启动 (包已替换) 并新增日志文件, 所以重装后的文件列表只作展示.
        ok &= restored and installed and same
        print(f"{package}: databases/ {'restored' if restored else 'DIFFERS'} ({len(saved['files'])} file(s)), "
              f"install {'Success' if installed else (out + err).strip()[-120:]}, md5 {'matches' if same else 'DIFFERS'} "
              f"({', '.join(value[:8] for value in remote_md5.values())}), files after install {'unchanged' if files_after else 'changed'}")
    adb(args.serial, "shell", f"rm -rf {DEVICE_TMP}")
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("command", choices=("save", "restore"))
    parser.add_argument("-s", "--serial", required=True)
    parser.add_argument("--dir", required=True, help="folder on the PC for the APKs, the tar files and state.json")
    parser.add_argument("--package", action="append", required=True)
    args = parser.parse_args()
    try:
        passed = save(args) if args.command == "save" else restore(args)
    except (StateError, subprocess.TimeoutExpired) as error:
        print(f"ERROR: {error}")
        return 2
    print("PASS" if passed else "FAIL")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
