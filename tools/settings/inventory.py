"""Inventory of the settings, remembered state and stores of the AutoJs6 host (docs/settings/ROADMAP.md P0.2 - P0.4).

Usage:
  py -3.12 -I tools/settings/inventory.py [--host HOST] [--date YYYY-MM-DD]
      [--json OUT] [--markdown OUT] [--defaults-markdown OUT] [--check]

Scans:
  1. every `key_*` string resource in res/values (strings_donottranslate.xml holds the baseline 265, a few more live in
     other files such as ace_editor_language_keys.xml);
  2. the 5 settings page XMLs: the page and the PreferenceCategory titles each key appears under, plus the
     `defaultValue` / `itemDefaultKey` / `itemKeys` attributes;
  3. the `keys_*` string arrays (candidate values of list preferences) and the `default_key_*` aliases;
  4. app/src/main/java: every `R.string.key_*` reference (file and line, comment lines skipped), string literals that
     look like preference keys (`"key_$_..."`), `getSharedPreferences(` call sites with the file name resolved from
     literals, constants, constructor arguments and small helper functions, files that bypass `Pref` through
     `PreferenceManager.getDefaultSharedPreferences`, and JSON file names used next to `filesDir` / `noBackupFilesDir`;
  5. default values: the second argument of `getBoolean` / `getInt` / `getLong` / `getFloat` / `getString` /
     `getStringSet` calls whose first argument resolves to a key, followed through `R.bool` / `R.integer` / `R.string`,
     companion constants and local variables; the default argument of the key declarations in core/pref/keys
     (`boolResource` / `intResource` / `stringResource` / `keyResource`, `SettingDefault.of(...)`,
     `SettingDefault.computed("<expression>")`, `SettingDefault.None`, docs/settings/ROADMAP.md P2); XML defaults are
     added as separate sources.

The hand-written classification (kind, category id, note, extra default sources) lives in
tools/settings/classification.json and is merged into the outputs when present. `--check` exits with status 2 when an
item has no kind, when a kind or category id is unknown, or when the classification names an item the scan no longer
finds.

Outputs (defaults):
  tools/settings/baseline/inventory-<date>.json, docs/settings/inventory.md, docs/settings/defaults.md
"""
import argparse
import ast
import bisect
import datetime
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from xml.parsers import expat

HERE = Path(__file__).resolve().parent
HOST = HERE.parents[1]
SCHEMA = 'autojs6.settings-inventory/v1'

JAVA_ROOT = 'app/src/main/java'
PACKAGE_ROOT = 'org/autojs/autojs/'
RES_ROOT = 'app/src/main/res'
ZH_VALUES = 'values-zh'

PAGES = [
    ('fragment_preferences.xml', '设置'),
    ('fragment_code_editor_settings.xml', '代码编辑器设置'),
    ('fragment_developer_options.xml', '开发者选项'),
    ('fragment_trash_and_version_history_settings.xml', '回收站与版本历史设置'),
    ('fragment_plugin_center_preferences.xml', '插件中心设置'),
]

KINDS = ('Setting', 'State', 'Cache', 'ValueConstant', 'Excluded')

# Category ids of docs/settings/ROADMAP.md appendix A.1 (order = page order).
CATEGORIES = (
    'appearance', 'general', 'explorer', 'history', 'editor_general', 'editor_appearance', 'editor_log',
    'editor_assistance', 'editor_smart_hint', 'editor_gestures', 'editor_gutter', 'editor_performance',
    'editor_keyboard', 'timed_task', 'a11y', 'running', 'record', 'extensibility', 'updates', 'console', 'connection',
    'layout_inspector', 'floating', 'developer', 'plugin_center', 'plugin_enable_state', 'apk_build', 'wizards',
    'data_shortcuts', 'data_color_library', 'credentials', 'other',
)

# Data categories (D21 / T19) hold no keys; their entries are counted per item at run time.
DATA_CATEGORIES = ('data_shortcuts', 'data_color_library')

# Deployment kinds of ROADMAP T18 (P2.2 / P2.3).
DEPLOYMENTS = ('CREATE_DIRECTORY', 'DOWNLOAD_EDITOR_FONT')

# The 14 file-based JSON stores listed in ROADMAP 1.1, used to reconcile the heuristic scan.
ROADMAP_JSON_STORES = (
    'FontStore', 'FontCatalogRepository', 'ModelStore', 'InstalledPluginMetadataStore', 'PluginIndexRepository',
    'NodeLifecycleCheckpointStore', 'NodeWorkManagerBridgeScheduler', 'RuntimeWebLocalDownloads',
    'PythonPrivateExecutionSnapshot', 'PrivilegedEditSessions', 'PrivilegedFileOperationJournal',
    'PrivilegedBackupManager', 'BuildActivity', 'NodeDoctorReportGenerator',
)

# Sources whose JSON files lie in a directory that another source passes in, so that they name no `filesDir` of their
# own (ROADMAP P5.1): display path -> that source. Same cases as MANUAL_JSON_FILE_SOURCES in SettingsRegistryGuardTest.
MANUAL_JSON_FILE_SOURCES = {
    'core/accessibility/codegen/store/ModelStore.kt': 'ui/settings/codegen/CodegenModelStoreFactory.kt',
}

# getSharedPreferences( arguments that no generic rule can resolve: (display path, argument) -> (name, how).
MANUAL_STORE_NAMES = {
    ('runtime/api/modules/storages/Storages.kt', 'it'): (
        'autojs.localstorage.*', '`.let` 的接收者是 `LocalStorage.NAME_PREFIX + name`'),
}

# Default preference keys that the sources name through constants whose values do not start with "key_" (so the
# literal scan misses them) and that no key declaration in core/pref/keys names yet: (display path, constant). They are
# read from `const val NAME = "..."`. Empty since P4.2: the colour selection states listed here in P3.6 (appendix C
# Q15) are declared in MiscStateKeys, whose names the declaration scan finds.
LITERAL_KEY_CONSTANTS = ()

KEY_REF = re.compile(r'\bR\.string\.(key_[A-Za-z0-9_]+)\b')
STRING_LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
KEY_LITERAL_PREFIX = re.compile(r'^key_')
FILES_DIR = re.compile(r'\b(?:filesDir|getFilesDir\(\)|noBackupFilesDir|getNoBackupFilesDir\(\))')
JSON_LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*\.json)"')
GETTER = re.compile(r'\b(getBoolean|getInt|getLong|getFloat|getString|getStringOrNull|getStringSet|getLinkedHashSet'
                    r'|getLinkedList|getTimestamp)\s*\(')
GETTER_TYPES = {
    'getBoolean': 'Boolean', 'getInt': 'Int', 'getLong': 'Long', 'getFloat': 'Float', 'getString': 'String',
    'getStringOrNull': 'String', 'getStringSet': 'StringSet', 'getLinkedHashSet': 'Json (Gson LinkedHashSet)',
    'getLinkedList': 'Json (Gson LinkedList)', 'getTimestamp': 'Long',
}
IMPLICIT_DEFAULTS = {
    'getStringOrNull': 'null', 'getLinkedHashSet': '[] (键不存在时)', 'getLinkedList': '[] (键不存在时)',
    'getTimestamp': '-1',
}


# ---------------------------------------------------------------------------------------------------------- XML

class Node:
    __slots__ = ('tag', 'attrs', 'line', 'children', 'parent', 'text')

    def __init__(self, tag, attrs, line, parent):
        self.tag, self.attrs, self.line, self.parent = tag, attrs, line, parent
        self.children, self.text = [], ''

    def itertext(self):
        yield self.text
        for c in self.children:
            yield from c.itertext()


def parse_xml(path):
    root = Node('#root', {}, 0, None)
    stack = [root]
    parser = expat.ParserCreate()

    def start(tag, attrs):
        node = Node(tag, attrs, parser.CurrentLineNumber, stack[-1])
        stack[-1].children.append(node)
        stack.append(node)

    def end(_tag):
        stack.pop()

    def chars(data):
        stack[-1].text += data

    parser.StartElementHandler = start
    parser.EndElementHandler = end
    parser.CharacterDataHandler = chars
    with open(path, 'rb') as f:
        parser.ParseFile(f)
    return root


def walk(node):
    for c in node.children:
        yield c
        yield from walk(c)


# ---------------------------------------------------------------------------------------------------- resources

class Resources:
    def __init__(self, res_dir):
        self.strings, self.bools, self.integers, self.arrays = {}, {}, {}, {}
        self.zh_strings = {}
        for path in sorted((res_dir / 'values').glob('*.xml')):
            self._load(path, self.strings, True)
        zh = res_dir / ZH_VALUES
        if zh.is_dir():
            for path in sorted(zh.glob('*.xml')):
                self._load(path, self.zh_strings, False)

    def _load(self, path, strings, everything):
        try:
            root = parse_xml(path)
        except expat.ExpatError:
            return
        for res in root.children:
            if res.tag != 'resources':
                continue
            for item in res.children:
                name = item.attrs.get('name')
                if not name:
                    continue
                text = ''.join(item.itertext()).strip()
                entry = {'value': text, 'file': path.name, 'line': item.line}
                if item.tag == 'string':
                    strings[name] = entry
                elif not everything:
                    continue
                elif item.tag == 'bool':
                    self.bools[name] = entry
                elif item.tag == 'integer':
                    self.integers[name] = entry
                elif item.tag == 'string-array':
                    entry['items'] = [''.join(i.itertext()).strip() for i in item.children if i.tag == 'item']
                    self.arrays[name] = entry

    def string(self, name, hops=5):
        """Resolves @string/ chains; returns (final resource name, value) or (None, None)."""
        while hops > 0 and name in self.strings:
            value = self.strings[name]['value']
            if value.startswith('@string/'):
                name, hops = value[len('@string/'):], hops - 1
                continue
            return name, value
        return None, None

    def title(self, ref):
        if not ref or not ref.startswith('@string/'):
            return ref or ''
        name = ref[len('@string/'):]
        zh = self.zh_strings.get(name, {}).get('value')
        en = self.string(name)[1]
        return zh or en or name


