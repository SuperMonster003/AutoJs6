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

- stubs (P5.2e): the type stubs in the plugin checkout and their copies in the Ace Editor checkout must
  name exactly the listed API: a .pyi package (public definitions; signatures with defaults written as ...),
  a LuaLS ---@meta file (functions and assigned values), a jar of the entry classes (read as the AAR) or an
  Ace language index (modules and class globals of the listed types, parameter counts). Without the
  checkout: skipped.
- host (Q109 (c)): every type and method of a plugin's frozen AAR must exist, with the same parameter count,
  in the host's JvmEntryApi.kt; host methods the plugin's older Entry API lacks are only printed.
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
JVM_INDEX_NAME = re.compile(r"^(?:AutoJsJvmEntry|Jvm[A-Z]\w*)$")
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
    path = root / config["source"]
    with zipfile.ZipFile(path) as archive:
        jar = zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) if path.suffix == ".aar" else archive
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


def stub_form(signature):
    """A Python signature with every default written as ... and builtins.<name> as <name>, as the .pyi stubs spell it."""
    node = ast.parse(f"def f{signature}: ...").body[0]
    node.args.defaults = [ast.Constant(...) for _ in node.args.defaults]
    node.args.kw_defaults = [item and ast.Constant(...) for item in node.args.kw_defaults]
    return (ast.unparse(node.args).join("()") + (" -> " + ast.unparse(node.returns) if node.returns else "")).replace("builtins.", "")


def jvm_arity(signature):
    return signature.count(",") + (signature[1] != ")")


def read_python_stubs(package):
    """Public definitions of __init__.pyi and of the submodules it re-exports (`from . import x as x`)."""
    names = {"functions": {}, "constants": [], "classes": []}

    def body(name):
        return ast.parse((package / f"{name}.pyi").read_text(encoding="utf-8")).body

    def add(prefix, node):
        target = node.target if isinstance(node, ast.AnnAssign) else node.targets[0] if isinstance(node, ast.Assign) else node
        name = getattr(target, "name", None) or getattr(target, "id", None) or "_"
        if name.startswith("_") or not isinstance(node, (ast.FunctionDef, ast.ClassDef, ast.AnnAssign, ast.Assign)):
            return
        if isinstance(node, ast.FunctionDef):
            signature = ast.unparse(node.args).join("()") + (" -> " + ast.unparse(node.returns) if node.returns else "")
            names["functions"][prefix + name] = stub_form(signature)
        else:
            names["classes" if isinstance(node, ast.ClassDef) else "constants"].append(prefix + name)

    for node in body("__init__"):
        for alias in node.names if isinstance(node, ast.ImportFrom) and node.level == 1 else ():
            if alias.asname == alias.name:
                for child in body(node.module or alias.name):
                    if node.module is None or getattr(child, "name", None) == alias.name:
                        add("" if node.module else alias.name + ".", child)
        add("", node)
    return names


def read_lua_meta(path, prefix):
    """Functions (`function autojs.x.y(...)`) and assigned values that are not a table of functions."""
    text, module = path.read_text(encoding="utf-8"), re.escape(prefix)
    functions = {name: None for name in re.findall(rf"^function {module}([\w.]+)\(", text, re.M)}
    assigned = re.findall(rf"^{module}([\w.]+) = ", text, re.M)
    return {"functions": functions, "values": [name for name in assigned if not any(item.startswith(name + ".") for item in functions)]}


def read_jvm_index(path):
    """Modules and class globals of an Ace language index named like the JVM Entry API, with parameter counts."""
    text = path.read_text(encoding="utf-8")
    index = json.loads(text[text.index("] = {") + 4:text.rindex("};") + 1])
    names = {"functions": {}, "types": [item["name"] for item in index["globals"] if item["type"] == "class" and JVM_INDEX_NAME.match(item["name"])]}
    for module, members in index["modules"].items():
        for member in members if JVM_INDEX_NAME.match(module) else ():
            names["functions"][f"{module}.{member['name']}"] = len(python_parameters(re.search(r"\((.*)\)", member["signature"]).group(1)))
    return names


