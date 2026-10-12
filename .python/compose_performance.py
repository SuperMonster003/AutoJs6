#!/usr/bin/env python3
"""Collect and summarize opt-in Compose P5.3 device observations.

Examples:
  py .python/compose_performance.py run --serial emulator-5560 --output-dir build/compose-performance/api24
  py .python/compose_performance.py summarize build/compose-performance/api24/*-records.json --output build/compose-performance/summary.json --plugin-apk <release.apk>
  py .python/compose_performance.py run --serial emulator-5560 --sections list-updates --output-dir build/compose-performance/f6

Install the isolated spike host, its test APK and the matching release plugin first.
Grant the isolated host overlay permission and unlock the device. The runner stops
only the explicitly named .compose.spike host before each instrumentation invocation.
Cold means a fresh host process, not cleared disk/page/dex caches. Warm samples use
new script engines after same-process warmups. No screenshot images are saved.
The opt-in list-updates section (roadmap F.6) compares one visible state change on a
mounted 1000-row tree between whole-tree render, keyed compose.memo rows and direct handles.

Visibility numbers include tree queries, two display callbacks and screenshot
capture. The color swatch is rendered in the same tree as the observed value. This
is a reproducible visibility confirmation upper bound, not a presentation fence.
FrameMetrics deadline/period excess is a rendering-jank estimate. Android's metric
report drop count is NOT a count of display frames dropped. Public API definitions:
https://developer.android.com/reference/android/view/FrameMetrics
https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener
https://developer.android.com/reference/android/os/Debug.MemoryInfo
"""

from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime, timezone
import glob
import hashlib
import json
import math
from pathlib import Path
import re
import statistics
import struct
import subprocess
import sys
import uuid
import zipfile
import zlib

TEST_CLASS = "org.autojs.autojs.runtime.api.augment.compose.ComposePerformanceDeviceTest"
STATUS_PREFIX = "INSTRUMENTATION_STATUS: compose_perf_json="
METHODS = {
    "cold": "coldUiFirstFrame",
    "warm": "warmUiFirstFrames",
    "updates": "stateUpdatesAndBatchedRenders",
    "list": "thousandItemListFirstFrameAndScrollFrames",
    "memory": "hostPssAcrossFreshLoaderAndOwnedSessions",
    # F.6 decision input; not part of the default P5.3 baseline sections.
    "list-updates": "thousandItemListStateUpdates",
}
LIST_UPDATE_METRICS = ("list_render_update", "list_memo_update", "list_handle_update")
LATENCY_METRICS = {"first_frame", "state_update", "batch_100", "list_first_frame", *LIST_UPDATE_METRICS}
PSS_STAGES = (
    "instrumented_host_before_script", "empty_native_ui_engine", "after_empty_ui_close",
    "first_compose_ui", "after_compose_ui_close", "warm_compose_floaty", "after_floaty_close",
    "warm_compose_1000_item_list", "after_list_close",
)


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    if not ordered or not 0 <= fraction <= 1:
        raise ValueError("A percentile requires values and a fraction in [0, 1]")
    position = (len(ordered) - 1) * fraction
    low = math.floor(position)
    high = math.ceil(position)
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def describe(values: list[float]) -> dict:
    if not values or any(not math.isfinite(value) for value in values):
        raise ValueError("Finite samples are required")
    return {
        "count": len(values), "min": min(values), "median": statistics.median(values),
        "p90": percentile(values, 0.9), "max": max(values),
        "mean": statistics.mean(values),
        "percentileDefinition": "linear interpolation at (n - 1) * p; small samples are descriptive only",
    }


