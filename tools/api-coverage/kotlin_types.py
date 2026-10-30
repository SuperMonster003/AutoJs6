#!/usr/bin/env python3
"""Parameter and return types of script functions and their comparison with the d.ts types (api-coverage P2.4, D9 step 2).

attach() reads the Kotlin types script-modules.json gives every parameter, return value and accessor of a
@ScriptFunction / @ScriptGetter / @ScriptProperty (and the invoke / construct overloads of callable modules) and maps
each to a descriptor with type-map.json:

    {"ts": {"nominal", "accepted", "returns", "open"}, "nullable"}   a TypeScript type (type-map.json kotlin / collections)
    {"object": <name>, "probe": <d.ts path>, "nullable"}              a class of returned-objects.json
    {"java": <qualified name>, "nullable"}                            any other class, resolved by name in the d.ts
    {"unmapped": <reason>}                                            no type to compare (type-map.json unmapped)

The rows go to exports.json "typeRows"; verify-surfaces.mjs (dts-types.mjs) compares them with the d.ts types and
writes types.json, which report() summarizes for check.py (README "Types"). @ScriptInstall names carry no types and
are counted as unmapped.
"""
import json
from collections import Counter, defaultdict
from fnmatch import fnmatchcase
from pathlib import Path

HERE = Path(__file__).resolve().parent
LEVELS = ['match', 'refined', 'coerced', 'narrow', 'wide', 'error', 'unmapped']
CONSISTENT = {'match', 'refined', 'coerced'}


def load_map(path=None):
    return json.loads(Path(path or HERE / 'type-map.json').read_text(encoding='utf-8'))


def split_generic(text):
    """('kotlin.collections.List', ['kotlin.Int']) of 'kotlin.collections.List<kotlin.Int>'; variance (`out`) dropped."""
    start = text.find('<')
    if start < 0:
        return text, []
    inner, args, depth, begin = text[start + 1:text.rindex('>')], [], 0, 0
    for index, char in enumerate(inner):
        if char == '<':
            depth += 1
        elif char == '>':
            depth -= 1
        elif char == ',' and not depth:
            args.append(inner[begin:index].strip())
            begin = index + 1
    args.append(inner[begin:].strip())
    return text[:start], [arg.removeprefix('out ').removeprefix('in ').strip() for arg in args]


class Mapper:
    """Kotlin type text -> descriptor (module docstring), for parameters (`param=True`) or return values."""

    def __init__(self, table, returned):
        self.table = table
        self.objects = {item['class']: item for item in returned.get('objects', [])}

    def __call__(self, text, param=False):
        nullable = text.endswith('?')
        base_text = text[:-1] if nullable else text
        if text in self.table['unmapped']:
            return {'unmapped': text}
        base, args = split_generic(base_text)
        if base in self.table['unmapped']:
            return {'unmapped': base}
        entry = self.table['kotlin'].get(base)
        if entry:
            ts = {'nominal': entry['nominal'], 'open': bool(entry.get('open'))}
            ts['accepted' if param else 'returns'] = entry.get('accepted' if param else 'returns', entry['nominal'])
            return {'ts': ts, 'nullable': nullable}
        collections = self.table['collections']
        if base in collections['list']:
            if param:
                return {'ts': {'nominal': 'readonly unknown[]', 'accepted': 'readonly unknown[]', 'open': True}, 'nullable': nullable}
            element = self(args[0]) if args else {'unmapped': 'no element type'}
            if 'ts' in element:
                text = element['ts']['returns'] + (' | null' if element['nullable'] else '')
                return {'ts': {'nominal': f'readonly ({text})[]', 'returns': f'readonly ({text})[]', 'open': element['ts']['open']}, 'nullable': nullable}
            return {'ts': {'nominal': 'readonly unknown[]', 'returns': 'readonly unknown[]', 'open': True}, 'nullable': nullable}
        if base in collections['map']:
            return {'ts': {'nominal': 'object', ('accepted' if param else 'returns'): 'object', 'open': True}, 'nullable': nullable}
        if base in self.objects:
            return {'object': self.objects[base]['object'], 'probe': self.objects[base]['probe'], 'nullable': nullable}
        if base.startswith('kotlin.') and not base.startswith('kotlin.collections.'):
            return {'unmapped': base}
        return {'java': base, 'nullable': nullable}


def overload_types(mapper, overload):
    return {'params': [{'name': p['name'], 'kotlin': p['type'], 'optional': p['optional'], 'vararg': p['vararg'], 'type': mapper(p['type'], param=True)}
                       for p in overload['params']],
            'returns': {'kotlin': overload['returns'], 'type': mapper(overload['returns'])}}


