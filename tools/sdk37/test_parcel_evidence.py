"""Prevent a host-only toggle or unapplied raw override from producing a passing plugin receipt."""
import unittest

from run_parcel_tests import effective_parcel_policy


class ParcelPolicyEvidenceTest(unittest.TestCase):
    BASE = "ChangeId(416031865; name=PARCEL_HARDENING; enableSinceTargetSdk=37"

    def test_default_gate_uses_each_packages_own_target(self):
        self.assertFalse(effective_parcel_policy(self.BASE + ")", "example.plugin", 36)["enabled"])
        self.assertTrue(effective_parcel_policy(self.BASE + ")", "example.host", 37)["enabled"])

    def test_host_override_does_not_enable_a_separate_plugin(self):
        line = self.BASE + "; packageOverrides={example.host=true}; rawOverrides={example.host=true})"
        self.assertTrue(effective_parcel_policy(line, "example.host", 36)["enabled"])
        self.assertFalse(effective_parcel_policy(line, "example.plugin", 36)["enabled"])

    def test_effective_override_wins_over_target_and_raw_record(self):
        line = self.BASE + "; packageOverrides={example.host=false, example.plugin=true}; rawOverrides={example.host=true})"
        self.assertFalse(effective_parcel_policy(line, "example.host", 37)["enabled"])
        self.assertTrue(effective_parcel_policy(line, "example.plugin", 36)["enabled"])
        raw_only = self.BASE + "; rawOverrides={example.plugin=true})"
        self.assertFalse(effective_parcel_policy(raw_only, "example.plugin", 36)["enabled"])

    def test_unknown_gate_format_is_not_assumed_compatible(self):
        for line in ("", self.BASE.replace("416031865", "123") + ")", self.BASE.replace("=37", "=38") + ")"):
            with self.subTest(line=line), self.assertRaises(ValueError):
                effective_parcel_policy(line, "example.host", 37)


if __name__ == "__main__":
    unittest.main()
