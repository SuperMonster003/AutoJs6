import json
from pathlib import Path
import shutil
import tempfile
import unittest

import plugin_exports
from returned_objects import attach as attach_returned
import runtime_matrix

HOST = Path(__file__).resolve().parents[2]
CONTRACT = 'plugin-api/compose-ui-api/src/main/java/org/autojs/plugin/compose/api'
POLICY = {'pluginExports': {'compose': {'kind': 'compose-ui-catalog', 'module': 'compose', 'root': CONTRACT}}}
DTS = '''declare namespace Sample {
    type Listener = (event: { readonly name: string }) => void;
    /** A comment does not declare fake(): void; */
    interface Base<Options extends object = object> {
        /** The id. */
        readonly id: string;
        visible: boolean;
        close(reason?: string): this;
    }
    interface Extra { extra?: number; readonly other: "a;b{c"; }
    interface Panel<Row = any, Handler extends (value: Row) => void = () => void> extends Base<{ size: number }>, Extra, Promise<Row> {
        close(): this;
        on<Name extends "a" | "b">(name: Name, listener: Listener): this;
        on(name: "c"): this;
        snapshot(): {
            /** Inside a return type: not a member of Panel. */
            readonly nested: number;
        };
        render(
            first: number,
            inner: { skipped: number },
        ): void;
    }
}
'''


def copy_contract(target):
    for name in plugin_exports.COMPOSE_FILES.values():
        (target / name).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(HOST / CONTRACT / name, target / name)


