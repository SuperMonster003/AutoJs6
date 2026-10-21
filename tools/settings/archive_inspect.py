"""Inspect a settings archive of AutoJs6 (docs/settings/ROADMAP.md P6.4, appendix B "format v1", T7).

Usage: py -3.12 -I tools/settings/archive_inspect.py ARCHIVE [--expect-categories N] [--expect-ids ID,ID,...]
       [--set-format-version N --out OUT]

Prints a summary of manifest.json (format, version, export time, host, device) and one line per category with the
number of entries the manifest lists and the number the category file holds, then checks the structure:
  - the file is a zip of at most 16 MB whose first entry is manifest.json, and no file in it inflates beyond 16 MB;
  - manifest.json has the format "autojs6-settings", a formatVersion >= 1 and every required field (appendix B);
  - every category of the manifest has categories/<id>.json, whose "id" matches and whose stores hold exactly the
    listed number of entries, each a {"t", "v"} pair with a known type tag and a value of that type;
  - only "credentials" is flagged sensitive; every data entry has its file under data/; nothing else is in the zip
    except the reserved plugins/ directory (D3);
  - with --expect-categories / --expect-ids, the manifest lists that many categories / exactly those ids in that order.
Values are never printed (the archive may hold credentials, R6). The exit status is 1 when a check fails.

--set-format-version writes a copy of ARCHIVE to OUT with manifest.json's formatVersion replaced (appendix D item 3).
"""
import argparse
import json
import pathlib
import sys
import zipfile

MAX_BYTES = 16 * 1024 * 1024
FORMAT = "autojs6-settings"
TAGS = {"bool", "int", "long", "float", "string", "stringSet", "json"}
INT_RANGE = (-2 ** 31, 2 ** 31 - 1)
LONG_RANGE = (-2 ** 63, 2 ** 63 - 1)


def value_matches(tag, value):
    if tag == "bool":
        return isinstance(value, bool)
    if tag in ("int", "long"):
        low, high = INT_RANGE if tag == "int" else LONG_RANGE
        return isinstance(value, int) and not isinstance(value, bool) and low <= value <= high
    if tag == "float":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if tag == "string":
        return isinstance(value, str)
    if tag == "stringSet":
        return isinstance(value, list) and all(isinstance(item, str) for item in value)
    return tag == "json"


