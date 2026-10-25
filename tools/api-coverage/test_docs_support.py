import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import docs_support

HOST = Path(__file__).resolve().parents[2]
DOCS = HOST.parent / 'AutoJs6-Documentation'
NODE = shutil.which('node')


def row(api, module, owner, cells=('bridged', 'bridged', 'capability-gated'), kind='export'):
    return {'id': api, 'kind': kind, 'module': module, 'member': api.split('.')[-1], 'ownerModule': owner, 'aliases': [], 'rhino': 'native',
            'node_main': {'status': cells[0], 'module': 'autojs6:' + (owner or 'x'), 'member': api.split('.')[-1], 'via': 'same-name'},
            'node_worker': {'status': cells[1], 'module': 'autojs6:' + (owner or 'x'), 'member': api.split('.')[-1], 'via': 'same-name'},
            'bun': {'status': cells[2], 'namespace': owner, 'member': api.split('.')[-1], 'capability': 'demo.control', 'via': 'same-name'}}


MATRIX = {'schema': 'autojs6-runtime-matrix-v1', 'dataSource': {'kind': 'ksp'}, 'rows': [
    row('demo.read', 'demo', 'demo'), row('demo.sub.write', 'demo.sub', 'demo'), row('capture', '', 'demo'),
    row('toast', '', ''), row('Thing#size', None, None, kind='returned-member'),
]}

STUB = '''
const report = {matrix: process.argv[5], dataSource: "ksp", mode: "check", problems: [], uncovered: ["global"],
  pages: [{page: "api/demo.md", status: "current", roots: ["demo"], rows: Number(process.env.STUB_ROWS), differences: 0, line: 3}]};
if (process.env.STUB_EXIT) { console.error("boom"); process.exit(Number(process.env.STUB_EXIT)); }
console.log(JSON.stringify(report));
'''


class StripBlocksTest(unittest.TestCase):
    def test_blanks_block_lines_and_keeps_numbering(self):
        text = '# Demo\r\n<!-- runtime-support:begin demo -->\r\n| `demo.read` |\r\n<!-- runtime-support:end -->\r\ndemo.read\r\n'
        stripped = docs_support.strip_blocks(text)
        self.assertEqual(stripped.split('\n'), ['# Demo\r', '', '', '', 'demo.read\r', ''])

    def test_text_without_block_and_lookalikes_unchanged(self):
        text = '# Demo\n<!-- runtime-support -->\n`<!-- runtime-support:begin demo -->` in code\n'
        self.assertEqual(docs_support.strip_blocks(text), text)

    def test_root_counts_match_documentation_roots(self):
        self.assertEqual(docs_support.root_counts(MATRIX), {'demo': 3, 'global': 1})

    def test_documentation_gate_ignores_the_tables(self):
        from check import documentation_rows
        with tempfile.TemporaryDirectory() as directory:
            api = Path(directory)
            (api / 'demo.md').write_text('# Demo\n\n<!-- runtime-support:begin demo -->\n| `demo.write` |\n<!-- runtime-support:end -->\n\n## demo.read()\n',
                                         encoding='utf-8')
            exports = [{'api': name, 'module': 'demo', 'ownerModule': 'demo', 'member': name.split('.')[1]} for name in ('demo.read', 'demo.write')]
            rows = documentation_rows(exports, api, {'documentationModules': {}})
        self.assertEqual([(row['api'], row['status']) for row in rows], [('demo.read', 'heading'), ('demo.write', 'missing')])
        self.assertEqual(rows[0]['evidence'], [{'page': 'api/demo.md', 'line': 7, 'heading': True}])


@unittest.skipUnless(NODE, 'needs Node.js')
class CheckTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        (self.root / 'api').mkdir()
        self.matrix = self.root / 'runtime-matrix.json'
        self.matrix.write_text(json.dumps(MATRIX), encoding='utf-8')

    def stub(self, rows, exit_code=None, **env):
        (self.root / 'generator').mkdir(exist_ok=True)
        (self.root / 'generator' / 'runtime-support.js').write_text(STUB, encoding='utf-8')
        os.environ['STUB_ROWS'] = str(rows)
        os.environ.pop('STUB_EXIT', None)
        if exit_code is not None:
            os.environ['STUB_EXIT'] = str(exit_code)
        self.addCleanup(os.environ.pop, 'STUB_ROWS', None)
        self.addCleanup(os.environ.pop, 'STUB_EXIT', None)

    def test_missing_generator_fails(self):
        summary, failures = docs_support.check(self.root, self.matrix)
        self.assertEqual(summary, {'generator': None})
        self.assertIn('generator not found', failures[0])

    def test_row_counts_cross_checked(self):
        self.stub(3)
        summary, failures = docs_support.check(self.root, self.matrix, output=self.root)
        self.assertEqual(failures, [])
        self.assertEqual((summary['pages'], summary['rows'], summary['coveredRoots'], summary['uncoveredRoots']), (1, 3, 1, 1))
        self.assertTrue((self.root / 'docs-support.json').is_file())
        self.stub(2)
        _, failures = docs_support.check(self.root, self.matrix)
        self.assertEqual(failures, ['Documentation runtime support: api/demo.md shows 2 rows, the matrix has 3 for demo'])

    def test_generator_error_fails(self):
        self.stub(3, exit_code=2)
        _, failures = docs_support.check(self.root, self.matrix)
        self.assertIn('failed (2): boom', failures[0])

    @unittest.skipUnless((DOCS / docs_support.GENERATOR).is_file(), 'needs the AutoJs6-Documentation checkout')
    def test_real_generator_current_then_stale(self):
        (self.root / 'generator').mkdir()
        shutil.copy(DOCS / docs_support.GENERATOR, self.root / docs_support.GENERATOR)
        page = self.root / 'api' / 'demo.md'
        page.write_text('# Demo\n\n<!-- runtime-support:begin demo -->\n<!-- runtime-support:end -->\n\nIntro.\n', encoding='utf-8')
        _, failures = docs_support.check(self.root, self.matrix)
        self.assertEqual(len(failures), 1)
        self.assertIn('api/demo.md: runtime-support block differs from the matrix', failures[0])
        subprocess.run([NODE, str(self.root / docs_support.GENERATOR), '--matrix', str(self.matrix), '--api', str(self.root / 'api')], check=True, capture_output=True)
        summary, failures = docs_support.check(self.root, self.matrix, require_all=True)
        self.assertEqual(failures, ['Documentation runtime support: module roots without a runtime-support block: global'])
        self.assertEqual((summary['byStatus'], summary['rows']), ({'current': 1}, 3))
        text = page.read_text(encoding='utf-8')
        self.assertIn('`demo.sub`', text)
        stripped = docs_support.strip_blocks(text).splitlines()
        self.assertEqual(len(stripped), len(text.splitlines()))
        self.assertEqual([line for line in stripped if line], ['# Demo', 'Intro.'])


if __name__ == '__main__':
    unittest.main()
