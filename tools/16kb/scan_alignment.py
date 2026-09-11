"""16 KB page-size readiness scan for AutoJs6 and its sibling plugin repositories.

For each project (default: AutoJs6 plus every AutoJs6-Plugin-* directory next to it) the script
inspects the newest APK per variant directory under **/build/outputs/apk, every checked-in .so file,
and every .so inside AAR / JAR files in the repo. For each shared library it reports the ELF
PT_LOAD minimum alignment, the zip compression flag, the zip data offset alignment, and the
manifest extractNativeLibs / native-code values (via aapt2 when available).

zh-CN: 跨仓库巡检脚本 (roadmap: docs/dev/page-size-16kb-roadmap.md). 单仓库门禁由共享 Gradle 插件承担.

Usage:
  python tools/16kb/scan_alignment.py                      # scan all sibling projects
  python tools/16kb/scan_alignment.py --project D:/idea-projects/AutoJs6 --max-apks 2
  python tools/16kb/scan_alignment.py --apk path/to/app.apk  # single APK mode

Environment overrides: AUTOJS6_PROJECTS_ROOT (parent directory of the repos), ANDROID_HOME
(for aapt2), MAX_APKS (newest APK groups per project, default 5). Exit code is 1 when any
64-bit (arm64-v8a / x86_64) shared library fails the 16 KB checks.
"""
import argparse
import glob
import os
import struct
import subprocess
import sys
import time
import zipfile
from collections import defaultdict
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
PAGE = 16384
STRICT_ABIS = ("arm64-v8a", "x86_64")


def projects_root() -> Path:
    override = os.environ.get("AUTOJS6_PROJECTS_ROOT")
    return Path(override) if override else REPO_ROOT.parent


def sdk_root() -> Path | None:
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value and Path(value).is_dir():
            return Path(value)
    local_properties = REPO_ROOT / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith("sdk.dir="):
                raw = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                candidate = Path(raw)
                if candidate.is_dir():
                    return candidate
    return None


def find_aapt2() -> Path | None:
    sdk = sdk_root()
    if sdk is None:
        return None
    build_tools = sdk / "build-tools"
    if not build_tools.is_dir():
        return None
    exe = "aapt2.exe" if os.name == "nt" else "aapt2"
    candidates = sorted(
        (p for p in build_tools.iterdir() if (p / exe).is_file()),
        key=lambda p: [int(x) if x.isdigit() else x for x in p.name.replace("-", ".").split(".")],
    )
    return (candidates[-1] / exe) if candidates else None


AAPT2 = find_aapt2()


def default_project_dirs(root: Path):
    for p in sorted(root.iterdir()):
        if not p.is_dir():
            continue
        if p.name == "AutoJs6" or p.name.startswith("AutoJs6-Plugin-"):
            yield p


def elf_min_load_align(head: bytes):
    """Return (min PT_LOAD p_align, error) for the given ELF prefix (>= program header table)."""
    if len(head) < 64 or head[:4] != b"\x7fELF":
        return None, "not-elf"
    cls = head[4]
    end = "<" if head[5] == 1 else ">"
    aligns = []
    try:
        if cls == 2:
            e_phoff = struct.unpack_from(end + "Q", head, 0x20)[0]
            e_phentsize, e_phnum = struct.unpack_from(end + "HH", head, 0x36)
            for i in range(e_phnum):
                off = e_phoff + i * e_phentsize
                if struct.unpack_from(end + "I", head, off)[0] == 1:
                    aligns.append(struct.unpack_from(end + "Q", head, off + 48)[0])
        elif cls == 1:
            e_phoff = struct.unpack_from(end + "I", head, 0x1C)[0]
            e_phentsize, e_phnum = struct.unpack_from(end + "HH", head, 0x2A)
            for i in range(e_phnum):
                off = e_phoff + i * e_phentsize
                if struct.unpack_from(end + "I", head, off)[0] == 1:
                    aligns.append(struct.unpack_from(end + "I", head, off + 28)[0])
        else:
            return None, "bad-class"
    except struct.error:
        return None, "truncated-phdr"
    if not aligns:
        return None, "no-load"
    return min(aligns), None


def zip_data_offset(zf: zipfile.ZipFile, zi: zipfile.ZipInfo):
    fp = zf.fp
    fp.seek(zi.header_offset)
    hdr = fp.read(30)
    if len(hdr) < 30 or hdr[:4] != b"PK\x03\x04":
        return None
    fnlen, extralen = struct.unpack_from("<HH", hdr, 26)
    return zi.header_offset + 30 + fnlen + extralen


