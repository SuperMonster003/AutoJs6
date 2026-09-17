"""Report what an APK is made of and compare it with a saved baseline.

The report answers the questions that drive docs/dev/apk-size-reduction-roadmap.md:
how many bytes each top-level group (DEX, res, assets, lib, loose resources) costs
after zip compression, which zip entries are the largest, and how many raw DEX bytes
each Java package occupies (via `apkanalyzer dex packages`, raw bytes, not compressed).

Usage:
  python tools/size/apk_size_report.py <apk> [<apk> ...]
      [--json out.json] [--markdown out.md] [--compare baseline.json]
      [--apkanalyzer PATH] [--no-dex] [--top N] [--dex-depth N] [--dex-min-kib N] [--dex-keep-kib N]

APKs are matched to baseline entries by file name with the version replaced by `*`
(`autojs6-v6.8.0-universal.apk` -> `autojs6-v*-universal.apk`), so a newer build can be
compared with an older baseline without renaming anything.

`apkanalyzer` is looked up in this order: --apkanalyzer, $APKANALYZER, then
`cmdline-tools/latest/bin/apkanalyzer[.bat]` below $ANDROID_HOME, $ANDROID_SDK_ROOT or
`sdk.dir` in local.properties (searched upwards from this script).
"""
import argparse
import collections
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

SCHEMA = "autojs.apk-size-report/v1"
MIB = 1024 * 1024

# Packages the roadmap tracks explicitly; they always appear in the DEX table and in the
# comparison, regardless of the size threshold, so a removal shows up as an explicit row.
WATCH_PACKAGES = [
    "org.autojs.autojs",
    "com.stardust",
    "org.mozilla.javascript",
    "com.android.tools.r8",
    "com.android.dx",
    "com.legacy.android.dx",
    "com.android.apksig",
    "com.mcal.apksigner",
    "org.spongycastle",
    "androidx.media3",
    "com.google.common",
    "kotlin.reflect",
    "kotlinx.coroutines",
    "com.fasterxml",
    "com.vladsch",
    "org.commonmark",
    "org.kohsuke",
    "org.apache.commons.lang3",
    "org.apache.commons.io",
    "org.apache.commons.codec",
    "org.apache.log4j",
    "org.jline",
    "org.joda",
    "org.jsoup",
    "javax.mail",
    "com.sun.mail",
    "javax.activation",
    "junit",
    "org.junit",
    "org.hamcrest",
    "androidx.appcompatlegacy",
    "org.opencv",
    "io.reactivex",
    "com.google.android.material",
    "com.google.gson",
    "okhttp3",
    "retrofit2",
    "com.bumptech.glide",
    "org.eclipse.paho",
    "j$",
]

VERSION_RE = re.compile(r"v\d+(?:\.\d+)*")


def mib(value):
    return value / MIB


def apk_key(name):
    return VERSION_RE.sub("v*", name)


def group_key(name):
    parts = name.split("/")
    if re.fullmatch(r"classes\d*\.dex", name):
        return "dex"
    if name == "resources.arsc":
        return "resources.arsc"
    if name == "AndroidManifest.xml":
        return "manifest"
    if parts[0] == "res":
        return "res"
    if parts[0] == "lib":
        return "lib/" + parts[1] if len(parts) > 2 else "lib"
    if parts[0] == "assets":
        return "assets/" + parts[1] if len(parts) > 2 else "assets/(root files)"
    if parts[0] == "META-INF":
        return "META-INF"
    depth = 3 if parts[0] in ("org", "com") else 2
    if len(parts) > depth:
        return "other/" + "/".join(parts[:depth])
    return "other/" + "/".join(parts[:-1]) if len(parts) > 1 else "other/(root files)"


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_apkanalyzer(explicit):
    if explicit:
        return explicit
    env = os.environ.get("APKANALYZER")
    if env:
        return env
    roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    for parent in [Path(__file__).resolve().parent] + list(Path(__file__).resolve().parents):
        local = parent / "local.properties"
        if local.is_file():
            for line in local.read_text(encoding="utf-8", errors="replace").splitlines():
                if line.startswith("sdk.dir="):
                    roots.append(line[len("sdk.dir="):].strip().replace("\\:", ":").replace("\\\\", "\\"))
            break
    exe = "apkanalyzer.bat" if os.name == "nt" else "apkanalyzer"
    for root in roots:
        if not root:
            continue
        candidate = Path(root) / "cmdline-tools" / "latest" / "bin" / exe
        if candidate.is_file():
            return str(candidate)
    return None


