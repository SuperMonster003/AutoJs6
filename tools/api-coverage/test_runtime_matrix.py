import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest

from runtime_matrix import (MARKDOWN, POLICY_SCHEMA, build, compare_markdown, compare_pending_baseline, default_typescript, gate, generate,
                            markdown_state, parse_host_api, parse_worker_modules, render_markdown, write, write_pending_baseline)

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


def member(is_async=True, callable_=True, rejects=False):
    return {'callable': callable_, 'constructable': False, 'signatures': 1 if callable_ else 0, 'async': is_async, 'rejects': rejects}


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


def valid_policy():
    """POLICY without its deliberately invalid exclusions (check.py --matrix fails on them since D12 was decided)."""
    policy = copy.deepcopy(POLICY)
    policy['exclusions'] = [dict(rule, equivalent='fetch') if rule['id'] == 'native-http' else rule
                            for rule in policy['exclusions'] if rule['id'] != 'bad-reason']
    return policy


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
        dotted = FACADE.replace('image:', '"jsox.mathx": Object.keys(x), image:')
        self.assertEqual(parse_worker_modules(dotted)['jsox.mathx'], None)


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
        policy['exclusions'].append({'id': 'prototype', 'match': ['ImageWrapper#recycle'], 'status': 'not-portable', 'reason': 'java-prototype'})
        result = matrix(exports, policy)
        rows = {row['id']: row for row in result['rows']}
        self.assertEqual(result['summary']['returnedMembers'], 2)
        self.assertEqual(result['summary']['exports'], len(EXPORTS['records']))
        width = rows['ImageWrapper#getWidth']
        self.assertEqual((width['kind'], width['object'], width['producers'], width['arity']), ('returned-member', 'ImageWrapper', ['images.read'], {'min': 0, 'max': 0}))
        self.assertEqual({column: width[column]['status'] for column in ['node_main', 'node_worker', 'bun']}, dict.fromkeys(['node_main', 'node_worker', 'bun'], 'pending'))
        self.assertEqual(rows['ImageWrapper#recycle']['node_worker']['reason'], 'java-prototype')

    def test_handle_types(self):
        # Q27: returnedObjects maps a host object to a runtime handle type, compared member by member.
        exports = copy.deepcopy(EXPORTS)
        exports['returnedObjects'] = [{'object': 'Flow', 'producers': ['flow.waitFor'], 'memberSource': 'augment-prototype',
                                       'members': [{'name': name, 'kind': 'Functions'} for name in ['then', 'cancel', 'sync', 'abort']]},
                                      {'object': 'Storage', 'producers': [], 'memberSource': 'augment-prototype', 'members': [{'name': 'get', 'kind': 'Functions'}]}]
        node = copy.deepcopy(NODE_SURFACES)
        node['handles'] = [{'module': 'autojs6:flow', 'type': 'Flow<unknown>', 'members': {'then': member(), 'cancel': member(), 'abort': member(rejects=True)}},
                           {'module': 'storage', 'type': 'StorageStore', 'members': {'get': member()}}]
        bun = copy.deepcopy(BUN_SURFACES)
        bun['handles'] = [{'type': 'RuntimeFlow.Flow<unknown>', 'members': {'then': member(), 'cancel': member(False)}},
                          {'type': 'RuntimeStorage.StorageStore', 'members': {'get': member()}}]
        bun['capabilities'].append('storage.control')
        policy = copy.deepcopy(POLICY)
        policy['returnedObjects'] = {
            '_note': 'x',
            'Flow': {'node': {'module': 'autojs6:flow', 'type': 'Flow<unknown>'}, 'bun': {'namespace': 'flow', 'type': 'RuntimeFlow.Flow<unknown>'}},
            'Storage': {'node': {'module': 'storage', 'type': 'StorageStore'}, 'bun': {'namespace': 'storages', 'type': 'RuntimeStorage.StorageStore', 'capability': 'storage.control'}},
            'Missing': {'node': {'module': 'storage', 'type': 'StorageStore'}},
        }
        result = build(exports, policy, POLICY_JSON, node, bun, parse_host_api(HOST_API), WORKER)
        rows = {row['id']: row for row in result['rows']}
        then = rows['Flow#then']
        self.assertEqual(then['node_main'], {'status': 'bridged', 'module': 'autojs6:flow', 'type': 'Flow<unknown>', 'member': 'then', 'async': True, 'via': 'handle-type'})
        self.assertEqual(then['node_worker']['status'], 'bridged')
        self.assertEqual(then['bun'], {'status': 'capability-gated', 'namespace': 'flow', 'type': 'RuntimeFlow.Flow<unknown>', 'member': 'then', 'async': True,
                                       'via': 'handle-type', 'capability': 'flow.control'})
        self.assertFalse(rows['Flow#cancel']['bun']['async'])
        self.assertEqual({column: rows['Flow#sync'][column]['status'] for column in ['node_main', 'bun']}, {'node_main': 'pending', 'bun': 'pending'})
        self.assertEqual(rows['Flow#abort']['node_main'], {'status': 'pending', 'rejects': True, 'module': 'autojs6:flow', 'type': 'Flow<unknown>',
                                                            'member': 'abort', 'via': 'handle-type'})
        # storage is not a Worker host module; the Bun grant comes from the mapping.
        self.assertEqual(rows['Storage#get']['node_main']['status'], 'bridged')
        self.assertEqual(rows['Storage#get']['node_worker']['status'], 'pending')
        self.assertEqual(rows['Storage#get']['bun']['capability'], 'storage.control')
        self.assertIn({'rule': 'returnedObjects Missing', 'kind': 'unused'}, result['summary']['policyConflicts'])
        policy['returnedObjects']['Flow']['node']['type'] = 'Chain'
        with self.assertRaises(ValueError):
            build(exports, policy, POLICY_JSON, node, bun, parse_host_api(HOST_API), WORKER)

    def test_rejecting_entries(self):
        # Q26: a declared entry that only returns never / Promise<never> leaves the row pending, keeping the entry.
        exports = copy.deepcopy(EXPORTS)
        exports['records'] += [record('files.open', 'files', 'files'), record('open', '', 'files', implementation='open'),
                               record('console.input', 'console', 'console'), record('files.write', 'files', 'files')]
        node = copy.deepcopy(NODE_SURFACES)
        node['modules']['files']['members'].update(open=member(False, rejects=True), write=member(False, rejects=True))
        node['modules']['autojs6:console'] = {'members': {'input': member(False, rejects=True)}}
        node['modules']['autojs6:compat'] = {'members': {'files.write': member(False)}}
        bun = copy.deepcopy(BUN_SURFACES)
        bun['namespaces']['files'] = {'members': {'open': member(False, rejects=True)}}
        bun['capabilities'].append('files.control')
        policy = copy.deepcopy(POLICY)
        policy['exclusions'].append({'id': 'console-input', 'match': ['console.input'], 'status': 'not-portable', 'reason': 'sync-only'})
        result = build(exports, policy, POLICY_JSON, node, bun, parse_host_api(HOST_API), {**WORKER, 'console': None})
        rows = {row['id']: row for row in result['rows']}
        self.assertEqual(rows['files.open']['node_main'], {'status': 'pending', 'rejects': True, 'module': 'files', 'member': 'open', 'via': 'same-name'})
        self.assertEqual(rows['open']['node_main']['via'], 'owner-module')
        self.assertEqual(rows['files.open']['bun'], {'status': 'pending', 'rejects': True, 'namespace': 'files', 'member': 'open', 'via': 'same-name'})
        # Another entry that does not reject covers the row.
        self.assertEqual((rows['files.write']['node_main']['status'], rows['files.write']['node_main']['via']), ('bridged', 'compat'))
        # An exclusion with a reason is the only way out of pending; the cell keeps the marker.
        self.assertEqual(rows['console.input']['node_worker'], {'status': 'not-portable', 'reason': 'sync-only', 'rule': 'console-input', 'rejects': True})
        self.assertEqual(result['summary']['rejects'], {'node_main': 3, 'node_worker': 1, 'bun': 2})
        text = render_markdown(result)
        self.assertIn('| `files.open` | pending (rejects) | pending | pending (rejects) |', text)
        self.assertIn('Node main 3 / Node Worker 1 / Bun 2', text)

    def test_member_aliases(self):
        # Q28: a host name mapped to a runtime member of another module (automator Toolkit helpers -> flow).
        exports = copy.deepcopy(EXPORTS)
        exports['records'] += [record('automator.clickAny', 'automator', 'automator', implementation='clickAny'),
                               record('clickAny', '', 'automator', implementation='clickAny')]
        node = copy.deepcopy(NODE_SURFACES)
        node['modules']['autojs6:flow']['members']['clickAny'] = member()
        bun = copy.deepcopy(BUN_SURFACES)
        bun['namespaces']['flow']['members']['clickAny'] = member()
        policy = copy.deepcopy(POLICY)
        policy['node']['memberAliases'] = {name: {'module': 'autojs6:flow', 'member': 'clickAny'} for name in ['automator.clickAny', 'clickAny']}
        policy['bun']['memberAliases'] = {name: {'namespace': 'flow', 'member': 'clickAny'} for name in ['automator.clickAny', 'clickAny']}
        rows = {row['id']: row for row in build(exports, policy, POLICY_JSON, node, bun, parse_host_api(HOST_API), WORKER)['rows']}
        for name in ['automator.clickAny', 'clickAny']:
            self.assertEqual((rows[name]['node_main']['module'], rows[name]['node_main']['via']), ('autojs6:flow', 'member-alias'))
            self.assertEqual(rows[name]['node_worker']['status'], 'bridged')
            self.assertEqual((rows[name]['bun']['capability'], rows[name]['bun']['via']), ('flow.control', 'member-alias'))

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

    def test_prototype_members(self):
        # Q18 (a): prototype members are module members (`<module>.<member>`), looked up like exports; rules with a
        # memberSource of returned objects never match them, rules on the id do.
        exports = copy.deepcopy(EXPORTS)
        exports['prototypes'] = [{'module': 'images', 'memberSource': 'script-prototype', 'memberList': 'kotlin-class',
                                  'members': [{'name': 'captureScreen2', 'kind': 'Functions', 'source': 'Images.kt', 'line': 9, 'arity': None}]},
                                 {'module': 'threads', 'memberSource': 'script-prototype', 'memberList': 'kotlin-class',
                                  'members': [{'name': 'atomic', 'kind': 'Functions'}]}]
        node = copy.deepcopy(NODE_SURFACES)
        node['modules']['autojs6:images']['members']['captureScreen2'] = member()
        policy = copy.deepcopy(POLICY)
        policy['exclusions'].append({'id': 'java-objects', 'match': ['*'], 'memberSource': 'kotlin-class', 'status': 'not-portable', 'reason': 'java-prototype'})
        result = build(exports, policy, POLICY_JSON, node, BUN_SURFACES, parse_host_api(HOST_API), WORKER)
        rows = {row['id']: row for row in result['rows']}
        capture = rows['images.captureScreen2']
        self.assertEqual((capture['kind'], capture['memberSource'], capture['registration'], capture['metadataSource']),
                         ('prototype-member', 'script-prototype', '@ScriptPrototype', 'kotlin-class'))
        self.assertEqual((capture['node_main']['status'], capture['node_main']['via']), ('bridged', 'same-name'))
        self.assertEqual(capture['node_worker']['status'], rows['images.read']['node_worker']['status'])
        self.assertEqual(capture['bun']['status'], 'pending')
        self.assertEqual(rows['threads.atomic']['node_main']['rule'], 'sync-threads')
        summary = result['summary']
        self.assertEqual((summary['exports'], summary['prototypeMembers'], summary['returnedMembers']), (len(EXPORTS['records']), 2, 0))
        self.assertIn('原型成员 2', render_markdown(result))

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
        result = matrix(policy=valid_policy())
        text = render_markdown(result)
        self.assertEqual(text, render_markdown(matrix(policy=valid_policy())))
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

    def test_missing_reasons_fail(self):
        # D12 decided (P1.4): an exclusion without a valid reason fails check.py --matrix.
        failures = gate(matrix(), Path('missing.md'))['failures']
        self.assertTrue(any('http.postJson' in item and 'runtime-native without equivalent' in item for item in failures))
        self.assertTrue(any('database.close' in item and 'reason missing or unknown' in item for item in failures))

    def test_without_ace_or_reports(self):
        # check.py --skip-ace: surfaces.json has aceChecked false; the ace column stays unset and MATRIX.md is not compared.
        def without_ace():
            data = surfaces()
            data['aceChecked'] = False
            return data
        host_api = parse_host_api(HOST_API)
        full = matrix(policy=valid_policy())
        partial = build(copy.deepcopy(EXPORTS), valid_policy(), POLICY_JSON, NODE_SURFACES, BUN_SURFACES, host_api, WORKER,
                        {('flow', 'waitFor'): {'accessibility'}}, documentation(), without_ace())
        rows = {row['id']: row for row in partial['rows']}
        self.assertEqual((rows['images.read']['doc'], rows['images.read']['dts'], rows['images.read']['ace']), ('heading', 'arity', None))
        self.assertIsNone(partial['summary']['byColumn']['ace'])
        self.assertEqual([row['node_main'] for row in partial['rows']], [row['node_main'] for row in full['rows']])
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'MATRIX.md'
            path.write_text(render_markdown(full), encoding='utf-8', newline='\n')
            for result in (partial, matrix(policy=valid_policy(), docs=False)):
                self.assertEqual(compare_markdown(result, path), 'skipped')
                outcome = gate(result, path)
                self.assertEqual(outcome['failures'], [])
                self.assertTrue(outcome['findings'][0].startswith(f'{MARKDOWN} not compared: the matrix has no '))
            self.assertIn('no ace column', gate(partial, path)['findings'][0])
            with self.assertRaises(ValueError):
                write(partial, Path(temp) / 'reports', path)
            self.assertEqual(path.read_text(encoding='utf-8'), render_markdown(full))



