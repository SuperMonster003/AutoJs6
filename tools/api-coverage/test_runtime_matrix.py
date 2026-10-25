import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest

from runtime_matrix import (MARKDOWN, POLICY_SCHEMA, build, compare_markdown, default_typescript, gate, generate, markdown_state,
                            parse_host_api, parse_worker_modules, render_markdown, write)

HOST = Path(__file__).resolve().parents[2]
NODE = HOST.parent / 'AutoJs6-Plugin-NodeJs-Runtime'
BUN = HOST.parent / 'AutoJs6-Plugin-Bun-Runtime'
ACE = HOST.parent / 'AutoJs6-Plugin-Ace-Editor'

HOST_API = '''# Node API
## 状态说明
| 状态 | 含义 |
|---|---|
| **bridged** | x |
## 一. 已桥接 — 默认可用
| 模块 | 方法 | 备注 |
|---|---|---|
| `toast` | showToast | |
| `shell` / `shizuku` | exec | |
## 二. 依赖宿主提供方和 Android 授权 (bridged)
| 模块 | live 方法 | blocked 方法 |
|---|---|---|
| `image` / `images` | 编解码 | |
## 三. 稳定能力与外部依赖 (bridged-policy)
| 模块 | 门禁 |
|---|---|
| `autojs6:fetch` / `axios` | network |
## 四. 本地工具与宿主桥接模块
`files` (Node fs 便捷封装), `base64`, `jsox` (+.mathx) 由插件本地实现. `autojs6:opencc` 通过真实宿主插件提供.
## 八. 明确不可用 (denied)
| 项 | 原因 |
|---|---|
| `rawNode` | blocked |
'''

FACADE = '''
          hostModuleMethods: {
            flow:Object.keys(__autojs6_create_flow(()=>{})),
            database: ["open"], sensors: ["policy", "once"],
            image: Object.keys(__autojs6_limited_image("image")),
          },
          hostModuleConstants: {}
'''


def record(api, module, owner, member=None, implementation=None, kind='Functions', metadata='ksp', **extra):
    member = member or api.rsplit('.', 1)[-1]
    return {'api': api, 'module': module, 'ownerModule': owner, 'member': member, 'implementation': implementation or member,
            'kind': kind, 'registration': '@ScriptFunction', 'source': 'Sample.kt', 'line': 1, 'metadata': metadata, **extra}


def member(is_async=True, callable_=True):
    return {'callable': callable_, 'constructable': False, 'signatures': 1 if callable_ else 0, 'async': is_async}


EXPORTS = {
    'dataSource': {'kind': 'ksp', 'file': 'script-modules.json'},
    'records': [
        record('images.read', 'images', 'images', arity={'min': 1, 'max': 2, 'source': 'ksp'}),
        record('images.getSize', 'images', 'images'),
        record('captureScreen', '', 'images', implementation='captureScreen'),
        record('images.captureScreen', 'images', 'images', implementation='captureScreen'),
        record('screenshot', '', 'images', implementation='captureScreen'),
        record('flow.waitFor', 'flow', 'flow'),
        record('wait.async', '', 'flow', member='async'),
        record('click', '', 'automator'),
        record('files.read', 'files', 'files'),
        record('setTimeout', '', 'timers'),
        record('threads.start', 'threads', 'threads'),
        record('http.postJson', 'http', 'http'),
        record('cX', '', ''),
        record('database.open', 'database', 'database'),
        record('database.close', 'database', 'database'),
        record('colors.toInt', 'colors', 'colors'),
        record('__internal', '', ''),
        record('cvt.bytes.toBytes', 'cvt.bytes', 'cvt'),
    ],
}

NODE_SURFACES = {
    'modules': {
        'autojs6:images': {'members': {'read': member(), 'getSize': member(False), 'captureScreen': member()}},
        'images': {'members': {'read': member(), 'getSize': member(False), 'captureScreen': member()}},
        'autojs6:flow': {'members': {'waitFor': member(), 'wait': member(), 'wait.async': member()}},
        'accessibility': {'members': {'click': member()}},
        'files': {'members': {'read': member(False)}},
        'timers': {'members': {'setTimeout': member(False)}},
        'autojs6:screen_metrics': {'members': {'cX': member()}},
        'autojs6:database': {'members': {'open': member(), 'close': member()}},
        'colors': {'members': {'toInt': member(False)}},
        'converter': {'members': {'bytes': member(False, False), 'bytes.toBytes': member(False)}},
    },
    'explicitGlobal': {'flow': 'flow'},
}

