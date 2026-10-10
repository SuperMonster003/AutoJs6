"""Replace every OpenCV 4.8.0 JNI payload in the four Paddle vendor AARs with the OpenCV plugin donor.

The donor is the reproducible NDK r28c rebuild from AutoJs6-Plugin-OpenCV (16 KB PT_LOAD alignment and a
RELRO layout that keeps writable data off the protected pages). Java classes and resources in the vendor
AAR stay byte-identical; only `jni/<abi>/libopencv_java4.so` entries and the Paddle v3 SDK copies change.

A receipt is written only when an AAR changes. Its `date` is that day in UTC+08:00, like plugin release
dates, unless `--date` sets it; the donor NDK release is read from the donor provenance.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import zipfile

DONOR_SHA = "f760265bbc0bae75f2f906088fee3447eb32d9bc2b4d7ad8fd246923ddc26dd6"  # NDK r28c rebuild, 2026-09-12 (UTC+08:00)
DONOR_PROVENANCE = "AutoJs6-Plugin-OpenCV/libs/opencv-native-4.8.0.provenance.json"
RELEASE_ZONE = datetime.timezone(datetime.timedelta(hours=8))
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
ENTRIES = {abi: f"jni/{abi}/libopencv_java4.so" for abi in ABIS}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--date", help="receipt date as YYYY-MM-DD (default: today in UTC+08:00)")
    args = parser.parse_args()
    receipt_date = datetime.date.fromisoformat(args.date).isoformat() if args.date else datetime.datetime.now(RELEASE_ZONE).date().isoformat()
    donor = args.root / "AutoJs6-Plugin-OpenCV/libs/opencv-native-4.8.0.aar"
    assert digest(donor.read_bytes()) == DONOR_SHA, "Unexpected donor AAR"
    provenance = json.loads((args.root / DONOR_PROVENANCE).read_text(encoding="utf-8"))
    assert provenance["artifact"]["sha256"] == DONOR_SHA, "The donor provenance does not describe the pinned donor AAR"
    donor_ndk_release = provenance["build"]["androidNdkRelease"]
    with zipfile.ZipFile(donor) as archive:
        replacements = {entry: archive.read(entry) for entry in ENTRIES.values()}
    for version in range(3, 7):
        repo = args.root / f"AutoJs6-Plugin-Paddle-OCR-PP-OCRv{version}"
        aar = repo / "libs/org-opencv-4_8_0/opencv-4.8.0.aar"
        receipt_path = aar.with_suffix(".native-alignment.json")
        before_sha = digest(aar.read_bytes())
        with zipfile.ZipFile(aar) as archive:
            entries = [(entry, archive.read(entry)) for entry in archive.infolist()]
        originals = {name: data for entry, data in entries for name in [entry.filename] if name in replacements}
        changed = {name for name, data in originals.items() if data != replacements[name]}
        if changed:
            output = aar.with_suffix(".16kb.tmp")
            with zipfile.ZipFile(output, "w") as archive:
                for entry, data in entries:
                    archive.writestr(entry, replacements.get(entry.filename, data))
            with zipfile.ZipFile(output) as archive:
                assert all(archive.read(entry.filename) == data for entry, data in entries if entry.filename not in replacements)
            output.replace(aar)
            receipt = {"schemaVersion": 2, "date": receipt_date, "beforeSha256": before_sha,
                       "afterSha256": digest(aar.read_bytes()),
                       "replacedEntries": {name: {"originalLibrarySha256": digest(originals[name]),
                                                  "librarySha256": digest(replacements[name])} for name in sorted(replacements)},
                       "minLoadAlign": 16384, "donorArtifactSha256": DONOR_SHA,
                       "donorRepository": "AutoJs6-Plugin-OpenCV", "donorProvenance": "libs/opencv-native-4.8.0.provenance.json",
                       "donorNdkRelease": donor_ndk_release,
                       "note": f"All four JNI payloads come from the OpenCV plugin's NDK {donor_ndk_release} rebuild; classes.jar and resources are unchanged."}
            receipt_path.write_text(json.dumps(receipt, indent=2) + chr(10), encoding="utf-8")
        for abi, entry in ENTRIES.items():
            sdk_copy = repo / f"libs/paddleocr/src/sdk/native/libs/{abi}/libopencv_java4.so"
            if sdk_copy.is_file():
                sdk_copy.write_bytes(replacements[entry])
        print(repo.name, digest(aar.read_bytes()), "replaced" if changed else "unchanged")


if __name__ == "__main__":
    main()
