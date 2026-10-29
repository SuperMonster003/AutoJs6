#!/usr/bin/env python3
"""Generate the runtime coverage matrix (api-coverage roadmap P0.2, definition in ROADMAP section 4).

One row per explicit export of exports.json (plus the members of module prototypes
and of returned objects, its prototypes and returnedObjects lists), with the documentation, declaration
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
PENDING_BASELINE = 'tools/api-coverage/pending-baseline.json'
BASELINE_SCHEMA = 'autojs6-pending-baseline-v1'
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
    # Dotted names ("jsox.mathx") can only be string keys.
    for quoted, plain, value in re.findall(r'(?:"([\w.]+)"|(\w+))\s*:\s*(Object\.keys\(|\[[^\]]*\])', body):
        modules[quoted or plain] = None if value.startswith('Object.keys') else re.findall(r'"([^"]+)"', value)
    if not modules:
        raise ValueError('Worker hostModuleMethods table is empty or changed shape')
    return modules


def catalog_capabilities(catalog):
    result = defaultdict(set)
    for item in catalog.get('bridge', {}).get('operations', []):
        result[(item['module'], item['method'])].update(item.get('requiredCapabilities', []))
    return result


def list_surfaces(node, bun, typescript, output):
    """Run list-runtime-surfaces.mjs (TypeScript checker over the runtime declarations, and the handle types
    runtime-policy.json returnedObjects names)."""
    command = ['node', str(HERE / 'list-runtime-surfaces.mjs'), '--node', str(node), '--bun', str(bun),
               '--typescript', str(typescript), '--policy', str(HERE / 'runtime-policy.json'), '--output', str(output)]
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


def refused(cell):
    """An entry whose declarations only reject (never / Promise<never>) leaves the row pending (Q26); the cell keeps
    the entry, and an exclusion rule with a reason is the only way to mark the row not-portable / denied."""
    if not cell or not cell.get('rejects'):
        return cell
    return {'status': 'pending', 'rejects': True} | {key: cell[key] for key in ('module', 'namespace', 'type', 'member', 'via') if key in cell}


def handle_types(items, key):
    return {key(item): item['members'] for item in items or []}


class NodeIndex:
    """Node main-thread / Worker entry lookup over the declared modules."""

    def __init__(self, surfaces, policy, host_api, worker, catalog=None):
        self.modules = surfaces['modules']
        self.explicit = set(surfaces.get('explicitGlobal', {}).values())
        self.handles = handle_types(surfaces.get('handles'), lambda item: (item['module'], item['type']))
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

    def in_worker(self, name, path):
        key = 'console' if name == 'node:console' else bare(name)
        if key not in self.worker:
            return False
        members = self.worker[key]
        return members is None or path.split('.')[0] in members

    def available(self, name, path, worker, rejecting=False):
        """`rejecting`: also accept entries that only reject (the second pass of lookup)."""
        module = self.modules.get(name)
        if module is None or path not in module['members'] or module['members'][path].get('rejects') and not rejecting:
            return False
        return not worker or bare(name) in self.native or self.in_worker(name, path)

    def find(self, host_module, member, worker, rejecting=False):
        parts = host_module.split('.') if host_module else []
        for k in range(len(parts), 0, -1):
            path = '.'.join(parts[k:] + [member])
            for name, via in self.candidates('.'.join(parts[:k])):
                if self.available(name, path, worker, rejecting):
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
        if info.get('rejects'):
            cell['rejects'] = True
        if host_api:
            cell['hostApi'] = host_api
        capabilities = sorted(self.catalog.get((bare(name), path.split('.')[0]), ()))
        if capabilities:
            cell['capabilities'] = capabilities
        if bare(name) in self.explicit:
            cell['explicitGlobal'] = '$autojs.' + bare(name)
        return cell

    def lookup(self, row, group, worker=False):
        """The first entry that does not only reject, else the first rejecting one (pending, Q26)."""
        return refused(self.search(row, group, worker) or self.search(row, group, worker, rejecting=True))

    def search(self, row, group, worker, rejecting=False):
        alias = self.policy.get('memberAliases', {}).get(row['id'])
        if alias:
            if self.available(alias['module'], alias['member'], worker, rejecting):
                return self.cell(alias['module'], alias['member'], 'member-alias')
            return None
        for candidate, via in [(row, None)] + [(other, 'alias') for other in group]:
            module, path, own = placement(candidate)
            if module:
                found = self.find(module, path, worker, rejecting)
                if found:
                    return self.cell(found[0], found[1], via or (own if own and found[2] == 'same-name' else found[2]))
        if not row['module']:
            for name in self.policy.get('globalModules', []):
                if self.available(name, row['member'], worker, rejecting):
                    return self.cell(name, row['member'], 'global-module')
        compat = self.policy.get('compatModule')
        if compat:
            path = (row['module'] + '.' if row['module'] else '') + row['member']
            if self.available(compat, path, worker, rejecting):
                return self.cell(compat, path, 'compat')
        return None

    def returned(self, row, target, worker=False):
        """A returned-object member through the runtime handle type of runtime-policy.json returnedObjects (Q27):
        covered when the type declares the member (in Worker when the type's module is a Worker host module)."""
        members = self.handles.get((target['module'], target['type']))
        if members is None:
            raise ValueError(f"runtime-policy.json returnedObjects {row['object']}: Node type {target['type']} not found in {target['module']}")
        info = members.get(row['member'])
        if info is None or worker and bare(target['module']) not in self.worker:
            return None
        cell = {'status': self.status(target['module'])[0], 'module': target['module'], 'type': target['type'], 'member': row['member'],
                'async': info['async'], 'via': 'handle-type'}
        if info.get('rejects'):
            cell['rejects'] = True
        return refused(cell)


class BunIndex:
    """Bun host client entry lookup: HostClient namespaces and client members."""

    def __init__(self, surfaces, policy):
        self.namespaces = surfaces['namespaces']
        self.client = surfaces.get('clientMembers', {})
        self.methods = surfaces.get('methods', {})
        self.capabilities = surfaces.get('capabilities', [])
        self.handles = handle_types(surfaces.get('handles'), lambda item: item['type'])
        self.policy = policy
        self.used = set()

    def related(self, namespace):
        related = self.policy.get('namespaceCapabilities', {}).get(namespace)
        if related is None:
            prefixes = {namespace, namespace.rstrip('s')}
            related = [name for name in self.capabilities if name.split('.')[0] in prefixes]
        return related

    def capability(self, namespace, path):
        """The grant of a namespace member: a related capability whose method table has it, else the only related one,
        else the explicit `memberCapabilities` mapping (client-side helpers and renamed wrappers of a method-table entry)."""
        related = self.related(namespace)
        owners = [name for name in related if path.split('.')[0] in self.methods.get(name, ())]
        if owners or len(related) == 1:
            return owners[0] if owners else related[0]
        key = f'{namespace}.{path}'
        mapped = self.policy.get('memberCapabilities', {}).get(key)
        if mapped:
            self.used.add(key)
            return mapped['capability'] if mapped['capability'] in self.capabilities else None
        return None

    def cell(self, namespace, path, via):
        """Client members are capability-gated; a namespace without any related capability holds client-side helpers,
        except the members `memberCapabilities` maps to a grant (util.java.* over the Java facade)."""
        info = self.namespaces[namespace]['members'][path] if namespace else self.client[path]
        mapped = bool(namespace) and f'{namespace}.{path}' in self.policy.get('memberCapabilities', {})
        status = 'capability-gated' if not namespace or self.related(namespace) or mapped else 'local-shim'
        cell = {'status': status, 'namespace': namespace, 'member': path, 'async': info['async'], 'via': via}
        if info.get('rejects'):
            cell['rejects'] = True
        capability = self.capability(namespace, path) if namespace else None
        if capability:
            cell['capability'] = capability
        return cell

    def has(self, namespace, path, rejecting):
        info = self.namespaces.get(namespace, {}).get('members', {}).get(path) if namespace else self.client.get(path)
        return info is not None and (rejecting or not info.get('rejects'))

    def find(self, host_module, member, rejecting=False):
        parts = host_module.split('.')
        for k in range(len(parts), 0, -1):
            base = '.'.join(parts[:k])
            path = '.'.join(parts[k:] + [member])
            names = [(base, 'same-name')] + [(alias, 'module-alias') for alias in self.policy.get('namespaceAliases', {}).get(base, [])]
            for name, via in names:
                # A dotted alias names an object nested in a namespace (Mathx -> jsox.mathx).
                namespace, _, prefix = name.partition('.') if via == 'module-alias' else (name, '', '')
                nested = f'{prefix}.{path}' if prefix else path
                if namespace and self.has(namespace, nested, rejecting):
                    return namespace, nested, via
        return None

    def lookup(self, row, group):
        """The first entry that does not only reject, else the first rejecting one (pending, Q26)."""
        return refused(self.search(row, group) or self.search(row, group, rejecting=True))

    def search(self, row, group, rejecting=False):
        alias = self.policy.get('memberAliases', {}).get(row['id'])
        if alias:
            namespace, path = alias['namespace'], alias['member']
            return self.cell(namespace, path, 'member-alias') if self.has(namespace, path, rejecting) else None
        for candidate, via in [(row, None)] + [(other, 'alias') for other in group]:
            module, path, own = placement(candidate)
            if module:
                found = self.find(module, path, rejecting)
                if found:
                    return self.cell(found[0], found[1], via or (own if own and found[2] == 'same-name' else found[2]))
        if not row['module']:
            for name in self.policy.get('globalNamespaces', []):
                if self.has(name, row['member'], rejecting):
                    return self.cell(name, row['member'], 'global-module')
            if self.client.get(row['member'], {}).get('callable') and self.has('', row['member'], rejecting):
                return self.cell('', row['member'], 'client-member')
        return None

    def returned(self, row, target):
        """A returned-object member through the host client handle type of runtime-policy.json returnedObjects (Q27):
        capability-gated by the grant of the namespace producing the handle (`capability` when it has several)."""
        members = self.handles.get(target['type'])
        if members is None:
            raise ValueError(f"runtime-policy.json returnedObjects {row['object']}: Bun type {target['type']} not found in {BUN_DECLARATIONS}")
        info = members.get(row['member'])
        if info is None:
            return None
        related = self.related(target['namespace'])
        capability = target.get('capability') or (related[0] if len(related) == 1 else None)
        cell = {'status': 'capability-gated' if related else 'local-shim', 'namespace': target['namespace'], 'type': target['type'],
                'member': row['member'], 'async': info['async'], 'via': 'handle-type'}
        if info.get('rejects'):
            cell['rejects'] = True
        if capability in self.capabilities:
            cell['capability'] = capability
        return refused(cell)


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
                # The documentation runtime support table of the object is on this page (Q20 (a)).
                'page': (item.get('documentation') or [None])[0],
            })
    return rows