BUN_SURFACES = {
    'namespaces': {
        'images': {'members': {'read': member(), 'getSize': member(False), 'captureScreen': member()}},
        'flow': {'members': {'waitFor': member()}},
        'accessibility': {'members': {'click': member()}},
        'coordinates': {'members': {'cX': member()}},
        'colors': {'members': {'toInt': member(False)}},
    },
    'clientMembers': {'toast': member()},
    'capabilities': ['accessibility.control', 'flow.control', 'image.files', 'image.process', 'screen.capture', 'screen.metrics'],
    'methods': {'image.files': ['read'], 'image.process': ['getSize'], 'screen.capture': ['captureScreen'], 'flow.control': ['waitFor']},
}

POLICY = {
    'schema': POLICY_SCHEMA,
    'node': {'nativeModules': {'timers': 'node:timers'}, 'moduleAliases': {'automator': ['accessibility'], 'cvt': ['converter']},
             'globalModules': ['autojs6:screen_metrics'], 'compatModule': 'autojs6:compat', 'memberAliases': {}},
    'bun': {'namespaceAliases': {'automator': ['accessibility']}, 'globalNamespaces': ['coordinates'],
            'namespaceCapabilities': {'images': ['image.process', 'image.files', 'screen.capture'], 'coordinates': ['screen.metrics']}},
    'exclusions': [
        {'id': 'sync-threads', 'match': ['threads.*'], 'status': 'not-portable', 'reason': 'sync-only', 'equivalent': 'worker_threads'},
        {'id': 'native-http', 'match': ['http.*'], 'columns': ['node_main', 'bun'], 'status': 'not-portable', 'reason': 'runtime-native'},
        {'id': 'shadowed-images', 'match': ['images.read'], 'columns': ['node_main'], 'status': 'denied', 'reason': 'security'},
        {'id': 'never-used', 'match': ['nothing.*'], 'status': 'denied', 'reason': 'security'},
        {'id': 'bad-reason', 'match': ['database.close'], 'columns': ['node_worker'], 'status': 'denied', 'reason': 'policy'},
    ],
}

POLICY_JSON = {'internalExports': {'__internal': 'internal'}, 'standardExports': {}}
WORKER = {'flow': None, 'database': ['open'], 'image': None}


def documentation():
    return [{'api': 'images.read', 'status': 'heading'}, {'api': 'images.getSize', 'status': 'mention'}, {'api': 'flow.waitFor', 'status': 'missing'}]


def surfaces():
    return {'rows': [
        {'api': 'images.read', 'declaration': {'status': 'explicit'}, 'aceDefault': True, 'aceAllGroups': True, 'aceStatic': True, 'arity': {'status': 'wider'}},
        {'api': 'images.getSize', 'declaration': {'status': 'explicit'}, 'aceDefault': True, 'aceAllGroups': True, 'aceStatic': None},
        {'api': 'flow.waitFor', 'declaration': {'status': 'missing-member'}, 'aceDefault': False, 'aceAllGroups': True, 'aceStatic': False},
    ]}


def matrix(exports=EXPORTS, policy=POLICY, docs=True):
    host_api = parse_host_api(HOST_API)
    return build(copy.deepcopy(exports), copy.deepcopy(policy), POLICY_JSON, NODE_SURFACES, BUN_SURFACES, host_api, WORKER,
                 {('flow', 'waitFor'): {'accessibility'}}, documentation() if docs else None, surfaces() if docs else None)


class ParsingTest(unittest.TestCase):
    def test_host_api_statuses(self):
        statuses = parse_host_api(HOST_API)
        self.assertEqual(statuses['toast'], 'bridged')
        self.assertEqual(statuses['shizuku'], 'bridged')
        self.assertEqual(statuses['images'], 'bridged')
        self.assertEqual(statuses['fetch'], 'bridged-policy')
        self.assertEqual(statuses['axios'], 'bridged-policy')
        self.assertEqual(statuses['files'], 'local-shim')
        self.assertEqual(statuses['jsox'], 'local-shim')
        self.assertNotIn('opencc', statuses)
        self.assertNotIn('rawNode', statuses)
        self.assertNotIn('状态', statuses)

    def test_worker_modules(self):
        self.assertEqual(parse_worker_modules(FACADE), {'flow': None, 'database': ['open'], 'sensors': ['policy', 'once'], 'image': None})
        with self.assertRaises(ValueError):
            parse_worker_modules('const x = {};')


