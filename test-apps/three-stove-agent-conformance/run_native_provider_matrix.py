"""Exercise installed real host/Agent/fake Provider with a fresh host process per fault case.

Requires same-test-signer APKs, including host androidTest. Does not install, sign or
replace applications. Evidence is private; print only case names and pass counts.
"""
import argparse
import pathlib
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()

    def adb(*parts, timeout=60):
        return subprocess.run([args.adb, "-s", args.serial, *parts], capture_output=True,
                              timeout=timeout, check=True).stdout.decode("utf-8", "replace")

    if not args.serial.startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise SystemExit("This matrix requires a disposable emulator")
    if not adb("emu", "avd", "name").splitlines()[0].startswith("AI_Agent_Conformance_"):
        raise SystemExit("Use an AI_Agent_Conformance_* AVD with its own data directory")
    host = adb("shell", "dumpsys", "package", "org.autojs.autojs6")
    if "versionName=" not in host or "versionName=conformance" in host:
        raise SystemExit("Install the real host, not the fake-host fixture")
    args.output.mkdir(parents=True, exist_ok=False)
    source = pathlib.Path(__file__).resolve().parents[2] / "app/src/androidTest/java/org/autojs/autojs/core/plugin/agent"
    total = 0
    for name in ("ThreeStoveAgentNativeProviderRoundTripTest", "ThreeStoveAgentModelBrokerAndroidTest"):
        methods = re.findall(r"@Test\s+fun\s+(\w+)\(", (source / f"{name}.kt").read_text(encoding="utf-8"))
        if not methods or len(methods) != len(set(methods)):
            raise RuntimeError("Missing or duplicate test methods")
        for method in methods:
            # Instrumentation starts a fresh host process. Deadline/hostile tests may deliberately
            # fuse a Provider for that process; sharing it makes later healthy cases order-dependent.
            output = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                         f"org.autojs.autojs.core.plugin.agent.{name}#{method}",
                         "-e", "autojs.agent.nativeProvider", "true",
                         "-e", "autojs.threeStoveAgent.model.conformance.enabled", "true",
                         "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner", timeout=90)
            (args.output / f"{name}-{method}.txt").write_text(output, encoding="utf-8")
            if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE) or re.search(
                    r"^(?:FAILURES!!!|INSTRUMENTATION_FAILED:|INSTRUMENTATION_ABORTED:)", output, re.MULTILINE):
                raise RuntimeError(f"Failed {name}#{method}; inspect its evidence file")
            total += 1
            print(f"PASS {name}#{method}", flush=True)
    print(f"PASS {total}/{total} on {args.serial}", flush=True)


if __name__ == "__main__":
    main()
