"""Static source audit for docs/memory/ROADMAP.md P0.6 (appendix B draft, T8 whitelist source).

Scans app/src/main/java (Java and Kotlin) and reports:

(a) process-lifetime fields (Java `static`, Kotlin `object` / `companion object` / top-level
    properties) whose declared type mentions ScriptRuntime, ScriptEngine, a Rhino Scriptable or
    scope, the Rhino Context, Activity, View, Fragment or Dialog (Android Context is listed
    separately for information), marked as weak when wrapped in WeakReference / SoftReference;
(b) files that call registerReceiver / bindService / .subscribe( / create a WebView /
    addIdleHandler / Looper.prepare / ThreadLocal, with the count of the matching release calls
    (unregisterReceiver, unbindService, dispose, destroy, removeIdleHandler, quit, remove) found in
    the same file.

It is a text scan, not a compiler: comments and string literals are blanked first, types are
taken from declarations, and the ownership column (process / script / page) is guessed from the
package path. Every row needs a human look before it becomes an appendix B decision.

--guard (ROADMAP T8 / P4.3) writes the generated columns of the source guard whitelist instead of the
report: the fields of (a) other than weak references (Android Context included), the registries of (c)
and the files of (b) that call registerReceiver / bindService / addIdleHandler / Looper.prepare or
create a WebView. Reasons already written for a key are kept, new keys get the reason TODO.
app/src/test/java/org/autojs/autojs/memory/StaticReferenceScanner.kt implements the same rules and
StaticReferenceGuardTest checks that both give the same rows.

Usage: py -3.12 -I tools/memory/static_audit.py [--out docs/memory/evidence] [--json]
       py -3.12 -I tools/memory/static_audit.py --guard [--check]
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app" / "src" / "main" / "java"
WHITELIST = ROOT / "app" / "src" / "test" / "resources" / "memory" / "static-reference-whitelist.txt"

RHINO_SCOPE_TYPES = {"Scriptable", "ScriptableObject", "NativeObject", "NativeArray", "NativeFunction", "BaseFunction",
                     "TopLevelScope", "VarScope", "ImporterTopLevel", "NativeJavaObject", "IdScriptableObject"}
ENGINE_TYPES = {"ScriptEngine", "JavaScriptEngine", "RhinoJavaScriptEngine", "LoopBasedJavaScriptEngine"}
WEAK_WRAPPERS = {"WeakReference", "SoftReference"}
COLLECTIONS = {"List", "MutableList", "ArrayList", "Set", "MutableSet", "HashSet", "LinkedHashSet", "Map", "MutableMap",
               "HashMap", "LinkedHashMap", "ConcurrentHashMap", "CopyOnWriteArrayList", "CopyOnWriteArraySet",
               "SparseArray", "Array", "Collection", "Queue", "Deque", "ArrayDeque", "WeakHashMap", "LruCache"}

PAIRS = [
    ("registerReceiver", r"\bregisterReceiver\s*\(", r"\bunregisterReceiver\s*\("),
    ("bindService", r"\bbindService\s*\(", r"\bunbindService\s*\("),
    (".subscribe(", r"\.subscribe\s*\(", r"\.dispose\s*\(|\bCompositeDisposable\b|\.autoDispose\b"),
    ("WebView(", r"(?<![\w.])(?:new\s+)?\w*WebView\s*\(\s*[\w@]", r"\.destroy\s*\(\s*\)"),
    ("addIdleHandler", r"\baddIdleHandler\s*\(", r"\bremoveIdleHandler\s*\("),
    ("Looper.prepare", r"\bLooper\.prepare\s*\(", r"\.quit(?:Safely)?\s*\(|\bquitForThread\s*\("),
    ("ThreadLocal", r"\bThreadLocal\s*(?:<[^>;]*>)?\s*(?:\(|\.withInitial)|:\s*ThreadLocal\s*<", r"\.remove\s*\(\s*\)"),
]
SCRIPT_PATHS = ("runtime/", "execution/", "core/looper/", "core/image/", "core/ui/", "core/web/", "core/console/",
                "core/record/", "core/automator/", "core/accessibility/", "core/eventloop/", "core/floaty/", "rhino/",
                "engine/Runtime", "core/opencv/", "core/pref/Pref")
PAGE_PATHS = ("ui/", "theme/")


def strip_code(text, kotlin):
    """Blank comments and string / char literals, keeping line breaks so line numbers stay valid."""
    out = []
    i, n = 0, len(text)
    depth = 0
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if two == "//":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if two == "/*":
            depth = 1
            out.append("  ")
            i += 2
            while i < n and depth:
                if kotlin and text[i:i + 2] == "/*":
                    depth += 1
                    out.append("  ")
                    i += 2
                elif text[i:i + 2] == "*/":
                    depth -= 1
                    out.append("  ")
                    i += 2
                else:
                    out.append("\n" if text[i] == "\n" else " ")
                    i += 1
            continue
        if kotlin and text[i:i + 3] == '"""':
            out.append('"""')
            i += 3
            while i < n and text[i:i + 3] != '"""':
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append('"""')
            i += 3
            continue
        if c in "\"'":
            quote = c
            out.append(c)
            i += 1
            while i < n and text[i] != quote and text[i] != "\n":
                if text[i] == "\\":
                    out.append("  ")
                    i += 2
                    continue
                out.append(" ")
                i += 1
            if i < n and text[i] == quote:
                out.append(quote)
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def imports_of(text):
    return dict((m[2], m[1]) for m in re.finditer(r"^\s*import\s+([\w.]+\.(\w+))\s*;?\s*$", text, re.MULTILINE))


