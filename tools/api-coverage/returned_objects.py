#!/usr/bin/env python3
"""Objects that script functions return, with the members scripts reach on them (api-coverage P0.4).

returned-objects.json lists the objects (UiObject, ImageWrapper, Flow, ShellProcess, ...): the class, the
member list source and the documentation pages. Producers are the export rows whose script-modules.json
overloads return the class, plus the producers the file names (functions declared as returning kotlin.Any).
Member list sources:

- kotlin-class: a Java object scripts see through Rhino's NativeJavaObject: the public methods, fields and
  properties (as their name, with the JVM accessors as aliases) declared by the class and its supertypes in
  the host sources; supertypes outside the sources (Android, androidx, Java) are listed, their members are not.
- augment-prototype: a Scriptable built by the runtime: the names it registers with string literals
  (defineProperty, defineFunction, function / method helpers with their aliases, defineOp with its range), the
  name arrays passed to defineFunctionProperties or named in the file, and collections iterated by
  `X.forEach { name -> helper(name, ...) }` when X resolves to a literal list or map in the sources; other
  computed registrations are listed in "dynamic" and the member list is marked incomplete.
- policy: the members the file lists.
- plugin:<name>: the members a plugin contract lists (policy.json pluginExports, plugin_exports.py; P5.2c), the list
  named by `catalog` (the Compose catalogue modifiers of ComposeModifier, the shortcuts of ComposeNode);
  `catalogProducers` makes every row the source adds a producer, `members` adds the names the object defines itself (the
  properties and operations of ComposeNode, Q106 (a)). Without the contract checkout the object is skipped.

The name-level gate (gate()) checks every member against the object's documentation pages, the d.ts type of
a probe expression (`<probe>.<member>`) and Ace completion, in a separate surface run whose failures are
reported apart from the export gate (P2.3 consumes them).

The "prototypes" list of returned-objects.json (api-coverage Q18 (a)) names the prototype of every module declaring
@ScriptPrototype: scripts call its members as module members (`files.isFile`), so they become rows `<module>.<member>`
with memberSource "script-prototype", looked up in the runtimes like explicit exports. The member list comes from the
same sources (kotlin-class, augment-prototype, policy); names the module object defines itself shadow the prototype and
are left out, and constant tables or Java class proxies are described ("notEnumerated") instead of listed.
"""
import json
from pathlib import Path
import re
import subprocess

from extract_exports import end_group, masked

HERE = Path(__file__).resolve().parent
CONFIG = HERE / 'returned-objects.json'
SOURCE_ROOT = 'app/src/main/java'
OBJECT_MEMBERS = {'equals', 'hashCode', 'toString', 'getClass', 'notify', 'notifyAll', 'wait', 'finalize', 'clone'}
SCRIPTABLE_BASES = {'ScriptableObject', 'NativeObject', 'IdScriptableObject', 'BaseFunction', 'NativeFunction', 'Scriptable',
                    'NativeArray', 'ImporterTopLevel', 'TopLevel'}
HELPERS = {'function': 'Functions', 'method': 'Functions', 'defineFunction': 'Functions', 'defineOp': 'Functions',
           'listenerFunction': 'Functions', 'defineFlagShortcut': 'Functions', 'defineMethod': 'Functions',
           'defineProperty': None, 'defineGetter': 'Getters'}
STRING = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'')
KOTLIN_MODIFIERS = r'(?:(?:public|private|protected|internal|open|override|final|abstract|inline|operator|infix|suspend|tailrec|external|lateinit|const|actual|expect)\s+)*'
KOTLIN_MEMBER = re.compile(r'(?m)^[ \t]*((?:@[\w.]+(?:\([^()\n]*\))?\s+)*)(' + KOTLIN_MODIFIERS + r')(fun|val|var)\s+(?:<[^>\n]*>\s*)?(?:[\w.]+(?:<[^>\n]*>)?\??\.)?`?(\w+)`?')
JAVA_MEMBER = re.compile(r'(?m)^[ \t]*((?:@\w+(?:\([^()\n]*\))?\s+)*)((?:(?:public|protected|private|static|final|synchronized|abstract|native|default|strictfp|transient|volatile)\s+)*)(?:<[^>\n]+>\s+)?([\w.<>\[\]?, ]+?)\s+(\w+)\s*(\(|=|;)')


def blank(text):
    """Comments and string contents replaced by spaces (offsets kept): braces in them do not count."""
    return STRING.sub(lambda m: m[0][0] + re.sub(r'[^\n]', ' ', m[0][1:-1]) + m[0][-1], masked(text))


def line_of(text, offset):
    return text.count('\n', 0, offset) + 1


