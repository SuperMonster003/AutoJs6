#!/usr/bin/env python3
"""Non-JS language runtime APIs against their documentation pages (api-coverage Q77 (a), P5.2d).

The Python, Java, Kotlin and Lua runtime plugins give their scripts own APIs that have no Node / Bun
columns and stay out of the runtime matrix (PLUGIN-APIS.md 8.1, Q2-4). policy.json "languageRuntimes"
names, per plugin, the checkout (a sibling directory name or a local path; --plugin NAME=PATH overrides),
the registration source and the documentation page. language-runtimes.json is the committed name list:

- source: the names the plugin checkout registers, compared with the list (--write rewrites it). Python:
  `__all__` of the package and of its submodules (functions with their signatures, constants, classes);
  java / kotlin: the frozen protocol/jvm-source-api.aar, every public type and method reachable from the
  entry interface through parameter, return and thrown types of its package; lua: the table the C
  function builds (stack simulation of lua_newtable / lua_setfield). Without the checkout: skipped.
- doc: `### <prefix><name>(params)` headings for functions (Python parameter names and defaults, JVM
  parameter counts must match the signature), a heading or a table row naming `<prefix><name>` for the
  rest. Names missing from the page, API headings or rows the list lacks and a missing page fail;
  without the documentation checkout: skipped.

P5.2e adds the type stubs (.pyi, LuaLS meta, Java / Kotlin stubs) as another surface checked against the
same list (a reader per stub kind next to check_page).
"""
import argparse
import ast
import io
import json
import os
from pathlib import Path
import re
import struct
import sys
import zipfile

HERE = Path(__file__).resolve().parent
LIST = HERE / "language-runtimes.json"
HEADING = re.compile(r"^### ([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+)(?:\((.*)\))?\s*$")
ROW = re.compile(r"^\| `([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)` \|")
JVM_TYPES = {"Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long", "F": "float", "D": "double", "V": "void"}


def read_python(root, config):
    names = {"functions": {}, "constants": [], "classes": []}

    def module_items(path):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        exported = next(ast.literal_eval(node.value) for node in tree.body if isinstance(node, ast.Assign)
                        and any(getattr(target, "id", None) == "__all__" for target in node.targets))
        defined = {getattr(node, "name", None) or getattr(getattr(node, "target", None), "id", None)
                   or getattr(node.targets[0], "id", None): node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.ClassDef, ast.Assign, ast.AnnAssign))}
        imported = {alias.asname or alias.name: node.module for node in tree.body if isinstance(node, ast.ImportFrom) and node.level == 1 for alias in node.names}
        return exported, defined, imported

    def add(prefix, name, node):
        if isinstance(node, ast.FunctionDef):
            signature = ast.unparse(node.args).join("()") + (" -> " + ast.unparse(node.returns) if node.returns else "")
            names["functions"][prefix + name] = signature
        else:
            names["classes" if isinstance(node, ast.ClassDef) else "constants"].append(prefix + name)

    package = root / config["source"]
    exported, defined, imported = module_items(package / "__init__.py")
    for name in exported:
        if (package / f"{name}.py").is_file() and imported.get(name) is None:
            sub_exported, sub_defined, _ = module_items(package / f"{name}.py")
            for member in sub_exported:
                add(name + ".", member, sub_defined[member])
        else:
            add("", name, defined[name] if name in defined else module_items(package / f"{imported[name]}.py")[1][name])
    return names


def class_info(data):
    """(access flags, [(name, descriptor, access, thrown classes)]) of one class file."""
    pool, offset = [None], 10
    while len(pool) < struct.unpack(">H", data[8:10])[0]:
        tag = data[offset]
        if tag == 1:
            length = struct.unpack(">H", data[offset + 1:offset + 3])[0]
            pool.append(data[offset + 3:offset + 3 + length].decode("utf-8", "replace"))
            offset += 3 + length
            continue
        pool.append(struct.unpack(">H", data[offset + 1:offset + 3])[0] if tag == 7 else None)
        offset += 1 + {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}[tag]
        if tag in (5, 6):
            pool.append(None)
    access = struct.unpack(">H", data[offset:offset + 2])[0]
    offset += 8 + 2 * struct.unpack(">H", data[offset + 6:offset + 8])[0]
    members = []
    for _ in range(2):  # fields, then methods
        members.clear()
        count, offset = struct.unpack(">H", data[offset:offset + 2])[0], offset + 2
        for _ in range(count):
            flags, name, descriptor, attributes = struct.unpack(">HHHH", data[offset:offset + 8])
            offset, thrown = offset + 8, []
            for _ in range(attributes):
                attribute, length = struct.unpack(">HI", data[offset:offset + 6])
                if pool[attribute] == "Exceptions":
                    total = struct.unpack(">H", data[offset + 6:offset + 8])[0]
                    thrown = [pool[pool[index]] for index in struct.unpack(f">{total}H", data[offset + 8:offset + 8 + 2 * total])]
                offset += 6 + length
            members.append((pool[name], pool[descriptor], flags, thrown))
    return access, members


