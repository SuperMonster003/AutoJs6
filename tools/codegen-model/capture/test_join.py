import unittest

from capture.join import AssociationError, associate, flatten


class AssociationTests(unittest.TestCase):
    def snapshot(self):
        return {"version": 1, "root": {"className": "Root", "bounds": [0, 0, 200, 200], "depth": 0,
                "children": [{"className": "Button", "bounds": [10, 10, 110, 60], "depth": 1},
                             {"className": "Button", "bounds": [10, 80, 110, 130], "depth": 1}]}}

    def truth(self, **changes):
        target = {"key": "button", "bounds": [10, 10, 110, 60], "depth": 1,
                  "className": "Button", "capabilities": ["click"]}
        target.update(changes)
        return {"format": 1, "targets": [target]}

    def test_unique_rectangle_associates_without_private_key_in_snapshot(self):
        item = associate(self.snapshot(), self.truth())[0]
        self.assertEqual((0,), item.path)
        self.assertEqual(("click",), item.capabilities)
        self.assertNotIn("key", self.snapshot()["root"]["children"][0])

    def test_clipped_rectangle_is_rejected_not_nearest_matched(self):
        with self.assertRaises(AssociationError) as error:
            associate(self.snapshot(), self.truth(bounds=[10, 10, 110, 59]))
        self.assertEqual("NO_EXACT_BOUNDS", error.exception.code)

    def test_duplicate_bounds_are_resolved_only_by_depth(self):
        snapshot = self.snapshot()
        snapshot["root"]["children"][0]["children"] = [
            {"className": "Button", "bounds": [10, 10, 110, 60], "depth": 2}]
        self.assertEqual((0, 0), associate(snapshot, self.truth(depth=2))[0].path)

    def test_remaining_ambiguity_rejects_state(self):
        snapshot = self.snapshot()
        snapshot["root"]["children"][1]["bounds"] = [10, 10, 110, 60]
        with self.assertRaises(AssociationError):
            associate(snapshot, self.truth())

    def test_wrong_class_or_duplicate_target_cannot_be_accepted(self):
        with self.assertRaises(AssociationError):
            associate(self.snapshot(), self.truth(className="EditText"))
        truth = self.truth()
        truth["targets"].append(dict(truth["targets"][0], key="another-key"))
        with self.assertRaises(AssociationError) as error:
            associate(self.snapshot(), truth)
        self.assertEqual("DUPLICATE_TARGET_NODE", error.exception.code)

    def test_paths_and_target_order_are_deterministic(self):
        snapshot = self.snapshot()
        self.assertEqual([(), (0,), (1,)], [path for path, _ in flatten(snapshot)])
        truth = self.truth()
        truth["targets"].insert(0, {"key": "a", "bounds": [10, 80, 110, 130], "depth": 1})
        self.assertEqual(["a", "button"], [item.key for item in associate(snapshot, truth)])

    def test_invalid_truth_rejected(self):
        for truth in ({"format": 2, "targets": []}, self.truth(bounds=[True, 0, 1, 1]),
                      self.truth(bounds=[0, 0, 0, 1])):
            with self.assertRaises(ValueError):
                associate(self.snapshot(), truth)

    def test_malformed_truth_objects_and_keys_raise_validation_errors(self):
        malformed = [None, [], {"format": True, "targets": self.truth()["targets"]},
                     {"format": 1, "targets": [None]}, {"format": 1, "targets": ["button"]},
                     self.truth(key=" "), self.truth(key=True), self.truth(key="button\u0000"),
                     self.truth(key="button\u0085")]
        for truth in malformed:
            with self.subTest(truth=truth), self.assertRaises(ValueError):
                associate(self.snapshot(), truth)
        with self.assertRaises(ValueError):
            flatten(None)

    def test_rectangles_and_depth_use_android_integer_ranges(self):
        for bounds in ([0, 0, 2**31, 60], [-(2**31) - 1, 0, 110, 60], [10.0, 10, 110, 60]):
            with self.subTest(bounds=bounds), self.assertRaises(ValueError):
                associate(self.snapshot(), self.truth(bounds=bounds))
        for depth in (-1, 2**31, True, 1.0, None):
            with self.subTest(depth=depth), self.assertRaises(ValueError):
                associate(self.snapshot(), self.truth(depth=depth))
        snapshot = self.snapshot()
        bounds = [-10, -20, 110, 60]
        snapshot["root"]["children"][0]["bounds"] = bounds
        self.assertEqual((0,), associate(snapshot, self.truth(bounds=bounds))[0].path)

    def test_unknown_or_repeated_capabilities_and_invalid_classes_are_rejected(self):
        for capabilities in (["tap"], ["click", "click"], [True], [None], "click"):
            with self.subTest(capabilities=capabilities), self.assertRaises(ValueError):
                associate(self.snapshot(), self.truth(capabilities=capabilities))
        for class_name in (False, [], "", "Button\n"):
            with self.subTest(class_name=class_name), self.assertRaises(ValueError):
                associate(self.snapshot(), self.truth(className=class_name))

    def test_boolean_captured_depth_cannot_resolve_ambiguous_bounds(self):
        snapshot = self.snapshot()
        snapshot["root"]["children"][0]["depth"] = True
        snapshot["root"]["children"][1]["bounds"] = [10, 10, 110, 60]
        snapshot["root"]["children"][1]["depth"] = 2
        with self.assertRaises(ValueError):
            associate(snapshot, self.truth())


if __name__ == "__main__":
    unittest.main()