def categories(type_text, imports):
    tokens = re.findall(r"[A-Za-z_][\w.]*", type_text)
    simple = [token.split(".")[-1] for token in tokens]
    found = set()
    for token, name in zip(tokens, simple):
        qualified = token if "." in token else imports.get(name, "")
        if name == "ScriptRuntime":
            found.add("ScriptRuntime")
        elif name in ENGINE_TYPES or (name.endswith("ScriptEngine") and name != "ScriptEngineService"):
            found.add("ScriptEngine")
        elif name in RHINO_SCOPE_TYPES:
            found.add("Scriptable")
        elif name == "AutoJsContext" or (name == "Context" and qualified.startswith("org.mozilla.javascript")):
            found.add("RhinoContext")
        elif name == "Context":
            found.add("AndroidContext")
        elif name == "Activity" or name.endswith("Activity"):
            found.add("Activity")
        elif name == "Fragment" or name.endswith("Fragment"):
            found.add("Fragment")
        elif name == "Dialog" or name.endswith("Dialog"):
            found.add("Dialog")
        elif (name == "View" or name.endswith("View") or name == "ViewGroup") and not name.endswith("ViewModel"):
            found.add("View")
    if not found:
        return None, None
    if any(name in WEAK_WRAPPERS for name in simple):
        holding = "weak"
    elif simple and simple[0] == "WeakHashMap":
        holding = "weak-key map"
    elif any(name in COLLECTIONS for name in simple):
        holding = "strong collection"
    else:
        holding = "strong"
    return sorted(found), holding


def container_kind(type_text):
    """`registry` for a map / set / list / array / cache field, otherwise `value`."""
    outer = re.match(r"\s*([A-Za-z_][\w.]*)", type_text)
    name = outer[1].split(".")[-1] if outer else ""
    if name in COLLECTIONS or re.search(r"(?:Map|Set|List|Cache|Registry|Queue)$", name) or type_text.rstrip().endswith("[]"):
        return "weak-key registry" if name == "WeakHashMap" else "registry"
    return "value"


# Kotlin collection factories with explicit type arguments give the type of a property declared without one (P4.3).
KOTLIN_FACTORIES = {"mutableListOf": "MutableList", "mutableSetOf": "MutableSet", "mutableMapOf": "MutableMap",
                    "arrayListOf": "ArrayList", "hashSetOf": "HashSet", "hashMapOf": "HashMap",
                    "linkedSetOf": "LinkedHashSet", "linkedMapOf": "LinkedHashMap"}
