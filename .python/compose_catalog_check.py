"""Generate/check the P6 declaration and documentation projections of the frozen Compose catalog.

1. Export actual JVM objects (the exporter lives in test sources, outside the contract AAR):
   gradlew :plugin-api:compose-ui-api:exportComposeUiCatalog
2. Generate reviewable fragments, without changing external repositories:
   py .python/compose_catalog_check.py generate --output-dir build/compose-catalog
3. During P6, paste each complete marked fragment into aj6-int-compose.d.ts / api/composeComponents.md.
   Declare the named Compose*Input helper types in Internal alongside the generated interfaces,
   and use ComposeCatalogProps in the public element factory signatures. Handwritten API methods,
   input conversion helper definitions and cross-property rules remain outside this inventory gate.
4. Check the delivered files against a fresh export:
   py .python/compose_catalog_check.py check --dts <aj6-int-compose.d.ts> --docs <api/composeComponents.md>

Generate is not a successful consistency check. Check fails for missing files or markers, including
before P6 has been delivered. No Kotlin, TypeScript or Markdown source is parsed with heuristics:
only the stable generated regions are compared, including real TS property declarations/types.
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import html
import json
import math
from pathlib import Path
import sys
from typing import Any


SOURCE = "org.autojs.plugin.compose.api.catalog.ComponentCatalog.V1"
DEFAULT_CATALOG = Path(__file__).resolve().parents[1] / "plugin-api/compose-ui-api/build/reports/compose-ui/component-catalog-v1.json"
DTS_START = "// <autojs6-compose-catalog:v1>"
DTS_END = "// </autojs6-compose-catalog:v1>"
DOC_START = "<!-- autojs6-compose-catalog:v1:start -->"
DOC_END = "<!-- autojs6-compose-catalog:v1:end -->"
KINDS = {"STRING", "NUMBER", "BOOLEAN", "COLOR", "DP", "SP", "ENUM", "TEXT_STYLE", "BITMAP", "ICON_NAME", "SHAPE", "LIST", "NULL"}
TYPE_NAMES = {
    "STRING": "string", "NUMBER": "number", "BOOLEAN": "boolean", "COLOR": "ComposeColorInput",
    "DP": "ComposeDpInput", "SP": "ComposeSpInput", "TEXT_STYLE": "ComposeTextStyleInput",
    "BITMAP": "ComposeImageInput", "ICON_NAME": "ComposeIconNameInput", "SHAPE": "ComposeShapeInput", "NULL": "null",
}


class CatalogError(ValueError):
    """A missing, malformed or inconsistent deliverable; never a skipped success."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise CatalogError(message)


def object_keys(value: Any, names: set[str], label: str) -> None:
    require(isinstance(value, dict), f"{label}: expected an object")
    require(set(value) == names, f"{label}: unexpected/missing fields {sorted(set(value) ^ names)}")


def names(value: Any, label: str, *, nonempty: bool = False) -> list[str]:
    require(isinstance(value, list) and all(isinstance(item, str) and bool(item) for item in value), f"{label}: expected string names")
    require(len(value) == len(set(value)), f"{label}: duplicate name")
    require(not nonempty or bool(value), f"{label}: cannot be empty")
    return value


def validate_type(value: Any, label: str) -> None:
    object_keys(value, {"kinds", "enumValues", "minimum", "maximum", "minimumExclusive", "integral", "listKinds", "listMinimum", "listMaximum"}, label)
    kinds = names(value["kinds"], f"{label}.kinds", nonempty=True)
    elements = names(value["listKinds"], f"{label}.listKinds")
    require(set(kinds + elements) <= KINDS, f"{label}: unsupported value kind")
    enum_values = names(value["enumValues"], f"{label}.enumValues")
    require(not enum_values or "ENUM" in kinds, f"{label}: enum literals without ENUM kind")
    for key in ["minimum", "maximum"]:
        number = value[key]
        require(number is None or type(number) in (int, float) and math.isfinite(number), f"{label}.{key}: expected a finite number or null")
    require(value["minimum"] is None or value["maximum"] is None or value["minimum"] <= value["maximum"], f"{label}: inverted numeric bounds")
    require(type(value["minimumExclusive"]) is bool and type(value["integral"]) is bool, f"{label}: invalid numeric constraint flag")
    require(type(value["listMinimum"]) is int and type(value["listMaximum"]) is int and 0 <= value["listMinimum"] <= value["listMaximum"], f"{label}: invalid list bounds")


