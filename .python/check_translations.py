"""Check release-history translations against the Simplified Chinese source.

This is an offline, read-only check. It validates correspondence and protected
content, not the linguistic accuracy of a translation. Chinese-only drafts can
still be rendered; --check and CI require all translations to be synchronized.

After reviewing all nine translations against the Chinese source, explicitly
record that review with: python .python/check_translations.py --record-sync
Ordinary generation never updates the reviewed-source baseline automatically.
"""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import sys
import unicodedata

LANGUAGES = ("zh-Hans", "zh-Hant-HK", "zh-Hant-TW", "en", "fr", "es", "ja", "ko", "ru", "ar")
CATEGORIES = ("hint", "feature", "fix", "improvement", "dependency")
LABELS = {
    "zh-Hans": ("提示", "新增", "修复", "优化", "依赖"),
    "zh-Hant-HK": ("提示", "新增", "修復", "優化", "依賴"),
    "zh-Hant-TW": ("提示", "新增", "修復", "最佳化", "依賴"),
    "en": ("Hint", "Feature", "Fix", "Improvement", "Dependency"),
    "fr": ("Astuce", "Fonctionnalité", "Correction", "Amélioration", "Dépendance"),
    "es": ("Sugerencia", "Nuevo", "Corrección", "Mejora", "Dependencia"),
    "ja": ("ヒント", "新機能", "修正", "改善", "依存関係"),
    "ko": ("힌트", "추가", "수정", "개선", "의존성"),
    "ru": ("Подсказка", "Новое", "Исправление", "Улучшение", "Зависимость"),
    "ar": ("تلميح", "إضافة جديدة", "تصليح", "تحسين", "التبعيات"),
}
PROTECTED = {
    "URLs": re.compile(r"https?://[^\s)<>]+"),
    "inline code and citation labels": re.compile(r"`([^`]+)`"),
    "attribution and repository link labels": re.compile(r"\[([^\]]+)\]\((https?://github\.com/[^)]+)\)"),
}
BASELINE_FILE = "translation_baseline.json"
API_IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*(?:[.#][A-Za-z_$][A-Za-z0-9_$]*)+")
NUMBER = re.compile(r"(?<![A-Za-z0-9_])(?:\d+(?:\.\d+)+(?:-[A-Za-z][A-Za-z0-9.-]*)?|\d+)(?![A-Za-z0-9_])")


def text_without_urls(text):
    return PROTECTED["URLs"].sub("", text)


def content_digest(document):
    # Hash parsed values, not indentation or platform line endings. Preserve
    # version/category/entry order because it is part of the review contract.
    content = json.dumps(document, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def translation_baseline(root):
    digests = {}
    for language in LANGUAGES:
        path = Path(root) / ".changelog" / f"lang_{language}.json"
        document = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
        digests[language] = content_digest(document)
    return {"schema": 1, "source_language": "zh-Hans", "reviewed_content_sha256": digests}


def record_translation_baseline(root):
    errors = check_translations(root, check_baseline=False)
    if errors:
        raise ValueError("\n".join(errors))
    baseline = translation_baseline(root)
    path = Path(root) / ".changelog" / BASELINE_FILE
    path.write_text(json.dumps(baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def validate_text(value, location, errors):
    if not isinstance(value, str) or not value.strip():
        errors.append(f"{location}: expected nonempty text")
        return False
    if value != value.strip():
        errors.append(f"{location}: unexpected leading or trailing whitespace")
    invalid = sorted({character for character in value if ord(character) > 127 and (
        (unicodedata.category(character).startswith("P") and character != "\u00b7")
        or 0x2190 <= ord(character) <= 0x21FF
        or character == "\u3000" or 0xFF01 <= ord(character) <= 0xFF65
    )})
    if invalid:
        errors.append(f"{location}: non-ASCII punctuation: " + ", ".join(f"U+{ord(c):04X}" for c in invalid))
    return True


def validate_structure(document, language, errors):
    location = f"lang_{language}.json"
    if not isinstance(document, dict):
        errors.append(f"{location}: expected a JSON object")
        return False
    expected = {"$data", *(f"changelog_label_{category}" for category in CATEGORIES)}
    if set(document) != expected:
        errors.append(f"{location}: unexpected or missing top-level fields")
    for category, label in zip(CATEGORIES, LABELS[language]):
        if document.get(f"changelog_label_{category}") != label:
            errors.append(f"{location}: incorrect {category} label")
    data = document.get("$data")
    if not isinstance(data, dict) or not data:
        errors.append(f"{location}: expected nonempty $data")
        return False
    for version, block in data.items():
        where = f"{location}/{version}"
        if not re.fullmatch(r"v\d+\.\d+\.\d+", version):
            errors.append(f"{where}: invalid version")
        if not isinstance(block, dict):
            errors.append(f"{where}: expected a version object")
            continue
        if set(block) - {"released_date", "released_hint", *CATEGORIES}:
            errors.append(f"{where}: unknown version fields")
        date = block.get("released_date")
        try:
            if not isinstance(date, str) or not re.fullmatch(r"\d{4}/\d{2}/\d{2}", date):
                raise ValueError("expected YYYY/MM/DD")
            datetime.strptime(date, "%Y/%m/%d")
        except ValueError:
            errors.append(f"{where}: invalid release date")
        if "released_hint" in block:
            validate_text(block["released_hint"], f"{where}/released_hint", errors)
        actual_categories = [key for key in block if key in CATEGORIES]
        if actual_categories != [key for key in CATEGORIES if key in block]:
            errors.append(f"{where}: category order differs from hint/feature/fix/improvement/dependency")
        for category in actual_categories:
            entries = block[category]
            if not isinstance(entries, list) or not entries:
                errors.append(f"{where}/{category}: expected a nonempty list")
                continue
            seen = set()
            for index, entry in enumerate(entries):
                if validate_text(entry, f"{where}/{category}/{index}", errors):
                    if entry in seen:
                        errors.append(f"{where}/{category}/{index}: duplicate entry")
                    seen.add(entry)
    return True


def check_translations(root, check_baseline=True):
    root = Path(root)
    folder = root / ".changelog"
    errors = []
    expected_files = {f"lang_{language}.json" for language in LANGUAGES}
    actual_files = {path.name for path in folder.glob("lang_*.json")}
    if actual_files != expected_files:
        errors.append(".changelog: expected exactly 10 language sources; missing="
                      + str(sorted(expected_files - actual_files)) + "; extra="
                      + str(sorted(actual_files - expected_files)))
    documents = {}
    for language in LANGUAGES:
        path = folder / f"lang_{language}.json"
        try:
            document = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
        except (OSError, ValueError) as error:
            errors.append(f"{path.name}: {error}")
            continue
        if validate_structure(document, language, errors):
            documents[language] = document["$data"]
    source = documents.get("zh-Hans")
    if source is None:
        return errors
    for language, translated in documents.items():
        if language == "zh-Hans":
            continue
        if list(translated) != list(source):
            errors.append(f"{language}: version set or order differs from zh-Hans")
        for version, block in source.items():
            target = translated.get(version)
            if not isinstance(block, dict) or not isinstance(target, dict):
                continue
            where = f"{language}/{version}"
            if list(target) != list(block):
                errors.append(f"{where}: fields or category order differ from zh-Hans")
            if target.get("released_date") != block.get("released_date"):
                errors.append(f"{where}: release date differs from zh-Hans")
            for category in (*CATEGORIES, "released_hint"):
                if category not in block:
                    continue
                entries = block[category] if category in CATEGORIES else [block[category]]
                others = target.get(category, []) if category in CATEGORIES else [target.get(category)]
                if not isinstance(entries, list) or not isinstance(others, list):
                    continue
                if len(others) != len(entries):
                    errors.append(f"{where}/{category}: expected {len(entries)} entries, found {len(others)}")
                    continue
                for index, (entry, other) in enumerate(zip(entries, others)):
                    if not isinstance(entry, str) or not isinstance(other, str):
                        continue
                    for description, pattern in PROTECTED.items():
                        if Counter(pattern.findall(entry)) != Counter(pattern.findall(other)):
                            errors.append(f"{where}/{category}/{index}: {description} differ from zh-Hans")
                    source_text, target_text = text_without_urls(entry), text_without_urls(other)
                    missing_identifiers = sorted({token for token in API_IDENTIFIER.findall(source_text) if token not in target_text})
                    if missing_identifiers:
                        errors.append(f"{where}/{category}/{index}: missing API identifiers: {', '.join(missing_identifiers)}")
                    source_numbers, target_numbers = NUMBER.findall(source_text), NUMBER.findall(target_text)
                    if category == "dependency":
                        if source_numbers != target_numbers:
                            errors.append(f"{where}/{category}/{index}: dependency version sequence differs from zh-Hans")
                        dependency_name = source_text.split(" 版本 ", 1)[0]
                        name_tokens = re.findall(r"[A-Za-z][A-Za-z0-9_.+-]*", dependency_name)
                        missing_names = [token for token in name_tokens if token not in target_text]
                        if missing_names:
                            errors.append(f"{where}/{category}/{index}: missing dependency names: {', '.join(missing_names)}")
                    elif Counter(source_numbers) - Counter(target_numbers):
                        errors.append(f"{where}/{category}/{index}: numeric conditions differ from zh-Hans")
    if check_baseline and not errors:
        path = folder / BASELINE_FILE
        try:
            recorded = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
            actual = translation_baseline(root)
            if recorded != actual:
                errors.append("Reviewed translation baseline differs from source content; review all affected languages, "
                              "then explicitly run .python/check_translations.py --record-sync")
        except (OSError, ValueError) as error:
            errors.append(f"{BASELINE_FILE}: {error}; record the baseline after reviewing all translations")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record-sync", action="store_true", help="explicitly record reviewed translations after structural validation")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.record_sync:
        try:
            record_translation_baseline(root)
        except (OSError, ValueError) as error:
            print(f"CHANGELOG_TRANSLATIONS_ERROR\n{error}", file=sys.stderr)
            return 1
        print("CHANGELOG_TRANSLATIONS_RECORDED languages=10")
        return 0
    errors = check_translations(root)
    if errors:
        print("CHANGELOG_TRANSLATIONS_ERROR\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print("CHANGELOG_TRANSLATIONS_OK languages=10")
    return 0


if __name__ == "__main__":
    sys.exit(main())
