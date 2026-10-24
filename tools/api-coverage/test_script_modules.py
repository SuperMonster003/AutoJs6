import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest

from extract_exports import DEFAULT_METADATA, compare, extract, placements, split_source

HOST = Path(__file__).resolve().parents[2]
SOURCE = 'app/src/main/java/org/autojs/autojs/runtime/api/modules/sample/SampleModule.kt'


def overload(name, line, params=()):
    return {'kotlinName': name, 'params': list(params), 'injected': [], 'returns': 'kotlin.Unit', 'source': f'{SOURCE}:{line}'}


def module(name, **fields):
    value = {'name': name, 'aliases': [], 'names': [name, '$' + name], 'dollarPrefix': True, 'eager': False, 'parent': None,
             'attributes': 4, 'emitter': False, 'builtIn': None, 'assign': False, 'proxy': False, 'install': None,
             'proxyGet': None, 'proxySet': None, 'class': 'sample.' + name.capitalize() + 'Module', 'binding': 'generated.Binding',
             'source': f'{SOURCE}:1', 'invoke': None, 'construct': None, 'prototype': None, 'functions': [], 'accessors': [], 'javaClasses': []}
    value.update(fields)
    return value


def function(name, line, aliases=(), placement='NONE', built_in_only=False):
    return {'id': line, 'name': name, 'aliases': list(aliases), 'global': placement, 'attributes': 4, 'toStringRole': 'NONE',
            'builtIn': [], 'builtInOnly': built_in_only, 'length': 0, 'overloads': [overload(name, line)]}


def accessor(name, line, kind='getter', writable=False, placement='NONE'):
    return {'id': line, 'name': name, 'kind': kind, 'writable': writable, 'type': 'kotlin.Int', 'global': placement,
            'attributes': 4, 'builtIn': [], 'source': f'{SOURCE}:{line}'}


def write_host(root, modules, kotlin=None):
    metadata = root / DEFAULT_METADATA
    metadata.parent.mkdir(parents=True)
    metadata.write_text(json.dumps({'format': 1, 'generator': 'ksp-script-modules-processor', 'modules': modules}), encoding='utf-8')
    runtime = root / 'app/src/main/java/org/autojs/autojs/runtime'
    runtime.mkdir(parents=True, exist_ok=True)
    imports, mounts = [], []
    for class_name, source in (kotlin or {}).items():
        path = runtime / 'api/augment/sample' / (class_name + '.kt')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
        imports.append('import org.autojs.autojs.runtime.api.augment.sample.' + class_name)
        mounts.append(f'    {class_name}(this).augment(target)')
    (runtime / 'ScriptRuntime.kt').write_text('\n'.join(imports) + '\nprivate fun augment(target: ScriptableObject) {\n' + '\n'.join(mounts) + '\n}\n', encoding='utf-8')
    return metadata


def quiet(call, *args, **kwargs):
    with contextlib.redirect_stderr(io.StringIO()):
        return call(*args, **kwargs)


