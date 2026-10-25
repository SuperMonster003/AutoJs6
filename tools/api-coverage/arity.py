#!/usr/bin/env python3
"""Argument-count ranges of script functions and their comparison with the d.ts overloads (api-coverage P0.3).

A function's arity is a list of overload ranges {min, max, basis}; max None means unbounded.
With script-modules.json (metadata ksp) the ranges come from the overloads: the declared
arity of a single raw vararg overload (basis declared), else the parameter list (basis
params: required parameters to all parameters, unbounded with a vararg). A single raw
vararg overload without a declared arity checks its arguments itself (basis vararg, [0, n);
the d.ts comparison skips it). Install functions use the arity of their "installs" entry.
Kotlin registrations (metadata kotlin) read the ensureArguments* guard that starts the
implementation body (basis ensureArguments, the guard name in "guard"); unwrapArguments
is an unchecked vararg like basis vararg; anything else is unknown and counted.

The comparison with the d.ts call signatures (README "Arity"): a d.ts overload that no
implementation overload intersects is a failure; one that reaches counts the
implementation rejects is "wider" (warning); implementation counts no d.ts overload
accepts are "narrower" (warning).
"""
import json
from pathlib import Path
import re

from extract_exports import end_group, masked

GUARDS = ['OnlyOne', 'Length', 'LengthInRange', 'AtMost', 'AtLeast', 'IsEmpty', 'NotEmpty']
STATUSES = ['match', 'fail', 'wider', 'narrower', 'vararg', 'unknown', 'no-dts']
GUARD_CALL = re.compile(r'(?:return\s+)?(?:ArgumentGuards\.)?ensureArguments(' + '|'.join(GUARDS) + r')\(\s*(\w+)\s*(?:,\s*([^{}()]*?)\s*)?\)?\s*(?:\{|\)|$)')
UNWRAP_CALL = re.compile(r'(?:return\s+)?(?:ArgumentGuards\.)?unwrapArguments\(\s*(\w+)\s*\)')


def span(lo, hi, basis, **extra):
    return dict({'min': lo, 'max': hi, 'basis': basis}, **extra)


def overload_range(overload):
    """Range of one script-modules.json overload (function, invoke or construct)."""
    declared = overload.get('arity')
    params = overload['params']
    if declared:
        return span(declared['min'], declared.get('max'), 'declared')
    if len(params) == 1 and params[0]['vararg'] and params[0]['kind'] == 'RAW':
        return span(0, None, 'vararg')
    required = sum(1 for p in params if not p['optional'] and not p['vararg'])
    return span(required, None if any(p['vararg'] for p in params) else len(params), 'params')


def ksp_arity(overloads):
    return {'source': 'ksp', 'overloads': [overload_range(o) for o in overloads]}


def install_arity(install):
    if install.get('arity'):
        return {'source': 'ksp', 'overloads': [span(install['arity']['min'], install['arity'].get('max'), 'declared')]}
    return {'source': 'unknown', 'overloads': [], 'reason': 'install function without a declared arity'}


def guard_range(guard, value, constants):
    """[min, max] of an ensureArguments<guard>(args, value) call; None when the value is not a literal or constant."""
    if guard in ('OnlyOne', 'IsEmpty', 'NotEmpty'):
        return {'OnlyOne': (1, 1), 'IsEmpty': (0, 0), 'NotEmpty': (1, None)}[guard]
    value = (value or '').strip()
    resolve = lambda text: int(text) if text.isdigit() else constants.get(text)
    if guard == 'LengthInRange':
        m = re.fullmatch(r'(\w+)\s*(\.\.|until|\.\.<)\s*(\w+)', value)
        if not m or resolve(m[1]) is None or resolve(m[3]) is None:
            return None
        return resolve(m[1]), resolve(m[3]) - (0 if m[2] == '..' else 1)
    n = resolve(value)
    if n is None:
        return None
    return {'Length': (n, n), 'AtLeast': (n, None), 'AtMost': (0, n)}[guard]


