"""Package the verified OpenCV 4.8.0 TBB runtime for Rapid OCR (no second OpenMP runtime).

The donor AAR is produced by AutoJs6-Plugin-OpenCV/scripts/opencv/build-native-aar.ps1.
Headers come from the official opencv-4.8.0-android-sdk.zip (or its deployed Paddle SDK).
Only JNI binaries and headers are copied; Rapid does not depend on OpenCV Java classes.

The receipt `date` is the day the payload last changed, in UTC+08:00 like plugin release dates.
An unchanged payload keeps the date of its existing receipt; `--date` sets it explicitly.
Donor build facts (NDK release, compiler) are copied from the donor provenance, not hard-coded.
An AAR whose entries already match is left byte-for-byte untouched.

Example: python tools/16kb/refresh_rapid_opencv.py --targets v3
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import zipfile

DONOR_SHA = "f760265bbc0bae75f2f906088fee3447eb32d9bc2b4d7ad8fd246923ddc26dd6"  # NDK r28c rebuild, 2026-09-12 (UTC+08:00)
DONOR_PROVENANCE = "AutoJs6-Plugin-OpenCV/libs/opencv-native-4.8.0.provenance.json"
DONOR_BUILD_KEYS = ("androidNdkRelease", "androidNdkVersion", "compilerIdent", "stl", "elfLoadSegmentAlignmentBytes")
RELEASE_ZONE = datetime.timezone(datetime.timedelta(hours=8))
TARGETS = {"v3": "AutoJs6-Plugin-Rapid-OCR-PP-OCRv3", "v6": "AutoJs6-Plugin-Rapid-OCR-PP-OCRv6"}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def donor_build(root):
    provenance = json.loads((root / DONOR_PROVENANCE).read_text(encoding="utf-8"))
    assert provenance["artifact"]["sha256"] == DONOR_SHA, "The donor provenance does not describe the pinned donor AAR"
    return {key: provenance["build"][key] for key in DONOR_BUILD_KEYS}


def write_payload(path, entries):
    """Writes the AAR only when its entries differ; returns whether the file changed."""
    if path.is_file():
        with zipfile.ZipFile(path) as existing:
            if {info.filename: existing.read(info) for info in existing.infolist()} == entries:
                return False
    temporary = path.with_suffix(".tmp")
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(entry, data)
    temporary.replace(path)
    return True


def receipt_date(explicit, changed, previous, sha256):
    if explicit:
        return datetime.date.fromisoformat(explicit).isoformat()
    if not changed and previous.get("sha256") == sha256 and previous.get("donorSha256") == DONOR_SHA and previous.get("date"):
        return previous["date"]
    return datetime.datetime.now(RELEASE_ZONE).date().isoformat()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--opencv-headers", type=Path)
    parser.add_argument("--targets", nargs="+", choices=sorted(TARGETS), default=sorted(TARGETS))
    parser.add_argument("--date", help="receipt date as YYYY-MM-DD (default: see module help)")
    args = parser.parse_args()
    donor = args.root / "AutoJs6-Plugin-OpenCV/libs/opencv-native-4.8.0.aar"
    headers = args.opencv_headers or args.root / "AutoJs6-Plugin-Paddle-OCR-PP-OCRv3/libs/paddleocr/src/sdk/native/jni/include"
    assert digest(donor.read_bytes()) == DONOR_SHA, "Unexpected donor AAR"
    build = donor_build(args.root)
    version_header = (headers / "opencv2/core/version.hpp").read_text()
    assert "#define CV_VERSION_MAJOR    4" in version_header
    assert "#define CV_VERSION_MINOR    8" in version_header
    entries = {}
    with zipfile.ZipFile(donor) as source:
        entries.update({entry.filename: source.read(entry) for entry in source.infolist() if entry.filename.startswith("jni/") and entry.filename.endswith(".so")})
    entries.update({"headers/" + path.relative_to(headers).as_posix(): path.read_bytes() for path in headers.rglob("*") if path.is_file()})
    for key in args.targets:
        target = args.root / TARGETS[key] / "libs/opencv-native-4.8.0.aar"
        receipt_path = target.with_suffix(".provenance.json")
        previous = json.loads(receipt_path.read_text(encoding="utf-8")) if receipt_path.is_file() else {}
        changed = write_payload(target, entries)
        sha256 = digest(target.read_bytes())
        receipt = {"schemaVersion": 1, "date": receipt_date(args.date, changed, previous, sha256), "opencvVersion": "4.8.0",
                   "sha256": sha256, "donorSha256": DONOR_SHA,
                   "donorProvenance": DONOR_PROVENANCE, "donorBuild": build,
                   "headersUrl": "https://github.com/opencv/opencv/releases/download/4.8.0/opencv-4.8.0-android-sdk.zip",
                   "threading": "TBB; no statically linked OpenMP in RapidOcr",
                   "entries": {name: digest(data) for name, data in sorted(entries.items())}}
        receipt_path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        print(target, target.stat().st_size, sha256, "rewritten" if changed else "unchanged", receipt["date"])


if __name__ == "__main__":
    main()
