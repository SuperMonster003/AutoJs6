# -*- coding: utf-8 -*-
"""Classify the frozen com.stardust compatibility package and keep :compat:stardust in sync (roadmap P7.2).

The package lives in two places that must stay consistent:
  - compat/stardust/src/main/java/com/stardust/**  : standalone files (Android / AndroidX / Rhino / third-party only)
  - app/src/main/java/com/stardust/**              : bridging files (reach host classes) and tainted files
                                                     (depend transitively on a bridging file)

Classification rules:
  - bridging  : imports org.autojs.*, references org.autojs. inline in code (e.g. the BuildConfig typealiases,
                `extends org.autojs.autojs.rhino.TokenStream`), or imports a class that exists as host source outside
                com.stardust (ezy.assist.compat, host-side org.mozilla.javascript additions). String literals such as
                "org.autojs.autojs4" are ignored.
  - tainted   : depends on a bridging or tainted file through an import, a same-package simple name or an inline FQN
  - standalone: everything else

Usage (from the repository root):
  py tools/stardust/split_stardust_compat.py            # report; exit 1 when a file is in the wrong half
  py tools/stardust/split_stardust_compat.py --apply    # git mv files into the half the classification requires

The classification is a static approximation; `./gradlew.bat :compat:stardust:assembleDebug` is the authority. When
the module fails to compile because of a missed reference, fix the rule here rather than moving the file by hand.

zh-CN: 冻结的 com.stardust 兼容包分两处存放: 独立文件在 compat/stardust, 桥接 (直接引用宿主类) 与受污染 (传递依赖桥接文件)
的文件留在 app. 本脚本按 import, 同包简单名与内联全限定名做静态分类, 字符串字面量不算引用; `--apply` 用 git mv 把文件移到
分类要求的一侧. 以 :compat:stardust 的编译结果为准, 发现漏判时修正规则而不是手工搬文件.
"""
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

HOST = Path(__file__).resolve().parents[2]
APP_ROOT = HOST / "app/src/main/java"
COMPAT_ROOT = HOST / "compat/stardust/src/main/java"

SOURCE_SUFFIXES = (".java", ".kt")
HOST_INLINE = re.compile(r"(?<![\w.])org\.autojs\.")
STARDUST_FQN = re.compile(r"(?<![\w.])com\.stardust\.[\w.]+")
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\\n])*"')


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def package_of(text: str) -> str:
    m = re.search(r"^package\s+([\w.]+)", text, re.M)
    return m.group(1) if m else ""


def code_only(text: str) -> str:
    return STRING_LITERAL.sub('""', text)


def classify():
    local_fqcn = set()
    local_packages = set()
    for p in APP_ROOT.rglob("*"):
        if p.suffix in SOURCE_SUFFIXES and "com/stardust" not in p.as_posix():
            pkg = package_of(read(p))
            if pkg:
                local_fqcn.add(f"{pkg}.{p.stem}")
                local_packages.add(pkg)

    files = {}
    for root in (APP_ROOT, COMPAT_ROOT):
        base = root / "com/stardust"
        if not base.exists():
            continue
        for p in sorted(base.rglob("*")):
            if p.suffix in SOURCE_SUFFIXES:
                rel = p.relative_to(root).as_posix()
                text = read(p)
                files[rel] = {
                    "path": p, "root": root, "package": package_of(text), "name": p.stem, "text": text,
                    "imports": re.findall(r"^import\s+(?:static\s+)?([\w.]+)(?:\.\*)?", text, re.M),
                    "wildcards": re.findall(r"^import\s+(?:static\s+)?([\w.]+)\.\*", text, re.M),
                }

    fqcn_to_rel = {f"{v['package']}.{v['name']}": k for k, v in files.items()}
    by_package = defaultdict(list)
    for k, v in files.items():
        by_package[v["package"]].append(k)

    def resolve_stardust(symbol):
        parts = symbol.split(".")
        for cut in range(len(parts), 2, -1):
            candidate = ".".join(parts[:cut])
            if candidate in fqcn_to_rel:
                return fqcn_to_rel[candidate]
        return None

    def is_host_import(imp):
        if imp.startswith("org.autojs."):
            return True
        parts = imp.split(".")
        return any(".".join(parts[:cut]) in local_fqcn for cut in range(len(parts), 1, -1))

    def deps_of(rel):
        v = files[rel]
        result = set()
        for imp in v["imports"]:
            if imp.startswith("com.stardust."):
                target = resolve_stardust(imp)
                if target:
                    result.add(target)
        for pkg in v["wildcards"]:
            if pkg in by_package:
                result.update(by_package[pkg])
        for fqn in STARDUST_FQN.findall(v["text"]):
            target = resolve_stardust(fqn)
            if target and target != rel:
                result.add(target)
        for other in by_package[v["package"]]:
            if other != rel and re.search(r"(?<![\w.])" + re.escape(files[other]["name"]) + r"\b", v["text"]):
                result.add(other)
        return result

    deps = {rel: deps_of(rel) for rel in files}
    bridging = {}
    for rel, v in files.items():
        host_imports = sorted({i for i in v["imports"] if is_host_import(i)})
        if host_imports:
            bridging[rel] = ", ".join(host_imports)
        elif HOST_INLINE.search(code_only(v["text"])):
            bridging[rel] = "inline org.autojs. reference"
        elif any(pkg in local_packages and not pkg.startswith("org.mozilla.javascript") for pkg in v["wildcards"]):
            bridging[rel] = "wildcard import of a host package"
    tainted = set()
    changed = True
    while changed:
        changed = False
        for rel in files:
            if rel not in bridging and rel not in tainted and deps[rel] & (set(bridging) | tainted):
                tainted.add(rel)
                changed = True
    standalone = set(files) - set(bridging) - tainted
    return files, bridging, tainted, standalone


def git_mv(src: Path, dst: Path):
    dst.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "-C", str(HOST), "mv", src.relative_to(HOST).as_posix(), dst.relative_to(HOST).as_posix()], check=True)


def main(argv):
    files, bridging, tainted, standalone = classify()
    misplaced = []
    for rel, v in sorted(files.items()):
        expected = COMPAT_ROOT if rel in standalone else APP_ROOT
        if v["root"] != expected:
            misplaced.append((rel, expected))
    print(f"files={len(files)} bridging={len(bridging)} tainted={len(tainted)} standalone={len(standalone)} misplaced={len(misplaced)}")
    for rel, reason in sorted(bridging.items()):
        if not reason.startswith("org.autojs."):
            print(f"  bridging: {rel} -> {reason}")
    for rel, expected in misplaced:
        print(f"  misplaced: {rel} -> {'compat' if expected == COMPAT_ROOT else 'app'}")
    if "--apply" in argv:
        for rel, expected in misplaced:
            git_mv(files[rel]["path"], expected / rel)
        print(f"moved {len(misplaced)} files")
        return 0
    return 1 if misplaced else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