def attach(host, result, metadata=None, table=None, returned=None):
    """Adds result["typeRows"] and result["typeStats"]; empty without script-modules.json (Kotlin registrations)."""
    host = Path(host)
    if result['dataSource']['kind'] != 'ksp':
        result['typeRows'], result['typeStats'] = [], {'source': 'unavailable (Kotlin registrations are the data source)'}
        return result
    metadata = Path(metadata) if metadata else host / result['dataSource']['file']
    meta = json.loads(metadata.read_text(encoding='utf-8'))
    returned = returned if returned is not None else json.loads((HERE / 'returned-objects.json').read_text(encoding='utf-8'))
    mapper = Mapper(table or load_map(), returned)
    path_of = {m['class']: m['module'] for m in result['modules'] if m.get('metadata') == 'ksp'}
    functions, accessors, calls = {}, {}, {}
    for m in meta['modules']:
        path = path_of.get(m['class'])
        for f in m['functions']:
            for name in [f['name']] + f['aliases']:
                functions[(path, name)] = f
        for a in m['accessors']:
            accessors[(path, a['name'])] = a
        calls[path] = {key: m[key] for key in ('invoke', 'construct') if m.get(key)}
    rows, skipped = [], Counter()
    for record in result['records']:
        if record.get('metadata') != 'ksp':
            continue
        key = (record['ownerModule'], record['member'])
        if record['registration'] == '@ScriptFunction' and key in functions:
            rows.append({'api': record['api'], 'module': record['ownerModule'], 'kind': 'function', 'query': record['api'], 'signature': 'call',
                         'overloads': [overload_types(mapper, o) for o in functions[key]['overloads']]})
        elif record['registration'] in ('@ScriptGetter', '@ScriptProperty') and key in accessors:
            accessor = accessors[key]
            rows.append({'api': record['api'], 'module': record['ownerModule'], 'kind': 'accessor', 'query': record['api'],
                         'value': {'kotlin': accessor['type'], 'type': mapper(accessor['type'])}})
        else:
            skipped[record['registration']] += 1
    for module in result['modules']:
        for key, call in calls.get(module['module'], {}).items() if module.get('metadata') == 'ksp' and module['module'] else ():
            rows.append({'api': module['module'] + ('.()' if key == 'invoke' else '.new()'), 'module': module['module'], 'kind': key,
                         'query': module['module'], 'signature': 'call' if key == 'invoke' else 'construct', 'overloads': [overload_types(mapper, call)]})
    result['typeRows'] = rows
    result['typeStats'] = {'rows': len(rows), 'notTyped': dict(skipped)}
    return result


def summarize(rows):
    """Counts by level for parameter positions, return values (accessor values included) and rows."""
    params, returns, by_row = Counter(), Counter(), Counter()
    reasons = Counter()
    for row in rows:
        for item in row.get('params', []):
            params[item['level']] += 1
            if item['level'] == 'unmapped':
                reasons[item.get('reason', '?')] += 1
        if row.get('returns'):
            returns[row['returns']['level']] += 1
            if row['returns']['level'] == 'unmapped':
                reasons[row['returns'].get('reason', '?')] += 1
        by_row[row['status']] += 1
    return {'params': {level: params[level] for level in LEVELS if params[level]},
            'returns': {level: returns[level] for level in LEVELS if returns[level]},
            'rows': {status: by_row[status] for status in ('typed', 'differs', 'unmapped', 'no-dts') if by_row[status]},
            'unmappedReasons': dict(reasons.most_common())}


DIFFERING = ('narrow', 'wide', 'error')


def differing_positions(rows):
    """(key, kind, level, message) of the positions that differ; kind is "params" or "returns" (accessor values too)."""
    found = []
    for row in rows:
        for item in row.get('params', []):
            if item['level'] in DIFFERING:
                key = f"{row['api']} parameter {item['index'] + 1}"
                found.append((key, 'params', item['level'], f"{key} ({item['level']}): d.ts {item.get('dts')} / Kotlin {item.get('kotlin')}"))
        returned = row.get('returns')
        if returned and returned['level'] in DIFFERING:
            key = f"{row['api']} {'value' if row['kind'] == 'accessor' else 'return'}"
            found.append((key, 'returns', returned['level'], f"{key} ({returned['level']}): d.ts {returned.get('dts')} / Kotlin {returned.get('kotlin')}"))
    return found


def reviewed_entry(entries, key, level):
    """The type-map.json reviewed entry (fnmatch pattern keys) covering a differing position, or None."""
    return next((pattern for pattern, entry in entries.items() if entry.get('level') == level and fnmatchcase(key, pattern)), None)