# ------------------------------------------------------------------------------------------------------- source

class SourceFile:
    def __init__(self, path, rel, display):
        self.path, self.rel, self.display = path, rel, display
        self.text = path.read_text(encoding='utf-8', errors='replace')
        self.lines = self.text.split('\n')
        self.offsets = [0]
        for line in self.lines[:-1]:
            self.offsets.append(self.offsets[-1] + len(line) + 1)
        self.stem = path.stem

    def line_of(self, offset):
        return bisect.bisect_right(self.offsets, offset)

    def is_comment_line(self, line_no):
        s = self.lines[line_no - 1].lstrip()
        return s.startswith('//') or s.startswith('*') or s.startswith('/*')


def display_path(rel):
    p = rel[len(JAVA_ROOT) + 1:] if rel.startswith(JAVA_ROOT + '/') else rel
    return p[len(PACKAGE_ROOT):] if p.startswith(PACKAGE_ROOT) else p


def load_sources(host):
    root = host / JAVA_ROOT
    files = []
    for dirpath, _dirs, names in os.walk(root):
        for n in sorted(names):
            if n.endswith('.kt') or n.endswith('.java'):
                path = Path(dirpath) / n
                rel = path.relative_to(host).as_posix()
                files.append(SourceFile(path, rel, display_path(rel)))
    files.sort(key=lambda f: f.rel)
    return files


def scan_args(text, start):
    """Splits the argument list that starts right after '(' at `start`; returns (args, end offset)."""
    args, depth, i, cur_start = [], 0, start, start
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            if text.startswith('"""', i):
                j = text.find('"""', i + 3)
                i = n if j < 0 else j + 3
                continue
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == '\\' else 1
            i += 1
            continue
        if c == "'":
            j = i + 1
            while j < n and j < i + 8 and text[j] != "'":
                j += 2 if text[j] == '\\' else 1
            i = j + 1
            continue
        if text.startswith('//', i):
            j = text.find('\n', i)
            i = n if j < 0 else j
            continue
        if text.startswith('/*', i):
            j = text.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        if c in '([{':
            depth += 1
        elif c in ')]}':
            if depth == 0:
                args.append(text[cur_start:i].strip())
                return [a for a in args if a], i
            depth -= 1
        elif c == ',' and depth == 0:
            args.append(text[cur_start:i].strip())
            cur_start = i + 1
        i += 1
    return [a for a in args if a], n


def split_top_level(expr, sep='+'):
    parts, depth, cur, i, in_str = [], 0, [], 0, False
    while i < len(expr):
        c = expr[i]
        if in_str:
            cur.append(c)
            if c == '\\' and i + 1 < len(expr):
                cur.append(expr[i + 1])
                i += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            cur.append(c)
        elif c in '([{':
            depth += 1
            cur.append(c)
        elif c in ')]}':
            depth -= 1
            cur.append(c)
        elif c == sep and depth == 0:
            parts.append(''.join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    parts.append(''.join(cur).strip())
    return parts


def safe_number(expr):
    """Evaluates a constant arithmetic expression such as `20L * 1024L * 1024L`; returns int / float or None."""
    s = re.sub(r'(?<=\d)[LlFfDd]\b', '', expr.strip())
    s = s.replace('_', '')
    if not re.fullmatch(r'[\d\s.+\-*/()]+', s):
        return None
    try:
        tree = ast.parse(s, mode='eval')
    except SyntaxError:
        return None

    def ev(node):
        if isinstance(node, ast.Expression):
            return ev(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return node.value
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.USub, ast.UAdd)):
            v = ev(node.operand)
            return -v if isinstance(node.op, ast.USub) else v
        if isinstance(node, ast.BinOp) and isinstance(node.op, (ast.Add, ast.Sub, ast.Mult, ast.Div, ast.FloorDiv)):
            a, b = ev(node.left), ev(node.right)
            if isinstance(node.op, ast.Add):
                return a + b
            if isinstance(node.op, ast.Sub):
                return a - b
            if isinstance(node.op, ast.Mult):
                return a * b
            return a // b if isinstance(a, int) and isinstance(b, int) else a / b
        raise ValueError(node)

    try:
        return ev(tree)
    except (ValueError, ZeroDivisionError, TypeError):
        return None