def aapt2_info(apk: Path):
    info = {"extractNativeLibs": "?", "nativeCode": "?", "targetSdk": "?", "package": "?", "versionName": "?"}
    if AAPT2 is None:
        return info
    try:
        out = subprocess.run([str(AAPT2), "dump", "badging", str(apk)], capture_output=True, text=True,
                             timeout=60, errors="replace").stdout
        for line in out.splitlines():
            if line.startswith("native-code:"):
                info["nativeCode"] = line.split(":", 1)[1].strip()
            elif line.startswith("targetSdkVersion:"):
                info["targetSdk"] = line.split(":", 1)[1].strip().strip("'")
            elif line.startswith("package:"):
                for tok in line.split():
                    if tok.startswith("name="):
                        info["package"] = tok[5:].strip("'")
                    elif tok.startswith("versionName="):
                        info["versionName"] = tok[12:].strip("'")
        out = subprocess.run([str(AAPT2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)],
                             capture_output=True, text=True, timeout=60, errors="replace").stdout
        for line in out.splitlines():
            if "extractNativeLibs" in line:
                info["extractNativeLibs"] = line.rsplit("=", 1)[-1].strip()
                break
        else:
            info["extractNativeLibs"] = "(absent)"
    except Exception as e:  # noqa: BLE001
        info["error"] = str(e)
    return info


def entry_abi(name: str) -> str:
    parts = name.split("/")
    return parts[1] if len(parts) >= 3 else "?"


def scan_apk(apk: Path):
    rows = []
    with zipfile.ZipFile(apk) as zf:
        for zi in zf.infolist():
            name = zi.filename
            if not (name.startswith("lib/") and name.endswith(".so")):
                continue
            compressed = zi.compress_type != zipfile.ZIP_STORED
            data_off = zip_data_offset(zf, zi)
            with zf.open(zi) as f:
                head = f.read(65536)
            align, err = elf_min_load_align(head)
            zip_ok = compressed or (data_off is not None and data_off % PAGE == 0)
            rows.append({
                "entry": name,
                "abi": entry_abi(name),
                "size": zi.file_size,
                "compressed": compressed,
                "dataOffset": data_off,
                "zipAligned16k": (data_off is not None and data_off % PAGE == 0),
                "elfAlign": align,
                "elfErr": err,
                "ok": (align is not None and align >= PAGE and zip_ok),
            })
    return rows


def scan_so_file(path: Path):
    with open(path, "rb") as f:
        head = f.read(65536)
    align, err = elf_min_load_align(head)
    machine = struct.unpack_from("<H" if head[5:6] == b"\x01" else ">H", head, 18)[0] if len(head) >= 20 else None
    abi = {183: "arm64-v8a", 62: "x86_64", 40: "armeabi-v7a", 3: "x86"}.get(machine)
    return {"path": str(path), "abi": abi, "size": path.stat().st_size, "elfAlign": align, "elfErr": err,
            "ok": (align is not None and align >= PAGE)}


def pick_apks(project: Path, max_groups: int):
    pattern = str(project / "**" / "build" / "outputs" / "apk" / "**" / "*.apk")
    files = [Path(p) for p in glob.glob(pattern, recursive=True)]
    files = [p for p in files if "androidTest" not in p.parts and ".git" not in p.parts]
    groups = defaultdict(list)
    for p in files:
        groups[p.parent].append(p)
    newest_per_group = [max(v, key=lambda p: p.stat().st_mtime) for v in groups.values()]
    newest_per_group.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return newest_per_group[:max_groups]


# Generated intermediates and deliberately invalid test fixtures are not shipping inputs.
SKIP_PARTS = ("build", ".cxx", ".externalNativeBuild", ".git", ".gradle", "node_modules", ".bak", "test-fixtures")


def loose_so_files(project: Path):
    return sorted(p for p in project.rglob("*.so") if not any(x in p.parts for x in SKIP_PARTS))


def aar_so_files(project: Path):
    out = []
    archives = [p for p in list(project.rglob("*.aar")) + list(project.rglob("*.jar"))
                if not any(x in p.parts for x in SKIP_PARTS)]
    for aar in archives:
        try:
            with zipfile.ZipFile(aar) as zf:
                for zi in zf.infolist():
                    if zi.filename.endswith(".so") and (zi.filename.startswith("jni/") or zi.filename.startswith("lib/")):
                        with zf.open(zi) as f:
                            head = f.read(65536)
                        align, err = elf_min_load_align(head)
                        out.append({"archive": str(aar.relative_to(project)), "entry": zi.filename,
                                    "abi": entry_abi(zi.filename), "size": zi.file_size,
                                    "elfAlign": align, "elfErr": err,
                                    "ok": (align is not None and align >= PAGE)})
        except zipfile.BadZipFile:
            continue
    return out


def fmt_align(a):
    return "-" if a is None else hex(a)


def strict_failures(rows):
    return [r for r in rows if r.get("abi") in STRICT_ABIS and not r["ok"]]