def prototype_rows(exports):
    """Rows for the prototypes list (Q18 (a)): members scripts reach through the prototype of a @ScriptPrototype module,
    `<module>.<member>` like an explicit export and looked up in the runtimes the same way."""
    rows = []
    for item in exports.get('prototypes') or []:
        for member in item.get('members', []):
            rows.append({
                'id': f"{item['module']}.{member['name']}", 'kind': 'prototype-member', 'module': item['module'], 'member': member['name'],
                'ownerModule': item['module'], 'aliases': [], 'exportKind': member.get('kind'), 'registration': '@ScriptPrototype',
                'source': member.get('source'), 'line': member.get('line'), 'metadataSource': item.get('memberList'),
                'surface': 'public', 'arity': member.get('arity'), 'object': None, 'producers': [], 'memberSource': 'script-prototype',
            })
    return rows


def surface_states(row, documentation, surfaces, ace_checked=True):
    """doc / dts / ace states of a row; a column stays None without its check.py report (ace: check.py --skip-ace)."""
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
        if ace_checked:
            completes = surface and surface.get('aceDefault') and surface.get('aceAllGroups')
            states['ace'] = ('static' if surface.get('aceStatic') else 'semantic') if completes else 'none'
    return states


def apply_exclusions(rows, rules):
    """Exclusions fill pending cells only (a rejecting entry's cell keeps `rejects`); a rule matching a covered cell
    is reported as shadowed. A rule with `memberSource` only matches returned-member rows of that source
    (kotlin-class / augment-prototype)."""
    used, shadowed = Counter(), defaultdict(list)
    for row in rows:
        for column in RUNTIME_COLUMNS:
            for rule in rules:
                if column not in rule.get('columns', RUNTIME_COLUMNS) or not any(fnmatchcase(row['id'], pattern) for pattern in rule['match']):
                    continue
                if rule.get('memberSource') and row['memberSource'] != rule['memberSource']:
                    continue
                used[rule['id']] += 1
                cell = row[column]
                if cell['status'] == 'pending' or rule.get('override'):
                    row[column] = ({key: rule[key] for key in ('status', 'reason', 'equivalent', 'note') if rule.get(key)} | {'rule': rule['id']}
                                   | ({'rejects': True} if cell.get('rejects') else {}))
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


