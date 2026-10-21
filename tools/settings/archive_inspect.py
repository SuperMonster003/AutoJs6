"""Inspect a settings archive of AutoJs6 (docs/settings/ROADMAP.md P6.4 / P6.5, appendix B "format v1", T7 / T21).

Usage: py -3.12 -I tools/settings/archive_inspect.py ARCHIVE [--expect-categories N] [--expect-ids ID,ID,...]
       [--set-format-version N --out OUT]

Prints a summary of manifest.json (format, version, export time, host, device) and one line per category with the
number of entries the manifest lists and the number the category file holds, then checks the structure:
  - the file is a zip of at most 16 MB whose first entry is manifest.json, and no file in it inflates beyond 16 MB;
  - manifest.json has the format "autojs6-settings", a formatVersion >= 1 and every required field (appendix B);
  - every category of the manifest has categories/<id>.json, whose "id" matches and whose stores hold exactly the
    listed number of entries, each a {"t", "v"} pair with a known type tag and a value of that type;
  - only "credentials" is flagged sensitive, and its file is a sealed envelope (D23): version 1, AES-256-GCM, a
    12-byte nonce, data with its 16-byte tag, and a device slot (HKDF-SHA256, a 16-digit hex fingerprint, a 12-byte
    nonce and a wrapped 32-byte key), and any password slot (P7.7) has PBKDF2-HMAC-SHA256, 1000 to 10000000
    iterations, a 16- to 64-byte salt, a 12-byte nonce and a wrapped 32-byte key; its entries cannot be counted
    without the key of the device, so the manifest's number is shown as listed;
  - every data entry has its file under data/, and the data categories of T19 (P10.1) are checked the way their pages
    read them, with the manifest's number of entries being the number of shortcuts / libraries the file holds:
    data_shortcuts is data/shortcuts.zip, a shortcut backup ("autojs6-shortcuts" v1 / v2 holding only manifest.json and
    icons/<n>.png, each declared icon present once and a PNG), data_color_library is data/color-library.json, a color
    library document ("autojs6.color-library" schemaVersion 1 with plain or intelligent libraries); data of other ids
    is only checked to exist;
  - every plugin entry (D3, P13.1) has a package name as id, listed once, a number of entries, and its file
    plugins/<id>/settings.json, a JSON object (the payload of the plugin, P13.2); nothing else is in the zip but
    directory entries under plugins/;
  - with --expect-categories / --expect-ids, the manifest lists that many categories / exactly those ids in that order.
Values are never printed (the archive may hold credentials, R6). The exit status is 1 when a check fails.

--set-format-version writes a copy of ARCHIVE to OUT with manifest.json's formatVersion replaced (appendix D item 3).
"""
import argparse
import base64
import binascii
import io
import json
import pathlib
import re
import sys
import zipfile

MAX_BYTES = 16 * 1024 * 1024
FORMAT = "autojs6-settings"
TAGS = {"bool", "int", "long", "float", "string", "stringSet", "json"}
INT_RANGE = (-2 ** 31, 2 ** 31 - 1)
FINGERPRINT = re.compile(r"[0-9a-f]{16}")
LONG_RANGE = (-2 ** 63, 2 ** 63 - 1)