class Sources:
    """Host Kotlin / Java sources: files by class name, imports, class bodies and literal constants (cached)."""

    def __init__(self, host):
        self.host = Path(host)
        self.root = self.host / SOURCE_ROOT
        self.texts = {}
        self.all_files = None

    def text(self, path):
        if path not in self.texts:
            raw = path.read_text(encoding='utf-8')
            self.texts[path] = (raw, masked(raw), blank(raw))
        return self.texts[path]

    def files(self):
        if self.all_files is None:
            self.all_files = sorted(p for ext in ('*.kt', '*.java') for p in self.root.rglob(ext))
        return self.all_files

    def relative(self, path):
        return path.relative_to(self.host).as_posix()

    def declaration(self, path, name, within=None):
        """(kind, start, header end, body start or None, body end) of `class|interface|object|enum name` in path."""
        _, _, code = self.text(path)
        lo, hi = within or (0, len(code))
        for m in re.finditer(r'\b(class|interface|object|enum|record)\s+' + re.escape(name) + r'\b', code[lo:hi]):
            start = lo + m.start()
            if code[lo:start].rstrip().endswith(('companion', 'fun')):
                continue
            i, depth = lo + m.end(), 0
            while i < hi:
                c = code[i]
                if code.startswith('->', i):
                    i += 2
                    continue
                if c in '(<[':
                    depth += 1
                elif c in ')>]':
                    depth -= 1
                elif depth <= 0 and c == '{':
                    return m[1], start, i, i, end_group(code, i)
                elif depth <= 0 and c in ';\n' and not code[i + 1:hi].lstrip().startswith((':', '{', ',', 'extends', 'implements', 'where')) \
                        and not code[lo + m.end():i].rstrip().endswith((':', ',')):
                    if c == ';' or re.match(r'\s*(?:@|(?:(?:private|public|internal|data|open|abstract|sealed|enum|inner)\s+)*(?:class|interface|object|fun|val|var|enum|\}))', code[i:hi]):
                        return m[1], start, i, None, i
                i += 1
            return m[1], start, hi, None, hi
        return None

    def locate(self, fqn):
        """(path, class names from the top-level class down) of a fully qualified class name, or None."""
        parts = fqn.split('.')
        for i in range(len(parts), 0, -1):
            base = self.root.joinpath(*parts[:i])
            for ext in ('.kt', '.java'):
                if base.with_suffix(ext).is_file():
                    return base.with_suffix(ext), parts[i - 1:]
        for j in range(len(parts) - 1, 0, -1):
            package = self.root.joinpath(*parts[:j])
            if package.is_dir():
                for path in sorted(package.glob('*.kt')) + sorted(package.glob('*.java')):
                    if self.declaration(path, parts[j]):
                        return path, parts[j:]
                return None
        return None

    def class_region(self, fqn):
        """(path, kind, start, header end, body start, body end) of a class; nested names walk the outer bodies."""
        found = self.locate(fqn)
        if not found:
            return None
        path, chain = found
        within = None
        result = None
        for name in chain:
            result = self.declaration(path, name, within)
            if not result:
                return None
            within = (result[3] + 1 if result[3] is not None else result[2], result[4])
        return (path,) + result

    def imports(self, path):
        raw, _, _ = self.text(path)
        table = {}
        for m in re.finditer(r'(?m)^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*;?\s*$', raw):
            table[m[2] or m[1].split('.')[-1]] = m[1]
        package = re.search(r'(?m)^package\s+([\w.]+)', raw)
        return table, package[1] if package else ''

    def resolve_type(self, path, name):
        """Fully qualified name of a type written in path, or the name itself when it is not a host class."""
        name = re.sub(r'<.*', '', name).strip().rstrip('?')
        head = name.split('.')[0]
        table, package = self.imports(path)
        if head in table:
            return table[head] + name[len(head):]
        if '.' in name and self.locate(name):
            return name
        candidate = (package + '.' if package else '') + name
        if self.locate(candidate):
            return candidate
        outer = path.stem
        if head != outer and self.declaration(path, head):
            return (package + '.' if package else '') + outer + '.' + name
        return name

    def constant(self, name, hint=None, only=False):
        """Value of `const val name = "..."`, `val name = Owner::member.name` or a Java `static final String name = "..."`,
        from the hinted file first (only there when `only`)."""
        files = ([hint] if hint else []) + ([] if only else self.files())
        pattern = re.compile(r'(?:(?:const\s+)?val|static\s+final\s+String)\s+' + re.escape(name) + r'\s*(?::\s*String\s*)?=\s*(?:"([^"\\\n]*)"|(?:\w+)?::`?(\w+)`?\.name\b)')
        for path in files:
            raw = self.text(path)[0]
            if name in raw:
                m = pattern.search(raw)
                if m:
                    return m[1] if m[1] is not None else m[2]
        return None

    def literal_names(self, expression, hint=None):
        """Names of a literal collection `[Owner.]NAME[.keys]`: listOf / setOf / arrayOf entries or mapOf keys. None if unresolved."""
        m = re.fullmatch(r'(?:(\w+)\.)?(\w+)(?:\.keys)?', expression.strip())
        if not m:
            return None
        owner, name = m[1], m[2]
        decl = re.compile(r'\bval\s+' + re.escape(name) + r'\b[^=\n{]*(?:=|\bby\s+lazy\s*\{)\s*(?:\w+\s*\{\s*)?(?:listOf|setOf|arrayOf|mapOf|linkedMapOf|hashMapOf|mutableListOf|linkedSetOf)(?:<[^>\n]*>)?\(')
        candidates = []
        for path in ([hint] if hint else []) + self.files():
            raw, mk, code = self.text(path)
            if name not in raw:
                continue
            for d in decl.finditer(mk):
                if owner and not re.search(r'\b(?:object|class|interface)\s+' + owner + r'\b', mk) and path.stem != owner:
                    continue
                candidates.append((path, d))
            if candidates and path == hint:
                break
        if not candidates:
            return None
        path, d = candidates[0]
        raw, mk, code = self.text(path)
        start = d.end() - 1
        names = []
        for offset, entry in _entries(mk, code, start + 1, end_group(code, start)):
            key = entry.split(' to ')[0].strip() if ' to ' in entry else entry.strip()
            literal = re.fullmatch(r'"([^"\\\n]*)"', key) or re.fullmatch(r'(?:\w+)?::`?(\w+)`?\.name', key)
            reference = re.fullmatch(r'(?:([\w.]+)\.)?(\w+)', key)
            if literal:
                names.append(literal[1])
            elif reference:
                hint_path = None
                if reference[1]:
                    located = self.locate(self.resolve_type(path, reference[1]))
                    hint_path = located[0] if located else None
                value = self.constant(reference[2], hint_path or path)
                if value is None:
                    return None
                names.append(value)
            else:
                return None
        return names


def _entries(mk, code, start, end):
    """Comma-separated entries of mk[start:end] at nesting depth 0 (depth read from code, strings blanked)."""
    i, prev = start, start
    while i < end:
        if code[i] in '([{':
            i = end_group(code, i) + 1
            continue
        if code[i] == ',':
            if mk[prev:i].strip():
                yield prev, mk[prev:i].strip()
            prev = i + 1
        i += 1
    if mk[prev:end].strip():
        yield prev, mk[prev:end].strip()


