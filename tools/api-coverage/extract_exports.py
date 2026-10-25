#!/usr/bin/env python3
"""Extract explicit Rhino script module exports without treating JVM helpers as APIs.

The source of truth is script-modules.json, the metadata the KSP processor of
the script module layer writes (augment roadmap T2 / T10): module names and
aliases, functions with their aliases and placement, getters, properties and
Java classes. Names that a module's @ScriptInstall hook defines at run time are
read from the module's "installs" list (metadata written before the processor
had that list falls back to the Kotlin registrations of the current augment
package, marked with metadata "kotlin"); names a hook computes at run time are
only described there ("installsComputed"). Without script-modules.json (no KSP
build, e.g. the coverage CI job) the Kotlin registration parser is used for
everything.

The Kotlin path is deliberately a registration parser, not a Kotlin compiler.
Unknown registration expressions are returned for review and rejected by the
gate. Java prototypes, generated catalogues and returned objects require
separate contract probes; they must not be silently counted as complete here.
"""
import argparse
import json
import os
from pathlib import Path
import re
import sys

DEFAULT_METADATA = 'app/build/generated/ksp/appDebug/resources/autojs6/script-modules.json'
METADATA_INPUTS = ['app/src/main/java/org/autojs/autojs/runtime/api/modules', 'build-logic/ksp-script-modules-processor/src']
COMPARED_FIELDS = ['module', 'ownerModule', 'member', 'kind']

FUNCTION_NAME = r'(?:\w+)?::`?\w+`?\.name(?:\.lowercase\(\))?'
STRING_NAME = r'"(?:\\.|[^"\\])*"'
NAME = rf'(?:{FUNCTION_NAME}|{STRING_NAME})'
NAME_LIST = rf'listOf(?:<String>)?\(\s*(?:{NAME}(?:\s*,\s*{NAME})*\s*,?\s*)?\)'
FLAGS = r'(?:[A-Z][A-Z0-9_]*|\(\s*[A-Z][A-Z0-9_]*(?:\s+or\s+[A-Z][A-Z0-9_]*)*\s*\))'
FUNCTION_ASSIGNMENT = re.compile(rf'{NAME}(?:\s+to\s+(?:(?:{NAME}|{NAME_LIST})(?:\s+to\s+{FLAGS})?|{FLAGS}))?')


def function_assignment_is_supported(expression):
    """Recognizing a function prefix must not hide a dynamic alias RHS."""
    return FUNCTION_ASSIGNMENT.fullmatch(expression) is not None


def initializer_tail_is_supported(source, list_end, limit, lazy):
    """Reject list concatenation, mutation chains and other unparsed suffixes."""
    tail = source[list_end + 1:limit].strip()
    if lazy:
        if not tail.startswith('}'):
            return False
        tail = tail[1:].lstrip()
    return re.match(r'^(?:$|[};@]|(?:override|private|internal|protected|companion|init|fun|val|var|constructor|class|object|enum)\b)', tail) is not None


def mail_forwarded_names(source, list_end, limit):
    """Resolve the explicitly named default-client forwarding table."""
    expected = '''+ FORWARDED
        .flatMap { name -> if (name in SYNC_ONLY) listOf(name) else listOf(name, name + ASYNC_SUFFIX) }
        .map { name -> name to newBaseFunction(scriptRuntime.topLevelScope, name, { args -> forward(name, args) }, NOT_CONSTRUCTABLE) }
    }'''
    compact = lambda text: re.sub(r'\s+', '', text)
    if compact(source[list_end + 1:limit]) != compact(expected):
        return None
    values = {}
    for field in ['FORWARDED', 'SYNC_ONLY']:
        match = re.search(r'val ' + field + r'[^\n=]*=\s*(?:listOf|setOf)\(', source)
        if not match:
            return None
        start = match.end() - 1
        end = end_group(source, start)
        if not initializer_tail_is_supported(source, end, len(source), False):
            return None
        names = []
        for _, expression in entries(source, start + 1, end):
            if not re.fullmatch(STRING_NAME, expression):
                return None
            names.append(json.loads(expression))
        values[field] = names
    suffix = re.search(r'const val ASYNC_SUFFIX\s*=\s*("[^"\n]+")', source)
    if not suffix or not initializer_tail_is_supported(source, suffix.end() - 1, len(source), False):
        return None
    return [alias for name in values['FORWARDED'] for alias in
            ([name] if name in values['SYNC_ONLY'] else [name, name + json.loads(suffix[1])])]


