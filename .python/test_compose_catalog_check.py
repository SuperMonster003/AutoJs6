"""Pure Python regressions for the P4.3 gate; run with unittest discover -s .python."""

import contextlib
import copy
import io
import json
from pathlib import Path
import tempfile
import unittest

import compose_catalog_check as tool


def value_type(*kinds, enum=(), elements=(), minimum=None, maximum=None, length=(0, 2000)):
    return {"kinds": list(kinds), "enumValues": list(enum), "minimum": minimum, "maximum": maximum,
            "minimumExclusive": False, "integral": False, "listKinds": list(elements),
            "listMinimum": length[0], "listMaximum": length[1]}


def prop(name, kind, *, required=False, aliases=(), default=None, update="SET_PROPS"):
    return {"name": name, "type": kind, "required": required, "default": default,
            "aliases": list(aliases), "update": update}


def component(name, properties=(), invocation="NODE"):
    return {"name": name, "invocation": invocation, "children": "NONE", "scope": "ANY",
            "commands": [], "properties": list(properties), "slots": [], "events": []}


def fixture():
    components = [
        component("Switch", [prop("checked", value_type("BOOLEAN"), default={"kind": "BOOLEAN", "value": False})]),
        component("TextField", [prop("text", value_type("STRING"), aliases=["value"], default={"kind": "STRING", "value": ""}, update="EDIT_COMMAND")]),
        component("Snackbar", [prop("message", value_type("STRING"), required=True)], invocation="COMMAND"),
        component("Slider", [prop("range", value_type("LIST", elements=["NUMBER"], length=(2, 2)))]),
        component("Spacer"),
    ]
    return {"schemaVersion": 1, "contractVersion": 1, "source": tool.SOURCE,
            "componentCount": len(components), "propertyCount": 4, "valueKinds": sorted(tool.KINDS), "components": components}


def interop_fixture():
    view_type = value_type("NUMBER", minimum=1, maximum=2147483647)
    view_type["integral"] = True
    return {"schemaVersion": 1, "contractVersion": 1, "extensionVersion": 1,
            "source": tool.INTEROP_SOURCE, "feature": tool.INTEROP_FEATURE,
            "componentCount": 1, "propertyCount": 1, "valueKinds": sorted(tool.KINDS),
            "components": [component("AndroidView", [prop("view", view_type, required=True)])],
            "scriptProjection": {"AndroidView.view": copy.deepcopy(tool.VIEW_PROJECTION)}}


def v2_fixture():
    result = fixture()
    date = component("DatePicker", [
        prop("selectedDateMillis", value_type("NUMBER", "NULL"), default={"kind": "NULL", "value": None}),
        prop("displayMode", value_type("ENUM", enum=["picker", "input"]), default={"kind": "ENUM", "value": "picker"}),
    ])
    date["events"] = [
        {"name": "dateChange", "payload": [{"name": "selectedDateMillis", "type": "LONG", "required": False}]},
        {"name": "displayModeChange", "payload": [{"name": "displayMode", "type": "STRING", "required": True}]},
    ]
    chip = component("AssistChip", [prop("enabled", value_type("BOOLEAN"), default={"kind": "BOOLEAN", "value": True})])
    chip["slots"] = [{"name": "label", "required": True}]
    result.update(contractVersion=2, source=tool.V2_SOURCE)
    result["components"] += [date, chip]
    result["componentCount"] += 2
    result["propertyCount"] += 3
    return result


class CatalogGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="compose-catalog-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.catalog = tool.validate_catalog(fixture())
        self.catalog_file = self.root / "catalog.json"
        self.catalog_file.write_text(json.dumps(self.catalog), encoding="utf-8")
        self.dts = self.root / "aj6-int-compose.d.ts"
        self.docs = self.root / "compose.md"
        self.dts.write_text("// Handwritten API is outside the catalog projection.\n" + tool.render_dts(self.catalog), encoding="utf-8")
        self.docs.write_text("# Compose\n\n" + tool.render_docs(self.catalog) + "\nOther API documentation.\n", encoding="utf-8")

    def run_cli(self, *args):
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            status = tool.main(list(args))
        return status, output.getvalue(), errors.getvalue()

    def check(self):
        return self.run_cli("check", "--catalog", str(self.catalog_file), "--dts", str(self.dts), "--docs", str(self.docs))

    def test_matching_delivered_regions_pass_and_ignore_unrelated_handwritten_sections(self):
        status, output, errors = self.check()
        self.assertEqual(0, status, errors)
        self.assertIn("5 components / 4 canonical properties", output)
        self.assertEqual("", errors)

    def test_missing_component_without_properties_still_fails(self):
        text = self.dts.read_text(encoding="utf-8")
        self.dts.write_text(text.replace('        "Spacer": {\n        };\n', ""), encoding="utf-8")
        status, _, errors = self.check()
        self.assertEqual(1, status)
        self.assertIn('"Spacer"', errors)

    def test_missing_typescript_property_is_not_hidden_by_matching_component_and_hash(self):
        text = self.dts.read_text(encoding="utf-8")
        self.dts.write_text(text.replace('            "checked"?: boolean;\n', ""), encoding="utf-8")
        status, _, errors = self.check()
        self.assertEqual(1, status)
        self.assertIn('"checked"?: boolean;', errors)

    def test_wrong_typescript_property_type_fails(self):
        text = self.dts.read_text(encoding="utf-8")
        self.dts.write_text(text.replace('"checked"?: boolean;', '"checked"?: string;'), encoding="utf-8")
        status, _, errors = self.check()
        self.assertEqual(1, status)
        self.assertIn('"checked"?: string;', errors)
        self.assertIn('"checked"?: boolean;', errors)

    def test_missing_markdown_property_row_fails(self):
        lines = self.docs.read_text(encoding="utf-8").splitlines()
        self.docs.write_text("\n".join(line for line in lines if not line.startswith("| <code>Switch</code> | <code>checked</code> |")) + "\n", encoding="utf-8")
        status, _, errors = self.check()
        self.assertEqual(1, status)
        self.assertIn("component documentation differs", errors)
        self.assertIn("<code>checked</code>", errors)

    def test_wrong_markdown_type_fails(self):
        text = self.docs.read_text(encoding="utf-8")
        self.docs.write_text(text.replace("<code>checked</code> | <code>boolean</code>", "<code>checked</code> | <code>number</code>"), encoding="utf-8")
        status, _, errors = self.check()
        self.assertEqual(1, status)
        self.assertIn("<code>number</code>", errors)

    def test_aliases_required_properties_and_tuple_types_are_real_declarations(self):
        text = tool.render_dts(self.catalog)
        self.assertIn('"value"?: string;', text)
        self.assertIn('"message": string;', text)
        self.assertIn('"range"?: readonly [number, number];', text)
        node_names = next(line for line in text.splitlines() if "type ComposeCatalogNodeName" in line)
        command_names = next(line for line in text.splitlines() if "type ComposeCatalogCommandName" in line)
        self.assertNotIn('"Snackbar"', node_names)
        self.assertIn('"Snackbar"', command_names)
        self.dts.write_text(self.dts.read_text(encoding="utf-8").replace('            "value"?: string;\n', ""), encoding="utf-8")
        self.assertEqual(1, self.check()[0])

    def test_missing_external_files_and_markers_are_failures_before_p6_delivery(self):
        self.dts.unlink()
        status, output, errors = self.check()
        self.assertEqual(1, status)
        self.assertNotIn("OK:", output)
        self.assertIn("not a successful consistency check", errors)
        self.dts.write_text("declare namespace Internal {}\n", encoding="utf-8")
        self.assertIn("exactly one catalog start/end marker", self.check()[2])

    def test_duplicate_or_reversed_markers_are_rejected(self):
        self.dts.write_text(tool.render_dts(self.catalog) * 2, encoding="utf-8")
        self.assertIn("exactly one", self.check()[2])
        self.dts.write_text(tool.DTS_END + "\n" + tool.DTS_START + "\n", encoding="utf-8")
        self.assertIn("reversed", self.check()[2])

    def test_generation_is_explicitly_not_a_check(self):
        output_dir = self.root / "fragments"
        status, output, errors = self.run_cli("generate", "--catalog", str(self.catalog_file), "--output-dir", str(output_dir))
        self.assertEqual(0, status, errors)
        self.assertIn("NOT been checked", output)
        self.assertEqual(tool.render_dts(self.catalog), (output_dir / "compose-catalog.fragment.d.ts").read_text(encoding="utf-8"))
        self.assertEqual(tool.render_docs(self.catalog), (output_dir / "compose-catalog.fragment.md").read_text(encoding="utf-8"))
        self.assertEqual(tool.render_jsx(self.catalog), (output_dir / "compose-jsx.fragment.d.ts").read_text(encoding="utf-8"))

    def test_jsx_map_uses_exact_node_props_without_changing_global_element_or_legacy_tags(self):
        text = tool.render_jsx(self.catalog)
        self.assertIn('"compose:Switch": Internal.ComposeProps<"Switch">;', text)
        self.assertIn('"compose:Spacer": Internal.ComposeProps<"Spacer">;', text)
        self.assertNotIn('"compose:Snackbar"', text)
        self.assertNotIn('type Element', text)
        self.assertNotIn('interface Element ', text)
        self.assertNotIn('any', text)
        self.assertNotIn('[name:', text)
        self.assertNotIn('"text":', text)

    def test_jsx_check_is_required_when_requested_and_rejects_widening_or_missing_tags(self):
        jsx = self.root / "jsx.d.ts"
        def check_jsx():
            return self.run_cli("check", "--catalog", str(self.catalog_file), "--dts", str(self.dts),
                                "--docs", str(self.docs), "--jsx", str(jsx))
        self.assertEqual(1, check_jsx()[0])
        expected = tool.render_jsx(self.catalog)
        jsx.write_text("// Legacy declarations remain outside this generated region.\n" + expected, encoding="utf-8")
        self.assertEqual(0, check_jsx()[0])
        jsx.write_text(expected.replace('Internal.ComposeProps<"Switch">', 'any'), encoding="utf-8")
        self.assertEqual(1, check_jsx()[0])
        jsx.write_text(expected.replace('        "compose:Spacer": Internal.ComposeProps<"Spacer">;\n', ''), encoding="utf-8")
        self.assertEqual(1, check_jsx()[0])

    def test_interop_is_separate_and_projects_script_views_instead_of_wire_numbers(self):
        base = fixture()
        base["components"].append(component("Box"))
        base["componentCount"] += 1
        base = tool.validate_catalog(base)
        before = [tool.render_dts(base), tool.render_docs(base), tool.render_jsx(base)]
        interop = tool.validate_interop_catalog(interop_fixture(), base)
        self.assertIn('"view": ComposeViewInput', tool.render_interop_dts(interop))
        self.assertNotIn('"view": number', tool.render_interop_dts(interop))
        self.assertIn('"compose:AndroidView": Internal.ComposeProps<"AndroidView">', tool.render_interop_jsx(interop))
        self.assertNotIn('"compose:Text"', tool.render_interop_jsx(interop))
        self.assertNotIn('type Element', tool.render_interop_jsx(interop))
        self.assertIn('View &#124; ((context: android.content.Context) =&gt; View)', tool.render_interop_docs(interop))
        self.assertEqual(before, [tool.render_dts(base), tool.render_docs(base), tool.render_jsx(base)])

    def test_interop_rejects_wire_drift_missing_script_projection_and_base_replacement(self):
        base = fixture()
        base["components"].append(component("Box"))
        base["componentCount"] += 1
        mutations = [
            lambda x: x.update(feature="unreviewed"),
            lambda x: x.update(extensionVersion=2),
            lambda x: x["scriptProjection"].clear(),
            lambda x: x["scriptProjection"]["AndroidView.view"].update(input="number"),
            lambda x: x["components"][0]["properties"][0]["type"].update(maximum=2147483648),
            lambda x: x["components"][0].update(name="Box"),
            lambda x: x["components"][0].update(children="MANY"),
            lambda x: x["components"][0]["slots"].append({"name": "content", "required": False}),
        ]
        for mutate in mutations:
            broken = interop_fixture()
            mutate(broken)
            with self.subTest(broken=broken):
                with self.assertRaises(tool.CatalogError):
                    tool.validate_interop_catalog(broken, base)

    def test_interop_check_requires_all_three_regions_and_never_prints_partial_success(self):
        base = fixture()
        base["components"].append(component("Box"))
        base["componentCount"] += 1
        self.catalog_file.write_text(json.dumps(base), encoding="utf-8")
        interop = tool.validate_interop_catalog(interop_fixture(), base)
        interop_file = self.root / "interop.json"
        interop_file.write_text(json.dumps(interop), encoding="utf-8")
        jsx = self.root / "jsx.d.ts"
        self.dts.write_text(tool.render_dts(base) + tool.render_interop_dts(interop), encoding="utf-8")
        self.docs.write_text(tool.render_docs(base) + tool.render_interop_docs(interop), encoding="utf-8")
        jsx.write_text(tool.render_jsx(base) + tool.render_interop_jsx(interop), encoding="utf-8")
        args = ["check", "--catalog", str(self.catalog_file), "--interop-catalog", str(interop_file),
                "--dts", str(self.dts), "--docs", str(self.docs)]
        self.assertEqual(1, self.run_cli(*args)[0])
        self.assertEqual(0, self.run_cli(*args, "--jsx", str(jsx))[0])
        jsx.write_text(tool.render_jsx(base), encoding="utf-8")
        status, output, _ = self.run_cli(*args, "--jsx", str(jsx))
        self.assertEqual(1, status)
        self.assertNotIn("OK:", output)
        jsx.write_text(tool.render_jsx(base) + tool.render_interop_jsx(interop).replace('Internal.ComposeProps<"AndroidView">', 'any'), encoding="utf-8")
        self.assertEqual(1, self.run_cli(*args, "--jsx", str(jsx))[0])

    def test_crlf_outputs_are_portable_but_catalog_type_corruption_is_not_accepted(self):
        self.dts.write_bytes(tool.render_dts(self.catalog).replace("\n", "\r\n").encode("utf-8"))
        self.assertEqual(0, self.check()[0])
        broken = copy.deepcopy(self.catalog)
        broken["components"][0]["properties"][0]["type"]["kinds"] = ["UNREVIEWED_KIND"]
        with self.assertRaisesRegex(tool.CatalogError, "unsupported value kind"):
            tool.validate_catalog(broken)

    def test_v2_retains_v1_and_projects_only_appended_components_with_precise_events_and_slots(self):
        before = [tool.render_dts(self.catalog), tool.render_docs(self.catalog), tool.render_jsx(self.catalog)]
        v2 = tool.validate_v2_catalog(v2_fixture(), self.catalog)
        dts, jsx = tool.render_v2_dts(v2, self.catalog), tool.render_v2_jsx(v2, self.catalog)
        self.assertIn('"selectedDateMillis"?: null | number;', dts)
        self.assertIn('"dateChange": (this: ComposeNode<"DatePicker">, selectedDateMillis: number | null) => void;', dts)
        self.assertIn('displayMode: "input" | "picker"', dts)
        self.assertIn('type ComposeV2RequiredPropsName = "AssistChip";', dts)
        self.assertIn('get "selectedDateMillis"(): null | number;', dts)
        self.assertIn('"compose:DatePicker": Internal.ComposeProps<"DatePicker">;', jsx)
        self.assertNotIn('"compose:TextField"', jsx)
        self.assertNotIn('interface Element', jsx)
        self.assertNotIn('any', jsx)
        self.assertEqual(before, [tool.render_dts(self.catalog), tool.render_docs(self.catalog), tool.render_jsx(self.catalog)])

    def test_v2_rejects_removed_changed_or_replaced_frozen_v1_entries(self):
        for kind in ["removed", "property", "event", "interop", "source", "contract"]:
            broken = v2_fixture()
            if kind == "removed":
                broken["components"].pop(0)
                broken["componentCount"] -= 1
                broken["propertyCount"] -= 1
            elif kind == "property":
                broken["components"][0]["properties"][0]["default"]["value"] = True
            elif kind == "event":
                broken["components"][0]["events"].append({"name": "extra", "payload": []})
            elif kind == "interop":
                broken["components"].append(component("AndroidView"))
                broken["componentCount"] += 1
            elif kind == "source":
                broken["source"] = tool.SOURCE
            else:
                broken["contractVersion"] = 1
            with self.subTest(kind=kind), self.assertRaises(tool.CatalogError):
                tool.validate_v2_catalog(broken, self.catalog)

    def test_v2_check_requires_all_three_regions_and_detects_callback_and_accessor_drift(self):
        v2 = tool.validate_v2_catalog(v2_fixture(), self.catalog)
        v2_file, jsx = self.root / "v2.json", self.root / "jsx.d.ts"
        v2_file.write_text(json.dumps(v2), encoding="utf-8")
        expected = tool.render_dts(self.catalog) + tool.render_v2_dts(v2, self.catalog)
        self.dts.write_text(expected, encoding="utf-8")
        self.docs.write_text(tool.render_docs(self.catalog) + tool.render_v2_docs(v2, self.catalog), encoding="utf-8")
        jsx.write_text(tool.render_jsx(self.catalog) + tool.render_v2_jsx(v2, self.catalog), encoding="utf-8")
        args = ["check", "--catalog", str(self.catalog_file), "--v2-catalog", str(v2_file),
                "--dts", str(self.dts), "--docs", str(self.docs)]
        self.assertEqual(1, self.run_cli(*args)[0])
        self.assertEqual(0, self.run_cli(*args, "--jsx", str(jsx))[0])
        for before, after in [('selectedDateMillis: number | null', 'selectedDateMillis: number'),
                              ('get "selectedDateMillis"(): null | number;', 'get "selectedDateMillis"(): string;')]:
            self.dts.write_text(expected.replace(before, after), encoding="utf-8")
            status, output, _ = self.run_cli(*args, "--jsx", str(jsx))
            self.assertEqual(1, status)
            self.assertNotIn("OK:", output)

    def test_markdown_union_types_and_default_pipes_do_not_create_extra_table_columns(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["components"][0]["properties"].append(prop("color", value_type("COLOR", "NULL")))
        catalog["components"][1]["properties"][0]["default"]["value"] = "left|right"
        catalog["propertyCount"] += 1
        text = tool.render_docs(tool.validate_catalog(catalog))
        self.assertIn("ComposeColorInput &#124; null", text)
        self.assertIn("left&#124;right", text)
        for line in text.splitlines():
            if line.startswith("|"):
                self.assertEqual(10, len(line.split("|")), line)

    def test_missing_documentation_table_is_not_a_successful_p6_check(self):
        self.docs.write_text("# Compose\nP6 reference is not delivered yet.\n", encoding="utf-8")
        status, output, errors = self.check()
        self.assertEqual(1, status)
        self.assertNotIn("OK:", output)
        self.assertIn("component documentation: expected exactly one catalog start/end marker", errors)

    def test_partial_or_duplicate_export_is_rejected_before_generation(self):
        broken = copy.deepcopy(self.catalog)
        broken["components"].pop()
        with self.assertRaisesRegex(tool.CatalogError, "component count"):
            tool.validate_catalog(broken)
        broken = copy.deepcopy(self.catalog)
        broken["components"][0]["properties"].append(copy.deepcopy(broken["components"][0]["properties"][0]))
        with self.assertRaisesRegex(tool.CatalogError, "duplicate property/alias"):
            tool.validate_catalog(broken)
        broken = copy.deepcopy(self.catalog)
        broken["components"][0]["properties"][0]["required"] = "false"
        with self.assertRaisesRegex(tool.CatalogError, "required must be a boolean"):
            tool.validate_catalog(broken)


if __name__ == "__main__":
    unittest.main()