def member_depth_matches(pattern, mk, code, body_start, body_end, group=3):
    """Matches of pattern whose group starts directly in the body (not in nested functions, classes or initializers, nor in
    the primary constructor of a nested class without a body)."""
    spans, i = [], body_start + 1
    while i < body_end:
        if code[i] in '{(':
            spans.append((i, end_group(code, i)))
            i = spans[-1][1] + 1
            continue
        i += 1
    for m in pattern.finditer(mk, body_start + 1, body_end):
        if not any(s < m.start(group) < e for s, e in spans):
            yield m


def annotations_before(mk, offset):
    """Annotation names on the lines right above offset (and on its own line)."""
    names = []
    line_start = mk.rfind('\n', 0, offset) + 1
    names += re.findall(r'@([\w.]+)', mk[line_start:offset])
    pos = line_start - 1
    while pos > 0:
        prev_start = mk.rfind('\n', 0, pos) + 1
        line = mk[prev_start:pos].strip()
        if not line.startswith('@'):
            break
        names += re.findall(r'@([\w.]+)', line)
        pos = prev_start - 1
    return names


def kotlin_params(mk, code, open_paren):
    """[(name, optional, vararg)] of a Kotlin parameter list starting at open_paren."""
    close = end_group(code, open_paren)
    params = []
    for _, entry in _entries(mk, code, open_paren + 1, close):
        entry = re.sub(r'@[\w.]+(?:\([^()]*\))?\s*', '', entry)
        m = re.match(r'(?:(?:vararg|noinline|crossinline|private|public|internal|protected|override|open|val|var)\s+)*`?(\w+)`?\s*:', entry)
        if m:
            params.append((m[1], '=' in entry.split(':', 1)[1] and not entry.split(':', 1)[1].strip().startswith('='), bool(re.match(r'(?:\w+\s+)*vararg\b', entry))))
    return params


def kotlin_members(sources, path, region):
    _, kind, start, header_end, body_start, body_end = region
    raw, mk, code = sources.text(path)
    members = []
    header = mk[start:header_end]
    # Properties of the primary constructor.
    ctor = re.search(r'\b(?:class)\s+\w+\s*(?:<[^{(]*?>)?\s*(?:(?:private|internal|protected|public)?\s*(?:@\w+\s*)*constructor\s*)?\(', mk[start:header_end])
    if ctor:
        open_paren = start + ctor.end() - 1
        for offset, entry in _entries(mk, code, open_paren + 1, end_group(code, open_paren)):
            m = re.match(r'(?:@[\w.]+(?:\([^()]*\))?\s*)*((?:(?:private|public|internal|protected|override|open)\s+)*)(val|var)\s+`?(\w+)`?', entry)
            if m and not re.search(r'\b(?:private|protected|internal)\b', m[1]):
                field = 'JvmField' in entry
                members.append(property_member(m[3], m[2], field, sources.relative(path), line_of(mk, offset)))
    if body_start is None:
        return members, header
    for m in member_depth_matches(KOTLIN_MEMBER, mk, code, body_start, body_end):
        modifiers = m[2]
        # Also in an interface: a private fun with a body (UiObjectActions.performTooltipAction) is private on the JVM.
        if re.search(r'\b(?:private|protected|internal)\b', modifiers):
            continue
        annotations = annotations_before(mk, m.start(3)) + re.findall(r'@([\w.]+)', m[1])
        name, keyword = m[4], m[3]
        line = line_of(mk, m.start(3))
        if 'JvmSynthetic' in annotations:
            continue
        if keyword == 'fun':
            jvm_name = re.search(r'@JvmName\(\s*"(\w+)"', mk[max(0, m.start() - 200):m.start(3)])
            open_paren = code.find('(', m.end())
            params = kotlin_params(mk, code, open_paren) if open_paren >= 0 else []
            vararg = any(p[2] for p in params)
            required = sum(1 for p in params if not p[1] and not p[2])
            total = len([p for p in params if not p[2]])
            low = required if 'JvmOverloads' in annotations else total
            members.append({'name': jvm_name[1] if jvm_name else name, 'kind': 'Functions', 'source': sources.relative(path), 'line': line,
                            'arity': {'source': 'kotlin-class', 'overloads': [{'min': low, 'max': None if vararg else total, 'basis': 'params'}]}})
        else:
            setter = keyword == 'var' and not re.search(r'\b(?:private|protected|internal)\s+set\b', accessor_lines(mk, m.start(3)))
            members.append(property_member(name, 'var' if setter else 'val', 'JvmField' in annotations or 'const' in modifiers, sources.relative(path), line))
    return members, header


def accessor_lines(mk, offset):
    """The lines after a property declaration that are indented deeper (its getter / setter), up to a blank line."""
    start = mk.rfind('\n', 0, offset) + 1
    indent = len(mk[start:offset]) - len(mk[start:offset].lstrip())
    lines = []
    for line in mk[offset:].split('\n')[1:]:
        if not line.strip() or len(line) - len(line.lstrip()) <= indent:
            break
        lines.append(line)
    return '\n'.join(lines)


def property_member(name, keyword, field, source, line):
    if field:
        return {'name': name, 'kind': 'Properties', 'source': source, 'line': line, 'arity': None}
    base = name[2:] if re.match(r'is[A-Z]', name) else name
    getter = name if re.match(r'is[A-Z]', name) else 'get' + name[0].upper() + name[1:]
    aliases = [getter] + (['set' + base[0].upper() + base[1:]] if keyword == 'var' else [])
    if base != name:
        aliases.append(base[0].lower() + base[1:])
    return {'name': name, 'kind': 'GettersAndSetters' if keyword == 'var' else 'Getters', 'source': source, 'line': line, 'arity': None, 'aliases': aliases}


