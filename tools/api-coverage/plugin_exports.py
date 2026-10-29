#!/usr/bin/env python3
"""Script API names a plugin contract defines (api-coverage P5.2c, roadmap appendix C Q75 (a)).

policy.json "pluginExports" names contracts whose names scripts reach although no host module registers them one by
one. The only source is the Compose UI catalogue (kind "compose-ui-catalog"), the contract the 3-Block Compose plugin
renders and the compose module installs at run time (the KSP "installsComputed" of compose): the NODE components are
the functions `compose.<Name>`, the modifiers the methods of `compose.modifier()` chains, the shortcuts the accessors
every Compose node defines. The catalogue is read statically from the Kotlin sources of the contract
(`plugin-api/compose-ui-api`): component, modifier and shortcut names are the constants of the vocabulary objects
the catalogue functions reference.

A source whose root is missing (another checkout layout, a machine without the contract) is "skipped": its rows are
left out and the matrix, the pending baseline and the documentation tables are reported as skipped, not stale.
"""
import json
import os
from pathlib import Path
import re

from extract_exports import end_group, masked

SOURCE_PREFIX = 'plugin:'
COMPOSE_FILES = {'vocabulary': 'ComposeUiVocabulary.kt', 'v1': 'catalog/V1Catalog.kt', 'v2': 'v2/ComposeUiV2.kt',
                 'interop': 'interop/AndroidViewInteropV1.kt'}


def line_of(text, offset):
    return text.count('\n', 0, offset) + 1


def constants(text, name):
    """`const val X = "y"` of `object <name> { ... }` as {X: y}."""
    match = re.search(r'\bobject\s+' + name + r'\s*\{', text)
    if not match:
        raise ValueError(f'object {name} not found')
    body = text[match.end():end_group(text, match.end() - 1)]
    return dict(re.findall(r'\bconst\s+val\s+(\w+)\s*=\s*"([^"\n]*)"', body))


def body_of(text, signature):
    """(start, end) of the block of the first function matching the regular expression `signature`."""
    match = re.search(signature, text)
    if not match:
        raise ValueError(f'{signature} not found')
    start = text.index('{', match.end())
    return start, end_group(text, start)


def references(text, region, qualifiers, table, problems, label):
    """Names of the `<qualifier>.<CONSTANT>` references in region, in order, with their lines."""
    found, seen = [], set()
    pattern = r'(?<![\w.])(?:' + '|'.join(re.escape(q) for q in qualifiers) + r')\.([A-Z][A-Z0-9_]*)\b'
    for match in re.finditer(pattern, text[region[0]:region[1]]):
        constant = match[1]
        if constant not in table:
            problems.append(f'{label}: {match[0]} is not a vocabulary constant')
            continue
        if table[constant] not in seen:
            seen.add(table[constant])
            found.append((table[constant], line_of(text, region[0] + match.start())))
    return found


def command_components(text, region, qualifier, table):
    """Names of `ComponentSpec(<qualifier>.X, ..., invocation = ComponentInvocation.COMMAND)` (not node factories)."""
    names = set()
    for match in re.finditer(r'\bComponentSpec\(\s*' + re.escape(qualifier) + r'\.(\w+)', text[region[0]:region[1]]):
        start = region[0] + match.start() + len('ComponentSpec')
        if re.search(r'\binvocation\s*=\s*ComponentInvocation\.COMMAND\b', text[start:end_group(text, start)]):
            names.add(table.get(match[1], match[1]))
    return names


