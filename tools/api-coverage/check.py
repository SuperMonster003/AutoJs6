#!/usr/bin/env python3
"""Check runtime exports against local documentation, declarations and Ace.

This gate checks public names and usable types/completion paths. Dedicated
signature, negative-type and runtime tests remain necessary for semantics.
"""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import subprocess
import sys

import arity
import docs_support
from extract_exports import extract
import returned_objects

HERE = Path(__file__).resolve().parent


def documentation_rows(exports, api_root, policy):
    # The generated runtime support tables (P0.5) name APIs without documenting them.
    texts = {path.stem: docs_support.strip_blocks(path.read_text(encoding="utf-8")) for path in api_root.glob("*.md")}
    if not texts:
        raise ValueError(f"No Markdown sources under {api_root}")
    rows = []
    for item in exports:
        root = (item["module"] or item["ownerModule"] or "global").split(".")[0]
        pages = policy["documentationModules"].get(root, [root])
        pattern = re.compile(r"(?<![\w$])" + re.escape(item["member"]) + r"(?![\w$])")
        evidence = []
        for page in pages:
            for number, line in enumerate(texts.get(page, "").splitlines(), 1):
                if pattern.search(line):
                    evidence.append({"page": f"api/{page}.md", "line": number, "heading": line.startswith("#")})
        headings = [hit for hit in evidence if hit["heading"]]
        rows.append({"api": item["api"], "status": "heading" if headings else "mention" if evidence else "missing", "evidence": headings or evidence})
    return rows


