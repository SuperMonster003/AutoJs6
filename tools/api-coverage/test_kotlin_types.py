import json
from pathlib import Path
import tempfile
import unittest

import kotlin_types
from kotlin_types import Mapper, attach, load_map, node_reviewed, report, split_generic, strict_failures, summarize

RETURNED = {'objects': [{'object': 'ImageWrapper', 'class': 'org.autojs.autojs.core.image.ImageWrapper', 'probe': 'images.read.()'}]}


def mapper():
    return Mapper(load_map(), RETURNED)


def param(name, type_, optional=False, vararg=False):
    return {'name': name, 'type': type_, 'kind': 'RAW', 'optional': optional, 'vararg': vararg}


def overload(params, returns):
    return {'kotlinName': 'f', 'params': params, 'injected': [], 'arity': None, 'returns': returns, 'source': 'X.kt:1'}


class MapperTests(unittest.TestCase):
    def test_generic_arguments(self):
        self.assertEqual(split_generic('kotlin.collections.Map<kotlin.String, kotlin.collections.List<kotlin.Int>>'),
                         ('kotlin.collections.Map', ['kotlin.String', 'kotlin.collections.List<kotlin.Int>']))
        self.assertEqual(split_generic('kotlin.collections.Set<out org.Engine<out org.Source>>'), ('kotlin.collections.Set', ['org.Engine<out org.Source>']))

    def test_primitives_for_parameters_and_returns(self):
        m = mapper()
        self.assertEqual(m('kotlin.String', param=True), {'ts': {'nominal': 'string', 'open': False, 'accepted': 'string | number | boolean | object'}, 'nullable': False})
        self.assertEqual(m('kotlin.Int?'), {'ts': {'nominal': 'number', 'open': False, 'returns': 'number'}, 'nullable': True})
        self.assertEqual(m('kotlin.Any')['ts']['returns'], '{} | undefined')
        self.assertEqual(m('kotlin.Any', param=True)['ts']['accepted'], '{}')
        self.assertEqual(m('kotlin.ByteArray')['ts']['returns'], 'number[]', 'Q188 (a)')

    def test_unmapped_types(self):
        m = mapper()
        self.assertEqual(m('kotlin.Any?', param=True), {'unmapped': 'kotlin.Any?'})
        self.assertEqual(m('org.autojs.autojs.rhino.ProxyObject'), {'unmapped': 'org.autojs.autojs.rhino.ProxyObject'})
        self.assertEqual(m('kotlin.collections.Set<kotlin.String>'), {'unmapped': 'kotlin.collections.Set'})
        self.assertEqual(m('kotlin.Throwable'), {'unmapped': 'kotlin.Throwable'})

    def test_collections(self):
        m = mapper()
        self.assertEqual(m('kotlin.collections.List<kotlin.Any?>', param=True)['ts']['accepted'], 'readonly unknown[]')
        self.assertEqual(m('kotlin.collections.List<kotlin.Double>')['ts'], {'nominal': 'readonly (number)[]', 'returns': 'readonly (number)[]', 'open': False})
        self.assertEqual(m('kotlin.collections.List<kotlin.String?>')['ts']['returns'], 'readonly (string | null)[]')
        self.assertEqual(m('kotlin.collections.List<org.Unknown>')['ts'], {'nominal': 'readonly unknown[]', 'returns': 'readonly unknown[]', 'open': True})
        self.assertEqual(m('kotlin.collections.Map<kotlin.String, kotlin.Int>')['ts']['returns'], 'object')

    def test_classes(self):
        m = mapper()
        self.assertEqual(m('org.autojs.autojs.core.image.ImageWrapper?'), {'object': 'ImageWrapper', 'probe': 'images.read.()', 'nullable': True})
        self.assertEqual(m('org.opencv.core.Point?'), {'java': 'org.opencv.core.Point', 'nullable': True})
        self.assertEqual(m('androidx.core.app.NotificationCompat.Builder'), {'java': 'androidx.core.app.NotificationCompat.Builder', 'nullable': False})