def java_members(sources, path, region):
    _, kind, start, header_end, body_start, body_end = region
    raw, mk, code = sources.text(path)
    members = []
    header = mk[start:header_end]
    class_name = re.search(r'\b(?:class|interface|enum|record)\s+(\w+)', header)[1]
    if kind == 'record':
        components = re.search(r'\brecord\s+\w+\s*(?:<[^>]*>)?\s*\(([^)]*)\)', header)
        for component in (components[1].split(',') if components and components[1].strip() else []):
            name = component.split()[-1]
            members.append({'name': name, 'kind': 'Functions', 'source': sources.relative(path), 'line': line_of(mk, start),
                            'arity': {'source': 'kotlin-class', 'overloads': [{'min': 0, 'max': 0, 'basis': 'params'}]}})
    if body_start is None:
        return members, header
    for m in member_depth_matches(JAVA_MEMBER, mk, code, body_start, body_end, group=4):
        modifiers, type_name, name, after = m[2], m[3].strip(), m[4], m[5]
        if not ('public' in modifiers or (kind == 'interface' and 'private' not in modifiers)):
            continue
        if type_name in ('return', 'new', 'throw', 'else', 'class', 'interface', 'enum', 'record') or name == class_name:
            continue
        line = line_of(mk, m.start(4))
        if after == '(':
            close = end_group(code, m.end() - 1)
            params = [e for _, e in _entries(mk, code, m.end(), close)]
            vararg = any('...' in p for p in params)
            count = len(params)
            members.append({'name': name, 'kind': 'Functions', 'source': sources.relative(path), 'line': line,
                            'arity': {'source': 'kotlin-class', 'overloads': [{'min': count - 1 if vararg else count, 'max': None if vararg else count, 'basis': 'params'}]}})
        else:
            members.append({'name': name, 'kind': 'Properties', 'source': sources.relative(path), 'line': line, 'arity': None})
    return members, header


def supertypes(sources, path, header):
    """Fully qualified (or external) names of the supertypes in a class header."""
    names = []
    if path.suffix == '.java':
        for clause in re.findall(r'\b(?:extends|implements)\s+([^{]+?)(?=\bimplements\b|$)', header, re.S):
            while re.search(r'<[^<>]*>', clause):
                clause = re.sub(r'<[^<>]*>', '', clause)
            names += [p.strip() for p in clause.split(',') if p.strip()]
    else:
        code = blank(header).replace('->', '  ')
        depth, colon = 0, None
        for i, c in enumerate(code):
            if c in '(<[':
                depth += 1
            elif c in ')>]':
                depth -= 1
            elif c == ':' and depth == 0:
                colon = i
                break
        if colon is not None:
            tail = re.sub(r'\bwhere\b.*', '', header[colon + 1:], flags=re.S)
            while re.search(r'<[^<>]*>|\([^()]*\)', tail):
                tail = re.sub(r'<[^<>]*>|\([^()]*\)', '', tail)
            for part in tail.split(','):
                part = re.sub(r'\bby\b.*', '', part).strip()
                if part:
                    names.append(part)
    return [sources.resolve_type(path, n) for n in names]


def class_members(sources, fqn, seen=None):
    """(members, external supertypes, scriptable, source) of a host class and its host supertypes."""
    seen = seen or set()
    if fqn in seen:
        return [], [], False, None
    seen.add(fqn)
    region = sources.class_region(fqn)
    if region is None:
        return None
    path = region[0]
    declared, header = (java_members if path.suffix == '.java' else kotlin_members)(sources, path, region)
    # Overloads are one member; their ranges are the member's arity overloads.
    members, by_name = [], {}
    for member in declared:
        kept = by_name.get(member['name'])
        if kept is None:
            by_name[member['name']] = member
            members.append(member)
        elif kept.get('arity') and member.get('arity'):
            ranges = kept['arity']['overloads'] + [r for r in member['arity']['overloads'] if r not in kept['arity']['overloads']]
            kept['arity'] = dict(kept['arity'], overloads=ranges)
    external, scriptable = [], False
    names = {m['name'] for m in members}
    for parent in supertypes(sources, path, header):
        simple = parent.split('.')[-1]
        if simple in SCRIPTABLE_BASES:
            scriptable = True
        inherited = class_members(sources, parent, seen) if sources.locate(parent) else None
        if inherited is None:
            if parent not in external:
                external.append(parent)
            continue
        sub, ext, scr, _ = inherited
        scriptable |= scr
        external += [e for e in ext if e not in external]
        for member in sub:
            if member['name'] not in names:
                names.add(member['name'])
                members.append(dict(member, inheritedFrom=parent))
    return members, external, scriptable, sources.relative(path)


REGISTRATION = re.compile(r'(?<![\w.])(?:(\w+)\.)?(' + '|'.join(HELPERS) + r')\(\s*(?:this\s*,\s*|\w+\s*,\s*(?="))?"([A-Za-z_$][\w$]*)"((?:\s*,\s*"[A-Za-z_$][\w$]*")*)')
COMPUTED = re.compile(r'(?<![\w.])(?:\w+\.)?(' + '|'.join(HELPERS) + r')\(\s*(?:this\s*,\s*(?!")|(?!this\b)(?!"))([\w.]+(?:\([^()]*\))?)')
FOR_IN = re.compile(r'\bfor\s*\(\s*(\w+)\s+in\s+(?:listOf|arrayOf|setOf)\(((?:\s*"[\w$]+"\s*,?)+)\)\s*\)\s*\{?\s*(?:\w+\.)?(' + '|'.join(HELPERS) + r')\(\s*(?:this\s*,\s*)?\1\b')
FOR_EACH = re.compile(r'([\w.]+?)(?:\.keys)?\.forEach\s*\{\s*(\w+)\s*->\s*(?:\w+\.)?(' + '|'.join(HELPERS) + r')\(\s*(?:this\s*,\s*)?\2\b')