def missing_capabilities(rows):
    """Bun capability-gated cells without a capability name (Q12 (a): the grant must be named)."""
    return [{'id': row['id'], 'namespace': row['bun'].get('namespace'), 'member': row['bun'].get('member')}
            for row in rows if row['bun']['status'] == 'capability-gated' and not row['bun'].get('capability')]


def build(exports, policy, policy_json, node_surfaces, bun_surfaces, host_api, worker, catalog=None, documentation=None, surfaces=None, sources=None):
    if policy.get('schema') != POLICY_SCHEMA:
        raise ValueError(f'runtime-policy.json schema must be {POLICY_SCHEMA}')
    rows = export_rows(exports, policy_json) + prototype_rows(exports) + returned_rows(exports)
    by_id = {row['id']: row for row in rows}
    if len(by_id) != len(rows):
        raise ValueError('Duplicate row ids: ' + ', '.join(sorted(k for k, v in Counter(r['id'] for r in rows).items() if v > 1)))
    documentation = None if documentation is None else {item['api']: item for item in documentation}
    ace_checked = surfaces is not None and surfaces.get('aceChecked', True)
    surfaces = None if surfaces is None else {item['api']: item for item in surfaces.get('rows', [])}
    node = NodeIndex(node_surfaces, policy.get('node', {}), host_api, worker, catalog)
    bun = BunIndex(bun_surfaces, policy.get('bun', {}))
    returned = policy.get('returnedObjects', {})
    for row in rows:
        row['rhino'] = 'native'
        row.update({column: None for column in ['doc', 'dts', 'ace']})
        row.update(surface_states(row, documentation, surfaces, ace_checked))
        if row['kind'] in ('export', 'prototype-member'):
            group = [by_id[other] for other in row['aliases']]
            row['node_main'] = node.lookup(row, group) or {'status': 'pending'}
            row['node_worker'] = node.lookup(row, group, worker=True) or {'status': 'pending'}
            row['bun'] = bun.lookup(row, group) or {'status': 'pending'}
        else:
            mapping = returned.get(row['object'], {})
            row['node_main'] = 'node' in mapping and node.returned(row, mapping['node']) or {'status': 'pending'}
            row['node_worker'] = 'node' in mapping and node.returned(row, mapping['node'], worker=True) or {'status': 'pending'}
            row['bun'] = 'bun' in mapping and bun.returned(row, mapping['bun']) or {'status': 'pending'}
    conflicts = apply_exclusions(rows, policy.get('exclusions', []))
    objects = {row['object'] for row in rows}
    conflicts += [{'rule': f'returnedObjects {name}', 'kind': 'unused'} for name in sorted(returned) if not name.startswith('_') and name not in objects]
    conflicts += [{'rule': f'bun.memberCapabilities {key}', 'kind': 'unused'}
                  for key in sorted(set(policy.get('bun', {}).get('memberCapabilities', {})) - bun.used) if not key.startswith('_')]
    rows.sort(key=lambda row: row['id'])
    by_column = {}
    for column, values in [('doc', DOC_VALUES), ('dts', DTS_VALUES), ('ace', ACE_VALUES)]:
        counts = Counter(row[column] for row in rows if row[column] is not None)
        by_column[column] = {value: counts.get(value, 0) for value in values} if counts else None
    for column in RUNTIME_COLUMNS:
        counts = Counter(row[column]['status'] for row in rows)
        by_column[column] = {status: counts.get(status, 0) for status in STATUSES}
    exported = sum(row['kind'] == 'export' for row in rows)
    prototyped = sum(row['kind'] == 'prototype-member' for row in rows)
    return {
        'schema': SCHEMA, 'generatedBy': 'tools/api-coverage/runtime_matrix.py',
        'dataSource': exports.get('dataSource', {'kind': 'kotlin'}), 'sources': sources or {}, 'columns': COLUMNS,
        'summary': {'rows': len(rows), 'exports': exported, 'prototypeMembers': prototyped, 'returnedMembers': len(rows) - exported - prototyped,
                    'returnedObjectsInput': 'returnedObjects' in exports, 'byColumn': by_column,
                    'pending': {column: by_column[column]['pending'] for column in RUNTIME_COLUMNS},
                    'rejects': {column: sum(bool(row[column].get('rejects')) for row in rows) for column in RUNTIME_COLUMNS},
                    'missingReasons': missing_reasons(rows), 'missingCapabilities': missing_capabilities(rows), 'policyConflicts': conflicts},
        'rows': rows,
    }


