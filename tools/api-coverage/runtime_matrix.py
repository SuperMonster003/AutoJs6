#!/usr/bin/env python3
"""Generate the runtime coverage matrix (api-coverage roadmap P0.2, definition in ROADMAP section 4).

One row per explicit export of exports.json (plus returned-object members once
exports.json has a returnedObjects list), with the documentation, declaration
and Ace states of check.py's reports and a state per runtime column: Node main
thread, Node Worker and Bun. Runtime entries are read from the runtime
repositories (Node declarations, HOST-API status tables and the Worker module
table of the embedded facade; the Bun host client declarations and its
capability method table); manual annotations come only from runtime-policy.json.
Outputs: build/reports/api-coverage/runtime-matrix.json and docs/api-coverage/MATRIX.md.

This is a declared-entry matrix, not a behavior proof: an entry counts when the
runtime declares it, Rhino-equivalent behavior is reviewed separately (P1).
"""
import argparse
from collections import Counter, defaultdict
from fnmatch import fnmatchcase
import json
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
SCHEMA = 'autojs6-runtime-matrix-v1'
POLICY_SCHEMA = 'autojs6-runtime-policy-v1'
COLUMNS = ['rhino', 'doc', 'dts', 'ace', 'node_main', 'node_worker', 'bun']
RUNTIME_COLUMNS = ['node_main', 'node_worker', 'bun']
STATUSES = ['bridged', 'local-shim', 'capability-gated', 'not-portable', 'denied', 'pending']
COVERED = {'bridged', 'local-shim', 'capability-gated'}
EXCLUDED = {'not-portable', 'denied'}
REASONS = ['security', 'sync-only', 'java-prototype', 'runtime-native', 'runtime-builtin', 'rhino-only', 'ui-thread']
DOC_VALUES = ['none', 'mention', 'heading', 'signature', 'example']
DTS_VALUES = ['none', 'name', 'arity', 'typed']
ACE_VALUES = ['none', 'semantic', 'static']
MARKDOWN = 'docs/api-coverage/MATRIX.md'
REPORTS = 'build/reports/api-coverage'
NODE_DECLARATIONS = 'docs/nodejs/types/autojs6-node'
NODE_HOST_API = 'docs/HOST-API.md'
NODE_FACADE = 'app/src/main/cpp/node_bridge_sources.cpp'
NODE_CATALOG = 'app/src/main/assets/nodejs/node-capability-catalog.json'
BUN_DECLARATIONS = 'app/src/main/assets/autojs6/host-client.d.mts'
CANONICAL = 'autojs6:'
COLUMN_TITLES = {'node_main': 'Node main', 'node_worker': 'Node Worker', 'bun': 'Bun'}


def bare(name):
    return name[len(CANONICAL):] if name.startswith(CANONICAL) else name


def parse_host_api(text):
    """Module statuses of Node docs/HOST-API.md: the tables of sections 1-3 and the local-shim list of section 4."""
    statuses = {}
    section = None
    for line in text.splitlines():
        heading = re.match(r'^##\s+(\S+?)\.\s*(.*)$', line)
        if line.startswith('## '):
            section = None
            if heading and heading.group(1) in ('一', '二', '三'):
                token = re.findall(r'\(([a-z][a-z-]*)\)', heading.group(2))
                section = token[-1] if token else 'bridged'
            elif heading and heading.group(1) == '四':
                section = 'local-shim'
            continue
        if section == 'local-shim':
            if '由插件本地实现' in line:
                for name in re.findall(r'`([^`]+)`', line.split('由插件本地实现')[0]):
                    statuses.setdefault(bare(name), 'local-shim')
                section = None
        elif section and line.startswith('|') and not re.match(r'^\|\s*(模块|项|---)', line):
            first = line.split('|')[1]
            for name in re.findall(r'`([^`]+)`', first):
                statuses.setdefault(bare(name), section)
    return statuses


def parse_worker_modules(source):
    """Worker host modules of the embedded facade: name -> None (all main-thread members) or an explicit member list."""
    start = source.find('hostModuleMethods: {')
    if start < 0:
        raise ValueError('Worker hostModuleMethods table not found in the Node facade')
    end = source.find('\n          },', start)
    body = source[start + len('hostModuleMethods: {'):end]
    modules = {}
    for name, value in re.findall(r'(\w+)\s*:\s*(Object\.keys\(|\[[^\]]*\])', body):
        modules[name] = None if value.startswith('Object.keys') else re.findall(r'"([^"]+)"', value)
    if not modules:
        raise ValueError('Worker hostModuleMethods table is empty or changed shape')
    return modules