def render_apk_section(md, label: str, apk: Path, rows, info):
    bad = [r for r in rows if not r["ok"]]
    strict_bad = strict_failures(rows)
    mtime = time.strftime("%Y-%m-%d", time.localtime(apk.stat().st_mtime))
    md.append(f"\n### APK `{label}` ({mtime}, {apk.stat().st_size // 1048576} MiB)\n")
    md.append(f"package={info['package']} versionName={info['versionName']} targetSdk={info['targetSdk']} "
              f"native-code={info['nativeCode']} extractNativeLibs={info['extractNativeLibs']}  ")
    md.append(f"so entries={len(rows)}, failing={len(bad)}, failing 64-bit={len(strict_bad)}\n")
    if rows:
        md.append("| entry | KiB | compressed | zipAligned16k | ELF min p_align | ok |")
        md.append("| --- | ---: | --- | --- | --- | --- |")
        for r in rows:
            err = f" {r['elfErr']}" if r["elfErr"] else ""
            md.append(f"| {r['entry']} | {r['size'] // 1024} | {r['compressed']} | {r['zipAligned16k']} | "
                      f"{fmt_align(r['elfAlign'])}{err} | {'OK' if r['ok'] else 'BAD'} |")
    return len(strict_bad)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=None, help="parent directory of the repositories")
    parser.add_argument("--project", action="append", type=Path, default=[], help="scan only this project (repeatable)")
    parser.add_argument("--apk", action="append", type=Path, default=[], help="scan only this APK (repeatable)")
    parser.add_argument("--out", type=Path, default=None, help="report directory (default: <repo>/build/tmp-16kb)")
    parser.add_argument("--max-apks", type=int, default=int(os.environ.get("MAX_APKS", "5")))
    args = parser.parse_args()

    out_dir = args.out or (REPO_ROOT / "build" / "tmp-16kb")
    out_dir.mkdir(parents=True, exist_ok=True)
    md = [f"# 16 KB alignment scan ({time.strftime('%Y-%m-%d %H:%M')})\n",
          f"PAGE={PAGE}; OK means every PT_LOAD p_align >= 0x4000 and, for uncompressed entries, "
          f"zip data offset % {PAGE} == 0. Strict ABIs: {', '.join(STRICT_ABIS)}. aapt2={AAPT2 or 'unavailable'}\n"]
    summary = []
    strict_failure_total = 0

    if args.apk:
        for apk in args.apk:
            info = aapt2_info(apk)
            rows = scan_apk(apk)
            strict_failure_total += render_apk_section(md, str(apk), apk, rows, info)
            summary.append((apk.name, "1 apk", len(rows), len(strict_failures(rows)), f"extractNativeLibs={info['extractNativeLibs']}"))
    else:
        root = args.root or projects_root()
        projects = args.project or list(default_project_dirs(root))
        for project in projects:
            apks = pick_apks(project, args.max_apks)
            loose = loose_so_files(project)
            aars = aar_so_files(project)
            if not apks and not loose and not aars:
                summary.append((project.name, "no APK / no .so", 0, 0, "-"))
                continue
            md.append(f"\n## {project.name}\n")
            proj_total = proj_strict_bad = 0
            packaging_notes = set()
            for apk in apks:
                info = aapt2_info(apk)
                rows = scan_apk(apk)
                proj_total += len(rows)
                proj_strict_bad += render_apk_section(md, str(apk.relative_to(project)), apk, rows, info)
                packaging_notes.add(f"extractNativeLibs={info['extractNativeLibs']}")
            if loose:
                md.append("\n### Source-tree .so files (including untracked vendor inputs)\n")
                md.append("| path | KiB | ELF min p_align | ok |")
                md.append("| --- | ---: | --- | --- |")
                for p in loose:
                    r = scan_so_file(p)
                    r["abi"] = next((part for part in p.parts if part in STRICT_ABIS), r["abi"])
                    proj_total += 1
                    proj_strict_bad += len(strict_failures([r]))
                    md.append(f"| {p.relative_to(project)} | {r['size'] // 1024} | {fmt_align(r['elfAlign'])} | {'OK' if r['ok'] else 'BAD'} |")
            if aars:
                md.append("\n### .so inside AAR/JAR under the repo\n")
                md.append("| archive | entry | KiB | ELF min p_align | ok |")
                md.append("| --- | --- | ---: | --- | --- |")
                for r in aars:
                    proj_total += 1
                    proj_strict_bad += len(strict_failures([r]))
                    md.append(f"| {r['archive']} | {r['entry']} | {r['size'] // 1024} | {fmt_align(r['elfAlign'])} | {'OK' if r['ok'] else 'BAD'} |")
            strict_failure_total += proj_strict_bad
            summary.append((project.name, f"{len(apks)} apk, {len(loose)} loose .so, {len(aars)} aar .so",
                            proj_total, proj_strict_bad, ", ".join(sorted(packaging_notes)) or "-"))

    summary_md = ("\n## Summary\n\n| project | scanned | native entries total | 64-bit failing | packaging |\n| --- | --- | ---: | ---: | --- |\n"
                  + "\n".join(f"| {n} | {s} | {t} | {b} | {p} |" for n, s, t, b, p in summary) + "\n")
    md.insert(2, summary_md)
    report = out_dir / "alignment-report.md"
    report.write_text("\n".join(md), encoding="utf-8")
    print(summary_md)
    print(f"full report: {report}")
    print(f"64-bit failures: {strict_failure_total}")
    return 1 if strict_failure_total else 0


if __name__ == "__main__":
    sys.exit(main())