REGISTRY_HINT = re.compile(r"Thread|Looper|Listener|Callback|Window|Wrapper|Engine|Runtime|ClassLoader|Receiver|"
                           r"Connection|Observer|Handler|Execution|Session|Scope|Delegate|Bitmap|Image|\bMat\b")


def java_statics(path, code, imports):
    rows = []
    pattern = re.compile(r"\bstatic\b(?:\s+(?:final|volatile|transient|public|private|protected))*\s+"
                         r"([A-Za-z_][\w.]*(?:\s*<[^;=(){}]*>)?(?:\s*\[\s*\])*)\s+(\w+)\s*(?:=([^;]{0,300})|;)")
    for match in pattern.finditer(code):
        type_text, name = match[1], match[2]
        if type_text in ("class", "interface", "enum", "final", "abstract", "void"):
            continue
        found, holding = categories(type_text, imports)
        rows.append({"file": path, "line": code.count("\n", 0, match.start()) + 1, "holder": "java static",
                     "name": name, "type": " ".join(type_text.split()), "categories": found or [],
                     "holding": weak_initializer(match[3], holding or container_kind(type_text))})
    return rows


def weak_initializer(initializer, holding):
    """A declared Map / reference type created as a WeakHashMap or WeakReference holds weakly."""
    if initializer and re.search(r"\bWeakHashMap\b", initializer) and holding in ("strong collection", "registry"):
        return "weak-key map" if holding == "strong collection" else "weak-key registry"
    if initializer and re.search(r"\b(?:WeakReference|SoftReference)\b", initializer) and holding == "strong":
        return "weak"
    return holding


def kotlin_statics(path, code, imports):
    """Properties declared at top level or directly in an `object` / `companion object` body."""
    rows = []
    stack = []
    statement_start = 0
    property_pattern = re.compile(r"^(?:(?:private|internal|public|protected|override|lateinit|const|@\w+(?:\([^)]*\))?|"
                                  r"@field:\w+|@Volatile|@JvmField|@JvmStatic)\s+)*(?:val|var)\s+(\w+)\s*"
                                  r"(?::\s*([^=\n{]+?))?\s*(?:=\s*([^\n]*)|by\s+([^\n]*))?$")
    lines = code.split("\n")
    # One pass over the characters records the scope kind in effect at each line start. The kind of a
    # block comes from the text between the previous brace or semicolon and its opening brace.
    scopes_by_line = ["top"]
    for i, c in enumerate(code):
        if c == "{":
            header = code[max(statement_start, i - 300):i]
            if re.search(r"\bcompanion\s+object\b", header) or re.search(r"(?:^|[\s;])object\s+\w+", header):
                stack.append("object")
            elif re.search(r"\b(?:class|interface)\s+\w+", header):
                stack.append("class")
            elif re.search(r"\bfun\b", header):
                stack.append("fun")
            else:
                stack.append("block")
            statement_start = i + 1
        elif c == "}":
            while stack and stack[-1] == "paren":
                stack.pop()
            if stack:
                stack.pop()
            statement_start = i + 1
        elif c == "(":
            stack.append("paren")
        elif c == ")":
            if stack and stack[-1] == "paren":
                stack.pop()
        elif c == ";":
            statement_start = i + 1
        elif c == "\n":
            scopes_by_line.append(stack[-1] if stack else "top")
    for number, line in enumerate(lines):
        scope = scopes_by_line[number]
        if scope not in ("top", "object"):
            continue
        match = property_pattern.match(line.strip())
        if not match:
            continue
        name, declared, initializer, delegate = match[1], match[2], match[3], match[4]
        if initializer is not None and not initializer.strip():
            # `val x =` with the initializer on the next non-blank line (ROADMAP Q45: H29 was declared that way).
            initializer = next((rest.strip() for rest in lines[number + 1:] if rest.strip()), "")
        type_text = declared or ""
        if not type_text:
            source = (initializer or delegate or "").strip()
            inferred = re.match(r"(?:lazy\s*\{\s*)?(?:Collections\.\w+\s*\(\s*)?([A-Z][\w.]*\s*(?:<.*>)?)\s*[({]", source)
            factory = re.match(r"(?:lazy\s*\{\s*)?(" + "|".join(KOTLIN_FACTORIES) + r")\s*(<.*>)\s*\(", source)
            type_text = inferred[1] if inferred else KOTLIN_FACTORIES[factory[1]] + factory[2] if factory else ""
        if not type_text:
            continue
        found, holding = categories(type_text, imports)
        rows.append({"file": path, "line": number + 1, "holder": "kotlin " + ("top-level" if scope == "top" else "object"),
                     "name": name, "type": " ".join(type_text.split()), "categories": found or [],
                     "holding": weak_initializer(initializer or delegate, holding or container_kind(type_text)),
                     "inferred": not declared})
    return rows


