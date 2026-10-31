"""Builds the fixtures of the loadDex and loadJar cases of RhinoClassSyntaxDeviceTest and prints them as Base64.

Usage: py -3.12 -I tools/rhino/build_class_fixtures.py [--build-tools DIR]

javac (--release 8) compiles tools/rhino/fixtures/*.java; RhinoClassDexFixture goes through d8 (--min-api 24) into a dex
file, RhinoClassJarFixture stays a jar, which the host dexes when it loads it. The class in the jar is marked as class
file version 51: API 24 and 25 dex it with legacy dx 1.7.0, which refuses version 52, and the fixture uses nothing of
Java 8. Paste the two strings into FIXTURE_DEX and FIXTURE_JAR of app/src/androidTest/assets/rhino/class-syntax-cases.js.
"""
import argparse
import base64
import os
import pathlib
import shutil
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "tools/rhino/fixtures"
PACKAGE = "org/autojs/rhino/fixture"
CLASS_FILE_VERSION = 51


def run(*command):
    subprocess.run([str(part) for part in command], check=True, capture_output=True, text=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--build-tools", default=os.path.join(os.environ.get("ANDROID_HOME", "E:/.android/sdk"), "build-tools/37.0.0"))
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as work:
        work = pathlib.Path(work)
        run("javac", "--release", "8", "-nowarn", "-d", work / "classes", *sorted(FIXTURES.glob("*.java")))
        (work / "dex").mkdir()
        d8 = shutil.which("d8", path=args.build_tools) or str(pathlib.Path(args.build_tools) / "d8.bat")
        run(d8, "--min-api", "24", "--output", work / "dex", work / f"classes/{PACKAGE}/RhinoClassDexFixture.class")
        with zipfile.ZipFile(work / "fixture.jar", "w", zipfile.ZIP_DEFLATED) as jar:
            data = bytearray((work / f"classes/{PACKAGE}/RhinoClassJarFixture.class").read_bytes())
            data[6:8] = CLASS_FILE_VERSION.to_bytes(2, "big")
            jar.writestr(f"{PACKAGE}/RhinoClassJarFixture.class", bytes(data))
        print("FIXTURE_DEX =", base64.b64encode((work / "dex/classes.dex").read_bytes()).decode())
        print("FIXTURE_JAR =", base64.b64encode((work / "fixture.jar").read_bytes()).decode())


if __name__ == "__main__":
    main()