def public_queries(baseline, policy):
    ignored = set(policy["internalExports"]) | set(policy["standardExports"])
    queries = {item["api"] for item in baseline["records"] if item["api"] not in ignored}
    # Include callable modules with no explicit members, plus all declared keys.
    for module in baseline["modules"]:
        if module["module"]:
            parent = module["module"].rsplit(".", 1)[0] + "." if "." in module["module"] else ""
            queries.update(parent + alias for alias in module["aliases"])
    queries.update(policy["returnedObjectProbes"])
    return sorted(queries)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", type=Path, default=HERE.parents[1])
    parser.add_argument("--documentation", type=Path, help="AutoJs6-Documentation checkout (default: AUTOJS6_DOCUMENTATION_ROOT, else the sibling directory)")
    parser.add_argument("--declarations", type=Path)
    parser.add_argument("--ace", type=Path)
    parser.add_argument("--typescript", type=Path, help="Optional TypeScript compiler module; defaults to Ace's bundled compiler")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--skip-ace", action="store_true", help="Explicitly check only source docs and canonical types")
    parser.add_argument("--metadata", type=Path, help="script-modules.json (default: the KSP output of :app:kspAppDebugKotlin)")
    parser.add_argument("--strict-arity", action="store_true", help="Fail on d.ts overloads disjoint from the implementation arity (default: report only)")
    parser.add_argument("--source", choices=["auto", "ksp", "kotlin"], default="auto",
                        help="Exports from script-modules.json (ksp, required in CI), the Kotlin registrations (kotlin), or ksp when the file exists (auto)")
    parser.add_argument("--matrix", action="store_true",
                        help="Also build runtime-matrix.json and fail when docs/api-coverage/MATRIX.md differs; pending and missing exclusion reasons are reported only (until D12)")
    parser.add_argument("--write-matrix", action="store_true", help="Like --matrix, and rewrite docs/api-coverage/MATRIX.md")
    parser.add_argument("--node", type=Path, help="Node runtime checkout for --matrix (default: sibling AutoJs6-Plugin-NodeJs-Runtime)")
    parser.add_argument("--bun", type=Path, help="Bun runtime checkout for --matrix (default: sibling AutoJs6-Plugin-Bun-Runtime)")
    parser.add_argument("--docs-support", action="store_true",
                        help="Also build runtime-matrix.json and fail when a documentation runtime support table differs from it (MATRIX.md is compared only with --matrix)")
    args = parser.parse_args()
    if args.write_matrix and args.skip_ace:
        parser.error("--write-matrix needs the Ace column of MATRIX.md; drop --skip-ace (--matrix --skip-ace reports MATRIX.md as skipped)")
    host = args.host.resolve()
    siblings = host.parent
    docs = (args.documentation or Path(os.environ.get("AUTOJS6_DOCUMENTATION_ROOT") or siblings / "AutoJs6-Documentation")).resolve()
    declarations = (args.declarations or siblings / "AutoJs6-TypeScript-Declarations").resolve()
    ace = (args.ace or siblings / "AutoJs6-Plugin-Ace-Editor").resolve()
    output = (args.output or host / "build/reports/api-coverage").resolve()
    output.mkdir(parents=True, exist_ok=True)
    policy = json.loads((HERE / "policy.json").read_text(encoding="utf-8"))
    baseline = extract(host, args.metadata, args.source)
    failures = []
    if not baseline["modules"] or not baseline["records"]:
        failures.append("No runtime exports extracted")
    reviewed = set()
    for item in baseline["unresolved"]:
        key = item.get("module", "") + "." + item.get("registration", "")
        reviewed.add(key)
        if key not in policy["dynamicRegistrations"]:
            failures.append(f"Unreviewed runtime registration: {item}")
    failures.extend("Stale dynamicRegistrations entry (no such unresolved registration): " + key
                    for key in policy["dynamicRegistrations"] if key not in reviewed)
    internal = policy["internalExports"]
    documented = documentation_rows([r for r in baseline["records"] if r["api"] not in internal], docs / "api", policy)
    failures.extend("Missing documentation name: " + r["api"] for r in documented if r["status"] == "missing")
    baseline["queries"] = public_queries(baseline, policy)
    exports_path = output / "exports.json"
    exports_path.write_text(json.dumps(baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "documentation.json").write_text(json.dumps(documented, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    command = ["node", str(HERE / "verify-surfaces.mjs"), "--exports", str(exports_path), "--declarations", str(declarations), "--ace", str(ace), "--output", str(output)]
    if args.typescript:
        command += ["--typescript", str(args.typescript.resolve())]
    if args.skip_ace:
        command += ["--skip-ace"]
    contract_command = ["node", str(HERE / "check-shell-contract.mjs"), "--host", str(host), "--declarations", str(declarations), "--ace", str(ace)]
    if args.typescript:
        contract_command += ["--typescript", str(args.typescript.resolve())]
    if args.skip_ace:
        contract_command.append("--skip-ace")
    contract = subprocess.run(contract_command, check=False)
    if contract.returncode:
        failures.append("Shell process/options/event contract differs from native source or Ace copies")
    result = subprocess.run(command, check=False)
    if result.returncode:
        failures.append("TypeScript/Ace surface checks failed; see surfaces.json")
    arity_summary = arity.report(host, baseline, output)
    if args.strict_arity:
        failures.extend("d.ts overloads disjoint from the implementation arity: " + row["api"] for row in json.loads((output / "arity.json").read_text(encoding="utf-8"))["rows"] if row["status"] == "fail")
    # Members of returned objects (P0.4): reported apart, never failures (P2.3 consumes returned-objects.json).
    returned_summary, returned_docs, returned_surfaces = returned_objects.gate(host, baseline, output, docs / "api", command)
    matrix_summary = docs_summary = None
    if args.matrix or args.write_matrix or args.docs_support:
        import runtime_matrix
        node = (args.node or siblings / "AutoJs6-Plugin-NodeJs-Runtime").resolve()
        bun = (args.bun or siblings / "AutoJs6-Plugin-Bun-Runtime").resolve()
        typescript = args.typescript.resolve() if args.typescript else runtime_matrix.default_typescript(ace)
        surfaces = json.loads((output / "surfaces.json").read_text(encoding="utf-8"))
        surfaces["rows"] += returned_surfaces
        matrix = runtime_matrix.generate(host, node, bun, typescript, output, exports=baseline, documentation=documented + returned_docs, surfaces=surfaces)
        runtime_matrix.write(matrix, output, host / runtime_matrix.MARKDOWN if args.write_matrix else None)
        if args.matrix or args.write_matrix:
            matrix_summary = runtime_matrix.gate(matrix, host / runtime_matrix.MARKDOWN)
            failures.extend(matrix_summary.pop("failures"))
        if args.docs_support:
            docs_summary, docs_failures = docs_support.check(docs, output / "runtime-matrix.json", output)
            failures.extend(docs_failures)
    summary = {
        "registrationRoutes": len(baseline["modules"]), "explicitExports": len(baseline["records"]),
        "documentation": dict(Counter(row["status"] for row in documented)),
        "queriedPaths": len(baseline["queries"]), "aceChecked": not args.skip_ace,
        "dataSource": baseline["dataSource"]["kind"], "unresolved": len(baseline["unresolved"]),
        "arity": arity_summary, "returnedObjects": dict(returned_summary, problems=baseline["returnedObjectsStats"]["problems"]), "matrix": matrix_summary,
        "docsSupport": docs_summary,
        "scope": baseline["scope"], "failures": failures,
    }
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