# The data categories of T19 and their files (P10.1); the limits are those of ShortcutBackupArchive and ColorLibraryJson.
DATA_FILES = {"data_shortcuts": "data/shortcuts.zip", "data_color_library": "data/color-library.json"}
SHORTCUT_ICON = re.compile(r"icons/[0-9]+\.png")
SHORTCUT_TARGETS = {"script", "settings", "docs", "log", "plugins"}
SHORTCUT_STATUSES = {"PENDING", "PINNED", "MISSING", "SAVED", "REMOVED"}
SHORTCUT_LIMITS = {"entries": 1000, "icon": 1024 * 1024, "manifest": 4 * 1024 * 1024, "total": 64 * 1024 * 1024}
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
COLOR = re.compile(r"#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")
COLOR_LIBRARY_LIMITS = {"bytes": 2 * 1024 * 1024, "libraries": 256, "items": 4096}
PACKAGE_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+")


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
            if held == "sealed":
                # The entries of a sealed file cannot be counted without the device key (D23); stores None marks it.
                if not entry.get("sensitive"):
                    self.fail(f"{cid}: the file is sealed but the category is not sensitive")
                rows.append((cid, listed, None, None, entry.get("sensitive")))
                continue
            if entry.get("sensitive") and held is not None:
                self.fail(f"{cid}: a sensitive category is written in plain text, not sealed (D23)")
            if listed != held:
                self.fail(f"{cid}: the manifest lists {listed} entries, the file holds {held}")
            rows.append((cid, listed, held, stores, entry.get("sensitive")))
        for entry in manifest.get("data") or []:
            file = entry.get("file") if isinstance(entry, dict) else None
            if not (isinstance(file, str) and file.startswith("data/")):
                self.fail(f"a data entry is malformed: {entry!r}")
                continue
            did = entry.get("id")
            expected_files.add(file)
            held = None
            if file not in names:
                self.fail(f"{did}: {file} is missing")
            elif did in DATA_FILES:
                held = self.check_data(archive, did, file, entry.get("entries"))
            rows.append((did, entry.get("entries"), held, file, False))
        self.check_plugins(archive, manifest.get("plugins", []), names, expected_files)
        extra = [n for n in names if n not in expected_files and not (n.startswith("plugins/") and n.endswith("/"))]
        if extra:
            self.fail(f"unexpected files: {extra}")
        return rows

    def check_plugins(self, archive, plugins, names, expected_files):
        """The plugin directories of P13.1 (D3): plugins/<packageName>/settings.json per listed plugin."""
        if not isinstance(plugins, list):
            self.fail("plugins is not an array")
            return
        seen = set()
        for entry in plugins:
            pid = entry.get("id") if isinstance(entry, dict) else None
            listed = entry.get("entries") if isinstance(entry, dict) else None
            if not (isinstance(pid, str) and PACKAGE_NAME.fullmatch(pid)) or isinstance(listed, bool) or not isinstance(listed, int) or listed < 0:
                self.fail(f"a plugin entry is malformed: {entry!r}")
                continue
            if pid in seen:
                self.fail(f"plugin {pid} is listed twice")
            seen.add(pid)
            file = f"plugins/{pid}/settings.json"
            expected_files.add(file)
            if file not in names:
                self.fail(f"plugin {pid}: {file} is missing")
                continue
            doc = self.read_json(archive, file)
            if doc is not None and not isinstance(doc, dict):
                self.fail(f"{file}: not a JSON object")

    def check_data(self, archive, did, file, listed):
        """Checks the file of a data category of T19 and returns the number of entries it holds (None if unreadable)."""
        if file != DATA_FILES[did]:
            self.fail(f"{did}: the file is {file}, not {DATA_FILES[did]}")
        raw = archive.read(file)
        held = self.check_shortcuts(file, raw) if did == "data_shortcuts" else self.check_color_library(file, raw)
        if held is not None and listed != held:
            self.fail(f"{did}: the manifest lists {listed} entries, the file holds {held}")
        return held

    def check_shortcuts(self, file, raw):
        """data/shortcuts.zip as ShortcutBackupArchive.read reads it; labels, paths and ids are never printed."""
        try:
            inner = zipfile.ZipFile(io.BytesIO(raw))
        except zipfile.BadZipFile as e:
            self.fail(f"{file}: not a zip archive: {e}")
            return None
        with inner:
            infos = inner.infolist()
            names = [info.filename for info in infos]
            unexpected = [n for n in names if n != "manifest.json" and not SHORTCUT_ICON.fullmatch(n)]
            if unexpected or len(set(names)) != len(names):
                self.fail(f"{file}: unexpected or duplicate entries: {sorted(set(unexpected)) or 'duplicates'}")
            for info in infos:
                limit = SHORTCUT_LIMITS["manifest" if info.filename == "manifest.json" else "icon"]
                if info.file_size > limit:
                    self.fail(f"{file}: {info.filename} inflates to {info.file_size} bytes, more than {limit}")
            if sum(info.file_size for info in infos) > SHORTCUT_LIMITS["total"]:
                self.fail(f"{file}: the shortcut backup inflates to more than {SHORTCUT_LIMITS['total']} bytes")
            if "manifest.json" not in names:
                self.fail(f"{file}: manifest.json is missing")
                return None
            try:
                root = json.loads(inner.read("manifest.json").decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as e:
                self.fail(f"{file}: manifest.json is not valid JSON: {e}")
                return None
            version = root.get("version") if isinstance(root, dict) else None
            if not isinstance(root, dict) or root.get("format") != "autojs6-shortcuts" or isinstance(version, bool) or str(version) not in ("1", "2"):
                self.fail(f"{file}: not a shortcut backup of format autojs6-shortcuts v1 / v2")
                return None
            shortcuts = root.get("shortcuts")
            if not isinstance(shortcuts, list) or len(shortcuts) > SHORTCUT_LIMITS["entries"]:
                self.fail(f"{file}: shortcuts is not an array of at most {SHORTCUT_LIMITS['entries']} items")
                return None
            ids, icons = [], []
            for index, item in enumerate(shortcuts):
                fields = ("id", "shortLabel", "longLabel", "target", "status")
                if not isinstance(item, dict) or not all(isinstance(item.get(k), str) and item[k].strip() for k in fields):
                    self.fail(f"{file}: shortcut {index} lacks id / shortLabel / longLabel / target / status")
                    continue
                ids.append(item["id"])
                if item["target"] not in SHORTCUT_TARGETS or item["status"] not in SHORTCUT_STATUSES:
                    self.fail(f"{file}: shortcut {index} has an unknown target or status")
                if item["target"] == "script" and not str(item.get("path") or "").startswith("/"):
                    self.fail(f"{file}: shortcut {index} is a script without an absolute path")
                if "icon" not in item or str(version) == "1" and item["icon"] is None:
                    self.fail(f"{file}: shortcut {index} declares no icon")
                elif item["icon"] is not None:
                    icon = item["icon"]
                    if not isinstance(icon, str) or icon not in names:
                        self.fail(f"{file}: the icon of shortcut {index} is missing")
                    else:
                        icons.append(icon)
                        if not inner.read(icon).startswith(PNG_SIGNATURE):
                            self.fail(f"{file}: the icon of shortcut {index} is not a PNG")
            if len(set(ids)) != len(ids):
                self.fail(f"{file}: shortcut ids are not unique")
            if len(set(icons)) != len(icons) or set(icons) != {n for n in names if n != "manifest.json"}:
                self.fail(f"{file}: icons are shared or not declared by any shortcut")
            return len(shortcuts)

    def check_color_library(self, file, raw):
        """data/color-library.json as ColorLibraryJson.decode reads it; library and color names are never printed."""
        if len(raw) > COLOR_LIBRARY_LIMITS["bytes"]:
            self.fail(f"{file}: {len(raw)} bytes, more than {COLOR_LIBRARY_LIMITS['bytes']}")
        try:
            doc = json.loads(raw.decode("utf-8").removeprefix("\ufeff"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            self.fail(f"{file}: not valid JSON: {e}")
            return None
        version = doc.get("schemaVersion") if isinstance(doc, dict) else None
        if not isinstance(doc, dict) or doc.get("format") != "autojs6.color-library":
            self.fail(f"{file}: not a color library document (format autojs6.color-library)")
            return None
        if isinstance(version, bool) or not isinstance(version, (int, float)) or version != 1:
            self.fail(f"{file}: schemaVersion {version!r} is not 1")
            return None
        libraries = doc.get("libraries")
        if not isinstance(libraries, list) or not libraries or len(libraries) > COLOR_LIBRARY_LIMITS["libraries"]:
            self.fail(f"{file}: libraries is not an array of 1 to {COLOR_LIBRARY_LIMITS['libraries']} libraries")
            return None
        for index, library in enumerate(libraries):
            kind = library.get("type") if isinstance(library, dict) else None
            items = library.get("colors" if kind == "plain" else "groups") if kind in ("plain", "intelligent") else None
            if not isinstance(items, list):
                self.fail(f"{file}: library {index} is not a plain library with colors or an intelligent one with groups")
                continue
            if len(items) > COLOR_LIBRARY_LIMITS["items"]:
                self.fail(f"{file}: library {index} holds {len(items)} items, more than {COLOR_LIBRARY_LIMITS['items']}")
            if kind == "plain" and not all(isinstance(c, dict) and COLOR.fullmatch(str(c.get("color"))) for c in items):
                self.fail(f"{file}: library {index} holds a color that is not #RRGGBB")
        return len(libraries)

    def check_sealed(self, file, cid, doc):
        if set(doc) != {"id", "sealed"} or doc.get("id") != cid:
            self.fail(f"{file}: a sealed file holds exactly the id {cid} and the sealed object")
        sealed = doc.get("sealed")
        if not isinstance(sealed, dict):
            self.fail(f"{file}: sealed is not an object")
            return
        if sealed.get("version") != 1 or sealed.get("cipher") != "AES-256-GCM":
            self.fail(f"{file}: sealing version {sealed.get('version')!r} / cipher {sealed.get('cipher')!r} is not 1 / AES-256-GCM")
        if b64(sealed.get("nonce"), size=12) is None:
            self.fail(f"{file}: the nonce is not 12 bytes of Base64")
        if b64(sealed.get("data"), at_least=16) is None:
            self.fail(f"{file}: the data is not Base64 of at least the 16-byte tag")
        slots = sealed.get("slots")
        if not isinstance(slots, list) or not slots:
            self.fail(f"{file}: no slots")
            return
        devices = [slot for slot in slots if isinstance(slot, dict) and slot.get("kind") == "device"]
        if not devices:
            self.fail(f"{file}: no device slot")
        for slot in devices:
            if slot.get("kdf") != "HKDF-SHA256" or not FINGERPRINT.fullmatch(str(slot.get("fingerprint"))):
                self.fail(f"{file}: a device slot lacks the kdf HKDF-SHA256 or a 16-digit hex fingerprint")
            if b64(slot.get("nonce"), size=12) is None or b64(slot.get("key"), size=48) is None:
                self.fail(f"{file}: a device slot lacks a 12-byte nonce or a wrapped 32-byte key")
        for slot in (slot for slot in slots if isinstance(slot, dict) and slot.get("kind") == "password"):
            iterations = slot.get("iterations")
            if slot.get("kdf") != "PBKDF2-HMAC-SHA256" or isinstance(iterations, bool) or not isinstance(iterations, int)                     or not 1000 <= iterations <= 10_000_000:
                self.fail(f"{file}: a password slot lacks the kdf PBKDF2-HMAC-SHA256 or 1000 to 10000000 iterations")
            salt = b64(slot.get("salt"), at_least=16)
            if salt is None or len(salt) > 64 or b64(slot.get("nonce"), size=12) is None or b64(slot.get("key"), size=48) is None:
                self.fail(f"{file}: a password slot lacks a 16- to 64-byte salt, a 12-byte nonce or a wrapped 32-byte key")

    def check_category(self, archive, file, cid, names):
        if file not in names:
            self.fail(f"{cid}: {file} is missing")
            return None, 0
        doc = self.read_json(archive, file)
        if isinstance(doc, dict) and "sealed" in doc:
            self.check_sealed(file, cid, doc)
            return "sealed", 0
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


def b64(value, size=None, at_least=None):
    """The bytes of a standard Base64 string, or None when it is not one or has the wrong size."""
    if not isinstance(value, str):
        return None
    try:
        data = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError):
        return None
    if size is not None and len(data) != size or at_least is not None and len(data) < at_least:
        return None
    return data


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
            elif stores is None:
                print(f"  {cid:<22} {listed!s:>5} entries  (sealed for the device, D23; listed, not counted)")
            else:
                note = ", sensitive" if sensitive else ""
                print(f"  {cid:<22} {listed!s:>5} entries  ({held} in the file, {stores} store{'s' if stores != 1 else ''}{note})")
    for problem in inspector.problems:
        print(f"FAIL {problem}")
    print(f"structure: {'FAIL' if inspector.problems else 'PASS'} ({len(inspector.problems)} problems)")
    return 1 if inspector.problems else 0


if __name__ == "__main__":
    sys.exit(main())