def short(cell):
    status = cell['status']
    if status in EXCLUDED:
        return f"{status}: {cell.get('reason', '?')} ({cell['rule']})" if cell.get('rule') else f"{status}: {cell.get('reason', '?')}"
    return f'{status} (rejects)' if cell.get('rejects') else status


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
        '人工标注 (别名映射, 排除理由, 等价写法) 只在 `tools/api-coverage/runtime-policy.json`; 排除规则已拍板 (D12 / Q1, ROADMAP 附录 D), '
        '新增排除先登记待决; pending 只能减少 (`tools/api-coverage/pending-baseline.json`).',
        '',
        f"数据源: {kind}, {summary['rows']} 行 (显式导出 {summary['exports']}, 原型成员 {summary.get('prototypeMembers', 0)}, "
        f"返回对象成员 {summary['returnedMembers']}"
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
    lines += ['', f"排除理由缺失: {len(summary['missingReasons'])}. Bun 缺能力名: {len(summary.get('missingCapabilities', []))}. 策略冲突: "
              + (', '.join(f"{item['rule']} {item['kind']}" + (f" ({item['column']}, {len(item['ids'])})" if item['kind'] == 'shadowed' else '')
                           for item in summary['policyConflicts']) or '无') + '.']
    rejects = summary.get('rejects', {})
    lines += ['', '拒绝入口 (运行时声明只返回 never / Promise<never>, 记 pending, 排除须由规则给理由; Q26): '
              + ' / '.join(f'{COLUMN_TITLES[column]} {rejects.get(column, 0)}' for column in RUNTIME_COLUMNS)
              + '. 句柄类型 (runtime-policy.json returnedObjects, Q27) 的成员按运行时类型逐个比对.']
    lines += ['', '## 排除规则', '', '| 规则 | 状态 / 理由 | Node main | Node Worker | Bun | 等价写法 |', '|---|---|---:|---:|---:|---|']
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


