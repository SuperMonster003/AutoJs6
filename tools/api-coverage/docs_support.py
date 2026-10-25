"""Documentation "runtime support" tables (api-coverage roadmap D13 / P0.5).

The documentation repository's `generator/runtime-support.js` writes the tables between
`<!-- runtime-support:begin <module roots> -->` and `<!-- runtime-support:end -->` of the
module pages from `runtime-matrix.json`. `check.py --docs-support` runs its `--check`
against the matrix of the same run and cross-checks the row counts. The documentation
name gate ignores the blocks (`strip_blocks`): the tables name APIs without documenting them.
"""
from collections import Counter
import json
from pathlib import Path
import re
import subprocess

GENERATOR = Path("generator/runtime-support.js")
BEGIN = re.compile(r"^<!-- runtime-support:begin((?: [\w$.]+)*) -->$")
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
    return Counter(row_root(row) for row in matrix["rows"] if row["kind"] == "export")


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
    counts = root_counts(json.loads(Path(matrix_path).read_text(encoding="utf-8")))
    for page in report["pages"]:
        if page["status"] in ("current", "stale", "skipped"):
            expected = sum(counts[root] for root in page["roots"])
            if page["rows"] != expected:
                failures.append(f"Documentation runtime support: {page['page']} shows {page['rows']} rows, the matrix has {expected} for {' '.join(page['roots'])}")
    summary = {
        "generator": str(script), "dataSource": report["dataSource"], "pages": len(report["pages"]),
        "byStatus": dict(Counter(page["status"] for page in report["pages"])),
        "skipped": [page["page"] for page in report["pages"] if page["status"] == "skipped"],
        "rows": sum(page["rows"] or 0 for page in report["pages"]),
        "differences": sum(page["differences"] or 0 for page in report["pages"]),
        "coveredRoots": len(counts) - len(report["uncovered"]), "uncoveredRoots": len(report["uncovered"]),
        "requireAll": require_all,
    }
    return summary, failures
