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


def merge_missing_formats(values: ValuesFile, other: ValuesFile) -> None:
    """Definitions [values] lacks, or has without a format, taken from [other]: attrs.xml only references `logo` and
    `theme` in styleables because attrs_manifest.xml declares them."""
    for key, definition in other.attrs.items():
        target = values.attrs.setdefault(key, AttrDef())
        if not target.formats:
            target.formats |= definition.formats
            target.enums.update(definition.enums)
            target.flags.update(definition.flags)


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


def super_name(data: bytes) -> tuple[str | None, bool]:
    """The superclass of a class file (dotted) and whether the class is public; None for java.lang.Object."""
    if data[:4] != bytes.fromhex("cafebabe"):
        raise SystemExit("not a class file")
    count = int.from_bytes(data[8:10], "big")
    offset = 10
    utf8: dict[int, str] = {}
    classes: dict[int, int] = {}
    index = 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = int.from_bytes(data[offset:offset + 2], "big")
            utf8[index] = data[offset + 2:offset + 2 + length].decode("utf-8", "replace")
            offset += 2 + length
        elif tag == 7:
            classes[index] = int.from_bytes(data[offset:offset + 2], "big")
            offset += 2
        elif tag in sizes:
            offset += sizes[tag]
        else:
            raise SystemExit(f"unknown constant pool tag {tag}")
        index += 2 if tag in (5, 6) else 1
    access = int.from_bytes(data[offset:offset + 2], "big")
    super_index = int.from_bytes(data[offset + 4:offset + 6], "big")
    name = utf8[classes[super_index]].replace("/", ".") if super_index else None
    return name, bool(access & 0x0001)


def read_supers(jar: zipfile.ZipFile, supers: dict[str, str | None], public: set[str]) -> None:
    for entry in jar.namelist():
        if entry.endswith(".class") and not entry.startswith("META-INF/"):
            name = entry[: -len(".class")].replace("/", ".")
            parent, is_public = super_name(jar.read(entry))
            supers[name] = parent
            if is_public:
                public.add(name)


def extends(name: str, ancestor: str, supers: dict[str, str | None]) -> bool:
    current: str | None = name
    while current is not None:
        if current == ancestor:
            return True
        current = supers.get(current)
    return False


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
    manifest = sdk / "platforms" / SDK_LEVELS[-1][1] / "data/res/values/attrs_manifest.xml"
    if manifest.is_file():
        merge_missing_formats(newest, ValuesFile(manifest.read_bytes(), "android"))
    # Only public View subclasses own attributes, only ViewGroup subclasses own layout params.
    supers: dict[str, str | None] = {}
    public_classes: set[str] = set()
    with zipfile.ZipFile(sdk / "platforms" / SDK_LEVELS[-1][1] / "android.jar") as jar:
        android_classes = class_names(jar, ANDROID_PACKAGES)
        read_supers(jar, supers, public_classes)
    aar_data = []
    for group, artifact, version in AARS:
        aar = find_aar(gradle_home, group, artifact, version)
        with zipfile.ZipFile(aar) as archive:
            values_bytes = archive.read("res/values/values.xml") if "res/values/values.xml" in archive.namelist() else None
            with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as jar:
                library_classes = class_names(jar)
                read_supers(jar, supers, public_classes)
        aar_data.append((f"{group}:{artifact}:{version}", values_bytes, library_classes))

    def is_owner(name: str, kind: str) -> bool:
        return name in public_classes and extends(name, "android.view.ViewGroup" if kind else "android.view.View", supers)

    by_simple = {name.rsplit(".", 1)[1]: name for name in android_classes}
    for styleable, members in sorted(since.items()):
        owner, kind = owner_of(styleable, by_simple)
        if owner is None or not is_owner(owner, kind):
            continue
        key = f"android:{styleable}"
        styleables[key] = dict(sorted(members.items()))
        classes.setdefault(owner + kind, []).append(key)
        for attr in members:
            attrs["android"][attr] = newest.attrs.get(("android", attr)) or AttrDef()

    # app: each library's own styleables for its own classes; their android: members reuse the SDK definitions.
    sources = []
    for source, values_bytes, library_classes in aar_data:
        sources.append(source)
        if values_bytes is None:
            continue
        values = ValuesFile(values_bytes, "app")
        simple = {name.rsplit(".", 1)[1]: name for name in library_classes}
        for styleable, members in sorted(values.styleables.items()):
            owner, kind = owner_of(styleable, simple)
            if owner is None or not is_owner(owner, kind):
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

    split_constraint_layout(styleables, classes)
    return {
        "schema": SCHEMA,
        "generator": ".python/generate_android_attrs.py",
        "sources": {"sdk": [directory for _, directory in SDK_LEVELS], "aars": sources},
        "attrs": {ns: {name: definition.to_json() for name, definition in sorted(defs.items())} for ns, defs in attrs.items()},
        "styleables": dict(sorted(styleables.items())),
        "classes": {name: sorted(keys) for name, keys in sorted(classes.items())},
    }


CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
CONSTRAINT_HELPER = "androidx.constraintlayout.widget.ConstraintHelper"
# Attributes of the ConstraintLayout element itself and of its helpers (Barrier, Group, Flow, CircularFlow, Layer),
# which the library declares in ConstraintLayout_Layout together with the children's layout params (roadmap A9).
CONSTRAINT_CONTAINER_ATTRS = {
    "android:maxWidth", "android:maxHeight", "android:minWidth", "android:minHeight",
    "constraintSet", "layoutDescription", "layout_optimizationLevel",
}
CONSTRAINT_HELPER_ATTRS = {"android:orientation"}
CONSTRAINT_HELPER_PREFIXES = ("barrier", "constraint_referenced_", "flow_", "circularflow_")
CONSTRAINT_HELPER_CLASSES = (
    "androidx.constraintlayout.widget.Barrier",
    "androidx.constraintlayout.widget.Group",
    "androidx.constraintlayout.helper.widget.Flow",
    "androidx.constraintlayout.helper.widget.Layer",
    "androidx.constraintlayout.helper.widget.CircularFlow",
)


def split_constraint_layout(styleables: dict[str, dict[str, int]], classes: dict[str, list[str]]) -> None:
    """Moves the container and helper attributes out of `app:ConstraintLayout_Layout` into `app:ConstraintLayout_Container`
    (owned by ConstraintLayout) and `app:ConstraintHelper` (owned by ConstraintHelper, so Barrier / Group / Flow take
    them); the children keep the `layout_*` attributes (and guidelineUseRtl / chainUseRtl, and android:orientation, the
    orientation of a Guideline's params), not the View attributes (padding, elevation, visibility) the library also
    lists there."""
    members = styleables.get("app:ConstraintLayout_Layout")
    if not members:
        return
    container = {k: v for k, v in members.items() if k in CONSTRAINT_CONTAINER_ATTRS}
    helper = {k: v for k, v in members.items() if k in CONSTRAINT_HELPER_ATTRS or k.startswith(CONSTRAINT_HELPER_PREFIXES)}
    child = {
        k: v for k, v in members.items()
        if k not in container and (k not in helper or k == "android:orientation")
        and (k.removeprefix("android:").startswith("layout_") or not k.startswith("android:") or k == "android:orientation")
    }
    styleables["app:ConstraintLayout_Layout"] = child
    styleables["app:ConstraintLayout_Container"] = container
    styleables["app:ConstraintHelper"] = helper
    for owner, key in ((CONSTRAINT_LAYOUT, "app:ConstraintLayout_Container"), (CONSTRAINT_HELPER, "app:ConstraintHelper")):
        keys = classes.setdefault(owner, [])
        if key not in keys:
            keys.append(key)
    # The concrete helpers have no styleable of their own; listing them lets tools find the classes that take these.
    for helper_class in CONSTRAINT_HELPER_CLASSES:
        classes.setdefault(helper_class, [])


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


# --- Documentation page (roadmap A6) --------------------------------------------------------------------------------

COVERAGE = ROOT / "app" / "src" / "androidTest" / "assets" / "ui" / "android-attrs-coverage.json"
DOCS_PAGE = Path("api") / "uiAndroidAttributes.md"
DOCS_BEGIN = "<!-- android-attrs:begin (generated by AutoJs6 .python/generate_android_attrs.py --docs; edit the generator, not this block) -->"
DOCS_END = "<!-- android-attrs:end -->"
MIN_SDK = 24
DOCS_CATEGORIES = {
    "handler": "处理器",
    "setter": "公开方法",
    "params": "布局参数",
    "design": "设计期 (忽略)",
    "unsupported": "不支持",
    "api": "不支持",
}


def docs_format(definition: dict) -> str:
    """`dimension`, `enum: a, b`, `reference / color`; flags and enums list their names."""
    formats = [f for f in definition.get("f", []) if f not in ("enum", "flag")]
    parts = [" / ".join(formats)] if formats else []
    if definition.get("e"):
        parts.append("enum: " + ", ".join(definition["e"]))
    if definition.get("g"):
        parts.append("flag: " + ", ".join(definition["g"]))
    return "; ".join(parts) or "-"