def partial_columns(matrix):
    """doc / dts / ace columns the matrix was built without (no check.py report, or check.py --skip-ace for ace)."""
    return [column for column in ('doc', 'dts', 'ace') if matrix['summary']['byColumn'][column] is None]


def compare_markdown(matrix, path):
    """'current', 'stale', 'missing' or 'skipped': the checked-in file came from another data source, or the
    matrix lacks a column of it (MATRIX.md is only written with all columns)."""
    path = Path(path)
    if not path.is_file():
        return 'missing'
    text = path.read_text(encoding='utf-8')
    state = markdown_state(text)
    if state and state['dataSource'] != matrix['dataSource'].get('kind', 'kotlin') or partial_columns(matrix):
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


def pending_cells(matrix):
    """Row ids of the pending cells, per runtime column."""
    return {column: sorted(row['id'] for row in matrix['rows'] if row[column]['status'] == 'pending') for column in RUNTIME_COLUMNS}


def render_pending_baseline(matrix):
    kind = matrix['dataSource'].get('kind', 'kotlin')
    data = {'schema': BASELINE_SCHEMA, 'dataSource': kind,
            'note': 'Pending cells allowed by check.py --matrix (roadmap Q40 (a)): a pending cell missing here fails, and so does a listed '
                    'cell that is no longer pending; shrink with check.py --write-pending-baseline, add only with --allow-new-pending '
                    'and a pending decision on the api-coverage board.',
            'counts': {column: len(ids) for column, ids in pending_cells(matrix).items()}, 'pending': pending_cells(matrix)}
    return json.dumps(data, ensure_ascii=False, indent=1) + '\n'


def compare_pending_baseline(matrix, path):
    """('current' | 'differs' | 'missing' | 'skipped', new cells, stale cells); a cell is (column, row id). A baseline written
    from the other export source (ksp / kotlin) is skipped, as MATRIX.md is."""
    path = Path(path)
    if not path.is_file():
        return 'missing', [], []
    baseline = json.loads(path.read_text(encoding='utf-8'))
    if baseline.get('schema') != BASELINE_SCHEMA:
        raise ValueError(f'{PENDING_BASELINE} schema must be {BASELINE_SCHEMA}')
    if baseline.get('dataSource') != matrix['dataSource'].get('kind', 'kotlin'):
        return 'skipped', [], []
    current = pending_cells(matrix)
    listed = {column: set(baseline['pending'].get(column, [])) for column in RUNTIME_COLUMNS}
    new = [(column, item) for column in RUNTIME_COLUMNS for item in current[column] if item not in listed[column]]
    stale = [(column, item) for column in RUNTIME_COLUMNS for item in sorted(listed[column] - set(current[column]))]
    return ('differs' if new or stale else 'current'), new, stale