def validate_record(record: dict) -> None:
    if record.get("schemaVersion") != 1 or record.get("suite") != "compose-ui-p5.3":
        raise ValueError("Unsupported performance record")
    required = ("metric", "sample", "runId", "pid", "api", "device", "host", "plugin", "fields")
    if any(key not in record for key in required) or not isinstance(record["fields"], dict):
        raise ValueError("Incomplete performance record")
    if type(record["sample"]) is not int or record["sample"] < 0 or type(record["pid"]) is not int or record["pid"] <= 0:
        raise ValueError("Sample indexes and process IDs must be valid integers")
    if not record["host"]["package"].endswith(".compose.spike"):
        raise ValueError("Record does not come from the isolated spike host")
    if record["metric"] not in LATENCY_METRICS | {"list_scroll", "host_pss"}:
        raise ValueError("Unknown performance metric")
    fields = record["fields"]
    if record["metric"] in LATENCY_METRICS:
        for key in ("toSemanticsNs", "toPixelConfirmedNs", "semanticsQueryNs", "twoDisplayCallbacksNs", "screenshotNs"):
            if not isinstance(fields.get(key), (int, float)) or fields[key] < 0:
                raise ValueError(f"Missing or invalid timing: {key}")
        if not fields.get("pixelConfirmed") or fields["toPixelConfirmedNs"] < fields["toSemanticsNs"]:
            raise ValueError("A latency sample must include successful pixel confirmation after semantics")