class KotlinGuards:
    """ensureArguments* guards of the Kotlin implementations, read from masked sources (cached per file)."""

    def __init__(self, host):
        self.host = Path(host)
        self.files = {}

    def source(self, relative):
        if relative not in self.files:
            path = self.host / relative
            text = masked(path.read_text(encoding='utf-8')) if path.is_file() else ''
            constants = {m[1]: int(m[2]) for m in re.finditer(r'const val (\w+)\s*(?::\s*Int\s*)?=\s*(\d+)\b', text)}
            self.files[relative] = (text, constants)
        return self.files[relative]

    def body(self, text, name):
        """(body text, args parameter name) of `fun name(..., <args>: Array<out Any?>)` or None.

        Of same-name functions the one taking only a ScriptRuntime besides the arguments wins over prototype
        extensions that also take `thisObj` (jsox Arrayx / Numberx)."""
        candidates = []
        for m in re.finditer(r'\bfun\s+`?' + re.escape(name) + r'`?\s*\(', text):
            close = end_group(text, m.end() - 1)
            params = text[m.end():close]
            if re.search(r'\w+\s*:\s*Array<out Any\?>', params):
                others = [p for p in params.split(',') if p.strip() and 'Array<out Any?>' not in p]
                candidates.append((not all('ScriptRuntime' in p for p in others), m.start(), m, close))
        for _, _, m, close in sorted(candidates, key=lambda c: c[:2]):
            args = re.search(r'(\w+)\s*:\s*Array<out Any\?>', text[m.end():close])
            rest = text[close + 1:]
            start = re.match(r'\s*(?::\s*[^={\n]+?)?\s*([={])', rest)
            if not start:
                continue
            offset = close + 1 + start.end(1) - 1
            if start[1] == '{':
                return text[offset + 1:end_group(text, offset)], args[1]
            nxt = re.search(r'\n\s*(?:@|(?:(?:override|private|internal|protected|public|inline|operator)\s+)*fun\s)', text[offset:])
            return text[offset + 1:offset + (nxt.start() if nxt else len(text) - offset)], args[1]
        return None

    @staticmethod
    def unwrapped(head):
        """The body without leading runtime checks (`checkRuntime(x)`, `check(...) { ... }`) and lambda wrappers (`guarded {`)."""
        while True:
            m = re.match(r'(?:checkRuntime|check|require)\(', head)
            if m:
                close = end_group(head, m.end() - 1)
                rest = head[close + 1:].lstrip()
                head = (rest[end_group(rest, 0) + 1:] if rest.startswith('{') else rest).lstrip()
                continue
            m = re.match(r'(?:return\s+)?\w+\s*\{\s*(?:\w+\s*->\s*)?(?=(?:return\s+)?(?:ArgumentGuards\.)?ensureArguments)', head)
            if m:
                head = head[m.end():]
                continue
            return head

    def arity(self, relative, name, depth=0):
        text, constants = self.source(relative)
        found = self.body(text, name) if text else None
        if found is None:
            return {'source': 'unknown', 'overloads': [], 'reason': 'implementation not found'}
        body, args = found
        head = self.unwrapped(body.strip())
        m = GUARD_CALL.match(head)
        if m and m[2] == args:
            rng = guard_range(m[1], m[3], constants)
            if rng is None:
                return {'source': 'unknown', 'overloads': [], 'reason': f'ensureArguments{m[1]} bound is not a literal'}
            return {'source': 'ensureArguments', 'overloads': [span(rng[0], rng[1], 'ensureArguments', guard=m[1])]}
        u = UNWRAP_CALL.match(head)
        if u and u[1] == args:
            return {'source': 'ensureArguments', 'overloads': [span(0, None, 'vararg', guard='unwrapArguments')]}
        # One level of same-file delegation: `= other(scriptRuntime, args)`.
        d = re.match(r'(?:return\s+)?(\w+)\((?:\s*\w+\s*,)?\s*' + re.escape(args) + r'\s*\)\s*$', head)
        if d and depth < 2 and d[1] != name:
            return self.arity(relative, d[1], depth + 1)
        return {'source': 'unknown', 'overloads': [], 'reason': 'no ensureArguments guard at the start of the body'}