def write_pending_baseline(matrix, path, allow_new=False):
    """Rewrite the baseline with the current pending cells; without allow_new it only shrinks (returns the refused new cells)."""
    path = Path(path)
    state, new, _ = compare_pending_baseline(matrix, path) if path.is_file() else ('missing', [], [])
    if new and not allow_new:
        return new
    path.write_text(render_pending_baseline(matrix), encoding='utf-8', newline='\n')
    return []


def gate(matrix, markdown, baseline=None):
    """check.py --matrix: failures (the checked-in MATRIX.md differs, a Bun capability-gated cell without its capability,
    an exclusion without a valid reason, and with a baseline path a pending cell outside the baseline or a baseline cell
    no longer pending) and report-only findings (pending counts)."""
    state = compare_markdown(matrix, markdown)
    failures = []
    if state in ('missing', 'stale'):
        failures.append(f'{MARKDOWN} is {state}; regenerate it with python tools/api-coverage/check.py --matrix --write-matrix')
    summary = matrix['summary']
    failures += [f"Runtime matrix Bun capability-gated without a capability: {item['id']} ({item['namespace']}.{item['member']}); "
                 'map it in runtime-policy.json bun.memberCapabilities' for item in summary.get('missingCapabilities', [])]
    failures += [f"Runtime matrix exclusion without a valid reason: {item['id']} ({item['column']}: {item['problem']})" for item in summary['missingReasons']]
    findings = []
    if state == 'skipped':
        partial = partial_columns(matrix)
        findings.append(f"{MARKDOWN} not compared: the matrix has no {' / '.join(partial)} column (check.py --skip-ace, or no check.py reports)"
                        if partial else f'{MARKDOWN} not compared: it was generated from another data source')
    findings += [f'Runtime matrix pending in {COLUMN_TITLES[column]}: {count}' for column, count in summary['pending'].items() if count]
    baseline_state = None
    if baseline is not None:
        baseline_state, new, stale = compare_pending_baseline(matrix, baseline)
        if baseline_state == 'missing':
            failures.append(f'{PENDING_BASELINE} is missing; write it with python tools/api-coverage/check.py --write-pending-baseline')
        elif baseline_state == 'skipped':
            findings.append(f'{PENDING_BASELINE} not compared: it was written from another data source')
        failures += [f'Runtime matrix new pending cell {item} ({COLUMN_TITLES[column]}): cover it, exclude it with a decided reason, '
                     f'or register it and write {PENDING_BASELINE} with --write-pending-baseline --allow-new-pending' for column, item in new]
        if stale:
            failures.append(f'{PENDING_BASELINE} lists {len(stale)} cells that are no longer pending (first: {stale[0][1]} in '
                            f'{COLUMN_TITLES[stale[0][0]]}); shrink it with python tools/api-coverage/check.py --write-pending-baseline')
    return {'markdown': state, 'pending': summary['pending'], 'pendingBaseline': baseline_state, 'missingReasons': len(summary['missingReasons']),
            'missingCapabilities': len(summary.get('missingCapabilities', [])),
            'policyConflicts': len(summary['policyConflicts']), 'failures': failures, 'findings': findings}


def write(matrix, reports, markdown=None):
    reports = Path(reports)
    reports.mkdir(parents=True, exist_ok=True)
    (reports / 'runtime-matrix.json').write_text(json.dumps(matrix, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if markdown and partial_columns(matrix):
        raise ValueError(f"{MARKDOWN} is only written with every column; this matrix has no {' / '.join(partial_columns(matrix))} column "
                         '(run check.py --write-matrix without --skip-ace)')
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
    result = gate(matrix, host / MARKDOWN, host / PENDING_BASELINE)
    print(json.dumps({key: matrix['summary'][key] for key in ['rows', 'exports', 'returnedMembers', 'pending']}
                     | {'missingReasons': result['missingReasons'], 'missingCapabilities': result['missingCapabilities'],
                        'policyConflicts': result['policyConflicts'], 'markdown': result['markdown']},
                     ensure_ascii=False, indent=2))
    return 1 if args.check and result['failures'] else 0


if __name__ == '__main__':
    sys.exit(main())
