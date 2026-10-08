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
   py .python/compose_catalog_check.py check --dts <aj6-int-compose.d.ts> --docs <api/composeComponents.md> --jsx <aj6-jsx-element-extension.d.ts>

The separate JSX region maps only NODE entries to compose:Name intrinsic tags. It does not redefine
legacy XML intrinsic attributes, JSX.Element, or the ordinary Compose factory signatures.
F2 is a separate extension: exportComposeUiInteropCatalog writes component-catalog-interop-v1.json.
Pass --interop-catalog <that export> to generate/check its additional marked regions. The V1 outputs
remain unchanged; an interop check also requires --jsx and never accepts wire lease IDs as script Views.
F4 is append-only contract V2: --v2-catalog verifies the complete exported V2 union against V1,
then generates/checks separate V2-only regions. V1 components and their generated text stay frozen.

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
JSX_START = "// <autojs6-compose-jsx:v1>"
JSX_END = "// </autojs6-compose-jsx:v1>"
INTEROP_SOURCE = "org.autojs.plugin.compose.api.interop.AndroidViewInteropV1"
INTEROP_FEATURE = "android-view-interop-v1"
INTEROP_DTS_START = "// <autojs6-compose-interop:f2>"
INTEROP_DTS_END = "// </autojs6-compose-interop:f2>"
INTEROP_DOC_START = "<!-- autojs6-compose-interop:f2:start -->"
INTEROP_DOC_END = "<!-- autojs6-compose-interop:f2:end -->"
INTEROP_JSX_START = "// <autojs6-compose-interop-jsx:f2>"
INTEROP_JSX_END = "// </autojs6-compose-interop-jsx:f2>"
VIEW_PROJECTION = {"input": "View | ((context: android.content.Context) => View)", "output": "View", "wire": "leaseId"}
V2_SOURCE = "org.autojs.plugin.compose.api.v2.ComposeUiV2.catalog"
V2_DTS_START = "// <autojs6-compose-catalog:v2>"
V2_DTS_END = "// </autojs6-compose-catalog:v2>"
V2_DOC_START = "<!-- autojs6-compose-catalog:v2:start -->"
V2_DOC_END = "<!-- autojs6-compose-catalog:v2:end -->"
V2_JSX_START = "// <autojs6-compose-jsx:v2>"
V2_JSX_END = "// </autojs6-compose-jsx:v2>"
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


def validate_catalog(catalog: Any, *, expected_source: str = SOURCE, expected_contract: int = 1) -> dict[str, Any]:
    object_keys(catalog, {"schemaVersion", "contractVersion", "source", "componentCount", "propertyCount", "valueKinds", "components"}, "catalog")
    require(type(catalog["schemaVersion"]) is int and type(catalog["contractVersion"]) is int and
            catalog["schemaVersion"] == 1 and catalog["contractVersion"] == expected_contract,
            "Unsupported catalog schema/contract version; update this tool explicitly")
    require(catalog["source"] == expected_source, f"Catalog must be exported from {expected_source}")
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


def validate_v2_catalog(v2: Any, catalog: dict[str, Any]) -> dict[str, Any]:
    validate_catalog(v2, expected_source=V2_SOURCE, expected_contract=2)
    by_name = {component["name"]: component for component in v2["components"]}
    for frozen in catalog["components"]:
        require(by_name.get(frozen["name"]) == frozen,
                f"V2 must retain the frozen V1 component unchanged: {frozen['name']}")
    require(len(by_name) > catalog["componentCount"], "V2 must append components to V1")
    require("AndroidView" not in by_name, "AndroidView remains a separately negotiated F2 extension")
    return v2


def v2_delta(v2: dict[str, Any], catalog: dict[str, Any]) -> dict[str, Any]:
    frozen_names = {component["name"] for component in catalog["components"]}
    components = [component for component in v2["components"] if component["name"] not in frozen_names]
    return {**v2, "components": components, "componentCount": len(components),
            "propertyCount": sum(len(component["properties"]) for component in components)}


