"""SMS protection evidence requires real delivery, classification and negative controls."""
import copy
import unittest

from run_sms_probe import HOST, otp_compat, provider_rows, validate_observation


class SmsEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.messages = {"plain": "Lunch", "generic": "OTP", "webotp": "Web\n\n@example.com #736251"}
        self.stored = [{"body": body, "containsOtp": flag} for body, flag in zip(self.messages.values(), (0, 1, 513))]
        self.receipt = {"caseId": "fresh", "target": 37, "sdk": 37, "ready": True, "complete": True,
                        "queries": [{"rows": [{"body": "Lunch"}]}], "broadcasts": [{"body": "Lunch"}],
                        "notifications": [{"text": "Lunch"}] + [{"text": "Sensitive notification content hidden"}] * 2}

    def errors(self, receipt=None, stored=None):
        return validate_observation(self.receipt if receipt is None else receipt, "fresh", 37, self.messages,
                                    self.stored if stored is None else stored, True)

    def test_controls_and_classification_are_required(self):
        self.assertEqual([], self.errors())
        for key in ("queries", "broadcasts", "notifications"):
            self.assertTrue(self.errors(dict(self.receipt, **{key: []})))
        self.assertTrue(self.errors(stored=self.stored[:-1]))
        changed = copy.deepcopy(self.stored)
        changed[1]["containsOtp"] = 0
        self.assertTrue(self.errors(stored=changed))

    def test_wrong_identity_incomplete_and_leaked_otp_fail(self):
        for key, value in (("caseId", "old"), ("target", 36), ("ready", 1), ("complete", False)):
            self.assertTrue(self.errors(dict(self.receipt, **{key: value})))
        changed = copy.deepcopy(self.receipt)
        changed["notifications"].append({"extraText": "Code 482913"})
        self.assertTrue(self.errors(changed))

    def test_multiline_webotp_provider_row_is_preserved(self):
        output = "Row: 0 _id=7, address=15550001, body=Web\n\n@example.com #736251, date=1789316673513, contains_otp=513\n"
        output += "Row: 1 _id=8, address=15550001, body=Lunch, date=1789316679513, contains_otp=0\n"
        rows = provider_rows(output)
        self.assertEqual([513, 0], [row["containsOtp"] for row in rows])
        self.assertEqual(self.messages["webotp"], rows[0]["body"])

    def test_effective_override_and_strict_definition(self):
        definition = f"ChangeId(437043173; name=FILTER_GENERIC_OTP; enableSinceTargetSdk=37; packageOverrides={{{HOST}=false}}; rawOverrides={{{HOST}=true}})"
        self.assertFalse(otp_compat(definition, 37)["enabled"])
        with self.assertRaises(ValueError):
            otp_compat(definition.replace("TargetSdk=37", "TargetSdk=370"), 37)


if __name__ == "__main__":
    unittest.main()