class PendingBaselineTest(unittest.TestCase):
    """Q40 (a): pending cells may only shrink against tools/api-coverage/pending-baseline.json."""

    def test_new_and_stale_cells(self):
        result = matrix(policy=valid_policy())
        markdown = Path('missing.md')
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'pending-baseline.json'
            self.assertEqual(compare_pending_baseline(result, path)[0], 'missing')
            self.assertTrue(any('pending-baseline.json is missing' in item for item in gate(result, markdown, path)['failures']))
            self.assertEqual(write_pending_baseline(result, path), [])
            data = json.loads(path.read_text(encoding='utf-8'))
            self.assertEqual(data['counts'], result['summary']['pending'])
            self.assertEqual(compare_pending_baseline(result, path), ('current', [], []))
            self.assertEqual([item for item in gate(result, markdown, path)['failures'] if 'pending' in item], [])
            # A pending cell outside the baseline fails, and is not written without allow_new.
            cell = data['pending']['bun'].pop(0)
            path.write_text(json.dumps(data), encoding='utf-8')
            self.assertEqual(compare_pending_baseline(result, path), ('differs', [('bun', cell)], []))
            self.assertTrue(any(f'new pending cell {cell} (Bun)' in item for item in gate(result, markdown, path)['failures']))
            self.assertEqual(write_pending_baseline(result, path), [('bun', cell)])
            self.assertNotIn(cell, json.loads(path.read_text(encoding='utf-8'))['pending']['bun'])
            self.assertEqual(write_pending_baseline(result, path, allow_new=True), [])
            self.assertEqual(compare_pending_baseline(result, path)[0], 'current')
            # A baseline cell that is no longer pending fails until the baseline shrinks.
            data = json.loads(path.read_text(encoding='utf-8'))
            data['pending']['node_main'].append('images.read')
            path.write_text(json.dumps(data), encoding='utf-8')
            self.assertEqual(compare_pending_baseline(result, path)[2], [('node_main', 'images.read')])
            self.assertTrue(any('no longer pending' in item for item in gate(result, markdown, path)['failures']))
            self.assertEqual(write_pending_baseline(result, path), [])
            self.assertEqual(compare_pending_baseline(result, path)[0], 'current')
            # A baseline of the other export source is not compared.
            kotlin = copy.deepcopy(result)
            kotlin['dataSource'] = {'kind': 'kotlin'}
            self.assertEqual(compare_pending_baseline(kotlin, path)[0], 'skipped')

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
        self.assertEqual([item for item in self.matrix['summary']['policyConflicts'] if item['rule'].startswith('returnedObjects')], [])

    def test_p1_5(self):
        rows = {row['id']: row for row in self.matrix['rows']}
        # Q26: the dual-app entries only reject in both runtimes.
        for column in ['node_main', 'bun']:
            self.assertEqual((rows['app.launchDual'][column]['status'], rows['app.launchDual'][column].get('rejects')), ('pending', True), column)
        # Q27: every MailClient member has a runtime handle member.
        mail = [row for row in self.matrix['rows'] if row['object'] == 'MailClient']
        self.assertTrue(mail)
        self.assertEqual({row['bun']['capability'] for row in mail}, {'mail.control'})
        self.assertEqual({row['node_main']['via'] for row in mail}, {'handle-type'})
        # Q28: the Toolkit helpers resolve to flow.
        for name in ['automator.clickAny', 'clickAny']:
            self.assertEqual((rows[name]['node_main']['module'], rows[name]['bun']['namespace']), ('autojs6:flow', 'flow'))


    def test_p1_4(self):
        rows = {row['id']: row for row in self.matrix['rows']}
        # Appendix D decided 2026-10-29: the kept exclusions.
        self.assertEqual((rows['http.client']['bun']['rule'], rows['http.client']['bun']['reason']), ('http-java-objects', 'java-prototype'))
        self.assertEqual(rows['http.postJson']['bun']['reason'], 'runtime-native')
        self.assertEqual(rows['plugins.load']['node_worker']['rule'], 'rhino-engine')
        # The groups the maintainer kept pending for P3 / P4.
        for name in ['engines.execScript', 'auto.waitFor', 'threads.start', 'keepAlive', 'app.launchDual', 'ui.layout', 'files.open']:
            self.assertEqual(rows[name]['node_worker']['status'], 'pending', name)
        self.assertEqual(rows['Mathx.sum']['node_main']['status'], 'local-shim')

    def test_p3_2a(self):
        # The shared JS tools core: local-shim on the main thread and in Workers, global data functions through autojs6:global.
        rows = {row['id']: row for row in self.matrix['rows']}
        for name in ['Mathx.sum', 'util.isEmptyObject', 'util.java.array', 'species.isString', 's13n.point', 'cvt.bytes.strict', 'random', 'isObjectSpecies']:
            for column in ['node_main', 'node_worker']:
                self.assertEqual(rows[name][column]['status'], 'local-shim', (name, column))
        self.assertEqual((rows['Mathx.sum']['node_worker']['module'], rows['random']['node_worker']['module']), ('jsox.mathx', 'autojs6:global'))
        # Q61 and Q54 (b) stay pending.
        for name in ['dayjs', 'pangu', 'cheerio', 'i18n', 'util.class']:
            self.assertEqual(rows[name]['node_main']['status'], 'pending', name)
        self.assertEqual((rows['axios']['node_main']['status'], rows['axios']['node_worker']['status']), ('local-shim', 'pending'))

    def test_p2_3(self):
        # Q18 (a): every @ScriptPrototype module is listed, its members are rows like exports, Java internals are excluded.
        self.assertEqual(self.exports['prototypesStats']['problems'], [])
        rows = {row['id']: row for row in self.matrix['rows']}
        self.assertEqual((rows['files.isFile']['kind'], rows['files.isFile']['memberSource']), ('prototype-member', 'script-prototype'))
        self.assertEqual(rows['files.isFile']['node_main']['status'], 'local-shim')
        self.assertNotIn('files.context', rows)
        self.assertEqual(rows['crypto.digest']['bun']['rule'], 'native-crypto')
        self.assertEqual(self.matrix['summary']['prototypeMembers'], sum(len(p['members']) for p in self.exports['prototypes']))

if __name__ == '__main__':
    unittest.main()