def attach(host, result, metadata=None):
    """Adds "arity" to function records and invokeArity / constructArity to callable modules, and result["arityStats"]."""
    guards = KotlinGuards(host)
    meta = json.loads(Path(metadata).read_text(encoding='utf-8')) if metadata else None
    functions, installs, calls = {}, {}, {}
    if meta:
        path_of = {m['class']: m['module'] for m in result['modules'] if m.get('metadata') == 'ksp'}
        for m in meta['modules']:
            path = path_of.get(m['class'])
            for f in m['functions']:
                for name in [f['name']] + f['aliases']:
                    functions[(path, name)] = ksp_arity(f['overloads'])
            for i in m.get('installs', []):
                if i['kind'] == 'function':
                    installs[(path, i['name'])] = install_arity(i)
            calls[path] = {key: ksp_arity([m[key]]) for key in ('invoke', 'construct') if m.get(key)}
    for record in result['records']:
        if record['kind'] != 'Functions':
            continue
        key = (record['ownerModule'], record['member'])
        if record.get('metadata') == 'ksp':
            table = installs if record['registration'] == '@ScriptInstall' else functions
            record['arity'] = table.get(key) or {'source': 'unknown', 'overloads': [], 'reason': 'not in script-modules.json'}
        elif record['registration'] in ('selfAssignmentFunctions', 'globalAssignmentFunctions'):
            record['arity'] = guards.arity(record['source'], record['implementation'])
        else:
            record['arity'] = {'source': 'unknown', 'overloads': [], 'reason': f'registration {record["registration"]}'}
    for module in result['modules']:
        if module.get('metadata') == 'ksp':
            for key, value in calls.get(module['module'], {}).items():
                module[key + 'Arity'] = value
        elif module.get('callable') and module['module']:
            module['invokeArity'] = guards.arity(module['source'], 'invoke')
    result['arityStats'] = stats(result)
    return result


def stats(result):
    rows = [r['arity'] for r in result['records'] if 'arity' in r]
    rows += [m[k] for m in result['modules'] for k in ('invokeArity', 'constructArity') if k in m]
    count = lambda pred: sum(1 for a in rows if pred(a))
    bases, guards = {}, {}
    for a in rows:
        for o in a['overloads']:
            bases[o['basis']] = bases.get(o['basis'], 0) + 1
            if 'guard' in o:
                guards[o['guard']] = guards.get(o['guard'], 0) + 1
    return {'functions': len(rows), 'ksp': count(lambda a: a['source'] == 'ksp'),
            'ensureArguments': count(lambda a: a['source'] == 'ensureArguments'), 'unknown': count(lambda a: a['source'] == 'unknown'),
            'uncheckedVararg': count(lambda a: a['overloads'] and all(o['basis'] == 'vararg' for o in a['overloads'])),
            'overloadsByBasis': dict(sorted(bases.items())), 'guards': dict(sorted(guards.items()))}


def counts(ranges, cap):
    """Argument counts 0..cap accepted by ranges; cap stands for every count >= cap."""
    accepted = set()
    for r in ranges:
        hi = cap if r['max'] is None else min(r['max'], cap)
        accepted.update(range(r['min'], hi + 1))
    return accepted


def compare_ranges(implementation, dts):
    """Status and details of implementation arity against d.ts ranges (README "Arity")."""
    if implementation['source'] == 'unknown' or not implementation['overloads']:
        return 'unknown', {}
    if all(o['basis'] == 'vararg' for o in implementation['overloads']):
        return 'vararg', {}
    if not dts:
        return 'no-dts', {}
    bounds = [b for r in implementation['overloads'] + dts for b in (r['min'], r['max']) if b is not None]
    cap = max(bounds) + 1
    impl = counts(implementation['overloads'], cap)
    show = lambda r: f'{r["min"]}-{"n" if r["max"] is None else r["max"]}'
    disjoint = [show(d) for d in dts if not counts([d], cap) & impl]
    if disjoint:
        return 'fail', {'disjoint': disjoint}
    beyond = sorted(counts(dts, cap) - impl)
    if beyond:
        return 'wider', {'rejected': [str(c) if c < cap else f'{cap}+' for c in beyond]}
    if any(o['basis'] == 'vararg' for o in implementation['overloads']):
        return 'match', {}
    # Metadata ranges are spans (a declared arity or optional parameters may cover counts the body rejects, e.g.
    # images.clip (image, region) / (image, x, y, width, height) is 2-5), so only the ends are compared; an unbounded
    # end that comes from an undeclared vararg parameter (basis params) is not compared either.
    undeclared = []
    low, dts_low = min(o['min'] for o in implementation['overloads']), min(d['min'] for d in dts)
    if low < dts_low:
        undeclared.append(f'{low}' if dts_low - low == 1 else f'{low}-{dts_low - 1}')
    highs, dts_highs = [o['max'] for o in implementation['overloads']], [d['max'] for d in dts]
    if None not in dts_highs:
        dts_high = max(dts_highs)
        if None in highs:
            if any(o['max'] is None and o['basis'] != 'params' for o in implementation['overloads']):
                undeclared.append(f'{dts_high + 1}+')
        elif max(highs) > dts_high:
            undeclared.append(f'{dts_high + 1}' if max(highs) == dts_high + 1 else f'{dts_high + 1}-{max(highs)}')
    if undeclared:
        return 'narrower', {'undeclared': undeclared}
    return 'match', {}