def leading_offset(source, offset, end):
    return offset + len(source[offset:end]) - len(source[offset:end].lstrip())


def simple_name_table_names(source, initializer):
    """Resolve `= tables.map { t -> t::class.java.simpleName.lowercase() to t }` over `val tables = listOf(A.B, ...)`.

    The names are the lower-cased simple names of the listed objects (colors.android / css / web / material).
    Returns [(name, implementation, kind, offset)], or None for any other shape.
    """
    match = re.fullmatch(r'\s*(?::[^\n=]+)?=\s*(\w+)\.map\s*\{\s*(\w+)\s*->\s*\2::class\.java\.simpleName\.lowercase\(\)\s+to\s+\2\s*\}\s*', initializer)
    table = match and re.search(r'\bval ' + match[1] + r'\s*=\s*listOf\(', source)
    if not table:
        return None
    start = table.end() - 1
    end = end_group(source, start)
    if not initializer_tail_is_supported(source, end, len(source), False):
        return None
    names = []
    for offset, expression in entries(source, start + 1, end):
        reference = re.fullmatch(r'(?:\w+\.)*(\w+)', expression)
        if not reference:
            return None
        names.append((reference[1].lower(), expression, None, leading_offset(source, offset, end)))
    return names


def compose_function_map_names(source, after, limit):
    """Resolve Compose's lazy function map; its explicit keys are stable API names.

    Shape: `by lazy { val functions = linkedMapOf<...>("name" to { ... }, ...)`, optionally the NODE catalogue
    components added by `spec.name` (generated catalogue, validated by the Compose contract checks, like the KSP
    installsComputed), then `functions.map { (name, action) -> name to ... } + ("Name" to Factory(scope))`.
    Returns [(name, implementation, kind, offset)], or None for any other shape.
    """
    head = re.match(r'\s*(?::[^\n=]+)?\s*by\s+lazy\s*\{\s*val\s+functions\s*=\s*linkedMapOf[^\n]*\(', source[after:limit])
    if not head:
        return None
    lazy_end = end_group(source, source.index('{', after))
    map_open = after + head.end() - 1
    map_end = end_group(source, map_open)
    if lazy_end >= limit:
        return None
    names = []
    for offset, expression in entries(source, map_open + 1, map_end):
        entry = re.match(r'"(\w+)"\s+to\s+\{', expression)
        if not entry or end_group(expression, entry.end() - 1) != len(expression) - 1:
            return None
        names.append((entry[1], entry[1], 'Functions', leading_offset(source, offset, map_end)))
    tail = source[map_end + 1:lazy_end]
    position = 0
    catalogue = re.match(r'\s*[\w.]*\.interopCatalog\.components\.filter\s*\{\s*it\.invocation\s*==\s*ComponentInvocation\.NODE\s*\}'
                         r'\s*\.forEach\s*\{\s*(\w+)\s*->\s*functions\[\1\.name\]\s*=\s*\{', tail)
    if catalogue:
        close = re.match(r'\s*\}', tail[end_group(tail, catalogue.end() - 1) + 1:])
        if not close:
            return None
        position = end_group(tail, catalogue.end() - 1) + 1 + close.end()
    final = re.compile(r'\s*functions\.map\s*\{\s*\(\s*(\w+)\s*,\s*\w+\s*\)\s*->\s*\1\s+to\s').match(tail, position)
    if not final:
        return None
    position = end_group(tail, tail.index('{', position)) + 1
    extra = re.compile(r'\s*\+\s*\(\s*"(\w+)"\s+to\s+[\w.]+\(\w*\)\s*\)')
    while match := extra.match(tail, position):
        names.append((match[1], match[1], 'Properties', map_end + 1 + match.start(1) - 1))
        position = match.end()
    return names if not tail[position:].strip() else None

