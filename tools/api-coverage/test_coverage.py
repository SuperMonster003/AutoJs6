from pathlib import Path
import tempfile
import unittest

from check import documentation_rows, public_queries
from extract_exports import end_group, entries, extract_kotlin, function_assignment_is_supported, initializer_tail_is_supported, mail_forwarded_names, masked


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
        mail = host / 'app/src/main/java/org/autojs/autojs/runtime/api/augment/mail/Mail.kt'
        if not mail.is_file():
            self.skipTest('the augment package is gone (augment roadmap P5.2)')
        source = masked(mail.read_text(encoding='utf-8'))
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
            result = extract_kotlin(root)
            self.assertEqual({"sample.newMethod", "sample.camelCase", "sample.camelcase", "sample.aliasA", "sample.aliasB", "globalOnly", "aliasA", "aliasB"}, {item["api"] for item in result["records"]})
            self.assertEqual(["sample", "$sample", "legacy"], result["modules"][0]["aliases"])
            self.assertEqual(1, len(result["unresolved"]))
            self.assertEqual("computedName()", result["unresolved"][0]["expression"])

            # A new multiline constructor must not silently disappear from the
            # name inventory merely because the route parser cannot handle it.
            runtime_file = java / "ScriptRuntime.kt"
            runtime_file.write_text(runtime_file.read_text(encoding="utf-8").replace("Sample(this)", "Sample(\nthis\n)"), encoding="utf-8")
            self.assertTrue(any(item.get("reason") == "Unrecognized runtime mount syntax" for item in extract_kotlin(root)["unresolved"]))

    def test_inline_nested_mounts_keep_the_returned_parent_and_ignore_string_examples(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / 'app/src/main/java/org/autojs/autojs/runtime'
            base = java / 'api/augment/sample'
            base.mkdir(parents=True)
            for name, key in [('Parent', 'speech'), ('Child', 'models'), ('Other', 'tools')]:
                (base / (name + '.kt')).write_text(f'''class {name} : Augmentable() {{
                    override val keys = listOf(AugmentableKey("{key}", withDollarPrefix = false))
                    override val selfAssignmentFunctions = listOf(::list.name)
                }}''', encoding='utf-8')
            runtime = '\n'.join('import org.autojs.autojs.runtime.api.augment.sample.' + name for name in ['Parent', 'Child', 'Other'])
            runtime += '''
                Parent(this).augment(target).also { Child(this).augment(it).also { child -> Other(this).augment(child) } }
                val example = "Parent(this).augment(target)"
                // Child(this).augment(target)
            '''
            runtime_file = java / 'ScriptRuntime.kt'
            runtime_file.write_text(runtime, encoding='utf-8')
            result = extract_kotlin(root)
            self.assertEqual([], result['unresolved'])
            self.assertEqual({'speech.list', 'speech.models.list', 'speech.models.tools.list'},
                             {row['api'] for row in result['records']})
            runtime_file.write_text(runtime.replace('augment(it)', 'augment(makeTarget())'), encoding='utf-8')
            self.assertTrue(any(row.get('reason') == 'Unrecognized runtime mount syntax' for row in extract_kotlin(root)['unresolved']))

    @staticmethod
    def single_module(root, class_name, body):
        java = root / 'app/src/main/java/org/autojs/autojs/runtime'
        module = java / 'api/augment/sample' / (class_name + '.kt')
        module.parent.mkdir(parents=True, exist_ok=True)
        module.write_text(f'class {class_name} : Augmentable() {{\n{body}\n}}', encoding='utf-8')
        (java / 'ScriptRuntime.kt').write_text(f'import org.autojs.autojs.runtime.api.augment.sample.{class_name}\n'
                                              f'private fun augment(target: ScriptableObject) {{\n    {class_name}(this).augment(target)\n}}\n', encoding='utf-8')
        return extract_kotlin(root)

    def test_simple_name_tables_resolve_listed_objects_only(self):
        body = '''    override val keys = listOf(AugmentableKey("sample"))
    internal val tables = listOf(
        ColorTable.Android,
        Css,
    )
    override val selfAssignmentProperties = tables.map { table ->
        table::class.java.simpleName.lowercase() to table
    }
    override val selfAssignmentGetters = listOf("all" to Supplier { all })'''
        with tempfile.TemporaryDirectory() as directory:
            result = self.single_module(Path(directory), 'Sample', body)
            self.assertEqual([], result['unresolved'])
            rows = {row['api']: row for row in result['records']}
            self.assertEqual({'sample.android', 'sample.css', 'sample.all'}, set(rows))
            self.assertEqual(('Properties', 'selfAssignmentProperties/simpleName', 'ColorTable.Android', 4),
                             tuple(rows['sample.android'][key] for key in ['kind', 'registration', 'implementation', 'line']))
        for changed in [body.replace('Css,', 'makeTable(),'), body.replace('simpleName.lowercase()', 'simpleName'),
                        body.replace('    )\n', '    ) + extraTables\n', 1)]:
            with tempfile.TemporaryDirectory() as directory:
                result = self.single_module(Path(directory), 'Sample', changed)
                self.assertEqual(['selfAssignmentProperties'], [row['registration'] for row in result['unresolved']])
                self.assertNotIn('sample.android', {row['api'] for row in result['records']})

    def test_compose_function_map_keys_resolve_and_other_shapes_stay_unresolved(self):
        body = '''    override val keys = listOf(AugmentableKey("compose"))
    override val selfAssignmentProperties: List<Pair<Any, Any?>> by lazy {
        val functions = linkedMapOf<String, (Array<out Any?>) -> Any?>(
            "mount" to { args -> mount(args).js },
            "createElement" to { args ->
                if (args.isEmpty()) throw IllegalStateException("createElement requires a name, (or not)")
                create(args[0].toString())
            },
        )
        org.autojs.plugin.compose.api.v2.ComposeUiV2.interopCatalog.components.filter { it.invocation == ComponentInvocation.NODE }.forEach { spec ->
            functions[spec.name] = { args -> create(spec.name, args).js }
        }
        functions.map { (name, action) ->
            name to RhinoUtils.newBaseFunction(scope, name, { args -> guarded { action(args) } }, RhinoUtils.NOT_CONSTRUCTABLE)
        } + ("ComposeError" to ComposeJsErrors.constructor(scope))
    }

    override fun invoke(vararg args: Any?): Any? = guarded { mount(args).js }'''
        with tempfile.TemporaryDirectory() as directory:
            result = self.single_module(Path(directory), 'Compose', body)
            self.assertEqual([], result['unresolved'])
            rows = {row['api']: row for row in result['records']}
            self.assertEqual({'compose.mount', 'compose.createElement', 'compose.ComposeError'}, set(rows))
            self.assertEqual(('Functions', 5), (rows['compose.mount']['kind'], rows['compose.mount']['line']))
            self.assertEqual(('Properties', 'selfAssignmentProperties/map', 16),
                             tuple(rows['compose.ComposeError'][key] for key in ['kind', 'registration', 'line']))
        catalogue = 'functions[spec.name] = { args -> create(spec.name, args).js }'
        for changed in [body.replace('"mount" to {', 'computedName() to {'),
                        body.replace(catalogue, catalogue + '\n            functions["extra"] = { args -> null }'),
                        body.replace('        functions.map {', '        functions["extra"] = { args -> null }\n        functions.map {'),
                        body.replace('ComposeJsErrors.constructor(scope))', 'ComposeJsErrors.constructor(scope)) + extraPairs()'),
                        body.replace('(name, action) ->\n            name to', '(name, action) ->\n            name.uppercase() to')]:
            with tempfile.TemporaryDirectory() as directory:
                result = self.single_module(Path(directory), 'Compose', changed)
                self.assertEqual(['selfAssignmentProperties'], [row['registration'] for row in result['unresolved']])
                self.assertNotIn('compose.mount', {row['api'] for row in result['records']})

    def test_current_colour_tables_and_compose_function_map_resolve(self):
        host = Path(__file__).resolve().parents[2]
        if not (host / 'app/src/main/java/org/autojs/autojs/runtime/api/augment/compose/Compose.kt').is_file():
            self.skipTest('the augment package is gone (augment roadmap P5.2)')
        result = extract_kotlin(host)
        self.assertEqual([], [row for row in result['unresolved'] if row.get('module') in ('colors', 'compose')])
        apis = {row['api'] for row in result['records']}
        self.assertLessEqual({'colors.android', 'colors.css', 'colors.web', 'colors.material', 'compose.mount', 'compose.ComposeError'}, apis)


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