class AttachTests(unittest.TestCase):
    def run_attach(self, meta, records, modules):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'script-modules.json'
            path.write_text(json.dumps(meta), encoding='utf-8')
            result = {'dataSource': {'kind': 'ksp', 'file': 'script-modules.json'}, 'records': records, 'modules': modules}
            return attach(root, result, path, returned=RETURNED)

    def test_rows_of_functions_accessors_and_calls(self):
        meta = {'format': 1, 'modules': [{
            'class': 'a.SampleModule', 'name': 'sample',
            'functions': [{'name': 'read', 'aliases': ['load'], 'overloads': [overload([param('path', 'kotlin.String')], 'org.autojs.autojs.core.image.ImageWrapper?')]}],
            'accessors': [{'name': 'size', 'type': 'kotlin.Int', 'kind': 'getter'}],
            'invoke': overload([param('x', 'kotlin.Any?', vararg=True)], 'kotlin.String'), 'construct': None}]}
        modules = [{'module': 'sample', 'class': 'a.SampleModule', 'metadata': 'ksp'}]
        records = [
            {'api': 'sample.read', 'ownerModule': 'sample', 'member': 'read', 'registration': '@ScriptFunction', 'metadata': 'ksp'},
            {'api': 'sample.load', 'ownerModule': 'sample', 'member': 'load', 'registration': '@ScriptFunction', 'metadata': 'ksp'},
            {'api': 'read', 'ownerModule': 'sample', 'member': 'read', 'registration': '@ScriptFunction', 'metadata': 'ksp'},
            {'api': 'sample.size', 'ownerModule': 'sample', 'member': 'size', 'registration': '@ScriptGetter', 'metadata': 'ksp'},
            {'api': 'sample.hook', 'ownerModule': 'sample', 'member': 'hook', 'registration': '@ScriptInstall', 'metadata': 'ksp'},
            {'api': 'compose.Text', 'ownerModule': 'compose', 'member': 'Text', 'registration': 'pluginExports', 'metadata': 'plugin:compose'},
        ]
        result = self.run_attach(meta, records, modules)
        rows = {row['api']: row for row in result['typeRows']}
        self.assertEqual(sorted(rows), ['read', 'sample.()', 'sample.load', 'sample.read', 'sample.size'])
        self.assertEqual(rows['sample.load']['overloads'][0]['returns']['type'], {'object': 'ImageWrapper', 'probe': 'images.read.()', 'nullable': True})
        self.assertEqual(rows['sample.size']['value']['type']['ts']['returns'], 'number')
        self.assertEqual((rows['sample.()']['query'], rows['sample.()']['signature']), ('sample', 'call'))
        self.assertEqual(result['typeStats'], {'rows': 5, 'notTyped': {'@ScriptInstall': 1}})

    def test_kotlin_registrations_have_no_types(self):
        result = attach('.', {'dataSource': {'kind': 'kotlin'}, 'records': [], 'modules': []})
        self.assertEqual(result['typeRows'], [])


def row(api, params=(), returns=None, kind='function'):
    return {'api': api, 'module': api.split('.')[0], 'kind': kind, 'params': [dict(index=i, **item) for i, item in enumerate(params)],
            'returns': returns, 'status': 'differs' if any(item['level'] in ('narrow', 'wide', 'error') for item in list(params) + [returns or {'level': 'match'}]) else 'typed'}