class Resolver:
    """Resolves Kotlin / Java expressions of string constants, store names and default values."""

    # `NAME = <expr>` declarations of vals, vars, Java fields and constants (one line).
    DECL = re.compile(r'(?:(?:private|internal|public|protected|const|static|final|override|lateinit)\s+)*'
                      r'\b(?:val|var|String|int|long|boolean|float|Integer|Long|Boolean)\s+([A-Za-z_]\w*)\s*'
                      r'(?::\s*[\w.<>?]+)?\s*=\s*([^\n]*)')

    def __init__(self, sources, res):
        self.sources, self.res = sources, res
        self.by_stem = {}
        self.decls = {}
        for f in sources:
            self.by_stem.setdefault(f.stem, []).append(f)
            for m in self.DECL.finditer(f.text):
                expr = m.group(2).strip().rstrip(';').strip()
                if expr.startswith('lazy') or expr == '':
                    continue
                self.decls.setdefault(m.group(1), []).append((f, expr, f.line_of(m.start())))

    def find_decl(self, name, here, qualifier=None):
        found = self.decls.get(name, [])
        if not found:
            return None
        if qualifier:
            # `Foo.BAR` only resolves inside Foo.kt / Foo.java (an enum member path such as `Mode.AUTO.key` does not).
            stem = qualifier.split('.')[-1]
            scoped = [d for d in found if d[0].stem == stem]
            return scoped[0] if scoped else None
        local = [d for d in found if d[0] is here]
        if local:
            return local[0]
        # Only constant-looking names (`SP_NAME`, `PREFS`) are looked up in other files; `keyRes` or `key` there
        # would be some unrelated local variable.
        if not re.fullmatch(r'[A-Z][A-Z0-9_]*', name):
            return None
        return found[0] if len(found) == 1 else None

    def template(self, body, here, depth):
        if here.path.suffix != '.kt':
            return body.replace('\\"', '"').replace('\\\\', '\\')
        out, i = [], 0
        while i < len(body):
            c = body[i]
            if c == '\\' and i + 1 < len(body):
                out.append(body[i + 1])
                i += 2
                continue
            if c == '$':
                m = re.match(r'\$\{([^}]*)\}|\$([A-Za-z_]\w*)', body[i:])
                if m:
                    inner = (m.group(1) or m.group(2)).strip()
                    value, _how = self.string_expr(inner, here, depth + 1)
                    out.append(value if value is not None and '*' not in value else '*')
                    i += m.end()
                    continue
            out.append(c)
            i += 1
        return re.sub(r'\*+', '*', ''.join(out))

    def string_expr(self, expr, here, depth=0):
        """Returns (string or pattern with '*', how) or (None, reason)."""
        expr = expr.strip()
        if depth > 4:
            return None, '嵌套过深'
        while expr.startswith('(') and expr.endswith(')'):
            expr = expr[1:-1].strip()
        parts = split_top_level(expr, '+')
        if len(parts) > 1:
            values, hows = [], []
            for p in parts:
                v, how = self.string_expr(p, here, depth + 1)
                values.append(v if v is not None else '*')
                hows.append(how if v is not None else '未解析')
            return re.sub(r'\*+', '*', ''.join(values)), '拼接'
        m = re.fullmatch(r'"((?:[^"\\]|\\.)*)"', expr)
        if m:
            value = self.template(m.group(1), here, depth)
            return value, '字面量' if '*' not in value else '字符串模板'
        m = re.fullmatch(r'(?:[\w.()]*\.)?(?:getString|key)\(\s*R\.string\.(\w+)\s*\)', expr)
        if m:
            name, value = self.res.string(m.group(1))
            return (value, '字符串资源 ' + m.group(1)) if value is not None else (None, '资源缺失')
        m = re.fullmatch(r'R\.string\.(\w+)', expr)
        if m:
            name, value = self.res.string(m.group(1))
            return (value, '字符串资源 ' + m.group(1)) if value is not None else (None, '资源缺失')
        m = re.fullmatch(r'(?:([A-Z]\w*(?:\.[A-Z]\w*)*)\.)?([A-Za-z_]\w*)', expr)
        if m:
            qualifier, name = m.group(1), m.group(2)
            decl = self.find_decl(name, here, qualifier)
            if decl:
                f, value_expr, line = decl
                v, how = self.string_expr(value_expr, f, depth + 1)
                if v is not None:
                    return v, f'常量 {name} ({f.display}:{line})'
            ctor = self.constructor_args(name, here)
            if ctor:
                return ' | '.join(v for v, _ in ctor), '构造参数 ' + ', '.join(w for _, w in ctor)
            return None, '未找到声明'
        m = re.fullmatch(r'(?:([A-Z]\w*)\.)?([a-z]\w*)\((.*)\)', expr, re.S)
        if m:
            body = self.function_body(m.group(2), here, m.group(1))
            if body:
                f, body_expr, line = body
                v, how = self.string_expr(body_expr, f, depth + 1)
                if v is not None:
                    return v, f'函数 {m.group(2)} ({f.display}:{line})'
        return None, '无法解析'

    def constructor_args(self, name, here):
        m = re.search(r'class\s+(\w+)\s*\((?:[^()]|\([^()]*\))*?\b' + re.escape(name) + r'\s*:\s*String', here.text, re.S)
        if not m:
            return None
        cls = m.group(1)
        out = []
        for f in self.sources:
            if cls not in f.text:
                continue
            for c in re.finditer(r'\b' + cls + r'\s*\(', f.text):
                args, _ = scan_args(f.text, c.end())
                for a in args:
                    am = re.fullmatch(re.escape(name) + r'\s*=\s*"((?:[^"\\]|\\.)*)"', a)
                    if am:
                        out.append((self.template(am.group(1), f, 0), f'{f.display}:{f.line_of(c.start())}'))
        return out or None

    def function_body(self, fn, here, qualifier=None):
        files = [here] + (self.by_stem.get(qualifier, []) if qualifier else [])
        for f in files:
            for m in re.finditer(r'\bfun\s+' + re.escape(fn) + r'\s*\(', f.text):
                _args, end = scan_args(f.text, m.end())
                rest = f.text[end + 1:]
                bm = re.match(r'\s*(?::\s*[\w.<>?]+)?\s*=\s*([^\n]+)', rest)
                if bm:
                    return f, bm.group(1).strip(), f.line_of(m.start())
                bm = re.match(r'\s*(?::\s*[\w.<>?]+)?\s*\{', rest)
                if bm:
                    block_end = rest.find('\n    }')
                    block = rest[bm.end(): block_end if block_end > 0 else len(rest)]
                    rm = re.search(r'\breturn\s+([^\n]+)', block)
                    if rm:
                        return f, rm.group(1).strip(), f.line_of(m.start())
        return None

    def local_value(self, name, here, before_line):
        """Nearest `val name = expr` above `before_line` within 60 lines."""
        pattern = re.compile(r'^\s*(?:final\s+)?(?:val|var|int|long|boolean|float|String)\s+' + re.escape(name)
                             + r'\s*(?::\s*[\w.<>?]+)?\s*=\s*(.+?)\s*;?\s*$')
        # The search stays inside the enclosing function: it stops at the first `fun` (Kotlin) or method header
        # (Java) above the call, which also covers the call sitting on the header line itself.
        boundary = re.compile(r'\bfun\s+\w+\s*\(|^\s*(?:public|private|protected|static)\b[^=;]*\([^;]*\)\s*(?:throws[^{]*)?\{?\s*$')
        for ln in range(before_line, max(0, before_line - 60), -1):
            text = here.lines[ln - 1]
            m = pattern.match(text)
            if m and ln < before_line:
                return m.group(1), ln
            if boundary.search(text):
                return None
        return None

    def default_value(self, expr, here, line, depth=0):
        """Returns dict(form, expr, value, via) for a default value expression."""
        expr = expr.strip()
        res = self.res
        out = {'expr': expr}
        if depth > 4:
            return dict(out, form='表达式', value=None)
        if expr in ('true', 'false', 'null'):
            return dict(out, form='调用处字面量', value=expr)
        number = safe_number(expr)
        if number is not None and re.fullmatch(r'-?[\d_.]+[LlFf]?', expr.strip()):
            return dict(out, form='调用处字面量', value=str(number))
        m = re.fullmatch(r'"((?:[^"\\]|\\.)*)"', expr)
        if m:
            return dict(out, form='调用处字面量', value=m.group(1).replace('\\$', '$'))
        if re.fullmatch(r'(?:mutable|linked|hash)?(?:[Ss]etOf|[Ll]istOf|empty(?:Set|List))\(\s*\)', expr):
            return dict(out, form='调用处字面量', value='[]')
        if re.fullmatch(r'(?:mutable|linked|hash)?(?:[Mm]apOf|emptyMap)\(\s*\)', expr):
            return dict(out, form='调用处字面量', value='{}')
        m = re.search(r'R\.bool\.(\w+)', expr)
        if m and re.fullmatch(r'(?:[\w.()]*\.)?(?:getBoolean\()?\s*R\.bool\.\w+\s*\)?', expr):
            v = res.bools.get(m.group(1), {}).get('value')
            return dict(out, form='R.bool', value=v, ref=m.group(1))
        m = re.search(r'R\.integer\.(\w+)', expr)
        if m and re.fullmatch(r'(?:[\w.()]*\.)?(?:getInteger\()?\s*R\.integer\.\w+\s*\)?(?:\.to\w+\(\))?', expr):
            v = res.integers.get(m.group(1), {}).get('value')
            return dict(out, form='R.integer', value=v, ref=m.group(1))
        m = re.fullmatch(r'(?:[\w.()]*\.)?(?:key|str|getString)\(\s*R\.string\.(\w+)\s*\)|R\.string\.(\w+)', expr)
        if m:
            ref = m.group(1) or m.group(2)
            final, v = res.string(ref)
            return dict(out, form='R.string', value=v, ref=ref, resolved=final)
        m = re.fullmatch(r'(?:([A-Z]\w*(?:\.[A-Z]\w*)*)\.)?([A-Za-z_]\w*)((?:\.to\w+\(\))?)', expr)
        if m:
            qualifier, name = m.group(1), m.group(2)
            if not qualifier and name[0].islower():
                local = self.local_value(name, here, line)
                if local:
                    inner = self.default_value(local[0], here, local[1], depth + 1)
                    inner['via'] = f'局部变量 {name} ({here.display}:{local[1]})'
                    inner['expr'] = expr
                    return inner
                header = re.compile(r'\bfun\s+\w+\s*\((?:[^()]|\([^()]*\))*\b' + re.escape(name) + r'\s*:')
                for ln in range(line, max(0, line - 60), -1):
                    if header.search(here.lines[ln - 1]):
                        return dict(out, form='调用方传入', value=None, note=f'函数参数 {name}, 默认值由调用方决定')
                    if re.search(r'\bfun\s+\w+\s*\(', here.lines[ln - 1]):
                        break
            decl = self.find_decl(name, here, qualifier)
            if decl:
                f, value_expr, decl_line = decl
                inner = self.default_value(value_expr, f, decl_line, depth + 1)
                number = safe_number(value_expr)
                if inner.get('value') is None and number is not None:
                    inner = {'value': str(number)}
                if inner.get('form') in (None, '调用处字面量'):
                    inner['form'] = '伴生常量'
                inner['via'] = f'常量 {name} ({f.display}:{decl_line})'
                inner['expr'] = expr
                return inner
        m = re.fullmatch(r'(\w+(?:\.\w+)*)\.(key|preferenceValue|value|name)', expr)
        if m:
            return dict(out, form='表达式', value=None, note='枚举成员属性, 需人工核对')
        return dict(out, form='表达式', value=None)


# --------------------------------------------------------------------------------------------------------- scan

def git_head(host):
    try:
        head = subprocess.run(['git', '-C', str(host), 'rev-parse', '--short=9', 'HEAD'], capture_output=True,
                              text=True, check=True).stdout.strip()
        branch = subprocess.run(['git', '-C', str(host), 'rev-parse', '--abbrev-ref', 'HEAD'], capture_output=True,
                                text=True, check=True).stdout.strip()
        dirty = subprocess.run(['git', '-C', str(host), 'status', '--porcelain', '--', JAVA_ROOT, RES_ROOT],
                               capture_output=True, text=True, check=True).stdout.splitlines()
        return {'commit': head, 'branch': branch, 'dirtyScannedFiles': len(dirty)}
    except (OSError, subprocess.CalledProcessError):
        return {'commit': None, 'branch': None, 'dirtyScannedFiles': None}


def scan_pages(host, res):
    entries, literal_keys, page_counts = [], [], {}
    for page, label in PAGES:
        path = host / RES_ROOT / 'xml' / page
        root = parse_xml(path)
        count = set()
        for node in walk(root):
            key = node.attrs.get('app:key') or node.attrs.get('android:key')
            if not key:
                continue
            titles, p = [], node.parent
            while p is not None:
                if p.tag.endswith('PreferenceCategory'):
                    titles.append(p.attrs.get('app:title') or p.attrs.get('android:title') or '')
                p = p.parent
            titles.reverse()
            entry = {
                'page': page, 'pageLabel': label, 'line': node.line, 'tag': node.tag.split('.')[-1],
                'categoryTitleRes': [t.replace('@string/', '') for t in titles],
                'categoryTitles': [res.title(t) for t in titles],
            }
            for attr in ('defaultValue', 'itemDefaultKey', 'itemKeys'):
                v = node.attrs.get('app:' + attr) or node.attrs.get('android:' + attr)
                if v is not None:
                    entry[attr] = v
            if key.startswith('@string/'):
                entry['key'] = key[len('@string/'):]
                entries.append(entry)
                count.add(entry['key'])
            else:
                entry['literalKey'] = key
                literal_keys.append(entry)
        page_counts[page] = len(count)
    return entries, literal_keys, page_counts