class ScriptModulesTests(unittest.TestCase):
    def test_names_aliases_and_placements(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sample = module('sample', invoke=overload('invoke', 3), functions=[
                function('encode', 10, aliases=['enc']),
                function('both', 11, placement='ALSO'),
                function('globalOnly', 12, placement='ONLY'),
                function('extensionOnly', 13, built_in_only=True),
            ], accessors=[accessor('size', 20), accessor('mode', 21, writable=True), accessor('VERSION', 22, kind='property')],
               javaClasses=[{'name': 'Key', 'type': 'sample.Key', 'global': 'NONE', 'attributes': 4}])
            child = module('child', parent=sample['class'], names=['child'], **{'class': 'sample.ChildModule'}, functions=[function('list', 30)])
            assign = module('global', names=[], assign=True, **{'class': 'sample.GlobalModule'}, functions=[function('sleep', 40)])
            result = extract(root, write_host(root, [sample, child, assign]), 'ksp')
            records = {r['api']: r for r in result['records']}
            self.assertEqual({'sample.encode', 'sample.enc', 'sample.both', 'both', 'globalOnly', 'sample.size', 'sample.mode',
                              'sample.VERSION', 'sample.Key', 'sample.child.list', 'sleep'}, set(records))
            self.assertEqual(('', 'sample', 'encode'), (records['globalOnly']['module'], records['globalOnly']['ownerModule'], 'encode'))
            self.assertEqual('encode', records['sample.enc']['implementation'])
            self.assertEqual(['Functions', 'Getters', 'GettersAndSetters', 'Properties', 'JavaClasses'],
                             [records[api]['kind'] for api in ['sample.encode', 'sample.size', 'sample.mode', 'sample.VERSION', 'sample.Key']])
            self.assertEqual((SOURCE, 10, 'ksp'), (records['sample.enc']['source'], records['sample.enc']['line'], records['sample.enc']['metadata']))
            modules = {m['module'] + '|' + m['class']: m for m in result['modules']}
            self.assertEqual(['sample', '$sample'], modules['sample|sample.SampleModule']['aliases'])
            self.assertTrue(modules['sample|sample.SampleModule']['callable'])
            self.assertEqual(('sample', ['child']), (modules['sample.child|sample.ChildModule']['target'], modules['sample.child|sample.ChildModule']['aliases']))
            self.assertEqual(('assign', []), (modules['|sample.GlobalModule']['operation'], modules['|sample.GlobalModule']['aliases']))
            self.assertEqual([], result['unresolved'])
            self.assertEqual([], result['dataSource']['installFallback'])

    def test_a_name_of_two_modules_belongs_to_the_one_that_is_not_an_assign_module(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assign = module('global', names=[], assign=True, **{'class': 'sample.GlobalModule'}, functions=[function('isObject', 1)])
            species = module('species', functions=[function('isObject', 2, placement='ALSO')])
            for order in ([assign, species], [species, assign]):
                host = Path(tempfile.mkdtemp(dir=root))
                result = extract(host, write_host(host, order), 'ksp')
                self.assertEqual('species', {r['api']: r for r in result['records']}['isObject']['ownerModule'])

    def test_install_hooks_read_their_installs_list_or_the_kotlin_registrations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            declared = module('declared', install='install', installsComputed='names of a registry', installs=[
                {'name': 'MODE_NORMAL', 'kind': 'property', 'global': 'NONE'},
                {'name': 'waitAsync', 'kind': 'function', 'global': 'ONLY', 'source': SOURCE + ':9'},
                {'name': 'async', 'kind': 'function', 'global': 'NONE', 'attributes': 7, 'target': 'wait', 'source': SOURCE + ':9'},
            ])
            legacy = module('pinyin', install='install', functions=[function('convert', 5)])
            kotlin = {'Pinyin': '''class Pinyin : Augmentable() {
                override val keys = listOf(AugmentableKey("pinyin"))
                override val selfAssignmentFunctions = listOf(::convert.name)
                override val selfAssignmentProperties = listOf("STYLE_TONE" to 1)
            }'''}
            result = extract(root, write_host(root, [declared, legacy], kotlin), 'ksp')
            records = {r['api']: r for r in result['records']}
            self.assertEqual(('Properties', '@ScriptInstall', 'ksp'), (records['declared.MODE_NORMAL']['kind'], records['declared.MODE_NORMAL']['registration'], records['declared.MODE_NORMAL']['metadata']))
            self.assertEqual(('', 9), (records['waitAsync']['module'], records['waitAsync']['line']))
            self.assertEqual(('', 'declared', 'async', 'Functions'), tuple(records['wait.async'][k] for k in ('module', 'ownerModule', 'member', 'kind')))
            self.assertNotIn('declared.async', records)
            self.assertEqual([{'module': 'declared', 'computed': 'names of a registry'}], result['dataSource']['installComputed'])
            self.assertEqual('ksp', records['pinyin.convert']['metadata'])
            self.assertEqual(('kotlin', 'selfAssignmentProperties'), (records['pinyin.STYLE_TONE']['metadata'], records['pinyin.STYLE_TONE']['registration']))
            self.assertEqual(['pinyin'], result['dataSource']['installFallback'])
            self.assertEqual([], result['unresolved'])

    def test_a_fallback_without_kotlin_registrations_is_unresolved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = write_host(root, [module('mail', install='install')])
            (root / 'app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt').unlink()
            result = extract(root, metadata, 'ksp')
            self.assertEqual(1, len(result['unresolved']))
            self.assertIn('Kotlin registrations unavailable', result['unresolved'][0]['reason'])

    def test_auto_reads_the_metadata_only_when_it_exists(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = write_host(root, [module('sample', functions=[function('encode', 1)])],
                                  {'Sample': 'class Sample : Augmentable() {\n override val selfAssignmentFunctions = listOf(::legacyName.name)\n}'})
            self.assertEqual('ksp', quiet(extract, root)['dataSource']['kind'])
            metadata.unlink()
            result = quiet(extract, root)
            self.assertEqual('kotlin', result['dataSource']['kind'])
            self.assertEqual(['sample.legacyName'], [r['api'] for r in result['records']])
            self.assertEqual({'kotlin'}, {r['metadata'] for r in result['records'] + result['modules']})
            with self.assertRaises(FileNotFoundError):
                extract(root, source='ksp')

    def test_compare_reports_names_kinds_and_module_shapes(self):
        ksp = {'records': [{'api': 'a.x', 'module': 'a', 'ownerModule': 'a', 'member': 'x', 'kind': 'Functions'},
                           {'api': 'a.y', 'module': 'a', 'ownerModule': 'a', 'member': 'y', 'kind': 'Functions'}],
               'modules': [{'module': 'a', 'aliases': ['a'], 'callable': True}], 'unresolved': []}
        kotlin = {'records': [{'api': 'a.x', 'module': 'a', 'ownerModule': 'a', 'member': 'x', 'kind': 'Properties', 'registration': 'r'},
                              {'api': 'a.z', 'module': 'a', 'ownerModule': 'a', 'member': 'z', 'kind': 'Functions', 'registration': 'r'}],
                  'modules': [{'module': 'a', 'aliases': ['a'], 'callable': False}], 'unresolved': []}
        result = compare(ksp, kotlin)
        self.assertEqual(['a.y'], [r['api'] for r in result['onlyKsp']])
        self.assertEqual(['a.z'], [r['api'] for r in result['onlyKotlin']])
        self.assertEqual(['a.x'], [r['api'] for r in result['changed']])
        self.assertEqual(['a'], [m['module'] for m in result['modules']['changed']])

    def test_helpers(self):
        self.assertEqual(('a/b.kt', 12), split_source('a/b.kt:12'))
        self.assertEqual(('a/b.kt', None), split_source('a/b.kt'))
        self.assertEqual(['m'], placements('m', {'global': 'NONE'}))
        self.assertEqual(['m', ''], placements('m', {'global': 'ALSO'}))
        self.assertEqual([''], placements('m', {'global': 'ONLY'}))
        self.assertEqual([''], placements('', {'global': 'ALSO'}))
        self.assertEqual([], placements('m', {'global': 'NONE', 'builtInOnly': True}))


@unittest.skipUnless((HOST / DEFAULT_METADATA).is_file() and (HOST / 'app/src/main/java/org/autojs/autojs/runtime/api/augment').is_dir(),
                     'needs a KSP build and the current augment package')
class HostTransitionTests(unittest.TestCase):
    def test_every_kotlin_registration_is_still_exported(self):
        result = compare(quiet(extract, HOST, source='ksp'), extract(HOST, source='kotlin'))
        self.assertEqual([], result['onlyKotlin'])
        self.assertEqual([], result['modules']['onlyKotlin'])


if __name__ == '__main__':
    unittest.main()