def strict_failures(rows, table=None):
    """check.py --strict-types: positions on the unsafe levels of strictLevels that type-map.json reviewed does not cover
    (Q187 (a)), and reviewed entries that no differing position matches any more (stale)."""
    table = table or load_map()
    levels, entries = table['strictLevels'], table.get('reviewed', {}).get('entries', {})
    found = differing_positions(rows)
    failures = [message for key, kind, level, message in found if level in levels[kind] and not reviewed_entry(entries, key, level)]
    used = {reviewed_entry(entries, key, level) for key, _, level, _ in found}
    failures += [f"Stale type-map.json reviewed entry (no {entry.get('level')} position matches it): {pattern}; remove it"
                 for pattern, entry in entries.items() if pattern not in used]
    return failures


def report(baseline, output, table=None):
    """check.py: reads output/types.json (verify-surfaces.mjs), adds the summary and the per-module counts; returns the summary."""
    path = Path(output) / 'types.json'
    if not path.is_file():
        return {'status': 'not run'}
    table = table or load_map()
    data = json.loads(path.read_text(encoding='utf-8'))
    rows = data['rows']
    summary = summarize(rows)
    modules = defaultdict(Counter)
    for row in rows:
        modules[row['module'] or '(global)'][row['status']] += 1
    entries = table.get('reviewed', {}).get('entries', {})
    data['summary'] = summary
    data['modules'] = {name: dict(counts) for name, counts in sorted(modules.items())}
    data['strict'] = strict_failures(rows, table)
    data['reviewed'] = {key: reviewed_entry(entries, key, level) for key, _, level, _ in differing_positions(rows) if reviewed_entry(entries, key, level)}
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    return dict(summary, compared=len(rows), notTyped=baseline.get('typeStats', {}).get('notTyped', {}),
                strict=len(data['strict']), reviewed=len(data['reviewed']))


NODE_COVERED = ('bridged', 'local-shim')


def node_reviewed(rows, table):
    """The differing positions of the Node comparison that type-map.json nodeReviewed covers ({key: pattern}), the ones it
    does not cover (messages) and the failures for entries no position matches any more."""
    entries = table.get('nodeReviewed', {}).get('entries', {})
    found = differing_positions(rows)
    reviewed = {key: reviewed_entry(entries, key, level) for key, _, level, _ in found if reviewed_entry(entries, key, level)}
    unreviewed = [message for key, _, _, message in found if key not in reviewed]
    used = set(reviewed.values())
    stale = [f"Stale type-map.json nodeReviewed entry (no {entry.get('level')} position matches it): {pattern}; remove it"
             for pattern, entry in entries.items() if pattern not in used]
    return reviewed, unreviewed, stale


def runtime_report(baseline, matrix, node_root, typescript, output, table=None):
    """Q186 (a): compares the Node runtime declarations (autojs6-node d.ts) of the rows the matrix covers in Node main
    (bridged / local-shim with a module and member) with the same Kotlin types; return values are compared awaited. Writes output/runtime-types.json and returns its summary (reported only)."""
    import subprocess
    node_of = {row['id']: row['node_main'] for row in matrix['rows']
               if row['node_main'].get('status') in NODE_COVERED and row['node_main'].get('module') and row['node_main'].get('member')}
    rows = [dict(row, node={key: node_of[row['api']].get(key) for key in ('module', 'member', 'async')})
            for row in baseline.get('typeRows', []) if row['api'] in node_of]
    output = Path(output)
    request = output / 'runtime-types-input.json'
    request.write_text(json.dumps({'rows': rows}, ensure_ascii=False) + '\n', encoding='utf-8')
    command = ['node', str(HERE / 'verify-runtime-types.mjs'), '--node', str(node_root), '--typescript', str(typescript),
               '--rows', str(request), '--output', str(output / 'runtime-types.json')]
    result = subprocess.run(command, check=False, capture_output=True, text=True, encoding='utf-8')
    if result.returncode:
        return {'status': 'failed', 'error': (result.stderr or result.stdout).strip()[-2000:]}
    data = json.loads((output / 'runtime-types.json').read_text(encoding='utf-8'))
    summary = summarize(data['rows'])
    data['summary'] = summary
    reviewed, unreviewed, stale = node_reviewed(data['rows'], table or load_map())
    data['reviewed'], data['unreviewed'] = reviewed, unreviewed
    (output / 'runtime-types.json').write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    return dict(summary, compared=len(data['rows']), covered=len(node_of), reviewed=len(reviewed),
                unreviewed=[item.split(' (')[0] for item in unreviewed], staleReviewed=stale)