def validate_interop_catalog(interop: Any, catalog: dict[str, Any]) -> dict[str, Any]:
    extension_keys = {"extensionVersion", "feature", "scriptProjection"}
    catalog_keys = {"schemaVersion", "contractVersion", "source", "componentCount", "propertyCount", "valueKinds", "components"}
    object_keys(interop, catalog_keys | extension_keys, "interop catalog")
    validate_catalog({key: interop[key] for key in catalog_keys}, expected_source=INTEROP_SOURCE)
    require(type(interop["extensionVersion"]) is int and interop["extensionVersion"] == 1, "Unsupported interop extension version")
    require(interop["feature"] == INTEROP_FEATURE, "Unexpected interop feature")
    names_in_base = {component["name"] for component in catalog["components"]}
    require(not names_in_base.intersection(component["name"] for component in interop["components"]), "Interop cannot replace frozen V1 components")
    require(interop["componentCount"] == 1 and interop["propertyCount"] == 1 and interop["components"][0]["name"] == "AndroidView",
            "F2 must contain exactly the AndroidView extension")
    component = interop["components"][0]
    require(component["invocation"] == "NODE" and component["children"] == "NONE" and component["scope"] == "ANY" and
            not component["slots"] and not component["commands"], "Unexpected AndroidView structure")
    box = next((item for item in catalog["components"] if item["name"] == "Box"), None)
    require(box is not None and component["events"] == box["events"], "AndroidView must use the existing common events")
    prop = component["properties"][0]
    expected_type = {"kinds": ["NUMBER"], "enumValues": [], "minimum": 1, "maximum": 2147483647,
                     "minimumExclusive": False, "integral": True, "listKinds": [], "listMinimum": 0, "listMaximum": 2000}
    require(prop == {"name": "view", "type": expected_type, "required": True, "default": None, "aliases": [], "update": "SET_PROPS"},
            "AndroidView wire view must be the bounded, required lease ID")
    require(interop["scriptProjection"] == {"AndroidView.view": VIEW_PROJECTION},
            "AndroidView script View/factory projection is missing or changed")
    return interop


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


def render_jsx(catalog: dict[str, Any]) -> str:
    lines = [JSX_START, f"// Generated from {SOURCE}; schema=1; contract=1; sha256={digest(catalog)}.",
             "// Ordinary factories provide value-tag typing; these names never replace legacy XML tags.",
             "declare namespace JSX {", "    interface IntrinsicElements {"]
    for component in sorted(catalog["components"], key=lambda item: item["name"]):
        if component["invocation"] == "NODE":
            name = component["name"]
            lines.append(f"        {json.dumps('compose:' + name)}: Internal.ComposeProps<{json.dumps(name)}>;")
    return "\n".join(lines + ["    }", "}", JSX_END, ""])


def v2_notice(v2: dict[str, Any]) -> str:
    return f"Generated from {V2_SOURCE}; schema=1; contract=2; sha256={digest(v2)}."


def v2_value_type(prop: dict[str, Any]) -> str:
    value = prop["type"]
    kinds = []
    normalized = {**TYPE_NAMES, "COLOR": "number", "DP": "number", "SP": "number",
                  "SHAPE": "ComposeShapeValue", "TEXT_STYLE": "ComposeTextStyleValue"}
    for kind in sorted(value["kinds"]):
        if kind == "ENUM":
            kinds += [json.dumps(item) for item in sorted(value["enumValues"])] or ["string"]
        elif kind == "LIST":
            elements = list(dict.fromkeys(normalized.get(item, "ComposeValueInput") for item in sorted(value["listKinds"])))
            kinds.append("Array<" + (" | ".join(elements) or "ComposeValueInput") + ">")
        else:
            kinds.append(normalized[kind])
    if prop["default"] is None and not prop["required"]:
        kinds.append("undefined")
    return " | ".join(dict.fromkeys(kinds))


