#!/usr/bin/env python3
"""Generate the Android XML attribute metadata of the ui-mode dynamic layout inflater.

Host roadmap docs/dev/android-xml-attributes-roadmap.md, A1 (3-Block Compose decision D65). The table tells the
inflater's fallback (A2 / A3) which attributes a view class or a layout params class takes, in which format, with
which enum / flag values and since which API level:

- `android:` attributes come from the Android SDK platforms android-24 ... android-37.0
  (data/res/values/attrs.xml, the public attribute lists public.xml / public-final.xml / public-staging.xml, and
  the class names of android.jar). An attribute is kept only where it is public; its API level is the first
  platform whose styleable lists it as a public attribute.
- `app:` attributes come from the AndroidX / Material AARs of AARS (res/values/values.xml and the class names of
  classes.jar), taken from the Gradle cache. Keep AARS in step with the versions the app's runtime classpath
  resolves (`gradlew :app:dependencies --configuration appDebugRuntimeClasspath`).

A styleable is kept when a class of the same simple name exists in its source (`TextView`, `MaterialButton`), and
`<Name>_Layout` / `<Name>_MarginLayout` styleables become the layout params of class `<Name>`. Classes map to
`<namespace>:<styleable>` keys by their fully qualified names; the runtime walks a view's class chain.

Usage:
    py .python/generate_android_attrs.py --sdk <Android SDK> [--gradle-home <GRADLE_USER_HOME>] [--check]

Output: app/src/main/assets/ui/android-attrs.json (sorted, compact). --check compares instead of writing and fails
on any difference.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import xml.etree.ElementTree as ElementTree
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/ui/android-attrs.json"
SCHEMA = 1

# Platform directories in API order; android-37.0 is API 37 (minor platforms such as android-36.1 are skipped).
SDK_LEVELS = [(level, f"android-{level}") for level in range(24, 37)] + [(37, "android-37.0")]

# AndroidX / Material libraries whose views the inflater creates (group, artifact, resolved version).
AARS = [
    ("androidx.appcompat", "appcompat", "1.7.1"),
    ("androidx.cardview", "cardview", "1.0.0"),
    ("androidx.constraintlayout", "constraintlayout", "2.2.1"),
    ("androidx.coordinatorlayout", "coordinatorlayout", "1.1.0"),
    ("androidx.drawerlayout", "drawerlayout", "1.1.1"),
    ("androidx.recyclerview", "recyclerview", "1.4.0"),
    ("androidx.swiperefreshlayout", "swiperefreshlayout", "1.1.0"),
    ("com.google.android.material", "material", "1.13.0"),
]

# Packages of android.jar whose classes the inflater may create.
ANDROID_PACKAGES = ("android/widget/", "android/view/", "android/webkit/")

FORMATS = ("reference", "color", "dimension", "float", "integer", "boolean", "string", "fraction", "enum", "flag")
FORMAT_ALIASES = {"flags": "flag"}


class AttrDef:
    """One attribute definition: its formats and enum / flag values, merged over every place that defines it."""

    def __init__(self) -> None:
        self.formats: set[str] = set()
        self.enums: dict[str, int] = {}
        self.flags: dict[str, int] = {}

    def merge(self, element: ElementTree.Element) -> None:
        for name in filter(None, (element.get("format") or "").split("|")):
            # ConstraintLayout writes format="flags" for one attribute; the resource compiler reads it as flag.
            name = FORMAT_ALIASES.get(name, name)
            if name not in FORMATS:
                raise SystemExit(f"unknown attr format {name} in {element.get('name')}")
            self.formats.add(name)
        for child in element:
            value = parse_int(child.get("value"))
            if child.tag == "enum":
                self.formats.add("enum")
                self.enums[child.get("name")] = value
            elif child.tag == "flag":
                self.formats.add("flag")
                self.flags[child.get("name")] = value

    def to_json(self) -> dict:
        data: dict = {"f": sorted(self.formats)}
        if self.enums:
            data["e"] = dict(sorted(self.enums.items()))
        if self.flags:
            data["g"] = dict(sorted(self.flags.items()))
        return data


def parse_int(text: str | None) -> int:
    if text is None:
        raise SystemExit("an enum / flag without a value")
    value = int(text.strip(), 0)
    # Flags are 32-bit masks written in hex; keep them as signed ints the way Java reads them.
    return value - (1 << 32) if value >= (1 << 31) else value


def split_name(name: str, default_ns: str) -> tuple[str, str]:
    if name.startswith("android:"):
        return "android", name[len("android:"):]
    return default_ns, name


class ValuesFile:
    """Attribute definitions and styleables of one values XML (SDK attrs.xml or an AAR's values.xml)."""

    def __init__(self, data: bytes, default_ns: str) -> None:
        self.attrs: dict[tuple[str, str], AttrDef] = {}
        self.styleables: dict[str, list[tuple[str, str]]] = {}
        root = ElementTree.fromstring(data)
        for element in root:
            if element.tag == "attr":
                self._define(element, default_ns)
            elif element.tag == "declare-styleable":
                members = []
                for child in element:
                    if child.tag != "attr":
                        continue
                    key = split_name(child.get("name"), default_ns)
                    if child.get("format") or len(child):
                        self._define(child, default_ns)
                    members.append(key)
                self.styleables.setdefault(element.get("name"), []).extend(members)

    def _define(self, element: ElementTree.Element, default_ns: str) -> None:
        key = split_name(element.get("name"), default_ns)
        self.attrs.setdefault(key, AttrDef()).merge(element)


def public_attrs(values_dir: Path) -> set[str]:
    names: set[str] = set()
    for file in ("public.xml", "public-final.xml", "public-staging.xml"):
        path = values_dir / file
        if not path.is_file():
            continue
        root = ElementTree.parse(path).getroot()
        for element in root.iter("public"):
            if element.get("type") == "attr" and element.get("name"):
                names.add(element.get("name"))
    if not names:
        raise SystemExit(f"no public attribute list in {values_dir}")
    return names


def class_names(jar: zipfile.ZipFile, prefixes: tuple[str, ...] | None = None) -> list[str]:
    names = []
    for entry in jar.namelist():
        if not entry.endswith(".class") or "$" in entry or entry.startswith("META-INF/"):
            continue
        if prefixes is not None and not (entry.startswith(prefixes) and entry.count("/") == 2):
            continue
        names.append(entry[: -len(".class")].replace("/", "."))
    return sorted(names)


def find_aar(gradle_home: Path, group: str, artifact: str, version: str) -> Path:
    base = gradle_home / "caches/modules-2/files-2.1" / group / artifact / version
    found = sorted(base.glob(f"*/{artifact}-{version}.aar"))
    if not found:
        raise SystemExit(f"{group}:{artifact}:{version} is not in {base}; build the app once so Gradle downloads it")
    return found[0]


def generate(sdk: Path, gradle_home: Path) -> dict:
    attrs: dict[str, dict[str, AttrDef]] = {"android": {}, "app": {}}
    styleables: dict[str, dict[str, int]] = {}
    classes: dict[str, list[str]] = {}

    # android: the newest platform defines formats and values; every platform contributes API levels.
    since: dict[str, dict[str, int]] = {}
    newest: ValuesFile | None = None
    for level, directory in SDK_LEVELS:
        values_dir = sdk / "platforms" / directory / "data/res/values"
        values = ValuesFile((values_dir / "attrs.xml").read_bytes(), "android")
        public = public_attrs(values_dir)
        for name, members in values.styleables.items():
            for ns, attr in members:
                if ns == "android" and attr in public:
                    since.setdefault(name, {}).setdefault(attr, level)
        newest = values
    assert newest is not None
    with zipfile.ZipFile(sdk / "platforms" / SDK_LEVELS[-1][1] / "android.jar") as jar:
        android_classes = class_names(jar, ANDROID_PACKAGES)
    by_simple = {name.rsplit(".", 1)[1]: name for name in android_classes}
    for styleable, members in sorted(since.items()):
        owner, kind = owner_of(styleable, by_simple)
        if owner is None:
            continue
        key = f"android:{styleable}"
        styleables[key] = dict(sorted(members.items()))
        classes.setdefault(owner + kind, []).append(key)
        for attr in members:
            attrs["android"][attr] = newest.attrs.get(("android", attr)) or AttrDef()

    # app: each library's own styleables for its own classes; their android: members reuse the SDK definitions.
    sources = []
    for group, artifact, version in AARS:
        aar = find_aar(gradle_home, group, artifact, version)
        sources.append(f"{group}:{artifact}:{version}")
        with zipfile.ZipFile(aar) as archive:
            values = ValuesFile(archive.read("res/values/values.xml"), "app") if "res/values/values.xml" in archive.namelist() else None
            with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as jar:
                library_classes = class_names(jar)
        if values is None:
            continue
        simple = {name.rsplit(".", 1)[1]: name for name in library_classes}
        for styleable, members in sorted(values.styleables.items()):
            owner, kind = owner_of(styleable, simple)
            if owner is None:
                continue
            key = f"app:{styleable}"
            entry = styleables.setdefault(key, {})
            for ns, attr in members:
                if ns == "android":
                    if attr not in attrs["android"] and ("android", attr) not in newest.attrs:
                        continue
                    attrs["android"].setdefault(attr, newest.attrs.get(("android", attr)) or AttrDef())
                    entry["android:" + attr] = 0
                else:
                    definition = values.attrs.get(("app", attr))
                    if definition is None:
                        continue
                    attrs["app"].setdefault(attr, AttrDef())
                    merged = attrs["app"][attr]
                    merged.formats |= definition.formats
                    merged.enums.update(definition.enums)
                    merged.flags.update(definition.flags)
                    entry[attr] = 0
            styleables[key] = dict(sorted(entry.items()))
            if key not in classes.setdefault(owner + kind, []):
                classes[owner + kind].append(key)

    return {
        "schema": SCHEMA,
        "generator": ".python/generate_android_attrs.py",
        "sources": {"sdk": [directory for _, directory in SDK_LEVELS], "aars": sources},
        "attrs": {ns: {name: definition.to_json() for name, definition in sorted(defs.items())} for ns, defs in attrs.items()},
        "styleables": dict(sorted(styleables.items())),
        "classes": {name: sorted(keys) for name, keys in sorted(classes.items())},
    }


LAYOUT_SUFFIXES = ("_MarginLayout", "_Layout")


def owner_of(styleable: str, by_simple: dict[str, str]) -> tuple[str | None, str]:
    """The class a styleable belongs to and `""` (the view's own attributes) or `#layout` (its layout params)."""
    for suffix in LAYOUT_SUFFIXES:
        if styleable.endswith(suffix):
            owner = by_simple.get(styleable[: -len(suffix)])
            return (owner, "#layout") if owner else (None, "")
    owner = by_simple.get(styleable)
    return (owner, "") if owner else (None, "")


def render(table: dict) -> str:
    return json.dumps(table, ensure_ascii=True, separators=(",", ":"), sort_keys=False) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"), help="Android SDK root")
    parser.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle"))
    parser.add_argument("--check", action="store_true", help="compare with the committed table instead of writing it")
    args = parser.parse_args()
    if args.sdk is None:
        parser.error("--sdk is required (or set ANDROID_HOME)")
    text = render(generate(Path(args.sdk), Path(args.gradle_home)))
    if args.check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.is_file() else ""
        if current != text:
            print(f"ANDROID_ATTRS_DRIFT {OUTPUT.relative_to(ROOT)} differs from the sources", file=sys.stderr)
            return 1
        print(f"ANDROID_ATTRS_OK bytes={len(text.encode())}")
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(text, encoding="utf-8", newline="\n")
    table = json.loads(text)
    print(
        f"ANDROID_ATTRS_WRITTEN bytes={len(text.encode())} android={len(table['attrs']['android'])} app={len(table['attrs']['app'])} "
        f"styleables={len(table['styleables'])} classes={len(table['classes'])}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