class Inspector:
    def __init__(self):
        self.problems = []

    def fail(self, message):
        self.problems.append(message)

    def inspect(self, path, expect_count=None, expect_ids=None):
        size = path.stat().st_size
        if size > MAX_BYTES:
            self.fail(f"the archive has {size} bytes, more than {MAX_BYTES}")
        try:
            archive = zipfile.ZipFile(path)
        except zipfile.BadZipFile as e:
            self.fail(f"not a zip archive: {e}")
            return None
        with archive:
            infos = archive.infolist()
            names = [info.filename for info in infos]
            if not names:
                self.fail("the zip holds no file")
                return None
            if names[0] != "manifest.json":
                self.fail(f"the first entry is {names[0]}, not manifest.json")
            for info in infos:
                if info.file_size > MAX_BYTES:
                    self.fail(f"{info.filename} inflates to {info.file_size} bytes, more than {MAX_BYTES}")
            if "manifest.json" not in names:
                self.fail("manifest.json is missing")
                return None
            manifest = self.read_json(archive, "manifest.json")
            if not isinstance(manifest, dict):
                self.fail("manifest.json is not a JSON object")
                return None
            rows = self.check_manifest(archive, manifest, names)
            if expect_count is not None and len(manifest.get("categories", [])) != expect_count:
                self.fail(f"the manifest lists {len(manifest.get('categories', []))} categories, expected {expect_count}")
            if expect_ids is not None:
                ids = [entry.get("id") for entry in manifest.get("categories", [])]
                if ids != expect_ids:
                    self.fail(f"the manifest lists {ids}, expected {expect_ids}")
            return size, manifest, rows

    def read_json(self, archive, name):
        try:
            return json.loads(archive.read(name).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            self.fail(f"{name} is not valid JSON: {e}")
            return None

    def check_manifest(self, archive, manifest, names):
        if manifest.get("format") != FORMAT:
            self.fail(f"format is {manifest.get('format')!r}, not {FORMAT!r}")
        version = manifest.get("formatVersion")
        if not isinstance(version, int) or isinstance(version, bool) or version < 1:
            self.fail(f"formatVersion {version!r} is not an integer >= 1")
        if not isinstance(manifest.get("exportedAt"), str):
            self.fail("exportedAt is missing")
        host = manifest.get("host")
        if not (isinstance(host, dict) and isinstance(host.get("packageName"), str) and isinstance(host.get("versionCode"), int)
                and isinstance(host.get("versionName"), str)):
            self.fail("host lacks packageName / versionCode / versionName")
        device = manifest.get("device")
        if not (isinstance(device, dict) and isinstance(device.get("model"), str) and isinstance(device.get("sdk"), int)):
            self.fail("device lacks model / sdk")
        categories = manifest.get("categories")
        if not isinstance(categories, list):
            self.fail("categories is not an array")
            categories = []
        expected_files = {"manifest.json"}
        rows = []
        seen = set()
        for entry in categories:
            if not isinstance(entry, dict) or not isinstance(entry.get("id"), str):
                self.fail(f"a category entry is malformed: {entry!r}")
                continue
            cid = entry["id"]
            if cid in seen:
                self.fail(f"{cid} is listed twice")
            seen.add(cid)
            if not isinstance(entry.get("sensitive"), bool):
                self.fail(f"{cid}: no sensitive flag")
            elif entry["sensitive"] != (cid == "credentials"):
                self.fail(f"{cid}: sensitive is {entry['sensitive']}")
            listed = entry.get("entries")
            file = f"categories/{cid}.json"
            expected_files.add(file)
            held, stores = self.check_category(archive, file, cid, names)
            if listed != held:
                self.fail(f"{cid}: the manifest lists {listed} entries, the file holds {held}")
            rows.append((cid, listed, held, stores, entry.get("sensitive")))
        for entry in manifest.get("data") or []:
            file = entry.get("file") if isinstance(entry, dict) else None
            if not (isinstance(file, str) and file.startswith("data/")):
                self.fail(f"a data entry is malformed: {entry!r}")
                continue
            expected_files.add(file)
            if file not in names:
                self.fail(f"{entry.get('id')}: {file} is missing")
            rows.append((entry.get("id"), entry.get("entries"), None, file, False))
        if not isinstance(manifest.get("plugins", []), list):
            self.fail("plugins is not an array")
        extra = [n for n in names if n not in expected_files and not n.startswith("plugins/")]
        if extra:
            self.fail(f"unexpected files: {extra}")
        return rows

    def check_category(self, archive, file, cid, names):
        if file not in names:
            self.fail(f"{cid}: {file} is missing")
            return None, 0
        doc = self.read_json(archive, file)
        if not isinstance(doc, dict) or doc.get("id") != cid or not isinstance(doc.get("stores"), dict):
            self.fail(f"{file}: not a category file of {cid}")
            return None, 0
        held = 0
        for store, entries in doc["stores"].items():
            if not isinstance(entries, dict):
                self.fail(f"{file}: store {store} is not an object")
                continue
            for name, value in entries.items():
                held += 1
                if not (isinstance(value, dict) and set(value) == {"t", "v"} and value["t"] in TAGS):
                    self.fail(f"{file}: {store}/{name} is not a typed value")
                elif not value_matches(value["t"], value["v"]):
                    self.fail(f"{file}: {store}/{name} does not hold a {value['t']}")
        return held, len(doc["stores"])


def set_format_version(source, out, version):
    with zipfile.ZipFile(source) as archive, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as copy:
        for info in archive.infolist():
            data = archive.read(info.filename)
            if info.filename == "manifest.json":
                manifest = json.loads(data.decode("utf-8"))
                manifest["formatVersion"] = version
                data = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
            copy.writestr(info.filename, data)
    print(f"wrote {out} with formatVersion {version}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("archive", type=pathlib.Path)
    parser.add_argument("--expect-categories", type=int, help="number of categories the manifest must list")
    parser.add_argument("--expect-ids", help="category ids the manifest must list, comma separated, in order")
    parser.add_argument("--set-format-version", type=int, help="write a copy with this formatVersion to --out")
    parser.add_argument("--out", type=pathlib.Path, help="output of --set-format-version")
    args = parser.parse_args()

    if args.set_format_version is not None:
        if not args.out:
            parser.error("--out is required with --set-format-version")
        set_format_version(args.archive, args.out, args.set_format_version)
        return 0
    expect_ids = [i.strip() for i in args.expect_ids.split(",") if i.strip()] if args.expect_ids else None
    inspector = Inspector()
    result = inspector.inspect(args.archive, args.expect_categories, expect_ids)
    if result:
        size, manifest, rows = result
        host, device = manifest.get("host") or {}, manifest.get("device") or {}
        print(f"archive {args.archive.name}: {size} bytes")
        print(f"manifest: {manifest.get('format')} v{manifest.get('formatVersion')}, exported {manifest.get('exportedAt')}")
        print(f"host: {host.get('packageName')} {host.get('versionName')} ({host.get('versionCode')}); "
              f"device: {device.get('model')}, API {device.get('sdk')}")
        settings = [row for row in rows if not isinstance(row[3], str)]
        data = [row for row in rows if isinstance(row[3], str)]
        total = sum(row[1] or 0 for row in settings)
        print(f"categories: {len(settings)}, entries: {total}; data: {len(data)}; plugins: {len(manifest.get('plugins') or [])}")
        for cid, listed, held, stores, sensitive in rows:
            if isinstance(stores, str):
                print(f"  {cid:<22} {listed!s:>5} entries  {stores}")
            else:
                note = ", sensitive" if sensitive else ""
                print(f"  {cid:<22} {listed!s:>5} entries  ({held} in the file, {stores} store{'s' if stores != 1 else ''}{note})")
    for problem in inspector.problems:
        print(f"FAIL {problem}")
    print(f"structure: {'FAIL' if inspector.problems else 'PASS'} ({len(inspector.problems)} problems)")
    return 1 if inspector.problems else 0


if __name__ == "__main__":
    sys.exit(main())