class StatusTest(unittest.TestCase):
    def setUp(self):
        self.matrix = matrix()
        self.rows = {row['id']: row for row in self.matrix['rows']}

    def test_rows_equal_exports(self):
        self.assertEqual(self.matrix['summary']['rows'], len(EXPORTS['records']))
        self.assertEqual([row['id'] for row in self.matrix['rows']], sorted(r['api'] for r in EXPORTS['records']))
        self.assertEqual(self.rows['captureScreen']['aliases'], ['images.captureScreen', 'screenshot'])
        self.assertEqual(self.rows['images.read']['arity'], {'min': 1, 'max': 2, 'source': 'ksp'})
        self.assertEqual(self.rows['images.read']['metadataSource'], 'ksp')
        self.assertEqual(self.rows['__internal']['surface'], 'internal')
        self.assertTrue(all(row['rhino'] == 'native' for row in self.matrix['rows']))

    def test_same_name_and_async(self):
        cell = self.rows['images.read']['node_main']
        self.assertEqual((cell['status'], cell['module'], cell['member'], cell['async'], cell['via']), ('bridged', 'autojs6:images', 'read', True, 'same-name'))
        self.assertEqual(cell['hostApi'], 'bridged')
        self.assertFalse(self.rows['images.getSize']['node_main']['async'])
        self.assertEqual(self.rows['flow.waitFor']['node_main']['capabilities'], ['accessibility'])
        self.assertEqual(self.rows['flow.waitFor']['node_main']['explicitGlobal'], '$autojs.flow')

    def test_local_shim_alias_and_owner(self):
        self.assertEqual(self.rows['files.read']['node_main']['status'], 'local-shim')
        click = self.rows['click']['node_main']
        self.assertEqual((click['module'], click['via']), ('accessibility', 'module-alias'))
        capture = self.rows['captureScreen']['node_main']
        self.assertEqual((capture['module'], capture['member'], capture['via']), ('autojs6:images', 'captureScreen', 'owner-module'))
        self.assertEqual(self.rows['wait.async']['node_main']['member'], 'wait.async')
        self.assertEqual(self.rows['cX']['node_main']['via'], 'global-module')
        nested = self.rows['cvt.bytes.toBytes']['node_main']
        self.assertEqual((nested['module'], nested['member'], nested['via'], nested['status']), ('converter', 'bytes.toBytes', 'module-alias', 'bridged'))

    def test_alias_placement(self):
        exports = copy.deepcopy(EXPORTS)
        exports['records'].append(record('grab', '', 'other', implementation='grab'))
        exports['records'].append(record('images.grab', 'images', 'other', implementation='grab'))
        rows = {row['id']: row for row in matrix(exports)['rows']}
        self.assertEqual(rows['grab']['node_main']['status'], 'pending')
        surfaces_ = copy.deepcopy(NODE_SURFACES)
        surfaces_['modules']['autojs6:images']['members']['grab'] = member()
        result = build(exports, copy.deepcopy(POLICY), POLICY_JSON, surfaces_, BUN_SURFACES, parse_host_api(HOST_API), WORKER)
        rows = {row['id']: row for row in result['rows']}
        self.assertEqual((rows['grab']['node_main']['member'], rows['grab']['node_main']['via']), ('grab', 'alias'))

    def test_native_module(self):
        cell = self.rows['setTimeout']['node_main']
        self.assertEqual((cell['status'], cell['reason'], cell['equivalent']), ('not-portable', 'runtime-native', 'node:timers.setTimeout'))
        self.assertEqual(self.rows['setTimeout']['node_worker']['status'], 'not-portable')

    def test_worker(self):
        self.assertEqual(self.rows['flow.waitFor']['node_worker']['status'], 'bridged')
        self.assertEqual(self.rows['click']['node_worker']['status'], 'pending')
        self.assertEqual(self.rows['files.read']['node_worker']['status'], 'pending')
        self.assertEqual(self.rows['database.open']['node_worker']['status'], 'bridged')
        self.assertEqual(self.rows['database.close']['node_main']['status'], 'bridged')
        self.assertEqual(self.rows['database.close']['node_worker']['status'], 'denied')
        # image is a Worker module, images is reached through the autojs6:images name only on the main thread.
        self.assertEqual(self.rows['images.read']['node_worker']['status'], 'pending')

    def test_bun(self):
        read, size = self.rows['images.read']['bun'], self.rows['images.getSize']['bun']
        self.assertEqual((read['status'], read['namespace'], read['capability']), ('capability-gated', 'images', 'image.files'))
        self.assertEqual(size['capability'], 'image.process')
        self.assertEqual(self.rows['click']['bun']['capability'], 'accessibility.control')
        self.assertEqual(self.rows['cX']['bun']['capability'], 'screen.metrics')
        self.assertEqual(self.rows['colors.toInt']['bun']['status'], 'local-shim')
        self.assertEqual(self.rows['files.read']['bun']['status'], 'pending')
        self.assertEqual(self.rows['captureScreen']['bun']['capability'], 'screen.capture')

    def test_exclusions(self):
        self.assertEqual(self.rows['threads.start']['node_main'], {'status': 'not-portable', 'reason': 'sync-only', 'equivalent': 'worker_threads', 'rule': 'sync-threads'})
        self.assertEqual(self.rows['http.postJson']['node_worker']['status'], 'pending')
        self.assertEqual(self.rows['images.read']['node_main']['status'], 'bridged')
        conflicts = self.matrix['summary']['policyConflicts']
        self.assertIn({'rule': 'shadowed-images', 'column': 'node_main', 'kind': 'shadowed', 'ids': ['images.read']}, conflicts)
        self.assertIn({'rule': 'never-used', 'kind': 'unused'}, conflicts)
        override = copy.deepcopy(POLICY)
        override['exclusions'][2]['override'] = True
        rows = {row['id']: row for row in matrix(policy=override)['rows']}
        self.assertEqual(rows['images.read']['node_main']['status'], 'denied')

    def test_missing_reasons(self):
        missing = self.matrix['summary']['missingReasons']
        self.assertIn({'id': 'http.postJson', 'column': 'node_main', 'problem': 'runtime-native without equivalent'}, missing)
        self.assertIn({'id': 'database.close', 'column': 'node_worker', 'problem': 'reason missing or unknown'}, missing)
        self.assertNotIn('threads.start', {item['id'] for item in missing})

    def test_surface_columns(self):
        self.assertEqual((self.rows['images.read']['doc'], self.rows['images.read']['dts'], self.rows['images.read']['ace']), ('heading', 'arity', 'static'))
        self.assertEqual((self.rows['images.getSize']['doc'], self.rows['images.getSize']['dts'], self.rows['images.getSize']['ace']), ('mention', 'name', 'semantic'))
        self.assertEqual((self.rows['flow.waitFor']['doc'], self.rows['flow.waitFor']['dts'], self.rows['flow.waitFor']['ace']), ('none', 'none', 'none'))
        self.assertEqual(self.rows['click']['doc'], 'none')
        bare = {row['id']: row for row in matrix(docs=False)['rows']}
        self.assertIsNone(bare['images.read']['doc'])
        self.assertIsNone(matrix(docs=False)['summary']['byColumn']['dts'])

    def test_counts(self):
        summary = self.matrix['summary']
        for column in ['node_main', 'node_worker', 'bun']:
            self.assertEqual(sum(summary['byColumn'][column].values()), summary['rows'])
            self.assertEqual(summary['pending'][column], summary['byColumn'][column]['pending'])
        self.assertEqual(summary['byColumn']['doc']['heading'], 1)

    def test_returned_objects(self):
        exports = copy.deepcopy(EXPORTS)
        exports['returnedObjects'] = [{'object': 'ImageWrapper', 'class': 'x.ImageWrapper', 'producers': ['images.read'], 'memberSource': 'kotlin-class',
                                       'members': [{'name': 'getWidth', 'kind': 'Functions', 'source': 'ImageWrapper.kt', 'line': 3, 'arity': {'min': 0, 'max': 0}},
                                                   {'name': 'recycle', 'kind': 'Functions'}]}]
        policy = copy.deepcopy(POLICY)
        policy['returnedObjects'] = {'ImageWrapper': {'node_main': {'status': 'bridged', 'module': 'autojs6:images', 'member': 'getWidth'}}}
        policy['exclusions'].append({'id': 'prototype', 'match': ['ImageWrapper#recycle'], 'status': 'not-portable', 'reason': 'java-prototype'})
        result = matrix(exports, policy)
        rows = {row['id']: row for row in result['rows']}
        self.assertEqual(result['summary']['returnedMembers'], 2)
        self.assertEqual(result['summary']['exports'], len(EXPORTS['records']))
        width = rows['ImageWrapper#getWidth']
        self.assertEqual((width['kind'], width['object'], width['producers'], width['arity']), ('returned-member', 'ImageWrapper', ['images.read'], {'min': 0, 'max': 0}))
        self.assertEqual(width['node_main']['status'], 'bridged')
        self.assertEqual(width['bun']['status'], 'pending')
        self.assertEqual(rows['ImageWrapper#recycle']['node_worker']['reason'], 'java-prototype')

    def test_member_source_rule(self):
        exports = copy.deepcopy(EXPORTS)
        exports['returnedObjects'] = [
            {'object': 'UiObject', 'producers': ['click'], 'memberSource': 'kotlin-class', 'members': [{'name': 'click', 'kind': 'Functions'}]},
            {'object': 'Flow', 'producers': ['flow.waitFor'], 'memberSource': 'augment-prototype', 'members': [{'name': 'then', 'kind': 'Functions'}]},
        ]
        policy = copy.deepcopy(POLICY)
        policy['exclusions'].append({'id': 'java-objects', 'match': ['*#*'], 'memberSource': 'kotlin-class', 'status': 'not-portable', 'reason': 'java-prototype'})
        rows = {row['id']: row for row in matrix(exports, policy)['rows']}
        self.assertEqual(rows['UiObject#click']['bun'], {'status': 'not-portable', 'reason': 'java-prototype', 'rule': 'java-objects'})
        self.assertEqual(rows['Flow#then']['node_main']['status'], 'pending')
        self.assertEqual(rows['click']['node_main']['status'], 'bridged')

    def test_bun_member_capabilities(self):
        exports = copy.deepcopy(EXPORTS)
        exports['records'] += [record('images.save', 'images', 'images'), record('images.toBytes', 'images', 'images')]
        bun = copy.deepcopy(BUN_SURFACES)
        bun['namespaces']['images']['members'].update(save=member(), toBytes=member())
        policy = copy.deepcopy(POLICY)
        policy['bun']['memberCapabilities'] = {'_note': 'x', 'images.save': {'capability': 'image.files', 'basis': 'calls saveImage'},
                                               'images.toBytes': {'capability': 'image.unknown', 'basis': 'not a grant'},
                                               'images.stale': {'capability': 'image.files', 'basis': 'no such member'}}
        result = build(exports, policy, POLICY_JSON, NODE_SURFACES, bun, parse_host_api(HOST_API), WORKER)
        rows = {row['id']: row for row in result['rows']}
        self.assertEqual(rows['images.save']['bun']['capability'], 'image.files')
        self.assertEqual(rows['images.read']['bun']['capability'], 'image.files')
        self.assertEqual(result['summary']['missingCapabilities'], [{'id': 'images.toBytes', 'namespace': 'images', 'member': 'toBytes'}])
        self.assertEqual([item for item in result['summary']['policyConflicts'] if item['rule'].startswith('bun.')],
                         [{'rule': 'bun.memberCapabilities images.stale', 'kind': 'unused'}])
        failures = gate(result, Path('missing.md'))['failures']
        self.assertTrue(any('images.toBytes' in item for item in failures))
        self.assertEqual(matrix()['summary']['missingCapabilities'], [])

    def test_duplicate_ids_and_schema(self):
        exports = copy.deepcopy(EXPORTS)
        exports['records'].append(copy.deepcopy(exports['records'][0]))
        with self.assertRaises(ValueError):
            matrix(exports)
        with self.assertRaises(ValueError):
            matrix(policy=dict(POLICY, schema='other'))