def scan_other_res(host, res_names):
    """@string/key_* references in res XML other than res/values* and the 5 settings pages."""
    refs = []
    pages = {p for p, _ in PAGES}
    res_dir = host / RES_ROOT
    for path in sorted(res_dir.rglob('*.xml')):
        rel = path.relative_to(res_dir).as_posix()
        if rel.startswith('values') or (rel.startswith('xml/') and path.name in pages):
            continue
        for i, line in enumerate(path.read_text(encoding='utf-8', errors='replace').split('\n'), 1):
            for m in re.finditer(r'@string/(key_[A-Za-z0-9_]+)', line):
                if m.group(1) in res_names:
                    refs.append({'key': m.group(1), 'file': 'res/' + rel, 'line': i})
    return refs


def scan_sources(sources, res, key_values):
    refs = {}
    literals = []
    for f in sources:
        for i, line in enumerate(f.lines, 1):
            if f.is_comment_line(i):
                continue
            for m in KEY_REF.finditer(line):
                refs.setdefault(m.group(1), []).append({'file': f.display, 'line': i})
            for m in STRING_LITERAL.finditer(line):
                raw = m.group(1)
                if not KEY_LITERAL_PREFIX.match(raw):
                    continue
                if f.path.suffix == '.kt':
                    name = raw.replace('\\$', '\x00')
                    name = re.sub(r'\$\{[^}]*\}|\$[A-Za-z_]\w*', '*', name).replace('\x00', '$')
                else:
                    name = raw
                if not re.fullmatch(r'key_[\w$*]*', name) or name.startswith('key_') and len(name) <= 4:
                    continue
                const = re.search(r'\b(?:const\s+val|val|static\s+final\s+String|final\s+String)\s+([A-Za-z_]\w*)\s*'
                                  r'(?::\s*String)?\s*=\s*"' + re.escape(raw), line)
                literals.append({
                    'name': name, 'file': f.display, 'line': i, 'const': const.group(1) if const else None,
                    'resource': key_values.get(name),
                })
            # Key declarations (P2.6) also name states and caches whose names do not start with "key_", and key
            # families (P4.1) are named by such prefixes too.
            if f.display.startswith(DECLARATION_DIR):
                for m in list(DECLARED_NAME.finditer(line)) + list(DECLARED_FAMILY.finditer(line)):
                    name = m.group(1).replace(chr(92) + '$', '$')
                    if KEY_LITERAL_PREFIX.match(name):
                        continue
                    literals.append({'name': name, 'file': f.display, 'line': i, 'const': None, 'resource': key_values.get(name)})
        for path, const in LITERAL_KEY_CONSTANTS:
            if f.display != path:
                continue
            decl = re.compile(r'\bconst\s+val\s+' + re.escape(const) + r'\s*(?::\s*String\s*)?=\s*"([^"]*)"')
            for i, line in enumerate(f.lines, 1):
                m = decl.search(line)
                if m and not f.is_comment_line(i):
                    literals.append({'name': m.group(1), 'file': f.display, 'line': i, 'const': const,
                                     'resource': key_values.get(m.group(1))})
    return refs, literals


# The settings store layer (ROADMAP P1.2, T3) is where SharedPreferences are meant to be opened: its calls are not
# named stores of their own, and it is the whitelist of guard (d) for getDefaultSharedPreferences (T5).
STORE_LAYER = 'core/pref/store/'


def scan_named_stores(sources, resolver):
    calls = []
    for f in sources:
        if f.display.startswith(STORE_LAYER):
            continue
        for m in re.finditer(r'\bgetSharedPreferences\s*\(', f.text):
            line = f.line_of(m.start())
            if f.is_comment_line(line):
                continue
            args, _ = scan_args(f.text, m.end())
            if not args:
                continue
            arg = re.sub(r'\s+', ' ', args[0])
            manual = MANUAL_STORE_NAMES.get((f.display, arg))
            if manual:
                name, how = manual
                how = '人工: ' + how
            else:
                name, how = resolver.string_expr(arg, f)
            calls.append({'file': f.display, 'line': line, 'arg': arg, 'name': name, 'how': how})
    stores = {}
    for c in calls:
        for name in (c['name'] or '(未解析)').split(' | '):
            s = stores.setdefault(name, {'name': name, 'calls': [], 'files': []})
            s['calls'].append({k: c[k] for k in ('file', 'line', 'arg', 'how')})
            if c['file'] not in s['files']:
                s['files'].append(c['file'])
    # Stores declared in NamedStores (ROADMAP P3): their readers go through the store layer, so a migrated store has no
    # getSharedPreferences( call left; the declaration and the references to its constant stand for it.
    for d in scan_store_declarations(sources):
        s = stores.setdefault(d['name'], {'name': d['name'], 'calls': [], 'files': []})
        s['declared'] = d
        for r in d['refs']:
            if r['file'] not in s['files']:
                s['files'].append(r['file'])
    return calls, [stores[k] for k in sorted(stores)]


NAMED_STORES_FILE = 'core/pref/keys/NamedStores.kt'
STORE_DECLARATION = re.compile(r'\bval\s+(\w+)\s*=\s*(store|excluded)\(\s*"([^"]+)"')


def scan_store_declarations(sources):
    decl = next((f for f in sources if f.display == NAMED_STORES_FILE), None)
    if decl is None:
        return []
    out = []
    for i, line in enumerate(decl.lines, 1):
        m = STORE_DECLARATION.search(line)
        if not m or decl.is_comment_line(i):
            continue
        const, how, name = m.groups()
        name = name.replace(chr(92) + '$', '$')
        ref = re.compile(r'\bNamedStores\.' + re.escape(const) + r'\b')
        refs = [{'file': f.display, 'line': j} for f in sources if f.display != NAMED_STORES_FILE
                for j, text in enumerate(f.lines, 1) if ref.search(text) and not f.is_comment_line(j)]
        out.append({'name': name, 'const': const, 'file': decl.display, 'line': i, 'excluded': how == 'excluded',
                    'refs': refs})
    return out


def scan_default_bypass(sources):
    out = []
    for f in sources:
        if f.display.startswith(STORE_LAYER):
            continue
        lines = [i for i, line in enumerate(f.lines, 1)
                 if 'getDefaultSharedPreferences' in line and not f.is_comment_line(i)]
        if lines:
            out.append({'file': f.display, 'lines': lines})
    return out


def scan_pref_users(sources):
    users = []
    for f in sources:
        if f.display in ('core/pref/Pref.kt', 'inrt/Pref.kt') or f.display.startswith(STORE_LAYER):
            continue
        if re.search(r'(?<![\w.])Pref\.\w', f.text):
            users.append(f.display)
    return users


def scan_json_files(sources):
    out = []
    by_display = {f.display: f for f in sources}
    for f in sources:
        dir_lines = [i for i, line in enumerate(f.lines, 1) if FILES_DIR.search(line) and not f.is_comment_line(i)]
        owner = by_display.get(MANUAL_JSON_FILE_SOURCES.get(f.display))
        if not dir_lines and owner is not None and any(
                FILES_DIR.search(line) and not owner.is_comment_line(i) for i, line in enumerate(owner.lines, 1)):
            dir_lines = ['目录来自 ' + owner.display]
        if not dir_lines:
            continue
        names = []
        for i, line in enumerate(f.lines, 1):
            if f.is_comment_line(i):
                continue
            for m in JSON_LITERAL.finditer(line):
                names.append({'name': re.sub(r'\$\{[^}]*\}|\$[A-Za-z_]\w*', '*', m.group(1)), 'line': i})
        if names:
            out.append({'file': f.display, 'class': f.stem, 'dirLines': dir_lines, 'jsonNames': names})
    detected = {e['class'] for e in out}
    by_stem = {f.stem: f.display for f in sources}
    missing = [{'class': c, 'file': by_stem.get(c)} for c in ROADMAP_JSON_STORES if c not in detected]
    extra = [e['class'] for e in out if e['class'] not in ROADMAP_JSON_STORES]
    return out, missing, extra


JSON_FILE_STORES_FILE = 'core/pref/keys/JsonFileStores.kt'
JSON_FILE_STORE_DECLARATION = re.compile(r'\bval\s+(\w+)\s*=\s*excluded\(\s*"([^"]+)"')


def scan_json_store_declarations(sources):
    """The JSON file stores declared in JsonFileStores (ROADMAP P5.1): [{name, const, line}]."""
    decl = next((f for f in sources if f.display == JSON_FILE_STORES_FILE), None)
    if decl is None:
        return []
    return [{'name': m.group(2), 'const': m.group(1), 'line': i} for i, line in enumerate(decl.lines, 1)
            for m in [JSON_FILE_STORE_DECLARATION.search(line)] if m and not decl.is_comment_line(i)]


