#!/usr/bin/env python3
"""Extract explicit Rhino Augmentable exports without treating JVM helpers as APIs.

This is deliberately a registration parser, not a Kotlin compiler. Unknown
registration expressions are returned for review and rejected by the gate.
Java prototypes, generated catalogues and returned objects require separate
contract probes; they must not be silently counted as complete here.
"""
import argparse
import json
from pathlib import Path
import re

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

def extract(host):
    HOST = Path(host).resolve()
    BASE = HOST / 'app/src/main/java/org/autojs/autojs/runtime/api/augment'
    runtime = (HOST / 'app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt').read_text(encoding='utf-8')
    imports = {}
    for m in re.finditer('^import org\\.autojs\\.autojs\\.runtime\\.api\\.augment\\.([\\w.]+)(?: as (\\w+))?$', runtime, re.M):
        imports[m[2] or m[1].split('.')[-1]] = m[1]
    routes = []
    for m in re.finditer('^\\s*(\\w+)(?:\\([^\\n]*?\\))?(?:\\.also\\s*\\{[^\\n]*?\\})?\\.(augmentWithRuntime|augment|assignWithRuntime|proxying)\\((\\w+)', runtime, re.M):
        if m[1] in imports:
            routes.append((m[1], imports[m[1]], m[2], m[3]))
    records = []
    modules = []
    unresolved = []
    mount_calls = list(re.finditer(r'\.(augmentWithRuntime|augment|assignWithRuntime|proxying)\(', masked(runtime)))
    if len(mount_calls) != len(routes):
        unresolved.append({'reason': 'Unrecognized runtime mount syntax', 'mountCalls': len(mount_calls), 'parsedRoutes': len(routes)})
    for imported, qual, operation, target in routes:
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
        module = '' if operation == 'assignWithRuntime' else ('' if target == 'target' else target + '.') + keys[0]
        modules.append({'module': module, 'class': qual, 'source': str(path), 'operation': operation, 'target': target, 'aliases': keys if module else [], 'callable': bool(re.search('\\bInvokable\\b', s.split('override val')[0]))})
        for decl in re.finditer('override val ((?:self|global)Assignment\\w+)', s):
            reg = decl[1]
            after = decl.end()
            boundary = re.search('\\n\\s*(?:override |companion |@|(?:private |internal |protected )?fun )', s[after:])
            limit = after + (boundary.start() if boundary else len(s) - after)
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
    # Explicit keys of Compose's function map are stable API names; catalogue
    # components are validated separately by the Compose contract checks.
    if any(module['module'] == 'compose' for module in modules):
        cp = BASE / 'compose/Compose.kt'
        cs = masked(cp.read_text(encoding='utf-8'))
        cm = re.search('val functions = linkedMapOf[^\\n]*\\(', cs)
        if cm is None:
            raise ValueError('Compose registration changed; review its dynamic exports')
        for off, expr in entries(cs, cm.end(), end_group(cs, cm.end() - 1)):
            mm = re.match('"(\\w+)" to', expr)
            if mm:
                records.append({'api': 'compose.' + mm[1], 'module': 'compose', 'ownerModule': 'compose', 'member': mm[1], 'implementation': mm[1], 'kind': 'Functions', 'registration': 'selfAssignmentProperties/map', 'source': str(cp), 'line': cs[:off].count('\n') + 2})
        records.append({'api': 'compose.ComposeError', 'module': 'compose', 'ownerModule': 'compose', 'member': 'ComposeError', 'implementation': 'ComposeError', 'kind': 'Properties', 'registration': 'selfAssignmentProperties/map', 'source': str(cp), 'line': cs[:cs.index('+ ("ComposeError"')].count('\n') + 1})
    if 'augmentedApp.defineProp' in runtime:
        records.append({'api': 'app.autojs', 'module': 'app', 'ownerModule': 'app', 'member': 'autojs', 'implementation': 'autojs', 'kind': 'Properties', 'registration': 'ScriptRuntime.defineProp', 'source': str(HOST / 'app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt'), 'line': runtime[:runtime.index('augmentedApp.defineProp')].count('\n') + 1})
    unique = {r['api']: r for r in records}
    result = {'scope': 'Explicit Augmentable registrations plus explicit Compose function map. Excludes inherited Java prototype members, returned objects, nested dynamic objects, colour constants, generated Compose catalogue, JSOX prototype extensions, module alias expansion, overload semantics.', 'modules': modules, 'records': sorted(unique.values(), key=lambda x: x['api']), 'unresolved': unresolved}
    for record in result['records'] + result['modules'] + result['unresolved']:
        if 'source' in record:
            record['source'] = Path(record['source']).relative_to(HOST).as_posix()
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = extract(args.host)
    serialized = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding='utf-8')
    else:
        print(serialized, end='')
if __name__ == '__main__':
    main()