def ownership(path):
    if any(part in path for part in PAGE_PATHS) and not path.startswith("runtime/"):
        return "page"
    if any(path.startswith(prefix) or ("/" + prefix) in path for prefix in SCRIPT_PATHS):
        return "script"
    return "process"


def audit():
    statics, pairs = [], {name: [] for name, _, _ in PAIRS}
    files = sorted(p for p in SOURCE.rglob("*") if p.suffix in (".kt", ".java"))
    for file in files:
        text = file.read_text(encoding="utf-8", errors="replace")
        kotlin = file.suffix == ".kt"
        code = strip_code(text, kotlin)
        relative = file.relative_to(SOURCE / "org" / "autojs" / "autojs").as_posix() if (SOURCE / "org" / "autojs" / "autojs") in file.parents else file.relative_to(SOURCE).as_posix()
        imports = imports_of(text)
        statics += kotlin_statics(relative, code, imports) if kotlin else java_statics(relative, code, imports)
        for name, register, release in PAIRS:
            lines = [line for line in code.split("\n") if not re.search(r"^\s*(?:import|package)\b", line)]
            body = "\n".join(lines)
            if name == "WebView(":
                body = "\n".join(line for line in lines if not re.search(r"\b(?:class|fun|constructor|interface)\b|\bis\s+\w*WebView|\bas\??\s+\w*WebView", line))
            registered = len(re.findall(register, body))
            if registered:
                released = len(re.findall(release, body))
                pairs[name].append({"file": relative, "registrations": registered, "releases": released,
                                    "paired": released > 0, "ownership": ownership(relative)})
    return files, statics, pairs


CONSTANT_TOKENS = {"List", "Set", "Map", "MutableList", "MutableSet", "Array", "String", "Int", "Integer", "Long", "Boolean",
                   "Double", "Float", "Char", "Byte", "Short", "Pair", "IntRange", "Charset", "Regex", "Class", "KClass",
                   "int", "long", "double", "float", "boolean", "byte", "char", "short", "get", "out", "in", "newKeySet",
                   "ConcurrentHashMap", "HashMap", "LinkedHashMap", "HashSet", "SparseArray", "Unit", "Any"}


def registry_rows(statics):
    """Registries whose elements are more than strings, numbers or classes (constant tables are left out)."""
    def interesting(row):
        tokens = {token.split(".")[-1] for token in re.findall(r"[A-Za-z_][\w.]*", row["type"])}
        return not tokens <= CONSTANT_TOKENS
    rows = [row for row in statics if row["holding"] in ("registry", "weak-key registry", "strong collection", "weak-key map")
            and row["categories"] != ["AndroidContext"] and interesting(row)
            and (ownership(row["file"]) == "script" or REGISTRY_HINT.search(row["type"]))]
    return sorted(rows, key=lambda r: (ownership(r["file"]) != "script", r["file"], r["line"]))


