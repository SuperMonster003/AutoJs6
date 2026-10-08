"""Independent analysis guards; no Android build, adb or timing thresholds."""

import copy
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

import compose_performance as perf


def record(metric="state_update", value=10_000_000):
    return {
        "schemaVersion": 1, "suite": "compose-ui-p5.3", "metric": metric,
        "sample": 0, "runId": "test", "pid": 100, "api": 24, "device": "fixture",
        "host": {"package": "org.autojs.autojs6.compose.spike", "versionCode": 5318},
        "plugin": {"package": "io.github.supermonster003.autojs6.plugin.compose.ui", "versionCode": 18},
        "fields": {"surface": "ui", "toSemanticsNs": value, "toPixelConfirmedNs": value + 50_000_000,
                   "semanticsQueryNs": 2_000_000, "twoDisplayCallbacksNs": 30_000_000,
                   "screenshotNs": 20_000_000, "pixelConfirmed": True, "renderCount": 1},
    }


class ComposePerformanceAnalysisTest(unittest.TestCase):
    def test_status_parser_requires_one_successful_entry_and_real_records(self):
        output = perf.STATUS_PREFIX + json.dumps(record()) + "\nOK (1 test)\n"
        self.assertEqual([record()], perf.parse_instrumentation(output))
        for invalid in ("OK (1 test)\n", output + "FAILURES!!!", output.replace("1 test", "2 tests")):
            with self.assertRaises(ValueError):
                perf.parse_instrumentation(invalid)

    def test_visibility_endpoint_cannot_precede_semantics_or_omit_pixel_evidence(self):
        data = record()
        data["fields"]["toPixelConfirmedNs"] = 1
        with self.assertRaises(ValueError):
            perf.validate_record(data)
        data = record()
        data["fields"]["pixelConfirmed"] = False
        with self.assertRaises(ValueError):
            perf.validate_record(data)

    def test_statistics_preserve_instrument_overhead_and_do_not_enable_a_gate(self):
        records = [record(value=value * 1_000_000) for value in (10, 20, 30, 40, 50)]
        for index, item in enumerate(records):
            item["sample"] = index
        result = perf.summarize(records)
        stats = result["groups"][0]["statistics"]
        self.assertEqual(30, stats["toSemanticsMs"]["median"])
        self.assertEqual(96, stats["toPixelConfirmedMs"]["p90"])
        self.assertEqual(20, stats["screenshotMs"]["median"])
        self.assertEqual(30, stats["twoDisplayCallbacksMs"]["median"])
        self.assertFalse(result["thresholds"]["enforced"])
        self.assertTrue(result["thresholds"]["ownerApprovalRequired"])
        self.assertEqual(145, result["thresholds"]["candidates"][0]["candidateVisibilityConfirmationMs"])

    def test_two_devices_or_surfaces_never_share_a_latency_baseline(self):
        ui = record()
        floating = copy.deepcopy(ui)
        floating["fields"]["surface"] = "floaty"
        other = copy.deepcopy(ui)
        other["device"] = "second fixture"
        self.assertEqual(3, len(perf.summarize([ui, floating, other])["groups"]))

    def test_lost_metric_reports_are_separate_from_rendering_jank_estimates(self):
        frames = {
            "refreshRateHz": 100, "collectionOverflow": 0,
            "frames": [
                {"totalNs": 90_000_000, "firstDraw": True, "deadlineNs": -1, "metricReportsDropped": 7},
                {"totalNs": 8_000_000, "firstDraw": False, "deadlineNs": 10_000_000, "metricReportsDropped": 2},
                {"totalNs": 25_000_000, "firstDraw": False, "deadlineNs": 20_000_000, "metricReportsDropped": 0},
            ],
        }
        result = perf.frame_summary(frames)
        self.assertEqual(2, result["framesMeasured"])
        self.assertEqual(50, result["renderOverRefreshPeriodPercent"])
        self.assertEqual(2, result["estimatedExcessRefreshPeriods"])
        self.assertEqual(50, result["systemDeadlineMissPercent"])
        self.assertEqual(9, result["metricReportsDropped"])
        self.assertIsNone(result["presentationDropCount"])
        frames["frames"] = frames["frames"][:1]
        with self.assertRaises(ValueError):
            perf.frame_summary(frames)

    def test_pss_uses_native_ui_baseline_and_preserves_negative_noise(self):
        records = []
        for stage, pss in (("empty_native_ui_engine", 100_000), ("first_compose_ui", 130_000), ("after_list_close", 95_000)):
            item = record("host_pss")
            item["fields"] = {"stage": stage, "totalPssKb": pss}
            records.append(item)
        deltas = perf.summarize(records)["hostPssDeltas"][0]["stageMedianDeltaKb"]
        self.assertEqual(30_000, deltas["first_compose_ui"])
        self.assertEqual(-5_000, deltas["after_list_close"])

    def test_dex_footprint_counts_references_and_multidex_ten(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            dex = bytearray(112)
            dex[:8] = b"dex\n039\x00"
            struct.pack_into("<I", dex, 40, 0x12345678)
            struct.pack_into("<I", dex, 88, 12)
            struct.pack_into("<I", dex, 96, 3)
            with zipfile.ZipFile(apk, "w") as output:
                output.writestr("classes.dex", dex)
                output.writestr("classes10.dex", dex)
            report = perf.apk_footprint(apk)
            self.assertEqual(24, report["methodReferencesSummedAcrossDex"])
            self.assertEqual(6, report["classDefinitionsSummedAcrossDex"])
            self.assertEqual(2, len(report["dex"]))

    def test_short_cold_series_has_statistics_but_no_threshold_candidate(self):
        records = [record("first_frame") for _ in range(3)]
        for index, item in enumerate(records):
            item["sample"] = index
            item["pid"] += index
            item["fields"]["temperature"] = "process_cold_first_compose_mount"
        report = perf.summarize(records)
        self.assertEqual(3, report["groups"][0]["statistics"]["toPixelConfirmedMs"]["count"])
        self.assertEqual([], report["thresholds"]["candidates"])

    def test_invocation_requires_each_requested_sample_exactly_once(self):
        records = [record("first_frame") for _ in range(3)]
        for index, item in enumerate(records):
            item["sample"] = index
            item["fields"]["temperature"] = "same_process_new_engine_after_warmups"
        perf.validate_invocation(records, "warm", 0, 3, 5, "test")
        for invalid in (records[:-1], records + [records[0]]):
            with self.assertRaises(ValueError):
                perf.validate_invocation(invalid, "warm", 0, 3, 5, "test")
        split_process = copy.deepcopy(records)
        split_process[-1]["pid"] += 1
        with self.assertRaises(ValueError):
            perf.validate_invocation(split_process, "warm", 0, 3, 5, "test")

    def test_updates_need_both_surfaces_and_both_mutation_sizes(self):
        records = []
        for metric in ("state_update", "batch_100"):
            for surface in ("ui", "floaty"):
                item = record(metric)
                item["fields"]["surface"] = surface
                records.append(item)
        perf.validate_invocation(records, "updates", 0, 1, 5, "test")
        for invalid in ([item for item in records if item["fields"]["surface"] == "ui"], records[:-1]):
            with self.assertRaises(ValueError):
                perf.validate_invocation(invalid, "updates", 0, 1, 5, "test")

    def test_list_requires_the_scroll_trace_as_well_as_a_first_frame(self):
        first = record("list_first_frame")
        first["fields"]["temperature"] = "same_process_new_engine_after_list_warmups"
        trace = record("list_scroll")
        trace["fields"] = {}
        perf.validate_invocation([first, trace], "list", 0, 1, 5, "test")
        with self.assertRaises(ValueError):
            perf.validate_invocation([first], "list", 0, 1, 5, "test")

    def test_list_updates_need_both_layers_with_pixel_confirmation(self):
        records = [record(metric) for metric in perf.LIST_UPDATE_METRICS]
        perf.validate_invocation(records, "list-updates", 0, 1, 5, "test")
        for invalid in (records[:1], records + [copy.deepcopy(records[0])]):
            with self.assertRaises(ValueError):
                perf.validate_invocation(invalid, "list-updates", 0, 1, 5, "test")
        unconfirmed = copy.deepcopy(records)
        unconfirmed[1]["fields"]["pixelConfirmed"] = False
        with self.assertRaises(ValueError):
            perf.validate_invocation(unconfirmed, "list-updates", 0, 1, 5, "test")
        self.assertEqual("thousandItemListStateUpdates", perf.METHODS["list-updates"])

    def test_memory_requires_all_nine_stages_and_the_requested_samples(self):
        records = []
        for stage in perf.PSS_STAGES:
            for index in range(2):
                item = record("host_pss")
                item["sample"] = index
                item["fields"] = {"stage": stage, "totalPssKb": 100_000}
                records.append(item)
        perf.validate_invocation(records, "memory", 0, 1, 2, "test")
        with self.assertRaises(ValueError):
            perf.validate_invocation(records[:-1], "memory", 0, 1, 2, "test")

    def test_preserved_run_is_revalidated_and_cold_processes_cannot_share_a_pid(self):
        records = []
        for index in range(2):
            item = record("first_frame")
            item.update(sample=index, pid=100 + index, serial="device", invocation=f"cold-{index}")
            item["fields"]["temperature"] = "process_cold_first_compose_mount"
            records.append(item)
        data = {"schemaVersion": 1, "metadata": {"runId": "test", "serial": "device", "sections": ["cold"],
                "samples": 10, "coldSamples": 2, "memorySamples": 5},
                "completed": ["cold-0", "cold-1"], "records": records}
        perf.validate_run(data)
        missing = copy.deepcopy(data)
        missing["records"].pop()
        with self.assertRaises(ValueError):
            perf.validate_run(missing)
        same_process = copy.deepcopy(data)
        same_process["records"][1]["pid"] = 100
        with self.assertRaises(ValueError):
            perf.validate_run(same_process)
        mislabeled = copy.deepcopy(data)
        mislabeled["records"][1]["runId"] = "another-run"
        with self.assertRaises(ValueError):
            perf.validate_run(mislabeled)

    def test_merged_duplicate_records_are_rejected(self):
        with self.assertRaises(ValueError):
            perf.summarize([record(), record()])

    def test_pss_pairs_each_run_and_process_before_summarizing_deltas(self):
        records = []
        for index, (baseline, mounted) in enumerate(((100_000, 300_000), (200_000, 200_000), (300_000, 250_000))):
            for stage, pss in (("empty_native_ui_engine", baseline), ("first_compose_ui", mounted)):
                item = record("host_pss")
                item.update(runId=f"run-{index}", pid=100 + index)
                item["fields"] = {"stage": stage, "totalPssKb": pss}
                records.append(item)
        result = perf.summarize(records)
        self.assertEqual(3, len(result["hostPssDeltas"]))
        self.assertEqual([200_000, 0, -50_000], [pair["stageMedianDeltaKb"]["first_compose_ui"] for pair in result["hostPssDeltas"]])
        stats = result["hostPssDeltaStatistics"][0]["stagePairedDeltaKb"]["first_compose_ui"]
        self.assertEqual(0, stats["median"])
        self.assertEqual(3, stats["count"])
        # Pooling stage medians first would incorrectly report 250000 - 200000 = 50000.
        self.assertNotEqual(50_000, stats["median"])

    def test_pss_never_borrows_a_baseline_from_a_different_process(self):
        baseline = record("host_pss")
        baseline["fields"] = {"stage": "empty_native_ui_engine", "totalPssKb": 100_000}
        mounted = record("host_pss")
        mounted["pid"] += 1
        mounted["fields"] = {"stage": "first_compose_ui", "totalPssKb": 120_000}
        with self.assertRaises(ValueError):
            perf.summarize([baseline, mounted])


if __name__ == "__main__":
    unittest.main()
