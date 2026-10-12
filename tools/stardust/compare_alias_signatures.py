# -*- coding: utf-8 -*-
"""Compare the public API of com.stardust compatibility classes with their AutoJs6 counterparts (roadmap P7.3).

The Rhino alias table (`org.autojs.autojs.rhino.StardustClassAliases`) makes a script that names
`com.stardust.a.b.C` receive the host class `org.autojs.autojs.x.y.C` instead of the frozen copy. An alias is only
safe when every public constructor, method and field a script could have used on the copy also exists on the host
class with the same parameter types (after mapping `com.stardust` types to their host equivalents). This script
reads both sides with `javap -public` from the compiled class directories and reports, per candidate, the members
that are missing, differ in parameters, differ in return type or differ in static-ness.

Usage (from the repository root, after `gradlew :app:compileAppDebugKotlin :compat:stardust:compileDebugKotlin`):
  py tools/stardust/compare_alias_signatures.py            # the alias table of StardustClassAliases
  py tools/stardust/compare_alias_signatures.py --all      # every com.stardust class with a host class at the
                                                           # analogous path (candidate discovery)
  py tools/stardust/compare_alias_signatures.py --details  # list every member instead of the counts only

The unit test `StardustClassAliasesTest` performs the same comparison with reflection for the aliases that are
enabled; this script is the wider, read-only survey used to decide which classes to enable.

zh-CN: 比较 com.stardust 兼容类与 AutoJs6 对应类的 public API (roadmap P7.3). 别名表让脚本写 `com.stardust.a.b.C` 时拿到
宿主类, 只有兼容副本的每个 public 构造器, 方法与字段在宿主类上都有同参数类型 (com.stardust 类型按宿主对应类映射) 的对应项时
别名才安全. 本脚本用 javap 读取两侧的编译产物并逐类报告缺失, 参数不同, 返回类型不同与 static 不同的成员. 单元测试
StardustClassAliasesTest 对已启用的别名用反射做同样的核对; 本脚本是决定启用范围的只读普查.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOST_PACKAGE = "org.autojs.autojs"
CLASS_ROOTS = [
    ROOT / "app/build/intermediates/javac/appDebug/compileAppDebugJavaWithJavac/classes",
    ROOT / "app/build/intermediates/built_in_kotlinc/appDebug/compileAppDebugKotlin/classes",
    ROOT / "compat/stardust/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
    ROOT / "compat/stardust/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
]

# The surveyed candidates (first batch of roadmap P7.3). The entries that pass are enabled in
# org.autojs.autojs.rhino.StardustClassAliases.TABLE, which StardustClassAliasesTest checks against this dict; the
# others stay here so that a later host change that closes the gap shows up as "alias" in the report.
# zh-CN: 普查的候选 (roadmap P7.3 第一批). 通过的条目在 StardustClassAliases.TABLE 中启用 (单测校验该表是本字典的子集);
# 其余保留在此, 宿主日后补齐缺口时报告会直接显示为 "alias".
ALIASES = {
    "com.stardust.autojs.runtime.api.AbstractConsole": "org.autojs.autojs.runtime.api.AbstractConsole",
    "com.stardust.autojs.runtime.api.AbstractShell": "org.autojs.autojs.runtime.api.AbstractShell",
    "com.stardust.autojs.runtime.api.Device": "org.autojs.autojs.runtime.api.Device",
    "com.stardust.autojs.runtime.api.Dialogs": "org.autojs.autojs.runtime.api.Dialogs",
    "com.stardust.autojs.runtime.api.Engines": "org.autojs.autojs.runtime.api.Engines",
    "com.stardust.autojs.runtime.api.Events": "org.autojs.autojs.runtime.api.Events",
    "com.stardust.autojs.runtime.api.Files": "org.autojs.autojs.runtime.api.Files",
    "com.stardust.autojs.runtime.api.Images": "org.autojs.autojs.runtime.api.Images",
    "com.stardust.autojs.runtime.api.Threads": "org.autojs.autojs.runtime.api.Threads",
    "com.stardust.autojs.runtime.api.Timers": "org.autojs.autojs.runtime.api.Timers",
    "com.stardust.autojs.core.util.Shell": "org.autojs.autojs.runtime.api.Shell",
    "com.stardust.autojs.core.image.Colors": "org.autojs.autojs.core.image.Colors",
    "com.stardust.pio.PFiles": "org.autojs.autojs.pio.PFiles",
    "com.stardust.pio.PFile": "org.autojs.autojs.pio.PFile",
    "com.stardust.pio.PWritableTextFile": "org.autojs.autojs.pio.PWritableTextFile",
    "com.stardust.pio.PReadableTextFile": "org.autojs.autojs.pio.PReadableTextFile",
    # Second batch (2026-10-12).
    "com.stardust.autojs.core.eventloop.EventEmitter": "org.autojs.autojs.core.eventloop.EventEmitter",
    "com.stardust.autojs.core.eventloop.SimpleEvent": "org.autojs.autojs.core.eventloop.SimpleEvent",
    "com.stardust.autojs.core.image.ColorFinder": "org.autojs.autojs.core.image.ColorFinder",
    "com.stardust.autojs.core.inputevent.InputEventCodes": "org.autojs.autojs.core.inputevent.InputEventCodes",
    "com.stardust.autojs.core.storage.LocalStorage": "org.autojs.autojs.core.storage.LocalStorage",
    "com.stardust.autojs.core.util.ScriptPromiseAdapter": "org.autojs.autojs.runtime.api.ScriptPromiseAdapter",
    "com.stardust.concurrent.ConcurrentArrayList": "org.autojs.autojs.concurrent.ConcurrentArrayList",
    "com.stardust.concurrent.Value": "org.autojs.autojs.concurrent.Value",
    "com.stardust.concurrent.VolatileDispose": "org.autojs.autojs.concurrent.VolatileDispose",
    "com.stardust.io.ByteBufferBackedInputStream": "org.autojs.autojs.io.ByteBufferBackedInputStream",
    "com.stardust.io.ByteBufferBackedOutputStream": "org.autojs.autojs.io.ByteBufferBackedOutputStream",
    "com.stardust.io.ConcatReader": "org.autojs.autojs.io.ConcatReader",
    "com.stardust.io.EFile": "org.autojs.autojs.io.EFile",
    "com.stardust.lang.ThreadCompat": "org.autojs.autojs.lang.ThreadCompat",
    "com.stardust.notification.Notification": "org.autojs.autojs.core.notification.Notification",
    "com.stardust.pio.PFileInterface": "org.autojs.autojs.pio.PFileInterface",
    "com.stardust.pio.PRandomAccessBinaryFile": "org.autojs.autojs.pio.PRandomAccessBinaryFile",
    "com.stardust.pio.PReadableBinaryFile": "org.autojs.autojs.pio.PReadableBinaryFile",
    "com.stardust.pio.UncheckedIOException": "org.autojs.autojs.pio.UncheckedIOException",
    "com.stardust.util.AdvancedEncryptionStandard": "org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard",
    "com.stardust.util.ArrayUtils": "org.autojs.autojs.util.ArrayUtils",
    "com.stardust.util.AssetsCache": "org.autojs.autojs.util.AssetsCache",
    "com.stardust.util.BiMap": "org.autojs.autojs.core.ui.BiMap",
    "com.stardust.util.BiMaps": "org.autojs.autojs.core.ui.BiMaps",
    "com.stardust.util.Callback": "org.autojs.autojs.tool.Callback",
    "com.stardust.util.Consumer": "org.autojs.autojs.tool.Consumer",
    "com.stardust.util.Func1": "org.autojs.autojs.tool.Func1",
    "com.stardust.util.IntentExtras": "org.autojs.autojs.tool.IntentExtras",
    "com.stardust.util.LimitedHashMap": "org.autojs.autojs.tool.LimitedHashMap",
    "com.stardust.util.MD5": "org.autojs.autojs.inrt.launch.MD5",
    "com.stardust.util.MapBuilder": "org.autojs.autojs.tool.MapBuilder",
    "com.stardust.util.Objects": "org.autojs.autojs.util.Objects",
    "com.stardust.util.SimpleCache": "org.autojs.autojs.tool.SimpleCache",
    "com.stardust.util.SparseArrayEntries": "org.autojs.autojs.tool.SparseArrayEntries",
    "com.stardust.util.StateObserver": "org.autojs.autojs.tool.StateObserver",
    "com.stardust.util.Supplier": "org.autojs.autojs.tool.Supplier",
}

OBJECT_METHODS = {"equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "finalize", "clone"}

# What a Rhino script cannot tell apart: every Java number is a JS number, `java.lang.Object` accepts anything and a
# JS function or object satisfies both `Object` and the Rhino value types.
# zh-CN: Rhino 脚本无法区分的类型: Java 数值都是 JS number, Object 接受任何值, JS 函数或对象同时满足 Object 与 Rhino 值类型.
NUMERIC_TYPES = {
    "byte", "short", "int", "long", "float", "double", "char",
    "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
    "java.lang.Character", "java.lang.Number",
}
BOOLEAN_TYPES = {"boolean", "java.lang.Boolean"}
TEXT_TYPES = {"java.lang.String", "java.lang.CharSequence"}
JS_VALUE_TYPES = {
    "org.mozilla.javascript.Callable", "org.mozilla.javascript.Function", "org.mozilla.javascript.BaseFunction",
    "org.mozilla.javascript.NativeFunction", "org.mozilla.javascript.Scriptable", "org.mozilla.javascript.ScriptableObject",
    "org.mozilla.javascript.NativeObject", "org.mozilla.javascript.NativeArray",
}
# Types a script never holds: the compat engine stack is not instantiated by the host, so a constructor taking one
# of these was never callable from a script and its loss is not a regression.
# zh-CN: 脚本永远拿不到的类型: 宿主不实例化兼容包的引擎栈, 接收这些类型的构造器本来就无法从脚本调用, 丢失不算回退.
ENGINE_INTERNAL_PREFIX = "com.stardust.autojs."


def js_compatible_param(compat_type: str, host_type: str) -> bool:
    if compat_type == host_type or host_type == "java.lang.Object":
        return True
    if compat_type in NUMERIC_TYPES and host_type in NUMERIC_TYPES:
        return True
    if compat_type in BOOLEAN_TYPES and host_type in BOOLEAN_TYPES:
        return True
    if compat_type in TEXT_TYPES and host_type in TEXT_TYPES:
        return True
    if compat_type == "java.lang.Object" and host_type in JS_VALUE_TYPES:
        return True
    return False


def js_compatible_params(compat_params, host_params) -> bool:
    return len(compat_params) == len(host_params) and all(js_compatible_param(c, h) for c, h in zip(compat_params, host_params))

CLASS_LINE = re.compile(
    r"^(?:[\w ]*?\b)?(class|interface|enum)\s+([\w.$]+)(?:<[^{]*?>)?"
    r"(?:\s+extends\s+([\w.$<>, ]+?))?(?:\s+implements\s+([\w.$<>, ]+?))?\s*\{?\s*$"
)
MEMBER_LINE = re.compile(
    r"^\s{2}(?P<mods>(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp|transient|volatile)\s+)*)"
    r"(?:<[^>]+>\s+)?(?:(?P<type>[\w.$\[\]<>, ?]+?)\s+)?(?P<name>[\w.$]+)(?:\((?P<params>.*?)\))?(?:\s+throws\s+[\w.$, ]+)?;$"
)


def strip_generics(text: str) -> str:
    while True:
        stripped = re.sub(r"<[^<>]*>", "", text)
        if stripped == text:
            return text
        text = stripped


def normalize_type(text: str) -> str:
    text = strip_generics(text).strip()
    if text.endswith("..."):
        text = text[:-3] + "[]"
    return text


class Member:
    def __init__(self, kind: str, name: str, params, rtype: str, static: bool):
        self.kind = kind  # ctor | method | field
        self.name = name
        self.params = params  # list of types (fields: [])
        self.rtype = rtype  # fields: the field type; ctor: ""
        self.static = static

    def signature(self, mapper=None) -> str:
        convert = mapper if mapper else (lambda t: t)
        params = [convert(p) for p in self.params]
        prefix = "static " if self.static else ""
        if self.kind == "field":
            return f"{prefix}{self.name}: {convert(self.rtype)}"
        ret = "" if self.kind == "ctor" else f": {convert(self.rtype)}"
        return f"{prefix}{self.name}({', '.join(params)}){ret}"


class ClassInfo:
    def __init__(self, name: str):
        self.name = name
        self.kind = "class"
        self.supers = []  # extends + implements (generic-stripped)
        self.members = []


class ClassIndex:
    """All class files of the compiled roots, read lazily with javap."""

    def __init__(self, roots, javap: str):
        self.roots = [r for r in roots if r.is_dir()]
        self.javap = javap
        self.files = {}
        for root in self.roots:
            for path in root.rglob("*.class"):
                fqcn = path.relative_to(root).with_suffix("").as_posix().replace("/", ".")
                self.files.setdefault(fqcn, path)
        self.by_simple = defaultdict(list)
        for fqcn in self.files:
            if "$" not in fqcn:
                self.by_simple[fqcn.rsplit(".", 1)[-1]].append(fqcn)
        self.cache = {}

    def exists(self, fqcn: str) -> bool:
        return fqcn in self.files

    def nested_of(self, fqcn: str):
        prefix = fqcn + "$"
        return sorted(c for c in self.files if c.startswith(prefix) and not re.search(r"\$\d", c))

    def load(self, names):
        missing = [n for n in names if n not in self.cache and n in self.files]
        for start in range(0, len(missing), 50):
            chunk = missing[start:start + 50]
            result = subprocess.run(
                [self.javap, "-public", "-cp", os.pathsep.join(str(r) for r in self.roots), *chunk],
                capture_output=True, text=True, encoding="utf-8", errors="replace",
            )
            self._parse(result.stdout)
            for name in chunk:
                self.cache.setdefault(name, None)
        return {n: self.cache.get(n) for n in names}

    def get(self, fqcn: str):
        if fqcn not in self.cache:
            self.load([fqcn])
        return self.cache.get(fqcn)

    def _parse(self, text: str):
        current = None
        for raw in text.splitlines():
            line = raw.rstrip()
            if not line or line.startswith("Compiled from") or line == "}":
                continue
            if not line.startswith(" "):
                match = CLASS_LINE.match(line)
                if not match:
                    current = None
                    continue
                current = ClassInfo(match.group(2))
                current.kind = match.group(1)
                if current.kind == "class" and "abstract" in line[:match.start(1)].split():
                    current.kind = "abstract"
                supers = []
                for group in (match.group(3), match.group(4)):
                    if group:
                        supers += [normalize_type(s) for s in strip_generics(group).split(",") if s.strip()]
                current.supers = supers
                self.cache[current.name] = current
                continue
            if current is None:
                continue
            match = MEMBER_LINE.match(line)
            if not match:
                continue
            mods = (match.group("mods") or "").split()
            if "public" not in mods:
                continue
            name = match.group("name")
            static = "static" in mods
            if match.group("params") is None:
                current.members.append(Member("field", name, [], normalize_type(match.group("type") or ""), static))
                continue
            params_text = strip_generics(match.group("params"))
            params = [normalize_type(p) for p in params_text.split(",") if p.strip()]
            if match.group("type") is None and ("." in name or "$" in name):
                current.members.append(Member("ctor", "<init>", params, "", False))
                continue
            if "$" in name:
                continue  # synthetic (Kotlin default arguments, access bridges)
            current.members.append(Member("method", name, params, normalize_type(match.group("type") or "void"), static))

    def all_members(self, fqcn: str, include_object_methods: bool):
        """Declared members plus the inherited ones that are resolvable inside the roots."""
        seen = set()
        out = []
        queue = [fqcn]
        first = True
        while queue:
            name = queue.pop(0)
            if name in seen:
                continue
            seen.add(name)
            info = self.get(name)
            if info is None:
                continue
            for member in info.members:
                if member.kind == "ctor" and not first:
                    continue
                if member.kind == "method" and not include_object_methods and member.name in OBJECT_METHODS:
                    continue
                out.append(member)
            first = False
            queue += info.supers
        return out


class TypeMapper:
    def __init__(self, index: ClassIndex, aliases):
        self.index = index
        self.aliases = aliases
        self.fallbacks = {}

    def analogous(self, outer: str):
        if outer in self.aliases:
            return self.aliases[outer]
        if outer.startswith("com.stardust.autojs."):
            suffix = outer[len("com.stardust.autojs."):]
        elif outer.startswith("com.stardust."):
            suffix = outer[len("com.stardust."):]
        else:
            return None
        exact = f"{HOST_PACKAGE}.{suffix}"
        if self.index.exists(exact):
            return exact
        simple = outer.rsplit(".", 1)[-1]
        hosts = [c for c in self.index.by_simple.get(simple, []) if c.startswith(HOST_PACKAGE + ".")]
        if len(hosts) == 1:
            self.fallbacks[outer] = hosts[0]
            return hosts[0]
        return None

    def __call__(self, text: str) -> str:
        base = text.rstrip("[]")
        dims = text[len(base):]
        if base.startswith("com.stardust."):
            outer, sep, inner = base.partition("$")
            target = self.analogous(outer)
            if target:
                base = target + sep + inner
        return base + dims


def is_engine_internal(member: Member) -> bool:
    return any(p.startswith(ENGINE_INTERNAL_PREFIX) for p in member.params)


def compare(index: ClassIndex, mapper: TypeMapper, compat: str, host: str, details: bool):
    """Returns (counts, lines). Keys of counts: ok, relaxed, info, blocking.

    An alias changes only what the *name* resolves to; the objects a script already holds (`files`, `device`, ...)
    are host instances either way. Hence a difference blocks the alias only when a script could have reached the
    member through the name: static members, constructors a script could call (no engine-internal parameter) and,
    for classes a script can construct or extend (abstract types), the instance members. Everything else is
    informational. zh-CN: 别名只改变名字解析到的类, 脚本已持有的对象不受影响; 因此只有脚本能通过类名触及的成员
    (静态成员, 脚本可调用的构造器, 以及可构造或可继承类的实例成员) 的差异才阻止别名, 其余仅作记录.
    """
    lines = []
    info = index.get(compat)
    compat_members = index.all_members(compat, include_object_methods=False)
    host_members = index.all_members(host, include_object_methods=True)
    host_companion = index.get(host + "$Companion")
    companion_members = host_companion.members if host_companion else []
    ctors = [m for m in compat_members if m.kind == "ctor"]
    script_ctors = [c for c in ctors if not is_engine_internal(c)]
    abstract = info is not None and info.kind in ("interface", "abstract")
    static_only = all(m.static for m in compat_members if m.kind == "method") and not any(
        m.kind == "field" and not m.static for m in compat_members
    )
    constructible = bool(script_ctors) or abstract
    counts = defaultdict(int)

    def record(level: str, text: str):
        counts[level] += 1
        if level != "ok" or details:
            lines.append(f"- {level}: {text}")

    for member in compat_members:
        mapped = [mapper(p) for p in member.params]
        shown = member.signature(mapper)
        if member.kind == "field":
            same = [h for h in host_members if h.kind == "field" and h.name == member.name]
            level_if_bad = "blocking" if (member.static or constructible) else "info"
            if not same:
                record(level_if_bad, f"missing field `{shown}`")
            elif member.static and not same[0].static:
                record("blocking", f"field is not static on the host `{shown}` vs `{same[0].signature()}`")
            elif mapper(member.rtype) != same[0].rtype:
                record("info", f"field type differs `{shown}` vs host `{same[0].signature()}`")
            else:
                record("ok", f"`{shown}`")
            continue
        if member.kind == "ctor":
            exact = [h for h in host_members if h.kind == "ctor" and h.params == mapped]
            relaxed = [h for h in host_members if h.kind == "ctor" and js_compatible_params(mapped, h.params)]
            if exact:
                record("ok", f"`{shown}`")
            elif relaxed:
                record("relaxed", f"`{shown}` matched by host `{relaxed[0].signature()}`")
            elif is_engine_internal(member):
                record("info", f"engine-internal constructor has no host counterpart `{shown}` (never callable from a script)")
            elif static_only and not member.params:
                record("info", f"no-arg constructor of a static utility class is missing on the host `{shown}`")
            else:
                overloads = "; ".join(h.signature() for h in host_members if h.kind == "ctor") or "none"
                record("blocking", f"missing constructor `{shown}` (host constructors: {overloads})")
            continue
        same_name = [h for h in host_members if h.kind == "method" and h.name == member.name]
        exact = [h for h in same_name if h.params == mapped]
        relaxed = [h for h in same_name if js_compatible_params(mapped, h.params)]
        match = (exact or relaxed or [None])[0]
        reachable = member.static or constructible
        if match is None:
            companion = [h for h in companion_members if h.kind == "method" and h.name == member.name and js_compatible_params(mapped, h.params)]
            if companion:
                record("blocking" if member.static else "info", f"companion only (not static on the host class) `{shown}`")
            elif same_name:
                overloads = "; ".join(h.signature() for h in same_name)
                record("blocking" if reachable else "info", f"params differ `{shown}` vs host `{overloads}`")
            else:
                record("blocking" if reachable else "info", f"missing `{shown}`")
            continue
        if member.static and not match.static:
            record("blocking", f"static on the copy but not on the host `{shown}` vs `{match.signature()}`")
            continue
        notes = []
        if not exact:
            notes.append(f"params relaxed to host `{match.signature()}`")
        if mapper(member.rtype) != match.rtype:
            notes.append(f"return differs: host `{match.rtype}`")
        if not member.static and match.static:
            notes.append("static on the host (callable through instances in Rhino)")
        if notes:
            record("relaxed" if not exact else "info", f"`{shown}` ({'; '.join(notes)})")
        else:
            record("ok", f"`{shown}`")
    return counts, lines


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--all", action="store_true", help="survey every com.stardust class with a host class at the analogous path")
    parser.add_argument("--details", action="store_true", help="list matching members as well")
    parser.add_argument("--javap", default=shutil.which("javap") or os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "javap"))
    args = parser.parse_args()

    index = ClassIndex(CLASS_ROOTS, args.javap)
    if not index.files:
        print("no compiled classes found; build :app and :compat:stardust first", file=sys.stderr)
        return 2
    mapper = TypeMapper(index, ALIASES)

    pairs = dict(ALIASES)
    if args.all:
        for fqcn in sorted(index.files):
            if not fqcn.startswith("com.stardust.") or "$" in fqcn or fqcn in pairs:
                continue
            target = mapper.analogous(fqcn)
            if target and fqcn.rsplit(".", 1)[-1] == target.rsplit(".", 1)[-1]:
                pairs[fqcn] = target
    names = []
    for compat, host in pairs.items():
        names += [compat, host, host + "$Companion"] + index.nested_of(compat) + index.nested_of(host)
    index.load(sorted(set(n for n in names if index.exists(n))))

    print("| com.stardust class | host class | members | ok | relaxed | info | blocking | verdict |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- |")
    reports = []
    for compat, host in pairs.items():
        if not index.exists(compat) or not index.exists(host):
            print(f"| `{compat}` | `{host}` | - | - | - | - | - | class file missing |")
            continue
        total = defaultdict(int)
        report = []
        targets = [(compat, host)]
        for nested in index.nested_of(compat):
            suffix = nested[len(compat):]
            targets.append((nested, host + suffix))
        for c, h in targets:
            if not index.exists(h):
                total["blocking"] += 1
                report.append(f"- blocking: nested class `{c}` has no host counterpart `{h}`")
                continue
            counts, lines = compare(index, mapper, c, h, args.details)
            for key, value in counts.items():
                total[key] += value
            if lines and c != compat:
                report.append(f"- nested `{c.rsplit('$', 1)[-1]}`:")
                report += ["  " + line for line in lines]
            else:
                report += lines
        members = sum(total.values())
        verdict = "alias" if total["blocking"] == 0 else "keep copy"
        print(f"| `{compat}` | `{host}` | {members} | {total['ok']} | {total['relaxed']} | {total['info']} | {total['blocking']} | {verdict} |")
        reports.append((compat, host, report))
    for compat, host, report in reports:
        if report:
            print()
            print(f"### `{compat}` -> `{host}`")
            print()
            print("\n".join(report))
    if mapper.fallbacks:
        print()
        print("Type mappings resolved by simple name (no class at the analogous path):")
        for src, dst in sorted(mapper.fallbacks.items()):
            print(f"- `{src}` -> `{dst}`")
    return 0


if __name__ == "__main__":
    sys.exit(main())