GUARD_CALLS = ("registerReceiver", "bindService", "WebView(", "addIdleHandler", "Looper.prepare")
GUARD_HEADER = """\
# Static reference guard whitelist (docs/memory/ROADMAP.md T7 / T8, P4.3)
#
# Read by app/src/test/java/org/autojs/autojs/memory/StaticReferenceGuardTest.kt (part of :app:testAppDebugUnitTest).
# Every process-level reference the guard finds in app/src/main/java needs a line here with a reason:
#   field   a Java static field or a Kotlin object / companion object / top-level property whose type mentions
#           ScriptRuntime, ScriptEngine, a Rhino Scriptable or Context, Activity, View, Fragment, Dialog or an Android
#           Context and is not a WeakReference / SoftReference; or a process-level map / set / list / array in a script
#           package or typed with threads, loopers, listeners, windows, images, engines or class loaders
#           (sections (a) and (c) of tools/memory/static_audit.py)
#   call    a file that calls registerReceiver / bindService / addIdleHandler / Looper.prepare or creates a WebView
#   manual  an appendix B.1 entry the text scan does not report (instance fields of process singletons, event buses);
#           written by hand, the test only checks that the file still declares the name
#
# Columns: kind | path under org/autojs/autojs/ | name | detail | reason | note. The first four columns of field and
# call lines are generated. After adding, removing or retyping such a reference run
#     py -3.12 -I tools/memory/static_audit.py --guard
# It keeps the reason and note of every existing key, drops keys that are gone and adds new keys with the reason TODO;
# then replace TODO with a reason and a note (what the reference holds, who releases it). A WeakReference or a cleanup
# per runtime / owner id (ROADMAP T7) is preferred over a new line.
#
# Reasons:
#   process  process-level by design: holds the Application, process singletons or data that outlive every script
#   table    constant, function or class table filled once; holds no script, Rhino or UI instance
#   weak     keys (WeakHashMap) or values (WeakReference) are weak; the note says what the strong side holds. For a
#            weak-key map or registry the note also says "values do not reference the key": a WeakHashMap holds its
#            values strongly, and a value that references its key keeps both forever (H29); such a map takes another
#            reason (owner when its entries are removed as the key's owner ends)
#   owner    entries are removed per runtime / owner id when the owner ends (gate scenario in the note)
#   paired   (call) the release call is in the same file
#   page     page / UI lifetime outside the script path, covered by the P4.5 LeakCanary audit
#   exempt   a text scan false positive: not a process-level field, or not a registration (the note says why)
"""
GUARD_SECTIONS = (("field", "# Process-level fields"), ("call", "# Registration calls"),
                  ("manual", "# Appendix B.1 entries the scan does not report (hand-written)"))
GUARD_TODO = "TODO | describe what it holds and who releases it"
# Required in the note of a weak-key map or registry whose reason is weak (ROADMAP Q45); StaticReferenceGuardTest checks it.
WEAK_KEY_NOTE = "values do not reference the key"


def guard_rows(statics, pairs):
    """Generated whitelist columns (kind, path, name, detail) in file order; StaticReferenceScanner gives the same rows."""
    selected = {}
    for row in [r for r in statics if r["categories"] and r["holding"] != "weak"] + registry_rows(statics):
        selected[(row["file"], row["line"], row["name"])] = row
    rows, seen = [], {}
    for path, line, name in sorted(selected):
        row = selected[(path, line, name)]
        seen[(path, name)] = seen.get((path, name), 0) + 1
        key = name if seen[(path, name)] == 1 else f"{name}#{seen[(path, name)]}"
        rows.append(("field", path, key, f"{row['holder']}; {row['holding']}; {', '.join(row['categories']) or '-'}"))
    for kind in GUARD_CALLS:
        for row in pairs[kind]:
            rows.append(("call", row["file"], kind, f"released: {'yes' if row['paired'] else 'no'}; {row['ownership']}"))
    return sorted(rows, key=" | ".join)