def render_v2_dts(v2: dict[str, Any], catalog: dict[str, Any]) -> str:
    delta = v2_delta(v2, catalog)
    components = sorted(delta["components"], key=lambda item: item["name"])
    # Reuse the exact property projection; the V1 implementation remains unchanged.
    projected = render_dts(delta).splitlines()
    projected[0], projected[1], projected[-1] = V2_DTS_START, "// " + v2_notice(v2), V2_DTS_END
    projected = [line.replace("ComposeCatalogProps", "ComposeV2CatalogProps")
                 .replace("ComposeCatalogNodeName", "ComposeV2NodeName")
                 .replace("ComposeCatalogCommandName", "ComposeV2CommandName") for line in projected]
    lines = projected[:-2]
    for label, predicate in [
        ("ComposeV2ManyChildrenName", lambda item: item["children"] in {"MANY", "LAZY_ITEMS"}),
        ("ComposeV2SingleChildName", lambda item: item["children"] == "SINGLE"),
        ("ComposeV2RequiredPropsName", lambda item: any(prop["required"] for prop in item["properties"]) or any(slot["required"] for slot in item["slots"])),
    ]:
        values = [json.dumps(item["name"]) for item in components if predicate(item)]
        lines.append(f"    type {label} = " + (" | ".join(values) or "never") + ";")
    lines.append("    interface ComposeV2SlotNames {")
    for component in components:
        slots = [json.dumps(slot["name"]) for slot in component["slots"]]
        lines.append(f"        {json.dumps(component['name'])}: " + (" | ".join(slots) or "never") + ";")
    lines += ["    }", "    interface ComposeV2RequiredSlots {"]
    for component in components:
        slots = [json.dumps(slot["name"]) for slot in component["slots"] if slot["required"]]
        lines.append(f"        {json.dumps(component['name'])}: " + (" | ".join(slots) or "never") + ";")
    lines += ["    }", "    interface ComposeV2NodeEvents {"]
    common_events = {event["name"] for event in next((item for item in catalog["components"] if item["name"] == "Box"), {"events": []})["events"]}
    for component in components:
        name = json.dumps(component["name"])
        lines.append(f"        {name}: {{")
        for event in component["events"]:
            if event["name"] in common_events:
                continue
            args = []
            for field in event["payload"]:
                field_type = {"STRING": "string", "BOOLEAN": "boolean", "INTEGER": "number", "LONG": "number", "NUMBER": "number"}[field["type"]]
                property_spec = next((prop for prop in component["properties"] if prop["name"] == field["name"]), None)
                if field["type"] == "STRING" and property_spec is not None and property_spec["type"]["kinds"] == ["ENUM"]:
                    field_type = ts_type(property_spec["type"])
                if not field["required"]:
                    field_type += " | null"
                args.append(f"{field['name']}: {field_type}")
            signature = ", ".join([f"this: ComposeNode<{name}>"] + args)
            lines.append(f"            {json.dumps(event['name'])}: ({signature}) => void;")
        lines.append("        };")
    lines += ["    }", "    interface ComposeNodeProperties {"]
    for component in components:
        lines.append(f"        {json.dumps(component['name'])}: {{")
        for prop in sorted(component["properties"], key=lambda item: item["name"]):
            for name in [prop["name"]] + sorted(prop["aliases"]):
                input_type = "ComposePaddingInput" if name == "contentPadding" else ts_type(prop["type"])
                lines += [f"            get {json.dumps(name)}(): {v2_value_type(prop)};",
                          f"            set {json.dumps(name)}(value: {input_type});"]
        lines.append("        };")
    lines += ["    }", "    interface ComposeNodePropertyValues {"]
    for component in components:
        lines.append(f"        {json.dumps(component['name'])}: {{")
        for prop in sorted(component["properties"], key=lambda item: item["name"]):
            for name in [prop["name"]] + sorted(prop["aliases"]):
                lines.append(f"            {json.dumps(name)}: {v2_value_type(prop)};")
        lines.append("        };")
    return "\n".join(lines + ["    }", "}", V2_DTS_END, ""])


def render_v2_jsx(v2: dict[str, Any], catalog: dict[str, Any]) -> str:
    projected = render_jsx(v2_delta(v2, catalog)).splitlines()
    projected[0], projected[1], projected[-1] = V2_JSX_START, "// " + v2_notice(v2), V2_JSX_END
    return "\n".join(projected) + "\n"