def validate_default(value: Any, label: str) -> None:
    if value is None:
        return
    object_keys(value, {"kind", "value"}, label)
    kind, item = value["kind"], value["value"]
    require(kind in KINDS and kind != "BITMAP", f"{label}: unsupported default kind")
    if kind == "LIST":
        require(isinstance(item, list), f"{label}: expected a list default")
        for index, element in enumerate(item):
            require(element is not None, f"{label}[{index}]: unspecified list element")
            validate_default(element, f"{label}[{index}]")
    elif kind == "NULL":
        require(item is None, f"{label}: NULL default must be null")
    elif kind == "BOOLEAN":
        require(type(item) is bool, f"{label}: expected a boolean default")
    elif kind in {"NUMBER", "DP", "SP", "COLOR"}:
        require(type(item) in (int, float) and math.isfinite(item), f"{label}: expected a finite numeric default")
    elif kind in {"STRING", "ENUM", "ICON_NAME"}:
        require(isinstance(item, str), f"{label}: expected a string default")
    else:
        require(isinstance(item, dict), f"{label}: expected a structured default")


def validate_catalog(catalog: Any) -> dict[str, Any]:
    object_keys(catalog, {"schemaVersion", "contractVersion", "source", "componentCount", "propertyCount", "valueKinds", "components"}, "catalog")
    require(type(catalog["schemaVersion"]) is int and type(catalog["contractVersion"]) is int and
            catalog["schemaVersion"] == 1 and catalog["contractVersion"] == 1,
            "Unsupported catalog schema/contract version; update this tool explicitly")
    require(catalog["source"] == SOURCE, "Catalog must be exported from the frozen ComponentCatalog.V1")
    require(set(names(catalog["valueKinds"], "valueKinds")) == KINDS, "Unexpected frozen ValueKind inventory")
    require(isinstance(catalog["components"], list) and bool(catalog["components"]), "Catalog components are missing")
    component_names: list[str] = []
    property_count = 0
    for component in catalog["components"]:
        object_keys(component, {"name", "invocation", "children", "scope", "commands", "properties", "slots", "events"}, "component")
        name = component["name"]
        require(isinstance(name, str) and bool(name), "Invalid component name")
        component_names.append(name)
        require(component["invocation"] in {"NODE", "COMMAND"}, f"{name}: invalid invocation")
        require(component["children"] in {"NONE", "SINGLE", "MANY", "LAZY_ITEMS"}, f"{name}: invalid children policy")
        require(component["scope"] in {"ANY", "COLUMN_ROW", "BOX"}, f"{name}: invalid scope")
        names(component["commands"], f"{name}.commands")
        require(isinstance(component["properties"], list), f"{name}: properties are missing")
        property_names: list[str] = []
        aliases: list[str] = []
        for prop in component["properties"]:
            object_keys(prop, {"name", "type", "required", "default", "aliases", "update"}, f"{name}.property")
            label = f"{name}.{prop['name']}"
            require(isinstance(prop["name"], str) and bool(prop["name"]), f"{label}: invalid property name")
            property_names.append(prop["name"])
            aliases.extend(names(prop["aliases"], f"{label}.aliases"))
            require(type(prop["required"]) is bool, f"{label}: required must be a boolean")
            require(not prop["required"] or not prop["aliases"], f"{label}: required aliases need an explicit declaration-union implementation")
            require(prop["update"] in {"SET_PROPS", "EDIT_COMMAND"}, f"{label}: invalid update mode")
            validate_type(prop["type"], f"{label}.type")
            validate_default(prop["default"], f"{label}.default")
            if prop["default"] is not None:
                require(prop["default"]["kind"] in prop["type"]["kinds"], f"{label}: default kind does not match property type")
        require(len(property_names + aliases) == len(set(property_names + aliases)), f"{name}: duplicate property/alias")
        property_count += len(property_names)
        require(isinstance(component["slots"], list), f"{name}: slots are missing")
        slot_names: list[str] = []
        for slot in component["slots"]:
            object_keys(slot, {"name", "required"}, f"{name}.slot")
            require(type(slot["required"]) is bool, f"{name}: invalid required slot")
            slot_names.append(slot["name"])
        names(slot_names, f"{name}.slots")
        require(isinstance(component["events"], list), f"{name}: events are missing")
        event_names: list[str] = []
        for event in component["events"]:
            object_keys(event, {"name", "payload"}, f"{name}.event")
            event_names.append(event["name"])
            require(isinstance(event["payload"], list), f"{name}: invalid event payload")
            field_names: list[str] = []
            for field in event["payload"]:
                object_keys(field, {"name", "type", "required"}, f"{name}.{event['name']}.field")
                require(field["type"] in {"STRING", "BOOLEAN", "INTEGER", "LONG", "NUMBER"} and type(field["required"]) is bool, f"{name}: invalid payload field")
                field_names.append(field["name"])
            names(field_names, f"{name}.{event['name']}.payload")
        names(event_names, f"{name}.events")
    names(component_names, "components")
    require(type(catalog["componentCount"]) is int and catalog["componentCount"] == len(component_names), "Export component count does not match its inventory")
    require(type(catalog["propertyCount"]) is int and catalog["propertyCount"] == property_count, "Export property count does not match its inventory")
    return catalog