def dex_packages(apkanalyzer, apk):
    proc = subprocess.run([apkanalyzer, "dex", "packages", apk], capture_output=True, check=True)
    packages = {}
    total = None
    for line in proc.stdout.decode("utf-8", errors="replace").splitlines():
        columns = line.rstrip("\n").split("\t")
        if len(columns) < 4 or not columns[0].startswith("P "):
            continue
        head = columns[0].split(" ")
        record = {"defined": int(head[2]), "referenced": int(columns[1]), "bytes": int(columns[2])}
        if columns[3] == "<TOTAL>":
            total = record
        else:
            packages[columns[3]] = record
    return total, packages


def analyze(apk_path, apkanalyzer, with_dex, top, dex_keep_bytes):
    groups = collections.defaultdict(lambda: {"compressed": 0, "raw": 0, "count": 0})
    extensions = collections.defaultdict(lambda: {"compressed": 0, "raw": 0, "count": 0})
    entries = []
    dex_files = 0
    with zipfile.ZipFile(apk_path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            name = info.filename
            if re.fullmatch(r"classes\d*\.dex", name):
                dex_files += 1
            for table, key in ((groups, group_key(name)), (extensions, os.path.splitext(name)[1] or "(none)")):
                table[key]["compressed"] += info.compress_size
                table[key]["raw"] += info.file_size
                table[key]["count"] += 1
            entries.append((info.compress_size, info.file_size, name))
    entries.sort(reverse=True)
    report = {
        "key": apk_key(os.path.basename(apk_path)),
        "name": os.path.basename(apk_path),
        "path": os.path.abspath(apk_path).replace("\\", "/"),
        "bytes": os.path.getsize(apk_path),
        "sha256": sha256_of(apk_path),
        "entries": len(entries),
        "compressed_sum": sum(e[0] for e in entries),
        "raw_sum": sum(e[1] for e in entries),
        "groups": dict(sorted(groups.items(), key=lambda kv: -kv[1]["compressed"])),
        "extensions": dict(sorted(extensions.items(), key=lambda kv: -kv[1]["compressed"])),
        "largest": [{"compressed": c, "raw": r, "name": n} for c, r, n in entries[:top]],
        "dex": {"files": dex_files},
    }
    if with_dex and apkanalyzer:
        total, packages = dex_packages(apkanalyzer, apk_path)
        report["dex"]["total"] = total
        report["dex"]["packages"] = {
            name: record for name, record in packages.items()
            if record["bytes"] >= dex_keep_bytes or name in WATCH_PACKAGES
        }
    return report


def select_dex_rows(packages, depth, min_bytes):
    rows = {}
    for name, record in packages.items():
        if (name.count(".") < depth and record["bytes"] >= min_bytes) or name in WATCH_PACKAGES:
            rows[name] = record
    return dict(sorted(rows.items(), key=lambda kv: -kv[1]["bytes"]))


def delta_text(current, base, scale=MIB, digits=2):
    if base is None:
        return "(新)"
    return "%+.*f" % (digits, (current - base) / scale)


def render_markdown(reports, baseline_by_key, args):
    lines = ["# APK 体积报告", "", "生成时间: %s" % datetime.datetime.now().strftime("%Y-%m-%d %H:%M"), ""]
    lines.append("压缩 / 原始均为 MiB. DEX 包大小为 apkanalyzer `dex packages` 归属到包的原始字节 (不含共享的字符串池 / 类型表), 与 DEX 文件原始大小不等; DEX 文件在 APK 中约按 0.39 倍压缩, 移除一个包的 APK 收益以 0.39 倍归属字节为保守下限.")
    lines.append("")
    for report in reports:
        base = baseline_by_key.get(report["key"]) if baseline_by_key else None
        lines.append("## %s" % report["name"])
        lines.append("")
        lines.append("- 路径: `%s`" % report["path"])
        lines.append("- SHA-256: `%s`" % report["sha256"])
        if base:
            lines.append("- 基线: `%s` (%s 字节)" % (base["name"], format(base["bytes"], ",")))
        lines.append("")
        header = "| 指标 | 值 |" + (" 基线 | 差 |" if base else "")
        lines.append(header)
        lines.append("| --- | ---: |" + (" ---: | ---: |" if base else ""))

        def metric(label, value, base_value, fmt, count=False):
            row = "| %s | %s |" % (label, fmt(value))
            if base:
                if base_value is None:
                    row += " - | - |"
                elif count:
                    row += " %s | %s |" % (fmt(base_value), format(value - base_value, "+,"))
                else:
                    row += " %s | %s |" % (fmt(base_value), delta_text(value, base_value))
            lines.append(row)

        metric("APK 字节", report["bytes"], base and base["bytes"], lambda v: format(v, ","), count=True)
        metric("APK MiB", report["bytes"], base and base["bytes"], lambda v: "%.2f" % mib(v))
        metric("条目数", report["entries"], base and base["entries"], str, count=True)
        metric("压缩合计 MiB", report["compressed_sum"], base and base["compressed_sum"], lambda v: "%.2f" % mib(v))
        metric("原始合计 MiB", report["raw_sum"], base and base["raw_sum"], lambda v: "%.2f" % mib(v))
        dex_total = report["dex"].get("total")
        if dex_total:
            base_total = base["dex"].get("total") if base else None
            metric("DEX 文件数", report["dex"]["files"], base and base["dex"]["files"], str, count=True)
            metric("DEX 包归属原始 MiB", dex_total["bytes"], base_total and base_total["bytes"], lambda v: "%.2f" % mib(v))
            metric("DEX 定义方法数", dex_total["defined"], base_total and base_total["defined"], lambda v: format(v, ","), count=True)
            metric("DEX 引用方法数", dex_total["referenced"], base_total and base_total["referenced"], lambda v: format(v, ","), count=True)
        lines.append("")

        lines.append("### 顶层构成 (压缩 >= 16 KiB 或基线中存在)")
        lines.append("")
        lines.append("| 组 | 压缩 MiB | 原始 MiB | 文件数 |" + (" 基线压缩 MiB | 差 |" if base else ""))
        lines.append("| --- | ---: | ---: | ---: |" + (" ---: | ---: |" if base else ""))
        keys = list(report["groups"].keys())
        if base:
            keys += [k for k in base["groups"] if k not in report["groups"]]
        for key in keys:
            current = report["groups"].get(key)
            base_group = base["groups"].get(key) if base else None
            if current is None:
                lines.append("| %s | 0.00 | 0.00 | 0 | %.2f | %s |" % (key, mib(base_group["compressed"]), delta_text(0, base_group["compressed"])))
                continue
            if current["compressed"] < 16 * 1024 and not base_group:
                continue
            row = "| %s | %.2f | %.2f | %d |" % (key, mib(current["compressed"]), mib(current["raw"]), current["count"])
            if base:
                row += " %s | %s |" % ("%.2f" % mib(base_group["compressed"]) if base_group else "-", delta_text(current["compressed"], base_group["compressed"] if base_group else None))
            lines.append(row)
        lines.append("")

        packages = report["dex"].get("packages")
        if packages:
            lines.append("### DEX 包 (深度 <= %d 且 >= %d KiB, 或关注列表)" % (args.dex_depth, args.dex_min_kib))
            lines.append("")
            lines.append("| 包 | 定义方法 | 原始 MiB |" + (" 基线 MiB | 差 |" if base else ""))
            lines.append("| --- | ---: | ---: |" + (" ---: | ---: |" if base else ""))
            rows = select_dex_rows(packages, args.dex_depth, args.dex_min_kib * 1024)
            base_packages = base["dex"].get("packages", {}) if base else {}
            if base:
                for name in WATCH_PACKAGES:
                    if name in base_packages and name not in rows:
                        rows[name] = {"defined": 0, "referenced": 0, "bytes": 0}
            for name, record in rows.items():
                row = "| %s | %s | %.2f |" % (name, format(record["defined"], ","), mib(record["bytes"]))
                if base:
                    base_record = base_packages.get(name)
                    row += " %s | %s |" % ("%.2f" % mib(base_record["bytes"]) if base_record else "-", delta_text(record["bytes"], base_record["bytes"] if base_record else None))
                lines.append(row)
            lines.append("")

        lines.append("### 最大条目 (前 %d)" % args.top)
        lines.append("")
        lines.append("| 压缩 MiB | 原始 MiB | 条目 |")
        lines.append("| ---: | ---: | --- |")
        for entry in report["largest"]:
            lines.append("| %.2f | %.2f | %s |" % (mib(entry["compressed"]), mib(entry["raw"]), entry["name"]))
        lines.append("")
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("apks", nargs="+")
    parser.add_argument("--json")
    parser.add_argument("--markdown")
    parser.add_argument("--compare", help="baseline JSON written earlier by --json")
    parser.add_argument("--apkanalyzer")
    parser.add_argument("--no-dex", action="store_true", help="skip apkanalyzer (no per-package DEX sizes)")
    parser.add_argument("--top", type=int, default=30)
    parser.add_argument("--dex-depth", type=int, default=2, help="package depth listed in the DEX table (1 = top-level)")
    parser.add_argument("--dex-min-kib", type=int, default=150)
    parser.add_argument("--dex-keep-kib", type=int, default=8, help="packages below this size are dropped from the JSON unless watched")
    args = parser.parse_args(argv)

    apkanalyzer = None if args.no_dex else find_apkanalyzer(args.apkanalyzer)
    if not args.no_dex and not apkanalyzer:
        print("apkanalyzer not found; pass --apkanalyzer or --no-dex", file=sys.stderr)
        return 2

    reports = [analyze(apk, apkanalyzer, not args.no_dex, args.top, args.dex_keep_kib * 1024) for apk in args.apks]
    baseline_by_key = {}
    if args.compare:
        with open(args.compare, encoding="utf-8") as stream:
            baseline = json.load(stream)
        baseline_by_key = {entry["key"]: entry for entry in baseline["apks"]}

    document = {"schema": SCHEMA, "generated_at": datetime.datetime.now().isoformat(timespec="seconds"), "apks": reports}
    if args.json:
        Path(args.json).parent.mkdir(parents=True, exist_ok=True)
        with open(args.json, "w", encoding="utf-8") as stream:
            json.dump(document, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
    if args.markdown:
        Path(args.markdown).parent.mkdir(parents=True, exist_ok=True)
        with open(args.markdown, "w", encoding="utf-8") as stream:
            stream.write(render_markdown(reports, baseline_by_key, args))

    for report in reports:
        base = baseline_by_key.get(report["key"])
        summary = "%s: %s bytes (%.2f MiB), %d entries, dex files %d" % (
            report["name"], format(report["bytes"], ","), mib(report["bytes"]), report["entries"], report["dex"]["files"])
        if base:
            summary += ", vs baseline %+.2f MiB" % ((report["bytes"] - base["bytes"]) / MIB)
        total = report["dex"].get("total")
        if total:
            summary += ", dex raw %.2f MiB, %s methods" % (mib(total["bytes"]), format(total["defined"], ","))
        print(summary)
    return 0


if __name__ == "__main__":
    sys.exit(main())