def read_stubs(path, config):
    if path.suffix == ".js":
        return read_jvm_index(path)
    if path.suffix == ".jar":
        return read_jvm(path.parent, dict(config, source=path.name))
    return read_lua_meta(path, config["prefix"]) if path.suffix == ".lua" else read_python_stubs(path)


def stub_view(names, path):
    """Listed or stub names as a stub surface can carry them: .pyi signatures, parameter counts of an index."""
    view = flat(names)
    if path.suffix == ".js":
        return {key: jvm_arity(value) if isinstance(value, str) and value.startswith("(") else value for key, value in view.items()}
    return {key: stub_form(value) if not path.suffix and isinstance(value, str) and value.startswith("(") else value for key, value in view.items()}


def differences(found, stored):
    return ([f"+{key}" for key in found if key not in stored] + [f"-{key}" for key in stored if key not in found]
            + [f"~{key}" for key in found if key in stored and found[key] != stored[key]])


def read_host_entry_api(path):
    """Type names and Type.method parameter counts the host's JvmEntryApi.kt declares (Q109 (c))."""
    names, owner = {}, None
    for line in path.read_text(encoding="utf-8").splitlines():
        if match := re.match(r"(?:fun )?(?:interface|class) (\w+)", line):
            owner = match.group(1)
            names[owner] = None
        elif owner and (match := re.match(r"\s+fun (\w+)\(([^)]*)\)", line)):
            names[f"{owner}.{match.group(1)}"] = len([item for item in re.sub(r"<[^<>]*>", "", match.group(2)).split(",") if item.strip()])
    return names


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
    parser.add_argument("--ace", type=Path, help="AutoJs6-Plugin-Ace-Editor checkout (default: AUTOJS6_ACE_EDITOR_ROOT, else the sibling directory)")
    parser.add_argument("--write", action="store_true", help="Rewrite language-runtimes.json from the plugin checkouts that exist")
    args = parser.parse_args()
    host = args.host.resolve()
    docs = (args.documentation or Path(os.environ.get("AUTOJS6_DOCUMENTATION_ROOT") or host.parent / "AutoJs6-Documentation")).resolve()
    ace = (args.ace or Path(os.environ.get("AUTOJS6_ACE_EDITOR_ROOT") or host.parent / "AutoJs6-Plugin-Ace-Editor")).resolve()
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
            changed = differences(flat(names), flat(listed["runtimes"].get(name, {})))
            result.append(f"source FAIL: {' '.join(changed)} against language-runtimes.json (run --write)" if changed else "source pass")
            if config.get("hostApi"):
                host_api, plugin = read_host_entry_api(host / config["hostApi"]), flat(names)
                foreign = [key for key, value in plugin.items() if key not in host_api or (value.startswith("(") and jvm_arity(value) != host_api[key])]
                newer = sorted(set(host_api) - set(plugin))
                result.append(f"host FAIL: {', '.join(foreign)} not in {Path(config['hostApi']).name}" if foreign
                              else "host subset pass" + (f" (host-only: {', '.join(newer)})" if newer else ""))
        else:
            result.append(f"source skipped (no {root / config['source']})")
        for surface, stub in config.get("stubs", {}).items():
            checkout = ace if surface.startswith("ace") else root
            path = checkout / stub
            if checkout.is_dir() and not path.exists():
                result.append(f"{surface} stubs FAIL: no {stub}")
            elif path.exists():
                changed = differences(stub_view(read_stubs(path, config), path), stub_view(listed["runtimes"].get(name, {}), path))
                result.append(f"{surface} stubs FAIL ({stub}): {' '.join(changed)}" if changed else f"{surface} stubs pass")
            else:
                result.append(f"{surface} stubs skipped (no {path})")
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
