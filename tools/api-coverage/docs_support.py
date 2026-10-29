"""Documentation "runtime support" tables (api-coverage roadmap D13 / P0.5).

The documentation repository's `generator/runtime-support.js` writes the tables between
`<!-- runtime-support:begin <module roots> -->` and `<!-- runtime-support:end -->` of the
module pages from `runtime-matrix.json`. `check.py --docs-support` runs its `--check`
against the matrix of the same run and cross-checks the row counts. The documentation
name gate ignores the blocks (`strip_blocks`): the tables name APIs without documenting them.

Prototype members (Q18) count as exports of their module. Returned object members (Q20 (a))
belong to the object's first documentation page: a root `<Object>#` shows them on a type page,
otherwise the module page's block lists the object in its "返回对象" table (`objectRows`).
"""
from collections import Counter
import json
from pathlib import Path
import re
import subprocess

GENERATOR = Path("generator/runtime-support.js")
BEGIN = re.compile(r"^<!-- runtime-support:begin((?: [\w$.]+#?)*) -->$")
END = "<!-- runtime-support:end -->"


def strip_blocks(text):
    """Blank the lines of runtime support blocks, keeping the line numbers of the rest."""
    lines = text.split("\n")
    inside = False
    for index, line in enumerate(lines):
        bare = line.rstrip("\r")
        if inside or BEGIN.match(bare):
            inside = bare != END
            lines[index] = ""
    return "\n".join(lines)


def row_root(row):
    """Same root as check.py documentation_rows and runtime-support.js rowRoot."""
    return (row["module"] or row["ownerModule"] or "global").split(".")[0]


def root_counts(matrix):
    """Rows by block root: module roots of the exports and prototype members, `<Object>#` of the returned members."""
    counts = Counter(row_root(row) for row in matrix["rows"] if row["kind"] in ("export", "prototype-member"))
    counts.update(row["object"] + "#" for row in matrix["rows"] if row["kind"] == "returned-member")
    return counts


def page_object_counts(matrix, page, roots):
    """Returned members a module page's "返回对象" table shows: objects documented first on it, without a root of their own."""
    if not any(not root.endswith("#") for root in roots):
        return 0
    return sum(1 for row in matrix["rows"]
               if row["kind"] == "returned-member" and row.get("page") == page and row["object"] + "#" not in roots)


def check(docs, matrix_path, output=None, require_all=False, node="node"):
    """Returns (summary, failures); writes the generator's report to output/docs-support.json."""
    script = Path(docs) / GENERATOR
    if not script.is_file():
        return {"generator": None}, [f"Documentation runtime support generator not found: {script}"]
    command = [node, str(script), "--check", "--json", "--matrix", str(matrix_path), "--api", str(Path(docs) / "api")]
    if require_all:
        command.append("--require-all")
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", check=False)
    if result.returncode not in (0, 1):
        return {"generator": str(script)}, [f"Documentation runtime support generator failed ({result.returncode}): {result.stderr.strip()}"]
    report = json.loads(result.stdout)
    if output:
        (Path(output) / "docs-support.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    failures = ["Documentation runtime support: " + problem for problem in report["problems"]]
    matrix = json.loads(Path(matrix_path).read_text(encoding="utf-8"))
    counts = root_counts(matrix)
    for page in report["pages"]:
        if page["status"] in ("current", "stale", "skipped"):
            expected = sum(counts[root] for root in page["roots"])
            if page["rows"] != expected:
                failures.append(f"Documentation runtime support: {page['page']} shows {page['rows']} rows, the matrix has {expected} for {' '.join(page['roots'])}")
            objects = page_object_counts(matrix, Path(page["page"]).stem, page["roots"])
            if (page.get("objectRows") or 0) != objects:
                failures.append(f"Documentation runtime support: {page['page']} shows {page.get('objectRows') or 0} returned object rows, the matrix has {objects}")
    summary = {
        "generator": str(script), "dataSource": report["dataSource"], "pages": len(report["pages"]),
        "byStatus": dict(Counter(page["status"] for page in report["pages"])),
        "skipped": [page["page"] for page in report["pages"] if page["status"] == "skipped"],
        "rows": sum(page.get("rows") or 0 for page in report["pages"]),
        "objectRows": sum(page.get("objectRows") or 0 for page in report["pages"]),
        "differences": sum(page.get("differences") or 0 for page in report["pages"]),
        "coveredRoots": len([root for root in counts if not root.endswith("#")]) - len(report["uncovered"]),
        "uncoveredRoots": len(report["uncovered"]),
        "requireAll": require_all,
    }
    return summary, failures
