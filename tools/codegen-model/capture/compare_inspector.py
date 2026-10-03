"""Compare a real layout-inspector menu capture with the collection worker on one fixture page."""
from __future__ import annotations

import argparse
from pathlib import Path

from .collect import Adb, CaptureWorker, FAMILIES, read_json, save_json
from .join import flatten


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--screen", choices=FAMILIES, default="list")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    worker = CaptureWorker(Adb(args.adb, args.serial))
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)

    def command(name: str) -> dict:
        request = worker.request(name, "inspector-comparison")
        response = worker.wait_json("response.json", {"runId": worker.run_id, "requestId": request})
        if not response.get("ok"):
            raise RuntimeError(response.get("error", "Inspector request failed"))
        return response

    try:
        worker.start()
        worker.capture(args.screen, 42, (), "inspector-comparison", output / "worker.layout.json")
        command("prepare-inspector")
        input("Open AutoJs6's circular menu on the device, tap layout inspection, then close the inspector. Press Enter here after the capture: ")
        command("read-inspector")
        worker.adb.run("pull", worker.control + "/inspector.layout.json", str(output / "inspector.layout.json"))
        actual = [node.get("className") for _, node in flatten(read_json(output / "worker.layout.json"))]
        expected = [node.get("className") for _, node in flatten(read_json(output / "inspector.layout.json"))]
        report = {"format": 1, "serial": args.serial, "screenFamily": args.screen,
                  "workerNodeCount": len(actual), "inspectorNodeCount": len(expected), "equalPreorderClasses": actual == expected}
        save_json(output / "comparison.json", report)
        print(report)
        if actual != expected:
            raise RuntimeError("Worker differs from the real inspector UI capture")
    finally:
        worker.stop()


if __name__ == "__main__":
    main()
