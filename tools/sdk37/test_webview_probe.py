"""Evidence must fail closed on missing pages, headers or the wrong UA expectation."""
import copy
import unittest

from run_webview_probe import CUSTOM_UA, SUFFIX, reduced_ua, validate_observation


class WebViewEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.ua = "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/150.0.0.0 Mobile Safari/537.36"
        self.receipt = {"caseId": "fresh", "complete": True, "ok": True, "defaultUa": self.ua, "settingsUa": self.ua}
        self.dom = {"ready": "complete", "ua": self.ua, "width": 400, "height": 600, "caseId": "fresh",
                    "probeWidth": 350, "probeHeight": 100, "probeColor": "rgb(20, 125, 100)"}
        self.requests = [{"path": "/fresh", "ua": self.ua}]

    def errors(self, mode="default"):
        return validate_observation("ui", mode, True, self.receipt, self.dom, self.requests, "fresh")

    def test_complete_matching_page_and_http_request_pass(self):
        self.assertEqual([], self.errors())

    def test_custom_suffix_and_reset_have_distinct_expectations(self):
        for mode, ua in (("custom", CUSTOM_UA), ("suffix", self.ua + SUFFIX), ("reset", self.ua)):
            with self.subTest(mode=mode):
                self.receipt["settingsUa"] = self.dom["ua"] = self.requests[0]["ua"] = ua
                self.assertEqual([], self.errors(mode))

    def test_old_or_failed_receipt_is_not_a_pass(self):
        original = copy.deepcopy(self.receipt)
        for field, value in (("caseId", "stale"), ("complete", False), ("ok", False)):
            self.receipt = dict(original, **{field: value})
            self.assertTrue(self.errors())

    def test_incomplete_dom_or_missing_stylesheet_is_not_a_pass(self):
        original = copy.deepcopy(self.dom)
        for field, value in (("ready", "loading"), ("width", 0), ("caseId", "stale"), ("probeHeight", 0), ("probeColor", "transparent")):
            self.dom = dict(original, **{field: value})
            self.assertTrue(self.errors())

    def test_dom_and_http_must_both_match_websettings(self):
        self.dom["ua"] = CUSTOM_UA
        self.assertTrue(self.errors())
        self.dom["ua"] = self.ua
        self.requests[0]["ua"] = CUSTOM_UA
        self.assertTrue(self.errors())
        self.requests.clear()
        self.assertTrue(self.errors())

    def test_custom_agent_cannot_hide_a_wrong_default_expectation(self):
        self.receipt["defaultUa"] = self.ua.replace("Android 10; K", "Android 17; sdk_gphone16k_x86_64 Build/TEST")
        self.receipt["settingsUa"] = self.dom["ua"] = self.requests[0]["ua"] = CUSTOM_UA
        self.assertTrue(self.errors("custom"))

    def test_reduction_requires_os_build_and_chrome_version_tokens(self):
        self.assertTrue(reduced_ua(self.ua))
        self.assertFalse(reduced_ua(self.ua.replace("Chrome/150.0.0.0", "Chrome/150.0.7777.42")))
        self.assertFalse(reduced_ua(self.ua.replace("Android 10; K", "Android 17; sdk_gphone16k_x86_64")))

    def test_docs_requires_actual_content_and_html_uses_its_own_expected_ua(self):
        self.assertTrue(validate_observation("docs", "default", True, self.receipt, self.dom, [], "fresh"))
        self.dom.update(text="AutoJs6 documentation", linkCount=10)
        self.assertEqual([], validate_observation("docs", "default", True, self.receipt, self.dom, [], "fresh"))
        self.assertTrue(validate_observation("html", "default", False, self.receipt, self.dom, [], "fresh"))
        self.assertEqual([], validate_observation("html", "default", True, self.receipt, self.dom, [], "fresh"))


if __name__ == "__main__":
    unittest.main()
