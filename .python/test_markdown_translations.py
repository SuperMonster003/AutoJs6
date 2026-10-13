"""Regression coverage for drift between release-history source and translations."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from check_translations import CATEGORIES, LABELS, LANGUAGES, check_translations, record_translation_baseline


class ChangelogTranslationsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="autojs6-changelog-check-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / ".changelog").mkdir()
        self.document = {"$data": {"v1.0.0": {
            "released_date": "2026/10/13",
            "released_hint": "Read before upgrading",
            "feature": [
                "API using `a.b()` _[`issue #1`](http://issues.autojs6.com/1)_",
                "Another API (by [Author](https://github.com/Author))",
            ],
            "dependency": ["Upgraded Example version 1.0 -> 2.0"],
        }}}
        for language in LANGUAGES:
            document = copy.deepcopy(self.document)
            document.update({f"changelog_label_{category}": label
                             for category, label in zip(CATEGORIES, LABELS[language])})
            self.write(language, document)
        record_translation_baseline(self.root)

    def write(self, language, document):
        path = self.root / ".changelog" / f"lang_{language}.json"
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def mutate(self, change):
        path = self.root / ".changelog/lang_en.json"
        document = json.loads(path.read_text(encoding="utf-8"))
        change(document["$data"]["v1.0.0"])
        self.write("en", document)

    def snapshot(self):
        return {path.name: (hashlib.sha256(path.read_bytes()).hexdigest(), path.stat().st_mtime_ns)
                for path in (self.root / ".changelog").iterdir()}

    def assert_rejected_without_writes(self, expected):
        before = self.snapshot()
        errors = check_translations(self.root)
        self.assertTrue(any(expected in error for error in errors), errors)
        self.assertEqual(before, self.snapshot())

    def test_synchronized_sources_are_read_only(self):
        before = self.snapshot()
        self.assertEqual(check_translations(self.root), [])
        self.assertEqual(before, self.snapshot())

    def test_missing_translation_entry(self):
        self.mutate(lambda block: block["feature"].pop())
        self.assert_rejected_without_writes("expected 2 entries")

    def test_reordered_entries_preserve_count_but_break_correspondence(self):
        self.mutate(lambda block: block["feature"].reverse())
        self.assert_rejected_without_writes("URLs differ")

    def test_release_date_drift(self):
        self.mutate(lambda block: block.update(released_date="2026/10/12"))
        self.assert_rejected_without_writes("release date differs")

    def test_changed_reference_or_code_is_rejected(self):
        self.mutate(lambda block: block["feature"].__setitem__(0,
                    "API using `a.c()` _[`issue #1`](http://issues.autojs6.com/2)_"))
        self.assert_rejected_without_writes("URLs differ")
        self.assert_rejected_without_writes("inline code and citation labels differ")

    def test_contributor_name_cannot_be_translated(self):
        self.mutate(lambda block: block["feature"].__setitem__(1,
                    "Another API (by [Someone else](https://github.com/Author))"))
        self.assert_rejected_without_writes("attribution and repository link labels differ")

    def test_category_drift(self):
        self.mutate(lambda block: block.update(improvement=block.pop("dependency")))
        self.assert_rejected_without_writes("fields or category order differ")

    def test_duplicate_keys_are_rejected(self):
        path = self.root / ".changelog/lang_en.json"
        text = path.read_text(encoding="utf-8").replace('"$data":', '"$data": {}, "$data":', 1)
        path.write_text(text, encoding="utf-8")
        self.assert_rejected_without_writes("Duplicate JSON key")

    def test_non_ascii_punctuation_is_rejected(self):
        self.mutate(lambda block: block.update(released_hint="Read\u2014before upgrading"))
        self.assert_rejected_without_writes("non-ASCII punctuation")

    def test_missing_language_is_rejected(self):
        (self.root / ".changelog/lang_fr.json").unlink()
        self.assert_rejected_without_writes("expected exactly 10 language sources")

    def test_reference_order_within_a_sentence_can_follow_target_grammar(self):
        original = "[A](https://example.com/a) replaces [B](https://example.com/b)"
        translated = "Replaced [B](https://example.com/b) with [A](https://example.com/a)"
        for language in LANGUAGES:
            path = self.root / ".changelog" / f"lang_{language}.json"
            document = json.loads(path.read_text(encoding="utf-8"))
            document["$data"]["v1.0.0"]["feature"][0] = original if language == "zh-Hans" else translated
            self.write(language, document)
        record_translation_baseline(self.root)
        self.assertEqual(check_translations(self.root), [])

    def test_dependency_version_chain_is_preserved(self):
        self.mutate(lambda block: block["dependency"].__setitem__(0, "Upgraded Example version 1.0 -> 99.0"))
        self.assert_rejected_without_writes("dependency version sequence differs")

    def test_plain_api_identifiers_are_preserved(self):
        for language in LANGUAGES:
            path = self.root / ".changelog" / f"lang_{language}.json"
            document = json.loads(path.read_text(encoding="utf-8"))
            document["$data"]["v1.0.0"]["feature"][0] = "images.psnr method"
            self.write(language, document)
        record_translation_baseline(self.root)
        self.mutate(lambda block: block["feature"].__setitem__(0, "images.mse method"))
        self.assert_rejected_without_writes("missing API identifiers")

    def test_source_prose_change_requires_review_even_when_counts_match(self):
        path = self.root / ".changelog/lang_zh-Hans.json"
        document = json.loads(path.read_text(encoding="utf-8"))
        document["$data"]["v1.0.0"]["released_hint"] = "Manual migration is required"
        self.write("zh-Hans", document)
        self.assert_rejected_without_writes("Reviewed translation baseline differs")

    def test_plain_prose_reordering_requires_review(self):
        for language in LANGUAGES:
            path = self.root / ".changelog" / f"lang_{language}.json"
            document = json.loads(path.read_text(encoding="utf-8"))
            document["$data"]["v1.0.0"]["feature"] = ["First behavior", "Second behavior"]
            self.write(language, document)
        record_translation_baseline(self.root)
        self.mutate(lambda block: block["feature"].reverse())
        self.assert_rejected_without_writes("Reviewed translation baseline differs")

    def test_recording_cannot_approve_a_structural_mismatch(self):
        self.mutate(lambda block: block["feature"].pop())
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, "expected 2 entries"):
            record_translation_baseline(self.root)
        self.assertEqual(before, self.snapshot())

    def test_json_formatting_does_not_invalidate_the_review(self):
        path = self.root / ".changelog/lang_en.json"
        document = json.loads(path.read_text(encoding="utf-8"))
        path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
        self.assertEqual(check_translations(self.root), [])

    def test_numeric_units_and_korean_particles_are_not_version_suffixes(self):
        for language in LANGUAGES:
            path = self.root / ".changelog" / f"lang_{language}.json"
            document = json.loads(path.read_text(encoding="utf-8"))
            document["$data"]["v1.0.0"]["feature"][0] = (
                "64 bit; SDK 29 -> 37" if language == "zh-Hans" else "64-bit; SDK 29\uc5d0\uc11c 37\ub85c")
            self.write(language, document)
        record_translation_baseline(self.root)
        self.assertEqual(check_translations(self.root), [])


if __name__ == "__main__":
    unittest.main()