def masked(s):
    pattern = '"""[\\s\\S]*?"""|"(?:\\\\.|[^"\\\\])*"|\\\'(?:\\\\.|[^\\\'\\\\])*\\\'|//[^\\n]*|/\\*[\\s\\S]*?\\*/'
    return re.sub(pattern, lambda m: re.sub('[^\\n]', ' ', m[0]) if m[0].startswith(('/', '/*')) else m[0], s)

def end_group(s, pos):
    pairs = {'(': ')', '[': ']', '{': '}'}
    stack = [pairs[s[pos]]]
    i = pos + 1
    while i < len(s):
        if s.startswith('"""', i):
            close = s.find('"""', i + 3)
            if close < 0:
                raise ValueError('Unclosed Kotlin raw string')
            i = close + 3
            continue
        c = s[i]
        if c in '"\'':
            q = c
            i += 1
            while i < len(s):
                if s[i] == '\\':
                    i += 2
                elif s[i] == q:
                    i += 1
                    break
                else:
                    i += 1
            continue
        if c in pairs:
            stack.append(pairs[c])
        elif c in ')]}':
            if not stack or c != stack.pop():
                raise ValueError((i, c))
            if not stack:
                return i
        i += 1
    raise ValueError('Unclosed group')

def entries(s, start, end):
    i = start
    prev = start
    while i < end:
        if s.startswith('"""', i):
            close = s.find('"""', i + 3)
            if close < 0:
                raise ValueError('Unclosed Kotlin raw string')
            i = close + 3
            continue
        if s[i] in '([{':
            i = end_group(s, i) + 1
            continue
        if s[i] in '"\'':
            q = s[i]
            i += 1
            while i < end:
                if s[i] == '\\':
                    i += 2
                elif s[i] == q:
                    i += 1
                    break
                else:
                    i += 1
            continue
        if s[i] == ',':
            if s[prev:i].strip():
                yield (prev, s[prev:i].strip())
            prev = i + 1
        i += 1
    if s[prev:end].strip():
        yield (prev, s[prev:end].strip())