def write_guard(rows, check):
    """Rewrites the whitelist keeping the hand-written columns; with check, only reports whether it is current."""
    previous = WHITELIST.read_text(encoding="utf-8") if WHITELIST.exists() else ""
    reasons, manual = {}, []
    for line in previous.splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split(" | ", 5)
        if parts[0] == "manual":
            manual.append(line)
        elif len(parts) == 6:
            reasons[tuple(parts[:3])] = " | ".join(parts[4:])
    lines = [GUARD_HEADER]
    for kind, title in GUARD_SECTIONS:
        lines += [title, ""]
        if kind == "manual":
            lines += manual
        else:
            lines += [" | ".join(row) + " | " + reasons.get(row[:3], GUARD_TODO) for row in rows if row[0] == kind]
        lines.append("")
    content = "\n".join(lines)
    keys = {row[:3] for row in rows}
    added = [key for key in sorted(keys) if key not in reasons]
    removed = [key for key in sorted(reasons) if key not in keys]
    todo = sum(1 for row in rows if reasons.get(row[:3], GUARD_TODO).startswith("TODO "))
    details = {row[:3]: row[3] for row in rows}
    for line in manual:
        parts = line.split(" | ", 5)
        if len(parts) == 6:
            details[tuple(parts[:3])], reasons[tuple(parts[:3])] = parts[3], " | ".join(parts[4:])
    unexplained = [key for key, detail in sorted(details.items()) if "weak-key" in detail
                   and reasons.get(key, "").startswith("weak | ") and WEAK_KEY_NOTE not in reasons[key].lower()]
    for key in added:
        print("added (reason TODO): " + " | ".join(key))
    for key in removed:
        print("removed: " + " | ".join(key))
    for key in unexplained:
        print(f'weak-key note without "{WEAK_KEY_NOTE}": ' + " | ".join(key))
    print(f"{len(rows)} generated rows ({sum(1 for r in rows if r[0] == 'field')} fields, {sum(1 for r in rows if r[0] == 'call')} calls), "
          f"{len(manual)} manual; added {len(added)}, removed {len(removed)}, TODO {todo}")
    if check:
        current = content == previous.replace("\r\n", "\n")
        print(f"{WHITELIST.relative_to(ROOT).as_posix()}: {'current' if current else 'out of date, run with --guard'}")
        return 0 if current else 1
    with open(WHITELIST, "w", encoding="utf-8", newline="\n") as file:
        file.write(content)
    print(f"Written: {WHITELIST.relative_to(ROOT).as_posix()}")
    return 0


def git_head():
    try:
        return subprocess.run(["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"], capture_output=True, text=True, timeout=30).stdout.strip()
    except OSError:
        return "?"


