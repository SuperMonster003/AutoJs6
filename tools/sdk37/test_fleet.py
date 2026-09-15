"""Host-side checks for the plugin fleet helper; no git or Gradle calls."""
from pathlib import Path
import tempfile
import unittest

import fleet


MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="x">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
    <application>
        <service android:name=".Player" android:foregroundServiceType="mediaPlayback" />
        <service android:name="com.example.Shim" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" />
    </application>
</manifest>
"""


class ExposureTest(unittest.TestCase):
    def test_manifest_facts_use_the_appendix_vocabulary(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "AndroidManifest.xml")
            path.write_text(MANIFEST, encoding="utf-8")
            self.assertEqual(fleet.manifest_exposure(path),
                             {"INTERNET", "WRITE_SECURE_SETTINGS", "前台服务 mediaPlayback", "无障碍服务 Shim"})
            path.write_text("<manifest", encoding="utf-8")
            self.assertEqual(fleet.manifest_exposure(path), {"manifest error"})

    def test_gradle_script_selection_skips_build_outputs_and_fixtures(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            for relative in ("app/build.gradle.kts", "app/build/x.gradle.kts", "test-fixtures/a/app/build.gradle.kts", ".gradle/y.gradle.kts"):
                path = repo / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("compileSdk = 36\n", encoding="utf-8")
            self.assertEqual([str(p.relative_to(repo)).replace("\\", "/") for p in fleet.gradle_scripts(repo)], ["app/build.gradle.kts"])


class PlanTest(unittest.TestCase):
    def entry(self, **overrides):
        base = {"name": "X", "compileSdk": 36, "targetSdk": 36, "minSdk": 24, "hardcoded": [], "exposure": [], "dirty": 0,
                "accessorFiles": 1, "lastCommit": "abc 2026-09-13", "versionName": "1.0.0", "versionBuild": "1"}
        base.update(overrides)
        return base

    def test_hardcoded_literals_override_properties(self):
        entry = self.entry(compileSdk=37, hardcoded=[{"file": "app/build.gradle.kts", "key": "compileSdk", "value": 36}])
        self.assertEqual(fleet.effective(entry, "compile"), (36, True))
        self.assertEqual(fleet.effective(entry, "target"), (36, False))

    def test_plan_orders_cleanup_before_bumps_and_marks_two_step(self):
        plan = fleet.plan_entry(self.entry(dirty=3, exposure=["INTERNET"]))
        self.assertTrue(plan["twoStep"])
        self.assertEqual(plan["steps"][0], "维护者先处理 3 个未提交文件 (D11)")
        self.assertTrue(any("compileSdk -> 37" in s for s in plan["steps"]))
        self.assertTrue(any("独立提交" in s for s in plan["steps"]))
        done = fleet.plan_entry(self.entry(compileSdk=37, targetSdk=37))
        self.assertEqual(done["steps"], ["已是 37 / 37, 只需版本递增与 changelog"])
        pure = fleet.plan_entry(self.entry())
        self.assertFalse(pure["twoStep"])
        self.assertTrue(any("同一发布" in s for s in pure["steps"]))

    def test_table_renders_effective_values(self):
        report = {"repositories": [self.entry(hardcoded=[{"file": "app/build.gradle.kts", "key": "targetSdk", "value": 35}])]}
        rows = fleet.table(report).splitlines()
        self.assertEqual(len(rows), 3)
        self.assertIn("| X | 36 / 35 / 24 |", rows[2])
        self.assertIn("app/build.gradle.kts:targetSdk=35", rows[2])


class BumpTest(unittest.TestCase):
    def test_next_patch_version_keeps_milestone_suffixes(self):
        self.assertEqual(fleet.bump_version_name("1.0.2"), "1.0.3")
        self.assertEqual(fleet.bump_version_name("2.10.9\n"), "2.10.10")
        self.assertEqual(fleet.bump_version_name("0.8.1-m9"), "0.8.2-m9")
        with self.assertRaises(ValueError):
            fleet.bump_version_name("6.8.0 Alpha5")

    def test_changelog_key_follows_the_repository_precedent(self):
        self.assertEqual(fleet.changelog_version("0.8.2-m9", "0.8.1-m9", "v0.8.1"), "0.8.2")
        self.assertEqual(fleet.changelog_version("0.8.2-m9", "0.8.1-m9", "v0.8.1-m9"), "0.8.2-m9")
        self.assertEqual(fleet.changelog_version("1.0.3", "1.0.2", "v1.0.2"), "1.0.3")

    def test_properties_replaced_in_place_and_missing_keys_rejected(self):
        text = "#comment\nCOMPILE_SDK_VERSION=36\nTARGET_SDK_VERSION=36\nVERSION_BUILD=17\nVERSION_NAME=1.0.2\n"
        updated = fleet.set_properties(text, {"COMPILE_SDK_VERSION": 37, "VERSION_BUILD": 18, "VERSION_NAME": "1.0.3"})
        self.assertEqual(updated, "#comment\nCOMPILE_SDK_VERSION=37\nTARGET_SDK_VERSION=36\nVERSION_BUILD=18\nVERSION_NAME=1.0.3\n")
        with self.assertRaises(ValueError):
            fleet.set_properties(text, {"MIN_SDK_VERSION": 24})

    def test_changelog_entry_prepended_with_original_layout(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lang_en.json"
            path.write_text('{\n  "changelog_label_fix": "Fix",\n  "$data": {\n    "v1.0.2": {\n      "released_date": "2026/09/13",\n      "fix": [\n        "x"\n      ]\n    }\n  }\n}\n', encoding="utf-8")
            fleet.insert_changelog_entry(path, "1.0.3", "2026/09/15", {"improvement": ["Raise SDK"]})
            text = path.read_text(encoding="utf-8")
            self.assertTrue(text.startswith('{\n  "changelog_label_fix": "Fix",\n  "$data": {\n    "v1.0.3": {\n      "released_date": "2026/09/15",\n      "improvement": [\n        "Raise SDK"\n      ]\n    },\n    "v1.0.2": {'))
            self.assertTrue(text.endswith("}\n"))
            with self.assertRaises(ValueError):
                fleet.insert_changelog_entry(path, "1.0.3", "2026/09/15", {"improvement": ["again"]})

    def test_readme_common_numbers_rewritten_without_touching_layout(self):
        text = '{\n  "android_min_sdk": "24",\n  "android_target_sdk": "36",\n  "min_android_version": "7.0"\n}\n'
        updated, changed = fleet.update_readme_common(text, 37, 37)
        self.assertEqual(changed, ["android_target_sdk"])
        self.assertEqual(updated, '{\n  "android_min_sdk": "24",\n  "android_target_sdk": "37",\n  "min_android_version": "7.0"\n}\n')
        self.assertEqual(fleet.update_readme_common(updated, 37, 37), (updated, []))
        versioned = '{\n  "version_name": "0.8.1-m9",\n  "version_build": "52"\n}\n'
        self.assertEqual(fleet.update_readme_common(versioned, 37, 37, "0.8.2-m9", 56),
                         ('{\n  "version_name": "0.8.2-m9",\n  "version_build": "56"\n}\n', ["version_name", "version_build"]))

    def test_stale_sdk_mentions_are_listed_per_template_line(self):
        with tempfile.TemporaryDirectory() as directory:
            readme = Path(directory) / ".readme"
            readme.mkdir()
            (readme / "lang_en.json").write_text('{\n  "a": "targets SDK 36 today",\n  "b": "API 36 / Android 16",\n  "c": "36 items",\n  "d": "minSdk 24, targetSdk 36"\n}\n', encoding="utf-8")
            hits = fleet.stale_sdk_mentions(Path(directory))
            self.assertEqual(len(hits), 3)
            self.assertTrue(hits[0].startswith("lang_en.json:2:"))

    def test_readme_template_numbers_rewritten_only_inside_named_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            readme = Path(directory) / ".readme"
            readme.mkdir()
            (readme / "lang_en.json").write_text('{\n  "p_build_params": "min SDK 24, target SDK 36; build 1036 stays",\n  "other": "SDK 36 stays"\n}\n', encoding="utf-8")
            (readme / "lang_ja.json").write_text('{\n  "p_build_params": "ターゲットSDKは36です"\n}\n', encoding="utf-8")
            (readme / "common.json").write_text('{\n  "p_build_params": "36"\n}\n', encoding="utf-8")
            changed = fleet.refresh_readme_keys(Path(directory), ["p_build_params", "missing"], 36, 37)
            self.assertEqual(changed, {"lang_en.json": 1, "lang_ja.json": 1})
            self.assertEqual((readme / "lang_en.json").read_text(encoding="utf-8"),
                             '{\n  "p_build_params": "min SDK 24, target SDK 37; build 1036 stays",\n  "other": "SDK 36 stays"\n}\n')
            self.assertEqual((readme / "lang_ja.json").read_text(encoding="utf-8"), '{\n  "p_build_params": "ターゲットSDKは37です"\n}\n')
            self.assertEqual((readme / "common.json").read_text(encoding="utf-8"), '{\n  "p_build_params": "36"\n}\n')
            self.assertEqual(fleet.refresh_readme_keys(Path(directory), ["p_build_params"], 36, 37), {})

    def test_built_in_notes_cover_the_ten_changelog_languages(self):
        self.assertEqual(sorted(fleet.BUMP_NOTES), sorted(["en", "zh-Hans", "zh-Hant-HK", "zh-Hant-TW", "ja", "ko", "ru", "fr", "es", "ar"]))


if __name__ == "__main__":
    unittest.main()
