"""Compares member names of the obsolete __xxx__.js modules with the Kotlin Augmentable registrations.

Usage: py -3.12 -I tools/augment/inventory_diff.py [--host HOST] [--dts DTS_DIR] [--output report.json]

Heuristic: method shorthand `name(...) {`, getters `get name()`, object literal keys `name:` and the names passed to
__asGlobal__ are collected from each obsolete module. Kotlin names come from tools/api-coverage/extract_exports.py.
Names that only exist in JS are then filtered by the public d.ts declarations of the same module. Java prototype
members, sub-modules, returned objects and JS-internal helpers all show up as "JS only" and need manual review.
"""
import argparse
import json
import os
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
HOST = HERE.parents[1]
sys.path.insert(0, str(HOST / "tools" / "api-coverage"))

METHOD = re.compile(r'^\s{8,20}(?:async\s+)?([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{', re.M)
GETTER = re.compile(r'^\s{8,20}get\s+([A-Za-z_$][\w$]*)\s*\(\)', re.M)
KEY = re.compile(r'^\s{8,20}([A-Za-z_$][\w$]*)\s*:\s*(?!function)', re.M)
ASGLOBAL = re.compile(r'__asGlobal__\([^,]+,\s*\[([^\]]*)\]', re.S)
STRLIST = re.compile(r"'([A-Za-z_$][\w$]*)'")
SKIP = {'constructor', 'prototype', 'if', 'for', 'while', 'switch', 'return', 'function', 'catch', 'else', 'let',
        'const', 'var', 'new', 'typeof', 'throw', 'try', 'finally', 'default', 'case'}
DTS_MEMBER = re.compile(r'^\s{4,12}(?:readonly\s+)?(?:get\s+)?([A-Za-z_$][\w$]*)\s*(?:\(|\??:|<)', re.M)
DTS_TOPLEVEL = re.compile(r'^declare\s+(?:function|let|const|var|class)\s+([A-Za-z_$][\w$]*)', re.M)
MODULE_TO_DTS = {'globals': 'globals', 'RootAutomator': 'root-automator', 'Arrayx': 'arrayx', 'Mathx': 'mathx', 'Numberx': 'numberx'}


def js_names(source):
    names = set()
    for pattern in (METHOD, GETTER, KEY):
        names.update(pattern.findall(source))
    for m in ASGLOBAL.finditer(source):
        names.update(STRLIST.findall(m.group(1)))
    names -= SKIP
    return {n for n in names if not n.startswith('_') or n.startswith('__')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', type=pathlib.Path, default=HOST)
    parser.add_argument('--dts', type=pathlib.Path, default=HOST.parent / 'AutoJs6-TypeScript-Declarations' / 'declarations' / 'autojs6')
    parser.add_argument('--output', type=pathlib.Path)
    args = parser.parse_args()

    from extract_exports import extract
    kt = extract(args.host)
    kt_by_module = {}
    for r in kt['records']:
        kt_by_module.setdefault(r['module'].lower(), set()).add(r['member'])
        kt_by_module.setdefault(r['ownerModule'].lower(), set()).add(r['member'])
    kt_all = {r['member'] for r in kt['records']}

    js_dir = args.host / 'app/src/main/assets/modules/obsolete'
    report = {}
    print(f"{'module':16} {'js':>4} {'kt':>4} {'js-only':>8} {'js-only(all)':>12} {'declared':>9}")
    for fn in sorted(os.listdir(js_dir)):
        if not fn.startswith('__'):
            continue
        mod = fn[2:-5]
        names = js_names((js_dir / fn).read_text(encoding='utf-8'))
        key = mod.lower()
        ktnames = kt_by_module.get(key, set())
        if key == 'globals':
            ktnames = set().union(*(kt_by_module.get(k, set()) for k in ('global', 'legacy', 'species', 'isnullish')))
        js_only = sorted(n for n in names if n not in ktnames)
        js_only_all = sorted(n for n in js_only if n not in kt_all)
        dts_file = args.dts / f"aj6-int-{MODULE_TO_DTS.get(mod, key)}.d.ts"
        declared = set()
        if dts_file.exists():
            src = dts_file.read_text(encoding='utf-8')
            declared = set(DTS_MEMBER.findall(src)) | set(DTS_TOPLEVEL.findall(src))
        public_missing = [n for n in js_only_all if n in declared]
        report[mod] = {
            'js_names': sorted(names), 'kt_names': sorted(ktnames), 'js_only_in_module': js_only,
            'js_only_everywhere': js_only_all, 'declared_in_dts_but_unregistered': public_missing,
        }
        print(f"{mod:16} {len(names):4} {len(ktnames):4} {len(js_only):8} {len(js_only_all):12} {len(public_missing):9}")
    print()
    for mod, r in report.items():
        if r['declared_in_dts_but_unregistered']:
            print(f"[{mod}] declared in d.ts, not registered in Kotlin (review): {', '.join(r['declared_in_dts_but_unregistered'])}")
    if args.output:
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f"wrote {args.output}")


if __name__ == '__main__':
    sys.exit(main())