def markdown(date, head, files, statics, pairs):
    strong = [row for row in statics if row["categories"] and row["holding"] != "weak" and row["categories"] != ["AndroidContext"]]
    weak = [row for row in statics if row["categories"] and row["holding"] == "weak"]
    registries = registry_rows(statics)
    android_context = [row for row in statics if row["categories"] == ["AndroidContext"] and row["holding"] != "weak"]
    lines = [
        f"# Static memory audit {date}",
        "",
        f"Generated by `tools/memory/static_audit.py` from `app/src/main/java` at `{head}` ({len(files)} Java / Kotlin files). "
        "Text scan with comments and strings blanked; types come from declarations (Kotlin types without an annotation are "
        "inferred from a constructor call and marked `inferred`). Ownership is guessed from the package path "
        "(`script`: runtime / execution / core script modules, `page`: ui, otherwise `process`). Paths are relative to "
        "`org/autojs/autojs/` unless they start elsewhere. Every row needs a human review before it becomes an appendix B decision.",
        "",
        "## Summary",
        "",
        f"- Process-lifetime fields typed ScriptRuntime / ScriptEngine / Scriptable / Rhino Context / Activity / View / Fragment / Dialog: "
        f"{len(strong)} strong, {len(weak)} weak; Android Context (information): {len(android_context)}",
        f"- Process-lifetime registries (maps / sets / lists / arrays) in script code or typed with threads, loopers, listeners, "
        f"windows, images, engines or class loaders: {len(registries)} (section c)",
    ]
    for name, _, _ in PAIRS:
        rows = pairs[name]
        unpaired = [row for row in rows if not row["paired"]]
        lines.append(f"- `{name}`: {len(rows)} files, {sum(r['registrations'] for r in rows)} calls, "
                     f"{len(unpaired)} files without a release call in the same file")
    lines += ["", "## (a) Process-lifetime fields holding script, Rhino or UI objects", "",
              "| File:line | Holder | Name | Type | Category | Holding |", "| --- | --- | --- | --- | --- | --- |"]
    for row in sorted(strong + weak, key=lambda r: (r["holding"] == "weak", r["file"], r["line"])):
        inferred = " (inferred)" if row.get("inferred") else ""
        lines.append(f"| `{row['file']}:{row['line']}` | {row['holder']} | `{row['name']}` | `{row['type']}`{inferred} | "
                     f"{', '.join(row['categories'])} | {row['holding']} |")
    lines += ["", "### Android Context fields (information; Application contexts are expected)", "",
              "| File:line | Holder | Name | Type |", "| --- | --- | --- | --- |"]
    for row in sorted(android_context, key=lambda r: (r["file"], r["line"])):
        lines.append(f"| `{row['file']}:{row['line']}` | {row['holder']} | `{row['name']}` | `{row['type']}` |")
    lines += ["", "## (c) Process-lifetime registries (appendix B.1 candidates)", "",
              "Static or object-level map / set / list / array fields in script-owned packages, or whose type mentions a thread, "
              "looper, listener, callback, window, image, engine, runtime or class loader. `weak-key registry` is a WeakHashMap.", "",
              "| File:line | Holder | Name | Type | Holding | Ownership (guess) |", "| --- | --- | --- | --- | --- | --- |"]
    for row in registries:
        inferred = " (inferred)" if row.get("inferred") else ""
        lines.append(f"| `{row['file']}:{row['line']}` | {row['holder']} | `{row['name']}` | `{row['type']}`{inferred} | "
                     f"{row['holding']} | {ownership(row['file'])} |")
    lines += ["", "## (b) Registration calls and same-file releases", ""]
    release_names = {"registerReceiver": "unregisterReceiver", "bindService": "unbindService",
                     ".subscribe(": "dispose / CompositeDisposable", "WebView(": "destroy()",
                     "addIdleHandler": "removeIdleHandler", "Looper.prepare": "quit / quitSafely / quitForThread",
                     "ThreadLocal": "remove()"}
    for name, _, _ in PAIRS:
        rows = sorted(pairs[name], key=lambda r: (r["paired"], r["ownership"] != "script", r["file"]))
        lines += [f"### `{name}` (release: {release_names[name]})", "",
                  "| File | Calls | Releases | Paired | Ownership (guess) |", "| --- | --- | --- | --- | --- |"]
        for row in rows:
            lines.append(f"| `{row['file']}` | {row['registrations']} | {row['releases']} | {'yes' if row['paired'] else '**no**'} | {row['ownership']} |")
        lines.append("")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=ROOT / "docs" / "memory" / "evidence")
    parser.add_argument("--json", action="store_true", help="Also write static-audit-<date>.json")
    parser.add_argument("--date", default=datetime.date.today().strftime("%Y%m%d"))
    parser.add_argument("--guard", action="store_true", help="Update the source guard whitelist instead of writing the report")
    parser.add_argument("--check", action="store_true", help="With --guard: write nothing, exit 1 when the whitelist is out of date")
    args = parser.parse_args()
    files, statics, pairs = audit()
    if args.guard:
        WHITELIST.parent.mkdir(parents=True, exist_ok=True)
        return write_guard(guard_rows(statics, pairs), args.check)
    args.out.mkdir(parents=True, exist_ok=True)
    head = git_head()
    report = args.out / f"static-audit-{args.date}.md"
    report.write_text(markdown(args.date, head, files, statics, pairs), encoding="utf-8")
    if args.json:
        (args.out / f"static-audit-{args.date}.json").write_text(json.dumps(
            {"head": head, "files": len(files), "statics": [r for r in statics if r["categories"]],
             "registries": registry_rows(statics), "pairs": pairs}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    strong = [row for row in statics if row["categories"] and row["holding"] != "weak" and row["categories"] != ["AndroidContext"]]
    print(f"{len(files)} files; strong static references {len(strong)}, weak {sum(1 for r in statics if r['categories'] and r['holding'] == 'weak')}, "
          f"registries {len(registry_rows(statics))}")
    for name, _, _ in PAIRS:
        rows = pairs[name]
        print(f"{name}: {len(rows)} files, unpaired {sum(1 for r in rows if not r['paired'])}")
    print(f"Report: {report.relative_to(ROOT) if report.is_relative_to(ROOT) else report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
