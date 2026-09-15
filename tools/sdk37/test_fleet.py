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


if __name__ == "__main__":
    unittest.main()
