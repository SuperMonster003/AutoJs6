"""CI second opinion: scan every APK under a directory and run SDK zipalign -P 16."""
import argparse
import json
from pathlib import Path
import subprocess
from scan_alignment import AAPT2, STRICT_ABIS, scan_apk


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    apks = sorted(args.directory.rglob("*.apk"))
    if not apks:
        raise SystemExit(f"No APK found: {args.directory}")
    if AAPT2 is None:
        raise SystemExit("Android SDK build-tools 35+ required")
    zipalign = AAPT2.with_name("zipalign.exe" if AAPT2.suffix == ".exe" else "zipalign")
    results = []
    for apk in apks:
        rows = scan_apk(apk)
        alignment = subprocess.run([str(zipalign), "-c", "-P", "16", "-v", "4", str(apk)],
                                   capture_output=True, text=True, errors="replace")
        ok = alignment.returncode == 0 and all(row["ok"] for row in rows if row["abi"] in STRICT_ABIS)
        results.append(dict(apk=str(apk), ok=ok, entries=rows, zipalign=alignment.stdout + alignment.stderr))
        print(f"{'PASS' if ok else 'FAIL'} {apk}")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(0 if all(result["ok"] for result in results) else 1)


if __name__ == "__main__":
    main()