class ComposeCatalogueTests(unittest.TestCase):
    """The Compose catalogue the compose module installs (P5.2c, appendix C Q75 (a))."""

    def test_node_components_modifiers_and_shortcuts(self):
        catalogue = plugin_exports.read_compose(HOST / CONTRACT)
        self.assertEqual([], catalogue['problems'])
        names = [item[0] for item in catalogue['components']]
        # V1 29 node factories (Snackbar is a session command), V2 25, the negotiated AndroidView.
        self.assertEqual((55, 20, 8), (len(names), len(catalogue['modifiers']), len(catalogue['shortcuts'])))
        self.assertEqual(['Snackbar'], catalogue['commandComponents'])
        self.assertNotIn('Snackbar', names)
        self.assertTrue({'Column', 'Text', 'AlertDialog', 'NavigationBar', 'PullToRefresh', 'AndroidView'} <= set(names))
        self.assertEqual(['w', 'h', 'padding', 'bg', 'weight', 'alpha', 'testTag', 'contentDescription'], [item[0] for item in catalogue['shortcuts']])
        self.assertEqual('padding', catalogue['modifiers'][0][0])
        self.assertEqual('semantics', catalogue['modifiers'][-1][0])

    def test_vocabulary_constant_outside_the_catalogue_is_a_problem(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_contract(root)
            path = root / plugin_exports.COMPOSE_FILES['v1']
            text = path.read_text(encoding='utf-8')
            path.write_text(text.replace('components += node(C.HORIZONTAL_DIVIDER,', 'components += node("HorizontalDivider",', 1), encoding='utf-8')
            catalogue = plugin_exports.read_compose(root)
            self.assertIn('ComposeUiComponents.HorizontalDivider: constant not in the catalogue function', catalogue['problems'])
            self.assertNotIn('HorizontalDivider', [item[0] for item in catalogue['components']])

    def test_records_members_and_skipped_source(self):
        result = {'records': [{'api': 'compose.modifier', 'module': 'compose', 'ownerModule': 'compose', 'member': 'modifier'}],
                  'dataSource': {'kind': 'ksp'}}
        plugin_exports.attach(HOST, result, POLICY)
        compose = result['pluginExports']['compose']
        self.assertEqual('read', compose['status'])
        self.assertEqual({'components': 55, 'modifiers': 20, 'shortcuts': 8}, compose['counts'])
        button = next(record for record in result['records'] if record['api'] == 'compose.Button')
        self.assertEqual(('compose', 'Functions', 'pluginExports', 'plugin:compose'), (button['module'], button['kind'], button['registration'], button['metadata']))
        self.assertTrue(button['source'].endswith('catalog/V1Catalog.kt'))
        self.assertEqual(56, len(result['records']))
        self.assertNotIn('pluginExportsSkipped', result['dataSource'])
        missing = {'records': [], 'dataSource': {'kind': 'ksp'}}
        plugin_exports.attach(HOST, missing, {'pluginExports': {'compose': dict(POLICY['pluginExports']['compose'], root='no/such/contract')}})
        self.assertEqual('skipped', missing['pluginExports']['compose']['status'])
        self.assertEqual([], missing['records'])
        self.assertEqual(['compose'], missing['dataSource']['pluginExportsSkipped'])

    def test_returned_objects_from_the_catalogue(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / 'returned-objects.json'
            config.write_text(json.dumps({'objects': [
                {'object': 'ComposeModifier', 'class': 'x.ComposeModifier', 'memberSource': 'plugin:compose', 'catalog': 'modifiers', 'producers': ['compose.modifier']},
                {'object': 'ComposeNode', 'class': 'x.ComposeNode', 'memberSource': 'plugin:compose', 'catalog': 'shortcuts', 'catalogProducers': True},
                {'object': 'Unknown', 'class': 'x.Unknown', 'memberSource': 'plugin:compose', 'catalog': 'events'},
            ]}), encoding='utf-8')
            result = {'modules': [], 'records': [{'api': 'compose.modifier', 'module': 'compose', 'ownerModule': 'compose', 'member': 'modifier'}],
                      'dataSource': {'kind': 'ksp'}}
            plugin_exports.attach(HOST, result, POLICY)
            attach_returned(HOST, result, None, config)
            objects = {o['object']: o for o in result['returnedObjects']}
            self.assertEqual(20, len(objects['ComposeModifier']['members']))
            self.assertEqual(['compose.modifier'], objects['ComposeModifier']['producers'])
            self.assertEqual(55, len(objects['ComposeNode']['producers']))
            self.assertEqual('GettersAndSetters', objects['ComposeNode']['members'][0]['kind'])
            self.assertEqual(['Unknown: catalog events is not a list of plugin:compose'], result['returnedObjectsStats']['problems'])
            skipped = {'modules': [], 'records': [], 'dataSource': {'kind': 'ksp'},
                       'pluginExports': {'compose': {'status': 'skipped'}}}
            attach_returned(HOST, skipped, None, config)
            self.assertEqual([], skipped['returnedObjects'])
            self.assertEqual(['ComposeModifier', 'ComposeNode', 'Unknown'], skipped['returnedObjectsStats']['skipped'])

    def test_matrix_and_baseline_skip_without_the_contract(self):
        matrix = {'dataSource': {'kind': 'ksp', 'pluginExportsSkipped': ['compose']}, 'rows': [],
                  'summary': {'byColumn': {'doc': {}, 'dts': {}, 'ace': {}}}}
        with tempfile.TemporaryDirectory() as directory:
            markdown = Path(directory) / 'MATRIX.md'
            markdown.write_text('<!-- runtime-matrix: schema=autojs6-runtime-matrix-v1 dataSource=ksp rows=0 -->\n', encoding='utf-8')
            self.assertEqual('skipped', runtime_matrix.compare_markdown(matrix, markdown))
            baseline = Path(directory) / 'pending-baseline.json'
            baseline.write_text(json.dumps({'schema': runtime_matrix.BASELINE_SCHEMA, 'dataSource': 'ksp', 'pending': {}}), encoding='utf-8')
            self.assertEqual(('skipped', [], []), runtime_matrix.compare_pending_baseline(matrix, baseline))
            with self.assertRaises(ValueError):
                runtime_matrix.write_pending_baseline(matrix, baseline, allow_new=True)


class DtsInterfaceTests(unittest.TestCase):
    """Members of the interfaces of a plugin's d.ts, for the objects its JavaScript builds (P5.2f-2 card D, Q217 (a))."""

    @staticmethod
    def write_config(directory, root):
        config = Path(directory) / 'returned-objects.json'
        config.write_text(json.dumps({'objects': [
            {'object': 'SamplePanel', 'class': 'Sample.Panel', 'memberSource': 'plugin:sample', 'catalog': 'Panel', 'producers': ['sample.open']},
        ]}), encoding='utf-8')
        return config, {'pluginExports': {'sample': {'kind': 'dts-interfaces', 'plugin': 'AutoJs6-Plugin-Sample', 'file': 'sample.d.ts', 'root': root}}}

    def test_members_of_an_interface_and_its_bases(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / 'sample.d.ts').write_text(DTS, encoding='utf-8')
            config, policy = self.write_config(directory, directory)
            result = {'modules': [], 'records': [{'api': 'sample.open', 'module': 'sample', 'ownerModule': 'sample', 'member': 'open'}],
                      'dataSource': {'kind': 'ksp'}}
            plugin_exports.attach(HOST, result, policy)
            sample = result['pluginExports']['sample']
            self.assertEqual(('read', [], []), (sample['status'], sample['records'], sample['problems']))
            self.assertEqual({'interfaces': 3, 'members': 13}, sample['counts'])
            self.assertEqual({'Panel': ['Promise']}, sample['externalBases'])
            # close hides the member of Base, on is one member for two overloads, nested object types are not members.
            members = {member['name']: member for member in sample['members']['Panel']}
            self.assertEqual(['close', 'on', 'snapshot', 'render', 'id', 'visible', 'extra', 'other'], list(members))
            self.assertEqual(('Functions', 'Functions', 'Getters', 'GettersAndSetters'),
                             tuple(members[name]['kind'] for name in ('close', 'on', 'id', 'visible')))
            self.assertEqual((None, 'Base', 'Extra'), tuple(members[name].get('inheritedFrom') for name in ('close', 'id', 'extra')))
            self.assertTrue(members['id']['source'].endswith('sample.d.ts'))
            attach_returned(HOST, result, None, config)
            panel = result['returnedObjects'][0]
            self.assertEqual(('SamplePanel', ['sample.open'], True, 8), (panel['object'], panel['producers'], panel['complete'], len(panel['members'])))

    def test_source_is_skipped_without_the_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            config, policy = self.write_config(directory, str(Path(directory) / 'no-such-checkout'))
            result = {'modules': [], 'records': [], 'dataSource': {'kind': 'ksp'}}
            plugin_exports.attach(HOST, result, policy)
            self.assertEqual('skipped', result['pluginExports']['sample']['status'])
            self.assertEqual(['sample'], result['dataSource']['pluginExportsSkipped'])
            attach_returned(HOST, result, None, config)
            self.assertEqual([], result['returnedObjects'])
            self.assertEqual(['SamplePanel'], result['returnedObjectsStats']['skipped'])
            self.assertEqual([], result['returnedObjectsStats']['problems'])


if __name__ == '__main__':
    unittest.main()
