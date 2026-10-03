"""Collect controlled fixture task groups through the installed AutoJs6 accessibility service.

Run from tools/codegen-model with python -m capture.collect --help. No real applications are read.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import time
import uuid

from .join import AssociatedTarget, associate

FAMILIES = ("login", "list", "settings", "grid", "tabs", "dialog", "nested-scroll", "no-id")
MUTATIONS = ("shuffle", "insert", "delete", "retext", "locale", "strip-ids", "rename-ids", "wrap", "sibling")
FIXTURE = "org.autojs.test.codegen.fixture"
HOST = "org.autojs.autojs6"
SAFE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}\Z")


def safe_token(value: str) -> str:
    if not SAFE.fullmatch(value):
        raise ValueError(f"Unsafe identity: {value!r}")
    return value


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def save_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(json_bytes(value))
    temporary.replace(path)


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def mutation_plan(family: str, seed: int) -> tuple[tuple[str, ...], ...]:
    """Three states yield labels 0, 0.5, 1; every nine seeds cover every mutation.

    The first future state isolates one change; the second combines independent changes.
    ID removal and rename are never paired, and skipped changes are reported by the oracle.
    """
    offset = (seed + FAMILIES.index(family) * 2) % len(MUTATIONS)
    states = (), (MUTATIONS[offset],), (MUTATIONS[(offset + 3) % 9], MUTATIONS[(offset + 5) % 9])
    if family != "no-id":
        return states
    # An ID mutation on a page that already has no IDs would create an unchanged future state.
    choices = ("shuffle", "insert", "delete", "retext", "locale", "wrap", "sibling")
    return tuple(tuple(dict.fromkeys(choices[(offset + i) % len(choices)]
                                   if mutation in ("strip-ids", "rename-ids") else mutation
                                   for i, mutation in enumerate(mutations))) for mutations in states)


def default_action(target: AssociatedTarget, seed: int) -> str:
    capabilities = set(target.capabilities)
    if "set-text" in capabilities:
        return "set-text"
    if "scroll-forward" in capabilities:
        return "scroll-backward" if seed % 2 and "scroll-backward" in capabilities else "scroll-forward"
    if "long-click" in capabilities and seed % 3 == 1:
        return "long-click"
    return "click" if "click" in capabilities and seed % 3 != 2 else "none"


def make_tasks(family: str, seed: int, states: list[dict], joined: list[list[AssociatedTarget]]) -> list[dict]:
    if len(states) < 2 or len(joined) != len(states):
        raise ValueError("At least two complete associated states are required")
    keys = {item.key for item in joined[0]}
    if not keys or any({item.key for item in items} != keys for items in joined):
        raise ValueError("Every state must retain the same truth targets")
    maps = [{item.key: item for item in items} for items in joined]
    result = []
    for key in sorted(keys):
        safe_token(key)
        action = default_action(maps[0][key], seed)
        if action != "none" and any(action not in items[key].capabilities for items in maps):
            raise ValueError(f"Target capability changed across states: {key}")
        result.append({
            "format": 1, "groupId": f"{family}-{seed}-{key}", "source": "fixture", "app": FIXTURE,
            "screenFamily": family, "captureState": 0,
            "request": {"scope": "single", "attributes": ["id", "text", "desc"], "action": action, "receiver": "auto"},
            "states": [{"stateId": state["stateId"], "snapshot": state["snapshot"],
                        "mutations": state["appliedMutations"], "targets": [list(items[key].path)]}
                       for state, items in zip(states, maps)],
        })
    # A collection is one separate intent with all independently tagged targets; preserve every
    # target across states, including when a shared selector cannot express it (zero candidates).
    common = set.intersection(*(set(item.capabilities) for items in joined for item in items))
    collection_action = default_action(AssociatedTarget("all", (), tuple(sorted(common))), seed)
    result.append({
        "format": 1, "groupId": f"{family}-{seed}-collection", "source": "fixture", "app": FIXTURE,
        "screenFamily": family, "captureState": 0,
        "request": {"scope": "collection", "attributes": ["id", "text", "desc"],
                    "action": collection_action, "receiver": "auto"},
        "states": [{"stateId": state["stateId"], "snapshot": state["snapshot"],
                    "mutations": state["appliedMutations"], "targets": [list(items[key].path) for key in sorted(keys)]}
                   for state, items in zip(states, maps)],
    })
    return result


class Adb:
    def __init__(self, executable: str, serial: str):
        self.command = [executable, "-s", serial]

    def run(self, *args: str, check: bool = True, timeout: int = 30) -> bytes:
        proc = subprocess.run(self.command + list(args), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and proc.returncode:
            raise RuntimeError(f"adb {args[0]} failed: {proc.stderr.decode('utf-8', errors='replace').strip()}")
        return proc.stdout

    def shell(self, *args: str) -> str:
        # adb's remote shell reparses the command, even when subprocess itself uses an argv list.
        return self.run("shell", shlex.join(args)).decode("utf-8", errors="replace").strip()

    def private_json(self, name: str) -> dict | None:
        raw = self.run("exec-out", "run-as", FIXTURE, "cat", "files/" + name, check=False)
        try:
            value = json.loads(raw)
            return value if isinstance(value, dict) else None
        except (ValueError, UnicodeError):
            return None

    def remote_json(self, path: str) -> dict | None:
        raw = self.run("exec-out", "cat", path, check=False)
        try:
            value = json.loads(raw)
            return value if isinstance(value, dict) else None
        except (ValueError, UnicodeError):
            return None

    def push_json(self, path: str, value: dict) -> None:
        with tempfile.TemporaryDirectory(prefix="codegen-request-") as directory:
            local = Path(directory) / "request.json"
            local.write_bytes(json_bytes(value))
            self.run("push", str(local), path + ".tmp")
            self.shell("mv", path + ".tmp", path)


class CaptureWorker:
    def __init__(self, adb: Adb):
        self.adb = adb
        self.run_id = uuid.uuid4().hex
        self.control = f"/sdcard/Android/data/{HOST}/files/codegen-model/{self.run_id}"
        self.sequence = 0

    def start(self) -> None:
        self.adb.shell("mkdir", "-p", self.control)
        self.adb.push_json(self.control + "/config.json", {"format": 1, "runId": self.run_id, "fixturePackage": FIXTURE})
        self.adb.run("push", str(Path(__file__).with_name("capture.js")), self.control + "/capture.js")
        result = self.adb.shell("am", "start", "-W", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity",
                               "-a", "android.intent.action.VIEW", "-d", "file://" + self.control + "/capture.js")
        if "Error:" in result:
            raise RuntimeError(result)
        self.wait_json("worker-ready.json", {"runId": self.run_id}, timeout=20)

    def wait_json(self, name: str, expected: dict, timeout: int = 10) -> dict:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            result = self.adb.remote_json(self.control + "/" + name)
            if result and all(result.get(key) == value for key, value in expected.items()):
                return result
            time.sleep(0.05)
        raise RuntimeError(f"Timed out waiting for {name}: {expected}")

    def request(self, command: str, state_id: str = "stop") -> str:
        self.sequence += 1
        request_id = f"r{self.sequence}"
        self.adb.push_json(self.control + "/request.json", {
            "format": 1, "runId": self.run_id, "requestId": request_id, "stateId": safe_token(state_id), "command": command,
        })
        return request_id

    def capture(self, family: str, seed: int, mutations: tuple[str, ...], state_id: str, destination: Path) -> tuple[dict, dict]:
        result = self.adb.shell("am", "start", "-W", "--activity-clear-top", "--activity-single-top", "-n", FIXTURE + "/.MainActivity",
                               "--es", "screen", family, "--el", "seed", str(seed), "--es", "mutations", ",".join(mutations),
                               "--es", "stateId", safe_token(state_id), "--es", "runId", self.run_id)
        if "Error:" in result:
            raise RuntimeError(result)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            ready = self.adb.private_json("ready.json")
            if ready and ready.get("stateId") == state_id and ready.get("runId") == self.run_id:
                break
            error = self.adb.private_json("error.json")
            if error and error.get("stateId") == state_id and error.get("runId") == self.run_id:
                raise RuntimeError(f"Fixture rejected state: {error}")
            time.sleep(0.05)
        else:
            raise RuntimeError(f"Fixture readiness timed out: {state_id}")
        request_id = self.request("capture", state_id)
        response = self.wait_json("response.json", {"runId": self.run_id, "requestId": request_id, "stateId": state_id})
        if not response.get("ok"):
            raise RuntimeError(f"Host capture failed: {response.get('error')}")
        # The host resolves /sdcard to /storage/emulated/0. Never pull a path supplied by arbitrary response data.
        if response.get("packageName") != FIXTURE or not str(response.get("snapshot", "")).endswith(f"/files/codegen-model/{self.run_id}/snapshot.layout.json"):
            raise RuntimeError("Unexpected capture response")
        destination.parent.mkdir(parents=True, exist_ok=True)
        self.adb.run("pull", self.control + "/snapshot.layout.json", str(destination))
        truth = self.adb.private_json("truth/" + state_id + ".json")
        if not truth or truth.get("stateId") != state_id or truth.get("screenFamily") != family or truth.get("seed") != seed:
            raise RuntimeError("Truth identity does not match the requested state")
        if truth.get("mutations") != list(mutations):
            raise RuntimeError("Truth mutations do not match the requested state")
        return read_json(destination), truth

    def stop(self) -> None:
        self.request("stop")
        self.wait_json("worker-stopped.json", {"runId": self.run_id, "requested": True}, timeout=5)


def collect(args: argparse.Namespace) -> dict:
    safe_token(args.dataset_id)
    families = args.families.split(",")
    if not families or len(set(families)) != len(families) or any(f not in FAMILIES for f in families):
        raise ValueError("Use distinct known fixture families")
    if not 1 <= args.seeds <= 100000 or not -(2**63) <= args.first_seed <= args.first_seed + args.seeds - 1 < 2**63:
        raise ValueError("Invalid seed range")
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    adb = Adb(args.adb, args.serial)
    worker = CaptureWorker(adb)
    fixture_dump = adb.shell("dumpsys", "package", FIXTURE)
    version = re.search(r"versionCode=(\d+)", fixture_dump)
    if not version:
        raise ValueError("Install the debug codegen-fixture APK first")
    generator = args.generator_commit or subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=Path(__file__).resolve().parents[3], text=True).strip()
    device = {"serial": args.serial, "model": adb.shell("getprop", "ro.product.model"),
              "api": int(adb.shell("getprop", "ro.build.version.sdk"))}
    plan = {"families": families, "firstSeed": args.first_seed, "seeds": args.seeds, "statesPerGroup": 3}
    manifest_file = output / "collection.json"
    manifest = read_json(manifest_file) if manifest_file.exists() else {
        "format": 1, "datasetId": args.dataset_id, "generatorCommit": generator,
        "fixtureVersionCode": int(version[1]), "devices": [device], "plan": plan,
        "startedAt": utc_now(), "completedAt": None, "elapsedSeconds": 0.0,
        "attemptedStates": 0, "acceptedStates": 0, "discardedStates": 0,
        "discardRate": 0.0, "groups": 0, "scenarios": [], "failures": [], "pendingState": None,
    }
    for name, expected in (("datasetId", args.dataset_id), ("generatorCommit", generator), ("plan", plan),
                           ("fixtureVersionCode", int(version[1])), ("devices", [device])):
        if manifest.get(name) != expected:
            raise ValueError(f"Cannot resume with changed {name}; choose a new output directory")
    start = time.monotonic()
    earlier_elapsed = manifest["elapsedSeconds"]
    completed = set(manifest["scenarios"])
    consecutive_failures = 0
    # Pending tasks live outside the exporter input root. The complete scenario directory is
    # published in one rename, never alongside snapshots that a resumed capture can overwrite.
    staging = output.parent / f".{output.name}.pending-{worker.run_id}"
    staging.mkdir()
    if manifest.get("pendingState"):
        manifest["discardedStates"] += 1
        manifest["failures"].append({**manifest["pendingState"], "reason": "Interrupted before state completion"})
        manifest["pendingState"] = None

    def checkpoint() -> None:
        manifest["elapsedSeconds"] = round(earlier_elapsed + time.monotonic() - start, 3)
        manifest["discardRate"] = manifest["discardedStates"] / max(1, manifest["attemptedStates"])
        save_json(manifest_file, manifest)

    try:
        worker.start()
        for family in families:
            for seed in range(args.first_seed, args.first_seed + args.seeds):
                scenario = f"{family}-{seed}"
                if scenario in completed:
                    continue
                destination = output / "groups" / scenario
                if destination.exists():
                    # A process can stop after the directory rename but before the manifest
                    # checkpoint. Retain that unpublished scenario outside the exporter root.
                    if destination.is_symlink() or destination.resolve().parent != (output / "groups").resolve():
                        raise ValueError("Scenario destination must stay under the dataset groups directory")
                    retained = staging / (scenario + ".uncheckpointed")
                    destination.rename(retained)
                    manifest.setdefault("quarantinedScenarios", []).append(str(retained))
                directory = staging / scenario
                states, joined = [], []
                rejected = False
                for index, mutations in enumerate(mutation_plan(family, seed)):
                    state_id = f"{scenario}-s{index}"
                    snapshot_file = directory / f"s{index}.layout.json"
                    manifest["attemptedStates"] += 1
                    manifest["pendingState"] = {"stateId": state_id, "mutations": list(mutations)}
                    checkpoint()
                    try:
                        snapshot, truth = worker.capture(family, seed, mutations, state_id, snapshot_file)
                        save_json(directory / f"s{index}.truth.json", truth)
                        targets = associate(snapshot, truth)
                        if len(targets) != 6:
                            raise ValueError(f"Expected six independent targets, got {len(targets)}")
                        joined.append(targets)
                        states.append({"stateId": state_id, "snapshot": snapshot_file.name,
                                       "appliedMutations": truth["appliedMutations"]})
                    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
                        manifest["discardedStates"] += 1
                        manifest["pendingState"] = None
                        manifest["failures"].append({"stateId": state_id, "mutations": list(mutations), "reason": str(error)})
                        consecutive_failures += 1
                        rejected = True
                        checkpoint()
                        if isinstance(error, KeyboardInterrupt):
                            raise
                        print(f"REJECT {state_id}: {error}", flush=True)
                        if consecutive_failures >= 3:
                            raise RuntimeError("Three consecutive rejected states; fix capture/association before resuming") from error
                    else:
                        manifest["acceptedStates"] += 1
                        manifest["pendingState"] = None
                        checkpoint()
                        consecutive_failures = 0
                if not rejected:
                    tasks = make_tasks(family, seed, states, joined)
                    for task in tasks:
                        save_json(directory / (safe_token(task["groupId"]) + ".cgtask.json"), task)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    directory.rename(destination)
                    manifest["groups"] += len(tasks)
                # Failed scenarios are recorded, never silently retried to improve the discard rate.
                manifest["scenarios"].append(scenario)
                checkpoint()
                print(f"{scenario}: groups={manifest['groups']}, states={manifest['acceptedStates']}/{manifest['attemptedStates']}, "
                      f"discard={manifest['discardRate']:.2%}, elapsed={manifest['elapsedSeconds']:.1f}s", flush=True)
        manifest["completedAt"] = utc_now()
    finally:
        checkpoint()
        try:
            worker.stop()
        except (RuntimeError, subprocess.SubprocessError) as error:
            print(f"Worker stop not confirmed (it expires after 180s idle): {error}", flush=True)
        if not any(staging.iterdir()):
            staging.rmdir()
    if manifest["discardRate"] > 0.02:
        raise RuntimeError("Discard rate exceeds 2%; fix association before using this dataset")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True, help="Explicit authorized device; no implicit first-device selection")
    parser.add_argument("--output", required=True)
    parser.add_argument("--dataset-id", required=True)
    parser.add_argument("--families", default=",".join(FAMILIES))
    parser.add_argument("--first-seed", type=int, default=0)
    parser.add_argument("--seeds", type=int, default=48)
    parser.add_argument("--generator-commit", help="Override only when collecting from a known build/source checkout")
    args = parser.parse_args()
    result = collect(args)
    print(f"Completed {result['groups']} groups in {result['elapsedSeconds']:.1f}s; discard {result['discardRate']:.2%}")


if __name__ == "__main__":
    main()