def extract_kotlin(host):
    HOST = Path(host).resolve()
    BASE = HOST / 'app/src/main/java/org/autojs/autojs/runtime/api/augment'
    runtime = (HOST / 'app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt').read_text(encoding='utf-8')
    imports = {}
    for m in re.finditer('^import org\\.autojs\\.autojs\\.runtime\\.api\\.augment\\.([\\w.]+)(?: as (\\w+))?$', runtime, re.M):
        imports[m[2] or m[1].split('.')[-1]] = m[1]
    routes = []
    # Mount syntax is code, not strings or comments. Keep offsets for matching
    # a trailing also lambda to the object returned by its enclosing mount.
    mount_source = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                          lambda m: re.sub(r'[^\n]', ' ', m[0]), masked(runtime))
    scopes = []
    for m in re.finditer(r'(?<![\w.])(\w+)(?:\([^\n]*?\))?(?:\.also\s*\{[^\n]*?\})?\.(augmentWithRuntime|augment|assignWithRuntime|proxying)\((\w+)(?=\s*[,\)])', mount_source):
        if m[1] in imports:
            owner = next((index for start, end, alias, index in reversed(scopes)
                          if start < m.start() < end and alias == m[3]), None)
            routes.append((m[1], imports[m[1]], m[2], m[3], owner))
            call_open = mount_source.rfind('(', m.start(), m.end())
            call_end = end_group(mount_source, call_open)
            continuation = re.match(r'\s*\.also\s*\{\s*(?:(\w+)\s*->)?', mount_source[call_end + 1:])
            if continuation:
                start = call_end + 1 + continuation[0].index('{')
                scopes.append((start, end_group(mount_source, start), continuation[1] or 'it', len(routes) - 1))
    records = []
    modules = []
    unresolved = []
    mount_calls = list(re.finditer(r'\.(augmentWithRuntime|augment|assignWithRuntime|proxying)\(', mount_source))
    if len(mount_calls) != len(routes):
        unresolved.append({'reason': 'Unrecognized runtime mount syntax', 'mountCalls': len(mount_calls), 'parsedRoutes': len(routes)})
    route_modules = {}
    for route_index, (imported, qual, operation, target, owner) in enumerate(routes):
        rel = '/'.join(qual.split('.')[:-1])
        class_name = qual.split('.')[-1]
        candidates = list((BASE / rel).glob('*.kt'))
        path = next((p for p in candidates if re.search('\\b(?:class|object)\\s+' + class_name + '\\b', masked(p.read_text(encoding='utf-8')))), None)
        if path is None:
            unresolved.append({'class': qual, 'reason': 'source not found'})
            continue
        raw = path.read_text(encoding='utf-8')
        s = masked(raw)
        key_match = re.search('override val keys\\s*=\\s*listOf\\(', s)
        keys = []
        if key_match:
            st = key_match.end() - 1
            en = end_group(s, st)
            for km in re.finditer('AugmentableKey\\(([^)]+)\\)', s[st:en]):
                value = re.match('"([^"]+)"', km[1])
                if value:
                    name = value[1]
                else:
                    name = {'OcrMLKit': 'mlkit', 'OcrPaddle': 'paddle', 'OcrRapid': 'rapid'}[class_name]
                keys.append(name)
                if 'false' not in km[1]:
                    keys.append('$' + name)
        else:
            name = class_name[0].lower() + class_name[1:]
            keys = [name, '$' + name]
        if target == 'it' and owner is None:
            unresolved.append({'class': qual, 'reason': 'Unbound mount lambda receiver', 'target': target})
        parent = route_modules.get(owner, target)
        module = '' if operation == 'assignWithRuntime' else ('' if parent == 'target' else parent + '.') + keys[0]
        route_modules[route_index] = module
        modules.append({'module': module, 'class': qual, 'source': str(path), 'operation': operation, 'target': target, 'aliases': keys if module else [], 'callable': bool(re.search('\\bInvokable\\b', s.split('override val')[0]))})
        for decl in re.finditer('override val ((?:self|global)Assignment\\w+)', s):
            reg = decl[1]
            after = decl.end()
            boundary = re.search('\\n\\s*(?:override |companion |@|(?:private |internal |protected )?fun )', s[after:])
            limit = after + (boundary.start() if boundary else len(s) - after)
            # Two fixed table shapes (colour tables, Compose function map) without a listOf initializer.
            resolved, label = simple_name_table_names(s, s[after:limit]), 'simpleName'
            if resolved is None and module == 'compose' and reg == 'selfAssignmentProperties':
                resolved, label = compose_function_map_names(s, after, limit), 'map'
            if resolved is not None:
                loc = '' if reg.startswith('global') else module
                for name, implementation, kind, offset in resolved:
                    records.append({'api': loc + '.' + name if loc else name, 'module': loc, 'ownerModule': module, 'member': name, 'implementation': implementation, 'kind': kind or reg.split('Assignment')[1], 'registration': reg + '/' + label, 'source': str(path), 'line': s[:offset].count('\n') + 1})
                continue
            lm = re.search('listOf(?:<[^\\n]*>)?\\s*\\(', s[after:limit])
            if lm:
                start = after + lm.end() - 1
            elif 'FUNCTION_ASSIGNMENTS' in s[after:limit]:
                lm = re.search('val FUNCTION_ASSIGNMENTS\\s*=\\s*listOf\\(', s)
                start = lm.end() - 1
            else:
                unresolved.append({'module': module, 'source': str(path), 'registration': reg, 'line': s[:decl.start()].count('\n') + 1, 'expression': s[after:limit].strip()[:180]})
                continue
            end = end_group(s, start)
            prefix = s[after:start + 1]
            lazy = 'by lazy' in prefix
            constant_assignment = 'FUNCTION_ASSIGNMENTS' in s[after:limit] and start > limit
            forwarded = mail_forwarded_names(s, end, limit) if module == 'mail' and reg == 'selfAssignmentProperties' else None
            if not constant_assignment:
                expected_prefix = r'\s*(?::[^\n=]+)?\s*(?:=\s*|by\s+lazy\s*\{\s*)listOf(?:<[^\n]*>)?\s*\('
                if re.fullmatch(expected_prefix, prefix) is None or (not initializer_tail_is_supported(s, end, limit, lazy) and forwarded is None):
                    unresolved.append({'module': module, 'registration': reg, 'line': s[:decl.start()].count('\n') + 1, 'reason': 'Unparsed registration initializer prefix or suffix'})
            elif not re.fullmatch(r'\s*=\s*FUNCTION_ASSIGNMENTS\s*', s[after:limit]) or not initializer_tail_is_supported(s, end, len(s), False):
                unresolved.append({'module': module, 'registration': reg, 'reason': 'Unparsed assignment constant expression'})
            for name in forwarded or []:
                records.append({'api': module + '.' + name, 'module': module, 'ownerModule': module, 'member': name, 'implementation': name, 'kind': 'Functions', 'registration': 'selfAssignmentProperties/forwarded', 'source': str(path), 'line': s[:decl.start()].count('\n') + 1})
            for offset, expr in entries(s, start + 1, end):
                line = s[:offset].count('\n') + 1
                leading = len(s[offset:end]) - len(s[offset:end].lstrip())
                line = s[:offset + leading].count('\n') + 1
                if reg.endswith('Functions') and not function_assignment_is_supported(expr):
                    unresolved.append({'module': module, 'registration': reg, 'expression': expr, 'line': line, 'reason': 'Unparsed function registration or alias expression'})
                    continue
                fn = re.match('(?:\\w+)?::(`?\\w+`?)\\.name', expr)
                lit = re.match('\\(?"([^"\\n]+)"', expr)
                propref = re.match('(\\w+)\\.NAME', expr)
                triple = re.match('Triple\\(\\s*"([^"]+)"', expr)
                if fn:
                    implementation = fn[1].strip('`')
                    aliasExpr = expr[fn.end():]
                    aliases = []
                    for am in re.finditer('"([^"\\n]+)"|(?:\\w+)?::(`?\\w+`?)\\.name(\\.lowercase\\(\\))?', aliasExpr):
                        an = (am[1] or am[2]).strip('`')
                        aliases.append(an.lower() if am[3] else an)
                    names = aliases or [implementation]
                elif lit:
                    implementation = lit[1]
                    names = [implementation]
                    if reg.endswith('Functions'):
                        aliases = re.findall('"([^"\\n]+)"', expr)[1:]
                        names = aliases or names
                elif triple:
                    implementation = triple[1]
                    names = [implementation]
                elif propref:
                    ep = next(BASE.rglob(propref[1] + '.kt'), None)
                    nm = re.search('const val NAME\\s*=\\s*"([^"]+)"', ep.read_text(encoding='utf-8')) if ep else None
                    if not nm:
                        unresolved.append({'module': module, 'registration': reg, 'expression': expr[:200], 'line': line})
                        continue
                    implementation = nm[1]
                    names = [implementation]
                else:
                    constant = re.match('(\\w+) to', expr)
                    cmatch = re.search('const val ' + constant[1] + '\\s*=\\s*"([^"]+)"', s) if constant else None
                    if cmatch:
                        implementation = cmatch[1]
                        names = [implementation]
                    else:
                        unresolved.append({'module': module, 'registration': reg, 'expression': expr[:200], 'line': line})
                        continue
                locations = []
                if 'AS_IGNORED' not in expr:
                    locations.append('' if reg.startswith('global') else module)
                if 'AS_GLOBAL' in expr:
                    locations.append('')
                for loc in set(locations):
                    for name in names:
                        records.append({'api': loc + '.' + name if loc else name, 'module': loc, 'ownerModule': module, 'member': name, 'implementation': implementation, 'kind': reg.split('Assignment')[1], 'registration': reg, 'source': str(path), 'line': line})
    if 'augmentedApp.defineProp' in runtime:
        records.append({'api': 'app.autojs', 'module': 'app', 'ownerModule': 'app', 'member': 'autojs', 'implementation': 'autojs', 'kind': 'Properties', 'registration': 'ScriptRuntime.defineProp', 'source': str(HOST / 'app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt'), 'line': runtime[:runtime.index('augmentedApp.defineProp')].count('\n') + 1})
    unique = {r['api']: r for r in records}
    result = {'scope': 'Explicit Augmentable registrations plus explicit Compose function map and colour table names. Excludes inherited Java prototype members, returned objects, nested dynamic objects, colour constants, generated Compose catalogue, JSOX prototype extensions, module alias expansion, overload semantics.', 'modules': modules, 'records': sorted(unique.values(), key=lambda x: x['api']), 'unresolved': unresolved}
    for record in result['records'] + result['modules'] + result['unresolved']:
        if 'source' in record:
            record['source'] = Path(record['source']).relative_to(HOST).as_posix()
    return result


