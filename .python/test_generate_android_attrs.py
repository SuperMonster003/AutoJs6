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

    def test_flag_masks_are_signed_32_bit(self):
        self.assertEqual(-1, gen.parse_int("0xffffffff"))
        self.assertEqual(0x800005, gen.parse_int("0x00800005"))
        self.assertEqual(-1, gen.parse_int("-1"))


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
