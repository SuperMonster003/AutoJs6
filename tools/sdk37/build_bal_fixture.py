"""Build a disposable, independently signed BAL fixture without Gradle or network access."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allow-cross-uid-from-below", choices=("default", "true", "false"), default="default",
                        help="Application manifest opt-in for ASM; false enables protection, true permits switches")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    source = Path(__file__).resolve().parent / "fixtures/bal"
    build_tools = args.sdk.resolve() / "build-tools/37.0.0"
    android = args.sdk.resolve() / "platforms/android-37/android.jar"
    if not android.exists():
        android = args.sdk.resolve() / "platforms/android-37.0/android.jar"
    env = dict(os.environ, JAVA_HOME=str(args.jdk.resolve()))
    exe = ".exe" if os.name == "nt" else ""
    bat = ".bat" if os.name == "nt" else ""

    def run(*command):
        subprocess.run([str(c) for c in command], env=env, check=True)

    classes, dex = output / "classes", output / "dex"
    classes.mkdir()
    dex.mkdir()
    run(args.jdk / f"bin/javac{exe}", "--release", "8", "-classpath", android,
        "-d", classes, *sorted(source.glob("*.java")))
    run(build_tools / f"d8{bat}", "--min-api", "24", "--lib", android, "--output", dex,
        *sorted(classes.rglob("*.class")))
    unsigned, apk = output / "unsigned.apk", output / "bal-fixture.apk"
    manifest = (source / "AndroidManifest.xml").read_text(encoding="utf-8")
    if args.allow_cross_uid_from_below != "default":
        manifest = manifest.replace("<application ", "<application android:allowCrossUidActivitySwitchFromBelow=\""
                                    + args.allow_cross_uid_from_below + "\" ", 1)
    (output / "AndroidManifest.xml").write_text(manifest, encoding="utf-8")
    run(build_tools / f"aapt2{exe}", "link", "--manifest", output / "AndroidManifest.xml",
        "-I", android, "-o", unsigned)
    with zipfile.ZipFile(unsigned, "a") as archive:
        archive.write(dex / "classes.dex", "classes.dex")
    aligned = output / "aligned.apk"
    run(build_tools / f"zipalign{exe}", "-f", "4", unsigned, aligned)
    key = output / "fixture.keystore"
    run(args.jdk / f"bin/keytool{exe}", "-genkeypair", "-keystore", key, "-storepass", "android",
        "-keypass", "android", "-alias", "fixture", "-keyalg", "RSA", "-validity", "30",
        "-dname", "CN=SDK37 disposable fixture", "-noprompt")
    run(build_tools / f"apksigner{bat}", "sign", "--ks", key, "--ks-pass", "pass:android",
        "--key-pass", "pass:android", "--out", apk, aligned)
    run(build_tools / f"apksigner{bat}", "verify", apk)
    result = {"apk": str(apk), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
              "allowCrossUidActivitySwitchFromBelow": args.allow_cross_uid_from_below}
    (output / "build.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