def split_source(location):
    """`path:line` of script-modules.json as (path, line); line is None without one."""
    path, _, line = (location or '').rpartition(':')
    return (path, int(line)) if path and line.isdigit() else (location, None)


def placements(path, member):
    """The objects a member lands on: its module unless global ONLY or built-in only, and the global object for ALSO / ONLY."""
    placement = member.get('global', 'NONE')
    locations = [path] if placement != 'ONLY' and not member.get('builtInOnly', False) else []
    if placement in ('ALSO', 'ONLY') and '' not in locations:
        locations.append('')
    return locations


def metadata_is_stale(host, metadata):
    """True when a module or processor source is newer than the metadata file (a KSP build is due)."""
    built = metadata.stat().st_mtime
    for root in METADATA_INPUTS:
        for directory, _, files in os.walk(host / root):
            if any(name.endswith('.kt') and os.path.getmtime(os.path.join(directory, name)) > built for name in files):
                return True
    return False


def extract_ksp(host, metadata):
    """Exports declared in script-modules.json, in the shape of extract_kotlin (records, modules, unresolved)."""
    HOST = Path(host).resolve()
    meta = json.loads(Path(metadata).read_text(encoding='utf-8'))
    if meta.get('format') != 1:
        raise ValueError(f'Unsupported script-modules.json format: {meta.get("format")}')
    by_class = {m['class']: m for m in meta['modules']}

    def module_path(m):
        if m['parent']:
            return module_path(by_class[m['parent']]) + '.' + m['name']
        return '' if m['assign'] else m['name']

    records, modules, unresolved, fallback, computed = [], [], [], [], []
    for m in meta['modules']:
        path = module_path(m)
        source, line = split_source(m['source'])
        modules.append({'module': path, 'class': m['class'], 'source': source, 'line': line,
                        'operation': 'assign' if m['assign'] else 'proxy' if m['proxy'] else 'mount',
                        'target': module_path(by_class[m['parent']]) if m['parent'] else 'global',
                        'aliases': [] if m['assign'] else m['names'], 'callable': m['invoke'] is not None,
                        'constructable': m['construct'] is not None, 'metadata': 'ksp'})
        members = []
        for f in m['functions']:
            overload = f['overloads'][0]
            for name in [f['name']] + f['aliases']:
                members.append((f, name, overload['kotlinName'], 'Functions', '@ScriptFunction', overload['source']))
        for a in m['accessors']:
            kind = 'Properties' if a['kind'] == 'property' else 'GettersAndSetters' if a['writable'] else 'Getters'
            members.append((a, a['name'], a['name'], kind, '@ScriptProperty' if a['kind'] == 'property' else '@ScriptGetter', a['source']))
        for j in m['javaClasses']:
            members.append((j, j['name'], j['type'], 'JavaClasses', '@ScriptJavaClass', m['source']))
        # The "installs" list declares the names a @ScriptInstall hook defines: {name, kind: function | getter |
        # property, global, attributes, target, source}; a name with a target goes on the object bound to that name on
        # the global object (wait.async), reached from the global object (module ""). Names the hook computes at run time
        # are only described ("installsComputed"). Metadata without the list falls back to the Kotlin registrations below.
        for i in m.get('installs', []):
            kind = {'function': 'Functions', 'getter': 'Getters', 'property': 'Properties'}[i['kind']]
            members.append((i, i['name'], i['name'], kind, '@ScriptInstall', i.get('source') or m['source']))
        if m.get('installsComputed'):
            computed.append({'module': path, 'computed': m['installsComputed']})
        if m['install'] and 'installs' not in m:
            fallback.append(path)
        for member, name, implementation, kind, registration, location in members:
            member_source, member_line = split_source(location)
            if member.get('target'):
                records.append({'api': member['target'] + '.' + name, 'module': '', 'ownerModule': path, 'member': name,
                                'implementation': implementation, 'kind': kind, 'registration': registration,
                                'source': member_source, 'line': member_line, 'metadata': 'ksp'})
                continue
            for loc in placements(path, member):
                records.append({'api': loc + '.' + name if loc else name, 'module': loc, 'ownerModule': path, 'member': name,
                                'implementation': implementation, 'kind': kind, 'registration': registration,
                                'source': member_source, 'line': member_line, 'metadata': 'ksp'})
    # A name exported by several modules (isObject: global and species) belongs to the module that is not an assign
    # module: assign modules are mounted first, so the other one wins at run time.
    unique = {}
    for record in records:
        kept = unique.get(record['api'])
        if kept is None or (record['ownerModule'] and not kept['ownerModule']):
            unique[record['api']] = record
    if fallback:
        try:
            kotlin = extract_kotlin(HOST)
        except (OSError, ValueError, KeyError, StopIteration) as error:
            kotlin = {'records': [], 'unresolved': [{'reason': f'Kotlin registrations unavailable: {error}'}]}
        for record in kotlin['records']:
            if record['ownerModule'] in fallback and record['api'] not in unique:
                unique[record['api']] = dict(record, metadata='kotlin')
        # Mount and source errors (no module) mean the fallback itself is incomplete.
        unresolved += [item for item in kotlin['unresolved'] if item.get('module', fallback[0]) in fallback]
    return {'scope': 'Modules, functions with their aliases, getters, properties and Java classes declared in script-modules.json (KSP), placed on their module and / or the global object; names defined by @ScriptInstall hooks from the module "installs" list (without one, from the Kotlin registrations, metadata kotlin). Excludes inherited Java prototype members, returned objects, nested dynamic objects, names install hooks compute at run time (dataSource installComputed: the global selector functions, the Compose catalogue), JSOX built-in extensions, overload semantics.',
            'dataSource': {'kind': 'ksp', 'file': Path(metadata).resolve().relative_to(HOST).as_posix() if Path(metadata).resolve().is_relative_to(HOST) else str(metadata),
                           'format': meta['format'], 'installFallback': fallback, 'installComputed': computed},
            'modules': modules, 'records': sorted(unique.values(), key=lambda x: x['api']), 'unresolved': unresolved}