def text(ranges):
    return ','.join(f'{r["min"]}-{"n" if r["max"] is None else r["max"]}' for r in ranges) or '-'


def compare(baseline, surfaces):
    """Rows of every queried function (and callable module) with implementation and d.ts arity, plus counts."""
    declared = {row['api']: row['declaration'] for row in surfaces}
    rows = []

    def add(api, owner, metadata, arity, kind):
        declaration = declared.get(api.removesuffix('.()').removesuffix('.new()'))
        if declaration is None:
            return
        dts = (declaration.get('signatures') or {}).get(kind) if declaration['status'] == 'explicit' else None
        status, detail = compare_ranges(arity, dts)
        rows.append(dict({'api': api, 'ownerModule': owner, 'metadata': metadata, 'source': arity['source'],
                          'implementation': text(arity['overloads']), 'dts': text(dts or []), 'status': status}, **detail))

    for record in baseline['records']:
        if 'arity' in record:
            add(record['api'], record['ownerModule'], record.get('metadata'), record['arity'], 'call')
    for module in baseline['modules']:
        for key, suffix, kind in (('invokeArity', '.()', 'call'), ('constructArity', '.new()', 'construct')):
            if key in module and module['module']:
                add(module['module'] + suffix, module['module'], module.get('metadata'), module[key], kind)
    summary = {status: sum(1 for r in rows if r['status'] == status) for status in STATUSES}
    return {'rows': sorted(rows, key=lambda r: r['api']), 'counts': summary}


def report(host, baseline, output):
    """check.py: compares with output/surfaces.json, adds rows[].arity there, writes output/arity.json; returns the summary."""
    surfaces_path = Path(output) / 'surfaces.json'
    surfaces = json.loads(surfaces_path.read_text(encoding='utf-8'))
    result = compare(baseline, surfaces['rows'])
    by_api = {row['api']: row for row in result['rows']}
    for row in surfaces['rows']:
        if row['api'] in by_api:
            found = by_api[row['api']]
            row['arity'] = {key: found[key] for key in ('status', 'source', 'implementation', 'dts', 'disjoint', 'rejected', 'undeclared') if key in found}
    surfaces_path.write_text(json.dumps(surfaces, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    crossed, crossing = [], 'not applicable (Kotlin registrations are the data source)'
    if baseline['dataSource']['kind'] == 'ksp':
        from extract_exports import extract_kotlin
        try:
            crossed, crossing = cross_check(baseline, attach(host, extract_kotlin(host))), 'ensureArguments guards of the Kotlin registrations'
        except (OSError, ValueError, KeyError, StopIteration) as error:
            crossing = f'unavailable: {error}'
    result['stats'] = baseline.get('arityStats')
    result['crossCheck'] = {'source': crossing, 'rows': crossed}
    (Path(output) / 'arity.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    return dict(result['counts'], crossCheck=len(crossed))


def cross_check(ksp, kotlin):
    """APIs whose ksp arity differs from the ensureArguments guard of the Kotlin implementation (D9 cross-check)."""
    old = {r['api']: r['arity'] for r in kotlin['records'] if r.get('arity', {}).get('source') == 'ensureArguments'}
    rows = []
    for record in ksp['records']:
        a, b = record.get('arity'), old.get(record['api'])
        if not a or a['source'] != 'ksp' or not b or all(o['basis'] == 'vararg' for o in b['overloads']):
            continue
        undeclared = all(o['basis'] == 'vararg' for o in a['overloads'])
        bounds = [x for r in a['overloads'] + b['overloads'] for x in (r['min'], r['max']) if x is not None]
        cap = max(bounds, default=0) + 1
        if undeclared or counts(a['overloads'], cap) != counts(b['overloads'], cap):
            rows.append({'api': record['api'], 'ownerModule': record['ownerModule'], 'ksp': text(a['overloads']),
                         'kspBasis': sorted({o['basis'] for o in a['overloads']}), 'kotlin': text(b['overloads']),
                         'guard': b['overloads'][0]['guard'], 'kind': 'vararg-undeclared' if undeclared else 'differs'})
    return rows