def render_v2_docs(v2: dict[str, Any], catalog: dict[str, Any]) -> str:
    projected = render_docs(v2_delta(v2, catalog)).splitlines()
    projected[0], projected[1], projected[-1] = V2_DOC_START, "<!-- " + v2_notice(v2) + " -->", V2_DOC_END
    # Event payloads are part of the V2 consistency gate as well as names and property types.
    lines = projected[:-1] + ["| Component | Event | Callback arguments |", "| --- | --- | --- |"]
    for component in sorted(v2_delta(v2, catalog)["components"], key=lambda item: item["name"]):
        for event in component["events"]:
            fields = ", ".join(f"{field['name']}: {field['type']}" + (" | null" if not field["required"] else "") for field in event["payload"]) or "none"
            lines.append("| " + " | ".join([code(component["name"]), code(event["name"]), code(fields)]) + " |")
    return "\n".join(lines + ["", V2_DOC_END, ""])


def interop_notice(interop: dict[str, Any]) -> str:
    return f"Generated from {INTEROP_SOURCE}; schema=1; contract=1; extension=1; feature={INTEROP_FEATURE}; sha256={digest(interop)}."


def render_interop_dts(interop: dict[str, Any]) -> str:
    return "\n".join([INTEROP_DTS_START, "// " + interop_notice(interop),
                       "// Script View inputs remain outside the frozen V1 wire value inventory.",
                       "declare namespace Internal {", "    interface ComposeInteropCatalogProps {",
                       '        "AndroidView": { "view": ComposeViewInput; };', "    }",
                       '    type ComposeInteropNodeName = "AndroidView";', "}", INTEROP_DTS_END, ""])


def render_interop_jsx(interop: dict[str, Any]) -> str:
    return "\n".join([INTEROP_JSX_START, "// " + interop_notice(interop), "declare namespace JSX {",
                       "    interface IntrinsicElements {", '        "compose:AndroidView": Internal.ComposeProps<"AndroidView">;',
                       "    }", "}", INTEROP_JSX_END, ""])


def render_interop_docs(interop: dict[str, Any]) -> str:
    component = interop["components"][0]
    return "\n".join([INTEROP_DOC_START, "<!-- " + interop_notice(interop) + " -->", "",
                       "| Component | Children | Slots | Events | Feature |",
                       "| --- | --- | --- | --- | --- |",
                       "| " + " | ".join([code(component["name"]), component["children"], "-",
                                          ", ".join(code(event["name"]) for event in component["events"]), code(interop["feature"])]) + " |",
                       "", "| Property | Script input | Getter | Required |",
                       "| --- | --- | --- | --- |",
                       "| " + " | ".join([code("view"), code(VIEW_PROJECTION["input"]), code(VIEW_PROJECTION["output"]), "true"]) + " |",
                       "", INTEROP_DOC_END, ""])


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


def load_interop_catalog(path: Path, catalog: dict[str, Any]) -> dict[str, Any]:
    try:
        return validate_interop_catalog(json.loads(read_text(path, "interop catalog export")), catalog)
    except (json.JSONDecodeError, RecursionError) as error:
        raise CatalogError(f"Invalid interop catalog JSON: {path}: {error}") from error