def prototype_members(sources, entry):
    """Members a Scriptable registers in its class (and the extra classes of the entry), with the computed leftovers."""
    regions = []
    for fqn in [entry['class']] + entry.get('classes', []):
        region = sources.class_region(fqn)
        if region is None:
            return None
        regions.append(region)
    members, dynamic, seen = [], [], set()

    def add(name, kind, path, mk, offset, arity=None, alias_of=None):
        if name in seen:
            return
        seen.add(name)
        item = {'name': name, 'kind': kind, 'source': sources.relative(path), 'line': line_of(mk, offset), 'arity': arity}
        if alias_of:
            item['aliasOf'] = alias_of
        members.append(item)

    skip_functions = set(entry.get('skipFunctions', [])) | set(HELPERS)
    for path, kind, start, header_end, body_start, body_end in regions:
        raw, mk, code = sources.text(path)
        if body_start is None:
            continue
        # Nested classes and the bodies of helper / skipped functions belong to other objects or define the helpers.
        excluded = []
        for m in re.finditer(r'\b(?:class|object|interface)\s+\w+', mk[body_start + 1:body_end]):
            nested = sources.declaration(path, m[0].split()[-1], (body_start + 1, body_end))
            if nested and nested[3] is not None and nested[1] > body_start:
                excluded.append((nested[1], nested[4]))
        for m in re.finditer(r'\bfun\s+(?:<[^>\n]*>\s*)?(\w+)\s*\(', mk[body_start:body_end]):
            if m[1] in skip_functions:
                p = body_start + m.end() - 1
                close = end_group(code, p)
                rest = re.match(r'\s*(?::\s*[^={\n]+?)?\s*([={])', code[close + 1:])
                if rest:
                    s = close + 1 + rest.start(1)
                    e = end_group(code, s) if rest[1] == '{' else (code.find('\n\n', s) if code.find('\n\n', s) > 0 else body_end)
                    excluded.append((body_start + m.start(), e))
        inside = lambda p: not any(s <= p <= e for s, e in excluded)
        iterated = []
        for m in FOR_EACH.finditer(mk, body_start, body_end):
            if not inside(m.start()):
                continue
            close = end_group(code, code.index('{', m.end(1)))
            iterated.append((m.start(), close))
            names = sources.literal_names(m[1], path)
            if names is None:
                dynamic.append(f'{m[1]}.forEach -> {m[3]} ({sources.relative(path)}:{line_of(mk, m.start())})')
                continue
            for name in names:
                add(name, HELPERS[m[3]] or 'Functions', path, mk, m.start())
        for m in REGISTRATION.finditer(mk, body_start, body_end):
            if not inside(m.start()):
                continue
            helper, name = m[2], m[3]
            kind_name = HELPERS[helper]
            arity = None
            if helper == 'defineProperty':
                after = code[m.end():m.end() + 400]
                rest = after.lstrip()[1:].lstrip() if after.lstrip().startswith(',') else ''
                if rest.startswith(('{', '::')):
                    getter_end = end_group(code, m.end() + after.index('{')) if rest.startswith('{') else None
                    tail = code[getter_end + 1:getter_end + 40].lstrip() if getter_end else rest.split(',', 1)[-1].lstrip()
                    kind_name = 'Getters' if tail.lstrip(',').lstrip().startswith('null') or not tail.startswith(',') else 'GettersAndSetters'
                elif re.match(r'(?:newBaseFunction|\w*Function\(|Method\(|object\s*:\s*BaseFunction|f\b|function\b|constructor\b)', rest):
                    kind_name = 'Functions'
                else:
                    kind_name = 'Properties'
            elif helper == 'defineOp':
                r = re.match(r'\s*,\s*(\d+)\s*\.\.\s*(\d+)', mk[m.end():m.end() + 40])
                if r:
                    arity = {'source': 'defineOp', 'overloads': [{'min': int(r[1]), 'max': int(r[2]), 'basis': 'defineOp'}]}
            add(name, kind_name, path, mk, m.start(3), arity)
            for suffix in entry.get('helperSuffixes', {}).get(helper, []):
                add(name + suffix, kind_name, path, mk, m.start(3), arity, alias_of=name)
            for alias in re.findall(r'"([A-Za-z_$][\w$]*)"', m[4] or '') if helper == 'method' else []:
                add(alias, kind_name, path, mk, m.start(3), arity, alias_of=name)
        for m in re.finditer(r'defineFunctionProperties\([^)]*?\b(m?\w*[Nn]ames)\b', mk[body_start:body_end]):
            for name in sources.literal_names(m[1], path) or []:
                add(name, 'Functions', path, mk, body_start + m.start())
        for array in entry.get('nameArrays', []):
            names = sources.literal_names(array, path)
            if names is None:
                dynamic.append(f'name array {array} not resolved')
            for name in names or []:
                add(name, 'Functions', path, mk, mk.find(array, body_start))
        for m in FOR_IN.finditer(mk, body_start, body_end):
            if inside(m.start()):
                iterated.append((m.start(), m.end()))
                for name in re.findall(r'"([A-Za-z_$][\w$]*)"', m[2]):
                    add(name, HELPERS[m[3]] or 'Functions', path, mk, m.start())
        for m in COMPUTED.finditer(mk, body_start, body_end):
            if not inside(m.start()) or any(s <= m.start() <= e for s, e in iterated):
                continue
            if m[2] in ('name', 'it') or m[2] in entry.get('ignoreComputed', []):
                continue
            constant = re.fullmatch(r'(?:([\w.]+)\.)?([A-Z][A-Z0-9_]*)', m[2])
            if constant:
                owner = sources.locate(sources.resolve_type(path, constant[1])) if constant[1] else None
                value = sources.constant(constant[2], owner[0] if owner else path, only=True)
                if value:
                    add(value, HELPERS[m[1]] or 'Functions', path, mk, m.start())
                    continue
            dynamic.append(f'{m[1]}({m[2]}, ...) ({sources.relative(path)}:{line_of(mk, m.start())})')
    for name in entry.get('members', []):
        add(name, 'Functions', regions[0][0], sources.text(regions[0][0])[1], regions[0][2])
    return members, dynamic, sources.relative(regions[0][0])


def producers_from_metadata(result, metadata, classes):
    """Export rows whose script-modules.json function overloads or getters return one of classes."""
    meta = json.loads(Path(metadata).read_text(encoding='utf-8'))
    path_of = {m['class']: m['module'] for m in result['modules'] if m.get('metadata') == 'ksp'}
    rows = {}
    for record in result['records']:
        rows.setdefault((record['ownerModule'], record['member']), []).append(record['api'])
    found = {c: [] for c in classes}
    normalize = lambda t: re.sub(r'\?$', '', t or '').replace('$', '.')
    for m in meta['modules']:
        path = path_of.get(m['class'])
        for f in m['functions']:
            types = {normalize(o['returns']) for o in f['overloads']}
            for c in classes:
                if c in types:
                    for name in [f['name']] + f['aliases']:
                        found[c] += rows.get((path, name), [])
        for a in m['accessors']:
            if normalize(a.get('type')) in found:
                found[normalize(a['type'])] += rows.get((path, a['name']), [])
    return {c: sorted(set(v)) for c, v in found.items()}


