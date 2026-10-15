from copy import deepcopy
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib

from dataset.task_io import read_json, write_json
from package.build_package import (BinaryModel, BinaryTree, MAX_DEPTH, MAX_MODEL_BYTES, MAX_SCHEMA_BYTES,
                                   MAX_TEXT_BYTES, ZIP_NAMES, _archive, build_package, decode_model,
                                   encode_model, score_model)
from train.data import FeatureSchema, sha256


# Authored from the binary table, independently of encode_model. Header:
# AJCG / v1 / 2 features / 2 trees / bias .25 / scale 2. Tree 0: depth1,
# feature0 >1.5, leaves[-1,2]. Tree1: depth2, feature1 >.25 then feature0
# >-2, leaves[.5,1,1.5,2]. CRC32 of these 72 bytes is D77A82FB.
GOLDEN = bytes.fromhex(
    "414a43470100020002000000000000000000d03f0000000000000040"
    "0100000000c03f000080bf00000040"
    "0201000000803e0000000000c00000003f0000803f0000c03f00000040"
    "fb827ad7"
)


def toy_schema():
    names = ("quality", "rule_rank_norm")
    return FeatureSchema(1, names, hashlib.sha256("\n".join(names).encode()).hexdigest()[:16])


def toy_model():
    return {
        "features_info": {"float_features": [
            {"feature_id": "quality", "feature_index": 0, "flat_feature_index": 0, "has_nans": False, "borders": [-2, 1.5]},
            {"feature_id": "rule_rank_norm", "feature_index": 1, "flat_feature_index": 1, "has_nans": False, "borders": [0.25]},
        ]},
        "oblivious_trees": [
            {"splits": [{"split_type": "FloatFeature", "float_feature_index": 0, "border": 1.5}],
             "leaf_values": [-1, 2]},
            {"splits": [{"split_type": "FloatFeature", "float_feature_index": 1, "border": 0.25},
                        {"split_type": "FloatFeature", "float_feature_index": 0, "border": -2}],
             "leaf_values": [0.5, 1, 1.5, 2]},
        ],
        "scale_and_bias": [2, [0.25]],
    }


def repair_crc(payload):
    return payload[:-4] + struct.pack("<I", zlib.crc32(payload[:-4]) & 0xFFFFFFFF)