class StrictTests(unittest.TestCase):
    TABLE = {'strictLevels': {'params': ['error', 'wide'], 'returns': ['error', 'narrow']},
             'reviewed': {'entries': {'a.known parameter 1': {'level': 'wide'}, 'tasks.add*Task parameter 1': {'level': 'narrow'}}}}

    def test_unsafe_levels_fail_unless_reviewed(self):
        rows = [row('a.known', [{'level': 'wide'}]), row('a.bad', [{'level': 'wide', 'dts': 'string | null', 'kotlin': 'kotlin.Any'}]),
                row('a.safe', [{'level': 'narrow'}], {'level': 'wide'}), row('a.ret', returns={'level': 'narrow', 'dts': 'string', 'kotlin': 'kotlin.String?'}),
                row('a.acc', returns={'level': 'error'}, kind='accessor'), row('tasks.addDailyTask', [{'level': 'narrow'}])]
        failures = strict_failures(rows, self.TABLE)
        self.assertEqual(failures, ['a.bad parameter 1 (wide): d.ts string | null / Kotlin kotlin.Any',
                                    'a.ret return (narrow): d.ts string / Kotlin kotlin.String?', 'a.acc value (error): d.ts None / Kotlin None'])

    def test_stale_reviewed_entries_fail(self):
        failures = strict_failures([row('a.known', [{'level': 'match'}])], self.TABLE)
        self.assertEqual(len(failures), 2)
        self.assertTrue(all(item.startswith('Stale type-map.json reviewed entry') for item in failures))

    def test_node_reviewed_lists_uncovered_and_stale(self):
        table = {'nodeReviewed': {'entries': {'a.b return': {'level': 'error'}, 'a.gone return': {'level': 'error'}}}}
        rows = [{'api': 'a.b', 'kind': 'function', 'returns': {'level': 'error'}}, {'api': 'a.c', 'kind': 'function', 'returns': {'level': 'narrow'}}]
        reviewed, unreviewed, stale = node_reviewed(rows, table)
        self.assertEqual(reviewed, {'a.b return': 'a.b return'})
        self.assertEqual([item.split(' (')[0] for item in unreviewed], ['a.c return'])
        self.assertEqual(len(stale), 1)
        self.assertIn('a.gone return', stale[0])

    def test_summary_and_report(self):
        rows = [row('a.x', [{'level': 'match'}, {'level': 'unmapped', 'reason': 'kotlin.Any?'}], {'level': 'refined'}),
                row('a.y', [{'level': 'wide'}], {'level': 'unmapped', 'reason': 'dts-any'})]
        self.assertEqual(summarize(rows), {'params': {'match': 1, 'wide': 1, 'unmapped': 1}, 'returns': {'refined': 1, 'unmapped': 1},
                                           'rows': {'typed': 1, 'differs': 1}, 'unmappedReasons': {'kotlin.Any?': 1, 'dts-any': 1}})
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / 'types.json').write_text(json.dumps({'rows': rows}), encoding='utf-8')
            table = dict(self.TABLE, reviewed={'entries': {'a.y parameter 1': {'level': 'wide'}}})
            summary = report({'typeStats': {'notTyped': {'@ScriptInstall': 2}}}, root, table)
            data = json.loads((Path(root) / 'types.json').read_text(encoding='utf-8'))
        self.assertEqual((summary['compared'], summary['strict'], summary['reviewed'], summary['notTyped']), (2, 0, 1, {'@ScriptInstall': 2}))
        self.assertEqual(data['modules'], {'a': {'typed': 1, 'differs': 1}})
        self.assertEqual(data['reviewed'], {'a.y parameter 1': 'a.y parameter 1'})

    def test_type_map_reviewed_entries_name_their_level(self):
        table = kotlin_types.load_map()
        for pattern, entry in table['reviewed']['entries'].items():
            self.assertIn(entry['level'], ('narrow', 'wide', 'error'), pattern)
            self.assertTrue(entry.get('reason'), pattern)


class MatrixStateTests(unittest.TestCase):
    def test_typed_dts_state(self):
        import runtime_matrix
        state = lambda **surface: runtime_matrix.surface_states({'id': 'a.f'}, None, {'a.f': dict({'api': 'a.f', 'declaration': {'status': 'explicit'}}, **surface)}, False)['dts']
        self.assertEqual(state(arity={'status': 'match'}, types='typed'), 'typed')
        self.assertEqual(state(arity={'status': 'match'}, types='differs'), 'arity')
        self.assertEqual(state(arity={'status': 'narrower'}, types='typed'), 'name', 'typed needs the arity level first')
        self.assertEqual(state(types='typed'), 'typed', 'accessors have no arity row')
        self.assertEqual(state(arity={'status': 'match'}), 'arity')


if __name__ == '__main__':
    unittest.main()