def scan_defaults(sources, resolver, key_values, literal_by_const):
    """Default value sources from getter calls: {pref key name: [source, ...]}."""
    found = {}
    for f in sources:
        for m in GETTER.finditer(f.text):
            line = f.line_of(m.start())
            if f.is_comment_line(line):
                continue
            method = m.group(1)
            args, _ = scan_args(f.text, m.end())
            if not args:
                continue
            first = re.sub(r'\s+', ' ', args[0])
            km = KEY_REF.search(first)
            key = km.group(1) if km and re.fullmatch(r'(?:[\w.()]*\.)?(?:key|getString)?\(?\s*R\.string\.key_\w+\s*\)?',
                                                     first) else None
            literal = None
            if key is None:
                im = re.fullmatch(r'(?:([A-Z]\w*)\.)?([A-Za-z_]\w*)', first)
                if not im:
                    continue
                value, _how = resolver.string_expr(first, f)
                if value is None:
                    local = resolver.local_value(im.group(2), f, line)
                    if local:
                        value, _how = resolver.string_expr(local[0], f)
                if value is None:
                    continue
                key = key_values.get(value)
                if key is None:
                    if not value.startswith('key_'):
                        continue
                    literal = value
            if len(args) > 1:
                default = resolver.default_value(args[1], f, line)
            elif method in IMPLICIT_DEFAULTS:
                default = {'form': '调用处字面量', 'expr': '(方法默认)', 'value': IMPLICIT_DEFAULTS[method]}
            else:
                continue
            default.update({'file': f.display, 'line': line, 'method': method, 'type': GETTER_TYPES[method]})
            found.setdefault(key or literal, []).append(default)
    return found


# Key declarations of the domain objects (docs/settings/ROADMAP.md P2): `SettingKey.Bool(R.string.key_x, CATEGORY,
# <default>, ...)` or with `name = "key_$_..."`; the default is the third positional argument (the fourth for Enum and
# Json, after the type) or the named argument `default`.
DECLARATION = re.compile(r'\bSettingKey\.(Bool|Int|Long|Float|Str|StrSet|Enum|Json)\s*\(')
DECLARED_TYPES = {'Bool': 'Boolean', 'Int': 'Int', 'Long': 'Long', 'Float': 'Float', 'Str': 'String',
                  'StrSet': 'StringSet', 'Enum': 'String (Enum)', 'Json': 'Json'}
DECLARATION_DIR = 'core/pref/keys/'
DECLARED_NAME = re.compile(r'\bname\s*=\s*"((?:[^"\\]|\\.)*)"')
DECLARED_FAMILY = re.compile(r'\bKeyFamily\(\s*"((?:[^"\\]|\\.)+)"')
# A key family (ROADMAP P3) declares its members with the factory parameter as the name:
# `KeyFamily("key_$_prefix_") { name -> SettingKey.Bool(name = name, ...) }`; the default belongs to the prefix.
FAMILY_DECLARATION = re.compile(r'\bKeyFamily\(\s*"([^"]*)"\s*\)\s*\{\s*(\w+)\s*->\s*$')
RESOURCE_DEFAULT = re.compile(r'(bool|int|string|key)Resource\(\s*R\.(bool|integer|string)\.(\w+)\s*,')
NAMED_ARG = re.compile(r'^(\w+)\s*=(?!=)\s*(.*)$', re.S)


def declared_default(expr, resolver, res, f, line):
    """dict(form, expr, value, ...) for the default argument of a key declaration."""
    expr = re.sub(r'\s+', ' ', expr.strip())
    m = RESOURCE_DEFAULT.match(expr)
    if m:
        kind, ref = m.group(2), m.group(3)
        if kind == 'bool':
            return {'form': 'R.bool', 'ref': ref, 'value': res.bools.get(ref, {}).get('value'), 'expr': expr}
        if kind == 'integer':
            return {'form': 'R.integer', 'ref': ref, 'value': res.integers.get(ref, {}).get('value'), 'expr': expr}
        final, value = res.string(ref)
        return {'form': 'R.string', 'ref': ref, 'resolved': final, 'value': value, 'expr': expr}
    m = re.match(r'SettingDefault\.computed\(\s*"((?:[^"\\]|\\.)*)"', expr)
    if m:
        source = m.group(1).replace(chr(92) + '$', '$')
        # Evaluated at run time, so it takes no part in the conflict check (like the run-time extraDefaults).
        return {'form': '表达式', 'expr': source, 'value': '运行时: ' + source, 'ignoreForConflict': True}
    m = re.fullmatch(r'SettingDefault\.of\((.*)\)', expr)
    if m:
        inner = resolver.default_value(m.group(1), f, line)
        inner['expr'] = expr
        return inner
    if expr == 'SettingDefault.None':
        return {'form': '声明', 'expr': expr, 'value': 'null'}
    return {'form': '表达式', 'expr': expr, 'value': None}


def scan_declared_defaults(sources, resolver, res):
    """Default value sources of the key declarations in core/pref/keys: {pref key name: [source, ...]}."""
    found = {}
    for f in sources:
        if not f.display.startswith(DECLARATION_DIR):
            continue
        for m in DECLARATION.finditer(f.text):
            line = f.line_of(m.start())
            if f.is_comment_line(line):
                continue
            args, _ = scan_args(f.text, m.end())
            named = {}
            positional = []
            for a in args:
                nm = NAMED_ARG.match(a)
                if nm and nm.group(1) in ('keyRes', 'category', 'default', 'effect', 'sensitive', 'deployment', 'store',
                                         'name', 'enumType', 'jsonType'):
                    named[nm.group(1)] = nm.group(2).strip()
                else:
                    positional.append(a)
            key = None
            if 'name' in named:
                lm = re.fullmatch(r'"((?:[^"\\]|\\.)*)"', named['name'])
                key = lm.group(1).replace(chr(92) + '$', '$') if lm else None
                if lm is None:
                    # The factory of a key family: the prefix is on the line that opens the lambda.
                    fm = FAMILY_DECLARATION.search(f.lines[line - 2]) if line >= 2 else None
                    if fm and fm.group(2) == named['name']:
                        key = fm.group(1).replace(chr(92) + '$', '$')
            else:
                km = re.fullmatch(r'R\.string\.(key_\w+)', named.get('keyRes', positional[0] if positional else ''))
                key = km.group(1) if km else None
            if key is None:
                continue
            kind = m.group(1)
            index = 3 if kind in ('Enum', 'Json') else 2
            if 'default' in named:
                default_expr = named['default']
            elif len(positional) > index:
                default_expr = positional[index]
            else:
                continue
            d = declared_default(default_expr, resolver, res, f, line)
            d.update({'file': f.display, 'line': line, 'method': 'SettingKey.' + kind, 'type': DECLARED_TYPES[kind]})
            found.setdefault(key, []).append(d)
    return found


def xml_defaults(page_entries, res):
    out = {}
    for e in page_entries:
        if 'defaultValue' in e:
            v = e['defaultValue']
            if v.startswith('@bool/'):
                d = {'form': 'R.bool', 'ref': v[6:], 'value': res.bools.get(v[6:], {}).get('value')}
            elif v.startswith('@integer/'):
                d = {'form': 'R.integer', 'ref': v[9:], 'value': res.integers.get(v[9:], {}).get('value')}
            elif v.startswith('@string/'):
                final, value = res.string(v[8:])
                d = {'form': 'R.string', 'ref': v[8:], 'resolved': final, 'value': value}
            else:
                d = {'form': 'XML 字面量', 'value': v}
            d.update({'expr': 'app:defaultValue="%s"' % v, 'file': 'res/xml/' + e['page'], 'line': e['line'],
                      'method': e['tag'], 'type': 'Boolean' if 'Switch' in e['tag'] else None})
            out.setdefault(e['key'], []).append(d)
        if 'itemDefaultKey' in e:
            v = e['itemDefaultKey']
            final, value = res.string(v[8:]) if v.startswith('@string/') else (None, v)
            out.setdefault(e['key'], []).append({
                'form': 'R.string', 'ref': v.replace('@string/', ''), 'resolved': final, 'value': value,
                'expr': 'app:itemDefaultKey="%s"' % v, 'file': 'res/xml/' + e['page'], 'line': e['line'],
                'method': e['tag'], 'type': 'String (Enum)'})
    return out