def load_v2_catalog(path: Path, catalog: dict[str, Any]) -> dict[str, Any]:
    try:
        return validate_v2_catalog(json.loads(read_text(path, "V2 catalog export")), catalog)
    except (json.JSONDecodeError, RecursionError) as error:
        raise CatalogError(f"Invalid V2 catalog JSON: {path}: {error}") from error


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
    generate.add_argument("--interop-catalog", type=Path, help="Also generate the separate F2 extension projections")
    generate.add_argument("--v2-catalog", type=Path, help="Also generate append-only contract V2 projections")
    generate.add_argument("--output-dir", type=Path, required=True)
    check = commands.add_parser("check", help="Require and compare both delivered P6 marked regions")
    check.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    check.add_argument("--interop-catalog", type=Path, help="Also require all delivered F2 extension projections")
    check.add_argument("--v2-catalog", type=Path, help="Also require all delivered append-only contract V2 projections")
    check.add_argument("--dts", type=Path, required=True)
    check.add_argument("--docs", type=Path, required=True)
    check.add_argument("--jsx", type=Path, help="Also require the delivered Compose JSX intrinsic map")
    args = parser.parse_args(argv)
    try:
        catalog = load_catalog(args.catalog)
        interop = load_interop_catalog(args.interop_catalog, catalog) if args.interop_catalog is not None else None
        v2 = load_v2_catalog(args.v2_catalog, catalog) if args.v2_catalog is not None else None
        dts, docs = render_dts(catalog), render_docs(catalog)
        if args.command == "generate":
            args.output_dir.mkdir(parents=True, exist_ok=True)
            (args.output_dir / "compose-catalog.fragment.d.ts").write_text(dts, encoding="utf-8", newline="\n")
            (args.output_dir / "compose-catalog.fragment.md").write_text(docs, encoding="utf-8", newline="\n")
            (args.output_dir / "compose-jsx.fragment.d.ts").write_text(render_jsx(catalog), encoding="utf-8", newline="\n")
            if interop is not None:
                for name, content in [("compose-interop.fragment.d.ts", render_interop_dts(interop)),
                                      ("compose-interop.fragment.md", render_interop_docs(interop)),
                                      ("compose-interop-jsx.fragment.d.ts", render_interop_jsx(interop))]:
                    (args.output_dir / name).write_text(content, encoding="utf-8", newline="\n")
            if v2 is not None:
                for name, content in [("compose-catalog-v2.fragment.d.ts", render_v2_dts(v2, catalog)),
                                      ("compose-catalog-v2.fragment.md", render_v2_docs(v2, catalog)),
                                      ("compose-jsx-v2.fragment.d.ts", render_v2_jsx(v2, catalog))]:
                    (args.output_dir / name).write_text(content, encoding="utf-8", newline="\n")
            print(f"GENERATED {catalog['componentCount']} components / {catalog['propertyCount']} canonical properties in {args.output_dir}; external files have NOT been checked.")
        else:
            check_artifact(args.dts, dts, DTS_START, DTS_END, "TypeScript declarations")
            check_artifact(args.docs, docs, DOC_START, DOC_END, "component documentation")
            if args.jsx is not None:
                check_artifact(args.jsx, render_jsx(catalog), JSX_START, JSX_END, "JSX intrinsic declarations")
            if interop is not None:
                require(args.jsx is not None, "An interop consistency check requires --jsx")
                check_artifact(args.dts, render_interop_dts(interop), INTEROP_DTS_START, INTEROP_DTS_END, "interop TypeScript declarations")
                check_artifact(args.docs, render_interop_docs(interop), INTEROP_DOC_START, INTEROP_DOC_END, "interop documentation")
                check_artifact(args.jsx, render_interop_jsx(interop), INTEROP_JSX_START, INTEROP_JSX_END, "interop JSX declarations")
            if v2 is not None:
                require(args.jsx is not None, "A V2 consistency check requires --jsx")
                check_artifact(args.dts, render_v2_dts(v2, catalog), V2_DTS_START, V2_DTS_END, "V2 TypeScript declarations")
                check_artifact(args.docs, render_v2_docs(v2, catalog), V2_DOC_START, V2_DOC_END, "V2 component documentation")
                check_artifact(args.jsx, render_v2_jsx(v2, catalog), V2_JSX_START, V2_JSX_END, "V2 JSX declarations")
            print(f"OK: {catalog['componentCount']} components / {catalog['propertyCount']} canonical properties match both supplied catalog regions.")
            if args.jsx is not None:
                count = sum(component['invocation'] == 'NODE' for component in catalog['components'])
                print(f"OK: {count} Compose JSX intrinsic tags match the supplied JSX region.")
            else:
                print("JSX intrinsic region was not requested and has NOT been checked.")
            if interop is not None:
                print("OK: AndroidView script inputs, documentation and JSX match the separate F2 extension.")
            else:
                print("F2 interop regions were not requested and have NOT been checked.")
            if v2 is not None:
                delta = v2_delta(v2, catalog)
                print(f"OK: V2 appends {delta['componentCount']} components / {delta['propertyCount']} canonical properties; all V1 entries are unchanged.")
            else:
                print("V2 catalog regions were not requested and have NOT been checked.")
        return 0
    except (CatalogError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