class BinaryFormatTest(unittest.TestCase):
    def test_two_tree_binary_matches_independent_hex_golden(self):
        actual = encode_model(toy_model(), toy_schema())
        self.assertEqual(76, len(GOLDEN))
        self.assertEqual(GOLDEN, actual)
        model = decode_model(GOLDEN)
        self.assertEqual((2, 0.25, 2), (model.feature_count, model.bias, model.scale))
        self.assertEqual((1, 0), model.trees[1].features)
        self.assertEqual((0.5, 1, 1.5, 2), model.trees[1].leaves)
        self.assertEqual(1.25, score_model(model, [0, 0]))
        self.assertEqual(2.25, score_model(model, [0, 0.5]))
        self.assertEqual(8.25, score_model(model, [2, 0.5]))

    def test_float_feature_mapping_uses_internal_to_flat_not_list_position(self):
        model = toy_model()
        features = model["features_info"]["float_features"]
        features.reverse()
        self.assertEqual(GOLDEN, encode_model(model, toy_schema()))
        for feature in features:
            feature["feature_index"] = 1 - feature["feature_index"]
        for tree in model["oblivious_trees"]:
            for split in tree["splits"]:
                split["float_feature_index"] = 1 - split["float_feature_index"]
        self.assertEqual(GOLDEN, encode_model(model, toy_schema()))

    def test_float32_inputs_and_equal_threshold_take_zero_bit(self):
        model = BinaryModel(1, -2, -3, (BinaryTree((0,), (1.0,), (2.0, 7.0)),))
        self.assertEqual(-8, score_model(model, [1.0]))
        self.assertEqual(-8, score_model(model, [1.0 + 1e-8]))  # rounds back to Float(1)
        self.assertEqual(-23, score_model(model, [1.0 + 1e-6]))
        for values in ([], [float("nan")], [float("inf")], [1e100]):
            with self.assertRaises(ValueError):
                score_model(model, values)

    def test_categorical_ctr_non_symmetric_and_multi_output_are_rejected(self):
        variants = []
        for field in ("categorical_features", "ctrs", "text_features", "embedding_features", "estimated_features"):
            model = toy_model()
            model["features_info"][field] = [{}]
            variants.append(model)
        model = toy_model()
        model["ctr_data"] = {"anything": {}}
        variants.append(model)
        model = toy_model()
        model["trees"] = []
        variants.append(model)
        model = toy_model()
        model["scale_and_bias"] = [1, [0, 0]]
        variants.append(model)
        model = toy_model()
        model["oblivious_trees"][0]["leaf_values"] *= 2
        variants.append(model)
        model = toy_model()
        model["oblivious_trees"][0]["splits"][0]["split_type"] = "OnlineCtr"
        variants.append(model)
        for model in variants:
            with self.assertRaises(ValueError):
                encode_model(model, toy_schema())

    def test_invalid_feature_dimensions_indices_and_names_are_rejected(self):
        variants = []
        model = toy_model()
        model["features_info"]["float_features"].pop()
        variants.append(model)
        for field, value in (("feature_index", 0), ("flat_feature_index", 0), ("flat_feature_index", 2),
                             ("feature_id", "wrong"), ("has_nans", True)):
            model = toy_model()
            model["features_info"]["float_features"][1][field] = value
            variants.append(model)
        for index in (-1, 2, True, 1.5):
            model = toy_model()
            model["oblivious_trees"][0]["splits"][0]["float_feature_index"] = index
            variants.append(model)
        for model in variants:
            with self.assertRaises(ValueError):
                encode_model(model, toy_schema())

    def test_depth_tree_count_and_numeric_limits_are_checked(self):
        for count in (0, 4097):
            model = toy_model()
            model["oblivious_trees"] = [model["oblivious_trees"][0]] * count
            with self.assertRaisesRegex(ValueError, "tree count"):
                encode_model(model, toy_schema())
        for depth in (0, MAX_DEPTH + 1):
            model = toy_model()
            model["oblivious_trees"][0]["splits"] *= depth
            model["oblivious_trees"][0]["leaf_values"] = [0] * (1 << depth)
            with self.assertRaisesRegex(ValueError, "depth"):
                encode_model(model, toy_schema())
        for invalid in (float("nan"), float("inf"), -float("inf"), 1e100, True):
            for target in ("border", "leaf"):
                model = toy_model()
                if target == "border":
                    model["oblivious_trees"][0]["splits"][0]["border"] = invalid
                else:
                    model["oblivious_trees"][0]["leaf_values"][0] = invalid
                with self.assertRaises(ValueError):
                    encode_model(model, toy_schema())
        for invalid in (float("nan"), float("inf"), True):
            model = toy_model()
            model["scale_and_bias"][0] = invalid
            with self.assertRaises(ValueError):
                encode_model(model, toy_schema())

    def test_decoder_rejects_corruption_truncation_trailing_bytes_and_bad_shapes(self):
        for data in (GOLDEN[:-1], GOLDEN + b"x", b"", bytes(len(GOLDEN)), GOLDEN[:30]):
            with self.assertRaises(ValueError):
                decode_model(data)
        for offset, replacement in ((0, b"XXXX"), (4, b"\x02\x00"), (6, b"\x00\x00"),
                                    (8, b"\x01\x10\x00\x00"), (28, b"\x00"), (29, b"\x02\x00"),
                                    (31, struct.pack("<f", float("nan")))):
            data = bytearray(GOLDEN)
            data[offset:offset + len(replacement)] = replacement
            with self.assertRaises(ValueError):
                decode_model(repair_crc(data))
        with self.assertRaisesRegex(ValueError, "Trailing"):
            decode_model(repair_crc(GOLDEN[:-4] + b"padding" + GOLDEN[-4:]))