def jvm_signature(descriptor):
    parameters, result = re.fullmatch(r"\((.*)\)(.+)", descriptor).groups()
    def simple(item):
        return JVM_TYPES.get(item.lstrip("["), item.lstrip("[")[1:-1].rsplit("/", 1)[-1]) + "[]" * (len(item) - len(item.lstrip("[")))
    return "(" + ", ".join(map(simple, re.findall(r"\[*(?:[ZBCSIJFD]|L[^;]+;)", parameters))) + ") -> " + simple(result)


def read_jvm(root, config):
    with zipfile.ZipFile(root / config["source"]) as aar, zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as jar:
        classes = {name[:-6]: class_info(jar.read(name)) for name in jar.namelist() if name.endswith(".class")}
    package, names, pending, seen = config["entry"].rsplit("/", 1)[0] + "/", {"functions": {}, "types": []}, [config["entry"]], set()
    while pending:
        internal = pending.pop(0)
        if internal in seen or internal not in classes or not classes[internal][0] & 1:
            continue
        seen.add(internal)
        names["types"].append(internal.rsplit("/", 1)[1])
        for name, descriptor, flags, thrown in classes[internal][1]:
            if flags & 1 and not flags & 0x1000 and not name.startswith("<"):
                names["functions"][internal.rsplit("/", 1)[1] + "." + name] = jvm_signature(descriptor)
            pending += [item for item in re.findall(r"L([^;]+);", descriptor) + thrown if item.startswith(package)]
    return names


def read_lua(root, config):
    text = (root / config["source"]).read_text(encoding="utf-8")
    start = re.search(r"\bint " + config["function"] + r"\(lua_State\* \w+\) \{", text).end()
    depth, end = 1, start
    while depth:
        depth += {"{": 1, "}": -1}.get(text[end], 0)
        end += 1
    stack, tables = [], []
    for match in re.finditer(r"\b(lua_newtable|lua_pushcfunction|lua_pushcclosure|push_native_arguments|lua_getglobal|lua_getfield|"
                             r"lua_pushvalue|lua_setfield|lua_setglobal|lua_pop)\(([^()]*)\)", text[start:end]):
        call, arguments = match.group(1), [item.strip() for item in match.group(2).split(",")]
        if call == "lua_newtable":
            tables.append({})
            stack.append(tables[-1])
        elif call == "lua_pushvalue":
            stack.append(stack[-1])
        elif call == "lua_setfield":
            value = stack.pop()
            if isinstance(stack[-1], dict):
                stack[-1][arguments[2].strip('"')] = value
        elif call in ("lua_setglobal", "lua_pop"):
            del stack[-int(arguments[1]) if call == "lua_pop" else -1:]
        else:
            del stack[len(stack) - int(arguments[2]) if call == "lua_pushcclosure" else len(stack):]
            stack.append("function" if call.startswith("lua_pushc") else "value" if call == "push_native_arguments" else None)
    nested = {id(value) for table in tables for value in table.values() if isinstance(value, dict)}
    names = {"functions": {}, "values": []}

    def walk(table, prefix):
        for key, value in table.items():
            if isinstance(value, dict):
                walk(value, prefix + key + ".")
            elif value == "function":
                names["functions"][prefix + key] = None
            elif value == "value":
                names["values"].append(prefix + key)
    walk(next(table for table in tables if table and id(table) not in nested), "")
    return names


READERS = {"python-package": read_python, "jvm-entry-aar": read_jvm, "lua-module": read_lua}


def flat(names):
    """{name: signature (functions) or kind} of one runtime's names."""
    return {key: value for kind, items in names.items() if kind != "kind"
            for key, value in (items.items() if isinstance(items, dict) else ((item, kind) for item in items))}


