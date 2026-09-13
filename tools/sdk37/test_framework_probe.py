"""Reject stale, incomplete and misconfigured framework evidence."""
import copy
import unittest

from run_framework_probe import CHANGES, ROWS, close_host_tasks, compat_state, validate_allfiles_page, validate_receipt


class FrameworkEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.receipt = {"caseId": "fresh", "suite": "storage", "target": 37, "complete": True,
                        "rows": [{"name": name, "ok": True} for name in sorted(ROWS["storage"])]}

    def errors(self, value=None):
        return validate_receipt(self.receipt if value is None else value, "fresh", "storage", 37)

    def test_complete_receipt_passes(self):
        self.assertEqual([], self.errors())

    def test_stale_wrong_target_and_incomplete_receipts_fail(self):
        for field, value in (("caseId", "old"), ("suite", "widgets"), ("target", 36), ("complete", 1)):
            with self.subTest(field=field):
                changed = dict(self.receipt, **{field: value})
                self.assertTrue(self.errors(changed))

    def test_missing_duplicate_and_extra_rows_fail(self):
        for rows in (self.receipt["rows"][:-1], self.receipt["rows"] + self.receipt["rows"][:1],
                     self.receipt["rows"] + [{"name": "unknown", "ok": True}]):
            self.assertTrue(self.errors(dict(self.receipt, rows=rows)))

    def test_malformed_rows_and_non_boolean_success_fail(self):
        for rows in (None, {}, [None], [{"name": []}]):
            self.assertTrue(self.errors(dict(self.receipt, rows=rows)))
        changed = copy.deepcopy(self.receipt)
        changed["rows"][0]["ok"] = 1
        self.assertTrue(self.errors(changed))
        self.assertTrue(self.errors(dict(self.receipt, error="Cleanup failed")))
        self.assertTrue(validate_receipt(None, "fresh", "storage", 37))

    def test_effective_compat_uses_package_override_not_raw_override(self):
        lines = [f"ChangeId({number}; name={name}; enableSinceTargetSdk=37; "
                 "packageOverrides={org.autojs.autojs6=false}; rawOverrides={org.autojs.autojs6=true})"
                 for name, number in CHANGES.items()]
        states = compat_state("\n".join(lines), "org.autojs.autojs6", 37)
        self.assertTrue(all(not row["enabled"] and row["source"] == "packageOverrides" for row in states.values()))
        other = compat_state("\n".join(lines), "unrelated.package", 37)
        self.assertTrue(all(row["enabled"] for row in other.values()))

    def test_unknown_or_missing_compat_definition_fails(self):
        lines = [f"ChangeId({number}; name={name}; enableSinceTargetSdk=37)" for name, number in CHANGES.items()]
        with self.assertRaises(ValueError):
            compat_state("\n".join(lines[:-1]), "org.autojs.autojs6", 37)
        with self.assertRaises(ValueError):
            compat_state("\n".join(lines).replace("enableSinceTargetSdk=37", "enableSinceTargetSdk=38"), "org.autojs.autojs6", 37)

    def test_permission_page_requires_host_identity_and_actual_switch_state(self):
        xml = '''<hierarchy><node package="com.android.settings" text="All files access"/>
<node package="com.android.settings" text="AutoJs6"/>
<node package="com.android.settings" text="Allow access to manage all files" checkable="true" checked="true"/></hierarchy>'''
        self.assertTrue(validate_allfiles_page(xml, True))
        self.assertFalse(validate_allfiles_page(xml, False))
        self.assertFalse(validate_allfiles_page(xml.replace("AutoJs6", "Another app"), True))
        self.assertFalse(validate_allfiles_page(xml.replace("com.android.settings", "unrelated.app"), True))

    def test_task_cleanup_is_limited_to_simple_host_tasks_in_user_zero(self):
        class FakeDevice:
            calls = []

            def shell(self, *args):
                self.calls.append(args)
                listing = """RootTask id=20 displayId=0 userId=0
  taskId=20: org.autojs.autojs6/org.autojs.autojs.execution.ScriptExecuteActivity topActivity=ComponentInfo{com.google.android.photopicker/MainActivity}
RootTask id=21 displayId=0 userId=0
  taskId=21: unrelated.app/MainActivity
RootTask id=22 displayId=0 userId=10
  taskId=22: org.autojs.autojs6/MainActivity
RootTask id=23 displayId=0 userId=0
  taskId=23: org.autojs.autojs6/MainActivity
  taskId=24: unrelated.app/MainActivity
"""
                return {"exitCode": 0, "stdout": listing if args == ("am", "stack", "list") else ""}

        device = FakeDevice()
        self.assertEqual([20], close_host_tasks(device))
        self.assertEqual([("am", "stack", "list"), ("am", "stack", "remove", "20")], device.calls)


if __name__ == "__main__":
    unittest.main()