def read_compose(root):
    """Components (NODE invocation), modifiers and shortcuts of the interop catalogue the compose module installs.

    Returns {components, modifiers, shortcuts: [(name, file, line)], commandComponents, problems}."""
    root = Path(root)
    texts = {key: masked((root / name).read_text(encoding='utf-8')) for key, name in COMPOSE_FILES.items()}
    files = {key: (root / name) for key, name in COMPOSE_FILES.items()}
    problems = []
    components_v1 = constants(texts['vocabulary'], 'ComposeUiComponents')
    modifiers = constants(texts['vocabulary'], 'ComposeUiModifiers')
    shortcuts = constants(texts['vocabulary'], 'ComposeUiShortcuts')
    components_v2 = constants(texts['v2'], 'Components')
    v1 = texts['v1']
    create_v1 = body_of(v1, r'\bfun\s+create\s*\(\s*\)\s*:\s*ComponentCatalog')
    found_v1 = references(v1, create_v1, ['C'], components_v1, problems, 'V1Catalog.create')
    commands = command_components(v1, create_v1, 'C', components_v1)
    v2 = texts['v2']
    create_v2 = body_of(v2, r'\bprivate\s+fun\s+create\s*\(\s*\)\s*:\s*ComponentCatalog')
    found_v2 = references(v2, create_v2, ['c', 'Components'], components_v2, problems, 'ComposeUiV2.create')
    commands |= command_components(v2, create_v2, 'c', components_v2)
    # The interop catalogue is the V2 catalogue plus the negotiated AndroidView component.
    if not re.search(r'\binteropCatalog\s*=\s*ComponentCatalog\(\s*catalog\.components\s*\+\s*AndroidViewInteropV1\.component\b', v2):
        problems.append('ComposeUiV2.interopCatalog is no longer catalog.components + AndroidViewInteropV1.component')
    interop = texts['interop']
    android = re.search(r'\bconst\s+val\s+COMPONENT\s*=\s*"(\w+)"', interop)
    component_spec = re.search(r'\bval\s+component\s*=\s*ComponentSpec\(\s*COMPONENT\b', interop)
    if not android or not component_spec:
        problems.append('AndroidViewInteropV1.component is no longer ComponentSpec(COMPONENT, ...)')
    for label, table, found in [('ComposeUiComponents', components_v1, found_v1), ('ComposeUiV2.Components', components_v2, found_v2)]:
        missing = sorted(set(table.values()) - {name for name, _ in found})
        problems += [f'{label}.{name}: constant not in the catalogue function' for name in missing]
    rows = [(name, files['v1'], line) for name, line in found_v1] + [(name, files['v2'], line) for name, line in found_v2]
    if android and component_spec:
        rows.append((android[1], files['interop'], line_of(interop, component_spec.start())))
    components = [row for row in rows if row[0] not in commands]
    # Modifiers come from the V1 modifier catalogue (V2 and the interop catalogue keep base.modifiers / shortcuts).
    modifier_region = _expression_body(v1, r'\bprivate\s+fun\s+modifierCatalog\s*\(\s*\)\s*:\s*List<ModifierSpec>\s*=\s*listOf')
    # Only the names opening a ModifierSpec (C.COLUMN / C.ROW / C.BOX in allowedParents are components).
    found_modifiers = [row for row in references(v1, modifier_region, ['M'], modifiers, problems, 'V1Catalog.modifierCatalog')
                       if _spec_call(v1, modifier_region, 'ModifierSpec', 'M', row[0], modifiers)]
    shortcut_region = _expression_body(v1, r'\bval\s+shortcuts\s*=\s*listOf')
    found_shortcuts = []
    for match in re.finditer(r'\bShortcutSpec\(\s*Q\.(\w+)', v1[shortcut_region[0]:shortcut_region[1]]):
        if match[1] not in shortcuts:
            problems.append(f'V1Catalog shortcuts: Q.{match[1]} is not a vocabulary constant')
            continue
        found_shortcuts.append((shortcuts[match[1]], line_of(v1, shortcut_region[0] + match.start())))
    for label, table, found in [('ComposeUiModifiers', modifiers, found_modifiers), ('ComposeUiShortcuts', shortcuts, found_shortcuts)]:
        missing = sorted(set(table.values()) - {name for name, _ in found})
        problems += [f'{label}.{name}: constant not in the catalogue' for name in missing]
    for kind, items in [('component', components), ('modifier', found_modifiers), ('shortcut', found_shortcuts)]:
        duplicates = sorted({item[0] for item in items if [i[0] for i in items].count(item[0]) > 1})
        problems += [f'duplicate {kind} {name}' for name in duplicates]
    return {'components': components, 'modifiers': [(name, files['v1'], line) for name, line in found_modifiers],
            'shortcuts': [(name, files['v1'], line) for name, line in found_shortcuts],
            'commandComponents': sorted(commands), 'problems': problems}