def attach(host, result, metadata=None, config=CONFIG):
    """Adds result["returnedObjects"] (P0.2 schema) and result["returnedObjectsStats"]."""
    entries = json.loads(Path(config).read_text(encoding='utf-8'))
    sources = Sources(host)
    classes = [e['class'] for e in entries['objects']]
    typed = producers_from_metadata(result, metadata, classes) if metadata else {c: [] for c in classes}
    # Producers are export rows, or calls of a callable / constructable module (`Color.()`, `Canvas.new()`).
    apis = {r['api'] for r in result['records']}
    apis |= {m['module'] + suffix for m in result['modules'] if m['module'] for suffix in ('.()', '.new()')}
    objects, problems, skipped = [], [], []
    plugins = result.get('pluginExports') or {}
    for entry in entries['objects']:
        listed = entry.get('producers', [])
        plugin = plugins.get(entry['memberSource'][len('plugin:'):]) if entry['memberSource'].startswith('plugin:') else None
        if entry['memberSource'].startswith('plugin:') and (plugin or {}).get('status') != 'read':
            # The contract checkout is missing (plugin_exports.py): the object is left out with its rows.
            skipped.append(entry['object'])
            continue
        # catalogProducers: every row the plugin source adds returns the object (the compose.<Name> node factories).
        catalogue = set(plugin['records']) if plugin and entry.get('catalogProducers') else set()
        producers = sorted(set(typed.get(entry['class'], [])) | {p for p in listed if p in apis} | catalogue)
        problems += [f'{entry["object"]}: producer {p} is not an export' for p in listed if p not in apis]
        item = {'object': entry['object'], 'class': entry['class'], 'producers': producers, 'memberSource': entry['memberSource'],
                'documentation': entry.get('documentation', []), 'probe': entry.get('probe')}
        if entry['memberSource'] == 'kotlin-class':
            found = class_members(sources, entry['class'])
            if found is None:
                problems.append(f'{entry["object"]}: class {entry["class"]} not found')
                continue
            members, external, scriptable, source = found
            if scriptable:
                problems.append(f'{entry["object"]}: {entry["class"]} is a Scriptable; use augment-prototype')
            names = {m['name'] for m in members}
            problems += [f'{entry["object"]}: exclude {name} matches no member' for name in sorted(set(entry.get('exclude', [])) - names)]
            exclude = set(entry.get('exclude', [])) | OBJECT_MEMBERS
            # Upper-case fields are constants of the class (TimedTask.FLAG_MONDAY), not state of the returned object.
            constants = {m['name'] for m in members if entry.get('skipConstants') and m['kind'] == 'Properties' and CONSTANT_NAME.match(m['name'])}
            members = [m for m in members if m['name'] not in exclude | constants]
            item.update(source=source, externalBases=external, complete=True, constants=len(constants))
        elif entry['memberSource'] == 'augment-prototype':
            found = prototype_members(sources, entry)
            if found is None:
                problems.append(f'{entry["object"]}: class {entry["class"]} not found')
                continue
            members, dynamic, source = found
            exclude = set(entry.get('exclude', []))
            members = [m for m in members if m['name'] not in exclude]
            notes = entry.get('dynamic', [])
            item.update(source=source, dynamic=notes + dynamic, complete=not dynamic and not notes)
        elif plugin:
            # plugin:<name> (P5.2c): the members a plugin contract lists (plugin_exports.py), `catalog` naming the list.
            if entry.get('catalog') not in plugin['members']:
                problems.append(f'{entry["object"]}: catalog {entry.get("catalog")} is not a list of {entry["memberSource"]}')
                continue
            members = [dict(m) for m in plugin['members'][entry['catalog']]]
            # `members` (Q106 (a)): the names the object defines itself next to the contract list (ComposeNode's own members).
            listed = {m['name'] for m in members}
            members += [{'name': n, 'kind': 'Functions', 'source': None, 'line': None, 'arity': None} for n in entry.get('members', []) if n not in listed]
            item.update(source=plugin['root'], complete=True)
        else:
            members = [{'name': n, 'kind': 'Functions', 'source': None, 'line': None, 'arity': None} for n in entry['members']]
            item.update(source=None, complete=True)
        item['members'] = sorted(members, key=lambda m: m['name'])
        objects.append(item)
    result['returnedObjects'] = objects
    result['returnedObjectsStats'] = dict({'objects': len(objects), 'members': sum(len(o['members']) for o in objects),
                                           'incomplete': [o['object'] for o in objects if not o['complete']], 'problems': problems,
                                           'skipped': skipped},
                                          **(other_return_types(sources, metadata, set(classes) | set(entries.get('ignoredReturnTypes', []))) if metadata else {}))
    attach_prototypes(sources, result, entries.get('prototypes', []), metadata)
    return result


CONSTANT_NAME = re.compile(r'[A-Z][A-Z0-9_]*$')


def prototype_modules(metadata):
    """Module paths (as extract_exports.extract_ksp names them, "" for an assign module) of the script-modules.json modules
    declaring @ScriptPrototype."""
    meta = json.loads(Path(metadata).read_text(encoding='utf-8'))
    by_class = {m['class']: m for m in meta['modules']}

    def path(m):
        return (path(by_class[m['parent']]) + '.' if m.get('parent') else '') + ('' if m.get('assign') else m['name'])

    return sorted(path(m) for m in meta['modules'] if m.get('prototype'))