class PackageFixture:
    def __init__(self, root):
        self.root = Path(root)
        schema = toy_schema()
        self.schema = {"format": 1, "featureSchemaVersion": schema.version, "names": list(schema.names), "namesHash": schema.names_hash}
        write_json(self.root / "features.json", self.schema)
        write_json(self.root / "model.json", toy_model())
        self.dataset = {"format": 1, "datasetId": "handwritten-only", "status": "complete", "generatedAt": "2026-10-03T00:00:00Z",
                        "splits": {split: {"groups": groups, "groupsWithoutCandidates": 0}
                                   for split, groups in (("train", 2), ("valid", 1), ("test", 1), ("real-test", 0))}}
        write_json(self.root / "dataset-manifest.json", self.dataset)
        self.training = {"format": 1, "datasetId": "handwritten-only", "featureSchemaVersion": 1, "namesHash": schema.names_hash,
                         "inputSha256": {"manifest": sha256(self.root / "dataset-manifest.json"), "schema": sha256(self.root / "features.json")},
                         "artifacts": {"model.json": {"sha256": sha256(self.root / "model.json")}, "model.cbm": {"sha256": "c" * 64}},
                         "groups": {"train": 2, "valid": 1}, "treeCount": 2, "maximumTreeDepth": 2,
                         "versions": {"catboost": "handwritten-test", "python": "3.12-test"}, "exportGeneratorCommit": "a" * 40,
                         "completedAt": "2026-10-03T00:01:00Z"}
        write_json(self.root / "train-log.json", self.training)
        self.metrics = {"format": 1, "datasetId": "handwritten-only", "featureSchemaVersion": 1, "featureNamesHash": schema.names_hash,
                        "modelSha256": "c" * 64, "datasetHashes": deepcopy(self.training["inputSha256"]),
                        "test": {"groups": 1, "top1Survival": 1, "ruleTop1Survival": 0.5, "regret": 0},
                        "realTest": {"groups": 0, "top1Survival": None, "ruleTop1Survival": None, "regret": None}}
        write_json(self.root / "metrics.json", self.metrics)
        (self.root / "LICENSE").write_text("Handwritten test fixture license; not a trained model.\n", encoding="utf-8")

    def build(self, name="first.ajcgm", **overrides):
        options = {"spdx": "LicenseRef-TestFixture", "publisher": "AutoJs6 tests", "model_id": "autojs6.codegen.unit-test",
                   "version_code": 1, "version_name": "1-test", "display_name": {"en": "Test ranker", "zh-Hans": "测试排序器"},
                   "min_host_version_code": 1, "output": self.root / name}
        options.update(overrides)
        return build_package(*(self.root / name for name in ("model.json", "features.json", "dataset-manifest.json",
                                                              "train-log.json", "metrics.json", "LICENSE")), **options)


class PackageBuildTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = PackageFixture(self.temporary.name)

    def manifest_times(self, created_at, trained_at):
        dataset = deepcopy(self.fixture.dataset)
        dataset["generatedAt"] = created_at
        write_json(self.fixture.root / "dataset-manifest.json", dataset)
        digest = sha256(self.fixture.root / "dataset-manifest.json")
        training = deepcopy(self.fixture.training)
        training["completedAt"] = trained_at
        training["inputSha256"]["manifest"] = digest
        write_json(self.fixture.root / "train-log.json", training)
        metrics = deepcopy(self.fixture.metrics)
        metrics["datasetHashes"]["manifest"] = digest
        write_json(self.fixture.root / "metrics.json", metrics)

    def test_two_packages_are_byte_identical_and_have_fixed_entries(self):
        manifest = self.fixture.build("one.ajcgm")
        self.fixture.build("two.ajcgm")
        first, second = (self.fixture.root / name for name in ("one.ajcgm", "two.ajcgm"))
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as archive:
            self.assertEqual(ZIP_NAMES, tuple(archive.namelist()))
            self.assertEqual(GOLDEN, archive.read("model.bin"))
            self.assertEqual((self.fixture.root / "features.json").read_bytes(), archive.read("features.json"))
            self.assertEqual((self.fixture.root / "LICENSE").read_bytes(), archive.read("LICENSE"))
            self.assertEqual(manifest, json.loads(archive.read("manifest.json")))
            for info in archive.infolist():
                self.assertEqual((1980, 1, 1, 0, 0, 0), info.date_time)
                self.assertEqual(zipfile.ZIP_STORED, info.compress_type)
                self.assertEqual(info.file_size, info.compress_size)
                self.assertEqual(0o100644 << 16, info.external_attr)
            card = archive.read("MODEL_CARD.md").decode()
            self.assertIn("handwritten-only", card)
            self.assertIn("not measured", card)
        self.assertEqual(2, manifest["dataset"]["groups"])
        self.assertEqual("2026-10-03T00:01:00Z", manifest["training"]["trainedAt"])
        self.assertEqual(hashlib.sha256(GOLDEN).hexdigest(), manifest["model"]["sha256"])

    def with_real_train(self, dataset_groups, training_groups):
        dataset = read_json(self.fixture.root / "dataset-manifest.json")
        dataset["splits"]["real-train"] = {"groups": dataset_groups, "groupsWithoutCandidates": 0,
                                           "apps": {"a.private.app": 2, "b.private.app": dataset_groups - 2}}
        write_json(self.fixture.root / "dataset-manifest.json", dataset)
        digest = sha256(self.fixture.root / "dataset-manifest.json")
        training, metrics = read_json(self.fixture.root / "train-log.json"), read_json(self.fixture.root / "metrics.json")
        training["inputSha256"]["manifest"] = metrics["datasetHashes"]["manifest"] = digest
        if training_groups is None:
            training["groups"].pop("real-train", None)
        else:
            training["groups"]["real-train"] = training_groups
        write_json(self.fixture.root / "train-log.json", training)
        write_json(self.fixture.root / "metrics.json", metrics)

    def test_real_train_groups_are_counted_and_disclosed_in_aggregate(self):
        self.with_real_train(3, 3)
        manifest = self.fixture.build("real.ajcgm")
        self.assertEqual(5, manifest["dataset"]["groups"])
        with zipfile.ZipFile(self.fixture.root / "real.ajcgm") as archive:
            card = archive.read("MODEL_CARD.md").decode()
        self.assertIn("of which 3 come from 2 real applications assigned to real-train", card)
        self.assertNotIn("private.app", card)
        for training_groups in (2, None):
            self.with_real_train(3, training_groups)
            with self.assertRaisesRegex(ValueError, "real-train"):
                self.fixture.build("mismatch.ajcgm")

    def test_corrupt_provenance_cannot_produce_a_package(self):
        cases = [("train-log.json", "inputSha256", "manifest", "0" * 64),
                 ("train-log.json", "inputSha256", "schema", "0" * 64),
                 ("metrics.json", "datasetHashes", "manifest", "0" * 64),
                 ("metrics.json", "test", "groups", 2)]
        for file, section, key, value in cases:
            path = self.fixture.root / file
            original = read_json(path)
            edited = deepcopy(original)
            edited[section][key] = value
            write_json(path, edited)
            with self.assertRaises(ValueError):
                self.fixture.build()
            self.assertFalse((self.fixture.root / "first.ajcgm").exists())
            write_json(path, original)
        path = self.fixture.root / "model.json"
        model = read_json(path)
        model["oblivious_trees"][0]["leaf_values"][0] = -2
        write_json(path, model)
        with self.assertRaisesRegex(ValueError, "training artifacts"):
            self.fixture.build()

    def test_empty_real_test_metrics_are_null_and_invalid_bounds_are_rejected(self):
        path = self.fixture.root / "metrics.json"
        for bucket, field, value in (("realTest", "top1Survival", 1), ("test", "regret", 1.1), ("test", "top1Survival", None)):
            metrics = deepcopy(self.fixture.metrics)
            metrics[bucket][field] = value
            write_json(path, metrics)
            with self.assertRaises(ValueError):
                self.fixture.build()
        self.assertFalse((self.fixture.root / "first.ajcgm").exists())

    def test_manifest_names_version_dates_and_string_limits_are_checked(self):
        for options in ({"model_id": "../evil"}, {"model_id": "x"}, {"version_code": 0},
                        {"min_host_version_code": 0}, {"display_name": {"fr": "nom"}},
                        {"publisher": "bad\nname"}, {"display_name": {"en": "😀" * 129}},
                        {"version_name": "x" * 257}, {"spdx": ""}):
            with self.assertRaises(ValueError):
                self.fixture.build(**options)
        training = deepcopy(self.fixture.training)
        for time in ("2026-10-03", "2026-10-03T12:00:00", "invented", "2026-10-03T00:00:00+19:00"):
            training["completedAt"] = time
            write_json(self.fixture.root / "train-log.json", training)
            with self.assertRaises(ValueError):
                self.fixture.build()

    def test_manifest_calendar_timestamps_reject_formats_the_host_cannot_parse(self):
        invalid = (
            "2026-W40-6T00:00:00Z",
            "20261003T000000Z",
            "2026-10-03T00:00:00.1234567890Z",
            "2026-10-03T00:00:00+12:99",
            "2026-10-03T00:00:00+18:01",
            "2026-02-29T00:00:00Z",
        )
        for field in ("dataset.createdAt", "training.trainedAt"):
            for value in invalid:
                with self.subTest(field=field, value=value):
                    self.manifest_times(
                        value if field == "dataset.createdAt" else self.fixture.dataset["generatedAt"],
                        value if field == "training.trainedAt" else self.fixture.training["completedAt"],
                    )
                    with self.assertRaisesRegex(ValueError, field.replace(".", r"\.")):
                        self.fixture.build()
                    self.assertFalse((self.fixture.root / "first.ajcgm").exists())

    def test_manifest_preserves_legal_calendar_offsets_and_nanosecond_text(self):
        legal = (
            "2026-10-03T00:00:00Z",
            "2026-10-03T08:15:30+08:00",
            "2024-02-29T00:00:00.1Z",
            "2026-10-03T08:15:30.123456789+08:00",
            "2026-10-03T00:00:00.000000001-00:00",
            "2026-10-03T00:00:00+18:00",
            "2026-10-03T00:00:00-18:00",
        )
        for index, value in enumerate(legal):
            with self.subTest(value=value):
                self.manifest_times(value, value)
                name = f"timestamp-{index}.ajcgm"
                manifest = self.fixture.build(name)
                with zipfile.ZipFile(self.fixture.root / name) as archive:
                    emitted = json.loads(archive.read("manifest.json"))
                for actual in (manifest, emitted):
                    self.assertEqual(value, actual["dataset"]["createdAt"])
                    self.assertEqual(value, actual["training"]["trainedAt"])

    def test_explicit_license_and_existing_output_are_preserved(self):
        first = self.fixture.root / "first.ajcgm"
        first.write_bytes(b"keep existing artifact")
        with self.assertRaisesRegex(ValueError, "new .ajcgm"):
            self.fixture.build()
        self.assertEqual(b"keep existing artifact", first.read_bytes())
        for content in (b"", b"  \n", b"\xff", b"x" * (MAX_TEXT_BYTES + 1)):
            (self.fixture.root / "LICENSE").write_bytes(content)
            with self.assertRaises((ValueError, UnicodeError)):
                self.fixture.build("invalid.ajcgm")
            self.assertFalse((self.fixture.root / "invalid.ajcgm").exists())

    def test_schema_and_entry_limits_and_duplicate_json_fields_are_rejected(self):
        path = self.fixture.root / "features.json"
        original = path.read_bytes()
        path.write_bytes(original.replace(b'"format": 1', b'"format": 1, "format": 1'))
        with self.assertRaisesRegex(ValueError, "Duplicate JSON"):
            self.fixture.build()
        entries = {name: b"x" for name in ZIP_NAMES}
        for name, limit in (("manifest.json", MAX_TEXT_BYTES), ("features.json", MAX_SCHEMA_BYTES), ("model.bin", MAX_MODEL_BYTES)):
            oversized = {**entries, name: b"x" * (limit + 1)}
            with self.assertRaisesRegex(ValueError, "oversized"):
                _archive(oversized)
        with self.assertRaisesRegex(ValueError, "fixed five-entry"):
            _archive(dict(reversed(list(entries.items()))))


if __name__ == "__main__":
    unittest.main()
