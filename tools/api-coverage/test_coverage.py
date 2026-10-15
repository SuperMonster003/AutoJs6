from pathlib import Path
import tempfile
import unittest

from check import documentation_rows, public_queries
from extract_exports import end_group, entries, extract, function_assignment_is_supported, initializer_tail_is_supported, mail_forwarded_names, masked


class RegistrationTests(unittest.TestCase):
    def test_strings_comments_and_nested_lambdas_do_not_create_exports(self):
        text = masked('''listOf(
            "url" to "https://example.test/a,b", // ::notAnApi.name
            "getter" to Supplier { mapOf("x" to ")", "y" to 2) },
            /* ::alsoNotAnApi.name */ "raw" to """a,"b",(c)""",
        )''')
        start = text.index("(")
        found = [value for _, value in entries(text, start + 1, end_group(text, start))]
        self.assertEqual(3, len(found))
        self.assertIn("https://example.test/a,b", found[0])
        self.assertNotIn("notAnApi", text)
        self.assertNotIn("alsoNotAnApi", text)

    def test_unclosed_groups_and_raw_strings_fail(self):
        for text in ['("unterminated', '("""unterminated', '(]']:
            with self.assertRaises(ValueError):
                end_group(text, 0)

    def test_recognized_prefix_does_not_hide_dynamic_aliases_or_list_suffixes(self):
        self.assertFalse(function_assignment_is_supported('::encode.name to futureAliases()'))
        self.assertFalse(function_assignment_is_supported('::encode.name to listOf("valid").plus(futureAliases())'))
        self.assertTrue(function_assignment_is_supported('::encode.name to listOf("a", "b") to AS_GLOBAL'))
        for tail in [' + listOf(::encode.name to "futureAlias")', '\n.toMutableList().apply { add("futureAlias") }']:
            source = 'listOf(::encode.name)' + tail
            end = end_group(source, source.index('('))
            self.assertFalse(initializer_tail_is_supported(source, end, len(source), False))

    def test_mail_forwarding_constants_cannot_hide_unparsed_suffixes(self):
        import re
        host = Path(__file__).resolve().parents[2]
        source = masked((host / 'app/src/main/java/org/autojs/autojs/runtime/api/augment/mail/Mail.kt').read_text(encoding='utf-8'))
        start = source.index('listOf<Pair<Any, Any?>>(') + len('listOf<Pair<Any, Any?>>')
        end = end_group(source, start)
        limit = source.index('@RhinoRuntimeFunctionInterface', end)
        self.assertIsNotNone(mail_forwarded_names(source, end, limit))
        for field in ['FORWARDED', 'SYNC_ONLY']:
            match = re.search(r'val ' + field + r'[^\n=]*=\s*(?:listOf|setOf)\(', source)
            close = end_group(source, match.end() - 1)
            changed = source[:close + 1] + ' + listOf("futureAlias")' + source[close + 1:]
            self.assertIsNone(mail_forwarded_names(changed, end, limit))
        changed = source.replace('const val ASYNC_SUFFIX = "Async"', 'const val ASYNC_SUFFIX = "Async" + "Other"')
        self.assertIsNone(mail_forwarded_names(changed, end, limit))

    def test_registered_aliases_flags_and_unknown_forms(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / "app/src/main/java/org/autojs/autojs/runtime"
            module = java / "api/augment/sample/Sample.kt"
            module.parent.mkdir(parents=True)
            module.write_text('''class Sample : Augmentable() {
                override val keys = listOf(AugmentableKey("sample"), AugmentableKey("legacy", withDollarPrefix = false))
                override val selfAssignmentFunctions = listOf(
                    ::newMethod.name,
                    ::camelCase.name to listOf(::camelCase.name, ::camelCase.name.lowercase()),
                    ::globalOnly.name to (AS_GLOBAL or AS_IGNORED),
                    ::shared.name to listOf("aliasA", "aliasB") to AS_GLOBAL,
                    computedName(),
                )
                fun unregisteredHelper() {}
            }''', encoding="utf-8")
            (java / "ScriptRuntime.kt").write_text('''import org.autojs.autojs.runtime.api.augment.sample.Sample
                private fun augment(target: ScriptableObject) {
                    Sample(this).augment(target)
                }
            ''', encoding="utf-8")
            result = extract(root)
            self.assertEqual({"sample.newMethod", "sample.camelCase", "sample.camelcase", "sample.aliasA", "sample.aliasB", "globalOnly", "aliasA", "aliasB"}, {item["api"] for item in result["records"]})
            self.assertEqual(["sample", "$sample", "legacy"], result["modules"][0]["aliases"])
            self.assertEqual(1, len(result["unresolved"]))
            self.assertEqual("computedName()", result["unresolved"][0]["expression"])

            # A new multiline constructor must not silently disappear from the
            # name inventory merely because the route parser cannot handle it.
            runtime_file = java / "ScriptRuntime.kt"
            runtime_file.write_text(runtime_file.read_text(encoding="utf-8").replace("Sample(this)", "Sample(\nthis\n)"), encoding="utf-8")
            self.assertTrue(any(item.get("reason") == "Unrecognized runtime mount syntax" for item in extract(root)["unresolved"]))


class CoverageTests(unittest.TestCase):
    def test_doc_search_is_scoped_and_does_not_match_partial_identifiers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app.md").write_text("# launchPackage\n", encoding="utf-8")
            (root / "unrelated.md").write_text("# launch\n", encoding="utf-8")
            rows = documentation_rows([{"api": "app.launch", "module": "app", "ownerModule": "app", "member": "launch"}], root, {"documentationModules": {}})
            self.assertEqual("missing", rows[0]["status"])
            (root / "app.md").write_text("`launch` is an alias of launchPackage.\n", encoding="utf-8")
            rows = documentation_rows([{"api": "app.launch", "module": "app", "ownerModule": "app", "member": "launch"}], root, {"documentationModules": {}})
            self.assertEqual("mention", rows[0]["status"])

    def test_module_aliases_keep_their_parent_and_exclusions_are_exact(self):
        policy = {"internalExports": {"ui.__bridge": "internal"}, "standardExports": {}, "returnedObjectProbes": ["threads.disposable.().blockedGet"]}
        baseline = {"records": [{"api": "ui.__bridge"}, {"api": "ui.newPublicMethod"}], "modules": [{"module": "util.version", "aliases": ["version"]}, {"module": "powerManager", "aliases": ["powerManager", "$powerManager", "power_manager", "$power_manager"]}]}
        result = public_queries(baseline, policy)
        self.assertIn("util.version", result)
        self.assertNotIn("version", result)
        self.assertIn("ui.newPublicMethod", result)
        self.assertNotIn("ui.__bridge", result)
        self.assertIn("$power_manager", result)
        self.assertIn("threads.disposable.().blockedGet", result)


if __name__ == "__main__":
    unittest.main()
