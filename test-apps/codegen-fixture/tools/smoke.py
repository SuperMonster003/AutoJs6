"""Verify repeatable oracle bytes and action receipts on one explicit test device.

Only the controlled fixture is force-stopped, launched, or acted upon. The installed host
executes its existing selector APIs; this tool never creates a UiAutomation connection.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import time
import uuid

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "tools" / "codegen-model"))
from capture.collect import Adb, CaptureWorker, FAMILIES, FIXTURE, HOST, mutation_plan, save_json  # noqa: E402
from capture.join import associate  # noqa: E402


def private_bytes(adb: Adb, name: str) -> bytes:
    return adb.run("exec-out", "run-as", FIXTURE, "cat", "files/" + name)


def wait_remote(adb: Adb, path: str, run_id: str, timeout: int = 20) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = adb.remote_json(path)
        if value and value.get("runId") == run_id:
            return value
        time.sleep(0.05)
    raise RuntimeError(f"Timed out waiting for {path}")


def assert_receipts(before: bytes, after: bytes, state_id: str, actions: list[dict]) -> list[dict]:
    if not after.startswith(before):
        raise AssertionError("Receipt history was replaced during the test")
    receipts = [json.loads(line) for line in after[len(before):].splitlines() if line]
    results = []
    for action in actions:
        matching = [item for item in receipts if item.get("stateId") == state_id
                    and item.get("key") == action["key"] and item.get("action") == action["action"]]
        if not matching:
            raise AssertionError(f"No receipt for {action['key']} {action['action']}")
        if action["action"] == "set-text" and matching[-1].get("args", {}).get("text") != action["text"]:
            raise AssertionError("Text receipt differs from the requested escaped string")
        if action["action"] in {"scroll-forward", "scroll-backward"}:
            sign = 1 if action["action"] == "scroll-forward" else -1
            if not any((item["args"]["y"] - item["args"]["oldY"]) * sign > 0 for item in matching):
                raise AssertionError("Scroll receipt does not show movement in the requested direction")
        elif len(matching) != 1:
            raise AssertionError(f"Expected one action receipt, received {len(matching)}")
        results.append({"key": action["key"], "action": action["action"], "receipts": len(matching)})
    return results


def check_actions(adb: Adb, worker: CaptureWorker, output: Path, family: str, actions: list[dict]) -> dict:
    run_id = uuid.uuid4().hex
    control = f"/sdcard/Android/data/{HOST}/files/codegen-fixture-smoke"
    state_id = "smoke-actions-" + family
    # Start a gated host script first. Launching RunIntentActivity must finish before the
    # fixture is brought forward; no arbitrary sleep can establish that ordering reliably.
    adb.shell("mkdir", "-p", control)
    adb.run("push", str(Path(__file__).with_name("smoke_actions.js")), control + "/smoke_actions.js")
    adb.push_json(control + "/config.json", {"format": 1, "runId": run_id})
    result = adb.shell("am", "start", "-W", "-n", HOST + "/org.autojs.autojs.external.open.RunIntentActivity",
                       "-a", "android.intent.action.VIEW", "-d", "file://" + control + "/smoke_actions.js")
    if "Error:" in result:
        raise RuntimeError(result)
    wait_remote(adb, control + "/ready.json", run_id)
    snapshot, truth = worker.capture(family, 17, (), state_id, output / (family + "-actions.layout.json"))
    associate(snapshot, truth)
    targets = {target["key"]: target for target in truth["targets"]}
    requests = []
    for action in actions:
        target = targets[action["key"]]
        if action["action"] not in target["capabilities"]:
            raise AssertionError("Fixture does not declare the requested capability")
        requests.append({**action, "bounds": target["bounds"], "className": target["className"]})
    before = private_bytes(adb, "receipts.jsonl")
    adb.push_json(control + "/go.json", {"runId": run_id, "stateId": state_id, "actions": requests})
    response = wait_remote(adb, control + "/result.json", run_id)
    if not response.get("ok"):
        raise AssertionError(f"Host action failed: {response.get('error')}")
    after = private_bytes(adb, "receipts.jsonl")
    (output / (family + "-receipts.jsonl")).write_bytes(after[len(before):])
    return {"family": family, "stateId": state_id, "actions": assert_receipts(before, after, state_id, actions)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    adb = Adb(args.adb, args.serial)
    worker = CaptureWorker(adb)
    report = {"format": 1, "serial": args.serial, "model": adb.shell("getprop", "ro.product.model"),
              "api": int(adb.shell("getprop", "ro.build.version.sdk")), "determinism": [], "receipts": [], "ok": False}
    started = time.monotonic()
    try:
        worker.start()
        for index, family in enumerate(FAMILIES):
            state_id = "smoke-repeat-" + family
            mutations = mutation_plan(family, index)[2]
            runs = []
            for repeat in range(2):
                # Remove only the named readiness file, preventing stale same-state readiness.
                # A second process launch proves determinism independently of retained Views.
                adb.shell("am", "force-stop", FIXTURE)
                adb.shell("run-as", FIXTURE, "rm", "-f", "files/ready.json")
                snapshot, truth = worker.capture(family, index, mutations, state_id,
                                                  output / f"{family}-{repeat}.layout.json")
                if len(associate(snapshot, truth)) != 6:
                    raise AssertionError("All six targets must associate on every launch")
                runs.append(private_bytes(adb, "truth/" + state_id + ".json"))
            if runs[0] != runs[1]:
                raise AssertionError(f"Truth changed byte-for-byte on repeated {family} launch")
            report["determinism"].append({"family": family, "seed": index, "mutations": list(mutations),
                                          "targets": 6, "sha256": hashlib.sha256(runs[0]).hexdigest()})
            print(f"PASS deterministic truth: {family}", flush=True)
        report["receipts"].append(check_actions(adb, worker, output, "login", [
            {"key": "login.help", "action": "click"},
            {"key": "login.help", "action": "long-click"},
            {"key": "login.email", "action": "set-text", "text": 'a"b\\c\nend'},
        ]))
        report["receipts"].append(check_actions(adb, worker, output, "nested-scroll", [
            {"key": "nested-scroll.news", "action": "scroll-forward"},
            {"key": "nested-scroll.news", "action": "scroll-backward"},
        ]))
        report["ok"] = True
    finally:
        report["elapsedSeconds"] = round(time.monotonic() - started, 3)
        save_json(output / "report.json", report)
        try:
            worker.stop()
        except (RuntimeError, TimeoutError) as error:
            print(f"Capture worker stop was not confirmed: {error}", flush=True)
    print(f"PASS: eight repeatable families and five receipt actions in {report['elapsedSeconds']:.1f}s", flush=True)


if __name__ == "__main__":
    main()