def attach_prototypes(sources, result, entries, metadata=None):
    """Adds result["prototypes"] and result["prototypesStats"] (Q18 (a)): the members scripts reach through the prototype of
    a module declaring @ScriptPrototype, without the names the module object defines itself (they shadow the prototype),
    the entry's `exclude` (public Java members that are not script API) and, with `skipConstants`, upper-case fields
    (constant tables). An entry with only `notEnumerated` describes what the prototype holds without listing it."""
    modules = {m['module'] for m in result['modules']}
    own = {}
    for record in result['records']:
        own.setdefault(record.get('module'), set()).add(record['member'])
    items, problems = [], []
    for entry in entries:
        module = entry['module']
        if module not in modules:
            problems.append(f'prototype {module}: no such module')
            continue
        item = {'module': module, 'class': entry.get('class'), 'memberSource': 'script-prototype', 'memberList': entry.get('memberSource'),
                'documentation': entry.get('documentation', []), 'probe': entry.get('probe', module), 'notEnumerated': entry.get('notEnumerated')}
        if entry.get('memberSource') == 'kotlin-class':
            found = class_members(sources, entry['class'])
            if found is None:
                problems.append(f'prototype {module}: class {entry["class"]} not found')
                continue
            members, external, scriptable, source = found
            if scriptable:
                problems.append(f'prototype {module}: {entry["class"]} is a Scriptable; use augment-prototype or policy')
            item.update(source=source, externalBases=external, complete=True)
        elif entry.get('memberSource') == 'augment-prototype':
            found = prototype_members(sources, entry)
            if found is None:
                problems.append(f'prototype {module}: class {entry["class"]} not found')
                continue
            members, dynamic, source = found
            item.update(source=source, dynamic=dynamic, complete=not dynamic)
        elif entry.get('memberSource') == 'policy':
            members = [{'name': n, 'kind': 'Functions', 'source': None, 'line': None, 'arity': None} for n in entry['members']]
            item.update(source=None, complete=True)
        else:
            members = []
            item.update(source=None, complete=True)
            if not entry.get('notEnumerated'):
                problems.append(f'prototype {module}: neither a member source nor notEnumerated')
        names = {m['name'] for m in members}
        exclude = set(entry.get('exclude', []))
        problems += [f'prototype {module}: exclude {name} matches no member' for name in sorted(exclude - names)]
        constants = [m['name'] for m in members if entry.get('skipConstants') and m['kind'] == 'Properties' and CONSTANT_NAME.match(m['name'])]
        shadowed = sorted(n for n in names if n in own.get(module, ()))
        dropped = OBJECT_MEMBERS | exclude | set(constants) | set(shadowed)
        item.update(members=sorted((m for m in members if m['name'] not in dropped), key=lambda m: m['name']),
                    shadowed=shadowed, excluded=sorted(exclude & names), constants=len(constants))
        items.append(item)
    listed = {entry['module'] for entry in entries}
    if metadata:
        problems += [f'prototype {module}: @ScriptPrototype module not listed in returned-objects.json prototypes'
                     for module in prototype_modules(metadata) if module not in listed]
    result['prototypes'] = items
    result['prototypesStats'] = {'modules': len(items), 'enumerated': sum(bool(i['members']) for i in items),
                                 'members': sum(len(i['members']) for i in items), 'shadowed': sum(len(i['shadowed']) for i in items),
                                 'excluded': sum(len(i['excluded']) for i in items), 'constants': sum(i['constants'] for i in items),
                                 'notEnumerated': [i['module'] for i in items if i['notEnumerated']],
                                 'incomplete': [i['module'] for i in items if not i['complete']], 'problems': problems}
    return result


def other_return_types(sources, metadata, known):
    """Return and getter types of script-modules.json outside returned-objects.json: host classes not listed yet, and
    classes outside the host sources (Android, Java, OpenCV, OkHttp; Java objects whose members are not enumerated)."""
    meta = json.loads(Path(metadata).read_text(encoding='utf-8'))
    types = {}
    for m in meta['modules']:
        returns = [(f['name'], o['returns']) for f in m['functions'] for o in f['overloads']] + [(a['name'], a.get('type')) for a in m['accessors']]
        for name, value in returns:
            value = re.sub(r'\?$', '', value or '')
            if not value or value in known or value.startswith(('kotlin.collections.', 'kotlin.Array')) or '.' not in value:
                continue
            types.setdefault(value, []).append(f'{m["name"]}.{name}')
    unlisted = {t: sorted(set(p)) for t, p in types.items() if sources.locate(t)}
    external = {t: sorted(set(p)) for t, p in types.items() if t not in unlisted}
    return {'unlistedHostTypes': dict(sorted(unlisted.items())), 'externalTypes': dict(sorted(external.items()))}


def ace_status(hits):
    """default: completed with the default library set; all-groups: only with every declaration group; none."""
    if any(h.get('aceDefault') and h.get('aceAllGroups') for h in hits):
        return 'default'
    return 'all-groups' if any(h.get('aceAllGroups') for h in hits) else 'none'