def docs_since(table: dict, class_key: str) -> dict[str, int]:
    """The API level each `namespace:name` of [class_key]'s own styleables appeared at."""
    since: dict[str, int] = {}
    for key in table["classes"].get(class_key, []):
        namespace = key.split(":", 1)[0]
        for name, level in table["styleables"].get(key, {}).items():
            qualified = name if name.startswith("android:") else f"{namespace}:{name}"
            since.setdefault(qualified, level)
    return since


def docs_block(table: dict, coverage: dict) -> str:
    """The generated part of api/uiAndroidAttributes.md: coverage totals, then a table per view class and per container."""
    totals = coverage["totals"]

    def count(section: str, *names: str) -> int:
        return sum(totals.get(section, {}).get(name, 0) for name in names)

    views, layouts = coverage["views"], coverage["layouts"]
    lines = [
        DOCS_BEGIN,
        "",
        f"以下内容由 AutoJs6 的覆盖矩阵 (设备测试 `AndroidAttributeCoverageDeviceTest`, Android API {coverage['api']}) 与属性表生成.",
        "",
        f"- View 属性: {sum(totals['views'].values())} 个 ({len(views)} 个类), 处理器 {count('views', 'handler')}, 公开方法 {count('views', 'setter')}, "
        f"不支持 {count('views', 'unsupported', 'api')}.",
        f"- 子视图布局参数: {sum(totals['layouts'].values())} 个 ({len(layouts)} 个容器), 处理器 {count('layouts', 'handler')}, 布局参数 {count('layouts', 'params')}, "
        f"设计期 {count('layouts', 'design')}, 不支持 {count('layouts', 'unsupported', 'api')}.",
        "",
    ]
    def title(class_name: str, used: dict[str, int]) -> str:
        package, simple = class_name.rsplit(".", 1)
        return simple if used.get(simple, 0) <= 1 else f"{simple} ({package})"

    def section(heading: str, entries: dict, layout: bool) -> None:
        lines.extend([f"## {heading}", ""])
        used: dict[str, int] = {}
        for class_name in entries:
            simple = class_name.rsplit(".", 1)[1]
            used[simple] = used.get(simple, 0) + 1
        for class_name in sorted(entries, key=lambda c: (c.rsplit(".", 1)[1].lower(), c)):
            since = docs_since(table, class_name + ("#layout" if layout else ""))
            rows = []
            for category, keys in entries[class_name].items():
                for key in keys:
                    namespace, name = key.split(":", 1)
                    definition = table["attrs"].get(namespace, {}).get(name, {})
                    level = since.get(key, 0)
                    rows.append((key, docs_format(definition), str(level) if level > MIN_SDK else "", DOCS_CATEGORIES[category]))
            lines.extend([
                f"### {title(class_name, used)}" + (" 子视图" if layout else ""),
                "",
                f"`{class_name}`",
                "",
                "| 属性 | 格式与取值 | API | 设置方式 |",
                "| --- | --- | --- | --- |",
            ])
            lines.extend(f"| `{key}` | {fmt} | {level} | {how} |" for key, fmt, level, how in sorted(rows))
            lines.append("")

    section("View 属性", views, layout=False)
    section("子视图布局参数", layouts, layout=True)
    lines.append(DOCS_END)
    return "\n".join(lines)


def docs_page(current: str, block: str) -> str:
    """[current] with its generated block replaced by [block]."""
    start = current.find(DOCS_BEGIN)
    end = current.find(DOCS_END)
    if start < 0 or end < start:
        raise SystemExit(f"{DOCS_PAGE} has no generated block ({DOCS_BEGIN} ... {DOCS_END})")
    return current[:start] + block + current[end + len(DOCS_END):]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"), help="Android SDK root")
    parser.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle"))
    parser.add_argument("--check", action="store_true", help="compare with the committed table (or, with --docs, page) instead of writing it")
    parser.add_argument("--docs", type=Path, help="AutoJs6-Documentation root: update the generated block of api/uiAndroidAttributes.md instead")
    args = parser.parse_args()
    if args.docs is not None:
        page = Path(args.docs) / DOCS_PAGE
        current = page.read_text(encoding="utf-8")
        table = json.loads(OUTPUT.read_text(encoding="utf-8"))
        coverage = json.loads(COVERAGE.read_text(encoding="utf-8"))
        text = docs_page(current, docs_block(table, coverage))
        if args.check:
            if current != text:
                print(f"ANDROID_ATTRS_DOCS_DRIFT {page} differs from the table and the coverage snapshot", file=sys.stderr)
                return 1
            print(f"ANDROID_ATTRS_DOCS_OK bytes={len(text.encode())}")
            return 0
        page.write_text(text, encoding="utf-8", newline="\n")
        print(f"ANDROID_ATTRS_DOCS_WRITTEN bytes={len(text.encode())}")
        return 0
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