def build(host, date):
    res = Resources(host / RES_ROOT)
    keys = {}
    for name, entry in res.strings.items():
        if name.startswith('key_'):
            keys[name] = {'name': name, 'value': entry['value'], 'file': entry['file'], 'line': entry['line']}
    key_values = {}
    for name in sorted(keys):
        key_values.setdefault(keys[name]['value'], name)

    page_entries, page_literal_keys, page_counts = scan_pages(host, res)
    for e in page_entries:
        if e['key'] in keys:
            keys[e['key']].setdefault('pages', []).append(
                {k: e[k] for k in ('page', 'pageLabel', 'line', 'tag', 'categoryTitleRes', 'categoryTitles')})

    array_owner = {}
    for e in page_entries:
        if 'itemKeys' in e:
            array_owner[e['itemKeys'].replace('@array/', '')] = e['key']
    for array_name, entry in sorted(res.arrays.items()):
        for item in entry['items']:
            if item.startswith('@string/key_') and item[8:] in keys:
                keys[item[8:]].setdefault('arrays', []).append(
                    {'array': array_name, 'owner': array_owner.get(array_name)})
    for name, entry in sorted(res.strings.items()):
        if name.startswith('default_key_') and entry['value'].startswith('@string/key_'):
            target = entry['value'][8:]
            if target in keys:
                keys[target].setdefault('defaultOf', []).append(name)

    sources = load_sources(host)
    resolver = Resolver(sources, res)
    refs, literals = scan_sources(sources, res, key_values)
    for name, r in refs.items():
        if name in keys:
            keys[name]['sourceRefs'] = r
    for ref in scan_other_res(host, set(keys)):
        keys[ref['key']].setdefault('otherResRefs', []).append({'file': ref['file'], 'line': ref['line']})

    literal_groups = {}
    for lit in literals:
        if lit['resource']:
            keys[lit['resource']].setdefault('literalRefs', []).append({'file': lit['file'], 'line': lit['line'],
                                                                        'const': lit['const']})
            continue
        g = literal_groups.setdefault(lit['name'], {'name': lit['name'], 'refs': []})
        g['refs'].append({'file': lit['file'], 'line': lit['line'], 'const': lit['const']})

    calls, stores = scan_named_stores(sources, resolver)
    store_files = {}
    for c in calls:
        store_files.setdefault(c['file'], set()).update((c['name'] or '(未解析)').split(' | '))
    for g in literal_groups.values():
        names = set()
        for r in g['refs']:
            names |= store_files.get(r['file'], {'default'})
        g['storeGuess'] = sorted(names)

    json_files, json_missing, json_extra = scan_json_files(sources)
    json_declarations = scan_json_store_declarations(sources)
    literal_by_const = {}
    defaults = scan_defaults(sources, resolver, key_values, literal_by_const)
    for name, d in scan_declared_defaults(sources, resolver, res).items():
        defaults.setdefault(name, []).extend(d)
    for name, d in xml_defaults(page_entries, res).items():
        defaults.setdefault(name, []).extend(d)

    xml_keys = {e['key'] for e in page_entries}
    donottranslate = [k for k in keys.values() if k['file'] == 'strings_donottranslate.xml']
    summary = {
        'keyResources': len(keys),
        'keyResourcesInStringsDonottranslate': len(donottranslate),
        'keyResourcesElsewhere': sorted(k['name'] for k in keys.values() if k['file'] != 'strings_donottranslate.xml'),
        'keysOnSettingsPages': len(xml_keys & set(keys)),
        'keysPerPage': page_counts,
        'keysOnlyInCode': len(set(keys) - xml_keys),
        'pageLiteralKeysPerPage': {p: sum(1 for e in page_literal_keys if e['page'] == p) for p, _ in PAGES},
        'keysWithoutSourceRefs': sorted(k for k in keys if k not in refs and not keys[k].get('literalRefs')),
        'keysWithoutAnyRefs': sorted(k for k in keys if k not in refs and not any(
            keys[k].get(f) for f in ('literalRefs', 'pages', 'arrays', 'defaultOf', 'otherResRefs'))),
        'literalKeyGroups': len(literal_groups),
        'namedStores': len([s for s in stores if s['name'] != '(未解析)']),
        'namedStoreCallFiles': len({c['file'] for c in calls}),
        'namedStoresDeclared': len([s for s in stores if s.get('declared')]),
        'unresolvedStoreCalls': [c for c in calls if c['name'] is None],
        'defaultPrefsBypassFiles': None,
        'prefUserFiles': None,
        'jsonFileStores': len(json_files),
        'jsonFileStoresDeclared': len(json_declarations),
    }
    bypass = scan_default_bypass(sources)
    pref_users = scan_pref_users(sources)
    summary['defaultPrefsBypassFiles'] = len([b for b in bypass if b['file'] != 'core/pref/Pref.kt'])
    summary['prefUserFiles'] = len(pref_users)

    return {
        'schema': SCHEMA,
        'date': date,
        'git': git_head(host),
        'summary': summary,
        'keys': [keys[k] for k in sorted(keys)],
        'pageLiteralKeys': page_literal_keys,
        'literalKeys': [literal_groups[k] for k in sorted(literal_groups)],
        'namedStores': stores,
        'defaultPrefsBypass': bypass,
        'prefUsers': pref_users,
        'jsonFileStores': json_files,
        'jsonFileStoresReconcile': {'roadmapNotDetected': json_missing, 'detectedNotInRoadmap': json_extra},
        'jsonFileStoreDeclarations': json_declarations,
        'defaults': {k: defaults[k] for k in sorted(defaults)},
    }


# ------------------------------------------------------------------------------------------------ classification

def load_classification(host):
    path = host / 'tools' / 'settings' / 'classification.json'
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding='utf-8'))


def merge_classification(data, cls):
    problems = []
    if cls is None:
        return problems
    sections = (('keys', 'keys', 'name'), ('literalKeys', 'literalKeys', 'name'), ('namedStores', 'namedStores', 'name'),
                ('jsonFileStores', 'jsonFileStores', 'file'), ('pageLiteralKeys', 'pageLiteralKeys', 'literalKey'))
    for data_key, cls_key, id_field in sections:
        table = cls.get(cls_key, {})
        seen = set()
        for item in data[data_key]:
            ident = item[id_field]
            c = table.get(ident)
            seen.add(ident)
            if c is None:
                problems.append(f'{cls_key}: {ident} 未分类')
                continue
            for field in ('kind', 'category', 'note', 'effect', 'store', 'deployment', 'storedAs', 'stores', 'otherJsonNames'):
                if field in c:
                    item[field] = c[field]
            if c.get('kind') not in KINDS:
                problems.append(f'{cls_key}: {ident} kind 无效: {c.get("kind")}')
            if c.get('category') is not None and (c['category'] not in CATEGORIES or c['category'] in DATA_CATEGORIES):
                problems.append(f'{cls_key}: {ident} 分类 id 无效: {c["category"]}')
            if c.get('deployment') is not None and (c['deployment'] not in DEPLOYMENTS or c.get('kind') != 'Setting'):
                problems.append(f'{cls_key}: {ident} deployment 无效或不是 Setting: {c["deployment"]}')
            if c.get('kind') in ('Setting', 'State') and not c.get('category'):
                problems.append(f'{cls_key}: {ident} 缺分类 id')
        for ident in sorted(set(table) - seen):
            if not ident.startswith('_'):
                problems.append(f'{cls_key}: {ident} 在扫描结果中不存在')
    # JSON file stores (ROADMAP P5.1): the stores a source claims are declared in JsonFileStores, every declaration is
    # claimed by exactly one source, and a JSON file store is a Cache or Excluded (it holds no settings or states).
    declared = {d['name'] for d in data.get('jsonFileStoreDeclarations', [])}
    claims = [s for c in cls.get('jsonFileStores', {}).values() for s in c.get('stores', [])]
    for s in sorted(set(claims) - declared):
        problems.append(f'jsonFileStores: {s} 未在 JsonFileStores 声明')
    for s in sorted(declared - set(claims)):
        problems.append(f'jsonFileStores: JsonFileStores 声明的 {s} 没有源文件认领')
    for s in sorted({s for s in claims if claims.count(s) > 1}):
        problems.append(f'jsonFileStores: {s} 被多个源文件认领')
    for ident, c in cls.get('jsonFileStores', {}).items():
        if c.get('kind') not in ('Cache', 'Excluded'):
            problems.append(f'jsonFileStores: {ident} kind 应为 Cache 或 Excluded: {c.get("kind")}')
    # An extra default with "match" completes the scanned sources whose expression equals it (value, form, note);
    # one without "match" is appended as a source of its own.
    for name, extra in cls.get('extraDefaults', {}).items():
        sources = data['defaults'].setdefault(name, [])
        for e in extra:
            if 'match' in e:
                hits = [s for s in sources if s.get('expr') == e['match']]
                if not hits:
                    problems.append(f'extraDefaults: {name} 没有表达式为 {e["match"]} 的来源')
                for s in hits:
                    s.update({k: v for k, v in e.items() if k != 'match'})
                    s['manual'] = True
            else:
                sources.append(dict(e, manual=True))
    data['categoryCounts'] = category_counts(data)
    return problems


def category_counts(data):
    counts = {c: {'keys': 0, 'literalKeys': 0, 'stores': 0} for c in CATEGORIES}
    for k in data['keys']:
        if k.get('kind') in ('Setting', 'State') and k.get('category') in counts:
            counts[k['category']]['keys'] += 1
        # A candidate value that also names a stored state (P4.1, "storedAs") is one entry of that category too.
        stored = k.get('storedAs') or {}
        if stored.get('kind') in ('Setting', 'State') and stored.get('category') in counts:
            counts[stored['category']]['keys'] += 1
    for k in data['literalKeys']:
        # Literal keys inside a named store are counted with that store ("one store = one entry").
        store = k.get('store') or (k['storeGuess'][0] if len(k['storeGuess']) == 1 else None)
        if store == 'default' and k.get('kind') in ('Setting', 'State') and k.get('category') in counts:
            counts[k['category']]['literalKeys'] += 1
    for s in data['namedStores']:
        if s.get('kind') in ('Setting', 'State') and s.get('category') in counts:
            counts[s['category']]['stores'] += 1
    return counts


# ----------------------------------------------------------------------------------------------------- markdown

