"""Copy libc++_shared.so from NDK r28.2 into a jniLibs tree without adding an NDK dependency to Gradle builds.

Default target is the host (`app/src/main/jniLibs`, all four ABIs). Plugin repositories that ship a
checked-in libc++ (Paddle OCR v4 / v5 / v6 runtime modules) pass `--target <jniLibs dir> --abis arm64-v8a armeabi-v7a`.
A `libc++_shared.provenance.json` is written next to the ABI directories.
"""
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
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--target", type=Path, default=None,
                        help="jniLibs directory containing <abi>/ subdirectories (default: host app/src/main/jniLibs)")
    parser.add_argument("--abis", nargs="+", choices=sorted(TRIPLES), default=sorted(TRIPLES))
    args = parser.parse_args()
    if f"Pkg.Revision = {NDK_VERSION}" not in (args.ndk / "source.properties").read_text():
        raise ValueError("Expected NDK " + NDK_VERSION)
    root = Path(__file__).resolve().parents[2]
    target = (args.target or root / "app/src/main/jniLibs").resolve()
    host = next((args.ndk / "toolchains/llvm/prebuilt").iterdir())
    records = []
    for abi in args.abis:
        triple = TRIPLES[abi]
        relative = f"toolchains/llvm/prebuilt/{host.name}/sysroot/usr/lib/{triple}/libc++_shared.so"
        source = args.ndk / relative
        data = source.read_bytes()
        align, error = elf_min_load_align(data[:65536])
        if error or (abi in ("arm64-v8a", "x86_64") and align < 16384):
            raise ValueError(f"Invalid NDK runtime: {relative} ({align}, {error})")
        destination = target / abi / source.name
        previous = hashlib.sha256(destination.read_bytes()).hexdigest() if destination.exists() else None
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        records.append(dict(abi=abi, source=relative, sha256=hashlib.sha256(data).hexdigest(), minLoadAlign=align,
                            replacedSha256=previous))
    (target / "libc++_shared.provenance.json").write_text(json.dumps(dict(
        ndkVersion=NDK_VERSION, source="https://developer.android.com/ndk/downloads/revision_history",
        policy="64-bit PT_LOAD >= 16384; 32-bit NDK runtimes retain upstream alignment", libraries=records,
    ), indent=2) + "\n", encoding="utf-8")
    print(f"Copied and verified libc++ into {target} for " + ", ".join(args.abis))


if __name__ == "__main__":
    main()
