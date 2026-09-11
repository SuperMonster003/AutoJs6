"""Copy libc++ from NDK r28.2 without introducing an NDK dependency in host builds."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
from scan_alignment import elf_min_load_align

NDK_VERSION = "28.2.13676358"
TRIPLES = {"arm64-v8a": "aarch64-linux-android", "armeabi-v7a": "arm-linux-androideabi",
           "x86": "i686-linux-android", "x86_64": "x86_64-linux-android"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    args = parser.parse_args()
    if f"Pkg.Revision = {NDK_VERSION}" not in (args.ndk / "source.properties").read_text():
        raise ValueError("Expected NDK " + NDK_VERSION)
    root = Path(__file__).resolve().parents[2]
    target = root / "app/src/main/jniLibs"
    host = next((args.ndk / "toolchains/llvm/prebuilt").iterdir())
    records = []
    for abi, triple in TRIPLES.items():
        relative = f"toolchains/llvm/prebuilt/{host.name}/sysroot/usr/lib/{triple}/libc++_shared.so"
        source = args.ndk / relative
        data = source.read_bytes()
        align, error = elf_min_load_align(data[:65536])
        if error or (abi in ("arm64-v8a", "x86_64") and align < 16384):
            raise ValueError(f"Invalid NDK runtime: {relative} ({align}, {error})")
        shutil.copyfile(source, target / abi / source.name)
        records.append(dict(abi=abi, source=relative, sha256=hashlib.sha256(data).hexdigest(), minLoadAlign=align))
    (target / "libc++_shared.provenance.json").write_text(json.dumps(dict(
        ndkVersion=NDK_VERSION, source="https://developer.android.com/ndk/downloads/revision_history",
        policy="64-bit PT_LOAD >= 16384; 32-bit NDK runtimes retain upstream alignment", libraries=records,
    ), indent=2) + "\n", encoding="utf-8")
    print("Copied and verified libc++ for " + ", ".join(TRIPLES))


if __name__ == "__main__":
    main()
