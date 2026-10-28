"""The node method registry of pickup / detect rendered for the documentation and the declarations (ROADMAP selector
P4.2 / P4.3, D11). Reads app/src/test/resources/node-method-registry.txt, the snapshot NodeMethodRegistrySnapshotTest keeps
equal to NodeMethodRegistry.uiObject on the JVM (when that test fails after an intended change, copy
app/build/node-method-registry.txt over the snapshot, then run this script), and rewrites the generated blocks of:

  - <docs>/api/dataTypes.md, between "<!-- node-method-registry:begin -->" and "<!-- node-method-registry:end -->":
    the method names of UiObjectInvokable (the methods of the node, then the readers of AccessibilityNodeInfoCompat);
  - <dts>/declarations/autojs6/aj6-int-selector.d.ts and the copy of the Ace Editor plugin, between
    "// <node-method-registry:begin>" and "// <node-method-registry:end>": Detect.ResultTypeFuncWithoutParams (the
    spellings with an overload taking no argument) and Detect.ResultTypeFuncWithParams (one tuple per overload taking
    arguments), which Pickup.ResultType and Detect.ResultType include.

A spelling is a method name, or for a getter getXyz also xyz (NodeMethodTable.lookup tries the name, then the getter);
xyz is left out where it is a name of its own (text / getText). --check writes nothing and exits 1 when a block differs.
A repository given as "-" is skipped.

Usage: py -3.12 -I tools/selector/registry_export.py [--docs DIR] [--dts DIR] [--ace DIR] [--check]
"""
import argparse
import json
import pathlib
import re
import sys

HOST = pathlib.Path(__file__).resolve().parents[2]
SNAPSHOT = HOST / "app/src/test/resources/node-method-registry.txt"

# The methods whose signature names a class of a later API: the registry leaves them out below that level (appendix C Q16).
MIN_API = {"getExtraRenderingInfo": 30}

NUMBERS = {"int", "long", "short", "byte", "float", "double", "java.lang.Integer", "java.lang.Long", "java.lang.Short",
           "java.lang.Byte", "java.lang.Float", "java.lang.Double", "java.lang.Number"}
BOOLEANS = {"boolean", "java.lang.Boolean"}
STRINGS = {"java.lang.String", "java.lang.CharSequence"}
# Classes the declarations name otherwise. zh-CN: 声明中另有名称的类.
DTS_NAMES = {"org.autojs.autojs.core.accessibility.UiSelector": "Internal.Selector"}

DOCS_BEGIN, DOCS_END = "<!-- node-method-registry:begin -->", "<!-- node-method-registry:end -->"
DTS_BEGIN, DTS_END = "// <node-method-registry:begin>", "// <node-method-registry:end>"


def read_snapshot():
    """[(kind, name, [parameter types], varargs)] in the order of the snapshot."""
    methods = []
    for line in SNAPSHOT.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        m = re.fullmatch(r"(own|compat_getter) ([A-Za-z_][A-Za-z0-9_]*)\((.*)\)", line)
        if not m:
            sys.exit(f"Malformed line of {SNAPSHOT.name}: {line}")
        params = [p.strip() for p in m.group(3).split(",")] if m.group(3) else []
        varargs = bool(params) and params[-1].endswith("...")
        if varargs:
            params[-1] = params[-1][:-3]
        methods.append((m.group(1), m.group(2), params, varargs))
    if not methods:
        sys.exit(f"{SNAPSHOT} holds no method; run NodeMethodRegistrySnapshotTest and copy app/build/node-method-registry.txt")
    return methods


def short(name):
    """xyz for a getter getXyz, else None."""
    return name[3].lower() + name[4:] if len(name) > 3 and name.startswith("get") and name[3].isupper() else None


def spellings(name, names):
    """The spellings that find the methods of name: itself, and xyz for getXyz unless xyz is a name of its own."""
    alias = short(name)
    return [name] if alias is None or alias in names else [name, alias]


def ts_type(java, element=False, docs=False):
    """The declaration type of a Java parameter type; docs: the simple class name for the documentation."""
    base = java[:-2] if java.endswith("[]") else java
    if base in NUMBERS:
        t = "number"
    elif base in BOOLEANS:
        t = "boolean"
    elif base in STRINGS:
        t = "string"
    elif base == "java.lang.Object":
        t = "any"
    elif docs:
        t = base.rsplit(".", 1)[-1].replace("$", ".")
    else:
        t = DTS_NAMES.get(base, base.replace("$", "."))
    return t + "[]" if java.endswith("[]") and not element else t


def tuple_of(spelling, params, varargs):
    items = [f"'{spelling}'"] + [ts_type(p) for p in (params[:-1] if varargs else params)]
    if varargs:
        items.append(f"...{ts_type(params[-1], element=True)}[]")
    return "[" + ", ".join(items) + "]"


def wrap(items, indent, width=120):
    """Lines of indent and items joined by " | ", each starting with "| ", wrapped before width."""
    lines, current = [], ""
    for item in items:
        piece = "| " + item if not current else current + " | " + item
        if current and len(indent) + len(piece) > width:
            lines.append(indent + current)
            current = "| " + item
        else:
            current = piece
    lines.append(indent + current)
    return "\n".join(lines)


