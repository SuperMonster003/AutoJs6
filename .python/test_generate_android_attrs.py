"""Tests of .python/generate_android_attrs.py (host roadmap docs/dev/android-xml-attributes-roadmap.md A1)."""

import unittest

import generate_android_attrs as gen

SDK_VALUES = b"""<resources>
  <attr name="textSize" format="dimension"/>
  <attr name="gravity">
    <flag name="top" value="0x30"/>
    <flag name="end" value="0x00800005"/>
  </attr>
  <attr name="visibility">
    <enum name="visible" value="0"/>
    <enum name="gone" value="2"/>
  </attr>
  <declare-styleable name="TextView">
    <attr name="textSize"/>
    <attr name="gravity"/>
    <attr name="inlineOnly" format="boolean"/>
  </declare-styleable>
  <declare-styleable name="LinearLayout_Layout">
    <attr name="layout_weight" format="float"/>
  </declare-styleable>
</resources>"""

AAR_VALUES = b"""<resources>
  <declare-styleable name="ConstraintLayout_Layout">
    <attr name="android:orientation"/>
    <attr format="reference|enum" name="layout_constraintTop_toTopOf"><enum name="parent" value="0"/></attr>
    <attr format="flags" name="springBoundary"><flag name="none" value="0"/></attr>
  </declare-styleable>
</resources>"""


class ValuesFileTest(unittest.TestCase):

    def test_sdk_definitions_and_styleables(self):
        values = gen.ValuesFile(SDK_VALUES, "android")
        self.assertEqual({"dimension"}, values.attrs[("android", "textSize")].formats)
        gravity = values.attrs[("android", "gravity")]
        self.assertEqual({"flag"}, gravity.formats)
        self.assertEqual(0x30, gravity.flags["top"])
        self.assertEqual({"visible": 0, "gone": 2}, values.attrs[("android", "visibility")].enums)
        self.assertEqual([("android", "textSize"), ("android", "gravity"), ("android", "inlineOnly")], values.styleables["TextView"])
        self.assertEqual({"boolean"}, values.attrs[("android", "inlineOnly")].formats)

    def test_library_attributes_keep_android_references_and_alias_flags(self):
        values = gen.ValuesFile(AAR_VALUES, "app")
        members = values.styleables["ConstraintLayout_Layout"]
        self.assertIn(("android", "orientation"), members)
        self.assertIn(("app", "layout_constraintTop_toTopOf"), members)
        constraint = values.attrs[("app", "layout_constraintTop_toTopOf")]
        self.assertEqual({"reference", "enum"}, constraint.formats)
        self.assertEqual({"parent": 0}, constraint.enums)
        self.assertEqual({"flag"}, values.attrs[("app", "springBoundary")].formats)

    def test_formats_missing_from_attrs_xml_come_from_the_manifest_attributes(self):
        values = gen.ValuesFile(b"""<resources>
  <attr name="presetSizes"/>
  <attr name="textSize" format="dimension"/>
  <declare-styleable name="Toolbar"><attr name="logo"/></declare-styleable>
</resources>""", "android")
        manifest = gen.ValuesFile(b"""<resources>
  <attr name="logo" format="reference"/>
  <attr name="textSize" format="string"/>
</resources>""", "android")
        gen.merge_missing_formats(values, manifest)
        self.assertEqual({"reference"}, values.attrs[("android", "logo")].formats)
        self.assertEqual({"dimension"}, values.attrs[("android", "textSize")].formats)
        self.assertEqual(set(), values.attrs[("android", "presetSizes")].formats)

    def test_constraint_layout_container_and_helper_attributes_get_their_own_owners(self):
        styleables = {"app:ConstraintLayout_Layout": {
            "layout_constraintTop_toTopOf": 0, "android:layout_width": 0, "guidelineUseRtl": 0,
            "android:padding": 0, "android:maxWidth": 0, "layout_optimizationLevel": 0,
            "barrierDirection": 0, "constraint_referenced_ids": 0, "flow_wrapMode": 0, "android:orientation": 0,
        }}
        classes = {"androidx.constraintlayout.widget.ConstraintLayout#layout": ["app:ConstraintLayout_Layout"]}
        gen.split_constraint_layout(styleables, classes)
        self.assertEqual({"layout_constraintTop_toTopOf", "android:layout_width", "guidelineUseRtl", "android:orientation"}, set(styleables["app:ConstraintLayout_Layout"]))
        self.assertEqual({"android:maxWidth", "layout_optimizationLevel"}, set(styleables["app:ConstraintLayout_Container"]))
        self.assertEqual({"barrierDirection", "constraint_referenced_ids", "flow_wrapMode", "android:orientation"}, set(styleables["app:ConstraintHelper"]))
        self.assertEqual(["app:ConstraintLayout_Container"], classes["androidx.constraintlayout.widget.ConstraintLayout"])
        self.assertEqual(["app:ConstraintHelper"], classes["androidx.constraintlayout.widget.ConstraintHelper"])
        self.assertEqual([], classes["androidx.constraintlayout.widget.Barrier"])

    def test_flag_masks_are_signed_32_bit(self):
        self.assertEqual(-1, gen.parse_int("0xffffffff"))
        self.assertEqual(0x800005, gen.parse_int("0x00800005"))
        self.assertEqual(-1, gen.parse_int("-1"))


def class_file(name: str, parent: str, public: bool = True) -> bytes:
    """A minimal class file: magic, version, a four-entry constant pool, flags, this and super."""
    def utf8(text: str) -> bytes:
        data = text.encode()
        return bytes([1]) + len(data).to_bytes(2, "big") + data
    pool = utf8(name) + bytes([7]) + (1).to_bytes(2, "big") + utf8(parent) + bytes([7]) + (3).to_bytes(2, "big")
    flags = 0x0021 if public else 0x0020
    return bytes.fromhex("cafebabe") + bytes(4) + (5).to_bytes(2, "big") + pool + flags.to_bytes(2, "big") + (2).to_bytes(2, "big") + (4).to_bytes(2, "big")


class SuperclassTest(unittest.TestCase):

    def test_class_files_name_their_superclass_and_visibility(self):
        self.assertEqual(("android.view.View", True), gen.super_name(class_file("a/B", "android/view/View")))
        self.assertEqual(("android.view.View", False), gen.super_name(class_file("a/B", "android/view/View", public=False)))

    def test_chains_reach_their_ancestors(self):
        supers = {"m.Chip": "a.AppCompatCheckBox", "a.AppCompatCheckBox": "android.widget.CheckBox",
                  "android.widget.CheckBox": "android.view.View", "android.view.View": "java.lang.Object", "java.lang.Object": None}
        self.assertTrue(gen.extends("m.Chip", "android.view.View", supers))
        self.assertFalse(gen.extends("m.Chip", "android.view.ViewGroup", supers))
        self.assertFalse(gen.extends("m.Unknown", "android.view.View", supers))


class OwnerTest(unittest.TestCase):

    def test_styleables_map_to_classes_and_layout_params(self):
        classes = {"TextView": "android.widget.TextView", "ViewGroup": "android.view.ViewGroup"}
        self.assertEqual(("android.widget.TextView", ""), gen.owner_of("TextView", classes))
        self.assertEqual(("android.view.ViewGroup", "#layout"), gen.owner_of("ViewGroup_Layout", classes))
        self.assertEqual(("android.view.ViewGroup", "#layout"), gen.owner_of("ViewGroup_MarginLayout", classes))
        self.assertEqual((None, ""), gen.owner_of("Theme", classes))
        self.assertEqual((None, ""), gen.owner_of("Window_Layout", classes))


if __name__ == "__main__":
    unittest.main()
