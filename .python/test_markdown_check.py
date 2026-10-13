"""Verify that documentation checks stay offline and never change source or output files."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class MarkdownCheckTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="autojs6-markdown-check-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for directory in (".readme", ".changelog"):
            shutil.copytree(ROOT / directory, self.root / directory)
        for relative in ("README.md", "version.properties", ".python/generate_markdown.py", ".python/check_translations.py"):
            source = ROOT / relative
            if source.is_file():
                target = self.root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
        assets = ROOT / "app/src/main/assets-app/doc"
        if assets.is_dir():
            target = self.root / "app/src/main/assets-app/doc"
            target.mkdir(parents=True)
            for source in assets.glob("CHANGELOG*.md"):
                shutil.copy2(source, target / source.name)

    def snapshot(self):
        return {str(path.relative_to(self.root)): (hashlib.sha256(path.read_bytes()).hexdigest(), path.stat().st_mtime_ns)
                for path in self.root.rglob("*") if path.is_file()}

    def run_check(self, *extra):
        script = self.root / ".python/generate_markdown.py"
        bootstrap = """import runpy,sys
from unittest.mock import patch
sys.path.insert(0, str(__import__('pathlib').Path(sys.argv[1]).parent))
sys.argv = sys.argv[1:]
with patch('urllib.request.urlopen', side_effect=AssertionError('Documentation checks must be offline')):
    runpy.run_path(sys.argv[0], run_name='__main__')
"""
        return subprocess.run([sys.executable, "-B", "-c", bootstrap, str(script), "--check", *extra],
                              cwd=self.root.parent, capture_output=True, text=True, encoding="utf-8",
                              env={**os.environ, "PYTHONIOENCODING": "utf-8", "PYTHONDONTWRITEBYTECODE": "1"})

    def test_clean_check_is_offline_and_read_only(self):
        before = self.snapshot()
        result = self.run_check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("MARKDOWN_OK", result.stdout)
        self.assertEqual(before, self.snapshot())

    def test_modified_output_is_reported_without_repair(self):
        (self.root / "README.md").write_text("Manual change\n", encoding="utf-8")
        before = self.snapshot()
        result = self.run_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("README.md", result.stdout + result.stderr)
        self.assertEqual(before, self.snapshot())

    def test_missing_output_is_not_recreated(self):
        (self.root / ".readme/README-fr.md").unlink()
        before = self.snapshot()
        result = self.run_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("README-fr.md", result.stdout + result.stderr)
        self.assertEqual(before, self.snapshot())

    def test_orphan_root_output_is_rejected(self):
        (self.root / "CHANGELOG.md").write_text("Obsolete alias\n", encoding="utf-8")
        before = self.snapshot()
        result = self.run_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("CHANGELOG.md", result.stdout + result.stderr)
        self.assertEqual(before, self.snapshot())

    def test_translation_drift_is_rejected_even_after_regenerating_outputs(self):
        path = self.root / ".changelog/lang_en.json"
        document = json.loads(path.read_text(encoding="utf-8"))
        version = next(iter(document["$data"].values()))
        version["feature"].pop()
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        # Rendering remains available for an explicitly requested Chinese-only draft.
        result = subprocess.run([sys.executable, "-B", str(self.root / ".python/generate_markdown.py")],
                                capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        before = self.snapshot()
        result = self.run_check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("CHANGELOG_TRANSLATIONS_ERROR", result.stderr)
        self.assertEqual(before, self.snapshot())

    def test_check_rejects_online_refresh_without_touching_files(self):
        before = self.snapshot()
        result = self.run_check("--refresh-metadata")
        self.assertEqual(result.returncode, 2)
        self.assertIn("cannot be combined", result.stderr)
        self.assertEqual(before, self.snapshot())


if __name__ == "__main__":
    unittest.main()
