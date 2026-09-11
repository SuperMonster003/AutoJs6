"""Replace the unused 4 KB x86_64 OpenCV 4.8.0 payload in the four Paddle vendor AARs.

Paddle currently filters to ARM. Keeping its vendor archive aligned avoids a latent
failure if x86_64 is enabled later. Java/resources and the ARM payloads stay byte-identical.
The donor is the existing reproducible OpenCV plugin artifact, pinned by SHA-256.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

DONOR_SHA = "869e3ee9cf0cbb6e76e8a02dd94cd12e6cd2445e4b6dc662f46ed8f715c8875a"
ENTRY = "jni/x86_64/libopencv_java4.so"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    args = parser.parse_args()
    donor = args.root / "AutoJs6-Plugin-OpenCV/libs/opencv-native-4.8.0.aar"
    assert digest(donor.read_bytes()) == DONOR_SHA, "Unexpected donor AAR"
    with zipfile.ZipFile(donor) as archive:
        replacement = archive.read(ENTRY)
    for version in range(3, 7):
        repo = args.root / f"AutoJs6-Plugin-Paddle-OCR-PP-OCRv{version}"
        aar = repo / "libs/org-opencv-4_8_0/opencv-4.8.0.aar"
        receipt_path = aar.with_suffix(".native-alignment.json")
        before_sha = digest(aar.read_bytes())
        with zipfile.ZipFile(aar) as archive:
            entries = [(entry, archive.read(entry)) for entry in archive.infolist()]
        original = dict((entry.filename, data) for entry, data in entries)[ENTRY]
        if original != replacement:
            output = aar.with_suffix(".16kb.tmp")
            with zipfile.ZipFile(output, "w") as archive:
                for entry, data in entries:
                    archive.writestr(entry, replacement if entry.filename == ENTRY else data)
            with zipfile.ZipFile(output) as archive:
                assert all(archive.read(entry.filename) == data for entry, data in entries if entry.filename != ENTRY)
            output.replace(aar)
            receipt = {"schemaVersion": 1, "date": "2026-09-11", "beforeSha256": before_sha,
                       "afterSha256": digest(aar.read_bytes()), "replacedEntry": ENTRY,
                       "originalLibrarySha256": digest(original), "librarySha256": digest(replacement),
                       "minLoadAlign": 16384, "donorArtifactSha256": DONOR_SHA,
                       "donorRepository": "AutoJs6-Plugin-OpenCV", "donorProvenance": "libs/opencv-native-4.8.0.provenance.json",
                       "note": "x86_64 is currently excluded by Paddle abiFilters; all other entries are unchanged."}
            receipt_path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        sdk_copy = repo / "libs/paddleocr/src/sdk/native/libs/x86_64/libopencv_java4.so"
        if sdk_copy.is_file():
            sdk_copy.write_bytes(replacement)
        print(repo.name, digest(aar.read_bytes()))


if __name__ == "__main__":
    main()
