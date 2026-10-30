import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest

import runtime_matrix
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
        # Q63 (a): obsoleted is a decided reason (appendix D D-21).
        override = copy.deepcopy(POLICY)
        override['exclusions'][4]['reason'] = 'obsoleted'
        self.assertNotIn('database.close', {item['id'] for item in matrix(policy=override)['summary']['missingReasons']})

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
            'Storage': {'node': {'module': 'storage', 'type': 'StorageStore', 'typeScope': 'global'},
                        'bun': {'namespace': 'storages', 'type': 'RuntimeStorage.StorageStore', 'capability': 'storage.control'}},
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
        # A global type of the module's declarations keeps typeScope for the editor queries; module types have none.
        self.assertEqual(rows['Storage#get']['node_main']['typeScope'], 'global')
        self.assertNotIn('typeScope', then['node_main'])
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

    def test_bun_nested_alias_and_member_grant(self):
        exports = copy.deepcopy(EXPORTS)
        exports['records'] += [record('Mathx.sum', 'Mathx', 'Mathx'), record('util.java.array', 'util.java', 'util.java'),
                               record('util.isArray', 'util', 'util')]
        bun = copy.deepcopy(BUN_SURFACES)
        bun['namespaces']['jsox'] = {'members': {'mathx': member(False), 'mathx.sum': member(False)}}
        bun['namespaces']['util'] = {'members': {'java': member(False), 'java.array': member(), 'isArray': member(False)}}
        bun['capabilities'].append('java.interop')
        policy = copy.deepcopy(POLICY)
        policy['bun']['namespaceAliases']['Mathx'] = ['jsox.mathx']
        policy['bun']['memberCapabilities'] = {'util.java.array': {'capability': 'java.interop', 'basis': 'Java facade'}}
        result = build(exports, policy, POLICY_JSON, NODE_SURFACES, bun, parse_host_api(HOST_API), WORKER)
        rows = {row['id']: row['bun'] for row in result['rows']}
        self.assertEqual((rows['Mathx.sum']['status'], rows['Mathx.sum']['namespace'], rows['Mathx.sum']['member'], rows['Mathx.sum']['via']),
                         ('local-shim', 'jsox', 'mathx.sum', 'module-alias'))
        self.assertEqual((rows['util.java.array']['status'], rows['util.java.array']['capability']), ('capability-gated', 'java.interop'))
        self.assertEqual(rows['util.isArray']['status'], 'local-shim')
        self.assertNotIn('capability', rows['util.isArray'])
        self.assertEqual(result['summary']['missingCapabilities'], [])

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

    def test_decisions_are_kept_and_added_once(self):
        result = matrix(policy=valid_policy())
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'pending-baseline.json'
            self.assertEqual(write_pending_baseline(result, path, decisions=['Q1: first']), [])
            self.assertEqual(write_pending_baseline(result, path, allow_new=True, decisions=['Q2: second', 'Q1: first', 'Q2: second']), [])
            self.assertEqual(write_pending_baseline(result, path), [])
            self.assertEqual(json.loads(path.read_text(encoding='utf-8'))['decisions'], ['Q1: first', 'Q2: second'])