class MarkdownTest(unittest.TestCase):
    def test_render_and_compare(self):
        result = matrix()
        text = render_markdown(result)
        self.assertEqual(text, render_markdown(matrix()))
        self.assertEqual(markdown_state(text), {'schema': 'autojs6-runtime-matrix-v1', 'dataSource': 'ksp', 'rows': len(EXPORTS['records'])})
        self.assertIn('| `threads.start` | not-portable: sync-only (sync-threads) |', text)
        self.assertIn('| images | 3 |', text)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'MATRIX.md'
            self.assertEqual(compare_markdown(result, path), 'missing')
            path.write_text(text, encoding='utf-8', newline='\n')
            self.assertEqual(compare_markdown(result, path), 'current')
            self.assertEqual(gate(result, path)['failures'], [])
            path.write_text(text + 'x', encoding='utf-8', newline='\n')
            self.assertEqual(compare_markdown(result, path), 'stale')
            self.assertEqual(len(gate(result, path)['failures']), 1)
            kotlin = copy.deepcopy(result)
            kotlin['dataSource'] = {'kind': 'kotlin'}
            self.assertEqual(compare_markdown(kotlin, path), 'skipped')
        findings = gate(result, Path('missing.md'))['findings']
        self.assertTrue(any(item.startswith('Runtime matrix pending in Node main') for item in findings))
        self.assertTrue(any('runtime-native without equivalent' in item for item in findings))

    def test_without_ace_or_reports(self):
        # check.py --skip-ace: surfaces.json has aceChecked false; the ace column stays unset and MATRIX.md is not compared.
        def without_ace():
            data = surfaces()
            data['aceChecked'] = False
            return data
        host_api = parse_host_api(HOST_API)
        full = matrix()
        partial = build(copy.deepcopy(EXPORTS), copy.deepcopy(POLICY), POLICY_JSON, NODE_SURFACES, BUN_SURFACES, host_api, WORKER,
                        {('flow', 'waitFor'): {'accessibility'}}, documentation(), without_ace())
        rows = {row['id']: row for row in partial['rows']}
        self.assertEqual((rows['images.read']['doc'], rows['images.read']['dts'], rows['images.read']['ace']), ('heading', 'arity', None))
        self.assertIsNone(partial['summary']['byColumn']['ace'])
        self.assertEqual([row['node_main'] for row in partial['rows']], [row['node_main'] for row in full['rows']])
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'MATRIX.md'
            path.write_text(render_markdown(full), encoding='utf-8', newline='\n')
            for result in (partial, matrix(docs=False)):
                self.assertEqual(compare_markdown(result, path), 'skipped')
                outcome = gate(result, path)
                self.assertEqual(outcome['failures'], [])
                self.assertTrue(outcome['findings'][0].startswith(f'{MARKDOWN} not compared: the matrix has no '))
            self.assertIn('no ace column', gate(partial, path)['findings'][0])
            with self.assertRaises(ValueError):
                write(partial, Path(temp) / 'reports', path)
            self.assertEqual(path.read_text(encoding='utf-8'), render_markdown(full))


