"""Compares two surface snapshots written by device_surface_snapshot.py (roadmap R1 / P2.4).

Usage: py -3.12 -I tools/augment/surface_compare.py BEFORE.txt AFTER.txt [--only NAME ...] [--allow-length]

Reports the property lines only in one of the files, the lines whose attributes or value differ, and differences of the
global key order and of the $ aliases. --only restricts the comparison to the given top-level names (a name covers its
"$" alias and everything under it; "@Array.prototype" covers that extra root) and to the order of those names among the
global keys. --allow-length accepts differences that are only the length of functions (appendix C Q9): the "length=N" of
a function line and the value of its ".length" / ".arity" lines. Header lines (version, device, counts) are not compared.
Exits with 1 when an unaccepted difference remains.
"""
import argparse
import pathlib
import re
import sys

LENGTH_IN_VALUE = re.compile(r"(function name='[^']*') length=\d+")


def read(path):
    props, keys, aliases = {}, [], {}
    for line in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        if line.startswith("# keys order:"):
            keys = line[len("# keys order:"):].split()
        elif line.startswith("# $ aliases:"):
            aliases = dict(item.split("=", 1) for item in line[len("# $ aliases:"):].split())
        elif line and not line.startswith("#"):
            path_part, _, rest = line.partition("\t")
            props[path_part] = rest
    return props, keys, aliases


def covered(path, names):
    if not names:
        return True
    for name in names:
        if name.startswith("@"):
            if path == name or path.startswith(name + "."):
                return True
            continue
        for candidate in (name, "$" + name):
            if path == candidate or path.startswith(candidate + "."):
                return True
    return False


def length_only(path, a, b):
    if path.endswith(".length") or path.endswith(".arity"):
        return a.split("\t")[0] == b.split("\t")[0] and a.split("\t")[1].startswith("number") and b.split("\t")[1].startswith("number")
    return LENGTH_IN_VALUE.sub(r"\1", a) == LENGTH_IN_VALUE.sub(r"\1", b)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("before")
    parser.add_argument("after")
    parser.add_argument("--only", nargs="*", default=[], help="top-level names (or @roots) to compare")
    parser.add_argument("--allow-length", action="store_true", help="accept differences of function length only")
    args = parser.parse_args()

    a_props, a_keys, a_aliases = read(args.before)
    b_props, b_keys, b_aliases = read(args.after)
    problems = 0
    accepted = 0

    for path in sorted(set(a_props) | set(b_props)):
        if not covered(path, args.only):
            continue
        a, b = a_props.get(path), b_props.get(path)
        if a == b:
            continue
        if a is None:
            print(f"only after   {path}\t{b}")
        elif b is None:
            print(f"only before  {path}\t{a}")
        elif args.allow_length and length_only(path, a, b):
            accepted += 1
            continue
        else:
            print(f"changed      {path}\n    before {a}\n    after  {b}")
        problems += 1

    top = [n for n in args.only if not n.startswith("@")]
    a_order = [k for k in a_keys if not top or k.lstrip("$") in top]
    b_order = [k for k in b_keys if not top or k.lstrip("$") in top]
    if a_order != b_order:
        print(f"keys order differs:\n    before {' '.join(a_order)}\n    after  {' '.join(b_order)}")
        problems += 1
    a_alias = {k: v for k, v in a_aliases.items() if not top or k[1:] in top}
    b_alias = {k: v for k, v in b_aliases.items() if not top or k[1:] in top}
    if a_alias != b_alias:
        print(f"$ aliases differ:\n    before {a_alias}\n    after  {b_alias}")
        problems += 1

    scope = " ".join(args.only) if args.only else "all"
    print(f"compared {scope}: {problems} difference(s), {accepted} accepted function length difference(s), "
          f"keys {len(a_order)} / {len(b_order)}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
