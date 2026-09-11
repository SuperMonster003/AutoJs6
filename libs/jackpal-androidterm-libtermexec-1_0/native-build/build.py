"""Rebuild only the JNI entries of the checked-in jackpal AAR using the locked NDK.

python build.py --ndk <sdk>/ndk/28.2.13676358 --cmake <sdk>/cmake/3.22.1/bin/cmake[.exe]
The output is written under the host build/ directory. --replace installs it after verification.
No Gradle, upstream Java compilation, or modifications to Java/resources are involved.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def run(*args):
    subprocess.run([str(x) for x in args], check=True)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def alignment(data):
    if data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("Not a little-endian ELF")
    is64 = data[4] == 2
    phoff = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
    size, count = struct.unpack_from("<HH", data, 54 if is64 else 42)
    return min(struct.unpack_from("<Q" if is64 else "<I", data, off + (48 if is64 else 28))[0]
               for off in range(phoff, phoff + size * count, size)
               if struct.unpack_from("<I", data, off)[0] == 1)


def jni_exports(nm, path):
    output = subprocess.check_output([str(nm), "-D", "--defined-only", str(path)], text=True)
    return sorted(line.split()[-1] for line in output.splitlines()
                  if line.split()[-1].startswith(("Java_", "JNI_OnLoad")))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", required=True, type=Path)
    parser.add_argument("--cmake", default="cmake")
    parser.add_argument("--ninja", type=Path)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()
    lock = json.loads((HERE / "upstream.lock.json").read_text(encoding="utf-8"))
    if f"Pkg.Revision = {lock['ndkVersion']}" not in (args.ndk / "source.properties").read_text():
        raise ValueError("Unexpected NDK version")
    work = ROOT / "build/tmp-16kb/jackpal-native"
    work.mkdir(parents=True, exist_ok=True)
    source = args.source or work / "upstream"
    if not source.exists():
        run("git", "clone", "--no-checkout", lock["repository"], source)
        run("git", "-C", source, "checkout", "--detach", lock["commit"])
    commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if commit != lock["commit"]:
        raise ValueError(f"Upstream commit mismatch: {commit}")
    for name, digest in lock["sources"].items():
        if sha((source / name).read_bytes()) != digest:
            raise ValueError(f"Upstream source digest mismatch: {name}")
    host = next((args.ndk / "toolchains/llvm/prebuilt").iterdir())
    suffix = ".exe" if os.name == "nt" else ""
    nm = host / f"bin/llvm-nm{suffix}"
    strip = host / f"bin/llvm-strip{suffix}"
    ninja = args.ninja or Path(args.cmake).resolve().with_name(f"ninja{suffix}")
    replacements = {}
    records = []
    original = HERE.parent / "libtermexec-release.aar"
    with zipfile.ZipFile(original) as old:
        for abi in ABIS:
            build = work / abi
            configure = [args.cmake, "-S", HERE, "-B", build, "-G", "Ninja",
                         f"-DCMAKE_TOOLCHAIN_FILE={args.ndk.resolve().as_posix()}/build/cmake/android.toolchain.cmake",
                         f"-DANDROID_ABI={abi}", "-DANDROID_PLATFORM=android-24", "-DANDROID_STL=none",
                         "-DCMAKE_BUILD_TYPE=Release", f"-DJACKPAL_SOURCE={source.resolve().as_posix()}"]
            if ninja.is_file():
                configure.append(f"-DCMAKE_MAKE_PROGRAM={ninja.as_posix()}")
            run(*configure)
            run(args.cmake, "--build", build)
            for name in ("libjackpal-androidterm5.so", "libjackpal-termexec2.so"):
                path = build / name
                run(strip, "--strip-unneeded", path)
                data = path.read_bytes()
                entry = f"jni/{abi}/{name}"
                old_path = build / f"old-{name}"
                old_path.write_bytes(old.read(entry))
                exports = jni_exports(nm, path)
                if exports != jni_exports(nm, old_path):
                    raise ValueError(f"JNI export set changed: {entry}")
                minimum = alignment(data)
                if minimum < 16384:
                    raise ValueError(f"ELF alignment failed: {entry}")
                replacements[entry] = data
                records.append(dict(path=entry, sha256=sha(data), minLoadAlign=minimum, jniExports=exports))
        output = work / "libtermexec-release.aar"
        with zipfile.ZipFile(output, "w") as new:
            for entry in old.infolist():
                new.writestr(copy.copy(entry), replacements.get(entry.filename, old.read(entry)))
        with zipfile.ZipFile(output) as new:
            for name in old.namelist():
                if name not in replacements and old.read(name) != new.read(name):
                    raise ValueError(f"Non-native entry changed: {name}")
        java_digest = sha(old.read("classes.jar"))
    provenance = dict(repository=lock["repository"], commit=commit, ndkVersion=lock["ndkVersion"],
                      androidPlatform=24, classesJarSha256=java_digest,
                      aarSha256=sha(output.read_bytes()), libraries=records)
    receipt = work / "provenance.json"
    receipt.write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    if args.replace:
        shutil.copyfile(output, original)
        shutil.copyfile(receipt, HERE / "provenance.json")
    print(f"Verified {len(records)} JNI libraries; Java/resources unchanged. Output: {output}")


if __name__ == "__main__":
    main()