@unittest.skipUnless(NODE.is_dir() and BUN.is_dir() and ACE.is_dir() and shutil.which('node'), 'Node, Bun and Ace checkouts are required')
class RepositoryTest(unittest.TestCase):
    """The matrix over the real runtime repositories: one row per export, Codex batch modules fully covered."""

    @classmethod
    def setUpClass(cls):
        from extract_exports import extract
        cls.exports = extract(HOST)
        with tempfile.TemporaryDirectory() as temp:
            cls.matrix = generate(HOST, NODE, BUN, default_typescript(ACE), temp, exports=copy.deepcopy(cls.exports), documentation=[], surfaces={'rows': []})

    def test_rows(self):
        self.assertEqual(self.matrix['summary']['exports'], len(self.exports['records']))
        self.assertEqual(self.matrix['dataSource']['kind'], self.exports['dataSource']['kind'])

    def test_codex_batches(self):
        for module in ['images', 'mail']:
            rows = [row for row in self.matrix['rows'] if row['module'] == module]
            self.assertTrue(rows)
            for column, status in [('node_main', 'bridged'), ('node_worker', 'bridged'), ('bun', 'capability-gated')]:
                self.assertEqual({row[column]['status'] for row in rows}, {status}, f'{module} {column}')

    def test_policy_reasons(self):
        self.assertEqual(self.matrix['summary']['missingReasons'], [])
        self.assertEqual(self.matrix['summary']['missingCapabilities'], [])


if __name__ == '__main__':
    unittest.main()