def gate(host, baseline, output, docs_root, surface_command, policy=None):
    """Name-level gate of the returned-object and prototype members (documentation, d.ts, Ace); writes returned-objects.json.

    Returns (counts, documentation rows, surface rows): the rows are keyed by the matrix row id (`<object>#<member>`,
    `<module>.<member>` for a prototype member) in the shapes of documentation.json and surfaces.json, so the matrix
    fills the doc / dts / ace columns of those rows from them."""
    from check import documentation_rows
    output = Path(output)
    work = output / 'returned-objects'
    work.mkdir(parents=True, exist_ok=True)
    objects = baseline.get('returnedObjects', [])
    prototypes = baseline.get('prototypes', [])
    queries, owners = set(), {}
    for o in objects:
        if not o.get('probe'):
            continue
        for m in o['members']:
            for name in [m['name']] + m.get('aliases', []):
                query = o['probe'] + '.' + name
                queries.add(query)
                owners.setdefault((o['object'], m['name']), []).append(query)
        queries.add(o['probe'])
    for p in prototypes:
        for m in p['members']:
            for name in [m['name']] + m.get('aliases', []):
                query = p['probe'] + '.' + name
                queries.add(query)
                owners.setdefault((p['module'] + '.', m['name']), []).append(query)
    exports = work / 'exports.json'
    exports.write_text(json.dumps({'queries': sorted(queries)}, ensure_ascii=False) + '\n', encoding='utf-8')
    command = list(surface_command)
    command[command.index('--exports') + 1] = str(exports)
    command[command.index('--output') + 1] = str(work)
    subprocess.run(command, check=False, stdout=subprocess.DEVNULL)
    surfaces = {row['api']: row for row in json.loads((work / 'surfaces.json').read_text(encoding='utf-8'))['rows']}
    ace = '--skip-ace' not in command
    rows, counts = [], {'members': 0, 'documentation': 0, 'dts': 0, 'ace': 0, 'aceAllGroupsOnly': 0, 'unprobed': 0}
    for o in objects:
        items = [{'api': f'{o["object"]}#{m["name"]}', 'module': o['object'], 'ownerModule': o['object'], 'member': name}
                 for m in o['members'] for name in [m['name']] + m.get('aliases', [])]
        docs = {}
        if o['documentation']:
            for row in documentation_rows(items, docs_root, {'documentationModules': {o['object']: o['documentation']}}):
                docs.setdefault(row['api'], []).append(row['status'])
        probe = surfaces.get(o.get('probe') or '', {}).get('declaration', {}).get('status') == 'explicit'
        for m in o['members']:
            row_id = f'{o["object"]}#{m["name"]}'
            hits = [surfaces.get(q, {}) for q in owners.get((o['object'], m['name']), [])]
            row = {'id': row_id, 'object': o['object'], 'member': m['name'],
                   'doc': max(docs.get(row_id, ['missing']), key=['missing', 'mention', 'heading'].index) if o['documentation'] else 'no-page',
                   'dts': 'explicit' if any(h.get('declaration', {}).get('status') == 'explicit' for h in hits) else ('no-probe' if not probe else 'missing'),
                   'ace': ace_status(hits) if ace and probe else None}
            counts['members'] += 1
            counts['documentation'] += row['doc'] in ('missing', 'no-page')
            counts['dts'] += row['dts'] != 'explicit'
            counts['ace'] += ace and row['ace'] != 'default'
            counts['aceAllGroupsOnly'] += row['ace'] == 'all-groups'
            counts['unprobed'] += not probe
            rows.append(row)
    # Members of Java objects are completed from the generated Java declaration groups, which the default library set of
    # Ace leaves out: "all-groups" is reported (aceAllGroupsOnly) but is not a failure.
    failing = [r for r in rows if r['doc'] in ('missing', 'no-page') or r['dts'] != 'explicit' or (ace and r['ace'] not in ('default', 'all-groups'))]
    # Prototype members are module members: their pages are the module's (policy.json documentationModules, as for an
    # export), and Ace must complete them as an export, in both library sets and in the static index (`static`).
    prototype_rows, prototype_counts = [], {'members': 0, 'documentation': 0, 'dts': 0, 'ace': 0}
    for p in prototypes:
        items = [{'api': f'{p["module"]}.{m["name"]}', 'module': p['module'], 'ownerModule': p['module'], 'member': name}
                 for m in p['members'] for name in [m['name']] + m.get('aliases', [])]
        pages = {'documentationModules': dict((policy or {}).get('documentationModules', {}),
                                              **({p['module'].split('.')[0]: p['documentation']} if p['documentation'] else {}))}
        docs = {}
        for row in documentation_rows(items, docs_root, pages) if items else []:
            docs.setdefault(row['api'], []).append(row['status'])
        for m in p['members']:
            row_id = f'{p["module"]}.{m["name"]}'
            hits = [surfaces.get(q, {}) for q in owners.get((p['module'] + '.', m['name']), [])]
            row = {'id': row_id, 'module': p['module'], 'member': m['name'],
                   'doc': max(docs.get(row_id, ['missing']), key=['missing', 'mention', 'heading'].index),
                   'dts': 'explicit' if any(h.get('declaration', {}).get('status') == 'explicit' for h in hits) else 'missing',
                   'ace': (('static' if any(h.get('aceStatic') for h in hits) else 'semantic')
                           if any(h.get('aceDefault') and h.get('aceAllGroups') for h in hits) else 'none') if ace else None}
            prototype_counts['members'] += 1
            prototype_counts['documentation'] += row['doc'] == 'missing'
            prototype_counts['dts'] += row['dts'] != 'explicit'
            prototype_counts['ace'] += ace and row['ace'] != 'static'
            prototype_rows.append(row)
    prototype_failing = [r for r in prototype_rows if r['doc'] == 'missing' or r['dts'] != 'explicit' or (ace and r['ace'] != 'static')]
    report = {'counts': counts, 'objects': {o['object']: {'members': len(o['members']), 'probe': o.get('probe'),
                                                          'probeResolved': surfaces.get(o.get('probe') or '', {}).get('declaration', {}).get('status') == 'explicit'}
                                            for o in objects},
              'failures': failing, 'rows': rows,
              'prototypes': {'counts': prototype_counts, 'modules': {p['module']: len(p['members']) for p in prototypes},
                             'failures': prototype_failing, 'rows': prototype_rows}}
    (output / 'returned-objects.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    documentation = [{'api': r['id'], 'status': {'heading': 'heading', 'mention': 'mention'}.get(r['doc'], 'missing')} for r in rows + prototype_rows]
    surface_rows = [{'api': r['id'], 'declaration': {'status': r['dts']}, 'aceDefault': r['ace'] == 'default', 'aceAllGroups': r['ace'] in ('default', 'all-groups')}
                    for r in rows]
    surface_rows += [{'api': r['id'], 'declaration': {'status': r['dts']}, 'aceDefault': r['ace'] in ('semantic', 'static'),
                      'aceAllGroups': r['ace'] in ('semantic', 'static'), 'aceStatic': r['ace'] == 'static'} for r in prototype_rows]
    return (dict(counts, failingMembers=len(failing), objects=len(objects),
                 prototypes=dict(prototype_counts, failingMembers=len(prototype_failing), modules=len(prototypes))),
            documentation, surface_rows)