def python_parameters(text):
    """Parameter names with "=" for a default and the bare "*" / "/" markers, from a Python signature or a heading."""
    return [re.sub(r"\s*[:=].*", "", item.strip()) + ("=" if "=" in item else "") for item in re.split(r",(?![^()\[\]]*[)\]])", text) if item.strip()]


def check_page(text, prefix, listed):
    """Failures of one documentation page against the listed names of its runtime."""
    found, problems = {}, []
    for line in text.splitlines():
        match = HEADING.match(line) or ROW.match(line)
        if match and match.group(1).startswith(prefix):
            found[match.group(1)[len(prefix):]] = match.group(2) if match.re is HEADING else None
    for name, signature in listed.get("functions", {}).items():
        params = found.get(name, "")
        if params is None:
            problems.append(f"{prefix}{name} needs a heading with its parameters")
        elif name in found and listed.get("kind") == "python-package":
            expected = python_parameters(signature[1:signature.rindex(")", 0, signature.find(" -> ") if " -> " in signature else None)])
            if python_parameters(params) != expected:
                problems.append(f"{prefix}{name}({params}) differs from ({', '.join(expected)})")
        elif name in found and signature and len(python_parameters(params)) != signature.count(",") + (signature[1] != ")"):
            problems.append(f"{prefix}{name}({params}) differs from {signature}")
    listed_names = set(listed.get("functions", {})).union(*(listed.get(kind, []) for kind in ("constants", "classes", "types", "values")))
    missing, extra = sorted(listed_names - set(found)), sorted(set(found) - listed_names)
    return ([f"missing {', '.join(prefix + name for name in missing)}"] if missing else []) + \
           ([f"extra {', '.join(prefix + name for name in extra)}"] if extra else []) + problems


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", type=Path, default=HERE.parents[1])
    parser.add_argument("--documentation", type=Path, help="AutoJs6-Documentation checkout (default: AUTOJS6_DOCUMENTATION_ROOT, else the sibling directory)")
    parser.add_argument("--plugin", action="append", default=[], metavar="NAME=PATH", help="Checkout of one runtime plugin (default: policy.json)")
    parser.add_argument("--write", action="store_true", help="Rewrite language-runtimes.json from the plugin checkouts that exist")
    args = parser.parse_args()
    host = args.host.resolve()
    docs = (args.documentation or Path(os.environ.get("AUTOJS6_DOCUMENTATION_ROOT") or host.parent / "AutoJs6-Documentation")).resolve()
    runtimes = json.loads((HERE / "policy.json").read_text(encoding="utf-8"))["languageRuntimes"]
    roots = {name: host.parent / config["repository"] for name, config in runtimes.items()} | dict(item.split("=", 1) for item in args.plugin)
    listed = json.loads(LIST.read_text(encoding="utf-8")) if LIST.is_file() else {"schema": "autojs6-language-runtimes-v1", "runtimes": {}}
    failed = False
    for name, config in runtimes.items():
        root, result = Path(roots[name]), []
        if (root / config["source"]).exists():
            names = READERS[config["kind"]](root, config)
            if args.write:
                listed["runtimes"][name] = names
            source, stored = flat(names), flat(listed["runtimes"].get(name, {}))
            changed = ([f"+{key}" for key in source if key not in stored] + [f"-{key}" for key in stored if key not in source]
                       + [f"~{key}" for key in source if key in stored and source[key] != stored[key]])
            result.append(f"source FAIL: {' '.join(changed)} against language-runtimes.json (run --write)" if changed else "source pass")
        else:
            result.append(f"source skipped (no {root / config['source']})")
        entry = dict(listed["runtimes"].get(name, {}), kind=config["kind"])
        page = docs / "api" / f"{config['page']}.md"
        if page.is_file():
            problems = check_page(page.read_text(encoding="utf-8"), config["prefix"], entry)
            result.append(f"doc FAIL ({page.name}): " + "; ".join(problems) if problems else f"doc pass ({page.name})")
        elif (docs / "api").is_dir():
            result.append(f"doc FAIL: no {page}")
        else:
            result.append(f"doc skipped (no {docs / 'api'})")
        counts = ", ".join(f"{len(value)} {key}" for key, value in entry.items() if key != "kind")
        failed |= any("FAIL" in item for item in result)
        print(f"{name}: {', '.join(result)} [{counts}]")
    if args.write:
        LIST.write_text(json.dumps(listed, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