def md_escape(text):
    return str(text).replace('|', '\\|').replace('\n', ' ')


def code(text):
    return '`%s`' % str(text).replace('`', "'")


def refs_cell(refs, limit=6):
    if not refs:
        return ''
    items = ['%s:%d' % (r['file'], r['line']) for r in refs]
    if len(items) > limit:
        return ', '.join(items[:limit]) + ' 等 %d 处' % len(items)
    return ', '.join(items)


def category_cell(item):
    if not item.get('category'):
        return ''
    cell = code(item['category'])
    if item.get('deployment'):
        cell += ', deployment %s' % code(item['deployment'])
    return cell


def page_cell(k):
    parts = []
    for p in k.get('pages', []):
        titles = ' > '.join(p['categoryTitles'])
        parts.append('%s > %s' % (p['pageLabel'], titles) if titles else p['pageLabel'])
    for a in k.get('arrays', []):
        owner = a['owner']
        parts.append('候选值 %s%s' % (code(a['array']), ' (%s)' % code(owner) if owner else ''))
    if k.get('defaultOf'):
        parts.append('默认值 ' + ', '.join(code(d) for d in k['defaultOf']))
    if k.get('otherResRefs'):
        parts.append('其他资源 ' + ', '.join('%s:%d' % (r['file'], r['line']) for r in k['otherResRefs']))
    return '; '.join(parts)


def render_inventory(data):
    s = data['summary']
    g = data['git']
    out = []
    w = out.append
    w('# 设置清单 (inventory)')
    w('')
    w('由 `tools/settings/inventory.py` 生成, 不要手工编辑; kind / 分类 id / 备注来自 `tools/settings/classification.json`. '
      '路线图见 [ROADMAP.md](ROADMAP.md) (P0.2 / P0.3).')
    w('')
    w('- 生成日期: %s, 扫描基线: `%s@%s` (扫描范围内未提交文件 %s 个)' % (
        data['date'], g['branch'], g['commit'], g['dirtyScannedFiles']))
    w('- 源码路径相对 `app/src/main/java/org/autojs/autojs/` (其他包保留 `com/...` 等前缀); 资源路径相对 `app/src/main/`.')
    w('- 引用处只计非注释行; 超过 6 处时只列前 6 处, 完整列表见 JSON 基线.')
    w('- kind: Setting (用户设置), State (记忆型状态, 归 `other`), Cache (缓存, 不可导出), ValueConstant (设置值常量, '
      '列表项候选值或默认值别名), Excluded (不是偏好键或永久排除, 见 ROADMAP 附录 A.2).')
    w('')
    w('## 1. 概况')
    w('')
    w('| 指标 | 数值 |')
    w('| --- | --- |')
    w('| `key_*` 字符串资源 | %d (`strings_donottranslate.xml` %d, 其他文件 %d: %s) |' % (
        s['keyResources'], s['keyResourcesInStringsDonottranslate'], len(s['keyResourcesElsewhere']),
        ', '.join(code(k) for k in s['keyResourcesElsewhere']) or '无'))
    w('| 出现在设置页 XML 的 `key_*` 键 (各页之和去重) | %d (%s) |' % (s['keysOnSettingsPages'], ', '.join(
        '%s %d' % (p.replace('fragment_', '').replace('.xml', ''), n) for p, n in s['keysPerPage'].items())))
    w('| 设置页中不经字符串资源的字面量键 | %d (%s) |' % (sum(s['pageLiteralKeysPerPage'].values()), ', '.join(
        '%s %d' % (p.replace('fragment_', '').replace('.xml', ''), n)
        for p, n in s['pageLiteralKeysPerPage'].items() if n)))
    w('| 不在设置页的 `key_*` 键 | %d |' % s['keysOnlyInCode'])
    w('| 源码 (`app/src/main/java`) 没有引用的键 | %d (其中设置页, 数组与其他资源也没有引用的 %d 个: %s) |' % (
        len(s['keysWithoutSourceRefs']), len(s['keysWithoutAnyRefs']),
        ', '.join(code(k) for k in s['keysWithoutAnyRefs']) or '无'))
    w('| 源码字面量键 (不对应 `key_*` 资源) | %d |' % s['literalKeyGroups'])
    w('| 命名 SharedPreferences / 调用文件 / 已登记 `StoreId` | %d / %d / %d |' % (
        s['namedStores'], s['namedStoreCallFiles'], s['namedStoresDeclared']))
    w('| 绕过 `Pref` 读默认偏好的文件 | %d |' % s['defaultPrefsBypassFiles'])
    w('| 经 `Pref.` 读写的文件 (不含两个 `Pref.kt`) | %d |' % s['prefUserFiles'])
    w('| filesDir / noBackupFilesDir 旁出现 JSON 文件名的源文件 | %d |' % s['jsonFileStores'])
    w('| `JsonFileStores` 声明的 JSON 文件存储 (P5.1) | %d |' % s.get('jsonFileStoresDeclared', 0))
    w('')
    if 'categoryCounts' in data:
        kinds = {}
        for k in data['keys']:
            kinds[k.get('kind')] = kinds.get(k.get('kind'), 0) + 1
        w('kind 统计 (`key_*` 资源): ' + ', '.join('%s %d' % (k, kinds.get(k, 0)) for k in KINDS) +
          ('' if None not in kinds else ', 未分类 %d' % kinds[None]))
        w('')
    w('## 2. `key_*` 字符串资源')
    w('')
    w('| 键 | 出现页面与分类 | 引用处 | kind | 分类 id | 备注 |')
    w('| --- | --- | --- | --- | --- | --- |')
    for k in data['keys']:
        refs = k.get('sourceRefs', []) + [dict(r, file=r['file']) for r in k.get('literalRefs', [])]
        w('| %s | %s | %s | %s | %s | %s |' % (
            code(k['name']), md_escape(page_cell(k)), md_escape(refs_cell(refs)), k.get('kind', ''),
            category_cell(k), md_escape(k.get('note', ''))))
    w('')
    w('## 3. 源码字面量键')
    w('')
    w('字符串字面量形如 `"key_$_..."` 且不等于任何 `key_*` 资源值的键 (`*` 为模板变量). 存储列是按调用文件推测的结果: '
      '文件内调用了 `getSharedPreferences(` 时取其文件名, 否则记为 `default`.')
    w('')
    w('| 键 | 存储 (推测) | 引用处 | kind | 分类 id | 备注 |')
    w('| --- | --- | --- | --- | --- | --- |')
    for k in data['literalKeys']:
        w('| %s | %s | %s | %s | %s | %s |' % (
            code(k['name']), md_escape(k.get('store') or ', '.join(k['storeGuess'])), md_escape(refs_cell(k['refs'])),
            k.get('kind', ''), code(k['category']) if k.get('category') else '', md_escape(k.get('note', ''))))
    w('')
    if data['pageLiteralKeys']:
        w('设置页中不经字符串资源的键: ' + ', '.join(
            '%s (%s:%d, kind %s)' % (code(e['literalKey']), e['page'], e['line'], e.get('kind', '未分类'))
            for e in data['pageLiteralKeys']))
        w('')
    w('## 4. 命名 SharedPreferences')
    w('')
    w('| 文件名 | 解析方式 | 调用处 | kind | 分类 id | 备注 |')
    w('| --- | --- | --- | --- | --- | --- |')
    for st in data['namedStores']:
        hows = sorted({c['how'] for c in st['calls']})
        refs = st['calls']
        d = st.get('declared')
        if d:
            hows.insert(0, '%s `NamedStores.%s` (%s:%d)' % ('排除登记' if d['excluded'] else '登记', d['const'], d['file'], d['line']))
            refs = refs + d['refs']
        w('| %s | %s | %s | %s | %s | %s |' % (
            code(st['name']), md_escape('; '.join(hows)), md_escape(refs_cell(refs, 4)), st.get('kind', ''),
            code(st['category']) if st.get('category') else '', md_escape(st.get('note', ''))))
    w('')
    w('## 5. 绕过 `Pref` 读默认偏好的文件')
    w('')
    for b in data['defaultPrefsBypass']:
        w('- %s:%s' % (b['file'], ','.join(str(x) for x in b['lines'])))
    w('')
    w('## 6. filesDir / noBackupFilesDir 下的 JSON 文件')
    w('')
    w('启发式: 源文件在非注释行引用 `filesDir` / `noBackupFilesDir` 且含 `.json` 字面量; 目录由其他源文件传入的见 '
      '`MANUAL_JSON_FILE_SOURCES`. 与 ROADMAP 1.1 的 14 个对照见表后. "登记" 列为源文件认领的 `JsonFileStores` 排除存储 '
      '(P5.1; 名称为相对 `filesDir` 的路径, `../no_backup/` 即 `noBackupFilesDir`, `*` 为运行时拼出的部分).')
    w('')
    w('| 源文件 | JSON 文件名 | 目录引用行 | 登记 (`JsonFileStores`) | kind | 分类 id | 备注 |')
    w('| --- | --- | --- | --- | --- | --- | --- |')
    decl = {d['name']: d for d in data.get('jsonFileStoreDeclarations', [])}
    for j in data['jsonFileStores']:
        names = sorted({n['name'] for n in j['jsonNames']})
        stores = j.get('stores')
        registered = '无 (不是存储)' if stores == [] else ', '.join(
            '%s `%s`' % (code(s), decl[s]['const']) if s in decl else code(s) for s in stores or [])
        w('| %s | %s | %s | %s | %s | %s | %s |' % (
            j['file'], ', '.join(code(n) for n in names), ','.join(str(x) for x in j['dirLines'][:6]), registered,
            j.get('kind', ''), code(j['category']) if j.get('category') else '', md_escape(j.get('note', ''))))
    rec = data['jsonFileStoresReconcile']
    w('')
    w('- ROADMAP 列出但启发式未命中: ' + (', '.join(
        '%s (%s)' % (code(m['class']), m['file']) for m in rec['roadmapNotDetected']) or '无') +
      ' (目录或 JSON 文件名在其他类中拼接, 归属以 ROADMAP 为准)')
    w('- 启发式命中但 ROADMAP 未列出: ' + (', '.join(code(c) for c in rec['detectedNotInRoadmap']) or '无'))
    w('')
    if 'categoryCounts' in data:
        w('## 7. 分类条目数 (回填 ROADMAP 附录 A)')
        w('')
        w('Setting 与 State 计入条目数; 默认偏好中的字面量键 (含前缀键, 每组算 1 条) 单独计数, 命名存储每份算 1 条, '
          '存储内部的键随整份计数. 数据分类 (D21) 不含键, 条目数在运行时按条 / 按库计算.')
        w('')
        w('| 分类 id | `key_*` 键 | 字面量键 (默认偏好) | 命名存储 | 合计 | 部署项 |')
        w('| --- | --- | --- | --- | --- | --- |')
        deployments = {}
        for item in data['keys'] + data['literalKeys']:
            if item.get('deployment') and item.get('category'):
                deployments.setdefault(item['category'], []).append('%s (%s)' % (code(item['deployment']),
                                                                                code(item['name'])))
        for cid, c in data['categoryCounts'].items():
            total = '数据' if cid in DATA_CATEGORIES else str(c['keys'] + c['literalKeys'] + c['stores'])
            w('| %s | %d | %d | %d | %s | %s |' % (code(cid), c['keys'], c['literalKeys'], c['stores'], total,
                                                ', '.join(deployments.get(cid, []))))
        w('')
    return '\n'.join(out)


