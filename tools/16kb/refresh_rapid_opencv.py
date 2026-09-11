"""Package the verified OpenCV 4.8.0 TBB runtime for Rapid OCR (no second OpenMP runtime).

The donor AAR is produced by AutoJs6-Plugin-OpenCV/scripts/opencv/build-native-aar.ps1.
Headers come from the official opencv-4.8.0-android-sdk.zip (or its deployed Paddle SDK).
Only JNI binaries and headers are copied; Rapid does not depend on OpenCV Java classes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

DONOR_SHA = "869e3ee9cf0cbb6e76e8a02dd94cd12e6cd2445e4b6dc662f46ed8f715c8875a"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--opencv-headers", type=Path)
    args = parser.parse_args()
    donor = args.root / "AutoJs6-Plugin-OpenCV/libs/opencv-native-4.8.0.aar"
    headers = args.opencv_headers or args.root / "AutoJs6-Plugin-Paddle-OCR-PP-OCRv3/libs/paddleocr/src/sdk/native/jni/include"
    assert hashlib.sha256(donor.read_bytes()).hexdigest() == DONOR_SHA
    assert '#define CV_VERSION_MAJOR    4' in (headers / 'opencv2/core/version.hpp').read_text()
    assert '#define CV_VERSION_MINOR    8' in (headers / 'opencv2/core/version.hpp').read_text()
    first = args.root / "AutoJs6-Plugin-Rapid-OCR-PP-OCRv3/libs/opencv-native-4.8.0.aar"
    entries = {}
    with zipfile.ZipFile(donor) as source:
        entries.update({entry.filename: source.read(entry) for entry in source.infolist() if entry.filename.startswith('jni/') and entry.filename.endswith('.so')})
    entries.update({"headers/" + path.relative_to(headers).as_posix(): path.read_bytes() for path in headers.rglob('*') if path.is_file()})
    with zipfile.ZipFile(first, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(entry, data)
    receipt = {"schemaVersion": 1, "date": "2026-09-11", "opencvVersion": "4.8.0",
               "sha256": hashlib.sha256(first.read_bytes()).hexdigest(), "donorSha256": DONOR_SHA,
               "donorProvenance": "AutoJs6-Plugin-OpenCV/libs/opencv-native-4.8.0.provenance.json",
               "headersUrl": "https://github.com/opencv/opencv/releases/download/4.8.0/opencv-4.8.0-android-sdk.zip",
               "threading": "TBB; no statically linked OpenMP in RapidOcr",
               "entries": {name: hashlib.sha256(data).hexdigest() for name, data in sorted(entries.items())}}
    for version in (3, 6):
        target = args.root / f"AutoJs6-Plugin-Rapid-OCR-PP-OCRv{version}/libs/opencv-native-4.8.0.aar"
        if target != first:
            shutil.copyfile(first, target)
        target.with_suffix('.provenance.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
        print(target, target.stat().st_size, receipt['sha256'])


if __name__ == '__main__':
    main()