def _expression_body(text, signature):
    """(start, end) of the parenthesized argument list after the regular expression `signature` (`listOf(...)`)."""
    match = re.search(signature + r'\s*\(', text)
    if not match:
        raise ValueError(f'{signature} not found')
    start = match.end() - 1
    return start, end_group(text, start)


def _spec_call(text, region, call, qualifier, name, table):
    """True when `<call>(<qualifier>.X` with X the constant of name occurs in region (the name opens a spec)."""
    keys = [key for key, value in table.items() if value == name]
    return any(re.search(r'\b' + call + r'\(\s*' + qualifier + r'\.' + key + r'\b', text[region[0]:region[1]]) for key in keys)


def resolve_root(host, config):
    """The contract root: `root` of the config (absolute, or relative to the host), else None."""
    value = os.environ.get(config.get('rootEnvironment') or '') or config.get('root')
    if not value:
        return None
    path = Path(value)
    return path if path.is_absolute() else Path(host) / path


def relative(host, path):
    try:
        return Path(path).resolve().relative_to(Path(host).resolve()).as_posix()
    except ValueError:
        return Path(path).as_posix()


def attach(host, result, policy):
    """Adds the plugin-defined export records (metadata plugin:<name>) and result["pluginExports"].

    result["pluginExports"][name]: {status: read | skipped, root, reason, module, counts, records (row ids),
    members: {modifiers: [...], shortcuts: [...]}, problems}. Returned objects with memberSource plugin:<name> take
    their members from members[<entry catalog>] (returned_objects.py)."""
    sources = {}
    known = {record['api'] for record in result['records']}
    for name, config in sorted((policy or {}).get('pluginExports', {}).items()):
        if name.startswith('_'):
            continue
        if config.get('kind') != 'compose-ui-catalog':
            sources[name] = {'status': 'skipped', 'reason': f'unknown kind {config.get("kind")}', 'problems': [f'{name}: unknown kind {config.get("kind")}']}
            continue
        root = resolve_root(host, config)
        if root is None or not all((root / file).is_file() for file in COMPOSE_FILES.values()):
            sources[name] = {'status': 'skipped', 'root': config.get('root'), 'reason': 'contract sources not found', 'problems': []}
            continue
        catalogue = read_compose(root)
        module = config['module']
        metadata = SOURCE_PREFIX + name
        added = []
        for component, path, line in catalogue['components']:
            api = module + '.' + component
            if api in known:
                catalogue['problems'].append(f'{api} is already an export of the host modules')
                continue
            result['records'].append({'api': api, 'module': module, 'ownerModule': module, 'member': component,
                                      'implementation': component, 'kind': 'Functions', 'registration': 'pluginExports',
                                      'source': relative(host, path), 'line': line, 'metadata': metadata})
            added.append(api)
        member = lambda item: {'name': item[0], 'kind': 'Functions', 'source': relative(host, item[1]), 'line': item[2], 'arity': None}
        sources[name] = {'status': 'read', 'root': relative(host, root), 'module': module, 'plugin': config.get('plugin'),
                         'counts': {'components': len(catalogue['components']), 'modifiers': len(catalogue['modifiers']),
                                    'shortcuts': len(catalogue['shortcuts'])},
                         'commandComponents': catalogue['commandComponents'], 'records': added,
                         'members': {'modifiers': [member(item) for item in catalogue['modifiers']],
                                     'shortcuts': [dict(member(item), kind='GettersAndSetters') for item in catalogue['shortcuts']]},
                         'problems': catalogue['problems']}
    result['records'].sort(key=lambda record: record['api'])
    result['pluginExports'] = sources
    if skipped(result):
        result.setdefault('dataSource', {})['pluginExportsSkipped'] = skipped(result)
    return result


def skipped(exports):
    """Names of the pluginExports sources the run skipped."""
    return sorted(name for name, item in (exports.get('pluginExports') or {}).items() if item.get('status') == 'skipped')


if __name__ == '__main__':
    import sys
    here = Path(__file__).resolve().parent
    policy = json.loads((here / 'policy.json').read_text(encoding='utf-8'))
    print(json.dumps(attach(here.parents[1], {'records': []}, policy)['pluginExports'], ensure_ascii=False, indent=2))
    sys.exit(0)
