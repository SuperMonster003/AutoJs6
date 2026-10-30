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
import kotlin_types

HERE = Path(__file__).resolve().parent
# Markdown backslash escapes, as the documentation generator drops them from headings (`\_\_name\_\_` is `__name__`).
MARKDOWN_ESCAPE = re.compile(r"\\([\\`*{}\[\]()#+\-.!_>])")


def name_root(item):
    """Module root the documentation gate looks pages up by: the placement module, the owner module of a global, else global."""
    return (item["module"] or item["ownerModule"] or "global").split(".")[0]


def documentation_rows(exports, api_root, policy):
    # The generated runtime support tables (P0.5) name APIs without documenting them.
    texts = {path.stem: MARKDOWN_ESCAPE.sub(r"\1", docs_support.strip_blocks(path.read_text(encoding="utf-8"))) for path in api_root.glob("*.md")}
    if not texts:
        raise ValueError(f"No Markdown sources under {api_root}")
    rows = []
    for item in exports:
        root = name_root(item)
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


def query_roots(baseline, deferred):
    """First query segment -> deferred module root: the root itself and the aliases of its modules (`$imgui`)."""
    roots = {root: root for root in deferred}
    for module in baseline["modules"]:
        root = module["module"].split(".")[0]
        if root in deferred and "." not in module["module"]:
            roots.update((alias, root) for alias in module["aliases"])
    return roots