def catalog_capabilities(catalog):
    result = defaultdict(set)
    for item in catalog.get('bridge', {}).get('operations', []):
        result[(item['module'], item['method'])].update(item.get('requiredCapabilities', []))
    return result


def list_surfaces(node, bun, typescript, output):
    """Run list-runtime-surfaces.mjs (TypeScript checker over the runtime declarations)."""
    command = ['node', str(HERE / 'list-runtime-surfaces.mjs'), '--node', str(node), '--bun', str(bun),
               '--typescript', str(typescript), '--output', str(output)]
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)
    return json.loads(Path(output).read_text(encoding='utf-8'))


def git_commit(root):
    try:
        result = subprocess.run(['git', '-C', str(root), 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True, check=False)
    except OSError:
        return None
    return result.stdout.strip() or None


def placement(row):
    """Runtime module and member path for a row: a global-object row (`wait.async`) is looked up in its owner module."""
    if row['module']:
        return row['module'], row['member'], None
    return row['ownerModule'], row['id'], 'owner-module'


class NodeIndex:
    """Node main-thread / Worker entry lookup over the declared modules."""

    def __init__(self, surfaces, policy, host_api, worker, catalog=None):
        self.modules = surfaces['modules']
        self.explicit = set(surfaces.get('explicitGlobal', {}).values())
        self.policy = policy
        self.host_api = host_api
        self.worker = worker
        self.catalog = catalog or {}
        self.native = policy.get('nativeModules', {})

    def candidates(self, base):
        names = [CANONICAL + base, base]
        for alias in self.policy.get('moduleAliases', {}).get(base, []):
            names += [CANONICAL + alias, alias]
        return [(name, 'same-name' if i < 2 else 'module-alias') for i, name in enumerate(names)]

    def available(self, name, path, worker):
        module = self.modules.get(name)
        if module is None or path not in module['members']:
            return False
        if not worker or bare(name) in self.native:
            return True
        key = 'console' if name == 'node:console' else bare(name)
        if key not in self.worker:
            return False
        members = self.worker[key]
        return members is None or path.split('.')[0] in members

    def find(self, host_module, member, worker):
        parts = host_module.split('.') if host_module else []
        for k in range(len(parts), 0, -1):
            path = '.'.join(parts[k:] + [member])
            for name, via in self.candidates('.'.join(parts[:k])):
                if self.available(name, path, worker):
                    return name, path, via
        return None

    def status(self, name):
        module = bare(name)
        status = self.host_api.get(module) or self.host_api.get(module.split('.')[0])
        return ('local-shim' if status == 'local-shim' else 'bridged'), status

    def cell(self, name, path, via):
        if not name.startswith(CANONICAL) and name in self.native:
            return {'status': 'not-portable', 'reason': 'runtime-native', 'equivalent': f'{self.native[name]}.{path}', 'module': name, 'member': path, 'via': via}
        status, host_api = self.status(name)
        info = self.modules[name]['members'][path]
        cell = {'status': status, 'module': name, 'member': path, 'async': info['async'], 'via': via}
        if host_api:
            cell['hostApi'] = host_api
        capabilities = sorted(self.catalog.get((bare(name), path.split('.')[0]), ()))
        if capabilities:
            cell['capabilities'] = capabilities
        if bare(name) in self.explicit:
            cell['explicitGlobal'] = '$autojs.' + bare(name)
        return cell

    def lookup(self, row, group, worker=False):
        alias = self.policy.get('memberAliases', {}).get(row['id'])
        if alias:
            if self.available(alias['module'], alias['member'], worker):
                return self.cell(alias['module'], alias['member'], 'member-alias')
            return None
        for candidate, via in [(row, None)] + [(other, 'alias') for other in group]:
            module, path, own = placement(candidate)
            if module:
                found = self.find(module, path, worker)
                if found:
                    return self.cell(found[0], found[1], via or (own if own and found[2] == 'same-name' else found[2]))
        if not row['module']:
            for name in self.policy.get('globalModules', []):
                if self.available(name, row['member'], worker):
                    return self.cell(name, row['member'], 'global-module')
        compat = self.policy.get('compatModule')
        if compat:
            path = (row['module'] + '.' if row['module'] else '') + row['member']
            if self.available(compat, path, worker):
                return self.cell(compat, path, 'compat')
        return None


class BunIndex:
    """Bun host client entry lookup: HostClient namespaces and client members."""

    def __init__(self, surfaces, policy):
        self.namespaces = surfaces['namespaces']
        self.client = surfaces.get('clientMembers', {})
        self.methods = surfaces.get('methods', {})
        self.capabilities = surfaces.get('capabilities', [])
        self.policy = policy

    def related(self, namespace):
        related = self.policy.get('namespaceCapabilities', {}).get(namespace)
        if related is None:
            prefixes = {namespace, namespace.rstrip('s')}
            related = [name for name in self.capabilities if name.split('.')[0] in prefixes]
        return related

    def capability(self, namespace, path):
        """The grant of a namespace member: a related capability whose method table has it, else the only related one."""
        related = self.related(namespace)
        owners = [name for name in related if path.split('.')[0] in self.methods.get(name, ())]
        return owners[0] if owners else related[0] if len(related) == 1 else None

    def cell(self, namespace, path, via):
        """Client members are capability-gated; a namespace without any related capability holds client-side helpers."""
        info = self.namespaces[namespace]['members'][path] if namespace else self.client[path]
        status = 'capability-gated' if not namespace or self.related(namespace) else 'local-shim'
        cell = {'status': status, 'namespace': namespace, 'member': path, 'async': info['async'], 'via': via}
        capability = self.capability(namespace, path) if namespace else None
        if capability:
            cell['capability'] = capability
        return cell

    def find(self, host_module, member):
        parts = host_module.split('.')
        for k in range(len(parts), 0, -1):
            base = '.'.join(parts[:k])
            path = '.'.join(parts[k:] + [member])
            names = [(base, 'same-name')] + [(alias, 'module-alias') for alias in self.policy.get('namespaceAliases', {}).get(base, [])]
            for name, via in names:
                if path in self.namespaces.get(name, {}).get('members', {}):
                    return name, path, via
        return None

    def lookup(self, row, group):
        alias = self.policy.get('memberAliases', {}).get(row['id'])
        if alias:
            namespace, path = alias['namespace'], alias['member']
            known = path in self.namespaces.get(namespace, {}).get('members', {}) if namespace else path in self.client
            return self.cell(namespace, path, 'member-alias') if known else None
        for candidate, via in [(row, None)] + [(other, 'alias') for other in group]:
            module, path, own = placement(candidate)
            if module:
                found = self.find(module, path)
                if found:
                    return self.cell(found[0], found[1], via or (own if own and found[2] == 'same-name' else found[2]))
        if not row['module']:
            for name in self.policy.get('globalNamespaces', []):
                if row['member'] in self.namespaces.get(name, {}).get('members', {}):
                    return self.cell(name, row['member'], 'global-module')
            if self.client.get(row['member'], {}).get('callable'):
                return self.cell('', row['member'], 'client-member')
        return None


def export_rows(exports, policy_json):
    internal = set(policy_json.get('internalExports', {}))
    standard = set(policy_json.get('standardExports', {}))
    groups = defaultdict(list)
    for record in exports['records']:
        groups[(record['ownerModule'], record['implementation'], record['kind'])].append(record['api'])
    rows = []
    for record in exports['records']:
        api = record['api']
        rows.append({
            'id': api, 'kind': 'export', 'module': record['module'], 'member': record['member'], 'ownerModule': record['ownerModule'],
            'aliases': sorted(other for other in groups[(record['ownerModule'], record['implementation'], record['kind'])] if other != api),
            'exportKind': record['kind'], 'registration': record['registration'], 'source': record.get('source'), 'line': record.get('line'),
            'metadataSource': record.get('metadata', exports.get('dataSource', {}).get('kind')),
            'surface': 'internal' if api in internal else 'standard' if api in standard else 'public',
            'arity': record.get('arity'), 'object': None, 'producers': [], 'memberSource': None,
        })
    return rows


def returned_rows(exports):
    """Rows for the returnedObjects list (P0.4); absent until extract_exports.py writes it."""
    rows = []
    for item in exports.get('returnedObjects') or []:
        for member in item.get('members', []):
            rows.append({
                'id': f"{item['object']}#{member['name']}", 'kind': 'returned-member', 'module': None, 'member': member['name'],
                'ownerModule': None, 'aliases': [], 'exportKind': member.get('kind'), 'registration': None,
                'source': member.get('source'), 'line': member.get('line'), 'metadataSource': item.get('memberSource'),
                'surface': 'public', 'arity': member.get('arity'), 'object': item['object'],
                'producers': sorted(item.get('producers', [])), 'memberSource': item.get('memberSource'),
            })
    return rows


def surface_states(row, documentation, surfaces):
    states = {}
    if documentation is not None:
        doc = documentation.get(row['id'])
        states['doc'] = {'heading': 'heading', 'mention': 'mention'}.get(doc['status'], 'none') if doc else 'none'
    if surfaces is not None:
        surface = surfaces.get(row['id'])
        if not surface or surface['declaration']['status'] != 'explicit':
            states['dts'] = 'none'
        else:
            arity = surface.get('arity') if isinstance(surface.get('arity'), dict) else {}
            states['dts'] = 'arity' if arity.get('status') in ('match', 'wider') else 'name'
        if not surface or not (surface.get('aceDefault') and surface.get('aceAllGroups')):
            states['ace'] = 'none'
        else:
            states['ace'] = 'static' if surface.get('aceStatic') else 'semantic'
    return states


def apply_exclusions(rows, rules):
    """Exclusions fill pending cells only; a rule matching a covered cell is reported as shadowed."""
    used, shadowed = Counter(), defaultdict(list)
    for row in rows:
        for column in RUNTIME_COLUMNS:
            for rule in rules:
                if column not in rule.get('columns', RUNTIME_COLUMNS) or not any(fnmatchcase(row['id'], pattern) for pattern in rule['match']):
                    continue
                used[rule['id']] += 1
                cell = row[column]
                if cell['status'] == 'pending' or rule.get('override'):
                    row[column] = {key: rule[key] for key in ('status', 'reason', 'equivalent', 'note') if rule.get(key)} | {'rule': rule['id']}
                elif cell['status'] in COVERED:
                    shadowed[(rule['id'], column)].append(row['id'])
                break
    conflicts = [{'rule': rule, 'column': column, 'kind': 'shadowed', 'ids': ids} for (rule, column), ids in sorted(shadowed.items())]
    conflicts += [{'rule': rule['id'], 'kind': 'unused'} for rule in rules if not used[rule['id']]]
    return conflicts


def missing_reasons(rows):
    result = []
    for row in rows:
        for column in RUNTIME_COLUMNS:
            cell = row[column]
            if cell['status'] not in EXCLUDED:
                continue
            if cell.get('reason') not in REASONS:
                result.append({'id': row['id'], 'column': column, 'problem': 'reason missing or unknown'})
            elif cell['reason'] == 'runtime-native' and not cell.get('equivalent'):
                result.append({'id': row['id'], 'column': column, 'problem': 'runtime-native without equivalent'})
    return result


def build(exports, policy, policy_json, node_surfaces, bun_surfaces, host_api, worker, catalog=None, documentation=None, surfaces=None, sources=None):
    if policy.get('schema') != POLICY_SCHEMA:
        raise ValueError(f'runtime-policy.json schema must be {POLICY_SCHEMA}')
    rows = export_rows(exports, policy_json) + returned_rows(exports)
    by_id = {row['id']: row for row in rows}
    if len(by_id) != len(rows):
        raise ValueError('Duplicate row ids: ' + ', '.join(sorted(k for k, v in Counter(r['id'] for r in rows).items() if v > 1)))
    documentation = None if documentation is None else {item['api']: item for item in documentation}
    surfaces = None if surfaces is None else {item['api']: item for item in surfaces.get('rows', [])}
    node = NodeIndex(node_surfaces, policy.get('node', {}), host_api, worker, catalog)
    bun = BunIndex(bun_surfaces, policy.get('bun', {}))
    returned = policy.get('returnedObjects', {})
    for row in rows:
        row['rhino'] = 'native'
        row.update({column: None for column in ['doc', 'dts', 'ace']})
        row.update(surface_states(row, documentation, surfaces))
        if row['kind'] == 'export':
            group = [by_id[other] for other in row['aliases']]
            row['node_main'] = node.lookup(row, group) or {'status': 'pending'}
            row['node_worker'] = node.lookup(row, group, worker=True) or {'status': 'pending'}
            row['bun'] = bun.lookup(row, group) or {'status': 'pending'}
        else:
            mapping = returned.get(row['object'], {})
            for column in RUNTIME_COLUMNS:
                row[column] = dict(mapping[column]) if column in mapping else {'status': 'pending'}
    conflicts = apply_exclusions(rows, policy.get('exclusions', []))
    rows.sort(key=lambda row: row['id'])
    by_column = {}
    for column, values in [('doc', DOC_VALUES), ('dts', DTS_VALUES), ('ace', ACE_VALUES)]:
        counts = Counter(row[column] for row in rows if row[column] is not None)
        by_column[column] = {value: counts.get(value, 0) for value in values} if counts else None
    for column in RUNTIME_COLUMNS:
        counts = Counter(row[column]['status'] for row in rows)
        by_column[column] = {status: counts.get(status, 0) for status in STATUSES}
    exported = sum(row['kind'] == 'export' for row in rows)
    return {
        'schema': SCHEMA, 'generatedBy': 'tools/api-coverage/runtime_matrix.py',
        'dataSource': exports.get('dataSource', {'kind': 'kotlin'}), 'sources': sources or {}, 'columns': COLUMNS,
        'summary': {'rows': len(rows), 'exports': exported, 'returnedMembers': len(rows) - exported,
                    'returnedObjectsInput': 'returnedObjects' in exports, 'byColumn': by_column,
                    'pending': {column: by_column[column]['pending'] for column in RUNTIME_COLUMNS},
                    'missingReasons': missing_reasons(rows), 'policyConflicts': conflicts},
        'rows': rows,
    }


def short(cell):
    status = cell['status']
    if status in EXCLUDED:
        return f"{status}: {cell.get('reason', '?')} ({cell['rule']})" if cell.get('rule') else f"{status}: {cell.get('reason', '?')}"
    return status


def cell_code(row, column):
    cell = row[column]
    return 'ok' if cell['status'] in COVERED else 'excluded' if cell['status'] in EXCLUDED else 'pending'


def render_markdown(matrix):
    summary = matrix['summary']
    kind = matrix['dataSource'].get('kind', 'kotlin')
    lines = [
        f"<!-- runtime-matrix: schema={SCHEMA} dataSource={kind} rows={summary['rows']} -->",
        '# AutoJs6 脚本 API 运行时覆盖矩阵',
        '',
        '由 `tools/api-coverage/runtime_matrix.py` 生成 (`python tools/api-coverage/check.py --matrix`), 不要手改. '
        '定义见 `docs/api-coverage/ROADMAP.md` 第 4 节, 机读版本是 CI 产物 `build/reports/api-coverage/runtime-matrix.json`, '
        '人工标注 (别名映射, 排除理由, 等价写法) 只在 `tools/api-coverage/runtime-policy.json`; 排除规则在 D12 / Q1 拍板前是草案.',
        '',
        f"数据源: {kind}, {summary['rows']} 行 (显式导出 {summary['exports']}, 返回对象成员 {summary['returnedMembers']}"
        + ('' if summary['returnedObjectsInput'] else ', exports.json 尚无 returnedObjects 段') + ').',
        '',
        '## 状态汇总',
        '',
        '| 列 | ' + ' | '.join(STATUSES) + ' |',
        '|---|' + '---:|' * len(STATUSES),
    ]
    for column in RUNTIME_COLUMNS:
        counts = summary['byColumn'][column]
        lines.append(f'| {COLUMN_TITLES[column]} | ' + ' | '.join(str(counts[status]) for status in STATUSES) + ' |')
    lines += ['', '| 列 | 取值 |', '|---|---|']
    for column in ['doc', 'dts', 'ace']:
        counts = summary['byColumn'][column]
        lines.append(f'| {column} | ' + (' / '.join(f'{value} {count}' for value, count in counts.items()) if counts else '未运行 (缺 check.py 报告)') + ' |')
    rules = defaultdict(lambda: Counter())
    for row in matrix['rows']:
        for column in RUNTIME_COLUMNS:
            if row[column].get('rule'):
                rules[row[column]['rule']][column] += 1
    lines += ['', f"排除理由缺失: {len(summary['missingReasons'])}. 策略冲突: "
              + (', '.join(f"{item['rule']} {item['kind']}" + (f" ({item['column']}, {len(item['ids'])})" if item['kind'] == 'shadowed' else '')
                           for item in summary['policyConflicts']) or '无') + '.']
    lines += ['', '## 排除规则 (草案)', '', '| 规则 | 状态 / 理由 | Node main | Node Worker | Bun | 等价写法 |', '|---|---|---:|---:|---:|---|']
    policy_rows = {}
    for row in matrix['rows']:
        for column in RUNTIME_COLUMNS:
            cell = row[column]
            if cell.get('rule'):
                policy_rows.setdefault(cell['rule'], cell)
    for rule in sorted(rules):
        cell = policy_rows[rule]
        lines.append(f"| {rule} | {cell['status']}: {cell.get('reason', '?')} | " + ' | '.join(str(rules[rule][column]) for column in RUNTIME_COLUMNS)
                     + f" | {cell.get('equivalent', '')} |")
    modules = defaultdict(list)
    for row in matrix['rows']:
        modules[row['object'] + '#' if row['kind'] == 'returned-member' else row['module'] or '(global)'].append(row)
    lines += ['', '## 按模块汇总', '', '运行时列为 覆盖 (bridged / local-shim / capability-gated) / 排除 (not-portable / denied) / pending; '
              'doc 为有标题的行数, d.ts 为声明可解析的行数, Ace 为默认与全组补全都命中的行数.', '',
              '| 模块 | 行 | doc 标题 | d.ts | Ace | Node main | Node Worker | Bun |', '|---|---:|---:|---:|---:|---|---|---|']
    for module in sorted(modules):
        items = modules[module]
        counts = [str(sum(row[column] in hits for row in items)) if items[0][column] is not None else '-'
                  for column, hits in [('doc', {'heading', 'signature', 'example'}), ('dts', {'name', 'arity', 'typed'}), ('ace', {'semantic', 'static'})]]
        runtime = [' / '.join(str(sum(cell_code(row, column) == code for row in items)) for code in ('ok', 'excluded', 'pending')) for column in RUNTIME_COLUMNS]
        lines.append(f"| {module} | {len(items)} | " + ' | '.join(counts + runtime) + ' |')
    lines += ['', '## 差异行', '', '只列至少一个运行时列不是覆盖状态的行.']
    for module in sorted(modules):
        items = [row for row in modules[module] if any(cell_code(row, column) != 'ok' for column in RUNTIME_COLUMNS)]
        if not items:
            continue
        lines += ['', f'### {module}', '', '| 行 id | Node main | Node Worker | Bun |', '|---|---|---|---|']
        for row in items:
            lines.append(f"| `{row['id']}` | " + ' | '.join(short(row[column]) for column in RUNTIME_COLUMNS) + ' |')
    return '\n'.join(lines) + '\n'


def markdown_state(text):
    match = re.match(r'<!-- runtime-matrix: schema=(\S+) dataSource=(\S+) rows=(\d+) -->', text or '')
    return {'schema': match.group(1), 'dataSource': match.group(2), 'rows': int(match.group(3))} if match else None


def compare_markdown(matrix, path):
    """'current', 'stale', 'missing' or 'skipped' (the checked-in file came from another data source)."""
    path = Path(path)
    if not path.is_file():
        return 'missing'
    text = path.read_text(encoding='utf-8')
    state = markdown_state(text)
    if state and state['dataSource'] != matrix['dataSource'].get('kind', 'kotlin'):
        return 'skipped'
    return 'current' if text == render_markdown(matrix) else 'stale'


def load_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def generate(host, node_root, bun_root, typescript, reports, exports=None, documentation=None, surfaces=None):
    """Read the runtime repositories and check.py's reports, return the matrix."""
    reports = Path(reports)
    if exports is None:
        exports_path = reports / 'exports.json'
        if exports_path.is_file():
            exports = load_json(exports_path)
        else:
            from extract_exports import extract
            exports = extract(host)
    if documentation is None and (reports / 'documentation.json').is_file():
        documentation = load_json(reports / 'documentation.json')
    if surfaces is None and (reports / 'surfaces.json').is_file():
        surfaces = load_json(reports / 'surfaces.json')
    runtime = list_surfaces(node_root, bun_root, typescript, reports / 'runtime-surfaces.json')
    catalog_path = Path(node_root) / NODE_CATALOG
    sources = {
        'node': {'root': Path(node_root).name, 'commit': git_commit(node_root),
                 'files': [NODE_DECLARATIONS, NODE_HOST_API, NODE_FACADE + ' (Worker hostModuleMethods)', NODE_CATALOG]},
        'bun': {'root': Path(bun_root).name, 'commit': git_commit(bun_root), 'files': [BUN_DECLARATIONS, 'app/src/main/assets/autojs6/host-client.mjs (HOST_API_METHODS)']},
        'typescript': runtime['typescript'], 'documentation': documentation is not None, 'surfaces': surfaces is not None,
    }
    return build(exports, load_json(HERE / 'runtime-policy.json'), load_json(HERE / 'policy.json'), runtime['node'], runtime['bun'],
                 parse_host_api((Path(node_root) / NODE_HOST_API).read_text(encoding='utf-8')),
                 parse_worker_modules((Path(node_root) / NODE_FACADE).read_text(encoding='utf-8')),
                 catalog_capabilities(load_json(catalog_path)) if catalog_path.is_file() else None,
                 documentation, surfaces, sources)


def default_typescript(ace):
    lock = load_json(Path(ace) / 'tools/ace-lsp/ace-builds-lock.json')
    return Path(ace) / lock['assetRoot'] / 'autojs6/typescript/typescript.js'


def gate(matrix, markdown):
    """check.py --matrix: failures (the checked-in MATRIX.md differs) and report-only findings (pending, missing reasons)."""
    state = compare_markdown(matrix, markdown)
    failures = []
    if state in ('missing', 'stale'):
        failures.append(f'{MARKDOWN} is {state}; regenerate it with python tools/api-coverage/check.py --matrix --write-matrix')
    summary = matrix['summary']
    findings = [f'Runtime matrix pending in {COLUMN_TITLES[column]}: {count}' for column, count in summary['pending'].items() if count]
    findings += [f"Runtime matrix exclusion without a valid reason: {item['id']} ({item['column']}: {item['problem']})" for item in summary['missingReasons']]
    return {'markdown': state, 'pending': summary['pending'], 'missingReasons': len(summary['missingReasons']),
            'policyConflicts': len(summary['policyConflicts']), 'failures': failures, 'findings': findings}


def write(matrix, reports, markdown=None):
    reports = Path(reports)
    reports.mkdir(parents=True, exist_ok=True)
    (reports / 'runtime-matrix.json').write_text(json.dumps(matrix, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if markdown:
        Path(markdown).parent.mkdir(parents=True, exist_ok=True)
        Path(markdown).write_text(render_markdown(matrix), encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--host', type=Path, default=HERE.parents[1])
    parser.add_argument('--node', type=Path, help='AutoJs6-Plugin-NodeJs-Runtime checkout (default: sibling of the host)')
    parser.add_argument('--bun', type=Path, help='AutoJs6-Plugin-Bun-Runtime checkout (default: sibling of the host)')
    parser.add_argument('--ace', type=Path, help='Ace plugin checkout providing the bundled TypeScript compiler')
    parser.add_argument('--typescript', type=Path, help='TypeScript compiler module (default: the Ace bundled compiler)')
    parser.add_argument('--reports', type=Path, help=f'check.py report directory (default: <host>/{REPORTS})')
    parser.add_argument('--write-markdown', action='store_true', help=f'Rewrite {MARKDOWN}')
    parser.add_argument('--check', action='store_true', help=f'Fail when {MARKDOWN} differs from the generated matrix')
    args = parser.parse_args()
    host = args.host.resolve()
    node = (args.node or host.parent / 'AutoJs6-Plugin-NodeJs-Runtime').resolve()
    bun = (args.bun or host.parent / 'AutoJs6-Plugin-Bun-Runtime').resolve()
    typescript = (args.typescript or default_typescript((args.ace or host.parent / 'AutoJs6-Plugin-Ace-Editor').resolve())).resolve()
    reports = (args.reports or host / REPORTS).resolve()
    matrix = generate(host, node, bun, typescript, reports)
    write(matrix, reports, host / MARKDOWN if args.write_markdown else None)
    result = gate(matrix, host / MARKDOWN)
    print(json.dumps({key: matrix['summary'][key] for key in ['rows', 'exports', 'returnedMembers', 'pending']}
                     | {'missingReasons': result['missingReasons'], 'policyConflicts': result['policyConflicts'], 'markdown': result['markdown']},
                     ensure_ascii=False, indent=2))
    return 1 if args.check and result['failures'] else 0


if __name__ == '__main__':
    sys.exit(main())