def parse_instrumentation(text: str) -> list[dict]:
    if any(token in text for token in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg=")):
        raise ValueError("Instrumentation failed; retain the raw log for diagnosis")
    if not re.search(r"\bOK\s*\(\s*1\s+tests?\s*\)", text):
        raise ValueError("Exactly one successful benchmark entry was expected")
    records = [json.loads(line[len(STATUS_PREFIX):]) for line in text.splitlines() if line.startswith(STATUS_PREFIX)]
    if not records:
        raise ValueError("No measurements were returned; an opt-in assumption may have skipped the test")
    for record in records:
        validate_record(record)
    return records


def sample_identity(record: dict) -> tuple:
    fields = record["fields"]
    return (record["metric"], fields.get("surface", ""), fields.get("temperature", ""),
            fields.get("stage", ""), record["sample"])


def validate_invocation(records: list[dict], section: str, index: int, samples: int,
                        memory_samples: int, run_id: str) -> None:
    """A successful JUnit entry must return every requested measurement exactly once."""
    for record in records:
        validate_record(record)
        if record["runId"] != run_id:
            raise ValueError("Measurement run ID does not match the requested invocation")
    if len({record["pid"] for record in records}) != 1:
        raise ValueError("One invocation must contain exactly one measured process")
    if section == "cold":
        expected = {("first_frame", "ui", "process_cold_first_compose_mount", "", index)}
    elif section == "warm":
        expected = {("first_frame", "ui", "same_process_new_engine_after_warmups", "", i) for i in range(samples)}
    elif section == "updates":
        expected = {(metric, surface, "", "", i) for metric in ("state_update", "batch_100")
                    for surface in ("ui", "floaty") for i in range(samples)}
    elif section == "list":
        expected = {("list_first_frame", "ui", "same_process_new_engine_after_list_warmups", "", i) for i in range(samples)}
        expected |= {("list_scroll", "", "", "", i) for i in range(samples)}
    elif section == "memory":
        expected = {("host_pss", "", "", stage, i) for stage in PSS_STAGES for i in range(memory_samples)}
    elif section == "list-updates":
        expected = {(metric, "ui", "", "", i) for metric in LIST_UPDATE_METRICS for i in range(samples)}
    else:
        raise ValueError(f"Unknown invocation section: {section}")
    actual = [sample_identity(record) for record in records]
    if len(actual) != len(set(actual)):
        raise ValueError(f"Duplicate measurements in {section}")
    missing = expected - set(actual)
    extra = set(actual) - expected
    if missing or extra:
        raise ValueError(f"Incomplete or unexpected {section} measurements: missing={sorted(missing)}, extra={sorted(extra)}")


def validate_run(data: dict) -> None:
    """Recheck preserved files, including runs produced by an earlier loaded runner."""
    if data.get("schemaVersion") != 1 or data.get("failedInvocation"):
        raise ValueError("An incomplete or unsupported run cannot be summarized")
    metadata = data["metadata"]
    sections = metadata["sections"]
    if not sections or len(sections) != len(set(sections)) or any(section not in METHODS for section in sections):
        raise ValueError("Invalid requested sections in the run metadata")
    samples = metadata["samples"]
    cold_samples = metadata["coldSamples"]
    # Schema v1's initial runner omitted this key and always used the default of five
    # for this campaign. All new runs persist the explicit requested value.
    memory_samples = metadata.get("memorySamples", 5)
    for name, value, maximum in (("samples", samples, 50), ("coldSamples", cold_samples, 10), ("memorySamples", memory_samples, 20)):
        if type(value) is not int or not 1 <= value <= maximum:
            raise ValueError(f"Invalid requested {name}")
    expected = [(section, index) for section in sections for index in range(cold_samples if section == "cold" else 1)]
    labels = [f"{section}-{index}" for section, index in expected]
    if data["completed"] != labels:
        raise ValueError("Completed invocations do not match the requested run")
    grouped = defaultdict(list)
    for record in data["records"]:
        if record.get("serial") != metadata["serial"]:
            raise ValueError("A record's serial does not match its run metadata")
        grouped[record.get("invocation")].append(record)
    if set(grouped) != set(labels):
        raise ValueError("Recorded invocations do not match the requested run")
    cold_pids = []
    for section, index in expected:
        records = grouped[f"{section}-{index}"]
        validate_invocation(records, section, index, samples, memory_samples, metadata["runId"])
        if section == "cold":
            cold_pids.append(records[0]["pid"])
    if len(cold_pids) != len(set(cold_pids)):
        raise ValueError("Process-cold samples reused a PID; fresh-process evidence is not established")


def frame_summary(fields: dict) -> dict:
    refresh = float(fields["refreshRateHz"])
    if not math.isfinite(refresh) or refresh <= 0:
        raise ValueError("A positive display refresh rate is required")
    period = 1_000_000_000 / refresh
    all_frames = fields["frames"]
    frames = [frame for frame in all_frames if not frame["firstDraw"] and frame["totalNs"] >= 0]
    if not frames:
        raise ValueError("No usable non-first-draw frame metrics were recorded")
    totals = [frame["totalNs"] for frame in frames]
    missed_deadline = sum(frame["totalNs"] >= frame["deadlineNs"] for frame in frames if frame["deadlineNs"] > 0)
    with_deadline = sum(frame["deadlineNs"] > 0 for frame in frames)
    over_period = sum(total > period for total in totals)
    # This is an estimate from render duration, never a count from a presentation trace.
    excess_periods = sum(max(0, math.ceil(total / period) - 1) for total in totals)
    return {
        "framesMeasured": len(frames), "firstDrawFramesExcluded": sum(frame["firstDraw"] for frame in all_frames),
        "refreshPeriodMs": period / 1_000_000, "renderAndIssueMs": describe([total / 1_000_000 for total in totals]),
        "renderOverRefreshPeriodCount": over_period, "renderOverRefreshPeriodPercent": 100 * over_period / len(frames),
        "estimatedExcessRefreshPeriods": excess_periods,
        "framesWithSystemDeadline": with_deadline, "systemDeadlineMissCount": missed_deadline,
        "systemDeadlineMissPercent": 100 * missed_deadline / with_deadline if with_deadline else None,
        "metricReportsDropped": sum(frame["metricReportsDropped"] for frame in all_frames),
        "collectionOverflow": fields["collectionOverflow"],
        "presentationDropCount": None,
        "presentationDropNote": "Not measured. Deadline or refresh-period excess estimates render jank; lost metric reports are separate.",
    }


def summarize(records: list[dict]) -> dict:
    groups = defaultdict(list)
    seen = set()
    for record in records:
        validate_record(record)
        identity = (record.get("serial", "unrecorded"), record["device"], record["api"], record["runId"], record["pid"], sample_identity(record))
        if identity in seen:
            raise ValueError("Duplicate measurement across input files")
        seen.add(identity)
        fields = record["fields"]
        key = (record.get("serial", "unrecorded"), record["device"], record["api"], record["host"]["versionCode"],
               record["plugin"]["versionCode"], record["metric"], fields.get("surface", ""),
               fields.get("temperature", ""), fields.get("stage", ""))
        groups[key].append(record)
    output = []
    for key, samples in sorted(groups.items()):
        serial, device, api, host_build, plugin_build, metric, surface, temperature, stage = key
        group = {
            "serial": serial, "device": device, "api": api, "hostBuild": host_build, "pluginBuild": plugin_build,
            "metric": metric, "surface": surface, "temperature": temperature, "stage": stage,
            "sampleCount": len(samples), "pids": sorted({sample["pid"] for sample in samples}), "statistics": {},
        }
        if metric == "list_scroll":
            group["traces"] = [frame_summary(sample["fields"]) for sample in samples]
        else:
            numeric_keys = sorted(set.intersection(*[
                {name for name, value in sample["fields"].items() if isinstance(value, (float, int)) and not isinstance(value, bool)
                 and not name.endswith("ElapsedNs") and name not in {"startElapsedNs"}}
                for sample in samples
            ]))
            for name in numeric_keys:
                divisor = 1_000_000 if name.endswith("Ns") else 1
                public_name = name[:-2] + "Ms" if name.endswith("Ns") else name
                group["statistics"][public_name] = describe([sample["fields"][name] / divisor for sample in samples])
        output.append(group)

    memory_groups = defaultdict(lambda: defaultdict(list))
    for record in records:
        if record["metric"] == "host_pss":
            key = (record.get("serial", "unrecorded"), record["device"], record["api"], record["host"]["versionCode"],
                   record["plugin"]["versionCode"], record["runId"], record["pid"])
            memory_groups[key][record["fields"]["stage"]].append(record["fields"]["totalPssKb"])
    deltas = []
    delta_groups = defaultdict(lambda: defaultdict(list))
    for key, stages in sorted(memory_groups.items()):
        baseline = stages.get("empty_native_ui_engine")
        if not baseline:
            raise ValueError(f"PSS process {key[-2:]} has no matching native UI baseline")
        baseline_pss = statistics.median(baseline)
        differences = {stage: statistics.median(values) - baseline_pss for stage, values in stages.items()}
        deltas.append({"serial": key[0], "device": key[1], "api": key[2], "hostBuild": key[3], "pluginBuild": key[4],
                       "runId": key[5], "pid": key[6],
                       "baseline": "empty_native_ui_engine", "baselineMedianPssKb": baseline_pss,
                       "stageSampleCounts": {stage: len(values) for stage, values in stages.items()},
                       "stageMedianDeltaKb": differences,
                       "interpretation": "Whole process observations without forced GC. Retained loader/JIT/engine allocations are included; this is not retained-leak attribution."})
        for stage, value in differences.items():
            delta_groups[key[:5]][stage].append(value)
    delta_statistics = [
        {"serial": key[0], "device": key[1], "api": key[2], "hostBuild": key[3], "pluginBuild": key[4],
         "stagePairedDeltaKb": {stage: describe(values) for stage, values in stages.items()},
         "pairing": "Each stage median minus the native UI baseline median from the same runId and PID, then summarized across processes."}
        for key, stages in sorted(delta_groups.items())
    ]

    candidates = []
    for group in output:
        if group["sampleCount"] < 5:
            continue
        timing = group["statistics"].get("toPixelConfirmedMs")
        if timing:
            candidates.append({"serial": group["serial"], "metric": group["metric"], "surface": group["surface"],
                               "temperature": group["temperature"], "basisP90Ms": timing["p90"],
                               "candidateVisibilityConfirmationMs": math.ceil(timing["p90"] * 1.5 / 5) * 5,
                               "proposal": "Illustrative p90 * 1.5 rounded up to 5 ms, only for the same device/build/instrument method; owner approval and repeated baselines are required."})
    return {
        "schemaVersion": 1, "recordCount": len(records), "groups": output, "hostPssDeltas": deltas,
        "hostPssDeltaStatistics": delta_statistics,
        "timingInterpretation": "UiAutomation semantics and screenshot-confirmed visibility upper bounds, with polling, two display callbacks and capture overhead reported separately. Overlapping work means these costs must not be naively subtracted to claim pure renderer latency.",
        "frameInterpretation": "FrameMetrics render-and-issue durations and Android 12+ system deadlines; estimated excess refresh periods are not actual presentation drops.",
        "thresholds": {"enforced": False, "ownerApprovalRequired": True, "candidates": candidates},
    }


def apk_footprint(path: Path) -> dict:
    data = path.read_bytes()
    dex_files = []
    with zipfile.ZipFile(path) as apk:
        for name in sorted(apk.namelist()):
            if not re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", name):
                continue
            dex = apk.read(name)
            if len(dex) < 112 or dex[:4] != b"dex\n" or dex[7] != 0 or dex[4:7] > b"040":
                raise ValueError(f"Unsupported DEX container in {name}")
            if struct.unpack_from("<I", dex, 40)[0] != 0x12345678:
                raise ValueError(f"Unsupported DEX byte order in {name}")
            dex_files.append({"file": name, "uncompressedBytes": len(dex),
                              "methodReferenceCount": struct.unpack_from("<I", dex, 88)[0],
                              "classDefinitionCount": struct.unpack_from("<I", dex, 96)[0]})
    if not dex_files:
        raise ValueError("APK has no supported classes DEX files")
    return {"file": path.name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
            "crc32": f"{zlib.crc32(data) & 0xffffffff:08x}", "dex": dex_files,
            "methodReferencesSummedAcrossDex": sum(dex["methodReferenceCount"] for dex in dex_files),
            "classDefinitionsSummedAcrossDex": sum(dex["classDefinitionCount"] for dex in dex_files),
            "methodCountInterpretation": "DEX method_ids references, summed per DEX; not unique defined methods."}


def adb(args, *command: str, timeout: int = 30) -> str:
    result = subprocess.run([args.adb, "-s", args.serial, *command], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"adb {command[0]} failed ({result.returncode}): {result.stdout}")
    return result.stdout


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def collect(args) -> Path:
    if not re.fullmatch(r"[A-Za-z0-9_.]+\.compose\.spike", args.host_package):
        raise ValueError("Only an explicitly named isolated .compose.spike package may be stopped")
    sections = args.sections.split(",")
    if len(sections) != len(set(sections)) or any(section not in METHODS for section in sections):
        raise ValueError("Sections must be a unique comma-separated subset of " + ",".join(METHODS))
    run_id = args.run_id or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", run_id):
        raise ValueError("run-id must be a plain label of at most 80 characters")
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    record_path = output / f"{run_id}-records.json"
    if record_path.exists():
        raise ValueError(f"Refusing to overwrite an existing run: {record_path}")
    if adb(args, "get-state").strip() != "device":
        raise RuntimeError("The explicitly selected device is not ready")
    for name in (args.host_package, args.host_package + ".test", "io.github.supermonster003.autojs6.plugin.three.block.compose"):
        if not adb(args, "shell", "pm", "path", name).strip().startswith("package:"):
            raise RuntimeError(f"Required package is not installed: {name}")
    metadata = {"serial": args.serial, "runId": run_id, "hostPackage": args.host_package,
                "samples": args.samples, "coldSamples": args.cold_samples, "warmups": args.warmups,
                "memorySamples": args.memory_samples, "scrollSwipes": args.scroll_swipes, "pollMs": args.poll_ms,
                "sections": sections, "startedUtc": datetime.now(timezone.utc).isoformat(),
                "buildFingerprint": adb(args, "shell", "getprop", "ro.build.fingerprint").strip(),
                "model": adb(args, "shell", "getprop", "ro.product.model").strip(),
                "api": adb(args, "shell", "getprop", "ro.build.version.sdk").strip(),
                "coldDefinition": "Only the isolated host is force-stopped; disk/page/dex caches and device settings are preserved."}
    records = []
    completed = []
    component = args.host_package + ".test/androidx.test.runner.AndroidJUnitRunner"
    for section in sections:
        for index in range(args.cold_samples if section == "cold" else 1):
            label = f"{section}-{index}"
            print(f"Collecting {label} on {args.serial}...", flush=True)
            # Exact, checked package name; never stop another host or all script engines.
            adb(args, "shell", "am", "force-stop", args.host_package)
            extras = {
                "class": TEST_CLASS + "#" + METHODS[section], "composePerfEnabled": "true",
                "composePerfFreshProcess": "true", "composePerfRunId": run_id,
                "composePerfColdIndex": str(index), "composePerfSamples": str(args.samples),
                "composePerfWarmups": str(args.warmups), "composePerfPollMs": str(args.poll_ms),
                "composePerfScrollSwipes": str(args.scroll_swipes), "composePerfMemorySamples": str(args.memory_samples),
            }
            command = ["shell", "am", "instrument", "-w", "-r"]
            for name, value in extras.items():
                command.extend(["-e", name, value])
            command.append(component)
            log_path = output / f"{run_id}-{label}.log"
            try:
                log = adb(args, *command, timeout=args.timeout)
                log_path.write_text(log, encoding="utf-8")
                result = parse_instrumentation(log)
                validate_invocation(result, section, index, args.samples, args.memory_samples, run_id)
                for record in result:
                    record["serial"] = args.serial
                    record["invocation"] = label
                    record["rawLog"] = log_path.name
                records.extend(result)
                completed.append(label)
                write_json(record_path, {"schemaVersion": 1, "metadata": metadata, "completed": completed, "records": records})
            except BaseException as error:
                if isinstance(error, subprocess.TimeoutExpired):
                    partial = error.stdout or ""
                    if isinstance(partial, bytes):
                        partial = partial.decode("utf-8", errors="replace")
                    log_path.write_text(partial + "\nBENCHMARK_TIMEOUT\n", encoding="utf-8")
                    # A timed-out instrumentation cannot run its own finally cleanup.
                    adb(args, "shell", "am", "force-stop", args.host_package)
                write_json(record_path, {"schemaVersion": 1, "metadata": metadata, "completed": completed,
                                         "failedInvocation": label, "records": records})
                raise
    validate_run({"schemaVersion": 1, "metadata": metadata, "completed": completed, "records": records})
    summary_path = output / f"{run_id}-summary.json"
    write_json(summary_path, summarize(records))
    print(f"Records: {record_path}\nSummary: {summary_path}", flush=True)
    return summary_path


def bounded_integer(minimum: int, maximum: int):
    def parse(value: str) -> int:
        number = int(value)
        if not minimum <= number <= maximum:
            raise argparse.ArgumentTypeError(f"Expected {minimum}..{maximum}")
        return number
    return parse


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    run = commands.add_parser("run", help="Collect observations on one explicitly named device")
    run.add_argument("--serial", required=True)
    run.add_argument("--output-dir", required=True, type=Path)
    run.add_argument("--adb", default="adb")
    run.add_argument("--host-package", default="org.autojs.autojs6.compose.spike")
    run.add_argument("--run-id")
    run.add_argument("--sections", default="cold,warm,updates,list,memory")
    run.add_argument("--cold-samples", type=bounded_integer(1, 10), default=3)
    run.add_argument("--samples", type=bounded_integer(1, 50), default=10)
    run.add_argument("--warmups", type=bounded_integer(1, 5), default=2)
    run.add_argument("--poll-ms", type=bounded_integer(1, 100), default=8)
    run.add_argument("--scroll-swipes", type=bounded_integer(1, 40), default=12)
    run.add_argument("--memory-samples", type=bounded_integer(1, 20), default=5)
    run.add_argument("--timeout", type=bounded_integer(60, 1800), default=600)
    report = commands.add_parser("summarize", help="Recompute a report from preserved records")
    report.add_argument("inputs", nargs="+", type=Path)
    report.add_argument("--output", required=True, type=Path)
    report.add_argument("--plugin-apk", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == "run":
            collect(args)
        else:
            records = []
            paths = sorted({Path(match).resolve() for pattern in args.inputs for match in glob.glob(str(pattern))})
            if not paths:
                raise ValueError("No record files match the requested inputs")
            for path in paths:
                data = json.loads(path.read_text(encoding="utf-8"))
                validate_run(data)
                records.extend(data["records"])
            if not records:
                raise ValueError("No measurements to summarize")
            result = summarize(records)
            if args.plugin_apk:
                result["pluginApk"] = apk_footprint(args.plugin_apk)
            write_json(args.output, result)
            print(f"Summary: {args.output}")
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Compose performance: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