def deferred_names(baseline, documented, surfaces, policy):
    """Name-gate gaps under the module roots of policy.json deferredNameGate (decided deferrals, Q115 (a)) are counted, not failed.
    An entry without exports under its root, or whose root no longer has a gap, is stale and fails."""
    deferred = policy.get("deferredNameGate", {})
    roots = {item["api"]: name_root(item) for item in baseline["records"]}
    aliases = query_roots(baseline, deferred)
    report, failures = {}, []
    for root, note in deferred.items():
        exports = sum(value == root for value in roots.values())
        docs = sum(roots.get(row["api"]) == root and row["status"] == "missing" for row in documented)
        paths = sum(aliases.get(row["api"].split(".")[0]) == root for row in surfaces.get("deferred", []))
        report[root] = {"note": note, "exports": exports, "missingDocumentation": docs, "failingPaths": paths}
        if not exports:
            failures.append(f"Stale deferredNameGate entry (no exports under the module root): {root}")
        elif not docs and not paths:
            failures.append(f"Stale deferredNameGate entry (documentation, d.ts and Ace cover the module root): {root}; remove it from policy.json")
    return report, failures


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
    parser.add_argument("--strict-types", action="store_true",
                        help="Fail on d.ts parameter / return types on the unsafe levels of type-map.json strictLevels (default: report only; P2.4)")
    parser.add_argument("--source", choices=["auto", "ksp", "kotlin"], default="auto",
                        help="Exports from script-modules.json (ksp, required in CI), the Kotlin registrations (kotlin), or ksp when the file exists (auto)")
    parser.add_argument("--matrix", action="store_true",
                        help="Also build runtime-matrix.json and fail when docs/api-coverage/MATRIX.md differs, an exclusion has no valid reason (D12 decided) "
                             "or the pending cells differ from tools/api-coverage/pending-baseline.json (Q40)")
    parser.add_argument("--write-matrix", action="store_true", help="Like --matrix, and rewrite docs/api-coverage/MATRIX.md")
    parser.add_argument("--write-pending-baseline", action="store_true",
                        help="Like --matrix, and rewrite tools/api-coverage/pending-baseline.json; refused when it would add pending cells")
    parser.add_argument("--allow-new-pending", action="store_true",
                        help="With --write-pending-baseline: also write pending cells the baseline does not list (register them as a pending decision)")
    parser.add_argument("--pending-decision", metavar="TEXT", action="append", default=[],
                        help="With --write-pending-baseline: add a board decision that allows new cells to the baseline's decisions (repeatable)")
    parser.add_argument("--node", type=Path, help="Node runtime checkout for --matrix (default: sibling AutoJs6-Plugin-NodeJs-Runtime)")
    parser.add_argument("--bun", type=Path, help="Bun runtime checkout for --matrix (default: sibling AutoJs6-Plugin-Bun-Runtime)")
    parser.add_argument("--docs-support", action="store_true",
                        help="Also build runtime-matrix.json and fail when a documentation runtime support table differs from it, or a module root or returned object "
                             "has none (MATRIX.md is compared only with --matrix)")
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
    baseline = kotlin_types.attach(host, extract(host, args.metadata, args.source, policy), args.metadata)
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
    deferred = policy.get("deferredNameGate", {})
    roots = {item["api"]: name_root(item) for item in baseline["records"]}
    failures.extend("Missing documentation name: " + r["api"] for r in documented if r["status"] == "missing" and roots[r["api"]] not in deferred)
    baseline["queries"] = public_queries(baseline, policy)
    # verify-surfaces.mjs reports the paths under these roots as deferred instead of failing them (Q115 (a)).
    baseline["deferredNameGate"] = sorted(query_roots(baseline, deferred))
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
    (output / "types.json").unlink(missing_ok=True)  # written again only when there are type rows (P2.4)
    result = subprocess.run(command, check=False)
    if result.returncode:
        failures.append("TypeScript/Ace surface checks failed; see surfaces.json")
    surfaces_path = output / "surfaces.json"
    deferred_summary, deferred_failures = deferred_names(baseline, documented, json.loads(surfaces_path.read_text(encoding="utf-8")) if surfaces_path.is_file() else {}, policy)
    failures.extend(deferred_failures)
    arity_summary = arity.report(host, baseline, output)
    # Parameter / return types against script-modules.json (P2.4): types.json, reported only unless --strict-types.
    types_summary = kotlin_types.report(baseline, output)
    if args.strict_types:
        failures.extend("d.ts type differs from the implementation: " + item for item in json.loads((output / "types.json").read_text(encoding="utf-8"))["strict"])
    if args.strict_arity:
        failures.extend("d.ts overloads disjoint from the implementation arity: " + row["api"] for row in json.loads((output / "arity.json").read_text(encoding="utf-8"))["rows"] if row["status"] == "fail")
    # Members of returned objects (P0.4) and of module prototypes (Q18): name-level failures since P2.3, like the exports.
    returned_summary, returned_docs, returned_surfaces = returned_objects.gate(host, baseline, output, docs / "api", command, policy)
    returned_report = json.loads((output / "returned-objects.json").read_text(encoding="utf-8"))
    failures.extend(f"Returned object member below the name gate: {row['id']} (doc {row['doc']}, d.ts {row['dts']}, Ace {row['ace']})"
                    for row in returned_report["failures"])
    failures.extend(f"Prototype member below the name gate: {row['id']} (doc {row['doc']}, d.ts {row['dts']}, Ace {row['ace']})"
                    for row in returned_report["prototypes"]["failures"])
    failures.extend("returned-objects.json: " + problem for problem in baseline["returnedObjectsStats"]["problems"] + baseline["prototypesStats"]["problems"])
    # Names plugin contracts define (policy.json pluginExports, P5.2c): a catalogue that no longer parses fails; a
    # missing contract checkout is skipped (summary pluginExports), as are the comparisons depending on its rows.
    failures.extend(f"pluginExports {name}: {problem}" for name, item in baseline["pluginExports"].items() for problem in item.get("problems", []))
    matrix_summary = docs_summary = None
    if args.write_pending_baseline:
        args.matrix = True
    if args.matrix or args.write_matrix or args.docs_support:
        import runtime_matrix
        node = (args.node or siblings / "AutoJs6-Plugin-NodeJs-Runtime").resolve()
        bun = (args.bun or siblings / "AutoJs6-Plugin-Bun-Runtime").resolve()
        typescript = args.typescript.resolve() if args.typescript else runtime_matrix.default_typescript(ace)
        surfaces = json.loads((output / "surfaces.json").read_text(encoding="utf-8"))
        surfaces["rows"] += returned_surfaces
        # The d.ts column is "typed" where the type comparison found the row consistent (P2.4).
        type_status = {row["api"]: row["status"] for row in json.loads((output / "types.json").read_text(encoding="utf-8"))["rows"]} if (output / "types.json").is_file() else {}
        for row in surfaces["rows"]:
            row["types"] = type_status.get(row["api"])
        matrix = runtime_matrix.generate(host, node, bun, typescript, output, exports=baseline, documentation=documented + returned_docs, surfaces=surfaces)
        runtime_matrix.write(matrix, output, host / runtime_matrix.MARKDOWN if args.write_matrix else None)
        # The Node runtime declarations of the covered rows against the same Kotlin types (Q186 (a)): reported only.
        types_summary["node"] = kotlin_types.runtime_report(baseline, matrix, node, typescript, output)
        failures.extend(types_summary["node"].pop("staleReviewed", []))
        if args.write_pending_baseline:
            refused = runtime_matrix.write_pending_baseline(matrix, host / runtime_matrix.PENDING_BASELINE, args.allow_new_pending,
                                                             args.pending_decision)
            failures.extend(f"Not written to {runtime_matrix.PENDING_BASELINE}: new pending cell {item} ({column}); "
                            "add --allow-new-pending after registering it" for column, item in refused)
        if args.matrix or args.write_matrix:
            matrix_summary = runtime_matrix.gate(matrix, host / runtime_matrix.MARKDOWN, host / runtime_matrix.PENDING_BASELINE)
            failures.extend(matrix_summary.pop("failures"))
        if args.docs_support:
            # Since P2.5 every module root and returned object has a table on its page.
            docs_summary, docs_failures = docs_support.check(docs, output / "runtime-matrix.json", output, require_all=True)
            failures.extend(docs_failures)
    summary = {
        "registrationRoutes": len(baseline["modules"]), "explicitExports": len(baseline["records"]),
        "documentation": dict(Counter(row["status"] for row in documented)),
        "deferredNameGate": deferred_summary,
        "queriedPaths": len(baseline["queries"]), "aceChecked": not args.skip_ace,
        "dataSource": baseline["dataSource"]["kind"], "unresolved": len(baseline["unresolved"]),
        "arity": arity_summary, "types": types_summary, "returnedObjects": dict(returned_summary, problems=baseline["returnedObjectsStats"]["problems"] + baseline["prototypesStats"]["problems"]),
        "pluginExports": {name: {key: item[key] for key in ("status", "root", "reason", "counts") if key in item} for name, item in baseline["pluginExports"].items()},
        "matrix": matrix_summary,
        "docsSupport": docs_summary,
        "scope": baseline["scope"], "failures": failures,
    }
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
