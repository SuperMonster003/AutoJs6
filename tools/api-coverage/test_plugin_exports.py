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


if __name__ == '__main__':
    unittest.main()