def fmt_value(d):
    v = d.get('value')
    if v is None:
        return '(由调用方决定)' if d.get('form') == '调用方传入' else '(需人工核对)'
    text = str(v) if str(v) != '' else '""'
    if d.get('resolved') and d.get('resolved') != d.get('ref'):
        text = '%s = %s' % (d['resolved'], text)
    return text


def norm_value(v):
    if v is None:
        return None
    s = str(v).strip()
    n = safe_number(s)
    return str(n) if n is not None else s


def render_defaults(data, cls):
    out = []
    w = out.append
    keys = {k['name']: k for k in data['keys']}
    settings = [k for k in data['keys'] if k.get('kind') == 'Setting']
    literal_settings = [k for k in data['literalKeys'] if k.get('kind') == 'Setting' and k.get('store') == 'default']
    w('# 设置默认值表 (defaults)')
    w('')
    w('由 `tools/settings/inventory.py` 生成, 不要手工编辑; 人工补充的来源写在 `tools/settings/classification.json` 的 '
      '`extraDefaults`. 路线图见 [ROADMAP.md](ROADMAP.md) (P0.4, 红线 R2).')
    w('')
    w('- 生成日期: %s, 扫描基线: `%s@%s`' % (data['date'], data['git']['branch'], data['git']['commit']))
    w('- 来源形式: `R.bool` / `R.integer` / `R.string` (含 `default_key_*` 别名) / 伴生常量 / 调用处字面量 / '
      'XML 字面量 (设置页 `app:defaultValue`); "表达式" 表示运行时计算, 取值列写明依据.')
    w('- 自动提取规则: `getBoolean` / `getInt` / `getLong` / `getFloat` / `getString` / `getStringSet` 等调用的第一个参数'
      '解析为键 (`R.string.key_*`, 常量或局部变量), 第二个参数经资源, 常量与局部变量求值; 设置页的 `app:defaultValue` 与 '
      '`app:itemDefaultKey` 单独成行. 人工补充行标 "(人工)".')
    w('- 范围: kind 为 Setting 的 `key_*` 键与默认偏好中的字面量键; 命名存储整份作为条目, 其内部默认值不在本表.')
    w('')
    w('## 1. 默认值来源')
    w('')
    w('| 键 | 类型 | 来源形式 | 表达式 | 取值 | 位置 | 备注 |')
    w('| --- | --- | --- | --- | --- | --- | --- |')
    conflicts = []
    missing = []
    for k in settings + literal_settings:
        name = k['name']
        sources = data['defaults'].get(name, [])
        if not sources:
            missing.append(name)
            w('| %s | | (未找到) | | | | |' % code(name))
            continue
        for d in sources:
            expr = d.get('expr', '')
            if d.get('via'):
                expr += ' (经 %s)' % d['via']
            w('| %s | %s | %s | %s | %s | %s | %s |' % (
                code(name), d.get('type') or '', d.get('form', '') + (' (人工)' if d.get('manual') else ''),
                md_escape(code(expr) if expr else ''), md_escape(code(fmt_value(d))),
                '%s:%s' % (d['file'], d.get('line', '')) if d.get('file') else '', md_escape(d.get('note', ''))))
        values = {}
        for d in sources:
            if d.get('ignoreForConflict'):
                continue
            nv = norm_value(d.get('value'))
            # `null` means "no default here, the caller falls back" (getStringOrNull, change detection); not compared.
            if nv is not None and nv != 'null':
                values.setdefault(nv, []).append(d)
        if len(values) > 1:
            conflicts.append((name, values))
    w('')
    w('## 2. 同一键多处默认值不一致')
    w('')
    w('只比较能求出取值的来源; 取值为 `null` 的行 (`getStringOrNull` 或变更检测, 调用处自行回退) 不参与比较. '
      '逐条登记在 ROADMAP 附录 C Q10, 由维护者定统一值; P1-P5 期间保持现状 (红线 R2).')
    w('')
    if not conflicts:
        w('无.')
    for i, (name, values) in enumerate(conflicts, 1):
        w('%d. %s: ' % (i, code(name)) + '; '.join(
            '%s <- %s' % (code(v), ', '.join('%s:%s' % (d.get('file', '?'), d.get('line', '?')) for d in ds))
            for v, ds in values.items()))
    w('')
    if missing:
        w('## 3. 未找到默认值来源的 Setting')
        w('')
        w(', '.join(code(m) for m in missing))
        w('')
    return '\n'.join(out), conflicts, missing


# ---------------------------------------------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--host', type=Path, default=HOST)
    parser.add_argument('--date', default=datetime.date.today().isoformat())
    parser.add_argument('--json', type=Path)
    parser.add_argument('--markdown', type=Path)
    parser.add_argument('--defaults-markdown', type=Path)
    parser.add_argument('--check', action='store_true', help='exit 2 when the classification is incomplete or stale')
    args = parser.parse_args()

    host = args.host.resolve()
    data = build(host, args.date)
    cls = load_classification(host)
    problems = merge_classification(data, cls)
    if cls is not None:
        data['classificationProblems'] = problems

    json_path = args.json or host / 'tools' / 'settings' / 'baseline' / f'inventory-{args.date}.json'
    md_path = args.markdown or host / 'docs' / 'settings' / 'inventory.md'
    defaults_path = args.defaults_markdown or host / 'docs' / 'settings' / 'defaults.md'

    conflicts, missing = [], []
    if cls is not None:
        text, conflicts, missing = render_defaults(data, cls)
        defaults_path.parent.mkdir(parents=True, exist_ok=True)
        defaults_path.write_text(text, encoding='utf-8', newline='\n')
        data['defaultConflicts'] = [{'key': n, 'values': sorted(v)} for n, v in conflicts]
        data['defaultsMissing'] = missing
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + '\n', encoding='utf-8', newline='\n')
    md_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.write_text(render_inventory(data), encoding='utf-8', newline='\n')

    s = data['summary']
    print('key_* resources: %d (strings_donottranslate.xml %d); on pages: %d %s; code only: %d' % (
        s['keyResources'], s['keyResourcesInStringsDonottranslate'], s['keysOnSettingsPages'],
        list(s['keysPerPage'].values()), s['keysOnlyInCode']))
    print('literal key groups: %d; named stores: %d in %d files; unresolved calls: %d' % (
        s['literalKeyGroups'], s['namedStores'], s['namedStoreCallFiles'], len(s['unresolvedStoreCalls'])))
    print('default prefs bypass files: %d; Pref users: %d; json file stores: %d' % (
        s['defaultPrefsBypassFiles'], s['prefUserFiles'], s['jsonFileStores']))
    if cls is not None:
        print('classification problems: %d; default conflicts: %d; settings without default source: %d' % (
            len(problems), len(conflicts), len(missing)))
        for p in problems[:40]:
            print('  ' + p)
    def shown(path):
        return path.relative_to(host).as_posix() if path.resolve().is_relative_to(host) else str(path)
    print('wrote %s, %s%s' % (shown(json_path), shown(md_path), ', ' + shown(defaults_path) if cls is not None else ''))
    if args.check and (cls is None or problems):
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