def extract(host, metadata=None, source='auto'):
    """Exports of `source` ksp (script-modules.json), kotlin (registration parser), or auto: ksp when the file exists.

    Functions and callable modules carry their argument-count ranges (arity.py, api-coverage P0.3); the returned
    objects and their members are in "returnedObjects" (returned_objects.py, P0.4)."""
    from arity import attach as attach_arity
    from returned_objects import attach as attach_returned
    HOST = Path(host).resolve()
    metadata = Path(metadata) if metadata else HOST / DEFAULT_METADATA
    if source == 'ksp' or (source == 'auto' and metadata.is_file()):
        if not metadata.is_file():
            raise FileNotFoundError(f'{metadata} not found; build it with gradlew :app:kspAppDebugKotlin')
        if metadata_is_stale(HOST, metadata):
            print(f'warning: {metadata} is older than the module sources; run gradlew :app:kspAppDebugKotlin', file=sys.stderr)
        return attach_returned(HOST, attach_arity(HOST, extract_ksp(HOST, metadata), metadata), metadata)
    if source == 'auto':
        print(f'warning: {metadata} not found, reading the Kotlin registrations (run gradlew :app:kspAppDebugKotlin)', file=sys.stderr)
    result = extract_kotlin(HOST)
    for item in result['records'] + result['modules']:
        item['metadata'] = 'kotlin'
    result['dataSource'] = {'kind': 'kotlin'}
    return attach_returned(HOST, attach_arity(HOST, result))