def render_dts(methods):
    names = {name for _, name, _, _ in methods}
    plain, tuples = [], []
    for _, name, params, varargs in methods:
        for spelling in spellings(name, names):
            if not params or (varargs and len(params) == 1):
                if f"'{spelling}'" not in plain:
                    plain.append(f"'{spelling}'")
            if params:
                t = tuple_of(spelling, params, varargs)
                if t not in tuples:
                    tuples.append(t)
    plain.sort(key=lambda s: (s.lower(), s))
    # By spelling; the overloads of a spelling keep the order of the snapshot (fewer parameters first).
    tuples.sort(key=lambda t: (t.split("'")[1].lower(), t.split("'")[1]))
    indent = " " * 8
    return "\n".join([
        f"    {DTS_BEGIN}",
        "    // Generated by AutoJs6 tools/selector/registry_export.py from NodeMethodRegistry (the node methods a",
        "    // pickup / detect result type calls by name, ROADMAP selector D11); do not edit by hand.",
        f"    // {len(names)} names; getXyz is also spelt xyz. getExtraRenderingInfo needs API 30.",
        "    type ResultTypeFuncWithoutParams =",
        wrap(plain, indent) + ";",
        "    type ResultTypeFuncWithParams =",
        wrap(tuples, indent) + ";",
        f"    {DTS_END}",
    ])


def render_docs(methods):
    groups = {"own": {}, "compat_getter": {}}
    for kind, name, params, varargs in methods:
        shown = groups[kind].setdefault(name, [])
        if params:
            args = [ts_type(p, docs=True) for p in (params[:-1] if varargs else params)]
            if varargs:
                args.append("..." + ts_type(params[-1], element=True, docs=True))
            shown.append(f"{name}({', '.join(args)})")
        else:
            shown.append(name)
    out = [DOCS_BEGIN, ""]
    titles = {
        "own": "控件自有方法 ([UiObject](uiObjectType) 与 [控件行为](uiObjectActionsType), {n} 个名称; 带参数的重载列出参数类型):",
        "compat_getter": "[AccessibilityNodeInfoCompat](https://developer.android.com/reference/androidx/core/view/accessibility/AccessibilityNodeInfoCompat) 的无参只读方法 ({n} 个名称; `getXyz` 也可写作 `xyz`, 与上一组同名时调用上一组的方法):",
    }
    for kind in ("own", "compat_getter"):
        entries = groups[kind]
        out.append(titles[kind].format(n=len(entries)))
        out.append("")
        by_letter = {}
        for name in sorted(entries, key=lambda s: (s.lower(), s)):
            suffix = f" (API {MIN_API[name]}+)" if name in MIN_API else ""
            by_letter.setdefault(name[0].lower(), []).extend(f"`{form}`{suffix}" for form in entries[name])
        for letter in sorted(by_letter):
            out.append("- " + " / ".join(by_letter[letter]))
        out.append("")
    out.append(DOCS_END)
    return "\n".join(out)


def replace_block(path, begin, end, block, check):
    text = path.read_text(encoding="utf-8")
    newline = "\r\n" if "\r\n" in text else "\n"
    body = text.replace("\r\n", "\n")
    pattern = re.compile(r"[ \t]*" + re.escape(begin) + r".*?" + re.escape(end), re.S)
    if not pattern.search(body):
        print(f"{path}: FAIL markers {begin} ... {end} not found")
        return False
    updated = pattern.sub(lambda _: block, body, count=1)
    if updated == body:
        print(f"{path}: up to date")
        return True
    if check:
        print(f"{path}: FAIL differs from the registry")
        return False
    path.write_bytes(updated.replace("\n", newline).encode("utf-8"))
    print(f"{path}: written")
    return True


def ace_copy(ace):
    lock = json.loads((ace / "tools/ace-lsp/ace-builds-lock.json").read_text(encoding="utf-8"))
    return ace / lock["assetRoot"] / "autojs6/types/autojs6/aj6-int-selector.d.ts"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--docs", default=str(HOST.parent / "AutoJs6-Documentation"))
    parser.add_argument("--dts", default=str(HOST.parent / "AutoJs6-TypeScript-Declarations"))
    parser.add_argument("--ace", default=str(HOST.parent / "AutoJs6-Plugin-Ace-Editor"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    methods = read_snapshot()
    kinds = {k: len({n for kk, n, _, _ in methods if kk == k}) for k in ("own", "compat_getter")}
    print(f"registry: {len({n for _, n, _, _ in methods})} names, {len(methods)} methods (own {kinds['own']}, compat getters {kinds['compat_getter']})")
    ok = True
    if args.docs != "-":
        ok &= replace_block(pathlib.Path(args.docs) / "api/dataTypes.md", DOCS_BEGIN, DOCS_END, render_docs(methods), args.check)
    dts = render_dts(methods)
    if args.dts != "-":
        ok &= replace_block(pathlib.Path(args.dts) / "declarations/autojs6/aj6-int-selector.d.ts", DTS_BEGIN, DTS_END, dts, args.check)
    if args.ace != "-":
        ok &= replace_block(ace_copy(pathlib.Path(args.ace)), DTS_BEGIN, DTS_END, dts, args.check)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