def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"), allow_nan=False)


def digest(catalog: dict[str, Any]) -> str:
    return hashlib.sha256(canonical(catalog).encode("utf-8")).hexdigest()


def ts_type(value: dict[str, Any]) -> str:
    def kind_type(kind: str) -> str:
        if kind == "ENUM":
            return " | ".join(json.dumps(name, ensure_ascii=True) for name in sorted(value["enumValues"])) or "string"
        if kind == "LIST":
            elements = [TYPE_NAMES.get(item, "string" if item == "ENUM" else "ReadonlyArray<ComposeValueInput>") for item in sorted(value["listKinds"])]
            item = " | ".join(elements) or "ComposeValueInput"
            if value["listMinimum"] == value["listMaximum"] and value["listMaximum"] <= 16:
                return "readonly [" + ", ".join([item] * value["listMaximum"]) + "]"
            return f"ReadonlyArray<{item}>"
        return TYPE_NAMES[kind]
    return " | ".join(kind_type(kind) for kind in sorted(value["kinds"]))


def constraints(value: dict[str, Any]) -> dict[str, Any]:
    return {name: value[name] for name in ["minimum", "maximum", "minimumExclusive", "integral", "listKinds", "listMinimum", "listMaximum"]
            if value[name] is not None and (name.startswith("list") and "LIST" in value["kinds"] or not name.startswith("list") and value[name] is not False)}


def render_dts(catalog: dict[str, Any]) -> str:
    lines = [DTS_START, f"// Generated from {SOURCE}; schema=1; contract=1; sha256={digest(catalog)}.",
             "// Input helper types are defined by the surrounding P6 declaration file.",
             "declare namespace Internal {", "    interface ComposeCatalogProps {"]
    for component in sorted(catalog["components"], key=lambda item: item["name"]):
        lines += [f"        /** @invocation {component['invocation']} @children {component['children']} @scope {component['scope']} */",
                  f"        {json.dumps(component['name'])}: {{"]
        for prop in sorted(component["properties"], key=lambda item: item["name"]):
            metadata = canonical({"default": prop["default"], "update": prop["update"], "constraints": constraints(prop["type"])}).replace("*/", "*\\/")
            lines += [f"            /** @catalog {metadata} */", f"            {json.dumps(prop['name'])}{'' if prop['required'] else '?'}: {ts_type(prop['type'])};"]
            for alias in sorted(prop["aliases"]):
                lines += [f"            /** @aliasFor {prop['name']} */", f"            {json.dumps(alias)}?: {ts_type(prop['type'])};"]
        lines += ["        };"]
    lines += ["    }"]
    for invocation, label in [("NODE", "ComposeCatalogNodeName"), ("COMMAND", "ComposeCatalogCommandName")]:
        items = sorted(component["name"] for component in catalog["components"] if component["invocation"] == invocation)
        lines.append(f"    type {label} = " + (" | ".join(json.dumps(item) for item in items) or "never") + ";")
    return "\n".join(lines + ["}", DTS_END, ""])


def code(value: Any) -> str:
    rendered = value if isinstance(value, str) else canonical(value)
    return "<code>" + html.escape(rendered).replace("|", "&#124;") + "</code>"