class DeferredNameGateTest(unittest.TestCase):
    """policy.json deferredNameGate (Q115 (a)): the matrix counts the deferred root's rows and MATRIX.md names the decision."""

    def test_summary_and_markdown(self):
        result = build(copy.deepcopy(EXPORTS), valid_policy(), dict(POLICY_JSON, deferredNameGate={'flow': 'Q0: later'}), NODE_SURFACES, BUN_SURFACES,
                       parse_host_api(HOST_API), WORKER, None, documentation(), surfaces())
        # A global placement counts under its owner module, as in the documentation gate.
        rows = sum((row['module'] or row['ownerModule']).split('.')[0] == 'flow' for row in result['rows'] if row['kind'] == 'export')
        self.assertEqual(rows, 2)
        self.assertEqual(result['summary']['deferredNameGate'], {'flow': {'rows': rows, 'note': 'Q0: later'}})
        self.assertIn(f'名称门禁暂缓 (policy.json deferredNameGate; doc / d.ts / Ace 列照实记录, 缺口不计入 check.py 失败): flow {rows} 行 (Q0: later).',
                      render_markdown(result))
        self.assertNotIn('名称门禁暂缓', render_markdown(matrix(policy=valid_policy())))

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
        # Q26: an entry that only rejects stays pending; console.input (D-7) left with P3.3 (Q165 (a), test_p3_3), the dual-app
        # entries with P3.1a (test_p3_1a).
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
        # The groups the maintainer kept pending for P3 / P4 all came: app.launchDual with P3.1a (test_p3_1a), engines.execScript
        # in Workers with P3.1b (test_p3_1b), auto.waitFor, threads.start and keepAlive with P3.4 (test_p3_4), ui.layout in Workers
        # with P3.3 (test_p3_3).
        self.assertEqual(rows['Mathx.sum']['node_main']['status'], 'local-shim')

    def test_p3_1a(self):
        # The app and shell family: app from one factory in both threads (Workers gain app), the D-4 dual-user helpers on
        # the module and the global object, the shell / shizuku members with the Q18 prototype rows, the global key
        # functions and the ShellProcess members; app.autojs came with the autojs module of P3.1b (Q93 (2), test_p3_1b).
        rows = {row['id']: row for row in self.matrix['rows']}
        for name in ['app.launchDual', 'launchDual', 'app.getInstalledPackages', 'shell.execCommand', 'shizuku.isRunning', 'Home', 'ShellProcess#getInputStream']:
            for column in ['node_main', 'node_worker']:
                self.assertEqual(rows[name][column]['status'], 'bridged', (name, column))
        self.assertEqual((rows['app.launchDual']['node_worker']['module'], rows['Home']['node_worker']['module']), ('autojs6:app', 'autojs6:shell'))
        self.assertEqual(rows['app.autojs']['node_main']['status'], 'bridged')

    def test_p3_1b(self):
        # The system information & engines family: the autojs6: modules of the shared system factory in both threads, the
        # global system functions through the autojs6:system global module, powerManager / workManager through module
        # aliases (power_manager, tasks), the notice extras, plugins.extend* / tools, the host view of engines (Q131 (a)),
        # dialogs.build (Q132 (a)); memory.watch is runtime-native (Q134 (a), D-22).
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, status, module, via in [
                ('autojs.getRootMode', 'bridged', 'autojs6:autojs', 'same-name'), ('autojs.version.isAtLeast', 'bridged', 'autojs6:autojs', 'same-name'),
                ('R', 'bridged', 'autojs6:autojs', 'owner-module'), ('app.autojs', 'bridged', 'autojs6:app', 'same-name'),
                ('installer.session', 'bridged', 'autojs6:installer', 'same-name'), ('memory.collect', 'bridged', 'autojs6:memory', 'same-name'),
                ('settings.export', 'bridged', 'autojs6:settings', 'same-name'), ('sysprops.getAll', 'bridged', 'autojs6:sysprops', 'same-name'),
                ('powerManager.isIgnoringBatteryOptimizations', 'bridged', 'autojs6:power_manager', 'module-alias'),
                ('workManager.addDailyTask', 'bridged', 'autojs6:tasks', 'module-alias'),
                ('notice.channel.create', 'bridged', 'autojs6:notifications', 'module-alias'),
                ('currentPackage', 'bridged', 'autojs6:system', 'global-module'), ('waitForActivity', 'bridged', 'autojs6:system', 'global-module'),
                ('isAutoJs6', 'bridged', 'autojs6:system', 'global-module'), ('engines.execScript', 'bridged', 'engines', 'same-name'),
                ('engines.execAutoFile', 'bridged', 'engines', 'same-name'), ('dialogs.build', 'bridged', 'autojs6:dialogs', 'same-name'),
                # plugins stays a local module (HOST-API section 4); extend* use the shared jsox, tools the host routes.
                ('plugins.extendAllBut', 'local-shim', 'plugins', 'same-name'), ('plugins.tools.callAsync', 'local-shim', 'plugins', 'same-name')]:
            for column in ['node_main', 'node_worker']:
                cell = rows[name][column]
                self.assertEqual((cell['status'], cell['module'], cell['via']), (status, module, via), (name, column))
        # Rhino getClip / setClip: autojs6:compat on the main thread, autojs6:system in Workers.
        self.assertEqual((rows['getClip']['node_main']['module'], rows['getClip']['node_worker']['module']), ('autojs6:compat', 'autojs6:system'))
        for column in ['node_main', 'node_worker']:
            self.assertEqual((rows['memory.watch'][column]['status'], rows['memory.watch'][column]['reason']), ('not-portable', 'runtime-native'))
        self.assertEqual(rows['plugins.load']['node_worker']['status'], 'not-portable')

    def test_p3_1c(self):
        # The device, sensors and media family: the shared device / sensors factories in both threads (the startup snapshot
        # with Rhino's fields, Q148 (a)), Rhino's device queries over RuntimeDeviceBridge with device.identifiers (Q146 (a)),
        # Rhino's sensor names and delays (Q147 (a)) and Rhino's media members in autojs6:media, forwarded by Workers (Q149 (a)).
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, module, capabilities in [
                ('device.baseOS', 'autojs6:device', None), ('device.manufacturers', 'autojs6:device', None),
                ('device.getIMEI', 'autojs6:device', ['device', 'device.identifiers']),
                ('device.keepAwake', 'autojs6:device', ['device', 'device.power']), ('device.vibrate', 'autojs6:device', ['device']),
                ('sensors.register', 'autojs6:sensors', None), ('sensors.delay', 'autojs6:sensors', None),
                ('sensors.once', 'autojs6:sensors', ['sensors']), ('media.playMusic', 'autojs6:media', None),
                ('media.scanFile', 'autojs6:media', None)]:
            for column in ['node_main', 'node_worker']:
                cell = rows[name][column]
                self.assertEqual((cell['status'], cell['module'], cell['via'], cell.get('capabilities')),
                                 ('bridged', module, 'same-name', capabilities), (name, column))
        family = [row for row in self.matrix['rows'] if (row['module'] or row['ownerModule'] or '').split('.')[0] in ('device', 'sensors', 'media')]
        self.assertGreaterEqual(len(family), 116)
        for row in family:
            for column in ['node_main', 'node_worker']:
                self.assertNotEqual(row[column]['status'], 'pending', (row['id'], column))

    def test_p3_3(self):
        # The UI family: Rhino's ui members (the content view and window members, D-17 / Q164 (a)), autojs6:floaty / web /
        # terminal of the shared UI factory (terminal with its own capability, Q167 (a)), console input and build (Q165 (a) /
        # Q166 (a)), the leveled toasts and the family's globals in their modules (member aliases); the Rhino Java objects
        # ConsoleConfigurator, the floaty windows, WebSocket and the injectable WebView / client (Q168 (a)) and the compose
        # objects of Q104 / Q106 through their handle types; ui.layoutInflater excluded with the Java prototypes (Q169 (a)).
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, status, module, via, capabilities in [
                ('ui.layout', 'bridged', 'autojs6:ui', 'same-name', None), ('ui.statusBarColor', 'bridged', 'autojs6:ui', 'same-name', None),
                ('ui.emitter', 'bridged', 'autojs6:ui', 'same-name', None), ('floaty.rawWindow', 'bridged', 'autojs6:floaty', 'same-name', None),
                ('web.newWebSocket', 'bridged', 'autojs6:web', 'same-name', None), ('terminal.open', 'bridged', 'autojs6:terminal', 'same-name', ['terminal']),
                ('console.rawInput', 'bridged', 'autojs6:console', 'same-name', ['console']), ('console.build', 'bridged', 'autojs6:console', 'same-name', None),
                ('console.input', 'bridged', 'autojs6:console', 'same-name', None),
                ('toast.dismissAll', 'bridged', 'toast', 'same-name', ['toast']), ('toastlog', 'bridged', 'toast', 'member-alias', None),
                ('isUiThread', 'bridged', 'ui', 'member-alias', None), ('ComposeError', 'bridged', 'autojs6:compose', 'member-alias', None),
                ('newWebSocket', 'bridged', 'autojs6:web', 'member-alias', None),
                ('ConsoleConfigurator#setTitle', 'bridged', 'console', 'handle-type', None), ('FloatyWindow#isAdjustEnabled', 'bridged', 'autojs6:floaty', 'handle-type', None),
                ('RawWindow#setTouchable', 'bridged', 'autojs6:floaty', 'handle-type', None), ('WebSocket#send', 'bridged', 'autojs6:web', 'handle-type', None),
                ('InjectableWebView#inject', 'bridged', 'autojs6:web', 'handle-type', None), ('ComposeNode#parent', 'bridged', 'autojs6:compose', 'handle-type', None),
                ('ComposeNode#w', 'bridged', 'autojs6:compose', 'handle-type', None), ('ComposeSession#showSnackbar', 'bridged', 'autojs6:compose', 'handle-type', None),
                ('ComposeFloatyWindow#setAdjustEnabled', 'bridged', 'autojs6:compose', 'handle-type', None),
                ('ComposeState#value', 'bridged', 'autojs6:compose', 'handle-type', None), ('ComposeRef#current', 'bridged', 'autojs6:compose', 'handle-type', None)]:
            for column in ['node_main', 'node_worker']:
                cell = rows[name][column]
                self.assertEqual((cell['status'], cell['module'], cell['via'], cell.get('capabilities')), (status, module, via, capabilities), (name, column))
        for column in ['node_main', 'node_worker', 'bun']:
            cell = rows['ui.layoutInflater'][column]
            self.assertEqual((cell['status'], cell['reason'], cell['equivalent']), ('not-portable', 'java-prototype', 'ui.inflate / ui.createView'))
        objects = ('ConsoleConfigurator', 'FloatyWindow', 'RawWindow', 'WebSocket', 'InjectableWebView', 'InjectableWebClient',
                   'ComposeNode', 'ComposeSession', 'ComposeFloatyWindow', 'ComposeState', 'ComposeRef')
        family = [row for row in self.matrix['rows'] if (row['module'] or row['ownerModule'] or '').split('.')[0] in
                  ('ui', 'console', 'toast', 'floaty', 'web', 'terminal', 'compose') or row['object'] in objects]
        self.assertGreaterEqual(len(family), 370)
        for row in family:
            for column in ['node_main', 'node_worker']:
                self.assertNotEqual(row[column]['status'], 'pending', (row['id'], column))
        # Q106 (a): the compose objects' members are rows (20 own members of ComposeNode next to its 8 shortcut accessors).
        counts = {name: sum(row['object'] == name for row in family) for name in objects[6:]}
        self.assertEqual(counts, {'ComposeNode': 28, 'ComposeSession': 7, 'ComposeFloatyWindow': 18, 'ComposeState': 1, 'ComposeRef': 1})

    def test_p3_4(self):
        # The automation family: the autojs6: modules of the shared automation factory in both threads (automator with the
        # gesture and service capabilities, auto, threads / continuation / timers local, yolo, selector, qrcode), Rhino's events
        # members on autojs6:events (Q18), keys codes, ocr modes, the global sleep / wait (wait.async through the member alias)
        # and the family's globals in their owner modules (Q177 (a)); VolatileDispose / YoloDetector through their handle types.
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, status, module, via, capabilities in [
                ('automator.click', 'bridged', 'autojs6:automator', 'same-name', None), ('click', 'bridged', 'autojs6:automator', 'owner-module', None),
                ('press', 'bridged', 'autojs6:automator', 'owner-module', ['accessibility', 'accessibility.gesture']),
                ('auto.start', 'bridged', 'autojs6:auto', 'same-name', ['accessibility', 'accessibility.service']),
                ('auto.waitFor', 'bridged', 'autojs6:auto', 'same-name', None), ('auto.enable', 'bridged', 'autojs6:auto', 'same-name', None),
                ('threads.start', 'local-shim', 'autojs6:threads', 'same-name', None), ('sync', 'local-shim', 'autojs6:threads', 'owner-module', None),
                ('continuation.enabled', 'local-shim', 'autojs6:continuation', 'same-name', None),
                ('keepAlive', 'local-shim', 'autojs6:timers', 'owner-module', None), ('timers.setIntervalExt', 'local-shim', 'autojs6:timers', 'same-name', None),
                ('yolo.load', 'bridged', 'autojs6:yolo', 'same-name', ['image', 'yolo']), ('pickup', 'bridged', 'autojs6:selector', 'owner-module', None),
                ('qrcode.detect', 'bridged', 'autojs6:qrcode', 'same-name', None), ('keys.HOME', 'bridged', 'autojs6:keys', 'same-name', None),
                ('ocr.mlkit.detect', 'bridged', 'ocr', 'same-name', None), ('events.emitSticky', 'bridged', 'autojs6:events', 'same-name', None),
                ('events.observeTouch', 'bridged', 'autojs6:events', 'same-name', ['events', 'events.touch']),
                ('wait', 'bridged', 'autojs6:system', 'global-module', None), ('wait.async', 'bridged', 'autojs6:system', 'member-alias', None),
                ('VolatileDispose#blockedGet', 'local-shim', 'autojs6:threads', 'handle-type', None),
                ('YoloDetector#detect', 'bridged', 'autojs6:yolo', 'handle-type', None)]:
            for column in ['node_main', 'node_worker']:
                cell = rows[name][column]
                self.assertEqual((cell['status'], cell['module'], cell['via'], cell.get('capabilities')), (status, module, via, capabilities), (name, column))
        # Rhino's sleep: autojs6:compat on the main thread (the first global module that has it), autojs6:system in Workers.
        self.assertEqual((rows['sleep']['node_main']['module'], rows['sleep']['node_worker']['module']), ('autojs6:compat', 'autojs6:system'))
        family = [row for row in self.matrix['rows'] if (row['module'] or row['ownerModule'] or '').split('.')[0] in
                  ('automator', 'auto', 'threads', 'continuation', 'yolo', 'selector', 'qrcode', 'keys', 'ocr', 'events')
                  or row['object'] in ('VolatileDispose', 'YoloDetector')]
        self.assertGreaterEqual(len(family), 170)
        for row in family:
            for column in ['node_main', 'node_worker']:
                self.assertNotEqual(row[column]['status'], 'pending', (row['id'], column))
        # Q31: Flow#await / Flow#sync stay pending (appendix D).
        for column in ['node_main', 'node_worker']:
            self.assertEqual((rows['Flow#await'][column]['status'], rows['Flow#sync'][column]['status']), ('pending', 'pending'))

    def test_p3_2a(self):
        # The shared JS tools core: local-shim on the main thread and in Workers, global data functions through autojs6:global.
        rows = {row['id']: row for row in self.matrix['rows']}
        for name in ['Mathx.sum', 'util.isEmptyObject', 'util.java.array', 'species.isString', 's13n.point', 'cvt.bytes.strict', 'random', 'isObjectSpecies']:
            for column in ['node_main', 'node_worker']:
                self.assertEqual(rows[name][column]['status'], 'local-shim', (name, column))
        self.assertEqual((rows['Mathx.sum']['node_worker']['module'], rows['random']['node_worker']['module']), ('jsox.mathx', 'autojs6:global'))
        # Q61 (b) came with P3.2b (test_p3_2b); the Java introspection members are excluded by Q118 (a) (D-11).
        self.assertEqual((rows['util.class']['node_main']['status'], rows['util.class']['node_main']['rule']), ('not-portable', 'java-introspection'))

    def test_p3_2b(self):
        # The files and data family: colors / Color, files (with open) and mime in the shared JS tools core, zip, recorder,
        # storages, mediainfo and EpubBook through bridge facades, in both threads; Color / Zip / Storage members through
        # their Node handle types; Q61 (b) libraries and the Worker's axios on autojs6:global.
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, status in [('colors.toInt', 'local-shim'), ('files.isFile', 'local-shim'), ('files.open', 'local-shim'), ('open', 'local-shim'),
                             ('mime.getMediaType', 'local-shim'), ('Color#argb', 'local-shim'), ('zip.unzip', 'bridged'), ('Zip#addFile', 'bridged'),
                             ('recorder.save', 'bridged'), ('storages.names', 'bridged'), ('Storage#size', 'bridged'), ('mediainfo.get', 'bridged'),
                             ('EpubBook#toString', 'bridged'), ('dayjs', 'local-shim'), ('cheerio', 'local-shim'), ('axios', 'local-shim')]:
            for column in ['node_main', 'node_worker']:
                self.assertEqual(rows[name][column]['status'], status, (name, column))
        self.assertEqual({rows[name]['node_worker']['via'] for name in ['Color#argb', 'Zip#addFile', 'Storage#size']}, {'handle-type'})
        self.assertEqual(rows['axios']['node_worker']['module'], 'autojs6:global')
        # Q80 / Q81 / Q82 (a): host bridge modules in P3.5; Q63 (a): obsoleted.
        for name in ['colors.theme.get', 'gallery.pick', 'files.batchRename.plan']:
            self.assertEqual(rows[name]['node_worker']['status'], 'pending', name)
        self.assertEqual(rows['util.toRegularAndCall']['node_main']['status'], 'not-portable')

    def test_p4_2b(self):
        # The Bun files and data family: the shared JS tools core in the client (colors / Color, mime, recorder, the
        # libraries), the Java facade (zip / Zip and the colors members over Java objects) behind java.interop, and the host
        # bridges behind files.control / storage.control / mediainfo.control / epub.control.
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, status, capability in [('colors.toInt', 'local-shim', None), ('Color#argb', 'local-shim', None), ('mime.getMediaType', 'local-shim', None),
                                         ('recorder.save', 'local-shim', None), ('dayjs', 'local-shim', None), ('axios', 'local-shim', None),
                                         ('colors.toColorStateList', 'capability-gated', 'java.interop'), ('zip.unzip', 'capability-gated', 'java.interop'),
                                         ('Zip#addFile', 'capability-gated', 'java.interop'), ('files.isFile', 'capability-gated', 'files.control'),
                                         ('files.open', 'capability-gated', 'files.control'), ('open', 'capability-gated', 'files.control'),
                                         ('storages.names', 'capability-gated', 'storage.control'), ('Storage#size', 'capability-gated', 'storage.control'),
                                         ('mediainfo.get', 'capability-gated', 'mediainfo.control'), ('EpubBook#toString', 'capability-gated', 'epub.control')]:
            self.assertEqual((rows[name]['bun']['status'], rows[name]['bun'].get('capability')), (status, capability), name)
        self.assertEqual({rows[name]['bun']['via'] for name in ['Color#argb', 'Zip#addFile', 'Storage#size']}, {'handle-type'})
        # Q80 / Q81 / Q82 (a): host bridge modules in P4.5; util.class is excluded by Q118 (a).
        for name in ['colors.theme.get', 'gallery.pick', 'files.batchRename.plan']:
            self.assertEqual(rows[name]['bun']['status'], 'pending', name)
        self.assertEqual(rows['util.class']['bun']['status'], 'not-portable')

    def test_p4_3(self):
        # The Bun UI family: the shared UI facade of the client with the content view and window members behind ui.layout
        # (Q164 (a)), floaty behind floaty.control (rawWindow floaty.raw, getClip clipboard.control, Q173 (a)), terminal behind
        # terminal.control (Q3 (a)), the injectable WebView and client behind ui.layout and newWebSocket on Bun's WebSocket
        # (Q174 (a)), console.build / rawInput behind console.control, ComposeError and ComposeNode's accessors (Q104) behind
        # ui.compose; the facade's handles (Q106 (a) / Q166 (a) / Q168 (a)) through their Bun handle types.
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, status, capability in [('ui.layout', 'capability-gated', 'ui.layout'), ('ui.statusBarColor', 'capability-gated', 'ui.layout'),
                                         ('ui.bindingContext', 'capability-gated', 'ui.layout'), ('floaty.window', 'capability-gated', 'floaty.control'),
                                         ('floaty.rawWindow', 'capability-gated', 'floaty.raw'), ('floaty.getClip', 'capability-gated', 'clipboard.control'),
                                         ('terminal.exec', 'capability-gated', 'terminal.control'), ('terminal.npm', 'capability-gated', 'terminal.control'),
                                         ('web.newInjectableWebView', 'capability-gated', 'ui.layout'), ('newInjectableWebClient', 'capability-gated', 'ui.layout'),
                                         ('web.newWebSocket', 'local-shim', None), ('newWebSocket', 'local-shim', None), ('web.ByteString', 'local-shim', None),
                                         ('console.build', 'capability-gated', 'console.control'), ('console.input', 'capability-gated', 'console.control'),
                                         ('ComposeError', 'capability-gated', 'ui.compose'), ('ComposeNode#w', 'capability-gated', 'ui.compose'),
                                         ('ComposeNode#append', 'capability-gated', 'ui.compose'), ('ComposeSession#update', 'capability-gated', 'ui.compose'),
                                         ('ConsoleConfigurator#setTitle', 'capability-gated', 'console.control'),
                                         ('FloatyWindow#setPosition', 'capability-gated', 'floaty.control'), ('RawWindow#setTouchable', 'capability-gated', 'floaty.raw'),
                                         ('WebSocket#send', 'local-shim', None), ('InjectableWebClient#injectAndWait', 'capability-gated', 'ui.layout')]:
            self.assertEqual((rows[name]['bun']['status'], rows[name]['bun'].get('capability')), (status, capability), name)
        self.assertEqual({rows[name]['bun']['via'] for name in ['ComposeNode#w', 'ComposeSession#update', 'FloatyWindow#setPosition', 'WebSocket#send']},
                         {'handle-type'})
        # ui.layoutInflater is Rhino's Java LayoutInflater (Q169 (a)).
        self.assertEqual(rows['ui.layoutInflater']['bun']['status'], 'not-portable')
        handles = ('ComposeNode', 'ComposeSession', 'ComposeFloatyWindow', 'ComposeState', 'ComposeRef', 'ConsoleConfigurator', 'FloatyWindow',
                   'RawWindow', 'WebSocket', 'InjectableWebView', 'InjectableWebClient')
        family = [row for row in self.matrix['rows'] if (row['module'] or row['ownerModule'] or '').split('.')[0] in
                  ('ui', 'floaty', 'web', 'terminal', 'console', 'toast', 'compose') or row['object'] in handles]
        self.assertGreaterEqual(len(family), 370)
        for row in family:
            self.assertNotEqual(row['bun']['status'], 'pending', row['id'])

    def test_p4_4(self):
        # The Bun automation family: Node's shared automation factory in the client, automator / auto as second modules of
        # accessibility.control (Q183 (a)), threads / continuation / timers / keys local, yolo / ocr / barcode / qrcode behind
        # yolo.control / ocr.control / barcode.control (Q181 (a)), the selector functions over flow's wait (accessibility.control),
        # Rhino's events members behind the events grants (Q182 (a)), the family's globals on host.global (Q184 (a)) and the
        # VolatileDispose / YoloDetector handles; Rhino's timers.setTimeout and the five others are Bun's own timers (D-9).
        rows = {row['id']: row for row in self.matrix['rows']}
        for name, status, capability in [('automator.click', 'capability-gated', 'accessibility.control'), ('click', 'capability-gated', 'accessibility.control'),
                                         ('automator.captureScreen', 'capability-gated', 'accessibility.control'),
                                         ('auto.start', 'capability-gated', 'accessibility.control'), ('auto.dump', 'capability-gated', 'accessibility.control'),
                                         ('threads.start', 'local-shim', None), ('sync', 'local-shim', None), ('continuation.enabled', 'local-shim', None),
                                         ('keepAlive', 'local-shim', None), ('timers.setIntervalExt', 'local-shim', None), ('keys.HOME', 'local-shim', None),
                                         ('timers.addDailyTask', 'capability-gated', 'tasks.control'), ('yolo.load', 'capability-gated', 'yolo.control'),
                                         ('ocr.mlkit.detect', 'capability-gated', 'ocr.control'), ('barcode.detect', 'capability-gated', 'barcode.control'),
                                         ('qrcode.detect', 'capability-gated', 'barcode.control'), ('pickup', 'capability-gated', 'accessibility.control'),
                                         ('events.emitSticky', 'capability-gated', 'events.observe'), ('events.onKeyDown', 'capability-gated', 'events.key'),
                                         ('events.onTouch', 'capability-gated', 'events.touch'),
                                         ('events.setKeyInterceptionEnabled', 'capability-gated', 'events.key.intercept'),
                                         ('sleep', 'local-shim', None), ('wait', 'local-shim', None), ('wait.async', 'local-shim', None),
                                         ('VolatileDispose#blockedGet', 'local-shim', None), ('YoloDetector#detect', 'capability-gated', 'yolo.control')]:
            self.assertEqual((rows[name]['bun']['status'], rows[name]['bun'].get('capability')), (status, capability), name)
        self.assertEqual({rows[name]['bun']['via'] for name in ['VolatileDispose#blockedGet', 'YoloDetector#detect']}, {'handle-type'})
        self.assertEqual((rows['click']['bun']['namespace'], rows['sleep']['bun']['namespace']), ('automator', 'global'))
        self.assertEqual((rows['timers.setTimeout']['bun']['status'], rows['timers.setTimeout']['bun']['rule']), ('not-portable', 'native-timers'))
        family = [row for row in self.matrix['rows'] if (row['module'] or row['ownerModule'] or '').split('.')[0] in
                  ('automator', 'auto', 'threads', 'continuation', 'timers', 'yolo', 'selector', 'qrcode', 'barcode', 'keys', 'ocr', 'events')
                  or row['object'] in ('VolatileDispose', 'YoloDetector')]
        self.assertGreaterEqual(len(family), 240)
        for row in family:
            self.assertNotEqual(row['bun']['status'], 'pending', row['id'])
        # Q31: Flow#await / Flow#sync stay pending (appendix D).
        self.assertEqual((rows['Flow#await']['bun']['status'], rows['Flow#sync']['bun']['status']), ('pending', 'pending'))

    def test_p5_2f1(self):
        # Q122 - Q124: the host imgui module is in the denominator, pending in every runtime column except the internal __pluginId.
        rows = {row['id']: row for row in self.matrix['rows'] if row['kind'] == 'export' and row['module'].split('.')[0] == 'imgui'}
        if not rows:
            self.skipTest('the imgui installs come from the KSP metadata')
        self.assertEqual(rows['imgui.__pluginId']['node_main'].get('rule'), 'internal-exports')
        public = [row for name, row in rows.items() if name != 'imgui.__pluginId']
        for column in ['node_main', 'node_worker', 'bun']:
            self.assertEqual({row[column]['status'] for row in public}, {'pending'}, column)
        self.assertEqual(self.matrix['summary']['deferredNameGate']['imgui']['rows'], len(rows))

    def test_p2_3(self):
        # Q18 (a): every @ScriptPrototype module is listed, its members are rows like exports, Java internals are excluded.
        self.assertEqual(self.exports['prototypesStats']['problems'], [])
        rows = {row['id']: row for row in self.matrix['rows']}
        self.assertEqual((rows['files.isFile']['kind'], rows['files.isFile']['memberSource']), ('prototype-member', 'script-prototype'))
        self.assertEqual(rows['files.isFile']['node_main']['status'], 'local-shim')
        self.assertNotIn('files.context', rows)
        self.assertEqual(rows['crypto.digest']['bun']['rule'], 'native-crypto')
        self.assertEqual(self.matrix['summary']['prototypeMembers'], sum(len(p['members']) for p in self.exports['prototypes']))

    def test_except_leaves_rows_of_a_broad_rule_out(self):
        rows = [{'id': f'{name}#x', 'memberSource': 'kotlin-class', **{column: {'status': status} for column in runtime_matrix.RUNTIME_COLUMNS}}
                for name, status in (('Handle', 'bridged'), ('Plain', 'pending'))]
        rule = {'id': 'objects', 'match': ['*#*'], 'memberSource': 'kotlin-class', 'status': 'not-portable', 'reason': 'java-prototype'}
        self.assertEqual({item['kind'] for item in runtime_matrix.apply_exclusions(rows, [rule])}, {'shadowed'})
        self.assertEqual(runtime_matrix.apply_exclusions(rows, [dict(rule, id='narrow', **{'except': ['Handle#*']})]), [])

if __name__ == '__main__':
    unittest.main()
