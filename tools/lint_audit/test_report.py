from pathlib import Path
import tempfile
import unittest
from report import Issue, compare, legacy, read_report


class ReportTest(unittest.TestCase):
    def test_shifted_lines_keep_duplicates_and_do_not_hide_a_new_error(self):
        before = [Issue("NewApi", "Error", "old", ("app/src/main/A.kt",), "1")] * 2
        after = [Issue("NewApi", "Error", "old", ("app/src/main/A.kt",), "20"),
                 Issue("NewApi", "Error", "new", ("app/src/main/A.kt",), "21")]
        removed, added, suppressed = compare(before, after, [])
        self.assertEqual(sum(removed.values()), 1)
        self.assertEqual(sum(added.values()), 1)
        self.assertFalse(suppressed)

    def test_legacy_path_filter_keeps_modern_code_and_secondary_references_checked(self):
        paths = ["app/src/main/java/com/stardust/**"]
        old = Issue("NewApi", "Error", "call", ("app/src/main/java/com/stardust/a/A.kt",))
        modern = Issue("NewApi", "Error", "call", ("app/src/main/java/org/autojs/A.kt", old.locations[0]))
        lookalike = Issue("NewApi", "Error", "call", ("app/src/main/java/com/stardust2/A.kt",))
        self.assertTrue(legacy(old, paths))
        self.assertFalse(legacy(modern, paths))
        self.assertFalse(legacy(lookalike, paths))
        _, _, suppressed = compare([old, modern, lookalike], [], paths)
        self.assertEqual(sum(suppressed.values()), 1)

    def test_reports_from_separate_worktrees_compare_as_the_same_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            issues = []
            for checkout in (root / "original", root / "verification"):
                report = root / f"{checkout.name}.xml"
                source = (checkout / "app/src/main/A.kt").as_posix()
                report.write_text(
                    f'<issues><issue id="NewApi" severity="Error" message="call">'
                    f'<location file="{source}" line="1"/></issue></issues>', encoding="utf-8")
                issues.append(read_report(report, checkout))
            self.assertEqual(issues[0][0].locations, ("app/src/main/A.kt",))
            removed, added, _ = compare(*issues, [])
            self.assertFalse(removed)
            self.assertFalse(added)


if __name__ == "__main__":
    unittest.main()