def compare(ksp, kotlin):
    """Differences of the two sources by api (COMPARED_FIELDS) and by module (aliases, callable)."""
    a = {r['api']: r for r in ksp['records']}
    b = {r['api']: r for r in kotlin['records']}
    pick = lambda r: {field: r[field] for field in COMPARED_FIELDS}
    modules_a = {m['module']: m for m in ksp['modules']}
    modules_b = {m['module']: m for m in kotlin['modules']}
    return {
        'records': {'ksp': len(a), 'kotlin': len(b), 'kspFromKotlin': sum(r.get('metadata') == 'kotlin' for r in a.values())},
        'onlyKsp': [pick(a[api]) | {'api': api} for api in sorted(set(a) - set(b))],
        'onlyKotlin': [pick(b[api]) | {'api': api, 'registration': b[api]['registration']} for api in sorted(set(b) - set(a))],
        'changed': [{'api': api, 'ksp': pick(a[api]), 'kotlin': pick(b[api])} for api in sorted(set(a) & set(b)) if pick(a[api]) != pick(b[api])],
        'modules': {'ksp': len(ksp['modules']), 'kotlin': len(kotlin['modules']),
                    'onlyKsp': sorted(set(modules_a) - set(modules_b)), 'onlyKotlin': sorted(set(modules_b) - set(modules_a)),
                    'changed': [{'module': name, 'ksp': {k: modules_a[name][k] for k in ['aliases', 'callable']}, 'kotlin': {k: modules_b[name][k] for k in ['aliases', 'callable']}}
                                for name in sorted(set(modules_a) & set(modules_b))
                                if (modules_a[name]['aliases'], modules_a[name]['callable']) != (modules_b[name]['aliases'], modules_b[name]['callable'])]},
        'unresolved': {'ksp': ksp['unresolved'], 'kotlin': kotlin['unresolved']},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--host', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--metadata', type=Path, help=f'script-modules.json (default: <host>/{DEFAULT_METADATA})')
    parser.add_argument('--source', choices=['auto', 'ksp', 'kotlin'], default='auto', help='auto reads script-modules.json when it exists')
    parser.add_argument('--compare-kotlin', action='store_true', help='Write the differences between script-modules.json and the Kotlin registrations')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.compare_kotlin:
        result = compare(extract(args.host, args.metadata, 'ksp'), extract(args.host, source='kotlin'))
    else:
        result = extract(args.host, args.metadata, args.source)
    serialized = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding='utf-8')
    else:
        print(serialized, end='')
if __name__ == '__main__':
    main()