def render_docs(catalog: dict[str, Any]) -> str:
    lines = [DOC_START, f"<!-- Generated from {SOURCE}; schema=1; contract=1; sha256={digest(catalog)}. -->", "",
             "| Component | Invocation | Children | Scope | Canonical properties | Slots | Events | Commands |",
             "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for component in sorted(catalog["components"], key=lambda item: item["name"]):
        rows = [code(component["name"]), component["invocation"], component["children"], component["scope"],
                ", ".join(code(prop["name"]) for prop in sorted(component["properties"], key=lambda item: item["name"])) or "-",
                ", ".join(code(slot["name"] + (" (required)" if slot["required"] else "")) for slot in component["slots"]) or "-",
                ", ".join(code(event["name"]) for event in component["events"]) or "-",
                ", ".join(code(command) for command in component["commands"]) or "-"]
        lines.append("| " + " | ".join(rows) + " |")
    lines += ["", "| Component | Property | TypeScript input type | Required | Default (wire value) | Aliases | Update | Constraints |",
              "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    for component in sorted(catalog["components"], key=lambda item: item["name"]):
        for prop in sorted(component["properties"], key=lambda item: item["name"]):
            rows = [code(component["name"]), code(prop["name"]), code(ts_type(prop["type"])), str(prop["required"]).lower(),
                    "unspecified" if prop["default"] is None else code(prop["default"]),
                    ", ".join(code(alias) for alias in sorted(prop["aliases"])) or "-", prop["update"], code(constraints(prop["type"]))]
            lines.append("| " + " | ".join(rows) + " |")
    return "\n".join(lines + ["", DOC_END, ""])


def read_text(path: Path, label: str) -> str:
    try:
        return path.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeError) as error:
        raise CatalogError(f"Missing/unreadable {label}: {path}. This is not a successful consistency check.") from error


def load_catalog(path: Path) -> dict[str, Any]:
    try:
        return validate_catalog(json.loads(read_text(path, "catalog export")))
    except (json.JSONDecodeError, RecursionError) as error:
        raise CatalogError(f"Invalid catalog JSON: {path}: {error}") from error


def marked_region(content: str, start: str, end: str, label: str) -> str:
    lines = content.splitlines()
    starts = [index for index, line in enumerate(lines) if line.strip() == start]
    ends = [index for index, line in enumerate(lines) if line.strip() == end]
    require(len(starts) == 1 and len(ends) == 1, f"{label}: expected exactly one catalog start/end marker; generate and integrate the P6 fragment")
    require(starts[0] < ends[0], f"{label}: catalog markers are reversed")
    return "\n".join(lines[starts[0]:ends[0] + 1]) + "\n"


def check_artifact(path: Path, expected: str, start: str, end: str, label: str) -> None:
    actual = marked_region(read_text(path, label), start, end, label)
    if actual != expected:
        difference = list(difflib.unified_diff(actual.splitlines(), expected.splitlines(), fromfile=str(path), tofile="generated catalog projection", lineterm=""))
        raise CatalogError(f"{label} differs from the frozen catalog:\n" + "\n".join(difference[:60]))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    generate = commands.add_parser("generate", help="Write fragments only; never claim declaration/docs consistency")
    generate.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    generate.add_argument("--output-dir", type=Path, required=True)
    check = commands.add_parser("check", help="Require and compare both delivered P6 marked regions")
    check.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    check.add_argument("--dts", type=Path, required=True)
    check.add_argument("--docs", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        catalog = load_catalog(args.catalog)
        dts, docs = render_dts(catalog), render_docs(catalog)
        if args.command == "generate":
            args.output_dir.mkdir(parents=True, exist_ok=True)
            (args.output_dir / "compose-catalog.fragment.d.ts").write_text(dts, encoding="utf-8", newline="\n")
            (args.output_dir / "compose-catalog.fragment.md").write_text(docs, encoding="utf-8", newline="\n")
            print(f"GENERATED {catalog['componentCount']} components / {catalog['propertyCount']} canonical properties in {args.output_dir}; external files have NOT been checked.")
        else:
            check_artifact(args.dts, dts, DTS_START, DTS_END, "TypeScript declarations")
            check_artifact(args.docs, docs, DOC_START, DOC_END, "component documentation")
            print(f"OK: {catalog['componentCount']} components / {catalog['propertyCount']} canonical properties match both supplied catalog regions.")
        return 0
    except (CatalogError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
